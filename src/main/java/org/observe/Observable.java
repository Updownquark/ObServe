package org.observe;

import static org.observe.ObservableDebug.debug;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A stream of values that can be filtered, mapped, composed, etc. and evaluated on
 *
 * @param <T> The type of values this observable provides
 */
public interface Observable<T> {
	/**
	 * Heavier-weight than {@link #observe(Observer)}, but supports subscription chaining. The Subscription returned from this method can be
	 * used in place of this observable; observers added to it will receive the same events as if they were added to this observable
	 * directly. However, when the subscription is {@link Subscription#unsubscribe() unsubscribed}, observers added to the subscription will
	 * have their {@link Observer#onCompleted(Object) onCompleted} method called and their notifications will cease.
	 *
	 * @param observer The observer to listen to this observable
	 * @return A subscription to use to unsubscribe the listener from this observable
	 */
	default Subscription<T> subscribe(Observer<? super T> observer) {
		Observable<T> outer = this;
		class SubscriptionHolder {
			boolean alive = true;
			Subscription<T> subscription;
			Runnable internalSub;
			Observer<T> wrapper;
			private java.util.concurrent.CopyOnWriteArrayList<Runnable> subSubscriptions;
		}
		SubscriptionHolder holder = new SubscriptionHolder();
		holder.wrapper = new Observer<T>() {
			@Override
			public <V extends T> void onNext(V value) {
				observer.onNext(value);
			}

			@Override
			public <V extends T> void onCompleted(V value) {
				if(!holder.alive)
					return;
				try {
					// if(holder.subscription != null) //Looks like this can only cause double-unsubscribe errors in correct code
					// holder.subscription.unsubscribe();
					observer.onCompleted(value);
				} finally {
					holder.alive = false;
				}
			}

			@Override
			public void onError(Throwable e) {
				observer.onError(e);
			}
		};
		if(!holder.alive)
			return new Subscription<T>() {
			@Override
			public Runnable observe(Observer<? super T> observer2) {
				observer2.onCompleted(null);
				return () -> {
				};
			}

			@Override
			public void unsubscribe() {
			}
		};
		holder.subscription = new Subscription<T>() {
			@Override
			public Runnable observe(Observer<? super T> observer2) {
				if(!holder.alive) {
					observer2.onCompleted(null);
					return () -> {
					};
				}
				Runnable internalSubSub = outer.observe(observer2);
				if(holder.subSubscriptions == null)
					holder.subSubscriptions = new java.util.concurrent.CopyOnWriteArrayList<>();
				holder.subSubscriptions.add(internalSubSub);
				return internalSubSub;
			}

			@Override
			public void unsubscribe() {
				try {
					if(holder.internalSub != null)
						holder.internalSub.run();
					if(!holder.alive)
						return;
					if(holder.subSubscriptions != null)
						for(Runnable subSub : holder.subSubscriptions)
							subSub.run();
					holder.subSubscriptions = null;
				} finally {
					holder.alive = false;
				}
			}
		};
		holder.internalSub = observe(holder.wrapper);
		if(holder.internalSub == null)
			throw new NullPointerException();
		return debug(holder.subscription).from("subscribe", this).tag("observer", observer).get();
	}

	/**
	 * Adds the observer to the list of listeners to be notified of values. The Runnable returned from this observable is lighter-weight
	 * than the {@link Subscription} object returned by {@link #subscribe(Observer)}, but doesn't facilitate subscription chaining.
	 *
	 * @param observer The observer to be notified when new values are available from this observable
	 * @return A runnable that, when invoked, will cease notifications to the observer
	 */
	Runnable observe(Observer<? super T> observer);

	/**
	 * @param action The action to perform for each new value
	 * @return The subscription for the action
	 */
	default Subscription<T> act(Action<? super T> action) {
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
		return debug(new Observable<Throwable>() {
			@Override
			public Runnable observe(Observer<? super Throwable> observer) {
				return outer.observe(new ErrorObserver(observer));
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
		return debug(new Observable<T>() {
			@Override
			public Runnable observe(Observer<? super T> observer) {
				return outer.observe(new CompleteObserver(observer));
			}
		}).from("completed", outer).get();
	}

	/**
	 * @return An observable that returns the same values as this one except that any initialization events (for cold observables) will be
	 *         ignored.
	 */
	default Observable<T> noInit() {
		Observable<T> outer = this;
		return debug(new Observable<T>() {
			@Override
			public Runnable observe(Observer<? super T> observer) {
				boolean [] initialized = new boolean[1];
				Runnable ret = outer.observe(new Observer<T>() {
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
		}).from("noInit", outer).get();
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
		return debug(new ComposedObservable<R>(args -> {
			return func.apply((T) args[0]);
		}, this)).from("mapped", this).using("map", func).get();
	}

	/**
	 * @param <R> The type of the returned observable
	 * @param func The filter map function
	 * @return An observable that provides the values of this observable, mapped by the given function, except where that function returns
	 *         null
	 */
	default <R> Observable<R> filterMap(Function<? super T, R> func) {
		Observable<T> outer = this;
		return debug(new Observable<R>() {
			@Override
			public Runnable observe(Observer<? super R> observer) {
				return outer.observe(new Observer<T>() {
					@Override
					public <V extends T> void onNext(V value) {
						R mapped = func.apply(value);
						if(mapped != null)
							observer.onNext(mapped);
					}

					@Override
					public <V extends T> void onCompleted(V value) {
						R mapped = func.apply(value);
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
			public String toString() {
				return "filterMap(" + outer + ")";
			}
		}).from("filterMap", this).using("map", func).get();
	}

	/**
	 * @param <V> The type of the other observable to be combined with this one
	 * @param <R> The type of the returned observable
	 * @param other The other observable to compose
	 * @param func The function to use to combine the observables' values
	 * @return A new observable whose values are the specified combination of this observable and the others'
	 */
	default <V, R> Observable<R> combine(Observable<V> other, BiFunction<? super T, ? super V, R> func) {
		return debug(new ComposedObservable<R>(args -> {
			return func.apply((T) args[0], (V) args[1]);
		}, this, other)).from("combine-arg0", this).from("combine-arg1", other).using("combination", func).get();
	}

	/**
	 * @param until The observable to watch
	 * @return An observable that provides the same values as this observable until the first value is observed from the given observable
	 */
	default Observable<T> takeUntil(Observable<?> until) {
		Observable<T> outer = this;
		return debug(new Observable<T>() {
			@Override
			public Runnable observe(Observer<? super T> observer) {
				Runnable outerSub = outer.observe(observer);
				boolean [] complete = new boolean[1];
				Runnable [] untilSub = new Runnable[1];
				untilSub[0] = until.observe(new Observer<Object>() {
					@Override
					public void onNext(Object value) {
						onCompleted(value);
					}

					@Override
					public void onCompleted(Object value) {
						if(complete[0])
							return;
						complete[0] = true;
						outerSub.run();
						observer.onCompleted(null);
					}
				});
				return () -> {
					if(complete[0])
						return;
					complete[0] = true;
					outerSub.run();
					untilSub[0].run();
				};
			}
		}).from("take", this).from("until", until).get();
	}

	/**
	 * @param times The number of values to take from this observable
	 * @return An observable that provides the same values as this observable but completes after the given number of values
	 */
	default Observable<T> take(int times) {
		Observable<T> outer = this;
		return debug(new Observable<T>() {
			@Override
			public Runnable observe(Observer<? super T> observer) {
				AtomicInteger counter = new AtomicInteger(times);
				return outer.observe(new Observer<T>() {
					@Override
					public <V extends T> void onNext(V value) {
						int count = counter.decrementAndGet();
						if(count >= 0)
							observer.onNext(value);
						if(count == 0)
							observer.onCompleted(value);
					}

					@Override
					public <V extends T> void onCompleted(V value) {
						if(counter.get() > 0)
							observer.onCompleted(value);
					}

					@Override
					public void onError(Throwable e) {
						if(counter.get() > 0)
							observer.onError(e);
					}
				});
			}
		}).from("take", this).tag("times", times).get();
	}

	/**
	 * @param times The number of values to skip from this observable
	 * @return An observable that provides the same values as this observable but ignores the first {@code times} values
	 */
	default Observable<T> skip(int times) {
		Observable<T> outer = this;
		return debug(new Observable<T>() {
			@Override
			public Runnable observe(Observer<? super T> observer) {
				return outer.observe(new Observer<T>() {
					private final AtomicInteger counter = new AtomicInteger(times);

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
			public String toString() {
				return outer + ".skip(" + times + ")";
			}
		}).from("skip", this).tag("times", times).get();
	}

	/**
	 * Like {@link #skip(int)}, but the number of times to skip is retrieved when the observable is subscribed to instead of when it is
	 * created.
	 *
	 * @param times A supplier that returns the number of values to skip from this observable.
	 * @return An observable that provides the same values as this observable but ignores the first {@code times} values
	 */
	default Observable<T> skip(java.util.function.Supplier<Integer> times) {
		Observable<T> outer = this;
		return debug(new Observable<T>() {
			@Override
			public Runnable observe(Observer<? super T> observer) {
				return outer.observe(new Observer<T>() {
					private final AtomicInteger counter = new AtomicInteger(times.get());

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
			public String toString() {
				return outer + ".skip(" + times + ")";
			}
		}).from("skip", this).using("times", times).get();
	}

	/**
	 * @param <V> The super-type of all observables to or
	 * @param obs The observables to combine
	 * @return An observable that pushes a value each time any of the given observables pushes a value
	 */
	public static <V> Observable<V> or(Observable<? extends V>... obs) {
		return debug(new Observable<V>() {
			@Override
			public Runnable observe(Observer<? super V> observer) {
				Runnable [] subs = new Runnable[obs.length];
				for(int i = 0; i < subs.length; i++)
					subs[i] = obs[i].observe(new Observer<V>() {
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
					for(Runnable sub : subs)
						sub.run();
				};
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

	/** An empty observable that never does anything */
	public static Observable<?> empty = new Observable<Object>() {
		@Override
		public Runnable observe(Observer<? super Object> observer) {
			return () -> {
			};
		}
	};

	/**
	 * @param <T> The type of the observable to create
	 * @param value The value for the observable
	 * @return An observable that pushes the given value as soon as it is subscribed to and never completes
	 */
	public static <T> Observable<T> constant(T value) {
		return debug(new Observable<T>() {
			@Override
			public Runnable observe(Observer<? super T> observer) {
				observer.onNext(value);
				return () -> {
				};
			}
		}).tag("constant", value).get();
	}
}
