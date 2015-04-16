package org.observe.collect;

import java.util.Collection;

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
		Runnable collectSub = collection.onElement(element -> element.observe(new Observer<ObservableValueEvent<E>>() {
			@Override
			public <V2 extends ObservableValueEvent<E>> void onNext(V2 evt) {
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
			Collection<E> elements;
			if(preType == null) {
				session.put(key, "type", type);
				elements = new java.util.ArrayList<>();
				session.put(key, "elements", elements);
			} else{
				if(preType!=type){
					fireEventsFromSessionData(session, observer);
					session.put(key, "type", type);
					elements = new java.util.ArrayList<>();
					session.put(key, "elements", elements);
				} else
					elements = (Collection<E>) session.get(key, "elements");
			}
			elements.add(evt.getValue());
		} else {
			CollectionChangeEvent<E> toFire = new CollectionChangeEvent<>(type, evt.getValue());
			observer.onNext((CCE) toFire);
		}
	}

	protected void fireEventsFromSessionData(CollectionSession session, Observer<? super CCE> observer) {
		CollectionChangeType type=(CollectionChangeType) session.get(key, "type");
		if(type==null)
			return;
		Collection<E> elements = (Collection<E>) session.put(key, "elements", null);
		CollectionChangeEvent<E> evt=new CollectionChangeEvent<>(type, elements);
		observer.onNext((CCE) evt);
	}

	@Override
	public String toString() {
		return "changes(" + collection + ")";
	}
}