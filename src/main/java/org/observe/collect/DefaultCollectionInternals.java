package org.observe.collect;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

import org.observe.Subscription;

abstract class DefaultCollectionInternals<E> {
	private final java.util.concurrent.ConcurrentHashMap<Consumer<? super ObservableElement<E>>, ConcurrentLinkedQueue<Subscription>> theObservers;
	private final ReentrantReadWriteLock theLock;
	private final AtomicBoolean hasIssuedController;
	private Consumer<? super Consumer<? super ObservableElement<E>>> theOnSubscribe;

	private final Consumer<? super Boolean> thePreAction;
	private final Consumer<? super Boolean> thePostAction;

	DefaultCollectionInternals(ReentrantReadWriteLock lock, AtomicBoolean issuedController, Consumer<? super Boolean> preAction,
		Consumer<? super Boolean> postAction) {
		theObservers = new java.util.concurrent.ConcurrentHashMap<>();
		theLock = lock;
		hasIssuedController = issuedController;
		thePreAction = preAction;
		thePostAction = postAction;
	}

	ReentrantReadWriteLock getLock() {
		return theLock;
	}

	void setOnSubscribe(Consumer<? super Consumer<? super ObservableElement<E>>> onSubscribe) {
		theOnSubscribe = onSubscribe;
	}

	/**
	 * @param action The action to perform under a lock
	 * @param write Whether to perform the action under a write lock or a read lock
	 * @param errIfControlled Whether to throw an exception if this list is controlled
	 */
	void doLocked(Runnable action, boolean write, boolean errIfControlled) {
		if(errIfControlled && hasIssuedController.get())
			throw new IllegalStateException("Controlled default observable collections cannot be modified directly");
		Lock lock = write ? theLock.writeLock() : theLock.readLock();
		lock.lock();
		try {
			if(thePreAction != null)
				thePreAction.accept(write);
			action.run();
		} finally {
			try {
				if(thePostAction != null)
					thePostAction.accept(write);
			} finally {
				lock.unlock();
			}
		}
	}

	Subscription onElement(Consumer<? super ObservableElement<E>> onElement, boolean forward) {
		ConcurrentLinkedQueue<Subscription> subSubscriptions = new ConcurrentLinkedQueue<>();
		theObservers.put(onElement, subSubscriptions);
		doLocked(() -> {
			for(InternalObservableElementImpl<E> el : getElements(forward))
				onElement.accept(createExposedElement(el, subSubscriptions));
		}, false, false);
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
