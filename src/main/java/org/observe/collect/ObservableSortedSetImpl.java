package org.observe.collect;

import java.util.Collection;
import java.util.Comparator;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.Subscription;
import org.observe.collect.ObservableCollection.UniqueSortedDataFlow;
import org.observe.collect.ObservableCollection.UniqueSortedMappedCollectionBuilder;
import org.observe.collect.ObservableCollection.UniqueSortedModFilterBuilder;
import org.observe.collect.ObservableCollectionDataFlowImpl.AbstractDataFlow;
import org.observe.collect.ObservableCollectionDataFlowImpl.CollectionManager;
import org.observe.collect.ObservableCollectionDataFlowImpl.UniqueSortedDataFlowWrapper;
import org.observe.collect.ObservableSetImpl.UniqueBaseFlow;
import org.qommons.Transaction;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.SimpleCause;
import org.qommons.value.Value;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

public class ObservableSortedSetImpl {
	private ObservableSortedSetImpl() {}

	/**
	 * Implements {@link ObservableSortedSet#subSet(Object, boolean, Object, boolean)}
	 *
	 * @param <E> The type of elements in the set
	 */
	public static class ObservableSubSet<E> implements ObservableSortedSet<E> {
		protected static class BoundedValue<E> {
			final E value;
			final boolean included;

			BoundedValue(E value, boolean included) {
				this.value = value;
				this.included = included;
			}
		}
		private final ObservableSortedSet<E> theWrapped;

		private final Comparable<? super E> from;
		private final Comparable<? super E> to;

		public ObservableSubSet(ObservableSortedSet<E> set, Comparable<? super E> from, Comparable<? super E> to) {
			theWrapped = set;
			this.from = from;
			this.to = to;
		}

		public ObservableSortedSet<E> getWrapped() {
			return theWrapped;
		}

		public Comparable<? super E> getFrom() {
			return from;
		}

		public Comparable<? super E> getTo() {
			return to;
		}

		public int isInRange(E value) {
			if (from != null && from.compareTo(value) > 0)
				return -1;
			if (to != null && to.compareTo(value) < 0)
				return 1;
			return 0;
		}

		protected Comparable<E> boundSearch(Comparable<? super E> search) {
			return v -> {
				int compare = isInRange(v);
				if (compare == 0)
					compare = search.compareTo(v);
				return compare;
			};
		}

		/** @return The first index in the wrapped sorted set that is included in this set */
		protected int getMinIndex() {
			if (from == null)
				return 0;
			int index = theWrapped.indexFor(from);
			if (index > 0)
				return index;
			else
				return -index - 1;
		}

		/** @return The last index in the wrapped */
		protected int getMaxIndex() {
			if (to == null)
				return size() - 1;
			int index = theWrapped.indexFor(to);
			if (index > 0)
				return index;
			else
				return -index - 1;
		}

		@Override
		public TypeToken<E> getType() {
			return theWrapped.getType();
		}

		@Override
		public boolean isLockSupported() {
			return theWrapped.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theWrapped.lock(write, cause);
		}

		@Override
		public Equivalence<? super E> equivalence() {
			return theWrapped.equivalence();
		}

		@Override
		public Comparator<? super E> comparator() {
			return theWrapped.comparator();
		}

		@Override
		public int getElementsBefore(ElementId id) {
			return getWrapped().getElementsBefore(id) - getMinIndex();
		}

		@Override
		public int getElementsAfter(ElementId id) {
			int wrappedAfter = getWrapped().getElementsAfter(id);
			int wrappedSize = getWrapped().size();
			int maxIndex = getMaxIndex();
			return wrappedAfter - (wrappedSize - maxIndex - 1);
		}

		@Override
		public int size() {
			int minIndex = getMinIndex();
			int maxIndex = getMaxIndex();
			return maxIndex - minIndex + 1; // Both minIndex and maxIndex are included here
		}

		@Override
		public boolean isEmpty() {
			return getMinIndex() > getMaxIndex(); // Both minIndex and maxIndex are included here
		}

		@Override
		public int indexFor(Comparable<? super E> search) {
			return theWrapped.indexFor(boundSearch(search));
		}

		@Override
		public ObservableValue<E> observeRelative(Comparable<? super E> search, boolean up) {
			return theWrapped.observeRelative(boundSearch(search), up).mapV(v -> isInRange(v) == 0 ? v : null, true);
		}

		@Override
		public ObservableSortedSet<E> subSet(Comparable<? super E> from, Comparable<? super E> to) {
			return new ObservableSubSet<>(theWrapped, boundSearch(from), boundSearch(to));
		}

		@Override
		public boolean forObservableElement(Comparable<? super E> search, boolean up,
			Consumer<? super ObservableCollectionElement<? extends E>> onElement) {
			boolean[] success = new boolean[1];
			theWrapped.forObservableElement(boundSearch(from), up, el -> {
				if (isInRange(el.get()) == 0) {
					success[0] = true;
					onElement.accept(el);
				}
			});
			return success[0];
		}

		@Override
		public boolean forMutableElement(Comparable<? super E> search, boolean up,
			Consumer<? super MutableObservableElement<? extends E>> onElement) {
			boolean[] success = new boolean[1];
			theWrapped.forMutableElement(boundSearch(search), up, el -> {
				if (isInRange(el.get()) == 0) {
					success[0] = true;
					onElement.accept(el);
				}
			});
			return success[0];
		}

		@Override
		public <T> T ofElementAt(ElementId elementId, Function<? super ObservableCollectionElement<? extends E>, T> onElement) {
			return theWrapped.ofElementAt(elementId, el -> {
				if (isInRange(el.get()) == 0)
					return onElement.apply(el);
				else
					throw new IllegalArgumentException(CollectionElement.StdMsg.NOT_FOUND);
			});
		}

		@Override
		public <T> T ofMutableElementAt(ElementId elementId, Function<? super MutableObservableElement<? extends E>, T> onElement) {
			return theWrapped.ofMutableElementAt(elementId, el -> {
				if (isInRange(el.get()) == 0)
					return onElement.apply(new BoundedMutableElement<>(el));
				else
					throw new IllegalArgumentException(CollectionElement.StdMsg.NOT_FOUND);
			});
		}

		@Override
		public <T> T ofElementAt(int index, Function<? super ObservableCollectionElement<? extends E>, T> onElement) {
			return getWrapped().ofElementAt(index - getMinIndex(), el -> {
				if (isInRange(el.get()) != 0)
					throw new IndexOutOfBoundsException(index + " of " + size());
				return onElement.apply(el);
			});
		}

		@Override
		public <T> T ofMutableElementAt(int index, Function<? super MutableObservableElement<? extends E>, T> onElement) {
			return getWrapped().ofMutableElementAt(index - getMinIndex(), el -> {
				if (isInRange(el.get()) != 0)
					throw new IndexOutOfBoundsException(index + " of " + size());
				return onElement.apply(new BoundedMutableElement<>(el));
			});
		}

		@Override
		public ObservableElementSpliterator<E> spliterator(boolean fromStart) {
			ObservableElementSpliterator<E> wrapSpliter;
			if (fromStart) {
				if (from == null)
					wrapSpliter = theWrapped.spliterator(true);
				else
					wrapSpliter = theWrapped.spliterator(from, true);
			} else {
				if (to == null)
					wrapSpliter = theWrapped.spliterator(false);
				else
					wrapSpliter = theWrapped.spliterator(to, false);
			}
			return new BoundedSpliterator(wrapSpliter);
		}

		@Override
		public MutableObservableSpliterator<E> mutableSpliterator(boolean fromStart) {
			MutableObservableSpliterator<E> wrapSpliter;
			if (fromStart) {
				if (from == null)
					wrapSpliter = theWrapped.mutableSpliterator(true);
				else
					wrapSpliter = theWrapped.mutableSpliterator(from, true);
			} else {
				if (to == null)
					wrapSpliter = theWrapped.mutableSpliterator(false);
				else
					wrapSpliter = theWrapped.mutableSpliterator(to, false);
			}
			return new BoundedMutableSpliterator(wrapSpliter);
		}

		@Override
		public ObservableElementSpliterator<E> spliterator(Comparable<? super E> search, boolean up) {
			return new BoundedSpliterator(theWrapped.spliterator(boundSearch(search), up));
		}

		@Override
		public MutableObservableSpliterator<E> mutableSpliterator(Comparable<? super E> search, boolean up) {
			return new BoundedMutableSpliterator(theWrapped.mutableSpliterator(boundSearch(search), up));
		}

		@Override
		public MutableObservableSpliterator<E> mutableSpliterator(int index) {
			int minIndex = getMinIndex();
			int maxIndex = getMaxIndex();
			if (index > (maxIndex - minIndex + 1))
				throw new IndexOutOfBoundsException(index + " of " + (maxIndex - minIndex + 1));
			return new BoundedMutableSpliterator(getWrapped().mutableSpliterator(index - minIndex));
		}

		@Override
		public String canAdd(E value) {
			if (isInRange(value) != 0)
				return CollectionElement.StdMsg.ILLEGAL_ELEMENT;
			return theWrapped.canAdd(value);
		}

		@Override
		public boolean add(E value) {
			if (isInRange(value) != 0)
				throw new IllegalArgumentException(CollectionElement.StdMsg.ILLEGAL_ELEMENT);
			return theWrapped.add(value);
		}

		@Override
		public boolean addAll(Collection<? extends E> values) {
			for (E value : values)
				if (isInRange(value) != 0)
					throw new IllegalArgumentException(CollectionElement.StdMsg.ILLEGAL_ELEMENT);
			return theWrapped.addAll(values);
		}

		@Override
		public String canRemove(Object value) {
			if (value != null || !theWrapped.getType().getRawType().isInstance(value))
				return CollectionElement.StdMsg.BAD_TYPE;
			if (isInRange((E) value) != 0)
				return CollectionElement.StdMsg.ILLEGAL_ELEMENT;
			return theWrapped.canRemove(value);
		}

		@Override
		public boolean remove(Object value) {
			if (isInRange((E) value) != 0)
				return false;
			return theWrapped.remove(value);
		}

		@Override
		public boolean removeLast(Object o) {
			if (!theWrapped.equivalence().isElement(o) || isInRange((E) o) != 0)
				return false;
			return theWrapped.removeLast(o);
		}

		@Override
		public void clear() {
			try (Transaction t = lock(true, null)) {
				SimpleCause.doWith(new SimpleCause(), c -> mutableSpliterator().forEachMutableElement(el -> el.remove(c)));
			}
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
			return theWrapped.onChange(new Consumer<ObservableCollectionEvent<? extends E>>() {
				private int startIndex;

				@Override
				public void accept(ObservableCollectionEvent<? extends E> evt) {
					int inRange = isInRange(evt.getNewValue());
					int oldInRange = evt.getType() == CollectionChangeType.set ? isInRange(evt.getOldValue()) : 0;
					if (inRange < 0) {
						switch (evt.getType()) {
						case add:
							startIndex++;
							break;
						case remove:
							startIndex--;
							break;
						case set:
							if (oldInRange > 0)
								startIndex++;
							else if (oldInRange == 0)
								fire(evt, CollectionChangeType.remove, evt.getOldValue(), evt.getOldValue());
						}
					} else if (inRange > 0) {
						switch (evt.getType()) {
						case set:
							if (oldInRange < 0)
								startIndex--;
							else if (oldInRange == 0)
								fire(evt, CollectionChangeType.remove, evt.getOldValue(), evt.getOldValue());
							break;
						default:
						}
					} else {
						switch (evt.getType()) {
						case add:
							fire(evt, evt.getType(), null, evt.getNewValue());
							break;
						case remove:
							fire(evt, evt.getType(), evt.getOldValue(), evt.getNewValue());
							break;
						case set:
							if (oldInRange < 0) {
								startIndex--;
								fire(evt, CollectionChangeType.add, null, evt.getNewValue());
							} else if (oldInRange == 0)
								fire(evt, CollectionChangeType.set, evt.getOldValue(), evt.getNewValue());
							else
								fire(evt, CollectionChangeType.add, null, evt.getNewValue());
						}
					}
				}

				void fire(ObservableCollectionEvent<? extends E> evt, CollectionChangeType type, E oldValue, E newValue) {
					observer.accept(new ObservableCollectionEvent<>(evt.getElementId(), evt.getIndex() - startIndex, evt.getType(),
						evt.getOldValue(), evt.getNewValue(), evt));
				}
			});
		}

		@Override
		public String toString() {
			return ObservableSet.toString(this);
		}

		private class BoundedSpliterator implements ObservableElementSpliterator<E> {
			private final ObservableElementSpliterator<E> theWrappedSpliter;

			BoundedSpliterator(ObservableElementSpliterator<E> wrappedSpliter) {
				theWrappedSpliter = wrappedSpliter;
			}

			protected ObservableElementSpliterator<E> getWrappedSpliter() {
				return theWrappedSpliter;
			}

			@Override
			public long estimateSize() {
				return theWrappedSpliter.estimateSize();
			}

			@Override
			public int characteristics() {
				return DISTINCT | ORDERED | SORTED;
			}

			@Override
			public Comparator<? super E> getComparator() {
				return theWrappedSpliter.getComparator();
			}

			@Override
			public TypeToken<E> getType() {
				return ObservableSubSet.this.getType();
			}

			@Override
			public boolean tryAdvanceObservableElement(Consumer<? super ObservableCollectionElement<E>> action) {
				boolean[] success = new boolean[1];
				if (theWrappedSpliter.tryAdvanceObservableElement(el -> {
					if (isInRange(el.get()) == 0) {
						success[0] = true;
						action.accept(el);
					}
				}) && !success[0]) {
					// If there was a super-set element that was not in range, need to back up back to the last in-range element
					theWrappedSpliter.tryReverse(v -> {
					});
				}
				return success[0];
			}

			@Override
			public boolean tryReverseObservableElement(Consumer<? super ObservableCollectionElement<E>> action) {
				boolean[] success = new boolean[1];
				if (theWrappedSpliter.tryReverseObservableElement(el -> {
					if (isInRange(el.get()) == 0) {
						success[0] = true;
						action.accept(el);
					}
				}) && !success[0]) {
					// If there was a super-set element that was not in range, need to back up back to the last in-range element
					theWrappedSpliter.tryAdvance(v -> {
					});
				}
				return success[0];
			}

			@Override
			public void forEachObservableElement(Consumer<? super ObservableCollectionElement<E>> action) {
				boolean[] lastOutOfRange = new boolean[1];
				theWrappedSpliter.forEachObservableElement(el -> {
					if (isInRange(el.get()) == 0)
						action.accept(el);
					else
						lastOutOfRange[0] = true;
				});
				if (lastOutOfRange[0]) {
					// Need to back up back to the last in-range element
					theWrappedSpliter.tryReverse(v -> {
					});
				}
			}

			@Override
			public void forEachReverseObservableElement(Consumer<? super ObservableCollectionElement<E>> action) {
				boolean[] lastOutOfRange = new boolean[1];
				theWrappedSpliter.forEachReverseObservableElement(el -> {
					if (isInRange(el.get()) == 0)
						action.accept(el);
					else
						lastOutOfRange[0] = true;
				});
				if (lastOutOfRange[0]) {
					// Need to back up back to the last in-range element
					theWrappedSpliter.tryAdvance(v -> {
					});
				}
			}

			@Override
			public boolean tryAdvance(Consumer<? super E> action) {
				boolean[] success = new boolean[1];
				if (theWrappedSpliter.tryAdvance(v -> {
					if (isInRange(v) == 0) {
						success[0] = true;
						action.accept(v);
					}
				}) && !success[0]) {
					// If there was a super-set element that was not in range, need to back up back to the last in-range element
					theWrappedSpliter.tryReverse(v -> {
					});
				}
				return success[0];
			}

			@Override
			public boolean tryReverse(Consumer<? super E> action) {
				boolean[] success = new boolean[1];
				if (theWrappedSpliter.tryReverse(v -> {
					if (isInRange(v) == 0) {
						success[0] = true;
						action.accept(v);
					}
				}) && !success[0]) {
					// If there was a super-set element that was not in range, need to back up back to the last in-range element
					theWrappedSpliter.tryAdvance(v -> {
					});
				}
				return success[0];
			}

			@Override
			public void forEachRemaining(Consumer<? super E> action) {
				boolean[] lastOutOfRange = new boolean[1];
				theWrappedSpliter.forEachRemaining(v -> {
					if (isInRange(v) == 0)
						action.accept(v);
					else
						lastOutOfRange[0] = true;
				});
				if (lastOutOfRange[0]) {
					// Need to back up back to the last in-range element
					theWrappedSpliter.tryReverse(v -> {
					});
				}
			}

			@Override
			public void forEachReverse(Consumer<? super E> action) {
				boolean[] lastOutOfRange = new boolean[1];
				theWrappedSpliter.forEachReverse(v -> {
					if (isInRange(v) == 0)
						action.accept(v);
					else
						lastOutOfRange[0] = true;
				});
				if (lastOutOfRange[0]) {
					// Need to back up back to the last in-range element
					theWrappedSpliter.tryAdvance(v -> {
					});
				}
			}

			@Override
			public ObservableElementSpliterator<E> trySplit() {
				ObservableElementSpliterator<E> wrapSplit = theWrappedSpliter.trySplit();
				return wrapSplit == null ? null : new BoundedSpliterator(wrapSplit);
			}
		}

		private class BoundedMutableSpliterator extends BoundedSpliterator implements MutableObservableSpliterator<E> {
			BoundedMutableSpliterator(MutableObservableSpliterator<E> wrappedSpliter) {
				super(wrappedSpliter);
			}

			@Override
			protected MutableObservableSpliterator<E> getWrappedSpliter() {
				return (MutableObservableSpliterator<E>) super.getWrappedSpliter();
			}

			@Override
			public boolean tryAdvanceMutableElement(Consumer<? super MutableObservableElement<E>> action) {
				boolean[] success = new boolean[1];
				if (getWrappedSpliter().tryAdvanceMutableElement(el -> {
					if (isInRange(el.get()) == 0) {
						success[0] = true;
						action.accept(new BoundedMutableElement<>(el));
					}
				}) && !success[0]) {
					// If there was a super-set element that was not in range, need to back up back to the last in-range element
					getWrappedSpliter().tryReverse(v -> {
					});
				}
				return success[0];
			}

			@Override
			public boolean tryReverseMutableElement(Consumer<? super MutableObservableElement<E>> action) {
				boolean[] success = new boolean[1];
				if (getWrappedSpliter().tryReverseMutableElement(el -> {
					if (isInRange(el.get()) == 0) {
						success[0] = true;
						action.accept(new BoundedMutableElement<>(el));
					}
				}) && !success[0]) {
					// If there was a super-set element that was not in range, need to back up back to the last in-range element
					getWrappedSpliter().tryAdvance(v -> {
					});
				}
				return success[0];
			}

			@Override
			public void forEachMutableElement(Consumer<? super MutableObservableElement<E>> action) {
				boolean[] lastOutOfRange = new boolean[1];
				getWrappedSpliter().forEachMutableElement(el -> {
					if (isInRange(el.get()) == 0)
						action.accept(new BoundedMutableElement<>(el));
					else
						lastOutOfRange[0] = true;
				});
				if (lastOutOfRange[0]) {
					// Need to back up back to the last in-range element
					getWrappedSpliter().tryReverse(v -> {
					});
				}
			}

			@Override
			public void forEachReverseMutableElement(Consumer<? super MutableObservableElement<E>> action) {
				boolean[] lastOutOfRange = new boolean[1];
				getWrappedSpliter().forEachReverseMutableElement(el -> {
					if (isInRange(el.get()) == 0)
						action.accept(new BoundedMutableElement<>(el));
					else
						lastOutOfRange[0] = true;
				});
				if (lastOutOfRange[0]) {
					// Need to back up back to the last in-range element
					getWrappedSpliter().tryAdvance(v -> {
					});
				}
			}

			@Override
			public MutableObservableSpliterator<E> trySplit() {
				MutableObservableSpliterator<E> wrapSplit = getWrappedSpliter().trySplit();
				return wrapSplit == null ? null : new BoundedMutableSpliterator(wrapSplit);
			}
		}

		class BoundedMutableElement<T extends E> implements MutableObservableElement<T> {
			private final MutableObservableElement<T> theWrappedEl;

			BoundedMutableElement(MutableObservableElement<T> wrappedEl) {
				theWrappedEl = wrappedEl;
			}

			@Override
			public ElementId getElementId() {
				return theWrappedEl.getElementId();
			}

			@Override
			public TypeToken<T> getType() {
				return theWrappedEl.getType();
			}

			@Override
			public T get() {
				return theWrappedEl.get();
			}

			@Override
			public Value<String> isEnabled() {
				return theWrappedEl.isEnabled();
			}

			@Override
			public <V extends T> String isAcceptable(V value) {
				if (isInRange(value) != 0)
					return CollectionElement.StdMsg.ILLEGAL_ELEMENT;
				return theWrappedEl.isAcceptable(value);
			}

			@Override
			public <V extends T> T set(V value, Object cause) throws IllegalArgumentException, UnsupportedOperationException {
				if (isInRange(value) != 0)
					throw new IllegalArgumentException(CollectionElement.StdMsg.ILLEGAL_ELEMENT);
				return theWrappedEl.set(value, cause);
			}

			@Override
			public String canRemove() {
				return theWrappedEl.canRemove();
			}

			@Override
			public void remove(Object cause) throws UnsupportedOperationException {
				theWrappedEl.remove(cause);
			}

			@Override
			public String canAdd(T value, boolean before) {
				if (isInRange(value) != 0)
					return CollectionElement.StdMsg.ILLEGAL_ELEMENT;
				return theWrappedEl.canAdd(value, before);
			}

			@Override
			public void add(T value, boolean before, Object cause) throws UnsupportedOperationException, IllegalArgumentException {
				if (isInRange(value) != 0)
					throw new IllegalArgumentException(CollectionElement.StdMsg.ILLEGAL_ELEMENT);
				theWrappedEl.add(value, before, cause);
			}

			@Override
			public String toString() {
				return theWrappedEl.toString();
			}
		}
	}

	/**
	 * Implements {@link ObservableSortedSet#reverse()}
	 *
	 * @param <E> The type of elements in the collection
	 */
	public static class ReversedSortedSet<E> extends ObservableSetImpl.ReversedSet<E> implements ObservableSortedSet<E> {
		public ReversedSortedSet(ObservableSortedSet<E> wrap) {
			super(wrap);
		}

		@Override
		protected ObservableSortedSet<E> getWrapped() {
			return (ObservableSortedSet<E>) super.getWrapped();
		}

		@Override
		public Comparator<? super E> comparator() {
			return getWrapped().comparator().reversed();
		}

		@Override
		public int indexFor(Comparable<? super E> search) {
			try (Transaction t = lock(false, null)) {
				int index = getWrapped().indexFor(search);
				if (index >= 0)
					return size() - index - 1;
				else {
					index = -index - 1;
					index = size() - index;
					return -(index + 1);
				}
			}
		}

		@Override
		public ObservableValue<E> observeRelative(Comparable<? super E> search, boolean up) {
			return getWrapped().observeRelative(search, !up);
		}

		@Override
		public boolean forObservableElement(Comparable<? super E> value, boolean up,
			Consumer<? super ObservableCollectionElement<? extends E>> onElement) {
			return getWrapped().forObservableElement(value, !up, onElement);
		}

		@Override
		public boolean forMutableElement(Comparable<? super E> value, boolean up,
			Consumer<? super MutableObservableElement<? extends E>> onElement) {
			return getWrapped().forMutableElement(value, !up, onElement);
		}

		@Override
		public MutableObservableSpliterator<E> mutableSpliterator(Comparable<? super E> value, boolean up) {
			return getWrapped().mutableSpliterator(value, !up);
		}

		@Override
		public ObservableSortedSet<E> subSet(E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
			return getWrapped().subSet(toElement, toInclusive, fromElement, fromInclusive);
		}

		@Override
		public ObservableSortedSet<E> headSet(E toElement, boolean inclusive) {
			return getWrapped().tailSet(toElement, inclusive);
		}

		@Override
		public ObservableSortedSet<E> tailSet(E fromElement, boolean inclusive) {
			return getWrapped().headSet(fromElement, inclusive);
		}

		@Override
		public ObservableSortedSet<E> reverse() {
			return (ObservableSortedSet<E>) super.reverse();
		}
	}

	public static class UniqueSortedBaseFlow<E> extends UniqueBaseFlow<E> implements UniqueSortedDataFlow<E, E, E> {
		protected UniqueSortedBaseFlow(ObservableSortedSet<E> source) {
			super(source);
		}

		@Override
		protected ObservableSortedSet<E> getSource() {
			return (ObservableSortedSet<E>) super.getSource();
		}

		@Override
		public Comparator<? super E> comparator() {
			return getSource().comparator();
		}

		@Override
		public UniqueSortedDataFlow<E, E, E> filter(Function<? super E, String> filter) {
			return new UniqueSortedDataFlowWrapper<>(getSource(), (AbstractDataFlow<E, ?, E>) super.filter(filter),
				getSource().comparator());
		}

		@Override
		public UniqueSortedDataFlow<E, E, E> filterStatic(Function<? super E, String> filter) {
			return new UniqueSortedDataFlowWrapper<>(getSource(), (AbstractDataFlow<E, ?, E>) super.filterStatic(filter),
				getSource().comparator());
		}

		@Override
		public <X> UniqueSortedMappedCollectionBuilder<E, E, X> mapEquivalent(TypeToken<X> target) {
			return new UniqueSortedMappedCollectionBuilder<>(getSource(), this, target);
		}

		@Override
		public UniqueSortedDataFlow<E, E, E> refresh(Observable<?> refresh) {
			return new UniqueSortedDataFlowWrapper<>(getSource(), (AbstractDataFlow<E, ?, E>) super.refresh(refresh),
				getSource().comparator());
		}

		@Override
		public UniqueSortedDataFlow<E, E, E> refreshEach(Function<? super E, ? extends Observable<?>> refresh) {
			return new UniqueSortedDataFlowWrapper<>(getSource(), (AbstractDataFlow<E, ?, E>) super.refreshEach(refresh),
				getSource().comparator());
		}

		@Override
		public UniqueSortedModFilterBuilder<E, E> filterModification() {
			return new UniqueSortedModFilterBuilder<>(getSource(), this);
		}

		@Override
		public ObservableSortedSet<E> collectLW() {
			return getSource();
		}

		@Override
		public ObservableSortedSet<E> collect() {
			return (ObservableSortedSet<E>) super.collect();
		}

		@Override
		public ObservableSortedSet<E> collect(Observable<?> until) {
			if (until == Observable.empty)
				return getSource();
			else
				return new DerivedSortedSet<>(getSource(), manageCollection(), getSource().comparator(), until);
		}
	}

	public static class DerivedLWSortedSet<E, T> extends ObservableSetImpl.DerivedLWSet<E, T> implements ObservableSortedSet<T> {
		private final Comparator<? super T> theCompare;

		public DerivedLWSortedSet(ObservableSortedSet<E> source, CollectionManager<E, ?, T> flow, Comparator<? super T> compare) {
			super(source, flow);
			theCompare = compare;
		}

		@Override
		protected ObservableSortedSet<E> getSource() {
			return (ObservableSortedSet<E>) super.getSource();
		}

		@Override
		public Comparator<? super T> comparator() {
			return theCompare;
		}

		protected Comparable<? super E> mappedSearch(Comparable<? super T> search) {
			return v -> search.compareTo(getFlow().map(v).result);
		}

		@Override
		public ObservableValue<T> observeRelative(Comparable<? super T> search, boolean up) {
			return getSource().observeRelative(mappedSearch(search), up).mapV(getType(), v -> getFlow().map(v).result);
		}

		@Override
		public int indexFor(Comparable<? super T> search) {
			return getSource().indexFor(mappedSearch(search));
		}

		@Override
		public boolean forObservableElement(Comparable<? super T> search, boolean up,
			Consumer<? super ObservableCollectionElement<? extends T>> onElement) {
			return getSource().forObservableElement(mappedSearch(search), up, el -> onElement.accept(elementFor(el)));
		}

		@Override
		public boolean forMutableElement(Comparable<? super T> search, boolean up,
			Consumer<? super MutableObservableElement<? extends T>> onElement) {
			return getSource().forMutableElement(mappedSearch(search), up, el -> onElement.accept(mutableElementFor(el)));
		}

		@Override
		public MutableObservableSpliterator<T> mutableSpliterator(Comparable<? super T> search, boolean up) {
			return new DerivedMutableSpliterator(getSource().mutableSpliterator(mappedSearch(search), up));
		}
	}

	public static class DerivedSortedSet<E, T> extends ObservableSetImpl.DerivedSet<E, T> implements ObservableSortedSet<T> {
		private final Comparator<? super T> theCompare;

		public DerivedSortedSet(ObservableCollection<E> source, CollectionManager<E, ?, T> flow, Comparator<? super T> compare,
			Observable<?> until) {
			super(source, flow, until);
			theCompare = compare;
		}

		@Override
		public Comparator<? super T> comparator() {
			return theCompare;
		}

		@Override
		public ObservableValue<T> observeRelative(Comparable<? super T> search, boolean up) {
			if (up)
				return subSet(search, null).observeFind(v -> true, () -> null, true);
			else
				return subSet(null, search).observeFind(v -> true, () -> null, false);
		}

		@Override
		public boolean forObservableElement(Comparable<? super T> search, boolean up,
			Consumer<? super ObservableCollectionElement<? extends T>> onElement) {
			try (Transaction t = lock(false, null)) {
				DerivedCollectionElement element = getPresentElements().relative(el -> search.compareTo(el.getValue()), up);
				if (element == null)
					return false;
				forElementAt(element, onElement);
				return true;
			}
		}

		@Override
		public boolean forMutableElement(Comparable<? super T> search, boolean up,
			Consumer<? super MutableObservableElement<? extends T>> onElement) {
			try (Transaction t = lock(true, null)) {
				DerivedCollectionElement element = getPresentElements().relative(el -> search.compareTo(el.getValue()), up);
				if (element == null)
					return false;
				forMutableElementAt(element, onElement);
				return true;
			}
		}

		@Override
		public MutableObservableSpliterator<T> mutableSpliterator(Comparable<? super T> search, boolean up) {
			return new MutableDerivedSpliterator(getPresentElements().relative(search, up));
		}
	}

	/**
	 * Implements {@link ObservableSortedSet#flattenValue(ObservableValue)}
	 *
	 * @param <E> The type of elements in the set
	 */
	public static class FlattenedValueSortedSet<E> extends ObservableCollectionImpl.FlattenedValueCollection<E>
	implements ObservableSortedSet<E> {
		public FlattenedValueSortedSet(ObservableValue<? extends ObservableSortedSet<E>> collectionObservable) {
			super(collectionObservable);
		}

		@Override
		protected ObservableValue<? extends ObservableSortedSet<E>> getWrapped() {
			return (ObservableValue<? extends ObservableSortedSet<E>>) super.getWrapped();
		}

		@Override
		public Comparator<? super E> comparator() {
			ObservableSortedSet<E> set = getWrapped().get();
			return set == null ? (o1, o2) -> -1 : (Comparator<? super E>) set.comparator();
		}

		@Override
		public int indexFor(Comparable<? super E> search) {
			ObservableSortedSet<E> wrapped = getWrapped().get();
			if (wrapped == null)
				return -1;
			return wrapped.indexFor(search);
		}

		@Override
		public ObservableValue<E> observeRelative(Comparable<? super E> value, boolean up) {
			return ObservableValue
				.flatten(getWrapped().mapV(new TypeToken<ObservableValue<E>>() {}.where(new TypeParameter<E>() {}, getType()),
					v -> v == null ? null : v.observeRelative(value, up)));
		}

		@Override
		public boolean forObservableElement(Comparable<? super E> value, boolean up,
			Consumer<? super ObservableCollectionElement<? extends E>> onElement) {
			ObservableSortedSet<E> wrapped = getWrapped().get();
			if (wrapped == null)
				return false;
			return wrapped.forObservableElement(value, up, onElement);
		}

		@Override
		public boolean forMutableElement(Comparable<? super E> value, boolean up,
			Consumer<? super MutableObservableElement<? extends E>> onElement) {
			ObservableSortedSet<E> wrapped = getWrapped().get();
			if (wrapped == null)
				return false;
			return wrapped.forMutableElement(value, up, onElement);
		}

		@Override
		public MutableObservableSpliterator<E> mutableSpliterator(Comparable<? super E> value, boolean up) {
			ObservableSortedSet<E> wrapped = getWrapped().get();
			if (wrapped == null)
				return MutableObservableSpliterator.empty(getType());
			return wrapped.mutableSpliterator(value, up);
		}
	}
}
