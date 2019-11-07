package org.observe.assoc.impl;

import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.Observable;
import org.observe.Subscription;
import org.observe.assoc.ObservableMultiMap;
import org.observe.assoc.ObservableMultiMapEvent;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollectionDataFlowImpl.ActiveCollectionManager;
import org.observe.collect.ObservableCollectionDataFlowImpl.ActiveSetManager;
import org.observe.collect.ObservableCollectionDataFlowImpl.CollectionElementListener;
import org.observe.collect.ObservableCollectionDataFlowImpl.DerivedCollectionElement;
import org.observe.collect.ObservableCollectionDataFlowImpl.ElementAccepter;
import org.observe.collect.ObservableCollectionEvent;
import org.observe.collect.ObservableSet;
import org.observe.util.WeakListening;
import org.qommons.Identifiable.AbstractIdentifiable;
import org.qommons.Lockable;
import org.qommons.Transaction;
import org.qommons.collect.BetterSortedMap;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.ElementId;
import org.qommons.collect.ListenerList;
import org.qommons.collect.MultiEntryHandle;
import org.qommons.collect.MultiEntryValueHandle;
import org.qommons.tree.BetterTreeList;
import org.qommons.tree.BetterTreeMap;
import org.qommons.tree.BetterTreeSet;

import com.google.common.reflect.TypeToken;

public class CollectionGatheredMultiMap<S, K, V> extends AbstractIdentifiable implements ObservableMultiMap<K, V> {
	private final ObservableCollection<S> theSource;
	private final ActiveCollectionManager<S, ?, K> theKeyManager;
	private final ActiveCollectionManager<S, ?, V> theValueManager;

	private final WeakListening.Builder theWeakListening;
	private TypeToken<MultiEntryHandle<K, V>> theEntryType;
	private TypeToken<MultiEntryValueHandle<K, V>> theValueEntryType;

	private final BetterTreeList<KeyEntry> theEntries;
	private final BetterTreeList<KeyEntry> theActiveEntries;

	private final BetterSortedMap<ElementId, GroupedElementInfo> theElementInfo;

	private long theStamp;
	private int theValueSize;

	private final ListenerList<Consumer<? super ObservableMultiMapEvent<? extends K, ? extends V>>> theMapListeners;
	private final ListenerList<Consumer<? super ObservableCollectionEvent<? extends K>>> theKeySetListeners;

	public CollectionGatheredMultiMap(ObservableCollection<S> source, ActiveSetManager<S, ?, K> keyManager,
		ActiveCollectionManager<S, ?, V> valueManager, Observable<?> until) {
		theSource = source;
		theKeyManager = keyManager;
		theValueManager = valueManager;

		theEntries = new BetterTreeList<>(false);
		theActiveEntries = new BetterTreeList<>(false);
		theElementInfo = new BetterTreeMap<>(false, ElementId::compareTo);

		theMapListeners = ListenerList.build().withFastSize(false).build();
		theKeySetListeners = ListenerList.build().withFastSize(false).build();

		// Must maintain a strong reference to the event listening so it is not GC'd while the collection is still alive
		theWeakListening = WeakListening.build().withUntil(r -> until.act(v -> r.run()));
		WeakListening listening = theWeakListening.getListening();
		theKeyManager.begin(true, new ElementAccepter<K>() {
			@Override
			public void accept(DerivedCollectionElement<K> element, Object cause) {
				KeyEntry entry = addKey(element, cause);
				element.setListener(new CollectionElementListener<K>() {
					@Override
					public void update(K oldValue, K newValue, Object updateCause) {
						entry.updated(newValue, updateCause);
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
						ref.updated(newValue, updateCause);
					}

					@Override
					public void removed(V value, Object removeCause) {
						ref.removed(removeCause);
					}
				});

			}
		}, listening);
	}

	@Override
	public boolean isLockSupported() {
		return theKeyManager.isLockSupported() || theValueManager.isLockSupported();
	}

	@Override
	public Transaction lock(boolean write, Object cause) {
		return Lockable.lockAll(//
			Lockable.lockable(theKeyManager, write, cause), Lockable.lockable(theValueManager, write, cause));
	}

	@Override
	public Transaction tryLock(boolean write, Object cause) {
		return Lockable.tryLockAll(//
			Lockable.lockable(theKeyManager, write, cause), Lockable.lockable(theValueManager, write, cause));
	}

	@Override
	public long getStamp() {
		return theStamp;
	}

	@Override
	protected Object createIdentity() {
		// TODO Auto-generated method stub
	}

	@Override
	public TypeToken<K> getKeyType() {
		return theKeyManager.getTargetType();
	}

	@Override
	public TypeToken<V> getValueType() {
		return theValueManager.getTargetType();
	}

	@Override
	public TypeToken<MultiEntryHandle<K, V>> getEntryType() {
		if (theEntryType == null)
			theEntryType = ObservableMultiMap.buildEntryType(getKeyType(), getValueType());
		return theEntryType;
	}

	@Override
	public TypeToken<MultiEntryValueHandle<K, V>> getEntryValueType() {
		if (theValueEntryType == null)
			theValueEntryType = ObservableMultiMap.buildValueEntryType(getKeyType(), getValueType());
		return theValueEntryType;
	}

	@Override
	public ObservableSet<K> keySet() {
		// TODO Auto-generated method stub
	}

	@Override
	public MultiEntryHandle<K, V> getEntryById(ElementId keyId) {
		return theEntries.getElement(keyId).get();
	}

	@Override
	public MultiEntryHandle<K, V> getOrPutEntry(K key, Function<? super K, ? extends Iterable<? extends V>> value, ElementId afterKey,
		ElementId beforeKey, boolean first, Runnable added) {
		// TODO Auto-generated method stub
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
		// TODO Auto-generated method stub
	}

	@Override
	public Subscription onChange(Consumer<? super ObservableMultiMapEvent<? extends K, ? extends V>> action) {
		return theMapListeners.add(action, true)::run;
	}

	@Override
	public MultiMapFlow<K, V> flow() {
		// TODO Auto-generated method stub
	}

	@Override
	protected void finalize() throws Throwable {
		super.finalize();
		theWeakListening.unsubscribe();
	}

	// These are synchronized because even though the keys and values are from the same source,
	// other sources of change or updates may have been introduced to the key or value flows.
	// Without protection, one of these other sources could potentially fire during a source change,
	// causing simultaneous modification and corruption.

	private synchronized KeyEntry addKey(DerivedCollectionElement<K> keyElement, Object cause) {
		KeyEntry entry = new KeyEntry(keyElement);
		entry.entryId = theEntries.addElement(entry, false).getElementId();
		for (ElementId sourceElement : theKeyManager.getSourceElements(keyElement, theSource)) {
			GroupedElementInfo info = theElementInfo.computeIfAbsent(sourceElement, GroupedElementInfo::new);
			info.addKey(entry);
			entry.addSource(info, cause);
		}
		return entry;
	}

	private synchronized ValueRef addValue(DerivedCollectionElement<V> valueElement, Object cause) {
		ValueRef ref = new ValueRef(valueElement);
		for (ElementId sourceElement : theValueManager.getSourceElements(valueElement, theSource)) {
			GroupedElementInfo info = theElementInfo.computeIfAbsent(sourceElement, GroupedElementInfo::new);
			info.addValue(ref);
			ref.addSource(info, cause);
		}
		return ref;
	}

	class KeyEntry implements MultiEntryHandle<K, V> {
		private final DerivedCollectionElement<K> theKeyElement;
		private final ValueCollection theValues;

		ElementId entryId;
		ElementId activEntryId;

		KeyEntry(DerivedCollectionElement<K> keyElement) {
			theKeyElement = keyElement;
			theValues = new ValueCollection();
		}

		void updated(K newKey, Object cause) {}

		void removed(Object cause) {}

		void addSource(GroupedElementInfo sourceInfo, Object cause) {}
	}

	class ValueCollection implements ObservableCollection<V> {}

	class ValueRef implements Comparable<ValueRef> {
		private final DerivedCollectionElement<V> theValueElement;

		ValueRef(DerivedCollectionElement<V> valueElement) {
			theValueElement = valueElement;
		}

		@Override
		public int compareTo(ValueRef o) {
			return theValueElement.compareTo(o.theValueElement);
		}

		void updated(V newValue, Object cause) {}

		void removed(Object cause) {}

		void addSource(GroupedElementInfo sourceInfo, Object cause) {}
	}

	class GroupedElementInfo {
		final ElementId sourceElement;
		final BetterSortedSet<KeyEntry> theKeys;
		final BetterSortedSet<ValueRef> theValues;

		GroupedElementInfo(ElementId sourceElement) {
			this.sourceElement = sourceElement;
			theKeys = new BetterTreeSet<>(false, KeyEntry::compareTo);
			theValues = new BetterTreeSet<>(false, ValueRef::compareTo);
		}

		void addKey(KeyEntry key) {
			theKeys.add(key);
		}

		void addValue(ValueRef value) {
			theValues.add(value);
		}
	}
}
