package org.observe.collect;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Subscription;
import org.observe.assoc.ObservableMultiMap;
import org.observe.assoc.ObservableMultiMap.MultiMapFlow;
import org.observe.assoc.ObservableSortedMultiMap;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollection.CombinedCollectionBuilder;
import org.observe.collect.ObservableCollection.CombinedValues;
import org.observe.collect.ObservableCollection.ElementSetter;
import org.observe.collect.ObservableCollection.MappedCollectionBuilder;
import org.observe.collect.ObservableCollection.ModFilterBuilder;
import org.observe.collect.ObservableCollection.UniqueDataFlow;
import org.observe.collect.ObservableCollection.UniqueSortedDataFlow;
import org.observe.collect.ObservableCollectionDataFlowImpl.CollectionElementManager;
import org.observe.collect.ObservableCollectionDataFlowImpl.DerivedCollectionElement;
import org.observe.collect.ObservableCollectionDataFlowImpl.FilterMapResult;
import org.observe.collect.ObservableCollectionDataFlowImpl.FilteredCollectionManager;
import org.observe.collect.ObservableCollectionDataFlowImpl.NonMappingCollectionElement;
import org.observe.collect.ObservableCollectionImpl.ActiveDerivedCollection;
import org.observe.collect.ObservableCollectionImpl.DerivedCollection;
import org.observe.collect.ObservableCollectionImpl.DerivedLWCollection;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.BetterHashSet;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.tree.BetterTreeList;
import org.qommons.tree.BetterTreeMap;
import org.qommons.tree.BetterTreeSet;
import org.qommons.tree.BinaryTreeNode;

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
		private boolean isError;
		private String rejectReason;

		public FilterMapResult() {}

		public FilterMapResult(E src) {
			source = src;
		}

		public boolean isAccepted() {
			return rejectReason == null;
		}

		public boolean isError() {
			return isError;
		}

		public String getRejectReason() {
			return rejectReason;
		}

		public void clearRejection() {
			rejectReason = null;
			isError = false;
		}

		public <X extends Throwable> String throwIfError(Function<String, X> type) throws X {
			if (rejectReason == null)
				return null;
			if (isError)
				throw type.apply(rejectReason);
			return rejectReason;
		}

		public FilterMapResult<E, T> reject(String reason, boolean error) {
			if (error && reason == null)
				throw new IllegalArgumentException("Need a reason for the error");
			result = null;
			rejectReason = reason;
			isError = error;
			return this;
		}

		public FilterMapResult<E, T> maybeReject(String reason, boolean error) {
			if (reason != null)
				reject(reason, error);
			return this;
		}
	}

	public static interface ElementFinder<T> {
		DerivedCollectionElement<T> findElement(BetterSortedSet<DerivedCollectionElement<T>> elements, T value, boolean first);
	}

	public static interface CollectionOperation<E, I, T> extends Transactable {
		TypeToken<T> getTargetType();

		Equivalence<? super T> equivalence();
	}

	public static interface PassiveCollectionManager<E, I, T> extends CollectionOperation<E, I, T> {
		T map(E source);

		boolean isReversible();

		FilterMapResult<T, E> reverse(FilterMapResult<T, E> dest);

		default FilterMapResult<T, E> reverse(T dest) {
			return reverse(new FilterMapResult<>(dest));
		}
	}

	public static interface ActiveCollectionManager<E, I, T> extends CollectionOperation<E, I, T> {
		/** @return Whether this manager always has a representation for each element in the source collection */
		boolean isEachRepresented();

		ElementFinder<T> getElementFinder();

		FilterMapResult<T, E> canAdd(FilterMapResult<T, E> toAdd);

		DerivedCollectionElement<T> addElement(T value, boolean first);

		void begin(Consumer<DerivedCollectionElement<T>> onElement, Observable<?> until);
	}

	public static interface DerivedCollectionElement<E> extends Comparable<DerivedCollectionElement<E>> {
		void setListener(CollectionElementListener<E> listener);

		E get();

		String isEnabled();

		String isAcceptable(E value);

		void set(E value) throws UnsupportedOperationException, IllegalArgumentException;

		String canRemove();

		void remove() throws UnsupportedOperationException;

		String canAdd(E value, boolean before);

		DerivedCollectionElement<E> add(E value, boolean before) throws UnsupportedOperationException, IllegalArgumentException;
	}

	public static interface CollectionElementListener<E> {
		void update(E oldValue, E newValue, Object cause);

		void removed(E value, Object cause);
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
		public CollectionDataFlow<E, T, T> filter(Function<? super T, String> filter) {
			return new FilterOp<>(theSource, this, filter, false);
		}

		@Override
		public CollectionDataFlow<E, T, T> filterStatic(Function<? super T, String> filter) {
			return new FilterOp<>(theSource, this, filter, true);
		}

		@Override
		public <X> CollectionDataFlow<E, T, T> whereContained(ObservableCollection<X> other, boolean include) {
			return new IntersectionFlow<>(theSource, this, other, !include);
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
		public <X> CollectionDataFlow<E, ?, X> flatMapF(TypeToken<X> target,
			Function<? super T, ? extends CollectionDataFlow<?, ?, ? extends X>> map) {
			return new FlattenedOp<>(theSource, this, target, map);
		}

		@Override
		public <V, X> ObservableCollection.CombinedCollectionBuilder2<E, T, V, X> combineWith(ObservableValue<V> value,
			TypeToken<X> target) {
			return new ObservableCollection.CombinedCollectionBuilder2<>(theSource, this, target, value);
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
			return new ObservableSetImpl.UniqueOp<>(theSource, this, alwaysUseFirst);
		}

		@Override
		public UniqueSortedDataFlow<E, T, T> uniqueSorted(Comparator<? super T> compare, boolean alwaysUseFirst) {
			return new ObservableSortedSetImpl.UniqueSortedDataFlowImpl<>(theSource, this, compare, alwaysUseFirst);
		}

		@Override
		public <K> MultiMapFlow<E, K, T> groupBy(Function<? super CollectionDataFlow<E, I, T>, UniqueDataFlow<E, ?, K>> keyFlow,
			boolean staticCategories) {
			UniqueDataFlow<E, ?, K> keyFlowed = keyFlow.apply(this);
			CollectionOperation<E, ?, K> keyMgr = keyFlowed.manageActive();
			CollectionOperation<E, ?, T> valueMgr = isPassive() ? managePassive() : manageActive();
			return new ObservableMultiMap.DefaultMultiMapFlow<>(theSource, keyFlowed, theTargetType, key -> {
				FilterMapResult<E, K> mappedKey = keyMgr.map(key);
				if (!mappedKey.isAccepted()) // Invalid key
					return ObservableCollection.constant(theTargetType).flow();
				Function<T, String> filter = value -> {
					// Stinks to have to back up to the root type and then map back to the key,
					// but right now the API doesn't allow for better
					FilterMapResult<T, E> reversed = valueMgr.reverse(value);
					if (!reversed.isAccepted())
						return reversed.getRejectReason();
					FilterMapResult<E, K> mappedValueKey = keyMgr.map(reversed.result);
					if (!mappedValueKey.isAccepted())
						return mappedValueKey.getRejectReason();
					else if (keyMgr.equivalence().elementEquals((K) key, mappedValueKey.result))
						return null;
					else
						return StdMsg.WRONG_GROUP;
				};
				if (staticCategories)
					return filterStatic(filter);
				else
					return filter(filter);
			});
		}

		@Override
		public <K> MultiMapFlow<E, K, T> groupBy(Function<? super CollectionDataFlow<E, I, T>, CollectionDataFlow<E, ?, K>> keyFlow,
			Comparator<? super K> keyCompare, boolean staticCategories) {
			UniqueSortedDataFlow<E, ?, K> keyFlowed = keyFlow.apply(this).uniqueSorted(keyCompare, true);
			CollectionOperation<E, ?, K> keyMgr = keyFlowed.manageActive();
			CollectionOperation<E, ?, T> valueMgr = isPassive() ? managePassive() : manageActive();
			// Can't think of a real easy way to pull this code out so it's not copy-and-paste from the method above
			return new ObservableSortedMultiMap.DefaultSortedMultiMapFlow<>(theSource, keyFlowed, theTargetType, key -> {
				FilterMapResult<E, K> mappedKey = keyMgr.map(key);
				if (!mappedKey.isAccepted()) // Invalid key
					return ObservableCollection.constant(theTargetType).flow();
				Function<T, String> filter = value -> {
					// Stinks to have to back up to the root type and then map back to the key,
					// but right now the API doesn't allow for better
					FilterMapResult<T, E> reversed = valueMgr.reverse(value);
					if (!reversed.isAccepted())
						return reversed.getRejectReason();
					FilterMapResult<E, K> mappedValueKey = keyMgr.map(reversed.result);
					if (!mappedValueKey.isAccepted())
						return mappedValueKey.getRejectReason();
					else if (keyMgr.equivalence().elementEquals((K) key, mappedValueKey.result))
						return null;
					else
						return StdMsg.WRONG_GROUP;
				};
				if (staticCategories)
					return filterStatic(filter);
				else
					return filter(filter);
			});
		}

		@Override
		public ObservableCollection<T> collectPassive() {
			if (!isPassive())
				throw new UnsupportedOperationException("This flow does not support passive collection");
			return new DerivedLWCollection<>(getSource(), managePassive());
		}

		@Override
		public ObservableCollection<T> collect(Observable<?> until) {
			if (until == Observable.empty && isPassive())
				return new DerivedLWCollection<>(getSource(), managePassive());
			else
				return new DerivedCollection<>(getSource(), manageActive(), until);
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
		public boolean isPassive() {
			return false;
		}

		@Override
		public PassiveCollectionManager<E, ?, T> managePassive() {
			return null;
		}

		@Override
		public ActiveCollectionManager<E, ?, T> manageActive() {
			return new SortedManager<>(getParent().manageActive(), theCompare);
		}
	}

	public static class BaseCollectionDataFlow<E> extends AbstractDataFlow<E, E, E> {
		protected BaseCollectionDataFlow(ObservableCollection<E> source) {
			super(source, null, source.getType());
		}

		@Override
		public boolean isPassive() {
			return true;
		}

		@Override
		public PassiveCollectionManager<E, ?, E> managePassive() {
			return new BaseCollectionPassThrough<>(getSource().getType(), getSource().equivalence(), getSource().isLockSupported());
		}

		@Override
		public ActiveCollectionManager<E, ?, E> manageActive() {
			return new BaseCollectionManager<>(getSource());
		}

		@Override
		public ObservableCollection<E> collect(Observable<?> until) {
			if (until == Observable.empty)
				return getSource();
			else
				return new ActiveDerivedCollection<>(getSource(), manageActive(), until);
		}
	}

	public static class FilterOp<E, T> extends AbstractDataFlow<E, T, T> {
		private final Function<? super T, String> theFilter;
		private final boolean isStaticFilter;

		protected FilterOp(ObservableCollection<E> source, AbstractDataFlow<E, ?, T> parent, Function<? super T, String> filter,
			boolean staticFilter) {
			super(source, parent, parent.getTargetType());
			theFilter = filter;
			isStaticFilter = staticFilter;
		}

		@Override
		public boolean isPassive() {
			return false;
		}

		@Override
		public PassiveCollectionManager<E, ?, T> managePassive() {
			return null;
		}

		@Override
		public ActiveCollectionManager<E, ?, T> manageActive() {
			return new FilteredCollectionManager<>(getParent().manageActive(), theFilter, isStaticFilter);
		}
	}

	protected static class IntersectionFlow<E, T, X> extends AbstractDataFlow<E, T, T> {
		private final ObservableCollection<X> theFilter;
		private final boolean isExclude;

		protected IntersectionFlow(ObservableCollection<E> source, CollectionDataFlow<E, ?, T> parent, ObservableCollection<X> filter,
			boolean exclude) {
			super(source, parent, parent.getTargetType());
			theFilter = filter;
			isExclude = exclude;
		}

		@Override
		public boolean isPassive() {
			return false;
		}

		@Override
		public PassiveCollectionManager<E, ?, T> managePassive() {
			return null;
		}

		@Override
		public ActiveCollectionManager<E, ?, T> manageActive() {
			return new IntersectionManager<>(getParent().manageActive(), theFilter, isExclude);
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
		public boolean isPassive() {
			return true;
		}

		@Override
		public AbstractCollectionManager<E, ?, T> manageCollection() {
			class EquivalenceSwitchedCollectionManager extends NonMappingCollectionManager<E, T> {
				EquivalenceSwitchedCollectionManager() {
					super(EquivalenceSwitchOp.this.getParent().manageCollection());
				}

				@Override
				public void createElement(ElementId id, E init, Object cause, Consumer<CollectionElementManager<E, ?, T>> onElement) {
					getParent().addElement(id, init, cause, onElement);
				}

				@Override
				protected void begin(Observable<?> until) {}

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
		private final Function<? super T, ? extends I> theReverse;
		private final ElementSetter<? super I, ? super T> theElementReverse;
		private final boolean reEvalOnUpdate;
		private final boolean fireIfUnchanged;
		private final boolean isCached;

		protected MapOp(ObservableCollection<E> source, AbstractDataFlow<E, ?, I> parent, TypeToken<T> target,
			Function<? super I, ? extends T> map, Function<? super T, ? extends I> reverse,
			ElementSetter<? super I, ? super T> elementReverse, boolean reEvalOnUpdate, boolean fireIfUnchanged, boolean cached) {
			super(source, parent, target);
			theMap = map;
			theReverse = reverse;
			theElementReverse = elementReverse;
			this.reEvalOnUpdate = reEvalOnUpdate;
			this.fireIfUnchanged = fireIfUnchanged;
			isCached = cached;
		}

		@Override
		public boolean isLightWeight() {
			if (isCached)
				return false;
			return getParent().isLightWeight();
		}

		@Override
		public CollectionManager<E, ?, T> manageCollection() {
			return new MappedCollectionManager<>(getParent().manageCollection(), getTargetType(), theMap, theReverse, theElementReverse,
				reEvalOnUpdate, fireIfUnchanged, isCached);
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
		private final Set<ObservableValue<?>> theArgs;
		private final Function<? super CombinedValues<? extends I>, ? extends T> theCombination;
		private final Function<? super CombinedValues<? extends T>, ? extends I> theReverse;
		private final boolean isCached;

		protected CombinedCollectionDef(ObservableCollection<E> source, AbstractDataFlow<E, ?, I> parent, TypeToken<T> target,
			Set<ObservableValue<?>> args, Function<? super CombinedValues<? extends I>, ? extends T> combination,
			Function<? super CombinedValues<? extends T>, ? extends I> reverse, boolean cached) {
			super(source, parent, target);
			theArgs = Collections.unmodifiableSet(args);
			theCombination = combination;
			theReverse = reverse;
			isCached = cached;
		}

		@Override
		public boolean isLightWeight() {
			return false;
		}

		@Override
		public AbstractCollectionManager<E, ?, T> manageCollection() {
			return new CombinedCollectionManager<>(getParent().manageCollection(), getTargetType(), theArgs, theCombination, theReverse,
				isCached);
		}
	}

	public static abstract class AbstractCombinedCollectionBuilder<E, I, R> implements CombinedCollectionBuilder<E, I, R> {
		private final ObservableCollection<E> theSource;
		private final AbstractDataFlow<E, ?, I> theParent;
		private final TypeToken<R> theTargetType;
		private final BetterHashSet<ObservableValue<?>> theArgs;
		private Function<? super CombinedValues<? extends R>, ? extends I> theReverse;
		private boolean isCached;

		protected AbstractCombinedCollectionBuilder(ObservableCollection<E> source, AbstractDataFlow<E, ?, I> parent,
			TypeToken<R> targetType) {
			theSource = source;
			theParent = parent;
			theTargetType = targetType;
			theArgs = BetterHashSet.build().identity().unsafe().buildSet();
			isCached = true;
		}

		protected ObservableCollection<E> getSource() {
			return theSource;
		}

		protected void addArg(ObservableValue<?> arg) {
			if (theArgs.contains(arg))
				throw new IllegalArgumentException("Argument " + arg + " is already combined");
			theArgs.add(arg);
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

		@Override
		public AbstractCombinedCollectionBuilder<E, I, R> withReverse(Function<? super CombinedValues<? extends R>, ? extends I> reverse) {
			theReverse = reverse;
			return this;
		}

		@Override
		public AbstractCombinedCollectionBuilder<E, I, R> cache(boolean cache) {
			isCached = cache;
			return this;
		}

		@Override
		public CollectionDataFlow<E, I, R> build(Function<? super CombinedValues<? extends I>, ? extends R> combination) {
			return new CombinedCollectionDef<>(theSource, theParent, theTargetType,
				BetterHashSet.build().identity().unsafe().buildSet(theArgs), combination, theReverse, isCached);
		}
	}

	public static class RefreshOp<E, T> extends AbstractDataFlow<E, T, T> {
		private final Observable<?> theRefresh;

		protected RefreshOp(ObservableCollection<E> source, AbstractDataFlow<E, ?, T> parent, Observable<?> refresh) {
			super(source, parent, parent.getTargetType());
			theRefresh = refresh;
		}

		@Override
		public boolean isLightWeight() {
			return false;
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
		public boolean isLightWeight() {
			return false;
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
		public boolean isLightWeight() {
			return getParent().isLightWeight();
		}

		@Override
		public CollectionManager<E, ?, T> manageCollection() {
			return new ModFilteredCollectionManager<>(getParent().manageCollection(), theImmutableMessage, areUpdatesAllowed, theAddMessage,
				theRemoveMessage, theAddFilter, theRemoveFilter);
		}
	}

	private static class FlattenedOp<E, I, T> extends AbstractDataFlow<E, I, T> {
		private final Function<? super I, ? extends CollectionDataFlow<?, ?, ? extends T>> theMap;

		FlattenedOp(ObservableCollection<E> source, CollectionDataFlow<E, ?, I> parent, TypeToken<T> targetType,
			Function<? super I, ? extends CollectionDataFlow<?, ?, ? extends T>> map) {
			super(source, parent, targetType);
			theMap = map;
		}

		@Override
		public boolean isLightWeight() {
			return false;
		}

		@Override
		public CollectionManager<E, ?, T> manageCollection() {
			return new FlattenedManager<>(getParent().manageCollection(), getTargetType(), theMap);
		}
	}

	public static interface CollectionManager<E, I, T> extends Transactable {
		TypeToken<T> getTargetType();

		Equivalence<? super T> equivalence();

		boolean isFiltered();

		boolean isReversible();

		ElementFinder<T> getElementFinder();

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

		void begin(Consumer<CollectionElementManager<E, ?, T>> onElement, Consumer<CollectionUpdate> onUpdate, Observable<?> until);

		ElementController<E> addElement(ElementId id, E init, Object cause);
	}

	public interface ElementController<E> {
		ElementId getSourceId();

		void set(E value, Object cause);

		void remove();
	}

	public static abstract class AbstractPassiveManager<E, I, T> implements PassiveCollectionManager<E, I, T> {

		protected abstract FilterMapResult<I, T> mapTop(FilterMapResult<I, T> source);

		@Override
		public FilterMapResult<E, T> map(FilterMapResult<E, T> source) {
			FilterMapResult<I, T> intermediate = (FilterMapResult<I, T>) source;
			if (getParent() != null) {
				getParent().map((FilterMapResult<E, I>) source);
				if (source.isAccepted())
					intermediate.source = ((FilterMapResult<E, I>) source).result;
			}
			if (source.isAccepted())
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
			if (dest.isAccepted() && getParent() != null) {
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
	}

	public static abstract class AbstractCollectionManager<E, I, T> implements ActiveCollectionManager<E, I, T> {
		private final ActiveCollectionManager<E, ?, I> theParent;
		private final TypeToken<T> theTargetType;
		private boolean isBegun;
		private Consumer<DerivedCollectionElement<T>> theElementAccepter;

		protected AbstractCollectionManager(ActiveCollectionManager<E, ?, I> parent, TypeToken<T> targetType) {
			theParent = parent;
			theTargetType = targetType;
		}

		protected ActiveCollectionManager<E, ?, I> getParent() {
			return theParent;
		}

		protected Consumer<DerivedCollectionElement<T>> getElementAccepter() {
			return theElementAccepter;
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theParent.lock(write, cause);
		}

		@Override
		public TypeToken<T> getTargetType() {
			return theTargetType;
		}

		@Override
		public boolean isEachRepresented() {
			return theParent.isEachRepresented();
		}

		protected abstract FilterMapResult<T, I> canAddTop(FilterMapResult<T, I> dest);

		@Override
		public FilterMapResult<T, E> canAdd(FilterMapResult<T, E> toAdd) {
			FilterMapResult<T, I> top = (FilterMapResult<T, I>) toAdd;
			canAddTop(top);
			if (toAdd.isAccepted() && getParent() != null) {
				FilterMapResult<I, E> intermediate = (FilterMapResult<I, E>) toAdd;
				intermediate.source = top.result;
				getParent().canAdd(intermediate);
			}
			return toAdd;
		}

		protected abstract void begin(Observable<?> until);

		@Override
		public void begin(Consumer<DerivedCollectionElement<T>> onElement, Observable<?> until) {
			if (isBegun)
				throw new IllegalStateException("Cannot begin twice");
			isBegun = true;
			theElementAccepter = onElement;
			begin(until);
			if (theParent != null)
				theParent.begin(this::elementCreated, until);
		}

		protected abstract void elementCreated(DerivedCollectionElement<I> parent);
	}

	public static abstract class CollectionElementManager<E, I, T> implements Comparable<CollectionElementManager<E, ?, T>> {
		private final AbstractCollectionManager<E, I, T> theCollection;
		private final CollectionElementManager<E, ?, I> theParent;
		private final BetterTreeList<FlowSourceListener<E, T>> theSourceListeners;
		private final ElementId theSourceId;

		protected CollectionElementManager(AbstractCollectionManager<E, I, T> collection, CollectionElementManager<E, ?, I> parent,
			ElementId id, E init, Object cause) {
			theCollection = collection;
			theParent = parent;
			theSourceId = id;
			theSourceListeners = new BetterTreeList<>(false); // Super light-weight collection

			if (parent != null)
				parent.addSourceListener((evt, updates) -> fireNewValue(evt.onward(refresh(evt.value, cause, updates)), updates));
		}

		protected CollectionElementManager<E, ?, I> getParent() {
			return theParent;
		}

		public void addSourceListener(FlowSourceListener<E, T> listener) {
			theSourceListeners.add(listener);
		}

		protected void fireNewValue(FlowSourceEvent<E, T> event, Collection<CollectionUpdate> updates) {
			// Re-using the event
			T value = event.value;
			for (FlowSourceListener<E, T> listener : theSourceListeners) {
				listener.changed(event, updates);
				event.value = value;
			}
		}

		public ElementId getElementId() {
			return theSourceId;
		}

		@Override
		public int compareTo(CollectionElementManager<E, ?, T> other) {
			if (getParent() == null)
				return theSourceId.compareTo(other.theSourceId);
			return getParent().compareTo(((CollectionElementManager<E, I, T>) other).getParent());
		}

		public boolean isPresent() {
			// Most elements don't filter
			return theParent.isPresent();
		}

		public abstract T get();

		public MutableCollectionElement<T> mutable(ObservableCollection<E> source) {
			class MutableManagedElement implements MutableCollectionElement<T> {
				private final MutableCollectionElement<? extends E> theWrapped;

				MutableManagedElement(MutableCollectionElement<? extends E> wrapped) {
					theWrapped = wrapped;
				}

				@Override
				public T get() {
					return CollectionElementManager.this.get();
				}

				@Override
				public ElementId getElementId() {
					return CollectionElementManager.this.getElementId();
				}

				@Override
				public String isEnabled() {
					if (isInterceptingSet())
						return null;
					String msg = filterEnabled();
					if (msg == null)
						msg = theWrapped.isEnabled();
					return msg;
				}

				@Override
				public String isAcceptable(T value) {
					if (isInterceptingSet())
						return filterInterceptSet(new FilterMapResult<>(value)).getRejectReason();
					FilterMapResult<T, E> result = CollectionElementManager.this.filterAccept(new FilterMapResult<>(value), false);
					if (!result.isAccepted())
						return result.getRejectReason();
					if (result.result != null && !theCollection.getTargetType().getRawType().isInstance(result.result))
						return MutableCollectionElement.StdMsg.BAD_TYPE;
					return ((MutableCollectionElement<E>) theWrapped).isAcceptable(result.result);
				}

				@Override
				public void set(T value) throws IllegalArgumentException, UnsupportedOperationException {
					if (isInterceptingSet()) {
						interceptSet(new FilterMapResult<>(value));
					}
					FilterMapResult<T, E> result = CollectionElementManager.this.filterAccept(new FilterMapResult<>(value), true);
					if (result.throwIfError(IllegalArgumentException::new) != null)
						return;
					if (result.result != null && !theCollection.getTargetType().getRawType().isInstance(result.result))
						throw new IllegalArgumentException(MutableCollectionElement.StdMsg.BAD_TYPE);
					((MutableCollectionElement<E>) theWrapped).set(result.result);
				}

				@Override
				public String canRemove() {
					String msg = filterRemove(false);
					if (msg == null)
						msg = theWrapped.canRemove();
					return msg;
				}

				@Override
				public void remove() throws UnsupportedOperationException {
					String msg = filterRemove(true);
					if (msg != null)
						throw new UnsupportedOperationException(msg);
					theWrapped.remove();
				}

				@Override
				public String canAdd(T value, boolean before) {
					FilterMapResult<T, E> result = filterAdd(new FilterMapResult<>(value), before, false);
					if (!result.isAccepted())
						return result.getRejectReason();
					if (result.result != null && !theCollection.getTargetType().getRawType().isInstance(result.result))
						return MutableCollectionElement.StdMsg.BAD_TYPE;
					return ((MutableCollectionElement<E>) theWrapped).canAdd(result.result, before);
				}

				@Override
				public ElementId add(T value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
					FilterMapResult<T, E> result = filterAdd(new FilterMapResult<>(value), before, true);
					if (result.throwIfError(IllegalArgumentException::new) != null)
						return null;
					if (result.result != null && !theCollection.getTargetType().getRawType().isInstance(result.result))
						throw new IllegalArgumentException(MutableCollectionElement.StdMsg.BAD_TYPE);
					return ((MutableCollectionElement<E>) theWrapped).add(result.result, before);
				}
			}
			return new MutableManagedElement(source.mutableElement(theSourceId));
		}

		protected String filterRemove(boolean isRemoving) {
			return getParent() == null ? null : getParent().filterRemove(isRemoving);
		}

		protected String filterEnabled() {
			return getParent() == null ? null : getParent().filterEnabled();
		}

		protected FilterMapResult<T, E> filterAccept(FilterMapResult<T, E> value, boolean isReplacing) {
			FilterMapResult<T, I> top = theCollection.reverseTop((FilterMapResult<T, I>) value);
			if (top.isAccepted() && getParent() != null) {
				FilterMapResult<I, E> intermediate = (FilterMapResult<I, E>) top;
				intermediate.source = top.result;
				getParent().filterAccept(intermediate, isReplacing);
			}
			return value;
		}

		protected FilterMapResult<T, E> filterAdd(FilterMapResult<T, E> value, boolean before, boolean isAdding) {
			FilterMapResult<T, I> top = theCollection.reverseTop((FilterMapResult<T, I>) value);
			if (top.isAccepted() && getParent() != null) {
				FilterMapResult<I, E> intermediate = (FilterMapResult<I, E>) top;
				intermediate.source = top.result;
				getParent().filterAdd(intermediate, before, isAdding);
			}
			return value;
		}

		protected boolean isInterceptingSet() {
			return getParent() == null ? false : getParent().isInterceptingSet();
		}

		protected FilterMapResult<T, E> filterInterceptSet(FilterMapResult<T, E> value) {
			if (getParent() == null)
				return value.reject(MutableCollectionElement.StdMsg.UNSUPPORTED_OPERATION, true);
			else if (!getParent().isInterceptingSet())
				return value.reject(MutableCollectionElement.StdMsg.UNSUPPORTED_OPERATION, true);
			FilterMapResult<T, I> top = theCollection.reverseTop((FilterMapResult<T, I>) value);
			if (top.isAccepted()) {
				FilterMapResult<I, E> intermediate = (FilterMapResult<I, E>) top;
				intermediate.source = top.result;
				getParent().filterInterceptSet(intermediate);
			}
			return value;
		}

		protected void interceptSet(FilterMapResult<T, E> value) throws UnsupportedOperationException, IllegalArgumentException {
			if (getParent() == null)
				throw new UnsupportedOperationException(MutableCollectionElement.StdMsg.UNSUPPORTED_OPERATION);
			FilterMapResult<T, I> top = theCollection.reverseTop((FilterMapResult<T, I>) value);
			if (top.throwIfError(IllegalArgumentException::new) != null)
				return;
			FilterMapResult<I, E> intermediate = (FilterMapResult<I, E>) top;
			intermediate.source = top.result;
			FilterMapResult<I, T> map = (FilterMapResult<I, T>) value;
			getParent().interceptSet(intermediate);
		}

		protected boolean applies(CollectionUpdate update) {
			return update instanceof CollectionUpdate && update.getCollection() == theCollection//
				&& (update.getElement() == null || update.getElement().equals(getElementId()));
		}

		protected abstract T refresh(I source, Object cause, Collection<CollectionUpdate> updates);
	}

	public static abstract class NonMappingCollectionManager<E, T> extends AbstractCollectionManager<E, T, T> {
		protected NonMappingCollectionManager(AbstractCollectionManager<E, ?, T> parent) {
			super(parent, parent.getTargetType());
		}

		@Override
		public ObservableCollectionDataFlowImpl.ElementFinder<T> getElementFinder() {
			return getParent().getElementFinder();
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
			ElementId id, E init, Object cause) {
			super(collection, parent, id, init, cause);
		}
	}

	public static class BaseCollectionManager<E> implements ActiveCollectionManager<E, E, E> {
		private final ObservableCollection<E> theSource;
		private Subscription theWeakSubscription;
		private final BetterTreeMap<ElementId, CollectionElementListener<E>> theElementListeners;

		public BaseCollectionManager(ObservableCollection<E> source) {
			theSource = source;
			theElementListeners = new BetterTreeMap<>(false, ElementId::compareTo);
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theSource.lock(write, cause);
		}

		@Override
		public TypeToken<E> getTargetType() {
			return theSource.getType();
		}

		@Override
		public Equivalence<? super E> equivalence() {
			return theSource.equivalence();
		}

		@Override
		public ObservableCollectionDataFlowImpl.ElementFinder<E> getElementFinder() {
			return null;
		}

		@Override
		public boolean isEachRepresented() {
			return true;
		}

		@Override
		public FilterMapResult<E, E> canAdd(FilterMapResult<E, E> toAdd) {
			return toAdd.maybeReject(theSource.canAdd(toAdd.source), true);
		}

		@Override
		public DerivedCollectionElement<E> addElement(E value, boolean first) {
			CollectionElement<E> srcEl = theSource.addElement(value, first);
			return srcEl == null ? null : new BaseDerivedElement(theSource.mutableElement(srcEl.getElementId()));
		}

		@Override
		public void begin(Consumer<DerivedCollectionElement<E>> onElement, Observable<?> until) {
			// Need to hold on to the subscription because it contains strong references that keep the listeners alive
			theWeakSubscription = WeakConsumer.build()//
				.<ObservableCollectionEvent<? extends E>> withAction(evt -> {
					switch (evt.getType()) {
					case add:
						BaseDerivedElement el = new BaseDerivedElement(theSource.mutableElement(evt.getElementId()));
						onElement.accept(el);
						break;
					case remove:
						theElementListeners.remove(evt.getElementId()).removed(evt.getOldValue(), evt);
						break;
					case set:
						theElementListeners.get(evt.getElementId()).update(evt.getOldValue(), evt.getNewValue(), evt);
						break;
					}
				}, action -> theSource.subscribe(action, true).removeAll())//
				.withUntil(until::act).build();
		}

		class BaseDerivedElement implements DerivedCollectionElement<E> {
			private final MutableCollectionElement<E> source;

			BaseDerivedElement(MutableCollectionElement<E> src) {
				source = src;
			}

			@Override
			public int compareTo(DerivedCollectionElement<E> o) {
				return source.getElementId().compareTo(((BaseDerivedElement) o).source.getElementId());
			}

			@Override
			public void setListener(CollectionElementListener<E> listener) {
				theElementListeners.put(source.getElementId(), listener);
			}

			@Override
			public E get() {
				return source.get();
			}

			@Override
			public String isEnabled() {
				return source.isEnabled();
			}

			@Override
			public String isAcceptable(E value) {
				return source.isAcceptable(value);
			}

			@Override
			public void set(E value) throws UnsupportedOperationException, IllegalArgumentException {
				source.set(value);
			}

			@Override
			public String canRemove() {
				return source.canRemove();
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				source.remove();
			}

			@Override
			public String canAdd(E value, boolean before) {
				return source.canAdd(value, before);
			}

			@Override
			public DerivedCollectionElement<E> add(E value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
				ElementId newId = source.add(value, before);
				return new BaseDerivedElement(theSource.mutableElement(newId));
			}
		}
	}

	public static class SortedManager2<E, T> implements ActiveCollectionManager<E, T, T> {
		private final ActiveCollectionManager<E, ?, T> theParent;
		private final Comparator<? super T> theCompare;

		public SortedManager2(ActiveCollectionManager<E, ?, T> parent, Comparator<? super T> compare) {
			theParent = parent;
			theCompare = compare;
		}

		@Override
		public TypeToken<T> getTargetType() {
			return theParent.getTargetType();
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return theParent.equivalence();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theParent.lock(write, cause);
		}

		@Override
		public boolean isEachRepresented() {
			return theParent.isEachRepresented();
		}

		@Override
		public ElementFinder<T> getElementFinder() {
			return null; // Even if the parent could've found it, the order will be mixed up now
		}

		@Override
		public FilterMapResult<T, E> canAdd(FilterMapResult<T, E> toAdd) {
			return theParent.canAdd(toAdd);
		}

		@Override
		public DerivedCollectionElement<T> addElement(T value, boolean first) {
			DerivedCollectionElement<T> parentEl = theParent.addElement(value, first);
			return parentEl == null ? null : new SortedElement(parentEl);
		}

		@Override
		public void begin(Consumer<DerivedCollectionElement<T>> onElement, Observable<?> until) {
			theParent.begin(parentEl -> onElement.accept(new SortedElement(parentEl)), until);
		}

		class SortedElement implements DerivedCollectionElement<T> {
			private final DerivedCollectionElement<T> theParentEl;

			SortedElement(DerivedCollectionElement<T> parentEl) {
				theParentEl = parentEl;
			}

			@Override
			public int compareTo(DerivedCollectionElement<T> o) {
				int comp = theCompare.compare(get(), o.get());
				if (comp == 0)
					comp = theParentEl.compareTo(((SortedElement) o).theParentEl);
				return comp;
			}

			@Override
			public void setListener(CollectionElementListener<T> listener) {
				theParentEl.setListener(listener);
			}

			@Override
			public T get() {
				return theParentEl.get();
			}

			@Override
			public String isEnabled() {
				return theParentEl.isEnabled();
			}

			@Override
			public String isAcceptable(T value) {
				return theParentEl.isAcceptable(value);
			}

			@Override
			public void set(T value) throws UnsupportedOperationException, IllegalArgumentException {
				theParentEl.set(value);
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
			public String canAdd(T value, boolean before) {
				return theParentEl.canAdd(value, before);
			}

			@Override
			public DerivedCollectionElement<T> add(T value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
				return new SortedElement(theParentEl.add(value, before));
			}
		}
	}

	public static class SortedManager<E, T> extends NonMappingCollectionManager<E, T> {
		private final Comparator<? super T> theCompare;
		private final Equivalence<? super T> theEquivalence;

		protected SortedManager(AbstractCollectionManager<E, ?, T> parent, Comparator<? super T> compare) {
			super(parent);
			theCompare = compare;
			theEquivalence = Equivalence.of((Class<T>) parent.getTargetType().getRawType(), theCompare, true);
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return theEquivalence;
		}

		@Override
		protected FilterMapResult<T, T> canAddTop(FilterMapResult<T, T> dest) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		protected void begin(Observable<?> until) {}

		@Override
		protected void elementCreated(DerivedCollectionElement<T> parent) {
			class SortedElementI
			class SortedElement implements DerivedCollectionElement<T>{
				@Override
				public boolean isPresent() {
					return parent.isPresent();
				}

				@Override
				public ElementId getElementId() {
					// TODO Auto-generated method stub
					return null;
				}

				@Override
				public T get() {
					// TODO Auto-generated method stub
					return null;
				}

				@Override
				public String isEnabled() {
					// TODO Auto-generated method stub
					return null;
				}

				@Override
				public String isAcceptable(T value) {
					// TODO Auto-generated method stub
					return null;
				}

				@Override
				public void set(T value) throws UnsupportedOperationException, IllegalArgumentException {
					// TODO Auto-generated method stub

				}

				@Override
				public String canRemove() {
					// TODO Auto-generated method stub
					return null;
				}

				@Override
				public void remove() throws UnsupportedOperationException {
					// TODO Auto-generated method stub

				}

				@Override
				public String canAdd(T value, boolean before) {
					// TODO Auto-generated method stub
					return null;
				}

				@Override
				public ElementId add(T value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
					// TODO Auto-generated method stub
					return null;
				}
			}
		}

		@Override
		public void createElement(ElementId id, E init, Object cause, Consumer<CollectionElementManager<E, ?, T>> onElement) {
			class SortedElement extends NonMappingCollectionElement<E, T> {
				SortedElement(CollectionElementManager<E, ?, T> parent) {
					super(SortedManager.this, parent, id, init, cause);
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
				protected FilterMapResult<T, E> filterAccept(FilterMapResult<T, E> value, boolean isReplacing) {
					if (get() != value.source && theCompare.compare(get(), value.source) != 0)
						return value.reject("Cannot modify a sorted value", true);
					return super.filterAccept(value, isReplacing);
				}

				@Override
				protected FilterMapResult<T, E> filterAdd(FilterMapResult<T, E> value, boolean before, boolean isAdding) {
					value.reject("Cannot add values to a sorted collection via spliteration", true);
					return value;
				}

				@Override
				protected FilterMapResult<T, E> filterInterceptSet(FilterMapResult<T, E> value) {
					if (get() != value.source && theCompare.compare(get(), value.source) != 0)
						return value.reject("Cannot modify a sorted value", true);
					return super.filterInterceptSet(value);
				}

				@Override
				protected void interceptSet(FilterMapResult<T, E> value) throws UnsupportedOperationException, IllegalArgumentException {
					if (get() != value.source && theCompare.compare(get(), value.source) != 0)
						throw new UnsupportedOperationException("Cannot modify a sorted value");
					super.interceptSet(value);
				}
			}
			getParent().addElement(id, init, cause, el -> onElement.accept(new SortedElement(el)));
		}
	}

	public static class FilteredCollectionManager2<E, T> implements ActiveCollectionManager<E, T, T> {
		private final ActiveCollectionManager<E, ?, T> theParent;
		private final Function<? super T, String> theFilter;
		private final boolean isStatic;

		public FilteredCollectionManager2(ActiveCollectionManager<E, ?, T> parent, Function<? super T, String> filter, boolean isStatic) {
			theParent = parent;
			theFilter = filter;
			this.isStatic = isStatic;
		}

		@Override
		public TypeToken<T> getTargetType() {
			return theParent.getTargetType();
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return theParent.equivalence();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theParent.lock(write, cause);
		}

		@Override
		public boolean isEachRepresented() {
			return false;
		}

		@Override
		public ElementFinder<T> getElementFinder() {
			return theParent.getElementFinder();
		}

		@Override
		public FilterMapResult<T, E> canAdd(FilterMapResult<T, E> toAdd) {
			toAdd.maybeReject(theFilter.apply(toAdd.source), true);
			if (toAdd.isAccepted())
				theParent.canAdd(toAdd);
			return toAdd;
		}

		@Override
		public DerivedCollectionElement<T> addElement(T value, boolean first) {
			String msg = theFilter.apply(value);
			if (msg != null)
				throw new IllegalArgumentException(msg);
			DerivedCollectionElement<T> parentEl = theParent.addElement(value, first);
			return parentEl == null ? null : new FilteredElement(parentEl);
		}

		@Override
		public void begin(Consumer<DerivedCollectionElement<T>> onElement, Observable<?> until) {
			// TODO Auto-generated method stub

		}

		class FilteredElement implements DerivedCollectionElement<T> {
			private final DerivedCollectionElement<T> theParentEl;

			FilteredElement(DerivedCollectionElement<T> parentEl) {
				theParentEl = parentEl;
			}
		}
	}

	public static class FilteredCollectionManager<E, T> extends NonMappingCollectionManager<E, T> {
		private final Function<? super T, String> theFilter;
		private final boolean isStaticFilter;

		protected FilteredCollectionManager(CollectionManager<E, ?, T> parent, Function<? super T, String> filter, boolean staticFilter) {
			super(parent);
			theFilter = filter;
			this.isStaticFilter = staticFilter;
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return getParent().equivalence();
		}

		@Override
		public boolean isFiltered() {
			return true;
		}

		@Override
		public FilterMapResult<E, T> map(FilterMapResult<E, T> source) {
			getParent().map(source);
			String error = theFilter.apply(source.result);
			if (error != null)
				source.reject(error, true);
			return source;
		}

		@Override
		public FilterMapResult<T, E> reverse(FilterMapResult<T, E> dest) {
			String error = theFilter.apply(dest.source);
			if (error != null)
				dest.reject(error, true);
			else
				getParent().reverse(dest);
			return dest;
		}

		@Override
		public void begin(Observable<?> until) {}

		@Override
		public void createElement(ElementId id, E init, Object cause, Consumer<CollectionElementManager<E, ?, T>> onElement) {
			getParent().addElement(id, init, cause, parentElement -> {
				if (isStaticFilter) {
					T value = parentElement.get();
					if (theFilter.apply(value) == null)
						onElement.accept(parentElement);
				} else {
					class FilteredElement extends NonMappingCollectionElement<E, T> {
						private boolean isPresent;

						protected FilteredElement(CollectionElementManager<E, ?, T> parent) {
							super(FilteredCollectionManager.this, parent, id, init, cause);
							isPresent = theFilter.apply(getParent().get()) == null;
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
							isPresent = theFilter.apply(source) == null;
							return true;
						}
					}
					onElement.accept(new FilteredElement(parentElement));
				}
			});
		}
	}

	protected static class IntersectionManager<E, T, X> extends NonMappingCollectionManager<E, T> {
		private class IntersectionElement {
			int rightCount;
			final Set<ElementId> leftElements;

			IntersectionElement() {
				leftElements = new HashSet<>();
			}
		}

		private final ObservableCollection<X> theFilter;
		private final Equivalence<? super T> theEquivalence; // Make this a field since we'll need it often
		/** Whether a values' presence in the right causes the value in the left to be present (true) or absent (false) in the result */
		private final boolean isExclude;
		private Map<T, IntersectionElement> theValues;
		// The following two fields are needed because the values may mutate
		private Map<ElementId, IntersectionElement> theLeftElementValues;
		private Map<ElementId, IntersectionElement> theRightElementValues;
		private Subscription theCountSub; // Need to hold a strong ref to this to prevent GC of listeners

		public IntersectionManager(CollectionManager<E, ?, T> parent, ObservableCollection<X> filter, boolean exclude) {
			super(parent);
			theFilter = filter;
			theEquivalence = parent.equivalence();
			isExclude = exclude;
			theValues = new HashMap<>();
			theLeftElementValues = new HashMap<>();
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return theEquivalence;
		}

		@Override
		public FilterMapResult<T, T> mapTop(FilterMapResult<T, T> source) {
			FilterMapResult<T, T> res = super.mapTop(source);
			if (res.isAccepted()) {
				IntersectionElement element = theValues.get(res.result);
				boolean rightPresent = element != null && element.rightCount != 0;
				if (rightPresent == isExclude)
					res.reject(StdMsg.ILLEGAL_ELEMENT, false);
			}
			return res;
		}

		@Override
		public FilterMapResult<T, T> reverseTop(FilterMapResult<T, T> dest) {
			IntersectionElement element = theValues.get(dest.source);
			boolean rightPresent = element != null && element.rightCount != 0;
			if (rightPresent == isExclude)
				return dest.reject(StdMsg.ILLEGAL_ELEMENT, false);
			return super.reverseTop(dest);
		}

		@Override
		protected void begin(Observable<?> until) {
			theCountSub = WeakConsumer.build().<ObservableCollectionEvent<? extends X>> withAction(evt -> {
				try (Transaction t = getParent().lock(false, evt)) {
					IntersectionElement element;
					switch (evt.getType()) {
					case add:
						if (!equivalence().isElement(evt.getNewValue()))
							return;
						element = theValues.computeIfAbsent((T) evt.getNewValue(), v -> new IntersectionElement());
						element.rightCount++;
						if (element.rightCount == 1)
							update(element.leftElements, evt);
						theRightElementValues.put(evt.getElementId(), element);
						break;
					case remove:
						element = theRightElementValues.remove(evt.getElementId());
						if (element == null)
							return;
						element.rightCount--;
						if (element.rightCount == 0)
							update(element.leftElements, evt);
						break;
					case set:
						boolean oldIsElement = equivalence().isElement(evt.getOldValue());
						boolean newIsElement = equivalence().isElement(evt.getNewValue());
						if ((oldIsElement == newIsElement)
							&& (!oldIsElement || equivalence().elementEquals((T) evt.getOldValue(), evt.getNewValue())))
							return; // No change
						if (oldIsElement) {
							element = theRightElementValues.remove(evt.getElementId());
							element.rightCount--;
							if (element.rightCount == 0)
								update(element.leftElements, evt);
						}
						if (newIsElement) {
							element = theValues.computeIfAbsent((T) evt.getNewValue(), v -> new IntersectionElement());
							element.rightCount++;
							if (element.rightCount == 1)
								update(element.leftElements, evt);
							theRightElementValues.put(evt.getElementId(), element);
						}
						break;
					}
				}
			}, action -> theFilter.subscribe(action, true).removeAll()).build();
		}

		private void update(Set<ElementId> leftElements, Object cause) {
			for (ElementId element : leftElements)
				getUpdateListener().accept(new CollectionUpdate(this, element, cause));
		}

		@Override
		public void createElement(ElementId id, E init, Object cause, Consumer<CollectionElementManager<E, ?, T>> onElement) {
			class IntersectedCollectionElement extends NonMappingCollectionElement<E, T> {
				IntersectedCollectionElement(CollectionElementManager<E, ?, T> parent) {
					super(IntersectionManager.this, parent, id, init, cause);
				}

				@Override
				public T get() {
					return getParent().get();
				}

				@Override
				public boolean isPresent() {
					if (!super.isPresent())
						return false;
					boolean rightIncluded = theLeftElementValues.get(getElementId()).rightCount > 0;
					return rightIncluded != isExclude;
				}

				@Override
				protected boolean refresh(T source, Object cause2) {
					return false; // If the present state doesn't change, don't fire an update
				}
			}
			getParent().addElement(id, init, cause, el -> onElement.accept(new IntersectedCollectionElement(el)));
		}
	}

	public static class MappedCollectionManager<E, I, T> extends AbstractCollectionManager<E, I, T> {
		private final Function<? super I, ? extends T> theMap;
		private final Function<? super T, ? extends I> theReverse;
		private final ElementSetter<? super I, ? super T> theElementReverse;
		private final boolean reEvalOnUpdate;
		private final boolean fireIfUnchanged;
		private final boolean isCached;

		protected MappedCollectionManager(CollectionManager<E, ?, I> parent, TypeToken<T> targetType, Function<? super I, ? extends T> map,
			Function<? super T, ? extends I> reverse, ElementSetter<? super I, ? super T> elementReverse, boolean reEvalOnUpdate,
			boolean fireIfUnchanged, boolean cached) {
			super(parent, targetType);
			theMap = map;
			theReverse = reverse;
			theElementReverse = elementReverse;
			this.reEvalOnUpdate = reEvalOnUpdate;
			this.fireIfUnchanged = fireIfUnchanged;
			isCached = cached;
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return Equivalence.DEFAULT;
		}

		@Override
		public boolean isReversible() {
			return theReverse != null && getParent().isReversible();
		}

		@Override
		public ObservableCollectionDataFlowImpl.ElementFinder<T> getElementFinder() {
			if (theReverse == null)
				return null;
			ObservableCollectionDataFlowImpl.ElementFinder<I> pef = getParent().getElementFinder();
			if (pef == null)
				return null;
			return v -> pef.findElement(reverseValue(v));
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
			return theMap.apply(source);
		}

		protected I reverseValue(T dest) {
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
				dest.reject(MutableCollectionElement.StdMsg.UNSUPPORTED_OPERATION, true);
			else
				dest.result = reverseValue(dest.source);
			return dest;
		}

		@Override
		public void createElement(ElementId id, E init, Object cause, Consumer<CollectionElementManager<E, ?, T>> onElement) {
			class MappedElement extends CollectionElementManager<E, I, T> {
				private I theSource;
				private T theValue;
				private boolean isInitial;

				MappedElement(CollectionElementManager<E, ?, I> parent) {
					super(MappedCollectionManager.this, parent, id, init, cause);
					theSource = getParent().get();
					if (isCached)
						theValue = mapValue(theSource);
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
					if (theElementReverse != null)
						return value.maybeReject(theElementReverse.setElement(getParent().get(), value.source, false, cause), true);
					return super.filterInterceptSet(value);
				}

				@Override
				protected void interceptSet(FilterMapResult<T, E> value) throws UnsupportedOperationException, IllegalArgumentException {
					if (theElementReverse != null) {
						value.maybeReject(theElementReverse.setElement(getParent().get(), value.source, true, cause), true)
						.throwIfError(IllegalArgumentException::new);
						return;
					}
					super.interceptSet(value);
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
			getParent().addElement(id, init, cause, el -> onElement.accept(new MappedElement(el)));
		}

		@Override
		protected void begin(Observable<?> until) {}
	}

	public static class CombinedCollectionManager<E, I, T> extends AbstractCollectionManager<E, I, T> {
		private static class ArgHolder<T> {
			Subscription actionSub;
			T value;
		}
		private final Map<ObservableValue<?>, ArgHolder<?>> theArgs;
		private final Function<? super CombinedValues<? extends I>, ? extends T> theCombination;
		private final Function<? super CombinedValues<? extends T>, ? extends I> theReverse;
		private final boolean isCached;
		/** Held as a strong reference to keep the observable value listeners from being GC'd */
		private Subscription theSubscription;

		protected CombinedCollectionManager(CollectionManager<E, ?, I> parent, TypeToken<T> targetType, Set<ObservableValue<?>> args,
			Function<? super CombinedValues<? extends I>, ? extends T> combination,
			Function<? super CombinedValues<? extends T>, ? extends I> reverse, boolean cached) {
			super(parent, targetType);
			theArgs = new HashMap<>();
			for (ObservableValue<?> arg : args)
				theArgs.put(arg, new ArgHolder<>());
			theCombination = combination;
			theReverse = reverse;
			isCached = cached;
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
		public ObservableCollectionDataFlowImpl.ElementFinder<T> getElementFinder() {
			if (theReverse == null)
				return null;
			ObservableCollectionDataFlowImpl.ElementFinder<I> pef = getParent().getElementFinder();
			if (pef == null)
				return null;
			return v -> pef.findElement(reverseValue(v));
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

		protected <V> V getArgValue(ObservableValue<V> arg) {
			ArgHolder<V> holder = (ArgHolder<V>) theArgs.get(arg);
			if (holder == null)
				throw new IllegalArgumentException("Unrecognized value: " + arg);
			if (isLightWeight())
				return arg.get();
			else
				return holder.value;
		}

		protected T combineValue(I source) {
			return theCombination.apply(new CombinedValues<I>() {
				@Override
				public I getElement() {
					return source;
				}

				@Override
				public <V> V get(ObservableValue<V> arg) {
					return getArgValue(arg);
				}
			});
		}

		protected I reverseValue(T dest) {
			return theReverse.apply(new CombinedValues<T>() {
				@Override
				public T getElement() {
					return dest;
				}

				@Override
				public <V> V get(ObservableValue<V> arg) {
					return getArgValue(arg);
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
				dest.reject(MutableCollectionElement.StdMsg.UNSUPPORTED_OPERATION, true);
				return dest;
			}
			dest.result = reverseValue(dest.source);
			return dest;
		}

		@Override
		public void createElement(ElementId id, E init, Object cause, Consumer<CollectionElementManager<E, ?, T>> onElement) {
			class CombinedCollectionElement extends CollectionElementManager<E, I, T> {
				private T theValue;

				CombinedCollectionElement(CollectionElementManager<E, ?, I> parent) {
					super(CombinedCollectionManager.this, parent, id, init, cause);
					if (isCached)
						theValue = combineValue(getParent().get());
				}

				@Override
				public T get() {
					if (isCached)
						return theValue;
					else
						return combineValue(getParent().get());
				}

				@Override
				protected boolean refresh(I source, Object cause) {
					if (isCached)
						theValue = combineValue(source);
					return true;
				}
			}
			getParent().addElement(id, init, cause, el -> onElement.accept(new CombinedCollectionElement(el)));
		}

		@Override
		public void begin(Observable<?> until) {
			if (!isLightWeight()) {
				WeakConsumer.WeakConsumerBuilder builder = WeakConsumer.build();
				for (Map.Entry<ObservableValue<?>, ArgHolder<?>> arg : theArgs.entrySet()) {
					ArgHolder<?> holder = arg.getValue();
					builder.withAction((ObservableValueEvent<?> evt) -> {
						if (evt.isInitial()) {
							// If this is an initial event, don't do any locking or updating
							((ArgHolder<Object>) holder).value = evt.getNewValue();
							return;
						}
						try (Transaction t = lock(true, null)) {
							((ArgHolder<Object>) holder).value = evt.getNewValue();
							getUpdateListener().accept(new CollectionUpdate(this, null, evt));
						}
					}, action -> arg.getKey().act(action));
				}
				builder.withUntil(until::act);
				theSubscription = builder.build();
			}
		}
	}

	public static class RefreshingCollectionManager<E, T> extends NonMappingCollectionManager<E, T> {
		private final Observable<?> theRefresh;
		private Subscription theActionSub;

		protected RefreshingCollectionManager(CollectionManager<E, ?, T> parent, Observable<?> refresh) {
			super(parent);
			theRefresh = refresh;
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return getParent().equivalence();
		}

		@Override
		public void begin(Observable<?> until) {
			if (!isLightWeight())
				theActionSub = WeakConsumer.build()//
				.withAction(v -> getUpdateListener().accept(new CollectionUpdate(this, null, v)), theRefresh::act)//
				.withUntil(until::act).build();
		}

		@Override
		public void createElement(ElementId id, E init, Object cause, Consumer<CollectionElementManager<E, ?, T>> onElement) {
			class RefreshingElement extends NonMappingCollectionElement<E, T> {
				public RefreshingElement(CollectionElementManager<E, ?, T> parent) {
					super(RefreshingCollectionManager.this, parent, id, init, cause);
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
			getParent().addElement(id, init, cause, el -> new RefreshingElement(el));
		}
	}

	public static class ElementRefreshingCollectionManager<E, T> extends NonMappingCollectionManager<E, T> {
		private static class RefreshHolder {
			final Observable<?> theRefresh;
			Subscription theSub;
			int theElementCount;

			RefreshHolder(Observable<?> refresh, Subscription sub) {
				theRefresh = refresh;
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
		public void createElement(ElementId id, E init, Object cause, Consumer<CollectionElementManager<E, ?, T>> onElement) {
			class ElementRefreshElement extends NonMappingCollectionElement<E, T> {
				private Observable<?> theRefreshObservable;

				protected ElementRefreshElement(CollectionElementManager<E, ?, T> parent) {
					super(ElementRefreshingCollectionManager.this, parent, id, init, cause);

					newRefreshObs(theRefresh.apply(get()));
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
					Consumer<Consumer<MutableCollectionElement<? extends E>>> sourceElement) {
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
					// No need for until the elements will be removed when the until fires
					Subscription sub = WeakConsumer.build()
						.withAction(v -> ElementRefreshingCollectionManager.this.update(refreshObs, v), refreshObs::act).build();
					return new RefreshHolder(refreshObs, sub);
				}
			}
			getParent().addElement(id, init, cause, el -> onElement.accept(new ElementRefreshElement(el)));
		}

		private void update(Observable<?> refreshObs, Object cause) {
			getUpdateListener().accept(new ElementRefreshUpdate(refreshObs, cause));
		}

		@Override
		public void begin(Observable<?> until) {}

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
			dest.maybeReject(checkAdd(dest.source), true);
			if (dest.isAccepted())
				dest.result = dest.source;
			return dest;
		}

		@Override
		protected void begin(Observable<?> until) {}

		@Override
		public void createElement(ElementId id, E init, Object cause, Consumer<CollectionElementManager<E, ?, T>> onElement) {
			class ModFilteredElement extends NonMappingCollectionElement<E, T> {
				ModFilteredElement(CollectionElementManager<E, ?, T> parent) {
					super(ModFilteredCollectionManager.this, parent, id, init, cause);
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
				protected String filterRemove(boolean isRemoving) {
					if (isRemoving)
						tryRemove(get());
					else {
						String msg = checkRemove(get());
						if (msg != null)
							return msg;
					}
					return super.filterRemove(isRemoving);
				}

				@Override
				protected FilterMapResult<T, E> filterAccept(FilterMapResult<T, E> value, boolean isReplacing) {
					if (isReplacing)
						tryReplace(get(), value.source);
					else {
						value.maybeReject(checkReplace(get(), value.source), true);
						if (!value.isAccepted())
							return value;
					}
					value.maybeReject(checkReplace(get(), value.source), true);
					if (!value.isAccepted())
						return value;
					return super.filterAccept(value, isReplacing);
				}

				@Override
				protected FilterMapResult<T, E> filterAdd(FilterMapResult<T, E> value, boolean before, boolean isAdding) {
					if (isAdding)
						tryAdd(value.source);
					else {
						value.maybeReject(checkAdd(value.source), true);
						if (!value.isAccepted())
							return value;
					}
					return super.filterAdd(value, before, isAdding);
				}

				@Override
				protected FilterMapResult<T, E> filterInterceptSet(FilterMapResult<T, E> value) {
					value.maybeReject(checkReplace(get(), value.source), true);
					if (!value.isAccepted())
						return value;
					return super.filterInterceptSet(value);
				}

				@Override
				protected void interceptSet(FilterMapResult<T, E> value) throws UnsupportedOperationException, IllegalArgumentException {
					value.maybeReject(checkReplace(get(), value.source), true).throwIfError(IllegalArgumentException::new);
					if (value.isAccepted())
						super.interceptSet(value);
				}
			}
			getParent().addElement(id, init, cause, el -> onElement.accept(new ModFilteredElement(el)));
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
					msg = MutableCollectionElement.StdMsg.BAD_TYPE;
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

	private static class FlattenedManager<E, I, T> extends AbstractCollectionManager<E, I, T> {
		private final Function<? super I, ? extends CollectionDataFlow<?, ?, ? extends T>> theMap;

		public FlattenedManager(CollectionManager<E, ?, I> parent, TypeToken<T> targetType,
			Function<? super I, ? extends CollectionDataFlow<?, ?, ? extends T>> map) {
			super(parent, targetType);
			theMap = map;
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return Equivalence.DEFAULT;
		}

		@Override
		public ObservableCollectionDataFlowImpl.ElementFinder<T> getElementFinder() {
			return null;
		}

		@Override
		public Comparator<? super T> comparator() {
			return null;
		}

		@Override
		protected FilterMapResult<I, T> mapTop(FilterMapResult<I, T> source) {
			// TODO Auto-generated method stub
		}

		@Override
		protected FilterMapResult<T, I> reverseTop(FilterMapResult<T, I> dest) {
			// TODO Auto-generated method stub
		}

		@Override
		protected void begin(Observable<?> until) {}

		@Override
		public void createElement(ElementId id, E init, Object cause, Consumer<CollectionElementManager<E, ?, T>> onElement) {
			getParent().addElement(id, init, cause, el -> {
				CollectionDataFlow<?, ?, ? extends T> flow = theMap.apply(el.get());
				// TODO
			});
		}
	}

}
