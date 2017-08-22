package org.observe.assoc;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.observe.Observable;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollection.UniqueSortedDataFlow;
import org.observe.collect.ObservableCollectionDataFlowImpl.CollectionManager;
import org.observe.collect.ObservableSortedSet;
import org.qommons.Transaction;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.MutableCollectionElement.StdMsg;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

/**
 * A sorted {@link ObservableMultiMap}
 *
 * @param <K> The key type of the map
 * @param <V> The value type of the map
 */
public interface ObservableSortedMultiMap<K, V> extends ObservableMultiMap<K, V> {
	@Override
	ObservableSortedSet<K> keySet();

	@Override
	default ObservableSortedSet<ObservableMultiEntry<K, V>> entrySet() {
		return keySet().flow().mapEquivalent(getEntryType()).cache(false).map(this::entryFor, entry -> entry.getKey()).collect();
	}

	default ObservableMultiEntry<K, V> lowerEntry(K key) {
		return entrySet().lower(ObservableMultiEntry.empty(getKeyType(), getValueType(), key, keySet().equivalence()));
	}

	default ObservableMultiEntry<K, V> floorEntry(K key) {
		return entrySet().floor(ObservableMultiEntry.empty(getKeyType(), getValueType(), key, keySet().equivalence()));
	}

	default ObservableMultiEntry<K, V> ceilingEntry(K key) {
		return entrySet().ceiling(ObservableMultiEntry.empty(getKeyType(), getValueType(), key, keySet().equivalence()));
	}

	default ObservableMultiEntry<K, V> higherEntry(K key) {
		return entrySet().higher(ObservableMultiEntry.empty(getKeyType(), getValueType(), key, keySet().equivalence()));
	}

	default ObservableSortedMultiMap<K, V> reverse() {
		ObservableSortedMultiMap<K, V> outer = this;
		return new ObservableSortedMultiMap<K, V>() {
			@Override
			public TypeToken<K> getKeyType() {
				return outer.getKeyType();
			}

			@Override
			public TypeToken<V> getValueType() {
				return outer.getValueType();
			}

			@Override
			public TypeToken<ObservableMultiEntry<K, V>> getEntryType() {
				return outer.getEntryType();
			}

			@Override
			public boolean isLockSupported() {
				return outer.isLockSupported();
			}

			@Override
			public Transaction lock(boolean write, Object cause) {
				return outer.lock(write, cause);
			}

			@Override
			public ObservableSortedSet<K> keySet() {
				return outer.keySet().reverse();
			}

			@Override
			public ObservableCollection<V> get(Object key) {
				return outer.get(key);
			}

			@Override
			public ObservableCollection<V> values() {
				return outer.values();
			}

			@Override
			public ObservableSortedSet<ObservableMultiEntry<K, V>> entrySet() {
				return outer.entrySet().reverse();
			}

			@Override
			public ObservableSortedMultiMap<K, V> reverse() {
				return outer;
			}
		};
	}

	default ObservableSortedMultiMap<K, V> subMap(Comparable<? super K> from, Comparable<? super K> to) {
		return new ObservableSubMultiMap<>(this, from, to);
	}

	default ObservableSortedMultiMap<K, V> subMap(K fromKey, boolean fromInclusive, K toKey, boolean toInclusive) {
		return subMap(v -> {
			int comp = keySet().comparator().compare(toKey, v);
			if (!fromInclusive && comp == 0)
				comp = 1;
			return comp;
		}, v -> {
			int comp = keySet().comparator().compare(toKey, v);
			if (!fromInclusive && comp == 0)
				comp = -1;
			return comp;
		});
	}

	default ObservableSortedMultiMap<K, V> headMap(K toKey, boolean inclusive) {
		return subMap(null, v -> {
			int comp = keySet().comparator().compare(toKey, v);
			if (!inclusive && comp == 0)
				comp = -1;
			return comp;
		});
	}

	default ObservableSortedMultiMap<K, V> tailMap(K fromKey, boolean inclusive) {
		return subMap(v -> {
			int comp = keySet().comparator().compare(fromKey, v);
			if (!inclusive && comp == 0)
				comp = 1;
			return comp;
		}, null);
	}

	default ObservableSortedMultiMap<K, V> subMap(K fromKey, K toKey) {
		return subMap(fromKey, true, toKey, true);
	}

	default ObservableSortedMultiMap<K, V> headMap(K toKey) {
		return headMap(toKey, true);
	}

	default ObservableSortedMultiMap<K, V> tailMap(K fromKey) {
		return tailMap(fromKey, true);
	}

	@Override
	default ObservableSortedMap<K, V> unique(){
		return new UniqueSortedMap<>(this);
	}

	static <K, V> SortedMultiMapFlow<?, K, V> create(TypeToken<K> keyType, TypeToken<V> valueType, Comparator<? super K> keyCompare) {
		TypeToken<Map.Entry<K, V>> entryType = new TypeToken<Map.Entry<K, V>>() {}.where(new TypeParameter<K>() {}, keyType)
			.where(new TypeParameter<V>() {}, valueType);
		class MapEntry implements Map.Entry<K, V> {
			private final K theKey;
			private V theValue;

			public MapEntry(K key, V value) {
				theKey = key;
				theValue = value;
			}

			@Override
			public K getKey() {
				return theKey;
			}

			@Override
			public V getValue() {
				return theValue;
			}

			@Override
			public V setValue(V value) {
				V old = theValue;
				theValue = value;
				return old;
			}

			@Override
			public int hashCode() {
				return Objects.hashCode(theKey);
			}

			@Override
			public boolean equals(Object obj) {
				return (obj == null || keyType.getRawType().isInstance(obj)) && keyCompare.compare(theKey, (K) obj) == 0;
			}

			@Override
			public String toString() {
				return theKey + "=" + theValue;
			}
		}
		ObservableCollection<Map.Entry<K, V>> simpleEntryCollection = ObservableCollection.create(entryType);
		ObservableSortedSet<K> keySet = simpleEntryCollection.flow().map(keyType).map(Map.Entry::getKey).uniqueSorted(keyCompare, true)
			.collect();
		return new DefaultSortedMultiMapFlow<>(keySet, keySet.flow(), valueType,
			key -> simpleEntryCollection.flow()//
			.filterStatic(entry -> keyCompare.compare(entry.getKey(), key) == 0 ? null : StdMsg.WRONG_GROUP)//
			.map(valueType).cache(false).withReverse(value -> new MapEntry(key, value)).map(Map.Entry::getValue));
	}

	interface SortedMultiMapFlow<OK, K, V> extends MultiMapFlow<OK, K, V> {
		@Override
		UniqueSortedDataFlow<OK, ?, K> keys();

		<K2> SortedMultiMapFlow<OK, K2, V> onEquivalentSortedKeys(
			Function<? super UniqueSortedDataFlow<OK, ?, K>, ? extends UniqueSortedDataFlow<OK, ?, K2>> keyFlow);

		@Override
		<V2> SortedMultiMapFlow<OK, K, V2> onValues(TypeToken<V2> targetType,
			Function<? super CollectionDataFlow<?, ?, V>, ? extends CollectionDataFlow<?, ?, V2>> valueFlow);

		@Override
		ObservableSortedMultiMap<K, V> collectLW();

		@Override
		default ObservableSortedMultiMap<K, V> collect() {
			return (ObservableSortedMultiMap<K, V>) MultiMapFlow.super.collect();
		}

		@Override
		ObservableSortedMultiMap<K, V> collect(Observable<?> until);
	}

	class DefaultSortedMultiMapFlow<OK, K, V> extends DefaultMultiMapFlow<OK, K, V> implements SortedMultiMapFlow<OK, K, V> {
		public DefaultSortedMultiMapFlow(ObservableCollection<OK> keyCollection, UniqueSortedDataFlow<OK, ?, K> keyFlow,
			TypeToken<V> valueType, Function<? super OK, CollectionDataFlow<?, ?, V>> valueMaker) {
			super(keyCollection, keyFlow, valueType, valueMaker);
		}

		@Override
		public UniqueSortedDataFlow<OK, ?, K> keys() {
			return (UniqueSortedDataFlow<OK, ?, K>) super.keys();
		}

		@Override
		public <K2> SortedMultiMapFlow<OK, K2, V> onEquivalentSortedKeys(
			Function<? super UniqueSortedDataFlow<OK, ?, K>, ? extends UniqueSortedDataFlow<OK, ?, K2>> keyFlow) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public <V2> SortedMultiMapFlow<OK, K, V2> onValues(TypeToken<V2> targetType,
			Function<? super CollectionDataFlow<?, ?, V>, ? extends CollectionDataFlow<?, ?, V2>> valueFlow) {
			Function<? super OK, CollectionDataFlow<?, ?, V2>> newValues = getValueMaker().andThen(valueFlow);
			return new DefaultSortedMultiMapFlow<>(getKeyCollection(), keys(), targetType, newValues);
		}

		@Override
		public ObservableSortedMultiMap<K, V> collectLW() {
			return (ObservableSortedMultiMap<K, V>) super.collectLW();
		}

		@Override
		public ObservableSortedMultiMap<K, V> collect(Observable<?> until) {
			return (ObservableSortedMultiMap<K, V>) super.collect(until);
		}

		@Override
		protected ObservableSortedMultiMap<K, V> collect(boolean lightWeight, Observable<?> until) {
			return new DerivedSortedMultiMap<>(getKeyCollection(), keys(), until, getTargetValueType(), getValueMaker(), lightWeight);
		}
	}

	class DerivedSortedMultiMap<OK, K, V> extends DerivedMultiMap<OK, K, V> implements ObservableSortedMultiMap<K, V> {
		public DerivedSortedMultiMap(ObservableCollection<OK> keySource, UniqueSortedDataFlow<OK, ?, K> keyFlow, Observable<?> until,
			TypeToken<V> valueType, Function<? super OK, CollectionDataFlow<?, ?, V>> valueMaker, boolean lightWeight) {
			super(keySource, keyFlow, until, valueType, valueMaker, lightWeight);
		}

		@Override
		public ObservableSortedSet<K> keySet() {
			return (ObservableSortedSet<K>) super.keySet();
		}

		@Override
		protected DerivedEntrySet createEntrySet(ObservableCollection<OK> keySource) {
			return new DerivedSortedEntrySet(keySource, getKeyManager(), getUntil());
		}

		protected class DerivedSortedEntrySet extends DerivedEntrySet implements ObservableSortedSet<K> {
			public DerivedSortedEntrySet(ObservableCollection<OK> keySource, CollectionManager<OK, ?, K> flow, Observable<?> until) {
				super(keySource, flow, until);
			}

			@Override
			public Comparator<? super K> comparator() {
				return getKeyManager().comparator();
			}

			@Override
			public CollectionElement<K> addIfEmpty(K value) throws IllegalStateException {
				try (Transaction t = lock(true, null)) {
					if (!isEmpty())
						throw new IllegalStateException("Set is not empty");
					return super.addElement(value, true);
				}
			}

			@Override
			public int indexFor(Comparable<? super K> search) {
				return getPresentElements().indexFor(el -> search.compareTo(el.get()));
			}

			@Override
			public CollectionElement<K> search(Comparable<? super K> search, SortedSearchFilter filter) {
				CollectionElement<DerivedCollectionElement<OK, K>> element = getPresentElements().search(el -> search.compareTo(el.get()),
					filter);
				if (element == null)
					return null;
				return observableElementFor(element.get());
			}
		}
	}

	/**
	 * Implements {@link ObservableSortedMap#subMap(Object, boolean, Object, boolean)}
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	class ObservableSubMultiMap<K, V> implements ObservableSortedMultiMap<K, V> {
		private final ObservableSortedMultiMap<K, V> theOuter;
		private final Comparable<? super K> theLower;
		private final Comparable<? super K> theUpper;

		public ObservableSubMultiMap(ObservableSortedMultiMap<K, V> outer, Comparable<? super K> lower, Comparable<? super K> upper) {
			theOuter = outer;
			theLower = lower;
			theUpper = upper;
		}

		@Override
		public TypeToken<K> getKeyType() {
			return theOuter.getKeyType();
		}

		@Override
		public TypeToken<V> getValueType() {
			return theOuter.getValueType();
		}

		@Override
		public TypeToken<ObservableMultiEntry<K, V>> getEntryType() {
			return theOuter.getEntryType();
		}

		@Override
		public boolean isLockSupported() {
			return theOuter.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theOuter.lock(write, cause);
		}

		@Override
		public ObservableSortedSet<K> keySet() {
			return theOuter.keySet().subSet(theLower, theUpper);
		}

		@Override
		public ObservableCollection<V> get(Object key) {
			if (!keySet().belongs(key))
				return ObservableCollection.constant(getValueType());
			return theOuter.get(key);
		}
	};

	class UniqueSortedMap<K, V> extends UniqueMap<K, V> implements ObservableSortedMap<K, V> {
		public UniqueSortedMap(ObservableSortedMultiMap<K, V> outer) {
			super(outer);
		}

		@Override
		protected ObservableSortedMultiMap<K, V> getOuter() {
			return (ObservableSortedMultiMap<K, V>) super.getOuter();
		}

		@Override
		public ObservableSortedSet<K> keySet() {
			return (ObservableSortedSet<K>) super.keySet();
		}
	}
}
