package org.observe.collect;

import static org.observe.ObservableDebug.d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.Subscription;
import org.observe.util.ObservableUtils;
import org.observe.util.Transaction;

import prisms.lang.Type;
import prisms.util.ArrayUtils;

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
	default Subscription onElementReverse(Consumer<? super ObservableOrderedElement<E>> onElement) {
		List<ObservableOrderedElement<E>> initElements = new ArrayList<>();
		boolean [] initialized = new boolean[1];
		Subscription ret = onOrderedElement(element -> {
			if(initialized[0])
				onElement.accept(element);
			else
				initElements.add(element);
		});
		ObservableOrderedElement<E> [] e = initElements.toArray(new ObservableOrderedElement[initElements.size()]);
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
	default ObservableValue<E> getLast() {
		return d().debug(new OrderedReversibleCollectionFinder<>(this, value -> true, false)).from("last", this).get();
	}

	@Override
	default int lastIndexOf(Object value) {
		try (Transaction t = lock(false, null)) {
			int size = size();
			Iterator<E> iter = descending().iterator();
			for(int i = 0; iter.hasNext(); i++) {
				if(Objects.equals(iter.next(), value))
					return size - i - 1;
			}
			return -1;
		}
	}

	@Override
	default <T> ObservableReversibleCollection<T> map(Function<? super E, T> map) {
		return (ObservableReversibleCollection<T>) ObservableOrderedCollection.super.map(map);
	}

	@Override
	default <T> ObservableReversibleCollection<T> map(Type type, Function<? super E, T> map) {
		return (ObservableReversibleCollection<T>) ObservableOrderedCollection.super.map(type, map);
	}

	@Override
	default <T> ObservableReversibleCollection<T> map(Type type, Function<? super E, T> map, Function<? super T, E> reverse) {
		return d().debug(new MappedReversibleCollection<>(this, type, map, reverse)).from("map", this).using("map", map)
			.using("reverse", reverse).get();
	}

	@Override
	default ObservableReversibleCollection<E> filter(Predicate<? super E> filter) {
		return (ObservableReversibleCollection<E>) ObservableOrderedCollection.super.filter(filter);
	}

	@Override
	default ObservableReversibleCollection<E> filter(Predicate<? super E> filter, boolean staticFilter) {
		return (ObservableReversibleCollection<E>) ObservableOrderedCollection.super.filter(filter, staticFilter);
	}

	@Override
	default ObservableReversibleCollection<E> filterDynamic(Predicate<? super E> filter) {
		return (ObservableReversibleCollection<E>) ObservableOrderedCollection.super.filterDynamic(filter);
	}

	@Override
	default ObservableReversibleCollection<E> filterStatic(Predicate<? super E> filter) {
		return (ObservableReversibleCollection<E>) ObservableOrderedCollection.super.filterStatic(filter);
	}

	@Override
	default <T> ObservableReversibleCollection<T> filter(Class<T> type) {
		return (ObservableReversibleCollection<T>) ObservableOrderedCollection.super.filter(type);
	}

	@Override
	default <T> ObservableReversibleCollection<T> filterMap(Function<? super E, T> filterMap) {
		return (ObservableReversibleCollection<T>) ObservableOrderedCollection.super.filterMap(filterMap);
	}

	@Override
	default <T> ObservableReversibleCollection<T> filterMap(Type type, Function<? super E, T> map, boolean staticFilter) {
		return (ObservableReversibleCollection<T>) ObservableOrderedCollection.super.filterMap(type, map, staticFilter);
	}

	@Override
	default <T> ObservableReversibleCollection<T> filterMap(Type type, Function<? super E, T> map, Function<? super T, E> reverse,
		boolean staticFilter) {
		if(staticFilter)
			return d().debug(new StaticFilteredReversibleCollection<>(this, type, map, reverse)).from("filterMap", this).using("map", map)
				.using("reverse", reverse).get();
		else
			return d().debug(new DynamicFilteredReversibleCollection<>(this, type, map, reverse)).from("filterMap", this).using("map", map)
				.using("reverse", reverse).get();
	}

	@Override
	default <T, V> ObservableReversibleCollection<V> combine(ObservableValue<T> arg, BiFunction<? super E, ? super T, V> func) {
		return combine(arg, ObservableUtils.getReturnType(func), func);
	}

	@Override
	default <T, V> ObservableReversibleCollection<V> combine(ObservableValue<T> arg, Type type, BiFunction<? super E, ? super T, V> func) {
		return combine(arg, type, func, null);
	}

	@Override
	default <T, V> ObservableReversibleCollection<V> combine(ObservableValue<T> arg, Type type, BiFunction<? super E, ? super T, V> func,
		BiFunction<? super V, ? super T, E> reverse) {
		return d().debug(new CombinedReversibleCollection<>(this, arg, type, func, reverse)).from("combine", this).from("with", arg)
			.using("combination", func).using("reverse", reverse).get();
	}

	/**
	 * @param refresh The observable to re-fire events on
	 * @return A collection whose elements fire additional value events when the given observable fires
	 */
	@Override
	default ObservableReversibleCollection<E> refresh(Observable<?> refresh) {
		return d().debug(new RefreshingReversibleCollection<>(this, refresh)).from("refresh", this).from("on", refresh).get();
	}

	@Override
	default ObservableReversibleCollection<E> refreshEach(Function<? super E, Observable<?>> refire) {
		return d().debug(new ElementRefreshingReversibleCollection<>(this, refire)).from("refreshEach", this).using("on", refire).get();
	}

	@Override
	default ObservableReversibleCollection<E> immutable() {
		return d().debug(new ImmutableReversibleCollection<>(this)).from("immutable", this).get();
	}

	@Override
	default ObservableReversibleCollection<E> filterRemove(Predicate<? super E> filter) {
		return (ObservableReversibleCollection<E>) ObservableOrderedCollection.super.filterRemove(filter);
	}

	@Override
	default ObservableReversibleCollection<E> noRemove() {
		return (ObservableReversibleCollection<E>) ObservableOrderedCollection.super.noRemove();
	}

	@Override
	default ObservableReversibleCollection<E> filterAdd(Predicate<? super E> filter) {
		return (ObservableReversibleCollection<E>) ObservableOrderedCollection.super.filterAdd(filter);
	}

	@Override
	default ObservableReversibleCollection<E> noAdd() {
		return (ObservableReversibleCollection<E>) ObservableOrderedCollection.super.noAdd();
	}

	@Override
	default ObservableReversibleCollection<E> filterModification(Predicate<? super E> removeFilter, Predicate<? super E> addFilter) {
		return new ModFilteredReversibleCollection<>(this, removeFilter, addFilter);
	}

	@Override
	default ObservableReversibleCollection<E> cached() {
		return d().debug(new SafeCachedReversibleCollection<>(this)).from("cached", this).get();
	}

	/**
	 * Implements {@link ObservableReversibleCollection#reverse()}
	 *
	 * @param <E> The type of elements in the collection
	 */
	class ReversedCollection<E> implements PartialCollectionImpl<E>, ObservableReversibleCollection<E> {
		private final ObservableReversibleCollection<E> theWrapped;

		protected ReversedCollection(ObservableReversibleCollection<E> wrap) {
			theWrapped = wrap;
		}

		protected ObservableReversibleCollection<E> getWrapped() {
			return theWrapped;
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
		public Transaction lock(boolean write, Object cause) {
			return theWrapped.lock(write, cause);
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
		public Subscription onOrderedElement(Consumer<? super ObservableOrderedElement<E>> onElement) {
			return theWrapped.onElementReverse(element -> {
				onElement.accept(new ReversedElement(element));
			});
		}

		@Override
		public Subscription onElementReverse(Consumer<? super ObservableOrderedElement<E>> onElement) {
			return theWrapped.onOrderedElement(element -> {
				onElement.accept(new ReversedElement(element));
			});
		}

		@Override
		public E get(int index) {
			try (Transaction t = theWrapped.lock(false, null)) {
				return theWrapped.get(theWrapped.size() - index - 1);
			}
		}

		@Override
		public int indexOf(Object o) {
			return theWrapped.lastIndexOf(o);
		}

		@Override
		public int lastIndexOf(Object o) {
			return theWrapped.indexOf(o);
		}

		class ReversedElement implements ObservableOrderedElement<E> {
			private final ObservableOrderedElement<E> theWrappedElement;

			ReversedElement(ObservableOrderedElement<E> wrap) {
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
	class OrderedReversibleCollectionFinder<E> extends OrderedCollectionFinder<E> {
		protected OrderedReversibleCollectionFinder(ObservableReversibleCollection<E> collection, Predicate<? super E> filter,
			boolean forward) {
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

	/**
	 * Implements {@link ObservableReversibleCollection#map(Function)}
	 *
	 * @param <E> The type of the collection to map
	 * @param <T> The type of the mapped collection
	 */
	class MappedReversibleCollection<E, T> extends MappedOrderedCollection<E, T> implements ObservableReversibleCollection<T> {
		protected MappedReversibleCollection(ObservableReversibleCollection<E> wrap, Type type, Function<? super E, T> map,
			Function<? super T, E> reverse) {
			super(wrap, type, map, reverse);
		}

		@Override
		protected ObservableReversibleCollection<E> getWrapped() {
			return (ObservableReversibleCollection<E>) super.getWrapped();
		}

		@Override
		public Subscription onElementReverse(Consumer<? super ObservableOrderedElement<T>> onElement) {
			return getWrapped().onElementReverse(element -> onElement.accept(element.mapV(getMap())));
		}

		@Override
		public Iterable<T> descending() {
			return new Iterable<T>() {
				@Override
				public Iterator<T> iterator() {
					return map(getWrapped().descending().iterator());
				}
			};
		}
	}

	/**
	 * Implements {@link ObservableOrderedCollection#filterMap(Function)}
	 *
	 * @param <E> The type of the collection to filter/map
	 * @param <T> The type of the filter/mapped collection
	 */
	class StaticFilteredReversibleCollection<E, T> extends StaticFilteredOrderedCollection<E, T> implements
	ObservableReversibleCollection<T> {
		public StaticFilteredReversibleCollection(ObservableReversibleCollection<E> wrap, Type type, Function<? super E, T> map,
			Function<? super T, E> reverse) {
			super(wrap, type, map, reverse);
		}

		@Override
		protected ObservableReversibleCollection<E> getWrapped() {
			return (ObservableReversibleCollection<E>) super.getWrapped();
		}

		@Override
		public Iterable<T> descending() {
			return new Iterable<T>() {
				@Override
				public Iterator<T> iterator() {
					return filter(getWrapped().descending().iterator());
				}
			};
		}
	}

	/**
	 * Implements {@link ObservableOrderedCollection#filterMap(Function)}
	 *
	 * @param <E> The type of the collection to filter/map
	 * @param <T> The type of the filter/mapped collection
	 */
	class DynamicFilteredReversibleCollection<E, T> extends DynamicFilteredOrderedCollection<E, T> implements ObservableReversibleCollection<T> {
		public DynamicFilteredReversibleCollection(ObservableReversibleCollection<E> wrap, Type type, Function<? super E, T> map,
			Function<? super T, E> reverse) {
			super(wrap, type, map, reverse);
		}

		@Override
		protected ObservableReversibleCollection<E> getWrapped() {
			return (ObservableReversibleCollection<E>) super.getWrapped();
		}

		@Override
		public Subscription onElementReverse(Consumer<? super ObservableOrderedElement<T>> onElement) {
			return getWrapped().onElementReverse(element -> {
				FilteredOrderedElement<E, T> retElement = filter(element);
				element.act(elValue -> {
					if(!retElement.isIncluded()) {
						T mapped = getMap().apply(elValue.getValue());
						if(mapped != null)
							onElement.accept(retElement);
					}
				});
			});
		}

		@Override
		public Iterable<T> descending() {
			return new Iterable<T>() {
				@Override
				public Iterator<T> iterator() {
					return filter(getWrapped().descending().iterator());
				}
			};
		}
	}

	/**
	 * Implements {@link ObservableOrderedCollection#combine(ObservableValue, BiFunction)}
	 *
	 * @param <E> The type of the collection to combine
	 * @param <T> The type of the argument value
	 * @param <V> The type of the combined collection
	 */
	class CombinedReversibleCollection<E, T, V> extends CombinedOrderedCollection<E, T, V> implements ObservableReversibleCollection<V> {
		public CombinedReversibleCollection(ObservableReversibleCollection<E> collection, ObservableValue<T> value, Type type,
			BiFunction<? super E, ? super T, V> map, BiFunction<? super V, ? super T, E> reverse) {
			super(collection, value, type, map, reverse);
		}

		@Override
		protected ObservableReversibleCollection<E> getWrapped() {
			return (ObservableReversibleCollection<E>) super.getWrapped();
		}

		@Override
		public Subscription onElementReverse(Consumer<? super ObservableOrderedElement<V>> onElement) {
			return getManager().onElement(getWrapped(), getValue(),
				element -> onElement.accept((ObservableOrderedElement<V>) element.combineV(getMap(), getValue())), false);
		}

		@Override
		public Iterable<V> descending() {
			return new Iterable<V>(){
				@Override
				public Iterator<V> iterator(){
					return combine(getWrapped().descending().iterator());
				}
			};
		}
	}

	/**
	 * Implements {@link ObservableOrderedCollection#refresh(Observable)}
	 *
	 * @param <E> The type of the collection
	 */
	class RefreshingReversibleCollection<E> extends RefreshingOrderedCollection<E> implements ObservableReversibleCollection<E> {
		public RefreshingReversibleCollection(ObservableReversibleCollection<E> wrap, Observable<?> refresh) {
			super(wrap, refresh);
		}

		@Override
		protected ObservableReversibleCollection<E> getWrapped() {
			return (ObservableReversibleCollection<E>) super.getWrapped();
		}

		@Override
		public Subscription onElementReverse(Consumer<? super ObservableOrderedElement<E>> onElement) {
			return getManager().onElement(getWrapped(), getRefresh(),
				element -> onElement.accept((ObservableOrderedElement<E>) element.refresh(getRefresh())), false);
		}

		@Override
		public Iterable<E> descending() {
			return getWrapped().descending();
		}
	}

	/**
	 * Implements {@link ObservableOrderedCollection#refreshEach(Function)}
	 *
	 * @param <E> The type of the collection
	 */
	class ElementRefreshingReversibleCollection<E> extends ElementRefreshingOrderedCollection<E> implements
	ObservableReversibleCollection<E> {
		public ElementRefreshingReversibleCollection(ObservableReversibleCollection<E> wrap, Function<? super E, Observable<?>> refresh) {
			super(wrap, refresh);
		}

		@Override
		protected ObservableReversibleCollection<E> getWrapped() {
			return (ObservableReversibleCollection<E>) super.getWrapped();
		}

		@Override
		public Subscription onElementReverse(Consumer<? super ObservableOrderedElement<E>> onElement) {
			return getWrapped().onElementReverse(element -> onElement.accept(element.refreshForValue(getRefresh())));
		}

		@Override
		public Iterable<E> descending() {
			return getWrapped().descending();
		}
	}

	/**
	 * Implements {@link ObservableOrderedCollection#immutable()}
	 *
	 * @param <E> The type of the collection
	 */
	class ImmutableReversibleCollection<E> extends ImmutableOrderedCollection<E> implements ObservableReversibleCollection<E> {
		public ImmutableReversibleCollection(ObservableReversibleCollection<E> wrap) {
			super(wrap);
		}

		@Override
		protected ObservableReversibleCollection<E> getWrapped() {
			return (ObservableReversibleCollection<E>) super.getWrapped();
		}

		@Override
		public Subscription onElementReverse(Consumer<? super ObservableOrderedElement<E>> onElement) {
			return getWrapped().onElementReverse(onElement);
		}

		@Override
		public Iterable<E> descending() {
			return ArrayUtils.immutableIterable(getWrapped().descending());
		}

		@Override
		public ImmutableReversibleCollection<E> immutable() {
			return this;
		}
	}

	/**
	 * Implements {@link ObservableReversibleCollection#filterModification(Predicate, Predicate)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	class ModFilteredReversibleCollection<E> extends ModFilteredOrderedCollection<E> implements ObservableReversibleCollection<E> {
		public ModFilteredReversibleCollection(ObservableReversibleCollection<E> wrapped, Predicate<? super E> removeFilter,
			Predicate<? super E> addFilter) {
			super(wrapped, removeFilter, addFilter);
		}

		@Override
		protected ObservableReversibleCollection<E> getWrapped() {
			return (ObservableReversibleCollection<E>) super.getWrapped();
		}

		@Override
		public Iterable<E> descending() {
			if(getRemoveFilter() == null)
				return getWrapped().descending();

			return () -> new Iterator<E>() {
				private final Iterator<E> backing = getWrapped().descending().iterator();

				private E theLast;

				@Override
				public boolean hasNext() {
					return backing.hasNext();
				}

				@Override
				public E next() {
					theLast = backing.next();
					return theLast;
				}

				@Override
				public void remove() {
					if(getRemoveFilter().test(theLast))
						backing.remove();
				}
			};
		}
	}

	/**
	 * Implements {@link ObservableOrderedCollection#cached()}
	 *
	 * @param <E> The type of the collection
	 */
	class SafeCachedReversibleCollection<E> extends SafeCachedOrderedCollection<E> implements ObservableReversibleCollection<E> {
		public SafeCachedReversibleCollection(ObservableReversibleCollection<E> wrap) {
			super(wrap);
		}

		@Override
		protected ObservableReversibleCollection<E> getWrapped() {
			return (ObservableReversibleCollection<E>) super.getWrapped();
		}

		@Override
		public Subscription onElementReverse(Consumer<? super ObservableOrderedElement<E>> onElement) {
			Subscription ret = addListener(element -> onElement.accept((ObservableOrderedElement<E>) element));
			List<OrderedCachedElement<E>> cache = cachedElements();
			for(int i = cache.size() - 1; i >= 0; i--)
				onElement.accept(cache.get(i).cached());
			return ret;
		}

		@Override
		public Iterable<E> descending() {
			return new Iterable<E>() {
				@Override
				public Iterator<E> iterator() {
					List<E> ret = refresh();
					return new Iterator<E>() {
						private final java.util.ListIterator<E> backing = ret.listIterator(ret.size());

						private E theLastRet;

						@Override
						public boolean hasNext() {
							return backing.hasPrevious();
						}

						@Override
						public E next() {
							return theLastRet = backing.previous();
						}

						@Override
						public void remove() {
							backing.remove();
							getWrapped().remove(theLastRet);
						}
					};
				}
			};
		}

		@Override
		public ObservableReversibleCollection<E> cached() {
			return this;
		}
	}
}
