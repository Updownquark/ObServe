package org.observe.collect;

import static org.observe.ObservableDebug.debug;

import java.util.function.BiFunction;
import java.util.function.Function;

import org.observe.*;

import prisms.lang.Type;

/**
 * An observable wrapper around an element in a {@link ObservableCollection}. This observable will call its observers'
 * {@link Observer#onCompleted(Object)} method when the element is removed from the collection. This element will also complete when the
 * subscription used to subscribe to the outer collection is {@link Subscription#unsubscribe() unsubscribed}.
 *
 * @param <E> The type of the element
 */
public interface ObservableElement<E> extends ObservableValue<E> {
	/** @return An observable value that keeps reporting updates after the subscription to the parent collection is unsubscribed */
	ObservableValue<E> persistent();

	@Override
	default ObservableElement<E> cached() {
		return debug(new CachedObservableElement<>(this)).from("cached", this).get();
	}

	@Override
	default <R> ObservableElement<R> mapV(Function<? super E, R> function) {
		return mapV(null, function, false);
	};

	@Override
	default <R> ObservableElement<R> mapV(Type type, Function<? super E, R> function, boolean combineNull) {
		return debug(new ComposedObservableElement<R>(this, type, args -> {
			return function.apply((E) args[0]);
		}, combineNull, this)).from("map", this).using("map", function).tag("combineNull", combineNull).get();
	};

	@Override
	default <U, R> ObservableElement<R> combineV(BiFunction<? super E, ? super U, R> function, ObservableValue<U> arg) {
		return combineV(null, function, arg, false);
	}

	@Override
	default <U, R> ObservableElement<R> combineV(Type type, BiFunction<? super E, ? super U, R> function, ObservableValue<U> arg,
		boolean combineNull) {
		return debug(new ComposedObservableElement<R>(this, type, args -> {
			return function.apply((E) args[0], (U) args[1]);
		}, combineNull, this, arg)).from("combine", this).from("with", arg).using("combination", function).tag("combineNull", combineNull)
		.get();
	}

	@Override
	default <U> ObservableElement<BiTuple<E, U>> tupleV(ObservableValue<U> arg) {
		return combineV(BiTuple<E, U>::new, arg);
	}

	@Override
	default <U, V> ObservableElement<TriTuple<E, U, V>> tupleV(ObservableValue<U> arg1, ObservableValue<V> arg2) {
		return combineV(TriTuple<E, U, V>::new, arg1, arg2);
	}

	@Override
	default <U, V, R> ObservableElement<R> combineV(TriFunction<? super E, ? super U, ? super V, R> function, ObservableValue<U> arg2,
		ObservableValue<V> arg3) {
		return combineV(null, function, arg2, arg3, false);
	}

	@Override
	default <U, V, R> ObservableElement<R> combineV(Type type, TriFunction<? super E, ? super U, ? super V, R> function,
		ObservableValue<U> arg2, ObservableValue<V> arg3, boolean combineNull) {
		return debug(new ComposedObservableElement<R>(this, type, args -> {
			return function.apply((E) args[0], (U) args[1], (V) args[2]);
		}, combineNull, this, arg2, arg3)).from("combine", this).from("with", arg2, arg3).using("combination", function)
		.tag("combineNull", combineNull).get();
	}

	@Override
	default ObservableElement<E> refresh(Observable<?> observable) {
		ObservableElement<E> outer = this;
		return debug(new ObservableElement<E>() {
			@Override
			public Type getType() {
				return outer.getType();
			}

			@Override
			public E get() {
				return outer.get();
			}

			@Override
			public ObservableValue<E> persistent() {
				return outer.persistent().refresh(observable);
			}

			@Override
			public Runnable observe(Observer<? super ObservableValueEvent<E>> observer) {
				Runnable outerSub = outer.observe(observer);
				Runnable refireSub = observable.observe(new Observer<Object>() {
					@Override
					public <V> void onNext(V value) {
						ObservableValueEvent<E> event2 = outer.createEvent(outer.get(), outer.get(), value);
						observer.onNext(event2);
					}
				});
				return () -> {
					outerSub.run();
					refireSub.run();
				};
			}

			@Override
			public String toString() {
				return outer + ".refresh(" + observable + ")";
			}
		}).from("refresh", this).from("on", observable).get();
	}

	/**
	 * @param observable A function providing an observable to refire on as a function of a value
	 * @return An observable element that refires its value when the observable returned by the given function fires
	 */
	default ObservableElement<E> refreshForValue(Function<? super E, Observable<?>> observable) {
		ObservableElement<E> outer = this;
		return debug(new ObservableElement<E>() {
			@Override
			public Type getType() {
				return outer.getType();
			}

			@Override
			public E get() {
				return outer.get();
			}

			@Override
			public ObservableValue<E> persistent() {
				return outer.persistent().refresh(observable.apply(get()));
			}

			@Override
			public Runnable observe(Observer<? super ObservableValueEvent<E>> observer) {
				Runnable [] refireSub = new Runnable[1];
				Observer<Object> refireObs = new Observer<Object>() {
					@Override
					public <V> void onNext(V value) {
						E outerVal = get();
						ObservableValueEvent<E> event2 = outer.createEvent(outerVal, outerVal, value);
						observer.onNext(event2);
					}

					@Override
					public <V> void onCompleted(V value) {
						E outerVal = get();
						ObservableValueEvent<E> event2 = outer.createEvent(outerVal, outerVal, value);
						observer.onNext(event2);
						refireSub[0] = null;
					}
				};
				Runnable outerSub = outer.observe(new Observer<ObservableValueEvent<E>>() {
					@Override
					public <V extends ObservableValueEvent<E>> void onNext(V value) {
						refireSub[0] = observable.apply(value.getValue()).noInit().takeUntil(outer).observe(refireObs);
						observer.onNext(value);
					}

					@Override
					public <V extends ObservableValueEvent<E>> void onCompleted(V value) {
						if(refireSub[0] != null)
							refireSub[0].run();
						observer.onCompleted(value);
					}

					@Override
					public void onError(Throwable e) {
						observer.onError(e);
					}
				});
				refireSub[0] = observable.apply(outer.get()).noInit().takeUntil(outer).observe(refireObs);
				return () -> {
					outerSub.run();
					if(refireSub[0] != null)
						refireSub[0].run();
				};
			}

			@Override
			public String toString() {
				return outer + ".refireWhen(" + observable + ")";
			}
		}).from("refresh", this).using("on", observable).get();
	}

	/**
	 * Implements {@link #cached()}
	 * 
	 * @param <T> The type of the value
	 */
	class CachedObservableElement<T> extends CachedObservableValue<T> implements ObservableElement<T> {
		public CachedObservableElement(ObservableElement<T> wrapped) {
			super(wrapped);
		}

		@Override
		public ObservableValue<T> persistent() {
			return getWrapped().persistent();
		}

		@Override
		protected ObservableElement<T> getWrapped() {
			return (ObservableElement<T>) super.getWrapped();
		}

		@Override
		public ObservableElement<T> cached() {
			return this;
		}
	}

	/** @param <T> The type of the element */
	class ComposedObservableElement<T> extends ComposedObservableValue<T> implements ObservableElement<T> {
		private final ObservableElement<?> theRoot;

		public ComposedObservableElement(ObservableElement<?> root, Type t, Function<Object [], T> f, boolean combineNull,
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
	}
}
