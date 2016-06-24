package org.observe.assoc;

import java.util.Collection;
import java.util.Comparator;

import org.observe.ObservableValue;
import org.observe.collect.CollectionSession;
import org.observe.collect.ObservableSortedSet;
import org.qommons.Transaction;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

/**
 * A sorted {@link ObservableMultiMap}
 *
 * @param <K> The key type of the map
 * @param <V> The value type of the map
 */
public interface ObservableSortedMultiMap<K, V> extends ObservableMultiMap<K, V> {
	/** @return The comparator by which this map's keys are sorted */
	default Comparator<? super K> comparator() {
		return keySet().comparator();
	}

	@Override
	ObservableSortedSet<K> keySet();

	/**
	 * <p>
	 * A default implementation of {@link #keySet()}.
	 * </p>
	 * <p>
	 * No {@link ObservableMultiMap} implementation may use the default implementations for its {@link #keySet()}, {@link #get(Object)}, and
	 * {@link #entrySet()} methods. {@link #defaultEntrySet(ObservableSortedMultiMap)} may not be used in the same implementation as
	 * {@link #defaultKeySet(ObservableSortedMultiMap)} or {@link ObservableMultiMap#defaultGet(ObservableMultiMap, Object)}. Either
	 * {@link #entrySet()} or both {@link #keySet()} and {@link #get(Object)} must be custom. If an implementation supplies custom
	 * {@link #keySet()} and {@link #get(Object)} implementations, it may use {@link #defaultEntrySet(ObservableSortedMultiMap)} for its
	 * {@link #entrySet()} . If an implementation supplies a custom {@link #entrySet()} implementation, it may use
	 * {@link #defaultKeySet(ObservableSortedMultiMap)} and {@link ObservableMultiMap#defaultGet(ObservableMultiMap, Object)} for its
	 * {@link #keySet()} and {@link #get(Object)} implementations, respectively. Using default implementations for both will result in
	 * infinite loops.
	 * </p>
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 * @param map The map to create a key set for
	 * @return A key set for the map
	 */
	public static <K, V> ObservableSortedSet<K> defaultKeySet(ObservableSortedMultiMap<K, V> map) {
		return ObservableSortedSet.unique(map.entrySet().map(ObservableMultiEntry::getKey), map.comparator());
	}

	@Override
	ObservableSortedSet<? extends ObservableMultiEntry<K, V>> entrySet();

	/**
	 * <p>
	 * A default implementation of {@link #entrySet()}.
	 * </p>
	 * <p>
	 * No {@link ObservableMultiMap} implementation may use the default implementations for its {@link #keySet()}, {@link #get(Object)}, and
	 * {@link #entrySet()} methods. {@link #defaultEntrySet(ObservableSortedMultiMap)} may not be used in the same implementation as
	 * {@link #defaultKeySet(ObservableSortedMultiMap)} or {@link ObservableMultiMap#defaultGet(ObservableMultiMap, Object)}. Either
	 * {@link #entrySet()} or both {@link #keySet()} and {@link #get(Object)} must be custom. If an implementation supplies custom
	 * {@link #keySet()} and {@link #get(Object)} implementations, it may use {@link #defaultEntrySet(ObservableSortedMultiMap)} for its
	 * {@link #entrySet()} . If an implementation supplies a custom {@link #entrySet()} implementation, it may use
	 * {@link #defaultKeySet(ObservableSortedMultiMap)} and {@link ObservableMultiMap#defaultGet(ObservableMultiMap, Object)} for its
	 * {@link #keySet()} and {@link #get(Object)} implementations, respectively. Using default implementations for both will result in
	 * infinite loops.
	 * </p>
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 * @param map The map to create an entry set for
	 * @return An entry set for the map
	 */
	public static <K, V> ObservableSortedSet<? extends ObservableMultiEntry<K, V>> defaultEntrySet(ObservableSortedMultiMap<K, V> map) {
		return ObservableSortedSet.unique(map.keySet().map(map::entryFor),
				(entry1, entry2) -> map.keySet().comparator().compare(entry1.getKey(), entry2.getKey()));
	}

	@Override
	default ObservableSortedMap<K, Collection<V>> asCollectionMap() {
		ObservableSortedMultiMap<K, V> outer = this;
		class SortedCollectionMap implements ObservableSortedMap<K, Collection<V>> {
			private TypeToken<Collection<V>> theValueType = new TypeToken<Collection<V>>() {}.where(new TypeParameter<V>() {},
					outer.getValueType());

			@Override
			public TypeToken<K> getKeyType() {
				return outer.getKeyType();
			}

			@Override
			public TypeToken<Collection<V>> getValueType() {
				return theValueType;
			}

			@Override
			public ObservableValue<CollectionSession> getSession() {
				return outer.getSession();
			}

			@Override
			public Transaction lock(boolean write, Object cause) {
				return outer.lock(write, cause);
			}

			@Override
			public boolean isSafe() {
				return outer.isSafe();
			}

			@Override
			public ObservableSortedSet<K> keySet() {
				return outer.keySet();
			}

			@Override
			public ObservableValue<Collection<V>> observe(Object key) {
				return outer.get(key).asValue();
			}

			@Override
			public ObservableSortedSet<? extends ObservableEntry<K, Collection<V>>> observeEntries() {
				return ObservableSortedMap.defaultObserveEntries(this);
			}
		}
		return new SortedCollectionMap();
	}
}
