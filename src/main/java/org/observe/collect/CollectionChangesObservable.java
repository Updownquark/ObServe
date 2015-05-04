package org.observe.collect;

import static java.util.Arrays.asList;

import java.util.ArrayList;
import java.util.List;

import org.observe.Observable;
import org.observe.ObservableValueEvent;
import org.observe.Observer;

class CollectionChangesObservable<E, CCE extends CollectionChangeEvent<E>> implements Observable<CCE> {
	protected final ObservableCollection<E> collection;
	protected final Object key = this;

	CollectionChangesObservable(ObservableCollection<E> coll) {
		collection = coll;
	}

	@Override
	public Runnable observe(Observer<? super CCE> observer) {
		boolean [] initialized = new boolean[1];
		Runnable collectSub = collection.onElement(element -> element.observe(new Observer<ObservableValueEvent<E>>() {
			@Override
			public <V2 extends ObservableValueEvent<E>> void onNext(V2 evt) {
				if(!initialized[0])
					return;
				if(evt.getOldValue() == null)
					newEvent(CollectionChangeType.add, evt, observer);
				else
					newEvent(CollectionChangeType.set, evt, observer);
			}

			@Override
			public <V2 extends ObservableValueEvent<E>> void onCompleted(V2 evt) {
				newEvent(CollectionChangeType.remove, evt, observer);
			}
		}));
		initialized[0] = true;
		Runnable transSub = collection.getSession().observe(new Observer<ObservableValueEvent<CollectionSession>>() {
			@Override
			public <V extends ObservableValueEvent<CollectionSession>> void onNext(V value) {
				if(value.getOldValue() != null)
					fireEventsFromSessionData(value.getOldValue(), observer);
			}
		});
		return () -> {
			collectSub.run();
			transSub.run();
		};
	}

	protected void newEvent(CollectionChangeType type, ObservableValueEvent<E> evt, Observer<? super CCE> observer) {
		CollectionSession session = collection.getSession().get();
		if(session != null) {
			CollectionChangeType preType = (CollectionChangeType) session.get(key, "type");
			List<E> elements;
			List<E> oldElements = null;
			if(preType == null) {
				session.put(key, "type", type);
				elements = new ArrayList<>();
				session.put(key, "elements", elements);
				if(type == CollectionChangeType.set) {
					oldElements = new ArrayList<>();
					session.put(key, "oldElements", oldElements);
				}
			} else{
				if(preType!=type){
					fireEventsFromSessionData(session, observer);
					session.put(key, "type", type);
					elements = new ArrayList<>();
					session.put(key, "elements", elements);
					if(type == CollectionChangeType.set) {
						oldElements = new ArrayList<>();
						session.put(key, "oldElements", oldElements);
					}
				} else {
					elements = (List<E>) session.get(key, "elements");
					oldElements = (List<E>) session.get(key, "oldElements");
				}
			}
			elements.add(evt.getValue());
			if(oldElements != null)
				oldElements.add(evt.getOldValue());
		} else {
			CollectionChangeEvent<E> toFire = new CollectionChangeEvent<>(type, asList(evt.getValue()),
				type == CollectionChangeType.set ? asList(evt.getOldValue()) : null);
			observer.onNext((CCE) toFire);
		}
	}

	protected void fireEventsFromSessionData(CollectionSession session, Observer<? super CCE> observer) {
		CollectionChangeType type=(CollectionChangeType) session.get(key, "type");
		if(type==null)
			return;
		List<E> elements = (List<E>) session.put(key, "elements", null);
		List<E> oldElements = (List<E>) session.put(key, "oldElements", null);
		CollectionChangeEvent<E> evt = new CollectionChangeEvent<>(type, elements, oldElements);
		observer.onNext((CCE) evt);
	}

	@Override
	public String toString() {
		return "changes(" + collection + ")";
	}
}