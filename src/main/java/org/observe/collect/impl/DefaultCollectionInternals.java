package org.observe.collect.impl;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

import org.observe.Subscription;
import org.observe.collect.ObservableElement;
import org.observe.util.Transaction;

abstract class DefaultCollectionInternals<E> {
	private final java.util.concurrent.ConcurrentHashMap<Consumer<? super ObservableElement<E>>, ConcurrentLinkedQueue<Subscription>> theObservers;
	private final ReentrantReadWriteLock theLock;
	private Consumer<? super Consumer<? super ObservableElement<E>>> theOnSubscribe;

	private final Consumer<? super Boolean> thePreAction;
	private final Consumer<? super Boolean> thePostAction;

	DefaultCollectionInternals(ReentrantReadWriteLock lock, Consumer<? super Boolean> preAction, Consumer<? super Boolean> postAction) {
		theObservers = new java.util.concurrent.ConcurrentHashMap<>();
		theLock = lock;
		thePreAction = preAction;
		thePostAction = postAction;
	}

	ReentrantReadWriteLock getLock() {
		return theLock;
	}

	void setOnSubscribe(Consumer<? super Consumer<? super ObservableElement<E>>> onSubscribe) {
		theOnSubscribe = onSubscribe;
	}

	Transaction lock(boolean write) {
		Lock lock = write ? theLock.writeLock() : theLock.readLock();
		lock.lock();
		boolean success = false;
		try {
			if(thePreAction != null)
				thePreAction.accept(write);
			success = true;
			return new Transaction() {
				private volatile boolean hasRun;

				@Override
				public void close() {
					if(hasRun)
						return;
					hasRun = true;
					lock.unlock();
					if(thePostAction != null)
						thePostAction.accept(write);
				}

				@Override
				protected void finalize() throws Throwable {
					if(!hasRun)
						close();
				}
			};
		} finally {
			if(!success)
				lock.unlock();
		}
	}

	Subscription onElement(Consumer<? super ObservableElement<E>> onElement, boolean forward) {
		ConcurrentLinkedQueue<Subscription> subSubscriptions = new ConcurrentLinkedQueue<>();
		theObservers.put(onElement, subSubscriptions);
		try (Transaction t = lock(false)) {
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
		for(Map.Entry<Consumer<? super ObservableElement<E>>, ConcurrentLinkedQueue<Subscription>> observer : theObservers.entrySet()) {
			observer.getKey().accept(createExposedElement(el, observer.getValue()));
		}
	}

	abstract Iterable<? extends InternalObservableElementImpl<E>> getElements(boolean forward);

	abstract ObservableElement<E> createExposedElement(InternalObservableElementImpl<E> internal, Collection<Subscription> subscriptions);
}
