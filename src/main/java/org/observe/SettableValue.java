package org.observe;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.observe.Transformation.ReverseQueryResult;
import org.observe.Transformation.TransformationState;
import org.observe.Transformation.TransformedElement;
import org.observe.collect.ObservableCollection;
import org.qommons.BiTuple;
import org.qommons.CausalLock;
import org.qommons.Identifiable;
import org.qommons.QommonsUtils;
import org.qommons.Subscription;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.collect.CollectionUtils;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.fn.FunctionUtils;
import org.qommons.fn.TriFunction;

/**
 * An observable value for which a value can be assigned directly
 *
 * @param <T> The type of the value
 */
public interface SettableValue<T> extends ObservableValue<T>, CausalLock {
	/** A string-typed observable that always returns null */
	ObservableValue<String> ALWAYS_ENABLED = ObservableValue.of(null);
	/** A string-typed observable that always returns {@link org.qommons.collect.MutableCollectionElement.StdMsg#UNSUPPORTED_OPERATION} */
	ObservableValue<String> ALWAYS_DISABLED = ObservableValue.of(StdMsg.UNSUPPORTED_OPERATION);

	public interface Setter<T> extends Getter<T> {
		String isEnabled();

		String isAcceptable(T value);

		T set(T value);

		@Override
		default Setter<T> combine(Transaction... t) {
			return new CombinedSetter<>(this, t);
		}

		static class CombinedSetter<T> implements Setter<T> {
			private final Setter<T> theSource;
			private final Transaction theTransaction;

			public CombinedSetter(Setter<T> source, Transaction... transactions) {
				theSource = source;
				theTransaction = Transaction.and(transactions);
			}

			@Override
			public T get() {
				return theSource.get();
			}

			@Override
			public String isEnabled() {
				return theSource.isEnabled();
			}

			@Override
			public String isAcceptable(T value) {
				return theSource.isAcceptable(value);
			}

			@Override
			public T set(T value) {
				return theSource.set(value);
			}

			@Override
			public void close() {
				theTransaction.close();
				theSource.close();
			}
		}

		static class Unsettable<T> extends Getter.ConstantGetter<T> implements Setter<T> {
			private final Supplier<String> theMessage;

			public Unsettable(Supplier<? extends T> value, Transaction transaction, String message) {
				this(value, transaction, FunctionUtils.constantSupplier(message));
			}

			public Unsettable(Supplier<? extends T> value, Transaction transaction, Supplier<String> message) {
				super(value, transaction);
				theMessage = message;
			}

			@Override
			public String isEnabled() {
				return theMessage.get();
			}

			@Override
			public String isAcceptable(T value) {
				return theMessage.get();
			}

			@Override
			public T set(T value) {
				throw new UnsupportedOperationException(theMessage.get());
			}
		}
	}

	/**
	 * @param value The value to assign to this value
	 * @return The value that was previously set for in this container
	 * @throws IllegalArgumentException If the value is not acceptable or setting it fails
	 * @throws UnsupportedOperationException If this operation is not supported (e.g. because this value is {@link #isEnabled() disabled}
	 */
	T set(T value) throws IllegalArgumentException, UnsupportedOperationException;

	/**
	 * @param value The value to assign to this value
	 * @param cause Something that may have caused this change
	 * @return The value that was previously set for in this container
	 * @throws IllegalArgumentException If the value is not acceptable or setting it fails
	 * @throws UnsupportedOperationException If this operation is not supported (e.g. because this value is {@link #isEnabled() disabled}
	 */
	default T set(T value, Object cause) throws IllegalArgumentException, UnsupportedOperationException {
		if (cause == null)
			return set(value);
		try (Transaction t = lockWrite(false, cause)) {
			return set(value);
		}
	}

	@Override
	Setter<T> lockWrite(boolean tryOnly, Object cause);

	/**
	 * @param value The value to assign to this value
	 * @param cause Something that may have caused this change
	 * @return The value that was previously set for in this container
	 * @throws IllegalArgumentException If the value is not acceptable or setting it fails
	 * @throws UnsupportedOperationException If this operation is not supported (e.g. because this value is {@link #isEnabled() disabled}
	 */
	default SettableValue<T> withValue(T value, Object cause) throws IllegalArgumentException, UnsupportedOperationException {
		set(value, cause);
		return this;
	}

	/**
	 * @param value The value to check
	 * @return null if the value is not known to be unacceptable for this value, or an error text if it is known to be unacceptable. A null
	 *         value returned from this method does not guarantee that a call to {@link #set(Object, Object)} for the same value will not
	 *         throw an IllegalArgumentException
	 */
	String isAcceptable(T value);

	/** @return An observable whose value reports null if this value can be set directly, or a string describing why it cannot */
	ObservableValue<String> isEnabled();

	@Override
	SettableValue<T> alias(String alias);

	@Override
	default CoreId getCoreId() {
		return ObservableValue.super.getCoreId();
	}

	@Override
	default boolean isEventing() {
		return !getCurrentCauses().isEmpty();
	}

	/**
	 * @param <V> The type of value to assign to
	 * @param value The value to assign this settable to
	 * @return An action whose {@link ObservableAction#isEnabled() enabled} property is tied to this settable's {@link #isEnabled() enabled}
	 *         property and the current value's {@link #isAcceptable(Object) acceptability} for this settable.
	 */
	default <V extends T> ObservableAction assignmentTo(ObservableValue<V> value) {
		return assignmentTo(value, null);
	}

	/**
	 * @param <V> The type of value to assign to
	 * @param value The value to assign this settable to
	 * @param onError The error handler for when the assignment fails
	 * @return An action whose {@link ObservableAction#isEnabled() enabled} property is tied to this settable's {@link #isEnabled() enabled}
	 *         property and the current value's {@link #isAcceptable(Object) acceptability} for this settable.
	 */
	default <V extends T> ObservableAction assignmentTo(ObservableValue<V> value, Consumer<IllegalArgumentException> onError) {
		return new ObservableAction() {
			@Override
			public void act(Object cause) throws IllegalStateException {
				try {
					V newValue = value.get();
					set(newValue, cause);
				} catch (IllegalArgumentException e) {
					if (onError != null)
						onError.accept(e);
					throw e;
				}
			}

			@Override
			public boolean isEventing() {
				return SettableValue.this.isEventing();
			}

			@Override
			public ObservableValue<String> isEnabled() {
				return ObservableValue.firstValue(FunctionUtils.NON_NULL, null, //
					SettableValue.this.isEnabled(), //
					value.refresh(noInitChanges())
					.map(FunctionUtils.printableFn(v -> isAcceptable(v), () -> "acceptableTo(" + this + ")", null))//
					);
			}

			@Override
			public String toString() {
				return SettableValue.this + "=" + value;
			}
		};
	}

	/**
	 * @param <V> The type of the value to set
	 * @param value The observable value to link this value to
	 * @return A subscription by which the link may be canceled
	 */
	default <V extends T> Subscription link(ObservableValue<V> value) {
		return value.changes().act(event -> {
			set(event.getNewValue(), event);
		});
	}

	/** @return This value, but not settable */
	default ObservableValue<T> unsettable() {
		return new UnsettableValue<>(this);
	}

	/**
	 * @param accept The filter
	 * @return A settable value that rejects values that return other than null for the given test
	 */
	default SettableValue<T> filterAccept(Function<? super T, String> accept) {
		return new FilterAcceptValue<>(this, accept);
	}

	/**
	 * Allows an alert when {@link #set(Object, Object)} on this value is called. This is different than subscribing to the value in that
	 * the action is <b>not</b> called when the value changes behind the scenes, but only when the {@link #set(Object, Object)} method on
	 * this value is called.
	 *
	 * @param onSetAction The action to invoke just before {@link #set(Object, Object)} is called
	 * @return The settable
	 */
	default SettableValue<T> onSet(Consumer<T> onSetAction) {
		SettableValue<T> source = this;
		return new WrappingSettableValue<T>(this) {
			@Override
			public Getter<T> lock(boolean tryOnly) {
				return source.lock(tryOnly);
			}

			@Override
			public Setter<T> lockWrite(boolean tryOnly, Object cause) {
				Setter<T> sourceSetter = source.lockWrite(tryOnly, cause);
				if (sourceSetter == null)
					return null;
				return new Setter<T>() {
					@Override
					public T get() {
						return sourceSetter.get();
					}

					@Override
					public String isEnabled() {
						return sourceSetter.isEnabled();
					}

					@Override
					public String isAcceptable(T value) {
						return sourceSetter.isAcceptable(value);
					}

					@Override
					public T set(T value) {
						onSetAction.accept(value);
						return sourceSetter.set(value);
					}

					@Override
					public void close() {
						sourceSetter.close();
					}
				};
			}

			@Override
			public T set(T value) throws IllegalArgumentException {
				onSetAction.accept(value);
				return getWrapped().set(value);
			}
		};
	}

	/**
	 * @param enabled The observable value to use to disable the value
	 * @return A settable value reflecting this value's value and enablement, but which is also disabled when <code>enabled</code> contains
	 *         a non-null value
	 */
	default SettableValue<T> disableWith(ObservableValue<String> enabled) {
		return new DisabledValue<>(this, enabled);
	}

	/**
	 * <p>
	 * Transforms this value into a derived value, potentially including other sources as well. This method satisfies both mapping and
	 * combination use cases.
	 * </p>
	 *
	 * @param <R> The type of the combined value
	 * @param combination Determines how this value an any other arguments are to be combined
	 * @return The transformed value
	 * @see Transformation for help using the API
	 */
	default <R> SettableValue<R> transformReversible(
		Function<Transformation.ReversibleTransformationPrecursor<T, R, ?>, Transformation.ReversibleTransformation<T, R>> combination) {
		Transformation.ReversibleTransformation<T, R> def = combination.apply(new Transformation.ReversibleTransformationPrecursor<>());
		if (def.getArgs().isEmpty() && FunctionUtils.isTrivial(def.getCombination()))
			return (SettableValue<R>) this;
		return new TransformedSettableValue<>(this, def);
	}

	/**
	 * @param <R> The type of the new settable value to create
	 * @param function The function to map this value to another
	 * @param reverse The function to map the other value to this one
	 * @return The mapped settable value
	 */
	default <R> SettableValue<R> map(Function<? super T, ? extends R> function, Function<? super R, ? extends T> reverse) {
		return map(function, reverse, null);
	}

	/**
	 * @param <R> The type of the new settable value to create
	 * @param type The type for the new value
	 * @param function The function to map this value to another
	 * @param reverse The function to map the other value to this one
	 * @param options Options determining the behavior of the result
	 * @return The mapped settable value
	 */
	default <R> SettableValue<R> map(Function<? super T, ? extends R> function, Function<? super R, ? extends T> reverse,
		Consumer<XformOptions> options) {
		return transformReversible(tx -> {
			if (options != null)
				options.accept(tx);
			return tx.map(function).withReverse(reverse);
		});
	}

	/**
	 * @param <R> The type of the new settable value to create
	 * @param type The type for the new value
	 * @param function The function to map this value to another
	 * @param reverse The function to map the other value to this one
	 * @param options Options determining the behavior of the result
	 * @return The mapped settable value
	 */
	default <R> SettableValue<R> map(Function<? super T, ? extends R> function, BiFunction<? super T, ? super R, ? extends T> reverse,
		Consumer<XformOptions> options) {
		return transformReversible(tx -> {
			if (options != null)
				options.accept(tx);
			return tx.map(function).replaceSourceWith((r, rtx) -> {
				return reverse.apply(rtx.getCurrentSource(), r);
			});
		});
	}

	/**
	 * Interprets this value as a selected value and returns a settable value for editing a particular field on the selected value
	 *
	 * @param fieldType The type of the field
	 * @param getter The getter for the field
	 * @param setter The setter for the field
	 * @param options Options for the returned value--may be null
	 * @return The field value
	 */
	default <F> SettableValue<F> asFieldEditor(Function<? super T, ? extends F> getter, BiConsumer<? super T, ? super F> setter,
		Consumer<XformOptions> options) {
		return transformReversible(tx -> {
			tx.nullToNull(true);
			if (options != null)
				options.accept(tx);
			return tx.map(getter).modifySource(setter);
		});
	}

	/**
	 * Composes this settable value with another observable value
	 *
	 * @param <U> The type of the value to compose this value with
	 * @param <R> The type of the new settable value to create
	 * @param function The function to combine the values into another value
	 * @param arg The value to combine this value with
	 * @param reverse The function to reverse the transformation
	 * @return The composed settable value
	 */
	default <U, R> SettableValue<R> compose(BiFunction<? super T, ? super U, R> function, ObservableValue<U> arg,
		BiFunction<? super R, ? super U, ? extends T> reverse) {
		return combine(function, arg, null, reverse, null);
	}

	/**
	 * Composes this settable value with another observable value
	 *
	 * @param <U> The type of the value to compose this value with
	 * @param <R> The type of the new settable value to create
	 * @param type The type of the new value
	 * @param function The function to combine the values into another value
	 * @param arg The value to combine this value with
	 * @param reverse The function to reverse the transformation
	 * @param options Options determining the behavior of the result
	 * @return The composed settable value
	 */
	default <U, R> SettableValue<R> combine(BiFunction<? super T, ? super U, R> function, ObservableValue<U> arg,
		BiFunction<? super R, ? super U, ? extends T> reverse, Consumer<XformOptions> options) {
		return combine(function, arg, null, reverse, options);
	}

	/**
	 * Composes this settable value with another observable value
	 *
	 * @param <U> The type of the value to compose this value with
	 * @param <R> The type of the new settable value to create
	 * @param type The type of the new value
	 * @param function The function to combine the values into another value
	 * @param arg The value to combine this value with
	 * @param accept The function to filter acceptance of values for the new value
	 * @param reverse The function to reverse the transformation
	 * @param options Options determining the behavior of the result
	 * @return The composed settable value
	 */
	default <U, R> SettableValue<R> combine(BiFunction<? super T, ? super U, R> function, ObservableValue<U> arg,
		BiFunction<? super R, ? super U, String> accept, BiFunction<? super R, ? super U, ? extends T> reverse,
		Consumer<XformOptions> options) {
		return transformReversible(tx -> {
			if (options != null)
				options.accept(tx);
			return tx.combineWith(arg).combine(function).replaceSource(reverse,
				accept == null ? null : rvrs -> rvrs.rejectWith((r, rtx) -> {
					return accept.apply(r, rtx.get(arg));
				}, false, false));
		});
	}

	/**
	 * Composes this settable value with 2 other observable values
	 *
	 * @param <U> The type of the first value to compose this value with
	 * @param <V> The type of the second value to compose this value with
	 * @param <R> The type of the new settable value to create
	 * @param function The function to combine the values into another value
	 * @param arg2 The first other value to combine this value with
	 * @param arg3 The second other value to combine this value with
	 * @param reverse The function to reverse the transformation
	 * @return The composed settable value
	 */
	default <U, V, R> SettableValue<R> combine(TriFunction<? super T, ? super U, ? super V, R> function, ObservableValue<U> arg2,
		ObservableValue<V> arg3, TriFunction<? super R, ? super U, ? super V, ? extends T> reverse) {
		return combine(function, arg2, arg3, reverse, null);
	}

	/**
	 * Composes this settable value with 2 other observable values
	 *
	 * @param <U> The type of the first value to compose this value with
	 * @param <V> The type of the second value to compose this value with
	 * @param <R> The type of the new settable value to create
	 * @param type The type of the new value
	 * @param function The function to combine the values into another value
	 * @param arg2 The first other value to combine this value with
	 * @param arg3 The second other value to combine this value with
	 * @param reverse The function to reverse the transformation
	 * @param options Options determining the behavior of the result
	 * @return The composed settable value
	 */
	default <U, V, R> SettableValue<R> combine(TriFunction<? super T, ? super U, ? super V, R> function, ObservableValue<U> arg2,
		ObservableValue<V> arg3, TriFunction<? super R, ? super U, ? super V, ? extends T> reverse, Consumer<XformOptions> options) {
		return transformReversible(tx -> {
			if (options != null)
				options.accept(tx);
			return tx.combineWith(arg2).combineWith(arg3).combine(function).withReverse(reverse);
		});
	}

	@Override
	default ObservableValue<T> noUpdates() {
		return new NoUpdatesSettableValue<>(this);
	}

	@Override
	default SettableValue<T> takeUntil(Observable<?> until) {
		return new SettableValueTakenUntil<>(this, until, true);
	}

	@Override
	default SettableValue<T> refresh(Observable<?> refresh) {
		return new RefreshingSettableValue<>(this, refresh);
	}

	@Override
	default SettableValue<T> refreshEach(Function<? super T, ? extends Observable<?>> refresh) {
		return new RefreshEachSettableValue<>(this, refresh);
	}

	@Override
	default SettableValue<T> safe(ThreadConstraint threading) {
		if (getThreadConstraint() == threading || getThreadConstraint() == ThreadConstraint.NONE)
			return this;
		return new SafeSettableValue<>(this, threading);
	}

	/**
	 * @param value An observable value that supplies settable values
	 * @return A settable value that represents the current value in the inner observable
	 */
	public static <T> SettableValue<T> flatten(ObservableValue<SettableValue<T>> value) {
		return flatten(value, FunctionUtils.constantSupplier(null));
	}

	/**
	 * @param value An observable value that supplies settable values
	 * @param defaultValue The default value supplier for when the outer observable is empty
	 * @return A settable value that represents the current value in the inner observable
	 */
	public static <T> SettableValue<T> flatten(ObservableValue<SettableValue<T>> value, Supplier<? extends T> defaultValue) {
		if (value.getThreadConstraint() == ThreadConstraint.NONE) {
			SettableValue<? extends T> v = value.get();
			if (v == null)
				return SettableValue.of(defaultValue == null ? null : defaultValue.get(), "Constant value");
			return (SettableValue<T>) v;
		}
		return new SettableFlattenedObservableValue<>(value, defaultValue);
	}

	/**
	 * @param value An observable value that supplies observable values that may possibly be settable
	 * @param defaultValue The default value supplier for when the outer observable is empty
	 * @return A settable value that represents the current value in the inner observable
	 */
	public static <T> SettableValue<T> flattenAsSettable(ObservableValue<? extends ObservableValue<T>> value,
		Supplier<? extends T> defaultValue) {
		if (value.getThreadConstraint() == ThreadConstraint.NONE) {
			ObservableValue<? extends T> v = value.get();
			if (v == null)
				return SettableValue.of(defaultValue == null ? null : defaultValue.get(), "Constant value");
			if (v instanceof SettableValue)
				return (SettableValue<T>) v;
			else
				return asSettable((ObservableValue<T>) v, __ -> "Not settable");
		}
		return new SettableFlattenedObservableValue<>(value, defaultValue);
	}

	/**
	 * @param <T> The type of the value
	 * @param value The value to represent
	 * @param disabled The message to report for the disablement of the value
	 * @return A SettableValue that reflects the given value and is always disabled
	 */
	public static <T> SettableValue<T> asSettable(ObservableValue<T> value, Function<? super T, String> disabled) {
		return new AlwaysDisabledValue<>(value, disabled);
	}

	/**
	 * @param <T> The type of the value
	 * @param value The value to represent
	 * @param lock The locking for the settable value
	 * @param set A consumer that effectively changes the value (and fires an event) when called
	 * @return A SettableValue that reflects the given value and is always enabled
	 */
	public static <T> SettableValue<T> settable(ObservableValue<T> value, CausalLock lock, Consumer<? super T> set) {
		return new SyntheticSettableValue<>(value, lock, set);
	}

	/**
	 * @param <T> The type of the value
	 * @param value The value
	 * @param disabled The {@link SettableValue#isEnabled() disabled} message
	 * @return An unmodifiable settable value
	 */
	public static <T> SettableValue<T> of(T value, String disabled) {
		return asSettable(ObservableValue.of(value), FunctionUtils.constantFn(disabled, disabled, disabled));
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
	public static <T> SettableValue<T> firstValue(Predicate<? super T> test, Supplier<? extends T> def,
		SettableValue<? extends T>... values) {
		return new FirstSettableValue<>(values, test, def);
	}

	/**
	 * Implements {@link SettableValue#unsettable()}
	 *
	 * @param <T> The type of the value
	 */
	class UnsettableValue<T> implements ObservableValue<T> {
		private final SettableValue<T> theSource;

		public UnsettableValue(SettableValue<T> value) {
			theSource = value;
		}

		/** @return The source value */
		protected SettableValue<T> getSource() {
			return theSource;
		}

		@Override
		public Getter<T> lock(boolean tryOnly) {
			return theSource.lock(tryOnly);
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return theSource.getThreadConstraint();
		}

		@Override
		public Object getIdentity() {
			return theSource.getIdentity();
		}

		@Override
		public UnsettableValue<T> alias(String alias) {
			theSource.alias(alias);
			return this;
		}

		@Override
		public Set<String> getAliases() {
			return theSource.getAliases();
		}

		@Override
		public long getStamp() {
			return theSource.getStamp();
		}

		@Override
		public T get() {
			return theSource.get();
		}

		@Override
		public Observable<ObservableValueEvent<T>> noInitChanges() {
			return theSource.noInitChanges();
		}

		@Override
		public boolean isEventing() {
			return theSource.isEventing();
		}

		@Override
		public String toString() {
			return theSource.toString();
		}
	}

	/**
	 * A utility class to make the boilerplate of creating settable values wrapping another settable value easier
	 *
	 * @param <T> The type of the value
	 */
	public abstract class WrappingSettableValue<T> extends AbstractIdentifiable implements SettableValue<T> {
		private final SettableValue<T> theWrapped;

		/** @param wrapped The wrapped value */
		protected WrappingSettableValue(SettableValue<T> wrapped) {
			theWrapped = wrapped;
		}

		/** @return The wrapped value */
		protected SettableValue<T> getWrapped() {
			return theWrapped;
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return theWrapped.getThreadConstraint();
		}

		@Override
		public Getter<T> lock(boolean tryOnly) {
			return theWrapped.lock(tryOnly);
		}

		@Override
		public boolean isEventing() {
			return theWrapped.isEventing();
		}

		@Override
		public Collection<Cause> getCurrentCauses() {
			return theWrapped.getCurrentCauses();
		}

		@Override
		public T get() {
			return theWrapped.get();
		}

		@Override
		public Observable<ObservableValueEvent<T>> noInitChanges() {
			return theWrapped.noInitChanges();
		}

		@Override
		public long getStamp() {
			return theWrapped.getStamp();
		}

		@Override
		protected Object createIdentity() {
			return theWrapped.getIdentity();
		}

		@Override
		public WrappingSettableValue<T> alias(String alias) {
			super.alias(alias);
			return this;
		}

		@Override
		public String isAcceptable(T value) {
			return theWrapped.isAcceptable(value);
		}

		@Override
		public ObservableValue<String> isEnabled() {
			return theWrapped.isEnabled();
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
			return getIdentity().toString();
		}
	}

	/**
	 * Implements {@link SettableValue#filterAccept(Function)}
	 *
	 * @param <T> The type of the value
	 */
	public class FilterAcceptValue<T> extends WrappingSettableValue<T> {
		private final Function<? super T, String> theFilter;

		/**
		 * @param wrapped The value to filter
		 * @param filter The filter for the {@link #isAcceptable(Object)} and {@link #set(Object)} methods
		 */
		public FilterAcceptValue(SettableValue<T> wrapped, Function<? super T, String> filter) {
			super(wrapped);
			theFilter = filter;
		}

		@Override
		public Getter<T> lock(boolean tryOnly) {
			return getWrapped().lock(tryOnly);
		}

		@Override
		public Setter<T> lockWrite(boolean tryOnly, Object cause) {
			Setter<T> wrapped = getWrapped().lockWrite(tryOnly, cause);
			if (wrapped == null)
				return null;
			return new Setter<T>() {
				@Override
				public T get() {
					return wrapped.get();
				}

				@Override
				public String isEnabled() {
					return wrapped.isEnabled();
				}

				@Override
				public String isAcceptable(T value) {
					String error = theFilter.apply(value);
					if (error != null)
						return error;
					return wrapped.isAcceptable(value);
				}

				@Override
				public T set(T value) {
					String error = theFilter.apply(value);
					if (error != null)
						throw new IllegalArgumentException(error);
					return wrapped.set(value);
				}

				@Override
				public void close() {
					wrapped.close();
				}
			};
		}

		@Override
		public T set(T value) throws IllegalArgumentException {
			String error = theFilter.apply(value);
			if (error != null)
				throw new IllegalArgumentException(error);
			return getWrapped().set(value);
		}

		@Override
		public String isAcceptable(T value) {
			String error = theFilter.apply(value);
			if (error != null)
				return error;
			return getWrapped().isAcceptable(value);
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(getWrapped().getIdentity(), "filterAccept", theFilter);
		}
	};

	/**
	 * Implements {@link SettableValue#transformReversible(Function)}
	 *
	 * @param <S> The type of the source value
	 * @param <T> The type of the combined value
	 */
	public class TransformedSettableValue<S, T> extends TransformedObservableValue<S, T> implements SettableValue<T> {
		/**
		 * @param source The source value to combine
		 * @param combination The definition of the combination operation
		 */
		public TransformedSettableValue(SettableValue<S> source, Transformation.ReversibleTransformation<S, T> combination) {
			super(source, combination);
		}

		@Override
		protected SettableValue<S> getSource() {
			return (SettableValue<S>) super.getSource();
		}

		@Override
		public Transformation.ReversibleTransformation<S, T> getTransformation() {
			return (Transformation.ReversibleTransformation<S, T>) super.getTransformation();
		}

		@Override
		public TransformedSettableValue<S, T> alias(String alias) {
			super.alias(alias);
			return this;
		}

		@Override
		public Setter<T> lockWrite(boolean tryOnly, Object cause) {
			Setter<S> sourceLock = getSource().lockWrite(tryOnly, cause);
			if (sourceLock == null)
				return null;
			Getter<TransformationState> engineLock = getEngine().lock(true);
			Transaction listenerLock = engineLock == null ? null : super.lockListeners(true, cause);
			if (listenerLock == null) {
				if (tryOnly) {
					if (engineLock != null)
						engineLock.close();
					sourceLock.close();
					return null;
				}
				do {
					if (engineLock != null)
						engineLock.close();
					sourceLock.close();
					sourceLock = getSource().lockWrite(false, cause);
					engineLock = getEngine().lock(true);
					listenerLock = engineLock == null ? null : super.lockListeners(true, cause);
				} while (listenerLock == null);
			}
			Setter<S> fSource = sourceLock;
			Getter<TransformationState> fEngineLock = engineLock;
			Transaction fListenerLock = listenerLock;
			// BiTuple<TransformedElement<S, T>, TransformationState> state = getState(false, false);
			return new Setter<T>() {
				@Override
				public T get() {
					BiTuple<TransformedElement<S, T>, TransformationState> state = getState(fSource, fEngineLock, false);
					return state.getValue1().getCurrentValue(state.getValue2());
				}

				@Override
				public String isEnabled() {
					BiTuple<TransformedElement<S, T>, TransformationState> state = getState(fSource, fEngineLock, false);
					return state.getValue1().isEnabled(state.getValue2());
				}

				@Override
				public String isAcceptable(T value) {
					BiTuple<TransformedElement<S, T>, TransformationState> state = getState(fSource, fEngineLock, false);
					ReverseQueryResult<S> rq = state.getValue1().set(value, state.getValue2(), true);
					if (rq.getError() != null)
						return rq.getError();
					return getSource().isAcceptable(rq.getReversed());
				}

				@Override
				public T set(T value) {
					BiTuple<TransformedElement<S, T>, TransformationState> state = getState(fSource, fEngineLock, false);
					S source = state.getValue1()//
						.set(//
							value, state.getValue2(), false)
						.getReversed();
					T prevResult = getTransformation().isCached() ? get() : null;
					S oldSource = getSource().set(source);
					return getTransformation().getCombination().apply(oldSource, new Transformation.TransformationValues<S, T>() {
						@Override
						public boolean isSourceChange() {
							return false;
						}

						@Override
						public S getCurrentSource() {
							return oldSource;
						}

						@Override
						public boolean hasPreviousResult() {
							return getTransformation().isCached();
						}

						@Override
						public T getPreviousResult() {
							return prevResult;
						}

						@Override
						public boolean has(ObservableValue<?> arg) {
							return getTransformation().hasArg(arg);
						}

						@Override
						public <V2> V2 get(ObservableValue<V2> arg) throws IllegalArgumentException {
							int index = getTransformation().getArgIndex(arg);
							return state.getValue2().get(index);
						}
					});
				}

				@Override
				public void close() {
					fListenerLock.close();
					fEngineLock.close();
					fSource.close();
				}
			};
		}

		@Override
		public Collection<Cause> getCurrentCauses() {
			return getSource().getCurrentCauses();
		}

		@Override
		public ObservableValue<String> isEnabled() {
			ObservableValue<String> txEnabled = transform(tx -> tx.cache(true).map(FunctionUtils.printableFn(__ -> {
				BiTuple<TransformedElement<S, T>, TransformationState> state = getState(getSource(), getEngine(), false);
				return state.getValue1().isEnabled(state.getValue2());
			}, "enabled", "enabled")));
			if (getTransformation().getReverse().requiresSourceModification()) {
				return ObservableValue.firstValue(FunctionUtils.NON_NULL, null, txEnabled, getSource().isEnabled());
			} else
				return txEnabled;
		}

		@Override
		public String isAcceptable(T value) {
			try (TransformedValueGetter t = lock(false)) {
				BiTuple<TransformedElement<S, T>, TransformationState> state = t.getState();
				ReverseQueryResult<S> rq = state.getValue1().set(value, state.getValue2(), true);
				if (rq.getError() != null)
					return rq.getError();
				return getSource().isAcceptable(rq.getReversed());
			}
		}

		@Override
		public T set(T value) throws IllegalArgumentException, UnsupportedOperationException {
			try (Setter<T> setter = lockWrite(false, null)) {
				return setter.set(value);
			}
		}
	}

	/**
	 * Implements {@link SettableValue#noUpdates()}
	 *
	 * @param <T> The type of the value
	 */
	class NoUpdatesSettableValue<T> extends NoUpdatesValue<T> implements SettableValue<T> {
		public NoUpdatesSettableValue(SettableValue<T> wrapped) {
			super(wrapped);
		}

		@Override
		protected SettableValue<T> getWrapped() {
			return (SettableValue<T>) super.getWrapped();
		}

		@Override
		public NoUpdatesSettableValue<T> alias(String alias) {
			super.alias(alias);
			return this;
		}

		@Override
		public Getter<T> lock(boolean tryOnly) {
			return getWrapped().lock(tryOnly);
		}

		@Override
		public Setter<T> lockWrite(boolean tryOnly, Object cause) {
			return getWrapped().lockWrite(tryOnly, cause);
		}

		@Override
		public Collection<Cause> getCurrentCauses() {
			return getWrapped().getCurrentCauses();
		}

		@Override
		public T set(T value) throws IllegalArgumentException, UnsupportedOperationException {
			return getWrapped().set(value);
		}

		@Override
		public String isAcceptable(T value) {
			return getWrapped().isAcceptable(value);
		}

		@Override
		public ObservableValue<String> isEnabled() {
			return getWrapped().isEnabled();
		}
	}

	/**
	 * Implements {@link SettableValue#takeUntil(Observable)}
	 *
	 * @param <T> The type of the value
	 */
	class SettableValueTakenUntil<T> extends ObservableValueTakenUntil<T> implements SettableValue<T> {
		private final ObservableValue<String> isEnabled;

		public SettableValueTakenUntil(SettableValue<T> wrap, Observable<?> until, boolean terminate) {
			super(wrap, until, terminate);
			isEnabled = wrap.isEnabled().takeUntil(until);
		}

		@Override
		protected SettableValue<T> getWrapped() {
			return (SettableValue<T>) super.getWrapped();
		}

		@Override
		public SettableValueTakenUntil<T> alias(String alias) {
			super.alias(alias);
			return this;
		}

		@Override
		public Getter<T> lock(boolean tryOnly) {
			return getWrapped().lock(tryOnly);
		}

		@Override
		public Setter<T> lockWrite(boolean tryOnly, Object cause) {
			return getWrapped().lockWrite(tryOnly, cause);
		}

		@Override
		public Collection<Cause> getCurrentCauses() {
			return getWrapped().getCurrentCauses();
		}

		@Override
		public T set(T value) throws IllegalArgumentException {
			return getWrapped().set(value);
		}

		@Override
		public String isAcceptable(T value) {
			return getWrapped().isAcceptable(value);
		}

		@Override
		public ObservableValue<String> isEnabled() {
			return isEnabled;
		}
	}

	/**
	 * Implements {@link SettableValue#refresh(Observable)}
	 *
	 * @param <T> The type of the value
	 */
	class RefreshingSettableValue<T> extends RefreshingObservableValue<T> implements SettableValue<T> {
		public RefreshingSettableValue(SettableValue<T> wrap, Observable<?> refresh) {
			super(wrap, refresh);
		}

		@Override
		protected SettableValue<T> getWrapped() {
			return (SettableValue<T>) super.getWrapped();
		}

		@Override
		public RefreshingSettableValue<T> alias(String alias) {
			super.alias(alias);
			return this;
		}

		@Override
		public Getter<T> lock(boolean tryOnly) {
			return getWrapped().lock(tryOnly);
		}

		@Override
		public Setter<T> lockWrite(boolean tryOnly, Object cause) {
			return getWrapped().lockWrite(tryOnly, cause);
		}

		@Override
		public Collection<Cause> getCurrentCauses() {
			return getWrapped().getCurrentCauses();
		}

		@Override
		public T set(T value) throws IllegalArgumentException {
			return getWrapped().set(value);
		}

		@Override
		public String isAcceptable(T value) {
			return getWrapped().isAcceptable(value);
		}

		@Override
		public ObservableValue<String> isEnabled() {
			return getWrapped().isEnabled();
		}
	}

	/**
	 * Implements {@link SettableValue#refreshEach(Function)}
	 *
	 * @param <T> The type of the value
	 */
	class RefreshEachSettableValue<T> extends RefreshEachValue<T> implements SettableValue<T> {
		public RefreshEachSettableValue(SettableValue<T> wrapped, Function<? super T, ? extends Observable<?>> refresh) {
			super(wrapped, refresh);
		}

		@Override
		protected SettableValue<T> getWrapped() {
			return (SettableValue<T>) super.getWrapped();
		}

		@Override
		public RefreshEachSettableValue<T> alias(String alias) {
			super.alias(alias);
			return this;
		}

		@Override
		public Setter<T> lockWrite(boolean tryOnly, Object cause) {
			// The purpose of the refresh lock is solely to prevent simultaneous refresh events,
			// or any refresh events that would violate the contract of a held lock
			// If this lock method will obtain any exclusive locks, then locking the refresh lock is unnecessary,
			// because incoming refresh updates obtain a read lock on the parent
			Setter<T> source = getWrapped().lockWrite(tryOnly, cause);
			if (source == null)
				return null;
			Observable<?> refresh = getRefresh().apply(source.get());
			Transaction refreshLock = refresh == null ? Transaction.NONE : refresh.lock(true);
			if (refreshLock == null) {
				if (tryOnly) {
					source.close();
					return null;
				}
				do {
					source.close();
					source = getWrapped().lockWrite(false, cause);
					refresh = getRefresh().apply(source.get());
					refreshLock = refresh == null ? Transaction.NONE : refresh.lock(true);
				} while (refreshLock == null);
			}
			return source.combine(refreshLock);
		}

		@Override
		public Collection<Cause> getCurrentCauses() {
			return getWrapped().getCurrentCauses();
		}

		@Override
		public T set(T value) throws IllegalArgumentException {
			return getWrapped().set(value);
		}

		@Override
		public String isAcceptable(T value) {
			return getWrapped().isAcceptable(value);
		}

		@Override
		public ObservableValue<String> isEnabled() {
			return getWrapped().isEnabled();
		}
	}

	/**
	 * Implements {@link SettableValue#safe(ThreadConstraint)}
	 *
	 * @param <T> The type of the value
	 */
	class SafeSettableValue<T> extends SafeObservableValue<T> implements SettableValue<T> {
		private ObservableValue<String> isEnabled;

		public SafeSettableValue(SettableValue<T> wrapped, ThreadConstraint threading) {
			super(wrapped, threading);
		}

		@Override
		protected SettableValue<T> getWrapped() {
			return (SettableValue<T>) super.getWrapped();
		}

		@Override
		public SafeSettableValue<T> alias(String alias) {
			super.alias(alias);
			return this;
		}

		@Override
		public Getter<T> lock(boolean tryOnly) {
			return getWrapped().lock(tryOnly);
		}

		@Override
		public Setter<T> lockWrite(boolean tryOnly, Object cause) {
			if (!getThreadConstraint().isEventThread()) {
				if (tryOnly)
					return null;
				throw new IllegalStateException(WRONG_THREAD_MESSAGE);
			}
			return getWrapped().lockWrite(tryOnly, cause);
		}

		@Override
		public Collection<Cause> getCurrentCauses() {
			return getWrapped().getCurrentCauses();
		}

		@Override
		public T set(T value) throws IllegalArgumentException, UnsupportedOperationException {
			if (!getThreadConstraint().isEventThread())
				throw new IllegalStateException(WRONG_THREAD_MESSAGE);
			return getWrapped().set(value);
		}

		@Override
		public String isAcceptable(T value) {
			return getWrapped().isAcceptable(value);
		}

		@Override
		public ObservableValue<String> isEnabled() {
			ObservableValue<String> enabled = isEnabled;
			if (enabled == null) {
				synchronized (this) {
					enabled = isEnabled;
					if (enabled == null)
						isEnabled = enabled = getWrapped().isEnabled().safe(getThreadConstraint());
				}
			}
			return enabled;
		}
	}

	/**
	 * Implements {@link SettableValue#flatten(ObservableValue)}
	 *
	 * @param <T> The type of the value
	 */
	class SettableFlattenedObservableValue<T> extends FlattenedObservableValue<T> implements SettableValue<T> {
		protected SettableFlattenedObservableValue(ObservableValue<? extends ObservableValue<? extends T>> value,
			Supplier<? extends T> defaultValue) {
			super(value, defaultValue);
		}

		@Override
		public SettableFlattenedObservableValue<T> alias(String alias) {
			super.alias(alias);
			return this;
		}

		@Override
		public Setter<T> lockWrite(boolean tryOnly, Object cause) {
			Getter<? extends ObservableValue<? extends T>> outer = getWrapped().lock(tryOnly);
			if (outer == null)
				return null;
			ObservableValue<? extends T> wrapped = outer.get();
			Setter<? extends T> setter;
			if (wrapped instanceof SettableValue)
				setter = ((SettableValue<T>) wrapped).lockWrite(true, cause);
			else {
				Getter<? extends T> getter = wrapped == null ? null : wrapped.lock(true);
				setter = getter == null ? null : new Setter.Unsettable<>(getter, getter, StdMsg.UNSUPPORTED_OPERATION);
			}
			if (setter == null) {
				if (tryOnly) {
					outer.close();
					return null;
				}
				do {
					outer.close();
					outer = getWrapped().lock(false);
					wrapped = getWrapped().get();
					if (wrapped instanceof SettableValue)
						setter = ((SettableValue<T>) wrapped).lockWrite(true, cause);
					else {
						Getter<? extends T> getter = wrapped == null ? null : wrapped.lock(true);
						setter = getter == null ? null : new Setter.Unsettable<>(getter, getter, StdMsg.UNSUPPORTED_OPERATION);
					}
				} while (setter == null);
			}
			return createSetter(outer, wrapped, setter, cause);
		}

		protected Setter<T> createSetter(Getter<? extends ObservableValue<? extends T>> outer, ObservableValue<? extends T> wrapped,
			Setter<? extends T> setter, Object cause) {
			return new FlattenedValueSetter(outer, wrapped, setter, cause);
		}

		protected class FlattenedValueSetter extends FlattenedValueGetter implements Setter<T> {
			private final Object theCause;

			public FlattenedValueSetter(Getter<? extends ObservableValue<? extends T>> outerGetter, ObservableValue<? extends T> innerValue,
				Setter<? extends T> innerGetter, Object cause) {
				super(outerGetter, innerValue, innerGetter);
				theCause = cause;
			}

			@Override
			protected Setter<? extends T> createInnerGetter(ObservableValue<? extends T> value) {
				if (value instanceof SettableValue)
					return ((SettableValue<? extends T>) value).lockWrite(true, theCause);
				else if (value != null) {
					Getter<? extends T> getter = value.lock(true);
					if (getter == null)
						return null;
					else
						return new Setter.Unsettable<>(getter, getter, StdMsg.UNSUPPORTED_OPERATION);
				} else
					return null;
			}

			@Override
			protected Setter<T> getInnerGetter() {
				return (Setter<T>) super.getInnerGetter();
			}

			@Override
			public String isEnabled() {
				Setter<T> innerSetter = getInnerGetter();
				return innerSetter == null ? StdMsg.UNSUPPORTED_OPERATION : innerSetter.isEnabled();
			}

			@Override
			public String isAcceptable(T value) {
				Setter<T> innerSetter = getInnerGetter();
				return innerSetter == null ? StdMsg.UNSUPPORTED_OPERATION : innerSetter.isAcceptable(value);
			}

			@Override
			public T set(T value) {
				Setter<T> innerSetter = getInnerGetter();
				if (innerSetter == null)
					throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
				return innerSetter.set(value);
			}
		}

		@Override
		public Collection<Cause> getCurrentCauses() {
			ObservableValue<? extends T> value = getWrapped().get();
			return value instanceof SettableValue ? ((SettableValue<?>) value).getCurrentCauses() : Collections.emptyList();
		}

		@Override
		public ObservableValue<String> isEnabled() {
			ObservableValue<ObservableValue<String>> wrapE = getWrapped().map(sv -> {
				if (sv == null)
					return ObservableValue.of("No wrapped value to set");
				else if (sv instanceof SettableValue)
					return ((SettableValue<? extends T>) sv).isEnabled();
				else
					return ObservableValue.of("Wrapped value is not settable");
			});
			return ObservableValue.flatten(wrapE);
		}

		@Override
		public String isAcceptable(T value) {
			ObservableValue<? extends T> sv = getWrapped().get();
			if (sv == null)
				return "No wrapped value to set";
			else if (sv instanceof SettableValue)
				return ((SettableValue<T>) sv).isAcceptable(value);
			else
				return "Wrapped value is not settable";
		}

		@Override
		public T set(T value) throws IllegalArgumentException {
			ObservableValue<? extends T> sv = getWrapped().get();
			if (sv == null)
				throw new IllegalArgumentException("No wrapped value to set");
			else if (sv instanceof SettableValue)
				return ((SettableValue<T>) sv).set(value);
			else
				throw new IllegalArgumentException("Wrapped value is not settable");
		}
	}

	/**
	 * Implements {@link SettableValue#asSettable(ObservableValue, Function)}
	 *
	 * @param <T> The type of the value
	 */
	class AlwaysDisabledValue<T> implements SettableValue<T> {
		private final ObservableValue<T> theValue;
		private final Function<? super T, String> theDisablement;

		protected AlwaysDisabledValue(ObservableValue<T> value, Function<? super T, String> disablement) {
			if (value == null || disablement == null)
				throw new NullPointerException();
			theValue = value;
			theDisablement = disablement;
		}

		protected ObservableValue<T> getValue() {
			return theValue;
		}

		protected Function<? super T, String> getDisablement() {
			return theDisablement;
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return theValue.getThreadConstraint();
		}

		@Override
		public Getter<T> lock(boolean tryOnly) {
			return theValue.lock(tryOnly);
		}

		@Override
		public Setter<T> lockWrite(boolean tryOnly, Object cause) {
			Getter<T> lock = theValue.lock(tryOnly);
			if (lock == null)
				return null;
			return new Setter.Unsettable<>(lock, Transaction.NONE, () -> theDisablement.apply(lock.get()));
		}

		@Override
		public boolean isEventing() {
			return theValue.isEventing();
		}

		@Override
		public Collection<Cause> getCurrentCauses() {
			return Collections.emptyList();
		}

		@Override
		public T get() {
			return theValue.get();
		}

		@Override
		public Observable<ObservableValueEvent<T>> noInitChanges() {
			return theValue.noInitChanges();
		}

		@Override
		public long getStamp() {
			return theValue.getStamp();
		}

		@Override
		public Object getIdentity() {
			return theValue.getIdentity();
		}

		@Override
		public AlwaysDisabledValue<T> alias(String alias) {
			theValue.alias(alias);
			return this;
		}

		@Override
		public Set<String> getAliases() {
			return theValue.getAliases();
		}

		@Override
		public T set(T value) throws IllegalArgumentException, UnsupportedOperationException {
			throw new UnsupportedOperationException(isAcceptable(value));
		}

		@Override
		public String isAcceptable(T value) {
			String enabled = theDisablement.apply(theValue.get());
			if (enabled == null)
				enabled = "Not enabled";
			return enabled;
		}

		@Override
		public ObservableValue<String> isEnabled() {
			return theValue.map(theDisablement);
		}

		@Override
		public int hashCode() {
			return theValue.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			return obj instanceof AlwaysDisabledValue && theValue.equals(((AlwaysDisabledValue<?>) obj).theValue);
		}

		@Override
		public String toString() {
			return theValue.toString();
		}
	}

	/**
	 * Implements {@link SettableValue#asSettable(ObservableValue, Function)}
	 *
	 * @param <T> The type of the value
	 */
	class DisabledValue<T> extends WrappingSettableValue<T> {
		private final ObservableValue<String> isEnabled;

		public DisabledValue(SettableValue<T> wrapped, ObservableValue<String> enabled) {
			super(wrapped);
			isEnabled = enabled;
		}

		protected ObservableValue<String> getEnabled() {
			return isEnabled;
		}

		@Override
		public Getter<T> lock(boolean tryOnly) {
			return getWrapped().lock(tryOnly);
		}

		@Override
		public Setter<T> lockWrite(boolean tryOnly, Object cause) {
			Setter<T> wrapped = getWrapped().lockWrite(tryOnly, cause);
			if (wrapped == null)
				return null;
			Getter<String> enabled = isEnabled.lock(true);
			if (enabled == null) {
				if (tryOnly) {
					wrapped.close();
					return null;
				}
				do {
					wrapped.close();
					wrapped = getWrapped().lockWrite(false, cause);
					enabled = isEnabled.lock(true);
				} while (enabled == null);
			}
			return new DVSetter<>(wrapped, enabled);
		}

		static class DVSetter<T> implements Setter<T> {
			private final Setter<T> theSource;
			private final Getter<String> theEnabled;

			DVSetter(Setter<T> source, Getter<String> enabled) {
				theSource = source;
				theEnabled = enabled;
			}

			@Override
			public T get() {
				return theSource.get();
			}

			@Override
			public String isEnabled() {
				String msg = theEnabled.get();
				if (msg == null)
					msg = theSource.isEnabled();
				return msg;
			}

			@Override
			public String isAcceptable(T value) {
				String msg = theEnabled.get();
				if (msg == null)
					msg = theSource.isAcceptable(value);
				return msg;
			}

			@Override
			public T set(T value) {
				String msg = theEnabled.get();
				if (msg != null)
					throw new UnsupportedOperationException(msg);
				return theSource.set(value);
			}

			@Override
			public void close() {
				theEnabled.close();
				theSource.close();
			}
		}

		@Override
		public String isAcceptable(T value) {
			String msg = isEnabled.get();
			if (msg != null)
				return msg;
			return getWrapped().isAcceptable(value);
		}

		@Override
		public T set(T value) throws IllegalArgumentException {
			String msg = isEnabled.get();
			if (msg != null)
				throw new IllegalArgumentException(msg);
			return getWrapped().set(value);
		}

		@Override
		public ObservableValue<String> isEnabled() {
			return ObservableValue.firstValue(FunctionUtils.NON_NULL, null, isEnabled, getWrapped().isEnabled());
		}
	}

	/**
	 * Implements {@link SettableValue#settable(ObservableValue, CausalLock, Consumer)}
	 *
	 * @param <T> The type of the value
	 */
	class SyntheticSettableValue<T> extends WrappingObservableValue<T, T> implements SettableValue<T> {
		private final CausalLock theLock;
		private final Consumer<? super T> theSet;

		public SyntheticSettableValue(ObservableValue<T> wrapped, CausalLock lock, Consumer<? super T> set) {
			super(wrapped);
			theLock = lock;
			theSet = set;
		}

		@Override
		public T get() {
			return getWrapped().get();
		}

		@Override
		public Observable<ObservableValueEvent<T>> noInitChanges() {
			return getWrapped().noInitChanges();
		}

		@Override
		public boolean isEventing() {
			return getWrapped().isEventing();
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(getWrapped().getIdentity(), "settable", theSet);
		}

		@Override
		public SyntheticSettableValue<T> alias(String alias) {
			super.alias(alias);
			return this;
		}

		@Override
		public Collection<Cause> getCurrentCauses() {
			return theLock.getCurrentCauses();
		}

		@Override
		public Getter<T> lock(boolean tryOnly) {
			Transaction lock = theLock.lock(tryOnly);
			if (lock == null)
				return null;
			Getter<T> getter = getWrapped().lock(true);
			if (getter == null) {
				if (tryOnly) {
					lock.close();
					return null;
				}
				do {
					lock.close();
					lock = theLock.lock(false);
					getter = getWrapped().lock(true);
				} while (getter == null);
			}
			return Getter.of(getter, lock);
		}

		@Override
		public Setter<T> lockWrite(boolean tryOnly, Object cause) {
			Transaction lock = theLock.lockWrite(tryOnly, cause);
			if (lock == null)
				return null;
			Getter<T> getter = getWrapped().lock(true);
			if (getter == null) {
				if (tryOnly) {
					lock.close();
					return null;
				}
				do {
					lock.close();
					lock = theLock.lock(false);
					getter = getWrapped().lock(true);
				} while (getter == null);
			}
			return new SSVSetter<>(getter, lock, theSet);
		}

		static class SSVSetter<T> implements Setter<T> {
			private final Getter<T> theGetter;
			private final Transaction theLock;
			private final Consumer<? super T> theSet;

			SSVSetter(Getter<T> getter, Transaction lock, Consumer<? super T> set) {
				theGetter = getter;
				theLock = lock;
				theSet = set;
			}

			@Override
			public T get() {
				return theGetter.get();
			}

			@Override
			public String isEnabled() {
				return null;
			}

			@Override
			public String isAcceptable(T value) {
				return null;
			}

			@Override
			public T set(T value) {
				T prev = get();
				theSet.accept(value);
				return prev;
			}

			@Override
			public void close() {
				theLock.close();
			}
		}

		@Override
		public T set(T value) throws IllegalArgumentException, UnsupportedOperationException {
			try (Transaction t = lockWrite(false, null)) {
				T old = get();
				theSet.accept(value);
				return old;
			}
		}

		@Override
		public String isAcceptable(T value) {
			return null;
		}

		@Override
		public ObservableValue<String> isEnabled() {
			// The creator of this value can use disableWith() to control this
			return SettableValue.ALWAYS_ENABLED;
		}
	}

	/**
	 * Implements {@link SettableValue#firstValue(Predicate, Supplier, SettableValue...)}
	 *
	 * @param <T> The type of the value
	 */
	class FirstSettableValue<T> extends FirstObservableValue<T> implements SettableValue<T> {
		public FirstSettableValue(SettableValue<? extends T>[] values, Predicate<? super T> test, Supplier<? extends T> def) {
			super(values, test, def);
		}

		@Override
		protected List<? extends SettableValue<? extends T>> getValues() {
			return (List<? extends SettableValue<? extends T>>) super.getValues();
		}

		@Override
		public FirstSettableValue<T> alias(String alias) {
			super.alias(alias);
			return this;
		}

		@Override
		public Setter<T> lockWrite(boolean tryOnly, Object cause) {
			Setter<? extends T>[] locks = new Setter[getValues().size()];
			Transaction fullLock = Transaction.and(locks);
			boolean success;
			do {
				success = true;
				boolean complete = false;
				try {
					for (int i = 0; success && i < locks.length; i++) {
						Setter<? extends T> lock = getValues().get(i).lockWrite(true, cause);
						if (lock == null)
							success = false;
						else
							locks[i] = lock;
					}
					complete = true;
				} finally {
					if (!success || !complete)
						fullLock.close();
				}
			} while (!success && !tryOnly);
			if (!success)
				return null;
			return new FSVSetter(locks, fullLock);
		}

		class FSVSetter implements Setter<T> {
			private final Setter<? extends T>[] theComponents;
			private final Transaction theFullLock;

			FSVSetter(Setter<? extends T>[] components, Transaction fullLock) {
				theComponents = components;
				theFullLock = fullLock;
			}

			@Override
			public T get() {
				for (Setter<? extends T> getter : theComponents) {
					T value = getter.get();
					if (test(value))
						return value;
				}
				return getDefault() == null ? null : getDefault().get();
			}

			@Override
			public String isEnabled() {
				for (Setter<? extends T> getter : theComponents) {
					String enabled = getter.isEnabled();
					if (enabled == null)
						return null;
					T value = getter.get();
					if (test(value))
						return enabled;
				}
				return StdMsg.UNSUPPORTED_OPERATION;
			}

			@Override
			public String isAcceptable(T value) {
				if (!test(value))
					return StdMsg.ILLEGAL_ELEMENT;
				for (Setter<? extends T> getter : theComponents) {
					String enabled = ((Setter<T>) getter).isAcceptable(value);
					if (enabled == null)
						return null;
					T valueI = getter.get();
					if (test(valueI))
						return enabled;
				}
				return StdMsg.UNSUPPORTED_OPERATION;
			}

			@Override
			public T set(T value) {
				if (!test(value))
					throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
				boolean isSet = false;
				for (Setter<? extends T> getter : theComponents) {
					String enabled = ((Setter<T>) getter).isAcceptable(value);
					T valueI;
					if (enabled == null) {
						isSet = true;
						valueI = ((Setter<T>) getter).set(value);
					} else
						valueI = getter.get();
					if (test(valueI)) {
						if (isSet)
							return valueI;
					}
				}
				if (isSet)
					return getDefault() == null ? null : getDefault().get();
				else
					throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			}

			@Override
			public void close() {
				theFullLock.close();
			}
		}

		@Override
		public Collection<Cause> getCurrentCauses() {
			return CollectionUtils.concat(QommonsUtils.filterMap(getValues(), null, v -> v.getCurrentCauses()));
		}

		@Override
		public ObservableValue<String> isEnabled() {
			ObservableValue<BiTuple<T, String>>[] evs = new ObservableValue[getValues().size()];
			for (int i = 0; i < evs.length; i++) {
				ObservableValue<String> enabledI = getValues().get(i).isEnabled();
				evs[i] = getValues().get(i).transform(tx -> tx.combineWith(enabledI).cache(false).combine((v, e) -> new BiTuple<>(v, e)));
			}
			return ObservableValue.firstValue(tuple -> {
				if (tuple.getValue2() == null)
					return true;
				else if (getTest().test(tuple.getValue1()))
					return true;
				else
					return false;
			}, null, evs).map(tuple -> tuple == null ? StdMsg.UNSUPPORTED_OPERATION : tuple.getValue2());
		}

		@Override
		public String isAcceptable(T value) {
			if (!test(value))
				return StdMsg.ILLEGAL_ELEMENT;
			String enabled = null;
			for (SettableValue<? extends T> v : getValues()) {
				String msg = ((SettableValue<T>) v).isAcceptable(value);
				if (msg == null)
					return null;
				else if (enabled == null)
					enabled = msg;
				if (getTest().test(v.get()))
					return enabled;
			}
			if (enabled == null)
				return StdMsg.UNSUPPORTED_OPERATION;
			return enabled;
		}

		@Override
		public T set(T value) throws IllegalArgumentException, UnsupportedOperationException {
			if (!test(value))
				throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
			String enabled = null;
			boolean set = false;
			T setValue = null;
			for (SettableValue<? extends T> v : getValues()) {
				T vValue = v.get();
				boolean pass = getTest().test(vValue);
				if (pass)
					setValue = vValue;
				String msg = ((SettableValue<T>) v).isAcceptable(value);
				if (msg == null)
					return ((SettableValue<T>) v).set(value);
				else if (enabled == null)
					enabled = msg;
				if (pass) {
					if (set)
						return setValue;
					else if (enabled != null)
						throw new IllegalArgumentException(enabled);
				}
			}
			if (enabled != null)
				throw new IllegalArgumentException(enabled);
			else
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}
	}

	/**
	 * @param <T> The type for the new value
	 * @param type The type for the new value
	 * @return A builder to create a new settable value
	 */
	static <T> Builder<T> build() {
		return new Builder<>();
	}

	/**
	 * @param <T> The type of the value
	 * @return The new settable value
	 */
	static <T> SettableValue<T> create() {
		return SettableValue.<T> build().build();
	}

	/**
	 * @param <T> The type of the value
	 * @param initialValue The initial value for the settable value
	 * @return The new settable value
	 */
	static <T> SettableValue<T> create(T initialValue) {
		return SettableValue.<T> build().withValue(initialValue).build();
	}

	/**
	 * Sometimes this create method is nicer than using {@link #build()} because that method usually requires the type to be specified
	 * explicitly, but this the compiler can often fill in the type for this method.
	 *
	 * @param <T> The type for the value
	 * @param build Configuration for the value
	 * @return The built value
	 */
	static <T> SettableValue<T> create(Consumer<Builder<T>> build) {
		Builder<T> builder = new Builder<>();
		build.accept(builder);
		return builder.build();
	}

	/** @param <T> The type for the settable value */
	class Builder<T> extends AbstractEventableBuilder<SettableValue<T>, Builder<T>> {
		private T theInitialValue;
		private boolean isNullable;

		Builder() {
			super("settable-value");
			isNullable = true;
		}

		public Builder<T> nullable(boolean nullable) {
			isNullable = nullable;
			return this;
		}

		public Builder<T> withValue(T value) {
			if (!isNullable && value == null)
				throw new IllegalArgumentException("This value cannot be null");
			theInitialValue = value;
			return this;
		}

		public SettableValue<T> build() {
			if (!isNullable && theInitialValue == null)
				throw new IllegalArgumentException("This value cannot be null.  Provide an initial value.");
			return new SimpleSettableValue<>(getDescription(), isNullable, buildData(), theInitialValue);
		}
	}
}
