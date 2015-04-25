package org.observe.collect;

import static org.observe.ObservableDebug.debug;
import static org.observe.ObservableDebug.label;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.observe.*;
import org.observe.Observable;
import org.observe.Observer;
import org.observe.util.ListenerSet;

import prisms.lang.Type;

/**
 * An ordered collection whose content can be observed. All {@link ObservableElement}s returned by this observable will be instances of
 * {@link OrderedObservableElement}. In addition, it is guaranteed that the {@link OrderedObservableElement#getIndex() index} of an element
 * given to the observer passed to {@link #onElement(Consumer)} will be less than or equal to the number of uncompleted elements previously
 * passed to the observer. This means that, for example, the first element passed to an observer will always be index 0. The second may be 0
 * or 1. If one of these is then completed, the next element may be 0 or 1 as well.
 *
 * @param <E> The type of element in the collection
 */
public interface ObservableOrderedCollection<E> extends ObservableCollection<E> {
	/** @return An observable that returns null whenever any elements in this collection are added, removed or changed */
	@Override
	default Observable<? extends OrderedCollectionChangeEvent<E>> changes() {
		return debug(new OrderedCollectionChangesObservable<>(this)).from("changes", this).get();
	}

	/**
	 * @param filter The filter function
	 * @return The first value in this collection passing the filter, or null if none of this collection's elements pass
	 */
	@Override
	default ObservableValue<E> find(Predicate<E> filter) {
		ObservableOrderedCollection<E> outer = this;
		return debug(new ObservableValue<E>() {
			private final Type type = outer.getType().isPrimitive() ? new Type(Type.getWrapperType(outer.getType().getBaseType())) : outer
				.getType();

			@Override
			public Type getType() {
				return type;
			}

			@Override
			public E get() {
				for(E element : ObservableOrderedCollection.this) {
					if(filter.test(element))
						return element;
				}
				return null;
			}

			@Override
			public Runnable observe(Observer<? super ObservableValueEvent<E>> observer) {
				if(isEmpty())
					observer.onNext(new ObservableValueEvent<>(this, null, null, null));
				final Object key = new Object();
				Runnable collSub = ObservableOrderedCollection.this.onElement(new Consumer<ObservableElement<E>>() {
					private E theValue;
					private int theIndex = -1;

					@Override
					public void accept(ObservableElement<E> element) {
						element.subscribe(new Observer<ObservableValueEvent<E>>() {
							@Override
							public <V3 extends ObservableValueEvent<E>> void onNext(V3 value) {
								int listIndex = ((OrderedObservableElement<?>) value.getObservable()).getIndex();
								if(theIndex < 0 || listIndex <= theIndex) {
									if(filter.test(value.getValue()))
										newBest(value.getValue(), listIndex);
									else if(listIndex == theIndex)
										findNextBest(listIndex + 1);
								}
							}

							@Override
							public <V3 extends ObservableValueEvent<E>> void onCompleted(V3 value) {
								int listIndex = ((OrderedObservableElement<?>) value.getObservable()).getIndex();
								if(listIndex == theIndex) {
									findNextBest(listIndex + 1);
								} else if(listIndex < theIndex)
									theIndex--;
							}

							private void findNextBest(int index) {
								boolean found = false;
								java.util.Iterator<E> iter = ObservableOrderedCollection.this.iterator();
								int idx = 0;
								for(idx = 0; iter.hasNext() && idx < index; idx++)
									iter.next();
								for(; iter.hasNext(); idx++) {
									E val = iter.next();
									if(filter.test(val)) {
										found = true;
										newBest(val, idx);
										break;
									}
								}
								if(!found)
									newBest(null, -1);
							}
						});
					}

					void newBest(E value, int index) {
						E oldValue = theValue;
						theValue = value;
						theIndex = index;
						CollectionSession session = getSession().get();
						if(session == null)
							observer.onNext(createEvent(oldValue, theValue, null));
						else {
							session.putIfAbsent(key, "oldBest", oldValue);
							session.put(key, "newBest", theValue);
						}
					}
				});
				Runnable transSub = getSession().observe(new Observer<ObservableValueEvent<CollectionSession>>() {
					@Override
					public <V2 extends ObservableValueEvent<CollectionSession>> void onNext(V2 value) {
						CollectionSession completed = value.getOldValue();
						if(completed == null)
							return;
						E oldBest = (E) completed.get(key, "oldBest");
						E newBest = (E) completed.get(key, "newBest");
						if(oldBest == null && newBest == null)
							return;
						observer.onNext(createEvent(oldBest, newBest, value));
					}
				});
				return () -> {
					collSub.run();
					transSub.run();
				};
			}

			@Override
			public String toString() {
				return "find in " + ObservableOrderedCollection.this;
			}
		}).from("find", this).using("filter", filter).get();
	}

	/** @return The first value in this collection, or null if this collection is empty */
	default ObservableValue<E> first() {
		ObservableOrderedCollection<E> outer = this;
		return debug(new ObservableValue<E>() {
			private final Type type = outer.getType().isPrimitive() ? new Type(Type.getWrapperType(outer.getType().getBaseType())) : outer
				.getType();

			@Override
			public Type getType() {
				return type;
			}

			@Override
			public E get() {
				Iterator<E> iter = iterator();
				if(!iter.hasNext())
					return null;
				return iter.next();
			}

			@Override
			public Runnable observe(Observer<? super ObservableValueEvent<E>> observer) {
				if(isEmpty())
					observer.onNext(createEvent(null, null, null));
				Object key = new Object();
				Object [] oldValue = new Object[1];
				Runnable collSub = ObservableOrderedCollection.this.onElement(new Consumer<ObservableElement<E>>() {
					@Override
					public void accept(ObservableElement<E> element) {
						OrderedObservableElement<E> orderedEl = (OrderedObservableElement<E>) element;
						orderedEl.observe(new Observer<ObservableValueEvent<E>>() {
							@Override
							public <V2 extends ObservableValueEvent<E>> void onNext(V2 event) {
								if(orderedEl.getIndex() != 0)
									return;
								CollectionSession session = getSession().get();
								if(session == null) {
									observer.onNext(createEvent((E) oldValue[0], event.getValue(), event));
									oldValue[0] = event.getValue();
								} else {
									session.put(key, "hasNewFirst", true);
									session.put(key, "newFirst", event.getValue());
								}
							}

							@Override
							public <V2 extends ObservableValueEvent<E>> void onCompleted(V2 event) {
								if(orderedEl.getIndex() != 0)
									return;
								E newValue = get();
								CollectionSession session = getSession().get();
								if(session == null) {
									observer.onNext(createEvent((E) oldValue[0], newValue, event));
									oldValue[0] = newValue;
								} else {
									session.put(key, "hasNewFirst", true);
									session.put(key, "newFirst", newValue);
								}
							}
						});
					}
				});
				Runnable transSub = getSession().observe(new Observer<ObservableValueEvent<CollectionSession>>() {
					@Override
					public <V2 extends ObservableValueEvent<CollectionSession>> void onNext(V2 value) {
						CollectionSession completed = value.getOldValue();
						if(completed == null || completed.get(key, "hasNewFirst") == null)
							return;
						E newFirst = (E) completed.get(key, "newFirst");
						if(newFirst != oldValue[0])
							return;
						observer.onNext(createEvent((E) oldValue[0], newFirst, value));
					}
				});
				return () -> {
					collSub.run();
					transSub.run();
				};
			}
		}).from("first", this).get();
	}

	/** @return The last value in this collection, or null if this collection is empty */
	default ObservableValue<E> last() {
		ObservableOrderedCollection<E> outer = this;
		return debug(new ObservableValue<E>() {
			private final Type type = outer.getType().isPrimitive() ? new Type(Type.getWrapperType(outer.getType().getBaseType())) : outer
				.getType();

			@Override
			public Type getType() {
				return type;
			}

			@Override
			public E get() {
				Iterator<E> iter = iterator();
				if(!iter.hasNext())
					return null;
				E ret = null;
				do {
					ret = iter.next();
				} while(iter.hasNext());
				return ret;
			}

			@Override
			public Runnable observe(Observer<? super ObservableValueEvent<E>> observer) {
				if(isEmpty())
					observer.onNext(createEvent(null, null, null));
				Object key = new Object();
				Object [] oldValue = new Object[1];
				Runnable collSub = ObservableOrderedCollection.this.onElement(new Consumer<ObservableElement<E>>() {
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

	@Override
	default <T, V> ObservableOrderedCollection<V> combine(ObservableValue<T> arg, Type type, BiFunction<? super E, ? super T, V> func) {
		ObservableOrderedCollection<E> outer = this;
		class CombinedObservableCollection extends AbstractCollection<V> implements ObservableOrderedCollection<V> {
			private final DefaultTransactionManager theTransactionManager = new DefaultTransactionManager(outer);

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
			public Iterator<V> iterator() {
				return new Iterator<V>() {
					private final Iterator<E> backing = outer.iterator();

					@Override
					public boolean hasNext() {
						return backing.hasNext();
					}

					@Override
					public V next() {
						return func.apply(backing.next(), arg.get());
					}
				};
			}

			@Override
			public Runnable onElement(Consumer<? super ObservableElement<V>> observer) {
				return theTransactionManager.onElement(outer, arg, element -> observer.accept(element.combineV(func, arg)));
			}
		}
		return debug(new CombinedObservableCollection()).from("combine", this).from("with", arg).using("combination", func).get();
	}

	/**
	 * @param refresh The observable to re-fire events on
	 * @return A collection whose elements fire additional value events when the given observable fires
	 */
	@Override
	default ObservableOrderedCollection<E> refresh(Observable<?> refresh) {
		ObservableOrderedCollection<E> outer = this;
		class RefreshingCollection extends AbstractCollection<E> implements ObservableOrderedCollection<E> {
			private final DefaultTransactionManager theTransactionManager = new DefaultTransactionManager(outer);

			@Override
			public Type getType() {
				return outer.getType();
			}

			@Override
			public ObservableValue<CollectionSession> getSession() {
				return theTransactionManager.getSession();
			}

			@Override
			public Iterator<E> iterator() {
				return outer.iterator();
			}

			@Override
			public int size() {
				return outer.size();
			}

			@Override
			public Runnable onElement(Consumer<? super ObservableElement<E>> observer) {
				return theTransactionManager.onElement(outer, refresh, element -> observer.accept(element.refresh(refresh)));
			}
		};
		return debug(new RefreshingCollection()).from("refresh", this).from("on", refresh).get();
	}

	@Override
	default <T> ObservableOrderedCollection<T> map(Function<? super E, T> map) {
		return map(ComposedObservableValue.getReturnType(map), map);
	}

	@Override
	default <T> ObservableOrderedCollection<T> map(Type type, Function<? super E, T> map) {
		ObservableCollection<E> outer = this;
		class MappedObservableCollection extends java.util.AbstractCollection<T> implements ObservableOrderedCollection<T> {
			@Override
			public Type getType() {
				return type;
			}

			@Override
			public ObservableValue<CollectionSession> getSession() {
				return outer.getSession();
			}

			@Override
			public int size() {
				return outer.size();
			}

			@Override
			public Iterator<T> iterator() {
				return new Iterator<T>() {
					private final Iterator<E> backing = outer.iterator();

					@Override
					public boolean hasNext() {
						return backing.hasNext();
					}

					@Override
					public T next() {
						return map.apply(backing.next());
					}

					@Override
					public void remove() {
						backing.remove();
					}
				};
			}

			@Override
			public Runnable onElement(Consumer<? super ObservableElement<T>> observer) {
				return outer.onElement(element -> observer.accept(element.mapV(map)));
			}
		}
		return debug(new MappedObservableCollection()).from("map", this).using("map", map).get();
	}

	@Override
	default ObservableOrderedCollection<E> filter(Function<? super E, Boolean> filter) {
		return label(filterMap(value -> {
			return (value != null && filter.apply(value)) ? value : null;
		})).label("filter").tag("filter", filter).get();
	}

	@Override
	default <T> ObservableOrderedCollection<T> filterMap(Function<? super E, T> filterMap) {
		return filterMap(ComposedObservableValue.getReturnType(filterMap), filterMap);
	}

	@Override
	default <T> ObservableOrderedCollection<T> filterMap(Type type, Function<? super E, T> map) {
		ObservableOrderedCollection<E> outer = this;
		class FilteredOrderedCollection extends AbstractCollection<T> implements ObservableOrderedCollection<T> {
			private List<FilteredOrderedElement<T, E>> theFilteredElements = new java.util.ArrayList<>();

			@Override
			public Type getType() {
				return type;
			}

			@Override
			public ObservableValue<CollectionSession> getSession() {
				return outer.getSession();
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
			public Iterator<T> iterator() {
				return new Iterator<T>() {
					private final Iterator<E> backing = outer.iterator();
					private T nextVal;

					@Override
					public boolean hasNext() {
						while(nextVal == null && backing.hasNext()) {
							nextVal = map.apply(backing.next());
						}
						return nextVal != null;
					}

					@Override
					public T next() {
						if(nextVal == null && !hasNext())
							throw new java.util.NoSuchElementException();
						T ret = nextVal;
						nextVal = null;
						return ret;
					}
				};
			}

			@Override
			public Runnable onElement(Consumer<? super ObservableElement<T>> observer) {
				return outer.onElement(element -> {
					OrderedObservableElement<E> outerElement = (OrderedObservableElement<E>) element;
					FilteredOrderedElement<T, E> retElement = debug(
						new FilteredOrderedElement<>(outerElement, map, type, theFilteredElements)).from("element", this)
						.tag("wrapped", element).get();
					theFilteredElements.add(outerElement.getIndex(), retElement);
					outerElement.completed().act(elValue -> theFilteredElements.remove(outerElement.getIndex()));
					outerElement.act(elValue -> {
						if(!retElement.isIncluded()) {
							T mapped = map.apply(elValue.getValue());
							if(mapped != null)
								observer.accept(retElement);
						}
					});
				});
			}
		}
		return debug(new FilteredOrderedCollection()).from("filterMap", this).using("map", map).get();
	}

	@Override
	default ObservableOrderedCollection<E> immutable() {
		return debug(new Immutable<>(this)).from("immutable", this).get();
	}

	@Override
	default ObservableOrderedCollection<E> cached(){
		return debug(new SafeCached<>(this)).from("cached", this).get();
	}

	/**
	 * @param <E> The type of the collection to sort
	 * @param coll The collection whose elements to sort
	 * @param compare The comparator to sort the elements
	 * @return An observable collection containing all elements in <code>coll</code> (even multiples for which <code>compare</code>
	 *         {@link Comparator#compare(Object, Object) .compare} returns 0), sorted according to <code>compare()</code>.
	 */
	public static <E> ObservableOrderedCollection<E> sort(ObservableCollection<E> coll, java.util.Comparator<? super E> compare) {
		if(compare == null) {
			if(!new Type(Comparable.class).isAssignable(coll.getType()))
				throw new IllegalArgumentException("No natural ordering for collection of type " + coll.getType());
			compare = (Comparator<? super E>) (Comparable<Comparable<?>> o1, Comparable<Comparable<?>> o2) -> o1.compareTo(o2);
		}
		return debug(new SortedObservableCollectionWrapper<>(coll, compare)).from("sort", coll).using("compare", compare).get();
	}

	/**
	 * Flattens a collection of ordered collections. The inner collections must be sorted according to the given comparator in order for the
	 * result to be correctly sorted.
	 *
	 * @param <E> The super-type of all collections in the wrapping collection
	 * @param list The collection to flatten
	 * @param compare The comparator to compare elements between collections
	 * @return A collection containing all elements of all collections in the outer collection, sorted according to the given comparator,
	 *         then by order in the outer collection.
	 */
	public static <E> ObservableOrderedCollection<E> flatten(ObservableOrderedCollection<? extends ObservableOrderedCollection<E>> list,
		Comparator<? super E> compare) {
		if(compare == null) {
			compare = (Comparator<? super E>) (Comparable<Comparable<?>> o1, Comparable<Comparable<?>> o2) -> o1.compareTo(o2);
		}
		Comparator<? super E> fCompare = compare;
		class FlattenedOrderedElement extends FlattenedElement<E> implements OrderedObservableElement<E> {
			FlattenedOrderedElement(OrderedObservableElement<E> subEl, ObservableElement<? extends ObservableOrderedCollection<E>> subList) {
				super(subEl, subList);
			}

			@Override
			protected OrderedObservableElement<? extends ObservableOrderedCollection<E>> getSubCollectionElement() {
				return (OrderedObservableElement<? extends ObservableOrderedCollection<E>>) super.getSubCollectionElement();
			}

			@Override
			protected OrderedObservableElement<E> getSubElement() {
				return (OrderedObservableElement<E>) super.getSubElement();
			}

			@Override
			public int getIndex() {
				E value = get();
				int subListIndex = getSubCollectionElement().getIndex();
				int subElIdx = getSubElement().getIndex();
				int ret = 0;
				int index = 0;
				for(ObservableOrderedCollection<E> sub : list) {
					if(index == subListIndex) {
						ret += subElIdx;
						index++;
						continue;
					}
					for(E el : sub) {
						int comp = fCompare.compare(value, el);
						if(comp < 0)
							break;
						if(index > subListIndex && comp == 0)
							break;
						ret++;
					}
					index++;
				}
				return ret;
			}
		}
		class FlattenedObservableOrderedCollection extends AbstractCollection<E> implements ObservableOrderedCollection<E> {
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
			public int size() {
				int ret = 0;
				for(ObservableOrderedCollection<E> subList : list)
					ret += subList.size();
				return ret;
			}

			@Override
			public Iterator<E> iterator() {
				ArrayList<Iterator<? extends E>> iters = new ArrayList<>();
				for(ObservableOrderedCollection<E> subList : list)
					iters.add(subList.iterator());
				return new Iterator<E>() {
					private Object [] subValues = new Object[iters.size()];
					/** This list represents the indexes of the sub values, as they would be in a list sorted according to the comparator. */
					private prisms.util.IntList indexes = new prisms.util.IntList(subValues.length);
					private boolean initialized;

					@Override
					public boolean hasNext() {
						if(!initialized)
							init();
						return !indexes.isEmpty();
					}

					@Override
					public E next() {
						if(!initialized)
							init();
						if(indexes.isEmpty())
							throw new java.util.NoSuchElementException();
						int nextIndex = indexes.remove(0);
						E ret = (E) subValues[nextIndex];
						if(iters.get(nextIndex).hasNext()) {
							E nextVal = iters.get(nextIndex).next();
							subValues[nextIndex] = nextVal;
							int i;
							for(i = 0; i < indexes.size(); i++) {
								int comp = fCompare.compare(nextVal, (E) subValues[indexes.get(i)]);
								if(comp < 0)
									continue;
								if(comp == 0 && nextIndex > indexes.get(i))
									continue;
							}
							indexes.add(i, nextIndex);
						} else
							subValues[nextIndex] = null;
						return ret;
					}

					private void init() {
						initialized = true;
						for(int i = 0; i < subValues.length; i++) {
							if(iters.get(i).hasNext()) {
								subValues[i] = iters.get(i).next();
								indexes.add(i);
							} else
								indexes.add(-1);
						}
						prisms.util.ArrayUtils.sort(subValues.clone(), new prisms.util.ArrayUtils.SortListener<Object>() {
							@Override
							public int compare(Object o1, Object o2) {
								if(o1 == null && o2 == null)
									return 0;
								// Place nulls last
								else if(o1 == null)
									return 1;
								else if(o2 == null)
									return -1;
								return fCompare.compare((E) o1, (E) o2);
							}

							@Override
							public void swapped(Object o1, int idx1, Object o2, int idx2) {
								int firstIdx = indexes.get(idx1);
								indexes.set(idx1, indexes.get(idx2));
								indexes.set(idx2, firstIdx);
							}
						});
						for(int i = 0; i < indexes.size(); i++) {
							if(indexes.get(i) < 0) {
								indexes.remove(i);
								i--;
							}
						}
					}
				};
			}

			@Override
			public boolean contains(Object o) {
				for(ObservableOrderedCollection<E> subList : list)
					if(subList.contains(o))
						return true;
				return false;
			}

			@Override
			public boolean containsAll(Collection<?> c) {
				Object [] ca = c.toArray();
				BitSet contained = new BitSet(ca.length);
				for(ObservableOrderedCollection<E> subList : list) {
					for(int i = contained.nextClearBit(0); i < ca.length; i = contained.nextClearBit(i))
						if(subList.contains(ca[i]))
							contained.set(i);
					if(contained.nextClearBit(0) == ca.length)
						break;
				}
				return contained.nextClearBit(0) == ca.length;
			}

			@Override
			public Runnable onElement(Consumer<? super ObservableElement<E>> observer) {
				/* This has to be handled a little differently.  If the initial elements were simply handed to the observer, they could be
				 * out of order, since the first sub-collection might contain elements that compare greater to elements in a following
				 * sub-collection.  Thus, for example, the first element fed to the observer might have non-zero index.  This violates the
				 * contract of an ordered collection and will frequently cause problems in the observer.
				 * Thus, we compile an ordered list of the initial elements and then send them all to the observer after all the lists have
				 * delivered their initial elements.  Subsequent elements can be delivered in the normal way. */
				final List<FlattenedOrderedElement> initialElements = new ArrayList<>();
				final boolean [] initializing = new boolean[] {true};
				Runnable ret = list.onElement(new Consumer<ObservableElement<? extends ObservableOrderedCollection<E>>>() {
					private Map<ObservableOrderedCollection<E>, Runnable> subListSubscriptions;

					{
						subListSubscriptions = new org.observe.util.ConcurrentIdentityHashMap<>();
					}

					@Override
					public void accept(ObservableElement<? extends ObservableOrderedCollection<E>> subList) {
						subList.subscribe(new Observer<ObservableValueEvent<? extends ObservableOrderedCollection<E>>>() {
							@Override
							public <V2 extends ObservableValueEvent<? extends ObservableOrderedCollection<E>>> void onNext(V2 subListEvent) {
								if(subListEvent.getOldValue() != null && subListEvent.getOldValue() != subListEvent.getValue()) {
									Runnable subListSub = subListSubscriptions.get(subListEvent.getOldValue());
									if(subListSub != null)
										subListSub.run();
								}
								Runnable subListSub = subListEvent.getValue().onElement(subElement -> {
									OrderedObservableElement<E> subListEl = (OrderedObservableElement<E>) subElement;
									FlattenedOrderedElement flatEl = debug(new FlattenedOrderedElement(subListEl, subList))
										.from("element", this).tag("wrappedCollectionElement", subList)
										.tag("wrappedSubElement", subListEl).get();
									if(initializing[0]) {
										int index = flatEl.getIndex();
										while(initialElements.size() <= index)
											initialElements.add(null);
										initialElements.set(index, flatEl);
									} else
										observer.accept(flatEl);
								});
								subListSubscriptions.put(subListEvent.getValue(), subListSub);
							}

							@Override
							public <V2 extends ObservableValueEvent<? extends ObservableOrderedCollection<E>>> void onCompleted(
								V2 subListEvent) {
								subListSubscriptions.remove(subListEvent.getValue()).run();
							}
						});
					}
				});
				initializing[0] = false;
				for(FlattenedOrderedElement el : initialElements)
					observer.accept(el);
				initialElements.clear();
				return ret;
			}
		}
		return debug(new FlattenedObservableOrderedCollection()).from("flatten", list).using("compare", compare).get();
	}

	/**
	 * The type of element in filtered ordered collections
	 *
	 * @param <T> The type of this element
	 * @param <E> The type of element wrapped by this element
	 */
	class FilteredOrderedElement<T, E> extends FilteredElement<T, E> implements OrderedObservableElement<T> {
		private List<FilteredOrderedElement<T, E>> theFilteredElements;

		FilteredOrderedElement(OrderedObservableElement<E> wrapped, Function<? super E, T> map, Type type,
			List<FilteredOrderedElement<T, E>> filteredEls) {
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
	 * Backs elements in {@link ObservableOrderedCollection#sort(ObservableCollection, Comparator)}
	 *
	 * @param <E> The type of the element
	 */
	static class SortedElementWrapper<E> implements OrderedObservableElement<E> {
		private final SortedObservableCollectionWrapper<E> theList;
		private final ObservableElement<E> theWrapped;
		private final SortedObservableWrapperObserver<E> theParentObserver;
		SortedElementWrapper<E> theLeft;
		SortedElementWrapper<E> theRight;

		SortedElementWrapper(SortedObservableCollectionWrapper<E> list, ObservableElement<E> wrap,
			SortedObservableWrapperObserver<E> parentObs, SortedElementWrapper<E> anchor) {
			theList = list;
			theWrapped = wrap;
			theParentObserver = parentObs;
			if(anchor != null)
				findPlace(theWrapped.get(), anchor);
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
		public Runnable observe(Observer<? super ObservableValueEvent<E>> observer) {
			return theWrapped.observe(new Observer<ObservableValueEvent<E>>() {
				@Override
				public <V extends ObservableValueEvent<E>> void onNext(V event) {
					if(theLeft != null && theList.getCompare().compare(event.getValue(), theLeft.get()) < 0) {
						observer.onCompleted(new ObservableValueEvent<>(SortedElementWrapper.this, event.getOldValue(), event.getValue(),
							event));
						theLeft.theRight = theRight;
						if(theRight != null)
							theRight.theLeft = theLeft;
						findPlace(event.getValue(), theLeft);
						theParentObserver.theOuterObserver.accept(SortedElementWrapper.this);
					} else if(theRight != null && theList.getCompare().compare(event.getValue(), theRight.get()) > 0) {
						observer.onCompleted(new ObservableValueEvent<>(SortedElementWrapper.this, event.getOldValue(), event.getValue(),
							event));
						if(theLeft != null)
							theLeft.theRight = theRight;
						theRight.theLeft = theLeft;
						findPlace(event.getValue(), theRight);
						theParentObserver.theOuterObserver.accept(SortedElementWrapper.this);
					} else
						observer.onNext(new ObservableValueEvent<>(SortedElementWrapper.this, event.getOldValue(), event.getValue(), event));
				}

				@Override
				public <V extends ObservableValueEvent<E>> void onCompleted(V event) {
					if(theParentObserver.theAnchor == SortedElementWrapper.this) {
						if(theLeft != null)
							theParentObserver.theAnchor = theLeft;
						else if(theRight != null)
							theParentObserver.theAnchor = theRight;
						else
							theParentObserver.theAnchor = null;
					}
					observer.onCompleted(new ObservableValueEvent<>(SortedElementWrapper.this, event.getOldValue(), event.getValue(), event));
				}
			});
		}

		@Override
		public int getIndex() {
			SortedElementWrapper<E> left = theLeft;
			int ret = 0;
			while(left != null) {
				ret++;
				left = left.theLeft;
			}
			return ret;
		}

		private void findPlace(E value, SortedElementWrapper<E> anchor) {
			SortedElementWrapper<E> test = anchor;
			int comp = theList.getCompare().compare(value, test.get());
			if(comp >= 0) {
				while(test.theRight != null && comp >= 0) {
					test = test.theRight;
					comp = theList.getCompare().compare(value, test.get());
				}

				if(comp >= 0) { // New element is right-most
					theLeft = test;
					test.theRight = this;
				} else { // New element to be inserted to the left of test
					theLeft = test.theLeft;
					theRight = test;
					test.theLeft = this;
				}
			} else {
				while(test.theLeft != null && comp < 0) {
					test = test.theLeft;
					comp = theList.getCompare().compare(value, test.get());
				}

				if(comp < 0) { // New element is left-most
					theRight = test;
					test.theLeft = this;
				} else { // New element to be inserted to the right of test
					theLeft = test;
					theRight = test.theRight;
					test.theRight = this;
				}
			}
		}
	}

	/**
	 * Backs {@link ObservableOrderedCollection#sort(ObservableCollection, Comparator)}
	 *
	 * @param <E> The type of the elements in the collection
	 */
	static class SortedObservableCollectionWrapper<E> extends AbstractCollection<E> implements ObservableOrderedCollection<E> {
		private final ObservableCollection<E> theWrapped;
		private final Comparator<? super E> theCompare;

		SortedObservableCollectionWrapper(ObservableCollection<E> wrap, Comparator<? super E> compare) {
			theWrapped = wrap;
			theCompare = compare;
		}

		@Override
		public ObservableValue<CollectionSession> getSession() {
			return theWrapped.getSession();
		}

		Comparator<? super E> getCompare() {
			return theCompare;
		}

		@Override
		public Type getType() {
			return theWrapped.getType();
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
		public boolean containsAll(Collection<?> c) {
			return theWrapped.containsAll(c);
		}

		@Override
		public Iterator<E> iterator() {
			ArrayList<E> list = new ArrayList<>(theWrapped);
			Collections.sort(new ArrayList<>(theWrapped), theCompare);
			return Collections.unmodifiableCollection(list).iterator();
		}

		@Override
		public Runnable onElement(Consumer<? super ObservableElement<E>> observer) {
			return theWrapped.onElement(new SortedObservableWrapperObserver<>(this, observer));
		}
	}

	/**
	 * Used by {@link ObservableOrderedCollection#sort(ObservableCollection, Comparator)}
	 *
	 * @param <E> The type of the elements in the collection
	 */
	static class SortedObservableWrapperObserver<E> implements Consumer<ObservableElement<E>> {
		private final SortedObservableCollectionWrapper<E> theList;

		final Consumer<? super ObservableElement<E>> theOuterObserver;
		private SortedElementWrapper<E> theAnchor;

		SortedObservableWrapperObserver(SortedObservableCollectionWrapper<E> list, Consumer<? super ObservableElement<E>> outerObs) {
			theList = list;
			theOuterObserver = outerObs;
		}

		@Override
		public void accept(ObservableElement<E> outerEl) {
			SortedElementWrapper<E> newEl = debug(new SortedElementWrapper<>(theList, outerEl, this, theAnchor)).from("element", theList)
				.tag("wrapped", outerEl).get();
			if(theAnchor == null)
				theAnchor = newEl;
			theOuterObserver.accept(newEl);
		}
	}

	/**
	 * An observable ordered collection that cannot be modified directly, but reflects the value of a wrapped collection as it changes
	 *
	 * @param <E> The type of elements in the collection
	 */
	public static class Immutable<E> extends AbstractCollection<E> implements ObservableOrderedCollection<E> {
		private final ObservableOrderedCollection<E> theWrapped;

		/** @param wrap The collection to wrap */
		public Immutable(ObservableOrderedCollection<E> wrap) {
			theWrapped = wrap;
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
		public ObservableValue<CollectionSession> getSession() {
			return theWrapped.getSession();
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
	public static class SafeCached<E> extends AbstractCollection<E> implements ObservableOrderedCollection<E> {
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

		private final ObservableOrderedCollection<E> theWrapped;
		private final ListenerSet<Consumer<? super ObservableElement<E>>> theListeners;
		private final java.util.concurrent.CopyOnWriteArrayList<CachedElement<E>> theCache;
		private final ReentrantLock theLock;
		private final Consumer<ObservableElement<E>> theWrappedOnElement;

		private Runnable theUnsubscribe;

		/** @param wrap The collection to cache */
		public SafeCached(ObservableOrderedCollection<E> wrap) {
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
		public Iterator<E> iterator() {
			Collection<E> ret = refresh();
			return ret.iterator();
		}

		@Override
		public int size() {
			Collection<E> ret = refresh();
			return ret.size();
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

		private Collection<E> refresh() {
			// If we're currently caching, then returned the cached values. Otherwise return the dynamic values.
			if(theUnsubscribe != null)
				return theCache.stream().map(CachedElement::get).collect(Collectors.toList());
			else
				return theWrapped;
		}
	}
}
