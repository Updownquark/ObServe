package org.observe.assoc;

import java.util.Comparator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.observe.Observable;
import org.observe.Subscription;
import org.observe.assoc.ObservableMultiMap.MultiMapFlow;
import org.observe.assoc.ObservableSortedMultiMap.SortedMultiMapFlow;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.Combination.CombinationPrecursor;
import org.observe.collect.Combination.CombinedFlowDef;
import org.observe.collect.Equivalence;
import org.observe.collect.FlowOptions;
import org.observe.collect.FlowOptions.GroupingOptions;
import org.observe.collect.FlowOptions.MapOptions;
import org.observe.collect.FlowOptions.UniqueOptions;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollection.ModFilterBuilder;
import org.observe.collect.ObservableCollection.UniqueDataFlow;
import org.observe.collect.ObservableCollection.UniqueSortedDataFlow;
import org.observe.collect.ObservableCollectionDataFlowImpl;
import org.observe.collect.ObservableCollectionDataFlowImpl.ActiveCollectionManager;
import org.observe.collect.ObservableCollectionDataFlowImpl.DerivedCollectionElement;
import org.observe.collect.ObservableCollectionDataFlowImpl.PassiveCollectionManager;
import org.observe.collect.ObservableCollectionEvent;
import org.observe.collect.ObservableSet;
import org.observe.collect.ObservableSetImpl;
import org.observe.collect.ObservableSetImpl.UniqueManager;
import org.observe.collect.ObservableSortedSetImpl;
import org.qommons.Transaction;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.ListenerList;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.collect.MutableElementSpliterator;
import org.qommons.collect.SimpleMapEntry;
import org.qommons.tree.BetterTreeSet;

import com.google.common.reflect.TypeToken;

public class ObservableMultiMapImpl {
	private ObservableMultiMapImpl() {}

	static class KeyFlow<E, I, K, V> implements UniqueDataFlow<E, I, K> {
		private final CollectionDataFlow<?, ?, Map.Entry<K, V>> theEntryFlow;
		private final TypeToken<K> theKeyType;
		private final Equivalence<? super K> theKeyEquivalence;

		KeyFlow(CollectionDataFlow<?, ?, Entry<K, V>> entryFlow, TypeToken<K> keyType, Equivalence<? super K> keyEquivalence) {
			theEntryFlow = entryFlow;
			theKeyType = keyType;
			theKeyEquivalence = keyEquivalence;
		}

		public CollectionDataFlow<?, ?, Map.Entry<K, V>> getEntries() {
			return theEntryFlow;
		}

		@Override
		public TypeToken<K> getTargetType() {
			return theKeyType;
		}

		public Equivalence<? super K> equivalence() {
			return theKeyEquivalence;
		}

		@Override
		public UniqueDataFlow<E, K, K> filter(Function<? super K, String> filter) {
			return new KeyFlow<>(theEntryFlow.filter(entry -> filter.apply(entry.getKey())), theKeyType, theKeyEquivalence);
		}

		@Override
		public UniqueDataFlow<E, K, K> filterStatic(Function<? super K, String> filter) {
			return new KeyFlow<>(theEntryFlow.filterStatic(entry -> filter.apply(entry.getKey())), theKeyType, theKeyEquivalence);
		}

		@Override
		public <X> UniqueDataFlow<E, K, K> whereContained(CollectionDataFlow<?, ?, X> other, boolean include) {
			CollectionDataFlow<?, ?, Map.Entry<K, V>> otherEntryFlow = other.filterStatic(//
				v -> v == null || theKeyType.getRawType().isInstance(v) ? null : StdMsg.ILLEGAL_ELEMENT//
				).map(//
					theEntryFlow.getTargetType(), v -> new SimpleMapEntry<>((K) v, null)//
					, options -> options.cache(false));
			CollectionDataFlow<?, Map.Entry<K, V>, Map.Entry<K, V>> filteredEntries = theEntryFlow.whereContained(otherEntryFlow, include);
			return new KeyFlow<>(filteredEntries, theKeyType, theKeyEquivalence);
		}

		@Override
		public UniqueDataFlow<E, K, K> refresh(Observable<?> refresh) {
			return new KeyFlow<>(theEntryFlow.refresh(refresh), theKeyType, theKeyEquivalence);
		}

		@Override
		public UniqueDataFlow<E, K, K> refreshEach(Function<? super K, ? extends Observable<?>> refresh) {
			return new KeyFlow<>(theEntryFlow.refreshEach(entry -> refresh.apply(entry.getKey())), theKeyType, theKeyEquivalence);
		}

		@Override
		public <X> UniqueDataFlow<E, K, X> mapEquivalent(TypeToken<X> target, Function<? super K, ? extends X> map,
			Function<? super X, ? extends K> reverse, Consumer<MapOptions<K, X>> options) {
			TypeToken<Map.Entry<X, V>> mappedEntryType = ObservableMap.buildEntryType(target,
				(TypeToken<V>) theEntryFlow.getTargetType().resolveType(Map.Entry.class.getTypeParameters()[1]));
			Function<Map.Entry<K, V>, Map.Entry<X, V>> entryMap = entry -> new SimpleMapEntry<>(map.apply(entry.getKey()),
				entry.getValue());
			Function<Map.Entry<X, V>, Map.Entry<K, V>> entryReverse = entry -> new SimpleMapEntry<>(reverse.apply(entry.getKey()),
				entry.getValue());
			MapOptions<K, X> keyOptions = new MapOptions<>();
			options.accept(keyOptions);
			if (keyOptions.getElementReverse() != null)
				throw new UnsupportedOperationException("Element setting unsupported");
			CollectionDataFlow<?, Map.Entry<K, V>, Map.Entry<X, V>> mappedEntries = theEntryFlow.map(mappedEntryType, //
				entryMap, //
				mapOptions -> {
					mapOptions.cache(keyOptions.isCached())//
					.fireIfUnchanged(mapOptions.isFireIfUnchanged())//
					.reEvalOnUpdate(keyOptions.isReEvalOnUpdate())//
					.withReverse(entryReverse);
				});
			Equivalence<X> mappedEquiv = theKeyEquivalence.map((Class<X>) target.getRawType(), x -> true, map, reverse);
			return new KeyFlow<>(mappedEntries, target, mappedEquiv);
		}

		@Override
		public UniqueDataFlow<E, K, K> filterMod(Consumer<ModFilterBuilder<K>> options) {
			ModFilterBuilder<K> keyOptions=new ModFilterBuilder<>();
			options.accept(keyOptions);
			if (keyOptions.getImmutableMsg() != null)
				throw new UnsupportedOperationException("Key set immutability is not supported");
			CollectionDataFlow<?, Map.Entry<K, V>, Map.Entry<K, V>> modFilteredEntries=theEntryFlow.filterMod(entryOptions->{
				if (keyOptions.getAddMsg() != null)
					entryOptions.noAdd(keyOptions.getAddMsg());
				Function<? super K, String> addMsgFn = keyOptions.getAddMsgFn();
				if (addMsgFn != null)
					entryOptions.filterAdd(entry -> addMsgFn.apply(entry.getKey()));
				if (keyOptions.getRemoveMsg() != null)
					entryOptions.noRemove(keyOptions.getRemoveMsg());
				Function<? super K, String> removeMsgFn = keyOptions.getRemoveMsgFn();
				if (removeMsgFn != null)
					entryOptions.filterRemove(entry -> removeMsgFn.apply(entry.getKey()));
			});
			return new KeyFlow<>(modFilteredEntries, theKeyType, theKeyEquivalence);
		}

		@Override
		public CollectionDataFlow<E, K, K> withEquivalence(Equivalence<? super K> equivalence) {
			throw new UnsupportedOperationException("Only equivalent unique operations are permitted");
		}

		@Override
		public <X> CollectionDataFlow<E, K, X> map(TypeToken<X> target, Function<? super K, ? extends X> map,
			Consumer<MapOptions<K, X>> options) {
			throw new UnsupportedOperationException("Only equivalent unique operations are permitted");
		}

		@Override
		public <X> CollectionDataFlow<E, K, X> combine(TypeToken<X> targetType,
			Function<CombinationPrecursor<K, X>, CombinedFlowDef<K, X>> combination) {
			throw new UnsupportedOperationException("Only equivalent unique operations are permitted");
		}

		@Override
		public <X> CollectionDataFlow<E, ?, X> flatMap(TypeToken<X> target,
			Function<? super K, ? extends CollectionDataFlow<?, ?, ? extends X>> map) {
			throw new UnsupportedOperationException("Only equivalent unique operations are permitted");
		}

		@Override
		public CollectionDataFlow<E, K, K> sorted(Comparator<? super K> compare) {
			throw new IllegalStateException("Only equivalent unique operations are permitted");
		}

		@Override
		public UniqueDataFlow<E, K, K> distinct(Consumer<UniqueOptions> options) {
			return (UniqueDataFlow<E, K, K>) this;
		}

		@Override
		public UniqueSortedDataFlow<E, K, K> distinctSorted(Comparator<? super K> compare, boolean alwaysUseFirst) {
			throw new UnsupportedOperationException("Only equivalent unique operations are permitted");
		}

		@Override
		public <K2> MultiMapFlow<E, K2, K> groupBy(TypeToken<K2> keyType, Function<? super K, ? extends K2> keyMap,
			Consumer<GroupingOptions> options) {
			throw new UnsupportedOperationException("Group by unsupported");
		}

		@Override
		public <K2> SortedMultiMapFlow<E, K2, K> groupBy(TypeToken<K2> keyType, Function<? super K, ? extends K2> keyMap,
			Comparator<? super K2> keyCompare, Consumer<GroupingOptions> options) {
			throw new UnsupportedOperationException("Group by unsupported");
		}

		@Override
		public boolean supportsPassive() {
			return theEntryFlow.supportsPassive();
		}

		@Override
		public ActiveCollectionManager<E, ?, K> manageActive() {
			throw new UnsupportedOperationException("Materialization not supported");
		}

		@Override
		public PassiveCollectionManager<E, ?, K> managePassive() {
			throw new UnsupportedOperationException("Materialization not supported");
		}

		@Override
		public ObservableSet<K> collectPassive() {
			throw new UnsupportedOperationException("Materialization not supported");
		}

		@Override
		public ObservableSet<K> collectActive(Observable<?> until) {
			throw new UnsupportedOperationException("Materialization not supported");
		}
	}

	static class SortedKeyFlow<E, I, K, V> extends KeyFlow<E, I, K, V> implements UniqueSortedDataFlow<E, I, K> {
		private final Comparator<? super K> theKeyCompare;

		SortedKeyFlow(CollectionDataFlow<?, Entry<I, V>, Entry<K, V>> entryFlow, TypeToken<K> keyType, Comparator<? super K> keyCompare) {
			super(entryFlow, keyType, Equivalence.of((Class<K>) keyType.getRawType(), keyCompare, true));
			theKeyCompare = keyCompare;
		}

		@Override
		public Comparator<? super K> comparator() {
			return theKeyCompare;
		}

		@Override
		public <X> UniqueSortedDataFlow<E, K, X> mapEquivalent(TypeToken<X> target, Function<? super K, ? extends X> map,
			Comparator<? super X> compare, Consumer<MapOptions<K, X>> options) {
			// TODO Auto-generated method stub
		}
	}

	static class ValueFlow<E, K, I, V> implements CollectionDataFlow<E, I, V> {
		private final CollectionDataFlow<?, ?, Map.Entry<K, V>> theEntryFlow;
		private final TypeToken<V> theValueType;
		private final Equivalence<? super V> theValueEquivalence;

		ValueFlow(CollectionDataFlow<?, ?, Entry<K, V>> entryFlow, TypeToken<V> valueType,
			Equivalence<? super V> valueEquivalence) {
			theEntryFlow = entryFlow;
			theValueType = valueType;
			theValueEquivalence = valueEquivalence;
		}

		public CollectionDataFlow<?, ?, Map.Entry<K, V>> getEntries() {
			return theEntryFlow;
		}

		@Override
		public TypeToken<V> getTargetType() {
			return theValueType;
		}

		public Equivalence<? super V> equivalence() {
			return theValueEquivalence;
		}

		@Override
		public CollectionDataFlow<E, V, V> filter(Function<? super V, String> filter) {
			return new ValueFlow<>(theEntryFlow.filter(entry -> filter.apply(entry.getValue())), theValueType, theValueEquivalence);
		}

		@Override
		public CollectionDataFlow<E, V, V> filterStatic(Function<? super V, String> filter) {
			return new ValueFlow<>(theEntryFlow.filterStatic(entry -> filter.apply(entry.getValue())), theValueType, theValueEquivalence);
		}

		@Override
		public <X> CollectionDataFlow<E, V, V> whereContained(CollectionDataFlow<?, ?, X> other, boolean include) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public CollectionDataFlow<E, V, V> withEquivalence(Equivalence<? super V> equivalence) {
			// Intermediate type doesn't matter here
			return new ValueFlow<>((CollectionDataFlow<E, Map.Entry<K, V>, Map.Entry<K, V>>) (CollectionDataFlow<?, ?, ?>) theEntryFlow,
				theValueType, equivalence);
		}

		@Override
		public CollectionDataFlow<E, V, V> refresh(Observable<?> refresh) {
			return new ValueFlow<>(theEntryFlow.refresh(refresh), theValueType, theValueEquivalence);
		}

		@Override
		public CollectionDataFlow<E, V, V> refreshEach(Function<? super V, ? extends Observable<?>> refresh) {
			return new ValueFlow<>(theEntryFlow.refreshEach(entry -> refresh.apply(entry.getValue())), theValueType, theValueEquivalence);
		}

		@Override
		public <X> CollectionDataFlow<E, V, X> map(TypeToken<X> target, Function<? super V, ? extends X> map,
			Consumer<MapOptions<V, X>> options) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public <X> CollectionDataFlow<E, V, X> combine(TypeToken<X> targetType,
			Function<CombinationPrecursor<V, X>, CombinedFlowDef<V, X>> combination) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public <X> CollectionDataFlow<E, ?, X> flatMap(TypeToken<X> target,
			Function<? super V, ? extends CollectionDataFlow<?, ?, ? extends X>> map) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public CollectionDataFlow<E, V, V> sorted(Comparator<? super V> compare) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public UniqueDataFlow<E, V, V> distinct(Consumer<UniqueOptions> options) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public UniqueSortedDataFlow<E, V, V> distinctSorted(Comparator<? super V> compare, boolean alwaysUseFirst) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public CollectionDataFlow<E, V, V> filterMod(Consumer<ModFilterBuilder<V>> options) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public <K2> MultiMapFlow<E, K2, V> groupBy(TypeToken<K2> keyType, Function<? super V, ? extends K2> keyMap,
			Consumer<GroupingOptions> options) {
			throw new UnsupportedOperationException("Group by unsupported");
		}

		@Override
		public <K2> SortedMultiMapFlow<E, K2, V> groupBy(TypeToken<K2> keyType, Function<? super V, ? extends K2> keyMap,
			Comparator<? super K2> keyCompare, Consumer<GroupingOptions> options) {
			throw new UnsupportedOperationException("Group by unsupported");
		}

		@Override
		public boolean supportsPassive() {
			return theEntryFlow.supportsPassive();
		}

		@Override
		public ActiveCollectionManager<E, ?, V> manageActive() {
			throw new UnsupportedOperationException("Materialization not supported");
		}

		@Override
		public PassiveCollectionManager<E, ?, V> managePassive() {
			throw new UnsupportedOperationException("Materialization not supported");
		}

		@Override
		public ObservableCollection<V> collectPassive() {
			throw new UnsupportedOperationException("Materialization not supported");
		}

		@Override
		public ObservableCollection<V> collectActive(Observable<?> until) {
			throw new UnsupportedOperationException("Materialization not supported");
		}
	}

	static class GroupingManager<E, K, V> extends ObservableSetImpl.UniqueManager<E, Map.Entry<K, V>> {
		private final ActiveCollectionManager<E, ?, Map.Entry<K, V>> theParent;
		private final TypeToken<K> theKeyType;
		private final TypeToken<V> theValueType;
		private final Consumer<ObservableMapEvent<K, V>> theEventListener;

		private BetterList<K> theAssembledKeys;

		public GroupingManager(ActiveCollectionManager<E, ?, Map.Entry<K, V>> parent, //
			TypeToken<K> keyType, TypeToken<V> valueType, //
			FlowOptions.GroupingDef options, Consumer<ObservableMapEvent<K, V>> listener) {
			super(parent, options.isUsingFirst(), options.isPreservingSourceOrder());

			theParent = parent;
			theKeyType = keyType;
			theValueType = valueType;
			theEventListener = listener;
		}

		void setAssembledKeys(BetterList<K> assembledKeys) {
			theAssembledKeys = assembledKeys;
		}

		ActiveCollectionManager<E, ?, Map.Entry<K, V>> getParent() {
			return theParent;
		}

		@Override
		protected CollectionElement<UniqueManager<E, Entry<K, V>>.UniqueElement> getElement(Entry<K, V> value) {
			return super.getElement(value);
		}

		@Override
		protected UniqueManager<E, Map.Entry<K, V>>.UniqueElement createUniqueElement(Map.Entry<K, V> value) {
			return new GroupedElement(value);
		}

		protected class GroupedElement extends UniqueElement {
			private ElementId theKeyId;

			protected GroupedElement(Map.Entry<K, V> key) {
				super(key);
			}

			protected void setKeyId(ElementId keyId) {
				theKeyId = keyId;
			}

			@Override
			protected BetterTreeSet<DerivedCollectionElement<Entry<K, V>>> getParentElements() {
				return super.getParentElements();
			}

			@Override
			protected CollectionElement<DerivedCollectionElement<Map.Entry<K, V>>> addParent(
				DerivedCollectionElement<Map.Entry<K, V>> parentEl, Object cause) {
				CollectionElement<DerivedCollectionElement<Map.Entry<K, V>>> node = super.addParent(parentEl, cause);
				if (theKeyId != null) {
					int keyIndex = theAssembledKeys.getElementsBefore(theKeyId);
					int valueIndex = getParentElements().getElementsBefore(node.getElementId());
					theEventListener.accept(new ObservableMapEvent<>(theKeyId, node.getElementId(), theKeyType, theValueType, //
						keyIndex, valueIndex, CollectionChangeType.add, parentEl.get().getKey(), null, parentEl.get().getValue(), cause));
				}
				return node;
			}

			@Override
			protected void parentUpdated(CollectionElement<DerivedCollectionElement<Map.Entry<K, V>>> parentEl, Map.Entry<K, V> oldValue,
				Map.Entry<K, V> newValue, Object cause) {
				super.parentUpdated(parentEl, oldValue, newValue, cause);
				int keyIndex = theAssembledKeys.getElementsBefore(theKeyId);
				int valueIndex = getParentElements().getElementsBefore(parentEl.getElementId());
				DerivedCollectionElement<Map.Entry<K, V>> active = getActiveElement();
				theEventListener.accept(new ObservableMapEvent<>(theKeyId, parentEl.getElementId(), theKeyType, theValueType, //
					keyIndex, valueIndex, CollectionChangeType.set, active.get().getKey(), oldValue.getValue(), newValue.getValue(),
					cause));
			}

			@Override
			protected void parentRemoved(CollectionElement<DerivedCollectionElement<Map.Entry<K, V>>> parentEl, Map.Entry<K, V> value,
				Object cause) {
				super.parentRemoved(parentEl, value, cause);
				int keyIndex = theAssembledKeys.getElementsBefore(theKeyId);
				int valueIndex = getParentElements().getElementsBefore(parentEl.getElementId());
				DerivedCollectionElement<Map.Entry<K, V>> active = getActiveElement();
				theEventListener.accept(new ObservableMapEvent<>(theKeyId, parentEl.getElementId(), theKeyType, theValueType, //
					keyIndex, valueIndex, CollectionChangeType.remove, active.get().getKey(), value.getValue(), value.getValue(), cause));
			}
		}
	}

	static class DerivedObservableMultiMap<E, K, V> implements ObservableMultiMap<K, V> {
		private final TypeToken<K> theKeyType;
		private final TypeToken<V> theValueType;
		private TypeToken<ObservableMultiEntry<K, V>> theEntryType;
		private final Equivalence<? super K> theKeyEquivalence;
		private final Equivalence<? super V> theValueEquivalence;
		private final ActiveCollectionManager<E, ?, Entry<K, V>> theEntries;

		private final ListenerList<Consumer<? super ObservableMapEvent<? extends K, ? extends V>>> theEventListeners;
		private final GroupingManager<E, K, V> theGrouping;
		private final ObservableSet<K> theKeySet;

		public DerivedObservableMultiMap(CollectionDataFlow<E, ?, Map.Entry<K, V>> entryFlow, //
			TypeToken<K> keyType, TypeToken<V> valueType, //
			Equivalence<? super K> keyEquivalence, Equivalence<? super V> valueEquivalence, boolean uniqueValues, //
			FlowOptions.GroupingDef options, Observable<?> until) {
			theKeyType = keyType;
			theValueType = valueType;
			theKeyEquivalence = keyEquivalence;
			theValueEquivalence = valueEquivalence;

			theEventListeners = new ListenerList<>(null);
			Predicate<Map.Entry<K, ?>> entryFilter = entry -> {
				if (entry == null || !theKeyEquivalence.isElement(entry.getKey()))
					return false;
				if (entry.getKey() != null && !theKeyType.getRawType().isInstance(entry.getKey()))
					return false;
				return true;
			};
			Equivalence<Map.Entry<K, ?>> entryEquivalence = theKeyEquivalence.map((Class<Map.Entry<K, ?>>) (Class<?>) Map.Entry.class, //
				entryFilter, key -> new SimpleMapEntry<>(key, null), Map.Entry::getKey);
			theGrouping = new GroupingManager<>(entryFlow.withEquivalence(entryEquivalence).manageActive(), keyType, valueType, options, //
				event -> theEventListeners.forEach(listener -> listener.accept(event)));
			theEntries = theGrouping.getParent();
			FlowOptions.MapOptions<Map.Entry<K, V>, K> keyMapOptions = new FlowOptions.MapOptions<>();
			keyMapOptions.withReverse(k -> new SimpleMapEntry<>(k, null)).reEvalOnUpdate(!options.isStaticCategories());
			ActiveCollectionManager<E, Map.Entry<K, V>, K> keyFlow;
			keyFlow = new ObservableCollectionDataFlowImpl.ActiveMappedCollectionManager<>(theGrouping, theKeyType, Map.Entry::getKey, //
				new FlowOptions.MapDef<>(keyMapOptions));

			if (theKeyEquivalence instanceof Equivalence.ComparatorEquivalence) {
				Comparator<? super K> keySorting = ((Equivalence.ComparatorEquivalence<? super K>) theKeyEquivalence).comparator();
				theKeySet = new ObservableSortedSetImpl.ActiveDerivedSortedSet<K>(keyFlow, keySorting, until) {
					@Override
					protected DerivedElementHolder<K> createHolder(DerivedCollectionElement<K> el) {
						DerivedElementHolder<K> holder = super.createHolder(el);
						((GroupingManager<E, K, V>.GroupedElement) el).setKeyId(holder);
						return holder;
					}
				};
			} else {
				theKeySet = new ObservableSetImpl.ActiveDerivedSet<K>(keyFlow, until) {
					@Override
					protected DerivedElementHolder<K> createHolder(DerivedCollectionElement<K> el) {
						DerivedElementHolder<K> holder = super.createHolder(el);
						((GroupingManager<E, K, V>.GroupedElement) el).setKeyId(holder);
						return holder;
					}
				};
			}
			theGrouping.setAssembledKeys(theKeySet);
		}

		@Override
		public Transaction lock(boolean write, boolean structural, Object cause) {
			return theGrouping.lock(write, structural, cause);
		}

		@Override
		public TypeToken<K> getKeyType() {
			return theKeyType;
		}

		@Override
		public TypeToken<V> getValueType() {
			return theValueType;
		}

		@Override
		public TypeToken<ObservableMultiEntry<K, V>> getEntryType() {
			if (theEntryType == null)
				theEntryType = ObservableMultiMap.buildEntryType(theKeyType, theValueType);
			return theEntryType;
		}

		@Override
		public boolean isLockSupported() {
			return theGrouping.isLockSupported();
		}

		@Override
		public ObservableSet<K> keySet() {
			return theKeySet;
		}

		@Override
		public ObservableCollection<V> get(Object key) {
			if (key == null && !theKeyType.getRawType().isInstance(key))
				return ObservableCollection.of(theValueType);
			if (!theKeyEquivalence.isElement(key))
				return ObservableCollection.of(theValueType);
			return new Values((K) key);
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableMapEvent<? extends K, ? extends V>> action) {
			return theEventListeners.add(action, true)::run;
		}

		private class Values implements ObservableCollection<V> {
			private final K theKey;
			private CollectionElement<GroupingManager<E, K, V>.GroupedElement> theElement;

			public Values(K key) {
				theKey = key;
			}

			protected BetterSortedSet<DerivedCollectionElement<Map.Entry<K, V>>> getValues(boolean retry) {
				if (retry && (theElement == null || !theElement.getElementId().isPresent())) {
					CollectionElement<?> uniqueEl = theGrouping.getElement(new SimpleMapEntry<>(theKey, null));
					theElement = (CollectionElement<GroupingManager<E, K, V>.GroupedElement>) uniqueEl;
				}
				if (theElement == null)
					return null;
				else
					return theElement.get().getParentElements();
			}

			@Override
			public TypeToken<V> getType() {
				return theValueType;
			}

			@Override
			public Equivalence<? super V> equivalence() {
				return theValueEquivalence;
			}

			@Override
			public boolean isLockSupported() {
				return theGrouping.isLockSupported();
			}

			@Override
			public Transaction lock(boolean write, boolean structural, Object cause) {
				return theGrouping.lock(write, structural, cause);
			}

			@Override
			public boolean isContentControlled() {
				return true;
			}

			@Override
			public int size() {
				try (Transaction t = lock(false, false, null)) {
					BetterSortedSet<DerivedCollectionElement<Map.Entry<K, V>>> values = getValues(true);
					if (values == null)
						return 0;
					else
						return values.size();
				}
			}

			@Override
			public boolean isEmpty() {
				try (Transaction t = lock(false, false, null)) {
					BetterSortedSet<DerivedCollectionElement<Map.Entry<K, V>>> values = getValues(true);
					if (values == null)
						return true;
					else
						return values.isEmpty();
				}
			}

			@Override
			public int getElementsBefore(ElementId id) {
				try (Transaction t = lock(false, false, null)) {
					BetterSortedSet<DerivedCollectionElement<Map.Entry<K, V>>> values = getValues(false);
					if (values == null)
						throw new IllegalArgumentException("No such element");
					else
						return values.getElementsBefore(id);
				}
			}

			@Override
			public int getElementsAfter(ElementId id) {
				try (Transaction t = lock(false, false, null)) {
					BetterSortedSet<DerivedCollectionElement<Map.Entry<K, V>>> values = getValues(false);
					if (values == null)
						throw new IllegalArgumentException("No such element");
					else
						return values.getElementsAfter(id);
				}
			}

			@Override
			public long getStamp(boolean structuralOnly) {
				return theKeySet.getStamp(false);
			}

			@Override
			public CollectionElement<V> getElement(V value, boolean first) {
				try (Transaction t = lock(false, false, null)) {
					BetterSortedSet<DerivedCollectionElement<Map.Entry<K, V>>> values = getValues(true);
					if (values == null)
						return null;
					Comparable<DerivedCollectionElement<Map.Entry<K, V>>> finder = theEntries
						.getElementFinder(new SimpleMapEntry<>(theKey, value));
					if (finder != null) {
						return elementFor(values.search(finder, BetterSortedSet.SortedSearchFilter.OnlyMatch));
					} else {
						ElementId[] id = new ElementId[1];
						MutableElementSpliterator<DerivedCollectionElement<Map.Entry<K, V>>> spliter = values.spliterator(first);
						while (id[0] == null && spliter.forElement(el -> {
							if (theValueEquivalence.elementEquals(el.get().get().getValue(), value))
								id[0] = el.getElementId();
						}, first)) {}
						if (id[0] == null)
							return null;
						else
							return elementFor(values.getElement(id[0]));
					}
				}
			}

			@Override
			public CollectionElement<V> getElement(int index) {
				try (Transaction t = lock(false, false, null)) {
					BetterSortedSet<DerivedCollectionElement<Map.Entry<K, V>>> values = getValues(true);
					if (values == null)
						return null;
					else
						return elementFor(values.getElement(index));
				}
			}

			@Override
			public CollectionElement<V> getElement(ElementId id) {
				try (Transaction t = lock(false, false, null)) {
					BetterSortedSet<DerivedCollectionElement<Map.Entry<K, V>>> values = getValues(true);
					if (values == null)
						throw new IllegalArgumentException("No such element");
					else
						return elementFor(values.getElement(id));
				}
			}

			@Override
			public MutableCollectionElement<V> mutableElement(ElementId id) {
				try (Transaction t = lock(false, false, null)) {
					BetterSortedSet<DerivedCollectionElement<Map.Entry<K, V>>> values = getValues(true);
					if (values == null)
						throw new IllegalArgumentException("No such element");
					else
						return mutableElementFor(values.getElement(id));
				}
			}

			protected CollectionElement<V> elementFor(CollectionElement<DerivedCollectionElement<Map.Entry<K, V>>> entryEl) {
				if (entryEl == null)
					return null;
				return new CollectionElement<V>() {
					@Override
					public ElementId getElementId() {
						return entryEl.getElementId();
					}

					@Override
					public V get() {
						return entryEl.get().get().getValue();
					}
				};
			}

			protected MutableCollectionElement<V> mutableElementFor(CollectionElement<DerivedCollectionElement<Map.Entry<K, V>>> entryEl) {
				if (entryEl == null)
					return null;
				return new MutableCollectionElement<V>() {
					@Override
					public ElementId getElementId() {
						return entryEl.getElementId();
					}

					@Override
					public V get() {
						return entryEl.get().get().getValue();
					}

					@Override
					public String isEnabled() {
						return entryEl.get().isEnabled();
					}

					@Override
					public String isAcceptable(V value) {
						return entryEl.get().isAcceptable(new SimpleMapEntry<>(theKey, value));
					}

					@Override
					public void set(V value) throws UnsupportedOperationException, IllegalArgumentException {
						entryEl.get().set(new SimpleMapEntry<>(theKey, value));
					}

					@Override
					public String canRemove() {
						return entryEl.get().canRemove();
					}

					@Override
					public void remove() throws UnsupportedOperationException {
						entryEl.get().remove();
					}

					@Override
					public String canAdd(V value, boolean before) {
						return entryEl.get().canAdd(new SimpleMapEntry<>(theKey, value), before);
					}

					@Override
					public ElementId add(V value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
						try (Transaction t = lock(true, true, null)) {
							DerivedCollectionElement<Map.Entry<K, V>> el = entryEl.get().add(new SimpleMapEntry<>(theKey, value), before);
							if (el == null)
								return null;
							BetterSortedSet<DerivedCollectionElement<Map.Entry<K, V>>> values = getValues(true);
							if (values == null)
								throw new IllegalStateException("No element added");
							CollectionElement<DerivedCollectionElement<Map.Entry<K, V>>> entryEl2 = values.getElement(el, true);
							return entryEl2 == null ? null : entryEl2.getElementId();
						}
					}
				};
			}

			@Override
			public String canAdd(V value) {
				return theEntries.canAdd(new SimpleMapEntry<>(theKey, value));
			}

			@Override
			public CollectionElement<V> addElement(V value, boolean first) {
				try (Transaction t = lock(true, true, null)) {
					DerivedCollectionElement<Map.Entry<K, V>> el = theEntries.addElement(new SimpleMapEntry<>(theKey, value), first);
					if (el == null)
						return null;
					BetterSortedSet<DerivedCollectionElement<Map.Entry<K, V>>> values = getValues(true);
					if (values == null)
						throw new IllegalStateException("No element added");
					CollectionElement<DerivedCollectionElement<Map.Entry<K, V>>> entryEl = values.getElement(el, first);
					return elementFor(entryEl);
				}
			}

			@Override
			public void clear() {
				spliterator(false).forEachElementM(el -> {
					if (el.canRemove() == null)
						el.remove();
				}, false);
			}

			@Override
			public MutableElementSpliterator<V> spliterator(boolean fromStart) {
				try (Transaction t = lock(false, false, null)) {
					BetterSortedSet<DerivedCollectionElement<Map.Entry<K, V>>> values = getValues(true);
					if (values == null)
						return MutableElementSpliterator.empty();
					else
						return spliteratorFor(values.spliterator(fromStart));
				}
			}

			@Override
			public MutableElementSpliterator<V> spliterator(ElementId element, boolean asNext) {
				try (Transaction t = lock(false, false, null)) {
					BetterSortedSet<DerivedCollectionElement<Map.Entry<K, V>>> values = getValues(true);
					if (values == null)
						return MutableElementSpliterator.empty();
					else
						return spliteratorFor(values.spliterator(element, asNext));
				}
			}

			private MutableElementSpliterator<V> spliteratorFor(
				MutableElementSpliterator<DerivedCollectionElement<Entry<K, V>>> spliterator) {
				return new MutableElementSpliterator<V>() {
					@Override
					public int characteristics() {
						return spliterator.characteristics() & (~SORTED);
					}

					@Override
					public long estimateSize() {
						return spliterator.estimateSize();
					}

					@Override
					public long getExactSizeIfKnown() {
						return spliterator.getExactSizeIfKnown();
					}

					@Override
					public boolean forElement(Consumer<? super CollectionElement<V>> action, boolean forward) {
						return spliterator.forElement(el -> action.accept(elementFor(el)), forward);
					}

					@Override
					public void forEachElement(Consumer<? super CollectionElement<V>> action, boolean forward) {
						spliterator.forEachElement(el -> action.accept(elementFor(el)), forward);
					}

					@Override
					public boolean forElementM(Consumer<? super MutableCollectionElement<V>> action, boolean forward) {
						return spliterator.forElementM(el -> action.accept(mutableElementFor(el)), forward);
					}

					@Override
					public void forEachElementM(Consumer<? super MutableCollectionElement<V>> action, boolean forward) {
						spliterator.forEachElementM(el -> action.accept(mutableElementFor(el)), forward);
					}

					@Override
					public MutableElementSpliterator<V> trySplit() {
						MutableElementSpliterator<DerivedCollectionElement<Entry<K, V>>> split = spliterator.trySplit();
						return split == null ? null : spliteratorFor(split);
					}
				};
			}

			@Override
			public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends V>> observer) {
				return DerivedObservableMultiMap.this.onChange(mapEvent -> {
					if (!theKeyEquivalence.elementEquals(theKey, mapEvent.getKey()))
						return;
					observer.accept(mapEvent);
				});
			}
		}
	}
}
