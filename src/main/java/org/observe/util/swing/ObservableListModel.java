package org.observe.util.swing;

import java.awt.EventQueue;
import java.util.ArrayList;
import java.util.List;

import javax.swing.ListModel;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

import org.observe.Subscription;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.ObservableOrderedCollection;
import org.observe.collect.OrderedCollectionChangeEvent;
import org.qommons.ListenerSet;

/**
 * A swing ListModel backed by an ObservableList
 *
 * @param <E>
 *            The type of data in the list
 */
public class ObservableListModel<E> implements ListModel<E> {
	private final ObservableOrderedCollection<E> theWrapped;
	/**
	 * Because of the way the {@link ObservableOrderedCollection#changes()} method is implemented, it is possible for an event received by
	 * the observer to be an incomplete representation of all the changes that are already represented in the collection. E.g. if a user
	 * adds data to a collection, then removes data, the remove will take effect in the collection's internals before the add event is fired
	 * to the listener.
	 *
	 * In addition, {@link OrderedCollectionChangeEvent} contains more information than can be communicated in a single
	 * {@link ListDataEvent}. So multiple swing events must be fired for each change event.
	 *
	 * Since the {@link ListDataEvent} class does not contain the actual data added/removed/modified, the swing classes must peek into the
	 * current value of the list to obtain this information. If this class were to use the collection directly for these peeks, the result
	 * would be inconsistent, since the collection may contain structural modifications not yet accounted for by swing events.
	 *
	 * So we must maintain a cache of what the collection looks like up to and including any currently firing swing event.
	 */
	private final List<E> theCache;
	private final ListenerSet<ListDataListener> theListeners;

	/**
	 * @param wrap
	 *            The observable list to back this model
	 */
	public ObservableListModel(ObservableOrderedCollection<E> wrap) {
		super();
		theWrapped = wrap;
		theCache = new ArrayList<>();
		theListeners = new ListenerSet<>();
		Subscription[] wrapSub = new Subscription[1];
		theListeners.setUsedListener(used -> {
			if (used) {
				wrapSub[0] = theWrapped.changes().act(event -> {
					// DEADLOCK can occur if the event handling is done off the EDT
					if (EventQueue.isDispatchThread()) {
						handleEvent(event);
					} else {
						try {
							EventQueue.invokeLater(() -> handleEvent(event));
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				});
				theCache.addAll(theWrapped);
			} else {
				if (wrapSub[0] != null) {
					wrapSub[0].unsubscribe();
					wrapSub[0] = null;
				}
				theCache.clear();
			}
		});
	}

	/** @return The observable list that this model wraps */
	public ObservableOrderedCollection<E> getWrapped() {
		return theWrapped;
	}

	@Override
	public int getSize() {
		return theCache.size();
	}

	@Override
	public E getElementAt(int index) {
		return theCache.get(index);
	}

	@Override
	public void addListDataListener(ListDataListener l) {
		theListeners.add(l);
	}

	private void handleEvent(OrderedCollectionChangeEvent<E> event) {
		int[][] split = ObservableSwingUtils.getContinuousIntervals(event.indexes.toArray(), true);
		if (event.type == CollectionChangeType.remove)
			split = reverse(split); // Need to descend for removal
		for (int[] indexes : split) {
			// Update the cache for this interval
			switch (event.type) {
			case add:
				for (int index = indexes[0]; index <= indexes[1]; index++) {
					int elIdx = event.indexes.indexOf(index);
					theCache.add(index, event.values.get(elIdx));
				}
				break;
			case remove:
				for (int index = indexes[1]; index >= indexes[0]; index--) {
					theCache.remove(index);
				}
				break;
			case set:
				for (int index = indexes[0]; index <= indexes[1]; index++) {
					int elIdx = event.indexes.indexOf(index);
					theCache.set(index, event.values.get(elIdx));
				}
				break;
			}
			// Fire the change event for this interval to all listeners
			ListDataEvent wrappedEvent = new ListDataEvent(ObservableListModel.this, getSwingType(event.type), indexes[0], indexes[1]);
			theListeners.forEach(l -> {
				switch (event.type) {
				case add:
					l.intervalAdded(wrappedEvent);
					break;
				case remove:
					l.intervalRemoved(wrappedEvent);
					break;
				case set:
					l.contentsChanged(wrappedEvent);
					break;
				}
			});
		}
	}

	private static int getSwingType(CollectionChangeType type) {
		switch(type){
		case add:
			return ListDataEvent.INTERVAL_ADDED;
		case remove:
			return ListDataEvent.INTERVAL_REMOVED;
		case set:
			return ListDataEvent.CONTENTS_CHANGED;
		}
		throw new IllegalStateException("Unrecognized event type: " + type);
	}

	private static int[][] reverse(int[][] array) {
		int lDiv2 = array.length / 2;
		for (int i = 0; i < lDiv2; i++) {
			int[] temp = array[i];
			array[i] = array[array.length - i - 1];
			array[array.length - i - 1] = temp;
		}
		return array;
	}

	@Override
	public void removeListDataListener(ListDataListener l) {
		theListeners.remove(l);
	}
}
