package org.observe.assoc.impl;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.Observable;
import org.observe.Subscription;
import org.observe.assoc.ObservableMultiMapEvent;
import org.observe.collect.CollectionChangeType;
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
import org.qommons.collect.BetterSortedMap;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.ListenerList;
import org.qommons.collect.MultiEntryHandle;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.tree.BetterTreeList;
import org.qommons.tree.BetterTreeMap;
import org.qommons.tree.BetterTreeSet;

import com.google.common.reflect.TypeToken;

public class DefaultActiveMultiMap<S, K, V> extends AbstractDerivedObservableMultiMap<S, K, V> {
	private final ActiveSetManager<S, ?, K> theKeyManager;
	private final ActiveCollectionManager<S, ?, V> theValueManager;

	private final WeakListening.Builder theWeakListening;

	private final BetterTreeList<KeyEntry> theEntries;
	private final BetterTreeList<KeyEntry> theActiveEntries;

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

		theEntries = new BetterTreeList<>(false);
		theActiveEntries = new BetterTreeList<>(false);
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
			GroupedElementInfo info = theElementInfo.computeIfAbsent(sourceElement, GroupedElementInfo::new);
			info.addKey(entry);
			entry.addSource(info, cause);
		}
		return entry;
	}

	private synchronized ValueRef addValue(DerivedCollectionElement<V> valueElement, Object cause) {
		ValueRef ref = new ValueRef(valueElement);
		for (ElementId sourceElement : theValueManager.getSourceElements(valueElement, getSourceCollection())) {
			GroupedElementInfo info = theElementInfo.computeIfAbsent(sourceElement, GroupedElementInfo::new);
			info.addValue(ref);
			ref.addSource(info, cause);
		}
		return ref;
	}

	class KeyEntry implements MultiEntryHandle<K, V> {
		final DerivedCollectionElement<K> theKeyElement;
		final ValueCollection theValues;
		final BetterSortedSet<GroupedElementInfo> theSources;

		ElementId entryId;
		ElementId activeEntryId;

		KeyEntry(DerivedCollectionElement<K> keyElement) {
			theKeyElement = keyElement;
			theValues = new ValueCollection(this);
			theSources = new BetterTreeSet<>(false, GroupedElementInfo::compareTo);
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
		public BetterCollection<V> getValues() {
			return theValues;
		}

		void removeActive(K key, Object cause) {
			// TODO
		}

		void updated(K oldKey, K newKey, Object cause) {
			synchronized (DefaultActiveMultiMap.this) {
				TypeToken<V> valueType = getValueType();
				if (!theKeyManager.equivalence().elementEquals(newKey, oldKey)) {
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
					if (activeEntryId != null) {
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
			// No need to do any eventing because by this point the key has no values so it is not actually in the exposed map
			synchronized (DefaultActiveMultiMap.this) {
				for (GroupedElementInfo source : theSources)
					source.theKeys.remove(this);
				theEntries.mutableElement(entryId).remove();
			}
		}

		void addSource(GroupedElementInfo sourceInfo, Object cause) {
			theSources.add(sourceInfo);
			theValues.addValues(sourceInfo.theValues);
		}
	}

	class ValueRef implements Comparable<ValueRef> {
		final DerivedCollectionElement<V> theValueElement;
		final BetterSortedMap<KeyEntry, CollectionElement<ValueRef>> theKeys;
		final BetterSortedSet<GroupedElementInfo> theSources;

		ValueRef(DerivedCollectionElement<V> valueElement) {
			theValueElement = valueElement;
			theKeys = new BetterTreeMap<>(false, (k1, k2) -> k1.entryId.compareTo(k2.entryId));
			theSources = new BetterTreeSet<>(false, GroupedElementInfo::compareTo);
		}

		@Override
		public int compareTo(ValueRef o) {
			return theValueElement.compareTo(o.theValueElement);
		}

		void updated(V oldValue, V newValue, Object cause) {
			synchronized (DefaultActiveMultiMap.this) {
				// TODO check source elements and adjust against keys
				TypeToken<K> keyType = getKeyType();
				TypeToken<V> valueType = getValueType();
				for (Map.Entry<KeyEntry, CollectionElement<ValueRef>> entry : theKeys.entrySet()) {
					if (entry.getKey().activeEntryId != null && entry.getKey().activeEntryId.isPresent()) {
						ObservableMultiMapEvent<K, V> event = new ObservableMultiMapEvent<>(//
							entry.getKey().activeEntryId, entry.getValue().getElementId(), keyType, valueType, //
							theActiveEntries.getElementsBefore(entry.getKey().activeEntryId), //
							entry.getKey().theValues.theValues.getElementsBefore(entry.getValue().getElementId()), //
							CollectionChangeType.set, entry.getKey().get(), oldValue, newValue, cause);
						try (Transaction evtT = Causable.use(event)) {
							theMapListeners.forEach(//
								listener -> listener.accept(event));
						}
						entry.getKey().theValues.valueUpdated(entry.getValue().getElementId(), oldValue, newValue, cause);
					}
				}
			}
		}

		void removed(V value, Object cause) {
			synchronized (DefaultActiveMultiMap.this) {
				TypeToken<K> keyType = getKeyType();
				TypeToken<V> valueType = getValueType();
				for (Map.Entry<KeyEntry, CollectionElement<ValueRef>> entry : theKeys.entrySet()) {
					if (entry.getKey().activeEntryId != null && entry.getKey().activeEntryId.isPresent()) {
						K key = entry.getKey().get();
						ObservableMultiMapEvent<K, V> event = new ObservableMultiMapEvent<>(//
							entry.getKey().activeEntryId, entry.getValue().getElementId(), keyType, valueType, //
							theActiveEntries.getElementsBefore(entry.getKey().activeEntryId), //
							entry.getKey().theValues.theValues.getElementsBefore(entry.getValue().getElementId()), //
							CollectionChangeType.remove, key, value, value, cause);
						try (Transaction evtT = Causable.use(event)) {
							theMapListeners.forEach(//
								listener -> listener.accept(event));
						}
						entry.getKey().theValues.valueRemoved(entry.getValue().getElementId(), key, value, cause);
					}
				}
			}
		}

		void addSource(GroupedElementInfo sourceInfo, Object cause) {
			theSources.add(sourceInfo);
			for (KeyEntry key : sourceInfo.theKeys)
				theKeys.computeIfAbsent(key, k -> k.theValues.addValue(this, cause));
		}
	}

	class GroupedElementInfo implements Comparable<GroupedElementInfo> {
		final ElementId sourceElement;
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

		void addKey(KeyEntry key) {
			theKeys.add(key);
		}

		void addValue(ValueRef value) {
			theValues.add(value);
		}
	}

	class ValueCollection extends AbstractIdentifiable implements BetterList<V> {
		private final KeyEntry theEntry;
		final BetterSortedSet<ValueRef> theValues;

		public ValueCollection(KeyEntry entry) {
			theEntry = entry;
			theValues = new BetterTreeSet<>(false, ValueRef::compareTo);
		}

		void addValues(Collection<ValueRef> values) {
			// TODO
		}

		CollectionElement<ValueRef> addValue(ValueRef value, Object cause) {
			CollectionElement<ValueRef> element = theValues.addElement(value, false);
			// TODO Fire events
			return element;
		}

		void valueUpdated(ElementId id, V oldValue, V newValue, Object cause) {
			// TODO
		}

		void valueRemoved(ElementId id, K key, V value, Object cause) {
			// TODO Remove the value and fire events
			if (theValues.isEmpty())
				theEntry.removeActive(key, cause);
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

	class KeySet implements ObservableSet<K> {}

	class KeyValueCollection implements ObservableCollection<V> {
		private final K theKey;
		private KeyEntry theEntry;

		KeyValueCollection(K key) {
			theKey = key;
		}
	}
}
