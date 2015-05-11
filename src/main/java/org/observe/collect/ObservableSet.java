package org.observe.collect;

import static org.observe.ObservableDebug.debug;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

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
	default E [] toArray() {
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
	default ObservableSet<E> filter(Predicate<? super E> filter) {
		Function<E, E> map = value -> (value != null && filter.test(value)) ? value : null;
		return debug(new FilteredSet<>(this, getType(), map)).from("filter", this).using("filter", filter).get();
	}

	@Override
	default <T> ObservableSet<T> filter(Class<T> type) {
		Function<E, T> map = value -> type.isInstance(value) ? type.cast(value) : null;
		return debug(new FilteredSet<>(this, new Type(type), map)).from("filterMap", this).using("map", map).tag("filterType", type).get();
	}

	/**
	 * @param refresh The observable to re-fire events on
	 * @return A set whose elements fire additional value events when the given observable fires
	 */
	@Override
	default ObservableSet<E> refresh(Observable<?> refresh) {
		return debug(new RefreshingSet<>(this, refresh)).from("refresh", this).from("on", refresh).get();
	}

	/**
	 * @param refresh A function that supplies a refresh observable as a function of element value
	 * @return A collection whose values individually refresh when the observable returned by the given function fires
	 */
	@Override
	default ObservableSet<E> refreshEach(Function<? super E, Observable<?>> refresh) {
		return debug(new ElementRefreshingSet<>(this, refresh)).from("refreshEach", this).using("on", refresh).get();
	}

	@Override
	default ObservableSet<E> immutable() {
		return debug(new ImmutableObservableSet<>(this)).from("immutable", this).get();
	}

	/**
	 * Creates a collection with the same elements as this collection, but cached, such that the
	 *
	 * @return The cached collection
	 */
	@Override
	default ObservableSet<E> cached() {
		return debug(new SafeCachedObservableSet<>(this)).from("cached", this).get();
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
		class ConstantObservableSet implements PartialSetImpl<T> {
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
		class UniqueSet implements PartialSetImpl<T> {
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
	 * An extension of ObservableSet that implements some of the redundant methods and throws UnsupportedOperationExceptions for
	 * modifications.
	 *
	 * @param <E> The type of element in the set
	 */
	interface PartialSetImpl<E> extends PartialCollectionImpl<E>, ObservableSet<E> {
		@Override
		default boolean remove(Object o) {
			return PartialCollectionImpl.super.remove(o);
		}

		@Override
		default boolean removeAll(Collection<?> c) {
			return PartialCollectionImpl.super.removeAll(c);
		}

		@Override
		default boolean retainAll(Collection<?> c) {
			return PartialCollectionImpl.super.retainAll(c);
		}

		@Override
		default void clear() {
			PartialCollectionImpl.super.clear();
		}
	}

	/**
	 * Implements {@link ObservableSet#filter(Predicate)} and {@link ObservableSet#filter(Class)}
	 *
	 * @param <E> The type of the set to filter
	 * @param <T> the type of the mapped set
	 */
	class FilteredSet<E, T> extends FilteredCollection<E, T> implements PartialSetImpl<T> {
		protected FilteredSet(ObservableSet<E> wrap, Type type, Function<? super E, T> map) {
			super(wrap, type, map);
		}

		@Override
		protected ObservableSet<E> getWrapped() {
			return (ObservableSet<E>) super.getWrapped();
		}
	}

	/**
	 * Implements {@link ObservableSet#refresh(Observable)}
	 *
	 * @param <E> The type of the set
	 */
	class RefreshingSet<E> extends RefreshingCollection<E> implements PartialSetImpl<E> {
		protected RefreshingSet(ObservableSet<E> wrap, Observable<?> refresh) {
			super(wrap, refresh);
		}

		@Override
		protected ObservableSet<E> getWrapped() {
			return (ObservableSet<E>) super.getWrapped();
		}
	}

	/**
	 * Implements {@link ObservableSet#refreshEach(Function)}
	 *
	 * @param <E> The type of the set
	 */
	class ElementRefreshingSet<E> extends ElementRefreshingCollection<E> implements PartialSetImpl<E> {
		protected ElementRefreshingSet(ObservableSet<E> wrap, Function<? super E, Observable<?>> refresh) {
			super(wrap, refresh);
		}

		@Override
		protected ObservableSet<E> getWrapped() {
			return (ObservableSet<E>) super.getWrapped();
		}
	}

	/**
	 * An observable set that cannot be modified directly, but reflects the value of a wrapped set as it changes
	 *
	 * @param <E> The type of elements in the set
	 */
	class ImmutableObservableSet<E> extends ImmutableObservableCollection<E> implements PartialSetImpl<E> {
		protected ImmutableObservableSet(ObservableSet<E> wrap) {
			super(wrap);
		}

		@Override
		protected ObservableSet<E> getWrapped() {
			return (ObservableSet<E>) super.getWrapped();
		}

		@Override
		public ImmutableObservableSet<E> immutable() {
			return this;
		}
	}

	/**
	 * Implements {@link ObservableSet#cached()}
	 *
	 * @param <E> The type of elements in the set
	 */
	class SafeCachedObservableSet<E> extends SafeCachedObservableCollection<E> implements PartialSetImpl<E> {
		protected SafeCachedObservableSet(ObservableSet<E> wrap) {
			super(wrap);
		}

		@Override
		protected ObservableSet<E> getWrapped() {
			return (ObservableSet<E>) super.getWrapped();
		}

		@Override
		public ObservableSet<E> cached() {
			return this;
		}
	}
}
