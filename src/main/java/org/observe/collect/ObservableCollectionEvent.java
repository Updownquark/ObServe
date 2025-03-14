package org.observe.collect;

import java.util.Collection;

import org.observe.ObservableValueEvent;
import org.qommons.Causable;
import org.qommons.Transaction;
import org.qommons.collect.BetterList;
import org.qommons.collect.ElementId;

/**
 * An event representing a change to an {@link ObservableCollection}
 *
 * @param <E> The type of values in the collection
 */
public interface ObservableCollectionEvent<E> extends ObservableValueEvent<E> {
	/** @return The ID of the element that was changed */
	ElementId getElementId();

	/** @return The index of the element in the collection */
	int getIndex();

	/** @return The type of the change */
	CollectionChangeType getType();

	/**
	 * @return If this event represents either the removal of an element in preparation for a move, or the re-addition of an element that
	 *         was just removed in the same move operation, this will be an identifier that links the two operations. A movement operation
	 *         happens in a single transaction.
	 */
	CollectionElementMove getMovement();

	/** @return true for type {@link CollectionChangeType#remove}, false otherwise */
	default boolean isFinal() {
		return getType() == CollectionChangeType.remove;
	}

	@Override
	default boolean isUpdate() {
		return getType() == CollectionChangeType.set && ObservableValueEvent.super.isUpdate();
	}

	/**
	 * @param <E> The type of the collection event
	 *
	 * @param elementId The ID of the element that was added, removed, or changed
	 * @param index The index of the element that was added, removed, or changed
	 * @param type The type of the change (add, remove, or set)
	 * @param oldValue The value that was previously present in the element (or null if the element was just added)
	 * @param newValue The value that is now present in the element (or the value that was just removed)
	 * @param causes The causes of the change
	 * @return The collection event
	 */
	static <E> ObservableCollectionEvent<E> createCollectionEvent(ElementId elementId, int index, CollectionChangeType type, E oldValue,
		E newValue, Object... causes) {
		return new DefaultObservableCollectionEvent<>(elementId, index, type, oldValue, newValue, causes);
	}

	/**
	 * @param <E> The type of the collection event
	 *
	 * @param elementId The ID of the element that was added, removed, or changed
	 * @param index The index of the element that was added, removed, or changed
	 * @param type The type of the change (add, remove, or set)
	 * @param oldValue The value that was previously present in the element (or null if the element was just added)
	 * @param newValue The value that is now present in the element (or the value that was just removed)
	 * @param causes The causes of the change
	 * @return The collection event
	 */
	static <E> ObservableCollectionEvent<E> createCollectionEvent(ElementId elementId, int index, CollectionChangeType type, E oldValue,
		E newValue, Collection<?> causes) {
		return new DefaultObservableCollectionEvent<>(elementId, index, type, oldValue, newValue, causes);
	}

	/*
	 * The following methods are helpful performance improvements, allowing the creation of smaller, simpler derived objects that use
	 * their parents for the heavy, complex Causable functionality.
	 *
	 * Use of these methods does mean that the derived elements don't report being finished until their source does,
	 * but this is necessary for performance.
	 */

	/**
	 * @param element The ID of the derived element
	 * @param index The index of the derived element
	 * @return A collection event with all of this event's information, except the given element ID and index
	 */
	ObservableCollectionEvent<E> derive(ElementId element, int index);

	/**
	 * @param <E2> The type of the derived event
	 * @param element The ID of the derived element
	 * @param index The index of the derived element
	 * @param oldValue The previous value in the derived element
	 * @param newValue The new value in the derived element
	 * @return A collection event with all of this event's information, except the given element ID, index, and values
	 */
	<E2> ObservableCollectionEvent<E2> derive(ElementId element, int index, E2 oldValue, E2 newValue);

	/**
	 * Default implementation returned from
	 * {@link ObservableCollectionEvent#createCollectionEvent(ElementId, int, CollectionChangeType, Object, Object, Collection)}
	 *
	 * @param <E> The type of the event
	 */
	class DefaultObservableCollectionEvent<E> extends ObservableValueEvent.DefaultObservableValueEvent<E>
	implements ObservableCollectionEvent<E> {
		private final ElementId theElementId;
		private final int theIndex;
		private final CollectionChangeType theType;
		private final CollectionElementMove theMovement;

		/**
		 * @param elementId The ID of the element that was changed
		 * @param index The index of the element in the collection
		 * @param type The type of the change
		 * @param oldValue The old value for the element ({@link CollectionChangeType#set}-type only)
		 * @param newValue The new value for the element
		 * @param causes The causes of the change
		 */
		public DefaultObservableCollectionEvent(ElementId elementId, int index, CollectionChangeType type, E oldValue, E newValue,
			Object... causes) {
			super(type == CollectionChangeType.add, oldValue, newValue, causes);
			theElementId = elementId;
			theIndex = index;
			theType = type;
			// A movement can also be specified as one of the event's direct causes
			CollectionElementMove movement = null;
			for (Object cause : causes) {
				if (cause instanceof CollectionElementMove) {
					movement = (CollectionElementMove) cause;
					break;
				}
			}
			theMovement = movement;
			checkIndex(index);
		}

		/**
		 * Checks the index for this type and throws an exception if invalid
		 *
		 * @param index The index passed to the constructor
		 */
		protected void checkIndex(int index) {
			if (index < 0)
				throw new IndexOutOfBoundsException("" + index);
		}

		/**
		 * @param elementId The ID of the element that was changed
		 * @param index The index of the element in the collection
		 * @param type The type of the change
		 * @param oldValue The old value for the element ({@link CollectionChangeType#set}-type only)
		 * @param newValue The new value for the element
		 * @param causes The causes of the change
		 */
		public DefaultObservableCollectionEvent(ElementId elementId, int index, CollectionChangeType type, E oldValue, E newValue,
			Collection<?> causes) {
			this(elementId, index, type, oldValue, newValue, causes.toArray());
		}

		@Override
		public ElementId getElementId() {
			return theElementId;
		}

		@Override
		public int getIndex() {
			return theIndex;
		}

		@Override
		public CollectionChangeType getType() {
			return theType;
		}

		@Override
		public CollectionElementMove getMovement() {
			return theMovement;
		}

		@Override
		public ObservableCollectionEvent<E> derive(ElementId element, int index) {
			if (element.equals(theElementId))
				return this;
			return new ElementChangedCollectionEvent<>(this, element, index);
		}

		@Override
		public <E2> ObservableCollectionEvent<E2> derive(ElementId element, int index, E2 oldValue, E2 newValue) {
			if (element.equals(theElementId) && getOldValue() == oldValue && getNewValue() == newValue)
				return (ObservableCollectionEvent<E2>) this;
			return new ValueChangedCollectionEvent<>(this, element, index, oldValue, newValue);
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			str.append('[').append(theIndex).append(']');
			switch (theType) {
			case add:
				str.append("+:").append(getNewValue());
				break;
			case remove:
				str.append("-:").append(getOldValue());
				break;
			case set:
				str.append(':').append(getOldValue()).append("->").append(getNewValue());
				break;
			}
			if (theMovement != null)
				str.append("(move)");
			return str.toString();
		}
	}

	/**
	 * Abstract derived implementation for {@link #derive(ElementId, int)} and {@link #derive(ElementId, int, Object, Object)}
	 *
	 * @param <T> The type of the derived collection
	 */
	public abstract class DerivedObservableCollectionEvent<T> implements ObservableCollectionEvent<T> {
		private final ObservableCollectionEvent<?> theSource;
		private final ElementId theId;
		private final int theIndex;

		/**
		 * @param source The source collection event that this event is derived from
		 * @param id The ID of the derived element
		 * @param index The index of the derived element
		 */
		protected DerivedObservableCollectionEvent(ObservableCollectionEvent<?> source, ElementId id, int index) {
			theSource = source;
			theId = id;
			theIndex = index;
		}

		/** @return The source collection event that this event is derived from */
		protected ObservableCollectionEvent<?> getSource() {
			return theSource;
		}

		@Override
		public boolean isInitial() {
			return theSource.isInitial();
		}

		@Override
		public BetterList<Object> getCauses() {
			return theSource.getCauses();
		}

		@Override
		public Causable getRootCausable() {
			return theSource.getRootCausable();
		}

		@Override
		public Effect onFinish(CausableKey key) {
			return theSource.onFinish(key);
		}

		@Override
		public boolean isFinished() {
			return theSource.isFinished();
		}

		@Override
		public boolean isTerminated() {
			return theSource.isTerminated();
		}

		@Override
		public Transaction use() {
			return Transaction.NONE; // The source event is already in use
		}

		@Override
		public ElementId getElementId() {
			return theId;
		}

		@Override
		public int getIndex() {
			return theIndex;
		}

		@Override
		public CollectionChangeType getType() {
			return theSource.getType();
		}

		@Override
		public CollectionElementMove getMovement() {
			return theSource.getMovement();
		}

		@Override
		public <E2> ObservableCollectionEvent<E2> derive(ElementId element, int index, E2 oldValue, E2 newValue) {
			if (element.equals(getElementId()) && getOldValue() == oldValue && getNewValue() == newValue)
				return (ObservableCollectionEvent<E2>) this;
			return theSource.derive(element, index, oldValue, newValue);
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			str.append('[').append(theIndex).append(']');
			switch (getType()) {
			case add:
				str.append("+:").append(getNewValue());
				break;
			case remove:
				str.append("-:").append(getOldValue());
				break;
			case set:
				str.append(':').append(getOldValue()).append("->").append(getNewValue());
				break;
			}
			if (getMovement() != null)
				str.append("(move)");
			return str.toString();
		}
	}

	/**
	 * Default derived implementation for {@link #derive(ElementId, int)}
	 *
	 * @param <T> The type of both the source and derived collections
	 */
	public class ElementChangedCollectionEvent<T> extends DerivedObservableCollectionEvent<T> {
		/**
		 * @param source The source collection event that this event is derived from
		 * @param id The ID of the derived element
		 * @param index The index of the derived element
		 */
		public ElementChangedCollectionEvent(ObservableCollectionEvent<T> source, ElementId id, int index) {
			super(source, id, index);
		}

		@Override
		protected ObservableCollectionEvent<T> getSource() {
			return (ObservableCollectionEvent<T>) super.getSource();
		}

		@Override
		public T getOldValue() {
			return getSource().getOldValue();
		}

		@Override
		public T getNewValue() {
			return getSource().getNewValue();
		}

		@Override
		public ObservableCollectionEvent<T> derive(ElementId element, int index) {
			if (element.equals(getElementId()))
				return this;
			return getSource().derive(element, index);
		}
	}

	/**
	 * Default derived implementation for {@link #derive(ElementId, int, Object, Object)}
	 *
	 * @param <T> The type of the derived collection
	 */
	public class ValueChangedCollectionEvent<T> extends DerivedObservableCollectionEvent<T> {
		private final T theOldValue;
		private final T theNewValue;

		/**
		 * @param source The source collection event that this event is derived from
		 * @param id The ID of the derived element
		 * @param index The index of the derived element
		 * @param oldValue The previous value in the derived element
		 * @param newValue The new value in the derived element
		 */
		public ValueChangedCollectionEvent(ObservableCollectionEvent<?> source, ElementId id, int index, T oldValue, T newValue) {
			super(source, id, index);
			theOldValue = oldValue;
			theNewValue = newValue;
		}

		@Override
		public T getOldValue() {
			return theOldValue;
		}

		@Override
		public T getNewValue() {
			return theNewValue;
		}

		@Override
		public ObservableCollectionEvent<T> derive(ElementId element, int index) {
			if (element.equals(getElementId()))
				return this;
			return getSource().derive(element, index, theOldValue, theNewValue);
		}
	}
}
