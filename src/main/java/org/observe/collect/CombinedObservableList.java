package org.observe.collect;

import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import java.util.function.BiFunction;

import org.observe.ObservableValue;

import prisms.lang.Type;

public class CombinedObservableList<E, T, V> extends CombinedObservableOrderedCollection<E, T, V> implements ObservableList<V> {
	CombinedObservableList(ObservableList<E> collection, ObservableValue<T> value, Type type, BiFunction<? super E, ? super T, V> map) {
		super(collection, value, type, map);
	}

	@Override
	public boolean addAll(int index, Collection<? extends V> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public V get(int index) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public V set(int index, V element) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void add(int index, V element) {
		throw new UnsupportedOperationException();
	}

	@Override
	public V remove(int index) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int indexOf(Object o) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int lastIndexOf(Object o) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public SimpleListIterator<V> listIterator() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public SimpleListIterator<V> listIterator(int index) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<V> subList(int fromIndex, int toIndex) {
		// TODO Auto-generated method stub
		return null;
	}
}
