package org.observe.collect;

import static org.observe.ObservableDebug.debug;

import java.util.RandomAccess;
import java.util.function.Predicate;

import org.observe.ObservableValue;

/**
 * An ObservableList whose contents can be accessed by index with constant (or near-constant) time
 *
 * @param <E> The type of values stored in the list
 */
public interface ObservableRandomAccessList<E> extends ObservableList<E>, RandomAccess {
	/* Overridden for performance.  get() is linear in the super, constant time here */
	@Override
	default ObservableValue<E> findLast(Predicate<E> filter) {
		return debug(new RandomAccessFinder<>(this, filter, false)).from("findLast", this).using("filter", filter).get();
	}

	/* Overridden for performance.  get() is linear in the super, constant time here */
	@Override
	default ObservableValue<E> last() {
		return debug(new RandomAccessFinder<>(this, value -> true, false)).from("last", this).get();
	}

	/**
	 * Finds values in a ObservableRandomAccessList. More performant for backward searching than {@link ObservableOrderedCollection}.
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
}
