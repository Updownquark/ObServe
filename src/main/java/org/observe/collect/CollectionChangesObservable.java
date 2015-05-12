package org.observe.collect;

import static java.util.Arrays.asList;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.observe.Observable;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.Subscription;
import org.observe.util.ListenerSet;

class CollectionChangesObservable<E, CCE extends CollectionChangeEvent<E>> implements Observable<CCE> {
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
							if(evt.getOldValue() == null)
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
								fireEventsFromSessionData(value.getOldValue());
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
					fireEventsFromSessionData(session);
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
			fireEvent((CCE) toFire);
		}
	}

	protected void fireEventsFromSessionData(CollectionSession session) {
		CollectionChangeType type = (CollectionChangeType) session.put(key, "type", null);
		if(type==null)
			return;
		List<E> elements = (List<E>) session.put(key, "elements", null);
		List<E> oldElements = (List<E>) session.put(key, "oldElements", null);
		CollectionChangeEvent<E> evt = new CollectionChangeEvent<>(type, elements, oldElements);
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