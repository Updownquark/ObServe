package org.observe.collect;

import java.util.AbstractCollection;
import java.util.Iterator;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import org.observe.ObservableValue;

import prisms.lang.Type;

class CombinedObservableOrderedCollection<E, T, V> extends AbstractCollection<V> implements ObservableOrderedCollection<V> {
	private final ObservableOrderedCollection<E> theCollection;
	private final ObservableValue<T> theValue;
	private final Type theType;
	private final BiFunction<? super E, ? super T, V> theMap;

	private final SubCollectionTransactionManager theTransactionManager;

	CombinedObservableOrderedCollection(ObservableOrderedCollection<E> collection, ObservableValue<T> value, Type type,
		BiFunction<? super E, ? super T, V> map) {
		theCollection = collection;
		theValue = value;
		theType = type;
		theMap = map;
		theTransactionManager = new SubCollectionTransactionManager(theCollection);
	}

	@Override
	public ObservableValue<CollectionSession> getSession() {
		return theTransactionManager.getSession();
	}

	@Override
	public Type getType() {
		return theType;
	}

	@Override
	public int size() {
		return theCollection.size();
	}

	@Override
	public Iterator<V> iterator() {
		return new Iterator<V>() {
			private final Iterator<E> backing = theCollection.iterator();

			@Override
			public boolean hasNext() {
				return backing.hasNext();
			}

			@Override
			public V next() {
				return theMap.apply(backing.next(), theValue.get());
			}
		};
	}

	@Override
	public Runnable onOrderedElement(Consumer<? super OrderedObservableElement<V>> observer) {
		return theTransactionManager.onElement(theCollection, theValue,
			element -> observer.accept((OrderedObservableElement<V>) element.combineV(theMap, theValue)));
	}
}