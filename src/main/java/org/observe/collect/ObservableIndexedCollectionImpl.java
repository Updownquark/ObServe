package org.observe.collect;

import static org.observe.collect.CollectionChangeType.remove;
import static org.observe.collect.CollectionChangeType.set;

import java.util.Collection;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.Observer;
import org.observe.collect.ObservableCollectionImpl.CachedObservableCollection;
import org.observe.collect.ObservableCollectionImpl.CollectionChangesObservable;
import org.observe.collect.ObservableCollectionImpl.CombinedObservableCollection;
import org.observe.collect.ObservableCollectionImpl.ElementRefreshingCollection;
import org.observe.collect.ObservableCollectionImpl.EquivalenceSwitchedCollection;
import org.observe.collect.ObservableCollectionImpl.FilterMappedObservableCollection;
import org.observe.collect.ObservableCollectionImpl.FlattenedObservableCollection;
import org.observe.collect.ObservableCollectionImpl.FlattenedValueCollection;
import org.observe.collect.ObservableCollectionImpl.FlattenedValuesCollection;
import org.observe.collect.ObservableCollectionImpl.ModFilteredCollection;
import org.observe.collect.ObservableCollectionImpl.RefreshingCollection;
import org.observe.collect.ObservableCollectionImpl.TakenUntilObservableCollection;
import org.qommons.IntList;
import org.qommons.Ternian;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.TreeList;
import org.qommons.tree.CountedRedBlackNode.DefaultNode;
import org.qommons.tree.CountedRedBlackNode.DefaultTreeMap;
import org.qommons.tree.CountedRedBlackNode.DefaultTreeSet;

import com.google.common.reflect.TypeToken;

/** Contains implementation classes for {@link ObservableIndexedCollection} */
public class ObservableIndexedCollectionImpl {
	private ObservableIndexedCollectionImpl() {}

	/**
	 * A simple, linear time, default implementation for {@link ObservableIndexedCollection#indexOf(Object)}
	 *
	 * @param coll The collection to search
	 * @param value The value to search for
	 * @return The index of the first element in the collection that is {@link ObservableCollection#equivalence() equivalent} to the given
	 *         object
	 */
	public static <E> int indexOf(ObservableIndexedCollection<E> coll, Object value) {
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
	 * A simple, linear time, default implementation for {@link ObservableIndexedCollection#lastIndexOf(Object)}
	 *
	 * @param coll The collection to search
	 * @param value The value to search for
	 * @return The index of the last element in the collection that is {@link ObservableCollection#equivalence() equivalent} to the given
	 *         object
	 */
	public static <E> int lastIndexOf(ObservableIndexedCollection<E> coll, Object value) {
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
	 * Fires {@link OrderedCollectionChangeEvent}s in response to sets of changes on an {@link ObservableIndexedCollection}.
	 * CollectionChangeEvents contain more information than the standard {@link IndexedCollectionEvent}, so listening to
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
		protected OrderedCollectionChangesObservable(ObservableIndexedCollection<E> coll) {
			super(coll);
		}

		@Override
		protected void accumulate(SessionChangeTracker<E> tracker, ObservableCollectionEvent<? extends E> event,
			Observer<? super OCCE> observer) {
			OrderedSessionChangeTracker<E> orderedTracker = (OrderedSessionChangeTracker<E>) tracker;
			int[] index = new int[] { ((IndexedCollectionEvent<E>) event).getIndex() };
			adjustTrackerForChange(orderedTracker, index, (IndexedCollectionEvent<? extends E>) event, observer);
			super.accumulate(tracker, event, observer);
			orderedTracker.indexes.add(((IndexedCollectionEvent<? extends E>) event).getIndex());
		}

		private OrderedSessionChangeTracker<E> adjustTrackerForChange(OrderedSessionChangeTracker<E> tracker, int[] index,
			IndexedCollectionEvent<? extends E> event, Observer<? super OCCE> observer) {
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

		private void fireEventsUpTo(OrderedSessionChangeTracker<E> tracker, int[] index, IndexedCollectionEvent<? extends E> event,
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

		private boolean adjustEventsPast(OrderedSessionChangeTracker<E> tracker, int[] index, IndexedCollectionEvent<? extends E> event,
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
	 * Implements {@link ObservableIndexedCollection#withEquivalence(Equivalence)}
	 *
	 * @param <E> The type of values in the collection
	 */
	public static class EquivalenceSwitchedOrderedCollection<E> extends EquivalenceSwitchedCollection<E>
	implements ObservableIndexedCollection<E> {
		/**
		 * @param wrap The source collection
		 * @param equivalence The equivalence set to use for equivalence operations
		 */
		protected EquivalenceSwitchedOrderedCollection(ObservableIndexedCollection<E> wrap, Equivalence<? super E> equivalence) {
			super(wrap, equivalence);
		}

		@Override
		protected ObservableIndexedCollection<E> getWrapped() {
			return (ObservableIndexedCollection<E>) super.getWrapped();
		}

		@Override
		public E get(int index) {
			return getWrapped().get(index);
		}

		@Override
		public int indexOf(Object value) {
			return ObservableIndexedCollectionImpl.indexOf(this, value);
		}

		@Override
		public int lastIndexOf(Object value) {
			return ObservableIndexedCollectionImpl.lastIndexOf(this, value);
		}
	}

	/**
	 * Implements {@link ObservableIndexedCollection#filterMap(ObservableCollection.FilterMapDef)}
	 *
	 * @param <E> The type of the collection to filter/map
	 * @param <T> The type of the filter/mapped collection
	 */
	public static class FilterMappedOrderedCollection<E, T> extends FilterMappedObservableCollection<E, T>
	implements ObservableIndexedCollection<T> {
		/**
		 * @param wrap The source collection
		 * @param filterMapDef The filter-mapping definition defining which elements are filtered from the collection and how they are
		 *        mapped
		 */
		protected FilterMappedOrderedCollection(ObservableIndexedCollection<E> wrap,
			ObservableCollection.FilterMapDef<E, ?, T> filterMapDef) {
			super(wrap, filterMapDef);
		}

		@Override
		protected ObservableIndexedCollection<E> getWrapped() {
			return (ObservableIndexedCollection<E>) super.getWrapped();
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
					i++;
				}
				throw new IndexOutOfBoundsException(index + " of " + i);
			}
		}

		@Override
		public int indexOf(Object value) {
			if (!getDef().checkDestType(value))
				return -1;
			if (!getDef().isReversible())
				return ObservableIndexedCollectionImpl.indexOf(this, value);
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
				return ObservableIndexedCollectionImpl.lastIndexOf(this, value);
			FilterMapResult<T, E> reversed = getDef().reverse(new FilterMapResult<>((T) value));
			if (reversed.error != null)
				return -1;
			return getWrapped().lastIndexOf(reversed.result);
		}

		@Override
		protected Object createSubscriptionMetadata() {
			// If this collection is filtered, then we need to keep track of the ordered set of elements that are included in this
			// collection to get the indexes right
			return getDef().isFiltered() ? new DefaultTreeSet<ElementId>(Comparable::compareTo) : null;
		}

		@Override
		protected IndexedCollectionEvent<T> map(ObservableCollectionEvent<? extends E> cause, CollectionChangeType type, T oldValue,
			T newValue, Object metadata) {
			DefaultTreeSet<ElementId> elements = (DefaultTreeSet<ElementId>) metadata;
			int index;
			if (!getDef().isFiltered())
				index = ((IndexedCollectionEvent<? extends E>) cause).getIndex();
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
			return new IndexedCollectionEvent<>(cause.getElementId(), index, type, oldValue, newValue, cause);
		}
	}

	/**
	 * Implements {@link ObservableIndexedCollection#combine(ObservableCollection.CombinedCollectionDef)}
	 *
	 * @param <E> The type of values in the source collection
	 * @param <V> The type of values in this collection
	 */
	public static class CombinedOrderedCollection<E, V> extends CombinedObservableCollection<E, V>
	implements ObservableIndexedCollection<V> {
		/**
		 * @param wrap The source collection
		 * @param def The combination definition containing the observable values to combine the source collection's elements with and how
		 *        to combine them
		 */
		protected CombinedOrderedCollection(ObservableIndexedCollection<E> wrap, CombinedCollectionDef<E, V> def) {
			super(wrap, def);
		}

		@Override
		protected ObservableIndexedCollection<E> getWrapped() {
			return (ObservableIndexedCollection<E>) super.getWrapped();
		}

		@Override
		public V get(int index) {
			return combine(getWrapped().get(index));
		}

		@Override
		public int indexOf(Object value) {
			return ObservableIndexedCollectionImpl.indexOf(this, value);
		}

		@Override
		public int lastIndexOf(Object value) {
			return ObservableIndexedCollectionImpl.lastIndexOf(this, value);
		}

		@Override
		protected Object createSubscriptionMetadata() {
			return new DefaultTreeSet<ElementId>(Comparable::compareTo);
		}

		@Override
		protected ObservableCollectionEvent<V> createEvent(ElementId elementId, CollectionChangeType type, V oldValue, V newValue,
			Object cause, Object metadata) {
			DefaultTreeSet<ElementId> presentIds = (DefaultTreeSet<ElementId>) metadata;
			DefaultNode<ElementId> node;
			if (type == CollectionChangeType.add)
				node = presentIds.addGetNode(elementId);
			else
				node = presentIds.getNode(elementId);
			IndexedCollectionEvent<V> event = new IndexedCollectionEvent<>(elementId, node.getIndex(), type, oldValue, newValue, cause);
			if (type == CollectionChangeType.remove)
				presentIds.remove(elementId);
			return event;
		}
	}

	/**
	 * Implements {@link ObservableIndexedCollection#refresh(Observable)}
	 *
	 * @param <E> The type of the collection to refresh
	 */
	public static class RefreshingOrderedCollection<E> extends RefreshingCollection<E> implements ObservableIndexedCollection<E> {
		/**
		 * @param wrap The source collection
		 * @param refresh The observable to use to refresh the collection's elements
		 */
		protected RefreshingOrderedCollection(ObservableIndexedCollection<E> wrap, Observable<?> refresh) {
			super(wrap, refresh);
		}

		@Override
		protected ObservableIndexedCollection<E> getWrapped() {
			return (ObservableIndexedCollection<E>) super.getWrapped();
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
		public CollectionSubscription subscribeOrdered(Consumer<? super IndexedCollectionEvent<? extends E>> observer) {
			return subscribe(evt -> observer.accept((IndexedCollectionEvent<? extends E>) evt));
		}

		@Override
		protected void doRefresh(Consumer<? super ObservableCollectionEvent<? extends E>> observer, Object cause) {
			int[] index = new int[1];
			getWrapped().spliterator().forEachObservableElement(el -> {
				IndexedCollectionEvent.doWith(
					new IndexedCollectionEvent<>(el.getElementId(), index[0]++, CollectionChangeType.set, el.get(), el.get(), cause),
					observer::accept);
			});
		}
	}

	/**
	 * Implements {@link ObservableIndexedCollection#refreshEach(Function)}
	 *
	 * @param <E> The type of the collection to refresh
	 */
	public static class ElementRefreshingOrderedCollection<E> extends ElementRefreshingCollection<E> implements ObservableIndexedCollection<E> {
		/**
		 * @param wrap The source collection
		 * @param refresh The function of observables to use to refresh each element
		 */
		protected ElementRefreshingOrderedCollection(ObservableIndexedCollection<E> wrap, Function<? super E, Observable<?>> refresh) {
			super(wrap, refresh);
		}

		@Override
		protected ObservableIndexedCollection<E> getWrapped() {
			return (ObservableIndexedCollection<E>) super.getWrapped();
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
		public CollectionSubscription subscribeOrdered(Consumer<? super IndexedCollectionEvent<? extends E>> observer) {
			return subscribe(evt -> observer.accept((IndexedCollectionEvent<? extends E>) evt));
		}

		@Override
		protected <V> Map<ElementId, V> createElementMap() {
			return new DefaultTreeMap<>(Comparable::compareTo);
		}

		@Override
		protected IndexedCollectionEvent<E> refresh(ElementId elementId, E value, Map<ElementId, ?> elements, Object cause) {
			int index = ((DefaultTreeMap<ElementId, ?>) elements).getNode(elementId).getIndex();
			return new IndexedCollectionEvent<>(elementId, index, CollectionChangeType.set, value, value, cause);
		}
	}

	/**
	 * Backs {@link ObservableIndexedCollection#takeUntil(Observable)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	public static class TakenUntilOrderedCollection<E> extends TakenUntilObservableCollection<E> implements ObservableIndexedCollection<E> {
		/**
		 * @param wrap The source collection
		 * @param until The observable that will terminate all this collection's listeners whenever it fires a value
		 * @param terminate Whether the until observable will also cause this collection's listeners to get remove events, a la
		 *        {@link CollectionSubscription#unsubscribe(boolean) CollectionSubscription.unsubscribe(true)}
		 */
		protected TakenUntilOrderedCollection(ObservableIndexedCollection<E> wrap, Observable<?> until, boolean terminate) {
			super(wrap, until, terminate);
		}

		@Override
		protected ObservableIndexedCollection<E> getWrapped() {
			return (ObservableIndexedCollection<E>) super.getWrapped();
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
		public CollectionSubscription subscribeOrdered(Consumer<? super IndexedCollectionEvent<? extends E>> observer) {
			return super.subscribe(evt -> observer.accept((IndexedCollectionEvent<? extends E>) evt));
		}
	}

	/**
	 * Implements {@link ObservableIndexedCollection#filterModification(ModFilterDef)}
	 *
	 * @param <E> The type of values in the collection
	 */
	public static class ModFilteredOrderedCollection<E> extends ModFilteredCollection<E> implements ObservableIndexedCollection<E> {
		/**
		 * @param wrapped The source collection
		 * @param def The definition to define which modifications are permitted on the collection
		 */
		protected ModFilteredOrderedCollection(ObservableIndexedCollection<E> wrapped, ModFilterDef<E> def) {
			super(wrapped, def);
		}

		@Override
		protected ObservableIndexedCollection<E> getWrapped() {
			return (ObservableIndexedCollection<E>) super.getWrapped();
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
		public CollectionSubscription subscribeOrdered(Consumer<? super IndexedCollectionEvent<? extends E>> observer) {
			return subscribe(evt -> observer.accept((IndexedCollectionEvent<? extends E>) evt));
		}
	}

	/**
	 * Implements {@link ObservableIndexedCollection#cached(Observable)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	public static class CachedOrderedCollection<E> extends CachedObservableCollection<E> implements ObservableIndexedCollection<E> {
		/**
		 * @param wrapped The collection whose values to reflect
		 * @param until The observable to listen to to cease caching
		 */
		protected CachedOrderedCollection(ObservableIndexedCollection<E> wrapped, Observable<?> until) {
			super(wrapped, until);
		}

		@Override
		protected ObservableIndexedCollection<E> getWrapped() {
			return (ObservableIndexedCollection<E>) super.getWrapped();
		}

		@Override
		protected Observable<? extends IndexedCollectionEvent<? extends E>> getChanges() {
			return (Observable<? extends IndexedCollectionEvent<? extends E>>) super.getChanges();
		}

		@Override
		protected BetterCollection<E> createCache() {
			return new TreeList<E>() {
				@Override
				public boolean contains(Object o) {
					return find(v -> equivalence().elementEquals(v, o), el -> {
					});
				}

				@Override
				public int indexOf(Object o) {
					DefaultNode<E> node = findNode(n -> equivalence().elementEquals(n.getValue(), o), Ternian.TRUE);
					return node == null ? -1 : node.getIndex();
				}

				@Override
				public int lastIndexOf(Object o) {
					DefaultNode<E> node = findNode(n -> equivalence().elementEquals(n.getValue(), o), Ternian.FALSE);
					return node == null ? -1 : node.getIndex();
				}
			};
		}

		@Override
		protected DefaultTreeMap<ElementId, E> createCacheMap() {
			return new DefaultTreeMap<>(Comparable::compareTo);
		}

		@Override
		protected DefaultTreeMap<ElementId, E> getCacheMap() {
			return (DefaultTreeMap<ElementId, E>) super.getCacheMap();
		}

		@Override
		protected void updateCache(ObservableCollectionEvent<? extends E> change) {
			TreeList<E> cache = (TreeList<E>) getCache();
			IndexedCollectionEvent<? extends E> orderedChange = (IndexedCollectionEvent<? extends E>) change;
			switch (change.getType()) {
			case add:
				cache.add(orderedChange.getIndex(), orderedChange.getNewValue());
				break;
			case remove:
				cache.remove(orderedChange.getIndex());
				break;
			case set:
				cache.set(orderedChange.getIndex(), orderedChange.getNewValue());
				break;
			}
		}

		@Override
		protected IndexedCollectionEvent<? extends E> initialEvent(E value, ElementId elementId) {
			return new IndexedCollectionEvent<>(elementId, getCacheMap().indexOfKey(elementId), CollectionChangeType.add, null, value,
				null);
		}

		@Override
		protected IndexedCollectionEvent<? extends E> wrapEvent(ObservableCollectionEvent<? extends E> change) {
			return new IndexedCollectionEvent<>(change.getElementId(), ((IndexedCollectionEvent<? extends E>) change).getIndex(),
				change.getType(), change.getOldValue(), change.getNewValue(), change);
		}

		@Override
		protected IndexedCollectionEvent<? extends E> removeEvent(E value, ElementId elementId) {
			return new IndexedCollectionEvent<>(elementId, getCacheMap().indexOfKey(elementId), CollectionChangeType.remove, value, value,
				null);
		}

		@Override
		public E get(int index) {
			return ((TreeList<E>) getCache()).get(index);
		}

		@Override
		public int indexOf(Object value) {
			return ((TreeList<E>) getCache()).indexOf(value);
		}

		@Override
		public int lastIndexOf(Object value) {
			return ((TreeList<E>) getCache()).lastIndexOf(value);
		}

		@Override
		public CollectionSubscription subscribeOrdered(Consumer<? super IndexedCollectionEvent<? extends E>> observer) {
			return subscribe(evt -> observer.accept((IndexedCollectionEvent<? extends E>) evt));
		}
	}

	/**
	 * Implements {@link ObservableIndexedCollection#flattenValues(ObservableIndexedCollection)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	public static class FlattenedOrderedValuesCollection<E> extends FlattenedValuesCollection<E> implements ObservableIndexedCollection<E> {
		/** @param collection The collection of values to flatten */
		protected FlattenedOrderedValuesCollection(ObservableIndexedCollection<? extends ObservableValue<? extends E>> collection) {
			super(collection);
		}

		@Override
		protected ObservableIndexedCollection<? extends ObservableValue<? extends E>> getWrapped() {
			return (ObservableIndexedCollection<? extends ObservableValue<? extends E>>) super.getWrapped();
		}

		@Override
		public E get(int index) {
			return unwrap(getWrapped().get(index));
		}

		@Override
		public int indexOf(Object value) {
			return ObservableIndexedCollectionImpl.indexOf(this, value);
		}

		@Override
		public int lastIndexOf(Object value) {
			return ObservableIndexedCollectionImpl.lastIndexOf(this, value);
		}

		/** An observer for the ObservableValue inside one element of this collection */
		protected class OrderedAddObserver extends AddObserver {
			private final DefaultTreeSet<ElementId> thePresentIds;

			/**
			 * @param elementId The ID of the element to observe
			 * @param observer The subscriber
			 * @param presentIds The set of element IDs that are currently present in the collection
			 */
			protected OrderedAddObserver(ElementId elementId, Consumer<? super ObservableCollectionEvent<? extends E>> observer,
				DefaultTreeSet<ElementId> presentIds) {
				super(elementId, observer);
				thePresentIds = presentIds;
			}

			@Override
			protected IndexedCollectionEvent<E> createEvent(CollectionChangeType type, E oldValue, E newValue, Object cause) {
				DefaultNode<ElementId> node;
				if (type == CollectionChangeType.add)
					node = thePresentIds.addGetNode(getElementId());
				else
					node = thePresentIds.getNode(getElementId());
				IndexedCollectionEvent<E> event = new IndexedCollectionEvent<>(getElementId(), node.getIndex(), type, oldValue, newValue,
					cause);
				if (type == CollectionChangeType.remove)
					thePresentIds.remove(getElementId());
				return event;
			}
		}

		@Override
		public CollectionSubscription subscribeOrdered(Consumer<? super IndexedCollectionEvent<? extends E>> observer) {
			return subscribe(evt -> observer.accept((IndexedCollectionEvent<? extends E>) evt));
		}

		@Override
		protected Object createSubscriptionMetadata() {
			return new DefaultTreeSet<ElementId>(Comparable::compareTo);
		}

		@Override
		protected AddObserver createElementObserver(ElementId elementId, Consumer<? super ObservableCollectionEvent<? extends E>> observer,
			Object metadata) {
			return new OrderedAddObserver(elementId, observer, (DefaultTreeSet<ElementId>) metadata);
		}
	}

	/**
	 * Implements {@link ObservableIndexedCollection#flattenValue(ObservableValue)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	public static class FlattenedOrderedValueCollection<E> extends FlattenedValueCollection<E> implements ObservableIndexedCollection<E> {
		/** @param collectionObservable The value containing the collection to flatten */
		protected FlattenedOrderedValueCollection(
			ObservableValue<? extends ObservableIndexedCollection<? extends E>> collectionObservable) {
			super(collectionObservable);
		}

		@Override
		protected ObservableValue<? extends ObservableIndexedCollection<? extends E>> getWrapped() {
			return (ObservableValue<? extends ObservableIndexedCollection<? extends E>>) super.getWrapped();
		}

		@Override
		public E get(int index) {
			ObservableIndexedCollection<? extends E> coll = getWrapped().get();
			if (coll == null)
				throw new IndexOutOfBoundsException(index + " of 0");
			return coll.get(index);
		}

		@Override
		public int indexOf(Object value) {
			ObservableIndexedCollection<? extends E> coll = getWrapped().get();
			if (coll == null)
				return -1;
			return coll.indexOf(value);
		}

		@Override
		public int lastIndexOf(Object value) {
			ObservableIndexedCollection<? extends E> coll = getWrapped().get();
			if (coll == null)
				return -1;
			return coll.lastIndexOf(value);
		}

		@Override
		public CollectionSubscription subscribeOrdered(Consumer<? super IndexedCollectionEvent<? extends E>> observer) {
			return subscribe(evt -> observer.accept((IndexedCollectionEvent<? extends E>) evt));
		}
	}

	/**
	 * Implements {@link ObservableIndexedCollection#flatten(ObservableIndexedCollection)}
	 *
	 * @param <E> The type of the collection
	 */
	public static class FlattenedOrderedCollection<E> extends FlattenedObservableCollection<E> implements ObservableIndexedCollection<E> {
		/** @param outer The collection of collections to flatten */
		protected FlattenedOrderedCollection(ObservableIndexedCollection<? extends ObservableIndexedCollection<? extends E>> outer) {
			super(outer);
		}

		@Override
		protected ObservableIndexedCollection<? extends ObservableIndexedCollection<? extends E>> getOuter() {
			return (ObservableIndexedCollection<? extends ObservableIndexedCollection<? extends E>>) super.getOuter();
		}

		/**
		 * Used with {@link FlattenedOrderedCollection#doForIndex(int, boolean, boolean, IndexedAction)}
		 *
		 * @param <E> The type of the flattened collection
		 * @param <V> The type of the result of the action
		 */
		protected interface IndexedAction<E, V> {
			/**
			 * Called by {@link FlattenedOrderedCollection#doForIndex(int, boolean, boolean, IndexedAction)}
			 *
			 * @param subColl The sub-collection at the given index
			 * @param subIndex The index in the sub-collection corresponding to the index given for the flattened collection
			 * @return The result of the action
			 */
			V apply(ObservableIndexedCollection<? extends E> subColl, int subIndex);
		}

		/**
		 * @param <V> The type of the result
		 * @param index The index in this collection to operate on
		 * @param includeTerminus Whether {@link #size()} is a valid index (e.g. for add operations)
		 * @param write Whether the action may perform a write operation
		 * @param action The action to perform on the sub-collection
		 * @return The result of the action
		 * @throws IndexOutOfBoundsException If the index is not valid for this collection
		 */
		protected <V> V doForIndex(int index, boolean includeTerminus, boolean write, IndexedAction<E, V> action) {
			if (index < 0)
				throw new IndexOutOfBoundsException("" + index);
			int passed = 0;
			ObservableIndexedCollection<? extends E> last = null;
			int lastSize = -1;
			Transaction innerTrans = null;
			try (Transaction t = getOuter().lock(write, null)) {
				for (ObservableIndexedCollection<? extends E> inner : getOuter()) {
					if (innerTrans != null) {
						innerTrans.close();
						innerTrans = null;
					}
					innerTrans = inner.lock(write, null);
					int size = inner.size();
					if (index < passed + size)
						return action.apply(inner, index - passed);
					passed += size;
					last = inner;
					lastSize = size;
				}
				if (includeTerminus && passed == index) {
					if (last == null) // Zero may be valid, but we can't go adding new inner collections, so this is unsupported
						throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
					return action.apply(last, lastSize);
				}
			} finally {
				if (innerTrans != null)
					innerTrans.close();
			}
			throw new IndexOutOfBoundsException(index + " of " + passed);
		}

		@Override
		public E get(int index) {
			return doForIndex(index, false, false, (coll, idx) -> coll.get(idx));
		}

		@Override
		public int indexOf(Object value) {
			int passed = 0;
			for (ObservableIndexedCollection<? extends E> inner : getOuter()) {
				int index = inner.indexOf(value);
				if (index >= 0)
					return passed + index;
				else
					passed += inner.size();
			}
			return -1;
		}

		@Override
		public int lastIndexOf(Object value) {
			int passed = 0;
			int lastFound = -1;
			for (ObservableIndexedCollection<? extends E> inner : getOuter()) {
				int index = inner.indexOf(value);
				if (index >= 0)
					lastFound = passed + index;
				passed += inner.size();
			}
			return lastFound;
		}

		@Override
		protected Object createSubscriptionMetadata() {
			return new DefaultTreeSet<ElementId>(Comparable::compareTo);
		}

		@Override
		public CollectionSubscription subscribe(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
			return subscribeOrdered(observer);
		}

		@Override
		public CollectionSubscription subscribeOrdered(Consumer<? super IndexedCollectionEvent<? extends E>> observer) {
			OrderedOuterObserver outerObs = new OrderedOuterObserver(observer);
			CollectionSubscription collSub;
			try (Transaction t = getOuter().lock(false, null)) {
				collSub = getOuter().subscribe(outerObs);
				outerObs.setInitialized();
			}
			return removeAll -> {
				try (Transaction t = getOuter().lock(false, null)) {
					if (!removeAll)
						outerObs.done();
					collSub.unsubscribe(removeAll);
				}
			};
		}

		/** An observer for the outer collection that creates {@link OrderedAddObserver}s to fire ordered events */
		protected class OrderedOuterObserver extends OuterObserver {
			/** @param observer The observer for this collection */
			protected OrderedOuterObserver(Consumer<? super IndexedCollectionEvent<? extends E>> observer) {
				super(evt -> observer.accept((IndexedCollectionEvent<? extends E>) evt));
			}

			@Override
			protected AddObserver createElementObserver(ElementId elementId,
				Consumer<? super ObservableCollectionEvent<? extends E>> observer, Object metadata) {
				return new OrderedAddObserver(elementId, observer, (DefaultTreeSet<ElementId>) metadata);
			}
		}

		/** An observer for the ObservableCollection inside one element of this collection */
		protected class OrderedAddObserver extends AddObserver {
			private final DefaultTreeSet<ElementId> thePresentIds;

			/**
			 * @param elementId The ID of the element to observe
			 * @param observer The subscriber
			 * @param presentIds The set of element IDs that are currently present in the collection
			 */
			protected OrderedAddObserver(ElementId elementId, Consumer<? super ObservableCollectionEvent<? extends E>> observer,
				DefaultTreeSet<ElementId> presentIds) {
				super(elementId, observer);
				thePresentIds = presentIds;
			}

			@Override
			protected ObservableCollectionEvent<E> createEvent(ObservableCollectionEvent<? extends E> innerEvent) {
				ElementId compoundId = compoundId(getOuterElementId(), innerEvent.getElementId());
				DefaultNode<ElementId> node;
				if (innerEvent.getType() == CollectionChangeType.add)
					node = thePresentIds.addGetNode(compoundId);
				else
					node = thePresentIds.getNode(compoundId);
				IndexedCollectionEvent<E> event = new IndexedCollectionEvent<>(compoundId, node.getIndex(), innerEvent.getType(),
					innerEvent.getOldValue(), innerEvent.getNewValue(), innerEvent);
				if (innerEvent.getType() == CollectionChangeType.remove)
					thePresentIds.remove(compoundId);
				return event;
			}
		}
	}

	/**
	 * Implements {@link ObservableCollection#indexify()}
	 *
	 * @param <E> The type of the collection
	 */
	public static class IndexifiedCollection<E> implements ObservableIndexedCollection<E> {
		private final ObservableCollection<E> theCollection;

		/** @param collection The source collection */
		protected IndexifiedCollection(ObservableCollection<E> collection) {
			theCollection = collection;
		}

		@Override
		public TypeToken<E> getType() {
			return theCollection.getType();
		}

		@Override
		public boolean isLockSupported() {
			return theCollection.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theCollection.lock(write, cause);
		}

		@Override
		public Equivalence<? super E> equivalence() {
			return theCollection.equivalence();
		}

		@Override
		public int size() {
			return theCollection.size();
		}

		@Override
		public boolean isEmpty() {
			return theCollection.isEmpty();
		}

		@Override
		public boolean contains(Object o) {
			return theCollection.contains(o);
		}

		@Override
		public boolean containsAny(Collection<?> c) {
			return theCollection.containsAny(c);
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			return theCollection.containsAll(c);
		}

		@Override
		public E get(int index) {
			if (index < 0)
				throw new IndexOutOfBoundsException("" + index);
			try (Transaction t = lock(false, null)) {
				int size = theCollection.size();
				if (index >= size)
					throw new IndexOutOfBoundsException(index + " of " + size);
				ObservableElementSpliterator<E> spliter = theCollection.spliterator();
				for (int i = 0; i < index; i++) {
					spliter.tryAdvance(v -> {
					});
				}
				Object[] res = new Object[1];
				spliter.tryAdvance(v -> res[0] = v);
				return (E) res[0];
			}
		}

		@Override
		public int indexOf(Object value) {
			if(!getType().getRawType().isInstance(value))
				return -1;
			try(Transaction t=lock(false, null)){
				boolean [] found=new boolean[1];
				ObservableElementSpliterator<E> spliter = theCollection.spliterator();
				int i;
				for(i=0;!found[0] && spliter.tryAdvance(v->found[0]=equivalence().elementEquals(v, value));i++){
				}
				if(found[0])
					return i;
				else
					return-1;
			}
		}

		@Override
		public int lastIndexOf(Object value) {
			if(!getType().getRawType().isInstance(value))
				return -1;
			try(Transaction t=lock(false, null)){
				int [] found=new int[]{-1};
				int [] i=new int[1];
				theCollection.spliterator().forEachRemaining(v->{
					if(equivalence().elementEquals(v, value))
						found[0]=i[0];
					i[0]++;
				});
				return found[0];
			}
		}

		@Override
		public String canAdd(E value) {
			return theCollection.canAdd(value);
		}

		@Override
		public String canRemove(Object value) {
			return theCollection.canRemove(value);
		}

		@Override
		public boolean add(E e) {
			return theCollection.add(e);
		}

		@Override
		public boolean remove(Object o) {
			return theCollection.remove(o);
		}

		@Override
		public boolean addAll(Collection<? extends E> c) {
			return theCollection.addAll(c);
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			return theCollection.removeAll(c);
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			return theCollection.retainAll(c);
		}

		@Override
		public void clear() {
			theCollection.clear();
		}

		@Override
		public ObservableElementSpliterator<E> spliterator() {
			return theCollection.spliterator();
		}

		@Override
		public CollectionSubscription subscribeOrdered(Consumer<? super IndexedCollectionEvent<? extends E>> observer) {
			DefaultTreeSet<ElementId> ids = new DefaultTreeSet<>(Comparable::compareTo);
			return theCollection.subscribe(evt -> {
				int index = -1; // Compiler-required initialization--will always be changed
				switch (evt.getType()) {
				case add:
					index = ids.addGetNode(evt.getElementId()).getIndex();
					break;
				case remove:
					DefaultNode<ElementId> node = ids.getNode(evt.getElementId());
					index = node.getIndex();
					ids.removeNode(node);
					break;
				case set:
					index = ids.indexOf(evt.getElementId());
					break;
				}
				observer.accept(
					new IndexedCollectionEvent<>(evt.getElementId(), index, evt.getType(), evt.getOldValue(), evt.getNewValue(), evt));
			});
		}
	}
}
