package org.observe.collect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
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
import org.qommons.ArrayUtils;
import org.qommons.Causable;
import org.qommons.ConcurrentHashSet;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.ElementSpliterator;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.collect.MutableElementSpliterator;
import org.qommons.tree.BetterTreeList;
import org.qommons.tree.BetterTreeSet;
import org.qommons.tree.BinaryTreeNode;

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

	public static abstract class AbstractObservableElementFinder<E> implements ObservableValue<CollectionElement<? extends E>> {
		private final ObservableCollection<E> theCollection;
		private final Comparator<CollectionElement<? extends E>> theElementCompare;
		private final TypeToken<CollectionElement<? extends E>> theType;

		/**
		 * @param collection The collection to find elements in
		 * @param elementCompare A comparator to determine whether to prefer one {@link #test(Object) matching} element over another. When
		 *        <code>elementCompare{@link Comparable#compareTo(Object) compareTo}(el1, el2)<0</code>, el1 will replace el2.
		 */
		public AbstractObservableElementFinder(ObservableCollection<E> collection,
			Comparator<CollectionElement<? extends E>> elementCompare) {
			theCollection = collection;
			theElementCompare = elementCompare;
			theType = new TypeToken<CollectionElement<? extends E>>() {}.where(new TypeParameter<E>() {}, collection.getType().wrap());
		}

		protected ObservableCollection<E> getCollection() {
			return theCollection;
		}

		@Override
		public TypeToken<CollectionElement<? extends E>> getType() {
			return theType;
		}

		@Override
		public boolean isSafe() {
			return true;
		}

		@Override
		public CollectionElement<? extends E> get() {
			CollectionElement<? extends E>[] element = new CollectionElement[1];
			find(el -> element[0] = new SimpleElement(el.getElementId(), el.get()));
			return element[0];
		}

		protected abstract boolean find(Consumer<? super CollectionElement<? extends E>> onElement);

		protected abstract boolean test(E value);

		@Override
		public Subscription subscribe(Observer<? super ObservableValueEvent<CollectionElement<? extends E>>> observer) {
			try (Transaction t = theCollection.lock(false, null)) {
				class FinderListener implements Consumer<ObservableCollectionEvent<? extends E>> {
					private SimpleElement theCurrentElement;

					@Override
					public void accept(ObservableCollectionEvent<? extends E> evt) {
						boolean mayReplace;
						if (theCurrentElement == null)
							mayReplace = true;
						else if (theCurrentElement.getElementId().equals(evt.getElementId()))
							mayReplace = true;
						else {
							mayReplace = theElementCompare.compare(theCurrentElement,
								new SimpleElement(evt.getElementId(), evt.getNewValue())) > 0;
						}
						if (!mayReplace)
							return; // Even if the new element's value matches, it wouldn't replace the current value
						boolean matches = test(evt.getNewValue());
						if (!matches && (theCurrentElement == null || !theCurrentElement.getElementId().equals(evt.getElementId())))
							return; // If the new value doesn't match and it's not the current element, we don't care

						// At this point we know that we will have to do something
						Map<Object, Object> causeData = evt.getRootCausable().onFinish(this, (cause, data) -> {
							SimpleElement oldElement = theCurrentElement;
							if (data.get("replacement") == null) {
								// Means we need to find the new value in the collection
								if (!find(el -> theCurrentElement = new SimpleElement(el.getElementId(), el.get())))
									theCurrentElement = null;
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
				observer.onNext(createInitialEvent(listener.theCurrentElement, null));
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

		private class SimpleElement implements CollectionElement<E> {
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
		private final boolean isFirst;

		/**
		 * @param collection The collection to find elements in
		 * @param test The test to find elements that pass
		 * @param first Whether to get the first value in the collection that passes or the last value
		 */
		protected ObservableCollectionFinder(ObservableCollection<E> collection, Predicate<? super E> test, boolean first) {
			super(collection, (el1, el2) -> {
				int compare = el1.getElementId().compareTo(el2.getElementId());
				if (!first)
					compare = -compare;
				return compare;
			});
			theTest = test;
			isFirst = first;
		}

		@Override
		protected boolean find(Consumer<? super CollectionElement<? extends E>> onElement) {
			return getCollection().find(theTest, onElement, isFirst);
		}

		@Override
		protected boolean test(E value) {
			return theTest.test(value);
		}
	}

	public static class ObservableEquivalentFinder<E> extends AbstractObservableElementFinder<E> {
		private final E theValue;
		private final boolean isFirst;

		public ObservableEquivalentFinder(ObservableCollection<E> collection, E value, boolean first) {
			super(collection, (el1, el2) -> {
				int compare = el1.getElementId().compareTo(el2.getElementId());
				if (!first)
					compare = -compare;
				return compare;
			});
			if (!collection.belongs(value))
				throw new IllegalArgumentException("Illegal value for collection: " + value);
			theValue = value;
			isFirst = first;
		}

		@Override
		protected boolean find(Consumer<? super CollectionElement<? extends E>> onElement) {
			return getCollection().forElement(theValue, onElement, isFirst);
		}

		@Override
		protected boolean test(E value) {
			return getCollection().equivalence().elementEquals(theValue, value);
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

	public static class ValueCount<E> {
		E value;
		int left;
		int right;

		public ValueCount(E val) {
			value = val;
		}

		public E getValue() {
			return value;
		}

		public int getLeftCount() {
			return left;
		}

		public int getRightCount() {
			return right;
		}

		public boolean isEmpty() {
			return left == 0 && right == 0;
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
		final Map<E, ValueCount<E>> rightCounts;
		final ReentrantLock theLock;
		private int leftCount;
		private int commonCount;
		private int rightCount;

		ValueCounts(Equivalence<? super E> leftEquiv) {
			this.leftEquiv = leftEquiv;
			leftCounts = leftEquiv.createMap();
			rightCounts = leftEquiv.createMap();
			theLock = new ReentrantLock();
		}

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

		public Subscription init(ObservableCollection<E> left, ObservableCollection<X> right, Observable<?> until, boolean weak) {
			theLock.lock();
			try (Transaction lt = left.lock(false, null); Transaction rt = right.lock(false, null)) {
				left.spliterator().forEachRemaining(e -> modify(e, true, true, null));
				right.spliterator().forEachRemaining(x -> {
					if (leftEquiv.isElement(x))
						modify((E) x, true, false, null);
				});

				Consumer<ObservableCollectionEvent<? extends E>> leftListener = evt -> onEvent(evt, true);
				Consumer<ObservableCollectionEvent<? extends X>> rightListener = evt -> onEvent(evt, false);
				if (weak) {
					WeakConsumer.WeakConsumerBuilder builder = WeakConsumer.build()//
						.withAction(leftListener, left::onChange)//
						.withAction(rightListener, right::onChange);
					if (until != null)
						builder.withUntil(until::act);
					return builder.build();
				} else {
					Subscription leftSub = left.onChange(leftListener);
					Subscription rightSub = right.onChange(rightListener);
					return Subscription.forAll(leftSub, rightSub);
				}
			} finally {
				theLock.unlock();
			}
		}

		private void onEvent(ObservableCollectionEvent<?> evt, boolean onLeft) {
			theLock.lock();
			try {
				switch (evt.getType()) {
				case add:
					if (onLeft || leftEquiv.isElement(evt.getNewValue()))
						modify((E) evt.getNewValue(), true, onLeft, evt);
					break;
				case remove:
					if (onLeft || leftEquiv.isElement(evt.getOldValue()))
						modify((E) evt.getOldValue(), false, onLeft, evt);
					break;
				case set:
					boolean oldApplies = onLeft || leftEquiv.isElement(evt.getOldValue());
					boolean newApplies = onLeft || leftEquiv.isElement(evt.getNewValue());
					if ((oldApplies != newApplies) || (oldApplies && !leftEquiv.elementEquals((E) evt.getOldValue(), evt.getNewValue()))) {
						if (oldApplies)
							modify((E) evt.getOldValue(), false, onLeft, evt);
						if (newApplies)
							modify((E) evt.getNewValue(), true, onLeft, evt);
					} else if (oldApplies)
						update(evt.getOldValue(), evt.getNewValue(), onLeft, evt);
				}
			} finally {
				theLock.unlock();
			}
		}

		private void modify(E value, boolean add, boolean onLeft, Causable cause) {
			ValueCount<E> count = leftCounts.get(value);
			if (count == null && rightCounts != null)
				count = rightCounts.get(value);
			if (count == null) {
				if (add)
					count = new ValueCount<>(value);
				else
					throw new IllegalStateException("Value not found: " + value + " on " + (onLeft ? "left" : "right"));
			}
			boolean containmentChange = count.modify(add, onLeft);
			if (containmentChange) {
				if (onLeft) {
					if (add) {
						leftCount++;
						leftCounts.put(value, count);
					} else {
						leftCount--;
						leftCounts.remove(value);
					}
				} else {
					if (add) {
						rightCount++;
						rightCounts.put(value, count);
					} else {
						rightCount--;
						rightCounts.remove(value);
					}
				}
			}
			Object oldValue = add ? null : value;
			if (cause != null)
				changed(count, oldValue, add ? CollectionChangeType.add : CollectionChangeType.remove, onLeft, containmentChange, cause);
		}

		private void update(Object oldValue, Object newValue, boolean onLeft, Causable cause) {
			ValueCount<?> count = leftCounts.get(oldValue);
			if (count == null && rightCounts != null)
				count = rightCounts.get(oldValue);
			if (count == null) {
				if (onLeft || rightCounts != null)
					throw new IllegalStateException("Value not found: " + oldValue + " on " + (onLeft ? "left" : "right"));
				else
					return; // Not of concern
			}
			if (onLeft && oldValue != newValue)
				((ValueCount<Object>) count).value = newValue;
			if (cause != null)
				changed(count, oldValue, CollectionChangeType.set, onLeft, false, cause);
		}

		protected abstract void changed(ValueCount<?> count, Object oldValue, CollectionChangeType type, boolean onLeft,
			boolean containmentChange, Causable cause);
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
		private final Predicate<ValueCounts<E, X>> theSatisfiedCheck;

		/**
		 * @param left The left collection
		 * @param right The right collection
		 * @param satisfied The test to determine this value after any changes
		 */
		public IntersectionValue(ObservableCollection<E> left, ObservableCollection<X> right, Predicate<ValueCounts<E, X>> satisfied) {
			theLeft = left;
			theRight = right;
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
			boolean[] initialized = new boolean[1];
			boolean[] satisfied = new boolean[1];
			ValueCounts<E, X> counts = new ValueCounts<E, X>(theLeft.equivalence()) {
				@Override
				protected void changed(ValueCount<?> count, Object oldValue, CollectionChangeType type, boolean onLeft,
					boolean containmentChange, Causable cause) {
					cause.getRootCausable().onFinish(this, (c, data) -> {
						boolean wasSatisfied = satisfied[0];
						satisfied[0] = theSatisfiedCheck.test(this);
						if (!initialized[0] && wasSatisfied != satisfied[0])
							fireChangeEvent(wasSatisfied, satisfied[0], cause, observer::onNext);
					});
				}
			};
			return counts.init(theLeft, theRight, null, false);
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
			super(collection, toCollection(value), counts -> counts.getCommonCount() > 0);
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
			super(left, right, counts -> counts.getRightCount() == 0);
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
			super(left, right, counts -> counts.getCommonCount() > 0);
		}

		@Override
		public Boolean get() {
			return getLeft().containsAny(getRight());
		}
	}

	public static class ReversedObservableCollection<E> extends BetterList.ReversedList<E> implements ObservableCollection<E> {
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
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
			return getWrapped().onChange(evt -> observer.accept(new ObservableCollectionEvent<>(evt.getElementId().reverse(), getType(),
				size() - evt.getIndex() - 1, evt.getType(), evt.getOldValue(), evt.getNewValue(), evt)));
		}

		@Override
		public ObservableCollection<E> reverse() {
			return getWrapped();
		}

		@Override
		public E[] toArray() {
			try (Transaction t = lock(false, null)) {
				return ObservableCollection.super.toArray();
			}
		}

		@Override
		public CollectionSubscription subscribe(Consumer<? super ObservableCollectionEvent<? extends E>> observer, boolean forward) {
			return getWrapped().subscribe(new ReversedSubscriber(observer), !forward);
		}

		private class ReversedSubscriber implements Consumer<ObservableCollectionEvent<? extends E>> {
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
				ObservableCollectionEvent.doWith(new ObservableCollectionEvent<>(evt.getElementId().reverse(), getType(), index,
					evt.getType(), evt.getOldValue(), evt.getNewValue(), evt), theObserver);
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
		public Transaction lock(boolean write, boolean structural, Object cause) {
			return theSource.lock(write, structural, cause);
		}

		@Override
		public long getStamp(boolean structuralOnly) {
			return theSource.getStamp(structuralOnly);
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
			if (!reversed.isAccepted())
				return reversed.getRejectReason();
			return theSource.canAdd(reversed.result);
		}

		@Override
		public CollectionElement<T> addElement(T e, boolean first) {
			FilterMapResult<T, E> reversed = theFlow.reverse(e);
			if (reversed.throwIfError(IllegalArgumentException::new) != null)
				return null;
			return elementFor(theSource.addElement(reversed.result, first));
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
		public CollectionElement<T> getElement(int index) {
			return elementFor(theSource.getElement(index));
		}

		@Override
		public CollectionElement<T> getElement(T value, boolean first) {
			CollectionElement<E> srcEl = theSource.getElement(theFlow.reverse(value).result, first);
			return srcEl == null ? null : elementFor(srcEl);
		}

		@Override
		public CollectionElement<T> getElement(ElementId id) {
			return elementFor(theSource.getElement(id));
		}

		@Override
		public <X> X ofMutableElement(ElementId element, Function<? super MutableCollectionElement<T>, X> onElement) {
			return theSource.ofMutableElement(element, el -> onElement.apply(mutableElementFor(el)));
		}

		@Override
		public MutableElementSpliterator<T> mutableSpliterator(ElementId element, boolean asNext) {
			return new DerivedMutableSpliterator(theSource.mutableSpliterator(element, asNext));
		}

		protected CollectionElement<T> elementFor(CollectionElement<? extends E> el) {
			return new CollectionElement<T>() {
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

		protected MutableCollectionElement<T> mutableElementFor(MutableCollectionElement<? extends E> el) {
			CollectionElementManager<E, ?, T>[] elMgr = new CollectionElementManager[1];
			theFlow.createElement(el.getElementId(), el.get(), null, mgr -> elMgr[0] = mgr);
			return elMgr[0].map(el, el.getElementId());
		}

		@Override
		public MutableElementSpliterator<T> mutableSpliterator(boolean fromStart) {
			MutableElementSpliterator<E> srcSpliter = theSource.mutableSpliterator(fromStart);
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
				observer.accept(
					new ObservableCollectionEvent<>(evt.getElementId(), getType(), evt.getIndex(), evt.getType(), oldValue, newValue, evt));
			});
		}

		protected class DerivedMutableSpliterator extends MutableElementSpliterator.SimpleMutableSpliterator<T> {
			private final MutableElementSpliterator<E> theSourceSpliter;

			DerivedMutableSpliterator(MutableElementSpliterator<E> srcSpliter) {
				super(DerivedLWCollection.this);
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
			protected boolean internalForElement(Consumer<? super CollectionElement<T>> action, boolean forward) {
				return theSourceSpliter.forElement(el -> action.accept(elementFor(el)), forward);
			}

			@Override
			protected boolean internalForElementM(Consumer<? super MutableCollectionElement<T>> action, boolean forward) {
				return theSourceSpliter.forElementM(el -> action.accept(mutableElementFor(el)), forward);
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

			protected DerivedCollectionElement(CollectionElementManager<E, ?, T> manager) {
				this.manager = manager;
			}

			@Override
			public boolean isPresent() {
				return presentNode.getElementId().isPresent();
			}

			@Override
			public int compareTo(ElementId o) {
				return presentNode.getElementId().compareTo(((DerivedCollectionElement<E, T>) o).presentNode.getElementId());
			}

			DerivedCollectionElement<E, T> check() {
				if (presentNode == null)
					throw new IllegalStateException("This node is not currentlly present in the collection");
				return this;
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
				Consumer<Consumer<MutableCollectionElement<? extends E>>> sourceElement) {
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
		private final Map<ElementId, List<DerivedCollectionElement<E, T>>> theElements;
		private final BetterTreeSet<DerivedCollectionElement<E, T>> thePresentElements;
		private final BetterTreeList<Consumer<? super ObservableCollectionEvent<? extends T>>> theListeners;
		private final AtomicInteger theListenerCount;
		private final Equivalence<? super T> theEquivalence;
		private final AtomicLong theModCount;
		private final AtomicLong theStructureStamp;
		@SuppressWarnings("unused")
		/** This reference is required so that the source listeners driving this collection are not GC'd */
		private final Subscription theWeakSubscription;

		public DerivedCollection(ObservableCollection<E> source, CollectionManager<E, ?, T> flow, Observable<?> until) {
			theSource = source;
			theFlow = flow;
			theElements = new java.util.TreeMap<>(ElementId::compareTo);
			thePresentElements = new BetterTreeSet<>(false, (e1, e2) -> e1.manager.compareTo(e2.manager));
			theListeners = new BetterTreeList<>(true);
			theListenerCount = new AtomicInteger();
			theEquivalence = flow.equivalence();
			theModCount = new AtomicLong();
			theStructureStamp = new AtomicLong();
			// Must maintain a strong reference to the event listener so it is not GC'd while the collection is still alive

			// Begin listening
			try (Transaction initialTransaction = lock(false, null)) {
				theFlow.begin(update -> {
					try (Transaction collT = theSource.lock(false, update.getCause())) {
						if (update.getElement() != null)
							applyUpdate(theElements.get(update.getElement()), update);
						else {
							for (List<DerivedCollectionElement<E, T>> element : theElements.values())
								applyUpdate(element, update);
						}
					}
					theFlow.postChange();
				}, until);

				// Need to hold on to the subscription because it contains strong references that keep the listeners alive
				theWeakSubscription = WeakConsumer.build()//
					.<ObservableCollectionEvent<? extends E>> withAction(evt -> {
						ElementId elementId = evt.getElementId();
						try (Transaction flowTransaction = theFlow.lock(false, null)) {
							switch (evt.getType()) {
							case add:
								theFlow.createElement(evt.getElementId(), evt.getNewValue(), evt, elMgr -> {
									final DerivedCollectionElement<E, T> element = createElement(elMgr);
									if (element.manager == null)
										return; // Statically filtered out
									theElements.computeIfAbsent(elementId, id -> new ArrayList<>()).add(element);
									if (element.manager.isPresent())
										addToPresent(element, evt);
								});
								break;
							case remove:
								List<DerivedCollectionElement<E, T>> elements = theElements.remove(elementId);
								if (elements == null)
									return; // Must be statically filtered out or removed via spliterator
								for (DerivedCollectionElement<E, T> element : elements) {
									if (element.presentNode != null)
										removeFromPresent(element, element.get(), evt);
									element.removed(evt);
								}
								break;
							case set:
								elements = theElements.get(elementId);
								if (elements == null)
									return; // Must be statically filtered out
								for (DerivedCollectionElement<E, T> element : elements) {
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
								}
								break;
							}
							theFlow.postChange();
						}
					}, action -> theSource.subscribe(action, true).removeAll())//
					.withUntil(until::act).build();
			}
		}

		protected DerivedCollectionElement<E, T> createElement(CollectionElementManager<E, ?, T> elMgr) {
			return new DerivedCollectionElement<>(elMgr);
		}

		protected ObservableCollection<E> getSource() {
			return theSource;
		}

		protected CollectionManager<E, ?, T> getFlow() {
			return theFlow;
		}

		protected List<DerivedCollectionElement<E, T>> getPresentElements(ElementId sourceId) {
			return theElements.get(sourceId).stream().filter(el -> el.isPresent()).collect(Collectors.toList());
		}

		protected BetterTreeSet<DerivedCollectionElement<E, T>> getPresentElements() {
			return thePresentElements;
		}

		private void addToPresent(DerivedCollectionElement<E, T> element, Object cause) {
			element.presentNode = thePresentElements.addElement(element, false);
			theModCount.incrementAndGet();
			theStructureStamp.incrementAndGet();
			fireListeners(new ObservableCollectionEvent<>(element, getType(), element.presentNode.getNodesBefore(),
				CollectionChangeType.add, null, element.get(), cause));
		}

		private void removeFromPresent(DerivedCollectionElement<E, T> element, T oldValue, Object cause) {
			thePresentElements.forMutableElement(element.presentNode.getElementId(), el -> el.remove());
			theModCount.incrementAndGet();
			theStructureStamp.incrementAndGet();
			fireListeners(new ObservableCollectionEvent<>(element, getType(), element.presentNode.getNodesBefore(),
				CollectionChangeType.remove, oldValue, oldValue, cause));
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
			} else if (oldValue != element.get()) {
				theModCount.incrementAndGet();
				fireListeners(new ObservableCollectionEvent<>(element, getType(), element.presentNode.getNodesBefore(),
					CollectionChangeType.set, oldValue, element.get(), cause));
			} else if (fireUpdate) {
				theModCount.incrementAndGet();
				fireListeners(new ObservableCollectionEvent<>(element, getType(), element.presentNode.getNodesBefore(),
					CollectionChangeType.set, oldValue, oldValue, cause));
			}
		}

		private void fireListeners(ObservableCollectionEvent<T> event) {
			for (Consumer<? super ObservableCollectionEvent<? extends T>> listener : theListeners)
				listener.accept(event);
		}

		private void applyUpdate(List<DerivedCollectionElement<E, T>> elements, CollectionUpdate update) {
			for (DerivedCollectionElement<E, T> element : elements) {
				boolean prePresent = element.manager.isPresent();
				T oldValue = prePresent ? element.get() : null;
				ElementUpdateResult fireUpdate = element.update(update,
					onEl -> theSource.forMutableElement(element.manager.getElementId(), onEl));
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
							new ObservableCollectionEvent<>(element, getType(), index++, CollectionChangeType.add, null, element.get(), c));
					}
				});
				Subscription changeSub = onChange(observer);
				return removeAll -> {
					if (!removeAll) {
						changeSub.unsubscribe();
						return;
					}
					try (Transaction closeT = lock(false, null)) {
						changeSub.unsubscribe();
						SubscriptionCause.doWith(new SubscriptionCause(), c -> {
							int index = 0;
							for (DerivedCollectionElement<E, T> element : thePresentElements) {
								observer.accept(new ObservableCollectionEvent<>(element, getType(), index++, CollectionChangeType.remove,
									element.get(), element.get(), c));
							}
						});
					}
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
		public Transaction lock(boolean write, boolean structural, Object cause) {
			Transaction flowSub = theFlow.lock(write, cause);
			Transaction collSub = theSource.lock(write, structural, cause);
			return () -> {
				collSub.close();
				flowSub.close();
			};
		}

		@Override
		public long getStamp(boolean structuralOnly) {
			return structuralOnly ? theStructureStamp.get() : theModCount.get();
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

		@Override
		public CollectionElement<T> getElement(int index) {
			return observableElementFor(thePresentElements.get(index));
		}

		@Override
		public CollectionElement<T> getElement(T value, boolean first) {
			try (Transaction t = lock(false, null)) {
				ObservableSetImpl.UniqueElementFinder<T> finder = getFlow().getElementFinder();
				if (finder != null) {
					ElementId id = finder.getUniqueElement(value);
					if (id == null)
						return null;
					List<DerivedCollectionElement<E, T>> elements = theElements.get(id);
					if (elements == null)
						throw new IllegalStateException(MutableCollectionElement.StdMsg.NOT_FOUND);
					if(!first){
						elements = new ArrayList<>(elements);
						java.util.Collections.reverse(elements);
					}
					for (DerivedCollectionElement<E, T> element : elements) {
						if (element.presentNode != null)
							return observableElementFor(element);
					}
					return null;
				}
				for (DerivedCollectionElement<E, T> el : (first ? thePresentElements : thePresentElements.reverse()))
					if (equivalence().elementEquals(el.get(), value))
						return observableElementFor(el);
				return null;
			}
		}

		@Override
		public CollectionElement<T> getElement(ElementId id) {
			return observableElementFor((DerivedCollectionElement<E, T>) id);
		}

		@Override
		public <X> X ofMutableElement(ElementId element, Function<? super MutableCollectionElement<T>, X> onElement) {
			DerivedCollectionElement<E, T> el = (DerivedCollectionElement<E, T>) element;
			return theSource.ofMutableElement(el.check().manager.getElementId(), srcEl -> onElement.apply(el.manager.map(srcEl, el)));
		}

		@Override
		public MutableElementSpliterator<T> mutableSpliterator(ElementId element, boolean asNext) {
			DerivedCollectionElement<E, T> el = (DerivedCollectionElement<E, T>) element;
			return new MutableDerivedSpliterator(thePresentElements.spliterator(el.check().presentNode.getElementId(), asNext));
		}

		protected CollectionElement<T> observableElementFor(DerivedCollectionElement<E, T> el) {
			el.check();
			return new CollectionElement<T>() {
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
		public MutableElementSpliterator<T> mutableSpliterator(boolean fromStart) {
			return new MutableDerivedSpliterator(thePresentElements.spliterator(fromStart));
		}

		protected MutableElementSpliterator<T> spliterator(ElementSpliterator<DerivedCollectionElement<E, T>> elementSpliter) {
			return new MutableDerivedSpliterator(elementSpliter);
		}

		@Override
		public String canAdd(T value) {
			if (!theFlow.isReversible())
				return MutableCollectionElement.StdMsg.UNSUPPORTED_OPERATION;
			else if (!belongs(value))
				return MutableCollectionElement.StdMsg.BAD_TYPE;
			try (Transaction t = lock(false, null)) {
				FilterMapResult<T, E> reversed = theFlow.canAdd(new FilterMapResult<>(value));
				if (!reversed.isAccepted())
					return reversed.getRejectReason();
				return theSource.canAdd(reversed.result);
			}
		}

		@Override
		public CollectionElement<T> addElement(T e, boolean first) {
			if (!theFlow.isReversible())
				throw new UnsupportedOperationException(MutableCollectionElement.StdMsg.UNSUPPORTED_OPERATION);
			else if (!belongs(e))
				throw new IllegalArgumentException(MutableCollectionElement.StdMsg.BAD_TYPE);
			try (Transaction t = lock(true, null)) {
				FilterMapResult<T, E> reversed = theFlow.canAdd(new FilterMapResult<>(e));
				if (reversed.throwIfError(IllegalArgumentException::new) != null)
					return null;
				CollectionElement<E> sourceElement = theSource.addElement(reversed.result, first);
				List<DerivedCollectionElement<E, T>> elements = theElements.get(sourceElement.getElementId());
				return observableElementFor(elements.get(elements.size() - 1)); // Should have been added
			}
		}

		@Override
		public void clear() {
			try (Transaction t = lock(true, null)) {
				if (!theFlow.isFiltered())
					theSource.clear();
				else
					mutableSpliterator().forEachElementM(el -> el.remove(), true);
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

		private class MutableDerivedSpliterator extends MutableElementSpliterator.SimpleMutableSpliterator<T> {
			private final ElementSpliterator<DerivedCollectionElement<E, T>> theElementSpliter;

			MutableDerivedSpliterator(ElementSpliterator<DerivedCollectionElement<E, T>> elementSpliter) {
				super(DerivedCollection.this);
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
			protected boolean internalForElement(Consumer<? super CollectionElement<T>> action, boolean forward) {
				return theElementSpliter.forValue(element -> action.accept(observableElementFor(element)), forward);
			}

			@Override
			protected boolean internalForElementM(Consumer<? super MutableCollectionElement<T>> action, boolean forward) {
				return theElementSpliter.forValue(element -> theSource.forMutableElement(element.manager.getElementId(),
					sourceEl -> action.accept(element.manager.map(sourceEl, element))), forward);
			}

			@Override
			public MutableElementSpliterator<T> trySplit() {
				ElementSpliterator<DerivedCollectionElement<E, T>> split = theElementSpliter.trySplit();
				return split == null ? null : new MutableDerivedSpliterator(split);
			}
		}
	}

	public static <E> ObservableCollection<E> create(TypeToken<E> type, boolean threadSafe, Collection<? extends E> initialValues) {
		DefaultObservableCollection<E> collection = new DefaultObservableCollection<>(type, new BetterTreeList<>(threadSafe));
		if (initialValues != null)
			collection.addAll(initialValues);
		return collection;
	}

	/**
	 * <p>
	 * This collection is a base class for gimped collections that rely on {@link DerivedCollection} for almost all their functionality and
	 * must be used as the base of a {@link CollectionDataFlow#isLightWeight() heavy-weight} data flow. Such collections should never be
	 * used, except as a source for DerivedCollection.
	 * </p>
	 *
	 * @param <E> The type of the collection
	 */
	protected static abstract class HeavyFlowOnlyCollection<E> implements ObservableCollection<E> {
		/**
		 * Returns {@link Equivalence#DEFAULT} unless overridden by a subclass
		 *
		 * @see org.observe.collect.ObservableCollection#equivalence()
		 */
		@Override
		public Equivalence<? super E> equivalence() {
			return Equivalence.DEFAULT;
		}

		protected <T> T illegalAccess() {
			throw new UnsupportedOperationException("This method is not implemented for this collection type.\n"
				+ "This collection type should only be used as a base for a CollectionDataFlow.  It seems this is not currently the case.");
		}

		@Override
		public int size() {
			return illegalAccess();
		}

		@Override
		public boolean isEmpty() {
			return illegalAccess();
		}

		@Override
		public CollectionElement<E> getElement(int index) {
			return illegalAccess();
		}

		@Override
		public CollectionElement<E> getElement(E value, boolean first) {
			return illegalAccess();
		}

		@Override
		public <T> CollectionDataFlow<E, E, E> flow() {
			// Overridden because by default, ObservableCollection.flow().collect() just returns the collection
			return new HeavyOnlyCollectionFlow<>(this);
		}

		private static class HeavyOnlyCollectionFlow<E> extends BaseCollectionDataFlow<E> {
			HeavyOnlyCollectionFlow(ObservableCollection<E> source) {
				super(source);
			}

			@Override
			public boolean isLightWeight() {
				return false;
			}

			@Override
			public AbstractCollectionManager<E, ?, E> manageCollection() {
				return new BaseCollectionManager<>(getTargetType(), getSource().equivalence(), getSource().isLockSupported());
			}

			@Override
			public ObservableCollection<E> collect(Observable<?> until) {
				return new DerivedCollection<>(getSource(), manageCollection(), until);
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
		public Transaction lock(boolean write, boolean structural, Object cause) {
			Lock lock = write ? theLock.writeLock() : theLock.readLock();
			lock.lock();
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			Transaction t = coll == null ? Transaction.NONE : coll.lock(write, structural, cause);
			return () -> {
				t.close();
				lock.unlock();
			};
		}

		@Override
		public long getStamp(boolean structuralOnly) {
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			return coll == null ? -1 : coll.getStamp(structuralOnly);
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
		public CollectionElement<E> getElement(int index) {
			ObservableCollection<? extends E> current = getWrapped().get();
			if (current == null)
				throw new IndexOutOfBoundsException(index + " of 0");
			return ((ObservableCollection<E>) current).getElement(index);
		}

		@Override
		public CollectionElement<E> getElement(E value, boolean first) {
			ObservableCollection<? extends E> current = getWrapped().get();
			if (current == null)
				return null;
			return ((ObservableCollection<E>) current).getElement(value, first);
		}

		@Override
		public CollectionElement<E> getElement(ElementId id) {
			ObservableCollection<? extends E> current = getWrapped().get();
			if (current == null)
				throw new IllegalArgumentException(StdMsg.NOT_FOUND);
			return ((ObservableCollection<E>) current).getElement(id);
		}

		@Override
		public <X> X ofMutableElement(ElementId element, Function<? super MutableCollectionElement<E>, X> onElement) {
			ObservableCollection<? extends E> current = getWrapped().get();
			if (current == null)
				throw new IllegalArgumentException(StdMsg.NOT_FOUND);
			return ((ObservableCollection<E>) current).ofMutableElement(element, onElement);
		}

		@Override
		public String canAdd(E value) {
			ObservableCollection<? extends E> current = theCollectionObservable.get();
			if (current == null)
				return MutableCollectionElement.StdMsg.UNSUPPORTED_OPERATION;
			else if (value != null && !current.getType().getRawType().isInstance(value))
				return MutableCollectionElement.StdMsg.BAD_TYPE;
			return ((ObservableCollection<E>) current).canAdd(value);
		}

		@Override
		public CollectionElement<E> addElement(E e, boolean first) {
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			if (coll == null)
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			if (e != null && !coll.getType().getRawType().isInstance(e))
				throw new IllegalArgumentException(MutableCollectionElement.StdMsg.BAD_TYPE);
			return ((ObservableCollection<E>) coll).addElement(e, first);
		}

		@Override
		public void clear() {
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			if (coll != null)
				coll.clear();
		}

		@Override
		public MutableElementSpliterator<E> mutableSpliterator(boolean fromStart) {
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			if (coll == null)
				return MutableElementSpliterator.empty();
			return ((ObservableCollection<E>) coll).mutableSpliterator(fromStart);
		}

		@Override
		public MutableElementSpliterator<E> mutableSpliterator(ElementId id, boolean asNext) {
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			if (coll == null)
				throw new IllegalArgumentException(StdMsg.NOT_FOUND);
			return ((ObservableCollection<E>) coll).mutableSpliterator(id, asNext);
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

	public static <E> CollectionDataFlow<E, E, E> flatten(ObservableCollection<? extends ObservableCollection<? extends E>> collections) {
		return new FlattenedObservableCollection<E>(collections).flow();
	}

	private static class FlattenedObservableCollection<E> extends HeavyFlowOnlyCollection<E> {
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
		public Transaction lock(boolean write, boolean structural, Object cause) {
			Transaction outerLock = theOuter.lock(write, false, cause);
			Transaction[] innerLocks = new Transaction[theOuter.size()];
			int i = 0;
			for (ObservableCollection<?> c : theOuter)
				innerLocks[i++] = c.lock(write, structural, cause);
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
		public long getStamp(boolean structuralOnly) {
			// This method technically does not provide the guarantee of its contract, since it is a hash.
			// This is unavoidable since there is more information than can be represented in a single long value.
			long[] stamp = new long[] { theOuter.getStamp(structuralOnly) };
			theOuter.spliterator().forEachRemaining(coll -> stamp[0] = stamp[0] * 17 + (coll == null ? 0 : coll.getStamp(structuralOnly)));
			return stamp[0];
		}

		@Override
		public TypeToken<E> getType() {
			return theType;
		}

		@Override
		public int getElementsBefore(ElementId id) {
			if (!(id instanceof FlattenedObservableCollection.CompoundId))
				throw new IllegalArgumentException(StdMsg.NOT_FOUND);
			CompoundId compound = (CompoundId) id;
			try (Transaction t = lock(false, null)) {
				ElementSpliterator<? extends ObservableCollection<? extends E>> outerSplit = theOuter.spliterator();
				boolean[] found = new boolean[1];
				int[] soFar = new int[1];
				while (!found[0] && outerSplit.forElement(el -> {
					if (el.getElementId().equals(compound.getOuter())) {
						soFar[0] += el.get().getElementsBefore(compound.getInner());
						found[0] = true;
					} else
						soFar[0] += el.get().size();
				}, true)) {
				}
				if (!found[0])
					throw new IllegalArgumentException(StdMsg.NOT_FOUND);
				return soFar[0];
			}
		}

		@Override
		public int getElementsAfter(ElementId id) {
			if (!(id instanceof FlattenedObservableCollection.CompoundId))
				throw new IllegalArgumentException(StdMsg.NOT_FOUND);
			CompoundId compound = (CompoundId) id;
			try (Transaction t = lock(false, null)) {
				ElementSpliterator<? extends ObservableCollection<? extends E>> outerSplit = theOuter.spliterator(false);
				boolean[] found = new boolean[1];
				int[] soFar = new int[1];
				while (!found[0] && outerSplit.forElement(el -> {
					if (el.getElementId().equals(compound.getOuter())) {
						soFar[0] += el.get().getElementsAfter(compound.getInner());
						found[0] = true;
					} else
						soFar[0] += el.get().size();
				}, false)) {
				}
				if (!found[0])
					throw new IllegalArgumentException(StdMsg.NOT_FOUND);
				return soFar[0];
			}
		}

		@Override
		public String canAdd(E value) {
			String firstMsg = null;
			try (Transaction t = lock(true, null)) {
				for (ObservableCollection<? extends E> subColl : theOuter.reverse()) {
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
				firstMsg = StdMsg.UNSUPPORTED_OPERATION;
			return firstMsg;
		}

		@Override
		public CollectionElement<E> addElement(E e, boolean first) {
			String[] msg = new String[1];
			boolean[] hasInner = new boolean[1];
			CollectionElement<E>[] element = new CollectionElement[1];
			try (Transaction t = lock(true, null)) {
				ElementSpliterator<? extends ObservableCollection<? extends E>> outerSplit = theOuter.spliterator(first);
				while (element[0] == null && outerSplit.forElement(el -> {
					hasInner[0] = true;
					if (!el.get().belongs(e))
						return;
					msg[0] = ((ObservableCollection<E>) el.get()).canAdd(e);
					if (msg[0] == null)
						element[0] = elementFor(el.getElementId(), ((ObservableCollection<E>) el.get()).addElement(e, first));
				}, first)) {
				}
			}
			if (msg[0] == null) {
				if (hasInner[0])
					msg[0] = StdMsg.ILLEGAL_ELEMENT;
				else
					throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			}
			throw new IllegalArgumentException(msg[0]);
		}

		@Override
		public void clear() {
			for (ObservableCollection<? extends E> coll : theOuter)
				coll.clear();
		}

		private CollectionElement<E> elementFor(ElementId outerId, CollectionElement<? extends E> innerEl) {
			return new CollectionElement<E>() {
				@Override
				public ElementId getElementId() {
					return new CompoundId(outerId, innerEl.getElementId());
				}

				@Override
				public E get() {
					return innerEl.get();
				}
			};
		}

		private MutableCollectionElement<E> mutableElementFor(ElementId outerId, ObservableCollection<? extends E> innerCollection,
			MutableCollectionElement<? extends E> innerEl) {
			return new MutableCollectionElement<E>() {
				@Override
				public ElementId getElementId() {
					return new CompoundId(outerId, innerEl.getElementId());
				}

				@Override
				public E get() {
					return innerEl.get();
				}

				@Override
				public String isEnabled() {
					return innerEl.isEnabled();
				}

				@Override
				public String isAcceptable(E value) {
					if (!innerCollection.belongs(value))
						return StdMsg.BAD_TYPE;
					return ((MutableCollectionElement<E>) innerEl).isAcceptable(value);
				}

				@Override
				public void set(E value) throws UnsupportedOperationException, IllegalArgumentException {
					String msg = isAcceptable(value);
					if (msg != null)
						throw new IllegalArgumentException(msg);
					((MutableCollectionElement<E>) innerEl).set(value);
				}

				@Override
				public String canRemove() {
					return innerEl.canRemove();
				}

				@Override
				public void remove() throws UnsupportedOperationException {
					innerEl.remove();
				}

				@Override
				public String canAdd(E value, boolean before) {
					if (!innerCollection.belongs(value))
						return StdMsg.BAD_TYPE;
					return ((MutableCollectionElement<E>) innerEl).canAdd(value, before);
				}

				@Override
				public ElementId add(E value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
					String msg = canAdd(value, before);
					if (msg != null)
						throw new IllegalArgumentException(msg);
					return new CompoundId(outerId, ((MutableCollectionElement<E>) innerEl).add(value, before));
				}
			};
		}

		@Override
		public <T> T ofMutableElement(ElementId elementId, Function<? super MutableCollectionElement<E>, T> onElement) {
			if (!(elementId instanceof FlattenedObservableCollection.CompoundId))
				throw new IllegalArgumentException(StdMsg.NOT_FOUND);
			CompoundId compound = (CompoundId) elementId;
			ObservableCollection<? extends E> innerColl = theOuter.getElement(compound.getOuter()).get();
			return innerColl.ofMutableElement(compound.getInner(),
				el -> onElement.apply(mutableElementFor(compound.getOuter(), innerColl, el)));
		}

		@Override
		public CollectionElement<E> getElement(ElementId id) {
			CompoundId compound = (CompoundId) id;
			return elementFor(compound.getOuter(), theOuter.getElement(compound.getOuter()).get().getElement(compound.getInner()));
		}

		@Override
		public MutableElementSpliterator<E> mutableSpliterator(ElementId element, boolean asNext) {
			CompoundId compound = (CompoundId) element;
			MutableElementSpliterator<? extends ObservableCollection<? extends E>> outerSplit = theOuter
				.mutableSpliterator(compound.getOuter(), true);
			ObservableCollection<? extends E> innerColl = theOuter.getElement(compound.getOuter()).get();
			MutableElementSpliterator<? extends E> innerSpliter = innerColl.mutableSpliterator(compound.getInner(), asNext);
			return new FlattenedSpliterator(outerSplit, compound.getOuter(), innerColl, innerSpliter, false);
		}

		@Override
		public MutableElementSpliterator<E> mutableSpliterator(boolean fromStart) {
			return new FlattenedSpliterator(theOuter.mutableSpliterator(), null, null, null, false);
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
			Map<ElementId, Subscription> subscriptions = new HashMap<>();
			Subscription outerSub = theOuter
				.onChange(new Consumer<ObservableCollectionEvent<? extends ObservableCollection<? extends E>>>() {
					@Override
					public void accept(ObservableCollectionEvent<? extends ObservableCollection<? extends E>> outerEvt) {
						try (Transaction t = lock(false, null)) {
							int index = getInnerElementsBefore(outerEvt.getElementId());
							switch (outerEvt.getType()) {
							case add:
								addElements(outerEvt, index);
								break;
							case remove:
								removeElements(outerEvt, index);
								break;
							case set:
								if (outerEvt.getOldValue() != outerEvt.getNewValue()) {
									removeElements(outerEvt, index);
									addElements(outerEvt, index);
								}
								break;
							}
						}
					}

					private int getInnerElementsBefore(ElementId outerId) {
						int[] index = new int[1];
						boolean[] found = new boolean[1];
						ElementSpliterator<? extends ObservableCollection<? extends E>> outerSplit = theOuter.spliterator();
						while (!found[0] && outerSplit.forElement(el -> {
							if (el.getElementId().compareTo(outerId) < 0)
								index[0] += el.get().size();
							else
								found[0] = true;
						}, true)) {
						}
						if (!found[0])
							throw new IllegalStateException(StdMsg.NOT_FOUND);
						return index[0];
					}

					private void addElements(ObservableCollectionEvent<? extends ObservableCollection<? extends E>> outerEvt, int index) {
						if (outerEvt.getNewValue() == null) {
							subscriptions.put(outerEvt.getElementId(), () -> {
							});
							return;
						}
						subscriptions.put(outerEvt.getElementId(), outerEvt.getNewValue().onChange(innerEvt -> {
							try (Transaction t = lock(false, null)) {
								CompoundId id = new CompoundId(outerEvt.getElementId(), innerEvt.getElementId());
								int innerIndex = getInnerElementsBefore(outerEvt.getElementId()) + innerEvt.getIndex();
								observer.accept(new ObservableCollectionEvent<>(id, getType(), innerIndex, innerEvt.getType(),
									innerEvt.getOldValue(), innerEvt.getNewValue(), innerEvt));
							}
						}));
					}

					private void removeElements(ObservableCollectionEvent<? extends ObservableCollection<? extends E>> outerEvt,
						int index) {
						subscriptions.remove(outerEvt.getElementId()).unsubscribe();
						if (outerEvt.getOldValue() == null)
							return;
						outerEvt.getOldValue().spliterator()
						.forEachElement(el -> observer.accept(//
							new ObservableCollectionEvent<>(new CompoundId(outerEvt.getElementId(), el.getElementId()), getType(),
								index, CollectionChangeType.remove, el.get(), el.get(), outerEvt)),
							true);
					}
				});
			return () -> {
				outerSub.unsubscribe();
				for (Subscription sub : subscriptions.values())
					sub.unsubscribe();
				subscriptions.clear();
			};
		}

		protected static class CompoundId implements ElementId {
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
			public boolean isPresent() {
				return theOuter.isPresent() && theInner.isPresent();
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

		class FlattenedSpliterator extends MutableElementSpliterator.SimpleMutableSpliterator<E> {
			private final MutableElementSpliterator<? extends ObservableCollection<? extends E>> theOuterSpliter;
			private ElementId theOuterId;
			private ObservableCollection<? extends E> theInnerCollection;
			private MutableElementSpliterator<? extends E> theInnerSpliter;
			private final boolean isInnerSplit;

			public FlattenedSpliterator(MutableElementSpliterator<? extends ObservableCollection<? extends E>> outerSpliter,
				ElementId outerId, ObservableCollection<? extends E> innerCollection, MutableElementSpliterator<? extends E> innerSpliter,
				boolean innerSplit) {
				super(FlattenedObservableCollection.this);
				theOuterSpliter = outerSpliter;
				theOuterId = outerId;
				theInnerCollection = innerCollection;
				theInnerSpliter = innerSpliter;
				isInnerSplit = innerSplit;
			}

			@Override
			public long estimateSize() {
				return 0;
			}

			@Override
			public int characteristics() {
				return ORDERED;
			}

			@Override
			protected boolean internalForElement(Consumer<? super CollectionElement<E>> action, boolean forward) {
				while (true) {
					if (theInnerSpliter != null
						&& theInnerSpliter.forElement(innerEl -> action.accept(elementFor(theOuterId, innerEl)), forward))
						return true;
					if (!nextInnerCollection(forward))
						return false;
				}
			}

			@Override
			protected boolean internalForElementM(Consumer<? super MutableCollectionElement<E>> action, boolean forward) {
				while (true) {
					if (theInnerSpliter != null && theInnerSpliter
						.forElementM(innerEl -> action.accept(mutableElementFor(theOuterId, theInnerCollection, innerEl)), forward))
						return true;
					if (!nextInnerCollection(forward))
						return false;
				}
			}

			private boolean nextInnerCollection(boolean forward) {
				if (isInnerSplit) {
					// If this spliterator's inner spliterator is from an inner split, then advancing to the next inner collection
					// would iterate over duplicate data
					return false;
				}
				return theOuterSpliter.forElement(outerEl -> {
					theOuterId = outerEl.getElementId();
					theInnerCollection = outerEl.get();
					theInnerSpliter = theInnerCollection == null ? null : theInnerCollection.mutableSpliterator(true);
				}, forward);
			}

			@Override
			public MutableElementSpliterator<E> trySplit() {
				try (Transaction t = lock(false, null)) {
					MutableElementSpliterator<? extends ObservableCollection<? extends E>> outerSplit = theOuterSpliter.trySplit();
					if (theOuterSpliter != null)
						return new FlattenedSpliterator(outerSplit, null, null, null, false);
					if (theInnerSpliter == null) {
						Consumer<Object> nothing = v -> {
						};
						// Grab the next or previous inner spliterator
						if (forElement(nothing, true))
							forElement(nothing, false);
						else if (forElement(nothing, false))
							forElement(nothing, true);
						// Should have an inner spliterator now if there is a non-empty one
						if (theInnerSpliter == null)
							return null;
					}
					MutableElementSpliterator<? extends E> innerSplit = theInnerSpliter.trySplit();
					if (innerSplit != null)
						return new FlattenedSpliterator(theOuterSpliter, theOuterId, theInnerCollection, innerSplit, true);
					return null;
				}
			}
		}
	}
}
