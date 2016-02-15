package org.observe.collect;

import static org.observe.ObservableDebug.d;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.observe.DefaultObservable;
import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.Subscription;
import org.observe.util.ObservableUtils;
import org.qommons.ListenerSet;
import org.qommons.Transaction;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

/**
 * A list whose content can be observed. This list is immutable in that none of its methods, including {@link List} methods, can modify its
 * content (List modification methods will throw {@link UnsupportedOperationException}). All {@link ObservableElement}s returned by this
 * observable will be instances of {@link ObservableOrderedElement}.
 *
 * @param <E> The type of element in the list
 */
public interface ObservableList<E> extends ObservableReversibleCollection<E>, TransactableList<E> {
	@Override
	default boolean isEmpty() {
		return ObservableReversibleCollection.super.isEmpty();
	}

	@Override
	default boolean contains(Object o) {
		return ObservableReversibleCollection.super.contains(o);
	}

	@Override
	default boolean containsAll(Collection<?> o) {
		return ObservableReversibleCollection.super.containsAll(o);
	}

	@Override
	default E [] toArray() {
		return ObservableReversibleCollection.super.toArray();
	}

	@Override
	default <T> T [] toArray(T [] a) {
		return ObservableReversibleCollection.super.toArray(a);
	}

	@Override
	abstract E get(int index);

	@Override
	default int indexOf(Object o) {
		return ObservableReversibleCollection.super.indexOf(o);
	}

	@Override
	default int lastIndexOf(Object o) {
		return ObservableReversibleCollection.super.lastIndexOf(o);
	}

	@Override
	default void removeRange(int fromIndex, int toIndex) {
		try (Transaction t = lock(true, null)) {
			TransactableList.super.removeRange(fromIndex, toIndex);
		}
	}

	@Override
	default void clear() {
		removeRange(0, size());
	}

	@Override
	default ListIterator<E> listIterator() {
		return listIterator(0);
	}

	@Override
	default ListIterator<E> listIterator(int index) {
		return new SimpleListIterator<>(this, index);
	}

	/**
	 * A sub-list of this list. The returned list is backed by this list and updated along with it. The index arguments may be any
	 * non-negative value. If this list's size is {@code <=fromIndex}, the list will be empty. If {@code toIndex>} this list's size, the
	 * returned list's size may be less than {@code toIndex-fromIndex}.
	 *
	 * @see java.util.List#subList(int, int)
	 */
	@Override
	default List<E> subList(int fromIndex, int toIndex) {
		return new SubListImpl<>(this, this, fromIndex, toIndex);
	}

	@Override
	default Iterable<E> descending() {
		return new Iterable<E>() {
			@Override
			public Iterator<E> iterator() {
				return new Iterator<E>() {
					private final ListIterator<E> backing;

					{
						backing = listIterator(size());
					}

					@Override
					public boolean hasNext() {
						return backing.hasPrevious();
					}

					@Override
					public E next() {
						return backing.previous();
					}

					@Override
					public void remove() {
						backing.remove();
					}
				};
			}
		};
	}

	@Override
	default <T> ObservableList<T> map(Function<? super E, T> map) {
		return map((TypeToken<T>) TypeToken.of(map.getClass()).resolveType(Function.class.getTypeParameters()[1]), map);
	}

	@Override
	default <T> ObservableList<T> map(TypeToken<T> type, Function<? super E, T> map) {
		return map(type, map, null);
	}

	@Override
	default <T> ObservableList<T> map(TypeToken<T> type, Function<? super E, T> map, Function<? super T, E> reverse) {
		return d().debug(new MappedList<>(this, type, map, reverse)).from("map", this).using("map", map).using("reverse", reverse).get();
	}

	@Override
	default <T> ObservableList<T> filter(Class<T> type) {
		return (ObservableList<T>) ObservableReversibleCollection.super.filter(type);
	}

	@Override
	default ObservableList<E> filter(Predicate<? super E> filter) {
		return (ObservableList<E>) ObservableReversibleCollection.super.filter(filter);
	}

	@Override
	default ObservableList<E> filter(Predicate<? super E> filter, boolean staticFilter) {
		return (ObservableList<E>) ObservableReversibleCollection.super.filter(filter, staticFilter);
	}

	@Override
	default ObservableList<E> filterDynamic(Predicate<? super E> filter) {
		return (ObservableList<E>) ObservableReversibleCollection.super.filterDynamic(filter);
	}

	@Override
	default ObservableList<E> filterStatic(Predicate<? super E> filter) {
		return (ObservableList<E>) ObservableReversibleCollection.super.filterStatic(filter);
	}

	@Override
	default <T> ObservableList<T> filterMap(Function<? super E, T> map) {
		return (ObservableList<T>) ObservableReversibleCollection.super.filterMap(map);
	}

	@Override
	default <T> ObservableList<T> filterMap(TypeToken<T> type, Function<? super E, T> map, boolean staticFilter) {
		return (ObservableList<T>) ObservableReversibleCollection.super.filterMap(type, map, staticFilter);
	}

	@Override
	default <T> ObservableList<T> filterMap(TypeToken<T> type, Function<? super E, T> map, Function<? super T, E> reverse,
			boolean staticFilter) {
		return (ObservableList<T>) ObservableReversibleCollection.super.filterMap(type, map, reverse, staticFilter);
	}

	@Override
	default <T> ObservableList<T> filterMap2(TypeToken<T> type, Function<? super E, FilterMapResult<T>> map, Function<? super T, E> reverse,
			boolean staticFilter) {
		if (staticFilter)
			return d().debug(new StaticFilteredList<>(this, type, map, reverse)).from("filterMap", this).using("map", map)
					.using("reverse", reverse).get();
		else
			return d().debug(new DynamicFilteredList<>(this, type, map, reverse)).from("filterMap", this).using("map", map)
					.using("reverse", reverse).get();
	}

	@Override
	default <T, V> ObservableList<V> combine(ObservableValue<T> arg, BiFunction<? super E, ? super T, V> func) {
		return combine(arg, (TypeToken<V>) TypeToken.of(func.getClass()).resolveType(BiFunction.class.getTypeParameters()[2]), func);
	}

	@Override
	default <T, V> ObservableList<V> combine(ObservableValue<T> arg, TypeToken<V> type, BiFunction<? super E, ? super T, V> func) {
		return combine(arg, type, func, null);
	}

	@Override
	default <T, V> ObservableList<V> combine(ObservableValue<T> arg, TypeToken<V> type, BiFunction<? super E, ? super T, V> func,
			BiFunction<? super V, ? super T, E> reverse) {
		return d().debug(new CombinedObservableList<>(this, arg, type, func, reverse)).from("combine", this).from("with", arg)
				.using("combination", func).using("reverse", reverse).get();
	}

	@Override
	default ObservableList<E> refresh(Observable<?> refresh) {
		return d().debug(new RefreshingList<>(this, refresh)).from("refresh", this).from("on", refresh).get();
	}

	@Override
	default ObservableList<E> refreshEach(Function<? super E, Observable<?>> refire) {
		return d().debug(new ElementRefreshingList<>(this, refire)).from("refreshEach", this).using("on", refire).get();
	}

	@Override
	default ObservableList<E> immutable() {
		return d().debug(new ImmutableObservableList<>(this)).from("immutable", this).get();
	}

	@Override
	default ObservableList<E> filterRemove(Predicate<? super E> filter) {
		return (ObservableList<E>) ObservableReversibleCollection.super.filterRemove(filter);
	}

	@Override
	default ObservableList<E> noRemove() {
		return (ObservableList<E>) ObservableReversibleCollection.super.noRemove();
	}

	@Override
	default ObservableList<E> filterAdd(Predicate<? super E> filter) {
		return (ObservableList<E>) ObservableReversibleCollection.super.filterAdd(filter);
	}

	@Override
	default ObservableList<E> noAdd() {
		return (ObservableList<E>) ObservableReversibleCollection.super.noAdd();
	}

	@Override
	default ObservableList<E> filterModification(Predicate<? super E> removeFilter, Predicate<? super E> addFilter) {
		return new ModFilteredList<>(this, removeFilter, addFilter);
	}

	@Override
	default ObservableList<E> cached() {
		return d().debug(new SafeCachedObservableList<>(this)).from("cached", this).get();
	}

	@Override
	default ObservableList<E> takeUntil(Observable<?> until) {
		return d().debug(new TakenUntilObservableList<>(this, until, true)).from("take", this).from("until", until).tag("terminate", true)
				.get();
	}

	@Override
	default ObservableList<E> unsubscribeOn(Observable<?> until) {
		return d().debug(new TakenUntilObservableList<>(this, until, false)).from("take", this).from("until", until).tag("terminate", false)
				.get();
	}

	@Override
	default ObservableList<E> reverse() {
		return new ReversedList<>(this);
	}

	/**
	 * @param <T> The type of the value to wrap
	 * @param type The type of the elements in the list
	 * @param list The list of items for the new list
	 * @return An observable list whose contents are given and never changes
	 */
	public static <T> ObservableList<T> constant(TypeToken<T> type, List<T> list) {
		class ConstantObservableElement implements ObservableOrderedElement<T> {
			private final TypeToken<T> theType;
			private final T theValue;
			private final int theIndex;

			public ConstantObservableElement(T value, int index) {
				theType = type;
				theValue = value;
				theIndex = index;
			}

			@Override
			public Subscription subscribe(Observer<? super ObservableValueEvent<T>> observer) {
				observer.onNext(createInitialEvent(theValue));
				return () -> {
				};
			}

			@Override
			public ObservableValue<T> persistent() {
				return this;
			}

			@Override
			public int getIndex() {
				return theIndex;
			}

			@Override
			public TypeToken<T> getType() {
				return theType;
			}

			@Override
			public T get() {
				return theValue;
			}

			@Override
			public String toString() {
				return "" + theValue;
			}
		}
		List<T> constList = java.util.Collections.unmodifiableList(new ArrayList<>(list));
		List<ObservableOrderedElement<T>> obsEls = new java.util.ArrayList<>();
		class ConstantObservableList implements PartialListImpl<T> {
			@Override
			public ObservableValue<CollectionSession> getSession() {
				return ObservableValue.constant(TypeToken.of(CollectionSession.class), null);
			}

			@Override
			public Transaction lock(boolean write, Object cause) {
				return () -> {
				};
			}

			@Override
			public TypeToken<T> getType() {
				return type;
			}

			@Override
			public Subscription onOrderedElement(Consumer<? super ObservableOrderedElement<T>> observer) {
				for(ObservableOrderedElement<T> ob : obsEls)
					observer.accept(ob);
				return () -> {
				};
			}

			@Override
			public Subscription onElementReverse(Consumer<? super ObservableOrderedElement<T>> observer) {
				for(int i = obsEls.size() - 1; i >= 0; i--)
					observer.accept(obsEls.get(i));
				return () -> {
				};
			}

			@Override
			public T get(int index) {
				return constList.get(index);
			}

			@Override
			public int size() {
				return constList.size();
			}

			@Override
			public String toString() {
				return ObservableList.toString(this);
			}
		}
		ConstantObservableList ret = d().debug(new ConstantObservableList()).tag("constant", list).get();
		for(int i = 0; i < constList.size(); i++)
			obsEls.add(d().debug(new ConstantObservableElement(constList.get(i), i)).from("element", ret).tag("value", constList.get(i))
					.get());
		return ret;
	}

	/**
	 * @param <T> The type of the elements
	 * @param type The type of the elements in the list
	 * @param list The array of items for the new list
	 * @return An observable list whose contents are given and never changes
	 */
	public static <T> ObservableList<T> constant(TypeToken<T> type, T... list) {
		return constant(type, java.util.Arrays.asList(list));
	}

	/**
	 * Turns a list of observable values into a list composed of those holders' values
	 *
	 * @param <E> The type of elements held in the values
	 * @param collection The collection to flatten
	 * @return The flattened collection
	 */
	public static <E> ObservableList<E> flattenValues(ObservableList<? extends ObservableValue<? extends E>> collection) {
		return d().debug(new FlattenedObservableValuesList<E>(collection)).from("flatten", collection).get();
	}

	/**
	 * Turns an observable value containing an observable list into the contents of the value
	 *
	 * @param collectionObservable The observable value
	 * @return A list representing the contents of the value, or a zero-length list when null
	 */
	public static <E> ObservableList<E> flattenValue(ObservableValue<ObservableList<? extends E>> collectionObservable) {
		return d().debug(new FlattenedObservableValueList<>(collectionObservable)).from("flatten", collectionObservable).get();
	}

	/**
	 * Flattens a collection of lists.
	 *
	 * @param <E> The super-type of all list in the wrapping list
	 * @param list The list to flatten
	 * @return A list containing all elements of all lists in the outer list
	 */
	public static <E> ObservableList<E> flatten(ObservableList<? extends ObservableList<E>> list) {
		return d().debug(new FlattenedObservableList<>(list)).from("flatten", list).get();
	}

	/**
	 * @param <T> The supertype of elements in the lists
	 * @param type The super type of all possible lists in the outer list
	 * @param lists The lists to flatten
	 * @return An observable list that contains all the values of the given lists
	 */
	public static <T> ObservableList<T> flattenLists(TypeToken<T> type, ObservableList<T>... lists) {
		type = type.wrap();
		if(lists.length == 0)
			return constant(type);
		ObservableList<ObservableList<T>> wrapper = constant(new TypeToken<ObservableList<T>>() {}.where(new TypeParameter<T>() {}, type),
				lists);
		return flatten(wrapper);
	}

	/**
	 * @param <T> The type of the collection
	 * @param collection The collection to wrap as a list
	 * @return A list containing all elements in the collection, ordered and accessible by index
	 */
	public static <T> ObservableList<T> asList(ObservableCollection<T> collection) {
		if(collection instanceof ObservableList)
			return (ObservableList<T>) collection;
		return new CollectionWrappingList<>(collection);
	}

	/**
	 * A default toString() method for list implementations to use
	 *
	 * @param list The list to print
	 * @return The string representation of the list
	 */
	public static String toString(ObservableList<?> list) {
		StringBuilder ret = new StringBuilder("[");
		boolean first = true;
		try (Transaction t = list.lock(false, null)) {
			for(Object value : list) {
				if(!first) {
					ret.append(", ");
				} else
					first = false;
				ret.append(value);
			}
		}
		ret.append(']');
		return ret.toString();
	}

	/**
	 * Implements {@link ObservableList#listIterator()}
	 *
	 * @param <E> The type of values to iterate
	 */
	class SimpleListIterator<E> implements java.util.ListIterator<E> {
		private final List<E> theList;

		/** Index of element to be returned by subsequent call to next. */
		int cursor = 0;

		/** Index of element returned by most recent call to next or previous. Reset to -1 if this element is deleted by a call to remove. */
		int lastRet = -1;

		SimpleListIterator(List<E> list, int index) {
			theList = list;
			cursor = index;
		}

		@Override
		public boolean hasNext() {
			return cursor != theList.size();
		}

		@Override
		public E next() {
			try {
				int i = cursor;
				E next = theList.get(i);
				lastRet = i;
				cursor = i + 1;
				return next;
			} catch(IndexOutOfBoundsException e) {
				throw new NoSuchElementException();
			}
		}

		@Override
		public boolean hasPrevious() {
			return cursor != 0;
		}

		@Override
		public E previous() {
			try {
				int i = cursor - 1;
				E previous = theList.get(i);
				lastRet = cursor = i;
				return previous;
			} catch(IndexOutOfBoundsException e) {
				throw new NoSuchElementException();
			}
		}

		@Override
		public int nextIndex() {
			return cursor;
		}

		@Override
		public int previousIndex() {
			return cursor - 1;
		}

		@Override
		public void remove() {
			if(lastRet < 0)
				throw new IllegalStateException();

			try {
				theList.remove(lastRet);
				if(lastRet < cursor)
					cursor--;
				lastRet = -1;
			} catch(IndexOutOfBoundsException e) {
				throw new ConcurrentModificationException();
			}
		}

		@Override
		public void add(E e) {
			try {
				int i = cursor;
				theList.add(i, e);
				lastRet = -1;
				cursor = i + 1;
			} catch(IndexOutOfBoundsException ex) {
				throw new ConcurrentModificationException();
			}
		}

		@Override
		public void set(E e) {
			if(lastRet < 0)
				throw new IllegalStateException();

			try {
				theList.set(lastRet, e);
			} catch(IndexOutOfBoundsException ex) {
				throw new ConcurrentModificationException();
			}
		}
	}

	/**
	 * An extension of ObservableList that implements some of the redundant methods and throws UnsupportedOperationExceptions for
	 * modifications. Mostly copied from {@link java.util.AbstractList}.
	 *
	 * @param <E> The type of element in the list
	 */
	interface PartialListImpl<E> extends PartialCollectionImpl<E>, ObservableList<E> {
		@Override
		default boolean contains(Object o) {
			return ObservableList.super.contains(o);
		}

		@Override
		default boolean containsAll(Collection<?> coll) {
			return ObservableList.super.containsAll(coll);
		}

		@Override
		default boolean add(E o) {
			return ObservableList.super.add(o);
		}

		@Override
		default boolean addAll(Collection<? extends E> c) {
			return addAll(size(), c);
		}

		@Override
		default void add(int index, E element) {
			throw new UnsupportedOperationException(getClass().getName() + " does not implement add(index, value)");
		}

		@Override
		default boolean addAll(int index, Collection<? extends E> c) {
			try (Transaction t = lock(true, null)) {
				if(index < 0 || index > size())
					throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size());
				boolean modified = false;
				for(E e : c) {
					add(index++, e);
					modified = true;
				}
				return modified;
			}
		}

		@Override
		default boolean retainAll(Collection<?> coll) {
			return PartialCollectionImpl.super.retainAll(coll);
		}

		@Override
		default boolean removeAll(Collection<?> coll) {
			return PartialCollectionImpl.super.removeAll(coll);
		}

		@Override
		default boolean remove(Object o) {
			return PartialCollectionImpl.super.remove(o);
		}

		@Override
		default E remove(int index) {
			throw new UnsupportedOperationException(getClass().getName() + " does not implement remove(index)");
		}

		@Override
		default void clear() {
			ObservableList.super.clear();
		}

		@Override
		default E set(int index, E element) {
			throw new UnsupportedOperationException(getClass().getName() + " does not implement set(index, value)");
		}

		@Override
		default Iterator<E> iterator() {
			return listIterator();
		}
	}

	/**
	 * Implements {@link ObservableList#subList(int, int)}
	 *
	 * @param <E>
	 */
	class SubListImpl<E> implements RRList<E> {
		private final ObservableList<E> theRoot;

		private final RRList<E> theList;

		private final int theOffset;
		private int theSize;

		protected SubListImpl(ObservableList<E> root, RRList<E> list, int fromIndex, int toIndex) {
			if(fromIndex < 0)
				throw new IndexOutOfBoundsException("fromIndex = " + fromIndex);
			if(fromIndex > toIndex)
				throw new IllegalArgumentException("fromIndex(" + fromIndex + ") > toIndex(" + toIndex + ")");
			theRoot = root;
			theList=list;
			theOffset = fromIndex;
			theSize = toIndex - fromIndex;
		}

		@Override
		public E get(int index) {
			rangeCheck(index, false);
			return theList.get(index + theOffset);
		}

		@Override
		public int size() {
			int size = theList.size() - theOffset;
			if(theSize < size)
				size = theSize;
			return size;
		}

		@Override
		public boolean isEmpty() {
			return size() == 0;
		}

		@Override
		public boolean contains(Object o) {
			try (Transaction t = theRoot.lock(false, null)) {
				for(Object value : this)
					if(Objects.equals(value, o))
						return true;
				return false;
			}
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			if(c.isEmpty())
				return true;
			ArrayList<Object> copy = new ArrayList<>(c);
			BitSet found = new BitSet(copy.size());
			try (Transaction t = theRoot.lock(false, null)) {
				Iterator<E> iter = iterator();
				while(iter.hasNext()) {
					E next = iter.next();
					int stop = found.previousClearBit(copy.size());
					for(int i = found.nextClearBit(0); i < stop; i = found.nextClearBit(i + 1))
						if(Objects.equals(next, copy.get(i)))
							found.set(i);
				}
				return found.cardinality() == copy.size();
			}
		}

		@Override
		public E [] toArray() {
			ArrayList<E> ret = new ArrayList<>();
			try (Transaction t = theRoot.lock(false, null)) {
				for(E value : this)
					ret.add(value);
			}
			return ret.toArray((E []) java.lang.reflect.Array.newInstance(theRoot.getType().wrap().getRawType(), ret.size()));
		}

		@Override
		public <T> T [] toArray(T [] a) {
			ArrayList<E> ret = new ArrayList<>();
			try (Transaction t = theRoot.lock(false, null)) {
				for(E value : this)
					ret.add(value);
			}
			return ret.toArray(a);
		}

		@Override
		public int indexOf(Object o) {
			try (Transaction t = theRoot.lock(false, null)) {
				ListIterator<E> it = listIterator();
				int i;
				for(i = 0; it.hasNext(); i++)
					if(Objects.equals(it.next(), o))
						return i;
				return -1;
			}
		}

		@Override
		public int lastIndexOf(Object o) {
			try (Transaction t = theRoot.lock(false, null)) {
				ListIterator<E> it = listIterator(size());
				int i;
				for(i = size() - 1; it.hasPrevious(); i--)
					if(Objects.equals(it.previous(), o))
						return i;
				return -1;
			}
		}

		@Override
		public boolean add(E value) {
			try (Transaction t = theRoot.lock(true, null)) {
				int preSize = theList.size();
				theList.add(theOffset + theSize, value);
				if(preSize < theList.size()) {
					theSize++;
					return true;
				}
				return false;
			}
		}

		@Override
		public boolean addAll(Collection<? extends E> c) {
			return addAll(size(), c);
		}

		@Override
		public boolean addAll(int index, Collection<? extends E> c) {
			try (Transaction t = theRoot.lock(true, null)) {
				rangeCheck(index, true);
				int preSize = theList.size();
				theList.addAll(theOffset + index, c);
				int sizeDiff = theList.size() - preSize;
				if(sizeDiff > 0) {
					theSize += sizeDiff;
					return true;
				}
				return false;
			}
		}

		@Override
		public void add(int index, E value) {
			try (Transaction t = theRoot.lock(true, null)) {
				rangeCheck(index, true);
				int preSize = theList.size();
				theList.add(theOffset + index, value);
				if(preSize < theList.size()) {
					theSize++;
				}
			}
		}

		@Override
		public boolean remove(Object o) {
			try (Transaction t = theRoot.lock(true, null)) {
				Iterator<E> it = iterator();
				while(it.hasNext()) {
					if(Objects.equals(it.next(), o)) {
						it.remove();
						return true;
					}
				}
				return false;
			}
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			if(c.isEmpty())
				return false;
			try (Transaction t = theRoot.lock(true, null)) {
				boolean modified = false;
				Iterator<?> it = iterator();
				while(it.hasNext()) {
					if(c.contains(it.next())) {
						it.remove();
						modified = true;
					}
				}
				return modified;
			}
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			if(c.isEmpty()) {
				clear();
				return false;
			}
			try (Transaction t = theRoot.lock(true, null)) {
				boolean modified = false;
				Iterator<E> it = iterator();
				while(it.hasNext()) {
					if(!c.contains(it.next())) {
						it.remove();
						modified = true;
					}
				}
				return modified;
			}
		}

		@Override
		public E remove(int index) {
			try (Transaction t = theRoot.lock(true, null)) {
				rangeCheck(index, false);
				int preSize = theList.size();
				E ret = theList.remove(theOffset + index);
				if(theList.size() < preSize)
					theSize--;
				return ret;
			}
		}

		@Override
		public void clear() {
			if(!isEmpty())
				removeRange(0, size());
		}

		@Override
		public E set(int index, E value) {
			try (Transaction t = theRoot.lock(true, null)) {
				rangeCheck(index, false);
				return theList.set(theOffset + index, value);
			}
		}

		@Override
		public void removeRange(int fromIndex, int toIndex) {
			try (Transaction t = theRoot.lock(true, null)) {
				rangeCheck(fromIndex, false);
				rangeCheck(toIndex, true);
				int preSize = theList.size();
				theList.removeRange(fromIndex + theOffset, toIndex + theOffset);
				int sizeDiff = theList.size() - preSize;
				theSize += sizeDiff;
			}
		}

		@Override
		public List<E> subList(int fromIndex, int toIndex) {
			rangeCheck(fromIndex, false);
			if(toIndex < fromIndex)
				throw new IllegalArgumentException("" + toIndex);
			return new SubListImpl<>(theRoot, this, fromIndex, toIndex);
		}

		@Override
		public ListIterator<E> listIterator(int index) {
			int size = size();
			if(index < 0 || index > size)
				throw new IndexOutOfBoundsException(index + " of " + size);
			return new ListIterator<E>() {
				private final ListIterator<E> backing = theList.listIterator(theOffset + index);

				private int theIndex = index;

				private boolean lastPrevious;

				@Override
				public boolean hasNext() {
					if(theIndex >= theSize)
						return false;
					return backing.hasNext();
				}

				@Override
				public E next() {
					if(theIndex >= theSize)
						throw new NoSuchElementException();
					theIndex++;
					lastPrevious = false;
					return backing.next();
				}

				@Override
				public boolean hasPrevious() {
					if(theIndex <= 0)
						return false;
					return backing.hasPrevious();
				}

				@Override
				public E previous() {
					if(theIndex <= 0)
						throw new NoSuchElementException();
					theIndex--;
					lastPrevious = true;
					return backing.previous();
				}

				@Override
				public int nextIndex() {
					return theIndex;
				}

				@Override
				public int previousIndex() {
					return theIndex - 1;
				}

				@Override
				public void remove() {
					try (Transaction t = theRoot.lock(true, null)) {
						int preSize = theList.size();
						backing.remove();
						if(theList.size() < preSize) {
							theSize--;
							if(!lastPrevious)
								theIndex--;
						}
					}
				}

				@Override
				public void set(E e) {
					backing.set(e);
				}

				@Override
				public void add(E e) {
					try (Transaction t = theRoot.lock(true, null)) {
						int preSize = theList.size();
						backing.add(e);
						if(theList.size() > preSize) {
							theSize++;
							theIndex++;
						}
					}
				}
			};
		}

		private void rangeCheck(int index, boolean withAdd) {
			if(index < 0 || (!withAdd && index >= theSize) || (withAdd && index > theSize))
				throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
		}

		private String outOfBoundsMsg(int index) {
			return "Index: " + index + ", Size: " + theSize;
		}

		@Override
		public String toString() {
			StringBuilder ret = new StringBuilder("[");
			boolean first = true;
			try (Transaction t = theRoot.lock(false, null)) {
				for(E value : this) {
					if(!first) {
						ret.append(", ");
					} else
						first = false;
					ret.append(value);
				}
			}
			ret.append(']');
			return ret.toString();
		}

		class Element implements ObservableOrderedElement<E> {
			private final ObservableOrderedElement<E> theWrapped;

			private final DefaultObservable<Void> theRemovedObservable;
			private final Observer<Void> theRemovedController;

			private boolean isRemoved;

			Element(ObservableOrderedElement<E> wrap) {
				theWrapped = wrap;
				theRemovedObservable = new DefaultObservable<>();
				theRemovedController = theRemovedObservable.control(null);
			}

			@Override
			public ObservableValue<E> persistent() {
				return theWrapped.persistent();
			}

			@Override
			public TypeToken<E> getType() {
				return theWrapped.getType();
			}

			@Override
			public E get() {
				return theWrapped.get();
			}

			@Override
			public Subscription subscribe(Observer<? super ObservableValueEvent<E>> observer) {
				return theWrapped.takeUntil(theRemovedObservable).subscribe(new Observer<ObservableValueEvent<E>>() {
					@Override
					public <V extends ObservableValueEvent<E>> void onNext(V value) {
						if(isRemoved)
							return;
						observer.onNext(ObservableUtils.wrap(value, Element.this));
					}

					@Override
					public <V extends ObservableValueEvent<E>> void onCompleted(V value) {
						if(isRemoved)
							return;
						observer.onCompleted(ObservableUtils.wrap(value, Element.this));
					}
				});
			}

			@Override
			public int getIndex() {
				return theWrapped.getIndex() - theOffset;
			}

			void remove() {
				theRemovedController.onNext(null);
				isRemoved = true;
			}
		}
	}

	/**
	 * Implements {@link ObservableList#map(Function)}
	 *
	 * @param <E> The type of the collection to map
	 * @param <T> The type of the mapped collection
	 */
	class MappedList<E, T> extends MappedReversibleCollection<E, T> implements PartialListImpl<T> {
		protected MappedList(ObservableList<E> wrap, TypeToken<T> type, Function<? super E, T> map, Function<? super T, E> reverse) {
			super(wrap, type, map, reverse);
		}

		@Override
		protected ObservableList<E> getWrapped() {
			return (ObservableList<E>) super.getWrapped();
		}

		@Override
		public T get(int index) {
			return getMap().apply(getWrapped().get(index));
		}

		@Override
		public void add(int index, T element) {
			if(getReverse() == null)
				PartialListImpl.super.add(index, element);
			else
				getWrapped().add(index, getReverse().apply(element));
		}

		@Override
		public boolean addAll(int index, Collection<? extends T> c) {
			if(getReverse() == null)
				return PartialListImpl.super.addAll(index, c);
			else
				return getWrapped().addAll(index, c.stream().map(getReverse()).collect(Collectors.toList()));
		}

		@Override
		public T remove(int index) {
			return getMap().apply(getWrapped().remove(index));
		}

		@Override
		public T set(int index, T element) {
			if(getReverse() == null)
				return PartialListImpl.super.set(index, element);
			else
				return getMap().apply(getWrapped().set(index, getReverse().apply(element)));
		}

		@Override
		public String toString() {
			return ObservableList.toString(this);
		}
	}

	/**
	 * Implements several list functions for the two filtered list implementations ({@link ObservableList.StaticFilteredList} and
	 * {@link ObservableList.DynamicFilteredList})
	 *
	 * @param <E> The type of the list to filter
	 * @param <T> The type of the filtered list
	 */
	interface PartialFilteredListImpl<E, T> extends PartialListImpl<T> {
		ObservableList<E> getWrapped();

		Function<? super E, FilterMapResult<T>> getMap();

		Function<? super T, E> getReverse();

		@Override
		default T get(int index) {
			if(index < 0)
				throw new IndexOutOfBoundsException("" + index);
			int size = 0;
			int idx = index;
			try (Transaction t = lock(false, null)) {
				for(E el : getWrapped()) {
					FilterMapResult<T> mapped = getMap().apply(el);
					if (mapped.passed) {
						size++;
						if(idx == 0)
							return mapped.mapped;
						else
							idx--;
					}
				}
			}
			throw new IndexOutOfBoundsException(index + " of " + size);
		}

		@Override
		default void add(int index, T element) {
			if(getReverse() == null)
				PartialListImpl.super.add(index, element);
			if(index < 0)
				throw new IndexOutOfBoundsException("" + index);
			int size = 0;
			int idx = index;
			try (Transaction t = lock(true, null)) {
				ListIterator<E> iter = getWrapped().listIterator();
				while(iter.hasNext()) {
					E el = iter.next();
					FilterMapResult<T> mapped = getMap().apply(el);
					if (mapped.passed) {
						size++;
						idx--;
					}
					if(idx == 0) {
						iter.add(getReverse().apply(element));
						return;
					}
				}
			}
			throw new IndexOutOfBoundsException(index + " of " + size);
		}

		@Override
		default boolean addAll(int index, Collection<? extends T> c) {
			if(getReverse() == null)
				return PartialListImpl.super.addAll(index, c);
			if(index < 0)
				throw new IndexOutOfBoundsException("" + index);
			int size = 0;
			int idx = index;
			try (Transaction t = lock(true, null)) {
				ListIterator<E> iter = getWrapped().listIterator();
				while(iter.hasNext()) {
					E el = iter.next();
					FilterMapResult<T> mapped = getMap().apply(el);
					if (mapped.passed) {
						size++;
						idx--;
					}
					if(idx == 0) {
						for(T value : c)
							iter.add(getReverse().apply(value));
						return !c.isEmpty();
					}
				}
			}
			throw new IndexOutOfBoundsException(index + " of " + size);
		}

		@Override
		default T remove(int index) {
			if(index < 0)
				throw new IndexOutOfBoundsException("" + index);
			int size = 0;
			int idx = index;
			try (Transaction t = lock(true, null)) {
				Iterator<E> iter = getWrapped().iterator();
				while(iter.hasNext()) {
					E el = iter.next();
					FilterMapResult<T> mapped = getMap().apply(el);
					if (mapped.passed) {
						size++;
						if(idx == 0) {
							iter.remove();
							return mapped.mapped;
						} else
							idx--;
					}
				}
			}
			throw new IndexOutOfBoundsException(index + " of " + size);
		}

		@Override
		default T set(int index, T element) {
			if(getReverse() == null)
				return PartialListImpl.super.set(index, element);
			if(index < 0)
				throw new IndexOutOfBoundsException("" + index);
			int size = 0;
			int idx = index;
			try (Transaction t = lock(true, null)) {
				ListIterator<E> iter = getWrapped().listIterator();
				while(iter.hasNext()) {
					E el = iter.next();
					FilterMapResult<T> mapped = getMap().apply(el);
					if (mapped.passed) {
						size++;
						if(idx == 0) {
							iter.set(getReverse().apply(element));
							return mapped.mapped;
						} else
							idx--;
					}
				}
			}
			throw new IndexOutOfBoundsException(index + " of " + size);
		}

		@Override
		default ListIterator<T> listIterator(int index) {
			return new ListIterator<T>() {
				private final ListIterator<E> backing = getWrapped().listIterator(index);

				private FilterMapResult<T> thePreviousValue;

				private FilterMapResult<T> theNextValue;

				private int theIndex = index;

				@Override
				public boolean hasNext() {
					while ((theNextValue == null || !theNextValue.passed) && backing.hasNext()) {
						theNextValue = getMap().apply(backing.next());
					}
					return theNextValue != null && theNextValue.passed;
				}

				@Override
				public T next() {
					if ((theNextValue == null || !theNextValue.passed) && !hasNext())
						throw new NoSuchElementException();
					T ret = theNextValue.mapped;
					theNextValue = null;
					thePreviousValue = null;
					theIndex++;
					return ret;
				}

				@Override
				public boolean hasPrevious() {
					while ((thePreviousValue == null || !thePreviousValue.passed) && backing.hasPrevious()) {
						thePreviousValue = getMap().apply(backing.previous());
					}
					return thePreviousValue != null && thePreviousValue.passed;
				}

				@Override
				public T previous() {
					if ((thePreviousValue == null || !thePreviousValue.passed) && !hasPrevious())
						throw new NoSuchElementException();
					T ret = thePreviousValue.mapped;
					thePreviousValue = null;
					theNextValue = null;
					theIndex--;
					return ret;
				}

				@Override
				public int nextIndex() {
					return theIndex;
				}

				@Override
				public int previousIndex() {
					return theIndex - 1;
				}

				@Override
				public void remove() {
					backing.remove();
					theIndex--;
				}

				@Override
				public void set(T e) {
					if(getReverse() == null)
						throw new UnsupportedOperationException();
					E toSet = getReverse().apply(e);
					if (!getMap().apply(toSet).passed)
						throw new IllegalArgumentException("Value " + e + " is not acceptable in this mapped list");
					backing.set(toSet);
					theNextValue = null;
					thePreviousValue = null;
					if(theNextValue == null) // next() called last
						thePreviousValue = null;
					else
						theNextValue = null;
				}

				@Override
				public void add(T e) {
					if(getReverse() == null)
						throw new UnsupportedOperationException();
					E toAdd = getReverse().apply(e);
					if (!getMap().apply(toAdd).passed)
						throw new IllegalArgumentException("Value " + e + " is not acceptable in this mapped list");
					backing.add(toAdd);
					theIndex++;
					thePreviousValue = null;
				}
			};
		}
	}

	/**
	 * Implements {@link ObservableList#filterMap(TypeToken, Function, Function, boolean)} for static filtering
	 *
	 * @param <E> The type of the collection to be filter-mapped
	 * @param <T> The type of the mapped collection
	 */
	class StaticFilteredList<E, T> extends StaticFilteredReversibleCollection<E, T> implements PartialFilteredListImpl<E, T> {
		protected StaticFilteredList(ObservableList<E> wrap, TypeToken<T> type, Function<? super E, FilterMapResult<T>> map,
				Function<? super T, E> reverse) {
			super(wrap, type, map, reverse);
		}

		// Have to expose these as public to satisfy the interface

		@Override
		public ObservableList<E> getWrapped() {
			return (ObservableList<E>) super.getWrapped();
		}

		@Override
		public Function<? super E, FilterMapResult<T>> getMap() {
			return super.getMap();
		}

		@Override
		public Function<? super T, E> getReverse() {
			return super.getReverse();
		}

		@Override
		public String toString() {
			return ObservableList.toString(this);
		}
	}

	/**
	 * Implements {@link ObservableList#filterMap(TypeToken, Function, Function, boolean)} for dynamic filtering
	 *
	 * @param <E> The type of the collection to be filter-mapped
	 * @param <T> The type of the mapped collection
	 */
	class DynamicFilteredList<E, T> extends DynamicFilteredReversibleCollection<E, T> implements PartialFilteredListImpl<E, T> {
		protected DynamicFilteredList(ObservableList<E> wrap, TypeToken<T> type, Function<? super E, FilterMapResult<T>> map,
				Function<? super T, E> reverse) {
			super(wrap, type, map, reverse);
		}

		// Have to expose these as public to satisfy the interface

		@Override
		public ObservableList<E> getWrapped() {
			return (ObservableList<E>) super.getWrapped();
		}

		@Override
		public Function<? super E, FilterMapResult<T>> getMap() {
			return super.getMap();
		}

		@Override
		public Function<? super T, E> getReverse() {
			return super.getReverse();
		}

		@Override
		public String toString() {
			return ObservableList.toString(this);
		}
	}

	/**
	 * Implements {@link ObservableList#combine(ObservableValue, BiFunction)}
	 *
	 * @param <E> The type of the collection to be combined
	 * @param <T> The type of the value to combine the collection elements with
	 * @param <V> The type of the combined collection
	 */
	class CombinedObservableList<E, T, V> extends CombinedReversibleCollection<E, T, V> implements PartialListImpl<V> {
		protected CombinedObservableList(ObservableList<E> wrap, ObservableValue<T> value, TypeToken<V> type,
				BiFunction<? super E, ? super T, V> map, BiFunction<? super V, ? super T, E> reverse) {
			super(wrap, value, type, map, reverse);
		}

		@Override
		protected ObservableList<E> getWrapped() {
			return (ObservableList<E>) super.getWrapped();
		}

		@Override
		public V get(int index) {
			return getMap().apply(getWrapped().get(index), getValue().get());
		}

		@Override
		public void add(int index, V element) {
			if(getReverse() == null)
				PartialListImpl.super.add(index, element);
			else {
				T combinedValue = getValue().get();
				getWrapped().add(index, getReverse().apply(element, combinedValue));
			}
		}

		@Override
		public boolean addAll(int index, Collection<? extends V> c) {
			if(getReverse() == null)
				return PartialListImpl.super.addAll(index, c);
			else {
				T combinedValue = getValue().get();
				return getWrapped().addAll(index, c.stream().map(v -> getReverse().apply(v, combinedValue)).collect(Collectors.toList()));
			}
		}

		@Override
		public V remove(int index) {
			return getMap().apply(getWrapped().remove(index), getValue().get());
		}

		@Override
		public V set(int index, V element) {
			if(getReverse() == null)
				return PartialListImpl.super.set(index, element);
			else {
				T combinedValue = getValue().get();
				return getMap().apply(getWrapped().set(index, getReverse().apply(element, combinedValue)), combinedValue);
			}
		}

		@Override
		public String toString() {
			return ObservableList.toString(this);
		}
	}

	/**
	 * Implements {@link ObservableList#refresh(Observable)}
	 *
	 * @param <E> The type of the collection to refresh
	 */
	class RefreshingList<E> extends RefreshingReversibleCollection<E> implements PartialListImpl<E> {
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
		public Subscription onElementReverse(Consumer<? super ObservableOrderedElement<E>> onElement) {
			return getManager().onElement(getWrapped(),
					element -> onElement.accept((ObservableOrderedElement<E>) element.refresh(getRefresh())), false);
		}
	}

	/**
	 * Implements {@link ObservableList#refreshEach(Function)}
	 *
	 * @param <E> The type of the collection to refresh
	 */
	class ElementRefreshingList<E> extends ElementRefreshingReversibleCollection<E> implements PartialListImpl<E> {
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
	}

	/**
	 * An observable list that cannot be modified directly, but reflects the value of a wrapped list as it changes
	 *
	 * @param <E> The type of elements in the list
	 */
	class ImmutableObservableList<E> extends ImmutableReversibleCollection<E> implements PartialListImpl<E> {
		public ImmutableObservableList(ObservableList<E> wrap) {
			super(wrap);
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
		public ImmutableObservableList<E> immutable() {
			return this;
		}
	}

	/**
	 * Implements {@link ObservableList#filterModification(Predicate, Predicate)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	class ModFilteredList<E> extends ModFilteredReversibleCollection<E> implements ObservableList<E> {
		public ModFilteredList(ObservableList<E> wrapped, Predicate<? super E> removeFilter, Predicate<? super E> addFilter) {
			super(wrapped, removeFilter, addFilter);
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
		public int indexOf(Object o) {
			return getWrapped().indexOf(o);
		}

		@Override
		public int lastIndexOf(Object o) {
			return getWrapped().lastIndexOf(o);
		}

		@Override
		public void add(int index, E element) {
			if(getAddFilter() == null || getAddFilter().test(element))
				getWrapped().add(index, element);
		}

		@Override
		public boolean addAll(int index, Collection<? extends E> c) {
			if(getAddFilter() == null)
				return getWrapped().addAll(c);
			else
				return getWrapped().addAll(c.stream().filter(getAddFilter()).collect(Collectors.toList()));
		}

		@Override
		public E set(int index, E element) {
			if(getAddFilter() != null && !getAddFilter().test(element))
				return get(index);
			if(getRemoveFilter() == null)
				return set(index, element);
			ListIterator<E> iter = getWrapped().listIterator(index);
			E value = iter.next();
			if(getRemoveFilter().test(value))
				iter.set(element);
			return value;
		}

		@Override
		public E remove(int index) {
			if(getRemoveFilter() == null)
				return getWrapped().remove(index);
			ListIterator<E> iter = getWrapped().listIterator(index);
			E value = iter.next();
			if(getRemoveFilter().test(value))
				iter.remove();
			return value;
		}
	}

	/**
	 * Backs {@link ObservableList#cached()}
	 *
	 * @param <E> The type of elements in the collection
	 */
	class SafeCachedObservableList<E> extends SafeCachedReversibleCollection<E> implements PartialListImpl<E> {
		protected SafeCachedObservableList(ObservableList<E> wrap) {
			super(wrap);
		}

		@Override
		protected ObservableList<E> getWrapped() {
			return (ObservableList<E>) super.getWrapped();
		}

		@Override
		public E get(int index) {
			List<E> ret = refresh();
			return ret.get(index);
		}

		@Override
		public ObservableList<E> cached() {
			return this;
		}
	}

	/**
	 * Backs {@link ObservableList#takeUntil(Observable)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	class TakenUntilObservableList<E> extends TakenUntilReversibleCollection<E> implements PartialListImpl<E> {
		public TakenUntilObservableList(ObservableList<E> wrap, Observable<?> until, boolean terminate) {
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
	}

	/**
	 * Implements {@link ObservableList#reverse()}
	 *
	 * @param <E> The type of elements in the collection
	 */
	class ReversedList<E> extends ObservableReversedCollection<E> implements PartialListImpl<E> {
		protected ReversedList(ObservableList<E> list) {
			super(list);
		}

		@Override
		protected ObservableList<E> getWrapped() {
			return (ObservableList<E>) super.getWrapped();
		}
	}

	/**
	 * Implements {@link ObservableList#flattenValues(ObservableList)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	class FlattenedObservableValuesList<E> extends FlattenedReversibleValuesCollection<E> implements PartialListImpl<E> {
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
		public String toString() {
			return ObservableList.toString(this);
		}
	}

	/**
	 * Implements {@link ObservableList#flattenValue(ObservableValue)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	class FlattenedObservableValueList<E> extends FlattenedReversibleValueCollection<E> implements PartialListImpl<E> {
		public FlattenedObservableValueList(ObservableValue<? extends ObservableList<? extends E>> collectionObservable) {
			super(collectionObservable);
		}

		@Override
		protected ObservableValue<? extends ObservableList<? extends E>> getWrapped() {
			return (ObservableValue<? extends ObservableList<? extends E>>) super.getWrapped();
		}

		@Override
		public E get(int index) {
			ObservableList<? extends E> list = getWrapped().get();
			if (list == null)
				throw new IndexOutOfBoundsException(index + " of 0");
			return list.get(index);
		}

		@Override
		public String toString() {
			return ObservableList.toString(this);
		}
	}

	/**
	 * Implements {@link ObservableList#flatten(ObservableList)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	class FlattenedObservableList<E> extends FlattenedReversibleCollection<E> implements PartialListImpl<E> {
		public FlattenedObservableList(ObservableList<? extends ObservableList<E>> outer) {
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

		@Override
		public String toString() {
			return ObservableList.toString(this);
		}
	}

	/**
	 * Implements {@link ObservableList#asList(ObservableCollection)}
	 *
	 * @param <T> The type of the elements in the collection
	 */
	class CollectionWrappingList<T> implements PartialListImpl<T> {
		private final ObservableCollection<T> theWrapped;

		private final ListenerSet<Consumer<? super ObservableOrderedElement<T>>> theListeners;

		private final ReentrantReadWriteLock theLock;

		private final List<WrappingListElement<T>> theElements;

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
			initialized[0] = true;
			theListeners.add(listener);
			for(int i = theElements.size() - 1; i >= 0; i--)
				onElement.accept(theElements.get(i));
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
		public boolean addAll(int index, Collection<? extends T> c) {
			throw new UnsupportedOperationException();
		}

		@Override
		public T get(int index) {
			Lock lock = theLock.readLock();
			lock.lock();
			try {
				if(theListeners.isUsed())
					return theElements.get(index).get();
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
						if(Objects.equals(theElements.get(i).get(), o))
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
		public T [] toArray() {
			Lock lock = theLock.readLock();
			lock.lock();
			try {
				return PartialListImpl.super.toArray();
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

	/**
	 * An element for a {@link org.observe.collect.ObservableList.CollectionWrappingList}
	 *
	 * @param <T> The type of the value in the element
	 */
	class WrappingListElement<T> implements ObservableOrderedElement<T> {
		private final CollectionWrappingList<T> theList;

		private final ObservableElement<T> theWrapped;

		private int theRemovedIndex = -1;

		WrappingListElement(CollectionWrappingList<T> list, ObservableElement<T> wrap) {
			theList = list;
			theWrapped = wrap;
		}

		void setRemovedIndex(int index) {
			theRemovedIndex = index;
		}

		@Override
		public ObservableValue<T> persistent() {
			return theWrapped.persistent();
		}

		@Override
		public TypeToken<T> getType() {
			return theWrapped.getType();
		}

		@Override
		public T get() {
			return theWrapped.get();
		}

		@Override
		public Subscription subscribe(Observer<? super ObservableValueEvent<T>> observer) {
			return theWrapped.subscribe(new Observer<ObservableValueEvent<T>>() {
				@Override
				public <V extends ObservableValueEvent<T>> void onNext(V value) {
					observer.onNext(ObservableUtils.wrap(value, WrappingListElement.this));
				}

				@Override
				public <V extends ObservableValueEvent<T>> void onCompleted(V value) {
					observer.onCompleted(ObservableUtils.wrap(value, WrappingListElement.this));
				}
			});
		}

		@Override
		public int getIndex() {
			if(theRemovedIndex >= 0)
				return theRemovedIndex;
			return theList.theElements.indexOf(this);
		}
	}
}
