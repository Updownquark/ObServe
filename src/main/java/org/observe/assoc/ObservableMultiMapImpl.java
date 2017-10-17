package org.observe.assoc;

import java.util.Comparator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.Observable;
import org.observe.assoc.ObservableMultiMap.MultiMapFlow;
import org.observe.assoc.ObservableSortedMultiMap.SortedMultiMapFlow;
import org.observe.collect.Combination.CombinationPrecursor;
import org.observe.collect.Combination.CombinedFlowDef;
import org.observe.collect.Equivalence;
import org.observe.collect.FlowOptions;
import org.observe.collect.FlowOptions.GroupingOptions;
import org.observe.collect.FlowOptions.MapOptions;
import org.observe.collect.FlowOptions.UniqueOptions;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollection.ModFilterBuilder;
import org.observe.collect.ObservableCollection.UniqueDataFlow;
import org.observe.collect.ObservableCollection.UniqueSortedDataFlow;
import org.observe.collect.ObservableCollectionDataFlowImpl;
import org.observe.collect.ObservableCollectionDataFlowImpl.ActiveCollectionManager;
import org.observe.collect.ObservableCollectionDataFlowImpl.CollectionElementListener;
import org.observe.collect.ObservableCollectionDataFlowImpl.DerivedCollectionElement;
import org.observe.collect.ObservableCollectionDataFlowImpl.ElementAccepter;
import org.observe.collect.ObservableCollectionDataFlowImpl.PassiveCollectionManager;
import org.observe.collect.ObservableSet;
import org.observe.collect.ObservableSetImpl;
import org.observe.collect.ObservableSetImpl.UniqueManager;
import org.observe.util.WeakListening;
import org.qommons.Transaction;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.collect.SimpleMapEntry;
import org.qommons.tree.BinaryTreeNode;

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

	static class GroupingManager<E, K, V> extends ObservableSetImpl.UniqueManager<E, K> {
		private final ActiveCollectionManager<E, ?, Map.Entry<K, V>> theParent;
		private final TypeToken<V> theValueType;
		private final Equivalence<? super K> theKeyEquivalence;
		private final Equivalence<? super V> theValueEquivalence;
		private final FlowOptions.GroupingDef theOptions;

		public GroupingManager(ActiveCollectionManager<E, ?, Map.Entry<K, V>> parent, //
			TypeToken<K> keyType, TypeToken<V> valueType, //
			Equivalence<? super K> keyEquivalence, Equivalence<? super V> valueEquivalence, //
			FlowOptions.GroupingDef options) {
			super(map(parent, keyType, options), options.isUsingFirst(), options.isPreservingSourceOrder());

			theParent = parent;
			theValueType = valueType;
			theKeyEquivalence = keyEquivalence;
			theValueEquivalence = valueEquivalence;
			theOptions = options;
		}

		private static <E, K, V> ActiveCollectionManager<E, ?, K> map(ActiveCollectionManager<E, ?, Map.Entry<K, V>> parent,
			TypeToken<K> keyType, FlowOptions.GroupingDef options) {
			FlowOptions.MapOptions<Map.Entry<K, V>, K> mapOptions = new FlowOptions.MapOptions<Map.Entry<K, V>, K>()//
				.cache(!options.isStaticCategories())// Don't cache if the keys are static
				.reEvalOnUpdate(!options.isStaticCategories());
			return new ObservableCollectionDataFlowImpl.ActiveMappedCollectionManager<>(parent, keyType, Map.Entry::getKey, //
				new FlowOptions.MapDef<>(mapOptions));
		}

		@Override
		protected UniqueManager<E, K>.UniqueElement createUniqueElement(K value) {
			return new GroupedElement(value);
		}

		protected class GroupedElement extends UniqueElement implements ActiveCollectionManager<E, Object, V> {
			private ElementAccepter<V> theValueElAccepter;
			private final NavigableMap<ElementId, ValueElement> theValueElements;

			protected GroupedElement(K key) {
				super(key);
				theValueElements = new TreeMap<>();
			}

			@Override
			protected void addParent(DerivedCollectionElement<K> parentEl, Object cause) {
				super.addParent(parentEl, cause);
			}

			@Override
			protected void parentUpdated(CollectionElement<DerivedCollectionElement<K>> parentEl, K oldValue, K newValue, Object cause) {
				super.parentUpdated(parentEl, oldValue, newValue, cause);
			}

			@Override
			protected void parentRemoved(CollectionElement<DerivedCollectionElement<K>> parentEl, K value, Object cause) {
				super.parentRemoved(parentEl, value, cause);
			}

			@Override
			public TypeToken<V> getTargetType() {
				return theValueType;
			}

			@Override
			public Equivalence<? super V> equivalence() {
				return theValueEquivalence;
			}

			@Override
			public Transaction lock(boolean write, boolean structural, Object cause) {
				return GroupingManager.this.lock(write, structural, cause);
			}

			@Override
			public boolean isContentControlled() {
				return true;
			}

			@Override
			public Comparable<DerivedCollectionElement<V>> getElementFinder(V value) {
				// TODO Auto-generated method stub
			}

			@Override
			public String canAdd(V toAdd) {
				return GroupingManager.this.theParent.canAdd(new SimpleMapEntry<>(get(), toAdd));
			}

			@Override
			public DerivedCollectionElement<V> addElement(V value, boolean first) {
				DerivedCollectionElement<Map.Entry<K, V>> addedEl = GroupingManager.this.theParent
					.addElement(new SimpleMapEntry<>(get(), value), first);
				return valueElement(addedEl);
			}

			@Override
			public boolean clear() {
				if (canRemove() == null) {
					remove();
					return true;
				} else
					return false;
			}

			@Override
			public void begin(ElementAccepter<V> onElement, WeakListening listening) {
				theValueElAccepter = onElement;
				for (ValueElement valueEl : theValueElements.values())
					onElement.accept(valueEl, null);
			}

			private ValueElement valueElement(DerivedCollectionElement<Map.Entry<K, V>> entryEl) {
				if (entryEl == null)
					return null;
				BinaryTreeNode<DerivedCollectionElement<K>> found = getParentElements().search(keyEl -> {
					ObservableCollectionDataFlowImpl.ActiveMappedCollectionManager<E, Map.Entry<K, V>, K>.MappedElement mappedEl;
					mappedEl = (ObservableCollectionDataFlowImpl.ActiveMappedCollectionManager<E, Map.Entry<K, V>, K>.MappedElement) keyEl;
					return entryEl.compareTo(mappedEl.getParentEl());
				}, BetterSortedSet.SortedSearchFilter.OnlyMatch);
				if (found == null)
					return null;
				return theValueElements.get(found.getElementId());
			}

			private class ValueElement implements DerivedCollectionElement<V> {
				private final DerivedCollectionElement<Map.Entry<K, V>> theParentEl;

				ValueElement(DerivedCollectionElement<Entry<K, V>> parentEl) {
					theParentEl = parentEl;
				}

				@Override
				public int compareTo(DerivedCollectionElement<V> o) {
					return theParentEl.compareTo(((ValueElement) o).theParentEl);
				}

				@Override
				public void setListener(CollectionElementListener<V> listener) {
					// TODO Auto-generated method stub

				}

				@Override
				public V get() {
					return theParentEl.get().getValue();
				}

				@Override
				public String isEnabled() {
					return theParentEl.isEnabled();
				}

				@Override
				public String isAcceptable(V value) {
					return theParentEl.isAcceptable(new SimpleMapEntry<>(GroupedElement.this.get(), value));
				}

				@Override
				public void set(V value) throws UnsupportedOperationException, IllegalArgumentException {
					theParentEl.set(new SimpleMapEntry<>(GroupedElement.this.get(), value));
				}

				@Override
				public String canRemove() {
					return theParentEl.canRemove();
				}

				@Override
				public void remove() throws UnsupportedOperationException {
					theParentEl.remove();
				}

				@Override
				public String canAdd(V value, boolean before) {
					return theParentEl.canAdd(new SimpleMapEntry<>(GroupedElement.this.get(), value), before);
				}

				@Override
				public DerivedCollectionElement<V> add(V value, boolean before)
					throws UnsupportedOperationException, IllegalArgumentException {
					DerivedCollectionElement<Map.Entry<K, V>> addedEl = theParentEl
						.add(new SimpleMapEntry<>(GroupedElement.this.get(), value), before);
					return valueElement(addedEl);
				}
			}
		}
	}
}
