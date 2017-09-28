package org.observe;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.observe.XformOptions.SimpleXformOptions;
import org.observe.XformOptions.XformDef;
import org.qommons.Transaction;
import org.qommons.TriFunction;

import com.google.common.reflect.TypeToken;

/**
 * An observable value for which a value can be assigned directly
 *
 * @param <T> The type of the value
 */
public interface SettableValue<T> extends ObservableValue<T> {
	/** TypeToken for String.class */
	TypeToken<String> STRING_TYPE = TypeToken.of(String.class);

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
	 * @param <V> The type of the value to check
	 * @param value The value to check
	 * @return null if the value is not known to be unacceptable for this value, or an error text if it is known to be unacceptable. A null
	 *         value returned from this method does not guarantee that a call to {@link #set(Object, Object)} for the same value will not
	 *         throw an IllegalArgumentException
	 */
	<V extends T> String isAcceptable(V value);

	/** @return An observable whose value reports null if this value can be set directly, or a string describing why it cannot */
	ObservableValue<String> isEnabled();

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
					value.refresh(SettableValue.this.changes().noInit()).map(STRING_TYPE, v -> isAcceptable(v)), options -> {});
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
		return new ObservableValue<T>() {
			@Override
			public TypeToken<T> getType() {
				return SettableValue.this.getType();
			}

			@Override
			public T get() {
				return SettableValue.this.get();
			}

			@Override
			public Observable<ObservableValueEvent<T>> changes() {
				return SettableValue.this.changes();
			}

			@Override
			public Transaction lock() {
				return SettableValue.this.lock();
			}

			@Override
			public String toString() {
				return SettableValue.this.toString();
			}
		};
	}

	/**
	 * @param accept The filter
	 * @return A settable value that rejects values that return other than null for the given test
	 */
	default SettableValue<T> filterAccept(Function<? super T, String> accept) {
		SettableValue<T> outer = this;
		return new SettableValue<T>() {
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
				return SettableValue.this.changes();
			}

			@Override
			public Transaction lock() {
				return outer.lock();
			}

			@Override
			public <V extends T> T set(V value, Object cause) throws IllegalArgumentException {
				String error = accept.apply(value);
				if (error != null)
					throw new IllegalArgumentException(error);
				return outer.set(value, cause);
			}

			@Override
			public <V extends T> String isAcceptable(V value) {
				String error = accept.apply(value);
				if (error != null)
					return error;
				return outer.isAcceptable(value);
			}

			@Override
			public ObservableValue<String> isEnabled() {
				return outer.isEnabled();
			}

			@Override
			public String toString() {
				return SettableValue.this.toString();
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
		SettableValue<T> outer = this;
		return new SettableValue<T>() {
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
				return SettableValue.this.changes();
			}

			@Override
			public Transaction lock() {
				return outer.lock();
			}

			@Override
			public <V extends T> T set(V value, Object cause) throws IllegalArgumentException {
				onSetAction.accept(value);
				return outer.set(value, cause);
			}

			@Override
			public <V extends T> String isAcceptable(V value) {
				return outer.isAcceptable(value);
			}

			@Override
			public ObservableValue<String> isEnabled() {
				return outer.isEnabled();
			}

			@Override
			public String toString() {
				return SettableValue.this.toString();
			}
		};
	}

	/**
	 * @param enabled The observable value to use to disable the value
	 * @return A settable value reflecting this value's value and enablement, but which is also disabled when <code>enabled</code> contains
	 *         a non-null value
	 */
	default SettableValue<T> disableWith(ObservableValue<String> enabled) {
		SettableValue<T> outer = this;
		return new SettableValue<T>() {
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
				return SettableValue.this.changes();
			}

			@Override
			public Transaction lock() {
				return outer.lock();
			}

			@Override
			public <V extends T> T set(V value, Object cause) throws IllegalArgumentException {
				String msg = enabled.get();
				if (msg != null)
					throw new IllegalArgumentException(msg);
				return outer.set(value, cause);
			}

			@Override
			public <V extends T> String isAcceptable(V value) {
				return outer.isAcceptable(value);
			}

			@Override
			public ObservableValue<String> isEnabled() {
				return ObservableValue.firstValue(TypeToken.of(String.class), s -> s != null, () -> null, enabled, outer.isEnabled());
			}

			@Override
			public String toString() {
				return SettableValue.this.toString();
			}
		};
	}

	/**
	 * @param <R> The type of the new settable value to create
	 * @param function The function to map this value to another
	 * @param reverse The function to map the other value to this one
	 * @return The mapped settable value
	 */
	public default <R> SettableValue<R> map(Function<? super T, R> function, Function<? super R, ? extends T> reverse) {
		return map(null, function, reverse, options -> {});
	}

	/**
	 * @param <R> The type of the new settable value to create
	 * @param type The type for the new value
	 * @param function The function to map this value to another
	 * @param reverse The function to map the other value to this one
	 * @param options Options determining the behavior of the result
	 * @return The mapped settable value
	 */
	public default <R> SettableValue<R> map(TypeToken<R> type, Function<? super T, R> function, Function<? super R, ? extends T> reverse,
		Consumer<XformOptions> options) {
		SimpleXformOptions xform = new SimpleXformOptions();
		options.accept(xform);
		SettableValue<T> root = this;
		return new ComposedSettableValue<R>(type, args -> {
			return function.apply((T) args[0]);
		}, new XformDef(xform), this) {
			@Override
			public <V extends R> R set(V value, Object cause) throws IllegalArgumentException {
				T old = root.set(reverse.apply(value), cause);
				return function.apply(old);
			}

			@Override
			public <V extends R> String isAcceptable(V value) {
				return root.isAcceptable(reverse.apply(value));
			}

			@Override
			public ObservableValue<String> isEnabled() {
				return root.isEnabled();
			}
		};
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
	public default <U, R> SettableValue<R> compose(BiFunction<? super T, ? super U, R> function, ObservableValue<U> arg,
		BiFunction<? super R, ? super U, ? extends T> reverse) {
		return combine(null, function, arg, reverse, options -> {});
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
	public default <U, R> SettableValue<R> combine(TypeToken<R> type, BiFunction<? super T, ? super U, R> function, ObservableValue<U> arg,
		BiFunction<? super R, ? super U, ? extends T> reverse, Consumer<XformOptions> options) {
		SimpleXformOptions xform = new SimpleXformOptions();
		options.accept(xform);
		SettableValue<T> root = this;
		return new ComposedSettableValue<R>(type, args -> {
			return function.apply((T) args[0], (U) args[1]);
		}, new XformDef(xform), this, arg) {
			@Override
			public <V extends R> R set(V value, Object cause) throws IllegalArgumentException {
				U argVal = arg.get();
				T old = root.set(reverse.apply(value, argVal), cause);
				return function.apply(old, argVal);
			}

			@Override
			public <V extends R> String isAcceptable(V value) {
				return root.isAcceptable(reverse.apply(value, arg.get()));
			}

			@Override
			public ObservableValue<String> isEnabled() {
				return root.isEnabled();
			}
		};
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
	public default <U, R> SettableValue<R> combine(TypeToken<R> type, BiFunction<? super T, ? super U, R> function, ObservableValue<U> arg,
		BiFunction<? super R, ? super U, String> accept, BiFunction<? super R, ? super U, ? extends T> reverse,
		Consumer<XformOptions> options) {
		SimpleXformOptions xform = new SimpleXformOptions();
		options.accept(xform);
		SettableValue<T> root = this;
		return new ComposedSettableValue<R>(type, args -> {
			return function.apply((T) args[0], (U) args[1]);
		}, new XformDef(xform), this, arg) {
			@Override
			public <V extends R> R set(V value, Object cause) throws IllegalArgumentException {
				U argVal = arg.get();
				T old = root.set(reverse.apply(value, argVal), cause);
				return function.apply(old, argVal);
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
			public ObservableValue<String> isEnabled() {
				return root.isEnabled();
			}
		};
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
	public default <U, V, R> SettableValue<R> combine(TriFunction<? super T, ? super U, ? super V, R> function, ObservableValue<U> arg2,
		ObservableValue<V> arg3, TriFunction<? super R, ? super U, ? super V, ? extends T> reverse) {
		return combine(null, function, arg2, arg3, reverse, options -> {});
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
	public default <U, V, R> SettableValue<R> combine(TypeToken<R> type, TriFunction<? super T, ? super U, ? super V, R> function,
		ObservableValue<U> arg2, ObservableValue<V> arg3, TriFunction<? super R, ? super U, ? super V, ? extends T> reverse,
		Consumer<XformOptions> options) {
		SimpleXformOptions xform = new SimpleXformOptions();
		options.accept(xform);
		SettableValue<T> root = this;
		return new ComposedSettableValue<R>(type, args -> {
			return function.apply((T) args[0], (U) args[1], (V) args[2]);
		}, new XformDef(xform), this, arg2, arg3) {
			@Override
			public <V2 extends R> R set(V2 value, Object cause) throws IllegalArgumentException {
				U arg2Val = arg2.get();
				V arg3Val = arg3.get();
				T old = root.set(reverse.apply(value, arg2Val, arg3Val), cause);
				return function.apply(old, arg2Val, arg3Val);
			}

			@Override
			public <V2 extends R> String isAcceptable(V2 value) {
				return root.isAcceptable(reverse.apply(value, arg2.get(), arg3.get()));
			}

			@Override
			public ObservableValue<String> isEnabled() {
				return root.isEnabled();
			}
		};
	}

	@Override
	default SettableValue<T> takeUntil(Observable<?> until){
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
	 * Implements the SettableValue.combine methods
	 *
	 * @param <T> The type of the value
	 */
	abstract class ComposedSettableValue<T> extends ComposedObservableValue<T> implements SettableValue<T> {
		public ComposedSettableValue(Function<Object[], T> function, XformDef options, ObservableValue<?>[] composed) {
			super(function, options, composed);
		}

		public ComposedSettableValue(TypeToken<T> type, Function<Object[], T> function, XformDef options, ObservableValue<?>... composed) {
			super(type, function, options, composed);
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
}
