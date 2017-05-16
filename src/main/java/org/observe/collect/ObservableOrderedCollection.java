package org.observe.collect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.SimpleObservable;
import org.observe.Subscription;
import org.observe.collect.ObservableCollectionImpl.ElementRefreshingCollection;
import org.observe.collect.ObservableCollectionImpl.FlattenedObservableCollection;
import org.observe.collect.ObservableCollectionImpl.FlattenedValueCollection;
import org.observe.collect.ObservableCollectionImpl.FlattenedValuesCollection;
import org.observe.collect.ObservableCollectionImpl.ModFilteredCollection;
import org.observe.collect.ObservableCollectionImpl.RefreshingCollection;
import org.observe.collect.ObservableCollectionImpl.TakenUntilObservableCollection;
import org.observe.collect.ObservableOrderedCollection.TakenUntilOrderedElement;
import org.observe.collect.ObservableOrderedCollectionImpl.PositionObservable;
import org.observe.util.ObservableUtils;
import org.qommons.Transaction;
import org.qommons.tree.CountedRedBlackNode;
import org.qommons.tree.CountedRedBlackNode.DefaultNode;
import org.qommons.tree.CountedRedBlackNode.DefaultTreeSet;

import com.google.common.reflect.TypeToken;

/**
 * An ordered collection whose content can be observed. All {@link ObservableElement}s returned by this observable will be instances of
 * {@link ObservableOrderedElement}. In addition, it is guaranteed that the {@link ObservableOrderedElement#getIndex() index} of an element
 * given to the observer passed to {@link #onElement(Consumer)} will be less than or equal to the number of uncompleted elements previously
 * passed to the observer. This means that, for example, the first element passed to an observer will always be index 0. The second may be 0
 * or 1. If one of these is then completed, the next element may be 0 or 1 as well.
 *
 * @param <E> The type of element in the collection
 */
public interface ObservableOrderedCollection<E> extends ObservableCollection<E> {
	/**
	 * @param onElement The listener to be notified when new elements are added to the collection
	 * @return The function to call when the calling code is no longer interested in this collection
	 */
	CollectionSubscription subscribeOrdered(Consumer<? super OrderedCollectionEvent<? extends E>> observer);

	@Override
	default CollectionSubscription subscribe(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
		return subscribeOrdered(observer);
	}

	/**
	 * @return An observable that returns null whenever any elements in this collection are added, removed or changed. The order of events as
	 *         reported by this observable may not be the same as their occurrence in the collection. Any discrepancy will be resolved when
	 *         the transaction ends.
	 */
	@Override
	default Observable<? extends OrderedCollectionChangeEvent<E>> changes() {
		return new ObservableOrderedCollectionImpl.OrderedCollectionChangesObservable<>(this);
	}

	// Ordered collections need to know the indexes of their elements in a somewhat efficient way, so these index methods make sense here

	/**
	 * @param index The index of the element to get
	 * @return The element of this collection at the given index
	 */
	default E get(int index) {
		try (Transaction t = lock(false, null)) {
			if(index < 0 || index >= size())
				throw new IndexOutOfBoundsException(index + " of " + size());
			Iterator<E> iter = iterator();
			for(int i = 0; i < index; i++)
				iter.next();
			return iter.next();
		}
	}

	/**
	 * @param value The value to get the index of in this collection
	 * @return The index of the first position in this collection occupied by the given value, or &lt; 0 if the element does not exist in
	 *         this collection
	 */
	default int indexOf(Object value) {
		try (Transaction t = lock(false, null)) {
			Iterator<E> iter = iterator();
			for(int i = 0; iter.hasNext(); i++) {
				if(Objects.equals(iter.next(), value))
					return i;
			}
			return -1;
		}
	}

	/**
	 * @param value The value to get the index of in this collection
	 * @return The index of the last position in this collection occupied by the given value, or &lt; 0 if the element does not exist in
	 *         this collection
	 */
	default int lastIndexOf(Object value) {
		try (Transaction t = lock(false, null)) {
			int ret = -1;
			Iterator<E> iter = iterator();
			for(int i = 0; iter.hasNext(); i++) {
				if(Objects.equals(iter.next(), value))
					ret = i;
			}
			return ret;
		}
	}

	/**
	 * @param index The index to observe the value of
	 * @param defValueGen The function to generate the value for the observable if this collection's size is {@code &lt;=index}. The
	 *        argument is the current size. This function may throw a runtime exception, such as {@link IndexOutOfBoundsException}. Null is
	 *        acceptable here, which will mean a null default value.
	 * @return The observable value at the given position in the collection
	 */
	default ObservableValue<E> observeAt(int index, Function<Integer, E> defValueGen) {
		return new PositionObservable<>(this, index, defValueGen);
	}

	/** @return The last value in this collection, or null if the collection is empty */
	default E last() {
		E lastValue = null;
		try (Transaction t = lock(false, null)) {
			Iterator<E> iter = iterator();
			while (iter.hasNext()) {
				lastValue = iter.next();
			}
		}
		return lastValue;
	}

	@Override
	default ObservableOrderedCollection<E> withEquivalence(Equivalence<? super E> otherEquiv) {
		return new ObservableOrderedCollectionImpl.EquivalenceSwitchedOrderedCollection<>(this, otherEquiv);
	}

	@Override
	default ObservableOrderedCollection<E> filter(Function<? super E, String> filter) {
		return (ObservableOrderedCollection<E>) ObservableCollection.super.filter(filter);
	}

	@Override
	default <T> ObservableOrderedCollection<T> filter(Class<T> type) {
		return (ObservableOrderedCollection<T>) ObservableCollection.super.filter(type);
	}

	@Override
	default <T> ObservableOrderedCollection<T> map(Function<? super E, T> map) {
		return (ObservableOrderedCollection<T>) ObservableCollection.super.map(map);
	}

	@Override
	default <T> MappedCollectionBuilder<E, E, T> buildMap(TypeToken<T> type) {
		// TODO Auto-generated method stub
	}

	@Override
	default <T> ObservableOrderedCollection<T> filterMap(FilterMapDef<E, ?, T> filterMap) {
		// TODO Auto-generated method stub
	}

	@Override
	default <T, V> CombinedCollectionBuilder2<E, T, V> combineWith(ObservableValue<T> arg, TypeToken<V> targetType) {
		// TODO Auto-generated method stub
	}

	@Override
	default <V> ObservableOrderedCollection<V> combine(CombinedCollectionDef<E, V> combination) {
		// TODO Auto-generated method stub
	}

	@Override
	default ModFilterBuilder<E> filterModification() {
		// TODO Auto-generated method stub
		return ObservableCollection.super.filterModification();
	}

	@Override
	default ObservableOrderedCollection<E> filterModification(ModFilterDef<E> filter) {
		// TODO Auto-generated method stub
	}

	@Override
	default ObservableOrderedCollection<E> cached(Observable<?> until) {
		// TODO Auto-generated method stub
	}

	/**
	 * @param refresh The observable to re-fire events on
	 * @return A collection whose elements fire additional value events when the given observable fires
	 */
	@Override
	default ObservableOrderedCollection<E> refresh(Observable<?> refresh) {
		return new RefreshingOrderedCollection<>(this, refresh);
	}

	@Override
	default ObservableOrderedCollection<E> refreshEach(Function<? super E, Observable<?>> refire) {
		return new ElementRefreshingOrderedCollection<>(this, refire);
	}

	@Override
	default ObservableOrderedCollection<E> takeUntil(Observable<?> until) {
		return new TakenUntilOrderedCollection<>(this, until, true);
	}

	@Override
	default ObservableOrderedCollection<E> unsubscribeOn(Observable<?> until) {
		return new TakenUntilOrderedCollection<>(this, until, false);
	}

	/**
	 * Turns a collection of observable values into a collection composed of those holders' values
	 *
	 * @param <E> The type of elements held in the values
	 * @param collection The collection to flatten
	 * @return The flattened collection
	 */
	public static <E> ObservableOrderedCollection<E> flattenValues(
		ObservableOrderedCollection<? extends ObservableValue<? extends E>> collection) {
		return new FlattenedOrderedValuesCollection<E>(collection);
	}

	/**
	 * Turns an observable value containing an observable collection into the contents of the value
	 *
	 * @param collectionObservable The observable value
	 * @return A collection representing the contents of the value, or a zero-length collection when null
	 */
	public static <E> ObservableOrderedCollection<E> flattenValue(
		ObservableValue<? extends ObservableOrderedCollection<E>> collectionObservable) {
		return new FlattenedOrderedValueCollection<>(collectionObservable);
	}

	/**
	 * Flattens a collection of ordered collections
	 *
	 * @param <E> The super-type of all collections in the wrapping collection
	 * @param list The collection to flatten
	 * @return A collection containing all elements of all collections in the outer collection
	 */
	public static <E> ObservableOrderedCollection<E> flatten(ObservableOrderedCollection<? extends ObservableOrderedCollection<E>> list) {
		return new FlattenedOrderedCollection<>(list);
	}

	/**
	 * Implements {@link ObservableOrderedCollection#refresh(Observable)}
	 *
	 * @param <E> The type of the collection to refresh
	 */
	class RefreshingOrderedCollection<E> extends RefreshingCollection<E> implements ObservableOrderedCollection<E> {
		protected RefreshingOrderedCollection(ObservableOrderedCollection<E> wrap, Observable<?> refresh) {
			super(wrap, refresh);
		}

		@Override
		protected ObservableOrderedCollection<E> getWrapped() {
			return (ObservableOrderedCollection<E>) super.getWrapped();
		}

		@Override
		public CollectionSubscription subscribeOrdered(Consumer<? super OrderedCollectionEvent<? extends E>> observer) {
			return subscribe(evt -> observer.accept((OrderedCollectionEvent<? extends E>) evt));
		}
	}

	/**
	 * Implements {@link ObservableOrderedCollection#refreshEach(Function)}
	 *
	 * @param <E> The type of the collection to refresh
	 */
	class ElementRefreshingOrderedCollection<E> extends ElementRefreshingCollection<E> implements ObservableOrderedCollection<E> {
		protected ElementRefreshingOrderedCollection(ObservableOrderedCollection<E> wrap, Function<? super E, Observable<?>> refresh) {
			super(wrap, refresh);
		}

		@Override
		protected ObservableOrderedCollection<E> getWrapped() {
			return (ObservableOrderedCollection<E>) super.getWrapped();
		}

		@Override
		public CollectionSubscription subscribeOrdered(Consumer<? super OrderedCollectionEvent<? extends E>> observer) {
			return subscribe(evt -> observer.accept((OrderedCollectionEvent<? extends E>) evt));
		}
	}

	/**
	 * Implements {@link ObservableOrderedCollection#sorted(Comparator)}
	 *
	 * @param <E> The type of the elements in the collection
	 */
	class SortedObservableCollection<E> implements PartialCollectionImpl<E>, ObservableOrderedCollection<E> {
		private final ObservableOrderedCollection<E> theWrapped;
		private final Comparator<? super E> theCompare;

		public SortedObservableCollection(ObservableOrderedCollection<E> wrap, Comparator<? super E> compare) {
			theWrapped = wrap;
			theCompare = compare;
		}

		@Override
		public TypeToken<E> getType() {
			return theWrapped.getType();
		}

		/** @return The comparator sorting this collection's elements */
		public Comparator<? super E> comparator() {
			return theCompare;
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
		public boolean canRemove(Object value) {
			return theWrapped.canRemove(value);
		}

		@Override
		public boolean canAdd(E value) {
			return theWrapped.canAdd(value);
		}

		@Override
		public Iterator<E> iterator() {
			// TODO Any way to do this better?
			ArrayList<E> sorted = new ArrayList<>(theWrapped);
			Collections.sort(sorted, theCompare);
			return sorted.iterator();
		}

		protected Subscription onOrderedElement(Consumer<? super ObservableOrderedElement<E>> onElement, Comparator<? super E> comparator) {
			class SortedElement implements ObservableOrderedElement<E>, Comparable<SortedElement> {
				private final ObservableOrderedElement<E> theWrappedEl;
				private final DefaultTreeSet<SortedElement> theElements;
				private final SimpleObservable<Void> theRemoveObservable;
				private DefaultNode<SortedElement> theNode;
				private int theRemovedIndex;

				SortedElement(ObservableOrderedElement<E> wrap, DefaultTreeSet<SortedElement> elements) {
					theWrappedEl = wrap;
					theElements = elements;
					theRemoveObservable = new SimpleObservable<>();
				}

				@Override
				public TypeToken<E> getType() {
					return theWrappedEl.getType();
				}

				@Override
				public boolean isSafe() {
					return theWrappedEl.isSafe();
				}

				@Override
				public int getIndex() {
					return theNode != null ? theNode.getIndex() : theRemovedIndex;
				}

				@Override
				public E get() {
					return theWrappedEl.get();
				}

				@Override
				public Subscription subscribe(Observer<? super ObservableValueEvent<E>> observer) {
					return ObservableUtils.wrap(theWrappedEl.takeUntil(theRemoveObservable), this, observer);
				}

				@Override
				public ObservableValue<E> persistent() {
					return theWrappedEl.persistent();
				}

				@Override
				public int compareTo(SortedElement el) {
					int compare = comparator.compare(get(), el.get());
					if (compare != 0)
						return compare;
					return theWrappedEl.getIndex() - el.theWrappedEl.getIndex();
				}

				void delete() {
					theRemovedIndex = theNode.getIndex();
					theNode.delete();
					theNode = null;
				}

				void checkOrdering() {
					CountedRedBlackNode<SortedElement> parent = theNode.getParent();
					boolean isLeft = theNode.getSide();
					CountedRedBlackNode<SortedElement> left = theNode.getLeft();
					CountedRedBlackNode<SortedElement> right = theNode.getRight();

					boolean changed = false;
					if (parent != null) {
						int compare = comparator.compare(parent.getValue().get(), get());
						if (compare == 0)
							changed = true;
						else if (compare > 0 != isLeft)
							changed = true;
					}
					if (!changed && left != null) {
						if (comparator.compare(left.getValue().get(), get()) >= 0)
							changed = true;
					}
					if (!changed && right != null) {
						if (comparator.compare(right.getValue().get(), get()) <= 0)
							changed = true;
					}
					if (changed) {
						theRemoveObservable.onNext(null);
						theNode.delete();
						addIn();
					}
				}

				void addIn() {
					theNode = theElements.addGetNode(this);
					onElement.accept(this);
				}
			}
			DefaultTreeSet<SortedElement> elements = new DefaultTreeSet<>(SortedElement::compareTo);
			SimpleObservable<Void> unSubObs = new SimpleObservable<>();
			Subscription collSub = theWrapped.onOrderedElement(element -> {
				SortedElement sortedEl = new SortedElement(element, elements);
				element.unsubscribeOn(unSubObs).subscribe(new Observer<ObservableValueEvent<E>>() {
					@Override
					public <V extends ObservableValueEvent<E>> void onNext(V event) {
						if (event.isInitial())
							sortedEl.addIn();
						else
							sortedEl.checkOrdering();// Compensate if the value change has changed the sorting
					}

					@Override
					public <V extends ObservableValueEvent<E>> void onCompleted(V event) {
						sortedEl.delete();
					}
				});
			});
			return () -> {
				collSub.unsubscribe();
				unSubObs.onNext(null);
				elements.clear();
			};
		}

		@Override
		public Subscription onOrderedElement(Consumer<? super ObservableOrderedElement<E>> onElement) {
			return onOrderedElement(onElement, comparator());
		}

		@Override
		public String toString() {
			return ObservableCollection.toString(this);
		}
	}

	/**
	 * An observable ordered collection that cannot be modified directly, but reflects the value of a wrapped collection as it changes
	 *
	 * @param <E> The type of elements in the collection
	 */
	class ImmutableOrderedCollection<E> extends ImmutableObservableCollection<E> implements
	ObservableOrderedCollection<E> {
		protected ImmutableOrderedCollection(ObservableOrderedCollection<E> wrap) {
			super(wrap);
		}

		@Override
		protected ObservableOrderedCollection<E> getWrapped() {
			return (ObservableOrderedCollection<E>) super.getWrapped();
		}

		@Override
		public Subscription onOrderedElement(Consumer<? super ObservableOrderedElement<E>> observer) {
			return getWrapped().onOrderedElement(observer);
		}

		@Override
		public ImmutableOrderedCollection<E> immutable() {
			return this;
		}
	}

	/**
	 * Implements {@link ObservableOrderedCollection#filterModification(Predicate, Predicate)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	class ModFilteredOrderedCollection<E> extends ModFilteredCollection<E> implements ObservableOrderedCollection<E> {
		public ModFilteredOrderedCollection(ObservableOrderedCollection<E> wrapped, Predicate<? super E> removeFilter,
			Predicate<? super E> addFilter) {
			super(wrapped, removeFilter, addFilter);
		}

		@Override
		protected ObservableOrderedCollection<E> getWrapped() {
			return (ObservableOrderedCollection<E>) super.getWrapped();
		}

		@Override
		public Subscription onOrderedElement(Consumer<? super ObservableOrderedElement<E>> onElement) {
			return getWrapped().onOrderedElement(onElement);
		}
	}

	/**
	 * Backs {@link ObservableOrderedCollection#cached()}
	 *
	 * @param <E> The type of elements in the collection
	 */
	class SafeCachedOrderedCollection<E> extends SafeCachedObservableCollection<E> implements ObservableOrderedCollection<E> {
		protected static class OrderedCachedElement<E> extends CachedElement<E> implements ObservableOrderedElement<E> {
			protected OrderedCachedElement(ObservableOrderedElement<E> wrap) {
				super(wrap);
			}

			@Override
			protected ObservableOrderedElement<E> getWrapped() {
				return (ObservableOrderedElement<E>) super.getWrapped();
			}

			@Override
			public int getIndex() {
				return getWrapped().getIndex();
			}
		}

		protected SafeCachedOrderedCollection(ObservableOrderedCollection<E> wrap) {
			super(wrap);
		}

		@Override
		protected ObservableOrderedCollection<E> getWrapped() {
			return (ObservableOrderedCollection<E>) super.getWrapped();
		}

		@Override
		protected List<E> refresh() {
			return new ArrayList<>(super.refresh());
		}

		@Override
		protected List<OrderedCachedElement<E>> cachedElements() {
			ArrayList<OrderedCachedElement<E>> ret = new ArrayList<>(
				(Collection<OrderedCachedElement<E>>) (Collection<?>) super.cachedElements());
			Comparator<OrderedCachedElement<E>> compare = (OrderedCachedElement<E> el1, OrderedCachedElement<E> el2) -> el1.getIndex()
				- el2.getIndex();
			Collections.sort(ret, compare);
			return ret;
		}

		@Override
		protected CachedElement<E> createElement(ObservableElement<E> element) {
			return new OrderedCachedElement<>((ObservableOrderedElement<E>) element);
		}

		@Override
		public Subscription onOrderedElement(Consumer<? super ObservableOrderedElement<E>> onElement) {
			Subscription ret = addListener(element -> onElement.accept((ObservableOrderedElement<E>) element));
			for(OrderedCachedElement<E> el : cachedElements())
				onElement.accept(el.cached());
			return ret;
		}

		@Override
		public ObservableOrderedCollection<E> cached() {
			return this;
		}
	}

	/**
	 * Backs {@link ObservableOrderedCollection#takeUntil(Observable)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	class TakenUntilOrderedCollection<E> extends TakenUntilObservableCollection<E> implements ObservableOrderedCollection<E> {
		public TakenUntilOrderedCollection(ObservableOrderedCollection<E> wrap, Observable<?> until, boolean terminate) {
			super(wrap, until, terminate);
		}

		@Override
		protected ObservableOrderedCollection<E> getWrapped() {
			return (ObservableOrderedCollection<E>) super.getWrapped();
		}

		@Override
		public Subscription onElement(Consumer<? super ObservableElement<E>> onElement) {
			return onOrderedElement(onElement);
		}

		@Override
		public Subscription onOrderedElement(Consumer<? super ObservableOrderedElement<E>> onElement) {
			List<TakenUntilOrderedElement<E>> elements = new ArrayList<>();
			Subscription[] collSub = new Subscription[] { getWrapped().onOrderedElement(element -> {
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
	 * An element in a {@link ObservableOrderedCollection.TakenUntilOrderedCollection}
	 *
	 * @param <E> The type of value in the element
	 */
	class TakenUntilOrderedElement<E> extends TakenUntilElement<E> implements ObservableOrderedElement<E> {
		public TakenUntilOrderedElement(ObservableOrderedElement<E> wrap, boolean terminate) {
			super(wrap, terminate);
		}

		@Override
		public int getIndex() {
			return getWrapped().getIndex();
		}

		@Override
		protected ObservableOrderedElement<E> getWrapped() {
			return (ObservableOrderedElement<E>) super.getWrapped();
		}
	}

	/**
	 * Implements {@link ObservableOrderedCollection#flattenValues(ObservableOrderedCollection)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	class FlattenedOrderedValuesCollection<E> extends FlattenedValuesCollection<E> implements ObservableOrderedCollection<E> {
		protected FlattenedOrderedValuesCollection(ObservableOrderedCollection<? extends ObservableValue<? extends E>> collection) {
			super(collection);
		}

		@Override
		protected ObservableOrderedCollection<? extends ObservableValue<? extends E>> getWrapped() {
			return (ObservableOrderedCollection<? extends ObservableValue<? extends E>>) super.getWrapped();
		}

		@Override
		protected FlattenedOrderedValueElement<E> createFlattenedElement(
			ObservableElement<? extends ObservableValue<? extends E>> element) {
			return new FlattenedOrderedValueElement<>((ObservableOrderedElement<? extends ObservableValue<? extends E>>) element,
				getType());
		}

		@Override
		public Subscription onOrderedElement(Consumer<? super ObservableOrderedElement<E>> onElement) {
			return getWrapped().onOrderedElement(element -> onElement.accept(createFlattenedElement(element)));
		}

		@Override
		public Subscription onElement(Consumer<? super ObservableElement<E>> onElement) {
			return onOrderedElement(onElement);
		}
	}

	/**
	 * Implements elements for {@link ObservableOrderedCollection#flattenValues(ObservableOrderedCollection)}
	 *
	 * @param <E> The type of value in the element
	 */
	class FlattenedOrderedValueElement<E> extends FlattenedValueElement<E> implements ObservableOrderedElement<E> {
		public FlattenedOrderedValueElement(ObservableOrderedElement<? extends ObservableValue<? extends E>> wrap, TypeToken<E> type) {
			super(wrap, type);
		}

		@Override
		protected ObservableOrderedElement<? extends ObservableValue<? extends E>> getWrapped() {
			return (ObservableOrderedElement<? extends ObservableValue<? extends E>>) super.getWrapped();
		}

		@Override
		public int getIndex() {
			return getWrapped().getIndex();
		}
	}

	/**
	 * Implements {@link ObservableOrderedCollection#flattenValue(ObservableValue)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	class FlattenedOrderedValueCollection<E> extends FlattenedValueCollection<E> implements ObservableOrderedCollection<E> {
		public FlattenedOrderedValueCollection(ObservableValue<? extends ObservableOrderedCollection<E>> collectionObservable) {
			super(collectionObservable);
		}

		@Override
		protected ObservableValue<? extends ObservableOrderedCollection<E>> getWrapped() {
			return (ObservableValue<? extends ObservableOrderedCollection<E>>) super.getWrapped();
		}

		@Override
		public Subscription onOrderedElement(Consumer<? super ObservableOrderedElement<E>> onElement) {
			SimpleObservable<Void> unSubObs = new SimpleObservable<>();
			Subscription collSub = getWrapped()
				.subscribe(new Observer<ObservableValueEvent<? extends ObservableOrderedCollection<? extends E>>>() {
					@Override
					public <V extends ObservableValueEvent<? extends ObservableOrderedCollection<? extends E>>> void onNext(V event) {
						if (event.getValue() != null) {
							Observable<?> until = ObservableUtils.makeUntil(getWrapped(), event);
							((ObservableOrderedCollection<E>) event.getValue().takeUntil(until).unsubscribeOn(unSubObs))
							.onOrderedElement(onElement);
						}
					}
				});
			return () -> {
				collSub.unsubscribe();
				unSubObs.onNext(null);
			};
		}

		@Override
		public Subscription onElement(Consumer<? super ObservableElement<E>> onElement) {
			return onOrderedElement(onElement);
		}
	}

	/**
	 * Implements {@link ObservableOrderedCollection#flatten(ObservableOrderedCollection)}
	 *
	 * @param <E> The type of the collection
	 */
	class FlattenedOrderedCollection<E> extends FlattenedObservableCollection<E> implements ObservableOrderedCollection<E> {
		protected FlattenedOrderedCollection(ObservableOrderedCollection<? extends ObservableOrderedCollection<? extends E>> outer) {
			super(outer);
		}

		@Override
		protected ObservableOrderedCollection<? extends ObservableOrderedCollection<? extends E>> getOuter() {
			return (ObservableOrderedCollection<? extends ObservableOrderedCollection<? extends E>>) super.getOuter();
		}

		@Override
		public Subscription onOrderedElement(Consumer<? super ObservableOrderedElement<E>> onElement) {
			return onElement(ObservableOrderedCollection::onOrderedElement, onElement);
		}

		protected interface ElementSubscriber {
			<E> Subscription onElement(ObservableOrderedCollection<E> coll, Consumer<? super ObservableOrderedElement<E>> onElement);
		}

		protected Subscription onElement(ElementSubscriber subscriber, Consumer<? super ObservableOrderedElement<E>> onElement) {
			class OuterNode {
				final ObservableOrderedElement<? extends ObservableOrderedCollection<? extends E>> element;
				final List<ObservableOrderedElement<? extends E>> subElements;

				OuterNode(ObservableOrderedElement<? extends ObservableOrderedCollection<? extends E>> el) {
					element = el;
					subElements = new ArrayList<>();
				}
			}
			List<OuterNode> nodes = new ArrayList<>();
			class InnerElement implements ObservableOrderedElement<E> {
				private final ObservableOrderedElement<? extends E> theWrapped;
				private final OuterNode theOuterNode;

				InnerElement(ObservableOrderedElement<? extends E> wrap, OuterNode outerNode) {
					theWrapped = wrap;
					theOuterNode = outerNode;
				}

				@Override
				public TypeToken<E> getType() {
					return FlattenedOrderedCollection.this.getType();
				}

				@Override
				public boolean isSafe() {
					return theWrapped.isSafe();
				}

				@Override
				public E get() {
					return theWrapped.get();
				}

				@Override
				public int getIndex() {
					int index = 0;
					for (int i = 0; i < theOuterNode.element.getIndex(); i++) {
						index += nodes.get(i).subElements.size();
					}
					index += theWrapped.getIndex();
					return index;
				}

				@Override
				public Subscription subscribe(Observer<? super ObservableValueEvent<E>> observer) {
					return ObservableUtils.wrap(theWrapped, this, observer);
				}

				@Override
				public ObservableValue<E> persistent() {
					return (ObservableValue<E>) theWrapped.persistent();
				}

				@Override
				public String toString() {
					return getType() + " list[" + getIndex() + "]";
				}
			}
			SimpleObservable<Void> unSubObs = new SimpleObservable<>();
			Subscription outerSub = subscriber.onElement(getOuter(), outerEl -> {
				OuterNode outerNode = new OuterNode(outerEl);
				outerEl.subscribe(new Observer<ObservableValueEvent<? extends ObservableOrderedCollection<? extends E>>>() {
					@Override
					public <E1 extends ObservableValueEvent<? extends ObservableOrderedCollection<? extends E>>> void onNext(
						E1 outerEvent) {
						Observable<?> until = ObservableUtils.makeUntil(outerEl, outerEvent);
						if (outerEvent.isInitial())
							nodes.add(outerEl.getIndex(), outerNode);
						outerEvent.getValue().safe().takeUntil(until).unsubscribeOn(unSubObs).onOrderedElement(innerEl -> {
							innerEl.subscribe(new Observer<ObservableValueEvent<? extends E>>() {
								@Override
								public <E2 extends ObservableValueEvent<? extends E>> void onNext(E2 innerEvent) {
									if (innerEvent.isInitial())
										outerNode.subElements.add(innerEl.getIndex(), innerEl);
								}

								@Override
								public <E2 extends ObservableValueEvent<? extends E>> void onCompleted(E2 innerEvent) {
									outerNode.subElements.remove(innerEl.getIndex());
								}
							});
							InnerElement innerWrappedEl = new InnerElement(innerEl, outerNode);
							onElement.accept(innerWrappedEl);
						});
					}

					@Override
					public <E1 extends ObservableValueEvent<? extends ObservableOrderedCollection<? extends E>>> void onCompleted(
						E1 outerEvent) {
						nodes.remove(outerEl.getIndex());
					}
				});
			});
			return () -> {
				outerSub.unsubscribe();
				unSubObs.onNext(null);
				nodes.clear();
			};
		}

		@Override
		public Subscription onElement(Consumer<? super ObservableElement<E>> observer) {
			return onOrderedElement(observer);
		}
	}
}
