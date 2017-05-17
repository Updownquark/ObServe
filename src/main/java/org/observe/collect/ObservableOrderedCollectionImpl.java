package org.observe.collect;

import static org.observe.collect.CollectionChangeType.remove;
import static org.observe.collect.CollectionChangeType.set;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.SimpleObservable;
import org.observe.Subscription;
import org.observe.collect.ObservableCollectionImpl.CollectionChangesObservable;
import org.observe.collect.ObservableCollectionImpl.ElementRefreshingCollection;
import org.observe.collect.ObservableCollectionImpl.EquivalenceSwitchedCollection;
import org.observe.collect.ObservableCollectionImpl.FilterMappedObservableCollection;
import org.observe.collect.ObservableCollectionImpl.FlattenedObservableCollection;
import org.observe.collect.ObservableCollectionImpl.FlattenedValueCollection;
import org.observe.collect.ObservableCollectionImpl.FlattenedValuesCollection;
import org.observe.collect.ObservableCollectionImpl.RefreshingCollection;
import org.observe.collect.ObservableCollectionImpl.TakenUntilObservableCollection;
import org.observe.util.ObservableUtils;
import org.qommons.IntList;
import org.qommons.Transaction;
import org.qommons.tree.CountedRedBlackNode.DefaultNode;
import org.qommons.tree.CountedRedBlackNode.DefaultTreeMap;
import org.qommons.tree.CountedRedBlackNode.DefaultTreeSet;

import com.google.common.reflect.TypeToken;

/** Contains implementation classes for {@link ObservableOrderedCollection} */
public class ObservableOrderedCollectionImpl {
	private ObservableOrderedCollectionImpl() {}

	/**
	 * A simple, linear time, default implementation for {@link ObservableOrderedCollection#indexOf(Object)}
	 *
	 * @param coll The collection to search
	 * @param value The value to search for
	 * @return The index of the first element in the collection that is {@link ObservableCollection#equivalence() equivalent} to the given
	 *         object
	 */
	public static <E> int indexOf(ObservableOrderedCollection<E> coll, Object value) {
		if (!coll.equivalence().isElement(value))
			return -1;
		try (Transaction t = coll.lock(false, null)) {
			int index = 0;
			for (E v : coll) {
				if (coll.equivalence().elementEquals(v, value))
					return index;
				index++;
			}
			return -1;
		}
	}

	/**
	 * A simple, linear time, default implementation for {@link ObservableOrderedCollection#lastIndexOf(Object)}
	 *
	 * @param coll The collection to search
	 * @param value The value to search for
	 * @return The index of the last element in the collection that is {@link ObservableCollection#equivalence() equivalent} to the given
	 *         object
	 */
	public static <E> int lastIndexOf(ObservableOrderedCollection<E> coll, Object value) {
		if (!coll.equivalence().isElement(value))
			return -1;
		try (Transaction t = coll.lock(false, null)) {
			int result = -1;
			int index = 0;
			for (E v : coll) {
				if (coll.equivalence().elementEquals(v, value))
					result = index;
				index++;
			}
			return result;
		}
	}

	/**
	 * Fires {@link OrderedCollectionChangeEvent}s in response to sets of changes on an {@link ObservableOrderedCollection}.
	 * CollectionChangeEvents contain more information than the standard {@link OrderedCollectionEvent}, so listening to
	 * CollectionChangeEvents may result in better application performance.
	 *
	 * @param <E> The type of values in the collection
	 * @param <OCCE> The sub-type of OrderedCollectionChangeEvents that this observable fires
	 */
	public static class OrderedCollectionChangesObservable<E, OCCE extends OrderedCollectionChangeEvent<E>>
	extends CollectionChangesObservable<E, OCCE> {
		/**
		 * The sub-type of change tracker for this observable
		 *
		 * @param <E> The type of elements in the collection
		 */
		protected static class OrderedSessionChangeTracker<E> extends SessionChangeTracker<E> {
			/** The indexes for the element changes */
			protected final IntList indexes;

			/** @param typ The initial change type of events to accumulate */
			protected OrderedSessionChangeTracker(CollectionChangeType typ) {
				super(typ);
				indexes = new IntList();
			}

			@Override
			protected void clear(CollectionChangeType typ) {
				super.clear(typ);
				indexes.clear();
			}
		}

		/** @param coll The collection to observe */
		protected OrderedCollectionChangesObservable(ObservableOrderedCollection<E> coll) {
			super(coll);
		}

		@Override
		protected void accumulate(SessionChangeTracker<E> tracker, ObservableCollectionEvent<? extends E> event,
			Observer<? super OCCE> observer) {
			OrderedSessionChangeTracker<E> orderedTracker = (OrderedSessionChangeTracker<E>) tracker;
			int[] index = new int[] { ((OrderedCollectionEvent<E>) event).getIndex() };
			adjustTrackerForChange(orderedTracker, index, (OrderedCollectionEvent<? extends E>) event, observer);
			super.accumulate(tracker, event, observer);
			orderedTracker.indexes.add(((OrderedCollectionEvent<? extends E>) event).getIndex());
		}

		private OrderedSessionChangeTracker<E> adjustTrackerForChange(OrderedSessionChangeTracker<E> tracker, int[] index,
			OrderedCollectionEvent<? extends E> event, Observer<? super OCCE> observer) {
			if (tracker.type != event.getType()) {
				fireEventsUpTo(tracker, index, event, observer);
				if (adjustEventsPast(tracker, index, event, observer))
					return null;
				OrderedSessionChangeTracker<E> newTracker = new OrderedSessionChangeTracker<>(event.getType());
				newTracker.indexes.add(index[0]);
				newTracker.elements.add(event.getNewValue());
				if (newTracker.oldElements != null)
					newTracker.oldElements.add(event.getOldValue());
				fireEventsFromSessionData(newTracker, event, observer);
				fireEventsFromSessionData(tracker, event, observer);
				tracker.clear(event.getType());
				return tracker;
			} else {
				if (adjustEventsPast(tracker, index, event, observer))
					return null;
				return tracker;
			}
		}

		private void fireEventsUpTo(OrderedSessionChangeTracker<E> tracker, int[] index, OrderedCollectionEvent<? extends E> event,
			Observer<? super OCCE> observer) {
			// Fire events for indexes before the new change index, since otherwise those changes would affect the index
			if (tracker.indexes.size() < 25) {
				// If it's not too expensive, let's see if we need to do anything before constructing the lists needlessly
				boolean hasIndexesBefore = false;
				for (int i = 0; i < tracker.indexes.size(); i++)
					if (tracker.indexes.get(i) < index[0] || (event.getType() == remove && tracker.indexes.get(i) == index[0])) {
						hasIndexesBefore = true;
						break;
					}
				if (!hasIndexesBefore)
					return;
			}

			// Compile an event with the changes recorded in the tracker whose indexes were at or before the new change's index.
			// Remove those changes from the tracker and fire the event for them separately
			OrderedSessionChangeTracker<E> subTracker = new OrderedSessionChangeTracker<>(tracker.type);
			for (int i = 0; i < tracker.indexes.size(); i++) {
				if (tracker.indexes.get(i) < index[0] || (event.getType() == remove && tracker.indexes.get(i) == index[0])) {
					subTracker.indexes.add(tracker.indexes.remove(i));
					subTracker.elements.add(tracker.elements.remove(i));
					if (tracker.oldElements != null)
						subTracker.oldElements.add(tracker.oldElements.remove(i));
					i--;
				}
			}
			fireEventsFromSessionData(subTracker, event, observer);
		}

		private boolean adjustEventsPast(OrderedSessionChangeTracker<E> tracker, int[] index, OrderedCollectionEvent<? extends E> event,
			Observer<? super OCCE> observer) {

			// Adjust all indexes strictly past the change index first
			if (event.getType() != set && (tracker.type != remove || event.getType() != remove)) {
				for (int i = 0; i < tracker.indexes.size(); i++) {
					int changeIdx = tracker.indexes.get(i);
					if (changeIdx > index[0]) {
						if (event.getType() == remove)
							changeIdx--;
						else
							changeIdx++;
						tracker.indexes.set(i, changeIdx);
					}
				}
			}

			// Now handle the case where the indexes are the same
			int i = tracker.indexes.indexOf(index[0]);
			if (i >= 0) {
				switch (tracker.type) {
				case add:
					switch (event.getType()) {
					case add:
						tracker.indexes.set(i, index[0] + 1);
						break;
					case remove:
						tracker.indexes.remove(i);
						tracker.elements.remove(i);
						// oldElements will be null since tracker.type==add
						return true;
					case set:
						tracker.elements.set(i, event.getNewValue());
						return true;
					}
					break;
				case remove:
					switch (event.getType()) {
					case add:
						tracker.indexes.set(i, index[0] + 1);
						break;
					case remove:
						break;
					case set:
						break;
					}
					break;
				case set:
					switch (event.getType()) {
					case add:
						tracker.indexes.set(i, index[0] + 1);
						break;
					case remove:
						tracker.indexes.remove(i);
						tracker.elements.remove(i);
						tracker.oldElements.remove(i);
						break;
					case set:
						tracker.elements.set(i, event.getNewValue());
						return true;
					}
					break;
				}
			}

			if (tracker.type == remove && event.getType() == remove) {
				int indexAdd = 0;
				for (i = 0; i < tracker.indexes.size(); i++) {
					if (tracker.indexes.get(i) <= index[0])
						indexAdd++;
				}
				index[0] += indexAdd;
			}
			return false;
		}

		@Override
		protected void fireEventsFromSessionData(SessionChangeTracker<E> tracker, Object cause, Observer<? super OCCE> observer) {
			if (tracker.elements.isEmpty())
				return;
			OrderedSessionChangeTracker<E> orderedTracker = (OrderedSessionChangeTracker<E>) tracker;
			OrderedCollectionChangeEvent.doWith(
				new OrderedCollectionChangeEvent<>(tracker.type, tracker.elements, tracker.oldElements, orderedTracker.indexes, cause),
				evt -> observer.onNext((OCCE) evt));
		}
	}

	/**
	 * Implements {@link ObservableOrderedCollection#withEquivalence(Equivalence)}
	 *
	 * @param <E> The type of values in the collection
	 */
	public static class EquivalenceSwitchedOrderedCollection<E> extends EquivalenceSwitchedCollection<E>
	implements ObservableOrderedCollection<E> {
		/**
		 * @param wrap The source collection
		 * @param equivalence The equivalence set to use for equivalence operations
		 */
		protected EquivalenceSwitchedOrderedCollection(ObservableOrderedCollection<E> wrap, Equivalence<? super E> equivalence) {
			super(wrap, equivalence);
		}

		@Override
		protected ObservableOrderedCollection<E> getWrapped() {
			return (ObservableOrderedCollection<E>) super.getWrapped();
		}

		@Override
		public E get(int index) {
			return getWrapped().get(index);
		}

		@Override
		public int indexOf(Object value) {
			return ObservableOrderedCollectionImpl.indexOf(this, value);
		}

		@Override
		public int lastIndexOf(Object value) {
			return ObservableOrderedCollectionImpl.lastIndexOf(this, value);
		}

		@Override
		public CollectionSubscription subscribeOrdered(Consumer<? super OrderedCollectionEvent<? extends E>> observer) {
			return super.subscribe(evt -> observer.accept((OrderedCollectionEvent<? extends E>) evt));
		}
	}

	/**
	 * Implements {@link ObservableOrderedCollection#filterMap(ObservableCollection.FilterMapDef)}
	 *
	 * @param <E> The type of the collection to filter/map
	 * @param <T> The type of the filter/mapped collection
	 */
	public static class FilterMappedOrderedCollection<E, T> extends FilterMappedObservableCollection<E, T>
	implements ObservableOrderedCollection<T> {
		/**
		 * @param wrap The source collection
		 * @param filterMapDef The filter-mapping definition defining which elements are filtered from the collection and how they are
		 *        mapped
		 */
		protected FilterMappedOrderedCollection(ObservableOrderedCollection<E> wrap,
			ObservableCollection.FilterMapDef<E, ?, T> filterMapDef) {
			super(wrap, filterMapDef);
		}

		@Override
		protected ObservableOrderedCollection<E> getWrapped() {
			return (ObservableOrderedCollection<E>) super.getWrapped();
		}

		@Override
		public T get(int index) {
			if (!getDef().isFiltered())
				return getDef().map(new FilterMapResult<>(getWrapped().get(index))).result;
			if (index < 0)
				throw new IndexOutOfBoundsException("" + index);
			try (Transaction t = lock(false, null)) {
				int i = 0;
				for (T v : this) {
					if (i == index)
						return v;
					index++;
				}
				throw new IndexOutOfBoundsException(index + " of " + i);
			}
		}

		@Override
		public int indexOf(Object value) {
			if (!getDef().checkDestType(value))
				return -1;
			if (!getDef().isReversible())
				return ObservableOrderedCollectionImpl.indexOf(this, value);
			FilterMapResult<T, E> reversed = getDef().reverse(new FilterMapResult<>((T) value));
			if (reversed.error != null)
				return -1;
			return getWrapped().indexOf(reversed.result);
		}

		@Override
		public int lastIndexOf(Object value) {
			if (!getDef().checkDestType(value))
				return -1;
			if (!getDef().isReversible())
				return ObservableOrderedCollectionImpl.lastIndexOf(this, value);
			FilterMapResult<T, E> reversed = getDef().reverse(new FilterMapResult<>((T) value));
			if (reversed.error != null)
				return -1;
			return getWrapped().lastIndexOf(reversed.result);
		}

		@Override
		public CollectionSubscription subscribeOrdered(Consumer<? super OrderedCollectionEvent<? extends T>> observer) {
			return super.subscribe(evt -> observer.accept((OrderedCollectionEvent<? extends T>) evt));
		}

		@Override
		protected Object createSubscriptionMetadata() {
			// If this collection is filtered, then we need to keep track of the ordered set of elements that are included in this
			// collection to get the indexes right
			return getDef().isFiltered() ? new DefaultTreeSet<ElementId>(Comparable::compareTo) : null;
		}

		@Override
		protected OrderedCollectionEvent<T> map(ObservableCollectionEvent<? extends E> cause, CollectionChangeType type, T oldValue,
			T newValue, Object metadata) {
			DefaultTreeSet<ElementId> elements = (DefaultTreeSet<ElementId>) metadata;
			int index;
			if (!getDef().isFiltered())
				index = ((OrderedCollectionEvent<? extends E>) cause).getIndex();
			else {
				if (type == CollectionChangeType.add)
					index = elements.addGetNode(cause.getElementId()).getIndex();
				else if (type == CollectionChangeType.remove) {
					DefaultNode<ElementId> node = elements.getNode(cause.getElementId());
					index = node.getIndex();
					elements.removeNode(node);
				} else
					index = elements.indexOf(cause.getElementId());
			}
			return new OrderedCollectionEvent<>(cause.getElementId(), index, type, oldValue, newValue, cause);
		}
	}

	/**
	 * Implements {@link ObservableOrderedCollection#refresh(Observable)}
	 *
	 * @param <E> The type of the collection to refresh
	 */
	public static class RefreshingOrderedCollection<E> extends RefreshingCollection<E> implements ObservableOrderedCollection<E> {
		/**
		 * @param wrap The source collection
		 * @param refresh The observable to use to refresh the collection's elements
		 */
		protected RefreshingOrderedCollection(ObservableOrderedCollection<E> wrap, Observable<?> refresh) {
			super(wrap, refresh);
		}

		@Override
		protected ObservableOrderedCollection<E> getWrapped() {
			return (ObservableOrderedCollection<E>) super.getWrapped();
		}

		@Override
		public E get(int index) {
			return getWrapped().get(index);
		}

		@Override
		public int indexOf(Object value) {
			return getWrapped().indexOf(value);
		}

		@Override
		public int lastIndexOf(Object value) {
			return getWrapped().lastIndexOf(value);
		}

		@Override
		public CollectionSubscription subscribeOrdered(Consumer<? super OrderedCollectionEvent<? extends E>> observer) {
			return subscribe(evt -> observer.accept((OrderedCollectionEvent<? extends E>) evt));
		}

		@Override
		protected void doRefresh(Consumer<? super ObservableCollectionEvent<? extends E>> observer, Object cause) {
			int[] index = new int[1];
			getWrapped().spliterator().forEachObservableElement(el -> {
				OrderedCollectionEvent.doWith(
					new OrderedCollectionEvent<>(el.getElementId(), index[0]++, CollectionChangeType.set, el.get(), el.get(), cause),
					observer::accept);
			});
		}
	}

	/**
	 * Implements {@link ObservableOrderedCollection#refreshEach(Function)}
	 *
	 * @param <E> The type of the collection to refresh
	 */
	public static class ElementRefreshingOrderedCollection<E> extends ElementRefreshingCollection<E> implements ObservableOrderedCollection<E> {
		/**
		 * @param wrap The source collection
		 * @param refresh The function of observables to use to refresh each element
		 */
		protected ElementRefreshingOrderedCollection(ObservableOrderedCollection<E> wrap, Function<? super E, Observable<?>> refresh) {
			super(wrap, refresh);
		}

		@Override
		protected ObservableOrderedCollection<E> getWrapped() {
			return (ObservableOrderedCollection<E>) super.getWrapped();
		}

		@Override
		public E get(int index) {
			return getWrapped().get(index);
		}

		@Override
		public int indexOf(Object value) {
			return getWrapped().indexOf(value);
		}

		@Override
		public int lastIndexOf(Object value) {
			return getWrapped().lastIndexOf(value);
		}

		@Override
		public CollectionSubscription subscribeOrdered(Consumer<? super OrderedCollectionEvent<? extends E>> observer) {
			return subscribe(evt -> observer.accept((OrderedCollectionEvent<? extends E>) evt));
		}

		@Override
		protected <V> Map<ElementId, V> createElementMap() {
			return new DefaultTreeMap<>(Comparable::compareTo);
		}

		@Override
		protected OrderedCollectionEvent<E> refresh(ElementId elementId, E value, Map<ElementId, ?> elements, Object cause) {
			int index = ((DefaultTreeMap<ElementId, ?>) elements).getNode(elementId).getIndex();
			return new OrderedCollectionEvent<>(elementId, index, CollectionChangeType.set, value, value, cause);
		}
	}

	/**
	 * Backs {@link ObservableOrderedCollection#takeUntil(Observable)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	public static class TakenUntilOrderedCollection<E> extends TakenUntilObservableCollection<E> implements ObservableOrderedCollection<E> {
		/**
		 * @param wrap The source collection
		 * @param until The observable that will terminate all this collection's listeners whenever it fires a value
		 * @param terminate Whether the until observable will also cause this collection's listeners to get remove events, a la
		 *        {@link CollectionSubscription#unsubscribe(boolean) CollectionSubscription.unsubscribe(true)}
		 */
		protected TakenUntilOrderedCollection(ObservableOrderedCollection<E> wrap, Observable<?> until, boolean terminate) {
			super(wrap, until, terminate);
		}

		@Override
		protected ObservableOrderedCollection<E> getWrapped() {
			return (ObservableOrderedCollection<E>) super.getWrapped();
		}

		@Override
		public E get(int index) {
			return getWrapped().get(index);
		}

		@Override
		public int indexOf(Object value) {
			return getWrapped().indexOf(value);
		}

		@Override
		public int lastIndexOf(Object value) {
			return getWrapped().lastIndexOf(value);
		}

		@Override
		public CollectionSubscription subscribeOrdered(Consumer<? super OrderedCollectionEvent<? extends E>> observer) {
			return super.subscribe(evt -> observer.accept((OrderedCollectionEvent<? extends E>) evt));
		}
	}

	/**
	 * Implements {@link ObservableOrderedCollection#flattenValues(ObservableOrderedCollection)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	public static class FlattenedOrderedValuesCollection<E> extends FlattenedValuesCollection<E> implements ObservableOrderedCollection<E> {
		protected FlattenedOrderedValuesCollection(ObservableOrderedCollection<? extends ObservableValue<? extends E>> collection) {
			super(collection);
		}

		@Override
		protected ObservableOrderedCollection<? extends ObservableValue<? extends E>> getWrapped() {
			return (ObservableOrderedCollection<? extends ObservableValue<? extends E>>) super.getWrapped();
		}
	}

	/**
	 * Implements {@link ObservableOrderedCollection#flattenValue(ObservableValue)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	public static class FlattenedOrderedValueCollection<E> extends FlattenedValueCollection<E> implements ObservableOrderedCollection<E> {
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
	public static class FlattenedOrderedCollection<E> extends FlattenedObservableCollection<E> implements ObservableOrderedCollection<E> {
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
