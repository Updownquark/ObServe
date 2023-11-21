package org.observe;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.qommons.Causable;
import org.qommons.Identifiable;
import org.qommons.LambdaUtils;
import org.qommons.Lockable;
import org.qommons.QommonsUtils;
import org.qommons.StringUtils;
import org.qommons.ThreadConstrained;
import org.qommons.ThreadConstraint;
import org.qommons.TimeUtils;
import org.qommons.Transaction;
import org.qommons.collect.ListenerList;
import org.qommons.threading.QommonsTimer;

/**
 * A stream of values that can be filtered, mapped, composed, etc. and evaluated on
 *
 * @param <T> The type of values this observable provides
 */
public interface Observable<T> extends Lockable, Identifiable, Eventable {
	/**
	 * Subscribes to this observable such that the given observer will be notified of any new values on this observable.
	 *
	 * @param observer The observer to be notified when new values are available from this observable
	 * @return A subscription that, when invoked, will cease notifications to the observer
	 */
	Subscription subscribe(Observer<? super T> observer);

	/**
	 * @param action The action to perform for each new value
	 * @return The subscription for the action
	 */
	default Subscription act(Consumer<? super T> action) {
		return subscribe(new Observer<T>() {
			@Override
			public <V extends T> void onNext(V value) {
				action.accept(value);
			}

			@Override
			public void onCompleted(Causable cause) {
			}

			@Override
			public String toString() {
				return action.toString();
			}
		});
	}

	/** @return An observable that will fire once when this observable completes */
	default Observable<Causable> completed() {
		return new CompletionObservable<>(this);
	}

	/**
	 * @return An observable that returns the same values as this one except that any initialization events (for cold observables) will be
	 *         ignored.
	 */
	default Observable<T> noInit() {
		return new NoInitObservable<>(this);
	}

	/**
	 * @param func The filter function
	 * @return An observable that provides the same values as this observable minus those that the filter function returns false for
	 */
	default Observable<T> filter(Function<? super T, Boolean> func) {
		return filterMap(LambdaUtils.printableFn(value -> (value != null && func.apply(value)) ? value : null, func::toString, func));
	}

	/**
	 * @param func The filter function
	 * @return An observable that provides the same values as this observable minus those that the filter function returns false for
	 */
	default Observable<T> filterP(Predicate<? super T> func) {
		return filterMap(LambdaUtils.printableFn(value -> func.test(value) ? value : null, func::toString, func));
	}

	/**
	 * @param type The type of values to filter
	 * @return An observable that only fires values from this observable that are an instance of the given type
	 */
	default <X> Observable<X> filter(Class<X> type) {
		return filterMap(
			LambdaUtils.printableFn(v -> type.isInstance(type) ? type.cast(v) : null, () -> "instanceof " + type.getName(), null));
	}

	/**
	 * @param <R> The type of the returned observable
	 * @param func The map function
	 * @return An observable that provides the values of this observable, mapped by the given function
	 */
	default <R> Observable<R> map(Function<? super T, R> func) {
		return new ComposedObservable<>(LambdaUtils.printableFn(args -> func.apply((T) args[0]), func::toString, func), "map", this);
	}

	/**
	 * A shortcut for {@link #flatten(Observable) flatten}({@link #map(Function) map}(map))
	 *
	 * @param map The function producing an observable for each value from this observable
	 * @return An observable that may produce any number of values for each value from this observable
	 */
	default <R> Observable<R> flatMap(Function<? super T, ? extends Observable<? extends R>> map) {
		return flatten(map(map));
	}

	/**
	 * @param <R> The type of the returned observable
	 * @param func The filter map function
	 * @return An observable that provides the values of this observable, mapped by the given function, except where that function returns
	 *         null
	 */
	default <R> Observable<R> filterMap(Function<? super T, R> func) {
		return new FilteredObservable<>(this, func);
	}

	/**
	 * @param <V> The type of the other observable to be combined with this one
	 * @param <R> The type of the returned observable
	 * @param other The other observable to compose
	 * @param func The function to use to combine the observables' values
	 * @return A new observable whose values are the specified combination of this observable and the others'
	 */
	default <V, R> Observable<R> combine(Observable<V> other, BiFunction<? super T, ? super V, R> func) {
		return new ComposedObservable<>(LambdaUtils.printableFn(args -> func.apply((T) args[0], (V) args[1]), func::toString, func),
			"combine", this, other);
	}

	/**
	 * @param until The observable to watch
	 * @return An observable that provides the same values as this observable until the first value is observed from the given observable
	 */
	default Observable<T> takeUntil(Observable<?> until) {
		return new ObservableTakenUntil<>(this, until, true);
	}

	/**
	 * A different form of {@link #takeUntil(Observable)} that does not complete the observable when <code>until</code> fires, but merely
	 * unsubscribes all subscriptions
	 *
	 * @param until the observable to watch
	 * @return An observable that provides the same values as this observable until the first value is observed from the given observable
	 */
	default Observable<T> unsubscribeOn(Observable<?> until) {
		return new ObservableTakenUntil<>(this, until, false);
	}

	/**
	 * @param times The number of values to take from this observable
	 * @return An observable that provides the same values as this observable but completes after the given number of values
	 */
	default Observable<T> take(int times) {
		return new ObservableTakenTimes<>(this, times);
	}

	/**
	 * @param times The number of values to skip from this observable
	 * @return An observable that provides the same values as this observable but ignores the first {@code times} values
	 */
	default Observable<T> skip(int times) {
		return skip(() -> times);
	}

	/**
	 * Like {@link #skip(int)}, but the number of times to skip is retrieved when the observable is subscribed to instead of when it is
	 * created.
	 *
	 * @param times A supplier that returns the number of values to skip from this observable.
	 * @return An observable that provides the same values as this observable but ignores the first {@code times} values
	 */
	default Observable<T> skip(java.util.function.Supplier<Integer> times) {
		return new SkippingObservable<>(this, times);
	}

	/** @return Whether this observable is thread-safe, meaning it is constrained to only fire values on a single thread at a time */
	boolean isSafe();

	@Override
	default boolean isLockSupported() {
		return isSafe();
	}

	/**
	 * Prevents this observable from firing while the lock is held. The lock is not exclusive.
	 *
	 * @return The transaction to close to release the lock
	 */
	@Override
	Transaction lock();

	@Override
	Transaction tryLock();

	/**
	 * @param threading The thread constraint for the new observable to obey
	 * @return An observable that fires the same values as this one, but on the given thread
	 */
	default Observable<T> safe(ThreadConstraint threading) {
		if (getThreadConstraint() == threading || getThreadConstraint() == ThreadConstraint.NONE)
			return this;
		return new SafeObservable<>(this, threading);
	}

	/**
	 * @param <V> The super-type of all observables to or
	 * @param obs The observables to combine
	 * @return An observable that pushes a value each time any of the given observables pushes a value
	 */
	public static <V> Observable<V> or(Observable<? extends V>... obs) {
		return new OrObservable<>(Arrays.asList(obs));
	}

	/**
	 * @param obs The observable to watch
	 * @return An observable that fires when the source's root causable finishes.
	 * @see Causable#getRootCausable()
	 * @see Causable#onFinish(org.qommons.Causable.CausableKey)
	 */
	public static Observable<Causable> onRootFinish(Observable<? extends Causable> obs) {
		return new CausableRootFinish((Observable<Causable>) obs);
	}

	/**
	 * @param <T> The type of the observable to create
	 * @param value The value for the observable
	 * @return An observable that pushes the given value as soon as it is subscribed to and never completes
	 */
	public static <T> Observable<T> constant(T value) {
		return new Observable<T>() {
			private Object theIdentity;
			@Override
			public Object getIdentity() {
				if (theIdentity == null)
					theIdentity = Identifiable.idFor(value, () -> String.valueOf(value), () -> Objects.hashCode(value),
						other -> Objects.equals(value, other));
				return theIdentity;
			}

			@Override
			public Subscription subscribe(Observer<? super T> observer) {
				observer.onNext(value);
				return Subscription.NONE;
			}

			@Override
			public ThreadConstraint getThreadConstraint() {
				return ThreadConstraint.NONE;
			}

			@Override
			public boolean isEventing() {
				return false;
			}

			@Override
			public boolean isSafe() {
				return true;
			}

			@Override
			public Transaction lock() {
				return Transaction.NONE;
			}

			@Override
			public Transaction tryLock() {
				return Transaction.NONE;
			}

			@Override
			public CoreId getCoreId() {
				return CoreId.EMPTY;
			}

			@Override
			public String toString() {
				return "" + value;
			}
		};
	}

	/**
	 * @param ov An observable of observables
	 * @return An observable reflecting the values of the inner observables
	 */
	public static <T> Observable<T> flatten(Observable<? extends Observable<? extends T>> ov) {
		return new FlattenedObservable<>(ov);
	}

	/** An empty observable that never does anything */
	public static Observable<?> empty = new Observable<Object>() {
		private final Object theIdentity = Identifiable.baseId("empty", this);

		@Override
		public Object getIdentity() {
			return theIdentity;
		}

		@Override
		public Subscription subscribe(Observer<? super Object> observer) {
			try (Causable.CausableInUse cause = Causable.cause()) {
				observer.onCompleted(cause);
			}
			return Subscription.NONE;
		}

		@Override
		public Observable<Object> noInit() {
			return this;
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return ThreadConstraint.NONE;
		}

		@Override
		public boolean isEventing() {
			return false;
		}

		@Override
		public boolean isSafe() {
			return true;
		}

		@Override
		public Transaction lock() {
			return Transaction.NONE;
		}

		@Override
		public Transaction tryLock() {
			return Transaction.NONE;
		}

		@Override
		public CoreId getCoreId() {
			return CoreId.EMPTY;
		}

		@Override
		public Observable<Object> filter(Function<? super Object, Boolean> func) {
			return this;
		}

		@Override
		public <X> Observable<X> filter(Class<X> type) {
			return (Observable<X>) this;
		}

		@Override
		public <R> Observable<R> map(Function<? super Object, R> func) {
			return (Observable<R>) this;
		}

		@Override
		public <R> Observable<R> flatMap(Function<? super Object, ? extends Observable<? extends R>> map) {
			return (Observable<R>) this;
		}

		@Override
		public <R> Observable<R> filterMap(Function<? super Object, R> func) {
			return (Observable<R>) this;
		}

		@Override
		public Observable<Object> takeUntil(Observable<?> until) {
			return this;
		}

		@Override
		public Observable<Object> unsubscribeOn(Observable<?> until) {
			return this;
		}

		@Override
		public Observable<Object> take(int times) {
			return this;
		}

		@Override
		public Observable<Object> skip(int times) {
			return this;
		}

		@Override
		public Observable<Object> skip(Supplier<Integer> times) {
			return this;
		}

		@Override
		public Observable<Object> safe(ThreadConstraint threading) {
			return this;
		}

		@Override
		public String toString() {
			return "empty";
		}
	};

	/**
	 * @param <T> The type of the observable
	 * @return An observable that never does anything
	 */
	static <T> Observable<T> empty() {
		return (Observable<T>) empty;
	}

	/**
	 * @return An observable that fires (a null value) both for {@link Observer#onNext(Object) onNext} and
	 *         {@link Observer#onCompleted(Causable) onCompleted} as the Java VM is shutting down
	 */
	static Observable<Void> onVmShutdown() {
		return new VmShutdownObservable();
	}

	/**
	 * @param initDelay The initial delay before firing the first value
	 * @param interval The interval at which to fire values
	 * @param until The duration after which values will stop being fired (and an {@link Observer#onCompleted(Causable) onCompleted} event
	 *        will be fired)
	 * @param value The function to produce values for the observable
	 * @param dispose An action to be taken on each generated value after it is used by all listeners (e.g. {@link AutoCloseable#close()})
	 * @return The (configurable) observable
	 */
	static <T> IntervalObservable<T> every(Duration initDelay, Duration interval, Duration until,
		Function<? super Duration, ? extends T> value, Consumer<? super T> dispose) {
		return new IntervalObservable<>(QommonsTimer.getCommonInstance(), initDelay, interval, until, value, dispose);
	}

	/**
	 * An abstract class that handles some code needed for wrapping a single observable to produce another
	 *
	 * @param <F> The type of wrapped observable
	 * @param <T> The type of this observable
	 */
	abstract class WrappingObservable<F, T> extends AbstractIdentifiable implements Observable<T> {
		protected final Observable<F> theWrapped;

		public WrappingObservable(Observable<F> wrapped) {
			theWrapped = wrapped;
		}

		protected Observable<F> getWrapped() {
			return theWrapped;
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return theWrapped.getThreadConstraint();
		}

		@Override
		public boolean isEventing() {
			return theWrapped.isEventing();
		}

		@Override
		public boolean isSafe() {
			return theWrapped.isSafe();
		}

		@Override
		public Transaction lock() {
			return theWrapped.lock();
		}

		@Override
		public Transaction tryLock() {
			return theWrapped.tryLock();
		}

		@Override
		public CoreId getCoreId() {
			return theWrapped.getCoreId();
		}
	}

	/**
	 * Implements {@link Observable#completed()}
	 *
	 * @param <T> The type of the source observable
	 */
	class CompletionObservable<T> extends WrappingObservable<T, Causable> {
		public CompletionObservable(Observable<T> wrapped) {
			super(wrapped);
		}

		@Override
		public Subscription subscribe(Observer<? super Causable> observer) {
			class CompleteObserver implements Observer<T> {
				private final Observer<? super Causable> wrapped;

				CompleteObserver(Observer<? super Causable> wrap) {
					wrapped = wrap;
				}

				@Override
				public <V extends T> void onNext(V value) {
				}

				@Override
				public void onCompleted(Causable cause) {
					wrapped.onNext(cause);
					wrapped.onCompleted(cause);
				}
			}
			return getWrapped().subscribe(new CompleteObserver(observer));
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(getWrapped().getIdentity(), "completed");
		}
	}

	/**
	 * Implements {@link Observable#noInit()}
	 *
	 * @param <T> The type of the observable
	 */
	class NoInitObservable<T> extends WrappingObservable<T, T> {
		protected NoInitObservable(Observable<T> wrap) {
			super(wrap);
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(getWrapped().getIdentity(), "noInit");
		}

		@Override
		public Subscription subscribe(Observer<? super T> observer) {
			boolean[] initialized = new boolean[1];
			Subscription ret = theWrapped.subscribe(new Observer<T>() {
				@Override
				public <V extends T> void onNext(V value) {
					if (initialized[0])
						observer.onNext(value);
				}

				@Override
				public void onCompleted(Causable cause) {
					observer.onCompleted(cause);
				}
			});
			initialized[0] = true;
			return ret;
		}
	}

	/**
	 * Implements {@link Observable#filterMap(Function)}
	 *
	 * @param <T> The type of the observable to filter-map
	 * @param <R> The type of the mapped observable
	 */
	class FilteredObservable<T, R> extends WrappingObservable<T, R> {
		private final Function<? super T, R> theMap;

		protected FilteredObservable(Observable<T> wrap, Function<? super T, R> map) {
			super(wrap);
			theMap = map;
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(getWrapped().getIdentity(), "map", theMap);
		}

		protected Function<? super T, R> getMap() {
			return theMap;
		}

		@Override
		public Subscription subscribe(Observer<? super R> observer) {
			return theWrapped.subscribe(new Observer<T>() {
				@Override
				public <V extends T> void onNext(V value) {
					R mapped;
					try {
						mapped = theMap.apply(value);
					} catch (RuntimeException e) {
						mapped = null;
						e.printStackTrace();
					}
					if (mapped != null)
						observer.onNext(mapped);
				}

				@Override
				public void onCompleted(Causable cause) {
					observer.onCompleted(cause);
				}
			});
		}

		@Override
		public boolean isSafe() {
			return theWrapped.isSafe();
		}

		@Override
		public Transaction lock() {
			return theWrapped.lock();
		}

		@Override
		public Transaction tryLock() {
			return theWrapped.tryLock();
		}

		@Override
		public String toString() {
			return "filterMap(" + theWrapped + ")";
		}
	}

	/**
	 * An observable that depends on the values of other observables
	 *
	 * @param <T> The type of the composed observable
	 */
	public class ComposedObservable<T> implements Observable<T> {
		private static final Object UNSET = new Object();

		private final Observable<?>[] theComposed;

		private final Function<Object[], T> theFunction;

		private final ListenerList<Observer<? super T>> theObservers;

		private final String theOperation;
		private Object theIdentity;

		/**
		 * @param function The function that operates on the argument observables to produce this observable's value
		 * @param operation A description of the composition operation
		 * @param composed The argument observables whose values are passed to the function
		 */
		public ComposedObservable(Function<Object[], T> function, String operation, Observable<?>... composed) {
			theFunction = function;
			theComposed = composed.clone();
			theObservers = ListenerList.build().withFastSize(false).withInUse(new ListenerList.InUseListener() {
				private final Subscription[] composedSubs = new Subscription[theComposed.length];

				private final Object[] values = new Object[theComposed.length];

				{
					Arrays.fill(values, UNSET);
				}

				@Override
				public void inUseChanged(boolean used) {
					if (used) {
						for (int i = 0; i < theComposed.length; i++) {
							int index = i;
							composedSubs[i] = theComposed[i].subscribe(new Observer<Object>() {
								@Override
								public <V> void onNext(V value) {
									values[index] = value;
									Object next = getNext();
									if (next != UNSET)
										fireNext((T) next);
								}

								@Override
								public void onCompleted(Causable cause) {
									Object next = getNext();
									if (next != UNSET)
										fireCompleted(cause);
								}

								private Object getNext() {
									Object[] args = values.clone();
									for (Object value : args)
										if (value == UNSET)
											return UNSET;
									return theFunction.apply(args);
								}

								private void fireNext(T next) {
									theObservers.forEach(//
										listener -> listener.onNext(next));
								}

								private void fireCompleted(Causable cause) {
									theObservers.forEach(//
										listener -> listener.onCompleted(cause));
								}
							});
						}
					} else {
						for (int i = 0; i < theComposed.length; i++) {
							composedSubs[i].unsubscribe();
							composedSubs[i] = null;
							values[i] = UNSET;
						}
					}
				}
			}).build();
			theOperation = operation;
		}

		@Override
		public Object getIdentity() {
			if (theIdentity == null) {
				Object[] obsIds = new Object[theComposed.length - 1];
				for (int i = 0; i < obsIds.length; i++)
					obsIds[i] = theComposed[i + 1].getIdentity();
				theIdentity = Identifiable.wrap(theComposed[0].getIdentity(), theOperation, obsIds);
			}
			return theIdentity;
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return ThreadConstrained.getThreadConstraint(theComposed);
		}

		@Override
		public boolean isEventing() {
			for (Observable<?> comp : theComposed) {
				if (comp.isEventing())
					return true;
			}
			return false;
		}

		@Override
		public Subscription subscribe(Observer<? super T> observer) {
			return theObservers.add(observer, true)::run;
		}

		@Override
		public boolean isSafe() {
			for (Observable<?> o : theComposed)
				if (!o.isSafe())
					return false;
			return true;
		}

		@Override
		public Transaction lock() {
			return Lockable.lockAll(theComposed);
		}

		@Override
		public Transaction tryLock() {
			return Lockable.tryLockAll(theComposed);
		}

		@Override
		public CoreId getCoreId() {
			return Lockable.getCoreId(theComposed);
		}

		@Override
		public int hashCode() {
			return getIdentity().hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof Observable && getIdentity().equals(((Observable<?>) obj).getIdentity());
		}

		@Override
		public String toString() {
			return getIdentity().toString();
		}
	}

	/**
	 * Implements {@link Observable#takeUntil(Observable)}
	 *
	 * @param <T> The type of the observable
	 */
	class ObservableTakenUntil<T> extends WrappingObservable<T, T> {
		private final Observable<?> theUntil;
		private final boolean isTerminating;

		protected ObservableTakenUntil(Observable<T> wrap, Observable<?> until, boolean terminate) {
			super(wrap);
			theUntil = until;
			isTerminating = terminate;
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(getWrapped().getIdentity(), (isTerminating ? "takeUntil" : "unsubscribeOn"), theUntil.getIdentity());
		}

		protected Observable<?> getUntil() {
			return theUntil;
		}

		@Override
		public Subscription subscribe(Observer<? super T> observer) {
			TakenUntilObserver untilObserver = new TakenUntilObserver(observer, theWrapped);
			return untilObserver.withUntilSubscription(theUntil.subscribe(untilObserver));
		}

		class TakenUntilObserver implements Observer<Object>, Subscription {
			private final Observer<? super T> theWrappedObserver;
			private Subscription theTargetSub;
			private Subscription theUntilSub;

			TakenUntilObserver(Observer<? super T> wrappedObserver, Observable<T> wrapped) {
				theWrappedObserver = wrappedObserver;
				theTargetSub = wrapped.subscribe(new Observer<T>() {
					@Override
					public <V extends T> void onNext(V value) {
						wrappedObserver.onNext(value);
					}

					@Override
					public void onCompleted(Causable cause) {
						unsubscribe();
						wrappedObserver.onCompleted(cause);
					}
				});
			}

			Subscription withUntilSubscription(Subscription untilSub) {
				theUntilSub = untilSub;
				if (theTargetSub == null) {
					untilSub.unsubscribe();
					theUntilSub = null;
					return Subscription.NONE;
				} else
					return this;
			}

			@Override
			public <V> void onNext(V value) {
				boolean fireComplete = isTerminating && theTargetSub != null;
				unsubscribe();
				if (fireComplete) {
					if (value instanceof Causable)
						theWrappedObserver.onCompleted((Causable) value);
					else {
						try (Causable.CausableInUse cause = Causable.cause(value)) {
							theWrappedObserver.onCompleted(cause);
						}
					}
				}
			}

			@Override
			public void onCompleted(Causable cause) {
				// A terminated until just means we'll listen to the target forever
				theUntilSub = null;
			}

			@Override
			public void unsubscribe() {
				Subscription sub = theTargetSub;
				theTargetSub = null;
				if (sub != null)
					sub.unsubscribe();

				sub = theUntilSub;
				theUntilSub = null;
				if (sub != null)
					sub.unsubscribe();
			}
		}
	}

	/**
	 * Implements {@link Observable#take(int)}
	 *
	 * @param <T> The type of the observable
	 */
	class ObservableTakenTimes<T> extends WrappingObservable<T, T> {
		private final int theTimes;

		protected ObservableTakenTimes(Observable<T> wrap, int times) {
			super(wrap);
			theTimes = times;
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(getWrapped().getIdentity(), "take", theTimes);
		}

		protected int getTimes() {
			return theTimes;
		}

		@Override
		public Subscription subscribe(Observer<? super T> observer) {
			TakenTimesObserver wrapper = new TakenTimesObserver(observer);
			return wrapper.withSubscription(theWrapped.subscribe(wrapper));
		}

		class TakenTimesObserver implements Observer<T>, Subscription {
			private final Observer<? super T> theWrappedObserver;
			private final AtomicInteger theCounter = new AtomicInteger();
			private Subscription theSubscription;

			TakenTimesObserver(Observer<? super T> wrapped) {
				theWrappedObserver = wrapped;
			}

			Subscription withSubscription(Subscription sub) {
				theSubscription = sub;
				if (theCounter.get() >= theTimes) {
					unsubscribe();
					return Subscription.NONE;
				} else
					return this;
			}

			@Override
			public <V extends T> void onNext(V value) {
				int count = theCounter.incrementAndGet();
				if (count < theTimes)
					theWrappedObserver.onNext(value);
				else if (count == theTimes) {
					unsubscribe();

					theWrappedObserver.onNext(value);

					if (value instanceof Causable)
						theWrappedObserver.onCompleted((Causable) value);
					else {
						try (Causable.CausableInUse cause = Causable.cause(value)) {
							theWrappedObserver.onCompleted(cause);
						}
					}
				}
			}

			@Override
			public void onCompleted(Causable cause) {
				theSubscription = null;
				theWrappedObserver.onCompleted(cause);
			}

			@Override
			public void unsubscribe() {
				Subscription sub = theSubscription;
				theSubscription = null;
				if (sub != null)
					sub.unsubscribe();
			}
		}
	}

	/**
	 * Implements {@link Observable#skip(Supplier)}
	 *
	 * @param <T> The type of the observable
	 */
	class SkippingObservable<T> extends WrappingObservable<T, T> {
		private final Supplier<Integer> theTimes;

		protected SkippingObservable(Observable<T> wrap, Supplier<Integer> times) {
			super(wrap);
			theTimes = times;
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(getWrapped().getIdentity(), "skip", theTimes);
		}

		protected Supplier<Integer> getTimes() {
			return theTimes;
		}

		@Override
		public Subscription subscribe(Observer<? super T> observer) {
			return theWrapped.subscribe(new Observer<T>() {
				private final AtomicInteger counter = new AtomicInteger(theTimes.get());

				@Override
				public <V extends T> void onNext(V value) {
					if (counter.get() <= 0 || counter.getAndDecrement() <= 0)
						observer.onNext(value);
				}

				@Override
				public void onCompleted(Causable cause) {
					observer.onCompleted(cause);
				}
			});
		}
	}

	/**
	 * Implements {@link Observable#safe(ThreadConstraint)}
	 *
	 * @param <T> The type of the observable
	 */
	class SafeObservable<T> extends WrappingObservable<T, T> {
		private final ThreadConstraint theThreading;

		public SafeObservable(Observable<T> wrapped, ThreadConstraint threading) {
			super(wrapped);
			if (!threading.supportsInvoke())
				throw new IllegalArgumentException("Thread constraints for safe structures must be invokable");
			theThreading = threading;
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(getWrapped().getIdentity(), "safe", theThreading);
		}

		@Override
		public Subscription subscribe(Observer<? super T> observer) {
			return getWrapped().subscribe(new Observer<T>() {
				@Override
				public <V extends T> void onNext(V value) {
					theThreading.invoke(() -> {
						observer.onNext(value);
					});
				}

				@Override
				public void onCompleted(Causable cause) {
					theThreading.invoke(() -> {
						observer.onCompleted(cause);
					});
				}
			});
		}
	}

	/**
	 * Implements {@link Observable#or(Observable...)}
	 *
	 * @param <V> The super type of the observables being or-ed
	 */
	class OrObservable<V> extends AbstractIdentifiable implements Observable<V> {
		private final Observable<? extends V>[] theObservables;

		public OrObservable(List<? extends Observable<? extends V>> obs) {
			this.theObservables = obs.toArray(new Observable[obs.size()]);
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.idFor(theObservables, () -> {
				return StringUtils.conversational(", ", null).print(theObservables, StringBuilder::append).toString();
			}, () -> Arrays.hashCode(theObservables), other -> theObservables.equals(other));
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			ThreadConstraint c = ThreadConstraint.NONE;
			for (Observable<? extends V> obs : theObservables) {
				ThreadConstraint obsC = obs.getThreadConstraint();
				if (c == ThreadConstraint.NONE)
					c = obsC;
				else if (obsC != ThreadConstraint.NONE && c != obsC)
					return ThreadConstraint.ANY;
			}
			return c;
		}

		@Override
		public boolean isEventing() {
			for (Observable<? extends V> obs : theObservables) {
				if (obs != null && obs.isEventing())
					return true;
			}
			return false;
		}

		@Override
		public Subscription subscribe(Observer<? super V> observer) {
			Subscription[] subs = new Subscription[theObservables.length];
			boolean[] init = new boolean[] { true };
			for (int i = 0; i < theObservables.length; i++) {
				int index = i;
				Lockable[] others = new Lockable[theObservables.length - 1];
				for (int j = 0; j < theObservables.length; j++) {
					if (j < index)
						others[j] = theObservables[j];
					else if (j > index)
						others[j - 1] = theObservables[j];
				}
				subs[i] = theObservables[i] == null ? Subscription.NONE : theObservables[i].subscribe(new Observer<V>() {
					@Override
					public <V2 extends V> void onNext(V2 value) {
						try (Transaction t = Lockable.lockAll(others)) {
							observer.onNext(value);
						}
					}

					@Override
					public void onCompleted(Causable cause) {
						try (Transaction t = Lockable.lockAll(others)) {
							subs[index] = null;
							boolean allDone = !init[0];
							for (int j = 0; allDone && j < subs.length; j++)
								if (subs[j] != null)
									allDone = false;
							if (allDone)
								observer.onCompleted(cause);
						}
					}
				});
			}
			init[0] = false;
			boolean allDone = true;
			for (int j = 0; allDone && j < subs.length; j++)
				if (subs[j] != null)
					allDone = false;
			if (allDone)
				observer.onCompleted(null);
			return Subscription.forAll(subs);
		}

		@Override
		public boolean isSafe() {
			for (Observable<?> o : theObservables)
				if (o != null && !o.isSafe())
					return false;
			return true;
		}

		@Override
		public Transaction lock() {
			return Lockable.lockAll(theObservables);
		}

		@Override
		public Transaction tryLock() {
			return Lockable.tryLockAll(theObservables);
		}

		@Override
		public CoreId getCoreId() {
			return Lockable.getCoreId(theObservables);
		}
	};

	/** Implements {@link Observable#onRootFinish(Observable)} */
	class CausableRootFinish extends WrappingObservable<Causable, Causable> {
		public CausableRootFinish(Observable<Causable> wrapped) {
			super(wrapped);
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(getWrapped().getIdentity(), "onRootFinish");
		}

		@Override
		public Subscription subscribe(Observer<? super Causable> observer) {
			Causable.CausableKey key = Causable.key((cause, values) -> {
				observer.onNext(cause);
			});
			return getWrapped().subscribe(new Observer<Causable>() {
				@Override
				public <V extends Causable> void onNext(V value) {
					value.getRootCausable().onFinish(key);
				}

				@Override
				public void onCompleted(Causable cause) {
					observer.onCompleted(cause);
				}
			});
		}
	}
	/**
	 * Implements {@link Observable#flatten(Observable)}
	 *
	 * @param <T> The type of value fired by the inner observable
	 */
	class FlattenedObservable<T> implements Observable<T> {
		private final Observable<? extends Observable<? extends T>> theWrapper;
		private Object theIdentity;

		protected FlattenedObservable(Observable<? extends Observable<? extends T>> wrapper) {
			theWrapper = wrapper;
		}

		@Override
		public Object getIdentity() {
			if (theIdentity == null)
				theIdentity = Identifiable.wrap(theWrapper.getIdentity(), "flatten");
			return theIdentity;
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return null; // We can't know
		}

		@Override
		public boolean isEventing() {
			return theWrapper.isEventing(); // Best guess, can't know for sure
		}

		protected Observable<? extends Observable<? extends T>> getWrapper() {
			return theWrapper;
		}

		@Override
		public Subscription subscribe(Observer<? super T> observer) {
			return theWrapper.subscribe(new Observer<Observable<? extends T>>() {
				@Override
				public <O extends Observable<? extends T>> void onNext(O innerObs) {
					if (innerObs != null) {
						innerObs.takeUntil(theWrapper.noInit()).subscribe(new Observer<T>() {
							@Override
							public <V extends T> void onNext(V value) {
								observer.onNext(value);
							}

							@Override
							public void onCompleted(Causable cause) {
								// Do nothing. The outer observable may get another value.
							}
						});
					} else
						observer.onNext(null);
				}

				@Override
				public void onCompleted(Causable cause) {
					observer.onCompleted(cause);
				}
			});
		}

		@Override
		public boolean isSafe() {
			return false; // Can't guarantee that all values in the wrapper will be safe
		}

		@Override
		public Transaction lock() {
			return theWrapper.lock(); // Can't access the contents reliably
		}

		@Override
		public Transaction tryLock() {
			return theWrapper.tryLock(); // Can't access the contents reliably
		}

		@Override
		public CoreId getCoreId() {
			return theWrapper.getCoreId(); // Can't access the contents reliably
		}

		@Override
		public int hashCode() {
			return theWrapper.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof FlattenedObservable))
				return false;
			return theWrapper.equals(((FlattenedObservable<?>) obj).theWrapper);
		}

		@Override
		public String toString() {
			return theWrapper + ".flat()";
		}
	}

	/** Implements {@link Observable#onVmShutdown()} */
	class VmShutdownObservable implements Observable<Void> {
		private Object theIdentity;

		@Override
		public Object getIdentity() {
			if (theIdentity == null)
				theIdentity = Identifiable.baseId("vmShutdown", this);
			return theIdentity;
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return ThreadConstraint.ANY;
		}

		@Override
		public boolean isEventing() {
			return false;
		}

		@Override
		public Subscription subscribe(Observer<? super Void> observer) {
			Thread hook = new Thread(() -> {
				observer.onNext(null);
				observer.onCompleted(null);
			}, "VM Shutdown");
			Runtime.getRuntime().addShutdownHook(hook);
			return () -> Runtime.getRuntime().removeShutdownHook(hook);
		}

		@Override
		public boolean isSafe() {
			return true;
		}

		@Override
		public boolean isLockSupported() {
			return false;
		}

		@Override
		public Transaction lock() {
			return Transaction.NONE;
		}

		@Override
		public Transaction tryLock() {
			return Transaction.NONE;
		}

		@Override
		public CoreId getCoreId() {
			return CoreId.EMPTY;
		}

		@Override
		public int hashCode() {
			return 0;
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof VmShutdownObservable;
		}

		@Override
		public String toString() {
			return "vmShutdown";
		}
	}

	/**
	 * Implements {@link Observable#every(Duration, Duration, Duration, Function, Consumer)}
	 *
	 * @param <T> The type of value the observable publishes
	 */
	class IntervalObservable<T> implements Observable<T> {
		private final QommonsTimer theTimer;
		private final QommonsTimer.TaskHandle theTask;
		private final Function<? super Duration, ? extends T> theValue;
		private final Consumer<? super T> thePostAction;
		private final ListenerList<Observer<? super T>> theObservers;
		private Instant theStartTime;
		private boolean isActual;

		private Duration theInitDelay;

		private Object theIdentity;

		public IntervalObservable(QommonsTimer timer, Duration initDelay, Duration interval, Duration until,
			Function<? super Duration, ? extends T> value, Consumer<? super T> postAction) {
			theTimer = timer;
			theTask = QommonsTimer.getCommonInstance().build(this::fire, interval, true);
			theInitDelay = initDelay;
			theObservers = ListenerList.build().withInUse(inUse -> {
				if (inUse) {
					theStartTime = QommonsTimer.getCommonInstance().getClock().now();
					theTask.resetExecutionCount();
				}
				theTask.setActive(inUse);
			}).build();
			if (theInitDelay != null) {
				if (initDelay.isNegative())
					throw new IllegalArgumentException("Initial delay must be >=0");
				theInitDelay = initDelay;
			}
			if (interval.compareTo(Duration.ofMillis(1)) < 0)
				throw new IllegalArgumentException("Interval must be >=1ms");
			if (until != null && theInitDelay != null && until.compareTo(theInitDelay.plus(interval)) < 0)
				throw new IllegalArgumentException("Until, if specified (not null), must be >=initial delay + interval");
			theValue = value;
			thePostAction = postAction;
			if (until != null)
				theTask.endIn(until, true);
		}

		@Override
		public Object getIdentity() {
			if (theIdentity == null) {
				StringBuilder str = new StringBuilder("every(");
				if (theInitDelay == null)
					str.append("null");
				else
					QommonsUtils.printDuration(theInitDelay, str, true);
				str.append(", ");
				QommonsUtils.printDuration(theTask.getFrequency(), str, true);
				str.append(", ");
				if (theTask.getLastRun() == null)
					str.append("null");
				else
					QommonsUtils.printDuration(Duration.between(Instant.now(), theTask.getLastRun()), str, true);
				theIdentity = Identifiable.baseId(str.toString(), this);
			}
			return theIdentity;
		}

		public IntervalObservable<T> actualDuration(boolean actual) {
			isActual = actual;
			return this;
		}

		public IntervalObservable<T> preciseTiming(boolean precise) {
			theTask.setFrequency(theTask.getFrequency(), precise);
			return this;
		}

		@Override
		public Subscription subscribe(Observer<? super T> observer) {
			return theObservers.add(observer, true)::run;
		}

		void fire() {
			Duration valueTime;
			if (isActual)
				valueTime = TimeUtils.between(theStartTime, theTimer.getClock().now());
			else
				valueTime = theTask.getFrequency().multipliedBy(theTask.getExecutionCount());
			T value = theValue == null ? null : theValue.apply(valueTime);
			try {
				theObservers.forEach(//
					o -> o.onNext(value));
			} finally {
				if (thePostAction != null)
					thePostAction.accept(value);
			}
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			if (theTask.getThreading() == QommonsTimer.TaskThreading.EDT)
				return ThreadConstraint.EDT;
			else
				return ThreadConstraint.ANY;
		}

		@Override
		public boolean isEventing() {
			return theObservers.isFiring();
		}

		@Override
		public boolean isSafe() {
			return true;
		}

		@Override
		public boolean isLockSupported() {
			return false;
		}

		@Override
		public Transaction lock() {
			return Transaction.NONE;
		}

		@Override
		public Transaction tryLock() {
			return Transaction.NONE;
		}

		@Override
		public CoreId getCoreId() {
			return CoreId.EMPTY;
		}

		@Override
		public int hashCode() {
			return getIdentity().hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof Identifiable && getIdentity().equals(((Identifiable) obj).getIdentity());
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder("every(");
			if (theInitDelay == null)
				str.append("null");
			else
				QommonsUtils.printDuration(theInitDelay, str, true);
			str.append(", ");
			QommonsUtils.printDuration(theTask.getFrequency(), str, true);
			str.append(", ");
			if (theTask.getLastRun() == null)
				str.append("null");
			else
				QommonsUtils.printDuration(Duration.between(Instant.now(), theTask.getLastRun()), str, true);
			return str.toString();
		}
	}
}
