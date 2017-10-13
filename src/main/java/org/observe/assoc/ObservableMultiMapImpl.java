package org.observe.assoc;

import java.util.Comparator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.Observable;
import org.observe.assoc.ObservableMultiMap.MultiMapFlow;
import org.observe.assoc.ObservableSortedMultiMap.SortedMultiMapFlow;
import org.observe.collect.Equivalence;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableSet;
import org.observe.collect.Combination.CombinationPrecursor;
import org.observe.collect.Combination.CombinedFlowDef;
import org.observe.collect.FlowOptions.GroupingOptions;
import org.observe.collect.FlowOptions.MapOptions;
import org.observe.collect.FlowOptions.UniqueOptions;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollection.ModFilterBuilder;
import org.observe.collect.ObservableCollection.UniqueDataFlow;
import org.observe.collect.ObservableCollection.UniqueSortedDataFlow;
import org.observe.collect.ObservableCollectionDataFlowImpl.ActiveCollectionManager;
import org.observe.collect.ObservableCollectionDataFlowImpl.PassiveCollectionManager;

import com.google.common.reflect.TypeToken;

public class ObservableMultiMapImpl {
	private ObservableMultiMapImpl() {}

	static class KeyFlow<E, I, K, V> implements UniqueDataFlow<E, I, K> {
		private final CollectionDataFlow<?, Map.Entry<I, V>, Map.Entry<K, V>> theEntryFlow;
		private final TypeToken<K> theKeyType;

		KeyFlow(CollectionDataFlow<?, Entry<I, V>, Entry<K, V>> entryFlow, TypeToken<K> keyType) {
			theEntryFlow = entryFlow;
			theKeyType = keyType;
		}

		@Override
		public TypeToken<K> getTargetType() {
			return theKeyType;
		}

		@Override
		public UniqueDataFlow<E, K, K> filter(Function<? super K, String> filter) {
			return new KeyFlow<>(theEntryFlow.filter(entry -> filter.apply(entry.getKey())), theKeyType);
		}

		@Override
		public UniqueDataFlow<E, K, K> filterStatic(Function<? super K, String> filter) {
			return new KeyFlow<>(theEntryFlow.filterStatic(entry -> filter.apply(entry.getKey())), theKeyType);
		}

		@Override
		public <X> UniqueDataFlow<E, K, K> whereContained(ObservableCollection<X> other, boolean include) {
			// TODO Auto-generated method stub
		}

		@Override
		public UniqueDataFlow<E, K, K> refresh(Observable<?> refresh) {
			return new KeyFlow<>(theEntryFlow.refresh(refresh), theKeyType);
		}

		@Override
		public UniqueDataFlow<E, K, K> refreshEach(Function<? super K, ? extends Observable<?>> refresh) {
			return new KeyFlow<>(theEntryFlow.refreshEach(entry -> refresh.apply(entry.getKey())), theKeyType);
		}

		@Override
		public <X> UniqueDataFlow<E, K, X> mapEquivalent(TypeToken<X> target, Function<? super K, ? extends X> map,
			Function<? super X, ? extends K> reverse, Consumer<MapOptions<K, X>> options) {
			// TODO Auto-generated method stub
		}

		@Override
		public UniqueDataFlow<E, K, K> filterMod(Consumer<ModFilterBuilder<K>> options) {
			ModFilterBuilder<Map.Entry<K, V>
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public <X> UniqueDataFlow<E, K, X> mapEquivalent(TypeToken<X> target, Function<? super K, ? extends X> map,
			Equivalence<? super X> equivalence) {
			throw new IllegalStateException("Only equivalent unique operations are permitted");
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
		public <X> CollectionDataFlow<E, ?, X> flatMapF(TypeToken<X> target,
			Function<? super K, ? extends CollectionDataFlow<?, ?, ? extends X>> map) {
			throw new IllegalStateException("Only equivalent unique operations are permitted");
		}

		@Override
		public CollectionDataFlow<E, K, K> sorted(Comparator<? super K> compare) {
			throw new IllegalStateException("Only equivalent unique operations are permitted");
		}

		@Override
		public UniqueDataFlow<E, K, K> distinct(Consumer<UniqueOptions> options) {
			throw new IllegalStateException("Only equivalent unique operations are permitted");
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
}
