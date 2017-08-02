package org.observe.collect;

import java.util.Comparator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.Subscription;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollection.ElementSetter;
import org.observe.collect.ObservableCollection.UniqueSortedDataFlow;
import org.observe.collect.ObservableCollection.UniqueSortedMappedCollectionBuilder;
import org.observe.collect.ObservableCollection.UniqueSortedModFilterBuilder;
import org.observe.collect.ObservableCollectionDataFlowImpl.AbstractDataFlow;
import org.observe.collect.ObservableCollectionDataFlowImpl.CollectionManager;
import org.observe.collect.ObservableCollectionDataFlowImpl.FilterMapResult;
import org.observe.collect.ObservableCollectionDataFlowImpl.SortedManager;
import org.observe.collect.ObservableSetImpl.UniqueBaseFlow;
import org.qommons.Transaction;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.BetterSortedSet.SortedSearchFilter;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.MutableCollectionElement.StdMsg;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

public class ObservableSortedSetImpl {
	private ObservableSortedSetImpl() {}

	public static class RelativeFinder<E> extends ObservableCollectionImpl.AbstractObservableElementFinder<E> {
		private final Comparable<? super E> theSearch;
		private final SortedSearchFilter theFilter;

		public RelativeFinder(ObservableSortedSet<E> set, Comparable<? super E> search, SortedSearchFilter filter) {
			super(set, (el1, el2) -> {
				int comp1 = search.compareTo(el1.get());
				int comp2 = search.compareTo(el2.get());
				if (comp1 == 0) {
					if (comp2 == 0)
						return 0;
					else
						return -1;
				} else if (comp2 == 0)
					return 1;
				// Neither are a perfect match.
				// From here on, it's safe to assume filter.less.value is non-null because otherwise one or the other element
				// would not have passed the test method.
				else if (comp1 < 0) {
					if (comp2 < 0)
						return -el1.getElementId().compareTo(el2.getElementId());// Both less, so take the greater of the two
					else
						return filter.less.value ? -1 : 1;
				} else {
					if (comp2 < 0)
						return filter.less.value ? 1 : -1;
					else
						return el1.getElementId().compareTo(el2.getElementId());// Both greater, so take the lesser of the two
				}
			});
			theSearch = search;
			theFilter = filter;
		}

		@Override
		protected ObservableSortedSet<E> getCollection() {
			return (ObservableSortedSet<E>) super.getCollection();
		}

		@Override
		protected boolean find(Consumer<? super CollectionElement<? extends E>> onElement) {
			CollectionElement<E> el = getCollection().search(theSearch, theFilter);
			if (el == null)
				return false;
			onElement.accept(el);
			return true;
		}

		@Override
		protected boolean test(E value) {
			if (theFilter == SortedSearchFilter.OnlyMatch && theSearch.compareTo(value) != 0)
				return false;
			return true;
		}
	}

	/**
	 * Implements {@link ObservableSortedSet#subSet(Object, boolean, Object, boolean)}
	 *
	 * @param <E> The type of elements in the set
	 */
	public static class ObservableSubSet<E> extends BetterSortedSet.BetterSubSet<E> implements ObservableSortedSet<E> {
		public ObservableSubSet(ObservableSortedSet<E> set, Comparable<? super E> from, Comparable<? super E> to) {
			super(set, from, to);
		}

		@Override
		public ObservableSortedSet<E> getWrapped() {
			return (ObservableSortedSet<E>) super.getWrapped();
		}

		@Override
		public TypeToken<E> getType() {
			return getWrapped().getType();
		}

		@Override
		public boolean isLockSupported() {
			return getWrapped().isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return getWrapped().lock(write, cause);
		}

		@Override
		public Equivalence<? super E> equivalence() {
			return getWrapped().equivalence();
		}

		@Override
		public E[] toArray() {
			return ObservableSortedSet.super.toArray();
		}

		@Override
		public ObservableSortedSet<E> subSet(Comparable<? super E> from, Comparable<? super E> to) {
			return new ObservableSubSet<>(getWrapped(), boundSearch(from), boundSearch(to));
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
			return getWrapped().onChange(new Consumer<ObservableCollectionEvent<? extends E>>() {
				private int startIndex;

				@Override
				public void accept(ObservableCollectionEvent<? extends E> evt) {
					int inRange = isInRange(evt.getNewValue());
					int oldInRange = evt.getType() == CollectionChangeType.set ? isInRange(evt.getOldValue()) : 0;
					if (inRange < 0) {
						switch (evt.getType()) {
						case add:
							startIndex++;
							break;
						case remove:
							startIndex--;
							break;
						case set:
							if (oldInRange > 0)
								startIndex++;
							else if (oldInRange == 0)
								fire(evt, CollectionChangeType.remove, evt.getOldValue(), evt.getOldValue());
						}
					} else if (inRange > 0) {
						switch (evt.getType()) {
						case set:
							if (oldInRange < 0)
								startIndex--;
							else if (oldInRange == 0)
								fire(evt, CollectionChangeType.remove, evt.getOldValue(), evt.getOldValue());
							break;
						default:
						}
					} else {
						switch (evt.getType()) {
						case add:
							fire(evt, evt.getType(), null, evt.getNewValue());
							break;
						case remove:
							fire(evt, evt.getType(), evt.getOldValue(), evt.getNewValue());
							break;
						case set:
							if (oldInRange < 0) {
								startIndex--;
								fire(evt, CollectionChangeType.add, null, evt.getNewValue());
							} else if (oldInRange == 0)
								fire(evt, CollectionChangeType.set, evt.getOldValue(), evt.getNewValue());
							else
								fire(evt, CollectionChangeType.add, null, evt.getNewValue());
						}
					}
				}

				void fire(ObservableCollectionEvent<? extends E> evt, CollectionChangeType type, E oldValue, E newValue) {
					observer.accept(new ObservableCollectionEvent<>(evt.getElementId(), evt.getIndex() - startIndex, evt.getType(),
						evt.getOldValue(), evt.getNewValue(), evt));
				}
			});
		}

		@Override
		public String toString() {
			return ObservableSet.toString(this);
		}
	}

	/**
	 * Implements {@link ObservableSortedSet#reverse()}
	 *
	 * @param <E> The type of elements in the collection
	 */
	public static class ReversedSortedSet<E> extends ObservableSetImpl.ReversedSet<E> implements ObservableSortedSet<E> {
		public ReversedSortedSet(ObservableSortedSet<E> wrap) {
			super(wrap);
		}

		@Override
		protected ObservableSortedSet<E> getWrapped() {
			return (ObservableSortedSet<E>) super.getWrapped();
		}

		@Override
		public Comparator<? super E> comparator() {
			return getWrapped().comparator().reversed();
		}

		@Override
		public int indexFor(Comparable<? super E> search) {
			try (Transaction t = lock(false, null)) {
				int index = getWrapped().indexFor(search);
				if (index >= 0)
					return size() - index - 1;
				else {
					index = -index - 1;
					index = size() - index;
					return -(index + 1);
				}
			}
		}

		@Override
		public CollectionElement<E> search(Comparable<? super E> search, SortedSearchFilter filter) {
			return getWrapped().search(v -> -search.compareTo(v), filter.opposite());
		}

		@Override
		public CollectionElement<E> addIfEmpty(E value) throws IllegalStateException {
			return getWrapped().addIfEmpty(value).reverse();
		}

		@Override
		public ObservableSortedSet<E> reverse() {
			return (ObservableSortedSet<E>) super.reverse();
		}
	}

	public static class UniqueSortedDataFlowWrapper<E, T> extends ObservableSetImpl.UniqueDataFlowWrapper<E, T>
	implements UniqueSortedDataFlow<E, T, T> {
		private final Comparator<? super T> theCompare;

		protected UniqueSortedDataFlowWrapper(ObservableCollection<E> source, CollectionDataFlow<E, ?, T> parent,
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
			return new UniqueSortedDataFlowWrapper<>(getSource(), getParent().filter(filter), theCompare);
		}

		@Override
		public UniqueSortedDataFlow<E, T, T> filterStatic(Function<? super T, String> filter) {
			return new UniqueSortedDataFlowWrapper<>(getSource(), getParent().filterStatic(filter), theCompare);
		}

		@Override
		public <X> UniqueSortedDataFlow<E, T, T> whereContained(ObservableCollection<X> other, boolean include) {
			return new UniqueSortedDataFlowWrapper<>(getSource(), getParent().whereContained(other, include), theCompare);
		}

		@Override
		public <X> UniqueSortedMappedCollectionBuilder<E, T, X> mapEquivalent(TypeToken<X> target) {
			return new UniqueSortedMappedCollectionBuilder<>(getSource(), this, target);
		}

		@Override
		public UniqueSortedDataFlow<E, T, T> refresh(Observable<?> refresh) {
			return new UniqueSortedDataFlowWrapper<>(getSource(), getParent().refresh(refresh), theCompare);
		}

		@Override
		public UniqueSortedDataFlow<E, T, T> refreshEach(Function<? super T, ? extends Observable<?>> refresh) {
			return new UniqueSortedDataFlowWrapper<>(getSource(), getParent().refreshEach(refresh), theCompare);
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
		public ObservableSortedSet<T> collectLW() {
			if (!isLightWeight())
				throw new IllegalStateException("This data flow is not light-weight");
			return new DerivedLWSortedSet<>((ObservableSortedSet<E>) getSource(), manageCollection(), theCompare);
		}

		@Override
		public ObservableSortedSet<T> collect(Observable<?> until) {
			return new DerivedSortedSet<>(getSource(), manageCollection(), theCompare, until);
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
			return new ObservableSetImpl.UniqueManager<>(new SortedManager<>(getParent().manageCollection(), comparator()),
				isAlwaysUsingFirst);
		}
	}

	public static class UniqueSortedMapOp<E, I, T> extends ObservableSetImpl.UniqueMapOp<E, I, T> implements UniqueSortedDataFlow<E, I, T> {
		private final Comparator<? super T> theCompare;

		protected UniqueSortedMapOp(ObservableCollection<E> source, AbstractDataFlow<E, ?, I> parent, TypeToken<T> target,
			Function<? super I, ? extends T> map, Function<? super T, ? extends I> reverse,
			ElementSetter<? super I, ? super T> elementReverse, boolean reEvalOnUpdate, boolean fireIfUnchanged, boolean isCached,
			Comparator<? super T> compare) {
			super(source, parent, target, map, reverse, elementReverse, reEvalOnUpdate, fireIfUnchanged, isCached);
			theCompare = compare;
		}

		@Override
		public Comparator<? super T> comparator() {
			return theCompare;
		}

		@Override
		public UniqueSortedDataFlow<E, T, T> filter(Function<? super T, String> filter) {
			return new UniqueSortedDataFlowWrapper<>(getSource(), super.filter(filter), comparator());
		}

		@Override
		public UniqueSortedDataFlow<E, T, T> filterStatic(Function<? super T, String> filter) {
			return new UniqueSortedDataFlowWrapper<>(getSource(), super.filterStatic(filter), comparator());
		}

		@Override
		public <X> UniqueSortedDataFlow<E, T, T> whereContained(ObservableCollection<X> other, boolean include) {
			return new UniqueSortedDataFlowWrapper<>(getSource(), super.whereContained(other, include), theCompare);
		}

		@Override
		public <X> UniqueSortedMappedCollectionBuilder<E, T, X> mapEquivalent(TypeToken<X> target) {
			return new UniqueSortedMappedCollectionBuilder<>(getSource(), this, target);
		}

		@Override
		public UniqueSortedDataFlow<E, T, T> refresh(Observable<?> refresh) {
			return new UniqueSortedDataFlowWrapper<>(getSource(), super.refresh(refresh), comparator());
		}

		@Override
		public UniqueSortedDataFlow<E, T, T> refreshEach(Function<? super T, ? extends Observable<?>> refresh) {
			return new UniqueSortedDataFlowWrapper<>(getSource(), super.refreshEach(refresh), comparator());
		}

		@Override
		public UniqueSortedModFilterBuilder<E, T> filterModification() {
			return new UniqueSortedModFilterBuilder<>(getSource(), this);
		}

		@Override
		public ObservableSortedSet<T> collectLW() {
			if (!isLightWeight())
				throw new IllegalStateException("This data flow is not light-weight");
			return new DerivedLWSortedSet<>((ObservableSortedSet<E>) getSource(), manageCollection(), comparator());
		}

		@Override
		public ObservableSortedSet<T> collect(Observable<?> until) {
			return new DerivedSortedSet<>(getSource(), manageCollection(), comparator(), until);
		}
	}

	public static class UniqueSortedModFilteredOp<E, T> extends ObservableSetImpl.UniqueModFilteredOp<E, T>
	implements UniqueSortedDataFlow<E, T, T> {
		public UniqueSortedModFilteredOp(ObservableCollection<E> source, AbstractDataFlow<E, ?, T> parent, String immutableMsg,
			boolean allowUpdates, String addMsg, String removeMsg, Function<? super T, String> addMsgFn,
			Function<? super T, String> removeMsgFn) {
			super(source, parent, immutableMsg, allowUpdates, addMsg, removeMsg, addMsgFn, removeMsgFn);
		}

		@Override
		public Comparator<? super T> comparator() {
			return ((UniqueSortedDataFlow<E, ?, T>) getParent()).comparator();
		}

		@Override
		public UniqueSortedDataFlow<E, T, T> filter(Function<? super T, String> filter) {
			return new UniqueSortedDataFlowWrapper<>(getSource(), getParent().filter(filter), comparator());
		}

		@Override
		public UniqueSortedDataFlow<E, T, T> filterStatic(Function<? super T, String> filter) {
			return new UniqueSortedDataFlowWrapper<>(getSource(), getParent().filterStatic(filter), comparator());
		}

		@Override
		public <X> UniqueSortedDataFlow<E, T, T> whereContained(ObservableCollection<X> other, boolean include) {
			return new UniqueSortedDataFlowWrapper<>(getSource(), super.whereContained(other, include), comparator());
		}

		@Override
		public <X> UniqueSortedMappedCollectionBuilder<E, T, X> mapEquivalent(TypeToken<X> target) {
			return new UniqueSortedMappedCollectionBuilder<>(getSource(), this, target);
		}

		@Override
		public UniqueSortedDataFlow<E, T, T> refresh(Observable<?> refresh) {
			return new UniqueSortedDataFlowWrapper<>(getSource(), getParent().refresh(refresh), comparator());
		}

		@Override
		public UniqueSortedDataFlow<E, T, T> refreshEach(Function<? super T, ? extends Observable<?>> refresh) {
			return new UniqueSortedDataFlowWrapper<>(getSource(), getParent().refreshEach(refresh), comparator());
		}

		@Override
		public UniqueSortedModFilterBuilder<E, T> filterModification() {
			return new UniqueSortedModFilterBuilder<>(getSource(), this);
		}

		@Override
		public ObservableSortedSet<T> collectLW() {
			if (!isLightWeight())
				throw new IllegalStateException("This data flow is not light-weight");
			return new DerivedLWSortedSet<>((ObservableSortedSet<E>) getSource(), manageCollection(), comparator());
		}

		@Override
		public ObservableSortedSet<T> collect(Observable<?> until) {
			return new DerivedSortedSet<>(getSource(), manageCollection(), comparator(), until);
		}
	}

	public static class UniqueSortedBaseFlow<E> extends UniqueBaseFlow<E> implements UniqueSortedDataFlow<E, E, E> {
		protected UniqueSortedBaseFlow(ObservableSortedSet<E> source) {
			super(source);
		}

		@Override
		protected ObservableSortedSet<E> getSource() {
			return (ObservableSortedSet<E>) super.getSource();
		}

		@Override
		public Comparator<? super E> comparator() {
			return getSource().comparator();
		}

		@Override
		public UniqueSortedDataFlow<E, E, E> filter(Function<? super E, String> filter) {
			return new UniqueSortedDataFlowWrapper<>(getSource(), super.filter(filter), getSource().comparator());
		}

		@Override
		public UniqueSortedDataFlow<E, E, E> filterStatic(Function<? super E, String> filter) {
			return new UniqueSortedDataFlowWrapper<>(getSource(), super.filterStatic(filter), getSource().comparator());
		}

		@Override
		public <X> UniqueSortedDataFlow<E, E, E> whereContained(ObservableCollection<X> other, boolean include) {
			return new UniqueSortedDataFlowWrapper<>(getSource(), super.whereContained(other, include), comparator());
		}

		@Override
		public <X> UniqueSortedMappedCollectionBuilder<E, E, X> mapEquivalent(TypeToken<X> target) {
			return new UniqueSortedMappedCollectionBuilder<>(getSource(), this, target);
		}

		@Override
		public UniqueSortedDataFlow<E, E, E> refresh(Observable<?> refresh) {
			return new UniqueSortedDataFlowWrapper<>(getSource(), super.refresh(refresh), getSource().comparator());
		}

		@Override
		public UniqueSortedDataFlow<E, E, E> refreshEach(Function<? super E, ? extends Observable<?>> refresh) {
			return new UniqueSortedDataFlowWrapper<>(getSource(), super.refreshEach(refresh), getSource().comparator());
		}

		@Override
		public UniqueSortedModFilterBuilder<E, E> filterModification() {
			return new UniqueSortedModFilterBuilder<>(getSource(), this);
		}

		@Override
		public ObservableSortedSet<E> collectLW() {
			return getSource();
		}

		@Override
		public ObservableSortedSet<E> collect() {
			return (ObservableSortedSet<E>) super.collect();
		}

		@Override
		public ObservableSortedSet<E> collect(Observable<?> until) {
			if (until == Observable.empty)
				return getSource();
			else
				return new DerivedSortedSet<>(getSource(), manageCollection(), getSource().comparator(), until);
		}
	}

	public static class DerivedLWSortedSet<E, T> extends ObservableSetImpl.DerivedLWSet<E, T> implements ObservableSortedSet<T> {
		private final Comparator<? super T> theCompare;

		public DerivedLWSortedSet(ObservableSortedSet<E> source, CollectionManager<E, ?, T> flow, Comparator<? super T> compare) {
			super(source, flow);
			theCompare = compare;
		}

		@Override
		protected ObservableSortedSet<E> getSource() {
			return (ObservableSortedSet<E>) super.getSource();
		}

		@Override
		public Comparator<? super T> comparator() {
			return theCompare;
		}

		protected Comparable<? super E> mappedSearch(Comparable<? super T> search) {
			return v -> search.compareTo(getFlow().map(v).result);
		}

		@Override
		public int indexFor(Comparable<? super T> search) {
			return getSource().indexFor(mappedSearch(search));
		}

		@Override
		public CollectionElement<T> search(Comparable<? super T> search, SortedSearchFilter filter) {
			CollectionElement<E> srcEl = getSource().search(mappedSearch(search), filter);
			return srcEl == null ? null : elementFor(srcEl);
		}

		@Override
		public CollectionElement<T> addIfEmpty(T value) throws IllegalStateException {
			return elementFor(getSource().addIfEmpty(getFlow().reverse(value).result));
		}
	}

	public static class DerivedSortedSet<E, T> extends ObservableSetImpl.DerivedSet<E, T> implements ObservableSortedSet<T> {
		private final Comparator<? super T> theCompare;

		public DerivedSortedSet(ObservableCollection<E> source, CollectionManager<E, ?, T> flow, Comparator<? super T> compare,
			Observable<?> until) {
			super(source, flow, until);
			theCompare = compare;
		}

		@Override
		public Comparator<? super T> comparator() {
			return theCompare;
		}

		@Override
		public int indexFor(Comparable<? super T> search) {
			return getPresentElements().indexFor(el -> search.compareTo(el.get()));
		}

		@Override
		public CollectionElement<T> search(Comparable<? super T> search, SortedSearchFilter filter) {
			CollectionElement<DerivedCollectionElement<E, T>> presentEl = getPresentElements().search(el -> search.compareTo(el.get()),
				filter);
			return presentEl == null ? null : observableElementFor(presentEl.get());
		}

		@Override
		public CollectionElement<T> addIfEmpty(T value) throws IllegalStateException {
			if (!belongs(value))
				throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
			try (Transaction t = lock(true, null)) {
				FilterMapResult<T, E> reverse = getFlow().reverse(value);
				if (reverse.error != null)
					throw new IllegalArgumentException(reverse.error);
				CollectionElement<E> added = getSource().addElement(reverse.result, false);
				return observableElementFor(getPresentElement(added.getElementId()));
			}
		}
	}

	/**
	 * Implements {@link ObservableSortedSet#flattenValue(ObservableValue)}
	 *
	 * @param <E> The type of elements in the set
	 */
	public static class FlattenedValueSortedSet<E> extends ObservableCollectionImpl.FlattenedValueCollection<E>
	implements ObservableSortedSet<E> {
		public FlattenedValueSortedSet(ObservableValue<? extends ObservableSortedSet<E>> collectionObservable) {
			super(collectionObservable);
		}

		@Override
		protected ObservableValue<? extends ObservableSortedSet<E>> getWrapped() {
			return (ObservableValue<? extends ObservableSortedSet<E>>) super.getWrapped();
		}

		@Override
		public Comparator<? super E> comparator() {
			ObservableSortedSet<E> set = getWrapped().get();
			return set == null ? (o1, o2) -> -1 : (Comparator<? super E>) set.comparator();
		}

		@Override
		public int indexFor(Comparable<? super E> search) {
			ObservableSortedSet<E> wrapped = getWrapped().get();
			if (wrapped == null)
				return -1;
			return wrapped.indexFor(search);
		}

		@Override
		public CollectionElement<E> search(Comparable<? super E> search, SortedSearchFilter filter) {
			ObservableSortedSet<E> wrapped = getWrapped().get();
			if (wrapped == null)
				return null;
			return wrapped.search(search, filter);
		}

		@Override
		public ObservableValue<E> observeRelative(Comparable<? super E> value, SortedSearchFilter filter, Supplier<? extends E> def) {
			return ObservableValue
				.flatten(getWrapped().mapV(new TypeToken<ObservableValue<E>>() {}.where(new TypeParameter<E>() {}, getType()),
					v -> v == null ? null : v.observeRelative(value, filter, def)));
		}

		@Override
		public CollectionElement<E> addIfEmpty(E value) throws IllegalStateException {
			ObservableSortedSet<E> wrapped = getWrapped().get();
			if (wrapped == null)
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			return wrapped.addIfEmpty(value);
		}
	}
}
