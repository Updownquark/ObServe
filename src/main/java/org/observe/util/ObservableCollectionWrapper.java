package org.observe.util;

import java.util.Collection;

import org.observe.ObservableValue;
import org.observe.Subscription;
import org.observe.collect.CollectionElement;
import org.observe.collect.CollectionSession;
import org.observe.collect.ElementSpliterator;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableElement;
import org.qommons.Transaction;
import org.qommons.value.Value;

import com.google.common.reflect.TypeToken;

/**
 * Wraps an observable set
 *
 * @param <E> The type of the set
 */
public class ObservableCollectionWrapper<E> implements ObservableCollection<E> {
	private final ObservableCollection<E> theWrapped;

	private final boolean isModifiable;

	/** @param wrap The collection to wrap */
	public ObservableCollectionWrapper(ObservableCollection<E> wrap) {
		this(wrap, true);
	}

	/**
	 * @param wrap The collection to wrap
	 * @param modifiable Whether this collection can propagate modifications to the wrapped collection. If false, this collection will be
	 *            immutable.
	 */
	public ObservableCollectionWrapper(ObservableCollection<E> wrap, boolean modifiable) {
		theWrapped = wrap;
		isModifiable = modifiable;
	}

	/** @return The collection that this wrapper wraps */
	protected ObservableCollection<E> getWrapped() {
		return theWrapped;
	}

	/** @return Whether this collection can be modified directly */
	protected boolean isModifiable() {
		return isModifiable;
	}

	@Override
	public TypeToken<E> getType() {
		return theWrapped.getType();
	}

	@Override
	public ObservableValue<CollectionSession> getSession() {
		return theWrapped.getSession();
	}

	@Override
	public Transaction lock(boolean write, Object cause) {
		return theWrapped.lock(write, cause);
	}

	@Override
	public boolean isSafe() {
		return theWrapped.isSafe();
	}

	@Override
	public Subscription onElement(java.util.function.Consumer<? super ObservableElement<E>> observer) {
		return theWrapped.onElement(observer);
	}

	@Override
	public int size() {
		return theWrapped.size();
	}

	@Override
	public boolean isEmpty() {
		return theWrapped.isEmpty();
	}

	@Override
	public boolean contains(Object o) {
		return theWrapped.contains(o);
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		return theWrapped.containsAll(c);
	}

	@Override
	public boolean containsAny(Collection<?> c) {
		return theWrapped.containsAny(c);
	}

	@Override
	public E [] toArray() {
		return theWrapped.toArray();
	}

	@Override
	public <T2> T2 [] toArray(T2 [] a) {
		return theWrapped.toArray(a);
	}

	@Override
	public ElementSpliterator<E> spliterator() {
		return new ElementSpliterator.WrappingSpliterator<>(theWrapped.spliterator(), theWrapped.getType(),
			() -> el -> new CollectionElement<E>() {
				@Override
				public TypeToken<E> getType() {
					return (TypeToken<E>) el.getType();
				}

				@Override
				public E get() {
					return el.get();
				}

				@Override
				public <V extends E> E set(V value, Object cause) throws IllegalArgumentException {
					assertModifiable();
					return ((CollectionElement<E>) el).set(value, cause);
				}

				@Override
				public <V extends E> String isAcceptable(V value) {
					if (!isModifiable())
						return ObservableCollection.StdMsg.UNSUPPORTED_OPERATION;
					return ((CollectionElement<E>) el).isAcceptable(value);
				}

				@Override
				public Value<String> isEnabled() {
					if (!isModifiable())
						return Value.constant(ObservableCollection.StdMsg.UNSUPPORTED_OPERATION);
					return el.isEnabled();
				}

				@Override
				public String canRemove() {
					if (!isModifiable())
						return ObservableCollection.StdMsg.UNSUPPORTED_OPERATION;
					return el.canRemove();
				}

				@Override
				public void remove() throws IllegalArgumentException {
					assertModifiable();
					el.remove();
				}
			});
	}

	/** Throws an {@link UnsupportedOperationException} if this collection is not {@link #isModifiable() modifiable} */
	protected void assertModifiable() {
		if(!isModifiable)
			throw new UnsupportedOperationException(org.qommons.collect.CollectionElement.StdMsg.UNSUPPORTED_OPERATION);
	}

	@Override
	public String canAdd(E value) {
		if (!isModifiable)
			return org.qommons.collect.CollectionElement.StdMsg.UNSUPPORTED_OPERATION;
		return theWrapped.canAdd(value);
	}

	@Override
	public boolean add(E e) {
		assertModifiable();
		return theWrapped.add(e);
	}

	@Override
	public boolean addAll(Collection<? extends E> c) {
		assertModifiable();
		return theWrapped.addAll(c);
	}

	@Override
	public ObservableCollection<E> addValues(E... values) {
		assertModifiable();
		theWrapped.addValues(values);
		return this;
	}

	@Override
	public String canRemove(Object value) {
		if (!isModifiable)
			return org.qommons.collect.CollectionElement.StdMsg.UNSUPPORTED_OPERATION;
		return theWrapped.canRemove(value);
	}

	@Override
	public boolean remove(Object o) {
		assertModifiable();
		return theWrapped.remove(o);
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		assertModifiable();
		return theWrapped.removeAll(c);
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		assertModifiable();
		return theWrapped.retainAll(c);
	}

	@Override
	public void clear() {
		assertModifiable();
		theWrapped.clear();
	}

	@Override
	public String toString() {
		return theWrapped.toString();
	}
}
