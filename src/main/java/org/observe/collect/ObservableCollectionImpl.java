package org.observe.collect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Spliterator;
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
import org.observe.SettableValue;
import org.observe.SimpleObservable;
import org.observe.Subscription;
import org.observe.assoc.ObservableMultiMap;
import org.observe.assoc.ObservableSortedMultiMap;
import org.observe.collect.ObservableCollection.GroupingBuilder;
import org.observe.collect.ObservableCollection.SortedGroupingBuilder;
import org.observe.collect.ObservableCollection.StdMsg;
import org.observe.collect.ObservableElementSpliterator.WrappingObservableElement;
import org.observe.collect.ObservableElementSpliterator.WrappingObservableSpliterator;
import org.qommons.BiTuple;
import org.qommons.Causable;
import org.qommons.Ternian;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementSpliterator;
import org.qommons.collect.ReversibleSpliterator;
import org.qommons.collect.TreeList;
import org.qommons.tree.CountedRedBlackNode.DefaultNode;
import org.qommons.tree.CountedRedBlackNode.DefaultTreeMap;
import org.qommons.value.Value;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

/** Holds default implementation methods and classes for {@link ObservableCollection} methods */
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
		public CollectionSubscription subscribe(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
			return theWrapped.subscribe(observer);
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

		@Override
		public ObservableElementSpliterator<T> spliterator() {
			return map(theWrapped.spliterator());
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

		private List<E> reverse(Collection<?> input) {
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
				theWrapped.spliterator().forEachElement(el -> {
					result.source = el.get();
					theDef.checkSourceValue(result);
					if (result.error == null)
						el.remove();
				});
			}
		}

		/**
		 * @param iter The spliterator from the source collection
		 * @return The corresponding spliterator for this collection
		 */
		protected ObservableElementSpliterator<T> map(ObservableElementSpliterator<E> iter) {
			return new WrappingObservableSpliterator<>(iter, getType(), () -> {
				ObservableCollectionElement<? extends E>[] container = new ObservableCollectionElement[1];
				FilterMapResult<E, T> mapped = new FilterMapResult<>();
				WrappingObservableElement<E, T> wrapperEl = new WrappingObservableElement<E, T>(
					getType(), container) {
					@Override
					public T get() {
						return mapped.result;
					}

					@Override
					public <V extends T> String isAcceptable(V value) {
						if (!theDef.isReversible())
							return StdMsg.UNSUPPORTED_OPERATION;
						else if (!theDef.checkDestType(value))
							return StdMsg.BAD_TYPE;
						FilterMapResult<T, E> reversed = theDef.reverse(new FilterMapResult<>(value));
						if (reversed.error != null)
							return reversed.error;
						return ((CollectionElement<E>) getWrapped()).isAcceptable(reversed.result);
					}

					@Override
					public <V extends T> T set(V value, Object cause) throws IllegalArgumentException {
						if (!theDef.isReversible())
							throw new IllegalArgumentException(StdMsg.UNSUPPORTED_OPERATION);
						else if (!theDef.checkDestType(value))
							throw new IllegalArgumentException(StdMsg.BAD_TYPE);
						FilterMapResult<T, E> reversed = theDef.reverse(new FilterMapResult<>(value));
						if (reversed.error != null)
							throw new IllegalArgumentException(reversed.error);
						((CollectionElement<E>) getWrapped()).set(reversed.result, cause);
						T old = mapped.result;
						mapped.source = reversed.result;
						mapped.result = value;
						return old;
					}
				};
				return el -> {
					mapped.source = el.get();
					theDef.map(mapped);
					if (mapped.error != null)
						return null;
					container[0] = el;
					return wrapperEl;
				};
			});
		}

		@Override
		public CollectionSubscription subscribe(Consumer<? super ObservableCollectionEvent<? extends T>> observer) {
			Object meta = createSubscriptionMetadata();
			return theWrapped.subscribe(evt -> {
				switch (evt.getType()) {
				case add:
					FilterMapResult<E, T> res = getDef().map(new FilterMapResult<>(evt.getNewValue()));
					if (res.error == null)
						ObservableCollectionEvent.doWith(map(evt, evt.getType(), null, res.result, meta), observer);
					break;
				case remove:
					res = getDef().map(new FilterMapResult<>(evt.getOldValue()));
					if (res.error == null)
						ObservableCollectionEvent.doWith(map(evt, evt.getType(), res.result, res.result, meta), observer);
					break;
				case set:
					res = getDef().map(new FilterMapResult<>(evt.getOldValue()));
					FilterMapResult<E, T> newRes = getDef().map(new FilterMapResult<>(evt.getNewValue()));
					if (res.error == null) {
						if (newRes.error == null)
							ObservableCollectionEvent.doWith(map(evt, evt.getType(), res.result, newRes.result, meta), observer);
						else
							ObservableCollectionEvent.doWith(map(evt, CollectionChangeType.remove, res.result, res.result, meta), observer);
					} else if (newRes.error == null)
						ObservableCollectionEvent.doWith(map(evt, CollectionChangeType.add, null, newRes.result, meta), observer);
					break;
				}
			});
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
				ElementSpliterator<E> spliter = theWrapped.spliterator();
				while (!found[0] && spliter.tryAdvanceElement(el -> {
					combined.element = el.get();
					// If we're not reversible, then the default equivalence is used
					if (Objects.equals(theDef.getCombination().apply(combined), value)) {
						found[0] = true;
						msg[0] = el.canRemove();
					}
				})) {
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

		@Override
		public ObservableElementSpliterator<V> spliterator() {
			return combine(theWrapped.spliterator());
		}

		/**
		 * @param value The value from the wrapped collection
		 * @return The corresponding value in this collection
		 */
		protected V combine(E value) {
			return theDef.getCombination().apply(combineDynamic(value));
		}

		/**
		 * @param source The spliterator from the wrapped collection
		 * @return The mapped spliterator for this collection
		 */
		protected ObservableElementSpliterator<V> combine(ObservableElementSpliterator<E> source) {
			Supplier<Function<ObservableCollectionElement<? extends E>, ObservableCollectionElement<V>>> elementMap = () -> {
				ObservableCollectionElement<? extends E>[] container = new ObservableCollectionElement[1];
				WrappingObservableElement<E, V> wrapper = new WrappingObservableElement<E, V>(
					getType(), container) {
					@Override
					public V get() {
						return combine(getWrapped().get());
					}

					@Override
					public <V2 extends V> String isAcceptable(V2 value) {
						if (theDef.getReverse() == null)
							return StdMsg.UNSUPPORTED_OPERATION;
						if (value == null && !theDef.areNullsReversed())
							return StdMsg.NULL_DISALLOWED;
						E reverse = theDef.getReverse().apply(new DynamicCombinedValues<>(value));
						return ((CollectionElement<E>) getWrapped()).isAcceptable(reverse);
					}

					@Override
					public <V2 extends V> V set(V2 value, Object cause) throws IllegalArgumentException {
						if (theDef.getReverse() == null)
							throw new IllegalArgumentException(StdMsg.UNSUPPORTED_OPERATION);
						if (value == null && !theDef.areNullsReversed())
							throw new IllegalArgumentException(StdMsg.NULL_DISALLOWED);
						E reverse = theDef.getReverse().apply(new DynamicCombinedValues<>(value));
						return combine(((CollectionElement<E>) getWrapped()).set(reverse, cause));
					}
				};
				return el -> {
					container[0] = el;
					return wrapper;
				};
			};
			return new WrappingObservableSpliterator<>(source, getType(), elementMap);
		}

		@Override
		public CollectionSubscription subscribe(Consumer<? super ObservableCollectionEvent<? extends V>> observer) {
			StaticCombinedValues<E> combined = new StaticCombinedValues<>();
			boolean[] initialized = new boolean[1];
			boolean[] complete = new boolean[1];
			ReentrantLock lock = new ReentrantLock();
			CollectionSubscription[] collSub = new CollectionSubscription[1];
			Subscription[] argSubs = new Subscription[theDef.getArgs().size()];
			CollectionSubscription sub = removeAll -> {
				Transaction cLock = Transaction.NONE;
				if (initialized[0]) {
					lock.lock();
					cLock = theWrapped.lock(false, null);
				}
				try {
					if (collSub[0] != null) {
						collSub[0].unsubscribe(removeAll);
						collSub[0] = null;
					}
					int a = 0;
					for (ObservableValue<?> arg : theDef.getArgs()) {
						if (arg != null)
							argSubs[a].unsubscribe();
						argSubs[a++] = null;
					}
				} finally {
					if (initialized[0]) {
						lock.unlock();
						cLock.close();
					}
				}
			};
			Map<ObservableValue<?>, Object> oldArgValues = new HashMap<>(theDef.getArgs().size() * 4 / 3);
			Map<ObservableValue<?>, Object> argValues = new HashMap<>(theDef.getArgs().size() * 4 / 3);
			int a = 0;
			for (ObservableValue<?> arg : theDef.getArgs()) {
				argSubs[a++] = arg.subscribe(new Observer<ObservableValueEvent<?>>() {
					@Override
					public <V2 extends ObservableValueEvent<?>> void onNext(V2 event) {
						Transaction cLock = Transaction.NONE;
						if (initialized[0]) {
							lock.lock();
							cLock = theWrapped.lock(false, null);
						}
						try {
							argValues.put(event.getObservable(), StaticCombinedValues.valueFor(event.getValue()));
							if (initialized[0]) {
								theWrapped.spliterator().forEachObservableElement(el -> {
									combined.element = el.get();
									combined.argValues = oldArgValues;
									V oldValue = theDef.getCombination().apply(combined);
									combined.argValues = argValues;
									V newValue = theDef.getCombination().apply(combined);
									ObservableCollectionEvent.doWith(new ObservableCollectionEvent<>(el.getElementId(),
										CollectionChangeType.set, oldValue, newValue, event), observer);
								});
							}
						} finally {
							if (initialized[0]) {
								lock.unlock();
								cLock.close();
							}
						}
					}

					@Override
					public <V2 extends ObservableValueEvent<?>> void onCompleted(V2 event) {
						Transaction cLock = Transaction.NONE;
						if (initialized[0]) {
							lock.lock();
							cLock = theWrapped.lock(false, null);
						}
						try {
							if (complete[0])
								return;
							complete[0] = true;
							sub.unsubscribe(true);
						} finally {
							if (initialized[0]) {
								lock.unlock();
								cLock.close();
							}
						}
					}
				});
				if (complete[0])
					break;
			}
			if (complete[0]) {
				return removeAll -> {
				};
			}
			collSub[0] = theWrapped.subscribe(evt -> {
				if (initialized[0])
					lock.lock();
				try {
					combined.argValues = argValues;
					V oldValue = null, newValue = null;
					switch (evt.getType()) {
					case add:
						combined.element = evt.getNewValue();
						newValue = theDef.getCombination().apply(combined);
						break;
					case remove:
						combined.element = evt.getOldValue();
						oldValue = theDef.getCombination().apply(combined);
						break;
					case set:
						combined.element = evt.getOldValue();
						oldValue = theDef.getCombination().apply(combined);
						combined.element = evt.getNewValue();
						newValue = theDef.getCombination().apply(combined);
						break;
					}
					ObservableCollectionEvent.doWith(
						new ObservableCollectionEvent<>(evt.getElementId(), CollectionChangeType.set, oldValue, newValue, evt), observer);
				} finally {
					if (initialized[0])
						lock.unlock();
				}
			});
			return sub;
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

		private class DynamicCombinedValues<T> implements CombinedValues<T> {
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

		private static class StaticCombinedValues<T> implements CombinedValues<T> {
			static final Object NULL = new Object();

			T element;
			Map<ObservableValue<?>, Object> argValues;

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
		public ObservableElementSpliterator<E> spliterator() {
			return theElements.spliterator();
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
		public ObservableElementSpliterator<E> spliterator() {
			return theWrapped.spliterator();
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
		public ObservableElementSpliterator<E> spliterator() {
			return theWrapped.spliterator();
		}

		@Override
		public CollectionSubscription subscribe(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
			class ElementRefreshValue{
				E value;
				Subscription refreshSub;
			}
			Map<ElementId, ElementRefreshValue> elements = createElementMap();
			CollectionSubscription collSub = theWrapped.subscribe(evt -> {
				observer.accept(evt);
				switch (evt.getType()) {
				case add:
					ElementRefreshValue erv=new ElementRefreshValue();
					erv.value = evt.getNewValue();
					erv.refreshSub = theRefresh.apply(erv.value).act(r -> {
						// There's a possibility that the refresh observable could fire on one thread while the collection fires on
						// another, so need to make sure the collection isn't firing while this refresh event happens.
						try (Transaction t = theWrapped.lock(false, r)) {
							ObservableCollectionEvent.doWith(refresh(evt.getElementId(), erv.value, elements, r), observer);
						}
					});
					elements.put(evt.getElementId(), erv);
					break;
				case remove:
					elements.remove(evt.getElementId()).refreshSub.unsubscribe();
					break;
				case set:
					erv = elements.get(evt.getElementId());
					erv.value = evt.getNewValue();
					erv.refreshSub.unsubscribe();
					erv.refreshSub = theRefresh.apply(erv.value).act(r -> {
						// There's a possibility that the refresh observable could fire on one thread while the collection fires on
						// another, so need to make sure the collection isn't firing while this refresh event happens.
						try (Transaction t = theWrapped.lock(false, r)) {
							ObservableCollectionEvent.doWith(refresh(evt.getElementId(), erv.value, elements, r), observer);
						}
					});
					break;
				}
			});
			return removeAll -> {
				collSub.unsubscribe(removeAll);
				// If removeAll is true, elements should be empty
				try (Transaction t = theWrapped.lock(false, null)) {
					for (ElementRefreshValue erv : elements.values())
						erv.refreshSub.unsubscribe();
				}
				elements.clear();
			};
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

		@Override
		public ObservableElementSpliterator<E> spliterator() {
			return modFilter(theWrapped.spliterator());
		}

		/**
		 * Modification-filters a spliterator
		 *
		 * @param source The source spliterator to filter
		 * @return A spliterator reflecting the given spliterator's content and order but disallowing appropriate modification
		 */
		protected ObservableElementSpliterator<E> modFilter(ObservableElementSpliterator<E> source) {
			return new WrappingObservableSpliterator<>(source, getType(), () -> {
				ObservableCollectionElement<E>[] container = new ObservableCollectionElement[1];
				WrappingObservableElement<E, E> wrapperEl = new WrappingObservableElement<E, E>(
					getType(), container) {
					@Override
					public E get() {
						return getWrapped().get();
					}

					@Override
					public <V extends E> String isAcceptable(V value) {
						String s = theDef.attemptRemove(get());
						if (s == null)
							s = theDef.attemptAdd(value);
						if (s == null)
							s = ((CollectionElement<E>) getWrapped()).isAcceptable(value);
						return s;
					}

					@Override
					public <V extends E> E set(V value, Object cause) throws IllegalArgumentException {
						String s = theDef.attemptRemove(get());
						if (s == null)
							s = theDef.attemptAdd(value);
						if (s != null)
							throw new IllegalArgumentException(s);
						return ((CollectionElement<E>) getWrapped()).set(value, cause);
					}

					@Override
					public String canRemove() {
						String s = theDef.attemptRemove(get());
						if (s == null)
							s = getWrapped().canRemove();
						return s;
					}

					@Override
					public void remove() {
						String s = theDef.attemptRemove(get());
						if (s != null)
							throw new IllegalArgumentException(s);
						getWrapped().remove();
					}
				};
				return el -> {
					container[0] = (ObservableCollectionElement<E>) el;
					return wrapperEl;
				};
			});
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
			String s = theDef.attemptAdd(value);
			if (s == null)
				s = theWrapped.canAdd(value);
			return s;
		}

		@Override
		public boolean add(E value) {
			if (theDef.attemptAdd(value) == null)
				return theWrapped.add(value);
			else
				return false;
		}

		@Override
		public boolean addAll(Collection<? extends E> values) {
			if (theDef.isAddFiltered())
				return theWrapped.addAll(values.stream().filter(v -> theDef.attemptAdd(v) == null).collect(Collectors.toList()));
			else
				return theWrapped.addAll(values);
		}

		@Override
		public ObservableCollection<E> addValues(E... values) {
			if (theDef.isAddFiltered())
				theWrapped.addAll(Arrays.stream(values).filter(v -> theDef.attemptAdd(v) == null).collect(Collectors.toList()));
			else
				theWrapped.addValues(values);
			return this;
		}

		@Override
		public String canRemove(Object value) {
			String s = theDef.attemptRemove(value);
			if (s == null)
				s = theWrapped.canRemove(value);
			return s;
		}

		@Override
		public boolean remove(Object value) {
			if (theDef.attemptRemove(value) == null)
				return theWrapped.remove(value);
			else
				return false;
		}

		@Override
		public boolean removeAll(Collection<?> values) {
			if (theDef.isRemoveFiltered())
				return theWrapped.removeAll(values.stream().filter(v -> theDef.attemptRemove(v) == null).collect(Collectors.toList()));
			else
				return theWrapped.removeAll(values);
		}

		@Override
		public boolean retainAll(Collection<?> values) {
			if (!theDef.isRemoveFiltered())
				return theWrapped.retainAll(values);

			boolean[] removed = new boolean[1];
			theWrapped.spliterator().forEachElement(el -> {
				E v = el.get();
				if (!values.contains(v) && theDef.attemptRemove(v) == null) {
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

			theWrapped.spliterator().forEachElement(el -> {
				if (theDef.attemptRemove(el.get()) == null)
					el.remove();
			});
		}

		@Override
		public CollectionSubscription subscribe(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
			return theWrapped.subscribe(evt -> ObservableCollectionEvent.doWith(
				new ObservableCollectionEvent<>(evt.getElementId(), evt.getType(), evt.getOldValue(), evt.getNewValue(), evt), observer));
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
		private final Map<Object, E> theCacheMap;
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
			theCacheMap = new HashMap<>();
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

		/** @return This cache's collection */
		protected BetterCollection<E> getCache() {
			return theCache;
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
		public ObservableElementSpliterator<E> spliterator() {
			if (isDone.get())
				throw new IllegalStateException("This cached collection's finisher has fired");
			ObservableElementSpliterator<E> elSpliter = theWrapped.spliterator();
			return new ObservableElementSpliterator<E>() {
				@Override
				public TypeToken<E> getType() {
					return elSpliter.getType();
				}

				@Override
				public long estimateSize() {
					return theCache.size();
				}

				@Override
				public int characteristics() {
					return elSpliter.characteristics() | Spliterator.SIZED;
				}

				@Override
				public boolean tryAdvanceObservableElement(Consumer<? super ObservableCollectionElement<E>> action) {
					return elSpliter.tryAdvanceObservableElement(el -> {
						action.accept(new ObservableCollectionElement<E>() {
							@Override
							public ElementId getElementId() {
								return el.getElementId();
							}

							@Override
							public TypeToken<E> getType() {
								return el.getType();
							}

							@Override
							public E get() {
								Object elId = el.getElementId();
								if (elId == null) // Element removed
									return el.get();
								else
									return theCacheMap.get(el.getElementId());
							}

							@Override
							public Value<String> isEnabled() {
								return el.isEnabled();
							}

							@Override
							public <V extends E> String isAcceptable(V value) {
								return el.isAcceptable(value);
							}

							@Override
							public <V extends E> E set(V value, Object cause) throws IllegalArgumentException {
								return el.set(value, cause);
							}

							@Override
							public String canRemove() {
								return el.canRemove();
							}

							@Override
							public void remove() throws IllegalArgumentException {
								el.remove();
							}
						});
					});
				}

				@Override
				public ObservableElementSpliterator<E> trySplit() {
					return null;
				}
			};
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
		public CollectionSubscription subscribe(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
			if (isDone.get())
				throw new IllegalStateException("This cached collection's finisher has fired");
			Subscription changeSub;
			try (Transaction t = lock(false, null)) {
				spliterator()
				.forEachObservableElement(el -> ObservableCollectionEvent.doWith(initialEvent(el.get(), el.getElementId()), observer));
				changeSub = theChanges.act(observer::accept);
			}
			return removeAll -> {
				changeSub.unsubscribe();
				if (removeAll) {
					try (Transaction t = lock(false, null)) {
						ObservableElementSpliterator<E> spliter = spliterator();
						// Better to remove from the end if possible
						if (spliter instanceof ReversibleSpliterator)
							spliter = (ObservableElementSpliterator<E>) ((ReversibleSpliterator<E>) spliter).reverse();
						spliter.forEachObservableElement(
							el -> ObservableCollectionEvent.doWith(removeEvent(el.get(), el.getElementId()), observer));
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
					hashCode = 31 * hashCode + (e == null ? 0 : e.hashCode());
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
		public Transaction lock(boolean write, Object cause) {
			return theWrapped.lock(write, cause);
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
		private class ConstantElement implements ObservableCollectionElement<E> {
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
		private final Collection<E> theCollection;

		/**
		 * @param type The type of the values
		 * @param collection The collection whose values to present
		 */
		public ConstantObservableCollection(TypeToken<E> type, Collection<E> collection) {
			theType = type;
			theCollection = collection;
			int[] index = new int[1];
			theElements = collection.stream().map(v -> new ConstantElement(v, index[0]++))
				.collect(Collectors.toCollection(() -> new ArrayList<>(collection.size())));
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
			return theCollection.size();
		}

		@Override
		public boolean isEmpty() {
			return theCollection.isEmpty();
		}

		@Override
		public ObservableElementSpliterator<E> spliterator() {
			return new ObservableElementSpliterator.SimpleObservableSpliterator<>(theElements.spliterator(), theType, () -> el -> el);
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
		public Transaction lock(boolean write, Object cause) {
			return Transaction.NONE;
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
		public Transaction lock(boolean write, Object cause) {
			return theCollection.lock(write, cause);
		}

		@Override
		public Equivalence<? super E> equivalence() {
			return Equivalence.DEFAULT;
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
		public ObservableElementSpliterator<E> spliterator() {
			return new WrappingObservableSpliterator<>(theCollection.spliterator(), theType, () -> {
				ObservableCollectionElement<ObservableValue<? extends E>>[] container = new ObservableCollectionElement[1];
				WrappingObservableElement<ObservableValue<? extends E>, E> wrapperEl;
				wrapperEl = new WrappingObservableElement<ObservableValue<? extends E>, E>(getType(),
					container) {
					@Override
					public E get() {
						ObservableValue<? extends E> value = getWrapped().get();
						return value == null ? null : value.get();
					}

					@Override
					public <V extends E> String isAcceptable(V value) {
						ObservableValue<? extends E> obValue = getWrapped().get();
						if (!(obValue instanceof SettableValue))
							return StdMsg.UNSUPPORTED_OPERATION;
						if (value != null && !obValue.getType().getRawType().isInstance(value))
							return StdMsg.BAD_TYPE;
						return ((SettableValue<E>) obValue).isAcceptable(value);
					}

					@Override
					public <V extends E> E set(V value, Object cause) throws IllegalArgumentException {
						ObservableValue<? extends E> obValue = getWrapped().get();
						if (!(obValue instanceof SettableValue))
							throw new IllegalArgumentException(StdMsg.UNSUPPORTED_OPERATION);
						if (value != null && !obValue.getType().getRawType().isInstance(value))
							throw new IllegalArgumentException(StdMsg.BAD_TYPE);
						return ((SettableValue<E>) obValue).set(value, cause);
					}
				};
				return el -> {
					container[0] = (ObservableCollectionElement<ObservableValue<? extends E>>) el;
					return wrapperEl;
				};
			});
		}

		@Override
		public boolean contains(Object o) {
			return theCollection.stream().map(v -> v == null ? null : v.get()).anyMatch(v -> Objects.equals(v, o));
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
			return theCollection.stream().map(v -> v == null ? null : v.get()).anyMatch(c::contains);
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
			if (!canAcceptConst)
				return false;
			if (e != null && !theType.getRawType().isInstance(e))
				return false;
			return ((ObservableCollection<ObservableValue<E>>) theCollection).add(ObservableValue.constant(theType, e));
		}

		@Override
		public boolean addAll(Collection<? extends E> c) {
			if (!canAcceptConst)
				return false;
			return ((ObservableCollection<ObservableValue<E>>) theCollection)
				.addAll(c.stream().filter(v -> v == null || theType.getRawType().isInstance(v))
					.map(v -> ObservableValue.constant(theType, v)).collect(Collectors.toList()));
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
			theCollection.spliterator().forEachElement(el -> {
				if (Objects.equals(el.get() == null ? null : el.get().get(), o)) {
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
		public CollectionSubscription subscribe(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
			ReentrantLock lock = new ReentrantLock();
			class AddObserver implements Observer<ObservableValueEvent<? extends E>> {
				private final ElementId theElementId;
				private boolean isInitialized;
				final AtomicReference<Subscription> valueSub;
				private E value;

				AddObserver(ElementId elementId) {
					theElementId = elementId;
					valueSub = new AtomicReference<>();
				}

				@Override
				public <V extends ObservableValueEvent<? extends E>> void onNext(V event) {
					E oldValue = value;
					value = event.getValue();
					if (!event.isInitial())
						lock.lock();
					try {
						ObservableCollectionEvent.doWith(new ObservableCollectionEvent<>(theElementId,
							isInitialized ? CollectionChangeType.set : CollectionChangeType.add, oldValue, value, event), observer);
					} finally {
						if (event.isInitial())
							lock.unlock();
					}
					isInitialized = true;
				}

				@Override
				public <V extends ObservableValueEvent<? extends E>> void onCompleted(V event) {
					lock.lock();
					try {
						valueSub.set(null);
						fireRemove(event);
					} finally {
						lock.unlock();
					}
				}

				void remove(Object cause) {
					Subscription oldValueSub = valueSub.getAndSet(null);
					if (oldValueSub != null) {
						oldValueSub.unsubscribe();
						fireRemove(cause);
					}
				}

				private void fireRemove(Object cause) {
					E oldValue = value;
					value = null;
					ObservableCollectionEvent.doWith(
						new ObservableCollectionEvent<>(theElementId, CollectionChangeType.remove, oldValue, oldValue, cause), observer);
				}
			}
			HashMap<Object, AddObserver> elements = new HashMap<>();
			CollectionSubscription collSub = theCollection.subscribe(evt -> {
				lock.lock();
				try {
					switch (evt.getType()) {
					case add:
						AddObserver addObs = new AddObserver(evt.getElementId());
						elements.put(evt.getElementId(), addObs);
						ObservableValue<? extends E> obValue = evt.getNewValue();
						if (obValue != null)
							addObs.valueSub.set(obValue.safe().subscribe(addObs));
						break;
					case remove:
						elements.remove(evt.getElementId()).remove(evt);
						break;
					case set:
						addObs = elements.get(evt.getElementId());
						Subscription valueSub = addObs.valueSub.getAndSet(null);
						if (valueSub != null)
							valueSub.unsubscribe();
						obValue = evt.getNewValue();
						if (obValue != null)
							addObs.valueSub.set(obValue.safe().subscribe(addObs));
						break;
					}
				} finally {
					lock.unlock();
				}
			});
			return removeAll -> {
				lock.lock();
				try (Transaction t = theCollection.lock(false, null)) {
					if (!removeAll) {
						for (AddObserver addObs : elements.values()) {
							Subscription valueSub = addObs.valueSub.getAndSet(null);
							if (valueSub != null)
								valueSub.unsubscribe();
						}
						elements.clear();
					}
					collSub.unsubscribe(removeAll);
				} finally {
					lock.unlock();
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
	 * Implements {@link ObservableCollection#flattenValue(ObservableValue)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	public static class FlattenedValueCollection<E> implements ObservableCollection<E> {
		private final ObservableValue<? extends ObservableCollection<E>> theCollectionObservable;
		private final TypeToken<E> theType;

		/** @param collectionObservable The value to present as a static collection */
		protected FlattenedValueCollection(ObservableValue<? extends ObservableCollection<E>> collectionObservable) {
			theCollectionObservable = collectionObservable;
			theType = (TypeToken<E>) theCollectionObservable.getType().resolveType(ObservableCollection.class.getTypeParameters()[0]);
		}

		/** @return The value that backs this collection */
		protected ObservableValue<? extends ObservableCollection<E>> getWrapped() {
			return theCollectionObservable;
		}

		@Override
		public TypeToken<E> getType() {
			return theType;
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			return coll == null ? Transaction.NONE : coll.lock(write, cause);
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
		public ObservableElementSpliterator<E> spliterator() {
			ObservableCollection<E> coll = theCollectionObservable.get();
			return coll == null ? ObservableElementSpliterator.empty(theType) : coll.spliterator();
		}

		@Override
		public boolean contains(Object o) {
			ObservableCollection<E> coll = theCollectionObservable.get();
			return coll == null ? false : coll.contains(o);
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			if (c.isEmpty())
				return true;
			ObservableCollection<E> coll = theCollectionObservable.get();
			return coll == null ? false : coll.containsAll(c);
		}

		@Override
		public boolean containsAny(Collection<?> c) {
			if (c.isEmpty())
				return false;
			ObservableCollection<E> coll = theCollectionObservable.get();
			return coll == null ? false : coll.containsAny(c);
		}

		@Override
		public String canAdd(E value) {
			ObservableCollection<E> current = theCollectionObservable.get();
			if (current == null)
				return StdMsg.UNSUPPORTED_OPERATION;
			return current.canAdd(value);
		}

		@Override
		public boolean add(E e) {
			ObservableCollection<E> coll = theCollectionObservable.get();
			return coll == null ? false : coll.add(e);
		}

		@Override
		public boolean addAll(Collection<? extends E> c) {
			ObservableCollection<E> coll = theCollectionObservable.get();
			return coll == null ? false : coll.addAll(c);
		}

		@Override
		public String canRemove(Object value) {
			ObservableCollection<E> current = theCollectionObservable.get();
			if (current == null)
				return StdMsg.UNSUPPORTED_OPERATION;
			return current.canRemove(value);
		}

		@Override
		public boolean remove(Object o) {
			ObservableCollection<E> coll = theCollectionObservable.get();
			return coll == null ? false : coll.remove(o);
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			ObservableCollection<E> coll = theCollectionObservable.get();
			return coll == null ? false : coll.removeAll(c);
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			ObservableCollection<E> coll = theCollectionObservable.get();
			return coll == null ? false : coll.retainAll(c);
		}

		@Override
		public void clear() {
			ObservableCollection<E> coll = theCollectionObservable.get();
			if (coll != null)
				coll.clear();
		}

		@Override
		public CollectionSubscription subscribe(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
			ReentrantLock lock = new ReentrantLock();
			CollectionSubscription[] innerSub = new CollectionSubscription[1];
			Subscription valueSub = theCollectionObservable.safe()
				.subscribe(new Observer<ObservableValueEvent<? extends ObservableCollection<E>>>() {
					@Override
					public <V extends ObservableValueEvent<? extends ObservableCollection<E>>> void onNext(V value) {
						lock.lock();
						try {
							if (innerSub[0] != null)
								innerSub[0].unsubscribe(true);
							ObservableCollection<? extends E> coll = value.getValue();
							if (coll != null)
								innerSub[0] = coll
								.subscribe(evt -> ObservableCollectionEvent.doWith(new ObservableCollectionEvent<E>(evt.getElementId(),
									evt.getType(), evt.getOldValue(), evt.getNewValue(), evt), observer));
							else
								innerSub[0] = null;
						} finally {
							lock.unlock();
						}
					}

					@Override
					public <V extends ObservableValueEvent<? extends ObservableCollection<E>>> void onCompleted(V value) {
						lock.lock();
						try {
							if (innerSub[0] != null) {
								innerSub[0].unsubscribe(true);
								innerSub[0] = null;
							}
						} finally {
							lock.unlock();
						}
					}
				});
			return removeAll -> {
				valueSub.unsubscribe();
				lock.lock();
				try {
					if (innerSub[0] != null) {
						innerSub[0].unsubscribe(true);
						innerSub[0] = null;
					}
				} finally {
					lock.unlock();
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
			for (ObservableCollection<?> c : theOuter) {
				innerLocks[i++] = c.lock(write, cause);
			}
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
					ElementSpliterator<? extends E> spliter = coll.spliterator();
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
					ElementSpliterator<? extends E> spliter = coll.spliterator();
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
					coll.spliterator().forEachElement(el -> {
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
					coll.spliterator().forEachElement(el -> {
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
		public ObservableElementSpliterator<E> spliterator() {
			return flatten(theOuter.spliterator(), ObservableCollection::spliterator);
		}

		/**
		 * @param outer A spliterator from the outer collection
		 * @param innerSplit The function to produce spliterators for the inner collections
		 * @return The flattened spliterator
		 */
		protected ObservableElementSpliterator<E> flatten(ObservableElementSpliterator<? extends ObservableCollection<? extends E>> outer,
			Function<ObservableCollection<? extends E>, ObservableElementSpliterator<? extends E>> innerSplit) {
			return new ObservableElementSpliterator<E>() {
				private ObservableCollectionElement<? extends ObservableCollection<? extends E>> theOuterElement;
				private WrappingObservableSpliterator<E, E> theInnerator;
				private Supplier<Function<ObservableCollectionElement<? extends E>, ObservableCollectionElement<E>>> theElementMap;
				private boolean isSplit;

				{
					theElementMap = () -> {
						ObservableCollectionElement<? extends E>[] container = new ObservableCollectionElement[1];
						WrappingObservableElement<E, E> wrapper = new WrappingObservableElement<E, E>(getType(), container) {
							private final ElementId theFlattenedElementId = compoundId(theOuterElement.getElementId(),
								getWrapped().getElementId());

							@Override
							public ElementId getElementId() {
								return theFlattenedElementId;
							}

							@Override
							public E get() {
								return getWrapped().get();
							}

							@Override
							public <V extends E> E set(V value, Object cause) throws IllegalArgumentException {
								if (!getWrapped().getType().getRawType().isInstance(value))
									throw new IllegalArgumentException(StdMsg.BAD_TYPE);
								return ((CollectionElement<E>) getWrapped()).set(value, cause);
							}

							@Override
							public <V extends E> String isAcceptable(V value) {
								if (!getWrapped().getType().getRawType().isInstance(value))
									return StdMsg.BAD_TYPE;
								return ((CollectionElement<E>) getWrapped()).isAcceptable(value);
							}
						};
						return el -> {
							container[0] = el;
							return wrapper;
						};
					};
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

				@Override
				public boolean tryAdvanceObservableElement(Consumer<? super ObservableCollectionElement<E>> action) {
					if (theInnerator == null && !outer.tryAdvanceObservableElement(el -> {
						theOuterElement = el;
						theInnerator = new WrappingObservableSpliterator<>(innerSplit.apply(el.get()), theType, theElementMap);
					}))
						return false;
					while (!theInnerator.tryAdvanceObservableElement(action)) {
						if (!outer
							.tryAdvance(
								coll -> theInnerator = new WrappingObservableSpliterator<>(innerSplit.apply(coll), theType, theElementMap)))
							return false;
					}
					return true;
				}

				@Override
				public void forEachObservableElement(Consumer<? super ObservableCollectionElement<E>> action) {
					try (Transaction t = isSplit ? Transaction.NONE : theOuter.lock(false, null)) { // Won't modify the outer
						outer.forEachRemaining(coll -> {
							new WrappingObservableSpliterator<>(innerSplit.apply(coll), theType, theElementMap)
							.forEachObservableElement(action);
						});
					}
				}

				@Override
				public ObservableElementSpliterator<E> trySplit() {
					ObservableElementSpliterator<E>[] ret = new ObservableElementSpliterator[1];
					isSplit |= outer.tryAdvance(coll -> {
						ret[0] = new WrappingObservableSpliterator<>(innerSplit.apply(coll), theType, theElementMap);
					});
					return ret[0];
				}
			};
		}

		static ElementId compoundId(ElementId outerId, ElementId innerId) {
			return ElementId.of(new BiTuple<>(outerId, innerId), (tup1, tup2) -> {
				int comp = tup1.getValue1().compareTo(tup2.getValue1());
				if (comp == 0)
					comp = tup1.getValue2().compareTo(tup2.getValue2());
				return comp;
			});
		}

		@Override
		public CollectionSubscription subscribe(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
			ReentrantLock lock = new ReentrantLock();
			class AddObserver implements Consumer<ObservableCollectionEvent<? extends E>> {
				private final ElementId theOuterElementId;
				final AtomicReference<CollectionSubscription> valueSub;

				AddObserver(ElementId elementId) {
					theOuterElementId = elementId;
					valueSub = new AtomicReference<>();
				}

				@Override
				public void accept(ObservableCollectionEvent<? extends E> event) {
					ObservableCollectionEvent.doWith(new ObservableCollectionEvent<>(compoundId(theOuterElementId, event.getElementId()),
						event.getType(), event.getOldValue(), event.getNewValue(), event), observer);
				}

				void remove(boolean removeAll) {
					CollectionSubscription oldValueSub = valueSub.getAndSet(null);
					if (oldValueSub != null)
						oldValueSub.unsubscribe(removeAll);
				}
			}
			HashMap<Object, AddObserver> elements = new HashMap<>();
			CollectionSubscription collSub = theOuter.subscribe(evt -> {
				switch (evt.getType()) {
				case add:
					AddObserver addObs = new AddObserver(evt.getElementId());
					elements.put(evt.getElementId(), addObs);
					ObservableCollection<? extends E> inner = evt.getNewValue();
					if (inner != null)
						addObs.valueSub.set(inner.subscribe(addObs));
					break;
				case remove:
					elements.remove(evt.getElementId()).remove(true);
					break;
				case set:
					addObs = elements.get(evt.getElementId());
					Subscription valueSub = addObs.valueSub.getAndSet(null);
					if (valueSub != null)
						valueSub.unsubscribe();
					inner = evt.getNewValue();
					if (inner != null)
						addObs.valueSub.set(inner.subscribe(addObs));
					break;
				}
			});
			return removeAll -> {
				lock.lock();
				try (Transaction t = theOuter.lock(false, null)) {
					if (!removeAll) {
						for (AddObserver addObs : elements.values()) {
							CollectionSubscription valueSub = addObs.valueSub.getAndSet(null);
							if (valueSub != null)
								valueSub.unsubscribe(false);
						}
						elements.clear();
					}
					collSub.unsubscribe(removeAll);
				} finally {
					lock.unlock();
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
}
