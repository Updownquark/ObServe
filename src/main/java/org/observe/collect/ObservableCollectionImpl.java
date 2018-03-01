package org.observe.collect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.Spliterator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
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
import org.observe.collect.ObservableCollectionDataFlowImpl.ActiveCollectionManager;
import org.observe.collect.ObservableCollectionDataFlowImpl.CollectionElementListener;
import org.observe.collect.ObservableCollectionDataFlowImpl.DerivedCollectionElement;
import org.observe.collect.ObservableCollectionDataFlowImpl.ElementAccepter;
import org.observe.collect.ObservableCollectionDataFlowImpl.FilterMapResult;
import org.observe.collect.ObservableCollectionDataFlowImpl.PassiveCollectionManager;
import org.observe.util.WeakListening;
import org.qommons.ArrayUtils;
import org.qommons.BiTuple;
import org.qommons.Causable;
import org.qommons.Causable.CausableKey;
import org.qommons.ConcurrentHashSet;
import org.qommons.Ternian;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.ValueHolder;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterSortedSet.SortedSearchFilter;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.ListenerList;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.collect.MutableElementSpliterator;
import org.qommons.tree.BetterTreeSet;
import org.qommons.tree.BinaryTreeNode;

import com.google.common.reflect.TypeToken;

/** Holds default implementation methods and classes for {@link ObservableCollection} */
public final class ObservableCollectionImpl {
	private ObservableCollectionImpl() {}

	/** Cached TypeToken of {@link String} */
	public static final TypeToken<String> STRING_TYPE = TypeToken.of(String.class);

	/**
	 * @param <E> The type for the set
	 * @param collection The collection to create the value set for (whose {@link ObservableCollection#equivalence() equivalence} will be
	 *        used)
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

			@Override
			public String toString() {
				StringBuilder str = new StringBuilder(type.toString()).append('\n');
				for (ChangeValue<E> el : elements)
					str.append('\t').append(el).append('\n');
				if (str.length() > 0)
					str.deleteCharAt(str.length() - 1);
				return str.toString();
			}
		}

		private static class ChangeValue<E> {
			E newValue;
			final E oldValue;
			int index;

			ChangeValue(E oldValue, E newValue, int index) {
				this.oldValue = oldValue;
				this.newValue = newValue;
				this.index = index;
			}

			@Override
			public String toString() {
				return new StringBuilder().append(index).append(':').append(oldValue).append('/').append(newValue).toString();
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
			Causable.CausableKey key = Causable.key((cause, data) -> {
				fireEventsFromSessionData((SessionChangeTracker<E>) data.get(SESSION_TRACKER_PROPERTY), cause, observer);
				data.remove(SESSION_TRACKER_PROPERTY);
			});
			return collection.onChange(evt -> {
				evt.getRootCausable().onFinish(key).compute(SESSION_TRACKER_PROPERTY,
					(k, tracker) -> accumulate((SessionChangeTracker<E>) tracker, evt, observer));
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
			int collIndex = event.getIndex();
			if (tracker == null)
				return replace(tracker, event, observer);
			int changeIndex;
			switch (tracker.type) {
			case add:
				switch (event.getType()) {
				case add:
					tracker = insertAddition(tracker, event);
					break;
				case remove:
					changeIndex = indexForAdd(tracker, collIndex);
					if (changeIndex < tracker.elements.size() && tracker.elements.get(changeIndex).index == collIndex)
						removeAddition(tracker, changeIndex);
					else
						tracker = replace(tracker, event, observer);
					break;
				case set:
					changeIndex = indexForAdd(tracker, collIndex);
					if (changeIndex < tracker.elements.size() && tracker.elements.get(changeIndex).index == collIndex)
						tracker.elements.get(changeIndex).newValue = event.getNewValue();
					else
						tracker = replace(tracker, event, observer);
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
						tracker = replace(tracker, event, observer);
					break;
				case remove:
					tracker = insertRemove(tracker, event);
					if (collection.isEmpty() && tracker.elements.size() > 1) {
						// If the collection is empty, no more elements can be removed and any other change will just call a replace,
						// so there's no more information we can possibly accumulate in this session.
						// Let's preemptively fire the event now.
						fireEventsFromSessionData(tracker, event, observer);
						tracker = null;
					}
					break;
				case set:
					tracker = replace(tracker, event, observer);
					break;
				}
				break;
			case set:
				switch (event.getType()) {
				case add:
					tracker = replace(tracker, event, observer);
					break;
				case remove:
					if (tracker.elements.size() == 1 && tracker.elements.get(0).index == event.getIndex()) {
						// The single element that was set is now being removed.
						// Replace the tracker with one that removes the old element, not the new one
						SessionChangeTracker<E> newTracker = new SessionChangeTracker<>(CollectionChangeType.remove);
						E oldValue = tracker.elements.get(0).oldValue;
						newTracker.elements.add(new ChangeValue<>(oldValue, oldValue, event.getIndex()));
						return newTracker;
					}
					changeIndex = indexForAdd(tracker, collIndex);
					if (changeIndex < tracker.elements.size() && tracker.elements.get(changeIndex).index == collIndex) {
						SessionChangeTracker<E> newTracker = new SessionChangeTracker<>(CollectionChangeType.remove);
						E oldValue = tracker.elements.get(changeIndex).oldValue;
						newTracker.elements.add(new ChangeValue<>(oldValue, oldValue, event.getIndex()));
						tracker.elements.remove(changeIndex);
						fireEventsFromSessionData(tracker, event, observer);
						return newTracker;
					} else
						tracker = replace(tracker, event, observer);
					break;
				case set:
					changeIndex = indexForAdd(tracker, collIndex);
					if (changeIndex < tracker.elements.size() && tracker.elements.get(changeIndex).index == collIndex)
						tracker.elements.get(changeIndex).newValue = event.getNewValue();
					else
						tracker.elements.add(changeIndex, new ChangeValue<>(event.getOldValue(), event.getNewValue(), collIndex));
					break;
				}
				break;
			}
			return tracker;
		}

		private SessionChangeTracker<E> replace(SessionChangeTracker<E> tracker, ObservableCollectionEvent<? extends E> event,
			Observer<? super CollectionChangeEvent<E>> observer) {
			fireEventsFromSessionData(tracker, event, observer);
			tracker = new SessionChangeTracker<>(event.getType());
			tracker.elements.add(new ChangeValue<>(event.getOldValue(), event.getNewValue(), event.getIndex()));
			return tracker;
		}

		private SessionChangeTracker<E> insertAddition(SessionChangeTracker<E> tracker, ObservableCollectionEvent<? extends E> event) {
			int changeIndex = indexForAdd(tracker, event.getIndex());
			if (changeIndex < tracker.elements.size() && tracker.elements.get(changeIndex).index >= event.getIndex())
				tracker.elements.get(changeIndex).index++;
			for (int i = changeIndex + 1; i < tracker.elements.size(); i++)
				tracker.elements.get(i).index++;
			tracker.elements.add(changeIndex, new ChangeValue<>(null, event.getNewValue(), event.getIndex()));
			return tracker;
		}

		private void removeAddition(SessionChangeTracker<E> tracker, int changeIndex) {
			tracker.elements.remove(changeIndex);
			for (; changeIndex < tracker.elements.size(); changeIndex++)
				tracker.elements.get(changeIndex).index--;
		}

		private SessionChangeTracker<E> insertRemove(SessionChangeTracker<E> tracker, ObservableCollectionEvent<? extends E> event) {
			int collectionIndex = event.getIndex();
			int changeIndex = indexForAdd(tracker, collectionIndex);
			collectionIndex += changeIndex;
			while (changeIndex < tracker.elements.size() && tracker.elements.get(changeIndex).index <= collectionIndex) {
				changeIndex++;
				collectionIndex++;
			}
			tracker.elements.add(changeIndex, new ChangeValue<>(event.getOldValue(), event.getNewValue(), collectionIndex));
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
			CollectionChangeEvent<E> evt = new CollectionChangeEvent<>(tracker.type, elements, cause);
			try (Transaction t = CollectionChangeEvent.use(evt)) {
				observer.onNext(evt);
			}
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
	 * An {@link ObservableElement} whose state reflects the value of an element within a collection whose value matches some condition
	 *
	 * @param <E> The type of the element
	 */
	public static abstract class AbstractObservableElementFinder<E> implements ObservableElement<E> {
		private final ObservableCollection<E> theCollection;
		private final Comparator<CollectionElement<? extends E>> theElementCompare;
		private final Supplier<? extends E> theDefault;

		/**
		 * @param collection The collection to find elements in
		 * @param elementCompare A comparator to determine whether to prefer one {@link #test(Object) matching} element over another. When
		 *        <code>elementCompare{@link Comparable#compareTo(Object) compareTo}(el1, el2)<0</code>, el1 will replace el2.
		 * @param def The default value to use when no element matches this finder's condition
		 */
		public AbstractObservableElementFinder(ObservableCollection<E> collection,
			Comparator<CollectionElement<? extends E>> elementCompare, Supplier<? extends E> def) {
			theCollection = collection;
			theElementCompare = elementCompare;
			theDefault = def;
		}

		/** @return The collection that this finder searches elements in */
		protected ObservableCollection<E> getCollection() {
			return theCollection;
		}

		@Override
		public TypeToken<E> getType() {
			return theCollection.getType();
		}

		@Override
		public ElementId getElementId() {
			ValueHolder<CollectionElement<E>> element = new ValueHolder<>();
			find(el -> element.accept(new SimpleElement(el.getElementId(), el.get())));
			if (element.get() != null)
				return element.get().getElementId();
			else
				return null;
		}

		@Override
		public E get() {
			ValueHolder<CollectionElement<E>> element = new ValueHolder<>();
			find(el -> element.accept(new SimpleElement(el.getElementId(), el.get())));
			if (element.get() != null)
				return element.get().get();
			else
				return theDefault.get();
		}

		/**
		 * Finds the value ad-hoc (stateless) from the concrete class
		 *
		 * @param onElement The consumer to give the element to when found
		 * @return Whether an element matching this finder's condition was found
		 */
		protected abstract boolean find(Consumer<? super CollectionElement<? extends E>> onElement);

		/**
		 * @param value The value to test
		 * @return Whether the value matches this finder's condition
		 */
		protected abstract boolean test(E value);

		@Override
		public Observable<ObservableElementEvent<E>> elementChanges() {
			return new Observable<ObservableElementEvent<E>>() {
				@Override
				public Subscription subscribe(Observer<? super ObservableElementEvent<E>> observer) {
					try (Transaction t = theCollection.lock(false, null)) {
						class FinderListener implements Consumer<ObservableCollectionEvent<? extends E>> {
							private SimpleElement theCurrentElement;
							private final Causable.CausableKey theCauseKey;

							{
								theCauseKey = Causable.key((cause, data) -> {
									SimpleElement oldElement = theCurrentElement;
									if (data.containsKey("re-search")) {
										// Means we need to find the new value in the collection
										if (!find(//
											el -> theCurrentElement = new SimpleElement(el.getElementId(), el.get())))
											theCurrentElement = null;
									} else
										theCurrentElement = (SimpleElement) data.get("replacement");
									ElementId oldId = oldElement == null ? null : oldElement.getElementId();
									ElementId newId = theCurrentElement == null ? null : theCurrentElement.getElementId();
									E oldVal = oldElement == null ? theDefault.get() : oldElement.get();
									E newVal = theCurrentElement == null ? theDefault.get() : theCurrentElement.get();
									observer.onNext(createChangeEvent(oldId, oldVal, newId, newVal, cause));
								});
							}

							@Override
							public void accept(ObservableCollectionEvent<? extends E> evt) {
								Map<Object, Object> causeData = theCauseKey.getData();
								if (causeData.containsKey("re-search"))
									return; // We've lost track of the current best and will need to find it again later
								SimpleElement current = (SimpleElement) causeData.getOrDefault("replacement", theCurrentElement);
								boolean mayReplace;
								boolean sameElement;
								boolean better;
								if (current == null) {
									sameElement = false;
									mayReplace = better = true;
								} else if (current.getElementId().equals(evt.getElementId())) {
									sameElement = true;
									mayReplace = true;
									better = evt.getType() == CollectionChangeType.set//
										? theElementCompare.compare(new SimpleElement(evt.getElementId(), evt.getNewValue()), current) <= 0
										: false;
								} else {
									sameElement = false;
									mayReplace = better = theElementCompare
										.compare(new SimpleElement(evt.getElementId(), evt.getNewValue()), current) < 0;
								}
								if (!mayReplace)
									return; // Even if the new element's value matches, it wouldn't replace the current value
								boolean matches = test(evt.isFinal() ? evt.getOldValue() : evt.getNewValue());
								if (!matches && !sameElement)
									return;// If the new value doesn't match and it's not the current element, we don't care

								// At this point we know that we will have to do something
								// If causeData!=null, then it's unmodifiable, so we need to grab the modifiable form using the cause
								causeData = evt.getRootCausable().onFinish(theCauseKey);
								if (!matches) {
									// The current element's value no longer matches
									// We need to search for the new value if we don't already know of a better match.
									causeData.remove("replacement");
									causeData.put("re-search", true);
								} else {
									if (evt.isFinal()) {
										// The current element has been removed
										// We need to search for the new value if we don't already know of a better match.
										// The signal for this is a null replacement
										causeData.remove("replacement");
										causeData.put("re-search", true);
									} else {
										// Either:
										// * There is no current element and the new element matches
										// ** --use it unless we already know of a better match
										// * The new value is better than the current element
										// If we already know of a replacement element even better-positioned than the new element,
										// ignore the new one
										if (current == null || better) {
											causeData.put("replacement", new SimpleElement(evt.getElementId(), evt.getNewValue()));
											causeData.remove("re-search");
										} else if (sameElement) {
											// The current best element is removed or replaced with an inferior value. Need to re-search.
											causeData.remove("replacement");
											causeData.put("re-search", true);
										}
									}
								}
							}
						}
						FinderListener listener = new FinderListener();
						if (!find(el -> {
							listener.theCurrentElement = new SimpleElement(el.getElementId(), el.get());
						}))
							listener.theCurrentElement = null;
						Subscription collSub = theCollection.onChange(listener);
						ElementId initId = listener.theCurrentElement == null ? null : listener.theCurrentElement.getElementId();
						E initVal = listener.theCurrentElement == null ? theDefault.get() : listener.theCurrentElement.get();
						observer.onNext(createInitialEvent(initId, initVal, null));
						return new Subscription() {
							private boolean isDone;

							@Override
							public void unsubscribe() {
								if (isDone)
									return;
								isDone = true;
								collSub.unsubscribe();
								ElementId endId = listener.theCurrentElement == null ? null : listener.theCurrentElement.getElementId();
								E endVal = listener.theCurrentElement == null ? theDefault.get() : listener.theCurrentElement.get();
								observer.onCompleted(createChangeEvent(endId, endVal, endId, endVal, null));
							}
						};
					}
				}

				@Override
				public boolean isSafe() {
					return true;
				}
			};
		}

		@Override
		public Transaction lock() {
			return theCollection.lock(false, null);
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

			@Override
			public String toString() {
				return String.valueOf(theValue);
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
		private final Ternian isFirst;

		/**
		 * @param collection The collection to find elements in
		 * @param test The test to find elements that pass
		 * @param def The default value to use when no passing element is found in the collection
		 * @param first Whether to get the first value in the collection that passes, the last value, or any passing value
		 */
		protected ObservableCollectionFinder(ObservableCollection<E> collection, Predicate<? super E> test, Supplier<? extends E> def,
			Ternian first) {
			super(collection, (el1, el2) -> {
				if (first == Ternian.NONE)
					return 0;
				int compare = el1.getElementId().compareTo(el2.getElementId());
				if (!first.value)
					compare = -compare;
				return compare;
			}, def);
			theTest = test;
			isFirst = first;
		}

		@Override
		protected boolean find(Consumer<? super CollectionElement<? extends E>> onElement) {
			return getCollection().find(theTest, onElement, isFirst.withDefault(true));
		}

		@Override
		protected boolean test(E value) {
			return theTest.test(value);
		}
	}

	/**
	 * Finds an element in a collection with a value equivalent to a given value
	 *
	 * @param <E> The type of the element
	 */
	public static class ObservableEquivalentFinder<E> extends AbstractObservableElementFinder<E> {
		private final E theValue;
		private final boolean isFirst;

		/**
		 * @param collection The collection to find the element within
		 * @param value The value to find
		 * @param first Whether to search for the first, last, or any equivalent element
		 */
		public ObservableEquivalentFinder(ObservableCollection<E> collection, E value, boolean first) {
			super(collection, (el1, el2) -> {
				int compare = el1.getElementId().compareTo(el2.getElementId());
				if (!first)
					compare = -compare;
				return compare;
			}, () -> null);
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
	 * Searches all the elements in a collection for the best by some measure
	 *
	 * @param <E> The type of the element
	 */
	public static class BestCollectionElement<E> extends AbstractObservableElementFinder<E> {
		private final Comparator<? super E> theCompare;
		private final Ternian isFirst;

		/**
		 * @param collection The collection to search within
		 * @param compare The comparator for element values
		 * @param def The default value for an empty collection
		 * @param first Whether to use the first, last, or any element in a tie
		 */
		public BestCollectionElement(ObservableCollection<E> collection, Comparator<? super E> compare, Supplier<? extends E> def,
			Ternian first) {
			super(collection, (el1, el2) -> {
				int comp = compare.compare(el1.get(), el2.get());
				if (comp == 0 && first.value != null) {
					comp = el1.getElementId().compareTo(el2.getElementId());
					if (!first.value)
						comp = -comp;
				}
				return comp;
			}, def);
			theCompare = compare;
			isFirst = first;
		}

		@Override
		protected boolean find(Consumer<? super CollectionElement<? extends E>> onElement) {
			ValueHolder<CollectionElement<E>> best = new ValueHolder<>();
			boolean last = !isFirst.withDefault(true);
			getCollection().spliterator().forEachElement(el -> {
				if (!best.isPresent())
					best.accept(el);
				else {
					int comp = theCompare.compare(el.get(), best.get().get());
					if (comp < 0)
						best.accept(el);
					else if (last && comp == 0)
						best.accept(el);
				}
			}, true);
			if (best.isPresent())
				onElement.accept(best.get());
			return best.isPresent();
		}

		@Override
		protected boolean test(E value) {
			return true;
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
		public Observable<ObservableValueEvent<T>> changes() {
			return new Observable<ObservableValueEvent<T>>() {
				@Override
				public boolean isSafe() {
					return true;
				}

				@Override
				public Subscription subscribe(Observer<? super ObservableValueEvent<T>> observer) {
					ValueHolder<X> x = new ValueHolder<>();
					ValueHolder<T> value = new ValueHolder<>();
					boolean[] init = new boolean[1];
					Causable.CausableKey key = Causable
						.key((root, values) -> {
							T oldV = value.get();
							T v = getValue((X) values.get("x"));
							value.accept(v);
							if (!init[0])
								fireChangeEvent(oldV, v, root, observer::onNext);
						});
					x.accept(init());
					value.accept(getValue(x.get()));
					Subscription sub;
					try (Transaction t = theCollection.lock(false, null)) {
						sub = theCollection.onChange(evt -> {
							X newX = update(x.get(), evt);
							x.accept(newX);

							Map<Object, Object> values = evt.getRootCausable().onFinish(key);
							values.put("x", newX);
						});
						fireInitialEvent(value.get(), null, observer::onNext);
						init[0] = false;
					}
					return sub;
				}
			};
		}

		@Override
		public Transaction lock() {
			return theCollection.lock(false, null);
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

	/**
	 * A structure to keeps track of the number of occurrences of a particular value in two collections
	 *
	 * @param <E> The type of the value to track
	 */
	public static class ValueCount<E> {
		E value;
		int left;
		int right;

		/** @param val The value to track */
		public ValueCount(E val) {
			value = val;
		}

		/** @return The value being tracked */
		public E getValue() {
			return value;
		}

		/** @return The number of occurrences of this value in the left collection */
		public int getLeftCount() {
			return left;
		}

		/** @return The number of occurrences of this value in the right collection */
		public int getRightCount() {
			return right;
		}

		/** @return Whether this value occurs at all in either collection */
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

		/**
		 * @param left The left collection
		 * @param right The right collection
		 * @param until The observable to terminate this structure's record-keeping activity
		 * @param weak Whether to listen to the collections weakly or strongly
		 * @param initAction The action to perform on this structure after the initial values of the collections are accounted for
		 * @return The subscription to use to cease record-keeping
		 */
		public Subscription init(ObservableCollection<E> left, ObservableCollection<X> right, Observable<?> until, boolean weak,
			Consumer<ValueCounts<E, X>> initAction) {
			theLock.lock();
			try (Transaction lt = left.lock(false, null); Transaction rt = right.lock(false, null)) {
				left.spliterator().forEachRemaining(e -> modify(e, true, true, null));
				right.spliterator().forEachRemaining(x -> {
					if (leftEquiv.isElement(x))
						modify((E) x, true, false, null);
				});

				Consumer<ObservableCollectionEvent<? extends E>> leftListener = evt -> onEvent(evt, true);
				Consumer<ObservableCollectionEvent<? extends X>> rightListener = evt -> onEvent(evt, false);
				Subscription sub;
				if (weak) {
					WeakListening.Builder builder = WeakListening.build();
					if (until != null)
						builder.withUntil(r -> until.act(v -> r.run()));
					WeakListening listening = builder.getListening();
					listening.withConsumer(leftListener, left::onChange);
					listening.withConsumer(rightListener, right::onChange);
					sub = builder::unsubscribe;
				} else {
					Subscription leftSub = left.onChange(leftListener);
					Subscription rightSub = right.onChange(rightListener);
					sub = Subscription.forAll(leftSub, rightSub);
				}
				initAction.accept(this);
				return sub;
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
						if (count.right > 0)
							commonCount++;
					} else {
						leftCount--;
						leftCounts.remove(value);
						if (count.right > 0)
							commonCount--;
					}
				} else {
					if (add) {
						rightCount++;
						rightCounts.put(value, count);
						if (count.left > 0)
							commonCount++;
					} else {
						rightCount--;
						rightCounts.remove(value);
						if (count.left > 0)
							commonCount--;
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

		/**
		 * @param count The counts for value in the left and right collections
		 * @param oldValue The old value of the change
		 * @param type The type of the change that occurred
		 * @param onLeft Whether the change occurred in the left or in the right collection
		 * @param containmentChange Whether the change resulted in either the left or right collection's containment of the value to change
		 *        to true or false
		 * @param cause The cause of the change
		 */
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
		public Observable<ObservableValueEvent<Boolean>> changes() {
			return new Observable<ObservableValueEvent<Boolean>>() {
				@Override
				public boolean isSafe() {
					return true;
				}

				@Override
				public Subscription subscribe(Observer<? super ObservableValueEvent<Boolean>> observer) {
					boolean[] initialized = new boolean[1];
					boolean[] satisfied = new boolean[1];
					ValueCounts<E, X> counts = new ValueCounts<E, X>(theLeft.equivalence()) {
						private final CausableKey theKey = Causable.key((c, data) -> {
							boolean wasSatisfied = satisfied[0];
							satisfied[0] = theSatisfiedCheck.test(this);
							if (!initialized[0] && wasSatisfied != satisfied[0])
								fireChangeEvent(wasSatisfied, satisfied[0], c, observer::onNext);
						});

						@Override
						protected void changed(ValueCount<?> count, Object oldValue, CollectionChangeType type, boolean onLeft,
							boolean containmentChange, Causable cause) {
							cause.getRootCausable().onFinish(theKey);
						}
					};
					return counts.init(theLeft, theRight, null, false, c -> {
						ObservableValueEvent<Boolean> evt = createInitialEvent(theSatisfiedCheck.test(counts), null);
						try (Transaction t = ObservableValueEvent.use(evt)) {
							observer.onNext(evt);
						}
					});
				}
			};
		}

		@Override
		public Transaction lock() {
			Transaction leftLock = theLeft.lock(false, null);
			Transaction rightLock = theRight.lock(false, null);
			return () -> {
				leftLock.close();
				rightLock.close();
			};
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
			ObservableValue<ObservableCollection<T>> cv = value.map(v -> ObservableCollection.of(value.getType(), v));
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
			super(left, right, counts -> counts.getRightCount() == counts.getCommonCount());
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

	/**
	 * An {@link ObservableCollection} whose elements are reversed from its parent
	 *
	 * @param <E> The type of values in the collection
	 */
	public static class ReversedObservableCollection<E> extends BetterList.ReversedList<E> implements ObservableCollection<E> {
		/** @param wrapped The collection whose elements to reverse */
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
			try (Transaction t = lock(false, null)) {
				return getWrapped().onChange(new ReversedSubscriber(observer, size()));
			}
		}

		@Override
		public ObservableCollection<E> reverse() {
			return getWrapped();
		}

		@Override
		public E[] toArray() {
			return ObservableCollection.super.toArray();
		}

		@Override
		public void setValue(Collection<ElementId> elements, E value) {
			getWrapped().setValue(//
				elements.stream().map(el -> el.reverse()).collect(Collectors.toList()), value);
		}

		@Override
		public CollectionSubscription subscribe(Consumer<? super ObservableCollectionEvent<? extends E>> observer, boolean forward) {
			return getWrapped().subscribe(new ReversedSubscriber(observer, 0), !forward);
		}

		class ReversedSubscriber implements Consumer<ObservableCollectionEvent<? extends E>> {
			private final Consumer<? super ObservableCollectionEvent<? extends E>> theObserver;
			private int theSize;

			ReversedSubscriber(Consumer<? super ObservableCollectionEvent<? extends E>> observer, int size) {
				theObserver = observer;
				theSize = size;
			}

			@Override
			public void accept(ObservableCollectionEvent<? extends E> evt) {
				if (evt.getType() == CollectionChangeType.add)
					theSize++;
				int index = theSize - evt.getIndex() - 1;
				if (evt.getType() == CollectionChangeType.remove)
					theSize--;
				ObservableCollectionEvent<E> reversed = new ObservableCollectionEvent<>(evt.getElementId().reverse(), getType(), index,
					evt.getType(), evt.getOldValue(), evt.getNewValue(), evt);
				try (Transaction t = ObservableCollectionEvent.use(reversed)) {
					theObserver.accept(reversed);
				}
			}
		}
	}

	/**
	 * A derived collection, {@link ObservableCollection.CollectionDataFlow#collect() collected} from a
	 * {@link ObservableCollection.CollectionDataFlow}. A passive collection maintains no information about its sources (either the source
	 * collection or any external sources from its flow), but relies on the state of the collection. Each listener to the collection
	 * maintains its own state which is released when the listener is {@link Subscription#unsubscribe() unsubscribed}. This stateless
	 * behavior cannot accommodate some categories of operations, but may be much more efficient for those it does support in many
	 * situations.
	 *
	 * @param <E> The type of the source collection
	 * @param <T> The type of values in the collection
	 */
	public static class PassiveDerivedCollection<E, T> implements ObservableCollection<T> {
		private final ObservableCollection<E> theSource;
		private final PassiveCollectionManager<E, ?, T> theFlow;
		private final Equivalence<? super T> theEquivalence;
		private final boolean isReversed;

		/**
		 * @param source The source collection
		 * @param flow The passive manager to produce this collection's elements
		 */
		public PassiveDerivedCollection(ObservableCollection<E> source, PassiveCollectionManager<E, ?, T> flow) {
			theSource = source;
			theFlow = flow;
			theEquivalence = theFlow.equivalence();
			isReversed = theFlow.isReversed();
		}

		/** @return The source collection */
		protected ObservableCollection<E> getSource() {
			return theSource;
		}

		/** @return The passive manager that produces this collection's elements */
		protected PassiveCollectionManager<E, ?, T> getFlow() {
			return theFlow;
		}

		@Override
		public boolean isContentControlled() {
			return theSource.isContentControlled();
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
			return theSource.isLockSupported() || theFlow.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, boolean structural, Object cause) {
			Transaction srcT = theSource.lock(write, structural, cause);
			Transaction flowT = theFlow.lock(write, cause);
			return () -> {
				srcT.close();
				flowT.close();
			};
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

		/**
		 * @param source The element ID from the source collection
		 * @return The ID of the corresponding element in this collection
		 */
		protected ElementId mapId(ElementId source) {
			if (source == null)
				return null;
			return isReversed ? source.reverse() : source;
		}

		/**
		 * @param mapped The ID of an element in this collection
		 * @return The ID of the corresponding element in the source collection
		 */
		protected ElementId reverseId(ElementId mapped) {
			if (mapped == null)
				return null;
			return isReversed ? mapped.reverse() : mapped;
		}

		@Override
		public String canAdd(T value, ElementId after, ElementId before) {
			FilterMapResult<T, E> reversed = theFlow.reverse(value, true);
			if (!reversed.isAccepted())
				return reversed.getRejectReason();
			if (isReversed) {
				ElementId temp = reverseId(after);
				after = reverseId(before);
				before = temp;
			}
			return theSource.canAdd(reversed.result, after, before);
		}

		@Override
		public CollectionElement<T> addElement(T value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			try (Transaction t = lock(true, null)) {
				// Lock so the reversed value is consistent until it is added
				FilterMapResult<T, E> reversed = theFlow.reverse(value, true);
				if (reversed.throwIfError(IllegalArgumentException::new) != null)
					return null;
				if (isReversed) {
					ElementId temp = reverseId(after);
					after = reverseId(before);
					before = temp;
				}
				CollectionElement<E> srcEl = theSource.addElement(reversed.result, after, before, first);
				return srcEl == null ? null : elementFor(srcEl, null);
			}
		}

		@Override
		public void clear() {
			if (!theFlow.isRemoveFiltered())
				theSource.clear();
			else {
				boolean reverse = isReversed;
				spliterator(!reverse).forEachElementM(el -> {
					if (el.canRemove() == null)
						el.remove();
				}, !reverse);
			}
		}

		@Override
		public void setValue(Collection<ElementId> elements, T value) {
			try (Transaction t = lock(true, false, null)) {
				Function<? super E, ? extends T> map = theFlow.map().get();
				theFlow.setValue(//
					elements.stream().map(el -> theFlow.map(theSource.mutableElement(reverseId(el)), map)).collect(Collectors.toList()),
					value);
			}
		}

		@Override
		public int getElementsBefore(ElementId id) {
			if (isReversed)
				return theSource.getElementsAfter(id.reverse());
			else
				return theSource.getElementsBefore(id);
		}

		@Override
		public int getElementsAfter(ElementId id) {
			if (isReversed)
				return theSource.getElementsBefore(id.reverse());
			else
				return theSource.getElementsAfter(id);
		}

		@Override
		public CollectionElement<T> getElement(int index) {
			if (isReversed)
				index = getSource().size() - index - 1;
			return elementFor(theSource.getElement(index), null);
		}

		@Override
		public CollectionElement<T> getElement(T value, boolean first) {
			if (!belongs(value))
				return null;
			try (Transaction t = lock(false, null)) {
				Function<? super E, ? extends T> map = theFlow.map().get();
				if (!theFlow.isManyToOne()) {
					// If the flow is one-to-one, we can use any search optimizations the source collection may be capable of
					FilterMapResult<T, E> reversed = theFlow.reverse(value, false);
					if (!reversed.isError() && equivalence().elementEquals(map.apply(reversed.result), value)) {
						CollectionElement<E> srcEl = theSource.getElement(reversed.result, first);
						return srcEl == null ? null : elementFor(srcEl, null);
					}
				}
				ElementId[] match = new ElementId[1];
				boolean forward = first ^ isReversed;
				MutableElementSpliterator<E> spliter = theSource.spliterator(forward);
				while (match[0] == null && spliter.forElement(el -> {
					if (equivalence().elementEquals(map.apply(el.get()), value))
						match[0] = el.getElementId();
				}, forward)) {}
				if (match[0] == null)
					return null;
				return elementFor(theSource.getElement(match[0]), map);
			}
		}

		@Override
		public CollectionElement<T> getElement(ElementId id) {
			return elementFor(theSource.getElement(reverseId(id)), null);
		}

		@Override
		public CollectionElement<T> getTerminalElement(boolean first) {
			if (isReversed)
				first = !first;
			CollectionElement<E> t = theSource.getTerminalElement(first);
			return t == null ? null : elementFor(t, null);
		}

		@Override
		public CollectionElement<T> getAdjacentElement(ElementId elementId, boolean next) {
			if (isReversed) {
				elementId = elementId.reverse();
				next = !next;
			}
			CollectionElement<E> adj = theSource.getAdjacentElement(elementId, next);
			return adj == null ? null : elementFor(adj, null);
		}

		@Override
		public MutableCollectionElement<T> mutableElement(ElementId id) {
			return mutableElementFor(theSource.mutableElement(reverseId(id)), null);
		}

		@Override
		public MutableElementSpliterator<T> spliterator(ElementId element, boolean asNext) {
			if (isReversed) {
				element = reverseId(element);
				asNext = !asNext;
			}
			return new PassiveDerivedMutableSpliterator(theSource.spliterator(element, asNext));
		}

		/**
		 * @param el The source element
		 * @param map The mapping function for the element's values, or null to just get the current map from the flow
		 * @return The corresponding element for this collection
		 */
		protected CollectionElement<T> elementFor(CollectionElement<? extends E> el, Function<? super E, ? extends T> map) {
			Function<? super E, ? extends T> fMap = map == null ? theFlow.map().get() : map;
			return new CollectionElement<T>() {
				@Override
				public T get() {
					return fMap.apply(el.get());
				}

				@Override
				public ElementId getElementId() {
					return mapId(el.getElementId());
				}
			};
		}

		/**
		 * @param el The source mutable element
		 * @param map The mapping function for the element's values, or null to just get the current map from the flow
		 * @return The corresponding mutable element for this collection
		 */
		protected MutableCollectionElement<T> mutableElementFor(MutableCollectionElement<E> el, Function<? super E, ? extends T> map) {
			Function<? super E, ? extends T> fMap = map == null ? theFlow.map().get() : map;
			MutableCollectionElement<T> flowEl = theFlow.map(el, fMap);
			class PassiveMutableElement implements MutableCollectionElement<T> {
				@Override
				public BetterCollection<T> getCollection() {
					return PassiveDerivedCollection.this;
				}

				@Override
				public ElementId getElementId() {
					return mapId(el.getElementId());
				}

				@Override
				public T get() {
					return flowEl.get();
				}

				@Override
				public String isEnabled() {
					return flowEl.isEnabled();
				}

				@Override
				public String isAcceptable(T value) {
					return flowEl.isAcceptable(value);
				}

				@Override
				public void set(T value) throws UnsupportedOperationException, IllegalArgumentException {
					flowEl.set(value);
				}

				@Override
				public String canRemove() {
					return flowEl.canRemove();
				}

				@Override
				public void remove() throws UnsupportedOperationException {
					flowEl.remove();
				}

				@Override
				public String toString() {
					return flowEl.toString();
				}
			}
			return new PassiveMutableElement();
		}

		@Override
		public MutableElementSpliterator<T> spliterator(boolean fromStart) {
			if (isReversed)
				fromStart = !fromStart;
			return new PassiveDerivedMutableSpliterator(theSource.spliterator(fromStart));
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends T>> observer) {
			Subscription sourceSub, mapSub;
			try (Transaction outerFlowLock = theFlow.lock(false, null)) {
				Function<? super E, ? extends T>[] currentMap = new Function[1];
				mapSub = theFlow.map().changes().act(evt -> {
					if (evt.isInitial()) {
						currentMap[0] = evt.getNewValue();
						return;
					}
					try (Transaction sourceLock = theSource.lock(false, evt)) {
						currentMap[0] = evt.getNewValue();
						MutableElementSpliterator<? extends E> sourceSpliter = theSource.spliterator(!isReversed);
						int[] index = new int[1];
						sourceSpliter.forEachElement(sourceEl -> {
							E sourceVal = sourceEl.get();
							observer.accept(new ObservableCollectionEvent<>(mapId(sourceEl.getElementId()), getType(), index[0]++,
								CollectionChangeType.set, evt.getOldValue().apply(sourceVal), currentMap[0].apply(sourceVal), evt));
						}, !isReversed);
					}
				});
				try (Transaction sourceT = theSource.lock(false, null)) {
					sourceSub = getSource().onChange(new Consumer<ObservableCollectionEvent<? extends E>>() {
						private int theSize = isReversed ? size() : -1;

						@Override
						public void accept(ObservableCollectionEvent<? extends E> evt) {
							try (Transaction t = theFlow.lock(true, evt)) {
								T oldValue, newValue;
								switch (evt.getType()) {
								case add:
									newValue = currentMap[0].apply(evt.getNewValue());
									oldValue = null;
									break;
								case remove:
									oldValue = currentMap[0].apply(evt.getOldValue());
									newValue = oldValue;
									break;
								case set:
									BiTuple<T, T> values = theFlow.map(evt.getOldValue(), evt.getNewValue(), currentMap[0]);
									if (values == null)
										return;
									oldValue = values.getValue1();
									newValue = values.getValue2();
									break;
								default:
									throw new IllegalStateException("Unrecognized collection change type: " + evt.getType());
								}
								int index;
								if (!isReversed)
									index = evt.getIndex();
								else {
									if (evt.getType() == CollectionChangeType.add)
										theSize++;
									index = theSize - evt.getIndex() - 1;
									if (evt.getType() == CollectionChangeType.remove)
										theSize--;
								}
								observer.accept(new ObservableCollectionEvent<>(mapId(evt.getElementId()), getType(), index, evt.getType(),
									oldValue, newValue, evt));
							}
						}
					});
				}
			}
			return Subscription.forAll(sourceSub, mapSub);
		}

		@Override
		public String toString() {
			return ObservableCollection.toString(this);
		}

		/** A spliterator for the {@link PassiveDerivedCollection} class */
		protected class PassiveDerivedMutableSpliterator extends MutableElementSpliterator.SimpleMutableSpliterator<T> {
			private final MutableElementSpliterator<E> theSourceSpliter;
			private final Function<? super E, ? extends T> theMap;

			PassiveDerivedMutableSpliterator(MutableElementSpliterator<E> srcSpliter) {
				super(PassiveDerivedCollection.this);
				theSourceSpliter = srcSpliter;
				theMap = theFlow.map().get();
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
				if (isReversed)
					forward = !forward;
				return theSourceSpliter.forElement(//
					el -> action.accept(elementFor(el, theMap)), forward);
			}

			@Override
			protected boolean internalForElementM(Consumer<? super MutableCollectionElement<T>> action, boolean forward) {
				if (isReversed)
					forward = !forward;
				return theSourceSpliter.forElementM(//
					el -> action.accept(mutableElementFor(el, theMap)), forward);
			}

			@Override
			public MutableElementSpliterator<T> trySplit() {
				MutableElementSpliterator<E> srcSplit = theSourceSpliter.trySplit();
				return srcSplit == null ? null : new PassiveDerivedMutableSpliterator(srcSplit);
			}
		}
	}

	/**
	 * Stores strong references to actively-derived collections on which listeners are installed, preventing the garbage-collection of these
	 * collections since the listeners only contain weak references to their data sources.
	 */
	private static final Set<ActiveDerivedCollection<?>> STRONG_REFS = new ConcurrentHashSet<>();

	/**
	 * A derived collection, {@link ObservableCollection.CollectionDataFlow#collect() collected} from a
	 * {@link ObservableCollection.CollectionDataFlow}. An active collection maintains a sorted collection of derived elements, enabling
	 * complex and order-affecting operations like {@link ObservableCollection.CollectionDataFlow#sorted(Comparator) sort} and
	 * {@link ObservableCollection.CollectionDataFlow#distinct() distinct}.
	 *
	 * @param <T> The type of values in the collection
	 */
	public static class ActiveDerivedCollection<T> implements ObservableCollection<T> {
		/**
		 * Holds a {@link ObservableCollectionDataFlowImpl.DerivedCollectionElement}s for an {@link ActiveDerivedCollection}
		 *
		 * @param <T> The type of the collection
		 */
		protected static class DerivedElementHolder<T> implements ElementId {
			/** The element from the flow */
			protected final DerivedCollectionElement<T> element;
			BinaryTreeNode<DerivedElementHolder<T>> treeNode;

			/** @param element The element from the flow */
			protected DerivedElementHolder(DerivedCollectionElement<T> element) {
				this.element = element;
			}

			@Override
			public boolean isPresent() {
				return treeNode.getElementId().isPresent();
			}

			@Override
			public int compareTo(ElementId o) {
				return treeNode.getElementId().compareTo(((DerivedElementHolder<T>) o).treeNode.getElementId());
			}

			DerivedElementHolder<T> check() {
				if (treeNode == null)
					throw new IllegalStateException("This node is not currentlly present in the collection");
				return this;
			}

			/** @return The current value of this element */
			public T get() {
				return element.get();
			}

			@Override
			public int hashCode() {
				return treeNode.hashCode();
			}

			@Override
			public boolean equals(Object obj) {
				if (!(obj instanceof DerivedElementHolder))
					return false;
				return treeNode.equals(((DerivedElementHolder<T>) obj).treeNode);
			}

			@Override
			public String toString() {
				return element.toString();
			}
		}

		private final ActiveCollectionManager<?, ?, T> theFlow;
		private final BetterTreeSet<DerivedElementHolder<T>> theDerivedElements;
		private final ListenerList<Consumer<? super ObservableCollectionEvent<? extends T>>> theListeners;
		private final AtomicInteger theListenerCount;
		private final Equivalence<? super T> theEquivalence;
		private final AtomicLong theModCount;
		private final AtomicLong theStructureStamp;
		private final WeakListening.Builder theWeakListening;

		/**
		 * @param flow The active data manager to power this collection
		 * @param until The observable to cease maintenance
		 */
		public ActiveDerivedCollection(ActiveCollectionManager<?, ?, T> flow, Observable<?> until) {
			theFlow = flow;
			theDerivedElements = new BetterTreeSet<>(false, (e1, e2) -> e1.element.compareTo(e2.element));
			theListeners = new ListenerList<>(null);
			theListenerCount = new AtomicInteger();
			theEquivalence = flow.equivalence();
			theModCount = new AtomicLong();
			theStructureStamp = new AtomicLong();

			// Begin listening
			ElementAccepter<T> onElement = (el, cause) -> {
				theStructureStamp.incrementAndGet();
				DerivedElementHolder<T>[] holder = new DerivedElementHolder[] { createHolder(el) };
				holder[0].treeNode = theDerivedElements.addElement(holder[0], false);
				fireListeners(new ObservableCollectionEvent<>(holder[0], theFlow.getTargetType(), holder[0].treeNode.getNodesBefore(),
					CollectionChangeType.add, null, el.get(), cause));
				el.setListener(new CollectionElementListener<T>() {
					@Override
					public void update(T oldValue, T newValue, Object elCause) {
						BinaryTreeNode<DerivedElementHolder<T>> left = holder[0].treeNode.getClosest(true);
						BinaryTreeNode<DerivedElementHolder<T>> right = holder[0].treeNode.getClosest(false);
						if ((left != null && left.get().element.compareTo(holder[0].element) > 0)
							|| (right != null && right.get().element.compareTo(holder[0].element) < 0)) {
							theStructureStamp.incrementAndGet();
							// Remove the element and re-add at the new position.
							int index = holder[0].treeNode.getNodesBefore();
							theDerivedElements.mutableElement(holder[0].treeNode.getElementId()).remove();
							fireListeners(new ObservableCollectionEvent<>(holder[0], theFlow.getTargetType(), index,
								CollectionChangeType.remove, oldValue, null, elCause));
							// Don't re-use elements
							holder[0] = createHolder(el);
							holder[0].treeNode = theDerivedElements.addElement(holder[0], false);
							fireListeners(new ObservableCollectionEvent<>(holder[0], theFlow.getTargetType(),
								holder[0].treeNode.getNodesBefore(), CollectionChangeType.add, null, newValue, elCause));
						} else {
							theModCount.incrementAndGet();
							fireListeners(new ObservableCollectionEvent<>(holder[0], getType(), holder[0].treeNode.getNodesBefore(),
								CollectionChangeType.set, oldValue, newValue, elCause));
						}
					}

					@Override
					public void removed(T value, Object elCause) {
						theStructureStamp.incrementAndGet();
						int index = holder[0].treeNode.getNodesBefore();
						if (holder[0].treeNode.getElementId().isPresent()) // May have been removed already
							theDerivedElements.mutableElement(holder[0].treeNode.getElementId()).remove();
						fireListeners(new ObservableCollectionEvent<>(holder[0], theFlow.getTargetType(), index,
							CollectionChangeType.remove, value, value, elCause));
					}
				});
			};
			// Must maintain a strong reference to the event listening so it is not GC'd while the collection is still alive
			theWeakListening = WeakListening.build().withUntil(r -> until.act(v -> r.run()));
			theFlow.begin(true, onElement, theWeakListening.getListening());
		}

		/**
		 * @param el The flow element
		 * @return The holder for the element
		 */
		protected DerivedElementHolder<T> createHolder(DerivedCollectionElement<T> el) {
			return new DerivedElementHolder<>(el);
		}

		/** @return This collection's data manager */
		protected ActiveCollectionManager<?, ?, T> getFlow() {
			return theFlow;
		}

		/** @return This collection's element holders */
		protected BetterTreeSet<DerivedElementHolder<T>> getPresentElements() {
			return theDerivedElements;
		}

		void fireListeners(ObservableCollectionEvent<T> event) {
			try (Transaction t = ObservableCollectionEvent.use(event)) {
				theListeners.forEach(//
					listener -> listener.accept(event));
			}
		}

		@Override
		public boolean isContentControlled() {
			return theFlow.isContentControlled();
		}

		@Override
		public int getElementsBefore(ElementId id) {
			return ((DerivedElementHolder<T>) id).treeNode.getNodesBefore();
		}

		@Override
		public int getElementsAfter(ElementId id) {
			return ((DerivedElementHolder<T>) id).treeNode.getNodesAfter();
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends T>> observer) {
			Runnable remove = theListeners.add(observer, true);
			// Add a strong reference to this collection while we have listeners.
			// Otherwise, this collection could be GC'd and listeners (which may not reference this collection) would just be left hanging
			if (theListenerCount.getAndIncrement() == 0)
				STRONG_REFS.add(this);
			return () -> {
				remove.run();
				if (theListenerCount.decrementAndGet() == 0)
					STRONG_REFS.remove(this);
			};
		}

		@Override
		public TypeToken<T> getType() {
			return theFlow.getTargetType();
		}

		@Override
		public boolean isLockSupported() {
			return theFlow.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, boolean structural, Object cause) {
			return theFlow.lock(write, structural, cause);
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
			return theDerivedElements.size();
		}

		@Override
		public boolean isEmpty() {
			return theDerivedElements.isEmpty();
		}

		@Override
		public T get(int index) {
			try (Transaction t = lock(false, null)) {
				return theDerivedElements.get(index).get();
			}
		}

		@Override
		public CollectionElement<T> getElement(int index) {
			return elementFor(theDerivedElements.get(index));
		}

		@Override
		public CollectionElement<T> getElement(T value, boolean first) {
			try (Transaction t = lock(false, null)) {
				Comparable<DerivedCollectionElement<T>> finder = getFlow().getElementFinder(value);
				if (finder != null) {
					BinaryTreeNode<DerivedElementHolder<T>> found = theDerivedElements.search(holder -> finder.compareTo(holder.element), //
						SortedSearchFilter.of(first, false));
					if (found == null || !equivalence().elementEquals(found.get().element.get(), value))
						return null;
					while (found.getChild(first) != null && equivalence().elementEquals(found.getChild(first).get().element.get(), value))
						found = found.getChild(first);
					return elementFor(found.get());
				}
				for (DerivedElementHolder<T> el : (first ? theDerivedElements : theDerivedElements.reverse()))
					if (equivalence().elementEquals(el.get(), value))
						return elementFor(el);
				return null;
			}
		}

		@Override
		public CollectionElement<T> getElement(ElementId id) {
			return elementFor((DerivedElementHolder<T>) id);
		}

		@Override
		public CollectionElement<T> getTerminalElement(boolean first) {
			DerivedElementHolder<T> holder = first ? theDerivedElements.peekFirst() : theDerivedElements.peekLast();
			return holder == null ? null : getElement(holder);
		}

		@Override
		public CollectionElement<T> getAdjacentElement(ElementId elementId, boolean next) {
			DerivedElementHolder<T> holder = (DerivedElementHolder<T>) elementId;
			BinaryTreeNode<DerivedElementHolder<T>> adjacentNode = holder.treeNode.getClosest(!next);
			return adjacentNode == null ? null : getElement(adjacentNode.get());
		}

		@Override
		public MutableCollectionElement<T> mutableElement(ElementId id) {
			return mutableElement(id, null);
		}

		private MutableCollectionElement<T> mutableElement(ElementId id, MutableCollectionElement<DerivedElementHolder<T>> spliterElement) {
			DerivedElementHolder<T> el = (DerivedElementHolder<T>) id;
			class DerivedMutableCollectionElement implements MutableCollectionElement<T> {
				@Override
				public BetterCollection<T> getCollection() {
					return ActiveDerivedCollection.this;
				}

				@Override
				public ElementId getElementId() {
					return el;
				}

				@Override
				public T get() {
					return el.element.get();
				}

				@Override
				public String isEnabled() {
					return el.element.isEnabled();
				}

				@Override
				public String isAcceptable(T value) {
					return el.element.isAcceptable(value);
				}

				@Override
				public void set(T value) throws UnsupportedOperationException, IllegalArgumentException {
					el.element.set(value);
				}

				@Override
				public String canRemove() {
					return el.element.canRemove();
				}

				@Override
				public void remove() throws UnsupportedOperationException {
					if (spliterElement != null) { // Don't remove the spliterator element if the removal is not allowed
						String msg = el.element.canRemove();
						if (msg != null)
							throw new UnsupportedOperationException(msg);
						spliterElement.remove();
					}
					el.element.remove();
				}

				@Override
				public String toString() {
					return el.element.toString();
				}
			}
			return new DerivedMutableCollectionElement();
		}

		@Override
		public MutableElementSpliterator<T> spliterator(ElementId element, boolean asNext) {
			DerivedElementHolder<T> el = (DerivedElementHolder<T>) element;
			return new MutableDerivedSpliterator(theDerivedElements.spliterator(el.check().treeNode.getElementId(), asNext));
		}

		/**
		 * @param el The element holder
		 * @return A collection element for the given element in this collection
		 */
		protected CollectionElement<T> elementFor(DerivedElementHolder<T> el) {
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

				@Override
				public String toString() {
					return el.element.toString();
				}
			};
		}

		@Override
		public MutableElementSpliterator<T> spliterator(boolean fromStart) {
			return new MutableDerivedSpliterator(theDerivedElements.spliterator(fromStart));
		}

		/**
		 * @param elementSpliter The element set's spliterator
		 * @return A spliterator for this collection placed at the given spliterator's position
		 */
		protected MutableElementSpliterator<T> spliterator(MutableElementSpliterator<DerivedElementHolder<T>> elementSpliter) {
			return new MutableDerivedSpliterator(elementSpliter);
		}

		@Override
		public String canAdd(T value, ElementId after, ElementId before) {
			return theFlow.canAdd(value, strip(after), strip(before));
		}

		@Override
		public CollectionElement<T> addElement(T value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			DerivedCollectionElement<T> derived = theFlow.addElement(value, strip(after), strip(before), first);
			return derived == null ? null : elementFor(idFromSynthetic(derived));
		}

		private DerivedElementHolder<T> idFromSynthetic(DerivedCollectionElement<T> added) {
			BinaryTreeNode<DerivedElementHolder<T>> found = theDerivedElements.search(//
				holder -> added.compareTo(holder.element), SortedSearchFilter.OnlyMatch);
			return found.get();
		}

		private DerivedCollectionElement<T> strip(ElementId id) {
			return id == null ? null : ((DerivedElementHolder<T>) id).element;
		}

		@Override
		public void clear() {
			if (isEmpty())
				return;
			Causable cause = Causable.simpleCause(null);
			try (Transaction cst = Causable.use(cause); Transaction t = lock(true, cause)) {
				if (!theFlow.clear()) {
					new ArrayList<>(theDerivedElements).forEach(el -> {
						if (el.element.canRemove() == null)
							el.element.remove();
					});
				}
			}
		}

		@Override
		public void setValue(Collection<ElementId> elements, T value) {
			theFlow.setValues(//
				elements.stream().map(el -> ((DerivedElementHolder<T>) el).element).collect(Collectors.toList()), value);
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

		@Override
		protected void finalize() throws Throwable {
			super.finalize();
			theWeakListening.unsubscribe();
		}

		private class MutableDerivedSpliterator extends MutableElementSpliterator.SimpleMutableSpliterator<T> {
			private final MutableElementSpliterator<DerivedElementHolder<T>> theElementSpliter;

			MutableDerivedSpliterator(MutableElementSpliterator<DerivedElementHolder<T>> elementSpliter) {
				super(ActiveDerivedCollection.this);
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
				return theElementSpliter.forValue(//
					element -> action.accept(elementFor(element)), forward);
			}

			@Override
			protected boolean internalForElementM(Consumer<? super MutableCollectionElement<T>> action, boolean forward) {
				return theElementSpliter.forElementM(//
					element -> action.accept(mutableElement(element.get(), element)), forward);
			}

			@Override
			public MutableElementSpliterator<T> trySplit() {
				MutableElementSpliterator<DerivedElementHolder<T>> split = theElementSpliter.trySplit();
				return split == null ? null : new MutableDerivedSpliterator(split);
			}
		}
	}

	/**
	 * An {@link ObservableCollection} whose contents are fixed
	 *
	 * @param <E> The type of values in the collection
	 */
	public static class ConstantCollection<E> implements ObservableCollection<E> {
		private final TypeToken<E> theType;
		private final BetterList<? extends E> theValues;

		ConstantCollection(TypeToken<E> type, BetterList<? extends E> values) {
			theType = type;
			theValues = values;
		}

		@Override
		public Equivalence<? super E> equivalence() {
			return Equivalence.DEFAULT;
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
			return Transaction.NONE;
		}

		@Override
		public long getStamp(boolean structuralOnly) {
			return 0;
		}

		@Override
		public boolean isContentControlled() {
			return true;
		}

		@Override
		public int size() {
			return theValues.size();
		}

		@Override
		public boolean isEmpty() {
			return theValues.isEmpty();
		}

		@Override
		public int getElementsBefore(ElementId id) {
			return theValues.getElementsBefore(id);
		}

		@Override
		public int getElementsAfter(ElementId id) {
			return theValues.getElementsAfter(id);
		}

		@Override
		public CollectionElement<E> getElement(int index) {
			return (CollectionElement<E>) theValues.getElement(index);
		}

		@Override
		public CollectionElement<E> getElement(E value, boolean first) {
			if (!theValues.belongs(value))
				return null;
			return ((BetterList<E>) theValues).getElement(value, first);
		}

		@Override
		public CollectionElement<E> getElement(ElementId id) {
			return (CollectionElement<E>) theValues.getElement(id);
		}

		@Override
		public CollectionElement<E> getTerminalElement(boolean first) {
			return (CollectionElement<E>) theValues.getTerminalElement(first);
		}

		@Override
		public CollectionElement<E> getAdjacentElement(ElementId elementId, boolean next) {
			return (CollectionElement<E>) theValues.getAdjacentElement(elementId, next);
		}

		@Override
		public MutableCollectionElement<E> mutableElement(ElementId id) {
			MutableCollectionElement<? extends E> mutable = theValues.mutableElement(id);
			return mutableElement(mutable);
		}

		private MutableCollectionElement<E> mutableElement(MutableCollectionElement<? extends E> el) {
			return new MutableCollectionElement<E>() {
				@Override
				public BetterCollection<E> getCollection() {
					return ConstantCollection.this;
				}

				@Override
				public ElementId getElementId() {
					return el.getElementId();
				}

				@Override
				public E get() {
					return el.get();
				}

				@Override
				public String isEnabled() {
					return StdMsg.UNSUPPORTED_OPERATION;
				}

				@Override
				public String isAcceptable(E value) {
					return StdMsg.UNSUPPORTED_OPERATION;
				}

				@Override
				public void set(E value) throws UnsupportedOperationException, IllegalArgumentException {
					throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
				}

				@Override
				public String canRemove() {
					return StdMsg.UNSUPPORTED_OPERATION;
				}

				@Override
				public void remove() throws UnsupportedOperationException {
					throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
				}

				@Override
				public String canAdd(E value, boolean before) {
					return StdMsg.UNSUPPORTED_OPERATION;
				}

				@Override
				public ElementId add(E value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
					throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
				}
			};
		}

		@Override
		public String canAdd(E value) {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public CollectionElement<E> addElement(E value, boolean first) {
			return null;
		}

		@Override
		public String canAdd(E value, ElementId after, ElementId before) {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public CollectionElement<E> addElement(E value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			return null;
		}

		@Override
		public MutableElementSpliterator<E> spliterator(boolean fromStart) {
			MutableElementSpliterator<? extends E> split = theValues.spliterator(fromStart);
			return mutableSpliterator(split);
		}

		@Override
		public MutableElementSpliterator<E> spliterator(ElementId element, boolean asNext) {
			MutableElementSpliterator<? extends E> split = theValues.spliterator(element, asNext);
			return mutableSpliterator(split);
		}

		private MutableElementSpliterator<E> mutableSpliterator(MutableElementSpliterator<? extends E> split) {
			return new MutableElementSpliterator<E>() {
				@Override
				public long estimateSize() {
					return split.estimateSize();
				}

				@Override
				public int characteristics() {
					return split.characteristics() | Spliterator.IMMUTABLE;
				}

				@Override
				public long getExactSizeIfKnown() {
					return split.getExactSizeIfKnown();
				}

				@Override
				public Comparator<? super E> getComparator() {
					return (Comparator<? super E>) split.getComparator();
				}

				@Override
				public boolean forElement(Consumer<? super CollectionElement<E>> action, boolean forward) {
					return split.forElement(el -> action.accept((CollectionElement<E>) el), forward);
				}

				@Override
				public void forEachElement(Consumer<? super CollectionElement<E>> action, boolean forward) {
					split.forEachElement(el -> action.accept((CollectionElement<E>) el), forward);
				}

				@Override
				public boolean forElementM(Consumer<? super MutableCollectionElement<E>> action, boolean forward) {
					return split.forElementM(el -> action.accept(mutableElement(el)), forward);
				}

				@Override
				public void forEachElementM(Consumer<? super MutableCollectionElement<E>> action, boolean forward) {
					split.forEachElementM(el -> action.accept(mutableElement(el)), forward);
				}

				@Override
				public MutableElementSpliterator<E> trySplit() {
					MutableElementSpliterator<? extends E> subSplit = split.trySplit();
					return subSplit == null ? null : mutableSpliterator(subSplit);
				}
			};
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
			return () -> {};
		}

		@Override
		public void clear() {}

		@Override
		public void setValue(Collection<ElementId> elements, E value) {
			if (!elements.isEmpty())
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
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
		private final TypeToken<E> theType;
		private final Equivalence<? super E> theEquivalence;

		/**
		 * @param collectionObservable The value to present as a static collection
		 * @param equivalence The equivalence for the collection
		 */
		protected FlattenedValueCollection(ObservableValue<? extends ObservableCollection<? extends E>> collectionObservable,
			Equivalence<? super E> equivalence) {
			theCollectionObservable = collectionObservable;
			theType = (TypeToken<E>) theCollectionObservable.getType().resolveType(ObservableCollection.class.getTypeParameters()[0]);
			theEquivalence = equivalence;
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
			Transaction valueLock = theCollectionObservable.lock();
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			Transaction t = coll == null ? Transaction.NONE : coll.lock(write, structural, cause);
			return () -> {
				t.close();
				valueLock.close();
			};
		}

		@Override
		public long getStamp(boolean structuralOnly) {
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			// TODO Add stamps to ObservableValue?
			return coll == null ? -1 : coll.getStamp(structuralOnly);
		}

		@Override
		public boolean isContentControlled() {
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			return coll == null || coll.isContentControlled();
		}


		@Override
		public Equivalence<? super E> equivalence() {
			return theEquivalence;
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
				throw new NoSuchElementException();
			return ((ObservableCollection<E>) current).getElement(id);
		}

		@Override
		public CollectionElement<E> getTerminalElement(boolean first) {
			ObservableCollection<? extends E> current = getWrapped().get();
			if (current == null)
				return null;
			return ((ObservableCollection<E>) current).getTerminalElement(first);
		}

		@Override
		public CollectionElement<E> getAdjacentElement(ElementId elementId, boolean next) {
			ObservableCollection<? extends E> current = getWrapped().get();
			if (current == null)
				throw new NoSuchElementException();
			return ((ObservableCollection<E>) current).getAdjacentElement(elementId, next);
		}

		@Override
		public MutableCollectionElement<E> mutableElement(ElementId id) {
			ObservableCollection<? extends E> current = getWrapped().get();
			if (current == null)
				throw new NoSuchElementException();
			return ((ObservableCollection<E>) current).mutableElement(id);
		}

		@Override
		public String canAdd(E value, ElementId after, ElementId before) {
			ObservableCollection<? extends E> current = theCollectionObservable.get();
			if (current == null)
				return MutableCollectionElement.StdMsg.UNSUPPORTED_OPERATION;
			else if (value != null && !current.getType().wrap().getRawType().isInstance(value))
				return MutableCollectionElement.StdMsg.BAD_TYPE;
			return ((ObservableCollection<E>) current).canAdd(value, after, before);
		}

		@Override
		public CollectionElement<E> addElement(E value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			if (coll == null)
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			if (value != null && !coll.getType().wrap().getRawType().isInstance(value))
				throw new IllegalArgumentException(MutableCollectionElement.StdMsg.BAD_TYPE);
			return ((ObservableCollection<E>) coll).addElement(value, after, before, first);
		}

		@Override
		public void clear() {
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			if (coll != null)
				coll.clear();
		}

		@Override
		public void setValue(Collection<ElementId> elements, E value) {
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			if (coll != null)
				((ObservableCollection<E>) coll).setValue(elements, value);
			else if (!elements.isEmpty())
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public MutableElementSpliterator<E> spliterator(boolean fromStart) {
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			if (coll == null)
				return MutableElementSpliterator.empty();
			return ((ObservableCollection<E>) coll).spliterator(fromStart);
		}

		@Override
		public MutableElementSpliterator<E> spliterator(ElementId id, boolean asNext) {
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			if (coll == null)
				throw new NoSuchElementException();
			return ((ObservableCollection<E>) coll).spliterator(id, asNext);
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
			return ObservableCollectionImpl.defaultOnChange(this, observer);
		}

		@Override
		public CollectionSubscription subscribe(Consumer<? super ObservableCollectionEvent<? extends E>> observer, boolean forward) {
			CollectionSubscription[] collectSub = new CollectionSubscription[1];
			Subscription valueSub = theCollectionObservable.changes()
				.subscribe(new Observer<ObservableValueEvent<? extends ObservableCollection<? extends E>>>() {
					@Override
					public <V extends ObservableValueEvent<? extends ObservableCollection<? extends E>>> void onNext(V value) {
						if (!value.isInitial() && value.getOldValue() == value.getNewValue())
							return;
						if (collectSub[0] != null) {
							collectSub[0].unsubscribe(true);
							collectSub[0] = null;
						}
						// Only honor the forward parameter for the initial value
						boolean subscribeForward = value.isInitial() ? forward : true;
						if (value.getNewValue() != null)
							collectSub[0] = value.getNewValue().subscribe(observer, subscribeForward);
					}

					@Override
					public <V extends ObservableValueEvent<? extends ObservableCollection<? extends E>>> void onCompleted(V value) {
						if (collectSub[0] != null) {
							collectSub[0].unsubscribe(true);
							collectSub[0] = null;
						}
					}
				});
			return removeAll -> {
				valueSub.unsubscribe();
				if (collectSub[0] != null) {
					collectSub[0].unsubscribe(removeAll);
					collectSub[0] = null;
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
