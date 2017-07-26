package org.observe.collect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
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
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollectionDataFlowImpl.AbstractCollectionManager;
import org.observe.collect.ObservableCollectionDataFlowImpl.BaseCollectionDataFlow;
import org.observe.collect.ObservableCollectionDataFlowImpl.BaseCollectionManager;
import org.observe.collect.ObservableCollectionDataFlowImpl.CollectionElementManager;
import org.observe.collect.ObservableCollectionDataFlowImpl.CollectionManager;
import org.observe.collect.ObservableCollectionDataFlowImpl.CollectionUpdate;
import org.observe.collect.ObservableCollectionDataFlowImpl.ElementUpdateResult;
import org.observe.collect.ObservableCollectionDataFlowImpl.FilterMapResult;
import org.observe.collect.ObservableCollectionDataFlowImpl.UniqueElementFinder;
import org.qommons.ArrayUtils;
import org.qommons.Causable;
import org.qommons.ConcurrentHashSet;
import org.qommons.LinkedQueue;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterList;
import org.qommons.collect.ElementHandle;
import org.qommons.collect.ElementId;
import org.qommons.collect.ElementId.SimpleElementIdGenerator;
import org.qommons.collect.ElementSpliterator;
import org.qommons.collect.MutableElementHandle;
import org.qommons.collect.MutableElementHandle.StdMsg;
import org.qommons.collect.MutableElementSpliterator;
import org.qommons.collect.SimpleCause;
import org.qommons.tree.BetterTreeSet;
import org.qommons.tree.BinaryTreeNode;
import org.qommons.tree.CountedRedBlackNode;
import org.qommons.tree.CountedRedBlackNode.DefaultNode;
import org.qommons.tree.CountedRedBlackNode.DefaultTreeMap;
import org.qommons.tree.CountedRedBlackNode.DefaultTreeSet;
import org.qommons.value.Value;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

/** Holds default implementation methods and classes for {@link ObservableCollection} */
public final class ObservableCollectionImpl {
	private ObservableCollectionImpl() {}

	/** Cached TypeToken of {@link String} */
	public static final TypeToken<String> STRING_TYPE = TypeToken.of(String.class);

	/**
	 * @param <E> The type for the set
	 * @param equiv The equivalence set to make a set of
	 * @param c The collection whose values to add to the set
	 * @return The set
	 */
	public static <E> Set<E> toSet(BetterCollection<E> collection, Equivalence<? super E> equiv, Collection<?> c) {
		try (Transaction t = Transactable.lock(c, false, null)) {
			return c.stream().filter(el -> collection.belongs(el)).map(e -> (E) e).collect(Collectors.toCollection(equiv::createSet));
		}
	}

	/**
	 * A default version of {@link ObservableCollection#onChange(Consumer)} for collections whose changes may depend on the elements that
	 * already existed in the collection when the change subscription was made. Such collections must override
	 * {@link ObservableCollection#subscribe(Consumer, boolean)}
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
			}, true);
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
	 */
	public static class CollectionChangesObservable<E> implements Observable<CollectionChangeEvent<E>> {
		/**
		 * Tracks a set of changes corresponding to a set of {@link ObservableCollectionEvent}s, so those changes can be fired at once
		 *
		 * @param <E> The type of values in the collection
		 */
		private static class SessionChangeTracker<E> {
			final CollectionChangeType type;
			final List<ChangeValue<E>> elements;

			/** @param typ The initial change type for this tracker's accumulation */
			protected SessionChangeTracker(CollectionChangeType typ) {
				type = typ;
				elements = new ArrayList<>();
			}
		}

		private static class ChangeValue<E> {
			E newValue;
			E oldValue;
			int index;

			ChangeValue(E oldValue, E newValue, int index) {
				this.oldValue = oldValue;
				this.newValue = newValue;
				this.index = index;
			}
		}

		private static final String SESSION_TRACKER_PROPERTY = "change-tracker";

		/** The collection that this change observable watches */
		protected final ObservableCollection<E> collection;

		/** @param coll The collection for this change observable to watch */
		protected CollectionChangesObservable(ObservableCollection<E> coll) {
			collection = coll;
		}

		@Override
		public Subscription subscribe(Observer<? super CollectionChangeEvent<E>> observer) {
			Object key = new Object();
			return collection.onChange(evt -> {
				evt.getRootCausable().onFinish(key, (cause, data) -> {
					fireEventsFromSessionData((SessionChangeTracker<E>) data.get(SESSION_TRACKER_PROPERTY), cause, observer);
					data.remove(SESSION_TRACKER_PROPERTY);
				}).compute(SESSION_TRACKER_PROPERTY, (k, tracker) -> accumulate((SessionChangeTracker<E>) tracker, evt, observer));
			});
		}

		/**
		 * Accumulates a new collection change into a session tracker. This method may result in events firing.
		 *
		 * @param tracker The change tracker accumulating events
		 * @param event The new event to accumulate
		 * @param observer The observer to fire events for, if necessary
		 * @return The tracker to place in the session to have its changes fired later, if any
		 */
		private SessionChangeTracker<E> accumulate(SessionChangeTracker<E> tracker, ObservableCollectionEvent<? extends E> event,
			Observer<? super CollectionChangeEvent<E>> observer) {
			int collIndex = collection.getElementsBefore(event.getElementId());
			if (tracker == null)
				return replace(tracker, event, observer, collIndex);
			int changeIndex;
			switch (tracker.type) {
			case add:
				switch (event.getType()) {
				case add:
					changeIndex = indexForAdd(tracker, collIndex);
					tracker.elements.add(changeIndex, new ChangeValue<>(event.getOldValue(), event.getNewValue(), collIndex));
					for (changeIndex++; changeIndex < tracker.elements.size(); changeIndex++)
						tracker.elements.get(changeIndex).index++;
					break;
				case remove:
					changeIndex = indexForAdd(tracker, collIndex);
					if (changeIndex < tracker.elements.size() && tracker.elements.get(changeIndex).index == collIndex) {
						tracker.elements.remove(changeIndex);
						for (; changeIndex < tracker.elements.size(); changeIndex++)
							tracker.elements.get(changeIndex).index--;
					} else
						tracker = replace(tracker, event, observer, collIndex);
					break;
				case set:
					changeIndex = indexForAdd(tracker, collIndex);
					if (changeIndex < tracker.elements.size() && tracker.elements.get(changeIndex).index == collIndex)
						tracker.elements.get(changeIndex).newValue = event.getNewValue();
					else
						tracker = replace(tracker, event, observer, collIndex);
					break;
				}
				break;
			case remove:
				switch (event.getType()) {
				case add:
					changeIndex = indexForAdd(tracker, collIndex);
					if (tracker.elements.size() == 1 && changeIndex == 0 && tracker.elements.get(changeIndex).index == collIndex) {
						ChangeValue<E> changeValue = tracker.elements.get(changeIndex);
						tracker = new SessionChangeTracker<>(CollectionChangeType.set);
						changeValue.newValue = event.getNewValue();
						tracker.elements.add(changeValue);
					} else
						tracker = replace(tracker, event, observer, collIndex);
					break;
				case remove:
					changeIndex = indexForAdd(tracker, collIndex);
					while (changeIndex < tracker.elements.size() && tracker.elements.get(changeIndex).index == collIndex) {
						collIndex++;
						changeIndex++;
					}
					tracker.elements.add(changeIndex, new ChangeValue<>(event.getOldValue(), event.getNewValue(), collIndex));
					break;
				case set:
					tracker = replace(tracker, event, observer, collIndex);
					break;
				}
				break;
			case set:
				switch (event.getType()) {
				case add:
					tracker = replace(tracker, event, observer, collIndex);
					break;
				case remove:
					changeIndex = indexForAdd(tracker, collIndex);
					if (changeIndex < tracker.elements.size() && tracker.elements.get(changeIndex).index == collIndex)
						tracker.elements.remove(changeIndex);
					tracker = replace(tracker, event, observer, collIndex);
					break;
				case set:
					changeIndex = indexForAdd(tracker, collIndex);
					if (changeIndex < tracker.elements.size() && tracker.elements.get(changeIndex).index == collIndex)
						tracker.elements.get(changeIndex).newValue = event.getNewValue();
					else
						tracker.elements.add(new ChangeValue<>(event.getOldValue(), event.getNewValue(), collIndex));
					break;
				}
				break;
			}
			return tracker;
		}

		private SessionChangeTracker<E> replace(SessionChangeTracker<E> tracker, ObservableCollectionEvent<? extends E> event,
			Observer<? super CollectionChangeEvent<E>> observer, int collIndex) {
			fireEventsFromSessionData(tracker, event, observer);
			tracker = new SessionChangeTracker<>(event.getType());
			tracker.elements.add(new ChangeValue<>(event.getOldValue(), event.getNewValue(), collIndex));
			return tracker;
		}

		private int indexForAdd(SessionChangeTracker<E> tracker, int collectionIndex) {
			int index = ArrayUtils.binarySearch(tracker.elements, el -> collectionIndex - el.index);
			if (index < 0)
				index = -index - 1;
			return index;
		}

		/**
		 * Fires a change event communicating all changes accumulated into a change tracker
		 *
		 * @param tracker The change tracker into which changes have been accumulated
		 * @param cause The overall cause of the change event
		 * @param observer The observer on which to fire the change event
		 */
		private void fireEventsFromSessionData(SessionChangeTracker<E> tracker, Object cause,
			Observer<? super CollectionChangeEvent<E>> observer) {
			if (tracker == null || tracker.elements.isEmpty())
				return;
			List<CollectionChangeEvent.ElementChange<E>> elements = new ArrayList<>(tracker.elements.size());
			for (ChangeValue<E> elChange : tracker.elements)
				elements.add(new CollectionChangeEvent.ElementChange<>(elChange.newValue, elChange.oldValue, elChange.index));
			CollectionChangeEvent.doWith(new CollectionChangeEvent<>(tracker.type, elements, cause), observer::onNext);
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

	public static abstract class AbstractObservableElementFinder<E> implements ObservableValue<ElementHandle<? extends E>> {
		protected final ObservableCollection<E> theCollection;
		protected final boolean isFirst;
		private final TypeToken<ElementHandle<? extends E>> theType;

		public AbstractObservableElementFinder(ObservableCollection<E> collection, boolean first) {
			theCollection = collection;
			isFirst = first;
			theType = new TypeToken<ElementHandle<? extends E>>() {}.where(new TypeParameter<E>() {}, collection.getType());
		}

		@Override
		public TypeToken<ElementHandle<? extends E>> getType() {
			return theType;
		}

		@Override
		public boolean isSafe() {
			return true;
		}

		@Override
		public ElementHandle<? extends E> get() {
			ElementHandle<? extends E>[] element = new ElementHandle[1];
			find(el -> element[0] = new SimpleElement(el.getElementId(), el.get()));
			return element[0];
		}

		protected abstract boolean find(Consumer<? super ElementHandle<? extends E>> onElement);

		protected abstract boolean test(E value);

		@Override
		public Subscription subscribe(Observer<? super ObservableValueEvent<ElementHandle<? extends E>>> observer) {
			try (Transaction t = theCollection.lock(false, null)) {
				class FinderListener implements Consumer<ObservableCollectionEvent<? extends E>> {
					private SimpleElement theCurrentElement;

					@Override
					public void accept(ObservableCollectionEvent<? extends E> evt) {
						boolean mayReplace;
						int comp;
						if (theCurrentElement == null) {
							mayReplace = true;
							comp = 0;
						} else if (theCurrentElement.getElementId().equals(evt.getElementId())) {
							mayReplace = true;
							comp = 0;
						} else
							mayReplace = ((comp = evt.getElementId().compareTo(theCurrentElement.getElementId())) < 0) == isFirst;
						if (!mayReplace)
							return; // Even if the new element's value matches, it wouldn't replace the current value
						boolean matches = test(evt.getNewValue());
						if (!matches && comp != 0)
							return; // If the new value doesn't match and it's not the current element, we don't care

						// At this point we know that we will have to do something
						Map<Object, Object> causeData = evt.getRootCausable().onFinish(this, (cause, data) -> {
							ElementHandle<E> oldElement = theCurrentElement;
							if (data.get("replacement") == null) {
								// Means we need to find the new value in the collection
								if (!find(el -> {
									theCurrentElement = new SimpleElement(el.getElementId(), el.get());
								})) {
									theCurrentElement = null;
								}
							} else
								theCurrentElement = (SimpleElement) data.get("replacement");
							observer.onNext(createChangeEvent(oldElement, theCurrentElement, cause));
						});
						if (!matches) {
							// The current element's value no longer matches--we need to search for the new value if we don't already know
							// of a better match. The signal for this is a null replacement, so nothing to do here.
						} else {
							// Either:
							// There is no current element and the new element matches--use it unless we already know of a better match
							// Or there the new value is in a better position than the current element
							SimpleElement replacement = (SimpleElement) causeData.get("replacement");
							// If we already know of a replacement element even better-positioned than the new element, ignore the new one
							if (replacement == null || evt.getElementId().compareTo(replacement.getElementId()) <= 0)
								causeData.put("replacement", new SimpleElement(evt.getElementId(), evt.getNewValue()));
						}
					}
				}
				FinderListener listener = new FinderListener();
				if (!find(el -> {
					listener.theCurrentElement = new SimpleElement(el.getElementId(), el.get());
				}))
					listener.theCurrentElement = null;
				Subscription collSub = theCollection.onChange(listener);
				return new Subscription() {
					private boolean isDone;

					@Override
					public void unsubscribe() {
						if (isDone)
							return;
						isDone = true;
						collSub.unsubscribe();
						observer.onCompleted(createChangeEvent(listener.theCurrentElement, listener.theCurrentElement, null));
					}
				};
			}
		}

		private class SimpleElement implements ElementHandle<E> {
			private final ElementId theId;
			private final E theValue;

			public SimpleElement(ElementId id, E value) {
				theId = id;
				theValue = value;
			}

			@Override
			public ElementId getElementId() {
				return theId;
			}

			@Override
			public E get() {
				return theValue;
			}
		}
	}

	/**
	 * Implements {@link ObservableCollection#observeFind(Predicate, Supplier, boolean)}
	 *
	 * @param <E> The type of the value
	 */
	public static class ObservableCollectionFinder<E> extends AbstractObservableElementFinder<E> {
		private final Predicate<? super E> theTest;

		/**
		 * @param collection The collection to find elements in
		 * @param test The test to find elements that pass
		 * @param first Whether to get the first value in the collection that passes or the last value
		 */
		protected ObservableCollectionFinder(ObservableCollection<E> collection, Predicate<? super E> test, boolean first) {
			super(collection, first);
			theTest = test;
		}

		@Override
		protected boolean find(Consumer<? super ElementHandle<? extends E>> onElement) {
			return theCollection.find(theTest, onElement, isFirst);
		}

		@Override
		protected boolean test(E value) {
			return theTest.test(value);
		}
	}

	public static class ObservableEquivalentFinder<E> extends AbstractObservableElementFinder<E> {
		private final E theValue;

		public ObservableEquivalentFinder(ObservableCollection<E> collection, E value, boolean first) {
			super(collection, first);
			if (!collection.belongs(value))
				throw new IllegalArgumentException("Illegal value for collection: " + value);
			theValue = value;
		}

		@Override
		protected boolean find(Consumer<? super ElementHandle<? extends E>> onElement) {
			return theCollection.forElement(theValue, onElement, isFirst);
		}

		@Override
		protected boolean test(E value) {
			return theCollection.equivalence().elementEquals(theValue, value);
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
			Object key = new Object();
			Object[] x = new Object[] { init() };
			Object[] v = new Object[] { getValue((X) x[0]) };
			Subscription sub = theCollection.onChange(evt -> {
				T oldV = (T) v[0];
				X newX = update((X) x[0], evt);
				x[0] = newX;
				v[0] = getValue(newX);
				evt.getRootCausable()
				.onFinish(key, (root, values) -> fireChangeEvent((T) values.get("oldValue"), (T) v[0], root, observer::onNext))
				.computeIfAbsent("oldValue", k -> oldV);
			});
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
			leftSub = left.subscribe(new ValueCountElModifier(true), true);
			rightSub = right.subscribe(new ValueCountElModifier(false), true);

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
			ObservableValue<ObservableCollection<T>> cv = value.mapV(v -> ObservableCollection.constant(value.getType(), v).collect());
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

	public static class ReversedObservableCollection<E> extends BetterList.ReversedList<E>
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
		public int getElementsBefore(ElementId id) {
			return getWrapped().getElementsAfter(id);
		}

		@Override
		public int getElementsAfter(ElementId id) {
			return getWrapped().getElementsBefore(id);
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
			return getWrapped().onChange(evt -> observer.accept(new ObservableCollectionEvent<>(evt.getElementId().reverse(),
				size() - evt.getIndex() - 1, evt.getType(), evt.getOldValue(), evt.getNewValue(), evt)));
		}

		@Override
		public ObservableCollection<E> reverse() {
			return getWrapped();
		}

		@Override
		public ElementSpliterator<E> spliterator(int index) {
			try (Transaction t = lock(false, null)) {
				return super.spliterator(index);
			}
		}

		@Override
		public MutableElementSpliterator<E> mutableSpliterator(int index) {
			try (Transaction t = lock(true, null)) {
				return super.mutableSpliterator(index);
			}
		}

		@Override
		public <T> T ofElementAt(int index, Function<? super ElementHandle<? extends E>, T> onElement) {
			try (Transaction t = lock(false, null)) {
				return super.ofElementAt(index, onElement);
			}
		}

		@Override
		public <T> T ofMutableElementAt(int index, Function<? super MutableElementHandle<? extends E>, T> onElement) {
			try (Transaction t = lock(true, null)) {
				return super.ofMutableElementAt(index, onElement);
			}
		}

		@Override
		public E[] toArray() {
			try (Transaction t = lock(false, null)) {
				return ObservableCollection.super.toArray();
			}
		}

		@Override
		public CollectionSubscription subscribe(Consumer<? super ObservableCollectionEvent<? extends E>> observer, boolean forward) {
			return getWrapped().subscribe(new ReversedSubscriber<>(observer), !forward);
		}

		private static class ReversedSubscriber<E> implements Consumer<ObservableCollectionEvent<? extends E>> {
			private final Consumer<? super ObservableCollectionEvent<? extends E>> theObserver;
			private int theSize;

			ReversedSubscriber(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
				theObserver = observer;
			}

			@Override
			public void accept(ObservableCollectionEvent<? extends E> evt) {
				if (evt.getType() == CollectionChangeType.add)
					theSize++;
				int index = theSize - evt.getIndex() - 1;
				if (evt.getType() == CollectionChangeType.remove)
					theSize++;
				ObservableCollectionEvent.doWith(new ObservableCollectionEvent<>(evt.getElementId().reverse(), index, evt.getType(),
					evt.getOldValue(), evt.getNewValue(), evt), theObserver);
			}
		}
	}

	public static class DerivedLWCollection<E, T> implements ObservableCollection<T> {
		private final ObservableCollection<E> theSource;
		private final CollectionManager<E, ?, T> theFlow;
		private final Equivalence<? super T> theEquivalence;

		public DerivedLWCollection(ObservableCollection<E> source, CollectionManager<E, ?, T> flow) {
			theSource = source;
			theFlow = flow;
			theEquivalence = theFlow.equivalence();

			theFlow.begin(null, null); // Nulls imply light-weight
		}

		protected ObservableCollection<E> getSource() {
			return theSource;
		}

		protected CollectionManager<E, ?, T> getFlow() {
			return theFlow;
		}

		@Override
		public TypeToken<T> getType() {
			return theFlow.getTargetType();
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return theEquivalence;
		}

		@Override
		public boolean isLockSupported() {
			return theSource.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theSource.lock(write, cause);
		}

		@Override
		public int size() {
			return theSource.size();
		}

		@Override
		public boolean isEmpty() {
			return theSource.isEmpty();
		}

		@Override
		public String canAdd(T value) {
			FilterMapResult<T, E> reversed = theFlow.reverse(value);
			if (reversed.error != null)
				return reversed.error;
			return theSource.canAdd(reversed.result);
		}

		@Override
		public ElementId addElement(T e) {
			FilterMapResult<T, E> reversed = theFlow.reverse(e);
			if (reversed.error != null)
				throw new IllegalArgumentException(reversed.error);
			return theSource.addElement(reversed.result);
		}

		@Override
		public void clear() {
			theSource.clear();
		}

		@Override
		public int getElementsBefore(ElementId id) {
			return theSource.getElementsBefore(id);
		}

		@Override
		public int getElementsAfter(ElementId id) {
			return theSource.getElementsAfter(id);
		}

		@Override
		public boolean forElement(T value, Consumer<? super ElementHandle<? extends T>> onElement, boolean first) {
			ElementSpliterator<E> spliter = first ? theSource.spliterator(true) : theSource.spliterator(false).reverse();
			boolean[] success = new boolean[1];
			while (!success[0] && spliter.tryAdvanceElement(el -> {
				if (equivalence().elementEquals(theFlow.map(el.get()).result, value)) {
					onElement.accept(elementFor(el));
					success[0] = true;
				}
			})) {
			}
			return success[0];
		}

		@Override
		public boolean forMutableElement(T value, Consumer<? super MutableElementHandle<? extends T>> onElement, boolean first) {
			MutableElementSpliterator<E> spliter = first ? theSource.mutableSpliterator(true)
				: theSource.mutableSpliterator(false).reverse();
			boolean[] success = new boolean[1];
			while (!success[0] && spliter.tryAdvanceElementM(el -> {
				if (equivalence().elementEquals(theFlow.map(el.get()).result, value)) {
					onElement.accept(mutableElementFor(el));
					success[0] = true;
				}
			})) {
			}
			return success[0];
		}

		protected ElementHandle<T> elementFor(ElementHandle<? extends E> el) {
			return new ElementHandle<T>() {
				@Override
				public T get() {
					return theFlow.map(el.get()).result;
				}

				@Override
				public ElementId getElementId() {
					return el.getElementId();
				}
			};
		}

		protected MutableElementHandle<T> mutableElementFor(MutableElementHandle<? extends E> el) {
			return theFlow.createElement(el.getElementId(), el.get(), null).map(el, el.getElementId());
		}

		@Override
		public <X> X ofElementAt(ElementId elementId, Function<? super ElementHandle<? extends T>, X> onElement) {
			return theSource.ofElementAt(elementId, el -> onElement.apply(elementFor(el)));
		}

		@Override
		public <X> X ofMutableElementAt(ElementId elementId, Function<? super MutableElementHandle<? extends T>, X> onElement) {
			return theSource.ofMutableElementAt(elementId, el -> onElement.apply(mutableElementFor(el)));
		}

		@Override
		public <X> X ofElementAt(int index, Function<? super ElementHandle<? extends T>, X> onElement) {
			return theSource.ofElementAt(index, el -> onElement.apply(elementFor(el)));
		}

		@Override
		public <X> X ofMutableElementAt(int index, Function<? super MutableElementHandle<? extends T>, X> onElement) {
			return theSource.ofMutableElementAt(index, el -> onElement.apply(mutableElementFor(el)));
		}

		@Override
		public MutableElementSpliterator<T> mutableSpliterator(boolean fromStart) {
			MutableElementSpliterator<E> srcSpliter = theSource.mutableSpliterator(fromStart);
			return new DerivedMutableSpliterator(srcSpliter);
		}

		@Override
		public MutableElementSpliterator<T> mutableSpliterator(int index) {
			MutableElementSpliterator<E> srcSpliter = theSource.mutableSpliterator(index);
			return new DerivedMutableSpliterator(srcSpliter);
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends T>> observer) {
			return getSource().onChange(evt -> {
				T oldValue, newValue;
				switch (evt.getType()) {
				case add:
					newValue = theFlow.map(evt.getNewValue()).result;
					oldValue = null;
					break;
				case remove:
					oldValue = theFlow.map(evt.getOldValue()).result;
					newValue = oldValue;
					break;
				case set:
					oldValue = theFlow.map(evt.getOldValue()).result;
					newValue = theFlow.map(evt.getNewValue()).result;
					break;
				default:
					throw new IllegalStateException("Unrecognized collection change type: " + evt.getType());
				}
				observer
				.accept(new ObservableCollectionEvent<>(evt.getElementId(), evt.getIndex(), evt.getType(), oldValue, newValue, evt));
			});
		}

		protected class DerivedMutableSpliterator implements MutableElementSpliterator<T> {
			private final MutableElementSpliterator<E> theSourceSpliter;

			DerivedMutableSpliterator(MutableElementSpliterator<E> srcSpliter) {
				theSourceSpliter = srcSpliter;
			}

			@Override
			public long estimateSize() {
				return theSourceSpliter.estimateSize();
			}

			@Override
			public long getExactSizeIfKnown() {
				return theSourceSpliter.getExactSizeIfKnown();
			}

			@Override
			public int characteristics() {
				return theSourceSpliter.characteristics() & (~(DISTINCT | SORTED));
			}

			@Override
			public boolean tryAdvance(Consumer<? super T> action) {
				return theSourceSpliter.tryAdvance(v -> action.accept(theFlow.map(v).result));
			}

			@Override
			public void forEachRemaining(Consumer<? super T> action) {
				theSourceSpliter.forEachRemaining(v -> action.accept(theFlow.map(v).result));
			}

			@Override
			public boolean tryReverse(Consumer<? super T> action) {
				return theSourceSpliter.tryReverse(v -> action.accept(theFlow.map(v).result));
			}

			@Override
			public void forEachReverse(Consumer<? super T> action) {
				theSourceSpliter.forEachReverse(v -> action.accept(theFlow.map(v).result));
			}

			@Override
			public boolean tryAdvanceElement(Consumer<? super ElementHandle<T>> action) {
				return theSourceSpliter.tryAdvanceElement(el -> action.accept(elementFor(el)));
			}

			@Override
			public void forEachElement(Consumer<? super ElementHandle<T>> action) {
				theSourceSpliter.forEachElement(el -> action.accept(elementFor(el)));
			}

			@Override
			public boolean tryReverseElement(Consumer<? super ElementHandle<T>> action) {
				return theSourceSpliter.tryReverseElement(el -> action.accept(elementFor(el)));
			}

			@Override
			public void forEachElementReverse(Consumer<? super ElementHandle<T>> action) {
				theSourceSpliter.forEachElementReverse(el -> action.accept(elementFor(el)));
			}

			@Override
			public boolean tryAdvanceElementM(Consumer<? super MutableElementHandle<T>> action) {
				return theSourceSpliter.tryAdvanceElementM(el -> action.accept(mutableElementFor(el)));
			}

			@Override
			public void forEachElementM(Consumer<? super MutableElementHandle<T>> action) {
				theSourceSpliter.forEachElementM(el -> action.accept(mutableElementFor(el)));
			}

			@Override
			public boolean tryReverseElementM(Consumer<? super MutableElementHandle<T>> action) {
				return theSourceSpliter.tryReverseElementM(el -> action.accept(mutableElementFor(el)));
			}

			@Override
			public void forEachElementReverseM(Consumer<? super MutableElementHandle<T>> action) {
				theSourceSpliter.forEachElementReverseM(el -> action.accept(mutableElementFor(el)));
			}

			@Override
			public MutableElementSpliterator<T> trySplit() {
				MutableElementSpliterator<E> srcSplit = theSourceSpliter.trySplit();
				return srcSplit == null ? null : new DerivedMutableSpliterator(srcSplit);
			}
		}
	}

	private static final Set<DerivedCollection<?, ?>> STRONG_REFS = new ConcurrentHashSet<>();

	public static class DerivedCollection<E, T> implements ObservableCollection<T> {
		protected static class DerivedCollectionElement<E, T> implements ElementId {
			final CollectionElementManager<E, ?, T> manager;
			BinaryTreeNode<DerivedCollectionElement<E, T>> presentNode;

			protected DerivedCollectionElement(CollectionElementManager<E, ?, T> manager, E initValue) {
				this.manager = manager;
			}

			@Override
			public int compareTo(ElementId o) {
				if (presentNode == null)
					throw new IllegalStateException("This node is not currentl present in the collection");
				DerivedCollectionElement<E, T> other = (DerivedCollectionElement<E, T>) o;
				if (other.presentNode == null)
					throw new IllegalStateException("The node is not currentl present in the collection");
				return presentNode.getNodesBefore() - other.presentNode.getNodesBefore();
			}

			public T get() {
				return manager.get();
			}

			protected boolean set(E baseValue, Object cause) {
				return manager.set(baseValue, cause);
			}

			protected void removed(Object cause) {
				manager.removed(cause);
			}

			protected ElementUpdateResult update(CollectionUpdate update,
				Consumer<Consumer<MutableElementHandle<? extends E>>> sourceElement) {
				return manager.update(update, sourceElement);
			}

			@Override
			public int hashCode() {
				return manager.getElementId().hashCode();
			}

			@Override
			public boolean equals(Object obj) {
				return obj instanceof DerivedCollection.DerivedCollectionElement
					&& manager.getElementId().equals(((DerivedCollectionElement<E, T>) obj).manager.getElementId());
			}

			@Override
			public String toString() {
				return manager.getElementId().toString();
			}
		}

		private final ObservableCollection<E> theSource;
		private final CollectionManager<E, ?, T> theFlow;
		private final Map<ElementId, DerivedCollectionElement<E, T>> theElements;
		private final BetterTreeSet<DerivedCollectionElement<E, T>> thePresentElements;
		private final LinkedQueue<Consumer<? super ObservableCollectionEvent<? extends T>>> theListeners;
		private final AtomicInteger theListenerCount;
		private final Equivalence<? super T> theEquivalence;
		private final Consumer<ObservableCollectionEvent<? extends E>> theSourceAction;

		public DerivedCollection(ObservableCollection<E> source, CollectionManager<E, ?, T> flow, Observable<?> until) {
			theSource = source;
			theFlow = flow;
			theElements = new java.util.TreeMap<>(ElementId::compareTo);
			thePresentElements = new BetterTreeSet<>(false, (e1, e2) -> e1.manager.compareTo(e2.manager));
			theListeners = new LinkedQueue<>();
			theListenerCount = new AtomicInteger();
			theEquivalence = flow.equivalence();
			// Must maintain a strong reference to the event listener so it is not GC'd while the collection is still alive
			theSourceAction = evt -> {
				final DerivedCollectionElement<E, T> element;
				try (Transaction flowTransaction = theFlow.lock(false, null)) {
					switch (evt.getType()) {
					case add:
						element = createElement(theFlow.createElement(evt.getElementId(), evt.getNewValue(), evt), evt.getNewValue());
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
							removeFromPresent(element, element.get(), evt);
						element.removed(evt);
						break;
					case set:
						element = theElements.get(evt.getElementId());
						if (element == null)
							return; // Must be statically filtered out
						boolean prePresent = element.presentNode != null;
						T oldValue = prePresent ? element.get() : null;
						boolean fireUpdate = element.set(evt.getNewValue(), evt);
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
			};

			// Begin listening
			try (Transaction initialTransaction = lock(false, null)) {
				theFlow.begin(update -> {
					try (Transaction collT = theSource.lock(false, update.getCause())) {
						if (update.getElement() != null)
							applyUpdate(theElements.get(update.getElement()), update);
						else {
							for (DerivedCollectionElement<E, T> element : theElements.values())
								applyUpdate(element, update);
						}
					}
					theFlow.postChange();
				}, until);
				WeakConsumer<ObservableCollectionEvent<? extends E>> weak = new WeakConsumer<>(theSourceAction);
				CollectionSubscription sub = theSource.subscribe(weak, true);
				weak.withSubscription(sub);
				Subscription takeSub = until.take(1).act(v -> sub.unsubscribe(true));
				weak.onUnsubscribe(() -> takeSub.unsubscribe());
			}
		}

		protected DerivedCollectionElement<E, T> createElement(CollectionElementManager<E, ?, T> elMgr, E initValue) {
			return new DerivedCollectionElement<>(elMgr, initValue);
		}

		protected ObservableCollection<E> getSource() {
			return theSource;
		}

		protected CollectionManager<E, ?, T> getFlow() {
			return theFlow;
		}

		protected BetterTreeSet<DerivedCollectionElement<E, T>> getPresentElements() {
			return thePresentElements;
		}

		private void addToPresent(DerivedCollectionElement<E, T> element, Object cause) {
			element.presentNode = thePresentElements.addElement(element);
			fireListeners(new ObservableCollectionEvent<>(element, element.presentNode.getNodesBefore(), CollectionChangeType.add, null,
				element.get(), cause));
		}

		private void removeFromPresent(DerivedCollectionElement<E, T> element, T oldValue, Object cause) {
			fireListeners(
				new ObservableCollectionEvent<>(element, element.presentNode.getNodesBefore(), CollectionChangeType.remove, oldValue,
					oldValue, cause));
			thePresentElements.forMutableElementAt(element.presentNode, el -> el.remove());
			element.presentNode = null;
		}

		private void updateInPresent(DerivedCollectionElement<E, T> element, T oldValue, Object cause, boolean fireUpdate) {
			// Need to verify that the ordering is still correct. Otherwise, remove and re-add.
			BinaryTreeNode<DerivedCollectionElement<E, T>> left = element.presentNode.getClosest(true);
			BinaryTreeNode<DerivedCollectionElement<E, T>> right = element.presentNode.getClosest(false);
			if ((left != null && left.compareTo(element.presentNode) > 0) || (right != null && right.compareTo(element.presentNode) < 0)) {
				// Remove the element and re-add at the new position.
				// Need to fire the remove event while the node is in the old position.
				removeFromPresent(element, oldValue, cause);
				addToPresent(element, cause);
			} else if (oldValue != element.get())
				fireListeners(
					new ObservableCollectionEvent<>(element, element.presentNode.getNodesBefore(), CollectionChangeType.set, oldValue,
						element.get(), cause));
			else if (fireUpdate)
				fireListeners(
					new ObservableCollectionEvent<>(element, element.presentNode.getNodesBefore(), CollectionChangeType.set, oldValue,
						oldValue, cause));
		}

		private void fireListeners(ObservableCollectionEvent<T> event) {
			for (Consumer<? super ObservableCollectionEvent<? extends T>> listener : theListeners)
				listener.accept(event);
		}

		private void applyUpdate(DerivedCollectionElement<E, T> element, CollectionUpdate update) {
			boolean prePresent = element.manager.isPresent();
			T oldValue = prePresent ? element.get() : null;
			ElementUpdateResult fireUpdate = element.update(update,
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
		public int getElementsBefore(ElementId id) {
			BinaryTreeNode<?> node = ((DerivedCollectionElement<E, T>) id).presentNode;
			if (node == null)
				throw new IllegalArgumentException("This element is not present in the collection");
			return node.getNodesBefore();
		}

		@Override
		public int getElementsAfter(ElementId id) {
			BinaryTreeNode<?> node = ((DerivedCollectionElement<E, T>) id).presentNode;
			if (node == null)
				throw new IllegalArgumentException("This element is not present in the collection");
			return node.getNodesAfter();
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends T>> observer) {
			theListeners.add(observer);
			// Add a strong reference to this collection while we have listeners.
			// Otherwise, this collection could be GC'd and listeners (which may not reference this collection) would just be left hanging
			if (theListenerCount.getAndIncrement() == 0)
				STRONG_REFS.add(this);
			return () -> {
				theListeners.remove(observer);
				if (theListenerCount.decrementAndGet() == 0)
					STRONG_REFS.remove(this);
			};
		}

		@Override
		public CollectionSubscription subscribe(Consumer<? super ObservableCollectionEvent<? extends T>> observer, boolean forward) {
			try (Transaction t = lock(false, null)) {
				SubscriptionCause.doWith(new SubscriptionCause(), c -> {
					int index = 0;
					for (DerivedCollectionElement<E, T> element : thePresentElements) {
						observer.accept(
							new ObservableCollectionEvent<>(element, index++, CollectionChangeType.add, null, element.get(), c));
					}
				});
				return removeAll -> {
					SubscriptionCause.doWith(new SubscriptionCause(), c -> {
						int index = 0;
						for (DerivedCollectionElement<E, T> element : thePresentElements.reverse()) {
							observer.accept(new ObservableCollectionEvent<>(element, index++, CollectionChangeType.remove, null,
								element.get(), c));
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
				return thePresentElements.get(index).get();
			}
		}

		protected boolean checkValue(Object o) {
			return equivalence().isElement(o) && (o == null || getType().getRawType().isInstance(o));
		}

		@Override
		public boolean forElement(T value, Consumer<? super ElementHandle<? extends T>> onElement, boolean first) {
			try (Transaction t = lock(false, null)) {
				UniqueElementFinder<T> finder = getFlow().getElementFinder();
				if (finder != null) {
					ElementId id = finder.getUniqueElement(value);
					if (id == null)
						return false;
					DerivedCollectionElement<E, T> element = theElements.get(id);
					if (element == null)
						throw new IllegalStateException(MutableElementHandle.StdMsg.NOT_FOUND);
					if (element.presentNode == null)
						return false;
					onElement.accept(observableElementFor(element));
					return true;
				}
				for (DerivedCollectionElement<E, T> el : (first ? thePresentElements : thePresentElements.reverse()))
					if (equivalence().elementEquals(el.get(), value)) {
						onElement.accept(observableElementFor(el));
						return true;
					}
				return false;
			}
		}

		@Override
		public <X> X ofElementAt(ElementId elementId, Function<? super ElementHandle<? extends T>, X> onElement) {
			return onElement.apply(observableElementFor((DerivedCollectionElement<E, T>) elementId));
		}

		protected ElementHandle<T> observableElementFor(DerivedCollectionElement<E, T> el) {
			return new ElementHandle<T>() {
				@Override
				public T get() {
					return el.get();
				}

				@Override
				public ElementId getElementId() {
					return el;
				}
			};
		}

		@Override
		public boolean forMutableElement(T value, Consumer<? super MutableElementHandle<? extends T>> onElement, boolean first) {
			try (Transaction t = lock(true, null)) {
				UniqueElementFinder<T> finder = getFlow().getElementFinder();
				if (finder != null) {
					ElementId id = finder.getUniqueElement(value);
					if (id == null)
						return false;
					DerivedCollectionElement<E, T> element = theElements.get(id);
					if (element == null)
						throw new IllegalStateException(MutableElementHandle.StdMsg.NOT_FOUND);
					if (element.presentNode == null)
						return false;
					theSource.forMutableElementAt(id, srcEl -> onElement.accept(element.manager.map(srcEl, element)));
					return true;
				}
				for (DerivedCollectionElement<E, T> el : thePresentElements)
					if (equivalence().elementEquals(el.get(), value)) {
						theSource.forMutableElementAt(el.manager.getElementId(), srcEl -> onElement.accept(el.manager.map(srcEl, el)));
						return true;
					}
				return false;
			}
		}

		@Override
		public <X> X ofMutableElementAt(ElementId elementId, Function<? super MutableElementHandle<? extends T>, X> onElement) {
			DerivedCollectionElement<E, T> el = (DerivedCollectionElement<E, T>) elementId;
			return theSource.ofMutableElementAt(el.manager.getElementId(), srcEl -> onElement.apply(el.manager.map(srcEl, el)));
		}

		@Override
		public <X> X ofElementAt(int index, Function<? super ElementHandle<? extends T>, X> onElement) {
			return ofElementAt(thePresentElements.get(index), onElement);
		}

		@Override
		public <X> X ofMutableElementAt(int index, Function<? super MutableElementHandle<? extends T>, X> onElement) {
			return ofMutableElementAt(thePresentElements.get(index), onElement);
		}

		@Override
		public MutableElementSpliterator<T> mutableSpliterator(boolean fromStart) {
			return new MutableDerivedSpliterator(thePresentElements.spliterator(fromStart));
		}

		@Override
		public MutableElementSpliterator<T> mutableSpliterator(int index) {
			return new MutableDerivedSpliterator(getPresentElements().spliterator(index));
		}

		protected MutableElementSpliterator<T> spliterator(ElementSpliterator<DerivedCollectionElement<E, T>> elementSpliter) {
			return new MutableDerivedSpliterator(elementSpliter);
		}

		@Override
		public String canAdd(T value) {
			if (!theFlow.isReversible())
				return MutableElementHandle.StdMsg.UNSUPPORTED_OPERATION;
			else if (!checkValue(value))
				return MutableElementHandle.StdMsg.BAD_TYPE;
			try (Transaction t = lock(false, null)) {
				FilterMapResult<T, E> reversed = theFlow.canAdd(new FilterMapResult<>(value));
				if (reversed.error != null)
					return reversed.error;
				return theSource.canAdd(reversed.result);
			}
		}

		@Override
		public ElementId addElement(T e) {
			if (!theFlow.isReversible())
				throw new UnsupportedOperationException(MutableElementHandle.StdMsg.UNSUPPORTED_OPERATION);
			else if (!checkValue(e))
				throw new IllegalArgumentException(MutableElementHandle.StdMsg.BAD_TYPE);
			try (Transaction t = lock(true, null)) {
				FilterMapResult<T, E> reversed = theFlow.canAdd(new FilterMapResult<>(e));
				if (reversed.error != null)
					throw new IllegalArgumentException(reversed.error);
				ElementId sourceElementId = theSource.addElement(reversed.result);
				return theElements.get(sourceElementId); // Should have been added
			}
		}

		@Override
		public void clear() {
			try (Transaction t = lock(true, null)) {
				if (!theFlow.isStaticallyFiltered() && !theFlow.isDynamicallyFiltered())
					theSource.clear();
				else
					mutableSpliterator().forEachElementM(el -> el.remove());
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

		private class MutableDerivedSpliterator implements MutableElementSpliterator<T> {
			private final ElementSpliterator<DerivedCollectionElement<E, T>> theElementSpliter;

			MutableDerivedSpliterator(ElementSpliterator<DerivedCollectionElement<E, T>> elementSpliter) {
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
			public boolean tryAdvanceElement(Consumer<? super ElementHandle<T>> action) {
				try (Transaction t = lock(false, null)) {
					return theElementSpliter.tryAdvance(element -> action.accept(observableElementFor(element)));
				}
			}

			@Override
			public boolean tryReverseElement(Consumer<? super ElementHandle<T>> action) {
				try (Transaction t = lock(false, null)) {
					return theElementSpliter.tryReverse(element -> action.accept(observableElementFor(element)));
				}
			}

			@Override
			public void forEachElement(Consumer<? super ElementHandle<T>> action) {
				try (Transaction t = lock(false, null)) {
					theElementSpliter.forEachRemaining(element -> action.accept(observableElementFor(element)));
				}
			}

			@Override
			public void forEachElementReverse(Consumer<? super ElementHandle<T>> action) {
				try (Transaction t = lock(false, null)) {
					theElementSpliter.forEachReverse(element -> action.accept(observableElementFor(element)));
				}
			}

			@Override
			public boolean tryAdvanceElementM(Consumer<? super MutableElementHandle<T>> action) {
				try (Transaction t = lock(true, null)) {
					return theElementSpliter.tryAdvance(element -> theSource.forMutableElementAt(element.manager.getElementId(),
						sourceEl -> action.accept(element.manager.map(sourceEl, element))));
				}
			}

			@Override
			public boolean tryReverseElementM(Consumer<? super MutableElementHandle<T>> action) {
				try (Transaction t = lock(true, null)) {
					return theElementSpliter.tryReverse(element -> theSource.forMutableElementAt(element.manager.getElementId(),
						sourceEl -> action.accept(element.manager.map(sourceEl, element))));
				}
			}

			@Override
			public void forEachElementM(Consumer<? super MutableElementHandle<T>> action) {
				try (Transaction t = lock(true, null)) {
					theElementSpliter.forEachRemaining(element -> theSource.forMutableElementAt(element.manager.getElementId(),
						sourceEl -> action.accept(element.manager.map(sourceEl, element))));
				}
			}

			@Override
			public void forEachElementReverseM(Consumer<? super MutableElementHandle<T>> action) {
				try (Transaction t = lock(true, null)) {
					theElementSpliter.forEachReverse(element -> theSource.forMutableElementAt(element.manager.getElementId(),
						sourceEl -> action.accept(element.manager.map(sourceEl, element))));
				}
			}

			@Override
			public MutableElementSpliterator<T> trySplit() {
				ElementSpliterator<DerivedCollectionElement<E, T>> split = theElementSpliter.trySplit();
				return split == null ? null : new MutableDerivedSpliterator(split);
			}
		}
	}

	public static <E> CollectionDataFlow<E, E, E> create(TypeToken<E> type, boolean threadSafe, Collection<? extends E> initialValues) {
		DefaultObservableCollection<E> collection = new DefaultObservableCollection<>(type, threadSafe);
		CollectionDataFlow<E, E, E> flow = collection.flow();
		if (!initialValues.isEmpty())
			flow = new ObservableCollectionDataFlowImpl.InitialElementsDataFlow<>(collection, flow, type, initialValues);
		return flow;
	}

	/**
	 * <p>
	 * Implements {@link ObservableCollectionImpl#create(TypeToken)}.
	 * </p>
	 *
	 * <p>
	 * This collection is a gimped collection that does not even keep track of its values, but relies on {@link DerivedCollection} for
	 * almost all its functionality. As such, this collection should never be used, except as a source for DerivedCollection.
	 * </p>
	 *
	 * @param <E> The type for the collection
	 */
	private static class DefaultObservableCollection<E> implements ObservableCollection<E> {
		private final TypeToken<E> theType;
		private final ReentrantReadWriteLock theLock;
		private final LinkedList<Causable> theTransactionCauses;
		private final SimpleElementIdGenerator theElementIdGen;
		private Consumer<? super ObservableCollectionEvent<? extends E>> theObserver;

		DefaultObservableCollection(TypeToken<E> type, boolean threadSafe) {
			theType = type;
			theLock = threadSafe ? new ReentrantReadWriteLock() : null;
			theTransactionCauses = new LinkedList<>();
			theElementIdGen = ElementId.createSimpleIdGenerator();
		}

		@Override
		public boolean isLockSupported() {
			return theLock != null;
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			Lock lock;
			if (theLock == null)
				lock = null;
			else if (write)
				lock = theLock.writeLock();
			else
				lock = theLock.readLock();
			if (lock != null)
				lock.lock();
			Causable tCause;
			if (cause == null && !theTransactionCauses.isEmpty())
				tCause = null;
			else if (cause instanceof Causable)
				tCause = (Causable) cause;
			else
				tCause = new SimpleCause(cause);
			if (write && tCause != null)
				theTransactionCauses.add(tCause);
			return new Transaction() {
				private boolean isClosed;

				@Override
				public void close() {
					if (isClosed)
						return;
					isClosed = true;
					if (write && tCause != null)
						theTransactionCauses.removeLastOccurrence(tCause);
					if (lock != null)
						lock.unlock();
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
			throw new UnsupportedOperationException("This method is not implemented for the default observable collection");
		}

		@Override
		public boolean isEmpty() {
			throw new UnsupportedOperationException("This method is not implemented for the default observable collection");
		}

		@Override
		public int getElementsBefore(ElementId id) {
			return theElementIdGen.getElementsBefore(id);
		}

		@Override
		public int getElementsAfter(ElementId id) {
			return theElementIdGen.getElementsAfter(id);
		}

		@Override
		public ElementId addElement(E e) {
			try (Transaction t = lock(true, null)) {
				ElementId newId = theElementIdGen.newId();
				theObserver.accept(new ObservableCollectionEvent<>(newId, theElementIdGen.getElementsBefore(newId),
					CollectionChangeType.add, null, e, theTransactionCauses.peekLast()));
				return newId;
			}
		}

		@Override
		public void clear() {
			throw new UnsupportedOperationException("This method is not implemented for the default observable collection");
		}

		@Override
		public MutableElementSpliterator<E> mutableSpliterator(boolean fromStart) {
			if (!theElementIdGen.isEmpty())
				throw new UnsupportedOperationException(
					"This method is not implemented for the default observable collection" + " (when non-empty)");
			return MutableElementSpliterator.empty();
		}

		@Override
		public MutableElementSpliterator<E> mutableSpliterator(int index) {
			throw new UnsupportedOperationException("This method is not implemented for the default observable collection");
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
			if (theObserver != null)
				throw new UnsupportedOperationException("Multiple observers are not supported for the default observable collection");
			theObserver = observer;
			return () -> theObserver = null;
		}

		@Override
		public String canAdd(E value) {
			return null;
		}

		@Override
		public boolean forElement(E value, Consumer<? super ElementHandle<? extends E>> onElement, boolean first) {
			throw new UnsupportedOperationException("This method is not implemented for the default observable collection");
		}

		@Override
		public boolean forMutableElement(E value, Consumer<? super MutableElementHandle<? extends E>> onElement, boolean first) {
			throw new UnsupportedOperationException("This method is not implemented for the default observable collection");
		}

		@Override
		public <T> T ofElementAt(ElementId elementId, Function<? super ElementHandle<? extends E>, T> onElement) {
			throw new UnsupportedOperationException("This method is not implemented for the default observable collection");
		}

		@Override
		public <T> T ofMutableElementAt(ElementId elementId, Function<? super MutableElementHandle<? extends E>, T> onElement) {
			class DefaultMutableElement implements MutableElementHandle<E> {
				private boolean isRemoved;

				@Override
				public ElementId getElementId() {
					return elementId;
				}

				@Override
				public E get() {
					// The collection manager on top of this element keeps track of its own value and does not ask for its source
					throw new UnsupportedOperationException(MutableElementHandle.StdMsg.UNSUPPORTED_OPERATION);
				}

				@Override
				public String canRemove() {
					if (isRemoved)
						throw new IllegalStateException(MutableElementHandle.StdMsg.NOT_FOUND);
					return null;
				}

				@Override
				public void remove() throws UnsupportedOperationException {
					if (isRemoved)
						throw new IllegalStateException(MutableElementHandle.StdMsg.NOT_FOUND);
					try (Transaction t = lock(true, null)) {
						// The DerivedCollection keeps track of its own values and does not pay attention to the values in the event
						ObservableCollectionEvent<E> evt = new ObservableCollectionEvent<>(elementId,
							theElementIdGen.getElementsBefore(elementId), CollectionChangeType.remove, null, null,
							theTransactionCauses.getLast());
						theElementIdGen.remove(elementId);
						theObserver.accept(evt);
						isRemoved = true;
					}
				}

				@Override
				public String canAdd(E value, boolean before) {
					if (isRemoved)
						throw new IllegalStateException(MutableElementHandle.StdMsg.NOT_FOUND);
					return null;
				}

				@Override
				public ElementId add(E value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
					if (isRemoved)
						throw new IllegalStateException(MutableElementHandle.StdMsg.NOT_FOUND);
					try (Transaction t = lock(true, null)) {
						ElementId newId = theElementIdGen.newId(elementId, before);
						theObserver.accept(new ObservableCollectionEvent<>(newId, theElementIdGen.getElementsBefore(newId),
							CollectionChangeType.add, null, value, theTransactionCauses.peekLast()));
						return newId;
					}
				}

				@Override
				public void set(E value) throws IllegalArgumentException, UnsupportedOperationException {
					if (isRemoved)
						throw new IllegalStateException(MutableElementHandle.StdMsg.NOT_FOUND);
					try (Transaction t = lock(true, null)) {
						// The DerivedCollection keeps track of its own values and does not pay attention to the values in the event
						theObserver.accept(new ObservableCollectionEvent<>(elementId, theElementIdGen.getElementsBefore(elementId),
							CollectionChangeType.remove, null, value, theTransactionCauses.getLast()));
					}
				}

				@Override
				public String isAcceptable(E value) {
					if (isRemoved)
						throw new IllegalStateException(MutableElementHandle.StdMsg.NOT_FOUND);
					return null;
				}

				@Override
				public String isEnabled() {
					if (isRemoved)
						throw new IllegalStateException(MutableElementHandle.StdMsg.NOT_FOUND);
					return null;
				}
			}
			return onElement.apply(new DefaultMutableElement());
		}

		@Override
		public <T> T ofElementAt(int index, Function<? super ElementHandle<? extends E>, T> onElement) {
			throw new UnsupportedOperationException("This method is not implemented for the default observable collection");
		}

		@Override
		public <T> T ofMutableElementAt(int index, Function<? super MutableElementHandle<? extends E>, T> onElement) {
			throw new UnsupportedOperationException("This method is not implemented for the default observable collection");
		}

		@Override
		public <T> CollectionDataFlow<E, E, E> flow() {
			// Overridden because by default, ObservableCollection.flow().collect() just returns the collection
			return new DefaultCollectionFlow<>(this);
		}

		private static class DefaultCollectionFlow<E> extends BaseCollectionDataFlow<E> {
			DefaultCollectionFlow(ObservableCollection<E> source) {
				super(source);
			}

			@Override
			public boolean isLightWeight() {
				return false;
			}

			@Override
			public AbstractCollectionManager<E, ?, E> manageCollection() {
				return new DefaultCollectionManager<>(getTargetType(), getSource().equivalence(), getSource().isLockSupported());
			}

			@Override
			public ObservableCollection<E> collect(Observable<?> until) {
				return new DerivedCollection<>(getSource(), manageCollection(), until);
			}
		}

		private static class DefaultCollectionManager<E> extends BaseCollectionManager<E> {
			public DefaultCollectionManager(TypeToken<E> targetType, Equivalence<? super E> equivalence, boolean threadSafe) {
				super(targetType, equivalence, threadSafe);
			}

			@Override
			public boolean isStaticallyFiltered() {
				return true; // This flag prevents DerivedCollection from calling the clear() method
			}
		}
	}

	/**
	 * Implements {@link ObservableCollection#flattenValue(ObservableValue)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	public static class FlattenedValueCollection<E> implements ObservableCollection<E> {
		private final ObservableValue<? extends ObservableCollection<? extends E>> theCollectionObservable;
		private final ReentrantReadWriteLock theLock;
		private final TypeToken<E> theType;

		/** @param collectionObservable The value to present as a static collection */
		protected FlattenedValueCollection(ObservableValue<? extends ObservableCollection<? extends E>> collectionObservable) {
			theCollectionObservable = collectionObservable;
			theLock = new ReentrantReadWriteLock();
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
			Lock lock = write ? theLock.writeLock() : theLock.readLock();
			lock.lock();
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			Transaction t = coll == null ? Transaction.NONE : coll.lock(write, cause);
			return () -> {
				t.close();
				lock.unlock();
			};
		}

		@Override
		public boolean belongs(Object o) {
			// This collection's equivalence may change (ugh), so we need to be more inclusive than the default
			return o == null || theType.getRawType().isInstance(o);
		}

		@Override
		public Equivalence<? super E> equivalence() {
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			return coll == null ? Equivalence.DEFAULT : (Equivalence<? super E>) coll.equivalence();
		}

		@Override
		public int getElementsBefore(ElementId id) {
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			if (coll == null)
				throw new IllegalArgumentException("This element is not present in this collection");
			return coll.getElementsBefore(id);
		}

		@Override
		public int getElementsAfter(ElementId id) {
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			if (coll == null)
				throw new IllegalArgumentException("This element is not present in this collection");
			return coll.getElementsAfter(id);
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
		public E get(int index) {
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			if (coll == null)
				throw new IndexOutOfBoundsException(index + " of 0");
			return coll.get(index);
		}

		@Override
		public String canAdd(E value) {
			ObservableCollection<? extends E> current = theCollectionObservable.get();
			if (current == null)
				return MutableElementHandle.StdMsg.UNSUPPORTED_OPERATION;
			else if (value != null && !current.getType().getRawType().isInstance(value))
				return MutableElementHandle.StdMsg.BAD_TYPE;
			return ((ObservableCollection<E>) current).canAdd(value);
		}

		@Override
		public ElementId addElement(E e) {
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			if (coll == null)
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			if (e != null && !coll.getType().getRawType().isInstance(e))
				throw new IllegalArgumentException(MutableElementHandle.StdMsg.BAD_TYPE);
			return ((ObservableCollection<E>) coll).addElement(e);
		}

		@Override
		public void clear() {
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			if (coll != null)
				coll.clear();
		}

		@Override
		public boolean forElement(E value, Consumer<? super ElementHandle<? extends E>> onElement, boolean first) {
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			if (coll == null || !coll.belongs(value))
				return false;
			return ((ObservableCollection<E>) coll).forElement(value, onElement, first);
		}

		@Override
		public boolean forMutableElement(E value, Consumer<? super MutableElementHandle<? extends E>> onElement, boolean first) {
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			if (coll == null || !coll.belongs(value))
				return false;
			return ((ObservableCollection<E>) coll).forMutableElement(value, onElement, first);
		}

		@Override
		public <T> T ofElementAt(ElementId elementId, Function<? super ElementHandle<? extends E>, T> onElement) {
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			if (coll == null)
				throw new NoSuchElementException();
			return ((ObservableCollection<E>) coll).ofElementAt(elementId, onElement);
		}

		@Override
		public <T> T ofMutableElementAt(ElementId elementId, Function<? super MutableElementHandle<? extends E>, T> onElement) {
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			if (coll == null)
				throw new NoSuchElementException();
			return ((ObservableCollection<E>) coll).ofMutableElementAt(elementId, onElement);
		}

		@Override
		public <T> T ofElementAt(int index, Function<? super ElementHandle<? extends E>, T> onElement) {
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			if (coll == null)
				throw new NoSuchElementException();
			return ((ObservableCollection<E>) coll).ofElementAt(index, onElement);
		}

		@Override
		public <T> T ofMutableElementAt(int index, Function<? super MutableElementHandle<? extends E>, T> onElement) {
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			if (coll == null)
				throw new NoSuchElementException();
			return ((ObservableCollection<E>) coll).ofMutableElementAt(index, onElement);
		}

		@Override
		public MutableElementSpliterator<E> mutableSpliterator(boolean fromStart) {
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			if (coll == null)
				return MutableElementSpliterator.empty();
			return ((ObservableCollection<E>) coll).mutableSpliterator(fromStart);
		}

		@Override
		public MutableElementSpliterator<E> mutableSpliterator(int index) {
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			if (coll == null) {
				if (index == 0)
					return MutableElementSpliterator.empty();
				else
					throw new IndexOutOfBoundsException(index + " of 0");
			}
			return ((ObservableCollection<E>) coll).mutableSpliterator(index);
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
			return ObservableCollectionImpl.defaultOnChange(this, observer);
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
		public E get(int index) {
			int soFar = 0;
			try (Transaction t = theOuter.lock(false, null)) {
				for (ObservableCollection<? extends E> coll : theOuter) {
					try (Transaction innerT = coll.lock(false, null)) {
						int size = coll.size();
						if (index < soFar + size)
							return coll.get(index - soFar);
						soFar += size;
					}
				}
			}
			throw new IndexOutOfBoundsException(index + " of " + soFar);
		}

		@Override
		public String canAdd(E value) {
			String firstMsg = null;
			try (Transaction t = lock(true, null)) {
				for (ObservableCollection<? extends E> subColl : theOuter) {
					if (!subColl.belongs(value))
						continue;
					String msg = ((ObservableCollection<E>) subColl).canAdd(value);
					if (msg == null)
						return null;
					else if (firstMsg == null)
						firstMsg = msg;
				}
			}
			if (firstMsg == null)
				firstMsg = CollectionElement.StdMsg.UNSUPPORTED_OPERATION;
			return firstMsg;
		}

		@Override
		public boolean add(E e) {
			try (Transaction t = lock(true, null)) {
				for (ObservableCollection<? extends E> subColl : theOuter) {
					if (subColl.belongs(e) && ((ObservableCollection<E>) subColl).canAdd(e) == null)
						return ((ObservableCollection<E>) subColl).add(e);
				}
			}
			return false;
		}

		@Override
		public void clear() {
			for (ObservableCollection<? extends E> coll : theOuter)
				coll.clear();
		}

		@Override
		public boolean forObservableElement(E value, Consumer<? super ObservableCollectionElement<? extends E>> onElement, boolean first) {
			ObservableCollection<? extends ObservableCollection<? extends E>> outer = first ? getOuter() : getOuter().reverse();
			for (ObservableCollection<? extends E> c : outer)
				if (((ObservableCollection<E>) c).forObservableElement(value, onElement, first))
					return true;
			return false;
		}

		@Override
		public boolean forMutableElement(E value, Consumer<? super MutableObservableElement<? extends E>> onElement, boolean first) {
			ObservableCollection<? extends ObservableCollection<? extends E>> outer = first ? getOuter() : getOuter().reverse();
			for (ObservableCollection<? extends E> c : outer)
				if (((ObservableCollection<E>) c).forMutableElement(value, onElement, first))
					return true;
			return false;
		}

		@Override
		public <T> T ofElementAt(ElementId elementId, Function<? super ObservableCollectionElement<? extends E>, T> onElement) {
			CompoundId id = (CompoundId) elementId;
			return theOuter.ofElementAt(id.getOuter(), outerEl -> outerEl.get().ofElementAt(id.getInner(), onElement));
		}

		@Override
		public <T> T ofMutableElementAt(ElementId elementId, Function<? super MutableObservableElement<? extends E>, T> onElement) {
			CompoundId id = (CompoundId) elementId;
			return theOuter.ofElementAt(id.getOuter(), outerEl -> outerEl.get().ofMutableElementAt(id.getInner(), onElement));
		}

		@Override
		public <T> T ofElementAt(int index, Function<? super ObservableCollectionElement<? extends E>, T> onElement) {
			int soFar = 0;
			try (Transaction t = theOuter.lock(false, null)) {
				for (ObservableCollection<? extends E> coll : theOuter) {
					try (Transaction innerT = coll.lock(false, null)) {
						int size = coll.size();
						if (index < soFar + size)
							return coll.ofElementAt(index - soFar, onElement);
						soFar += size;
					}
				}
			}
			throw new IndexOutOfBoundsException(index + " of " + soFar);
		}

		@Override
		public <T> T ofMutableElementAt(int index, Function<? super MutableObservableElement<? extends E>, T> onElement) {
			int soFar = 0;
			try (Transaction t = theOuter.lock(false, null)) {
				for (ObservableCollection<? extends E> coll : theOuter) {
					try (Transaction innerT = coll.lock(true, null)) {
						int size = coll.size();
						if (index < soFar + size)
							return coll.ofMutableElementAt(index - soFar, onElement);
						soFar += size;
					}
				}
			}
			throw new IndexOutOfBoundsException(index + " of " + soFar);
		}

		@Override
		public MutableObservableSpliterator<E> mutableSpliterator(boolean fromStart) {
			class FlattenedSpliterator implements MutableObservableSpliterator<E> {}
			return new FlattenedSpliterator();
		}

		@Override
		public MutableObservableSpliterator<E> mutableSpliterator(int index) {
			// TODO Auto-generated method stub
		}

		@Override
		public boolean isEventIndexed() {
			// This method is used when the onChange method is called, so even if all the collections are indexed currently, others might be
			// added later that are not.
			return false;
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
			Map<ElementId, CollectionSubscription> subscriptions = new HashMap<>();
			Subscription outerSub = theOuter
				.onChange(new Consumer<ObservableCollectionEvent<? extends ObservableCollection<? extends E>>>() {
					@Override
					public void accept(ObservableCollectionEvent<? extends ObservableCollection<? extends E>> outerEvt) {
						switch (outerEvt.getType()) {
						case add:
							subscriptions.put(outerEvt.getElementId(), subscribeInner(outerEvt.getElementId(), outerEvt.getNewValue()));
							break;
						case remove:
							subscriptions.remove(outerEvt.getElementId()).unsubscribe(true);
							break;
						case set:
							if (outerEvt.getOldValue() != outerEvt.getNewValue()) {
								subscriptions.remove(outerEvt.getOldValue()).unsubscribe(true);
								subscriptions.put(outerEvt.getElementId(), subscribeInner(outerEvt.getElementId(), outerEvt.getNewValue()));
							}
							break;
						}
					}

					private CollectionSubscription subscribeInner(ElementId outerId, ObservableCollection<? extends E> innerColl) {
						if (innerColl == null)
							return removeAll -> {
							};
							else
								return innerColl.subscribe(innerEvt -> {
									observer.accept(new ObservableCollectionEvent<>(new CompoundId(outerId, innerEvt.getElementId()),
										innerEvt.getType(), innerEvt.getOldValue(), innerEvt.getNewValue(), innerEvt));
								});
					}
				});
			return () -> {
				outerSub.unsubscribe();
				for (Subscription sub : subscriptions.values())
					sub.unsubscribe();
				subscriptions.clear();
			};
		}

		@Override
		public int getElementsBefore(ElementId id) {
			CompoundId cId=(CompoundId) id;
			return getInnerElementsBefore(theOuter.getElementsBefore(cId.getOuter()) + cId.getInner().getElementsBefore();
		}

		@Override
		public int getElementsAfter(ElementId id) {
			return getInnerElementsAfter(theOuter.getElementsAfter()) + theInner.getElementsAfter();
		}

		int getInnerElementsBefore(int outerIndex) {
			if (outerIndex == 0)
				return 0;
			int count = 0;
			int oi = 0;
			for (ObservableCollection<? extends E> inner : theOuter) {
				if (oi == outerIndex)
					break;
				count += inner.size();
			}
			return count;
		}

		int getInnerElementsAfter(int outerIndex) {
			int count = 0;
			int oi = size() - 1;
			if (outerIndex == oi)
				return 0;
			for (ObservableCollection<? extends E> inner : theOuter.reverse()) {
				if (oi == outerIndex)
					break;
				count += inner.size();
			}
			return count;
		}

		protected class CompoundId implements ElementId {
			private final ElementId theOuter;
			private final ElementId theInner;

			public CompoundId(ElementId outer, ElementId inner) {
				theOuter = outer;
				theInner = inner;
			}

			protected ElementId getOuter() {
				return theOuter;
			}

			protected ElementId getInner() {
				return theInner;
			}

			@Override
			public int compareTo(ElementId o) {
				int comp = theOuter.compareTo(((CompoundId) o).theOuter);
				if (comp == 0)
					comp = theInner.compareTo(((CompoundId) o).theInner);
				return comp;
			}

			@Override
			public int hashCode() {
				return theOuter.hashCode() * 13 + theInner.hashCode();
			}

			@Override
			public boolean equals(Object obj) {
				return obj instanceof FlattenedObservableCollection.CompoundId && theOuter.equals(((CompoundId) obj).theOuter)
					&& theInner.equals(((CompoundId) obj).theInner);
			}

			@Override
			public String toString() {
				return theOuter + "(" + theInner + ")";
			}
		}
	}
}
