package org.observe.supertest.dev;

import java.util.List;

import org.observe.collect.CollectionChangeType;
import org.observe.collect.ObservableCollection;
import org.observe.supertest.dev.ObservableChainTester.TestValueType;
import org.qommons.TestHelper;

interface ObservableCollectionChainLink<E, T> extends ObservableChainLink<T> {
	static class CollectionOp<E> {
		final CollectionOp<?> theRoot;
		final CollectionChangeType type;
		final E source;
		final int index;

		private String theMessage;
		private boolean isError;

		CollectionOp(CollectionOp<?> root, CollectionChangeType type, E source, int index) {
			theRoot = root == null ? null : root.getRoot();
			this.type = type;
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

	TestValueType getTestType();

	@Override
	ObservableCollectionChainLink<T, ?> getChild();

	ObservableCollection<T> getCollection();

	List<T> getExpected();

	void checkModifiable(List<CollectionOp<T>> ops, int subListStart, int subListEnd, TestHelper helper);

	void fromBelow(List<CollectionOp<T>> ops, TestHelper helper);

	void fromAbove(List<CollectionOp<T>> ops, TestHelper helper, boolean above);
}