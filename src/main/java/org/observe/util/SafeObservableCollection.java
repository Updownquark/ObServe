package org.observe.util;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.observe.Observable;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollectionEvent;
import org.qommons.Transaction;
import org.qommons.collect.CollectionElement;

/**
 * An {@link ObservableCollection} that only fires updates on a particular thread
 *
 * @param <E> The type of elements in the collection
 */
public class SafeObservableCollection<E> extends AbstractSafeObservableCollection<E> {
	private final ConcurrentLinkedQueue<ObservableCollectionEvent<? extends E>> theEventQueue;

	/**
	 * @param collection The backing collection
	 * @param onEventThread A test that returns true only if the thread it is invoked from is acceptable for firing events directly
	 * @param eventThreadExec An executor to invoke events on an acceptable event thread
	 * @param until An observable which, when fired, will stop the eventing on this collection and release its resources and listeners
	 */
	public SafeObservableCollection(ObservableCollection<E> collection, BooleanSupplier onEventThread, Consumer<Runnable> eventThreadExec,
		Observable<?> until) {
		super(collection, onEventThread);

		theEventQueue = new ConcurrentLinkedQueue<>();

		init(until);
	}

	@Override
	protected void handleEvent(ObservableCollectionEvent<? extends E> evt, boolean initial) {
		if (!initial && (!theEventQueue.isEmpty() || !isOnEventThread()))
			theEventQueue.add(evt);
		else
			eventOccurred(evt);
	}

	@Override
	public boolean hasQueuedEvents() {
		return !theEventQueue.isEmpty();
	}

	@Override
	protected boolean doFlush() {
		ObservableCollectionEvent<? extends E> evt = theEventQueue.poll();
		if (evt != null) {
			try (Transaction t = theSyntheticCollection.lock(true, null)) {
				while (evt != null) {
					eventOccurred(evt);
					evt = theEventQueue.poll();
				}
			}
			return true;
		} else
			return false;
	}

	private void eventOccurred(ObservableCollectionEvent<? extends E> evt) {
		switch (evt.getType()) {
		case add:
			theSyntheticCollection.add(evt.getIndex(), createElement(evt.getElementId(), evt.getNewValue()));
			break;
		case remove:
			theSyntheticCollection.remove(evt.getIndex());
			break;
		case set:
			CollectionElement<ElementRef<E>> refEl = theSyntheticCollection.getElement(evt.getIndex());
			refEl.get().setValue(evt.getNewValue());
			theSyntheticCollection.mutableElement(refEl.getElementId()).set(refEl.get());// Update event
			break;
		}
	}
}
