package org.observe;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.observe.Transformation.ReverseQueryResult;
import org.observe.Transformation.TransformationState;
import org.observe.Transformation.TransformedElement;
import org.observe.util.TypeTokens;
import org.qommons.BiTuple;
import org.qommons.Identifiable;
import org.qommons.LambdaUtils;
import org.qommons.Lockable;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.TriFunction;
import org.qommons.collect.ListenerList;
import org.qommons.collect.MutableCollectionElement.StdMsg;

import com.google.common.reflect.TypeToken;

/**
 * An observable value for which a value can be assigned directly
 *
 * @param <T> The type of the value
 */
public interface SettableValue<T> extends ObservableValue<T>, Transactable {
	/** This class's wildcard {@link TypeToken} */
	static TypeToken<SettableValue<?>> TYPE = TypeTokens.get().keyFor(SettableValue.class).wildCard();

	/** TypeToken for String.class */
	TypeToken<String> STRING_TYPE = TypeTokens.get().of(String.class);

	/** A string-typed observable that always returns null */
	ObservableValue<String> ALWAYS_ENABLED = ObservableValue.of(STRING_TYPE, null);
	/** A string-typed observable that always returns {@link org.qommons.collect.MutableCollectionElement.StdMsg#UNSUPPORTED_OPERATION} */
	ObservableValue<String> ALWAYS_DISABLED = ObservableValue.of(STRING_TYPE, StdMsg.UNSUPPORTED_OPERATION);

	@Override
	boolean isLockSupported();

	/**
	 * @param <V> The type of the value to set
	 * @param value The value to assign to this value
	 * @param cause Something that may have caused this change
	 * @return The value that was previously set for in this container
	 * @throws IllegalArgumentException If the value is not acceptable or setting it fails
	 * @throws UnsupportedOperationException If this operation is not supported (e.g. because this value is {@link #isEnabled() disabled}
	 */
	<V extends T> T set(V value, Object cause) throws IllegalArgumentException, UnsupportedOperationException;

	/**
	 * @param <V> The type of the value to set
	 * @param value The value to assign to this value
	 * @param cause Something that may have caused this change
	 * @return The value that was previously set for in this container
	 * @throws IllegalArgumentException If the value is not acceptable or setting it fails
	 * @throws UnsupportedOperationException If this operation is not supported (e.g. because this value is {@link #isEnabled() disabled}
	 */
	default <V extends T> SettableValue<T> withValue(V value, Object cause) throws IllegalArgumentException, UnsupportedOperationException {
		set(value, cause);
		return this;
	}

	/**
	 * @param <V> The type of the value to check
	 * @param value The value to check
	 * @return null if the value is not known to be unacceptable for this value, or an error text if it is known to be unacceptable. A null
	 *         value returned from this method does not guarantee that a call to {@link #set(Object, Object)} for the same value will not
	 *         throw an IllegalArgumentException
	 */
	<V extends T> String isAcceptable(V value);

	/** @return An observable whose value reports null if this value can be set directly, or a string describing why it cannot */
	ObservableValue<String> isEnabled();

	@Override
	default Transaction lock() {
		return lock(false, null);
	}

	@Override
	default Transaction tryLock() {
		return tryLock(false, null);
	}

	/**
	 * @param value The value to assign this settable to
	 * @return An action whose {@link ObservableAction#isEnabled() enabled} property is tied to this settable's {@link #isEnabled() enabled}
	 *         property and the current value's {@link #isAcceptable(Object) acceptability} for this settable.
	 */
	default <V extends T> ObservableAction<V> assignmentTo(ObservableValue<V> value) {
		return new ObservableAction<V>() {
			@Override
			public TypeToken<V> getType() {
				return value.getType();
			}

			@Override
			public V act(Object cause) throws IllegalStateException {
				try {
					V newValue = value.get();
					set(newValue, cause);
					return newValue;
				} catch (IllegalArgumentException e) {
					throw new IllegalStateException(e.getMessage(), e);
				}
			}

			@Override
			public ObservableValue<String> isEnabled() {
				BiFunction<String, String, String> combineFn = (str1, str2) -> str1 != null ? str1 : str2;
				return SettableValue.this.isEnabled().combine(STRING_TYPE, combineFn,
					value.refresh(SettableValue.this.changes().noInit()).map(STRING_TYPE, v -> isAcceptable(v)),
					options -> options.fireIfUnchanged(false));
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
		return new WrappingSettableValue<T>(this) {
			@Override
			public <V extends T> T set(V value, Object cause) throws IllegalArgumentException {
				String error = accept.apply(value);
				if (error != null)
					throw new IllegalArgumentException(error);
				return theWrapped.set(value, cause);
			}

			@Override
			public <V extends T> String isAcceptable(V value) {
				String error = accept.apply(value);
				if (error != null)
					return error;
				return theWrapped.isAcceptable(value);
			}
		};
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
		return new WrappingSettableValue<T>(this) {
			@Override
			public <V extends T> T set(V value, Object cause) throws IllegalArgumentException {
				onSetAction.accept(value);
				return theWrapped.set(value, cause);
			}
		};
	}

	/**
	 * @param enabled The observable value to use to disable the value
	 * @return A settable value reflecting this value's value and enablement, but which is also disabled when <code>enabled</code> contains
	 *         a non-null value
	 */
	default SettableValue<T> disableWith(ObservableValue<String> enabled) {
		return new WrappingSettableValue<T>(this) {
			@Override
			public <V extends T> String isAcceptable(V value) {
				String msg = enabled.get();
				if (msg != null)
					return msg;
				return theWrapped.isAcceptable(value);
			}

			@Override
			public <V extends T> T set(V value, Object cause) throws IllegalArgumentException {
				String msg = enabled.get();
				if (msg != null)
					throw new IllegalArgumentException(msg);
				return theWrapped.set(value, cause);
			}

			@Override
			public ObservableValue<String> isEnabled() {
				return ObservableValue.firstValue(TypeTokens.get().STRING, s -> s != null, () -> null, enabled, theWrapped.isEnabled());
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
	 * @param <R> The type of the combined value
	 * @param combination Determines how this value an any other arguments are to be combined
	 * @return The transformed value
	 * @see Transformation for help using the API
	 */
	default <R> SettableValue<R> transformReversible(
		Function<Transformation.ReversibleTransformationPrecursor<T, R, ?>, Transformation.ReversibleTransformation<T, R>> combination) {
		return transformReversible((TypeToken<R>) null, combination);
	}

	/**
	 * Transforms this value into a derived value, potentially including other sources as well. This method satisfies both mapping and
	 * combination use cases.
	 *
	 * @param <R> The type of the combined value
	 * @param type The type of the combined value
	 * @param combination Determines how this value an any other arguments are to be combined
	 * @return The transformed value
	 * @see Transformation for help using the API
	 */
	default <R> SettableValue<R> transformReversible(TypeToken<R> type,
		Function<Transformation.ReversibleTransformationPrecursor<T, R, ?>, Transformation.ReversibleTransformation<T, R>> combination) {
		Transformation.ReversibleTransformation<T, R> def = combination.apply(new Transformation.ReversibleTransformationPrecursor<>());
		return new TransformedSettableValue<>(type, this, def);
	}

	/**
	 * Transforms this value into a derived value, potentially including other sources as well. This method satisfies both mapping and
	 * combination use cases.
	 *
	 * @param <R> The type of the combined value
	 * @param type The type of the combined value
	 * @param combination Determines how this value an any other arguments are to be combined
	 * @return The transformed value
	 * @see Transformation for help using the API
	 */
	default <R> SettableValue<R> transformReversible(Class<R> type,
		Function<Transformation.ReversibleTransformationPrecursor<T, R, ?>, Transformation.ReversibleTransformation<T, R>> combination) {
		return transformReversible(type == null ? null : TypeTokens.get().of(type), combination);
	}

	/**
	 * @param <R> The type of the new settable value to create
	 * @param function The function to map this value to another
	 * @param reverse The function to map the other value to this one
	 * @return The mapped settable value
	 */
	default <R> SettableValue<R> map(Function<? super T, ? extends R> function, Function<? super R, ? extends T> reverse) {
		return map(null, function, reverse, null);
	}

	/**
	 * @param <R> The type of the new settable value to create
	 * @param type The type for the new value
	 * @param function The function to map this value to another
	 * @param reverse The function to map the other value to this one
	 * @param options Options determining the behavior of the result
	 * @return The mapped settable value
	 */
	default <R> SettableValue<R> map(TypeToken<R> type, Function<? super T, ? extends R> function, Function<? super R, ? extends T> reverse,
		Consumer<XformOptions> options) {
		return transformReversible(type, tx -> {
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
	default <R> SettableValue<R> map(TypeToken<R> type, Function<? super T, ? extends R> function,
		BiFunction<? super T, ? super R, ? extends T> reverse, Consumer<XformOptions> options) {
		return transformReversible(type, tx -> {
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
	default <F> SettableValue<F> asFieldEditor(TypeToken<F> fieldType, Function<? super T, ? extends F> getter,
		BiConsumer<? super T, ? super F> setter, Consumer<XformOptions> options) {
		return transformReversible(fieldType, tx -> {
			tx.nullToNull(true);
			if (options != null)
				options.accept(tx);
			return tx.map(getter).modifySource(setter);
		});
	}

	/**
	 * Same as {@link #asFieldEditor(TypeToken, Function, BiConsumer, Consumer)}, but accepts a Class type
	 *
	 * @param fieldType The type of the field
	 * @param getter The getter for the field
	 * @param setter The setter for the field
	 * @param options Options for the returned value--may be null
	 * @return The field value
	 */
	default <F> SettableValue<F> asFieldEditor(Class<F> fieldType, Function<? super T, ? extends F> getter,
		BiConsumer<? super T, ? super F> setter, Consumer<XformOptions> options) {
		return transformReversible(fieldType, tx -> {
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
		return combine(null, function, arg, reverse, null);
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
	default <U, R> SettableValue<R> combine(TypeToken<R> type, BiFunction<? super T, ? super U, R> function, ObservableValue<U> arg,
		BiFunction<? super R, ? super U, ? extends T> reverse, Consumer<XformOptions> options) {
		return combine(type, function, arg, (__, ___) -> null, reverse, options);
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
	default <U, R> SettableValue<R> combine(TypeToken<R> type, BiFunction<? super T, ? super U, R> function, ObservableValue<U> arg,
		BiFunction<? super R, ? super U, String> accept, BiFunction<? super R, ? super U, ? extends T> reverse,
		Consumer<XformOptions> options) {
		return transformReversible(type, tx -> {
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
		return combine(null, function, arg2, arg3, reverse, null);
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
	default <U, V, R> SettableValue<R> combine(TypeToken<R> type, TriFunction<? super T, ? super U, ? super V, R> function,
		ObservableValue<U> arg2, ObservableValue<V> arg3, TriFunction<? super R, ? super U, ? super V, ? extends T> reverse,
		Consumer<XformOptions> options) {
		return transformReversible(type, tx -> {
			if (options != null)
				options.accept(tx);
			return tx.combineWith(arg2).combineWith(arg3).combine(function).withReverse(reverse);
		});
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
	default SettableValue<T> safe() {
		return new SafeSettableValue<>(this);
	}

	/**
	 * @param value An observable value that supplies settable values
	 * @return A settable value that represents the current value in the inner observable
	 */
	public static <T> SettableValue<T> flatten(ObservableValue<SettableValue<T>> value) {
		return flatten(value, () -> null);
	}

	/**
	 * @param value An observable value that supplies settable values
	 * @param defaultValue The default value supplier for when the outer observable is empty
	 * @return A settable value that represents the current value in the inner observable
	 */
	public static <T> SettableValue<T> flatten(ObservableValue<SettableValue<T>> value, Supplier<? extends T> defaultValue) {
		return new SettableFlattenedObservableValue<>(value, defaultValue);
	}

	/**
	 * @param value An observable value that supplies observable values that may possibly be settable
	 * @param defaultValue The default value supplier for when the outer observable is empty
	 * @return A settable value that represents the current value in the inner observable
	 */
	public static <T> SettableValue<T> flattenAsSettable(ObservableValue<? extends ObservableValue<T>> value,
		Supplier<? extends T> defaultValue) {
		return new SettableFlattenedObservableValue<>(value, defaultValue);
	}

	/**
	 * @param <T> The type of the value
	 * @param value The value to represent
	 * @param disabled The message to report for the disablement of the value
	 * @return A SettableValue that reflects the given value and is always enabled
	 */
	public static <T> SettableValue<T> asSettable(ObservableValue<T> value, Function<? super T, String> disabled){
		return new DisabledValue<>(value, disabled);
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
		public TypeToken<T> getType() {
			return theSource.getType();
		}

		@Override
		public Object getIdentity() {
			return theSource.getIdentity();
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
		public String toString() {
			return theSource.toString();
		}
	}

	/**
	 * A utility class to make the boilerplate of creating settable values wrapping another settable value easier
	 *
	 * @param <T> The type of the value
	 */
	class WrappingSettableValue<T> implements SettableValue<T> {
		protected final SettableValue<T> theWrapped;

		public WrappingSettableValue(SettableValue<T> wrapped) {
			theWrapped = wrapped;
		}

		@Override
		public TypeToken<T> getType() {
			return theWrapped.getType();
		}

		@Override
		public boolean isLockSupported() {
			return theWrapped.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theWrapped.lock(write, cause);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return theWrapped.lock(write, cause);
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
		public Object getIdentity() {
			return theWrapped.getIdentity();
		}

		@Override
		public <V extends T> T set(V value, Object cause) throws IllegalArgumentException, UnsupportedOperationException {
			return theWrapped.set(value, cause);
		}

		@Override
		public <V extends T> String isAcceptable(V value) {
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
	 * Implements {@link SettableValue#transformReversible(TypeToken, Function)}
	 *
	 * @param <S> The type of the source value
	 * @param <T> The type of the combined value
	 */
	public class TransformedSettableValue<S, T> extends TransformedObservableValue<S, T> implements SettableValue<T> {
		/**
		 * @param type The type of the combined value
		 * @param source The source value to combine
		 * @param combination The definition of the combination operation
		 */
		public TransformedSettableValue(TypeToken<T> type, SettableValue<S> source,
			Transformation.ReversibleTransformation<S, T> combination) {
			super(type, source, combination);
		}

		@Override
		protected SettableValue<S> getSource() {
			return (SettableValue<S>) super.getSource();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return Lockable.lockAll(Lockable.lockable(getSource(), write, cause), getEngine());
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return Lockable.tryLockAll(Lockable.lockable(getSource(), write, cause), getEngine());
		}

		@Override
		public ObservableValue<String> isEnabled() {
			return transform(TypeTokens.get().STRING, tx -> tx.cache(true).map(LambdaUtils.printableFn(__ -> {
				BiTuple<TransformedElement<S, T>, TransformationState> state = getState();
				return state.getValue1().isEnabled(state.getValue2());
			}, "enabled", "enabled")));
		}

		@Override
		public <V extends T> String isAcceptable(V value) {
			try (Transaction t = lock()) {
				BiTuple<TransformedElement<S, T>, TransformationState> state = getState();
				ReverseQueryResult<S> rq = state.getValue1().set(value, state.getValue2(), true);
				if (rq.getError() != null)
					return rq.getError();
				return getSource().isAcceptable(rq.getReversed());
			}
		}

		@Override
		public <V extends T> T set(V value, Object cause) throws IllegalArgumentException, UnsupportedOperationException {
			try (Transaction t = lock()) {
				BiTuple<TransformedElement<S, T>, TransformationState> state = getState();
				S source = state.getValue1().set(value, state.getValue2(), false).getReversed();
				T prevResult = getTransformation().isCached() ? get() : null;
				S oldSource = getSource().set(source, cause);
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
		public boolean isLockSupported() {
			return getWrapped().isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return getWrapped().lock(write, cause);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return getWrapped().tryLock(write, cause);
		}

		@Override
		public <V extends T> T set(V value, Object cause) throws IllegalArgumentException {
			return getWrapped().set(value, cause);
		}

		@Override
		public <V extends T> String isAcceptable(V value) {
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
	 * @param <T> The type of value
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
		public boolean isLockSupported() {
			return getWrapped().isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return getWrapped().lock(write, cause);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return getWrapped().tryLock(write, cause);
		}

		@Override
		public <V extends T> T set(V value, Object cause) throws IllegalArgumentException {
			return getWrapped().set(value, cause);
		}

		@Override
		public <V extends T> String isAcceptable(V value) {
			return getWrapped().isAcceptable(value);
		}

		@Override
		public ObservableValue<String> isEnabled() {
			return getWrapped().isEnabled();
		}
	}

	/**
	 * Implements {@link SettableValue#safe()}
	 *
	 * @param <T> The type of the value
	 */
	class SafeSettableValue<T> extends SafeObservableValue<T> implements SettableValue<T> {
		private final ObservableValue<String> isEnabled;

		public SafeSettableValue(SettableValue<T> wrap) {
			super(wrap);
			isEnabled = wrap.isEnabled().safe();
		}

		@Override
		protected SettableValue<T> getWrapped() {
			return (SettableValue<T>) super.getWrapped();
		}

		@Override
		public boolean isLockSupported() {
			return getWrapped().isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return getWrapped().lock(write, cause);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return getWrapped().tryLock(write, cause);
		}

		@Override
		public <V extends T> T set(V value, Object cause) throws IllegalArgumentException {
			return getWrapped().set(value, cause);
		}

		@Override
		public <V extends T> String isAcceptable(V value) {
			return getWrapped().isAcceptable(value);
		}

		@Override
		public ObservableValue<String> isEnabled() {
			return isEnabled;
		}

		@Override
		public SettableValue<T> safe() {
			return this;
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
		public boolean isLockSupported() {
			if (!getWrapped().isLockSupported())
				return false;
			ObservableValue<? extends T> value = getWrapped().get();
			if (value == null)
				return false;
			else
				return value.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return Lockable.lockAll(getWrapped(), () -> {
				ObservableValue<? extends T> value = getWrapped().get();
				if (value == null)
					return Collections.emptyList();
				else if (value instanceof SettableValue)
					return Arrays.asList(Lockable.lockable((SettableValue<? extends T>) value, write, cause));
				else
					return Arrays.asList(value);
			}, v -> v);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return Lockable.tryLockAll(getWrapped(), () -> {
				ObservableValue<? extends T> value = getWrapped().get();
				if (value == null)
					return Collections.emptyList();
				else if (value instanceof SettableValue)
					return Arrays.asList(Lockable.lockable((SettableValue<? extends T>) value, write, cause));
				else
					return Arrays.asList(value);
			}, v -> v);
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
		public <V extends T> String isAcceptable(V value) {
			ObservableValue<? extends T> sv = getWrapped().get();
			if (sv == null)
				return "No wrapped value to set";
			else if (sv instanceof SettableValue)
				return ((SettableValue<T>) sv).isAcceptable(value);
			else
				return "Wrapped value is not settable";
		}

		@Override
		public <V extends T> T set(V value, Object cause) throws IllegalArgumentException {
			ObservableValue<? extends T> sv = getWrapped().get();
			if (sv == null)
				throw new IllegalArgumentException("No wrapped value to set");
			else if (sv instanceof SettableValue)
				return ((SettableValue<T>) sv).set(value, cause);
			else
				throw new IllegalArgumentException("Wrapped value is not settable");
		}
	}

	/**
	 * Implements {@link SettableValue#asSettable(ObservableValue, Function)}
	 *
	 * @param <T> The type of the value
	 */
	class DisabledValue<T> implements SettableValue<T> {
		private final ObservableValue<T> theValue;
		private final Function<? super T, String> theDisablement;

		DisabledValue(ObservableValue<T> value, Function<? super T, String> disablement) {
			theValue = value;
			theDisablement = disablement;
		}

		@Override
		public TypeToken<T> getType() {
			return theValue.getType();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theValue.lock();
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return theValue.tryLock();
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
		public boolean isLockSupported() {
			return theValue.isLockSupported();
		}

		@Override
		public <V extends T> T set(V value, Object cause) throws IllegalArgumentException, UnsupportedOperationException {
			throw new UnsupportedOperationException(isAcceptable(value));
		}

		@Override
		public <V extends T> String isAcceptable(V value) {
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
			return obj instanceof DisabledValue && theValue.equals(((DisabledValue<?>) obj).theValue);
		}

		@Override
		public String toString() {
			return theValue.toString();
		}
	}

	/**
	 * @param <T> The type for the new value
	 * @param type The type for the new value
	 * @return A builder to create a new settable value
	 */
	static <T> Builder<T> build(Class<T> type) {
		return new Builder<>(TypeTokens.get().of(type));
	}

	/**
	 * @param <T> The type for the new value
	 * @param type The type for the new value
	 * @return A builder to create a new settable value
	 */
	static <T> Builder<T> build(TypeToken<T> type) {
		return new Builder<>(type);
	}

	/** @param <T> The type for the settable value */
	class Builder<T> {
		static final AtomicLong ID_GEN = new AtomicLong();

		private final TypeToken<T> theType;
		private String theDescription;
		private boolean isVetoable;
		private Function<Object, Transactable> theLock;
		private boolean isSafe;
		private ListenerList.Builder theListenerBuilder;
		private T theInitialValue;
		private boolean isNullable;

		Builder(TypeToken<T> type) {
			theType = type;
			theDescription = "settable-value";
			isSafe = true;
			theListenerBuilder = ListenerList.build();
			isNullable = true;
		}

		public Builder<T> vetoable() {
			isVetoable = true;
			return this;
		}

		public Builder<T> nullable(boolean nullable) {
			isNullable = nullable;
			return this;
		}

		public Builder<T> withLock(Transactable lock) {
			theLock = __ -> lock;
			return this;
		}

		public Builder<T> withLock(Function<Object, Transactable> lock) {
			theLock = lock;
			return this;
		}

		public Builder<T> safe(boolean safe) {
			isSafe = safe;
			return this;
		}

		public Builder<T> withDescription(String description) {
			theDescription = description;
			return this;
		}

		public Builder<T> withListening(Consumer<ListenerList.Builder> listening) {
			listening.accept(theListenerBuilder);
			return this;
		}

		public Builder<T> withValue(T value) {
			theInitialValue = value;
			return this;
		}

		public SettableValue<T> build() {
			Function<Object, Transactable> lock = theLock;
			if (lock == null && isSafe)
				lock = sv -> Transactable.transactable(new ReentrantReadWriteLock(), sv);
				if (isVetoable)
					return new VetoableSettableValue<>(theType, theDescription, isNullable, theListenerBuilder, lock).withValue(theInitialValue,
						null);
				else
					return new SimpleSettableValue<>(theType, theDescription, isNullable, lock, theListenerBuilder).withValue(theInitialValue,
						null);
		}
	}
}
