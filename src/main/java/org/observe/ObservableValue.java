package org.observe;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.observe.XformOptions.SimpleXformOptions;
import org.observe.XformOptions.XformDef;
import org.observe.collect.ObservableCollection;
import org.qommons.BiTuple;
import org.qommons.ListenerSet;
import org.qommons.Transaction;
import org.qommons.TriFunction;

import com.google.common.reflect.TypeToken;

/**
 * A value holder that can notify listeners when the value changes. The {@link #changes()} observable will always notify subscribers with an
 * {@link ObservableValueEvent#isInitial() initial} event whose old value is null and whose new value is this holder's current value before
 * the {@link Observable#subscribe(Observer)} method exits.
 *
 * @param <T> The compile-time type of this observable's value
 */
public interface ObservableValue<T> extends java.util.function.Supplier<T> {
	/** @return The run-time type of this value */
	TypeToken<T> getType();

	/** @return The current value of this observable */
	@Override
	T get();

	/**
	 * @return An observable that fires an {@link ObservableValueEvent#isInitial() initial} event for the current value and subsequent
	 *         change events when this value changes
	 */
	Observable<ObservableValueEvent<T>> changes();

	/**
	 * Locks this value for modification. Does not affect {@link #get()}, i.e. the lock is not exclusive. Only prevents modification of this
	 * value while the lock is held.
	 *
	 * @return The transaction to close to release the lock
	 */
	Transaction lock();

	/** @return An observable that just reports this observable value's value in an observable without the event */
	default Observable<T> value() {
		return changes().map(evt -> evt.getNewValue());
	}

	/**
	 * Creates an {@link ObservableValueEvent} to propagate the current value of this observable to a new subscriber
	 *
	 * @param value The current value of this observable
	 * @param cause The cause of the change
	 * @return The event to propagate
	 */
	default ObservableValueEvent<T> createInitialEvent(T value, Object cause) {
		return ObservableValueEvent.createInitialEvent(this, value, cause);
	}

	/**
	 * Creates an {@link ObservableValueEvent} to propagate a change to this observable's value
	 *
	 * @param oldVal The previous value of this observable
	 * @param newVal The new value of this observable
	 * @param cause The cause of the change
	 * @return The event to propagate
	 */
	default ObservableValueEvent<T> createChangeEvent(T oldVal, T newVal, Object cause) {
		return ObservableValueEvent.createChangeEvent(this, oldVal, newVal, cause);
	}

	/**
	 * @param value The initial value to fire the event for
	 * @param cause The cause of the initial event
	 * @param action The action to perform on the event
	 */
	default void fireInitialEvent(T value, Object cause, Consumer<? super ObservableValueEvent<T>> action) {
		ObservableValueEvent<T> evt = createInitialEvent(value, cause);
		try (Transaction t = ObservableValueEvent.use(evt)) {
			action.accept(evt);
		}
	}

	/**
	 * @param newVal The old value to fire the change event for
	 * @param oldVal The new value to fire the change event for
	 * @param cause The cause of the change
	 * @param action The action to perform on the event
	 */
	default void fireChangeEvent(T oldVal, T newVal, Object cause, Consumer<? super ObservableValueEvent<T>> action) {
		ObservableValueEvent<T> evt = createChangeEvent(oldVal, newVal, cause);
		try (Transaction t = ObservableValueEvent.use(evt)) {
			action.accept(evt);
		}
	}

	/**
	 * @param eventMap The mapping function that intercepts value events from this value and creates new, equivalent events
	 * @return An observable value identical to this one but whose change events are mapped by the given function
	 */
	default ObservableValue<T> mapEvent(Function<? super ObservableValueEvent<T>, ObservableValueEvent<T>> eventMap) {
		ObservableValue<T> outer = this;
		return new ObservableValue<T>() {
			@Override
			public TypeToken<T> getType() {
				return outer.getType();
			}

			@Override
			public T get() {
				return outer.get();
			}

			@Override
			public Observable<ObservableValueEvent<T>> changes() {
				return outer.changes().map(eventMap);
			}

			@Override
			public Transaction lock() {
				return outer.lock();
			}

			@Override
			public String toString() {
				return outer.toString();
			}
		};
	}

	/**
	 * Composes this observable into another observable that depends on this one
	 *
	 * @param <R> The type of the new observable
	 * @param function The function to apply to this observable's value
	 * @return The new observable whose value is a function of this observable's value
	 */
	default <R> ObservableValue<R> map(Function<? super T, R> function) {
		return map(null, function, options -> {});
	}

	/**
	 * Composes this observable into another observable that depends on this one
	 *
	 * @param <R> The type of the new observable
	 * @param type The run-time type of the new observable
	 * @param function The function to apply to this observable's value
	 * @return The new observable whose value is a function of this observable's value
	 */
	default <R> ObservableValue<R> map(TypeToken<R> type, Function<? super T, R> function) {
		return map(type, function, options -> {});
	}

	/**
	 * @param <R> The type of the new observable
	 * @param type The run-time type of the new observable
	 * @param function The function to apply to this observable's value
	 * @param options Options determining the behavior of the result
	 * @return The new observable whose value is a function of this observable's value
	 */
	default <R> ObservableValue<R> map(TypeToken<R> type, Function<? super T, R> function, Consumer<XformOptions> options) {
		SimpleXformOptions xform = new SimpleXformOptions();
		options.accept(xform);
		return new ComposedObservableValue<>(type, args -> {
			return function.apply((T) args[0]);
		}, new XformDef(xform), this);
	}

	/**
	 * A shortcut for {@link #flatten(ObservableValue) flatten}({@link #map(Function) mapV}(map))
	 *
	 * @param map The function producing an observable for each value from this observable
	 * @return An observable that may produce any number of values for each value from this observable
	 */
	default <R> ObservableValue<R> flatMap(Function<? super T, ? extends ObservableValue<? extends R>> map) {
		return flatten(map(map));
	}

	/**
	 * Composes this observable into another observable that depends on this one and one other
	 *
	 * @param <U> The type of the other argument observable
	 * @param <R> The type of the new observable
	 * @param function The function to apply to the values of the observables
	 * @param arg The other observable to be composed
	 * @return The new observable whose value is a function of this observable's value and the other's
	 */
	default <U, R> ObservableValue<R> combine(BiFunction<? super T, ? super U, R> function, ObservableValue<U> arg) {
		return combine(null, function, arg, options -> {});
	}

	/**
	 * Composes this observable into another observable that depends on this one and one other
	 *
	 * @param <U> The type of the other argument observable
	 * @param <R> The type of the new observable
	 * @param type The run-time type of the new observable
	 * @param function The function to apply to the values of the observables
	 * @param arg The other observable to be composed
	 * @param options Options determining the behavior of the result
	 * @return The new observable whose value is a function of this observable's value and the other's
	 */
	default <U, R> ObservableValue<R> combine(TypeToken<R> type, BiFunction<? super T, ? super U, R> function, ObservableValue<U> arg,
		Consumer<XformOptions> options) {
		SimpleXformOptions xform = new SimpleXformOptions();
		options.accept(xform);
		return new ComposedObservableValue<>(type, args -> {
			return function.apply((T) args[0], (U) args[1]);
		}, new XformDef(xform), this, arg);
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
	default <U, V, R> ObservableValue<R> combine(TriFunction<? super T, ? super U, ? super V, R> function, ObservableValue<U> arg2,
		ObservableValue<V> arg3) {
		return combine(null, function, arg2, arg3, options -> {});
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
	 * @param options Options determining the behavior of the result
	 * @return The new observable whose value is a function of this observable's value and the others'
	 */
	default <U, V, R> ObservableValue<R> combine(TypeToken<R> type, TriFunction<? super T, ? super U, ? super V, R> function,
		ObservableValue<U> arg2, ObservableValue<V> arg3, Consumer<XformOptions> options) {
		SimpleXformOptions xform = new SimpleXformOptions();
		options.accept(xform);
		return new ComposedObservableValue<>(type, args -> {
			return function.apply((T) args[0], (U) args[1], (V) args[2]);
		}, new XformDef(xform), this, arg2, arg3);
	}

	/**
	 * @param until The observable to complete the value
	 * @return An observable value identical to this one, but that will {@link Observer#onCompleted(Object) complete} when
	 *         <code>until</code> fires
	 */
	default ObservableValue<T> takeUntil(Observable<?> until) {
		return new ObservableValueTakenUntil<>(this, until, true);
	}

	/**
	 * @param until The observable to cease subscription on
	 * @return {@link #changes() changes()}.Observable{@link #unsubscribeOn(Observable) unsubscribeOn}<code>(until)</code>
	 */
	default Observable<ObservableValueEvent<T>> unsubscribeOn(Observable<?> until) {
		return changes().unsubscribeOn(until);
	}

	/**
	 * @param refresh The observer to duplicate event firing for
	 * @return An observable value that fires additional value events when the given observable fires
	 */
	default ObservableValue<T> refresh(Observable<?> refresh) {
		return new RefreshingObservableValue<>(this, refresh);
	}

	/** @return An observable identical to this, but whose {@link #changes()} observable is {@link Observable#isSafe() safe} */
	default ObservableValue<T> safe() {
		return new SafeObservableValue<>(this);
	}

	/**
	 * A shortened version of {@link #of(TypeToken, Object)}. The type of the object will be value's class. This is not always a good
	 * idea. If the variable passed to this method may have a value that is a subclass of the variable's type, there may be unintended
	 * consequences of using this method. Also, the type cannot be derived if the value is null, so an {@link IllegalArgumentException} will
	 * be thrown in this case.
	 *
	 * In general, this shorthand method should only be used if the value is a literal or a newly constructed value.
	 *
	 * @param <X> The type of the value to wrap
	 * @param value The value to wrap
	 * @return An observable that always returns the given value
	 */
	public static <X> ObservableValue<X> of(final X value) {
		if (value == null)
			throw new IllegalArgumentException("Cannot call constant(value) with a null value.  Use constant(TypeToken<X>, X).");
		return new ConstantObservableValue<>(TypeToken.of((Class<X>) value.getClass()), value);
	}

	/**
	 * @param <X> The compile-time type of the value to wrap
	 * @param type The run-time type of the value to wrap
	 * @param value The value to wrap
	 * @return An observable that always returns the given value
	 */
	public static <X> ObservableValue<X> of(final TypeToken<X> type, final X value) {
		return new ConstantObservableValue<>(type, value);
	}

	/**
	 * @param <T> The compile-time super type of all observables contained in the nested observable
	 * @param ov The nested observable
	 * @return An observable value whose value is the value of <code>ov.get()</code>
	 */
	public static <T> ObservableValue<T> flatten(ObservableValue<? extends ObservableValue<? extends T>> ov) {
		return flatten(ov, () -> null);
	}

	/**
	 * @param <T> The compile-time super type of all observables contained in the nested observable
	 * @param ov The nested observable
	 * @param defaultValue The default value supplier for when the outer observable is empty
	 * @return An observable value whose value is the value of <code>ov.get()</code>
	 */
	public static <T> ObservableValue<T> flatten(ObservableValue<? extends ObservableValue<? extends T>> ov,
		Supplier<? extends T> defaultValue) {
		return new FlattenedObservableValue<>(ov, defaultValue);
	}

	/**
	 * @param <T> The type of observables in the value
	 * @param value An observable value containing an observable
	 * @return An observable that fires whenever the current contents of the value fires. This observable will not complete until the value
	 *         completes.
	 */
	public static <T> Observable<T> flattenObservableValue(ObservableValue<? extends Observable<? extends T>> value) {
		return new Observable<T>() {
			@Override
			public Subscription subscribe(Observer<? super T> observer) {
				return value.changes().subscribe(new Observer<ObservableValueEvent<? extends Observable<? extends T>>>() {
					@Override
					public <E extends ObservableValueEvent<? extends Observable<? extends T>>> void onNext(E event) {
						if (event.getNewValue() != null) {
							event.getNewValue().takeUntil(value.changes().noInit()).subscribe(new Observer<T>() {
								@Override
								public <V extends T> void onNext(V event2) {
									observer.onNext(event2);
								}

								@Override
								public <V extends T> void onCompleted(V event2) {
									// Don't use the completed events because the contents of this observable may be replaced
								}
							});
						}
					}

					@Override
					public <E extends ObservableValueEvent<? extends Observable<? extends T>>> void onCompleted(E event) {
						observer.onCompleted(null);
					}
				});
			}

			@Override
			public boolean isSafe() {
				return false; // Can't guarantee that the contents will always be safe
			}
		};
	}

	/**
	 * Creates an observable value that reflects the value of the first value in the given sequence passing the given test, or the value
	 * given by the default if none of the values in the sequence pass. This can also be accomplished via:
	 *
	 * <code>
	 * 	{@link ObservableCollection#of(TypeToken, Object...) ObservableCollection.of(type, values)}.collect()
	 * {@link ObservableCollection#observeFind(Predicate, Supplier, boolean) .observeFind(test, ()->null, true)}
	 * {{@link #map(Function) .mapV(v->v!=null ? v : def.get()}
	 * </code>
	 *
	 * but this method only subscribes to the values in the sequence up to the one that has a passing value. This can be of great advantage
	 * if one of the earlier values is likely to pass and some of the later values are expensive to compute.
	 *
	 * @param <T> The compile-time type of the value
	 * @param type The run-time type of the value
	 * @param test The test to for the value. If null, <code>v->v!=null</code> will be used
	 * @param def Supplies a default value in the case that none of the values in the sequence pass the test. If null, a null default will
	 *        be used.
	 * @param values The sequence of ObservableValues to get the first passing value of
	 * @return The observable for the first passing value in the sequence
	 */
	public static <T> ObservableValue<T> firstValue(TypeToken<T> type, Predicate<? super T> test, Supplier<? extends T> def,
		ObservableValue<? extends T>... values) {
		return new FirstObservableValue<>(type, values, test, def);
	}

	/**
	 * Assembles an observable value, with changes occurring on the basis of changes to a set of components
	 *
	 * @param <T> The type of the value to produce
	 * @param type The type of the new value
	 * @param value The function to get the new value on demand
	 * @param components The components whose changes require a new value to be produced
	 * @return The new observable value
	 */
	public static <T> ObservableValue<T> assemble(TypeToken<T> type, Supplier<T> value, ObservableValue<?>... components) {
		return assemble(type, value, options -> {}, components);
	}

	/**
	 * Assembles an observable value, with changes occurring on the basis of changes to a set of components
	 *
	 * @param <T> The type of the value to produce
	 * @param type The type of the new value
	 * @param value The function to get the new value on demand
	 * @param options The transform options for the assembly
	 * @param components The components whose changes require a new value to be produced
	 * @return The new observable value
	 */
	public static <T> ObservableValue<T> assemble(TypeToken<T> type, Supplier<T> value, Consumer<XformOptions> options,
		ObservableValue<?>... components) {
		TypeToken<T> t = type == null ? (TypeToken<T>) TypeToken.of(value.getClass()).resolveType(Supplier.class.getTypeParameters()[0])
			: type;
		XformOptions.SimpleXformOptions opts = new XformOptions.SimpleXformOptions();
		options.accept(opts);
		return new ComposedObservableValue<>(t, c -> value.get(), new XformOptions.XformDef(opts), components);
	}

	/**
	 * An observable that depends on the values of other observables
	 *
	 * @param <T> The type of the composed observable
	 */
	public class ComposedObservableValue<T> implements ObservableValue<T> {
		private final List<ObservableValue<?>> theComposed;

		private final Function<Object[], T> theFunction;

		private final ListenerSet<Observer<? super ObservableValueEvent<T>>> theObservers;

		private final TypeToken<T> theType;

		private final XformDef theOptions;

		private T theValue;

		/**
		 * @param function The function that operates on the argument observables to produce this observable's value
		 * @param options Options determining the behavior of the observable
		 * @param composed The argument observables whose values are passed to the function
		 */
		public ComposedObservableValue(Function<Object[], T> function, XformDef options, ObservableValue<?>... composed) {
			this(null, function, options, composed);
		}

		/**
		 * @param type The type for this value
		 * @param function The function that operates on the argument observables to produce this observable's value
		 * @param options Options determining the behavior of the observable
		 * @param composed The argument observables whose values are passed to the function
		 */
		public ComposedObservableValue(TypeToken<T> type, Function<Object[], T> function, XformDef options,
			ObservableValue<?>... composed) {
			theFunction = function;
			theOptions = options;
			theType = type != null ? type
				: (TypeToken<T>) TypeToken.of(function.getClass()).resolveType(Function.class.getTypeParameters()[1]);
			theComposed = java.util.Collections.unmodifiableList(java.util.Arrays.asList(composed));
			theObservers = new ListenerSet<>();
			final Subscription[] composedSubs = new Subscription[theComposed.size()];
			boolean[] completed = new boolean[1];
			theObservers.setUsedListener(new Consumer<Boolean>() {
				@Override
				public void accept(Boolean used) {
					if (used) {
						XformOptions.XformCacheHandler<Object, T>[] caches = new XformOptions.XformCacheHandler[composed.length];
						boolean[] initialized = new boolean[composed.length];
						for (int i = 0; i < composed.length; i++) {
							int index = i;
							caches[index] = theOptions
								.createCacheHandler(new XformOptions.XformCacheHandlingInterface<Object, T>() {
									@Override
									public Function<? super Object, ? extends T> map() {
										Object[] composedValues = new Object[theComposed.size()];
										for (int j = 0; j < composed.length; j++) {
											if (j != index)
												composedValues[j] = theOptions.isCached() ? caches[j].getSourceCache() : composed[j].get();
										}
										return src -> {
											composedValues[index] = src;
											return combine(composedValues);
										};
									}

									@Override
									public Transaction lock() {
										return ComposedObservableValue.this.lock();
									}

									@Override
									public T getDestCache() {
										return theValue;
									}

									@Override
									public void setDestCache(T value) {
										theValue = value;
									}
								});
							composedSubs[i] = theComposed.get(i).changes().subscribe(new Observer<ObservableValueEvent<?>>() {
								@Override
								public <V extends ObservableValueEvent<?>> void onNext(V event) {
									if (event.isInitial()) {
										initialized[index] = true;
										caches[index].initialize(event.getNewValue());
										return;
									} else if (!isInitialized())
										caches[index].initialize(event.getNewValue());
									BiTuple<T, T> change = caches[index].handleChange(event.getOldValue(), event.getNewValue());
									if (change != null) {
										ObservableValueEvent<T> toFire = ComposedObservableValue.this.createChangeEvent(change.getValue1(),
											change.getValue2(), event);
										fireNext(toFire);
									}
								}

								private boolean isInitialized() {
									for (boolean b : initialized)
										if (!b)
											return false;
									return true;
								}

								@Override
								public <V extends ObservableValueEvent<?>> void onCompleted(V event) {
									completed[0] = true;
									if (!isInitialized()) {
										caches[index].initialize(event.getNewValue());
										return;
									}
									BiTuple<T, T> change = caches[index].handleChange(event.getOldValue(), event.getNewValue());
									if (change == null) {
										T value = combineCache(caches, index, event.getNewValue());
										change = new BiTuple<>(value, value);
									}
									ObservableValueEvent<T> toFire = createChangeEvent(change.getValue1(), change.getValue2(), event);
									fireCompleted(toFire);
								}

								private void fireNext(ObservableValueEvent<T> next) {
									try (Transaction t = ObservableValueEvent.use(next)) {
										theObservers.forEach(listener -> listener.onNext(next));
									}
								}

								private void fireCompleted(ObservableValueEvent<T> next) {
									try (Transaction t = ObservableValueEvent.use(next)) {
										theObservers.forEach(listener -> listener.onCompleted(next));
										Subscription.forAll(composedSubs).unsubscribe();
									}
								}
							});
							if (completed[0])
								break;
						}
						for (int i = 0; i < composed.length; i++)
							if (!initialized[i])
								throw new IllegalStateException(theComposed.get(i) + " did not fire an initial value");
						if (!completed[0] && theOptions.isCached())
							theValue = combineCache(caches, -1, null);
						// initialized[0] = true;
					} else {
						theValue = null;
						for (int i = 0; i < theComposed.size(); i++) {
							if (composedSubs[i] != null) {
								composedSubs[i].unsubscribe();
								composedSubs[i] = null;
							}
						}
						completed[0] = false;
					}
				}

				private T combineCache(XformOptions.XformCacheHandler<Object, T>[] caches, int valueIdx, Object value) {
					Object[] composedValues = new Object[theComposed.size()];
					for (int j = 0; j < composed.length; j++) {
						if (j == valueIdx)
							composedValues[j] = value;
						else
							composedValues[j] = theOptions.isCached() ? caches[j].getSourceCache() : composed[j].get();
					}
					return combine(composedValues);
				}
			});
			theObservers.setOnSubscribe(observer -> {
				ObservableValueEvent<T> evt = createInitialEvent(theValue, null);
				try (Transaction t = ObservableValueEvent.use(evt)) {
					if (completed[0])
						observer.onCompleted(evt);
					else
						observer.onNext(evt);
				}
			});
		}

		@Override
		public TypeToken<T> getType() {
			return theType;
		}

		/** @return The observable values that compose this value */
		public ObservableValue<?>[] getComposed() {
			return theComposed.toArray(new ObservableValue[theComposed.size()]);
		}

		/** @return The function used to map this observable's composed values into its return value */
		public Function<Object[], T> getFunction() {
			return theFunction;
		}

		/** @return Options that determine the behavior of this value */
		public XformDef getOptions() {
			return theOptions;
		}

		@Override
		public T get() {
			if (theOptions.isCached() && theObservers.isUsed())
				return theValue;
			else {
				Object[] composed = new Object[theComposed.size()];
				for (int i = 0; i < composed.length; i++)
					composed[i] = theComposed.get(i).get();
				return combine(composed);
			}
		}

		/**
		 * @param args The arguments to combine
		 * @return The combined value
		 */
		protected T combine(Object[] args) {
			return theFunction.apply(args.clone());
		}

		@Override
		public Observable<ObservableValueEvent<T>> changes() {
			return new Observable<ObservableValueEvent<T>>() {
				@Override
				public Subscription subscribe(Observer<? super ObservableValueEvent<T>> observer) {
					theObservers.add(observer);
					return () -> theObservers.remove(observer);
				}

				@Override
				public boolean isSafe() {
					return true;
				}
			};
		}

		@Override
		public Transaction lock() {
			Transaction[] locks = new Transaction[theComposed.size()];
			for (int i = 0; i < locks.length; i++)
				locks[i] = theComposed.get(i).lock();
			return () -> {
				for (int i = 0; i < locks.length; i++)
					locks[i].close();
			};
		}

		@Override
		public String toString() {
			return theComposed.toString();
		}
	}

	/**
	 * Implements {@link ObservableValue#takeUntil(Observable)}
	 *
	 * @param <T> The type of the value
	 */
	class ObservableValueTakenUntil<T> implements ObservableValue<T> {
		private final ObservableValue<T> theWrapped;
		private final Observable<ObservableValueEvent<T>> theChanges;

		protected ObservableValueTakenUntil(ObservableValue<T> wrap, Observable<?> until, boolean terminate) {
			theWrapped = wrap;
			theChanges = new Observable.ObservableTakenUntil<>(theWrapped.changes(), until, terminate, () -> {
				T value = theWrapped.get();
				return theWrapped.createChangeEvent(value, value, null);
			});
		}

		protected ObservableValue<T> getWrapped() {
			return theWrapped;
		}

		@Override
		public TypeToken<T> getType() {
			return theWrapped.getType();
		}

		@Override
		public T get() {
			return theWrapped.get();
		}

		@Override
		public Observable<ObservableValueEvent<T>> changes() {
			return theChanges;
		}

		@Override
		public Transaction lock() {
			return theWrapped.lock();
		}
	}

	/**
	 * Implements {@link ObservableValue#refresh(Observable)}
	 *
	 * @param <T> The type of the value
	 */
	class RefreshingObservableValue<T> implements ObservableValue<T> {
		private final ObservableValue<T> theWrapped;
		private final Observable<?> theRefresh;

		protected RefreshingObservableValue(ObservableValue<T> wrap, Observable<?> refresh) {
			theWrapped = wrap;
			theRefresh = refresh;
		}

		protected ObservableValue<T> getWrapped() {
			return theWrapped;
		}

		protected Observable<?> getRefresh() {
			return theRefresh;
		}

		@Override
		public TypeToken<T> getType() {
			return theWrapped.getType();
		}

		@Override
		public T get() {
			return theWrapped.get();
		}

		@Override
		public Observable<ObservableValueEvent<T>> changes() {
			return new Observable<ObservableValueEvent<T>>() {
				@Override
				public Subscription subscribe(Observer<? super ObservableValueEvent<T>> observer) {
					Subscription[] refireSub = new Subscription[1];
					boolean[] completed = new boolean[1];
					Subscription outerSub = theWrapped.changes().subscribe(new Observer<ObservableValueEvent<T>>() {
						@Override
						public <V extends ObservableValueEvent<T>> void onNext(V value) {
							observer.onNext(value);
						}

						@Override
						public <V extends ObservableValueEvent<T>> void onCompleted(V value) {
							observer.onCompleted(value);
							if (refireSub[0] != null) {
								refireSub[0].unsubscribe();
								refireSub[0] = null;
							}
							completed[0] = true;
						}
					});
					if (completed[0]) {
						return () -> {};
					}
					refireSub[0] = theRefresh.act(evt -> {
						T value = get();
						ObservableValueEvent<T> evt2 = createChangeEvent(value, value, evt);
						try (Transaction t = ObservableValueEvent.use(evt2)) {
							observer.onNext(evt2);
						}
					});
					return () -> {
						outerSub.unsubscribe();
						if (refireSub[0] != null)
							refireSub[0].unsubscribe();
					};
				}

				@Override
				public boolean isSafe() {
					return false;
				}
			};
		}

		@Override
		public Transaction lock() {
			return theWrapped.lock();
		}

		@Override
		public String toString() {
			return theWrapped.toString();
		}
	}

	/**
	 * An observable value whose value cannot change
	 *
	 * @param <T> The type of this value
	 */
	class ConstantObservableValue<T> implements ObservableValue<T> {
		private final TypeToken<T> theType;
		private final T theValue;

		/**
		 * @param type The type of this observable value
		 * @param value This observable value's value
		 */
		public ConstantObservableValue(TypeToken<T> type, T value) {
			theType = type;
			theValue = (T) type.wrap().getRawType().cast(value);
		}

		@Override
		public Observable<ObservableValueEvent<T>> changes() {
			return new Observable<ObservableValueEvent<T>>() {
				@Override
				public Subscription subscribe(Observer<? super ObservableValueEvent<T>> observer) {
					ObservableValueEvent<T> evt = createInitialEvent(theValue, null);
					try (Transaction t = ObservableValueEvent.use(evt)) {
						observer.onNext(evt);
					}
					return () -> {};
				}

				@Override
				public boolean isSafe() {
					return true;
				}
			};
		}

		@Override
		public TypeToken<T> getType() {
			return theType;
		}

		@Override
		public T get() {
			return theValue;
		}

		@Override
		public Transaction lock() {
			return Transaction.NONE;
		}

		@Override
		public String toString() {
			return "" + theValue;
		}
	}

	/**
	 * Implements {@link ObservableValue#safe()}
	 *
	 * @param <T> The type of the value
	 */
	class SafeObservableValue<T> implements ObservableValue<T> {
		private final ObservableValue<T> theWrapped;

		public SafeObservableValue(ObservableValue<T> wrap) {
			theWrapped = wrap;
		}

		protected ObservableValue<T> getWrapped() {
			return theWrapped;
		}

		@Override
		public TypeToken<T> getType() {
			return theWrapped.getType();
		}

		@Override
		public T get() {
			return theWrapped.get();
		}

		@Override
		public Observable<ObservableValueEvent<T>> changes() {
			return theWrapped.changes().safe();
		}

		@Override
		public Transaction lock() {
			return theWrapped.lock();
		}

		@Override
		public ObservableValue<T> safe() {
			return this;
		}
	}

	/**
	 * Implements {@link ObservableValue#flatten(ObservableValue)}
	 *
	 * @param <T> The type of the value
	 */
	class FlattenedObservableValue<T> implements ObservableValue<T> {
		private final ObservableValue<? extends ObservableValue<? extends T>> theValue;
		private final TypeToken<T> theType;
		private final Supplier<? extends T> theDefaultValue;

		protected FlattenedObservableValue(ObservableValue<? extends ObservableValue<? extends T>> value,
			Supplier<? extends T> defaultValue) {
			if (value == null)
				throw new NullPointerException("Null observable");
			theValue = value;
			theType = (TypeToken<T>) value.getType().resolveType(ObservableValue.class.getTypeParameters()[0]);
			theDefaultValue = defaultValue;
		}

		protected ObservableValue<? extends ObservableValue<? extends T>> getWrapped() {
			return theValue;
		}

		@Override
		public TypeToken<T> getType() {
			if (theType != null)
				return theType;
			ObservableValue<? extends T> outerVal = theValue.get();
			if (outerVal == null)
				throw new IllegalStateException("Flattened observable is null and no type given: " + theValue);
			return (TypeToken<T>) outerVal.getType();
		}

		/** @return The type of the currently held observable */
		public TypeToken<? extends T> getDeepType() {
			ObservableValue<? extends T> inner = theValue.get();
			if (inner == null)
				return getType();
			else if (inner instanceof FlattenedObservableValue)
				return ((FlattenedObservableValue<? extends T>) inner).getDeepType();
			else
				return inner.getType();
		}

		/** @return The supplier of the default value, in case the outer observable is empty */
		protected Supplier<? extends T> getDefaultValue() {
			return theDefaultValue;
		}

		@Override
		public T get() {
			return get(theValue.get());
		}

		private T get(ObservableValue<? extends T> value) {
			return value != null ? value.get() : theDefaultValue.get();
		}

		@Override
		public Observable<ObservableValueEvent<T>> changes() {
			return new Observable<ObservableValueEvent<T>>() {
				@Override
				public Subscription subscribe(Observer<? super ObservableValueEvent<T>> observer) {
					ObservableValue<T> retObs = FlattenedObservableValue.this;
					AtomicReference<Subscription> innerSub = new AtomicReference<>();
					boolean[] firedInit = new boolean[1];
					Subscription outerSub = theValue.changes()
						.subscribe(new Observer<ObservableValueEvent<? extends ObservableValue<? extends T>>>() {
							private final ReentrantLock theLock = new ReentrantLock();

							@Override
							public <V extends ObservableValueEvent<? extends ObservableValue<? extends T>>> void onNext(V event) {
								firedInit[0] = true;
								theLock.lock();
								try {
									final ObservableValue<? extends T> innerObs = event.getNewValue();
									// Shouldn't have 2 inner observables potentially generating events at the same time
									if (!Objects.equals(innerObs, event.getOldValue()))
										Subscription.unsubscribe(innerSub.getAndSet(null));
									Object[] old = new Object[1];
									if (innerObs != null && !innerObs.equals(event.getOldValue())) {
										boolean[] firedInit2 = new boolean[1];
										Subscription.unsubscribe(innerSub
											.getAndSet(innerObs.changes().subscribe(new Observer<ObservableValueEvent<? extends T>>() {
												@Override
												public <V2 extends ObservableValueEvent<? extends T>> void onNext(V2 event2) {
													firedInit2[0] = true;
													theLock.lock();
													try {
														T innerOld;
														if (event2.isInitial())
															innerOld = (T) old[0];
														else
															old[0] = innerOld = event2.getOldValue();
														ObservableValueEvent<T> toFire;
														if (event.isInitial() && event2.isInitial())
															toFire = retObs.createInitialEvent(event2.getNewValue(), event2.getCause());
														else
															toFire = retObs.createChangeEvent(innerOld, event2.getNewValue(),
																event2.getCause());
														try (Transaction t = ObservableValueEvent.use(toFire)) {
															observer.onNext(toFire);
														}
													} finally {
														theLock.unlock();
													}
												}

												@Override
												public <V2 extends ObservableValueEvent<? extends T>> void onCompleted(V2 value) {}
											})));
										if (!firedInit2[0])
											throw new IllegalStateException(innerObs + " did not fire an initial value");
									} else {
										T newValue = get(event.getNewValue());
										ObservableValueEvent<T> toFire;
										if (event.isInitial())
											toFire = retObs.createInitialEvent(newValue, event.getCause());
										else
											toFire = retObs.createChangeEvent((T) old[0], newValue, event.getCause());
										old[0] = newValue;
										try (Transaction t = ObservableValueEvent.use(toFire)) {
											observer.onNext(toFire);
										}
									}
								} finally {
									theLock.unlock();
								}
							}

							@Override
							public <V extends ObservableValueEvent<? extends ObservableValue<? extends T>>> void onCompleted(V event) {
								firedInit[0] = true;
								Subscription.unsubscribe(innerSub.getAndSet(null));
								theLock.lock();
								try {
									ObservableValueEvent<T> toFire = retObs.createChangeEvent(get(event.getOldValue()),
										get(event.getNewValue()), event.getCause());
									try (Transaction t = ObservableValueEvent.use(toFire)) {
										observer.onCompleted(toFire);
									}
								} finally {
									theLock.unlock();
								}
							}
						});
					if (!firedInit[0])
						throw new IllegalStateException(theValue + " did not fire an initial value");
					return () -> {
						outerSub.unsubscribe();
						Subscription.unsubscribe(innerSub.getAndSet(null));
					};
				}

				@Override
				public boolean isSafe() {
					return false;
				}
			};
		}

		@Override
		public Transaction lock() {
			Transaction outerLock = theValue.lock();
			ObservableValue<? extends T> inner = theValue.get();
			Transaction innerLock = inner == null ? Transaction.NONE : inner.lock();
			return () -> {
				innerLock.close();
				outerLock.close();
			};
		}

		@Override
		public String toString() {
			return "flat(" + theValue + ")";
		}
	}

	/**
	 * Implements {@link ObservableValue#firstValue(TypeToken, Predicate, Supplier, ObservableValue...)}
	 *
	 * @param <T> The type of the value
	 */
	class FirstObservableValue<T> implements ObservableValue<T> {
		private final TypeToken<T> theType;
		private final ObservableValue<? extends T>[] theValues;
		private final Predicate<? super T> theTest;
		private final Supplier<? extends T> theDefault;

		protected FirstObservableValue(TypeToken<T> type, ObservableValue<? extends T>[] values, Predicate<? super T> test,
			Supplier<? extends T> def) {
			theType = type;
			theValues = values;
			theTest = test == null ? v -> v != null : test;
			theDefault = def == null ? () -> null : def;
		}

		@Override
		public TypeToken<T> getType() {
			return theType;
		}

		@Override
		public T get() {
			T value = null;
			for (ObservableValue<? extends T> v : theValues) {
				value = v.get();
				if (theTest.test(value))
					break;
			}
			return value;
		}

		@Override
		public Observable<ObservableValueEvent<T>> changes() {
			return new Observable<ObservableValueEvent<T>>() {
				@Override
				public Subscription subscribe(Observer<? super ObservableValueEvent<T>> observer) {
					if (theValues.length == 0) {
						ObservableValueEvent<T> evt = createInitialEvent(null, null);
						try (Transaction t = ObservableValueEvent.use(evt)) {
							observer.onNext(evt);
						}
						return () -> {};
					}
					Subscription[] valueSubs = new Subscription[theValues.length];
					boolean[] finished = new boolean[theValues.length];
					Object[] lastValue = new Object[1];
					boolean[] hasFiredInit = new boolean[1];
					Lock lock = new ReentrantLock();
					class ElementFirstObserver implements Observer<ObservableValueEvent<? extends T>> {
						private final int index;
						private boolean isFound;

						ElementFirstObserver(int idx) {
							index = idx;
						}

						@Override
						public <V extends ObservableValueEvent<? extends T>> void onNext(V event) {
							event(event, false);
						}

						@Override
						public <V extends ObservableValueEvent<? extends T>> void onCompleted(V event) {
							event(event, true);
						}

						private void event(ObservableValueEvent<? extends T> event, boolean complete) {
							lock.lock();
							try {
								boolean found = !complete && theTest.test(event.getNewValue());
								int nextIndex = index + 1;
								if (!found) {
									while (nextIndex < theValues.length && finished[nextIndex])
										nextIndex++;
								}
								T oldValue = (T) lastValue[0];
								ObservableValueEvent<T> toFire;
								if (complete) {
									finished[index] = true;
									valueSubs[index] = null;
									if (allCompleted())
										toFire = new ObservableValueEvent<>(getType(), !hasFiredInit[0], oldValue, oldValue, event);
									else
										toFire = null;
								} else {
									if (found) {
										lastValue[0] = event.getNewValue();
										toFire = new ObservableValueEvent<>(getType(), !hasFiredInit[0], oldValue, event.getNewValue(),
											event);
									} else if (nextIndex == theValues.length)
										toFire = new ObservableValueEvent<>(getType(), !hasFiredInit[0], oldValue, theDefault.get(), event);
									else
										toFire = null;
								}
								if (toFire != null) {
									hasFiredInit[0] = true;
									try (Transaction t = ObservableValueEvent.use(toFire)) {
										observer.onNext(toFire);
									}
								}
								if (found != isFound) {
									isFound = found;
									if (found) {
										for (int i = index + 1; i < valueSubs.length; i++) {
											if (valueSubs[i] != null) {
												valueSubs[i].unsubscribe();
												valueSubs[i] = null;
											}
										}
									} else if (nextIndex < theValues.length)
										valueSubs[nextIndex] = theValues[nextIndex].changes()
										.subscribe(new ElementFirstObserver(nextIndex));
								} else if (!hasFiredInit[0])
									valueSubs[nextIndex] = theValues[nextIndex].changes().subscribe(new ElementFirstObserver(nextIndex));
							} finally {
								lock.unlock();
							}
						}

						private boolean allCompleted() {
							for (boolean f : finished)
								if (f)
									return false;
							return true;
						}
					}
					valueSubs[0] = theValues[0].safe().changes().subscribe(new ElementFirstObserver(0));
					return () -> {
						for (int i = 0; i < valueSubs.length; i++) {
							if (valueSubs[i] != null) {
								valueSubs[i].unsubscribe();
								valueSubs[i] = null;
							}
						}
					};
				}

				@Override
				public boolean isSafe() {
					return true;
				}
			};
		}

		@Override
		public Transaction lock() {
			Transaction[] locks = new Transaction[theValues.length];
			for (int i = 0; i < locks.length; i++)
				locks[i] = theValues[i].lock();
			return () -> {
				for (int i = 0; i < locks.length; i++)
					locks[i].close();
			};
		}
	}
}
