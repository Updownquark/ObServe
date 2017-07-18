package org.observe.collect;

import java.util.Collection;
import java.util.Comparator;
import java.util.Set;
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
import org.qommons.collect.SimpleCause;
import org.qommons.value.Value;

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

		private final E theMin;
		private final boolean isMinIncluded;
		private final E theMax;
		private final boolean isMaxIncluded;

		public ObservableSubSet(ObservableSortedSet<E> set, E min, boolean includeMin, E max, boolean includeMax) {
			theWrapped = set;
			theMin = min;
			isMinIncluded = includeMin;
			theMax = max;
			isMaxIncluded = includeMax;
		}

		public ObservableSortedSet<E> getWrapped() {
			return theWrapped;
		}

		public E getMin() {
			return theMin;
		}

		public boolean isMinIncluded() {
			return isMinIncluded;
		}

		public E getMax() {
			return theMax;
		}

		public boolean isMaxIncluded() {
			return isMaxIncluded;
		}

		public int isInRange(E value) {
			Comparator<? super E> compare = theWrapped.comparator();
			if (theMin != null) {
				int comp = compare.compare(value, theMin);
				if (comp < 0 || (!isMinIncluded && comp == 0))
					return -1;
			}
			if (theMax != null) {
				int comp = compare.compare(value, theMax);
				if (comp > 0 || (!isMaxIncluded && comp == 0))
					return 1;
			}
			return 0;
		}

		protected BoundedValue<E> bound(E value, boolean up, boolean included) {
			E internal;
			boolean include;
			int inRange = isInRange(value);
			if (up) {
				if (inRange < 0) {
					internal = theMin;
					include = isMinIncluded;
				} else if (inRange == 0) {
					internal = value;
					include = included;
				} else
					return null;
			} else {
				if (inRange < 0)
					return null;
				else if (inRange == 0) {
					internal = value;
					include = included;
				} else {
					internal = theMax;
					include = isMaxIncluded;
				}
			}
			return new BoundedValue<>(internal, include);
		}

		/** @return The first index in the wrapped sorted set that is included in this set */
		protected int getMinIndex() {
			int minIndex;
			if (theMin == null)
				minIndex = 0;
			else {
				minIndex = theWrapped.indexOf(theMin);
				if (minIndex < 0)
					minIndex = -minIndex - 1; // Include the element at the insertion index
				else if (!isMinIncluded)
					minIndex++;
			}
			return minIndex;
		}

		/** @return The last index in the wrapped */
		protected int getMaxIndex() {
			int maxIndex;
			if (theMax == null)
				maxIndex = theWrapped.size() - 1;
			else {
				maxIndex = theWrapped.indexOf(theMax);
				if (maxIndex < 0) {
					maxIndex = -maxIndex - 1;
					maxIndex--; // Don't include the element at the insertion index
				} else if (!isMaxIncluded)
					maxIndex--;
			}
			return maxIndex;
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
		public boolean contains(Object o) {
			if (!equivalence().isElement(o) || isInRange((E) o) != 0)
				return false;
			return theWrapped.contains(o);
		}

		@Override
		public boolean containsAny(Collection<?> c) {
			Set<Object> unique = (Set<Object>) equivalence().createSet();
			for (Object o : c)
				if (equivalence().isElement(o) && isInRange((E) o) == 0)
					unique.add(o);
			return theWrapped.containsAny(unique);
		}

		@Override
		public boolean containsAll(Collection<?> values) {
			for (Object o : values)
				if (!equivalence().isElement(o) || isInRange((E) o) != 0)
					return false;
			return theWrapped.containsAll(values);
		}

		@Override
		public E get(int index) {
			int minIndex;
			if (theMin == null)
				minIndex = 0;
			else {
				minIndex = theWrapped.indexOf(theMin);
				if (minIndex < 0)
					minIndex = -minIndex - 1;
				else if (!isMinIncluded)
					minIndex++;
			}
			int maxIndex;
			if (theMax == null)
				maxIndex = theWrapped.size();
			else {
				maxIndex = theWrapped.indexOf(theMax);
				if (maxIndex < 0)
					maxIndex = -maxIndex - 1;
				else if (!isMaxIncluded)
					maxIndex--;
			}
			int size = maxIndex - minIndex;
			if (size < 0)
				size = 0;
			if (index < 0 || index >= size)
				throw new IndexOutOfBoundsException(index + " of " + size);
			return theWrapped.get(index + minIndex);
		}

		@Override
		public ObservableValue<E> relative(E value, boolean up, boolean withValue) {
			BoundedValue<E> bounded = bound(value, up, withValue);
			if (bounded == null)
				return ObservableValue.constant(getType(), null);
			return theWrapped.relative(bounded.value, up, bounded.included).mapV(v -> isInRange(v) == 0 ? v : null, true);
		}

		@Override
		public ObservableSortedSet<E> subSet(E min, boolean includeMin, E max, boolean includeMax) {
			BoundedValue<E> lowerBound = bound(min, true, includeMin);
			if (lowerBound == null)
				return ObservableCollection.constant(getType()).uniqueSorted(comparator(), false).collect();
			BoundedValue<E> upperBound = bound(max, false, includeMax);
			if (upperBound == null)
				return ObservableCollection.constant(getType()).uniqueSorted(comparator(), false).collect();
			return new ObservableSubSet<>(theWrapped, lowerBound.value, lowerBound.included, upperBound.value, upperBound.included);
		}

		@Override
		public boolean forElement(E value, boolean up, boolean withValue,
			Consumer<? super ObservableCollectionElement<? extends E>> onElement) {
			BoundedValue<E> bounded = bound(value, up, withValue);
			if (bounded == null)
				return false;
			boolean[] success = new boolean[1];
			theWrapped.forElement(bounded.value, up, bounded.included, el -> {
				if (isInRange(el.get()) == 0) {
					success[0] = true;
					onElement.accept(el);
				}
			});
			return success[0];
		}

		@Override
		public boolean forMutableElement(E value, boolean up, boolean withValue,
			Consumer<? super MutableObservableElement<? extends E>> onElement) {
			BoundedValue<E> bounded = bound(value, up, withValue);
			if (bounded == null)
				return false;
			boolean[] success = new boolean[1];
			theWrapped.forMutableElement(bounded.value, up, bounded.included, el -> {
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
					throw new IllegalArgumentException(StdMsg.NOT_FOUND);
			});
		}

		@Override
		public <T> T ofMutableElementAt(ElementId elementId, Function<? super MutableObservableElement<? extends E>, T> onElement) {
			return theWrapped.ofMutableElementAt(elementId, el -> {
				if (isInRange(el.get()) == 0)
					return onElement.apply(new BoundedMutableElement<>(el));
				else
					throw new IllegalArgumentException(StdMsg.NOT_FOUND);
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
			E start = fromStart ? theMin : theMax;
			boolean startIncluded = fromStart ? isMinIncluded : isMaxIncluded;
			return new BoundedSpliterator(theWrapped.spliterator(start, fromStart, startIncluded));
		}

		@Override
		public MutableObservableSpliterator<E> mutableSpliterator(boolean fromStart) {
			E start = fromStart ? theMin : theMax;
			boolean startIncluded = fromStart ? isMinIncluded : isMaxIncluded;
			return new BoundedMutableSpliterator(theWrapped.mutableSpliterator(start, fromStart, startIncluded));
		}

		@Override
		public ObservableElementSpliterator<E> spliterator(E value, boolean up, boolean withValue) {
			BoundedValue<E> bounded = bound(value, up, withValue);
			if (bounded == null)
				return ObservableElementSpliterator.empty(getType());
			return new BoundedSpliterator(theWrapped.spliterator(bounded.value, up, bounded.included));
		}

		@Override
		public MutableObservableSpliterator<E> mutableSpliterator(E value, boolean up, boolean withValue) {
			BoundedValue<E> bounded = bound(value, up, withValue);
			if (bounded == null)
				return MutableObservableSpliterator.empty(getType());
			return new BoundedMutableSpliterator(theWrapped.mutableSpliterator(bounded.value, up, bounded.included));
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
				return StdMsg.ILLEGAL_ELEMENT;
			return theWrapped.canAdd(value);
		}

		@Override
		public boolean add(E value) {
			if (isInRange(value) != 0)
				throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
			return theWrapped.add(value);
		}

		@Override
		public boolean addAll(Collection<? extends E> values) {
			for (E value : values)
				if (isInRange(value) != 0)
					throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
			return theWrapped.addAll(values);
		}

		@Override
		public String canRemove(Object value) {
			if (value != null || !theWrapped.getType().getRawType().isInstance(value))
				return StdMsg.BAD_TYPE;
			if (isInRange((E) value) != 0)
				return StdMsg.ILLEGAL_ELEMENT;
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
		public boolean isEventIndexed() {
			return theWrapped.isEventIndexed();
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
					if (evt instanceof IndexedCollectionEvent)
						observer.accept(
							new IndexedCollectionEvent<>(evt.getElementId(), ((IndexedCollectionEvent<E>) evt).getIndex() - startIndex,
								evt.getType(), evt.getOldValue(), evt.getNewValue(), evt));
					else
						observer.accept(
							new ObservableCollectionEvent<>(evt.getElementId(), evt.getType(), evt.getOldValue(), evt.getNewValue(), evt));
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
					return StdMsg.ILLEGAL_ELEMENT;
				return theWrappedEl.isAcceptable(value);
			}

			@Override
			public <V extends T> T set(V value, Object cause) throws IllegalArgumentException, UnsupportedOperationException {
				if (isInRange(value) != 0)
					throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
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
					return StdMsg.ILLEGAL_ELEMENT;
				return theWrappedEl.canAdd(value, before);
			}

			@Override
			public void add(T value, boolean before, Object cause) throws UnsupportedOperationException, IllegalArgumentException {
				if (isInRange(value) != 0)
					throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
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
		public ObservableValue<E> relative(E value, boolean up, boolean withValue) {
			return getWrapped().relative(value, !up, withValue);
		}

		@Override
		public boolean forElement(E value, boolean up, boolean withValue,
			Consumer<? super ObservableCollectionElement<? extends E>> onElement) {
			return getWrapped().forElement(value, !up, withValue, onElement);
		}

		@Override
		public boolean forMutableElement(E value, boolean up, boolean withValue,
			Consumer<? super MutableObservableElement<? extends E>> onElement) {
			return getWrapped().forMutableElement(value, !up, withValue, onElement);
		}

		@Override
		public MutableObservableSpliterator<E> mutableSpliterator(E value, boolean up, boolean withValue) {
			return getWrapped().mutableSpliterator(value, !up, withValue);
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

		public DerivedLWSortedSet(ObservableSortedSet<E> source, CollectionDataFlow<E, ?, T> flow, Comparator<? super T> compare) {
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

		@Override
		public ObservableValue<T> relative(T value, boolean up, boolean withValue) {
		}

		@Override
		public boolean forElement(T value, boolean up, boolean withValue,
			Consumer<? super ObservableCollectionElement<? extends T>> onElement) {
		}

		@Override
		public boolean forMutableElement(T value, boolean up, boolean withValue,
			Consumer<? super MutableObservableElement<? extends T>> onElement) {
		}

		@Override
		public MutableObservableSpliterator<T> mutableSpliterator(T value, boolean up, boolean withValue) {
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
		public ObservableValue<T> relative(T value, boolean up, boolean withValue) {
			if (up)
				return tailSet(value, withValue).observeFind(v -> true, () -> null, true);
			else
				return headSet(value, withValue).observeFind(v -> true, () -> null, false);
		}

		@Override
		public boolean forElement(T value, boolean up, boolean withValue,
			Consumer<? super ObservableCollectionElement<? extends T>> onElement) {
			try (Transaction t = lock(false, null)) {
				DerivedCollectionElement element = getPresentElements().relative(v -> theCompare.compare(value, v), up, withValue);
				if (element == null)
					return false;
				forElementAt(element, onElement);
				return true;
			}
		}

		@Override
		public boolean forMutableElement(T value, boolean up, boolean withValue,
			Consumer<? super MutableObservableElement<? extends T>> onElement) {
			try (Transaction t = lock(true, null)) {
				DerivedCollectionElement element = getPresentElements().relative(v -> theCompare.compare(value, v), up, withValue);
				if (element == null)
					return false;
				forMutableElementAt(element, onElement);
				return true;
			}
		}

		@Override
		public MutableObservableSpliterator<T> mutableSpliterator(T value, boolean up, boolean withValue) {
			DerivedCollectionElement element = getPresentElements().relative(v -> theCompare.compare(value, v), up, withValue);
			if (element == null)
				return MutableObservableSpliterator.empty(getType());
			return new MutableDerivedSpliterator(getPresentElements().spliteratorFrom(element));
		}
	}
}
