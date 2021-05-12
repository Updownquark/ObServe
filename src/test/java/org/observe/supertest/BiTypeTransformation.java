package org.observe.supertest;

/**
 * A 2-to-1 transformation
 *
 * @param <S> The source value type
 * @param <V> The input value type
 * @param <T> The target type
 */
public interface BiTypeTransformation<S, V, T> {
	/** @return The source value type */
	TestValueType getSourceType();

	/** @return The input value type */
	TestValueType getValueType();

	/** @return The target type */
	TestValueType getTargetType();

	/**
	 * @param source The source value to map
	 * @param value The input value to combine
	 * @return The result value
	 */
	T map(S source, V value);

	/** @return Whether this transformation supports {@link #reverse(Object, Object) reversal} */
	boolean supportsReverse();

	/**
	 * @param mapped The result value to reverse
	 * @param value The input value to de-combine
	 * @return The reversed source value
	 */
	S reverse(T mapped, V value);

	/** @return Whether many source values may map to a single target value */
	boolean isManyToOne();

	/** @return Whether many target values may reverse-map to a single source value */
	boolean isOneToMany();

	/** @return The name of the {@link #reverse() reverse} mapping */
	String reverseName();

	/** @return The reverse of this mapping, if supported */
	default BiTypeTransformation<T, V, S> reverse() {
		if (!supportsReverse())
			throw new UnsupportedOperationException();
		BiTypeTransformation<S, V, T> outer = this;
		return new BiTypeTransformation<T, V, S>() {
			@Override
			public TestValueType getSourceType() {
				return outer.getTargetType();
			}

			@Override
			public TestValueType getValueType() {
				return outer.getValueType();
			}

			@Override
			public TestValueType getTargetType() {
				return outer.getSourceType();
			}

			@Override
			public S map(T source, V value) {
				return outer.reverse(source, value);
			}

			@Override
			public boolean supportsReverse() {
				return true;
			}

			@Override
			public T reverse(S mapped, V value) {
				return outer.map(mapped, value);
			}

			@Override
			public boolean isManyToOne() {
				return outer.isOneToMany();
			}

			@Override
			public boolean isOneToMany() {
				return outer.isManyToOne();
			}

			@Override
			public BiTypeTransformation<S, V, T> reverse() {
				return outer;
			}

			@Override
			public String reverseName() {
				return outer.toString();
			}

			@Override
			public String toString() {
				return outer.reverseName();
			}
		};
	}
}