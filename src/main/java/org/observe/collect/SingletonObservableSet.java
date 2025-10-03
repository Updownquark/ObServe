package org.observe.collect;

import java.util.Collection;
import java.util.NoSuchElementException;
import java.util.function.Consumer;

import org.observe.Equivalence;
import org.observe.Observable;
import org.observe.Observable.CoreChangeSources;
import org.observe.SettableValue;
import org.observe.Subscription;
import org.qommons.Identifiable.AbstractIdentifiable;
import org.qommons.Lockable.CoreId;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.ListElement;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.collect.MutableListElement;

/**
 * An {@link ObservableSet} whose size is always 1 and whose only element is backed by a {@link SettableValue}
 *
 * @param <T> The type of value in the collection
 */
public class SingletonObservableSet<T> extends AbstractIdentifiable implements ObservableSet<T> {
	private final SettableValue<T> theValue;
	private final ElementId theId;
	private final ListElement<T> theElement;
	private final MutableListElement<T> theMutableElement;

	/**
	 * @param value The value for the set's element
	 */
	public SingletonObservableSet(SettableValue<T> value) {
		theValue = value;
		theId = new ElementId() {
			@Override
			public int compareTo(ElementId o) {
				return 0;
			}

			@Override
			public boolean isPresent() {
				return true;
			}

			@Override
			public String toString() {
				return theValue.toString();
			}
		};
		theElement = new ValueElement();
		theMutableElement = new MutableValueElement();
	}

	/** @return The value backing this set's element */
	public SettableValue<T> getValue() {
		return theValue;
	}

	@Override
	public ThreadConstraint getThreadConstraint() {
		return theValue.getThreadConstraint();
	}

	@Override
	public Transaction lock(boolean write, Object cause) {
		return theValue.lock(write, cause);
	}

	@Override
	public Transaction tryLock(boolean write, Object cause) {
		return theValue.tryLock(write, cause);
	}

	@Override
	public Collection<Cause> getCurrentCauses() {
		return theValue.getCurrentCauses();
	}

	@Override
	public CoreId getCoreId() {
		return theValue.getCoreId();
	}

	@Override
	public CoreChangeSources getChangeSources() {
		return theValue.noInitChanges().getChangeSources();
	}

	@Override
	public boolean isEventing() {
		return theValue.isEventing();
	}

	@Override
	protected Object createIdentity() {
		return theValue.getIdentity();
	}

	@Override
	public SingletonObservableSet<T> alias(String alias) {
		super.alias(alias);
		return this;
	}

	@Override
	public long getStamp() {
		return theValue.getStamp();
	}

	@Override
	public boolean isLockSupported() {
		return theValue.isLockSupported();
	}

	@Override
	public void clear() {
	}

	@Override
	public Equivalence<? super T> equivalence() {
		return Equivalence.DEFAULT;
	}

	@Override
	public void setValue(Collection<ElementId> elements, T value) {
		boolean hasId = false;
		for (ElementId el : elements) {
			if (theId != el)
				throw new NoSuchElementException();
			hasId = true;
		}
		if (hasId)
			theValue.set(value, null);
	}

	@Override
	public ListElement<T> getElement(int index) throws IndexOutOfBoundsException {
		if (index == 0)
			return theElement;
		throw new IndexOutOfBoundsException(index + " of 1");
	}

	@Override
	public boolean isContentControlled() {
		return true;
	}

	@Override
	public ListElement<T> getElement(T value, boolean first) {
		if (equivalence().elementEquals(theValue.get(), value))
			return theElement;
		return null;
	}

	@Override
	public ListElement<T> getElement(ElementId id) {
		if (id == theId)
			return theElement;
		throw new NoSuchElementException();
	}

	@Override
	public ListElement<T> getTerminalElement(boolean first) {
		return theElement;
	}

	@Override
	public MutableListElement<T> mutableElement(ElementId id) {
		if (id == theId)
			return theMutableElement;
		throw new NoSuchElementException();
	}

	@Override
	public BetterList<CollectionElement<T>> getElementsBySource(ElementId sourceEl, BetterCollection<?> sourceCollection) {
		if (sourceCollection == this) {
			if (theId == sourceEl)
				return BetterList.of(theElement);
			else
				throw new NoSuchElementException();
		} else
			return BetterList.empty();
	}

	@Override
	public BetterList<ElementId> getSourceElements(ElementId localElement, BetterCollection<?> sourceCollection) {
		if (sourceCollection == this) {
			if (theId == localElement)
				return BetterList.of(theId);
			else
				throw new NoSuchElementException();
		} else
			return BetterList.empty();
	}

	@Override
	public ElementId getEquivalentElement(ElementId equivalentEl) {
		if (equivalentEl == theId)
			return theId;
		return null;
	}

	@Override
	public String canAdd(T value, ElementId after, ElementId before) {
		if (equivalence().elementEquals(theValue.get(), value))
			return null;
		return StdMsg.UNSUPPORTED_OPERATION;
	}

	@Override
	public ListElement<T> addElement(T value, ElementId after, ElementId before, boolean first)
		throws UnsupportedOperationException, IllegalArgumentException {
		if (equivalence().elementEquals(theValue.get(), value))
			return null;
		throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
	}

	@Override
	public String canMove(ElementId valueEl, ElementId after, ElementId before) {
		return null;
	}

	@Override
	public ListElement<T> move(ElementId valueEl, ElementId after, ElementId before, boolean first, Runnable afterRemove)
		throws UnsupportedOperationException, IllegalArgumentException {
		return theElement;
	}

	@Override
	public ListElement<T> getOrAdd(T value, ElementId after, ElementId before, boolean first, Runnable preAdd, Runnable postAdd) {
		if (equivalence().elementEquals(theValue.get(), value))
			return theElement;
		throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
	}

	@Override
	public boolean isConsistent(ElementId element) {
		return true;
	}

	@Override
	public boolean checkConsistency() {
		return false;
	}

	@Override
	public <X> boolean repair(ElementId element, RepairListener<T, X> listener) {
		return false;
	}

	@Override
	public <X> boolean repair(RepairListener<T, X> listener) {
		return false;
	}

	@Override
	public int size() {
		return 1;
	}

	@Override
	public boolean isEmpty() {
		return false;
	}

	@Override
	public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends T>> observer) {
		return theValue.noInitChanges().act(evt -> {
			ObservableCollectionEvent<T> oce = ObservableCollectionEvent.createCollectionEvent(theId, 0, CollectionChangeType.set,
				evt.getOldValue(), evt.getNewValue(), evt);
			try (Transaction t = oce.use()) {
				observer.accept(oce);
			}
		});
	}

	@Override
	public SingletonObservableSet<T> safe(ThreadConstraint threading, Observable<?> until) {
		return new SingletonObservableSet<>(theValue.safe(threading));
	}

	class ValueElement implements ListElement<T> {
		@Override
		public ElementId getElementId() {
			return theId;
		}

		@Override
		public T get() {
			return theValue.get();
		}

		@Override
		public ListElement<T> getAdjacent(boolean next) {
			return null;
		}

		@Override
		public int getElementsBefore() {
			return 0;
		}

		@Override
		public int getElementsAfter() {
			return 0;
		}

		@Override
		public String toString() {
			return theId.toString();
		}
	}

	class MutableValueElement extends ValueElement implements MutableListElement<T> {
		@Override
		public MutableListElement<T> getAdjacent(boolean next) {
			return null;
		}

		@Override
		public String isEnabled() {
			return theValue.isEnabled().get();
		}

		@Override
		public String isAcceptable(T value) {
			return theValue.isAcceptable(value);
		}

		@Override
		public void set(T value) throws UnsupportedOperationException, IllegalArgumentException {
			theValue.set(value, null);
		}

		@Override
		public String canRemove() {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public void remove() throws UnsupportedOperationException {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}
	}
}
