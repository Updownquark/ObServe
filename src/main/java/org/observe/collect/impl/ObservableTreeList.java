package org.observe.collect.impl;

import java.util.function.Consumer;

import org.observe.ObservableValue;
import org.observe.Subscription;
import org.observe.collect.CollectionSession;
import org.observe.collect.ObservableElement;
import org.observe.collect.ObservableList;
import org.observe.collect.ObservableList.PartialListImpl;

import prisms.lang.Type;

public class ObservableTreeList<E> implements PartialListImpl<E> {


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
	public int size() {
		// TODO Auto-generated method stub
		return 0;
	}
}
