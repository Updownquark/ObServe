package org.observe.assoc.impl;

import java.util.Collection;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.Observable;
import org.observe.Subscription;
import org.observe.assoc.ObservableMultiMap;
import org.observe.assoc.ObservableMultiMapEvent;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.Equivalence;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollection.DistinctDataFlow;
import org.observe.collect.ObservableCollectionActiveManagers;
import org.observe.collect.ObservableCollectionActiveManagers.ActiveCollectionManager;
import org.observe.collect.ObservableCollectionActiveManagers.ActiveSetManager;
import org.observe.collect.ObservableCollectionActiveManagers.CollectionElementListener;
import org.observe.collect.ObservableCollectionActiveManagers.DerivedCollectionElement;
import org.observe.collect.ObservableCollectionActiveManagers.ElementAccepter;
import org.observe.collect.ObservableCollectionDataFlowImpl;
import org.observe.collect.ObservableCollectionEvent;
import org.observe.collect.ObservableSet;
import org.observe.util.TypeTokens;
import org.observe.util.WeakListening;
import org.qommons.Causable;
import org.qommons.Identifiable;
import org.qommons.Lockable;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterMap;
import org.qommons.collect.BetterSet;
import org.qommons.collect.BetterSortedList.SortedSearchFilter;
import org.qommons.collect.BetterSortedMap;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.ListenerList;
import org.qommons.collect.MapEntryHandle;
import org.qommons.collect.MultiEntryHandle;
import org.qommons.collect.MutableCollectionElement;
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
	private final ActiveSetManager<S, ?, K> theKeyManager;
	private final ActiveCollectionManager<S, ?, V> theValueManager;

	private final WeakListening.Builder theWeakListening;

	private final BetterSortedSet<KeyEntry> theEntries;
	private final BetterSortedSet<KeyEntry> theActiveEntries;

	private final ObservableSet<K> theKeySet;

	private final BetterSortedMap<ElementId, GroupedElementInfo> theElementInfo;

	private long theStamp;
	private int theValueSize;

	private volatile boolean isNeedingNewKey;
	private volatile ElementId theNewKeyId;

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

		theEntries = new BetterTreeSet<>(false, KeyEntry::compareBySource);
		theActiveEntries = new BetterTreeSet<>(false, KeyEntry::compareInternal);
		theElementInfo = new BetterTreeMap<>(false, ElementId::compareTo);

		theMapListeners = ListenerList.build().withFastSize(false).build();
		theKeySetListeners = ListenerList.build().withFastSize(false).build();
		theValueListeners = keyFlow.equivalence().createMap();

		// Must maintain a strong reference to the event listening so it is not GC'd while the collection is still alive
		theWeakListening = WeakListening.build().withUntil(r -> until.act(v -> r.run()));
		WeakListening listening = theWeakListening.getListening();
		theKeyManager.begin(true, new ElementAccepter<K>() {
			@Override
			public void accept(ObservableCollectionActiveManagers.DerivedCollectionElement<K> element, Object cause) {
				KeyEntry entry = addKey(element, cause);
				if (isNeedingNewKey)
					theNewKeyId = entry.entryId;
				element.setListener(new CollectionElementListener<K>() {
					@Override
					public void update(K oldValue, K newValue, Object updateCause) {
						entry.updated(oldValue, newValue, updateCause);
					}

					@Override
					public void removed(K value, Object removeCause) {
						entry.removed(removeCause);
					}
				});
			}
		}, listening);
		theValueManager.begin(true, new ElementAccepter<V>() {
			@Override
			public void accept(ObservableCollectionActiveManagers.DerivedCollectionElement<V> element, Object cause) {
				ValueRef ref = addValue(element, cause);
				element.setListener(new CollectionElementListener<V>() {
					@Override
					public void update(V oldValue, V newValue, Object updateCause) {
						ref.updated(oldValue, newValue, updateCause);
					}

					@Override
					public void removed(V value, Object removeCause) {
						ref.removed(value, removeCause);
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

	GroupedElementInfo getSourceElementInfo(ElementId sourceElement) {
		MapEntryHandle<ElementId, GroupedElementInfo> srcEntry = theElementInfo.getOrPutEntry(sourceElement, GroupedElementInfo::new, null,
			null, false, null);
		GroupedElementInfo info = srcEntry.getValue();
		info.elementInfoId = srcEntry.getElementId();
		return info;
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
	protected ActiveSetManager<S, ?, K> getKeyManager() {
		return theKeyManager;
	}

	@Override
	protected ActiveCollectionManager<?, ?, V> getValueManager() {
		return theValueManager;
	}

	/** @return The set of all keys managed by this map, some of which may have no values and therefore may be absent from the key set */
	protected BetterSortedSet<KeyEntry> getAllEntries() {
		return theEntries;
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
		return theEntries.getElement(keyId).get();
	}

	@Override
	public MultiEntryHandle<K, V> getOrPutEntry(K key, Function<? super K, ? extends Iterable<? extends V>> value, ElementId afterKey,
		ElementId beforeKey, boolean first, Runnable added) {
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
			ObservableCollectionActiveManagers.DerivedCollectionElement<V> beforeEl = getValueElement(afterKey, true);
			isNeedingNewKey = true;
			ElementId newKeyId = null;
			getAddKey().accept(key);
			for (V v : value.apply(key)) {
				theValueManager.addElement(v, afterEl, beforeEl, first);
				if (newKeyId == null) {
					newKeyId = theNewKeyId;
					theNewKeyId = null;
				}
				first = true; // Add the next elements after
				isNeedingNewKey = false;
			}
			getAddKey().clear();
			return newKeyId == null ? null : getEntryById(newKeyId);
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
	protected void finalize() throws Throwable {
		super.finalize();
		theWeakListening.unsubscribe();
	}

	private ObservableCollectionActiveManagers.DerivedCollectionElement<V> getValueElement(ElementId keyId, boolean firstValue) {
		if (keyId == null)
			return null;
		KeyEntry entry = theEntries.getElement(keyId).get();
		return entry.theValues.theValues.getTerminalElement(firstValue).get().theValueElement;
	}

	// These are synchronized because even though the keys and values are from the same source,
	// other sources of change or updates may have been introduced to the key or value flows.
	// Without protection, one of these other sources could potentially fire during a source change,
	// causing simultaneous modification and corruption.

	private synchronized KeyEntry addKey(ObservableCollectionActiveManagers.DerivedCollectionElement<K> keyElement, Object cause) {
		KeyEntry entry = new KeyEntry(keyElement);
		entry.entryId = theEntries.addElement(entry, false).getElementId();
		for (ElementId sourceElement : theKeyManager.getSourceElements(keyElement, getSourceCollection())) {
			GroupedElementInfo info = getSourceElementInfo(sourceElement);
			entry.addSource(info, info.addKey(entry), cause);
		}
		return entry;
	}

	private synchronized ValueRef addValue(ObservableCollectionActiveManagers.DerivedCollectionElement<V> valueElement, Object cause) {
		ValueRef ref = new ValueRef(valueElement);
		for (ElementId sourceElement : theValueManager.getSourceElements(valueElement, getSourceCollection())) {
			GroupedElementInfo info = getSourceElementInfo(sourceElement);
			ref.addSource(info, info.addValue(ref), cause);
		}
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
			return entry.activeEntryId.compareTo(((KeyEntryId) o).entry.activeEntryId);
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
		final ValueCollection theValues;
		final BetterSortedMap<GroupedElementInfo, ElementId> theSources;
		final KeyEntryId theExposedId;

		ElementId entryId;
		ElementId activeEntryId;

		private MutableKeyEntry theMutableEntry;

		KeyEntry(ObservableCollectionActiveManagers.DerivedCollectionElement<K> keyElement) {
			theKeyElement = keyElement;
			theValues = new ValueCollection(this);
			theSources = new BetterTreeMap<>(false, GroupedElementInfo::compareTo);
			theExposedId = new KeyEntryId(this);
		}

		int compareInternal(KeyEntry other) {
			return entryId.compareTo(other.entryId);
		}

		int compareBySource(KeyEntry other) {
			return theKeyElement.compareTo(other.theKeyElement);
		}

		@Override
		public ElementId getElementId() {
			return theExposedId;
		}

		@Override
		public K getKey() {
			return theKeyElement.get();
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

		void deactivate(K key, int keyIndex, Object cause) {
			if (activeEntryId == null || !activeEntryId.isPresent())
				return; // Already removed
			theActiveEntries.mutableElement(activeEntryId).remove();
			if (!theKeySetListeners.isEmpty()) {
				ObservableCollectionEvent<K> keyEvent = new ObservableCollectionEvent<>(activeEntryId, getKeyType(),
					keyIndex, CollectionChangeType.remove, key, key, cause);
				try (Transaction evtT = Causable.use(keyEvent)) {
					theKeySetListeners.forEach(//
						listener -> listener.accept(keyEvent));
				}
			}
		}

		void updated(K oldKey, K newKey, Object cause) {
			synchronized (DefaultActiveMultiMap.this) {
				if (activeEntryId != null && activeEntryId.isPresent() && !theKeyManager.equivalence().elementEquals(newKey, oldKey)) {
					TypeToken<V> valueType = getValueType();
					ListenerList<Consumer<? super ObservableCollectionEvent<? extends V>>> listeners = theValueListeners.get(oldKey);
					if (listeners != null) {
						int index = theValues.theValues.size() - 1;
						for (CollectionElement<ValueRef> value : theValues.theValues.elements().reverse()) {
							V v = value.get().get();
							ObservableCollectionEvent<V> event = new ObservableCollectionEvent<>(value.getElementId(), valueType, index--, //
								CollectionChangeType.remove, v, v, cause);
							try (Transaction evtT = Causable.use(event)) {
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
							ObservableCollectionEvent<V> event = new ObservableCollectionEvent<>(value.getElementId(), valueType, index++, //
								CollectionChangeType.add, null, v, cause);
							try (Transaction evtT = Causable.use(event)) {
								listeners.forEach(//
									listener -> listener.accept(event));
							}
						}
					}
					if (!theKeySetListeners.isEmpty()) {
						ObservableCollectionEvent<K> keyEvent = new ObservableCollectionEvent<>(activeEntryId, getKeyType(), //
							theActiveEntries.getElementsBefore(activeEntryId), CollectionChangeType.set, oldKey, newKey, cause);
						try (Transaction evtT = Causable.use(keyEvent)) {
							theKeySetListeners.forEach(//
								listener -> listener.accept(keyEvent));
						}
					}
				}
			}
		}

		void removed(Object cause) {
			synchronized (DefaultActiveMultiMap.this) {
				if (activeEntryId != null && activeEntryId.isPresent()) {
					K key = get();
					int keyIndex = theActiveEntries.getElementsBefore(activeEntryId);
					theValues.entryRemoved(get(), keyIndex, cause);
					deactivate(key, keyIndex, cause);
				}
				for (Map.Entry<GroupedElementInfo, ElementId> source : theSources.entrySet())
					source.getKey().removeKey(source.getValue());
				theEntries.mutableElement(entryId).remove();
			}
		}

		void addSource(GroupedElementInfo sourceInfo, ElementId id, Object cause) {
			theSources.put(sourceInfo, id);
			theValues.addValues(sourceInfo.theValues, cause);
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
		final BetterSortedMap<KeyEntry, ElementId> theKeys;
		final BetterSortedMap<GroupedElementInfo, ElementId> theSources;

		ValueRef(ObservableCollectionActiveManagers.DerivedCollectionElement<V> valueElement) {
			theValueElement = valueElement;
			theKeys = new BetterTreeMap<>(false, (k1, k2) -> k1.entryId.compareTo(k2.entryId));
			theSources = new BetterTreeMap<>(false, GroupedElementInfo::compareTo);
		}

		BetterSortedSet<KeyEntry> adjustKeys(V oldValue, Object cause) {
			BetterSortedSet<KeyEntry> keys = new BetterTreeSet<>(false, KeyEntry::compareInternal);
			for (ElementId sourceElement : getValueManager().getSourceElements(theValueElement, getSourceCollection())) {
				GroupedElementInfo info = getSourceElementInfo(sourceElement);
				keys.addAll(info.theKeys);
			}
			// Remove this value in key entries that we don't belong to anymore
			MapEntryHandle<KeyEntry, ElementId> currentKey = theKeys.getTerminalEntry(true);
			TypeToken<K> keyType = getKeyType();
			TypeToken<V> valueType = getValueType();
			while (currentKey != null) {
				if (!keys.remove(currentKey.getKey())) {
					currentKey.getKey().theValues.valueRemoved(currentKey.getValue(), currentKey.getKey().get(), oldValue, keyType, //
						valueType, theActiveEntries.getElementsBefore(currentKey.getKey().activeEntryId), cause);
					theKeys.mutableEntry(currentKey.getElementId()).remove();
				}

				currentKey = theKeys.getAdjacentEntry(currentKey.getElementId(), true);
			}

			return keys;
		}

		void addToKeys(BetterSortedSet<KeyEntry> keys, V value, Object cause) {
			for (KeyEntry newKey : keys)
				theKeys.put(newKey, newKey.theValues.addValue(this, value, cause).getElementId());
		}

		@Override
		public int compareTo(ValueRef o) {
			return theValueElement.compareTo(o.theValueElement);
		}

		/** @return The value in this element */
		protected V get() {
			return theValueElement.get();
		}

		void updated(V oldValue, V newValue, Object cause) {
			synchronized (DefaultActiveMultiMap.this) {
				// Check source elements and adjust against keys
				BetterSortedSet<GroupedElementInfo> elements = new BetterTreeSet<>(false, GroupedElementInfo::compareTo);
				for (ElementId sourceElement : getValueManager().getSourceElements(theValueElement, getSourceCollection()))
					elements.add(getSourceElementInfo(sourceElement));
				MapEntryHandle<GroupedElementInfo, ElementId> source = theSources.getTerminalEntry(true);
				while (source != null) {
					if (!elements.remove(source.getKey())) {
						source.getKey().removeValue(source.getElementId());
						theSources.mutableEntry(source.getElementId()).remove();
					}
					source = theSources.getAdjacentEntry(source.getElementId(), true);
				}
				for (GroupedElementInfo info : elements)
					theSources.put(info, info.addValue(this));
				BetterSortedSet<KeyEntry> keys = new BetterTreeSet<>(false, KeyEntry::compareInternal);
				for (GroupedElementInfo info : theSources.keySet()) {
					info.checkKeys();
					keys.addAll(info.theKeys);
				}
				// Remove this value in key entries that we don't belong to anymore
				MapEntryHandle<KeyEntry, ElementId> currentKey = theKeys.getTerminalEntry(true);
				TypeToken<K> keyType = getKeyType();
				TypeToken<V> valueType = getValueType();
				while (currentKey != null) {
					if (!keys.remove(currentKey.getKey())) {
						currentKey.getKey().theValues.valueRemoved(currentKey.getValue(), currentKey.getKey().get(), oldValue, keyType, //
							valueType, theActiveEntries.getElementsBefore(currentKey.getKey().activeEntryId), cause);
						theKeys.mutableEntry(currentKey.getElementId()).remove();
					}

					currentKey = theKeys.getAdjacentEntry(currentKey.getElementId(), true);
				}

				for (Map.Entry<KeyEntry, ElementId> entry : theKeys.entrySet()) {
					if (entry.getKey().activeEntryId != null && entry.getKey().activeEntryId.isPresent())
						entry.getKey().theValues.valueUpdated(entry.getValue(), oldValue, newValue, cause);
				}

				// Add this value to keys that we didn't belong to before, but do now
				addToKeys(keys, newValue, cause);
			}
		}

		void removed(V value, Object cause) {
			synchronized (DefaultActiveMultiMap.this) {
				TypeToken<K> keyType = getKeyType();
				TypeToken<V> valueType = getValueType();
				CollectionElement<KeyEntry> keyEl = theKeys.keySet().getTerminalElement(true);
				while (keyEl != null) {
					MapEntryHandle<KeyEntry, ElementId> entry = theKeys.getEntryById(keyEl.getElementId());
					keyEl = theKeys.keySet().getAdjacentElement(keyEl.getElementId(), true);
					if (entry.getKey().activeEntryId != null && entry.getKey().activeEntryId.isPresent()) {
						entry.getKey().theValues.valueRemoved(entry.getValue(), entry.getKey().get(), value, keyType, valueType, //
							theActiveEntries.getElementsBefore(entry.getKey().activeEntryId), cause);
					}
					theKeys.mutableEntry(entry.getElementId()).remove();
				}
				for (Map.Entry<GroupedElementInfo, ElementId> entry : theSources.entrySet())
					entry.getKey().removeValue(entry.getValue());
			}
		}

		void addSource(GroupedElementInfo sourceInfo, ElementId id, Object cause) {
			theSources.put(sourceInfo, id);
			for (KeyEntry key : sourceInfo.theKeys)
				theKeys.computeIfAbsent(key, k -> k.theValues.addValue(this, get(), cause).getElementId());
		}

		@Override
		public String toString() {
			return String.valueOf(get());
		}
	}

	/** Holds all information related to a particular element from the source collection, including the keys and values representing it */
	protected class GroupedElementInfo implements Comparable<GroupedElementInfo> {
		final ElementId sourceElement;
		ElementId elementInfoId;
		final BetterSortedSet<KeyEntry> theKeys;
		final BetterSortedSet<ValueRef> theValues;

		GroupedElementInfo(ElementId sourceElement) {
			this.sourceElement = sourceElement;
			theKeys = new BetterTreeSet<>(false, KeyEntry::compareTo);
			theValues = new BetterTreeSet<>(false, ValueRef::compareTo);
		}

		@Override
		public int compareTo(GroupedElementInfo o) {
			return sourceElement.compareTo(o.sourceElement);
		}

		ElementId addKey(KeyEntry key) {
			return theKeys.addElement(key, false).getElementId();
		}

		void checkKeys() {
			BetterSortedSet<ObservableCollectionActiveManagers.DerivedCollectionElement<K>> keyElements = BetterTreeSet
				.<ObservableCollectionActiveManagers.DerivedCollectionElement<K>> buildTreeSet(ObservableCollectionActiveManagers.DerivedCollectionElement::compareTo)//
				.safe(false).build(getKeyManager().getElementsBySource(sourceElement, getSourceCollection()));
			for (CollectionElement<KeyEntry> key : theKeys.elements()) {
				CollectionElement<ObservableCollectionActiveManagers.DerivedCollectionElement<K>> keyEl = keyElements.search(key.get().theKeyElement,
					SortedSearchFilter.OnlyMatch);
				if (keyEl != null)
					keyElements.mutableElement(keyEl.getElementId()).remove(); // Accounted for
				else {
					key.get().theSources.remove(this);
					theKeys.mutableElement(key.getElementId()).remove();
				}
			}
			for (ObservableCollectionActiveManagers.DerivedCollectionElement<K> newKey : keyElements) {
				KeyEntry entry = theEntries.searchValue(k -> newKey.compareTo(k.theKeyElement), SortedSearchFilter.OnlyMatch);
				entry.theSources.put(this, addKey(entry));
			}
		}

		ElementId addValue(ValueRef value) {
			checkKeys();
			return theValues.addElement(value, false).getElementId();
		}

		void removeKey(ElementId keyId) {
			theKeys.mutableElement(keyId).remove();
			if (theValues.isEmpty() && theKeys.isEmpty() && elementInfoId.isPresent())
				theElementInfo.mutableEntry(elementInfoId).remove();
		}

		void removeValue(ElementId valueId) {
			theValues.mutableElement(valueId).remove();
			if (theValues.isEmpty() && (theKeys.isEmpty() || !sourceElement.isPresent()) && elementInfoId.isPresent()) {
				for (KeyEntry key : theKeys)
					key.theSources.remove(this);
				theElementInfo.mutableEntry(elementInfoId).remove();
			}
		}
	}

	/** Implements a collection (list) of values for a particular key in the map */
	protected class ValueCollection extends AbstractIdentifiable implements BetterList<V> {
		private final KeyEntry theEntry;
		final BetterSortedSet<ValueRef> theValues;

		ValueCollection(KeyEntry entry) {
			theEntry = entry;
			theValues = new BetterTreeSet<>(false, ValueRef::compareTo);
		}

		void addValues(Collection<ValueRef> values, Object cause) {
			if (values.isEmpty())
				return;
			TypeToken<K> keyType = getKeyType();
			TypeToken<V> valueType = getValueType();
			K key = theEntry.get();
			boolean newKey = theEntry.activate(key);
			int keyIndex = theActiveEntries.getElementsBefore(theEntry.activeEntryId);
			for (ValueRef value : values)
				addValue(value, keyType, valueType, keyIndex, key, value.get(), newKey, cause);
		}

		CollectionElement<ValueRef> addValue(ValueRef value, V val, Object cause) {
			K key = theEntry.get();
			boolean newKey = theEntry.activate(key);
			return addValue(value, getKeyType(), getValueType(), theActiveEntries.getElementsBefore(theEntry.activeEntryId), key, val,
				newKey, cause);
		}

		private CollectionElement<ValueRef> addValue(ValueRef value, TypeToken<K> keyType, TypeToken<V> valueType, int keyIndex, K key,
			V val, boolean newKey, Object cause) {
			CollectionElement<ValueRef> added = theValues.addElement(value, false);
			value.theKeys.put(theEntry, added.getElementId());
			if (keyIndex < 0)
				keyIndex = theActiveEntries.getElementsBefore(theEntry.activeEntryId);
			int valueIndex = theValues.getElementsBefore(added.getElementId());
			if (!theMapListeners.isEmpty()) {
				ObservableMultiMapEvent<K, V> mapEvent = new ObservableMultiMapEvent<>(theEntry.activeEntryId, added.getElementId(),
					keyType, valueType, keyIndex, valueIndex, //
					CollectionChangeType.add, key, null, val, cause);
				try (Transaction evtT = Causable.use(mapEvent)) {
					theMapListeners.forEach(//
						listener -> listener.accept(mapEvent));
				}
			}
			if (newKey) {
				ObservableCollectionEvent<K> keyEvent = new ObservableCollectionEvent<>(theEntry.activeEntryId, keyType, keyIndex, //
					CollectionChangeType.add, null, key, cause);
				try (Transaction evtT = Causable.use(keyEvent)) {
					theKeySetListeners.forEach(//
						listener -> listener.accept(keyEvent));
				}
			}
			ListenerList<Consumer<? super ObservableCollectionEvent<? extends V>>> valueListeners = theValueListeners.get(key);
			if (valueListeners != null) {
				ObservableCollectionEvent<V> valueEvent = new ObservableCollectionEvent<>(added.getElementId(), valueType, valueIndex,
					CollectionChangeType.add, null, val, cause);
				try (Transaction evtT = Causable.use(valueEvent)) {
					valueListeners.forEach(//
						listener -> listener.accept(valueEvent));
				}
			}
			return added;
		}

		void valueUpdated(ElementId id, V oldValue, V newValue, Object cause) {
			K key = theEntry.get();
			int valueIndex = theValues.getElementsBefore(id);
			TypeToken<V> valueType = getValueType();
			if (!theMapListeners.isEmpty()) {
				ObservableMultiMapEvent<K, V> event = new ObservableMultiMapEvent<>(//
					theEntry.activeEntryId, id, getKeyType(), valueType, theActiveEntries.getElementsBefore(theEntry.activeEntryId),
					valueIndex, CollectionChangeType.set, key, oldValue, newValue, cause);
				try (Transaction evtT = Causable.use(event)) {
					theMapListeners.forEach(//
						listener -> listener.accept(event));
				}
			}
			ListenerList<Consumer<? super ObservableCollectionEvent<? extends V>>> valueListeners = theValueListeners.get(key);
			if (valueListeners != null) {
				ObservableCollectionEvent<V> valueEvent = new ObservableCollectionEvent<>(id, valueType, valueIndex,
					CollectionChangeType.set, oldValue, newValue, cause);
				try (Transaction evtT = Causable.use(valueEvent)) {
					valueListeners.forEach(//
						listener -> listener.accept(valueEvent));
				}
			}
		}

		void valueRemoved(ElementId id, K key, V value, TypeToken<K> keyType, TypeToken<V> valueType, int keyIndex, Object cause) {
			if (!id.isPresent())
				return; // This can happen when the last value for a key is removed
			theValues.mutableElement(id).remove();
			int valueIndex = theValues.getElementsBefore(id);
			if (!theMapListeners.isEmpty()) {
				ObservableMultiMapEvent<K, V> event = new ObservableMultiMapEvent<>(//
					theEntry.activeEntryId, id, keyType, valueType, keyIndex, valueIndex, //
					CollectionChangeType.remove, key, value, value, cause);
				try (Transaction evtT = Causable.use(event)) {
					theMapListeners.forEach(//
						listener -> listener.accept(event));
				}
			}

			ListenerList<Consumer<? super ObservableCollectionEvent<? extends V>>> valueListeners = theValueListeners.get(key);
			if (valueListeners != null) {
				ObservableCollectionEvent<V> valueEvent = new ObservableCollectionEvent<>(id, getValueType(), valueIndex,
					CollectionChangeType.remove, value, value, cause);
				try (Transaction evtT = Causable.use(valueEvent)) {
					valueListeners.forEach(//
						listener -> listener.accept(valueEvent));
				}
			}

			if (theValues.isEmpty())
				theEntry.deactivate(key, keyIndex, cause);
		}

		void entryRemoved(K key, int keyIndex, Object cause) {
			TypeToken<K> keyType = getKeyType();
			TypeToken<V> valueType = getValueType();
			ListenerList<Consumer<? super ObservableCollectionEvent<? extends V>>> valueListeners = theValueListeners.get(key);
			if (!theMapListeners.isEmpty() || valueListeners != null) {
				int valueIndex = theValues.size() - 1;
				for (CollectionElement<ValueRef> valueEl : theValues.elements().reverse()) {
					V val = valueEl.get().get();
					if (!theMapListeners.isEmpty()) {
						ObservableMultiMapEvent<K, V> event = new ObservableMultiMapEvent<>(//
							theEntry.activeEntryId, valueEl.getElementId(), keyType, valueType, keyIndex, valueIndex, //
							CollectionChangeType.remove, key, val, val, cause);
						try (Transaction evtT = Causable.use(event)) {
							theMapListeners.forEach(//
								listener -> listener.accept(event));
						}
					}
					if (valueListeners != null) {
						ObservableCollectionEvent<V> valueEvent = new ObservableCollectionEvent<>(valueEl.getElementId(), valueType,
							valueIndex--, CollectionChangeType.remove, val, val, cause);
						try (Transaction evtT = Causable.use(valueEvent)) {
							valueListeners.forEach(//
								listener -> listener.accept(valueEvent));
						}
					}
					theValues.mutableElement(valueEl.getElementId()).remove();
				}
			}
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
			return theValues.getElementsBefore(((ValueElementId) id).valueEl.valueEl.getElementId());
		}

		@Override
		public int getElementsAfter(ElementId id) {
			return theValues.getElementsAfter(((ValueElementId) id).valueEl.valueEl.getElementId());
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
			ValueElementId vei = (DefaultActiveMultiMap<S, K, V>.ValueCollection.ValueElementId) id;
			if (vei.getVC() != this)
				throw new NoSuchElementException();
			return vei.valueEl;
		}

		@Override
		public CollectionElement<V> getAdjacentElement(ElementId elementId, boolean next) {
			return elementFor(theValues.getAdjacentElement(((ValueElementId) elementId).valueEl.valueEl.getElementId(), next));
		}

		@Override
		public MutableCollectionElement<V> mutableElement(ElementId id) {
			return mutableElementFor(theValues.mutableElement(((ValueElementId) id).valueEl.valueEl.getElementId()));
		}

		@Override
		public BetterList<CollectionElement<V>> getElementsBySource(ElementId sourceEl, BetterCollection<?> sourceCollection) {
			if (sourceCollection == this)
				return BetterList.of(getElement(sourceEl));
			return BetterList.of(getValueManager().getElementsBySource(sourceEl, sourceCollection).stream().map(el -> elementFor(el))
				.filter(el -> el != null));
		}

		@Override
		public BetterList<ElementId> getSourceElements(ElementId localElement, BetterCollection<?> sourceCollection) {
			if (sourceCollection == this || sourceCollection == theValues)
				return BetterList.of(localElement);
			return getValueManager().getSourceElements(theValues.getElement(localElement).get().theValueElement, sourceCollection);
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
			DerivedCollectionElement<V> found = getValueManager().getEquivalentElement(other.valueEl.valueEl.get().theValueElement);
			return found == null ? null : CollectionElement.getElementId(elementFor(found));
		}

		@Override
		public String canAdd(V value, ElementId after, ElementId before) {
			ObservableCollectionActiveManagers.DerivedCollectionElement<V> afterEl = after == null ? null : theValues.getElement(after).get().theValueElement;
			ObservableCollectionActiveManagers.DerivedCollectionElement<V> beforeEl = before == null ? null : theValues.getElement(before).get().theValueElement;
			try (Transaction t = getAddKey().lock()) {
				getAddKey().accept(theEntry.getKey());
				return getValueManager().canAdd(value, afterEl, beforeEl);
			}
		}

		@Override
		public CollectionElement<V> addElement(V value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			ObservableCollectionActiveManagers.DerivedCollectionElement<V> afterEl = after == null ? null : theValues.getElement(after).get().theValueElement;
			ObservableCollectionActiveManagers.DerivedCollectionElement<V> beforeEl = before == null ? null : theValues.getElement(before).get().theValueElement;
			try (Transaction t = Lockable.lockAll(//
				Lockable.lockable(DefaultActiveMultiMap.this, true, null), getAddKey())) {
				getAddKey().accept(theEntry.getKey());
				return elementFor(getValueManager().addElement(value, afterEl, beforeEl, first));
			}
		}

		@Override
		public String canMove(ElementId valueEl, ElementId after, ElementId before) {
			ObservableCollectionActiveManagers.DerivedCollectionElement<V> valueEl2 = theValues.getElement(valueEl).get().theValueElement;
			ObservableCollectionActiveManagers.DerivedCollectionElement<V> afterEl = after == null ? null : theValues.getElement(after).get().theValueElement;
			ObservableCollectionActiveManagers.DerivedCollectionElement<V> beforeEl = before == null ? null : theValues.getElement(before).get().theValueElement;
			try (Transaction t = getAddKey().lock()) {
				getAddKey().accept(theEntry.getKey());
				return getValueManager().canMove(valueEl2, afterEl, beforeEl);
			}
		}

		@Override
		public CollectionElement<V> move(ElementId valueEl, ElementId after, ElementId before, boolean first, Runnable afterRemove)
			throws UnsupportedOperationException, IllegalArgumentException {
			ObservableCollectionActiveManagers.DerivedCollectionElement<V> valueEl2 = theValues.getElement(valueEl).get().theValueElement;
			ObservableCollectionActiveManagers.DerivedCollectionElement<V> afterEl = after == null ? null : theValues.getElement(after).get().theValueElement;
			ObservableCollectionActiveManagers.DerivedCollectionElement<V> beforeEl = before == null ? null : theValues.getElement(before).get().theValueElement;
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
			return el == null ? null : new ValueElement(el);
		}

		MutableCollectionElement<V> mutableElementFor(MutableCollectionElement<ValueRef> el) {
			return el == null ? null : new MutableValueElement(el);
		}

		class ValueElementId implements ElementId {
			final ValueElement valueEl;

			ValueElementId(DefaultActiveMultiMap<S, K, V>.ValueCollection.ValueElement valueEl) {
				this.valueEl = valueEl;
			}

			ValueCollection getVC() {
				return ValueCollection.this;
			}

			@Override
			public int compareTo(ElementId o) {
				return valueEl.valueEl.getElementId().compareTo(((ValueElementId) o).valueEl.getElementId());
			}

			@Override
			public boolean isPresent() {
				return valueEl.valueEl.getElementId().isPresent();
			}

			@Override
			public int hashCode() {
				return valueEl.valueEl.getElementId().hashCode();
			}

			@Override
			public boolean equals(Object obj) {
				return this == obj;
			}

			@Override
			public String toString() {
				return theEntry.get() + ": " + valueEl.valueEl.get().get();
			}
		}

		class ValueElement implements CollectionElement<V> {
			final ValueElementId theId;
			final CollectionElement<ValueRef> valueEl;

			ValueElement(CollectionElement<ValueRef> valueEl) {
				theId = new ValueElementId(this);
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
		}

		class MutableValueElement extends ValueElement implements MutableCollectionElement<V> {
			MutableValueElement(CollectionElement<ValueRef> valueEl) {
				super(valueEl);
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
			throw new IllegalArgumentException(StdMsg.UNSUPPORTED_OPERATION);
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
		public CollectionElement<K> getOrAdd(K value, ElementId after, ElementId before, boolean first, Runnable added) {
			return getElement(value, first); // Not possible to add to the key set because the values would be empty, which is not allowed
			// Should we throw an exception if it doesn't exist?
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
		public long getStamp() {
			return DefaultActiveMultiMap.this.getStamp();
		}

		KeyEntry getCurrentEntry(boolean throwIfEmpty) {
			if (theCurrentEntry == null || !theCurrentEntry.entryId.isPresent()) {
				Comparable<ObservableCollectionActiveManagers.DerivedCollectionElement<K>> finder = getKeyManager().getElementFinder(theKey);
				theCurrentEntry = getAllEntries().searchValue(entry -> finder.compareTo(entry.theKeyElement), SortedSearchFilter.OnlyMatch);
			}
			if (theCurrentEntry != null && theCurrentEntry.activeEntryId != null && theCurrentEntry.activeEntryId.isPresent())
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
