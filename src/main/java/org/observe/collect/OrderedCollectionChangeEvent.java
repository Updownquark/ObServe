package org.observe.collect;

import java.util.Collection;

import prisms.util.IntList;

public class OrderedCollectionChangeEvent<E> extends CollectionChangeEvent<E> {
	public IntList indexes;

	public OrderedCollectionChangeEvent(CollectionChangeType type, Collection<E> values, IntList idxs) {
		super(type, values);
		indexes = idxs;
		indexes.seal();
	}

	public OrderedCollectionChangeEvent(CollectionChangeType type, E value, int idx) {
		super(type, value);
		indexes = new IntList(new int[] {idx});
		indexes.seal();
	}
}
