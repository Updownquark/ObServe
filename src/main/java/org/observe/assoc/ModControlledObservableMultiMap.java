package org.observe.assoc;

import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.assoc.impl.GeneralMultiMapFlow;
import org.observe.collect.ModControlledObservableCollection;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableSet;
import org.observe.collect.ObservableSortedCollection;
import org.observe.collect.ObservableSortedCollectionImpl;
import org.observe.collect.ObservableSortedSet;
import org.observe.collect.ObservableSortedSetImpl;
import org.qommons.Subscription;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.ElementId;
import org.qommons.collect.ModControlledMultiMap;
import org.qommons.collect.OrderedMultiEntry;

/**
 * Observable extension of {@link ModControlledMultiMap}
 *
 * @param <K> The key type of the multi-map
 * @param <V> The value type of the multi-map
 * @param <M> The sub-type of ObservableMultiMap
 */
public class ModControlledObservableMultiMap<K, V, M extends ObservableMultiMap<K, V>> extends ModControlledMultiMap<K, V, M>
implements ObservableMultiMap<K, V> {
	/**
	 * Creates a modification-controlled ObservableMultiMap
	 *
	 * @param <K> The type of keys in the multi-map
	 * @param <V> The type of values in the multi-map
	 * @param <M> The sub type of the multi-map
	 * @param map The multi-map to control
	 * @param control The optional modification controller
	 * @param listener The optional modification listener
	 * @return The modification-controlled ObservableMultiMap
	 */
	public static <K, V, M extends ObservableMultiMap<K, V>> M controlMultiMap(M map, MultiMapModificationControl<K, V> control,
		MultiMapModificationListener<K, V> listener) {
		if (map instanceof ObservableSortedMultiMap)
			return (M) new MCOSortedMultiMap<>((ObservableSortedMultiMap<K, V>) map, control, listener);
		else
			return (M) new ModControlledObservableMultiMap<>(map, control, listener);
	}

	/**
	 * @param backing The multi-map to control
	 * @param control The optional modification controller
	 * @param listener The optional modification listener
	 */
	public ModControlledObservableMultiMap(M backing, MultiMapModificationControl<K, V> control,
		MultiMapModificationListener<K, V> listener) {
		super(backing, control, listener);
	}

	@Override
	public ObservableMultiMap<K, V> alias(String alias) {
		super.alias(alias);
		return this;
	}

	@Override
	public boolean isEventing() {
		return getBacking().isEventing();
	}

	@Override
	public boolean isLockSupported() {
		return getBacking().isLockSupported();
	}

	@Override
	public ObservableSet<K> keySet() {
		return (ObservableSet<K>) super.keySet();
	}

	@Override
	protected ObservableSet<K> createKeySet() {
		return ModControlledObservableCollection.controlCollection(getBacking().keySet(), createKeyControl(),
			createKeyModListener());
	}

	@Override
	public OrderedMultiEntry<K, V> getEntryById(ElementId keyId) {
		return (OrderedMultiEntry<K, V>) super.getEntryById(keyId);
	}

	@Override
	public OrderedMultiEntry<K, V> getOrPutEntry(K key, Function<? super K, ? extends Iterable<? extends V>> value, ElementId afterKey,
		ElementId beforeKey, boolean first, Runnable preAdd, Runnable postAdd) {
		return (OrderedMultiEntry<K, V>) super.getOrPutEntry(key, value, afterKey, beforeKey, first, preAdd, postAdd);
	}

	@Override
	public ObservableCollection<V> get(K key) {
		return (ObservableCollection<V>) super.get(key);
	}

	@Override
	public ObservableMultiEntry<K, V> watchById(ElementId keyId) {
		return wrapObservableEntry(getBacking().watchById(keyId));
	}

	@Override
	public ObservableMultiEntry<K, V> watch(K key) {
		return wrapObservableEntry(getBacking().watch(key));
	}

	@Override
	public Subscription onChange(Consumer<? super ObservableMultiMapEvent<? extends K, ? extends V>> action) {
		return getBacking().onChange(action);
	}

	@Override
	public MultiMapFlow<K, V> flow() {
		return GeneralMultiMapFlow.init(this);
	}

	@Override
	protected BetterCollection<V> wrapValues(K key, BetterCollection<V> values) {
		if (values instanceof ObservableCollection)
			return ModControlledObservableCollection.controlCollection((ObservableCollection<V>) values, new ValuesControl(key),
				new ValuesListener(key));
		else
			return super.wrapValues(key, values);
	}

	/**
	 * @param backingEntry The source {@link ObservableMultiMap.ObservableMultiEntry} to wrap
	 * @return The modification-controlled wrapping multi-entry
	 */
	protected ObservableMultiEntry<K, V> wrapObservableEntry(ObservableMultiEntry<K, V> backingEntry) {
		ValuesControl control = new ValuesControl(backingEntry.getKey());
		ValuesListener listener = new ValuesListener(backingEntry.getKey());
		if (backingEntry instanceof ObservableSortedSet)
			return new MCOSortedObservableSetEntry<>(backingEntry, control, listener);
		else if (backingEntry instanceof ObservableSortedCollection)
			return new MCOSortedObservableEntry<>(backingEntry, control, listener);
		else
			return new MCOObservableEntry<>(backingEntry, control, listener);
	}

	static class MCOObservableEntry<K, V> extends ModControlledObservableCollection<V, ObservableMultiEntry<K, V>>
	implements ObservableMultiEntry<K, V> {
		public MCOObservableEntry(ObservableMultiEntry<K, V> backing, CollectionModificationControl<V> control,
			CollectionModificationListener<V> listener) {
			super(backing, control, listener);
		}

		@Override
		public K getKey() {
			return getBacking().getKey();
		}

		@Override
		public ElementId getKeyId() {
			return getBacking().getKeyId();
		}
	}

	static class MCOSortedObservableEntry<K, V> extends
	ModControlledObservableCollection.MCOSortedCollection<V, ObservableSortedCollection<V>> implements ObservableMultiEntry<K, V> {
		public MCOSortedObservableEntry(ObservableMultiEntry<K, V> backing, CollectionModificationControl<V> control,
			CollectionModificationListener<V> listener) {
			super((ObservableSortedCollection<V>) backing, control, listener);
		}

		@Override
		public K getKey() {
			return ((ObservableMultiEntry<K, V>) getBacking()).getKey();
		}

		@Override
		public ElementId getKeyId() {
			return ((ObservableMultiEntry<K, V>) getBacking()).getKeyId();
		}

		@Override
		public ReversedSortedObservableMultiEntry<K, V> reverse() {
			return new ReversedSortedObservableMultiEntry<>(this);
		}
	}

	static class ReversedSortedObservableMultiEntry<K, V> extends ObservableSortedCollectionImpl.ReversedSortedCollection<V>
	implements ObservableMultiEntry<K, V> {
		ReversedSortedObservableMultiEntry(MCOSortedObservableEntry<K, V> source) {
			super(source);
		}

		@Override
		protected MCOSortedObservableEntry<K, V> getWrapped() {
			return (MCOSortedObservableEntry<K, V>) super.getWrapped();
		}

		@Override
		public ReversedSortedObservableMultiEntry<K, V> alias(String alias) {
			super.alias(alias);
			return this;
		}

		@Override
		public K getKey() {
			return getWrapped().getKey();
		}

		@Override
		public ElementId getKeyId() {
			return getWrapped().getKeyId();
		}

		@Override
		public MCOSortedObservableEntry<K, V> reverse() {
			return getWrapped();
		}
	}

	static class MCOSortedObservableSetEntry<K, V> extends ModControlledObservableCollection.MCOSortedSet<V, ObservableSortedSet<V>>
	implements ObservableMultiEntry<K, V> {
		public MCOSortedObservableSetEntry(ObservableMultiEntry<K, V> backing, CollectionModificationControl<V> control,
			CollectionModificationListener<V> listener) {
			super((ObservableSortedSet<V>) backing, control, listener);
		}

		@Override
		public K getKey() {
			return ((ObservableMultiEntry<K, V>) getBacking()).getKey();
		}

		@Override
		public ElementId getKeyId() {
			return ((ObservableMultiEntry<K, V>) getBacking()).getKeyId();
		}

		@Override
		public ReversedSortedObservableSetMultiEntry<K, V> reverse() {
			return new ReversedSortedObservableSetMultiEntry<>(this);
		}
	}

	static class ReversedSortedObservableSetMultiEntry<K, V> extends ObservableSortedSetImpl.ReversedSortedSet<V>
	implements ObservableMultiEntry<K, V> {
		ReversedSortedObservableSetMultiEntry(MCOSortedObservableSetEntry<K, V> source) {
			super(source);
		}

		@Override
		protected MCOSortedObservableSetEntry<K, V> getWrapped() {
			return (MCOSortedObservableSetEntry<K, V>) super.getWrapped();
		}

		@Override
		public ReversedSortedObservableSetMultiEntry<K, V> alias(String alias) {
			super.alias(alias);
			return this;
		}

		@Override
		public K getKey() {
			return getWrapped().getKey();
		}

		@Override
		public ElementId getKeyId() {
			return getWrapped().getKeyId();
		}

		@Override
		public MCOSortedObservableSetEntry<K, V> reverse() {
			return getWrapped();
		}
	}

	/**
	 * SortedObservableMultiMap implementation for {@link ModControlledObservableMultiMap}
	 *
	 * @param <K> The type of keys in the multi-map
	 * @param <V> The type of values in the multi-map
	 * @param <M> The sub type of this sorted multi-map
	 */
	public static class MCOSortedMultiMap<K, V, M extends ObservableSortedMultiMap<K, V>> extends ModControlledObservableMultiMap<K, V, M>
	implements ObservableSortedMultiMap<K, V> {
		/**
		 * @param backing The multi-map to control
		 * @param control The optional modification controller
		 * @param listener The optional modification listener
		 */
		public MCOSortedMultiMap(M backing, MultiMapModificationControl<K, V> control, MultiMapModificationListener<K, V> listener) {
			super(backing, control, listener);
		}

		@Override
		public ObservableSortedMultiMap<K, V> alias(String alias) {
			super.alias(alias);
			return this;
		}

		@Override
		public ObservableSortedSet<K> keySet() {
			return (ObservableSortedSet<K>) super.keySet();
		}

		@Override
		public SortedMultiMapFlow<K, V> flow() {
			return GeneralMultiMapFlow.init(this);
		}
	}
}
