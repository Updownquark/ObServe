package org.observe.collect;

import static org.observe.ObservableDebug.debug;
import static org.observe.ObservableDebug.label;

import java.util.AbstractList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.observe.ComposedObservableValue;
import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.util.ListenerSet;

import prisms.lang.Type;

/**
 * A list whose content can be observed. This list is immutable in that none of its methods, including {@link List} methods, can modify its
 * content (List modification methods will throw {@link UnsupportedOperationException}). All {@link ObservableElement}s returned by this
 * observable will be instances of {@link OrderedObservableElement}.
 *
 * @param <E> The type of element in the list
 */
public interface ObservableList<E> extends ObservableOrderedCollection<E>, List<E> {
	/* Overridden for performance.  get() is linear in the super, constant time here (assuming random-access) */
	@Override
	default ObservableValue<E> last() {
		return debug(new ObservableValue<E>() {
			private final Type type = ObservableList.this.getType().isPrimitive() ? new Type(Type.getWrapperType(ObservableList.this
				.getType().getBaseType())) : ObservableList.this.getType();

			@Override
			public Type getType() {
				return type;
			}

			@Override
			public E get() {
				int size = size();
				return size == 0 ? null : ObservableList.this.get(size - 1);
			}

			@Override
			public Runnable observe(Observer<? super ObservableValueEvent<E>> observer) {
				if(isEmpty())
					observer.onNext(createEvent(null, null, null));
				Object key = new Object();
				Object [] oldValue = new Object[1];
				Runnable collSub = ObservableList.this.onElement(new Consumer<ObservableElement<E>>() {
					@Override
					public void accept(ObservableElement<E> element) {
						OrderedObservableElement<E> orderedEl = (OrderedObservableElement<E>) element;
						orderedEl.observe(new Observer<ObservableValueEvent<E>>() {
							private OrderedObservableElement<E> lastElement;

							@Override
							public <V2 extends ObservableValueEvent<E>> void onNext(V2 event) {
								if(lastElement != null && orderedEl.getIndex() < lastElement.getIndex())
									return;
								CollectionSession session = getSession().get();
								if(session == null) {
									observer.onNext(createEvent((E) oldValue[0], event.getValue(), event));
									oldValue[0] = event.getValue();
								} else {
									session.put(key, "hasNewLast", true);
									session.put(key, "newLast", event.getValue());
								}
							}

							@Override
							public <V2 extends ObservableValueEvent<E>> void onCompleted(V2 event) {
								if(orderedEl != lastElement)
									return;
								CollectionSession session = getSession().get();
								if(session == null) {
									E newValue = get();
									observer.onNext(createEvent((E) oldValue[0], newValue, event));
									oldValue[0] = newValue;
								} else {
									session.put(key, "hasNewLast", true);
									session.put(key, "findNewLast", true);
								}
							}
						});
					}
				});
				Runnable transSub = getSession().observe(new Observer<ObservableValueEvent<CollectionSession>>() {
					@Override
					public <V2 extends ObservableValueEvent<CollectionSession>> void onNext(V2 value) {
						CollectionSession completed = value.getOldValue();
						if(completed == null || completed.get(key, "hasNewLast") == null)
							return;
						E newLast;
						if(completed.get(key, "findNewLast") != null)
							newLast = get();
						else
							newLast = (E) completed.get(key, "newLast");
						if(newLast != oldValue[0])
							return;
						observer.onNext(createEvent((E) oldValue[0], newLast, value));
					}
				});
				return () -> {
					collSub.run();
					transSub.run();
				};
			}
		}).from("last", this).get();
	}

	/**
	 * @param map The mapping function
	 * @return An observable list of a new type backed by this list and the mapping function
	 */
	@Override
	default <T> ObservableList<T> map(Function<? super E, T> map) {
		return map(ComposedObservableValue.getReturnType(map), map);
	}

	/**
	 * @param type The type for the mapped list
	 * @param map The mapping function
	 * @return An observable list of a new type backed by this list and the mapping function
	 */
	@Override
	default <T> ObservableList<T> map(Type type, Function<? super E, T> map) {
		ObservableList<E> outer = this;
		class MappedObservableList extends AbstractList<T> implements ObservableList<T> {
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
			public Runnable onElement(Consumer<? super ObservableElement<T>> observer) {
				return outer.onElement(element -> observer.accept(element.mapV(map)));
			}
		}
		return debug(new MappedObservableList()).from("map", this).using("map", map).get();
	}

	/**
	 * @param filter The filter function
	 * @return A list containing all elements of this list that pass the given test
	 */
	@Override
	default ObservableList<E> filter(Function<? super E, Boolean> filter) {
		return label(filterMap(getType(), (E value) -> {
			return (value != null && filter.apply(value)) ? value : null;
		})).label("filter").tag("filter", filter).get();
	}

	@Override
	default <T> ObservableList<T> filterMap(Function<? super E, T> map) {
		return filterMap(ComposedObservableValue.getReturnType(map), map);
	}

	@Override
	default <T> ObservableList<T> filterMap(Type type, Function<? super E, T> map) {
		ObservableList<E> outer = this;
		class FilteredList extends AbstractList<T> implements ObservableList<T> {
			private List<FilteredListElement<T, E>> theFilteredElements = new java.util.ArrayList<>();

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
				int ret = 0;
				for(E el : outer)
					if(map.apply(el) != null)
						ret++;
				return ret;
			}

			@Override
			public T get(int index) {
				if(index < 0)
					throw new IndexOutOfBoundsException("" + index);
				int size = 0;
				int idx = index;
				for(E el : outer) {
					T mapped = map.apply(el);
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
			public Runnable onElement(Consumer<? super ObservableElement<T>> observer) {
				return outer.onElement(element -> {
					OrderedObservableElement<E> outerElement = (OrderedObservableElement<E>) element;
					FilteredListElement<T, E> retElement = debug(new FilteredListElement<>(outerElement, map, type, theFilteredElements))
						.from("element", outer).tag("wrapped", element).get();
					theFilteredElements.add(outerElement.getIndex(), retElement);
					outerElement.observe(new Observer<ObservableValueEvent<E>>() {
						@Override
						public <V2 extends ObservableValueEvent<E>> void onNext(V2 elValue) {
							if(!retElement.isIncluded()) {
								T mapped = map.apply(elValue.getValue());
								if(mapped != null)
									observer.accept(retElement);
							}
						}

						@Override
						public <V2 extends ObservableValueEvent<E>> void onCompleted(V2 elValue) {
							theFilteredElements.remove(outerElement.getIndex());
						}
					});
				});
			}
		}
		return debug(new FilteredList()).from("filterMap", this).using("map", map).get();
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
		return combine(arg, ComposedObservableValue.getReturnType(func), func);
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
		ObservableList<E> outer = this;
		class CombinedObservableList extends AbstractList<V> implements ObservableList<V> {
			private final SubCollectionTransactionManager theTransactionManager = new SubCollectionTransactionManager(outer);

			@Override
			public ObservableValue<CollectionSession> getSession() {
				return theTransactionManager.getSession();
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
			public V get(int index) {
				return func.apply(outer.get(index), arg.get());
			}

			@Override
			public Runnable onElement(Consumer<? super ObservableElement<V>> observer) {
				return theTransactionManager.onElement(outer, arg, element -> observer.accept(element.combineV(func, arg)));
			}
		}
		return debug(new CombinedObservableList()).from("combine", this).from("with", arg).using("combination", func).get();
	}

	/**
	 * @param refresh The observable to re-fire events on
	 * @return A collection whose elements fire additional value events when the given observable fires
	 */
	@Override
	default ObservableList<E> refresh(Observable<?> refresh) {
		ObservableList<E> outer = this;
		class RefreshingCollection extends AbstractList<E> implements ObservableList<E> {
			private final SubCollectionTransactionManager theTransactionManager = new SubCollectionTransactionManager(outer);

			@Override
			public Type getType() {
				return outer.getType();
			}

			@Override
			public ObservableValue<CollectionSession> getSession() {
				return theTransactionManager.getSession();
			}

			@Override
			public int size() {
				return outer.size();
			}

			@Override
			public E get(int index) {
				return outer.get(index);
			}

			@Override
			public Runnable onElement(Consumer<? super ObservableElement<E>> observer) {
				return theTransactionManager.onElement(outer, refresh, element -> observer.accept(element.refresh(refresh)));
			}
		};
		return debug(new RefreshingCollection()).from("refresh", this).from("on", refresh).get();
	}

	@Override
	default ObservableList<E> immutable() {
		return debug(new Immutable<>(this)).from("immutable", this).get();
	}

	@Override
	default ObservableOrderedCollection<E> cached() {
		return debug(new SafeCached<>(this)).from("cached", this).get();
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
			public Runnable observe(Observer<? super ObservableValueEvent<T>> observer) {
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
		List<T> constList = java.util.Collections.unmodifiableList(list);
		List<ObservableElement<T>> obsEls = new java.util.ArrayList<>();
		class ConstantObservableList extends AbstractList<T> implements ObservableList<T> {
			@Override
			public ObservableValue<CollectionSession> getSession() {
				return ObservableValue.constant(new Type(CollectionSession.class), null);
			}

			@Override
			public Type getType() {
				return type;
			}

			@Override
			public Runnable onElement(Consumer<? super ObservableElement<T>> observer) {
				for(ObservableElement<T> ob : obsEls)
					observer.accept(ob);
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
		ConstantObservableList ret = debug(new ConstantObservableList()).tag("constant", list).get();
		for(int i = 0; i < constList.size(); i++)
			obsEls.add(debug(new ConstantObservableElement(constList.get(i), i)).from("element", ret).tag("value", constList.get(i)).get());
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
		class FlattenedObservableList extends AbstractList<T> implements ObservableList<T> {
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
			public Runnable onElement(Consumer<? super ObservableElement<T>> observer) {
				return list.onElement(new Consumer<ObservableElement<? extends ObservableList<? extends T>>>() {
					private Map<ObservableList<?>, Runnable> subListSubscriptions;

					{
						subListSubscriptions = new org.observe.util.ConcurrentIdentityHashMap<>();
					}

					@Override
					public void accept(ObservableElement<? extends ObservableList<? extends T>> subList) {
						class FlattenedListElement extends FlattenedElement<T> implements OrderedObservableElement<T> {
							private final List<FlattenedListElement> subListElements;

							FlattenedListElement(OrderedObservableElement<T> subEl, List<FlattenedListElement> subListEls) {
								super(subEl, subList);
								subListElements = subListEls;
							}

							@Override
							protected OrderedObservableElement<T> getSubElement() {
								return (OrderedObservableElement<T>) super.getSubElement();
							}

							@Override
							public int getIndex() {
								int subListIndex = ((OrderedObservableElement<?>) subList).getIndex();
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
						subList.observe(new Observer<ObservableValueEvent<? extends ObservableList<? extends T>>>() {
							private List<FlattenedListElement> subListEls = new java.util.ArrayList<>();

							@Override
							public <V2 extends ObservableValueEvent<? extends ObservableList<? extends T>>> void onNext(V2 subListEvent) {
								if(subListEvent.getOldValue() != null && subListEvent.getOldValue() != subListEvent.getValue()) {
									Runnable subListSub = subListSubscriptions.get(subListEvent.getOldValue());
									if(subListSub != null)
										subListSub.run();
								}
								Runnable subListSub = subListEvent.getValue().onElement(subElement -> {
									OrderedObservableElement<T> subListEl = (OrderedObservableElement<T>) subElement;
									FlattenedListElement flatEl = debug(new FlattenedListElement(subListEl, subListEls))
										.from("element", FlattenedObservableList.this).tag("wrappedCollectionElement", subList)
										.tag("wrappedSubElement", subElement).get();
									subListEls.add(subListEl.getIndex(), flatEl);
									subListEl.completed().act(x -> subListEls.remove(subListEl.getIndex()));
									observer.accept(flatEl);
								});
								subListSubscriptions.put(subListEvent.getValue(), subListSub);
							}

							@Override
							public <V2 extends ObservableValueEvent<? extends ObservableList<? extends T>>> void onCompleted(V2 subListEvent) {
								subListSubscriptions.remove(subListEvent.getValue()).run();
							}
						});
					}
				});
			}
		}
		return debug(new FlattenedObservableList()).from("flatten", list).get();
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
	 * The type of element in filtered lists
	 *
	 * @param <T> The type of this element
	 * @param <E> The type of element wrapped by this element
	 */
	class FilteredListElement<T, E> extends FilteredElement<T, E> implements OrderedObservableElement<T> {
		private List<FilteredListElement<T, E>> theFilteredElements;

		FilteredListElement(OrderedObservableElement<E> wrapped, Function<? super E, T> map, Type type,
			List<FilteredListElement<T, E>> filteredEls) {
			super(wrapped, map, type);
			theFilteredElements = filteredEls;
		}

		@Override
		protected OrderedObservableElement<E> getWrapped() {
			return (OrderedObservableElement<E>) super.getWrapped();
		}

		@Override
		public int getIndex() {
			int ret = 0;
			int outerIdx = getWrapped().getIndex();
			for(int i = 0; i < outerIdx; i++)
				if(theFilteredElements.get(i).isIncluded())
					ret++;
			return ret;
		}
	}

	/**
	 * An observable list that cannot be modified directly, but reflects the value of a wrapped list as it changes
	 *
	 * @param <E> The type of elements in the list
	 */
	public static class Immutable<E> extends AbstractList<E> implements ObservableList<E> {
		private final ObservableList<E> theWrapped;

		/** @param wrap The collection to wrap */
		public Immutable(ObservableList<E> wrap) {
			theWrapped = wrap;
		}

		@Override
		public ObservableValue<CollectionSession> getSession() {
			return theWrapped.getSession();
		}

		@Override
		public Runnable onElement(Consumer<? super ObservableElement<E>> observer) {
			return theWrapped.onElement(observer);
		}

		@Override
		public Type getType() {
			return theWrapped.getType();
		}

		@Override
		public E get(int index) {
			return theWrapped.get(index);
		}

		@Override
		public Iterator<E> iterator() {
			return prisms.util.ArrayUtils.immutableIterator(theWrapped.iterator());
		}

		@Override
		public int size() {
			return theWrapped.size();
		}

		@Override
		public Immutable<E> immutable() {
			return this;
		}
	}

	/**
	 * Backs {@link ObservableOrderedCollection#cached()}
	 *
	 * @param <E> The type of elements in the collection
	 */
	public static class SafeCached<E> extends AbstractList<E> implements ObservableList<E> {
		private static class CachedElement<E> implements OrderedObservableElement<E> {
			private final OrderedObservableElement<E> theWrapped;

			private final ListenerSet<Observer<? super ObservableValueEvent<E>>> theElementListeners;

			private E theCachedValue;

			CachedElement(OrderedObservableElement<E> wrap) {
				theWrapped = wrap;
				theElementListeners = new ListenerSet<>();
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
				return theCachedValue;
			}

			@Override
			public Runnable observe(Observer<? super ObservableValueEvent<E>> observer) {
				theElementListeners.add(observer);
				observer.onNext(new ObservableValueEvent<>(this, theCachedValue, theCachedValue, null));
				return () -> theElementListeners.remove(observer);
			}

			@Override
			public int getIndex() {
				return theWrapped.getIndex();
			}

			private void newValue(ObservableValueEvent<E> event) {
				E oldValue = theCachedValue;
				theCachedValue = event.getValue();
				ObservableValueEvent<E> cachedEvent = new ObservableValueEvent<>(this, oldValue, theCachedValue, event);
				theElementListeners.forEach(observer -> observer.onNext(cachedEvent));
			}

			private void completed(ObservableValueEvent<E> event) {
				ObservableValueEvent<E> cachedEvent = new ObservableValueEvent<>(this, theCachedValue, theCachedValue, event);
				theElementListeners.forEach(observer -> observer.onCompleted(cachedEvent));
			}
		}

		private final ObservableList<E> theWrapped;

		private final ListenerSet<Consumer<? super ObservableElement<E>>> theListeners;

		private final java.util.concurrent.CopyOnWriteArrayList<CachedElement<E>> theCache;

		private final ReentrantLock theLock;

		private final Consumer<ObservableElement<E>> theWrappedOnElement;

		private Runnable theUnsubscribe;

		/** @param wrap The collection to cache */
		public SafeCached(ObservableList<E> wrap) {
			theWrapped = wrap;
			theListeners = new ListenerSet<>();
			theCache = new java.util.concurrent.CopyOnWriteArrayList<>();
			theLock = new ReentrantLock();
			theWrappedOnElement = element -> {
				CachedElement<E> cached = debug(new CachedElement<>((OrderedObservableElement<E>) element)).from("element", this)
					.tag("wrapped", element).get();
				theCache.add(cached.getIndex(), cached);
				element.observe(new Observer<ObservableValueEvent<E>>() {
					@Override
					public <V extends ObservableValueEvent<E>> void onNext(V event) {
						cached.newValue(event);
					}

					@Override
					public <V extends ObservableValueEvent<E>> void onCompleted(V event) {
						cached.completed(event);
						theCache.remove(element);
					}
				});
				theListeners.forEach(onElement -> onElement.accept(cached));
			};

			theListeners.setUsedListener(this::setUsed);
		}

		@Override
		public Type getType() {
			return theWrapped.getType();
		}

		@Override
		public Runnable onElement(Consumer<? super ObservableElement<E>> onElement) {
			theListeners.add(onElement);
			for(CachedElement<E> cached : theCache)
				onElement.accept(cached);
			return () -> theListeners.remove(onElement);
		}

		@Override
		public ObservableValue<CollectionSession> getSession() {
			return theWrapped.getSession();
		}

		@Override
		public int size() {
			Collection<E> ret = refresh();
			return ret.size();
		}

		@Override
		public E get(int index) {
			List<E> ret = refresh();
			return ret.get(index);
		}

		private void setUsed(boolean used) {
			if(used && theUnsubscribe == null) {
				theLock.lock();
				try {
					theCache.clear();
					theUnsubscribe = theWrapped.onElement(theWrappedOnElement);
				} finally {
					theLock.unlock();
				}
			} else if(!used && theUnsubscribe != null) {
				theUnsubscribe.run();
				theUnsubscribe = null;
			}
		}

		private List<E> refresh() {
			// If we're currently caching, then returned the cached values. Otherwise return the dynamic values.
			if(theUnsubscribe != null)
				return theCache.stream().map(CachedElement::get).collect(Collectors.toList());
			else
				return theWrapped;
		}
	}
}
