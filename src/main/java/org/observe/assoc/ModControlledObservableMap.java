package org.observe.assoc;

import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.Equivalence;
import org.observe.collect.ModControlledObservableCollection;
import org.observe.collect.ObservableSet;
import org.observe.collect.ObservableSortedSet;
import org.qommons.Subscription;
import org.qommons.collect.ElementId;
import org.qommons.collect.ModControlledMap;
import org.qommons.collect.MutableOrderedMapEntry;
import org.qommons.collect.OrderedMapEntry;

/**
 * Observable extension of {@link ModControlledMap}
 *
 * @param <K> The key type of the map
 * @param <V> The value type of the map
 * @param <M> The sub-type of ObservableMap
 */
public class ModControlledObservableMap<K, V, M extends ObservableMap<K, V>> extends ModControlledMap<K, V, M>
implements ObservableMap<K, V> {
	/**
	 * Creates a modification-controlled ObservableMap
	 *
	 * @param <K> The type of keys in the map
	 * @param <V> The type of values in the map
	 * @param <M> The sub type of the map
	 * @param map The map to control
	 * @param control The optional modification controller
	 * @param listener The optional modification listener
	 * @return The modification-controlled ObservableMap
	 */
	public static <K, V, M extends ObservableMap<K, V>> M controlMap(M map, MapModificationControl<K, V> control,
		MapModificationListener<K, V> listener) {
		if (map instanceof ObservableSortedMap)
			return (M) new MCOSortedMap<>((ObservableSortedMap<K, V>) map, control, listener);
		else
			return (M) new ModControlledObservableMap<>(map, control, listener);
	}

	/**
	 * @param backing The map to control
	 * @param control The optional modification controller
	 * @param listener The optional modification listener
	 */
	public ModControlledObservableMap(M backing, MapModificationControl<K, V> control, MapModificationListener<K, V> listener) {
		super(backing, control, listener);
	}

	@Override
	public ModControlledObservableMap<K, V, M> alias(String alias) {
		super.alias(alias);
		return this;
	}

	@Override
	public boolean isEventing() {
		return getBacking().isEventing();
	}

	@Override
	public Equivalence<? super V> equivalence() {
		return getBacking().equivalence();
	}

	@Override
	public ObservableSet<K> keySet() {
		return (ObservableSet<K>) super.keySet();
	}

	@Override
	protected ObservableSet<K> createKeySet() {
		return ModControlledObservableCollection.controlCollection(getBacking().keySet(),
			new KeySetControl<>(getBacking(), getControl()), new KeySetModListener<>(getBacking(), getListener()));
	}

	@Override
	public OrderedMapEntry<K, V> getEntry(K key) {
		return (OrderedMapEntry<K, V>) super.getEntry(key);
	}

	@Override
	public OrderedMapEntry<K, V> getEntryById(ElementId entryId) {
		return (OrderedMapEntry<K, V>) super.getEntryById(entryId);
	}

	@Override
	public OrderedMapEntry<K, V> getOrPutEntry(K key, Function<? super K, ? extends V> value, ElementId after, ElementId before,
		boolean first, Runnable preAdd, Runnable postAdd) {
		return (OrderedMapEntry<K, V>) super.getOrPutEntry(key, value, after, before, first, preAdd, postAdd);
	}

	@Override
	public MutableOrderedMapEntry<K, V> mutableEntry(ElementId entryId) {
		return (MutableOrderedMapEntry<K, V>) super.mutableEntry(entryId);
	}

	@Override
	public Subscription onChange(Consumer<? super ObservableMapEvent<? extends K, ? extends V>> action) {
		return getBacking().onChange(action);
	}

	/**
	 * SortedObservableMap implementation for {@link ModControlledObservableMap}
	 *
	 * @param <K> The type of keys in the map
	 * @param <V> The type of values in the map
	 * @param <M> The sub type of this sorted map
	 */
	public static class MCOSortedMap<K, V, M extends ObservableSortedMap<K, V>> extends ModControlledMap.ModControlledSortedMap<K, V, M>
	implements ObservableSortedMap<K, V> {
		/**
		 * @param backing The map to control
		 * @param control The optional modification controller
		 * @param listener The optional modification listener
		 */
		public MCOSortedMap(M backing, MapModificationControl<K, V> control, MapModificationListener<K, V> listener) {
			super(backing, control, listener);
		}

		@Override
		public MCOSortedMap<K, V, M> alias(String alias) {
			super.alias(alias);
			return this;
		}

		@Override
		public boolean isEventing() {
			return getBacking().isEventing();
		}

		@Override
		public Equivalence<? super V> equivalence() {
			return getBacking().equivalence();
		}

		@Override
		public ObservableSortedSet<K> keySet() {
			return (ObservableSortedSet<K>) super.keySet();
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableMapEvent<? extends K, ? extends V>> action) {
			return getBacking().onChange(action);
		}

	}
}
