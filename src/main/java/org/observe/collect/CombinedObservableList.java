package org.observe.collect;

import java.util.function.BiFunction;
import java.util.function.Consumer;

import org.observe.ObservableValue;

import prisms.lang.Type;

class CombinedObservableList<E, T, V> extends CombinedObservableOrderedCollection<E, T, V> implements PartialListImpl<V> {
	CombinedObservableList(ObservableList<E> collection, ObservableValue<T> value, Type type, BiFunction<? super E, ? super T, V> map) {
		super(collection, value, type, map);
	}

	@Override
	ObservableList<E> getCollection() {
		return (ObservableList<E>) super.getCollection();
	}

	@Override
	public V get(int index) {
		return getMap().apply(getCollection().get(index), getValue().get());
	}

	@Override
	public V remove(int index) {
		return getMap().apply(getCollection().remove(index), getValue().get());
	}

	@Override
	public void removeRange(int fromIndex, int toIndex) {
		for(int i = fromIndex; i < toIndex; i++)
			getCollection().remove(i);
	}

	@Override
	public Runnable onElementReverse(Consumer<? super OrderedObservableElement<V>> onElement) {
		return getManager().onElement(getCollection(), getValue(),
			element -> onElement.accept((OrderedObservableElement<V>) element.combineV(getMap(), getValue())), false);
	}
}
