package org.observe.supertest;

import org.observe.collect.Equivalence;
import org.qommons.TestHelper;

interface ObservableCollectionChainLink<E, T> extends ObservableChainLink<T> {
	static class CollectionOp<E> implements Cloneable {
		final Equivalence<? super E> equivalence;
		final E source;
		final int index;

		E result;
		String message;
		boolean isError;

		CollectionOp(Equivalence<? super E> equivalence, E source, int index) {
			this.equivalence = equivalence;
			this.source = source;
			this.index = index;
		}

		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof CollectionOp))
				return false;
			CollectionOp<?> other = (CollectionOp<?>) obj;
			return equivalence.elementEquals(result, other.result) && index == other.index && isError == other.isError
				&& (message == null) == (other.message == null);
		}

		@Override
		public CollectionOp<E> clone() {
			try {
				return (CollectionOp<E>) super.clone();
			} catch (CloneNotSupportedException e) {
				throw new IllegalStateException(e);
			}
		}
	}

	void checkAddable(CollectionOp<T> add, TestHelper helper);

	void checkRemovable(CollectionOp<T> remove, TestHelper helper);

	void checkSettable(CollectionOp<T> set, TestHelper helper);

	void addedFromBelow(int index, E value, TestHelper helper);

	void removedFromBelow(int index, TestHelper helper);

	void setFromBelow(int index, E value, TestHelper helper);

	void addedFromAbove(int index, T value, TestHelper helper);

	int removedFromAbove(int index, T value, TestHelper helper);

	void setFromAbove(int index, T value, TestHelper helper);
}