package org.observe.collect;

import java.util.Collections;

import org.observe.ObservableValue;
import org.qommons.ex.CheckedExceptionWrapper;

import com.google.common.reflect.TypeToken;

public interface DataControlledCollection<E, V> extends ObservableCollection<E> {
	public static <E, V> DataControlledCollection<E, V> empty(TypeToken<E> type) {
		return new ObservableCollectionBuilder.DataControlledCollectionBuilderImpl<>(ObservableCollection.of(type),
			Collections::<V> emptyList).build(v -> null, null);
	}

	long getMaxRefreshFrequency();
	DataControlledCollection<E, V> setMaxRefreshFrequency(long frequency);

	/**
	 * Attempts to synchronize this collection with its backing data source
	 *
	 * @return Whether the synchronization was successful. Synchronization can fail (without exception) if:
	 *         <ul>
	 *         <li>The collection has been synchronized too recently (@see {@link #setMaxRefreshFrequency(long)})</li>
	 *         <li>The collection is locked by another thread. This situation can be avoided by calling this method within a write lock on
	 *         the collection. This method will always return false if called within a read lock (unless a write lock is also held by the
	 *         current thread).</li>
	 *         </ul>
	 * @throws CheckedExceptionWrapper If the configured adjustment throws a checked exception
	 */
	boolean refresh();

	ObservableValue<Boolean> isRefreshing();

	public interface Set<E, V> extends DataControlledCollection<E, V>, ObservableSet<E> {
		@Override
		Set<E, V> setMaxRefreshFrequency(long frequency);
	}

	public interface Sorted<E, V> extends DataControlledCollection<E, V>, ObservableSortedCollection<E> {
		@Override
		Sorted<E, V> setMaxRefreshFrequency(long frequency);
	}

	public interface SortedSet<E, V> extends Set<E, V>, Sorted<E, V>, ObservableSortedSet<E> {
		@Override
		SortedSet<E, V> setMaxRefreshFrequency(long frequency);
	}
}
