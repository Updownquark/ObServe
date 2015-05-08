package org.observe;

import static org.observe.ObservableDebug.debug;
import static org.observe.ObservableDebug.lambda;

import java.util.function.BiFunction;
import java.util.function.Function;

import prisms.lang.Type;

/**
 * An observable value for which a value can be assigned directly
 *
 * @param <T> The type of the value
 */
public interface SettableValue<T> extends ObservableValue<T> {
	/**
	 * @param <V> The type of the value to set
	 * @param value The value to assign to this value
	 * @param cause Something that may have caused this change
	 * @return This value, for chaining
	 * @throws IllegalArgumentException If the value is not acceptable or setting it fails
	 */
	<V extends T> SettableValue<T> set(V value, Object cause) throws IllegalArgumentException;

	/**
	 * @param <V> The type of the value to check
	 * @param value The value to check
	 * @return null if the value is not known to be unacceptable for this value, or an error text if it is known to be unacceptable. A null
	 *         value returned from this method does not guarantee that a call to {@link #set(Object, Object)} for the same value will not
	 *         throw an IllegalArgumentException
	 */
	<V extends T> String isAcceptable(V value);

	/** @return An observable whose value reports whether or not this value can be set directly */
	ObservableValue<Boolean> isEnabled();

	/**
	 * @param <V> The type of the value to set
	 * @param value The observable value to link this value to
	 * @return A subscription by which the link may be canceled
	 */
	default <V extends T> Subscription<ObservableValueEvent<V>> link(ObservableValue<V> value) {
		return value.act(lambda(event -> {
			set(event.getValue(), event);
		}, "link"));
	}

	/** @return This value, but not settable */
	default ObservableValue<T> unsettable() {
		return debug(new ObservableValue<T>() {
			@Override
			public Type getType() {
				return SettableValue.this.getType();
			}

			@Override
			public T get() {
				return SettableValue.this.get();
			}

			@Override
			public Runnable observe(Observer<? super ObservableValueEvent<T>> observer) {
				return SettableValue.this.observe(observer);
			}
		}).from("unsettable", this).get();
	}

	/**
	 * @param <R> The type of the new settable value to create
	 * @param function The function to map this value to another
	 * @param reverse The function to map the other value to this one
	 * @return The mapped settable value
	 */
	public default <R> SettableValue<R> mapV(Function<? super T, R> function, Function<? super R, ? extends T> reverse) {
		return mapV(null, function, reverse, false);
	}

	/**
	 * @param <R> The type of the new settable value to create
	 * @param type The type for the new value
	 * @param function The function to map this value to another
	 * @param reverse The function to map the other value to this one
	 * @param combineNull Whether to apply the combination function if the arguments are null. If false and any arguments are null, the
	 *            result will be null.
	 * @return The mapped settable value
	 */
	public default <R> SettableValue<R> mapV(Type type, Function<? super T, R> function, Function<? super R, ? extends T> reverse,
		boolean combineNull) {
		SettableValue<T> root = this;
		return debug(new ComposedSettableValue<R>(type, lambda(args -> {
			return function.apply((T) args[0]);
		}, "mapV"), combineNull, this) {
			@Override
			public <V extends R> SettableValue<R> set(V value, Object cause) throws IllegalArgumentException {
				root.set(reverse.apply(value), cause);
				return this;
			}

			@Override
			public <V extends R> String isAcceptable(V value) {
				return root.isAcceptable(reverse.apply(value));
			}

			@Override
			public ObservableValue<Boolean> isEnabled() {
				return root.isEnabled();
			}
		}).from("map", this).using("map", function).using("reverse", reverse).tag("combineNull", combineNull).get();
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
	public default <U, R> SettableValue<R> composeV(BiFunction<? super T, ? super U, R> function, ObservableValue<U> arg,
		BiFunction<? super R, ? super U, ? extends T> reverse) {
		return combineV(null, function, arg, reverse, false);
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
	 * @param combineNull Whether to apply the combination function if the arguments are null. If false and any arguments are null, the
	 *            result will be null.
	 * @return The composed settable value
	 */
	public default <U, R> SettableValue<R> combineV(Type type, BiFunction<? super T, ? super U, R> function, ObservableValue<U> arg,
		BiFunction<? super R, ? super U, ? extends T> reverse, boolean combineNull) {
		SettableValue<T> root = this;
		return debug(new ComposedSettableValue<R>(type, lambda(args -> {
			return function.apply((T) args[0], (U) args[1]);
		}, "combineV"), combineNull, this) {
			@Override
			public <V extends R> SettableValue<R> set(V value, Object cause) throws IllegalArgumentException {
				root.set(reverse.apply(value, arg.get()), cause);
				return this;
			}

			@Override
			public <V extends R> String isAcceptable(V value) {
				return root.isAcceptable(reverse.apply(value, arg.get()));
			}

			@Override
			public ObservableValue<Boolean> isEnabled() {
				return root.isEnabled();
			}
		}).from("combine", this).from("with", arg).using("combination", function).using("reverse", reverse).tag("combineNull", combineNull)
		.get();
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
	 * @param combineNull Whether to apply the filter to null values or simply preserve the null
	 * @return The composed settable value
	 */
	public default <U, R> SettableValue<R> combineV(Type type, BiFunction<? super T, ? super U, R> function, ObservableValue<U> arg,
		BiFunction<? super R, ? super U, String> accept, BiFunction<? super R, ? super U, ? extends T> reverse, boolean combineNull) {
		SettableValue<T> root = this;
		return debug(new ComposedSettableValue<R>(type, args -> {
			return function.apply((T) args[0], (U) args[1]);
		}, combineNull, this) {
			@Override
			public <V extends R> SettableValue<R> set(V value, Object cause) throws IllegalArgumentException {
				root.set(reverse.apply(value, arg.get()), cause);
				return this;
			}

			@Override
			public <V extends R> String isAcceptable(V value) {
				U argVal = arg.get();
				String ret = accept.apply(value, argVal);
				if(ret == null)
					ret = root.isAcceptable(reverse.apply(value, arg.get()));
				return ret;
			}

			@Override
			public ObservableValue<Boolean> isEnabled() {
				return root.isEnabled();
			}
		}).from("combine", this).from("with", arg).using("combination", function).using("reverse", reverse).using("accept", accept)
		.tag("combineNull", combineNull).get();
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
	public default <U, V, R> SettableValue<R> combineV(TriFunction<? super T, ? super U, ? super V, R> function, ObservableValue<U> arg2,
		ObservableValue<V> arg3, TriFunction<? super R, ? super U, ? super V, ? extends T> reverse) {
		return combineV(null, function, arg2, arg3, reverse, false);
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
	 * @param combineNull Whether to apply the combination function if the arguments are null. If false and any arguments are null, the
	 *            result will be null.
	 * @return The composed settable value
	 */
	public default <U, V, R> SettableValue<R> combineV(Type type, TriFunction<? super T, ? super U, ? super V, R> function,
		ObservableValue<U> arg2, ObservableValue<V> arg3, TriFunction<? super R, ? super U, ? super V, ? extends T> reverse,
		boolean combineNull) {
		SettableValue<T> root = this;
		return debug(new ComposedSettableValue<R>(type, args -> {
			return function.apply((T) args[0], (U) args[1], (V) args[2]);
		}, combineNull, this) {
			@Override
			public <V2 extends R> SettableValue<R> set(V2 value, Object cause) throws IllegalArgumentException {
				root.set(reverse.apply(value, arg2.get(), arg3.get()), cause);
				return this;
			}

			@Override
			public <V2 extends R> String isAcceptable(V2 value) {
				return root.isAcceptable(reverse.apply(value, arg2.get(), arg3.get()));
			}

			@Override
			public ObservableValue<Boolean> isEnabled() {
				return root.isEnabled();
			}
		}).from("combine", this).from("with", arg2).from("with", arg3).using("combination", function).using("reverse", reverse)
		.tag("combineNull", combineNull).get();
	}

	/**
	 * Implements the SettableValue.combine methods
	 * 
	 * @param <T> The type of the value
	 */
	abstract class ComposedSettableValue<T> extends ComposedObservableValue<T> implements SettableValue<T> {
		public ComposedSettableValue(Function<Object [], T> function, boolean combineNull, ObservableValue<?> [] composed) {
			super(function, combineNull, composed);
		}

		public ComposedSettableValue(Type type, Function<Object [], T> function, boolean combineNull, ObservableValue<?>... composed) {
			super(type, function, combineNull, composed);
		}
	}
}
