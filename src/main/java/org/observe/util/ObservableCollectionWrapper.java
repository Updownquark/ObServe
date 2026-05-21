package org.observe.util;

import java.util.Collection;
import java.util.function.Consumer;

import org.observe.Equivalence;
import org.observe.Observable.CoreChangeSources;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollectionEvent;
import org.qommons.Identifiable.AbstractIdentifiable;
import org.qommons.Subscription;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.ListElement;
import org.qommons.collect.MutableListElement;

/**
 * An ObservableCollection that simply delegates to another
 *
 * @param <E> The type of the collection
 */
public abstract class ObservableCollectionWrapper<E> extends AbstractIdentifiable implements ObservableCollection<E> {
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
	protected Object createIdentity() {
		return getWrapped().getIdentity();
	}

	@Override
	public ObservableCollectionWrapper<E> alias(String alias) {
		super.alias(alias);
		return this;
	}

	@Override
	public ListElement<E> getElement(int index) {
		return getWrapped().getElement(index);
	}

	@Override
	public boolean isContentControlled() {
		return getWrapped().isContentControlled();
	}

	@Override
	public long getStamp() {
		return getWrapped().getStamp();
	}

	@Override
	public ListElement<E> getElement(E value, boolean first) {
		return getWrapped().getElement(value, first);
	}

	@Override
	public ListElement<E> getElement(ElementId id) {
		return getWrapped().getElement(id);
	}

	@Override
	public ListElement<E> getTerminalElement(boolean first) {
		return getWrapped().getTerminalElement(first);
	}

	@Override
	public MutableListElement<E> mutableElement(ElementId id) {
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
	public ListElement<E> addElement(E value, ElementId after, ElementId before, boolean first)
		throws UnsupportedOperationException, IllegalArgumentException {
		return getWrapped().addElement(value, after, before, first);
	}

	@Override
	public String canMove(ElementId valueEl, ElementId after, ElementId before) {
		return getWrapped().canMove(valueEl, after, before);
	}

	@Override
	public ListElement<E> move(ElementId valueEl, ElementId after, ElementId before, boolean first, Runnable afterRemove)
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
	public ThreadConstraint getThreadConstraint() {
		return getWrapped().getThreadConstraint();
	}

	@Override
	public boolean isEventing() {
		return getWrapped().isEventing();
	}

	@Override
	public Transaction lock(boolean tryOnly) {
		return getWrapped().lock(tryOnly);
	}

	@Override
	public Transaction lockWrite(boolean tryOnly, Object cause) {
		return getWrapped().lockWrite(tryOnly, cause);
	}

	@Override
	public Collection<Cause> getCurrentCauses() {
		return getWrapped().getCurrentCauses();
	}

	@Override
	public CoreId getCoreId() {
		return getWrapped().getCoreId();
	}

	@Override
	public CoreChangeSources getChangeSources() {
		return getWrapped().getChangeSources();
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
