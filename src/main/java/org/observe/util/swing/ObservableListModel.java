package org.observe.util.swing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.ListModel;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

import org.observe.Subscription;
import org.observe.collect.CollectionChangeEvent;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.ObservableCollection;
import org.qommons.Transaction;

/**
 * A swing ListModel backed by an {@link ObservableCollection}
 *
 * @param <E> The type of data in the collection
 */
public class ObservableListModel<E> implements ListModel<E> {
	private final ObservableCollection<E> theWrapped;
	/**
	 * This model must keep an independent representation of its data, which is only modified on the EDT, just before firing an event
	 * documenting the modification
	 */
	private final List<E> theCachedData;
	private final List<ListDataListener> theListeners;
	private Subscription theListening;
	private final AtomicInteger thePendingUpdates;

	/**
	 * @param wrap
	 *            The observable list to back this model
	 */
	public ObservableListModel(ObservableCollection<E> wrap) {
		theWrapped = wrap;
		theCachedData = new ArrayList<>();
		theListeners = new ArrayList<>();
		thePendingUpdates = new AtomicInteger();
	}

	/** @return The observable list that this model wraps */
	public ObservableCollection<E> getWrapped() {
		return theWrapped;
	}

	@Override
	public int getSize() {
		if (theListening != null)
			return theCachedData.size();
		else
			return theWrapped.size();
	}

	@Override
	public E getElementAt(int index) {
		if (theListening != null)
			return theCachedData.get(index);
		else
			return theWrapped.get(index);
	}

	@Override
	public void addListDataListener(ListDataListener l) {
		ObservableSwingUtils.onEQ(() -> {
			if (theListeners.isEmpty())
				beginListening();
			theListeners.add(l);
		});
	}

	@Override
	public void removeListDataListener(ListDataListener l) {
		ObservableSwingUtils.onEQ(() -> {
			theListeners.remove(l);
			if (theListeners.isEmpty() && theListening != null) {
				theListening.unsubscribe();
				theListening = null;
				theCachedData.clear();
			}
		});
	}

	/**
	 * @return The number of update events that have occurred in the backing collection but are not yet represented in the EDT-safe list
	 *         model
	 */
	public int getPendingUpdates() {
		return thePendingUpdates.get();
	}

	private void beginListening() {
		try (Transaction t = theWrapped.lock(false, null)) {
			theCachedData.addAll(theWrapped);
			theListening = theWrapped.changes().act(event -> {
				// All internal data representation mutation and event firing must be done on the EDT
				thePendingUpdates.getAndIncrement();
				ObservableSwingUtils.onEQ(() -> handleEvent(event));
			});
		}
	}

	private void handleEvent(CollectionChangeEvent<E> event) {
		thePendingUpdates.getAndDecrement();
		Map<Integer, E> changesByIndex = new HashMap<>();
		if (event.type != CollectionChangeType.remove) {
			for (CollectionChangeEvent.ElementChange<E> el : event.elements)
				changesByIndex.put(el.index, el.newValue);
		}
		int[][] split = ObservableSwingUtils.getContinuousIntervals(event.elements, event.type != CollectionChangeType.remove);
		for (int[] indexes : split) {
			ListDataEvent wrappedEvent = new ListDataEvent(ObservableListModel.this, getSwingType(event.type), indexes[0], indexes[1]);
			switch (event.type) {
			case add:
				for (int i = indexes[0]; i <= indexes[1]; i++)
					theCachedData.add(i, changesByIndex.remove(i));
				intervalAdded(wrappedEvent);
				break;
			case remove:
				for (int i = indexes[1]; i >= indexes[0]; i--)
					theCachedData.remove(i);
				intervalRemoved(wrappedEvent);
				break;
			case set:
				for (int i = indexes[0]; i <= indexes[1]; i++)
					theCachedData.set(i, changesByIndex.remove(i));
				contentsChanged(wrappedEvent);
				break;
			}
		}
	}

	private void intervalAdded(ListDataEvent event) {
		for (ListDataListener listener : theListeners) {
			listener.intervalAdded(event);
		}
	}

	private void intervalRemoved(ListDataEvent event) {
		for (ListDataListener listener : theListeners) {
			listener.intervalRemoved(event);
		}
	}

	private void contentsChanged(ListDataEvent event) {
		for (ListDataListener listener : theListeners) {
			listener.contentsChanged(event);
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
}
