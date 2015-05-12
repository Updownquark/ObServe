package org.observe;

import static org.observe.ObservableDebug.debug;
import static org.observe.ObservableDebug.label;
import static org.observe.ObservableDebug.lambda;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.util.ListenerSet;
import org.observe.util.ObservableUtils;

import prisms.lang.Type;

/**
 * A value holder that can notify listeners when the value changes. This type of observable will always notify subscribers with an event
 * whose old value is null and whose new value is this holder's current value before the {@link #subscribe(Observer)} method exits.
 *
 * @param <T> The compile-time type of this observable's value
 */
public interface ObservableValue<T> extends Observable<ObservableValueEvent<T>>, java.util.function.Supplier<T> {
	/** @return The type of value this observable contains. May be null if this observable's value is always null. */
	Type getType();

	/** @return The current value of this observable */
	@Override
	T get();

	/** @return An observable that just reports this observable value's value in an observable without the event */
	default Observable<T> value() {
		return debug(new Observable<T>() {
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
			public String toString() {
				return ObservableValue.this.toString() + ".value()";
			}
		}).from("value", this).get();
	}

	/** @return A cached version of this value, which */
	default ObservableValue<T> cached() {
		return debug(new CachedObservableValue<>(this)).from("cached", this).get();
	}

	/**
	 * Creates an {@link ObservableValueEvent} to propagate a change to this observable's value
	 *
	 * @param oldVal The previous value of this observable
	 * @param newVal The new value of this observable
	 * @param cause The cause of the change
	 * @return New event to propagate
	 */
	default ObservableValueEvent<T> createEvent(T oldVal, T newVal, Object cause) {
		return new ObservableValueEvent<>(this, oldVal, newVal, cause);
	}

	/**
	 * @param eventMap The mapping function that intercepts value events from this value and creates new, equivalent events
	 * @return An observable value identical to this one but whose change events are mapped by the given function
	 */
	default ObservableValue<T> mapEvent(Function<? super ObservableValueEvent<T>, ? extends ObservableValueEvent<T>> eventMap) {
		ObservableValue<T> outer = this;
		return debug(new ObservableValue<T>() {
			@Override
			public Type getType() {
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
	 * @param type The run-time type of the new observable
	 * @param function The function to apply to this observable's value
	 * @return The new observable whose value is a function of this observable's value
	 */
	default <R> ObservableValue<R> mapV(Type type, Function<? super T, R> function) {
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
	default <R> ObservableValue<R> mapV(Type type, Function<? super T, R> function, boolean filterNull) {
		return debug(new ComposedObservableValue<R>(type, lambda(args -> {
			return function.apply((T) args[0]);
		}, "mapV"), filterNull, this)).from("map", this).using("map", function).tag("filterNull", filterNull).get();
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
	default <U, R> ObservableValue<R> combineV(Type type, BiFunction<? super T, ? super U, R> function, ObservableValue<U> arg,
		boolean combineNull) {
		return debug(new ComposedObservableValue<R>(type, lambda(args -> {
			return function.apply((T) args[0], (U) args[1]);
		}, "combineV"), combineNull, this, arg)).from("combine", this).from("with", arg).using("combination", function)
		.tag("combineNull", combineNull).get();
	}

	/**
	 * @param <U> The type of the other observable to tuplize
	 * @param arg The other observable to tuplize
	 * @return An observable which broadcasts tuples of the latest values of this observable value and another
	 */
	default <U> ObservableValue<BiTuple<T, U>> tupleV(ObservableValue<U> arg) {
		return label(combineV(null, BiTuple<T, U>::new, arg, true)).label("tuple").get();
	}

	/**
	 * @param <U> The type of the first other observable to tuplize
	 * @param <V> The type of the second other observable to tuplize
	 * @param arg1 The first other observable to tuplize
	 * @param arg2 The second other observable to tuplize
	 * @return An observable which broadcasts tuples of the latest values of this observable value and 2 others
	 */
	default <U, V> ObservableValue<TriTuple<T, U, V>> tupleV(ObservableValue<U> arg1, ObservableValue<V> arg2) {
		return label(combineV(null, TriTuple<T, U, V>::new, arg1, arg2, true)).label("tuple").get();
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
	default <U, V, R> ObservableValue<R> combineV(Type type, TriFunction<? super T, ? super U, ? super V, R> function,
		ObservableValue<U> arg2, ObservableValue<V> arg3, boolean combineNull) {
		return debug(new ComposedObservableValue<R>(type, lambda(args -> {
			return function.apply((T) args[0], (U) args[1], (V) args[2]);
		}, "combineV"), combineNull, this, arg2, arg3)).from("combine", this).from("with", arg2).from("with", arg3)
		.using("combination", function).tag("combineNull", combineNull).get();
	}

	@Override
	default ObservableValue<T> takeUntil(Observable<?> until) {
		return debug(new ObservableValueTakenUntil<>(this, until)).from("take", this).from("until", until).get();
	}

	/**
	 * @param refresh The observer to duplicate event firing for
	 * @return An observable value that fires additional value events when the given observable fires
	 */
	default ObservableValue<T> refresh(Observable<?> refresh) {
		return debug(new RefreshingObservableValue<>(this, refresh)).from("refresh", this).from("on", refresh).get();
	}

	/**
	 * @param <X> The type of the value to wrap
	 * @param value The value to wrap
	 * @return An observable that always returns the given value
	 */
	public static <X> ObservableValue<X> constant(final X value) {
		return debug(new ConstantObservableValue<>(value == null ? Type.NULL : new Type(value.getClass()), value)).label("constant")
			.tag("value", value).get();
	}

	/**
	 * @param <X> The compile-time type of the value to wrap
	 * @param type The run-time type of the value to wrap
	 * @param value The value to wrap
	 * @return An observable that always returns the given value
	 */
	public static <X> ObservableValue<X> constant(final Type type, final X value) {
		return debug(new ConstantObservableValue<>(type, value)).label("constant").tag("value", value).get();
	}

	/**
	 * @param <T> The compile-time super type of all observables contained in the nested observable
	 * @param type The super type of all observables possibly contained in the given nested observable, or null to use the type of the
	 *            contained observable
	 * @param ov The nested observable
	 * @return An observable value whose value is the value of <code>ov.get()</code>
	 */
	public static <T> ObservableValue<T> flatten(final Type type, final ObservableValue<? extends ObservableValue<? extends T>> ov) {
		if(ov == null)
			throw new NullPointerException("Null observable");
		return debug(new ObservableValue<T>() {
			@Override
			public Type getType() {
				if(type != null)
					return type;
				ObservableValue<? extends T> outerVal = ov.get();
				if(outerVal == null)
					throw new IllegalStateException("Flattened observable is null and no type given: " + ov);
				return outerVal.getType();
			}

			@Override
			public T get() {
				return get(ov.get());
			}

			private T get(ObservableValue<? extends T> value) {
				return value == null ? null : value.get();
			}

			@Override
			public Subscription subscribe(Observer<? super ObservableValueEvent<T>> observer) {
				ObservableValue<T> retObs = this;
				Subscription [] innerSub = new Subscription[1];
				Subscription outerSub = ov.subscribe(new Observer<ObservableValueEvent<? extends ObservableValue<? extends T>>>() {
					@Override
					public <V extends ObservableValueEvent<? extends ObservableValue<? extends T>>> void onNext(V value) {
						if(innerSub[0] != null) {
							innerSub[0].unsubscribe();
							innerSub[0] = null;
						}
						T old = get(value.getOldValue());
						if(value.getValue() != null) {
							boolean [] init = new boolean[] {true};
							innerSub[0] = value.getValue().subscribe(new Observer<ObservableValueEvent<? extends T>>() {
								@Override
								public <V2 extends ObservableValueEvent<? extends T>> void onNext(V2 value2) {
									T innerOld;
									if(init[0]) {
										init[0] = false;
										innerOld = old;
									} else
										innerOld = value2.getValue();
									observer.onNext(new ObservableValueEvent<>(retObs, innerOld, value2.getValue(), value2.getCause()));
								}

								@Override
								public void onError(Throwable e) {
									observer.onError(e);
								}
							});
						} else
							observer.onNext(retObs.createEvent(old, null, value.getCause()));
					}

					@Override
					public <V extends ObservableValueEvent<? extends ObservableValue<? extends T>>> void onCompleted(V value) {
						observer.onCompleted(retObs.createEvent(get(value.getOldValue()), get(value.getValue()), value
							.getCause()));
					}

					@Override
					public void onError(Throwable e) {
						observer.onError(e);
					}
				});
				return () -> {
					outerSub.unsubscribe();
					if(innerSub[0] != null) {
						innerSub[0].unsubscribe();
						innerSub[0] = null;
					}
				};
			}

			@Override
			public String toString() {
				return "flat(" + ov + ")";
			}
		}).from("flatten", ov).get();
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
	public static <T> ObservableValue<T> assemble(Type type, java.util.function.Supplier<T> value, ObservableValue<?>... components) {
		Type t = type == null ? ObservableUtils.getReturnType(value) : type;
		return debug(new ObservableValue<T>() {
			@Override
			public Type getType() {
				return t;
			}

			@Override
			public T get() {
				return value.get();
			}

			@Override
			public Subscription subscribe(Observer<? super ObservableValueEvent<T>> observer) {
				ObservableValue<T> outer = this;
				Subscription [] subSubs = new Subscription[components.length];
				Object [] oldValue = new Object[1];
				for(int i = 0; i < subSubs.length; i++)
					subSubs[i] = components[i].subscribe(new Observer<ObservableValueEvent<?>>() {
						@Override
						public <V extends ObservableValueEvent<?>> void onNext(V value2) {
							T newVal = value.get();
							T oldVal = (T) oldValue[0];
							oldValue[0] = newVal;
							observer.onNext(outer.createEvent(oldVal, newVal, value2.getCause()));
						}

						@Override
						public <V extends ObservableValueEvent<?>> void onCompleted(V value2) {
						}

						@Override
						public void onError(Throwable e) {
							observer.onError(e);
						}
					});
				return () -> {
					for(Subscription sub : subSubs)
						sub.unsubscribe();
				};
			}

			@Override
			public String toString(){
				return "Assembled " + type;
			}
		}).from("assemble", (Object []) components).using("assembler", value).get();
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

		private final Type theType;

		private final boolean combineNulls;

		private Object [] theComposedValues;

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
		public ComposedObservableValue(Type type, Function<Object [], T> function, boolean combineNull, ObservableValue<?>... composed) {
			theFunction = function;
			combineNulls = combineNull;
			theType = type != null ? type : ObservableUtils.getReturnType(function);
			theComposed = java.util.Collections.unmodifiableList(java.util.Arrays.asList(composed));
			theObservers = new ListenerSet<>();
			theComposedValues = new Object[composed.length];
			final Subscription [] composedSubs = new Subscription[theComposed.size()];
			boolean [] completed = new boolean[1];
			theObservers.setUsedListener(new Consumer<Boolean>() {
				@Override
				public void accept(Boolean used) {
					if(used) {
						/*Don't need to initialize this explicitly, because these will be populated when the components are subscribed to
						 * for(int i = 0; i < args.length; i++)
						 * 	args[i] = theComposed.get(i).get(); */
						boolean [] initialized = new boolean[1];
						for(int i = 0; i < theComposedValues.length; i++) {
							int index = i;
							composedSubs[i] = theComposed.get(i).subscribe(new Observer<ObservableValueEvent<?>>() {
								@Override
								public <V extends ObservableValueEvent<?>> void onNext(V event) {
									theComposedValues[index] = event.getValue();
									if(!initialized[0])
										return;
									T oldValue = theValue;
									theValue = combine(theComposedValues);
									ObservableValueEvent<T> toFire = new ObservableValueEvent<>(ComposedObservableValue.this, oldValue,
										theValue, event);
									fireNext(toFire);
								}

								@Override
								public <V extends ObservableValueEvent<?>> void onCompleted(V event) {
									theComposedValues[index] = event.getValue();
									completed[0] = true;
									if(!initialized[0])
										return;
									T oldValue = theValue;
									T newValue = combine(theComposedValues);
									theValue = null;
									ObservableValueEvent<T> toFire = createEvent(oldValue, newValue, event);
									fireCompleted(toFire);
								}

								@Override
								public void onError(Throwable e) {
									fireError(e);
								}

								private void fireNext(ObservableValueEvent<T> next) {
									theObservers.forEach(listener -> listener.onNext(next));
								}

								private void fireCompleted(ObservableValueEvent<T> next) {
									theObservers.forEach(listener -> listener.onCompleted(next));
								}

								private void fireError(Throwable error) {
									theObservers.forEach(listener -> listener.onError(error));
								}
							});
						}
						theValue = combine(theComposedValues);
						initialized[0] = true;
					} else {
						for(int i = 0; i < theComposed.size(); i++) {
							composedSubs[i].unsubscribe();
							composedSubs[i] = null;
							theComposedValues[i] = null;
							theValue = null;
							completed[0] = false;
						}
					}
				}
			});
			theObservers.setOnSubscribe(observer -> {
				if(completed[0])
					observer.onCompleted(new ObservableValueEvent<>(this, null, theValue, null));
				else
					observer.onNext(new ObservableValueEvent<>(this, null, theValue, null));
			});
		}

		@Override
		public Type getType() {
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

		private T combine(Object [] args) {
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
		protected ObservableValueTakenUntil(ObservableValue<T> wrap, Observable<?> until) {
			super(wrap, until);
		}

		@Override
		protected ObservableValue<T> getWrapped() {
			return (ObservableValue<T>) super.getWrapped();
		}

		@Override
		public Type getType() {
			return getWrapped().getType();
		}

		@Override
		public T get() {
			return getWrapped().get();
		}

		@Override
		protected ObservableValueEvent<T> getDefaultValue() {
			T value = get();
			return getWrapped().createEvent(value, value, null);
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
		public Type getType() {
			return theWrapped.getType();
		}

		@Override
		public T get() {
			return theWrapped.get();
		}

		@Override
		public Subscription subscribe(Observer<? super ObservableValueEvent<T>> observer) {
			Subscription outerSub = theWrapped.subscribe(observer);
			Subscription refireSub = theRefresh.subscribe(new Observer<Object>() {
				@Override
				public <V> void onNext(V value) {
					observer.onNext(createEvent(get(), get(), value));
				}
			});
			return () -> {
				outerSub.unsubscribe();
				refireSub.unsubscribe();
			};
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

		private org.observe.util.ListenerSet<Observer<? super ObservableValueEvent<T>>> theObservers;

		public CachedObservableValue(ObservableValue<T> wrapped) {
			theWrapped = wrapped;
			theObservers = new org.observe.util.ListenerSet<>();
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
									ObservableValueEvent<T> cachedEvent = createEvent(oldValue, theValue, value.getCause());
									theObservers.forEach(observer -> observer.onNext(cachedEvent));
								}
							}

							@Override
							public <V extends ObservableValueEvent<T>> void onCompleted(V value) {
								T oldValue = theValue;
								T newValue = value.getValue();
								if(initialized[0]) {
									ObservableValueEvent<T> cachedEvent = createEvent(oldValue, newValue, value.getCause());
									theObservers.forEach(observer -> observer.onNext(cachedEvent));
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
		public Type getType() {
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
	}

	/**
	 * An observable value whose value cannot change
	 *
	 * @param <T> The type of this value
	 */
	public static final class ConstantObservableValue<T> implements ObservableValue<T> {
		private final Type theType;
		private final T theValue;

		/**
		 * @param type The type of this observable value
		 * @param value This observable value's value
		 */
		public ConstantObservableValue(Type type, T value) {
			theType = type;
			theValue = (T) type.cast(value);
		}

		@Override
		public Subscription subscribe(Observer<? super ObservableValueEvent<T>> observer) {
			observer.onNext(createEvent(theValue, theValue, null));
			return () -> {
			};
		}

		@Override
		public Type getType() {
			return theType;
		}

		@Override
		public T get() {
			return theValue;
		}

		@Override
		public String toString() {
			return "" + theValue;
		}
	}
}
