package org.observe.collect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.Spliterator;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.Subscription;
import org.observe.assoc.ObservableMultiMap;
import org.observe.assoc.ObservableSortedMultiMap;
import org.observe.collect.MutableObservableSpliterator.MutableObservableSpliteratorMap;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollection.CombinedCollectionBuilder;
import org.observe.collect.ObservableCollection.CombinedValues;
import org.observe.collect.ObservableCollection.GroupingBuilder;
import org.observe.collect.ObservableCollection.MappedCollectionBuilder;
import org.observe.collect.ObservableCollection.MappedCollectionBuilder.ElementSetter;
import org.observe.collect.ObservableCollection.SortedGroupingBuilder;
import org.observe.collect.ObservableCollection.StdMsg;
import org.observe.collect.ObservableCollection.UniqueDataFlow;
import org.observe.collect.ObservableCollection.UniqueMappedCollectionBuilder;
import org.observe.collect.ObservableCollection.UniqueSortedDataFlow;
import org.observe.collect.ObservableCollectionImpl.FilterMapResult;
import org.qommons.BiTuple;
import org.qommons.Causable;
import org.qommons.LinkedQueue;
import org.qommons.Ternian;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.CircularArrayList;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementSpliterator;
import org.qommons.collect.ReversibleCollection;
import org.qommons.collect.ReversibleElementSpliterator;
import org.qommons.collect.ReversibleIterable;
import org.qommons.collect.ReversibleList;
import org.qommons.collect.ReversibleSpliterator;
import org.qommons.collect.UpdatableMap;
import org.qommons.tree.CountedRedBlackNode.DefaultNode;
import org.qommons.tree.CountedRedBlackNode.DefaultTreeMap;
import org.qommons.tree.CountedRedBlackNode.DefaultTreeSet;
import org.qommons.value.Value;

import com.google.common.reflect.TypeToken;

/** Holds default implementation methods and classes for {@link ObservableCollection} */
public final class ObservableCollectionImpl {
	private ObservableCollectionImpl() {}

	/**
	 * @param <E> The type for the set
	 * @param equiv The equivalence set to make a set of
	 * @param c The collection whose values to add to the set
	 * @return The set
	 */
	public static <E> Set<E> toSet(Equivalence<? super E> equiv, Collection<?> c) {
		try (Transaction t = Transactable.lock(c, false, null)) {
			return c.stream().filter(equiv::isElement).map(e -> (E) e).collect(Collectors.toCollection(equiv::createSet));
		}
	}

	/**
	 * A default implementation for {@link ObservableCollection#contains(Object)}
	 *
	 * @param <E> The type of the collection
	 * @param coll The collection to test
	 * @param value The object to find
	 * @return Whether the given collection contains the given object
	 */
	public static <E> boolean contains(ObservableCollection<E> coll, Object value) {
		try (Transaction t = coll.lock(false, null)) {
			Spliterator<E> iter = coll.spliterator();
			boolean[] found = new boolean[1];
			while (!found[0] && iter.tryAdvance(v -> {
				if (coll.equivalence().elementEquals(v, value))
					found[0] = true;
			})) {
			}
			return found[0];
		}
	}

	/**
	 * A default implementation for {@link ObservableCollection#containsAny(Collection)}
	 *
	 * @param <E> The type of the collection
	 * @param coll The collection to test
	 * @param c The collection to test for containment
	 * @return Whether the first collection contains any elements in the second collection
	 */
	public static <E> boolean containsAny(ObservableCollection<E> coll, Collection<?> c) {
		if (c.isEmpty())
			return false;
		Set<E> cSet = toSet(coll.equivalence(), c);
		if (cSet.isEmpty())
			return false;
		try (Transaction t = coll.lock(false, null)) {
			Spliterator<E> iter = coll.spliterator();
			boolean[] found = new boolean[1];
			while (iter.tryAdvance(next -> {
				found[0] = cSet.contains(next);
			}) && !found[0]) {
			}
			return found[0];
		}
	}

	/**
	 * A default implementation for {@link ObservableCollection#containsAll(Collection)}
	 *
	 * @param <E> The type of the collection
	 * @param coll The collection to test
	 * @param c The collection to test for containment
	 * @return Whether the first collection contains all elements in the second collection
	 */
	public static <E> boolean containsAll(ObservableCollection<E> coll, Collection<?> c) {
		if (c.isEmpty())
			return true;
		Set<E> cSet = toSet(coll.equivalence(), c);
		if (cSet.isEmpty())
			return false;
		try (Transaction t = coll.lock(false, null)) {
			Spliterator<E> iter = coll.spliterator();
			while (iter.tryAdvance(next -> {
				cSet.remove(next);
			}) && !cSet.isEmpty()) {
			}
			return cSet.isEmpty();
		}
	}

	/**
	 * A default implementation for {@link ObservableCollection#addAll(Collection)}
	 *
	 * @param coll The collection to add to
	 * @param values The values to add
	 * @return Whether the collection was changed as a result of the call
	 */
	public static <E> boolean addAll(ObservableCollection<E> coll, Collection<? extends E> values) {
		boolean mod = false;
		try (Transaction t = coll.lock(true, null); Transaction t2 = Transactable.lock(values, false, null)) {
			for (E o : values)
				mod |= coll.add(o);
		}
		return mod;
	}

	/**
	 * A default implementation for {@link ObservableCollection#remove(Object)}
	 *
	 * @param coll The collection to remove from
	 * @param o The value to remove
	 * @return Whether the value was found and removed
	 */
	public static <E> boolean remove(ObservableCollection<E> coll, Object o) {
		return coll.find(v -> coll.equivalence().elementEquals(v, o), el -> el.remove());
	}

	/**
	 * A default implementation for {@link ObservableCollection#remove(Object)}
	 *
	 * @param coll The collection to remove from
	 * @param o The value to remove
	 * @return Whether the value was found and removed
	 */
	public static <E> boolean removeLast(ObservableCollection<E> coll, Object o) {
		return coll.find(v -> coll.equivalence().elementEquals(v, o), el -> el.remove(), false);
	}

	/**
	 * A default implementation for {@link ObservableCollection#removeAll(Collection)}
	 *
	 * @param coll The collection to remove from
	 * @param c The values to remove
	 * @return Whether any values were found and removed
	 */
	public static <E> boolean removeAll(ObservableCollection<E> coll, Collection<?> c) {
		if (c.isEmpty())
			return true;
		Set<E> cSet = toSet(coll.equivalence(), c);
		if (cSet.isEmpty())
			return false;
		return coll.removeIf(cSet::contains);
	}

	/**
	 * A default implementation for {@link ObservableCollection#retainAll(Collection)}
	 *
	 * @param coll The collection to remove from
	 * @param c The values to keep in the collection
	 * @return Whether any values were removed
	 */
	public static <E> boolean retainAll(ObservableCollection<E> coll, Collection<?> c) {
		if (c.isEmpty())
			return false;
		Set<E> cSet = toSet(coll.equivalence(), c);
		if (cSet.isEmpty())
			return false;
		return coll.removeIf(v -> !cSet.contains(v));
	}

	/**
	 * A default version of {@link ObservableCollection#onChange(Consumer)} for collections whose changes may depend on the elements that
	 * already existed in the collection when the change subscription was made. Such collections must override
	 * {@link ObservableCollection#subscribe(Consumer)}
	 *
	 * @param coll The collection to subscribe to changes for
	 * @param observer The observer to be notified of changes (but not initial elements)
	 * @return the subscription to unsubscribe to the changes
	 */
	public static <E> Subscription defaultOnChange(ObservableCollection<E> coll,
		Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
		boolean[] initialized = new boolean[1];
		CollectionSubscription sub;
		try (Transaction t = coll.lock(false, null)) {
			sub = coll.subscribe(evt -> {
				if (initialized[0])
					observer.accept(evt);
			});
			initialized[0] = true;
		}
		return sub;
	}

	/**
	 * Fires {@link CollectionChangeEvent}s in response to sets of changes on an {@link ObservableCollection}. CollectionChangeEvents
	 * contain more information than the standard {@link ObservableCollectionEvent}, so listening to CollectionChangeEvents may result in
	 * better application performance.
	 *
	 * @param <E> The type of values in the collection
	 * @param <CCE> The sub-type of CollectionChangeEvents that this observable fires
	 */
	public static class CollectionChangesObservable<E, CCE extends CollectionChangeEvent<E>> implements Observable<CCE> {
		/**
		 * Tracks a set of changes corresponding to a set of {@link ObservableCollectionEvent}s, so those changes can be fired at once
		 *
		 * @param <E> The type of values in the collection
		 */
		protected static class SessionChangeTracker<E> {
			/** The type of the change set that this tracker is currently accumulating. */
			protected CollectionChangeType type;
			/** The list of elements that have been added or removed, or the new values replaced in the collection */
			protected final List<E> elements;
			/** If this tracker's type is {@link CollectionChangeType#set}, the old values replaced in the collection */
			protected List<E> oldElements;

			/** @param typ The initial change type for this tracker's accumulation */
			protected SessionChangeTracker(CollectionChangeType typ) {
				type = typ;
				elements = new ArrayList<>();
				oldElements = type == CollectionChangeType.set ? new ArrayList<>() : null;
			}

			/** @param typ The new change type for this tracker's accumulation */
			protected void clear(CollectionChangeType typ) {
				elements.clear();
				if (oldElements != null)
					oldElements.clear();
				type = typ;
				if (type == CollectionChangeType.set && oldElements == null)
					oldElements = new ArrayList<>();
			}
		}

		/**
		 * The key used in the {@link Causable#onFinish(Object, org.qommons.Causable.TerminalAction) finish action map} that the change
		 * tracker is stored under
		 */
		protected static final String SESSION_TRACKER_PROPERTY = "change-tracker";

		/** The collection that this change observable watches */
		protected final ObservableCollection<E> collection;

		/** @param coll The collection for this change observable to watch */
		protected CollectionChangesObservable(ObservableCollection<E> coll) {
			collection = coll;
		}

		@Override
		public Subscription subscribe(Observer<? super CCE> observer) {
			Object key = new Object();
			return collection.subscribe(evt -> {
				SessionChangeTracker<E> tracker = (SessionChangeTracker<E>) evt.getRootCausable()
					.onFinish(key, //
						(cause, data) -> fireEventsFromSessionData((SessionChangeTracker<E>) data.get(SESSION_TRACKER_PROPERTY), //
							cause, observer))//
					.computeIfAbsent(SESSION_TRACKER_PROPERTY, p -> new SessionChangeTracker<>(evt.getType()));
				accumulate(tracker, evt, observer);
			});
		}

		/**
		 * Accumulates a new collection change into a session tracker. This method may result in events firing.
		 *
		 * @param tracker The change tracker accumulating events
		 * @param event The new event to accumulate
		 * @param observer The observer to fire events for, if necessary
		 */
		protected void accumulate(SessionChangeTracker<E> tracker, ObservableCollectionEvent<? extends E> event,
			Observer<? super CCE> observer) {
			if (event.getType() != tracker.type) {
				fireEventsFromSessionData(tracker, event, observer);
				tracker.clear(event.getType());
			}
			tracker.elements.add(event.getNewValue());
			if (tracker.type == CollectionChangeType.set)
				tracker.oldElements.add(event.getOldValue());
		}

		/**
		 * Fires a change event communicating all changes accumulated into a change tracker
		 *
		 * @param tracker The change tracker into which changes have been accumulated
		 * @param cause The overall cause of the change event
		 * @param observer The observer on which to fire the change event
		 */
		protected void fireEventsFromSessionData(SessionChangeTracker<E> tracker, Object cause, Observer<? super CCE> observer) {
			if (tracker.elements.isEmpty())
				return;
			CollectionChangeEvent.doWith(new CollectionChangeEvent<>(tracker.type, tracker.elements, tracker.oldElements, cause),
				evt -> observer.onNext((CCE) evt));
		}

		@Override
		public boolean isSafe() {
			return true;
		}

		@Override
		public String toString() {
			return "changes(" + collection + ")";
		}
	}

	/**
	 * Implements {@link ObservableCollection#find(Predicate, Supplier, boolean)}
	 *
	 * @param <E> The type of the value
	 */
	public static class ObservableCollectionFinder<E> implements ObservableValue<E> {
		private final ObservableCollection<E> theCollection;
		private final Predicate<? super E> theTest;
		private final Supplier<? extends E> theDefaultValue;
		private final boolean isFirst;

		/**
		 * @param collection The collection to find elements in
		 * @param test The test to find elements that pass
		 * @param defaultValue Provides default values when no elements of the collection pass the test
		 * @param first Whether to get the first value in the collection that passes or the last value
		 */
		protected ObservableCollectionFinder(ObservableCollection<E> collection, Predicate<? super E> test,
			Supplier<? extends E> defaultValue, boolean first) {
			theCollection = collection;
			theTest = test;
			theDefaultValue = defaultValue;
			isFirst = first;
		}

		@Override
		public TypeToken<E> getType() {
			return theCollection.getType();
		}

		@Override
		public boolean isSafe() {
			return true;
		}

		@Override
		public E get() {
			Object[] value = new Object[1];
			boolean found;
			if (isFirst) {
				if (theCollection.find(theTest, el -> value[0] = el.get()))
					return (E) value[0];
				else
					return theDefaultValue.get();
			} else {
				ElementId[] elId = new ElementId[1];
				found = theCollection.findAllObservableElements(theTest, el -> {
					if (elId[0] == null || elId[0].compareTo(el.getElementId()) < 0) {
						elId[0] = el.getElementId();
						value[0] = el.get();
					}
				}) > 0;
			}
			return found ? (E) value[0] : theDefaultValue.get();
		}

		@Override
		public Subscription subscribe(Observer<? super ObservableValueEvent<E>> observer) {
			DefaultTreeMap<ElementId, E> matches = new DefaultTreeMap<>(ElementId::compareTo);
			boolean[] initialized = new boolean[1];
			return theCollection.subscribe(new Consumer<ObservableCollectionEvent<? extends E>>() {
				private E theCurrentValue;

				@Override
				public void accept(ObservableCollectionEvent<? extends E> evt) {
					switch (evt.getType()) {
					case add:
						if (!theTest.test(evt.getNewValue()))
							return;
						int index = matches.putGetNode(evt.getElementId(), evt.getNewValue()).getIndex();
						if ((isFirst && index == 0) || (!isFirst && index == matches.size() - 1)) {
							if (initialized[0])
								fireChangeEvent(theCurrentValue, evt.getNewValue(), evt, observer::onNext);
							theCurrentValue = evt.getNewValue();
						}
						break;
					case remove:
						if (!theTest.test(evt.getOldValue()))
							return;
						DefaultNode<Map.Entry<ElementId, E>> node = matches.getNode(evt.getElementId());
						index = node.getIndex();
						matches.removeNode(node);
						if ((isFirst && index == 0) || (!isFirst && index == matches.size() - 1)) {
							E newValue;
							if (matches.isEmpty())
								newValue = theDefaultValue.get();
							else if (isFirst)
								newValue = matches.firstEntry().getValue();
							else
								newValue = matches.lastEntry().getValue();
							fireChangeEvent(theCurrentValue, newValue, evt, observer::onNext);
							theCurrentValue = newValue;
						}
						break;
					case set:
						boolean oldPass = theTest.test(evt.getOldValue());
						boolean newPass = evt.getOldValue() == evt.getNewValue() ? oldPass : theTest.test(evt.getNewValue());
						if (oldPass == newPass) {
							if (!oldPass)
								return;
							node = matches.putGetNode(evt.getElementId(), evt.getNewValue());
							index = node.getIndex();
							if ((isFirst && index == 0) || (!isFirst && index == matches.size() - 1)) {
								fireChangeEvent(theCurrentValue, evt.getNewValue(), evt, observer::onNext);
								theCurrentValue = evt.getNewValue();
							}
						} else if (oldPass) {
							doRemove(evt);
						} else {
							doAdd(evt);
						}
						break;
					}
				}

				private void doAdd(ObservableCollectionEvent<? extends E> evt) {
					int index = matches.putGetNode(evt.getElementId(), evt.getNewValue()).getIndex();
					if ((isFirst && index == 0) || (!isFirst && index == matches.size() - 1)) {
						if (initialized[0])
							fireChangeEvent(theCurrentValue, evt.getNewValue(), evt, observer::onNext);
						theCurrentValue = evt.getNewValue();
					}
				}

				private void doRemove(ObservableCollectionEvent<? extends E> evt) {
					DefaultNode<Map.Entry<ElementId, E>> node = matches.getNode(evt.getElementId());
					int index = node.getIndex();
					matches.removeNode(node);
					if ((isFirst && index == 0) || (!isFirst && index == matches.size() - 1)) {
						E newValue;
						if (matches.isEmpty())
							newValue = theDefaultValue.get();
						else if (isFirst)
							newValue = matches.firstEntry().getValue();
						else
							newValue = matches.lastEntry().getValue();
						fireChangeEvent(theCurrentValue, newValue, evt, observer::onNext);
						theCurrentValue = newValue;
					}
				}
			});
		}
	}

	/**
	 * A value that is a combination of a collection's values
	 *
	 * @param <E> The type of values in the collection
	 * @param <X> The type of the intermediate result used for calculation
	 * @param <T> The type of the produced value
	 */
	public static abstract class ReducedValue<E, X, T> implements ObservableValue<T> {
		private final ObservableCollection<E> theCollection;
		private final TypeToken<T> theDerivedType;

		/**
		 * @param collection The collection to reduce
		 * @param derivedType The type of the produced value
		 */
		public ReducedValue(ObservableCollection<E> collection, TypeToken<T> derivedType) {
			theCollection = collection;
			theDerivedType = derivedType;
		}

		/** @return The reduced collection */
		protected ObservableCollection<E> getCollection() {
			return theCollection;
		}

		@Override
		public TypeToken<T> getType() {
			return theDerivedType;
		}

		@Override
		public boolean isSafe() {
			return true;
		}

		@Override
		public Subscription subscribe(Observer<? super ObservableValueEvent<T>> observer) {
			boolean[] initialized = new boolean[1];
			Object key = new Object();
			Object[] x = new Object[] { init() };
			Object[] v = new Object[] { getValue((X) x[0]) };
			Subscription sub = theCollection.subscribe(evt -> {
				T oldV = (T) v[0];
				X newX = update((X) x[0], evt);
				x[0] = newX;
				v[0] = getValue(newX);
				if (initialized[0])
					evt.getRootCausable()
					.onFinish(key, (root, values) -> fireChangeEvent((T) values.get("oldValue"), (T) v[0], root, observer::onNext))
					.computeIfAbsent("oldValue", k -> oldV);
			});
			initialized[0] = true;
			fireInitialEvent((T) v[0], null, observer::onNext);
			return sub;
		}

		/** @return The initial computation value */
		protected abstract X init();

		/**
		 * Performs a reduction of a computation value with a collection element
		 *
		 * @param oldValue The value of the computation before the change
		 * @param change The collection element change to reduce into the computation
		 * @return The new value of the reduction
		 */
		protected abstract X update(X oldValue, ObservableCollectionEvent<? extends E> change);

		/**
		 * @param updated The computation value
		 * @return The value for the result
		 */
		protected abstract T getValue(X updated);
	}

	private static class ValueCount<E> {
		final E value;
		int left;
		int right;

		ValueCount(E val) {
			value = val;
		}

		boolean modify(boolean add, boolean lft) {
			boolean modified;
			if (add) {
				if (lft) {
					modified = left == 0;
					left++;
				} else {
					modified = right == 0;
					right++;
				}
			} else {
				if (lft) {
					modified = left == 1;
					left--;
				} else {
					modified = right == 1;
					right--;
				}
			}
			return modified;
		}

		boolean isEmpty() {
			return left == 0 && right == 0;
		}

		@Override
		public String toString() {
			return value + " (" + left + "/" + right + ")";
		}
	}

	/**
	 * Used by {@link IntersectionValue}
	 *
	 * @param <E> The type of values in the left collection
	 * @param <X> The type of values in the right collection
	 */
	public static abstract class ValueCounts<E, X> {
		final Equivalence<? super E> leftEquiv;
		final Map<E, ValueCount<E>> leftCounts;
		final Map<X, ValueCount<X>> rightCounts;
		int leftCount;
		int commonCount;
		int rightCount;

		ValueCounts(Equivalence<? super E> leftEquiv, Equivalence<? super X> rightEquiv) {
			this.leftEquiv = leftEquiv;
			leftCounts = leftEquiv.createMap();
			rightCounts = rightEquiv == null ? null : rightEquiv.createMap();
		}

		abstract void check(boolean initial, Object cause);

		/** @return The number of values in the left collection that do not exist in the right collection */
		public int getLeftCount() {
			return leftCount;
		}

		/** @return The number of values in the right collection that do not exist in the left collection */
		public int getRightCount() {
			return rightCount;
		}

		/** @return The number of values in the right collection that also exist in the left collection */
		public int getCommonCount() {
			return commonCount;
		}
	}

	private static <E, X> Subscription maintainValueCount(ValueCounts<E, X> counts, ObservableCollection<E> left,
		ObservableCollection<X> right) {
		final ReentrantLock lock = new ReentrantLock();
		boolean[] initialized = new boolean[1];
		Object key = new Object();
		abstract class ValueCountModifier {
			final void doNotify(Causable cause) {
				if (initialized[0])
					cause.getRootCausable().onFinish(key, (root, values) -> {
						lock.lock();
						try {
							counts.check(false, root);
						} finally {
							lock.unlock();
						}
					});
			}
		}
		class ValueCountElModifier extends ValueCountModifier implements Consumer<ObservableCollectionEvent<?>> {
			final boolean onLeft;

			ValueCountElModifier(boolean lft) {
				onLeft = lft;
			}

			@Override
			public void accept(ObservableCollectionEvent<?> evt) {
				if (!initialized[0])
					lock.lock();
				try {
					switch (evt.getType()) {
					case add:
						modify(evt.getNewValue(), true);
						doNotify(evt);
						break;
					case remove:
						modify(evt.getOldValue(), false);
						doNotify(evt);
						break;
					case set:
						Equivalence<Object> equiv = (Equivalence<Object>) (onLeft ? left.equivalence() : right.equivalence());
						if (!equiv.elementEquals(evt.getOldValue(), evt.getNewValue())) {
							modify(evt.getOldValue(), false);
							modify(evt.getNewValue(), true);
							doNotify(evt);
						}
					}
				} finally {
					if (!initialized[0])
						lock.unlock();
				}
			}

			private <V> void modify(V value, boolean add) {
				Map<V, ValueCount<V>> countMap;
				if (!onLeft) {
					if (counts.leftEquiv.isElement(value))
						countMap = (Map<V, ValueCount<V>>) (Map<?, ?>) counts.leftCounts;
					else if (counts.rightCounts == null)
						return;
					else
						countMap = (Map<V, ValueCount<V>>) (Map<?, ?>) counts.rightCounts;
				} else
					countMap = (Map<V, ValueCount<V>>) (Map<?, ?>) counts.leftCounts;
				ValueCount<V> count;
				if (add) {
					boolean[] added = new boolean[1];
					count = countMap.computeIfAbsent(value, v -> {
						added[0] = true;
						return new ValueCount<>(v);
					});
					if (added[0]) {
						count.modify(true, onLeft);
						if (onLeft)
							counts.leftCount++;
						else
							counts.rightCount++;
						return;
					}
				} else {
					count = countMap.get(value);
					if (count == null)
						return;
				}
				if (count.modify(add, onLeft)) {
					if (add) {
						if (onLeft)
							counts.rightCount--;
						else
							counts.leftCount--;
						counts.commonCount++;
					} else if (count.isEmpty()) {
						countMap.remove(value);
						if (onLeft)
							counts.leftCount--;
						else
							counts.rightCount--;
					} else {
						counts.commonCount--;
						if (onLeft)
							counts.rightCount++;
						else
							counts.leftCount++;
					}
				}
			}
		}
		Subscription leftSub;
		Subscription rightSub;
		lock.lock();
		try {
			leftSub = left.subscribe(new ValueCountElModifier(true));
			rightSub = right.subscribe(new ValueCountElModifier(false));

			counts.check(true, null);
		} finally {
			initialized[0] = true;
			lock.unlock();
		}
		return Subscription.forAll(leftSub, rightSub);
	}

	/**
	 * An observable value that reflects some quality of the intersection between two collections
	 *
	 * @param <E> The type of the left collection
	 * @param <X> The type of the right collection
	 */
	public abstract static class IntersectionValue<E, X> implements ObservableValue<Boolean> {
		private final ObservableCollection<E> theLeft;
		private final ObservableCollection<X> theRight;
		private final boolean isTrackingRight;
		private final Predicate<ValueCounts<E, X>> theSatisfiedCheck;

		/**
		 * @param left The left collection
		 * @param right The right collection
		 * @param trackRight Whether elements in the right collection that cannot possibly intersect with the left collection need to be
		 *        tracked
		 * @param satisfied The test to determine this value after any changes
		 */
		public IntersectionValue(ObservableCollection<E> left, ObservableCollection<X> right, boolean trackRight,
			Predicate<ValueCounts<E, X>> satisfied) {
			theLeft = left;
			theRight = right;
			isTrackingRight = trackRight;
			theSatisfiedCheck = satisfied;
		}

		/** @return The left collection */
		protected ObservableCollection<E> getLeft() {
			return theLeft;
		}

		/** @return The right collection */
		protected ObservableCollection<X> getRight() {
			return theRight;
		}

		@Override
		public TypeToken<Boolean> getType() {
			return TypeToken.of(Boolean.TYPE);
		}

		@Override
		public boolean isSafe() {
			return true;
		}

		@Override
		public Subscription subscribe(Observer<? super ObservableValueEvent<Boolean>> observer) {
			ValueCounts<E, X> counts = new ValueCounts<E, X>(theLeft.equivalence(), isTrackingRight ? theRight.equivalence() : null) {
				private boolean isSatisfied;

				@Override
				void check(boolean initial, Object cause) {
					boolean satisfied = theSatisfiedCheck.test(this);
					if (initial)
						fireInitialEvent(satisfied, null, observer::onNext);
					else if (satisfied != isSatisfied)
						fireChangeEvent(isSatisfied, satisfied, cause, observer::onNext);
					isSatisfied = satisfied;
				}
			};
			return maintainValueCount(counts, theLeft, theRight);
		}
	}

	/**
	 * A value that reflects whether a collection contains a given value
	 *
	 * @param <E> The type of the collection
	 * @param <X> The type of the value to find
	 */
	public static class ContainsValue<E, X> extends IntersectionValue<E, X> {
		private final ObservableValue<X> theValue;

		/**
		 * @param collection The collection
		 * @param value The value to find
		 */
		public ContainsValue(ObservableCollection<E> collection, ObservableValue<X> value) {
			super(collection, toCollection(value), false, counts -> counts.getCommonCount() > 0);
			theValue = value;
		}

		private static <T> ObservableCollection<T> toCollection(ObservableValue<T> value) {
			ObservableValue<ObservableCollection<T>> cv = value.mapV(v -> ObservableCollection.constant(value.getType(), v));
			return ObservableCollection.flattenValue(cv);
		}

		@Override
		public Boolean get() {
			return getLeft().contains(theValue.get());
		}
	}

	/**
	 * A value that reflects whether one collection contains any elements of another
	 *
	 * @param <E> The type of the left collection
	 * @param <X> The type of the right collection
	 */
	public static class ContainsAllValue<E, X> extends IntersectionValue<E, X> {
		/**
		 * @param left The left collection
		 * @param right The right collection
		 */
		public ContainsAllValue(ObservableCollection<E> left, ObservableCollection<X> right) {
			super(left, right, true, counts -> counts.getRightCount() == 0);
		}

		@Override
		public Boolean get() {
			return getLeft().containsAll(getRight());
		}
	}

	/**
	 * A value that reflects whether one collection contains all elements of another
	 *
	 * @param <E> The type of the left collection
	 * @param <X> The type of the right collection
	 */
	public static class ContainsAnyValue<E, X> extends IntersectionValue<E, X> {
		/**
		 * @param left The left collection
		 * @param right The right collection
		 */
		public ContainsAnyValue(ObservableCollection<E> left, ObservableCollection<X> right) {
			super(left, right, false, counts -> counts.getCommonCount() > 0);
		}

		@Override
		public Boolean get() {
			return getLeft().containsAny(getRight());
		}
	}

	public static class ReversedObservableCollection<E> extends ReversibleCollection.ReversedCollection<E>
	implements ObservableCollection<E> {
		public ReversedObservableCollection(ObservableCollection<E> wrapped) {
			super(wrapped);
		}

		@Override
		protected ObservableCollection<E> getWrapped() {
			return (ObservableCollection<E>) super.getWrapped();
		}

		@Override
		public TypeToken<E> getType() {
			return getWrapped().getType();
		}

		@Override
		public Equivalence<? super E> equivalence() {
			return getWrapped().equivalence();
		}

		@Override
		public boolean isLockSupported() {
			return getWrapped().isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return getWrapped().lock(write, cause);
		}

		@Override
		public ObservableElementSpliterator<E> spliterator() {
			return spliterator(true);
		}

		@Override
		public ObservableElementSpliterator<E> spliterator(boolean fromStart) {
			return (ObservableElementSpliterator<E>) super.spliterator(!fromStart).reverse();
		}

		@Override
		public MutableObservableSpliterator<E> mutableSpliterator(boolean fromStart) {
			return getWrapped().mutableSpliterator(!fromStart).reverse();
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
			return ObservableCollectionImpl.defaultOnChange(this, observer);
		}

		@Override
		public ObservableCollection<E> reverse() {
			return getWrapped();
		}

		@Override
		public E get(int index) {
			try (Transaction t = getWrapped().lock(false, null)) {
				return getWrapped().get(getWrapped().size() - index - 1);
			}
		}

		@Override
		public int indexOf(Object value) {
			try (Transaction t = getWrapped().lock(false, null)) {
				return getWrapped().size() - getWrapped().lastIndexOf(value) - 1;
			}
		}

		@Override
		public int lastIndexOf(Object value) {
			try (Transaction t = getWrapped().lock(false, null)) {
				return getWrapped().size() - getWrapped().indexOf(value) - 1;
			}
		}

		@Override
		public E[] toArray() {
			return ObservableCollection.super.toArray();
		}

		@Override
		public <T> T[] toArray(T[] a) {
			return ObservableCollection.super.toArray(a);
		}

		@Override
		public String canRemove(Object value) {
			return getWrapped().canRemove(value);
		}

		@Override
		public String canAdd(E value) {
			return getWrapped().canAdd(value);
		}

		@Override
		public CollectionSubscription subscribeIndexed(Consumer<? super IndexedCollectionEvent<? extends E>> observer) {
			return getWrapped().subscribeReverseIndexed(new ReversedSubscriber<>(observer));
		}

		@Override
		public CollectionSubscription subscribeReverseIndexed(Consumer<? super IndexedCollectionEvent<? extends E>> observer) {
			return getWrapped().subscribeIndexed(new ReversedSubscriber<>(observer));
		}

		private static class ReversedSubscriber<E> implements Consumer<IndexedCollectionEvent<? extends E>> {
			private final Consumer<? super IndexedCollectionEvent<? extends E>> theObserver;
			private int theSize;

			ReversedSubscriber(Consumer<? super IndexedCollectionEvent<? extends E>> observer) {
				theObserver = observer;
			}

			@Override
			public void accept(IndexedCollectionEvent<? extends E> evt) {
				if (evt.getType() == CollectionChangeType.add)
					theSize++;
				int index = theSize - evt.getIndex() - 1;
				if (evt.getType() == CollectionChangeType.remove)
					theSize++;
				IndexedCollectionEvent.doWith(new IndexedCollectionEvent<>(new ReversedElementId(evt.getElementId()), index, evt.getType(),
					evt.getOldValue(), evt.getNewValue(), evt), theObserver);
			}
		}

		private static class ReversedElementId implements ElementId {
			private final ElementId theSource;

			ReversedElementId(ElementId source) {
				super();
				theSource = source;
			}

			@Override
			public int compareTo(ElementId o) {
				return -theSource.compareTo(((ReversedElementId) o).theSource);
			}
		}
	}

	/**
	 * Used in mapping/filtering collection data
	 *
	 * @param <E> The source type
	 * @param <T> The destination type
	 */
	public static class FilterMapResult<E, T> {
		public E source;
		public T result;
		public String error;

		public FilterMapResult() {}

		public FilterMapResult(E src) {
			source = src;
		}
	}

	public static abstract class AbstractDataFlow<E, I, T> implements CollectionDataFlow<E, I, T> {
		private final AbstractDataFlow<E, ?, I> theParent;
		private final TypeToken<T> theTargetType;

		protected AbstractDataFlow(AbstractDataFlow<E, ?, I> parent, TypeToken<T> targetType) {
			theParent = parent;
			theTargetType = targetType;
		}

		protected ObservableCollection<E> getSource() {
			return theParent.getSource();
		}

		protected AbstractDataFlow<E, ?, I> getParent() {
			return theParent;
		}

		@Override
		public TypeToken<T> getTargetType() {
			return theTargetType;
		}

		@Override
		public CollectionDataFlow<E, T, T> filter(Function<? super T, String> filter, boolean filterNulls) {
			return new FilterOp<>(this, filter, filterNulls);
		}

		@Override
		public CollectionDataFlow<E, T, T> filterStatic(Function<? super T, String> filter, boolean filterNulls) {
			return new StaticFilterOp<>(this, filter, filterNulls);
		}

		@Override
		public CollectionDataFlow<E, T, T> withEquivalence(Equivalence<? super T> equivalence) {
			return new EquivalenceSwitchOp<>(this, equivalence);
		}

		@Override
		public CollectionDataFlow<E, T, T> refresh(Observable<?> refresh) {
			return new RefreshOp<>(this, refresh);
		}

		@Override
		public CollectionDataFlow<E, T, T> refreshEach(Function<? super T, ? extends Observable<?>> refresh) {
			return new ElementRefreshOp<>(this, refresh);
		}

		@Override
		public <X> MappedCollectionBuilder<E, T, X> map(TypeToken<X> target) {
			return new MappedCollectionBuilder<>(this, target);
		}

		@Override
		public <V, X> ObservableCollection.CombinedCollectionBuilder2<E, T, V, X> combineWith(ObservableValue<V> value,
			boolean combineNulls, TypeToken<X> target) {
			return new ObservableCollection.CombinedCollectionBuilder2<>(this, target, value, Ternian.of(combineNulls));
		}

		@Override
		public CollectionDataFlow<E, T, T> sorted(Comparator<? super T> compare) {
			return new SortedDataFlow<>(this, compare);
		}

		@Override
		public UniqueDataFlow<E, T, T> unique(Equivalence<? super T> equivalence, boolean alwaysUseFirst) {
			return new UniqueOp<>(this, equivalence, alwaysUseFirst);
		}

		@Override
		public UniqueSortedDataFlow<E, T, T> uniqueSorted(Comparator<? super T> compare, boolean alwaysUseFirst) {
			return new UniqueSortedDataFlowImpl<>(this, compare, alwaysUseFirst);
		}

		public abstract CollectionManager<E, ?, T> manageCollection();

		@Override
		public ObservableCollection<T> build() {
			return new ObservableCollectionImpl.DerivedCollection<>(getSource(), manageCollection());
		}

		@Override
		public ReversibleIterable<T> iterable() {
			return new ObservableCollectionImpl.DerivedIterable<>(getSource(), manageCollection());
		}
	}

	public static class UniqueDataFlowWrapper<E, T> extends AbstractDataFlow<E, T, T> implements UniqueDataFlow<E, T, T> {
		protected UniqueDataFlowWrapper(AbstractDataFlow<E, ?, T> parent) {
			super(parent, parent.getTargetType());
		}

		@Override
		public UniqueDataFlow<E, T, T> filter(Function<? super T, String> filter) {
			return (UniqueDataFlow<E, T, T>) super.filter(filter);
		}

		@Override
		public UniqueDataFlow<E, T, T> filter(Function<? super T, String> filter, boolean filterNulls) {
			return new UniqueDataFlowWrapper<>((AbstractDataFlow<E, ?, T>) getParent().filter(filter, filterNulls));
		}

		@Override
		public UniqueDataFlow<E, T, T> filterStatic(Function<? super T, String> filter) {
			return (UniqueDataFlow<E, T, T>) super.filterStatic(filter);
		}

		@Override
		public UniqueDataFlow<E, T, T> filterStatic(Function<? super T, String> filter, boolean filterNulls) {
			return new UniqueDataFlowWrapper<>((AbstractDataFlow<E, ?, T>) getParent().filterStatic(filter, filterNulls));
		}

		@Override
		public <X> UniqueMappedCollectionBuilder<E, T, X> mapEquivalent(TypeToken<X> target) {
			return new UniqueMappedCollectionBuilder<>(this, target);
		}

		@Override
		public UniqueDataFlow<E, T, T> refresh(Observable<?> refresh) {
			return new UniqueDataFlowWrapper<>((AbstractDataFlow<E, ?, T>) getParent().refresh(refresh));
		}

		@Override
		public UniqueDataFlow<E, T, T> refreshEach(Function<? super T, ? extends Observable<?>> refresh) {
			return new UniqueDataFlowWrapper<>((AbstractDataFlow<E, ?, T>) getParent().refreshEach(refresh));
		}

		@Override
		public CollectionManager<E, ?, T> manageCollection() {
			return getParent().manageCollection();
		}

		@Override
		public ObservableSet<T> build() {
			return new ObservableSetImpl.DerivedSet<>((ObservableSet<E>) getSource(), manageCollection());
		}
	}

	public static class UniqueOp<E, T> extends UniqueDataFlowWrapper<E, T> {
		private final Equivalence<? super T> theEquivalence;
		private final boolean isAlwaysUsingFirst;

		protected UniqueOp(AbstractDataFlow<E, ?, T> parent, Equivalence<? super T> equivalence, boolean alwaysUseFirst) {
			super(parent);
			theEquivalence = equivalence;
			isAlwaysUsingFirst = alwaysUseFirst;
		}

		@Override
		public CollectionManager<E, ?, T> manageCollection() {
			return new UniqueManager<>(getParent().manageCollection(), theEquivalence, isAlwaysUsingFirst);
		}
	}

	public static class SortedDataFlow<E, T> extends AbstractDataFlow<E, T, T> {
		private final Comparator<? super T> theCompare;

		protected SortedDataFlow(AbstractDataFlow<E, ?, T> parent, Comparator<? super T> compare) {
			super(parent, parent.getTargetType());
			theCompare = compare;
		}

		protected Comparator<? super T> getCompare() {
			return theCompare;
		}

		@Override
		public CollectionManager<E, ?, T> manageCollection() {
			return new SortedManager<>(getParent().manageCollection(), theCompare);
		}

		@Override
		public ObservableCollection<T> build() {
			return new DerivedCollection<>(getSource(), manageCollection());
		}
	}

	public static class UniqueSortedDataFlowWrapper<E, T> extends UniqueDataFlowWrapper<E, T> implements UniqueSortedDataFlow<E, T, T> {
		private final Comparator<? super T> theCompare;

		protected UniqueSortedDataFlowWrapper(AbstractDataFlow<E, ?, T> parent, Comparator<? super T> compare) {
			super(parent);
			theCompare = compare;
		}

		protected Comparator<? super T> getCompare() {
			return theCompare;
		}

		@Override
		public UniqueSortedDataFlow<E, T, T> filter(Function<? super T, String> filter) {
			return (UniqueSortedDataFlow<E, T, T>) super.filter(filter);
		}

		@Override
		public UniqueSortedDataFlow<E, T, T> filter(Function<? super T, String> filter, boolean filterNulls) {
			return new UniqueSortedDataFlowWrapper<>((AbstractDataFlow<E, ?, T>) getParent().filter(filter, filterNulls), theCompare);
		}

		@Override
		public UniqueSortedDataFlow<E, T, T> filterStatic(Function<? super T, String> filter) {
			return (UniqueSortedDataFlow<E, T, T>) super.filterStatic(filter);
		}

		@Override
		public UniqueSortedDataFlow<E, T, T> filterStatic(Function<? super T, String> filter, boolean filterNulls) {
			return new UniqueSortedDataFlowWrapper<>((AbstractDataFlow<E, ?, T>) getParent().filterStatic(filter, filterNulls), theCompare);
		}

		@Override
		public UniqueSortedDataFlow<E, T, T> refresh(Observable<?> refresh) {
			return new UniqueSortedDataFlowWrapper<>((AbstractDataFlow<E, ?, T>) getParent().refresh(refresh), theCompare);
		}

		@Override
		public UniqueSortedDataFlow<E, T, T> refreshEach(Function<? super T, ? extends Observable<?>> refresh) {
			return new UniqueSortedDataFlowWrapper<>((AbstractDataFlow<E, ?, T>) getParent().refreshEach(refresh), theCompare);
		}

		@Override
		public ObservableSortedSet<T> build() {
			return new ObservableSortedSetImpl.DerivedSortedSet<>((ObservableSortedSet<E>) getSource(), manageCollection(), theCompare);
		}
	}

	public static class UniqueSortedDataFlowImpl<E, T> extends UniqueSortedDataFlowWrapper<E, T> {
		private final boolean isAlwaysUsingFirst;

		protected UniqueSortedDataFlowImpl(AbstractDataFlow<E, ?, T> parent, Comparator<? super T> compare, boolean alwaysUseFirst) {
			super(parent, compare);
			isAlwaysUsingFirst = alwaysUseFirst;
		}

		@Override
		public CollectionManager<E, ?, T> manageCollection() {
			return new SortedManager<>(new UniqueManager<>(getParent().manageCollection(),
				Equivalence.of((Class<T>) getTargetType().getRawType(), getCompare(), true), isAlwaysUsingFirst), getCompare());
		}
	}

	public static class BaseCollectionDataFlow<E> extends AbstractDataFlow<E, E, E> {
		private final ObservableCollection<E> theSource;

		protected BaseCollectionDataFlow(ObservableCollection<E> source) {
			super(null, source.getType());
			theSource = source;
		}

		@Override
		protected ObservableCollection<E> getSource() {
			return theSource;
		}

		@Override
		public CollectionManager<E, ?, E> manageCollection() {
			return new BaseCollectionManager<>(theSource.getType(), theSource.equivalence(), theSource.isLockSupported());
		}

		@Override
		public ObservableCollection<E> build() {
			return theSource;
		}
	}

	public static class FilterOp<E, T> extends AbstractDataFlow<E, T, T> {
		private final Function<? super T, String> theFilter;
		private final boolean areNullsFiltered;

		protected FilterOp(AbstractDataFlow<E, ?, T> parent, Function<? super T, String> filter, boolean filterNulls) {
			super(parent, parent.getTargetType());
			theFilter = filter;
			areNullsFiltered = filterNulls;
		}

		@Override
		public CollectionManager<E, ?, T> manageCollection() {
			return new ObservableCollectionImpl.FilteredCollectionManager<>(getParent().manageCollection(), theFilter, areNullsFiltered);
		}
	}

	public static class StaticFilterOp<E, T> extends AbstractDataFlow<E, T, T> {
		private final Function<? super T, String> theFilter;
		private final boolean areNullsFiltered;

		protected StaticFilterOp(AbstractDataFlow<E, ?, T> parent, Function<? super T, String> filter, boolean filterNulls) {
			super(parent, parent.getTargetType());
			theFilter = filter;
			areNullsFiltered = filterNulls;
		}

		@Override
		public CollectionManager<E, ?, T> manageCollection() {
			return new ObservableCollectionImpl.StaticFilteredCollectionManager<>(getParent().manageCollection(), theFilter,
				areNullsFiltered);
		}
	}

	public static class EquivalenceSwitchOp<E, T> extends AbstractDataFlow<E, T, T> {
		private final Equivalence<? super T> theEquivalence;

		protected EquivalenceSwitchOp(AbstractDataFlow<E, ?, T> parent, Equivalence<? super T> equivalence) {
			super(parent, parent.getTargetType());
			theEquivalence = equivalence;
		}

		@Override
		public CollectionManager<E, ?, T> manageCollection() {
			class EquivalenceSwitchedCollectionManager extends CollectionManager<E, T, T> {
				EquivalenceSwitchedCollectionManager() {
					super(EquivalenceSwitchOp.this.getParent().manageCollection(), EquivalenceSwitchOp.this.getTargetType());
				}

				@Override
				public FilterMapResult<E, T> map(FilterMapResult<E, T> source) {
					return getParent().map(source);
				}

				@Override
				public FilterMapResult<T, E> reverse(FilterMapResult<T, E> dest) {
					return getParent().reverse(dest);
				}

				@Override
				public CollectionElementManager<E, ?, T> createElement(ElementId id, E init, Object cause) {
					return getParent().createElement(id, init, cause);
				}

				@Override
				protected void begin() {}
			}
			return new EquivalenceSwitchedCollectionManager();
		}
	}

	public static class MapOp<E, I, T> extends AbstractDataFlow<E, I, T> {
		private final Function<? super I, ? extends T> theMap;
		private final boolean areNullsMapped;
		private final Function<? super T, ? extends I> theReverse;
		private final ElementSetter<? super I, ? super T> theElementReverse;
		private final boolean areNullsReversed;
		private final boolean reEvalOnUpdate;
		private final boolean fireIfUnchanged;
		private final boolean isCached;

		protected MapOp(AbstractDataFlow<E, ?, I> parent, TypeToken<T> target, Function<? super I, ? extends T> map, boolean mapNulls,
			Function<? super T, ? extends I> reverse, ElementSetter<? super I, ? super T> elementReverse, boolean reverseNulls,
			boolean reEvalOnUpdate, boolean fireIfUnchanged, boolean cached) {
			super(parent, target);
			theMap = map;
			areNullsMapped = mapNulls;
			theReverse = reverse;
			theElementReverse = elementReverse;
			areNullsReversed = reverseNulls;
			this.reEvalOnUpdate = reEvalOnUpdate;
			this.fireIfUnchanged = fireIfUnchanged;
			isCached = cached;
		}

		@Override
		public CollectionManager<E, ?, T> manageCollection() {
			return new ObservableCollectionImpl.MappedCollectionManager<>(getParent().manageCollection(), getTargetType(), theMap,
				areNullsMapped, theReverse, theElementReverse, areNullsReversed, reEvalOnUpdate, fireIfUnchanged, isCached);
		}
	}

	public static class UniqueMapOp<E, I, T> extends MapOp<E, I, T> implements UniqueDataFlow<E, I, T> {
		protected UniqueMapOp(AbstractDataFlow<E, ?, I> parent, TypeToken<T> target, Function<? super I, ? extends T> map, boolean mapNulls,
			Function<? super T, ? extends I> reverse, ElementSetter<? super I, ? super T> elementReverse, boolean reverseNulls,
			boolean reEvalOnUpdate, boolean fireIfUnchanged, boolean isCached) {
			super(parent, target, map, mapNulls, reverse, elementReverse, reverseNulls, reEvalOnUpdate, fireIfUnchanged, isCached);
		}

		@Override
		public UniqueDataFlow<E, T, T> filter(Function<? super T, String> filter) {
			return (UniqueDataFlow<E, T, T>) super.filter(filter);
		}

		@Override
		public UniqueDataFlow<E, T, T> filter(Function<? super T, String> filter, boolean filterNulls) {
			return new UniqueDataFlowWrapper<>((AbstractDataFlow<E, ?, T>) super.filter(filter, filterNulls));
		}

		@Override
		public UniqueDataFlow<E, T, T> filterStatic(Function<? super T, String> filter) {
			return (UniqueDataFlow<E, T, T>) super.filterStatic(filter);
		}

		@Override
		public UniqueDataFlow<E, T, T> filterStatic(Function<? super T, String> filter, boolean filterNulls) {
			return new UniqueDataFlowWrapper<>((AbstractDataFlow<E, ?, T>) super.filterStatic(filter, filterNulls));
		}

		@Override
		public <X> UniqueMappedCollectionBuilder<E, T, X> mapEquivalent(TypeToken<X> target) {
			return new UniqueMappedCollectionBuilder<>(this, target);
		}

		@Override
		public UniqueDataFlow<E, T, T> refresh(Observable<?> refresh) {
			return new UniqueDataFlowWrapper<>((AbstractDataFlow<E, ?, T>) super.refresh(refresh));
		}

		@Override
		public UniqueDataFlow<E, T, T> refreshEach(Function<? super T, ? extends Observable<?>> refresh) {
			return new UniqueDataFlowWrapper<>((AbstractDataFlow<E, ?, T>) super.refreshEach(refresh));
		}

		@Override
		public ObservableSet<T> build() {
			return new ObservableSetImpl.DerivedSet<>((ObservableSet<E>) getSource(), manageCollection());
		}
	}

	/**
	 * Defines a combination of a single source collection with one or more observable values
	 *
	 * @param <E> The type of elements in the source collection
	 * @param <I> Intermediate type
	 * @param <T> The type of elements in the resulting collection
	 */
	public static class CombinedCollectionDef<E, I, T> extends AbstractDataFlow<E, I, T> {
		private final Map<ObservableValue<?>, Boolean> theArgs;
		private final Function<? super CombinedValues<? extends I>, ? extends T> theCombination;
		private final boolean combineNulls;
		private final Function<? super CombinedValues<? extends T>, ? extends I> theReverse;
		private final boolean reverseNulls;

		protected CombinedCollectionDef(AbstractDataFlow<E, ?, I> parent, TypeToken<T> target, Map<ObservableValue<?>, Boolean> args,
			Function<? super CombinedValues<? extends I>, ? extends T> combination, boolean combineNulls,
			Function<? super CombinedValues<? extends T>, ? extends I> reverse, boolean reverseNulls) {
			super(parent, target);
			theArgs = Collections.unmodifiableMap(args);
			theCombination = combination;
			this.combineNulls = combineNulls;
			theReverse = reverse;
			this.reverseNulls = reverseNulls;
		}

		@Override
		public CollectionManager<E, ?, T> manageCollection() {
			return new ObservableCollectionImpl.CombinedCollectionManager<>(getParent().manageCollection(), getTargetType(), theArgs,
				theCombination, combineNulls, theReverse, reverseNulls);
		}
	}

	public static abstract class AbstractCombinedCollectionBuilder<E, I, R> implements CombinedCollectionBuilder<E, I, R> {
		private final AbstractDataFlow<E, ?, I> theParent;
		private final TypeToken<R> theTargetType;
		private final LinkedHashMap<ObservableValue<?>, Ternian> theArgs;
		private Function<? super CombinedValues<? extends R>, ? extends I> theReverse;
		private boolean defaultCombineNulls = false;
		private boolean isReverseNulls = false;

		protected AbstractCombinedCollectionBuilder(AbstractDataFlow<E, ?, I> parent, TypeToken<R> targetType) {
			theParent = parent;
			theTargetType = targetType;
			theArgs = new LinkedHashMap<>();
		}

		protected void addArg(ObservableValue<?> arg, Ternian combineNulls) {
			if (theArgs.containsKey(arg))
				throw new IllegalArgumentException("Argument " + arg + " is already combined");
			theArgs.put(arg, combineNulls);
		}

		protected Ternian combineNulls(ObservableValue<?> arg) {
			return theArgs.get(arg);
		}

		protected AbstractDataFlow<E, ?, I> getParent() {
			return theParent;
		}

		protected TypeToken<R> getTargetType() {
			return theTargetType;
		}

		protected Function<? super CombinedValues<? extends R>, ? extends I> getReverse() {
			return theReverse;
		}

		protected boolean isDefaultCombineNulls() {
			return defaultCombineNulls;
		}

		protected boolean isReverseNulls() {
			return isReverseNulls;
		}

		public AbstractCombinedCollectionBuilder<E, I, R> combineNullsByDefault() {
			defaultCombineNulls = true;
			return this;
		}

		@Override
		public AbstractCombinedCollectionBuilder<E, I, R> withReverse(Function<? super CombinedValues<? extends R>, ? extends I> reverse,
			boolean reverseNulls) {
			theReverse = reverse;
			this.isReverseNulls = reverseNulls;
			return this;
		}

		@Override
		public CollectionDataFlow<E, I, R> build(Function<? super CombinedValues<? extends I>, ? extends R> combination,
			boolean combineNulls) {
			return new CombinedCollectionDef<>(theParent, theTargetType, getResultArgs(), combination, combineNulls, theReverse,
				isReverseNulls);
		}

		private Map<ObservableValue<?>, Boolean> getResultArgs() {
			Map<ObservableValue<?>, Boolean> result = new LinkedHashMap<>(theArgs.size() * 3 / 2);
			for (Map.Entry<ObservableValue<?>, Ternian> arg : theArgs.entrySet())
				result.put(arg.getKey(), arg.getValue().withDefault(defaultCombineNulls));
			return result;
		}
	}

	public static class RefreshOp<E, T> extends AbstractDataFlow<E, T, T> {
		private final Observable<?> theRefresh;

		protected RefreshOp(AbstractDataFlow<E, ?, T> parent, Observable<?> refresh) {
			super(parent, parent.getTargetType());
			theRefresh = refresh;
		}

		@Override
		public CollectionManager<E, ?, T> manageCollection() {
			return new ObservableCollectionImpl.RefreshingCollectionManager<>(getParent().manageCollection(), theRefresh);
		}
	}

	public static class ElementRefreshOp<E, T> extends AbstractDataFlow<E, T, T> {
		private final Function<? super T, ? extends Observable<?>> theElementRefresh;

		protected ElementRefreshOp(AbstractDataFlow<E, ?, T> parent, Function<? super T, ? extends Observable<?>> elementRefresh) {
			super(parent, parent.getTargetType());
			theElementRefresh = elementRefresh;
		}

		@Override
		public CollectionManager<E, ?, T> manageCollection() {
			return new ObservableCollectionImpl.ElementRefreshingCollectionManager<>(getParent().manageCollection(), theElementRefresh);
		}
	}

	public static abstract class CollectionManager<E, I, T> {
		private final CollectionManager<E, ?, I> theParent;
		private final TypeToken<T> theTargetType;
		private ReentrantReadWriteLock theLock;
		private Consumer<CollectionUpdate> theUpdateListener;
		private final List<Runnable> thePostChanges;

		protected CollectionManager(CollectionManager<E, ?, I> parent, TypeToken<T> targetType) {
			theParent = parent;
			theTargetType = targetType;
			theLock = theParent != null ? null : new ReentrantReadWriteLock();
			thePostChanges = new LinkedList<>();
		}

		private BaseCollectionManager<E> getBase() {
			if (this instanceof BaseCollectionManager)
				return (BaseCollectionManager<E>) this;
			else
				return theParent.getBase();
		}

		protected CollectionManager<E, ?, I> getParent() {
			return theParent;
		}

		protected Consumer<CollectionUpdate> getUpdateListener() {
			return theUpdateListener;
		}

		public Transaction lock(boolean write, Object cause) {
			if (theParent != null)
				return theParent.lock(write, cause);
			Lock lock = write ? theLock.writeLock() : theLock.readLock();
			lock.lock();
			return () -> lock.unlock();
		}

		public TypeToken<T> getTargetType() {
			return theTargetType;
		}

		public Equivalence<? super T> equivalence() {
			if (!isMapped())
				return (Equivalence<? super T>) getBase().equivalence();
			else
				return Equivalence.DEFAULT;
		}

		public boolean isDynamicallyFiltered() {
			return theParent == null ? false : theParent.isDynamicallyFiltered();
		}

		public boolean isStaticallyFiltered() {
			return theParent == null ? false : theParent.isStaticallyFiltered();
		}

		public boolean isMapped() {
			return theParent == null ? false : theParent.isMapped();
		}

		public boolean isReversible() {
			return theParent == null ? true : theParent.isReversible();
		}

		public boolean isSorted() {
			return theParent == null ? false : theParent.isSorted();
		}

		public abstract FilterMapResult<E, T> map(FilterMapResult<E, T> source);

		public FilterMapResult<E, T> map(E source) {
			return map(new FilterMapResult<>(source));
		}

		public abstract FilterMapResult<T, E> reverse(FilterMapResult<T, E> dest);

		public FilterMapResult<T, E> reverse(T dest) {
			return reverse(new FilterMapResult<>(dest));
		}

		public void postChange() {
			if (!thePostChanges.isEmpty()) {
				Runnable[] changes = thePostChanges.toArray(new Runnable[thePostChanges.size()]);
				thePostChanges.clear();
				for (Runnable postChange : changes)
					postChange.run();
			}
		}

		protected void postChange(Runnable run) {
			thePostChanges.add(run);
		}

		protected abstract void begin();

		public void begin(Consumer<CollectionUpdate> onUpdate) {
			if (theUpdateListener != null)
				throw new IllegalStateException("Cannot begin twice");
			theUpdateListener = onUpdate;
			begin();
			if (theParent != null)
				theParent.begin(onUpdate);
		}

		public abstract CollectionElementManager<E, ?, T> createElement(ElementId id, E init, Object cause);
	}

	public static class CollectionUpdate<E, I, T> {
		private final CollectionManager<E, I, T> theCollection;
		private final ElementId theElement;
		private final Object theCause;

		public CollectionUpdate(CollectionManager<E, I, T> collection, ElementId element, Object cause) {
			theCollection = collection;
			theElement = element;
			theCause = cause;
		}

		public CollectionManager<E, I, T> getCollection() {
			return theCollection;
		}

		public ElementId getElement() {
			return theElement;
		}

		public Object getCause() {
			return theCause;
		}
	}

	public static abstract class CollectionElementManager<E, I, T> implements Comparable<CollectionElementManager<E, ?, T>> {
		private final CollectionManager<E, I, T> theCollection;
		private final CollectionElementManager<E, ?, I> theParent;
		private final ElementId theId;

		protected CollectionElementManager(CollectionManager<E, I, T> collection, CollectionElementManager<E, ?, I> parent, ElementId id) {
			theCollection = collection;
			theParent = parent;
			theId = id;
		}

		protected CollectionElementManager<E, ?, I> getParent() {
			return theParent;
		}

		public ElementId getElementId() {
			return theId;
		}

		@Override
		public int compareTo(CollectionElementManager<E, ?, T> other) {
			if (getParent() == null)
				return theId.compareTo(other.theId);
			return getParent().compareTo(((CollectionElementManager<E, I, T>) other).getParent());
		}

		public boolean isPresent() {
			// Most elements don't filter
			return theParent.isPresent();
		}

		public abstract T get();

		public boolean set(E value, Object cause) {
			if (!getParent().set(value, cause))
				return false;
			return refresh(getParent().get(), cause);
		}

		public boolean isElementSettable() {
			return theParent.isElementSettable();
		}

		public String setElement(T newValue, boolean doSet, Object cause) {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		protected boolean applies(CollectionUpdate update) {
			return update instanceof CollectionUpdate && ((CollectionUpdate<?, ?, ?>) update).getCollection() == theCollection//
				&& (update.getElement() == null || update.getElement().equals(getElementId()));
		}

		public boolean update(CollectionUpdate update) {
			if (applies(update)) {
				return refresh(getParent().get(), update.getCause());
			} else if (getParent().update(update)) {
				return refresh(getParent().get(), update.getCause());
			} else
				return false;
		}

		protected abstract boolean refresh(I source, Object cause);

		public void remove(Object cause) {
			// Most elements don't need to do anything when they're removed
		}
	}

	public static abstract class NonMappingCollectionManager<E, T> extends CollectionManager<E, T, T> {
		protected NonMappingCollectionManager(CollectionManager<E, ?, T> parent) {
			super(parent, parent.getTargetType());
		}

		@Override
		public FilterMapResult<E, T> map(FilterMapResult<E, T> source) {
			return getParent().map(source);
		}

		@Override
		public FilterMapResult<T, E> reverse(FilterMapResult<T, E> dest) {
			return getParent().reverse(dest);
		}
	}

	public static abstract class NonMappingCollectionElement<E, T> extends CollectionElementManager<E, T, T> {
		protected NonMappingCollectionElement(NonMappingCollectionManager<E, T> collection, CollectionElementManager<E, ?, T> parent,
			ElementId id) {
			super(collection, parent, id);
		}

		@Override
		public String setElement(T newValue, boolean doSet, Object cause) {
			return getParent().setElement(newValue, doSet, cause);
		}
	}

	public static class BaseCollectionManager<E> extends CollectionManager<E, E, E> {
		private final Equivalence<? super E> theEquivalence;
		private final ReentrantReadWriteLock theLock;

		public BaseCollectionManager(TypeToken<E> targetType, Equivalence<? super E> equivalence, boolean threadSafe) {
			super(null, targetType);
			theEquivalence = equivalence;
			theLock = threadSafe ? new ReentrantReadWriteLock() : null;
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			if (theLock == null)
				return Transaction.NONE;
			Lock lock = write ? theLock.writeLock() : theLock.readLock();
			lock.lock();
			return () -> lock.unlock();
		}

		@Override
		public Equivalence<? super E> equivalence() {
			return theEquivalence;
		}

		@Override
		public FilterMapResult<E, E> map(FilterMapResult<E, E> source) {
			source.result = source.source;
			return source;
		}

		@Override
		public FilterMapResult<E, E> reverse(FilterMapResult<E, E> dest) {
			dest.result = dest.source;
			return dest;
		}

		@Override
		public CollectionElementManager<E, ?, E> createElement(ElementId id, E init, Object cause) {
			class DefaultElement extends CollectionElementManager<E, E, E> {
				private E theValue;

				protected DefaultElement() {
					super(BaseCollectionManager.this, null, id);
				}

				@Override
				public E get() {
					return theValue;
				}

				@Override
				public boolean set(E value, Object cause) {
					theValue = value;
					return true;
				}

				@Override
				public boolean update(CollectionUpdate update) {
					return false;
				}

				@Override
				protected boolean refresh(E source, Object cause) {
					// Never called
					return true;
				}
			}
			return new DefaultElement();
		}

		@Override
		public void begin() {}
	}

	public static class UniqueManager<E, T> extends NonMappingCollectionManager<E, T> {
		private final Equivalence<? super T> theEquivalence;
		private final UpdatableMap<T, DefaultTreeSet<UniqueElement>> theElementsByValue;
		private final boolean isAlwaysUsingFirst;

		protected UniqueManager(CollectionManager<E, ?, T> parent, Equivalence<? super T> equivalence, boolean alwaysUseFirst) {
			super(parent);
			theEquivalence = equivalence;
			theElementsByValue = theEquivalence.createMap();
			isAlwaysUsingFirst = alwaysUseFirst;
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return theEquivalence;
		}

		@Override
		protected void begin() {}

		@Override
		public CollectionElementManager<E, ?, T> createElement(ElementId id, E init, Object cause) {
			return new UniqueElement(id, init, cause);
		}

		void update(UniqueElement element, Object cause) {
			getUpdateListener().accept(new CollectionUpdate<>(this, element.getElementId(), cause));
		}

		class UniqueElement extends NonMappingCollectionElement<E, T> {
			private T theValue;
			private boolean isPresent;
			private DefaultTreeSet<UniqueElement> theValueElements;
			private DefaultNode<UniqueElement> theNode;

			protected UniqueElement(ElementId id, E init, Object cause) {
				super(UniqueManager.this, UniqueManager.this.getParent().createElement(id, init, cause), id);

				T value = getParent().get();
				theValue = value;
				addToSet(cause);
			}

			@Override
			public boolean isPresent() {
				return isPresent;
			}

			@Override
			public T get() {
				return theValue;
			}

			@Override
			protected boolean refresh(T source, Object cause) {
				if (theElementsByValue.get(source) != theValueElements) {
					removeFromSet(cause);
					theValue = source;
					addToSet(cause);
				}
				return true;
			}

			@Override
			public void remove(Object cause) {
				super.remove(cause);
				removeFromSet(cause);
			}

			private void removeFromSet(Object cause) {
				theValueElements.removeNode(theNode);
				theNode = null;
				if (theValueElements.isEmpty())
					theElementsByValue.remove(theValue);
				else {
					if (isPresent) {
						// We're the first value
						isPresent = false;
						UniqueElement newFirst = theValueElements.first();
						// Delay installing the new value this node has been reported removed so the set is always unique
						postChange(() -> {
							newFirst.isPresent = true;
							UniqueManager.this.update(newFirst, cause);
						});
					}
					theValueElements = null; // Other elements are using that set, so we can't re-use it
				}
			}

			private void addToSet(Object cause) {
				theValueElements = theElementsByValue.computeIfAbsent(theValue, v -> new DefaultTreeSet<>(UniqueElement::compareTo));
				// Grab our node, since we can use it to remove even if the comparison properties change
				theNode = theValueElements.addGetNode(this);
				if (theValueElements.size() == 1) {
					// We're currently the only element for the value
					isPresent = true;
				} else if (isAlwaysUsingFirst && theNode.getIndex() == 0) {
					isPresent = true;
					// We're replacing the existing representative for the value
					UniqueElement replaced = theValueElements.higher(this);
					// Remove the replaced node before this one is installed so the set is always unique
					replaced.isPresent = false;
					UniqueManager.this.update(replaced, cause);
				} else {
					// There are other elements for the value that we will not replace
					isPresent = false;
				}
			}
		}
	}

	public static class SortedManager<E, T> extends NonMappingCollectionManager<E, T> {
		private final Comparator<? super T> theCompare;

		protected SortedManager(CollectionManager<E, ?, T> parent, Comparator<? super T> compare) {
			super(parent);
			theCompare = compare;
		}

		@Override
		protected void begin() {}

		@Override
		public CollectionElementManager<E, ?, T> createElement(ElementId id, E init, Object cause) {
			class SortedElement extends NonMappingCollectionElement<E, T> {
				SortedElement() {
					super(SortedManager.this, SortedManager.this.getParent().createElement(id, init, cause), id);
				}

				@Override
				public T get() {
					return getParent().get();
				}

				@Override
				protected boolean refresh(T source, Object cause) {
					return true;
				}

				@Override
				public int compareTo(CollectionElementManager<E, ?, T> other) {
					int compare = theCompare.compare(get(), other.get());
					if (compare != 0)
						return compare;
					return super.compareTo(other);
				}
			}
			return new SortedElement();
		}
	}

	public static class FilteredCollectionManager<E, T> extends NonMappingCollectionManager<E, T> {
		private final Function<? super T, String> theFilter;
		private final boolean filterNulls;

		protected FilteredCollectionManager(CollectionManager<E, ?, T> parent, Function<? super T, String> filter, boolean filterNulls) {
			super(parent);
			theFilter = filter;
			this.filterNulls = filterNulls;
		}

		@Override
		public boolean isDynamicallyFiltered() {
			return true;
		}

		@Override
		public FilterMapResult<E, T> map(FilterMapResult<E, T> source) {
			getParent().map(source);
			String error;
			if (!filterNulls && source.result == null)
				error = StdMsg.NULL_DISALLOWED;
			else
				error = theFilter.apply(source.result);
			if (error != null) {
				source.result = null;
				source.error = error;
			}
			return source;
		}

		@Override
		public FilterMapResult<T, E> reverse(FilterMapResult<T, E> dest) {
			String error;
			if (!filterNulls && dest.source == null)
				error = StdMsg.NULL_DISALLOWED;
			else
				error = theFilter.apply(dest.source);
			if (error != null)
				dest.error = error;
			else
				getParent().reverse(dest);
			return dest;
		}

		@Override
		public void begin() {}

		@Override
		public NonMappingCollectionElement<E, T> createElement(ElementId id, E init, Object cause) {
			class FilteredElement extends NonMappingCollectionElement<E, T> {
				private boolean isPresent;

				protected FilteredElement() {
					super(FilteredCollectionManager.this, FilteredCollectionManager.this.getParent().createElement(id, init, cause), id);
				}

				@Override
				public boolean isPresent() {
					return isPresent && super.isPresent();
				}

				@Override
				public T get() {
					return getParent().get();
				}

				@Override
				public String setElement(T newValue, boolean doSet, Object cause) {
					return getParent().setElement(newValue, doSet, cause);
				}

				@Override
				protected boolean refresh(T source, Object cause) {
					if (source == null && !filterNulls)
						isPresent = false;
					else
						isPresent = theFilter.apply(source) == null;
					return true;
				}
			}
			return new FilteredElement();
		}
	}

	public static class StaticFilteredCollectionManager<E, T> extends NonMappingCollectionManager<E, T> {
		private final Function<? super T, String> theFilter;
		private final boolean filterNulls;

		protected StaticFilteredCollectionManager(CollectionManager<E, ?, T> parent, Function<? super T, String> filter,
			boolean filterNulls) {
			super(parent);
			theFilter = filter;
			this.filterNulls = filterNulls;
		}

		@Override
		public boolean isStaticallyFiltered() {
			return true;
		}

		@Override
		public FilterMapResult<E, T> map(FilterMapResult<E, T> source) {
			getParent().map(source);
			String error;
			if (!filterNulls && source.result == null)
				error = StdMsg.NULL_DISALLOWED;
			else
				error = theFilter.apply(source.result);
			if (error != null) {
				source.result = null;
				source.error = error;
			}
			return source;
		}

		@Override
		public FilterMapResult<T, E> reverse(FilterMapResult<T, E> dest) {
			String error;
			if (!filterNulls && dest.source == null)
				error = StdMsg.NULL_DISALLOWED;
			else
				error = theFilter.apply(dest.source);
			if (error != null)
				dest.error = error;
			else
				getParent().reverse(dest);
			return dest;
		}

		@Override
		public void begin() {}

		@Override
		public CollectionElementManager<E, ?, T> createElement(ElementId id, E init, Object cause) {
			CollectionElementManager<E, ?, T> parentElement = getParent().createElement(id, init, cause);
			T value = parentElement.get();
			if (value == null && !filterNulls)
				return null;
			else if (theFilter.apply(value) != null)
				return null;
			else
				return parentElement;
		}
	}

	public static class MappedCollectionManager<E, I, T> extends CollectionManager<E, I, T> {
		private final Function<? super I, ? extends T> theMap;
		private final boolean areNullsMapped;
		private final Function<? super T, ? extends I> theReverse;
		private final ElementSetter<? super I, ? super T> theElementReverse;
		private final boolean areNullsReversed;
		private final boolean reEvalOnUpdate;
		private final boolean fireIfUnchanged;
		private final boolean isCached;

		protected MappedCollectionManager(CollectionManager<E, ?, I> parent, TypeToken<T> targetType, Function<? super I, ? extends T> map,
			boolean areNullsMapped, Function<? super T, ? extends I> reverse, ElementSetter<? super I, ? super T> elementReverse,
			boolean areNullsReversed, boolean reEvalOnUpdate, boolean fireIfUnchanged, boolean cached) {
			super(parent, targetType);
			theMap = map;
			this.areNullsMapped = areNullsMapped;
			theReverse = reverse;
			theElementReverse = elementReverse;
			this.areNullsReversed = areNullsReversed;
			this.reEvalOnUpdate = reEvalOnUpdate;
			this.fireIfUnchanged = fireIfUnchanged;
			isCached = cached;
		}

		@Override
		public boolean isMapped() {
			return true;
		}

		@Override
		public boolean isReversible() {
			return theReverse != null && getParent().isReversible();
		}

		@Override
		public FilterMapResult<E, T> map(FilterMapResult<E, T> source) {
			FilterMapResult<E, I> intermediate = (FilterMapResult<E, I>) source;
			getParent().map(intermediate);
			if (intermediate.result == null && !areNullsMapped)
				source.result = null;
			else
				source.result = theMap.apply(intermediate.result);
			return source;
		}

		@Override
		public FilterMapResult<T, E> reverse(FilterMapResult<T, E> dest) {
			if (!isReversible())
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			FilterMapResult<I, E> intermediate = (FilterMapResult<I, E>) dest;
			if (dest.source == null && !areNullsReversed)
				intermediate.source = null;
			else
				intermediate.source = theReverse.apply(dest.source);

			if (intermediate.source == null && !areNullsMapped)
				dest.error = StdMsg.ILLEGAL_ELEMENT;
			else
				getParent().reverse(intermediate);
			return dest;
		}

		@Override
		public CollectionElementManager<E, I, T> createElement(ElementId id, E init, Object cause) {
			class MappedElement extends CollectionElementManager<E, I, T> {
				private I theSource;
				private T theValue;
				private boolean isInitial;

				MappedElement() {
					super(MappedCollectionManager.this, MappedCollectionManager.this.getParent().createElement(id, init, cause), id);
				}

				@Override
				public T get() {
					if (isCached)
						return theValue;
					else
						return theMap.apply(getParent().get());
				}

				@Override
				public boolean isElementSettable() {
					return theElementReverse != null || (theReverse != null && getParent().isElementSettable());
				}

				@Override
				public String setElement(T newValue, boolean doSet, Object cause) {
					String msg = null;
					if (theElementReverse != null) {
						msg = theElementReverse.setElement(getParent().get(), newValue, doSet, cause);
						if (msg == null)
							return null;
					}
					if (theReverse != null) {
						I intermediate;
						if (newValue == null && !areNullsReversed)
							msg = getParent().setElement(null, doSet, cause);
						else
							msg = getParent().setElement(theReverse.apply(newValue), doSet, cause);
					} else
						msg = StdMsg.UNSUPPORTED_OPERATION;
					return msg;
				}

				@Override
				protected boolean refresh(I source, Object cause) {
					if (!isCached)
						return true;
					if (!reEvalOnUpdate && source == theSource) {
						if (isInitial)
							isInitial = false;
						else
							return fireIfUnchanged;
					}
					theSource = source;
					T newValue;
					if (source == null && !areNullsMapped)
						newValue = null;
					else
						newValue = theMap.apply(source);
					if (!fireIfUnchanged && newValue == theValue)
						return false;
					theValue = newValue;
					return true;
				}
			}
			return new MappedElement();
		}

		@Override
		protected void begin() {}
	}

	public static class CombinedCollectionManager<E, I, T> extends CollectionManager<E, I, T> {
		private static class ArgHolder<T> {
			final boolean combineNull;
			Consumer<ObservableValueEvent<?>> action;
			T value;

			ArgHolder(boolean combineNull) {
				this.combineNull = combineNull;
			}
		}
		private final Map<ObservableValue<?>, ArgHolder<?>> theArgs;
		private final Function<? super CombinedValues<? extends I>, ? extends T> theCombination;
		private final boolean combineNulls;
		private final Function<? super CombinedValues<? extends T>, ? extends I> theReverse;
		private final boolean reverseNulls;

		/** The number of combined values which must be non-null to be passed to the combination function but are null */
		private int isCombining;

		protected CombinedCollectionManager(CollectionManager<E, ?, I> parent, TypeToken<T> targetType,
			Map<ObservableValue<?>, Boolean> args, Function<? super CombinedValues<? extends I>, ? extends T> combination,
			boolean combineNulls, Function<? super CombinedValues<? extends T>, ? extends I> reverse, boolean reverseNulls) {
			super(parent, targetType);
			theArgs = new HashMap<>();
			for (Map.Entry<ObservableValue<?>, Boolean> arg : args.entrySet())
				theArgs.put(arg.getKey(), new ArgHolder<>(arg.getValue()));
			theCombination = combination;
			this.combineNulls = combineNulls;
			theReverse = reverse;
			this.reverseNulls = reverseNulls;
		}

		@Override
		public boolean isMapped() {
			return true;
		}

		@Override
		public boolean isReversible() {
			return theReverse != null && super.isReversible();
		}

		@Override
		public FilterMapResult<E, T> map(FilterMapResult<E, T> source) {
			if (getParent() != null)
				getParent().map((FilterMapResult<E, I>) source);
			if (source.error != null)
				return source;
			if (isCombining > 0 || (source.source == null && !combineNulls)) {
				source.result = null;
				return source;
			}

			source.result = theCombination.apply(new CombinedValues<I>() {
				@Override
				public I getElement() {
					return (I) source.result;
				}

				@Override
				public <V> V get(ObservableValue<V> arg) {
					ArgHolder<V> holder = (ArgHolder<V>) theArgs.get(arg);
					if (holder == null)
						throw new IllegalArgumentException("Unrecognized value: " + arg);
					return holder.value;
				}
			});
			return source;
		}

		@Override
		public FilterMapResult<T, E> reverse(FilterMapResult<T, E> dest) {
			if (theReverse == null)
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);

			if (isCombining > 0 || (dest.source == null && !reverseNulls)) {
				if (!combineNulls)
					dest.error = StdMsg.NULL_DISALLOWED;
				else
					dest.result = null;
				return dest;
			}

			FilterMapResult<I, E> intermediate = (FilterMapResult<I, E>) dest;
			intermediate.source = theReverse.apply(new CombinedValues<T>() {
				@Override
				public T getElement() {
					return dest.source;
				}

				@Override
				public <V> V get(ObservableValue<V> arg) {
					ArgHolder<V> holder = (ArgHolder<V>) theArgs.get(arg);
					if (holder == null)
						throw new IllegalArgumentException("Unrecognized value: " + arg);
					return holder.value;
				}
			});
			if (getParent() != null)
				getParent().reverse(intermediate);
			return dest;
		}

		@Override
		public CollectionElementManager<E, I, T> createElement(ElementId id, E init, Object cause) {
			class CombinedCollectionElement extends CollectionElementManager<E, I, T> implements CombinedValues<I> {
				private T theValue;
				private I theSource;

				CombinedCollectionElement() {
					super(CombinedCollectionManager.this, CombinedCollectionManager.this.getParent().createElement(id, init, cause), id);
				}

				@Override
				public T get() {
					return theValue;
				}

				@Override
				public boolean isElementSettable() {
					return theReverse != null && super.isElementSettable();
				}

				@Override
				public String setElement(T newValue, boolean doSet, Object cause) {
					if (theReverse == null || !isElementSettable())
						return super.setElement(newValue, doSet, cause);
					else if (isCombining > 0 || (newValue == null && !reverseNulls)) {
						if (!combineNulls)
							return StdMsg.NULL_DISALLOWED;
						else
							return getParent().setElement(null, doSet, cause);
					} else
						return getParent().setElement(theReverse.apply(new CombinedValues<T>() {
							@Override
							public T getElement() {
								return newValue;
							}

							@Override
							public <V> V get(ObservableValue<V> arg) {
								ArgHolder<V> holder = (ArgHolder<V>) theArgs.get(arg);
								if (holder == null)
									throw new IllegalArgumentException("Unrecognized value: " + arg);
								return holder.value;
							}
						}), doSet, cause);
				}

				@Override
				protected boolean refresh(I source, Object cause) {
					if (isCombining > 0 || (source == null && !combineNulls))
						theValue = null;
					else {
						theSource = source;
						theValue = theCombination.apply(this);
						theSource = null;
					}
					return true;
				}

				@Override
				public I getElement() {
					return theSource;
				}

				@Override
				public <V> V get(ObservableValue<V> arg) {
					ArgHolder<V> holder = (ArgHolder<V>) theArgs.get(arg);
					if (holder == null)
						throw new IllegalArgumentException("Unrecognized value: " + arg);
					return holder.value;
				}
			}
			return new CombinedCollectionElement();
		}

		@Override
		public void begin() {
			for (Map.Entry<ObservableValue<?>, ArgHolder<?>> arg : theArgs.entrySet()) {
				ArgHolder<?> holder = arg.getValue();
				holder.action = evt -> {
					try (Transaction t = lock(true, null)) {
						if (!holder.combineNull && holder.value == null)
							isCombining--;
						((ArgHolder<Object>) holder).value = evt.getValue();
						if (!holder.combineNull && holder.value == null)
							isCombining++;
						getUpdateListener().accept(new CollectionUpdate<>(this, null, evt));
					}
				};
				WeakConsumer<ObservableValueEvent<?>> weak = new WeakConsumer<>(holder.action);
				weak.withSubscription(arg.getKey().act(weak));
			}
		}
	}

	public static class RefreshingCollectionManager<E, T> extends NonMappingCollectionManager<E, T> {
		private final Observable<?> theRefresh;
		private Consumer<Object> theAction;

		protected RefreshingCollectionManager(CollectionManager<E, ?, T> parent, Observable<?> refresh) {
			super(parent);
			theRefresh = refresh;
		}

		@Override
		public FilterMapResult<E, T> map(FilterMapResult<E, T> source) {
			return getParent().map(source);
		}

		@Override
		public FilterMapResult<T, E> reverse(FilterMapResult<T, E> dest) {
			return getParent().reverse(dest);
		}

		@Override
		public void begin() {
			theAction = v -> getUpdateListener().accept(new CollectionUpdate<>(this, null, v));
			WeakConsumer<Object> weak = new WeakConsumer<>(theAction);
			weak.withSubscription(theRefresh.act(weak));
		}

		@Override
		public CollectionElementManager<E, ?, T> createElement(ElementId id, E init, Object cause) {
			class RefreshingElement extends NonMappingCollectionElement<E, T> {
				public RefreshingElement() {
					super(RefreshingCollectionManager.this, RefreshingCollectionManager.this.getParent().createElement(id, init, cause),
						id);
				}

				@Override
				public T get() {
					return getParent().get();
				}

				@Override
				protected boolean refresh(T source, Object cause) {
					return true;
				}
			}
			return new RefreshingElement();
		}
	}

	public static class ElementRefreshingCollectionManager<E, T> extends NonMappingCollectionManager<E, T> {
		private static class RefreshHolder {
			final Observable<?> theRefresh;
			Consumer<Object> theAction;
			Subscription theSub;
			int theElementCount;

			RefreshHolder(Observable<?> refresh, Consumer<Object> action, Subscription sub) {
				theRefresh = refresh;
				theAction = action;
				theSub = sub;
			}
		}

		private final Function<? super T, ? extends Observable<?>> theRefresh;
		private final Map<Observable<?>, RefreshHolder> theRefreshObservables;

		protected ElementRefreshingCollectionManager(CollectionManager<E, ?, T> parent,
			Function<? super T, ? extends Observable<?>> refresh) {
			super(parent);
			theRefresh = refresh;
			theRefreshObservables = new IdentityHashMap<>();
		}

		@Override
		public FilterMapResult<E, T> map(FilterMapResult<E, T> source) {
			return getParent().map(source);
		}

		@Override
		public FilterMapResult<T, E> reverse(FilterMapResult<T, E> dest) {
			return getParent().reverse(dest);
		}

		@Override
		public CollectionElementManager<E, ?, T> createElement(ElementId id, E init, Object cause) {
			class ElementRefreshElement extends NonMappingCollectionElement<E, T> {
				private Observable<?> theRefreshObservable;

				protected ElementRefreshElement() {
					super(ElementRefreshingCollectionManager.this,
						ElementRefreshingCollectionManager.this.getParent().createElement(id, init, cause), id);
				}

				@Override
				public T get() {
					return getParent().get();
				}

				@Override
				public void remove(Object cause) {
					if (theRefreshObservable != null)
						removeRefreshObs(theRefreshObservable);
					super.remove(cause);
				}

				@Override
				protected boolean applies(CollectionUpdate update) {
					return super.applies(update) && ((ElementRefreshUpdate) update).getRefreshObs() == theRefreshObservable;
				}

				@Override
				public boolean update(CollectionUpdate update) {
					if (applies(update)) {
						refresh(getParent().get(), update.getCause());
						return true;
					} else if (getParent().update(update)) {
						T value = getParent().get();
						Observable<?> refreshObs = theRefresh.apply(value);
						if (theRefreshObservable != refreshObs) {
							removeRefreshObs(theRefreshObservable);
							newRefreshObs(refreshObs);
						}
						refresh(value, update.getCause());
						return true;
					} else
						return false;
				}

				@Override
				protected boolean refresh(T source, Object cause) {
					return true;
				}

				private void removeRefreshObs(Observable<?> refreshObs) {
					if (refreshObs != null) {
						RefreshHolder holder = theRefreshObservables.get(refreshObs);
						holder.theElementCount--;
						if (holder.theElementCount == 0) {
							theRefreshObservables.remove(refreshObs);
							holder.theSub.unsubscribe();
						}
					}
				}

				private void newRefreshObs(Observable<?> refreshObs) {
					if (refreshObs != null)
						theRefreshObservables.computeIfAbsent(refreshObs, this::createHolder).theElementCount++;
					theRefreshObservable = refreshObs;
				}

				private RefreshHolder createHolder(Observable<?> refreshObs) {
					Consumer<Object> action = v -> ElementRefreshingCollectionManager.this.update(refreshObs, v);
					WeakConsumer<Object> weak = new WeakConsumer<>(action);
					Subscription sub = refreshObs.act(weak);
					weak.withSubscription(sub);
					return new RefreshHolder(refreshObs, action, sub);
				}
			}
			return new ElementRefreshElement();
		}

		private void update(Observable<?> refreshObs, Object cause) {
			getUpdateListener().accept(new ElementRefreshUpdate(refreshObs, cause));
		}

		@Override
		public void begin() {}

		private class ElementRefreshUpdate extends CollectionUpdate<E, T, T> {
			private final Observable<?> theRefreshObs;

			ElementRefreshUpdate(Observable<?> refreshObs, Object cause) {
				super(ElementRefreshingCollectionManager.this, null, cause);
				theRefreshObs = refreshObs;
			}

			Observable<?> getRefreshObs() {
				return theRefreshObs;
			}
		}
	}

	public static class DerivedIterable<E, T> implements ReversibleIterable<T> {
		private final ObservableCollection<E> theSource;
		private final CollectionManager<E, ?, T> theManager;

		public DerivedIterable(ObservableCollection<E> source, CollectionManager<E, ?, T> manager) {
			theSource = source;
			theManager = manager;
		}

		private MutableObservableSpliteratorMap<E, T> map() {
			return new MutableObservableSpliteratorMap<E, T>() {
				@Override
				public TypeToken<T> getType() {
					return theManager.getTargetType();
				}

				@Override
				public T map(E value) {
					FilterMapResult<E, T> res = theManager.map(value);
					if (res.error != null)
						throw new IllegalArgumentException(res.error);
					return res.result;
				}

				@Override
				public boolean test(E srcValue) {
					return theManager.map(srcValue).error == null;
				}

				@Override
				public E reverse(T value) {
					FilterMapResult<T, E> res = theManager.reverse(value);
					if (res.error != null)
						throw new IllegalArgumentException(res.error);
					return res.result;
				}

				@Override
				public String filterEnabled(CollectionElement<E> el) {
					if (!theManager.isReversible())
						return StdMsg.UNSUPPORTED_OPERATION;
					return null;
				}

				@Override
				public String filterAccept(T value) {
					FilterMapResult<T, E> res = theManager.reverse(value);
					return res.error;
				}

				@Override
				public String filterRemove(CollectionElement<E> sourceEl) {
					return null;
				}
			};
		}

		@Override
		public ReversibleElementSpliterator<T> mutableSpliterator(boolean fromStart) {
			int todo = todo;// TODO!!! Need to handle sorting. How??? And uniqueness
			return theSource.mutableSpliterator(fromStart).map(map());
		}
	}

	public static class DerivedCollection<E, T> implements ObservableCollection<T> {
		int todo = todo;// TODO!!! Need to handle sorting
		// Change thePresentElements to a sorted set of elements.
		// Change theElements to have a structure for values holding the element manager and a link to
		// the node in thePresentElements
		// Whenever a value is updated, check to make sure the element's node is greater than its predecessor, less than its successor.
		// If it's not remove it and re-add it, with accompanying events
		private final ObservableCollection<E> theSource;
		private final CollectionManager<E, ?, T> theFlow;
		private final DefaultTreeMap<ElementId, CollectionElementManager<E, ?, T>> theElements;
		private final DefaultTreeMap<ElementId, CollectionElementManager<E, ?, T>> thePresentElements;
		private final boolean isFiltered;
		private final Consumer<ObservableCollectionEvent<? extends E>> theSourceAction;
		private final LinkedQueue<Consumer<? super ObservableCollectionEvent<? extends T>>> theListeners;
		private final Equivalence<? super T> theEquivalence;

		private int theSize;

		public DerivedCollection(ObservableCollection<E> source, CollectionManager<E, ?, T> flow) {
			theSource = source;
			theFlow = flow;
			isFiltered = theFlow.isDynamicallyFiltered();
			theElements = new DefaultTreeMap<>(ElementId::compareTo);
			thePresentElements = isFiltered ? new DefaultTreeMap<>(ElementId::compareTo) : null;
			theListeners = new LinkedQueue<>();
			theEquivalence = flow.equivalence();

			Consumer<ObservableCollectionEvent<? extends T>> fireListeners = f -> {
				switch (f.getType()) {
				case add:
					theSize++;
					thePresentElements.put(f.getElementId(), theElements.get(f.getElementId()));
					break;
				case remove:
					theSize--;
					thePresentElements.remove(f.getElementId());
					break;
				default:
				}
				for (Consumer<? super ObservableCollectionEvent<? extends T>> listener : theListeners)
					listener.accept(f);
			};
			theSourceAction = evt -> {
				CollectionElementManager<E, ?, T> elMgr;
				DefaultNode<Map.Entry<ElementId, CollectionElementManager<E, ?, T>>> node;
				ObservableCollectionEvent<T> toFire = null;
				T value;
				try (Transaction t = theFlow.lock(false, null)) {
					switch (evt.getType()) {
					case add:
						elMgr = theFlow.createElement(evt.getElementId(), evt.getNewValue(), evt);
						if (elMgr == null)
							return; // Statically filtered out
						value = elMgr.get();
						node = theElements.putGetNode(evt.getElementId(), elMgr);
						if (isFiltered) {
							if (elMgr.isPresent())
								toFire = new ObservableCollectionEvent<>(evt.getElementId(), CollectionChangeType.add, null, value, evt);
						} else {
							toFire = new IndexedCollectionEvent<>(evt.getElementId(), node.getIndex(), CollectionChangeType.add, null,
								value, evt);
						}
						break;
					case remove:
						node = theElements.getNode(evt.getElementId());
						if (node == null)
							return; // Statically filtered out
						elMgr = node.getValue().getValue();
						int index = node.getIndex();
						theElements.removeNode(node);
						if (isFiltered) {
							if (elMgr.isPresent())
								toFire = new ObservableCollectionEvent<>(evt.getElementId(), CollectionChangeType.remove,
									value = elMgr.get(), value, evt);
						} else {
							toFire = new IndexedCollectionEvent<>(evt.getElementId(), index, CollectionChangeType.remove,
								value = elMgr.get(), value, evt);
						}
						elMgr.remove(evt);
						break;
					case set:
						node = theElements.getNode(evt.getElementId());
						if (node == null)
							return; // Statically filtered out
						elMgr = node.getValue().getValue();
						index = node.getIndex();
						boolean prePresent = elMgr.isPresent();
						T oldValue = prePresent ? elMgr.get() : null;
						elMgr.set(evt.getNewValue(), evt);
						boolean present = elMgr.isPresent();
						value = present ? elMgr.get() : null;
						if (isFiltered) {
							if (elMgr.isPresent()) {
								if (prePresent)
									toFire = new ObservableCollectionEvent<>(evt.getElementId(), CollectionChangeType.set, oldValue, value,
										evt);
								else
									toFire = new ObservableCollectionEvent<>(evt.getElementId(), CollectionChangeType.add, null, value,
										evt);
							} else if (prePresent)
								toFire = new ObservableCollectionEvent<>(evt.getElementId(), CollectionChangeType.remove, oldValue,
									oldValue, evt);
						} else
							toFire = new IndexedCollectionEvent<>(evt.getElementId(), index, CollectionChangeType.set, oldValue, value,
								evt);
						break;
					}
					if (toFire != null)
						ObservableCollectionEvent.doWith(toFire, fireListeners);
					theFlow.postChange();
				}
			};

			// Begin listening
			try (Transaction t = theFlow.lock(false, null)) {
				theFlow.begin(update -> {
					try (Transaction collT = theSource.lock(false, update.getCause())) {
						int index = 0;
						if (update.getElement() != null) {
							DefaultNode<Map.Entry<ElementId, CollectionElementManager<E, ?, T>>> node = theElements
								.getNode(update.getElement());
							applyUpdate(node.getValue().getValue(), update, node.getIndex());
						} else {
							Iterator<DefaultNode<Map.Entry<ElementId, CollectionElementManager<E, ?, T>>>> nodeIter = theElements
								.nodeIterator();
							while (nodeIter.hasNext()) {
								ObservableCollectionEvent<T> toFire = applyUpdate(nodeIter.next().getValue().getValue(), update, index);
								if (toFire != null)
									ObservableCollectionEvent.doWith(toFire, fireListeners);
								index++;
							}
						}
					}
					theFlow.postChange();
				});
				WeakConsumer<ObservableCollectionEvent<? extends E>> weak = new WeakConsumer<>(theSourceAction);
				weak.withSubscription(theSource.subscribe(weak));
			}
		}

		protected ObservableCollection<E> getSource() {
			return theSource;
		}

		protected CollectionManager<E, ?, T> getFlow() {
			return theFlow;
		}

		protected boolean isFiltered() {
			return isFiltered;
		}

		protected DefaultTreeMap<ElementId, CollectionElementManager<E, ?, T>> getElements() {
			return theElements;
		}

		protected DefaultTreeMap<ElementId, CollectionElementManager<E, ?, T>> getPresentElements() {
			return isFiltered ? thePresentElements : theElements;
		}

		private ObservableCollectionEvent<T> applyUpdate(CollectionElementManager<E, ?, T> elMgr, CollectionUpdate update, int index) {
			boolean prePresent = elMgr.isPresent();
			T oldValue = prePresent ? elMgr.get() : null;
			ObservableCollectionEvent<T> toFire = null;
			if (elMgr.update(update)) {
				ElementId id = elMgr.getElementId();
				boolean present = elMgr.isPresent();
				T newValue = present ? elMgr.get() : null;
				if (isFiltered) {
					if (present) {
						if (prePresent)
							toFire = new ObservableCollectionEvent<>(id, CollectionChangeType.set, oldValue, newValue, update.getCause());
						else
							toFire = new ObservableCollectionEvent<>(id, CollectionChangeType.add, null, newValue, update.getCause());
					} else if (prePresent)
						toFire = new ObservableCollectionEvent<>(id, CollectionChangeType.remove, oldValue, oldValue, update.getCause());
				} else
					toFire = new IndexedCollectionEvent<>(id, index, CollectionChangeType.set, oldValue, newValue, update.getCause());
			}
			return toFire;
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends T>> observer) {
			theListeners.add(observer);
			return () -> theListeners.remove(observer);
		}

		@Override
		public CollectionSubscription subscribe(Consumer<? super ObservableCollectionEvent<? extends T>> observer) {
			if (isFiltered)
				return ObservableCollection.super.subscribe(observer);
			else
				return subscribeIndexed(observer);
		}

		@Override
		public CollectionSubscription subscribeIndexed(Consumer<? super IndexedCollectionEvent<? extends T>> observer) {
			if (!isFiltered)
				return ObservableCollection.super.subscribeIndexed(observer);
			try (Transaction t = lock(false, null)) {
				SubscriptionCause.doWith(new SubscriptionCause(), c -> {
					int index = 0;
					for (Map.Entry<ElementId, CollectionElementManager<E, ?, T>> entry : theElements.entrySet()) {
						observer.accept(new IndexedCollectionEvent<>(entry.getKey(), index++, CollectionChangeType.add, null,
							entry.getValue().get(), c));
					}
				});
				return removeAll -> {
					SubscriptionCause.doWith(new SubscriptionCause(), c -> {
						int index = 0;
						for (Map.Entry<ElementId, CollectionElementManager<E, ?, T>> entry : theElements.entrySet()) {
							T value = entry.getValue().get();
							observer.accept(
								new IndexedCollectionEvent<>(entry.getKey(), index++, CollectionChangeType.remove, value, value, c));
						}
					});
				};
			}
		}

		@Override
		public TypeToken<T> getType() {
			return theFlow.getTargetType();
		}

		@Override
		public boolean isLockSupported() {
			return theSource.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			Transaction flowSub = theFlow.lock(write, cause);
			Transaction collSub = theSource.lock(write, cause);
			return () -> {
				collSub.close();
				flowSub.close();
			};
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return theEquivalence;
		}

		@Override
		public int size() {
			return theSize;
		}

		@Override
		public boolean isEmpty() {
			return theSize == 0;
		}

		@Override
		public T get(int index) {
			try (Transaction t = lock(false, null)) {
				return (isFiltered ? thePresentElements : theElements).entrySet().get(index).getValue().get();
			}
		}

		protected boolean checkDestType(Object o) {
			return o == null || theFlow.getTargetType().getRawType().isInstance(o);
		}

		@Override
		public boolean contains(Object o) {
			if (!checkDestType(o))
				return false;
			if (!theFlow.isReversible())
				return ObservableCollectionImpl.contains(this, o);
			try (Transaction t = lock(false, null)) {
				FilterMapResult<T, E> reversed = theFlow.reverse(new FilterMapResult<>((T) o));
				if (reversed.error != null)
					return false;
				return theSource.contains(reversed.result);
			}
		}

		@Override
		public boolean containsAny(Collection<?> c) {
			return ObservableCollectionImpl.containsAny(this, c);
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			return ObservableCollectionImpl.containsAll(this, c);
		}

		@Override
		public ObservableElementSpliterator<T> spliterator() {
			return spliterator(true);
		}

		@Override
		public ObservableElementSpliterator<T> spliterator(boolean fromStart) {
			class CachedSpliterator implements ObservableElementSpliterator<T> {
				private final ReversibleSpliterator<Map.Entry<ElementId, CollectionElementManager<E, ?, T>>> theIdSpliterator;
				private final ObservableCollectionElement<T> theElement;
				ElementId theCurrentId;
				T theCurrentValue;

				CachedSpliterator(ReversibleSpliterator<Map.Entry<ElementId, CollectionElementManager<E, ?, T>>> idSpliterator) {
					theIdSpliterator = idSpliterator;
					theElement = new ObservableCollectionElement<T>() {
						@Override
						public TypeToken<T> getType() {
							return DerivedCollection.this.getType();
						}

						@Override
						public T get() {
							return theCurrentValue;
						}

						@Override
						public ElementId getElementId() {
							return theCurrentId;
						}
					};
				}

				@Override
				public long estimateSize() {
					return theIdSpliterator.estimateSize();
				}

				@Override
				public long getExactSizeIfKnown() {
					return theIdSpliterator.getExactSizeIfKnown();
				}

				@Override
				public int characteristics() {
					return theIdSpliterator.characteristics();
				}

				@Override
				public TypeToken<T> getType() {
					return DerivedCollection.this.getType();
				}

				@Override
				public boolean tryAdvanceObservableElement(Consumer<? super ObservableCollectionElement<T>> action) {
					boolean[] success = new boolean[1];
					try (Transaction t = lock(false, null)) {
						while (!success[0] && theIdSpliterator.tryAdvance(entry -> {
							CollectionElementManager<E, ?, T> elMgr = entry.getValue();
							if (!elMgr.isPresent())
								return;
							success[0] = true;
							theCurrentId = entry.getKey();
							theCurrentValue = elMgr.get();
							action.accept(theElement);
						})) {
						}
					}
					return success[0];
				}

				@Override
				public boolean tryReverseObservableElement(Consumer<? super ObservableCollectionElement<T>> action) {
					boolean[] success = new boolean[1];
					try (Transaction t = lock(false, null)) {
						while (!success[0] && theIdSpliterator.tryReverse(entry -> {
							CollectionElementManager<E, ?, T> elMgr = entry.getValue();
							if (!elMgr.isPresent())
								return;
							success[0] = true;
							theCurrentId = entry.getKey();
							theCurrentValue = elMgr.get();
							action.accept(theElement);
						})) {
						}
					}
					return success[0];
				}

				@Override
				public void forEachObservableElement(Consumer<? super ObservableCollectionElement<T>> action) {
					try (Transaction t = lock(false, null)) {
						theIdSpliterator.forEachRemaining(entry -> {
							CollectionElementManager<E, ?, T> elMgr = entry.getValue();
							if (!elMgr.isPresent())
								return;
							theCurrentId = entry.getKey();
							theCurrentValue = elMgr.get();
							action.accept(theElement);
						});
					}
				}

				@Override
				public void forEachReverseObservableElement(Consumer<? super ObservableCollectionElement<T>> action) {
					try (Transaction t = lock(false, null)) {
						theIdSpliterator.forEachReverse(entry -> {
							CollectionElementManager<E, ?, T> elMgr = entry.getValue();
							if (!elMgr.isPresent())
								return;
							theCurrentId = entry.getKey();
							theCurrentValue = elMgr.get();
							action.accept(theElement);
						});
					}
				}

				@Override
				public ObservableElementSpliterator<T> trySplit() {
					ReversibleSpliterator<Map.Entry<ElementId, CollectionElementManager<E, ?, T>>> split = theIdSpliterator.trySplit();
					return split == null ? null : new CachedSpliterator(split);
				}
			}
			return new CachedSpliterator((isFiltered ? thePresentElements : theElements).entrySet().spliterator(fromStart));
		}

		@Override
		public boolean forObservableElement(T value, Consumer<? super ObservableCollectionElement<? extends T>> onElement) {
			for (CollectionElementManager<E, ?, T> el : thePresentElements.values())
				if (equivalence().elementEquals(el.get(), value)) {
					onElement.accept(observableElementFor(el));
					return true;
				}
			return false;
		}

		@Override
		public boolean forElementAt(ElementId elementId, Consumer<? super ObservableCollectionElement<? extends T>> onElement) {
			CollectionElementManager<E, ?, T> el = theElements.get(elementId);
			if (el == null)
				return false;
			onElement.accept(observableElementFor(el));
			return true;
		}

		protected ObservableCollectionElement<T> observableElementFor(CollectionElementManager<E, ?, T> el) {
			// TODO Auto-generated method stub
		}

		@Override
		public boolean forMutableElement(T value, Consumer<? super MutableObservableElement<? extends T>> onElement) {
			for (CollectionElementManager<E, ?, T> el : thePresentElements.values())
				if (equivalence().elementEquals(el.get(), value)) {
					onElement.accept(mutableElementFor(el));
					return true;
				}
			return false;
		}

		@Override
		public boolean forMutableElementAt(ElementId elementId, Consumer<? super MutableObservableElement<? extends T>> onElement) {
			CollectionElementManager<E, ?, T> el = theElements.get(elementId);
			if (el == null)
				return false;
			onElement.accept(mutableElementFor(el));
			return true;
		}

		protected MutableObservableElement<T> mutableElementFor(CollectionElementManager<E, ?, T> el) {
			// TODO Auto-generated method stub
		}

		@Override
		public String canAdd(T value) {
			if (!theFlow.isReversible())
				return StdMsg.UNSUPPORTED_OPERATION;
			else if (!checkDestType(value))
				return StdMsg.BAD_TYPE;
			try (Transaction t = lock(false, null)) {
				FilterMapResult<T, E> reversed = theFlow.reverse(new FilterMapResult<>(value));
				if (reversed.error != null)
					return reversed.error;
				return theSource.canAdd(reversed.result);
			}
		}

		@Override
		public boolean add(T e) {
			if (!theFlow.isReversible())
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			if (!checkDestType(e))
				throw new IllegalArgumentException(StdMsg.BAD_TYPE);
			try (Transaction t = lock(true, null)) {
				FilterMapResult<T, E> reversed = theFlow.reverse(new FilterMapResult<>(e));
				if (reversed.error != null)
					throw new IllegalArgumentException(reversed.error);
				return theSource.add(reversed.result);
			}
		}

		/**
		 * @param input The collection to reverse
		 * @return The collection, with its elements {@link ObservableCollection.CollectionManager#reverse(FilterMapResult) reversed}
		 */
		protected List<E> reverse(Collection<?> input, boolean checkInput) {
			FilterMapResult<T, E> reversed = new FilterMapResult<>();
			return input.stream().<E> flatMap(v -> {
				if (!checkDestType(v)) {
					if (checkInput)
						throw new IllegalArgumentException(StdMsg.BAD_TYPE);
					else
						return Stream.empty();
				}
				reversed.source = (T) v;
				theFlow.reverse(reversed);
				if (reversed.error == null)
					return Stream.of(reversed.result);
				else if (checkInput)
					throw new IllegalArgumentException(reversed.error);
				else
					return Stream.empty();
			}).collect(Collectors.toList());
		}

		@Override
		public boolean addAll(Collection<? extends T> c) {
			if (c.isEmpty())
				return false;
			if (!theFlow.isReversible())
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			try (Transaction t = lock(true, null)) {
				return theSource.addAll(reverse(c, true));
			}
		}

		@Override
		public String canRemove(Object value) {
			if (!theFlow.isReversible())
				return StdMsg.UNSUPPORTED_OPERATION;
			else if (!checkDestType(value))
				return StdMsg.BAD_TYPE;
			try (Transaction t = lock(false, null)) {
				FilterMapResult<T, E> reversed = theFlow.reverse(new FilterMapResult<>((T) value));
				if (reversed.error != null)
					return reversed.error;
				return theSource.canRemove(reversed.result);
			}
		}

		@Override
		public boolean remove(Object o) {
			if (o != null && !checkDestType(o))
				return false;
			if (theFlow.isReversible()) {
				try (Transaction t = lock(false, null)) {
					FilterMapResult<T, E> reversed = theFlow.reverse(new FilterMapResult<>((T) o));
					if (reversed.error != null)
						return false;
					return theSource.remove(reversed.result);
				}
			} else
				return ObservableCollectionImpl.remove(this, o);
		}

		@Override
		public boolean removeLast(Object o) {
			if (o != null && !checkDestType(o))
				return false;
			if (theFlow.isReversible()) {
				try (Transaction t = lock(false, null)) {
					FilterMapResult<T, E> reversed = theFlow.reverse(new FilterMapResult<>((T) o));
					if (reversed.error != null)
						return false;
					return theSource.removeLast(reversed.result);
				}
			} else
				return ObservableCollectionImpl.removeLast(this, o);
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			return ObservableCollectionImpl.removeAll(this, c);
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			return ObservableCollectionImpl.retainAll(this, c);
		}

		@Override
		public void clear() {
			if (!isFiltered)
				theSource.clear();
			try (Transaction t = lock(true, null)) {
				mutableSpliterator().forEachMutableElement(el -> el.remove());
			}
		}

		@Override
		public MutableObservableSpliterator<T> mutableSpliterator() {
			return mutableSpliterator(true);
		}

		@Override
		public MutableObservableSpliterator<T> mutableSpliterator(boolean fromStart) {
			class MutableCachedSpliterator implements MutableObservableSpliterator<T> {
				private final MutableObservableSpliterator<E> theWrappedSpliter;
				private final MutableObservableElement<T> theElement;
				private MutableObservableElement<E> theCurrentElement;
				private ElementId theCurrentId;
				private CollectionElementManager<E, ?, T> theCurrentElementMgr;

				MutableCachedSpliterator(MutableObservableSpliterator<E> wrap) {
					theWrappedSpliter = wrap;
					theElement = new MutableObservableElement<T>() {
						@Override
						public ElementId getElementId() {
							return theCurrentId;
						}

						@Override
						public TypeToken<T> getType() {
							return DerivedCollection.this.getType();
						}

						@Override
						public T get() {
							return theCurrentElementMgr.get();
						}

						@Override
						public String canRemove() {
							return theCurrentElement.canRemove();
						}

						@Override
						public void remove() throws UnsupportedOperationException {
							theCurrentElement.remove();
						}

						@Override
						public <V extends T> String isAcceptable(V value) {
							String msg = theCurrentElementMgr.setElement(value, false, null);
							if (msg == null)
								return null;
							FilterMapResult<T, E> result = theFlow.reverse(new FilterMapResult<>(value));
							if (result.error != null)
								return result.error;
							return theCurrentElement.isAcceptable(result.result);
						}

						@Override
						public <V extends T> T set(V value, Object cause) throws IllegalArgumentException, UnsupportedOperationException {
							T oldValue = theCurrentElementMgr.get();
							String msg = theCurrentElementMgr.setElement(value, false, cause);
							if (msg == null)
								return null;
							FilterMapResult<T, E> result = theFlow.reverse(new FilterMapResult<>(value));
							if (result.error != null)
								throw new IllegalArgumentException(result.error);
							theCurrentElement.set(result.result, cause);
							return oldValue;
						}

						@Override
						public Value<String> isEnabled() {
							return new Value<String>() {
								@Override
								public TypeToken<String> getType() {
									return TypeToken.of(String.class);
								}

								@Override
								public String get() {
									if (theCurrentElementMgr.isElementSettable())
										return null;
									else if (!theFlow.isReversible())
										return StdMsg.UNSUPPORTED_OPERATION;
									return theCurrentElement.isEnabled().get();
								}
							};
						}
					};
				}

				@Override
				public TypeToken<T> getType() {
					return DerivedCollection.this.getType();
				}

				@Override
				public long estimateSize() {
					return theWrappedSpliter.estimateSize();
				}

				@Override
				public long getExactSizeIfKnown() {
					if (!isFiltered)
						return theWrappedSpliter.getExactSizeIfKnown();
					else
						return -1;
				}

				@Override
				public int characteristics() {
					int ch = theWrappedSpliter.characteristics();
					ch &= ~SORTED;
					if (!isFiltered)
						ch &= ~(SIZED | SUBSIZED);
					return ch;
				}

				@Override
				public Comparator<? super T> getComparator() {
					return null;
				}

				@Override
				public MutableObservableSpliterator<T> trySplit() {
					MutableObservableSpliterator<E> split = theWrappedSpliter.trySplit();
					return split == null ? null : new MutableCachedSpliterator(split);
				}

				@Override
				public boolean tryAdvanceMutableElement(Consumer<? super MutableObservableElement<T>> action) {
					boolean[] success = new boolean[1];
					try (Transaction t = lock(true, null)) {
						while (!success[0] && theWrappedSpliter.tryAdvanceMutableElement(el -> {
							theCurrentElementMgr = theElements.get(theCurrentId);
							if (theCurrentElementMgr == null || !theCurrentElementMgr.isPresent())
								return;
							success[0] = true;
							theCurrentElement = el;
							theCurrentId = el.getElementId();
							action.accept(theElement);
						})) {
						}
						;
					}
					return success[0];
				}

				@Override
				public boolean tryReverseMutableElement(Consumer<? super MutableObservableElement<T>> action) {
					boolean[] success = new boolean[1];
					try (Transaction t = lock(true, null)) {
						while (!success[0] && theWrappedSpliter.tryReverseMutableElement(el -> {
							theCurrentElementMgr = theElements.get(theCurrentId);
							if (theCurrentElementMgr == null || !theCurrentElementMgr.isPresent())
								return;
							success[0] = true;
							theCurrentElement = el;
							theCurrentId = el.getElementId();
							action.accept(theElement);
						})) {
						}
						;
					}
					return success[0];
				}

				@Override
				public void forEachMutableElement(Consumer<? super MutableObservableElement<T>> action) {
					try (Transaction t = lock(true, null)) {
						theWrappedSpliter.forEachMutableElement(el -> {
							theCurrentElementMgr = theElements.get(theCurrentId);
							if (theCurrentElementMgr == null || !theCurrentElementMgr.isPresent())
								return;
							theCurrentElement = el;
							theCurrentId = el.getElementId();
							action.accept(theElement);
						});
						MutableObservableSpliterator.super.forEachMutableElement(action);
					}
				}

				@Override
				public void forEachReverseMutableElement(Consumer<? super MutableObservableElement<T>> action) {
					try (Transaction t = lock(true, null)) {
						theWrappedSpliter.forEachReverseMutableElement(el -> {
							theCurrentElementMgr = theElements.get(theCurrentId);
							if (theCurrentElementMgr == null || !theCurrentElementMgr.isPresent())
								return;
							theCurrentElement = el;
							theCurrentId = el.getElementId();
							action.accept(theElement);
						});
						MutableObservableSpliterator.super.forEachMutableElement(action);
					}
				}
			}
			return new MutableCachedSpliterator(theSource.mutableSpliterator(fromStart));
		}

		@Override
		public int hashCode() {
			return ObservableCollection.hashCode(this);
		}

		@Override
		public boolean equals(Object obj) {
			return ObservableCollection.equals(this, obj);
		}

		@Override
		public String toString() {
			return ObservableCollection.toString(this);
		}
	}

	/**
	 * Implements {@link ObservableCollection.ViewBuilder#build()}
	 *
	 * @param <E> The type of elements in the collection
	 */
	public static class CollectionView<E> implements ObservableCollection<E> {
		private final ObservableCollection<E> theCollection;
		private final ViewDef<E> theDef;

		/**
		 * @param collection The collection whose data to present
		 * @param def The definition of what operations to allow on the data
		 */
		public CollectionView(ObservableCollection<E> collection, ViewDef<E> def) {
			theCollection = collection;
			theDef = def;
		}

		protected ObservableCollection<E> getCollection() {
			return theCollection;
		}

		protected ViewDef<E> getDef() {
			return theDef;
		}

		@Override
		public TypeToken<E> getType() {
			return theCollection.getType();
		}

		@Override
		public Equivalence<? super E> equivalence() {
			return theCollection.equivalence();
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
		public int size() {
			return theCollection.size();
		}

		@Override
		public boolean isEmpty() {
			return theCollection.isEmpty();
		}

		@Override
		public E get(int index) {
			return theCollection.get(index);
		}

		@Override
		public int indexOf(Object value) {
			return theCollection.indexOf(value);
		}

		@Override
		public int lastIndexOf(Object value) {
			return theCollection.lastIndexOf(value);
		}

		@Override
		public E last() {
			return theCollection.last();
		}

		@Override
		public ObservableCollection<E> reverse() {
			return new CollectionView<>(theCollection.reverse(), theDef);
		}

		@Override
		public E[] toArray() {
			return theCollection.toArray();
		}

		@Override
		public <T> T[] toArray(T[] a) {
			return theCollection.toArray(a);
		}

		@Override
		public ObservableElementSpliterator<E> spliterator() {
			return theCollection.spliterator();
		}

		@Override
		public boolean contains(Object o) {
			return theCollection.contains(o);
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			return theCollection.containsAll(c);
		}

		@Override
		public boolean containsAny(Collection<?> c) {
			return theCollection.containsAny(c);
		}

		@Override
		public MutableObservableSpliterator<E> mutableSpliterator() {
			return mutableSpliterator(true);
		}

		@Override
		public MutableObservableSpliterator<E> mutableSpliterator(boolean fromStart) {
			return theCollection.mutableSpliterator(fromStart).map(theDef.mapSpliterator());
		}

		@Override
		public String canAdd(E value) {
			String s = theDef.checkAdd(value);
			if (s == null)
				s = theCollection.canAdd(value);
			return s;
		}

		@Override
		public boolean add(E value) {
			if (theDef.checkAdd(value) == null)
				return theCollection.add(value);
			else
				return false;
		}

		@Override
		public boolean addAll(Collection<? extends E> values) {
			if (theDef.isAddFiltered())
				return theCollection.addAll(values.stream().filter(v -> theDef.checkAdd(v) == null).collect(Collectors.toList()));
			else
				return theCollection.addAll(values);
		}

		@Override
		public ObservableCollection<E> addValues(E... values) {
			if (theDef.isAddFiltered())
				theCollection.addAll(Arrays.stream(values).filter(v -> theDef.checkAdd(v) == null).collect(Collectors.toList()));
			else
				theCollection.addValues(values);
			return this;
		}

		@Override
		public String canRemove(Object value) {
			String s = theDef.checkRemove(value);
			if (s == null)
				s = theCollection.canRemove(value);
			return s;
		}

		@Override
		public boolean remove(Object value) {
			if (theDef.checkRemove(value) != null)
				return false;
			else
				return theCollection.remove(value);
		}

		@Override
		public boolean removeLast(Object value) {
			if (theDef.checkRemove(value) != null)
				return false;
			else
				return theCollection.removeLast(value);
		}

		@Override
		public boolean removeAll(Collection<?> values) {
			Collection<?> toRemove;
			if (theDef.isRemoveFiltered())
				toRemove = values.stream().filter(v -> theDef.checkRemove(v) == null).collect(Collectors.toList());
			else
				toRemove = values;
			return theCollection.removeAll(values);
		}

		@Override
		public boolean retainAll(Collection<?> values) {
			if (!theDef.isRemoveFiltered())
				return theCollection.retainAll(values);

			if (values.isEmpty())
				return false;
			Set<E> cSet = toSet(equivalence(), values);
			if (cSet.isEmpty())
				return false;
			return theCollection.removeIf(v -> theDef.checkRemove(v) == null && !cSet.contains(v));
		}

		@Override
		public void clear() {
			if (!theDef.isRemoveFiltered()) {
				theCollection.clear();
				return;
			}

			theCollection.mutableSpliterator().forEachElement(el -> {
				if (theDef.checkRemove(el.get()) == null)
					el.remove();
			});
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
			if (theDef.getUntil() == null)
				return theCollection.onChange(observer);
			else
				return ObservableCollectionImpl.defaultOnChange(this, observer);
		}

		@Override
		public CollectionSubscription subscribe(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
			if (theDef.getUntil() == null)
				return theCollection.subscribe(observer);
			CollectionSubscription collSub = theCollection.subscribe(observer);
			AtomicBoolean complete = new AtomicBoolean(false);
			Subscription obsSub = theDef.getUntil().take(1).act(u -> {
				if (!complete.getAndSet(true))
					collSub.unsubscribe(theDef.isUntilRemoves());
			});
			return removeAll -> {
				if (!complete.getAndSet(true)) {
					obsSub.unsubscribe();
					collSub.unsubscribe(removeAll);
				}
				// If the until has already fired and this collection is non-terminating, there's no way to determine which elements the
				// listener knows about, hence no way to act on the removeAll given here. We just have to assume that for non-terminating
				// collections, the code no longer cares about the content.
			};
		}

		@Override
		public CollectionSubscription subscribeIndexed(Consumer<? super IndexedCollectionEvent<? extends E>> observer) {
			if (theDef.getUntil() == null)
				return theCollection.subscribeIndexed(observer);
			else
				return ObservableCollection.super.subscribeIndexed(observer);
		}

		@Override
		public int hashCode() {
			return theCollection.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return theCollection.equals(obj);
		}

		@Override
		public String toString() {
			return theCollection.toString();
		}
	}

	/**
	 * Implements {@link ObservableCollection#groupBy(ObservableCollection.GroupingBuilder)}
	 *
	 * @param <K> The key type of the map
	 * @param <E> The value type of the map
	 */
	public static class GroupedMultiMap<K, E> implements ObservableMultiMap<K, E> {
		private final ObservableCollection<E> theWrapped;
		private final GroupingBuilder<E, K> theBuilder;

		private final ObservableSet<K> theKeySet;

		/**
		 * @param wrap The collection whose content to reflect
		 * @param builder The grouping builder defining the grouping of the content
		 */
		protected GroupedMultiMap(ObservableCollection<E> wrap, GroupingBuilder<E, K> builder) {
			theWrapped = wrap;
			theBuilder = builder;

			theKeySet = unique(wrap.flow().map(theBuilder.getKeyType()).map(theBuilder.getKeyMaker(), theBuilder.areNullsMapped())).build();
		}

		/** @return The collection whose content is reflected by this multi-map */
		protected ObservableCollection<E> getWrapped() {
			return theWrapped;
		}

		/** @return The grouping builder that defines this map's grouping */
		protected GroupingBuilder<E, K> getBuilder() {
			return theBuilder;
		}

		/**
		 * @param keyCollection The key collection to group
		 * @return The key set for the map
		 */
		protected UniqueDataFlow<E, ?, K> unique(CollectionDataFlow<E, ?, K> keyFlow) {
			return keyFlow.unique(getBuilder().getEquivalence(), theBuilder.isAlwaysUsingFirst());
		}

		@Override
		public TypeToken<K> getKeyType() {
			return theBuilder.getKeyType();
		}

		@Override
		public TypeToken<E> getValueType() {
			return theWrapped.getType();
		}

		@Override
		public boolean isLockSupported() {
			return theWrapped.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theWrapped.lock(write, cause);
		}

		@Override
		public ObservableSet<K> keySet() {
			return theKeySet;
		}

		@Override
		public ObservableCollection<E> get(Object key) {
			if (key != null && !theBuilder.getKeyType().getRawType().isInstance(key))
				return ObservableCollection.constant(theWrapped.getType());
			CollectionDataFlow<E, E, E> flow = theWrapped.flow();
			Function<E, String> filter = v -> {
				if (v != null || theBuilder.areNullsMapped())
					return theBuilder.getEquivalence().elementEquals((K) key, theBuilder.getKeyMaker().apply(v)) ? null
						: StdMsg.WRONG_GROUP;
				else
					return key == null ? null : StdMsg.WRONG_GROUP;
			};
			if (theBuilder.isStatic())
				flow = flow.filterStatic(filter, true);
			else
				flow = flow.filter(filter, true);
			return flow.build();
		}

		@Override
		public ObservableSet<? extends ObservableMultiEntry<K, E>> entrySet() {
			return ObservableMultiMap.defaultEntrySet(this);
		}

		@Override
		public String toString() {
			return entrySet().toString();
		}
	}

	/**
	 * Implements {@link ObservableCollection#groupBy(ObservableCollection.SortedGroupingBuilder)}
	 *
	 * @param <K> The key type of the map
	 * @param <E> The value type of the map
	 */
	public static class GroupedSortedMultiMap<K, E> extends GroupedMultiMap<K, E> implements ObservableSortedMultiMap<K, E> {
		GroupedSortedMultiMap(ObservableCollection<E> wrap, SortedGroupingBuilder<E, K> builder) {
			super(wrap, builder);
		}

		@Override
		protected SortedGroupingBuilder<E, K> getBuilder() {
			return (SortedGroupingBuilder<E, K>) super.getBuilder();
		}

		@Override
		public Comparator<? super K> comparator() {
			return getBuilder().getCompare();
		}

		@Override
		protected UniqueSortedDataFlow<E, ?, K> unique(CollectionDataFlow<E, ?, K> keyFlow) {
			return keyFlow.uniqueSorted(getBuilder().getCompare(), getBuilder().isAlwaysUsingFirst());
		}

		@Override
		public ObservableSortedSet<K> keySet() {
			return (ObservableSortedSet<K>) super.keySet();
		}

		@Override
		public ObservableSortedSet<? extends ObservableSortedMultiEntry<K, E>> entrySet() {
			return ObservableSortedMultiMap.defaultEntrySet(this);
		}
	}

	/**
	 * Implements {@link ObservableCollection#constant(TypeToken, Collection)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	public static class ConstantObservableCollection<E> implements ObservableCollection<E> {
		/** An element in a {@link ConstantObservableCollection} */
		protected class ConstantElement implements MutableObservableElement<E> {
			private final E theValue;
			private final ElementId theId;

			ConstantElement(E value, int index) {
				theValue = value;
				theId = ElementId.of(index);
			}

			@Override
			public ElementId getElementId() {
				return theId;
			}

			@Override
			public TypeToken<E> getType() {
				return theType;
			}

			@Override
			public E get() {
				return theValue;
			}

			@Override
			public Value<String> isEnabled() {
				return Value.constant(StdMsg.UNSUPPORTED_OPERATION);
			}

			@Override
			public <V extends E> String isAcceptable(V value) {
				return StdMsg.UNSUPPORTED_OPERATION;
			}

			@Override
			public <V extends E> E set(V value, Object cause) throws IllegalArgumentException {
				throw new IllegalArgumentException(StdMsg.UNSUPPORTED_OPERATION);
			}

			@Override
			public String canRemove() {
				return StdMsg.UNSUPPORTED_OPERATION;
			}

			@Override
			public void remove() throws IllegalArgumentException {
				throw new IllegalArgumentException(StdMsg.UNSUPPORTED_OPERATION);
			}
		}
		private final TypeToken<E> theType;
		private final ReversibleList<ConstantElement> theElements;
		private final Collection<? extends E> theCollection;

		/**
		 * @param type The type of the values
		 * @param collection The collection whose values to present
		 */
		public ConstantObservableCollection(TypeToken<E> type, Collection<? extends E> collection) {
			theType = type;
			theCollection = collection;
			int[] index = new int[1];
			theElements = CircularArrayList.build().unsafe().withInitCapacity(collection.size()).build();
			collection.stream().map(v -> new ConstantElement(v, index[0]++)).collect(Collectors.toCollection(() -> theElements));
		}

		/** @return The list of collection elements in this collection */
		protected List<ConstantElement> getElements() {
			return theElements;
		}

		/** @return The collection of values */
		protected Collection<? extends E> getCollection() {
			return theCollection;
		}

		@Override
		public TypeToken<E> getType() {
			return theType;
		}

		@Override
		public Equivalence<? super E> equivalence() {
			return Equivalence.DEFAULT;
		}

		@Override
		public boolean isLockSupported() {
			// False implies that the locking can't be relied on for thread-safety, but we're immutable, so we're always thread-safe
			return true;
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return Transaction.NONE;
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
		public MutableObservableSpliterator<E> mutableSpliterator() {
			class ConstantObservableSpliterator implements MutableObservableSpliterator<E> {
				private final ReversibleSpliterator<ConstantElement> theElementSpliter;

				ConstantObservableSpliterator(ReversibleSpliterator<ConstantObservableCollection<E>.ConstantElement> elementSpliter) {
					theElementSpliter = elementSpliter;
				}

				@Override
				public TypeToken<E> getType() {
					return theType;
				}

				@Override
				public long estimateSize() {
					return theCollection.size();
				}

				@Override
				public long getExactSizeIfKnown() {
					return theCollection.size();
				}

				@Override
				public int characteristics() {
					return IMMUTABLE | theElementSpliter.characteristics();
				}

				@Override
				public boolean tryAdvanceMutableElement(Consumer<? super MutableObservableElement<E>> action) {
					return theElementSpliter.tryAdvance(action);
				}

				@Override
				public boolean tryReverseMutableElement(Consumer<? super MutableObservableElement<E>> action) {
					return theElementSpliter.tryReverse(action);
				}

				@Override
				public MutableObservableSpliterator<E> trySplit() {
					ReversibleSpliterator<ConstantElement> split = theElementSpliter.trySplit();
					return split == null ? null : new ConstantObservableSpliterator(split);
				}
			}
			return new ConstantObservableSpliterator(theElements.spliterator());
		}

		@Override
		public boolean contains(Object o) {
			return theCollection.contains(o);
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			return theCollection.containsAll(c);
		}

		@Override
		public boolean containsAny(Collection<?> c) {
			return ObservableCollectionImpl.containsAny(this, c);
		}

		@Override
		public String canAdd(E value) {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public boolean add(E e) {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public boolean addAll(Collection<? extends E> c) {
			if (!c.isEmpty())
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			return false;
		}

		@Override
		public String canRemove(Object value) {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public boolean remove(Object o) {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public boolean removeLast(Object o) {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			if (!c.isEmpty())
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			return false;
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public void clear() {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
			return () -> {
			};
		}

		@Override
		public int hashCode() {
			return ObservableCollection.hashCode(this);
		}

		@Override
		public boolean equals(Object obj) {
			return ObservableCollection.equals(this, obj);
		}

		@Override
		public String toString() {
			return ObservableCollection.toString(this);
		}
	}

	/**
	 * Implements {@link ObservableCollection#flattenValue(ObservableValue)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	public static class FlattenedValueCollection<E> implements ObservableCollection<E> {
		private final ObservableValue<? extends ObservableCollection<? extends E>> theCollectionObservable;
		private final ReentrantLock theLock;
		private final TypeToken<E> theType;

		/** @param collectionObservable The value to present as a static collection */
		protected FlattenedValueCollection(ObservableValue<? extends ObservableCollection<? extends E>> collectionObservable) {
			theCollectionObservable = collectionObservable;
			theLock = new ReentrantLock();
			theType = (TypeToken<E>) theCollectionObservable.getType().resolveType(ObservableCollection.class.getTypeParameters()[0]);
		}

		/** @return The value that backs this collection */
		protected ObservableValue<? extends ObservableCollection<? extends E>> getWrapped() {
			return theCollectionObservable;
		}

		@Override
		public TypeToken<E> getType() {
			return theType;
		}

		@Override
		public boolean isLockSupported() {
			return true;
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			theLock.lock();
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			Transaction t = coll == null ? Transaction.NONE : coll.lock(write, cause);
			return () -> {
				t.close();
				theLock.unlock();
			};
		}

		@Override
		public Equivalence<? super E> equivalence() {
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			return coll == null ? Equivalence.DEFAULT : (Equivalence<? super E>) coll.equivalence();
		}

		@Override
		public int size() {
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			return coll == null ? 0 : coll.size();
		}

		@Override
		public boolean isEmpty() {
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			return coll == null ? true : coll.isEmpty();
		}

		@Override
		public MutableObservableSpliterator<E> mutableSpliterator() {
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			return coll == null ? MutableObservableSpliterator.empty(theType)
				: ((MutableObservableSpliterator<E>) coll.mutableSpliterator());
		}

		@Override
		public boolean contains(Object o) {
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			return coll == null ? false : coll.contains(o);
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			if (c.isEmpty())
				return true;
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			return coll == null ? false : coll.containsAll(c);
		}

		@Override
		public boolean containsAny(Collection<?> c) {
			if (c.isEmpty())
				return false;
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			return coll == null ? false : coll.containsAny(c);
		}

		@Override
		public String canAdd(E value) {
			ObservableCollection<? extends E> current = theCollectionObservable.get();
			if (current == null)
				return StdMsg.UNSUPPORTED_OPERATION;
			else if (value != null && !current.getType().getRawType().isInstance(value))
				return StdMsg.BAD_TYPE;
			return ((ObservableCollection<E>) current).canAdd(value);
		}

		@Override
		public boolean add(E e) {
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			if (coll == null)
				return false;
			if (e != null && !coll.getType().getRawType().isInstance(e))
				throw new IllegalArgumentException(StdMsg.BAD_TYPE);
			return ((ObservableCollection<E>) coll).add(e);
		}

		@Override
		public boolean addAll(Collection<? extends E> c) {
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			return ((ObservableCollection<E>) coll).addAll(c);
		}

		@Override
		public String canRemove(Object value) {
			ObservableCollection<? extends E> current = theCollectionObservable.get();
			if (current == null)
				return StdMsg.UNSUPPORTED_OPERATION;
			return current.canRemove(value);
		}

		@Override
		public boolean remove(Object o) {
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			return coll == null ? false : coll.remove(o);
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			return coll == null ? false : coll.removeAll(c);
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			return coll == null ? false : coll.retainAll(c);
		}

		@Override
		public void clear() {
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			if (coll != null)
				coll.clear();
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
			return ObservableCollectionImpl.defaultOnChange(this, observer);
		}

		@Override
		public CollectionSubscription subscribe(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
			FlattenedObserver flatObs = new FlattenedObserver(observer);
			Subscription valueSub = theCollectionObservable.safe().subscribe(flatObs);
			return removeAll -> {
				valueSub.unsubscribe();
				flatObs.unsubscribe(removeAll);
			};
		}

		/**
		 * An observable for this collection's value that propagates the change events from the content collections to an observer on this
		 * collection
		 */
		protected class FlattenedObserver implements Observer<ObservableValueEvent<? extends ObservableCollection<? extends E>>> {
			private final Consumer<? super ObservableCollectionEvent<? extends E>> theObserver;
			private CollectionSubscription theInnerSub;

			/** @param observer The observer on this collection */
			protected FlattenedObserver(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
				theObserver = observer;
			}

			/**
			 * @param coll The content collection to subscribe to
			 * @param observer The observer to subscribe to the collection
			 * @return The collection subscription
			 */
			protected CollectionSubscription subscribe(ObservableCollection<? extends E> coll,
				Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
				return coll.subscribe(observer);
			}

			@Override
			public <V extends ObservableValueEvent<? extends ObservableCollection<? extends E>>> void onNext(V value) {
				theLock.lock();
				try {
					if (theInnerSub != null)
						theInnerSub.unsubscribe(true);
					ObservableCollection<? extends E> coll = value.getValue();
					if (coll != null) {
						boolean[] initialized = new boolean[1];
						theInnerSub = coll.subscribe(evt -> {
							if (initialized[0])
								theLock.lock();
							try {
								theObserver.accept(evt);
							} finally {
								if (!initialized[0])
									theLock.unlock();
							}
						});
						initialized[0] = true;
					} else
						theInnerSub = null;
				} finally {
					theLock.unlock();
				}
			}

			@Override
			public <V extends ObservableValueEvent<? extends ObservableCollection<? extends E>>> void onCompleted(V value) {
				unsubscribe(true);
			}

			/** @param removeAll Whether to remove all the elements from the observer */
			protected void unsubscribe(boolean removeAll) {
				theLock.lock();
				try {
					if (theInnerSub != null) {
						theInnerSub.unsubscribe(removeAll);
						theInnerSub = null;
					}
				} finally {
					theLock.unlock();
				}
			}
		}

		@Override
		public int hashCode() {
			return ObservableCollection.hashCode(this);
		}

		@Override
		public boolean equals(Object obj) {
			return ObservableCollection.equals(this, obj);
		}

		@Override
		public String toString() {
			return ObservableCollection.toString(this);
		}
	}

	/**
	 * Implements {@link ObservableCollection#flatten(ObservableCollection)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	public static class FlattenedObservableCollection<E> implements ObservableCollection<E> {
		private final ObservableCollection<? extends ObservableCollection<? extends E>> theOuter;
		private final TypeToken<E> theType;

		/** @param collection The collection of collections to flatten */
		protected FlattenedObservableCollection(ObservableCollection<? extends ObservableCollection<? extends E>> collection) {
			theOuter = collection;
			theType = (TypeToken<E>) theOuter.getType().resolveType(ObservableCollection.class.getTypeParameters()[0]);
		}

		/** @return The flattened collection of collections */
		protected ObservableCollection<? extends ObservableCollection<? extends E>> getOuter() {
			return theOuter;
		}

		@Override
		public boolean isLockSupported() {
			// Just a guess, since we can't be sure whether any inner collections will ever support locking
			return theOuter.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			Transaction outerLock = theOuter.lock(write, cause);
			Transaction[] innerLocks = new Transaction[theOuter.size()];
			int i = 0;
			for (ObservableCollection<?> c : theOuter)
				innerLocks[i++] = c.lock(write, cause);
			return new Transaction() {
				private volatile boolean hasRun;

				@Override
				public void close() {
					if (hasRun)
						return;
					hasRun = true;
					for (int j = innerLocks.length - 1; j >= 0; j--)
						innerLocks[j].close();
					outerLock.close();
				}
			};
		}

		@Override
		public TypeToken<E> getType() {
			return theType;
		}

		@Override
		public Equivalence<? super E> equivalence() {
			return Equivalence.DEFAULT;
		}

		@Override
		public int size() {
			int ret = 0;
			for (ObservableCollection<? extends E> subColl : theOuter)
				ret += subColl.size();
			return ret;
		}

		@Override
		public boolean isEmpty() {
			for (ObservableCollection<? extends E> subColl : theOuter)
				if (!subColl.isEmpty())
					return false;
			return true;
		}

		@Override
		public boolean contains(Object o) {
			return ObservableCollectionImpl.contains(this, o);
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			return ObservableCollectionImpl.containsAll(this, c);
		}

		@Override
		public boolean containsAny(Collection<?> c) {
			return ObservableCollectionImpl.containsAny(this, c);
		}

		@Override
		public String canAdd(E value) {
			String msg = null;
			for (ObservableCollection<? extends E> coll : theOuter) {
				if (value != null && !coll.getType().getRawType().isInstance(value))
					continue;
				String collMsg = ((ObservableCollection<E>) coll).canAdd(value);
				if (collMsg == null)
					return null;
				if (msg == null)
					msg = collMsg;
			}
			return msg;
		}

		@Override
		public boolean add(E e) {
			for (ObservableCollection<? extends E> coll : theOuter)
				if ((e == null || coll.getType().getRawType().isInstance(e)) && ((ObservableCollection<E>) coll).add(e))
					return true;
			return false;
		}

		@Override
		public boolean addAll(Collection<? extends E> c) {
			return ObservableCollectionImpl.addAll(this, c);
		}

		@Override
		public String canRemove(Object value) {
			String msg = null;
			String[] collMsg = new String[1];
			for (ObservableCollection<? extends E> coll : theOuter) {
				collMsg[0] = null;
				if (coll.equivalence().equals(equivalence())) {
					collMsg[0] = ((ObservableCollection<E>) coll).canRemove(value);
				} else {
					boolean[] found = new boolean[1];
					ElementSpliterator<? extends E> spliter = coll.mutableSpliterator();
					while (!found[0] && spliter.tryAdvanceElement(el -> {
						found[0] = equivalence().elementEquals(el.get(), value);
						if (found[0])
							collMsg[0] = el.canRemove();
					})) {
					}
				}
				if (collMsg[0] == null)
					return null;
				if (msg == null)
					msg = collMsg[0];
			}
			if (msg == null)
				return StdMsg.NOT_FOUND;
			return msg;
		}

		@Override
		public boolean remove(Object o) {
			for (ObservableCollection<? extends E> coll : theOuter) {
				if (coll.equivalence().equals(equivalence())) {
					if (coll.remove(o))
						return true;
				} else {
					boolean[] found = new boolean[1];
					boolean[] removed = new boolean[1];
					ElementSpliterator<? extends E> spliter = coll.mutableSpliterator();
					while (!found[0] && spliter.tryAdvanceElement(el -> {
						found[0] = equivalence().elementEquals(el.get(), o);
						if (found[0] && el.canRemove() == null) {
							el.remove();
							removed[0] = true;
						}
					})) {
					}
					if (removed[0])
						return true;
				}
			}
			return false;
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			if (c.isEmpty())
				return false;
			boolean removed = false;
			Set<E> set = ObservableCollectionImpl.toSet(equivalence(), c);
			for (ObservableCollection<? extends E> coll : theOuter) {
				if (coll.equivalence().equals(equivalence()))
					removed |= coll.removeAll(set);
				else {
					boolean[] cRemoved = new boolean[1];
					coll.mutableSpliterator().forEachMutableElement(el -> {
						if (set.contains(el.get()) && el.canRemove() == null) {
							cRemoved[0] = true;
							el.remove();
						}
					});
					removed |= cRemoved[0];
				}
			}
			return removed;
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			if (c.isEmpty())
				return false;
			boolean removed = false;
			Set<E> set = ObservableCollectionImpl.toSet(equivalence(), c);
			for (ObservableCollection<? extends E> coll : theOuter) {
				if (coll.equivalence().equals(equivalence()))
					removed |= coll.retainAll(set);
				else {
					boolean[] cRemoved = new boolean[1];
					coll.mutableSpliterator().forEachElement(el -> {
						if (!set.contains(el.get()) && el.canRemove() == null) {
							cRemoved[0] = true;
							el.remove();
						}
					});
					removed |= cRemoved[0];
				}
			}
			return removed;
		}

		@Override
		public void clear() {
			for (ObservableCollection<? extends E> coll : theOuter)
				coll.clear();
		}

		@Override
		public MutableObservableSpliterator<E> mutableSpliterator() {
			return new FlattenedSpliterator<>(theOuter.mutableSpliterator());
		}

		/**
		 * A spliterator for the flattened collection
		 *
		 * @param <C> The sub-type of collection held by the outer collection
		 */
		protected class FlattenedSpliterator<C extends ObservableCollection<? extends E>> implements MutableObservableSpliterator<E> {
			private final MutableObservableSpliterator<? extends C> theOuterSpliterator;

			private MutableObservableElement<? extends C> theOuterElement;
			private MutableObservableSpliterator<E> theInnerator;
			private final MutableObservableSpliteratorMap<? extends E, E> theElementMap;

			/** @param outerSpliterator The spliterator for the outer collection */
			protected FlattenedSpliterator(MutableObservableSpliterator<? extends C> outerSpliterator) {
				theOuterSpliterator = outerSpliterator;
				theElementMap = new MutableObservableSpliteratorMap<E, E>() {
					@Override
					public TypeToken<E> getType() {
						return theType;
					}

					@Override
					public E map(E value) {
						return value;
					}

					@Override
					public boolean test(E srcValue) {
						return true;
					}

					@Override
					public E reverse(E value) {
						return value;
					}

					@Override
					public String filterEnabled(CollectionElement<E> el) {
						return null;
					}

					@Override
					public String filterAccept(E value) {
						if (!theInnerator.getType().getRawType().isInstance(value))
							return StdMsg.BAD_TYPE;
						return null;
					}

					@Override
					public String filterRemove(CollectionElement<E> sourceEl) {
						return null;
					}

					@Override
					public long filterExactSize(long srcSize) {
						return srcSize;
					}

					@Override
					public ElementId mapId(ElementId id) {
						return compoundId(theOuterElement.getElementId(), id);
					}
				};
			}

			/**
			 * @param outerSpliterator A spliterator from the outer collection
			 * @param outerElement The initial element from the outer collection
			 * @param innerSpliterator The initial inner spliterator
			 * @param copied Whether this is from the
			 *        {@link #copy(MutableObservableSpliterator, MutableObservableElement, MutableObservableSpliterator) copy} method
			 *        (implying that <code>innerSpliterator</code> is already wrapped and need not be again
			 */
			protected FlattenedSpliterator(MutableObservableSpliterator<? extends C> outerSpliterator,
				MutableObservableElement<? extends C> outerElement, MutableObservableSpliterator<? extends E> innerSpliterator,
				boolean copied) {
				this(outerSpliterator);

				if (outerElement != null) {
					theOuterElement = outerElement;
					theInnerator = copied ? (MutableObservableSpliterator<E>) innerSpliterator : wrapInnerSplit(innerSpliterator);
				}
			}

			/**
			 * @param outerSpliterator A spliterator from the outer collection
			 * @param outerElement The initial element from the outer collection
			 * @param innerSpliterator The initial inner spliterator
			 * @return The copy of this spliterator with the given state
			 */
			protected FlattenedSpliterator<C> copy(MutableObservableSpliterator<? extends C> outerSpliterator,
				MutableObservableElement<? extends C> outerElement, MutableObservableSpliterator<E> innerSpliterator) {
				return new FlattenedSpliterator<>(outerSpliterator, innerSpliterator == null ? null : theOuterElement, innerSpliterator,
					true);
			}

			/** @return The outer spliterator backing this flattened spliterator */
			protected MutableObservableSpliterator<? extends C> getOuterSpliterator() {
				return theOuterSpliterator;
			}

			/** @return The outer element whose inner spliterator is currently being used */
			protected MutableObservableElement<? extends C> getOuterElement() {
				return theOuterElement;
			}

			/**
			 * @return The element map for {@link MutableObservableSpliterator#map(MutableObservableSpliteratorMap)}
			 */
			protected <E2 extends E> MutableObservableSpliteratorMap<E2, E> getElementMap() {
				return (MutableObservableSpliteratorMap<E2, E>) theElementMap;
			}

			/**
			 * @param toWrap The inner spliterator to wrap
			 * @return A spliterator giving collection elements to be passed to this spliterator's actions
			 */
			protected MutableObservableSpliterator<E> wrapInnerSplit(MutableObservableSpliterator<? extends E> toWrap) {
				return toWrap.map(getElementMap());
			}

			/** @return The spliterator for the inner collection at the current cursor */
			protected ObservableElementSpliterator<E> getInnerator() {
				return theInnerator;
			}

			@Override
			public TypeToken<E> getType() {
				return theType;
			}

			@Override
			public long estimateSize() {
				return size();
			}

			@Override
			public int characteristics() {
				return Spliterator.SIZED;
			}

			/**
			 * Advances the outer spliterator to get the next inner spliterator
			 *
			 * @return Whether there was a next outer element
			 */
			protected boolean advanceOuter() {
				return theOuterSpliterator.tryAdvanceMutableElement(el -> newInner(el, el.get().mutableSpliterator()));
			}

			/**
			 * @param outerEl The outer element for the next inner spliterator
			 * @param innerSpliter The next inner spliterator
			 */
			protected void newInner(MutableObservableElement<? extends C> outerEl, MutableObservableSpliterator<? extends E> innerSpliter) {
				theOuterElement = outerEl;
				theInnerator = wrapInnerSplit(innerSpliter);
			}

			@Override
			public boolean tryAdvanceMutableElement(Consumer<? super MutableObservableElement<E>> action) {
				boolean[] found = new boolean[1];
				while (!found[0]) {
					if (theInnerator != null && theInnerator.tryAdvanceMutableElement(action))
						found[0] = true;
					else if (!advanceOuter())
						return false;
				}
				return found[0];
			}

			@Override
			public MutableObservableSpliterator<E> trySplit() {
				MutableObservableSpliterator<? extends C> outerSplit = theOuterSpliterator.trySplit();
				if (outerSplit != null)
					return copy(outerSplit, null, null);
				if (theInnerator == null && advanceOuter()) {
					MutableObservableSpliterator<E> innerSplit = theInnerator.trySplit();
					if (innerSplit != null)
						return copy(outerSplit, theOuterElement, innerSplit);
				}
				return null;
			}
		}

		/**
		 * Creates a compound ElementId from the outer and inner IDs that compose an element in this collection
		 *
		 * @param outerId The ID of the outer element containing a collection
		 * @param innerId The ID of the element in a collection in an element of the outer collection
		 * @return The composed element ID for this collection
		 */
		protected static ElementId compoundId(ElementId outerId, ElementId innerId) {
			return ElementId.of(new BiTuple<>(outerId, innerId), (tup1, tup2) -> {
				int comp = tup1.getValue1().compareTo(tup2.getValue1());
				if (comp == 0)
					comp = tup1.getValue2().compareTo(tup2.getValue2());
				return comp;
			});
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
			return ObservableCollectionImpl.defaultOnChange(this, observer);
		}

		@Override
		public CollectionSubscription subscribe(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
			OuterObserver outerObs = new OuterObserver(observer);
			CollectionSubscription collSub;
			try (Transaction t = theOuter.lock(false, null)) {
				collSub = theOuter.subscribe(outerObs);
				outerObs.setInitialized();
			}
			return removeAll -> {
				try (Transaction t = theOuter.lock(false, null)) {
					if (!removeAll)
						outerObs.done();
					collSub.unsubscribe(removeAll);
				}
			};
		}

		/** An observer to the outer collection */
		protected class OuterObserver implements Consumer<ObservableCollectionEvent<? extends ObservableCollection<? extends E>>> {
			private final Consumer<? super ObservableCollectionEvent<? extends E>> theObserver;
			private final NavigableMap<ElementId, AddObserver> theElements;
			private final Object theMetadata;
			private boolean isInitialized;

			/** @param observer The oberver for this collection */
			protected OuterObserver(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
				theObserver = observer;
				theElements = new TreeMap<>();
				theMetadata = createSubscriptionMetadata();
			}

			/** @return The observers for the outer collection's elements */
			protected NavigableMap<ElementId, AddObserver> getElements() {
				return theElements;
			}

			@Override
			public void accept(ObservableCollectionEvent<? extends ObservableCollection<? extends E>> evt) {
				switch (evt.getType()) {
				case add:
					AddObserver addObs = createElementObserver(evt.getElementId(), theObserver, theMetadata);
					theElements.put(evt.getElementId(), addObs);
					addObs.set(evt.getNewValue(), isInitialized);
					break;
				case remove:
					theElements.remove(evt.getElementId()).remove(true);
					break;
				case set:
					theElements.get(evt.getElementId()).set(evt.getNewValue(), false);
					break;
				}
			}

			/**
			 * Tells this observer that the initial elements of the outer collection have all been fired (i.e. the
			 * {@link ObservableCollection#subscribe(Consumer)} method has returned
			 */
			protected void setInitialized() {
				isInitialized = true;
			}

			/** Called when the subscription to the outer collection is unsubscribed */
			protected void done() {
				for (AddObserver addObs : theElements.values())
					addObs.remove(false);
				theElements.clear();
			}

			/**
			 * @param elementId The ID of the element to observe
			 * @param observer The subscriber
			 * @param metadata The metadata for the subscription
			 * @return The value observer for the element's observable value contents
			 */
			protected AddObserver createElementObserver(ElementId elementId,
				Consumer<? super ObservableCollectionEvent<? extends E>> observer, Object metadata) {
				return new AddObserver(elementId, observer);
			}
		}

		/** An observer for the ObservableCollection inside one element of this collection */
		protected class AddObserver implements Consumer<ObservableCollectionEvent<? extends E>> {
			private final ElementId theOuterElementId;
			private final Consumer<? super ObservableCollectionEvent<? extends E>> theObserver;
			private final AtomicReference<CollectionSubscription> valueSub;
			private boolean isInitialized;

			/**
			 * @param elementId The ID of the element to observe
			 * @param observer The subscriber
			 */
			protected AddObserver(ElementId elementId, Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
				theOuterElementId = elementId;
				theObserver = observer;
				valueSub = new AtomicReference<>();
			}

			/** @return The ID of the element in the outer collection that this observer is watching */
			protected ElementId getOuterElementId() {
				return theOuterElementId;
			}

			@Override
			public void accept(ObservableCollectionEvent<? extends E> event) {
				try (Transaction t = isInitialized ? theOuter.lock(false, null) : Transaction.NONE) {
					ObservableCollectionEvent.doWith(createEvent(event), theObserver);
				}
			}

			void set(ObservableCollection<? extends E> collection, boolean outerInitialized) {
				CollectionSubscription oldSub = valueSub.getAndSet(null);
				if (oldSub != null)
					oldSub.unsubscribe(true);
				if (collection != null) {
					isInitialized = false;
					try (Transaction t = outerInitialized ? theOuter.lock(false, null) : Transaction.NONE) {
						valueSub.set(subscribe(collection));
					} finally {
						isInitialized = true;
					}
				}
			}

			void remove(boolean removeAll) {
				CollectionSubscription oldValueSub = valueSub.getAndSet(null);
				isInitialized = false; // Don't make the accept method acquire a lock
				if (oldValueSub != null)
					oldValueSub.unsubscribe(removeAll);
			}

			/**
			 * @param collection The inner collection to subscribe to (with this observer)
			 * @return The subscription
			 */
			protected CollectionSubscription subscribe(ObservableCollection<? extends E> collection) {
				return collection.subscribe(this);
			}

			/**
			 * @param innerEvent The event from the collection in one element of this collection
			 * @return The event to fire to the subscriber
			 */
			protected ObservableCollectionEvent<E> createEvent(ObservableCollectionEvent<? extends E> innerEvent) {
				return new ObservableCollectionEvent<>(compoundId(theOuterElementId, innerEvent.getElementId()), innerEvent.getType(),
					innerEvent.getOldValue(), innerEvent.getNewValue(), innerEvent);
			}
		}

		/** @return Metadata that subclasses may use to keep track of elements over the life of a subscription */
		protected Object createSubscriptionMetadata() {
			return null;
		}

		@Override
		public int hashCode() {
			return ObservableCollection.hashCode(this);
		}

		@Override
		public boolean equals(Object obj) {
			return ObservableCollection.equals(this, obj);
		}

		@Override
		public String toString() {
			return ObservableCollection.toString(this);
		}
	}
}
