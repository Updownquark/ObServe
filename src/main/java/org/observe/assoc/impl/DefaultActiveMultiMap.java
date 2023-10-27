package org.observe.assoc.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.Equivalence;
import org.observe.Observable;
import org.observe.Subscription;
import org.observe.assoc.ObservableMultiMap;
import org.observe.assoc.ObservableMultiMapEvent;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.CollectionElementMove;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollection.DistinctDataFlow;
import org.observe.collect.ObservableCollectionActiveManagers;
import org.observe.collect.ObservableCollectionActiveManagers.ActiveCollectionManager;
import org.observe.collect.ObservableCollectionActiveManagers.ActiveValueStoredManager;
import org.observe.collect.ObservableCollectionActiveManagers.CollectionElementListener;
import org.observe.collect.ObservableCollectionActiveManagers.DerivedCollectionElement;
import org.observe.collect.ObservableCollectionActiveManagers.ElementAccepter;
import org.observe.collect.ObservableCollectionDataFlowImpl;
import org.observe.collect.ObservableCollectionEvent;
import org.observe.collect.ObservableSet;
import org.observe.util.TypeTokens;
import org.observe.util.WeakListening;
import org.qommons.ArrayUtils;
import org.qommons.Identifiable;
import org.qommons.Lockable;
import org.qommons.Lockable.CoreId;
import org.qommons.ThreadConstraint;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.*;
import org.qommons.collect.BetterSortedList.SortedSearchFilter;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.tree.BetterTreeMap;
import org.qommons.tree.BetterTreeSet;

import com.google.common.reflect.TypeToken;

/**
 * Default, actively-gathered implementation of {@link ObservableMultiMap}
 *
 * @param <S> The type of the source collection whose data the map is gathered from
 * @param <K> The key type of the map
 * @param <V> The value type of the map
 */
public class DefaultActiveMultiMap<S, K, V> extends AbstractDerivedObservableMultiMap<S, K, V> {
	private final ActiveValueStoredManager<S, ?, K> theKeyManager;
	private final ActiveCollectionManager<S, ?, V> theValueManager;

	private final WeakListening.Builder theWeakListening;

	private final BetterSortedSet<KeyEntry> theActiveEntries;

	private final ObservableSet<K> theKeySet;

	private final BetterMap<ElementId, Set<KeyEntry>> theKeysBySourceElement;

	private long theStamp;
	private int theValueSize;

	private volatile boolean isNeedingNewKey;
	private volatile KeyEntry theNewKeyEntry;

	private final ListenerList<Consumer<? super ObservableMultiMapEvent<? extends K, ? extends V>>> theMapListeners;
	private final ListenerList<Consumer<? super ObservableCollectionEvent<? extends K>>> theKeySetListeners;
	private final BetterMap<K, ListenerList<Consumer<? super ObservableCollectionEvent<? extends V>>>> theValueListeners;

	/**
	 * @param source The source collection whose data the map is to be gathered from
	 * @param keyFlow The data flow for the map's key set
	 * @param valueFlow The data flow for all the map's values
	 * @param until The observable that, when fired, will release all of this map's resources
	 * @param addKey Stores the key for which the next value is to be added
	 */
	public DefaultActiveMultiMap(ObservableCollection<S> source, DistinctDataFlow<S, ?, K> keyFlow, CollectionDataFlow<S, ?, V> valueFlow,
		Observable<?> until, AddKeyHolder<K> addKey) {
		super(source, keyFlow, valueFlow, addKey);
		theKeyManager = keyFlow.manageActive();
		theValueManager = valueFlow.manageActive();

		theActiveEntries = BetterTreeSet.<KeyEntry> buildTreeSet(KeyEntry::compareBySource).build();
		theKeysBySourceElement = BetterHashMap.build().build();

		theMapListeners = ListenerList.build().withFastSize(false).build();
		theKeySetListeners = ListenerList.build().withFastSize(false).build();
		theValueListeners = keyFlow.equivalence().createMap();

		// Must maintain a strong reference to the event listening so it is not GC'd while the collection is still alive
		theWeakListening = WeakListening.build().withUntil(r -> until.act(v -> r.run()));
		WeakListening listening = theWeakListening.getListening();
		theKeyManager.begin(true, new ElementAccepter<K>() {
			@Override
			public void accept(ObservableCollectionActiveManagers.DerivedCollectionElement<K> element, Object... causes) {
				theStamp++;
				KeyEntry entry = addKey(element, causes);
				if (isNeedingNewKey)
					theNewKeyEntry = entry;
				element.setListener(new CollectionElementListener<K>() {
					@Override
					public void update(K oldValue, K newValue, boolean internalOnly, Object... updateCauses) {
						theStamp++;
						entry.updated(oldValue, newValue, internalOnly, updateCauses);
					}

					@Override
					public void removed(K value, Object... removeCauses) {
						theStamp++;
						entry.removed(value, removeCauses);
					}
				});
			}
		}, listening);
		theValueManager.begin(true, new ElementAccepter<V>() {
			@Override
			public void accept(ObservableCollectionActiveManagers.DerivedCollectionElement<V> element, Object... causes) {
				theStamp++;
				ValueRef ref = addValue(element, causes);
				element.setListener(new CollectionElementListener<V>() {
					@Override
					public void update(V oldValue, V newValue, boolean internalOnly, Object... updateCauses) {
						theStamp++;
						ref.updated(oldValue, newValue, internalOnly, updateCauses);
					}

					@Override
					public void removed(V value, Object... removeCauses) {
						theStamp++;
						ref.removed(value, removeCauses);
					}
				});

			}
		}, listening);

		theKeySet = createKeySet();
	}

	private synchronized ListenerList<Consumer<? super ObservableCollectionEvent<? extends V>>> getValueListeners(K key) {
		return theValueListeners.computeIfAbsent(key, k -> ListenerList.build().withFastSize(false).withInUse(inUse -> {
			if (!inUse)
				theValueListeners.remove(key); // Clean up
		}).build());
	}

	/** @return The new key set for this map */
	protected ObservableSet<K> createKeySet() {
		return new KeySet();
	}

	@Override
	protected Transactable getKeyLocker() {
		return theKeyManager;
	}

	/** @return The key manager for this map */
	protected ActiveValueStoredManager<S, ?, K> getKeyManager() {
		return theKeyManager;
	}

	@Override
	protected ActiveCollectionManager<?, ?, V> getValueManager() {
		return theValueManager;
	}

	/** @return The set of all keys managed by this map that have at least one value and therefore are included in the key set */
	protected BetterSortedSet<KeyEntry> getActiveEntries() {
		return theActiveEntries;
	}

	@Override
	public long getStamp() {
		return theStamp;
	}

	@Override
	public ObservableSet<K> keySet() {
		return theKeySet;
	}

	@Override
	public MultiEntryHandle<K, V> getEntryById(ElementId keyId) {
		KeyEntryId kei = (KeyEntryId) keyId;
		if (kei.getMap() != this)
			throw new NoSuchElementException();
		return kei.entry;
	}

	@Override
	public MultiEntryHandle<K, V> getOrPutEntry(K key, Function<? super K, ? extends Iterable<? extends V>> value, ElementId afterKey,
		ElementId beforeKey, boolean first, Runnable preAdd, Runnable postAdd) {
		long stamp = theStamp;
		MultiEntryHandle<K, V> found = getEntry(key);
		if (found != null)
			return found;
		try (Transaction t = Lockable.lockAll(//
			Lockable.lockable(this, true, null), getAddKey())) {
			if (stamp != theStamp) {
				// Need to try again since someone else could have done it while we were waiting for the lock
				found = getEntry(key);
				if (found != null)
					return found;
			}
			ObservableCollectionActiveManagers.DerivedCollectionElement<V> afterEl = getValueElement(afterKey, false);
			ObservableCollectionActiveManagers.DerivedCollectionElement<V> beforeEl = getValueElement(beforeKey, true);
			isNeedingNewKey = true;
			KeyEntry newKeyEntry = null;
			if (preAdd != null)
				preAdd.run();
			getAddKey().accept(key);
			for (V v : value.apply(key)) {
				theValueManager.addElement(v, afterEl, beforeEl, first);
				if (newKeyEntry == null) {
					newKeyEntry = theNewKeyEntry;
					if (newKeyEntry == null)
						throw new IllegalStateException("Values added, but no new key");
					theNewKeyEntry = null;
				}
				first = true; // Add the next elements after
				isNeedingNewKey = false;
			}
			getAddKey().clear();
			if (postAdd != null)
				postAdd.run();
			return newKeyEntry;
		}
	}

	@Override
	public int valueSize() {
		return theValueSize;
	}

	@Override
	public boolean clear() {
		return theValueManager.clear();
	}

	@Override
	public ObservableMultiEntry<K, V> watch(K key) {
		return new KeyValueCollection(key, null);
	}

	@Override
	public ObservableMultiEntry<K, V> watchById(ElementId keyId) {
		MultiEntryHandle<K, V> entry = getEntryById(keyId);
		return new KeyValueCollection(entry.getKey(), (KeyEntry) entry);
	}

	@Override
	public Subscription onChange(Consumer<? super ObservableMultiMapEvent<? extends K, ? extends V>> action) {
		return theMapListeners.add(action, true)::run;
	}

	@Override
	public String toString() {
		return entrySet().toString();
	}

	@Override
	protected void finalize() throws Throwable {
		// TODO Move this functionality to java.lang.ref.Cleanable, BUT ONLY when JDK 8 is no longer supported
		super.finalize();
		theWeakListening.unsubscribe();
	}

	private ObservableCollectionActiveManagers.DerivedCollectionElement<V> getValueElement(ElementId keyId, boolean firstValue) {
		if (keyId == null)
			return null;
		KeyEntryId kei = (KeyEntryId) keyId;
		if (kei.getMap() != this)
			throw new NoSuchElementException();
		KeyEntry entry = kei.entry;
		return entry.theValues.theValues.getTerminalElement(firstValue).get().theValueElement;
	}

	// These are synchronized because even though the keys and values are from the same source,
	// other sources of change or updates may have been introduced to the key or value flows.
	// Without protection, one of these other sources could potentially fire during a source change,
	// causing simultaneous modification and corruption.

	private synchronized KeyEntry addKey(ObservableCollectionActiveManagers.DerivedCollectionElement<K> keyElement, Object... causes) {
		KeyEntry entry = new KeyEntry(keyElement);
		return entry;
	}

	private synchronized ValueRef addValue(ObservableCollectionActiveManagers.DerivedCollectionElement<V> valueElement, Object... causes) {
		ValueRef ref = new ValueRef(valueElement, causes);
		return ref;
	}

	/** Implements ElementId for key and entry elements */
	protected class KeyEntryId implements ElementId {
		final KeyEntry entry;

		KeyEntryId(DefaultActiveMultiMap<S, K, V>.KeyEntry entry) {
			this.entry = entry;
		}

		DefaultActiveMultiMap<S, K, V> getMap() {
			return DefaultActiveMultiMap.this;
		}

		@Override
		public int compareTo(ElementId o) {
			if (entry.activeEntryId != null && ((KeyEntryId) o).entry.activeEntryId != null)
				return entry.activeEntryId.compareTo(((KeyEntryId) o).entry.activeEntryId);
			return entry.theKeyElement.compareTo(((KeyEntryId) o).entry.theKeyElement);
		}

		@Override
		public boolean isPresent() {
			return entry.activeEntryId.isPresent();
		}

		@Override
		public int hashCode() {
			return entry.activeEntryId.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof DefaultActiveMultiMap.KeyEntryId && entry == ((KeyEntryId) obj).entry;
		}

		@Override
		public String toString() {
			return entry.toString();
		}
	}

	/**
	 * Holds all information regarding a key managed by this map. This is the implementation of {@link MultiEntryHandle} returned by the map
	 * as well.
	 */
	protected class KeyEntry implements MultiEntryHandle<K, V> {
		final ObservableCollectionActiveManagers.DerivedCollectionElement<K> theKeyElement;
		/** These entries are references into {@link DefaultActiveMultiMap#theKeysBySourceElement}, not the source elements themselves */
		final Set<ElementId> theSources;
		final ValueCollection theValues;
		final KeyEntryId theExposedId;

		ElementId activeEntryId;
		long keyStamp;
		private K theRemovedKey;

		private MutableKeyEntry theMutableEntry;

		KeyEntry(ObservableCollectionActiveManagers.DerivedCollectionElement<K> keyElement) {
			theKeyElement = keyElement;
			theSources = new HashSet<>();
			theValues = new ValueCollection(this);
			theExposedId = new KeyEntryId(this);
			updateSources();
		}

		int compareByEntry(KeyEntry other) {
			return activeEntryId.compareTo(other.activeEntryId);
		}

		int compareBySource(KeyEntry other) {
			return theKeyElement.compareTo(other.theKeyElement);
		}

		boolean isPresent() {
			return !theSources.isEmpty();
		}

		@Override
		public ElementId getElementId() {
			return theExposedId;
		}

		@Override
		public K getKey() {
			if (isPresent())
				return theKeyElement.get();
			else
				return theRemovedKey;
		}

		@Override
		public ValueCollection getValues() {
			return theValues;
		}

		boolean activate(K key) {
			if (activeEntryId != null && activeEntryId.isPresent())
				return false;
			activeEntryId = theActiveEntries.addElement(this, false).getElementId();
			return true;
		}

		void deactivate(K key, int keyIndex, Object... causes) {
			if (activeEntryId == null || !activeEntryId.isPresent())
				return; // Already removed
			theActiveEntries.mutableElement(activeEntryId).remove();
			if (!theKeySetListeners.isEmpty()) {
				ObservableCollectionEvent<K> keyEvent = new ObservableCollectionEvent<>(theExposedId, keyIndex, CollectionChangeType.remove,
					key, key, causes);
				try (Transaction evtT = keyEvent.use()) {
					theKeySetListeners.forEach(//
						listener -> listener.accept(keyEvent));
				}
			}
		}

		private void updateSources() {
			SortedSet<ElementId> newSources = new TreeSet<>();
			for (ElementId source : getKeyManager().getSourceElements(theKeyElement, getSourceCollection())) {
				if (source.isPresent())
					newSources.add(source);
			}
			// Remove sources the key no longer represents
			Iterator<ElementId> oldSourceIter = theSources.iterator();
			while (oldSourceIter.hasNext()) {
				ElementId source = oldSourceIter.next();
				MapEntryHandle<ElementId, Set<KeyEntry>> sourceEntry = theKeysBySourceElement.getEntryById(source);
				if (!newSources.remove(sourceEntry.getKey())) { // Remove so we don't add it next
					// This key no longer represents the source
					sourceEntry.getValue().remove(this);
					if (sourceEntry.getValue().isEmpty())
						theKeysBySourceElement.mutableEntry(source).remove();
					oldSourceIter.remove();
				}
			}
			// Register this key for new source elements
			for (ElementId source : newSources) {
				MapEntryHandle<ElementId, Set<KeyEntry>> sourceEntry = theKeysBySourceElement.getOrPutEntry(source, __ -> new HashSet<>(),
					null, null, false, null, null);
				sourceEntry.getValue().add(this);
				theSources.add(sourceEntry.getElementId());
			}
		}

		void updated(K oldKey, K newKey, boolean internalOnly, Object... causes) {
			synchronized (DefaultActiveMultiMap.this) {
				if (!getActiveKeyFlow().equivalence().elementEquals(oldKey, newKey))
					keyStamp++;
				updateSources();

				if (!internalOnly && !theSources.isEmpty() && activeEntryId != null && activeEntryId.isPresent()) {
					int keyIndex = theActiveEntries.getElementsBefore(activeEntryId);
					if (!theMapListeners.isEmpty()) {
						ObservableMultiMapEvent<K, V> mapEvent = new ObservableMultiMapEvent<>(theExposedId, null, keyIndex, -1, //
							CollectionChangeType.set, oldKey, newKey, null, null, causes);
						try (Transaction evtT = mapEvent.use()) {
							theMapListeners.forEach(//
								listener -> listener.accept(mapEvent));
						}
					}
					if (!theKeyManager.equivalence().elementEquals(newKey, oldKey)) {
						ListenerList<Consumer<? super ObservableCollectionEvent<? extends V>>> listeners = theValueListeners.get(oldKey);
						if (listeners != null) {
							int index = theValues.theValues.size() - 1;
							for (CollectionElement<ValueRef> value : theValues.theValues.elements().reverse()) {
								V v = value.get().get();
								ObservableCollectionEvent<V> event = new ObservableCollectionEvent<>(value.getElementId(), index--, //
									CollectionChangeType.remove, v, v, causes);
								try (Transaction evtT = event.use()) {
									listeners.forEach(//
										listener -> listener.accept(event));
								}
							}
						}
						listeners = theValueListeners.get(newKey);
						if (listeners != null) {
							int index = 0;
							for (CollectionElement<ValueRef> value : theValues.theValues.elements()) {
								V v = value.get().get();
								ObservableCollectionEvent<V> event = new ObservableCollectionEvent<>(value.getElementId(), index++, //
									CollectionChangeType.add, null, v, causes);
								try (Transaction evtT = event.use()) {
									listeners.forEach(//
										listener -> listener.accept(event));
								}
							}
						}
					}
					if (!theKeySetListeners.isEmpty()) {
						ObservableCollectionEvent<K> keyEvent = new ObservableCollectionEvent<>(theExposedId, keyIndex, //
							CollectionChangeType.set, oldKey, newKey, causes);
						try (Transaction evtT = keyEvent.use()) {
							theKeySetListeners.forEach(//
								listener -> listener.accept(keyEvent));
						}
					}
				}
			}
		}

		void removed(K key, Object... causes) {
			synchronized (DefaultActiveMultiMap.this) {
				theRemovedKey = key;
				Iterator<ElementId> oldSourceIter = theSources.iterator();
				while (oldSourceIter.hasNext()) {
					ElementId source = oldSourceIter.next();
					// This key no longer represents the source
					MapEntryHandle<ElementId, Set<KeyEntry>> sourceEntry = theKeysBySourceElement.getEntryById(source);
					sourceEntry.getValue().remove(this);
					if (sourceEntry.getValue().isEmpty())
						theKeysBySourceElement.mutableEntry(source).remove();
					oldSourceIter.remove();
				}
			}
		}

		MutableCollectionElement<K> mutable() {
			if (theMutableEntry == null)
				theMutableEntry = new MutableKeyEntry();
			return theMutableEntry;
		}

		@Override
		public String toString() {
			return new StringBuilder().append(get()).append('=').append(theValues).toString();
		}

		class MutableKeyEntry implements MutableCollectionElement<K> {
			@Override
			public ElementId getElementId() {
				return KeyEntry.this.getElementId();
			}

			@Override
			public K get() {
				return KeyEntry.this.get();
			}

			@Override
			public BetterCollection<K> getCollection() {
				return theKeySet;
			}

			@Override
			public String isEnabled() {
				return theKeyElement.isEnabled();
			}

			@Override
			public String isAcceptable(K value) {
				return theKeyElement.isAcceptable(value);
			}

			@Override
			public void set(K value) throws UnsupportedOperationException, IllegalArgumentException {
				theKeyElement.set(value);
			}

			@Override
			public String canRemove() {
				return theKeyElement.canRemove();
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				theKeyElement.remove();
			}
		}
	}

	/**
	 * Holds all information regarding a particular value element managed by this map. Not all values are necessarily mapped to keys in the
	 * map, and some values may be mapped to multiple keys.
	 */
	protected class ValueRef implements Comparable<ValueRef> {
		final ObservableCollectionActiveManagers.DerivedCollectionElement<V> theValueElement;
		/** Values are the reference into {@link ValueCollection#theValues} for each key */
		final BetterSortedMap<KeyEntry, ElementId> theKeyMembership;

		ValueRef(ObservableCollectionActiveManagers.DerivedCollectionElement<V> valueElement, Object... causes) {
			theValueElement = valueElement;
			theKeyMembership = BetterTreeMap.<KeyEntry> build(KeyEntry::compareByEntry).buildMap();

			// Initialize and insert into all keys the new value belongs to
			SortedSet<KeyEntry> newKeys = new TreeSet<>(KeyEntry::compareBySource);
			for (ElementId source : getValueManager().getSourceElements(theValueElement, getSourceCollection())) {
				if (source.isPresent()) {
					Set<KeyEntry> sourceKeys = theKeysBySourceElement.get(source);
					if (sourceKeys != null)
						newKeys.addAll(sourceKeys);
				}
			}
			if (!newKeys.isEmpty()) {
				V value = theValueElement.get();
				for (KeyEntry key : newKeys)
					theKeyMembership.put(key, key.theValues.addValue(this, value, causes).getElementId());
			}
		}

		@Override
		public int compareTo(ValueRef o) {
			return theValueElement.compareTo(o.theValueElement);
		}

		/** @return The value in this element */
		protected V get() {
			return theValueElement.get();
		}

		void updated(V oldValue, V newValue, boolean internalOnly, Object... causes) {
			synchronized (DefaultActiveMultiMap.this) {
				// Check source elements and adjust against keys
				Set<ElementId> sources = new HashSet<>();
				SortedSet<KeyEntry> newKeys = new TreeSet<>();
				for (ElementId source : getValueManager().getSourceElements(theValueElement, getSourceCollection())) {
					if (sources.add(source) && source.isPresent()) {
						Set<KeyEntry> sourceKeys = theKeysBySourceElement.get(source);
						if (sourceKeys != null)
							newKeys.addAll(sourceKeys);
					}
				}
				// Remove this value from keys it no longer belongs to
				TypeToken<K> keyType = null;
				TypeToken<V> valueType = null;
				MapEntryHandle<KeyEntry, ElementId> oldKey = theKeyMembership.getTerminalEntry(true);
				List<MapEntryHandle<KeyEntry, ElementId>> updated = null;
				while (oldKey != null) {
					if (oldKey.getKey().isPresent() && newKeys.remove(oldKey.getKey())) {
						if (!internalOnly) {
							if (updated == null)
								updated = new ArrayList<>();
							updated.add(oldKey);
						}
					} else {
						// This key no longer represents the source
						oldKey.getKey().theValues.valueRemoved(oldKey.getValue(), oldKey.getKey().get(), oldValue, //
							(keyType != null ? keyType : (keyType = getKeyType())),
							(valueType != null ? valueType : (valueType = getValueType())), //
							-1, causes);
						theKeyMembership.mutableEntry(oldKey.getElementId()).remove();
					}
					oldKey = theKeyMembership.getAdjacentEntry(oldKey.getElementId(), true);
				}
				if (updated != null) {
					// Fire updates for keys that this value still belongs to
					for (MapEntryHandle<KeyEntry, ElementId> key : updated)
						key.getKey().theValues.valueUpdated(key.getValue(), oldValue, newValue, causes);
				}
				// Register this key for new source elements
				for (KeyEntry key : newKeys)
					theKeyMembership.put(key, key.theValues.addValue(this, newValue, causes).getElementId());
			}
		}

		void removed(V value, Object... causes) {
			synchronized (DefaultActiveMultiMap.this) {
				TypeToken<K> keyType = null;
				TypeToken<V> valueType = null;
				MapEntryHandle<KeyEntry, ElementId> oldKey = theKeyMembership.getTerminalEntry(true);
				while (oldKey != null) {
					oldKey.getKey().theValues.valueRemoved(oldKey.getValue(), oldKey.getKey().get(), value, //
						(keyType != null ? keyType : (keyType = getKeyType())),
						(valueType != null ? valueType : (valueType = getValueType())), //
						-1, causes);
					theKeyMembership.mutableEntry(oldKey.getElementId()).remove();
					oldKey = theKeyMembership.getAdjacentEntry(oldKey.getElementId(), true);
				}
			}
		}

		@Override
		public String toString() {
			return String.valueOf(get());
		}
	}

	/** Implements a collection (list) of values for a particular key in the map */
	protected class ValueCollection extends AbstractIdentifiable implements BetterList<V> {
		private final KeyEntry theEntry;
		final BetterSortedSet<ValueRef> theValues;

		ValueCollection(KeyEntry entry) {
			theEntry = entry;
			theValues = BetterTreeSet.<ValueRef> buildTreeSet(ValueRef::compareTo).build();
		}

		CollectionElement<ValueRef> addValue(ValueRef value, V val, Object... causes) {
			K key = theEntry.get();
			boolean newKey = theEntry.activate(key);
			return addValue(value, getKeyType(), getValueType(), theActiveEntries.getElementsBefore(theEntry.activeEntryId), key, val,
				newKey, causes);
		}

		private CollectionElement<ValueRef> addValue(ValueRef value, TypeToken<K> keyType, TypeToken<V> valueType, int keyIndex, K key,
			V val, boolean newKey, Object... causes) {
			CollectionElement<ValueRef> added = theValues.addElement(value, false);
			theValueSize++;
			if (keyIndex < 0)
				keyIndex = theActiveEntries.getElementsBefore(theEntry.theExposedId);
			int valueIndex = theValues.getElementsBefore(added.getElementId());
			ValueElementId addedId = new ValueElementId(theEntry, added.getElementId(), added.get());
			CollectionElementMove move = null;
			for (Object cause : causes) {
				if (cause instanceof ObservableCollectionEvent && ((ObservableCollectionEvent<?>) cause).getMovement() != null)
					move = ((ObservableCollectionEvent<?>) cause).getMovement();
			}
			if (move != null)
				causes = ArrayUtils.add(causes, move);
			if (!theMapListeners.isEmpty()) {
				ObservableMultiMapEvent<K, V> mapEvent = new ObservableMultiMapEvent<>(theEntry.theExposedId, addedId, keyIndex, valueIndex, //
					CollectionChangeType.add, key, key, null, val, causes);
				try (Transaction evtT = mapEvent.use()) {
					theMapListeners.forEach(//
						listener -> listener.accept(mapEvent));
				}
			}
			if (newKey) {
				ObservableCollectionEvent<K> keyEvent = new ObservableCollectionEvent<>(theEntry.theExposedId, keyIndex, //
					CollectionChangeType.add, null, key, causes);
				try (Transaction evtT = keyEvent.use()) {
					theKeySetListeners.forEach(//
						listener -> listener.accept(keyEvent));
				}
			}
			ListenerList<Consumer<? super ObservableCollectionEvent<? extends V>>> valueListeners = theValueListeners.get(key);
			if (valueListeners != null) {
				ObservableCollectionEvent<V> valueEvent = new ObservableCollectionEvent<>(addedId, valueIndex,
					CollectionChangeType.add, null, val, causes);
				try (Transaction evtT = valueEvent.use()) {
					valueListeners.forEach(//
						listener -> listener.accept(valueEvent));
				}
			}
			return added;
		}

		void valueUpdated(ElementId id, V oldValue, V newValue, Object... causes) {
			K key = theEntry.get();
			int valueIndex = theValues.getElementsBefore(id);
			ValueElementId valueId = new ValueElementId(theEntry, id, theValues.getElement(id).get());
			if (!theMapListeners.isEmpty()) {
				ObservableMultiMapEvent<K, V> event = new ObservableMultiMapEvent<>(//
					theEntry.theExposedId, valueId, theActiveEntries.getElementsBefore(theEntry.activeEntryId), valueIndex,
					CollectionChangeType.set, key, key, oldValue, newValue, causes);
				try (Transaction evtT = event.use()) {
					theMapListeners.forEach(//
						listener -> listener.accept(event));
				}
			}
			ListenerList<Consumer<? super ObservableCollectionEvent<? extends V>>> valueListeners = theValueListeners.get(key);
			if (valueListeners != null) {
				ObservableCollectionEvent<V> valueEvent = new ObservableCollectionEvent<>(valueId, valueIndex,
					CollectionChangeType.set, oldValue, newValue, causes);
				try (Transaction evtT = valueEvent.use()) {
					valueListeners.forEach(//
						listener -> listener.accept(valueEvent));
				}
			}
		}

		void valueRemoved(ElementId id, K key, V value, TypeToken<K> keyType, TypeToken<V> valueType, int keyIndex, Object... causes) {
			if (!id.isPresent())
				return; // This can happen when the last value for a key is removed
			theValueSize--;
			ValueElementId valueId = new ValueElementId(theEntry, id, theValues.getElement(id).get());
			int valueIndex = theValues.getElementsBefore(id);
			theValues.mutableElement(id).remove();
			if (keyIndex < 0)
				keyIndex = theActiveEntries.getElementsBefore(theEntry.activeEntryId);
			CollectionElementMove move = null;
			for (Object cause : causes) {
				if (cause instanceof ObservableCollectionEvent && ((ObservableCollectionEvent<?>) cause).getMovement() != null)
					move = ((ObservableCollectionEvent<?>) cause).getMovement();
			}
			if (move != null)
				causes = ArrayUtils.add(causes, move);
			if (!theMapListeners.isEmpty()) {
				ObservableMultiMapEvent<K, V> event = new ObservableMultiMapEvent<>(//
					theEntry.theExposedId, valueId, keyIndex, valueIndex, //
					CollectionChangeType.remove, key, key, value, value, causes);
				try (Transaction evtT = event.use()) {
					theMapListeners.forEach(//
						listener -> listener.accept(event));
				}
			}

			ListenerList<Consumer<? super ObservableCollectionEvent<? extends V>>> valueListeners = theValueListeners.get(key);
			if (valueListeners != null) {
				ObservableCollectionEvent<V> valueEvent = new ObservableCollectionEvent<>(valueId, valueIndex,
					CollectionChangeType.remove, value, value, causes);
				try (Transaction evtT = valueEvent.use()) {
					valueListeners.forEach(//
						listener -> listener.accept(valueEvent));
				}
			}

			if (theValues.isEmpty())
				theEntry.deactivate(key, keyIndex, causes);
		}

		@Override
		public Object createIdentity() {
			return Identifiable.wrap(DefaultActiveMultiMap.this.getIdentity(), "values", theEntry.get());
		}

		@Override
		public long getStamp() {
			return DefaultActiveMultiMap.this.getStamp();
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return getValueManager().getThreadConstraint();
		}

		@Override
		public boolean isLockSupported() {
			return getValueManager().isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return getValueManager().lock(write, cause);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return getValueManager().tryLock(write, cause);
		}

		@Override
		public CoreId getCoreId() {
			return getValueManager().getCoreId();
		}

		@Override
		public boolean belongs(Object o) {
			return TypeTokens.get().isInstance(getValueManager().getTargetType(), o);
		}

		@Override
		public boolean isContentControlled() {
			return getValueManager().isContentControlled();
		}

		@Override
		public int size() {
			return theValues.size();
		}

		@Override
		public boolean isEmpty() {
			return theValues.isEmpty();
		}

		@Override
		public CollectionElement<V> getTerminalElement(boolean first) {
			return elementFor(theValues.getTerminalElement(first));
		}

		@Override
		public int getElementsBefore(ElementId id) {
			return theValues.getElementsBefore(((ValueElementId) id).theValuesId);
		}

		@Override
		public int getElementsAfter(ElementId id) {
			return theValues.getElementsAfter(((ValueElementId) id).theValuesId);
		}

		@Override
		public CollectionElement<V> getElement(int index) throws IndexOutOfBoundsException {
			return elementFor(theValues.getElement(index));
		}

		@Override
		public CollectionElement<V> getElement(V value, boolean first) {
			Comparable<ObservableCollectionActiveManagers.DerivedCollectionElement<V>> finder = getValueManager().getElementFinder(value);
			if (finder != null) {
				CollectionElement<ValueRef> found = theValues.search(v -> finder.compareTo(v.theValueElement),
					BetterSortedSet.SortedSearchFilter.PreferLess);
				if (found != null && finder.compareTo(found.get().theValueElement) != 0)
					return null;
				return elementFor(found);
			} else {
				Equivalence<? super V> equivalence = getValueManager().equivalence();
				for (CollectionElement<ValueRef> val : (first ? theValues.elements() : theValues.elements().reverse()))
					if (equivalence.elementEquals(val.get().get(), value))
						return elementFor(val);
				return null;
			}
		}

		@Override
		public CollectionElement<V> getElement(ElementId id) {
			return new ValueElement(theEntry, theValues.getElement(strip(id).theValuesId));
		}

		ValueElementId strip(ElementId id) {
			if (id == null)
				return null;
			else if (!(id instanceof DefaultActiveMultiMap.ValueCollection.ValueElementId))
				throw new NoSuchElementException();
			ValueElementId vei = (ValueElementId) id;
			if (vei.getVC() != this)
				throw new NoSuchElementException();
			return vei;
		}

		@Override
		public CollectionElement<V> getAdjacentElement(ElementId elementId, boolean next) {
			return elementFor(theValues.getAdjacentElement(strip(elementId).theValuesId, next));
		}

		@Override
		public MutableCollectionElement<V> mutableElement(ElementId id) {
			return mutableElementFor(theValues.mutableElement(strip(id).theValuesId));
		}

		@Override
		public BetterList<CollectionElement<V>> getElementsBySource(ElementId sourceEl, BetterCollection<?> sourceCollection) {
			if (sourceCollection == this)
				return BetterList.of(getElement(sourceEl));
			return BetterList
				.of(getValueManager().getElementsBySource(strip(sourceEl).theValuesId, sourceCollection).stream().map(el -> elementFor(el))
					.filter(el -> el != null));
		}

		@Override
		public BetterList<ElementId> getSourceElements(ElementId localElement, BetterCollection<?> sourceCollection) {
			if (sourceCollection == this || sourceCollection == theValues)
				return BetterList.of(localElement);
			return getValueManager().getSourceElements(theValues.getElement(strip(localElement).theValuesId).get().theValueElement,
				sourceCollection);
		}

		@Override
		public ElementId getEquivalentElement(ElementId equivalentEl) {
			if (!(equivalentEl instanceof DefaultActiveMultiMap.ValueCollection.ValueElementId))
				return null;
			ValueElementId other = (ValueElementId) equivalentEl;
			if (other.getVC() == this)
				return other;
			if (!theKeyManager.equivalence().elementEquals(theEntry.get(), other.getVC().theEntry.get()))
				return null; // Different key collection
			DerivedCollectionElement<V> found = getValueManager().getEquivalentElement(other.theValueRef.theValueElement);
			return found == null ? null : CollectionElement.getElementId(elementFor(found));
		}

		ElementId getValueId(ElementId id) {
			ValueElementId valueId = strip(id);
			return valueId == null ? null : valueId.theValuesId;
		}

		@Override
		public String canAdd(V value, ElementId after, ElementId before) {
			ObservableCollectionActiveManagers.DerivedCollectionElement<V> afterEl = after == null ? null
				: theValues.getElement(getValueId(after)).get().theValueElement;
			ObservableCollectionActiveManagers.DerivedCollectionElement<V> beforeEl = before == null ? null
				: theValues.getElement(getValueId(before)).get().theValueElement;
			try (Transaction t = getAddKey().lock()) {
				getAddKey().accept(theEntry.getKey());
				return getValueManager().canAdd(value, afterEl, beforeEl);
			}
		}

		@Override
		public CollectionElement<V> addElement(V value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			ObservableCollectionActiveManagers.DerivedCollectionElement<V> afterEl = after == null ? null
				: theValues.getElement(getValueId(after)).get().theValueElement;
			ObservableCollectionActiveManagers.DerivedCollectionElement<V> beforeEl = before == null ? null
				: theValues.getElement(getValueId(before)).get().theValueElement;
			try (Transaction t = Lockable.lockAll(//
				Lockable.lockable(DefaultActiveMultiMap.this, true, null), getAddKey())) {
				getAddKey().accept(theEntry.getKey());
				return elementFor(getValueManager().addElement(value, afterEl, beforeEl, first));
			}
		}

		@Override
		public String canMove(ElementId valueEl, ElementId after, ElementId before) {
			ObservableCollectionActiveManagers.DerivedCollectionElement<V> valueEl2 = theValues.getElement(getValueId(valueEl))
				.get().theValueElement;
			ObservableCollectionActiveManagers.DerivedCollectionElement<V> afterEl = after == null ? null
				: theValues.getElement(getValueId(after)).get().theValueElement;
			ObservableCollectionActiveManagers.DerivedCollectionElement<V> beforeEl = before == null ? null
				: theValues.getElement(getValueId(before)).get().theValueElement;
			try (Transaction t = getAddKey().lock()) {
				getAddKey().accept(theEntry.getKey());
				return getValueManager().canMove(valueEl2, afterEl, beforeEl);
			}
		}

		@Override
		public CollectionElement<V> move(ElementId valueEl, ElementId after, ElementId before, boolean first, Runnable afterRemove)
			throws UnsupportedOperationException, IllegalArgumentException {
			ObservableCollectionActiveManagers.DerivedCollectionElement<V> valueEl2 = theValues.getElement(getValueId(valueEl))
				.get().theValueElement;
			ObservableCollectionActiveManagers.DerivedCollectionElement<V> afterEl = after == null ? null
				: theValues.getElement(getValueId(after)).get().theValueElement;
			ObservableCollectionActiveManagers.DerivedCollectionElement<V> beforeEl = before == null ? null
				: theValues.getElement(getValueId(before)).get().theValueElement;
			try (Transaction t = Lockable.lockAll(//
				Lockable.lockable(DefaultActiveMultiMap.this, true, null), getAddKey())) {
				getAddKey().accept(theEntry.getKey());
				return elementFor(getValueManager().move(valueEl2, afterEl, beforeEl, first, afterRemove));
			}
		}

		@Override
		public void clear() {
			try (Transaction t = DefaultActiveMultiMap.this.lock(true, null)) {
				for (ValueRef value : theValues.reverse()) {
					if (value.theValueElement.canRemove() == null)
						value.theValueElement.remove();
				}
			}
		}

		@Override
		public int hashCode() {
			return BetterCollection.hashCode(this);
		}

		@Override
		public boolean equals(Object obj) {
			return BetterCollection.equals(this, obj);
		}

		@Override
		public String toString() {
			return BetterCollection.toString(this);
		}

		CollectionElement<V> elementFor(ObservableCollectionActiveManagers.DerivedCollectionElement<V> el) {
			return elementFor(theValues.search(val -> el.compareTo(val.theValueElement), SortedSearchFilter.OnlyMatch));
		}

		CollectionElement<V> elementFor(CollectionElement<ValueRef> el) {
			return el == null ? null : new ValueElement(theEntry, el);
		}

		MutableCollectionElement<V> mutableElementFor(MutableCollectionElement<ValueRef> el) {
			return el == null ? null : new MutableValueElement(theEntry, el);
		}

		class ValueElementId implements ElementId {
			final KeyEntry theKey;
			final long theKeyStamp;
			final ElementId theValuesId;
			final ValueRef theValueRef;

			ValueElementId(KeyEntry key, ElementId valuesId, DefaultActiveMultiMap<S, K, V>.ValueRef valueRef) {
				theKey = key;
				theKeyStamp = key.keyStamp;
				theValuesId = valuesId;
				theValueRef = valueRef;
			}

			ValueCollection getVC() {
				return ValueCollection.this;
			}

			@Override
			public int compareTo(ElementId o) {
				return theValuesId.compareTo(((ValueElementId) o).theValuesId);
			}

			@Override
			public boolean isPresent() {
				return theKey.isPresent() && theKey.keyStamp == theKeyStamp && theValuesId.isPresent();
			}

			@Override
			public int hashCode() {
				return Objects.hash(theKey, theKeyStamp, theValuesId);
			}

			@Override
			public boolean equals(Object obj) {
				if(this==obj)
					return true;
				else if (!(obj instanceof DefaultActiveMultiMap.ValueCollection.ValueElementId))
					return false;
				DefaultActiveMultiMap<?, ?, ?>.ValueCollection.ValueElementId  other=(DefaultActiveMultiMap<?, ?, ?>.ValueCollection.ValueElementId) obj;
				return theKey.equals(other.theKey) && theKeyStamp == other.theKeyStamp && theValuesId.equals(other.theValuesId);
			}

			@Override
			public String toString() {
				return theEntry.get() + ": " + theValueRef.get();
			}
		}

		class ValueElement implements CollectionElement<V> {
			final ValueElementId theId;
			final CollectionElement<ValueRef> valueEl;

			ValueElement(KeyEntry key, CollectionElement<ValueRef> valueEl) {
				theId = new ValueElementId(key, valueEl.getElementId(), valueEl.get());
				this.valueEl = valueEl;
			}

			@Override
			public ElementId getElementId() {
				return theId;
			}

			@Override
			public V get() {
				return valueEl.get().get();
			}

			@Override
			public int hashCode() {
				return theId.hashCode();
			}

			@Override
			public boolean equals(Object obj) {
				return obj instanceof DefaultActiveMultiMap.ValueCollection.ValueElement && theId.equals(((ValueElement) obj).theId);
			}

			@Override
			public String toString() {
				return theId.toString();
			}
		}

		class MutableValueElement extends ValueElement implements MutableCollectionElement<V> {
			MutableValueElement(KeyEntry key, CollectionElement<ValueRef> valueEl) {
				super(key, valueEl);
			}

			@Override
			public BetterCollection<V> getCollection() {
				return ValueCollection.this;
			}

			@Override
			public String isEnabled() {
				return valueEl.get().theValueElement.isEnabled();
			}

			@Override
			public String isAcceptable(V value) {
				return valueEl.get().theValueElement.isAcceptable(value);
			}

			@Override
			public void set(V value) throws UnsupportedOperationException, IllegalArgumentException {
				valueEl.get().theValueElement.set(value);
			}

			@Override
			public String canRemove() {
				return valueEl.get().theValueElement.canRemove();
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				valueEl.get().theValueElement.remove();
			}
		}
	}

	/** Implements {@link DefaultActiveMultiMap#keySet()} */
	protected class KeySet extends AbstractIdentifiable implements ObservableSet<K> {
		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(DefaultActiveMultiMap.this.getIdentity(), "keySet");
		}

		@Override
		public TypeToken<K> getType() {
			return getKeyType();
		}

		@Override
		public Equivalence<? super K> equivalence() {
			return theKeyManager.equivalence();
		}

		@Override
		public boolean isContentControlled() {
			return theKeyManager.isContentControlled();
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return DefaultActiveMultiMap.this.getThreadConstraint();
		}

		@Override
		public boolean isEventing() {
			return DefaultActiveMultiMap.this.isEventing();
		}

		@Override
		public boolean isLockSupported() {
			return DefaultActiveMultiMap.this.isLockSupported();
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return DefaultActiveMultiMap.this.tryLock(write, cause);
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return DefaultActiveMultiMap.this.lock(write, cause);
		}

		@Override
		public CoreId getCoreId() {
			return DefaultActiveMultiMap.this.getCoreId();
		}

		@Override
		public long getStamp() {
			return DefaultActiveMultiMap.this.getStamp();
		}

		@Override
		public boolean isEmpty() {
			return theActiveEntries.isEmpty();
		}

		@Override
		public int size() {
			return theActiveEntries.size();
		}

		@Override
		public CollectionElement<K> getElement(ElementId id) {
			if (((KeyEntryId) id).getMap() != DefaultActiveMultiMap.this)
				throw new NoSuchElementException();
			return ((KeyEntryId) id).entry;
		}

		@Override
		public CollectionElement<K> getTerminalElement(boolean first) {
			return CollectionElement.get(theActiveEntries.getTerminalElement(first));
		}

		@Override
		public CollectionElement<K> getAdjacentElement(ElementId elementId, boolean next) {
			return CollectionElement.get(theActiveEntries.getAdjacentElement(((KeyEntryId) elementId).entry.activeEntryId, next));
		}

		@Override
		public MutableCollectionElement<K> mutableElement(ElementId id) {
			return theActiveEntries.getElement(((KeyEntryId) id).entry.activeEntryId).get().mutable();
		}

		@Override
		public CollectionElement<K> getElement(K value, boolean first) {
			Comparable<ObservableCollectionActiveManagers.DerivedCollectionElement<K>> finder = theKeyManager.getElementFinder(value);
			return theActiveEntries.searchValue(entry -> finder.compareTo(entry.theKeyElement), SortedSearchFilter.OnlyMatch);
		}

		@Override
		public BetterList<CollectionElement<K>> getElementsBySource(ElementId sourceEl, BetterCollection<?> sourceCollection) {
			if (sourceCollection == this)
				return BetterList.of(getElement(sourceEl));
			return BetterList
				.of(theKeyManager.getElementsBySource(sourceEl, sourceCollection).stream().map(this::elementFor).filter(el -> el != null));
		}

		@Override
		public BetterList<ElementId> getSourceElements(ElementId localElement, BetterCollection<?> sourceCollection) {
			if (sourceCollection == this)
				return BetterList.of(localElement);
			return theKeyManager.getSourceElements(((KeyEntryId) localElement).entry.theKeyElement, sourceCollection);
		}

		@Override
		public ElementId getEquivalentElement(ElementId equivalentEl) {
			if (!(equivalentEl instanceof DefaultActiveMultiMap.KeyEntryId))
				return null;
			if (((KeyEntryId) equivalentEl).getMap() == DefaultActiveMultiMap.this)
				return equivalentEl;
			DerivedCollectionElement<K> found = theKeyManager.getEquivalentElement(((KeyEntryId) equivalentEl).entry.theKeyElement);
			return found == null ? null : elementFor(found).getElementId();
		}

		@Override
		public String canAdd(K value, ElementId after, ElementId before) {
			if (getElement(value, true) != null)
				return StdMsg.ELEMENT_EXISTS;
			else
				return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public CollectionElement<K> addElement(K value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			if (getElement(value, first) != null)
				return null; // Signifying it wasn't added
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public String canMove(ElementId valueEl, ElementId after, ElementId before) {
			ObservableCollectionActiveManagers.DerivedCollectionElement<K> valueEl2 = ((KeyEntryId) valueEl).entry.theKeyElement;
			ObservableCollectionActiveManagers.DerivedCollectionElement<K> afterEl = after == null ? null
				: ((KeyEntryId) after).entry.theKeyElement;
			ObservableCollectionActiveManagers.DerivedCollectionElement<K> beforeEl = before == null ? null
				: ((KeyEntryId) before).entry.theKeyElement;
			return theKeyManager.canMove(valueEl2, afterEl, beforeEl);
		}

		@Override
		public CollectionElement<K> move(ElementId valueEl, ElementId after, ElementId before, boolean first, Runnable afterRemove)
			throws UnsupportedOperationException, IllegalArgumentException {
			ObservableCollectionActiveManagers.DerivedCollectionElement<K> valueEl2 = ((KeyEntryId) valueEl).entry.theKeyElement;
			ObservableCollectionActiveManagers.DerivedCollectionElement<K> afterEl = after == null ? null
				: ((KeyEntryId) after).entry.theKeyElement;
			ObservableCollectionActiveManagers.DerivedCollectionElement<K> beforeEl = before == null ? null
				: ((KeyEntryId) before).entry.theKeyElement;
			ObservableCollectionActiveManagers.DerivedCollectionElement<K> newKeyEl = theKeyManager.move(valueEl2, afterEl, beforeEl, first, afterRemove);
			return elementFor(newKeyEl);
		}

		@Override
		public void clear() {
			DefaultActiveMultiMap.this.clear();
		}

		@Override
		public void setValue(Collection<ElementId> elements, K value) {
			// This probably won't work, but whatever
			for (ElementId element : elements)
				((KeyEntryId) element).entry.theKeyElement.set(value);
		}

		@Override
		public CollectionElement<K> getElement(int index) throws IndexOutOfBoundsException {
			return theActiveEntries.getElement(index).get();
		}

		@Override
		public int getElementsBefore(ElementId id) {
			return theActiveEntries.getElementsBefore(((KeyEntryId) id).entry.activeEntryId);
		}

		@Override
		public int getElementsAfter(ElementId id) {
			return theActiveEntries.getElementsAfter(((KeyEntryId) id).entry.activeEntryId);
		}

		@Override
		public CollectionElement<K> getOrAdd(K value, ElementId after, ElementId before, boolean first, Runnable preAdd, Runnable postAdd) {
			return getElement(value, first); // Not possible to add to the key set because the values would be empty, which is not allowed
		}

		@Override
		public boolean isConsistent(ElementId element) {
			return theKeyManager.isConsistent(((KeyEntryId) element).entry.theKeyElement);
		}

		@Override
		public boolean checkConsistency() {
			return theKeyManager.checkConsistency();
		}

		@Override
		public <X> boolean repair(ElementId element, RepairListener<K, X> listener) {
			return theKeyManager.repair(((KeyEntryId) element).entry.theKeyElement, //
				listener == null ? null : new ObservableCollectionDataFlowImpl.RepairListener<K, X>() {
				@Override
				public X removed(ObservableCollectionActiveManagers.DerivedCollectionElement<K> el) {
					return listener.removed(elementFor(el));
				}

				@Override
				public void disposed(K value, X data) {
					listener.disposed(value, data);
				}

				@Override
				public void transferred(ObservableCollectionActiveManagers.DerivedCollectionElement<K> el, X data) {
					listener.transferred(elementFor(el), data);
				}
			});
		}

		@Override
		public <X> boolean repair(RepairListener<K, X> listener) {
			return theKeyManager.repair(listener == null ? null : new ObservableCollectionDataFlowImpl.RepairListener<K, X>() {
				@Override
				public X removed(ObservableCollectionActiveManagers.DerivedCollectionElement<K> el) {
					return listener.removed(elementFor(el));
				}

				@Override
				public void disposed(K value, X data) {
					listener.disposed(value, data);
				}

				@Override
				public void transferred(ObservableCollectionActiveManagers.DerivedCollectionElement<K> el, X data) {
					listener.transferred(elementFor(el), data);
				}
			});
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends K>> observer) {
			return theKeySetListeners.add(observer, true)::run;
		}

		@Override
		public int hashCode() {
			return BetterSet.hashCode(this);
		}

		@Override
		public boolean equals(Object obj) {
			return BetterSet.equals(this, obj);
		}

		@Override
		public String toString() {
			return BetterSet.toString(this);
		}

		CollectionElement<K> elementFor(ObservableCollectionActiveManagers.DerivedCollectionElement<K> el) {
			return theActiveEntries.searchValue(entry -> el.compareTo(entry.theKeyElement), SortedSearchFilter.OnlyMatch);
		}
	}

	/** Implements {@link DefaultActiveMultiMap#get(Object)} */
	protected class KeyValueCollection extends AbstractIdentifiable implements ObservableMultiEntry<K, V> {
		private final K theKey;
		private KeyEntry theCurrentEntry;

		KeyValueCollection(K key, KeyEntry currentEntry) {
			theKey = key;
			theCurrentEntry = currentEntry;
		}

		@Override
		public ElementId getKeyId() {
			return CollectionElement.getElementId(getCurrentEntry(false));
		}

		@Override
		public K getKey() {
			return theKey;
		}

		@Override
		public boolean isContentControlled() {
			return getValueManager().isContentControlled();
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(DefaultActiveMultiMap.this.getIdentity(), "values", theKey);
		}

		@Override
		public TypeToken<V> getType() {
			return getValueType();
		}

		@Override
		public Equivalence<? super V> equivalence() {
			return getValueManager().equivalence();
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return DefaultActiveMultiMap.this.getThreadConstraint();
		}

		@Override
		public boolean isEventing() {
			return DefaultActiveMultiMap.this.isEventing();
		}

		@Override
		public boolean isLockSupported() {
			return DefaultActiveMultiMap.this.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return DefaultActiveMultiMap.this.lock(write, cause);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return DefaultActiveMultiMap.this.tryLock(write, cause);
		}

		@Override
		public CoreId getCoreId() {
			return DefaultActiveMultiMap.this.getCoreId();
		}

		@Override
		public long getStamp() {
			return DefaultActiveMultiMap.this.getStamp();
		}

		KeyEntry getCurrentEntry(boolean throwIfEmpty) {
			if (theCurrentEntry == null || theCurrentEntry.activeEntryId == null || !theCurrentEntry.activeEntryId.isPresent()
				|| !getKeyManager().equivalence().elementEquals(theCurrentEntry.get(), theKey)) {
				Comparable<ObservableCollectionActiveManagers.DerivedCollectionElement<K>> finder = getKeyManager().getElementFinder(theKey);
				theCurrentEntry = theActiveEntries.searchValue(entry -> finder.compareTo(entry.theKeyElement),
					SortedSearchFilter.OnlyMatch);
			}
			if (theCurrentEntry != null)
				return theCurrentEntry;
			if (throwIfEmpty)
				throw new NoSuchElementException();
			return null;
		}

		@Override
		public int size() {
			try (Transaction t = lock(false, null)) {
				KeyEntry entry = getCurrentEntry(false);
				return entry == null ? 0 : entry.getValues().size();
			}
		}

		@Override
		public boolean isEmpty() {
			try (Transaction t = lock(false, null)) {
				return getCurrentEntry(false) == null;
			}
		}

		@Override
		public CollectionElement<V> getTerminalElement(boolean first) {
			try (Transaction t = lock(false, null)) {
				KeyEntry entry = getCurrentEntry(false);
				return entry == null ? null : entry.getValues().getTerminalElement(first);
			}
		}

		@Override
		public CollectionElement<V> getAdjacentElement(ElementId elementId, boolean next) {
			try (Transaction t = lock(false, null)) {
				KeyEntry entry = getCurrentEntry(true);
				return entry.getValues().getAdjacentElement(elementId, next);
			}
		}

		@Override
		public CollectionElement<V> getElement(V value, boolean first) {
			try (Transaction t = lock(false, null)) {
				KeyEntry entry = getCurrentEntry(false);
				return entry == null ? null : entry.getValues().getElement(value, first);
			}
		}

		@Override
		public CollectionElement<V> getElement(ElementId id) {
			try (Transaction t = lock(false, null)) {
				KeyEntry entry = getCurrentEntry(true);
				return entry.getValues().getElement(id);
			}
		}

		@Override
		public MutableCollectionElement<V> mutableElement(ElementId id) {
			try (Transaction t = lock(false, null)) {
				KeyEntry entry = getCurrentEntry(true);
				return entry.getValues().mutableElement(id);
			}
		}

		@Override
		public CollectionElement<V> getElement(int index) throws IndexOutOfBoundsException {
			try (Transaction t = lock(false, null)) {
				KeyEntry entry = getCurrentEntry(false);
				if (entry == null)
					throw new IndexOutOfBoundsException(index + " of 0");
				return entry.getValues().getElement(index);
			}
		}

		@Override
		public int getElementsBefore(ElementId id) {
			try (Transaction t = lock(false, null)) {
				KeyEntry entry = getCurrentEntry(true);
				return entry.getValues().getElementsBefore(id);
			}
		}

		@Override
		public int getElementsAfter(ElementId id) {
			try (Transaction t = lock(false, null)) {
				KeyEntry entry = getCurrentEntry(true);
				return entry.getValues().getElementsAfter(id);
			}
		}

		@Override
		public BetterList<CollectionElement<V>> getElementsBySource(ElementId sourceEl, BetterCollection<?> sourceCollection) {
			try (Transaction t = lock(false, null)) {
				if (sourceCollection == this)
					return BetterList.of(getElement(sourceEl));
				KeyEntry entry = getCurrentEntry(true);
				return entry.getValues().getElementsBySource(sourceEl, sourceCollection);
			}
		}

		@Override
		public BetterList<ElementId> getSourceElements(ElementId localElement, BetterCollection<?> sourceCollection) {
			if (sourceCollection == this)
				return BetterList.of(localElement);
			try (Transaction t = lock(false, null)) {
				KeyEntry entry = getCurrentEntry(true);
				return entry.getValues().getSourceElements(localElement, sourceCollection);
			}
		}

		@Override
		public ElementId getEquivalentElement(ElementId equivalentEl) {
			try (Transaction t = lock(false, null)) {
				KeyEntry entry = getCurrentEntry(true);
				return entry.getValues().getEquivalentElement(equivalentEl);
			}
		}

		@Override
		public String canAdd(V value, ElementId after, ElementId before) {
			try (Transaction t = Lockable.lockAll(//
				Lockable.lockable(this, false, null), getAddKey())) {
				getAddKey().accept(theKey);
				if (after != null || before != null) {
					KeyEntry entry = getCurrentEntry(true);
					return entry.getValues().canAdd(value, after, before);
				}
				return getValueManager().canAdd(value, null, null);
			} finally {
				getAddKey().clear();
			}
		}

		@Override
		public CollectionElement<V> addElement(V value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			try (Transaction t = Lockable.lockAll(//
				Lockable.lockable(this, true, null), getAddKey())) {
				getAddKey().accept(theKey);
				if (after != null || before != null) {
					KeyEntry entry = getCurrentEntry(true);
					return entry.getValues().addElement(value, after, before, first);
				}
				ObservableCollectionActiveManagers.DerivedCollectionElement<V> newElement = getValueManager().addElement(value, null, null, first);
				if (newElement == null)
					return null;
				KeyEntry entry = getCurrentEntry(false);
				if (entry == null)
					throw new IllegalStateException("Value added, but not to specified key");
				return entry.theValues.elementFor(newElement);
			} finally {
				getAddKey().clear();
			}
		}

		@Override
		public String canMove(ElementId valueEl, ElementId after, ElementId before) {
			return "Not Implemented"; // TODO
		}

		@Override
		public CollectionElement<V> move(ElementId valueEl, ElementId after, ElementId before, boolean first, Runnable afterRemove)
			throws UnsupportedOperationException, IllegalArgumentException {
			throw new UnsupportedOperationException("Not Implemented"); // TODO
		}

		@Override
		public void setValue(Collection<ElementId> elements, V value) {
			if (elements.isEmpty())
				return;
			try (Transaction t = lock(true, null)) {
				KeyEntry entry = getCurrentEntry(true);
				for (ElementId el : elements)
					entry.theValues.mutableElement(el).set(value);
			}
		}

		@Override
		public void clear() {
			try (Transaction t = lock(true, null)) {
				KeyEntry entry = getCurrentEntry(false);
				if (entry == null)
					return;
				entry.theValues.clear();
			}
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends V>> observer) {
			return getValueListeners(theKey).add(observer, true)::run;
		}

		@Override
		public int hashCode() {
			return BetterCollection.hashCode(this);
		}

		@Override
		public boolean equals(Object obj) {
			return BetterCollection.equals(this, obj);
		}

		@Override
		public String toString() {
			return BetterCollection.toString(this);
		}
	}
}
