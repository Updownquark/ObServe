package org.observe.util.swing;

import java.awt.EventQueue;
import java.beans.Transient;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EventListener;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.swing.DefaultListSelectionModel;
import javax.swing.ListSelectionModel;
import javax.swing.event.EventListenerList;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.observe.Equivalence;
import org.observe.Observable;
import org.observe.collect.CollectionChangeEvent;
import org.observe.collect.CollectionElementMove;
import org.observe.collect.ObservableCollection;
import org.qommons.Causable;
import org.qommons.LambdaUtils;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.collect.BetterBitSet;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.CollectionUtils;

/**
 * A copy of {@link DefaultListSelectionModel} that is able to efficiently synchronize selection to an {@link ObservableCollection}.
 *
 * @param <E> The type of elements in the list model that this selection model manages the selection of
 */
@SuppressWarnings("serial") // Same-version serialization only
public class ObservableListSelectionModel<E> implements ListSelectionModel, Cloneable, Serializable {
	private static final int MIN = -1;
	private static final int MAX = Integer.MAX_VALUE;

	private final ObservableListModel<E> theListModel;
	private int selectionMode = MULTIPLE_INTERVAL_SELECTION;
	private int minIndex = MAX;
	private int maxIndex = MIN;
	private int anchorIndex = -1;
	private int leadIndex = -1;
	private int firstAdjustedIndex = MAX;
	private int lastAdjustedIndex = MIN;
	private boolean isAdjusting = false;

	private int firstChangedIndex = MAX;
	private int lastChangedIndex = MIN;

	private boolean addSelectionOnInsert = false;

	private BetterBitSet value = new BetterBitSet(32);
	/**
	 * The list of listeners.
	 */
	protected EventListenerList listenerList = new EventListenerList();

	/**
	 * Whether or not the lead anchor notification is enabled.
	 */
	protected boolean leadAnchorNotificationEnabled = true;

	/** @param listModel The list model to manage selection of */
	public ObservableListSelectionModel(ObservableListModel<E> listModel) {
		theListModel = listModel;
	}

	/** @return The list model that this selection model manages selection of */
	public ObservableListModel<E> getListModel() {
		return theListModel;
	}

	/** @return All values in the {@link #getListModel() list model} that are selected in this selection model */
	public List<E> getSelectedValues() {
		if (isSelectionEmpty())
			return Collections.emptyList();
		else if (minIndex == maxIndex)
			return Collections.singletonList(theListModel.getElementAt(minIndex));
		List<E> values = new ArrayList<>();
		for (int i = minIndex; i >= 0; i = value.nextSetBit(i + 1))
			values.add(theListModel.getElementAt(i));
		return values;
	}

	/** {@inheritDoc} */
	@Override
	public int getMinSelectionIndex() {
		return isSelectionEmpty() ? -1 : minIndex;
	}

	/** {@inheritDoc} */
	@Override
	public int getMaxSelectionIndex() {
		return maxIndex;
	}

	/** {@inheritDoc} */
	@Override
	public boolean getValueIsAdjusting() {
		return isAdjusting;
	}

	/** {@inheritDoc} */
	@Override
	public int getSelectionMode() {
		return selectionMode;
	}

	/**
	 * {@inheritDoc}
	 *
	 * @throws IllegalArgumentException {@inheritDoc}
	 */
	@Override
	public void setSelectionMode(int selectionMode) {
		int oldMode = this.selectionMode;
		switch (selectionMode) {
		case SINGLE_SELECTION:
		case SINGLE_INTERVAL_SELECTION:
		case MULTIPLE_INTERVAL_SELECTION:
			this.selectionMode = selectionMode;
			break;
		default:
			throw new IllegalArgumentException("invalid selectionMode");
		}

		/*
		This code will only be executed when selection needs to be updated on
		changing selection mode. It will happen only if selection mode is changed
		from MULTIPLE_INTERVAL to SINGLE_INTERVAL or SINGLE or from
		SINGLE_INTERVAL to SINGLE
		 */
		if (oldMode > this.selectionMode) {
			if (this.selectionMode == SINGLE_SELECTION) {
				if (!isSelectionEmpty()) {
					setSelectionInterval(minIndex, minIndex);
				}
			} else if (this.selectionMode == SINGLE_INTERVAL_SELECTION) {
				if (!isSelectionEmpty()) {
					int selectionEndindex = minIndex;
					while (value.get(selectionEndindex + 1)) {
						selectionEndindex++;
					}
					setSelectionInterval(minIndex, selectionEndindex);
				}
			}
		}
	}

	/** @return Whether this model selects inserted elements when they are added at a currently selected index */
	public boolean isAddSelectionOnInsert() {
		return addSelectionOnInsert;
	}

	/**
	 * @param addSelectionOnInsert Whether this model should select inserted elements when they are added at a currently selected index
	 * @return This model
	 */
	public ObservableListSelectionModel<E> setAddSelectionOnInsert(boolean addSelectionOnInsert) {
		this.addSelectionOnInsert = addSelectionOnInsert;
		return this;
	}

	/** {@inheritDoc} */
	@Override
	public boolean isSelectedIndex(int index) {
		return ((index < minIndex) || (index > maxIndex)) ? false : value.get(index);
	}

	/** {@inheritDoc} */
	@Override
	public boolean isSelectionEmpty() {
		return (minIndex > maxIndex);
	}

	/** {@inheritDoc} */
	@Override
	public void addListSelectionListener(ListSelectionListener l) {
		listenerList.add(ListSelectionListener.class, l);
	}

	/** {@inheritDoc} */
	@Override
	public void removeListSelectionListener(ListSelectionListener l) {
		listenerList.remove(ListSelectionListener.class, l);
	}

	/**
	 * <p>
	 * Creates a dynamic link between this selection model, its {@link #getListModel() data model}, and the given selection collection.
	 * </p>
	 * <p>
	 * When the data model or the user's selection changes in a way that affects selection (e.g. a selected value is removed or moved), the
	 * corresponding change will be made in the given collection. When values are removed externally in the given collection, those values
	 * will be de-selected in this model. When updates come from the data model, those will be propagated as updates in the selection
	 * collection.
	 * </p>
	 * <p>
	 * When values are added to the collection, a search will be made for an unselected value in the model and that value will be selected.
	 * If no such value can be found, that value will be removed from the collection. A value will first be sought in a range that is
	 * consistent with the location in which the new value is added. If the value cannot be found there but is found elsewhere, it will be
	 * moved unless the collection is {@link org.qommons.collect.BetterList#isContentControlled() content controlled}.
	 * </p>
	 * <p>
	 * This method works best with a collection with no restrictions--no internally-enforced ordering or filtering. This method may be used
	 * with ordered collections (if they advertise via {@link org.qommons.collect.BetterList#isContentControlled()}), but performance may be
	 * degraded, especially if many items are selected at once. This link will break, throwing exceptions and becoming out-of-sync if it is
	 * prevented from managing the collection by adding to or removing from it.
	 * </p>
	 *
	 * @param selection The collection to populate with this model's selection
	 * @param until The observable that will terminate the synchronization when it fires
	 */
	public void synchronize(ObservableCollection<E> selection, Observable<?> until) {
		/*
		 * One of the things this code does it propagate updates from the data model to the selection collection and vice versa.
		 * However, this is problematic because this has the potential to create an infinite loop.
		 * Compounding this is the fact that the loop may not be detectable with a simple boolean because the safe collections
		 * may batch incoming changes and then flush them with an invokeLater.
		 */
		if (until == null)
			until = Observable.empty();
		selection.changes().act(evt -> System.out.println(selection.size() + " selected"));
		ObservableCollection<E> safeSelection = selection.safe(ThreadConstraint.EDT, until);
		class ListSynchronization implements ListDataListener, ListSelectionListener, Consumer<CollectionChangeEvent<E>> {
			BetterBitSet previousValue = new BetterBitSet().replaceWith(value);
			private final Map<CollectionElementMove, CollectionElementMove> movements = new HashMap<>();
			private boolean isCollectionChanging;
			private boolean isModelChanging;
			private int isOutOfSync;

			@Override
			public void intervalRemoved(ListDataEvent e) {
				/* Handle elements removed from the model.
				 * Biggest job here is to catch selected elements which are part of a move operation and record this
				 * for when they're re-added so we can select them again.
				 */
				if (isOutOfSync > 0) {
					EventQueue.invokeLater(() -> intervalRemoved(e));
					return;
				}
				CollectionChangeEvent<?> cause = ((ObservableListModel.CausableListEvent) e).getCause();
				Transaction selectionLock = null;
				isModelChanging = true;
				try {
					int start = previousValue.nextSetBit(e.getIndex0());
					if (start < 0)
						return;
					int end = previousValue.previousSetBit(e.getIndex1());
					if (end < 0)
						return;
					for (int i = end; i >= start; i--) {
						if (!previousValue.get(i))
							continue;
						// else This index was selected before it was removed
						if (selectionLock == null)
							selectionLock = safeSelection.lock(true, cause);
						CollectionChangeEvent.ElementChange<?> change = cause.getChangeFor(i);
						if (change.movement != null) {
							CollectionElementMove selMove = new CollectionElementMove();
							movements.put(change.movement.onDiscard(m -> {
								CollectionElementMove selMove2 = movements.remove(m);
								if (selMove2 != null)
									selMove2.moveFinished();
							}), selMove);
							try (Transaction moveT = safeSelection.lock(true, selMove)) {
								int selectionIndex = previousValue.countBitsSetBetween(0, i);
								if (selectionIndex >= previousValue.cardinality()) // TODO Debugging, remove
									previousValue.countBitsSetBetween(0, i);
								safeSelection.remove(selectionIndex);
							}
						}
					}
				} finally {
					if (selectionLock != null)
						selectionLock.close();
					previousValue.replaceWith(value);
					isModelChanging = false;
				}
			}

			@Override
			public void intervalAdded(ListDataEvent e) {
				/* Handle elements added from the model.
				 * Biggest job here is to catch elements which are part of a move operation and re-select them
				 */
				if (isOutOfSync > 0) {
					EventQueue.invokeLater(() -> intervalAdded(e));
					return;
				}
				CollectionChangeEvent<?> cause = ((ObservableListModel.CausableListEvent) e).getCause();
				Transaction selectionLock = null;
				isModelChanging = true;
				try {
					CollectionElement<E> lastSelectionAdded = null;
					boolean selectionInOrder = !selection.isContentControlled();
					for (int i = e.getIndex0(); i <= e.getIndex1(); i++) {
						CollectionChangeEvent.ElementChange<?> change = cause.getChangeFor(i);
						CollectionElementMove selMove = change.movement == null ? null : movements.remove(change.movement);
						if (selMove != null) {
							if (!value.get(i)) {
								if (selectionLock == null)
									selectionLock = safeSelection.lock(true, cause);
								addSelectionInterval(i, i);
								try (Transaction moveT = safeSelection.lock(true, selMove)) {
									if (!selectionInOrder)
										safeSelection.add(getListModel().getElementAt(i));
									else if (lastSelectionAdded == null) {
										int selectionAddIndex = previousValue.countBitsSetBetween(0, e.getIndex0());
										lastSelectionAdded = safeSelection.addElement(selectionAddIndex, getListModel().getElementAt(i));
									} else
										lastSelectionAdded = safeSelection.addElement(getListModel().getElementAt(i),
											lastSelectionAdded.getElementId(), null, true);
								}
							}
						}
					}
				} finally {
					if (selectionLock != null)
						selectionLock.close();
					// Too complicated trying to insert the new indices and the model has already done it
					previousValue.replaceWith(value);
					isModelChanging = false;
					if (isAddSelectionOnInsert()) {
						// Indexes besides the movements may have been added, and the selection event was ignored
						syncSelectionWithSledgehammer(e);
					}
				}
			}

			@Override
			public void contentsChanged(ListDataEvent e) {
				if (isOutOfSync > 0) {
					EventQueue.invokeLater(() -> contentsChanged(e));
					return;
				}
				CollectionChangeEvent<?> cause = ((ObservableListModel.CausableListEvent) e).getCause();
				Transaction selectionLock = null;
				isModelChanging = true;
				try {
					int start = previousValue.nextSetBit(e.getIndex0());
					if (start < 0)
						return;
					int end = previousValue.previousSetBit(e.getIndex1());
					if (end < 0)
						return;
					for (int i = start; i <= start; i++) {
						if (!previousValue.get(i))
							continue;
						// else This index was selected before it was removed
						if (selectionLock == null)
							selectionLock = safeSelection.lock(true, cause);
						int selectionIndex = previousValue.countBitsSetBetween(0, i);
						if (selectionIndex >= previousValue.cardinality()) // TODO Debugging, remove
							previousValue.countBitsSetBetween(0, i);
						safeSelection.set(selectionIndex, getListModel().getElementAt(i));
					}
				} finally {
					if (selectionLock != null)
						selectionLock.close();
					isModelChanging = false;
				}
			}

			@Override
			public void valueChanged(ListSelectionEvent e) {
				if (isCollectionChanging || getListModel().isEventing() || getValueIsAdjusting())
					return;
				// User selection
				userSelectionChanged(e);
			}

			private void userSelectionChanged(ListSelectionEvent e) {
				if (isOutOfSync > 0) {
					EventQueue.invokeLater(() -> userSelectionChanged(e));
					return;
				}
				Transaction selectionLock = null;
				if (!selection.isContentControlled()) {
					// In this case we can track things better and keep the selection in the proper order.
					// Therefore we have the ability to be smarter and faster here.
					BetterBitSet currentValue = value;
					int nextPrev = previousValue.nextSetBit(e.getFirstIndex());
					if (nextPrev < 0)
						nextPrev = e.getLastIndex() + 1;
					int nextCurr = currentValue.nextSetBit(e.getFirstIndex());
					if (nextCurr < 0)
						nextCurr = e.getLastIndex() + 1;
					CollectionElement<E> lastSelection = null;
					ObservableListModel<E> listModel = getListModel();
					isModelChanging = true;
					try {
						while (nextPrev <= e.getLastIndex() || nextCurr <= e.getLastIndex()) {
							if (nextPrev == nextCurr) { // Both selected, no change, move on
								nextPrev = previousValue.nextSetBit(nextPrev + 1);
								if (nextPrev < 0)
									nextPrev = e.getLastIndex() + 1;
								nextCurr = currentValue.nextSetBit(nextCurr + 1);
								if (nextCurr < 0)
									nextCurr = e.getLastIndex() + 1;
								if (lastSelection != null)
									lastSelection = safeSelection.getAdjacentElement(lastSelection.getElementId(), true);
							} else if (nextPrev < nextCurr) { // Value de-selected
								if (selectionLock == null)
									selectionLock = safeSelection.lock(true, e);
								CollectionElement<E> toRemove;
								if (lastSelection != null)
									toRemove = safeSelection.getAdjacentElement(lastSelection.getElementId(), true);
								else {
									int selectionIndex = previousValue.countBitsSetBetween(0, nextPrev);
									toRemove = safeSelection.getElement(selectionIndex);
									lastSelection = safeSelection.getAdjacentElement(toRemove.getElementId(), false);
								}
								safeSelection.mutableElement(toRemove.getElementId()).remove();
								previousValue.clear(nextPrev);
								nextPrev = previousValue.nextSetBit(nextPrev + 1);
								if (nextPrev < 0)
									nextPrev = e.getLastIndex() + 1;
							} else { // Value selected
								if (selectionLock == null)
									selectionLock = safeSelection.lock(true, e);
								E newSelected = listModel.getElementAt(nextCurr);
								if (lastSelection != null)
									lastSelection = safeSelection.addElement(newSelected, lastSelection.getElementId(), null, true);
								else {
									int selectionIndex = previousValue.countBitsSetBetween(0, nextPrev);
									if (selectionIndex == 0)
										lastSelection = safeSelection.addElement(newSelected, null, null, true);
									else
										lastSelection = safeSelection.addElement(newSelected,
											safeSelection.getElement(selectionIndex - 1).getElementId(), null, true);
								}
								previousValue.set(nextCurr);
								nextCurr = currentValue.nextSetBit(nextCurr + 1);
								if (nextCurr < 0)
									nextCurr = e.getLastIndex() + 1;
							}
						}
					} finally {
						if (selectionLock != null)
							selectionLock.close();
						isModelChanging = false;
					}
				} else {
					// We may not be able to keep the selection in the proper order, so the best we can do is a sledgehammer
					syncSelectionWithSledgehammer(e);
					previousValue.replaceWith(value);
				}
			}

			/**
			 * This method resets the selection collection to what's in the model.
			 *
			 * It's very expensive when the selection is large, so we only do this when we have no other choice.
			 */
			private void syncSelectionWithSledgehammer(Object cause) {
				Equivalence<? super E> equivalence = selection.equivalence();
				if (!selection.isContentControlled() && safeSelection.size() == value.cardinality()) {
					// Let's see if we actually have to do anything first, since this case is much cheaper
					ObservableListModel<E> listModel = getListModel();
					Iterator<E> selectionIter = safeSelection.iterator();
					boolean equal = true;
					for (int i = getMinSelectionIndex(); equal && i >= 0; i = value.nextSetBit(i + 1))
						equal = equivalence.elementEquals(selectionIter.next(), listModel.getElementAt(i));
					if (equal)
						return;
				}
				List<E> selValues = getSelectedValues();
				isModelChanging = true;
				try (Transaction t = safeSelection.lock(true, cause)) {
					CollectionUtils.SimpleAdjustment<E, E, RuntimeException> adjustment;
					adjustment = CollectionUtils.synchronize(safeSelection, selValues, (v1, v2) -> equivalence.elementEquals(v1, v2))//
						.simple(LambdaUtils.identity())//
						.commonUses(true, false);
					if (safeSelection.isContentControlled())
						adjustment.addLast();
					adjustment.adjust();
				} finally {
					isModelChanging = false;
				}
			}

			@Override
			public void accept(CollectionChangeEvent<E> event) {
				// Even though the safe collections sometimes batch incoming changes and flush them on an invokeLater,
				// we don't have to worry about that here. If the changes are coming from the model, the model is operating
				// on the safe collection, and the contract says that the events have to be fired before an operation returns.
				if (isModelChanging)
					return;
				// Something besides this model has triggered a change in the selection. See if we can update the selection to reflect it.
				ObservableSwingUtils.flushEQCache();
				BetterBitSet current = value;
				isCollectionChanging = true;
				boolean needsSledgehammerSync = false;
				try {
					switch (event.type) {
					case add:
						for (CollectionChangeEvent.ElementChange<E> change : event.elements)
							needsSledgehammerSync = addToSelection(change, current);
						break;
					case remove:
						for (CollectionChangeEvent.ElementChange<E> change : event.getElements()) {
							int modelIdx = current.indexOfNthSetBit(change.index);
							removeFromSelection(modelIdx);
						}
						break;
					case set:
						Equivalence<? super E> equivalence = selection.equivalence();
						for (CollectionChangeEvent.ElementChange<E> change : event.elements) {
							if (equivalence.elementEquals(change.oldValue, change.newValue))
								continue;
							int modelIdx = current.indexOfNthSetBit(change.index);
							removeFromSelection(modelIdx);
							needsSledgehammerSync = addToSelection(change, current);
						}
						break;
					}
					if (needsSledgehammerSync) {
						isOutOfSync++;
						EventQueue.invokeLater(() -> {
							syncSelectionWithSledgehammer(Causable.broken(event));
							isOutOfSync--;
						});
					}
				} finally {
					isCollectionChanging = false;
				}
			}

			private boolean addToSelection(CollectionChangeEvent.ElementChange<E> change, BetterBitSet current) {
				ObservableListModel<E> listModel = getListModel();
				Equivalence<? super E> equivalence = selection.equivalence();
				int minModelIdx = change.index == 0 ? 0 : current.indexOfNthSetBit(change.index - 1);
				int maxModelIdx = current.nextSetBit(minModelIdx + 1);
				if (maxModelIdx < 0)
					maxModelIdx = listModel.getSize();
				int found = -1;
				for (int i = minModelIdx; found < 0 && i < maxModelIdx; i++) {
					if (equivalence.elementEquals(change.newValue, listModel.getElementAt(i)))
						found = i;
				}
				if (found >= 0) {
					addSelectionInterval(found, found);
					previousValue.set(found);
					return false;
				} else {
					// Not found in the region where we'd like it. Seek elsewhere
					// TODO How to do movement.
					for (int i = 0; found < 0 && i < minModelIdx; i++) {
						if (equivalence.elementEquals(change.newValue, listModel.getElementAt(i)))
							found = i;
					}
					for (int i = maxModelIdx; found < 0 && i < listModel.getSize(); i++) {
						if (equivalence.elementEquals(change.newValue, listModel.getElementAt(i)))
							found = i;
					}
					if (found >= 0) {
						// Found it.
						addSelectionInterval(found, found);
						previousValue.set(found);
						// If we can't control the locations, then nothing to do and it's as good as it can be
						// Otherwise we need to move it, but we can't do it directly as it's illegal to modify a collection
						// in response to a change event on that collection
						return !selection.isContentControlled();
					} else {
						// Not found, so this is an illegal value. We need to remove it, but we can't do it directly
						// as it's illegal to modify a collection in response to a change event on that collection
						return !selection.isContentControlled();
					}
				}
			}

			private void removeFromSelection(int modelIndex) {
				removeSelectionInterval(modelIndex, modelIndex);
				previousValue.clear(modelIndex);
			}
		}
		ListSynchronization syncListener = new ListSynchronization();
		try (Transaction t = safeSelection.lock(true, null)) {
			syncListener.syncSelectionWithSledgehammer(null);
			theListModel.addListDataListener(syncListener);
			addListSelectionListener(syncListener);
			until.take(1).act(__ -> {
				removeListSelectionListener(syncListener);
				theListModel.removeListDataListener(syncListener);
			});
			safeSelection.changes().takeUntil(until).act(syncListener);
		}
	}

	/**
	 * Returns an array of all the list selection listeners registered on this <code>DefaultListSelectionModel</code>.
	 *
	 * @return all of this model's <code>ListSelectionListener</code>s or an empty array if no list selection listeners are currently
	 *         registered
	 *
	 * @see #addListSelectionListener
	 * @see #removeListSelectionListener
	 *
	 * @since 1.4
	 */
	public ListSelectionListener[] getListSelectionListeners() {
		return listenerList.getListeners(ListSelectionListener.class);
	}

	/**
	 * Notifies listeners that we have ended a series of adjustments.
	 *
	 * @param isAdjusting2 true if this is the final change in a series of adjustments
	 */
	protected void fireValueChanged(boolean isAdjusting2) {
		if (lastChangedIndex == MIN) {
			return;
		}
		/* Change the values before sending the event to the
		 * listeners in case the event causes a listener to make
		 * another change to the selection.
		 */
		int oldFirstChangedIndex = firstChangedIndex;
		int oldLastChangedIndex = lastChangedIndex;
		firstChangedIndex = MAX;
		lastChangedIndex = MIN;
		fireValueChanged(oldFirstChangedIndex, oldLastChangedIndex, isAdjusting2);
	}

	/**
	 * Notifies <code>ListSelectionListeners</code> that the value of the selection, in the closed interval <code>firstIndex</code>,
	 * <code>lastIndex</code>, has changed.
	 *
	 * @param firstIndex the first index in the interval
	 * @param lastIndex the last index in the interval
	 */
	protected void fireValueChanged(int firstIndex, int lastIndex) {
		fireValueChanged(firstIndex, lastIndex, getValueIsAdjusting());
	}

	/**
	 * @param firstIndex the first index in the interval
	 * @param lastIndex the last index in the interval
	 * @param isAdjusting2 true if this is the final change in a series of adjustments
	 * @see EventListenerList
	 */
	protected void fireValueChanged(int firstIndex, int lastIndex, boolean isAdjusting2) {
		Object[] listeners = listenerList.getListenerList();
		ListSelectionEvent e = null;

		for (int i = listeners.length - 2; i >= 0; i -= 2) {
			if (listeners[i] == ListSelectionListener.class) {
				if (e == null) {
					e = new ListSelectionEvent(this, firstIndex, lastIndex, isAdjusting2);
				}
				((ListSelectionListener) listeners[i + 1]).valueChanged(e);
			}
		}
	}

	private void fireValueChanged() {
		if (lastAdjustedIndex == MIN) {
			return;
		}
		/* If getValueAdjusting() is true, (eg. during a drag opereration)
		 * record the bounds of the changes so that, when the drag finishes (and
		 * setValueAdjusting(false) is called) we can post a single event
		 * with bounds covering all of these individual adjustments.
		 */
		if (getValueIsAdjusting()) {
			firstChangedIndex = Math.min(firstChangedIndex, firstAdjustedIndex);
			lastChangedIndex = Math.max(lastChangedIndex, lastAdjustedIndex);
		}
		/* Change the values before sending the event to the
		 * listeners in case the event causes a listener to make
		 * another change to the selection.
		 */
		int oldFirstAdjustedIndex = firstAdjustedIndex;
		int oldLastAdjustedIndex = lastAdjustedIndex;
		firstAdjustedIndex = MAX;
		lastAdjustedIndex = MIN;

		fireValueChanged(oldFirstAdjustedIndex, oldLastAdjustedIndex);
	}

	/**
	 * Returns an array of all the objects currently registered as <code><em>Foo</em>Listener</code>s upon this model.
	 * <code><em>Foo</em>Listener</code>s are registered using the <code>add<em>Foo</em>Listener</code> method.
	 * <p>
	 * You can specify the <code>listenerType</code> argument with a class literal, such as <code><em>Foo</em>Listener.class</code>. For
	 * example, you can query a <code>DefaultListSelectionModel</code> instance <code>m</code> for its list selection listeners with the
	 * following code:
	 *
	 * <pre>
	 * ListSelectionListener[] lsls = (ListSelectionListener[]) (m.getListeners(ListSelectionListener.class));
	 * </pre>
	 *
	 * If no such listeners exist, this method returns an empty array.
	 *
	 * @param <T> the type of {@code EventListener} class being requested
	 * @param listenerType the type of listeners requested; this parameter should specify an interface that descends from
	 *        <code>java.util.EventListener</code>
	 * @return an array of all objects registered as <code><em>Foo</em>Listener</code>s on this model, or an empty array if no such
	 *         listeners have been added
	 * @exception ClassCastException if <code>listenerType</code> doesn't specify a class or interface that implements
	 *            <code>java.util.EventListener</code>
	 *
	 * @see #getListSelectionListeners
	 *
	 * @since 1.3
	 */
	public <T extends EventListener> T[] getListeners(Class<T> listenerType) {
		return listenerList.getListeners(listenerType);
	}

	// Updates first and last change indices
	private void markAsDirty(int r) {
		if (r == -1) {
			return;
		}

		firstAdjustedIndex = Math.min(firstAdjustedIndex, r);
		lastAdjustedIndex = Math.max(lastAdjustedIndex, r);
	}

	// Sets the state at this index and update all relevant state.
	private void set(int r) {
		if (value.get(r)) {
			return;
		}
		value.set(r);
		markAsDirty(r);

		// Update minimum and maximum indices
		minIndex = Math.min(minIndex, r);
		maxIndex = Math.max(maxIndex, r);
	}

	// Clears the state at this index and update all relevant state.
	private void clear(int r) {
		if (!value.get(r)) {
			return;
		}
		value.clear(r);
		markAsDirty(r);

		// Update minimum and maximum indices
		/*
		   If (r > minIndex) the minimum has not changed.
		   The case (r < minIndex) is not possible because r'th value was set.
		   We only need to check for the case when lowest entry has been cleared,
		   and in this case we need to search for the first value set above it.
		 */
		if (r == minIndex) {
			for (minIndex = minIndex + 1; minIndex <= maxIndex; minIndex++) {
				if (value.get(minIndex)) {
					break;
				}
			}
		}
		/*
		   If (r < maxIndex) the maximum has not changed.
		   The case (r > maxIndex) is not possible because r'th value was set.
		   We only need to check for the case when highest entry has been cleared,
		   and in this case we need to search for the first value set below it.
		 */
		if (r == maxIndex) {
			for (maxIndex = maxIndex - 1; minIndex <= maxIndex; maxIndex--) {
				if (value.get(maxIndex)) {
					break;
				}
			}
		}
		/* Performance note: This method is called from inside a loop in
		   changeSelection() but we will only iterate in the loops
		   above on the basis of one iteration per deselected cell - in total.
		   Ie. the next time this method is called the work of the previous
		   deselection will not be repeated.

		   We also don't need to worry about the case when the min and max
		   values are in their unassigned states. This cannot happen because
		   this method's initial check ensures that the selection was not empty
		   and therefore that the minIndex and maxIndex had 'real' values.

		   If we have cleared the whole selection, set the minIndex and maxIndex
		   to their cannonical values so that the next set command always works
		   just by using Math.min and Math.max.
		 */
		if (isSelectionEmpty()) {
			minIndex = MAX;
			maxIndex = MIN;
		}
	}

	/**
	 * Sets the value of the leadAnchorNotificationEnabled flag.
	 *
	 * @param flag boolean value for {@code leadAnchorNotificationEnabled}
	 * @see #isLeadAnchorNotificationEnabled()
	 */
	public void setLeadAnchorNotificationEnabled(boolean flag) {
		leadAnchorNotificationEnabled = flag;
	}

	/**
	 * Returns the value of the <code>leadAnchorNotificationEnabled</code> flag. When <code>leadAnchorNotificationEnabled</code> is true the
	 * model generates notification events with bounds that cover all the changes to the selection plus the changes to the lead and anchor
	 * indices. Setting the flag to false causes a narrowing of the event's bounds to include only the elements that have been selected or
	 * deselected since the last change. Either way, the model continues to maintain the lead and anchor variables internally. The default
	 * is true.
	 * <p>
	 * Note: It is possible for the lead or anchor to be changed without a change to the selection. Notification of these changes is often
	 * important, such as when the new lead or anchor needs to be updated in the view. Therefore, caution is urged when changing the default
	 * value.
	 *
	 * @return the value of the <code>leadAnchorNotificationEnabled</code> flag
	 * @see #setLeadAnchorNotificationEnabled(boolean)
	 */
	public boolean isLeadAnchorNotificationEnabled() {
		return leadAnchorNotificationEnabled;
	}

	private void updateLeadAnchorIndices(int anchorIndex2, int leadIndex2) {
		if (leadAnchorNotificationEnabled) {
			if (this.anchorIndex != anchorIndex2) {
				markAsDirty(this.anchorIndex);
				markAsDirty(anchorIndex2);
			}

			if (this.leadIndex != leadIndex2) {
				markAsDirty(this.leadIndex);
				markAsDirty(leadIndex2);
			}
		}
		this.anchorIndex = anchorIndex2;
		this.leadIndex = leadIndex2;
	}

	private boolean contains(int a, int b, int i) {
		return (i >= a) && (i <= b);
	}

	private void changeSelection(int clearMin, int clearMax, int setMin, int setMax, boolean clearFirst) {
		for (int i = Math.min(setMin, clearMin); i <= Math.max(setMax, clearMax); i++) {

			boolean shouldClear = contains(clearMin, clearMax, i);
			boolean shouldSet = contains(setMin, setMax, i);

			if (shouldSet && shouldClear) {
				if (clearFirst) {
					shouldClear = false;
				} else {
					shouldSet = false;
				}
			}

			if (shouldSet) {
				set(i);
			}
			if (shouldClear) {
				clear(i);
			}
		}
		fireValueChanged();
	}

	/**
	 * Change the selection with the effect of first clearing the values in the inclusive range [clearMin, clearMax] then setting the values
	 * in the inclusive range [setMin, setMax]. Do this in one pass so that no values are cleared if they would later be set.
	 */
	private void changeSelection(int clearMin, int clearMax, int setMin, int setMax) {
		changeSelection(clearMin, clearMax, setMin, setMax, true);
	}

	/** {@inheritDoc} */
	@Override
	public void clearSelection() {
		System.out.println("clear");
		removeSelectionIntervalImpl(minIndex, maxIndex, false);
	}

	/**
	 * Changes the selection to be between {@code index0} and {@code index1} inclusive. {@code index0} doesn't have to be less than or equal
	 * to {@code index1}.
	 * <p>
	 * In {@code SINGLE_SELECTION} selection mode, only the second index is used.
	 * <p>
	 * If this represents a change to the current selection, then each {@code ListSelectionListener} is notified of the change.
	 * <p>
	 * If either index is {@code -1}, this method does nothing and returns without exception. Otherwise, if either index is less than
	 * {@code -1}, an {@code IndexOutOfBoundsException} is thrown.
	 *
	 * @param index0 one end of the interval.
	 * @param index1 other end of the interval
	 * @throws IndexOutOfBoundsException if either index is less than {@code -1} (and neither index is {@code -1})
	 * @see #addListSelectionListener
	 */
	@Override
	public void setSelectionInterval(int index0, int index1) {
		if (index0 == -1 || index1 == -1) {
			return;
		}

		if (getSelectionMode() == SINGLE_SELECTION) {
			index0 = index1;
		}

		updateLeadAnchorIndices(index0, index1);

		int clearMin = minIndex;
		int clearMax = maxIndex;
		int setMin = Math.min(index0, index1);
		int setMax = Math.max(index0, index1);
		changeSelection(clearMin, clearMax, setMin, setMax);
	}

	/**
	 * Changes the selection to be the set union of the current selection and the indices between {@code index0} and {@code index1}
	 * inclusive.
	 * <p>
	 * In {@code SINGLE_SELECTION} selection mode, this is equivalent to calling {@code setSelectionInterval}, and only the second index is
	 * used. In {@code SINGLE_INTERVAL_SELECTION} selection mode, this method behaves like {@code setSelectionInterval}, unless the given
	 * interval is immediately adjacent to or overlaps the existing selection, and can therefore be used to grow it.
	 * <p>
	 * If this represents a change to the current selection, then each {@code ListSelectionListener} is notified of the change. Note that
	 * {@code index0} doesn't have to be less than or equal to {@code index1}.
	 * <p>
	 * If either index is {@code -1}, this method does nothing and returns without exception. Otherwise, if either index is less than
	 * {@code -1}, an {@code IndexOutOfBoundsException} is thrown.
	 *
	 * @param index0 one end of the interval.
	 * @param index1 other end of the interval
	 * @throws IndexOutOfBoundsException if either index is less than {@code -1} (and neither index is {@code -1})
	 * @see #addListSelectionListener
	 * @see #setSelectionInterval
	 */
	@Override
	public void addSelectionInterval(int index0, int index1) {
		if (index0 == -1 || index1 == -1) {
			return;
		}

		// If we only allow a single selection, channel through
		// setSelectionInterval() to enforce the rule.
		if (getSelectionMode() == SINGLE_SELECTION) {
			setSelectionInterval(index0, index1);
			return;
		}

		updateLeadAnchorIndices(index0, index1);

		int clearMin = MAX;
		int clearMax = MIN;
		int setMin = Math.min(index0, index1);
		int setMax = Math.max(index0, index1);

		// If we only allow a single interval and this would result
		// in multiple intervals, then set the selection to be just
		// the new range.
		if (getSelectionMode() == SINGLE_INTERVAL_SELECTION && (setMax < minIndex - 1 || setMin > maxIndex + 1)) {

			setSelectionInterval(index0, index1);
			return;
		}

		changeSelection(clearMin, clearMax, setMin, setMax);
	}

	/**
	 * Changes the selection to be the set difference of the current selection and the indices between {@code index0} and {@code index1}
	 * inclusive. {@code index0} doesn't have to be less than or equal to {@code index1}.
	 * <p>
	 * In {@code SINGLE_INTERVAL_SELECTION} selection mode, if the removal would produce two disjoint selections, the removal is extended
	 * through the greater end of the selection. For example, if the selection is {@code 0-10} and you supply indices {@code 5,6} (in any
	 * order) the resulting selection is {@code 0-4}.
	 * <p>
	 * If this represents a change to the current selection, then each {@code ListSelectionListener} is notified of the change.
	 * <p>
	 * If either index is {@code -1}, this method does nothing and returns without exception. Otherwise, if either index is less than
	 * {@code -1}, an {@code IndexOutOfBoundsException} is thrown.
	 *
	 * @param index0 one end of the interval
	 * @param index1 other end of the interval
	 * @throws IndexOutOfBoundsException if either index is less than {@code -1} (and neither index is {@code -1})
	 * @see #addListSelectionListener
	 */
	@Override
	public void removeSelectionInterval(int index0, int index1) {
		removeSelectionIntervalImpl(index0, index1, true);
	}

	// private implementation allowing the selection interval
	// to be removed without affecting the lead and anchor
	private void removeSelectionIntervalImpl(int index0, int index1, boolean changeLeadAnchor) {

		if (index0 == -1 || index1 == -1) {
			return;
		}

		if (changeLeadAnchor) {
			updateLeadAnchorIndices(index0, index1);
		}

		int clearMin = Math.min(index0, index1);
		int clearMax = Math.max(index0, index1);
		int setMin = MAX;
		int setMax = MIN;

		// If the removal would produce to two disjoint selections in a mode
		// that only allows one, extend the removal to the end of the selection.
		if (getSelectionMode() != MULTIPLE_INTERVAL_SELECTION && clearMin > minIndex && clearMax < maxIndex) {
			clearMax = maxIndex;
		}

		changeSelection(clearMin, clearMax, setMin, setMax);
	}

	private void setState(int index, boolean state) {
		if (state) {
			set(index);
		} else {
			clear(index);
		}
	}

	/**
	 * Insert length indices beginning before/after index. If the value at index is itself selected and the selection mode is not
	 * SINGLE_SELECTION, set all of the newly inserted items as selected. Otherwise leave them unselected. This method is typically called
	 * to sync the selection model with a corresponding change in the data model.
	 */
	@Override
	public void insertIndexInterval(int index, int length, boolean before) {
		/* The first new index will appear at insMinIndex and the last
		 * one will appear at insMaxIndex
		 */
		int insMinIndex = (before) ? index : index + 1;
		int insMaxIndex = (insMinIndex + length) - 1;

		/* Right shift the entire bitset by length, beginning with
		 * index-1 if before is true, index+1 if it's false (i.e. with
		 * insMinIndex).
		 */
		for (int i = maxIndex; i >= insMinIndex; i--) {
			setState(i + length, value.get(i));
		}

		/* Initialize the newly inserted indices.
		 */
		boolean setInsertedValues = ((!addSelectionOnInsert || getSelectionMode() == SINGLE_SELECTION) ? false : value.get(index));
		for (int i = insMinIndex; i <= insMaxIndex; i++) {
			setState(i, setInsertedValues);
		}

		int leadIndex2 = this.leadIndex;
		if (leadIndex2 > index || (before && leadIndex2 == index)) {
			leadIndex2 = this.leadIndex + length;
		}
		int anchorIndex2 = this.anchorIndex;
		if (anchorIndex2 > index || (before && anchorIndex2 == index)) {
			anchorIndex2 = this.anchorIndex + length;
		}
		if (leadIndex2 != this.leadIndex || anchorIndex2 != this.anchorIndex) {
			updateLeadAnchorIndices(anchorIndex2, leadIndex2);
		}

		fireValueChanged();
	}


	/**
	 * Remove the indices in the interval index0,index1 (inclusive) from the selection model. This is typically called to sync the selection
	 * model width a corresponding change in the data model. Note that (as always) index0 need not be &lt;= index1.
	 */
	@Override
	public void removeIndexInterval(int index0, int index1) {
		int rmMinIndex = Math.min(index0, index1);
		int rmMaxIndex = Math.max(index0, index1);
		int gapLength = (rmMaxIndex - rmMinIndex) + 1;

		/* Shift the entire bitset to the left to close the index0, index1
		 * gap.
		 */
		for (int i = rmMinIndex; i <= maxIndex; i++) {
			setState(i, value.get(i + gapLength));
		}

		int leadIndex2 = this.leadIndex;
		if (leadIndex2 == 0 && rmMinIndex == 0) {
			// do nothing
		} else if (leadIndex2 > rmMaxIndex) {
			leadIndex2 = this.leadIndex - gapLength;
		} else if (leadIndex2 >= rmMinIndex) {
			leadIndex2 = rmMinIndex - 1;
		}

		int anchorIndex2 = this.anchorIndex;
		if (anchorIndex2 == 0 && rmMinIndex == 0) {
			// do nothing
		} else if (anchorIndex2 > rmMaxIndex) {
			anchorIndex2 = this.anchorIndex - gapLength;
		} else if (anchorIndex2 >= rmMinIndex) {
			anchorIndex2 = rmMinIndex - 1;
		}

		if (leadIndex2 != this.leadIndex || anchorIndex2 != this.anchorIndex) {
			updateLeadAnchorIndices(anchorIndex2, leadIndex2);
		}

		fireValueChanged();
	}


	/** {@inheritDoc} */
	@Override
	public void setValueIsAdjusting(boolean isAdjusting) {
		if (isAdjusting != this.isAdjusting) {
			this.isAdjusting = isAdjusting;
			this.fireValueChanged(isAdjusting);
		}
	}


	/**
	 * Returns a string that displays and identifies this object's properties.
	 *
	 * @return a <code>String</code> representation of this object
	 */
	@Override
	public String toString() {
		String s = ((getValueIsAdjusting()) ? "~" : "=") + value.toString();
		return getClass().getName() + " " + Integer.toString(hashCode()) + " " + s;
	}

	/**
	 * Returns a clone of this selection model with the same selection. <code>listenerLists</code> are not duplicated.
	 *
	 * @exception CloneNotSupportedException if the selection model does not both (a) implement the Cloneable interface and (b) define a
	 *            <code>clone</code> method.
	 */
	@Override
	public Object clone() throws CloneNotSupportedException {
		ObservableListSelectionModel<E> clone = (ObservableListSelectionModel<E>) super.clone();
		clone.value = value.clone();
		clone.listenerList = new EventListenerList();
		return clone;
	}

	/** {@inheritDoc} */
	@Override
	@Transient
	public int getAnchorSelectionIndex() {
		return anchorIndex;
	}

	/** {@inheritDoc} */
	@Override
	@Transient
	public int getLeadSelectionIndex() {
		return leadIndex;
	}

	/**
	 * Set the anchor selection index, leaving all selection values unchanged. If leadAnchorNotificationEnabled is true, send a notification
	 * covering the old and new anchor cells.
	 *
	 * @see #getAnchorSelectionIndex
	 * @see #setLeadSelectionIndex
	 */
	@Override
	public void setAnchorSelectionIndex(int anchorIndex) {
		updateLeadAnchorIndices(anchorIndex, this.leadIndex);
		fireValueChanged();
	}

	/**
	 * Set the lead selection index, leaving all selection values unchanged. If leadAnchorNotificationEnabled is true, send a notification
	 * covering the old and new lead cells.
	 *
	 * @param leadIndex2 the new lead selection index
	 *
	 * @see #setAnchorSelectionIndex
	 * @see #setLeadSelectionIndex
	 * @see #getLeadSelectionIndex
	 *
	 * @since 1.5
	 */
	public void moveLeadSelectionIndex(int leadIndex2) {
		// disallow a -1 lead unless the anchor is already -1
		if (leadIndex2 == -1) {
			if (this.anchorIndex != -1) {
				return;
			}

			/* PENDING(shannonh) - The following check is nice, to be consistent with
			           setLeadSelectionIndex. However, it is not absolutely
			           necessary: One could work around it by setting the anchor
			           to something valid, modifying the lead, and then moving
			           the anchor back to -1. For this reason, there's no sense
			           in adding it at this time, as that would require
			           updating the spec and officially committing to it.

			        // otherwise, don't do anything if the anchor is -1
			        } else if (this.anchorIndex == -1) {
			return;
			 */

		}

		updateLeadAnchorIndices(this.anchorIndex, leadIndex2);
		fireValueChanged();
	}

	/**
	 * Sets the lead selection index, ensuring that values between the anchor and the new lead are either all selected or all deselected. If
	 * the value at the anchor index is selected, first clear all the values in the range [anchor, oldLeadIndex], then select all the values
	 * values in the range [anchor, newLeadIndex], where oldLeadIndex is the old leadIndex and newLeadIndex is the new one.
	 * <p>
	 * If the value at the anchor index is not selected, do the same thing in reverse selecting values in the old range and deselecting
	 * values in the new one.
	 * <p>
	 * Generate a single event for this change and notify all listeners. For the purposes of generating minimal bounds in this event, do the
	 * operation in a single pass; that way the first and last index inside the ListSelectionEvent that is broadcast will refer to cells
	 * that actually changed value because of this method. If, instead, this operation were done in two steps the effect on the selection
	 * state would be the same but two events would be generated and the bounds around the changed values would be wider, including cells
	 * that had been first cleared only to later be set.
	 * <p>
	 * This method can be used in the <code>mouseDragged</code> method of a UI class to extend a selection.
	 *
	 * @see #getLeadSelectionIndex
	 * @see #setAnchorSelectionIndex
	 */
	@Override
	public void setLeadSelectionIndex(int leadIndex) {
		int anchorIndex2 = this.anchorIndex;

		// only allow a -1 lead if the anchor is already -1
		if (leadIndex == -1) {
			if (anchorIndex2 == -1) {
				updateLeadAnchorIndices(anchorIndex2, leadIndex);
				fireValueChanged();
			}

			return;
			// otherwise, don't do anything if the anchor is -1
		} else if (anchorIndex2 == -1) {
			return;
		}

		if (this.leadIndex == -1) {
			this.leadIndex = leadIndex;
		}

		boolean shouldSelect = value.get(this.anchorIndex);

		if (getSelectionMode() == SINGLE_SELECTION) {
			anchorIndex2 = leadIndex;
			shouldSelect = true;
		}

		int oldMin = Math.min(this.anchorIndex, this.leadIndex);
		int oldMax = Math.max(this.anchorIndex, this.leadIndex);
		int newMin = Math.min(anchorIndex2, leadIndex);
		int newMax = Math.max(anchorIndex2, leadIndex);

		updateLeadAnchorIndices(anchorIndex2, leadIndex);

		if (shouldSelect) {
			changeSelection(oldMin, oldMax, newMin, newMax);
		} else {
			changeSelection(newMin, newMax, oldMin, oldMax, false);
		}
	}
}
