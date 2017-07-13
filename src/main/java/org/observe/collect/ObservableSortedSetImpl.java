package org.observe.collect;

import java.util.Comparator;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.collect.ObservableCollection.UniqueSortedDataFlow;
import org.observe.collect.ObservableCollectionDataFlowImpl.AbstractDataFlow;
import org.observe.collect.ObservableCollectionDataFlowImpl.CollectionManager;
import org.observe.collect.ObservableCollectionDataFlowImpl.UniqueSortedCollectionManager;
import org.observe.collect.ObservableCollectionDataFlowImpl.UniqueSortedDataFlowWrapper;
import org.observe.collect.ObservableCollectionDataFlowImpl.UniqueSortedElementFinder;
import org.observe.collect.ObservableSetImpl.UniqueBaseFlow;

public class ObservableSortedSetImpl {
	private ObservableSortedSetImpl() {}

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
		public ObservableValue<E> relative(E value, boolean up, boolean withValue) {
			return getWrapped().relative(value, !up, withValue);
		}

		@Override
		public boolean forElement(E value, boolean up, boolean withValue,
			Consumer<? super ObservableCollectionElement<? extends E>> onElement) {
			return getWrapped().forElement(value, !up, withValue, onElement);
		}

		@Override
		public boolean forMutableElement(E value, boolean up, boolean withValue,
			Consumer<? super MutableObservableElement<? extends E>> onElement) {
			return getWrapped().forMutableElement(value, !up, withValue, onElement);
		}

		@Override
		public MutableObservableSpliterator<E> mutableSpliterator(E value, boolean up, boolean withValue) {
			return getWrapped().mutableSpliterator(value, !up, withValue);
		}

		@Override
		public ObservableSortedSet<E> subSet(E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
			return getWrapped().subSet(toElement, toInclusive, fromElement, fromInclusive);
		}

		@Override
		public ObservableSortedSet<E> headSet(E toElement, boolean inclusive) {
			return getWrapped().tailSet(toElement, inclusive);
		}

		@Override
		public ObservableSortedSet<E> tailSet(E fromElement, boolean inclusive) {
			return getWrapped().headSet(fromElement, inclusive);
		}

		@Override
		public ObservableSortedSet<E> reverse() {
			return (ObservableSortedSet<E>) super.reverse();
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
		public UniqueSortedDataFlow<E, E, E> filter(Function<? super E, String> filter) {
			return (UniqueSortedDataFlow<E, E, E>) super.filter(filter);
		}

		@Override
		public UniqueSortedDataFlow<E, E, E> filter(Function<? super E, String> filter, boolean filterNulls) {
			return new UniqueSortedDataFlowWrapper<>(getSource(), (AbstractDataFlow<E, ?, E>) super.filter(filter, filterNulls),
				getSource().comparator());
		}

		@Override
		public UniqueSortedDataFlow<E, E, E> filterStatic(Function<? super E, String> filter) {
			return (UniqueSortedDataFlow<E, E, E>) super.filterStatic(filter);
		}

		@Override
		public UniqueSortedDataFlow<E, E, E> filterStatic(Function<? super E, String> filter, boolean filterNulls) {
			return new UniqueSortedDataFlowWrapper<>(getSource(), (AbstractDataFlow<E, ?, E>) super.filterStatic(filter, filterNulls),
				getSource().comparator());
		}

		@Override
		public UniqueSortedDataFlow<E, E, E> refresh(Observable<?> refresh) {
			return new UniqueSortedDataFlowWrapper<>(getSource(), (AbstractDataFlow<E, ?, E>) super.refresh(refresh),
				getSource().comparator());
		}

		@Override
		public UniqueSortedDataFlow<E, E, E> refreshEach(Function<? super E, ? extends Observable<?>> refresh) {
			return new UniqueSortedDataFlowWrapper<>(getSource(), (AbstractDataFlow<E, ?, E>) super.refreshEach(refresh),
				getSource().comparator());
		}

		@Override
		public ObservableSortedSet<E> collect(Observable<?> until) {
			if (until == Observable.empty)
				return getSource();
			else
				return new DerivedSortedSet<>(getSource(), manageCollection(), getSource().comparator(), until);
		}
	}

	public static class DerivedSortedSet<E, T> extends ObservableSetImpl.DerivedSet<E, T> implements ObservableSortedSet<T> {
		private final Comparator<? super T> theCompare;

		public DerivedSortedSet(ObservableCollection<E> source, CollectionManager<E, ?, T> flow, UniqueSortedElementFinder<T> elementFinder,
			Comparator<? super T> compare, Observable<?> until) {
			super(source, flow, elementFinder, until);
			theCompare = compare;
		}

		@Override
		public Comparator<? super T> comparator() {
			return theCompare;
		}

		@Override
		protected UniqueSortedCollectionManager<E, ?, T> getFlow() {
			return (UniqueSortedCollectionManager<E, ?, T>) super.getFlow();
		}

		@Override
		public ObservableValue<T> relative(T value, boolean up, boolean withValue) {
			if (up)
				return tailSet(value, withValue).find(v -> true, () -> null, true);
			else
				return headSet(value, withValue).find(v -> true, () -> null, false);
		}

		@Override
		public boolean forElement(T value, boolean up, boolean withValue,
			Consumer<? super ObservableCollectionElement<? extends T>> onElement) {
			ElementId id = getFlow().relativeId(value, up, withValue);
			if (id == null)
				return false;
			forWrappedElementAt(id, onElement);
			return true;
		}

		@Override
		public boolean forMutableElement(T value, boolean up, boolean withValue,
			Consumer<? super MutableObservableElement<? extends T>> onElement) {
			ElementId id = getFlow().relativeId(value, up, withValue);
			if (id == null)
				return false;
			forWrappedMutableElementAt(id, onElement);
			return true;
		}

		@Override
		public MutableObservableSpliterator<T> mutableSpliterator(T value, boolean up, boolean withValue) {
			ElementId id = getFlow().relativeId(value, up, withValue);
			if (id == null)
				return MutableObservableSpliterator.empty(getType());
			return new MutableDerivedSpliterator(getPresentElements().spliteratorFrom(id));
		}
	}

	/**
	 * Implements {@link ObservableSortedSet#flattenValue(ObservableValue)}
	 *
	 * @param <E> The type of elements in the set
	 */
	public static class FlattenedValueSortedSet<E> extends ObservableReversibleCollectionImpl.FlattenedReversibleValueCollection<E>
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
		public Iterable<E> iterateFrom(E element, boolean included, boolean reversed) {
			ObservableSortedSet<E> set = getWrapped().get();
			return set == null ? java.util.Collections.EMPTY_LIST : set.iterateFrom(element, included, reversed);
		}
	}
}
