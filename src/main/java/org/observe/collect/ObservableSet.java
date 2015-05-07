package org.observe.collect;

import static org.observe.ObservableDebug.debug;

import java.util.AbstractSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;

import prisms.lang.Type;

/**
 * A set whose content can be observed
 *
 * @param <E> The type of element in the set
 */
public interface ObservableSet<E> extends ObservableCollection<E>, Set<E> {
	@Override
	default boolean isEmpty() {
		return ObservableCollection.super.isEmpty();
	}

	@Override
	default boolean contains(Object o) {
		return ObservableCollection.super.contains(o);
	}

	@Override
	default boolean containsAll(java.util.Collection<?> coll) {
		return ObservableCollection.super.containsAll(coll);
	}

	@Override
	default Object [] toArray() {
		return ObservableCollection.super.toArray();
	}

	@Override
	default <T> T [] toArray(T [] a) {
		return ObservableCollection.super.toArray(a);
	}

	/**
	 * @param filter The filter function
	 * @return A set containing all elements passing the given test
	 */
	@Override
	default ObservableSet<E> filter(Function<? super E, Boolean> filter) {
		ObservableSet<E> outer = this;
		class FilteredSet extends AbstractSet<E> implements ObservableSet<E> {
			@Override
			public ObservableValue<CollectionSession> getSession() {
				return outer.getSession();
			}

			@Override
			public Type getType() {
				return outer.getType();
			}

			@Override
			public int size() {
				int ret = 0;
				for(E el : outer)
					if(filter.apply(el) != null)
						ret++;
				return ret;
			}

			@Override
			public Iterator<E> iterator() {
				return new Iterator<E>() {
					private final Iterator<E> backing = outer.iterator();

					private E nextVal;

					@Override
					public boolean hasNext() {
						while(nextVal == null && backing.hasNext()) {
							nextVal = backing.next();
							if(!filter.apply(nextVal))
								nextVal = null;
						}
						return nextVal != null;
					}

					@Override
					public E next() {
						if(nextVal == null && !hasNext())
							throw new java.util.NoSuchElementException();
						E ret = nextVal;
						nextVal = null;
						return ret;
					}
				};
			}

			@Override
			public Runnable onElement(Consumer<? super ObservableElement<E>> observer) {
				Function<E, E> map = value -> (filter.apply(value) ? value : null);
				return outer.onElement(element -> {
					FilteredElement<E, E> retElement = debug(new FilteredElement<>(element, map, getType())).from("element", this)
						.tag("wrapped", element).using("map", map).get();
					element.act(elValue -> {
						if(!retElement.isIncluded()) {
							E mapped = map.apply(elValue.getValue());
							if(mapped != null)
								observer.accept(retElement);
						}
					});
				});
			}

			@Override
			public String toString() {
				return "filter(" + outer + ")";
			}
		}
		return debug(new FilteredSet()).from("filter", this).using("filter", filter).get();
	}

	/**
	 * @param refresh The observable to re-fire events on
	 * @return A collection whose elements fire additional value events when the given observable fires
	 */
	@Override
	default ObservableSet<E> refresh(Observable<?> refresh) {
		ObservableSet<E> outer = this;
		class RefreshingCollection extends AbstractSet<E> implements ObservableSet<E> {
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
			public Iterator<E> iterator() {
				return outer.iterator();
			}

			@Override
			public int size() {
				return outer.size();
			}

			@Override
			public Runnable onElement(Consumer<? super ObservableElement<E>> observer) {
				return theTransactionManager.onElement(outer, refresh, element -> observer.accept(element.refresh(refresh)), true);
			}
		};
		return debug(new RefreshingCollection()).from("refresh", this).from("on", refresh).get();
	}

	/**
	 * @param refire A function that supplies a refresh observable as a function of element value
	 * @return A collection whose values individually refresh when the observable returned by the given function fires
	 */
	@Override
	default ObservableSet<E> refreshEach(Function<? super E, Observable<?>> refire) {
		ObservableCollection<E> outer = this;
		return debug(new org.observe.util.ObservableSetWrapper<E>(this) {
			@Override
			public Runnable onElement(Consumer<? super ObservableElement<E>> observer) {
				return outer.onElement(element -> observer.accept(element.refreshForValue(refire)));
			}
		}).from("refreshEach", this).using("on", refire).get();
	}

	@Override
	default ObservableSet<E> immutable() {
		return debug(new Immutable<>(this)).from("immutable", this).get();
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
			public Runnable onElement(Consumer<? super ObservableElement<T>> observer) {
				for(ObservableElement<T> el : els)
					observer.accept(el);
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
		ConstantObservableSet ret = debug(new ConstantObservableSet()).tag("constant", coll).tag("type", type).get();
		for(T value : constSet)
			els.add(debug(new ObservableElement<T>() {
				@Override
				public Type getType() {
					return type;
				}

				@Override
				public T get() {
					return value;
				}

				@Override
				public Runnable observe(Observer<? super ObservableValueEvent<T>> observer) {
					observer.onNext(new ObservableValueEvent<>(this, null, value, null));
					return () -> {
					};
				}

				@Override
				public ObservableValue<T> persistent() {
					return this;
				}
			}).from("element", ret).tag("value", value).get());
		return ret;
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
			public Runnable observe(Observer<? super ObservableValueEvent<T>> observer2) {
				Runnable [] innerSub = new Runnable[1];
				innerSub[0] = theWrappedElement.observe(new Observer<ObservableValueEvent<T>>() {
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
			public Runnable onElement(Consumer<? super ObservableElement<T>> observer) {
				return coll.onElement(element -> {
					UniqueFilteredElement retElement = debug(new UniqueFilteredElement(element, theElements.keySet()))
						.from("element", this).tag("wrapped", element).get();
					theElements.put(retElement, 0);
					element.subscribe(new Observer<ObservableValueEvent<T>>() {
						@Override
						public <V2 extends ObservableValueEvent<T>> void onNext(V2 elValue) {
							if(!retElement.isIncluded() && retElement.shouldBeIncluded(elValue.getValue())) {
								observer.accept(retElement);
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
										observer.accept(el2);
										break;
									}
								}
							}
						}
					});
				});
			}
		}
		return debug(new UniqueSet()).from("unique", coll).get();
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
}
