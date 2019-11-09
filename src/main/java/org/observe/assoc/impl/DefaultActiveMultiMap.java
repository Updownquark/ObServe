package org.observe.assoc.impl;

import java.util.Collection;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
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
import org.observe.collect.ObservableCollectionDataFlowImpl.ActiveCollectionManager;
import org.observe.collect.ObservableCollectionDataFlowImpl.ActiveSetManager;
import org.observe.collect.ObservableCollectionDataFlowImpl.CollectionElementListener;
import org.observe.collect.ObservableCollectionDataFlowImpl.CollectionOperation;
import org.observe.collect.ObservableCollectionDataFlowImpl.DerivedCollectionElement;
import org.observe.collect.ObservableCollectionDataFlowImpl.ElementAccepter;
import org.observe.collect.ObservableCollectionEvent;
import org.observe.collect.ObservableSet;
import org.observe.util.TypeTokens;
import org.observe.util.WeakListening;
import org.qommons.Causable;
import org.qommons.Identifiable;
import org.qommons.Lockable;
import org.qommons.QommonsUtils;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterSortedList.SortedSearchFilter;
import org.qommons.collect.BetterSortedMap;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.ListenerList;
import org.qommons.collect.MapEntryHandle;
import org.qommons.collect.MultiEntryHandle;
import org.qommons.collect.MutableCollectionElement;
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

	private final KeySet theKeySet;

	private final BetterSortedMap<ElementId, GroupedElementInfo> theElementInfo;

	private long theStamp;
	private int theValueSize;

	private volatile boolean isNeedingNewKey;
	private volatile ElementId theNewKeyId;

	private final ListenerList<Consumer<? super ObservableMultiMapEvent<? extends K, ? extends V>>> theMapListeners;
	private final ListenerList<Consumer<? super ObservableCollectionEvent<? extends K>>> theKeySetListeners;
	private final ConcurrentHashMap<K, ListenerList<Consumer<? super ObservableCollectionEvent<? extends V>>>> theValueListeners;

	public DefaultActiveMultiMap(ObservableCollection<S> source, DistinctDataFlow<S, ?, K> keyFlow, CollectionDataFlow<S, ?, V> valueFlow,
		Observable<?> until, AddKeyHolder<K> addKey) {
		super(source, keyFlow, valueFlow, addKey);
		theKeyManager = keyFlow.manageActive();
		theValueManager = valueFlow.manageActive();

		theEntries = new BetterTreeSet<>(false, KeyEntry::compareInternal);
		theActiveEntries = new BetterTreeSet<>(false, KeyEntry::compareTo);
		theElementInfo = new BetterTreeMap<>(false, ElementId::compareTo);

		theMapListeners = ListenerList.build().withFastSize(false).build();
		theKeySetListeners = ListenerList.build().withFastSize(false).build();
		theValueListeners = new ConcurrentHashMap<>();

		// Must maintain a strong reference to the event listening so it is not GC'd while the collection is still alive
		theWeakListening = WeakListening.build().withUntil(r -> until.act(v -> r.run()));
		WeakListening listening = theWeakListening.getListening();
		theKeyManager.begin(true, new ElementAccepter<K>() {
			@Override
			public void accept(DerivedCollectionElement<K> element, Object cause) {
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
			public void accept(DerivedCollectionElement<V> element, Object cause) {
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

		theKeySet = new KeySet();
	}

	private ListenerList<Consumer<? super ObservableCollectionEvent<? extends V>>> getValueListeners(K key) {
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

	@Override
	protected Transactable getKeyLocker() {
		return theKeyManager;
	}

	@Override
	protected CollectionOperation<?, ?, V> getValueManager() {
		return theValueManager;
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
			DerivedCollectionElement<V> afterEl = getValueElement(afterKey, false);
			DerivedCollectionElement<V> beforeEl = getValueElement(afterKey, true);
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
	public ObservableCollection<V> get(Object key) {
		if (!keySet().belongs(key))
			return ObservableCollection.of(getValueType());
		return new KeyValueCollection((K) key);
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

	private DerivedCollectionElement<V> getValueElement(ElementId keyId, boolean firstValue) {
		if (keyId == null)
			return null;
		KeyEntry entry = theEntries.getElement(keyId).get();
		return entry.theValues.theValues.getTerminalElement(firstValue).get().theValueElement;
	}

	// These are synchronized because even though the keys and values are from the same source,
	// other sources of change or updates may have been introduced to the key or value flows.
	// Without protection, one of these other sources could potentially fire during a source change,
	// causing simultaneous modification and corruption.

	private synchronized KeyEntry addKey(DerivedCollectionElement<K> keyElement, Object cause) {
		KeyEntry entry = new KeyEntry(keyElement);
		entry.entryId = theEntries.addElement(entry, false).getElementId();
		for (ElementId sourceElement : theKeyManager.getSourceElements(keyElement, getSourceCollection())) {
			GroupedElementInfo info = getSourceElementInfo(sourceElement);
			entry.addSource(info, info.addKey(entry), cause);
		}
		return entry;
	}

	private synchronized ValueRef addValue(DerivedCollectionElement<V> valueElement, Object cause) {
		ValueRef ref = new ValueRef(valueElement);
		for (ElementId sourceElement : theValueManager.getSourceElements(valueElement, getSourceCollection())) {
			GroupedElementInfo info = getSourceElementInfo(sourceElement);
			ref.addSource(info, info.addValue(ref), cause);
		}
		return ref;
	}

	class KeyEntry implements MultiEntryHandle<K, V> {
		final DerivedCollectionElement<K> theKeyElement;
		final ValueCollection theValues;
		final BetterSortedMap<GroupedElementInfo, ElementId> theSources;

		ElementId entryId;
		ElementId activeEntryId;

		KeyEntry(DerivedCollectionElement<K> keyElement) {
			theKeyElement = keyElement;
			theValues = new ValueCollection(this);
			theSources = new BetterTreeMap<>(false, GroupedElementInfo::compareTo);
		}

		int compareInternal(KeyEntry other) {
			return entryId.compareTo(other.entryId);
		}

		@Override
		public ElementId getElementId() {
			return activeEntryId;
		}

		@Override
		public K getKey() {
			return theKeyElement.get();
		}

		@Override
		public BetterList<V> getValues() {
			return theValues;
		}

		boolean activate(K key) {
			if (activeEntryId != null && activeEntryId.isPresent())
				return false;
			activeEntryId = theActiveEntries.addElement(this, false).getElementId();
			return true;
		}

		void deactivate(K key, Object cause) {
			theActiveEntries.mutableElement(activeEntryId).remove();
			if (!theKeySetListeners.isEmpty()) {
				ObservableCollectionEvent<K> keyEvent = new ObservableCollectionEvent<>(activeEntryId, getKeyType(),
					theActiveEntries.getElementsBefore(activeEntryId), CollectionChangeType.remove, key, key, cause);
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
							V v = value.get().theValueElement.get();
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
							V v = value.get().theValueElement.get();
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
				int keyIndex = -1;
				if (activeEntryId != null && activeEntryId.isPresent())
					keyIndex = theActiveEntries.getElementsBefore(activeEntryId);
				for (Map.Entry<GroupedElementInfo, ElementId> source : theSources.entrySet())
					source.getKey().removeKey(source.getValue());
				if (keyIndex >= 0) {
					theValues.entryRemoved(get(), keyIndex, cause);
					theActiveEntries.mutableElement(activeEntryId).remove();
				}
				theEntries.mutableElement(entryId).remove();
			}
		}

		void addSource(GroupedElementInfo sourceInfo, ElementId id, Object cause) {
			theSources.put(sourceInfo, id);
			theValues.addValues(sourceInfo.theValues, cause);
		}
	}

	class ValueRef implements Comparable<ValueRef> {
		final DerivedCollectionElement<V> theValueElement;
		final BetterSortedMap<KeyEntry, ElementId> theKeys;
		final BetterSortedMap<GroupedElementInfo, ElementId> theSources;

		ValueRef(DerivedCollectionElement<V> valueElement) {
			theValueElement = valueElement;
			theKeys = new BetterTreeMap<>(false, (k1, k2) -> k1.entryId.compareTo(k2.entryId));
			theSources = new BetterTreeMap<>(false, GroupedElementInfo::compareTo);
		}

		@Override
		public int compareTo(ValueRef o) {
			return theValueElement.compareTo(o.theValueElement);
		}

		void updated(V oldValue, V newValue, Object cause) {
			synchronized (DefaultActiveMultiMap.this) {
				// Check source elements and adjust against keys
				BetterSortedSet<KeyEntry> keys = new BetterTreeSet<>(false, KeyEntry::compareInternal);
				for (ElementId sourceElement : theValueManager.getSourceElements(theValueElement, getSourceCollection())) {
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

				for (Map.Entry<KeyEntry, ElementId> entry : theKeys.entrySet()) {
					if (entry.getKey().activeEntryId != null && entry.getKey().activeEntryId.isPresent()) {
						ObservableMultiMapEvent<K, V> event = new ObservableMultiMapEvent<>(//
							entry.getKey().activeEntryId, entry.getValue(), keyType, valueType, //
							theActiveEntries.getElementsBefore(entry.getKey().activeEntryId), //
							entry.getKey().theValues.theValues.getElementsBefore(entry.getValue()), //
							CollectionChangeType.set, entry.getKey().get(), oldValue, newValue, cause);
						try (Transaction evtT = Causable.use(event)) {
							theMapListeners.forEach(//
								listener -> listener.accept(event));
						}
						entry.getKey().theValues.valueUpdated(entry.getValue(), oldValue, newValue, cause);
					}
				}

				// Add this value to keys that we didn't belong to before, but do now
				for (KeyEntry newKey : keys)
					theKeys.put(newKey, newKey.theValues.addValue(this, cause).getElementId());
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
					entry.getKey().theValues.valueRemoved(entry.getValue(), entry.getKey().get(), value, keyType, valueType, //
						theActiveEntries.getElementsBefore(entry.getKey().activeEntryId), cause);
					theKeys.mutableEntry(entry.getElementId()).remove();
				}
				for (Map.Entry<GroupedElementInfo, ElementId> entry : theSources.entrySet())
					entry.getKey().removeValue(entry.getValue());
			}
		}

		void addSource(GroupedElementInfo sourceInfo, ElementId id, Object cause) {
			theSources.put(sourceInfo, id);
			for (KeyEntry key : sourceInfo.theKeys)
				theKeys.computeIfAbsent(key, k -> k.theValues.addValue(this, cause).getElementId());
		}
	}

	class GroupedElementInfo implements Comparable<GroupedElementInfo> {
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
		public int compareTo(DefaultActiveMultiMap<S, K, V>.GroupedElementInfo o) {
			return sourceElement.compareTo(o.sourceElement);
		}

		ElementId addKey(KeyEntry key) {
			return theKeys.addElement(key, false).getElementId();
		}

		ElementId addValue(ValueRef value) {
			return theValues.addElement(value, false).getElementId();
		}

		void removeKey(ElementId keyId) {
			theKeys.mutableElement(keyId).remove();
			if (theKeys.isEmpty() && theValues.isEmpty())
				theElementInfo.mutableEntry(elementInfoId).remove();
		}

		void removeValue(ElementId valueId) {
			theValues.mutableElement(valueId).remove();
			if (theKeys.isEmpty() && theValues.isEmpty())
				theElementInfo.mutableEntry(elementInfoId).remove();
		}
	}

	class ValueCollection extends AbstractIdentifiable implements BetterList<V> {
		private final KeyEntry theEntry;
		final BetterSortedSet<ValueRef> theValues;

		public ValueCollection(KeyEntry entry) {
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
				addValue(value, keyType, valueType, keyIndex, key, newKey, cause);
		}

		CollectionElement<ValueRef> addValue(ValueRef value, Object cause) {
			K key = theEntry.get();
			boolean newKey = theEntry.activate(key);
			return addValue(value, getKeyType(), getValueType(), theActiveEntries.getElementsBefore(theEntry.activeEntryId), key, newKey,
				cause);
		}

		private CollectionElement<ValueRef> addValue(ValueRef value, TypeToken<K> keyType, TypeToken<V> valueType, int keyIndex, K key,
			boolean newKey, Object cause) {
			CollectionElement<ValueRef> added = theValues.addElement(value, false);
			value.theKeys.put(theEntry, added.getElementId());
			if (keyIndex < 0)
				keyIndex = theActiveEntries.getElementsBefore(theEntry.activeEntryId);
			int valueIndex = theValues.getElementsBefore(added.getElementId());
			V val = value.theValueElement.get();
			ObservableMultiMapEvent<K, V> mapEvent = new ObservableMultiMapEvent<>(theEntry.activeEntryId, added.getElementId(), keyType,
				valueType, keyIndex, valueIndex, //
				CollectionChangeType.add, key, null, val, cause);
			try (Transaction evtT = Causable.use(mapEvent)) {
				theMapListeners.forEach(//
					listener -> listener.accept(mapEvent));
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
				theEntry.deactivate(key, cause);
		}

		void entryRemoved(K key, int keyIndex, Object cause) {
			TypeToken<K> keyType = getKeyType();
			TypeToken<V> valueType = getValueType();
			ListenerList<Consumer<? super ObservableCollectionEvent<? extends V>>> valueListeners = theValueListeners.get(key);
			if (!theMapListeners.isEmpty() || valueListeners != null) {
				int valueIndex = theValues.size() - 1;
				for (CollectionElement<ValueRef> valueEl : theValues.elements().reverse()) {
					V val = valueEl.get().theValueElement.get();
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
			return theValueManager.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theValueManager.lock(write, cause);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return theValueManager.tryLock(write, cause);
		}

		@Override
		public boolean belongs(Object o) {
			return TypeTokens.get().isInstance(theValueManager.getTargetType(), o);
		}

		@Override
		public boolean isContentControlled() {
			return theValueManager.isContentControlled();
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
			return theValues.getElementsBefore(id);
		}

		@Override
		public int getElementsAfter(ElementId id) {
			return theValues.getElementsAfter(id);
		}

		@Override
		public CollectionElement<V> getElement(int index) throws IndexOutOfBoundsException {
			return elementFor(theValues.getElement(index));
		}

		@Override
		public CollectionElement<V> getElement(V value, boolean first) {
			Comparable<DerivedCollectionElement<V>> finder = theValueManager.getElementFinder(value);
			CollectionElement<ValueRef> found = theValues.search(v -> finder.compareTo(v.theValueElement),
				BetterSortedSet.SortedSearchFilter.PreferLess);
			if (found != null && finder.compareTo(found.get().theValueElement) != 0)
				return null;
			return elementFor(found);
		}

		@Override
		public CollectionElement<V> getElement(ElementId id) {
			return elementFor(theValues.getElement(id));
		}

		@Override
		public CollectionElement<V> getAdjacentElement(ElementId elementId, boolean next) {
			return elementFor(theValues.getAdjacentElement(elementId, next));
		}

		@Override
		public MutableCollectionElement<V> mutableElement(ElementId id) {
			return mutableElementFor(theValues.mutableElement(id));
		}

		@Override
		public BetterList<CollectionElement<V>> getElementsBySource(ElementId sourceEl) {
			BetterList<CollectionElement<ValueRef>> els = theValues.getElementsBySource(sourceEl);
			if (!els.isEmpty())
				return QommonsUtils.map2(els, this::elementFor);
			return BetterList.of(theValueManager.getElementsBySource(sourceEl).stream().map(el -> elementFor(el)).filter(el -> el != null));
		}

		@Override
		public BetterList<ElementId> getSourceElements(ElementId localElement, BetterCollection<?> sourceCollection) {
			if (sourceCollection == this || sourceCollection == theValues)
				return BetterList.of(localElement);
			return theValueManager.getSourceElements(theValues.getElement(localElement).get().theValueElement, sourceCollection);
		}

		@Override
		public String canAdd(V value, ElementId after, ElementId before) {
			DerivedCollectionElement<V> afterEl = after == null ? null : theValues.getElement(after).get().theValueElement;
			DerivedCollectionElement<V> beforeEl = before == null ? null : theValues.getElement(before).get().theValueElement;
			try (Transaction t = getAddKey().lock()) {
				getAddKey().accept(theEntry.getKey());
				return theValueManager.canAdd(value, afterEl, beforeEl);
			}
		}

		@Override
		public CollectionElement<V> addElement(V value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			DerivedCollectionElement<V> afterEl = after == null ? null : theValues.getElement(after).get().theValueElement;
			DerivedCollectionElement<V> beforeEl = before == null ? null : theValues.getElement(before).get().theValueElement;
			try (Transaction t = Lockable.lockAll(//
				Lockable.lockable(DefaultActiveMultiMap.this, true, null), getAddKey())) {
				getAddKey().accept(theEntry.getKey());
				return elementFor(theValueManager.addElement(value, afterEl, beforeEl, first));
			}
		}

		@Override
		public void clear() {
			try (Transaction t = DefaultActiveMultiMap.this.lock(true, null)) {
				for (ValueRef value : theValues) {
					if (value.theValueElement.canRemove() == null)
						value.theValueElement.remove();
				}
			}
		}

		CollectionElement<V> elementFor(DerivedCollectionElement<V> el) {}

		CollectionElement<V> elementFor(CollectionElement<ValueRef> el) {}

		MutableCollectionElement<V> mutableElementFor(MutableCollectionElement<ValueRef> el) {}
	}

	class KeySet extends AbstractIdentifiable implements ObservableSet<K> {
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
			// TODO Auto-generated method stub
		}

		@Override
		public CollectionElement<K> getTerminalElement(boolean first) {
			// TODO Auto-generated method stub
		}

		@Override
		public CollectionElement<K> getAdjacentElement(ElementId elementId, boolean next) {
			// TODO Auto-generated method stub
		}

		@Override
		public MutableCollectionElement<K> mutableElement(ElementId id) {
			// TODO Auto-generated method stub
		}

		@Override
		public CollectionElement<K> getElement(K value, boolean first) {
			// TODO Auto-generated method stub
		}

		@Override
		public BetterList<CollectionElement<K>> getElementsBySource(ElementId sourceEl) {
			// TODO Auto-generated method stub
		}

		@Override
		public BetterList<ElementId> getSourceElements(ElementId localElement, BetterCollection<?> sourceCollection) {
			// TODO Auto-generated method stub
		}

		@Override
		public String canAdd(K value, ElementId after, ElementId before) {
			// TODO Auto-generated method stub
		}

		@Override
		public CollectionElement<K> addElement(K value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			// TODO Auto-generated method stub
		}

		@Override
		public void clear() {
			DefaultActiveMultiMap.this.clear();
		}

		@Override
		public void setValue(Collection<ElementId> elements, K value) {
			// TODO Auto-generated method stub

		}

		@Override
		public CollectionElement<K> getElement(int index) throws IndexOutOfBoundsException {
			// TODO Auto-generated method stub
		}

		@Override
		public int getElementsBefore(ElementId id) {
			// TODO Auto-generated method stub
		}

		@Override
		public int getElementsAfter(ElementId id) {
			// TODO Auto-generated method stub
		}

		@Override
		public CollectionElement<K> getOrAdd(K value, ElementId after, ElementId before, boolean first, Runnable added) {
			// TODO Auto-generated method stub
		}

		@Override
		public boolean isConsistent(ElementId element) {
			// TODO Auto-generated method stub
		}

		@Override
		public boolean checkConsistency() {
			return theKeyManager.checkConsistency();
		}

		@Override
		public <X> boolean repair(ElementId element, RepairListener<K, X> listener) {
			// TODO Auto-generated method stub
		}

		@Override
		public <X> boolean repair(RepairListener<K, X> listener) {
			// TODO Auto-generated method stub
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends K>> observer) {
			return theKeySetListeners.add(observer, true)::run;
		}
	}

	class KeyValueCollection extends AbstractIdentifiable implements ObservableCollection<V> {
		private final K theKey;
		private KeyEntry theCurrentEntry;

		KeyValueCollection(K key) {
			theKey = key;
		}

		@Override
		public boolean isContentControlled() {
			return theValueManager.isContentControlled();
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
			return theValueManager.equivalence();
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
				Comparable<DerivedCollectionElement<K>> finder = theKeyManager.getElementFinder(theKey);
				theCurrentEntry = theEntries.searchValue(entry -> finder.compareTo(entry.theKeyElement), SortedSearchFilter.OnlyMatch);
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
		public BetterList<CollectionElement<V>> getElementsBySource(ElementId sourceEl) {
			try (Transaction t = lock(false, null)) {
				KeyEntry entry = getCurrentEntry(true);
				return entry.getValues().getElementsBySource(sourceEl);
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
		public String canAdd(V value, ElementId after, ElementId before) {
			try (Transaction t = Lockable.lockAll(//
				Lockable.lockable(this, false, null), getAddKey())) {
				getAddKey().accept(theKey);
				if (after != null || before != null) {
					KeyEntry entry = getCurrentEntry(true);
					return entry.getValues().canAdd(value, after, before);
				}
				return theValueManager.canAdd(value, null, null);
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
				DerivedCollectionElement<V> newElement = theValueManager.addElement(value, null, null, first);
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
	}
}
