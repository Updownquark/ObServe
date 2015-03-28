package org.observe.util;

import java.util.Collection;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

/**
 * Stores a set of listeners. This class includes functionality allowing containing code to lazily initialize itself when the first listener
 * is added and release its resources when the last listener is removed.
 *
 * @param <E> The type of listener to store
 */
public class ListenerSet<E> {
	private final Collection<E> theListeners;
	private final ReentrantReadWriteLock theLock;

	private final ConcurrentHashMap<IdentityKey<E>, Boolean> theListenersToRemove;

	private final Collection<E> theListenersToAdd;

	private Consumer<Boolean> theUsedListener;
	private Consumer<E> theOnSubscribe;

	/** Creates the set of listeners */
	public ListenerSet() {
		theListeners = new LinkedList<>();
		theLock = new ReentrantReadWriteLock();
		// This need not be thread safe since it will
		theListenersToRemove = new ConcurrentHashMap<>();
		theListenersToAdd = new ConcurrentLinkedQueue<>();
		theOnSubscribe = listener -> {
		};
		theUsedListener = used -> {
		};
	}

	/** @param onSubscribe The function to call for each listener that is added to this set */
	public void setOnSubscribe(Consumer<E> onSubscribe) {
		theOnSubscribe = onSubscribe;
	}

	/**
	 * @param used The function to call when this set goes from being unused (no listeners) to used (having listeners) and vice versa. The
	 *            parameter passed to the function is true when this set goes from being unused to used and false when it goes from being
	 *            used to unused
	 */
	public void setUsedListener(Consumer<Boolean> used) {
		theUsedListener = used;
	}

	/**
	 * @param listener The listener to add
	 * @return Whether the listener was added (only false if the listener is already in this set)
	 */
	public boolean add(E listener) {
		if(theLock.getReadHoldCount() > 0 && !theListeners.contains(listener)) {
			theListenersToAdd.add(listener);
			return true;
		}
		Lock lock = theLock.writeLock();
		lock.lock();
		boolean ret;
		try {
			boolean wasEmpty = theListeners.isEmpty();
			ret = theListeners.add(listener);
			if(wasEmpty)
				theUsedListener.accept(true);
			theOnSubscribe.accept(listener);
		} finally {
			lock.unlock();
		}
		return ret;
	}

	/**
	 * @param listener The listener to remove
	 * @return Whether the listener was removed (false if the listener was not in this set)
	 */
	public boolean remove(E listener) {
		if(theListenersToAdd.remove(listener))
			return true;
		if(theLock.getReadHoldCount() > 0 && theListeners.contains(listener)) {
			theListenersToRemove.put(new IdentityKey<>(listener), true);
			return true;
		}
		Lock lock = theLock.writeLock();
		lock.lock();
		boolean ret;
		try {
			ret = theListeners.remove(listener);
			if(ret && theListeners.isEmpty())
				theUsedListener.accept(false);
		} finally {
			lock.unlock();
		}
		return ret;
	}

	/**
	 * Invokes this set's listeners
	 *
	 * @param call The function to use each listener in this set
	 */
	public void forEach(Consumer<? super E> call) {
		Lock lock = theLock.readLock();
		lock.lock();
		try {
			for(E listener : theListeners) {
				if(!theListenersToRemove.containsKey(listener))
					call.accept(listener);
			}
		} finally {
			lock.unlock();
		}
		if(theLock.getReadHoldCount() == 0) {
			addAndRemoveQueuedListeners();
		}
	}

	private void addAndRemoveQueuedListeners() {
		if(theListenersToRemove.isEmpty() && theListenersToAdd.isEmpty())
			return;
		Lock lock = theLock.writeLock();
		lock.lock();
		try {
			boolean beforeEmpty = theListeners.isEmpty();
			if(!theListenersToRemove.isEmpty()) {
				for(IdentityKey<E> listener : theListenersToRemove.keySet())
					theListeners.remove(listener.value);
				theListenersToRemove.clear();
			}
			if(!theListenersToAdd.isEmpty()) {
				for(E listener : theListenersToAdd) {
					theListeners.add(listener);
					theOnSubscribe.accept(listener);
				}
			}

			if(beforeEmpty != theListeners.isEmpty())
				theUsedListener.accept(!theListeners.isEmpty());
		} finally {
			lock.unlock();
		}
	}
}
