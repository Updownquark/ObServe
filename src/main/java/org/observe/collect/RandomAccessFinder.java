package org.observe.collect;

import java.util.function.Predicate;

/**
 * Finds values in a ObservableRandomAccessList. More performant for backward searching than {@link OrderedCollectionFinder}.
 *
 * @param <E> The type of value to find
 */
public class RandomAccessFinder<E> extends OrderedCollectionFinder<E> {
	RandomAccessFinder(ObservableRandomAccessList<E> collection, Predicate<? super E> filter, boolean forward) {
		super(collection, filter, forward);
	}

	@Override
	public ObservableRandomAccessList<E> getCollection() {
		return (ObservableRandomAccessList<E>) super.getCollection();
	}

	@Override
	public E get() {
		if(isForward())
			return super.get();
		else {
			ObservableRandomAccessList<E> list = getCollection();
			for(int i = list.size() - 1; i >= 0; i--) {
				E value = list.get(i);
				if(getFilter().test(value))
					return value;
			}
			return null;
		}
	}
}
