package org.observe.collect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.Spliterator;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
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
import org.observe.SimpleObservable;
import org.observe.Subscription;
import org.observe.assoc.ObservableMultiMap;
import org.observe.assoc.ObservableSortedMultiMap;
import org.observe.collect.MutableObservableSpliterator.MutableObservableSpliteratorMap;
import org.observe.collect.ObservableCollection.GroupingBuilder;
import org.observe.collect.ObservableCollection.SortedGroupingBuilder;
import org.observe.collect.ObservableCollection.StdMsg;
import org.qommons.BiTuple;
import org.qommons.Causable;
import org.qommons.Ternian;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementSpliterator;
import org.qommons.collect.TreeList;
import org.qommons.tree.CountedRedBlackNode.DefaultNode;
import org.qommons.tree.CountedRedBlackNode.DefaultTreeMap;
import org.qommons.tree.CountedRedBlackNode.DefaultTreeSet;
import org.qommons.value.Value;

import com.google.common.reflect.TypeParameter;
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

	/**
	 * Implements {@link ObservableCollection#withEquivalence(Equivalence)}
	 *
	 * @param <E> The type of values in the collection
	 */
	public static class EquivalenceSwitchedCollection<E> implements ObservableCollection<E> {
		private final ObservableCollection<E> theWrapped;
		private final Equivalence<? super E> theEquivalence;

		/**
		 * @param wrap The source collection
		 * @param equivalence The equivalence set to use for containment operations
		 */
		protected EquivalenceSwitchedCollection(ObservableCollection<E> wrap, Equivalence<? super E> equivalence) {
			theWrapped = wrap;
			theEquivalence = equivalence;
		}

		/** @return The source collection */
		protected ObservableCollection<E> getWrapped() {
			return theWrapped;
		}

		@Override
		public TypeToken<E> getType() {
			return theWrapped.getType();
		}

		@Override
		public Equivalence<? super E> equivalence() {
			return theEquivalence;
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
		public int size() {
			return theWrapped.size();
		}

		@Override
		public boolean isEmpty() {
			return theWrapped.isEmpty();
		}

		@Override
		public ObservableElementSpliterator<E> spliterator() {
			return theWrapped.spliterator();
		}

		@Override
		public MutableObservableSpliterator<E> mutableSpliterator() {
			return theWrapped.mutableSpliterator();
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
			return theWrapped.canAdd(value);
		}

		@Override
		public boolean add(E e) {
			return theWrapped.add(e);
		}

		@Override
		public boolean addAll(Collection<? extends E> c) {
			return theWrapped.addAll(c);
		}

		@Override
		public String canRemove(Object value) {
			return theWrapped.canRemove(value);
		}

		@Override
		public boolean remove(Object o) {
			return ObservableCollectionImpl.remove(this, o);
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
			theWrapped.clear();
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
			return theWrapped.onChange(observer);
		}
	}

	/**
	 * Implements {@link ObservableCollection#filterMap(ObservableCollection.FilterMapDef)}
	 *
	 * @param <E> The type of the collection to filter/map
	 * @param <T> The type of the filter/mapped collection
	 */
	public static class FilterMappedObservableCollection<E, T> implements ObservableCollection<T> {
		private final ObservableCollection<E> theWrapped;
		private final FilterMapDef<E, ?, T> theDef;
		private final Equivalence<? super T> theEquivalence;

		/**
		 * @param wrap The source collection
		 * @param filterMapDef The filter-mapping definition defining how the source values are to be filtered and mapped
		 */
		protected FilterMappedObservableCollection(ObservableCollection<E> wrap, FilterMapDef<E, ?, T> filterMapDef) {
			theWrapped = wrap;
			theDef = filterMapDef;
			if (filterMapDef.isReversible())
				theEquivalence = theWrapped.equivalence().map(filterMapDef.destType.getRawType(), //
					v -> filterMapDef.map(new FilterMapResult<>((E) v)).result, //
					v -> filterMapDef.reverse(new FilterMapResult<>((T) v)).result, //
					v -> filterMapDef.reverse(new FilterMapResult<>((T) v)).error == null);
			else
				theEquivalence = Equivalence.DEFAULT;
		}

		/** @return The source collection */
		protected ObservableCollection<E> getWrapped() {
			return theWrapped;
		}

		/** @return The filter-mapping definition defining how the source values are filtered and mapped */
		protected FilterMapDef<E, ?, T> getDef() {
			return theDef;
		}

		@Override
		public TypeToken<T> getType() {
			return theDef.destType;
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
		public Equivalence<? super T> equivalence() {
			return theEquivalence;
		}

		@Override
		public int size() {
			if (!theDef.isFiltered())
				return theWrapped.size();

			int[] size = new int[1];
			FilterMapResult<E, T> result = new FilterMapResult<>();
			theWrapped.spliterator().forEachRemaining(v -> {
				result.source = v;
				theDef.checkSourceValue(result);
				if (result.error == null)
					size[0]++;
			});
			return size[0];
		}

		@Override
		public boolean isEmpty() {
			if (theWrapped.isEmpty())
				return true;
			else if (!theDef.isFiltered())
				return false;
			FilterMapResult<E, T> result = new FilterMapResult<>();
			boolean[] contained = new boolean[1];
			while (!contained[0] && theWrapped.spliterator().tryAdvance(v -> {
				result.source = v;
				theDef.checkSourceValue(result);
				if (result.error == null)
					contained[0] = true;
			})) {
			}
			return !contained[0];
		}

		protected MutableObservableSpliteratorMap<E, T> map() {
			return new MutableObservableSpliteratorMap<E, T>() {
				@Override
				public TypeToken<T> getType() {
					return FilterMappedObservableCollection.this.getType();
				}

				@Override
				public boolean test(E srcValue) {
					if (!theDef.isFiltered())
						return true;
					return theDef.map(new FilterMapResult<>(srcValue)).error == null;
				}

				@Override
				public T map(E value) {
					return theDef.map(new FilterMapResult<>(value)).result;
				}

				@Override
				public E reverse(T value) {
					if (!theDef.isReversible())
						throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
					return theDef.reverse(new FilterMapResult<>(value)).result;
				}

				@Override
				public String filterEnabled(CollectionElement<E> el) {
					if (!theDef.isReversible())
						return StdMsg.UNSUPPORTED_OPERATION;
					return null;
				}

				@Override
				public String filterRemove(CollectionElement<E> sourceEl) {
					return null;
				}

				@Override
				public long filterExactSize(long srcSize) {
					return getDef().isFiltered() ? -1 : srcSize;
				}
			};
		}

		@Override
		public ObservableElementSpliterator<T> spliterator() {
			return theWrapped.spliterator().map(map());
		}

		@Override
		public MutableObservableSpliterator<T> mutableSpliterator() {
			return theWrapped.mutableSpliterator().map(map());
		}

		@Override
		public boolean contains(Object o) {
			if (!theDef.checkDestType(o))
				return false;
			if (!theDef.isReversible())
				return ObservableCollectionImpl.contains(this, o);
			FilterMapResult<T, E> reversed = theDef.reverse(new FilterMapResult<>((T) o));
			if (reversed.error != null)
				return false;
			return theWrapped.contains(reversed.result);
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			return ObservableCollectionImpl.containsAll(this, c);
		}

		@Override
		public boolean containsAny(Collection<?> c) {
			return ObservableCollectionImpl.containsAll(this, c);
		}

		/**
		 * @param input The collection to reverse
		 * @return The collection, with its elements {@link ObservableCollection.FilterMapDef#reverse(FilterMapResult) reversed}
		 */
		protected List<E> reverse(Collection<?> input) {
			FilterMapResult<T, E> reversed = new FilterMapResult<>();
			return input.stream().<E> flatMap(v -> {
				if (!theDef.checkDestType(v))
					return Stream.empty();
				reversed.source = (T) v;
				theDef.reverse(reversed);
				if (reversed.error == null)
					return Stream.of(reversed.result);
				else
					return Stream.empty();
			}).collect(Collectors.toList());
		}

		@Override
		public String canAdd(T value) {
			if (!theDef.isReversible())
				return StdMsg.UNSUPPORTED_OPERATION;
			else if (!theDef.checkDestType(value))
				return StdMsg.BAD_TYPE;
			FilterMapResult<T, E> reversed = theDef.reverse(new FilterMapResult<>(value));
			if (reversed.error != null)
				return reversed.error;
			return theWrapped.canAdd(reversed.result);
		}

		@Override
		public boolean add(T e) {
			if (!theDef.isReversible() || !theDef.checkDestType(e))
				return false;
			FilterMapResult<T, E> reversed = theDef.reverse(new FilterMapResult<>(e));
			if (reversed.error != null)
				return false;
			return theWrapped.add(reversed.result);
		}

		@Override
		public boolean addAll(Collection<? extends T> c) {
			if (!theDef.isReversible())
				return false;
			return theWrapped.addAll(reverse(c));
		}

		@Override
		public String canRemove(Object value) {
			if (!theDef.isReversible())
				return StdMsg.UNSUPPORTED_OPERATION;
			else if (!theDef.checkDestType(value))
				return StdMsg.BAD_TYPE;
			FilterMapResult<T, E> reversed = theDef.reverse(new FilterMapResult<>((T) value));
			if (reversed.error != null)
				return reversed.error;
			return theWrapped.canRemove(reversed.result);
		}

		@Override
		public boolean remove(Object o) {
			if (o != null && !theDef.checkDestType(o))
				return false;
			if (theDef.isReversible()) {
				FilterMapResult<T, E> reversed = theDef.reverse(new FilterMapResult<>((T) o));
				if (reversed.error != null)
					return false;
				return theWrapped.remove(reversed.result);
			} else
				return ObservableCollectionImpl.remove(this, o);
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
			try (Transaction t = lock(true, null)) {
				FilterMapResult<E, T> result = new FilterMapResult<>();
				theWrapped.mutableSpliterator().forEachMutableElement(el -> {
					result.source = el.get();
					theDef.checkSourceValue(result);
					if (result.error == null)
						el.remove();
				});
			}
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends T>> observer) {
			DefaultTreeSet<ElementId> elements = new DefaultTreeSet<>(Comparable::compareTo);
			try (Transaction t = lock(false, null)) {
				initListening(elements, null);
				return onChange(elements, observer);
			}
		}

		@Override
		public CollectionSubscription subscribe(Consumer<? super ObservableCollectionEvent<? extends T>> observer) {
			DefaultTreeSet<ElementId> elements = new DefaultTreeSet<>(Comparable::compareTo);
			Subscription changeSub;
			try (Transaction t = lock(false, null)) {
				initListening(elements, observer);
				changeSub = onChange(elements, observer);
			}
			return removeAll -> {
				try (Transaction t = lock(false, null)) {
					changeSub.unsubscribe();
					spliterator().forEachObservableElement(el -> observer
						.accept(new ObservableCollectionEvent<>(el.getElementId(), CollectionChangeType.remove, el.get(), el.get(), null)));
				}
			};
		}

		private void initListening(DefaultTreeSet<ElementId> elements, Consumer<? super ObservableCollectionEvent<? extends T>> observer) {
			spliterator().forEachObservableElement(el -> {
				elements.add(el.getElementId());
				if (observer != null)
					observer.accept(new ObservableCollectionEvent<>(el.getElementId(), CollectionChangeType.add, null, el.get(), null));
			});
		}

		private Subscription onChange(DefaultTreeSet<ElementId> elements,
			Consumer<? super ObservableCollectionEvent<? extends T>> observer) {
			return theWrapped.onChange(new FilterMappedObserver(observer));
		}

		/** An observer on the wrapped collection that pipes filter-mapped events to an observer on this collection */
		protected class FilterMappedObserver implements Consumer<ObservableCollectionEvent<? extends E>> {
			private final Consumer<? super ObservableCollectionEvent<? extends T>> theObserver;
			private final Object theMetadata;

			/** @param observer The observer for this collection */
			protected FilterMappedObserver(Consumer<? super ObservableCollectionEvent<? extends T>> observer) {
				theObserver = observer;
				theMetadata = createSubscriptionMetadata();
			}

			@Override
			public void accept(ObservableCollectionEvent<? extends E> evt) {
				switch (evt.getType()) {
				case add:
					FilterMapResult<E, T> res = getDef().map(new FilterMapResult<>(evt.getNewValue()));
					if (res.error == null)
						ObservableCollectionEvent.doWith(map(evt, evt.getType(), null, res.result, theMetadata), theObserver);
					break;
				case remove:
					res = getDef().map(new FilterMapResult<>(evt.getOldValue()));
					if (res.error == null)
						ObservableCollectionEvent.doWith(map(evt, evt.getType(), res.result, res.result, theMetadata), theObserver);
					break;
				case set:
					res = getDef().map(new FilterMapResult<>(evt.getOldValue()));
					FilterMapResult<E, T> newRes = getDef().map(new FilterMapResult<>(evt.getNewValue()));
					if (res.error == null) {
						if (newRes.error == null)
							ObservableCollectionEvent.doWith(map(evt, evt.getType(), res.result, newRes.result, theMetadata), theObserver);
						else
							ObservableCollectionEvent.doWith(map(evt, CollectionChangeType.remove, res.result, res.result, theMetadata),
								theObserver);
					} else if (newRes.error == null)
						ObservableCollectionEvent.doWith(map(evt, CollectionChangeType.add, null, newRes.result, theMetadata), theObserver);
					break;
				}
			}
		}

		/**
		 * @return Metadata that will be passed to {@link #map(ObservableCollectionEvent, CollectionChangeType, Object, Object, Object)} to
		 *         keep track of changes for subclasses, if needed
		 */
		protected Object createSubscriptionMetadata() {
			return null;
		}

		/**
		 * @param cause The event from the source collection
		 * @param type The type of the event to fire
		 * @param oldValue The old value for the event
		 * @param newValue The new value for the event
		 * @param metadata The metadata for the subscription, created by {@link #createSubscriptionMetadata()}
		 * @return The event to fire to a listener to this collection
		 */
		protected ObservableCollectionEvent<T> map(ObservableCollectionEvent<? extends E> cause, CollectionChangeType type, T oldValue,
			T newValue, Object metadata) {
			return new ObservableCollectionEvent<>(cause.getElementId(), type, oldValue, newValue, cause);
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
	 * Implements {@link ObservableCollection#combine(ObservableCollection.CombinedCollectionDef)}
	 *
	 * @param <E> The type of the collection to be combined
	 * @param <V> The type of the combined collection
	 */
	public static class CombinedObservableCollection<E, V> implements ObservableCollection<V> {
		private final ObservableCollection<E> theWrapped;
		private final CombinedCollectionDef<E, V> theDef;
		private final Equivalence<? super V> theEquivalence;

		/**
		 * @param wrap The collection whose values are to be combined
		 * @param def The combination definition containing the other values to be combined and the combination functions and settings
		 */
		protected CombinedObservableCollection(ObservableCollection<E> wrap, CombinedCollectionDef<E, V> def) {
			theWrapped = wrap;
			theDef = def;
			if (def.getReverse() != null)
				theEquivalence = theWrapped.equivalence().map(def.targetType.getRawType(),
					v -> def.getCombination().apply(new DynamicCombinedValues<>((E) v)),
					v -> def.getReverse().apply(new DynamicCombinedValues<>((V) v)), v -> v != null || def.areNullsReversed());
			else
				theEquivalence = Equivalence.DEFAULT;
		}

		/** @return The collection whose values are the source for this collection */
		protected ObservableCollection<E> getWrapped() {
			return theWrapped;
		}

		/** @return The combination definition containing the other values to be combined and the combination functions and settings */
		protected CombinedCollectionDef<E, V> getDef() {
			return theDef;
		}

		@Override
		public Equivalence<? super V> equivalence() {
			return theEquivalence;
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
		public TypeToken<V> getType() {
			return theDef.targetType;
		}

		@Override
		public int size() {
			return theWrapped.size();
		}

		@Override
		public boolean isEmpty() {
			return theWrapped.isEmpty();
		}

		@Override
		public boolean contains(Object o) {
			if (o!=null && !theDef.targetType.getRawType().isInstance(o))
				return false;
			if (theDef.getReverse() != null && (o != null || theDef.areNullsReversed()))
				return theWrapped.contains(theDef.getReverse().apply(combineDynamic((V) o)));
			else
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
		public String canAdd(V value) {
			if (theDef.getReverse() == null)
				return StdMsg.UNSUPPORTED_OPERATION;
			else if (value == null && !theDef.areNullsReversed())
				return StdMsg.NULL_DISALLOWED;
			else
				return theWrapped.canAdd(theDef.getReverse().apply(new DynamicCombinedValues<>(value)));
		}

		@Override
		public boolean add(V e) {
			if (theDef.getReverse() == null)
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			else if (e == null || !theDef.areNullsReversed())
				throw new UnsupportedOperationException(StdMsg.NULL_DISALLOWED);
			else
				return theWrapped.add(theDef.getReverse().apply(new DynamicCombinedValues<>(e)));
		}

		@Override
		public boolean addAll(Collection<? extends V> c) {
			if (theDef.getReverse() == null)
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			if (!theDef.areNullsReversed()) {
				for (V v : c)
					if (v == null)
						throw new UnsupportedOperationException(StdMsg.NULL_DISALLOWED);
			}
			Map<ObservableValue<?>, Object> argValues = new HashMap<>(theDef.getArgs().size() * 4 / 3);
			for (ObservableValue<?> arg : theDef.getArgs())
				argValues.put(arg, arg.get());
			StaticCombinedValues<V> combined = new StaticCombinedValues<>();
			combined.argValues = argValues;
			return theWrapped.addAll(c.stream().map(o -> {
				combined.element = o;
				return theDef.getReverse().apply(combined);
			}).collect(Collectors.toList()));
		}

		@Override
		public ObservableCollection<V> addValues(V... values) {
			addAll(Arrays.asList(values));
			return this;
		}

		@Override
		public String canRemove(Object value) {
			if (theDef.getReverse() != null) {
				if (value == null && !theDef.areNullsReversed())
					return StdMsg.NULL_DISALLOWED;
				else if (value != null && theDef.targetType.getRawType().isInstance(value))
					return StdMsg.BAD_TYPE;

				Map<ObservableValue<?>, Object> argValues = new HashMap<>(theDef.getArgs().size() * 4 / 3);
				for (ObservableValue<?> arg : theDef.getArgs())
					argValues.put(arg, arg.get());
				StaticCombinedValues<V> combined = new StaticCombinedValues<>();
				combined.argValues = argValues;
				combined.element = (V) value;
				return theWrapped.canRemove(theDef.getReverse().apply(combined));
			} else {
				Map<ObservableValue<?>, Object> argValues = new HashMap<>(theDef.getArgs().size() * 4 / 3);
				for (ObservableValue<?> arg : theDef.getArgs())
					argValues.put(arg, arg.get());
				StaticCombinedValues<E> combined = new StaticCombinedValues<>();
				combined.argValues = argValues;

				String[] msg = new String[1];
				boolean[] found = new boolean[1];
				try (Transaction t = lock(false, null)) {
					ElementSpliterator<E> spliter = theWrapped.mutableSpliterator();
					while (!found[0] && spliter.tryAdvanceElement(el -> {
						combined.element = el.get();
						// If we're not reversible, then the default equivalence is used
						if (Objects.equals(theDef.getCombination().apply(combined), value)) {
							found[0] = true;
							msg[0] = el.canRemove();
						}
					})) {
					}
				}
				if (!found[0])
					msg[0] = StdMsg.NOT_FOUND;
				return msg[0];
			}
		}

		@Override
		public boolean remove(Object value) {
			if (theDef.getReverse() != null) {
				if (value == null && !theDef.areNullsReversed())
					return false;
				else if (value != null && theDef.targetType.getRawType().isInstance(value))
					return false;

				Map<ObservableValue<?>, Object> argValues = new HashMap<>(theDef.getArgs().size() * 4 / 3);
				for (ObservableValue<?> arg : theDef.getArgs())
					argValues.put(arg, arg.get());
				StaticCombinedValues<V> combined = new StaticCombinedValues<>();
				combined.argValues = argValues;
				combined.element = (V) value;
				return theWrapped.remove(theDef.getReverse().apply(combined));
			} else
				return ObservableCollectionImpl.remove(this, value);
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
			getWrapped().clear();
		}

		protected MutableObservableSpliteratorMap<E, V> map() {
			return new MutableObservableSpliteratorMap<E, V>() {
				@Override
				public TypeToken<V> getType() {
					return CombinedObservableCollection.this.getType();
				}

				@Override
				public V map(E value) {
					return combine(value);
				}

				@Override
				public boolean test(E srcValue) {
					return true;
				}

				@Override
				public E reverse(V value) {
					if (getDef().getReverse() == null)
						throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
					return getDef().getReverse().apply(new DynamicCombinedValues<>(value));
				}

				@Override
				public String filterEnabled(CollectionElement<E> el) {
					if (getDef().getReverse() == null)
						return StdMsg.UNSUPPORTED_OPERATION;
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
			};
		}

		@Override
		public MutableObservableSpliterator<V> mutableSpliterator() {
			return theWrapped.mutableSpliterator().map(map());
		}

		/**
		 * @param value The value from the wrapped collection
		 * @return The corresponding value in this collection
		 */
		protected V combine(E value) {
			return theDef.getCombination().apply(combineDynamic(value));
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends V>> observer) {
			return defaultOnChange(this, observer);
		}

		@Override
		public CollectionSubscription subscribe(Consumer<? super ObservableCollectionEvent<? extends V>> observer) {
			CombinedObserver combinedObs = new CombinedObserver(observer);
			if (combinedObs.isComplete()) {
				return removeAll -> {
				};
			}
			try (Transaction t = theWrapped.lock(false, null)) {
				combinedObs.init(theWrapped.subscribe(combinedObs));
			}
			return combinedObs;
		}

		/** A subscription for the wrapped collection that forwards events for combined values to an observer on this collection */
		protected class CombinedObserver implements Consumer<ObservableCollectionEvent<? extends E>>, CollectionSubscription {
			private final Consumer<? super ObservableCollectionEvent<? extends V>> theObserver;
			private final Object theMetadata;
			private boolean isInitialized;
			private boolean isComplete;
			private final Subscription[] theArgSubs;
			private final Map<ObservableValue<?>, Object> theArgOldValues;
			private final Map<ObservableValue<?>, Object> theArgValues;
			private StaticCombinedValues<E> theCombined;
			private CollectionSubscription theCollectionSub;

			/** @param observer The observer on this collection */
			protected CombinedObserver(Consumer<? super ObservableCollectionEvent<? extends V>> observer) {
				theObserver = observer;
				theMetadata = createSubscriptionMetadata();
				theArgSubs = new Subscription[theDef.getArgs().size()];
				theArgOldValues = new HashMap<>(theDef.getArgs().size() * 4 / 3);
				theArgValues = new HashMap<>(theDef.getArgs().size() * 4 / 3);
				theCombined = new StaticCombinedValues<>();

				int a = 0;
				for (ObservableValue<?> arg : theDef.getArgs()) {
					theArgSubs[a++] = arg.subscribe(new Observer<ObservableValueEvent<?>>() {
						@Override
						public <V2 extends ObservableValueEvent<?>> void onNext(V2 event) {
							try (Transaction t = isInitialized ? theWrapped.lock(false, null) : Transaction.NONE) {
								theArgValues.put(event.getObservable(), StaticCombinedValues.valueFor(event.getValue()));
								if (isInitialized) {
									theWrapped.spliterator().forEachObservableElement(el -> {
										theCombined.element = el.get();
										theCombined.argValues = theArgOldValues;
										V oldValue = theDef.getCombination().apply(theCombined);
										theCombined.argValues = theArgValues;
										V newValue = theDef.getCombination().apply(theCombined);
										ObservableCollectionEvent.doWith(createEvent(el.getElementId(), CollectionChangeType.set, oldValue,
											newValue, event, theMetadata), theObserver);
									});
								}
							}
						}

						@Override
						public <V2 extends ObservableValueEvent<?>> void onCompleted(V2 event) {
							unsubscribe(true);
						}
					});
					if (isComplete)
						break;
				}
			}

			@Override
			public void accept(ObservableCollectionEvent<? extends E> evt) {
				theCombined.argValues = theArgValues;
				V oldValue = null, newValue = null;
				switch (evt.getType()) {
				case add:
					theCombined.element = evt.getNewValue();
					newValue = theDef.getCombination().apply(theCombined);
					break;
				case remove:
					theCombined.element = evt.getOldValue();
					oldValue = theDef.getCombination().apply(theCombined);
					break;
				case set:
					theCombined.element = evt.getOldValue();
					oldValue = theDef.getCombination().apply(theCombined);
					theCombined.element = evt.getNewValue();
					newValue = theDef.getCombination().apply(theCombined);
					break;
				}
				ObservableCollectionEvent.doWith(createEvent(evt.getElementId(), evt.getType(), oldValue, newValue, evt, theMetadata),
					theObserver);
			}

			void init(CollectionSubscription collSub) {
				theCollectionSub = collSub;
				isInitialized = true;
			}

			boolean isComplete() {
				return isComplete;
			}

			@Override
			public void unsubscribe(boolean removeAll) {
				try (Transaction t = isInitialized ? theWrapped.lock(false, null) : Transaction.NONE) {
					if (isComplete)
						return;
					if (theCollectionSub != null) {
						theCollectionSub.unsubscribe(removeAll);
						theCollectionSub = null;
					}
					int a = 0;
					for (ObservableValue<?> arg : theDef.getArgs()) {
						if (arg != null)
							theArgSubs[a].unsubscribe();
						theArgSubs[a++] = null;
					}
					isComplete = true;
				}
			}
		}

		/** @return Metadata that a subclass may use to keep track of elements for the life of a subscription */
		protected Object createSubscriptionMetadata() {
			return null;
		}

		/**
		 * @param elementId The ID of the element that changed
		 * @param type The type of the event
		 * @param oldValue The old value for the event
		 * @param newValue The new value for the event
		 * @param cause The cause of the event
		 * @param metadata The metadata for the subscription
		 * @return The event to fire to the listener
		 */
		protected ObservableCollectionEvent<V> createEvent(ElementId elementId, CollectionChangeType type, V oldValue, V newValue,
			Object cause, Object metadata) {
			return new ObservableCollectionEvent<>(elementId, type, oldValue, newValue, cause);
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

		/**
		 * @param <T> The type of the input value
		 * @param value The input value
		 * @return A combined values object with the given element
		 */
		protected <T> CombinedValues<T> combineDynamic(T value) {
			return new DynamicCombinedValues<>(value);
		}

		/**
		 * Simple {@link ObservableCollection.CombinedValues} implementation that uses the current ({@link ObservableValue#get()}) values of
		 * the observables in the combo definition
		 *
		 * @param <T> The type of the combined value
		 */
		protected class DynamicCombinedValues<T> implements CombinedValues<T> {
			private final T theElement;

			DynamicCombinedValues(T element) {
				theElement = element;
			}

			@Override
			public T getElement() {
				return theElement;
			}

			@Override
			public <T2> T2 get(ObservableValue<T2> arg) {
				if (!theDef.getArgs().contains(arg))
					throw new IllegalArgumentException("Unrecognized argument value: " + arg);
				return arg.get();
			}
		}

		/**
		 * Simple {@link ObservableCollection.CombinedValues} implementation that takes the values of the observables
		 *
		 * @param <T> The type of the combined value
		 */
		protected static class StaticCombinedValues<T> implements CombinedValues<T> {
			static final Object NULL = new Object();

			T element;
			/** The arg values for this value structure */
			protected Map<ObservableValue<?>, Object> argValues;

			@Override
			public T getElement() {
				return element;
			}

			static <T> T valueFor(T value) {
				return value == null ? (T) NULL : value;
			}

			@Override
			public <T2> T2 get(ObservableValue<T2> arg) {
				Object value = argValues.get(arg);
				if (value == null)
					throw new IllegalArgumentException("Unrecognized value: " + arg);
				return value == NULL ? null : (T2) value;
			}
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

			ObservableCollection<K> mapped = theWrapped.buildMap(theBuilder.getKeyType())
				.map(theBuilder.getKeyMaker(), theBuilder.areNullsMapped()).build();
			theKeySet = unique(mapped);
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
		protected ObservableSet<K> unique(ObservableCollection<K> keyCollection) {
			return keyCollection.unique();
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
			return theWrapped.buildMap(theWrapped.getType()).filter(v -> {
				if (v != null || theBuilder.areNullsMapped())
					return theBuilder.getEquivalence().elementEquals((K) key, theBuilder.getKeyMaker().apply(v)) ? null
						: StdMsg.WRONG_GROUP;
				else
					return key == null ? null : StdMsg.WRONG_GROUP;
			}, true).build();
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
	 * An entry in a {@link ObservableCollectionImpl.GroupedMultiMap}
	 *
	 * @param <K> The key type of the entry
	 * @param <E> The value type of the entry
	 */
	public static class GroupedMultiEntry<K, E> implements ObservableMultiMap.ObservableMultiEntry<K, E> {
		private final K theKey;
		private final ObservableCollection<E> theElements;

		/**
		 * @param key The map key that this entry is for
		 * @param wrap The collection whose values to present
		 * @param builder The grouping builder used to construct the map
		 */
		protected GroupedMultiEntry(K key, ObservableCollection<E> wrap, GroupingBuilder<E, K> builder) {
			theKey = key;
			theElements = wrap
				.filter(el -> builder.getEquivalence().elementEquals(theKey, builder.getKeyMaker().apply(el)) ? null : StdMsg.WRONG_GROUP);
		}

		@Override
		public K getKey() {
			return theKey;
		}

		@Override
		public TypeToken<E> getType() {
			return theElements.getType();
		}

		@Override
		public Equivalence<? super E> equivalence() {
			return theElements.equivalence();
		}

		@Override
		public boolean isLockSupported() {
			return theElements.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theElements.lock(write, cause);
		}

		@Override
		public int size() {
			return theElements.size();
		}

		@Override
		public boolean isEmpty() {
			return theElements.isEmpty();
		}

		@Override
		public boolean contains(Object o) {
			return theElements.contains(o);
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			return theElements.containsAll(c);
		}

		@Override
		public boolean containsAny(Collection<?> c) {
			return theElements.containsAny(c);
		}

		@Override
		public MutableObservableSpliterator<E> mutableSpliterator() {
			return theElements.mutableSpliterator();
		}

		@Override
		public String canAdd(E value) {
			return theElements.canAdd(value);
		}

		@Override
		public boolean add(E e) {
			return theElements.add(e);
		}

		@Override
		public boolean remove(Object o) {
			return theElements.remove(o);
		}

		@Override
		public boolean addAll(Collection<? extends E> c) {
			return theElements.addAll(c);
		}

		@Override
		public String canRemove(Object value) {
			return theElements.canRemove(value);
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			return theElements.removeAll(c);
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			return theElements.retainAll(c);
		}

		@Override
		public void clear() {
			theElements.clear();
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
			return theElements.onChange(observer);
		}

		@Override
		public CollectionSubscription subscribe(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
			return theElements.subscribe(observer);
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			return ObservableCollection.equals(this, o);
		}

		@Override
		public int hashCode() {
			return ObservableCollection.hashCode(this);
		}

		@Override
		public String toString() {
			return ObservableCollection.toString(this);
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
		protected ObservableSortedSet<K> unique(ObservableCollection<K> keyCollection) {
			return ObservableSortedSet.unique(keyCollection, getBuilder().getCompare());
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
	 * Implements {@link ObservableCollection#refresh(Observable)}
	 *
	 * @param <E> The type of the collection to refresh
	 */
	public static class RefreshingCollection<E> implements ObservableCollection<E> {
		private final ObservableCollection<E> theWrapped;
		private final Observable<?> theRefresh;

		/**
		 * @param wrap The collection whose content to reflect
		 * @param refresh The observable to refresh the collection's content with
		 */
		protected RefreshingCollection(ObservableCollection<E> wrap, Observable<?> refresh) {
			theWrapped = wrap;
			theRefresh = refresh;
		}

		/** @return The collection whose content is reflected */
		protected ObservableCollection<E> getWrapped() {
			return theWrapped;
		}

		/** @return The observable refreshing this collection's content */
		protected Observable<?> getRefresh() {
			return theRefresh;
		}

		@Override
		public TypeToken<E> getType() {
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
		public Equivalence<? super E> equivalence() {
			return theWrapped.equivalence();
		}

		@Override
		public int size() {
			return theWrapped.size();
		}

		@Override
		public boolean isEmpty() {
			return theWrapped.isEmpty();
		}

		@Override
		public boolean contains(Object o) {
			return theWrapped.contains(o);
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			return theWrapped.containsAll(c);
		}

		@Override
		public boolean containsAny(Collection<?> c) {
			return theWrapped.containsAny(c);
		}

		@Override
		public String canAdd(E value) {
			return theWrapped.canAdd(value);
		}

		@Override
		public boolean add(E e) {
			return theWrapped.add(e);
		}

		@Override
		public boolean addAll(Collection<? extends E> c) {
			return theWrapped.addAll(c);
		}

		@Override
		public String canRemove(Object value) {
			return theWrapped.canRemove(value);
		}

		@Override
		public boolean remove(Object o) {
			return theWrapped.remove(o);
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			return theWrapped.removeAll(c);
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			return theWrapped.retainAll(c);
		}

		@Override
		public void clear() {
			theWrapped.clear();
		}

		@Override
		public MutableObservableSpliterator<E> mutableSpliterator() {
			return theWrapped.mutableSpliterator();
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
			return defaultOnChange(theWrapped, observer);
		}

		@Override
		public CollectionSubscription subscribe(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
			CollectionSubscription collSub = theWrapped.subscribe(observer);
			Subscription refreshSub = theRefresh.act(v -> {
				// There's a possibility that the refresh observable could fire on one thread while the collection fires on
				// another, so need to make sure the collection isn't firing while this refresh event happens.
				try (Transaction t = theWrapped.lock(false, v)) {
					doRefresh(observer, v);
				}
			});
			return removeAll -> {
				refreshSub.unsubscribe();
				collSub.unsubscribe(removeAll);
			};
		}

		/**
		 * Does the refresh when this collection's {@link #getRefresh() refresh} observable fires
		 *
		 * @param observer The observer to fire the events to
		 * @param cause The object fired from the refresh observable
		 */
		protected void doRefresh(Consumer<? super ObservableCollectionEvent<? extends E>> observer, Object cause) {
			theWrapped.spliterator().forEachObservableElement(el -> {
				ObservableCollectionEvent.doWith(
					new ObservableCollectionEvent<>(el.getElementId(), CollectionChangeType.set, el.get(), el.get(), cause),
					observer::accept);
			});
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
			return theWrapped.toString();
		}
	}

	/**
	 * Implements {@link ObservableCollection#refreshEach(Function)}
	 *
	 * @param <E> The type of the collection to refresh
	 */
	public static class ElementRefreshingCollection<E> implements ObservableCollection<E> {
		private final ObservableCollection<E> theWrapped;

		private final Function<? super E, Observable<?>> theRefresh;

		/**
		 * @param wrap The collection whose content to reflect
		 * @param refresh A function that will refresh each element independently
		 */
		protected ElementRefreshingCollection(ObservableCollection<E> wrap, Function<? super E, Observable<?>> refresh) {
			theWrapped = wrap;
			theRefresh = refresh;
		}

		/** @return The collection whose content this collection reflects */
		protected ObservableCollection<E> getWrapped() {
			return theWrapped;
		}

		/** @return The function giving the refresher for each element */
		protected Function<? super E, Observable<?>> getRefresh() {
			return theRefresh;
		}

		@Override
		public TypeToken<E> getType() {
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
		public Equivalence<? super E> equivalence() {
			return theWrapped.equivalence();
		}

		@Override
		public int size() {
			return theWrapped.size();
		}

		@Override
		public boolean isEmpty() {
			return theWrapped.isEmpty();
		}

		@Override
		public boolean contains(Object o) {
			return theWrapped.contains(o);
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			return theWrapped.containsAll(c);
		}

		@Override
		public boolean containsAny(Collection<?> c) {
			return theWrapped.containsAny(c);
		}

		@Override
		public String canAdd(E value) {
			return theWrapped.canAdd(value);
		}

		@Override
		public boolean add(E e) {
			return theWrapped.add(e);
		}

		@Override
		public boolean addAll(Collection<? extends E> c) {
			return theWrapped.addAll(c);
		}

		@Override
		public String canRemove(Object value) {
			return theWrapped.canRemove(value);
		}

		@Override
		public boolean remove(Object o) {
			return theWrapped.remove(o);
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			return theWrapped.removeAll(c);
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			return theWrapped.retainAll(c);
		}

		@Override
		public void clear() {
			theWrapped.clear();
		}

		@Override
		public MutableObservableSpliterator<E> mutableSpliterator() {
			return theWrapped.mutableSpliterator();
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
			return ObservableCollectionImpl.defaultOnChange(this, observer);
		}

		@Override
		public CollectionSubscription subscribe(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
			ElementRefreshingObserver refreshing = new ElementRefreshingObserver(observer);
			CollectionSubscription collSub = theWrapped.subscribe(refreshing);
			return removeAll -> {
				collSub.unsubscribe(removeAll);
				// If removeAll is true, elements should be empty
				if (!removeAll) {
					refreshing.done();
				}
			};
		}

		class ElementRefreshValue {
			E value;
			Subscription refreshSub;
		}

		/** An observer that also fires refresh events on the elements */
		protected class ElementRefreshingObserver implements Consumer<ObservableCollectionEvent<? extends E>> {
			private final Consumer<? super ObservableCollectionEvent<? extends E>> theObserver;
			private final Map<ElementId, ElementRefreshValue> theElements = createElementMap();

			/** @param observer The observer for this collection */
			public ElementRefreshingObserver(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
				theObserver = observer;
			}

			@Override
			public void accept(ObservableCollectionEvent<? extends E> evt) {
				theObserver.accept(evt);
				switch (evt.getType()) {
				case add:
					ElementRefreshValue erv=new ElementRefreshValue();
					erv.value = evt.getNewValue();
					erv.refreshSub = theRefresh.apply(erv.value).act(r -> {
						// There's a possibility that the refresh observable could fire on one thread while the collection fires on
						// another, so need to make sure the collection isn't firing while this refresh event happens.
						try (Transaction t = theWrapped.lock(false, r)) {
							ObservableCollectionEvent.doWith(refresh(evt.getElementId(), erv.value, theElements, r), theObserver);
						}
					});
					theElements.put(evt.getElementId(), erv);
					break;
				case remove:
					theElements.remove(evt.getElementId()).refreshSub.unsubscribe();
					break;
				case set:
					erv = theElements.get(evt.getElementId());
					erv.value = evt.getNewValue();
					erv.refreshSub.unsubscribe();
					erv.refreshSub = theRefresh.apply(erv.value).act(r -> {
						// There's a possibility that the refresh observable could fire on one thread while the collection fires on
						// another, so need to make sure the collection isn't firing while this refresh event happens.
						try (Transaction t = theWrapped.lock(false, r)) {
							ObservableCollectionEvent.doWith(refresh(evt.getElementId(), erv.value, theElements, r), theObserver);
						}
					});
					break;
				}
			}

			void done() {
				try (Transaction t = theWrapped.lock(false, null)) {
					for (ElementRefreshValue erv : theElements.values())
						erv.refreshSub.unsubscribe();
				}
				theElements.clear();
			}
		}

		/**
		 * @return A map of elements to value-holding structures to use for keeping track of values and subscriptions in
		 *         {@link #subscribe(Consumer)}
		 */
		protected <V> Map<ElementId, V> createElementMap() {
			return new HashMap<>();
		}

		/**
		 * Generates a refresh event on an element in response to a refresh event
		 *
		 * @param elementId The ID of the element that the refresh is for
		 * @param value The value of the element
		 * @param elements The element map created by {@link #createElementMap()}
		 * @param cause The value fired from the refresh observable
		 * @return The collection event to fire to this collection's listener
		 */
		protected ObservableCollectionEvent<E> refresh(ElementId elementId, E value, Map<ElementId, ?> elements, Object cause) {
			return new ObservableCollectionEvent<>(elementId, CollectionChangeType.set, value, value, cause);
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
			return theWrapped.toString();
		}
	}

	/**
	 * Implements {@link ObservableCollection#filterModification(ModFilterDef)}
	 *
	 * @param <E> The type of the collection to control
	 */
	public static class ModFilteredCollection<E> implements ObservableCollection<E> {
		private final ObservableCollection<E> theWrapped;
		private final org.observe.collect.ObservableCollection.ModFilterDef<E> theDef;

		/**
		 * @param wrapped The collection whose content to present
		 * @param def The modification-filter definition to determine which modifications to allow
		 */
		public ModFilteredCollection(ObservableCollection<E> wrapped, ModFilterDef<E> def) {
			theWrapped = wrapped;
			theDef = def;
		}

		/** @return The collection whose content this collection reflects */
		protected ObservableCollection<E> getWrapped() {
			return theWrapped;
		}

		/** @return The modification-filter definition determining which modifications are allowed */
		protected ModFilterDef<E> getDef() {
			return theDef;
		}

		@Override
		public TypeToken<E> getType() {
			return theWrapped.getType();
		}

		protected MutableObservableSpliteratorMap<E, E> map() {
			return new MutableObservableSpliteratorMap<E, E>() {
				@Override
				public TypeToken<E> getType() {
					return ModFilteredCollection.this.getType();
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
					return theDef.checkRemove(el.get());
				}

				@Override
				public String filterRemove(CollectionElement<E> sourceEl) {
					return theDef.checkRemove(sourceEl.get());
				}

				@Override
				public String filterAccept(E value) {
					return theDef.checkAdd(value);
				}

				@Override
				public long filterExactSize(long srcSize) {
					return srcSize;
				}
			};
		}

		@Override
		public MutableObservableSpliterator<E> mutableSpliterator() {
			return theWrapped.mutableSpliterator().map(map());
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
		public Equivalence<? super E> equivalence() {
			return theWrapped.equivalence();
		}

		@Override
		public int size() {
			return theWrapped.size();
		}

		@Override
		public boolean isEmpty() {
			return theWrapped.isEmpty();
		}

		@Override
		public boolean contains(Object o) {
			return theWrapped.contains(o);
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			return theWrapped.containsAll(c);
		}

		@Override
		public boolean containsAny(Collection<?> c) {
			return theWrapped.containsAny(c);
		}

		@Override
		public String canAdd(E value) {
			String s = theDef.checkAdd(value);
			if (s == null)
				s = theWrapped.canAdd(value);
			return s;
		}

		@Override
		public boolean add(E value) {
			if (theDef.checkAdd(value) == null)
				return theWrapped.add(value);
			else
				return false;
		}

		@Override
		public boolean addAll(Collection<? extends E> values) {
			if (theDef.isAddFiltered())
				return theWrapped.addAll(values.stream().filter(v -> theDef.checkAdd(v) == null).collect(Collectors.toList()));
			else
				return theWrapped.addAll(values);
		}

		@Override
		public ObservableCollection<E> addValues(E... values) {
			if (theDef.isAddFiltered())
				theWrapped.addAll(Arrays.stream(values).filter(v -> theDef.checkAdd(v) == null).collect(Collectors.toList()));
			else
				theWrapped.addValues(values);
			return this;
		}

		@Override
		public String canRemove(Object value) {
			String s = theDef.checkRemove(value);
			if (s == null)
				s = theWrapped.canRemove(value);
			return s;
		}

		@Override
		public boolean remove(Object value) {
			if (theDef.checkRemove(value) == null)
				return theWrapped.remove(value);
			else
				return false;
		}

		@Override
		public boolean removeAll(Collection<?> values) {
			if (theDef.isRemoveFiltered())
				return theWrapped.removeAll(values.stream().filter(v -> theDef.checkRemove(v) == null).collect(Collectors.toList()));
			else
				return theWrapped.removeAll(values);
		}

		@Override
		public boolean retainAll(Collection<?> values) {
			if (!theDef.isRemoveFiltered())
				return theWrapped.retainAll(values);

			boolean[] removed = new boolean[1];
			theWrapped.mutableSpliterator().forEachElement(el -> {
				E v = el.get();
				if (!values.contains(v) && theDef.checkRemove(v) == null) {
					el.remove();
					removed[0] = true;
				}
			});
			return removed[0];
		}

		@Override
		public void clear() {
			if (!theDef.isRemoveFiltered()) {
				theWrapped.clear();
				return;
			}

			theWrapped.mutableSpliterator().forEachElement(el -> {
				if (theDef.checkRemove(el.get()) == null)
					el.remove();
			});
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
			return theWrapped.onChange(observer);
		}

		@Override
		public CollectionSubscription subscribe(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
			return theWrapped.subscribe(observer);
		}

		@Override
		public int hashCode() {
			return theWrapped.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return theWrapped.equals(obj);
		}

		@Override
		public String toString() {
			return theWrapped.toString();
		}
	}

	/**
	 * Implements {@link ObservableCollection#cached(Observable)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	public static class CachedObservableCollection<E> implements ObservableCollection<E> {
		private final ObservableCollection<E> theWrapped;
		private final Observable<?> theUntil;
		private final NavigableMap<ElementId, E> theCacheMap;
		private final SimpleObservable<ObservableCollectionEvent<? extends E>> theChanges;
		private final BetterCollection<E> theCache;
		private final AtomicBoolean isDone;

		/**
		 * @param wrapped The collection whose values to reflect
		 * @param until The observable to listen to to cease caching
		 */
		protected CachedObservableCollection(ObservableCollection<E> wrapped, Observable<?> until) {
			theWrapped = wrapped;
			theUntil = until;
			theChanges = new SimpleObservable<>();
			theCacheMap = createCacheMap();
			theCache = createCache();
			isDone = new AtomicBoolean();
			beginCache();
		}

		/** @return The collection whose values this collection reflects */
		protected ObservableCollection<E> getWrapped() {
			return theWrapped;
		}

		/** @return The observable that, when it fires, will cause this collection to cease caching */
		protected Observable<?> getUntil() {
			return theUntil;
		}

		/** @return Whether this cache's {@link #getUntil() finisher} has fired */
		protected boolean isDone() {
			return isDone.get();
		}

		/**
		 * Creates the collection to be the cache for this collection. The cache must use the same {@link #equivalence() equivalence} as the
		 * wrapped collection. It need not be thread-safe.
		 *
		 * The default implementation of this method returns a List. This may be unnecessary in general, but it may be useful for many
		 * specific implementations.
		 *
		 * @return The cache collection for this cache to use
		 */
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

		/** @return The map of values by element ID for this cache */
		protected NavigableMap<ElementId, E> createCacheMap() {
			return new TreeMap<>();
		}

		/** @return This cache's collection */
		protected BetterCollection<E> getCache() {
			return theCache;
		}

		/** @return The map of values by element ID for this cache */
		protected Map<ElementId, E> getCacheMap() {
			return theCacheMap;
		}

		/** @return The observable firing changes to this collection's content */
		protected Observable<? extends ObservableCollectionEvent<? extends E>> getChanges() {
			return theChanges;
		}

		/** Subscribes to the wrapped collection to update the cache until the observable fires */
		protected void beginCache() {
			CollectionSubscription collSub = theWrapped.subscribe(evt -> {
				switch (evt.getType()) {
				case add:
				case set:
					theCacheMap.put(evt.getElementId(), evt.getNewValue());
					break;
				case remove:
					theCacheMap.remove(evt.getElementId());
					break;
				}
				updateCache(evt);
				ObservableCollectionEvent.doWith(wrapEvent(evt), theChanges::onNext);
			});
			theUntil.take(1).act(u -> {
				collSub.unsubscribe(true);
				isDone.set(true);
			});
		}

		/**
		 * Updates the cache collection for the change
		 *
		 * @param change The change event from the source
		 */
		protected void updateCache(ObservableCollectionEvent<? extends E> change) {
			switch (change.getType()) {
			case add:
				getCache().add(change.getNewValue());
				break;
			case remove:
				getCache().remove(change.getOldValue());
				break;
			case set:
				getCache().find(v -> equivalence().elementEquals(v, change.getOldValue()),
					el -> ((CollectionElement<E>) el).set(change.getNewValue(), change));
				break;
			}
		}

		/**
		 * @param change The change from the source collection
		 * @return The change to fire to this cache collection's listeners
		 */
		protected ObservableCollectionEvent<? extends E> wrapEvent(ObservableCollectionEvent<? extends E> change) {
			return new ObservableCollectionEvent<>(change.getElementId(), change.getType(), change.getOldValue(), change.getNewValue(),
				change);
		}

		/**
		 * @param value The value in the cache
		 * @param elementId The element ID for the value
		 * @return The event to fire to the listener that has just been added
		 */
		protected ObservableCollectionEvent<? extends E> initialEvent(E value, ElementId elementId) {
			return new ObservableCollectionEvent<>(elementId, CollectionChangeType.add, null, value, null);
		}

		/**
		 * @param value The value in the cache
		 * @param elementId The element ID for the value
		 * @return The event to fire to the listener that has just been removed (with the {@link CollectionSubscription#unsubscribe(boolean)
		 *         removeAll} option)
		 */
		protected ObservableCollectionEvent<? extends E> removeEvent(E value, ElementId elementId) {
			return new ObservableCollectionEvent<>(elementId, CollectionChangeType.remove, value, value, null);
		}

		@Override
		public TypeToken<E> getType() {
			return theWrapped.getType();
		}

		@Override
		public boolean isLockSupported() {
			if (isDone.get())
				throw new IllegalStateException("This cached collection's finisher has fired");
			return theWrapped.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			if (isDone.get())
				throw new IllegalStateException("This cached collection's finisher has fired");
			return theWrapped.lock(write, cause);
		}

		@Override
		public Equivalence<? super E> equivalence() {
			return theWrapped.equivalence();
		}

		@Override
		public int size() {
			if (isDone.get())
				throw new IllegalStateException("This cached collection's finisher has fired");
			return theCache.size();
		}

		@Override
		public boolean isEmpty() {
			if (isDone.get())
				throw new IllegalStateException("This cached collection's finisher has fired");
			return theCache.isEmpty();
		}

		@Override
		public MutableObservableSpliterator<E> mutableSpliterator() {
			if (isDone.get())
				throw new IllegalStateException("This cached collection's finisher has fired");
			class MutableCachedSpliterator implements MutableObservableSpliterator<E> {
				private final MutableObservableSpliterator<E> theWrappedSpliter;
				private final MutableObservableElement<E> theElement;
				private MutableObservableElement<E> theCurrentElement;
				private ElementId theCurrentId;
				private E theCurrentValue;

				MutableCachedSpliterator(MutableObservableSpliterator<E> wrap) {
					theWrappedSpliter = wrap;
					theElement = new MutableObservableElement<E>() {
						@Override
						public ElementId getElementId() {
							return theCurrentId;
						}

						@Override
						public TypeToken<E> getType() {
							return theCurrentElement.getType();
						}

						@Override
						public E get() {
							return theCurrentValue;
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
						public <V extends E> E set(V value, Object cause) throws IllegalArgumentException, UnsupportedOperationException {
							return theCurrentElement.set(value, cause);
						}

						@Override
						public <V extends E> String isAcceptable(V value) {
							return theCurrentElement.isAcceptable(value);
						}

						@Override
						public Value<String> isEnabled() {
							return theCurrentElement.isEnabled();
						}
					};
				}

				@Override
				public TypeToken<E> getType() {
					return theWrappedSpliter.getType();
				}

				@Override
				public long estimateSize() {
					return theWrappedSpliter.estimateSize();
				}

				@Override
				public long getExactSizeIfKnown() {
					return theWrappedSpliter.getExactSizeIfKnown();
				}

				@Override
				public int characteristics() {
					return theWrappedSpliter.characteristics();
				}

				@Override
				public Comparator<? super E> getComparator() {
					return theWrappedSpliter.getComparator();
				}

				@Override
				public MutableObservableSpliterator<E> trySplit() {
					MutableObservableSpliterator<E> split = theWrappedSpliter.trySplit();
					return split == null ? null : new MutableCachedSpliterator(split);
				}

				@Override
				public boolean tryAdvanceMutableElement(Consumer<? super MutableObservableElement<E>> action) {
					return theWrappedSpliter.tryAdvanceMutableElement(el -> {
						theCurrentElement = el;
						theCurrentId = el.getElementId();
						theCurrentValue = theCacheMap.get(theCurrentId);
						action.accept(theElement);
					});
				}

				@Override
				public void forEachMutableElement(Consumer<? super MutableObservableElement<E>> action) {
					try (Transaction t = lock(true, null)) {
						MutableObservableSpliterator.super.forEachMutableElement(action);
					}
				}
			}
			return new MutableCachedSpliterator(theWrapped.mutableSpliterator());
		}

		@Override
		public ObservableElementSpliterator<E> spliterator() {
			if (isDone.get())
				throw new IllegalStateException("This cached collection's finisher has fired");
			class CachedSpliterator implements ObservableElementSpliterator<E> {
				private final Spliterator<ElementId> theIdSpliterator;
				private final ObservableCollectionElement<E> theElement;
				ElementId theCurrentId;
				E theCurrentValue;

				CachedSpliterator(Spliterator<ElementId> idSpliterator) {
					theIdSpliterator = idSpliterator;
					theElement = new ObservableCollectionElement<E>() {
						@Override
						public TypeToken<E> getType() {
							return theWrapped.getType();
						}

						@Override
						public E get() {
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
					if (isDone.get())
						throw new IllegalStateException("This cached collection's finisher has fired");
					return theIdSpliterator.estimateSize();
				}

				@Override
				public long getExactSizeIfKnown() {
					if (isDone.get())
						throw new IllegalStateException("This cached collection's finisher has fired");
					return theIdSpliterator.getExactSizeIfKnown();
				}

				@Override
				public int characteristics() {
					return theIdSpliterator.characteristics();
				}

				@Override
				public TypeToken<E> getType() {
					return theWrapped.getType();
				}

				@Override
				public boolean tryAdvanceObservableElement(Consumer<? super ObservableCollectionElement<E>> action) {
					if (isDone.get())
						throw new IllegalStateException("This cached collection's finisher has fired");
					return theIdSpliterator.tryAdvance(id -> {
						theCurrentId = id;
						theCurrentValue = theCacheMap.get(id);
						action.accept(theElement);
					});
				}

				@Override
				public void forEachObservableElement(Consumer<? super ObservableCollectionElement<E>> action) {
					if (isDone.get())
						throw new IllegalStateException("This cached collection's finisher has fired");
					try (Transaction t = lock(false, null)) {
						ObservableElementSpliterator.super.forEachObservableElement(action);
					}
				}

				@Override
				public ObservableElementSpliterator<E> trySplit() {
					Spliterator<ElementId> split = theIdSpliterator.trySplit();
					return split == null ? null : new CachedSpliterator(split);
				}
			}
			return new CachedSpliterator(theCacheMap.keySet().spliterator());
		}

		@Override
		public boolean contains(Object o) {
			if (isDone.get())
				throw new IllegalStateException("This cached collection's finisher has fired");
			try (Transaction t = lock(false, null)) {
				return theCache.contains(o);
			}
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			if (isDone.get())
				throw new IllegalStateException("This cached collection's finisher has fired");
			if (c.isEmpty())
				return true;
			try (Transaction t = lock(false, null)) {
				return theCache.containsAll(c);
			}
		}

		@Override
		public boolean containsAny(Collection<?> c) {
			if (isDone.get())
				throw new IllegalStateException("This cached collection's finisher has fired");
			if (c.isEmpty())
				return false;
			Set<E> cSet = toSet(equivalence(), c);
			if (cSet.isEmpty())
				return false;
			try (Transaction t = lock(false, null)) {
				Spliterator<E> iter = spliterator();
				boolean[] found = new boolean[1];
				while (iter.tryAdvance(next -> {
					found[0] = cSet.contains(next);
				}) && !found[0]) {
				}
				return found[0];
			}
		}

		@Override
		public String canAdd(E value) {
			if (isDone.get())
				throw new IllegalStateException("This cached collection's finisher has fired");
			return theWrapped.canAdd(value);
		}

		@Override
		public boolean add(E e) {
			if (isDone.get())
				throw new IllegalStateException("This cached collection's finisher has fired");
			return theWrapped.add(e);
		}

		@Override
		public boolean addAll(Collection<? extends E> c) {
			if (isDone.get())
				throw new IllegalStateException("This cached collection's finisher has fired");
			return theWrapped.addAll(c);
		}

		@Override
		public String canRemove(Object value) {
			if (isDone.get())
				throw new IllegalStateException("This cached collection's finisher has fired");
			return theWrapped.canRemove(value);
		}

		@Override
		public boolean remove(Object o) {
			if (isDone.get())
				throw new IllegalStateException("This cached collection's finisher has fired");
			return theWrapped.remove(o);
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			if (isDone.get())
				throw new IllegalStateException("This cached collection's finisher has fired");
			return theWrapped.removeAll(c);
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			if (isDone.get())
				throw new IllegalStateException("This cached collection's finisher has fired");
			return theWrapped.retainAll(c);
		}

		@Override
		public void clear() {
			if (isDone.get())
				throw new IllegalStateException("This cached collection's finisher has fired");
			theWrapped.clear();
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
			if (isDone.get())
				throw new IllegalStateException("This cached collection's finisher has fired");
			return theChanges.act(observer);
		}

		@Override
		public CollectionSubscription subscribe(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
			// Overridden because the default impl uses spliterator, which requires spliterating over the wrapped collection.
			// Since the iteration is read-only here, we can do better
			if (isDone.get())
				throw new IllegalStateException("This cached collection's finisher has fired");
			Subscription changeSub;
			try (Transaction t = lock(false, null)) {
				for (Map.Entry<ElementId, E> entry : theCacheMap.entrySet())
					ObservableCollectionEvent.doWith(initialEvent(entry.getValue(), entry.getKey()), observer);
				changeSub = theChanges.act(observer::accept);
			}
			return removeAll -> {
				changeSub.unsubscribe();
				if (removeAll) {
					try (Transaction t = lock(false, null)) {
						// Remove from the end
						for (Map.Entry<ElementId, E> entry : theCacheMap.descendingMap().entrySet())
							ObservableCollectionEvent.doWith(removeEvent(entry.getValue(), entry.getKey()), observer);
					}
				}
			};
		}

		@Override
		public int hashCode() {
			if (isDone.get())
				throw new IllegalStateException("This cached collection's finisher has fired");
			try (Transaction t = lock(false, null)) {
				int hashCode = 1;
				for (Object e : theCache)
					hashCode += Objects.hashCode(e);
				return hashCode;
			}
		}

		@Override
		public boolean equals(Object o) {
			if (isDone.get())
				throw new IllegalStateException("This cached collection's finisher has fired");
			if (!(o instanceof Collection))
				return false;
			Collection<?> c = (Collection<?>) o;

			try (Transaction t1 = lock(false, null); Transaction t2 = Transactable.lock(c, false, null)) {
				Iterator<E> e1 = theCache.iterator();
				Iterator<?> e2 = c.iterator();
				while (e1.hasNext() && e2.hasNext()) {
					E o1 = e1.next();
					Object o2 = e2.next();
					if (!equivalence().elementEquals(o1, o2))
						return false;
				}
				return !(e1.hasNext() || e2.hasNext());
			}
		}

		@Override
		public String toString() {
			if (isDone.get())
				throw new IllegalStateException("This cached collection's finisher has fired");
			StringBuilder ret = new StringBuilder("(");
			boolean first = true;
			try (Transaction t = lock(false, null)) {
				for (Object value : theCache) {
					if (!first) {
						ret.append(", ");
					} else
						first = false;
					ret.append(value);
				}
			}
			ret.append(')');
			return ret.toString();
		}
	}

	/**
	 * Backs {@link ObservableCollection#takeUntil(Observable)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	public static class TakenUntilObservableCollection<E> implements ObservableCollection<E> {
		private final ObservableCollection<E> theWrapped;
		private final Observable<?> theUntil;
		private final boolean isTerminating;

		/**
		 * @param wrap The collection whose content to use
		 * @param until The observable to terminate observation into the collection
		 * @param terminate Whether the until observable's firing will remove all the collections's elements
		 */
		public TakenUntilObservableCollection(ObservableCollection<E> wrap, Observable<?> until, boolean terminate) {
			theWrapped = wrap;
			theUntil = until;
			isTerminating = terminate;
		}

		/** @return The collection that this taken until collection wraps */
		protected ObservableCollection<E> getWrapped() {
			return theWrapped;
		}

		/** @return The observable that ends this collection */
		protected Observable<?> getUntil() {
			return theUntil;
		}

		/** @return Whether this collection's elements will be removed when the {@link #getUntil() until} observable fires */
		protected boolean isTerminating() {
			return isTerminating;
		}

		@Override
		public TypeToken<E> getType() {
			return theWrapped.getType();
		}

		@Override
		public Equivalence<? super E> equivalence() {
			return theWrapped.equivalence();
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
		public int size() {
			return theWrapped.size();
		}

		@Override
		public boolean isEmpty() {
			return theWrapped.isEmpty();
		}

		@Override
		public MutableObservableSpliterator<E> mutableSpliterator() {
			return theWrapped.mutableSpliterator();
		}

		@Override
		public boolean contains(Object o) {
			return theWrapped.contains(o);
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			return theWrapped.containsAll(c);
		}

		@Override
		public boolean containsAny(Collection<?> c) {
			return theWrapped.containsAny(c);
		}

		@Override
		public String canAdd(E value) {
			return theWrapped.canAdd(value);
		}

		@Override
		public boolean add(E e) {
			return theWrapped.add(e);
		}

		@Override
		public boolean addAll(Collection<? extends E> c) {
			return theWrapped.addAll(c);
		}

		@Override
		public String canRemove(Object value) {
			return theWrapped.canRemove(value);
		}

		@Override
		public boolean remove(Object o) {
			return theWrapped.remove(o);
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			return theWrapped.removeAll(c);
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			return theWrapped.retainAll(c);
		}

		@Override
		public void clear() {
			theWrapped.clear();
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
			return ObservableCollectionImpl.defaultOnChange(this, observer);
		}

		@Override
		public CollectionSubscription subscribe(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
			CollectionSubscription collSub = theWrapped.subscribe(observer);
			AtomicBoolean complete = new AtomicBoolean(false);
			Subscription obsSub = theUntil.take(1).act(u -> {
				if (!complete.getAndSet(true))
					collSub.unsubscribe(isTerminating);
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
		public int hashCode() {
			return theWrapped.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return theWrapped.equals(obj);
		}

		@Override
		public String toString() {
			return theWrapped.toString();
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
		private final List<ConstantElement> theElements;
		private final Collection<? extends E> theCollection;

		/**
		 * @param type The type of the values
		 * @param collection The collection whose values to present
		 */
		public ConstantObservableCollection(TypeToken<E> type, Collection<? extends E> collection) {
			theType = type;
			theCollection = collection;
			int[] index = new int[1];
			theElements = collection.stream().map(v -> new ConstantElement(v, index[0]++))
				.collect(Collectors.toCollection(() -> new ArrayList<>(collection.size())));
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
				private final Spliterator<ConstantElement> theElementSpliter;

				ConstantObservableSpliterator(Spliterator<ConstantObservableCollection<E>.ConstantElement> elementSpliter) {
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
				public MutableObservableSpliterator<E> trySplit() {
					Spliterator<ConstantElement> split = theElementSpliter.trySplit();
					return split == null ? null : new ConstantObservableSpliterator(split);
				}
			}
			;
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
			return false;
		}

		@Override
		public boolean addAll(Collection<? extends E> c) {
			return false;
		}

		@Override
		public String canRemove(Object value) {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public boolean remove(Object o) {
			return false;
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			return false;
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			return false;
		}

		@Override
		public void clear() {
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
			return () -> {
			};
		}

		@Override
		public CollectionSubscription subscribe(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
			for (ConstantElement el : theElements)
				ObservableCollectionEvent
				.doWith(new ObservableCollectionEvent<>(el.getElementId(), CollectionChangeType.add, null, el.get(), null), observer);
			return removeAll -> {
				if (removeAll) {
					for (int i = theElements.size() - 1; i >= 0; i--) {
						ConstantElement el = theElements.get(i);
						ObservableCollectionEvent.doWith(
							new ObservableCollectionEvent<>(el.getElementId(), CollectionChangeType.remove, el.get(), el.get(), null),
							observer);
					}
				}
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
	 * Implements {@link ObservableCollection#flattenValues(ObservableCollection)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	public static class FlattenedValuesCollection<E> implements ObservableCollection<E> {
		private final ObservableCollection<? extends ObservableValue<? extends E>> theCollection;
		private final TypeToken<E> theType;
		private final boolean canAcceptConst;

		/** @param collection A collection of values to flatten */
		protected FlattenedValuesCollection(ObservableCollection<? extends ObservableValue<? extends E>> collection) {
			theCollection = collection;
			theType = (TypeToken<E>) theCollection.getType().resolveType(ObservableValue.class.getTypeParameters()[0]);
			canAcceptConst = theCollection.getType()
				.isAssignableFrom(new TypeToken<ObservableValue.ConstantObservableValue<E>>() {}.where(new TypeParameter<E>() {}, theType));
		}

		/** @return The collection of values that this collection flattens */
		protected ObservableCollection<? extends ObservableValue<? extends E>> getWrapped() {
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
			return theCollection.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theCollection.lock(write, cause);
		}

		/**
		 * @param value The value in an element of this collection
		 * @return The value's contents, or null if the value is null
		 */
		protected E unwrap(ObservableValue<? extends E> value) {
			return value == null ? null : value.get();
		}

		@Override
		public int size() {
			return theCollection.size();
		}

		@Override
		public boolean isEmpty() {
			return theCollection.isEmpty();
		}

		protected MutableObservableSpliteratorMap<ObservableValue<? extends E>, E> map() {
			return new MutableObservableSpliteratorMap<ObservableValue<? extends E>, E>() {
				@Override
				public TypeToken<E> getType() {
					return theType;
				}

				@Override
				public E map(ObservableValue<? extends E> value) {
					return value.get();
				}

				@Override
				public boolean test(ObservableValue<? extends E> srcValue) {
					return true;
				}

				@Override
				public ObservableValue<? extends E> reverse(E value) {
					if (canAcceptConst)
						throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
					return ObservableValue.constant(theType, value);
				}

				@Override
				public String filterEnabled(CollectionElement<ObservableValue<? extends E>> el) {
					if (canAcceptConst)
						return StdMsg.UNSUPPORTED_OPERATION;
					return null;
				}

				@Override
				public String filterRemove(CollectionElement<ObservableValue<? extends E>> sourceEl) {
					return null;
				}

				@Override
				public long filterExactSize(long srcSize) {
					return srcSize;
				}
			};
		}

		@Override
		public MutableObservableSpliterator<E> mutableSpliterator() {
			return ((MutableObservableSpliterator<ObservableValue<? extends E>>) theCollection.mutableSpliterator()).map(map());
		}

		@Override
		public boolean contains(Object o) {
			return theCollection.stream().map(v -> v == null ? null : v.get()).anyMatch(v -> equivalence().elementEquals(v, o));
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			try (Transaction t = lock(false, null)) {
				return c.stream().allMatch(this::contains);
			}
		}

		@Override
		public boolean containsAny(Collection<?> c) {
			if (c.isEmpty())
				return false;
			return c.stream().anyMatch(this::contains);
		}

		/**
		 * Converts a value to be added to this list into a value that may be added to the wrapped list, or throws an exception if this is
		 * not possible
		 *
		 * @param value The value to add
		 * @return The observable value to add to the wrapped list
		 */
		protected ObservableValue<E> attemptedAdd(E value) {
			if (!canAcceptConst)
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			if (value != null && !theType.getRawType().isInstance(value))
				throw new IllegalArgumentException(StdMsg.BAD_TYPE);
			return ObservableValue.constant(theType, value);
		}

		@Override
		public String canAdd(E value) {
			if (!canAcceptConst)
				return StdMsg.UNSUPPORTED_OPERATION;
			if (value != null && !theType.getRawType().isInstance(value))
				return StdMsg.BAD_TYPE;
			return ((ObservableCollection<ObservableValue<E>>) theCollection).canAdd(ObservableValue.constant(theType, value));
		}

		@Override
		public boolean add(E e) {
			return ((ObservableCollection<ObservableValue<E>>) theCollection).add(attemptedAdd(e));
		}

		@Override
		public boolean addAll(Collection<? extends E> c) {
			if (!canAcceptConst)
				return false;
			return ((ObservableCollection<ObservableValue<E>>) theCollection)
				.addAll(c.stream().map(v -> attemptedAdd(v)).collect(Collectors.toList()));
		}

		@Override
		public String canRemove(Object value) {
			for (ObservableValue<? extends E> v : theCollection)
				if (Objects.equals(v.get(), value))
					return theCollection.canRemove(v);
			return StdMsg.NOT_FOUND;
		}

		@Override
		public boolean remove(Object o) {
			boolean[] removed = new boolean[1];
			theCollection.mutableSpliterator().forEachElement(el -> {
				if (equivalence().elementEquals(unwrap(el.get()), o)) {
					el.remove();
					removed[0] = true;
				}
			});
			return removed[0];
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			return ObservableCollectionImpl.removeAll(theCollection, c);
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			return ObservableCollectionImpl.retainAll(theCollection, c);
		}

		@Override
		public void clear() {
			theCollection.clear();
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
			return ObservableCollectionImpl.defaultOnChange(this, observer);
		}

		/** An observer for the ObservableValue inside one element of this collection */
		protected class AddObserver implements Observer<ObservableValueEvent<? extends E>> {
			private final ElementId theElementId;
			private final Consumer<? super ObservableCollectionEvent<? extends E>> theObserver;
			private final AtomicReference<Subscription> valueSub;
			private boolean isInitialized;
			private E value;

			/**
			 * @param elementId The ID of the element to observe
			 * @param observer The subscriber
			 */
			protected AddObserver(ElementId elementId, Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
				theElementId = elementId;
				theObserver = observer;
				valueSub = new AtomicReference<>();
			}

			/** @return The ID of the element that this observer observes */
			protected ElementId getElementId() {
				return theElementId;
			}

			@Override
			public <V extends ObservableValueEvent<? extends E>> void onNext(V event) {
				E oldValue = value;
				value = event.getValue();
				if (!isInitialized) {
					ObservableCollectionEvent.doWith(createEvent(CollectionChangeType.add, oldValue, value, event), theObserver);
					isInitialized = true;
				} else {
					try (Transaction t = event.isInitial() ? Transaction.NONE : lock(false, null)) {
						ObservableCollectionEvent.doWith(createEvent(CollectionChangeType.set, oldValue, value, event), theObserver);
					}
				}
			}

			@Override
			public <V extends ObservableValueEvent<? extends E>> void onCompleted(V event) {
				try (Transaction t = lock(false, null)) {
					valueSub.set(null);
					E oldValue = value;
					value = null;
					ObservableCollectionEvent.doWith(createEvent(CollectionChangeType.set, oldValue, null, event), theObserver);
				}
			}

			void remove(Object cause) {
				Subscription oldSub = valueSub.getAndSet(null);
				if (oldSub != null) {
					oldSub.unsubscribe();
					fireRemove(cause);
				}
			}

			void set(ObservableValue<? extends E> newValue, Object cause) {
				Subscription oldSub = valueSub.getAndSet(null);
				if (oldSub != null)
					oldSub.unsubscribe();
				if (newValue != null)
					valueSub.set(newValue.safe().subscribe(this));
				else if (isInitialized) {
					E oldValue = value;
					value = null;
					ObservableCollectionEvent.doWith(createEvent(CollectionChangeType.set, oldValue, null, cause), theObserver);
				} else {
					ObservableCollectionEvent.doWith(createEvent(CollectionChangeType.add, null, null, cause), theObserver);
					isInitialized = true;
				}
			}

			private void fireRemove(Object cause) {
				E oldValue = value;
				value = null;
				if (isInitialized) {
					ObservableCollectionEvent.doWith(createEvent(CollectionChangeType.remove, oldValue, oldValue, cause), theObserver);
					isInitialized = false;
				}
			}

			private void unsubscribe() {
				Subscription sub = valueSub.getAndSet(null);
				if (sub != null)
					sub.unsubscribe();
			}

			/**
			 * @param type The type of the event
			 * @param oldValue The old value for the event
			 * @param newValue The new value for the event
			 * @param cause The cause of the event
			 * @return The event to fire to the listener
			 */
			protected ObservableCollectionEvent<E> createEvent(CollectionChangeType type, E oldValue, E newValue, Object cause) {
				return new ObservableCollectionEvent<>(theElementId, type, oldValue, newValue, cause);
			}
		}

		@Override
		public CollectionSubscription subscribe(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
			FlatteningObserver flattening = new FlatteningObserver(observer);
			CollectionSubscription collSub = theCollection.subscribe(flattening);
			return removeAll -> {
				try (Transaction t = theCollection.lock(false, null)) {
					collSub.unsubscribe(removeAll);
					if (!removeAll) {
						flattening.done();
					}
				}
			};
		}

		/** Observes the wrapped collection and forwards the flattened events to a listener on this collection */
		protected class FlatteningObserver implements Consumer<ObservableCollectionEvent<? extends ObservableValue<? extends E>>> {
			private final Consumer<? super ObservableCollectionEvent<? extends E>> theObserver;
			private final TreeMap<ElementId, AddObserver> theElements;
			private final Object theMetadata;

			/** @param observer The listener on this collection */
			public FlatteningObserver(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
				theObserver = observer;
				theElements = new TreeMap<>();
				theMetadata = createSubscriptionMetadata();
			}

			@Override
			public void accept(ObservableCollectionEvent<? extends ObservableValue<? extends E>> evt) {
				switch (evt.getType()) {
				case add:
					AddObserver addObs = createElementObserver(evt.getElementId(), theObserver, theMetadata);
					theElements.put(evt.getElementId(), addObs);
					addObs.set(evt.getNewValue(), evt);
					break;
				case remove:
					theElements.remove(evt.getElementId()).remove(evt);
					break;
				case set:
					theElements.get(evt.getElementId()).set(evt.getNewValue(), evt);
					break;
				}
			}

			void done() {
				// Remove from the end first--better performance for array listeners
				for (AddObserver addObs : theElements.descendingMap().values())
					addObs.unsubscribe();
				theElements.clear();
			}
		}

		/** @return Metadata that subclasses may use to keep track of elements over the life of a subscription */
		protected Object createSubscriptionMetadata() {
			return null;
		}

		/**
		 * @param elementId The ID of the element to observe
		 * @param observer The subscriber
		 * @param metadata The metadata for the subscription
		 * @return The value observer for the element's observable value contents
		 */
		protected AddObserver createElementObserver(ElementId elementId, Consumer<? super ObservableCollectionEvent<? extends E>> observer,
			Object metadata) {
			return new AddObserver(elementId, observer);
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
