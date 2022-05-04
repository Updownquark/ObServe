package org.observe.remote;

import org.observe.Subscription;
import org.qommons.collect.BetterSortedList.SortedSearchFilter;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.ListenerList;
import org.qommons.tree.BetterTreeSet;

public class MemoryEventStore<P> implements CollectionEventStore<P> {
	static class IdEvent<P> implements Comparable<IdEvent<P>> {
		final P event;
		final long id;

		IdEvent(P event, long id) {
			this.event = event;
			this.id = id;
		}

		@Override
		public int compareTo(IdEvent<P> o) {
			return Long.compare(id, o.id);
		}
	}
	private final BetterSortedSet<IdEvent<P>> theEvents;
	private final ListenerList<EventStoreListener> theListeners;

	public MemoryEventStore() {
		theEvents = BetterTreeSet.<IdEvent<P>> buildTreeSet(IdEvent::compareTo).build();
		theListeners = ListenerList.build().build();
	}

	@Override
	public synchronized void store(long eventId, P event) {
		theEvents.add(new IdEvent<>(event, eventId));
	}

	@Override
	public synchronized P retrieve(long eventId) {
		IdEvent<P> found = theEvents.searchValue(ev -> Long.compare(eventId, ev.id), SortedSearchFilter.OnlyMatch);
		return found == null ? null : found.event;
	}

	@Override
	public synchronized void release(long oldestNeededEventId) {
		theEvents.subSequence(null, ev -> {
			int comp = Long.compare(oldestNeededEventId, ev.id);
			if (comp == 0)
				comp = 1;
			return comp;
		}).clear();
		theListeners.forEach(//
			listener -> listener.eventsPurged(oldestNeededEventId));
	}

	@Override
	public Subscription addListener(EventStoreListener listener) {
		return theListeners.add(listener, true)::run;
	}
}
