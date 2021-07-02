package org.observe.util;

import java.util.Collection;
import java.util.function.Consumer;

import org.observe.Equivalence;
import org.observe.Subscription;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollectionEvent;
import org.qommons.Lockable.CoreId;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.MutableCollectionElement;

import com.google.common.reflect.TypeToken;

/**
 * An ObservableCollection that simply delegates to another
 *
 * @param <E> The type of the collection
 */
public abstract class ObservableCollectionWrapper<E> implements ObservableCollection<E> {
	private ObservableCollection<E> theWrapped;

	/**
	 * Initializes this collection with the collection to delegate to
	 *
	 * @param wrapped The wrapped collection to delegate to
	 * @throws IllegalStateException If this collection has already been initialized
	 */
	protected void init(ObservableCollection<E> wrapped) throws IllegalStateException {
		if (theWrapped != null)
			throw new IllegalStateException("This wrapper is already initialized");
		theWrapped = wrapped;
	}

	/**
	 * @return The collection this collection delegates to
	 * @throws IllegalStateException If this collection has not been {@link #init(ObservableCollection) initialized}
	 */
	protected ObservableCollection<E> getWrapped() throws IllegalStateException {
		if (theWrapped == null)
			throw new IllegalStateException("This wrapper has not been initialized");
		return theWrapped;
	}

	@Override
	public Object getIdentity() {
		return getWrapped().getIdentity();
	}

	@Override
	public CollectionElement<E> getElement(int index) {
		return getWrapped().getElement(index);
	}

	@Override
	public boolean isContentControlled() {
		return getWrapped().isContentControlled();
	}

	@Override
	public int getElementsBefore(ElementId id) {
		return getWrapped().getElementsBefore(id);
	}

	@Override
	public int getElementsAfter(ElementId id) {
		return getWrapped().getElementsAfter(id);
	}

	@Override
	public long getStamp() {
		return getWrapped().getStamp();
	}

	@Override
	public CollectionElement<E> getElement(E value, boolean first) {
		return getWrapped().getElement(value, first);
	}

	@Override
	public CollectionElement<E> getElement(ElementId id) {
		return getWrapped().getElement(id);
	}

	@Override
	public CollectionElement<E> getTerminalElement(boolean first) {
		return getWrapped().getTerminalElement(first);
	}

	@Override
	public CollectionElement<E> getAdjacentElement(ElementId elementId, boolean next) {
		return getWrapped().getAdjacentElement(elementId, next);
	}

	@Override
	public MutableCollectionElement<E> mutableElement(ElementId id) {
		return getWrapped().mutableElement(id);
	}

	@Override
	public BetterList<CollectionElement<E>> getElementsBySource(ElementId sourceEl, BetterCollection<?> sourceCollection) {
		if (sourceCollection == this)
			return BetterList.of(getElement(sourceEl));
		return getWrapped().getElementsBySource(sourceEl, sourceCollection);
	}

	@Override
	public BetterList<ElementId> getSourceElements(ElementId localElement, BetterCollection<?> sourceCollection) {
		if (sourceCollection == this)
			return getWrapped().getSourceElements(localElement, getWrapped());
		return getWrapped().getSourceElements(localElement, sourceCollection);
	}

	@Override
	public ElementId getEquivalentElement(ElementId equivalentEl) {
		return getWrapped().getEquivalentElement(equivalentEl);
	}

	@Override
	public String canAdd(E value, ElementId after, ElementId before) {
		return getWrapped().canAdd(value, after, before);
	}

	@Override
	public CollectionElement<E> addElement(E value, ElementId after, ElementId before, boolean first)
		throws UnsupportedOperationException, IllegalArgumentException {
		return getWrapped().addElement(value, after, before, first);
	}

	@Override
	public String canMove(ElementId valueEl, ElementId after, ElementId before) {
		return getWrapped().canMove(valueEl, after, before);
	}

	@Override
	public CollectionElement<E> move(ElementId valueEl, ElementId after, ElementId before, boolean first, Runnable afterRemove)
		throws UnsupportedOperationException, IllegalArgumentException {
		return getWrapped().move(valueEl, after, before, first, afterRemove);
	}

	@Override
	public int size() {
		return getWrapped().size();
	}

	@Override
	public boolean isEmpty() {
		return getWrapped().isEmpty();
	}

	@Override
	public Transaction lock(boolean write, Object cause) {
		return getWrapped().lock(write, cause);
	}

	@Override
	public Transaction tryLock(boolean write, Object cause) {
		return getWrapped().tryLock(write, cause);
	}

	@Override
	public CoreId getCoreId() {
		return getWrapped().getCoreId();
	}

	@Override
	public TypeToken<E> getType() {
		return getWrapped().getType();
	}

	@Override
	public boolean isLockSupported() {
		return getWrapped().isLockSupported();
	}

	@Override
	public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
		return getWrapped().onChange(observer);
	}

	@Override
	public void clear() {
		getWrapped().clear();
	}

	@Override
	public Equivalence<? super E> equivalence() {
		return getWrapped().equivalence();
	}

	@Override
	public void setValue(Collection<ElementId> elements, E value) {
		getWrapped().setValue(elements, value);
	}

	@Override
	public int hashCode() {
		return getWrapped().hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return getWrapped().equals(obj);
	}

	@Override
	public String toString() {
		return getWrapped().toString();
	}
}
