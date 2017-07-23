package org.observe.collect;

import java.util.Comparator;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.Subscription;
import org.observe.collect.ObservableCollection.UniqueSortedDataFlow;
import org.observe.collect.ObservableCollection.UniqueSortedMappedCollectionBuilder;
import org.observe.collect.ObservableCollection.UniqueSortedModFilterBuilder;
import org.observe.collect.ObservableCollectionDataFlowImpl.AbstractDataFlow;
import org.observe.collect.ObservableCollectionDataFlowImpl.CollectionManager;
import org.observe.collect.ObservableCollectionDataFlowImpl.UniqueSortedDataFlowWrapper;
import org.observe.collect.ObservableSetImpl.UniqueBaseFlow;
import org.qommons.Transaction;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.ElementHandle;
import org.qommons.collect.MutableElementHandle;
import org.qommons.collect.MutableElementSpliterator;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

public class ObservableSortedSetImpl {
	private ObservableSortedSetImpl() {}

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
		public ObservableValue<E> observeRelative(Comparable<? super E> search, boolean up) {
			return getWrapped().observeRelative(boundSearch(search), up).mapV(v -> isInRange(v) == 0 ? v : null, true);
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
		public ObservableValue<E> observeRelative(Comparable<? super E> search, boolean up) {
			return getWrapped().observeRelative(search, !up);
		}

		@Override
		public boolean forElement(Comparable<? super E> value, Consumer<? super ElementHandle<? extends E>> onElement, boolean up) {
			return getWrapped().forElement(value, onElement, !up);
		}

		@Override
		public boolean forMutableElement(Comparable<? super E> value, Consumer<? super MutableElementHandle<? extends E>> onElement,
			boolean up) {
			return getWrapped().forMutableElement(value, onElement, !up);
		}

		@Override
		public MutableElementSpliterator<E> mutableSpliterator(Comparable<? super E> value, boolean up) {
			return getWrapped().mutableSpliterator(value, !up).reverse();
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
		public Comparator<? super E> comparator() {
			return getSource().comparator();
		}

		@Override
		public UniqueSortedDataFlow<E, E, E> filter(Function<? super E, String> filter) {
			return new UniqueSortedDataFlowWrapper<>(getSource(), (AbstractDataFlow<E, ?, E>) super.filter(filter),
				getSource().comparator());
		}

		@Override
		public UniqueSortedDataFlow<E, E, E> filterStatic(Function<? super E, String> filter) {
			return new UniqueSortedDataFlowWrapper<>(getSource(), (AbstractDataFlow<E, ?, E>) super.filterStatic(filter),
				getSource().comparator());
		}

		@Override
		public <X> UniqueSortedMappedCollectionBuilder<E, E, X> mapEquivalent(TypeToken<X> target) {
			return new UniqueSortedMappedCollectionBuilder<>(getSource(), this, target);
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
		public ObservableValue<T> observeRelative(Comparable<? super T> search, boolean up) {
			return getSource().observeRelative(mappedSearch(search), up).mapV(getType(), v -> getFlow().map(v).result);
		}

		@Override
		public int indexFor(Comparable<? super T> search) {
			return getSource().indexFor(mappedSearch(search));
		}

		@Override
		public boolean forElement(Comparable<? super T> search, Consumer<? super ElementHandle<? extends T>> onElement, boolean up) {
			return getSource().forElement(mappedSearch(search), el -> onElement.accept(elementFor(el)), up);
		}

		@Override
		public boolean forMutableElement(Comparable<? super T> search, Consumer<? super MutableElementHandle<? extends T>> onElement,
			boolean up) {
			return getSource().forMutableElement(mappedSearch(search), el -> onElement.accept(mutableElementFor(el)), up);
		}

		@Override
		public MutableElementSpliterator<T> mutableSpliterator(Comparable<? super T> search, boolean up) {
			return new DerivedMutableSpliterator(getSource().mutableSpliterator(mappedSearch(search), up));
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
			return getPresentElements().indexFor(el -> search.compareTo(el.getValue()));
		}

		@Override
		public ObservableValue<T> observeRelative(Comparable<? super T> search, boolean up) {
			if (up)
				return subSet(search, null).observeFind(v -> true, () -> null, true);
			else
				return subSet(null, search).observeFind(v -> true, () -> null, false);
		}

		@Override
		public boolean forElement(Comparable<? super T> search, Consumer<? super ElementHandle<? extends T>> onElement, boolean up) {
			try (Transaction t = lock(false, null)) {
				DerivedCollectionElement element = getPresentElements().relative(el -> search.compareTo(el.getValue()), up);
				if (element == null)
					return false;
				forElementAt(element, onElement);
				return true;
			}
		}

		@Override
		public boolean forMutableElement(Comparable<? super T> search, Consumer<? super MutableElementHandle<? extends T>> onElement,
			boolean up) {
			try (Transaction t = lock(true, null)) {
				DerivedCollectionElement element = getPresentElements().relative(el -> search.compareTo(el.getValue()), up);
				if (element == null)
					return false;
				forMutableElementAt(element, onElement);
				return true;
			}
		}

		@Override
		public MutableElementSpliterator<T> mutableSpliterator(Comparable<? super T> search, boolean up) {
			return new MutableDerivedSpliterator(getPresentElements().relative(search, up));
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
		public ObservableValue<E> observeRelative(Comparable<? super E> value, boolean up) {
			return ObservableValue
				.flatten(getWrapped().mapV(new TypeToken<ObservableValue<E>>() {}.where(new TypeParameter<E>() {}, getType()),
					v -> v == null ? null : v.observeRelative(value, up)));
		}

		@Override
		public boolean forElement(Comparable<? super E> value, Consumer<? super ElementHandle<? extends E>> onElement, boolean up) {
			ObservableSortedSet<E> wrapped = getWrapped().get();
			if (wrapped == null)
				return false;
			return wrapped.forElement(value, onElement, up);
		}

		@Override
		public boolean forMutableElement(Comparable<? super E> value, Consumer<? super MutableElementHandle<? extends E>> onElement,
			boolean up) {
			ObservableSortedSet<E> wrapped = getWrapped().get();
			if (wrapped == null)
				return false;
			return wrapped.forMutableElement(value, onElement, up);
		}

		@Override
		public MutableElementSpliterator<E> mutableSpliterator(Comparable<? super E> value, boolean up) {
			ObservableSortedSet<E> wrapped = getWrapped().get();
			if (wrapped == null)
				return MutableElementSpliterator.empty();
			return wrapped.mutableSpliterator(value, up);
		}
	}
}
