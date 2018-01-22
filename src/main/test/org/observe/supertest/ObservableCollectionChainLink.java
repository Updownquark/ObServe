package org.observe.supertest;

import java.util.List;

import org.observe.collect.CollectionChangeType;
import org.observe.collect.ObservableCollection;
import org.observe.supertest.ObservableChainTester.TestValueType;
import org.qommons.TestHelper;

interface ObservableCollectionChainLink<E, T> extends ObservableChainLink<T> {
	static class CollectionOp<E> {
		final CollectionOp<?> theRoot;
		final CollectionChangeType type;
		final E value;
		final int index;

		private String theMessage;
		private boolean isError;

		CollectionOp(CollectionChangeType type, E source, int index){
			theRoot=null;
			this.type = type;
			this.value = source;
			this.index = index;
		}

		CollectionOp(CollectionOp<?> root, CollectionChangeType type, E source, int index) {
			theRoot = root == null ? null : root.getRoot();
			this.type = type;
			this.value = source;
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
			StringBuilder str = new StringBuilder(type.name()).append(' ');
			if (value != null)
				str.append(value);
			if (index >= 0)
				str.append('@').append(index);
			return str.toString();
		}

		public static boolean isSameIndex(List<? extends CollectionOp<?>> ops) {
			int idx = ops.get(0).index;
			for (int i = 1; i < ops.size(); i++)
				if (ops.get(i).index != idx)
					return false;
			return true;
		}

		public static boolean isSameType(List<? extends CollectionOp<?>> ops) {
			CollectionChangeType type = ops.get(0).type;
			for (int i = 1; i < ops.size(); i++)
				if (ops.get(i).type != type)
					return false;
			return true;
		}

		public static String print(List<? extends CollectionOp<?>> ops) {
			if (ops.isEmpty())
				return "[]";
			StringBuilder str = new StringBuilder();
			boolean separateTypes = !isSameType(ops);
			if (!separateTypes)
				str.append(ops.get(0).type);
			boolean sameIndexes = isSameIndex(ops);
			if (ops.get(0).index >= 0 && sameIndexes)
				str.append('@').append(ops.get(0).index);
			str.append('[');
			boolean first = true;
			for (CollectionOp<?> op : ops) {
				if (!first)
					str.append(", ");
				first = false;
				if (separateTypes)
					str.append(op.type);
				if (op.value != null)
					str.append(op.value);
				if (!sameIndexes && op.index >= 0)
					str.append('@').append(op.index);
			}
			str.append(']');
			return str.toString();
		}

		public static boolean isAddAllIndex(List<? extends CollectionOp<?>> ops) {
			return !ops.isEmpty()//
				&& ops.get(0).type == CollectionChangeType.add && ops.get(0).index >= 0//
				&& CollectionOp.isSameType(ops) && CollectionOp.isSameIndex(ops);
		}
	}

	TestValueType getTestType();

	@Override
	ObservableCollectionChainLink<T, ?> getChild();

	ObservableCollection<T> getCollection();

	List<T> getExpected();

	void checkModifiable(List<CollectionOp<T>> ops, int subListStart, int subListEnd, TestHelper helper);

	void fromBelow(List<CollectionOp<E>> ops, TestHelper helper);

	void fromAbove(List<CollectionOp<T>> ops, TestHelper helper, boolean above);
}