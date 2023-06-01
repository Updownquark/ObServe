package org.observe.entity.impl;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.observe.config.OperationResult;
import org.observe.entity.ConfigurableOperation;
import org.observe.entity.EntityChange;
import org.observe.entity.EntityCreator;
import org.observe.entity.EntityDeletion;
import org.observe.entity.EntityLoadRequest;
import org.observe.entity.EntityLoadRequest.Fulfillment;
import org.observe.entity.EntityOperationException;
import org.observe.entity.EntityQuery;
import org.observe.entity.EntityUpdate;
import org.observe.entity.ObservableEntityDataSet;
import org.observe.entity.ObservableEntityFieldType;
import org.observe.entity.ObservableEntityProvider;
import org.observe.entity.ObservableEntityType;
import org.observe.util.TypeTokens;
import org.qommons.Named;
import org.qommons.StringUtils;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.ElementId;
import org.qommons.collect.MultiMap;
import org.qommons.collect.QuickSet;
import org.qommons.collect.QuickSet.QuickMap;
import org.qommons.condition.Condition;

import com.google.common.reflect.TypeToken;

public abstract class AbstractTabularEntityProvider implements ObservableEntityProvider {
	// High-level, entity-centered classes

	public static class EntityTable<E> {
		private final ObservableEntityType<E> theType;
		private Table theTable;
		private final List<EntityTable<? super E>> theSuperTables;
		private final QuickMap<String, ? extends TableField<?>> theFields;

		public EntityTable(ObservableEntityType<E> type, List<EntityTable<? super E>> superTables,
			QuickMap<String, ? extends TableField<?>> fields) {
			theType = type;
			theSuperTables = superTables;
			theFields = fields;
		}

		public ObservableEntityType<E> getType() {
			return theType;
		}

		public Table getTable() {
			return theTable;
		}

		void setTable(Table table) {
			theTable = table;
		}

		public List<EntityTable<? super E>> getSuperTables() {
			return theSuperTables;
		}

		public QuickMap<String, ? extends TableField<?>> getFields() {
			return theFields;
		}
	}

	public interface TableField<F> {
		Table getOwnerTable();

		List<? extends TableColumn> getOwnerTableColumns();
	}

	public interface ScalarField<F> extends TableField<F> {
		// void toColumns(F value, ); TODO
		//
		// F fromColumns(Object
	}

	public interface SuperReferenceTableField<E, F> extends TableField<F> {
		EntityTable<E> getEntityTable();

		List<? extends TableField<? super F>> getSuperFields();

		@Override
		default List<? extends TableColumn> getOwnerTableColumns() {
			return getOwnerTable().getIdentityColumns().allValues();
		}
	}

	public interface ReferenceTableField<F> extends TableField<F> {
		EntityTable<F> getTargetEntity();

		@Override
		List<? extends ReferenceColumn<?>> getOwnerTableColumns();
	}

	public interface CollectionTableField<V, F> extends TableField<F> {
		Table getCollectionTable();

		List<? extends ReferenceColumn<?>> getBackReferenceColumns();

		TableField<V> getValueField();

		@Override
		default List<? extends TableColumn> getOwnerTableColumns() {
			return Collections.emptyList();
		}
	}

	public interface MapTableField<K, V, F> extends CollectionTableField<V, F> {
		TableField<K> getKeyField();
	}

	// Table-level classes

	public interface Row {
	}

	public interface TableOrJoin extends Named {
		QuickMap<String, ? extends TableOrJoinColumn> getColumns();

		default TableOrJoinColumn getField(String fieldName) throws UnsupportedOperationException, IllegalArgumentException {
			return getColumns().get(fieldName);
		}

		default boolean owns(TableOrJoinColumn column) {
			return getColumns().getIfPresent(column.getName()) == column;
		}
	}

	public interface TableOrJoinColumn extends Named, Comparable<TableOrJoinColumn> {
		TableOrJoin getOwner();

		@Override
		default int compareTo(TableOrJoinColumn o) {
			if (getOwner() != o.getOwner())
				throw new IllegalArgumentException("Cannot compare fields of different entity types");
			return StringUtils.compareNumberTolerant(getName(), o.getName(), true, true);
		}
	}

	public interface Table extends TableOrJoin {
		QuickMap<String, TableColumn> getIdentityColumns();

		@Override
		QuickMap<String, TableColumn> getColumns();

		public static class Default implements Table {
			private final String theName;
			private final QuickMap<String, TableColumn> theIdColumns;
			private final QuickMap<String, TableColumn> theColumns;

			public Default(String name, QuickMap<String, TableColumn> idColumns, QuickMap<String, TableColumn> columns) {
				theName = name;
				theIdColumns = idColumns.unmodifiable();
				theColumns = columns.unmodifiable();
			}

			@Override
			public String getName() {
				return theName;
			}

			@Override
			public QuickMap<String, TableColumn> getIdentityColumns() {
				return theIdColumns;
			}

			@Override
			public QuickMap<String, TableColumn> getColumns() {
				return theColumns;
			}

			@Override
			public String toString() {
				return super.toString();
			}
		}
	}

	public interface TableColumn extends TableOrJoinColumn {
		@Override
		Table getOwner();
	}

	public interface ReferenceColumn<V> extends TableColumn {
		Table getReferenceTable();

		TableColumn getReferenceColumn();
	}

	public static class Join implements TableOrJoin {
		private final String theJoinName;
		private final TableOrJoin theLeft;
		private final Table theRight;
		private final List<JoinColumn> theJoinColumns;
		private final QuickMap<String, JoinColumn> theAllJoinColumns;

		public Join(String joinName, TableOrJoin left, Table right, List<TableOrJoinColumn> leftJoins, List<TableColumn> rightJoins) {
			theJoinName = joinName;
			theLeft = left;
			theRight = right;
			List<JoinColumn> joinColumns = new ArrayList<>(leftJoins.size());
			theJoinColumns = Collections.unmodifiableList(joinColumns);
			Map<String, JoinColumn> allJoinColumns = new HashMap<>();
			for (int i = 0; i < leftJoins.size(); i++) {
				String joinColumnName = StringUtils.getNewItemName(allJoinColumns.values(), JoinColumn::getName,
					joinName + rightJoins.get(i).getName(), StringUtils.SIMPLE_DUPLICATES);
				JoinColumn joinColumn = new JoinColumn(this, joinColumnName, leftJoins.get(i), rightJoins.get(i));
				joinColumns.add(joinColumn);
				allJoinColumns.put(joinColumnName, joinColumn);
			}
			theAllJoinColumns = QuickMap.of(allJoinColumns, StringUtils.DISTINCT_NUMBER_TOLERANT);
		}

		@Override
		public String getName() {
			return theJoinName;
		}

		@Override
		public QuickMap<String, JoinColumn> getColumns() {
			return theAllJoinColumns;
		}

		public TableOrJoin getLeft() {
			return theLeft;
		}

		public Table getRight() {
			return theRight;
		}

		public List<JoinColumn> getJoinColumns() {
			return theJoinColumns;
		}
	}

	private static class JoinColumn implements TableOrJoinColumn {
		private final Join theJoin;
		private final String theJoinColumnName;
		private final TableOrJoinColumn theLeft;
		private final TableColumn theRight;

		public JoinColumn(Join join, String joinColumnName, TableOrJoinColumn left, TableColumn right) {
			theJoin = join;
			theJoinColumnName = joinColumnName;
			theLeft = left;
			theRight = right;
		}

		@Override
		public Join getOwner() {
			return theJoin;
		}

		@Override
		public String getName() {
			return theJoinColumnName;
		}

		public TableOrJoinColumn getLeft() {
			return theLeft;
		}

		public TableColumn getRight() {
			return theRight;
		}

		@Override
		public String toString() {
			return theJoinColumnName;
		}
	}

	public interface Selection {
	}

	public interface Variable extends Named {
	}

	public interface Variables {
		Object getVariable(String variableName);
	}

	public interface TableOperation<R> {
		Object prepare() throws EntityOperationException;

		R execute(Object prepared, Variables variables) throws EntityOperationException;

		OperationResult<R> executeAsync(Object prepared, Variables variables);
	}

	public static class TableCreateRequest {
		private final Table theTable;
		private final Map<TableColumn, Object> theValues;

		public TableCreateRequest(Table table, Map<TableColumn, Object> values) {
			theTable = table;
			theValues = values;
		}

		public Table getTable() {
			return theTable;
		}

		public Map<TableColumn, Object> getValues() {
			return theValues;
		}
	}

	public interface QueryResult {
		Row getNextRow() throws EntityOperationException;
	}

	public interface QueryOperation extends TableOperation<QueryResult> {
		long count(Object prepared, Variables variables);

		OperationResult<Long> countAsync(Object prepared, Variables variables);
	}

	// public interface TableQuery<C extends QuickTableColumn, T extends Table> {
	// T getTable();
	//
	// TableQuery<C, T> addColumns(QuickTableColumn... columns);
	//
	// TableQuery<C, T> join(Table table, QuickTableColumn... joinColumns);
	// }
	//
	// public interface TableInsert<C extends QuickTableColumn, T extends Table> {
	// T getTable();
	//
	// // TableInsert<C, T> with(
	// }
	//
	// public interface TableUpdate<C extends QuickTableColumn, T extends Table> {
	// T getTable();
	//
	// }
	//
	// public interface TableDelete<C extends QuickTableColumn, T extends Table> {
	// T getTable();
	//
	// }
	//
	// public interface TableCreate<C extends QuickTableColumn, T extends Table> {
	//
	// }

	private ObservableEntityDataSet theEntitySet;
	private Map<String, EntityTable<?>> theEntityTables;
	private List<EntityChange<?>> theLocalChanges;

	@Override
	public void install(ObservableEntityDataSet entitySet) throws EntityOperationException {
		theEntitySet = entitySet;
		theLocalChanges = new ArrayList<>();
		QuickMap<String, EntityTable<?>> entityTables = QuickSet
			.of(theEntitySet.getEntityTypes().stream().map(e -> e.getName()).collect(Collectors.toList()))//
			.createMap();
		List<QuickMap<String, TableField<?>>> fields = new ArrayList<>(theEntitySet.getEntityTypes().size());
		// Create the entity table instances
		for (int i = 0; i < entityTables.keySize(); i++) {
			ObservableEntityType<?> entity = theEntitySet.getEntityTypes().get(i);
			try {
				entityTables.put(entity.getName(), _createEntityTable(entity, entityTables, fields));
			} catch (SQLException e) {
				throw new EntityOperationException("Could not initialize entity tables from DB", e);
			}
		}
		theEntityTables = Collections.unmodifiableMap(new HashMap<>(entityTables.asJavaMap()));
		// Create non-reference ID fields
		for (int i = 0; i < entityTables.keySize(); i++)
			initFields(entityTables, fields, i, false, false, false);
		// Create reference ID fields
		for (int i = 0; i < entityTables.keySize(); i++)
			initFields(entityTables, fields, i, false, true, false);
		// Create non-reference, scalar, non-ID fields
		for (int i = 0; i < entityTables.keySize(); i++)
			initFields(entityTables, fields, i, true, false, false);
		// Create reference, scalar, non-ID fields
		for (int i = 0; i < entityTables.keySize(); i++)
			initFields(entityTables, fields, i, true, true, false);
		// Create non-scalar fields
		for (int i = 0; i < entityTables.keySize(); i++)
			initFields(entityTables, fields, i, false, true, true);

		init();
	}

	private <E> EntityTable<E> _createEntityTable(ObservableEntityType<E> entity, QuickMap<String, EntityTable<?>> entityTables,
		List<QuickMap<String, TableField<?>>> fields) throws SQLException, EntityOperationException {
		List<EntityTable<? super E>> superTables = new ArrayList<>(entity.getSupers().size());
		for (ObservableEntityType<? super E> superEntity : entity.getSupers()) {
			entityTables.put(superEntity.getName(), _createEntityTable(superEntity, entityTables, fields));
		}
		QuickMap<String, TableField<?>> entityFields = entity.getFields().keySet().createMap();
		fields.add(entityTables.keyIndex(entity.getName()), entityFields);
		EntityTable<E> table = new EntityTable<>(entity, //
			superTables.isEmpty() ? Collections.<EntityTable<? super E>> emptyList() : Collections.unmodifiableList(superTables), //
				entityFields);
		table.setTable(createTable(table));
		return table;
	}

	private <E> void initFields(QuickMap<String, EntityTable<?>> entityTables, List<QuickMap<String, TableField<?>>> fields,
		int entityIndex,
		boolean withNonIdFields, boolean withReferences, boolean withNonScalar) {
		EntityTable<E> entityTable = (EntityTable<E>) entityTables.get(entityIndex);
		for (ObservableEntityType<? super E> superType : entityTable.getType().getSupers()) {
			int superIndex = entityTables.keyIndex(superType.getName());
			initFields(entityTables, fields, superIndex, withNonIdFields, withReferences, withNonScalar);
		}
		QuickMap<String, TableField<?>> entityFields = fields.get(entityIndex);
		for (int f = 0; f < entityFields.keySize(); f++) {
			if (entityTable.getFields().get(f) != null)
				continue; // Already initialized in a previous round
			ObservableEntityFieldType<E, ?> field = entityTable.getType().getFields().get(f);
			if (!withNonIdFields && field.getIdIndex() < 0)
				continue;
			if (!withReferences && field.getTargetEntity() != null)
				continue;
			if (!withNonScalar && !isScalar(TypeTokens.getRawType(field.getFieldType()), field))
				continue;
			entityFields.put(f, createField(entityTable, entityTable.getTable(), field.getName(), (TypeToken<Object>) field.getFieldType(),
				field, (ObservableEntityType<Object>) field.getTargetEntity(), field.getKeyTarget(), field.getValueTarget()));
		}
	}

	protected <F> boolean isScalar(Class<F> fieldType, ObservableEntityFieldType<?, ?> field) {
		if (Collection.class.isAssignableFrom(fieldType))
			return false;
		else if (Map.class.isAssignableFrom(fieldType))
			return false;
		else if (MultiMap.class.isAssignableFrom(fieldType))
			return false;
		return true;
	}

	protected <E> boolean useSuperField(EntityTable<E> entityTable, ObservableEntityFieldType<E, ?> field) {
		return true;
	}

	protected abstract <E> Table createTable(EntityTable<E> entity) throws SQLException, EntityOperationException;

	protected abstract Table createCollectionTable(Table ownerTable, String fieldName, Class<?> collectionClass);

	protected abstract <F> TableField<F> createScalarField(Table ownerTable, Class<F> fieldClass, TypeToken<F> fieldType,
		ObservableEntityFieldType<?, ?> field);

	protected abstract <F> TableField<F> createCustomNonScalarField(Table ownerTable, Class<F> fieldClass, TypeToken<F> fieldType,
		String fieldName, ObservableEntityFieldType<?, ?> field, ObservableEntityType<?> targetEntity,
		ObservableEntityType<?> keyTargetEntity, ObservableEntityType<?> valueTargetEntity);

	protected abstract <V> ReferenceColumn<V> createReferenceColumn(Table ownerTable, TableColumn idField);

	protected abstract void init();

	/**
	 * Creates the column instance for a field
	 *
	 * <p>
	 * This method is called for fields of all entities in cycles. In each cycle, one class of field is initialized. When this method is
	 * called on a field, all fields in previous classes have been initialized for all entities, and all fields of the fields own class have
	 * been initialized for any {@link EntityTable#getSuperTables() super tables} of the field's entity type.
	 * <ol>
	 * <li>Non-{@link ObservableEntityFieldType#getTargetEntity() reference} {@link ObservableEntityFieldType#getIdIndex() identity}
	 * fields</li>
	 * <li>{@link ObservableEntityFieldType#getTargetEntity() Reference} {@link ObservableEntityFieldType#getIdIndex() identity} fields</li>
	 * <li>Non-{@link ObservableEntityFieldType#getTargetEntity() reference} scalar (not collection/map-typed)
	 * non-{@link ObservableEntityFieldType#getIdIndex() identity} fields</li>
	 * <li>{@link ObservableEntityFieldType#getTargetEntity() Reference} scalar (not collection/map-typed)
	 * non-{@link ObservableEntityFieldType#getIdIndex() identity} fields</li>
	 * <li>Non-scalar (collection/map-typed) non-{@link ObservableEntityFieldType#getIdIndex() identity} fields</li>
	 * </ol>
	 * </p>
	 *
	 * @param <E> The entity type represented by the table
	 * @param <F> The type of the field
	 * @param entityTable The table representing the entity (null for a non-entity table)
	 * @param table The table to create the field for
	 * @param fieldName The name of the field
	 * @param fieldType The type of the field
	 * @param field The root entity field
	 * @param knownTargetEntity The entity type that is the type of the field, if known
	 * @param knownKeyTarget The entity type that is the key type of the map-typed field, if known
	 * @param knownValueTarget The entity type that is the value type of the collection- or map-typed field, if known
	 * @return The column instance for the field
	 */
	protected <E, F> TableField<F> createField(EntityTable<E> entityTable, Table table, String fieldName, TypeToken<F> fieldType,
		ObservableEntityFieldType<E, ?> field, //
		ObservableEntityType<F> knownTargetEntity, ObservableEntityType<?> knownKeyTarget, ObservableEntityType<?> knownValueTarget) {
		if (field.getFieldType() == fieldType && field.getIdIndex() < 0 && !field.getOverrides().isEmpty()
			&& useSuperField(entityTable, field))
			return createSuperReferenceField(entityTable, (ObservableEntityFieldType<E, F>) field);
		if (knownTargetEntity != null)
			return createReferenceField(table, knownTargetEntity, field.getName(), field);
		Class<F> fieldClass = TypeTokens.getRawType(fieldType);
		if (field.getFieldType() != fieldType) {
			knownTargetEntity = theEntitySet.getEntityType(fieldClass);
			if (knownTargetEntity != null)
				return createReferenceField(table, knownTargetEntity, field.getName(), field);
		}
		if (isScalar(fieldClass, field))
			return createScalarField(table, fieldClass, fieldType, field);
		else if (Collection.class.isAssignableFrom(fieldClass))
			return (TableField<F>) createCollectionField(table, (Class<Collection<Object>>) fieldClass,
				(TypeToken<Collection<Object>>) fieldType, field.getName(), (ObservableEntityType<Object>) knownValueTarget, field);
		else if (Map.class.isAssignableFrom(fieldClass))
			return (TableField<F>) createMapField(table, (Class<Map<Object, Object>>) fieldClass,
				(TypeToken<Map<Object, Object>>) fieldType, field.getName(), (ObservableEntityType<Object>) knownKeyTarget,
				(ObservableEntityType<Object>) knownValueTarget, field);
		else if (MultiMap.class.isAssignableFrom(fieldClass))
			return (TableField<F>) createMultiMapField(table, (Class<MultiMap<Object, Object>>) fieldClass,
				(TypeToken<MultiMap<Object, Object>>) fieldType, field.getName(), (ObservableEntityType<Object>) knownKeyTarget,
				(ObservableEntityType<Object>) knownValueTarget, field);
		else
			return createCustomNonScalarField(table, fieldClass, fieldType, field.getName(), field, knownTargetEntity, knownKeyTarget,
				knownValueTarget);
	}

	protected <E, F> TableField<F> createSuperReferenceField(EntityTable<E> entityTable, ObservableEntityFieldType<E, F> field) {
		List<TableField<? super F>> superFields = new ArrayList<>(field.getOverrides().size());
		for (ObservableEntityFieldType<? super E, ? super F> override : field.getOverrides()) {
			superFields
			.add((TableField<? super F>) theEntityTables.get(override.getOwnerType().getName()).getFields().get(override.getName()));
		}
		return new SuperReferenceFieldImpl<>(entityTable, superFields);
	}

	protected <F> TableField<F> createReferenceField(Table ownerTable, ObservableEntityType<F> targetType, String fieldName,
		ObservableEntityFieldType<?, ?> field) {
		EntityTable<F> referenceTable = (EntityTable<F>) theEntityTables.get(targetType.getName());
		List<ReferenceColumn<?>> refColumns = new ArrayList<>(referenceTable.getTable().getIdentityColumns().keySize());
		for (TableColumn idField : referenceTable.getTable().getIdentityColumns().allValues()) {
			refColumns.add(createReferenceColumn(ownerTable, idField));
		}
		return new ReferenceFieldImpl<>(ownerTable, referenceTable, Collections.unmodifiableList(refColumns));
	}

	protected <V, C extends Collection<V>> TableField<C> createCollectionField(Table ownerTable, Class<C> fieldClass,
		TypeToken<C> fieldType, String fieldName, ObservableEntityType<V> valueEntity, ObservableEntityFieldType<?, ?> field) {
		Table collectionTable = createCollectionTable(ownerTable, fieldName, fieldClass);
		TypeToken<V> valueType = (TypeToken<V>) fieldType.resolveType(Collection.class.getTypeParameters()[0]);
		Class<V> valueClass = TypeTokens.getRawType(valueType);
		String valueName;
		if (Collection.class.isAssignableFrom(valueClass) || Map.class.isAssignableFrom(valueClass)
			|| MultiMap.class.isAssignableFrom(valueClass))
			valueName = fieldName;
		else
			valueName = StringUtils.singularize(fieldName);
		TableField<V> valueField = createField(null, collectionTable, valueName, valueType, field, valueEntity, null, null);
		List<ReferenceColumn<?>> backRefColumns = new ArrayList<>(ownerTable.getIdentityColumns().keySize());
		for (TableColumn idField : ownerTable.getIdentityColumns().allValues()) {
			backRefColumns.add(createReferenceColumn(ownerTable, idField));
		}
		return new CollectionFieldImpl<>(ownerTable, collectionTable, Collections.unmodifiableList(backRefColumns), valueField);
	}

	private static final Pattern KEY_TO_VALUE_PATTERN = Pattern.compile("(?<key>.+)To(?<value>.+)");
	private static final Pattern VALUE_BY_KEY_PATTERN = Pattern.compile("(?<value>.+)By(?<key>.+)");

	protected <K, V, M extends Map<K, V>> TableField<M> createMapField(Table ownerTable, Class<M> fieldClass, TypeToken<M> fieldType,
		String fieldName, ObservableEntityType<K> keyEntity, ObservableEntityType<V> valueEntity, ObservableEntityFieldType<?, ?> field) {
		Table collectionTable = createCollectionTable(ownerTable, fieldName, fieldClass);
		TypeToken<K> keyType = (TypeToken<K>) fieldType.resolveType(Map.class.getTypeParameters()[0]);
		TypeToken<V> valueType = (TypeToken<V>) fieldType.resolveType(Map.class.getTypeParameters()[1]);
		String keyName, valueName;
		Matcher ktvpMatch = KEY_TO_VALUE_PATTERN.matcher(fieldName);
		if (ktvpMatch.matches()) {
			keyName = ktvpMatch.group("key");
			valueName = ktvpMatch.group("value");
		} else {
			ktvpMatch = VALUE_BY_KEY_PATTERN.matcher(fieldName);
			if (ktvpMatch.matches()) {
				keyName = ktvpMatch.group("key");
				valueName = ktvpMatch.group("value");
			} else {
				keyName = "key";
				valueName = fieldName;
			}
		}
		Class<V> valueClass = TypeTokens.getRawType(valueType);
		if (!Collection.class.isAssignableFrom(valueClass) && !Map.class.isAssignableFrom(valueClass)
			&& !MultiMap.class.isAssignableFrom(valueClass))
			valueName = StringUtils.singularize(fieldName);
		TableField<K> keyField = createField(null, collectionTable, keyName, keyType, field, keyEntity, null, null);
		TableField<V> valueField = createField(null, collectionTable, valueName, valueType, field, valueEntity, null, null);
		List<ReferenceColumn<?>> backRefColumns = new ArrayList<>(ownerTable.getIdentityColumns().keySize());
		for (TableColumn idField : ownerTable.getIdentityColumns().allValues()) {
			backRefColumns.add(createReferenceColumn(ownerTable, idField));
		}
		return new MapFieldImpl<>(ownerTable, collectionTable, Collections.unmodifiableList(backRefColumns), keyField, valueField);
	}

	protected <K, V, M extends MultiMap<K, V>> TableField<M> createMultiMapField(Table ownerTable, Class<M> fieldClass,
		TypeToken<M> fieldType, String fieldName, ObservableEntityType<K> keyEntity, ObservableEntityType<V> valueEntity,
		ObservableEntityFieldType<?, ?> field) {
		Table collectionTable = createCollectionTable(ownerTable, fieldName, fieldClass);
		TypeToken<K> keyType = (TypeToken<K>) fieldType.resolveType(Map.class.getTypeParameters()[0]);
		TypeToken<V> valueType = (TypeToken<V>) fieldType.resolveType(Map.class.getTypeParameters()[1]);
		String keyName, valueName;
		Matcher ktvpMatch = KEY_TO_VALUE_PATTERN.matcher(fieldName);
		if (ktvpMatch.matches()) {
			keyName = ktvpMatch.group("key");
			valueName = ktvpMatch.group("value");
		} else {
			ktvpMatch = VALUE_BY_KEY_PATTERN.matcher(fieldName);
			if (ktvpMatch.matches()) {
				keyName = ktvpMatch.group("key");
				valueName = ktvpMatch.group("value");
			} else {
				keyName = "key";
				valueName = fieldName;
			}
		}
		Class<V> valueClass = TypeTokens.getRawType(valueType);
		if (!Collection.class.isAssignableFrom(valueClass) && !Map.class.isAssignableFrom(valueClass)
			&& !MultiMap.class.isAssignableFrom(valueClass))
			valueName = StringUtils.singularize(fieldName);
		TableField<K> keyField = createField(null, collectionTable, keyName, keyType, field, keyEntity, null, null);
		TableField<V> valueField = createField(null, collectionTable, valueName, valueType, field, valueEntity, null, null);
		List<ReferenceColumn<?>> backRefColumns = new ArrayList<>(ownerTable.getIdentityColumns().keySize());
		for (TableColumn idField : ownerTable.getIdentityColumns().allValues()) {
			backRefColumns.add(createReferenceColumn(ownerTable, idField));
		}
		return new MultiMapFieldImpl<>(ownerTable, collectionTable, Collections.unmodifiableList(backRefColumns), keyField, valueField);
	}

	static class EntityCreateOperation<E> {
		private final TableOperation<Row> theOperation;
		// private final QuickMap<String, TableOrJoinColumn<?>> theFi
	}

	@Override
	public <E> SimpleEntity<E> create(EntityCreator<? super E, E> creator, Object prepared, boolean reportInChanges)
		throws EntityOperationException {
		EntityCreateOperation<E> op;
		if (prepared != null)
			op = (EntityCreateOperation<E>) prepared;
		else
			op = makeCreateOperation(creator);
		List<TableCreateRequest> creates = new ArrayList<>();

		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <E> OperationResult<SimpleEntity<E>> createAsync(EntityCreator<? super E, E> creator, Object prepared, boolean reportInChanges) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public OperationResult<Long> count(EntityQuery<?> query, Object prepared) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <E> OperationResult<Iterable<SimpleEntity<? extends E>>> query(EntityQuery<E> query, Object prepared) {

		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <E> long update(EntityUpdate<E> update, Object prepared, boolean reportInChanges) throws EntityOperationException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public <E> OperationResult<Long> updateAsync(EntityUpdate<E> update, Object prepared, boolean reportInChanges) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <E> long delete(EntityDeletion<E> delete, Object prepared, boolean reportInChanges) throws EntityOperationException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public <E> OperationResult<Long> deleteAsync(EntityDeletion<E> delete, Object prepared, boolean reportInChanges) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <V> ElementId updateCollection(BetterCollection<V> collection, CollectionOperationType changeType, ElementId element, V value,
		boolean reportInChanges) throws EntityOperationException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <V> OperationResult<ElementId> updateCollectionAsync(BetterCollection<V> collection, CollectionOperationType changeType,
		ElementId element, V value, boolean reportInChanges) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <K, V> ElementId updateMap(Map<K, V> collection, CollectionOperationType changeType, K key, V value, Runnable asyncResult) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <K, V> ElementId updateMultiMap(MultiMap<K, V> collection, CollectionOperationType changeType, ElementId valueElement, K key,
		V value, Consumer<ElementId> asyncResult) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object prepare(ConfigurableOperation<?> operation) throws EntityOperationException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<EntityChange<?>> changes() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<Fulfillment<?>> loadEntityData(List<EntityLoadRequest<?>> loadRequests) throws EntityOperationException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public OperationResult<List<Fulfillment<?>>> loadEntityDataAsync(List<EntityLoadRequest<?>> loadRequests) {
		// TODO Auto-generated method stub
		return null;
	}

	protected abstract TableOperation<Row> create(List<TableCreateRequest> creates);

	protected abstract Selection select(TableOrJoin tableOrJoin, Condition<TableOrJoin, ?, ?> condition);

	protected abstract QueryOperation query(Selection selection);

	protected abstract TableOperation<Long> update(Selection selection, Map<TableOrJoinColumn, Object> values);

	protected abstract TableOperation<Long> delete(Selection selection);

	@Override
	public abstract void dispose(Object prepared);

	protected abstract Iterable<EntityChange<?>> getExternalChanges();

	static abstract class TableFieldImpl<F> implements TableField<F> {
		private final Table theOwnerTable;

		public TableFieldImpl(Table ownerTable) {
			theOwnerTable = ownerTable;
		}

		@Override
		public Table getOwnerTable() {
			return theOwnerTable;
		}
	}

	static class SuperReferenceFieldImpl<E, F> extends TableFieldImpl<F> implements SuperReferenceTableField<E, F> {
		private final EntityTable<E> theEntityTable;
		private final List<? extends TableField<? super F>> theSuperFields;

		public SuperReferenceFieldImpl(EntityTable<E> entityTable, List<? extends TableField<? super F>> superFields) {
			super(entityTable.getTable());
			theEntityTable = entityTable;
			theSuperFields = superFields;
		}

		@Override
		public EntityTable<E> getEntityTable() {
			return theEntityTable;
		}

		@Override
		public List<? extends TableField<? super F>> getSuperFields() {
			return theSuperFields;
		}
	}

	static class ReferenceFieldImpl<F> extends TableFieldImpl<F> implements ReferenceTableField<F> {
		private final EntityTable<F> theTargetEntity;
		private final List<ReferenceColumn<?>> theReferenceColumns;

		public ReferenceFieldImpl(Table ownerTable, EntityTable<F> targetEntity, List<ReferenceColumn<?>> referenceColumns) {
			super(ownerTable);
			theTargetEntity = targetEntity;
			theReferenceColumns = referenceColumns;
		}

		@Override
		public EntityTable<F> getTargetEntity() {
			return theTargetEntity;
		}

		@Override
		public List<? extends ReferenceColumn<?>> getOwnerTableColumns() {
			return theReferenceColumns;
		}
	}

	static class CollectionFieldImpl<V, C extends Collection<V>> extends TableFieldImpl<C> implements CollectionTableField<V, C> {
		private final Table theCollectionTable;
		private final List<ReferenceColumn<?>> theBackReferenceColumns;
		private final TableField<V> theValueField;

		public CollectionFieldImpl(Table ownerTable, Table collectionTable, List<ReferenceColumn<?>> backReferenceColumns,
			TableField<V> valueField) {
			super(ownerTable);
			theCollectionTable = collectionTable;
			theBackReferenceColumns = backReferenceColumns;
			theValueField = valueField;
		}

		@Override
		public Table getCollectionTable() {
			return theCollectionTable;
		}

		@Override
		public List<? extends ReferenceColumn<?>> getBackReferenceColumns() {
			return theBackReferenceColumns;
		}

		@Override
		public TableField<V> getValueField() {
			return theValueField;
		}
	}

	static class MapFieldImpl<K, V, M extends Map<K, V>> extends TableFieldImpl<M> implements MapTableField<K, V, M> {
		private final Table theCollectionTable;
		private final List<ReferenceColumn<?>> theBackReferenceColumns;
		private final TableField<K> theKeyField;
		private final TableField<V> theValueField;

		public MapFieldImpl(Table ownerTable, Table collectionTable, List<ReferenceColumn<?>> backReferenceColumns, TableField<K> keyField,
			TableField<V> valueField) {
			super(ownerTable);
			theCollectionTable = collectionTable;
			theBackReferenceColumns = backReferenceColumns;
			theKeyField = keyField;
			theValueField = valueField;
		}

		@Override
		public Table getCollectionTable() {
			return theCollectionTable;
		}

		@Override
		public List<? extends ReferenceColumn<?>> getBackReferenceColumns() {
			return theBackReferenceColumns;
		}

		@Override
		public TableField<V> getValueField() {
			return theValueField;
		}

		@Override
		public TableField<K> getKeyField() {
			return theKeyField;
		}
	}

	static class MultiMapFieldImpl<K, V, M extends MultiMap<K, V>> extends TableFieldImpl<M> implements MapTableField<K, V, M> {
		private final Table theCollectionTable;
		private final List<ReferenceColumn<?>> theBackReferenceColumns;
		private final TableField<K> theKeyField;
		private final TableField<V> theValueField;

		public MultiMapFieldImpl(Table ownerTable, Table collectionTable, List<ReferenceColumn<?>> backReferenceColumns,
			TableField<K> keyField, TableField<V> valueField) {
			super(ownerTable);
			theCollectionTable = collectionTable;
			theBackReferenceColumns = backReferenceColumns;
			theKeyField = keyField;
			theValueField = valueField;
		}

		@Override
		public Table getCollectionTable() {
			return theCollectionTable;
		}

		@Override
		public List<? extends ReferenceColumn<?>> getBackReferenceColumns() {
			return theBackReferenceColumns;
		}

		@Override
		public TableField<V> getValueField() {
			return theValueField;
		}

		@Override
		public TableField<K> getKeyField() {
			return theKeyField;
		}
	}
}
