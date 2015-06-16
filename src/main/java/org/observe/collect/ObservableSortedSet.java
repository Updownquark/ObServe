package org.observe.collect;

import static org.observe.ObservableDebug.d;

import java.util.NavigableSet;
import java.util.function.Function;
import java.util.function.Predicate;

import org.observe.Observable;

import prisms.lang.Type;

/**
 * A sorted set whose content can be observed. This set is immutable in that none of its methods, including {@link java.util.Set} methods,
 * can modify its content (Set modification methods will throw {@link UnsupportedOperationException}). All {@link ObservableElement}s
 * returned by this observable will be instances of {@link OrderedObservableElement}.
 *
 * @param <E> The type of element in the set
 */
public interface ObservableSortedSet<E> extends ObservableSet<E>, ObservableOrderedCollection<E>, NavigableSet<E> {
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
	}
}
