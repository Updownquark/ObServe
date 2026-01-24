package org.observe.collect;

import java.util.Collection;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import org.observe.Equivalence;
import org.observe.LightWeightObservable;
import org.observe.Observable.CoreChangeSources;
import org.qommons.Causable;
import org.qommons.CausalLock;
import org.qommons.Subscription;
import org.qommons.Identifiable.AbstractIdentifiable;
import org.qommons.Lockable.CoreId;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.ListElement;
import org.qommons.collect.ListenerList;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.collect.MutableListElement;
import org.qommons.collect.ValueStoredCollection;

/**
 * An {@link ObservableCollection} built on a {@link BetterList}, which should never be modified from outside
 *
 * @param <E> The type for the collection
 */
public class DefaultObservableCollection<E> extends AbstractIdentifiable implements ObservableCollection<E> {
	/** @return A builder to build a new ObservableCollection */
	public static <E> ObservableCollectionBuilder<E, ?> build() {
		return new ObservableCollectionBuilder.CollectionBuilderImpl<>("observable-collection");
	}

	private final BetterList<E> theValues;
	private final CausalLock theLock;
	private final LightWeightObservable<ObservableCollectionEvent<E>> theChanges;
	private final BiFunction<ElementId, BetterCollection<?>, BetterList<ElementId>> theElementsBySource;
	private final BiFunction<ElementId, BetterCollection<?>, BetterList<ElementId>> theSourceElements;
	private final Equivalence<? super E> theEquivalence;

	/** @param list The list to hold this collection's elements */
	public DefaultObservableCollection(BetterList<E> list) {
		this(list, null, null, null);
	}

	/**
	 * @param type The type for this collection
	 * @param list The list to hold this collection's elements
	 * @param elementsBySource The function to provide element sources for this collection
	 * @param sourceElements The function to provide source elements for elements in this collection
	 * @param equivalence The equivalence for the collection
	 * @see #getElementsBySource(ElementId, BetterCollection)
	 * @see #getSourceElements(ElementId, BetterCollection)
	 */
	DefaultObservableCollection(BetterList<E> list, //
		BiFunction<ElementId, BetterCollection<?>, BetterList<ElementId>> elementsBySource,
		BiFunction<ElementId, BetterCollection<?>, BetterList<ElementId>> sourceElements, //
		Equivalence<? super E> equivalence) {
		if (list instanceof ObservableCollection)
			throw new UnsupportedOperationException("The backing for an ObservableCollection cannot be observable");
		theValues = list;
		theLock = list;
		theChanges = new LightWeightObservable<>(ListenerList.build()//
			.reentrancyError(() -> ObservableCollection.REENTRANT_EVENT_ERROR + ": " + getIdentity().toString())//
			.forEachSafe(!theLock.getThreadConstraint().isDedicated())//
			.skipAddByDefault(true)//
			.build());
		theElementsBySource = elementsBySource;
		theSourceElements = sourceElements;
		theEquivalence = equivalence == null ? Equivalence.DEFAULT : equivalence;
	}

	/** @return This collection's backing values */
	protected BetterList<E> getValues() {
		return theValues;
	}

	@Override
	protected Object createIdentity() {
		return theValues.getIdentity();
	}

	@Override
	public DefaultObservableCollection<E> alias(String alias) {
		super.alias(alias);
		return this;
	}

	@Override
	public ThreadConstraint getThreadConstraint() {
		return theValues.getThreadConstraint();
	}

	@Override
	public boolean isEventing() {
		return theChanges.isEventing() || theLock.hasFinishingCauses();
	}

	@Override
	public boolean isLockSupported() {
		return theLock.isLockSupported();
	}

	@Override
	public Transaction lock(boolean write, Object cause) {
		return theLock.lock(write, cause);
	}

	@Override
	public Transaction tryLock(boolean write, Object cause) {
		return theLock.tryLock(write, cause);
	}

	@Override
	public CoreId getCoreId() {
		return theLock.getCoreId();
	}

	@Override
	public Collection<CausalLock.Cause> getCurrentCauses() {
		return theLock.getCurrentCauses();
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
	public ListElement<E> getElement(int index) {
		return theValues.getElement(index);
	}

	@Override
	public ListElement<E> getElement(E value, boolean first) {
		return theValues.getElement(value, first);
	}

	@Override
	public ListElement<E> getElement(ElementId id) {
		return theValues.getElement(id);
	}

	@Override
	public ListElement<E> getTerminalElement(boolean first) {
		return theValues.getTerminalElement(first);
	}

	@Override
	public MutableListElement<E> mutableElement(ElementId id) {
		return mutableElementFor(theValues.mutableElement(id));
	}

	@Override
	public BetterList<CollectionElement<E>> getElementsBySource(ElementId sourceEl, BetterCollection<?> sourceCollection) {
		if (sourceCollection == this)
			return BetterList.of(getElement(sourceEl));
		else if (theElementsBySource != null) {
			BetterList<ElementId> els = theElementsBySource.apply(sourceEl, sourceCollection);
			return els == null ? BetterList.empty() : BetterList.of(els.stream().map(this::getElement));
		} else
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
	public ListElement<E> addElement(E value, ElementId after, ElementId before, boolean first)
		throws UnsupportedOperationException, IllegalArgumentException {
		try (Transaction t = lock(true, null)) {
			ListElement<E> el = theValues.addElement(value, after, before, first);
			if (el == null)
				return null;
			if (theChanges.isAnyoneListening()) {
				ObservableCollectionEvent<E> event = ObservableCollectionEvent.createCollectionEvent(el.getElementId(),
					el.getElementsBefore(), CollectionChangeType.add, //
					null, value, theLock.getUnfinishedCauses());
				fire(event);
			} else
				theChanges.incrementStamp();
			return el;
		}
	}

	@Override
	public String canMove(ElementId valueEl, ElementId after, ElementId before) {
		return theValues.canMove(valueEl, after, before);
	}

	@Override
	public ListElement<E> move(ElementId valueEl, ElementId after, ElementId before, boolean first, Runnable afterRemove)
		throws UnsupportedOperationException, IllegalArgumentException {
		try (Transaction t = lock(true, null)) {
			ListElement<E> el;
			CollectionElementMove move = new CollectionElementMove();
			ListElement<E> targetEl = theValues.getElement(valueEl);
			E value = targetEl.get();
			try (Transaction moveT = lock(true, move)) {
				el = theValues.move(valueEl, after, before, first, () -> {
					if (theChanges.isAnyoneListening()) {
						ObservableCollectionEvent<E> event = ObservableCollectionEvent.createCollectionEvent(valueEl,
							targetEl.getElementsBefore(), CollectionChangeType.remove, value, value, theLock.getUnfinishedCauses());
						fire(event);
					} else
						theChanges.incrementStamp();
					if (afterRemove != null)
						afterRemove.run();
				});
				move.moved();
				if (el.getElementId().equals(valueEl))
					return getElement(valueEl);
				if (theChanges.isAnyoneListening()) {
					ObservableCollectionEvent<E> event = ObservableCollectionEvent.createCollectionEvent(el.getElementId(),
						el.getElementsBefore(), CollectionChangeType.add, null, value, theLock.getUnfinishedCauses());
					fire(event);
				} else
					theChanges.incrementStamp();
			}
			return el;
		}
	}

	@Override
	public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
		return theChanges.act(observer::accept);
	}

	@Override
	public void clear() {
		if (isEmpty())
			return;
		try (Transaction t = lock(true, null)) {
			CollectionElement<E> el = getTerminalElement(true);
			while (el != null) {
				MutableCollectionElement<E> mutable = mutableElement(el.getElementId());
				if (mutable.canRemove() == null)
					mutable.remove();
				el = el.getAdjacent(true);
			}
		}
	}

	@Override
	public void setValue(Collection<ElementId> elements, E value) {
		for (ElementId el : elements)
			mutableElement(el).set(value);
	}

	@Override
	public CoreChangeSources getChangeSources() {
		return theChanges.getChangeSources();
	}

	void fire(ObservableCollectionEvent<E> evt) {
		try (Transaction t = evt.use()) {
			theChanges.onNext(evt);
		}
	}

	private MutableListElement<E> mutableElementFor(MutableListElement<E> valueEl) {
		return new WrappedMutableElement(valueEl);
	}

	class WrappedMutableElement implements MutableListElement<E> {
		private final MutableListElement<E> theWrapped;

		WrappedMutableElement(MutableListElement<E> valueEl) {
			theWrapped = valueEl;
		}

		@Override
		public ElementId getElementId() {
			return theWrapped.getElementId();
		}

		@Override
		public E get() {
			return theWrapped.get();
		}

		@Override
		public int getElementsBefore() {
			return theWrapped.getElementsBefore();
		}

		@Override
		public int getElementsAfter() {
			return theWrapped.getElementsAfter();
		}

		@Override
		public MutableListElement<E> getAdjacent(boolean next) {
			MutableListElement<E> adj = theWrapped.getAdjacent(next);
			return adj == null ? null : new WrappedMutableElement(adj);
		}

		@Override
		public String isEnabled() {
			return theWrapped.isEnabled();
		}

		@Override
		public String isAcceptable(E value) {
			return theWrapped.isAcceptable(value);
		}

		@Override
		public void set(E value) throws UnsupportedOperationException, IllegalArgumentException {
			E old = get();
			if (value == old && theValues instanceof ValueStoredCollection) {
				// A pure update on a value-stored collection may mean that the value has changed such that it needs to be moved
				// Correct the storage structure
				boolean[] thisMoved = new boolean[1];
				RepairOperation op = new RepairOperation(theLock.getUnfinishedCauses());
				try (Transaction opT = op.use(); Transaction vt = lock(true, op)) {
					((ValueStoredCollection<E>) theValues).repair(theWrapped.getElementId(),
						new ValueStoredCollection.RepairListener<E, CollectionElementMove>() {
						@Override
						public CollectionElementMove removed(CollectionElement<E> element) {
							if (element.getElementId().equals(theWrapped.getElementId()))
								thisMoved[0] = true;
							CollectionElementMove move = new CollectionElementMove();
							if (theChanges.isAnyoneListening()) {
								fire(ObservableCollectionEvent.createCollectionEvent(element.getElementId(),
									((ListElement<E>) element).getElementsBefore(), CollectionChangeType.remove, element.get(),
									element.get(), op, move));
							} else
								theChanges.incrementStamp();
							return move;
						}

						@Override
						public void disposed(E oldValue, CollectionElementMove data) {
							data.moveFinished();
						}

						@Override
						public void transferred(CollectionElement<E> element, CollectionElementMove data) {
							data.moved();
							if (theChanges.isAnyoneListening()) {
								fire(ObservableCollectionEvent.createCollectionEvent(element.getElementId(),
									((ListElement<E>) element).getElementsBefore(), CollectionChangeType.add, null, element.get(), op,
									data));
							} else
								theChanges.incrementStamp();
						}
					});
				}
				if (thisMoved[0])
					return;
			}
			if (value == old && theChanges.isEventing())
				return; // Don't throw errors on recursive updates
			theWrapped.set(value);
			if (theChanges.isAnyoneListening()) {
				fire(ObservableCollectionEvent.createCollectionEvent(getElementId(), getElementsBefore(), CollectionChangeType.set, old,
					value, theLock.getUnfinishedCauses()));
			} else
				theChanges.incrementStamp();
		}

		@Override
		public String canRemove() {
			return theWrapped.canRemove();
		}

		@Override
		public void remove() throws UnsupportedOperationException {
			try (Transaction t = lock(true, null)) {
				E old = get();
				theWrapped.remove();
				if (theChanges.isAnyoneListening()) {
					fire(ObservableCollectionEvent.createCollectionEvent(getElementId(), getElementsBefore(), CollectionChangeType.remove,
						old, old, theLock.getUnfinishedCauses()));
				} else
					theChanges.incrementStamp();
			}
		}

		@Override
		public String toString() {
			return theWrapped.toString();
		}
	}

	/** A Causable representing a {@link ValueStoredCollection} repair operation */
	static class RepairOperation extends Causable.AbstractCausable {
		RepairOperation(Collection<?> causes) {
			super(causes);
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