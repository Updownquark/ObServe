package org.observe.assoc;

import java.util.Comparator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.function.Function;

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
import org.observe.collect.ObservableCollectionImpl.ActiveDerivedCollection.DerivedElementHolder;
import org.observe.collect.ObservableSet;
import org.observe.collect.ObservableSetImpl;
import org.observe.collect.ObservableSetImpl.UniqueManager;
import org.observe.collect.ObservableSortedSetImpl;
import org.qommons.Transaction;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.ListenerList;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.collect.SimpleMapEntry;
import org.qommons.tree.BetterTreeSet;

import com.google.common.reflect.TypeToken;

public class ObservableMultiMapImpl {
	private ObservableMultiMapImpl() {}

	static class KeyFlow<E, I, K, V> implements UniqueDataFlow<E, I, K> {
		private final CollectionDataFlow<?, Map.Entry<I, V>, Map.Entry<K, V>> theEntryFlow;
		private final TypeToken<K> theKeyType;
		private final Equivalence<? super K> theKeyEquivalence;

		KeyFlow(CollectionDataFlow<?, Entry<I, V>, Entry<K, V>> entryFlow, TypeToken<K> keyType, Equivalence<? super K> keyEquivalence) {
			theEntryFlow = entryFlow;
			theKeyType = keyType;
			theKeyEquivalence = keyEquivalence;
		}

		@Override
		public TypeToken<K> getTargetType() {
			return theKeyType;
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
		public <X> UniqueDataFlow<E, K, X> mapEquivalent(TypeToken<X> target, Function<? super K, ? extends X> map,
			Equivalence<? super X> equivalence, Consumer<MapOptions<K, X>> options) {
			TypeToken<Map.Entry<X, V>> mappedEntryType = ObservableMap.buildEntryType(target,
				(TypeToken<V>) theEntryFlow.getTargetType().resolveType(Map.Entry.class.getTypeParameters()[1]));
			Function<Map.Entry<K, V>, Map.Entry<X, V>> entryMap = entry -> new SimpleMapEntry<>(map.apply(entry.getKey()),
				entry.getValue());
			MapOptions<K, X> keyOptions = new MapOptions<>();
			options.accept(keyOptions);
			if (keyOptions.getElementReverse() != null)
				throw new UnsupportedOperationException("Element setting unsupported");
			Function<Map.Entry<X, V>, Map.Entry<K, V>> entryReverse;
			if (keyOptions.getReverse() != null)
				entryReverse = entry -> new SimpleMapEntry<>(keyOptions.getReverse().apply(entry.getKey()), entry.getValue());
				else
					entryReverse = null;
			CollectionDataFlow<?, Map.Entry<K, V>, Map.Entry<X, V>> mappedEntries = theEntryFlow.map(mappedEntryType, //
				entryMap, //
				mapOptions -> {
					mapOptions.cache(keyOptions.isCached())//
					.fireIfUnchanged(mapOptions.isFireIfUnchanged())//
					.reEvalOnUpdate(keyOptions.isReEvalOnUpdate())//
					.withReverse(entryReverse);
				});
			return new KeyFlow<>(mappedEntries, target, equivalence);
		}

		@Override
		public UniqueDataFlow<E, K, K> filterMod(Consumer<ModFilterBuilder<K>> options) {
			// ModFilterBuilder<Map.Entry<K, V>
			// TODO Auto-generated method stub
		}

		@Override
		public CollectionDataFlow<E, K, K> withEquivalence(Equivalence<? super K> equivalence) {
			throw new IllegalStateException("Only equivalent unique operations are permitted");
		}

		@Override
		public <X> CollectionDataFlow<E, K, X> map(TypeToken<X> target, Function<? super K, ? extends X> map,
			Consumer<MapOptions<K, X>> options) {
			throw new IllegalStateException("Only equivalent unique operations are permitted");
		}

		@Override
		public <X> CollectionDataFlow<E, K, X> combine(TypeToken<X> targetType,
			Function<CombinationPrecursor<K, X>, CombinedFlowDef<K, X>> combination) {
			throw new IllegalStateException("Only equivalent unique operations are permitted");
		}

		@Override
		public <X> CollectionDataFlow<E, ?, X> flatMap(TypeToken<X> target,
			Function<? super K, ? extends CollectionDataFlow<?, ?, ? extends X>> map) {
			throw new IllegalStateException("Only equivalent unique operations are permitted");
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
			throw new IllegalStateException("Only equivalent unique operations are permitted");
		}

		@Override
		public <K2> MultiMapFlow<E, K2, K> groupBy(TypeToken<K2> keyType, Function<? super K, ? extends K2> keyMap,
			Consumer<GroupingOptions> options) {
			throw new IllegalStateException("Only equivalent unique operations are permitted");
		}

		@Override
		public <K2> SortedMultiMapFlow<E, K2, K> groupBy(TypeToken<K2> keyType, Function<? super K, ? extends K2> keyMap,
			Comparator<? super K2> keyCompare, Consumer<GroupingOptions> options) {
			throw new IllegalStateException("Only equivalent unique operations are permitted");
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

		void withHolder(K key, ElementId holder) {
			getElement(new SimpleMapEntry)
			// TODO Auto-generated method stub

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

		private final ListenerList<Consumer<? super ObservableMapEvent<? extends K, ? extends V>>> theEventListeners;
		private final GroupingManager<E, K, V> theGrouping;
		private final ObservableSet<K> theKeySet;

		public DerivedObservableMultiMap(CollectionDataFlow<E, ?, Map.Entry<K, V>> entryFlow, //
			TypeToken<K> keyType, TypeToken<V> valueType, //
			Equivalence<? super K> keyEquivalence, Equivalence<? super V> valueEquivalence, //
			Comparator<? super K> keySorting, boolean uniqueValues, Comparator<? super V> valueSorting, //
			FlowOptions.GroupingDef options, Observable<?> until) {
			theKeyType = keyType;
			theValueType = valueType;
			theKeyEquivalence = keyEquivalence;
			theValueEquivalence = valueEquivalence;

			theEventListeners = new ListenerList<>(null);
			theGrouping = new GroupingManager<>(entryFlow, keyType, valueType, options, //
				event -> theEventListeners.forEach(listener -> listener.accept(event)));
			FlowOptions.MapOptions<Map.Entry<K, V>, K> keyMapOptions = new FlowOptions.MapOptions<>();
			keyMapOptions.withReverse(k -> new SimpleMapEntry<>(k, null)).reEvalOnUpdate(!options.isStaticCategories());
			ActiveCollectionManager<E, Map.Entry<K, V>, K> keyFlow;
			keyFlow = new ObservableCollectionDataFlowImpl.ActiveMappedCollectionManager<>(theGrouping, theKeyType, Map.Entry::getKey, //
				new FlowOptions.MapDef<>(keyMapOptions));

			if (keySorting != null)
				theKeySet = new ObservableSortedSetImpl.ActiveDerivedSortedSet<K>(keyFlow, keySorting, until) {
				@Override
				protected DerivedElementHolder<K> createHolder(DerivedCollectionElement<K> el) {
					DerivedElementHolder<K> holder = super.createHolder(el);
					theGrouping.withHolder(el.get(), holder);
				}
				};
			else
				theKeySet = new ObservableSetImpl.ActiveDerivedSet<>(keyFlow, until);
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

			public Values(K key) {
				theKey = key;
			}
		}
	}
}
