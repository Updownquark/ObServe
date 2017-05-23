package org.observe.collect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Spliterator;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.Subscription;
import org.observe.collect.ObservableCollectionImpl.ConstantObservableCollection;
import org.observe.collect.ObservableListImpl.CollectionWrappingList;
import org.observe.collect.ObservableReversibleCollectionImpl.CachedReversibleCollection;
import org.observe.collect.ObservableReversibleCollectionImpl.CombinedReversibleCollection;
import org.observe.collect.ObservableReversibleCollectionImpl.EquivalenceSwitchedReversibleCollection;
import org.observe.collect.ObservableReversibleCollectionImpl.FilterMappedReversibleCollection;
import org.observe.collect.ObservableReversibleCollectionImpl.FlattenedReversibleValuesCollection;
import org.observe.collect.ObservableReversibleCollectionImpl.ModFilteredReversibleCollection;
import org.observe.collect.ObservableReversibleCollectionImpl.RefreshingReversibleCollection;
import org.observe.collect.ObservableReversibleSpliterator.WrappingReversibleObservableSpliterator;
import org.qommons.ListenerSet;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.Betterator;
import org.qommons.collect.CircularArrayList;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementSpliterator;
import org.qommons.collect.ReversibleList;
import org.qommons.collect.ReversibleSpliterator;
import org.qommons.collect.TransactableList;

import com.google.common.reflect.TypeToken;

/** Holds default implementation methods and classes for {@link ObservableList} */
public class ObservableListImpl {
	private ObservableListImpl() {}

	// /**
	// * Implements {@link ObservableList#listIterator()}
	// *
	// * @param <E> The type of values to iterate
	// */
	// public static class SimpleListIterator<E> implements java.util.ListIterator<E> {
	// private final List<E> theList;
	//
	// /** Index of element to be returned by subsequent call to next. */
	// int cursor = 0;
	//
	// /** Index of element returned by most recent call to next or previous. Reset to -1 if this element is deleted by a call to remove. */
	// int lastRet = -1;
	//
	// SimpleListIterator(List<E> list, int index) {
	// theList = list;
	// cursor = index;
	// }
	//
	// @Override
	// public boolean hasNext() {
	// return cursor != theList.size();
	// }
	//
	// @Override
	// public E next() {
	// try {
	// int i = cursor;
	// E next = theList.get(i);
	// lastRet = i;
	// cursor = i + 1;
	// return next;
	// } catch(IndexOutOfBoundsException e) {
	// throw new NoSuchElementException();
	// }
	// }
	//
	// @Override
	// public boolean hasPrevious() {
	// return cursor != 0;
	// }
	//
	// @Override
	// public E previous() {
	// try {
	// int i = cursor - 1;
	// E previous = theList.get(i);
	// lastRet = cursor = i;
	// return previous;
	// } catch(IndexOutOfBoundsException e) {
	// throw new NoSuchElementException();
	// }
	// }
	//
	// @Override
	// public int nextIndex() {
	// return cursor;
	// }
	//
	// @Override
	// public int previousIndex() {
	// return cursor - 1;
	// }
	//
	// @Override
	// public void remove() {
	// if(lastRet < 0)
	// throw new IllegalStateException();
	//
	// try {
	// theList.remove(lastRet);
	// if(lastRet < cursor)
	// cursor--;
	// lastRet = -1;
	// } catch(IndexOutOfBoundsException e) {
	// throw new ConcurrentModificationException();
	// }
	// }
	//
	// @Override
	// public void add(E e) {
	// try {
	// int i = cursor;
	// theList.add(i, e);
	// lastRet = -1;
	// cursor = i + 1;
	// } catch(IndexOutOfBoundsException ex) {
	// throw new ConcurrentModificationException();
	// }
	// }
	//
	// @Override
	// public void set(E e) {
	// if(lastRet < 0)
	// throw new IllegalStateException();
	//
	// try {
	// theList.set(lastRet, e);
	// } catch(IndexOutOfBoundsException ex) {
	// throw new ConcurrentModificationException();
	// }
	// }
	// }

	// /**
	// * Implements {@link ObservableList#subList(int, int)}
	// *
	// * @param <E> The type of element in the list
	// */
	// public static class SubListImpl<E> implements ReversibleList<E> {
	// private final ObservableList<E> theRoot;
	//
	// private final RRList<E> theList;
	//
	// private final int theOffset;
	// private int theSize;
	//
	// protected SubListImpl(ObservableList<E> root, RRList<E> list, int fromIndex, int toIndex) {
	// if(fromIndex < 0)
	// throw new IndexOutOfBoundsException("fromIndex = " + fromIndex);
	// if(fromIndex > toIndex)
	// throw new IllegalArgumentException("fromIndex(" + fromIndex + ") > toIndex(" + toIndex + ")");
	// theRoot = root;
	// theList=list;
	// theOffset = fromIndex;
	// theSize = toIndex - fromIndex;
	// }
	//
	// @Override
	// public E get(int index) {
	// rangeCheck(index, false);
	// return theList.get(index + theOffset);
	// }
	//
	// @Override
	// public int size() {
	// int size = theList.size() - theOffset;
	// if(theSize < size)
	// size = theSize;
	// return size;
	// }
	//
	// @Override
	// public boolean isEmpty() {
	// return size() == 0;
	// }
	//
	// @Override
	// public boolean contains(Object o) {
	// try (Transaction t = theRoot.lock(false, null)) {
	// for(Object value : this)
	// if(Objects.equals(value, o))
	// return true;
	// return false;
	// }
	// }
	//
	// @Override
	// public boolean containsAll(Collection<?> c) {
	// if(c.isEmpty())
	// return true;
	// ArrayList<Object> copy = new ArrayList<>(c);
	// BitSet found = new BitSet(copy.size());
	// try (Transaction t = theRoot.lock(false, null)) {
	// Iterator<E> iter = iterator();
	// while(iter.hasNext()) {
	// E next = iter.next();
	// int stop = found.previousClearBit(copy.size());
	// for(int i = found.nextClearBit(0); i < stop; i = found.nextClearBit(i + 1))
	// if(Objects.equals(next, copy.get(i)))
	// found.set(i);
	// }
	// return found.cardinality() == copy.size();
	// }
	// }
	//
	// @Override
	// public E [] toArray() {
	// ArrayList<E> ret = new ArrayList<>();
	// try (Transaction t = theRoot.lock(false, null)) {
	// for(E value : this)
	// ret.add(value);
	// }
	// return ret.toArray((E []) java.lang.reflect.Array.newInstance(theRoot.getType().wrap().getRawType(), ret.size()));
	// }
	//
	// @Override
	// public <T> T [] toArray(T [] a) {
	// ArrayList<E> ret = new ArrayList<>();
	// try (Transaction t = theRoot.lock(false, null)) {
	// for(E value : this)
	// ret.add(value);
	// }
	// return ret.toArray(a);
	// }
	//
	// @Override
	// public int indexOf(Object o) {
	// try (Transaction t = theRoot.lock(false, null)) {
	// ListIterator<E> it = listIterator();
	// int i;
	// for(i = 0; it.hasNext(); i++)
	// if(Objects.equals(it.next(), o))
	// return i;
	// return -1;
	// }
	// }
	//
	// @Override
	// public int lastIndexOf(Object o) {
	// try (Transaction t = theRoot.lock(false, null)) {
	// ListIterator<E> it = listIterator(size());
	// int i;
	// for(i = size() - 1; it.hasPrevious(); i--)
	// if(Objects.equals(it.previous(), o))
	// return i;
	// return -1;
	// }
	// }
	//
	// @Override
	// public boolean add(E value) {
	// try (Transaction t = theRoot.lock(true, null)) {
	// int preSize = theList.size();
	// theList.add(theOffset + theSize, value);
	// if(preSize < theList.size()) {
	// theSize++;
	// return true;
	// }
	// return false;
	// }
	// }
	//
	// @Override
	// public boolean addAll(Collection<? extends E> c) {
	// return addAll(size(), c);
	// }
	//
	// @Override
	// public boolean addAll(int index, Collection<? extends E> c) {
	// try (Transaction t = theRoot.lock(true, null)) {
	// rangeCheck(index, true);
	// int preSize = theList.size();
	// theList.addAll(theOffset + index, c);
	// int sizeDiff = theList.size() - preSize;
	// if(sizeDiff > 0) {
	// theSize += sizeDiff;
	// return true;
	// }
	// return false;
	// }
	// }
	//
	// @Override
	// public void add(int index, E value) {
	// try (Transaction t = theRoot.lock(true, null)) {
	// rangeCheck(index, true);
	// int preSize = theList.size();
	// theList.add(theOffset + index, value);
	// if(preSize < theList.size()) {
	// theSize++;
	// }
	// }
	// }
	//
	// @Override
	// public boolean remove(Object o) {
	// try (Transaction t = theRoot.lock(true, null)) {
	// Iterator<E> it = iterator();
	// while(it.hasNext()) {
	// if(Objects.equals(it.next(), o)) {
	// it.remove();
	// return true;
	// }
	// }
	// return false;
	// }
	// }
	//
	// @Override
	// public boolean removeAll(Collection<?> c) {
	// if(c.isEmpty())
	// return false;
	// try (Transaction t = theRoot.lock(true, null)) {
	// boolean modified = false;
	// Iterator<?> it = iterator();
	// while(it.hasNext()) {
	// if(c.contains(it.next())) {
	// it.remove();
	// modified = true;
	// }
	// }
	// return modified;
	// }
	// }
	//
	// @Override
	// public boolean retainAll(Collection<?> c) {
	// if(c.isEmpty()) {
	// clear();
	// return false;
	// }
	// try (Transaction t = theRoot.lock(true, null)) {
	// boolean modified = false;
	// Iterator<E> it = iterator();
	// while(it.hasNext()) {
	// if(!c.contains(it.next())) {
	// it.remove();
	// modified = true;
	// }
	// }
	// return modified;
	// }
	// }
	//
	// @Override
	// public E remove(int index) {
	// try (Transaction t = theRoot.lock(true, null)) {
	// rangeCheck(index, false);
	// int preSize = theList.size();
	// E ret = theList.remove(theOffset + index);
	// if(theList.size() < preSize)
	// theSize--;
	// return ret;
	// }
	// }
	//
	// @Override
	// public void clear() {
	// if(!isEmpty())
	// removeRange(0, size());
	// }
	//
	// @Override
	// public E set(int index, E value) {
	// try (Transaction t = theRoot.lock(true, null)) {
	// rangeCheck(index, false);
	// return theList.set(theOffset + index, value);
	// }
	// }
	//
	// @Override
	// public void removeRange(int fromIndex, int toIndex) {
	// try (Transaction t = theRoot.lock(true, null)) {
	// rangeCheck(fromIndex, false);
	// rangeCheck(toIndex, true);
	// int preSize = theList.size();
	// theList.removeRange(fromIndex + theOffset, toIndex + theOffset);
	// int sizeDiff = theList.size() - preSize;
	// theSize += sizeDiff;
	// }
	// }
	//
	// @Override
	// public List<E> subList(int fromIndex, int toIndex) {
	// rangeCheck(fromIndex, false);
	// if(toIndex < fromIndex)
	// throw new IllegalArgumentException("" + toIndex);
	// return new SubListImpl<>(theRoot, this, fromIndex, toIndex);
	// }
	//
	// @Override
	// public ListIterator<E> listIterator(int index) {
	// int size = size();
	// if(index < 0 || index > size)
	// throw new IndexOutOfBoundsException(index + " of " + size);
	// return new ListIterator<E>() {
	// private final ListIterator<E> backing = theList.listIterator(theOffset + index);
	//
	// private int theIndex = index;
	//
	// private boolean lastPrevious;
	//
	// @Override
	// public boolean hasNext() {
	// if(theIndex >= theSize)
	// return false;
	// return backing.hasNext();
	// }
	//
	// @Override
	// public E next() {
	// if(theIndex >= theSize)
	// throw new NoSuchElementException();
	// theIndex++;
	// lastPrevious = false;
	// return backing.next();
	// }
	//
	// @Override
	// public boolean hasPrevious() {
	// if(theIndex <= 0)
	// return false;
	// return backing.hasPrevious();
	// }
	//
	// @Override
	// public E previous() {
	// if(theIndex <= 0)
	// throw new NoSuchElementException();
	// theIndex--;
	// lastPrevious = true;
	// return backing.previous();
	// }
	//
	// @Override
	// public int nextIndex() {
	// return theIndex;
	// }
	//
	// @Override
	// public int previousIndex() {
	// return theIndex - 1;
	// }
	//
	// @Override
	// public void remove() {
	// try (Transaction t = theRoot.lock(true, null)) {
	// int preSize = theList.size();
	// backing.remove();
	// if(theList.size() < preSize) {
	// theSize--;
	// if(!lastPrevious)
	// theIndex--;
	// }
	// }
	// }
	//
	// @Override
	// public void set(E e) {
	// backing.set(e);
	// }
	//
	// @Override
	// public void add(E e) {
	// try (Transaction t = theRoot.lock(true, null)) {
	// int preSize = theList.size();
	// backing.add(e);
	// if(theList.size() > preSize) {
	// theSize++;
	// theIndex++;
	// }
	// }
	// }
	// };
	// }
	//
	// private void rangeCheck(int index, boolean withAdd) {
	// if(index < 0 || (!withAdd && index >= theSize) || (withAdd && index > theSize))
	// throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
	// }
	//
	// private String outOfBoundsMsg(int index) {
	// return "Index: " + index + ", Size: " + theSize;
	// }
	//
	// @Override
	// public String toString() {
	// StringBuilder ret = new StringBuilder("[");
	// boolean first = true;
	// try (Transaction t = theRoot.lock(false, null)) {
	// for(E value : this) {
	// if(!first) {
	// ret.append(", ");
	// } else
	// first = false;
	// ret.append(value);
	// }
	// }
	// ret.append(']');
	// return ret.toString();
	// }
	//
	// class Element implements ObservableOrderedElement<E> {
	// private final ObservableOrderedElement<E> theWrapped;
	//
	// private final DefaultObservable<Void> theRemovedObservable;
	// private final Observer<Void> theRemovedController;
	//
	// private boolean isRemoved;
	//
	// Element(ObservableOrderedElement<E> wrap) {
	// theWrapped = wrap;
	// theRemovedObservable = new DefaultObservable<>();
	// theRemovedController = theRemovedObservable.control(null);
	// }
	//
	// @Override
	// public ObservableValue<E> persistent() {
	// return theWrapped.persistent();
	// }
	//
	// @Override
	// public TypeToken<E> getType() {
	// return theWrapped.getType();
	// }
	//
	// @Override
	// public E get() {
	// return theWrapped.get();
	// }
	//
	// @Override
	// public Subscription subscribe(Observer<? super ObservableValueEvent<E>> observer) {
	// return theWrapped.takeUntil(theRemovedObservable).subscribe(new Observer<ObservableValueEvent<E>>() {
	// @Override
	// public <V extends ObservableValueEvent<E>> void onNext(V value) {
	// if(isRemoved)
	// return;
	// Observer.onNextAndFinish(observer, ObservableUtils.wrap(value, Element.this));
	// }
	//
	// @Override
	// public <V extends ObservableValueEvent<E>> void onCompleted(V value) {
	// if(isRemoved)
	// return;
	// Observer.onCompletedAndFinish(observer, ObservableUtils.wrap(value, Element.this));
	// }
	// });
	// }
	//
	// @Override
	// public boolean isSafe() {
	// return theWrapped.isSafe();
	// }
	//
	// @Override
	// public int getIndex() {
	// return theWrapped.getIndex() - theOffset;
	// }
	//
	// void remove() {
	// theRemovedController.onNext(null);
	// isRemoved = true;
	// }
	// }
	// }

	/**
	 * A simple {@link ObservableList#subList(int, int)} implementation for derived lists that are one-to-one mappings of their source lists
	 *
	 * @param <E> The type of values in the source list
	 * @param <T> The type of values in this list
	 */
	public static abstract class SimpleMappedSubList<E, T> implements ReversibleList<T>, TransactableList<T> {
		private final ReversibleList<? extends E> theWrapped;
		private final TypeToken<T> theType;
		private final Equivalence<? super T> theEquivalence;
		private final Transactable theTransactable;

		/**
		 * @param wrapped The source sub-list
		 * @param type The type of the derived list
		 * @param equivalence The equivalence set of the derived list
		 * @param transactable The transaction to use for the derived list
		 */
		public SimpleMappedSubList(ReversibleList<? extends E> wrapped, TypeToken<T> type, Equivalence<? super T> equivalence,
			Transactable transactable) {
			theWrapped = wrapped;
			theType = type;
			theEquivalence = equivalence;
			theTransactable = transactable;
		}

		/**
		 * @param wrap The source value
		 * @return The mapped value
		 */
		protected abstract T wrap(E wrap);

		/** @return Whether removals in this list are restricted */
		protected boolean isRemoveRestricted() {
			return false;
		}
		/**
		 * @param value The value to remove
		 * @return null if the value can be removed, or a message saying why it can't
		 */
		protected String checkRemove(E value) {
			return null;
		}
		/**
		 * @param value The value to add
		 * @return null if the value can be added, or a message saying why it can't
		 */
		protected abstract String checkAdd(T value);
		/**
		 * @param value The value to remove
		 * @return The value
		 * @throws RuntimeException If the value can't be removed
		 */
		protected E attemptedRemove(E value) {
			return null;
		}
		/**
		 * @param value The value to add
		 * @return The value to add to the source collection
		 */
		protected abstract E attemptedAdd(T value);

		/** @return Whether individual elements in this list may be set */
		protected abstract boolean isElementSettable();
		/**
		 * @param container The source value
		 * @param value The value to set
		 * @return null If the given value can be set in the source value, or a message saying why it can't
		 */
		protected abstract String checkSet(E container, T value);
		/**
		 * @param container The source value
		 * @param value The value to set
		 * @param cause The cause of the operation
		 * @return The value previously set in the container
		 */
		protected abstract T attemptSet(E container, T value, Object cause);

		/** @return The type of the derived collection */
		public TypeToken<T> getType() {
			return theType;
		}

		/** @return The equivalence of the derived collection */
		public Equivalence<? super T> equivalence() {
			return theEquivalence;
		}

		@Override
		public boolean isLockSupported() {
			return theTransactable.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theTransactable.lock(write, cause);
		}

		@Override
		public Betterator<T> iterator() {
			return ReversibleList.super.iterator();
		}

		@Override
		public int size() {
			return theWrapped.size();
		}

		@Override
		public boolean isEmpty() {
			return theWrapped.isEmpty();
		}

		@Override
		public boolean contains(Object value) {
			try (Transaction t = theTransactable.lock(false, null)) {
				Spliterator<T> iter = spliterator();
				boolean[] found = new boolean[1];
				while (!found[0] && iter.tryAdvance(v -> {
					if (equivalence().elementEquals(v, value))
						found[0] = true;
				})) {
				}
				return found[0];
			}
		}

		@Override
		public boolean containsAny(Collection<?> c) {
			if (c.isEmpty())
				return false;
			Set<T> cSet = ObservableCollectionImpl.toSet(equivalence(), c);
			if (cSet.isEmpty())
				return false;
			try (Transaction t = theTransactable.lock(false, null)) {
				Spliterator<T> iter = spliterator();
				boolean[] found = new boolean[1];
				while (iter.tryAdvance(next -> {
					found[0] = cSet.contains(next);
				}) && !found[0]) {
				}
				return found[0];
			}
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			if (c.isEmpty())
				return true;
			Set<T> cSet = ObservableCollectionImpl.toSet(equivalence(), c);
			if (cSet.isEmpty())
				return false;
			try (Transaction t = theTransactable.lock(false, null)) {
				Spliterator<T> iter = spliterator();
				while (iter.tryAdvance(next -> {
					cSet.remove(next);
				}) && !cSet.isEmpty()) {
				}
				return cSet.isEmpty();
			}
		}

		@Override
		public T get(int index) {
			return wrap(theWrapped.get(index));
		}

		@Override
		public int indexOf(Object value) {
			if (!equivalence().isElement(value))
				return -1;
			try (Transaction t = theTransactable.lock(false, null)) {
				int index = 0;
				for (T v : this) {
					if (equivalence().elementEquals(v, value))
						return index;
					index++;
				}
				return -1;
			}
		}

		@Override
		public int lastIndexOf(Object value) {
			if (!equivalence().isElement(value))
				return -1;
			try (Transaction t = theTransactable.lock(false, null)) {
				int result = -1;
				int index = 0;
				for (T v : this) {
					if (equivalence().elementEquals(v, value))
						result = index;
					index++;
				}
				return result;
			}
		}

		@Override
		public T[] toArray() {
			T[] array;
			try (Transaction t = theTransactable.lock(false, null)) {
				array = (T[]) java.lang.reflect.Array.newInstance(getType().wrap().getRawType(), size());
				int[] i = new int[1];
				spliterator().forEachRemaining(v -> array[i[0]++] = v);
			}
			return array;
		}

		@Override
		public <T2> T2[] toArray(T2[] a) {
			ArrayList<T> ret;
			try (Transaction t = theTransactable.lock(false, null)) {
				ret = new ArrayList<>();
				spliterator().forEachRemaining(v -> ret.add(v));
			}
			return ret.toArray(a);
		}

		@Override
		public ReversibleSpliterator<T> spliterator(boolean fromStart) {
			return new ReversibleSpliterator.WrappingReversibleSpliterator<>(theWrapped.spliterator(fromStart), getType(), map());
		}

		@Override
		public ReversibleSpliterator<T> spliterator(int index) {
			return new ReversibleSpliterator.WrappingReversibleSpliterator<>(theWrapped.spliterator(index), getType(), map());
		}

		private Supplier<Function<CollectionElement<? extends E>, CollectionElement<T>>> map() {
			return () -> {
				CollectionElement<? extends E>[] container = new CollectionElement[1];
				ElementSpliterator.WrappingElement<E, T> wrapping;
				wrapping = new ElementSpliterator.WrappingElement<E, T>(getType(), container) {
					@Override
					public T get() {
						return wrap(getWrapped().get());
					}

					@Override
					public String canRemove() {
						String msg = checkRemove(getWrapped().get());
						if (msg != null)
							return msg;
						return super.canRemove();
					}

					@Override
					public void remove() {
						attemptedRemove(getWrapped().get());
						super.remove();
					}

					@Override
					public <V extends T> String isAcceptable(V value) {
						if (isElementSettable())
							return checkSet(getWrapped().get(), value);
						else {
							String msg = checkAdd(value);
							if (msg != null)
								return msg;
							return ((CollectionElement<E>) getWrapped()).isAcceptable(attemptedAdd(value));
						}
					}

					@Override
					public <V extends T> T set(V value, Object cause) throws IllegalArgumentException {
						if (isElementSettable())
							return attemptSet(getWrapped().get(), value, cause);
						else
							return wrap(((CollectionElement<E>) getWrapped()).set(attemptedAdd(value), cause));
					}
				};
				return el -> {
					container[0] = el;
					return wrapping;
				};
			};
		}

		@Override
		public boolean add(T e) {
			return ((ReversibleList<E>) theWrapped).add(attemptedAdd(e));
		}

		@Override
		public boolean addAll(Collection<? extends T> c) {
			return ((ReversibleList<E>) theWrapped).addAll(c.stream().map(this::attemptedAdd).collect(Collectors.toList()));
		}

		@Override
		public void add(int index, T element) {
			((ReversibleList<E>) theWrapped).add(index, attemptedAdd(element));
		}

		@Override
		public boolean addAll(int index, Collection<? extends T> c) {
			return ((ReversibleList<E>) theWrapped).addAll(index, c.stream().map(this::attemptedAdd).collect(Collectors.toList()));
		}

		@Override
		public boolean removeLast(Object value) {
			return theWrapped.find(v -> equivalence().elementEquals(wrap(v), value), el -> {
				attemptedRemove(el.get());
				el.remove();
			}, false);
		}

		@Override
		public void removeRange(int fromIndex, int toIndex) {
			if (!isRemoveRestricted())
				theWrapped.removeRange(fromIndex, toIndex);
			else {
				try (Transaction t = lock(true, null)) {
					int size = size();
					for (int i = fromIndex; i < toIndex && i < size; i++)
						attemptedRemove(theWrapped.get(i));
					theWrapped.removeRange(fromIndex, toIndex);
				}
			}
		}

		@Override
		public boolean remove(Object o) {
			return theWrapped.find(v -> equivalence().elementEquals(wrap(v), o), el -> {
				attemptedRemove(el.get());
				el.remove();
			});
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			if (c.isEmpty())
				return true;
			Set<T> cSet = ObservableCollectionImpl.toSet(equivalence(), c);
			if (cSet.isEmpty())
				return false;
			return theWrapped.removeIf(v -> {
				boolean remove = cSet.contains(wrap(v));
				if (remove)
					attemptedRemove(v);
				return remove;
			});
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			if (c.isEmpty())
				return false;
			Set<T> cSet = ObservableCollectionImpl.toSet(equivalence(), c);
			if (cSet.isEmpty())
				return false;
			return theWrapped.removeIf(v -> {
				boolean remove = !cSet.contains(wrap(v));
				if (remove)
					attemptedRemove(v);
				return remove;
			});
		}

		@Override
		public void clear() {
			if (!isRemoveRestricted())
				theWrapped.clear();
			else {
				try (Transaction t = lock(true, null)) {
					for (E v : theWrapped)
						attemptedRemove(v);
					theWrapped.clear();
				}
			}
		}

		@Override
		public T remove(int index) {
			if (!isRemoveRestricted())
				return wrap(theWrapped.remove(index));
			else {
				try (Transaction t = lock(true, null)) {
					E value = theWrapped.get(index);
					attemptedRemove(value);
					return wrap(theWrapped.remove(index));
				}
			}
		}

		@Override
		public T set(int index, T element) {
			if (isElementSettable())
				return attemptSet(theWrapped.get(index), element, null);
			else if (isRemoveRestricted())
				return wrap(((ReversibleList<E>) theWrapped).set(index, attemptedAdd(element)));
			else {
				try (Transaction t = lock(true, null)) {
					E value = theWrapped.get(index);
					attemptedRemove(value);
					return wrap(((ReversibleList<E>) theWrapped).set(index, attemptedAdd(element)));
				}
			}
		}

		@Override
		public ListIterator<T> listIterator(int index) {
			return new SimpleMappedListIterator<E, T>(theWrapped.listIterator(index)) {
				@Override
				protected T wrap(E e) {
					return SimpleMappedSubList.this.wrap(e);
				}

				@Override
				protected void attemptRemove(E value) {
					SimpleMappedSubList.this.attemptedRemove(value);
				}

				@Override
				protected E attemptedAdd(T value) {
					return SimpleMappedSubList.this.attemptedAdd(value);
				}

				@Override
				protected boolean isElementSettable() {
					return SimpleMappedSubList.this.isElementSettable();
				}

				@Override
				protected T attemptSet(E container, T value, Object cause) {
					return SimpleMappedSubList.this.attemptSet(container, value, cause);
				}
			};
		}

		@Override
		public ReversibleList<T> subList(int fromIndex, int toIndex) {
			SimpleMappedSubList<E, T> outer = this;
			return new SimpleMappedSubList<E, T>(theWrapped.subList(fromIndex, toIndex), theType, theEquivalence, theTransactable) {
				@Override
				protected T wrap(E wrap) {
					return outer.wrap(wrap);
				}

				@Override
				protected boolean isRemoveRestricted() {
					return outer.isRemoveRestricted();
				}

				@Override
				protected String checkRemove(E value) {
					return outer.checkRemove(value);
				}

				@Override
				protected E attemptedRemove(E value) {
					return outer.attemptedRemove(value);
				}

				@Override
				protected String checkAdd(T value) {
					return outer.checkAdd(value);
				}

				@Override
				protected E attemptedAdd(T value) {
					return outer.attemptedAdd(value);
				}

				@Override
				protected boolean isElementSettable() {
					return outer.isElementSettable();
				}

				@Override
				protected String checkSet(E container, T value) {
					return outer.checkSet(container, value);
				}

				@Override
				protected T attemptSet(E container, T value, Object cause) {
					return outer.attemptSet(container, value, cause);
				}
			};
		}

		@Override
		public int hashCode() {
			try (Transaction t = theTransactable.lock(false, null)) {
				int hashCode = 1;
				for (Object e : this)
					hashCode += e.hashCode();
				return hashCode;
			}
		}

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof Collection))
				return false;
			Collection<?> c = (Collection<?>) o;

			try (Transaction t1 = theTransactable.lock(false, null); Transaction t2 = Transactable.lock(c, false, null)) {
				Iterator<T> e1 = iterator();
				Iterator<?> e2 = c.iterator();
				while (e1.hasNext() && e2.hasNext()) {
					T o1 = e1.next();
					Object o2 = e2.next();
					if (!equivalence().elementEquals(o1, o2))
						return false;
				}
				return !(e1.hasNext() || e2.hasNext());
			}
		}

		@Override
		public String toString() {
			StringBuilder ret = new StringBuilder("(");
			boolean first = true;
			try (Transaction t = theTransactable.lock(false, null)) {
				for (Object value : this) {
					if (!first) {
						ret.append(", ");
					} else
						first = false;
					ret.append(value);
				}
			}
			ret.append(')');
			return ret.toString();
		}
	}

	/**
	 * An implementation of {@link ObservableList#listIterator(int)} for derived lists that are one-to-one mappings of their source lists
	 *
	 * @param <E> The type of values in the source list
	 * @param <T> The type of values in the derived list
	 */
	public static abstract class SimpleMappedListIterator<E, T> implements ListIterator<T> {
		private final ListIterator<? extends E> theWrapped;
		private E lastValue;
		private boolean isRemoved;

		/** @param wrap The list iterator from the source list to wrap */
		public SimpleMappedListIterator(ListIterator<? extends E> wrap) {
			theWrapped = wrap;
		}

		/**
		 * @param e The source value
		 * @return The mapped value
		 */
		protected abstract T wrap(E e);
		/**
		 * @param value The value to attempt to remove
		 * @throw RuntimeException If the value may not be removed
		 */
		protected void attemptRemove(E value) {}
		/**
		 * @param value The value to attempt to add
		 * @return The value to add to the source collection
		 */
		protected abstract E attemptedAdd(T value);

		/** @return Whether individual values in the source collection are settable */
		protected abstract boolean isElementSettable();
		/**
		 * @param container The source value
		 * @param value The value to set
		 * @param cause The cause of the operation
		 * @return The value previously contained in the container
		 */
		protected abstract T attemptSet(E container, T value, Object cause);

		@Override
		public boolean hasNext() {
			return theWrapped.hasNext();
		}

		@Override
		public T next() {
			isRemoved = false;
			return wrap(lastValue = theWrapped.next());
		}

		@Override
		public boolean hasPrevious() {
			return theWrapped.hasPrevious();
		}

		@Override
		public T previous() {
			isRemoved = false;
			return wrap(lastValue = theWrapped.previous());
		}

		@Override
		public int nextIndex() {
			return theWrapped.nextIndex();
		}

		@Override
		public int previousIndex() {
			return theWrapped.previousIndex();
		}

		@Override
		public void remove() {
			attemptRemove(lastValue);
			theWrapped.remove();
			lastValue = null;
			isRemoved = true;
		}

		@Override
		public void set(T e) {
			if (isRemoved)
				throw new IllegalStateException("Element has been removed");
			if (isElementSettable())
				attemptSet(lastValue, e, null);
			else {
				attemptRemove(lastValue);
				((ListIterator<E>) theWrapped).set(attemptedAdd(e));
			}
		}

		@Override
		public void add(T e) {
			((ListIterator<E>) theWrapped).add(attemptedAdd(e));
		}
	}

	/**
	 * Implements {@link ObservableList#reverse()}
	 *
	 * @param <E> The type of elements in the collection
	 */
	public static class ReversedList<E> extends ObservableReversibleCollectionImpl.ObservableReversedCollection<E>
	implements ObservableList<E> {
		/** @param list The source list */
		protected ReversedList(ObservableList<E> list) {
			super(list);
		}

		@Override
		protected ObservableList<E> getWrapped() {
			return (ObservableList<E>) super.getWrapped();
		}

		@Override
		public ObservableList<E> reverse() {
			return getWrapped();
		}

		@Override
		public ObservableReversibleSpliterator<E> spliterator(int index) {
			try (Transaction t = lock(false, null)) {
				return getWrapped().spliterator(reflect(index, true));
			}
		}

		@Override
		public boolean add(E e) {
			getWrapped().add(0, e);
			return true;
		}

		@Override
		public boolean remove(Object o) {
			return getWrapped().removeLast(o);
		}

		@Override
		public boolean removeLast(Object o) {
			return getWrapped().remove(o);
		}

		@Override
		public boolean addAll(Collection<? extends E> c) {
			return getWrapped().addAll(0, reverse(c));
		}

		private static <T> Collection<T> reverse(Collection<T> coll) {
			List<T> copy = new ArrayList<>(coll);
			java.util.Collections.reverse(copy);
			return copy;
		}

		private int reflect(int index, boolean terminalInclusive) {
			int size = getWrapped().size();
			if (index < 0)
				throw new IndexOutOfBoundsException("" + index);
			if (index > size || (!terminalInclusive && index == size))
				throw new IndexOutOfBoundsException(index + " of " + size);
			int reflected = size - index;
			if (!terminalInclusive)
				reflected--;
			return reflected;
		}

		@Override
		public boolean addAll(int index, Collection<? extends E> c) {
			try (Transaction t = lock(true, null)) {
				return getWrapped().addAll(reflect(index, true), reverse(c));
			}
		}

		@Override
		public E get(int index) {
			try (Transaction t = lock(false, null)) {
				return getWrapped().get(reflect(index, false));
			}
		}

		@Override
		public E set(int index, E element) {
			try (Transaction t = lock(true, null)) {
				return getWrapped().set(reflect(index, false), element);
			}
		}

		@Override
		public void add(int index, E element) {
			try (Transaction t = lock(true, null)) {
				getWrapped().add(reflect(index, true), element);
			}
		}

		@Override
		public E remove(int index) {
			try (Transaction t = lock(true, null)) {
				return getWrapped().remove(reflect(index, false));
			}
		}

		@Override
		public void removeRange(int fromIndex, int toIndex) {
			try (Transaction t = lock(false, null)) {
				getWrapped().removeRange(reflect(toIndex, true), reflect(fromIndex, true));
			}
		}

		@Override
		public int indexOf(Object o) {
			try (Transaction t = lock(false, null)) {
				return reflect(getWrapped().lastIndexOf(o), false);
			}
		}

		@Override
		public int lastIndexOf(Object o) {
			try (Transaction t = lock(false, null)) {
				return reflect(getWrapped().indexOf(o), false);
			}
		}

		@Override
		public ListIterator<E> listIterator() {
			return listIterator(0);
		}

		@Override
		public ListIterator<E> listIterator(int index) {
			if (index < 0)
				throw new IndexOutOfBoundsException("" + index);
			try (Transaction t = getWrapped().lock(false, null)) {
				return new ReversedListIterator<>(getWrapped().listIterator(reflect(index, true)), () -> getWrapped().size());
			}
		}

		@Override
		public ReversibleList<E> subList(int fromIndex, int toIndex) {
			if (fromIndex < 0)
				throw new IndexOutOfBoundsException("" + fromIndex);
			if (fromIndex > toIndex)
				throw new IndexOutOfBoundsException(fromIndex + ">" + toIndex);
			try (Transaction t = getWrapped().lock(false, null)) {
				return getWrapped().subList(reflect(toIndex, true), reflect(fromIndex, true)).reverse();
			}
		}
	}

	/**
	 * Implements {@link ObservableList#withEquivalence(Equivalence)}
	 *
	 * @param <E> The type of values in the list
	 */
	public static class EquivalenceSwitchedObservableList<E> extends EquivalenceSwitchedReversibleCollection<E>
	implements ObservableList<E> {
		/**
		 * @param wrap The source list
		 * @param equivalence The equivalence for this list
		 */
		protected EquivalenceSwitchedObservableList(ObservableList<E> wrap, Equivalence<? super E> equivalence) {
			super(wrap, equivalence);
		}

		@Override
		protected ObservableList<E> getWrapped() {
			return (ObservableList<E>) super.getWrapped();
		}

		@Override
		public void add(int index, E element) {
			getWrapped().add(index, element);
		}

		@Override
		public boolean addAll(int index, Collection<? extends E> c) {
			return getWrapped().addAll(index, c);
		}

		@Override
		public boolean removeLast(Object o) {
			remove(o);
			return find(v -> equivalence().elementEquals(v, o), el -> el.remove(), false);
		}

		@Override
		public E remove(int index) {
			return getWrapped().remove(index);
		}

		@Override
		public void removeRange(int fromIndex, int toIndex) {
			getWrapped().removeRange(fromIndex, toIndex);
		}

		@Override
		public E set(int index, E element) {
			return getWrapped().set(index, element);
		}

		@Override
		public ObservableReversibleSpliterator<E> spliterator(int index) {
			return getWrapped().spliterator(index);
		}

		@Override
		public ListIterator<E> listIterator(int index) {
			return getWrapped().listIterator(index);
		}

		@Override
		public ReversibleList<E> subList(int fromIndex, int toIndex) {
			return new SimpleMappedSubList<E, E>(getWrapped().subList(fromIndex, toIndex), getType(), equivalence(), this) {
				@Override
				protected E wrap(E wrap) {
					return wrap;
				}

				@Override
				protected String checkAdd(E value) {
					return canAdd(value);
				}

				@Override
				protected E attemptedAdd(E value) {
					return value;
				}

				@Override
				protected boolean isElementSettable() {
					return false;
				}

				@Override
				protected String checkSet(E container, E value) {
					return null;
				}

				@Override
				protected E attemptSet(E container, E value, Object cause) {
					return null;
				}
			};
		}
	}

	/**
	 * Implements {@link ObservableList#filterMap(ObservableCollection.FilterMapDef)}
	 *
	 * @param <E> The type of values in the source collection
	 * @param <T> The type of values in this collection
	 */
	public static class FilterMappedObservableList<E, T> extends FilterMappedReversibleCollection<E, T> implements ObservableList<T> {
		/**
		 * @param wrap The source collection
		 * @param filterMapDef The definition of which values are filtered and how they are mapped
		 */
		protected FilterMappedObservableList(ObservableList<E> wrap, FilterMapDef<E, ?, T> filterMapDef) {
			super(wrap, filterMapDef);
		}

		@Override
		protected ObservableList<E> getWrapped() {
			return (ObservableList<E>) super.getWrapped();
		}

		/**
		 * @param index The index in this collection
		 * @param includeTerminus Whether the terminal index (size()) is valid for the index
		 * @return The corresponding index in the source collection
		 */
		protected int sourceIndex(int index, boolean includeTerminus) {
			if (!getDef().isFiltered())
				return index;
			if (index < 0)
				throw new IndexOutOfBoundsException("" + index);
			int srcIndex = 0;
			int mappedIndex = 0;
			FilterMapResult<E, T> res = new FilterMapResult<>();
			for (E v : getWrapped()) {
				res.source = v;
				if (getDef().map(res).error == null) {
					if (mappedIndex == index)
						return srcIndex;
					mappedIndex++;
				}
				srcIndex++;
			}
			if (includeTerminus && mappedIndex == index)
				return srcIndex;
			throw new IndexOutOfBoundsException(index + " of " + mappedIndex);
		}

		@Override
		public ObservableReversibleSpliterator<T> spliterator(int index) {
			try (Transaction t = lock(false, null)) {
				return new WrappingReversibleObservableSpliterator<>(getWrapped().spliterator(sourceIndex(index, true)), getType(), map());
			}
		}

		@Override
		public void add(int index, T element) {
			if (!getDef().isReversible() || !getDef().checkDestType(element))
				throw new IllegalArgumentException(StdMsg.BAD_TYPE);
			FilterMapResult<T, E> reversed = getDef().reverse(new FilterMapResult<>(element));
			if (reversed.error != null)
				throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
			try (Transaction t = lock(true, null)) {
				getWrapped().add(sourceIndex(index, true), reversed.result);
			}
		}

		@Override
		public boolean addAll(int index, Collection<? extends T> c) {
			if (!getDef().isReversible())
				return false;
			try (Transaction t = lock(true, null)) {
				return getWrapped().addAll(sourceIndex(index, true), reverse(c));
			}
		}

		@Override
		public T remove(int index) {
			try (Transaction t = lock(true, null)) {
				return getDef().map(new FilterMapResult<>(getWrapped().remove(sourceIndex(index, false)))).result;
			}
		}

		@Override
		public void removeRange(int fromIndex, int toIndex) {
			try (Transaction t = lock(true, null)) {
				getWrapped().removeRange(sourceIndex(fromIndex, true), sourceIndex(toIndex, true));
			}
		}

		@Override
		public T set(int index, T element) {
			if (!getDef().isReversible() || !getDef().checkDestType(element))
				throw new IllegalArgumentException(StdMsg.BAD_TYPE);
			FilterMapResult<T, E> reversed = getDef().reverse(new FilterMapResult<>(element));
			if (reversed.error != null)
				throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
			try (Transaction t = lock(true, null)) {
				return getDef().map(new FilterMapResult<>(getWrapped().set(sourceIndex(index, false), reversed.result))).result;
			}
		}

		/**
		 * @param e The source value
		 * @return The mapped value
		 */
		protected T map(E e) {
			return getDef().map(new FilterMapResult<>(e)).result;
		}

		/**
		 * @param value The value to add to this collection
		 * @return The value to add to the source collection
		 */
		protected E attemptedAdd(T value) {
			if (!getDef().isReversible())
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			FilterMapResult<T, E> res = getDef().reverse(new FilterMapResult<>(value));
			if (res.error != null)
				throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
			return res.result;
		}

		@Override
		public ListIterator<T> listIterator(int index) {
			if (!getDef().isFiltered()) {
				return new SimpleMappedListIterator<E, T>(getWrapped().listIterator(index)) {
					@Override
					protected T wrap(E e) {
						return FilterMappedObservableList.this.map(e);
					}

					@Override
					protected E attemptedAdd(T value) {
						return FilterMappedObservableList.this.attemptedAdd(value);
					}

					@Override
					protected boolean isElementSettable() {
						return false;
					}

					@Override
					protected T attemptSet(E container, T value, Object cause) {
						return null;
					}
				};
			}

			ObservableReversibleSpliterator<T> spliter = spliterator(index);
			return new ReversibleSpliterator.PartialListIterator<T>(spliter) {
				private int theNextIndex = index;

				@Override
				public T next() {
					theNextIndex++;
					return super.next();
				}

				@Override
				public T previous() {
					theNextIndex--;
					return super.previous();
				}

				@Override
				public int nextIndex() {
					return theNextIndex;
				}

				@Override
				public int previousIndex() {
					return theNextIndex - 1;
				}

				@Override
				public void add(T e) {
					// The spliterator doesn't support addition. If I use the main list to add, the spliterator may become invalid.
					// TODO There are potentially way to support this, but it's rather difficult.
					throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
				}
			};
		}

		@Override
		public ReversibleList<T> subList(int fromIndex, int toIndex) {
			if (!getDef().isFiltered()) {
				ReversibleList<E> wrapSub = getWrapped().subList(fromIndex, toIndex);
				return new SimpleMappedSubList<E, T>(wrapSub, getType(), equivalence(), this) {
					@Override
					protected T wrap(E wrap) {
						return FilterMappedObservableList.this.map(wrap);
					}

					@Override
					protected String checkAdd(T value) {
						return canAdd(value);
					}

					@Override
					protected E attemptedAdd(T value) {
						return FilterMappedObservableList.this.attemptedAdd(value);
					}

					@Override
					protected boolean isElementSettable() {
						return false;
					}

					@Override
					protected String checkSet(E container, T value) {
						return null;
					}

					@Override
					protected T attemptSet(E container, T value, Object cause) {
						return null;
					}
				};
			}
			// TODO Auto-generated method stub
		}
	}

	/**
	 * Implements {@link ObservableList#combine(ObservableCollection.CombinedCollectionDef)}
	 *
	 * @param <E> The type of values in the source list
	 * @param <V> The type of values in this list
	 */
	public static class CombinedObservableList<E, V> extends CombinedReversibleCollection<E, V> implements ObservableList<V> {
		/**
		 * @param wrap The source list
		 * @param def The combination definition containing the observables to combine and the functions to use to combine the values
		 */
		protected CombinedObservableList(ObservableReversibleCollection<E> wrap, CombinedCollectionDef<E, V> def) {
			super(wrap, def);
		}

		@Override
		protected ObservableList<E> getWrapped() {
			return (ObservableList<E>) super.getWrapped();
		}

		@Override
		public void add(int index, V element) {
			if (getDef().getReverse() == null)
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			else if (element == null || !getDef().areNullsReversed())
				throw new UnsupportedOperationException(StdMsg.NULL_DISALLOWED);
			else
				getWrapped().add(index, getDef().getReverse().apply(new DynamicCombinedValues<>(element)));
		}

		@Override
		public boolean addAll(int index, Collection<? extends V> c) {
			if (getDef().getReverse() == null)
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			if (!getDef().areNullsReversed()) {
				for (V v : c)
					if (v == null)
						throw new UnsupportedOperationException(StdMsg.NULL_DISALLOWED);
			}
			Map<ObservableValue<?>, Object> argValues = new HashMap<>(getDef().getArgs().size() * 4 / 3);
			for (ObservableValue<?> arg : getDef().getArgs())
				argValues.put(arg, arg.get());
			StaticCombinedValues<V> combined = new StaticCombinedValues<>();
			combined.argValues = argValues;
			return getWrapped().addAll(index, c.stream().map(o -> {
				combined.element = o;
				return getDef().getReverse().apply(combined);
			}).collect(Collectors.toList()));
		}

		@Override
		public V set(int index, V element) {
			if (getDef().getReverse() == null)
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			else if (element == null || !getDef().areNullsReversed())
				throw new UnsupportedOperationException(StdMsg.NULL_DISALLOWED);
			else
				return combine(getWrapped().set(index, getDef().getReverse().apply(new DynamicCombinedValues<>(element))));
		}

		@Override
		public V remove(int index) {
			return combine(getWrapped().remove(index));
		}

		@Override
		public void removeRange(int fromIndex, int toIndex) {
			getWrapped().removeRange(fromIndex, toIndex);
		}

		@Override
		public ObservableReversibleSpliterator<V> spliterator(int index) {
			return new WrappingReversibleObservableSpliterator<>(getWrapped().spliterator(index), getType(), map());
		}

		@Override
		public ListIterator<V> listIterator(int index) {
			return new SimpleMappedListIterator<E, V>(getWrapped().listIterator(index)) {
				@Override
				protected V wrap(E e) {
					return combine(e);
				}

				@Override
				protected E attemptedAdd(V value) {
					if (getDef().getReverse() == null)
						throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
					else if (value == null || !getDef().areNullsReversed())
						throw new UnsupportedOperationException(StdMsg.NULL_DISALLOWED);
					return getDef().getReverse().apply(new DynamicCombinedValues<>(value));
				}

				@Override
				protected boolean isElementSettable() {
					return false;
				}

				@Override
				protected V attemptSet(E container, V value, Object cause) {
					return null;
				}
			};
		}

		@Override
		public ReversibleList<V> subList(int fromIndex, int toIndex) {
			return new SimpleMappedSubList<E, V>(getWrapped().subList(fromIndex, toIndex), getType(), equivalence(), this) {
				@Override
				protected V wrap(E wrap) {
					return combine(wrap);
				}

				@Override
				protected String checkAdd(V value) {
					if (getDef().getReverse() == null)
						return StdMsg.UNSUPPORTED_OPERATION;
					else if (value == null || !getDef().areNullsReversed())
						return StdMsg.NULL_DISALLOWED;
					return getWrapped().canAdd(getDef().getReverse().apply(new DynamicCombinedValues<>(value)));
				}

				@Override
				protected E attemptedAdd(V value) {
					if (getDef().getReverse() == null)
						throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
					else if (value == null || !getDef().areNullsReversed())
						throw new UnsupportedOperationException(StdMsg.NULL_DISALLOWED);
					return getDef().getReverse().apply(new DynamicCombinedValues<>(value));
				}

				@Override
				protected boolean isElementSettable() {
					return false;
				}

				@Override
				protected String checkSet(E container, V value) {
					return null;
				}

				@Override
				protected V attemptSet(E container, V value, Object cause) {
					return null;
				}
			};
		}
	}

	/**
	 * Implements {@link ObservableList#filterModification(ObservableCollection.ModFilterDef)}
	 *
	 * @param <E> The type of values in the list
	 */
	public static class ModFilteredObservableList<E> extends ModFilteredReversibleCollection<E> implements ObservableList<E> {
		/**
		 * @param wrapped The source list
		 * @param def The definition for how the list may be modified
		 */
		protected ModFilteredObservableList(ObservableList<E> wrapped, ModFilterDef<E> def) {
			super(wrapped, def);
		}

		@Override
		protected ObservableList<E> getWrapped() {
			return (ObservableList<E>) super.getWrapped();
		}

		@Override
		public void add(int index, E element) {
			getWrapped().add(index, getDef().tryAdd(element));
		}

		@Override
		public boolean addAll(int index, Collection<? extends E> c) {
			return getWrapped().addAll(index, c.stream().map(v -> {
				if (!getType().getRawType().isInstance(v))
					throw new IllegalArgumentException(StdMsg.BAD_TYPE);
				return getDef().tryAdd(v);
			}).collect(Collectors.toList()));
		}

		@Override
		public E remove(int index) {
			try (Transaction t = lock(true, null)) {
				E val = getWrapped().get(index);
				String msg = getDef().checkRemove(val);
				if (msg != null)
					throw new IllegalArgumentException(msg);
				return getWrapped().remove(index);
			}
		}

		@Override
		public void removeRange(int fromIndex, int toIndex) {
			try (Transaction t = lock(true, null)) {
				for (int i = fromIndex; i < toIndex && i < size(); i++) {
					E val = getWrapped().get(i);
					String msg = getDef().checkRemove(val);
					if (msg != null)
						throw new IllegalArgumentException(msg);
				}
				getWrapped().removeRange(fromIndex, toIndex);
			}
		}

		@Override
		public E set(int index, E element) {
			getDef().tryAdd(element);
			try (Transaction t = lock(true, null)) {
				E val = getWrapped().get(index);
				String msg = getDef().checkRemove(val);
				if (msg != null)
					throw new IllegalArgumentException(msg);
				return getWrapped().set(index, element);
			}
		}

		@Override
		public ObservableReversibleSpliterator<E> spliterator(int index) {
			return new WrappingReversibleObservableSpliterator<>(getWrapped().spliterator(index), getType(), map());
		}

		@Override
		public ListIterator<E> listIterator(int index) {
			return new SimpleMappedListIterator<E, E>(getWrapped().listIterator(index)) {
				@Override
				protected E wrap(E e) {
					return e;
				}

				@Override
				protected void attemptRemove(E value) {
					getDef().tryRemove(value);
				}

				@Override
				protected E attemptedAdd(E value) {
					return getDef().tryAdd(value);
				}

				@Override
				protected boolean isElementSettable() {
					return false;
				}

				@Override
				protected E attemptSet(E container, E value, Object cause) {
					return null;
				}
			};
		}

		@Override
		public ReversibleList<E> subList(int fromIndex, int toIndex) {
			return new SimpleMappedSubList<E, E>(getWrapped().subList(fromIndex, toIndex), getType(), equivalence(), this) {
				@Override
				protected E wrap(E wrap) {
					return wrap;
				}

				@Override
				protected boolean isRemoveRestricted() {
					return getDef().isRemoveFiltered();
				}

				@Override
				protected String checkRemove(E value) {
					return getDef().checkRemove(value);
				}

				@Override
				protected E attemptedRemove(E value) {
					return getDef().tryRemove(value);
				}

				@Override
				protected String checkAdd(E value) {
					return getDef().checkAdd(value);
				}

				@Override
				protected E attemptedAdd(E value) {
					return getDef().tryAdd(value);
				}

				@Override
				protected boolean isElementSettable() {
					return false;
				}

				@Override
				protected String checkSet(E container, E value) {
					return null;
				}

				@Override
				protected E attemptSet(E container, E value, Object cause) {
					return null;
				}
			};
		}
	}

	/**
	 * Implements {@link ObservableList#cached(Observable)}
	 *
	 * @param <E> The type of values in the list
	 */
	public static class CachedObservableList<E> extends CachedReversibleCollection<E> implements ObservableList<E> {
		/**
		 * @param wrapped The source list
		 * @param until The observable that destroys this list
		 */
		protected CachedObservableList(ObservableList<E> wrapped, Observable<?> until) {
			super(wrapped, until);
		}

		@Override
		protected ObservableList<E> getWrapped() {
			return (ObservableList<E>) super.getWrapped();
		}

		@Override
		protected ReversibleList<E> createCache() {
			return CircularArrayList.build().unsafe().build();
		}

		@Override
		protected ReversibleList<E> getCache() {
			return (ReversibleList<E>) super.getCache();
		}

		@Override
		public void add(int index, E element) {
			if (isDone())
				throw new IllegalStateException("This cached collection's finisher has fired");
			getWrapped().add(index, element);
		}

		@Override
		public boolean addAll(int index, Collection<? extends E> c) {
			if (isDone())
				throw new IllegalStateException("This cached collection's finisher has fired");
			return getWrapped().addAll(index, c);
		}

		@Override
		public E remove(int index) {
			if (isDone())
				throw new IllegalStateException("This cached collection's finisher has fired");
			return getWrapped().remove(index);
		}

		@Override
		public void removeRange(int fromIndex, int toIndex) {
			if (isDone())
				throw new IllegalStateException("This cached collection's finisher has fired");
			getWrapped().removeRange(fromIndex, toIndex);
		}

		@Override
		public E set(int index, E element) {
			if (isDone())
				throw new IllegalStateException("This cached collection's finisher has fired");
			return getWrapped().set(index, element);
		}

		@Override
		public ObservableReversibleSpliterator<E> spliterator(int index) {
			if (isDone())
				throw new IllegalStateException("This cached collection's finisher has fired");
			return new WrappingReversibleObservableSpliterator<>(getWrapped().spliterator(index), getType(), map());
		}

		@Override
		public ListIterator<E> listIterator(int index) {
			if (isDone())
				throw new IllegalStateException("This cached collection's finisher has fired");
			// TODO Auto-generated method stub
		}

		@Override
		public ReversibleList<E> subList(int fromIndex, int toIndex) {
			if (isDone())
				throw new IllegalStateException("This cached collection's finisher has fired");
			// TODO Auto-generated method stub
		}
	}

	/**
	 * Implements {@link ObservableList#refresh(Observable)}
	 *
	 * @param <E> The type of the collection to refresh
	 */
	public static class RefreshingList<E> extends RefreshingReversibleCollection<E> implements ObservableList<E> {
		/**
		 * @param wrap The source list
		 * @param refresh The observable that refreshes this list's elements
		 */
		protected RefreshingList(ObservableList<E> wrap, Observable<?> refresh) {
			super(wrap, refresh);
		}

		@Override
		protected ObservableList<E> getWrapped() {
			return (ObservableList<E>) super.getWrapped();
		}

		@Override
		public E get(int index) {
			return getWrapped().get(index);
		}

		@Override
		public ObservableReversibleSpliterator<E> spliterator(int index) {
			return getWrapped().spliterator(index);
		}

		@Override
		public void add(int index, E element) {
			getWrapped().add(index, element);
		}

		@Override
		public boolean addAll(int index, Collection<? extends E> c) {
			return getWrapped().addAll(index, c);
		}

		@Override
		public E set(int index, E element) {
			return getWrapped().set(index, element);
		}

		@Override
		public E remove(int index) {
			return getWrapped().remove(index);
		}

		@Override
		public void removeRange(int fromIndex, int toIndex) {
			getWrapped().removeRange(fromIndex, toIndex);
		}

		@Override
		public boolean removeLast(Object o) {
			return getWrapped().removeLast(o);
		}

		@Override
		public ListIterator<E> listIterator(int index) {
			return getWrapped().listIterator(index);
		}

		@Override
		public ReversibleList<E> subList(int fromIndex, int toIndex) {
			return getWrapped().subList(fromIndex, toIndex);
		}
	}

	/**
	 * Implements {@link ObservableList#refreshEach(Function)}
	 *
	 * @param <E> The type of the collection to refresh
	 */
	public static class ElementRefreshingList<E> extends ObservableReversibleCollectionImpl.ElementRefreshingReversibleCollection<E>
	implements ObservableList<E> {
		/**
		 * @param wrap The source list
		 * @param refresh The function of observables that refresh this list's elements
		 */
		protected ElementRefreshingList(ObservableList<E> wrap, Function<? super E, Observable<?>> refresh) {
			super(wrap, refresh);
		}

		@Override
		protected ObservableList<E> getWrapped() {
			return (ObservableList<E>) super.getWrapped();
		}

		@Override
		public E get(int index) {
			return getWrapped().get(index);
		}

		@Override
		public ObservableReversibleSpliterator<E> spliterator(int index) {
			return getWrapped().spliterator(index);
		}

		@Override
		public void add(int index, E element) {
			getWrapped().add(index, element);
		}

		@Override
		public boolean addAll(int index, Collection<? extends E> c) {
			return getWrapped().addAll(index, c);
		}

		@Override
		public E set(int index, E element) {
			return getWrapped().set(index, element);
		}

		@Override
		public E remove(int index) {
			return getWrapped().remove(index);
		}

		@Override
		public void removeRange(int fromIndex, int toIndex) {
			getWrapped().removeRange(fromIndex, toIndex);
		}

		@Override
		public boolean removeLast(Object o) {
			return getWrapped().removeLast(o);
		}

		@Override
		public ListIterator<E> listIterator(int index) {
			return getWrapped().listIterator(index);
		}

		@Override
		public ReversibleList<E> subList(int fromIndex, int toIndex) {
			return getWrapped().subList(fromIndex, toIndex);
		}
	}

	/**
	 * Backs {@link ObservableList#takeUntil(Observable)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	public static class TakenUntilObservableList<E> extends ObservableReversibleCollectionImpl.TakenUntilReversibleCollection<E>
	implements ObservableList<E> {
		/**
		 * @param wrap The source list
		 * @param until The observable that terminates this list's observers
		 * @param terminate Whether the until observable removes all elements from the observers as well
		 */
		protected TakenUntilObservableList(ObservableList<E> wrap, Observable<?> until, boolean terminate) {
			super(wrap, until, terminate);
		}

		@Override
		public ObservableList<E> getWrapped() {
			return (ObservableList<E>) super.getWrapped();
		}

		@Override
		public E get(int index) {
			return getWrapped().get(index);
		}

		@Override
		public ObservableReversibleSpliterator<E> spliterator(int index) {
			return getWrapped().spliterator(index);
		}

		@Override
		public void add(int index, E element) {
			getWrapped().add(index, element);
		}

		@Override
		public boolean addAll(int index, Collection<? extends E> c) {
			return getWrapped().addAll(index, c);
		}

		@Override
		public E set(int index, E element) {
			return getWrapped().set(index, element);
		}

		@Override
		public E remove(int index) {
			return getWrapped().remove(index);
		}

		@Override
		public void removeRange(int fromIndex, int toIndex) {
			getWrapped().removeRange(fromIndex, toIndex);
		}

		@Override
		public boolean removeLast(Object o) {
			return getWrapped().removeLast(o);
		}

		@Override
		public ListIterator<E> listIterator(int index) {
			return getWrapped().listIterator(index);
		}

		@Override
		public ReversibleList<E> subList(int fromIndex, int toIndex) {
			return getWrapped().subList(fromIndex, toIndex);
		}
	}

	/**
	 * Implements {@link ObservableList#flattenValues(ObservableList)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	public static class FlattenedObservableValuesList<E> extends FlattenedReversibleValuesCollection<E>
	implements ObservableList<E> {
		/** @param collection The list of values to flatten */
		protected FlattenedObservableValuesList(ObservableList<? extends ObservableValue<? extends E>> collection) {
			super(collection);
		}

		@Override
		protected ObservableList<? extends ObservableValue<? extends E>> getWrapped() {
			return (ObservableList<? extends ObservableValue<? extends E>>) super.getWrapped();
		}

		@Override
		public E get(int index) {
			ObservableValue<? extends E> v = getWrapped().get(index);
			return v == null ? null : v.get();
		}

		@Override
		public ObservableReversibleSpliterator<E> spliterator(int index) {
			return new WrappingReversibleObservableSpliterator<>(getWrapped().spliterator(index), getType(), map());
		}

		@Override
		public void add(int index, E element) {
			((ObservableList<ObservableValue<E>>) getWrapped()).add(index, attemptedAdd(element));
		}

		@Override
		public boolean addAll(int index, Collection<? extends E> c) {
			return ((ObservableList<ObservableValue<E>>) getWrapped()).addAll(index,
				c.stream().map(this::attemptedAdd).collect(Collectors.toList()));
		}

		@Override
		public E set(int index, E element) {
			ObservableValue<? extends E> ov = getWrapped().get(index);
			if (ov instanceof SettableValue) {
				if (!ov.getType().getRawType().isInstance(element))
					throw new IllegalArgumentException(StdMsg.BAD_TYPE);
				return ((SettableValue<E>) ov).set(element, null);
			} else
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public E remove(int index) {
			return unwrap(getWrapped().remove(index));
		}

		@Override
		public void removeRange(int fromIndex, int toIndex) {
			getWrapped().removeRange(fromIndex, toIndex);
		}

		@Override
		public boolean removeLast(Object o) {
			return getWrapped().removeLast(o);
		}

		private String checkSet(ObservableValue<? extends E> container, E value) {
			if (container instanceof SettableValue) {
				if (!container.getType().getRawType().isInstance(value))
					return StdMsg.BAD_TYPE;
				return ((SettableValue<E>) container).isAcceptable(value);
			} else
				return StdMsg.UNSUPPORTED_OPERATION;
		}

		private E attemptedSet(ObservableValue<? extends E> container, E value, Object cause) {
			if (container instanceof SettableValue) {
				if (!container.getType().getRawType().isInstance(value))
					throw new IllegalArgumentException(StdMsg.BAD_TYPE);
				return ((SettableValue<E>) container).set(value, cause);
			} else
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public ListIterator<E> listIterator(int index) {
			return new SimpleMappedListIterator<ObservableValue<? extends E>, E>(getWrapped().listIterator(index)) {
				@Override
				protected E wrap(ObservableValue<? extends E> e) {
					return FlattenedObservableValuesList.this.unwrap(e);
				}

				@Override
				protected ObservableValue<? extends E> attemptedAdd(E value) {
					return FlattenedObservableValuesList.this.attemptedAdd(value);
				}

				@Override
				protected boolean isElementSettable() {
					return true;
				}

				@Override
				protected E attemptSet(ObservableValue<? extends E> container, E value, Object cause) {
					return FlattenedObservableValuesList.this.attemptedSet(container, value, cause);
				}
			};
		}

		@Override
		public ReversibleList<E> subList(int fromIndex, int toIndex) {
			return new SimpleMappedSubList<ObservableValue<? extends E>, E>(getWrapped().subList(fromIndex, toIndex), getType(),
				equivalence(), this) {
				@Override
				protected E wrap(ObservableValue<? extends E> wrap) {
					return FlattenedObservableValuesList.this.unwrap(wrap);
				}

				@Override
				protected String checkAdd(E value) {
					return FlattenedObservableValuesList.this.canAdd(value);
				}

				@Override
				protected ObservableValue<? extends E> attemptedAdd(E value) {
					return FlattenedObservableValuesList.this.attemptedAdd(value);
				}

				@Override
				protected boolean isElementSettable() {
					return true;
				}

				@Override
				protected String checkSet(ObservableValue<? extends E> container, E value) {
					return FlattenedObservableValuesList.this.checkSet(container, value);
				}

				@Override
				protected E attemptSet(ObservableValue<? extends E> container, E value, Object cause) {
					return FlattenedObservableValuesList.this.attemptedSet(container, value, cause);
				}
			};
		}
	}

	/**
	 * Implements {@link ObservableList#flattenValue(ObservableValue)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	public static class FlattenedObservableValueList<E> extends ObservableReversibleCollectionImpl.FlattenedReversibleValueCollection<E>
	implements ObservableList<E> {
		/** @param collectionObservable The value of lists to flatten */
		protected FlattenedObservableValueList(ObservableValue<? extends ObservableList<E>> collectionObservable) {
			super(collectionObservable);
		}

		@Override
		protected ObservableValue<? extends ObservableList<E>> getWrapped() {
			return (ObservableValue<? extends ObservableList<E>>) super.getWrapped();
		}

		@Override
		public E get(int index) {
			ObservableList<? extends E> list = getWrapped().get();
			if (list == null)
				throw new IndexOutOfBoundsException(index + " of 0");
			return list.get(index);
		}

		@Override
		public void add(int index, E element) {
			try (Transaction t = lock(true, null)) {
				ObservableList<? extends E> list = getWrapped().get();
				if (list == null)
					throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
				if (!list.getType().getRawType().isInstance(element))
					throw new IllegalArgumentException(StdMsg.BAD_TYPE);
				((ObservableList<E>) list).add(index, element);
			}
		}

		@Override
		public boolean addAll(int index, Collection<? extends E> c) {
			try (Transaction t = lock(true, null)) {
				ObservableList<? extends E> list = getWrapped().get();
				if (list == null)
					throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
				for (E v : c) {
					if (!list.getType().getRawType().isInstance(v))
						throw new IllegalArgumentException(StdMsg.BAD_TYPE);
				}
				return ((ObservableList<E>) list).addAll(index, c);
			}
		}

		@Override
		public E remove(int index) {
			try (Transaction t = lock(true, null)) {
				ObservableList<? extends E> list = getWrapped().get();
				if (list == null)
					throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
				return list.remove(index);
			}
		}

		@Override
		public void removeRange(int fromIndex, int toIndex) {
			try (Transaction t = lock(true, null)) {
				ObservableList<? extends E> list = getWrapped().get();
				if (list == null)
					throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
				list.removeRange(fromIndex, toIndex);
			}
		}

		@Override
		public E set(int index, E element) {
			try (Transaction t = lock(true, null)) {
				ObservableList<? extends E> list = getWrapped().get();
				if (list == null)
					throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
				if (!list.getType().getRawType().isInstance(element))
					throw new IllegalArgumentException(StdMsg.BAD_TYPE);
				return ((ObservableList<E>) list).set(index, element);
			}
		}

		@Override
		public ObservableReversibleSpliterator<E> spliterator(int index) {
			ObservableList<? extends E> list = getWrapped().get();
			if (list == null)
				return ObservableReversibleSpliterator.empty(getType());
			else
				return new WrappingReversibleObservableSpliterator<>(list.spliterator(index), getType(), map());
		}

		@Override
		public ListIterator<E> listIterator(int index) {
			ObservableList<? extends E> list = getWrapped().get();
			if (list == null)
				return Collections.<E> emptyList().listIterator(index);
			ListIterator<? extends E> iter = list.listIterator(index);
			return new ListIterator<E>() {
				@Override
				public boolean hasNext() {
					if (getWrapped().get() != list)
						throw new ConcurrentModificationException();
					return iter.hasNext();
				}

				@Override
				public E next() {
					return iter.next();
				}

				@Override
				public boolean hasPrevious() {
					if (getWrapped().get() != list)
						throw new ConcurrentModificationException();
					return iter.hasPrevious();
				}

				@Override
				public E previous() {
					if (getWrapped().get() != list)
						throw new ConcurrentModificationException();
					return iter.previous();
				}

				@Override
				public int nextIndex() {
					if (getWrapped().get() != list)
						throw new ConcurrentModificationException();
					return iter.nextIndex();
				}

				@Override
				public int previousIndex() {
					if (getWrapped().get() != list)
						throw new ConcurrentModificationException();
					return iter.previousIndex();
				}

				@Override
				public void remove() {
					if (getWrapped().get() != list)
						throw new ConcurrentModificationException();
					iter.remove();
				}

				@Override
				public void set(E e) {
					if (getWrapped().get() != list)
						throw new ConcurrentModificationException();
					if (!list.getType().getRawType().isInstance(e))
						throw new IllegalArgumentException(StdMsg.BAD_TYPE);
					((ListIterator<E>) iter).set(e);
				}

				@Override
				public void add(E e) {
					if (getWrapped().get() != list)
						throw new ConcurrentModificationException();
					if (!list.getType().getRawType().isInstance(e))
						throw new IllegalArgumentException(StdMsg.BAD_TYPE);
					((ListIterator<E>) iter).add(e);
				}
			};
		}

		@Override
		public ReversibleList<E> subList(int fromIndex, int toIndex) {
			ObservableList<? extends E> list = getWrapped().get();
			if (list == null)
				return ReversibleList.<E> empty().subList(fromIndex, toIndex);
			class RefCheckRevList implements ReversibleList<E> {
				private final ReversibleList<? extends E> subList;

				RefCheckRevList(ReversibleList<? extends E> subList) {
					this.subList = subList;
				}

				@Override
				public boolean addAll(int index, Collection<? extends E> c) {
					if (getWrapped().get() != list)
						throw new ConcurrentModificationException();
					// TODO Auto-generated method stub
				}

				@Override
				public E get(int index) {
					if (getWrapped().get() != list)
						throw new ConcurrentModificationException();
					return subList.get(index);
				}

				@Override
				public E set(int index, E element) {
					if (getWrapped().get() != list)
						throw new ConcurrentModificationException();
					// TODO Auto-generated method stub
				}

				@Override
				public void add(int index, E element) {
					if (getWrapped().get() != list)
						throw new ConcurrentModificationException();
					// TODO Auto-generated method stub
				}

				@Override
				public E remove(int index) {
					if (getWrapped().get() != list)
						throw new ConcurrentModificationException();
					return subList.remove(index);
				}

				@Override
				public int indexOf(Object o) {
					if (getWrapped().get() != list)
						throw new ConcurrentModificationException();
					if (!list.getType().getRawType().isInstance(o))
						return -1;
					return subList.indexOf(list);
				}

				@Override
				public int lastIndexOf(Object o) {
					if (getWrapped().get() != list)
						throw new ConcurrentModificationException();
					if (!list.getType().getRawType().isInstance(o))
						return -1;
					return subList.lastIndexOf(list);
				}

				@Override
				public ListIterator<E> listIterator(int index) {
					if (getWrapped().get() != list)
						throw new ConcurrentModificationException();
					// TODO Auto-generated method stub
				}

				@Override
				public boolean removeLast(Object o) {
					if (getWrapped().get() != list)
						throw new ConcurrentModificationException();
					if (!list.getType().getRawType().isInstance(o))
						return false;
					return subList.removeLast(list);
				}

				@Override
				public ReversibleSpliterator<E> spliterator(boolean fromStart) {
					if (getWrapped().get() != list)
						throw new ConcurrentModificationException();
					// TODO Auto-generated method stub
				}

				@Override
				public boolean containsAny(Collection<?> c) {
					if (getWrapped().get() != list)
						throw new ConcurrentModificationException();
					return subList.containsAny(c);
				}

				@Override
				public int size() {
					if (getWrapped().get() != list)
						throw new ConcurrentModificationException();
					return subList.size();
				}

				@Override
				public boolean isEmpty() {
					if (getWrapped().get() != list)
						throw new ConcurrentModificationException();
					return subList.isEmpty();
				}

				@Override
				public boolean contains(Object o) {
					if (getWrapped().get() != list)
						throw new ConcurrentModificationException();
					return subList.contains(o);
				}

				@Override
				public Object[] toArray() {
					if (getWrapped().get() != list)
						throw new ConcurrentModificationException();
					return subList.toArray();
				}

				@Override
				public <T> T[] toArray(T[] a) {
					if (getWrapped().get() != list)
						throw new ConcurrentModificationException();
					return subList.toArray(a);
				}

				@Override
				public boolean add(E e) {
					if (getWrapped().get() != list)
						throw new ConcurrentModificationException();
					// TODO Auto-generated method stub
				}

				@Override
				public boolean remove(Object o) {
					if (getWrapped().get() != list)
						throw new ConcurrentModificationException();
					return subList.remove(o);
				}

				@Override
				public boolean containsAll(Collection<?> c) {
					if (getWrapped().get() != list)
						throw new ConcurrentModificationException();
					return subList.containsAll(c);
				}

				@Override
				public boolean addAll(Collection<? extends E> c) {
					if (getWrapped().get() != list)
						throw new ConcurrentModificationException();
					// TODO Auto-generated method stub
				}

				@Override
				public boolean removeAll(Collection<?> c) {
					if (getWrapped().get() != list)
						throw new ConcurrentModificationException();
					return subList.removeAll(c);
				}

				@Override
				public boolean retainAll(Collection<?> c) {
					if (getWrapped().get() != list)
						throw new ConcurrentModificationException();
					return subList.retainAll(c);
				}

				@Override
				public void clear() {
					if (getWrapped().get() != list)
						throw new ConcurrentModificationException();
					subList.clear();
				}

				@Override
				public ReversibleSpliterator<E> spliterator(int index) {
					if (getWrapped().get() != list)
						throw new ConcurrentModificationException();
					// TODO Auto-generated method stub
				}

				@Override
				public ReversibleList<E> subList(int fromIndex, int toIndex) {
					return new RefCheckRevList(subList.subList(fromIndex, toIndex));
				}
			}
			return new RefCheckRevList(list.subList(fromIndex, toIndex));
		}
	}

	/**
	 * Implements {@link ObservableList#flatten(ObservableList)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	public static class FlattenedObservableList<E> extends ObservableReversibleCollectionImpl.FlattenedReversibleCollection<E>
	implements ObservableList<E> {
		/** @param outer The list of lists to flatten */
		protected FlattenedObservableList(ObservableList<? extends ObservableList<? extends E>> outer) {
			super(outer);
		}

		@Override
		protected ObservableList<? extends ObservableList<E>> getOuter() {
			return (ObservableList<? extends ObservableList<E>>) super.getOuter();
		}

		@Override
		public E get(int index) {
			int idx = index;
			for (ObservableList<? extends E> subList : getOuter()) {
				if (idx < subList.size())
					return subList.get(idx);
				else
					idx -= subList.size();
			}
			throw new IndexOutOfBoundsException(index + " out of " + size());
		}
	}

	/**
	 * Implements {@link ObservableList#constant(TypeToken, List)}
	 *
	 * @param <E> The type of values in the list
	 */
	public static class ConstantObservableList<E> extends ConstantObservableCollection<E> implements ObservableList<E> {
		/**
		 * @param type The type of the list
		 * @param collection The values in the list
		 */
		public ConstantObservableList(TypeToken<E> type, List<? extends E> collection) {
			super(type, collection);
		}

		@Override
		protected List<? extends E> getCollection() {
			return (List<? extends E>) super.getCollection();
		}

		@Override
		public E get(int index) {
			return getCollection().get(index);
		}

		@Override
		public int indexOf(Object value) {
			return getCollection().indexOf(value);
		}

		@Override
		public int lastIndexOf(Object value) {
			return getCollection().lastIndexOf(value);
		}

		@Override
		public void add(int index, E element) {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public boolean addAll(int index, Collection<? extends E> c) {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public boolean removeLast(Object o) {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public E remove(int index) {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public void removeRange(int fromIndex, int toIndex) {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public E set(int index, E element) {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public ObservableReversibleSpliterator<E> spliterator() {
			return spliterator(true);
		}

		@Override
		public ObservableReversibleSpliterator<E> spliterator(boolean fromStart) {
		}

		@Override
		public ObservableReversibleSpliterator<E> spliterator(int index) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public ListIterator<E> listIterator(int index) {
			return (ListIterator<E>) Collections.unmodifiableList(getCollection()).listIterator(index);
		}

		@Override
		public ReversibleList<E> subList(int fromIndex, int toIndex) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public CollectionSubscription subscribeOrdered(Consumer<? super OrderedCollectionEvent<? extends E>> observer) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public CollectionSubscription subscribeReverse(Consumer<? super OrderedCollectionEvent<? extends E>> observer) {
			// TODO Auto-generated method stub
			return null;
		}
	}

	/**
	 * Implements {@link ObservableList#asList(ObservableCollection)}
	 *
	 * @param <T> The type of the elements in the collection
	 */
	public static class CollectionWrappingList<T> implements ObservableList<T> {
		private final ObservableCollection<T> theWrapped;

		private final ListenerSet<Consumer<? super ObservableOrderedElement<T>>> theListeners;

		private final ReentrantReadWriteLock theLock;

		final List<WrappingListElement<T>> theElements;

		CollectionWrappingList(ObservableCollection<T> wrap) {
			theWrapped = wrap;
			theListeners = new ListenerSet<>();
			theLock = new ReentrantReadWriteLock();
			theElements = new ArrayList<>();
			theListeners.setUsedListener(new Consumer<Boolean>() {
				Subscription wrapSub;

				@Override
				public void accept(Boolean used) {
					Lock lock = theLock.writeLock();
					lock.lock();
					try {
						if(used) {
							boolean [] initialization = new boolean[] {true};
							wrapSub = theWrapped.onElement(element -> {
								int index = theElements.size();
								if(element instanceof ObservableOrderedElement)
									index = ((ObservableOrderedElement<T>) element).getIndex();
								WrappingListElement<T> listEl = new WrappingListElement<>(CollectionWrappingList.this, element);
								theElements.add(index, listEl);
								element.completed().act(event -> {
									Lock lock2 = theLock.writeLock();
									lock2.lock();
									try {
										int idx = listEl.getIndex();
										if(idx >= 0) {
											listEl.setRemovedIndex(idx);
											theElements.remove(idx);
										}
									} finally {
										lock2.unlock();
									}
								});
								if(!initialization[0]) {
									theListeners.forEach(listener -> {
										listener.accept(listEl);
									});
								}
							});
							initialization[0] = false;
						} else {
							wrapSub.unsubscribe();
							wrapSub = null;
							theElements.clear();
						}
					} finally {
						lock.unlock();
					}
				}
			});
			theListeners.setOnSubscribe(listener -> {
				Lock lock = theLock.readLock();
				lock.lock();
				try {
					for(WrappingListElement<T> el : theElements)
						listener.accept(el);
				} finally {
					lock.unlock();
				}
			});
		}

		@Override
		public TypeToken<T> getType() {
			return theWrapped.getType();
		}

		@Override
		public Subscription onOrderedElement(Consumer<? super ObservableOrderedElement<T>> onElement) {
			theListeners.add(onElement);
			return () -> {
				theListeners.remove(onElement);
			};
		}

		@Override
		public Subscription onElementReverse(Consumer<? super ObservableOrderedElement<T>> onElement) {
			boolean [] initialized = new boolean[1];
			Consumer<ObservableOrderedElement<T>> listener = element -> {
				if(initialized[0])
					onElement.accept(element);
			};
			theListeners.add(listener);
			initialized[0] = true;
			for(int i = theElements.size() - 1; i >= 0; i--)
				onElement.accept(theElements.unwrap(i));
			return () -> {
				theListeners.remove(listener);
			};
		}

		@Override
		public ObservableValue<CollectionSession> getSession() {
			return theWrapped.getSession();
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
		public boolean addAll(int index, Collection<? extends T> c) {
			throw new UnsupportedOperationException();
		}

		@Override
		public T get(int index) {
			Lock lock = theLock.readLock();
			lock.lock();
			try {
				if(theListeners.isUsed())
					return theElements.unwrap(index).get();
				else { // This is risky here. No guarantee that the collection preserves order between iterations
					int i = 0;
					for(T value : theWrapped) {
						if(i == index)
							return value;
						i++;
					}
					throw new IndexOutOfBoundsException(index + " of " + size());
				}
			} finally {
				lock.unlock();
			}
		}

		@Override
		public T set(int index, T element) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void add(int index, T element) {
			throw new UnsupportedOperationException();
		}

		@Override
		public T remove(int index) {
			T ret = get(index);
			theWrapped.remove(ret);
			return ret;
		}

		@Override
		public int indexOf(Object o) {
			Lock lock = theLock.readLock();
			lock.lock();
			try {
				if(theListeners.isUsed()) {
					int i = 0;
					for(WrappingListElement<T> el : theElements) {
						if(Objects.equals(el.get(), o))
							return i;
						i++;
					}
					return -1;
				} else { // This is risky here. No guarantee that the collection preserves order between iterations
					int i = 0;
					for(T value : theWrapped) {
						if(Objects.equals(value, o))
							return i;
						i++;
					}
					return -1;
				}
			} finally {
				lock.unlock();
			}
		}

		@Override
		public int lastIndexOf(Object o) {
			Lock lock = theLock.readLock();
			lock.lock();
			try {
				if(theListeners.isUsed()) {
					for(int i = theElements.size() - 1; i >= 0; i--) {
						if (Objects.equals(theElements.unwrap(i).get(), o))
							return i;
					}
					return -1;
				} else { // This is risky here. No guarantee that the collection preserves order between iterations
					int ret = -1;
					int i = 0;
					for(T value : theWrapped) {
						if(Objects.equals(value, o))
							ret = i;
						i++;
					}
					return ret;
				}
			} finally {
				lock.unlock();
			}
		}

		@Override
		public int size() {
			return theWrapped.size();
		}

		@Override
		public boolean isEmpty() {
			return theWrapped.isEmpty();
		}

		@Override
		public boolean contains(Object o) {
			return theWrapped.contains(o);
		}

		@Override
		public Iterator<T> iterator() {
			Lock lock = theLock.readLock();
			lock.lock();
			try {
				if(theListeners.isUsed())
					return new ArrayList<>(theElements.stream().map(el -> el.get()).collect(Collectors.toList())).iterator();
				else
					return theWrapped.iterator();
			} finally {
				lock.unlock();
			}
		}

		@Override
		public String canRemove(Object value) {
			return theWrapped.canRemove(value);
		}

		@Override
		public boolean canAdd(T value) {
			return theWrapped.canAdd(value);
		}

		@Override
		public T [] toArray() {
			Lock lock = theLock.readLock();
			lock.lock();
			try {
				return ObservableList.super.toArray();
			} finally {
				lock.unlock();
			}
		}

		@Override
		public <T2> T2 [] toArray(T2 [] a) {
			Lock lock = theLock.readLock();
			lock.lock();
			try {
				if(theListeners.isUsed())
					return theElements.stream().map(el -> el.get()).collect(Collectors.toList()).toArray(a);
				else
					return theWrapped.toArray(a);
			} finally {
				lock.unlock();
			}
		}

		@Override
		public boolean add(T e) {
			// Not supported because the contract of List says add must always return true, but the contract of the wrapped collection
			// is unknown
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean remove(Object o) {
			return theWrapped.remove(o);
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			return theWrapped.containsAll(c);
		}

		@Override
		public boolean addAll(Collection<? extends T> c) {
			// Not supported because the contract of List says add must always return true, but the contract of the wrapped collection
			// is unknown
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			return theWrapped.removeAll(c);
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			return theWrapped.retainAll(c);
		}

		@Override
		public void clear() {
			theWrapped.clear();
		}
	}
}
