package org.observe.collect;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.function.Consumer;

import org.observe.Subscription;
import org.qommons.Causable;
import org.qommons.Transaction;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.collect.MutableElementSpliterator;
import org.qommons.collect.SimpleCause;

import com.google.common.reflect.TypeToken;

/**
 * An {@link ObservableCollection} built on a {@link BetterList}, which should never be modified from outside
 *
 * @param <E> The type for the collection
 */
public class DefaultObservableCollection<E> implements ObservableCollection<E> {
	private final TypeToken<E> theType;
	private final LinkedList<Causable> theTransactionCauses;
	private final BetterList<E> theValues;
	private org.qommons.collect.ListenerList<Consumer<? super ObservableCollectionEvent<? extends E>>> theObservers;

	/**
	 * @param type The type for this collection
	 * @param list The list to hold this collection's elements
	 */
	public DefaultObservableCollection(TypeToken<E> type, BetterList<E> list) {
		theType = type;
		if (list instanceof ObservableCollection)
			throw new UnsupportedOperationException("ObservableCollection is not supported here");
		theTransactionCauses = new LinkedList<>();
		theValues = list;
		theObservers = new org.qommons.collect.ListenerList<>();
	}

	@Override
	public boolean isLockSupported() {
		return theValues.isLockSupported();
	}

	@Override
	public Transaction lock(boolean write, boolean structural, Object cause) {
		Transaction t = theValues.lock(write, structural, cause);
		Causable tCause;
		if (cause == null && !theTransactionCauses.isEmpty())
			tCause = null;
		else if (cause instanceof Causable)
			tCause = (Causable) cause;
		else
			tCause = new SimpleCause(cause);
		if (write && tCause != null)
			theTransactionCauses.add(tCause);
		return new Transaction() {
			private boolean isClosed;

			@Override
			public void close() {
				if (isClosed)
					return;
				isClosed = true;
				if (write && tCause != null)
					theTransactionCauses.removeLastOccurrence(tCause);
				t.close();
			}
		};
	}

	@Override
	public long getStamp(boolean structuralOnly) {
		return theValues.getStamp(structuralOnly);
	}

	@Override
	public TypeToken<E> getType() {
		return theType;
	}

	@Override
	public Equivalence<? super E> equivalence() {
		return Equivalence.DEFAULT;
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
	public MutableCollectionElement<E> mutableElement(ElementId id) {
		return mutableElementFor(theValues.mutableElement(id));
	}

	@Override
	public MutableElementSpliterator<E> spliterator(ElementId element, boolean asNext) {
		return new DefaultMutableSpliterator(theValues.spliterator(element, asNext));
	}

	@Override
	public String canAdd(E value) {
		return theValues.canAdd(value);
	}

	@Override
	public CollectionElement<E> addElement(E e, boolean first) {
		try (Transaction t = lock(true, null)) {
			CollectionElement<E> el = theValues.addElement(e, first);
			if (el == null)
				return null;
			fire(new ObservableCollectionEvent<>(el.getElementId(), getType(), theValues.getElementsBefore(el.getElementId()),
				CollectionChangeType.add, null, e, theTransactionCauses.peekLast()));
			return el;
		}
	}

	@Override
	public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
		try (Transaction t = lock(true, false, null)) {
			return theObservers.add(observer)::run;
		}
	}

	@Override
	public void clear() {
		try (Transaction t = lock(true, null)) {
			theValues.spliterator().forEachElementM(el -> {
				ObservableCollectionEvent<E> evt = new ObservableCollectionEvent<>(el.getElementId(), getType(), 0,
					CollectionChangeType.remove, el.get(), el.get(), theTransactionCauses.peekLast());
				el.remove();
				fire(evt);
			}, true);
		}
	}

	private void fire(ObservableCollectionEvent<E> evt) {
		theObservers.forEach(//
			listener -> listener.accept(evt));
	}

	@Override
	public MutableElementSpliterator<E> spliterator(boolean fromStart) {
		return new DefaultMutableSpliterator(theValues.spliterator(fromStart));
	}

	private MutableCollectionElement<E> mutableElementFor(MutableCollectionElement<E> valueEl) {
		return new MutableCollectionElement<E>() {
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
				valueEl.set(value);
				fire(new ObservableCollectionEvent<>(getElementId(), getType(), getElementsBefore(getElementId()), CollectionChangeType.set,
					old, value, theTransactionCauses.peekLast()));
			}

			@Override
			public String canRemove() {
				return valueEl.canRemove();
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				E old = get();
				valueEl.remove();
				fire(new ObservableCollectionEvent<>(getElementId(), getType(), getElementsBefore(getElementId()),
					CollectionChangeType.remove, old, old, theTransactionCauses.peekLast()));
			}

			@Override
			public String canAdd(E value, boolean before) {
				return valueEl.canAdd(value, before);
			}

			@Override
			public ElementId add(E value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
				ElementId newId = valueEl.add(value, before);
				fire(new ObservableCollectionEvent<>(newId, getType(), getElementsBefore(newId), CollectionChangeType.add, null, value,
					theTransactionCauses.peekLast()));
				return newId;
			}

			@Override
			public String toString() {
				return valueEl.toString();
			}
		};
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

	private class DefaultMutableSpliterator extends MutableElementSpliterator.SimpleMutableSpliterator<E> {
		private final MutableElementSpliterator<E> theValueSpliter;

		DefaultMutableSpliterator(MutableElementSpliterator<E> valueSpliter) {
			super(DefaultObservableCollection.this);
			theValueSpliter = valueSpliter;
		}

		@Override
		public long estimateSize() {
			return theValueSpliter.estimateSize();
		}

		@Override
		public int characteristics() {
			return theValueSpliter.characteristics();
		}

		@Override
		public long getExactSizeIfKnown() {
			return theValueSpliter.getExactSizeIfKnown();
		}

		@Override
		public Comparator<? super E> getComparator() {
			return theValueSpliter.getComparator();
		}

		@Override
		protected boolean internalForElement(Consumer<? super CollectionElement<E>> action, boolean forward) {
			return theValueSpliter.forElement(action, forward);
		}

		@Override
		protected boolean internalForElementM(Consumer<? super MutableCollectionElement<E>> action, boolean forward) {
			return theValueSpliter.forElementM(el -> action.accept(mutableElementFor(el)), forward);
		}

		@Override
		public MutableElementSpliterator<E> trySplit() {
			MutableElementSpliterator<E> valueSplit = theValueSpliter.trySplit();
			return valueSplit == null ? null : new DefaultMutableSpliterator(valueSplit);
		}
	}
}