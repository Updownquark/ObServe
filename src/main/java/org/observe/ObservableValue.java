package org.observe;

import static org.observe.ObservableDebug.d;

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

import org.observe.collect.ObservableList;
import org.observe.collect.ObservableOrderedCollection;
import org.qommons.BiTuple;
import org.qommons.ListenerSet;
import org.qommons.TriFunction;
import org.qommons.TriTuple;

import com.google.common.reflect.TypeToken;

/**
 * A value holder that can notify listeners when the value changes. This type of observable will always notify subscribers with an event
 * whose old value is null and whose new value is this holder's current value before the {@link #subscribe(Observer)} method exits.
 *
 * @param <T> The compile-time type of this observable's value
 */
public interface ObservableValue<T> extends Observable<ObservableValueEvent<T>>, java.util.function.Supplier<T> {
	/** @return The type of value this observable contains. May be null if this observable's value is always null. */
	TypeToken<T> getType();

	/** @return The current value of this observable */
	@Override
	T get();

	/** @return An observable that just reports this observable value's value in an observable without the event */
	default Observable<T> value() {
		return d().debug(new Observable<T>() {
			@Override
			public Subscription subscribe(Observer<? super T> observer) {
				return ObservableValue.this.subscribe(new Observer<ObservableValueEvent<T>>() {
					@Override
					public <V extends ObservableValueEvent<T>> void onNext(V value) {
						observer.onNext(value.getValue());
					}

					@Override
					public <V extends ObservableValueEvent<T>> void onCompleted(V value) {
						observer.onCompleted(value.getValue());
					}
				});
			}

			@Override
			public boolean isSafe() {
				return ObservableValue.this.isSafe();
			}

			@Override
			public String toString() {
				return ObservableValue.this.toString() + ".value()";
			}
		}).from("value", this).get();
	}

	/** @return A cached version of this value, which */
	default ObservableValue<T> cached() {
		return d().debug(new CachedObservableValue<>(this)).from("cached", this).get();
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
	 * @param eventMap The mapping function that intercepts value events from this value and creates new, equivalent events
	 * @return An observable value identical to this one but whose change events are mapped by the given function
	 */
	default ObservableValue<T> mapEvent(Function<? super ObservableValueEvent<T>, ? extends ObservableValueEvent<T>> eventMap) {
		ObservableValue<T> outer = this;
		return d().debug(new ObservableValue<T>() {
			@Override
			public TypeToken<T> getType() {
				return outer.getType();
			}

			@Override
			public T get() {
				return outer.get();
			}

			@Override
			public Subscription subscribe(Observer<? super ObservableValueEvent<T>> observer) {
				return outer.map(eventMap).subscribe(observer);
			}

			@Override
			public boolean isSafe() {
				return outer.isSafe();
			}

			@Override
			public String toString() {
				return outer.toString();
			}
		}).from("mapEvent", this).get();
	}

	/**
	 * Composes this observable into another observable that depends on this one
	 *
	 * @param <R> The type of the new observable
	 * @param function The function to apply to this observable's value
	 * @return The new observable whose value is a function of this observable's value
	 */
	default <R> ObservableValue<R> mapV(Function<? super T, R> function) {
		return mapV(null, function);
	};

	/**
	 * Composes this observable into another observable that depends on this one
	 *
	 * @param <R> The type of the new observable
	 * @param function The function to apply to this observable's value
	 * @param filterNull Whether to apply the filter to null values or simply preserve the null
	 * @return The new observable whose value is a function of this observable's value
	 */
	default <R> ObservableValue<R> mapV(Function<? super T, R> function, boolean filterNull) {
		return mapV(null, function, filterNull);
	};

	/**
	 * Composes this observable into another observable that depends on this one
	 *
	 * @param <R> The type of the new observable
	 * @param type The run-time type of the new observable
	 * @param function The function to apply to this observable's value
	 * @return The new observable whose value is a function of this observable's value
	 */
	default <R> ObservableValue<R> mapV(TypeToken<R> type, Function<? super T, R> function) {
		return mapV(type, function, false);
	}

	/**
	 * Composes this observable into another observable that depends on this one
	 *
	 * @param <R> The type of the new observable
	 * @param type The run-time type of the new observable
	 * @param function The function to apply to this observable's value
	 * @param filterNull Whether to apply the filter to null values or simply preserve the null
	 * @return The new observable whose value is a function of this observable's value
	 */
	default <R> ObservableValue<R> mapV(TypeToken<R> type, Function<? super T, R> function, boolean filterNull) {
		ComposedObservableValue<R> ret = new ComposedObservableValue<>(type, d().lambda(args -> {
			return function.apply((T) args[0]);
		} , "mapV"), filterNull, this);
		return d().debug(ret).from("map", this).using("map", function).tag("filterNull", filterNull).get();
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
	default <U, R> ObservableValue<R> combineV(BiFunction<? super T, ? super U, R> function, ObservableValue<U> arg) {
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
	 * @param combineNull Whether to apply the combination function if the arguments are null. If false and any arguments are null, the
	 *            result will be null.
	 * @return The new observable whose value is a function of this observable's value and the other's
	 */
	default <U, R> ObservableValue<R> combineV(TypeToken<R> type, BiFunction<? super T, ? super U, R> function, ObservableValue<U> arg,
		boolean combineNull) {
		ComposedObservableValue<R> ret = new ComposedObservableValue<>(type, d().lambda(args -> {
			return function.apply((T) args[0], (U) args[1]);
		} , "combineV"), combineNull, this, arg);
		return d().debug(ret).from("combine", this).from("with", arg).using("combination", function)
			.tag("combineNull", combineNull).get();
	}

	/**
	 * @param <U> The type of the other observable to tuplize
	 * @param arg The other observable to tuplize
	 * @return An observable which broadcasts tuples of the latest values of this observable value and another
	 */
	default <U> ObservableValue<BiTuple<T, U>> tupleV(ObservableValue<U> arg) {
		return d().label(combineV(null, BiTuple<T, U>::new, arg, true)).label("tuple").get();
	}

	/**
	 * @param <U> The type of the first other observable to tuplize
	 * @param <V> The type of the second other observable to tuplize
	 * @param arg1 The first other observable to tuplize
	 * @param arg2 The second other observable to tuplize
	 * @return An observable which broadcasts tuples of the latest values of this observable value and 2 others
	 */
	default <U, V> ObservableValue<TriTuple<T, U, V>> tupleV(ObservableValue<U> arg1, ObservableValue<V> arg2) {
		return d().label(combineV(null, TriTuple<T, U, V>::new, arg1, arg2, true)).label("tuple").get();
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
	default <U, V, R> ObservableValue<R> combineV(TriFunction<? super T, ? super U, ? super V, R> function, ObservableValue<U> arg2,
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
	 * @param combineNull Whether to apply the combination function if the arguments are null. If false and any arguments are null, the
	 *            result will be null.
	 * @return The new observable whose value is a function of this observable's value and the others'
	 */
	default <U, V, R> ObservableValue<R> combineV(TypeToken<R> type, TriFunction<? super T, ? super U, ? super V, R> function,
		ObservableValue<U> arg2, ObservableValue<V> arg3, boolean combineNull) {
		ComposedObservableValue<R> ret = new ComposedObservableValue<>(type, d().lambda(args -> {
			return function.apply((T) args[0], (U) args[1], (V) args[2]);
		} , "combineV"), combineNull, this, arg2, arg3);
		return d().debug(ret).from("combine", this).from("with", arg2).from("with", arg3)
			.using("combination", function).tag("combineNull", combineNull).get();
	}

	@Override
	default ObservableValue<T> takeUntil(Observable<?> until) {
		return d().debug(new ObservableValueTakenUntil<>(this, until, true)).from("take", this).from("until", until).tag("terminate", true)
			.get();
	}

	@Override
	default Observable<ObservableValueEvent<T>> unsubscribeOn(Observable<?> until) {
		return d().debug(new ObservableValueTakenUntil<>(this, until, false)).from("take", this).from("until", until)
			.tag("terminate", false).get();
	}

	/**
	 * @param refresh The observer to duplicate event firing for
	 * @return An observable value that fires additional value events when the given observable fires
	 */
	default ObservableValue<T> refresh(Observable<?> refresh) {
		return d().debug(new RefreshingObservableValue<>(this, refresh)).from("refresh", this).from("on", refresh).get();
	}

	@Override
	default ObservableValue<T> safe() {
		return d().debug(new SafeObservableValue<>(this)).from("safe", this).get();
	}

	/**
	 * A shortened version of {@link #constant(TypeToken, Object)}. The type of the object will be value's class. This is not always a good
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
	public static <X> ObservableValue<X> constant(final X value) {
		if (value == null)
			throw new IllegalArgumentException("Cannot call constant(value) with a null value.  Use constant(TypeToken<X>, X).");
		return d().debug(new ConstantObservableValue<>(TypeToken.of((Class<X>) value.getClass()), value)).label("constant")
			.tag("value", value).get();
	}

	/**
	 * @param <X> The compile-time type of the value to wrap
	 * @param type The run-time type of the value to wrap
	 * @param value The value to wrap
	 * @return An observable that always returns the given value
	 */
	public static <X> ObservableValue<X> constant(final TypeToken<X> type, final X value) {
		return d().debug(new ConstantObservableValue<>(type, value)).label("constant").tag("value", value).get();
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
		return d().debug(new FlattenedObservableValue<>(ov, defaultValue)).from("flatten", ov).get();
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
				return value.subscribe(new Observer<ObservableValueEvent<? extends Observable<? extends T>>>() {
					@Override
					public <E extends ObservableValueEvent<? extends Observable<? extends T>>> void onNext(E event) {
						if (event.getValue() != null) {
							event.getValue().takeUntil(value.noInit()).subscribe(new Observer<T>() {
								@Override
								public <V extends T> void onNext(V value2) {
									observer.onNext(value2);
								}
								// Don't use the completed events because the contents of this observable may be replaced
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
	 * 	{@link ObservableList#constant(TypeToken, Object...) ObservableList.constant(type, values)}
	 * {@link ObservableOrderedCollection#findFirst(Predicate) .findFirst(test)}{{@link #mapV(Function) .mapV(v->v!=null ? v : def.get()}
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
		return d().debug(new FirstObservableValue<>(type, values, test, def)).from("values", (Object[]) values).get();
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
		TypeToken<T> t = type == null ? (TypeToken<T>) TypeToken.of(value.getClass()).resolveType(Supplier.class.getTypeParameters()[0])
			: type;
		return d().debug(new ObservableValue<T>() {
			@Override
			public TypeToken<T> getType() {
				return t;
			}

			@Override
			public T get() {
				return value.get();
			}

			@Override
			public Subscription subscribe(Observer<? super ObservableValueEvent<T>> observer) {
				ObservableValue<T> outer = this;
				Subscription[] subSubs = new Subscription[components.length];
				Object[] oldValue = new Object[1];
				for (int i = 0; i < subSubs.length; i++)
					subSubs[i] = components[i].subscribe(new Observer<ObservableValueEvent<?>>() {
						@Override
						public <V extends ObservableValueEvent<?>> void onNext(V value2) {
							T newVal = value.get();
							T oldVal = (T) oldValue[0];
							oldValue[0] = newVal;
							if (value2.isInitial())
								Observer.onNextAndFinish(observer, outer.createInitialEvent(newVal, value2.getCause()));
							else
								Observer.onNextAndFinish(observer, outer.createChangeEvent(oldVal, newVal, value2.getCause()));
						}

						@Override
						public <V extends ObservableValueEvent<?>> void onCompleted(V value2) {}
					});
				return () -> {
					for (Subscription sub : subSubs)
						sub.unsubscribe();
				};
			}

			@Override
			public boolean isSafe() {
				return false;
			}

			@Override
			public String toString() {
				return "Assembled " + type;
			}
		}).from("assemble", (Object[]) components).using("assembler", value).get();
	}

	/**
	 * @param <V> The first argument type
	 * @param <U> The second argument type
	 * @return A binary function that returns its first argument
	 */
	public static <V, U> BiFunction<V, U, V> first() {
		return (V v1, U v2) -> {
			return v1;
		};
	}

	/**
	 * @param <V> The first argument type
	 * @param <U> The second argument type
	 * @return A binary function that returns its second argument
	 */
	public static <V, U> BiFunction<V, U, U> second() {
		return (V v1, U v2) -> {
			return v2;
		};
	}

	/**
	 * An observable that depends on the values of other observables
	 *
	 * @param <T> The type of the composed observable
	 */
	public class ComposedObservableValue<T> implements ObservableValue<T> {
		private final List<ObservableValue<?>> theComposed;

		private final Function<Object [], T> theFunction;

		private final ListenerSet<Observer<? super ObservableValueEvent<T>>> theObservers;

		private final TypeToken<T> theType;

		private final boolean combineNulls;

		private T theValue;

		/**
		 * @param function The function that operates on the argument observables to produce this observable's value
		 * @param combineNull Whether to apply the combination function if the arguments are null. If false and any arguments are null, the
		 *            result will be null.
		 * @param composed The argument observables whose values are passed to the function
		 */
		public ComposedObservableValue(Function<Object [], T> function, boolean combineNull, ObservableValue<?>... composed) {
			this(null, function, combineNull, composed);
		}

		/**
		 * @param type The type for this value
		 * @param function The function that operates on the argument observables to produce this observable's value
		 * @param combineNull Whether to apply the combination function if the arguments are null. If false and any arguments are null, the
		 *            result will be null.
		 * @param composed The argument observables whose values are passed to the function
		 */
		public ComposedObservableValue(TypeToken<T> type, Function<Object [], T> function, boolean combineNull,
			ObservableValue<?>... composed) {
			theFunction = function;
			combineNulls = combineNull;
			theType = type != null ? type
				: (TypeToken<T>) TypeToken.of(function.getClass()).resolveType(Function.class.getTypeParameters()[1]);
			theComposed = java.util.Collections.unmodifiableList(java.util.Arrays.asList(composed));
			theObservers = new ListenerSet<>();
			final Subscription [] composedSubs = new Subscription[theComposed.size()];
			boolean [] completed = new boolean[1];
			theObservers.setUsedListener(new Consumer<Boolean>() {
				@Override
				public void accept(Boolean used) {
					if(used) {
						if (theComposed.toString().equals("[model.flash.value, 0]"))
							System.out.print("");
						Object[] composedValues = new Object[theComposed.size()];
						boolean[] initialized = new boolean[composedValues.length];
						for (int i = 0; i < composedValues.length; i++) {
							int index = i;
							composedSubs[i] = theComposed.get(i).subscribe(new Observer<ObservableValueEvent<?>>() {
								@Override
								public <V extends ObservableValueEvent<?>> void onNext(V event) {
									composedValues[index] = event.getValue();
									if (event.isInitial()) {
										initialized[index] = true;
										return;
									} else if (!isInitialized())
										return;
									T oldValue = theValue;
									theValue = combine(composedValues);
									ObservableValueEvent<T> toFire = ComposedObservableValue.this.createChangeEvent(oldValue, theValue,
										event);
									fireNext(toFire);
								}

								private boolean isInitialized() {
									for (boolean b : initialized)
										if (!b)
											return false;
									return true;
								}

								@Override
								public <V extends ObservableValueEvent<?>> void onCompleted(V event) {
									composedValues[index] = event.getValue();
									completed[0] = true;
									if (!isInitialized())
										return;
									T oldValue = theValue;
									T newValue = combine(composedValues);
									theValue = null;
									ObservableValueEvent<T> toFire = createChangeEvent(oldValue, newValue, event);
									fireCompleted(toFire);
								}

								@Override
								public void onError(Throwable e) {
									fireError(e);
								}

								private void fireNext(ObservableValueEvent<T> next) {
									theObservers.forEach(listener -> listener.onNext(next));
									next.finish();
								}

								private void fireCompleted(ObservableValueEvent<T> next) {
									theObservers.forEach(listener -> listener.onCompleted(next));
									Subscription.forAll(composedSubs).unsubscribe();
									next.finish();
								}

								private void fireError(Throwable error) {
									theObservers.forEach(listener -> listener.onError(error));
								}
							});
							if(completed[0])
								break;
						}
						for (int i = 0; i < composedValues.length; i++)
							if (!initialized[i])
								throw new IllegalStateException(theComposed.get(i) + " did not fire an initial value");
						if (!completed[0])
							theValue = combine(composedValues);
						// initialized[0] = true;
					} else {
						theValue = null;
						for(int i = 0; i < theComposed.size(); i++) {
							if(composedSubs[i] != null) {
								composedSubs[i].unsubscribe();
								composedSubs[i] = null;
							}
						}
						completed[0] = false;
					}
				}
			});
			theObservers.setOnSubscribe(observer -> {
				if(completed[0])
					Observer.onCompletedAndFinish(observer, createInitialEvent(theValue, null));
				else
					Observer.onNextAndFinish(observer, createInitialEvent(theValue, null));
			});
		}

		@Override
		public TypeToken<T> getType() {
			return theType;
		}

		/** @return The observable values that compose this value */
		public ObservableValue<?> [] getComposed() {
			return theComposed.toArray(new ObservableValue[theComposed.size()]);
		}

		/** @return The function used to map this observable's composed values into its return value */
		public Function<Object [], T> getFunction() {
			return theFunction;
		}

		/**
		 * @return Whether the combination function will be applied if the arguments are null. If false and any arguments are null, the
		 *         result will be null.
		 */
		public boolean isNullCombined() {
			return combineNulls;
		}

		@Override
		public T get() {
			if(theObservers.isUsed())
				return theValue;
			else {
				Object [] composed = new Object[theComposed.size()];
				for(int i = 0; i < composed.length; i++)
					composed[i] = theComposed.get(i).get();
				return combine(composed);
			}
		}

		/**
		 * @param args The arguments to combine
		 * @return The combined value
		 */
		protected T combine(Object[] args) {
			if(!combineNulls) {
				for(Object arg : args)
					if(arg == null)
						return null;
			}
			return theFunction.apply(args.clone());
		}

		@Override
		public Subscription subscribe(Observer<? super ObservableValueEvent<T>> observer) {
			theObservers.add(observer);
			return () -> theObservers.remove(observer);
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
	 * Implements {@link ObservableValue#takeUntil(Observable)}
	 *
	 * @param <T> The type of the value
	 */
	class ObservableValueTakenUntil<T> extends Observable.ObservableTakenUntil<ObservableValueEvent<T>> implements ObservableValue<T> {
		protected ObservableValueTakenUntil(ObservableValue<T> wrap, Observable<?> until, boolean terminate) {
			super(wrap, until, terminate);
		}

		@Override
		protected ObservableValue<T> getWrapped() {
			return (ObservableValue<T>) super.getWrapped();
		}

		@Override
		public TypeToken<T> getType() {
			return getWrapped().getType();
		}

		@Override
		public T get() {
			return getWrapped().get();
		}

		@Override
		protected ObservableValueEvent<T> getDefaultValue() {
			T value = get();
			return getWrapped().createChangeEvent(value, value, null);
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
		public Subscription subscribe(Observer<? super ObservableValueEvent<T>> observer) {
			Subscription [] refireSub = new Subscription[1];
			boolean [] completed = new boolean[1];
			Subscription outerSub = theWrapped.subscribe(new Observer<ObservableValueEvent<T>>() {
				@Override
				public <V extends ObservableValueEvent<T>> void onNext(V value) {
					observer.onNext(value);
				}

				@Override
				public <V extends ObservableValueEvent<T>> void onCompleted(V value) {
					observer.onCompleted(value);
					if(refireSub[0] != null) {
						refireSub[0].unsubscribe();
						refireSub[0] = null;
					}
					completed[0] = true;
				}
			});
			if(completed[0]) {
				return () -> {
				};
			}
			refireSub[0] = theRefresh.act(evt -> {
				T value = get();
				Observer.onNextAndFinish(observer, createChangeEvent(value, value, evt));
			});
			return () -> {
				outerSub.unsubscribe();
				if(refireSub[0] != null)
					refireSub[0].unsubscribe();
			};
		}

		@Override
		public boolean isSafe() {
			return false;
		}

		@Override
		public String toString() {
			return theWrapped.toString();
		}
	}

	/**
	 * Observable value implementing the {@link #cached()} method
	 *
	 * @param <T> The type of the value
	 */
	class CachedObservableValue<T> implements ObservableValue<T> {
		private final ObservableValue<T> theWrapped;

		private T theValue;

		private org.qommons.ListenerSet<Observer<? super ObservableValueEvent<T>>> theObservers;

		public CachedObservableValue(ObservableValue<T> wrapped) {
			theWrapped = wrapped;
			theObservers = new org.qommons.ListenerSet<>();
			theObservers.setUsedListener(new java.util.function.Consumer<Boolean>() {
				private Subscription sub;

				@Override
				public void accept(Boolean used) {
					if(used) {
						boolean [] initialized = new boolean[1];
						sub = theWrapped.subscribe(new Observer<ObservableValueEvent<T>>() {
							@Override
							public <V extends ObservableValueEvent<T>> void onNext(V value) {
								T oldValue = theValue;
								theValue = value.getValue();
								if(initialized[0]) {
									ObservableValueEvent<T> cachedEvent = createChangeEvent(oldValue, theValue, value.getCause());
									theObservers.forEach(observer -> observer.onNext(cachedEvent));
									cachedEvent.finish();
								}
							}

							@Override
							public <V extends ObservableValueEvent<T>> void onCompleted(V value) {
								T oldValue = theValue;
								T newValue = value.getValue();
								if(initialized[0]) {
									ObservableValueEvent<T> cachedEvent = createChangeEvent(oldValue, newValue, value.getCause());
									theObservers.forEach(observer -> observer.onNext(cachedEvent));
									cachedEvent.finish();
								}
							}
						});
					} else {
						sub.unsubscribe();
						sub = null;
						theValue = null;
					}
				}
			});
		}

		protected ObservableValue<T> getWrapped() {
			return theWrapped;
		}

		@Override
		public Subscription subscribe(Observer<? super ObservableValueEvent<T>> observer) {
			theObservers.add(observer);
			return () -> {
				theObservers.remove(observer);
			};
		}

		@Override
		public TypeToken<T> getType() {
			return theWrapped.getType();
		}

		@Override
		public T get() {
			if(theObservers.isUsed())
				return theValue;
			else
				return theWrapped.get();
		}

		@Override
		public ObservableValue<T> cached() {
			return this;
		}

		@Override
		public boolean isSafe() {
			return true;
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
		public Subscription subscribe(Observer<? super ObservableValueEvent<T>> observer) {
			Observer.onNextAndFinish(observer, createInitialEvent(theValue, null));
			return () -> {
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
		public boolean isSafe() {
			return true;
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
	class SafeObservableValue<T> extends SafeObservable<ObservableValueEvent<T>> implements ObservableValue<T> {
		public SafeObservableValue(ObservableValue<T> wrap) {
			super(wrap);
		}

		@Override
		protected ObservableValue<T> getWrapped() {
			return (ObservableValue<T>) super.getWrapped();
		}

		@Override
		public TypeToken<T> getType() {
			return getWrapped().getType();
		}

		@Override
		public T get() {
			return getWrapped().get();
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
		public Subscription subscribe(Observer<? super ObservableValueEvent<T>> observer) {
			ObservableValue<T> retObs = this;
			AtomicReference<Subscription> innerSub = new AtomicReference<>();
			boolean[] firedInit = new boolean[1];
			Subscription outerSub = theValue.subscribe(new Observer<ObservableValueEvent<? extends ObservableValue<? extends T>>>() {
				private final ReentrantLock theLock = new ReentrantLock();

				@Override
				public <V extends ObservableValueEvent<? extends ObservableValue<? extends T>>> void onNext(V event) {
					firedInit[0] = true;
					theLock.lock();
					try {
						final ObservableValue<? extends T> innerObs = event.getValue();
						// Shouldn't have 2 inner observables potentially generating events at the same time
						if (!Objects.equals(innerObs, event.getOldValue()))
							Subscription.unsubscribe(innerSub.getAndSet(null));
						Object[] old = new Object[1];
						if (innerObs != null && !innerObs.equals(event.getOldValue())) {
							boolean[] firedInit2 = new boolean[1];
							Subscription.unsubscribe(
								innerSub.getAndSet(innerObs.subscribe(new Observer<ObservableValueEvent<? extends T>>() {
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
											if (event.isInitial() && event2.isInitial())
												Observer.onNextAndFinish(observer,
													retObs.createInitialEvent(event2.getValue(), event2.getCause()));
											else
												Observer.onNextAndFinish(observer,
													retObs.createChangeEvent(innerOld, event2.getValue(), event2.getCause()));
										} finally {
											theLock.unlock();
										}
									}
								})));
							if (!firedInit2[0])
								throw new IllegalStateException(innerObs + " did not fire an initial value");
						} else if (event.isInitial())
							Observer.onNextAndFinish(observer, retObs.createInitialEvent(get(null), event.getCause()));
						else if (old[0] != null)
							Observer.onNextAndFinish(observer, retObs.createChangeEvent((T) old[0], get(null), event.getCause()));
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
						Observer.onCompletedAndFinish(observer,
							retObs.createChangeEvent(get(event.getOldValue()), get(event.getValue()), event.getCause()));
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
		public Subscription subscribe(Observer<? super ObservableValueEvent<T>> observer) {
			if (theValues.length == 0) {
				Observer.onNextAndFinish(observer, createInitialEvent(null, null));
				return () -> {
				};
			}
			Subscription[] valueSubs = new Subscription[theValues.length];
			boolean[] finished = new boolean[theValues.length];
			Object [] lastValue=new Object[1];
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

				private void event(ObservableValueEvent<? extends T> event, boolean complete){
					lock.lock();
					try{
						boolean found = !complete && theTest.test(event.getValue());
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
								toFire = new ObservableValueEvent<>(FirstObservableValue.this, !hasFiredInit[0], oldValue, oldValue, event);
							else
								toFire = null;
						} else {
							if (found) {
								lastValue[0] = event.getValue();
								toFire = new ObservableValueEvent<>(FirstObservableValue.this, !hasFiredInit[0], oldValue, event.getValue(),
									event);
							} else if (nextIndex == theValues.length)
								toFire = new ObservableValueEvent<>(FirstObservableValue.this, !hasFiredInit[0], oldValue, theDefault.get(),
									event);
							else
								toFire = null;
						}
						if (toFire != null) {
							hasFiredInit[0] = true;
							observer.onNext(toFire);
							toFire.finish();
						}
						if (found != isFound) {
							isFound = found;
							if(found){
								for (int i = index + 1; i < valueSubs.length; i++) {
									if (valueSubs[i] != null) {
										valueSubs[i].unsubscribe();
										valueSubs[i] = null;
									}
								}
							} else if (nextIndex < theValues.length)
								valueSubs[nextIndex] = theValues[nextIndex].subscribe(new ElementFirstObserver(nextIndex));
						} else if (!hasFiredInit[0])
							valueSubs[nextIndex] = theValues[nextIndex].subscribe(new ElementFirstObserver(nextIndex));
					} finally{
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
			valueSubs[0] = theValues[0].safe().subscribe(new ElementFirstObserver(0));
			return () -> {
				for (int i = 0; i < valueSubs.length; i++){
					if(valueSubs[i]!=null){
						valueSubs[i].unsubscribe();
						valueSubs[i]=null;
					}
				}
			};
		}

		@Override
		public boolean isSafe() {
			return true;
		}
	}
}
