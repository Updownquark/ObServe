package org.observe.collect;

import static org.observe.collect.CollectionChangeType.remove;
import static org.observe.collect.CollectionChangeType.set;

import java.util.function.Function;

import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.Subscription;
import org.observe.collect.ObservableCollectionImpl.CollectionChangesObservable;
import org.qommons.IntList;

import com.google.common.reflect.TypeToken;

public class ObservableOrderedCollectionImpl {
	private ObservableOrderedCollectionImpl() {}

	public static class OrderedCollectionChangesObservable<E, OCCE extends OrderedCollectionChangeEvent<E>>
	extends CollectionChangesObservable<E, OCCE> {
		protected static class OrderedSessionChangeTracker<E> extends SessionChangeTracker<E> {
			protected final IntList indexes;

			protected OrderedSessionChangeTracker(CollectionChangeType typ) {
				super(typ);
				indexes = new IntList();
			}

			@Override
			protected void clear(CollectionChangeType type) {
				super.clear(type);
				indexes.clear();
			}
		}

		protected OrderedCollectionChangesObservable(ObservableOrderedCollection<E> coll) {
			super(coll);
		}

		@Override
		protected void accumulate(SessionChangeTracker<E> tracker, ObservableCollectionEvent<? extends E> event,
			Observer<? super OCCE> observer) {
			OrderedSessionChangeTracker<E> orderedTracker = (OrderedSessionChangeTracker<E>) tracker;
			int[] index = new int[] { ((OrderedCollectionEvent<E>) event).getIndex() };
			adjustTrackerForChange(orderedTracker, index, (OrderedCollectionEvent<? extends E>) event, observer);
			super.accumulate(tracker, event, observer);
			orderedTracker.indexes.add(((OrderedCollectionEvent<? extends E>) event).getIndex());
		}

		private OrderedSessionChangeTracker<E> adjustTrackerForChange(OrderedSessionChangeTracker<E> tracker, int[] index,
			OrderedCollectionEvent<? extends E> event, Observer<? super OCCE> observer) {
			if (tracker.type != event.getType()) {
				fireEventsUpTo(tracker, index, event, observer);
				if (adjustEventsPast(tracker, index, event, observer))
					return null;
				OrderedSessionChangeTracker<E> newTracker = new OrderedSessionChangeTracker<>(event.getType());
				newTracker.indexes.add(index[0]);
				newTracker.elements.add(event.getNewValue());
				if (newTracker.oldElements != null)
					newTracker.oldElements.add(event.getOldValue());
				fireEventsFromSessionData(newTracker, event, observer);
				fireEventsFromSessionData(tracker, event, observer);
				tracker.clear(event.getType());
				return tracker;
			} else {
				if (adjustEventsPast(tracker, index, event, observer))
					return null;
				return tracker;
			}
		}

		private void fireEventsUpTo(OrderedSessionChangeTracker<E> tracker, int[] index, OrderedCollectionEvent<? extends E> event,
			Observer<? super OCCE> observer) {
			// Fire events for indexes before the new change index, since otherwise those changes would affect the index
			if (tracker.indexes.size() < 25) {
				// If it's not too expensive, let's see if we need to do anything before constructing the lists needlessly
				boolean hasIndexesBefore = false;
				for (int i = 0; i < tracker.indexes.size(); i++)
					if (tracker.indexes.get(i) < index[0] || (event.getType() == remove && tracker.indexes.get(i) == index[0])) {
						hasIndexesBefore = true;
						break;
					}
				if (!hasIndexesBefore)
					return;
			}

			// Compile an event with the changes recorded in the tracker whose indexes were at or before the new change's index.
			// Remove those changes from the tracker and fire the event for them separately
			OrderedSessionChangeTracker<E> subTracker = new OrderedSessionChangeTracker<>(tracker.type);
			for (int i = 0; i < tracker.indexes.size(); i++) {
				if (tracker.indexes.get(i) < index[0] || (event.getType() == remove && tracker.indexes.get(i) == index[0])) {
					subTracker.indexes.add(tracker.indexes.remove(i));
					subTracker.elements.add(tracker.elements.remove(i));
					if (tracker.oldElements != null)
						subTracker.oldElements.add(tracker.oldElements.remove(i));
					i--;
				}
			}
			fireEventsFromSessionData(subTracker, event, observer);
		}

		private boolean adjustEventsPast(OrderedSessionChangeTracker<E> tracker, int[] index, OrderedCollectionEvent<? extends E> event,
			Observer<? super OCCE> observer) {

			// Adjust all indexes strictly past the change index first
			if (event.getType() != set && (tracker.type != remove || event.getType() != remove)) {
				for (int i = 0; i < tracker.indexes.size(); i++) {
					int changeIdx = tracker.indexes.get(i);
					if (changeIdx > index[0]) {
						if (event.getType() == remove)
							changeIdx--;
						else
							changeIdx++;
						tracker.indexes.set(i, changeIdx);
					}
				}
			}

			// Now handle the case where the indexes are the same
			int i = tracker.indexes.indexOf(index[0]);
			if (i >= 0) {
				switch (tracker.type) {
				case add:
					switch (event.getType()) {
					case add:
						tracker.indexes.set(i, index[0] + 1);
						break;
					case remove:
						tracker.indexes.remove(i);
						tracker.elements.remove(i);
						// oldElements will be null since tracker.type==add
						return true;
					case set:
						tracker.elements.set(i, event.getNewValue());
						return true;
					}
					break;
				case remove:
					switch (event.getType()) {
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
					switch (event.getType()) {
					case add:
						tracker.indexes.set(i, index[0] + 1);
						break;
					case remove:
						tracker.indexes.remove(i);
						tracker.elements.remove(i);
						tracker.oldElements.remove(i);
						break;
					case set:
						tracker.elements.set(i, event.getNewValue());
						return true;
					}
					break;
				}
			}

			if (tracker.type == remove && event.getType() == remove) {
				int indexAdd = 0;
				for (i = 0; i < tracker.indexes.size(); i++) {
					if (tracker.indexes.get(i) <= index[0])
						indexAdd++;
				}
				index[0] += indexAdd;
			}
			return false;
		}

		@Override
		protected void fireEventsFromSessionData(SessionChangeTracker<E> tracker, Object cause, Observer<? super OCCE> observer) {
			if (tracker.elements.isEmpty())
				return;
			OrderedSessionChangeTracker<E> orderedTracker = (OrderedSessionChangeTracker<E>) tracker;
			OrderedCollectionChangeEvent.doWith(
				new OrderedCollectionChangeEvent<>(tracker.type, tracker.elements, tracker.oldElements, orderedTracker.indexes, cause),
				evt -> observer.onNext((OCCE) evt));
		}
	}

	/**
	 * Implements {@link ObservableOrderedCollection#observeAt(int, Function)}
	 *
	 * @param <E> The type of the element
	 */
	class PositionObservable<E> implements ObservableValue<E> {
		private final ObservableOrderedCollection<E> theCollection;
		private final int theIndex;
		private final Function<Integer, E> theDefaultValueGenerator;

		protected PositionObservable(ObservableOrderedCollection<E> collection, int index, Function<Integer, E> defValueGen) {
			theCollection = collection;
			theIndex = index;
			theDefaultValueGenerator = defValueGen;
		}

		protected ObservableOrderedCollection<E> getCollection() {
			return theCollection;
		}

		protected int getIndex() {
			return theIndex;
		}

		protected Function<Integer, E> getDefaultValueGenerator() {
			return theDefaultValueGenerator;
		}

		@Override
		public TypeToken<E> getType() {
			return theCollection.getType();
		}

		@Override
		public E get() {
			if (theIndex < theCollection.size())
				return theCollection.get(theIndex);
			else if (theDefaultValueGenerator != null)
				return theDefaultValueGenerator.apply(theCollection.size());
			else
				throw new IndexOutOfBoundsException(theIndex + " of " + theCollection.size());
		}

		@Override
		public Subscription subscribe(Observer<? super ObservableValueEvent<E>> observer) {
		}

		@Override
		public boolean isSafe() {
			return true;
		}
	}

}
