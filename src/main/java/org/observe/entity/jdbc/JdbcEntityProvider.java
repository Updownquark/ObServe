package org.observe.entity.jdbc;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.observe.config.OperationResult;
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
import org.observe.entity.EntityModification;
import org.observe.entity.EntityOperationException;
import org.observe.entity.EntityQuery;
import org.observe.entity.EntityUpdate;
import org.observe.entity.EntityValueAccess;
import org.observe.entity.ObservableEntityDataSet;
import org.observe.entity.ObservableEntityFieldType;
import org.observe.entity.ObservableEntityProvider;
import org.observe.entity.ObservableEntityType;
import org.observe.entity.PreparedCreator;
import org.observe.entity.PreparedDeletion;
import org.observe.entity.PreparedOperation;
import org.observe.entity.PreparedQuery;
import org.observe.entity.PreparedUpdate;
import org.observe.entity.jdbc.JdbcEntitySupport.JdbcColumn;
import org.observe.util.TypeTokens;
import org.qommons.BiTuple;
import org.qommons.IntList;
import org.qommons.QommonsUtils;
import org.qommons.StringUtils;
import org.qommons.ValueHolder;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterCollections;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.ListenerList;
import org.qommons.collect.MultiMap;
import org.qommons.collect.QuickSet;
import org.qommons.collect.QuickSet.QuickMap;
import org.qommons.collect.StampedLockingStrategy;
import org.qommons.tree.BetterTreeList;
import org.qommons.tree.BetterTreeSet;

import com.google.common.reflect.TypeToken;

/** An observable entity provider implementation backed by a JDBC-accessed relational database */
public class JdbcEntityProvider implements ObservableEntityProvider {
	public interface SqlAction<T> {
		T execute(Statement stmt, BooleanSupplier canceled) throws SQLException, EntityOperationException;
	}

	public interface PreparedSqlAction<T> {
		T execute(PreparedStatement stmt, QuickMap<String, Object> variableValues, BooleanSupplier canceled)
			throws SQLException, EntityOperationException;
	}

	public interface PreparedSqlOperation<E, T> {
		T execute(PreparedOperation<E> operation) throws SQLException, EntityOperationException;

		OperationResult<T> executeAsync(PreparedOperation<E> operation, Function<SQLException, EntityOperationException> sqlError);

		void dispose();
	}

	public interface ConnectionPool {
		<T> T connect(SqlAction<T> action) throws SQLException, EntityOperationException;

		<T> OperationResult<T> connectAsync(SqlAction<T> action, Function<SQLException, EntityOperationException> sqlError);

		<E, T> PreparedSqlOperation<E, T> prepare(String sql, PreparedSqlAction<T> action) throws SQLException;
	}

	static final BooleanSupplier ALWAYS_FALSE = () -> false;

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
			return (Column<F>) theColumns[field.getIndex()];
		}

		@Override
		public String toString() {
			return theType.toString();
		}
	}

	protected static abstract class Column<T> {
		private final String theName;

		Column(String name) {
			theName = name;
		}

		public String getName() {
			return theName;
		}

		@Override
		public String toString() {
			return theName;
		}
	}

	interface SerialColumnComponent {
		String getName();

		boolean isNull();

		void writeValue(StringBuilder str);
	}

	protected static abstract class SerialColumn<T> extends Column<T> {
		SerialColumn(String name) {
			super(name);
		}

		abstract int getColumnCount();

		abstract void forEachColumn(Consumer<SimpleColumn<?>> onColumn);

		abstract void forEachColumnValue(T value, Consumer<SerialColumnComponent> onColumn);

		abstract T deserialize(ResultSet rs, int startColumn) throws SQLException;
	}

	protected static class SimpleColumn<T> extends SerialColumn<T> {
		private final JdbcColumn<T> type;
		private final boolean isNullable;

		SimpleColumn(String name, JdbcColumn<T> type, boolean nullable) {
			super(name);
			this.type = type;
			isNullable = nullable;
		}

		public JdbcColumn<T> getType() {
			return type;
		}

		@Override
		int getColumnCount() {
			return 1;
		}

		public void writeConstraints(StringBuilder str) {
			if (!isNullable)
				str.append(" NOT NULL");
		}

		@Override
		void forEachColumn(Consumer<SimpleColumn<?>> onColumn) {
			onColumn.accept(this);
		}

		@Override
		void forEachColumnValue(T value, Consumer<SerialColumnComponent> onColumn) {
			onColumn.accept(new SerialColumnComponent() {
				@Override
				public String getName() {
					return SimpleColumn.this.getName();
				}

				@Override
				public boolean isNull() {
					return value == null;
				}

				@Override
				public void writeValue(StringBuilder str) {
					type.serialize(value, str);
				}
			});
		}

		@Override
		T deserialize(ResultSet rs, int startColumn) throws SQLException {
			return type.deserialize(rs, startColumn);
		}
	}

	protected static class ReferenceColumn<E> extends SerialColumn<EntityIdentity<E>> {
		private final ObservableEntityType<E> theReferenceType;
		private final SerialColumn<?>[] idColumns;

		ReferenceColumn(String name, ObservableEntityType<E> referenceType, SerialColumn<?>[] idColumns) {
			super(name);
			theReferenceType = referenceType;
			this.idColumns = idColumns;
		}

		@Override
		int getColumnCount() {
			int columns = 0;
			for (int i = 0; i < idColumns.length; i++)
				columns += idColumns[i].getColumnCount();
			return columns;
		}

		@Override
		void forEachColumn(Consumer<SimpleColumn<?>> onColumn) {
			for (int i = 0; i < idColumns.length; i++)
				idColumns[i].forEachColumn(onColumn);
		}

		@Override
		void forEachColumnValue(EntityIdentity<E> value, Consumer<SerialColumnComponent> onColumn) {
			for (int i = 0; i < idColumns.length; i++)
				((SerialColumn<Object>) idColumns[i]).forEachColumnValue(value == null ? null : value.getFields().get(i), onColumn);
		}

		@Override
		EntityIdentity<E> deserialize(ResultSet rs, int startColumn) throws SQLException {
			EntityIdentity.Builder<E> builder = theReferenceType.buildId();
			for (int i = 0; i < idColumns.length; i++) {
				builder.with(i, idColumns[i].deserialize(rs, startColumn));
				startColumn += idColumns[i].getColumnCount();
			}
			return builder.build();
		}
	}

	protected static abstract class EntryColumn<E, T, V> extends Column<T> {
		private final String theTableName;
		private final String theIndexColumn;
		private final ReferenceColumn<E> ownerColumn;
		private final SerialColumn<V> valueColumn;

		EntryColumn(String name, String tableName, String indexColumn, ReferenceColumn<E> ownerColumn, SerialColumn<V> valueColumn) {
			super(name);
			theTableName = tableName;
			theIndexColumn = indexColumn;
			this.ownerColumn = ownerColumn;
			this.valueColumn = valueColumn;
		}

		public String getTableName() {
			return theTableName;
		}

		public String getIndexColumn() {
			return theIndexColumn;
		}

		public ReferenceColumn<E> getOwnerColumn() {
			return ownerColumn;
		}

		public SerialColumn<V> getValueColumn() {
			return valueColumn;
		}

		abstract int getValueColumnCount();

		abstract T createInitialValue(EntityIdentity<E> entity);

		abstract void addValue(T value, ResultSet rs, int columnIndex) throws SQLException;
	}

	static class BackingList<E, V> extends BetterCollections.UnmodifiableBetterList<V> {
		private final EntityIdentity<E> theEntity;
		private final CollectionColumn<E, V> theColumn;

		public BackingList(EntityIdentity<E> entity, CollectionColumn<E, V> column) {
			super(new BetterTreeList<>(false));
			theEntity = entity;
			theColumn = column;
		}

		@Override
		protected BetterList<V> getWrapped() {
			return (BetterList<V>) super.getWrapped();
		}

		EntityIdentity<E> getEntity() {
			return theEntity;
		}

		CollectionColumn<E, V> getColumn() {
			return theColumn;
		}
	}

	protected static class CollectionColumn<E, V> extends EntryColumn<E, BackingList<E, V>, V> {
		private final boolean isSorted;

		CollectionColumn(String name, String tableName, String indexColumn, ReferenceColumn<E> ownerColumn, SerialColumn<V> valueColumn,
			boolean sorted) {
			super(name, tableName, indexColumn, ownerColumn, valueColumn);
			isSorted = sorted;
		}

		public boolean isSorted() {
			return isSorted;
		}

		@Override
		int getValueColumnCount() {
			return getValueColumn().getColumnCount() + (getIndexColumn() == null ? 0 : 1);
		}

		@Override
		BackingList<E, V> createInitialValue(EntityIdentity<E> entity) {
			return new BackingList<>(entity, this);
		}

		@Override
		void addValue(BackingList<E, V> value, ResultSet rs, int columnIndex) throws SQLException {
			value.getWrapped().add(//
				getValueColumn().deserialize(rs, columnIndex));
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
	private final String theSchemaName;
	private final ListenerList<EntityChange<?>> theChanges;
	private final boolean installSchema;
	private ObservableEntityDataSet theEntitySet;

	public JdbcEntityProvider(StampedLockingStrategy locker, JdbcEntitySupport typeSupport, ConnectionPool connectionPool,
		String schemaName, boolean installSchema) {
		theLocker = locker;
		theConnectionPool = connectionPool;
		theTypeSupport = typeSupport;
		theSchemaName = schemaName;
		theTableNaming = new HashMap<>();
		theChanges = ListenerList.build().build();
		this.installSchema = installSchema;
	}

	@Override
	public void install(ObservableEntityDataSet entitySet) throws EntityOperationException {
		theEntitySet = entitySet;
		for (ObservableEntityType<?> type : entitySet.getEntityTypes()) {
			TableNaming<?> naming = generateNaming(type);
			theTableNaming.put(type.getName(), naming);
			if (installSchema)
				writeTableIfAbsent(type, naming);
		}
	}

	@Override
	public Object prepare(ConfigurableOperation<?> operation) throws EntityOperationException {
		throw new UnsupportedOperationException("TODO Not implemented yet");
	}

	@Override
	public void dispose(Object prepared) {
		((PreparedSqlOperation<?, ?>) prepared).dispose();
	}

	@Override
	public <E> SimpleEntity<E> create(EntityCreator<? super E, E> creator, Object prepared, boolean reportInChanges)
		throws EntityOperationException {
		try {
			if (prepared instanceof PreparedSqlOperation)
				return ((PreparedSqlOperation<E, SimpleEntity<E>>) prepared).execute((PreparedCreator<? super E, E>) creator);
			else
				return theConnectionPool.connect(new CreateAction<>(creator, reportInChanges));
		} catch (SQLException e) {
			throw new EntityOperationException("Create failed: " + creator, e);
		}
	}

	@Override
	public <E> OperationResult<SimpleEntity<E>> createAsync(EntityCreator<? super E, E> creator, Object prepared, boolean reportInChanges) {
		if (prepared instanceof PreparedSqlOperation) {
			return ((PreparedSqlOperation<E, SimpleEntity<E>>) prepared).executeAsync((PreparedCreator<? super E, E>) creator, //
				sqlE -> new EntityOperationException("Create failed: " + creator, sqlE));
		} else {
			return theConnectionPool.connectAsync(new CreateAction<>(creator, reportInChanges), //
				sqlE -> new EntityOperationException("Create failed: " + creator, sqlE));
		}
	}

	class CreateAction<E> implements SqlAction<SimpleEntity<E>> {
		private final EntityCreator<? super E, E> theCreator;
		private final boolean reportInChanges;
		private final TableNaming<E> theNaming;
		private final String theCreateSql;

		CreateAction(EntityCreator<? super E, E> creator, boolean reportInChanges) {
			theCreator = creator;
			this.reportInChanges = reportInChanges;
			theNaming = (TableNaming<E>) theTableNaming.get(creator.getEntityType().getName());
			StringBuilder sql = new StringBuilder();
			sql.append("INSERT INTO ");
			if (theSchemaName != null)
				sql.append(theSchemaName).append('.');
			sql.append(theNaming.getTableName());
			boolean[] firstValue = new boolean[] { true };
			QuickMap<String, Object> values = ((ConfigurableCreator<? super E, E>) creator).getFieldValues();
			for (int f = 0; f < values.keySize(); f++) {
				Object value = values.get(f);
				if (value == EntityUpdate.NOT_SET)
					continue;
				if(!(theNaming.getColumn(f) instanceof SerialColumn))
					continue;
				((SerialColumn<?>) theNaming.getColumn(f)).forEachColumn(column->{
					if (firstValue[0]) {
						sql.append(" (");
						firstValue[0] = false;
					} else
						sql.append(", ");
					sql.append(column.getName());
				});
			}
			if (!firstValue[0])
				sql.append(") VALUES (");
			firstValue[0] = true;
			for (int f = 0; f < values.keySize(); f++) {
				Object value = values.get(f);
				if (value == EntityUpdate.NOT_SET)
					continue;
				if(!(theNaming.getColumn(f) instanceof SerialColumn))
					continue;
				((SerialColumn<Object>) theNaming.getColumn(f)).forEachColumnValue(value, column->{
					if (firstValue[0])
						firstValue[0] = false;
					else
						sql.append(", ");
					column.writeValue(sql);
				});
			}
			sql.append(')');
			theCreateSql = sql.toString();
			// TODO Non-serial columns
		}

		@Override
		public SimpleEntity<E> execute(Statement stmt, BooleanSupplier canceled) throws SQLException, EntityOperationException {
			List<String> colNames = new ArrayList<>(theCreator.getEntityType().getFields().keySize() + 3);
			for (int i = 0; i < theCreator.getEntityType().getFields().keySize(); i++) {
				if (!(theNaming.getColumn(i) instanceof SerialColumn))
					continue;
				((SerialColumn<?>) theNaming.getColumn(i)).forEachColumn(col -> {
					colNames.add(col.getName());
				});
			}
			stmt.execute(theCreateSql, colNames.toArray(new String[colNames.size()]));
			SimpleEntity<E> entity;
			try (ResultSet rs = stmt.getGeneratedKeys()) {
				if (!rs.next())
					throw new EntityOperationException("No generated key set?");
				entity = buildEntity(theCreator.getEntityType(), rs, theNaming, Collections.emptyMap());
				if (rs.next())
					System.err.println("Multiple generated key sets?");
			}
			// TODO Non-serial columns
			if (reportInChanges)
				changed(new EntityChange.EntityExistenceChange<>(theCreator.getEntityType(), Instant.now(), true,
					BetterList.of(entity.getIdentity()), null));
			return entity;
		}
	}

	@Override
	public OperationResult<Long> count(EntityQuery<?> query, Object prepared) {
		if (prepared instanceof PreparedSqlOperation) {
			return ((PreparedSqlOperation<Object, Long>) prepared).executeAsync((PreparedQuery<Object>) query, //
				sqlE -> new EntityOperationException("Query failed: " + query, sqlE));
		} else {
			return theConnectionPool.connectAsync(new CountQueryAction<>(query), //
				sqlE -> new EntityOperationException("Query failed: " + query, sqlE));
		}
	}

	class CountQueryAction<E> implements SqlAction<Long> {
		private final String theCountSql;

		CountQueryAction(EntityQuery<E> query) {
			TableNaming<E> naming = (TableNaming<E>) theTableNaming.get(query.getEntityType().getName());
			StringBuilder sql = new StringBuilder();
			sql.append("SELECT COUNT(*) FROM ");
			if (theSchemaName != null)
				sql.append(theSchemaName).append('.');
			sql.append(naming.getTableName());
			Map<String, Join<?, ?, ?>> joins = new HashMap<>();
			if (query.getSelection() instanceof EntityCondition.All) {//
			} else {
				sql.append(" WHERE ");
				appendCondition(sql, query.getSelection(), naming, QuickMap.empty(), null, joins, false);
				// TODO Joins
			}
			theCountSql = sql.toString();
		}

		@Override
		public Long execute(Statement stmt, BooleanSupplier canceled) throws SQLException, EntityOperationException {
			try (ResultSet rs = stmt.executeQuery(theCountSql)) {
				if (!rs.next())
					return 0L;
				return rs.getLong(1);
			}
		}

		PreparedSqlAction<Long> prepare() {}
	}

	@Override
	public <E> OperationResult<Iterable<SimpleEntity<? extends E>>> query(EntityQuery<E> query, Object prepared) {
		if (prepared instanceof PreparedSqlOperation) {
			return ((PreparedSqlOperation<E, Iterable<SimpleEntity<? extends E>>>) prepared).executeAsync((PreparedQuery<E>) query, //
				sqlE -> new EntityOperationException("Query failed: " + query, sqlE));
		} else {
			return theConnectionPool.connectAsync(new CollectionQueryAction<>(query), //
				sqlE -> new EntityOperationException("Query failed: " + query, sqlE));
		}
	}

	class CollectionQueryAction<E> implements SqlAction<Iterable<SimpleEntity<? extends E>>> {
		private final EntityQuery<E> theQuery;
		private final TableNaming<E> theNaming;
		private final Map<String, Join<?, ?, ?>> theJoins;
		private final String theQuerySql;

		CollectionQueryAction(EntityQuery<E> query) {
			theQuery = query;
			theNaming = (TableNaming<E>) theTableNaming.get(query.getEntityType().getName());
			StringBuilder sql = new StringBuilder();
			sql.append("SELECT * FROM ");
			if (theSchemaName != null)
				sql.append(theSchemaName).append('.');
			sql.append(theNaming.getTableName());
			theJoins = new HashMap<>();
			if (query.getSelection() instanceof EntityCondition.All) {//
			} else {
				sql.append(" WHERE ");
				appendCondition(sql, query.getSelection(), theNaming, QuickMap.empty(), null, theJoins, false);
				// TODO Joins
			}
			theQuerySql = sql.toString();
		}

		@Override
		public Iterable<SimpleEntity<? extends E>> execute(Statement stmt, BooleanSupplier canceled)
			throws SQLException, EntityOperationException {
			List<SimpleEntity<? extends E>> entities = new ArrayList<>();
			try (ResultSet rs = stmt.executeQuery(theQuerySql)) {
				while (rs.next())
					entities.add(buildEntity(theQuery.getEntityType(), rs, theNaming, theJoins));
			}
			return entities;
		}

		PreparedSqlAction<Iterable<SimpleEntity<? extends E>>> prepare() {}
	}

	@Override
	public <E> long update(EntityUpdate<E> update, Object prepared, boolean reportInChanges) throws EntityOperationException {
		try {
			if (prepared instanceof PreparedSqlOperation)
				return ((PreparedSqlOperation<E, Long>) prepared).execute((PreparedUpdate<E>) update);
			else
				return theConnectionPool.connect(new UpdateAction<>(update, reportInChanges));
		} catch (SQLException e) {
			throw new EntityOperationException("Update failed: " + update, e);
		}
	}

	@Override
	public <E> OperationResult<Long> updateAsync(EntityUpdate<E> update, Object prepared, boolean reportInChanges) {
		if (prepared instanceof PreparedSqlOperation) {
			return ((PreparedSqlOperation<E, Long>) prepared).executeAsync((PreparedUpdate<E>) update, //
				sqlE -> new EntityOperationException("Update failed: " + update, sqlE));
		} else {
			return theConnectionPool.connectAsync(new UpdateAction<>(update, reportInChanges), //
				sqlE -> new EntityOperationException("Update failed: " + update, sqlE));
		}
	}

	abstract class ModificationAction<E> implements SqlAction<Long> {
		private final EntityModification<E> theModification;
		final boolean reportInChanges;
		private final TableNaming<E> theNaming;
		private final String theUpdateSql;
		private final String theQuerySql;
		private final Map<String, Join<?, ?, ?>> theJoins;

		ModificationAction(EntityModification<E> update, boolean reportInChanges) {
			theModification = update;
			this.reportInChanges = reportInChanges;
			theNaming = (TableNaming<E>) theTableNaming.get(update.getEntityType().getName());
			theJoins = new HashMap<>();

			StringBuilder updateSql = new StringBuilder();
			StringBuilder querySql = new StringBuilder();
			updateSql.append(getSqlActionName()).append(' ');
			querySql.append("SELECT * FROM ");
			if (theSchemaName != null) {
				updateSql.append(theSchemaName).append('.');
				querySql.append(theSchemaName).append('.');
			}
			querySql.append(theNaming.getTableName());
			updateSql.append(theNaming.getTableName());
			Map<String, Join<?, ?, ?>> joins = new HashMap<>();

			addSqlDetails(updateSql);
			if (update.getSelection() instanceof EntityCondition.All) {//
			} else {
				StringBuilder whereSql = new StringBuilder();
				whereSql.append(" WHERE ");
				appendCondition(whereSql, update.getSelection(), theNaming, QuickMap.empty(), null, joins, false);
				// TODO Joins
				updateSql.append(whereSql);
				querySql.append(whereSql);
			}

			theUpdateSql = updateSql.toString();
			theQuerySql = querySql.toString();
		}

		EntityModification<E> getModification() {
			return theModification;
		}

		TableNaming<E> getNaming() {
			return theNaming;
		}

		Map<String, Join<?, ?, ?>> getJoins() {
			return theJoins;
		}

		String getUpdateSql() {
			return theUpdateSql;
		}

		String getQuerySql() {
			return theQuerySql;
		}

		protected abstract String getSqlActionName();

		protected abstract void addSqlDetails(StringBuilder sql);

		protected abstract PreparedSqlAction<Long> prepare();
	}

	class UpdateAction<E> extends ModificationAction<E> {
		UpdateAction(EntityUpdate<E> update, boolean reportInChanges) {
			super(update, reportInChanges);
		}

		@Override
		public EntityUpdate<E> getModification() {
			return (EntityUpdate<E>) super.getModification();
		}

		@Override
		protected String getSqlActionName() {
			return "UPDATE";
		}

		@Override
		protected void addSqlDetails(StringBuilder sql) {
			sql.append(" SET ");
			QuickMap<String, Object> values = getModification().getFieldValues();
			boolean[] firstSet = new boolean[] { true };
			for (int f = 0; f < values.keySize(); f++) {
				if (values.get(f) == EntityUpdate.NOT_SET)
					continue;
				if (!(getNaming().getColumn(f) instanceof SerialColumn))
					throw new IllegalArgumentException(
						"Cannot use updates to modify non-serial field " + getModification().getEntityType().getFields().get(f));
				((SerialColumn<Object>) getNaming().getColumn(f)).forEachColumnValue(values.get(f), column -> {
					if (firstSet[0])
						firstSet[0] = false;
					else
						sql.append(", ");
					sql.append(column.getName()).append('=');
					column.writeValue(sql);
				});
			}
		}

		@Override
		public Long execute(Statement stmt, BooleanSupplier canceled) throws SQLException, EntityOperationException {
			if (canceled.getAsBoolean())
				return null;
			QuickMap<String, Object> values = getModification().getFieldValues();
			if (reportInChanges) {
				BetterSortedSet<EntityIdentity<E>> affected = new BetterTreeSet<>(false, EntityIdentity::compareTo);
				QuickMap<String, List<Object>> oldValues = values.keySet().createMap();
				for (int f = 0; f < values.keySize(); f++) {
					if (values.get(f) != EntityUpdate.NOT_SET)
						oldValues.put(f, new ArrayList<>());
				}
				long missed = 0;
				try (ResultSet rs = stmt.executeQuery(getQuerySql())) {
					while (rs.next()) {
						SimpleEntity<E> entity = buildEntity(getModification().getEntityType(), rs, getNaming(), getJoins());
						// TODO Invalid for sub-typed entities
						if (isDifferent(entity.getFields(), values)) {
							int index = affected.getElementsBefore(affected.addElement(entity.getIdentity(), false).getElementId());
							for (int f = 0; f < values.keySize(); f++) {
								Object targetValue = values.get(f);
								if (targetValue != EntityUpdate.NOT_SET)
									oldValues.get(f).add(index, entity.getFields().get(f));
							}
						} else
							missed++;
					}
				}
				if (affected.isEmpty())
					return Long.valueOf(0);
				if (canceled.getAsBoolean())
					return null;
				stmt.executeUpdate(getUpdateSql());
				List<EntityChange.FieldChange<E, ?>> fieldChanges = new ArrayList<>(oldValues.valueCount());
				for (int f = 0; f < values.keySize(); f++) {
					if (values.get(f) == EntityUpdate.NOT_SET)
						continue;
					fieldChanges.add(new EntityChange.FieldChange<>(
						(ObservableEntityFieldType<E, Object>) getModification().getEntityType().getFields().get(f), oldValues.get(f),
						values.get(f)));
				}
				boolean useSelection = affected.size() > 1 && (affected.size() * affected.size() <= missed)
					&& isConditionValidPostUpdate(getModification().getSelection(), values);
				if (canceled.getAsBoolean())
					return null;
				changed(new EntityChange.EntityFieldValueChange<>(getModification().getEntityType(), Instant.now(), affected, fieldChanges,
					useSelection ? getModification().getSelection() : null));
				return Long.valueOf(affected.size());
			} else
				return Long.valueOf(stmt.executeUpdate(getUpdateSql()));
		}

		@Override
		PreparedUpdateAction<E> prepare() {}
	}

	class PreparedUpdateAction<E> implements PreparedSqlAction<Long> {
	}

	@Override
	public <E> long delete(EntityDeletion<E> delete, Object prepared, boolean reportInChanges) throws EntityOperationException {
		try {
			if (prepared instanceof PreparedSqlOperation)
				return ((PreparedSqlOperation<E, Long>) prepared).execute((PreparedDeletion<E>) delete);
			else
				return theConnectionPool.connect(new DeleteAction<>(delete, reportInChanges));
		} catch (SQLException e) {
			throw new EntityOperationException("Delete failed: " + delete, e);
		}
	}

	@Override
	public <E> OperationResult<Long> deleteAsync(EntityDeletion<E> delete, Object prepared, boolean reportInChanges) {
		if (prepared instanceof PreparedSqlOperation) {
			return ((PreparedSqlOperation<E, Long>) prepared).executeAsync((PreparedDeletion<E>) delete, //
				sqlE -> new EntityOperationException("Delete failed: " + delete, sqlE));
		} else {
			return theConnectionPool.connectAsync(new DeleteAction<>(delete, reportInChanges), //
				sqlE -> new EntityOperationException("Delete failed: " + delete, sqlE));
		}
	}

	class DeleteAction<E> extends ModificationAction<E> {
		private final boolean hasNonSerialFields;

		DeleteAction(EntityDeletion<E> update, boolean reportInChanges) {
			super(update, reportInChanges);
			boolean nonSerial = false;
			for (int f = 0; f < getModification().getSelection().getEntityType().getFields().keySize(); f++) {
				if (getNaming().getColumn(f) instanceof EntryColumn) {
					nonSerial = true;
					break;
				}
			}
			hasNonSerialFields = nonSerial;
		}

		@Override
		EntityDeletion<E> getModification() {
			return (EntityDeletion<E>) super.getModification();
		}

		@Override
		protected String getSqlActionName() {
			return "DELETE FROM";
		}

		@Override
		protected void addSqlDetails(StringBuilder sql) {
		}

		@Override
		public Long execute(Statement stmt, BooleanSupplier canceled) throws SQLException, EntityOperationException {
			if (reportInChanges || hasNonSerialFields) {
				BetterSortedSet<EntityIdentity<E>> affected = new BetterTreeSet<>(false, EntityIdentity::compareTo);
				try (ResultSet rs = stmt.executeQuery(getQuerySql())) {
					while (rs.next()) {
						SimpleEntity<E> entity = buildEntity(getModification().getEntityType(), rs, getNaming(), getJoins());
						affected.add(entity.getIdentity());
					}
				}
				if (affected.isEmpty())
					return Long.valueOf(0);
				if (hasNonSerialFields) {
					StringBuilder sql = new StringBuilder();
					for (EntityIdentity<E> entity : affected) {
						for (int f = 0; f < getModification().getSelection().getEntityType().getFields().keySize(); f++) {
							if (!(getNaming().getColumn(f) instanceof EntryColumn))
								continue;
							EntryColumn<E, ?, ?> column = (EntryColumn<E, ?, ?>) getNaming().getColumn(f);
							sql.setLength(0);
							sql.append("DELETE FROM ");
							if (theSchemaName != null)
								sql.append(theSchemaName).append('.');
							sql.append(column.getTableName()).append(" WHERE ");
							boolean[] firstCol = new boolean[] { true };
							column.getOwnerColumn().forEachColumnValue(entity, col -> {
								if (firstCol[0])
									firstCol[0] = false;
								else
									sql.append(" AND ");
								sql.append(col.getName()).append('=');
								col.writeValue(sql);
							});
							stmt.executeUpdate(sql.toString());
						}
					}
				}
				stmt.executeUpdate(getUpdateSql().toString());
				if (reportInChanges)
					changed(
						new EntityChange.EntityExistenceChange<>(getModification().getEntityType(), Instant.now(), false, affected, null));
				return Long.valueOf(affected.size());
			} else
				return Long.valueOf(stmt.executeUpdate(getUpdateSql().toString()));
		}

		@Override
		PreparedDeleteAction<E> prepare();
	}

	class PreparedDeleteAction<E> implements PreparedSqlAction<Long> {
	}

	@Override
	public <V> ElementId updateCollection(BetterCollection<V> collection, CollectionOperationType changeType, ElementId element, V value,
		boolean reportInChanges) throws EntityOperationException {
		try {
			return doUpdateCollection((BackingList<Object, V>) collection, changeType, element, value, reportInChanges);
		} catch (SQLException e) {
			throw new EntityOperationException("Collection operation failed: ", e);
		}
	}

	private <E, V> ElementId doUpdateCollection(BackingList<E, V> collection, CollectionOperationType changeType, ElementId element,
		V value, boolean reportInChanges) throws SQLException, EntityOperationException {
		return theConnectionPool.connect((stmt, canceled) -> {
			StringBuilder sql = new StringBuilder();
			boolean[] first = new boolean[] { true };
			switch (changeType) {
			case add:
				int index;
				if (element != null && collection.getColumn().getIndexColumn() != null) {
					index = collection.getElementsBefore(element);
					sql.append("UPDATE ");
					if (theSchemaName != null)
						sql.append(theSchemaName).append('.');
					sql.append(collection.getColumn().getTableName()).append(" SET ").append(collection.getColumn().getIndexColumn())
					.append('=').append(collection.getColumn().getIndexColumn()).append("+1 WHERE ");
					boolean compoundId = collection.getColumn().getOwnerColumn().getColumnCount() > 1;
					if (compoundId)
						sql.append('(');
					first[0] = true;
					collection.getColumn().getOwnerColumn().forEachColumnValue(collection.getEntity(), col -> {
						if (first[0])
							first[0] = false;
						else
							sql.append(" AND ");
						sql.append(col.getName()).append('=');
						col.writeValue(sql);
					});
					if (compoundId)
						sql.append(')');
					if (index > 0)
						sql.append(" AND ").append(collection.getColumn().getIndexColumn()).append(">=").append(index);
					System.out.println(sql);
					stmt.executeUpdate(sql.toString());
					sql.setLength(0);
				} else
					index = collection.size();
				sql.append("INSERT INTO ");
				if (theSchemaName != null)
					sql.append(theSchemaName).append('.');
				sql.append(collection.getColumn().getTableName()).append(" (");
				first[0] = true;
				collection.getColumn().getOwnerColumn().forEachColumn(col -> {
					if (first[0])
						first[0] = false;
					else
						sql.append(", ");
					sql.append(col.getName());
				});
				if (collection.getColumn().getIndexColumn() != null)
					sql.append(", ").append(collection.getColumn().getIndexColumn());
				collection.getColumn().getValueColumn().forEachColumn(col -> {
					sql.append(", ").append(col.getName());
				});
				sql.append(") VALUES (");
				first[0] = true;
				collection.getColumn().getOwnerColumn().forEachColumnValue(collection.getEntity(), col -> {
					if (first[0])
						first[0] = false;
					else
						sql.append(", ");
					col.writeValue(sql);
				});
				if (collection.getColumn().getIndexColumn() != null)
					sql.append(", ").append(index);
				collection.getColumn().getValueColumn().forEachColumnValue(value, col -> {
					sql.append(", ");
					col.writeValue(sql);
				});
				sql.append(')');
				System.out.println(sql);
				stmt.executeUpdate(sql.toString());
				return collection.getWrapped().addElement(value, null, element, false).getElementId();
			case remove:
				if (collection.getColumn().getIndexColumn() != null)
					index = collection.getElementsBefore(element);
				else
					index = -1;
				sql.append("DELETE FROM ");
				if (theSchemaName != null)
					sql.append(theSchemaName).append('.');
				sql.append(collection.getColumn().getTableName()).append(" WHERE ");
				first[0] = true;
				collection.getColumn().getOwnerColumn().forEachColumnValue(collection.getEntity(), col -> {
					if (first[0])
						first[0] = false;
					else
						sql.append(" AND ");
					sql.append(col.getName()).append('=');
					col.writeValue(sql);
				});
				if (collection.getColumn().getIndexColumn() != null)
					sql.append(" AND ").append(collection.getColumn().getIndexColumn()).append('=').append(index);
				else {
					sql.append(" AND ");
					collection.getColumn().getValueColumn().forEachColumnValue(collection.getElement(element).get(), col -> {
						sql.append(col.getName()).append('=');
						col.writeValue(sql);
					});
				}
				System.out.println(sql);
				int count = stmt.executeUpdate(sql.toString());
				if (count != 1) {
					System.err.println("Expected 1 row removed, but removed " + count);
				}
				collection.getWrapped().mutableElement(element).remove();
				if (collection.getColumn().getIndexColumn() != null) {
					sql.setLength(0);
					index = collection.getElementsBefore(element);
					sql.append("UPDATE ");
					if (theSchemaName != null)
						sql.append(theSchemaName).append('.');
					sql.append(collection.getColumn().getTableName()).append(" SET ").append(collection.getColumn().getIndexColumn())
					.append('=').append(collection.getColumn().getIndexColumn()).append("-1 WHERE ");
					boolean compoundId = collection.getColumn().getOwnerColumn().getColumnCount() > 1;
					if (compoundId)
						sql.append('(');
					first[0] = true;
					collection.getColumn().getOwnerColumn().forEachColumnValue(collection.getEntity(), col -> {
						if (first[0])
							first[0] = false;
						else
							sql.append(" AND ");
						sql.append(col.getName()).append('=');
						col.writeValue(sql);
					});
					if (compoundId)
						sql.append(')');
					if (index > 0)
						sql.append(" AND ").append(collection.getColumn().getIndexColumn()).append(">").append(index);
					stmt.executeUpdate(sql.toString());
					System.out.println(sql);
					sql.setLength(0);
				} else
					index = -1;
				return element;
			case update:
				if (collection.getColumn().getIndexColumn() != null)
					index = collection.getElementsBefore(element);
				else
					index = -1;
				sql.append("UPDATE ");
				if (theSchemaName != null)
					sql.append(theSchemaName).append('.');
				sql.append(collection.getColumn().getTableName()).append(" SET ");
				first[0] = true;
				collection.getColumn().getValueColumn().forEachColumnValue(value, col -> {
					if (first[0])
						first[0] = false;
					else
						sql.append(", ");
					sql.append(col.getName()).append('=');
					col.writeValue(sql);
				});
				sql.append(" WHERE ");
				first[0] = true;
				collection.getColumn().getOwnerColumn().forEachColumnValue(collection.getEntity(), col -> {
					if (first[0])
						first[0] = false;
					else
						sql.append(" AND ");
					sql.append(col.getName()).append('=');
					col.writeValue(sql);
				});
				if (collection.getColumn().getIndexColumn() != null)
					sql.append(" AND ").append(collection.getColumn().getIndexColumn()).append('=').append(index);
				else {
					sql.append(" AND ");
					collection.getColumn().getValueColumn().forEachColumnValue(value, col -> {
						sql.append(col.getName()).append('=');
						col.writeValue(sql);
					});
				}
				count = stmt.executeUpdate(sql.toString());
				if (count != 1) {
					System.err.println("Expected 1 row updated, but updated " + count);
				}
				collection.getWrapped().mutableElement(element).set(value);
				System.out.println(sql);
				return element;
			case clear:
				sql.append("DELETE FROM ");
				if (theSchemaName != null)
					sql.append(theSchemaName).append('.');
				sql.append(collection.getColumn().getTableName()).append(" WHERE ");
				first[0] = true;
				collection.getColumn().getOwnerColumn().forEachColumnValue(collection.getEntity(), col -> {
					if (first[0])
						first[0] = false;
					else
						sql.append(" AND ");
					sql.append(col.getName()).append('=');
					col.writeValue(sql);
				});
				System.out.println(sql);
				count = stmt.executeUpdate(sql.toString());
				if (count != collection.size()) {
					System.err.println("Expected " + collection.size() + " row(s) removed, but removed " + count);
				}
				collection.getWrapped().clear();
				return null;
			}
			throw new IllegalStateException("" + changeType);
		});
	}

	@Override
	public <V> OperationResult<ElementId> updateCollectionAsync(BetterCollection<V> collection, CollectionOperationType changeType,
		ElementId element, V value, boolean reportInChanges) {
	}

	@Override
	public <K, V> ElementId updateMap(Map<K, V> collection, CollectionOperationType changeType, K key, V value, Runnable asyncResult) {}

	@Override
	public <K, V> ElementId updateMultiMap(MultiMap<K, V> collection, CollectionOperationType changeType, ElementId valueElement, K key,
		V value, Consumer<ElementId> asyncResult) {}

	@Override
	public List<EntityChange<?>> changes() {
		return theChanges.dumpAndClear();
	}

	@Override
	public List<Fulfillment<?>> loadEntityData(List<EntityLoadRequest<?>> loadRequests) throws EntityOperationException {
		try {
			return theConnectionPool.connect(new EntityDataLoadAction(loadRequests));
		} catch (SQLException e) {
			throw new EntityOperationException("Load requests failed: " + loadRequests, e);
		}
	}

	@Override
	public OperationResult<List<Fulfillment<?>>> loadEntityDataAsync(List<EntityLoadRequest<?>> loadRequests) {
		return theConnectionPool.connectAsync(new EntityDataLoadAction(loadRequests), //
			sqlE -> new EntityOperationException("Load requests failed: " + loadRequests, sqlE));
	}

	static final int MAX_RANGES_PER_QUERY = 25;
	static final int MAX_IDS_PER_QUERY = 100;

	class EntityDataLoadAction implements SqlAction<List<Fulfillment<?>>> {
		private final List<EntityLoadRequest<?>> theLoadRequests;

		EntityDataLoadAction(List<EntityLoadRequest<?>> loadRequests) {
			theLoadRequests = loadRequests;
		}

		@Override
		public List<Fulfillment<?>> execute(Statement stmt, BooleanSupplier canceled) throws SQLException, EntityOperationException {
			List<Fulfillment<?>> fulfillment = new ArrayList<>(theLoadRequests.size());
			for (EntityLoadRequest<?> request : theLoadRequests)
				fulfillment.add(satisfy(request, stmt));
			return fulfillment;
		}

		private <E> Fulfillment<E> satisfy(EntityLoadRequest<E> loadRequest, Statement stmt) throws SQLException {
			// TODO Support non-serial columns
			TableNaming<E> naming = (TableNaming<E>) theTableNaming.get(loadRequest.getType().getName());
			StringBuilder sql = new StringBuilder();
			sql.append("SELECT ");
			boolean[] firstField = new boolean[] { true };
			Map<String, Join<?, ?, ?>> joins = new HashMap<>();
			for (int i = 0; i < loadRequest.getType().getIdentityFields().keySize(); i++)
				addFieldRef(loadRequest.getType().getIdentityFields().get(i), naming, sql, joins, firstField);
			for (EntityValueAccess<? extends E, ?> field : loadRequest.getFields())
				addFieldRef(field, naming, sql, joins, firstField);
			SimpleEntity<E>[] results = new SimpleEntity[loadRequest.getEntities().size()];
			if (!firstField[0]) {
				// TODO Also need to join with subclass tables if any identities are sub-typed
				sql.append(" FROM ");
				if (theSchemaName != null)
					sql.append(theSchemaName).append('.');
				sql.append(naming.getTableName()).append(" WHERE ");
				if (loadRequest.getChange() != null && loadRequest.getChange().getCustomData() instanceof EntityCondition)
					appendCondition(sql, (EntityCondition<E>) loadRequest.getChange().getCustomData(), naming, QuickMap.empty(), null,
						joins, false);
				else {
					boolean firstEntity = true;
					for (EntityIdentity<? extends E> entity : loadRequest.getEntities()) {
						if (firstEntity)
							firstEntity = false;
						else
							sql.append(" OR ");
						if (entity.getFields().keySize() > 1)
							sql.append('(');
						firstField[0] = true;
						for (int i = 0; i < entity.getFields().keySize(); i++) {
							ObservableEntityFieldType<E, ?> field = loadRequest.getType().getIdentityFields().get(i);
							Column<?> column = naming.getColumn(field.getIndex());
							if (column instanceof SerialColumn) {
								((SerialColumn<Object>) column).forEachColumnValue(entity.getFields().get(i), col -> {
									if (firstField[0])
										firstField[0] = false;
									else
										sql.append(" AND ");
									sql.append(col.getName()).append('=');
									col.writeValue(sql);
								});
							}
						}
						if (entity.getFields().keySize() > 1)
							sql.append(')');
					}
				}

				try (ResultSet rs = stmt.executeQuery(sql.toString())) {
					while (rs.next()) {
						SimpleEntity<E> entity = buildEntity(loadRequest.getType(), rs, naming, joins);
						CollectionElement<EntityIdentity<E>> requested = loadRequest.getEntities().getElement(entity.getIdentity(), true);
						if (requested == null)
							continue;
						results[loadRequest.getEntities().getElementsBefore(requested.getElementId())] = entity;
					}
				}
			} else {
				for (int i = 0; i < loadRequest.getEntities().size(); i++)
					results[i] = new SimpleEntity<>(loadRequest.getEntities().get(i));
			}
			// TODO Check for missing results and do a secondary, identity-based query for the missing ones
			for (EntityValueAccess<? extends E, ?> field : loadRequest.getFields()) {
				if (!(field instanceof ObservableEntityFieldType)
					|| naming.getColumn((ObservableEntityFieldType<E, ?>) field) instanceof SerialColumn)
					continue;
				loadNonSerialField(loadRequest, (ObservableEntityFieldType<E, ?>) field, naming,
					(EntryColumn<E, ?, ?>) naming.getColumn((ObservableEntityFieldType<E, ?>) field), results, stmt);
			}
			return new Fulfillment<>(loadRequest, QommonsUtils.map(Arrays.asList(results), r -> r.getFields(), true));
		}

		private <E, T, V> void loadNonSerialField(EntityLoadRequest<E> loadRequest, ObservableEntityFieldType<E, ?> field,
			TableNaming<E> naming, EntryColumn<E, T, V> column, SimpleEntity<E>[] results, Statement stmt) throws SQLException {
			StringBuilder sql = new StringBuilder("SELECT ");
			StringBuilder order = new StringBuilder(" ORDER BY ");
			{
				int[] columnIndex = new int[1];
				column.getOwnerColumn().forEachColumn(col -> {
					if (columnIndex[0] > 0) {
						sql.append(", ");
						order.append(", ");
					}
					sql.append(col.getName());
					order.append(col.getName());
					columnIndex[0]++;
				});
				if (column.getIndexColumn() != null) {
					sql.append(", ").append(column.getIndexColumn());
					order.append(", ").append(column.getIndexColumn());
					columnIndex[0]++;
				}
				column.getValueColumn().forEachColumn(col -> {
					sql.append(", ").append(col.getName());
					columnIndex[0]++;
				});
			}
			sql.append(" FROM ");
			if (theSchemaName != null)
				sql.append(theSchemaName).append('.');
			sql.append(column.getTableName()).append(" WHERE ");
			List<BiTuple<String, int []>> queries;
			// See if we can grab everything in one query. This is possible if the list of entities is small
			// and either the owning entity has a simple identity (one field)
			// or the identities of all the entities to be loaded are equivalent but for one field
			if (loadRequest.getEntities().size() == 1) {
				boolean[] firstCol = new boolean[] { true };
				StringBuilder queryStr = new StringBuilder();
				column.getOwnerColumn().forEachColumnValue(loadRequest.getEntities().get(0), col -> {
					if (firstCol[0])
						firstCol[0] = false;
					else
						queryStr.append(" AND ");
					queryStr.append(col.getName()).append('=');
					col.writeValue(queryStr);
				});
				queries = Collections.singletonList(new BiTuple<>(queryStr.toString(), sequence(0, 1)));
			} else if (column.getOwnerColumn().getColumnCount() == 1) {
				// Single ID column
				ReferenceColumn<E> idColumn = column.getOwnerColumn();
				List<BiTuple<String[], int[]>> ranges = getContinuousRanges(loadRequest.getEntities(), naming,
					loadRequest.getType().getIdentityFields().get(0));
				if (ranges.size() == 1) {
					queries = Collections.singletonList(new BiTuple<>(//
						new StringBuilder().append(idColumn.getName()).append(">=").append(ranges.get(0).getValue1()[0])//
						.append(" AND ").append(idColumn.getName()).append("<=").append(ranges.get(0).getValue1()[1]).toString(), //
						sequence(0, loadRequest.getEntities().size())));
				} else if (Math.ceil(ranges.size() * 1.0 / MAX_RANGES_PER_QUERY) <= Math
					.ceil(loadRequest.getEntities().size() * 1.0 / MAX_IDS_PER_QUERY)) {
					IntList entities = new IntList();
					StringBuilder str = new StringBuilder();
					int querySize = ranges.size() / ((int) Math.ceil(ranges.size() / MAX_RANGES_PER_QUERY));
					queries = new ArrayList<>(querySize);
					for (int r = 0; r < ranges.size(); r++) {
						if (r % querySize == querySize - 1) {
							queries.add(new BiTuple<>(str.toString(), entities.toArray()));
							str.setLength(0);
							entities.clear();
						}
						if (str.length() > 0)
							str.append(" OR ");
						BiTuple<String[], int[]> range = ranges.get(r);
						if (range.getValue1().length == 1)
							str.append(idColumn.getName()).append('=').append(range.getValue1()[0]);
						else
							str.append('(').append(idColumn.getName()).append(">=").append(range.getValue1()[0])//
							.append(" AND ").append(idColumn.getName()).append("<=").append(range.getValue1()[1]).append(')');
						entities.addAll(range.getValue2());
					}
					queries.add(new BiTuple<>(str.toString(), entities.toArray()));
				} else {
					// Using IN clause(s) will be simpler and likely be faster
					int querySize = loadRequest.getEntities().size()
						/ ((int) Math.ceil(loadRequest.getEntities().size() / MAX_IDS_PER_QUERY));
					queries = new ArrayList<>(querySize);
					StringBuilder str = new StringBuilder(idColumn.getName()).append(" IN (");
					int start = 0;
					for (int i = 0; i < loadRequest.getEntities().size(); i++) {
						int mod = i % querySize;
						if (mod == querySize - 1) {
							str.append(')');
							queries.add(new BiTuple<>(str.toString(), sequence(start, i)));
							str.setLength(0);
							start = i;
						} else if (mod != 0)
							str.append(", ");
						idColumn.forEachColumnValue(loadRequest.getEntities().get(i), col -> col.writeValue(str));
					}
					str.append(')');
					queries.add(new BiTuple<>(str.toString(), sequence(start, loadRequest.getEntities().size())));
				}
			} else {
				// TODO Multiple identity fields
				throw new UnsupportedOperationException();
			}
			Map<EntityIdentity<E>, Integer> sortedIds = new TreeMap<>();
			for (int i = 0; i < loadRequest.getEntities().size(); i++) {
				results[i].set(field.getIndex(), column.createInitialValue(loadRequest.getEntities().get(i)));
				sortedIds.put(loadRequest.getEntities().get(i), i);
			}
			for(BiTuple<String, int []> query : queries){
				try (ResultSet rs = stmt.executeQuery(sql + query.getValue1() + order)) {
					Iterator<Map.Entry<EntityIdentity<E>, Integer>> entityIter = sortedIds.entrySet().iterator();
					Map.Entry<EntityIdentity<E>, Integer> entity = entityIter.next();
					while (rs.next()) {
						while (!matches(entity.getKey(), rs, naming))
							entity = entityIter.next();
						int columnIdx = column.getOwnerColumn().getColumnCount();
						if (column.getIndexColumn() != null)
							columnIdx++;
						column.addValue(//
							(T) results[entity.getValue()].getFields().get(field.getIndex()), //
							rs, columnIdx + 1);
					}
				}
			}
		}

		private <E> boolean matches(EntityIdentity<E> key, ResultSet rs, TableNaming<E> naming) throws SQLException {
			int columnIdx = 0;
			for (ObservableEntityFieldType<E, ?> idField : key.getEntityType().getIdentityFields().allValues()) {
				SerialColumn<?> column = (SerialColumn<?>) naming.getColumn(idField.getIndex());
				Object serial = column.deserialize(rs, columnIdx + 1);
				columnIdx += column.getColumnCount();
				if (!Objects.equals(key.getFields().get(idField.getIdIndex()), serial))
					return false;
			}
			return true;
		}

		private int[] sequence(int start, int end) {
			int[] seq = new int[end - start];
			for (int i = 0; i < seq.length; i++)
				seq[i] = start + i;
			return seq;
		}

		private <E, I extends Comparable<I>> List<BiTuple<String[], int[]>> getContinuousRanges(List<EntityIdentity<E>> entities,
			TableNaming<E> naming, ObservableEntityFieldType<E, ?> idField) {
			SerialColumn<?> idColumn = (SerialColumn<?>) naming.getColumn(idField.getIndex());
			SimpleColumn<I> simpleIdColumn;
			{
				if (idColumn instanceof SimpleColumn)
					simpleIdColumn = (SimpleColumn<I>) idColumn;
				else {
					ValueHolder<SimpleColumn<I>> idColHolder = new ValueHolder<>();
					idColumn.forEachColumn(col -> idColHolder.accept((SimpleColumn<I>) col));
					simpleIdColumn = idColHolder.get();
				}
			}
			if (!simpleIdColumn.getType().testsAdjacent())
				return null; // No adjacent test, can't detect ranges
			int idIndex = idField.getIdIndex();
			// Get the sorted list of IDs to select
			List<EntityIdentity<? extends E>> sorted = new ArrayList<>(entities.size());
			for (EntityIdentity<? extends E> entity : entities) {
				int idx = Collections.<EntityIdentity<? extends E>> binarySearch(sorted, entity, (e1, e2) -> {
					I id1 = (I) e1.getFields().get(idIndex);
					I id2 = (I) e2.getFields().get(idIndex);
					return id1.compareTo(id2);
				});
				sorted.add(-idx - 1, entity); // Assume no duplicates, index will be negative
			}
			// Group the IDs into ranges
			if (sorted.size() == 1) {
				String serialized = simpleIdColumn.getType().serialize((I) sorted.get(0).getFields().get(idIndex), new StringBuilder())
					.toString();
				return Collections.singletonList(new BiTuple<>(new String[] { serialized }, sequence(0, entities.size())));
			}
			List<BiTuple<String[], int[]>> ranges = new ArrayList<>();
			IntList rangeEntities = new IntList();
			rangeEntities.add(0);
			I start = (I) sorted.get(0).getFields().get(idIndex), end = null;
			for (int i = 1; i < sorted.size(); i++) {
				I id = (I) sorted.get(i).getFields().get(idIndex);
				if (!simpleIdColumn.getType().isAdjacent(start, id)) {
					if (end == null)
						ranges.add(new BiTuple<>(//
							new String[] { simpleIdColumn.getType().serialize(start, new StringBuilder()).toString() }, //
							rangeEntities.toArray()));
					else
						ranges.add(new BiTuple<>(new String[] { //
							simpleIdColumn.getType().serialize(start, new StringBuilder()).toString(),
							simpleIdColumn.getType().serialize(end, new StringBuilder()).toString() }, //
							rangeEntities.toArray()));
					rangeEntities.clear();
					start = id;
					end = null;
				} else
					end = id;
				rangeEntities.add(i);
			}
			return ranges;
		}
	}

	void changed(EntityChange<?> change) {
		theChanges.add(change, false);
	}

	protected <E> TableNaming<E> generateNaming(ObservableEntityType<E> type) {
		Column<?>[] columns = new Column[type.getFields().keySize()];
		for (int c = 0; c < columns.length; c++)
			columns[c] = generateColumn(type.getFields().get(c).getName(), type.getFields().get(c), type.getFields().get(c).getFieldType(),
				false);
		return new TableNaming<>(type, sqlIfyName(type.getName()), columns);
	}

	protected Column<?> generateColumn(String fieldName, ObservableEntityFieldType<?, ?> field, TypeToken<?> type, boolean onlySerial) {
		if (fieldName.length() == 0)
			throw new IllegalStateException("Empty field name not allowed");
		ObservableEntityType<?> targetEntity = theEntitySet.getEntityType(TypeTokens.getRawType(type));
		if (targetEntity != null)
			return generateReferenceColumn(fieldName, targetEntity);
		Class<?> raw = TypeTokens.getRawType(type);
		if (Collection.class.isAssignableFrom(raw)) {
			if (onlySerial)
				throw new IllegalArgumentException("Compound collection fields not supported: " + field);
			String tableName = sqlIfyName(field.getOwnerType().getName()) + "_" + sqlIfyName(field.getName());
			ReferenceColumn<?> ownerColumn = generateReferenceColumn(field.getOwnerType().getName(), field.getOwnerType());
			SerialColumn<?> valueColumn = (SerialColumn<?>) generateColumn(StringUtils.singularize(fieldName), field,
				type.resolveType(Collection.class.getTypeParameters()[0]), true);
			boolean sorted = SortedSet.class.isAssignableFrom(raw);
			return new CollectionColumn<>(sqlIfyName(fieldName), tableName, //
				sorted ? null : "index", //
					ownerColumn, valueColumn, sorted);
		} else
			return new SimpleColumn<>(sqlIfyName(fieldName), theTypeSupport.getColumnSupport(type, null), field.getIdIndex() < 0);
	}

	protected ReferenceColumn<?> generateReferenceColumn(String fieldName, ObservableEntityType<?> target) {
		SerialColumn<?>[] idColumns = new SerialColumn[target.getIdentityFields().keySize()];
		if (idColumns.length == 1)
			idColumns[0] = (SerialColumn<?>) generateColumn(fieldName, target.getIdentityFields().get(0),
				target.getIdentityFields().get(0).getFieldType(), true);
		else {
			for (int i = 0; i < idColumns.length; i++) {
				ObservableEntityFieldType<?, ?> refField = target.getIdentityFields().get(i);
				idColumns[i] = (SerialColumn<?>) generateColumn(fieldName + '_' + refField.getName(), refField, refField.getFieldType(),
					true);
			}
		}
		return new ReferenceColumn<>(sqlIfyName(fieldName), target, idColumns);
	}

	protected static String sqlIfyName(String javaName) {
		return StringUtils.parseByCase(javaName, true).toCaseScheme(false, false, "_").toUpperCase();
	}

	private <E, F> F deserialize(ObservableEntityFieldType<E, F> field, TableNaming<E> naming, ResultSet rs, int column)
		throws SQLException {
		return ((SerialColumn<F>) naming.getColumn(field)).deserialize(rs, column + 1);
	}

	private <E> SimpleEntity<E> buildEntity(ObservableEntityType<E> type, ResultSet results, TableNaming<E> naming,
		Map<String, Join<?, ?, ?>> joins) throws SQLException {
		EntityIdentity.Builder<E> idBuilder = type.buildId();
		ResultSetMetaData rsmd = results.getMetaData();
		for (int i = 0; i < rsmd.getColumnCount(); i++) {
			EntityValueAccess<E, ?> field = getField(rsmd, i, naming);
			if (field instanceof ObservableEntityFieldType) {
				ObservableEntityFieldType<E, ?> f = (ObservableEntityFieldType<E, ?>) field;
				if (f.getIdIndex() >= 0)
					idBuilder.with(f.getIdIndex(), deserializeField((ObservableEntityFieldType<E, ?>) field, naming, results, i));
			}
		}
		SimpleEntity<E> entity = new SimpleEntity<>(idBuilder.build());
		for (int i = 0; i < rsmd.getColumnCount(); i++) {
			EntityValueAccess<E, ?> field = getField(rsmd, i, naming);
			if (field instanceof ObservableEntityFieldType && ((ObservableEntityFieldType<E, ?>) field).getIdIndex() >= 0)
				continue;
			deserializeField(field, //
				entity::set, naming, results, i);
		}
		return entity;
	}

	private <E> EntityValueAccess<E, ?> getField(ResultSetMetaData rsmd, int column, TableNaming<E> naming) throws SQLException {
		if (rsmd.getTableName(column + 1).equals(naming.getTableName())) {
			return naming.getType().getFields().get(naming.getField(rsmd.getColumnName(column + 1)));
		} else
			throw new UnsupportedOperationException("TODO Chain selection is not supported yet"); // TODO
	}

	private <E> Object deserializeField(ObservableEntityFieldType<E, ?> field, TableNaming<E> naming, ResultSet rs, int column)
		throws SQLException {
		return deserialize((ObservableEntityFieldType<E, Object>) field, naming, rs, column);
	}

	private <E> Object deserializeField(EntityValueAccess<E, ?> field, BiConsumer<Integer, Object> setField, TableNaming<E> naming,
		ResultSet rs, int column) throws SQLException {
		if (field instanceof ObservableEntityFieldType) {
			Object fieldValue = deserialize((ObservableEntityFieldType<E, Object>) field, naming, rs, column);
			setField.accept(((ObservableEntityFieldType<E, ?>) field).getIndex(), fieldValue);
			return fieldValue;
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
				return updateValues.get(((ObservableEntityFieldType<?, ?>) field).getIndex()) == EntityUpdate.NOT_SET;
			else
				return updateValues.get(((EntityChainAccess<?, ?>) field).getFieldSequence().getFirst().getIndex()) == EntityUpdate.NOT_SET;
		} else if (condition instanceof CompositeCondition) {
			for (EntityCondition<?> component : ((CompositeCondition<?>) condition).getConditions()) {
				if (!isConditionValidPostUpdate(component, updateValues))
					return false;
			}
			return true;
		} else
			throw new IllegalStateException("Unrecognized condition type: " + condition.getClass().getName());
	}

	private <F> void addFieldRef(EntityValueAccess<?, F> field, TableNaming<?> naming, StringBuilder sql, Map<String, Join<?, ?, ?>> joins,
		boolean[] firstColumn) {
		if (field.getSourceEntity().equals(naming.getType())) {
			if (field instanceof ObservableEntityFieldType) {
				Column<F> column = (Column<F>) naming.getColumn(((ObservableEntityFieldType<?, ?>) field).getIndex());
				if (!(column instanceof SerialColumn))
					return;
				((SerialColumn<F>) column).forEachColumn(col -> {
					if (firstColumn[0])
						firstColumn[0] = false;
					else
						sql.append(", ");
					sql.append(col.getName());
				});
			} else
				throw new UnsupportedOperationException("TODO Chain selection is not supported yet"); // TODO
		} else
			throw new UnsupportedOperationException("TODO Type hierarchy is not supported yet"); // TODO
	}

	private <E> StringBuilder appendCondition(StringBuilder sql, EntityCondition<E> selection, TableNaming<E> naming,
		QuickMap<String, IntList> variableLocations, int[] lastLocation, Map<String, Join<?, ?, ?>> joins, boolean inner) {
		if (selection instanceof ValueCondition) {
			ValueCondition<E, ?> vc = (ValueCondition<E, ?>) selection;
			if (vc.getField() instanceof ObservableEntityFieldType) {
				ObservableEntityFieldType<E, Object> field = (ObservableEntityFieldType<E, Object>) vc.getField();
				Column<Object> column = (Column<Object>) naming.getColumn(field.getIndex());
				boolean compound = false;
				if (!(column instanceof SerialColumn)) {
					throw new IllegalArgumentException("Conditions on non-serial columns unsupported: " + field);
				} else if (((SerialColumn<Object>) column).getColumnCount() != 1) {
					if (vc.getComparison() != 0)
						throw new IllegalArgumentException("Non-equality comparisons on compound columns unsupported: " + field);
					compound = true;
				}
				String symbol;
				if (vc.getComparison() < 0) {
					if (vc.isWithEqual())
						symbol = "<=";
					else
						symbol = "<";
				} else if (vc.getComparison() > 0) {
					if (vc.isWithEqual())
						symbol = ">=";
					else
						symbol = ">";
				} else if (vc instanceof LiteralCondition && ((LiteralCondition<E, ?>) vc).getValue() == null) {
					if (!vc.isWithEqual())
						symbol = "IS NOT";
					else
						symbol = "IS";
				} else if (vc.isWithEqual())
					symbol = "=";
				else
					symbol = "<>";
				if (compound && inner)
					sql.append('(');
				if (vc instanceof LiteralCondition) {
					Object value = ((LiteralCondition<E, ?>) vc).getValue();
					boolean[] first = new boolean[] { true };
					((SerialColumn<Object>) column).forEachColumnValue(value, col -> {
						if (first[0])
							first[0] = false;
						else if (vc.isWithEqual())
							sql.append(" AND");
						else
							sql.append(" OR");
						sql.append(' ').append(col.getName()).append(symbol);
						col.writeValue(sql);
					});
				} else {
					boolean[] first = new boolean[] { true };
					((SerialColumn<Object>) column).forEachColumn(col -> {
						if (first[0])
							first[0] = false;
						else if (vc.isWithEqual())
							sql.append(" AND");
						else
							sql.append(" OR");
						sql.append(' ').append(col.getName()).append(symbol).append('?');
						variableLocations.get(((VariableCondition<E, ?>) vc).getVariable().getName()).add(lastLocation[0]);
						lastLocation[0]++;
					});
				}
				if (compound && inner)
					sql.append(')');
			} else
				throw new UnsupportedOperationException("TODO Chain selection is not supported yet"); // TODO
		} else if (selection instanceof CompositeCondition) {
			String name = selection instanceof OrCondition ? "OR" : "AND";
			if (inner)
				sql.append('(');
			boolean firstComponent = true;
			for (EntityCondition<E> component : ((CompositeCondition<E>) selection).getConditions()) {
				if (firstComponent)
					firstComponent = false;
				else
					sql.append(' ').append(name).append(' ');
				appendCondition(sql, component, naming, variableLocations, lastLocation, joins, true);
			}
			if (inner)
				sql.append(')');
		} else
			throw new IllegalStateException("Unrecognized condition type: " + selection.getClass().getName());
		return sql;
	}

	private void writeTableIfAbsent(ObservableEntityType<?> type, TableNaming<?> naming) throws EntityOperationException {
		try {
			theConnectionPool.<Void> connect((stmt, canceled) -> {
				boolean schemaPresent;
				StringBuilder sql = new StringBuilder();
				if (theSchemaName == null)
					schemaPresent = true;
				else {
					sql.append("SELECT * FROM INFORMATION_SCHEMA.SCHEMATA WHERE LOWER(SCHEMA_NAME)='").append(theSchemaName.toLowerCase())
					.append("'");
					try (ResultSet rs = stmt.executeQuery(sql.toString())) {
						schemaPresent = rs.next();
					}
				}
				if (!schemaPresent)
					stmt.execute("CREATE SCHEMA " + theSchemaName);
				sql.setLength(0);
				sql.append("SELECT * FROM INFORMATION_SCHEMA.TABLES WHERE ");
				if(theSchemaName==null)
					sql.append("TABLE_SCHEMA IS NULL");
				else
					sql.append("LOWER(TABLE_SCHEMA)='").append(theSchemaName).append("'");
				sql.append(" AND LOWER(TABLE_NAME)='").append(naming.getTableName().toLowerCase()).append("'");
				try (ResultSet rs = stmt.executeQuery(sql.toString())) {
					if (rs.next())
						return null;
				}
				sql.setLength(0);
				sql.append("CREATE TABLE ");
				if (theSchemaName != null)
					sql.append(theSchemaName).append('.');
				sql.append(naming.getTableName()).append('(');
				boolean [] first=new boolean[]{true};
				for (int c = 0; c < type.getFields().keySize(); c++) {
					Column<?> col = naming.getColumn(c);
					if(col instanceof SerialColumn)
						((SerialColumn<?>) col).forEachColumn(column->{
							if(first[0])
								first[0]=false;
							else
								sql.append(',');
							sql.append("\n\t").append(column.getName()).append(' ').append(column.getType().getTypeName());
							column.writeConstraints(sql);
						});
					// Auto-increment the last ID field if supported
					if (type.getFields().get(c).getIdIndex() == type.getIdentityFields().keySize() - 1
						&& theTypeSupport.getAutoIncrement() != null)
						sql.append(' ').append(theTypeSupport.getAutoIncrement());
				}
				sql.append("\n);");
				System.out.println("Creating table for " + type + ": " + sql);
				stmt.execute(sql.toString());
				for (int c = 0; c < type.getFields().keySize(); c++) {
					Column<?> col = naming.getColumn(c);
					if (col instanceof SerialColumn)
						continue;
					EntryColumn<?, ?, ?> entryCol = (EntryColumn<?, ?, ?>) col;
					sql.setLength(0);
					sql.append("CREATE TABLE ");
					if (theSchemaName != null)
						sql.append(theSchemaName).append('.');
					sql.append(entryCol.getTableName()).append('(');
					first[0] = true;
					entryCol.getOwnerColumn().forEachColumn(column -> {
						if (first[0])
							first[0] = false;
						else
							sql.append(',');
						sql.append("\n\t").append(column.getName()).append(' ').append(column.getType().getTypeName());
						column.writeConstraints(sql);
					});
					if (entryCol.getIndexColumn() != null)
						sql.append(",\n\tINDEX INTEGER NOT NULL");
					entryCol.getValueColumn().forEachColumn(column -> {
						sql.append(",\n\t").append(column.getName()).append(' ').append(column.getType().getTypeName());
						column.writeConstraints(sql);
					});
					sql.append(",\n\n\tPRIMARY KEY (");
					first[0] = true;
					entryCol.getOwnerColumn().forEachColumn(column -> {
						if (first[0])
							first[0] = false;
						else
							sql.append(", ");
						sql.append(column.getName());
					});
					if (entryCol.getIndexColumn() != null)
						sql.append(", ").append(entryCol.getIndexColumn());
					else
						entryCol.getValueColumn().forEachColumn(column -> sql.append(", ").append(column.getName()));
					sql.append("),\n\tFOREIGN KEY(");
					first[0] = true;
					entryCol.getOwnerColumn().forEachColumn(column -> {
						if (first[0])
							first[0] = false;
						else
							sql.append(", ");
						sql.append(column.getName());
					});
					sql.append(") REFERENCES ").append(naming.getTableName()).append('(');
					first[0] = true;
					for (ObservableEntityFieldType<?, ?> field : type.getIdentityFields().allValues()) {
						((SerialColumn<?>) naming.getColumn(field.getIndex())).forEachColumn(column -> {
							if (first[0])
								first[0] = false;
							else
								sql.append(", ");
							sql.append(column.getName());
						});
					}
					sql.append(")\n);");
					System.out.println("Creating table for " + type.getFields().get(c) + ": " + sql);
					stmt.execute(sql.toString());
				}
				return null;
			});
		} catch (SQLException e) {
			throw new EntityOperationException("Could not create table for entity " + type, e);
		}
	}
}
