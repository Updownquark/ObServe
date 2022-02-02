package org.observe.util;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.observe.Observable;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollectionEvent;
import org.qommons.BreakpointHere;
import org.qommons.Causable;
import org.qommons.Causable.CausableKey;
import org.qommons.Transaction;
import org.qommons.collect.BetterSortedList.SortedSearchFilter;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.threading.QommonsTimer;

/**
 * <p>
 * This collection wraps another ObservableCollection and exposes the same data with the same capabilities, but this collection fires events
 * in a way that is optimized for UI displays.
 * </p>
 * <p>
 * All events from the source collection are "saved up" and when the root causable of the changes finishes, all changes are flushed, with
 * removes firing first, then set/updates, and finally additions. This maximizes performance of user interfaces listening to this
 * collection.
 * </p>
 * <p>
 * For long-running, offloaded processes, this class also runs a timer that flushes changes periodically.
 * </p>
 *
 * @param <E> The type of value in the collection
 */
public class UIOptimizedCollection<E> extends AbstractSafeObservableCollection<E> {
	protected static class UIElementRef<E> extends ElementRef<E> {
		UIElementRef<E> thePreviousPresentElement;
		UIElementRef<E> theNextPresentElement;
		boolean isChanged;
		boolean isRemoved;

		public UIElementRef(ElementId sourceId, E value) {
			super(sourceId, value);
		}

		public int compareBySynthOrder(UIElementRef<E> other) {
			return getSynthId().compareTo(other.getSynthId());
		}
	}

	private final Set<ElementId> theAddedElements;
	private final List<UIElementRef<E>> theRemovedElements;
	private final List<UIElementRef<E>> theChangedElements;
	private final QommonsTimer.TaskHandle thePeriodicFlushTask;
	private final CausableKey theFlushKey;
	private final AtomicBoolean isFlushing;

	public UIOptimizedCollection(ObservableCollection<E> collection, BooleanSupplier onEventThread, Consumer<Runnable> eventThreadExec,
		Observable<?> until) {
		super(collection, onEventThread);
		thePeriodicFlushTask = QommonsTimer.getCommonInstance().build(() -> {
			if (doFlush())
				scheduleFlush();
		}, Duration.ofMillis(500), false).withThreading((task, timer) -> {
			eventThreadExec.accept(task);
			return true;
		});
		theFlushKey = Causable.key((cause, values) -> eventThreadExec.accept(this::doFlush));
		isFlushing = new AtomicBoolean();
		theAddedElements = new HashSet<>();
		theRemovedElements = new ArrayList<>();
		theChangedElements = new ArrayList<>();
		init(until);
	}

	private void scheduleFlush() {
		// thePeriodicFlushTask.times(2).setActive(true); TODO
	}

	@Override
	public boolean hasQueuedEvents() {
		return !theAddedElements.isEmpty() || !theRemovedElements.isEmpty() || !theChangedElements.isEmpty();
	}

	@Override
	protected void handleEvent(ObservableCollectionEvent<? extends E> evt, boolean initial) {
		while (!isFlushing.compareAndSet(false, true)) {
			try {
				Thread.sleep(5);
			} catch (InterruptedException e) {
			}
		}
		doHandleEvent(evt);
		isFlushing.set(false);
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
			UIElementRef<E> found = findRef(evt.getElementId());
			found.isRemoved = true;
			theRemovedElements.add(found);
			UIElementRef<E> adj = found.thePreviousPresentElement;
			if (adj == null)
				adj = (UIElementRef<E>) theSyntheticBacking.getTerminalElement(true).get();
			while (adj != found) {
				adj.theNextPresentElement = found.theNextPresentElement;
				adj = (UIElementRef<E>) theSyntheticBacking.getAdjacentElement(adj.getSynthId(), true).get();
			}
			adj = found.theNextPresentElement;
			if (adj == null)
				adj = (UIElementRef<E>) theSyntheticBacking.getTerminalElement(false).get();
			while (adj != found) {
				adj.thePreviousPresentElement = found.thePreviousPresentElement;
				adj = (UIElementRef<E>) theSyntheticBacking.getAdjacentElement(adj.getSynthId(), false).get();
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

	private UIElementRef<E> findRef(ElementId sourceId) {
		// This method assumes it will find the element, because it can only be called for elements as they are removed or changed
		ElementId begin = theSyntheticBacking.getTerminalElement(true).getElementId();
		ElementId end = theSyntheticBacking.getTerminalElement(false).getElementId();
		CollectionElement<ElementRef<E>> node = theSyntheticBacking.getRoot();
		while (true) {
			UIElementRef<E> el = (UIElementRef<E>) node.get();
			int comp;
			// System.out.print(theSyntheticBacking.getElementsBefore(el.getSynthId()) + "(" + el.isRemoved + "): " + el.getSourceId());
			if (el.isRemoved) {
				if (el.thePreviousPresentElement != null) {
					comp = sourceId.compareTo(el.thePreviousPresentElement.getSourceId());
					if (comp == 0)
						return el.thePreviousPresentElement;
					else if (comp < 0)
						end = el.thePreviousPresentElement.getSynthId();
					else
						begin = el.getSynthId();
				} else {
					comp = sourceId.compareTo(el.theNextPresentElement.getSourceId());
					if (comp == 0)
						return el.theNextPresentElement;
					else if (comp > 0)
						begin = el.theNextPresentElement.getSynthId();
					else
						end = el.getSynthId();
				}
			} else {
				comp = sourceId.compareTo(el.getSourceId());
				if (comp == 0)
					return el;
				else if (comp < 0)
					end = el.getSynthId();
				else
					begin = el.getSynthId();
			}
			// System.out.println(": " + comp);
			node = theSyntheticBacking.splitBetween(begin, end);
			if (node == null) {
				UIElementRef<E> terminal = (UIElementRef<E>) theSyntheticBacking.getElement(comp < 0 ? begin : end).get();
				if (!terminal.getSourceId().equals(sourceId))
					BreakpointHere.breakpoint();
				return terminal;
			}
		}
	}

	@Override
	protected boolean doFlush() {
		while (!isFlushing.compareAndSet(false, true)) {
			try {
				Thread.sleep(5);
			} catch (InterruptedException e) {
			}
		}
		boolean flushed = false;
		try (Transaction t = theSyntheticCollection.lock(true, null)) {
			// First, the removals
			if (!theRemovedElements.isEmpty()) {
				flushed = true;
				for (UIElementRef<E> removedEl : theRemovedElements)
					theSyntheticCollection.mutableElement(removedEl.getSynthId()).remove();
				theRemovedElements.clear();
			}

			// Now set/updates
			if (!theChangedElements.isEmpty()) {
				flushed = true;
				for (UIElementRef<E> changedEl : theChangedElements) {
					if (changedEl.isRemoved)
						continue;
					changedEl.setValue(theCollection.getElement(changedEl.getSourceId()).get());
					theSyntheticCollection.mutableElement(changedEl.getSynthId()).set(changedEl);
					changedEl.isChanged = false;
				}
				theChangedElements.clear();
			}

			// Finally, additions
			if (!theAddedElements.isEmpty()) {
				flushed = true;
				for (ElementId addedEl : theAddedElements) {
					CollectionElement<ElementRef<E>> before = theSyntheticBacking.search(el -> addedEl.compareTo(el.getSourceId()),
						SortedSearchFilter.Greater);
					UIElementRef<E> newEl = createElement(addedEl, theCollection.getElement(addedEl).get());
					if (before == null)
						theSyntheticCollection.addElement(newEl, false);
					else
						theSyntheticCollection.addElement(newEl, null, before.get().getSynthId(), false);
				}
				theAddedElements.clear();
			}
		} finally {
			isFlushing.set(false);
		}
		return flushed;
	}

	@Override
	protected void initialize(ElementRef<E> element, ElementId synthId) {
		// This is only called when no removed elements are present
		super.initialize(element, synthId);
		UIElementRef<E> el = (UIElementRef<E>) element;
		el.thePreviousPresentElement = (UIElementRef<E>) CollectionElement.get(theSyntheticBacking.getAdjacentElement(synthId, false));
		if (el.thePreviousPresentElement != null)
			el.thePreviousPresentElement.theNextPresentElement = el;
		el.theNextPresentElement = (UIElementRef<E>) CollectionElement.get(theSyntheticBacking.getAdjacentElement(synthId, true));
		if (el.theNextPresentElement != null)
			el.theNextPresentElement.thePreviousPresentElement = el;
	}

	@Override
	protected UIElementRef<E> createElement(ElementId sourceId, E value) {
		return new UIElementRef<>(sourceId, value);
	}
}
