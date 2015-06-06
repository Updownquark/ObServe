package org.observe.collect;

import static org.observe.ObservableDebug.d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.Subscription;

import prisms.lang.Type;

/**
 * An observable ordered collection that can be reversed
 *
 * @param <E> The type of elements in the collection
 */
public interface ObservableReversibleCollection<E> extends ObservableOrderedCollection<E> {
	/**
	 * Identical to {@link #onOrderedElement(Consumer)}, except that initial elements are given to the consumer in reverse.
	 *
	 * Although a default implementation is provided, subclasses should override this method for performance where possible
	 *
	 * @param onElement The element accepter
	 * @return The unsubscribe runnable
	 */
	default Subscription onElementReverse(Consumer<? super OrderedObservableElement<E>> onElement) {
		List<OrderedObservableElement<E>> initElements = new ArrayList<>();
		boolean [] initialized = new boolean[1];
		Subscription ret = onOrderedElement(element -> {
			if(initialized[0])
				onElement.accept(element);
			else
				initElements.add(element);
		});
		OrderedObservableElement<E> [] e = initElements.toArray(new OrderedObservableElement[initElements.size()]);
		initElements.clear();
		initialized[0] = true;
		for(int i = e.length - 1; i >= 0; i--)
			onElement.accept(e[i]);
		return ret;
	}

	/** @return An iterable that iterates through this collection's values in reverse */
	Iterable<E> descending();

	/** @return A collection that is identical to this one, but with its elements reversed */
	default ObservableReversibleCollection<E> reverse() {
		return new ReversedCollection<>(this);
	}

	/* Overridden for performance.  get() is linear in the super, constant time here */
	@Override
	default ObservableValue<E> findLast(Predicate<E> filter) {
		return d().debug(new OrderedReversibleCollectionFinder<>(this, filter, false)).from("findLast", this).using("filter", filter).get();
	}

	/* Overridden for performance.  get() is linear in the super, constant time here */
	@Override
	default ObservableValue<E> last() {
		return d().debug(new OrderedReversibleCollectionFinder<>(this, value -> true, false)).from("last", this).get();
	}

	// @Override
	// default <T, V> ObservableOrderedCollection<V> combine(ObservableValue<T> arg, Type type, BiFunction<? super E, ? super T, V> func) {
	// return d().debug(new CombinedObservableOrderedCollection<>(this, arg, type, func)).from("combine", this).from("with", arg)
	// .using("combination", func).get();
	// }
	//
	// /**
	// * @param refresh The observable to re-fire events on
	// * @return A collection whose elements fire additional value events when the given observable fires
	// */
	// @Override
	// default ObservableOrderedCollection<E> refresh(Observable<?> refresh) {
	// ObservableOrderedCollection<E> outer = this;
	// class RefreshingCollection implements PartialCollectionImpl<E>, ObservableOrderedCollection<E> {
	// private final SubCollectionTransactionManager theTransactionManager = new SubCollectionTransactionManager(outer);
	//
	// @Override
	// public Type getType() {
	// return outer.getType();
	// }
	//
	// @Override
	// public ObservableValue<CollectionSession> getSession() {
	// return theTransactionManager.getSession();
	// }
	//
	// @Override
	// public Iterator<E> iterator() {
	// return outer.iterator();
	// }
	//
	// @Override
	// public int size() {
	// return outer.size();
	// }
	//
	// @Override
	// public Runnable onOrderedElement(Consumer<? super OrderedObservableElement<E>> observer) {
	// return theTransactionManager.onElement(outer, refresh,
	// element -> observer.accept((OrderedObservableElement<E>) element.refresh(refresh)));
	// }
	// };
	// return d().debug(new RefreshingCollection()).from("refresh", this).from("on", refresh).get();
	// }
	//
	// @Override
	// default <T> ObservableOrderedCollection<T> map(Function<? super E, T> map) {
	// return map(ComposedObservableValue.getReturnType(map), map);
	// }
	//
	// @Override
	// default <T> ObservableOrderedCollection<T> map(Type type, Function<? super E, T> map) {
	// ObservableOrderedCollection<E> outer = this;
	// class MappedObservableOrderedCollection implements PartialCollectionImpl<T>, ObservableOrderedCollection<T> {
	// @Override
	// public Type getType() {
	// return type;
	// }
	//
	// @Override
	// public ObservableValue<CollectionSession> getSession() {
	// return outer.getSession();
	// }
	//
	// @Override
	// public int size() {
	// return outer.size();
	// }
	//
	// @Override
	// public Iterator<T> iterator() {
	// return new Iterator<T>() {
	// private final Iterator<E> backing = outer.iterator();
	//
	// @Override
	// public boolean hasNext() {
	// return backing.hasNext();
	// }
	//
	// @Override
	// public T next() {
	// return map.apply(backing.next());
	// }
	//
	// @Override
	// public void remove() {
	// backing.remove();
	// }
	// };
	// }
	//
	// @Override
	// public Runnable onOrderedElement(Consumer<? super OrderedObservableElement<T>> observer) {
	// return outer.onOrderedElement(element -> observer.accept(element.mapV(map)));
	// }
	// }
	// return d().debug(new MappedObservableOrderedCollection()).from("map", this).using("map", map).get();
	// }
	//
	// @Override
	// default ObservableOrderedCollection<E> filter(Function<? super E, Boolean> filter) {
	// return label(filterMap(value -> {
	// return (value != null && filter.apply(value)) ? value : null;
	// })).label("filter").tag("filter", filter).get();
	// }
	//
	// @Override
	// default <T> ObservableOrderedCollection<T> filter(Class<T> type) {
	// return label(filterMap(value -> type.isInstance(value) ? type.cast(value) : null)).tag("filterType", type).get();
	// }
	//
	// @Override
	// default <T> ObservableOrderedCollection<T> filterMap(Function<? super E, T> filterMap) {
	// return filterMap(ComposedObservableValue.getReturnType(filterMap), filterMap);
	// }
	//
	// @Override
	// default <T> ObservableOrderedCollection<T> filterMap(Type type, Function<? super E, T> map) {
	// ObservableOrderedCollection<E> outer = this;
	// class FilteredOrderedCollection implements PartialCollectionImpl<T>, ObservableOrderedCollection<T> {
	// private List<FilteredOrderedElement<T, E>> theFilteredElements = new java.util.ArrayList<>();
	//
	// @Override
	// public Type getType() {
	// return type;
	// }
	//
	// @Override
	// public ObservableValue<CollectionSession> getSession() {
	// return outer.getSession();
	// }
	//
	// @Override
	// public int size() {
	// int ret = 0;
	// for(E el : outer)
	// if(map.apply(el) != null)
	// ret++;
	// return ret;
	// }
	//
	// @Override
	// public Iterator<T> iterator() {
	// return new Iterator<T>() {
	// private final Iterator<E> backing = outer.iterator();
	// private T nextVal;
	//
	// @Override
	// public boolean hasNext() {
	// while(nextVal == null && backing.hasNext()) {
	// nextVal = map.apply(backing.next());
	// }
	// return nextVal != null;
	// }
	//
	// @Override
	// public T next() {
	// if(nextVal == null && !hasNext())
	// throw new java.util.NoSuchElementException();
	// T ret = nextVal;
	// nextVal = null;
	// return ret;
	// }
	// };
	// }
	//
	// @Override
	// public Runnable onOrderedElement(Consumer<? super OrderedObservableElement<T>> observer) {
	// return outer.onElement(element -> {
	// OrderedObservableElement<E> outerElement = (OrderedObservableElement<E>) element;
	// FilteredOrderedElement<T, E> retElement = d().debug(
	// new FilteredOrderedElement<>(outerElement, map, type, theFilteredElements)).from("element", this)
	// .tag("wrapped", element).get();
	// theFilteredElements.add(outerElement.getIndex(), retElement);
	// outerElement.completed().act(elValue -> theFilteredElements.remove(outerElement.getIndex()));
	// outerElement.act(elValue -> {
	// if(!retElement.isIncluded()) {
	// T mapped = map.apply(elValue.getValue());
	// if(mapped != null)
	// observer.accept(retElement);
	// }
	// });
	// });
	// }
	// }
	// return d().debug(new FilteredOrderedCollection()).from("filterMap", this).using("map", map).get();
	// }
	//
	// @Override
	// default ObservableOrderedCollection<E> immutable() {
	// return d().debug(new ImmutableOrderedObservableCollection<>(this)).from("immutable", this).get();
	// }
	//
	// @Override
	// default ObservableOrderedCollection<E> cached(){
	// return d().debug(new SafeCachedOrderedObservableCollection<>(this)).from("cached", this).get();
	// }
	//
	/**
	 * Implements {@link ObservableReversibleCollection#reverse()}
	 *
	 * @param <E> The type of elements in the collection
	 */
	class ReversedCollection<E> implements PartialCollectionImpl<E>, ObservableReversibleCollection<E> {
		private final ObservableReversibleCollection<E> theWrapped;

		ReversedCollection(ObservableReversibleCollection<E> wrap) {
			theWrapped = wrap;
		}

		@Override
		public Type getType() {
			return theWrapped.getType();
		}

		@Override
		public Subscription onElement(Consumer<? super ObservableElement<E>> onElement) {
			return theWrapped.onElementReverse(onElement);
		}

		@Override
		public ObservableValue<CollectionSession> getSession() {
			return theWrapped.getSession();
		}

		@Override
		public int size() {
			return theWrapped.size();
		}

		@Override
		public Iterator<E> iterator() {
			return theWrapped.descending().iterator();
		}

		@Override
		public Iterable<E> descending() {
			return theWrapped;
		}

		@Override
		public Subscription onOrderedElement(Consumer<? super OrderedObservableElement<E>> onElement) {
			return theWrapped.onElementReverse(element -> {
				onElement.accept(new ReversedElement(element));
			});
		}

		@Override
		public Subscription onElementReverse(Consumer<? super OrderedObservableElement<E>> onElement) {
			return theWrapped.onOrderedElement(element -> {
				onElement.accept(new ReversedElement(element));
			});
		}

		class ReversedElement implements OrderedObservableElement<E> {
			private final OrderedObservableElement<E> theWrappedElement;

			ReversedElement(OrderedObservableElement<E> wrap) {
				theWrappedElement = wrap;
			}

			@Override
			public ObservableValue<E> persistent() {
				return theWrappedElement.persistent();
			}

			@Override
			public Type getType() {
				return theWrappedElement.getType();
			}

			@Override
			public E get() {
				return theWrappedElement.get();
			}

			@Override
			public Subscription subscribe(Observer<? super ObservableValueEvent<E>> observer) {
				return theWrappedElement.subscribe(observer);
			}

			@Override
			public int getIndex() {
				return theWrapped.size() - theWrappedElement.getIndex() - 1;
			}
		}
	}

	/**
	 * Finds something in an {@link ObservableOrderedCollection}. More performant for backward searching.
	 *
	 * @param <E> The type of value to find
	 */
	public class OrderedReversibleCollectionFinder<E> extends OrderedCollectionFinder<E> {
		OrderedReversibleCollectionFinder(ObservableReversibleCollection<E> collection, Predicate<? super E> filter, boolean forward) {
			super(collection, filter, forward);
		}

		/** @return The collection that this finder searches */
		@Override
		public ObservableReversibleCollection<E> getCollection() {
			return (ObservableReversibleCollection<E>) super.getCollection();
		}

		@Override
		public E get() {
			if(isForward()) {
				return super.get();
			} else {
				for(E element : getCollection().descending()) {
					if(getFilter().test(element))
						return element;
				}
				return null;
			}
		}
	}
}
