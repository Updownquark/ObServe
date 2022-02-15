package org.observe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.observe.Transformation.TransformationState;
import org.observe.Transformation.TransformedElement;
import org.observe.collect.ObservableCollection;
import org.observe.util.TypeTokens;
import org.qommons.BiTuple;
import org.qommons.Identifiable;
import org.qommons.LambdaUtils;
import org.qommons.Lockable;
import org.qommons.Stamped;
import org.qommons.ThreadConstrained;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.TriFunction;
import org.qommons.collect.ListenerList;

import com.google.common.reflect.TypeToken;

/**
 * A value holder that can notify listeners when the value changes. The {@link #changes()} observable will always notify subscribers with an
 * {@link ObservableValueEvent#isInitial() initial} event whose old value is null and whose new value is this holder's current value before
 * the {@link Observable#subscribe(Observer)} method exits.
 *
 * @param <T> The compile-time type of this observable's value
 */
public interface ObservableValue<T> extends Supplier<T>, TypedValueContainer<T>, Lockable, Stamped, Identifiable {
	/** This class's wildcard {@link TypeToken} */
	static TypeToken<ObservableValue<?>> TYPE = TypeTokens.get().keyFor(ObservableValue.class).wildCard();

	/** @return The current value of this observable */
	@Override
	T get();

	/**
	 * @return An observable that fires an {@link ObservableValueEvent#isInitial() initial} event for the current value when subscribed, and
	 *         subsequent change events when this value changes
	 */
	default Observable<ObservableValueEvent<T>> changes() {
		return new ObservableValueChanges<>(this);
	}

	/**
	 * @return An observable that fires an event when this value changes. Unlike {@link #changes()}, this method does not fire an initial
	 *         event for the value when subscribed (unless the value happens to change during subscription, which is allowed).
	 */
	Observable<ObservableValueEvent<T>> noInitChanges();

	@Override
	default ThreadConstraint getThreadConstraint() {
		return noInitChanges().getThreadConstraint();
	}

	@Override
	default boolean isLockSupported() {
		return changes().isLockSupported();
	}

	@Override
	default Transaction lock() {
		return noInitChanges().lock();
	}

	@Override
	default Transaction tryLock() {
		return noInitChanges().tryLock();
	}

	@Override
	default CoreId getCoreId() {
		return noInitChanges().getCoreId();
	}

	/** @return An observable that just reports this observable value's value in an observable without the event */
	default Observable<T> value() {
		class ValueObservable extends AbstractIdentifiable implements Observable<T> {
			@Override
			public Subscription subscribe(Observer<? super T> observer) {
				return ObservableValue.this.changes().subscribe(new Observer<ObservableValueEvent<T>>() {
					@Override
					public <V extends ObservableValueEvent<T>> void onNext(V value) {
						observer.onNext(value.getNewValue());
					}

					@Override
					public <V extends ObservableValueEvent<T>> void onCompleted(V value) {
						observer.onCompleted(value.getNewValue());
					}
				});
			}

			@Override
			public ThreadConstraint getThreadConstraint() {
				return ObservableValue.this.getThreadConstraint();
			}

			@Override
			public boolean isSafe() {
				return ObservableValue.this.noInitChanges().isSafe();
			}

			@Override
			public Transaction lock() {
				return ObservableValue.this.lock();
			}

			@Override
			public Transaction tryLock() {
				return ObservableValue.this.tryLock();
			}

			@Override
			public CoreId getCoreId() {
				return ObservableValue.this.getCoreId();
			}

			@Override
			protected Object createIdentity() {
				return Identifiable.wrap(ObservableValue.this.getIdentity(), "value");
			}
		}
		return new ValueObservable();
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
	 * @param causes The causes of the change
	 * @return The event to propagate
	 */
	default ObservableValueEvent<T> createChangeEvent(T oldVal, T newVal, Object... causes) {
		return ObservableValueEvent.createChangeEvent(this, oldVal, newVal, causes);
	}

	/**
	 * Creates an {@link ObservableValueEvent} to propagate a change to this observable's value
	 *
	 * @param oldVal The previous value of this observable
	 * @param newVal The new value of this observable
	 * @param causes The causes of the change
	 * @return The event to propagate
	 */
	default ObservableValueEvent<T> createChangeEvent(T oldVal, T newVal, Collection<?> causes) {
		return ObservableValueEvent.createChangeEvent(this, oldVal, newVal, causes.toArray());
	}

	/**
	 * @param newVal The old value to fire the change event for
	 * @param oldVal The new value to fire the change event for
	 * @param cause The cause of the change
	 * @param action The action to perform on the event
	 */
	default void fireChangeEvent(T oldVal, T newVal, Object cause, Consumer<? super ObservableValueEvent<T>> action) {
		ObservableValueEvent<T> evt = createChangeEvent(oldVal, newVal, cause);
		try (Transaction t = evt.use()) {
			action.accept(evt);
		}
	}

	/**
	 * @param eventMap The mapping function that intercepts value events from this value and creates new, equivalent events
	 * @return An observable value identical to this one but whose change events are mapped by the given function
	 */
	default ObservableValue<T> mapEvent(Function<? super ObservableValueEvent<T>, ObservableValueEvent<T>> eventMap) {
		return new WrappingObservableValue<T, T>(this) {
			@Override
			public TypeToken<T> getType() {
				return getWrapped().getType();
			}

			@Override
			public CoreId getCoreId() {
				return getWrapped().getCoreId();
			}

			@Override
			public T get() {
				return getWrapped().get();
			}

			@Override
			public Observable<ObservableValueEvent<T>> noInitChanges() {
				return getWrapped().noInitChanges().map(eventMap);
			}

			@Override
			protected Object createIdentity() {
				return getWrapped().getIdentity();
			}
		};
	}

	/**
	 * <p>
	 * Transforms this value into a derived value, potentially including other sources as well. This method satisfies both mapping and
	 * combination use cases.
	 * </p>
	 * <p>
	 * If dynamic {@link #getType() types} are important, it is preferred to use {@link #transform(TypeToken, Function)}. If no target type
	 * is supplied (as with this method), one will be inferred, but this is not always reliable, especially with lambdas.
	 * </p>
	 *
	 * @param <R> The type for the combined value
	 * @param transform Determines how this value and any other arguments are to be combined
	 * @return The transformed value
	 * @see Transformation for help using the API
	 */
	default <R> ObservableValue<R> transform(Function<Transformation.TransformationPrecursor<T, R, ?>, Transformation<T, R>> transform) {
		return transform((TypeToken<R>) null, transform);
	}

	/**
	 * Transforms this value into a derived value, potentially including other sources as well. This method satisfies both mapping and
	 * combination use cases.
	 *
	 * @param <R> The type for the combined value
	 * @param targetType The type for the transformed value
	 * @param transform Determines how this value and any other arguments are to be combined
	 * @return The transformed value
	 * @see Transformation for help using the API
	 */
	default <R> ObservableValue<R> transform(TypeToken<R> targetType, //
		Function<Transformation.TransformationPrecursor<T, R, ?>, Transformation<T, R>> transform) {
		Transformation<T, R> def = transform.apply(new Transformation.TransformationPrecursor<>());
		ObservableValue<?>[] argValues = new ObservableValue[def.getArgs().size() + 1];
		argValues[0] = this;
		Map<ObservableValue<?>, Integer> otherArgs = new HashMap<>((int) Math.ceil(def.getArgs().size() * 1.5));
		Iterator<ObservableValue<?>> argIter = def.getArgs().iterator();
		for (int i = 1; i < argValues.length; i++) {
			ObservableValue<?> arg = argIter.next();
			argValues[i] = arg;
			otherArgs.put(arg, i - 1);
		}
		return new TransformedObservableValue<>(targetType, this, def);
	}

	/**
	 * Transforms this value into a derived value, potentially including other sources as well. This method satisfies both mapping and
	 * combination use cases.
	 *
	 * @param <R> The type for the combined value
	 * @param targetType The type for the transformed value
	 * @param transform Determines how this value and any other arguments are to be combined
	 * @return The transformed value
	 * @see Transformation for help using the API
	 */
	default <R> ObservableValue<R> transform(Class<R> targetType, //
		Function<Transformation.TransformationPrecursor<T, R, ?>, Transformation<T, R>> transform) {
		return transform(targetType == null ? null : TypeTokens.get().of(targetType), transform);
	}

	/**
	 * <p>
	 * Composes this observable into another observable that depends on this one
	 * </p>
	 * <p>
	 * This method is supported for compatibility, but {@link #transform(TypeToken, Function)} is a more flexible method for combining
	 * values
	 * </p>
	 *
	 * @param <R> The type of the new observable
	 * @param function The function to apply to this observable's value
	 * @return The new observable whose value is a function of this observable's value
	 */
	default <R> ObservableValue<R> map(Function<? super T, R> function) {
		return transform(tx -> tx.map(function));
	}

	/**
	 * <p>
	 * Composes this observable into another observable that depends on this one
	 * </p>
	 * <p>
	 * This method is supported for compatibility, but {@link #transform(TypeToken, Function)} is a more flexible method for combining
	 * values
	 * </p>
	 *
	 * @param <R> The type of the new observable
	 * @param type The run-time type of the new observable
	 * @param function The function to apply to this observable's value
	 * @return The new observable whose value is a function of this observable's value
	 */
	default <R> ObservableValue<R> map(TypeToken<R> type, Function<? super T, R> function) {
		return map(type, function, null);
	}

	/**
	 * <p>
	 * Composes this observable into another observable that depends on this one
	 * </p>
	 * <p>
	 * This method is supported for compatibility, but {@link #transform(TypeToken, Function)} is a more flexible method for combining
	 * values
	 * </p>
	 *
	 * @param <R> The type of the new observable
	 * @param type The run-time type of the new observable
	 * @param function The function to apply to this observable's value
	 * @return The new observable whose value is a function of this observable's value
	 */
	default <R> ObservableValue<R> map(Class<R> type, Function<? super T, R> function) {
		return map(type == null ? null : TypeTokens.get().of(type), function);
	}

	/**
	 * <p>
	 * This method is supported for compatibility, but {@link #transform(TypeToken, Function)} is a more flexible method for combining
	 * values
	 * </p>
	 *
	 * @param <R> The type of the new observable
	 * @param type The run-time type of the new observable
	 * @param function The function to apply to this observable's value
	 * @param options Options determining the behavior of the result
	 * @return The new observable whose value is a function of this observable's value
	 */
	default <R> ObservableValue<R> map(TypeToken<R> type, Function<? super T, R> function, Consumer<XformOptions> options) {
		return transform(type, tx -> {
			if (options != null)
				options.accept(tx);
			return tx.map(function);
		});
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
	 * <p>
	 * Composes this observable into another observable that depends on this one and one other
	 * </p>
	 * <p>
	 * This method is supported for compatibility, but {@link #transform(TypeToken, Function)} is a more flexible method for combining
	 * values
	 * </p>
	 *
	 * @param <U> The type of the other argument observable
	 * @param <R> The type of the new observable
	 * @param function The function to apply to the values of the observables
	 * @param arg The other observable to be composed
	 * @return The new observable whose value is a function of this observable's value and the other's
	 */
	default <U, R> ObservableValue<R> combine(BiFunction<? super T, ? super U, R> function, ObservableValue<U> arg) {
		return combine(null, function, arg, null);
	}

	/**
	 * <p>
	 * Composes this observable into another observable that depends on this one and one other
	 * </p>
	 * <p>
	 * This method is supported for compatibility, but {@link #transform(TypeToken, Function)} is a more flexible method for combining
	 * values
	 * </p>
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
		return transform(type, tx -> {
			if (options != null)
				options.accept(tx);
			return tx.combineWith(arg).combine(function);
		});
	}

	/**
	 * <p>
	 * Composes this observable into another observable that depends on this one and two others
	 * </p>
	 * <p>
	 * This method is supported for compatibility, but {@link #transform(TypeToken, Function)} is a more flexible method for combining
	 * values
	 * </p>
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
		return combine(null, function, arg2, arg3, null);
	}

	/**
	 * <p>
	 * Composes this observable into another observable that depends on this one and two others
	 * </p>
	 * <p>
	 * This method is supported for compatibility, but {@link #transform(TypeToken, Function)} is a more flexible method for combining
	 * values
	 * </p>
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
		return transform(type, tx -> {
			if (options != null)
				options.accept(tx);
			return tx.combineWith(arg2).combineWith(arg3).combine(function);
		});
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
	public static <X> ObservableValue<X> of(X value) {
		if (value == null)
			throw new IllegalArgumentException("Cannot call constant(value) with a null value.  Use constant(TypeToken<X>, X).");
		return new ConstantObservableValue<>(TypeTokens.get().of((Class<X>) value.getClass()), value);
	}

	/**
	 * @param <X> The compile-time type of the value to wrap
	 * @param type The run-time type of the value to wrap
	 * @param value The value to wrap
	 * @return An observable that always returns the given value
	 */
	public static <X> ObservableValue<X> of(Class<X> type, X value) {
		return new ConstantObservableValue<>(TypeTokens.get().of(type), value);
	}

	/**
	 * @param <X> The compile-time type of the value to wrap
	 * @param type The run-time type of the value to wrap
	 * @param value The value to wrap
	 * @return An observable that always returns the given value
	 */
	public static <X> ObservableValue<X> of(TypeToken<X> type, X value) {
		return new ConstantObservableValue<>(type, value);
	}

	/**
	 * @param <X> The compile-time type of the value to wrap
	 * @param type The run-time type of the value to wrap
	 * @param value Supplies the value for the observable
	 * @param stamp The stamp for the synthetic value
	 * @param changes The observable that signals that the value may have changed
	 * @return An observable that supplies the value of the given supplier, firing change events when the given observable fires
	 */
	public static <X> ObservableValue<X> of(Class<X> type, Supplier<? extends X> value, LongSupplier stamp, Observable<?> changes) {
		return of(TypeTokens.get().of(type), value, stamp, changes);
	}

	/**
	 * @param <X> The compile-time type of the value to wrap
	 * @param type The run-time type of the value to wrap
	 * @param value Supplies the value for the observable
	 * @param stamp The stamp for the synthetic value
	 * @param changes The observable that signals that the value may have changed
	 * @return An observable that supplies the value of the given supplier, firing change events when the given observable fires
	 */
	public static <X> ObservableValue<X> of(TypeToken<X> type, Supplier<? extends X> value, LongSupplier stamp, Observable<?> changes) {
		return new SyntheticObservable<>(type, value, stamp, changes);
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
		Observable<ObservableValueEvent<? extends Observable<? extends T>>> changes;
		changes = (Observable<ObservableValueEvent<? extends Observable<? extends T>>>) (Observable<?>) value.changes();
		return new Observable.WrappingObservable<ObservableValueEvent<? extends Observable<? extends T>>, T>(changes) {
			@Override
			public Subscription subscribe(Observer<? super T> observer) {
				return getWrapped().subscribe(new Observer<ObservableValueEvent<? extends Observable<? extends T>>>() {
					@Override
					public <E extends ObservableValueEvent<? extends Observable<? extends T>>> void onNext(E event) {
						if (event.getNewValue() != null) {
							event.getNewValue().takeUntil(value.noInitChanges()).subscribe(new Observer<T>() {
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

			@Override
			public boolean isLockSupported() {
				return value.changes().isLockSupported();
			}

			@Override
			public Transaction lock() {
				return Lockable.lock(value.changes(), value::get);
			}

			@Override
			public Transaction tryLock() {
				return Lockable.tryLock(value.changes(), value::get);
			}

			@Override
			protected Object createIdentity() {
				return Identifiable.wrap(value.getIdentity(), "flatten");
			}
		};
	}

	/**
	 * Creates an observable value that reflects the value of the first value in the given sequence passing the given test, or the value
	 * given by the default if none of the values in the sequence pass. This can also be accomplished via:
	 *
	 * <code>
	 * 	{@link ObservableCollection#of(TypeToken, Object...) ObservableCollection.of(type, values)}.collect()
	 * {@link ObservableCollection#observeFind(Predicate) .observeFind(test, ()->null, true)}.find()
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
		Observable<?>[] changes = new Observable[components.length];
		for (int i = 0; i < components.length; i++)
			changes[i] = components[i] == null ? null : components[i].noInitChanges();
		return of(type, value, () -> Stamped.compositeStamp(Arrays.asList(components)), Observable.or(changes));
	}

	/**
	 * Handles some of the boilerplate associated with an observable value wrapping another
	 *
	 * @param <F> The type of the wrapped value
	 * @param <T> The type of this value
	 */
	abstract class WrappingObservableValue<F, T> implements ObservableValue<T> {
		protected final ObservableValue<F> theWrapped;
		private Object theIdentity;

		public WrappingObservableValue(ObservableValue<F> wrapped) {
			theWrapped = wrapped;
		}

		protected ObservableValue<F> getWrapped() {
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
		public ThreadConstraint getThreadConstraint() {
			return theWrapped.getThreadConstraint();
		}

		@Override
		public long getStamp() {
			return theWrapped.getStamp();
		}

		@Override
		public int hashCode() {
			return getIdentity().hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof Identifiable)
				obj = ((Identifiable) obj).getIdentity();
			return getIdentity().equals(obj);
		}

		@Override
		public String toString() {
			return getIdentity().toString();
		}
	}

	/**
	 * Implements {@link ObservableValue#changes()} by default
	 *
	 * @param <T> The type of the value
	 */
	public class ObservableValueChanges<T> implements Observable<ObservableValueEvent<T>> {
		private final ObservableValue<T> theValue;
		private final Observable<ObservableValueEvent<T>> theNoInitChanges;
		private Object theIdentity;

		/** @param value The value that this changes observable is for */
		public ObservableValueChanges(ObservableValue<T> value) {
			theValue = value;
			theNoInitChanges = value.noInitChanges();
		}

		@Override
		public Object getIdentity() {
			if (theIdentity == null)
				theIdentity = Identifiable.wrap(theValue.getIdentity(), "changes");
			return theIdentity;
		}

		@Override
		public Subscription subscribe(Observer<? super ObservableValueEvent<T>> observer) {
			try (Transaction t = theNoInitChanges.lock()) {
				ObservableValueEvent<T> initEvent = theValue.createInitialEvent(theValue.get(), null);
				try (Transaction eventT = initEvent.use()) {
					observer.onNext(initEvent);
				}
				return theNoInitChanges.subscribe(observer);
			}
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return theNoInitChanges.getThreadConstraint();
		}

		@Override
		public boolean isSafe() {
			return theNoInitChanges.isSafe();
		}

		@Override
		public Transaction lock() {
			return theNoInitChanges.lock();
		}

		@Override
		public Transaction tryLock() {
			return theNoInitChanges.tryLock();
		}

		@Override
		public CoreId getCoreId() {
			return theNoInitChanges.getCoreId();
		}

		@Override
		public Observable<ObservableValueEvent<T>> noInit() {
			return theNoInitChanges;
		}

		@Override
		public int hashCode() {
			return getIdentity().hashCode();
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof Observable && getIdentity().equals(((Observable<?>) o).getIdentity());
		}

		@Override
		public String toString() {
			return getIdentity().toString();
		}
	}

	/**
	 * A class whose value is that of a source value, transformed by a {@link Transformation}
	 *
	 * @param <S> The type of the source value
	 * @param <T> The type of the transformed value
	 * @see ObservableValue#transform(TypeToken, Function)
	 */
	public class TransformedObservableValue<S, T> extends AbstractIdentifiable implements ObservableValue<T> {
		private final TypeToken<T> theType;
		private final ObservableValue<S> theSource;
		private final Transformation<S, T> theTransformation;
		private final Transformation.Engine<S, T> theEngine;
		private final TransformedElement<S, T> theElement;
		private volatile long theSourceStamp;
		private S theCachedSource;
		private final ListenerList<Observer<? super ObservableValueEvent<T>>> theObservers;

		/**
		 * @param type The type for this value
		 * @param source The source value to be transformed
		 * @param transformation The transformation to apply to the source value
		 */
		public TransformedObservableValue(TypeToken<T> type, ObservableValue<S> source, Transformation<S, T> transformation) {
			theType = type != null ? type : (TypeToken<T>) TypeToken.of(transformation.getCombination().getClass())
				.resolveType(BiFunction.class.getTypeParameters()[1]);
			theSource = source;
			theTransformation = transformation;
			theEngine = theTransformation.createEngine(Equivalence.DEFAULT);
			theElement = theEngine.createElement(theSource::get);
			theSourceStamp = -1;
			theObservers = ListenerList.build().withInUse(new ListenerList.InUseListener() {
				private Subscription theSourceSub;
				private Subscription theTransformSub;

				@Override
				public void inUseChanged(boolean inUse) {
					if (!inUse) {
						if (getTransformation().isCached()) {
							// So the get() method doesn't need to re-evaluate after cessation of listening
							// unless something actually changes
							theSourceStamp = theSource.getStamp();
						}
						Subscription.forAll(theSourceSub, theTransformSub).unsubscribe();
						theSourceSub = null;
						theTransformSub = null;
						return;
					}
					try (Transaction t = Lockable.lockAll(theSource, theEngine)) {
						theSourceSub = theSource.changes().act(evt -> {
							try (Transaction t2 = theEngine.lock()) {
								if (getTransformation().isCached())
									theCachedSource = evt.getNewValue();
								if (evt.isInitial()) {
									// This call just makes sure the internal state is up-to-date,
									// we don't have to do anything with the return values
									getState();
								} else {
									BiTuple<T, T> change = theElement.sourceChanged(evt.getOldValue(), evt.getNewValue(), theEngine.get());
									if (!evt.isInitial() && change != null)
										fire(change.getValue1(), change.getValue2(), evt);
								}
							}
						});
						theTransformSub = theEngine.noInitChanges().act(evt -> {
							BiTuple<T, T> change = theElement.transformationStateChanged(evt.getOldValue(), evt.getNewValue());
							if (change != null)
								fire(change.getValue1(), change.getValue2(), evt);
						});
					}
				}

				private void fire(T oldValue, T newValue, Object cause) {
					ObservableValueEvent<T> evt = createChangeEvent(oldValue, newValue, cause);
					try (Transaction t = evt.use()) {
						theObservers.forEach(//
							obs -> obs.onNext(evt));
					}
				}
			}).build();
		}

		/** @return The source value being transformed */
		protected ObservableValue<S> getSource() {
			return theSource;
		}

		/** @return The transformation applied to the source value */
		public Transformation<S, T> getTransformation() {
			return theTransformation;
		}

		/** @return The engine driving the transformation */
		protected Transformation.Engine<S, T> getEngine() {
			return theEngine;
		}

		/**
		 * Ensures that this value's state is up-to-date with any changes that may have occurred since the last poll, and returns the state
		 * of this transformed value.
		 *
		 * @return A tuple containing the current transformed element and transformation state of the engine
		 */
		protected BiTuple<TransformedElement<S, T>, TransformationState> getState() {
			Transformation.TransformationState state = theEngine.get();
			if (!theObservers.isEmpty() || !theTransformation.isCached())
				return new BiTuple<>(theElement, state);
			long stamp = theSource.getStamp();
			if (stamp == -1 || stamp != theSourceStamp) {
				try (Transaction t = lock()) {
					stamp = theSource.getStamp();
					theSourceStamp = stamp;
					S source = theSource.get();
					theCachedSource = source;
					theElement.sourceChanged(theCachedSource, source, theEngine.get());
				}
			}
			return new BiTuple<>(theElement, state);
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(theSource.getIdentity(), "transform", theTransformation);
		}

		@Override
		public long getStamp() {
			return Stamped.compositeStamp(theSource.getStamp(), theEngine.getStamp());
		}

		@Override
		public TypeToken<T> getType() {
			return theType;
		}

		@Override
		public boolean isLockSupported() {
			return theSource.isLockSupported() || theEngine.isLockSupported();
		}

		@Override
		public T get() {
			BiTuple<TransformedElement<S, T>, TransformationState> state = getState();
			return state.getValue1().getCurrentValue(state.getValue2());
		}

		@Override
		public Observable<ObservableValueEvent<T>> noInitChanges() {
			class Changes extends AbstractIdentifiable implements Observable<ObservableValueEvent<T>> {
				@Override
				public Object createIdentity() {
					return Identifiable.wrap(TransformedObservableValue.this.getIdentity(), "noInitChanges");
				}

				@Override
				public Subscription subscribe(Observer<? super ObservableValueEvent<T>> observer) {
					return theObservers.add(observer, true)::run;
				}

				@Override
				public boolean isSafe() {
					return theSource.isLockSupported() || theEngine.isLockSupported();
				}

				@Override
				public ThreadConstraint getThreadConstraint() {
					return ThreadConstrained.getThreadConstraint(theSource, theEngine);
				}

				@Override
				public Transaction lock() {
					return Lockable.lockAll(theSource, theEngine);
				}

				@Override
				public Transaction tryLock() {
					return Lockable.tryLockAll(theSource, theEngine);
				}

				@Override
				public CoreId getCoreId() {
					return Lockable.getCoreId(theSource, theEngine);
				}
			}
			return new Changes();
		}
	}

	/**
	 * Implements {@link ObservableValue#takeUntil(Observable)}
	 *
	 * @param <T> The type of the value
	 */
	class ObservableValueTakenUntil<T> extends WrappingObservableValue<T, T> {
		private final Observable<ObservableValueEvent<T>> theChanges;
		private final boolean isTerminating;
		private final Observable<?> theUntil;

		protected ObservableValueTakenUntil(ObservableValue<T> wrap, Observable<?> until, boolean terminate) {
			super(wrap);
			isTerminating = terminate;
			theUntil = until;
			theChanges = new Observable.ObservableTakenUntil<>(wrap.noInitChanges(), until, terminate, () -> {
				T value = wrap.get();
				return wrap.createChangeEvent(value, value);
			});
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(getWrapped().getIdentity(), isTerminating ? "takeUntil" : "unsubscribeOn", theUntil);
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
		public Observable<ObservableValueEvent<T>> noInitChanges() {
			return theChanges;
		}
	}

	/**
	 * Implements {@link ObservableValue#refresh(Observable)}
	 *
	 * @param <T> The type of the value
	 */
	class RefreshingObservableValue<T> extends WrappingObservableValue<T, T> {
		private final Observable<?> theRefresh;
		private Object theChangesIdentity;

		protected RefreshingObservableValue(ObservableValue<T> wrap, Observable<?> refresh) {
			super(wrap);
			theRefresh = refresh;
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(getWrapped().getIdentity(), "refresh", theRefresh.getIdentity());
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
		public Observable<ObservableValueEvent<T>> noInitChanges() {
			return new Observable<ObservableValueEvent<T>>() {
				@Override
				public Object getIdentity() {
					if (theChangesIdentity == null)
						theChangesIdentity = Identifiable.wrap(theWrapped.noInitChanges().getIdentity(), "refresh",
							theRefresh.getIdentity());
					return theChangesIdentity;
				}

				@Override
				public Subscription subscribe(Observer<? super ObservableValueEvent<T>> observer) {
					Subscription[] refireSub = new Subscription[1];
					boolean[] completed = new boolean[1];
					Subscription outerSub = theWrapped.noInitChanges().subscribe(new Observer<ObservableValueEvent<T>>() {
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
						try (Transaction t = evt2.use()) {
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
				public ThreadConstraint getThreadConstraint() {
					return theWrapped.getThreadConstraint();
				}

				@Override
				public boolean isSafe() {
					return theWrapped.changes().isSafe() && theRefresh.isSafe();
				}

				@Override
				public Transaction lock() {
					return Lockable.lockAll(theWrapped, theRefresh);
				}

				@Override
				public Transaction tryLock() {
					return Lockable.tryLockAll(theWrapped, theRefresh);
				}

				@Override
				public CoreId getCoreId() {
					return Lockable.getCoreId(theWrapped, theRefresh);
				}
			};
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

		private Object theIdentity;

		/**
		 * @param type The type of this observable value
		 * @param value This observable value's value
		 */
		public ConstantObservableValue(TypeToken<T> type, T value) {
			theType = type;
			theValue = TypeTokens.get().cast(type, value);
		}

		@Override
		public Object getIdentity() {
			if (theIdentity == null)
				theIdentity = Identifiable.idFor(theValue, () -> String.valueOf(theValue), () -> Objects.hashCode(theValue),
					other -> Objects.equals(theValue, other));
			return theIdentity;
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return ThreadConstraint.NONE;
		}

		@Override
		public long getStamp() {
			return 0;
		}

		@Override
		public Observable<ObservableValueEvent<T>> noInitChanges() {
			return Observable.empty();
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
		public String toString() {
			return "" + theValue;
		}
	}

	/**
	 * Implements {@link ObservableValue#of(TypeToken, Supplier, LongSupplier, Observable)}
	 *
	 * @param <T> The type of this value
	 */
	class SyntheticObservable<T> implements ObservableValue<T> {
		private final TypeToken<T> theType;
		private final Supplier<? extends T> theValue;
		private final LongSupplier theStamp;
		private final Observable<?> theChanges;
		private Object theIdentity;
		private Object theChangeIdentity;
		private Object theNoInitChangeIdentity;

		public SyntheticObservable(TypeToken<T> type, Supplier<? extends T> value, LongSupplier stamp, Observable<?> changes) {
			theType = type;
			theValue = value;
			theStamp = stamp;
			theChanges = changes;
		}

		@Override
		public TypeToken<T> getType() {
			return theType;
		}

		@Override
		public long getStamp() {
			return theStamp.getAsLong();
		}

		@Override
		public Object getIdentity() {
			if (theIdentity == null)
				theIdentity = Identifiable.wrap(theChanges.getIdentity(), "synthetic", theValue);
			return theIdentity;
		}

		@Override
		public T get() {
			return theValue.get();
		}

		@Override
		public Observable<ObservableValueEvent<T>> changes() {
			return changes(true);
		}

		@Override
		public Observable<ObservableValueEvent<T>> noInitChanges() {
			return changes(false);
		}

		private Observable<ObservableValueEvent<T>> changes(boolean withInit) {
			return new Observable<ObservableValueEvent<T>>() {
				@Override
				public Object getIdentity() {
					if (withInit) {
						if (theChangeIdentity == null)
							theChangeIdentity = Identifiable.wrap(SyntheticObservable.this.getIdentity(), "changes");
						return theChangeIdentity;
					} else {
						if (theNoInitChangeIdentity == null)
							theNoInitChangeIdentity = Identifiable.wrap(SyntheticObservable.this.getIdentity(), "noInitChanges");
						return theNoInitChangeIdentity;
					}
				}

				@Override
				public Subscription subscribe(Observer<? super ObservableValueEvent<T>> observer) {
					class SyntheticChanges implements Observer<Object> {
						T theCurrentValue;
						boolean isInitialized;

						void initialize() {
							isInitialized = true;
							theCurrentValue = theValue.get();
							if (withInit)
								observer.onNext(createInitialEvent(theCurrentValue, null));
						}

						@Override
						public <V> void onNext(V value) {
							boolean init = !isInitialized;
							T newValue = theValue.get();
							T oldValue = theCurrentValue;
							theCurrentValue = newValue;
							if (init) {
								isInitialized = true;
								if (!withInit)
									init = false;
							}
							if (init)
								observer.onNext(createInitialEvent(newValue, value));
							else
								observer.onNext(createChangeEvent(oldValue, newValue, value));
						}

						@Override
						public <V> void onCompleted(V value) {
							T oldValue;
							if (isInitialized)
								oldValue = theCurrentValue;
							else {
								theCurrentValue = oldValue = theValue.get();
								isInitialized = true;
							}
							observer.onCompleted(createChangeEvent(oldValue, oldValue, value));
						}
					}
					SyntheticChanges changes = new SyntheticChanges();
					Subscription sub = theChanges.subscribe(changes);
					if (!changes.isInitialized) {
						try (Transaction t = theChanges.lock()) {
							if (!changes.isInitialized) {
								changes.initialize();
							}
						}
					}
					return sub;
				}

				@Override
				public ThreadConstraint getThreadConstraint() {
					return theChanges.getThreadConstraint();
				}

				@Override
				public boolean isSafe() {
					return theChanges.isSafe();
				}

				@Override
				public Transaction lock() {
					return theChanges.lock();
				}

				@Override
				public Transaction tryLock() {
					return theChanges.tryLock();
				}

				@Override
				public CoreId getCoreId() {
					return theChanges.getCoreId();
				}

				@Override
				public Observable<ObservableValueEvent<T>> noInit() {
					if (withInit)
						return noInitChanges();
					else
						return this;
				}
			};
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
		private Object theIdentity;
		private Object theChangesIdentity;
		private Object theNoInitChangesIdentity;

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
		public Object getIdentity() {
			if (theIdentity == null)
				theIdentity = Identifiable.wrap(theValue.getIdentity(), "flatten");
			return theIdentity;
		}

		@Override
		public long getStamp() {
			long stamp = theValue.getStamp();
			ObservableValue<? extends T> wrapped = theValue.get();
			if (wrapped != null)
				stamp ^= Long.rotateRight(wrapped.getStamp(), 32);
			return stamp;
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
			return new FlattenedValueChanges(true);
		}

		@Override
		public Observable<ObservableValueEvent<T>> noInitChanges() {
			return new FlattenedValueChanges(false);
		}

		@Override
		public String toString() {
			return "flat(" + theValue + ")";
		}

		private class FlattenedValueChanges implements Observable<ObservableValueEvent<T>> {
			private final boolean withInitialEvent;

			public FlattenedValueChanges(boolean withInitialEvent) {
				this.withInitialEvent = withInitialEvent;
			}

			@Override
			public Object getIdentity() {
				if (withInitialEvent) {
					if (theChangesIdentity == null)
						theChangesIdentity = Identifiable.wrap(FlattenedObservableValue.this.getIdentity(), "changes");
					return theChangesIdentity;
				} else {
					if (theNoInitChangesIdentity == null)
						theNoInitChangesIdentity = Identifiable.wrap(FlattenedObservableValue.this.getIdentity(), "noInitChanges");
					return theNoInitChangesIdentity;
				}
			}

			@Override
			public Subscription subscribe(Observer<? super ObservableValueEvent<T>> observer) {
				ObservableValue<T> retObs = FlattenedObservableValue.this;
				AtomicReference<Subscription> innerSub = new AtomicReference<>();
				boolean[] firedInit = new boolean[1];
				Object[] old = new Object[1];
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
								if (innerObs != null && !innerObs.equals(event.getOldValue())) {
									boolean[] firedInit2 = new boolean[1];
									Subscription.unsubscribe(
										innerSub.getAndSet(innerObs.changes().subscribe(new Observer<ObservableValueEvent<? extends T>>() {
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
														toFire = withInitialEvent
														? retObs.createInitialEvent(event2.getNewValue(), event2.getCauses()) : null;
														else
															toFire = retObs.createChangeEvent(innerOld, event2.getNewValue(),
																event2.getCauses());
													if (toFire != null) {
														try (Transaction t = toFire.use()) {
															observer.onNext(toFire);
														}
													}
													old[0] = event2.getNewValue();
												} finally {
													theLock.unlock();
												}
											}

											@Override
											public <V2 extends ObservableValueEvent<? extends T>> void onCompleted(V2 value) {
											}
										})));
									if (!firedInit2[0])
										throw new IllegalStateException(innerObs + " did not fire an initial value");
								} else {
									T newValue = get(event.getNewValue());
									ObservableValueEvent<T> toFire;
									if (event.isInitial())
										toFire = withInitialEvent ? retObs.createInitialEvent(newValue, event.getCauses()) : null;
										else
											toFire = retObs.createChangeEvent((T) old[0], newValue, event.getCauses());
									old[0] = newValue;
									if (toFire != null) {
										try (Transaction t = toFire.use()) {
											observer.onNext(toFire);
										}
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
									get(event.getNewValue()), event.getCauses());
								try (Transaction t = toFire.use()) {
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
			public ThreadConstraint getThreadConstraint() {
				if (theValue.getThreadConstraint() == ThreadConstraint.NONE) {
					ObservableValue<? extends T> obs = theValue.get();
					return obs == null ? ThreadConstraint.NONE : obs.getThreadConstraint();
				}
				return ThreadConstraint.ANY; // Can't know
			}

			@Override
			public boolean isSafe() {
				return false;
			}

			@Override
			public Transaction lock() {
				return Lockable.lock(theValue, theValue::get);
			}

			@Override
			public Transaction tryLock() {
				return Lockable.tryLock(theValue, theValue::get);
			}

			@Override
			public CoreId getCoreId() {
				// Best we can do is a snapshot
				try (Transaction t = theValue.lock()) {
					return Lockable.getCoreId(theValue, theValue.get());
				}
			}
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
		private Object theIdentity;
		private Object theChangesIdentity;

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
		public Object getIdentity() {
			if (theIdentity == null) {
				StringBuilder str = new StringBuilder("first(");
				List<Object> obsIds = new ArrayList<>(theValues.length + 2);
				for (ObservableValue<? extends T> value : theValues) {
					obsIds.add(value.getIdentity());
					str.append(value.getIdentity()).append(", ");
				}
				obsIds.add(theTest);
				str.append(theTest).append(", ");
				obsIds.add(theDefault);
				str.append(theDefault).append(')');
				theIdentity = Identifiable.baseId(str.toString(), obsIds);
			}
			return theIdentity;
		}

		@Override
		public long getStamp() {
			return Stamped.compositeStamp(Arrays.asList(theValues), Stamped::getStamp);
		}

		@Override
		public T get() {
			for (ObservableValue<? extends T> v : theValues) {
				T value = v.get();
				if (theTest.test(value))
					return value;
			}
			return theDefault.get();
		}

		@Override
		public Observable<ObservableValueEvent<T>> changes() {
			return new FirstValueChanges();
		}

		@Override
		public Observable<ObservableValueEvent<T>> noInitChanges() {
			return changes().noInit();
		}

		class FirstValueChanges implements Observable<ObservableValueEvent<T>> {
			@Override
			public Object getIdentity() {
				if (theChangesIdentity == null)
					theChangesIdentity = Identifiable.wrap(FirstObservableValue.this.getIdentity(), "changes");
				return theChangesIdentity;
			}

			@Override
			public Subscription subscribe(Observer<? super ObservableValueEvent<T>> observer) {
				if (theValues.length == 0) {
					ObservableValueEvent<T> evt = createInitialEvent(theDefault.get(), null);
					try (Transaction t = evt.use()) {
						observer.onNext(evt);
					}
					return Subscription.NONE;
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
							boolean found;
							try {
								found = !complete && theTest.test(event.getNewValue());
							} catch (RuntimeException e) {
								e.printStackTrace();
								found = false;
							}
							int nextIndex = index + 1;
							if (!found) {
								while (nextIndex < theValues.length && finished[nextIndex])
									nextIndex++;
							}
							ObservableValueEvent<T> toFire;
							if (complete) {
								finished[index] = true;
								valueSubs[index] = null;
							}
							boolean allComplete = complete && allCompleted();
							if (!found) {
								if (!isFound && !event.isInitial())
									toFire = null;
								else if (nextIndex < theValues.length) {
									toFire = null;
									valueSubs[nextIndex] = theValues[nextIndex].changes().subscribe(new ElementFirstObserver(nextIndex));
								} else if (allComplete) {
									toFire = createChangeEvent((T) lastValue[0], (T) lastValue[0], event);
								} else {
									T def;
									try {
										def = theDefault.get();
									} catch (RuntimeException e) {
										def = null;
										e.printStackTrace();
									}
									if (!hasFiredInit[0])
										toFire = createInitialEvent(def, event);
									else if (def != lastValue[0])
										toFire = createChangeEvent((T) lastValue[0], def, event);
									else
										toFire = null;
									lastValue[0] = def;
								}
							} else {
								if (!isFound) {
									for (int i = index + 1; i < valueSubs.length; i++) {
										if (valueSubs[i] != null) {
											valueSubs[i].unsubscribe();
											valueSubs[i] = null;
										}
									}
								}
								if (!hasFiredInit[0])
									toFire = createInitialEvent(event.getNewValue(), event);
								else
									toFire = createChangeEvent((T) lastValue[0], event.getNewValue(), event);
								lastValue[0] = event.getNewValue();
							}
							isFound = found;

							if (toFire != null) {
								hasFiredInit[0] = true;
								lastValue[0] = toFire.getNewValue();
								try (Transaction t = toFire.use()) {
									if (allComplete)
										observer.onCompleted(toFire);
									else
										observer.onNext(toFire);
								}
							}
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
				valueSubs[0] = theValues[0].changes().subscribe(new ElementFirstObserver(0));
				return () -> {
					Subscription.forAll(valueSubs).unsubscribe();
				};
			}

			@Override
			public ThreadConstraint getThreadConstraint() {
				return ThreadConstrained.getThreadConstraint(null, Arrays.asList(theValues), LambdaUtils.identity());
			}

			@Override
			public boolean isSafe() {
				return true;
			}

			@Override
			public Transaction lock() {
				return Lockable.lockAll(null, () -> Arrays.asList(theValues), ObservableValue::noInitChanges);
			}

			@Override
			public Transaction tryLock() {
				return Lockable.tryLockAll(null, () -> Arrays.asList(theValues), ObservableValue::noInitChanges);
			}

			@Override
			public CoreId getCoreId() {
				return Lockable.getCoreId(null, () -> Arrays.asList(theValues), ObservableValue::noInitChanges);
			}
		}
	}
}
