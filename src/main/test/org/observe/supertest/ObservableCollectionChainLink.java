package org.observe.supertest;

import java.util.List;

import org.observe.collect.ObservableCollection;
import org.qommons.TestHelper;

interface ObservableCollectionChainLink<E, T> extends ObservableChainLink<T> {
	static class CollectionOp<E> implements Cloneable {
		final CollectionOp<?> theRoot;
		final E source;
		final int index;

		private String theMessage;
		private boolean isError;

		CollectionOp(CollectionOp<?> root, E source, int index) {
			theRoot = root == null ? null : root.getRoot();
			this.source = source;
			this.index = index;
		}

		public CollectionOp<?> getRoot() {
			return theRoot == null ? this : theRoot;
		}

		public void reject(String message, boolean error) {
			if (theRoot != null)
				theRoot.reject(message, error);
			else {
				theMessage = message;
				isError = message != null && error;
			}
		}

		public String getMessage() {
			return theRoot == null ? theMessage : theRoot.getMessage();
		}

		public boolean isError() {
			return isError;
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

	@Override
	ObservableCollectionChainLink<T, ?> getChild();

	ObservableCollection<T> getCollection();

	List<T> getExpected();

	void checkAddable(List<CollectionOp<T>> adds, int subListStart, int subListEnd, TestHelper helper);

	void checkRemovable(List<CollectionOp<T>> removes, int subListStart, int subListEnd, TestHelper helper);

	void checkSettable(List<CollectionOp<T>> sets, int subListStart, int subListEnd, TestHelper helper);

	void addedFromBelow(List<CollectionOp<E>> adds, TestHelper helper);

	void removedFromBelow(int index, TestHelper helper);

	void setFromBelow(int index, E value, TestHelper helper);

	void addedFromAbove(List<CollectionOp<T>> adds, TestHelper helper, boolean above);

	void removedFromAbove(int index, T value, TestHelper helper, boolean above);

	void setFromAbove(int index, T value, TestHelper helper, boolean above);
}