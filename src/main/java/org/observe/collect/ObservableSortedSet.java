package org.observe.collect;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.Subscription;
import org.qommons.Equalizer;
import org.qommons.Transaction;
import org.qommons.collect.ImmutableIterator;
import org.qommons.collect.SimpleCause;
import org.qommons.collect.TransactableSortedSet;
import org.qommons.value.Value;

import com.google.common.reflect.TypeToken;

/**
 * A sorted set whose content can be observed
 *
 * @param <E> The type of element in the set
 */
public interface ObservableSortedSet<E> extends ObservableSet<E>, TransactableSortedSet<E> {
	@Override
	default ImmutableIterator<E> iterator() {
		return ObservableSet.super.iterator();
	}

	@Override
	default ObservableElementSpliterator<E> spliterator() {
		return ObservableSet.super.spliterator();
	}

	/**
	 * Returns a value at or adjacent to another value
	 *
	 * @param value The relative value
	 * @param up Whether to get the closest value greater or less than the given value
	 * @param withValue Whether to return the given value if it exists in the map
	 * @return An observable value with the result of the operation
	 */
	ObservableValue<E> relative(E value, boolean up, boolean withValue);

	boolean forElement(E value, boolean up, boolean withValue, Consumer<? super ObservableCollectionElement<? extends E>> onElement);

	boolean forMutableElement(E value, boolean up, boolean withValue, Consumer<? super MutableObservableElement<? extends E>> onElement);

	@Override
	default boolean forObservableElement(E value, Consumer<? super ObservableCollectionElement<? extends E>> onElement, boolean first) {
		boolean[] found = new boolean[1];
		forElement(value, first, true, el -> {
			if (equivalence().elementEquals(el.get(), value)) {
				found[0] = true;
				onElement.accept(el);
			}
		});
		return found[0];
	}

	@Override
	default boolean forMutableElement(E value, Consumer<? super MutableObservableElement<? extends E>> onElement, boolean first) {
		boolean[] found = new boolean[1];
		forMutableElement(value, first, true, el -> {
			if (equivalence().elementEquals(el.get(), value)) {
				found[0] = true;
				onElement.accept(el);
			}
		});
		return found[0];
	}

	@Override
	default E first() {
		return getFirst();
	}

	@Override
	default E last() {
		return getLast();
	}

	@Override
	default E pollLast() {
		return ObservableSet.super.pollLast();
	}

	@Override
	default E pollFirst() {
		return ObservableSet.super.pollFirst();
	}

	@Override
	default E floor(E e) {
		return relative(e, false, true).get();
	}

	@Override
	default E lower(E e) {
		return relative(e, false, false).get();
	}

	@Override
	default E ceiling(E e) {
		return relative(e, true, true).get();
	}

	@Override
	default E higher(E e) {
		return relative(e, true, false).get();
	}

	@Override
	default ObservableSortedSet<E> reverse() {
		return descendingSet();
	}

	@Override
	default ObservableSortedSet<E> descendingSet() {
		return reverse();
	}

	@Override
	default Iterator<E> descendingIterator() {
		return reverse().iterator();
	}

	default ObservableElementSpliterator<E> spliterator(E value, boolean up, boolean withValue) {
		return mutableSpliterator(value, up, withValue).immutable();
	}

	MutableObservableSpliterator<E> mutableSpliterator(E value, boolean up, boolean withValue);

	/**
	 * A sub-set of this set. Like {@link #subSet(Object, boolean, Object, boolean)}, but may be reversed.
	 *
	 * @param fromElement The minimum bounding element for the sub set
	 * @param fromInclusive Whether the minimum bound will be included in the sub set (if present in this set)
	 * @param toElement The maximum bounding element for the sub set
	 * @param toInclusive Whether the maximum bound will be included in the sub set (if present in this set)
	 * @return The sub set
	 */
	@Override
	default ObservableSortedSet<E> subSet(E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
		return new ObservableSubSet<>(this, fromElement, fromInclusive, toElement, toInclusive);
	}

	@Override
	default ObservableSortedSet<E> headSet(E toElement, boolean inclusive) {
		return subSet(null, true, toElement, inclusive);
	}

	@Override
	default ObservableSortedSet<E> tailSet(E fromElement, boolean inclusive) {
		return subSet(fromElement, inclusive, null, true);
	}

	@Override
	default ObservableSortedSet<E> subSet(E fromElement, E toElement) {
		return subSet(fromElement, true, toElement, false);
	}

	@Override
	default ObservableSortedSet<E> headSet(E toElement) {
		return headSet(toElement, false);
	}

	@Override
	default ObservableSortedSet<E> tailSet(E fromElement) {
		return tailSet(fromElement, true);
	}

	@Override
	default <T> UniqueSortedDataFlow<E, E, E> flow() {
		return new ObservableSortedSetImpl.UniqueSortedBaseFlow<>(this);
	}

	/**
	 * @param <E> The type of elements in the collection
	 * @param outer The collection of collections
	 * @param compare The comparator to use to sort the elements
	 * @return An observable sorted set containing all unique elements in any collection in the outer collection
	 */
	public static <E> ObservableSortedSet<E> flatten(ObservableCollection<? extends ObservableSortedSet<? extends E>> outer,
		Comparator<? super E> compare) {
		return ObservableSortedSet.unique(ObservableCollection.flatten(outer), compare);
	}

	/**
	 * Turns an observable value containing an observable sorted set into the contents of the value
	 *
	 * @param collectionObservable The observable value
	 * @return A sorted set representing the contents of the value, or a zero-length set when null
	 */
	public static <E> ObservableSortedSet<E> flattenValue(ObservableValue<? extends ObservableSortedSet<E>> collectionObservable) {
		return new ObservableSortedSetImpl.FlattenedValueSortedSet<>(collectionObservable);
	}

	/**
	 * @param <T> The type of elements in the collection
	 * @param type The run-time type of elements in the collection
	 * @param coll The values for the collection
	 * @param compare The comparator to determine ordering of the values
	 * @return An observable sorted set containing all the values in the given collection
	 */
	public static <T> ObservableSortedSet<T> constant(TypeToken<T> type, Collection<? extends T> coll, Comparator<? super T> compare) {
		NavigableSet<T> modSet = new TreeSet<>(compare);
		modSet.addAll(coll);
		NavigableSet<T> constSet = Collections.unmodifiableNavigableSet(modSet);
		java.util.List<ObservableOrderedElement<T>> els = new java.util.ArrayList<>();
		class ConstantObservableSet implements PartialSortedSetImpl<T> {
			@Override
			public Equalizer getEqualizer() {
				return (o1, o2) -> compare.compare((T) o1, (T) o2) == 0;
			}

			@Override
			public ObservableValue<CollectionSession> getSession() {
				return ObservableValue.constant(TypeToken.of(CollectionSession.class), null);
			}

			@Override
			public Subscription onOrderedElement(Consumer<? super ObservableOrderedElement<T>> onElement) {
				for (ObservableOrderedElement<T> el : els)
					onElement.accept(el);
				return () -> {
				};
			}

			@Override
			public Transaction lock(boolean write, Object cause) {
				return () -> {
				};
			}

			@Override
			public boolean isSafe() {
				return true;
			}

			@Override
			public TypeToken<T> getType() {
				return type;
			}

			@Override
			public int size() {
				return constSet.size();
			}

			@Override
			public Iterator<T> iterator() {
				return constSet.iterator();
			}

			@Override
			public boolean canRemove(Object value) {
				return false;
			}

			@Override
			public boolean canAdd(T value) {
				return false;
			}

			@Override
			public Subscription onElementReverse(Consumer<? super ObservableOrderedElement<T>> onElement) {
				for (int i = els.size() - 1; i >= 0; i--)
					onElement.accept(els.unwrap(i));
				return () -> {
				};
			}

			@Override
			public Iterable<T> iterateFrom(T element, boolean included, boolean reversed) {
				NavigableSet<T> partial = reversed ? constSet.headSet(element, included) : constSet.tailSet(element, included);
				return reversed ? partial.descendingSet() : partial;
			}

			@Override
			public Iterable<T> descending() {
				return constSet.descendingSet();
			}

			@Override
			public Comparator<? super T> comparator() {
				return compare;
			}

			@Override
			public String toString() {
				return ObservableSet.toString(this);
			}
		}
		ConstantObservableSet ret = new ConstantObservableSet();
		int i = 0;
		for (T value : constSet) {
			int index = i;
			els.add(d().debug(new ObservableOrderedElement<T>() {
				@Override
				public TypeToken<T> getType() {
					return type;
				}

				@Override
				public int getIndex() {
					return index;
				}

				@Override
				public T get() {
					return value;
				}

				@Override
				public Subscription subscribe(Observer<? super ObservableValueEvent<T>> observer) {
					observer.onNext(createInitialEvent(value, null));
					return () -> {
					};
				}

				@Override
				public boolean isSafe() {
					return true;
				}

				@Override
				public ObservableValue<T> persistent() {
					return this;
				}
			}).from("element", ret).tag("value", value).get());
			i++;
		}
		return ret;
	}

	/**
	 * Implements {@link ObservableSortedSet#subSet(Object, boolean, Object, boolean, boolean)}
	 *
	 * @param <E> The type of elements in the set
	 */
	class ObservableSubSet<E> implements ObservableSortedSet<E> {
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
				return ObservableSortedSet.constant(getType(), Collections.emptyList(), comparator());
			BoundedValue<E> upperBound = bound(max, false, includeMax);
			if (upperBound == null)
				return ObservableSortedSet.constant(getType(), Collections.emptyList(), comparator());
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
		public void forElementAt(ElementId elementId, Consumer<? super ObservableCollectionElement<? extends E>> onElement) {
			theWrapped.forElementAt(elementId, el -> {
				if (isInRange(el.get()) == 0)
					onElement.accept(el);
				else
					throw new IllegalArgumentException(StdMsg.NOT_FOUND);
			});
		}

		@Override
		public void forMutableElementAt(ElementId elementId, Consumer<? super MutableObservableElement<? extends E>> onElement) {
			theWrapped.forMutableElementAt(elementId, el -> {
				if (isInRange(el.get()) == 0)
					onElement.accept(new BoundedMutableElement<>(el));
				else
					throw new IllegalArgumentException(StdMsg.NOT_FOUND);
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
}
