package org.observe.collect;

import static org.observe.ObservableDebug.d;

import java.util.function.BiFunction;
import java.util.function.Function;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.qommons.BiTuple;
import org.qommons.TriFunction;
import org.qommons.TriTuple;

import com.google.common.reflect.TypeToken;

/**
 * An observable element that knows its position in the collection
 *
 * @param <E> The type of the element
 */
public interface ObservableOrderedElement<E> extends ObservableElement<E> {
	/** @return The index of this element within its list */
	int getIndex();

	@Override
	default ObservableOrderedElement<E> takeUntil(Observable<?> until) {
		return d().debug(new OrderedElementTakenUntil<>(this, until, true)).from("take", this).from("until", until)
				.tag("withCompletion", true).get();
	}

	@Override
	default ObservableOrderedElement<E> unsubscribeOn(Observable<?> until) {
		return d().debug(new OrderedElementTakenUntil<>(this, until, false)).from("take", this).from("until", until)
				.tag("withCompletion", false).get();
	}

	@Override
	default ObservableOrderedElement<E> cached() {
		return d().debug(new CachedOrderedObservableElement<>(this)).from("cached", this).get();
	}

	/**
	 * Composes this observable into another observable that depends on this one
	 *
	 * @param <R> The type of the new observable
	 * @param function The function to apply to this observable's value
	 * @return The new observable whose value is a function of this observable's value
	 */
	@Override
	default <R> ObservableOrderedElement<R> mapV(Function<? super E, R> function) {
		return mapV(null, function, false);
	};

	/**
	 * Composes this observable into another observable that depends on this one
	 *
	 * @param <R> The type of the new observable
	 * @param type The run-time type of the new observable
	 * @param function The function to apply to this observable's value
	 * @return The new observable whose value is a function of this observable's value
	 */
	@Override
	default <R> ObservableOrderedElement<R> mapV(TypeToken<R> type, Function<? super E, R> function, boolean combineNull) {
		return new ComposedObservableListElement<>(this, type, args -> {
			return function.apply((E) args[0]);
		}, combineNull, this);
	};

	/**
	 * Composes this observable into another observable that depends on this one and one other
	 *
	 * @param <U> The type of the other argument observable
	 * @param <R> The type of the new observable
	 * @param function The function to apply to the values of the observables
	 * @param arg The other observable to be composed
	 * @return The new observable whose value is a function of this observable's value and the other's
	 */
	@Override
	default <U, R> ObservableOrderedElement<R> combineV(BiFunction<? super E, ? super U, R> function, ObservableValue<U> arg) {
		return combineV(null, function, arg, false);
	}

	/**
	 * Composes this observable into another observable that depends on this one and one other
	 *
	 * @param <U> The type of the other argument observable
	 * @param <R> The type of the new observable
	 * @param type The run-time type of the new observable
	 * @param function The function to apply to the values of the observables
	 * @param arg The other observable to be composed
	 * @return The new observable whose value is a function of this observable's value and the other's
	 */
	@Override
	default <U, R> ObservableOrderedElement<R> combineV(TypeToken<R> type, BiFunction<? super E, ? super U, R> function,
			ObservableValue<U> arg,
			boolean combineNull) {
		return new ComposedObservableListElement<>(this, type, args -> {
			return function.apply((E) args[0], (U) args[1]);
		}, combineNull, this, arg);
	}

	/**
	 * @param <U> The type of the other observable to tuplize
	 * @param arg The other observable to tuplize
	 * @return An observable which broadcasts tuples of the latest values of this observable value and another
	 */
	@Override
	default <U> ObservableOrderedElement<BiTuple<E, U>> tupleV(ObservableValue<U> arg) {
		return combineV(BiTuple<E, U>::new, arg);
	}

	/**
	 * @param <U> The type of the first other observable to tuplize
	 * @param <V> The type of the second other observable to tuplize
	 * @param arg1 The first other observable to tuplize
	 * @param arg2 The second other observable to tuplize
	 * @return An observable which broadcasts tuples of the latest values of this observable value and 2 others
	 */
	@Override
	default <U, V> ObservableOrderedElement<TriTuple<E, U, V>> tupleV(ObservableValue<U> arg1, ObservableValue<V> arg2) {
		return combineV(TriTuple<E, U, V>::new, arg1, arg2);
	}

	/**
	 * Composes this observable into another observable that depends on this one and two others
	 *
	 * @param <U> The type of the first other argument observable
	 * @param <V> The type of the second other argument observable
	 * @param <R> The type of the new observable
	 * @param function The function to apply to the values of the observables
	 * @param arg2 The first other observable to be composed
	 * @param arg3 The second other observable to be composed
	 * @return The new observable whose value is a function of this observable's value and the others'
	 */
	@Override
	default <U, V, R> ObservableOrderedElement<R> combineV(TriFunction<? super E, ? super U, ? super V, R> function, ObservableValue<U> arg2,
			ObservableValue<V> arg3) {
		return combineV(null, function, arg2, arg3, false);
	}

	/**
	 * Composes this observable into another observable that depends on this one and two others
	 *
	 * @param <U> The type of the first other argument observable
	 * @param <V> The type of the second other argument observable
	 * @param <R> The type of the new observable
	 * @param type The run-time type of the new observable
	 * @param function The function to apply to the values of the observables
	 * @param arg2 The first other observable to be composed
	 * @param arg3 The second other observable to be composed
	 * @return The new observable whose value is a function of this observable's value and the others'
	 */
	@Override
	default <U, V, R> ObservableOrderedElement<R> combineV(TypeToken<R> type, TriFunction<? super E, ? super U, ? super V, R> function,
			ObservableValue<U> arg2, ObservableValue<V> arg3, boolean combineNull) {
		return new ComposedObservableListElement<>(this, type, args -> {
			return function.apply((E) args[0], (U) args[1], (V) args[2]);
		}, combineNull, this, arg2, arg3);
	}

	@Override
	default ObservableOrderedElement<E> refresh(Observable<?> refresh) {
		return new RefreshingOrderedObservableElement<>(this, refresh);
	}

	@Override
	default ObservableOrderedElement<E> refreshForValue(Function<? super E, Observable<?>> refresh, Observable<Void> unsubscribe) {
		return d().debug(new ValueRefreshingOrderedObservableElement<>(this, refresh, unsubscribe)).from("refresh", this)
				.using("on", refresh).get();
	}

	/**
	 * Implements {@link ObservableElement#takeUntil(Observable)}
	 *
	 * @param <T> The type of the element value
	 */
	class OrderedElementTakenUntil<T> extends ObservableElementTakenUntil<T> implements ObservableOrderedElement<T> {
		public OrderedElementTakenUntil(ObservableOrderedElement<T> wrap, Observable<?> until, boolean withCompletion) {
			super(wrap, until, withCompletion);
		}

		@Override
		protected ObservableOrderedElement<T> getWrapped() {
			return (ObservableOrderedElement<T>) super.getWrapped();
		}

		@Override
		public int getIndex() {
			return getWrapped().getIndex();
		}
	}

	/**
	 * Implements {@link #cached()}
	 *
	 * @param <E> The type of the value
	 */
	class CachedOrderedObservableElement<E> extends CachedObservableElement<E> implements ObservableOrderedElement<E> {
		protected CachedOrderedObservableElement(ObservableOrderedElement<E> wrapped) {
			super(wrapped);
		}

		@Override
		protected ObservableOrderedElement<E> getWrapped() {
			return (ObservableOrderedElement<E>) super.getWrapped();
		}

		@Override
		public ObservableOrderedElement<E> cached() {
			return this;
		}

		@Override
		public int getIndex() {
			return getWrapped().getIndex();
		}
	}

	/** @param <T> The type of the element */
	class ComposedObservableListElement<T> extends ComposedObservableValue<T> implements ObservableOrderedElement<T> {
		private final ObservableOrderedElement<?> theRoot;

		public ComposedObservableListElement(ObservableOrderedElement<?> root, TypeToken<T> t, Function<Object [], T> f,
				boolean combineNull,
				ObservableValue<?>... composed) {
			super(t, f, combineNull, composed);
			theRoot = root;
		}

		@Override
		public ObservableValue<T> persistent() {
			ObservableValue<?> [] composed = getComposed();
			composed[0] = theRoot.persistent();
			return new ComposedObservableValue<>(getType(), getFunction(), isNullCombined(), composed);
		}

		@Override
		public int getIndex() {
			return theRoot.getIndex();
		}
	}

	/**
	 * Implements {@link ObservableOrderedElement#refresh(Observable)}
	 *
	 * @param <E> The type of the element
	 */
	class RefreshingOrderedObservableElement<E> extends RefreshingObservableElement<E> implements ObservableOrderedElement<E> {
		protected RefreshingOrderedObservableElement(ObservableOrderedElement<E> wrap, Observable<?> refresh) {
			super(wrap, refresh);
		}

		@Override
		protected ObservableOrderedElement<E> getWrapped() {
			return (ObservableOrderedElement<E>) super.getWrapped();
		}

		@Override
		public int getIndex() {
			return getWrapped().getIndex();
		}
	}

	/**
	 * Implements {@link ObservableOrderedElement#refreshForValue(Function, Observable)}
	 *
	 * @param <E> The type of the element
	 */
	class ValueRefreshingOrderedObservableElement<E> extends ValueRefreshingObservableElement<E> implements ObservableOrderedElement<E> {
		protected ValueRefreshingOrderedObservableElement(ObservableOrderedElement<E> wrap, Function<? super E, Observable<?>> refresh,
				Observable<Void> unsubscribe) {
			super(wrap, refresh, unsubscribe);
		}

		@Override
		protected ObservableOrderedElement<E> getWrapped() {
			return (ObservableOrderedElement<E>) super.getWrapped();
		}

		@Override
		public int getIndex() {
			return getWrapped().getIndex();
		}
	}
}