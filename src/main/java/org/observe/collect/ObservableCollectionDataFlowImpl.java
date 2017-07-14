package org.observe.collect;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Subscription;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollection.CombinedCollectionBuilder;
import org.observe.collect.ObservableCollection.CombinedValues;
import org.observe.collect.ObservableCollection.ElementSetter;
import org.observe.collect.ObservableCollection.MappedCollectionBuilder;
import org.observe.collect.ObservableCollection.ModFilterBuilder;
import org.observe.collect.ObservableCollection.StdMsg;
import org.observe.collect.ObservableCollection.UniqueDataFlow;
import org.observe.collect.ObservableCollection.UniqueMappedCollectionBuilder;
import org.observe.collect.ObservableCollection.UniqueModFilterBuilder;
import org.observe.collect.ObservableCollection.UniqueSortedDataFlow;
import org.observe.collect.ObservableCollection.UniqueSortedMappedCollectionBuilder;
import org.observe.collect.ObservableCollection.UniqueSortedModFilterBuilder;
import org.observe.collect.ObservableCollectionImpl.DerivedCollection;
import org.qommons.Ternian;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.UpdatableMap;
import org.qommons.tree.CountedRedBlackNode.DefaultNode;
import org.qommons.tree.CountedRedBlackNode.DefaultTreeSet;
import org.qommons.value.Value;

import com.google.common.reflect.TypeToken;

public class ObservableCollectionDataFlowImpl {
	private ObservableCollectionDataFlowImpl() {}

	/**
	 * Used in mapping/filtering collection data
	 *
	 * @param <E> The source type
	 * @param <T> The destination type
	 */
	public static class FilterMapResult<E, T> {
		public E source;
		public T result;
		public String error;

		public FilterMapResult() {}

		public FilterMapResult(E src) {
			source = src;
		}
	}

	public static abstract class AbstractDataFlow<E, I, T> implements CollectionDataFlow<E, I, T> {
		private final ObservableCollection<E> theSource;
		private final CollectionDataFlow<E, ?, I> theParent;
		private final TypeToken<T> theTargetType;

		protected AbstractDataFlow(ObservableCollection<E> source, CollectionDataFlow<E, ?, I> parent, TypeToken<T> targetType) {
			theSource = source;
			theParent = parent;
			theTargetType = targetType;
		}

		protected ObservableCollection<E> getSource() {
			return theSource;
		}

		protected CollectionDataFlow<E, ?, I> getParent() {
			return theParent;
		}

		@Override
		public TypeToken<T> getTargetType() {
			return theTargetType;
		}

		@Override
		public CollectionDataFlow<E, T, T> filter(Function<? super T, String> filter, boolean filterNulls) {
			return new FilterOp<>(theSource, this, filter, filterNulls);
		}

		@Override
		public CollectionDataFlow<E, T, T> filterStatic(Function<? super T, String> filter, boolean filterNulls) {
			return new StaticFilterOp<>(theSource, this, filter, filterNulls);
		}

		@Override
		public CollectionDataFlow<E, T, T> withEquivalence(Equivalence<? super T> equivalence) {
			return new EquivalenceSwitchOp<>(theSource, this, equivalence);
		}

		@Override
		public CollectionDataFlow<E, T, T> refresh(Observable<?> refresh) {
			return new RefreshOp<>(theSource, this, refresh);
		}

		@Override
		public CollectionDataFlow<E, T, T> refreshEach(Function<? super T, ? extends Observable<?>> refresh) {
			return new ElementRefreshOp<>(theSource, this, refresh);
		}

		@Override
		public <X> MappedCollectionBuilder<E, T, X> map(TypeToken<X> target) {
			return new MappedCollectionBuilder<>(theSource, this, target);
		}

		@Override
		public <V, X> ObservableCollection.CombinedCollectionBuilder2<E, T, V, X> combineWith(ObservableValue<V> value,
			boolean combineNulls, TypeToken<X> target) {
			return new ObservableCollection.CombinedCollectionBuilder2<>(theSource, this, target, value, Ternian.of(combineNulls));
		}

		@Override
		public ModFilterBuilder<E, T> filterModification() {
			return new ModFilterBuilder<>(theSource, this);
		}

		@Override
		public CollectionDataFlow<E, T, T> sorted(Comparator<? super T> compare) {
			return new SortedDataFlow<>(theSource, this, compare);
		}

		@Override
		public UniqueDataFlow<E, T, T> unique(boolean alwaysUseFirst) {
			return new UniqueOp<>(theSource, this, alwaysUseFirst);
		}

		@Override
		public UniqueSortedDataFlow<E, T, T> uniqueSorted(Comparator<? super T> compare, boolean alwaysUseFirst) {
			return new UniqueSortedDataFlowImpl<>(theSource, this, compare, alwaysUseFirst);
		}

		@Override
		public ObservableCollection<T> collect(Observable<?> until) {
			return new DerivedCollection<>(getSource(), manageCollection(), until);
		}
	}

	public static class UniqueDataFlowWrapper<E, T> extends AbstractDataFlow<E, T, T> implements UniqueDataFlow<E, T, T> {
		protected UniqueDataFlowWrapper(ObservableCollection<E> source, CollectionDataFlow<E, ?, T> parent) {
			super(source, parent, parent.getTargetType());
		}

		@Override
		public UniqueDataFlow<E, T, T> filter(Function<? super T, String> filter) {
			return (UniqueDataFlow<E, T, T>) super.filter(filter);
		}

		@Override
		public UniqueDataFlow<E, T, T> filter(Function<? super T, String> filter, boolean filterNulls) {
			return new UniqueDataFlowWrapper<>(getSource(), getParent().filter(filter, filterNulls));
		}

		@Override
		public UniqueDataFlow<E, T, T> filterStatic(Function<? super T, String> filter) {
			return (UniqueDataFlow<E, T, T>) super.filterStatic(filter);
		}

		@Override
		public UniqueDataFlow<E, T, T> filterStatic(Function<? super T, String> filter, boolean filterNulls) {
			return new UniqueDataFlowWrapper<>(getSource(), getParent().filterStatic(filter, filterNulls));
		}

		@Override
		public <X> UniqueMappedCollectionBuilder<E, T, X> mapEquivalent(TypeToken<X> target) {
			return new UniqueMappedCollectionBuilder<>(getSource(), this, target);
		}

		@Override
		public UniqueDataFlow<E, T, T> refresh(Observable<?> refresh) {
			return new UniqueDataFlowWrapper<>(getSource(), getParent().refresh(refresh));
		}

		@Override
		public UniqueDataFlow<E, T, T> refreshEach(Function<? super T, ? extends Observable<?>> refresh) {
			return new UniqueDataFlowWrapper<>(getSource(), getParent().refreshEach(refresh));
		}

		@Override
		public UniqueModFilterBuilder<E, T> filterModification() {
			return new UniqueModFilterBuilder<>(getSource(), this);
		}

		@Override
		public CollectionManager<E, ?, T> manageCollection() {
			return getParent().manageCollection();
		}

		@Override
		public ObservableSet<T> collect(Observable<?> until) {
			return new ObservableSetImpl.DerivedSet<>(getSource(), manageCollection(), until);
		}
	}

	public static class UniqueOp<E, T> extends UniqueDataFlowWrapper<E, T> {
		private final boolean isAlwaysUsingFirst;

		protected UniqueOp(ObservableCollection<E> source, AbstractDataFlow<E, ?, T> parent, boolean alwaysUseFirst) {
			super(source, parent);
			isAlwaysUsingFirst = alwaysUseFirst;
		}

		@Override
		public CollectionManager<E, ?, T> manageCollection() {
			return new UniqueManager<>(getParent().manageCollection(), isAlwaysUsingFirst);
		}
	}

	public static class InitialElementsDataFlow<E> extends AbstractDataFlow<E, E, E> {
		private final Collection<? extends E> theInitialValues;

		public InitialElementsDataFlow(ObservableCollection<E> source, CollectionDataFlow<E, ?, E> parent, TypeToken<E> targetType,
			Collection<? extends E> initialValues) {
			super(source, parent, targetType);
			theInitialValues = initialValues;
		}

		protected Collection<? extends E> getInitialValues() {
			return theInitialValues;
		}

		@Override
		public CollectionManager<E, ?, E> manageCollection() {
			return getParent().manageCollection();
		}

		@Override
		public ObservableCollection<E> collect(Observable<?> until) {
			ObservableCollection<E> collected = super.collect(until);
			getSource().addAll(theInitialValues);
			return collected;
		}
	}

	public static class SortedDataFlow<E, T> extends AbstractDataFlow<E, T, T> {
		private final Comparator<? super T> theCompare;

		protected SortedDataFlow(ObservableCollection<E> source, AbstractDataFlow<E, ?, T> parent, Comparator<? super T> compare) {
			super(source, parent, parent.getTargetType());
			theCompare = compare;
		}

		protected Comparator<? super T> getCompare() {
			return theCompare;
		}

		@Override
		public CollectionManager<E, ?, T> manageCollection() {
			return new SortedManager<>(getParent().manageCollection(), theCompare);
		}

		@Override
		public ObservableCollection<T> collect(Observable<?> until) {
			return new DerivedCollection<>(getSource(), manageCollection(), until);
		}
	}

	public static class UniqueSortedDataFlowWrapper<E, T> extends UniqueDataFlowWrapper<E, T> implements UniqueSortedDataFlow<E, T, T> {
		private final Comparator<? super T> theCompare;

		protected UniqueSortedDataFlowWrapper(ObservableCollection<E> source, AbstractDataFlow<E, ?, T> parent,
			Comparator<? super T> compare) {
			super(source, parent);
			theCompare = compare;
		}

		@Override
		public Comparator<? super T> comparator() {
			return theCompare;
		}

		@Override
		public UniqueSortedDataFlow<E, T, T> filter(Function<? super T, String> filter) {
			return (UniqueSortedDataFlow<E, T, T>) super.filter(filter);
		}

		@Override
		public UniqueSortedDataFlow<E, T, T> filter(Function<? super T, String> filter, boolean filterNulls) {
			return new UniqueSortedDataFlowWrapper<>(getSource(), (AbstractDataFlow<E, ?, T>) getParent().filter(filter, filterNulls),
				theCompare);
		}

		@Override
		public UniqueSortedDataFlow<E, T, T> filterStatic(Function<? super T, String> filter) {
			return (UniqueSortedDataFlow<E, T, T>) super.filterStatic(filter);
		}

		@Override
		public UniqueSortedDataFlow<E, T, T> filterStatic(Function<? super T, String> filter, boolean filterNulls) {
			return new UniqueSortedDataFlowWrapper<>(getSource(), (AbstractDataFlow<E, ?, T>) getParent().filterStatic(filter, filterNulls),
				theCompare);
		}

		@Override
		public <X> UniqueSortedMappedCollectionBuilder<E, T, X> mapEquivalent(TypeToken<X> target) {
			return new UniqueSortedMappedCollectionBuilder<>(getSource(), this, target);
		}

		@Override
		public UniqueSortedDataFlow<E, T, T> refresh(Observable<?> refresh) {
			return new UniqueSortedDataFlowWrapper<>(getSource(), (AbstractDataFlow<E, ?, T>) getParent().refresh(refresh),
				theCompare);
		}

		@Override
		public UniqueSortedDataFlow<E, T, T> refreshEach(Function<? super T, ? extends Observable<?>> refresh) {
			return new UniqueSortedDataFlowWrapper<>(getSource(), (AbstractDataFlow<E, ?, T>) getParent().refreshEach(refresh),
				theCompare);
		}

		@Override
		public UniqueSortedModFilterBuilder<E, T> filterModification() {
			return new UniqueSortedModFilterBuilder<>(getSource(), this);
		}

		@Override
		public CollectionManager<E, ?, T> manageCollection() {
			return getParent().manageCollection();
		}

		@Override
		public ObservableSortedSet<T> collect(Observable<?> until) {
			return new ObservableSortedSetImpl.DerivedSortedSet<>(getSource(), manageCollection(), theCompare, until);
		}
	}

	public static class UniqueSortedDataFlowImpl<E, T> extends UniqueSortedDataFlowWrapper<E, T> {
		private final boolean isAlwaysUsingFirst;

		protected UniqueSortedDataFlowImpl(ObservableCollection<E> source, AbstractDataFlow<E, ?, T> parent, Comparator<? super T> compare,
			boolean alwaysUseFirst) {
			super(source, parent, compare);
			isAlwaysUsingFirst = alwaysUseFirst;
		}

		@Override
		public CollectionManager<E, ?, T> manageCollection() {
			return new UniqueManager<>(new SortedManager<>(getParent().manageCollection(), comparator()), isAlwaysUsingFirst);
		}
	}

	public static class BaseCollectionDataFlow<E> extends AbstractDataFlow<E, E, E> {

		protected BaseCollectionDataFlow(ObservableCollection<E> source) {
			super(source, null, source.getType());
		}

		@Override
		protected ObservableCollection<E> getSource() {
			return getSource();
		}

		@Override
		public AbstractCollectionManager<E, ?, E> manageCollection() {
			return new BaseCollectionManager<>(getSource().getType(), getSource().equivalence(), getSource().isLockSupported());
		}

		@Override
		public ObservableCollection<E> collect(Observable<?> until) {
			if (until == Observable.empty)
				return getSource();
			else
				return new DerivedCollection<>(getSource(), manageCollection(), until);
		}
	}

	public static class FilterOp<E, T> extends AbstractDataFlow<E, T, T> {
		private final Function<? super T, String> theFilter;
		private final boolean areNullsFiltered;

		protected FilterOp(ObservableCollection<E> source, AbstractDataFlow<E, ?, T> parent, Function<? super T, String> filter,
			boolean filterNulls) {
			super(source, parent, parent.getTargetType());
			theFilter = filter;
			areNullsFiltered = filterNulls;
		}

		@Override
		public AbstractCollectionManager<E, ?, T> manageCollection() {
			return new FilteredCollectionManager<>(getParent().manageCollection(), theFilter, areNullsFiltered);
		}
	}

	public static class StaticFilterOp<E, T> extends AbstractDataFlow<E, T, T> {
		private final Function<? super T, String> theFilter;
		private final boolean areNullsFiltered;

		protected StaticFilterOp(ObservableCollection<E> source, AbstractDataFlow<E, ?, T> parent, Function<? super T, String> filter,
			boolean filterNulls) {
			super(source, parent, parent.getTargetType());
			theFilter = filter;
			areNullsFiltered = filterNulls;
		}

		@Override
		public AbstractCollectionManager<E, ?, T> manageCollection() {
			return new StaticFilteredCollectionManager<>(getParent().manageCollection(), theFilter, areNullsFiltered);
		}
	}

	public static class EquivalenceSwitchOp<E, T> extends AbstractDataFlow<E, T, T> {
		private final Equivalence<? super T> theEquivalence;

		protected EquivalenceSwitchOp(ObservableCollection<E> source, AbstractDataFlow<E, ?, T> parent,
			Equivalence<? super T> equivalence) {
			super(source, parent, parent.getTargetType());
			theEquivalence = equivalence;
		}

		@Override
		public AbstractCollectionManager<E, ?, T> manageCollection() {
			class EquivalenceSwitchedCollectionManager extends NonMappingCollectionManager<E, T> {
				EquivalenceSwitchedCollectionManager() {
					super(EquivalenceSwitchOp.this.getParent().manageCollection());
				}

				@Override
				public CollectionElementManager<E, ?, T> createElement(ElementId id, E init, Object cause) {
					return getParent().createElement(id, init, cause);
				}

				@Override
				protected void begin() {}

				@Override
				public Equivalence<? super T> equivalence() {
					return theEquivalence;
				}
			}
			return new EquivalenceSwitchedCollectionManager();
		}
	}

	public static class MapOp<E, I, T> extends AbstractDataFlow<E, I, T> {
		private final Function<? super I, ? extends T> theMap;
		private final boolean areNullsMapped;
		private final Function<? super T, ? extends I> theReverse;
		private final ElementSetter<? super I, ? super T> theElementReverse;
		private final boolean areNullsReversed;
		private final boolean reEvalOnUpdate;
		private final boolean fireIfUnchanged;
		private final boolean isCached;

		protected MapOp(ObservableCollection<E> source, AbstractDataFlow<E, ?, I> parent, TypeToken<T> target,
			Function<? super I, ? extends T> map, boolean mapNulls, Function<? super T, ? extends I> reverse,
			ElementSetter<? super I, ? super T> elementReverse, boolean reverseNulls, boolean reEvalOnUpdate, boolean fireIfUnchanged,
			boolean cached) {
			super(source, parent, target);
			theMap = map;
			areNullsMapped = mapNulls;
			theReverse = reverse;
			theElementReverse = elementReverse;
			areNullsReversed = reverseNulls;
			this.reEvalOnUpdate = reEvalOnUpdate;
			this.fireIfUnchanged = fireIfUnchanged;
			isCached = cached;
		}

		@Override
		public CollectionManager<E, ?, T> manageCollection() {
			return new MappedCollectionManager<>(getParent().manageCollection(), getTargetType(), theMap, areNullsMapped, theReverse,
				theElementReverse, areNullsReversed, reEvalOnUpdate, fireIfUnchanged, isCached);
		}
	}

	public static class UniqueMapOp<E, I, T> extends MapOp<E, I, T> implements UniqueDataFlow<E, I, T> {
		protected UniqueMapOp(ObservableCollection<E> source, AbstractDataFlow<E, ?, I> parent,
			TypeToken<T> target, Function<? super I, ? extends T> map, boolean mapNulls, Function<? super T, ? extends I> reverse,
			ElementSetter<? super I, ? super T> elementReverse, boolean reverseNulls, boolean reEvalOnUpdate, boolean fireIfUnchanged,
			boolean isCached) {
			super(source, parent, target, map, mapNulls, reverse, elementReverse, reverseNulls, reEvalOnUpdate, fireIfUnchanged, isCached);
		}

		@Override
		public UniqueDataFlow<E, T, T> filter(Function<? super T, String> filter) {
			return (UniqueDataFlow<E, T, T>) super.filter(filter);
		}

		@Override
		public UniqueDataFlow<E, T, T> filter(Function<? super T, String> filter, boolean filterNulls) {
			return new UniqueDataFlowWrapper<>(getSource(), super.filter(filter, filterNulls));
		}

		@Override
		public UniqueDataFlow<E, T, T> filterStatic(Function<? super T, String> filter) {
			return (UniqueDataFlow<E, T, T>) super.filterStatic(filter);
		}

		@Override
		public UniqueDataFlow<E, T, T> filterStatic(Function<? super T, String> filter, boolean filterNulls) {
			return new UniqueDataFlowWrapper<>(getSource(), super.filterStatic(filter, filterNulls));
		}

		@Override
		public <X> UniqueMappedCollectionBuilder<E, T, X> mapEquivalent(TypeToken<X> target) {
			return new UniqueMappedCollectionBuilder<>(getSource(), this, target);
		}

		@Override
		public UniqueDataFlow<E, T, T> refresh(Observable<?> refresh) {
			return new UniqueDataFlowWrapper<>(getSource(), super.refresh(refresh));
		}

		@Override
		public UniqueDataFlow<E, T, T> refreshEach(Function<? super T, ? extends Observable<?>> refresh) {
			return new UniqueDataFlowWrapper<>(getSource(), super.refreshEach(refresh));
		}

		@Override
		public UniqueModFilterBuilder<E, T> filterModification() {
			return new UniqueModFilterBuilder<>(getSource(), this);
		}

		@Override
		public ObservableSet<T> collect(Observable<?> until) {
			return new ObservableSetImpl.DerivedSet<>(getSource(), manageCollection(), until);
		}
	}

	public static class UniqueSortedMapOp<E, I, T> extends UniqueMapOp<E, I, T> implements UniqueSortedDataFlow<E, I, T> {
		private final Comparator<? super T> theCompare;

		protected UniqueSortedMapOp(ObservableCollection<E> source, AbstractDataFlow<E, ?, I> parent,
			TypeToken<T> target, Function<? super I, ? extends T> map, boolean mapNulls,
			Function<? super T, ? extends I> reverse, ElementSetter<? super I, ? super T> elementReverse, boolean reverseNulls,
			boolean reEvalOnUpdate, boolean fireIfUnchanged, boolean isCached, Comparator<? super T> compare) {
			super(source, parent, target, map, mapNulls, reverse, elementReverse, reverseNulls, reEvalOnUpdate,
				fireIfUnchanged, isCached);
			theCompare = compare;
		}

		@Override
		public Comparator<? super T> comparator() {
			return theCompare;
		}

		@Override
		public UniqueSortedDataFlow<E, T, T> filter(Function<? super T, String> filter) {
			return (UniqueSortedDataFlow<E, T, T>) super.filter(filter);
		}

		@Override
		public UniqueSortedDataFlow<E, T, T> filter(Function<? super T, String> filter, boolean filterNulls) {
			return new UniqueSortedDataFlowWrapper<>(getSource(), (AbstractDataFlow<E, ?, T>) super.filter(filter, filterNulls),
				comparator());
		}

		@Override
		public UniqueSortedDataFlow<E, T, T> filterStatic(Function<? super T, String> filter) {
			return (UniqueSortedDataFlow<E, T, T>) super.filterStatic(filter);
		}

		@Override
		public UniqueSortedDataFlow<E, T, T> filterStatic(Function<? super T, String> filter, boolean filterNulls) {
			return new UniqueSortedDataFlowWrapper<>(getSource(), (AbstractDataFlow<E, ?, T>) super.filterStatic(filter, filterNulls),
				comparator());
		}

		@Override
		public <X> UniqueSortedMappedCollectionBuilder<E, T, X> mapEquivalent(TypeToken<X> target) {
			return new UniqueSortedMappedCollectionBuilder<>(getSource(), this, target);
		}

		@Override
		public UniqueSortedDataFlow<E, T, T> refresh(Observable<?> refresh) {
			return new UniqueSortedDataFlowWrapper<>(getSource(), (AbstractDataFlow<E, ?, T>) super.refresh(refresh), comparator());
		}

		@Override
		public UniqueSortedDataFlow<E, T, T> refreshEach(Function<? super T, ? extends Observable<?>> refresh) {
			return new UniqueSortedDataFlowWrapper<>(getSource(), (AbstractDataFlow<E, ?, T>) super.refreshEach(refresh),
				comparator());
		}

		@Override
		public UniqueSortedModFilterBuilder<E, T> filterModification() {
			return new UniqueSortedModFilterBuilder<>(getSource(), this);
		}

		@Override
		public ObservableSortedSet<T> collect(Observable<?> until) {
			return new ObservableSortedSetImpl.DerivedSortedSet<>(getSource(), manageCollection(), comparator(), until);
		}
	}

	/**
	 * Defines a combination of a single source collection with one or more observable values
	 *
	 * @param <E> The type of elements in the source collection
	 * @param <I> Intermediate type
	 * @param <T> The type of elements in the resulting collection
	 */
	public static class CombinedCollectionDef<E, I, T> extends AbstractDataFlow<E, I, T> {
		private final Map<ObservableValue<?>, Boolean> theArgs;
		private final Function<? super CombinedValues<? extends I>, ? extends T> theCombination;
		private final boolean combineNulls;
		private final Function<? super CombinedValues<? extends T>, ? extends I> theReverse;
		private final boolean reverseNulls;

		protected CombinedCollectionDef(ObservableCollection<E> source, AbstractDataFlow<E, ?, I> parent, TypeToken<T> target,
			Map<ObservableValue<?>, Boolean> args, Function<? super CombinedValues<? extends I>, ? extends T> combination,
			boolean combineNulls, Function<? super CombinedValues<? extends T>, ? extends I> reverse, boolean reverseNulls) {
			super(source, parent, target);
			theArgs = Collections.unmodifiableMap(args);
			theCombination = combination;
			this.combineNulls = combineNulls;
			theReverse = reverse;
			this.reverseNulls = reverseNulls;
		}

		@Override
		public AbstractCollectionManager<E, ?, T> manageCollection() {
			return new CombinedCollectionManager<>(getParent().manageCollection(), getTargetType(), theArgs, theCombination, combineNulls,
				theReverse, reverseNulls);
		}
	}

	public static abstract class AbstractCombinedCollectionBuilder<E, I, R> implements CombinedCollectionBuilder<E, I, R> {
		private final ObservableCollection<E> theSource;
		private final AbstractDataFlow<E, ?, I> theParent;
		private final TypeToken<R> theTargetType;
		private final LinkedHashMap<ObservableValue<?>, Ternian> theArgs;
		private Function<? super CombinedValues<? extends R>, ? extends I> theReverse;
		private boolean defaultCombineNulls = false;
		private boolean isReverseNulls = false;

		protected AbstractCombinedCollectionBuilder(ObservableCollection<E> source, AbstractDataFlow<E, ?, I> parent,
			TypeToken<R> targetType) {
			theSource = source;
			theParent = parent;
			theTargetType = targetType;
			theArgs = new LinkedHashMap<>();
		}

		protected ObservableCollection<E> getSource() {
			return theSource;
		}

		protected void addArg(ObservableValue<?> arg, Ternian combineNulls) {
			if (theArgs.containsKey(arg))
				throw new IllegalArgumentException("Argument " + arg + " is already combined");
			theArgs.put(arg, combineNulls);
		}

		protected Ternian combineNulls(ObservableValue<?> arg) {
			return theArgs.get(arg);
		}

		protected AbstractDataFlow<E, ?, I> getParent() {
			return theParent;
		}

		protected TypeToken<R> getTargetType() {
			return theTargetType;
		}

		protected Function<? super CombinedValues<? extends R>, ? extends I> getReverse() {
			return theReverse;
		}

		protected boolean isDefaultCombineNulls() {
			return defaultCombineNulls;
		}

		protected boolean isReverseNulls() {
			return isReverseNulls;
		}

		public AbstractCombinedCollectionBuilder<E, I, R> combineNullsByDefault() {
			defaultCombineNulls = true;
			return this;
		}

		@Override
		public AbstractCombinedCollectionBuilder<E, I, R> withReverse(Function<? super CombinedValues<? extends R>, ? extends I> reverse,
			boolean reverseNulls) {
			theReverse = reverse;
			this.isReverseNulls = reverseNulls;
			return this;
		}

		@Override
		public CollectionDataFlow<E, I, R> build(Function<? super CombinedValues<? extends I>, ? extends R> combination,
			boolean combineNulls) {
			return new CombinedCollectionDef<>(theSource, theParent, theTargetType, getResultArgs(), combination, combineNulls, theReverse,
				isReverseNulls);
		}

		protected Map<ObservableValue<?>, Boolean> getResultArgs() {
			Map<ObservableValue<?>, Boolean> result = new LinkedHashMap<>(theArgs.size() * 3 / 2);
			for (Map.Entry<ObservableValue<?>, Ternian> arg : theArgs.entrySet())
				result.put(arg.getKey(), arg.getValue().withDefault(defaultCombineNulls));
			return result;
		}
	}

	public static class RefreshOp<E, T> extends AbstractDataFlow<E, T, T> {
		private final Observable<?> theRefresh;

		protected RefreshOp(ObservableCollection<E> source, AbstractDataFlow<E, ?, T> parent, Observable<?> refresh) {
			super(source, parent, parent.getTargetType());
			theRefresh = refresh;
		}

		@Override
		public AbstractCollectionManager<E, ?, T> manageCollection() {
			return new RefreshingCollectionManager<>(getParent().manageCollection(), theRefresh);
		}
	}

	public static class ElementRefreshOp<E, T> extends AbstractDataFlow<E, T, T> {
		private final Function<? super T, ? extends Observable<?>> theElementRefresh;

		protected ElementRefreshOp(ObservableCollection<E> source, AbstractDataFlow<E, ?, T> parent,
			Function<? super T, ? extends Observable<?>> elementRefresh) {
			super(source, parent, parent.getTargetType());
			theElementRefresh = elementRefresh;
		}

		@Override
		public AbstractCollectionManager<E, ?, T> manageCollection() {
			return new ElementRefreshingCollectionManager<>(getParent().manageCollection(), theElementRefresh);
		}
	}

	public static class ModFilteredOp<E, T> extends AbstractDataFlow<E, T, T> {
		private final String theImmutableMessage;
		private final boolean areUpdatesAllowed;
		private final String theAddMessage;
		private final String theRemoveMessage;
		private final Function<? super T, String> theAddFilter;
		private final Function<? super T, String> theRemoveFilter;

		public ModFilteredOp(ObservableCollection<E> source, AbstractDataFlow<E, ?, T> parent, String immutableMsg, boolean allowUpdates,
			String addMsg, String removeMsg, Function<? super T, String> addMsgFn, Function<? super T, String> removeMsgFn) {
			super(source, parent, parent.getTargetType());
			theImmutableMessage = immutableMsg;
			areUpdatesAllowed = allowUpdates;
			theAddMessage = addMsg;
			theRemoveMessage = removeMsg;
			theAddFilter = addMsgFn;
			theRemoveFilter = removeMsgFn;
		}

		@Override
		public CollectionManager<E, ?, T> manageCollection() {
			return new ModFilteredCollectionManager<>(getParent().manageCollection(), theImmutableMessage, areUpdatesAllowed, theAddMessage,
				theRemoveMessage, theAddFilter, theRemoveFilter);
		}
	}

	public static class UniqueModFilteredOp<E, T> extends ModFilteredOp<E, T> implements UniqueDataFlow<E, T, T> {
		public UniqueModFilteredOp(ObservableCollection<E> source, AbstractDataFlow<E, ?, T> parent,
			String immutableMsg, boolean allowUpdates, String addMsg, String removeMsg, Function<? super T, String> addMsgFn,
			Function<? super T, String> removeMsgFn) {
			super(source, parent, immutableMsg, allowUpdates, addMsg, removeMsg, addMsgFn, removeMsgFn);
		}

		@Override
		public UniqueDataFlow<E, T, T> filter(Function<? super T, String> filter) {
			return (UniqueDataFlow<E, T, T>) super.filter(filter);
		}

		@Override
		public UniqueDataFlow<E, T, T> filter(Function<? super T, String> filter, boolean filterNulls) {
			return new UniqueDataFlowWrapper<>(getSource(), super.filter(filter, filterNulls));
		}

		@Override
		public UniqueDataFlow<E, T, T> filterStatic(Function<? super T, String> filter) {
			return (UniqueDataFlow<E, T, T>) super.filterStatic(filter);
		}

		@Override
		public UniqueDataFlow<E, T, T> filterStatic(Function<? super T, String> filter, boolean filterNulls) {
			return new UniqueDataFlowWrapper<>(getSource(), super.filterStatic(filter, filterNulls));
		}

		@Override
		public <X> UniqueMappedCollectionBuilder<E, T, X> mapEquivalent(TypeToken<X> target) {
			return new UniqueMappedCollectionBuilder<>(getSource(), this, target);
		}

		@Override
		public UniqueDataFlow<E, T, T> refresh(Observable<?> refresh) {
			return new UniqueDataFlowWrapper<>(getSource(), super.refresh(refresh));
		}

		@Override
		public UniqueDataFlow<E, T, T> refreshEach(Function<? super T, ? extends Observable<?>> refresh) {
			return new UniqueDataFlowWrapper<>(getSource(), super.refreshEach(refresh));
		}

		@Override
		public UniqueModFilterBuilder<E, T> filterModification() {
			return new UniqueModFilterBuilder<>(getSource(), this);
		}

		@Override
		public ObservableSet<T> collect(Observable<?> until) {
			return new ObservableSetImpl.DerivedSet<>(getSource(), manageCollection(), until);
		}
	}

	public static class UniqueSortedModFilteredOp<E, T> extends UniqueModFilteredOp<E, T> implements UniqueSortedDataFlow<E, T, T> {
		public UniqueSortedModFilteredOp(ObservableCollection<E> source, AbstractDataFlow<E, ?, T> parent,
			String immutableMsg, boolean allowUpdates, String addMsg, String removeMsg,
			Function<? super T, String> addMsgFn, Function<? super T, String> removeMsgFn) {
			super(source, parent, immutableMsg, allowUpdates, addMsg, removeMsg, addMsgFn, removeMsgFn);
		}

		@Override
		public Comparator<? super T> comparator() {
			return ((UniqueSortedDataFlow<E, ?, T>) getParent()).comparator();
		}

		@Override
		public UniqueSortedDataFlow<E, T, T> filter(Function<? super T, String> filter) {
			return (UniqueSortedDataFlow<E, T, T>) super.filter(filter);
		}

		@Override
		public UniqueSortedDataFlow<E, T, T> filter(Function<? super T, String> filter, boolean filterNulls) {
			return new UniqueSortedDataFlowWrapper<>(getSource(), (AbstractDataFlow<E, ?, T>) getParent().filter(filter, filterNulls),
				comparator());
		}

		@Override
		public UniqueSortedDataFlow<E, T, T> filterStatic(Function<? super T, String> filter) {
			return (UniqueSortedDataFlow<E, T, T>) super.filterStatic(filter);
		}

		@Override
		public UniqueSortedDataFlow<E, T, T> filterStatic(Function<? super T, String> filter, boolean filterNulls) {
			return new UniqueSortedDataFlowWrapper<>(getSource(), (AbstractDataFlow<E, ?, T>) getParent().filterStatic(filter, filterNulls),
				comparator());
		}

		@Override
		public <X> UniqueSortedMappedCollectionBuilder<E, T, X> mapEquivalent(TypeToken<X> target) {
			return new UniqueSortedMappedCollectionBuilder<>(getSource(), this, target);
		}

		@Override
		public UniqueSortedDataFlow<E, T, T> refresh(Observable<?> refresh) {
			return new UniqueSortedDataFlowWrapper<>(getSource(), (AbstractDataFlow<E, ?, T>) getParent().refresh(refresh),
				comparator());
		}

		@Override
		public UniqueSortedDataFlow<E, T, T> refreshEach(Function<? super T, ? extends Observable<?>> refresh) {
			return new UniqueSortedDataFlowWrapper<>(getSource(), (AbstractDataFlow<E, ?, T>) getParent().refreshEach(refresh),
				comparator());
		}

		@Override
		public UniqueSortedModFilterBuilder<E, T> filterModification() {
			return new UniqueSortedModFilterBuilder<>(getSource(), this);
		}

		@Override
		public ObservableSortedSet<T> collect(Observable<?> until) {
			return new ObservableSortedSetImpl.DerivedSortedSet<>(getSource(), manageCollection(), comparator(), until);
		}
	}

	public static interface UniqueElementFinder<T> {
		ElementId getUniqueElement(T value);
	}

	public static interface CollectionManager<E, I, T> extends Transactable {
		TypeToken<T> getTargetType();

		Equivalence<? super T> equivalence();

		boolean isDynamicallyFiltered();

		boolean isStaticallyFiltered();

		boolean isMapped();

		boolean isReversible();

		UniqueElementFinder<T> getElementFinder();

		Comparator<? super T> comparator();

		FilterMapResult<E, T> map(FilterMapResult<E, T> source);

		default FilterMapResult<E, T> map(E source) {
			return map(new FilterMapResult<>(source));
		}

		FilterMapResult<T, E> reverse(FilterMapResult<T, E> dest);

		default FilterMapResult<T, E> reverse(T dest) {
			return reverse(new FilterMapResult<>(dest));
		}

		FilterMapResult<T, E> canAdd(FilterMapResult<T, E> toAdd);

		void begin(Consumer<CollectionUpdate> onUpdate, Observable<?> until);

		CollectionElementManager<E, ?, T> createElement(ElementId id, E init, Object cause);

		void postChange();
	}

	public static abstract class AbstractCollectionManager<E, I, T> implements CollectionManager<E, I, T> {
		private final CollectionManager<E, ?, I> theParent;
		private final TypeToken<T> theTargetType;
		private ReentrantReadWriteLock theLock;
		private Consumer<CollectionUpdate> theUpdateListener;
		private final List<Runnable> thePostChanges;

		protected AbstractCollectionManager(CollectionManager<E, ?, I> parent, TypeToken<T> targetType) {
			theParent = parent;
			theTargetType = targetType;
			theLock = theParent != null ? null : new ReentrantReadWriteLock();
			thePostChanges = new LinkedList<>();
		}

		protected CollectionManager<E, ?, I> getParent() {
			return theParent;
		}

		protected Consumer<CollectionUpdate> getUpdateListener() {
			return theUpdateListener;
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			if (theParent != null)
				return theParent.lock(write, cause);
			Lock lock = write ? theLock.writeLock() : theLock.readLock();
			lock.lock();
			return () -> lock.unlock();
		}

		@Override
		public TypeToken<T> getTargetType() {
			return theTargetType;
		}

		@Override
		public boolean isDynamicallyFiltered() {
			return theParent == null ? false : theParent.isDynamicallyFiltered();
		}

		@Override
		public boolean isStaticallyFiltered() {
			return theParent == null ? false : theParent.isStaticallyFiltered();
		}

		@Override
		public boolean isMapped() {
			return theParent == null ? false : theParent.isMapped();
		}

		@Override
		public boolean isReversible() {
			return theParent == null ? true : theParent.isReversible();
		}

		protected abstract FilterMapResult<I, T> mapTop(FilterMapResult<I, T> source);

		@Override
		public FilterMapResult<E, T> map(FilterMapResult<E, T> source) {
			FilterMapResult<I, T> intermediate = (FilterMapResult<I, T>) source;
			if (getParent() != null) {
				getParent().map((FilterMapResult<E, I>) source);
				if (source.error == null)
					intermediate.source = ((FilterMapResult<E, I>) source).result;
			}
			if (source.error == null)
				mapTop(intermediate);
			return source;
		}

		@Override
		public FilterMapResult<E, T> map(E source) {
			return map(new FilterMapResult<>(source));
		}

		protected abstract FilterMapResult<T, I> reverseTop(FilterMapResult<T, I> dest);

		@Override
		public FilterMapResult<T, E> reverse(FilterMapResult<T, E> dest) {
			FilterMapResult<T, I> top = (FilterMapResult<T, I>) dest;
			reverseTop(top);
			if (dest.error == null && getParent() != null) {
				FilterMapResult<I, E> intermediate = (FilterMapResult<I, E>) dest;
				intermediate.source = top.result;
				getParent().reverse(intermediate);
			}
			return dest;
		}

		@Override
		public FilterMapResult<T, E> reverse(T dest) {
			return reverse(new FilterMapResult<>(dest));
		}

		protected FilterMapResult<T, I> canAddTop(FilterMapResult<T, I> dest) {
			return reverseTop(dest);
		}

		@Override
		public FilterMapResult<T, E> canAdd(FilterMapResult<T, E> toAdd) {
			FilterMapResult<T, I> top = (FilterMapResult<T, I>) toAdd;
			canAddTop(top);
			if (toAdd.error == null && getParent() != null) {
				FilterMapResult<I, E> intermediate = (FilterMapResult<I, E>) toAdd;
				intermediate.source = top.result;
				getParent().canAdd(intermediate);
			}
			return toAdd;
		}

		@Override
		public void postChange() {
			if (!thePostChanges.isEmpty()) {
				Runnable[] changes = thePostChanges.toArray(new Runnable[thePostChanges.size()]);
				thePostChanges.clear();
				for (Runnable postChange : changes)
					postChange.run();
			}
		}

		protected void postChange(Runnable run) {
			thePostChanges.add(run);
		}

		protected abstract void begin();

		@Override
		public void begin(Consumer<CollectionUpdate> onUpdate, Observable<?> until) {
			if (theUpdateListener != null)
				throw new IllegalStateException("Cannot begin twice");
			theUpdateListener = onUpdate;
			begin();
			if (theParent != null)
				theParent.begin(onUpdate, until);
		}

		@Override
		public abstract CollectionElementManager<E, ?, T> createElement(ElementId id, E init, Object cause);
	}

	public static abstract class CollectionElementManager<E, I, T> implements Comparable<CollectionElementManager<E, ?, T>> {
		private final AbstractCollectionManager<E, I, T> theCollection;
		private final CollectionElementManager<E, ?, I> theParent;
		private final ElementId theId;

		protected CollectionElementManager(AbstractCollectionManager<E, I, T> collection, CollectionElementManager<E, ?, I> parent,
			ElementId id) {
			theCollection = collection;
			theParent = parent;
			theId = id;
		}

		protected CollectionElementManager<E, ?, I> getParent() {
			return theParent;
		}

		public ElementId getElementId() {
			return theId;
		}

		@Override
		public int compareTo(CollectionElementManager<E, ?, T> other) {
			if (getParent() == null)
				return theId.compareTo(other.theId);
			return getParent().compareTo(((CollectionElementManager<E, I, T>) other).getParent());
		}

		public boolean isPresent() {
			// Most elements don't filter
			return theParent.isPresent();
		}

		public abstract T get();

		public boolean set(E value, Object cause) {
			if (!getParent().set(value, cause))
				return false;
			return refresh(getParent().get(), cause);
		}

		public final MutableObservableElement<T> map(MutableObservableElement<? extends E> element, ElementId id) {
			class MutableManagedElement implements MutableObservableElement<T> {
				private final MutableObservableElement<? extends E> theWrapped;

				MutableManagedElement(MutableObservableElement<? extends E> wrapped) {
					theWrapped = wrapped;
				}

				@Override
				public TypeToken<T> getType() {
					return theCollection.getTargetType();
				}

				@Override
				public T get() {
					return CollectionElementManager.this.get();
				}

				@Override
				public ElementId getElementId() {
					return id; // The ID we have locally is from the source collection
				}

				@Override
				public Value<String> isEnabled() {
					Value<String> wrapEnabled = theWrapped.isEnabled();
					return new Value<String>() {
						@Override
						public TypeToken<String> getType() {
							return TypeToken.of(String.class);
						}

						@Override
						public String get() {
							if (isInterceptingSet())
								return null;
							String msg = filterEnabled();
							if (msg == null)
								msg = wrapEnabled.get();
							return msg;
						}
					};
				}

				@Override
				public <V extends T> String isAcceptable(V value) {
					if (isInterceptingSet())
						return filterInterceptSet(new FilterMapResult<>(value)).error;
					FilterMapResult<T, E> result = CollectionElementManager.this.filterAccept(new FilterMapResult<>(value), false, null);
					if (result.error != null)
						return result.error;
					if (result.result != null && !theWrapped.getType().getRawType().isInstance(result.result))
						return StdMsg.BAD_TYPE;
					return ((MutableObservableElement<E>) theWrapped).isAcceptable(result.result);
				}

				@Override
				public <V extends T> T set(V value, Object cause) throws IllegalArgumentException, UnsupportedOperationException {
					if (isInterceptingSet()) {
						return interceptSet(new FilterMapResult<>(value), cause);
					}
					FilterMapResult<T, E> result = CollectionElementManager.this.filterAccept(new FilterMapResult<>(value), true, cause);
					if (result.error != null)
						throw new IllegalArgumentException(result.error);
					if (result.result != null && !theWrapped.getType().getRawType().isInstance(result.result))
						throw new IllegalArgumentException(StdMsg.BAD_TYPE);
					T old = get();
					((MutableObservableElement<E>) theWrapped).set(result.result, cause);
					return old;
				}

				@Override
				public String canRemove() {
					String msg = filterRemove(false, null);
					if (msg == null)
						msg = theWrapped.canRemove();
					return msg;
				}

				@Override
				public void remove(Object cause) throws UnsupportedOperationException {
					String msg = filterRemove(true, cause);
					if (msg != null)
						throw new UnsupportedOperationException(msg);
					theWrapped.remove(cause);
				}

				@Override
				public String canAdd(T value, boolean before) {
					FilterMapResult<T, E> result = filterAdd(new FilterMapResult<>(value), before, false, null);
					if (result.error != null)
						return result.error;
					if (result.result != null && !theWrapped.getType().getRawType().isInstance(result.result))
						return StdMsg.BAD_TYPE;
					return ((MutableObservableElement<E>) theWrapped).canAdd(result.result, before);
				}

				@Override
				public void add(T value, boolean before, Object cause) throws UnsupportedOperationException, IllegalArgumentException {
					FilterMapResult<T, E> result = filterAdd(new FilterMapResult<>(value), before, true, cause);
					if (result.error != null)
						throw new IllegalArgumentException(result.error);
					if (result.result != null && !theWrapped.getType().getRawType().isInstance(result.result))
						throw new IllegalArgumentException(StdMsg.BAD_TYPE);
					((MutableObservableElement<E>) theWrapped).add(result.result, before, cause);
				}
			}
			return new MutableManagedElement(element);
		}

		protected String filterRemove(boolean isRemoving, Object cause) {
			return getParent() == null ? null : getParent().filterRemove(isRemoving, cause);
		}

		protected String filterEnabled() {
			return getParent() == null ? null : getParent().filterEnabled();
		}

		protected FilterMapResult<T, E> filterAccept(FilterMapResult<T, E> value, boolean isReplacing, Object cause) {
			FilterMapResult<T, I> top = theCollection.reverseTop((FilterMapResult<T, I>) value);
			if (top.error == null && getParent() != null) {
				FilterMapResult<I, E> intermediate = (FilterMapResult<I, E>) top;
				intermediate.source = top.result;
				getParent().filterAccept(intermediate, isReplacing, cause);
			}
			return value;
		}

		protected FilterMapResult<T, E> filterAdd(FilterMapResult<T, E> value, boolean before, boolean isAdding, Object cause) {
			FilterMapResult<T, I> top = theCollection.reverseTop((FilterMapResult<T, I>) value);
			if (top.error == null && getParent() != null) {
				FilterMapResult<I, E> intermediate = (FilterMapResult<I, E>) top;
				intermediate.source = top.result;
				getParent().filterAdd(intermediate, before, isAdding, cause);
			}
			return value;
		}

		protected boolean isInterceptingSet() {
			return getParent() == null ? false : getParent().isInterceptingSet();
		}

		protected FilterMapResult<T, E> filterInterceptSet(FilterMapResult<T, E> value) {
			if (getParent() == null)
				value.error = StdMsg.UNSUPPORTED_OPERATION;
			else if (!getParent().isInterceptingSet())
				value.error = StdMsg.UNSUPPORTED_OPERATION;
			else {
				FilterMapResult<T, I> top = theCollection.reverseTop((FilterMapResult<T, I>) value);
				if (top.error == null) {
					FilterMapResult<I, E> intermediate = (FilterMapResult<I, E>) top;
					intermediate.source = top.result;
					getParent().filterInterceptSet(intermediate);
				}
			}
			return value;
		}

		protected T interceptSet(FilterMapResult<T, E> value, Object cause) throws UnsupportedOperationException, IllegalArgumentException {
			if (getParent() == null)
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			FilterMapResult<T, I> top = theCollection.reverseTop((FilterMapResult<T, I>) value);
			if (top.error != null)
				throw new IllegalArgumentException(top.error);
			FilterMapResult<I, E> intermediate = (FilterMapResult<I, E>) top;
			intermediate.source = top.result;
			FilterMapResult<I, T> map = (FilterMapResult<I, T>) value;
			map.source = getParent().interceptSet(intermediate, cause);
			return theCollection.mapTop(map).result;// Shouldn't have an error since it had the opportunity to reject
		}

		protected boolean applies(CollectionUpdate update) {
			return update instanceof CollectionUpdate && update.getCollection() == theCollection//
				&& (update.getElement() == null || update.getElement().equals(getElementId()));
		}

		public ElementUpdateResult update(CollectionUpdate update,
			Consumer<Consumer<MutableObservableElement<? extends E>>> sourceElement) {
			if (applies(update)) {
				return refresh(getParent().get(), update.getCause()) ? ElementUpdateResult.FireUpdate : ElementUpdateResult.AppliedNoUpdate;
			} else {
				ElementUpdateResult result = getParent().update(update, sourceElement);
				switch (result) {
				case DoesNotApply:
				case AppliedNoUpdate:
					return result;
				case FireUpdate:
					return refresh(getParent().get(), update.getCause()) ? ElementUpdateResult.FireUpdate
						: ElementUpdateResult.AppliedNoUpdate;
				}
				throw new IllegalStateException("Unrecognized update result: " + result);
			}
		}

		protected abstract boolean refresh(I source, Object cause);

		public void removed(Object cause) {
			// Most elements don't need to do anything when they're removed
		}
	}

	public static class CollectionUpdate {
		private final AbstractCollectionManager<?, ?, ?> theCollection;
		private final ElementId theElement;
		private final Object theCause;

		public CollectionUpdate(AbstractCollectionManager<?, ?, ?> collection, ElementId element, Object cause) {
			theCollection = collection;
			theElement = element;
			theCause = cause;
		}

		public AbstractCollectionManager<?, ?, ?> getCollection() {
			return theCollection;
		}

		public ElementId getElement() {
			return theElement;
		}

		public Object getCause() {
			return theCause;
		}
	}

	public static class RemoveElementUpdate extends CollectionUpdate {
		public RemoveElementUpdate(AbstractCollectionManager<?, ?, ?> collection, ElementId element, Object cause) {
			super(collection, element, cause);
		}
	}

	public static enum ElementUpdateResult {
		DoesNotApply, FireUpdate, AppliedNoUpdate;
	}

	public static abstract class NonMappingCollectionManager<E, T> extends AbstractCollectionManager<E, T, T> {
		protected NonMappingCollectionManager(CollectionManager<E, ?, T> parent) {
			super(parent, parent.getTargetType());
		}

		@Override
		public UniqueElementFinder<T> getElementFinder() {
			return getParent().getElementFinder();
		}

		@Override
		public Comparator<? super T> comparator() {
			return getParent().comparator();
		}

		@Override
		public FilterMapResult<T, T> mapTop(FilterMapResult<T, T> source) {
			source.result = source.source;
			return source;
		}

		@Override
		public FilterMapResult<T, T> reverseTop(FilterMapResult<T, T> dest) {
			dest.result = dest.source;
			return dest;
		}
	}

	public static abstract class NonMappingCollectionElement<E, T> extends CollectionElementManager<E, T, T> {
		protected NonMappingCollectionElement(NonMappingCollectionManager<E, T> collection, CollectionElementManager<E, ?, T> parent,
			ElementId id) {
			super(collection, parent, id);
		}
	}

	public static class BaseCollectionManager<E> extends AbstractCollectionManager<E, E, E> {
		private final Equivalence<? super E> theEquivalence;
		private final ReentrantReadWriteLock theLock;

		public BaseCollectionManager(TypeToken<E> targetType, Equivalence<? super E> equivalence, boolean threadSafe) {
			super(null, targetType);
			theEquivalence = equivalence;
			theLock = threadSafe ? new ReentrantReadWriteLock() : null;
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			if (theLock == null)
				return Transaction.NONE;
			Lock lock = write ? theLock.writeLock() : theLock.readLock();
			lock.lock();
			return () -> lock.unlock();
		}

		@Override
		public Equivalence<? super E> equivalence() {
			return theEquivalence;
		}

		@Override
		public UniqueElementFinder<E> getElementFinder() {
			return null;
		}

		@Override
		public Comparator<? super E> comparator() {
			return null;
		}

		@Override
		public FilterMapResult<E, E> mapTop(FilterMapResult<E, E> source) {
			source.result = source.source;
			return source;
		}

		@Override
		public FilterMapResult<E, E> reverseTop(FilterMapResult<E, E> dest) {
			dest.result = dest.source;
			return dest;
		}

		@Override
		public CollectionElementManager<E, ?, E> createElement(ElementId id, E init, Object cause) {
			class DefaultElement extends CollectionElementManager<E, E, E> {
				private E theValue;

				protected DefaultElement() {
					super(BaseCollectionManager.this, null, id);
				}

				@Override
				public E get() {
					return theValue;
				}

				@Override
				public boolean set(E value, Object cause) {
					theValue = value;
					return true;
				}

				@Override
				public ElementUpdateResult update(CollectionUpdate update,
					Consumer<Consumer<MutableObservableElement<? extends E>>> sourceElement) {
					return ElementUpdateResult.DoesNotApply;
				}

				@Override
				protected boolean refresh(E source, Object cause) {
					// Never called
					return true;
				}
			}
			return new DefaultElement();
		}

		@Override
		public void begin() {}
	}

	public static class UniqueManager<E, T> extends AbstractCollectionManager<E, T, T> {
		private final UpdatableMap<T, DefaultTreeSet<UniqueElement>> theElementsByValue;
		private final boolean isAlwaysUsingFirst;

		protected UniqueManager(CollectionManager<E, ?, T> parent, boolean alwaysUseFirst) {
			super(parent, parent.getTargetType());
			theElementsByValue = parent.equivalence().createMap();
			isAlwaysUsingFirst = alwaysUseFirst;
		}

		@Override
		public UniqueElementFinder<T> getElementFinder() {
			return this::getUniqueElement;
		}

		public ElementId getUniqueElement(T value) {
			DefaultTreeSet<UniqueElement> valueElements = theElementsByValue.get(value);
			if (valueElements == null)
				return null;
			for (UniqueElement el : valueElements)
				if (el.isPresent)
					return el.getElementId();
			// Although this state is incorrect, since one value should be present, this state is reachable temporarily during
			// modifications. It represents a state where the present element for the value has been removed or deactivated
			// and the new one has not yet been installed. So according to what the derived collection knows, the element doesn't exist.
			return null;
		}

		@Override
		public Comparator<? super T> comparator() {
			return getParent().comparator();
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return getParent().equivalence();
		}

		@Override
		public boolean isDynamicallyFiltered() {
			return true;
		}

		@Override
		public FilterMapResult<T, T> mapTop(FilterMapResult<T, T> source) {
			source.result = source.source;
			return source;
		}

		@Override
		public FilterMapResult<T, T> reverseTop(FilterMapResult<T, T> dest) {
			dest.result = dest.source;
			return dest;
		}

		@Override
		public FilterMapResult<T, T> canAddTop(FilterMapResult<T, T> toAdd) {
			if (theElementsByValue.containsKey(toAdd.source))
				toAdd.error = StdMsg.ELEMENT_EXISTS;
			else
				toAdd.result = toAdd.source;
			return toAdd;
		}

		@Override
		protected void begin() {}

		@Override
		public CollectionElementManager<E, ?, T> createElement(ElementId id, E init, Object cause) {
			return new UniqueElement(id, init, cause);
		}

		void update(UniqueElement element, Object cause) {
			getUpdateListener().accept(new CollectionUpdate(this, element.getElementId(), cause));
		}

		class UniqueElement extends CollectionElementManager<E, T, T> {
			private T theValue;
			private boolean isPresent;
			private DefaultTreeSet<UniqueElement> theValueElements;
			private DefaultNode<UniqueElement> theNode;

			protected UniqueElement(ElementId id, E init, Object cause) {
				super(UniqueManager.this, UniqueManager.this.getParent().createElement(id, init, cause), id);

				T value = getParent().get();
				theValue = value;
				addToSet(cause);
			}

			@Override
			public boolean isPresent() {
				return isPresent;
			}

			@Override
			public T get() {
				return theValue;
			}

			@Override
			protected boolean refresh(T source, Object cause) {
				if (theElementsByValue.get(source) != theValueElements) {
					removeFromSet(cause);
					theValue = source;
					addToSet(cause);
				}
				return true;
			}

			@Override
			public void removed(Object cause) {
				super.removed(cause);
				removeFromSet(cause);
			}

			private void removeFromSet(Object cause) {
				theValueElements.removeNode(theNode);
				theNode = null;
				if (theValueElements.isEmpty())
					theElementsByValue.remove(theValue);
				else {
					if (isPresent) {
						// We're the first value
						isPresent = false;
						UniqueElement newFirst = theValueElements.first();
						// Delay installing the new value this node has been reported removed so the set is always unique
						postChange(() -> {
							newFirst.isPresent = true;
							UniqueManager.this.update(newFirst, cause);
						});
					}
					theValueElements = null; // Other elements are using that set, so we can't re-use it
				}
			}

			private void addToSet(Object cause) {
				theValueElements = theElementsByValue.computeIfAbsent(theValue, v -> new DefaultTreeSet<>(UniqueElement::compareTo));
				// Grab our node, since we can use it to remove even if the comparison properties change
				theNode = theValueElements.addGetNode(this);
				if (theValueElements.size() == 1) {
					// We're currently the only element for the value
					isPresent = true;
				} else if (isAlwaysUsingFirst && theNode.getIndex() == 0) {
					isPresent = true;
					// We're replacing the existing representative for the value
					UniqueElement replaced = theValueElements.higher(this);
					// Remove the replaced node before this one is installed so the set is always unique
					replaced.isPresent = false;
					UniqueManager.this.update(replaced, cause);
				} else {
					// There are other elements for the value that we will not replace
					isPresent = false;
				}
			}

			@Override
			protected String filterRemove(boolean isRemoving, Object cause) {
				String msg = super.filterRemove(isRemoving, cause);
				if (isRemoving && msg == null) {
					// The remove operation for this element needs to remove not only the source element mapping to to this element,
					// but also all other equivalent elements.
					// Remove the other elements first, since they are not present in the derived collection and won't result in events
					// for the collection if they're removed while this element is still present.
					UniqueElement[] elements = theValueElements.toArray(new UniqueManager.UniqueElement[theValueElements.size()]);
					for (UniqueElement other : elements) {
						if (other == this)
							continue;
						getUpdateListener().accept(new RemoveElementUpdate(UniqueManager.this, other.getElementId(), cause));
					}
				}
				return msg;
			}

			@Override
			protected FilterMapResult<T, E> filterAccept(FilterMapResult<T, E> value, boolean isReplacing, Object cause) {
				if (value.source != theValue && !equivalence().elementEquals(theValue, value.source)) {
					value.error = "Cannot change equivalence of a unique element";
					return value;
				} else
					return super.filterAccept(value, isReplacing, cause);
			}

			@Override
			public ElementUpdateResult update(CollectionUpdate update,
				Consumer<Consumer<MutableObservableElement<? extends E>>> sourceElement) {
				if (update instanceof RemoveElementUpdate && applies(update)) {
					sourceElement.accept(el -> el.remove(update.getCause()));
					return ElementUpdateResult.AppliedNoUpdate; // We're removed now, so obviously don't update
				} else
					return super.update(update, sourceElement);
			}
		}
	}

	public static class SortedManager<E, T> extends NonMappingCollectionManager<E, T> {
		private final Comparator<? super T> theCompare;
		private final Equivalence<? super T> theEquivalence;

		protected SortedManager(CollectionManager<E, ?, T> parent, Comparator<? super T> compare) {
			super(parent);
			theCompare = compare;
			theEquivalence = Equivalence.of((Class<T>) parent.getTargetType().getRawType(), theCompare, true);
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return theEquivalence;
		}

		@Override
		protected void begin() {}

		@Override
		public CollectionElementManager<E, ?, T> createElement(ElementId id, E init, Object cause) {
			class SortedElement extends NonMappingCollectionElement<E, T> {
				SortedElement() {
					super(SortedManager.this, SortedManager.this.getParent().createElement(id, init, cause), id);
				}

				@Override
				public T get() {
					return getParent().get();
				}

				@Override
				protected boolean refresh(T source, Object cause) {
					return true;
				}

				@Override
				public int compareTo(CollectionElementManager<E, ?, T> other) {
					int compare = theCompare.compare(get(), other.get());
					if (compare != 0)
						return compare;
					return super.compareTo(other);
				}

				@Override
				protected FilterMapResult<T, E> filterAccept(FilterMapResult<T, E> value, boolean isReplacing, Object cause) {
					if (get() != value.source && theCompare.compare(get(), value.source) != 0) {
						value.error = "Cannot modify a sorted value";
						return value;
					}
					return super.filterAccept(value, isReplacing, cause);
				}

				@Override
				protected FilterMapResult<T, E> filterAdd(FilterMapResult<T, E> value, boolean before, boolean isAdding, Object cause) {
					value.error = "Cannot add values to a sorted collection via spliteration";
					return value;
				}

				@Override
				protected FilterMapResult<T, E> filterInterceptSet(FilterMapResult<T, E> value) {
					if (get() != value.source && theCompare.compare(get(), value.source) != 0) {
						value.error = "Cannot modify a sorted value";
						return value;
					}
					return super.filterInterceptSet(value);
				}

				@Override
				protected T interceptSet(FilterMapResult<T, E> value, Object cause)
					throws UnsupportedOperationException, IllegalArgumentException {
					if (get() != value.source && theCompare.compare(get(), value.source) != 0) {
						throw new UnsupportedOperationException("Cannot modify a sorted value");
					}
					return super.interceptSet(value, cause);
				}
			}
			return new SortedElement();
		}
	}

	public static class FilteredCollectionManager<E, T> extends NonMappingCollectionManager<E, T> {
		private final Function<? super T, String> theFilter;
		private final boolean filterNulls;

		protected FilteredCollectionManager(CollectionManager<E, ?, T> parent, Function<? super T, String> filter, boolean filterNulls) {
			super(parent);
			theFilter = filter;
			this.filterNulls = filterNulls;
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return getParent().equivalence();
		}

		@Override
		public boolean isDynamicallyFiltered() {
			return true;
		}

		@Override
		public FilterMapResult<E, T> map(FilterMapResult<E, T> source) {
			getParent().map(source);
			String error;
			if (!filterNulls && source.result == null)
				error = StdMsg.NULL_DISALLOWED;
			else
				error = theFilter.apply(source.result);
			if (error != null) {
				source.result = null;
				source.error = error;
			}
			return source;
		}

		@Override
		public FilterMapResult<T, E> reverse(FilterMapResult<T, E> dest) {
			String error;
			if (!filterNulls && dest.source == null)
				error = StdMsg.NULL_DISALLOWED;
			else
				error = theFilter.apply(dest.source);
			if (error != null)
				dest.error = error;
			else
				getParent().reverse(dest);
			return dest;
		}

		@Override
		public void begin() {}

		@Override
		public NonMappingCollectionElement<E, T> createElement(ElementId id, E init, Object cause) {
			class FilteredElement extends NonMappingCollectionElement<E, T> {
				private boolean isPresent;

				protected FilteredElement() {
					super(FilteredCollectionManager.this, FilteredCollectionManager.this.getParent().createElement(id, init, cause), id);
				}

				@Override
				public boolean isPresent() {
					return isPresent && super.isPresent();
				}

				@Override
				public T get() {
					return getParent().get();
				}

				@Override
				protected boolean refresh(T source, Object cause) {
					if (source == null && !filterNulls)
						isPresent = false;
					else
						isPresent = theFilter.apply(source) == null;
					return true;
				}
			}
			return new FilteredElement();
		}
	}

	public static class StaticFilteredCollectionManager<E, T> extends NonMappingCollectionManager<E, T> {
		private final Function<? super T, String> theFilter;
		private final boolean filterNulls;

		protected StaticFilteredCollectionManager(CollectionManager<E, ?, T> parent, Function<? super T, String> filter,
			boolean filterNulls) {
			super(parent);
			theFilter = filter;
			this.filterNulls = filterNulls;
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return getParent().equivalence();
		}

		@Override
		public boolean isStaticallyFiltered() {
			return true;
		}

		@Override
		public FilterMapResult<E, T> map(FilterMapResult<E, T> source) {
			getParent().map(source);
			String error;
			if (!filterNulls && source.result == null)
				error = StdMsg.NULL_DISALLOWED;
			else
				error = theFilter.apply(source.result);
			if (error != null) {
				source.result = null;
				source.error = error;
			}
			return source;
		}

		@Override
		public FilterMapResult<T, E> reverse(FilterMapResult<T, E> dest) {
			String error;
			if (!filterNulls && dest.source == null)
				error = StdMsg.NULL_DISALLOWED;
			else
				error = theFilter.apply(dest.source);
			if (error != null)
				dest.error = error;
			else
				getParent().reverse(dest);
			return dest;
		}

		@Override
		public void begin() {}

		@Override
		public CollectionElementManager<E, ?, T> createElement(ElementId id, E init, Object cause) {
			CollectionElementManager<E, ?, T> parentElement = getParent().createElement(id, init, cause);
			T value = parentElement.get();
			if (value == null && !filterNulls)
				return null;
			else if (theFilter.apply(value) != null)
				return null;
			else
				return parentElement;
		}
	}

	public static class MappedCollectionManager<E, I, T> extends AbstractCollectionManager<E, I, T> {
		private final Function<? super I, ? extends T> theMap;
		private final boolean areNullsMapped;
		private final Function<? super T, ? extends I> theReverse;
		private final ElementSetter<? super I, ? super T> theElementReverse;
		private final boolean areNullsReversed;
		private final boolean reEvalOnUpdate;
		private final boolean fireIfUnchanged;
		private final boolean isCached;

		protected MappedCollectionManager(CollectionManager<E, ?, I> parent, TypeToken<T> targetType, Function<? super I, ? extends T> map,
			boolean areNullsMapped, Function<? super T, ? extends I> reverse, ElementSetter<? super I, ? super T> elementReverse,
			boolean areNullsReversed, boolean reEvalOnUpdate, boolean fireIfUnchanged, boolean cached) {
			super(parent, targetType);
			theMap = map;
			this.areNullsMapped = areNullsMapped;
			theReverse = reverse;
			theElementReverse = elementReverse;
			this.areNullsReversed = areNullsReversed;
			this.reEvalOnUpdate = reEvalOnUpdate;
			this.fireIfUnchanged = fireIfUnchanged;
			isCached = cached;
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return Equivalence.DEFAULT;
		}

		@Override
		public boolean isMapped() {
			return true;
		}

		@Override
		public boolean isReversible() {
			return theReverse != null && getParent().isReversible();
		}

		@Override
		public UniqueElementFinder<T> getElementFinder() {
			if (theReverse == null)
				return null;
			UniqueElementFinder<I> pef = getParent().getElementFinder();
			if (pef == null)
				return null;
			return v -> pef.getUniqueElement(reverseValue(v));
		}

		@Override
		public Comparator<? super T> comparator() {
			if (theReverse == null)
				return null;
			Comparator<? super I> pc = getParent().comparator();
			if (pc == null)
				return null;
			return (v1, v2) -> pc.compare(reverseValue(v1), reverseValue(v2));
		}

		protected T mapValue(I source) {
			if (source == null && !areNullsMapped)
				return null;
			else
				return theMap.apply(source);
		}

		protected I reverseValue(T dest) {
			if (dest == null && !areNullsReversed)
				return null;
			else
				return theReverse.apply(dest);
		}

		@Override
		public FilterMapResult<I, T> mapTop(FilterMapResult<I, T> source) {
			source.result = mapValue(source.source);
			return source;
		}

		@Override
		public FilterMapResult<T, I> reverseTop(FilterMapResult<T, I> dest) {
			if (!isReversible())
				dest.error = StdMsg.UNSUPPORTED_OPERATION;
			else
				dest.result = reverseValue(dest.source);
			return dest;
		}

		@Override
		public CollectionElementManager<E, I, T> createElement(ElementId id, E init, Object cause) {
			class MappedElement extends CollectionElementManager<E, I, T> {
				private I theSource;
				private T theValue;
				private boolean isInitial;

				MappedElement() {
					super(MappedCollectionManager.this, MappedCollectionManager.this.getParent().createElement(id, init, cause), id);
				}

				@Override
				public T get() {
					if (isCached)
						return theValue;
					else
						return mapValue(getParent().get());
				}

				@Override
				protected boolean isInterceptingSet() {
					return theElementReverse != null || super.isInterceptingSet();
				}

				@Override
				protected FilterMapResult<T, E> filterInterceptSet(FilterMapResult<T, E> value) {
					if (theElementReverse != null) {
						value.error = theElementReverse.setElement(getParent().get(), value.source, false, cause);
						return value;
					}
					return super.filterInterceptSet(value);
				}

				@Override
				protected T interceptSet(FilterMapResult<T, E> value, Object cause)
					throws UnsupportedOperationException, IllegalArgumentException {
					if (theElementReverse != null) {
						T oldValue = get();
						theElementReverse.setElement(getParent().get(), value.source, true, cause);
						return oldValue;
					}
					return super.interceptSet(value, cause);
				}

				@Override
				protected boolean refresh(I source, Object cause) {
					if (!isCached)
						return true;
					if (!reEvalOnUpdate && source == theSource) {
						if (isInitial)
							isInitial = false;
						else
							return fireIfUnchanged;
					}
					theSource = source;
					T newValue = mapValue(source);
					if (!fireIfUnchanged && newValue == theValue)
						return false;
					theValue = newValue;
					return true;
				}
			}
			return new MappedElement();
		}

		@Override
		protected void begin() {}
	}

	public static class CombinedCollectionManager<E, I, T> extends AbstractCollectionManager<E, I, T> {
		private static class ArgHolder<T> {
			final boolean combineNull;
			Consumer<ObservableValueEvent<?>> action;
			T value;

			ArgHolder(boolean combineNull) {
				this.combineNull = combineNull;
			}
		}
		private final Map<ObservableValue<?>, ArgHolder<?>> theArgs;
		private final Function<? super CombinedValues<? extends I>, ? extends T> theCombination;
		private final boolean combineNulls;
		private final Function<? super CombinedValues<? extends T>, ? extends I> theReverse;
		private final boolean reverseNulls;

		/** The number of combined values which must be non-null to be passed to the combination function but are, in fact, null */
		private int badCombineValues;

		protected CombinedCollectionManager(CollectionManager<E, ?, I> parent, TypeToken<T> targetType,
			Map<ObservableValue<?>, Boolean> args, Function<? super CombinedValues<? extends I>, ? extends T> combination,
			boolean combineNulls, Function<? super CombinedValues<? extends T>, ? extends I> reverse, boolean reverseNulls) {
			super(parent, targetType);
			theArgs = new HashMap<>();
			for (Map.Entry<ObservableValue<?>, Boolean> arg : args.entrySet())
				theArgs.put(arg.getKey(), new ArgHolder<>(arg.getValue()));
			theCombination = combination;
			this.combineNulls = combineNulls;
			theReverse = reverse;
			this.reverseNulls = reverseNulls;
		}

		@Override
		public boolean isMapped() {
			return true;
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return Equivalence.DEFAULT;
		}

		@Override
		public boolean isReversible() {
			return theReverse != null && super.isReversible();
		}

		@Override
		public UniqueElementFinder<T> getElementFinder() {
			if (theReverse == null)
				return null;
			UniqueElementFinder<I> pef = getParent().getElementFinder();
			if (pef == null)
				return null;
			return v -> pef.getUniqueElement(reverseValue(v));
		}

		@Override
		public Comparator<? super T> comparator() {
			if (theReverse == null)
				return null;
			Comparator<? super I> pc = getParent().comparator();
			if (pc == null)
				return null;
			return (v1, v2) -> pc.compare(reverseValue(v1), reverseValue(v2));
		}

		protected T combineValue(I source) {
			if (badCombineValues > 0 || (source == null && !combineNulls))
				return null;
			else
				return theCombination.apply(new CombinedValues<I>() {
					@Override
					public I getElement() {
						return source;
					}

					@Override
					public <V> V get(ObservableValue<V> arg) {
						ArgHolder<V> holder = (ArgHolder<V>) theArgs.get(arg);
						if (holder == null)
							throw new IllegalArgumentException("Unrecognized value: " + arg);
						return holder.value;
					}
				});
		}

		protected I reverseValue(T dest) {
			if (badCombineValues > 0 || (dest == null && !reverseNulls))
				return null;
			else
				return theReverse.apply(new CombinedValues<T>() {
					@Override
					public T getElement() {
						return dest;
					}

					@Override
					public <V> V get(ObservableValue<V> arg) {
						ArgHolder<V> holder = (ArgHolder<V>) theArgs.get(arg);
						if (holder == null)
							throw new IllegalArgumentException("Unrecognized value: " + arg);
						return holder.value;
					}
				});
		}

		@Override
		public FilterMapResult<I, T> mapTop(FilterMapResult<I, T> source) {
			source.result = combineValue(source.source);
			return source;
		}

		@Override
		public FilterMapResult<T, I> reverseTop(FilterMapResult<T, I> dest) {
			if (theReverse == null) {
				dest.error = StdMsg.UNSUPPORTED_OPERATION;
				return dest;
			}
			dest.result = reverseValue(dest.source);
			if (dest.result == null && !combineNulls)
				dest.error = StdMsg.NULL_DISALLOWED;
			return dest;
		}

		@Override
		public CollectionElementManager<E, I, T> createElement(ElementId id, E init, Object cause) {
			class CombinedCollectionElement extends CollectionElementManager<E, I, T> implements CombinedValues<I> {
				private T theValue;
				private I theSource;

				CombinedCollectionElement() {
					super(CombinedCollectionManager.this, CombinedCollectionManager.this.getParent().createElement(id, init, cause), id);
				}

				@Override
				public T get() {
					return theValue;
				}

				@Override
				protected boolean refresh(I source, Object cause) {
					if (badCombineValues > 0 || (source == null && !combineNulls))
						theValue = null;
					else {
						theSource = source;
						theValue = theCombination.apply(this);
						theSource = null;
					}
					return true;
				}

				@Override
				public I getElement() {
					return theSource;
				}

				@Override
				public <V> V get(ObservableValue<V> arg) {
					ArgHolder<V> holder = (ArgHolder<V>) theArgs.get(arg);
					if (holder == null)
						throw new IllegalArgumentException("Unrecognized value: " + arg);
					return holder.value;
				}
			}
			return new CombinedCollectionElement();
		}

		@Override
		public void begin() {
			for (Map.Entry<ObservableValue<?>, ArgHolder<?>> arg : theArgs.entrySet()) {
				ArgHolder<?> holder = arg.getValue();
				holder.action = evt -> {
					try (Transaction t = lock(true, null)) {
						if (!holder.combineNull && holder.value == null)
							badCombineValues--;
						((ArgHolder<Object>) holder).value = evt.getValue();
						if (!holder.combineNull && holder.value == null)
							badCombineValues++;
						getUpdateListener().accept(new CollectionUpdate(this, null, evt));
					}
				};
				WeakConsumer<ObservableValueEvent<?>> weak = new WeakConsumer<>(holder.action);
				weak.withSubscription(arg.getKey().act(weak));
			}
		}
	}

	public static class RefreshingCollectionManager<E, T> extends NonMappingCollectionManager<E, T> {
		private final Observable<?> theRefresh;
		private Consumer<Object> theAction;

		protected RefreshingCollectionManager(CollectionManager<E, ?, T> parent, Observable<?> refresh) {
			super(parent);
			theRefresh = refresh;
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return getParent().equivalence();
		}

		@Override
		public void begin() {
			theAction = v -> getUpdateListener().accept(new CollectionUpdate(this, null, v));
			WeakConsumer<Object> weak = new WeakConsumer<>(theAction);
			weak.withSubscription(theRefresh.act(weak));
		}

		@Override
		public CollectionElementManager<E, ?, T> createElement(ElementId id, E init, Object cause) {
			class RefreshingElement extends NonMappingCollectionElement<E, T> {
				public RefreshingElement() {
					super(RefreshingCollectionManager.this, RefreshingCollectionManager.this.getParent().createElement(id, init, cause),
						id);
				}

				@Override
				public T get() {
					return getParent().get();
				}

				@Override
				protected boolean refresh(T source, Object cause) {
					return true;
				}
			}
			return new RefreshingElement();
		}
	}

	public static class ElementRefreshingCollectionManager<E, T> extends NonMappingCollectionManager<E, T> {
		private static class RefreshHolder {
			final Observable<?> theRefresh;
			Consumer<Object> theAction;
			Subscription theSub;
			int theElementCount;

			RefreshHolder(Observable<?> refresh, Consumer<Object> action, Subscription sub) {
				theRefresh = refresh;
				theAction = action;
				theSub = sub;
			}
		}

		private final Function<? super T, ? extends Observable<?>> theRefresh;
		private final Map<Observable<?>, RefreshHolder> theRefreshObservables;

		protected ElementRefreshingCollectionManager(CollectionManager<E, ?, T> parent,
			Function<? super T, ? extends Observable<?>> refresh) {
			super(parent);
			theRefresh = refresh;
			theRefreshObservables = new IdentityHashMap<>();
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return getParent().equivalence();
		}

		@Override
		public CollectionElementManager<E, ?, T> createElement(ElementId id, E init, Object cause) {
			class ElementRefreshElement extends NonMappingCollectionElement<E, T> {
				private Observable<?> theRefreshObservable;

				protected ElementRefreshElement() {
					super(ElementRefreshingCollectionManager.this,
						ElementRefreshingCollectionManager.this.getParent().createElement(id, init, cause), id);
				}

				@Override
				public T get() {
					return getParent().get();
				}

				@Override
				public void removed(Object cause) {
					if (theRefreshObservable != null)
						removeRefreshObs(theRefreshObservable);
					super.removed(cause);
				}

				@Override
				protected boolean applies(CollectionUpdate update) {
					return super.applies(update) && ((ElementRefreshUpdate) update).getRefreshObs() == theRefreshObservable;
				}

				@Override
				public ElementUpdateResult update(CollectionUpdate update,
					Consumer<Consumer<MutableObservableElement<? extends E>>> sourceElement) {
					if (applies(update)) {
						refresh(getParent().get(), update.getCause());
						return ElementUpdateResult.FireUpdate;
					} else {
						ElementUpdateResult parentResult = getParent().update(update, sourceElement);
						switch (parentResult) {
						case DoesNotApply:
						case AppliedNoUpdate:
							return parentResult;
						case FireUpdate:
							T value = getParent().get();
							Observable<?> refreshObs = theRefresh.apply(value);
							if (theRefreshObservable != refreshObs) {
								removeRefreshObs(theRefreshObservable);
								newRefreshObs(refreshObs);
							}
							refresh(value, update.getCause());
							return ElementUpdateResult.FireUpdate;
						}
						throw new IllegalStateException("Unrecognized update result " + parentResult);
					}
				}

				@Override
				protected boolean refresh(T source, Object cause) {
					return true;
				}

				private void removeRefreshObs(Observable<?> refreshObs) {
					if (refreshObs != null) {
						RefreshHolder holder = theRefreshObservables.get(refreshObs);
						holder.theElementCount--;
						if (holder.theElementCount == 0) {
							theRefreshObservables.remove(refreshObs);
							holder.theSub.unsubscribe();
						}
					}
				}

				private void newRefreshObs(Observable<?> refreshObs) {
					if (refreshObs != null)
						theRefreshObservables.computeIfAbsent(refreshObs, this::createHolder).theElementCount++;
					theRefreshObservable = refreshObs;
				}

				private RefreshHolder createHolder(Observable<?> refreshObs) {
					Consumer<Object> action = v -> ElementRefreshingCollectionManager.this.update(refreshObs, v);
					WeakConsumer<Object> weak = new WeakConsumer<>(action);
					Subscription sub = refreshObs.act(weak);
					weak.withSubscription(sub);
					return new RefreshHolder(refreshObs, action, sub);
				}
			}
			return new ElementRefreshElement();
		}

		private void update(Observable<?> refreshObs, Object cause) {
			getUpdateListener().accept(new ElementRefreshUpdate(refreshObs, cause));
		}

		@Override
		public void begin() {}

		private class ElementRefreshUpdate extends CollectionUpdate {
			private final Observable<?> theRefreshObs;

			ElementRefreshUpdate(Observable<?> refreshObs, Object cause) {
				super(ElementRefreshingCollectionManager.this, null, cause);
				theRefreshObs = refreshObs;
			}

			Observable<?> getRefreshObs() {
				return theRefreshObs;
			}
		}
	}

	public static class ModFilteredCollectionManager<E, T> extends NonMappingCollectionManager<E, T> {
		private final String theImmutableMessage;
		private final boolean areUpdatesAllowed;
		private final String theAddMessage;
		private final String theRemoveMessage;
		private final Function<? super T, String> theAddFilter;
		private final Function<? super T, String> theRemoveFilter;

		public ModFilteredCollectionManager(CollectionManager<E, ?, T> parent, String immutableMessage, boolean allowUpdates,
			String addMessage, String removeMessage, Function<? super T, String> addFilter, Function<? super T, String> removeFilter) {
			super(parent);

			theImmutableMessage = immutableMessage;
			areUpdatesAllowed = allowUpdates;
			theAddMessage = addMessage;
			theRemoveMessage = removeMessage;
			theAddFilter = addFilter;
			theRemoveFilter = removeFilter;
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return getParent().equivalence();
		}

		@Override
		public FilterMapResult<T, T> reverseTop(FilterMapResult<T, T> dest) {
			dest.error = checkAdd(dest.source);
			if (dest.error == null)
				dest.result = dest.source;
			return dest;
		}

		@Override
		protected void begin() {}

		@Override
		public CollectionElementManager<E, ?, T> createElement(ElementId id, E init, Object cause) {
			class ModFilteredElement extends NonMappingCollectionElement<E, T> {
				ModFilteredElement() {
					super(ModFilteredCollectionManager.this, ModFilteredCollectionManager.this.getParent().createElement(id, init, cause),
						id);
				}

				@Override
				public T get() {
					return getParent().get();
				}

				@Override
				protected boolean refresh(T source, Object cause) {
					return true;
				}

				@Override
				protected String filterRemove(boolean isRemoving, Object cause) {
					if (isRemoving)
						tryRemove(get());
					else {
						String msg = checkRemove(get());
						if (msg != null)
							return msg;
					}
					return super.filterRemove(isRemoving, cause);
				}

				@Override
				protected FilterMapResult<T, E> filterAccept(FilterMapResult<T, E> value, boolean isReplacing, Object cause) {
					if (isReplacing)
						tryReplace(get(), value.source);
					else {
						value.error = checkReplace(get(), value.source);
						if (value.error != null)
							return value;
					}
					value.error = checkReplace(get(), value.source);
					if (value.error != null)
						return value;
					return super.filterAccept(value, isReplacing, cause);
				}

				@Override
				protected FilterMapResult<T, E> filterAdd(FilterMapResult<T, E> value, boolean before, boolean isAdding, Object cause) {
					if (isAdding)
						tryAdd(value.source);
					else {
						value.error = checkAdd(value.source);
						if (value.error != null)
							return value;
					}
					return super.filterAdd(value, before, isAdding, cause);
				}

				@Override
				protected FilterMapResult<T, E> filterInterceptSet(FilterMapResult<T, E> value) {
					value.error = checkReplace(get(), value.source);
					if (value.error != null)
						return value;
					return super.filterInterceptSet(value);
				}

				@Override
				protected T interceptSet(FilterMapResult<T, E> value, Object cause)
					throws UnsupportedOperationException, IllegalArgumentException {
					value.error = checkReplace(get(), value.source);
					if (value.error != null)
						throw new IllegalArgumentException(value.error);
					return super.interceptSet(value, cause);
				}
			}
			return new ModFilteredElement();
		}

		// Remove?
		private boolean isAddFiltered() {
			return theImmutableMessage != null || theAddMessage != null || theAddFilter != null;
		}

		public String checkAdd(T value) {
			String msg = null;
			if (theAddFilter != null)
				msg = theAddFilter.apply(value);
			if (msg == null && theAddMessage != null)
				msg = theAddMessage;
			if (msg == null && theImmutableMessage != null)
				msg = theImmutableMessage;
			return msg;
		}

		public T tryAdd(T value) {
			String msg = null;
			if (theAddFilter != null)
				msg = theAddFilter.apply(value);
			if (msg != null)
				throw new IllegalArgumentException(msg);
			if (theAddMessage != null)
				throw new UnsupportedOperationException(theAddMessage);
			if (theImmutableMessage != null)
				throw new UnsupportedOperationException(theImmutableMessage);
			return value;
		}

		// Remove?
		private boolean isRemoveFiltered() {
			return theImmutableMessage != null || theRemoveMessage != null || theRemoveFilter != null;
		}

		public String checkRemove(Object value) {
			String msg = null;
			if (theRemoveFilter != null) {
				if (value != null && !getTargetType().getRawType().isInstance(value))
					msg = StdMsg.BAD_TYPE;
				msg = theRemoveFilter.apply((T) value);
			}
			if (msg == null && theRemoveMessage != null)
				msg = theRemoveMessage;
			if (msg == null && theImmutableMessage != null)
				msg = theImmutableMessage;
			return msg;
		}

		public T tryRemove(T value) {
			String msg = null;
			if (theRemoveFilter != null)
				msg = theRemoveFilter.apply(value);
			if (msg != null)
				throw new IllegalArgumentException(msg);
			if (theRemoveMessage != null)
				throw new UnsupportedOperationException(theRemoveMessage);
			if (theImmutableMessage != null)
				throw new UnsupportedOperationException(theImmutableMessage);
			return value;
		}

		public String checkReplace(T current, T newValue) {
			if (current == newValue)
				return areUpdatesAllowed ? null : theImmutableMessage;
			String msg = checkRemove(current);
			if (msg == null)
				msg = checkAdd(newValue);
			return msg;
		}

		public T tryReplace(T current, T newValue) {
			if (current == newValue) {
				if (areUpdatesAllowed)
					return current;
				throw new UnsupportedOperationException(theImmutableMessage);
			}
			tryRemove(current);
			tryAdd(newValue);
			return newValue;
		}
	}
}
