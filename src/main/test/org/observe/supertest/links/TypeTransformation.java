package org.observe.supertest.links;

import org.observe.supertest.TestValueType;

public interface TypeTransformation<E, T> {
	TestValueType getSourceType();

	TestValueType getType();

	T map(E source);

	boolean supportsReverse();

	E reverse(T mapped);

	boolean isManyToOne();

	boolean isOneToMany();

	String reverseName();

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