package org.observe.collect;

import static org.observe.ObservableDebug.d;

import java.util.concurrent.locks.Lock;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.observe.Action;
import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.SimpleObservable;
import org.observe.Subscription;
import org.observe.util.ObservableUtils;
import org.qommons.BiTuple;
import org.qommons.TriFunction;
import org.qommons.TriTuple;

import com.google.common.reflect.TypeToken;

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
	default ObservableElement<E> takeUntil(Observable<?> until) {
		return d().debug(new ObservableElementTakenUntil<>(this, until, true)).from("take", this).from("until", until)
				.tag("terminate", true).get();
	}

	@Override
	default ObservableElement<E> unsubscribeOn(Observable<?> until) {
		return d().debug(new ObservableElementTakenUntil<>(this, until, false)).from("take", this).from("until", until)
				.tag("terminate", false).get();
	}

	@Override
	default ObservableElement<E> cached() {
		return d().debug(new CachedObservableElement<>(this)).from("cached", this).get();
	}

	@Override
	default <R> ObservableElement<R> mapV(Function<? super E, R> function) {
		return mapV(null, function, false);
	};

	@Override
	default <R> ObservableElement<R> mapV(TypeToken<R> type, Function<? super E, R> function, boolean combineNull) {
		ComposedObservableElement<R> ret = new ComposedObservableElement<>(this, type, args -> {
			return function.apply((E) args[0]);
		} , combineNull, this);
		return d().debug(ret).from("map", this).using("map", function).tag("combineNull", combineNull).get();
	};

	@Override
	default <U, R> ObservableElement<R> combineV(BiFunction<? super E, ? super U, R> function, ObservableValue<U> arg) {
		return combineV(null, function, arg, false);
	}

	@Override
	default <U, R> ObservableElement<R> combineV(TypeToken<R> type, BiFunction<? super E, ? super U, R> function, ObservableValue<U> arg,
			boolean combineNull) {
		ComposedObservableElement<R> ret = new ComposedObservableElement<>(this, type, args -> {
			return function.apply((E) args[0], (U) args[1]);
		} , combineNull, this, arg);
		return d().debug(ret).from("combine", this).from("with", arg).using("combination", function).tag("combineNull", combineNull)
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
	default <U, V, R> ObservableElement<R> combineV(TypeToken<R> type, TriFunction<? super E, ? super U, ? super V, R> function,
			ObservableValue<U> arg2, ObservableValue<V> arg3, boolean combineNull) {
		ComposedObservableElement<R> ret = new ComposedObservableElement<>(this, type, args -> {
			return function.apply((E) args[0], (U) args[1], (V) args[2]);
		} , combineNull, this, arg2, arg3);
		return d().debug(ret).from("combine", this).from("with", arg2, arg3).using("combination", function)
				.tag("combineNull", combineNull).get();
	}

	@Override
	default ObservableElement<E> refresh(Observable<?> refresh) {
		return d().debug(new RefreshingObservableElement<>(this, refresh)).from("refresh", this).from("on", refresh).get();
	}

	/**
	 * @param refresh A function providing an observable to refresh on as a function of a value
	 * @param unsubscribe An observable that fires when this element's collection subscription is unsubscribed
	 * @return An observable element that refires its value when the observable returned by the given function fires
	 */
	default ObservableElement<E> refreshForValue(Function<? super E, Observable<?>> refresh, Observable<Void> unsubscribe) {
		return d().debug(new ValueRefreshingObservableElement<>(this, refresh, unsubscribe)).from("refresh", this).using("on", refresh)
				.get();
	}

	@Override
	default ObservableElement<E> safe() {
		return d().debug(new SafeObservableElement<>(this, null)).from("safe", this).get();
	}

	@Override
	default ObservableElement<E> safe(Lock lock) {
		return d().debug(new SafeObservableElement<>(this, lock)).from("safe", this).using("lock", lock).get();
	}

	/**
	 * @param elementValue An observable value containing an observable element
	 * @return An element representing the value in the nested element
	 */
	public static <E> ObservableElement<E> flatten(ObservableValue<? extends ObservableElement<E>> elementValue) {
		return new FlattenedElementValue<>(elementValue);
	}

	/**
	 * Implements {@link ObservableElement#takeUntil(Observable)}
	 *
	 * @param <T> The type of the element value
	 */
	class ObservableElementTakenUntil<T> extends ObservableValueTakenUntil<T> implements ObservableElement<T> {
		public ObservableElementTakenUntil(ObservableElement<T> wrap, Observable<?> until, boolean terminate) {
			super(wrap, until, terminate);
		}

		@Override
		protected ObservableElement<T> getWrapped() {
			return (ObservableElement<T>) super.getWrapped();
		}

		@Override
		public ObservableValue<T> persistent() {
			return getWrapped().persistent();
		}
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

		public ComposedObservableElement(ObservableElement<?> root, TypeToken<T> t, Function<Object [], T> f, boolean combineNull,
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

	/**
	 * Implements {@link ObservableElement#refresh(Observable)}
	 *
	 * @param <E> The type of the element
	 */
	class RefreshingObservableElement<E> extends ObservableValue.RefreshingObservableValue<E> implements ObservableElement<E> {
		protected RefreshingObservableElement(ObservableElement<E> wrap, Observable<?> refresh) {
			super(wrap, refresh);
		}

		@Override
		protected ObservableElement<E> getWrapped() {
			return (ObservableElement<E>) super.getWrapped();
		}

		@Override
		public ObservableValue<E> persistent() {
			return getWrapped().persistent().refresh(getRefresh());
		}
	}

	/**
	 * Implements {@link ObservableElement#refreshForValue(Function, Observable)}
	 *
	 * @param <E> The type of the element
	 */
	class ValueRefreshingObservableElement<E> implements ObservableElement<E> {
		private final ObservableElement<E> theWrapped;

		private final Function<? super E, Observable<?>> theRefresh;

		private final Observable<Void> theUnsubscribe;

		protected ValueRefreshingObservableElement(ObservableElement<E> wrap, Function<? super E, Observable<?>> refresh,
				Observable<Void> unsubscribe) {
			theWrapped = wrap;
			theRefresh = refresh;
			theUnsubscribe = unsubscribe;
		}

		protected ObservableElement<E> getWrapped() {
			return theWrapped;
		}

		protected Function<? super E, Observable<?>> getRefresh() {
			return theRefresh;
		}

		@Override
		public TypeToken<E> getType() {
			return theWrapped.getType();
		}

		@Override
		public E get() {
			return theWrapped.get();
		}

		@Override
		public ObservableValue<E> persistent() {
			return theWrapped.persistent().refresh(theRefresh.apply(get()));
		}

		@Override
		public Subscription subscribe(Observer<? super ObservableValueEvent<E>> observer) {
			Subscription [] refireSub = new Subscription[1];
			Action<Object> refireObs = value -> {
				E outerVal = get();
				ObservableValueEvent<E> event2 = theWrapped.createChangeEvent(outerVal, outerVal, value);
				observer.onNext(event2);
			};
			Subscription outerSub = theWrapped.subscribe(new Observer<ObservableValueEvent<E>>() {
				@Override
				public <V extends ObservableValueEvent<E>> void onNext(V value) {
					refireSub[0] = theRefresh.apply(value.getValue()).noInit().takeUntil(ObservableUtils.makeUntil(theWrapped, value))
							.takeUntil(theUnsubscribe).act(refireObs);
					observer.onNext(value);
				}

				@Override
				public <V extends ObservableValueEvent<E>> void onCompleted(V value) {
					if(refireSub[0] != null)
						refireSub[0].unsubscribe();
					refireSub[0] = null;
					observer.onCompleted(value);
				}

				@Override
				public void onError(Throwable e) {
					observer.onError(e);
				}
			});
			return () -> {
				outerSub.unsubscribe();
				if(refireSub[0] != null)
					refireSub[0].unsubscribe();
			};
		}

		@Override
		public String toString() {
			return theWrapped + ".refireWhen(" + theRefresh + ")";
		}
	}

	/**
	 * Implements {@link ObservableElement#safe()}
	 *
	 * @param <E> The type of value in the element
	 */
	class SafeObservableElement<E> extends SafeObservableValue<E> implements ObservableElement<E> {
		public SafeObservableElement(ObservableElement<E> wrap, Lock lock) {
			super(wrap, lock);
		}

		@Override
		protected ObservableElement<E> getWrapped() {
			return (ObservableElement<E>) super.getWrapped();
		}

		@Override
		public ObservableValue<E> persistent() {
			return getWrapped().persistent().safe(getLock());
		}

		@Override
		public ObservableElement<E> safe() {
			return this;
		}

		@Override
		public ObservableElement<E> safe(Lock lock) {
			if (getLock() == lock)
				return this;
			else
				return ObservableElement.super.safe(lock);
		}
	}

	/**
	 * Implements {@link ObservableElement#flatten(ObservableValue)}
	 * @param <E> The type of value in the element
	 */
	class FlattenedElementValue<E> implements ObservableElement<E> {
		private final ObservableValue<? extends ObservableElement<E>> theValue;
		private final TypeToken<E> theType;

		public FlattenedElementValue(ObservableValue<? extends ObservableElement<E>> value) {
			theValue = value;
			theType = (TypeToken<E>) theValue.getType().resolveType(ObservableElement.class.getTypeParameters()[0]);
		}

		protected ObservableValue<? extends ObservableElement<E>> getValue() {
			return theValue;
		}

		@Override
		public TypeToken<E> getType() {
			return theType;
		}

		@Override
		public E get() {
			return get(theValue.get());
		}

		protected E get(ObservableElement<E> el) {
			return el == null ? null : el.get();
		}

		@Override
		public Subscription subscribe(Observer<? super ObservableValueEvent<E>> observer) {
			SimpleObservable<Void> completeObs = new SimpleObservable<>();
			return theValue.unsubscribeOn(completeObs).subscribe(new Observer<ObservableValueEvent<? extends ObservableElement<E>>>() {
				E preValue;
				boolean firedInitial;
				int theSwitchCount = 0;

				@Override
				public <V extends ObservableValueEvent<? extends ObservableElement<E>>> void onNext(V event) {
					theSwitchCount++;
					if (event.getValue() == null) {
						if (firedInitial)
							observer.onCompleted(createChangeEvent(preValue, preValue, event.getCause()));
						completeObs.onNext(null);
						return;
					}
					event.getValue().unsubscribeOn(ObservableUtils.makeUntil(theValue, event))
					.subscribe(new Observer<ObservableValueEvent<E>>() {
						private final int theSwitchTrack = theSwitchCount;
						@Override
						public <V2 extends ObservableValueEvent<E>> void onNext(V2 event2) {
							if (theSwitchTrack != theSwitchCount)
								return;
							if (!firedInitial) {
								firedInitial = true;
								observer.onNext(createInitialEvent(event2.getValue()));
							} else
								observer.onNext(createChangeEvent(preValue, event2.getValue(), event2.getCause()));
							preValue = event2.getValue();
						}

						@Override
						public <V2 extends ObservableValueEvent<E>> void onCompleted(V2 event2) {
							if (theSwitchTrack != theSwitchCount)
								return;
							completeObs.onNext(null);
							observer.onCompleted(createChangeEvent(preValue, preValue, event2.getCause()));
						}
					});
				}

				@Override
				public <V extends ObservableValueEvent<? extends ObservableElement<E>>> void onCompleted(V event) {
					theSwitchCount++;
					if (firedInitial)
						observer.onCompleted(createChangeEvent(preValue, preValue, event.getCause()));
				}
			});
		}

		@Override
		public ObservableValue<E> persistent() {
			ObservableElement<E> el = theValue.get();
			return el == null ? ObservableValue.constant(theType, null) : el.persistent();
		}
	}
}
