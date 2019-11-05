package org.observe.util;

import java.util.function.Function;

import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Subscription;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollectionEvent;
import org.qommons.Causable;
import org.qommons.Causable.CausableKey;
import org.qommons.Transaction;

/** Utility methods for observables */
public class ObservableUtils {
	/**
	 * Wraps an event from an observable value to use a different observable value as the source
	 *
	 * @param <T> The type of the value to wrap an event for
	 * @param event The event to wrap
	 * @param wrapper The wrapper observable to wrap the event for
	 * @return An event with the same values as the given event, but created by the given observable
	 */
	public static <T> ObservableValueEvent<T> wrap(ObservableValueEvent<? extends T> event, ObservableValue<T> wrapper) {
		if (event.isInitial())
			return wrapper.createInitialEvent(event.getNewValue(), event.getCause());
		else
			return wrapper.createChangeEvent(event.getOldValue(), event.getNewValue(), event.getCause());
	}

	/**
	 * Links 2 {@link ObservableCollection}s so that their contents stay identical.
	 *
	 * @param <E> The type of the values in the collections
	 * @param c1 The first collection to link
	 * @param c2 The second collection to link
	 * @return The subscription to {@link Subscription#unsubscribe()} to terminate the link and allow the collections to be independent
	 *         again
	 */
	public static <E> Subscription link(ObservableCollection<E> c1, ObservableCollection<E> c2) {
		return link(c1, c2, v -> v, v -> v,
			// The re-mapping option doesn't matter for content since the values in the collections are identical,
			// but true is more efficient since using false would require a lookup by index.
			true, true);
	}

	/**
	 * Links 2 {@link ObservableCollection}s so that their contents are a map of one another.
	 *
	 * @param <E1> The type of <code>c1</code>
	 * @param <E2> The type of <code>c2</code>
	 * @param c1 The first collection to link
	 * @param c2 The second collection to link
	 * @param map1 Produces values for <code>c2</code> from values in <code>c1</code>
	 * @param map2 Produces values for <code>c1</code> from values in <code>c2</code>
	 * @param reMapOnUpdate1 Whether values from pure updates ({@link CollectionChangeType#set} whose
	 *        {@link ObservableCollectionEvent#getOldValue() old} and {@link ObservableCollectionEvent#getNewValue() new} values are
	 *        identical) from <code>c1</code> should be re-mapped into <code>c2</code> as opposed to re-using the corresponding value in
	 *        <code>c2</code>.
	 * @param reMapOnUpdate2 Whether values from pure updates from <code>c2</code> should be re-mapped into <code>c1</code> as opposed to
	 *        re-using the corresponding value in <code>c2</code>.
	 * @return The subscription to {@link Subscription#unsubscribe()} to terminate the link and allow the collections to be independent
	 *         again
	 */
	public static <E1, E2> Subscription link(ObservableCollection<E1> c1, ObservableCollection<E2> c2, //
		Function<? super E1, ? extends E2> map1, Function<? super E2, ? extends E1> map2, //
		boolean reMapOnUpdate1, boolean reMapOnUpdate2) {
		CausableKey ck = Causable.key((cause, values) -> {
			Transaction t = (Transaction) values.get("c1Transaction");
			if (t != null)
				t.close();
			t = (Transaction) values.get("c2Transaction");
			if (t != null)
				t.close();
		});
		boolean[] isLinkChanging = new boolean[1]; // This boolean is thread-safed by the collections
		Subscription sub1 = c1.subscribe(evt -> {
			if (isLinkChanging[0])
				return;
			// This outer transaction is because locking once for a series of changes
			// is much more efficient than repeatedly locking for each change
			evt.getRootCausable().onFinish(ck).computeIfAbsent("c2Transaction",
				k -> {
					ObservableCollectionLinkEvent linkEvt = new ObservableCollectionLinkEvent(c1, evt.getRootCausable());
					return c2.lock(true, linkEvt);
				});
			// The inner transaction is so that each c1 change is causably linked to a particular c2 change
			ObservableCollectionLinkEvent linkEvt = new ObservableCollectionLinkEvent(c1, evt);
			try (Transaction linkEvtT = Causable.use(linkEvt);
				Transaction evtT = c2.lock(true, linkEvt)) {
				isLinkChanging[0] = true;
				try {
					switch (evt.getType()) {
					case add:
						c2.add(evt.getIndex(), map1.apply(evt.getNewValue()));
						break;
					case remove:
						c2.remove(evt.getIndex());
						break;
					case set:
						if (evt.getOldValue() != evt.getNewValue() || reMapOnUpdate1)
							c2.set(evt.getIndex(), map1.apply(evt.getNewValue()));
						else
							c2.set(evt.getIndex(), c2.get(evt.getIndex()));
						break;
					}
				} finally {
					isLinkChanging[0] = false;
				}
			}
		}, true);
		Subscription sub2 = c2.subscribe(evt -> {
			if (isLinkChanging[0])
				return;
			// This outer transaction is because locking once for a series of changes
			// is much more efficient than repeatedly locking for each change
			evt.getRootCausable().onFinish(ck).computeIfAbsent("c1Transaction",
				k -> c2.lock(true, new ObservableCollectionLinkEvent(c2, evt.getRootCausable())));
			// The inner transaction is so that each c2 change is causably linked to a particular c1 change
			ObservableCollectionLinkEvent linkEvt = new ObservableCollectionLinkEvent(c2, evt);
			try (Transaction linkEvtT = Causable.use(linkEvt);
				Transaction evtT = c1.lock(true, linkEvt)) {
				isLinkChanging[0] = true;
				try {
					switch (evt.getType()) {
					case add:
						c1.add(evt.getIndex(), map2.apply(evt.getNewValue()));
						break;
					case remove:
						c1.remove(evt.getIndex());
						break;
					case set:
						if (evt.getOldValue() != evt.getNewValue() || reMapOnUpdate2)
							c1.set(evt.getIndex(), map2.apply(evt.getNewValue()));
						else
							c1.set(evt.getIndex(), c1.get(evt.getIndex()));
						break;
					}
				} finally {
					isLinkChanging[0] = false;
				}
			}
		}, true);
		return Subscription.forAll(sub1, sub2);
	}

	/**
	 * An intermediate cause for events
	 * {@link ObservableUtils#link(ObservableCollection, ObservableCollection, Function, Function, boolean, boolean) linking} 2
	 * {@link ObservableCollection}s
	 */
	public static class ObservableCollectionLinkEvent extends Causable {
		private final ObservableCollection<?> theSource;

		ObservableCollectionLinkEvent(ObservableCollection<?> source, Object cause) {
			super(cause);
			theSource = source;
		}

		/**
		 * @param potentialSource An ObservableCollection to test
		 * @return Whether the given collection is this event's source
		 */
		public boolean isSource(ObservableCollection<?> potentialSource) {
			return theSource == potentialSource;
		}
	}
}
