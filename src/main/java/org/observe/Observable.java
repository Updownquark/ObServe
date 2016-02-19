package org.observe;

import static org.observe.ObservableDebug.d;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.qommons.ListenerSet;

/**
 * A stream of values that can be filtered, mapped, composed, etc. and evaluated on
 *
 * @param <T> The type of values this observable provides
 */
public interface Observable<T> {
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
	default Subscription act(Action<? super T> action) {
		return subscribe(new Observer<T>() {
			@Override
			public <V extends T> void onNext(V value) {
				action.act(value);
			}
		});
	}

	/** @return An observable for this observable's errors */
	default Observable<Throwable> error() {
		Observable<T> outer = this;
		class ErrorObserver implements Observer<T> {
			private final Observer<? super Throwable> wrapped;

			ErrorObserver(Observer<? super Throwable> wrap) {
				wrapped = wrap;
			}

			@Override
			public <V extends T> void onNext(V value) {
			}

			@Override
			public void onError(Throwable e) {
				wrapped.onNext(e);
			}
		}
		return d().debug(new Observable<Throwable>() {
			@Override
			public Subscription subscribe(Observer<? super Throwable> observer) {
				return outer.subscribe(new ErrorObserver(observer));
			}

			@Override
			public boolean isSafe() {
				return outer.isSafe();
			}
		}).from("error", outer).get();
	}

	/** @return An observable that will fire once when this observable completes (the value will be null) */
	default Observable<T> completed() {
		Observable<T> outer = this;
		class CompleteObserver implements Observer<T> {
			private final Observer<? super T> wrapped;

			CompleteObserver(Observer<? super T> wrap) {
				wrapped = wrap;
			}

			@Override
			public <V extends T> void onNext(V value) {
			}

			@Override
			public <V extends T> void onCompleted(V value) {
				wrapped.onNext(value);
				wrapped.onCompleted(value);
			}
		}
		return d().debug(new Observable<T>() {
			@Override
			public Subscription subscribe(Observer<? super T> observer) {
				return outer.subscribe(new CompleteObserver(observer));
			}

			@Override
			public boolean isSafe() {
				return outer.isSafe();
			}
		}).from("completed", outer).get();
	}

	/**
	 * @return An observable that fires the same values as this observable, but calls its observers' {@link Observer#onNext(Object)} method
	 *         as well when this observable completes.
	 */
	default Observable<T> fireOnComplete() {
		Observable<T> outer = this;
		return d().debug(new Observable<T>() {
			@Override
			public Subscription subscribe(Observer<? super T> observer) {
				return outer.subscribe(new Observer<T>() {
					@Override
					public <V extends T> void onNext(V value) {
						observer.onNext(value);
					}

					@Override
					public <V extends T> void onCompleted(V value) {
						observer.onNext(value);
						observer.onCompleted(value);
					}
				});
			}

			@Override
			public boolean isSafe() {
				return outer.isSafe();
			}
		}).from("fireOnComplete", this).get();
	}

	/**
	 * @return An observable that returns the same values as this one except that any initialization events (for cold observables) will be
	 *         ignored.
	 */
	default Observable<T> noInit() {
		Observable<T> outer = this;
		return d().debug(new NoInitObservable<>(this)).from("noInit", outer).get();
	}

	/**
	 * @param func The filter function
	 * @return An observable that provides the same values as this observable minus those that the filter function returns false for
	 */
	default Observable<T> filter(Function<? super T, Boolean> func) {
		return filterMap(d().lambda(value -> (value != null && func.apply(value)) ? value : null, "filter"));
	}

	/**
	 * @param <R> The type of the returned observable
	 * @param func The map function
	 * @return An observable that provides the values of this observable, mapped by the given function
	 */
	default <R> Observable<R> map(Function<? super T, R> func) {
		return d().debug(new ComposedObservable<R>(d().lambda(args -> {
			return func.apply((T) args[0]);
		}, "map"), this)).from("mapped", this).using("map", func).get();
	}

	/**
	 * @param <R> The type of the returned observable
	 * @param func The filter map function
	 * @return An observable that provides the values of this observable, mapped by the given function, except where that function returns
	 *         null
	 */
	default <R> Observable<R> filterMap(Function<? super T, R> func) {
		return d().debug(new FilteredObservable<>(this, func)).from("filterMap", this).using("map", func).get();
	}

	/**
	 * @param <V> The type of the other observable to be combined with this one
	 * @param <R> The type of the returned observable
	 * @param other The other observable to compose
	 * @param func The function to use to combine the observables' values
	 * @return A new observable whose values are the specified combination of this observable and the others'
	 */
	default <V, R> Observable<R> combine(Observable<V> other, BiFunction<? super T, ? super V, R> func) {
		return d().debug(new ComposedObservable<R>(d().lambda(args -> {
			return func.apply((T) args[0], (V) args[1]);
		}, "combine"), this, other)).from("combine-arg0", this).from("combine-arg1", other).using("combination", func).get();
	}

	/**
	 * @param until The observable to watch
	 * @return An observable that provides the same values as this observable until the first value is observed from the given observable
	 */
	default Observable<T> takeUntil(Observable<?> until) {
		return d().debug(new ObservableTakenUntil<>(this, until, true)).from("take", this).from("until", until).tag("terminate", true)
				.get();
	}

	/**
	 * A different form of {@link #takeUntil(Observable)} that does not complete the observable when <code>until</code> fires, but merely
	 * unsubscribes all subscriptions
	 *
	 * @param until the observable to watch
	 * @return An observable that provides the same values as this observable until the first value is observed from the given observable
	 */
	default Observable<T> unsubscribeOn(Observable<?> until) {
		return d().debug(new ObservableTakenUntil<>(this, until, false)).from("take", this).from("until", until)
				.tag("terminate", false)
				.get();
	}

	/**
	 * @param times The number of values to take from this observable
	 * @return An observable that provides the same values as this observable but completes after the given number of values
	 */
	default Observable<T> take(int times) {
		return d().debug(new ObservableTakenTimes<>(this, times)).from("take", this).tag("times", times).get();
	}

	/**
	 * @param times The number of values to skip from this observable
	 * @return An observable that provides the same values as this observable but ignores the first {@code times} values
	 */
	default Observable<T> skip(int times) {
		return d().label(skip(() -> times)).tag("times", times).get();
	}

	/**
	 * Like {@link #skip(int)}, but the number of times to skip is retrieved when the observable is subscribed to instead of when it is
	 * created.
	 *
	 * @param times A supplier that returns the number of values to skip from this observable.
	 * @return An observable that provides the same values as this observable but ignores the first {@code times} values
	 */
	default Observable<T> skip(java.util.function.Supplier<Integer> times) {
		return d().debug(new SkippingObservable<>(this, times)).from("skip", this).using("times", times).get();
	}

	/** @return Whether this observable is thread-safe, meaning it is constrained to only fire values on a single thread at a time */
	boolean isSafe();

	/** @return An observable firing the same values that only fires values on a single thread at a time */
	default Observable<T> safe() {
		if (isSafe())
			return this;
		else
			return d().debug(new SafeObservable<>(this)).from("safe", this).get();
	}

	/**
	 * @param <V> The super-type of all observables to or
	 * @param obs The observables to combine
	 * @return An observable that pushes a value each time any of the given observables pushes a value
	 */
	public static <V> Observable<V> or(Observable<? extends V>... obs) {
		return d().debug(new Observable<V>() {
			@Override
			public Subscription subscribe(Observer<? super V> observer) {
				Subscription [] subs = new Subscription[obs.length];
				for(int i = 0; i < subs.length; i++)
					subs[i] = obs[i].subscribe(new Observer<V>() {
						@Override
						public <V2 extends V> void onNext(V2 value) {
							observer.onNext(value);
						}

						@Override
						public <V2 extends V> void onCompleted(V2 value) {
							observer.onCompleted(value);
						}

						@Override
						public void onError(Throwable e) {
							observer.onError(e);
						}
					});
				return () -> {
					for(Subscription sub : subs)
						sub.unsubscribe();
				};
			}

			@Override
			public boolean isSafe() {
				return false;
			}

			@Override
			public String toString() {
				StringBuilder ret = new StringBuilder("or(");
				for(int i = 0; i < obs.length; i++) {
					if(i > 0)
						ret.append(", ");
					ret.append(obs[i]);
				}
				return ret.toString();
			}
		}).from("or", (Object []) obs).get(); // TODO
	}

	/**
	 * @param <T> The type of the observable to create
	 * @param value The value for the observable
	 * @return An observable that pushes the given value as soon as it is subscribed to and never completes
	 */
	public static <T> Observable<T> constant(T value) {
		return d().debug(new Observable<T>() {
			@Override
			public Subscription subscribe(Observer<? super T> observer) {
				observer.onNext(value);
				return () -> {
				};
			}

			@Override
			public boolean isSafe() {
				return true;
			}
		}).tag("constant", value).get();
	}

	/** An empty observable that never does anything */
	public static Observable<?> empty = new Observable<Object>() {
		@Override
		public Subscription subscribe(Observer<? super Object> observer) {
			return () -> {
			};
		}

		@Override
		public boolean isSafe() {
			return true;
		}
	};

	/**
	 * Implements {@link #chain()}
	 *
	 * @param <T> The type of the observable
	 */
	class DefaultChainingObservable<T> implements ChainingObservable<T> {
		private final Observable<T> theWrapped;

		private final Observable<Void> theCompletion;

		private final Observer<Void> theCompletionController;

		/**
		 * @param wrap The observable that this chaining observable reflects the values of
		 * @param completion The completion observable that will emit a value when the {@link #unsubscribe()} method of any link in the
		 *            chain is called. May be null if this is the first link in the chain, in which case the observable and its controller
		 *            will be created.
		 * @param controller The controller for the completion observable. May be null if <code>completion</code> is null.
		 */
		public DefaultChainingObservable(Observable<T> wrap, Observable<Void> completion, Observer<Void> controller) {
			theWrapped = wrap;
			theCompletion = completion == null ? new DefaultObservable<>() : completion;
			theCompletionController = completion == null ? ((DefaultObservable<Void>) theCompletion).control(null) : controller;
		}

		@Override
		public void unsubscribe() {
			theCompletionController.onNext(null);
		}

		@Override
		public Observable<T> unchain() {
			return theWrapped;
		}

		@Override
		public ChainingObservable<T> subscribe(Observer<? super T> observer) {
			theWrapped.takeUntil(theCompletion).subscribe(observer);
			return this;
		}

		@Override
		public ChainingObservable<T> act(Action<? super T> action) {
			theWrapped.takeUntil(theCompletion).act(action);
			return this;
		}

		@Override
		public ChainingObservable<Throwable> error() {
			return new DefaultChainingObservable<>(theWrapped.error(), theCompletion, theCompletionController);
		}

		@Override
		public ChainingObservable<T> completed() {
			return new DefaultChainingObservable<>(theWrapped.completed(), theCompletion, theCompletionController);
		}

		@Override
		public ChainingObservable<T> noInit() {
			return new DefaultChainingObservable<>(theWrapped.noInit(), theCompletion, theCompletionController);
		}

		@Override
		public ChainingObservable<T> filter(Function<? super T, Boolean> func) {
			return new DefaultChainingObservable<>(theWrapped.filter(func), theCompletion, theCompletionController);
		}

		@Override
		public <R> ChainingObservable<R> map(Function<? super T, R> func) {
			return new DefaultChainingObservable<>(theWrapped.map(func), theCompletion, theCompletionController);
		}

		@Override
		public <R> ChainingObservable<R> filterMap(Function<? super T, R> func) {
			return new DefaultChainingObservable<>(theWrapped.filterMap(func), theCompletion, theCompletionController);
		}

		@Override
		public <V, R> ChainingObservable<R> combine(Observable<V> other, BiFunction<? super T, ? super V, R> func) {
			return new DefaultChainingObservable<>(theWrapped.combine(other, func), theCompletion, theCompletionController);
		}

		@Override
		public ChainingObservable<T> takeUntil(Observable<?> until) {
			return new DefaultChainingObservable<>(theWrapped.takeUntil(until), theCompletion, theCompletionController);
		}

		@Override
		public ChainingObservable<T> take(int times) {
			return new DefaultChainingObservable<>(theWrapped.take(times), theCompletion, theCompletionController);
		}

		@Override
		public ChainingObservable<T> skip(int times) {
			return new DefaultChainingObservable<>(theWrapped.skip(times), theCompletion, theCompletionController);
		}

		@Override
		public ChainingObservable<T> skip(Supplier<Integer> times) {
			return new DefaultChainingObservable<>(theWrapped.skip(times), theCompletion, theCompletionController);
		}

		@Override
		public boolean isSafe() {
			return theWrapped.isSafe();
		}
	}

	/**
	 * Implements {@link Observable#noInit()}
	 *
	 * @param <T> The type of the observable
	 */
	class NoInitObservable<T> implements Observable<T> {
		private final Observable<T> theWrapped;

		protected NoInitObservable(Observable<T> wrap) {
			theWrapped = wrap;
		}

		protected Observable<T> getWrapped() {
			return theWrapped;
		}

		@Override
		public Subscription subscribe(Observer<? super T> observer) {
			boolean [] initialized = new boolean[1];
			Subscription ret = theWrapped.subscribe(new Observer<T>() {
				@Override
				public <V extends T> void onNext(V value) {
					if(initialized[0])
						observer.onNext(value);
				}

				@Override
				public <V extends T> void onCompleted(V value) {
					observer.onCompleted(value);
				}

				@Override
				public void onError(Throwable e) {
					observer.onError(e);
				}
			});
			initialized[0] = true;
			return ret;
		}

		@Override
		public boolean isSafe() {
			return theWrapped.isSafe();
		}
	}

	/**
	 * Implements {@link Observable#filterMap(Function)}
	 *
	 * @param <T> The type of the observable to filter-map
	 * @param <R> The type of the mapped observable
	 */
	class FilteredObservable<T, R> implements Observable<R> {
		private final Observable<T> theWrapped;

		private final Function<? super T, R> theMap;

		protected FilteredObservable(Observable<T> wrap, Function<? super T, R> map) {
			theWrapped = wrap;
			theMap = map;
		}

		protected Observable<T> getWrapped() {
			return theWrapped;
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
					if(mapped != null)
						observer.onNext(mapped);
				}

				@Override
				public <V extends T> void onCompleted(V value) {
					R mapped = theMap.apply(value);
					if(mapped != null)
						observer.onNext(mapped);
					else
						observer.onCompleted(null); // Gotta pass along the completion even if it doesn't include a value
				}

				@Override
				public void onError(Throwable e) {
					observer.onError(e);
				}
			});
		}

		@Override
		public boolean isSafe() {
			return theWrapped.isSafe();
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
		private static final Object NONE = new Object();

		private final List<Observable<?>> theComposed;

		private final Function<Object [], T> theFunction;

		private final ListenerSet<Observer<? super T>> theObservers;

		/**
		 * @param function The function that operates on the argument observables to produce this observable's value
		 * @param composed The argument observables whose values are passed to the function
		 */
		public ComposedObservable(Function<Object [], T> function, Observable<?>... composed) {
			theFunction = function;
			theComposed = java.util.Collections.unmodifiableList(java.util.Arrays.asList(composed));
			theObservers = new ListenerSet<>();
			theObservers.setUsedListener(new Consumer<Boolean>() {
				private final Subscription [] composedSubs = new Subscription[theComposed.size()];

				private final Object [] values = new Object[theComposed.size()];

				@Override
				public void accept(Boolean used) {
					if(used) {
						for(int i = 0; i < theComposed.size(); i++) {
							int index = i;
							composedSubs[i] = theComposed.get(i).subscribe(new Observer<Object>() {
								@Override
								public <V> void onNext(V value) {
									values[index] = value;
									Object next = getNext();
									if(next != NONE)
										fireNext((T) next);
								}

								@Override
								public <V> void onCompleted(V value) {
									values[index] = value;
									Object next = getNext();
									if(next != NONE)
										fireCompleted((T) next);
								}

								@Override
								public void onError(Throwable error) {
									fireError(error);
								}

								private Object getNext() {
									Object [] args = values.clone();
									for(Object value : args)
										if(value == NONE)
											return NONE;
									return theFunction.apply(args);
								}

								private void fireNext(T next) {
									theObservers.forEach(listener -> listener.onNext(next));
								}

								private void fireCompleted(T next) {
									theObservers.forEach(listener -> listener.onCompleted(next));
								}

								private void fireError(Throwable error) {
									theObservers.forEach(listener -> listener.onError(error));
								}
							});
						}
					} else {
						for(int i = 0; i < theComposed.size(); i++) {
							composedSubs[i].unsubscribe();
							composedSubs[i] = null;
							values[i] = null;
						}
					}
				}
			});
		}

		@Override
		public Subscription subscribe(Observer<? super T> observer) {
			theObservers.add(observer);
			return () -> theObservers.remove(observer);
		}

		/** @return The observables that this observable uses as sources */
		public Observable<?> [] getComposed() {
			return theComposed.toArray(new Observable[theComposed.size()]);
		}

		@Override
		public boolean isSafe() {
			return true;
		}

		@Override
		public String toString() {
			return theComposed.toString();
		}
	}

	/**
	 * Implements {@link Observable#takeUntil(Observable)}
	 *
	 * @param <T> The type of the observable
	 */
	class ObservableTakenUntil<T> implements Observable<T> {
		private final Observable<T> theWrapped;
		private final Observable<?> theUntil;
		private final boolean isTerminating;

		protected ObservableTakenUntil(Observable<T> wrap, Observable<?> until, boolean terminate) {
			theWrapped = wrap;
			theUntil = until;
			isTerminating = terminate;
		}

		protected Observable<T> getWrapped() {
			return theWrapped;
		}

		protected Observable<?> getUntil() {
			return theUntil;
		}

		protected T getDefaultValue() {
			return null;
		}

		@Override
		public Subscription subscribe(Observer<? super T> observer) {
			Subscription outerSub = theWrapped.subscribe(observer);
			boolean [] complete = new boolean[1];
			Subscription [] untilSub = new Subscription[1];
			untilSub[0] = theUntil.subscribe(new Observer<Object>() {
				@Override
				public void onNext(Object value) {
					onCompleted(value);
				}

				@Override
				public void onCompleted(Object value) {
					if(complete[0])
						return;
					complete[0] = true;
					outerSub.unsubscribe();
					if (isTerminating)
						observer.onCompleted(getDefaultValue());
				}
			});
			return () -> {
				if(complete[0])
					return;
				complete[0] = true;
				outerSub.unsubscribe();
				untilSub[0].unsubscribe();
			};
		}

		@Override
		public boolean isSafe() {
			return theWrapped.isSafe();
		}
	}

	/**
	 * Implements {@link Observable#take(int)}
	 *
	 * @param <T> The type of the observable
	 */
	class ObservableTakenTimes<T> implements Observable<T> {
		private final Observable<T> theWrapped;

		private final int theTimes;

		protected ObservableTakenTimes(Observable<T> wrap, int times) {
			theWrapped = wrap;
			theTimes = times;
		}

		protected Observable<T> getWrapped() {
			return theWrapped;
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
					if(count < theTimes)
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
					if(theCounter.get() < theTimes)
						observer.onCompleted(value);
				}

				@Override
				public void onError(Throwable e) {
					if(theCounter.get() < theTimes)
						observer.onError(e);
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

		@Override
		public boolean isSafe() {
			return theWrapped.isSafe();
		}
	}

	/**
	 * Implements {@link Observable#skip(Supplier)}
	 *
	 * @param <T> The type of the observable
	 */
	class SkippingObservable<T> implements Observable<T> {
		private final Observable<T> theWrapped;

		private final Supplier<Integer> theTimes;

		protected SkippingObservable(Observable<T> wrap, Supplier<Integer> times) {
			theWrapped = wrap;
			theTimes = times;
		}

		protected Observable<T> getWrapped() {
			return theWrapped;
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
					if(counter.get() <= 0 || counter.getAndDecrement() <= 0)
						observer.onNext(value);
				}

				@Override
				public <V extends T> void onCompleted(V value) {
					observer.onCompleted(value);
				}

				@Override
				public void onError(Throwable e) {
					if(counter.get() <= 0)
						observer.onError(e);
				}
			});
		}

		@Override
		public boolean isSafe() {
			return theWrapped.isSafe();
		}

		@Override
		public String toString() {
			return theWrapped + ".skip(" + theTimes + ")";
		}
	}

	/**
	 * Implements {@link Observable#safe()}
	 *
	 * @param <T> The type of values that the observable publishes
	 */
	class SafeObservable<T> implements Observable<T> {
		private final Observable<T> theWrapped;
		private final Lock theLock;

		protected SafeObservable(Observable<T> wrap) {
			theWrapped = wrap;
			theLock = new java.util.concurrent.locks.ReentrantLock();
		}

		protected Observable<T> getWrapped() {
			return theWrapped;
		}

		protected Lock getLock() {
			return theLock;
		}

		@Override
		public Subscription subscribe(Observer<? super T> observer) {
			return theWrapped.subscribe(new Observer<T>() {
				@Override
				public <V extends T> void onNext(V value) {
					theLock.lock();
					try {
						observer.onNext(value);
					} finally {
						theLock.unlock();
					}
				}

				@Override
				public <V extends T> void onCompleted(V value) {
					theLock.lock();
					try {
						observer.onCompleted(value);
					} finally {
						theLock.unlock();
					}
				}

				@Override
				public void onError(Throwable e) {
					theLock.lock();
					try {
						observer.onError(e);
					} finally {
						theLock.unlock();
					}
				}
			});
		}

		@Override
		public boolean isSafe() {
			return true;
		}
	}
}
