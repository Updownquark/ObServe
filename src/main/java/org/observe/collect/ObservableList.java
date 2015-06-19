package org.observe.collect;

import static org.observe.ObservableDebug.d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
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
import org.observe.util.ListenerSet;
import org.observe.util.ObservableUtils;

import prisms.lang.Type;

/**
 * A list whose content can be observed. This list is immutable in that none of its methods, including {@link List} methods, can modify its
 * content (List modification methods will throw {@link UnsupportedOperationException}). All {@link ObservableElement}s returned by this
 * observable will be instances of {@link OrderedObservableElement}.
 *
 * @param <E> The type of element in the list
 */
public interface ObservableList<E> extends ObservableReversibleCollection<E>, List<E> {
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
	default ListIterator<E> listIterator() {
		return new SimpleListIterator<>(this, 0);
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
	default ObservableList<E> subList(int fromIndex, int toIndex) {
		return new ObservableSubList<>(this, fromIndex, toIndex);
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
	/**
	 * @param map The mapping function
	 * @return An observable list of a new type backed by this list and the mapping function
	 */
	@Override
	default <T> ObservableList<T> map(Function<? super E, T> map) {
		return map(ObservableUtils.getReturnType(map), map);
	}

	/**
	 * @param type The type for the mapped list
	 * @param map The mapping function
	 * @return An observable list of a new type backed by this list and the mapping function
	 */
	@Override
	default <T> ObservableList<T> map(Type type, Function<? super E, T> map) {
		ObservableList<E> outer = this;
		class MappedObservableList implements PartialListImpl<T> {
			@Override
			public ObservableValue<CollectionSession> getSession() {
				return outer.getSession();
			}

			@Override
			public Type getType() {
				return type;
			}

			@Override
			public int size() {
				return outer.size();
			}

			@Override
			public T get(int index) {
				return map.apply(outer.get(index));
			}

			@Override
			public Subscription onOrderedElement(Consumer<? super OrderedObservableElement<T>> onElement) {
				return outer.onOrderedElement(element -> onElement.accept(element.mapV(map)));
			}

			@Override
			public Subscription onElementReverse(Consumer<? super OrderedObservableElement<T>> onElement) {
				return outer.onElementReverse(element -> onElement.accept(element.mapV(map)));
			}
		}
		return d().debug(new MappedObservableList()).from("map", this).using("map", map).get();
	}

	@Override
	default <T> ObservableList<T> filter(Class<T> type) {
		return d().label(filterMap(value -> type.isInstance(value) ? type.cast(value) : null)).tag("filterType", type).get();
	}

	/**
	 * @param filter The filter function
	 * @return A list containing all elements of this list that pass the given test
	 */
	@Override
	default ObservableList<E> filter(Predicate<? super E> filter) {
		return d().label(filterMap(getType(), (E value) -> {
			return (value != null && filter.test(value)) ? value : null;
		})).label("filter").tag("filter", filter).get();
	}

	@Override
	default <T> ObservableList<T> filterMap(Function<? super E, T> map) {
		return filterMap(ObservableUtils.getReturnType(map), map);
	}

	@Override
	default <T> ObservableList<T> filterMap(Type type, Function<? super E, T> map) {
		return d().debug(new FilteredList<>(this, type, map)).from("filterMap", this).using("map", map).get();
	}

	/**
	 * @param <T> The type of the argument value
	 * @param <V> The type of the new observable list
	 * @param arg The value to combine with each of this list's elements
	 * @param func The combination function to apply to this list's elements and the given value
	 * @return An observable list containing this list's elements combined with the given argument
	 */
	@Override
	default <T, V> ObservableList<V> combine(ObservableValue<T> arg, BiFunction<? super E, ? super T, V> func) {
		return combine(arg, ObservableUtils.getReturnType(func), func);
	}

	/**
	 * @param <T> The type of the argument value
	 * @param <V> The type of the new observable list
	 * @param arg The value to combine with each of this list's elements
	 * @param type The type for the new list
	 * @param func The combination function to apply to this list's elements and the given value
	 * @return An observable list containing this list's elements combined with the given argument
	 */
	@Override
	default <T, V> ObservableList<V> combine(ObservableValue<T> arg, Type type, BiFunction<? super E, ? super T, V> func) {
		return d().debug(new CombinedObservableList<>(this, arg, type, func)).from("combine", this).from("with", arg)
			.using("combination", func).get();
	}

	/**
	 * @param refresh The observable to re-fire events on
	 * @return A collection whose elements fire additional value events when the given observable fires
	 */
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
	default ObservableList<E> cached() {
		return d().debug(new SafeCachedObservableList<>(this)).from("cached", this).get();
	}

	/**
	 * @param <T> The type of the value to wrap
	 * @param type The type of the elements in the list
	 * @param list The list of items for the new list
	 * @return An observable list whose contents are given and never changes
	 */
	public static <T> ObservableList<T> constant(Type type, List<T> list) {
		class ConstantObservableElement implements OrderedObservableElement<T> {
			private final Type theType;
			private final T theValue;
			private final int theIndex;

			public ConstantObservableElement(T value, int index) {
				theType = type;
				theValue = value;
				theIndex = index;
			}

			@Override
			public Subscription subscribe(Observer<? super ObservableValueEvent<T>> observer) {
				observer.onNext(new ObservableValueEvent<>(this, theValue, theValue, null));
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
			public Type getType() {
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
		List<OrderedObservableElement<T>> obsEls = new java.util.ArrayList<>();
		class ConstantObservableList implements PartialListImpl<T> {
			@Override
			public ObservableValue<CollectionSession> getSession() {
				return ObservableValue.constant(new Type(CollectionSession.class), null);
			}

			@Override
			public Type getType() {
				return type;
			}

			@Override
			public Subscription onOrderedElement(Consumer<? super OrderedObservableElement<T>> observer) {
				for(OrderedObservableElement<T> ob : obsEls)
					observer.accept(ob);
				return () -> {
				};
			}

			@Override
			public Subscription onElementReverse(Consumer<? super OrderedObservableElement<T>> observer) {
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
	public static <T> ObservableList<T> constant(Type type, T... list) {
		return constant(type, java.util.Arrays.asList(list));
	}

	/**
	 * @param <T> The super-type of all lists in the wrapping list
	 * @param list The list to flatten
	 * @return A list containing all elements of all lists in the outer list
	 */
	public static <T> ObservableList<T> flatten(ObservableList<? extends ObservableList<? extends T>> list) {
		class FlattenedObservableList implements PartialListImpl<T> {
			class FlattenedListElement extends FlattenedElement<T> implements OrderedObservableElement<T> {
				private final List<FlattenedListElement> subListElements;

				private final OrderedObservableElement<? extends ObservableList<? extends T>> theSubList;

				FlattenedListElement(OrderedObservableElement<? extends T> subEl, List<FlattenedListElement> subListEls,
					OrderedObservableElement<? extends ObservableList<? extends T>> subList) {
					super((ObservableElement<T>) subEl, subList);
					subListElements = subListEls;
					theSubList = subList;
				}

				@Override
				protected OrderedObservableElement<T> getSubElement() {
					return (OrderedObservableElement<T>) super.getSubElement();
				}

				@Override
				public int getIndex() {
					int subListIndex = theSubList.getIndex();
					int ret = 0;
					for(int i = 0; i < subListIndex; i++)
						ret += list.get(i).size();
					int innerIndex = getSubElement().getIndex();
					for(int i = 0; i < innerIndex; i++)
						if(!subListElements.get(i).isRemoved())
							ret++;
					return ret;
				}
			}

			private final CombinedCollectionSessionObservable theSession = new CombinedCollectionSessionObservable(list);

			@Override
			public ObservableValue<CollectionSession> getSession() {
				return theSession;
			}

			@Override
			public Type getType() {
				return list.getType().getParamTypes().length == 0 ? new Type(Object.class) : list.getType().getParamTypes()[0];
			}

			@Override
			public T get(int index) {
				int idx = index;
				for(ObservableList<? extends T> subList : list) {
					if(idx < subList.size())
						return subList.get(idx);
					else
						idx -= subList.size();
				}
				throw new IndexOutOfBoundsException(index + " out of " + size());
			}

			@Override
			public int size() {
				int ret = 0;
				for(ObservableList<? extends T> subList : list)
					ret += subList.size();
				return ret;
			}

			@Override
			public Subscription onOrderedElement(Consumer<? super OrderedObservableElement<T>> onElement) {
				return onElement(onElement, true);
			}

			@Override
			public Subscription onElementReverse(Consumer<? super OrderedObservableElement<T>> onElement) {
				return onElement(onElement, false);
			}

			private Subscription onElement(Consumer<? super OrderedObservableElement<T>> onElement, boolean forward) {
				Consumer<OrderedObservableElement<? extends ObservableList<? extends T>>> outerConsumer;
				outerConsumer = new Consumer<OrderedObservableElement<? extends ObservableList<? extends T>>>() {
					private Map<ObservableList<?>, Subscription> subListSubscriptions;

					{
						subListSubscriptions = new org.observe.util.ConcurrentIdentityHashMap<>();
					}

					@Override
					public void accept(OrderedObservableElement<? extends ObservableList<? extends T>> subList) {
						subList.subscribe(new Observer<ObservableValueEvent<? extends ObservableList<? extends T>>>() {
							private List<FlattenedListElement> subListEls = new java.util.ArrayList<>();

							@Override
							public <V2 extends ObservableValueEvent<? extends ObservableList<? extends T>>> void onNext(V2 subListEvent) {
								if(subListEvent.getOldValue() != null && subListEvent.getOldValue() != subListEvent.getValue()) {
									Subscription subListSub = subListSubscriptions.get(subListEvent.getOldValue());
									if(subListSub != null)
										subListSub.unsubscribe();
								}
								Consumer<OrderedObservableElement<? extends T>> innerConsumer = subElement -> {
									FlattenedListElement flatEl = d().debug(new FlattenedListElement(subElement, subListEls, subList))
										.from("element", FlattenedObservableList.this).tag("wrappedCollectionElement", subList)
										.tag("wrappedSubElement", subElement).get();
									subListEls.add(subElement.getIndex(), flatEl);
									subElement.completed().act(x -> subListEls.remove(subElement.getIndex()));
									onElement.accept(flatEl);
								};
								Subscription subListSub;
								if(forward)
									subListSub = subListEvent.getValue().onOrderedElement(innerConsumer);
								else
									subListSub = subListEvent.getValue().onElementReverse(innerConsumer);
								subListSubscriptions.put(subListEvent.getValue(), subListSub);
							}

							@Override
							public <V2 extends ObservableValueEvent<? extends ObservableList<? extends T>>> void onCompleted(V2 subListEvent) {
								subListSubscriptions.remove(subListEvent.getValue()).unsubscribe();
							}
						});
					}
				};
				return forward ? list.onOrderedElement(outerConsumer) : list.onElementReverse(outerConsumer);
			}
		}
		return d().debug(new FlattenedObservableList()).from("flatten", list).get();
	}

	/**
	 * @param <T> The supertype of elements in the lists
	 * @param lists The lists to flatten
	 * @return An observable list that contains all the values of the given lists
	 */
	public static <T> ObservableList<T> flattenLists(ObservableList<? extends T>... lists) {
		if(lists.length == 0)
			return constant(new Type(Object.class));
		ObservableList<ObservableList<? extends T>> wrapper = constant(new Type(ObservableList.class, lists[0].getType()), lists);
		return flatten(wrapper);
	}

	/**
	 * @param <T> The type of the collection
	 * @param collection The collection to wrap as a list
	 * @return A list containing all elements in the collection, ordered and accessible by index
	 */
	public static <T> ObservableList<T> asList(ObservableCollection<T> collection){
		if(collection instanceof ObservableList)
			return (ObservableList<T>) collection;
		return new CollectionWrappingList<>(collection);
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
		public void set(E e) {
			if(lastRet < 0)
				throw new IllegalStateException();

			try {
				theList.set(lastRet, e);
			} catch(IndexOutOfBoundsException ex) {
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
		default boolean add(E e) {
			add(size(), e);
			return true;
		}

		@Override
		default E set(int index, E element) {
			throw new UnsupportedOperationException();
		}

		@Override
		default void add(int index, E element) {
			throw new UnsupportedOperationException();
		}

		@Override
		default E remove(int index) {
			throw new UnsupportedOperationException();
		}

		@Override
		default int indexOf(Object o) {
			ListIterator<E> it = listIterator();
			if(o == null) {
				while(it.hasNext())
					if(it.next() == null)
						return it.previousIndex();
			} else {
				while(it.hasNext())
					if(o.equals(it.next()))
						return it.previousIndex();
			}
			return -1;
		}

		@Override
		default int lastIndexOf(Object o) {
			ListIterator<E> it = listIterator(size());
			if(o == null) {
				while(it.hasPrevious())
					if(it.previous() == null)
						return it.nextIndex();
			} else {
				while(it.hasPrevious())
					if(o.equals(it.previous()))
						return it.nextIndex();
			}
			return -1;
		}

		@Override
		default void clear() {
			removeRange(0, size());
		}

		@Override
		default boolean addAll(Collection<? extends E> c) {
			return addAll(0, c);
		}

		@Override
		default boolean addAll(int index, Collection<? extends E> c) {
			if(index < 0 || index > size())
				throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size());
			boolean modified = false;
			for(E e : c) {
				add(index++, e);
				modified = true;
			}
			return modified;
		}

		@Override
		default Iterator<E> iterator() {
			return listIterator();
		}

		/**
		 * Removes from this list all of the elements whose index is between {@code fromIndex}, inclusive, and {@code toIndex}, exclusive.
		 * Shifts any succeeding elements to the left (reduces their index). This call shortens the list by {@code (toIndex - fromIndex)}
		 * elements. (If {@code toIndex==fromIndex}, this operation has no effect.)
		 *
		 * <p>
		 * This method is called by the {@code clear} operation on this list and its subLists. Overriding this method to take advantage of
		 * the internals of the list implementation can <i>substantially</i> improve the performance of the {@code clear} operation on this
		 * list and its subLists.
		 *
		 * <p>
		 * This implementation gets a list iterator positioned before {@code fromIndex}, and repeatedly calls {@code ListIterator.next}
		 * followed by {@code ListIterator.remove} until the entire range has been removed. <b>Note: if {@code ListIterator.remove} requires
		 * linear time, this implementation requires quadratic time.</b>
		 *
		 * @param fromIndex index of first element to be removed
		 * @param toIndex index after last element to be removed
		 */
		default void removeRange(int fromIndex, int toIndex) {
			ListIterator<E> it = listIterator(fromIndex);
			for(int i = 0, n = toIndex - fromIndex; i < n; i++) {
				it.next();
				it.remove();
			}
		}
	}

	/**
	 * Implements {@link ObservableList#subList(int, int)}
	 *
	 * @param <E>
	 */
	class ObservableSubList<E> implements PartialListImpl<E> {
		private final ObservableList<E> theList;

		private final int theOffset;

		private int theSize;

		protected ObservableSubList(ObservableList<E> list, int fromIndex, int toIndex) {
			if(fromIndex < 0)
				throw new IndexOutOfBoundsException("fromIndex = " + fromIndex);
			if(fromIndex > toIndex)
				throw new IllegalArgumentException("fromIndex(" + fromIndex + ") > toIndex(" + toIndex + ")");
			theList = list;
			theOffset = fromIndex;
			theSize = toIndex - fromIndex;
		}

		@Override
		public Subscription onOrderedElement(Consumer<? super OrderedObservableElement<E>> onElement) {
			List<OrderedObservableElement<E>> elements = new ArrayList<>();
			List<Element> wrappers = new ArrayList<>();
			return theList.onOrderedElement(element -> {
				int index = element.getIndex();
				Element wrapper = new Element(element);
				elements.add(index, element);
				wrappers.add(index, wrapper);
				int removeIdx = theOffset + theSize;
				if(index < removeIdx && removeIdx < wrappers.size())
					wrappers.get(removeIdx).remove();
				if(index < theOffset && theOffset < wrappers.size())
					onElement.accept(wrappers.get(theOffset));
			});
		}

		@Override
		public Type getType() {
			return theList.getType();
		}

		@Override
		public ObservableValue<CollectionSession> getSession() {
			return theList.getSession();
		}

		@Override
		public E set(int index, E element) {
			rangeCheck(index);
			return theList.set(index + theOffset, element);
		}

		@Override
		public E get(int index) {
			rangeCheck(index);
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
		public void removeRange(int fromIndex, int toIndex) {
			for(int i = fromIndex; i < toIndex; i++)
				theList.remove(theOffset + i);
			theSize -= (toIndex - fromIndex);
		}

		@Override
		public ObservableList<E> subList(int fromIndex, int toIndex) {
			return new ObservableSubList<>(this, fromIndex, toIndex);
		}

		private void rangeCheck(int index) {
			if(index < 0 || index >= theSize)
				throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
		}

		private String outOfBoundsMsg(int index) {
			return "Index: " + index + ", Size: " + theSize;
		}

		class Element implements OrderedObservableElement<E> {
			private final OrderedObservableElement<E> theWrapped;

			private final DefaultObservable<Void> theRemovedObservable;

			private final Observer<Void> theRemovedController;

			Element(OrderedObservableElement<E> wrap) {
				theWrapped = wrap;
				theRemovedObservable = new DefaultObservable<>();
				theRemovedController = theRemovedObservable.control(null);
			}

			@Override
			public ObservableValue<E> persistent() {
				return theWrapped.persistent();
			}

			@Override
			public Type getType() {
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
						observer.onNext(ObservableUtils.wrap(value, Element.this));
					}

					@Override
					public <V extends ObservableValueEvent<E>> void onCompleted(V value) {
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
			}
		}
	}

	/**
	 * Implements {@link ObservableList#filterMap(Function)}
	 *
	 * @param <E> The type of the collection to be filter-mapped
	 * @param <T> The type of the mapped collection
	 */
	class FilteredList<E, T> extends FilteredReversibleCollection<E, T> implements PartialListImpl<T> {
		protected FilteredList(ObservableList<E> wrap, Type type, Function<? super E, T> map) {
			super(wrap, type, map);
		}

		@Override
		protected ObservableList<E> getWrapped() {
			return (ObservableList<E>) super.getWrapped();
		}

		@Override
		public T get(int index) {
			if(index < 0)
				throw new IndexOutOfBoundsException("" + index);
			int size = 0;
			int idx = index;
			for(E el : getWrapped()) {
				T mapped = getMap().apply(el);
				if(mapped != null) {
					size++;
					if(idx == 0)
						return mapped;
					else
						idx--;
				}
			}
			throw new IndexOutOfBoundsException(index + " of " + size);
		}

		@Override
		public T remove(int index) {
			if(index < 0)
				throw new IndexOutOfBoundsException("" + index);
			int size = 0;
			int idx = index;
			Iterator<E> iter = getWrapped().iterator();
			while(iter.hasNext()) {
				E el = iter.next();
				T mapped = getMap().apply(el);
				if(mapped != null) {
					size++;
					if(idx == 0) {
						iter.remove();
						return mapped;
					} else
						idx--;
				}
			}
			throw new IndexOutOfBoundsException(index + " of " + size);
		}

		@Override
		public void clear() {
			Iterator<E> iter = getWrapped().iterator();
			while(iter.hasNext()) {
				E el = iter.next();
				T mapped = getMap().apply(el);
				if(mapped != null) {
					iter.remove();
				}
			}
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
		protected CombinedObservableList(ObservableList<E> wrap, ObservableValue<T> value, Type type,
			BiFunction<? super E, ? super T, V> map) {
			super(wrap, value, type, map);
		}

		@Override
		protected ObservableList<E> getWrapped() {
			return (ObservableList<E>) super.getWrapped();
		}

		@Override
		public V get(int index) {
			return getMap().apply(getWrapped().get(index), getValue().get());
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
		public Subscription onElementReverse(Consumer<? super OrderedObservableElement<E>> onElement) {
			return getManager().onElement(getWrapped(), getRefresh(),
				element -> onElement.accept((OrderedObservableElement<E>) element.refresh(getRefresh())), false);
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
	 * Backs {@link ObservableOrderedCollection#cached()}
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
	 * Implements {@link ObservableList#asList(ObservableCollection)}
	 *
	 * @param <T> The type of the elements in the collection
	 */
	class CollectionWrappingList<T> implements PartialListImpl<T> {
		private final ObservableCollection<T> theWrapped;

		private final ListenerSet<Consumer<? super OrderedObservableElement<T>>> theListeners;

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
							wrapSub = theWrapped.onElement(element -> {
								int index = theElements.size();
								if(element instanceof OrderedObservableElement)
									index = ((OrderedObservableElement<T>) element).getIndex();
								WrappingListElement<T> listEl = new WrappingListElement<>(CollectionWrappingList.this, element);
								theElements.add(index, listEl);
								theListeners.forEach(listener -> listener.accept(listEl));
								element.completed().act(event -> {
									Lock lock2 = theLock.writeLock();
									lock2.lock();
									try {
										theElements.remove(listEl);
									} finally {
										lock2.unlock();
									}
								});
							});
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
		public Type getType() {
			return theWrapped.getType();
		}

		@Override
		public Subscription onOrderedElement(Consumer<? super OrderedObservableElement<T>> onElement) {
			theListeners.add(onElement);
			return () -> {
				theListeners.remove(onElement);
			};
		}

		@Override
		public Subscription onElementReverse(Consumer<? super OrderedObservableElement<T>> onElement) {
			boolean [] initialized = new boolean[1];
			Consumer<OrderedObservableElement<T>> listener = element -> {
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
	class WrappingListElement<T> implements OrderedObservableElement<T> {
		private final CollectionWrappingList<T> theList;

		private final ObservableElement<T> theWrapped;

		WrappingListElement(CollectionWrappingList<T> list, ObservableElement<T> wrap) {
			theList = list;
			theWrapped = wrap;
		}

		@Override
		public ObservableValue<T> persistent() {
			return theWrapped.persistent();
		}

		@Override
		public Type getType() {
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
			return theList.theElements.indexOf(this);
		}
	}
}
