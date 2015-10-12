package org.observe.collect;

import static java.util.Arrays.asList;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.observe.Observable;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.Subscription;
import org.qommons.ListenerSet;

class CollectionChangesObservable<E, CCE extends CollectionChangeEvent<E>> implements Observable<CCE> {
	protected static class SessionChangeTracker<E> {
		protected CollectionChangeType type;

		protected final List<E> elements;
		protected final List<E> oldElements;

		protected SessionChangeTracker(CollectionChangeType typ) {
			type = typ;
			elements = new ArrayList<>();
			oldElements = type == CollectionChangeType.set ? new ArrayList<>() : null;
		}

		protected void clear() {
			elements.clear();
			if(oldElements != null)
				oldElements.clear();
		}
	}

	protected static final String SESSION_TRACKER_PROPERTY = "change-tracker";

	protected final ObservableCollection<E> collection;

	protected final Object key = this;

	private final ListenerSet<Observer<? super CCE>> theObservers;

	CollectionChangesObservable(ObservableCollection<E> coll) {
		collection = coll;
		theObservers = new ListenerSet<>();

		theObservers.setUsedListener(new Consumer<Boolean>() {
			private Subscription collectSub;

			private Subscription transSub;

			@Override
			public void accept(Boolean used) {
				if(used) {
					boolean [] initialized = new boolean[1];
					collectSub = collection.onElement(element -> element.subscribe(new Observer<ObservableValueEvent<E>>() {
						@Override
						public <V2 extends ObservableValueEvent<E>> void onNext(V2 evt) {
							if(!initialized[0])
								return;
							if(evt.isInitial())
								newEvent(CollectionChangeType.add, evt);
							else
								newEvent(CollectionChangeType.set, evt);
						}

						@Override
						public <V2 extends ObservableValueEvent<E>> void onCompleted(V2 evt) {
							newEvent(CollectionChangeType.remove, evt);
						}
					}));
					initialized[0] = true;
					transSub = collection.getSession().subscribe(new Observer<ObservableValueEvent<CollectionSession>>() {
						@Override
						public <V extends ObservableValueEvent<CollectionSession>> void onNext(V value) {
							if(value.getOldValue() != null)
								fireEventsFromSessionData((SessionChangeTracker<E>) value.getOldValue().get(key, SESSION_TRACKER_PROPERTY));
						}
					});
				} else {
					collectSub.unsubscribe();
					transSub.unsubscribe();
					collectSub = null;
					transSub = null;
				}
			}
		});
	}

	@Override
	public Subscription subscribe(Observer<? super CCE> observer) {
		theObservers.add(observer);
		return () -> {
			theObservers.remove(observer);
		};
	}

	protected void newEvent(CollectionChangeType type, ObservableValueEvent<E> evt) {
		CollectionSession session = collection.getSession().get();
		if(session != null) {
			SessionChangeTracker<E> tracker = (SessionChangeTracker<E>) session.get(key, SESSION_TRACKER_PROPERTY);
			if(tracker == null) {
				tracker = new SessionChangeTracker<>(type);
				session.put(key, SESSION_TRACKER_PROPERTY, tracker);
			} else {
				if(tracker.type != type) {
					fireEventsFromSessionData(tracker);
					tracker = new SessionChangeTracker<>(type);
					session.put(key, SESSION_TRACKER_PROPERTY, tracker);
				}
			}
			tracker.elements.add(evt.getValue());
			if(tracker.oldElements != null)
				tracker.oldElements.add(evt.getOldValue());
		} else {
			CollectionChangeEvent<E> toFire = new CollectionChangeEvent<>(type, asList(evt.getValue()),
				type == CollectionChangeType.set ? asList(evt.getOldValue()) : null);
			fireEvent((CCE) toFire);
		}
	}

	protected void fireEventsFromSessionData(SessionChangeTracker<E> tracker) {
		if(tracker == null)
			return;
		CollectionChangeEvent<E> evt = new CollectionChangeEvent<>(tracker.type, tracker.elements, tracker.oldElements);
		fireEvent((CCE) evt);
	}

	protected final void fireEvent(CCE evt) {
		theObservers.forEach(observer -> observer.onNext(evt));
	}

	@Override
	public String toString() {
		return "changes(" + collection + ")";
	}
}
