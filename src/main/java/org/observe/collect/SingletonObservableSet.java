package org.observe.collect;

import java.util.Collection;
import java.util.NoSuchElementException;
import java.util.function.Consumer;

import org.observe.Equivalence;
import org.observe.Observable;
import org.observe.SettableValue;
import org.observe.Subscription;
import org.qommons.Lockable.CoreId;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.collect.MutableCollectionElement.StdMsg;

import com.google.common.reflect.TypeToken;

public class SingletonObservableSet<T> implements ObservableSet<T> {
	private final SettableValue<T> theValue;
	private final ElementId theId;
	private final CollectionElement<T> theElement;
	private final MutableCollectionElement<T> theMutableElement;

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
	public CoreId getCoreId() {
		return theValue.getCoreId();
	}

	@Override
	public boolean isEventing() {
		return theValue.isEventing();
	}

	@Override
	public Object getIdentity() {
		return theValue.getIdentity();
	}

	@Override
	public long getStamp() {
		return theValue.getStamp();
	}

	@Override
	public TypeToken<T> getType() {
		return theValue.getType();
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
	public CollectionElement<T> getElement(int index) throws IndexOutOfBoundsException {
		if (index == 0)
			return theElement;
		throw new IndexOutOfBoundsException(index + " of 1");
	}

	@Override
	public boolean isContentControlled() {
		return true;
	}

	@Override
	public int getElementsBefore(ElementId id) {
		if (theId == id)
			return 0;
		throw new NoSuchElementException();
	}

	@Override
	public int getElementsAfter(ElementId id) {
		if (theId == id)
			return 0;
		throw new NoSuchElementException();
	}

	@Override
	public CollectionElement<T> getElement(T value, boolean first) {
		if (equivalence().elementEquals(theValue.get(), value))
			return theElement;
		return null;
	}

	@Override
	public CollectionElement<T> getElement(ElementId id) {
		if (id == theId)
			return theElement;
		throw new NoSuchElementException();
	}

	@Override
	public CollectionElement<T> getTerminalElement(boolean first) {
		return theElement;
	}

	@Override
	public CollectionElement<T> getAdjacentElement(ElementId elementId, boolean next) {
		return null;
	}

	@Override
	public MutableCollectionElement<T> mutableElement(ElementId id) {
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
	public CollectionElement<T> addElement(T value, ElementId after, ElementId before, boolean first)
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
	public CollectionElement<T> move(ElementId valueEl, ElementId after, ElementId before, boolean first, Runnable afterRemove)
		throws UnsupportedOperationException, IllegalArgumentException {
		return theElement;
	}

	@Override
	public CollectionElement<T> getOrAdd(T value, ElementId after, ElementId before, boolean first, Runnable preAdd, Runnable postAdd) {
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
			ObservableCollectionEvent<T> oce = new ObservableCollectionEvent<>(theId, 0, CollectionChangeType.set, evt.getOldValue(),
				evt.getNewValue(), evt);
			try (Transaction t = oce.use()) {
				observer.accept(oce);
			}
		});
	}

	@Override
	public SingletonObservableSet<T> safe(ThreadConstraint threading, Observable<?> until) {
		return new SingletonObservableSet<>(theValue.safe(threading, until));
	}

	class ValueElement implements CollectionElement<T> {
		@Override
		public ElementId getElementId() {
			return theId;
		}

		@Override
		public T get() {
			return theValue.get();
		}

		@Override
		public String toString() {
			return theId.toString();
		}
	}

	class MutableValueElement extends ValueElement implements MutableCollectionElement<T> {
		@Override
		public BetterCollection<T> getCollection() {
			return SingletonObservableSet.this;
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
