package org.observe.supertest;

import java.util.List;

import org.observe.collect.CollectionChangeType;

public class CollectionOp<E> {
	private final CollectionOp<?> theRoot;

	final CollectionChangeType type;
	final LinkElement elementId;
	final int index;
	final E value;

	private String theMessage;
	private boolean isError;

	/**
	 * Use this constructor for operations that have been successfully performed on a collection
	 *
	 * @param type The change type
	 * @param elementId The ID of the added, removed, or changed element in the link
	 * @param index The index of the change in the link
	 * @param value The value added or removed, or the new value for the change
	 */
	public CollectionOp(CollectionChangeType type, LinkElement elementId, int index, E value) {
		theRoot=null;
		this.type = type;
		this.elementId = elementId;
		this.index = index;
		this.value = value;
	}

	/**
	 * Use this constructor for checking the potential for operations that have not yet been attempted
	 *
	 * @param root The root operation to set the error/message on (or null if this is to be the root)
	 * @param type The change type
	 * @param index The index of the change, or -1 if unspecified
	 * @param value The value to add or remove, or the new value for the change
	 */
	public CollectionOp(CollectionOp<?> root, CollectionChangeType type, int index, E value) {
		theRoot = root == null ? null : root.getRoot();
		elementId = null;
		this.type = type;
		this.index = index;
		this.value = value;
	}

	private CollectionOp<?> getRoot() {
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
			&& isSameType(ops) && isSameIndex(ops);
	}

	public static boolean isMultiRemove(List<? extends CollectionOp<?>> ops) {
		return ops.size() > 1 && ops.get(0).type == CollectionChangeType.remove && isSameType(ops);
	}
}