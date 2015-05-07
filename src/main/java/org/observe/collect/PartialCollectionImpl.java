package org.observe.collect;

import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;

/**
 * An extension of ObservableCollection that implements some of the redundant methods and throws UnsupportedOperationExceptions for
 * modifications. Mostly copied from {@link AbstractCollection}.
 *
 * @param <E> The type of element in the collection
 */
public interface PartialCollectionImpl<E> extends ObservableCollection<E> {
	@Override
	default boolean add(E e) {
		throw new UnsupportedOperationException();
	}

	@Override
	default boolean remove(Object o) {
		Iterator<E> it = iterator();
		if(o == null) {
			while(it.hasNext()) {
				if(it.next() == null) {
					it.remove();
					return true;
				}
			}
		} else {
			while(it.hasNext()) {
				if(o.equals(it.next())) {
					it.remove();
					return true;
				}
			}
		}
		return false;
	}

	@Override
	default boolean addAll(Collection<? extends E> c) {
		boolean modified = false;
		for(E e : c)
			if(add(e))
				modified = true;
		return modified;
	}

	@Override
	default boolean removeAll(Collection<?> c) {
		Objects.requireNonNull(c);
		boolean modified = false;
		Iterator<?> it = iterator();
		while(it.hasNext()) {
			if(c.contains(it.next())) {
				it.remove();
				modified = true;
			}
		}
		return modified;
	}

	@Override
	default boolean retainAll(Collection<?> c) {
		Objects.requireNonNull(c);
		boolean modified = false;
		Iterator<E> it = iterator();
		while(it.hasNext()) {
			if(!c.contains(it.next())) {
				it.remove();
				modified = true;
			}
		}
		return modified;
	}

	@Override
	default void clear() {
		Iterator<E> it = iterator();
		while(it.hasNext()) {
			it.next();
			it.remove();
		}
	}
}
