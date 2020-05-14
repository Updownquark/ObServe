package org.observe.supertest;

/**
 * A mapping for tests
 * 
 * @param <E> The source type of the mapping
 * @param <T> The target type of the mapping
 */
public interface TypeTransformation<E, T> {
	/** @return The source type */
	TestValueType getSourceType();

	/** @return The target type */
	TestValueType getType();

	/**
	 * @param source The source value
	 * @return The target value
	 */
	T map(E source);

	/** @return Whether this transformation supports {@link #reverse(Object) reversal} */
	boolean supportsReverse();

	/**
	 * @param mapped The target value to reverse-map
	 * @return The reversed source value
	 */
	E reverse(T mapped);

	/** @return Whether many source values may map to a single target value */
	boolean isManyToOne();

	/** @return Whether many target values may reverse-map to a single source value */
	boolean isOneToMany();

	/** @return The name of the {@link #reverse() reverse} mapping */
	String reverseName();

	/** @return The reverse of this mapping, if supported */
	default TypeTransformation<T, E> reverse() {
		if (!supportsReverse())
			throw new UnsupportedOperationException();
		TypeTransformation<E, T> outer = this;
		return new TypeTransformation<T, E>() {
			@Override
			public TestValueType getSourceType() {
				return outer.getType();
			}

			@Override
			public TestValueType getType() {
				return outer.getSourceType();
			}

			@Override
			public E map(T source) {
				return outer.reverse(source);
			}

			@Override
			public boolean supportsReverse() {
				return true;
			}

			@Override
			public T reverse(E mapped) {
				return outer.map(mapped);
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
			public TypeTransformation<E, T> reverse() {
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