package org.observe.supertest;

import java.util.List;

import org.observe.collect.ObservableCollection;
import org.qommons.TestHelper;

interface ObservableCollectionChainLink<E, T> extends ObservableChainLink<T> {
	static class CollectionOp<E> implements Cloneable {
		final E source;
		final int index;

		String message;
		boolean isError;

		CollectionOp(E source, int index) {
			this.source = source;
			this.index = index;
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			if (index >= 0)
				str.append('[').append(index).append(']');
			if (source != null) {
				if (str.length() > 0)
					str.append(": ");
				str.append(source);
			}
			return str.toString();
		}
	}

	ObservableCollection<T> getCollection();

	default void checkAddable(List<CollectionOp<T>> add, int subListStart, int subListEnd, TestHelper helper) {
		add.stream().forEach(a -> checkAddable(a, subListStart, subListEnd, helper));
	}

	void checkAddable(CollectionOp<T> add, int subListStart, int subListEnd, TestHelper helper);

	void checkRemovable(CollectionOp<T> remove, int subListStart, int subListEnd, TestHelper helper);

	void checkSettable(CollectionOp<T> set, int subListStart, int subListEnd, TestHelper helper);

	void addedFromBelow(int index, E value, TestHelper helper);

	void removedFromBelow(int index, TestHelper helper);

	void setFromBelow(int index, E value, TestHelper helper);

	default void addedAll(int index, List<T> values, TestHelper helper) {
		for (int i = 0; i < values.size(); i++)
			addedFromAbove(index >= 0 ? index + i : -1, values.get(i), helper, false);
	}

	void addedFromAbove(int index, T value, TestHelper helper, boolean above);

	void removedFromAbove(int index, T value, TestHelper helper, boolean above);

	void setFromAbove(int index, T value, TestHelper helper, boolean above);
}