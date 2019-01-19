package org.observe;

import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.XformOptions.SimpleXformOptions;
import org.observe.XformOptions.XformDef;

import com.google.common.reflect.TypeToken;

public interface SettableStampedValue<T> extends SettableValue<T>, StampedObservableValue<T> {
	// TODO Throwing this together real quick with just what I need at the moment
	// Should implement a lot of other methods to return a stamped value

	@Override
	public default StampedObservableValue<T> unsettable() {
		return new StampedObservableValue<T>() {
			@Override
			public TypeToken<T> getType() {
				return SettableStampedValue.this.getType();
			}

			@Override
			public T get() {
				return SettableStampedValue.this.get();
			}

			@Override
			public Observable<ObservableValueEvent<T>> changes() {
				return SettableStampedValue.this.changes();
			}

			@Override
			public long getStamp() {
				return SettableStampedValue.this.getStamp();
			}

			@Override
			public String toString() {
				return SettableStampedValue.this.toString();
			}
		};
	}

	@Override
	default <R> StampedObservableValue<R> map(Function<? super T, R> function) {
		return map(null, function, options -> {});
	}

	@Override
	default <R> StampedObservableValue<R> map(TypeToken<R> type, Function<? super T, R> function) {
		return map(type, function, options -> {});
	}

	@Override
	default <R> StampedObservableValue<R> map(TypeToken<R> type, Function<? super T, R> function, Consumer<XformOptions> options) {
		SimpleXformOptions xform = new SimpleXformOptions();
		options.accept(xform);
		return new ComposedStampedValue<>(type, args -> {
			return function.apply((T) args[0]);
		}, new XformDef(xform), this);
	}

	@Override
	default <R> SettableStampedValue<R> map(Function<? super T, R> function, Function<? super R, ? extends T> reverse) {
		return map(null, function, reverse, options -> {});
	}

	@Override
	default <R> SettableStampedValue<R> map(TypeToken<R> type, Function<? super T, R> function, Function<? super R, ? extends T> reverse,
		Consumer<XformOptions> options) {
		SimpleXformOptions xform = new SimpleXformOptions();
		if (options != null)
			options.accept(xform);
		SettableValue<T> root = this;
		return new ComposedSettableStampedValue<R>(type, args -> {
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

	class ComposedStampedValue<T> extends ComposedObservableValue<T> implements StampedObservableValue<T> {
		public ComposedStampedValue(Function<Object[], T> function, XformDef options, StampedObservableValue<?>... composed) {
			super(function, options, composed);
		}

		public ComposedStampedValue(TypeToken<T> type, Function<Object[], T> function, XformDef options,
			StampedObservableValue<?>... composed) {
			super(type, function, options, composed);
		}

		@Override
		public long getStamp() {
			long stamp = 0;
			for (ObservableValue<?> composed : getComposed()) {
				stamp += ((StampedObservableValue<?>) composed).getStamp();
			}
			return stamp;
		}
	}

	abstract class ComposedSettableStampedValue<T> extends ComposedSettableValue<T> implements SettableStampedValue<T> {
		public ComposedSettableStampedValue(Function<Object[], T> function, XformDef options, StampedObservableValue<?>[] composed) {
			super(function, options, composed);
		}

		public ComposedSettableStampedValue(TypeToken<T> type, Function<Object[], T> function, XformDef options,
			StampedObservableValue<?>... composed) {
			super(type, function, options, composed);
		}

		@Override
		public long getStamp() {
			long stamp = 0;
			for (ObservableValue<?> composed : getComposed()) {
				stamp += ((StampedObservableValue<?>) composed).getStamp();
			}
			return stamp;
		}
	}
}
