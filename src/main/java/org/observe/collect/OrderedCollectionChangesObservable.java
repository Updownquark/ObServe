package org.observe.collect;

import java.util.Collection;

import org.observe.ObservableValueEvent;
import org.observe.Observer;

import prisms.util.IntList;

class OrderedCollectionChangesObservable<E, OCCE extends OrderedCollectionChangeEvent<E>> extends
CollectionChangesObservable<E, OCCE> {
	OrderedCollectionChangesObservable(ObservableOrderedCollection<E> coll) {
		super(coll);
	}

	@Override
	protected void newEvent(CollectionChangeType type, ObservableValueEvent<E> evt, Observer<? super OCCE> observer) {
		CollectionSession session = collection.getSession().get();
		int index = ((OrderedObservableElement<E>) evt.getObservable()).getIndex();
		if(session != null) {
			CollectionChangeType preType = (CollectionChangeType) session.get(key, "type");
			Collection<E> elements;
			IntList indexes;
			if(preType == null) {
				session.put(key, "type", type);
				elements = new java.util.ArrayList<>();
				indexes = new IntList();
				session.put(key, "elements", elements);
				session.put(key, "indexes", indexes);
			} else {
				if(preType != type) {
					fireEventsFromSessionData(session, observer);
					session.put(key, "type", type);
					elements = new java.util.ArrayList<>();
					indexes = new IntList();
					session.put(key, "elements", elements);
					session.put(key, "indexes", indexes);
				} else {
					elements = (Collection<E>) session.get(key, "elements");
					indexes = (IntList) session.get(key, "indexes");
				}
			}
			elements.add(evt.getValue());
			indexes.add(index);
		} else {
			OrderedCollectionChangeEvent<E> toFire = new OrderedCollectionChangeEvent<>(type, evt.getValue(), index);
			observer.onNext((OCCE) toFire);
		}
	}

	@Override
	protected void fireEventsFromSessionData(CollectionSession session, Observer<? super OCCE> observer) {
		CollectionChangeType type = (CollectionChangeType) session.get(key, "type");
		if(type == null)
			return;
		Collection<E> elements = (Collection<E>) session.put(key, "elements", null);
		IntList indexes = (IntList) session.get(key, "indexes");
		OrderedCollectionChangeEvent<E> evt = new OrderedCollectionChangeEvent<>(type, elements, indexes);
		observer.onNext((OCCE) evt);
	}
}
