package org.observe.collect;

import java.util.Comparator;

import org.observe.util.TypeTokens;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.CollectionElement;

import com.google.common.reflect.TypeToken;

/**
 * An {@link ObservableSortedSet} implementation based on a backing {@link BetterSortedSet}
 *
 * @param <E> The type of values in the set
 */
public class DefaultObservableSortedSet<E> extends DefaultObservableCollection<E> implements ObservableSortedSet<E> {
	private final Equivalence<? super E> theEquivalence;

	/**
	 * @param type The type for the sorted set
	 * @param sortedSet The backing sorted set to hold this observable set's values
	 */
	public DefaultObservableSortedSet(TypeToken<E> type, BetterSortedSet<E> sortedSet) {
		super(type, sortedSet);
		theEquivalence = Equivalence.of(TypeTokens.getRawType(type), sortedSet.comparator(), false);
	}

	@Override
	protected BetterSortedSet<E> getValues() {
		return (BetterSortedSet<E>) super.getValues();
	}

	@Override
	public int indexFor(Comparable<? super E> search) {
		return getValues().indexFor(search);
	}

	@Override
	public CollectionElement<E> search(Comparable<? super E> search, BetterSortedSet.SortedSearchFilter filter) {
		return getValues().search(search, filter);
	}

	@Override
	public Comparator<? super E> comparator() {
		return getValues().comparator();
	}

	@Override
	public Equivalence<? super E> equivalence() {
		return theEquivalence;
	}
}
