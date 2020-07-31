package org.observe.entity.impl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.function.Consumer;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.Observer;
import org.observe.SettableValue;
import org.observe.Subscription;
import org.observe.assoc.ObservableMap;
import org.observe.assoc.ObservableMultiMap;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollectionEvent;
import org.observe.collect.ObservableSet;
import org.observe.collect.ObservableSortedSet;
import org.observe.config.ConfigurableValueCreator;
import org.observe.config.ConfiguredValueField;
import org.observe.config.ConfiguredValueType;
import org.observe.config.ObservableCreationResult;
import org.observe.config.ObservableValueSet;
import org.observe.config.OperationResult;
import org.observe.config.OperationResult.ResultStatus;
import org.observe.config.ValueOperationException;
import org.observe.entity.ConfigurableCreator;
import org.observe.entity.EntityChainAccess;
import org.observe.entity.EntityChange;
import org.observe.entity.EntityChange.EntityFieldValueChange;
import org.observe.entity.EntityChange.FieldChange;
import org.observe.entity.EntityIdentity;
import org.observe.entity.EntityModificationResult;
import org.observe.entity.EntityOperationException;
import org.observe.entity.EntityUpdate;
import org.observe.entity.EntityValueAccess;
import org.observe.entity.ObservableEntity;
import org.observe.entity.ObservableEntityField;
import org.observe.entity.ObservableEntityFieldEvent;
import org.observe.entity.ObservableEntityFieldType;
import org.observe.entity.ObservableEntityProvider.CollectionOperationType;
import org.observe.entity.ObservableEntityType;
import org.observe.util.EntityReflector;
import org.observe.util.EntityReflector.EntityFieldChangeEvent;
import org.observe.util.EntityReflector.ObservableField;
import org.observe.util.EntityReflector.ReflectedField;
import org.observe.util.ObservableCollectionWrapper;
import org.observe.util.TypeTokens;
import org.qommons.Causable;
import org.qommons.Identifiable;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterMap;
import org.qommons.collect.BetterMultiMap;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.CollectionLockingStrategy;
import org.qommons.collect.ElementId;
import org.qommons.collect.ListenerList;
import org.qommons.collect.MultiMap;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.collect.QuickSet.QuickMap;
import org.qommons.collect.RRWLockingStrategy;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

class ObservableEntityImpl<E> implements ObservableEntity<E> {
	private static class FieldObserver<E> implements Observer<ObservableEntityFieldEvent<E, ?>> {
		// We'll do this by index if we can, but if the entity is a subclass, the index may be different
		private final int theFieldIndex;
		private final Observer<? super ObservableEntityFieldEvent<E, ?>> theListener;

		FieldObserver(int fieldIndex, Observer<? super ObservableEntityFieldEvent<E, ?>> listener) {
			theFieldIndex = fieldIndex;
			theListener = listener;
		}

		@Override
		public <V extends ObservableEntityFieldEvent<E, ?>> void onNext(V value) {
			ObservableEntityFieldType<E, ?> field = value.getField();
			if (field.getIndex() != theFieldIndex)
				return;
			theListener.onNext(value);
		}

		@Override
		public <V extends ObservableEntityFieldEvent<E, ?>> void onCompleted(V value) {
			ObservableEntityFieldType<E, ?> field = value.getField();
			if (field.getIndex() != theFieldIndex)
				return;
			theListener.onCompleted(value);
		}
	}

	private final ObservableEntityTypeImpl<E> theType;
	private final EntityIdentity<E> theId;
	private final E theEntity;
	private final QuickMap<String, Object> theFields;
	private long theStamp;
	private ListenerList<Observer<? super ObservableEntityFieldEvent<E, ?>>> theFieldObservers;
	private volatile boolean isPresent;

	ObservableEntityImpl(ObservableEntityTypeImpl<E> type, EntityIdentity<E> id) {
		theType = type;
		theId = id;
		theFields = type.getFields().keySet().createMap();
		for (int f = 0; f < theFields.keySize(); f++) {
			if (type.getFields().get(f).getIdIndex() >= 0)
				theFields.put(f, id.getFields().get(theType.getFields().get(f).getIdIndex()));
			else
				theFields.put(f, EntityUpdate.NOT_SET);
		}
		if (type.getReflector() == null)
			theEntity = null;
		else {
			theEntity = type.getReflector().newInstance(new EntityReflector.ObservableEntityInstanceBacking<E>() {
				@Override
				public Object get(int fieldIndex) {
					return ObservableEntityImpl.this.get(fieldIndex);
				}

				@Override
				public void set(int fieldIndex, Object newValue) {
					ObservableEntityImpl.this.set(fieldIndex, newValue, null);
				}

				@Override
				public Subscription addListener(E entity, int fieldIndex, Consumer<EntityReflector.FieldChange<?>> listener) {
					throw new IllegalStateException("watchField() is overridden, so this should not be called");
				}

				@Override
				public Transactable getLock(int fieldIndex) {
					return theType.getEntitySet();
				}

				@Override
				public long getStamp(int fieldIndex) {
					return theStamp;
				}

				@Override
				public String isAcceptable(int fieldIndex, Object value) {
					return ObservableEntityImpl.this.isAcceptable(fieldIndex, value);
				}

				@Override
				public ObservableValue<String> isEnabled(int fieldIndex) {
					ObservableEntityFieldType<E, ?> field = theType.getFields().get(fieldIndex);
					if (field.getIdIndex() >= 0)
						return ObservableEntityField.ID_FIELD_UNSETTABLE_VALUE;
					// TODO Check constraints
					return SettableValue.ALWAYS_ENABLED;
				}

				@Override
				public Subscription watchField(E entity, int fieldIndex, Consumer<? super EntityFieldChangeEvent<E, ?>> listener) {
					return allFieldChanges().subscribe(new FieldObserver<>(fieldIndex, new Observer<ObservableEntityFieldEvent<E, ?>>() {
						@Override
						public <V extends ObservableEntityFieldEvent<E, ?>> void onNext(V value) {
							listener.accept(value);
						}

						@Override
						public <V extends ObservableEntityFieldEvent<E, ?>> void onCompleted(V value) {}
					}));
				}

				@Override
				public Subscription watchAllFields(E entity, Consumer<? super EntityFieldChangeEvent<E, ?>> listener) {
					return allFieldChanges().act(listener);
				}

				@Override
				public <F> ObservableField<E, F> observeField(E entity, ReflectedField<E, F> field) {
					return ObservableEntityImpl.this.getField((ObservableEntityFieldType<E, F>) getField(field.getFieldIndex()));
				}

				@Override
				public <F> EntityFieldChangeEvent<E, F> createFieldChangeEvent(E entity, ReflectedField<E, F> field, F oldValue, F newValue,
					Object cause) {
					return new ObservableEntityFieldEvent<>(ObservableEntityImpl.this,
						(ObservableEntityFieldType<E, F>) getField(field.getFieldIndex()), oldValue, newValue, cause);
				}
			});
			type.getReflector().associate(theEntity, type, this);
		}
		theStamp = Double.doubleToLongBits(Math.random());
		isPresent = true;
	}

	@Override
	public ObservableEntityTypeImpl<E> getType() {
		return theType;
	}

	@Override
	public EntityIdentity<E> getId() {
		return theId;
	}

	@Override
	public E getEntity() {
		return theEntity;
	}

	@Override
	public long getStamp() {
		return theStamp;
	}

	@Override
	public Object get(int fieldIndex) {
		Object value = theFields.get(fieldIndex);
		if (value == EntityUpdate.NOT_SET) {
			try {
				load(theType.getFields().get(fieldIndex), null, null);
			} catch (EntityOperationException e) {
				throw new IllegalStateException("Could not load field " + theId + "." + theType.getFields().get(fieldIndex), e);
			}
			value = theFields.get(fieldIndex);
		}
		if (value instanceof ObservableEntity && theType.getFields().get(fieldIndex).getTargetEntity().getType() != null)
			value = ((ObservableEntity<?>) value).getEntity();
		return value;
	}

	@Override
	public ObservableEntity<?> getEntity(int fieldIndex) {
		if (theType.getFields().get(fieldIndex).getTargetEntity() == null)
			throw new IllegalArgumentException(theType.getFields().get(fieldIndex) + " is not an entity-typed field");
		Object value = theFields.get(fieldIndex);
		if (value == EntityUpdate.NOT_SET) {
			try {
				load(theType.getFields().get(fieldIndex), null, null);
			} catch (EntityOperationException e) {
				throw new IllegalStateException("Could not load field " + theId + "." + theType.getFields().get(fieldIndex), e);
			}
			value = theFields.get(fieldIndex);
		}
		return (ObservableEntity<?>) value;
	}

	@Override
	public String isAcceptable(int fieldIndex, Object value) {
		if (!isPresent)
			return ENTITY_REMOVED;
		return theType.getEntitySet().isAcceptable(this, fieldIndex, value);
	}

	@Override
	public <F> F set(int fieldIndex, F value, Object cause) {
		F old = _set(fieldIndex, value, cause);
		if (old instanceof ObservableEntity && theType.getFields().get(fieldIndex).getTargetEntity().getType() != null)
			old = (F) ((ObservableEntity<?>) old).getEntity();
		return old;
	}

	@Override
	public <F> ObservableEntity<F> setEntity(int fieldIndex, ObservableEntity<F> value, Object cause)
		throws UnsupportedOperationException, IllegalArgumentException {
		if (theType.getFields().get(fieldIndex).getTargetEntity() == null)
			throw new IllegalArgumentException(theType.getFields().get(fieldIndex) + " is not an entity-typed field");
		return _set(fieldIndex, value, cause);
	}

	<F> F _set(int fieldIndex, F value, Object cause) {
		if (!isPresent)
			throw new UnsupportedOperationException(ENTITY_REMOVED);
		String msg = isAcceptable(fieldIndex, value);
		if (msg == StdMsg.UNSUPPORTED_OPERATION || msg == ObservableEntityField.ID_FIELD_UNSETTABLE || msg == ENTITY_REMOVED)
			throw new UnsupportedOperationException(msg);
		else if (msg != null)
			throw new IllegalArgumentException(msg);
		ObservableEntityFieldType<E, F> field = (ObservableEntityFieldType<E, F>) theType.getFields().get(fieldIndex);
		Class<?> raw = TypeTokens.getRawType(field.getFieldType());
		if (Collection.class.isAssignableFrom(raw) || ObservableValueSet.class.isAssignableFrom(raw)//
			|| Map.class.isAssignableFrom(raw) || MultiMap.class.isAssignableFrom(raw))
			throw new UnsupportedOperationException("Fields of type " + raw.getName() + " cannot be set directly");

		try (Transaction t = theType.getEntitySet().lock(true, null)) {
			Object oldEntry = theFields.get(fieldIndex);
			F oldValue;
			if (oldEntry == EntityUpdate.NOT_SET) {
				try {
					load(field, null, null);
				} catch (EntityOperationException e) {
					throw new IllegalStateException("Could not load field " + theType.getFields().get(fieldIndex), e);
				}
				oldValue = (F) theFields.get(fieldIndex);
			} else
				oldValue = (F) oldEntry;
			if (isEquivalent(oldValue, value)) {
				_set(field, oldValue, value);
				return oldValue;
			}
			/* TODO There is a vulnerability here.
			 * Say this is called v0->v1 and the action is queued, then called again v1->v2 with another action queued.
			 * If both actions fail and the events from each get queued in order,
			 * the first action will re-install v0, then the second action will install v1,
			 * resulting in a final state not the same as the initial.
			 *
			 * We need to create some kind of structure to keep track of such ongoing asynchronous field operations.
			 * When they are stacked, the previous operation should be canceled and the "old value" should be tracked there.
			 */
			try {
				theType.getEntitySet().queueAction(sync -> {
					try {
						EntityModificationResult<E> result;
						if (sync) {
							result = theType.select(false).entity(theId).update().withField(field, value).execute(true, cause);
							_set(field, oldValue, value);
							theType.getEntitySet().processChangeFromEntity(
								new EntityChange.EntityFieldValueChange<>(theType, Instant.now(), BetterList.of(getId()),
									BetterList.of(new EntityChange.FieldChange<>(field, BetterList.of(oldValue), value)), null));
						} else {
							_set(field, oldValue, value);
							theType.getEntitySet().processChangeFromEntity(
								new EntityChange.EntityFieldValueChange<>(theType, Instant.now(), BetterList.of(getId()),
									BetterList.of(new EntityChange.FieldChange<>(field, BetterList.of(oldValue), value)), null));
							result = theType.select(false).entity(theId).update().withField(field, value).execute(false, cause);
							result.watchStatus().act(__ -> {
								// If the operation failed and the field
								if (result.getFailure() != null && theFields.get(fieldIndex) == value) {
									_set(field, value, oldValue);
									try {
										theType.getEntitySet().processChangeFromEntity(new EntityChange.EntityFieldValueChange<>(theType,
											Instant.now(), BetterList.of(getId()),
											BetterList.of(new EntityChange.FieldChange<>(field, BetterList.of(value), oldValue)), null));
									} catch (EntityOperationException e) {
										// Well, we tried
										e.printStackTrace();
									}
								}
							});
						}
						return result;
					} catch (RuntimeException | EntityOperationException | Error e) {
						e.printStackTrace();
						throw e;
					}
				});
			} catch (IllegalStateException | EntityOperationException e) {
				throw new IllegalArgumentException("Update failed", e);
			}
			return oldEntry == EntityUpdate.NOT_SET ? null : (F) oldEntry;
		}
	}

	private boolean isEquivalent(Object oldValue, Object newValue) {
		if (oldValue == newValue)
			return true;
		else if (oldValue == null)
			return false;
		else if (oldValue instanceof Collection || oldValue instanceof ObservableValueSet || oldValue instanceof Map
			|| oldValue instanceof MultiMap)
			return false;
		else
			return oldValue.equals(newValue);
	}

	@Override
	public EntityModificationResult<E> update(int fieldIndex, Object value, boolean sync, Object cause)
		throws IllegalArgumentException, EntityOperationException {
		if (!isPresent)
			throw new UnsupportedOperationException(ENTITY_REMOVED);
		String msg = isAcceptable(fieldIndex, value);
		if (msg == StdMsg.UNSUPPORTED_OPERATION || msg == ObservableEntityField.ID_FIELD_UNSETTABLE || msg == ENTITY_REMOVED)
			throw new UnsupportedOperationException(msg);
		else if (msg != null)
			throw new IllegalArgumentException(msg);
		ObservableEntityFieldType<E, Object> field = (ObservableEntityFieldType<E, Object>) theType.getFields().get(fieldIndex);
		try {
			return theType.select().entity(theId).update().withField(field, value).execute(true, cause);
		} catch (IllegalStateException | EntityOperationException e) {
			throw new IllegalArgumentException("Update failed", e);
		}
	}

	@Override
	public boolean isLoaded(ObservableEntityFieldType<? super E, ?> field) {
		if (!field.getOwnerType().equals(theType))
			field = theType.getFields().get(field.getName());
		return theFields.get(field.getIndex()) != EntityUpdate.NOT_SET;
	}

	@Override
	public <F> ObservableEntity<E> load(ObservableEntityFieldType<E, F> field, Consumer<? super F> onLoad,
		Consumer<EntityOperationException> onFail) throws EntityOperationException {
		Object value = theFields.get(field.getIndex());
		if (value != EntityUpdate.NOT_SET) {
			if (onLoad != null)
				onLoad.accept((F) value);
		} else
			theType.getEntitySet().loadField(this, field, onLoad, onFail);
		return this;
	}

	@Override
	public boolean isPresent() {
		return isPresent;
	}

	@Override
	public String canDelete() {
		if (!isPresent)
			return ENTITY_REMOVED;
		return theType.getEntitySet().canDelete(this);
	}

	@Override
	public void delete(Object cause) throws UnsupportedOperationException {
		if (!isPresent)
			throw new UnsupportedOperationException(ENTITY_REMOVED);
		try {
			theType.select().entity(theId).delete().execute(true, cause);
		} catch (IllegalStateException | EntityOperationException e) {
			throw new UnsupportedOperationException("Delete failed", e);
		}
	}

	void handleChange(EntityChange<E> change, int entityIndex, Map<EntityIdentity<?>, ObservableEntity<?>> entities) {
		switch (change.changeType) {
		case add:
		case remove:
			throw new IllegalStateException("Shouldn't receive this: " + change);
		case setField:
			EntityChange.EntityFieldValueChange<E> fieldValueChange = (EntityFieldValueChange<E>) change;
			for (FieldChange<E, ?> fieldChange : fieldValueChange.getFieldChanges()) {
				FieldChange<E, Object> fc = (FieldChange<E, Object>) fieldChange;
				_set(fc.getField(), fc.getOldValues().get(entityIndex), fc.getNewValue());
			}
			break;
		case updateCollectionField:
			break; // TODO
		case updateMapField:
			break; // TODO
		}
	}

	<F> void _set(ObservableEntityFieldType<? super E, F> field, F oldValue, F newValue) {
		if (field.getOwnerType() != theType)
			field = (ObservableEntityFieldType<E, F>) theType.getFields().get(field.getName());
		Object myOldValue = theFields.get(field.getIndex());
		theFields.put(field.getIndex(), resolve(field, field.getFieldType(), field.getTargetEntity(), myOldValue, newValue));
		if (myOldValue != EntityUpdate.NOT_SET) {
			theStamp++;
			if (oldValue == EntityUpdate.NOT_SET)
				oldValue = (F) myOldValue;
			if (theFieldObservers != null) {
				ObservableEntityFieldEvent<E, F> event = new ObservableEntityFieldEvent<>(this, (ObservableEntityFieldType<E, F>) field,
					oldValue, newValue, null);
				try (Transaction evtT = Causable.use(event)) {
					theFieldObservers.forEach(//
						listener -> listener.onNext(event));
				}
			}
		}
	}

	private <F> F resolve(ObservableEntityFieldType<? super E, ?> field, TypeToken<F> type, ObservableEntityType<F> target, Object oldValue,
		Object newValue) {
		Class<F> raw = TypeTokens.getRawType(type);
		if (Collection.class.isAssignableFrom(raw)) {
			newValue = (F) setCollection(localField(field, type, "Component collection not supported for field "), oldValue,
				(BetterCollection<?>) newValue);
		} else if (ObservableValueSet.class.isAssignableFrom(raw)) {
			newValue = (F) setValueSet(localField(field, type, "Component value set not supported for field "), oldValue,
				(BetterCollection<?>) newValue);
		} else if (Map.class.isAssignableFrom(raw)) {
			newValue = (F) setMap(localField(field, type, "Component map not supported for field "), oldValue, (BetterMap<?, ?>) newValue);
		} else if (MultiMap.class.isAssignableFrom(raw)) {
			newValue = (F) setMultiMap(localField(field, type, "Component multi-map not supported for field "), oldValue,
				(BetterMultiMap<?, ?>) newValue);
		}
		if (target != null) {
			if (newValue == null)
				return null;
			else if (newValue instanceof EntityIdentity) {
				try {
					newValue = target.observableEntity((EntityIdentity<F>) newValue);
				} catch (EntityOperationException e) {
					System.err.println("Unable to resolve " + newValue);
					e.printStackTrace();
					return null;
				}
			}
			// Some type-meddling here, but should be safe at run time
			if (TypeTokens.get().isInstance(type, newValue))
				return (F) newValue;
			if (newValue instanceof ObservableEntity)
				return ((ObservableEntity<? extends F>) newValue).getEntity();
			else
				return (F) target.observableEntity((F) newValue);
		}
		return (F) newValue;
	}

	private <F> ObservableEntityFieldType<E, ? extends F> localField(EntityValueAccess<? super E, ?> field, TypeToken<?> type,
		String errMsg) {
		if (!field.getValueType().equals(type))
			throw new IllegalStateException(errMsg + ((EntityChainAccess<? super E, F>) field).getFieldSequence().getFirst());
		ObservableEntityFieldType<? super E, F> f = (ObservableEntityFieldType<? super E, F>) field;
		if (f.getSourceEntity() != theType)
			f = (ObservableEntityFieldType<E, F>) theType.getFields().get(f.getName());
		return (ObservableEntityFieldType<E, ? extends F>) f;
	}

	private <V, C extends Collection<V>> C setCollection(ObservableEntityFieldType<E, C> field, Object oldValue,
		BetterCollection<V> newValue) {
		ObservableCollection<V> collection;
		if (oldValue == EntityUpdate.NOT_SET)
			collection = initCollection(field, newValue);
		else
			collection = (ObservableCollection<V>) oldValue;
		return (C) collection;
	}

	private <V, C extends ObservableValueSet<V>> C setValueSet(ObservableEntityFieldType<E, C> field, Object oldValue,
		BetterCollection<V> newValue) {
		ObservableValueSet<V> valueSet;
		if (oldValue == EntityUpdate.NOT_SET)
			valueSet = initValueSet(field, newValue);
		else
			valueSet = (ObservableValueSet<V>) oldValue;
		return (C) valueSet;
	}

	private <K, V, M extends Map<K, V>> M setMap(ObservableEntityFieldType<E, M> field, Object oldValue, BetterMap<K, V> newValue) {
		ObservableMap<K, V> map;
		if (oldValue == EntityUpdate.NOT_SET)
			map = initMap(field, newValue);
		else
			map = (ObservableMap<K, V>) oldValue;
		return (M) map;
	}

	private <K, V, M extends MultiMap<K, V>> M setMultiMap(ObservableEntityFieldType<E, M> field, Object oldValue,
		BetterMultiMap<K, V> newValue) {
		ObservableMultiMap<K, V> map;
		if (oldValue == EntityUpdate.NOT_SET)
			map = initMultiMap(field, newValue);
		else
			map = (ObservableMultiMap<K, V>) oldValue;
		return (M) map;
	}

	void removed(Object cause) {
		if (theFieldObservers == null)
			return;
		isPresent = false;
		for (int f = 0; f < theFields.keySize(); f++) {
			Object oldValue = theFields.get(f);
			ObservableEntityFieldType<E, Object> field = (ObservableEntityFieldType<E, Object>) theType.getFields().get(f);
			ObservableEntityFieldEvent<E, Object> event = new ObservableEntityFieldEvent<>(this, field, oldValue, oldValue, cause);
			try (Transaction evtT = Causable.use(event)) {
				theFieldObservers.forEach(//
					listener -> listener.onCompleted(event));
			}
		}
	}

	@Override
	public Observable<ObservableEntityFieldEvent<E, ?>> allFieldChanges() {
		class FieldChangesObservable extends AbstractIdentifiable implements Observable<ObservableEntityFieldEvent<E, ?>> {
			@Override
			protected Object createIdentity() {
				return Identifiable.wrap(ObservableEntityImpl.this.getIdentity(), "fieldChanges");
			}

			@Override
			public boolean isSafe() {
				return getType().getEntitySet().isLockSupported();
			}

			@Override
			public Transaction lock() {
				return getType().getEntitySet().lock(false, null);
			}

			@Override
			public Transaction tryLock() {
				return getType().getEntitySet().tryLock(false, null);
			}

			@Override
			public Subscription subscribe(Observer<? super ObservableEntityFieldEvent<E, ?>> observer) {
				if (theFieldObservers == null) {
					synchronized (ObservableEntityImpl.this) {
						if (theFieldObservers == null)
							theFieldObservers = ListenerList.build().build();
					}
				}
				return theFieldObservers.add(observer, true)::run;
			}
		}
		return new FieldChangesObservable();
	}

	@Override
	public int hashCode() {
		return theId.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof ObservableEntity && theId.equals(((ObservableEntity<?>) obj).getId());
	}

	@Override
	public String toString() {
		return theId.toString();
	}

	static class CollectionFieldElement<V> {
		ElementId sourceId;
		V oldValue;
		V value;
		OperationResult<?> currentOp;

		CollectionFieldElement(ElementId sourceId, V value) {
			this.sourceId = sourceId;
			this.value = value;
		}

		@Override
		public String toString() {
			return String.valueOf(value);
		}
	}
	@SuppressWarnings("rawtypes")
	private static final TypeTokens.TypeKey<CollectionFieldElement> ELEMENT_TYPE = TypeTokens.get().keyFor(CollectionFieldElement.class)//
	.enableCompoundTypes(new TypeTokens.UnaryCompoundTypeCreator<CollectionFieldElement>() {
		@Override
		public <P> TypeToken<? extends CollectionFieldElement> createCompoundType(TypeToken<P> param) {
			return new TypeToken<CollectionFieldElement<P>>() {}.where(new TypeParameter<P>() {}, param);
		}
	});

	<V, C extends Collection<V>> ObservableCollection<V> initCollection(ObservableEntityFieldType<E, C> field, BetterCollection<V> source) {
		Class<C> raw=TypeTokens.getRawType(field.getFieldType());
		TypeToken<V> valueType = (TypeToken<V>) field.getValueType().resolveType(Collection.class.getTypeParameters()[0]);
		ObservableEntityType<V> targetEntity = (ObservableEntityType<V>) field.getValueTarget();
		EntityCollectionField<V> collection;
		if (raw.isAssignableFrom(ObservableSortedSet.class))
			return new EntitySortedSetField<>(field, valueType, targetEntity, source);
		else if (raw.isAssignableFrom(ObservableSet.class))
			collection = new EntitySetField<>(field, valueType, targetEntity, source);
		else if (raw.isAssignableFrom(ObservableCollection.class))
			collection = new EntityListField<>(field, valueType, targetEntity, source);
		else
			throw new IllegalStateException("Cannot satisfy collection of type "+raw.getName()+" for field "+field);
		collection.init();
		return collection;
	}

	private <V, C extends ObservableValueSet<V>> ObservableValueSet<V> initValueSet(ObservableEntityFieldType<E, C> field,
		BetterCollection<V> source) {
		Class<C> raw = TypeTokens.getRawType(field.getFieldType());
		if (!raw.isAssignableFrom(ObservableValueSet.class))
			throw new IllegalStateException("Cannot satisfy value set of type " + raw.getName() + " for field " + field);
		if (field.getValueTarget() == null)
			throw new IllegalStateException("Cannot create an ObservableValueSets of type "
				+ field.getFieldType().resolveType(ObservableValueSet.class.getTypeParameters()[0])
				+ "--ObservableValueSets can only be created for entities");
		return new EntitySetImpl<>(field, (ObservableEntityType<V>) field.getValueTarget(), source);
	}

	abstract class EntityCollectionField<V> extends ObservableCollectionWrapper<V> {
		private final BetterCollection<V> theSource;
		private final ObservableEntityFieldType<E, ?> theField;
		private final ObservableEntityType<V> theEntityType;
		private final TypeToken<V> theValueType;
		private final Map<ElementId, ElementId> theElementMapping;
		private ObservableCollection<CollectionFieldElement<V>> theElements;
		private ObservableCollection<V> theValues;
		private Object theIdentity;

		EntityCollectionField(ObservableEntityFieldType<E, ?> field, TypeToken<V> valueType, ObservableEntityType<V> targetEntity,
			BetterCollection<V> source) {
			theSource = source;
			theField = field;
			theValueType = valueType;
			theElementMapping = new HashMap<>();
			theEntityType = (ObservableEntityType<V>) field.getValueTarget();
		}

		protected void init() {
			theElements = createCollection(
				(TypeToken<CollectionFieldElement<V>>) (TypeToken<?>) ELEMENT_TYPE.getCompoundType(theField.getFieldType()),
				new RRWLockingStrategy(theType.getEntitySet()));
			theValues = createValues(theElements);
			for (CollectionElement<V> element : theSource.elements())
				theElementMapping.put(element.getElementId(), //
					theElements.addElement(new CollectionFieldElement<>(element.getElementId(), element.get()), false).getElementId());
			init(theValues);
		}

		protected ObservableCollection<CollectionFieldElement<V>> getElements() {
			return theElements;
		}

		protected ObservableCollection<V> getValues() {
			return theValues;
		}

		protected abstract ObservableCollection<CollectionFieldElement<V>> createCollection(TypeToken<CollectionFieldElement<V>> type,
			CollectionLockingStrategy locking);

		protected abstract ObservableCollection<V> createValues(ObservableCollection<CollectionFieldElement<V>> elements);

		ElementId applyChange(CollectionChangeType type, ElementId sourceEl, ElementId valueId, V value) {
			switch (type) {
			case add:
				value = resolve(null, theValueType, theEntityType, null, value);
				ElementId prevEl = CollectionElement.getElementId(theSource.getAdjacentElement(sourceEl, false));
				while (prevEl != null && !theElementMapping.containsKey(prevEl))
					prevEl = CollectionElement.getElementId(theSource.getAdjacentElement(sourceEl, false));
				prevEl = prevEl == null ? null : theElementMapping.get(prevEl);
				ElementId nextEl = CollectionElement.getElementId(theSource.getAdjacentElement(sourceEl, true));
				while (nextEl != null && !theElementMapping.containsKey(nextEl))
					nextEl = CollectionElement.getElementId(theSource.getAdjacentElement(sourceEl, true));
				nextEl = nextEl == null ? null : theElementMapping.get(nextEl);
				valueId = theElements.addElement(new CollectionFieldElement<>(sourceEl, value), prevEl, nextEl, false).getElementId();
				theElementMapping.put(sourceEl, valueId);
				break;
			case remove:
				if (sourceEl != null)
					theElementMapping.remove(sourceEl);
				theElements.mutableElement(valueId).remove();
				break;
			case set:
				CollectionFieldElement<V> el = theElements.getElement(valueId).get();
				value = resolve(null, theValueType, theEntityType, el.value, value);
				el.oldValue = el.value;
				el.value = value;
				theElements.mutableElement(valueId).set(el);
				break;
			}
			return valueId;
		}

		@Override
		public TypeToken<V> getType() {
			return theValueType;
		}

		@Override
		public abstract boolean isContentControlled();

		protected class MutableElement implements MutableCollectionElement<V> {
			private final ElementId theElementId;
			private final CollectionFieldElement<V> theElement;

			protected MutableElement(ElementId id, CollectionFieldElement<V> element) {
				theElementId = id;
				theElement = element;
			}

			@Override
			public ElementId getElementId() {
				return theElementId;
			}

			@Override
			public V get() {
				return theElement.value;
			}

			@Override
			public BetterCollection<V> getCollection() {
				return EntityCollectionField.this;
			}

			@Override
			public String isEnabled() {
				return null;
			}

			@Override
			public String isAcceptable(V value) {
				if (value == null) {
					if (getType().isPrimitive())
						return StdMsg.NULL_DISALLOWED;
					return null;
				}
				if (!TypeTokens.get().isInstance(getType(), value))
					return StdMsg.BAD_TYPE;
				if (theEntityType != null) {
					ObservableEntity<? extends V> entity;
					if (value instanceof ObservableEntity) {
						entity = (ObservableEntity<V>) value;
					} else
						entity = theEntityType.observableEntity(value);
					if (!theEntityType.isAssignableFrom(entity.getType()))
						return StdMsg.BAD_TYPE;
					// TODO Check field constraints?
				}
				return null;
			}

			@Override
			public void set(V value) throws UnsupportedOperationException, IllegalArgumentException {
				try (Transaction t = lock(true, null)) {
					if (!theElementId.isPresent())
						throw new UnsupportedOperationException(StdMsg.ELEMENT_REMOVED);
					String msg = isAcceptable(value);
					if (StdMsg.UNSUPPORTED_OPERATION.equals(msg))
						throw new UnsupportedOperationException(msg);
					else if (msg != null)
						throw new IllegalArgumentException(msg);
					V oldValue = theElement.value;
					// TODO Need to de-resolve the value for the impl
					theType.getEntitySet().getImplementation().updateCollection(theSource, CollectionOperationType.update,
						theElement.sourceId, value, false);
					applyChange(CollectionChangeType.set, theElement.sourceId, theElementId, value);
					theType.getEntitySet().processChangeFromEntity(new EntityChange.EntityCollectionFieldChange<>(Instant.now(), getId(),
						theField, CollectionChangeType.set, theElementId, oldValue, theElement.value));
				} catch (IllegalStateException | EntityOperationException e) {
					throw new IllegalArgumentException("Update failed", e);
				}
			}

			@Override
			public String canRemove() {
				return null;
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				try (Transaction t = lock(true, null)) {
					if (!theElementId.isPresent())
						throw new UnsupportedOperationException(StdMsg.ELEMENT_REMOVED);
					String msg = canRemove();
					if (msg != null)
						throw new UnsupportedOperationException(msg);
					V oldValue = theElement.value;
					theType.getEntitySet().getImplementation().updateCollection(theSource, CollectionOperationType.remove,
						theElement.sourceId, theElement.value, false);
					applyChange(CollectionChangeType.remove, theElement.sourceId, theElementId, null);
					theType.getEntitySet().processChangeFromEntity(new EntityChange.EntityCollectionFieldChange<>(Instant.now(), getId(),
						theField, CollectionChangeType.remove, theElementId, oldValue, theElement.value));
				} catch (IllegalStateException | EntityOperationException e) {
					throw new IllegalArgumentException("Remove failed", e);
				}
			}
		}

		OperationResult<?> updateAsync(ElementId elementId, CollectionFieldElement<V> element, V oldValue, V value)
			throws EntityOperationException {
			applyChange(CollectionChangeType.set, element.sourceId, elementId, value);
			theType.getEntitySet().processChangeFromEntity(new EntityChange.EntityCollectionFieldChange<>(Instant.now(), getId(), theField,
				CollectionChangeType.set, elementId, oldValue, element.value));
			OperationResult<ElementId> result = theType.getEntitySet().getImplementation().updateCollectionAsync(theSource,
				CollectionOperationType.update, element.sourceId, value, false);
			result.whenDone(false, r -> {
				if (r.getStatus() == ResultStatus.CANCELLED) {//
				} else if (r.getStatus().isFailed()) {
					System.err.println(theField + " update failed");
					r.getFailure().printStackTrace();
					// TODO Logging
					applyChange(CollectionChangeType.set, element.sourceId, elementId, oldValue);
					try {
						theType.getEntitySet().processChangeFromEntity(new EntityChange.EntityCollectionFieldChange<>(Instant.now(),
							getId(), theField, CollectionChangeType.set, elementId, value, element.oldValue));
					} catch (EntityOperationException e2) {
						// Well, we tried
						e2.printStackTrace();
					}
				}
				if (element.currentOp == result) {
					element.currentOp = null;
					element.oldValue = null;
				}
			});
			return result;
		}

		OperationResult<?> removeAsync(CollectionFieldElement<V> element, ElementId id) throws EntityOperationException {
			V oldValue = element.value;
			applyChange(CollectionChangeType.remove, element.sourceId, id, null);
			theType.getEntitySet().processChangeFromEntity(new EntityChange.EntityCollectionFieldChange<>(Instant.now(), getId(), theField,
				CollectionChangeType.remove, id, oldValue, oldValue));
			OperationResult<ElementId> result = theType.getEntitySet().getImplementation().updateCollectionAsync(theSource,
				CollectionOperationType.remove, element.sourceId, null, false);
			result.whenDone(false, r -> {
				if (r.getStatus() == ResultStatus.CANCELLED) {//
				} else if (r.getStatus().isFailed()) {
					System.err.println(theField + " remove failed");
					r.getFailure().printStackTrace();
					// TODO Logging
					applyChange(CollectionChangeType.add, element.sourceId, null, oldValue);
					try {
						theType.getEntitySet().processChangeFromEntity(new EntityChange.EntityCollectionFieldChange<>(Instant.now(),
							getId(), theField, CollectionChangeType.add, id, null, oldValue));
					} catch (EntityOperationException e2) {
						// Well, we tried
						e2.printStackTrace();
					}
				}
				if (element.currentOp == result) {
					element.currentOp = null;
					element.oldValue = null;
				}
			});
			return result;
		}

		@Override
		public CollectionElement<V> addElement(V value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			ElementId[] newEl = new ElementId[1];
			try (Transaction t = lock(true, null)) {
				ElementId[] target = getAddLocation(value, after, before, first);
				if (target == null)
					return null;
				// Get the source ID to add before
				while (target[0] != null && theElements.getElement(target[0]).get().sourceId == null)
					target[0] = CollectionElement.getElementId(theElements.getAdjacentElement(target[0], true));
				target[0] = target[0] == null ? null : theElements.getElement(target[0]).get().sourceId;
				// TODO Need to de-resolve the value for the impl
				ElementId sourceEl = theType.getEntitySet().getImplementation().updateCollection(theSource, CollectionOperationType.add,
					target[0], value, false);
				newEl[0] = applyChange(CollectionChangeType.add, sourceEl, null, value);
				theType.getEntitySet().processChangeFromEntity(new EntityChange.EntityCollectionFieldChange<>(Instant.now(), getId(),
					theField, CollectionChangeType.add, newEl[0], null, value));
			} catch (IllegalStateException | EntityOperationException e) {
				throw new IllegalArgumentException("Addition failed", e);
			}
			return newEl[0] == null ? null : getElement(newEl[0]);
		}

		private OperationResult<ElementId> addAsync(ElementId target, V value, ElementId[] newEl) throws EntityOperationException {
			// Keeping this code unused for supporting async collection operations later
			ElementId valueBefore = target == null ? null : theElementMapping.get(target);
			ElementId valueAfter = CollectionElement.getElementId(//
				valueBefore == null//
				? theElements.getTerminalElement(false)//
					: theElements.getAdjacentElement(valueBefore, false));
			CollectionFieldElement<V> newFieldEl = new CollectionFieldElement<>(null, value);
			newEl[0] = theElements.addElement(newFieldEl, valueAfter, valueBefore, false).getElementId();
			theType.getEntitySet().processChangeFromEntity(new EntityChange.EntityCollectionFieldChange<>(Instant.now(), getId(), theField,
				CollectionChangeType.add, newEl[0], null, value));
			OperationResult<ElementId> result = theType.getEntitySet().getImplementation().updateCollectionAsync(theSource,
				CollectionOperationType.add, target, value, false);
			result.whenDone(false, r -> {
				if (r.getStatus() == ResultStatus.CANCELLED) {} else if (r.getStatus().isFailed()) {
					System.err.println(theField + " addition failed");
					r.getFailure().printStackTrace();
					// TODO Logging
					theElements.mutableElement(newEl[0]).remove();
					try {
						theType.getEntitySet().processChangeFromEntity(new EntityChange.EntityCollectionFieldChange<>(Instant.now(),
							getId(), theField, CollectionChangeType.remove, newEl[0], value, value));
					} catch (EntityOperationException e2) {
						// Well, we tried
						e2.printStackTrace();
					}
				} else {
					// Because this call is asynchronous, there is a possibility that a different call
					// changed the expected order
					ElementId sourceEl = r.getResult();
					ElementId prevSrc = CollectionElement.getElementId(theSource.getAdjacentElement(sourceEl, false));
					while (prevSrc != null && !theElementMapping.containsKey(prevSrc))
						prevSrc = CollectionElement.getElementId(theSource.getAdjacentElement(prevSrc, false));
					ElementId prevValueEl = theElementMapping.get(prevSrc);
					if (newEl[0].compareTo(prevValueEl) <= 0)
						reAdd(newEl[0], sourceEl, value);
					ElementId nextSrc = CollectionElement.getElementId(theSource.getAdjacentElement(sourceEl, true));
					while (nextSrc != null && !theElementMapping.containsKey(nextSrc))
						nextSrc = CollectionElement.getElementId(theSource.getAdjacentElement(nextSrc, true));
					ElementId nextValueEl = theElementMapping.get(nextSrc);
					if (newEl[0].compareTo(nextValueEl) >= 0)
						reAdd(newEl[0], sourceEl, value);
					newFieldEl.sourceId = sourceEl;
					newFieldEl.currentOp = null;
					theElementMapping.put(sourceEl, newEl[0]);
				}
				if (newFieldEl.currentOp == result)
					newFieldEl.currentOp = null;
			});
			newFieldEl.currentOp = result;
			return result;
		}

		protected abstract ElementId[] getAddLocation(V value, ElementId after, ElementId before, boolean first);

		void reAdd(ElementId oldEl, ElementId sourceEl, V value) {
			theElements.mutableElement(oldEl).remove();
			try {
				theType.getEntitySet().processChangeFromEntity(new EntityChange.EntityCollectionFieldChange<>(Instant.now(), getId(),
					theField, CollectionChangeType.remove, oldEl, value, value));
			} catch (EntityOperationException e) {
				// Well, we tried
				e.printStackTrace();
			}
			ElementId newEl = applyChange(CollectionChangeType.add, sourceEl, null, value);
			try {
				theType.getEntitySet().processChangeFromEntity(new EntityChange.EntityCollectionFieldChange<>(Instant.now(), getId(),
					theField, CollectionChangeType.add, newEl, null, value));
			} catch (EntityOperationException e) {
				e.printStackTrace();
			}
		}

		@Override
		public CollectionElement<V> move(ElementId valueEl, ElementId after, ElementId before, boolean first, Runnable afterRemove)
			throws UnsupportedOperationException, IllegalArgumentException {
			String msg = canMove(valueEl, after, before);
			if (StdMsg.UNSUPPORTED_OPERATION.equals(msg))
				throw new UnsupportedOperationException(msg);
			else if (msg != null)
				throw new IllegalArgumentException(msg);
			// In a sorted set, actual movement is not allowed. Since this operation is allowed, it must be trivial.
			if (this instanceof SortedSet)
				return getElement(valueEl);
			// First, see if this is a no-op
			if (first) {
				if (after == null) {
					if (getElementsBefore(valueEl) == 0)
						return getElement(valueEl);
				} else {
					int comp = valueEl.compareTo(after);
					if (comp == 0)
						return getElement(valueEl);
					else if (comp > 0 && getElementsBefore(valueEl) == getElementsBefore(after) + 1)
						return getElement(valueEl);
				}
			} else {
				if (before == null) {
					if (getElementsAfter(valueEl) == 0)
						return getElement(valueEl);
				} else {
					int comp = valueEl.compareTo(before);
					if (comp == 0)
						return getElement(valueEl);
					else if (comp < 0 && getElementsBefore(valueEl) == getElementsBefore(after) - 1)
						return getElement(valueEl);
				}
			}
			V value = getElement(valueEl).get();
			mutableElement(valueEl).remove();
			if (afterRemove != null)
				afterRemove.run();
			return addElement(value, after, before, first);
		}

		@Override
		public long getStamp() {
			return ObservableEntityImpl.this.getStamp();
		}

		@Override
		public Object getIdentity() {
			if (theIdentity == null)
				theIdentity = Identifiable.wrap(ObservableEntityImpl.this.getIdentity(), theField.getName());
			return theIdentity;
		}

		@Override
		public void clear() {
			try (Transaction t = lock(true, null)) {
				if (isEmpty())
					return;
				theType.getEntitySet().queueAction(sync -> {
					if (sync) {
						theType.getEntitySet().getImplementation().updateCollection(theSource, CollectionOperationType.clear, null, null,
							false);
						for (CollectionElement<CollectionFieldElement<V>> el : theElements.elements().reverse())
							applyChange(CollectionChangeType.remove, el.get().sourceId, el.getElementId(), null);
						return null;
					} else {
						return clearAsync();
					}
				});
			} catch (IllegalStateException | EntityOperationException e) {
				throw new UnsupportedOperationException("Clear failed", e);
			}
		}

		OperationResult<?> clearAsync() throws EntityOperationException {
			List<OperationResult<?>> results = new ArrayList<>(theElements.size());
			for (CollectionElement<CollectionFieldElement<V>> el : theElements.elements().reverse()) {
				V oldValue = el.get().value;
				applyChange(CollectionChangeType.remove, el.get().sourceId, el.getElementId(), null);
				theType.getEntitySet().processChangeFromEntity(new EntityChange.EntityCollectionFieldChange<>(Instant.now(), getId(),
					theField, CollectionChangeType.remove, el.getElementId(), oldValue, oldValue));
				OperationResult<?> result = removeAsync(el.get(), el.getElementId());
				results.add(result);
			}
			return new OperationResult.MultiResult<Object, Object>(results) {
				@Override
				protected Object getResult(List<Object> componentResults) {
					return null; // Don't care about the results, just the status
				}
			};
		}

		@Override
		public void setValue(Collection<ElementId> elements, V value) {
			try (Transaction t = lock(true, null)) {
				for (ElementId element : elements) {
					mutableElement(element).set(value);
				}
			}
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends V>> observer) {
			return theElements.onChange(elChange -> {
				V oldValue = null;
				switch (elChange.getType()) {
				case add:
					oldValue = null;
					break;
				case remove:
					oldValue = elChange.getNewValue().value;
					break;
				case set:
					oldValue = elChange.getNewValue().oldValue;
					break;
				}
				ObservableCollectionEvent<V> valueChange = new ObservableCollectionEvent<>(elChange.getElementId(), theValueType,
					elChange.getIndex(), elChange.getType(), oldValue, elChange.getNewValue().value, elChange);
				try (Transaction t = Causable.use(valueChange)) {
					observer.accept(valueChange);
				}
			});
		}
	}

	class EntityListField<V> extends EntityCollectionField<V> {
		protected EntityListField(ObservableEntityFieldType<E, ?> field, TypeToken<V> valueType, ObservableEntityType<V> targetEntity,
			BetterCollection<V> source) {
			super(field, valueType, targetEntity, source);
		}

		@Override
		protected ObservableCollection<CollectionFieldElement<V>> createCollection(TypeToken<CollectionFieldElement<V>> type,
			CollectionLockingStrategy locking) {
			return ObservableCollection.build(type).withLocker(locking).build();
		}

		@Override
		protected ObservableCollection<V> createValues(ObservableCollection<CollectionFieldElement<V>> elements) {
			return elements.flow().map(getType(), el -> el.value, opts -> opts.cache(false).fireIfUnchanged(true)).collectPassive();
		}

		@Override
		public boolean isContentControlled() {
			return false;
		}

		@Override
		protected ElementId[] getAddLocation(V value, ElementId after, ElementId before, boolean first) {
			ElementId addLoc;
			if (first)
				addLoc = CollectionElement
				.getElementId(after != null ? getElements().getAdjacentElement(after, true) : getElements().getTerminalElement(true));
			else {
				if (before != null)
					addLoc = before;
				else
					addLoc = null;
			}
			return new ElementId[] { addLoc };
		}

		@Override
		public MutableCollectionElement<V> mutableElement(ElementId id) {
			CollectionFieldElement<V> element = getElements().getElement(id).get();
			return new MutableElement(id, element);
		}
	}

	class EntitySetField<V> extends EntityListField<V> implements ObservableSet<V> {
		protected EntitySetField(ObservableEntityFieldType<E, ?> field, TypeToken<V> valueType, ObservableEntityType<V> targetEntity,
			BetterCollection<V> source) {
			super(field, valueType, targetEntity, source);
		}

		@Override
		protected ObservableSet<V> getValues() {
			return (ObservableSet<V>) super.getValues();
		}

		@Override
		protected ObservableSet<V> createValues(ObservableCollection<CollectionFieldElement<V>> elements) {
			return elements.flow().map(getType(), el -> el.value).distinct(opts -> opts.preserveSourceOrder(true)).collect();
		}

		@Override
		protected ElementId[] getAddLocation(V value, ElementId after, ElementId before, boolean first) {
			CollectionElement<V> el = getValues().getElement(value, true);
			if (el != null)
				return null;
			return super.getAddLocation(value, after, before, first);
		}

		@Override
		public boolean isContentControlled() {
			return true;
		}

		@Override
		public CollectionElement<V> getOrAdd(V value, ElementId after, ElementId before, boolean first, Runnable added) {
			CollectionElement<V> el = getElement(value, true);
			if (el != null)
				return el;
			try (Transaction t = lock(true, null)) {
				el = getElement(value, true);
				if (el != null)
					return el;
				el = addElement(value, after, before, first);
				if (added != null)
					added.run();
				return el;
			}
		}

		@Override
		public MutableCollectionElement<V> mutableElement(ElementId id) {
			CollectionFieldElement<V> element = getElements().getElement(id).get();
			return new DistinctMutableElement(id, element);
		}

		protected class DistinctMutableElement extends MutableElement {
			protected DistinctMutableElement(ElementId id, CollectionFieldElement<V> element) {
				super(id, element);
			}

			@Override
			public String isAcceptable(V value) {
				String msg = super.isAcceptable(value);
				if (msg != null)
					return msg;
				else if (Objects.equals(get(), value))
					return null;
				else if (contains(value))
					return StdMsg.ELEMENT_EXISTS;
				return null;
			}
		}

		@Override
		public boolean isConsistent(ElementId element) {
			return getValues().isConsistent(element);
		}

		@Override
		public boolean checkConsistency() {
			return getValues().checkConsistency();
		}

		@Override
		public <X> boolean repair(ElementId element, RepairListener<V, X> listener) {
			throw new UnsupportedOperationException("Not implemented");
		}

		@Override
		public <X> boolean repair(RepairListener<V, X> listener) {
			throw new UnsupportedOperationException("Not implemented");
		}
	}

	class EntitySortedSetField<V> extends EntitySetField<V> implements ObservableSortedSet<V> {
		private final Comparator<? super V> theSorting;

		protected EntitySortedSetField(ObservableEntityFieldType<E, ?> field, TypeToken<V> valueType, ObservableEntityType<V> targetEntity,
			BetterCollection<V> source) {
			super(field, valueType, targetEntity, source);
			if (!Comparable.class.isAssignableFrom(TypeTokens.getRawType(getType())))
				throw new IllegalStateException("SortedSet<" + getType() + "> not supported--" + getType() + " is not comparable");
			theSorting = (v1, v2) -> {
				return ((Comparable<V>) v1).compareTo(v2);
			};
		}

		@Override
		protected ObservableSortedSet<V> getValues() {
			return (ObservableSortedSet<V>) super.getValues();
		}

		@Override
		protected ObservableSortedSet<V> createValues(ObservableCollection<CollectionFieldElement<V>> elements) {
			return elements.flow().map(getType(), el -> el.value).distinctSorted(theSorting, false).collect();
		}

		@Override
		protected ElementId[] getAddLocation(V value, ElementId after, ElementId before, boolean first) {
			CollectionElement<V> el = getValues().search(getValues().searchFor(value, 0), SortedSearchFilter.Greater);
			if (el != null && theSorting.compare(el.get(), value) == 0)
				return null;
			if (after != null) {
				if (el != null && after.compareTo(el.getElementId()) >= 0)
					throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT_POSITION);
			}
			if (before != null) {
				if (el == null || before.compareTo(el.getElementId()) < 0)
					throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT_POSITION);
			}
			return new ElementId[] { el == null ? null : el.getElementId() };
		}

		@Override
		public MutableCollectionElement<V> mutableElement(ElementId id) {
			CollectionFieldElement<V> element = getElements().getElement(id).get();
			return new DistinctSortedMutableElement(id, element);
		}

		// The DistinctMutableElement class doesn't provide us anything we can't do ourselves more performantly
		protected class DistinctSortedMutableElement extends MutableElement {
			protected DistinctSortedMutableElement(ElementId id, CollectionFieldElement<V> element) {
				super(id, element);
			}

			@Override
			public String isAcceptable(V value) {
				String msg = super.isAcceptable(value);
				if (msg != null)
					return msg;
				int comp = theSorting.compare(get(), value);
				if (comp == 0)
					return null;
				CollectionElement<V> adj = getValues().getAdjacentElement(getElementId(), comp > 0);
				if (adj != null) {
					int adjComp = theSorting.compare(value, adj.get());
					if (adjComp == 0)
						return StdMsg.ELEMENT_EXISTS;
					else if ((adjComp > 0) == (comp > 0))
						return StdMsg.ILLEGAL_ELEMENT_POSITION;
				}
				return null;
			}
		}

		@Override
		public Comparator<? super V> comparator() {
			return theSorting;
		}

		@Override
		public int indexFor(Comparable<? super V> search) {
			return getValues().indexFor(search);
		}

		@Override
		public CollectionElement<V> search(Comparable<? super V> search, SortedSearchFilter filter) {
			return getValues().search(search, filter);
		}
	}

	class EntitySetImpl<V> implements ObservableValueSet<V> {
		private final EntityListField<V> theValues;
		private final ObservableEntityType<V> theEntityType;

		EntitySetImpl(ObservableEntityFieldType<E, ? extends ObservableValueSet<V>> field, ObservableEntityType<V> targetEntity,
			BetterCollection<V> source) {
			theValues = new EntityListField<>(field, //
				(TypeToken<V>) field.getValueType().resolveType(ObservableValueSet.class.getTypeParameters()[0]), //
				targetEntity, source);
			theEntityType = targetEntity;
		}

		@Override
		public ConfiguredValueType<V> getType() {
			return theEntityType;
		}

		@Override
		public ObservableCollection<? extends V> getValues() {
			return theValues;
		}

		@Override
		public <E2 extends V> ConfigurableValueCreator<V, E2> create(TypeToken<E2> subType) {
			ObservableEntityType<E2> entityType;
			if (subType == null || Objects.equals(theEntityType.getType(), subType))
				entityType = (ObservableEntityType<E2>) theEntityType;
			else {
				entityType = theEntityType.getEntitySet().getEntityType(TypeTokens.getRawType(subType));
				if (entityType == null)
					throw new IllegalArgumentException("Unrecognized sub-type " + subType + " of " + theEntityType);
				else if (!theEntityType.isAssignableFrom(entityType))
					throw new IllegalArgumentException(subType + " is not a sub-type of " + theEntityType);
			}
			ConfigurableCreator<E2, E2> typeCreator = entityType.create();
			return new ConfigurableValueCreator<V, E2>() {
				private ElementId theAfter;
				private ElementId theBefore;
				private boolean isTowardBeginning = true;

				@Override
				public ConfiguredValueType<E2> getType() {
					return entityType;
				}

				@Override
				public String canCreate() {
					return typeCreator.canCreate();
				}

				@Override
				public CollectionElement<V> create(Consumer<? super E2> preAddAction) throws ValueOperationException {
					E2 value = typeCreator.create(true, null, null).getOrFail();
					if (preAddAction != null)
						preAddAction.accept(value);
					return theValues.addElement(value, theAfter, theBefore, isTowardBeginning);
				}

				@Override
				public ObservableCreationResult<E2> createAsync(Consumer<? super E2> preAddAction) {
					ObservableCreationResult<E2> result = typeCreator.createAsync(null);
					result.whenDone(true, r -> {
						if (preAddAction != null)
							preAddAction.accept(r.getResult());
						theValues.addElement(r.getResult(), theAfter, theBefore, isTowardBeginning);
					});
					return result;
				}

				@Override
				public Set<Integer> getRequiredFields() {
					return typeCreator.getRequiredFields();
				}

				@Override
				public ConfigurableValueCreator<V, E2> after(ElementId after) {
					theAfter = after;
					return this;
				}

				@Override
				public ConfigurableValueCreator<V, E2> before(ElementId before) {
					theBefore = before;
					return this;
				}

				@Override
				public ConfigurableValueCreator<V, E2> towardBeginning(boolean towardBeginning) {
					isTowardBeginning = towardBeginning;
					return this;
				}

				@Override
				public String isEnabled(ConfiguredValueField<? super E2, ?> field) {
					return typeCreator.isEnabled(field);
				}

				@Override
				public <F> String isAcceptable(ConfiguredValueField<? super E2, F> field, F value) {
					return typeCreator.isAcceptable(field, value);
				}

				@Override
				public <F> ConfigurableValueCreator<V, E2> with(ConfiguredValueField<E2, F> field, F value)
					throws IllegalArgumentException {
					typeCreator.with(field, value);
					return this;
				}
			};
		}
	}
}
