package org.observe.collect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.Spliterator;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.Subscription;
import org.observe.assoc.ObservableMultiMap;
import org.observe.assoc.ObservableSortedMultiMap;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollection.GroupingBuilder;
import org.observe.collect.ObservableCollection.SortedGroupingBuilder;
import org.observe.collect.ObservableCollection.StdMsg;
import org.observe.collect.ObservableCollection.UniqueDataFlow;
import org.observe.collect.ObservableCollection.UniqueSortedDataFlow;
import org.observe.collect.ObservableCollectionDataFlowImpl.CollectionElementManager;
import org.observe.collect.ObservableCollectionDataFlowImpl.CollectionManager;
import org.observe.collect.ObservableCollectionDataFlowImpl.CollectionUpdate;
import org.observe.collect.ObservableCollectionDataFlowImpl.ElementUpdateResult;
import org.observe.collect.ObservableCollectionDataFlowImpl.FilterMapResult;
import org.observe.collect.ObservableCollectionDataFlowImpl.UniqueElementFinder;
import org.qommons.BiTuple;
import org.qommons.Causable;
import org.qommons.LinkedQueue;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.CircularArrayList;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementSpliterator;
import org.qommons.collect.ReversibleCollection;
import org.qommons.collect.ReversibleList;
import org.qommons.collect.ReversibleSpliterator;
import org.qommons.collect.SimpleCause;
import org.qommons.tree.CountedRedBlackNode;
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
			if (theCollection.find(theTest, el -> value[0] = el.get(), isFirst))
				return (E) value[0];
			else
				return theDefaultValue.get();
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
		public boolean forObservableElement(E value, Consumer<? super ObservableCollectionElement<? extends E>> onElement, boolean first) {
			return getWrapped().forObservableElement(value, el -> onElement.accept(el.reverse()), !first);
		}

		@Override
		public boolean forMutableElement(E value, Consumer<? super MutableObservableElement<? extends E>> onElement, boolean first) {
			return getWrapped().forMutableElement(value, el -> onElement.accept(el.reverse()), !first);
		}

		@Override
		public void forElementAt(ElementId elementId, Consumer<? super ObservableCollectionElement<? extends E>> onElement) {
			getWrapped().forElementAt(elementId, el -> onElement.accept(el.reverse()));
		}

		@Override
		public void forMutableElementAt(ElementId elementId, Consumer<? super MutableObservableElement<? extends E>> onElement) {
			getWrapped().forMutableElementAt(elementId, el -> onElement.accept(el.reverse()));
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
		public boolean isEventIndexed() {
			return getWrapped().isEventIndexed();
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

	public static class DerivedCollection<E, T> implements ObservableCollection<T> {
		protected class DerivedCollectionElement implements ElementId {
			final CollectionElementManager<E, ?, T> manager;
			DefaultNode<DerivedCollectionElement> presentNode;

			DerivedCollectionElement(CollectionElementManager<E, ?, T> manager) {
				this.manager = manager;
			}

			@Override
			public int compareTo(ElementId o) {
				if (presentNode == null)
					throw new IllegalStateException("This node is not currentl present in the collection");
				DerivedCollectionElement other = (DerivedCollection<E, T>.DerivedCollectionElement) o;
				if (other.presentNode == null)
					throw new IllegalStateException("The node is not currentl present in the collection");
				return presentNode.getIndex() - other.presentNode.getIndex();
			}

			@Override
			public int hashCode() {
				return manager.getElementId().hashCode();
			}

			@Override
			public boolean equals(Object obj) {
				return obj instanceof DerivedCollection.DerivedCollectionElement
					&& manager.getElementId().equals(((DerivedCollectionElement) obj).manager.getElementId());
			}

			@Override
			public String toString() {
				return manager.getElementId().toString();
			}
		}

		private final ObservableCollection<E> theSource;
		private final CollectionManager<E, ?, T> theFlow;
		private final DefaultTreeMap<ElementId, DerivedCollectionElement> theElements;
		private final DefaultTreeSet<DerivedCollectionElement> thePresentElements;
		private final LinkedQueue<Consumer<? super ObservableCollectionEvent<? extends T>>> theListeners;
		private final Equivalence<? super T> theEquivalence;

		public DerivedCollection(ObservableCollection<E> source, CollectionManager<E, ?, T> flow, Observable<?> until) {
			theSource = source;
			theFlow = flow;
			theElements = new DefaultTreeMap<>(ElementId::compareTo);
			thePresentElements = new DefaultTreeSet<>((e1, e2) -> e1.manager.compareTo(e2.manager));
			theListeners = new LinkedQueue<>();
			theEquivalence = flow.equivalence();

			// Begin listening
			try (Transaction initialTransaction = lock(false, null)) {
				theFlow.begin(update -> {
					try (Transaction collT = theSource.lock(false, update.getCause())) {
						if (update.getElement() != null)
							applyUpdate(theElements.get(update.getElement()), update);
						else {
							for (DerivedCollectionElement element : theElements.values())
								applyUpdate(element, update);
						}
					}
					theFlow.postChange();
				}, until);
				WeakConsumer<ObservableCollectionEvent<? extends E>> weak = new WeakConsumer<>(evt -> {
					final DerivedCollectionElement element;
					try (Transaction flowTransaction = theFlow.lock(false, null)) {
						switch (evt.getType()) {
						case add:
							element = new DerivedCollectionElement(theFlow.createElement(evt.getElementId(), evt.getNewValue(), evt));
							if (element.manager == null)
								return; // Statically filtered out
							theElements.put(evt.getElementId(), element);
							if (element.manager.isPresent())
								addToPresent(element, evt);
							break;
						case remove:
							element = theElements.remove(evt.getElementId());
							if (element == null)
								return; // Must be statically filtered out or removed via spliterator
							if (element.presentNode != null)
								removeFromPresent(element, element.manager.get(), evt);
							element.manager.removed(evt);
							break;
						case set:
							element = theElements.get(evt.getElementId());
							if (element == null)
								return; // Must be statically filtered out
							boolean prePresent = element.presentNode != null;
							T oldValue = prePresent ? element.manager.get() : null;
							boolean fireUpdate = element.manager.set(evt.getNewValue(), evt);
							if (element.manager.isPresent()) {
								if (prePresent)
									updateInPresent(element, oldValue, evt, fireUpdate);
								else
									addToPresent(element, evt);
							} else if (prePresent)
								removeFromPresent(element, oldValue, evt);
							break;
						}
						theFlow.postChange();
					}
				});
				CollectionSubscription sub = theSource.subscribe(weak);
				weak.withSubscription(sub);
				until.take(1).act(v -> sub.unsubscribe(true));
			}
		}

		protected ObservableCollection<E> getSource() {
			return theSource;
		}

		protected CollectionManager<E, ?, T> getFlow() {
			return theFlow;
		}

		protected DefaultTreeSet<DerivedCollectionElement> getPresentElements() {
			return thePresentElements;
		}

		private void addToPresent(DerivedCollectionElement element, Object cause) {
			element.presentNode = thePresentElements.addGetNode(element);
			fireListeners(new IndexedCollectionEvent<>(element, element.presentNode.getIndex(), CollectionChangeType.add, null,
				element.manager.get(), cause));
		}

		private void removeFromPresent(DerivedCollectionElement element, T oldValue, Object cause) {
			fireListeners(new IndexedCollectionEvent<>(element, element.presentNode.getIndex(), CollectionChangeType.remove, oldValue,
				oldValue, cause));
			thePresentElements.removeNode(element.presentNode);
			element.presentNode = null;
		}

		private void updateInPresent(DerivedCollectionElement element, T oldValue, Object cause, boolean fireUpdate) {
			// Need to verify that the ordering is still correct. Otherwise, remove and re-add.
			CountedRedBlackNode<DerivedCollectionElement> left = element.presentNode.getClosest(true);
			CountedRedBlackNode<DerivedCollectionElement> right = element.presentNode.getClosest(false);
			if ((left != null && left.compareTo(element.presentNode) > 0) || (right != null && right.compareTo(element.presentNode) < 0)) {
				// Remove the element and re-add at the new position.
				// Need to fire the remove event while the node is in the old position.
				removeFromPresent(element, oldValue, cause);
				addToPresent(element, cause);
			} else if (fireUpdate)
				fireListeners(new IndexedCollectionEvent<>(element, element.presentNode.getIndex(), CollectionChangeType.set, oldValue,
					element.manager.get(), cause));
		}

		private void fireListeners(IndexedCollectionEvent<T> event) {
			for (Consumer<? super ObservableCollectionEvent<? extends T>> listener : theListeners)
				listener.accept(event);
		}

		private void applyUpdate(DerivedCollectionElement element, CollectionUpdate update) {
			boolean prePresent = element.manager.isPresent();
			T oldValue = prePresent ? element.manager.get() : null;
			ElementUpdateResult fireUpdate = element.manager.update(update,
				onEl -> theSource.forMutableElementAt(element.manager.getElementId(), onEl));
			if (fireUpdate == ElementUpdateResult.DoesNotApply)
				return;
			if (element.manager.isPresent()) {
				if (prePresent)
					updateInPresent(element, oldValue, update.getCause(), fireUpdate == ElementUpdateResult.FireUpdate);
				else
					addToPresent(element, update.getCause());
			} else if (prePresent)
				removeFromPresent(element, oldValue, update.getCause());
		}

		@Override
		public boolean isEventIndexed() {
			return true;
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends T>> observer) {
			theListeners.add(observer);
			return () -> theListeners.remove(observer);
		}

		@Override
		public CollectionSubscription subscribe(Consumer<? super ObservableCollectionEvent<? extends T>> observer) {
			return subscribeIndexed(observer);
		}

		@Override
		public CollectionSubscription subscribeIndexed(Consumer<? super IndexedCollectionEvent<? extends T>> observer) {
			try (Transaction t = lock(false, null)) {
				SubscriptionCause.doWith(new SubscriptionCause(), c -> {
					int index = 0;
					for (DerivedCollectionElement element : thePresentElements) {
						observer.accept(
							new IndexedCollectionEvent<>(element, index++, CollectionChangeType.add, null, element.manager.get(), c));
					}
				});
				return removeAll -> {
					SubscriptionCause.doWith(new SubscriptionCause(), c -> {
						int index = 0;
						for (DerivedCollectionElement element : thePresentElements.reverse()) {
							observer.accept(new IndexedCollectionEvent<>(element, index++, CollectionChangeType.remove, null,
								element.manager.get(), c));
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
			return thePresentElements.size();
		}

		@Override
		public boolean isEmpty() {
			return thePresentElements.isEmpty();
		}

		@Override
		public T get(int index) {
			try (Transaction t = lock(false, null)) {
				return thePresentElements.get(index).manager.get();
			}
		}

		protected boolean checkValue(Object o) {
			return equivalence().isElement(o) && (o == null || getType().getRawType().isInstance(o));
		}

		@Override
		public boolean forObservableElement(T value, Consumer<? super ObservableCollectionElement<? extends T>> onElement, boolean first) {
			try (Transaction t = lock(false, null)) {
				UniqueElementFinder<T> finder = getFlow().getElementFinder();
				if (finder != null) {
					ElementId id = finder.getUniqueElement(value);
					if (id == null)
						return false;
					DerivedCollectionElement element = theElements.get(id);
					if (element == null)
						throw new IllegalArgumentException(StdMsg.NOT_FOUND);
					if (!element.manager.isPresent())
						return false;
					onElement.accept(observableElementFor(element));
					return true;
				}
				for (DerivedCollectionElement el : (first ? thePresentElements : thePresentElements.reverse()))
					if (equivalence().elementEquals(el.manager.get(), value)) {
						onElement.accept(observableElementFor(el));
						return true;
					}
				return false;
			}
		}

		@Override
		public void forElementAt(ElementId elementId, Consumer<? super ObservableCollectionElement<? extends T>> onElement) {
			onElement.accept(observableElementFor((DerivedCollectionElement) elementId));
		}

		protected ObservableCollectionElement<T> observableElementFor(DerivedCollectionElement el) {
			return new ObservableCollectionElement<T>() {
				@Override
				public TypeToken<T> getType() {
					return DerivedCollection.this.getType();
				}

				@Override
				public T get() {
					return el.manager.get();
				}

				@Override
				public ElementId getElementId() {
					return el;
				}
			};
		}

		@Override
		public boolean forMutableElement(T value, Consumer<? super MutableObservableElement<? extends T>> onElement, boolean first) {
			try (Transaction t = lock(true, null)) {
				UniqueElementFinder<T> finder = getFlow().getElementFinder();
				if (finder != null) {
					ElementId id = finder.getUniqueElement(value);
					if (id == null)
						return false;
					DerivedCollectionElement element = theElements.get(id);
					if (element == null)
						throw new IllegalArgumentException(StdMsg.NOT_FOUND);
					if (!element.manager.isPresent())
						return false;
					theSource.forMutableElementAt(id, srcEl -> onElement.accept(element.manager.map(srcEl, element)));
					return true;
				}
				for (DerivedCollectionElement el : thePresentElements)
					if (equivalence().elementEquals(el.manager.get(), value)) {
						theSource.forMutableElementAt(el.manager.getElementId(),
							srcEl -> onElement.accept(el.manager.map(srcEl, el)));
						return true;
					}
				return false;
			}
		}

		@Override
		public void forMutableElementAt(ElementId elementId, Consumer<? super MutableObservableElement<? extends T>> onElement) {
			DerivedCollectionElement el = (DerivedCollectionElement) elementId;
			theSource.forMutableElementAt(el.manager.getElementId(), srcEl -> onElement.accept(el.manager.map(srcEl, el)));
		}

		@Override
		public MutableObservableSpliterator<T> mutableSpliterator(boolean fromStart) {
			return new MutableDerivedSpliterator(thePresentElements.spliterator(fromStart));
		}

		@Override
		public String canAdd(T value) {
			if (!theFlow.isReversible())
				return StdMsg.UNSUPPORTED_OPERATION;
			else if (!checkValue(value))
				return StdMsg.BAD_TYPE;
			try (Transaction t = lock(false, null)) {
				FilterMapResult<T, E> reversed = theFlow.canAdd(new FilterMapResult<>(value));
				if (reversed.error != null)
					return reversed.error;
				return theSource.canAdd(reversed.result);
			}
		}

		@Override
		public boolean add(T e) {
			if (!theFlow.isReversible())
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			else if (!checkValue(e))
				throw new IllegalArgumentException(StdMsg.BAD_TYPE);
			try (Transaction t = lock(false, null)) {
				FilterMapResult<T, E> reversed = theFlow.canAdd(new FilterMapResult<>(e));
				if (reversed.error != null)
					throw new IllegalArgumentException(reversed.error);
				return theSource.add(reversed.result);
			}
		}

		@Override
		public void clear() {
			try (Transaction t = lock(true, null)) {
				if (!theFlow.isStaticallyFiltered() && !theFlow.isDynamicallyFiltered())
					theSource.clear();
				else
					SimpleCause.doWith(new SimpleCause(), c -> mutableSpliterator().forEachElement(el -> el.remove(c)));
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

		protected class MutableDerivedSpliterator implements MutableObservableSpliterator<T> {
			private final ReversibleSpliterator<DerivedCollectionElement> theElementSpliter;

			MutableDerivedSpliterator(ReversibleSpliterator<DerivedCollectionElement> elementSpliter) {
				theElementSpliter = elementSpliter;
			}

			@Override
			public long estimateSize() {
				return theElementSpliter.estimateSize();
			}

			@Override
			public long getExactSizeIfKnown() {
				return theElementSpliter.getExactSizeIfKnown();
			}

			@Override
			public int characteristics() {
				return theElementSpliter.characteristics();
			}

			@Override
			public TypeToken<T> getType() {
				return DerivedCollection.this.getType();
			}

			@Override
			public boolean tryAdvanceObservableElement(Consumer<? super ObservableCollectionElement<T>> action) {
				try (Transaction t = lock(false, null)) {
					return theElementSpliter.tryAdvance(element -> action.accept(observableElementFor(element)));
				}
			}

			@Override
			public boolean tryReverseObservableElement(Consumer<? super ObservableCollectionElement<T>> action) {
				try (Transaction t = lock(false, null)) {
					return theElementSpliter.tryReverse(element -> action.accept(observableElementFor(element)));
				}
			}

			@Override
			public void forEachObservableElement(Consumer<? super ObservableCollectionElement<T>> action) {
				try (Transaction t = lock(false, null)) {
					theElementSpliter.forEachRemaining(element -> action.accept(observableElementFor(element)));
				}
			}

			@Override
			public void forEachReverseObservableElement(Consumer<? super ObservableCollectionElement<T>> action) {
				try (Transaction t = lock(false, null)) {
					theElementSpliter.forEachReverse(element -> action.accept(observableElementFor(element)));
				}
			}

			@Override
			public boolean tryAdvanceMutableElement(Consumer<? super MutableObservableElement<T>> action) {
				try (Transaction t = lock(true, null)) {
					return theElementSpliter.tryAdvance(element -> theSource.forMutableElementAt(element.manager.getElementId(),
						sourceEl -> action.accept(element.manager.map(sourceEl, element))));
				}
			}

			@Override
			public boolean tryReverseMutableElement(Consumer<? super MutableObservableElement<T>> action) {
				try (Transaction t = lock(true, null)) {
					return theElementSpliter.tryReverse(element -> theSource.forMutableElementAt(element.manager.getElementId(),
						sourceEl -> action.accept(element.manager.map(sourceEl, element))));
				}
			}

			@Override
			public void forEachMutableElement(Consumer<? super MutableObservableElement<T>> action) {
				try (Transaction t = lock(true, null)) {
					theElementSpliter.forEachRemaining(element -> theSource.forMutableElementAt(element.manager.getElementId(),
						sourceEl -> action.accept(element.manager.map(sourceEl, element))));
				}
			}

			@Override
			public void forEachReverseMutableElement(Consumer<? super MutableObservableElement<T>> action) {
				try (Transaction t = lock(true, null)) {
					theElementSpliter.forEachReverse(element -> theSource.forMutableElementAt(element.manager.getElementId(),
						sourceEl -> action.accept(element.manager.map(sourceEl, element))));
				}
			}

			@Override
			public MutableObservableSpliterator<T> trySplit() {
				ReversibleSpliterator<DerivedCollectionElement> split = theElementSpliter.trySplit();
				return split == null ? null : new MutableDerivedSpliterator(split);
			}

			class MutableSpliteratorElement implements MutableObservableElement<T> {
				private final MutableObservableElement<T> theObservableElement;
				private final CollectionElement<DerivedCollectionElement> thePresentElement;

				MutableSpliteratorElement(MutableObservableElement<T> observableElement,
					CollectionElement<DerivedCollectionElement> presentElement) {
					theObservableElement = observableElement;
					thePresentElement = presentElement;
				}

				@Override
				public TypeToken<T> getType() {
					return theObservableElement.getType();
				}

				@Override
				public T get() {
					return theObservableElement.get();
				}

				@Override
				public ElementId getElementId() {
					return theObservableElement.getElementId();
				}

				@Override
				public Value<String> isEnabled() {
					return theObservableElement.isEnabled();
				}

				@Override
				public <V extends T> String isAcceptable(V value) {
					return theObservableElement.isAcceptable(value);
				}

				@Override
				public <V extends T> T set(V value, Object cause) throws IllegalArgumentException, UnsupportedOperationException {
					// We can't detect here whether setting the given value will change the ordering of the value.
					// SortingManager prevents this and any other CollectionManagers that affect ordering should as well.
					return theObservableElement.set(value, cause);
					// If ordering was affected by this, though, the element spliterator will now be invalid
				}

				@Override
				public String canRemove() {
					return theObservableElement.canRemove();
				}

				@Override
				public void remove(Object cause) throws UnsupportedOperationException {
					// TODO Remove locally
					theObservableElement.remove(cause);
				}

				@Override
				public String canAdd(T value, boolean before) {
					// Taking advantage of our knowledge of the Qommons tree collections to know that this
					// will not invalidate this spliterator
					return theObservableElement.canAdd(value, before);
				}

				@Override
				public void add(T value, boolean before, Object cause) throws UnsupportedOperationException, IllegalArgumentException {
					// Taking advantage of our knowledge of the Qommons tree collections to know that this
					// will not invalidate this spliterator
					theObservableElement.add(value, before, cause);
				}
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

			theKeySet = unique(wrap.flow().map(theBuilder.getKeyType()).map(theBuilder.getKeyMaker(), theBuilder.areNullsMapped())
				.withEquivalence(theBuilder.getEquivalence())).collect();
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
		 * @param keyFlow The flow to assign uniqueness to
		 * @return The key set for the map
		 */
		protected UniqueDataFlow<E, ?, K> unique(CollectionDataFlow<E, ?, K> keyFlow) {
			return keyFlow.unique(theBuilder.isAlwaysUsingFirst());
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
			return flow.collect();
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
			public void remove(Object cause) throws IllegalArgumentException {
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			}

			@Override
			public String canAdd(E value, boolean before) {
				return StdMsg.UNSUPPORTED_OPERATION;
			}

			@Override
			public void add(E value, boolean before, Object cause) throws UnsupportedOperationException, IllegalArgumentException {
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			}
		}
		private final TypeToken<E> theType;
		private final ReversibleList<ConstantElement> theElements;
		private final HashMap<E, List<ConstantElement>> theMap;

		/**
		 * @param type The type of the values
		 * @param collection The collection whose values to present
		 */
		public ConstantObservableCollection(TypeToken<E> type, Collection<? extends E> collection) {
			theType = type;
			int[] index = new int[1];
			theElements = CircularArrayList.build().unsafe().withInitCapacity(collection.size()).build();
			theMap = new HashMap<>(collection.size() * 3 / 2);
			collection.stream().forEach(v -> {
				ConstantElement element = new ConstantElement(v, index[0]++);
				theElements.add(element);
				theMap.computeIfAbsent(v, k -> new LinkedList<>()).add(element);
			});
		}

		/** @return The list of collection elements in this collection */
		protected List<ConstantElement> getElements() {
			return theElements;
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
			return theElements.size();
		}

		@Override
		public boolean isEmpty() {
			return theElements.isEmpty();
		}

		@Override
		public E get(int index) {
			return theElements.get(index).theValue;
		}

		@Override
		public boolean forObservableElement(E value, Consumer<? super ObservableCollectionElement<? extends E>> onElement, boolean first) {
			return forMutableElement(value, onElement, first);
		}

		@Override
		public boolean forMutableElement(E value, Consumer<? super MutableObservableElement<? extends E>> onElement, boolean first) {
			List<ConstantElement> elements = theMap.get(value);
			if (elements == null)
				return false;
			for (ConstantElement element : (first ? theElements : theElements.reverse()))
				if (Objects.equals(element.theValue, value)) {
					onElement.accept(element);
					return true;
				}
			return false;
		}

		@Override
		public void forElementAt(ElementId elementId, Consumer<? super ObservableCollectionElement<? extends E>> onElement) {
			onElement.accept(theElements.get(((ElementId.ComparableElementId<Integer>) elementId).getValue()));
		}

		@Override
		public void forMutableElementAt(ElementId elementId, Consumer<? super MutableObservableElement<? extends E>> onElement) {
			onElement.accept(theElements.get(((ElementId.ComparableElementId<Integer>) elementId).getValue()));
		}

		@Override
		public MutableObservableSpliterator<E> mutableSpliterator(boolean fromStart) {
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
					return theElementSpliter.estimateSize();
				}

				@Override
				public long getExactSizeIfKnown() {
					return theElementSpliter.getExactSizeIfKnown();
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
			return new ConstantObservableSpliterator(theElements.spliterator(fromStart));
		}

		@Override
		public boolean contains(Object o) {
			return theMap.containsKey(o);
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			return theMap.keySet().containsAll(c);
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
		public String canRemove(Object value) {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			if (!c.isEmpty() && !isEmpty())
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			return false;
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			if (isEmpty())
				return false;
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public void clear() {
			if (!isEmpty())
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public boolean isEventIndexed() {
			return true; // No changes. Might as well make the initial events indexed.
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
