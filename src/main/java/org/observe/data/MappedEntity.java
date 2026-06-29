package org.observe.data;

import java.util.Comparator;
import java.util.function.Function;

import org.observe.Equivalence;
import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValue.Getter;
import org.observe.Observer;
import org.observe.SettableValue.Setter;
import org.observe.SettableValueListening;
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
	private SettableValueListening<FieldChange<?>>[] theListeners;
	private SimpleObservable<Void> theUntil;

	/**
	 * @param type The entity type of this entity
	 * @param entitySet The entity set this entity belongs to
	 * @param id The ID values for this entity
	 */
	public MappedEntity(ReflectedEntityValueType<E> type, ReflectedEntitySet entitySet, Object[] id) {
		super(type.getGenericType(), entitySet, id);
		theType = type;
		theRealFieldValues = new Object[type.getReflector().getFields().keySize()];
		theRealEntity = theType.getReflector().newInstance(this);
		int f = 0;
		for (ReflectedFieldType<E, ?, ?> field : theType.getFields().allValues()) {
			int reflectedIndex = theType.genericToReflected().toDest(f);
			theRealFieldValues[reflectedIndex] = ((RealFieldValueProducer<Object, ?>) field.getRealMapping()).genericToReal(//
				get(field.getGenericField()), entitySet, this);
			f++;
		}
		EntityReflector.associate(theRealEntity, ENTITY_ASSOC, this);
		theType.getReflector().init(theRealEntity);
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
		try (Transaction t = getEntitySet().lockWrite(false, null)) {
			super.set(field, value);
			int genericIndex = theType.getGenericType().indexOf(field);
			int reflectedIndex = theType.genericToReflected().toDest(genericIndex);
			Object oldValue = theRealFieldValues[reflectedIndex];
			Object newValue = ((RealFieldValueProducer<Object, Object>) theType.getFields().get(genericIndex).getRealMapping()).asFunction()
				.apply(value);
			theRealFieldValues[reflectedIndex] = newValue;
			if (theListeners != null) {
				SettableValueListening<FieldChange<?>> listeners = theListeners[reflectedIndex];
				if (listeners != null) {
					FieldChange<Object> change = new FieldChange<>(oldValue, newValue, getEntitySet().getLock().getRootCausable());
					listeners.fire(change);
				}
			}
			getEntitySet().entityAffected(this);
		}
		return this;
	}

	@Override
	public Getter<?> getter(int fieldIndex, boolean tryOnly) {
		Transaction esLock = getEntitySet().lock(tryOnly);
		if (esLock == null)
			return null;
		return new Getter<Object>() {
			@Override
			public Object get() {
				return theRealFieldValues[fieldIndex];
			}

			@Override
			public void close() {
				esLock.close();
			}
		};
	}

	@Override
	public Setter<?> setter(int fieldIndex, boolean tryOnly, Object cause) {
		Transaction esLock = getEntitySet().lockWrite(tryOnly, cause);
		if (esLock == null)
			return null;
		int genericIndex = theType.genericToReflected().toSource(fieldIndex);
		SettableValueListening<FieldChange<?>> listeners = theListeners[fieldIndex];
		Transaction listenerLock;
		if (listeners != null) {
			Transaction listenerLock0 = listeners.lockWrite(true, cause);
			if (listenerLock0 == null) {
				if (tryOnly) {
					esLock.close();
					return null;
				}
				do {
					esLock.close();
					esLock = getEntitySet().lockWrite(false, cause);
					listenerLock0 = listeners.lockWrite(true, cause);
				} while (listenerLock0 == null);
			}
			listenerLock = listenerLock0;
		} else
			listenerLock = Transaction.NONE;
		Transaction fEsLock = esLock;
		EntityField<?> field = theType.getGenericType().getFields().get(genericIndex);
		return new Setter<Object>() {
			@Override
			public Object get() {
				return theRealFieldValues[fieldIndex];
			}

			@Override
			public String isEnabled() {
				return MappedEntity.this.isEnabled(field);
			}

			@Override
			public String isAcceptable(Object value) {
				return MappedEntity.this.isAcceptable(field, value);
			}

			@Override
			public Object set(Object value) {
				MappedEntity.super.set(field, value);
				Object oldValue = theRealFieldValues[fieldIndex];
				Object newValue = ((RealFieldValueProducer<Object, Object>) theType.getFields().get(genericIndex).getRealMapping())
					.asFunction().apply(value);
				theRealFieldValues[fieldIndex] = newValue;
				if (theListeners != null) {
					SettableValueListening<FieldChange<?>> innerListeners = theListeners[fieldIndex];
					if (innerListeners != null) {
						FieldChange<Object> change = new FieldChange<>(oldValue, newValue, getEntitySet().getLock().getRootCausable());
						innerListeners.fire(change);
					}
				}
				getEntitySet().entityAffected(MappedEntity.this);
				return oldValue;
			}

			@Override
			public void close() {
				listenerLock.close();
				fEsLock.close();
			}
		};
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
			return (T) builder.withLocking(getEntitySet().getLock()).withDescription(this + "." + field.getName()).buildMap();
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
		EntityReflector.destroyEntity(theRealEntity);
	}

	@Override
	public Object get(int fieldIndex) {
		return theRealFieldValues[fieldIndex];
	}

	@Override
	public void set(int fieldIndex, Object newValue) {
		int genericIndex = theType.genericToReflected().toSource(fieldIndex);
		if (genericIndex < 0) {
			theRealFieldValues[fieldIndex] = newValue;
			return;
		}
		ReflectedFieldType<E, ?, ?> field = theType.getFields().get(genericIndex);
		Function<?, ?> reverse = field.getGenericMapping();
		if (reverse == null)
			throw new IllegalArgumentException(isEnabled(fieldIndex).get());
		set(field.getGenericField(), ((Function<Object, ?>) reverse).apply(newValue));
	}

	private SettableValueListening<FieldChange<?>> getListeners(int fieldIndex) {
		if (theListeners == null)
			theListeners = new SettableValueListening[getType().getFields().size()];
		if (theListeners[fieldIndex] == null)
			theListeners[fieldIndex] = new SettableValueListening<>(null, null, ListenerList.build().skipAddByDefault(true).build());
		return theListeners[fieldIndex];
	}

	@Override
	public Subscription addListener(E entity, int fieldIndex, Observer<FieldChange<?>> listener) {
		return getListeners(fieldIndex).subscribe(listener);
	}

	@Override
	public CausalLock getLock(int fieldIndex) {
		return getEntitySet().getLock();
	}

	@Override
	public boolean isEventing(int fieldIndex) {
		return theListeners != null && theListeners[fieldIndex] != null && theListeners[fieldIndex].isEventing();
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
