package org.observe.collect;

import java.util.Collection;
import java.util.LinkedList;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import org.observe.Equivalence;
import org.observe.Subscription;
import org.qommons.Causable;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.collect.ValueStoredCollection;

import com.google.common.reflect.TypeToken;

/**
 * An {@link ObservableCollection} built on a {@link BetterList}, which should never be modified from outside
 *
 * @param <E> The type for the collection
 */
public class DefaultObservableCollection<E> implements ObservableCollection<E> {
	/**
	 * @param type The type for the new collection
	 * @return A builder to build a new ObservableCollection
	 */
	public static <E> ObservableCollectionBuilder<E, ?> build(TypeToken<E> type) {
		return new ObservableCollectionBuilder.CollectionBuilderImpl<>(type, "observable-collection");
	}

	private final TypeToken<E> theType;
	private final LinkedList<Causable> theTransactionCauses;
	private final BetterList<E> theValues;
	private final org.qommons.collect.ListenerList<Consumer<? super ObservableCollectionEvent<? extends E>>> theObservers;
	private final BiFunction<ElementId, BetterCollection<?>, ElementId> theElementSource;
	private final BiFunction<ElementId, BetterCollection<?>, BetterList<ElementId>> theSourceElements;
	private final Equivalence<? super E> theEquivalence;

	/**
	 * @param type The type for this collection
	 * @param list The list to hold this collection's elements
	 */
	public DefaultObservableCollection(TypeToken<E> type, BetterList<E> list) {
		this(type, list, null, null, null);
	}

	/**
	 * @param type The type for this collection
	 * @param list The list to hold this collection's elements
	 * @param elementSource The function to provide element sources for this collection
	 * @param sourceElements The function to provide source elements for elements in this collection
	 * @param equivalence The equivalence for the collection
	 * @see #getElementsBySource(ElementId, BetterCollection)
	 * @see #getSourceElements(ElementId, BetterCollection)
	 */
	public DefaultObservableCollection(TypeToken<E> type, BetterList<E> list, //
		BiFunction<ElementId, BetterCollection<?>, ElementId> elementSource,
		BiFunction<ElementId, BetterCollection<?>, BetterList<ElementId>> sourceElements, //
		Equivalence<? super E> equivalence) {
		theType = type;
		if (list instanceof ObservableCollection)
			throw new UnsupportedOperationException("The backing for an ObservableCollection cannot be observable is not supported here");
		theTransactionCauses = new LinkedList<>();
		theValues = list;
		theObservers = new org.qommons.collect.ListenerList<>(ObservableCollection.REENTRANT_EVENT_ERROR);
		theElementSource = elementSource;
		theSourceElements = sourceElements;
		theEquivalence = equivalence == null ? Equivalence.DEFAULT : equivalence;
	}

	/** @return This collection's backing values */
	protected BetterList<E> getValues() {
		return theValues;
	}

	@Override
	public Object getIdentity() {
		return theValues.getIdentity();
	}

	@Override
	public boolean isLockSupported() {
		return theValues.isLockSupported();
	}

	@Override
	public Transaction lock(boolean write, Object cause) {
		Transaction t = theValues.lock(write, cause);
		return addCause(t, write, cause);
	}

	private Transaction addCause(Transaction valueLock, boolean write, Object cause) {
		Causable tCause;
		Transaction causeFinish;
		if (cause == null && !theTransactionCauses.isEmpty()) {
			causeFinish = null;
			tCause = null;
		} else if (cause instanceof Causable) {
			causeFinish = null;
			tCause = (Causable) cause;
		} else {
			tCause = Causable.simpleCause(cause);
			causeFinish = tCause.use();
		}
		if (write && tCause != null)
			theTransactionCauses.add(tCause);
		return new Transaction() {
			private boolean isClosed;

			@Override
			public void close() {
				if (isClosed)
					return;
				isClosed = true;
				if (causeFinish != null)
					causeFinish.close();
				if (write && tCause != null)
					theTransactionCauses.removeLastOccurrence(tCause);
				valueLock.close();
			}
		};
	}

	@Override
	public Transaction tryLock(boolean write, Object cause) {
		Transaction t = theValues.tryLock(write, cause);
		return t == null ? null : addCause(t, write, cause);
	}

	Causable getCurrentCause() {
		return theTransactionCauses.peekFirst();
	}

	@Override
	public long getStamp() {
		return theValues.getStamp();
	}

	@Override
	public boolean isContentControlled() {
		return theValues.isContentControlled();
	}

	@Override
	public TypeToken<E> getType() {
		return theType;
	}

	@Override
	public Equivalence<? super E> equivalence() {
		return theEquivalence;
	}

	@Override
	public int size() {
		return theValues.size();
	}

	@Override
	public boolean isEmpty() {
		return theValues.isEmpty();
	}

	@Override
	public int getElementsBefore(ElementId id) {
		return theValues.getElementsBefore(id);
	}

	@Override
	public int getElementsAfter(ElementId id) {
		return theValues.getElementsAfter(id);
	}

	@Override
	public CollectionElement<E> getElement(int index) {
		return theValues.getElement(index);
	}

	@Override
	public CollectionElement<E> getElement(E value, boolean first) {
		return theValues.getElement(value, first);
	}

	@Override
	public CollectionElement<E> getElement(ElementId id) {
		return theValues.getElement(id);
	}

	@Override
	public CollectionElement<E> getTerminalElement(boolean first) {
		return theValues.getTerminalElement(first);
	}

	@Override
	public CollectionElement<E> getAdjacentElement(ElementId elementId, boolean next) {
		return theValues.getAdjacentElement(elementId, next);
	}

	@Override
	public MutableCollectionElement<E> mutableElement(ElementId id) {
		return mutableElementFor(theValues.mutableElement(id));
	}

	@Override
	public BetterList<CollectionElement<E>> getElementsBySource(ElementId sourceEl, BetterCollection<?> sourceCollection) {
		if (sourceCollection == this)
			return BetterList.of(getElement(sourceEl));
		BetterList<CollectionElement<E>> els = theValues.getElementsBySource(sourceEl, sourceCollection);
		if (!els.isEmpty())
			return els;
		if (theElementSource != null) {
			ElementId el = theElementSource.apply(sourceEl, sourceCollection);
			if (el == null)
				return BetterList.empty();
			return BetterList.of(getElement(el));
		}
		return BetterList.empty();
	}

	@Override
	public BetterList<ElementId> getSourceElements(ElementId localElement, BetterCollection<?> sourceCollection) {
		if (sourceCollection == this)
			return theValues.getSourceElements(localElement, theValues); // Validate element
		else if (theSourceElements != null)
			return theSourceElements.apply(localElement, sourceCollection);
		return theValues.getSourceElements(localElement, sourceCollection);
	}

	@Override
	public ElementId getEquivalentElement(ElementId equivalentEl) {
		return theValues.getEquivalentElement(equivalentEl);
	}

	@Override
	public String canAdd(E value, ElementId after, ElementId before) {
		return theValues.canAdd(value, after, before);
	}

	@Override
	public CollectionElement<E> addElement(E value, ElementId after, ElementId before, boolean first)
		throws UnsupportedOperationException, IllegalArgumentException {
		try (Transaction t = lock(true, null)) {
			CollectionElement<E> el = theValues.addElement(value, after, before, first);
			if (el == null)
				return null;
			ObservableCollectionEvent<E> event = new ObservableCollectionEvent<>(el.getElementId(), getType(),
				theValues.getElementsBefore(el.getElementId()), CollectionChangeType.add, null, value, getCurrentCause());
			fire(event);
			return el;
		}
	}

	@Override
	public String canMove(ElementId valueEl, ElementId after, ElementId before) {
		return theValues.canMove(valueEl, after, before);
	}

	@Override
	public CollectionElement<E> move(ElementId valueEl, ElementId after, ElementId before, boolean first, Runnable afterRemove)
		throws UnsupportedOperationException, IllegalArgumentException {
		try (Transaction t = lock(true, null)) {
			E value = theValues.getElement(valueEl).get();
			CollectionElement<E> el = theValues.move(valueEl, after, before, first, () -> {
				ObservableCollectionEvent<E> event = new ObservableCollectionEvent<>(valueEl, getType(),
					theValues.getElementsBefore(valueEl), CollectionChangeType.remove, value, value, getCurrentCause());
				fire(event);
				if (afterRemove != null)
					afterRemove.run();
			});
			if (el.getElementId().equals(valueEl))
				return getElement(valueEl);
			ObservableCollectionEvent<E> event = new ObservableCollectionEvent<>(el.getElementId(), getType(),
				theValues.getElementsBefore(el.getElementId()), CollectionChangeType.add, null, value, getCurrentCause());
			fire(event);
			return el;
		}
	}

	@Override
	public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
		return theObservers.add(observer, true)::run;
	}

	@Override
	public void clear() {
		try (Transaction t = lock(true, null)) {
			CollectionElement<E> el = getTerminalElement(true);
			while (el != null) {
				MutableCollectionElement<E> mutable = mutableElement(el.getElementId());
				if (mutable.canRemove() == null)
					mutable.remove();
				el = getAdjacentElement(el.getElementId(), true);
			}
		}
	}

	@Override
	public void setValue(Collection<ElementId> elements, E value) {
		for (ElementId el : elements)
			mutableElement(el).set(value);
	}

	void fire(ObservableCollectionEvent<E> evt) {
		try (Transaction t = evt.use()) {
			theObservers.forEach(//
				listener -> listener.accept(evt));
		}
	}

	private MutableCollectionElement<E> mutableElementFor(MutableCollectionElement<E> valueEl) {
		return new MutableCollectionElement<E>() {
			@Override
			public BetterCollection<E> getCollection() {
				return DefaultObservableCollection.this;
			}

			@Override
			public ElementId getElementId() {
				return valueEl.getElementId();
			}

			@Override
			public E get() {
				return valueEl.get();
			}

			@Override
			public String isEnabled() {
				return valueEl.isEnabled();
			}

			@Override
			public String isAcceptable(E value) {
				return valueEl.isAcceptable(value);
			}

			@Override
			public void set(E value) throws UnsupportedOperationException, IllegalArgumentException {
				E old = get();
				if (value == old && theValues instanceof ValueStoredCollection) {
					// A pure update on a value-stored collection may mean that the value has changed such that it needs to be moved
					// Correct the storage structure
					boolean[] thisMoved = new boolean[1];
					RepairOperation op = new RepairOperation(getCurrentCause());
					try (Transaction opT = op.use(); Transaction vt = lock(true, op)) {
						((ValueStoredCollection<E>) theValues).repair(valueEl.getElementId(),
							new ValueStoredCollection.RepairListener<E, Void>() {
							@Override
							public Void removed(CollectionElement<E> element) {
								if (element.getElementId().equals(valueEl.getElementId()))
									thisMoved[0] = true;
								fire(new ObservableCollectionEvent<>(element.getElementId(), getType(),
									theValues.getElementsBefore(element.getElementId()), CollectionChangeType.remove, element.get(),
									element.get(), op));
								return null;
							}

							@Override
							public void disposed(E oldValue, Void data) {}

							@Override
							public void transferred(CollectionElement<E> element, Void data) {
								fire(new ObservableCollectionEvent<>(element.getElementId(), getType(),
									theValues.getElementsBefore(element.getElementId()), CollectionChangeType.add, null, element.get(),
									op));
							}
						});
					}
					if (thisMoved[0])
						return;
				}
				valueEl.set(value);
				fire(new ObservableCollectionEvent<>(getElementId(), getType(), getElementsBefore(getElementId()), CollectionChangeType.set,
					old, value, getCurrentCause()));
			}

			@Override
			public String canRemove() {
				return valueEl.canRemove();
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				try (Transaction t = lock(true, null)) {
					E old = get();
					valueEl.remove();
					fire(new ObservableCollectionEvent<>(getElementId(), getType(), getElementsBefore(getElementId()),
						CollectionChangeType.remove, old, old, getCurrentCause()));
				}
			}

			@Override
			public String toString() {
				return valueEl.toString();
			}
		};
	}

	/** A Causable representing a {@link ValueStoredCollection} repair operation */
	static class RepairOperation extends Causable.AbstractCausable {
		RepairOperation(Object cause) {
			super(cause);
		}
	}

	@Override
	public int hashCode() {
		return theValues.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return theValues.equals(obj);
	}

	@Override
	public String toString() {
		return theValues.toString();
	}
}