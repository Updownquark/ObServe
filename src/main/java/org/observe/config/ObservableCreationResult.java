package org.observe.config;

import org.observe.entity.ObservableEntityResult;

/**
 * An {@link ObservableEntityResult} for a creation operation (e.g. {@link ConfigurableValueCreator#createAsync(java.util.function.Consumer)})
 *
 * @param <E> The type of value being created
 */
public interface ObservableCreationResult<E> extends ObservableOperationResult<E> {
	/**
	 * @return The value created as a result of the operation, or null if this result's {@link #getStatus() status} is not
	 *         {@link org.observe.config.ObservableOperationResult.ResultStatus#FULFILLED fulfilled}
	 */
	E getNewValue();

	/**
	 * Creates a value result from a synchronously-obtained result value
	 *
	 * @param type The value type of this result
	 * @param value The value for the result
	 * @return A {@link ObservableOperationResult.ResultStatus#FULFILLED fulfilled} result
	 */
	public static <E> ObservableCreationResult<E> simpleResult(ConfiguredValueType<E> type, E value) {
		return new SimpleCreationResult<>(type, value);
	}

	/**
	 * Implements {@link ObservableCreationResult#simpleResult(ConfiguredValueType, Object)}
	 *
	 * @param <E> The type of the value
	 */
	class SimpleCreationResult<E> extends SimpleResult<E> implements ObservableCreationResult<E> {
		private final E theValue;

		public SimpleCreationResult(ConfiguredValueType<E> type, E value) {
			super(type);
			theValue = value;
		}

		@Override
		public E getNewValue() {
			return theValue;
		}
	}
}
