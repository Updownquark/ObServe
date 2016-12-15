package org.observe.collect.impl;

import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.function.Consumer;

import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.Subscription;
import org.observe.collect.CollectionSession;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableElement;
import org.qommons.Transaction;

import com.google.common.reflect.TypeToken;

/**
 * An observable collection that wraps an observable collection and caches its elements. This can improve performance a great deal for
 * collections that are heavily derived. This class constitutes a memory leak if it is discarded without first calling
 * {@link #unsubscribe()} unless all of its sources are constant.
 *
 * @param <E> The type of element in the collection
 */
public abstract class CachingObservableCollection<E> implements ObservableCollection<E> {
	private final ObservableCollection<E> theWrapped;
	private final ObservableCollection<E> theCache;
	private final ObservableCollection<E> theImmutableCache;
	private Subscription theSubscription;

	/** @param wrap The collection to keep a cache of */
	public CachingObservableCollection(ObservableCollection<E> wrap) {
		theWrapped = wrap;
		theCache = createCacheCollection(wrap);
		theImmutableCache = theCache.immutable();
		theSubscription = sync(theWrapped, theCache);
	}

	/**
	 * @param wrap The collection to keep a cache of
	 * @return The collection that will hold the cached values
	 */
	protected abstract ObservableCollection<E> createCacheCollection(ObservableCollection<E> wrap);

	/**
	 * @param wrapped The collection to keep a cache of
	 * @param cached The collection that will hold the cached values
	 * @return The subscription to unsubscribe to release the listener on <code>wrapped</code>
	 */
	protected Subscription sync(ObservableCollection<E> wrapped, ObservableCollection<E> cached) {
		return wrapped.onElement(el -> {
			el.subscribe(new Observer<ObservableValueEvent<E>>() {
				@Override
				public <V extends ObservableValueEvent<E>> void onNext(V event) {
					if (event.isInitial())
						cached.add(event.getValue());
					else if (!Objects.equals(event.getOldValue(), event.getValue())) {
						cached.remove(event.getOldValue());
						cached.add(event.getValue());
					}
				}

				@Override
				public <V extends ObservableValueEvent<E>> void onCompleted(V event) {
					cached.remove(event.getValue());
				}
			});
		});
	}

	/** @return The collection that is supplying the values */
	protected ObservableCollection<E> getWrapped() {
		return theWrapped;
	}

	/** @return The cached elements */
	protected ObservableCollection<E> getCache() {
		return theImmutableCache;
	}

	/** Releases this caching collection's listener on its target */
	public synchronized void unsubscribe() {
		if (theSubscription != null) {
			theSubscription.unsubscribe();
			theSubscription = null;
		}
	}

	@Override
	public TypeToken<E> getType() {
		return theCache.getType();
	}

	@Override
	public ObservableValue<CollectionSession> getSession() {
		return theWrapped.getSession();
	}

	@Override
	public boolean isSafe() {
		return theCache.isSafe();
	}

	@Override
	public int size() {
		return theCache.size();
	}

	@Override
	public Subscription onElement(Consumer<? super ObservableElement<E>> onElement) {
		return theCache.onElement(onElement);
	}

	@Override
	public Iterator<E> iterator() {
		// Not supporting remove for now
		return theImmutableCache.iterator();
	}

	@Override
	public boolean canRemove(Object value) {
		return theWrapped.canRemove(value);
	}

	@Override
	public boolean canAdd(E value) {
		return theWrapped.canAdd(value);
	}

	@Override
	public boolean add(E e) {
		return theWrapped.add(e);
	}

	@Override
	public boolean remove(Object o) {
		return theWrapped.remove(o);
	}

	@Override
	public boolean addAll(Collection<? extends E> c) {
		return theWrapped.addAll(c);
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		return theWrapped.removeAll(c);
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		return theWrapped.retainAll(c);
	}

	@Override
	public void clear() {
		theWrapped.clear();
	}

	@Override
	public Transaction lock(boolean write, Object cause) {
		return theWrapped.lock(write, cause);
	}
}
