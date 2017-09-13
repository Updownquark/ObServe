package org.observe.collect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.observe.collect.ObservableCollectionDataFlowImpl.CollectionManager;
import org.observe.collect.ObservableCollectionDataFlowImpl.NonMappingCollectionElement;
import org.observe.collect.ObservableCollectionDataFlowImpl.NonMappingCollectionManager;
import org.observe.collect.ObservableCollectionImpl.ActiveDerivedCollection;
import org.observe.collect.ObservableCollectionImpl.PassiveDerivedCollection;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.BetterHashSet;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.tree.BetterTreeList;
import org.qommons.tree.BetterTreeMap;

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

		public <X> FilterMapResult<X, T> map(Function<? super E, ? extends X> map) {
			FilterMapResult<X, T> mapped = (FilterMapResult<X, T>) this;
			mapped.source = map.apply(source);
			return mapped;
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

		String canAdd(T toAdd);

		DerivedCollectionElement<T> addElement(T value, boolean first);

		void begin(ElementAccepter<T> onElement, Observable<?> until);
	}

	public static interface ElementAccepter<E> {
		void accept(DerivedCollectionElement<E> element, Object cause);
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

	private static <T> void update(CollectionElementListener<T> listener, T oldValue, T newValue, Object cause) {
		if (listener != null)
			listener.update(oldValue, newValue, cause);
	}

	private static <T> void removed(CollectionElementListener<T> listener, T value, Object cause) {
		if (listener != null)
			listener.removed(value, cause);
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
			return new PassiveDerivedCollection<>(getSource(), managePassive());
		}

		@Override
		public ObservableCollection<T> collect(Observable<?> until) {
			if (until == Observable.empty && isPassive())
				return new PassiveDerivedCollection<>(getSource(), managePassive());
			else
				return new ActiveDerivedCollection<>(getSource(), manageActive(), until);
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
			return new BaseCollectionPassThrough<>(getSource());
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
		public PassiveCollectionManager<E, ?, T> managePassive() {
			return new PassiveEquivalenceSwitchedManager(getParent().managePassive(), theEquivalence);
		}

		@Override
		public ActiveCollectionManager<E, ?, T> manageActive() {
			return new ActiveEquivalenceSwitchedManager(getParent().manageActive(), theEquivalence);
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
		public boolean isPassive() {
			if (isCached)
				return false;
			return getParent().isPassive();
		}

		@Override
		public PassiveCollectionManager<E, ?, T> managePassive() {
			return new PassiveMappedCollectionManager<>(getParent().managePassive(), getTargetType(), theMap, theReverse, theElementReverse,
				fireIfUnchanged);
		}

		@Override
		public ActiveCollectionManager<E, ?, T> manageActive() {
			return new MappedCollectionManager2<>(getParent().manageActive(), getTargetType(), theMap, theReverse, theElementReverse,
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
		public boolean isPassive() {
			return false; // TODO If cached is false, this could be passive
		}

		@Override
		public PassiveCollectionManager<E, ?, T> managePassive() {
			return null;
		}

		@Override
		public ActiveCollectionManager<E, ?, T> manageActive() {
			return new CombinedCollectionManager<>(getParent().manageActive(), getTargetType(), theArgs, theCombination, theReverse,
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
		public boolean isPassive() {
			return false;
		}

		@Override
		public PassiveCollectionManager<E, ?, T> managePassive() {
			return null;
		}

		@Override
		public ActiveCollectionManager<E, ?, T> manageActive() {
			return new RefreshingCollectionManager<>(getParent().manageActive(), theRefresh);
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
		public boolean isPassive() {
			return false;
		}

		@Override
		public PassiveCollectionManager<E, ?, T> managePassive() {
			return null;
		}

		@Override
		public ActiveCollectionManager<E, ?, T> manageActive() {
			return new ElementRefreshingCollectionManager<>(getParent().manageActive(), theElementRefresh);
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
		public boolean isPassive() {
			return getParent().isPassive();
		}

		@Override
		public PassiveCollectionManager<E, ?, T> managePassive() {
			return new PassiveModFilteredManager<>(getParent().managePassive(), theImmutableMessage, areUpdatesAllowed, theAddMessage,
				theRemoveMessage, theAddFilter, theRemoveFilter);
		}

		@Override
		public ActiveCollectionManager<E, ?, T> manageActive() {
			return new ActiveModFilteredManager<>(getParent().manageActive(), theImmutableMessage, areUpdatesAllowed, theAddMessage,
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
		public boolean isPassive() {
			return false;
		}

		@Override
		public PassiveCollectionManager<E, ?, T> managePassive() {
			return null;
		}

		@Override
		public ActiveCollectionManager<E, ?, T> manageActive() {
			return new FlattenedManager<>(getParent().manageActive(), getTargetType(), theMap);
		}
	}

	public static class BaseCollectionPassThrough<E> implements PassiveCollectionManager<E, E, E> {
		private final ObservableCollection<E> theSource;

		BaseCollectionPassThrough(ObservableCollection<E> source) {
			theSource = source;
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
		public E map(E source) {
			return source;
		}

		@Override
		public boolean isReversible() {
			return true;
		}

		@Override
		public FilterMapResult<E, E> reverse(FilterMapResult<E, E> dest) {
			dest.result = dest.source;
			return dest;
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
		public String canAdd(E toAdd) {
			return theSource.canAdd(toAdd);
		}

		@Override
		public DerivedCollectionElement<E> addElement(E value, boolean first) {
			CollectionElement<E> srcEl = theSource.addElement(value, first);
			return srcEl == null ? null : new BaseDerivedElement(theSource.mutableElement(srcEl.getElementId()));
		}

		@Override
		public void begin(ElementAccepter<E> onElement, Observable<?> until) {
			// Need to hold on to the subscription because it contains strong references that keep the listeners alive
			theWeakSubscription = WeakConsumer.build()//
				.<ObservableCollectionEvent<? extends E>> withAction(evt -> {
					switch (evt.getType()) {
					case add:
						BaseDerivedElement el = new BaseDerivedElement(theSource.mutableElement(evt.getElementId()));
						onElement.accept(el, evt);
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

	public static class SortedManager<E, T> implements ActiveCollectionManager<E, T, T> {
		private final ActiveCollectionManager<E, ?, T> theParent;
		private final Comparator<? super T> theCompare;

		public SortedManager(ActiveCollectionManager<E, ?, T> parent, Comparator<? super T> compare) {
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
		public String canAdd(T toAdd) {
			return theParent.canAdd(toAdd);
		}

		@Override
		public DerivedCollectionElement<T> addElement(T value, boolean first) {
			DerivedCollectionElement<T> parentEl = theParent.addElement(value, first);
			return parentEl == null ? null : new SortedElement(parentEl);
		}

		@Override
		public void begin(ElementAccepter<T> onElement, Observable<?> until) {
			theParent.begin((parentEl, cause) -> onElement.accept(new SortedElement(parentEl), cause), until);
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

	public static class FilteredCollectionManager<E, T> implements ActiveCollectionManager<E, T, T> {
		private final ActiveCollectionManager<E, ?, T> theParent;
		private final Function<? super T, String> theFilter;
		private final boolean isStatic;
		private ElementAccepter<T> theElementAccepter;

		public FilteredCollectionManager(ActiveCollectionManager<E, ?, T> parent, Function<? super T, String> filter, boolean isStatic) {
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
		public String canAdd(T toAdd) {
			String msg = theFilter.apply(toAdd);
			if (msg == null)
				msg = theParent.canAdd(toAdd);
			return msg;
		}

		@Override
		public DerivedCollectionElement<T> addElement(T value, boolean first) {
			String msg = theFilter.apply(value);
			if (msg != null)
				throw new IllegalArgumentException(msg);
			DerivedCollectionElement<T> parentEl = theParent.addElement(value, first);
			if (parentEl == null || theFilter.apply(parentEl.get()) != null)
				return null;
			return new FilteredElement(parentEl, true, true);
		}

		@Override
		public void begin(ElementAccepter<T> onElement, Observable<?> until) {
			theElementAccepter = onElement;
			theParent.begin((parentEl, cause) -> {
				String msg = theFilter.apply(parentEl.get());
				if (msg == null)
					onElement.accept(new FilteredElement(parentEl, false, true), cause);
				else if (isStatic) {
					return;
				} else
					new FilteredElement(parentEl, false, false);
			}, until);
		}

		class FilteredElement implements DerivedCollectionElement<T> {
			private final DerivedCollectionElement<T> theParentEl;
			private final boolean isSynthetic;
			private boolean included;
			private CollectionElementListener<T> theListener;

			FilteredElement(DerivedCollectionElement<T> parentEl, boolean synthetic, boolean included) {
				theParentEl = parentEl;
				isSynthetic = synthetic;
				this.included = included;
				if (!isSynthetic && !isStatic) {
					theParentEl.setListener(new CollectionElementListener<T>() {
						@Override
						public void update(T oldValue, T newValue, Object cause) {
							boolean oldIncluded = FilteredElement.this.included;
							boolean newIncluded = theFilter.apply(newValue) == null;
							FilteredElement.this.included = newIncluded;
							if (!oldIncluded && newIncluded) {
								theElementAccepter.accept(FilteredElement.this, cause);
							} else if (oldIncluded && !newIncluded && theListener != null) {
								theListener.removed(oldValue, cause);
								theListener = null;
							} else if (oldIncluded && newIncluded && theListener != null)
								theListener.update(oldValue, newValue, cause);
						}

						@Override
						public void removed(T value, Object cause) {
							if (FilteredElement.this.included && theListener != null) {
								theListener.removed(value, cause);
								theListener = null;
							}
						}
					});
				}
			}

			@Override
			public int compareTo(DerivedCollectionElement<T> o) {
				return theParentEl.compareTo(((FilteredElement) o).theParentEl);
			}

			@Override
			public void setListener(CollectionElementListener<T> listener) {
				if (isStatic)
					theParentEl.setListener(listener);
				else
					theListener = listener;
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
				String msg = theFilter.apply(value);
				if (msg == null)
					msg = theParentEl.isAcceptable(value);
				return msg;
			}

			@Override
			public void set(T value) throws UnsupportedOperationException, IllegalArgumentException {
				String msg = theFilter.apply(value);
				if (msg != null)
					throw new IllegalArgumentException(msg);
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
				String msg = theFilter.apply(value);
				if (msg == null)
					msg = theParentEl.canAdd(value, before);
				return msg;
			}

			@Override
			public DerivedCollectionElement<T> add(T value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
				String msg = theFilter.apply(value);
				if (msg != null)
					throw new IllegalArgumentException(msg);
				DerivedCollectionElement<T> parentEl = theParentEl.add(value, before);
				return parentEl == null ? null : new FilteredElement(parentEl, true, true);
			}
		}
	}

	public static class IntersectionManager<E, T, X> implements ActiveCollectionManager<E, T, T> {
		private class IntersectionElement {
			private final T value;
			private int rightCount;
			final List<IntersectedCollectionElement> leftElements;

			IntersectionElement(T value) {
				this.value = value;
				leftElements = new ArrayList<>();
			}

			boolean isPresent() {
				return (rightCount > 0) != isExclude;
			}

			void incrementRight(Object cause) {
				rightCount++;
				if (rightCount == 1)
					presentChanged(cause);
			}

			void decrementRight(Object cause) {
				rightCount--;
				if (rightCount == 0) {
					presentChanged(cause);
					if (leftElements.isEmpty())
						theValues.remove(value);
				}
			}

			void addLeft(IntersectedCollectionElement element) {
				leftElements.add(element);
			}

			void removeLeft(IntersectedCollectionElement element) {
				leftElements.remove(element);
				if (leftElements.isEmpty() && rightCount == 0)
					theValues.remove(value);
			}

			private void presentChanged(Object cause) {
				if (isPresent()) {
					for (IntersectedCollectionElement el : leftElements)
						theAccepter.accept(el, cause);
				} else {
					for (IntersectedCollectionElement el : leftElements)
						el.fireRemove(cause);
				}
			}
		}

		class IntersectedCollectionElement implements DerivedCollectionElement<T> {
			private final DerivedCollectionElement<T> theParentEl;
			private IntersectionElement intersection;
			private CollectionElementListener<T> theListener;

			IntersectedCollectionElement(DerivedCollectionElement<T> parentEl, IntersectionElement intersect, boolean synthetic) {
				theParentEl = parentEl;
				intersection = intersect;
				if (!synthetic)
					theParentEl.setListener(new CollectionElementListener<T>() {
						@Override
						public void update(T oldValue, T newValue, Object cause) {
							if (theEquivalence.elementEquals(intersection.value, newValue)) {
								ObservableCollectionDataFlowImpl.update(theListener, oldValue, newValue, cause);
							} else {
								boolean oldPresent = intersection.isPresent();
								intersection.removeLeft(IntersectedCollectionElement.this);
								intersection = theValues.computeIfAbsent(newValue, v -> new IntersectionElement(newValue));
								intersection.addLeft(IntersectedCollectionElement.this);
								boolean newPresent = intersection.isPresent();
								if (oldPresent && newPresent)
									ObservableCollectionDataFlowImpl.update(theListener, oldValue, newValue, cause);
								else if (oldPresent && !newPresent) {
									ObservableCollectionDataFlowImpl.removed(theListener, oldValue, cause);
									theListener = null;
								} else if (!oldPresent && newPresent)
									theAccepter.accept(IntersectedCollectionElement.this, cause);
								else { // Wasn't present before, still isn't present. Nothing to do.
								}
							}
						}

						@Override
						public void removed(T value, Object cause) {
							if (intersection.isPresent())
								ObservableCollectionDataFlowImpl.removed(theListener, value, cause);
							intersection.removeLeft(IntersectedCollectionElement.this);
							intersection = null;
						}
					});
			}

			void fireRemove(Object cause) {
				removed(theListener, theParentEl.get(), cause);
				theListener = null;
			}

			@Override
			public int compareTo(DerivedCollectionElement<T> o) {
				return theParentEl.compareTo(((IntersectedCollectionElement) o).theParentEl);
			}

			@Override
			public void setListener(CollectionElementListener<T> listener) {
				theListener = listener;
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
				String msg = theParentEl.isAcceptable(value);
				if (msg != null)
					return msg;
				IntersectionElement intersect = theValues.get(value);
				boolean filterHas = intersect == null ? false : intersect.rightCount > 0;
				if (filterHas == isExclude)
					return StdMsg.ILLEGAL_ELEMENT;
				return null;
			}

			@Override
			public void set(T value) throws UnsupportedOperationException, IllegalArgumentException {
				IntersectionElement intersect = theValues.get(value);
				boolean filterHas = intersect == null ? false : intersect.rightCount > 0;
				if (filterHas == isExclude)
					throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
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
				String msg = theParentEl.canAdd(value, before);
				if (msg != null)
					return msg;
				IntersectionElement intersect = theValues.get(value);
				boolean filterHas = intersect == null ? false : intersect.rightCount > 0;
				if (filterHas == isExclude)
					return StdMsg.ILLEGAL_ELEMENT;
				return null;
			}

			@Override
			public DerivedCollectionElement<T> add(T value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
				IntersectionElement intersect = theValues.get(value);
				boolean filterHas = intersect == null ? false : intersect.rightCount > 0;
				if (filterHas == isExclude)
					throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
				DerivedCollectionElement<T> parentEl = theParentEl.add(value, before);
				return parentEl == null ? null : new IntersectedCollectionElement(parentEl, null, true);
			}
		}

		private final ActiveCollectionManager<E, ?, T> theParent;
		private final ObservableCollection<X> theFilter;
		private final Equivalence<? super T> theEquivalence; // Make this a field since we'll need it often
		/** Whether a value's presence in the right causes the value in the left to be present (true) or absent (false) in the result */
		private final boolean isExclude;
		private Map<T, IntersectionElement> theValues;
		// The following two fields are needed because the values may mutate
		private Map<ElementId, IntersectionElement> theRightElementValues;
		private Subscription theCountSub; // Need to hold a strong ref to this to prevent GC of listeners

		private ElementAccepter<T> theAccepter;

		public IntersectionManager(ActiveCollectionManager<E, ?, T> parent, ObservableCollection<X> filter, boolean exclude) {
			theParent = parent;
			theFilter = filter;
			theEquivalence = parent.equivalence();
			isExclude = exclude;
			theValues = theEquivalence.createMap();
		}

		@Override
		public TypeToken<T> getTargetType() {
			return theParent.getTargetType();
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
		public String canAdd(T toAdd) {
			IntersectionElement intersect = theValues.get(toAdd);
			boolean filterHas = intersect == null ? false : intersect.rightCount > 0;
			if (filterHas == isExclude)
				return StdMsg.ILLEGAL_ELEMENT;
			return theParent.canAdd(toAdd);
		}

		@Override
		public DerivedCollectionElement<T> addElement(T value, boolean first) {
			IntersectionElement intersect = theValues.get(value);
			boolean filterHas = intersect == null ? false : intersect.rightCount > 0;
			if (filterHas == isExclude)
				throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
			DerivedCollectionElement<T> parentEl = theParent.addElement(value, first);
			return parentEl == null ? null : new IntersectedCollectionElement(parentEl, null, true);
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return theEquivalence;
		}

		@Override
		public void begin(ElementAccepter<T> onElement, Observable<?> until) {
			theCountSub = WeakConsumer.build().<ObservableCollectionEvent<? extends X>> withAction(evt -> {
				// We're not modifying, but we want to obtain an exclusive lock
				// to ensure that nothing above or below us is firing events at the same time.
				// TODO Maybe replace this with an independent lock on the base flow to avoid lock-writing?
				try (Transaction t = theParent.lock(true, evt)) {
					IntersectionElement element;
					switch (evt.getType()) {
					case add:
						if (!theEquivalence.isElement(evt.getNewValue()))
							return;
						element = theValues.computeIfAbsent((T) evt.getNewValue(), v -> new IntersectionElement(v));
						element.incrementRight(evt);
						theRightElementValues.put(evt.getElementId(), element);
						break;
					case remove:
						element = theRightElementValues.remove(evt.getElementId());
						if (element == null)
							return; // Must not have belonged to the flow's equivalence
						element.decrementRight(evt);
						break;
					case set:
						element = theRightElementValues.get(evt.getElementId());
						if (element != null && theEquivalence.elementEquals(element.value, evt.getNewValue()))
							return; // No change;
						boolean newIsElement = equivalence().isElement(evt.getNewValue());
						if (element != null) {
							theRightElementValues.remove(evt.getElementId());
							element.decrementRight(evt);
						}
						if (newIsElement) {
							element = theValues.computeIfAbsent((T) evt.getNewValue(), v -> new IntersectionElement(v));
							element.incrementRight(evt);
							theRightElementValues.put(evt.getElementId(), element);
						}
						break;
					}
				}
			}, action -> theFilter.subscribe(action, true).removeAll()).withUntil(until::act).build();
			theParent.begin((parentEl, cause) -> {
				IntersectionElement element = theValues.computeIfAbsent(parentEl.get(), v -> new IntersectionElement(v));
				IntersectedCollectionElement el = new IntersectedCollectionElement(parentEl, element, false);
				element.addLeft(el);
				if (element.isPresent())
					onElement.accept(el, cause);
			}, until);
		}
	}

	static class PassiveEquivalenceSwitchedManager<E, T> implements PassiveCollectionManager<E, T, T> {
		private final PassiveCollectionManager<E, ?, T> theParent;
		private final Equivalence<? super T> theEquivalence;

		PassiveEquivalenceSwitchedManager(PassiveCollectionManager<E, ?, T> parent, Equivalence<? super T> equivalence) {
			theParent = parent;
			theEquivalence = equivalence;
		}

		@Override
		public TypeToken<T> getTargetType() {
			return theParent.getTargetType();
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return theEquivalence;
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theParent.lock(write, cause);
		}

		@Override
		public T map(E source) {
			return theParent.map(source);
		}

		@Override
		public boolean isReversible() {
			return theParent.isReversible();
		}

		@Override
		public FilterMapResult<T, E> reverse(FilterMapResult<T, E> dest) {
			return theParent.reverse(dest);
		}
	}

	static class ActiveEquivalenceSwitchedManager<E, T> implements ActiveCollectionManager<E, T, T> {
		private final ActiveCollectionManager<E, ?, T> theParent;
		private final Equivalence<? super T> theEquivalence;

		ActiveEquivalenceSwitchedManager(ActiveCollectionManager<E, ?, T> parent, Equivalence<? super T> equivalence) {
			theParent = parent;
			theEquivalence = equivalence;
		}

		@Override
		public TypeToken<T> getTargetType() {
			return theParent.getTargetType();
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return theEquivalence;
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
			return null;
		}

		@Override
		public String canAdd(T toAdd) {
			return theParent.canAdd(toAdd);
		}

		@Override
		public DerivedCollectionElement<T> addElement(T value, boolean first) {
			return theParent.addElement(value, first);
		}

		@Override
		public void begin(ElementAccepter<T> onElement, Observable<?> until) {
			theParent.begin(onElement, until);
		}
	}

	public static class PassiveMappedCollectionManager<E, I, T> implements PassiveCollectionManager<E, I, T> {
		private final PassiveCollectionManager<E, ?, I> theParent;
		private final TypeToken<T> theTargetType;
		private final Function<? super I, ? extends T> theMap;
		private final Function<? super T, ? extends I> theReverse;
		private final ElementSetter<? super I, ? super T> theElementReverse;
		private final boolean fireIfUnchanged;

		public PassiveMappedCollectionManager(PassiveCollectionManager<E, ?, I> parent, TypeToken<T> targetType,
			Function<? super I, ? extends T> map, Function<? super T, ? extends I> reverse,
			ElementSetter<? super I, ? super T> elementReverse, boolean fireIfUnchanged) {
			theParent = parent;
			theTargetType = targetType;
			theMap = map;
			theReverse = reverse;
			theElementReverse = elementReverse;
			this.fireIfUnchanged = fireIfUnchanged;
		}

		@Override
		public TypeToken<T> getTargetType() {
			return theTargetType;
		}

		@Override
		public Equivalence<? super T> equivalence() {
			if (theParent.getTargetType().equals(theTargetType))
				return (Equivalence<? super T>) theParent.equivalence();
			else
				return Equivalence.DEFAULT;
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theParent.lock(write, cause);
		}

		@Override
		public T map(E source) {
			I srcMap = theParent.map(source);
			return theMap.apply(srcMap);
		}

		@Override
		public boolean isReversible() {
			return theReverse != null && theParent.isReversible();
		}

		@Override
		public FilterMapResult<T, E> reverse(FilterMapResult<T, E> dest) {
			if (theReverse == null)
				return dest.reject(StdMsg.UNSUPPORTED_OPERATION, true);
			FilterMapResult<I, E> intermediate = dest.map(theReverse);
			return (FilterMapResult<T, E>) theParent.reverse(intermediate);
		}
	}

	public static class MappedCollectionManager2<E, I, T> implements ActiveCollectionManager<E, I, T> {
		private final ActiveCollectionManager<E, ?, I> theParent;
		private final TypeToken<T> theTargetType;
		private final Function<? super I, ? extends T> theMap;
		private final Function<? super T, ? extends I> theReverse;
		private final ElementSetter<? super I, ? super T> theElementReverse;
		private final boolean reEvalOnUpdate;
		private final boolean fireIfUnchanged;
		private final boolean isCached;

		protected MappedCollectionManager2(ActiveCollectionManager<E, ?, I> parent, TypeToken<T> targetType,
			Function<? super I, ? extends T> map, Function<? super T, ? extends I> reverse,
			ElementSetter<? super I, ? super T> elementReverse, boolean reEvalOnUpdate, boolean fireIfUnchanged, boolean cached) {
			theParent = parent;
			theTargetType = targetType;
			theMap = map;
			theReverse = reverse;
			theElementReverse = elementReverse;
			this.reEvalOnUpdate = reEvalOnUpdate;
			this.fireIfUnchanged = fireIfUnchanged;
			isCached = cached;
		}

		@Override
		public TypeToken<T> getTargetType() {
			return theTargetType;
		}

		@Override
		public Equivalence<? super T> equivalence() {
			if (theParent.getTargetType().equals(theTargetType))
				return (Equivalence<? super T>) theParent.equivalence();
			else
				return Equivalence.DEFAULT;
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
			// TODO This might be possible if this manager is reversible. Revisit this after the element finder arch is thought out.
			// if (theReverse == null)
			// return null;
			// ElementFinder<I> pef = theParent.getElementFinder();
			// if (pef == null)
			// return null;
			// return v -> pef.findElement(reverseValue(v));
			return null;
		}

		@Override
		public String canAdd(T toAdd) {
			if (theReverse == null)
				return StdMsg.UNSUPPORTED_OPERATION;
			return theParent.canAdd(theReverse.apply(toAdd));
		}

		@Override
		public DerivedCollectionElement<T> addElement(T value, boolean first) {
			if (theReverse == null)
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			DerivedCollectionElement<I> parentEl = theParent.addElement(theReverse.apply(value), first);
			return parentEl == null ? null : new MappedElement(parentEl, true);
		}

		@Override
		public void begin(ElementAccepter<T> onElement, Observable<?> until) {
			theParent.begin((parentEl, cause) -> {
				onElement.accept(new MappedElement(parentEl, false), cause);
			}, until);
		}

		private class MappedElement implements DerivedCollectionElement<T> {
			private final DerivedCollectionElement<I> theParentEl;
			private CollectionElementListener<T> theListener;
			private I theSourceValue;
			private T theValue;

			MappedElement(DerivedCollectionElement<I> parentEl, boolean synthetic) {
				theParentEl = parentEl;
				if (!synthetic) {
					theParentEl.setListener(new CollectionElementListener<I>() {
						@Override
						public void update(I oldValue, I newValue, Object cause) {
							I srcVal;
							if (!fireIfUnchanged) {
								if (newValue == theSourceValue)
									return;
								srcVal = theSourceValue;
								theSourceValue = newValue;
							} else
								srcVal = oldValue;
							if (!reEvalOnUpdate && srcVal == newValue) {
								ObservableCollectionDataFlowImpl.update(theListener, theValue, theValue, cause);
								return;
							}
							T oldVal = isCached ? theValue : theMap.apply(oldValue);
							T newVal = theMap.apply(newValue);
							if (isCached)
								theValue = newVal;
							ObservableCollectionDataFlowImpl.update(theListener, oldVal, newVal, cause);
						}

						@Override
						public void removed(I value, Object cause) {
							T val = isCached ? theValue : theMap.apply(value);
							ObservableCollectionDataFlowImpl.removed(theListener, val, cause);
							theListener = null;
							theSourceValue = null;
							theValue = null;
						}
					});
					// Populate the initial values if these are needed
					if (isCached || !fireIfUnchanged) {
						I srcVal = parentEl.get();
						if (!fireIfUnchanged)
							theSourceValue = srcVal;
						if (isCached)
							theValue = theMap.apply(srcVal);
					}
				}
			}

			@Override
			public int compareTo(DerivedCollectionElement<T> o) {
				return theParentEl.compareTo(((MappedElement) o).theParentEl);
			}

			@Override
			public void setListener(CollectionElementListener<T> listener) {
				theListener = listener;
			}

			@Override
			public T get() {
				return isCached ? theValue : theMap.apply(theParentEl.get());
			}

			@Override
			public String isEnabled() {
				return theParentEl.isEnabled();
			}

			@Override
			public String isAcceptable(T value) {
				if (theReverse == null)
					return StdMsg.UNSUPPORTED_OPERATION;
				return theParentEl.isAcceptable(theReverse.apply(value));
			}

			@Override
			public void set(T value) throws UnsupportedOperationException, IllegalArgumentException {
				if (theReverse == null)
					throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
				theParentEl.set(theReverse.apply(value));
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
				if (theReverse == null)
					return StdMsg.UNSUPPORTED_OPERATION;
				return theParentEl.canAdd(theReverse.apply(value), before);
			}

			@Override
			public DerivedCollectionElement<T> add(T value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
				if (theReverse == null)
					throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
				DerivedCollectionElement<I> parentEl = theParentEl.add(theReverse.apply(value), before);
				return parentEl == null ? null : new MappedElement(parentEl, true);
			}
		}
	}

	static class CombinedCollectionManager<E, I, T> implements ActiveCollectionManager<E, I, T> {
		private static class ArgHolder<T> {
			Subscription actionSub;
			T value;
		}

		private final ActiveCollectionManager<E, ?, I> theParent;
		private final TypeToken<T> theTargetType;

		private final Map<ObservableValue<?>, ArgHolder<?>> theArgs;
		private final Function<? super CombinedValues<? extends I>, ? extends T> theCombination;
		private final Function<? super CombinedValues<? extends T>, ? extends I> theReverse;
		private final boolean isCached;
		/** Held as a strong reference to keep the observable value listeners from being GC'd */
		private Subscription theSubscription;

		// Need to keep track of these to update them when the combined values change
		private final List<CombinedElement> theElements;

		CombinedCollectionManager(ActiveCollectionManager<E, ?, I> parent, TypeToken<T> targetType, Set<ObservableValue<?>> args,
			Function<? super CombinedValues<? extends I>, ? extends T> combination,
			Function<? super CombinedValues<? extends T>, ? extends I> reverse, boolean cached) {
			theParent = parent;
			theTargetType = targetType;
			theArgs = new HashMap<>();
			for (ObservableValue<?> arg : args)
				theArgs.put(arg, new ArgHolder<>());
			theCombination = combination;
			theReverse = reverse;
			isCached = cached;

			theElements = new ArrayList<>();
		}

		@Override
		public TypeToken<T> getTargetType() {
			return theTargetType;
		}

		@Override
		public Equivalence<? super T> equivalence() {
			if (theTargetType.equals(theParent.getTargetType()))
				return (Equivalence<? super T>) theParent.equivalence();
			else
				return Equivalence.DEFAULT;
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
			// TODO This might be possible if this manager is reversible. Revisit this after the element finder arch is thought out.
			// if (theReverse == null)
			// return null;
			// ObservableCollectionDataFlowImpl.ElementFinder<I> pef = getParent().getElementFinder();
			// if (pef == null)
			// return null;
			// return v -> pef.findElement(reverseValue(v));
			return null;
		}

		@Override
		public String canAdd(T toAdd) {
			return theParent.canAdd(reverseValue(toAdd));
		}

		@Override
		public DerivedCollectionElement<T> addElement(T value, boolean first) {
			DerivedCollectionElement<I> parentEl = theParent.addElement(reverseValue(value), first);
			return parentEl == null ? null : new CombinedElement(parentEl, true);
		}

		@Override
		public void begin(ElementAccepter<T> onElement, Observable<?> until) {
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
						// The old values are not needed if we're caching each element value
						Object[] source = isCached ? null : new Object[1];
						CombinedValues<I> oldValues = isCached ? null : getCopy(source);
						((ArgHolder<Object>) holder).value = evt.getNewValue();
						// The order of update here may be different than the order in the derived collection
						// It's a lot of work to keep the elements in order (since the order may change),
						// so we'll just let order of addition be good enough
						for (CombinedElement el : theElements)
							el.updated(src -> {
								source[0] = src;
								return oldValues;
							}, evt);
					}
				}, action -> arg.getKey().act(action));
			}
			builder.withUntil(until::act);
			theSubscription = builder.build();
			theParent.begin((parentEl, cause) -> {
				CombinedElement el = new CombinedElement(parentEl, false);
				theElements.add(el);
				onElement.accept(el, cause);
			}, until);
		}

		private <V> V getArgValue(ObservableValue<V> arg) {
			ArgHolder<V> holder = (ArgHolder<V>) theArgs.get(arg);
			if (holder == null)
				throw new IllegalArgumentException("Unrecognized value: " + arg);
			return holder.value;
		}

		private T combineValue(I source) {
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

		private I reverseValue(T dest) {
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

		private CombinedValues<I> getCopy(Object[] source) {
			Map<ObservableValue<?>, Object> theValues = new HashMap<>();
			for (Map.Entry<ObservableValue<?>, ArgHolder<?>> holder : theArgs.entrySet())
				theValues.put(holder.getKey(), holder.getValue().value);
			return new CombinedValues<I>() {
				@Override
				public I getElement() {
					return (I) source[0];
				}

				@Override
				public <T> T get(ObservableValue<T> arg) {
					return (T) theValues.get(arg);
				}
			};
		}

		private class CombinedElement implements DerivedCollectionElement<T> {
			private final DerivedCollectionElement<I> theParentEl;
			private CollectionElementListener<T> theListener;
			private T theValue;

			CombinedElement(DerivedCollectionElement<I> parentEl, boolean synthetic) {
				theParentEl = parentEl;
				if (!synthetic) {
					theParentEl.setListener(new CollectionElementListener<I>() {
						@Override
						public void update(I oldValue, I newValue, Object cause) {
							T oldVal = isCached ? theValue : combineValue(oldValue);
							T newVal = combineValue(newValue);
							if (isCached)
								theValue = newVal;
							ObservableCollectionDataFlowImpl.update(theListener, oldVal, newVal, cause);
						}

						@Override
						public void removed(I value, Object cause) {
							T val = isCached ? theValue : combineValue(value);
							theElements.remove(this);
							ObservableCollectionDataFlowImpl.removed(theListener, val, cause);
							theListener = null;
							theValue = null;
						}
					});
					if (isCached)
						theValue = combineValue(theParentEl.get());
				}
			}

			void updated(Function<I, CombinedValues<I>> oldValues, Object cause) {
				T newVal = combineValue(theParentEl.get());
				T oldVal;
				if (isCached)
					oldVal = theValue;
				else
					oldVal = theCombination.apply(oldValues.apply(theParentEl.get()));
				ObservableCollectionDataFlowImpl.update(theListener, oldVal, newVal, cause);
			}

			@Override
			public int compareTo(DerivedCollectionElement<T> o) {
				return theParentEl.compareTo(((CombinedElement) o).theParentEl);
			}

			@Override
			public void setListener(CollectionElementListener<T> listener) {
				theListener = listener;
			}

			@Override
			public T get() {
				return isCached ? theValue : combineValue(theParentEl.get());
			}

			@Override
			public String isEnabled() {
				return theParentEl.isEnabled();
			}

			@Override
			public String isAcceptable(T value) {
				return theParentEl.isAcceptable(reverseValue(value));
			}

			@Override
			public void set(T value) throws UnsupportedOperationException, IllegalArgumentException {
				theParentEl.set(reverseValue(value));
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
				return theParentEl.canAdd(reverseValue(value), before);
			}

			@Override
			public DerivedCollectionElement<T> add(T value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
				DerivedCollectionElement<I> parentEl = theParentEl.add(reverseValue(value), before);
				return parentEl == null ? null : new CombinedElement(parentEl, true);
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

	// TODO TODO TODO!! This class doesn't have opportunity to do anything! Design flaw in passive derived collection architecture.
	static class PassiveModFilteredManager<E, T> implements PassiveCollectionManager<E, T, T> {
		private final PassiveCollectionManager<E, ?, T> theParent;
		private final String theImmutableMessage;
		private final boolean areUpdatesAllowed;
		private final String theAddMessage;
		private final String theRemoveMessage;
		private final Function<? super T, String> theAddFilter;
		private final Function<? super T, String> theRemoveFilter;

		PassiveModFilteredManager(PassiveCollectionManager<E, ?, T> parent, String immutableMessage, boolean allowUpdates,
			String addMessage, String removeMessage, Function<? super T, String> addFilter, Function<? super T, String> removeFilter) {
			theParent = parent;

			theImmutableMessage = immutableMessage;
			areUpdatesAllowed = allowUpdates;
			theAddMessage = addMessage;
			theRemoveMessage = removeMessage;
			theAddFilter = addFilter;
			theRemoveFilter = removeFilter;
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
		public T map(E source) {
			return theParent.map(source);
		}

		@Override
		public boolean isReversible() {
			return theParent.isReversible();
		}

		@Override
		public FilterMapResult<T, E> reverse(FilterMapResult<T, E> dest) {
			return theParent.reverse(dest);
		}
	}

	static class ActiveModFilteredManager<E, T> implements ActiveCollectionManager<E, T, T> {
		private final ActiveCollectionManager<E, ?, T> theParent;
		private final String theImmutableMessage;
		private final boolean areUpdatesAllowed;
		private final String theAddMessage;
		private final String theRemoveMessage;
		private final Function<? super T, String> theAddFilter;
		private final Function<? super T, String> theRemoveFilter;

		ActiveModFilteredManager(ActiveCollectionManager<E, ?, T> parent, String immutableMessage, boolean allowUpdates,
			String addMessage, String removeMessage, Function<? super T, String> addFilter, Function<? super T, String> removeFilter) {
			theParent = parent;

			theImmutableMessage = immutableMessage;
			areUpdatesAllowed = allowUpdates;
			theAddMessage = addMessage;
			theRemoveMessage = removeMessage;
			theAddFilter = addFilter;
			theRemoveFilter = removeFilter;
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
			return theParent.getElementFinder();
		}

		@Override
		public String canAdd(T toAdd) {
			String msg = null;
			if (theAddFilter != null)
				msg = theAddFilter.apply(toAdd);
			if (msg == null && theAddMessage != null)
				msg = theAddMessage;
			if (msg == null && theImmutableMessage != null)
				msg = theImmutableMessage;
			if (msg == null)
				msg = theParent.canAdd(toAdd);
			return msg;
		}

		@Override
		public DerivedCollectionElement<T> addElement(T value, boolean first) {
			String msg = null;
			if (theAddFilter != null)
				msg = theAddFilter.apply(value);
			if (msg != null)
				throw new IllegalArgumentException(msg);
			if (msg == null && theAddMessage != null)
				msg = theAddMessage;
			if (msg == null && theImmutableMessage != null)
				msg = theImmutableMessage;
			if (msg != null)
				throw new UnsupportedOperationException(msg);
			DerivedCollectionElement<T> parentEl = theParent.addElement(value, first);
			return parentEl == null ? null : new ModFilteredElement(parentEl);
		}

		@Override
		public void begin(ElementAccepter<T> onElement, Observable<?> until) {
			theParent.begin((parentEl, cause) -> onElement.accept(new ModFilteredElement(parentEl), cause), until);
		}

		private class ModFilteredElement implements DerivedCollectionElement<T> {
			private final DerivedCollectionElement<T> theParentEl;

			ModFilteredElement(DerivedCollectionElement<T> parentEl) {
				theParentEl = parentEl;
			}

			@Override
			public int compareTo(DerivedCollectionElement<T> o) {
				return theParentEl.compareTo(((ModFilteredElement) o).theParentEl);
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
				if (theImmutableMessage != null)
					return theImmutableMessage;
				return theParentEl.isEnabled();
			}

			@Override
			public String isAcceptable(T value) {
				String msg = null;
				if (theAddFilter != null)
					msg = theAddFilter.apply(value);
				if (msg == null) {
					if (value != get()) {
						msg = theAddMessage;
						if (msg == null)
							msg = theImmutableMessage;
					} else if (!areUpdatesAllowed)
						msg = theImmutableMessage;
				}
				if (msg == null)
					msg = theParentEl.isAcceptable(value);
				return msg;
			}

			@Override
			public void set(T value) throws UnsupportedOperationException, IllegalArgumentException {
				String msg = null;
				if (theAddFilter != null)
					msg = theAddFilter.apply(value);
				if (msg != null)
					throw new IllegalArgumentException(msg);
				if (msg == null) {
					if (value != get()) {
						msg = theAddMessage;
						if (msg == null)
							msg = theImmutableMessage;
					} else if (!areUpdatesAllowed)
						msg = theImmutableMessage;
				}
				if (msg != null)
					throw new UnsupportedOperationException(msg);
				theParentEl.set(value);
			}

			@Override
			public String canRemove() {
				String msg = null;
				if (theRemoveFilter != null)
					msg = theRemoveFilter.apply(get());
				if (msg == null)
					msg = theRemoveMessage;
				if (msg == null)
					msg = theImmutableMessage;
				return msg;
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				String msg = null;
				if (theRemoveFilter != null)
					msg = theRemoveFilter.apply(get());
				if (msg == null)
					msg = theRemoveMessage;
				if (msg == null)
					msg = theImmutableMessage;
				if (msg != null)
					throw new UnsupportedOperationException(msg);
				theParentEl.remove();
			}

			@Override
			public String canAdd(T value, boolean before) {
				String msg = null;
				if (theAddFilter != null)
					msg = theAddFilter.apply(value);
				if (msg == null && theAddMessage != null)
					msg = theAddMessage;
				if (msg == null && theImmutableMessage != null)
					msg = theImmutableMessage;
				if (msg == null)
					msg = theParentEl.canAdd(value, before);
				return msg;
			}

			@Override
			public DerivedCollectionElement<T> add(T value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
				String msg = null;
				if (theAddFilter != null)
					msg = theAddFilter.apply(value);
				if (msg != null)
					throw new IllegalArgumentException(msg);
				if (msg == null && theAddMessage != null)
					msg = theAddMessage;
				if (msg == null && theImmutableMessage != null)
					msg = theImmutableMessage;
				if (msg != null)
					throw new UnsupportedOperationException(msg);
				DerivedCollectionElement<T> parentEl = theParentEl.add(value, before);
				return parentEl == null ? null : new ModFilteredElement(parentEl);
			}
		}
	}

	private static class FlattenedManager<E, I, T> implements ActiveCollectionManager<E, I, T> {
		private final ActiveCollectionManager<E, ?, I> theParent;
		private final TypeToken<T> theTargetType;
		private final Function<? super I, ? extends CollectionDataFlow<?, ?, ? extends T>> theMap;

		private ElementAccepter<T> theAccepter;
		private final BetterList<FlattenedHolder> theOuterElements;

		public FlattenedManager(ActiveCollectionManager<E, ?, I> parent, TypeToken<T> targetType,
			Function<? super I, ? extends CollectionDataFlow<?, ?, ? extends T>> map) {
			theParent = parent;
			theTargetType = targetType;
			theMap = map;

			theOuterElements = new BetterTreeList<>(false);
		}

		@Override
		public TypeToken<T> getTargetType() {
			return theTargetType;
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return Equivalence.DEFAULT;
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			Transaction outerLock = theParent.lock(write, cause);
			List<Transaction> innerLocks = new LinkedList<>();
			for (FlattenedHolder holder : theOuterElements)
				innerLocks.add(Transactable.lock(holder.flow, write, cause));
			return () -> {
				for (Transaction innerLock : innerLocks)
					innerLock.close();
				outerLock.close();
			};
		}

		@Override
		public boolean isEachRepresented() {
			return false;
		}

		@Override
		public ObservableCollectionDataFlowImpl.ElementFinder<T> getElementFinder() {
			return null;
		}

		@Override
		public String canAdd(T toAdd) {
			String msg = null;
			for (FlattenedHolder holder : theOuterElements) {
				if (holder.flow.getTargetType().getRawType().isInstance(toAdd) && holder.flow.equivalence().isElement(toAdd))
					msg = ((ActiveCollectionManager<?, ?, T>) holder.flow).canAdd(toAdd);
				else
					msg = StdMsg.ILLEGAL_ELEMENT;
				if (msg == null)
					return null;
			}
			if (msg == null)
				msg = StdMsg.UNSUPPORTED_OPERATION;
			return msg;
		}

		@Override
		public DerivedCollectionElement<T> addElement(T value, boolean first) {
			String msg = null;
			boolean tried = false;
			for (FlattenedHolder holder : (first ? theOuterElements : theOuterElements.reverse())) {
				String msg_i;
				if (holder.flow.getTargetType().getRawType().isInstance(value) && holder.flow.equivalence().isElement(value))
					msg_i = ((ActiveCollectionManager<?, ?, T>) holder.flow).canAdd(value);
				else
					msg_i = StdMsg.ILLEGAL_ELEMENT;
				if (msg == null) {
					tried = true;
					DerivedCollectionElement<T> el = ((ActiveCollectionManager<?, ?, T>) holder.flow).addElement(value, first);
					if (el != null)
						return el;
				} else
					msg = msg_i;
			}
			if (tried)
				return null;
			if (msg == null)
				msg = StdMsg.UNSUPPORTED_OPERATION;
			throw new UnsupportedOperationException(msg);
		}

		@Override
		public void begin(ElementAccepter<T> onElement, Observable<?> until) {
			theAccepter = onElement;
			theParent.begin((parentEl, cause) -> {
				theOuterElements.add(new FlattenedHolder(parentEl, until));
			}, until);
		}

		private class FlattenedHolder {
			private final DerivedCollectionElement<I> theParentEl;
			private final List<FlattenedElement> theElements;
			private Subscription theWeakSubscription;
			ActiveCollectionManager<?, ?, ? extends T> flow;

			FlattenedHolder(DerivedCollectionElement<I> parentEl, Observable<?> until) {
				theParentEl = parentEl;
				flow = theMap.apply(theParentEl.get()).manageActive();
				theElements = new LinkedList<>();
				// Need to hold on to the subscription because it contains strong references that keep the listeners alive
				// TODO
				// theWeakSubscription = WeakConsumer.build()//
				// .<ObservableCollectionEvent<? extends E>> withAction(el -> {
				// }, action -> flow.subscribe(action, true).removeAll())//
				// .withUntil(until::act).build();
			}
		}

		private class FlattenedElement {}
	}
}
