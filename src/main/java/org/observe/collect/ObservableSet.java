package org.observe.collect;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.observe.*;
import org.observe.ObservableDebug.D;
import org.observe.Observable;
import org.observe.Observer;

import prisms.lang.Type;

/**
 * A set whose content can be observed
 *
 * @param <E> The type of element in the set
 */
public interface ObservableSet<E> extends ObservableCollection<E>, Set<E> {
	/**
	 * @param map The mapping function
	 * @return An observable collection of a new type backed by this collection and the mapping function
	 */
	@Override
	default <T> ObservableSet<T> mapC(Function<? super E, T> map) {
		return mapC(ComposedObservableValue.getReturnType(map), map);
	}

	@Override
	default <T> ObservableSet<T> mapC(Type type, Function<? super E, T> map) {
		ObservableSet<E> outerSet = this;
		class MappedObservableSet extends java.util.AbstractSet<T> implements ObservableSet<T> {
			@Override
			public ObservableValue<CollectionSession> getSession() {
				return outerSet.getSession();
			}

			@Override
			public Type getType() {
				return type;
			}

			@Override
			public int size() {
				return outerSet.size();
			}

			@Override
			public Iterator<T> iterator() {
				return new Iterator<T>() {
					private final Iterator<E> backing = outerSet.iterator();

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
			public Runnable internalSubscribe(Observer<? super ObservableElement<T>> observer) {
				Runnable sub = outerSet.internalSubscribe(new Observer<ObservableElement<E>>() {
					@Override
					public <V extends ObservableElement<E>> void onNext(V value) {
						observer.onNext(value.mapV(map));
					}

					@Override
					public <V extends ObservableElement<E>> void onCompleted(V value) {
						observer.onCompleted(value.mapV(map));
					}

					@Override
					public void onError(Throwable e) {
						observer.onError(e);

					}
				});
				return sub;
			}
		}
		return new MappedObservableSet();
	}

	/**
	 * @param filter The filter function
	 * @return A set containing all elements passing the given test
	 */
	@Override
	default ObservableSet<E> filterC(Function<? super E, Boolean> filter) {
		return filterMapC(value -> {
			return (value != null && filter.apply(value)) ? value : null;
		});
	}

	@Override
	default <T> ObservableSet<T> filterMapC(Function<? super E, T> filterMap) {
		return filterMapC(ComposedObservableValue.getReturnType(filterMap), filterMap);
	}

	@Override
	default <T> ObservableSet<T> filterMapC(Type type, Function<? super E, T> map) {
		ObservableSet<E> outer = this;
		class FilteredSet extends AbstractSet<T> implements ObservableSet<T> {
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
			public Runnable internalSubscribe(Observer<? super ObservableElement<T>> observer) {
				Runnable listSub = outer.internalSubscribe(new Observer<ObservableElement<E>>() {
					@Override
					public <V extends ObservableElement<E>> void onNext(V el) {
						FilteredElement<T, E> retElement = new FilteredElement<>(el, map, type);
						el.act(elValue -> {
							if(!retElement.isIncluded()) {
								T mapped = map.apply(elValue.getValue());
								if(mapped != null)
									observer.onNext(retElement);
							}
						});
					}

					@Override
					public void onError(Throwable e) {
						observer.onError(e);
					}
				});
				return () -> {
					listSub.run();
				};
			}

			@Override
			public String toString() {
				return "filter(" + outer + ")";
			}
		}
		return new FilteredSet();
	}

	/**
	 * @param <T> The type of the argument value
	 * @param <V> The type of the new observable set
	 * @param arg The value to combine with each of this set's elements
	 * @param func The combination function to apply to this set's elements and the given value
	 * @return An observable set containing this set's elements combined with the given argument
	 */
	default <T, V> ObservableSet<V> combineC(ObservableValue<T> arg, BiFunction<? super E, ? super T, V> func) {
		return combineC(arg, ComposedObservableValue.getReturnType(func), func);
	}

	/**
	 * @param <T> The type of the argument value
	 * @param <V> The type of the new observable set
	 * @param arg The value to combine with each of this set's elements
	 * @param type The type for the new set
	 * @param func The combination function to apply to this set's elements and the given value
	 * @return An observable set containing this set's elements combined with the given argument
	 */
	default <T, V> ObservableSet<V> combineC(ObservableValue<T> arg, Type type, BiFunction<? super E, ? super T, V> func) {
		ObservableSet<E> outerSet = this;
		class CombinedObservableSet extends AbstractSet<V> implements ObservableSet<V> {
			@Override
			public ObservableValue<CollectionSession> getSession() {
				return outerSet.getSession();
			}

			@Override
			public Type getType() {
				return type;
			}

			@Override
			public int size() {
				return outerSet.size();
			}

			@Override
			public Iterator<V> iterator() {
				return new Iterator<V>() {
					private final Iterator<E> backing = outerSet.iterator();

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
			public Runnable internalSubscribe(Observer<? super ObservableElement<V>> observer) {
				boolean [] complete = new boolean[1];
				Runnable setSub = outerSet.internalSubscribe(new Observer<ObservableElement<E>>() {
					@Override
					public <V2 extends ObservableElement<E>> void onNext(V2 value) {
						observer.onNext(value.combineV(func, arg));
					}

					@Override
					public <V2 extends ObservableElement<E>> void onCompleted(V2 value) {
						complete[0] = true;
						observer.onCompleted(value.combineV(func, arg));
					}

					@Override
					public void onError(Throwable e) {
						observer.onError(e);
					}
				});
				if(complete[0])
					return () -> {
					};
					Runnable argSub = arg.internalSubscribe(new Observer<ObservableValueEvent<T>>() {
						@Override
						public <V2 extends ObservableValueEvent<T>> void onNext(V2 value) {
						}

						@Override
						public <V2 extends ObservableValueEvent<T>> void onCompleted(V2 value) {
							complete[0] = true;
							observer.onCompleted(null);
							setSub.run();
						}
					});
					if(complete[0])
						return () -> {
						};
						return () -> {
							setSub.run();
							argSub.run();
						};
			}
		}
		return new CombinedObservableSet();
	}

	/**
	 * @param observable The observable to re-fire events on
	 * @return A collection whose elements fire additional value events when the given observable fires
	 */
	@Override
	default ObservableSet<E> refireWhen(Observable<?> observable) {
		ObservableSet<E> outer = this;
		return new org.observe.util.ObservableSetWrapper<E>(this) {
			@Override
			public Runnable internalSubscribe(Observer<? super ObservableElement<E>> observer) {
				return outer.internalSubscribe(new Observer<ObservableElement<E>>() {
					@Override
					public <V extends ObservableElement<E>> void onNext(V value) {
						D d = ObservableDebug.onNext(outer, "refireWhen", null);
						observer.onNext(value.refireWhen(observable));
						d.done(null);
					}
				});
			}
		};
	}

	/**
	 * @param refire A function that supplies a refire observable as a function of element value
	 * @return A collection whose values individually refire when the observable returned by the given function fires
	 */
	@Override
	default ObservableSet<E> refireWhenEach(Function<? super E, Observable<?>> refire) {
		ObservableCollection<E> outer = this;
		return new org.observe.util.ObservableSetWrapper<E>(this) {
			@Override
			public Runnable internalSubscribe(Observer<? super ObservableElement<E>> observer) {
				return outer.internalSubscribe(new Observer<ObservableElement<E>>() {
					@Override
					public <V extends ObservableElement<E>> void onNext(V element) {
						observer.onNext(element.refireWhenForValue(refire));
					}
				});
			}
		};
	}

	@Override
	default ObservableSet<E> immutable() {
		return new Immutable<>(this);
	}

	/**
	 * @param <T> The type of the collection
	 * @param type The run-time type of the collection
	 * @param coll The collection with elements to wrap
	 * @return A collection containing the given elements that cannot be changed
	 */
	public static <T> ObservableSet<T> constant(Type type, java.util.Collection<T> coll) {
		Set<T> modSet = new java.util.LinkedHashSet<>(coll);
		Set<T> constSet = java.util.Collections.unmodifiableSet(modSet);
		java.util.List<ObservableElement<T>> els = new java.util.ArrayList<>();
		for(T value : constSet)
			els.add(new ObservableElement<T>() {
				@Override
				public Type getType() {
					return type;
				}

				@Override
				public T get() {
					return value;
				}

				@Override
				public Runnable internalSubscribe(Observer<? super ObservableValueEvent<T>> observer) {
					observer.onNext(new ObservableValueEvent<>(this, null, value, null));
					return () -> {
					};
				}

				@Override
				public ObservableValue<T> persistent() {
					return this;
				}
			});
		class ConstantObservableSet extends AbstractSet<T> implements ObservableSet<T> {
			@Override
			public ObservableValue<CollectionSession> getSession() {
				return ObservableValue.constant(new Type(CollectionSession.class), null);
			}

			@Override
			public Type getType() {
				return type;
			}

			@Override
			public Runnable internalSubscribe(Observer<? super ObservableElement<T>> observer) {
				for(ObservableElement<T> el : els)
					observer.onNext(el);
				return () -> {
				};
			}

			@Override
			public int size() {
				return constSet.size();
			}

			@Override
			public Iterator<T> iterator() {
				return constSet.iterator();
			}
		}
		return new ConstantObservableSet();
	}

	/**
	 * @param <T> The type of the collection
	 * @param type The run-time type of the collection
	 * @param values The array with elements to wrap
	 * @return A collection containing the given elements that cannot be changed
	 */
	public static <T> ObservableSet<T> constant(Type type, T... values) {
		return constant(type, java.util.Arrays.asList(values));
	}

	/**
	 * @param <T> The type of the collection
	 * @param coll The collection to turn into a set
	 * @return A set containing all unique elements of the given collection
	 */
	public static <T> ObservableSet<T> unique(ObservableCollection<T> coll) {
		class UniqueFilteredElement implements ObservableElement<T> {
			private ObservableElement<T> theWrappedElement;
			private Set<UniqueFilteredElement> theElements;
			private boolean isIncluded;

			UniqueFilteredElement(ObservableElement<T> wrapped, Set<UniqueFilteredElement> elements) {
				theWrappedElement = wrapped;
				theElements = elements;
			}

			@Override
			public ObservableValue<T> persistent() {
				return theWrappedElement.persistent();
			}

			@Override
			public Type getType() {
				return coll.getType();
			}

			@Override
			public T get() {
				return theWrappedElement.get();
			}

			boolean isIncluded() {
				return isIncluded;
			}

			boolean shouldBeIncluded(T value) {
				for(UniqueFilteredElement el : theElements)
					if(el != this && el.isIncluded && Objects.equals(el.get(), value))
						return false;
				return true;
			}

			@Override
			public Runnable internalSubscribe(Observer<? super ObservableValueEvent<T>> observer2) {
				Runnable [] innerSub = new Runnable[1];
				innerSub[0] = theWrappedElement.internalSubscribe(new Observer<ObservableValueEvent<T>>() {
					@Override
					public <V2 extends ObservableValueEvent<T>> void onNext(V2 elValue) {
						boolean shouldBe = shouldBeIncluded(elValue.getValue());
						if(isIncluded && !shouldBe) {
							isIncluded = false;
							observer2.onCompleted(new ObservableValueEvent<>(UniqueFilteredElement.this, elValue.getOldValue(), elValue
								.getValue(), elValue));
							if(innerSub[0] != null) {
								innerSub[0].run();
								innerSub[0] = null;
							}
						} else if(!isIncluded && shouldBe) {
							isIncluded = true;
							observer2.onNext(new ObservableValueEvent<>(UniqueFilteredElement.this, elValue.getOldValue(), elValue
								.getValue(), elValue));
						}
					}

					@Override
					public <V2 extends ObservableValueEvent<T>> void onCompleted(V2 elValue) {
						T oldVal, newVal;
						if(elValue != null) {
							oldVal = elValue.getOldValue();
							newVal = elValue.getValue();
						} else {
							oldVal = get();
							newVal = oldVal;
						}
						observer2.onCompleted(new ObservableValueEvent<>(UniqueFilteredElement.this, oldVal, newVal, elValue));
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
		class UniqueSet extends AbstractSet<T> implements ObservableSet<T> {
			private java.util.concurrent.ConcurrentHashMap<UniqueFilteredElement, Object> theElements = new java.util.concurrent.ConcurrentHashMap<>();

			@Override
			public ObservableValue<CollectionSession> getSession() {
				return coll.getSession();
			}

			@Override
			public Type getType() {
				return coll.getType().getParamTypes().length == 0 ? new Type(Object.class) : coll.getType().getParamTypes()[0];
			}

			@Override
			public int size() {
				HashSet<T> set = new HashSet<>();
				for(T val : coll)
					set.add(val);
				return set.size();
			}

			@Override
			public Iterator<T> iterator() {
				return new Iterator<T>() {
					private final Iterator<T> backing = coll.iterator();
					private final HashSet<T> set = new HashSet<>();
					private T nextVal;

					@Override
					public boolean hasNext() {
						while(nextVal == null && backing.hasNext()) {
							nextVal = backing.next();
							if(!set.add(nextVal))
								nextVal = null;
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

					@Override
					public void remove() {
						backing.remove();
					}
				};
			}

			@Override
			public Runnable internalSubscribe(Observer<? super ObservableElement<T>> observer) {
				Runnable listSub = coll.internalSubscribe(new Observer<ObservableElement<T>>() {
					@Override
					public <V extends ObservableElement<T>> void onNext(V el) {
						UniqueFilteredElement retElement = new UniqueFilteredElement(el, theElements.keySet());
						theElements.put(retElement, 0);
						el.subscribe(new Observer<ObservableValueEvent<T>>() {
							@Override
							public <V2 extends ObservableValueEvent<T>> void onNext(V2 elValue) {
								if(!retElement.isIncluded() && retElement.shouldBeIncluded(elValue.getValue())) {
									observer.onNext(retElement);
								}
							}

							@Override
							public <V2 extends ObservableValueEvent<T>> void onCompleted(V2 elValue) {
								theElements.remove(retElement);
								if(retElement.isIncluded()) {
									for(UniqueFilteredElement el2 : theElements.keySet()) {
										if(el2 == retElement)
											continue;
										if(!el2.isIncluded() && Objects.equals(el2.get(), elValue.getOldValue())) {
											observer.onNext(el2);
											break;
										}
									}
								}
							}
						});
					}

					@Override
					public void onError(Throwable e) {
						observer.onError(e);
					}
				});
				return () -> {
					listSub.run();
				};
			}
		}
		return new UniqueSet();
	}

	/**
	 * @param <T> An observable set that contains all elements in all collections in the wrapping collection
	 * @param coll The collection to flatten
	 * @return A collection containing all elements of all collections in the outer collection
	 */
	public static <T> ObservableSet<T> flatten(ObservableCollection<? extends ObservableCollection<T>> coll) {
		return unique(ObservableCollection.flatten(coll));
	}

	/**
	 * @param <T> An observable set that contains all elements the given collections
	 * @param colls The collections to flatten
	 * @return A collection containing all elements of the given collections
	 */
	public static <T> ObservableSet<T> flattenCollections(ObservableCollection<T>... colls) {
		return flatten(ObservableList.constant(new Type(ObservableCollection.class, new Type(Object.class, true)), colls));
	}

	/**
	 * An observable set that cannot be modified directly, but reflects the value of a wrapped set as it changes
	 *
	 * @param <E> The type of elements in the set
	 */
	public static class Immutable<E> extends AbstractSet<E> implements ObservableSet<E> {
		private final ObservableSet<E> theWrapped;

		/** @param wrap The set to wrap */
		public Immutable(ObservableSet<E> wrap) {
			theWrapped = wrap;
		}

		@Override
		public ObservableValue<CollectionSession> getSession() {
			return theWrapped.getSession();
		}

		@Override
		public Runnable internalSubscribe(Observer<? super ObservableElement<E>> observer) {
			return theWrapped.internalSubscribe(observer);
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
}
