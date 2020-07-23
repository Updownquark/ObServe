package org.observe.config;

/**
 * An {@link ObservableOperationResult} for a creation operation (e.g.
 * {@link ConfigurableValueCreator#createAsync(java.util.function.Consumer)})
 *
 * @param <E> The type of value being created
 */
public interface ObservableCreationResult<E> extends ObservableOperationResult<E, E> {
	/**
	 * Creates a value result from a synchronously-obtained result value
	 *
	 * @param type The value type of this result
	 * @param value The value for the result
	 * @return A {@link OperationResult.ResultStatus#FULFILLED fulfilled} result
	 */
	public static <E> ObservableCreationResult<E> simpleResult(ConfiguredValueType<E> type, E value) {
		return new SimpleCreationResult<>(type, value);
	}

	/**
	 * Implements {@link ObservableCreationResult#simpleResult(ConfiguredValueType, Object)}
	 *
	 * @param <E> The type of the value
	 */
	class SimpleCreationResult<E> extends SynchronousResult<E, E> implements ObservableCreationResult<E> {
		public SimpleCreationResult(ConfiguredValueType<E> type, E value) {
			super(type, value);
		}
	}
}
