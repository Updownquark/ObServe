package org.observe.assoc;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.CausableChanging;
import org.observe.Equivalence;
import org.observe.Eventable;
import org.observe.Observable;
import org.observe.Observable.CoreChangeSources;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.CollectionSubscription;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollectionBuilder;
import org.observe.collect.ObservableCollectionEvent;
import org.observe.collect.ObservableSet;
import org.observe.collect.SettableElement;
import org.observe.util.ObservableUtils.SubscriptionCause;
import org.qommons.Causable;
import org.qommons.Identifiable;
import org.qommons.Lockable.CoreId;
import org.qommons.Subscription;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterCollection.EmptyCollection;
import org.qommons.collect.BetterHashMap;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterMap;
import org.qommons.collect.BetterSet;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.ListElement;
import org.qommons.collect.MapEntryHandle;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.collect.MutableListElement;
import org.qommons.collect.MutableMapEntryHandle;
import org.qommons.collect.MutableOrderedMapEntry;
import org.qommons.collect.OrderedMapEntry;
import org.qommons.collect.SimpleMapEntry;

/**
 * A map with observable capabilities
 *
 * @param <K> The type of keys this map uses
 * @param <V> The type of values this map stores
 */
public interface ObservableMap<K, V> extends BetterMap<K, V>, Eventable, CausableChanging {
	@Override
	abstract boolean isLockSupported();

	/** @return The {@link Equivalence} that is used by this map's values (for {@link #containsValue(Object)}) */
	Equivalence<? super V> equivalence();

	@Override
	ObservableSet<K> keySet();

	/**
	 * @param action The action to perform whenever this map changes
	 * @return The subscription to cease listening
	 */
	Subscription onChange(Consumer<? super ObservableMapEvent<? extends K, ? extends V>> action);

	@Override
	ObservableMap<K, V> alias(String alias);

	@Override
	default OrderedMapEntry<K, V> putEntry(K key, V value, boolean first) {
		return (OrderedMapEntry<K, V>) BetterMap.super.putEntry(key, value, first);
	}

	@Override
	default OrderedMapEntry<K, V> putEntry(K key, V value, ElementId after, ElementId before, boolean first) {
		return (OrderedMapEntry<K, V>) BetterMap.super.putEntry(key, value, after, before, first);
	}

	@Override
	OrderedMapEntry<K, V> getEntry(K key);

	@Override
	OrderedMapEntry<K, V> getOrPutEntry(K key, Function<? super K, ? extends V> value, ElementId after, ElementId before, boolean first,
		Runnable preAdd, Runnable postAdd);

	@Override
	OrderedMapEntry<K, V> getEntryById(ElementId entryId);

	@Override
	default OrderedMapEntry<K, V> getTerminalEntry(boolean first) {
		return (OrderedMapEntry<K, V>) BetterMap.super.getTerminalEntry(first);
	}

	@Override
	MutableOrderedMapEntry<K, V> mutableEntry(ElementId entryId);

	/** @return An observable collection of all the values stored in this map */
	@Override
	default ObservableCollection<V> values() {
		return new ObservableMapValueCollection<>(this);
	}

	@Override
	default ObservableSet<Map.Entry<K, V>> entrySet() {
		return new ObservableEntrySet<>(this);
	}

	/**
	 * @param action The action to perform on map events
	 * @param forward Whether to subscribe to the map forward or reverse)
	 * @return The collection subscription to use to terminate listening
	 */
	default CollectionSubscription subscribe(Consumer<? super ObservableMapEvent<? extends K, ? extends V>> action, boolean forward) {
		try (Transaction t = lock(false, null)) {
			Subscription sub = onChange(action);
			SubscriptionCause subCause = new SubscriptionCause(null);
			try (Transaction ct = subCause.use()) {
				int index = forward ? 0 : size() - 1;
				for (CollectionElement<Map.Entry<K, V>> entryEl : entrySet().elements()) {
					ObservableMultiMapEvent<K, V> mapEvent = new ObservableMultiMapEvent.Default<>(entryEl.getElementId(),
						entryEl.getElementId(), index, index, CollectionChangeType.add, entryEl.get().getKey(), entryEl.get().getKey(),
						null, entryEl.get().getValue(), subCause);
					try (Transaction mt = mapEvent.use()) {
						action.accept(mapEvent);
					}
					if (forward)
						index++;
					else
						index--;
				}
			}
			return removeAll -> {
				if (!removeAll) {
					sub.unsubscribe();
					return;
				}
				try (Transaction unsubT = lock(false, null)) {
					sub.unsubscribe();
					SubscriptionCause unsubCause = new SubscriptionCause(null);
					try (Transaction ct = unsubCause.use()) {
						int index = !forward ? 0 : size() - 1;
						for (CollectionElement<Map.Entry<K, V>> entryEl : entrySet().elements()) {
							ObservableMultiMapEvent<K, V> mapEvent = new ObservableMultiMapEvent.Default<>(entryEl.getElementId(),
								entryEl.getElementId(), index, index, CollectionChangeType.remove, //
								entryEl.get().getKey(), entryEl.get().getKey(), entryEl.get().getValue(), entryEl.get().getValue(),
								subCause);
							try (Transaction mt = mapEvent.use()) {
								action.accept(mapEvent);
							}
							if (forward)
								index--;
						}
					}
				}
			};
		}
	}

	/**
	 * @param key The key to get the value for
	 * @return An observable value that changes whenever the value for the given key changes in this map
	 */
	default SettableElement<V> observe(K key) {
		return observe(key, null);
	}

	/**
	 * @param key The key to get the value for
	 * @param defaultValue The value for the result to have when no entry for the given key is present in the map
	 * @return An observable value that changes whenever the value for the given key changes in this map
	 */
	default SettableElement<V> observe(K key, V defaultValue) {
		class MapValueObservable extends AbstractIdentifiable implements SettableElement<V> {
			private ElementId thePreviousElement;

			@Override
			public ElementId getElementId() {
				if (thePreviousElement == null || !thePreviousElement.isPresent()) {
					MapEntryHandle<K, V> entry = getEntry(key);
					thePreviousElement = entry == null ? null : entry.getElementId();
				}
				return thePreviousElement;
			}

			@Override
			public boolean isLockSupported() {
				return ObservableMap.this.isLockSupported();
			}

			@Override
			public Transaction lock(boolean write, Object cause) {
				return ObservableMap.this.lock(write, cause);
			}

			@Override
			public Transaction tryLock(boolean write, Object cause) {
				return ObservableMap.this.tryLock(write, cause);
			}

			@Override
			public Collection<Cause> getCurrentCauses() {
				return ObservableMap.this.getCurrentCauses();
			}

			@Override
			public V get() {
				try (Transaction t = ObservableMap.this.lock(false, null)) {
					MapEntryHandle<K, V> entry;
					if (thePreviousElement != null && thePreviousElement.isPresent())
						entry = getEntryById(thePreviousElement);
					else {
						entry = getEntry(key);
						thePreviousElement = entry == null ? null : entry.getElementId();
					}
					return entry == null ? defaultValue : entry.getValue();
				}
			}

			@Override
			public Observable<ObservableElementEvent<V>> elementChangesNoInit() {
				class MapValueChanges extends AbstractIdentifiable implements Observable<ObservableElementEvent<V>> {
					@Override
					public Subscription subscribe(Observer<? super ObservableElementEvent<V>> observer) {
						try (Transaction t = lock()) {
							boolean[] exists = new boolean[1];
							Subscription sub = onChange(evt -> {
								if (!keySet().equivalence().elementEquals(evt.getKey(), key))
									return;
								boolean newExists;
								if (evt.getNewValue() != null)
									newExists = true;
								else
									newExists = keySet().contains(key);
								V oldValue = exists[0] ? evt.getOldValue() : defaultValue;
								V newValue = newExists ? evt.getNewValue() : defaultValue;
								exists[0] = newExists;
								ObservableElementEvent<V> evt2 = new ObservableElementEvent<>(false, //
									evt.getType() == CollectionChangeType.add ? null : evt.getElementId(), evt.getElementId(), //
										oldValue, newValue, evt);
								if (evt.getType() == CollectionChangeType.remove)
									thePreviousElement = null;
								else
									thePreviousElement = evt.getElementId();
								try (Transaction evtT = evt2.use()) {
									observer.onNext(evt2);
								}
							});
							CollectionElement<Map.Entry<K, V>> entryEl = entrySet().getElement(new SimpleMapEntry<>(key, null), true);
							exists[0] = entryEl != null;
							return sub;
						}
					}

					@Override
					public ThreadConstraint getThreadConstraint() {
						return ObservableMap.this.getThreadConstraint();
					}

					@Override
					public boolean isEventing() {
						return ObservableMap.this.isEventing();
					}

					@Override
					public boolean isSafe() {
						return ObservableMap.this.isLockSupported();
					}

					@Override
					public Transaction lock() {
						return ObservableMap.this.lock(false, null);
					}

					@Override
					public Transaction tryLock() {
						return ObservableMap.this.tryLock(false, null);
					}

					@Override
					public CoreId getCoreId() {
						return ObservableMap.this.getCoreId();
					}

					@Override
					protected Object createIdentity() {
						return Identifiable.wrap(MapValueObservable.this.getIdentity(), "noInitChanges");
					}

					@Override
					public long getStamp() {
						return MapValueObservable.this.getStamp();
					}

					@Override
					public CoreChangeSources getChangeSources() {
						return changes().getChangeSources();
					}
				}
				return new MapValueChanges();
			}

			@Override
			public long getStamp() {
				return ObservableMap.this.getStamp();
			}

			@Override
			protected Object createIdentity() {
				return Identifiable.wrap(ObservableMap.this.getIdentity(), "observeValue", key);
			}

			@Override
			public MapValueObservable alias(String alias) {
				super.alias(alias);
				return this;
			}

			@Override
			public ObservableValue<String> isEnabled() {
				class Enabled extends AbstractIdentifiable implements ObservableValue<String> {
					@Override
					public long getStamp() {
						return ObservableMap.this.getStamp();
					}

					@Override
					public Object createIdentity() {
						return Identifiable.wrap(MapValueObservable.this.getIdentity(), "enabled");
					}

					@Override
					public Enabled alias(String alias) {
						super.alias(alias);
						return this;
					}

					@Override
					public String get() {
						try (Transaction t = ObservableMap.this.lock(false, null)) {
							MapEntryHandle<K, V> entry;
							if (thePreviousElement != null && thePreviousElement.isPresent())
								entry = getEntryById(thePreviousElement);
							else {
								entry = getEntry(key);
								thePreviousElement = CollectionElement.getElementId(entry);
							}
							if (entry != null)
								return mutableEntry(entry.getElementId()).isEnabled();
							else
								return null; // Without an actual value to test, we can't be sure whether ANY value could be inserted
						}
					}

					@Override
					public Observable<ObservableValueEvent<String>> noInitChanges() {
						class NoInitChanges extends AbstractIdentifiable implements Observable<ObservableValueEvent<String>> {
							@Override
							public Subscription subscribe(Observer<? super ObservableValueEvent<String>> observer) {
								String[] oldValue = new String[] { get() };
								return ObservableMap.this.onChange(mapEvt -> {
									if (keySet().equivalence().elementEquals(mapEvt.getKey(), key)) {
										String newValue = get();
										if (!Objects.equals(newValue, oldValue[0])) {
											ObservableValueEvent<String> enabledEvt = createChangeEvent(oldValue[0], newValue, mapEvt);
											try (Transaction evtT = enabledEvt.use()) {
												observer.onNext(enabledEvt);
											}
										}
									}
								});
							}

							@Override
							public ThreadConstraint getThreadConstraint() {
								return ObservableMap.this.getThreadConstraint();
							}

							@Override
							public boolean isEventing() {
								return ObservableMap.this.isEventing();
							}

							@Override
							public boolean isSafe() {
								return Enabled.this.isLockSupported();
							}

							@Override
							public Transaction lock() {
								return ObservableMap.this.lock(false, null);
							}

							@Override
							public Transaction tryLock() {
								return ObservableMap.this.tryLock(false, null);
							}

							@Override
							public CoreId getCoreId() {
								return ObservableMap.this.getCoreId();
							}

							@Override
							protected Object createIdentity() {
								return Identifiable.wrap(Enabled.this.getIdentity(), "noInitChanges");
							}

							@Override
							public long getStamp() {
								return Enabled.this.getStamp();
							}

							@Override
							public CoreChangeSources getChangeSources() {
								return ObservableMap.this.changes().getChangeSources();
							}
						}
						return new NoInitChanges();
					}

					@Override
					public boolean isEventing() {
						return ObservableMap.this.isEventing();
					}
				}
				return new Enabled();
			}

			@Override
			public String isAcceptable(V value) {
				try (Transaction t = ObservableMap.this.lock(false, null)) {
					MapEntryHandle<K, V> entry;
					if (thePreviousElement != null && thePreviousElement.isPresent())
						entry = getEntryById(thePreviousElement);
					else {
						entry = getEntry(key);
						thePreviousElement = CollectionElement.getElementId(entry);
					}
					if (entry != null) {
						MutableMapEntryHandle<K, V> mutableEntry = mutableEntry(entry.getElementId());
						String msg = mutableEntry.isAcceptable(value);
						if (msg != null && equivalence().elementEquals(defaultValue, value)) {
							String msg2 = mutableEntry.canRemove();
							if (msg2 == null)
								return null;
						}
						return msg;
					} else
						return canPut(key, value);
				}
			}

			@Override
			public V set(V value) throws IllegalArgumentException, UnsupportedOperationException {
				try (Transaction t = ObservableMap.this.lock(false, null)) {
					MapEntryHandle<K, V> entry;
					if (thePreviousElement != null && thePreviousElement.isPresent())
						entry = getEntryById(thePreviousElement);
					else {
						entry = getEntry(key);
						thePreviousElement = CollectionElement.getElementId(entry);
					}
					if (entry != null) {
						V oldValue = entry.getValue();
						MutableMapEntryHandle<K, V> mutableEntry = mutableEntry(entry.getElementId());
						if (mutableEntry.isAcceptable(value) == null) {
							mutableEntry.set(value);
						} else if (equivalence().elementEquals(defaultValue, value) && mutableEntry.canRemove() == null)
							mutableEntry.remove();
						else // Let the element throw the exception
							mutableEntry.set(value);
						return oldValue;
					} else {
						putEntry(key, value, false);
						return null;
					}
				}
			}

			@Override
			public Observable<ObservableElementEvent<V>> elementChanges() {
				class MapObservableWithInit extends AbstractIdentifiable implements Observable<ObservableElementEvent<V>> {
					private final Observable<ObservableElementEvent<V>> changes = elementChangesNoInit();

					@Override
					public Object createIdentity() {
						return Identifiable.wrap(MapValueObservable.this.getIdentity(), "changes");
					}

					@Override
					public Subscription subscribe(Observer<? super ObservableElementEvent<V>> observer) {
						try (Transaction t = ObservableMap.this.lock(false, null)) {
							MapEntryHandle<K, V> entry;
							if (thePreviousElement != null && thePreviousElement.isPresent())
								entry = getEntryById(thePreviousElement);
							else {
								entry = getEntry(key);
								thePreviousElement = CollectionElement.getElementId(entry);
							}
							ObservableElementEvent<V> initEvt = new ObservableElementEvent<>(true, null,
								entry == null ? null : entry.getElementId(), //
									null, entry == null ? defaultValue : entry.getValue(), null);
							try (Transaction evtT = initEvt.use()) {
								observer.onNext(initEvt);
							}
							return changes.subscribe(observer);
						}
					}

					@Override
					public ThreadConstraint getThreadConstraint() {
						return changes.getThreadConstraint();
					}

					@Override
					public boolean isEventing() {
						return changes.isEventing();
					}

					@Override
					public boolean isSafe() {
						return changes.isSafe();
					}

					@Override
					public Transaction lock() {
						return changes.lock();
					}

					@Override
					public Transaction tryLock() {
						return changes.tryLock();
					}

					@Override
					public CoreId getCoreId() {
						return changes.getCoreId();
					}

					@Override
					public long getStamp() {
						return changes.getStamp();
					}

					@Override
					public CoreChangeSources getChangeSources() {
						return changes.getChangeSources();
					}
				}
				return new MapObservableWithInit();
			}
		}
		return new MapValueObservable();
	}

	/**
	 * @return An observable that fires a value whenever anything in this structure changes. This observable will only fire 1 event per
	 *         transaction.
	 */
	default Observable<Causable> changes() {
		return values().simpleChanges();
	}

	@Override
	default Observable<? extends Causable> simpleChanges() {
		return changes();
	}

	/**
	 * Creates a builder to build an unconstrained {@link ObservableMap}
	 *
	 * @param <K> The key type for the map
	 * @param <V> The value type for the map
	 * @return The builder to build the map
	 */
	static <K, V> Builder<K, V, ?> build() {
		return new Builder<>("ObservableMap");
	}

	/**
	 * @param <K> The key type for the map
	 * @param <V> The value type for the map
	 * @param map The data for the map
	 * @return An immutable {@link ObservableMap} containing the given data
	 */
	static <K, V> ObservableMap<K, V> of(Map<K, V> map) {
		if (map.isEmpty())
			return empty();
		else if (map instanceof BetterMap)
			return new ConstantObservableMap<>((BetterMap<K, V>) map);
		else
			return new ConstantObservableMap<>(BetterHashMap.build().build(map));
	}

	/**
	 * @param <K> The key type for the map
	 * @param <V> The value type for the map
	 * @param key The key for the map
	 * @param value The value for the map
	 * @param map The data for the map
	 * @return An immutable {@link ObservableMap} containing a single entry with the given key/value pair
	 */
	static <K, V> ObservableMap<K, V> of(K key, V value) {
		return new ConstantObservableMap<>(BetterMap.of(key, value));
	}

	/**
	 * Builds an unconstrained {@link ObservableMap}
	 *
	 * @param <K> The key type for the map
	 * @param <V> The value type for the map
	 * @param <B> The sub-type of the builder
	 */
	class Builder<K, V, B extends Builder<K, V, ? extends B>> extends ObservableCollectionBuilder.CollectionBuilderImpl<K, B> {
		Builder(String initDescrip) {
			super(initDescrip);
		}

		public ObservableMap<K, V> buildMap() {
			Comparator<? super K> compare = getSorting();
			ObservableCollectionBuilder<Entry<K, V>, ?> entryBuilder = ObservableCollection.<Entry<K, V>> build()//
				.withBacking((BetterList<Map.Entry<K, V>>) (BetterList<?>) getBacking())//
				.withDescription(getDescription());
			entryBuilder.withElementsBySource(getElementsBySource()).withSourceElements(getSourceElements());
			entryBuilder.withCollectionLocking(getLocker());
			if (compare != null)
				entryBuilder = entryBuilder.sortBy((entry1, entry2) -> compare.compare(entry1.getKey(), entry2.getKey()));
			return new DefaultObservableMap<>(getEquivalence(), entryBuilder.build());
		}
	}

	/**
	 * @param <K> The key type for the map
	 * @param <V> The value type for the map
	 * @return An immutable, empty map with the given types
	 */
	static <K, V> ObservableMap<K, V> empty() {
		return new EmptyObservableMap<>();
	}

	/**
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 * @param map The map to wrap
	 * @return An {@link ObservableMap} that reflects the given map's contents but does not allow any modifications
	 */
	static <K, V> ObservableMap<K, V> unmodifiable(ObservableMap<K, V> map) {
		return new UnmodifiableObservableMap<>(map);
	}

	/**
	 * Implements {@link ObservableMap#values()}
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	class ObservableMapValueCollection<K, V> extends BetterMap.BetterMapValueCollection<K, V> implements ObservableCollection<V> {
		public ObservableMapValueCollection(ObservableMap<K, V> map) {
			super(map);
		}

		@Override
		protected ObservableMap<K, V> getMap() {
			return (ObservableMap<K, V>) super.getMap();
		}

		@Override
		public ObservableMapValueCollection<K, V> alias(String alias) {
			super.alias(alias);
			return this;
		}

		@Override
		public boolean isEventing() {
			return getMap().isEventing();
		}

		@Override
		public CoreChangeSources getChangeSources() {
			return getMap().getChangeSources();
		}

		@Override
		public boolean isContentControlled() {
			return true;
		}

		@Override
		public Equivalence<? super V> equivalence() {
			return Equivalence.DEFAULT;
		}

		@Override
		public ListElement<V> getTerminalElement(boolean first) {
			return (ListElement<V>) super.getTerminalElement(first);
		}

		@Override
		public ListElement<V> getElement(V value, boolean first) {
			return (ListElement<V>) super.getElement(value, first);
		}

		@Override
		public ListElement<V> getElement(ElementId id) {
			return (ListElement<V>) super.getElement(id);
		}

		@Override
		public ListElement<V> getElement(int index) throws IndexOutOfBoundsException {
			return getMap().getEntryById(getMap().keySet().getElement(index).getElementId());
		}

		@Override
		public ListElement<V> addElement(V value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			return (ListElement<V>) super.addElement(value, after, before, first);
		}

		@Override
		public ListElement<V> move(ElementId valueEl, ElementId after, ElementId before, boolean first, Runnable afterRemove)
			throws UnsupportedOperationException, IllegalArgumentException {
			return (ListElement<V>) super.move(valueEl, after, before, first, afterRemove);
		}

		@Override
		public MutableListElement<V> mutableElement(ElementId id) {
			return getMap().mutableEntry(id);
		}

		@Override
		public void setValue(Collection<ElementId> elements, V value) {
			try (Transaction t = lock(true, null)) {
				for (ElementId entryId : elements) {
					MutableMapEntryHandle<K, V> entry = getMap().mutableEntry(entryId);
					entry.set(value);
				}
			}
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends V>> observer) {
			return getMap().onChange(observer);
		}
	}

	/**
	 * Implements {@link ObservableMap#entrySet()}
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	class ObservableEntrySet<K, V> extends BetterMap.BetterEntrySet<K, V> implements ObservableSet<Map.Entry<K, V>> {
		private Equivalence<Map.Entry<K, V>> theEquivalence;

		public ObservableEntrySet(ObservableMap<K, V> map) {
			super(map);
		}

		@Override
		protected ObservableMap<K, V> getMap() {
			return (ObservableMap<K, V>) super.getMap();
		}

		@Override
		public ObservableEntrySet<K, V> alias(String alias) {
			super.alias(alias);
			return this;
		}

		@Override
		public boolean isEventing() {
			return getMap().isEventing();
		}

		@Override
		public CoreChangeSources getChangeSources() {
			return getMap().getChangeSources();
		}

		@Override
		public boolean isContentControlled() {
			return getMap().keySet().isContentControlled();
		}

		@Override
		public Equivalence<? super Map.Entry<K, V>> equivalence() {
			if (theEquivalence == null)
				theEquivalence = getMap().keySet().equivalence().map(null, //
					key -> new SimpleMapEntry<>(key, null, false), Map.Entry::getKey);
			return theEquivalence;
		}

		@Override
		public ListElement<Map.Entry<K, V>> getTerminalElement(boolean first) {
			return (ListElement<Map.Entry<K, V>>) super.getTerminalElement(first);
		}

		@Override
		public ListElement<Map.Entry<K, V>> getElement(Entry<K, V> value, boolean first) {
			return (ListElement<Map.Entry<K, V>>) super.getElement(value, first);
		}

		@Override
		public ListElement<Map.Entry<K, V>> getElement(ElementId id) {
			return (ListElement<Map.Entry<K, V>>) super.getElement(id);
		}

		@Override
		public ListElement<Map.Entry<K, V>> getElement(int index) throws IndexOutOfBoundsException {
			return getElement(getMap().keySet().getElement(index).getElementId());
		}

		@Override
		public ListElement<Entry<K, V>> getOrAdd(Entry<K, V> value, ElementId after, ElementId before, boolean first, Runnable preAdd,
			Runnable postAdd) {
			return (ListElement<Map.Entry<K, V>>) super.getOrAdd(value, after, before, first, preAdd, postAdd);
		}

		@Override
		public ListElement<Map.Entry<K, V>> addElement(Entry<K, V> value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			return (ListElement<Map.Entry<K, V>>) super.addElement(value, after, before, first);
		}

		@Override
		public ListElement<Map.Entry<K, V>> move(ElementId valueEl, ElementId after, ElementId before, boolean first, Runnable afterRemove)
			throws UnsupportedOperationException, IllegalArgumentException {
			return (ListElement<Map.Entry<K, V>>) super.move(valueEl, after, before, first, afterRemove);
		}

		@Override
		protected ListElement<Map.Entry<K, V>> entryFor(MapEntryHandle<K, V> entry) {
			return entry == null ? null : new ListEntry((OrderedMapEntry<K, V>) entry);
		}

		@Override
		public MutableListElement<Map.Entry<K, V>> mutableElement(ElementId id) {
			return new MutableListEntry(getMap().mutableEntry(id));
		}

		@Override
		public void setValue(Collection<ElementId> elements, Map.Entry<K, V> value) {
			try (Transaction t = lock(true, null)) {
				for (ElementId entryId : elements) {
					MutableMapEntryHandle<K, V> entry = getMap().mutableEntry(entryId);
					if (!getMap().keySet().equivalence().elementEquals(entry.getKey(), value.getKey()))
						throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
					entry.set(value.getValue());
				}
			}
		}

		@Override
		public Map.Entry<K, V>[] toArray() {
			try (Transaction t = lock(false, null)) {
				Map.Entry<K, V>[] array = new Map.Entry[size()];
				CollectionElement<K> keyElement = getMap().keySet().getTerminalElement(true);
				for (int i = 0; keyElement != null; i++, keyElement = keyElement.getAdjacent(true)) {
					array[i++] = getMap().getEntryById(keyElement.getElementId());
				}
				return array;
			}
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends Map.Entry<K, V>>> observer) {
			return getMap().onChange(mapEvt -> {
				MapEntryHandle<K, V> entry = getMap().getEntryById(mapEvt.getElementId());
				Map.Entry<K, V> oldEntry;
				if (mapEvt.getOldValue() == mapEvt.getNewValue())
					oldEntry = entry;
				else
					oldEntry = new SimpleMapEntry<>(mapEvt.getKey(), mapEvt.getOldValue(), false);
				ObservableCollectionEvent<Map.Entry<K, V>> entryEvt = ObservableCollectionEvent.createCollectionEvent(//
					mapEvt.getElementId(), mapEvt.getIndex(), mapEvt.getType(), oldEntry, entry, mapEvt, mapEvt.getMovement());
				try (Transaction evtT = entryEvt.use()) {
					observer.accept(entryEvt);
				}
			});
		}

		class ListEntry extends EntryElement implements ListElement<Map.Entry<K, V>> {
			ListEntry(OrderedMapEntry<K, V> entry) {
				super(entry);
			}

			@Override
			protected OrderedMapEntry<K, V> getEntry() {
				return (OrderedMapEntry<K, V>) super.getEntry();
			}

			@Override
			public int getElementsBefore() {
				return getEntry().getElementsBefore();
			}

			@Override
			public int getElementsAfter() {
				return getEntry().getElementsAfter();
			}

			@Override
			public ListElement<Map.Entry<K, V>> getAdjacent(boolean next) {
				OrderedMapEntry<K, V> adj = getEntry().getAdjacent(next);
				return entryFor(adj);
			}
		}

		class MutableListEntry extends MutableEntryElement implements MutableListElement<Map.Entry<K, V>> {
			MutableListEntry(MutableOrderedMapEntry<K, V> entry) {
				super(entry);
			}

			@Override
			protected MutableOrderedMapEntry<K, V> getEntry() {
				return (MutableOrderedMapEntry<K, V>) super.getEntry();
			}

			@Override
			public int getElementsBefore() {
				return getEntry().getElementsBefore();
			}

			@Override
			public int getElementsAfter() {
				return getEntry().getElementsAfter();
			}

			@Override
			public MutableListElement<Map.Entry<K, V>> getAdjacent(boolean next) {
				MutableOrderedMapEntry<K, V> adj = getEntry().getAdjacent(next);
				return adj == null ? null : new MutableListEntry(adj);
			}
		}
	}

	/**
	 * A simple, unconstrained {@link ObservableMap} implementation
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	class DefaultObservableMap<K, V> extends AbstractIdentifiable implements ObservableMap<K, V> {
		private final ObservableSet<Map.Entry<K, V>> theEntries;
		private final ObservableSet<K> theKeySet;
		private ObservableCollection<V> theValues;
		private ObservableSet<Map.Entry<K, V>> theExposedEntries;

		public DefaultObservableMap(Equivalence<? super K> keyEquivalence, ObservableCollection<Map.Entry<K, V>> entries) {
			if (keyEquivalence == null)
				throw new NullPointerException();
			if (keyEquivalence instanceof Equivalence.SortedEquivalence) {
				Comparator<? super K> compare = ((Equivalence.SortedEquivalence<? super K>) keyEquivalence).comparator();
				if (compare == null)
					throw new NullPointerException();
				theEntries = entries.flow().distinctSorted((entry1, entry2) -> {
					entry1.getKey();
					entry2.getKey();
					try {
						return compare.compare(entry1.getKey(), entry2.getKey());
					} catch (NullPointerException e) {
						// This catch is here to facilitate debugging at this site.
						// This is a pretty common error, especially when the comparator is a lambda
						// that assumes non-null values (e.g. Integer::compareTo).
						// In that case, the root of the stack will be the line above, not in the lambda,
						// making interpreting the stack difficult.
						throw e;
					}
				}, true).collect();
			} else
				theEntries = entries.flow().distinct().collect();
			theKeySet = theEntries.flow().<K> transformEquivalent(tx -> tx.cache(false).reEvalOnUpdate(false).fireIfUnchanged(false)//
				.map(Map.Entry::getKey).withEquivalence(keyEquivalence).withReverse(key -> new SimpleMapEntry<>(key, null, false)))
				.collectPassive();
		}

		protected ObservableSet<Map.Entry<K, V>> getEntries() {
			return theEntries;
		}

		@Override
		public boolean isEventing() {
			return theEntries.isEventing();
		}

		@Override
		public Object createIdentity() {
			return Identifiable.baseId("observable-map", this);
		}

		@Override
		public DefaultObservableMap<K, V> alias(String alias) {
			super.alias(alias);
			return this;
		}

		@Override
		public CoreChangeSources getChangeSources() {
			return theEntries.getChangeSources();
		}

		@Override
		public boolean isLockSupported() {
			return theEntries.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theEntries.lock(write, cause);
		}

		@Override
		public Equivalence<? super V> equivalence() {
			return Equivalence.DEFAULT;
		}

		@Override
		public ObservableSet<K> keySet() {
			return theKeySet;
		}

		@Override
		public ObservableCollection<V> values() {
			if (theValues == null)
				theValues = ObservableMap.super.values();
			return theValues;
		}

		@Override
		public ObservableSet<Map.Entry<K, V>> entrySet() {
			if (theExposedEntries == null)
				theExposedEntries = createEntrySet();
			return theExposedEntries;
		}

		protected ObservableSet<Map.Entry<K, V>> createEntrySet() {
			return ObservableMap.super.entrySet();
		}

		@Override
		public OrderedMapEntry<K, V> getEntry(K key) {
			ListElement<Map.Entry<K, V>> entryEl = theEntries.getElement(new SimpleMapEntry<>(key, null), true);
			return entryEl == null ? null : handleFor(entryEl);
		}

		@Override
		public OrderedMapEntry<K, V> getEntryById(ElementId entryId) {
			ListElement<Map.Entry<K, V>> entryEl = theEntries.getElement(entryId);
			return entryEl == null ? null : handleFor(entryEl);
		}

		@Override
		public OrderedMapEntry<K, V> putEntry(K key, V value, ElementId after, ElementId before, boolean first) {
			try (Transaction t = lock(true, null)) {
				ListElement<Map.Entry<K, V>> entryEl = theEntries.getElement(new SimpleMapEntry<>(key, null), true);
				if (entryEl != null) {
					entryEl.get().setValue(value);
					return handleFor(entryEl);
				}
				MapEntry newEntry = new MapEntry(key, value);
				entryEl = theEntries.addElement(newEntry, after, before, first);
				newEntry.theElementId = entryEl.getElementId();
				return handleFor(entryEl);
			}
		}

		@Override
		public OrderedMapEntry<K, V> getOrPutEntry(K key, Function<? super K, ? extends V> value, ElementId afterKey, ElementId beforeKey,
			boolean first, Runnable preAdd, Runnable postAdd) {
			MapEntry entry = new MapEntry(key, null);
			ListElement<Map.Entry<K, V>> entryEl = theEntries.getOrAdd(entry, afterKey, beforeKey, first, () -> {
				entry.setValue(value.apply(key));
				if (preAdd != null)
					preAdd.run();
			}, postAdd);
			if (entryEl != null)
				((MapEntry) entryEl.get()).theElementId = entryEl.getElementId();
			return entryEl == null ? null : handleFor(entryEl);
		}

		@Override
		public MutableOrderedMapEntry<K, V> mutableEntry(ElementId entryId) {
			MutableListElement<Map.Entry<K, V>> entryEl = theEntries.mutableElement(entryId);
			return entryEl == null ? null : mutableHandleFor(entryEl);
		}

		private OrderedMapEntry<K, V> handleFor(ListElement<Map.Entry<K, V>> entryEl) {
			return new MapElement(entryEl);
		}

		private MutableOrderedMapEntry<K, V> mutableHandleFor(MutableListElement<Map.Entry<K, V>> entryEl) {
			return new MutableMapElement(entryEl);
		}

		@Override
		public String canPut(K key, V value) {
			if (containsKey(key))
				return StdMsg.ELEMENT_EXISTS;
			else
				return theEntries.canAdd(new MapEntry(key, value));
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableMapEvent<? extends K, ? extends V>> action) {
			return theEntries.onChange(evt -> {
				V oldValue = ((MapEntry) evt.getNewValue()).getOldValue();
				ObservableMapEvent<K, V> mapEvent = new ObservableMapEvent.Default<>(evt.getElementId(), evt.getIndex(), evt.getType(),
					evt.getOldValue() == null ? null : evt.getOldValue().getKey(), evt.getNewValue().getKey(), oldValue,
						evt.getNewValue().getValue(), evt, evt.getMovement());
				try (Transaction t = mapEvent.use()) {
					action.accept(mapEvent);
				}
			});
		}

		@Override
		public int hashCode() {
			return BetterMap.hashCode(this);
		}

		@Override
		public boolean equals(Object obj) {
			return BetterMap.equals(this, obj);
		}

		@Override
		public String toString() {
			return entrySet().toString();
		}

		class MapEntry implements Map.Entry<K, V> {
			private final K theKey;
			private ElementId theElementId;
			private V theOldValue;
			private V theValue;

			MapEntry(K key, V value) {
				theKey = key;
				theValue = value;
			}

			@Override
			public K getKey() {
				return theKey;
			}

			@Override
			public V getValue() {
				return theValue;
			}

			V getOldValue() {
				return theOldValue;
			}

			@Override
			public V setValue(V value) {
				try (Transaction t = theEntries.lock(true, null)) {
					V oldValue;
					synchronized (this) {
						oldValue = theValue;
						theOldValue = oldValue;
						theValue = value;
						if (theElementId != null)
							theEntries.mutableElement(theElementId).set(this);
					}
					return oldValue;
				}
			}

			@Override
			public int hashCode() {
				return Objects.hashCode(theKey);
			}

			@Override
			public boolean equals(Object obj) {
				return obj instanceof Map.Entry && keySet().equivalence().elementEquals(theKey, ((Map.Entry<?, ?>) obj).getKey());
			}

			@Override
			public String toString() {
				return theKey + "=" + theValue;
			}
		}

		class MapElement implements OrderedMapEntry<K, V> {
			private final ListElement<Map.Entry<K, V>> theEntryEl;

			MapElement(ListElement<Entry<K, V>> entryEl) {
				theEntryEl = entryEl;
			}

			ListElement<Map.Entry<K, V>> getEntry() {
				return theEntryEl;
			}

			@Override
			public ElementId getElementId() {
				return theEntryEl.getElementId();
			}

			@Override
			public V get() {
				return theEntryEl.get().getValue();
			}

			@Override
			public int getElementsBefore() {
				return theEntryEl.getElementsBefore();
			}

			@Override
			public int getElementsAfter() {
				return theEntryEl.getElementsAfter();
			}

			@Override
			public OrderedMapEntry<K, V> getAdjacent(boolean next) {
				ListElement<Map.Entry<K, V>> adj = theEntryEl.getAdjacent(next);
				return adj == null ? null : new MapElement(adj);
			}

			@Override
			public K getKey() {
				return theEntryEl.get().getKey();
			}

			@Override
			public String toString() {
				return theEntryEl.get().toString();
			}
		}

		class MutableMapElement extends MapElement implements MutableOrderedMapEntry<K, V> {
			MutableMapElement(MutableListElement<Map.Entry<K, V>> entryEl) {
				super(entryEl);
			}

			@Override
			MutableListElement<Map.Entry<K, V>> getEntry() {
				return (MutableListElement<Map.Entry<K, V>>) super.getEntry();
			}

			@Override
			public MutableOrderedMapEntry<K, V> getAdjacent(boolean next) {
				MutableListElement<Map.Entry<K, V>> adj = getEntry().getAdjacent(next);
				return adj == null ? null : new MutableMapElement(adj);
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
				getEntry().get().setValue(value);
			}

			@Override
			public String canRemove() {
				return getEntry().canRemove();
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				getEntry().remove();
			}

			@Override
			public String toString() {
				return getEntry().get().toString();
			}
		}
	}

	/**
	 * An empty {@link ObservableMap} implementation
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	class EmptyObservableMap<K, V> extends AbstractIdentifiable implements ObservableMap<K, V> {
		private final ObservableSet<K> theKeySet;
		private ObservableCollection<V> theValues;
		private ObservableSet<Map.Entry<K, V>> theEntries;

		public EmptyObservableMap() {
			theKeySet = ObservableSet.<K> of();
		}

		@Override
		public boolean isEventing() {
			return false;
		}

		@Override
		public Object createIdentity() {
			return Identifiable.idFor(this, this::toString, this::hashCode, other -> other instanceof EmptyCollection);
		}

		@Override
		public EmptyObservableMap<K, V> alias(String alias) {
			return this; // No aliasing for this constant
		}

		@Override
		public CoreChangeSources getChangeSources() {
			return CoreChangeSources.empty();
		}

		@Override
		public boolean isLockSupported() {
			return true;
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return Transaction.NONE;
		}

		@Override
		public Equivalence<? super V> equivalence() {
			return Equivalence.DEFAULT;
		}

		@Override
		public ObservableSet<K> keySet() {
			return theKeySet;
		}

		@Override
		public ObservableCollection<V> values() {
			if (theValues == null)
				theValues = ObservableMap.super.values();
			return theValues;
		}

		@Override
		public ObservableSet<Entry<K, V>> entrySet() {
			if (theEntries == null)
				theEntries = ObservableMap.super.entrySet();
			return theEntries;
		}

		@Override
		public OrderedMapEntry<K, V> putEntry(K key, V value, ElementId after, ElementId before, boolean first) {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public OrderedMapEntry<K, V> getEntry(K key) {
			return null;
		}

		@Override
		public OrderedMapEntry<K, V> getEntryById(ElementId entryId) {
			throw new NoSuchElementException();
		}

		@Override
		public OrderedMapEntry<K, V> getOrPutEntry(K key, Function<? super K, ? extends V> value, ElementId after, ElementId before,
			boolean first, Runnable preAdd, Runnable postAdd) {
			return null;
		}

		@Override
		public MutableOrderedMapEntry<K, V> mutableEntry(ElementId entryId) {
			throw new NoSuchElementException();
		}

		@Override
		public String canPut(K key, V value) {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableMapEvent<? extends K, ? extends V>> action) {
			return Subscription.NONE;
		}

		@Override
		public int hashCode() {
			return BetterMap.hashCode(this);
		}

		@Override
		public boolean equals(Object obj) {
			return BetterMap.equals(this, obj);
		}

		@Override
		public String toString() {
			return entrySet().toString();
		}
	}

	/**
	 * Implements {@link ObservableMap#unmodifiable(ObservableMap)}
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	class UnmodifiableObservableMap<K, V> extends AbstractIdentifiable implements ObservableMap<K, V> {
		private final ObservableMap<K, V> theWrapped;
		private final ObservableSet<K> theKeySet;

		public UnmodifiableObservableMap(ObservableMap<K, V> wrapped) {
			theWrapped = wrapped;
			theKeySet = theWrapped.keySet().flow().unmodifiable().collectPassive();
		}

		/** @return The modifiable map this map wraps */
		protected ObservableMap<K, V> getWrapped() {
			return theWrapped;
		}

		@Override
		public boolean isEventing() {
			return theWrapped.isEventing();
		}

		@Override
		public CoreChangeSources getChangeSources() {
			return theWrapped.getChangeSources();
		}

		@Override
		public OrderedMapEntry<K, V> getEntry(K key) {
			return theWrapped.getEntry(key);
		}

		@Override
		public OrderedMapEntry<K, V> getOrPutEntry(K key, Function<? super K, ? extends V> value, ElementId after, ElementId before,
			boolean first, Runnable preAdd, Runnable postAdd) {
			return theWrapped.getEntry(key);
		}

		@Override
		public OrderedMapEntry<K, V> getEntryById(ElementId entryId) {
			return theWrapped.getEntryById(entryId);
		}

		@Override
		public MutableOrderedMapEntry<K, V> mutableEntry(ElementId entryId) {
			return new UnmodifiableEntry(getEntryById(entryId));
		}

		@Override
		protected Object createIdentity() {
			return theWrapped.getIdentity();
		}

		@Override
		public UnmodifiableObservableMap<K, V> alias(String alias) {
			super.alias(alias);
			return this;
		}

		@Override
		public boolean isLockSupported() {
			return theWrapped.isLockSupported();
		}

		@Override
		public Equivalence<? super V> equivalence() {
			return theWrapped.equivalence();
		}

		@Override
		public ObservableSet<K> keySet() {
			return theKeySet;
		}

		@Override
		public String canPut(K key, V value) {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableMapEvent<? extends K, ? extends V>> action) {
			return theWrapped.onChange(action);
		}

		@Override
		public int hashCode() {
			return theWrapped.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof UnmodifiableObservableMap)
				obj = ((UnmodifiableObservableMap<?, ?>) obj).theWrapped;
			return theWrapped.equals(obj);
		}

		@Override
		public String toString() {
			return theWrapped.toString();
		}

		class UnmodifiableEntry implements MutableOrderedMapEntry<K, V> {
			private final OrderedMapEntry<K, V> theWrappedEl;

			UnmodifiableEntry(OrderedMapEntry<K, V> wrappedEl) {
				theWrappedEl = wrappedEl;
			}

			@Override
			public K getKey() {
				return theWrappedEl.getKey();
			}

			@Override
			public ElementId getElementId() {
				return theWrappedEl.getElementId();
			}

			@Override
			public V get() {
				return theWrappedEl.get();
			}

			@Override
			public int getElementsBefore() {
				return theWrappedEl.getElementsBefore();
			}

			@Override
			public int getElementsAfter() {
				return theWrappedEl.getElementsAfter();
			}

			@Override
			public MutableOrderedMapEntry<K, V> getAdjacent(boolean next) {
				OrderedMapEntry<K, V> adj = theWrappedEl.getAdjacent(next);
				return adj == null ? null : new UnmodifiableEntry(adj);
			}

			@Override
			public String isEnabled() {
				return StdMsg.UNSUPPORTED_OPERATION;
			}

			@Override
			public String isAcceptable(V value) {
				return StdMsg.UNSUPPORTED_OPERATION;
			}

			@Override
			public void set(V value) throws UnsupportedOperationException, IllegalArgumentException {
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			}

			@Override
			public String canRemove() {
				return StdMsg.UNSUPPORTED_OPERATION;
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			}
		}
	}

	/**
	 * Implements {@link ObservableMap#of(Map)}
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	class ConstantObservableMap<K, V> extends AbstractIdentifiable implements ObservableMap<K, V> {
		private final BetterMap<K, V> theBacking;

		public ConstantObservableMap(BetterMap<K, V> backing) {
			theBacking = backing;
		}

		@Override
		public OrderedMapEntry<K, V> getEntry(K key) {
			return entryFor(theBacking.getEntry(key));
		}

		@Override
		public OrderedMapEntry<K, V> getOrPutEntry(K key, Function<? super K, ? extends V> value, ElementId after, ElementId before,
			boolean first, Runnable preAdd, Runnable postAdd) {
			return entryFor(theBacking.getEntry(key));
		}

		@Override
		public OrderedMapEntry<K, V> getEntryById(ElementId entryId) {
			return entryFor(theBacking.getEntryById(entryId));
		}

		@Override
		public MutableOrderedMapEntry<K, V> mutableEntry(ElementId entryId) {
			return new MutableEntry(getEntryById(entryId));
		}

		@Override
		public String canPut(K key, V value) {
			if (theBacking.containsKey(key))
				return null;
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		protected Object createIdentity() {
			return theBacking.getIdentity();
		}

		@Override
		public ConstantObservableMap<K, V> alias(String alias) {
			super.alias(alias);
			return this;
		}

		@Override
		public boolean isEventing() {
			return false;
		}

		@Override
		public CoreChangeSources getChangeSources() {
			return CoreChangeSources.empty();
		}

		@Override
		public boolean isLockSupported() {
			return true;
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return Transaction.NONE;
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return Transaction.NONE;
		}

		@Override
		public Equivalence<? super V> equivalence() {
			return Equivalence.DEFAULT;
		}

		@Override
		public ObservableSet<K> keySet() {
			return new KeySet<>(theBacking.keySet());
		}

		OrderedMapEntry<K, V> entryFor(MapEntryHandle<K, V> entry) {
			if (entry instanceof OrderedMapEntry)
				return (OrderedMapEntry<K, V>) entry;
			else
				return new OrderedEntry(entry);
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableMapEvent<? extends K, ? extends V>> action) {
			return Subscription.NONE;
		}

		@Override
		public int hashCode() {
			return theBacking.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return theBacking.equals(obj);
		}

		@Override
		public String toString() {
			return theBacking.toString();
		}

		protected static class KeySet<K> extends AbstractIdentifiable implements ObservableSet<K> {
			private final BetterSet<K> theBacking;

			protected KeySet(BetterSet<K> backing) {
				theBacking = backing;
			}

			@Override
			public KeySet<K> alias(String alias) {
				super.alias(alias);
				return this;
			}

			@Override
			public CoreChangeSources getChangeSources() {
				return CoreChangeSources.empty();
			}

			@Override
			public boolean isLockSupported() {
				return true;
			}

			@Override
			public Transaction lock(boolean write, Object cause) {
				return Transaction.NONE;
			}

			@Override
			public Transaction tryLock(boolean write, Object cause) {
				return Transaction.NONE;
			}

			@Override
			public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends K>> observer) {
				return Subscription.NONE;
			}

			@Override
			public void clear() {
			}

			@Override
			public Equivalence<? super K> equivalence() {
				return Equivalence.DEFAULT;
			}

			@Override
			public void setValue(Collection<ElementId> elements, K value) {
				if (!elements.isEmpty())
					throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			}

			@Override
			public ListElement<K> getElement(int index) throws IndexOutOfBoundsException {
				ListElement<K> el = getTerminalElement(true);
				for (int i = 0; el != null && i < index; i++) {
					el = el.getAdjacent(true);
				}
				if (el == null)
					throw new IndexOutOfBoundsException(index + " of " + size());
				return el;
			}

			@Override
			public boolean isContentControlled() {
				return true;
			}

			@Override
			public ListElement<K> getElement(K value, boolean first) {
				return elementFor(theBacking.getElement(value, first));
			}

			@Override
			public ListElement<K> getElement(ElementId id) {
				return elementFor(theBacking.getElement(id));
			}

			@Override
			public ListElement<K> getTerminalElement(boolean first) {
				return elementFor(theBacking.getTerminalElement(first));
			}

			@Override
			public MutableListElement<K> mutableElement(ElementId id) {
				return new MutableElement(getElement(id));
			}

			@Override
			public BetterList<CollectionElement<K>> getElementsBySource(ElementId sourceEl, BetterCollection<?> sourceCollection) {
				return theBacking.getElementsBySource(sourceEl, sourceCollection);
			}

			@Override
			public BetterList<ElementId> getSourceElements(ElementId localElement, BetterCollection<?> sourceCollection) {
				return theBacking.getSourceElements(localElement, sourceCollection);
			}

			@Override
			public ElementId getEquivalentElement(ElementId equivalentEl) {
				return theBacking.getEquivalentElement(equivalentEl);
			}

			@Override
			public String canAdd(K value, ElementId after, ElementId before) {
				if (theBacking.contains(value))
					return null;
				return StdMsg.UNSUPPORTED_OPERATION;
			}

			@Override
			public ListElement<K> addElement(K value, ElementId after, ElementId before, boolean first)
				throws UnsupportedOperationException, IllegalArgumentException {
				if (theBacking.contains(value))
					return null;
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			}

			@Override
			public String canMove(ElementId valueEl, ElementId after, ElementId before) {
				if (after != null && valueEl.compareTo(after) < 0)
					return StdMsg.UNSUPPORTED_OPERATION;
				else if (before != null && valueEl.compareTo(before) > 0)
					return StdMsg.UNSUPPORTED_OPERATION;
				else
					return null;
			}

			@Override
			public ListElement<K> move(ElementId valueEl, ElementId after, ElementId before, boolean first, Runnable afterRemove)
				throws UnsupportedOperationException, IllegalArgumentException {
				String msg = canMove(valueEl, after, before);
				if (msg != null)
					throw new UnsupportedOperationException(msg);
				return getElement(valueEl);
			}

			@Override
			public CoreId getCoreId() {
				return CoreId.EMPTY;
			}

			@Override
			protected Object createIdentity() {
				return theBacking.getIdentity();
			}

			@Override
			public ThreadConstraint getThreadConstraint() {
				return ThreadConstraint.NONE;
			}

			@Override
			public Collection<Cause> getCurrentCauses() {
				return Collections.emptyList();
			}

			@Override
			public boolean isEventing() {
				return false;
			}

			@Override
			public ListElement<K> getOrAdd(K value, ElementId after, ElementId before, boolean first, Runnable preAdd, Runnable postAdd) {
				return elementFor(theBacking.getElement(value, first));
			}

			@Override
			public boolean isConsistent(ElementId element) {
				return theBacking.isConsistent(element);
			}

			@Override
			public boolean checkConsistency() {
				return theBacking.checkConsistency();
			}

			@Override
			public <X> boolean repair(ElementId element, RepairListener<K, X> listener) {
				return false;
			}

			@Override
			public <X> boolean repair(RepairListener<K, X> listener) {
				return false;
			}

			@Override
			public boolean isEmpty() {
				return theBacking.isEmpty();
			}

			@Override
			public long getStamp() {
				return 0;
			}

			@Override
			public int size() {
				return theBacking.size();
			}

			@Override
			public int hashCode() {
				return theBacking.hashCode();
			}

			@Override
			public boolean equals(Object obj) {
				return theBacking.equals(obj);
			}

			@Override
			public String toString() {
				return theBacking.toString();
			}

			ListElement<K> elementFor(CollectionElement<K> el) {
				if (el instanceof ListElement)
					return (ListElement<K>) el;
				else
					return new Element(el);
			}

			protected class Element implements ListElement<K> {
				private final CollectionElement<K> theBackingEl;

				protected Element(CollectionElement<K> backingEl) {
					theBackingEl = backingEl;
				}

				protected CollectionElement<K> getBackingEl() {
					return theBackingEl;
				}

				@Override
				public ElementId getElementId() {
					return theBackingEl.getElementId();
				}

				@Override
				public K get() {
					return theBackingEl.get();
				}

				@Override
				public ListElement<K> getAdjacent(boolean next) {
					CollectionElement<K> adj = theBackingEl.getAdjacent(next);
					return adj == null ? null : new Element(adj);
				}

				@Override
				public int getElementsBefore() {
					CollectionElement<K> el = getTerminalElement(true);
					int i;
					for (i = 0; el != null && !el.getElementId().equals(theBackingEl.getElementId()); i++) {
						el = el.getAdjacent(true);
					}
					if (el == null)
						throw new NoSuchElementException(theBackingEl.getElementId().toString());
					return i;
				}

				@Override
				public int getElementsAfter() {
					CollectionElement<K> el = getTerminalElement(false);
					int i;
					for (i = 0; el != null && !el.getElementId().equals(theBackingEl.getElementId()); i++) {
						el = el.getAdjacent(false);
					}
					if (el == null)
						throw new NoSuchElementException(theBackingEl.getElementId().toString());
					return i;
				}

				@Override
				public int hashCode() {
					return theBackingEl.hashCode();
				}

				@Override
				public boolean equals(Object obj) {
					return theBackingEl.equals(obj);
				}

				@Override
				public String toString() {
					return theBackingEl.toString();
				}
			}

			protected class MutableElement extends Element implements MutableListElement<K> {
				protected MutableElement(CollectionElement<K> backingEl) {
					super(backingEl);
				}

				@Override
				public MutableListElement<K> getAdjacent(boolean next) {
					CollectionElement<K> adj = getBackingEl().getAdjacent(next);
					return adj == null ? null : new MutableElement(adj);
				}

				@Override
				public int getElementsBefore() {
					if (getBackingEl() instanceof ListElement)
						return ((ListElement<K>) getBackingEl()).getElementsBefore();
					else
						return super.getElementsBefore();
				}

				@Override
				public int getElementsAfter() {
					if (getBackingEl() instanceof ListElement)
						return ((ListElement<K>) getBackingEl()).getElementsAfter();
					else
						return super.getElementsAfter();
				}

				@Override
				public String isEnabled() {
					return StdMsg.UNSUPPORTED_OPERATION;
				}

				@Override
				public String isAcceptable(K value) {
					return StdMsg.UNSUPPORTED_OPERATION;
				}

				@Override
				public void set(K value) throws UnsupportedOperationException, IllegalArgumentException {
					throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
				}

				@Override
				public String canRemove() {
					return StdMsg.UNSUPPORTED_OPERATION;
				}

				@Override
				public void remove() throws UnsupportedOperationException {
					throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
				}
			}
		}

		class OrderedEntry implements OrderedMapEntry<K, V> {
			private final MapEntryHandle<K, V> theEntry;

			OrderedEntry(MapEntryHandle<K, V> entry) {
				theEntry = entry;
			}

			protected MapEntryHandle<K, V> getEntry() {
				return theEntry;
			}

			@Override
			public ElementId getElementId() {
				return theEntry.getElementId();
			}

			@Override
			public V get() {
				return theEntry.get();
			}

			@Override
			public int getElementsBefore() {
				MapEntryHandle<K, V> el = getTerminalEntry(true);
				int i;
				for (i = 0; el != null && !el.getElementId().equals(theEntry.getElementId()); i++) {
					el = el.getAdjacent(true);
				}
				if (el == null)
					throw new NoSuchElementException(theEntry.getElementId().toString());
				return i;
			}

			@Override
			public int getElementsAfter() {
				MapEntryHandle<K, V> el = getTerminalEntry(false);
				int i;
				for (i = 0; el != null && !el.getElementId().equals(theEntry.getElementId()); i++) {
					el = el.getAdjacent(false);
				}
				if (el == null)
					throw new NoSuchElementException(theEntry.getElementId().toString());
				return i;
			}

			@Override
			public OrderedMapEntry<K, V> getAdjacent(boolean next) {
				MapEntryHandle<K, V> adj = theEntry.getAdjacent(next);
				return adj == null ? null : new OrderedEntry(adj);
			}

			@Override
			public K getKey() {
				return theEntry.getKey();
			}

			@Override
			public int hashCode() {
				return theEntry.hashCode();
			}

			@Override
			public boolean equals(Object obj) {
				return theEntry.equals(obj);
			}

			@Override
			public String toString() {
				return theEntry.toString();
			}
		}

		public class MutableEntry extends OrderedEntry implements MutableOrderedMapEntry<K, V> {
			protected MutableEntry(MapEntryHandle<K, V> backing) {
				super(backing);
			}

			@Override
			public int getElementsBefore() {
				if (getEntry() instanceof OrderedMapEntry)
					return ((OrderedMapEntry<K, V>) getEntry()).getElementsBefore();
				else
					return super.getElementsBefore();
			}

			@Override
			public int getElementsAfter() {
				if (getEntry() instanceof OrderedMapEntry)
					return ((OrderedMapEntry<K, V>) getEntry()).getElementsBefore();
				else
					return super.getElementsAfter();
			}

			@Override
			public MutableOrderedMapEntry<K, V> getAdjacent(boolean next) {
				MapEntryHandle<K, V> adj = getEntry().getAdjacent(next);
				return adj == null ? null : new MutableEntry(adj);
			}

			@Override
			public String isEnabled() {
				return StdMsg.UNSUPPORTED_OPERATION;
			}

			@Override
			public String isAcceptable(V value) {
				return StdMsg.UNSUPPORTED_OPERATION;
			}

			@Override
			public void set(V value) throws UnsupportedOperationException, IllegalArgumentException {
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			}

			@Override
			public String canRemove() {
				return StdMsg.UNSUPPORTED_OPERATION;
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			}
		}
	}
}
