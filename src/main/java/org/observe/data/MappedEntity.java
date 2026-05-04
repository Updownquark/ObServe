package org.observe.data;

import java.util.Comparator;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.Equivalence;
import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SimpleObservable;
import org.observe.assoc.ModControlledObservableMap;
import org.observe.assoc.ModControlledObservableMultiMap;
import org.observe.assoc.ObservableMap;
import org.observe.assoc.ObservableMultiMap;
import org.observe.assoc.ObservableSortedMap;
import org.observe.collect.ModControlledObservableCollection;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollectionBuilder;
import org.observe.collect.ObservableSet;
import org.observe.collect.ObservableSortedCollection;
import org.observe.collect.ObservableSortedSet;
import org.observe.data.ReflectedEntityValueType.RealFieldValueProducer;
import org.observe.util.EntityReflector;
import org.observe.util.EntityReflector.FieldChange;
import org.qommons.CausalLock;
import org.qommons.SimpleUniqueKey;
import org.qommons.Subscription;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterMap;
import org.qommons.collect.BetterMultiMap;
import org.qommons.collect.ListenerList;
import org.qommons.data.impl.AbstractGenericEntity;
import org.qommons.data.types.Blob;
import org.qommons.data.types.EntityField;
import org.qommons.data.types.FieldMapping;
import org.qommons.data.types.FieldType;
import org.qommons.data.values.GenericEntity;

/**
 * Individual entity support for entities in an {@link ReflectedEntitySet}. Instances of this class are the generic entities as well as the
 * backing for the reflected {@link EntityReflector} proxies.
 *
 * @param <E> The type of the entity
 */
public class MappedEntity<E> extends AbstractGenericEntity implements EntityReflector.ObservableEntityInstanceBacking<E> {
	static final Object ENTITY_ASSOC = new SimpleUniqueKey("mappedEntity");

	private final ReflectedEntityValueType<E> theType;
	private final Object[] theRealFieldValues;
	private final E theRealEntity;
	private ListenerList<Consumer<FieldChange<?>>>[] theListeners;
	private SimpleObservable<Void> theUntil;

	/**
	 * @param type The entity type of this entity
	 * @param entitySet The entity set this entity belongs to
	 * @param id The ID values for this entity
	 */
	public MappedEntity(ReflectedEntityValueType<E> type, ReflectedEntitySet entitySet, Object[] id) {
		super(type.getGenericType(), entitySet, id);
		theType = type;
		theRealFieldValues = new Object[type.getFields().keySize()];
		theRealEntity = theType.getReflector().newInstance(this);
		EntityReflector.associate(theRealEntity, ENTITY_ASSOC, this);
		int f = 0;
		for (ReflectedFieldType<E, ?, ?> field : theType.getFields().values()) {
			theRealFieldValues[f] = ((RealFieldValueProducer<Object, ?>) field.getRealMapping()).genericToReal(//
				get(field.getGenericField()), entitySet, this);
			f++;
		}
	}

	/** @return The "real" entity proxy that is an instance of this entity's run-time java type */
	public E getRealEntity() {
		return theRealEntity;
	}

	/** @return An observable that will fire when this entity is deleted */
	public Observable<Void> getUntil() {
		if (theUntil == null)
			theUntil = SimpleObservable.create(b -> b.withLocking(getEntitySet()));
		return theUntil;
	}

	@Override
	public ReflectedEntitySet getEntitySet() {
		return (ReflectedEntitySet) super.getEntitySet();
	}

	@Override
	public MappedEntity<E> set(EntityField<?> field, Object value) {
		try (Transaction t = getEntitySet().lock(true, null)) {
			super.set(field, value);
			int index = theType.getGenericType().indexOf(field);
			Object oldValue = theRealFieldValues[index];
			Object newValue = ((RealFieldValueProducer<Object, Object>) theType.getFields().get(index).getRealMapping()).asFunction()
				.apply(value);
			theRealFieldValues[index] = newValue;
			if (theListeners != null) {
				ListenerList<Consumer<FieldChange<?>>> listeners = theListeners[index];
				if (listeners != null) {
					FieldChange<Object> change = new FieldChange<>(oldValue, newValue, getEntitySet().getLock().getRootCausable());
					listeners.forEach(//
						l -> l.accept(change));
				}
			}
			getEntitySet().entityAffected(this);
		}
		return this;
	}

	@Override
	protected void fieldStructureChanged(EntityField<?> field) {
		super.fieldStructureChanged(field);
		// Only tell the entity set if the field is not mapped.
		// The content of a mapped field is defined by properties of the member entities,
		// so changes to mapped fields are really changes to the members, and not to this entity.
		// In particular, changes to mapped fields don't affect how this entity is persisted.
		if (field.getMapping() == null)
			getEntitySet().entityAffected(this);
	}

	@Override
	protected <T> T createEmptyStructure(EntityField<?> field, FieldType.ParameterizedType<T> type) {
		if (type == FieldType.BLOB) {
			return (T) new Blob.InMemoryBlob();
		} else if (type instanceof FieldType.CollectionType) {
			ObservableCollectionBuilder<?, ?> builder;
			FieldType.CollectionType<?, ?> collType = (FieldType.CollectionType<?, ?>) type;
			if (collType.isSorted) {
				Comparator<?> sort;
				if (field.getMapping() != null && field.getMapping().sortByField != null)
					sort = field.getMapping().entitySort;
				else
					sort = collType.componentType;
				if (collType.isDistinct)
					builder = ObservableSortedSet.build(sort);
				else
					builder = ObservableSortedCollection.build(sort);
			} else {
				if (collType.isDistinct)
					builder = ObservableSet.build();
				else
					builder = ObservableCollection.build();
			}
			return (T) builder.withLocking(getEntitySet().getLock()).withDescription(this + "." + field.getName()).build();
		} else if (type instanceof FieldType.MapType) {
			ObservableMap.Builder<?, ?, ?> builder;
			FieldType.MapType<?, ?, ?> mapType = (FieldType.MapType<?, ?, ?>) type;
			if (mapType.isSorted)
				builder = ObservableSortedMap.build(mapType.keyType);
			else
				builder = ObservableMap.build();
			return (T) builder.withLocking(getEntitySet().getLock()).withDescription(this + "." + field.getName()).build();
		} else if (type instanceof FieldType.MultiMapType) {
			ObservableMultiMap.Builder<?, ?, ?> builder;
			FieldType.MultiMapType<?, ?, ?> mapType = (FieldType.MultiMapType<?, ?, ?>) type;
			if (mapType.isSorted)
				builder = ObservableMultiMap.build().sortedBy((Comparator<Object>) mapType.keyType);
			else
				builder = ObservableMultiMap.build();
			if (field.getMapping() != null && field.getMapping().sortByField != null)
				builder = ((ObservableMultiMap.Builder<?, GenericEntity, ?>) builder)
				.withValueEquivalence(Equivalence.DEFAULT.sorted(field.getMapping().entitySort, true));
			return (T) builder.withLocking(getEntitySet().getLock()).withDescription(this + "." + field.getName()).build(getUntil());
		} else
			throw new IllegalStateException("Unrecognized parameterized field type: " + type);
	}

	@Override
	protected <F, K, S> F controlMappedStructure(F structure, FieldMapping<F, K, S> field) {
		FieldType<F> type = field.parentField.getType();
		if (type instanceof FieldType.CollectionType) {
			MappedEntityCollectionControl<?> control = new MappedEntityCollectionControl<>(
				(FieldMapping<? extends BetterCollection<GenericEntity>, Void, S>) field, this);
			ObservableCollection<GenericEntity> collection = ModControlledObservableCollection
				.controlCollection((ObservableCollection<GenericEntity>) structure, control, control);
			((MappedEntityCollectionControl<ObservableCollection<GenericEntity>>) control).init(collection);
			return (F) collection;
		} else if (type instanceof FieldType.MapType) {
			MappedEntityMapControl<Object, ?> control = new MappedEntityMapControl<>(
				(FieldMapping<? extends BetterMap<Object, GenericEntity>, Object, S>) field, this);
			ObservableMap<Object, GenericEntity> map = ModControlledObservableMap
				.controlMap((ObservableMap<Object, GenericEntity>) structure, control, control);
			((MappedEntityMapControl<Object, ObservableMap<Object, GenericEntity>>) control).init(map);
			return (F) map;
		} else if (type instanceof FieldType.MultiMapType) {
			MappedEntityMultiMapControl<Object, ?> control = new MappedEntityMultiMapControl<>(
				(FieldMapping<? extends BetterMultiMap<Object, GenericEntity>, Object, S>) field, this);
			ObservableMultiMap<Object, GenericEntity> map = ModControlledObservableMultiMap
				.controlMultiMap((ObservableMultiMap<Object, GenericEntity>) structure, control, control);
			((MappedEntityMultiMapControl<Object, BetterMultiMap<Object, GenericEntity>>) control).init(map);
			return (F) map;
		} else
			throw new IllegalStateException("Unrecognized mapped field type: " + type);
	}

	@Override
	protected <K, V, F> F controlUnmappedStructure(F structure, EntityField<F> field) {
		if (field.getType() instanceof FieldType.CollectionType) {
			return (F) ModControlledObservableCollection.controlCollection((ObservableCollection<V>) structure, null,
				new MemberCollectionControl<>(this, field));
		} else if (field.getType() instanceof FieldType.MapType) {
			return (F) ModControlledObservableMap.controlMap((ObservableMap<K, V>) structure, null, new MemberMapControl<>(this, field));
		} else if (field.getType() instanceof FieldType.MultiMapType) {
			return (F) ModControlledObservableMultiMap.controlMultiMap((ObservableMultiMap<K, V>) structure, null,
				new MemberMultiMapControl<>(this, field));
		} else
			throw new IllegalStateException("Unrecognized structure field type: " + field.getType());
	}

	@Override
	protected void deleted() {
		getEntitySet().deleteEntity(this);
	}

	@Override
	public Object get(int fieldIndex) {
		return theRealFieldValues[fieldIndex];
	}

	@Override
	public void set(int fieldIndex, Object newValue) {
		ReflectedFieldType<E, ?, ?> field = theType.getFields().get(fieldIndex);
		Function<?, ?> reverse = field.getGenericMapping();
		if (reverse == null)
			throw new IllegalArgumentException(isEnabled(fieldIndex).get());
		set(field.getGenericField(), ((Function<Object, ?>) reverse).apply(newValue));
	}

	private ListenerList<Consumer<FieldChange<?>>> getListeners(int fieldIndex) {
		if (theListeners == null)
			theListeners = new ListenerList[getType().getFields().size()];
		if (theListeners[fieldIndex] == null)
			theListeners[fieldIndex] = ListenerList.build().build();
		return theListeners[fieldIndex];
	}

	@Override
	public Subscription addListener(E entity, int fieldIndex, Consumer<FieldChange<?>> listener) {
		return getListeners(fieldIndex).add(listener, true);
	}

	@Override
	public CausalLock getLock(int fieldIndex) {
		return getEntitySet().getLock();
	}

	@Override
	public boolean isEventing(int fieldIndex) {
		return theListeners != null && theListeners[fieldIndex] != null && theListeners[fieldIndex].isFiring();
	}

	@Override
	public long getStamp(int fieldIndex) {
		return getListeners(fieldIndex).getStamp();
	}

	@Override
	public ObservableValue<String> isEnabled(int fieldIndex) {
		return ObservableValue.of(isEnabled(theType.getGenericType().getFields().get(fieldIndex)));
	}

	@Override
	public String isAcceptable(int fieldIndex, Object value) {
		return isAcceptable(theType.getGenericType().getFields().get(fieldIndex), value);
	}
}
