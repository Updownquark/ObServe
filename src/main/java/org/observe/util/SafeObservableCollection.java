package org.observe.util;

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
import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterSortedList;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.ListenerList;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.tree.BetterTreeList;

import com.google.common.reflect.TypeToken;

public class SafeObservableCollection<E> extends ObservableCollectionWrapper<E> {
	static class ElementRef<E> {
		ElementId sourceId;
		ElementId synthId;
		E value;

		ElementRef(ElementId sourceId, E value) {
			this.sourceId = sourceId;
			this.value = value;
		}
	}

	private final ObservableCollection<E> theCollection;
	private final BetterTreeList<ElementRef<E>> theSyntheticBacking;
	private final ObservableCollection<ElementRef<E>> theSyntheticCollection;
	private final BooleanSupplier isOnEventThread;
	private final Consumer<Runnable> theEventThreadExecutor;

	private final ListenerList<ObservableCollectionEvent<E>> theEventQueue;

	public SafeObservableCollection(ObservableCollection<E> collection, BooleanSupplier onEventThread,
		Consumer<Runnable> eventThreadExec, Observable<?> until) {
		theCollection = collection;
		isOnEventThread = onEventThread;
		theEventThreadExecutor = eventThreadExec;

		theEventQueue = ListenerList.build().allowReentrant().forEachSafe(false).withInUse(inUse -> {
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
	}

	private boolean isFlushing;

	protected void flush() {
		if (!isOnEventThread.getAsBoolean())
			throw new IllegalStateException("Operations on this collection may only occur on the event thread");
		if (isFlushing)
			_flush(false);
	}

	private void _flush(boolean retryIfEmpty) {
		ListenerList.Element<ObservableCollectionEvent<E>> evt = theEventQueue.poll(0);
		if (retryIfEmpty && evt == null)
			theEventThreadExecutor.accept(() -> _flush(true));
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

	private ElementId _getElementSource(ElementId el) {
		BetterList<? extends CollectionElement<? extends E>> els = theCollection.getElementsBySource(el);
		if (!els.isEmpty()) {
			flush();
			ElementId id = els.getFirst().getElementId();
			ElementRef<E> ref = theSyntheticBacking.search(r -> id.compareTo(r.sourceId), BetterSortedList.SortedSearchFilter.OnlyMatch).get();
			return ref.synthId;
		}
		return null;
	}

	private BetterList<ElementId> _getSourceElements(ElementId localId, BetterCollection<?> collection) {
		flush();
		return theCollection.getSourceElements(localId, collection);
	}

	@Override
	public MutableCollectionElement<E> mutableElement(ElementId id) {
		try (Transaction t = theCollection.lock(false, null)) {
			flush();
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
					srcEl.set(value);
				}

				@Override
				public String canRemove() {
					return srcEl.canRemove();
				}

				@Override
				public void remove() throws UnsupportedOperationException {
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
			ElementRef<E> ref = theSyntheticBacking.search(r -> srcEl.getElementId().compareTo(r.sourceId), BetterSortedList.SortedSearchFilter.OnlyMatch)
				.get();
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
