package org.observe.util;

import java.util.Collection;
import java.util.Comparator;
import java.util.function.Consumer;
import java.util.function.Function;

import org.qommons.Transaction;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.collect.MutableElementSpliterator;

/**
 * A sorted set that is the result of a mapping operation applied to a source set. The mapped values must obey the same ordering as the
 * source values.
 *
 * @param <E> The type of the source set
 * @param <T> The type of the mapped set
 */
public class MappedSortedSet<E, T> implements BetterSortedSet<T> {
	private final BetterSortedSet<E> theSource;
	private final Function<? super E, ? extends T> theMap;
	private final Function<? super T, ? extends E> theReverse;

	public MappedSortedSet(BetterSortedSet<E> source, Function<? super E, ? extends T> map, Function<? super T, ? extends E> reverse) {
		theSource = source;
		theMap = map;
		theReverse = reverse;
	}

	@Override
	public boolean belongs(Object o) {
		return true;
	}

	@Override
	public boolean isLockSupported() {
		return theSource.isLockSupported();
	}

	@Override
	public Transaction lock(boolean write, boolean structural, Object cause) {
		return theSource.lock(write, structural, cause);
	}

	@Override
	public long getStamp(boolean structuralOnly) {
		return theSource.getStamp(structuralOnly);
	}

	@Override
	public int size() {
		return theSource.size();
	}

	@Override
	public boolean isEmpty() {
		return theSource.isEmpty();
	}

	@Override
	public CollectionElement<T> getElement(ElementId id) {
		return new MappedElement(theSource.getElement(id));
	}

	@Override
	public CollectionElement<T> getElement(int index) {
		return new MappedElement(theSource.getElement(index));
	}

	@Override
	public int getElementsBefore(ElementId id) {
		return theSource.getElementsBefore(id);
	}

	@Override
	public int getElementsAfter(ElementId id) {
		return theSource.getElementsAfter(id);
	}

	@Override
	public MutableCollectionElement<T> mutableElement(ElementId id) {
		return new MappedMutableElement(theSource.mutableElement(id));
	}

	@Override
	public MutableElementSpliterator<T> spliterator(boolean fromStart) {
		return new MappedSpliterator(theSource.spliterator(fromStart));
	}

	@Override
	public MutableElementSpliterator<T> spliterator(ElementId element, boolean asNext) {
		return new MappedSpliterator(theSource.spliterator(element, asNext));
	}

	@Override
	public Comparator<? super T> comparator() {
		return (v1, v2) -> theSource.comparator().compare(theReverse.apply(v1), theReverse.apply(v2));
	}

	@Override
	public CollectionElement<T> addIfEmpty(T value) throws IllegalStateException {
		CollectionElement<E> srcEl = theSource.addIfEmpty(theReverse.apply(value));
		return srcEl == null ? null : new MappedElement(srcEl);
	}

	@Override
	public boolean addAll(Collection<? extends T> c) {
		return BetterSortedSet.super.addAll(c);
	}

	@Override
	public <X> X[] toArray(X[] a) {
		return BetterSortedSet.super.toArray(a);
	}

	@Override
	public int indexFor(Comparable<? super T> search) {
		return theSource.indexFor(src -> search.compareTo(theMap.apply(src)));
	}

	@Override
	public CollectionElement<T> search(Comparable<? super T> search, org.qommons.collect.BetterSortedSet.SortedSearchFilter filter) {
		CollectionElement<E> srcEl = theSource.search(src -> search.compareTo(theMap.apply(src)), filter);
		return srcEl == null ? null : new MappedElement(srcEl);
	}

	@Override
	public void clear() {
		theSource.clear();
	}

	private class MappedElement implements CollectionElement<T> {
		private final CollectionElement<E> theSourceEl;

		MappedElement(CollectionElement<E> sourceEl) {
			theSourceEl = sourceEl;
		}

		@Override
		public ElementId getElementId() {
			return theSourceEl.getElementId();
		}

		@Override
		public T get() {
			return theMap.apply(theSourceEl.get());
		}
	}

	private class MappedMutableElement implements MutableCollectionElement<T> {
		private final MutableCollectionElement<E> theSourceEl;

		MappedMutableElement(MutableCollectionElement<E> sourceEl) {
			theSourceEl = sourceEl;
		}

		@Override
		public ElementId getElementId() {
			return theSourceEl.getElementId();
		}

		@Override
		public T get() {
			return theMap.apply(theSourceEl.get());
		}

		@Override
		public String isEnabled() {
			return theSourceEl.isEnabled();
		}

		@Override
		public String isAcceptable(T value) {
			return theSourceEl.isAcceptable(theReverse.apply(value));
		}

		@Override
		public void set(T value) throws UnsupportedOperationException, IllegalArgumentException {
			theSourceEl.set(theReverse.apply(value));
		}

		@Override
		public String canRemove() {
			return theSourceEl.canRemove();
		}

		@Override
		public void remove() throws UnsupportedOperationException {
			theSourceEl.remove();
		}

		@Override
		public String canAdd(T value, boolean before) {
			return theSourceEl.canAdd(theReverse.apply(value), before);
		}

		@Override
		public ElementId add(T value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
			return theSourceEl.add(theReverse.apply(value), before);
		}
	}

	private class MappedSpliterator implements MutableElementSpliterator<T> {
		private final MutableElementSpliterator<E> theSourceSpliter;

		MappedSpliterator(MutableElementSpliterator<E> sourceSpliter) {
			theSourceSpliter = sourceSpliter;
		}

		@Override
		public boolean forElement(Consumer<? super CollectionElement<T>> action, boolean forward) {
			return theSourceSpliter.forElement(el -> action.accept(new MappedElement(el)), forward);
		}

		@Override
		public void forEachElement(Consumer<? super CollectionElement<T>> action, boolean forward) {
			theSourceSpliter.forEachElement(el -> action.accept(new MappedElement(el)), forward);
		}

		@Override
		public long estimateSize() {
			return theSourceSpliter.estimateSize();
		}

		@Override
		public int characteristics() {
			return theSourceSpliter.characteristics();
		}

		@Override
		public boolean forElementM(Consumer<? super MutableCollectionElement<T>> action, boolean forward) {
			return theSourceSpliter.forElementM(el -> action.accept(new MappedMutableElement(el)), forward);
		}

		@Override
		public void forEachElementM(Consumer<? super MutableCollectionElement<T>> action, boolean forward) {
			theSourceSpliter.forEachElementM(el -> action.accept(new MappedMutableElement(el)), forward);
		}

		@Override
		public MutableElementSpliterator<T> trySplit() {
			MutableElementSpliterator<E> srcSplit = theSourceSpliter.trySplit();
			return srcSplit == null ? null : new MappedSpliterator(srcSplit);
		}
	}
}
