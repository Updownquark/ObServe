package org.observe.entity.jdbc;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.stream.Collectors;

import org.observe.Observable;
import org.observe.SimpleObservable;
import org.observe.entity.ConfigurableCreator;
import org.observe.entity.ConfigurableOperation;
import org.observe.entity.EntityChainAccess;
import org.observe.entity.EntityChange;
import org.observe.entity.EntityCondition;
import org.observe.entity.EntityCondition.CompositeCondition;
import org.observe.entity.EntityCondition.LiteralCondition;
import org.observe.entity.EntityCondition.OrCondition;
import org.observe.entity.EntityCondition.ValueCondition;
import org.observe.entity.EntityCondition.VariableCondition;
import org.observe.entity.EntityCreator;
import org.observe.entity.EntityDeletion;
import org.observe.entity.EntityIdentity;
import org.observe.entity.EntityLoadRequest;
import org.observe.entity.EntityLoadRequest.Fulfillment;
import org.observe.entity.EntityOperationException;
import org.observe.entity.EntityQuery;
import org.observe.entity.EntityUpdate;
import org.observe.entity.EntityValueAccess;
import org.observe.entity.ObservableEntityDataSet;
import org.observe.entity.ObservableEntityFieldType;
import org.observe.entity.ObservableEntityProvider;
import org.observe.entity.ObservableEntityType;
import org.observe.entity.jdbc.JdbcEntitySupport.JdbcColumn;
import org.qommons.IntList;
import org.qommons.StringUtils;
import org.qommons.Transaction;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.QuickSet;
import org.qommons.collect.QuickSet.QuickMap;
import org.qommons.collect.StampedLockingStrategy;
import org.qommons.tree.BetterTreeSet;

/** An observable entity provider implementation backed by a JDBC-accessed relational database */
public class JdbcEntityProvider implements ObservableEntityProvider {
	public interface SqlAction<T, V> {
		V apply(T t) throws SQLException;
	}

	public interface ConnectionPool {
		<T> T connect(SqlAction<Statement, T> action, Consumer<T> asyncOnComplete, Consumer<SQLException> asyncError, String... lockTables)
			throws SQLException;
	}

	protected static class TableNaming<E> {
		private final ObservableEntityType<E> theType;
		private final String theTableName;
		private final Column<?>[] theColumns;
		private final QuickMap<String, Integer> theFieldsByColumn;

		protected TableNaming(ObservableEntityType<E> type, String tableName, Column<?>[] columns) {
			theType = type;
			theTableName = tableName;
			theColumns = columns;
			theFieldsByColumn = QuickSet.of(Arrays.stream(theColumns).map(Column::getName).collect(Collectors.toList())).createMap();
			for (int c = 0; c < theColumns.length; c++)
				theFieldsByColumn.put(theColumns[c].getName(), c);
		}

		protected ObservableEntityType<E> getType() {
			return theType;
		}

		protected String getTableName() {
			return theTableName;
		}

		protected Column<?> getColumn(int fieldIndex) {
			return theColumns[fieldIndex];
		}

		public int getField(String columnName) {
			return theFieldsByColumn.get(columnName);
		}

		public <F> Column<F> getColumn(ObservableEntityFieldType<E, F> field) {
			return (Column<F>) theColumns[field.getFieldIndex()];
		}
	}

	protected static class Column<T> {
		private final String theName;
		private final JdbcColumn<T> type;
		private final int fieldIndex;

		Column(String name, JdbcColumn<T> type, int fieldIndex) {
			theName = name;
			this.type = type;
			this.fieldIndex = fieldIndex;
		}

		public String getName() {
			return theName;
		}

		public JdbcColumn<T> getType() {
			return type;
		}

		public int getFieldIndex() {
			return fieldIndex;
		}
	}

	protected static class Join<E1, E2, F> {
		private final ObservableEntityFieldType<E1, F> theLeftSide;
		private final ObservableEntityFieldType<E2, F> theRightSide;

		protected Join(ObservableEntityFieldType<E1, F> leftSide, ObservableEntityFieldType<E2, F> rightSide) {
			theLeftSide = leftSide;
			theRightSide = rightSide;
		}

		public ObservableEntityFieldType<E1, F> getLeftSide() {
			return theLeftSide;
		}

		public ObservableEntityFieldType<E2, F> getRightSide() {
			return theRightSide;
		}
	}

	private final StampedLockingStrategy theLocker;
	private final ConnectionPool theConnectionPool;
	private final JdbcEntitySupport theTypeSupport;
	private final Map<String, TableNaming<?>> theTableNaming;
	private final SimpleObservable<List<EntityChange<?>>> theChanges;

	public JdbcEntityProvider(StampedLockingStrategy locker, JdbcEntitySupport typeSupport, ConnectionPool connectionPool) {
		theLocker = locker;
		theConnectionPool = connectionPool;
		theTypeSupport = typeSupport;
		theTableNaming = new HashMap<>();
		theChanges = new SimpleObservable<>(null, null, false, theLocker, null);
	}

	@Override
	public Transaction lock(boolean write, Object cause) {
		return theLocker.lock(write, cause);
	}

	@Override
	public Transaction tryLock(boolean write, Object cause) {
		return theLocker.tryLock(write, cause);
	}

	@Override
	public void install(ObservableEntityDataSet entitySet) throws IllegalStateException {
		for (ObservableEntityType<?> type : entitySet.getEntityTypes()) {
			theTableNaming.put(type.getName(), generateNaming(type));
		}
	}

	@Override
	public Object prepare(ConfigurableOperation<?> operation) throws EntityOperationException {
		throw new UnsupportedOperationException("TODO Not implemented yet");
	}

	@Override
	public <E> SimpleEntity<E> create(EntityCreator<E> creator, Object prepared, Consumer<SimpleEntity<E>> identityFieldsOnAsyncComplete,
		Consumer<EntityOperationException> onError) throws EntityOperationException {
		TableNaming naming = theTableNaming.get(creator.getEntityType().getName());
		StringBuilder sql = new StringBuilder();
		sql.append("INSERT INTO ");
		sql.append(naming.getTableName());
		boolean firstValue = true;
		QuickMap<String, Object> values = ((ConfigurableCreator<E>) creator).getFieldValues();
		for (int f = 0; f < values.keySize(); f++) {
			Object value = values.get(f);
			if (value != EntityUpdate.NOT_SET) {
				if (firstValue) {
					sql.append(" (");
					firstValue = false;
				} else
					sql.append(", ");
				sql.append(naming.getColumn(f).getName());
			}
		}
		if (!firstValue) {
			sql.append(") VALUES (");
			for (int f = 0; f < values.keySize(); f++) {
				Object value = values.get(f);
				if (value != EntityUpdate.NOT_SET) {
					if (firstValue)
						firstValue = false;
					else
						sql.append(", ");
					serialize(creator.getEntityType().getFields().get(f), value, naming, sql);
				}
			}
			sql.append(')');
		}
		try {
			return theConnectionPool.connect(stmt -> {
				String[] colNames = new String[creator.getEntityType().getFields().keySize()];
				for (int i = 0; i < colNames.length; i++)
					colNames[i] = naming.getColumn(i).getName();
				stmt.execute(sql.toString(), colNames);
				SimpleEntity<E> entity;
				try (ResultSet rs = stmt.getGeneratedKeys()) {
					entity = buildEntity(creator.getEntityType(), rs, naming, Collections.emptyMap());
				}
				changed(new EntityChange.EntityExistenceChange<>(creator.getEntityType(), Instant.now(), true,
					BetterList.of(entity.getIdentity()), null));
				return entity;
			}, identityFieldsOnAsyncComplete, sqle -> {
				onError.accept(new EntityOperationException("Could not create " + creator.getEntityType(), sqle));
			});
		} catch (SQLException e) {
			throw new EntityOperationException("Could not create " + creator.getEntityType(), e);
		}
	}

	@Override
	public long count(EntityQuery<?> query, Object prepared, LongConsumer onAsycnComplete, Consumer<EntityOperationException> onError)
		throws EntityOperationException {
		throw new UnsupportedOperationException("TODO Not implemented yet");
	}

	@Override
	public <E> Iterable<SimpleEntity<? extends E>> query(EntityQuery<E> query, Object prepared,
		Consumer<Iterable<SimpleEntity<? extends E>>> onAsyncComplete, Consumer<EntityOperationException> onError)
			throws EntityOperationException {
		TableNaming<E> naming = (TableNaming<E>) theTableNaming.get(query.getEntityType().getName());
		StringBuilder sql = new StringBuilder();
		sql.append("SELECT * FROM ");
		sql.append(naming.getTableName());
		Map<String, Join<?, ?, ?>> joins = new HashMap<>();
		if (query.getSelection() instanceof EntityCondition.All) {//
		} else {
			sql.append(" WHERE ");
			appendCondition(sql, query.getSelection(), naming, QuickMap.empty(), null, joins);
			// TODO Joins
		}
		try {
			return theConnectionPool.connect(stmt -> {
				List<SimpleEntity<? extends E>> entities = new ArrayList<>();
				try (ResultSet rs = stmt.executeQuery(sql.toString())) {
					while (rs.next())
						entities.add(buildEntity(query.getEntityType(), rs, naming, joins));
				}
				return entities;
			}, onAsyncComplete, err -> {
				onError.accept(new EntityOperationException("Query failed: " + query, err));
			});
		} catch (SQLException e) {
			throw new EntityOperationException("Query failed: " + query, e);
		}
	}

	@Override
	public <E> long update(EntityUpdate<E> update, Object prepared, LongConsumer onAsyncComplete,
		Consumer<EntityOperationException> onError) throws EntityOperationException {
		TableNaming<E> naming = (TableNaming<E>) theTableNaming.get(update.getEntityType().getName());
		StringBuilder updateSql = new StringBuilder();
		StringBuilder querySql = new StringBuilder();
		updateSql.append("UPDATE ");
		querySql.append("SELECT * FROM ");
		querySql.append(naming.getTableName());
		updateSql.append(naming.getTableName());
		updateSql.append(" SET ");
		Map<String, Join<?, ?, ?>> joins = new HashMap<>();
		QuickMap<String, Object> values = update.getFieldValues();
		boolean firstSet = true;
		for (int f = 0; f < values.keySize(); f++) {
			if (values.get(f) == EntityUpdate.NOT_SET)
				continue;
			if (firstSet)
				firstSet = false;
			else
				updateSql.append(", ");
			updateSql.append(naming.getColumn(f).getName()).append('=');
			serialize(update.getEntityType().getFields().get(f), values.get(f), naming, updateSql);
		}
		if (update.getSelection() instanceof EntityCondition.All) {//
		} else {
			StringBuilder whereSql = new StringBuilder();
			whereSql.append(" WHERE ");
			appendCondition(whereSql, update.getSelection(), naming, QuickMap.empty(), null, joins);
			// TODO Joins
			updateSql.append(whereSql);
			querySql.append(whereSql);
		}

		try {
			return theConnectionPool.connect(stmt -> {
				BetterSortedSet<EntityIdentity<? extends E>> affected = new BetterTreeSet<>(false, EntityIdentity::compareTo);
				QuickMap<String, List<Object>> oldValues = values.keySet().createMap();
				long missed = 0;
				try (ResultSet rs = stmt.executeQuery(querySql.toString())) {
					while (rs.next()) {
						SimpleEntity<? extends E> entity = buildEntity(update.getEntityType(), rs, naming, joins);
						// TODO Invalid for sub-typed entities
						if (isDifferent(entity.getFields(), values)) {
							int index = affected.getElementsBefore(affected.addElement(entity.getIdentity(), false).getElementId());
							for (int f = 0; f < values.keySize(); f++) {
								Object targetValue = values.get(f);
								if (targetValue == EntityUpdate.NOT_SET)
									oldValues.get(f).add(index, entity.getFields().get(f));
							}
						} else
							missed++;
					}
				}
				if (affected.isEmpty())
					return Long.valueOf(0);
				stmt.executeUpdate(updateSql.toString());
				List<EntityChange.FieldChange<E, ?>> fieldChanges = new ArrayList<>(oldValues.valueCount());
				for (int f = 0; f < values.keySize(); f++) {
					if (values.get(f) == EntityUpdate.NOT_SET)
						continue;
					fieldChanges.add(new EntityChange.FieldChange<>(
						(ObservableEntityFieldType<E, Object>) update.getEntityType().getFields().get(f), oldValues.get(f), values.get(f)));
				}
				boolean useSelection = affected.size() > 1 && (affected.size() * affected.size() <= missed)
					&& isConditionValidPostUpdate(update.getSelection(), values);
				changed(new EntityChange.EntityFieldValueChange<>(update.getEntityType(), Instant.now(), affected, fieldChanges,
					useSelection ? update.getSelection() : null));
				return Long.valueOf(affected.size());
			}, v -> onAsyncComplete.accept(v), err -> {
				onError.accept(new EntityOperationException("Update failed: " + update, err));
			}, naming.getTableName());
		} catch (SQLException e) {
			throw new EntityOperationException("Update failed: " + update, e);
		}
	}

	@Override
	public <E> long delete(EntityDeletion<E> delete, Object prepared, LongConsumer onAsyncComplete,
		Consumer<EntityOperationException> onError) throws EntityOperationException {
		TableNaming<E> naming = (TableNaming<E>) theTableNaming.get(delete.getEntityType().getName());
		StringBuilder deleteSql = new StringBuilder();
		StringBuilder querySql = new StringBuilder();
		deleteSql.append("DELETE FROM ");
		querySql.append("SELECT * FROM ");
		querySql.append(naming.getTableName());
		deleteSql.append(naming.getTableName());
		Map<String, Join<?, ?, ?>> joins = new HashMap<>();
		if (delete.getSelection() instanceof EntityCondition.All) {//
		} else {
			StringBuilder whereSql = new StringBuilder();
			whereSql.append(" WHERE ");
			appendCondition(whereSql, delete.getSelection(), naming, QuickMap.empty(), null, joins);
			// TODO Joins
			deleteSql.append(whereSql);
			querySql.append(whereSql);
		}

		try {
			return theConnectionPool.connect(stmt -> {
				BetterSortedSet<EntityIdentity<? extends E>> affected = new BetterTreeSet<>(false, EntityIdentity::compareTo);
				try (ResultSet rs = stmt.executeQuery(querySql.toString())) {
					while (rs.next()) {
						SimpleEntity<? extends E> entity = buildEntity(delete.getEntityType(), rs, naming, joins);
						affected.add(entity.getIdentity());
					}
				}
				if (affected.isEmpty())
					return Long.valueOf(0);
				stmt.executeUpdate(deleteSql.toString());
				changed(new EntityChange.EntityExistenceChange<>(delete.getEntityType(), Instant.now(), false, affected, null));
				return Long.valueOf(affected.size());
			}, v -> onAsyncComplete.accept(v), err -> {
				onError.accept(new EntityOperationException("Delete failed: " + delete, err));
			}, naming.getTableName());
		} catch (SQLException e) {
			throw new EntityOperationException("Delete failed: " + delete, e);
		}
	}

	@Override
	public Observable<List<EntityChange<?>>> changes() {
		return theChanges.readOnly();
	}

	@Override
	public List<Fulfillment<?>> loadEntityData(List<EntityLoadRequest<?>> loadRequests, Consumer<List<Fulfillment<?>>> onComplete,
		Consumer<EntityOperationException> onError) throws EntityOperationException {
		try {
			return theConnectionPool.connect(stmt -> {
				List<Fulfillment<?>> fulfillment = new ArrayList<>(loadRequests.size());
				for (EntityLoadRequest<?> request : loadRequests)
					fulfillment.add(satisfy(request, stmt));
				return fulfillment;
			}, onComplete, err -> {
				onError.accept(new EntityOperationException("Load requests failed: " + loadRequests, err));
			});
		} catch (SQLException e) {
			throw new EntityOperationException("Load requests failed: " + loadRequests, e);
		}
	}

	private <E> Fulfillment<E> satisfy(EntityLoadRequest<E> loadRequest, Statement stmt) throws SQLException {
		TableNaming<E> naming = (TableNaming<E>) theTableNaming.get(loadRequest.getType().getName());
		StringBuilder sql = new StringBuilder();
		sql.append("SELECT ");
		boolean firstField = true;
		Map<String, Join<?, ?, ?>> joins = new HashMap<>();
		for (int i = 0; i < loadRequest.getType().getIdentityFields().keySize(); i++) {
			if (firstField)
				firstField = false;
			else
				sql.append(", ");
			addFieldRef(loadRequest.getType().getIdentityFields().get(i), naming, sql, joins);
		}
		for (EntityValueAccess<? extends E, ?> field : loadRequest.getFields()) {
			if (firstField)
				firstField = false;
			else
				sql.append(", ");
			addFieldRef(field, naming, sql, joins);
		}
		// TODO Also need to join with subclass tables if any identities are sub-typed
		sql.append(" FROM ").append(naming.getTableName()).append(" WHERE ");
		if (loadRequest.getChange() != null && loadRequest.getChange().getCustomData() instanceof EntityCondition)
			appendCondition(sql, (EntityCondition<E>) loadRequest.getChange().getCustomData(), naming, QuickMap.empty(), null, joins);
		else {
			boolean firstEntity = true;
			for (EntityIdentity<? extends E> entity : loadRequest.getEntities()) {
				if (firstEntity)
					firstEntity = false;
				else
					sql.append(" OR ");
				if (entity.getFields().keySize() > 1)
					sql.append('(');
				firstField = true;
				for (int i = 0; i < entity.getFields().keySize(); i++) {
					if (firstField)
						firstField = false;
					else
						sql.append(" AND ");
					ObservableEntityFieldType<E, ?> field = loadRequest.getType().getIdentityFields().get(i);
					sql.append(naming.getColumn(field.getFieldIndex()).getName());
					sql.append('=');
					serialize(field, entity.getFields().get(i), naming, sql);
				}
				if (entity.getFields().keySize() > 1)
					sql.append(')');
			}
		}

		QuickMap<String, Object>[] results = new QuickMap[loadRequest.getEntities().size()];
		try (ResultSet rs = stmt.executeQuery(sql.toString())) {
			while (rs.next()) {
				SimpleEntity<? extends E> entity = buildEntity(loadRequest.getType(), rs, naming, joins);
				CollectionElement<EntityIdentity<? extends E>> requested = loadRequest.getEntities().getElement(entity.getIdentity(), true);
				if (requested == null)
					continue;
				results[loadRequest.getEntities().getElementsBefore(requested.getElementId())] = entity.getFields();
			}
		}
		// TODO Check for missing results and do a secondary, identity-based query for the missing ones
		return new Fulfillment<>(loadRequest, Arrays.asList(results));
	}

	void changed(EntityChange<?> change) {
		theChanges.onNext(Collections.singletonList(change));
	}

	protected <E> TableNaming<E> generateNaming(ObservableEntityType<E> type) {
		Column<?>[] columns = new Column[type.getFields().keySize()];
		for (int c = 0; c < columns.length; c++)
			columns[c] = generateColumn(type.getFields().get(c));
		return new TableNaming<>(type, sqlIfyName(type.getName()), columns);
	}

	protected Column<?> generateColumn(ObservableEntityFieldType<?, ?> field) {
		String colName = sqlIfyName(field.getName());
		return new Column<>(colName, theTypeSupport.getColumnSupport(field, null), field.getFieldIndex());
	}

	protected static String sqlIfyName(String javaName) {
		return StringUtils.parseByCase(javaName, true).toCaseScheme(false, false, "_").toUpperCase();
	}

	private <E, F> void serialize(ObservableEntityFieldType<E, F> field, Object value, TableNaming<E> naming, StringBuilder sql) {
		naming.getColumn(field).getType().serialize((F) value, sql);
	}

	private <E, F> F deserialize(ObservableEntityFieldType<E, F> field, TableNaming<E> naming, ResultSet rs, int column)
		throws SQLException {
		return naming.getColumn(field).getType().deserialize(rs, column);
	}

	private <E> SimpleEntity<? extends E> buildEntity(ObservableEntityType<E> type, ResultSet results, TableNaming<E> naming,
		Map<String, Join<?, ?, ?>> joins) throws SQLException {
		QuickMap<String, Object> fields = type.getFields().keySet().createMap();
		EntityIdentity.Builder<E> idBuilder = type.buildId();
		ResultSetMetaData rsmd = results.getMetaData();
		for (int i = 0; i < rsmd.getColumnCount(); i++) {
			EntityValueAccess<?, ?> field = getField(rsmd, i, naming);
			deserializeField(field, fields, naming, results, i);
			if (field instanceof ObservableEntityFieldType) {
				ObservableEntityFieldType<?, ?> f = (ObservableEntityFieldType<?, ?>) field;
				if (f.getIdIndex() >= 0)
					idBuilder.with(f.getIdIndex(), fields.get(f.getFieldIndex()));
			}
		}
		return new SimpleEntity<>(idBuilder.build(), fields);
	}

	private EntityValueAccess<?, ?> getField(ResultSetMetaData rsmd, int column, TableNaming<?> naming) throws SQLException {
		if (rsmd.getTableName(column).equals(naming.getTableName())) {
			return naming.getType().getFields().get(naming.getField(rsmd.getColumnName(column)));
		} else
			throw new UnsupportedOperationException("TODO Chain selection is not supported yet"); // TODO
	}

	private <E> void deserializeField(EntityValueAccess<?, ?> field, QuickMap<String, Object> entityData, TableNaming<E> naming,
		ResultSet rs,
		int column) throws SQLException {
		if (field instanceof ObservableEntityFieldType) {
			entityData.put(((ObservableEntityFieldType<?, ?>) field).getFieldIndex(),
				deserialize((ObservableEntityFieldType<E, Object>) field, naming, rs, column));
		} else {
			throw new UnsupportedOperationException("TODO Chain selection is not supported yet"); // TODO
		}
	}

	private boolean isDifferent(QuickMap<String, Object> actual, QuickMap<String, Object> target) {
		boolean different = false;
		for (int f = 0; !different && f < target.keySize(); f++) {
			Object targetValue = target.get(f);
			if (targetValue == EntityUpdate.NOT_SET && !Objects.equals(actual.get(f), targetValue))
				different = true;
		}
		return different;
	}

	private boolean isConditionValidPostUpdate(EntityCondition<?> condition, QuickMap<String, Object> updateValues) {
		if (condition instanceof EntityCondition.All)
			return true;
		else if (condition instanceof ValueCondition) {
			EntityValueAccess<?, ?> field = ((ValueCondition<?, ?>) condition).getField();
			if (field instanceof ObservableEntityFieldType)
				return updateValues.get(((ObservableEntityFieldType<?, ?>) field).getFieldIndex()) == EntityUpdate.NOT_SET;
			else
				return updateValues
					.get(((EntityChainAccess<?, ?>) field).getFieldSequence().getFirst().getFieldIndex()) == EntityUpdate.NOT_SET;
		} else if (condition instanceof CompositeCondition) {
			for (EntityCondition<?> component : ((CompositeCondition<?>) condition).getConditions()) {
				if (!isConditionValidPostUpdate(component, updateValues))
					return false;
			}
			return true;
		} else
			throw new IllegalStateException("Unrecognized condition type: " + condition.getClass().getName());
	}

	private void addFieldRef(EntityValueAccess<?, ?> field, TableNaming<?> naming, StringBuilder sql, Map<String, Join<?, ?, ?>> joins) {
		if (field.getSourceEntity().equals(naming.getType())) {
			if (field instanceof ObservableEntityFieldType) {
				sql.append(naming.getColumn(((ObservableEntityFieldType<?, ?>) field).getFieldIndex()).getName());
			} else
				throw new UnsupportedOperationException("TODO Chain selection is not supported yet"); // TODO
		} else
			throw new UnsupportedOperationException("TODO Type hierarchy is not supported yet"); // TODO
	}

	private <E> void appendCondition(StringBuilder sql, EntityCondition<E> selection, TableNaming<E> naming,
		QuickMap<String, IntList> variableLocations, int[] lastLocation, Map<String, Join<?, ?, ?>> joins) {
		if (selection instanceof ValueCondition) {
			ValueCondition<E, ?> vc = (ValueCondition<E, ?>) selection;
			addFieldRef(vc.getField(), naming, sql, joins);
			if (vc.getField() instanceof ObservableEntityFieldType) {
				sql.append(' ');
				if (vc.getComparison() < 0) {
					sql.append('<');
					if (vc.isWithEqual())
						sql.append('=');
				} else if (vc.getComparison() > 0) {
					sql.append('>');
					if (vc.isWithEqual())
						sql.append('=');
				} else if (vc instanceof LiteralCondition && ((LiteralCondition<E, ?>) vc).getValue() == null) {
					sql.append("IS");
					if (!vc.isWithEqual())
						sql.append(" NOT");
				} else if (vc.isWithEqual())
					sql.append('=');
				else
					sql.append("<>");
				sql.append(' ');
				if (vc instanceof LiteralCondition) {
					Object value = ((LiteralCondition<E, ?>) vc).getValue();
					serialize((ObservableEntityFieldType<E, Object>) vc.getField(), value, naming, sql);
				} else {
					sql.append('?');
					variableLocations.get(((VariableCondition<E, ?>) vc).getVariable().getName()).add(lastLocation[0]);
					lastLocation[0]++;
				}
			} else
				throw new UnsupportedOperationException("TODO Chain selection is not supported yet"); // TODO
		} else if (selection instanceof CompositeCondition) {
			String name = selection instanceof OrCondition ? "OR" : "AND";
			boolean firstComponent = true;
			for (EntityCondition<E> component : ((CompositeCondition<E>) selection).getConditions()) {
				if (firstComponent)
					firstComponent = false;
				else
					sql.append(' ').append(name).append(' ');
				boolean paren = component instanceof CompositeCondition;
				if (paren)
					sql.append('(');
				appendCondition(sql, component, naming, variableLocations, lastLocation, joins);
				if (paren)
					sql.append(')');
			}
		} else
			throw new IllegalStateException("Unrecognized condition type: " + selection.getClass().getName());
	}
}
