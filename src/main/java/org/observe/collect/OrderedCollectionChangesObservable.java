package org.observe.collect;

import static java.util.Arrays.asList;
import static org.observe.collect.CollectionChangeType.remove;
import static org.observe.collect.CollectionChangeType.set;

import org.observe.ObservableValueEvent;
import org.qommons.IntList;

class OrderedCollectionChangesObservable<E, OCCE extends OrderedCollectionChangeEvent<E>> extends CollectionChangesObservable<E, OCCE> {
	protected static class OrderedSessionChangeTracker<E> extends SessionChangeTracker<E> {
		protected final IntList indexes;

		protected OrderedSessionChangeTracker(CollectionChangeType typ) {
			super(typ);
			indexes = new IntList();
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
		int [] index = new int[] {((ObservableOrderedElement<E>) evt.getObservable()).getIndex()};
		if(index[0] < 0)
			throw new IllegalStateException("Negative index!");
		if(session != null) {
			OrderedSessionChangeTracker<E> tracker = (OrderedSessionChangeTracker<E>) session.get(key, SESSION_TRACKER_PROPERTY);
			if(tracker == null) {
				tracker = new OrderedSessionChangeTracker<>(type);
				session.put(key, SESSION_TRACKER_PROPERTY, tracker);
			} else {
				tracker = adjustTrackerForChange(tracker, type, index, evt);
				if(tracker != null)
					session.put(key, SESSION_TRACKER_PROPERTY, tracker.indexes.isEmpty() ? null : tracker);
				else
					return; // Already taken care of
			}

			tracker.elements.add(evt.getValue());
			if(tracker.oldElements != null)
				tracker.oldElements.add(evt.getOldValue());
			tracker.indexes.add(index[0]);
		} else {
			OrderedCollectionChangeEvent<E> toFire = new OrderedCollectionChangeEvent<>(type, asList(evt.getValue()),
				type == CollectionChangeType.set ? asList(evt.getOldValue()) : null, new IntList(index), evt);
			fireEvent((OCCE) toFire);
		}
	}

	private OrderedSessionChangeTracker<E> adjustTrackerForChange(OrderedSessionChangeTracker<E> tracker, CollectionChangeType type,
		int [] index, ObservableValueEvent<E> evt) {
		if(tracker.type != type) {
			fireEventsUpTo(tracker, type, index, evt);
			if(adjustEventsPast(tracker, type, index, evt))
				return null;
			OrderedSessionChangeTracker<E> newTracker = new OrderedSessionChangeTracker<>(type);
			newTracker.indexes.add(index[0]);
			newTracker.elements.add(evt.getValue());
			if(newTracker.oldElements != null)
				newTracker.oldElements.add(evt.getOldValue());
			fireEventsFromSessionData(newTracker, evt);
			fireEventsFromSessionData(tracker, evt);
			tracker.clear();
			return tracker;
		} else {
			if(adjustEventsPast(tracker, type, index, evt))
				return null;
			return tracker;
		}
	}

	private void fireEventsUpTo(OrderedSessionChangeTracker<E> tracker, CollectionChangeType type, int[] index, Object cause) {
		// Fire events for indexes before the new change index, since otherwise those changes would affect the index
		if(tracker.indexes.size() < 25) {
			// If it's not too expensive, let's see if we need to do anything before constructing the lists needlessly
			boolean hasIndexesBefore = false;
			for(int i = 0; i < tracker.indexes.size(); i++)
				if(tracker.indexes.get(i) < index[0] || (type == remove && tracker.indexes.get(i) == index[0])) {
					hasIndexesBefore = true;
					break;
				}
			if(!hasIndexesBefore)
				return;
		}

		// Compile an event with the changes recorded in the tracker whose indexes were at or before the new change's index.
		// Remove those changes from the tracker and fire the event for them separately
		OrderedSessionChangeTracker<E> subTracker = new OrderedSessionChangeTracker<>(tracker.type);
		for(int i = 0; i < tracker.indexes.size(); i++) {
			if(tracker.indexes.get(i) < index[0] || (type == remove && tracker.indexes.get(i) == index[0])) {
				subTracker.indexes.add(tracker.indexes.remove(i));
				subTracker.elements.add(tracker.elements.remove(i));
				if(tracker.oldElements != null)
					subTracker.oldElements.add(tracker.oldElements.remove(i));
				i--;
			}
		}
		fireEventsFromSessionData(subTracker, cause);
	}

	private boolean adjustEventsPast(OrderedSessionChangeTracker<E> tracker, CollectionChangeType type, int [] index,
		ObservableValueEvent<E> evt) {

		// Adjust all indexes strictly past the change index first
		if(type != set && (tracker.type != remove || type != remove)) {
			for(int i = 0; i < tracker.indexes.size(); i++) {
				int changeIdx = tracker.indexes.get(i);
				if(changeIdx > index[0]) {
					if(type == remove)
						changeIdx--;
					else
						changeIdx++;
					tracker.indexes.set(i, changeIdx);
				}
			}
		}

		// Now handle the case where the indexes are the same
		int i = tracker.indexes.indexOf(index[0]);
		if(i >= 0) {
			switch (tracker.type) {
			case add:
				switch (type) {
				case add:
					tracker.indexes.set(i, index[0] + 1);
					break;
				case remove:
					tracker.indexes.remove(i);
					tracker.elements.remove(i);
					// oldElements will be null since tracker.type==add
					return true;
				case set:
					tracker.elements.set(i, evt.getValue());
					return true;
				}
				break;
			case remove:
				switch (type) {
				case add:
					tracker.indexes.set(i, index[0] + 1);
					break;
				case remove:
					break;
				case set:
					break;
				}
				break;
			case set:
				switch (type) {
				case add:
					tracker.indexes.set(i, index[0] + 1);
					break;
				case remove:
					tracker.indexes.remove(i);
					tracker.elements.remove(i);
					tracker.oldElements.remove(i);
					break;
				case set:
					tracker.elements.set(i, evt.getValue());
					return true;
				}
				break;
			}
		}

		if(tracker.type == remove && type == remove) {
			int indexAdd = 0;
			for(i = 0; i < tracker.indexes.size(); i++) {
				if(tracker.indexes.get(i) <= index[0])
					indexAdd++;
			}
			index[0] += indexAdd;
		}
		return false;
	}

	@Override
	protected void fireEventsFromSessionData(SessionChangeTracker<E> tracker, Object cause) {
		OrderedSessionChangeTracker<E> orderedTracker = (OrderedSessionChangeTracker<E>) tracker;
		if(tracker == null || tracker.elements.isEmpty())
			return;
		OrderedCollectionChangeEvent<E> evt = new OrderedCollectionChangeEvent<>(tracker.type, tracker.elements, tracker.oldElements,
			orderedTracker.indexes, cause);
		fireEvent((OCCE) evt);
	}
}
