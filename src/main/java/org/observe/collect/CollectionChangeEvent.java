package org.observe.collect;

import java.util.AbstractList;
import java.util.Collections;
import java.util.List;

import org.qommons.Causable;

/**
 * Represents a set of changes to a collection with a common {@link CollectionChangeType type}.
 *
 * @param <E> The type of element in the changed collection
 */
public class CollectionChangeEvent<E> extends Causable.AbstractCausable {
	/**
	 * Represents a change to a single element in a collection
	 *
	 * @param <E> The type of the value
	 */
	public static class ElementChange<E> {
		/** The new value of the element */
		public final E newValue;
		/** The old value of the element, if the event is of type {@link CollectionChangeType#set} */
		public final E oldValue;
		/** The index of the element in the collection */
		public final int index;
		/** The movement operation this change is a part of */
		public final CollectionElementMove movement;

		/**
		 * @param value The new value of the element
		 * @param oldValue The old value of the element, if the event is of type {@link CollectionChangeType#set}
		 * @param index The index of the element in the collection
		 * @param movement The movement operation this change is a part of
		 */
		public ElementChange(E value, E oldValue, int index, CollectionElementMove movement) {
			if (index < 0)
				throw new IndexOutOfBoundsException("" + index);
			this.newValue = value;
			this.oldValue = oldValue;
			this.index = index;
			this.movement = movement;
		}

		@Override
		public String toString() {
			return new StringBuilder().append(index).append(':').append(oldValue).append('/').append(newValue).toString();
		}
	}
	/** The type of the changes that this event represents */
	public final CollectionChangeType type;

	/** The IDs of each element added, removed, or changed */
	public final List<ElementChange<E>> elements;

	/**
	 * @param aType The common type of the changes
	 * @param elements The changes, by element ID
	 * @param causes The causes of the event
	 */
	public CollectionChangeEvent(CollectionChangeType aType, List<ElementChange<E>> elements, Object... causes) {
		super(causes);
		type = aType;
		this.elements = Collections.unmodifiableList(elements);
	}

	/** @return A list of the new values of this change's {@link #elements} */
	public List<E> getValues() {
		return new AbstractList<E>() {
			@Override
			public int size() {
				return elements.size();
			}

			@Override
			public E get(int index) {
				return elements.get(index).newValue;
			}
		};
	}

	/** @return A list of the old values of this change's {@link #elements} */
	public List<E> getOldValues() {
		return new AbstractList<E>() {
			@Override
			public int size() {
				return elements.size();
			}

			@Override
			public E get(int index) {
				return elements.get(index).oldValue;
			}
		};
	}

	/**
	 * Differs from the {@link #elements} field in that if this change's {@link #type} is {@link CollectionChangeType#remove}, the element
	 * list is {@link #getElementsReversed() reversed}, since this is generally the order elements should be processed.
	 *
	 * @return The (possibly reversed) list of elements in this change
	 */
	public List<ElementChange<E>> getElements() {
		return type == CollectionChangeType.remove ? getElementsReversed() : elements;
	}

	/** @return a list of this change's {@link #elements}, ordered by descending index */
	public List<ElementChange<E>> getElementsReversed() {
		return new AbstractList<ElementChange<E>>() {
			@Override
			public int size() {
				return elements.size();
			}

			@Override
			public ElementChange<E> get(int index) {
				return elements.get(elements.size() - index - 1);
			}
		};
	}

	/**
	 * @param index The index in the collection to get the change for
	 * @return The change in this event to the given element, or null if no change to the given element is recorded in this event
	 */
	public ElementChange<E> getChangeFor(int index) {
		int minIdx = 0, maxIdx = elements.size() - 1;
		while (minIdx < maxIdx) {
			int changeIdx = (minIdx + maxIdx) / 2;
			if (index < elements.get(changeIdx).index)
				maxIdx = changeIdx - 1;
			else if (index > elements.get(changeIdx).index)
				minIdx = changeIdx + 1;
			else
				return elements.get(changeIdx);
		}
		if (minIdx == maxIdx && elements.get(minIdx).index == index)
			return elements.get(minIdx);
		else
			return null;
	}

	/** @return The indexes of this change's {@link #elements} */
	public int[] getIndexes() {
		int[] indexes = new int[elements.size()];
		for (int i = 0; i < indexes.length; i++)
			indexes[i] = elements.get(i).index;
		return indexes;
	}

	/** @return Whether this event represents an update, i.e. an event that's fired even though no value references have changed */
	public boolean isUpdate() {
		if (type != CollectionChangeType.set)
			return false;
		for (ElementChange<E> change : elements) {
			if (change.oldValue != change.newValue)
				return false;
		}
		return true;
	}

	@Override
	public String toString() {
		StringBuilder ret = new StringBuilder();
		switch (type) {
		case add:
			ret.append("added ");
			break;
		case remove:
			ret.append("removed ");
			break;
		case set:
			ret.append("set ");
			break;
		}
		ret.append(" (\n");
		for (ElementChange<E> elChange : elements) {
			ret.append("\t[").append(elChange.index).append("]: ");
			switch (type) {
			case add:
				ret.append(elChange.newValue);
				break;
			case remove:
				ret.append(elChange.newValue);
				break;
			case set:
				ret.append(elChange.oldValue).append("->").append(elChange.newValue);
				break;
			}
			ret.append('\n');
		}
		ret.append(')');
		return ret.toString();
	}
}
