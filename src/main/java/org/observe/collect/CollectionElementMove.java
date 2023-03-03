package org.observe.collect;

import org.qommons.CausalLock;
import org.qommons.Ternian;
import org.qommons.collect.ListenerList;

/**
 * <p>
 * Represents a movement operation of a single collection element to a different location in the collection.
 * </p>
 * <p>
 * A move object can only be used to move a single element once. A move is first reported in a {@link ObservableCollectionEvent collection
 * event}'s {@link ObservableCollectionEvent#getMovement() movement} field for a {@link CollectionChangeType#remove remove} event. The
 * presence of the movement field indicates that the remove operation is part of some kind of rearrangement operation that keeps track of
 * each element.
 * </p>
 * <p>
 * The code managing the rearrangement should use the {@link CollectionElementMove#moved()} method when the removed element is re-added (and
 * before the {@link CollectionChangeType#add add} event is fired). The add event will have the same move instance in its movement field.
 * </p>
 * <p>
 * In some bulk rearrangement operations, a remove operation may take place before the operation is certain which elements may be re-added
 * to the collection (e.g.
 * {@link org.qommons.collect.ValueStoredCollection#repair(org.qommons.collect.ValueStoredCollection.RepairListener)} set repair). In such
 * cases, the {@link #moveFinished()} method may be called without calling {@link #moved()}, signifying that the element was discarded
 * rather than moved.
 * </p>
 * <p>
 * Movement operations must be accomplished in a single {@link ObservableCollection#lock(boolean, Object) transaction} on a single thread.
 * </p>
 */
public class CollectionElementMove implements CausalLock.Cause {
	/** A listener to be notified when something happens to a movement operation */
	public interface MovementListener {
		/** @param movement The movement that is now {@link CollectionElementMove#isFinished() finished} */
		void moveComplete(CollectionElementMove movement);
	}

	private static class MovementListenerHolder {
		final MovementListener listener;
		/** True to be notified only for movement, false to be notified when the element is discarded, none to be notified for either */
		final Ternian forMovement;

		public MovementListenerHolder(MovementListener listener, Ternian forMovement) {
			this.listener = listener;
			this.forMovement = forMovement;
		}
	}

	private int hashCode;
	private ListenerList<MovementListenerHolder> theListeners;
	private boolean wasMoved;
	private boolean isFinished;

	/** Creates the move */
	public CollectionElementMove() {
	}

	/**
	 * @param listener The listener to be notified when a movement operation terminates in a re-addition
	 * @return This movement
	 */
	public CollectionElementMove onMove(MovementListener listener) {
		return listen(listener, Ternian.TRUE);
	}

	/**
	 * @param listener The listener to be notified when a movement operation terminates without a re-addition
	 * @return This movement
	 */
	public CollectionElementMove onDiscard(MovementListener listener) {
		return listen(listener, Ternian.FALSE);
	}

	/**
	 * @param listener The listener to be notified when a movement operation terminates with or without a re-addition
	 * @return This movement
	 */
	public CollectionElementMove onFinish(MovementListener listener) {
		return listen(listener, Ternian.NONE);
	}

	private CollectionElementMove listen(MovementListener listener, Ternian forMovement) {
		if (isFinished) {
			listener.moveComplete(this);
			return this;
		} else if (theListeners == null)
			theListeners = ListenerList.build().unsafe().build(); // This should only be accessed and fired on a single thread
		theListeners.add(new MovementListenerHolder(listener, forMovement), false);
		return this;
	}

	/** @return Whether this movement operation terminated in a re-addition */
	public boolean wasMoved() {
		if (!isFinished)
			throw new IllegalStateException("Movement is not finished");
		return wasMoved;
	}

	/** @return Whether this movement operation has finished, one way or another */
	public boolean isFinished() {
		return isFinished;
	}

	/** Signifies that the removed element has been re-added to the collection */
	public void moved() {
		if (isFinished)
			throw new IllegalStateException((wasMoved ? "Moved" : "Finished") + " twice?");
		wasMoved = true;
		fire(true);
	}

	/**
	 * Signifies that the movement operation has finished. This method is always safe to invoke, e.g. if #moved() has already been called.
	 */
	public void moveFinished() {
		if (isFinished)
			return;
		fire(false);
	}

	private void fire(boolean moved) {
		isFinished = true;
		if (theListeners != null)
			theListeners.forEach(//
				l -> {
					if (l.forMovement.withDefault(moved) == moved)
						l.listener.moveComplete(this);
				});
		theListeners = null;
	}

	@Override
	public final int hashCode() {
		if (hashCode == 0)
			hashCode = super.hashCode();
		return hashCode;
	}

	@Override
	public final boolean equals(Object obj) {
		return this == obj;
	}

	@Override
	public String toString() {
		return "move" + Integer.toHexString(hashCode());
	}
}
