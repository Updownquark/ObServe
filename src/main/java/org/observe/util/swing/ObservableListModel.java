package org.observe.util.swing;

import java.awt.EventQueue;
import java.util.IdentityHashMap;

import javax.swing.ListModel;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

import org.observe.Subscription;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.ObservableList;
import org.observe.collect.OrderedCollectionChangeEvent;

/**
 * A swing ListModel backed by an ObservableList
 *
 * @param <E>
 *            The type of data in the list
 */
public class ObservableListModel<E> implements ListModel<E> {
	private final ObservableList<E> theWrapped;
	private final IdentityHashMap<ListDataListener, Subscription> theListenerSubscribes;

	/**
	 * @param wrap
	 *            The observable list to back this model
	 */
	public ObservableListModel(ObservableList<E> wrap) {
		super();
		theWrapped = wrap;
		theListenerSubscribes = new IdentityHashMap<>();
	}

	/** @return The observable list that this model wraps */
	public ObservableList<E> getWrapped() {
		return theWrapped;
	}

	@Override
	public int getSize() {
		return theWrapped.size();
	}

	@Override
	public E getElementAt(int index) {
		return theWrapped.get(index);
	}

	@Override
	public void addListDataListener(ListDataListener l) {
		theListenerSubscribes.put(l, theWrapped.changes().act(event -> {
			// DEADLOCK can occur if the event handling is done off the EDT
			if (EventQueue.isDispatchThread()) {
				handleEvent(l, event);
			} else {
				try {
					EventQueue.invokeLater(() -> handleEvent(l, event));
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}));
	}

	private void handleEvent(ListDataListener l, OrderedCollectionChangeEvent<E> event) {
		int[][] split = ObservableSwingUtils.getContinuousIntervals(event.indexes.toArray(), true);
		for (int[] indexes : split) {
			ListDataEvent wrappedEvent = new ListDataEvent(ObservableListModel.this, getSwingType(event.type), indexes[0], indexes[1]);
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
		}
	}

	private int getSwingType(CollectionChangeType type) {
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

	@Override
	public void removeListDataListener(ListDataListener l) {
		Subscription sub = theListenerSubscribes.remove(l);
		if (sub != null) {
			sub.unsubscribe();
		}
	}
}
