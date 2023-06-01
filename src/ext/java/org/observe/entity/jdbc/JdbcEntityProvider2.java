package org.observe.entity.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.observe.config.OperationResult;
import org.observe.entity.ConfigurableCreator;
import org.observe.entity.ConfigurableDeletion;
import org.observe.entity.ConfigurableOperation;
import org.observe.entity.ConfigurableQuery;
import org.observe.entity.ConfigurableUpdate;
import org.observe.entity.EntityChainAccess;
import org.observe.entity.EntityChange;
import org.observe.entity.EntityCondition;
import org.observe.entity.EntityCondition.CompositeCondition;
import org.observe.entity.EntityCondition.ValueCondition;
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
import org.observe.entity.jdbc.ConnectionPool.PreparedSqlOperation;
import org.observe.entity.jdbc.DbDialect.EntityTable;
import org.observe.entity.jdbc.DbDialect.JoinedTable;
import org.observe.entity.jdbc.JdbcTypesSupport.JdbcColumn;
import org.observe.entity.jdbc.JdbcFieldRepresentation.Column;
import org.observe.entity.jdbc.JdbcFieldRepresentation.JoinedField;
import org.observe.entity.jdbc.JdbcFieldRepresentation.OverriddenField;
import org.observe.entity.jdbc.JdbcFieldRepresentation.ReferenceField;
import org.observe.entity.jdbc.JdbcFieldRepresentation.SerialField;
import org.observe.entity.jdbc.JdbcFieldRepresentation.SimpleField;
import org.observe.util.TypeTokens;
import org.qommons.StringUtils;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterCollections;
import org.qommons.collect.BetterList;
import org.qommons.collect.ElementId;
import org.qommons.collect.ListenerList;
import org.qommons.collect.MultiMap;
import org.qommons.collect.QuickSet.QuickMap;
import org.qommons.collect.StampedLockingStrategy;
import org.qommons.tree.BetterTreeList;

import com.google.common.reflect.TypeToken;

/** An observable entity provider implementation backed by a JDBC-accessed relational database */
public class JdbcEntityProvider2 implements ObservableEntityProvider {
	protected static class SimpleFieldImpl<T> implements SimpleField<T> {
		private final String theName;
		private final JdbcColumn<T> type;
		private final boolean isNullable;

		SimpleFieldImpl(String name, JdbcColumn<T> type, boolean nullable) {
			theName = name;
			this.type = type;
			isNullable = nullable;
		}

		public JdbcColumn<T> getType() {
			return type;
		}

		public void writeConstraints(StringBuilder str) {
			if (!isNullable)
				str.append(" NOT NULL");
		}

		@Override
		public JdbcColumn<T> getColumn() {
			return type;
		}

		@Override
		public String getName() {
			return theName;
		}

		@Override
		public String toString() {
			return type + "." + theName;
		}
	}

	protected static class ReferenceFieldImpl<E> implements ReferenceField<E> {
		private final ObservableEntityType<E> theReferenceType;
		private final QuickMap<String, SerialField<?>> idColumns;

		ReferenceFieldImpl(ObservableEntityType<E> referenceType, QuickMap<String, SerialField<?>> idColumns) {
			theReferenceType = referenceType;
			this.idColumns = idColumns;
		}

		@Override
		public ObservableEntityType<E> getReferenceType() {
			return theReferenceType;
		}

		@Override
		public QuickMap<String, SerialField<?>> getIdColumns() {
			return idColumns;
		}

		@Override
		public String toString() {
			return theReferenceType.toString();
		}
	}

	protected static abstract class JoinedFieldImpl<E, V> implements JoinedField<E, V> {
		private final String theTableName;
		private final String theIndexColumn;
		private final ReferenceField<E> ownerColumn;
		private final SerialField<V> valueColumn;

		JoinedFieldImpl(String tableName, String indexColumn, ReferenceField<E> ownerColumn, SerialField<V> valueColumn) {
			theTableName = tableName;
			theIndexColumn = indexColumn;
			this.ownerColumn = ownerColumn;
			this.valueColumn = valueColumn;
		}

		@Override
		public String getTableName() {
			return theTableName;
		}

		@Override
		public String getIndexColumn() {
			return theIndexColumn;
		}

		@Override
		public SerialField<E> getOwner() {
			return ownerColumn;
		}

		@Override
		public SerialField<V> getValue() {
			return valueColumn;
		}

		@Override
		public String toString() {
			return theTableName;
		}
	}

	static class BackingList<E, V> extends BetterCollections.UnmodifiableBetterList<V> {
		private final EntityIdentity<E> theEntity;
		private final CollectionColumn<E, V> theColumn;
		private final JoinedTable<E, V> theTable;

		public BackingList(EntityIdentity<E> entity, CollectionColumn<E, V> column, JoinedTable<E, V> table) {
			super(new BetterTreeList<>(false));
			theEntity = entity;
			theColumn = column;
			theTable = table;
		}

		@Override
		protected BetterList<V> getWrapped() {
			return (BetterList<V>) super.getWrapped();
		}

		EntityIdentity<E> getEntity() {
			return theEntity;
		}

		JoinedTable<E, V> getTable() {
			return theTable;
		}

		CollectionColumn<E, V> getColumn() {
			return theColumn;
		}
	}

	protected static class CollectionColumn<E, V> extends JoinedFieldImpl<E, V> {
		private final boolean isSorted;

		CollectionColumn(String tableName, String indexColumn, ReferenceField<E> ownerColumn, SerialField<V> valueColumn, boolean sorted) {
			super(tableName, indexColumn, ownerColumn, valueColumn);
			isSorted = sorted;
		}

		public boolean isSorted() {
			return isSorted;
		}

		@Override
		public Object createInitialValue(EntityIdentity<E> entity, JoinedTable<E, V> table) {
			return new BackingList<>(entity, this, table);
		}

		@Override
		public void addValue(Object value, ResultSet rs, String qualifier) throws SQLException {
			((BackingList<E, V>) value).getWrapped().add(//
				getValue().deserialize(rs, qualifier));
		}
	}

	protected static class OverriddenFieldImpl<F> implements OverriddenField<F> {
		private final List<JdbcFieldRepresentation<? super F>> theOverrides;

		OverriddenFieldImpl(int size) {
			theOverrides = new ArrayList<>(size);
		}

		@Override
		public List<JdbcFieldRepresentation<? super F>> getOverrides() {
			return theOverrides;
		}
	}

	private final StampedLockingStrategy theLocker;
	private final ConnectionPool theConnectionPool;
	private final JdbcTypesSupport theTypeSupport;
	private final DbDialect theDialect;
	private final Map<ObservableEntityType<?>, EntityTable<?>> theTables;
	private final String theSchemaName;
	private final ListenerList<EntityChange<?>> theChanges;
	private final boolean installSchema;
	private ObservableEntityDataSet theEntitySet;

	public JdbcEntityProvider2(StampedLockingStrategy locker, JdbcTypesSupport typeSupport, DbDialect dialect,
		ConnectionPool connectionPool, String schemaName, boolean installSchema) {
		theLocker = locker;
		theConnectionPool = connectionPool;
		theTypeSupport = typeSupport;
		theDialect = dialect;
		theSchemaName = schemaName;
		theTables = new HashMap<>();
		theChanges = ListenerList.build().build();
		this.installSchema = installSchema;
	}

	@Override
	public void install(ObservableEntityDataSet entitySet) throws EntityOperationException {
		theEntitySet = entitySet;
		// TODO Need to fill in the overrides field in OverriddenField
		for (ObservableEntityType<?> type : entitySet.getEntityTypes()) {
			QuickMap<String, JdbcFieldRepresentation<?>> fields = type.getFields().keySet().createMap();
			for (int f = 0; f < type.getFields().keySize(); f++)
				fields.put(f, representField(type.getFields().get(f)));
			theTables.put(type, theDialect.generateTable(type, theSchemaName + "." + sqlIfyName(type.getName()), fields.unmodifiable()));
		}
		theDialect.initialize(theTables);
		if (installSchema && createSchemaIfAbsent()) {
			for (EntityTable<?> table : theTables.values())
				createTable(table);
			for (EntityTable<?> table : theTables.values())
				createTableLinks(table);
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

	protected <E> EntityTable<E> getTable(ObservableEntityType<E> type) {
		return (EntityTable<E>) theTables.get(type);
	}

	@Override
	public <E> SimpleEntity<E> create(EntityCreator<? super E, E> creator, Object prepared, boolean reportInChanges)
		throws EntityOperationException {
		try {
			return theConnectionPool.connect((stmt, canceled) -> {
				return _create(creator, prepared, reportInChanges, stmt, canceled);
			});
		} catch (SQLException e) {
			throw new EntityOperationException("Create failed: " + creator, e);
		}
	}

	@Override
	public <E> OperationResult<SimpleEntity<E>> createAsync(EntityCreator<? super E, E> creator, Object prepared, boolean reportInChanges) {
		return theConnectionPool.connectAsync((stmt, canceled) -> {
			return _create(creator, prepared, reportInChanges, stmt, canceled);
		}, sqlE -> new EntityOperationException("Create failed: " + creator, sqlE));
	}

	private <E> SimpleEntity<E> _create(EntityCreator<?, E> creator, Object prepared, boolean reportInChanges, Statement stmt,
		BooleanSupplier canceled) throws SQLException, EntityOperationException {
		SimpleEntity<E> entity = getTable(creator.getEntityType()).create((ConfigurableCreator<?, E>) creator, stmt, canceled);
		if (reportInChanges)
			changed(new EntityChange.EntityExistenceChange<>(creator.getType(), Instant.now(), true, BetterList.of(entity.getIdentity()),
				null));
		return entity;
	}

	@Override
	public OperationResult<Long> count(EntityQuery<?> query, Object prepared) {
		return _count(query, prepared);
	}

	private <E> OperationResult<Long> _count(EntityQuery<E> query, Object prepared) {
		return theConnectionPool.connectAsync((stmt, canceled) -> {
			return getTable(query.getEntityType()).count((ConfigurableQuery<E>) query, stmt, canceled);
		}, sqlE -> new EntityOperationException("Query failed: " + query, sqlE));
	}

	@Override
	public <E> OperationResult<Iterable<SimpleEntity<? extends E>>> query(EntityQuery<E> query, Object prepared) {
		return theConnectionPool.connectAsync((stmt, canceled) -> {
			return getTable(query.getEntityType()).query((ConfigurableQuery<E>) query, stmt, canceled);
		}, sqlE -> new EntityOperationException("Query failed: " + query, sqlE));
	}

	@Override
	public <E> long update(EntityUpdate<E> update, Object prepared, boolean reportInChanges) throws EntityOperationException {
		try {
			return theConnectionPool.connect((stmt, canceled) -> {
				return _update(update, prepared, reportInChanges, stmt, canceled);
			});
		} catch (SQLException e) {
			throw new EntityOperationException("Update failed: " + update, e);
		}
	}

	@Override
	public <E> OperationResult<Long> updateAsync(EntityUpdate<E> update, Object prepared, boolean reportInChanges) {
		return theConnectionPool.connectAsync((stmt, canceled) -> {
			return _update(update, prepared, reportInChanges, stmt, canceled);
		}, sqlE -> new EntityOperationException("Update failed: " + update, sqlE));
	}

	private <E> long _update(EntityUpdate<E> update, Object prepared, boolean reportInChanges, Statement stmt, BooleanSupplier canceled)
		throws SQLException, EntityOperationException {
		if (reportInChanges) {
			QuickMap<String, Object> values = update.getFieldValues();
			QuickMap<String, List<Object>> oldValues = values.keySet().createMap();
			List<EntityIdentity<E>> affected = getTable(update.getEntityType()).updateGetAffected((ConfigurableUpdate<E>) update, oldValues,
				stmt, canceled);
			List<EntityChange.FieldChange<E, ?>> fieldChanges = new ArrayList<>(oldValues.valueCount());
			for (int f = 0; f < values.keySize(); f++) {
				if (values.get(f) == EntityUpdate.NOT_SET)
					continue;
				fieldChanges.add(new EntityChange.FieldChange<>(
					(ObservableEntityFieldType<E, Object>) update.getEntityType().getFields().get(f), oldValues.get(f), values.get(f)));
			}
			boolean useSelection = affected.size() > 1 && isConditionValidPostUpdate(update.getSelection(), values);
			if (canceled.getAsBoolean())
				return -1;
			changed(new EntityChange.EntityFieldValueChange<>(update.getEntityType(), Instant.now(), BetterList.of(affected), fieldChanges,
				useSelection ? update.getSelection() : null));
			return affected.size();
		} else
			return getTable(update.getEntityType()).updateGetCount((ConfigurableUpdate<E>) update, stmt, canceled);
	}

	@Override
	public <E> long delete(EntityDeletion<E> delete, Object prepared, boolean reportInChanges) throws EntityOperationException {
		try {
			return theConnectionPool.connect((stmt, canceled) -> {
				return _delete(delete, prepared, reportInChanges, stmt, canceled);
			});
		} catch (SQLException e) {
			throw new EntityOperationException("Delete failed: " + delete, e);
		}
	}

	@Override
	public <E> OperationResult<Long> deleteAsync(EntityDeletion<E> delete, Object prepared, boolean reportInChanges) {
		return theConnectionPool.connectAsync((stmt, canceled) -> {
			return _delete(delete, prepared, reportInChanges, stmt, canceled);
		}, sqlE -> new EntityOperationException("Delete failed: " + delete, sqlE));
	}

	private <E> long _delete(EntityDeletion<E> delete, Object prepared, boolean reportInChanges, Statement stmt, BooleanSupplier canceled)
		throws SQLException, EntityOperationException {
		if (reportInChanges) {
			List<EntityIdentity<E>> affected = getTable(delete.getEntityType()).deleteGetAffected((ConfigurableDeletion<E>) delete, stmt,
				canceled);
			if (reportInChanges)
				changed(
					new EntityChange.EntityExistenceChange<>(delete.getEntityType(), Instant.now(), false, BetterList.of(affected), null));
			return Long.valueOf(affected.size());
		} else
			return getTable(delete.getEntityType()).deleteGetCount((ConfigurableDeletion<E>) delete, stmt, canceled);
	}

	@Override
	public <V> ElementId updateCollection(BetterCollection<V> collection, CollectionOperationType changeType, ElementId element, V value,
		boolean reportInChanges) throws EntityOperationException {
		try {
			return doUpdateCollection((BackingList<?, V>) collection, changeType, element, value, reportInChanges);
		} catch (SQLException e) {
			throw new EntityOperationException("Collection operation failed: ", e);
		}
	}

	private <E, V> ElementId doUpdateCollection(BackingList<E, V> collection, CollectionOperationType changeType, ElementId element,
		V value, boolean reportInChanges) throws SQLException, EntityOperationException {
		return theConnectionPool.connect((stmt, canceled) -> {
			switch (changeType) {
			case add:
				int index;
				if (element != null && collection.getColumn().getIndexColumn() != null)
					index = collection.getElementsBefore(element);
				else
					index = collection.size();
				collection.getTable().modify(changeType, index, collection.size(), (E) collection.getEntity(), value, stmt, canceled);
				return collection.getWrapped().addElement(value, null, element, false).getElementId();
			case remove:
				if (collection.getColumn().getIndexColumn() != null)
					index = collection.getElementsBefore(element);
				else
					index = -1;
				collection.getTable().modify(changeType, index, collection.size(), (E) collection.getEntity(), value, stmt, canceled);
				collection.getWrapped().mutableElement(element).remove();
				return element;
			case update:
				if (collection.getColumn().getIndexColumn() != null)
					index = collection.getElementsBefore(element);
				else
					index = -1;
				collection.getTable().modify(changeType, index, collection.size(), (E) collection.getEntity(), value, stmt, canceled);
				collection.getWrapped().mutableElement(element).set(value);
				return element;
			case clear:
				if (collection.isEmpty())
					return null;
				collection.getTable().modify(changeType, 0, collection.size(), (E) collection.getEntity(), value, stmt, canceled);
				collection.getWrapped().clear();
				return null;
			}
			throw new IllegalStateException("" + changeType);
		});
	}

	@Override
	public <V> OperationResult<ElementId> updateCollectionAsync(BetterCollection<V> collection, CollectionOperationType changeType,
		ElementId element, V value, boolean reportInChanges) {
		return theConnectionPool.connectAsync((stmt, canceled) -> {
			return doUpdateCollection((BackingList<?, V>) collection, changeType, element, value, reportInChanges);
		}, sqlE -> new EntityOperationException("Collection operation failed: " + changeType, sqlE));
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
			return theConnectionPool.connect((stmt, canceled) -> {
				List<Fulfillment<?>> fulfillment = new ArrayList<>(loadRequests.size());
				for (EntityLoadRequest<?> request : loadRequests)
					fulfillment.add(satisfy(request, stmt, canceled));
				return fulfillment;
			});
		} catch (SQLException e) {
			throw new EntityOperationException("Load requests failed: " + loadRequests, e);
		}
	}

	@Override
	public OperationResult<List<Fulfillment<?>>> loadEntityDataAsync(List<EntityLoadRequest<?>> loadRequests) {
		return theConnectionPool.connectAsync((stmt, canceled) -> {
			List<Fulfillment<?>> fulfillment = new ArrayList<>(loadRequests.size());
			for (EntityLoadRequest<?> request : loadRequests)
				fulfillment.add(satisfy(request, stmt, canceled));
			return fulfillment;
		}, sqlE -> new EntityOperationException("Load requests failed: " + loadRequests, sqlE));
	}

	private <E> Fulfillment<E> satisfy(EntityLoadRequest<E> loadRequest, Statement stmt, BooleanSupplier canceled)
		throws SQLException, EntityOperationException {
		return getTable(loadRequest.getType()).fulfill(loadRequest, stmt, canceled);
	}

	void changed(EntityChange<?> change) {
		theChanges.add(change, false);
	}

	protected <E, F> JdbcFieldRepresentation<F> representField(ObservableEntityFieldType<E, F> field) {
		return representField(field.getName(), field, field.getFieldType(), false);
	}

	protected <F> JdbcFieldRepresentation<F> representField(String fieldName, ObservableEntityFieldType<?, ?> field, TypeToken<F> type,
		boolean onlySerial) {
		if (fieldName.length() == 0)
			throw new IllegalStateException("Empty field name not allowed");
		if (field.getIdIndex() < 0 && !field.getOverrides().isEmpty())
			return new OverriddenFieldImpl<>(field.getOverrides().size());
		ObservableEntityType<F> targetEntity = theEntitySet.getEntityType(TypeTokens.getRawType(type));
		if (targetEntity != null)
			return representReferenceField(fieldName, targetEntity);
		Class<?> raw = TypeTokens.getRawType(type);
		if (Collection.class.isAssignableFrom(raw)) {
			if (onlySerial)
				throw new IllegalArgumentException("Compound collection fields not supported: " + field);
			String tableName = sqlIfyName(field.getOwnerType().getName()) + "_" + sqlIfyName(field.getName());
			ReferenceField<?> ownerColumn = representReferenceField(field.getOwnerType().getName(), field.getOwnerType());
			SerialField<F> valueColumn = (SerialField<F>) representField(StringUtils.singularize(fieldName), field,
				type.resolveType(Collection.class.getTypeParameters()[0]), true);
			boolean sorted = SortedSet.class.isAssignableFrom(raw);
			return new CollectionColumn<>(tableName, //
				sorted ? null : "index", //
					ownerColumn, valueColumn, sorted);
		} else
			return new SimpleFieldImpl<>(sqlIfyName(fieldName), theTypeSupport.getColumnSupport(type, null), field.getIdIndex() < 0);
	}

	protected <F> ReferenceField<F> representReferenceField(String fieldName, ObservableEntityType<F> target) {
		QuickMap<String, SerialField<?>> idColumns = target.getIdentityFields().keySet().createMap();
		if (idColumns.keySize() == 1)
			idColumns.put(0, (SerialField<?>) representField(fieldName, target.getIdentityFields().get(0),
				target.getIdentityFields().get(0).getFieldType(), true));
		else {
			for (int i = 0; i < idColumns.keySize(); i++) {
				ObservableEntityFieldType<?, ?> refField = target.getIdentityFields().get(i);
				idColumns.put(i,
					(SerialField<?>) representField(fieldName + '_' + refField.getName(), refField, refField.getFieldType(), true));
			}
		}
		return new ReferenceFieldImpl<>(target, idColumns);
	}

	protected static String sqlIfyName(String javaName) {
		return StringUtils.parseByCase(javaName, true).toCaseScheme(false, false, "_").toUpperCase();
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

	private boolean createSchemaIfAbsent() throws EntityOperationException {
		try {
			return theConnectionPool.connect((stmt, canceled) -> {
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
				return !schemaPresent;
			});
		} catch (SQLException e) {
			throw new EntityOperationException("Could not create schema ", e);
		}
	}

	private void createTable(EntityTable<?> table) throws EntityOperationException {
		try {
			theConnectionPool.<Void> connect((stmt, canceled) -> {
				StringBuilder sql = new StringBuilder();
				sql.append("CREATE TABLE ");
				sql.append(table.getTableName()).append('(');
				boolean first = true;
				for (int c = 0; c < table.getFields().keySize(); c++) {
					// Don't duplicate non-ID inherited fields
					if (table.getType().getFields().get(c).getIdIndex() < 0 && !table.getType().getFields().get(c).getOverrides().isEmpty())
						continue;
					JdbcFieldRepresentation<?> col = table.getFields().get(c);
					if (col instanceof SerialField)
						for (Column<?> column : ((SerialField<?>) col).getColumns()) {
							if (first)
								first = false;
							else
								sql.append(',');
							sql.append("\n\t").append(column.getName()).append(' ').append(column.getColumn().getTypeName());
							// column.writeConstraints(sql); TODO
						}
					// Auto-increment the last ID field if supported
					if (table.getType().getFields().get(c).getIdIndex() == table.getType().getIdentityFields().keySize() - 1
						&& theTypeSupport.getAutoIncrement() != null)
						sql.append(' ').append(theTypeSupport.getAutoIncrement());
				}
				sql.append(",\n\n\tPRIMARY KEY (");
				first = true;
				for (ObservableEntityFieldType<?, ?> idField : table.getType().getIdentityFields().allValues()) {
					for (Column<?> col : ((SerialField<?>) table.getFields().get(idField.getIndex())).getColumns()) {
						if (first)
							first = false;
						else
							sql.append(", ");
						sql.append(col.getName());
					}
				}
				sql.append(")\n);");
				System.out.println("Creating table for " + table.getType() + ": " + sql);
				stmt.execute(sql.toString());
				for (int c = 0; c < table.getFields().keySize(); c++) {
					if (!table.getType().getFields().get(c).getOverrides().isEmpty())
						continue;
					JdbcFieldRepresentation<?> col = table.getFields().get(c);
					if (col instanceof SerialField)
						continue;
					JoinedFieldImpl<?, ?> entryCol = (JoinedFieldImpl<?, ?>) col;
					sql.setLength(0);
					sql.append("CREATE TABLE ");
					sql.append(entryCol.getTableName()).append('(');
					first = true;
					for (Column<?> column : entryCol.getOwner().getColumns()) {
						if (first)
							first = false;
						else
							sql.append(',');
						sql.append("\n\t").append(column.getName()).append(' ').append(column.getColumn().getTypeName());
						// column.writeConstraints(sql); TODO
					}
					if (entryCol.getIndexColumn() != null)
						sql.append(",\n\tINDEX INTEGER NOT NULL");
					for (Column<?> column : entryCol.getValue().getColumns()) {
						sql.append(",\n\t").append(column.getName()).append(' ').append(column.getColumn().getTypeName());
						// column.writeConstraints(sql); TODO
					}
					sql.append(",\n\n\tPRIMARY KEY (");
					first = true;
					for (Column<?> column : entryCol.getOwner().getColumns()) {
						if (first)
							first = false;
						else
							sql.append(", ");
						sql.append(column.getName());
					}
					if (entryCol.getIndexColumn() != null)
						sql.append(", ").append(entryCol.getIndexColumn());
					else {
						for (Column<?> column : entryCol.getValue().getColumns())
							sql.append(", ").append(column.getName());
					}
					sql.append("),\n\tFOREIGN KEY (");
					first = true;
					for (Column<?> column : entryCol.getOwner().getColumns()) {
						if (first)
							first = false;
						else
							sql.append(", ");
						sql.append(column.getName());
					}
					sql.append(") REFERENCES ").append(table.getTableName()).append('(');
					first = true;
					for (ObservableEntityFieldType<?, ?> field : table.getType().getIdentityFields().allValues()) {
						for (Column<?> column : ((SerialField<?>) table.getFields().get(field.getIndex())).getColumns()) {
							if (first)
								first = false;
							else
								sql.append(", ");
							sql.append(column.getName());
						}
					}
					sql.append(")\n);");
					System.out.println("Creating table for " + table.getType().getFields().get(c) + ": " + sql);
					stmt.execute(sql.toString());
				}
				return null;
			});
		} catch (SQLException e) {
			throw new EntityOperationException("Could not create table for entity " + table.getType(), e);
		}
	}

	private void createTableLinks(EntityTable<?> table) throws EntityOperationException {
		try {
			theConnectionPool.<Void> connect((stmt, canceled) -> {
				StringBuilder sql = new StringBuilder();
				boolean[] first = new boolean[1];
				for (EntityTable<?> superTable : table.getSupers()) {
					sql.append("ALTER TABLE ");
					sql.append(table.getTableName()).append(" ADD CONSTRAINT ").append(table.getTableName()).append("_SUPER_")
					.append(superTable.getTableName()).append("_FK FOREIGN KEY (");
					first[0] = true;
					for (ObservableEntityFieldType<?, ?> idField : superTable.getType().getIdentityFields().allValues()) {
						for (Column<?> col : ((SerialField<?>) superTable.getFields().get(idField.getIndex())).getColumns()) {
							if (first[0])
								first[0] = false;
							else
								sql.append(", ");
							sql.append(col.getName());
						}
					}
					sql.append(") REFERENCES ");
					sql.append(superTable.getTableName()).append("(");
					first[0] = true;
					for (ObservableEntityFieldType<?, ?> idField : superTable.getType().getIdentityFields().allValues()) {
						for (Column<?> col : ((SerialField<?>) superTable.getFields().get(idField.getIndex())).getColumns()) {
							if (first[0])
								first[0] = false;
							else
								sql.append(", ");
							sql.append(col.getName());
						}
					}
					sql.append(");");
					System.out
					.println("Creating foreign key for inheritance " + superTable.getType() + "->" + table.getType() + ": " + sql);
					stmt.execute(sql.toString());
				}
				for (int f = 0; f < table.getFields().keySize(); f++) {
					JdbcFieldRepresentation<?> field = table.getFields().get(f);
					if (field instanceof ReferenceFieldImpl) {
						ReferenceFieldImpl<?> refCol = (ReferenceFieldImpl<?>) field;
						EntityTable<?> refTable = theTables.get(refCol.getReferenceType());
						sql.append("ALTER TABLE ");
						sql.append(table.getTableName()).append(" ADD CONSTRAINT ").append(table.getTableName()).append('_')
						.append(sqlIfyName(table.getType().getFields().get(f).getName())).append("_FK FOREIGN KEY (");
						first[0] = true;
						for (Column<?> col : refCol.getColumns()) {
							if (first[0])
								first[0] = false;
							else
								sql.append(", ");
							sql.append(col.getName());
						}
						sql.append(") REFERENCES ");
						sql.append(refTable.getTableName()).append(" (");
						first[0] = true;
						for (ObservableEntityFieldType<?, ?> idField : refCol.getReferenceType().getIdentityFields().allValues()) {
							for (Column<?> col : ((SerialField<?>) refTable.getFields().get(idField.getIndex())).getColumns()) {
								if (first[0])
									first[0] = false;
								else
									sql.append(", ");
								sql.append(col.getName());
							}
						}
						sql.append(");");
						System.out.println("Creating foreign key for field " + table.getType().getFields().get(f) + ": " + sql);
						stmt.execute(sql.toString());
					} else if (field instanceof JoinedFieldImpl
						&& ((JoinedFieldImpl<?, ?>) field).getValue() instanceof ReferenceFieldImpl) {
						JoinedFieldImpl<?, ?> entryCol = (JoinedFieldImpl<?, ?>) field;
						ReferenceFieldImpl<?> refCol = (ReferenceFieldImpl<?>) entryCol.getValue();
						EntityTable<?> refTable = theTables.get(refCol.getReferenceType());
						sql.append("ALTER TABLE ");
						sql.append(entryCol.getTableName()).append(" ADD CONSTRAINT ").append(entryCol.getTableName()).append('_')
						.append(refTable.getTableName()).append("_FK FOREIGN KEY (");
						first[0] = true;
						for (Column<?> col : refCol.getColumns()) {
							if (first[0])
								first[0] = false;
							else
								sql.append(", ");
							sql.append(col.getName());
						}
						sql.append(") REFERENCES ");
						if (theSchemaName != null)
							sql.append(theSchemaName).append('.');
						sql.append(refTable.getTableName()).append(" (");
						first[0] = true;
						for (ObservableEntityFieldType<?, ?> idField : refCol.getReferenceType().getIdentityFields().allValues()) {
							for (Column<?> col : ((SerialField<?>) refTable.getFields().get(idField.getIndex())).getColumns()) {
								if (first[0])
									first[0] = false;
								else
									sql.append(", ");
								sql.append(col.getName());
							}
						}
						sql.append(");");
						System.out.println("Creating foreign key for field " + table.getType().getFields().get(f) + ": " + sql);
						stmt.execute(sql.toString());
					}
				}
				return null;
			});
		} catch (SQLException e) {
			throw new EntityOperationException("Could not create table links for entity " + table.getType(), e);
		}
	}

}
