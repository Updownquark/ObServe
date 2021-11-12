package org.observe.util;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.observe.Observable;
import org.observe.Subscription;
import org.observe.collect.DefaultObservableCollection;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollectionEvent;
import org.qommons.Identifiable;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterSortedList;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.ListenerList;
import org.qommons.collect.MutableCollectionElement;
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
	static class ElementRef<E> {
		ElementId sourceId;
		ElementId synthId;
		E value;

		ElementRef(ElementId sourceId, E value) {
			this.sourceId = sourceId;
			this.value = value;
		}

		@Override
		public String toString() {
			return String.valueOf(value);
		}
	}

	private final ObservableCollection<E> theCollection;
	private final BetterTreeList<ElementRef<E>> theSyntheticBacking;
	private final ObservableCollection<ElementRef<E>> theSyntheticCollection;
	private final BooleanSupplier isOnEventThread;
	private final Consumer<Runnable> theEventThreadExecutor;

	private final ListenerList<ObservableCollectionEvent<E>> theEventQueue;
	private Object theIdentity;

	/**
	 * @param collection The backing collection
	 * @param onEventThread A test that returns true only if the thread it is invoked from is acceptable for firing events directly
	 * @param eventThreadExec An executor to invoke events on an acceptable event thread
	 * @param until An observable which, when fired, will stop the eventing on this collection and release its resources and listeners
	 */
	public SafeObservableCollection(ObservableCollection<E> collection, BooleanSupplier onEventThread, Consumer<Runnable> eventThreadExec,
		Observable<?> until) {
		theCollection = collection;
		isOnEventThread = onEventThread;
		theEventThreadExecutor = eventThreadExec;

		theEventQueue = ListenerList.build().forEachSafe(false).withInUse(inUse -> {
			if (inUse)
				theEventThreadExecutor.accept(() -> _flush(true));
		}).build();
		theSyntheticBacking = new BetterTreeList<>(false);

		theSyntheticCollection = DefaultObservableCollection
			.build((TypeToken<ElementRef<E>>) (TypeToken<?>) TypeTokens.get().of(ElementRef.class))//
			.withBacking(theSyntheticBacking)//
			.withElementSource(this::_getElementSource)//
			.withSourceElements(this::_getSourceElements)//
			.build();

		theSyntheticCollection.onChange(evt -> {
			switch (evt.getType()) {
			case add:
				evt.getNewValue().synthId = evt.getElementId();
				break;
			default:
			}
		});
		boolean[] init = new boolean[1];
		Subscription collSub = theCollection.subscribe(evt -> {
			if (init[0] && (!theEventQueue.isEmpty() || !isOnEventThread.getAsBoolean()))
				theEventQueue.add((ObservableCollectionEvent<E>) evt, false);
			else
				eventOccurred((ObservableCollectionEvent<E>) evt);
		}, true);
		init[0] = true;
		if (until != null)
			until.take(1).act(__ -> collSub.unsubscribe());

		init(theSyntheticCollection.flow()//
			.map(theCollection.getType(), ref -> ref.value)// Allow caching so old and new values are consistent in update events
			.withEquivalence(theCollection.equivalence())//
			.collect());
		DebugData d = Debug.d().debug(collection);
		if (d.isActive())
			Debug.d().debug(this, true).merge(d);
	}

	@Override
	public Object getIdentity() {
		if (theIdentity == null)
			theIdentity = Identifiable.wrap(theCollection.getIdentity(), "safe");
		return theIdentity;
	}

	/**
	 * @return Whether changes have happened in the backing collection that have not yet been represented in this collection's state or
	 *         reported to this collection's listeners
	 */
	public boolean hasQueuedEvents() {
		return !theEventQueue.isEmpty();
	}

	private boolean isFlushing;

	/**
	 * Attempts to flush changes from the backing collection to this collection's state and listeners. May only be called on an acceptable
	 * event thread
	 */
	protected void flush() {
		if (!isOnEventThread.getAsBoolean())
			throw new IllegalStateException("Operations on this collection may only occur on the event thread");
		if (isFlushing)
			_flush(false);
	}

	private void _flush(boolean retryIfEmpty) {
		ListenerList.Element<ObservableCollectionEvent<E>> evt = theEventQueue.poll(0);
		if (retryIfEmpty && evt == null) {
			// Don't spin the CPU checking for events over and over
			QommonsTimer.getCommonInstance().execute(() -> _flush(true), Duration.ofMillis(10), Duration.ofDays(1), false).times(1).onEDT();
			// theEventThreadExecutor.accept(() -> _flush(true));
		}
		while (evt != null) {
			eventOccurred(evt.get());
			evt = theEventQueue.poll(0);
		}
	}

	private void eventOccurred(ObservableCollectionEvent<E> evt) {
		switch (evt.getType()) {
		case add:
			theSyntheticCollection.add(evt.getIndex(), new ElementRef<>(evt.getElementId(), evt.getNewValue()));
			break;
		case remove:
			theSyntheticCollection.remove(evt.getIndex());
			break;
		case set:
			CollectionElement<ElementRef<E>> refEl = theSyntheticCollection.getElement(evt.getIndex());
			refEl.get().value = evt.getNewValue();
			theSyntheticCollection.mutableElement(refEl.getElementId()).set(refEl.get());// Update event
			break;
		}
	}

	private ElementId _getElementSource(ElementId el, BetterCollection<?> sourceCollection) {
		if (sourceCollection == this)
			return el;
		BetterList<? extends CollectionElement<? extends E>> els = theCollection.getElementsBySource(el, sourceCollection);
		if (!els.isEmpty()) {
			flush();
			ElementId id = els.getFirst().getElementId();
			ElementRef<E> ref = theSyntheticBacking.search(r -> id.compareTo(r.sourceId), BetterSortedList.SortedSearchFilter.OnlyMatch)
				.get();
			return ref.synthId;
		}
		return null;
	}

	private BetterList<ElementId> _getSourceElements(ElementId localId, BetterCollection<?> collection) {
		flush();
		ElementRef<E> ref = theSyntheticCollection.getElement(localId).get();
		return theCollection.getSourceElements(ref.sourceId, collection);
	}

	@Override
	public MutableCollectionElement<E> mutableElement(ElementId id) {
		try (Transaction t = theCollection.lock(false, null)) {
			MutableCollectionElement<E> srcEl = theCollection.mutableElement(//
				theSyntheticBacking.getElement(id).get().sourceId);
			return new MutableCollectionElement<E>() {
				@Override
				public ElementId getElementId() {
					return id;
				}

				@Override
				public E get() {
					return srcEl.get();
				}

				@Override
				public BetterCollection<E> getCollection() {
					return SafeObservableCollection.this;
				}

				@Override
				public String isEnabled() {
					return srcEl.isEnabled();
				}

				@Override
				public String isAcceptable(E value) {
					return srcEl.isAcceptable(value);
				}

				@Override
				public void set(E value) throws UnsupportedOperationException, IllegalArgumentException {
					flush();
					srcEl.set(value);
				}

				@Override
				public String canRemove() {
					return srcEl.canRemove();
				}

				@Override
				public void remove() throws UnsupportedOperationException {
					flush();
					srcEl.remove();
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
			ElementRef<E> ref = theSyntheticBacking
				.search(r -> srcEl.getElementId().compareTo(r.sourceId), BetterSortedList.SortedSearchFilter.OnlyMatch).get();
			return getElement(ref.synthId);
		}
	}

	@Override
	public Transaction lock(boolean write, Object cause) {
		return theCollection.lock(write, cause);
	}

	@Override
	public Transaction tryLock(boolean write, Object cause) {
		return theCollection.tryLock(write, cause);
	}

	@Override
	public boolean isLockSupported() {
		return theCollection.isLockSupported();
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
