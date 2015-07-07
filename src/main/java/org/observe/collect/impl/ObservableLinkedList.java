package org.observe.collect.impl;

import java.util.function.Consumer;

import org.observe.ObservableValue;
import org.observe.Subscription;
import org.observe.collect.CollectionSession;
import org.observe.collect.ObservableElement;
import org.observe.collect.ObservableList;
import org.observe.collect.OrderedObservableElement;
import org.observe.collect.ObservableList.PartialListImpl;
import org.observe.util.Transaction;

import prisms.lang.Type;

public class ObservableLinkedList<E> implements ObservableList.PartialListImpl<E> {

	@Override
	public Subscription onOrderedElement(Consumer<? super OrderedObservableElement<E>> onElement) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Type getType() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Subscription onElement(Consumer<? super ObservableElement<E>> onElement) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ObservableValue<CollectionSession> getSession() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public E get(int index) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int size() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public Transaction lock(boolean write, Object cause) {
		// TODO Auto-generated method stub
		return null;
	}
}
