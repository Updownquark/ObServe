package org.observe.collect;

import static org.observe.ObservableDebug.d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.observe.DefaultObservable;
import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.SimpleObservable;
import org.observe.Subscription;
import org.observe.util.ObservableUtils;
import org.qommons.IterableUtils;
import org.qommons.ReversibleCollection;
import org.qommons.Transaction;

import com.google.common.reflect.TypeToken;

/**
 * An observable ordered collection that can be reversed
 *
 * @param <E> The type of elements in the collection
 */
public interface ObservableReversibleCollection<E> extends ObservableOrderedCollection<E>, ReversibleCollection<E> {
	/**
	 * Identical to {@link #onOrderedElement(Consumer)}, except that initial elements are given to the consumer in reverse.
	 *
	 * Although a default implementation is provided, subclasses should override this method for performance where possible
	 *
	 * @param onElement The element accepter
	 * @return The unsubscribe runnable
	 */
	Subscription onElementReverse(Consumer<? super ObservableOrderedElement<E>> onElement);

	/**
	 * A default implementation of {@link #onElementReverse(Consumer)} that simply obtains all the elements and feeds them to the consumer
	 * in reverse. This implementation should only be used where a more performant implementation is not possible.
	 *
	 * @param coll The collection to get the reverse elements of
	 * @param onElement The consumer for the elements
	 * @return The subscription for the elements
	 */
	static <E> Subscription defaultOnElementReverse(ObservableReversibleCollection<E> coll,
		Consumer<? super ObservableOrderedElement<E>> onElement) {
		List<ObservableOrderedElement<E>> initElements = new ArrayList<>();
		boolean [] initialized = new boolean[1];
		Subscription ret = coll.onOrderedElement(element -> {
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

	/** @return A collection that is identical to this one, but with its elements reversed */
	@Override
	default ObservableReversibleCollection<E> reverse() {
		return new ObservableReversedCollection<>(this);
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
	default E last() {
		try (Transaction t = lock(false, null)) {
			Iterator<E> iter = descending().iterator();
			return iter.hasNext() ? iter.next() : null;
		}
	}

	@Override
	default ObservableReversibleCollection<E> safe() {
		if (isSafe())
			return this;
		else
			return d().debug(new SafeReversibleCollection<>(this)).from("safe", this).get();
	}

	@Override
	default <T> ObservableReversibleCollection<T> map(Function<? super E, T> map) {
		return (ObservableReversibleCollection<T>) ObservableOrderedCollection.super.map(map);
	}

	@Override
	default <T> ObservableReversibleCollection<T> map(TypeToken<T> type, Function<? super E, T> map) {
		return (ObservableReversibleCollection<T>) ObservableOrderedCollection.super.map(type, map);
	}

	@Override
	default <T> ObservableReversibleCollection<T> map(TypeToken<T> type, Function<? super E, T> map, Function<? super T, E> reverse) {
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
	default <T> ObservableReversibleCollection<T> filterMap(TypeToken<T> type, Function<? super E, T> map, boolean staticFilter) {
		return (ObservableReversibleCollection<T>) ObservableOrderedCollection.super.filterMap(type, map, staticFilter);
	}

	@Override
	default <T> ObservableReversibleCollection<T> filterMap(TypeToken<T> type, Function<? super E, T> map, Function<? super T, E> reverse,
		boolean staticFilter) {
		return (ObservableReversibleCollection<T>) ObservableOrderedCollection.super.filterMap(type, map, reverse, staticFilter);
	}

	@Override
	default <T> ObservableReversibleCollection<T> filterMap2(TypeToken<T> type, Function<? super E, FilterMapResult<T>> map,
		Function<? super T, E> reverse, boolean staticFilter) {
		if(staticFilter)
			return d().debug(new StaticFilteredReversibleCollection<>(this, type, map, reverse)).from("filterMap", this).using("map", map)
				.using("reverse", reverse).get();
		else
			return d().debug(new DynamicFilteredReversibleCollection<>(this, type, map, reverse)).from("filterMap", this).using("map", map)
				.using("reverse", reverse).get();
	}

	@Override
	default <T, V> ObservableReversibleCollection<V> combine(ObservableValue<T> arg, BiFunction<? super E, ? super T, V> func) {
		return combine(arg, (TypeToken<V>) TypeToken.of(func.getClass()).resolveType(BiFunction.class.getTypeParameters()[2]), func);
	}

	@Override
	default <T, V> ObservableReversibleCollection<V> combine(ObservableValue<T> arg, TypeToken<V> type,
		BiFunction<? super E, ? super T, V> func) {
		return combine(arg, type, func, null);
	}

	@Override
	default <T, V> ObservableReversibleCollection<V> combine(ObservableValue<T> arg, TypeToken<V> type,
		BiFunction<? super E, ? super T, V> func,
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

	@Override
	default ObservableOrderedCollection<E> takeUntil(Observable<?> until) {
		return d().debug(new TakenUntilReversibleCollection<>(this, until, true)).from("taken", this).from("until", until)
			.tag("terminate", true).get();
	}

	@Override
	default ObservableOrderedCollection<E> unsubscribeOn(Observable<?> until) {
		return d().debug(new TakenUntilReversibleCollection<>(this, until, false)).from("taken", this).from("until", until)
			.tag("terminate", false).get();
	}

	/**
	 * Turns a collection of observable values into a collection composed of those holders' values
	 *
	 * @param <E> The type of elements held in the values
	 * @param collection The collection to flatten
	 * @return The flattened collection
	 */
	public static <E> ObservableReversibleCollection<E> flattenValues(
		ObservableReversibleCollection<? extends ObservableValue<? extends E>> collection) {
		return d().debug(new FlattenedReversibleValuesCollection<E>(collection)).from("flatten", collection).get();
	}

	/**
	 * Turns an observable value containing an observable collection into the contents of the value
	 *
	 * @param collectionObservable The observable value
	 * @return A collection representing the contents of the value, or a zero-length collection when null
	 */
	public static <E> ObservableReversibleCollection<E> flattenValue(
		ObservableValue<? extends ObservableReversibleCollection<E>> collectionObservable) {
		return d().debug(new FlattenedReversibleValueCollection<>(collectionObservable)).from("flatten", collectionObservable).get();
	}

	/**
	 * Flattens a collection of ordered collections
	 *
	 * @param <E> The super-type of all collections in the wrapping collection
	 * @param list The collection to flatten
	 * @return A collection containing all elements of all collections in the outer collection
	 */
	public static <E> ObservableReversibleCollection<E> flatten(
		ObservableReversibleCollection<? extends ObservableReversibleCollection<E>> list) {
		return d().debug(new FlattenedReversibleCollection<>(list)).from("flatten", list).get();
	}

	/**
	 * Implements {@link ObservableReversibleCollection#reverse()}
	 *
	 * @param <E> The type of elements in the collection
	 */
	class ObservableReversedCollection<E> implements PartialCollectionImpl<E>, ObservableReversibleCollection<E> {
		private final ObservableReversibleCollection<E> theWrapped;

		protected ObservableReversedCollection(ObservableReversibleCollection<E> wrap) {
			theWrapped = wrap;
		}

		protected ObservableReversibleCollection<E> getWrapped() {
			return theWrapped;
		}

		@Override
		public TypeToken<E> getType() {
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
		public boolean isSafe() {
			return theWrapped.isSafe();
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
		public boolean canRemove(Object value) {
			return theWrapped.canRemove(value);
		}

		@Override
		public boolean canAdd(E value) {
			return theWrapped.canAdd(value);
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
			public TypeToken<E> getType() {
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
			public boolean isSafe() {
				return theWrappedElement.isSafe();
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
	 * Implements {@link ObservableReversibleCollection#safe()}
	 *
	 * @param <E> The type of elements in the collection
	 */
	class SafeReversibleCollection<E> extends SafeOrderedCollection<E> implements ObservableReversibleCollection<E> {
		public SafeReversibleCollection(ObservableReversibleCollection<E> wrap) {
			super(wrap);
		}

		@Override
		protected ObservableReversibleCollection<E> getWrapped() {
			return (ObservableReversibleCollection<E>) super.getWrapped();
		}

		@Override
		public Iterable<E> descending() {
			return getWrapped().descending();
		}

		@Override
		public Subscription onElementReverse(Consumer<? super ObservableOrderedElement<E>> onElement) {
			return getWrapped().onElementReverse(element -> onElement.accept(wrapElement(element)));
		}
	}

	/**
	 * Implements {@link ObservableReversibleCollection#map(Function)}
	 *
	 * @param <E> The type of the collection to map
	 * @param <T> The type of the mapped collection
	 */
	class MappedReversibleCollection<E, T> extends MappedOrderedCollection<E, T> implements ObservableReversibleCollection<T> {
		protected MappedReversibleCollection(ObservableReversibleCollection<E> wrap, TypeToken<T> type, Function<? super E, T> map,
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
		public StaticFilteredReversibleCollection(ObservableReversibleCollection<E> wrap, TypeToken<T> type,
			Function<? super E, FilterMapResult<T>> map,
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

		@Override
		public Subscription onElementReverse(Consumer<? super ObservableOrderedElement<T>> observer) {
			return getWrapped().onElementReverse(element -> {
				if (getMap().apply(element.get()) != null)
					observer.accept(element.mapV(value -> getMap().apply(value).mapped));
			});
		}
	}

	/**
	 * Implements {@link ObservableOrderedCollection#filterMap(Function)}
	 *
	 * @param <E> The type of the collection to filter/map
	 * @param <T> The type of the filter/mapped collection
	 */
	class DynamicFilteredReversibleCollection<E, T> extends DynamicFilteredOrderedCollection<E, T> implements ObservableReversibleCollection<T> {
		public DynamicFilteredReversibleCollection(ObservableReversibleCollection<E> wrap, TypeToken<T> type,
			Function<? super E, FilterMapResult<T>> map,
				Function<? super T, E> reverse) {
			super(wrap, type, map, reverse);
		}

		@Override
		protected ObservableReversibleCollection<E> getWrapped() {
			return (ObservableReversibleCollection<E>) super.getWrapped();
		}

		@Override
		public Subscription onElementReverse(Consumer<? super ObservableOrderedElement<T>> onElement) {
			List<Object> filteredElements = new ArrayList<>();
			SimpleObservable<Void> unSubObs = new SimpleObservable<>();
			Subscription collSub = getWrapped().onElementReverse(element -> {
				DynamicFilteredOrderedElement<E, T> retElement = filter(element, filteredElements);
				element.unsubscribeOn(unSubObs).act(elValue -> {
					if(!retElement.isIncluded()) {
						FilterMapResult<T> mapped = getMap().apply(elValue.getValue());
						if (mapped.passed)
							onElement.accept(retElement);
					}
				});
			});
			return () -> {
				collSub.unsubscribe();
				unSubObs.onNext(null);
			};
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
		public CombinedReversibleCollection(ObservableReversibleCollection<E> collection, ObservableValue<T> value, TypeToken<V> type,
			BiFunction<? super E, ? super T, V> map, BiFunction<? super V, ? super T, E> reverse) {
			super(collection, value, type, map, reverse);
		}

		@Override
		protected ObservableReversibleCollection<E> getWrapped() {
			return (ObservableReversibleCollection<E>) super.getWrapped();
		}

		@Override
		public Subscription onElementReverse(Consumer<? super ObservableOrderedElement<V>> onElement) {
			return getManager().onElement(getWrapped(),
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
			return getManager().onElement(getWrapped(),
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
			DefaultObservable<Void> unSubObs = new DefaultObservable<>();
			Observer<Void> unSubControl = unSubObs.control(null);
			Subscription collSub = getWrapped()
				.onElementReverse(element -> onElement.accept(element.refreshForValue(getRefresh(), unSubObs)));
			return () -> {
				unSubControl.onCompleted(null);
				collSub.unsubscribe();
			};
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
			return IterableUtils.immutableIterable(getWrapped().descending());
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

		@Override
		public Subscription onElementReverse(Consumer<? super ObservableOrderedElement<E>> onElement) {
			return getWrapped().onElementReverse(onElement);
		}

		@Override
		public String toString() {
			StringBuilder ret = new StringBuilder("[");
			boolean first = true;
			try (Transaction t = lock(false, null)) {
				for (Object value : this) {
					if (!first) {
						ret.append(", ");
					} else
						first = false;
					ret.append(value);
				}
			}
			ret.append(']');
			return ret.toString();
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

	/**
	 * Backs {@link ObservableReversibleCollection#takeUntil(Observable)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	class TakenUntilReversibleCollection<E> extends TakenUntilOrderedCollection<E> implements ObservableReversibleCollection<E> {
		public TakenUntilReversibleCollection(ObservableReversibleCollection<E> wrap, Observable<?> until, boolean terminate) {
			super(wrap, until, terminate);
		}

		@Override
		protected ObservableReversibleCollection<E> getWrapped() {
			return (ObservableReversibleCollection<E>) super.getWrapped();
		}

		@Override
		public Iterable<E> descending() {
			return getWrapped().descending();
		}

		@Override
		public Subscription onElementReverse(Consumer<? super ObservableOrderedElement<E>> onElement) {
			List<TakenUntilOrderedElement<E>> elements = new ArrayList<>();
			Subscription[] collSub = new Subscription[] { getWrapped().onElementReverse(element -> {
				TakenUntilOrderedElement<E> untilEl = new TakenUntilOrderedElement<>(element, isTerminating());
				elements.add(element.getIndex(), untilEl);
				onElement.accept(untilEl);
			}) };
			Subscription untilSub = getUntil().act(v -> {
				if (collSub[0] != null)
					collSub[0].unsubscribe();
				collSub[0] = null;
				for (int i = elements.size() - 1; i >= 0; i--)
					elements.get(i).end();
				elements.clear();
			});
			return () -> {
				if (collSub[0] != null)
					collSub[0].unsubscribe();
				collSub[0] = null;
				untilSub.unsubscribe();
			};
		}
	}

	/**
	 * Implements {@link ObservableReversibleCollection#flattenValues(ObservableReversibleCollection)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	class FlattenedReversibleValuesCollection<E> extends FlattenedOrderedValuesCollection<E> implements ObservableReversibleCollection<E> {
		protected FlattenedReversibleValuesCollection(ObservableOrderedCollection<? extends ObservableValue<? extends E>> collection) {
			super(collection);
		}

		@Override
		protected ObservableReversibleCollection<? extends ObservableValue<? extends E>> getWrapped() {
			return (ObservableReversibleCollection<? extends ObservableValue<? extends E>>) super.getWrapped();
		}

		@Override
		public Iterable<E> descending() {
			return IterableUtils.map(getWrapped().descending(), v -> v == null ? null : v.get());
		}

		@Override
		public Subscription onElementReverse(Consumer<? super ObservableOrderedElement<E>> onElement) {
			return getWrapped().onElementReverse(element -> onElement.accept(createFlattenedElement(element)));
		}
	}

	/**
	 * Implements {@link ObservableReversibleCollection#flattenValue(ObservableValue)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	class FlattenedReversibleValueCollection<E> extends FlattenedOrderedValueCollection<E> implements ObservableReversibleCollection<E> {
		public FlattenedReversibleValueCollection(ObservableValue<? extends ObservableReversibleCollection<E>> collectionObservable) {
			super(collectionObservable);
		}

		@Override
		protected ObservableValue<? extends ObservableReversibleCollection<E>> getWrapped() {
			return (ObservableValue<? extends ObservableReversibleCollection<E>>) super.getWrapped();
		}

		@Override
		public Iterable<E> descending() {
			ObservableReversibleCollection<? extends E> coll = getWrapped().get();
			if (coll == null)
				return Collections.EMPTY_LIST;
			else
				return (Iterable<E>) coll.descending();
		}

		@Override
		public Subscription onElementReverse(Consumer<? super ObservableOrderedElement<E>> onElement) {
			SimpleObservable<Void> unSubObs = new SimpleObservable<>();
			Subscription collSub = getWrapped()
				.subscribe(new Observer<ObservableValueEvent<? extends ObservableReversibleCollection<? extends E>>>() {
					@Override
					public <V extends ObservableValueEvent<? extends ObservableReversibleCollection<? extends E>>> void onNext(
						V event) {
						if (event.getValue() != null) {
							Observable<?> until = ObservableUtils.makeUntil(getWrapped(), event);
							((ObservableReversibleCollection<E>) event.getValue().takeUntil(until).unsubscribeOn(unSubObs))
							.onElementReverse(onElement);
						}
					}
				});
			return () -> {
				collSub.unsubscribe();
				unSubObs.onNext(null);
			};
		}
	}

	/**
	 * Implements {@link ObservableReversibleCollection#flatten(ObservableReversibleCollection)}
	 *
	 * @param <E> The type of the collection
	 */
	class FlattenedReversibleCollection<E> extends FlattenedOrderedCollection<E> implements ObservableReversibleCollection<E> {
		protected FlattenedReversibleCollection(
			ObservableReversibleCollection<? extends ObservableReversibleCollection<? extends E>> outer) {
			super(outer);
		}

		@Override
		protected ObservableReversibleCollection<? extends ObservableReversibleCollection<? extends E>> getOuter() {
			return (ObservableReversibleCollection<? extends ObservableReversibleCollection<? extends E>>) super.getOuter();
		}

		@Override
		public Iterable<E> descending() {
			return IterableUtils.flatten(
				IterableUtils.map(getOuter().descending(), rc -> rc == null ? (Iterable<E>) Collections.EMPTY_LIST : rc.descending()));
		}

		@Override
		public Subscription onElementReverse(Consumer<? super ObservableOrderedElement<E>> onElement) {
			return onElement(new ElementSubscriber() {
				@Override
				public <E2> Subscription onElement(ObservableOrderedCollection<E2> coll,
					Consumer<? super ObservableOrderedElement<E2>> onEl) {
					return ((ObservableReversibleCollection<E2>) coll).onElementReverse(onEl);
				}
			}, onElement);
		}
	}
}
