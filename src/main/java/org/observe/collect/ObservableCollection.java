package org.observe.collect;

import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.observe.*;
import org.observe.ObservableDebug.D;
import org.observe.util.ListenerSet;

import prisms.lang.Type;

/**
 * A collection whose content can be observed
 *
 * @param <E> The type of element in the collection
 */
public interface ObservableCollection<E> extends Collection<E> {
	/** @return The type of elements in this collection */
	Type getType();

	/**
	 * @param onElement The listener to be notified when new elements are added to the collection
	 * @return The function to call when the calling code is no longer interested in this collection
	 */
	Runnable onElement(Consumer<? super ObservableElement<E>> onElement);

	/**
	 * @return The observable value for the current session of this collection. The session allows listeners to retain state for the
	 *         duration of a unit of work (controlled by implementation-specific means), batching events where possible. Not all events on a
	 *         collection will have a session (the value may be null). In addition, the presence or absence of a session need not imply
	 *         anything about the threaded interactions with a session. A transaction may encompass events fired and received on multiple
	 *         threads. In short, the only thing guaranteed about sessions is that they will end. Therefore, if a session is present,
	 *         observers may assume that they can delay expensive results of collection events until the session completes.
	 */
	ObservableValue<CollectionSession> getSession();

	/** @return An observable value for the size of this collection */
	default ObservableValue<Integer> observeSize() {
		return new ObservableValue<Integer>() {
			private final Type intType = new Type(Integer.TYPE);

			@Override
			public Type getType() {
				return intType;
			}

			@Override
			public Integer get() {
				return size();
			}

			@Override
			public Runnable internalSubscribe(Observer<? super ObservableValueEvent<Integer>> observer) {
				ObservableValue<Integer> sizeObs = this;
				return onElement(new Consumer<ObservableElement<E>>() {
					private AtomicInteger size = new AtomicInteger();

					@Override
					public void accept(ObservableElement<E> value) {
						int newSize = size.incrementAndGet();
						fire(newSize - 1, newSize, value);
						value.internalSubscribe(new Observer<ObservableValueEvent<E>>() {
							@Override
							public <V2 extends ObservableValueEvent<E>> void onNext(V2 value2) {
							}

							@Override
							public <V2 extends ObservableValueEvent<E>> void onCompleted(V2 value2) {
								int newSize2 = size.decrementAndGet();
								fire(newSize2 + 1, newSize2, value2);
							}
						});
					}

					private void fire(int oldSize, int newSize, Object cause) {
						observer.onNext(new ObservableValueEvent<>(sizeObs, oldSize, newSize, cause));
					}
				});
			}
		};
	}

	/** @return An observable that fires a change event whenever any elements in it are added, removed or changed */
	default Observable<? extends CollectionChangeEvent<E>> changes() {
		return new CollectionChangesObservable<>(this);
	}

	/** @return An observable that passes along only events for removal of elements from the collection */
	default Observable<ObservableValueEvent<E>> removes() {
		ObservableCollection<E> coll = this;
		return new Observable<ObservableValueEvent<E>>() {
			@Override
			public Runnable internalSubscribe(Observer<? super ObservableValueEvent<E>> observer) {
				return coll.onElement(element -> element.completed().act(value -> observer.onNext(value)));
			}

			@Override
			public String toString() {
				return "removes(" + coll + ")";
			}
		};
	}

	/**
	 * @param <T> The type of the new collection
	 * @param map The mapping function
	 * @return An observable collection of a new type backed by this collection and the mapping function
	 */
	default <T> ObservableCollection<T> map(Function<? super E, T> map) {
		return map(ComposedObservableValue.getReturnType(map), map);
	}

	/**
	 * @param <T> The type of the mapped collection
	 * @param type The run-time type for the mapped collection (may be null)
	 * @param map The mapping function to map the elements of this collection
	 * @return The mapped collection
	 */
	default <T> ObservableCollection<T> map(Type type, Function<? super E, T> map) {
		ObservableCollection<E> outer = this;
		class MappedObservableCollection extends java.util.AbstractCollection<T> implements ObservableCollection<T> {
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
		return new MappedObservableCollection();
	}

	/**
	 * @param filter The filter function
	 * @return A collection containing all elements passing the given test
	 */
	default ObservableCollection<E> filter(Function<? super E, Boolean> filter) {
		return filterMap(value -> {
			return (value != null && filter.apply(value)) ? value : null;
		});
	}

	/**
	 * @param <T> The type of the mapped collection
	 * @param filterMap The mapping function
	 * @return An observable collection of a new type backed by this collection and the mapping function
	 */
	default <T> ObservableCollection<T> filterMap(Function<? super E, T> filterMap) {
		return filterMap(ComposedObservableValue.getReturnType(filterMap), filterMap);
	}

	/**
	 * @param <T> The type of the mapped collection
	 * @param type The run-time type of the mapped collection
	 * @param map The mapping function
	 * @return A collection containing every element in this collection for which the mapping function returns a non-null value
	 */
	default <T> ObservableCollection<T> filterMap(Type type, Function<? super E, T> map) {
		ObservableCollection<E> outer = this;
		class FilteredCollection extends AbstractCollection<T> implements ObservableCollection<T> {
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
					FilteredElement<T, E> retElement = new FilteredElement<>(element, map, type);
					element.act(elValue -> {
						if(!retElement.isIncluded()) {
							T mapped = map.apply(elValue.getValue());
							if(mapped != null)
								observer.accept(retElement);
						}
					});
				});
			}
		}
		return new FilteredCollection();
	}

	/**
	 * Searches in this collection for an element. Since an ObservableCollection's order may or may not be significant, the element
	 * reflected in the value may not be the first element in the collection (by {@link #iterator()}) to match the filter.
	 *
	 * @param filter The filter function
	 * @return A value in this list passing the filter, or null if none of this collection's elements pass.
	 */
	default ObservableValue<E> find(Predicate<E> filter) {
		ObservableCollection<E> outer = this;
		return new ObservableValue<E>() {
			private final Type type = outer.getType().isPrimitive() ? new Type(Type.getWrapperType(outer.getType().getBaseType())) : outer
				.getType();

			@Override
			public Type getType() {
				return type;
			}

			@Override
			public E get() {
				for(E element : ObservableCollection.this) {
					if(filter.test(element))
						return element;
				}
				return null;
			}

			@Override
			public Runnable internalSubscribe(Observer<? super ObservableValueEvent<E>> observer) {
				if(isEmpty())
					observer.onNext(new ObservableValueEvent<>(this, null, null, null));
				final Object key = new Object();
				Runnable collSub = ObservableCollection.this.onElement(new Consumer<ObservableElement<E>>() {
					private E theValue;

					private boolean isFound;

					@Override
					public void accept(ObservableElement<E> element) {
						element.subscribe(new Observer<ObservableValueEvent<E>>() {
							@Override
							public <V3 extends ObservableValueEvent<E>> void onNext(V3 value) {
								if(!isFound && filter.test(value.getValue())) {
									isFound = true;
									newBest(value.getValue());
								}
							}

							@Override
							public <V3 extends ObservableValueEvent<E>> void onCompleted(V3 value) {
								if(theValue == value.getOldValue())
									findNextBest();
							}

							private void findNextBest() {
								isFound = false;
								for(E value : ObservableCollection.this) {
									if(filter.test(value)) {
										isFound = true;
										newBest(value);
										break;
									}
								}
								if(!isFound)
									newBest(null);
							}
						});
					}

					void newBest(E value) {
						E oldValue = theValue;
						theValue = value;
						CollectionSession session = getSession().get();
						if(session == null)
							observer.onNext(createEvent(oldValue, theValue, null));
						else {
							session.putIfAbsent(key, "oldBest", oldValue);
							session.put(key, "newBest", theValue);
						}
					}
				});
				Runnable transSub = getSession().internalSubscribe(new Observer<ObservableValueEvent<CollectionSession>>() {
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
				return "find in " + ObservableCollection.this;
			}
		};
	}

	/**
	 * @param <T> The type of the argument value
	 * @param <V> The type of the new observable collection
	 * @param arg The value to combine with each of this collection's elements
	 * @param func The combination function to apply to this collection's elements and the given value
	 * @return An observable collection containing this collection's elements combined with the given argument
	 */
	default <T, V> ObservableCollection<V> combine(ObservableValue<T> arg, BiFunction<? super E, ? super T, V> func) {
		return combine(arg, ComposedObservableValue.getReturnType(func), func);
	}

	/**
	 * @param <T> The type of the argument value
	 * @param <V> The type of the new observable collection
	 * @param arg The value to combine with each of this collection's elements
	 * @param type The type for the new collection
	 * @param func The combination function to apply to this collection's elements and the given value
	 * @return An observable collection containing this collection's elements combined with the given argument
	 */
	default <T, V> ObservableCollection<V> combine(ObservableValue<T> arg, Type type, BiFunction<? super E, ? super T, V> func) {
		ObservableCollection<E> outer = this;
		class CombinedObservableCollection extends AbstractCollection<V> implements ObservableCollection<V> {
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
		return new CombinedObservableCollection();
	}

	/**
	 * @param refresh The observable to re-fire events on
	 * @return A collection whose elements fire additional value events when the given observable fires
	 */
	default ObservableCollection<E> refresh(Observable<?> refresh) {
		ObservableCollection<E> outer = this;
		class RefreshingCollection extends AbstractCollection<E> implements ObservableCollection<E>{
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
				return theTransactionManager.onElement(outer, refresh, element -> observer.accept(element.refireWhen(refresh)));
			}
		};
		return new RefreshingCollection();
	}

	/**
	 * @param refire A function that supplies a refresh observable as a function of element value
	 * @return A collection whose values individually refresh when the observable returned by the given function fires
	 */
	default ObservableCollection<E> refreshEach(Function<? super E, Observable<?>> refire) {
		ObservableCollection<E> outer = this;
		return new org.observe.util.ObservableCollectionWrapper<E>(this) {
			@Override
			public Runnable onElement(Consumer<? super ObservableElement<E>> observer) {
				return outer.onElement(element -> observer.accept(element.refireWhenForValue(refire)));
			}
		};
	}

	/** @return An observable collection that cannot be modified directly but reflects the value of this collection as it changes */
	default ObservableCollection<E> immutable() {
		return new Immutable<>(this);
	}

	/**
	 * Creates a collection with the same elements as this collection, but cached, such that the
	 *
	 * @return The cached collection
	 */
	default ObservableCollection<E> cached() {
		return new SafeCached<>(this);
	}

	/**
	 * @param <T> An observable collection that contains all elements in all collections in the wrapping collection
	 * @param coll The collection to flatten
	 * @return A collection containing all elements of all collections in the outer collection
	 */
	public static <T> ObservableCollection<T> flatten(ObservableCollection<? extends ObservableCollection<T>> coll) {
		class ComposedObservableCollection extends AbstractCollection<T> implements ObservableCollection<T> {
			private final CombinedCollectionSessionObservable theSession = new CombinedCollectionSessionObservable(coll);

			@Override
			public ObservableValue<CollectionSession> getSession() {
				return theSession;
			}

			@Override
			public Type getType() {
				return coll.getType().getParamTypes().length == 0 ? new Type(Object.class) : coll.getType().getParamTypes()[0];
			}

			@Override
			public int size() {
				int ret = 0;
				for(ObservableCollection<T> subColl : coll)
					ret += subColl.size();
				return ret;
			}

			@Override
			public Iterator<T> iterator() {
				return new Iterator<T>() {
					private Iterator<? extends ObservableCollection<T>> outerBacking = coll.iterator();
					private Iterator<? extends T> innerBacking;

					@Override
					public boolean hasNext() {
						while((innerBacking == null || !innerBacking.hasNext()) && outerBacking.hasNext())
							innerBacking = outerBacking.next().iterator();
						return innerBacking != null && innerBacking.hasNext();
					}

					@Override
					public T next() {
						if(!hasNext())
							throw new java.util.NoSuchElementException();
						return innerBacking.next();
					}
				};
			}

			@Override
			public Runnable onElement(Consumer<? super ObservableElement<T>> observer) {
				return coll.onElement(new Consumer<ObservableElement<? extends ObservableCollection<T>>>() {
					private java.util.Map<ObservableCollection<T>, Runnable> subCollSubscriptions;

					{
						subCollSubscriptions = new org.observe.util.ConcurrentIdentityHashMap<>();
					}

					@Override
					public void accept(ObservableElement<? extends ObservableCollection<T>> subColl) {
						subColl.subscribe(new Observer<ObservableValueEvent<? extends ObservableCollection<T>>>() {
							@Override
							public <V2 extends ObservableValueEvent<? extends ObservableCollection<T>>> void onNext(V2 subCollEvent) {
								if(subCollEvent.getOldValue() != null && subCollEvent.getOldValue() != subCollEvent.getValue()) {
									Runnable subCollSub = subCollSubscriptions.get(subCollEvent.getOldValue());
									if(subCollSub != null)
										subCollSub.run();
								}
								Runnable subCollSub = subCollEvent.getValue().onElement(
									subElement -> observer.accept(new FlattenedElement<>(subElement, subColl)));
								subCollSubscriptions.put(subCollEvent.getValue(), subCollSub);
							}

							@Override
							public <V2 extends ObservableValueEvent<? extends ObservableCollection<T>>> void onCompleted(V2 subCollEvent) {
								subCollSubscriptions.remove(subCollEvent.getValue()).run();
							}
						});
					}
				});
			}
		}
		return new ComposedObservableCollection();
	}

	/**
	 * @param <T> An observable collection that contains all elements the given collections
	 * @param colls The collections to flatten
	 * @return A collection containing all elements of the given collections
	 */
	public static <T> ObservableCollection<T> flattenCollections(ObservableCollection<T>... colls) {
		return flatten(ObservableList.constant(new Type(ObservableCollection.class, new Type(Object.class, true)), colls));
	}

	/**
	 * @param <T> The type of the folded observable
	 * @param coll The collection to fold
	 * @return An observable that is notified for every event on any observable in the collection
	 */
	public static <T> Observable<T> fold(ObservableCollection<? extends Observable<T>> coll) {
		return new Observable<T>() {
			@Override
			public Runnable internalSubscribe(Observer<? super T> observer) {
				Observable<T> outer = this;
				D d = ObservableDebug.onSubscribe(this, "fold", null);
				Runnable ret = coll.onElement(element -> {
					D d2 = ObservableDebug.onNext(outer, "fold", null);
					element.subscribe(new Observer<ObservableValueEvent<? extends Observable<T>>>() {
						@Override
						public <V2 extends ObservableValueEvent<? extends Observable<T>>> void onNext(V2 value) {
							D d3 = ObservableDebug.onNext(outer, "fold/element", null);
							value.getValue().takeUntil(element.noInit()).subscribe(new Observer<T>() {
								@Override
								public <V3 extends T> void onNext(V3 value3) {
									D d4 = ObservableDebug.onNext(outer, "fold/element/value", null);
									observer.onNext(value3);
									d4.done(null);
								}

								@Override
								public void onError(Throwable e) {
									observer.onError(e);
								}
							});
							d3.done(null);
						}

						@Override
						public void onError(Throwable e) {
							observer.onError(e);
						}
					});
					d2.done(null);
				});
				d.done(null);
				return ret;
			}

			@Override
			public String toString() {
				return "fold(" + coll + ")";
			}
		};
	}

	/**
	 * The type of elements returned from {@link ObservableCollection#filterMap(Function)}
	 *
	 * @param <T> The type of this element
	 * @param <E> The type of element being wrapped
	 */
	public static class FilteredElement<T, E> implements ObservableElement<T> {
		private final ObservableElement<E> theWrappedElement;
		private final Function<? super E, T> theMap;
		private final Type theType;
		private boolean isIncluded;

		/**
		 * @param wrapped The element to wrap
		 * @param map The mapping function to filter on
		 * @param type The type of the element
		 */
		protected FilteredElement(ObservableElement<E> wrapped, Function<? super E, T> map, Type type) {
			theWrappedElement = wrapped;
			theMap = map;
			theType = type;
		}

		@Override
		public ObservableValue<T> persistent() {
			return theWrappedElement.mapV(theMap);
		}

		@Override
		public Type getType() {
			return theType;
		}

		@Override
		public T get() {
			return theMap.apply(theWrappedElement.get());
		}

		/** @return The element that this filtered element wraps */
		protected ObservableElement<E> getWrapped() {
			return theWrappedElement;
		}

		/** @return The mapping function used by this element */
		protected Function<? super E, T> getMap() {
			return theMap;
		}

		/** @return Whether this element is currently included in the filtered collection */
		protected boolean isIncluded() {
			return isIncluded;
		}

		@Override
		public Runnable internalSubscribe(Observer<? super ObservableValueEvent<T>> observer2) {
			Runnable [] innerSub = new Runnable[1];
			innerSub[0] = theWrappedElement.internalSubscribe(new Observer<ObservableValueEvent<E>>() {
				@Override
				public <V2 extends ObservableValueEvent<E>> void onNext(V2 elValue) {
					T mapped = theMap.apply(elValue.getValue());
					if(mapped == null) {
						isIncluded = false;
						T oldValue = theMap.apply(elValue.getOldValue());
						observer2.onCompleted(new ObservableValueEvent<>(FilteredElement.this, oldValue, oldValue, elValue));
						if(innerSub[0] != null) {
							innerSub[0].run();
							innerSub[0] = null;
						}
					} else {
						isIncluded = true;
						observer2.onNext(new ObservableValueEvent<>(FilteredElement.this, theMap.apply(elValue.getOldValue()), mapped,
							elValue));
					}
				}

				@Override
				public <V2 extends ObservableValueEvent<E>> void onCompleted(V2 elValue) {
					T oldVal, newVal;
					if(elValue != null) {
						oldVal = theMap.apply(elValue.getOldValue());
						newVal = theMap.apply(elValue.getValue());
					} else {
						oldVal = get();
						newVal = oldVal;
					}
					observer2.onCompleted(new ObservableValueEvent<>(FilteredElement.this, oldVal, newVal, elValue));
				}
			});
			if(!isIncluded) {
				return () -> {
				};
			}
			return innerSub[0];
		}

		@Override
		public String toString() {
			return "filter(" + theWrappedElement + ")";
		}
	}

	/**
	 * An element in a {@link ObservableCollection#flatten(ObservableCollection) flattened} collection
	 *
	 * @param <T> The type of the element
	 */
	public class FlattenedElement<T> implements ObservableElement<T> {
		private final ObservableElement<T> subElement;
		private final ObservableElement<? extends ObservableCollection<T>> subCollectionEl;
		private boolean isRemoved;

		/**
		 * @param subEl The sub-collection element to wrap
		 * @param subColl The element containing the sub-collection
		 */
		protected FlattenedElement(ObservableElement<T> subEl, ObservableElement<? extends ObservableCollection<T>> subColl) {
			if(subEl == null)
				throw new NullPointerException();
			subElement = subEl;
			subCollectionEl = subColl;
			subColl.completed().act(value -> isRemoved = true);
		}

		/** @return The element in the outer collection containing the inner collection that contains this element's wrapped element */
		protected ObservableElement<? extends ObservableCollection<T>> getSubCollectionElement() {
			return subCollectionEl;
		}

		/** @return The wrapped sub-collection element */
		protected ObservableElement<T> getSubElement() {
			return subElement;
		}

		@Override
		public ObservableValue<T> persistent() {
			return subElement;
		}

		/** @return Whether this element has been removed or not */
		protected boolean isRemoved() {
			return isRemoved;
		}

		@Override
		public Type getType() {
			return subElement.getType();
		}

		@Override
		public T get() {
			return subElement.get();
		}

		@Override
		public Runnable internalSubscribe(Observer<? super ObservableValueEvent<T>> observer2) {
			return subElement.takeUntil(subCollectionEl.completed()).internalSubscribe(observer2);
		}

		@Override
		public String toString() {
			return "flattened(" + subElement.toString() + ")";
		}
	}

	/**
	 * An observable collection that cannot be modified directly, but reflects the value of a wrapped collection as it changes
	 *
	 * @param <E> The type of elements in the collection
	 */
	public static class Immutable<E> extends AbstractCollection<E> implements ObservableCollection<E> {
		private final ObservableCollection<E> theWrapped;

		/** @param wrap The collection to wrap */
		public Immutable(ObservableCollection<E> wrap) {
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
	 * Caches the values in an observable collection. As long as this collection is being listened to, it will maintain a cache of the
	 * values in the given collection. When all observers to the collection have been unsubscribed, the cache is cleared and not maintained.
	 * If the cache is active, all access methods to this cache, including the native {@link Collection} methods, will use the cached
	 * values. If the cache is not active, the {@link Collection} methods will delegate to the wrapped collection.
	 *
	 * @param <E> The type of elements in the collection
	 */
	public static class SafeCached<E> extends AbstractCollection<E> implements ObservableCollection<E> {
		private static class CachedElement<E> implements ObservableElement<E> {
			private final ObservableElement<E> theWrapped;
			private final ListenerSet<Observer<? super ObservableValueEvent<E>>> theElementListeners;

			private E theCachedValue;

			CachedElement(ObservableElement<E> wrap) {
				theWrapped = wrap;
				theElementListeners = new ListenerSet<>();
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
			public Runnable internalSubscribe(Observer<? super ObservableValueEvent<E>> observer) {
				theElementListeners.add(observer);
				observer.onNext(new ObservableValueEvent<>(this, theCachedValue, theCachedValue, null));
				return () -> theElementListeners.remove(observer);
			}

			@Override
			public ObservableValue<E> persistent() {
				return theWrapped.persistent();
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

		private ObservableCollection<E> theWrapped;
		private final ListenerSet<Consumer<? super ObservableElement<E>>> theListeners;
		private final org.observe.util.ConcurrentIdentityHashMap<ObservableElement<E>, CachedElement<E>> theCache;
		private final ReentrantLock theLock;
		private final Consumer<ObservableElement<E>> theWrappedOnElement;

		private Runnable theUnsubscribe;

		/** @param wrap The collection to cache */
		public SafeCached(ObservableCollection<E> wrap) {
			theWrapped = wrap;
			theListeners = new ListenerSet<>();
			theCache = new org.observe.util.ConcurrentIdentityHashMap<>();
			theLock = new ReentrantLock();
			theWrappedOnElement = element -> {
				CachedElement<E> cached=new CachedElement<>(element);
				theCache.put(element, cached);
				element.internalSubscribe(new Observer<ObservableValueEvent<E>>(){
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
			for(CachedElement<E> cached : theCache.values())
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
				theUnsubscribe=null;
			}
		}

		private Collection<E> refresh() {
			// If we're currently caching, then returned the cached values. Otherwise return the dynamic values.
			if(theUnsubscribe != null)
				return theCache.values().stream().map(CachedElement::get).collect(Collectors.toList());
			else
				return theWrapped;
		}
	}
}
