package org.observe.supertest;

import org.observe.collect.Equivalence;

interface ObservableCollectionChainLink<E, T> extends ObservableChainLink<T> {
	static class CollectionOp<E> implements Cloneable {
		final Equivalence<? super E> equivalence;
		E source;
		int index;

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

	void checkAddFromAbove(ObservableCollectionChainLink.CollectionOp<T> add);

	void checkRemoveFromAbove(ObservableCollectionChainLink.CollectionOp<T> remove);

	void checkSetFromAbove(ObservableCollectionChainLink.CollectionOp<T> value);

	void addedFromBelow(int index, E value);

	void removedFromBelow(int index);

	void setFromBelow(int index, E value);
}