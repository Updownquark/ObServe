package org.observe;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.observe.util.TypeTokens;
import org.qommons.Causable;
import org.qommons.Identifiable;
import org.qommons.Lockable;
import org.qommons.QommonsUtils;
import org.qommons.StringUtils;
import org.qommons.TimeUtils;
import org.qommons.Transaction;
import org.qommons.collect.ListenerList;
import org.qommons.threading.QommonsTimer;

import com.google.common.reflect.TypeToken;

/**
 * A stream of values that can be filtered, mapped, composed, etc. and evaluated on
 *
 * @param <T> The type of values this observable provides
 */
public interface Observable<T> extends Lockable, Identifiable {
	/** This class's wildcard {@link TypeToken} */
	static TypeToken<Observable<?>> TYPE = TypeTokens.get().keyFor(Observable.class).wildCard();

	/**
	 * Subscribes to this observable such that the given observer will be notified of any new values on this observable.
	 *
	 * @param observer The observer to be notified when new values are available from this observable
	 * @return A subscription that, when invoked, will cease notifications to the observer
	 */
	Subscription subscribe(Observer<? super T> observer);

	/** @return A chaining observable, allowing chained calls with one subscription to rule them all */
	default ChainingObservable<T> chain() {
		return new DefaultChainingObservable<>(this, null, null);
	}

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
			public <V extends T> void onCompleted(V value) {}

			@Override
			public String toString() {
				return action.toString();
			}
		});
	}

	/** @return An observable that will fire once when this observable completes (the value will be null) */
	default Observable<T> completed() {
		return new CompletionObservable<>(this, false);
	}

	/**
	 * @return An observable that fires the same values as this observable, but calls its observers' {@link Observer#onNext(Object)} method
	 *         as well when this observable completes.
	 */
	default Observable<T> fireOnComplete() {
		return new CompletionObservable<>(this, true);
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
		return filterMap(value -> (value != null && func.apply(value)) ? value : null);
	}

	/**
	 * @param <R> The type of the returned observable
	 * @param func The map function
	 * @return An observable that provides the values of this observable, mapped by the given function
	 */
	default <R> Observable<R> map(Function<? super T, R> func) {
		return new ComposedObservable<>(args -> func.apply((T) args[0]), "map", this);
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
	 * @param type The type to filter on
	 * @return An observable that provides all of this observable's values that are also an instance of the given type
	 */
	default <R> Observable<R> filterMap(Class<R> type) {
		return filterMap(v -> type.isInstance(type) ? type.cast(v) : null);
	}

	/**
	 * @param <V> The type of the other observable to be combined with this one
	 * @param <R> The type of the returned observable
	 * @param other The other observable to compose
	 * @param func The function to use to combine the observables' values
	 * @return A new observable whose values are the specified combination of this observable and the others'
	 */
	default <V, R> Observable<R> combine(Observable<V> other, BiFunction<? super T, ? super V, R> func) {
		return new ComposedObservable<>(args -> func.apply((T) args[0], (V) args[1]), "combine", this, other);
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

	/** @return An observable firing the same values that only fires values on a single thread at a time */
	default Observable<T> safe() {
		if (isSafe())
			return this;
		else
			return new SafeObservable<>(this);
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
			return Subscription.NONE;
		}

		@Override
		public Observable<Object> noInit() {
			return this;
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
	 * @param <T> The type of value to fire
	 * @param value Supplies the value to fire via {@link Observer#onCompleted(Object)} upon subscription
	 * @return An observable that fires the given value once via {@link Observer#onCompleted(Object)} once upon subscription
	 */
	static <T> Observable<T> once(Supplier<T> value) {
		class OnceObservable extends AbstractIdentifiable implements Observable<T> {
			@Override
			protected Object createIdentity() {
				return Identifiable.wrap(value, "once");
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
			public Subscription subscribe(Observer<? super T> observer) {
				observer.onCompleted(value.get());
				return Subscription.NONE;
			}
		}
		return new OnceObservable();
	}

	/**
	 * @return An observable that fires (a null value) both for {@link Observer#onNext(Object) onNext} and
	 *         {@link Observer#onCompleted(Object) onCompleted} as the Java VM is shutting down
	 */
	static Observable<Void> onVmShutdown() {
		return new VmShutdownObservable();
	}

	/**
	 * @param initDelay The initial delay before firing the first value
	 * @param interval The interval at which to fire values
	 * @param until The duration after which values will stop being fired (and an {@link Observer#onCompleted(Object) onCompleted} event
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
	abstract class WrappingObservable<F, T> implements Observable<T> {
		protected final Observable<F> theWrapped;
		private Object theIdentity;

		public WrappingObservable(Observable<F> wrapped) {
			theWrapped = wrapped;
		}

		protected Observable<F> getWrapped() {
			return theWrapped;
		}

		@Override
		public Object getIdentity() {
			if (theIdentity == null)
				theIdentity = createIdentity();
			return theIdentity;
		}

		protected abstract Object createIdentity();

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
	 * Implements {@link Observable#completed()}
	 *
	 * @param <T> The type of the observable
	 */
	class CompletionObservable<T> extends WrappingObservable<T, T> {
		private final boolean fireOnNext;

		public CompletionObservable(Observable<T> wrapped, boolean fireOnNext) {
			super(wrapped);
			this.fireOnNext = fireOnNext;
		}

		@Override
		public Subscription subscribe(Observer<? super T> observer) {
			class CompleteObserver implements Observer<T> {
				private final Observer<? super T> wrapped;

				CompleteObserver(Observer<? super T> wrap) {
					wrapped = wrap;
				}

				@Override
				public <V extends T> void onNext(V value) {
					if (fireOnNext)
						wrapped.onNext(value);
				}

				@Override
				public <V extends T> void onCompleted(V value) {
					wrapped.onNext(value);
					wrapped.onCompleted(value);
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
	 * Implements {@link #chain()}
	 *
	 * @param <T> The type of the observable
	 */
	class DefaultChainingObservable<T> extends WrappingObservable<T, T> implements ChainingObservable<T> {
		private final Observable<Void> theCompletion;

		private final Observer<Void> theCompletionController;

		/**
		 * @param wrap The observable that this chaining observable reflects the values of
		 * @param completion The completion observable that will emit a value when the {@link #unsubscribe()} method of any link in the
		 *        chain is called. May be null if this is the first link in the chain, in which case the observable and its controller will
		 *        be created.
		 * @param controller The controller for the completion observable. May be null if <code>completion</code> is null.
		 */
		public DefaultChainingObservable(Observable<T> wrap, Observable<Void> completion, Observer<Void> controller) {
			super(wrap);
			if (completion != null) {
				theCompletion = completion;
				theCompletionController = controller;
			} else {
				theCompletion = new SimpleObservable<>();
				theCompletionController = (Observer<Void>) theCompletion;
			}
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(getWrapped().getIdentity(), "chain");
		}

		@Override
		public void unsubscribe() {
			theCompletionController.onNext(null);
		}

		@Override
		public Observable<T> unchain() {
			return getWrapped();
		}

		@Override
		public ChainingObservable<T> subscribe(Observer<? super T> observer) {
			theWrapped.takeUntil(theCompletion).subscribe(observer);
			return this;
		}

		@Override
		public ChainingObservable<T> act(Consumer<? super T> action) {
			theWrapped.takeUntil(theCompletion).act(action);
			return this;
		}

		@Override
		public ChainingObservable<T> completed() {
			return new DefaultChainingObservable<>(unchain().completed(), theCompletion, theCompletionController);
		}

		@Override
		public ChainingObservable<T> noInit() {
			return new DefaultChainingObservable<>(unchain().noInit(), theCompletion, theCompletionController);
		}

		@Override
		public ChainingObservable<T> filter(Function<? super T, Boolean> func) {
			return new DefaultChainingObservable<>(unchain().filter(func), theCompletion, theCompletionController);
		}

		@Override
		public <R> ChainingObservable<R> map(Function<? super T, R> func) {
			return new DefaultChainingObservable<>(unchain().map(func), theCompletion, theCompletionController);
		}

		@Override
		public <R> ChainingObservable<R> filterMap(Function<? super T, R> func) {
			return new DefaultChainingObservable<>(unchain().filterMap(func), theCompletion, theCompletionController);
		}

		@Override
		public <V, R> ChainingObservable<R> combine(Observable<V> other, BiFunction<? super T, ? super V, R> func) {
			return new DefaultChainingObservable<>(unchain().combine(other, func), theCompletion, theCompletionController);
		}

		@Override
		public ChainingObservable<T> takeUntil(Observable<?> until) {
			return new DefaultChainingObservable<>(unchain().takeUntil(until), theCompletion, theCompletionController);
		}

		@Override
		public ChainingObservable<T> take(int times) {
			return new DefaultChainingObservable<>(unchain().take(times), theCompletion, theCompletionController);
		}

		@Override
		public ChainingObservable<T> skip(int times) {
			return new DefaultChainingObservable<>(unchain().skip(times), theCompletion, theCompletionController);
		}

		@Override
		public ChainingObservable<T> skip(Supplier<Integer> times) {
			return new DefaultChainingObservable<>(unchain().skip(times), theCompletion, theCompletionController);
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
				public <V extends T> void onCompleted(V value) {
					observer.onCompleted(value);
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
					R mapped = theMap.apply(value);
					if (mapped != null)
						observer.onNext(mapped);
				}

				@Override
				public <V extends T> void onCompleted(V value) {
					R mapped = theMap.apply(value);
					if (mapped != null)
						observer.onCompleted(mapped);
					else
						observer.onCompleted(null); // Gotta pass along the completion even if it doesn't include a value
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

		private final List<Observable<?>> theComposed;

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
			theComposed = java.util.Collections.unmodifiableList(java.util.Arrays.asList(composed));
			theObservers = ListenerList.build().withFastSize(false).withInUse(new ListenerList.InUseListener() {
				private final Subscription[] composedSubs = new Subscription[theComposed.size()];

				private final Object[] values = new Object[theComposed.size()];

				{
					Arrays.fill(values, UNSET);
				}

				@Override
				public void inUseChanged(boolean used) {
					if (used) {
						for (int i = 0; i < theComposed.size(); i++) {
							int index = i;
							composedSubs[i] = theComposed.get(i).subscribe(new Observer<Object>() {
								@Override
								public <V> void onNext(V value) {
									values[index] = value;
									Object next = getNext();
									if (next != UNSET)
										fireNext((T) next);
								}

								@Override
								public <V> void onCompleted(V value) {
									values[index] = value;
									Object next = getNext();
									if (next != UNSET)
										fireCompleted((T) next);
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

								private void fireCompleted(T next) {
									theObservers.forEach(//
										listener -> listener.onCompleted(next));
								}
							});
						}
					} else {
						for (int i = 0; i < theComposed.size(); i++) {
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
				Object[] obsIds = new Object[theComposed.size() - 1];
				for (int i = 0; i < obsIds.length; i++)
					obsIds[i] = theComposed.get(i + 1).getIdentity();
				theIdentity = Identifiable.wrap(theComposed.get(0).getIdentity(), theOperation, obsIds);
			}
			return theIdentity;
		}

		@Override
		public Subscription subscribe(Observer<? super T> observer) {
			return theObservers.add(observer, false)::run;
		}

		/** @return The observables that this observable uses as sources */
		public Observable<?>[] getComposed() {
			return theComposed.toArray(new Observable[theComposed.size()]);
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
		private final Supplier<T> theDefaultValue;

		protected ObservableTakenUntil(Observable<T> wrap, Observable<?> until, boolean terminate) {
			this(wrap, until, terminate, () -> null);
		}

		protected ObservableTakenUntil(Observable<T> wrap, Observable<?> until, boolean terminate, Supplier<T> def) {
			super(wrap);
			theUntil = until;
			isTerminating = terminate;
			theDefaultValue = def;
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
			Subscription outerSub = theWrapped.subscribe(observer);
			boolean[] complete = new boolean[1];
			Subscription[] untilSub = new Subscription[1];
			untilSub[0] = theUntil.subscribe(new Observer<Object>() {
				@Override
				public void onNext(Object value) {
					if (complete[0])
						return;
					complete[0] = true;
					outerSub.unsubscribe();
					if (isTerminating) {
						T defValue = theDefaultValue.get();
						try (Transaction t = Causable.use(defValue)) {
							observer.onCompleted(defValue);
						}
					}
				}

				@Override
				public void onCompleted(Object value) {
					// A completed until shouldn't affect things
				}
			});
			return () -> {
				if (complete[0])
					return;
				complete[0] = true;
				outerSub.unsubscribe();
				untilSub[0].unsubscribe();
			};
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
			Subscription[] wrapSub = new Subscription[1];
			boolean[] completed = new boolean[1];
			wrapSub[0] = theWrapped.subscribe(new Observer<T>() {
				private final AtomicInteger theCounter = new AtomicInteger();

				@Override
				public <V extends T> void onNext(V value) {
					int count = theCounter.getAndIncrement();
					if (count < theTimes)
						observer.onNext(value);
					if (count == theTimes - 1) {
						observer.onCompleted(value);
						if (wrapSub[0] != null)
							wrapSub[0].unsubscribe();
						completed[0] = true;
					}
				}

				@Override
				public <V extends T> void onCompleted(V value) {
					if (theCounter.get() < theTimes)
						observer.onCompleted(value);
				}
			});
			if (completed[0])
				wrapSub[0].unsubscribe();
			return () -> {
				if (!completed[0]) {
					completed[0] = true;
					wrapSub[0].unsubscribe();
				}
			};
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
				public <V extends T> void onCompleted(V value) {
					observer.onCompleted(value);
				}
			});
		}
	}

	/**
	 * Implements {@link Observable#safe()}
	 *
	 * @param <T> The type of values that the observable publishes
	 */
	class SafeObservable<T> extends WrappingObservable<T, T> {
		private final ReentrantReadWriteLock theLock;

		protected SafeObservable(Observable<T> wrap) {
			super(wrap);
			theLock = new ReentrantReadWriteLock();
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(getWrapped().getIdentity(), "safe");
		}

		@Override
		public Subscription subscribe(Observer<? super T> observer) {
			return theWrapped.subscribe(new Observer<T>() {
				@Override
				public <V extends T> void onNext(V value) {
					theLock.writeLock().lock();
					try {
						observer.onNext(value);
					} finally {
						theLock.writeLock().unlock();
					}
				}

				@Override
				public <V extends T> void onCompleted(V value) {
					theLock.writeLock().lock();
					try {
						observer.onCompleted(value);
					} finally {
						theLock.writeLock().unlock();
					}
				}
			});
		}

		@Override
		public boolean isSafe() {
			return true;
		}

		@Override
		public Transaction lock() {
			return Lockable.lockAll(theWrapped, Lockable.lockable(theLock, this, false));
		}

		@Override
		public Transaction tryLock() {
			return Lockable.tryLockAll(theWrapped, Lockable.lockable(theLock, this, false));
		}

		@Override
		public Observable<T> noInit() {
			Observable<T> wrap = getWrapped().noInit();
			if (wrap == getWrapped())
				return this;
			return wrap.safe();
		}
	}

	/**
	 * Implements {@link Observable#or(Observable...)}
	 *
	 * @param <V> The super type of the observables being or-ed
	 */
	class OrObservable<V> implements Observable<V> {
		private final List<? extends Observable<? extends V>> theObservables;

		private Object theIdentity;

		public OrObservable(List<? extends Observable<? extends V>> obs) {
			this.theObservables = obs;
		}

		@Override
		public Object getIdentity() {
			if (theIdentity == null) {
				theIdentity = Identifiable.idFor(theObservables, () -> {
					return StringUtils.conversational(", ", null).print(theObservables, StringBuilder::append).toString();
				}, () -> theObservables.hashCode(), other -> theObservables.equals(other));
			}
			return theIdentity;
		}

		@Override
		public Subscription subscribe(Observer<? super V> observer) {
			Subscription[] subs = new Subscription[theObservables.size()];
			boolean[] init = new boolean[] { true };
			for (int i = 0; i < theObservables.size(); i++) {
				int index = i;
				Lockable[] others = new Lockable[theObservables.size() - 1];
				for (int j = 0; j < theObservables.size(); j++) {
					if (j < index)
						others[j] = theObservables.get(j);
					else if (j > index)
						others[j - 1] = theObservables.get(j);
				}
				subs[i] = theObservables.get(i).subscribe(new Observer<V>() {
					@Override
					public <V2 extends V> void onNext(V2 value) {
						try (Transaction t = Lockable.lockAll(others)) {
							observer.onNext(value);
						}
					}

					@Override
					public <V2 extends V> void onCompleted(V2 value) {
						try (Transaction t = Lockable.lockAll(others)) {
							subs[index] = null;
							boolean allDone = !init[0];
							for (int j = 0; allDone && j < subs.length; j++)
								if (subs[j] != null)
									allDone = false;
							if (allDone)
								observer.onCompleted(value);
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
				if (!o.isSafe())
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
	};

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

		protected Observable<? extends Observable<? extends T>> getWrapper() {
			return theWrapper;
		}

		@Override
		public Subscription subscribe(Observer<? super T> observer) {
			return theWrapper.subscribe(new Observer<Observable<? extends T>>() {
				private T theLastValue;

				@Override
				public <O extends Observable<? extends T>> void onNext(O innerObs) {
					if (innerObs != null) {
						innerObs.takeUntil(theWrapper.noInit()).subscribe(new Observer<T>() {
							@Override
							public <V extends T> void onNext(V value) {
								theLastValue = value;
								observer.onNext(value);
							}

							@Override
							public <V extends T> void onCompleted(V value) {
								// Do nothing. The outer observable may get another value.
							}
						});
					} else {
						theLastValue = null;
						observer.onNext(null);
					}
				}

				@Override
				public <O extends Observable<? extends T>> void onCompleted(O innerObs) {
					observer.onCompleted(theLastValue);
					theLastValue = null;
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
			theObservers = ListenerList.build().withInUse(inUse -> {
				if (inUse) {
					theStartTime = QommonsTimer.getCommonInstance().getClock().now();
					theTask.resetExecutionCount();
				}
				theTask.setActive(inUse);
			}).build();
			if (initDelay.isNegative())
				throw new IllegalArgumentException("Initial delay must be >=0");
			theInitDelay = initDelay;
			if (interval.compareTo(Duration.ofMillis(1)) < 0)
				throw new IllegalArgumentException("Interval must be >=1ms");
			if (until != null && until.compareTo(theInitDelay.plus(interval)) < 0)
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
			T value = theValue.apply(valueTime);
			try {
				theObservers.forEach(//
					o -> o.onNext(value));
			} finally {
				if (thePostAction != null)
					thePostAction.accept(value);
			}
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
	}
}
