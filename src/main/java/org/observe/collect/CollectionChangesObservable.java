package org.observe.collect;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.observe.Observable;
import org.observe.Observer;
import org.observe.Subscription;
import org.observe.collect.CollectionChangeEvent.ElementChange;
import org.qommons.Causable;
import org.qommons.Identifiable;
import org.qommons.Identifiable.AbstractIdentifiable;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.collect.ReentrantNotificationException;
import org.qommons.debug.Debug;
import org.qommons.debug.Debug.DebugData;
import org.qommons.tree.RedBlackNode;
import org.qommons.tree.RedBlackTree;

/**
 * Fires {@link CollectionChangeEvent}s in response to sets of changes on an {@link ObservableCollection}. CollectionChangeEvents
 * contain more information than the standard {@link ObservableCollectionEvent}, so listening to CollectionChangeEvents may result in
 * better application performance.
 *
 * @param <E> The type of values in the collection
 */
public class CollectionChangesObservable<E> extends AbstractIdentifiable implements Observable<CollectionChangeEvent<E>> {
	/**
	 * Tracks a set of changes corresponding to a set of {@link ObservableCollectionEvent}s, so those changes can be fired at once
	 *
	 * @param <E> The type of values in the collection
	 */
	private static class SessionChangeTracker<E> {
		final CollectionChangeType type;
		ChangeList<E> changes;

		/** @param typ The initial change type for this tracker's accumulation */
		protected SessionChangeTracker(CollectionChangeType typ) {
			type = typ;
			switch (typ) {
			case add:
				changes = new AddList<>();
				break;
			case remove:
				changes = new RemoveList<>();
				break;
			case set:
				changes = new SetList<>();
				break;
			default:
				throw new IllegalStateException();
			}
		}

		@Override
		public String toString() {
			return changes.toString();
		}
	}

	private interface ChangeList<E> {
		int size();

		ChangeList<E> add(int collectionIndex, E oldValue, E newValue);

		E remove(int collectionIndex);

		boolean update(int collectionIndex, E newValue);

		List<CollectionChangeEvent.ElementChange<E>> dump();
	}

	static class AddList<E> implements ChangeList<E> {
		static class Add<E> {
			final int collectionIndex;
			final int changeIndex;
			E value;

			Add(int collectionIndex, int changeIndex, E value) {
				this.collectionIndex = collectionIndex;
				this.changeIndex = changeIndex;
				this.value = value;
			}

			@Override
			public String toString() {
				return value + "@" + collectionIndex;
			}
		}

		private final RedBlackTree<Add<E>> changes = new RedBlackTree<>();

		@Override
		public int size() {
			return changes.size();
		}

		@Override
		public ChangeList<E> add(int collectionIndex, E oldValue, E newValue) {
			RedBlackNode<Add<E>> node = changes.getRoot();
			if (node == null) {
				changes.setRoot(new RedBlackNode<>(changes, new Add<>(collectionIndex, 0, newValue)));
				return this;
			}
			int lastComp = 0;
			int changeIndex = 0;
			while (true) {
				int nodeIndex = changeIndex;
				RedBlackNode<Add<E>> left = node.getLeft();
				if (left != null)
					nodeIndex += left.size();
				lastComp = Integer.compare(collectionIndex, node.getValue().collectionIndex + nodeIndex - node.getValue().changeIndex);
				if (lastComp == 0) {
					changeIndex = nodeIndex;
					break;
				} else if (lastComp < 0) {
					if (left == null)
						break;
					node = left;
				} else {
					changeIndex = nodeIndex + 1;
					if (node.getRight() == null)
						break;
					node = node.getRight();
				}
			}
			boolean left = lastComp <= 0;
			node.add(new RedBlackNode<>(changes, new Add<>(collectionIndex, changeIndex, newValue)), left);
			return this;
		}

		@Override
		public E remove(int collectionIndex) {
			RedBlackNode<Add<E>> node = changes.getRoot();
			int changeIndex = 0;
			while (node != null) {
				int nodeIndex = changeIndex;
				RedBlackNode<Add<E>> left = node.getLeft();
				if (left != null)
					nodeIndex += left.size();
				int lastComp = Integer.compare(collectionIndex, node.getValue().collectionIndex + nodeIndex - node.getValue().changeIndex);
				if (lastComp == 0) {
					changeIndex = nodeIndex;
					break;
				} else if (lastComp < 0)
					node = left;
				else {
					changeIndex = nodeIndex + 1;
					node = node.getRight();
				}
			}
			if (node == null)
				return null;
			node.delete();
			return node.getValue().value;
		}

		@Override
		public boolean update(int collectionIndex, E newValue) {
			RedBlackNode<Add<E>> node = changes.getRoot();
			int changeIndex = 0;
			while (node != null) {
				int nodeIndex = changeIndex;
				RedBlackNode<Add<E>> left = node.getLeft();
				if (left != null)
					nodeIndex += left.size();
				int lastComp = Integer.compare(collectionIndex, node.getValue().collectionIndex + nodeIndex - node.getValue().changeIndex);
				if (lastComp == 0) {
					changeIndex = nodeIndex;
					break;
				} else if (lastComp < 0)
					node = left;
				else {
					changeIndex = nodeIndex + 1;
					node = node.getRight();
				}
			}
			if (node == null)
				return false;
			node.getValue().value = newValue;
			return true;
		}

		@Override
		public List<ElementChange<E>> dump() {
			ElementChange<E>[] dump = new ElementChange[changes.size()];
			RedBlackNode<Add<E>> node = changes.getFirst();
			int index = 0;
			while (node != null) {
				dump[index] = new ElementChange<>(node.getValue().value, node.getValue().value,
					node.getValue().collectionIndex + index - node.getValue().changeIndex);
				node = node.getClosest(false);
				index++;
			}
			return Arrays.asList(dump);
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder("add:\n");
			RedBlackNode<Add<E>> node = changes.getFirst();
			int index = 0;
			while (node != null) {
				str.append('\t').append(node.getValue().collectionIndex + index - node.getValue().changeIndex)//
				.append(": ").append(node.getValue().value).append('\n');
				node = node.getClosest(false);
				index++;
			}
			return str.toString();
		}
	}

	static class RemoveList<E> implements ChangeList<E> {
		static class Remove<E> {
			final int collectionIndex;
			E value;

			Remove(int collectionIndex, E value) {
				this.collectionIndex = collectionIndex;
				this.value = value;
			}

			@Override
			public String toString() {
				return value + "@" + collectionIndex;
			}
		}

		private final RedBlackTree<Remove<E>> changes = new RedBlackTree<>();

		@Override
		public int size() {
			return changes.size();
		}

		@Override
		public ChangeList<E> add(int collectionIndex, E oldValue, E newValue) {
			RedBlackNode<Remove<E>> node = changes.getRoot();
			if (node == null) {
				changes.setRoot(new RedBlackNode<>(changes, new Remove<>(collectionIndex, oldValue)));
				return this;
			}
			int lastComp = 0;
			while (true) {
				RedBlackNode<Remove<E>> left = node.getLeft();
				int newCI = collectionIndex;
				if (left != null)
					newCI += left.size();
				lastComp = Integer.compare(newCI, node.getValue().collectionIndex);
				if (lastComp < 0) {
					if (left == null)
						break;
					node = left;
				} else {
					collectionIndex = newCI + 1;
					if (node.getRight() == null)
						break;
					node = node.getRight();
				}
			}
			boolean left = lastComp < 0;
			node.add(new RedBlackNode<>(changes, new Remove<>(collectionIndex, oldValue)), left);
			return this;
		}

		@Override
		public E remove(int collectionIndex) {
			RedBlackNode<Remove<E>> node = changes.getRoot();
			while (node != null) {
				RedBlackNode<Remove<E>> left = node.getLeft();
				int lastComp = Integer.compare(collectionIndex, node.getValue().collectionIndex);
				if (lastComp == 0) {
					collectionIndex++;
					if (left != null)
						collectionIndex += left.size();
					break;
				} else if (lastComp < 0)
					node = left;
				else {
					if (left != null)
						collectionIndex += left.size();
					collectionIndex++;
					node = node.getRight();
				}
			}
			if (node == null)
				return null;
			node.delete();
			return node.getValue().value;
		}

		@Override
		public boolean update(int collectionIndex, E newValue) {
			RedBlackNode<Remove<E>> node = changes.getRoot();
			while (node != null) {
				RedBlackNode<Remove<E>> left = node.getLeft();
				int lastComp = Integer.compare(collectionIndex, node.getValue().collectionIndex);
				if (lastComp == 0) {
					collectionIndex++;
					if (left != null)
						collectionIndex += left.size();
					break;
				} else if (lastComp < 0)
					node = left;
				else {
					if (left != null)
						collectionIndex += left.size();
					collectionIndex++;
					node = node.getRight();
				}
			}
			if (node == null)
				return false;
			node.getValue().value = newValue;
			return true;
		}

		@Override
		public List<ElementChange<E>> dump() {
			ElementChange<E>[] dump = new ElementChange[changes.size()];
			RedBlackNode<Remove<E>> node = changes.getFirst();
			int index = 0;
			while (node != null) {
				dump[index] = new ElementChange<>(node.getValue().value, node.getValue().value, node.getValue().collectionIndex);
				node = node.getClosest(false);
				index++;
			}
			return Arrays.asList(dump);
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder("remove:\n");
			RedBlackNode<Remove<E>> node = changes.getFirst();
			while (node != null) {
				str.append('\t').append(node.getValue().collectionIndex)//
				.append(": ").append(node.getValue().value).append('\n');
				node = node.getClosest(false);
			}
			return str.toString();
		}
	}

	static class SetList<E> implements ChangeList<E> {
		static class Set<E> {
			final int collectionIndex;
			final E oldValue;
			E newValue;

			Set(int collectionIndex, E oldValue, E newValue) {
				this.collectionIndex = collectionIndex;
				this.oldValue = oldValue;
				this.newValue = newValue;
			}

			@Override
			public String toString() {
				return oldValue + "->" + newValue + "@" + collectionIndex;
			}
		}

		private final RedBlackTree<Set<E>> changes = new RedBlackTree<>();

		@Override
		public int size() {
			return changes.size();
		}

		@Override
		public ChangeList<E> add(int collectionIndex, E oldValue, E newValue) {
			RedBlackNode<Set<E>> node = changes.getRoot();
			if (node == null) {
				changes.setRoot(new RedBlackNode<>(changes, new Set<>(collectionIndex, oldValue, newValue)));
				return this;
			}
			int lastComp = 0;
			while (true) {
				RedBlackNode<Set<E>> left = node.getLeft();
				lastComp = Integer.compare(collectionIndex, node.getValue().collectionIndex);
				if (lastComp == 0)
					break;
				else if (lastComp < 0) {
					if (left == null)
						break;
					node = left;
				} else {
					if (node.getRight() == null)
						break;
					node = node.getRight();
				}
			}
			if (lastComp == 0)
				node.getValue().newValue = newValue;
			else {
				boolean left = lastComp < 0;
				node.add(new RedBlackNode<>(changes, new Set<>(collectionIndex, oldValue, newValue)), left);
			}
			return this;
		}

		@Override
		public E remove(int collectionIndex) {
			RedBlackNode<Set<E>> node = changes.getRoot();
			while (node != null) {
				RedBlackNode<Set<E>> left = node.getLeft();
				int lastComp = Integer.compare(collectionIndex, node.getValue().collectionIndex);
				if (lastComp == 0)
					break;
				else if (lastComp < 0)
					node = left;
				else
					node = node.getRight();
			}
			if (node == null)
				return null;
			node.delete();
			return node.getValue().oldValue;
		}

		@Override
		public boolean update(int collectionIndex, E newValue) {
			RedBlackNode<Set<E>> node = changes.getRoot();
			while (node != null) {
				RedBlackNode<Set<E>> left = node.getLeft();
				int lastComp = Integer.compare(collectionIndex, node.getValue().collectionIndex);
				if (lastComp == 0)
					break;
				else if (lastComp < 0)
					node = left;
				else
					node = node.getRight();
			}
			if (node == null)
				return false;
			node.getValue().newValue = newValue;
			return true;
		}

		@Override
		public List<ElementChange<E>> dump() {
			ElementChange<E>[] dump = new ElementChange[changes.size()];
			RedBlackNode<Set<E>> node = changes.getFirst();
			int index = 0;
			while (node != null) {
				dump[index] = new ElementChange<>(node.getValue().newValue, node.getValue().oldValue, node.getValue().collectionIndex);
				node = node.getClosest(false);
				index++;
			}
			return Arrays.asList(dump);
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder("set:\n");
			RedBlackNode<Set<E>> node = changes.getFirst();
			while (node != null) {
				str.append('\t').append(node.getValue().collectionIndex)//
				.append(": ").append(node.getValue().oldValue);
				if (node.getValue().oldValue == node.getValue().newValue)
					str.append(" (update)");
				else
					str.append("->").append(node.getValue().newValue);
				str.append('\n');
				node = node.getClosest(false);
			}
			return str.toString();
		}
	}

	private static final String SESSION_TRACKER_PROPERTY = "change-tracker";

	/** The collection that this change observable watches */
	protected final ObservableCollection<E> collection;
	private boolean isFiring;

	/** @param coll The collection for this change observable to watch */
	protected CollectionChangesObservable(ObservableCollection<E> coll) {
		collection = coll;
		DebugData d = Debug.d().debug(coll);
		if (d.isActive())
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
			CollectionChangesObservable.SessionChangeTracker<E> tracker = (CollectionChangesObservable.SessionChangeTracker<E>) data.get(SESSION_TRACKER_PROPERTY);
			debug(//
				s -> s.append("Transaction end, flushing ").append(tracker));
			fireEventsFromSessionData(tracker, cause, observer);
			data.remove(SESSION_TRACKER_PROPERTY);
			currentData[0] = null;
			currentCause[0] = null;
		});
		Subscription collSub = collection.onChange(evt -> {
			if (isFiring)
				throw new ReentrantNotificationException(ObservableCollection.REENTRANT_EVENT_ERROR);
			Causable cause = evt.getRootCausable();
			Map<Object, Object> data = cause.onFinish(key);
			Object newTracker = data.compute(SESSION_TRACKER_PROPERTY, (k, tracker) -> {
				tracker = accumulate((CollectionChangesObservable.SessionChangeTracker<E>) tracker, evt, observer);
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
					CollectionChangesObservable.SessionChangeTracker<E> tracker = (CollectionChangesObservable.SessionChangeTracker<E>) currentData[0].remove(SESSION_TRACKER_PROPERTY);
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
	private CollectionChangesObservable.SessionChangeTracker<E> accumulate(CollectionChangesObservable.SessionChangeTracker<E> tracker, ObservableCollectionEvent<? extends E> event,
		Observer<? super CollectionChangeEvent<E>> observer) {
		int collIndex = event.getIndex();
		if (tracker == null) {
			debug(s -> s.append("From clean slate, tracking ").append(event));
			return replace(tracker, event, observer);
		}
		CollectionChangesObservable.SessionChangeTracker<E> newTracker = tracker;
		switch (tracker.type) {
		case add:
			switch (event.getType()) {
			case add:
				debug(s -> s.append("\tAdding ").append(event));
				tracker.changes.add(collIndex, event.getOldValue(), event.getNewValue());
				break;
			case remove:
				int oldSize = tracker.changes.size();
				E oldValue = tracker.changes.remove(collIndex);
				if (tracker.changes.size() == 0) {
					debug(s -> s.append("\tRemoving add ").append(collIndex).append(": ").append(oldValue));
					newTracker = null;
				} else if (tracker.changes.size() < oldSize)
					debug(s -> s.append("\tRemoving add ").append(collIndex).append(": ").append(oldValue));
				else {
					debug(s -> s.append("Remove after adds, flushing ").append(tracker).append(", now tracking ").append(event));
					newTracker = replace(tracker, event, observer);
				}
				break;
			case set:
				if (tracker.changes.update(collIndex, event.getNewValue()))
					debug(s -> s.append("\tReplacing add ").append(collIndex).append(" with ").append(event.getNewValue()));
				else {
					debug(s -> s.append("Set after adds, flushing ").append(tracker).append(", now tracking ").append(event));
					newTracker = replace(tracker, event, observer);
				}
				break;
			}
			break;
		case remove:
			switch (event.getType()) {
			case add:
				int oldSize = tracker.changes.size();
				boolean replace = true;
				if (oldSize == 1) {
					E oldValue = tracker.changes.remove(collIndex);
					if (tracker.changes.size() == 0) {
						replace = false;
						debug(s -> s.append("\tChanging remove ").append(collIndex).append(" to set ").append(event.getNewValue()));
						newTracker = new CollectionChangesObservable.SessionChangeTracker<>(CollectionChangeType.set);
						newTracker.changes.add(collIndex, oldValue, event.getNewValue());
					}
				}
				if (replace) {
					debug(s -> s.append("Add after removes, flushing ").append(tracker).append(", now tracking ").append(event));
					newTracker = replace(tracker, event, observer);
				}
				break;
			case remove:
				tracker.changes.add(collIndex, event.getOldValue(), event.getNewValue());
				if (collection.isEmpty() && newTracker.changes.size() > 1) {
					// If the collection is empty, no more elements can be removed and any other change will just call a replace,
					// so there's no more information we can possibly accumulate in this session.
					// Let's preemptively fire the event now.
					debug(s -> s.append("\tCollection cleared, flushing ").append(tracker));
					fireEventsFromSessionData(newTracker, event, observer);
					newTracker = null;
				} else {
					debug(s -> s.append("Adding remove element ").append(event));
				}
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
				int oldSize = tracker.changes.size();
				E oldValue = tracker.changes.remove(collIndex);
				if (oldSize == 1 && tracker.changes.size() == 0) {
					debug(s -> s.append("\tSet element removed, replacing ").append(tracker).append(" with remove"));
					newTracker = new CollectionChangesObservable.SessionChangeTracker<>(CollectionChangeType.remove);
					newTracker.changes.add(collIndex, oldValue, oldValue);
				} else if (tracker.changes.size() < oldSize) {
					debug(s -> s.append("\tRemove after sets, flushing ").append(tracker).append(", now tracking remove [")
						.append(collIndex).append("] ").append(oldValue));
					newTracker = new CollectionChangesObservable.SessionChangeTracker<>(CollectionChangeType.remove);
					newTracker.changes.add(collIndex, oldValue, oldValue);
					fireEventsFromSessionData(tracker, event, observer);
				} else {
					debug(s -> s.append("Remove after sets, flushing ").append(tracker).append(", now tracking ").append(event));
					newTracker = replace(tracker, event, observer);
				}
				break;
			case set:
				debug(s -> s.append("\tAdding set ").append(collIndex).append(event.getNewValue()));
				tracker.changes.add(collIndex, event.getOldValue(), event.getNewValue());
				break;
			}
			break;
		}
		return newTracker;
	}

	private CollectionChangesObservable.SessionChangeTracker<E> replace(CollectionChangesObservable.SessionChangeTracker<E> tracker, ObservableCollectionEvent<? extends E> event,
		Observer<? super CollectionChangeEvent<E>> observer) {
		fireEventsFromSessionData(tracker, event, observer);
		tracker = new CollectionChangesObservable.SessionChangeTracker<>(event.getType());
		tracker.changes.add(event.getIndex(), event.getOldValue(), event.getNewValue());
		return tracker;
	}

	/**
	 * Fires a change event communicating all changes accumulated into a change tracker
	 *
	 * @param tracker The change tracker into which changes have been accumulated
	 * @param cause The overall cause of the change event
	 * @param observer The observer on which to fire the change event
	 */
	private void fireEventsFromSessionData(CollectionChangesObservable.SessionChangeTracker<E> tracker, Object cause,
		Observer<? super CollectionChangeEvent<E>> observer) {
		if (tracker == null || tracker.changes.size() == 0)
			return;
		if (isFiring)
			throw new ReentrantNotificationException(ObservableCollection.REENTRANT_EVENT_ERROR);
		CollectionChangeEvent<E> evt = new CollectionChangeEvent<>(tracker.type, tracker.changes.dump(), cause);
		isFiring = true;
		try (Transaction t = evt.use()) {
			observer.onNext(evt);
		} finally {
			isFiring = false;
		}
	}

	@Override
	public ThreadConstraint getThreadConstraint() {
		return collection.getThreadConstraint();
	}

	@Override
	public boolean isEventing() {
		return isFiring;
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
	public CoreId getCoreId() {
		return collection.getCoreId();
	}

	@Override
	public String toString() {
		return "changes(" + collection.getIdentity() + ")";
	}
}