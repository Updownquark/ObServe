package org.observe.collect;

import static java.util.Arrays.asList;

import java.util.ArrayList;
import java.util.List;

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
			List<E> elements;
			List<E> oldElements = null;
			IntList indexes;
			if(preType == null) {
				session.put(key, "type", type);
				elements = new java.util.ArrayList<>();
				indexes = new IntList();
				session.put(key, "elements", elements);
				if(type == CollectionChangeType.set) {
					oldElements = new ArrayList<>();
					session.put(key, "oldElements", oldElements);
				}
				session.put(key, "indexes", indexes);
			} else {
				if(preType != type) {
					fireEventsFromSessionData(session, observer);
					session.put(key, "type", type);
					elements = new java.util.ArrayList<>();
					indexes = new IntList();
					session.put(key, "elements", elements);
					if(type == CollectionChangeType.set) {
						oldElements = new ArrayList<>();
						session.put(key, "oldElements", oldElements);
					}
					session.put(key, "indexes", indexes);
				} else {
					elements = (List<E>) session.get(key, "elements");
					oldElements = (List<E>) session.get(key, "oldElements");
					indexes = (IntList) session.get(key, "indexes");
				}
			}
			elements.add(evt.getValue());
			if(oldElements != null)
				oldElements.add(evt.getOldValue());
			indexes.add(index);
		} else {
			OrderedCollectionChangeEvent<E> toFire = new OrderedCollectionChangeEvent<>(type, asList(evt.getValue()),
				type == CollectionChangeType.set ? asList(evt.getOldValue()) : null, new IntList(new int[] {index}));
			observer.onNext((OCCE) toFire);
		}
	}

	@Override
	protected void fireEventsFromSessionData(CollectionSession session, Observer<? super OCCE> observer) {
		CollectionChangeType type = (CollectionChangeType) session.get(key, "type");
		if(type == null)
			return;
		List<E> elements = (List<E>) session.put(key, "elements", null);
		List<E> oldElements = (List<E>) session.put(key, "oldElements", null);
		IntList indexes = (IntList) session.get(key, "indexes");
		OrderedCollectionChangeEvent<E> evt = new OrderedCollectionChangeEvent<>(type, elements, oldElements, indexes);
		observer.onNext((OCCE) evt);
	}
}
