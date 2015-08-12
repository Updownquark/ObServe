package org.observe.collect;

import static org.observe.ObservableDebug.d;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.Subscription;
import org.observe.datastruct.MultiMap;
import org.observe.util.Transaction;

import prisms.lang.Type;

/**
 * A set whose content can be observed
 *
 * @param <E> The type of element in the set
 */
public interface ObservableSet<E> extends ObservableCollection<E>, TransactableSet<E> {
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

	/**
	 * @param o The object to get the equivalent of
	 * @return The object in this set whose value is equivalent to the given value
	 */
	default ObservableValue<E> equivalent(Object o) {
		return new ObservableSetEquivalentFinder<>(this, o);
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
		return (ObservableSet<E>) ObservableCollection.super.filter(filter);
	}

	@Override
	default ObservableSet<E> filter(Predicate<? super E> filter, boolean staticFilter) {
		if(staticFilter)
			return filterStatic(filter);
		else
			return filterDynamic(filter);
	}

	@Override
	default ObservableSet<E> filterDynamic(Predicate<? super E> filter) {
		Function<E, E> map = value -> (value != null && filter.test(value)) ? value : null;
		return d().debug(new DynamicFilteredSet<>(this, getType(), map)).from("filter", this).using("filter", filter).get();
	}

	@Override
	default ObservableSet<E> filterStatic(Predicate<? super E> filter) {
		Function<E, E> map = value -> (value != null && filter.test(value)) ? value : null;
		return d().debug(new StaticFilteredSet<>(this, getType(), map)).from("filter", this).using("filter", filter).get();
	}

	@Override
	default <T> ObservableSet<T> filter(Class<T> type) {
		Function<E, T> map = value -> type.isInstance(value) ? type.cast(value) : null;
		return d().debug(new StaticFilteredSet<>(this, new Type(type), map)).from("filterMap", this).using("map", map)
			.tag("filterType", type).get();
	}

	/**
	 * @param refresh The observable to re-fire events on
	 * @return A set whose elements fire additional value events when the given observable fires
	 */
	@Override
	default ObservableSet<E> refresh(Observable<?> refresh) {
		return d().debug(new RefreshingSet<>(this, refresh)).from("refresh", this).from("on", refresh).get();
	}

	/**
	 * @param refresh A function that supplies a refresh observable as a function of element value
	 * @return A collection whose values individually refresh when the observable returned by the given function fires
	 */
	@Override
	default ObservableSet<E> refreshEach(Function<? super E, Observable<?>> refresh) {
		return d().debug(new ElementRefreshingSet<>(this, refresh)).from("refreshEach", this).using("on", refresh).get();
	}

	@Override
	default ObservableSet<E> immutable() {
		return d().debug(new ImmutableObservableSet<>(this)).from("immutable", this).get();
	}

	@Override
	default ObservableSet<E> filterRemove(Predicate<? super E> filter) {
		return (ObservableSet<E>) ObservableCollection.super.filterRemove(filter);
	}

	@Override
	default ObservableSet<E> noRemove() {
		return (ObservableSet<E>) ObservableCollection.super.noRemove();
	}

	@Override
	default ObservableSet<E> filterAdd(Predicate<? super E> filter) {
		return (ObservableSet<E>) ObservableCollection.super.filterAdd(filter);
	}

	@Override
	default ObservableSet<E> noAdd() {
		return (ObservableSet<E>) ObservableCollection.super.noAdd();
	}

	@Override
	default ObservableSet<E> filterModification(Predicate<? super E> removeFilter, Predicate<? super E> addFilter) {
		return new ModFilteredSet<>(this, removeFilter, addFilter);
	}

	/**
	 * Creates a collection with the same elements as this collection, but cached, such that the
	 *
	 * @return The cached collection
	 */
	@Override
	default ObservableSet<E> cached() {
		return d().debug(new SafeCachedObservableSet<>(this)).from("cached", this).get();
	}

	/**
	 * A default toString() method for set implementations to use
	 *
	 * @param set The set to print
	 * @return The string representation of the set
	 */
	public static String toString(ObservableSet<?> set) {
		StringBuilder ret = new StringBuilder("{");
		boolean first = true;
		try (Transaction t = set.lock(false, null)) {
			for(Object value : set) {
				if(!first) {
					ret.append(", ");
				} else
					first = false;
				ret.append(value);
			}
		}
		ret.append('}');
		return ret.toString();
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
			public Transaction lock(boolean write, Object cause) {
				return () -> {
				};
			}

			@Override
			public Type getType() {
				return type;
			}

			@Override
			public Subscription onElement(Consumer<? super ObservableElement<T>> observer) {
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
		ConstantObservableSet ret = d().debug(new ConstantObservableSet()).tag("constant", coll).tag("type", type).get();
		for(T value : constSet)
			els.add(d().debug(new ObservableElement<T>() {
				@Override
				public Type getType() {
					return type;
				}

				@Override
				public T get() {
					return value;
				}

				@Override
				public Subscription subscribe(Observer<? super ObservableValueEvent<T>> observer) {
					observer.onNext(createInitialEvent(value));
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
			private ObservableElement<T> theElement;

			private java.util.concurrent.ConcurrentLinkedQueue<Observer<? super ObservableValueEvent<T>>> theObservers;

			UniqueFilteredElement(ObservableElement<T> element) {
				theElement = element;
				theObservers = new java.util.concurrent.ConcurrentLinkedQueue<>();
			}

			@Override
			public ObservableValue<T> persistent() {
				return theElement.persistent();
			}

			@Override
			public Type getType() {
				return coll.getType();
			}

			@Override
			public T get() {
				return theElement.get();
			}

			@Override
			public Subscription subscribe(Observer<? super ObservableValueEvent<T>> observer2) {
				theObservers.add(observer2);
				observer2.onNext(createInitialEvent(theElement.get()));
				return () -> theObservers.remove(observer2);
			}

			void valueChanged(ObservableValueEvent<T> event) {
				ObservableValueEvent<T> wrapped = org.observe.util.ObservableUtils.wrap(event, this);
				theObservers.forEach(observer -> observer.onNext(wrapped));
			}

			void setElement(ObservableElement<T> element, ObservableValueEvent<T> cause) {
				if(theElement == element)
					return;
				ObservableValueEvent<T> event = createChangeEvent(theElement.get(), cause.getValue(), cause);
				theElement = element;
				theObservers.forEach(observer -> observer.onNext(event));
			}

			void remove(ObservableValueEvent<T> cause) {
				ObservableValueEvent<T> event = createChangeEvent(cause.getOldValue(), cause.getOldValue(), cause);
				theObservers.forEach(observer -> observer.onCompleted(event));
				theElement = null;
				theObservers.clear();
			}

			@Override
			public String toString() {
				return "unique(" + coll + "): " + get();
			}
		}
		class UniqueElementCollection extends java.util.ArrayList<ObservableElement<T>> {
			final UniqueFilteredElement theUniqueElement;

			UniqueElementCollection(ObservableElement<T> element) {
				theUniqueElement = new UniqueFilteredElement(element);
			}
		}
		class UniqueSet implements PartialSetImpl<T> {
			@Override
			public ObservableValue<CollectionSession> getSession() {
				return coll.getSession();
			}

			@Override
			public Transaction lock(boolean write, Object cause) {
				return coll.lock(write, cause);
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
			public Subscription onElement(Consumer<? super ObservableElement<T>> observer) {
				ObservableElement<T> [] newElement = new ObservableElement[1];
				MultiMap<T, ObservableElement<T>> elements = new org.observe.datastruct.impl.HashMultiMap<>(false,
					() ->{
						return d().debug(new UniqueElementCollection(newElement[0])).from("element", this).get();
					});

				return coll.onElement(element -> {
					element.subscribe(new Observer<ObservableValueEvent<T>>() {
						@Override
						public <V2 extends ObservableValueEvent<T>> void onNext(V2 elValue) {
							if(elValue.isInitial()){
								addFor(elValue);
							} else if(!Objects.equals(elValue.getOldValue(), elValue.getValue())) {
								removeFor(elValue, elValue.getOldValue());
								addFor(elValue);
							} else if(elValue.getOldValue() != elValue.getValue()) {
								UniqueElementCollection elColl = (UniqueElementCollection) elements.get(elValue.getValue());
								if(!elColl.isEmpty() && elColl.get(0) == element)
									elColl.theUniqueElement.valueChanged(elValue);
							}
						}

						@Override
						public <V2 extends ObservableValueEvent<T>> void onCompleted(V2 elValue) {
							removeFor(elValue, elValue.getValue());
						}

						private void addFor(ObservableValueEvent<T> elValue) {
							newElement[0] = element;
							elements.add(elValue.getValue(), element);
							newElement[0] = null;
							UniqueElementCollection elColl = (UniqueElementCollection) elements.get(elValue.getValue());
							if(elColl.size() == 1)
								observer.accept(elColl.theUniqueElement);
							else {
								if(coll instanceof ObservableOrderedCollection)
									sortOrdered(elColl);
								elColl.theUniqueElement.setElement(elColl.get(0), elValue);
							}
						}

						private void removeFor(ObservableValueEvent<T> elValue, T oldValue) {
							UniqueElementCollection elColl = (UniqueElementCollection) elements.get(elValue.getOldValue());
							elements.remove(elValue.getOldValue(), element);
							if(elColl.isEmpty()) {
								elColl.theUniqueElement.remove(elValue);
							} else {
								if(coll instanceof ObservableOrderedCollection)
									sortOrdered(elColl);
								elColl.theUniqueElement.setElement(elColl.get(0), elValue);
							}
						}

						private void sortOrdered(List<? extends ObservableElement<?>> elementList) {
							java.util.Collections.sort(elementList, (el1, el2) -> ((ObservableOrderedElement<?>) el1).getIndex()
								- ((ObservableOrderedElement<?>) el2).getIndex());
						}
					});
				});
			}

			@Override
			public String toString() {
				return ObservableSet.toString(this);
			}
		}
		return d().debug(new UniqueSet()).from("unique", coll).get();
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
	 * Implements {@link ObservableSet#equivalent(Object)}
	 *
	 * @param <E> The type of the set to find the value in
	 */
	class ObservableSetEquivalentFinder<E> implements ObservableValue<E> {
		private final ObservableSet<E> theSet;

		private final Object theKey;

		protected ObservableSetEquivalentFinder(ObservableSet<E> set, Object key) {
			theSet = set;
			theKey = key;
		}

		@Override
		public Type getType() {
			return theSet.getType();
		}

		@Override
		public E get() {
			for(E value : theSet) {
				if(Objects.equals(value, theKey))
					return value;
			}
			return null;
		}

		@Override
		public Subscription subscribe(Observer<? super ObservableValueEvent<E>> observer) {
			boolean [] isMatch = new boolean[1];
			boolean [] initialized = new boolean[1];
			Subscription ret = theSet.onElement(new Consumer<ObservableElement<E>>() {
				private E theCurrentMatch;

				@Override
				public void accept(ObservableElement<E> element) {
					element.subscribe(new Observer<ObservableValueEvent<E>>() {
						@Override
						public <V extends ObservableValueEvent<E>> void onNext(V event) {
							isMatch[0] = Objects.equals(event.getValue(), theKey);
							if(isMatch[0]) {
								E old = theCurrentMatch;
								theCurrentMatch = event.getValue();
								if(initialized[0])
									observer.onNext(createInitialEvent(event.getValue()));
								else
									observer.onNext(createChangeEvent(old, event.getValue(), event));
							}
						}

						@Override
						public <V extends ObservableValueEvent<E>> void onCompleted(V event) {
							if(isMatch[0] && theCurrentMatch == event.getValue()) {
								theCurrentMatch = null;
								observer.onNext(createChangeEvent(event.getValue(), null, event));
							}
						}
					});
				}
			});
			if(!isMatch[0])
				observer.onNext(createInitialEvent(null));
			return ret;
		}
	}

	/**
	 * Implements {@link ObservableSet#filter(Predicate)} and {@link ObservableSet#filter(Class)}
	 *
	 * @param <E> The type of the set to filter
	 * @param <T> the type of the mapped set
	 */
	class StaticFilteredSet<E, T> extends StaticFilteredCollection<E, T> implements PartialSetImpl<T> {
		protected StaticFilteredSet(ObservableSet<E> wrap, Type type, Function<? super E, T> map) {
			super(wrap, type, map, value -> (E) value);
		}

		@Override
		protected ObservableSet<E> getWrapped() {
			return (ObservableSet<E>) super.getWrapped();
		}
	}

	/**
	 * Implements {@link ObservableSet#filter(Predicate)} and {@link ObservableSet#filter(Class)}
	 *
	 * @param <E> The type of the set to filter
	 * @param <T> the type of the mapped set
	 */
	class DynamicFilteredSet<E, T> extends DynamicFilteredCollection<E, T> implements PartialSetImpl<T> {
		protected DynamicFilteredSet(ObservableSet<E> wrap, Type type, Function<? super E, T> map) {
			super(wrap, type, map, value -> (E) value);
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
	 * Implements {@link ObservableSet#filterModification(Predicate, Predicate)}
	 *
	 * @param <E> The type of elements in the set
	 */
	class ModFilteredSet<E> extends ModFilteredCollection<E> implements PartialSetImpl<E> {
		public ModFilteredSet(ObservableSet<E> wrapped, Predicate<? super E> removeFilter, Predicate<? super E> addFilter) {
			super(wrapped, removeFilter, addFilter);
		}

		@Override
		protected ObservableSet<E> getWrapped() {
			return (ObservableSet<E>) super.getWrapped();
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
