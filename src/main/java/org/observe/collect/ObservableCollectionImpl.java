package org.observe.collect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.observe.Equivalence;
import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.Subscription;
import org.observe.collect.ObservableCollectionActiveManagers.ActiveCollectionManager;
import org.observe.collect.ObservableCollectionActiveManagers.CollectionElementListener;
import org.observe.collect.ObservableCollectionActiveManagers.DerivedCollectionElement;
import org.observe.collect.ObservableCollectionActiveManagers.ElementAccepter;
import org.observe.collect.ObservableCollectionDataFlowImpl.FilterMapResult;
import org.observe.collect.ObservableCollectionPassiveManagers.PassiveCollectionManager;
import org.observe.util.ObservableUtils;
import org.observe.util.TypeTokens;
import org.observe.util.WeakListening;
import org.qommons.ArrayUtils;
import org.qommons.BiTuple;
import org.qommons.Causable;
import org.qommons.Causable.CausableKey;
import org.qommons.ConcurrentHashSet;
import org.qommons.Identifiable;
import org.qommons.Identifiable.AbstractIdentifiable;
import org.qommons.IdentityKey;
import org.qommons.Lockable;
import org.qommons.QommonsUtils;
import org.qommons.Ternian;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.ValueHolder;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterCollections;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterSortedList;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.ListenerList;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.debug.Debug;
import org.qommons.debug.Debug.DebugData;
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
	 * @param excluded A boolean flag that will be set to true if any elements in the second are excluded as not belonging to the
	 *        BetterCollection
	 * @return The set
	 */
	public static <E> Set<E> toSet(BetterCollection<E> collection, Equivalence<? super E> equiv, Collection<?> c, boolean[] excluded) {
		try (Transaction t = Transactable.lock(c, false, null)) {
			Set<E> set = equiv.createSet();
			for (Object value : c) {
				if (collection.belongs(value))
					set.add((E) value);
				else if (excluded != null)
					excluded[0] = true;
			}
			return set;
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
	public static class CollectionChangesObservable<E> extends AbstractIdentifiable implements Observable<CollectionChangeEvent<E>> {
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
		private boolean isFiring;

		/** @param coll The collection for this change observable to watch */
		protected CollectionChangesObservable(ObservableCollection<E> coll) {
			collection = coll;
			DebugData d=Debug.d().debug(coll);
			if(d.isActive())
				Debug.d().debug(this, true).merge(d);
		}

		@Override
		public Subscription subscribe(Observer<? super CollectionChangeEvent<E>> observer) {
			return subscribe(observer, null);
		}

		/**
		 * Subscribes to this changes observable, with the option to flush accumulated changes on unsubscribe
		 *
		 * @param observer The observer to receive batch change events
		 * @param flushOnUnsubscribe A boolean array, the first element of which determines whether, when the subscription is unsubscribed,
		 *        this observable will flush accumulated changes to the observer.
		 * @return The subscription to cease notification
		 */
		public Subscription subscribe(Observer<? super CollectionChangeEvent<E>> observer, boolean[] flushOnUnsubscribe) {
			Map<Object, Object>[] currentData = new Map[1];
			Causable[] currentCause = new Causable[1];
			boolean[] subscribed = new boolean[] { true };
			Causable.CausableKey key = Causable.key((cause, data) -> {
				if (!subscribed[0])
					return;
				SessionChangeTracker<E> tracker = (SessionChangeTracker<E>) data.get(SESSION_TRACKER_PROPERTY);
				debug(//
					s -> s.append("Transaction end, flushing ").append(tracker));
				fireEventsFromSessionData(tracker, cause, observer);
				data.remove(SESSION_TRACKER_PROPERTY);
				currentData[0] = null;
				currentCause[0] = null;
			});
			Subscription collSub = collection.onChange(evt -> {
				if (isFiring)
					throw new ListenerList.ReentrantNotificationException(ObservableCollection.REENTRANT_EVENT_ERROR);
				Causable cause = evt.getRootCausable();
				Map<Object, Object> data = cause.onFinish(key);
				Object newTracker = data.compute(SESSION_TRACKER_PROPERTY,
					(k, tracker) -> {
						tracker = accumulate((SessionChangeTracker<E>) tracker, evt, observer);
						return tracker;
					});
				if (newTracker == null) {
					currentData[0] = null;
					currentCause[0] = null;
				} else {
					currentData[0] = data;
					currentCause[0] = cause;
				}
			});
			return () -> {
				collSub.unsubscribe();
				if (flushOnUnsubscribe != null && flushOnUnsubscribe[0]) {
					if (currentData[0] != null) {
						SessionChangeTracker<E> tracker = (SessionChangeTracker<E>) currentData[0].remove(SESSION_TRACKER_PROPERTY);
						debug(//
							s -> s.append("Unsusbcribe, flushing ").append(tracker));
						fireEventsFromSessionData(tracker, currentCause[0], observer);
						currentData[0] = null;
						currentCause[0] = null;
					}
				}
				subscribed[0] = false;
			};
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(collection.getIdentity(), "changes");
		}

		void debug(Consumer<StringBuilder> debugMsg) {
			Debug.DebugData data = Debug.d().debug(this);
			if (!Boolean.TRUE.equals(data.getField("debug")))
				return;
			StringBuilder s = new StringBuilder();
			String debugName = data.getField("name", String.class);
			if (debugName != null)
				s.append(debugName).append('.');
			s.append("simpleChanges(): ");
			debugMsg.accept(s);
			System.out.println(s.toString());
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
			if (tracker == null) {
				debug(s -> s.append("From clean slate, tracking ").append(event));
				return replace(tracker, event, observer);
			}
			SessionChangeTracker<E> newTracker = tracker;
			int changeIndex;
			switch (tracker.type) {
			case add:
				switch (event.getType()) {
				case add:
					debug(s -> s.append("\tAdding ").append(event));
					newTracker = insertAddition(tracker, event);
					break;
				case remove:
					changeIndex = indexForAdd(tracker, collIndex);
					if (changeIndex < tracker.elements.size() && tracker.elements.get(changeIndex).index == collIndex) {
						debug(s -> s.append("\tRemoving add ").append(tracker.elements.get(changeIndex)));
						removeAddition(tracker, changeIndex);
					} else {
						debug(s -> s.append("Remove after adds, flushing ").append(tracker).append(", now tracking ").append(event));
						newTracker = replace(tracker, event, observer);
					}
					break;
				case set:
					changeIndex = indexForAdd(tracker, collIndex);
					if (changeIndex < tracker.elements.size() && tracker.elements.get(changeIndex).index == collIndex) {
						debug(s -> s.append("\tReplacing add ").append(tracker.elements.get(changeIndex)).append(" with ")
							.append(event.getNewValue()));
						tracker.elements.get(changeIndex).newValue = event.getNewValue();
					} else {
						debug(s -> s.append("Set after adds, flushing ").append(tracker).append(", now tracking ").append(event));
						newTracker = replace(tracker, event, observer);
					}
					break;
				}
				break;
			case remove:
				switch (event.getType()) {
				case add:
					changeIndex = indexForAdd(tracker, collIndex);
					if (tracker.elements.size() == 1 && changeIndex == 0 && tracker.elements.get(changeIndex).index == collIndex) {
						ChangeValue<E> changeValue = tracker.elements.get(changeIndex);
						debug(s -> s.append("\tChanging remove ").append(changeValue).append(" to set ").append(event.getNewValue()));
						newTracker = new SessionChangeTracker<>(CollectionChangeType.set);
						changeValue.newValue = event.getNewValue();
						newTracker.elements.add(changeValue);
					} else {
						debug(s -> s.append("Add after removes, flushing ").append(tracker).append(", now tracking ").append(event));
						newTracker = replace(tracker, event, observer);
					}
					break;
				case remove:
					newTracker = insertRemove(tracker, event);
					if (collection.isEmpty() && newTracker.elements.size() > 1) {
						// If the collection is empty, no more elements can be removed and any other change will just call a replace,
						// so there's no more information we can possibly accumulate in this session.
						// Let's preemptively fire the event now.
						debug(s -> s.append("\tCollection cleared, flushing ").append(tracker));
						fireEventsFromSessionData(newTracker, event, observer);
						newTracker = null;
					} else
						debug(s -> s.append("Adding remove element ").append(event));
					break;
				case set:
					debug(s -> s.append("Set after removes, flushing ").append(tracker).append(", now tracking ").append(event));
					newTracker = replace(tracker, event, observer);
					break;
				}
				break;
			case set:
				switch (event.getType()) {
				case add:
					debug(s -> s.append("Add after sets, flushing ").append(tracker).append(", now tracking ").append(event));
					newTracker = replace(tracker, event, observer);
					break;
				case remove:
					if (tracker.elements.size() == 1 && tracker.elements.get(0).index == event.getIndex()) {
						// The single element that was set is now being removed.
						// Replace the tracker with one that removes the old element, not the new one
						newTracker = new SessionChangeTracker<>(CollectionChangeType.remove);
						E oldValue = tracker.elements.get(0).oldValue;
						newTracker.elements.add(new ChangeValue<>(oldValue, oldValue, event.getIndex()));
						debug(s -> s.append("\tSet element removed, replacing ").append(tracker).append(" with remove"));
						return newTracker;
					}
					changeIndex = indexForAdd(tracker, collIndex);
					if (changeIndex < tracker.elements.size() && tracker.elements.get(changeIndex).index == collIndex) {
						newTracker = new SessionChangeTracker<>(CollectionChangeType.remove);
						E oldValue = tracker.elements.get(changeIndex).oldValue;
						newTracker.elements.add(new ChangeValue<>(oldValue, oldValue, event.getIndex()));
						tracker.elements.remove(changeIndex);
						debug(s -> s.append("\tRemove after sets, flushing ").append(tracker).append(", now tracking remove [")
							.append(collIndex).append("] ").append(oldValue));
						fireEventsFromSessionData(tracker, event, observer);
						return newTracker;
					} else {
						debug(s -> s.append("Remove after sets, flushing ").append(tracker).append(", now tracking ").append(event));
						newTracker = replace(tracker, event, observer);
					}
					break;
				case set:
					changeIndex = indexForAdd(tracker, collIndex);
					if (changeIndex < tracker.elements.size() && tracker.elements.get(changeIndex).index == collIndex) {
						debug(s -> s.append("\tReplacing set ").append(tracker.elements.get(changeIndex)).append(" with ")
							.append(event.getNewValue()));
						tracker.elements.get(changeIndex).newValue = event.getNewValue();
					} else {
						tracker.elements.add(changeIndex, new ChangeValue<>(event.getOldValue(), event.getNewValue(), collIndex));
						debug(s -> s.append("\tAdding set ").append(tracker.elements.get(changeIndex)));
					}
					break;
				}
				break;
			}
			return newTracker;
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
			if (isFiring)
				throw new ListenerList.ReentrantNotificationException(ObservableCollection.REENTRANT_EVENT_ERROR);
			List<CollectionChangeEvent.ElementChange<E>> elements = new ArrayList<>(tracker.elements.size());
			for (ChangeValue<E> elChange : tracker.elements)
				elements.add(new CollectionChangeEvent.ElementChange<>(elChange.newValue, elChange.oldValue, elChange.index));
			CollectionChangeEvent<E> evt = new CollectionChangeEvent<>(tracker.type, elements, cause);
			isFiring = true;
			try (Transaction t = evt.use()) {
				observer.onNext(evt);
			} finally {
				isFiring = false;
			}
		}

		@Override
		public boolean isSafe() {
			return collection.isLockSupported();
		}

		@Override
		public Transaction lock() {
			return collection.lock(false, null);
		}

		@Override
		public Transaction tryLock() {
			return collection.tryLock(false, null);
		}

		@Override
		public String toString() {
			return "changes(" + collection.getIdentity() + ")";
		}
	}

	/**
	 * Implements {@link ObservableCollection#only()}
	 *
	 * @param <E> The type of the collection
	 */
	public static class OnlyElement<E> extends AbstractIdentifiable implements SettableElement<E> {
		/** The message rejection message returned for set operations when the collection size is not exactly 1 */
		public static final String COLL_SIZE_NOT_1 = "Collection size is not 1";

		private final ObservableCollection<E> theCollection;

		/** @param collection The collection whose first element to represent */
		public OnlyElement(ObservableCollection<E> collection) {
			theCollection = collection;
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(theCollection.getIdentity(), "only");
		}

		@Override
		public TypeToken<E> getType() {
			return theCollection.getType();
		}

		@Override
		public long getStamp() {
			return theCollection.getStamp();
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
		public Transaction tryLock(boolean write, Object cause) {
			return theCollection.tryLock(write, cause);
		}

		@Override
		public ObservableValue<String> isEnabled() {
			class OnlyEnabled extends AbstractIdentifiable implements ObservableValue<String> {
				@Override
				protected Object createIdentity() {
					return Identifiable.wrap(OnlyElement.this.getIdentity(), "enabled");
				}

				@Override
				public TypeToken<String> getType() {
					return TypeTokens.get().STRING;
				}

				@Override
				public long getStamp() {
					return theCollection.getStamp();
				}

				@Override
				public String get() {
					try (Transaction t = theCollection.lock(false, null)) {
						if (theCollection.size() == 1)
							return theCollection.mutableElement(theCollection.getTerminalElement(true).getElementId()).isEnabled();
						else
							return COLL_SIZE_NOT_1;
					}
				}

				@Override
				public Observable<ObservableValueEvent<String>> noInitChanges() {
					class OnlyEnabledChanges extends AbstractIdentifiable implements Observable<ObservableValueEvent<String>> {
						@Override
						protected Object createIdentity() {
							return Identifiable.wrap(OnlyEnabled.this.getIdentity(), "noInitChanges");
						}

						@Override
						public boolean isSafe() {
							return theCollection.isLockSupported();
						}

						@Override
						public Transaction lock() {
							return theCollection.lock(false, null);
						}

						@Override
						public Transaction tryLock() {
							return theCollection.tryLock(false, null);
						}

						@Override
						public Subscription subscribe(Observer<? super ObservableValueEvent<String>> observer) {
							try (Transaction t = lock()) {
								return theCollection.onChange(new Consumer<ObservableCollectionEvent<? extends E>>() {
									private String theOldMessage = get();

									@Override
									public void accept(ObservableCollectionEvent<? extends E> event) {
										int size = theCollection.size();
										if (size > 2)
											return;
										switch (event.getType()) {
										case add:
											fireNewValue(size, event);
											break;
										case remove:
											if (size != 2)
												fireNewValue(size, event);
											break;
										case set:
											if (size == 1)
												fireNewValue(size, event);
											break;
										}
									}

									private void fireNewValue(int size, Object cause) {
										String message;
										if (size == 1)
											message = theCollection.mutableElement(theCollection.getTerminalElement(true).getElementId())
											.isEnabled();
										else
											message = COLL_SIZE_NOT_1;
										if (Objects.equals(theOldMessage, message))
											return;
										ObservableValueEvent<String> event = createChangeEvent(theOldMessage, message, cause);
										theOldMessage = message;
										try (Transaction evtT = event.use()) {
											observer.onNext(event);
										}
									}
								});
							}
						}
					}
					return new OnlyEnabledChanges();
				}
			}
			return new OnlyEnabled();
		}

		@Override
		public <V extends E> String isAcceptable(V value) {
			try (Transaction t = theCollection.lock(false, null)) {
				if (theCollection.size() == 1)
					return theCollection.mutableElement(theCollection.getTerminalElement(true).getElementId()).isAcceptable(value);
				else
					return COLL_SIZE_NOT_1;
			}
		}

		@Override
		public ElementId getElementId() {
			try (Transaction t = theCollection.lock(false, null)) {
				if (theCollection.size() == 1)
					return theCollection.getTerminalElement(true).getElementId();
				else
					return null;
			}
		}

		@Override
		public E get() {
			try (Transaction t = theCollection.lock(false, null)) {
				if (theCollection.size() == 1)
					return theCollection.getFirst();
				else
					return null;
			}
		}

		@Override
		public <V extends E> E set(V value, Object cause) throws IllegalArgumentException, UnsupportedOperationException {
			try (Transaction t = theCollection.lock(true, cause)) {
				if (theCollection.size() == 1) {
					CollectionElement<E> firstEl = theCollection.getTerminalElement(true);
					E oldValue = firstEl.get();
					theCollection.mutableElement(firstEl.getElementId()).set(value);
					return oldValue;
				} else
					throw new UnsupportedOperationException(COLL_SIZE_NOT_1);
			}
		}

		@Override
		public Observable<ObservableElementEvent<E>> elementChanges() {
			class OnlyElementChanges extends AbstractIdentifiable implements Observable<ObservableElementEvent<E>> {
				@Override
				protected Object createIdentity() {
					return Identifiable.wrap(OnlyElement.this.getIdentity(), "elementChanges");
				}

				@Override
				public boolean isSafe() {
					return theCollection.isLockSupported();
				}

				@Override
				public Transaction lock() {
					return theCollection.lock(false, null);
				}

				@Override
				public Transaction tryLock() {
					return theCollection.tryLock(false, null);
				}

				@Override
				public Subscription subscribe(Observer<? super ObservableElementEvent<E>> observer) {
					class OnlySubscriber implements Consumer<ObservableCollectionEvent<? extends E>> {
						private ElementId theOldElement;
						private E theOldValue;

						{
							ObservableElementEvent<E> event;
							if (theCollection.size() == 1) {
								theOldElement = theCollection.getTerminalElement(true).getElementId();
								theOldValue = theCollection.getElement(theOldElement).get();
								event = createInitialEvent(theOldElement, theOldValue, null);
							} else
								event = createInitialEvent(null, null, null);
							try (Transaction evtT = event.use()) {
								observer.onNext(event);
							}
						}

						@Override
						public void accept(ObservableCollectionEvent<? extends E> event) {
							if (theCollection.size() == 1 || theOldElement != null)
								fireNewValue(event);
						}

						private void fireNewValue(Object cause) {
							ElementId newElement;
							E newValue;
							if (theCollection.size() == 1) {
								newElement = theCollection.getTerminalElement(true).getElementId();
								newValue = theCollection.getFirst();
							} else {
								newElement = null;
								newValue = null;
							}
							ObservableElementEvent<E> event = createChangeEvent(theOldElement, theOldValue, newElement, newValue, cause);
							theOldElement = newElement;
							theOldValue = newValue;
							try (Transaction evtT = event.use()) {
								observer.onNext(event);
							}
						}
					}
					try (Transaction t = lock()) {
						return theCollection.onChange(new OnlySubscriber());
					}
				}
			}
			return new OnlyElementChanges();
		}
	}

	/**
	 * An {@link ObservableElement} whose state reflects the value of an element within a collection whose value matches some condition
	 *
	 * @param <E> The type of the element
	 */
	public static abstract class AbstractObservableElementFinder<E> extends AbstractIdentifiable implements ObservableElement<E> {
		private final ObservableCollection<E> theCollection;
		private final Comparator<CollectionElement<? extends E>> theElementCompare;
		private final Supplier<? extends E> theDefault;
		private final Observable<?> theRefresh;
		private Object theChangesIdentity;

		private ElementId theLastMatch;

		/**
		 * @param collection The collection to find elements in
		 * @param elementCompare A comparator to determine whether to prefer one {@link #test(Object) matching} element over another. When
		 *        <code>elementCompare{@link Comparable#compareTo(Object) compareTo}(el1, el2)<0</code>, el1 will replace el2.
		 * @param def The default value to use when no element matches this finder's condition
		 * @param refresh The observable which, when fired, will cause this value to re-check its elements
		 */
		public AbstractObservableElementFinder(ObservableCollection<E> collection,
			Comparator<CollectionElement<? extends E>> elementCompare, Supplier<? extends E> def, Observable<?> refresh) {
			theCollection = collection;
			theElementCompare = elementCompare;
			if (def != null)
				theDefault = def;
			else
				theDefault = () -> null;
				theRefresh = refresh;
		}

		/** @return The collection that this finder searches elements in */
		protected ObservableCollection<E> getCollection() {
			return theCollection;
		}

		/** @return The ID of the last known element matching this search */
		protected ElementId getLastMatch() {
			return theLastMatch;
		}

		/**
		 * @param value The value of the last known element matching this search
		 * @return Whether to use the element from {@link #get()} or {@link #getElementId()}
		 */
		protected abstract boolean useCachedMatch(E value);

		/** @return The default value supplier for this finder (used when no element in the collection matches the search) */
		protected Supplier<? extends E> getDefault() {
			return theDefault;
		}

		@Override
		public TypeToken<E> getType() {
			return theCollection.getType();
		}

		@Override
		public long getStamp() {
			return theCollection.getStamp();
		}

		@Override
		public ElementId getElementId() {
			try (Transaction t = getCollection().lock(false, null)) {
				if (theLastMatch != null && theLastMatch.isPresent() && useCachedMatch(getCollection().getElement(theLastMatch).get()))
					return theLastMatch;
				ValueHolder<CollectionElement<E>> element = new ValueHolder<>();
				find(el -> element.accept(new SimpleElement(el.getElementId(), el.get())));
				if (element.get() != null)
					return theLastMatch = element.get().getElementId();
				else {
					theLastMatch = null;
					return null;
				}
			}
		}

		@Override
		public E get() {
			try (Transaction t = getCollection().lock(false, null)) {
				if (theLastMatch != null && theLastMatch.isPresent() && useCachedMatch(getCollection().getElement(theLastMatch).get()))
					return getCollection().getElement(theLastMatch).get();
				ValueHolder<CollectionElement<E>> element = new ValueHolder<>();
				find(el -> element.accept(new SimpleElement(el.getElementId(), el.get())));
				if (element.get() != null) {
					theLastMatch = element.get().getElementId();
					return element.get().get();
				} else {
					theLastMatch = null;
					return theDefault.get();
				}
			}
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
			class ElementChanges extends AbstractIdentifiable implements Observable<ObservableElementEvent<E>> {
				@Override
				protected Object createIdentity() {
					if (theChangesIdentity == null)
						theChangesIdentity = Identifiable.wrap(AbstractObservableElementFinder.this.getIdentity(), "elementChanges");
					return theChangesIdentity;
				}

				@Override
				public Subscription subscribe(Observer<? super ObservableElementEvent<E>> observer) {
					try (Transaction t = Lockable.lockAll(theRefresh, Lockable.lockable(theCollection, false, null))) {
						class FinderListener implements Consumer<ObservableCollectionEvent<? extends E>> {
							private SimpleElement theCurrentElement;
							private final Causable.CausableKey theCollectionCauseKey;
							private final Causable.CausableKey theRefreshCauseKey;
							private boolean isChanging;
							private boolean isRefreshNeeded;

							{
								theCollectionCauseKey = Causable.key((cause, data) -> {
									synchronized (this) {
										boolean refresh = isRefreshNeeded;
										isRefreshNeeded = false;
										isChanging = false;
										if (refresh || data.containsKey("re-search")) {
											// Means we need to find the new value in the collection
											doRefresh(cause);
										} else
											setCurrentElement((SimpleElement) data.get("replacement"), cause);
									}
								});
								theRefreshCauseKey = Causable.key((cause, data) -> {
									synchronized (this) {
										if (isChanging)
											return; // We'll do it when the collection finishes changing
										if (!isRefreshNeeded)
											return; // Refresh has already been done
										isRefreshNeeded = false;
										doRefresh(cause);
									}
								});
							}

							@Override
							public void accept(ObservableCollectionEvent<? extends E> evt) {
								Map<Object, Object> causeData = theCollectionCauseKey.getData();
								if (isRefreshNeeded || causeData.containsKey("re-search")) {
									theLastMatch = null;
									return; // We've lost track of the current best and will need to find it again later
								}
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
								boolean refresh;
								if (!isChanging) {
									synchronized (this) {
										isChanging = true;
										refresh = isRefreshNeeded;
									}
								} else
									refresh = false;
								causeData = evt.getRootCausable().onFinish(theCollectionCauseKey);
								if (refresh) {
									theLastMatch = null;
									return;
								}
								if (!matches) {
									// The current element's value no longer matches
									// We need to search for the new value if we don't already know of a better match.
									causeData.remove("replacement");
									causeData.put("re-search", true);
									theLastMatch = null;
								} else {
									if (evt.isFinal()) {
										// The current element has been removed
										// We need to search for the new value if we don't already know of a better match.
										// The signal for this is a null replacement
										causeData.remove("replacement");
										causeData.put("re-search", true);
										theLastMatch = null;
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
											theLastMatch = evt.getElementId();
										} else if (sameElement) {
											// The current best element is removed or replaced with an inferior value. Need to re-search.
											causeData.remove("replacement");
											causeData.put("re-search", true);
											theLastMatch = null;
										}
									}
								}
							}

							synchronized void refresh(Object cause) {
								if (isRefreshNeeded) {
									// We already know, but the last match may have been found manually in the mean time
									theLastMatch = null;
									return;
								} else if (isChanging) {
									// If the collection is also changing, just do the refresh after all the other changes
									theLastMatch = null;
									isRefreshNeeded = true;
								} else if (cause instanceof Causable) {
									isRefreshNeeded = true;
									theLastMatch = null;
									((Causable) cause).getRootCausable().onFinish(theRefreshCauseKey);
								} else {
									doRefresh(cause);
								}
							}

							void doRefresh(Object cause) {
								if (!find(//
									el -> setCurrentElement(new SimpleElement(el.getElementId(), el.get()), cause)))
									setCurrentElement(null, cause);
							}

							void setCurrentElement(SimpleElement element, Object cause) {
								SimpleElement oldElement = theCurrentElement;
								ElementId oldId = oldElement == null ? null : oldElement.getElementId();
								ElementId newId = element == null ? null : element.getElementId();
								E oldVal = oldElement == null ? theDefault.get() : oldElement.get();
								E newVal = element == null ? theDefault.get() : element.get();
								if (Objects.equals(oldId, newId) && oldVal == newVal)
									return;
								theCurrentElement = element;
								theLastMatch = element == null ? null : element.getElementId();
								ObservableElementEvent<E> evt = createChangeEvent(oldId, oldVal, newId, newVal, cause);
								try (Transaction evtT = evt.use()) {
									observer.onNext(evt);
								}
							}
						}
						FinderListener listener = new FinderListener();
						if (!find(el -> {
							listener.theCurrentElement = new SimpleElement(el.getElementId(), el.get());
						}))
							listener.theCurrentElement = null;
						Subscription collSub = theCollection.onChange(listener);
						@SuppressWarnings("resource")
						Subscription refreshSub = theRefresh != null ? theRefresh.act(r -> listener.refresh(r)) : Subscription.NONE;
						ElementId initId = listener.theCurrentElement == null ? null : listener.theCurrentElement.getElementId();
						E initVal = listener.theCurrentElement == null ? theDefault.get() : listener.theCurrentElement.get();
						ObservableElementEvent<E> evt = createInitialEvent(initId, initVal, null);
						try (Transaction evtT = evt.use()) {
							observer.onNext(evt);
						}
						return new Subscription() {
							private boolean isDone;

							@Override
							public void unsubscribe() {
								if (isDone)
									return;
								isDone = true;
								refreshSub.unsubscribe();
								collSub.unsubscribe();
								ElementId endId = listener.theCurrentElement == null ? null : listener.theCurrentElement.getElementId();
								E endVal = listener.theCurrentElement == null ? theDefault.get() : listener.theCurrentElement.get();
								ObservableElementEvent<E> evt2 = createChangeEvent(endId, endVal, endId, endVal, null);
								try (Transaction evtT = evt2.use()) {
									observer.onCompleted(evt2);
								}
							}
						};
					}
				}

				@Override
				public boolean isSafe() {
					return theCollection.isLockSupported();
				}

				@Override
				public Transaction lock() {
					return theCollection.lock(false, null);
				}

				@Override
				public Transaction tryLock() {
					return theCollection.tryLock(false, null);
				}
			}
			;
			return new ElementChanges();
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
	 * Implements {@link ObservableCollection#observeFind(Predicate)}
	 *
	 * @param <E> The type of the value
	 */
	public static class ObservableCollectionFinder<E> extends AbstractObservableElementFinder<E> implements SettableElement<E> {
		private final Predicate<? super E> theTest;
		private final Ternian isFirst;

		/**
		 * @param collection The collection to find elements in
		 * @param test The test to find elements that pass
		 * @param def The default value to use when no passing element is found in the collection
		 * @param first Whether to get the first value in the collection that passes, the last value, or any passing value
		 * @param refresh The observable which, when fired, will cause this value to re-check its elements
		 */
		protected ObservableCollectionFinder(ObservableCollection<E> collection, Predicate<? super E> test, Supplier<? extends E> def,
			Ternian first, Observable<?> refresh) {
			super(collection, (el1, el2) -> {
				if (first == Ternian.NONE)
					return 0;
				int compare = el1.getElementId().compareTo(el2.getElementId());
				if (!first.value)
					compare = -compare;
				return compare;
			}, def, refresh);
			theTest = test;
			isFirst = first;
		}

		@Override
		protected boolean useCachedMatch(E value) {
			return theTest.test(value);
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(getCollection().getIdentity(), "find", theTest, describe(isFirst));
		}

		@Override
		protected boolean find(Consumer<? super CollectionElement<? extends E>> onElement) {
			CollectionElement<E> element = getCollection().find(theTest, isFirst.withDefault(true));
			if (element == null)
				return false;
			else {
				onElement.accept(element);
				return true;
			}
		}

		@Override
		protected boolean test(E value) {
			return theTest.test(value);
		}

		@Override
		public boolean isLockSupported() {
			return getCollection().isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return getCollection().lock(write, cause);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return getCollection().tryLock(write, cause);
		}

		@Override
		public ObservableValue<String> isEnabled() {
			class Enabled extends AbstractIdentifiable implements ObservableValue<String> {
				@Override
				public long getStamp() {
					return getCollection().getStamp();
				}

				@Override
				public TypeToken<String> getType() {
					return TypeTokens.get().STRING;
				}

				@Override
				public String get() {
					String msg = null;
					try (Transaction t = getCollection().lock(false, null)) {
						ElementId lastMatch = getLastMatch();
						if (lastMatch == null || !lastMatch.isPresent() || !theTest.test(getCollection().getElement(lastMatch).get())) {
							lastMatch = getElementId();
						}
						if (lastMatch != null) {
							msg = getCollection().mutableElement(lastMatch).isEnabled();
							if (msg == null)
								return null;
						}
						if (getDefault() != null)
							msg = getCollection().canAdd(getDefault().get(), //
								isFirst == Ternian.FALSE ? lastMatch : null, isFirst == Ternian.TRUE ? lastMatch : null);
					}
					if (msg == null)
						msg = StdMsg.UNSUPPORTED_OPERATION;
					return msg;
				}

				@Override
				public Observable<ObservableValueEvent<String>> noInitChanges() {
					class NoInitChanges extends AbstractIdentifiable implements Observable<ObservableValueEvent<String>> {
						@Override
						protected Object createIdentity() {
							return Identifiable.wrap(Enabled.this.getIdentity(), "noInitChanges");
						}

						@Override
						public Subscription subscribe(Observer<? super ObservableValueEvent<String>> observer) {
							String[] oldValue = new String[] { get() };
							return getCollection().onChange(collEvt -> {
								if (theTest.test(collEvt.getNewValue())//
									|| collEvt.getType() == CollectionChangeType.set && theTest.test(collEvt.getOldValue())) {
									String newValue = get();
									if (!Objects.equals(oldValue[0], newValue)) {
										ObservableValueEvent<String> evt = createChangeEvent(oldValue[0], newValue, collEvt);
										oldValue[0] = newValue;
										try (Transaction evtT = evt.use()) {
											observer.onNext(evt);
										}
									}
								}
							});
						}

						@Override
						public boolean isSafe() {
							return getCollection().isLockSupported();
						}

						@Override
						public Transaction lock() {
							return getCollection().lock(false, null);
						}

						@Override
						public Transaction tryLock() {
							return getCollection().tryLock(false, null);
						}
					}
					return new NoInitChanges();
				}

				@Override
				protected Object createIdentity() {
					return Identifiable.wrap(ObservableCollectionFinder.this.getIdentity(), "enabled");
				}
			}
			return new Enabled();
		}

		@Override
		public <V extends E> String isAcceptable(V value) {
			if (!theTest.test(value))
				return StdMsg.ILLEGAL_ELEMENT;
			String msg = null;
			try (Transaction t = getCollection().lock(false, null)) {
				ElementId lastMatch = getLastMatch();
				if (lastMatch == null || !lastMatch.isPresent() || !theTest.test(getCollection().getElement(lastMatch).get())) {
					lastMatch = getElementId();
				}
				if (lastMatch != null) {
					msg = getCollection().mutableElement(lastMatch).isAcceptable(value);
					if (msg == null)
						return null;
				}
				msg = getCollection().canAdd(value, //
					isFirst == Ternian.FALSE ? lastMatch : null, isFirst == Ternian.TRUE ? lastMatch : null);
				if (msg == null)
					return null;
			}
			return msg;
		}

		@Override
		public <V extends E> E set(V value, Object cause) throws IllegalArgumentException, UnsupportedOperationException {
			if (!theTest.test(value))
				throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
			String msg = null;
			try (Transaction t = getCollection().lock(true, null)) {
				ElementId lastMatch = getLastMatch();
				if (lastMatch == null || !lastMatch.isPresent() || !theTest.test(getCollection().getElement(lastMatch).get())) {
					lastMatch = getElementId();
				}
				if (lastMatch != null) {
					msg = getCollection().mutableElement(lastMatch).isAcceptable(value);
					if (msg == null) {
						E oldValue = getCollection().getElement(lastMatch).get();
						getCollection().mutableElement(lastMatch).set(value);
						return oldValue;
					}
				}
				msg = getCollection().canAdd(value, //
					isFirst == Ternian.FALSE ? lastMatch : null, isFirst == Ternian.TRUE ? lastMatch : null);
				if (msg == null) {
					E oldValue = get();
					getCollection().addElement(value, //
						isFirst == Ternian.FALSE ? lastMatch : null, isFirst == Ternian.TRUE ? lastMatch : null, isFirst == Ternian.TRUE);
					return oldValue;
				}
			}
			if (msg == null || msg.equals(StdMsg.UNSUPPORTED_OPERATION))
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			throw new IllegalArgumentException(msg);
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
			}, () -> null, null);
			theValue = value;
			isFirst = first;
		}

		@Override
		protected boolean useCachedMatch(E value) {
			return getCollection().equivalence().elementEquals(value, theValue);
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(getCollection().getIdentity(), "equivalent", theValue, isFirst ? "first" : "last");
		}

		@Override
		protected boolean find(Consumer<? super CollectionElement<? extends E>> onElement) {
			CollectionElement<E> element = getCollection().getElement(theValue, isFirst);
			if (element == null)
				return false;
			else {
				onElement.accept(element);
				return true;
			}
		}

		@Override
		protected boolean test(E value) {
			return getCollection().equivalence().elementEquals(theValue, value);
		}
	}

	static String describe(Ternian first) {
		String str = null;
		switch (first) {
		case FALSE:
			str = "last";
			break;
		case NONE:
			str = "any";
			break;
		case TRUE:
			str = "first";
			break;
		}
		return str;
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
		 * @param refresh The observable which, when fired, will cause this value to re-check its elements
		 */
		public BestCollectionElement(ObservableCollection<E> collection, Comparator<? super E> compare, Supplier<? extends E> def,
			Ternian first, Observable<?> refresh) {
			super(collection, (el1, el2) -> {
				int comp = compare.compare(el1.get(), el2.get());
				if (comp == 0 && first.value != null) {
					comp = el1.getElementId().compareTo(el2.getElementId());
					if (!first.value)
						comp = -comp;
				}
				return comp;
			}, def, refresh);
			theCompare = compare;
			isFirst = first;
		}

		@Override
		protected boolean useCachedMatch(E value) {
			return false; // Can't be sure that the cached match is the best one
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(getCollection().getIdentity(), "best", theCompare, describe(isFirst));
		}

		@Override
		protected boolean find(Consumer<? super CollectionElement<? extends E>> onElement) {
			boolean first = isFirst.withDefault(true);
			CollectionElement<E> best = null;
			CollectionElement<E> el = getCollection().getTerminalElement(first);
			while (el != null) {
				if (best == null || theCompare.compare(el.get(), best.get()) < 0)
					best = el;
				el = getCollection().getAdjacentElement(el.getElementId(), first);
			}
			if (best != null) {
				onElement.accept(best);
				return true;
			} else
				return false;
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
	public static abstract class ReducedValue<E, X, T> extends AbstractIdentifiable implements ObservableValue<T> {
		private final ObservableCollection<E> theCollection;
		private final TypeToken<T> theDerivedType;
		private Object theChangesIdentity;

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
		public long getStamp() {
			return theCollection.getStamp();
		}

		@Override
		public Observable<ObservableValueEvent<T>> noInitChanges() {
			return new Observable<ObservableValueEvent<T>>() {
				@Override
				public Object getIdentity() {
					if (theChangesIdentity == null)
						theChangesIdentity = Identifiable.wrap(ReducedValue.this.getIdentity(), "noInitChanges");
					return theChangesIdentity;
				}

				@Override
				public boolean isSafe() {
					return theCollection.isLockSupported();
				}

				@Override
				public Subscription subscribe(Observer<? super ObservableValueEvent<T>> observer) {
					ValueHolder<X> x = new ValueHolder<>();
					ValueHolder<T> value = new ValueHolder<>();
					boolean[] init = new boolean[1];
					Causable.CausableKey key = Causable.key((root, values) -> {
						T oldV = value.get();
						T v = getValue((X) values.get("x"));
						value.accept(v);
						if (init[0])
							fireChangeEvent(oldV, v, root, observer::onNext);
					});
					Subscription sub;
					try (Transaction t = theCollection.lock(false, null)) {
						x.accept(init());
						value.accept(getValue(x.get()));
						sub = theCollection.onChange(evt -> {
							X newX = update(x.get(), evt);
							x.accept(newX);

							Map<Object, Object> values = evt.getRootCausable().onFinish(key);
							values.put("x", newX);
						});
						init[0] = true;
					}
					return sub;
				}

				@Override
				public Transaction lock() {
					return theCollection.lock(false, null);
				}

				@Override
				public Transaction tryLock() {
					return theCollection.tryLock(false, null);
				}
			};
		}

		@Override
		public T get() {
			return getValue(getCurrent());
		}

		/** @return The initial computation value */
		protected abstract X init();

		/** @return The computation value for the collection's current state */
		protected X getCurrent() {
			try (Transaction t = theCollection.lock(false, null)) {
				X value = init();
				CollectionElement<E> el = getCollection().getTerminalElement(true);
				int index = 0;
				while (el != null) {
					value = update(value, new ObservableCollectionEvent<>(el.getElementId(), getCollection().getType(), index++, //
						CollectionChangeType.add, null, el.get()));
					el = getCollection().getAdjacentElement(el.getElementId(), true);
				}
				return value;
			}
		}

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
				for (E e : left)
					modify(e, true, true, null);
				for (X x : right) {
					if (leftEquiv.isElement(x))
						modify((E) x, true, false, null);
				}

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
	public abstract static class IntersectionValue<E, X> extends AbstractIdentifiable implements ObservableValue<Boolean> {
		private final ObservableCollection<E> theLeft;
		private final ObservableCollection<X> theRight;
		private final Predicate<ValueCounts<E, X>> theSatisfiedCheck;
		private Object theChangesIdentity;

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
		public long getStamp() {
			return theLeft.getStamp() ^ Long.rotateRight(theRight.getStamp(), 32);
		}

		@Override
		public Observable<ObservableValueEvent<Boolean>> changes() {
			return new Observable<ObservableValueEvent<Boolean>>() {
				@Override
				public Object getIdentity() {
					if (theChangesIdentity == null)
						theChangesIdentity = Identifiable.wrap(IntersectionValue.this.getIdentity(), "changes");
					return theChangesIdentity;
				}

				@Override
				public boolean isSafe() {
					return theLeft.isLockSupported() && theRight.isLockSupported();
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
						satisfied[0] = theSatisfiedCheck.test(counts);
						ObservableValueEvent<Boolean> evt = createInitialEvent(satisfied[0], null);
						try (Transaction t = evt.use()) {
							observer.onNext(evt);
						}
					});
				}

				@Override
				public Transaction lock() {
					return Lockable.lockAll(Lockable.lockable(theLeft), Lockable.lockable(theRight));
				}

				@Override
				public Transaction tryLock() {
					return Lockable.tryLockAll(Lockable.lockable(theLeft), Lockable.lockable(theRight));
				}
			};
		}

		@Override
		public Observable<ObservableValueEvent<Boolean>> noInitChanges() {
			return changes().noInit();
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

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(getLeft().getIdentity(), "contains", theValue.getIdentity());
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
		protected Object createIdentity() {
			return Identifiable.wrap(getLeft().getIdentity(), "containsAll", getRight().getIdentity());
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
		protected Object createIdentity() {
			return Identifiable.wrap(getLeft().getIdentity(), "containsAny", getRight().getIdentity());
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
			if (BetterCollections.simplifyDuplicateOperations())
				return getWrapped();
			else
				return ObservableCollection.super.reverse();
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
				try (Transaction t = reversed.use()) {
					theObserver.accept(reversed);
				}
			}
		}
	}

	/**
	 * An ObservableCollection derived from one or more source ObservableCollections
	 *
	 * @param <T> The type of elements in the collection
	 */
	public static interface DerivedCollection<T> extends ObservableCollection<T> {}

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
	public static class PassiveDerivedCollection<E, T> extends AbstractIdentifiable implements DerivedCollection<T> {
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

		/** @return Whether this collection's element order is the reverse of its source */
		protected boolean isReversed() {
			return isReversed;
		}

		@Override
		protected Object createIdentity() {
			return theFlow.getIdentity();
		}

		@Override
		public boolean isContentControlled() {
			return theFlow.isContentControlled();
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
			return theSource.isLockSupported() && theFlow.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return Lockable.lockAll(Lockable.lockable(theSource, write, cause), Lockable.lockable(theFlow, write, cause));
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return Lockable.tryLockAll(Lockable.lockable(theSource, write, cause), Lockable.lockable(theFlow, write, cause));
		}

		@Override
		public long getStamp() {
			return theFlow.getStamp();
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

		@Override
		public String canAdd(T value, ElementId after, ElementId before) {
			FilterMapResult<T, E> reversed = theFlow.reverse(value, true, true);
			if (!reversed.isAccepted())
				return reversed.getRejectReason();
			if (isReversed) {
				ElementId temp = mapId(after);
				after = mapId(before);
				before = temp;
			}
			return theSource.canAdd(reversed.result, after, before);
		}

		@Override
		public CollectionElement<T> addElement(T value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			try (Transaction t = lock(true, null)) {
				// Lock so the reversed value is consistent until it is added
				FilterMapResult<T, E> reversed = theFlow.reverse(value, true, false);
				if (reversed.throwIfError(IllegalArgumentException::new) != null)
					return null;
				if (isReversed) {
					ElementId temp = mapId(after);
					after = mapId(before);
					before = temp;
					first = !first;
				}
				CollectionElement<E> srcEl = theSource.addElement(reversed.result, after, before, first);
				return srcEl == null ? null : elementFor(srcEl, null);
			}
		}

		@Override
		public String canMove(ElementId valueEl, ElementId after, ElementId before) {
			String msg = theFlow.canMove();
			if (msg != null) {
				if ((after == null || valueEl.compareTo(after) >= 0) && (before == null || valueEl.compareTo(before) <= 0))
					return null;
				return msg;
			}
			if (isReversed) {
				ElementId temp = mapId(after);
				after = mapId(before);
				before = temp;
			}
			return theSource.canMove(mapId(valueEl), after, before);
		}

		@Override
		public CollectionElement<T> move(ElementId valueEl, ElementId after, ElementId before, boolean first, Runnable afterRemove)
			throws UnsupportedOperationException, IllegalArgumentException {
			String msg = theFlow.canMove();
			if (msg != null && (after == null || valueEl.compareTo(after) >= 0) && (before == null || valueEl.compareTo(before) <= 0))
				return getElement(valueEl);
			if (StdMsg.UNSUPPORTED_OPERATION.equals(msg))
				throw new UnsupportedOperationException(msg);
			else if (msg != null)
				throw new IllegalArgumentException(msg);
			if (isReversed) {
				ElementId temp = mapId(after);
				after = mapId(before);
				before = temp;
				first = !first;
			}
			return elementFor(theSource.move(mapId(valueEl), after, before, first, afterRemove), null);
		}

		@Override
		public void clear() {
			if (!theFlow.isRemoveFiltered())
				theSource.clear();
			else {
				boolean reverse = isReversed;
				try (Transaction t = lock(true, null)) {
					ElementId lastStatic = null;
					Function<? super E, ? extends T> map = theFlow.map().get();
					CollectionElement<E> el = theSource.getTerminalElement(reverse);
					while (el != null) {
						MutableCollectionElement<E> mutable = theSource.mutableElement(el.getElementId());
						if (theFlow.map(mutable, map).canRemove() == null)
							mutable.remove();
						else
							lastStatic = el.getElementId();
						el = lastStatic == null ? theSource.getTerminalElement(reverse) : theSource.getAdjacentElement(lastStatic, reverse);
					}
				}
			}
		}

		@Override
		public void setValue(Collection<ElementId> elements, T value) {
			try (Transaction t = lock(true, null)) {
				Function<? super E, ? extends T> map = theFlow.map().get();
				theFlow.setValue(//
					elements.stream().map(el -> theFlow.map(theSource.mutableElement(mapId(el)), map)).collect(Collectors.toList()), value);
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
				boolean forward = first ^ isReversed;
				if (!theFlow.isManyToOne()) {
					// If the flow is one-to-one, we can use any search optimizations the source collection may be capable of
					FilterMapResult<T, E> reversed = theFlow.reverse(value, false, true);
					if (!reversed.isError() && equivalence().elementEquals(map.apply(reversed.result), value)) {
						CollectionElement<E> srcEl = theSource.getElement(reversed.result, forward);
						return srcEl == null ? null : elementFor(srcEl, null);
					}
				}
				CollectionElement<E> el = theSource.getTerminalElement(forward);
				while (el != null) {
					if (equivalence().elementEquals(map.apply(el.get()), value))
						return elementFor(el, map);
					el = theSource.getAdjacentElement(el.getElementId(), forward);
				}
				return null;
			}
		}

		@Override
		public CollectionElement<T> getElement(ElementId id) {
			return elementFor(theSource.getElement(mapId(id)), null);
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
			return mutableElementFor(theSource.mutableElement(mapId(id)), null);
		}

		@Override
		public BetterList<CollectionElement<T>> getElementsBySource(ElementId sourceEl, BetterCollection<?> sourceCollection)
			throws NoSuchElementException {
			if (isReversed)
				sourceEl = sourceEl.reverse();
			if (sourceCollection == this)
				sourceCollection = theSource;
			return QommonsUtils.map2(theSource.getElementsBySource(sourceEl, sourceCollection), el -> elementFor(el, null));
		}

		@Override
		public BetterList<ElementId> getSourceElements(ElementId localElement, BetterCollection<?> sourceCollection) {
			if (isReversed)
				localElement = localElement.reverse();
			if (sourceCollection == this)
				return QommonsUtils.map2(theSource.getSourceElements(localElement, theSource), this::mapId);
			return theSource.getSourceElements(localElement, sourceCollection);
		}

		@Override
		public ElementId getEquivalentElement(ElementId equivalentEl) {
			if (isReversed)
				equivalentEl = equivalentEl.reverse();
			ElementId found = theSource.getEquivalentElement(equivalentEl);
			if (isReversed)
				found = ElementId.reverse(found);
			return found;
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

				@Override
				public int hashCode() {
					return getElementId().hashCode();
				}

				@Override
				public boolean equals(Object obj) {
					return obj instanceof CollectionElement && getElementId().equals(((CollectionElement<?>) obj).getElementId());
				}

				@Override
				public String toString() {
					return new StringBuilder("[").append(getElementsBefore(getElementId())).append("]: ").append(get()).toString();
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
				public int hashCode() {
					return getElementId().hashCode();
				}

				@Override
				public boolean equals(Object obj) {
					return obj instanceof MutableCollectionElement && getElementId().equals(((CollectionElement<?>) obj).getElementId());
				}

				@Override
				public String toString() {
					return flowEl.toString();
				}
			}
			return new PassiveMutableElement();
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
						CollectionElement<E> el = theSource.getTerminalElement(!isReversed);
						int index = 0;
						while (el != null) {
							E sourceVal = el.get();
							ObservableCollectionEvent<? extends T> evt2 = new ObservableCollectionEvent<>(mapId(el.getElementId()),
								getType(), index++, CollectionChangeType.set, evt.getOldValue().apply(sourceVal),
								currentMap[0].apply(sourceVal), evt);
							try (Transaction evtT = evt2.use()) {
								observer.accept(evt2);
							}
							el = theSource.getAdjacentElement(el.getElementId(), !isReversed);
						}
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
								ObservableCollectionEvent<? extends T> evt2 = new ObservableCollectionEvent<>(mapId(evt.getElementId()),
									getType(), index, evt.getType(), oldValue, newValue, evt);
								try (Transaction evtT = evt2.use()) {
									observer.accept(evt2);
								}
							}
						}
					});
				}
			}
			return Subscription.forAll(sourceSub, mapSub);
		}

		@Override
		public int hashCode() {
			return BetterCollection.hashCode(this);
		}

		@Override
		public boolean equals(Object obj) {
			return BetterCollection.equals(this, obj);
		}

		@Override
		public String toString() {
			return BetterCollection.toString(this);
		}
	}

	/**
	 * Stores strong references to actively-derived collections on which listeners are installed, preventing the garbage-collection of these
	 * collections since the listeners only contain weak references to their data sources.
	 */
	private static final Set<IdentityKey<ActiveDerivedCollection<?>>> STRONG_REFS = new ConcurrentHashSet<>();

	/**
	 * A derived collection, {@link ObservableCollection.CollectionDataFlow#collect() collected} from a
	 * {@link ObservableCollection.CollectionDataFlow}. An active collection maintains a sorted collection of derived elements, enabling
	 * complex and order-affecting operations like {@link ObservableCollection.CollectionDataFlow#sorted(Comparator) sort} and
	 * {@link ObservableCollection.CollectionDataFlow#distinct() distinct}.
	 *
	 * @param <T> The type of values in the collection
	 */
	public static class ActiveDerivedCollection<T> extends AbstractIdentifiable implements DerivedCollection<T> {
		/**
		 * Holds a {@link ObservableCollectionActiveManagers.DerivedCollectionElement}s for an {@link ActiveDerivedCollection}
		 *
		 * @param <T> The type of the collection
		 */
		protected static class DerivedElementHolder<T> implements ElementId {
			/** The element from the flow */
			protected final ObservableCollectionActiveManagers.DerivedCollectionElement<T> element;
			BinaryTreeNode<DerivedElementHolder<T>> treeNode;

			/** @param element The element from the flow */
			protected DerivedElementHolder(ObservableCollectionActiveManagers.DerivedCollectionElement<T> element) {
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
					throw new IllegalStateException("This node is not currently present in the collection");
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

			// Begin listening
			ElementAccepter<T> onElement = (el, cause) -> {
				theModCount.incrementAndGet();
				DerivedElementHolder<T>[] holder = new DerivedElementHolder[] { createHolder(el) };
				holder[0].treeNode = theDerivedElements.addElement(holder[0], false);
				if (holder[0].treeNode == null)
					throw new IllegalStateException("Element already exists: " + holder[0]);
				fireListeners(new ObservableCollectionEvent<>(holder[0], theFlow.getTargetType(), holder[0].treeNode.getNodesBefore(),
					CollectionChangeType.add, null, el.get(), cause));
				el.setListener(new CollectionElementListener<T>() {
					@Override
					public void update(T oldValue, T newValue, Object elCause) {
						theModCount.incrementAndGet();
						BinaryTreeNode<DerivedElementHolder<T>> left = holder[0].treeNode.getClosest(true);
						BinaryTreeNode<DerivedElementHolder<T>> right = holder[0].treeNode.getClosest(false);
						if ((left != null && left.get().element.compareTo(holder[0].element) > 0)
							|| (right != null && right.get().element.compareTo(holder[0].element) < 0)) {
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
							fireListeners(new ObservableCollectionEvent<>(holder[0], getType(), holder[0].treeNode.getNodesBefore(),
								CollectionChangeType.set, oldValue, newValue, elCause));
						}
					}

					@Override
					public void removed(T value, Object elCause) {
						theModCount.incrementAndGet();
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
		protected DerivedElementHolder<T> createHolder(ObservableCollectionActiveManagers.DerivedCollectionElement<T> el) {
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
			try (Transaction t = event.use()) {
				theListeners.forEach(//
					listener -> listener.accept(event));
			}
		}

		@Override
		protected Object createIdentity() {
			return theFlow.getIdentity();
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
			return onChange(observer, true);
		}

		/**
		 * Allows adding a listener to this collection without creating a persistent strong reference to keep it alive
		 *
		 * @param observer The listener for changes to this collection
		 * @param withStrongRef Whether to install a strong reference to this collection to keep it alive while the listener is active, in
		 *        case no strong reference to the actual collection (or the subscription returned from this method) is kept
		 * @return A subscription to uninstall the listener
		 */
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends T>> observer, boolean withStrongRef) {
			Runnable remove = theListeners.add(observer, true);
			// Add a strong reference to this collection while we have listeners.
			// Otherwise, this collection could be GC'd and listeners (which may not reference this collection) would just be left hanging
			if (withStrongRef && theListenerCount.getAndIncrement() == 0)
				STRONG_REFS.add(new IdentityKey<>(this));
			return () -> {
				remove.run();
				if (withStrongRef && theListenerCount.decrementAndGet() == 0)
					STRONG_REFS.remove(new IdentityKey<>(this));
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
		public Transaction lock(boolean write, Object cause) {
			return theFlow.lock(write, cause);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return theFlow.tryLock(write, cause);
		}

		@Override
		public long getStamp() {
			return theModCount.get();
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
				Comparable<ObservableCollectionActiveManagers.DerivedCollectionElement<T>> finder = getFlow().getElementFinder(value);
				if (finder != null) {
					BinaryTreeNode<DerivedElementHolder<T>> found = theDerivedElements.search(holder -> finder.compareTo(holder.element), //
						BetterSortedList.SortedSearchFilter.of(first, false));
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
		public BetterList<CollectionElement<T>> getElementsBySource(ElementId sourceEl, BetterCollection<?> sourceCollection)
			throws NoSuchElementException {
			try (Transaction t = lock(false, null)) {
				if (sourceCollection == this)
					return BetterList.of(getElement(sourceEl));

				return BetterList.of(theFlow.getElementsBySource(sourceEl, sourceCollection).stream().map(el -> {
					DerivedElementHolder<T> found = theDerivedElements.searchValue(de -> el.compareTo(de.element),
						BetterSortedList.SortedSearchFilter.OnlyMatch);
					if (found == null) {
						// This may happen if a listener for the source collection calls this method
						// before the element has been added to this collection
						return null;
					}
					return elementFor(found);
				}).filter(el -> el != null));
			}
		}

		@Override
		public BetterList<ElementId> getSourceElements(ElementId localElement, BetterCollection<?> sourceCollection) {
			if (sourceCollection == this)
				return BetterList.of(getElement(localElement).getElementId()); // Verify that it's actually our element
			try (Transaction t = lock(false, null)) {
				return theFlow.getSourceElements(((DerivedElementHolder<T>) localElement).element, sourceCollection);
			}
		}

		@Override
		public ElementId getEquivalentElement(ElementId equivalentEl) {
			if (!(equivalentEl instanceof DerivedElementHolder))
				return null;
			DerivedElementHolder<?> holder=(DerivedElementHolder<?>) equivalentEl;
			ElementId local=theDerivedElements.getEquivalentElement(holder.treeNode.getElementId());
			if(local==null)
				return equivalentEl;
			DerivedCollectionElement<T> found = theFlow.getEquivalentElement(holder.element);
			return found == null ? null : idFromSynthetic(found);
		}

		/**
		 * @param el The element holder
		 * @return A collection element for the given element in this collection
		 */
		protected CollectionElement<T> elementFor(DerivedElementHolder<T> el) {
			el.check();
			return new ActiveDerivedElement<>(el);
		}

		static class ActiveDerivedElement<T> implements CollectionElement<T> {
			private final DerivedElementHolder<T> element;

			ActiveDerivedElement(DerivedElementHolder<T> element) {
				this.element = element;
			}

			@Override
			public T get() {
				return element.get();
			}

			@Override
			public ElementId getElementId() {
				return element;
			}

			@Override
			public int hashCode() {
				return element.treeNode.hashCode();
			}

			@Override
			public boolean equals(Object o) {
				return o instanceof ActiveDerivedElement && element.treeNode.equals(((ActiveDerivedElement<?>) o).element.treeNode);
			}

			@Override
			public String toString() {
				return element.element.toString();
			}
		};
		@Override
		public String canAdd(T value, ElementId after, ElementId before) {
			return theFlow.canAdd(value, //
				strip(after), strip(before));
		}

		@Override
		public CollectionElement<T> addElement(T value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			ObservableCollectionActiveManagers.DerivedCollectionElement<T> derived = theFlow.addElement(value, //
				strip(after), strip(before), first);
			return derived == null ? null : elementFor(idFromSynthetic(derived));
		}

		@Override
		public String canMove(ElementId valueEl, ElementId after, ElementId before) {
			return theFlow.canMove(//
				strip(valueEl), strip(after), strip(before));
		}

		@Override
		public CollectionElement<T> move(ElementId valueEl, ElementId after, ElementId before, boolean first, Runnable afterRemove)
			throws UnsupportedOperationException, IllegalArgumentException {
			ObservableCollectionActiveManagers.DerivedCollectionElement<T> derived = theFlow.move(//
				strip(valueEl), strip(after), strip(before), first, afterRemove);
			return elementFor(idFromSynthetic(derived));
		}

		private DerivedElementHolder<T> idFromSynthetic(ObservableCollectionActiveManagers.DerivedCollectionElement<T> added) {
			BinaryTreeNode<DerivedElementHolder<T>> found = theDerivedElements.search(//
				holder -> added.compareTo(holder.element), BetterSortedList.SortedSearchFilter.OnlyMatch);
			return found.get();
		}

		private ObservableCollectionActiveManagers.DerivedCollectionElement<T> strip(ElementId id) {
			return id == null ? null : ((DerivedElementHolder<T>) id).element;
		}

		@Override
		public void clear() {
			if (isEmpty())
				return;
			Causable cause = Causable.simpleCause();
			try (Transaction cst = cause.use(); Transaction t = lock(true, cause)) {
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
			return BetterCollection.hashCode(this);
		}

		@Override
		public boolean equals(Object obj) {
			return BetterCollection.equals(this, obj);
		}

		@Override
		public String toString() {
			return BetterCollection.toString(this);
		}

		@Override
		protected void finalize() throws Throwable {
			super.finalize();
			theWeakListening.unsubscribe();
		}
	}

	/**
	 * An {@link ObservableCollection} whose contents are fixed
	 *
	 * @param <E> The type of values in the collection
	 */
	public static class ConstantCollection<E> extends BetterList.ConstantList<E> implements ObservableCollection<E> {
		private final TypeToken<E> theType;

		ConstantCollection(TypeToken<E> type, BetterList<? extends E> values) {
			super(values);
			theType = type;
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
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
			return () -> {};
		}

		@Override
		public void setValue(Collection<ElementId> elements, E value) {
			if (!elements.isEmpty())
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}
	}

	/**
	 * Implements {@link ObservableCollection#flattenValue(ObservableValue)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	public static class FlattenedValueCollection<E> extends AbstractIdentifiable implements ObservableCollection<E> {
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
		protected Object createIdentity() {
			return Identifiable.wrap(theCollectionObservable.getIdentity(), "flatten");
		}

		@Override
		public boolean isLockSupported() {
			return theCollectionObservable.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return Lockable.lock(theCollectionObservable, () -> Lockable.lockable(theCollectionObservable.get(), write, cause));
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return Lockable.tryLock(theCollectionObservable, () -> Lockable.lockable(theCollectionObservable.get(), write, cause));
		}

		@Override
		public long getStamp() {
			long stamp = theCollectionObservable.getStamp();
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			if (coll != null)
				stamp ^= Long.rotateRight(coll.getStamp(), 32);
			return stamp;
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
			return coll.getElementsBefore(strip(coll, id));
		}

		@Override
		public int getElementsAfter(ElementId id) {
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			return coll.getElementsAfter(strip(coll, id));
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
			return getElement(current, ((ObservableCollection<E>) current).getElement(index));
		}

		@Override
		public CollectionElement<E> getElement(E value, boolean first) {
			ObservableCollection<? extends E> current = getWrapped().get();
			if (current == null)
				return null;
			return getElement(current, ((ObservableCollection<E>) current).getElement(value, first));
		}

		@Override
		public CollectionElement<E> getElement(ElementId id) {
			ObservableCollection<? extends E> current = getWrapped().get();
			return getElement(current, current.getElement(strip(current, id)));
		}

		@Override
		public CollectionElement<E> getTerminalElement(boolean first) {
			ObservableCollection<? extends E> current = getWrapped().get();
			if (current == null)
				return null;
			return getElement(current, current.getTerminalElement(first));
		}

		@Override
		public CollectionElement<E> getAdjacentElement(ElementId elementId, boolean next) {
			ObservableCollection<? extends E> current = getWrapped().get();
			return getElement(current, current.getAdjacentElement(strip(current, elementId), next));
		}

		@Override
		public MutableCollectionElement<E> mutableElement(ElementId id) {
			ObservableCollection<? extends E> current = getWrapped().get();
			return getElement(current, current.mutableElement(strip(current, id)));
		}

		@Override
		public BetterList<CollectionElement<E>> getElementsBySource(ElementId sourceEl, BetterCollection<?> sourceCollection) {
			if (sourceCollection == this)
				return BetterList.of(getElement(sourceEl));
			ObservableCollection<? extends E> current = getWrapped().get();
			if (current == null)
				return BetterList.empty();
			return BetterList.of(current.getElementsBySource(sourceEl, sourceCollection).stream().map(el -> getElement(current, el)));
		}

		@Override
		public BetterList<ElementId> getSourceElements(ElementId localElement, BetterCollection<?> sourceCollection) {
			ObservableCollection<? extends E> current = getWrapped().get();
			if (current == null)
				return BetterList.empty();
			if (sourceCollection == this)
				return BetterList.of(current.getSourceElements(strip(current, localElement), current).stream()
					.map(el -> new FlattenedElementId(current, el)));
			else
				return BetterList.of(current.getSourceElements(strip(current, localElement), sourceCollection).stream()
					.map(el -> new FlattenedElementId(current, el)));
		}

		@Override
		public ElementId getEquivalentElement(ElementId equivalentEl) {
			ObservableCollection<? extends E> current = getWrapped().get();
			if (current == null)
				return null;
			if (equivalentEl instanceof FlattenedValueCollection.FlattenedElementId){
				if(((FlattenedElementId) equivalentEl).theCollection==current)
					return new FlattenedElementId(current, ((FlattenedElementId) equivalentEl).theSourceEl);
				else
					equivalentEl = ((FlattenedElementId) equivalentEl).theSourceEl;
			}
			ElementId found = current.getEquivalentElement(equivalentEl);
			return found == null ? null : new FlattenedElementId(current, found);
		}

		@Override
		public String canAdd(E value, ElementId after, ElementId before) {
			ObservableCollection<? extends E> current = theCollectionObservable.get();
			if (current == null)
				return MutableCollectionElement.StdMsg.UNSUPPORTED_OPERATION;
			else if (value != null && !TypeTokens.get().isInstance(current.getType(), value))
				return MutableCollectionElement.StdMsg.BAD_TYPE;
			return ((ObservableCollection<E>) current).canAdd(//
				value, strip(current, after), strip(current, before));
		}

		@Override
		public CollectionElement<E> addElement(E value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			try (Transaction t = lock(true, null)) {
				ObservableCollection<? extends E> coll = theCollectionObservable.get();
				if (coll == null)
					throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
				if (value != null && !TypeTokens.get().isInstance(coll.getType(), value))
					throw new IllegalArgumentException(MutableCollectionElement.StdMsg.BAD_TYPE);
				return getElement(coll, ((ObservableCollection<E>) coll).addElement(//
					value, strip(coll, after), strip(coll, before), first));
			}
		}

		@Override
		public String canMove(ElementId valueEl, ElementId after, ElementId before) {
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			if (coll == null)
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			return coll.canMove(//
				strip(coll, valueEl), strip(coll, after), strip(coll, before));
		}

		@Override
		public CollectionElement<E> move(ElementId valueEl, ElementId after, ElementId before, boolean first, Runnable afterRemove)
			throws UnsupportedOperationException, IllegalArgumentException {
			try (Transaction t = lock(true, null)) {
				ObservableCollection<? extends E> coll = theCollectionObservable.get();
				if (coll == null)
					throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
				return getElement(coll, coll.move(//
					strip(coll, valueEl), strip(coll, after), strip(coll, before), first, afterRemove));
			}
		}

		@Override
		public void clear() {
			try (Transaction t = lock(true, null)) {
				ObservableCollection<? extends E> coll = theCollectionObservable.get();
				if (coll != null)
					coll.clear();
			}
		}

		@Override
		public void setValue(Collection<ElementId> elements, E value) {
			try (Transaction t = lock(true, null)) {
				ObservableCollection<? extends E> coll = theCollectionObservable.get();
				if (coll == null && !elements.isEmpty())
					throw new NoSuchElementException(StdMsg.ELEMENT_REMOVED);
				List<ElementId> sourceEls = elements.stream().map(el -> strip(coll, el)).collect(Collectors.toList());
				((ObservableCollection<E>) coll).setValue(sourceEls, value);
			}
		}

		@Override
		public Observable<? extends CollectionChangeEvent<E>> changes() {
			class Changes extends AbstractIdentifiable implements Observable<CollectionChangeEvent<E>> {
				@Override
				public Subscription subscribe(Observer<? super CollectionChangeEvent<E>> observer) {
					class ChangeObserver implements Observer<ObservableValueEvent<? extends ObservableCollection<? extends E>>> {
						private ObservableCollection<? extends E> collection;
						private Subscription collectionSub;
						private final boolean[] flushOnUnsubscribe = new boolean[] { true };

						@Override
						public <V extends ObservableValueEvent<? extends ObservableCollection<? extends E>>> void onNext(V collEvt) {
							// If the collections have the same identity, then the content can't have changed
							boolean clearAndAdd;
							if (collEvt.isInitial())
								clearAndAdd = false;
							else if (collection == null || collEvt.getNewValue() == null)
								clearAndAdd = true;
							else
								clearAndAdd = !collection.getIdentity().equals(collEvt.getNewValue().getIdentity());
							if (collection != null) {
								if (clearAndAdd) {
									try (Transaction t = collection.lock(false, null)) {
										collectionSub.unsubscribe();
										collectionSub = null;
										if (clearAndAdd) {
											List<CollectionChangeEvent.ElementChange<E>> elements = new ArrayList<>(collection.size());
											int index = 0;
											for (E v : collection)
												elements.add(new CollectionChangeEvent.ElementChange<>(v, v, index++));
											CollectionChangeEvent<E> clearEvt = new CollectionChangeEvent<>(CollectionChangeType.remove, //
												elements, collEvt);
											debug(s -> s.append("clear: ").append(clearEvt));
											try (Transaction evtT = clearEvt.use()) {
												observer.onNext(clearEvt);
											}
										}
									}
								} else {
									collectionSub.unsubscribe();
									collectionSub = null;
								}
								collection = null;
							}
							collection = collEvt.getNewValue();
							if (collection != null) {
								CollectionChangesObservable<? extends E> changes;
								if (clearAndAdd) {
									try (Transaction t = collection.lock(false, null)) {
										List<CollectionChangeEvent.ElementChange<E>> elements = new ArrayList<>(collection.size());
										int index = 0;
										for (E v : collection)
											elements.add(new CollectionChangeEvent.ElementChange<>(v, null, index++));
										CollectionChangeEvent<E> populateEvt = new CollectionChangeEvent<>(CollectionChangeType.add, //
											elements, collEvt);
										debug(s -> s.append("populate: ").append(populateEvt));
										try (Transaction evtT = populateEvt.use()) {
											observer.onNext(populateEvt);
										}
										changes = new CollectionChangesObservable<>(collection);
										collectionSub = changes.subscribe((Observer<CollectionChangeEvent<? extends E>>) observer,
											flushOnUnsubscribe);
									}
								} else {
									changes = new CollectionChangesObservable<>(collection);
									collectionSub = changes.subscribe((Observer<CollectionChangeEvent<? extends E>>) observer,
										flushOnUnsubscribe);
								}
								if (Debug.d().debug(Changes.this).isActive())
									Debug.d().debug(changes, true).merge(Debug.d().debug(Changes.this));
							}
						}

						@Override
						public <V extends ObservableValueEvent<? extends ObservableCollection<? extends E>>> void onCompleted(V value) {
							unsubscribe();
						}

						void unsubscribe() {
							if (collectionSub != null) {
								collectionSub.unsubscribe();
								collectionSub = null;
							}
							collection = null;
						}
					}
					ChangeObserver chgObserver = new ChangeObserver();
					Subscription obsSub = theCollectionObservable.changes().subscribe(chgObserver);
					return () -> {
						chgObserver.flushOnUnsubscribe[0] = false;
						obsSub.unsubscribe();
						chgObserver.unsubscribe();
					};
				}

				void debug(Consumer<StringBuilder> debugMsg) {
					Debug.DebugData data = Debug.d().debug(this);
					if (!Boolean.TRUE.equals(data.getField("debug")))
						return;
					StringBuilder s = new StringBuilder();
					String debugName = data.getField("name", String.class);
					if (debugName != null)
						s.append(debugName).append(": ");
					debugMsg.accept(s);
					System.out.println(s.toString());
				}

				@Override
				public boolean isSafe() {
					return theCollectionObservable.noInitChanges().isSafe();
				}

				@Override
				public Transaction lock() {
					return FlattenedValueCollection.this.lock(false, null);
				}

				@Override
				public Transaction tryLock() {
					return FlattenedValueCollection.this.tryLock(false, null);
				}

				@Override
				protected Object createIdentity() {
					return Identifiable.wrap(FlattenedValueCollection.this.getIdentity(), "changes");
				}
			}
			return new Changes();
		}

		@Override
		public Observable<Object> simpleChanges() {
			return ObservableValue
				.flattenObservableValue(theCollectionObservable.map(coll -> coll != null ? coll.simpleChanges() : Observable.empty()));
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
			return subscribe(observer, false, true);
		}

		@Override
		public CollectionSubscription subscribe(Consumer<? super ObservableCollectionEvent<? extends E>> observer, boolean forward) {
			return subscribe(observer, true, forward);
		}

		private CollectionSubscription subscribe(Consumer<? super ObservableCollectionEvent<? extends E>> observer, boolean populate,
			boolean forward) {
			class ElementMappingChangeObserver implements Consumer<ObservableCollectionEvent<? extends E>> {
				private final ObservableCollection<? extends E> theCollection;
				private final Consumer<? super ObservableCollectionEvent<? extends E>> theWrapped;

				ElementMappingChangeObserver(ObservableCollection<? extends E> collection,
					Consumer<? super ObservableCollectionEvent<? extends E>> wrapped) {
					theCollection = collection;
					theWrapped = wrapped;
				}

				@Override
				public void accept(ObservableCollectionEvent<? extends E> event) {
					ObservableCollectionEvent<E> mapped = new ObservableCollectionEvent<>(//
						new FlattenedElementId(theCollection, event.getElementId()), getType(), event.getIndex(), event.getType(),
						event.getOldValue(), event.getNewValue(), event);
					theWrapped.accept(mapped);
				}
			}
			class ChangesSubscription implements Observer<ObservableValueEvent<? extends ObservableCollection<? extends E>>> {
				ObservableCollection<? extends E> collection;
				Subscription collectionSub;

				@Override
				public <V extends ObservableValueEvent<? extends ObservableCollection<? extends E>>> void onNext(V collEvt) {
					// The only way we can avoid de-populating and populating the values into the listener is if the old and new collections
					// share identity *and* element IDs, since those are part of the events and the contract of the subscribe method
					boolean clearAndAdd;
					if (!populate && collEvt.isInitial())
						clearAndAdd = false;
					else if (collection == collEvt.getNewValue())
						clearAndAdd = false;
					else if (collection == null || collEvt.getNewValue() == null//
						|| !collection.getIdentity().equals(collEvt.getNewValue().getIdentity()))
						clearAndAdd = true;
					else if (collection.size() != collEvt.getNewValue().size())
						clearAndAdd = true;
					else if (collection.isEmpty()//
						|| collection.getTerminalElement(true).getElementId()
						.equals(collEvt.getNewValue().getTerminalElement(true).getElementId()))
						clearAndAdd = false;
					else
						clearAndAdd = true;
					if (collection != null) {
						if (clearAndAdd) {
							try (Transaction t = collection.lock(false, null)) {
								collectionSub.unsubscribe();
								collectionSub = null;
								// De-populate in opposite direction
								ObservableUtils.depopulateValues(collection, new ElementMappingChangeObserver(collection, observer),
									!forward);
							}
						} else { // Don't need to lock
							collectionSub.unsubscribe();
							collectionSub = null;
						}
					}
					collection = collEvt.getNewValue();
					if (collection != null) {
						if (clearAndAdd) {
							collectionSub = collection.subscribe(new ElementMappingChangeObserver(collection, observer), forward);
						} else // Don't need to lock
							collectionSub = collection.onChange(new ElementMappingChangeObserver(collection, observer));
					}
				}

				@Override
				public <V extends ObservableValueEvent<? extends ObservableCollection<? extends E>>> void onCompleted(V value) {
					unsubscribe(true);
				}

				void unsubscribe(boolean removeAll) {
					if (collection != null) {
						if (removeAll) {
							try (Transaction t = collection.lock(false, null)) {
								collectionSub.unsubscribe();
								collectionSub = null;
								// De-populate in opposite direction
								ObservableUtils.depopulateValues(collection, new ElementMappingChangeObserver(collection, observer),
									!forward);
							}
						} else { // Don't need to lock
							collectionSub.unsubscribe();
							collectionSub = null;
						}
						collection = null;
					}
				}
			}
			ChangesSubscription chgSub = new ChangesSubscription();
			Subscription obsSub = theCollectionObservable.changes().subscribe(chgSub);
			return removeAll -> {
				obsSub.unsubscribe();
				chgSub.unsubscribe(removeAll);
			};
		}

		@Override
		public int hashCode() {
			return BetterCollection.hashCode(this);
		}

		@Override
		public boolean equals(Object obj) {
			return BetterCollection.equals(this, obj);
		}

		@Override
		public String toString() {
			return BetterCollection.toString(this);
		}

		ElementId strip(ObservableCollection<? extends E> coll, ElementId id) {
			if (id == null)
				return null;
			FlattenedElementId flatId = (FlattenedElementId) id;
			if (!flatId.check(coll))
				throw new NoSuchElementException(StdMsg.ELEMENT_REMOVED);
			return flatId.theSourceEl;
		}

		CollectionElement<E> getElement(ObservableCollection<? extends E> collection, CollectionElement<? extends E> element) {
			if (element == null)
				return null;
			return new FlattenedValueElement(collection, element);
		}

		MutableCollectionElement<E> getElement(ObservableCollection<? extends E> collection,
			MutableCollectionElement<? extends E> element) {
			return new MutableFlattenedValueElement(collection, element);
		}

		class FlattenedElementId implements ElementId {
			private final ObservableCollection<? extends E> theCollection;
			private final ElementId theSourceEl;

			FlattenedElementId(ObservableCollection<? extends E> collection, ElementId sourceEl) {
				theCollection = collection;
				theSourceEl = sourceEl;
			}

			boolean check(ObservableCollection<? extends E> collection) {
				if (collection == theCollection)
					return true;
				else if (collection != null && theCollection.getIdentity().equals(collection.getIdentity()))
					return true;
				else
					return false;
			}

			boolean check() {
				return check(getWrapped().get());
			}

			@Override
			public int compareTo(ElementId o) {
				FlattenedElementId other = (FlattenedElementId) o;
				if (!check(other.theCollection))
					throw new IllegalStateException("Cannot compare these elements--one or both have been removed");
				return theSourceEl.compareTo(other.theSourceEl);
			}

			@Override
			public boolean isPresent() {
				// Fudging this just a little, because the contract says that this method should never return true after it is removed
				// This could toggle between true and false, if the value of the wrapped observable goes from A to B to A.
				// The value of this method is only critical during transition events and when using an element from deep storage,
				// and we should be fine in those situations.
				return check() && theSourceEl.isPresent();
			}

			@Override
			public int hashCode() {
				return theSourceEl.hashCode();
			}

			@Override
			public boolean equals(Object obj) {
				if (obj == this)
					return true;
				else if (!(obj instanceof FlattenedValueCollection.FlattenedElementId))
					return false;
				FlattenedElementId other = (FlattenedElementId) obj;
				return check(other.theCollection) && theSourceEl.equals(other.theSourceEl);
			}

			@Override
			public String toString() {
				String str = theSourceEl.toString();
				if (theSourceEl.isPresent() && !check())
					str = "(removed) " + str;
				return str;
			}
		}

		class FlattenedValueElement implements CollectionElement<E> {
			private final FlattenedElementId theId;
			private final CollectionElement<? extends E> theElement;

			FlattenedValueElement(ObservableCollection<? extends E> collection, CollectionElement<? extends E> element) {
				theElement = element;
				theId = new FlattenedElementId(collection, element.getElementId());
			}

			protected CollectionElement<? extends E> getElement() {
				return theElement;
			}

			@Override
			public FlattenedElementId getElementId() {
				return theId;
			}

			@Override
			public E get() {
				return theElement.get();
			}

			@Override
			public int hashCode() {
				return theElement.hashCode();
			}

			@Override
			public boolean equals(Object obj) {
				return obj instanceof FlattenedValueCollection.FlattenedValueElement && theId.equals(((FlattenedValueElement) obj).theId);
			}

			@Override
			public String toString() {
				String str = theElement.toString();
				if (theElement.getElementId().isPresent() && !theId.check())
					str = "(removed) " + str;
				return str;
			}
		}

		class MutableFlattenedValueElement extends FlattenedValueElement implements MutableCollectionElement<E> {
			MutableFlattenedValueElement(ObservableCollection<? extends E> collection, MutableCollectionElement<? extends E> element) {
				super(collection, element);
			}

			@Override
			protected MutableCollectionElement<? extends E> getElement() {
				return (MutableCollectionElement<? extends E>) super.getElement();
			}

			@Override
			public BetterCollection<E> getCollection() {
				return FlattenedValueCollection.this;
			}

			@Override
			public String isEnabled() {
				if (!getElementId().check())
					return StdMsg.UNSUPPORTED_OPERATION;
				return getElement().isEnabled();
			}

			@Override
			public String isAcceptable(E value) {
				if (!getElementId().check())
					return StdMsg.UNSUPPORTED_OPERATION;
				else if (!getWrapped().get().belongs(value))
					return StdMsg.BAD_TYPE;
				return ((MutableCollectionElement<E>) getElement()).isAcceptable(value);
			}

			@Override
			public void set(E value) throws UnsupportedOperationException, IllegalArgumentException {
				if (!getElementId().check())
					throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
				else if (!getWrapped().get().belongs(value))
					throw new IllegalArgumentException(StdMsg.BAD_TYPE);
				((MutableCollectionElement<E>) getElement()).set(value);
			}

			@Override
			public String canRemove() {
				if (!getElementId().check())
					return StdMsg.UNSUPPORTED_OPERATION;
				return getElement().canRemove();
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				if (!getElementId().check())
					throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
				getElement().remove();
			}
		}
	}
}
