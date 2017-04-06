package org.observe.collect.impl;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

import org.observe.ObservableValue;
import org.observe.Subscription;
import org.observe.collect.CollectionSession;
import org.observe.collect.ObservableElement;
import org.observe.util.DefaultTransactable;
import org.qommons.Transactable;
import org.qommons.Transaction;

abstract class DefaultCollectionInternals<E> {
	private final java.util.concurrent.ConcurrentHashMap<Consumer<? super ObservableElement<E>>, ConcurrentLinkedQueue<Subscription>> theObservers;
	private final ReentrantReadWriteLock theLock;
	private final ObservableValue<CollectionSession> theSession;
	private final Transactable theSessionController;
	private Consumer<? super Consumer<? super ObservableElement<E>>> theOnSubscribe;

	private final Runnable thePostWriteAction;

	DefaultCollectionInternals(ReentrantReadWriteLock lock, ObservableValue<CollectionSession> session, Transactable sessionController,
		Runnable postWriteAction) {
		theObservers = new java.util.concurrent.ConcurrentHashMap<>();
		theLock = lock;
		theSessionController = session != null ? sessionController : new DefaultTransactable(theLock);
		theSession = session != null ? session : ((DefaultTransactable) theSessionController).getSession();
		thePostWriteAction = postWriteAction;
	}

	ReentrantReadWriteLock getLock() {
		return theLock;
	}

	void setOnSubscribe(Consumer<? super Consumer<? super ObservableElement<E>>> onSubscribe) {
		theOnSubscribe = onSubscribe;
	}

	ObservableValue<CollectionSession> getSession() {
		return theSession;
	}

	Transaction lock(boolean write, boolean withSession, Object cause) {
		Transaction sessTrans = (withSession && theSessionController != null) ? theSessionController.lock(write, cause) : null;
		Lock lock;
		if (sessTrans != null) {
			lock = null; // The session controller is doing the locking
		} else if (theLock.isWriteLockedByCurrentThread())
			lock = null; // If we're already locked, don't grab another one. May also let us avoid creating a transaction below.
		else if (theLock.getReadHoldCount() > 0) {
			if (write)
				throw new IllegalStateException("Cannot upgrade from read to write lock");
			else
				lock = null;// If we're already locked, don't grab another one. May also let us avoid creating a transaction below.
		} else {
			lock = write ? theLock.writeLock() : theLock.readLock();
		}
		if (lock != null)
			lock.lock();
		if (lock == null && sessTrans == null && (!write || thePostWriteAction == null))
			return Transaction.NONE;
		boolean success = false;
		try {
			success = true;
			return new Transaction() {
				private volatile boolean hasRun;

				@Override
				public void close() {
					if (hasRun)
						return;
					hasRun = true;
					if (write && thePostWriteAction != null)
						thePostWriteAction.run();
					if(lock != null)
						lock.unlock();
					if(sessTrans != null)
						sessTrans.close();
				}
			};
		} finally {
			if (!success) {
				if (lock != null)
					lock.unlock();
				if (sessTrans != null)
					sessTrans.close();
			}
		}
	}

	Subscription onElement(Consumer<? super ObservableElement<E>> onElement, boolean forward) {
		ConcurrentLinkedQueue<Subscription> subSubscriptions = new ConcurrentLinkedQueue<>();
		theObservers.put(onElement, subSubscriptions);
		try (Transaction t = lock(false, false, null)) {
			for(InternalObservableElementImpl<E> el : getElements(forward))
				onElement.accept(createExposedElement(el, subSubscriptions));
		}
		if(theOnSubscribe != null)
			theOnSubscribe.accept(onElement);
		return () -> {
			ConcurrentLinkedQueue<Subscription> subs = theObservers.remove(onElement);
			if(subs == null)
				return;
			for(Subscription sub : subs)
				sub.unsubscribe();
		};
	}

	void fireNewElement(InternalObservableElementImpl<E> el) {
		Map.Entry<Consumer<? super ObservableElement<E>>, ConcurrentLinkedQueue<Subscription>> [] observers = theObservers.entrySet()
			.toArray(new Map.Entry[theObservers.size()]);
		for(Map.Entry<Consumer<? super ObservableElement<E>>, ConcurrentLinkedQueue<Subscription>> observer : observers) {
			ObservableElement<E> exposed = createExposedElement(el, observer.getValue());
			Consumer<? super ObservableElement<E>> key = observer.getKey();
			key.accept(exposed);
		}
	}

	abstract Iterable<? extends InternalObservableElementImpl<E>> getElements(boolean forward);

	abstract ObservableElement<E> createExposedElement(InternalObservableElementImpl<E> internal, Collection<Subscription> subscriptions);
}
