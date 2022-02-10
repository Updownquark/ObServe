package org.observe.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.observe.Observable;
import org.observe.Subscription;
import org.observe.collect.DefaultObservableCollection;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollectionBuilder;
import org.observe.collect.ObservableCollectionEvent;
import org.qommons.Identifiable;
import org.qommons.ThreadConstraint;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterSortedList;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.debug.Debug;
import org.qommons.debug.Debug.DebugData;
import org.qommons.tree.BetterTreeList;

import com.google.common.reflect.TypeToken;

/**
 * An {@link ObservableCollection} that only fires updates on a particular thread
 *
 * @param <E> The type of elements in the collection
 */
public abstract class AbstractSafeObservableCollection<E> extends ObservableCollectionWrapper<E> {
	/**
	 * Represents an element in a {@link AbstractSafeObservableCollection}, which may or may not also be present in the source collection
	 *
	 * @param <E> The type of values in the collection
	 */
	protected static class ElementRef<E> {
		private final ElementId sourceId;
		private ElementId synthId;
		private E value;

		/**
		 * @param sourceId The element ID in the source
		 * @param value The value
		 */
		protected ElementRef(ElementId sourceId, E value) {
			this.sourceId = sourceId;
			this.value = value;
		}

		/** @return The element ID that this element represents in the source collection */
		public ElementId getSourceId() {
			return sourceId;
		}

		/** @return The element ID of this element in the safe collection */
		public ElementId getSynthId() {
			return synthId;
		}

		void setSynthId(ElementId synthId) {
			this.synthId = synthId;
		}

		/** @return The value of the element */
		public E getValue() {
			return value;
		}

		/** @param value The new value for the element */
		public void setValue(E value) {
			this.value = value;
		}

		@Override
		public String toString() {
			return String.valueOf(value);
		}
	}

	/** The source collection whose data this safe collection represents */
	protected final ObservableCollection<E> theCollection;
	/**
	 * The backing collection of elements that are present in this safe collection, which were at one time and may still be present in the
	 * source collection
	 */
	protected final BetterTreeList<ElementRef<E>> theSyntheticBacking;
	/**
	 * The observable collection of elements that are present in this safe collection, which were at one time and may still be present in
	 * the source collection
	 */
	protected final ObservableCollection<ElementRef<E>> theSyntheticCollection;

	private final ThreadConstraint theThreadConstraint;

	private Object theIdentity;

	/**
	 * @param collection The backing collection
	 * @param threadConstraint The thread constraint for this collection
	 */
	public AbstractSafeObservableCollection(ObservableCollection<E> collection, ThreadConstraint threadConstraint) {
		theCollection = collection;
		theSyntheticBacking = BetterTreeList.<ElementRef<E>> build().withThreadConstraint(threadConstraint).build();
		theThreadConstraint = threadConstraint;

		ObservableCollectionBuilder<ElementRef<E>, ?> builder = DefaultObservableCollection
			.build((TypeToken<ElementRef<E>>) (TypeToken<?>) TypeTokens.get().of(ElementRef.class))//
			.withBacking(theSyntheticBacking);
		builder.withSourceElements(this::_getSourceElements).withElementSource(this::_getElementSource);
		theSyntheticCollection = builder.build();

		theSyntheticCollection.onChange(evt -> {
			switch (evt.getType()) {
			case add:
				initialize(evt.getNewValue(), evt.getElementId());
				break;
			default:
			}
		});

		DebugData d = Debug.d().debug(collection);
		if (d.isActive())
			Debug.d().debug(this, true).merge(d);
	}

	/**
	 * Initializes this collection's synchronization
	 *
	 * @param until An observable to cease synchronization
	 */
	protected void init(Observable<?> until) {
		try (Transaction t = theCollection.lock(false, null)) {
			init(theSyntheticCollection.flow()//
				.map(theCollection.getType(), ref -> ref.value)// Allow caching so old and new values are consistent in update events
				.withEquivalence(theCollection.equivalence())//
				.collect());

			boolean[] init = new boolean[] { true };
			Subscription collSub = theCollection.subscribe(evt -> handleEvent(evt, init[0]), true);
			init[0] = false;
			if (until != null)
				until.take(1).act(__ -> collSub.unsubscribe());
			if (hasQueuedEvents()) {
				if (theThreadConstraint.isEventThread())
					doFlush();
				else {
					do {
						try {
							Thread.sleep(20);
						} catch (InterruptedException e) {
						}
					} while (hasQueuedEvents());
				}
			}
		}
	}

	/**
	 * @return Whether changes have happened in the backing collection that have not yet been represented in this collection's state or
	 *         reported to this collection's listeners
	 */
	public abstract boolean hasQueuedEvents();

	/**
	 * @param evt The event that occurred in the source collection
	 * @param initial Whether the event is occurring during initialization of this safe collection (for elements that were already present
	 *        in the source upon creation of the safe collection)
	 */
	protected abstract void handleEvent(ObservableCollectionEvent<? extends E> evt, boolean initial);

	/**
	 * Flushes this collection's events to the event thread
	 * 
	 * @return Whether anything was flushed, or also if the state of changes prevented flushing from occurring (should try again)
	 */
	protected abstract boolean doFlush();

	@Override
	public ThreadConstraint getThreadConstraint() {
		return theThreadConstraint;
	}

	/** @return Whether events for this safe collection can be fired on the current thread */
	public boolean isOnEventThread() {
		return theThreadConstraint.isEventThread();
	}

	/**
	 * @param element The new element in this safe collection
	 * @param synthId The ID of the element in this collection
	 */
	protected void initialize(ElementRef<E> element, ElementId synthId) {
		element.setSynthId(synthId);
	}

	/**
	 * Creates a new element for this collection
	 * 
	 * @param sourceId The element ID in the source collection
	 * @param value The value in the source collection
	 * @return The new element
	 */
	protected ElementRef<E> createElement(ElementId sourceId, E value) {
		return new ElementRef<>(sourceId, value);
	}

	@Override
	public boolean isLockSupported() {
		return theCollection.isLockSupported();
	}

	@Override
	public Object getIdentity() {
		if (theIdentity == null)
			theIdentity = Identifiable.wrap(theCollection.getIdentity(), "safe");
		return theIdentity;
	}

	@Override
	public Transaction lock(boolean write, Object cause) {
		// Even though the synthetic collection doesn't provide any thread safety,
		// it needs to be locked here to provide causation continuity
		return Transactable.combine(theCollection, theSyntheticCollection).lock(write, cause);
	}

	@Override
	public Transaction tryLock(boolean write, Object cause) {
		// Even though the synthetic collection doesn't provide any thread safety,
		// it needs to be locked here to provide causation continuity
		return Transactable.combine(theCollection, theSyntheticCollection).tryLock(write, cause);
	}

	/**
	 * <p>
	 * Attempts to flush changes from the backing collection to this collection's state and listeners.
	 * </p>
	 * <p>
	 * <b>May throw an exception if called from an unacceptable thread.</b>
	 * </p>
	 */
	protected void flush() {
		if (!isOnEventThread())
			throw new IllegalStateException("Operations on this collection may only occur on "+theThreadConstraint);
		doFlush();
	}

	private ElementId _getElementSource(ElementId el, BetterCollection<?> sourceCollection) {
		if (sourceCollection == this)
			return el;
		try (Transaction t = theCollection.lock(false, null)) {
			BetterList<? extends CollectionElement<? extends E>> els = theCollection.getElementsBySource(el, sourceCollection);
			if (!els.isEmpty()) {
				flush();
				ElementId id = els.getFirst().getElementId();
				ElementRef<E> ref = theSyntheticBacking.search(r -> id.compareTo(r.sourceId), BetterSortedList.SortedSearchFilter.OnlyMatch)
					.get();
				return ref.synthId;
			}
		}
		return null;
	}

	private BetterList<ElementId> _getSourceElements(ElementId localId, BetterCollection<?> collection) {
		try (Transaction t = theCollection.lock(false, null)) {
			flush();
			ElementRef<E> ref = theSyntheticCollection.getElement(localId).get();
			return theCollection.getSourceElements(ref.sourceId, collection);
		}
	}

	@Override
	public MutableCollectionElement<E> mutableElement(ElementId id) {
		try (Transaction t = theCollection.lock(false, null)) {
			ElementRef<E> ref = theSyntheticBacking.getElement(id).get();
			MutableCollectionElement<E> srcEl = ref.sourceId.isPresent() ? theCollection.mutableElement(ref.sourceId) : null;
			return new MutableCollectionElement<E>() {
				@Override
				public ElementId getElementId() {
					return id;
				}

				@Override
				public E get() {
					return ref.getValue();
				}

				@Override
				public BetterCollection<E> getCollection() {
					return AbstractSafeObservableCollection.this;
				}

				@Override
				public String isEnabled() {
					if (srcEl == null || !srcEl.getElementId().isPresent())
						return StdMsg.ELEMENT_REMOVED;
					return srcEl.isEnabled();
				}

				@Override
				public String isAcceptable(E value) {
					if (srcEl == null || !srcEl.getElementId().isPresent())
						return StdMsg.ELEMENT_REMOVED;
					return srcEl.isAcceptable(value);
				}

				@Override
				public void set(E value) throws UnsupportedOperationException, IllegalArgumentException {
					if (srcEl == null || !srcEl.getElementId().isPresent())
						throw new UnsupportedOperationException(StdMsg.ELEMENT_REMOVED);
					srcEl.set(value);
					flush();
				}

				@Override
				public String canRemove() {
					return srcEl == null ? null : srcEl.canRemove();
				}

				@Override
				public void remove() throws UnsupportedOperationException {
					if (srcEl != null && srcEl.getElementId().isPresent())
						srcEl.remove();
					flush();
				}
			};
		}
	}

	@Override
	public String canAdd(E value, ElementId after, ElementId before) {
		try (Transaction t = theCollection.lock(false, null)) {
			flush();
			ElementId srcAfter = after == null ? null : theSyntheticBacking.getElement(after).get().sourceId;
			ElementId srcBefore = before == null ? null : theSyntheticBacking.getElement(before).get().sourceId;
			return theCollection.canAdd(value, srcAfter, srcBefore);
		}
	}

	@Override
	public CollectionElement<E> addElement(E value, ElementId after, ElementId before, boolean first)
		throws UnsupportedOperationException, IllegalArgumentException {
		try (Transaction t = theCollection.lock(true, null)) {
			flush();
			ElementId srcAfter = after == null ? null : theSyntheticBacking.getElement(after).get().sourceId;
			ElementId srcBefore = before == null ? null : theSyntheticBacking.getElement(before).get().sourceId;
			CollectionElement<E> srcEl = theCollection.addElement(value, srcAfter, srcBefore, first);
			if (srcEl == null)
				return null;
			doFlush();
			ElementRef<E> ref = theSyntheticBacking
				.search(r -> srcEl.getElementId().compareTo(r.sourceId), BetterSortedList.SortedSearchFilter.OnlyMatch).get();
			return getElement(ref.synthId);
		}
	}

	@Override
	public String canMove(ElementId valueEl, ElementId after, ElementId before) {
		try (Transaction t = theCollection.lock(false, null)) {
			flush();
			ElementId srcValue = theSyntheticBacking.getElement(valueEl).get().sourceId;
			ElementId srcAfter = after == null ? null : theSyntheticBacking.getElement(after).get().sourceId;
			ElementId srcBefore = before == null ? null : theSyntheticBacking.getElement(before).get().sourceId;
			return theCollection.canMove(srcValue, srcAfter, srcBefore);
		}
	}

	@Override
	public CollectionElement<E> move(ElementId valueEl, ElementId after, ElementId before, boolean first, Runnable afterRemove)
		throws UnsupportedOperationException, IllegalArgumentException {
		try (Transaction t = theCollection.lock(true, null)) {
			flush();
			ElementId srcValue = theSyntheticBacking.getElement(valueEl).get().sourceId;
			ElementId srcAfter = after == null ? null : theSyntheticBacking.getElement(after).get().sourceId;
			ElementId srcBefore = before == null ? null : theSyntheticBacking.getElement(before).get().sourceId;
			CollectionElement<E> srcEl = theCollection.move(srcValue, srcAfter, srcBefore, first, afterRemove);
			doFlush();
			ElementRef<E> ref = theSyntheticBacking
				.search(r -> srcEl.getElementId().compareTo(r.sourceId), BetterSortedList.SortedSearchFilter.OnlyMatch).get();
			return getElement(ref.synthId);
		}
	}

	@Override
	public void clear() {
		theCollection.clear();
	}

	@Override
	public void setValue(Collection<ElementId> elements, E value) {
		try (Transaction t = theCollection.lock(true, null)) {
			flush();
			List<ElementId> srcElements = new ArrayList<>(elements.size());
			for (ElementId el : elements)
				srcElements.add(theSyntheticBacking.getElement(el).get().sourceId);
			theCollection.setValue(srcElements, value);
		}
	}
}
