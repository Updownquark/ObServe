package org.observe.util;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.observe.Observable;
import org.observe.Subscription;
import org.observe.collect.DefaultObservableCollection;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollectionBuilder;
import org.observe.collect.ObservableCollectionEvent;
import org.qommons.Causable;
import org.qommons.Causable.CausableKey;
import org.qommons.Identifiable;
import org.qommons.ThreadConstraint;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterSortedList;
import org.qommons.collect.BetterSortedList.SortedSearchFilter;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.collect.ReentrantNotificationException;
import org.qommons.debug.Debug;
import org.qommons.debug.Debug.DebugData;
import org.qommons.threading.QommonsTimer;
import org.qommons.tree.BetterTreeList;

import com.google.common.reflect.TypeToken;

/**
 * An {@link ObservableCollection} that only fires updates on a particular thread
 *
 * @param <E> The type of elements in the collection
 */
public class SafeObservableCollection<E> extends ObservableCollectionWrapper<E> {
	/**
	 * Represents an element in a {@link SafeObservableCollection}, which may or may not also be present in the source collection
	 *
	 * @param <E> The type of values in the collection
	 */
	protected static class ElementRef<E> {
		final ElementId sourceId;
		ElementId synthId;
		ElementRef<E> thePreviousPresentElement;
		ElementRef<E> theNextPresentElement;
		boolean isChanged;
		boolean isRemoved;
		private E value;

		/**
		 * @param sourceId The element ID in the source
		 * @param value The value
		 */
		protected ElementRef(ElementId sourceId, E value) {
			this.sourceId = sourceId;
			this.value = value;
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

	private final Set<ElementId> theAddedElements;
	private final List<ElementRef<E>> theRemovedElements;
	private final List<ElementRef<E>> theChangedElements;
	private final QommonsTimer.TaskHandle thePeriodicFlushTask;
	private final CausableKey theFlushKey;
	private final AtomicBoolean theFlushLock;
	private boolean isFlushing;
	private long theStamp;


	/**
	 * @param collection The backing collection
	 * @param threading The thread constraint for this collection
	 * @param until The observable to cease this collection's synchronization
	 */
	public SafeObservableCollection(ObservableCollection<E> collection, ThreadConstraint threading, Observable<?> until) {
		if (!threading.supportsInvoke())
			throw new IllegalArgumentException("Thread constraints for safe structures must be invokable");
		theCollection = collection;
		theSyntheticBacking = BetterTreeList.<ElementRef<E>> build().withThreadConstraint(threading).build();
		theThreadConstraint = threading;

		ObservableCollectionBuilder<ElementRef<E>, ?> builder = DefaultObservableCollection
			.build((TypeToken<ElementRef<E>>) (TypeToken<?>) TypeTokens.get().of(ElementRef.class))//
			.withBacking(theSyntheticBacking);
		builder.withSourceElements(this::_getSourceElements).withElementsBySource(this::_getElementsBySource);
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

		thePeriodicFlushTask = QommonsTimer.getCommonInstance().build(() -> {
			if (doFlush())
				scheduleFlush();
		}, Duration.ofMillis(500), false).withThreading((task, timer) -> {
			threading.invoke(task);
			return true;
		});
		theFlushKey = Causable.key((cause, values) -> {
			if (threading.isEventThread())
				doFlush();
			else
				threading.invoke(this::doFlush);
		});
		theFlushLock = new AtomicBoolean();
		theAddedElements = new HashSet<>();
		theRemovedElements = new ArrayList<>();
		theChangedElements = new ArrayList<>();
		try (Transaction t = collection.lock(false, null)) {
			theStamp = theCollection.getStamp();
			init(until);
		}
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

	private void scheduleFlush() {
		thePeriodicFlushTask.times(2).setActive(true);
	}

	@Override
	public long getStamp() {
		return theStamp;
	}

	/**
	 * @return Whether changes have happened in the backing collection that have not yet been represented in this collection's state or
	 *         reported to this collection's listeners
	 */
	private boolean hasQueuedEvents() {
		return !theAddedElements.isEmpty() || !theRemovedElements.isEmpty() || !theChangedElements.isEmpty();
	}

	/**
	 * @param evt The event that occurred in the source collection
	 * @param initial Whether the event is occurring during initialization of this safe collection (for elements that were already present
	 *        in the source upon creation of the safe collection)
	 */
	protected void handleEvent(ObservableCollectionEvent<? extends E> evt, boolean initial) {
		if (!theFlushLock.compareAndSet(false, true)) {
			if (isOnEventThread())
				throw new ReentrantNotificationException(REENTRANT_EVENT_ERROR);
			do {
				try {
					Thread.sleep(5);
				} catch (InterruptedException e) {
				}
			} while (!theFlushLock.compareAndSet(false, true));
		}
		try {
			doHandleEvent(evt);
		} finally {
			theFlushLock.set(false);
		}
		evt.getRootCausable().onFinish(theFlushKey);
		scheduleFlush();
	}

	private void doHandleEvent(ObservableCollectionEvent<? extends E> evt) {
		switch (evt.getType()) {
		case add:
			theAddedElements.add(evt.getElementId());
			break;
		case remove:
			if (theAddedElements.remove(evt.getElementId()))
				return;
			ElementRef<E> found = findRef(evt.getElementId());
			found.isRemoved = true;
			theRemovedElements.add(found);
			ElementRef<E> adj = found.thePreviousPresentElement;
			if (adj == null)
				adj = theSyntheticBacking.getTerminalElement(true).get();
			while (adj != found) {
				adj.theNextPresentElement = found.theNextPresentElement;
				adj = theSyntheticBacking.getAdjacentElement(adj.getSynthId(), true).get();
			}
			adj = found.theNextPresentElement;
			if (adj == null)
				adj = theSyntheticBacking.getTerminalElement(false).get();
			while (adj != found) {
				adj.thePreviousPresentElement = found.thePreviousPresentElement;
				adj = theSyntheticBacking.getAdjacentElement(adj.getSynthId(), false).get();
			}
			break;
		case set:
			if (theAddedElements.contains(evt.getElementId()))
				return;
			found = findRef(evt.getElementId());
			if (!found.isChanged) {
				found.isChanged = true;
				theChangedElements.add(found);
			}
			break;
		}
	}

	/**
	 * Flushes this collection's events to the event thread
	 *
	 * @return Whether anything was flushed, or also if the state of changes prevented flushing from occurring (should try again)
	 */
	protected boolean doFlush() {
		while (!theFlushLock.compareAndSet(false, true)) {
			if (isFlushing)
				return false;
			try {
				Thread.sleep(5);
			} catch (InterruptedException e) {
			}
		}
		isFlushing = true;
		boolean flushed = false;
		try (Transaction t = theSyntheticCollection.lock(true, null)) {
			// First, the removals
			if (!theRemovedElements.isEmpty()) {
				flushed = true;
				for (ElementRef<E> removedEl : theRemovedElements)
					theSyntheticCollection.mutableElement(removedEl.getSynthId()).remove();
				theRemovedElements.clear();
			}

			// Now set/updates
			if (!theChangedElements.isEmpty()) {
				flushed = true;
				for (ElementRef<E> changedEl : theChangedElements) {
					if (changedEl.isRemoved)
						continue;
					changedEl.setValue(theCollection.getElement(changedEl.sourceId).get());
					theSyntheticCollection.mutableElement(changedEl.getSynthId()).set(changedEl);
					changedEl.isChanged = false;
				}
				theChangedElements.clear();
			}

			// Finally, additions
			if (!theAddedElements.isEmpty()) {
				flushed = true;
				for (ElementId addedEl : theAddedElements) {
					CollectionElement<ElementRef<E>> before = theSyntheticBacking.search(el -> addedEl.compareTo(el.sourceId),
						SortedSearchFilter.Greater);
					ElementRef<E> newEl = createElement(addedEl, theCollection.getElement(addedEl).get());
					if (before == null)
						theSyntheticCollection.addElement(newEl, false);
					else
						theSyntheticCollection.addElement(newEl, null, before.get().getSynthId(), false);
				}
				theAddedElements.clear();
			}
			theStamp = theCollection.getStamp();
		} finally {
			isFlushing = false;
			theFlushLock.set(false);
		}
		return flushed;
	}

	private ElementRef<E> findRef(ElementId sourceId) {
		ElementId begin = theSyntheticBacking.getTerminalElement(true).getElementId();
		ElementId end = theSyntheticBacking.getTerminalElement(false).getElementId();
		CollectionElement<ElementRef<E>> node = theSyntheticBacking.getRoot();
		while (node != null) {
			ElementRef<E> el = node.get();
			int comp;
			if (el.isRemoved) {
				if (el.thePreviousPresentElement != null) {
					comp = sourceId.compareTo(el.thePreviousPresentElement.sourceId);
					if (comp == 0)
						return el.thePreviousPresentElement;
					else if (comp < 0)
						end = el.thePreviousPresentElement.getSynthId();
					else
						begin = el.getSynthId();
				} else if (el.theNextPresentElement != null) {
					comp = sourceId.compareTo(el.theNextPresentElement.sourceId);
					if (comp == 0)
						return el.theNextPresentElement;
					else if (comp > 0)
						begin = el.theNextPresentElement.getSynthId();
					else
						end = el.getSynthId();
				} else
					return null;
			} else {
				comp = sourceId.compareTo(el.sourceId);
				if (comp == 0)
					return el;
				else if (comp < 0)
					end = el.getSynthId();
				else
					begin = el.getSynthId();
			}
			node = theSyntheticBacking.splitBetween(begin, end);
			if (node == null) {
				node = theSyntheticBacking.getElement(comp < 0 ? begin : end);
				if (sourceId.equals(node.get().sourceId))
					return node.get();
				else
					return null;
			}
		}
		return null;
	}

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
		// This is only called when no removed elements are present
		element.setSynthId(synthId);
		element.thePreviousPresentElement = CollectionElement.get(theSyntheticBacking.getAdjacentElement(synthId, false));
		if (element.thePreviousPresentElement != null)
			element.thePreviousPresentElement.theNextPresentElement = element;
		element.theNextPresentElement = CollectionElement.get(theSyntheticBacking.getAdjacentElement(synthId, true));
		if (element.theNextPresentElement != null)
			element.theNextPresentElement.thePreviousPresentElement = element;
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
		Transaction t = Transactable.combine(theCollection, theSyntheticCollection).lock(write, cause);
		// In the case of a read lock on the event thread, this would not be safe if theSyntheticCollection actually performed locking,
		// because we obtained a read lock on it which is not generally upgradable to a write lock that is needed by the flush.
		// As it is, the collection's only constraint is that it is modified on the event thread, which is met.
		if (write || theThreadConstraint.isEventThread())
			doFlush();
		return t;
	}

	@Override
	public Transaction tryLock(boolean write, Object cause) {
		Transaction t = Transactable.combine(theCollection, theSyntheticCollection).tryLock(write, cause);
		if (t != null && (write || theThreadConstraint.isEventThread()))
			doFlush();
		return t;
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

	private BetterList<ElementId> _getElementsBySource(ElementId el, BetterCollection<?> sourceCollection) {
		if (sourceCollection == this)
			return BetterList.of(el);
		try (Transaction t = lock(false, null)) {
			BetterList<? extends CollectionElement<? extends E>> els = theCollection.getElementsBySource(el, sourceCollection);
			return BetterList
				.of(els.stream().map(srcEl -> findRef(srcEl.getElementId())).filter(ref -> ref != null).map(ElementRef::getSynthId));
		}
	}

	private BetterList<ElementId> _getSourceElements(ElementId localId, BetterCollection<?> collection) {
		if (collection == this)
			return BetterList.of(localId);
		try (Transaction t = lock(false, null)) {
			ElementRef<E> ref = theSyntheticCollection.getElement(localId).get();
			// The source element may have been removed.
			// Can't decide at the moment whether I should check that and return empty or let it throw the exception
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
					return SafeObservableCollection.this;
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
					try (Transaction t2 = lock(true, null)) {
						if (srcEl == null || !srcEl.getElementId().isPresent())
							throw new UnsupportedOperationException(StdMsg.ELEMENT_REMOVED);
						srcEl.set(value);
						doFlush();
					}
				}

				@Override
				public String canRemove() {
					return srcEl == null ? null : srcEl.canRemove();
				}

				@Override
				public void remove() throws UnsupportedOperationException {
					try (Transaction t2 = lock(true, null)) {
						if (srcEl != null && srcEl.getElementId().isPresent())
							srcEl.remove();
						doFlush();
					}
				}
			};
		}
	}

	@Override
	public String canAdd(E value, ElementId after, ElementId before) {
		try (Transaction t = lock(false, null)) {
			ElementId srcAfter = after == null ? null : theSyntheticBacking.getElement(after).get().sourceId;
			if (srcAfter != null && !srcAfter.isPresent())
				return StdMsg.ELEMENT_REMOVED;
			ElementId srcBefore = before == null ? null : theSyntheticBacking.getElement(before).get().sourceId;
			if (srcBefore != null && !srcBefore.isPresent())
				return StdMsg.ELEMENT_REMOVED;
			return theCollection.canAdd(value, srcAfter, srcBefore);
		}
	}

	@Override
	public CollectionElement<E> addElement(E value, ElementId after, ElementId before, boolean first)
		throws UnsupportedOperationException, IllegalArgumentException {
		try (Transaction t = lock(true, null)) {
			doFlush();
			ElementId srcAfter = after == null ? null : theSyntheticBacking.getElement(after).get().sourceId;
			if (srcAfter != null && !srcAfter.isPresent())
				throw new IllegalArgumentException(StdMsg.ELEMENT_REMOVED);
			ElementId srcBefore = before == null ? null : theSyntheticBacking.getElement(before).get().sourceId;
			if (srcBefore != null && !srcBefore.isPresent())
				throw new IllegalArgumentException(StdMsg.ELEMENT_REMOVED);
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
		try (Transaction t = lock(false, null)) {
			flush();
			ElementId srcValue = theSyntheticBacking.getElement(valueEl).get().sourceId;
			if (!srcValue.isPresent())
				return StdMsg.ELEMENT_REMOVED;
			ElementId srcAfter = after == null ? null : theSyntheticBacking.getElement(after).get().sourceId;
			if (srcAfter != null && !srcAfter.isPresent())
				return StdMsg.ELEMENT_REMOVED;
			ElementId srcBefore = before == null ? null : theSyntheticBacking.getElement(before).get().sourceId;
			if (srcBefore != null && !srcBefore.isPresent())
				return StdMsg.ELEMENT_REMOVED;
			return theCollection.canMove(srcValue, srcAfter, srcBefore);
		}
	}

	@Override
	public CollectionElement<E> move(ElementId valueEl, ElementId after, ElementId before, boolean first, Runnable afterRemove)
		throws UnsupportedOperationException, IllegalArgumentException {
		try (Transaction t = lock(true, null)) {
			ElementId srcValue = theSyntheticBacking.getElement(valueEl).get().sourceId;
			if (!srcValue.isPresent())
				throw new IllegalArgumentException(StdMsg.ELEMENT_REMOVED);
			ElementId srcAfter = after == null ? null : theSyntheticBacking.getElement(after).get().sourceId;
			if (srcAfter != null && !srcAfter.isPresent())
				throw new IllegalArgumentException(StdMsg.ELEMENT_REMOVED);
			ElementId srcBefore = before == null ? null : theSyntheticBacking.getElement(before).get().sourceId;
			if (srcBefore != null && !srcBefore.isPresent())
				throw new IllegalArgumentException(StdMsg.ELEMENT_REMOVED);
			CollectionElement<E> srcEl = theCollection.move(srcValue, srcAfter, srcBefore, first, afterRemove);
			doFlush();
			ElementRef<E> ref = theSyntheticBacking
				.search(r -> srcEl.getElementId().compareTo(r.sourceId), BetterSortedList.SortedSearchFilter.OnlyMatch).get();
			return getElement(ref.synthId);
		}
	}

	@Override
	public void clear() {
		try (Transaction t = lock(true, null)) {
			theCollection.clear();
			doFlush();
		}
	}

	@Override
	public void setValue(Collection<ElementId> elements, E value) {
		try (Transaction t = lock(true, null)) {
			List<ElementId> srcElements = new ArrayList<>(elements.size());
			for (ElementId el : elements)
				srcElements.add(theSyntheticBacking.getElement(el).get().sourceId);
			theCollection.setValue(srcElements, value);
			doFlush();
		}
	}
}
