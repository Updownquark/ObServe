package org.observe.supertest;

interface ObservableCollectionChainLink<E, T> extends ObservableChainLink<T> {
	static class CollectionOp<E> {
		E source;
		int index;

		E result;
		String message;
		boolean isError;

		CollectionOp(E source, int index) {
			this.source = source;
			this.index = index;
		}
	}

	void checkAddFromAbove(ObservableCollectionChainLink.CollectionOp<T> add);

	void checkRemoveFromAbove(ObservableCollectionChainLink.CollectionOp<T> remove);

	void checkSetFromAbove(ObservableCollectionChainLink.CollectionOp<T> value);

	void addedFromBelow(int index, E value);

	void removedFromBelow(int index);

	void setFromBelow(int index, E value);
}