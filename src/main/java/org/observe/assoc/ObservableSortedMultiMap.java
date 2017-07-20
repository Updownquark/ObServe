package org.observe.assoc;

import org.observe.collect.Equivalence;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableSortedSet;
import org.qommons.Transaction;

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
		return keySet().flow().mapEquivalent(getEntryType()).cache(false).map(this::entryFor, entry -> entry.getKey()).collectLW();
	}

	default ObservableMultiEntry<K, V> lowerEntry(K key) {
		return entrySet().lower(ObservableMultiEntry.empty(key, getKeyType(), getValueType()));
	}

	default ObservableMultiEntry<K, V> floorEntry(K key) {
		return entrySet().floor(ObservableMultiEntry.empty(key, getKeyType(), getValueType()));
	}

	default ObservableMultiEntry<K, V> ceilingEntry(K key) {
		return entrySet().ceiling(ObservableMultiEntry.empty(key, getKeyType(), getValueType()));
	}

	default ObservableMultiEntry<K, V> higherEntry(K key) {
		return entrySet().higher(ObservableMultiEntry.empty(key, getKeyType(), getValueType()));
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
			public Equivalence<? super V> valueEquivalence() {
				return outer.valueEquivalence();
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
		public Equivalence<? super V> valueEquivalence() {
			return theOuter.valueEquivalence();
		}

		@Override
		public ObservableSortedSet<K> keySet() {
			return theOuter.keySet().subSet(theLower, theUpper);
		}

		@Override
		public ObservableCollection<V> get(Object key) {
			if (!keySet().belongs(key))
				return ObservableCollection.constant(getValueType()).collect();
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
