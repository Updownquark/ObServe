package org.observe.collect;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.Subscription;
import org.observe.util.ObservableUtils;
import org.qommons.Equalizer;
import org.qommons.Transaction;
import org.qommons.collect.ImmutableIterator;
import org.qommons.collect.TransactableSortedSet;

import com.google.common.reflect.TypeToken;

/**
 * A sorted set whose content can be observed. This set is immutable in that none of its methods, including {@link java.util.Set} methods,
 * can modify its content (Set modification methods will throw {@link UnsupportedOperationException}). All {@link ObservableElement}s
 * returned by this observable will be instances of {@link ObservableOrderedElement}.
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
	default ObservableValue<E> relative(E value, boolean up, boolean withValue) {
		if(up)
			return tailSet(value, withValue).getFirst();
		else
			return headSet(value, withValue).getLast();
	}

	@Override
	default E first() {
		if(isEmpty())
			throw new java.util.NoSuchElementException();
		return getFirst().get();
	}

	@Override
	default E last() {
		// Can't throw NoSuchElementException to comply with ObservableIndexedCollection.last()
		return getLast().get();
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
		return subSet(null, true, null, true, true);
	}

	@Override
	default Iterator<E> descendingIterator() {
		return reverse().iterator();
	}

	/**
	 * <p>
	 * Starts iteration in either direction from a starting point.
	 * </p>
	 *
	 * <p>
	 * This method is used by the default implementation of {@link #subSet(Object, boolean, Object, boolean, boolean)}, so implementations
	 * and sub-interfaces should <b>NOT</b> call any of the sub-set methods from this method unless the subSet method itself is overridden.
	 * </p>
	 *
	 * @param element The element to start iteration at
	 * @param included Whether to include the given element in the iteration
	 * @param reversed Whether to iterate backward or forward from the given element
	 * @return An iterable that starts iteration from the given element
	 */
	Iterable<E> iterateFrom(E element, boolean included, boolean reversed);

	/**
	 * <p>
	 * Starts iteration in either direction from a starting point.
	 * </p>
	 *
	 * <p>
	 * This default implementation of the {@link #iterateFrom(Object, boolean, boolean)} method just takes a default iterator and skips over
	 * elements until the given starting point is passed. This method should be only be used when no other better performance is possible.
	 * </p>
	 *
	 * @param set The set to iterate on
	 * @param element The element to start iteration at
	 * @param included Whether to include the given element in the iteration
	 * @param reversed Whether to iterate backward or forward from the given element
	 * @return An iterable that starts iteration from the given element
	 */
	public static <E> Iterable<E> defaultIterateFrom(ObservableSortedSet<E> set, E element, boolean included, boolean reversed) {
		return () -> new Iterator<E>() {
			private final Iterator<E> backing = reversed ? set.descendingIterator() : set.iterator();

			private E theFirst;

			{
				if (element != null) {
					Comparator<? super E> compare = set.comparator();
					while (backing.hasNext()) {
						theFirst = backing.next();
						int comp = compare.compare(theFirst, element);
						if (comp > 0 || (included && comp == 0))
							break;
					}
				}
			}

			@Override
			public boolean hasNext() {
				return backing.hasNext();
			}

			@Override
			public E next() {
				return backing.next();
			}

			@Override
			public void remove() {
				backing.remove();
			}
		};
	}

	/**
	 * A sub-set of this set. Like {@link #subSet(Object, boolean, Object, boolean)}, but may be reversed.
	 *
	 * @param fromElement The minimum bounding element for the sub set
	 * @param fromInclusive Whether the minimum bound will be included in the sub set (if present in this set)
	 * @param toElement The maximum bounding element for the sub set
	 * @param toInclusive Whether the maximum bound will be included in the sub set (if present in this set)
	 * @param reversed Whether the returned sub set will be in the opposite order as this set
	 * @return The sub set
	 */
	default ObservableSortedSet<E> subSet(E fromElement, boolean fromInclusive, E toElement, boolean toInclusive, boolean reversed) {
		return new ObservableSubSet<>(this, fromElement, fromInclusive, toElement, toInclusive, reversed);
	}

	@Override
	default ObservableSortedSet<E> subSet(E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
		return subSet(fromElement, fromInclusive, toElement, toInclusive, false);
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

	/**
	 * @param o The value to get the index of
	 * @return The index of the given value in this collection, or, if the given value is not present in this set, <code>-dest-1</code>,
	 *         where <code>dest</code> is the index of the position where the given element would appear if it were added to this set.
	 * @throws ClassCastException If the given value is not null or an instance of this set's type.
	 * @see org.observe.collect.ObservableCollection#indexOf(java.lang.Object)
	 */
	@Override
	int indexOf(Object o);

	/**
	 * Same as {@link #indexOf(Object)} for sorted sets
	 *
	 * @param o The value to get the index of
	 * @return The index of the given value in this collection, or, if the given value is not present in this set, <code>-dest-1</code>,
	 *         where <code>dest</code> is the index of the position where the given element would appear if it were added to this set.
	 * @throws ClassCastException If the given value is not null or an instance of this set's type.
	 * @see org.observe.collect.ObservableIndObservableCollectionexedCollection#indexOf(java.lang.Object)
	 */
	@Override
	int lastIndexOf(Object o);

	@Override
	default <T> UniqueSortedDataFlow<E, E, E> flow() {
		return new ObservableSortedSetImpl.UniqueSortedBaseFlow<>(this);
	}

	@Override
	default SortedSetViewBuilder<E> view() {
		return new SortedSetViewBuilder<>(this);
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
		ConstantObservableSet ret = d().debug(new ConstantObservableSet()).tag("constant", coll).tag("type", type).get();
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

	class SortedSetViewBuilder<E> extends SetViewBuilder<E> {
		public SortedSetViewBuilder(ObservableSortedSet<E> collection) {
			super(collection);
		}

		@Override
		protected ObservableSortedSet<E> getSource() {
			return (ObservableSortedSet<E>) super.getSource();
		}

		@Override
		public ObservableSet<E> build() {
			return new ObservableSortedSetImpl.SortedSetView<>(this);
		}
	}

	/**
	 * Implements {@link ObservableSortedSet#subSet(Object, boolean, Object, boolean, boolean)}
	 *
	 * @param <E> The type of elements in the set
	 */
	class ObservableSubSet<E> implements ObservableSortedSet<E> {
		private final ObservableSortedSet<E> theWrapped;

		private final E theMin;
		private final boolean isMinIncluded;
		private final E theMax;
		private final boolean isMaxIncluded;

		private final boolean isReversed;

		public ObservableSubSet(ObservableSortedSet<E> set, E min, boolean includeMin, E max, boolean includeMax, boolean reversed) {
			super();
			theWrapped = set;
			theMin = min;
			isMinIncluded = includeMin;
			theMax = max;
			isMaxIncluded = includeMax;
			isReversed = reversed;
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

		public boolean isReversed() {
			return isReversed;
		}

		public boolean isInRange(E value) {
			Comparator<? super E> compare = theWrapped.comparator();
			if(theMin != null) {
				int comp = compare.compare(value, theMin);
				if(comp < 0 || (!isMinIncluded && comp == 0))
					return false;
			}
			if(theMax != null) {
				int comp = compare.compare(value, theMax);
				if(comp > 0 || (!isMaxIncluded && comp == 0))
					return false;
			}
			return true;
		}

		@Override
		public TypeToken<E> getType() {
			return theWrapped.getType();
		}

		@Override
		public ObservableValue<CollectionSession> getSession() {
			return theWrapped.getSession();
		}

		/** @return The first index in the wrapped sorted set that is included in this set */
		protected int getMinIndex() {
			int minIndex;
			if(theMin == null)
				minIndex = 0;
			else {
				minIndex = theWrapped.indexOf(theMin);
				if(minIndex < 0)
					minIndex = -minIndex - 1; // Include the element at the insertion index
				else if(!isMinIncluded)
					minIndex++;
			}
			return minIndex;
		}

		/** @return The last index in the wrapped */
		protected int getMaxIndex() {
			int maxIndex;
			if(theMax == null)
				maxIndex = theWrapped.size() - 1;
			else {
				maxIndex = theWrapped.indexOf(theMax);
				if(maxIndex < 0) {
					maxIndex = -maxIndex - 1;
					maxIndex--; // Don't include the element at the insertion index
				} else if(!isMaxIncluded)
					maxIndex--;
			}
			return maxIndex;
		}

		@Override
		public int size() {
			int minIndex = getMinIndex();
			int maxIndex = getMaxIndex();
			return maxIndex - minIndex + 1; // Both minIndex and maxIndex are included here
		}

		@Override
		public Iterator<E> iterator() {
			return iterateFrom(null, true, false).iterator();
		}

		@Override
		public Iterable<E> descending() {
			return iterateFrom(null, true, true);
		}

		@Override
		public boolean canRemove(Object value) {
			if (value != null || !theWrapped.getType().getRawType().isInstance(value))
				return false;
			if (!isInRange((E) value))
				return false;
			return theWrapped.canRemove(value);
		}

		@Override
		public boolean canAdd(E value) {
			if (!isInRange(value))
				return false;
			return theWrapped.canAdd(value);
		}

		@Override
		public Iterable<E> iterateFrom(E start, boolean included, boolean reversed) {
			E stop;
			boolean includeStop;
			Comparator<? super E> compare = comparator();
			if(isReversed)
				reversed = !reversed;
			if(reversed) {
				if(start == null || (theMax != null && compare.compare(start, theMax) > 0)) {
					start = theMax;
					included &= isMaxIncluded;
				}
				stop = theMin;
				includeStop = isMinIncluded;
			} else {
				if(start == null || (theMin != null && compare.compare(start, theMin) < 0)) {
					start = theMin;
					included &= isMinIncluded;
				}
				stop = theMax;
				includeStop = isMaxIncluded;
			}
			Iterable<E> backingIterable = theWrapped.iterateFrom(start, !reversed, included);
			return () -> new Iterator<E>() {
				private final Iterator<E> backing = backingIterable.iterator();

				private boolean calledHasNext;
				private E theNext;

				private boolean isEnded;

				@Override
				public boolean hasNext() {
					if(calledHasNext)
						return !isEnded;
					calledHasNext = true;
					if(!backing.hasNext()) {
						isEnded = true;
						return false;
					}
					theNext = backing.next();
					if(stop != null) {
						int comp = compare.compare(theNext, stop);
						if(comp > 0 || (comp == 0 && !includeStop))
							isEnded = true;
					}
					return !isEnded;
				}

				@Override
				public E next() {
					if(!hasNext())
						throw new java.util.NoSuchElementException();
					calledHasNext = false;
					return theNext;
				}

				@Override
				public void remove() {
					if(calledHasNext)
						throw new IllegalStateException("remove() must be called after next() and before hasNext()");
					backing.remove();
				}
			};
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theWrapped.lock(write, cause);
		}

		@Override
		public boolean isSafe() {
			return theWrapped.isSafe();
		}

		@Override
		public Subscription onOrderedElement(Consumer<? super ObservableOrderedElement<E>> onElement) {
			return theWrapped.onOrderedElement(element -> {
				if(isInRange(element.get()))
					onElement.accept(new ObservableOrderedElement<E>() {
						@Override
						public ObservableValue<E> persistent() {
							return element.persistent();
						}

						@Override
						public TypeToken<E> getType() {
							return element.getType();
						}

						@Override
						public E get() {
							return element.get();
						}

						@Override
						public int getIndex() {
							int elIndex = element.getIndex();
							int minIndex = getMinIndex();
							return elIndex - minIndex;
						}

						@Override
						public Subscription subscribe(Observer<? super ObservableValueEvent<E>> observer) {
							return ObservableUtils.wrap(element, this, observer);
						}

						@Override
						public boolean isSafe() {
							return element.isSafe();
						}
					});
			});
		}

		@Override
		public Subscription onElementReverse(Consumer<? super ObservableOrderedElement<E>> onElement) {
			return theWrapped.onElementReverse(element -> {
				if(isInRange(element.get()))
					onElement.accept(element);
			});
		}

		@Override
		public Comparator<? super E> comparator() {
			Comparator<? super E> compare = theWrapped.comparator();
			if(isReversed)
				compare = compare.reversed();
			return compare;
		}

		@Override
		public ObservableValue<E> relative(E value, boolean up, boolean withValue) {
			if(isReversed)
				up = !up;
			Comparator<? super E> compare = theWrapped.comparator();
			if(!up && theMin != null) {
				int comp = compare.compare(value, theMin);
				if(comp < 0 || (!withValue && comp == 0))
					return ObservableValue.constant(getType(), null);
			}
			if(up && theMax != null) {
				int comp = compare.compare(value, theMax);
				if(comp > 0 || (!withValue && comp == 0))
					return ObservableValue.constant(getType(), null);
			}
			return theWrapped.relative(value, up, withValue).mapV(v -> isInRange(v) ? v : null);
		}

		@Override
		public ObservableSortedSet<E> subSet(E min, boolean includeMin, E max, boolean includeMax, boolean reverse) {
			if(isReversed) {
				E temp = min;
				min = max;
				max = temp;
				boolean tempB = includeMin;
				includeMin = includeMax;
				includeMax = tempB;
			}
			if(min == null)
				min = theMin;
			else if(theMin != null && theWrapped.comparator().compare(min, theMin) <= 0) {
				min = theMin;
				includeMin = isMinIncluded;
			}
			if(max == null)
				max = theMax;
			else if(theMax != null && theWrapped.comparator().compare(max, theMax) >= 0) {
				max = theMax;
				includeMax = isMaxIncluded;
			}
			return new ObservableSubSet<>(theWrapped, min, includeMin, max, includeMax, reverse ^ isReversed);
		}

		@Override
		public E get(int index) {
			int minIndex;
			if(theMin == null)
				minIndex = 0;
			else {
				minIndex = theWrapped.indexOf(theMin);
				if(minIndex < 0)
					minIndex = -minIndex - 1;
				else if(!isMinIncluded)
					minIndex++;
			}
			int maxIndex;
			if(theMax == null)
				maxIndex = theWrapped.size();
			else {
				maxIndex = theWrapped.indexOf(theMax);
				if(maxIndex < 0)
					maxIndex = -maxIndex - 1;
				else if(!isMaxIncluded)
					maxIndex--;
			}
			int size = maxIndex - minIndex;
			if(size < 0)
				size = 0;
			if(index < 0 || index >= size)
				throw new IndexOutOfBoundsException(index + " of " + size);
			return theWrapped.get(index + minIndex);
		}

		@Override
		public int indexOf(Object value) {
			Comparator<? super E> compare = theWrapped.comparator();
			// If it's not in range, we'll return the bound index, even though actually adding it would generate an error
			if(theMin != null) {
				int comp = compare.compare((E) value, theMin);
				if(comp < 0 || (!isMinIncluded && comp == 0))
					return isReversed ? -size() - 1 : -1;
			}
			if(theMax != null) {
				int comp = compare.compare((E) value, theMax);
				if(comp < 0 || (!isMaxIncluded && comp == 0))
					return isReversed ? -1 : -size() - 1;
			}
			return PartialSortedSetImpl.super.indexOf(value);
		}

		@Override
		public boolean contains(Object o) {
			if(!isInRange((E) o))
				return false;
			return theWrapped.contains(o);
		}

		@Override
		public boolean containsAll(Collection<?> values) {
			for(Object o : values)
				if(!isInRange((E) o))
					return false;
			return theWrapped.containsAll(values);
		}

		@Override
		public boolean add(E value) {
			if(!isInRange(value))
				throw new IllegalArgumentException(value + " is not in the range of this sub-set");
			return theWrapped.add(value);
		}

		@Override
		public boolean addAll(Collection<? extends E> values) {
			for(E value : values)
				if(!isInRange(value))
					throw new IllegalArgumentException(value + " is not in the range of this sub-set");
			return theWrapped.addAll(values);
		}

		@Override
		public boolean remove(Object value) {
			if(!isInRange((E) value))
				return false;
			return theWrapped.remove(value);
		}

		@Override
		public boolean removeAll(Collection<?> values) {
			List<?> toRemove = values.stream().filter(v -> isInRange((E) v)).collect(Collectors.toList());
			return theWrapped.removeAll(toRemove);
		}

		@Override
		public boolean retainAll(Collection<?> values) {
			return PartialSortedSetImpl.super.retainAll(values);
		}

		@Override
		public void clear() {
			PartialSortedSetImpl.super.clear();
		}

		@Override
		public String toString() {
			return ObservableSet.toString(this);
		}
	}
}
