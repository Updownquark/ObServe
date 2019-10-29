package org.observe.supertest.dev2;

public interface TypeTransformation<E, T> {
	T map(E source);

	E reverse(T mapped);

	boolean isManyToOne();

	boolean isOneToMany();

	String reverseName();

	default TypeTransformation<T, E> reverse() {
		TypeTransformation<E, T> outer = this;
		return new TypeTransformation<T, E>() {
			@Override
			public E map(T source) {
				return outer.reverse(source);
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