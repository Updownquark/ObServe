package org.observe.entity.impl;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.Observer;
import org.observe.SettableValue;
import org.observe.Subscription;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.ObservableCollection;
import org.observe.config.ObservableValueSet;
import org.observe.entity.EntityIdentity;
import org.observe.entity.EntityModificationResult;
import org.observe.entity.EntityOperationException;
import org.observe.entity.EntityUpdate;
import org.observe.entity.ObservableEntity;
import org.observe.entity.ObservableEntityField;
import org.observe.entity.ObservableEntityFieldEvent;
import org.observe.entity.ObservableEntityFieldType;
import org.observe.entity.ObservableEntityProvider.CollectionOperationType;
import org.observe.util.EntityReflector;
import org.observe.util.EntityReflector.FieldChange;
import org.observe.util.ObservableCollectionWrapper;
import org.observe.util.TypeTokens;
import org.qommons.ArrayUtils;
import org.qommons.Causable;
import org.qommons.Identifiable;
import org.qommons.QommonsUtils;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterSortedMap;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.CollectionLockingStrategy;
import org.qommons.collect.ElementId;
import org.qommons.collect.ListenerList;
import org.qommons.collect.MapEntryHandle;
import org.qommons.collect.MultiMap;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.collect.QuickSet.QuickMap;
import org.qommons.collect.RRWLockingStrategy;
import org.qommons.tree.BetterTreeMap;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

class ObservableEntityImpl<E> implements ObservableEntity<E> {
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
		theBackupFields = theFields.keySet().createMap().fill(EntityUpdate.NOT_SET);
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
				public Subscription addListener(E entity, int fieldIndex, Consumer<FieldChange<?>> listener) {
					// TODO Auto-generated method stub
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
					// TODO Check constraints
					return null;
				}

				@Override
				public ObservableValue<String> isEnabled(int fieldIndex) {
					// TODO Check constraints
					return SettableValue.ALWAYS_ENABLED;
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
			return theFields.get(fieldIndex);
		} else
			return value;
	}

	@Override
	public String isAcceptable(int fieldIndex, Object value) {
		if (!isPresent)
			return ENTITY_REMOVED;
		return theType.getEntitySet().isAcceptable(this, fieldIndex, value);
	}

	@Override
	public <F> F set(int fieldIndex, F value, Object cause) {
		if (!isPresent)
			throw new UnsupportedOperationException(ENTITY_REMOVED);
		String msg = isAcceptable(fieldIndex, value);
		if (msg == StdMsg.UNSUPPORTED_OPERATION || msg == ObservableEntityField.ID_FIELD_UNSETTABLE || msg == ENTITY_REMOVED)
			throw new UnsupportedOperationException(msg);
		else if (msg != null)
			throw new IllegalArgumentException(msg);
		ObservableEntityFieldType<E, F> field = (ObservableEntityFieldType<E, F>) theType.getFields().get(fieldIndex);
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
			 */
			try {
				if (theType.getEntitySet().queueAction(sync -> {
					if (sync)
						return theType.select().entity(theId).update().with(field, value).execute(true, cause);
					else {
						EntityModificationResult<E> result = theType.select().entity(theId).update().with(field, value).execute(false,
							cause);
						result.onStatusChange(__ -> {
							// If the operation failed and the field
							if (result.getFailure() != null && theFields.get(fieldIndex) == value)
								_set(field, value, oldValue);
						});
						return result;
					}
				})) {
					_set(field, oldValue, value);
				}
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
			return theType.select().entity(theId).update().with(field, value).execute(true, cause);
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

	<F> void _set(ObservableEntityFieldType<? super E, F> field, F oldValue, F newValue) {
		if (field.getOwnerType() != theType)
			field = (ObservableEntityFieldType<E, F>) theType.getFields().get(field.getName());
		Object myOldValue = theFields.get(field.getIndex());
		theFields.put(field.getIndex(), resolve(field.getFieldType(), myOldValue, newValue));
		if (myOldValue != EntityUpdate.NOT_SET) {
			theStamp++;
			if (oldValue == EntityUpdate.NOT_SET)
				oldValue = (F) myOldValue;
			ObservableEntityFieldEvent<E, F> event = new ObservableEntityFieldEvent<>(this, (ObservableEntityFieldType<E, F>) field,
				oldValue, newValue, null);
			try (Transaction evtT = Causable.use(event)) {
				theFieldObservers.forEach(//
					listener -> listener.onNext(event));
			}
		}
	}

	private <F> F resolve(TypeToken<F> type, Object oldValue, Object newValue) {
		Class<F> raw = TypeTokens.getRawType(type);
		if (Collection.class.isAssignableFrom(raw)) {
			newValue = (F) setCollection((TypeToken<Collection<Object>>) type, oldValue, (BetterCollection<?>) newValue);
		} else if (ObservableValueSet.class.isAssignableFrom(raw))
			newValue = (F) setValueSet((TypeToken<ObservableValueSet<Object>>) type, oldValue, (BetterCollection<?>) newValue);
		else if (Map.class.isAssignableFrom(raw))
			newValue = (F) setMap((TypeToken<Map<Object, Object>>) type, oldValue, (Map<?, ?>) newValue);
		// TODO multi maps
		// TODO entities
	}

	private <V, C extends Collection<V>> C setCollection(TypeToken<C> collectionType, Object oldValue, Collection<?> newValue) {
		C collection;
		if (oldValue == EntityUpdate.NOT_SET)
			collection = initCollection(collectionType);
		else
			collection = (C) oldValue;
		if (collection instanceof List) {
			List<Object> newList;
			if (newValue instanceof List)
				newList = (List<Object>) newValue;
			else
				newList = QommonsUtils.unmodifiableCopy(newValue);
			ArrayUtils.adjust((List<V>) collection, newList, new ArrayUtils.DifferenceListener<V, Object>() {
				@Override
				public boolean identity(V o1, Object o2) {
					return Objects.equals(o1, o2);
				}

				@Override
				public V added(Object o, int mIdx, int retIdx) {
					// TODO Auto-generated method stub
					return null;
				}

				@Override
				public V removed(V o, int oIdx, int incMod, int retIdx) {
					// TODO Auto-generated method stub
					return null;
				}

				@Override
				public V set(V o1, int idx1, int incMod, Object o2, int idx2, int retIdx) {
					// TODO Auto-generated method stub
					return null;
				}
			});
		} else {

		}
	}

	private <K, V, M extends Map<K, V>> M setMap(TypeToken<M> mapType, Object oldValue, Map<?, ?> newValue) {
		M map;
		if (oldValue == EntityUpdate.NOT_SET)
			map = initMap(mapType);
		else
			map = (M) oldValue;
		// if (map instanceof List) {
		// List<V> newList;

		// } else {}
	}

	private <V, C extends ObservableValueSet<V>> C setValueSet(TypeToken<C> collectionType, Object oldValue,
		ObservableValueSet<?> newValue) {
		C collection;
		if (oldValue == EntityUpdate.NOT_SET)
			collection = initValueSet(collectionType);
		else
			collection = (C) oldValue;
		// if (collection instanceof List) {
		// List<V> newList;
		//
		// } else {}
	}

	void removed(Object cause) {
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
		final ElementId sourceId;
		V value;

		CollectionFieldElement(ElementId sourceId, V value) {
			this.sourceId = sourceId;
			this.value = value;
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

	abstract class EntityCollectionField<V> extends ObservableCollectionWrapper<V> {
		private final BetterCollection<V> theSource;
		private final TypeToken<V> theValueType;
		private final BetterSortedMap<ElementId, ElementId> theElementMapping;
		private final ObservableCollection<CollectionFieldElement<V>> theElements;
		private final ObservableCollection<V> theValues;
		private final Runnable theUpdate;

		EntityCollectionField(TypeToken<V> type, BetterCollection<V> source, Runnable update) {
			theSource = source;
			theValueType = type;
			theUpdate = update;
			theElementMapping = BetterTreeMap.build(ElementId::compareTo).safe(false).buildMap();
			theElements = createCollection((TypeToken<CollectionFieldElement<V>>) (TypeToken<?>) ELEMENT_TYPE.getCompoundType(type),
				new RRWLockingStrategy(theType.getEntitySet()));
			theValues = createValues(theElements);
			for (CollectionElement<V> element : source.elements())
				theElementMapping.put(element.getElementId(), //
					theValues.addElement(element.get(), false).getElementId());
			init(theValues);
		}

		protected abstract ObservableCollection<CollectionFieldElement<V>> createCollection(TypeToken<CollectionFieldElement<V>> type,
			CollectionLockingStrategy locking);

		protected abstract ObservableCollection<V> createValues(ObservableCollection<CollectionFieldElement<V>> elements);

		void applyChange(CollectionChangeType type, ElementId element, V value, Object cause) {
			switch (type) {
			case add:
				MapEntryHandle<ElementId, ElementId> mapping = theElementMapping.putEntry(element, null, false);
				if (mapping == null) {
					System.err.println("Received new entry event for pre-existing element: " + element + "=" + value);
					return;
				}
				theElementMapping.mutableEntry(mapping.getElementId()).setValue(addEntry(mapping, resolve(theValueType, null, value)));
				break;
			case remove:
				mapping = theElementMapping.getEntry(element);
				if (mapping == null) {
					System.err.println("Received remove event for non-existent element: " + element);
					return;
				}
				ElementId valueId = mapping.getValue();
				theElementMapping.mutableEntry(mapping.getElementId()).remove();
				theValues.mutableElement(valueId).remove();
				break;
			case set:
				mapping = theElementMapping.getEntry(element);
				if (mapping == null) {
					System.err.println("Received update event for non-existent element: " + element);
					return;
				}
				theValues.mutableElement(mapping.getValue()).set(value);
				break;
			}
			theUpdate.run();
		}

		protected abstract ElementId addEntry(MapEntryHandle<ElementId, ElementId> mapping, V value);

		@Override
		public abstract boolean isContentControlled();

		@Override
		public MutableCollectionElement<V> mutableElement(ElementId id) {
			CollectionFieldElement<V> element = theElements.getElement(id).get();
			return new MutableCollectionElement<V>() {
				@Override
				public ElementId getElementId() {
					return id;
				}

				@Override
				public V get() {
					return element.value;
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
					return null;
				}

				@Override
				public void set(V value) throws UnsupportedOperationException, IllegalArgumentException {
					theType.getEntitySet().getImplementation().updateCollection(theSource, CollectionOperationType.update, element.sourceId,
						value, null);
				}

				@Override
				public String canRemove() {
					return null;
				}

				@Override
				public void remove() throws UnsupportedOperationException {
					theType.getEntitySet().getImplementation().updateCollection(theSource, CollectionOperationType.remove, element.sourceId,
						null, null);
				}
			};
		}

		@Override
		public CollectionElement<V> addElement(V value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			// TODO Auto-generated method stub
		}

		@Override
		public long getStamp() {
			return super.getStamp(); // TODO Should this map the field's stamp? Or should the field's stamp match this?
		}

		@Override
		public Object getIdentity() {
			return super.getIdentity(); // TODO Should this map the field's identity or be related to it?
		}

		@Override
		public void clear() {
			theType.getEntitySet().getImplementation().updateCollection(theSource, CollectionOperationType.clear, null, null, null);
		}

		@Override
		public void setValue(Collection<ElementId> elements, V value) {
			boolean[] complete = new boolean[elements.size()];
			int i = 0;
			for (ElementId element : elements) {
				int fi = i;
				theType.getEntitySet().getImplementation().updateCollection(theSource, CollectionOperationType.update,
					theElements.getElement(element).get().sourceId, value, __ -> complete[fi] = true);
				i++;
			}
			while (true) {
				boolean allComplete = true;
				for (boolean c : complete) {
					if (!c) {
						allComplete = false;
						break;
					}
				}
				if (allComplete)
					break;
				else {
					try {
						Thread.sleep(10);
					} catch (InterruptedException e) {}
				}
			}
		}
	}
}
