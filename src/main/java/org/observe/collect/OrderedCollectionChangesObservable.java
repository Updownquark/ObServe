package org.observe.collect;

import static java.util.Arrays.asList;

import org.observe.ObservableValueEvent;
import org.qommons.IntList;

class OrderedCollectionChangesObservable<E, OCCE extends OrderedCollectionChangeEvent<E>> extends CollectionChangesObservable<E, OCCE> {
	protected static class OrderedSessionChangeTracker<E> extends SessionChangeTracker<E> {
		protected final IntList indexes;

		protected OrderedSessionChangeTracker(CollectionChangeType typ) {
			super(typ);
			indexes = new IntList(true, true);
		}

		@Override
		protected void clear() {
			super.clear();
			indexes.clear();
		}
	}

	OrderedCollectionChangesObservable(ObservableOrderedCollection<E> coll) {
		super(coll);
	}

	@Override
	protected void newEvent(CollectionChangeType type, ObservableValueEvent<E> evt) {
		CollectionSession session = collection.getSession().get();
		int removeIndex = ((ObservableOrderedElement<E>) evt.getObservable()).getIndex();
		if (removeIndex < 0)
			throw new IllegalStateException("Negative index!");
		int[] index = new int[] { removeIndex };
		if(session != null) {
			OrderedSessionChangeTracker<E> tracker = (OrderedSessionChangeTracker<E>) session.get(key, SESSION_TRACKER_PROPERTY);
			if(tracker == null) {
				tracker = new OrderedSessionChangeTracker<>(type);
				session.put(key, SESSION_TRACKER_PROPERTY, tracker);
			} else {
				tracker = adjustTrackerForChange(tracker, type, index, evt);
				session.put(key, SESSION_TRACKER_PROPERTY, tracker);
			}

			int chIdx = tracker.indexes.add(index[0]);
			tracker.elements.add(chIdx, evt.getValue());
			if(tracker.oldElements != null)
				tracker.oldElements.add(chIdx, evt.getOldValue());
		} else {
			OrderedCollectionChangeEvent<E> toFire = new OrderedCollectionChangeEvent<>(type, asList(evt.getValue()),
				type == CollectionChangeType.set ? asList(evt.getOldValue()) : null, new IntList(index).setSorted(true).setUnique(true),
					evt);
			fireEvent((OCCE) toFire);
			toFire.finish();
		}
	}

	private OrderedSessionChangeTracker<E> adjustTrackerForChange(OrderedSessionChangeTracker<E> tracker, CollectionChangeType type,
		int [] index, ObservableValueEvent<E> evt) {
		if(tracker.type != type) {
			OrderedSessionChangeTracker<E> newTracker = new OrderedSessionChangeTracker<>(type);
			fireEventsFromSessionData(tracker, evt);
			return newTracker;
		} else {
			adjustEventsPast(tracker, index, evt);
			return tracker;
		}
	}

	private void adjustEventsPast(OrderedSessionChangeTracker<E> tracker, int[] index, ObservableValueEvent<E> evt) {
		if (tracker.type == CollectionChangeType.set) {
			int i = tracker.indexes.indexOf(index[0]);
			if (i >= 0) {
				// Remove the old value so the new one can trump it
				tracker.indexes.remove(i);
				tracker.elements.remove(i);
				tracker.oldElements.remove(i);
			}
			return;
		}

		int newIdx = tracker.indexes.indexFor(index[0]);
		switch (tracker.type) {
		case add:
			for (int i = tracker.indexes.size() - 1; i >= newIdx; i--)
				tracker.indexes.set(i, tracker.indexes.get(i) + 1);
			break;
		case remove:
			index[0] += newIdx;
			newIdx = tracker.indexes.indexOf(index[0]);
			if (newIdx >= 0) {
				while (newIdx < tracker.indexes.size() && tracker.indexes.get(newIdx) == index[0]) {
					index[0]++;
					newIdx++;
				}
			}
			break;
		case set:
			break;
		}
	}

	@Override
	protected void fireEventsFromSessionData(SessionChangeTracker<E> tracker, Object cause) {
		OrderedSessionChangeTracker<E> orderedTracker = (OrderedSessionChangeTracker<E>) tracker;
		if(tracker == null || tracker.elements.isEmpty())
			return;
		OrderedCollectionChangeEvent<E> evt = new OrderedCollectionChangeEvent<>(tracker.type, tracker.elements, tracker.oldElements,
			orderedTracker.indexes, cause);
		fireEvent((OCCE) evt);
		evt.finish();
	}
}
