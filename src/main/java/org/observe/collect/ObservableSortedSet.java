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

import prisms.lang.Type;

/**
 * A sorted set whose content can be observed. This set is immutable in that none of its methods, including {@link java.util.Set} methods,
 * can modify its content (Set modification methods will throw {@link UnsupportedOperationException}). All {@link ObservableElement}s
 * returned by this observable will be instances of {@link OrderedObservableElement}.
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
		return getFirst().get();
	}

	@Override
	default E last() {
		return getLast().get();
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
		return new ReversedSortedSet<>(this);
	}

	@Override
	default ObservableSortedSet<E> descendingSet() {
		return reverse();
	}

	@Override
	default Iterator<E> descendingIterator() {
		return descending().iterator();
	}

	@Override
	default ObservableSortedSet<E> subSet(E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
		return filter(el -> {
			if(fromElement != null) {
				int fromCompare = comparator().compare(el, fromElement);
				if(fromCompare < 0)
					return false;
				if(fromCompare == 0 && !fromInclusive)
					return false;
			}

			if(toElement != null) {
				int toCompare = comparator().compare(el, toElement);
				if(toCompare > 0)
					return false;
				if(toCompare == 0 && !toInclusive)
					return false;
			}

			return true;
		});
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
			super(wrap, type, map);
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
