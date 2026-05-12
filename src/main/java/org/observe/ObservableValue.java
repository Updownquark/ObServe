package org.observe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.observe.Observer.NoArgObserver;
import org.observe.Observer.SimpleObserver;
import org.observe.Transformation.TransformationState;
import org.observe.Transformation.TransformedElement;
import org.observe.collect.ObservableCollection;
import org.qommons.BiTuple;
import org.qommons.Causable;
import org.qommons.Identifiable;
import org.qommons.Lockable;
import org.qommons.Stamped;
import org.qommons.Subscription;
import org.qommons.ThreadConstrained;
import org.qommons.ThreadConstraint;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionLockingStrategy;
import org.qommons.collect.ListenerList;
import org.qommons.collect.ThreadConstrainedLockingStrategy;
import org.qommons.fn.FunctionUtils;
import org.qommons.fn.TriFunction;

/**
 * A value holder that can notify listeners when the value changes. The {@link #changes()} observable will always notify subscribers with an
 * {@link ObservableValueEvent#isInitial() initial} event whose old value is null and whose new value is this holder's current value before
 * the {@link Observable#subscribe(Observer)} method exits.
 *
 * @param <T> The compile-time type of this observable's value
 */
public interface ObservableValue<T> extends Supplier<T>, Lockable, Stamped, Identifiable, Eventable, CausableChanging {
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

	/**
	 * @return An observable value identical to this one, but which will not propagate {@link ObservableValueEvent#isUpdate() update} events
	 */
	default ObservableValue<T> noUpdates() {
		return new NoUpdatesValue<>(this);
	}

	@Override
	default Observable<? extends Causable> simpleChanges() {
		return noInitChanges();
	}

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

	@Override
	ObservableValue<T> alias(String alias);

	/**
	 * @return An observable that just reports this observable value's value (including the initial value) in an observable without the
	 *         event
	 */
	default Observable<T> value() {
		class ValueObservable extends AbstractIdentifiable implements Observable<T> {
			@Override
			public Subscription subscribe(Observer<? super T> observer) {
				return ObservableValue.this.changes().subscribe(new Observer<ObservableValueEvent<T>>() {
					@Override
					public void onNext(ObservableValueEvent<T> value) {
						observer.onNext(value.getNewValue());
					}

					@Override
					public void onCompleted(Supplier<Causable> cause) {
						observer.onCompleted(cause);
					}
				});
			}

			@Override
			public ThreadConstraint getThreadConstraint() {
				return ObservableValue.this.getThreadConstraint();
			}

			@Override
			public boolean isEventing() {
				return ObservableValue.this.isEventing();
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

			@Override
			public long getStamp() {
				return ObservableValue.this.getStamp();
			}

			@Override
			public CoreChangeSources getChangeSources() {
				return noInitChanges().getChangeSources();
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
		return ObservableValueEvent.createInitialEvent(this, value, cause == null ? Causable.EMPTY_CAUSES : new Object[] { cause });
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
			public CoreId getCoreId() {
				return getWrapped().getCoreId();
			}

			@Override
			public T get() {
				return getWrapped().get();
			}

			@Override
			public boolean isEventing() {
				return getWrapped().isEventing();
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
	 *
	 * @param <R> The type for the combined value
	 * @param transform Determines how this value and any other arguments are to be combined
	 * @return The transformed value
	 * @see Transformation for help using the API
	 */
	default <R> ObservableValue<R> transform(Function<Transformation.TransformationPrecursor<T, R, ?>, Transformation<T, R>> transform) {
		Transformation<T, R> def = transform.apply(new Transformation.TransformationPrecursor<>());
		if (def.getArgs().isEmpty() && FunctionUtils.isTrivial(def.getCombination()))
			return (ObservableValue<R>) this;
		ObservableValue<?>[] argValues = new ObservableValue[def.getArgs().size() + 1];
		argValues[0] = this;
		Map<ObservableValue<?>, Integer> otherArgs = new HashMap<>((int) Math.ceil(def.getArgs().size() * 1.5));
		Iterator<ObservableValue<?>> argIter = def.getArgs().iterator();
		for (int i = 1; i < argValues.length; i++) {
			ObservableValue<?> arg = argIter.next();
			argValues[i] = arg;
			otherArgs.put(arg, i - 1);
		}
		return new TransformedObservableValue<>(this, def);
	}

	/**
	 * <p>
	 * Composes this observable into another observable that depends on this one
	 * </p>
	 * <p>
	 * This method is supported for compatibility, but {@link #transform(Function)} is a more flexible method for combining values
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
	 * This method is supported for compatibility, but {@link #transform(Function)} is a more flexible method for combining values
	 * </p>
	 *
	 * @param <R> The type of the new observable
	 * @param type The run-time type of the new observable
	 * @param function The function to apply to this observable's value
	 * @param options Options determining the behavior of the result
	 * @return The new observable whose value is a function of this observable's value
	 */
	default <R> ObservableValue<R> map(Function<? super T, R> function, Consumer<XformOptions> options) {
		return transform(tx -> {
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
	 * This method is supported for compatibility, but {@link #transform(Function)} is a more flexible method for combining values
	 * </p>
	 *
	 * @param <U> The type of the other argument observable
	 * @param <R> The type of the new observable
	 * @param function The function to apply to the values of the observables
	 * @param arg The other observable to be composed
	 * @return The new observable whose value is a function of this observable's value and the other's
	 */
	default <U, R> ObservableValue<R> combine(BiFunction<? super T, ? super U, R> function, ObservableValue<U> arg) {
		return combine(function, arg, null);
	}

	/**
	 * <p>
	 * Composes this observable into another observable that depends on this one and one other
	 * </p>
	 * <p>
	 * This method is supported for compatibility, but {@link #transform(Function)} is a more flexible method for combining values
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
	default <U, R> ObservableValue<R> combine(BiFunction<? super T, ? super U, R> function, ObservableValue<U> arg,
		Consumer<XformOptions> options) {
		return transform(tx -> {
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
	 * This method is supported for compatibility, but {@link #transform(Function)} is a more flexible method for combining values
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
		return combine(function, arg2, arg3, null);
	}

	/**
	 * <p>
	 * Composes this observable into another observable that depends on this one and two others
	 * </p>
	 * <p>
	 * This method is supported for compatibility, but {@link #transform(Function)} is a more flexible method for combining values
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
	default <U, V, R> ObservableValue<R> combine(TriFunction<? super T, ? super U, ? super V, R> function, ObservableValue<U> arg2,
		ObservableValue<V> arg3, Consumer<XformOptions> options) {
		return transform(tx -> {
			if (options != null)
				options.accept(tx);
			return tx.combineWith(arg2).combineWith(arg3).combine(function);
		});
	}

	/**
	 * @param until The observable to complete the value
	 * @return An observable value identical to this one, but that will {@link Observer#onCompleted(Supplier) complete} when
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
	 * @param refresh A function to produce a refresh observable for the content of this observable value
	 * @return An ObservableValue that fires events whenever the produced refresh observable does
	 */
	default ObservableValue<T> refreshEach(Function<? super T, ? extends Observable<?>> refresh) {
		return new RefreshEachValue<>(this, refresh);
	}

	/**
	 * @param threading The thread constraint for the new observable to obey
	 * @param until An observable to cease the safe observable's synchronization with this value
	 * @return An observable value with same value as this one, but is safe for use on the given thread and fires its events there
	 */
	default ObservableValue<T> safe(ThreadConstraint threading) {
		if (getThreadConstraint() == threading || getThreadConstraint() == ThreadConstraint.NONE || threading == ThreadConstraint.ANY)
			return this;
		return new SafeObservableValue<>(this, threading);
	}

	/** @return A value the same as this, but which caches its value for performance */
	default ObservableValue<T> cached() {
		return new CachedObservableValue<>(this);
	}

	/**
	 * @param <X> The compile-time type of the value to wrap
	 * @param value The value to wrap
	 * @return An observable that always returns the given value
	 */
	public static <X> ObservableValue<X> of(X value) {
		return new ConstantObservableValue<>(value);
	}

	/**
	 * @param <X> The compile-time type of the value to wrap
	 * @param value Supplies the value for the observable
	 * @param changes The observable that signals that the value may have changed
	 * @return An observable that supplies the value of the given supplier, firing change events when the given observable fires
	 */
	public static <X> SyntheticObservable<X> of(Supplier<? extends X> value, Observable<?> changes) {
		return of(value, changes::getStamp, changes, () -> Identifiable.wrap(changes.getIdentity(), "->", value));
	}

	/**
	 * @param <X> The compile-time type of the value to wrap
	 * @param value Supplies the value for the observable
	 * @param stamp The stamp for the synthetic value
	 * @param changes The observable that signals that the value may have changed
	 * @param identity The identity for the observable
	 * @return An observable that supplies the value of the given supplier, firing change events when the given observable fires
	 */
	public static <X> SyntheticObservable<X> of(Supplier<? extends X> value, LongSupplier stamp, Observable<?> changes,
		Supplier<?> identity) {
		return new SyntheticObservable<>(value, stamp, changes, identity);
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
		if (ov instanceof ConstantObservableValue) {
			ObservableValue<? extends T> v = ov.get();
			if (v == null)
				return ObservableValue.of(defaultValue == null ? null : defaultValue.get());
			return (ObservableValue<T>) v;
		}
		return new FlattenedObservableValue<>(ov, defaultValue);
	}

	/**
	 * @param <T> The type of observables in the value
	 * @param value An observable value containing an observable
	 * @return An observable that fires whenever the current contents of the value fires. This observable will not complete until the value
	 *         completes.
	 */
	public static <T> Observable<T> flattenObservableValue(ObservableValue<? extends Observable<? extends T>> value) {
		return new FlattenedValueObservable<>(value);
	}

	/**
	 * Creates an observable value that reflects the value of the first value in the given sequence passing the given test, or the value
	 * given by the default if none of the values in the sequence pass. This can also be accomplished via:
	 *
	 * <code>
	 * 	{@link ObservableCollection#of(Object...) ObservableCollection.of(type, values)}.collect()
	 * {@link ObservableCollection#observeFind(Predicate) .observeFind(test, ()->null, true)}.find()
	 * {{@link #map(Function) .mapV(v->v!=null ? v : def.get()}
	 * </code>
	 *
	 * but this method only subscribes to the values in the sequence up to the one that has a passing value. This can be of great advantage
	 * if one of the earlier values is likely to pass and some of the later values are expensive to compute.
	 *
	 * @param <T> The compile-time type of the value
	 * @param test The test to for the value. If null, <code>v->v!=null</code> will be used
	 * @param def Supplies a default value in the case that none of the values in the sequence pass the test. If null, a null default will
	 *        be used.
	 * @param values The sequence of ObservableValues to get the first passing value of
	 * @return The observable for the first passing value in the sequence
	 */
	@SafeVarargs
	public static <T> ObservableValue<T> firstValue(Predicate<? super T> test, Supplier<? extends T> def,
		ObservableValue<? extends T>... values) {
		return new FirstObservableValue<>(values, test, def);
	}

	/**
	 * @param values Any number of observable booleans (a null value is equivalent to FALSE)
	 * @return An observable value that is the AND operation of all the given values
	 */
	@SafeVarargs
	public static ObservableValue<Boolean> AND(ObservableValue<? extends Boolean>... values) {
		return firstValue(FunctionUtils.BOOLEAN_PREDICATE.negate(), FunctionUtils.constantSupplier(true), values);
	}

	/**
	 * @param values Any number of observable booleans (a null value is equivalent to FALSE)
	 * @return An observable value that is the OR operation of all the given values
	 */
	@SafeVarargs
	public static ObservableValue<Boolean> OR(ObservableValue<? extends Boolean>... values) {
		return firstValue(FunctionUtils.BOOLEAN_PREDICATE, FunctionUtils.constantSupplier(false), values);
	}

	/**
	 * Assembles an observable value, with changes occurring on the basis of changes to a set of components
	 *
	 * @param <T> The type of the value to produce
	 * @param value The function to get the new value on demand
	 * @param components The components whose changes require a new value to be produced
	 * @return The new observable value
	 */
	@SafeVarargs
	public static <T> ObservableValue<T> assemble(Supplier<T> value, ObservableValue<?>... components) {
		Observable<?>[] changes = new Observable[components.length];
		for (int i = 0; i < components.length; i++)
			changes[i] = components[i] == null ? null : components[i].noInitChanges();
		Observable<?> allChanges = Observable.or(changes);
		return of(value, () -> Stamped.compositeStamp(Arrays.asList(components)), allChanges, allChanges::getIdentity);
	}

	/**
	 * @param <T> The super-type of all the observables in the parameter list
	 * @param withInitial Whether the returned observable should fire an initial event
	 * @param values All the values to listen to
	 * @return An observable that fires whenever any of the given values changes
	 */
	@SafeVarargs
	public static <T> Observable<ObservableValueEvent<? extends T>> orChanges(boolean withInitial, ObservableValue<? extends T>... values) {
		List<Observable<? extends ObservableValueEvent<? extends T>>> changesList = new ArrayList<>(values.length);
		for (ObservableValue<? extends T> value : values) {
			if (value == null) {//
			} else if (withInitial && changesList.isEmpty())
				changesList.add(value.changes());
			else
				changesList.add(value.noInitChanges());
		}
		return Observable.or(changesList.toArray(new Observable[changesList.size()]));
	}

	/**
	 * Handles some of the boilerplate associated with an observable value wrapping another
	 *
	 * @param <F> The type of the wrapped value
	 * @param <T> The type of this value
	 */
	abstract class WrappingObservableValue<F, T> extends AbstractIdentifiable implements ObservableValue<T> {
		protected final ObservableValue<F> theWrapped;

		protected WrappingObservableValue(ObservableValue<F> wrapped) {
			theWrapped = wrapped;
		}

		protected ObservableValue<F> getWrapped() {
			return theWrapped;
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return theWrapped.getThreadConstraint();
		}

		@Override
		public long getStamp() {
			return theWrapped.getStamp();
		}

		@Override
		public WrappingObservableValue<F, T> alias(String alias) {
			super.alias(alias);
			return this;
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
	 * Implements {@link ObservableValue#noUpdates()}
	 *
	 * @param <T> The type of the value
	 */
	public class NoUpdatesValue<T> extends WrappingObservableValue<T, T> {
		/** @param wrapped The value to wrap */
		public NoUpdatesValue(ObservableValue<T> wrapped) {
			super(wrapped);
		}

		@Override
		public boolean isEventing() {
			return getWrapped().isEventing();
		}

		@Override
		public T get() {
			return getWrapped().get();
		}

		@Override
		public Observable<ObservableValueEvent<T>> noInitChanges() {
			return getWrapped().noInitChanges().filter(evt -> !evt.isUpdate());
		}

		@Override
		protected Object createIdentity() {
			return getWrapped().getIdentity();
		}
	}

	/**
	 * Implements {@link ObservableValue#changes()} by default
	 *
	 * @param <T> The type of the value
	 */
	public class ObservableValueChanges<T> extends AbstractIdentifiable implements Observable<ObservableValueEvent<T>> {
		private final ObservableValue<T> theValue;
		private final Observable<ObservableValueEvent<T>> theNoInitChanges;

		/** @param value The value that this changes observable is for */
		public ObservableValueChanges(ObservableValue<T> value) {
			theValue = value;
			theNoInitChanges = value.noInitChanges();
		}

		@Override
		public boolean isEventing() {
			return theNoInitChanges.isEventing();
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(theValue.getIdentity(), "changes");
		}

		@Override
		public Subscription subscribe(Observer<? super ObservableValueEvent<T>> observer) {
			try (Transaction t = theNoInitChanges.lock()) {
				// Subscribe first, then fire the initial event.
				// One would think this doesn't matter since we've got a lock,
				// but it affects the order in which listeners are registered, e.g. for flattened values
				Subscription sub = theNoInitChanges.subscribe(observer);
				boolean success = false;
				try {
					ObservableValueEvent<T> initEvent = theValue.createInitialEvent(theValue.get(), null);
					try (Transaction eventT = initEvent.use()) {
						observer.onNext(initEvent);
					}
					success = true;
				} finally {
					if (!success)
						sub.unsubscribe();
				}
				return sub;
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
		public long getStamp() {
			return theNoInitChanges.getStamp();
		}

		@Override
		public CoreChangeSources getChangeSources() {
			return theNoInitChanges.getChangeSources();
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
	 * @see ObservableValue#transform(Function)
	 */
	public class TransformedObservableValue<S, T> extends AbstractIdentifiable implements ObservableValue<T> {
		private final ObservableValue<S> theSource;
		private final Transformation<S, T> theTransformation;
		private final Transformation.Engine<S, T> theEngine;
		private final TransformedElement<S, T> theElement;
		private volatile long theSourceStamp;
		private S theCachedSource;
		private final ListenerList<Observer<? super ObservableValueEvent<T>>> theObservers;

		/**
		 * @param source The source value to be transformed
		 * @param transformation The transformation to apply to the source value
		 */
		public TransformedObservableValue(ObservableValue<S> source, Transformation<S, T> transformation) {
			theSource = source;
			theTransformation = transformation;
			theEngine = theTransformation.createEngine(source, Equivalence.DEFAULT, err -> {
				System.err.println("Transformation error @" + this);
				err.printStackTrace();
			});
			theElement = theEngine.createElement(FunctionUtils.printableSupplier(theSource::get, theSource::toString, null));
			theSourceStamp = -1;
			theObservers = ListenerList.build()//
				.reentrancyError(() -> "Reentrancy not allowed: " + toString())//
				.withInUse(new ListenerList.InUseListener() {
					private Subscription theSourceSub;
					private Subscription theTransformSub;

					@Override
					public void inUseChanged(boolean inUse) {
						if (!inUse) {
							// if (getTransformation().isCached()) {
							// Set the stamp so the get() method doesn't need to re-evaluate after cessation of listening
							// unless something actually changes

							// Actually, it turns out that this can cause issues.
							// If this unsubscription is due to a change that has affected the source value,
							// the source may have changed without yet calling our listener, so setting this stamp would signify
							// that we have the latest value, when actually it has changed.
							// There's no way (right here) to detect this condition.
							// The right way to do this would be to set the stamp each time an event fires,
							// but stamp computation is not always super cheap.
							// Instead, we'll just let the transformation re-evaluate.
							// theSourceStamp = theSource.getStamp();
							// }
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
										getState(false, true);
									} else {
										BiTuple<T, T> change = theElement.sourceChanged(evt.getOldValue(), evt.getNewValue(),
											theEngine.get(false));
										if (!evt.isInitial() && change != null)
											fire(change.getValue1(), change.getValue2(), evt);
									}
								}
							});
							theTransformSub = theEngine.noInitChanges().act(evt -> {
								try (Transaction t2 = theSource.lock()) {
									BiTuple<T, T> change = theElement.transformationStateChanged(evt.getOldValue(), evt.getNewValue());
									if (change == null)
										return;
									T oldValue = change.getValue1();
									T newValue;
									// Check to see if the source is also changed such that we may not have received the change yet
									if (theTransformation.isCached() && (theSource.isEventing() || theObservers.isEmpty())) {
										if (checkSourceChanged(evt.getNewValue()))
											newValue = theElement.getCurrentValue(theEngine.getCachedState());
										else
											newValue = change.getValue2();
									} else
										newValue = change.getValue2();
									if (change != null)
										fire(oldValue, newValue, evt);
								}
							});
						}
					}

					private void fire(T oldValue, T newValue, Object cause) {
						if (oldValue == newValue && theObservers.isFiring())
							return; // Avoid reentrancy error
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
		 * @param withLock Whether a lock needs to be
		 * @param init Whether this call is from the initialization of listening
		 * @return A tuple containing the current transformed element and transformation state of the engine
		 */
		protected BiTuple<TransformedElement<S, T>, TransformationState> getState(boolean withLock, boolean init) {
			Transformation.TransformationState cachedState = theEngine.getCachedState();
			Transformation.TransformationState state = theEngine.get(withLock);
			if (state != cachedState)
				theElement.transformationStateChanged(cachedState, state);
			boolean checkSource;
			if (init)
				checkSource = true;
			else if (!theTransformation.isCached())
				checkSource = false;
			else if (theSource.isEventing() || theObservers.isEmpty()) {
				// If the source is eventing, it's possible that we haven't received the event that will update us yet
				checkSource = true;
			} else
				checkSource = false;
			if (checkSource)
				checkSourceChanged(state);
			return new BiTuple<>(theElement, state);
		}

		boolean checkSourceChanged(Transformation.TransformationState state) {
			if (theSourceStamp == -1 || theSource.getStamp() != theSourceStamp) {
				try (Transaction t = lock()) {
					theSourceStamp = theSource.getStamp();
					S source = theSource.get();
					S oldSource = theCachedSource;
					theCachedSource = source;
					theElement.sourceChanged(oldSource, source, state);
				}
				return true;
			} else
				return false;
		}

		@Override
		protected Object createIdentity() {
			if (theTransformation.getArgs().isEmpty() && FunctionUtils.isTrivial(theTransformation.getCombination()))
				return theSource.getIdentity();
			Identifiable.CustomIdentityBuilder idBuilder = Identifiable.buildId()//
				.withPrintedId(theSource)//
				.append(".")//
				.withPrintedId(theTransformation.getCombination());
			if (theTransformation.getArgs().isEmpty())
				idBuilder.append("()");
			else {
				idBuilder.append("(");
				boolean firstArg = true;
				for (ObservableValue<?> arg : theTransformation.getArgs()) {
					if (firstArg)
						firstArg = false;
					else
						idBuilder.append(", ");
					idBuilder.withPrintedId(arg);
				}
				idBuilder.append(")");
			}
			return idBuilder.build();
		}

		@Override
		public TransformedObservableValue<S, T> alias(String alias) {
			super.alias(alias);
			return this;
		}

		@Override
		public long getStamp() {
			return Stamped.compositeOf2Stamps(theSource.getStamp(), theEngine.getStamp());
		}

		@Override
		public boolean isLockSupported() {
			return theSource.isLockSupported() || theEngine.isLockSupported();
		}

		@Override
		public boolean isEventing() {
			return theSource.isEventing() || theEngine.isEventing();
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return ThreadConstraint.union(theSource.getThreadConstraint(), theEngine.getThreadConstraint());
		}

		@Override
		public T get() {
			BiTuple<TransformedElement<S, T>, TransformationState> state = getState(true, false);
			TransformedElement<S, T> el = state.getValue1();
			TransformationState tx = state.getValue2();
			return el.getCurrentValue(tx);
		}

		@Override
		public Observable<ObservableValueEvent<T>> noInitChanges() {
			class Changes extends AbstractIdentifiable implements Observable<ObservableValueEvent<T>> {
				@Override
				public boolean isEventing() {
					return theSource.isEventing() || theEngine.isEventing();
				}

				@Override
				public Object createIdentity() {
					return Identifiable.wrap(TransformedObservableValue.this.getIdentity(), "noInitChanges");
				}

				@Override
				public Subscription subscribe(Observer<? super ObservableValueEvent<T>> observer) {
					return theObservers.add(observer, true);
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

				@Override
				public long getStamp() {
					return Stamped.compositeOf2Stamps(theSource.getStamp(), theEngine.getStamp());
				}

				@Override
				public CoreChangeSources getChangeSources() {
					return CoreChangeSources.of(theSource.noInitChanges(), theEngine.noInitChanges());
				}
			}
			return new Changes();
		}

		@Override
		public ObservableValue<T> cached() {
			if (theTransformation.isCached())
				return this;
			else
				return ObservableValue.super.cached();
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
			theChanges = new Observable.ObservableTakenUntil<>(wrap.noInitChanges(), until, terminate);
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(getWrapped().getIdentity(), isTerminating ? "takeUntil" : "unsubscribeOn", theUntil);
		}

		@Override
		public T get() {
			return theWrapped.get();
		}

		@Override
		public Observable<ObservableValueEvent<T>> noInitChanges() {
			return theChanges;
		}

		@Override
		public boolean isEventing() {
			return getWrapped().isEventing();
		}
	}

	/**
	 * Implements {@link ObservableValue#refresh(Observable)}
	 *
	 * @param <T> The type of the value
	 */
	class RefreshingObservableValue<T> extends WrappingObservableValue<T, T> {
		private final Observable<?> theRefresh;

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
		public T get() {
			return theWrapped.get();
		}

		@Override
		public Observable<ObservableValueEvent<T>> noInitChanges() {
			class RefreshingValueChanges extends AbstractIdentifiable implements Observable<ObservableValueEvent<T>> {
				@Override
				public boolean isEventing() {
					return getWrapped().isEventing() && theRefresh.isEventing();
				}

				@Override
				protected Object createIdentity() {
					return Identifiable.wrap(theWrapped.noInitChanges().getIdentity(), "refresh", theRefresh.getIdentity());
				}

				@Override
				public Subscription subscribe(Observer<? super ObservableValueEvent<T>> observer) {
					boolean[] completed = new boolean[2];
					Subscription outerSub = theWrapped.noInitChanges().subscribe(new Observer<ObservableValueEvent<T>>() {
						@Override
						public void onNext(ObservableValueEvent<T> value) {
							observer.onNext(value);
						}

						@Override
						public void onCompleted(Supplier<Causable> cause) {
							// Just because the changes are completed doesn't mean the value is. Continue observing the refresh.
							completed[0] = true;
							if (completed[1])
								observer.onCompleted(cause);
						}
					});
					Subscription refireSub = theRefresh.subscribe(new Observer<Object>() {
						@Override
						public void onNext(Object evt) {
							T value = get();
							ObservableValueEvent<T> evt2 = createChangeEvent(value, value, evt);
							try (Transaction t = evt2.use()) {
								observer.onNext(evt2);
							}
						}

						@Override
						public void onCompleted(Supplier<Causable> cause) {
							completed[1] = true;
							if (completed[0])
								observer.onCompleted(cause);
						}
					});
					return () -> {
						outerSub.unsubscribe();
						refireSub.unsubscribe();
					};
				}

				@Override
				public ThreadConstraint getThreadConstraint() {
					return ThreadConstraint.union(theWrapped.getThreadConstraint(), theRefresh.getThreadConstraint());
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

				@Override
				public long getStamp() {
					return Stamped.compositeOf2Stamps(theWrapped.getStamp(), theRefresh.getStamp());
				}

				@Override
				public CoreChangeSources getChangeSources() {
					return CoreChangeSources.of(theWrapped.noInitChanges(), theRefresh);
				}
			}
			return new RefreshingValueChanges();
		}

		@Override
		public boolean isEventing() {
			return theWrapped.isEventing() || theRefresh.isEventing();
		}

		@Override
		public long getStamp() {
			return Stamped.compositeOf2Stamps(theWrapped.getStamp(), theRefresh.getStamp());
		}

		@Override
		public String toString() {
			return theWrapped.toString();
		}
	}

	/**
	 * Implements {@link ObservableValue#refreshEach(Function)}
	 *
	 * @param <T> The type of the value
	 */
	class RefreshEachValue<T> extends WrappingObservableValue<T, T> {
		private final Function<? super T, ? extends Observable<?>> theRefresh;
		private final ReentrantLock theLock;

		protected RefreshEachValue(ObservableValue<T> wrapped, Function<? super T, ? extends Observable<?>> refresh) {
			super(wrapped);
			theRefresh = refresh;
			theLock = new ReentrantLock();
		}

		protected Function<? super T, ? extends Observable<?>> getRefresh() {
			return theRefresh;
		}

		@Override
		public T get() {
			return getWrapped().get();
		}

		@Override
		public Transaction lock() {
			// The purpose of the refresh lock is solely to prevent simultaneous refresh events,
			// or any refresh events that would violate the contract of a held lock
			// If this lock method will obtain any exclusive locks, then locking the refresh lock is unnecessary,
			// because incoming refresh updates obtain a read lock on the parent
			return Lockable.lockAll(getWrapped(), getRefreshLock());
		}

		@Override
		public Transaction tryLock() {
			// The purpose of the refresh lock is solely to prevent simultaneous refresh events,
			// or any refresh events that would violate the contract of a held lock
			// If this lock method will obtain any exclusive locks, then locking the refresh lock is unnecessary,
			// because incoming refresh updates obtain a read lock on the parent
			return Lockable.tryLockAll(getWrapped(), getRefreshLock());
		}

		protected Lockable getRefreshLock() {
			return Lockable.lockable(theLock, this, ThreadConstraint.ANY);
		}

		@Override
		public Observable<ObservableValueEvent<T>> noInitChanges() {
			Observable<ObservableValueEvent<T>> wrappedChanges = getWrapped().changes();
			class RefreshEachChanges extends AbstractIdentifiable implements Observable<ObservableValueEvent<T>> {
				private boolean isRefreshEventing;

				@Override
				public CoreId getCoreId() {
					return wrappedChanges.getCoreId();
				}

				@Override
				protected Object createIdentity() {
					return Identifiable.wrap(RefreshEachValue.this.getIdentity(), "noInitChanges");
				}

				@Override
				public boolean isEventing() {
					return wrappedChanges.isEventing() || isRefreshEventing;
				}

				@Override
				public ThreadConstraint getThreadConstraint() {
					return ThreadConstraint.ANY; // Can't know
				}

				@Override
				public Subscription subscribe(Observer<? super ObservableValueEvent<T>> observer) {
					class RefreshEachObserver implements Observer<ObservableValueEvent<T>>, Subscription {
						private T thePreviousValue;
						private Subscription theRefreshSub;

						@Override
						public void onNext(ObservableValueEvent<T> value) {
							if (theRefreshSub != null && !Objects.equals(thePreviousValue, value.getNewValue())) {
								theRefreshSub.unsubscribe();
								theRefreshSub = null;
							}
							if (theRefreshSub == null) {
								thePreviousValue = value.getNewValue();
								Observable<?> refresh = theRefresh.apply(value.getNewValue());
								if (refresh != null)
									theRefreshSub = refresh.subscribe(new Observer<Object>() {
										@Override
										public void onNext(Object value2) {
											refresh(value2);
										}

										@Override
										public void onCompleted(Supplier<Causable> cause) {
											refresh(cause);
										}
									});
							}
							if (!value.isInitial())
								observer.onNext(value);
						}

						@Override
						public void onCompleted(Supplier<Causable> cause) {
							unsubscribe();
							observer.onCompleted(cause);
						}

						private void refresh(Object cause) {
							ObservableValueEvent<T> change = createChangeEvent(thePreviousValue, thePreviousValue, cause);
							try (Transaction t = change.use()) {
								observer.onNext(change);
							}
						}

						@Override
						public void unsubscribe() {
							Subscription sub = theRefreshSub;
							theRefreshSub = null;
							if (sub != null)
								sub.unsubscribe();
						}
					}
					RefreshEachObserver refreshObs = new RefreshEachObserver();
					Subscription wrappedSub = wrappedChanges.subscribe(refreshObs);
					return Subscription.forAll(refreshObs, wrappedSub);
				}

				@Override
				public boolean isSafe() {
					return wrappedChanges.isSafe();
				}

				@Override
				public Transaction lock() {
					return RefreshEachValue.this.lock();
				}

				@Override
				public Transaction tryLock() {
					return RefreshEachValue.this.tryLock();
				}

				@Override
				public long getStamp() {
					return RefreshEachValue.this.getStamp();
				}

				@Override
				public CoreChangeSources getChangeSources() {
					T value = getWrapped().get();
					Observable<?> refresh = theRefresh.apply(value);
					if (refresh != null)
						return CoreChangeSources.of(getWrapped().noInitChanges(), refresh);
					else
						return getWrapped().noInitChanges().getChangeSources();
				}
			}
			return new RefreshEachChanges();
		}

		@Override
		public boolean isEventing() {
			return theWrapped.isEventing();
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(getWrapped().getIdentity(), "refreshEach", theRefresh);
		}

		@Override
		public long getStamp() {
			try (Transaction t = lock()) {
				T value = get();
				Observable<?> refresh = theRefresh.apply(value);
				if (refresh == null)
					return theWrapped.getStamp();
				else
					return Stamped.compositeStamp(theWrapped, refresh);
			}
		}
	}

	/**
	 * Implements {@link ObservableValue#safe(ThreadConstraint)}
	 *
	 * @param <T> The type of the value
	 */
	class SafeObservableValue<T> extends WrappingObservableValue<T, T> {
		private final ThreadConstraint theThreading;
		private T theLastEventedValue;
		private final AtomicReference<ObservableValueEvent<T>> theLastEvent;
		private CollectionLockingStrategy theLocking;
		private final ListenerList<Consumer<ObservableValueEvent<T>>> theListeners;
		private volatile boolean isEventing;

		private Subscription theWrappedSubscription;

		public SafeObservableValue(ObservableValue<T> wrapped, ThreadConstraint threading) {
			super(wrapped);
			if (!threading.supportsInvoke())
				throw new IllegalArgumentException("Thread constraints for safe structures must be invokable");
			theThreading = threading;
			theLastEvent = new AtomicReference<>();
			theLocking = ThreadConstrainedLockingStrategy.get(threading);
			theListeners = ListenerList.build()//
				.withInUse(inUse -> {
					if (inUse) {
						theWrappedSubscription = wrapped.changes().act(evt -> {
							if (theThreading.isEventThread()) {
								theLastEvent.set(null);
								fire(evt, true);
							} else {
								theLastEvent.set(evt);
								theThreading.invoke(() -> fire(evt, false));
							}
						});
					} else {
						Subscription wrapSub = theWrappedSubscription;
						theWrappedSubscription = null;
						if (wrapSub != null)
							wrapSub.unsubscribe();
					}
				})//
				.build();
		}

		private void fire(ObservableValueEvent<T> evt, boolean fromEventThread) {
			if (!fromEventThread && !theLastEvent.compareAndSet(evt, null))
				return; // Another event has happened--don't bother with this one

			ObservableValueEvent<T> toFire;
			if (evt.isInitial())
				toFire = null;
			else if (fromEventThread) {
				if (evt.getOldValue() == theLastEventedValue)
					toFire = evt;
				else
					toFire = createChangeEvent(theLastEventedValue, evt.getNewValue(), evt);
			} else
				toFire = createChangeEvent(theLastEventedValue, evt.getNewValue(), Causable.broken(evt));
			theLastEventedValue = evt.getNewValue();
			if (toFire != null) {
				isEventing = true;
				try (Transaction t = toFire == evt ? Transaction.NONE : toFire.use()) {
					theListeners.forEach(//
						listener -> listener.accept(toFire));
				} finally {
					isEventing = false;
				}
			}
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(getWrapped().getIdentity(), "safe", theThreading);
		}

		@Override
		public T get() {
			return theLastEventedValue;
		}

		@Override
		public Observable<ObservableValueEvent<T>> noInitChanges() {
			class SafeChanges extends AbstractIdentifiable implements Observable<ObservableValueEvent<T>> {
				@Override
				protected Object createIdentity() {
					return Identifiable.wrap(SafeObservableValue.this, "noInitChanges");
				}

				@Override
				public boolean isEventing() {
					return theListeners.isFiring();
				}

				@Override
				public ThreadConstraint getThreadConstraint() {
					return theThreading;
				}

				@Override
				public CoreId getCoreId() {
					return theLocking.getCoreId();
				}

				@Override
				public boolean isSafe() {
					return true;
				}

				@Override
				public Transaction lock() {
					return theLocking.lock(false, null);
				}

				@Override
				public Transaction tryLock() {
					return theLocking.tryLock(false, null);
				}

				@Override
				public long getStamp() {
					return SafeObservableValue.this.getStamp();
				}

				@Override
				public Subscription subscribe(Observer<? super ObservableValueEvent<T>> observer) {
					Runnable remove = theListeners.add(observer::onNext, true);
					return remove::run;
				}

				@Override
				public CoreChangeSources getChangeSources() {
					return getWrapped().noInitChanges().getChangeSources();
				}
			}
			return new SafeChanges();
		}

		@Override
		public boolean isEventing() {
			return isEventing || theWrapped.isEventing();
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return theThreading;
		}
	}

	/**
	 * An observable value whose value cannot change
	 *
	 * @param <T> The type of this value
	 */
	class ConstantObservableValue<T> extends AbstractIdentifiable implements ObservableValue<T> {
		private final T theValue;

		/** @param value This observable value's value */
		public ConstantObservableValue(T value) {
			theValue = value;
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.idFor(theValue, () -> String.valueOf(theValue), () -> Objects.hashCode(theValue),
				other -> Objects.equals(theValue, other));
		}

		@Override
		public ConstantObservableValue<T> alias(String alias) {
			super.alias(alias);
			return this;
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
			class ConstantNoInitChanges extends AbstractIdentifiable implements Observable<ObservableValueEvent<T>> {
				@Override
				protected Object createIdentity() {
					return Identifiable.wrap(ConstantObservableValue.this.getIdentity(), "noInitChanges");
				}

				@Override
				public CoreId getCoreId() {
					return CoreId.EMPTY;
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
				public Subscription subscribe(Observer<? super ObservableValueEvent<T>> observer) {
					try (Observer.CompletedCause completion = Observer.completion(() -> createInitialEvent(theValue, theValue))) {
						observer.onCompleted(completion);
					}
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
				public long getStamp() {
					return 0;
				}

				@Override
				public CoreChangeSources getChangeSources() {
					return CoreChangeSources.empty();
				}

				@Override
				public Subscription act(SimpleObserver<? super ObservableValueEvent<T>> action) {
					return Subscription.NONE;
				}

				@Override
				public Subscription act0(NoArgObserver action) {
					return Subscription.NONE;
				}

				@Override
				public <R> Observable<R> filterMap(Function<? super ObservableValueEvent<T>, R> func) {
					return (Observable<R>) this;
				}

				@Override
				public Observable<ObservableValueEvent<T>> takeUntil(Observable<?> until) {
					return this;
				}

				@Override
				public Observable<ObservableValueEvent<T>> take(int times) {
					return this;
				}

				@Override
				public Observable<ObservableValueEvent<T>> skip(int times) {
					return this;
				}

				@Override
				public Observable<ObservableValueEvent<T>> skip(Supplier<Integer> times) {
					return this;
				}

				@Override
				public Observable<ObservableValueEvent<T>> safe(ThreadConstraint threading) {
					return this;
				}
			}
			return new ConstantNoInitChanges();
		}

		@Override
		public Observable<ObservableValueEvent<T>> changes() {
			/* I have an application that calls the subscribe method on this observable often.
			 * Previously this was creating a default initial event for each invocation, and even though that type is not heavy,
			 * the object allocation was causing a lot of overhead.
			 *
			 * The logic with this implementation is that most observers:
			 * a) Don't use the onFinish() call, at least for initial events.
			 * b) Don't care about the onCompleted() call at all.
			 * This implementation is much faster for that case.  For the case that effects are used on the initial event,
			 * this implementation is safe, though less performant than the previous and obvious implementation.
			 */
			class ContantInitialEvent implements ObservableValueEvent<T> {
				private ThreadLocal<Map<CausableKey, Causable.Effect>> theEffects;

				@Override
				public BetterList<Object> getCauses() {
					return BetterList.empty();
				}

				@Override
				public Causable getRootCausable() {
					return this;
				}

				@Override
				public Effect onFinish(CausableKey key) {
					ThreadLocal<Map<CausableKey, Causable.Effect>> effects = theEffects;
					if (effects == null) {
						synchronized (this) {
							effects = theEffects;
							if (effects == null)
								theEffects = effects = new ThreadLocal<>();
						}
					}
					Map<CausableKey, Causable.Effect> localEffects = effects.get();
					if (localEffects == null) {
						localEffects = new LinkedHashMap<>();
						effects.set(localEffects);
					}
					return localEffects.computeIfAbsent(key, Effect::new);
				}

				@Override
				public boolean isFinished() {
					return false;
				}

				@Override
				public boolean isTerminated() {
					return false;
				}

				@Override
				public Transaction use() {
					return Transaction.NONE;
				}

				@Override
				public boolean isInitial() {
					return true;
				}

				@Override
				public T getOldValue() {
					return get();
				}

				@Override
				public T getNewValue() {
					return get();
				}

				void finish() {
					ThreadLocal<Map<CausableKey, Causable.Effect>> effects = theEffects;
					Map<CausableKey, Causable.Effect> localEffects = effects == null ? null : effects.get();
					if (localEffects != null)
						Causable.terminate(localEffects.values(), this);
				}
			}
			ContantInitialEvent initialEvent = new ContantInitialEvent();
			class ConstantInitChanges extends AbstractIdentifiable implements Observable<ObservableValueEvent<T>> {
				@Override
				protected Object createIdentity() {
					return Identifiable.wrap(ConstantObservableValue.this.getIdentity(), "changes");
				}

				@Override
				public CoreId getCoreId() {
					return CoreId.EMPTY;
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
				public Subscription subscribe(Observer<? super ObservableValueEvent<T>> observer) {
					try {
						observer.onNext(initialEvent);
						observer.onCompleted(() -> initialEvent);
					} finally {
						initialEvent.finish();
					}
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
				public long getStamp() {
					return 0;
				}

				@Override
				public CoreChangeSources getChangeSources() {
					return CoreChangeSources.empty();
				}
			}
			return new ConstantInitChanges();
		}

		@Override
		public T get() {
			return theValue;
		}

		@Override
		public boolean isEventing() {
			return false;
		}

		@Override
		public ObservableValue<T> cached() {
			return this;
		}

		@Override
		public String toString() {
			return "" + theValue;
		}
	}

	/**
	 * Implements {@link ObservableValue#of(Supplier, LongSupplier, Observable, Supplier)}
	 *
	 * @param <T> The type of this value
	 */
	class SyntheticObservable<T> extends AbstractIdentifiable implements ObservableValue<T> {
		private final Supplier<? extends T> theValue;
		private final LongSupplier theStamp;
		private final Observable<?> theChanges;
		private final Supplier<?> theIdentity;

		public SyntheticObservable(Supplier<? extends T> value, LongSupplier stamp, Observable<?> changes, Supplier<?> identity) {
			theValue = value;
			theStamp = stamp;
			theChanges = changes;
			theIdentity = identity;
		}

		@Override
		public long getStamp() {
			return theStamp.getAsLong();
		}

		@Override
		protected Object createIdentity() {
			return theIdentity.get();
		}

		@Override
		public SyntheticObservable<T> alias(String alias) {
			super.alias(alias);
			return this;
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

		@Override
		public boolean isEventing() {
			return theChanges.isEventing();
		}

		Observable<ObservableValueEvent<T>> changes(boolean withInit) {
			class SyntheticObservableChanges extends AbstractIdentifiable implements Observable<ObservableValueEvent<T>> {
				@Override
				protected Object createIdentity() {
					if (withInit)
						return Identifiable.wrap(SyntheticObservable.this.getIdentity(), "changes");
					else
						return Identifiable.wrap(SyntheticObservable.this.getIdentity(), "noInitChanges");
				}

				@Override
				public Subscription subscribe(Observer<? super ObservableValueEvent<T>> observer) {
					class SyntheticChanges implements Observer<Object> {
						T theCurrentValue = get();
						boolean isInitialized;

						void initialize() {
							isInitialized = true;
							theCurrentValue = get();
							if (withInit) {
								ObservableValueEvent<T> evt = createInitialEvent(theCurrentValue, null);
								try (Transaction t = evt.use()) {
									observer.onNext(evt);
								}
							}
						}

						@Override
						public void onNext(Object value) {
							boolean init = !isInitialized;
							T newValue = theValue.get();
							T oldValue = theCurrentValue;
							theCurrentValue = newValue;
							ObservableValueEvent<T> evt;
							if (init) {
								isInitialized = true;
								if (!withInit)
									init = false;
							}
							if (init)
								evt = createInitialEvent(newValue, value);
							else
								evt = createChangeEvent(oldValue, newValue, value);
							try (Transaction t = evt.use()) {
								observer.onNext(evt);
							}
						}

						@Override
						public void onCompleted(Supplier<Causable> cause) {
							observer.onCompleted(cause);
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
				public boolean isEventing() {
					return theChanges.isEventing();
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
				public long getStamp() {
					return SyntheticObservable.this.getStamp();
				}

				@Override
				public Observable<ObservableValueEvent<T>> noInit() {
					if (withInit)
						return noInitChanges();
					else
						return this;
				}

				@Override
				public CoreChangeSources getChangeSources() {
					return theChanges.getChangeSources();
				}
			}
			return new SyntheticObservableChanges();
		}

		@Override
		public ObservableValue<T> cached() {
			return new CachedSyntheticObservableValue<>(this);
		}

		static class CachedSyntheticObservableValue<T> extends AbstractIdentifiable implements ObservableValue<T> {
			private final SyntheticObservable<T> theValue;
			private final ListenerList<Observer<? super ObservableValueEvent<T>>> theListeners;
			private volatile T theCachedValue;
			private volatile long theCachedStamp;

			public CachedSyntheticObservableValue(SyntheticObservable<T> value) {
				theValue = value;
				theListeners = ListenerList.build()//
					.withInUse(new ListenerList.InUseListener() {
						private Subscription theChangesSub;

						@Override
						public void inUseChanged(boolean inUse) {
							if (!inUse) {
								theChangesSub.unsubscribe();
								theChangesSub = null;
								return;
							}
							get(); // Update for initial value
							theChangesSub = theValue.theChanges.act(cause -> {
								ObservableValueEvent<T> evt = createChangeEvent(theCachedValue, get(), cause);
								try (Transaction t = evt.use()) {
									theListeners.forEach(//
										l -> l.onNext(evt));
								}
							});
						}
					}).build();
				theCachedStamp = -1;
			}

			@Override
			protected Object createIdentity() {
				return theValue.getIdentity();
			}

			@Override
			public CachedSyntheticObservableValue<T> alias(String alias) {
				super.alias(alias);
				return this;
			}

			@Override
			public long getStamp() {
				return theValue.getStamp();
			}

			@Override
			public T get() {
				long newStamp = theValue.getStamp();
				if (theCachedStamp == -1 || theCachedStamp != newStamp) {
					theCachedValue = theValue.get();
					theCachedStamp = newStamp;
				}
				return theCachedValue;
			}

			@Override
			public Observable<ObservableValueEvent<T>> noInitChanges() {
				class CachedSyntheticChanges extends AbstractIdentifiable implements Observable<ObservableValueEvent<T>> {
					@Override
					public boolean isEventing() {
						return theListeners.isFiring();
					}

					@Override
					protected Object createIdentity() {
						return Identifiable.wrap(CachedSyntheticObservableValue.this.getIdentity(), "noInitChanges");
					}

					@Override
					public CoreId getCoreId() {
						return theValue.getCoreId();
					}

					@Override
					public ThreadConstraint getThreadConstraint() {
						return theValue.getThreadConstraint();
					}

					@Override
					public long getStamp() {
						return CachedSyntheticObservableValue.this.getStamp();
					}

					@Override
					public Subscription subscribe(Observer<? super ObservableValueEvent<T>> observer) {
						return theListeners.add(observer, true);
					}

					@Override
					public boolean isSafe() {
						return theValue.theChanges.isSafe();
					}

					@Override
					public Transaction lock() {
						return theValue.lock();
					}

					@Override
					public Transaction tryLock() {
						return theValue.tryLock();
					}

					@Override
					public CoreChangeSources getChangeSources() {
						return theValue.theChanges.getChangeSources();
					}
				}
				return new CachedSyntheticChanges();
			}

			@Override
			public boolean isEventing() {
				return theValue.isEventing();
			}

			@Override
			public int hashCode() {
				return theValue.hashCode();
			}

			@Override
			public boolean equals(Object obj) {
				if (obj == this)
					return true;
				else if (obj instanceof CachedObservableValue)
					return theValue.equals(((CachedObservableValue<?>) obj).theValue);
				else
					return theValue.equals(obj);
			}

			@Override
			public String toString() {
				return theValue.toString();
			}
		}
	}

	/**
	 * Implements {@link ObservableValue#flatten(ObservableValue)}
	 *
	 * @param <T> The type of the value
	 */
	class FlattenedObservableValue<T> extends AbstractIdentifiable implements ObservableValue<T> {
		private final ObservableValue<? extends ObservableValue<? extends T>> theValue;
		private final Supplier<? extends T> theDefaultValue;

		protected FlattenedObservableValue(ObservableValue<? extends ObservableValue<? extends T>> value,
			Supplier<? extends T> defaultValue) {
			if (value == null)
				throw new NullPointerException("Null observable");
			theValue = value;
			theDefaultValue = defaultValue;
		}

		protected ObservableValue<? extends ObservableValue<? extends T>> getWrapped() {
			return theValue;
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(theValue.getIdentity(), "flat");
		}

		@Override
		public FlattenedObservableValue<T> alias(String alias) {
			super.alias(alias);
			return this;
		}

		@Override
		public long getStamp() {
			long stamp = theValue.getStamp();
			ObservableValue<? extends T> wrapped = theValue.get();
			if (wrapped != null)
				stamp = Stamped.compositeOf2Stamps(stamp, wrapped.getStamp());
			return stamp;
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
			if (value != null)
				return value.get();
			else if (theDefaultValue != null)
				return theDefaultValue.get();
			else
				return null;
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
		public boolean isEventing() {
			if (theValue.isEventing())
				return true;
			ObservableValue<? extends T> value = theValue.get();
			return value != null && value.isEventing();
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
		public String toString() {
			return "flat(" + theValue + ")";
		}

		private class FlattenedValueChanges extends AbstractIdentifiable implements Observable<ObservableValueEvent<T>>, CausableChanging {
			private final boolean withInitialEvent;

			public FlattenedValueChanges(boolean withInitialEvent) {
				this.withInitialEvent = withInitialEvent;
			}

			@Override
			protected Object createIdentity() {
				if (withInitialEvent)
					return Identifiable.wrap(FlattenedObservableValue.this.getIdentity(), "changes");
				else
					return Identifiable.wrap(FlattenedObservableValue.this.getIdentity(), "noInitChanges");
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
						private ObservableValue<? extends T> theInnerObservable;

						@Override
						public void onNext(ObservableValueEvent<? extends ObservableValue<? extends T>> event) {
							firedInit[0] = true;
							theLock.lock();
							try {
								final ObservableValue<? extends T> innerObs = event.getNewValue();
								// Shouldn't have 2 inner observables potentially generating events at the same time
								boolean differentObservables = !Objects.equals(innerObs, theInnerObservable);
								if (differentObservables) {
									theInnerObservable = innerObs;
									Subscription.unsubscribe(innerSub.getAndSet(null));
								}
								if (innerObs != null && differentObservables) {
									boolean[] firedInit2 = new boolean[1];
									innerSub.getAndSet(innerObs.changes().subscribe(new Observer<ObservableValueEvent<? extends T>>() {
										@Override
										public void onNext(ObservableValueEvent<? extends T> event2) {
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
													toFire = retObs.createChangeEvent(innerOld, event2.getNewValue(), event2.getCauses());
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
										public void onCompleted(Supplier<Causable> cause) {
										}
									}));
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
						public void onCompleted(Supplier<Causable> cause) {
							firedInit[0] = true;
							// The outer *changes* observable is complete, meaning this value can now never change
							// It does NOT mean that we should stop listening to the inner observable
							// Subscription.unsubscribe(innerSub.getAndSet(null));
							// theLock.lock();
							// try {
							// observer.onCompleted(cause);
							// } finally {
							// theLock.unlock();
							// }
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
				return FlattenedObservableValue.this.getThreadConstraint();
			}

			@Override
			public boolean isEventing() {
				if (theValue.isEventing())
					return true;
				ObservableValue<?> content = theValue.get();
				return content != null && content.isEventing();
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
					return Lockable.getCoreId(theValue, theValue::get);
				}
			}

			@Override
			public long getStamp() {
				return FlattenedObservableValue.this.getStamp();
			}

			@Override
			public CoreChangeSources getChangeSources() {
				ObservableValue<?> content = theValue.get();
				if (content != null)
					return Observable.CoreChangeSources.of(theValue.noInitChanges(), content.noInitChanges());
				else
					return theValue.noInitChanges().getChangeSources();
			}

			@Override
			public Observable<? extends Causable> simpleChanges() {
				return this;
			}
		}
	}

	/**
	 * Implements {@link ObservableValue#firstValue(Predicate, Supplier, ObservableValue...)}
	 *
	 * @param <T> The type of the value
	 */
	class FirstObservableValue<T> extends AbstractIdentifiable implements ObservableValue<T> {
		private final ObservableValue<? extends T>[] theValues;
		private final Predicate<? super T> theTest;
		private final Supplier<? extends T> theDefault;

		protected FirstObservableValue(ObservableValue<? extends T>[] values, Predicate<? super T> test, Supplier<? extends T> def) {
			for (int i = 0; i < values.length; i++) {
				if (values[i] == null)
					throw new IllegalArgumentException("Null value at " + i);
			}
			theValues = values;
			theTest = test;
			theDefault = def;
		}

		/** @return The component values of this value */
		protected List<? extends ObservableValue<? extends T>> getValues() {
			return Arrays.asList(theValues);
		}

		protected Predicate<? super T> getTest() {
			return theTest;
		}

		protected Supplier<? extends T> getDefault() {
			return theDefault;
		}

		@Override
		protected Object createIdentity() {
			Identifiable.CustomIdentityBuilder builder = Identifiable.buildId();
			builder.append("first");
			if (theTest != null)
				builder.append(":").withPrintedId(theTest);
			builder.append("(");
			for (int i = 0; i < theValues.length; i++) {
				if (i > 0)
					builder.append(", ");
				builder.withPrintedId(theValues[i].getIdentity());
			}
			builder.append(")");
			if (theDefault != null)
				builder.append(":").withPrintedId(theDefault);
			return builder.build();
		}

		@Override
		public FirstObservableValue<T> alias(String alias) {
			super.alias(alias);
			return this;
		}

		@Override
		public long getStamp() {
			return Stamped.compositeStamp(Arrays.asList(theValues));
		}

		@Override
		public T get() {
			for (ObservableValue<? extends T> v : theValues) {
				T value = v.get();
				if (test(value))
					return value;
			}
			if (theDefault != null)
				return theDefault.get();
			return null;
		}

		private boolean test(T value) {
			if (theTest != null)
				return theTest.test(value);
			else
				return value != null;
		}

		@Override
		public Observable<ObservableValueEvent<T>> changes() {
			return new FirstValueChanges();
		}

		@Override
		public Observable<ObservableValueEvent<T>> noInitChanges() {
			return changes().noInit();
		}

		@Override
		public boolean isEventing() {
			for (ObservableValue<? extends T> value : theValues) {
				if (value.isEventing())
					return true;
				else if (test(value.get()))
					return false;
			}
			return false;
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return ThreadConstrained.getThreadConstraint(null, Arrays.asList(theValues), FunctionUtils.identity());
		}

		class FirstValueChanges extends AbstractIdentifiable implements Observable<ObservableValueEvent<T>> {
			@Override
			protected Object createIdentity() {
				return Identifiable.wrap(FirstObservableValue.this.getIdentity(), "changes");
			}

			@Override
			public Subscription subscribe(Observer<? super ObservableValueEvent<T>> observer) {
				if (theValues.length == 0) {
					T defaultV=theDefault==null ? null : theDefault.get();
					ObservableValueEvent<T> evt = createInitialEvent(defaultV, null);
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
					public void onNext(ObservableValueEvent<? extends T> event) {
						lock.lock();
						try {
							if (valueSubs[index] == null && !event.isInitial()) {
								// This may happen if two values fire events on different threads
								// We don't care about this, and we've unsubscribed to the value
								return;
							}
							boolean found;
							try {
								if (theTest != null)
									found = theTest.test(event.getNewValue());
								else
									found = event.getNewValue() != null;
							} catch (RuntimeException e) {
								e.printStackTrace();
								found = false;
							}
							int nextIndex = index + 1;
							ObservableValueEvent<T> toFire;
							if (!found) {
								if (!isFound && !event.isInitial())
									toFire = null;
								else if (nextIndex < theValues.length) {
									toFire = null;
									valueSubs[nextIndex] = theValues[nextIndex].changes().subscribe(//
										new ElementFirstObserver(nextIndex));
								} else {
									T def;
									try {
										def = theDefault == null ? null : theDefault.get();
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
									observer.onNext(toFire);
								}
							}
						} finally {
							lock.unlock();
						}
					}

					@Override
					public void onCompleted(Supplier<Causable> cause) {
						finished[index] = true;
						valueSubs[index] = null;
						if (allCompleted())
							observer.onCompleted(cause);
					}

					private boolean allCompleted() {
						for (boolean f : finished)
							if (!f)
								return false;
						return true;
					}
				}
				valueSubs[0] = theValues[0].changes().subscribe(//
					new ElementFirstObserver(0));
				return () -> {
					Subscription.forAll(valueSubs).unsubscribe();
				};
			}

			@Override
			public ThreadConstraint getThreadConstraint() {
				return ThreadConstrained.getThreadConstraint(null, Arrays.asList(theValues), FunctionUtils.identity());
			}

			@Override
			public boolean isEventing() {
				for (ObservableValue<? extends T> value : theValues)
					if (value != null && value.isEventing())
						return true;
				return false;
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

			@Override
			public long getStamp() {
				return FirstObservableValue.this.getStamp();
			}

			@Override
			public CoreChangeSources getChangeSources() {
				Observable<?>[] applicable = new Observable[theValues.length];
				int i = 0;
				for (ObservableValue<? extends T> value : theValues) {
					applicable[i++] = value.noInitChanges();
					T v = value.get();
					if (test(v))
						break;
				}
				return CoreChangeSources.of(applicable);
			}
		}

		@Override
		public String toString() {
			return getIdentity().toString();
		}
	}

	/**
	 * An observable composed of an ObservableValue containing an observable. This observable will fire when the content of the observable
	 * value fires.
	 *
	 * @param <T> The type of the observable
	 */
	class FlattenedValueObservable<T> extends Observable.WrappingObservable<ObservableValueEvent<? extends Observable<? extends T>>, T> {
		private final ObservableValue<? extends Observable<? extends T>> theValue;

		protected FlattenedValueObservable(ObservableValue<? extends Observable<? extends T>> value) {
			super((Observable<ObservableValueEvent<? extends Observable<? extends T>>>) (Observable<?>) value.changes());
			theValue = value;
		}

		@Override
		public Subscription subscribe(Observer<? super T> observer) {
			return getWrapped().subscribe(new Observer<ObservableValueEvent<? extends Observable<? extends T>>>() {
				@Override
				public void onNext(ObservableValueEvent<? extends Observable<? extends T>> event) {
					if (event.getNewValue() != null) {
						event.getNewValue().takeUntil(theValue.noInitChanges()).subscribe(new Observer<T>() {
							@Override
							public void onNext(T event2) {
								observer.onNext(event2);
							}

							@Override
							public void onCompleted(Supplier<Causable> cause) {
								// Don't use the completed events because the contents of this observable may be replaced
							}
						});
					}
				}

				@Override
				public void onCompleted(Supplier<Causable> cause) {
					observer.onCompleted(cause);
				}
			});
		}

		@Override
		public boolean isSafe() {
			return false; // Can't guarantee that the contents will always be safe
		}

		@Override
		public boolean isLockSupported() {
			return theValue.changes().isLockSupported();
		}

		@Override
		public Transaction lock() {
			return Lockable.lock(theValue.changes(), theValue::get);
		}

		@Override
		public Transaction tryLock() {
			return Lockable.tryLock(theValue.changes(), theValue::get);
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(theValue.getIdentity(), "flatten");
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			if (theValue.getThreadConstraint() == ThreadConstraint.NONE)
				return theValue.get().getThreadConstraint();
			else
				return ThreadConstraint.ANY; // Can't know
		}

		@Override
		public long getStamp() {
			Observable<?> value = theValue.get();
			if (value == null)
				return super.getStamp();
			else
				return Stamped.compositeOf2Stamps(super.getStamp(), value.getStamp());
		}
	}

	/**
	 * A partial {@link ObservableValue} implementation that makes it as easy as possible to implement one from scratch
	 *
	 * @param <T> The type of the value
	 */
	abstract class LazyObservableValue<T> extends AbstractIdentifiable implements ObservableValue<T> {
		private final Transactable theLock;
		private final ListenerList<Observer<? super ObservableValueEvent<T>>> theListeners;
		volatile boolean isValueUpToDate;
		volatile T theLastRememberedValue;
		volatile long theStamp;

		protected LazyObservableValue(Transactable lock) {
			theLock = lock;
			theStamp = -1;
			theListeners = ListenerList.build().withInUse(new ListenerList.InUseListener() {
				private Subscription theSub;

				@Override
				public void inUseChanged(boolean inUse) {
					if (inUse) {
						theSub = subscribe((value, cause) -> {
							T oldValue = theLastRememberedValue;
							theLastRememberedValue = value;
							theStamp++;
							isValueUpToDate = true;
							ObservableValueEvent<T> event = new ObservableValueEvent.DefaultObservableValueEvent<>(false, oldValue, value,
								cause);
							try (Transaction t = event.use()) {
								fire(event);
							}
						});
					} else {
						isValueUpToDate = false;
						theSub.unsubscribe();
						theSub = null;
					}
				}
			}).build();
		}

		@Override
		public LazyObservableValue<T> alias(String alias) {
			super.alias(alias);
			return this;
		}

		@Override
		public long getStamp() {
			if (isValueUpToDate)
				return theStamp;
			T value = getSpontaneous();
			if (theStamp == -1 || value != theLastRememberedValue) {
				theLastRememberedValue = value;
				theStamp++;
			}
			return theStamp;
		}

		@Override
		public T get() {
			if (isValueUpToDate)
				return theLastRememberedValue;
			T value = getSpontaneous();
			if (value != theLastRememberedValue) {
				theLastRememberedValue = value;
				theStamp++;
			}
			return value;
		}

		@Override
		public Observable<ObservableValueEvent<T>> noInitChanges() {
			class LOVChanges extends AbstractIdentifiable implements Observable<ObservableValueEvent<T>> {
				@Override
				protected Object createIdentity() {
					return Identifiable.wrap(LazyObservableValue.this.getIdentity(), "noInitChanges");
				}

				@Override
				public CoreId getCoreId() {
					return theLock.getCoreId();
				}

				@Override
				public ThreadConstraint getThreadConstraint() {
					return theLock.getThreadConstraint();
				}

				@Override
				public boolean isEventing() {
					return theListeners.isFiring();
				}

				@Override
				public boolean isSafe() {
					return theLock.isLockSupported();
				}

				@Override
				public Transaction lock() {
					return theLock.lock(false, null);
				}

				@Override
				public Transaction tryLock() {
					return theLock.tryLock(false, null);
				}

				@Override
				public Subscription subscribe(Observer<? super ObservableValueEvent<T>> observer) {
					Runnable remove = theListeners.add(observer, true);
					return remove::run;
				}

				@Override
				public long getStamp() {
					return LazyObservableValue.this.getStamp();
				}

				@Override
				public CoreChangeSources getChangeSources() {
					return LazyObservableValue.this.getChangeSources();
				}
			}
			return new LOVChanges();
		}

		void fire(ObservableValueEvent<T> event) {
			theListeners.forEach(//
				l -> l.onNext(event));
		}

		/**
		 * Grabs the value for this observable when it is not being listened to
		 *
		 * @return The current value for this observable
		 */
		protected abstract T getSpontaneous();

		/**
		 * Installs a listener for the value when this value is being listened to
		 *
		 * @param listener The listener to notify when the value of the observable changes
		 * @return A subscription to remove the listener
		 */
		protected abstract Subscription subscribe(BiConsumer<T, Object> listener);
	}

	/**
	 * A wrapping observable value that caches the wrapped value
	 *
	 * @param <T> The type of the value
	 */
	static class CachedObservableValue<T> extends AbstractIdentifiable implements ObservableValue<T> {
		private final ObservableValue<T> theValue;
		private final ListenerList<Observer<? super ObservableValueEvent<T>>> theListeners;
		private volatile T theCachedValue;
		private volatile long theCachedStamp;

		public CachedObservableValue(ObservableValue<T> value) {
			theValue = value;
			theListeners = ListenerList.build()//
				.withInUse(new ListenerList.InUseListener() {
					private Subscription theChangesSub;

					@Override
					public void inUseChanged(boolean inUse) {
						if (!inUse) {
							theChangesSub.unsubscribe();
							theChangesSub = null;
							return;
						}
						try (Transaction t = theValue.lock()) {
							get(); // Update for initial value
							theChangesSub = theValue.noInitChanges().act(evt -> theListeners.forEach(//
								l -> l.onNext(evt)));
						}
					}
				}).build();
			theCachedStamp = -1;
		}

		@Override
		protected Object createIdentity() {
			return theValue.getIdentity();
		}

		@Override
		public CachedObservableValue<T> alias(String alias) {
			super.alias(alias);
			return this;
		}

		@Override
		public long getStamp() {
			return theValue.getStamp();
		}

		@Override
		public T get() {
			long newStamp = theValue.getStamp();
			if (theCachedStamp == -1 || theCachedStamp != newStamp) {
				theCachedValue = theValue.get();
				theCachedStamp = newStamp;
			}
			return theCachedValue;
		}

		@Override
		public Observable<ObservableValueEvent<T>> noInitChanges() {
			class CachedSyntheticChanges extends AbstractIdentifiable implements Observable<ObservableValueEvent<T>> {
				@Override
				public boolean isEventing() {
					return theListeners.isFiring();
				}

				@Override
				protected Object createIdentity() {
					return Identifiable.wrap(CachedObservableValue.this.getIdentity(), "noInitChanges");
				}

				@Override
				public CoreId getCoreId() {
					return theValue.getCoreId();
				}

				@Override
				public ThreadConstraint getThreadConstraint() {
					return theValue.getThreadConstraint();
				}

				@Override
				public long getStamp() {
					return CachedObservableValue.this.getStamp();
				}

				@Override
				public Subscription subscribe(Observer<? super ObservableValueEvent<T>> observer) {
					return theListeners.add(observer, true);
				}

				@Override
				public boolean isSafe() {
					return theValue.noInitChanges().isSafe();
				}

				@Override
				public Transaction lock() {
					return theValue.lock();
				}

				@Override
				public Transaction tryLock() {
					return theValue.tryLock();
				}

				@Override
				public CoreChangeSources getChangeSources() {
					return theValue.noInitChanges().getChangeSources();
				}
			}
			return new CachedSyntheticChanges();
		}

		@Override
		public boolean isEventing() {
			return theValue.isEventing();
		}

		@Override
		public ObservableValue<T> cached() {
			return this;
		}

		@Override
		public int hashCode() {
			return theValue.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else if (obj instanceof CachedObservableValue)
				return theValue.equals(((CachedObservableValue<?>) obj).theValue);
			else
				return theValue.equals(obj);
		}

		@Override
		public String toString() {
			return theValue.toString();
		}
	}
}
