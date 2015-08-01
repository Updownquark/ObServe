package org.observe.collect;

import static org.observe.ObservableDebug.d;

import java.util.Comparator;
import java.util.Iterator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.Subscription;
import org.observe.util.Transaction;

import prisms.lang.Type;

/**
 * A sorted set whose content can be observed. This set is immutable in that none of its methods, including {@link java.util.Set} methods,
 * can modify its content (Set modification methods will throw {@link UnsupportedOperationException}). All {@link ObservableElement}s
 * returned by this observable will be instances of {@link ObservableOrderedElement}.
 *
 * @param <E> The type of element in the set
 */
public interface ObservableSortedSet<E> extends ObservableSet<E>, ObservableReversibleCollection<E>, TransactableSortedSet<E> {
	/**
	 * Returns a value at or adjacent to another value
	 *
	 * @param value The relative value
	 * @param up Whether to get the closest value greater or less than the given value
	 * @param withValue Whether to return the given value if it exists in the map
	 * @return An observable value with the result of the operation
	 */
	default ObservableValue<E> relative(E value, boolean up, boolean withValue) {
		if(up)
			return tailSet(value, withValue).getFirst();
		else
			return headSet(value, withValue).getLast();
	}

	@Override
	default E first() {
		E ret = getFirst().get();
		if(ret == null)
			throw new java.util.NoSuchElementException();
		return ret;
	}

	@Override
	default E last() {
		E ret = getLast().get();
		if(ret == null)
			throw new java.util.NoSuchElementException();
		return ret;
	}

	@Override
	default E floor(E e) {
		return relative(e, false, true).get();
	}

	@Override
	default E lower(E e) {
		return relative(e, false, false).get();
	}

	@Override
	default E ceiling(E e) {
		return relative(e, true, true).get();
	}

	@Override
	default E higher(E e) {
		return relative(e, true, false).get();
	}

	@Override
	default ObservableSortedSet<E> reverse() {
		return descendingSet();
	}

	@Override
	default ObservableSortedSet<E> descendingSet() {
		return subSet(null, true, null, true, true);
	}

	@Override
	default Iterator<E> descendingIterator() {
		return descending().iterator();
	}

	/**
	 * @param element The element to start iteration at
	 * @param included Whether to include the given element in the iteration
	 * @param reversed Whether to iterate backward or forward from the given element
	 * @return An iterable that starts iteration from the given element
	 */
	default Iterable<E> iterateFrom(E element, boolean included, boolean reversed) {
		return () -> new Iterator<E>() {
			private final Iterator<E> backing = reversed ? descendingIterator() : ObservableSortedSet.this.iterator();

			private E theFirst;

			{
				Comparator<? super E> compare = comparator();
				while(backing.hasNext()) {
					theFirst = backing.next();
					int comp = compare.compare(theFirst, element);
					if(comp > 0 || (included && comp == 0))
						break;
				}
			}

			@Override
			public boolean hasNext() {
				return backing.hasNext();
			}

			@Override
			public E next() {
				return backing.next();
			}

			@Override
			public void remove() {
				backing.remove();
			}
		};
	}

	/**
	 * A sub-set of this set. Like {@link #subSet(Object, boolean, Object, boolean)}, but may be reversed.
	 *
	 * @param fromElement The minimum bounding element for the sub set
	 * @param fromInclusive Whether the minimum bound will be included in the sub set (if present in this set)
	 * @param toElement The maximum bounding element for the sub set
	 * @param toInclusive Whether the maximum bound will be included in the sub set (if present in this set)
	 * @param reversed Whether the returned sub set will be in the opposite order as this set
	 * @return The sub set
	 */
	default ObservableSortedSet<E> subSet(E fromElement, boolean fromInclusive, E toElement, boolean toInclusive, boolean reversed) {
		return new ObservableSubSet<>(this, fromElement, fromInclusive, toElement, toInclusive, reversed);
	}

	@Override
	default ObservableSortedSet<E> subSet(E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
		return subSet(fromElement, fromInclusive, toElement, toInclusive, false);
	}

	@Override
	default ObservableSortedSet<E> headSet(E toElement, boolean inclusive) {
		return subSet(null, true, toElement, inclusive);
	}

	@Override
	default ObservableSortedSet<E> tailSet(E fromElement, boolean inclusive) {
		return subSet(fromElement, inclusive, null, true);
	}

	@Override
	default ObservableSortedSet<E> subSet(E fromElement, E toElement) {
		return subSet(fromElement, true, toElement, false);
	}

	@Override
	default ObservableSortedSet<E> headSet(E toElement) {
		return headSet(toElement, false);
	}

	@Override
	default ObservableSortedSet<E> tailSet(E fromElement) {
		return tailSet(fromElement, true);
	}

	/**
	 * @param o The value to get the index of
	 * @return The index of the given value in this collection, or, if the given value is not present in this set, <code>-dest-1</code>,
	 *         where <code>dest</code> is the index of the position where the given element would appear if it were added to this set.
	 * @throws ClassCastException If the given value is not null or an instance of this set's type.
	 * @see org.observe.collect.ObservableOrderedCollection#indexOf(java.lang.Object)
	 */
	@Override
	default int indexOf(Object o) {
		return ObservableReversibleCollection.super.indexOf(o);
	}

	/**
	 * Same as {@link #indexOf(Object)} for sorted sets
	 *
	 * @param o The value to get the index of
	 * @return The index of the given value in this collection, or, if the given value is not present in this set, <code>-dest-1</code>,
	 *         where <code>dest</code> is the index of the position where the given element would appear if it were added to this set.
	 * @throws ClassCastException If the given value is not null or an instance of this set's type.
	 * @see org.observe.collect.ObservableOrderedCollection#indexOf(java.lang.Object)
	 */
	@Override
	default int lastIndexOf(Object o) {
		return indexOf(o);
	}

	/**
	 * @param filter The filter function
	 * @return A set containing all elements passing the given test
	 */
	@Override
	default ObservableSortedSet<E> filter(Predicate<? super E> filter) {
		Function<E, E> map = value -> (value != null && filter.test(value)) ? value : null;
		return d().debug(new FilteredSortedSet<>(this, getType(), map)).from("filter", this).using("filter", filter).get();
	}

	@Override
	default <T> ObservableSortedSet<T> filter(Class<T> type) {
		Function<E, T> map = value -> type.isInstance(value) ? type.cast(value) : null;
		return d().debug(new FilteredSortedSet<>(this, new Type(type), map)).from("filterMap", this).using("map", map)
			.tag("filterType", type).get();
	}

	/**
	 * @param refresh The observable to re-fire events on
	 * @return A set whose elements fire additional value events when the given observable fires
	 */
	@Override
	default ObservableSortedSet<E> refresh(Observable<?> refresh) {
		return d().debug(new RefreshingSortedSet<>(this, refresh)).from("refresh", this).from("on", refresh).get();
	}

	/**
	 * @param refresh A function that supplies a refresh observable as a function of element value
	 * @return A collection whose values individually refresh when the observable returned by the given function fires
	 */
	@Override
	default ObservableSortedSet<E> refreshEach(Function<? super E, Observable<?>> refresh) {
		return d().debug(new ElementRefreshingSortedSet<>(this, refresh)).from("refreshEach", this).using("on", refresh).get();
	}

	@Override
	default ObservableSortedSet<E> immutable() {
		return d().debug(new ImmutableObservableSortedSet<>(this)).from("immutable", this).get();
	}

	@Override
	default ObservableSortedSet<E> filterRemove(Predicate<? super E> filter) {
		return (ObservableSortedSet<E>) ObservableReversibleCollection.super.filterRemove(filter);
	}

	@Override
	default ObservableSortedSet<E> noRemove() {
		return (ObservableSortedSet<E>) ObservableReversibleCollection.super.noRemove();
	}

	@Override
	default ObservableSortedSet<E> filterAdd(Predicate<? super E> filter) {
		return (ObservableSortedSet<E>) ObservableReversibleCollection.super.filterAdd(filter);
	}

	@Override
	default ObservableSortedSet<E> noAdd() {
		return (ObservableSortedSet<E>) ObservableReversibleCollection.super.noAdd();
	}

	@Override
	default ObservableSortedSet<E> filterModification(Predicate<? super E> removeFilter, Predicate<? super E> addFilter) {
		return new ModFilteredSortedSet<>(this, removeFilter, addFilter);
	}

	@Override
	default ObservableSortedSet<E> cached() {
		return d().debug(new SafeCachedObservableSortedSet<>(this)).from("cached", this).get();
	}

	/**
	 * An extension of ObservableSortedSet that implements some of the redundant methods and throws UnsupportedOperationExceptions for
	 * modifications.
	 *
	 * @param <E> The type of element in the set
	 */
	interface PartialSortedSetImpl<E> extends PartialSetImpl<E>, ObservableSortedSet<E> {
		@Override
		default Subscription onElement(Consumer<? super ObservableElement<E>> onElement) {
			return onOrderedElement(onElement);
		}

		@Override
		default ObservableValue<E> relative(E value, boolean up, boolean withValue) {
			if(up)
				return tailSet(value, withValue).getFirst();
			else
				return headSet(value, withValue).getFirst();
		}

		@Override
		default E pollFirst() {
			Iterator<E> iter = iterator();
			if(iter.hasNext()) {
				E ret = iter.next();
				iter.remove();
				return ret;
			}
			return null;
		}

		@Override
		default E pollLast() {
			Iterator<E> iter = descendingIterator();
			if(iter.hasNext()) {
				E ret = iter.next();
				iter.remove();
				return ret;
			}
			return null;
		}
	}

	class ObservableSubSet<E> implements PartialSortedSetImpl<E> {
		private final ObservableSortedSet<E> theWrapped;

		private final E theMin;
		private final boolean isMinIncluded;
		private final E theMax;
		private final boolean isMaxIncluded;

		private final boolean isReversed;

		public ObservableSubSet(ObservableSortedSet<E> set, E min, boolean includeMin, E max, boolean includeMax, boolean reversed) {
			super();
			theWrapped = set;
			theMin = min;
			isMinIncluded = includeMin;
			theMax = max;
			isMaxIncluded = includeMax;
			isReversed = reversed;
		}

		public ObservableSortedSet<E> getWrapped() {
			return theWrapped;
		}

		public E getMin() {
			return theMin;
		}

		public boolean isMinIncluded() {
			return isMinIncluded;
		}

		public E getMax() {
			return theMax;
		}

		public boolean isMaxIncluded() {
			return isMaxIncluded;
		}

		public boolean isReversed() {
			return isReversed;
		}

		public boolean isInRange(E value) {
			Comparator<? super E> compare = theWrapped.comparator();
			if(theMin != null) {
				int comp = compare.compare(value, theMin);
				if(comp < 0 || (!isMinIncluded && comp == 0))
					return false;
			}
			if(theMax != null) {
				int comp = compare.compare(value, theMax);
				if(comp < 0 || (!isMaxIncluded && comp == 0))
					return false;
			}
			return true;
		}

		@Override
		public Type getType() {
			return theWrapped.getType();
		}

		@Override
		public ObservableValue<CollectionSession> getSession() {
			return theWrapped.getSession();
		}

		@Override
		public int size() {
			int maxIndex = theWrapped.indexOf(theMax);
			if(maxIndex >= 0) {

			}
			// TODO Auto-generated method stub
			return 0;
		}

		@Override
		public Iterator<E> iterator() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theWrapped.lock(write, cause);
		}

		@Override
		public Subscription onOrderedElement(Consumer<? super ObservableOrderedElement<E>> onElement) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public Comparator<? super E> comparator() {
			Comparator<? super E> compare = theWrapped.comparator();
			if(isReversed)
				compare = compare.reversed();
			return compare;
		}

		@Override
		public Iterable<E> descending() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public ObservableValue<E> relative(E value, boolean up, boolean withValue) {
			if(isReversed)
				up = !up;
			Comparator<? super E> compare = theWrapped.comparator();
			if(!up && theMin != null) {
				int comp = compare.compare(value, theMin);
				if(comp < 0 || (!withValue && comp == 0))
					return ObservableValue.constant(getType(), null);
			}
			if(up && theMax != null) {
				int comp = compare.compare(value, theMax);
				if(comp > 0 || (!withValue && comp == 0))
					return ObservableValue.constant(getType(), null);
			}
			return theWrapped.relative(value, up, withValue).mapV(v -> isInRange(v) ? v : null);
		}

		@Override
		public ObservableSortedSet<E> subSet(E min, boolean includeMin, E max, boolean includeMax, boolean reverse) {
			if(isReversed) {
				E temp = min;
				min = max;
				max = temp;
				boolean tempB = includeMin;
				includeMin = includeMax;
				includeMax = tempB;
			}
			if(min == null)
				min = theMin;
			else if(theMin != null && theWrapped.comparator().compare(min, theMin) <= 0) {
				min = theMin;
				includeMin = isMinIncluded;
			}
			if(max == null)
				max = theMax;
			else if(theMax != null && theWrapped.comparator().compare(max, theMax) >= 0) {
				max = theMax;
				includeMax = isMaxIncluded;
			}
			return new ObservableSubSet<>(theWrapped, min, includeMin, max, includeMax, reverse ^ isReversed);
		}
	}

	/**
	 * Implements {@link ObservableSortedSet#reverse()}
	 *
	 * @param <E> The type of elements in the collection
	 */
	class ReversedSortedSet<E> extends ReversedCollection<E> implements PartialSortedSetImpl<E> {
		ReversedSortedSet(ObservableSortedSet<E> wrap) {
			super(wrap);
		}

		@Override
		protected ObservableSortedSet<E> getWrapped() {
			return (ObservableSortedSet<E>) super.getWrapped();
		}

		@Override
		public ObservableValue<E> relative(E value, boolean up, boolean withValue) {
			return getWrapped().relative(value, !up, withValue);
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
		public E pollFirst() {
			return getWrapped().pollLast();
		}

		@Override
		public E pollLast() {
			return getWrapped().pollFirst();
		}

		@Override
		public Comparator<? super E> comparator() {
			return getWrapped().comparator().reversed();
		}
	}

	/**
	 * Implements {@link ObservableSortedSet#filter(Predicate)} and {@link ObservableSortedSet#filter(Class)}
	 *
	 * @param <E> The type of the set to filter
	 * @param <T> the type of the mapped set
	 */
	class FilteredSortedSet<E, T> extends FilteredReversibleCollection<E, T> implements PartialSortedSetImpl<T> {
		/* Note that everywhere we cast a T-typed value to E is safe because this sorted set is only called from filter, not map */

		protected FilteredSortedSet(ObservableSortedSet<E> wrap, Type type, Function<? super E, T> map) {
			super(wrap, type, map, value -> (E) value);
		}

		@Override
		protected ObservableSortedSet<E> getWrapped() {
			return (ObservableSortedSet<E>) super.getWrapped();
		}

		@Override
		public ObservableSortedSet<T> subSet(T fromElement, boolean fromInclusive, T toElement, boolean toInclusive) {
			return new FilteredSortedSet<>(getWrapped().subSet((E) fromElement, fromInclusive, (E) toElement, toInclusive), getType(),
				getMap());
		}

		@Override
		public ObservableSortedSet<T> headSet(T toElement, boolean inclusive) {
			return new FilteredSortedSet<>(getWrapped().headSet((E) toElement, inclusive), getType(), getMap());
		}

		@Override
		public ObservableSortedSet<T> tailSet(T fromElement, boolean inclusive) {
			return new FilteredSortedSet<>(getWrapped().tailSet((E) fromElement, inclusive), getType(), getMap());
		}

		@Override
		public Comparator<? super T> comparator() {
			return (o1, o2) -> {
				return getWrapped().comparator().compare((E) o1, (E) o2);
			};
		}
	}

	/**
	 * Implements {@link ObservableSortedSet#refresh(Observable)}
	 *
	 * @param <E> The type of the set
	 */
	class RefreshingSortedSet<E> extends RefreshingReversibleCollection<E> implements PartialSortedSetImpl<E> {
		public RefreshingSortedSet(ObservableSortedSet<E> wrap, Observable<?> refresh) {
			super(wrap, refresh);
		}

		@Override
		protected ObservableSortedSet<E> getWrapped() {
			return (ObservableSortedSet<E>) super.getWrapped();
		}

		@Override
		public ObservableSortedSet<E> subSet(E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
			return new RefreshingSortedSet<>(getWrapped().subSet(fromElement, fromInclusive, toElement, toInclusive), getRefresh());
		}

		@Override
		public ObservableSortedSet<E> headSet(E toElement, boolean inclusive) {
			return new RefreshingSortedSet<>(getWrapped().headSet(toElement, inclusive), getRefresh());
		}

		@Override
		public ObservableSortedSet<E> tailSet(E fromElement, boolean inclusive) {
			return new RefreshingSortedSet<>(getWrapped().tailSet(fromElement, inclusive), getRefresh());
		}

		@Override
		public Comparator<? super E> comparator() {
			return getWrapped().comparator();
		}
	}

	/**
	 * Implements {@link ObservableSortedSet#refreshEach(Function)}
	 *
	 * @param <E> The type of the set
	 */
	class ElementRefreshingSortedSet<E> extends ElementRefreshingReversibleCollection<E> implements PartialSortedSetImpl<E> {
		public ElementRefreshingSortedSet(ObservableSortedSet<E> wrap, Function<? super E, Observable<?>> refresh) {
			super(wrap, refresh);
		}

		@Override
		protected ObservableSortedSet<E> getWrapped() {
			return (ObservableSortedSet<E>) super.getWrapped();
		}

		@Override
		public ObservableSortedSet<E> subSet(E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
			return new ElementRefreshingSortedSet<>(getWrapped().subSet(fromElement, fromInclusive, toElement, toInclusive), getRefresh());
		}

		@Override
		public ObservableSortedSet<E> headSet(E toElement, boolean inclusive) {
			return new ElementRefreshingSortedSet<>(getWrapped().headSet(toElement, inclusive), getRefresh());
		}

		@Override
		public ObservableSortedSet<E> tailSet(E fromElement, boolean inclusive) {
			return new ElementRefreshingSortedSet<>(getWrapped().tailSet(fromElement, inclusive), getRefresh());
		}

		@Override
		public Comparator<? super E> comparator() {
			return getWrapped().comparator();
		}
	}

	/**
	 * Implements {@link ObservableSortedSet#immutable()}
	 *
	 * @param <E> The type of the set
	 */
	class ImmutableObservableSortedSet<E> extends ImmutableReversibleCollection<E> implements PartialSortedSetImpl<E> {
		public ImmutableObservableSortedSet(ObservableSortedSet<E> wrap) {
			super(wrap);
		}

		@Override
		protected ObservableSortedSet<E> getWrapped() {
			return (ObservableSortedSet<E>) super.getWrapped();
		}

		@Override
		public ObservableSortedSet<E> subSet(E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
			return new ImmutableObservableSortedSet<>(getWrapped().subSet(fromElement, fromInclusive, toElement, toInclusive));
		}

		@Override
		public ObservableSortedSet<E> headSet(E toElement, boolean inclusive) {
			return new ImmutableObservableSortedSet<>(getWrapped().headSet(toElement, inclusive));
		}

		@Override
		public ObservableSortedSet<E> tailSet(E fromElement, boolean inclusive) {
			return new ImmutableObservableSortedSet<>(getWrapped().tailSet(fromElement, inclusive));
		}

		@Override
		public Comparator<? super E> comparator() {
			return getWrapped().comparator();
		}

		@Override
		public ImmutableObservableSortedSet<E> immutable() {
			return this;
		}
	}

	/**
	 * Implements {@link ObservableSortedSet#filterModification(Predicate, Predicate)}
	 *
	 * @param <E> The type of elements in the set
	 */
	class ModFilteredSortedSet<E> extends ModFilteredReversibleCollection<E> implements PartialSortedSetImpl<E> {
		public ModFilteredSortedSet(ObservableSortedSet<E> wrapped, Predicate<? super E> removeFilter, Predicate<? super E> addFilter) {
			super(wrapped, removeFilter, addFilter);
		}

		@Override
		protected ObservableSortedSet<E> getWrapped() {
			return (ObservableSortedSet<E>) super.getWrapped();
		}

		@Override
		public Comparator<? super E> comparator() {
			return getWrapped().comparator();
		}
	}

	/**
	 * Implements {@link ObservableSortedSet#cached()}
	 *
	 * @param <E> The type of the set
	 */
	class SafeCachedObservableSortedSet<E> extends SafeCachedReversibleCollection<E> implements PartialSortedSetImpl<E> {
		public SafeCachedObservableSortedSet(ObservableSortedSet<E> wrap) {
			super(wrap);
		}

		@Override
		protected ObservableSortedSet<E> getWrapped() {
			return (ObservableSortedSet<E>) super.getWrapped();
		}

		@Override
		public Comparator<? super E> comparator() {
			return getWrapped().comparator();
		}

		@Override
		public ObservableSortedSet<E> cached() {
			return this;
		}
	}
}
