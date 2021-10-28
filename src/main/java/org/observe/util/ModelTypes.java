package org.observe.util;

import org.observe.Observable;
import org.observe.ObservableAction;
import org.observe.SettableValue;
import org.observe.assoc.ObservableMap;
import org.observe.assoc.ObservableMultiMap;
import org.observe.assoc.ObservableSortedMap;
import org.observe.assoc.ObservableSortedMultiMap;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableSet;
import org.observe.collect.ObservableSortedCollection;
import org.observe.collect.ObservableSortedSet;

import com.google.common.reflect.TypeToken;

@SuppressWarnings("rawtypes")
public class ModelTypes {
	public static final ModelType.UnTyped<ObservableModelSet> Model = new ModelType.UnTyped<ObservableModelSet>("Model",
		ObservableModelSet.class) {
	};
	public static final ModelType.SingleTyped<Observable> Event = new ModelType.SingleTyped<Observable>("Event", Observable.class) {
		@Override
		public <V> ModelInstanceType.SingleTyped<Observable, V, Observable<V>> forType(TypeToken<V> type) {
			return (ModelInstanceType.SingleTyped<Observable, V, Observable<V>>) super.forType(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<Observable, V, Observable<V>> forType(Class<V> type) {
			return (ModelInstanceType.SingleTyped<Observable, V, Observable<V>>) super.forType(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<Observable, ? extends V, Observable<? extends V>> below(TypeToken<V> type) {
			return (ModelInstanceType.SingleTyped<Observable, ? extends V, Observable<? extends V>>) super.below(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<Observable, ? extends V, Observable<? extends V>> below(Class<V> type) {
			return (ModelInstanceType.SingleTyped<Observable, ? extends V, Observable<? extends V>>) super.below(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<Observable, ? super V, Observable<? super V>> above(TypeToken<V> type) {
			return (ModelInstanceType.SingleTyped<Observable, ? super V, Observable<? super V>>) super.above(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<Observable, ? super V, Observable<? super V>> above(Class<V> type) {
			return (ModelInstanceType.SingleTyped<Observable, ? super V, Observable<? super V>>) super.above(type);
		}
	};
	public static final ModelType.SingleTyped<ObservableAction> Action = new ModelType.SingleTyped<ObservableAction>("Action",
		ObservableAction.class) {
		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableAction, V, ObservableAction<V>> forType(TypeToken<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableAction, V, ObservableAction<V>>) super.forType(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableAction, V, ObservableAction<V>> forType(Class<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableAction, V, ObservableAction<V>>) super.forType(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableAction, ? extends V, ObservableAction<? extends V>> below(TypeToken<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableAction, ? extends V, ObservableAction<? extends V>>) super.below(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableAction, ? extends V, ObservableAction<? extends V>> below(Class<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableAction, ? extends V, ObservableAction<? extends V>>) super.below(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableAction, ? super V, ObservableAction<? super V>> above(TypeToken<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableAction, ? super V, ObservableAction<? super V>>) super.above(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableAction, ? super V, ObservableAction<? super V>> above(Class<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableAction, ? super V, ObservableAction<? super V>>) super.above(type);
		}
	};
	public static final ModelType.SingleTyped<SettableValue> Value = new ModelType.SingleTyped<SettableValue>("Value",
		SettableValue.class) {
		@Override
		public <V> ModelInstanceType.SingleTyped<SettableValue, V, SettableValue<V>> forType(TypeToken<V> type) {
			return (ModelInstanceType.SingleTyped<SettableValue, V, SettableValue<V>>) super.forType(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<SettableValue, V, SettableValue<V>> forType(Class<V> type) {
			return (ModelInstanceType.SingleTyped<SettableValue, V, SettableValue<V>>) super.forType(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<SettableValue, ? extends V, SettableValue<? extends V>> below(TypeToken<V> type) {
			return (ModelInstanceType.SingleTyped<SettableValue, ? extends V, SettableValue<? extends V>>) super.below(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<SettableValue, ? extends V, SettableValue<? extends V>> below(Class<V> type) {
			return (ModelInstanceType.SingleTyped<SettableValue, ? extends V, SettableValue<? extends V>>) super.below(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<SettableValue, ? super V, SettableValue<? super V>> above(TypeToken<V> type) {
			return (ModelInstanceType.SingleTyped<SettableValue, ? super V, SettableValue<? super V>>) super.above(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<SettableValue, ? super V, SettableValue<? super V>> above(Class<V> type) {
			return (ModelInstanceType.SingleTyped<SettableValue, ? super V, SettableValue<? super V>>) super.above(type);
		}

		@Override
		protected void setupConversions(ConversionBuilder<SettableValue> builder) {
			builder.convertibleTo(Event, (v, __) -> v.noInitChanges());
		}
	};
	public static final ModelType.SingleTyped<ObservableCollection> Collection = new ModelType.SingleTyped<ObservableCollection>(
		"Collection", ObservableCollection.class) {
		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableCollection, V, ObservableCollection<V>> forType(TypeToken<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableCollection, V, ObservableCollection<V>>) super.forType(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableCollection, V, ObservableCollection<V>> forType(Class<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableCollection, V, ObservableCollection<V>>) super.forType(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableCollection, ? extends V, ObservableCollection<? extends V>> below(
			TypeToken<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableCollection, ? extends V, ObservableCollection<? extends V>>) super.below(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableCollection, ? extends V, ObservableCollection<? extends V>> below(
			Class<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableCollection, ? extends V, ObservableCollection<? extends V>>) super.below(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableCollection, ? super V, ObservableCollection<? super V>> above(
			TypeToken<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableCollection, ? super V, ObservableCollection<? super V>>) super.above(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableCollection, ? super V, ObservableCollection<? super V>> above(Class<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableCollection, ? super V, ObservableCollection<? super V>>) super.above(type);
		}

		@Override
		protected void setupConversions(ConversionBuilder<ObservableCollection> builder) {
			builder.convertibleTo(Event, (c, __) -> c.changes());
		}
	};
	public static final ModelType.SingleTyped<ObservableSortedCollection> SortedCollection = new ModelType.SingleTyped<ObservableSortedCollection>(
		"SortedCollection", ObservableSortedCollection.class) {
		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableSortedCollection, V, ObservableSortedCollection<V>> forType(TypeToken<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableSortedCollection, V, ObservableSortedCollection<V>>) super.forType(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableSortedCollection, V, ObservableSortedCollection<V>> forType(Class<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableSortedCollection, V, ObservableSortedCollection<V>>) super.forType(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableSortedCollection, ? extends V, ObservableSortedCollection<? extends V>> below(
			TypeToken<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableSortedCollection, ? extends V, ObservableSortedCollection<? extends V>>) super.below(
				type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableSortedCollection, ? extends V, ObservableSortedCollection<? extends V>> below(
			Class<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableSortedCollection, ? extends V, ObservableSortedCollection<? extends V>>) super.below(
				type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableSortedCollection, ? super V, ObservableSortedCollection<? super V>> above(
			TypeToken<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableSortedCollection, ? super V, ObservableSortedCollection<? super V>>) super.above(
				type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableSortedCollection, ? super V, ObservableSortedCollection<? super V>> above(
			Class<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableSortedCollection, ? super V, ObservableSortedCollection<? super V>>) super.above(
				type);
		}

		@Override
		protected void setupConversions(ConversionBuilder<ObservableSortedCollection> builder) {
			builder.convertibleTo(Event, (c, __) -> c.changes())//
			.convertibleTo(Collection, (c, __) -> c);
		}
	};
	public static final ModelType.SingleTyped<ObservableSet> Set = new ModelType.SingleTyped<ObservableSet>("Set", ObservableSet.class) {
		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableSet, V, ObservableSet<V>> forType(TypeToken<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableSet, V, ObservableSet<V>>) super.forType(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableSet, V, ObservableSet<V>> forType(Class<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableSet, V, ObservableSet<V>>) super.forType(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableSet, ? extends V, ObservableSet<? extends V>> below(TypeToken<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableSet, ? extends V, ObservableSet<? extends V>>) super.below(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableSet, ? extends V, ObservableSet<? extends V>> below(Class<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableSet, ? extends V, ObservableSet<? extends V>>) super.below(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableSet, ? super V, ObservableSet<? super V>> above(TypeToken<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableSet, ? super V, ObservableSet<? super V>>) super.above(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableSet, ? super V, ObservableSet<? super V>> above(Class<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableSet, ? super V, ObservableSet<? super V>>) super.above(type);
		}

		@Override
		protected void setupConversions(ConversionBuilder<ObservableSet> builder) {
			builder.convertibleTo(Event, (c, __) -> c.changes())//
			.convertibleTo(Collection, (c, __) -> c);
		}
	};
	public static final ModelType.SingleTyped<ObservableSortedSet> SortedSet = new ModelType.SingleTyped<ObservableSortedSet>("SortedSet",
		ObservableSortedSet.class) {
		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableSortedSet, V, ObservableSortedSet<V>> forType(TypeToken<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableSortedSet, V, ObservableSortedSet<V>>) super.forType(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableSortedSet, V, ObservableSortedSet<V>> forType(Class<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableSortedSet, V, ObservableSortedSet<V>>) super.forType(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableSortedSet, ? extends V, ObservableSortedSet<? extends V>> below(
			TypeToken<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableSortedSet, ? extends V, ObservableSortedSet<? extends V>>) super.below(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableSortedSet, ? extends V, ObservableSortedSet<? extends V>> below(Class<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableSortedSet, ? extends V, ObservableSortedSet<? extends V>>) super.below(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableSortedSet, ? super V, ObservableSortedSet<? super V>> above(TypeToken<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableSortedSet, ? super V, ObservableSortedSet<? super V>>) super.above(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableSortedSet, ? super V, ObservableSortedSet<? super V>> above(Class<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableSortedSet, ? super V, ObservableSortedSet<? super V>>) super.above(type);
		}

		@Override
		protected void setupConversions(ConversionBuilder<ObservableSortedSet> builder) {
			builder.convertibleTo(Event, (c, __) -> c.changes())//
			.convertibleTo(Collection, (c, __) -> c)//
			.convertibleTo(SortedCollection, (c, __) -> c)//
			.convertibleTo(Set, (c, __) -> c);
		}
	};
	public static final ModelType.DoubleTyped<ObservableMap> Map = new ModelType.DoubleTyped<ObservableMap>("Map", ObservableMap.class) {
		@Override
		public <K, V> ModelInstanceType.DoubleTyped<ObservableMap, K, V, ObservableMap<K, V>> forType(TypeToken<K> keyType,
			TypeToken<V> valueType) {
			return (ModelInstanceType.DoubleTyped<ObservableMap, K, V, ObservableMap<K, V>>) super.forType(keyType, valueType);
		}

		@Override
		public <K, V> ModelInstanceType.DoubleTyped<ObservableMap, K, V, ObservableMap<K, V>> forType(Class<K> keyType,
			Class<V> valueType) {
			return (ModelInstanceType.DoubleTyped<ObservableMap, K, V, ObservableMap<K, V>>) super.forType(keyType, valueType);
		}

		@Override
		public <K, V> ModelInstanceType.DoubleTyped<ObservableMap, ? extends K, ? extends V, ObservableMap<? extends K, ? extends V>> below(
			TypeToken<K> keyType, TypeToken<V> valueType) {
			return (ModelInstanceType.DoubleTyped<ObservableMap, ? extends K, ? extends V, ObservableMap<? extends K, ? extends V>>) super.below(
				keyType, valueType);
		}

		@Override
		public <K, V> ModelInstanceType.DoubleTyped<ObservableMap, ? extends K, ? extends V, ObservableMap<? extends K, ? extends V>> below(
			Class<K> keyType, Class<V> valueType) {
			return (ModelInstanceType.DoubleTyped<ObservableMap, ? extends K, ? extends V, ObservableMap<? extends K, ? extends V>>) super.below(
				keyType, valueType);
		}

		@Override
		public <K, V> ModelInstanceType.DoubleTyped<ObservableMap, ? super K, ? super V, ObservableMap<? super K, ? super V>> above(
			TypeToken<K> keyType, TypeToken<V> valueType) {
			return (ModelInstanceType.DoubleTyped<ObservableMap, ? super K, ? super V, ObservableMap<? super K, ? super V>>) super.above(
				keyType, valueType);
		}

		@Override
		public <K, V> ModelInstanceType.DoubleTyped<ObservableMap, ? super K, ? super V, ObservableMap<? super K, ? super V>> above(
			Class<K> keyType, Class<V> valueType) {
			return (ModelInstanceType.DoubleTyped<ObservableMap, ? super K, ? super V, ObservableMap<? super K, ? super V>>) super.above(
				keyType, valueType);
		}

		@Override
		protected void setupConversions(ConversionBuilder<ObservableMap> builder) {
			builder.convertibleTo(Event, (m, __) -> m.changes());
		}
	};
	public static final ModelType.DoubleTyped<ObservableSortedMap> SortedMap = new ModelType.DoubleTyped<ObservableSortedMap>("SortedMap",
		ObservableSortedMap.class) {
		@Override
		public <K, V> ModelInstanceType.DoubleTyped<ObservableSortedMap, K, V, ObservableSortedMap<K, V>> forType(TypeToken<K> keyType,
			TypeToken<V> valueType) {
			return (ModelInstanceType.DoubleTyped<ObservableSortedMap, K, V, ObservableSortedMap<K, V>>) super.forType(keyType, valueType);
		}

		@Override
		public <K, V> ModelInstanceType.DoubleTyped<ObservableSortedMap, K, V, ObservableSortedMap<K, V>> forType(Class<K> keyType,
			Class<V> valueType) {
			return (ModelInstanceType.DoubleTyped<ObservableSortedMap, K, V, ObservableSortedMap<K, V>>) super.forType(keyType, valueType);
		}

		@Override
		public <K, V> ModelInstanceType.DoubleTyped<ObservableSortedMap, ? extends K, ? extends V, ObservableSortedMap<? extends K, ? extends V>> below(
			TypeToken<K> keyType, TypeToken<V> valueType) {
			return (ModelInstanceType.DoubleTyped<ObservableSortedMap, ? extends K, ? extends V, ObservableSortedMap<? extends K, ? extends V>>) super.below(
				keyType, valueType);
		}

		@Override
		public <K, V> ModelInstanceType.DoubleTyped<ObservableSortedMap, ? extends K, ? extends V, ObservableSortedMap<? extends K, ? extends V>> below(
			Class<K> keyType, Class<V> valueType) {
			return (ModelInstanceType.DoubleTyped<ObservableSortedMap, ? extends K, ? extends V, ObservableSortedMap<? extends K, ? extends V>>) super.below(
				keyType, valueType);
		}

		@Override
		public <K, V> ModelInstanceType.DoubleTyped<ObservableSortedMap, ? super K, ? super V, ObservableSortedMap<? super K, ? super V>> above(
			TypeToken<K> keyType, TypeToken<V> valueType) {
			return (ModelInstanceType.DoubleTyped<ObservableSortedMap, ? super K, ? super V, ObservableSortedMap<? super K, ? super V>>) super.above(
				keyType, valueType);
		}

		@Override
		public <K, V> ModelInstanceType.DoubleTyped<ObservableSortedMap, ? super K, ? super V, ObservableSortedMap<? super K, ? super V>> above(
			Class<K> keyType, Class<V> valueType) {
			return (ModelInstanceType.DoubleTyped<ObservableSortedMap, ? super K, ? super V, ObservableSortedMap<? super K, ? super V>>) super.above(
				keyType, valueType);
		}

		@Override
		protected void setupConversions(ConversionBuilder<ObservableSortedMap> builder) {
			builder.convertibleTo(Event, (m, __) -> m.changes())//
			.convertibleTo(Map, (m, __) -> m);
		}
	};
	public static final ModelType.DoubleTyped<ObservableMultiMap> MultiMap = new ModelType.DoubleTyped<ObservableMultiMap>("MultiMap",
		ObservableMultiMap.class) {
		@Override
		public <K, V> ModelInstanceType.DoubleTyped<ObservableMultiMap, K, V, ObservableMultiMap<K, V>> forType(TypeToken<K> keyType,
			TypeToken<V> valueType) {
			return (ModelInstanceType.DoubleTyped<ObservableMultiMap, K, V, ObservableMultiMap<K, V>>) super.forType(keyType, valueType);
		}

		@Override
		public <K, V> ModelInstanceType.DoubleTyped<ObservableMultiMap, K, V, ObservableMultiMap<K, V>> forType(Class<K> keyType,
			Class<V> valueType) {
			return (ModelInstanceType.DoubleTyped<ObservableMultiMap, K, V, ObservableMultiMap<K, V>>) super.forType(keyType, valueType);
		}

		@Override
		public <K, V> ModelInstanceType.DoubleTyped<ObservableMultiMap, ? extends K, ? extends V, ObservableMultiMap<? extends K, ? extends V>> below(
			TypeToken<K> keyType, TypeToken<V> valueType) {
			return (ModelInstanceType.DoubleTyped<ObservableMultiMap, ? extends K, ? extends V, ObservableMultiMap<? extends K, ? extends V>>) super.below(
				keyType, valueType);
		}

		@Override
		public <K, V> ModelInstanceType.DoubleTyped<ObservableMultiMap, ? extends K, ? extends V, ObservableMultiMap<? extends K, ? extends V>> below(
			Class<K> keyType, Class<V> valueType) {
			return (ModelInstanceType.DoubleTyped<ObservableMultiMap, ? extends K, ? extends V, ObservableMultiMap<? extends K, ? extends V>>) super.below(
				keyType, valueType);
		}

		@Override
		public <K, V> ModelInstanceType.DoubleTyped<ObservableMultiMap, ? super K, ? super V, ObservableMultiMap<? super K, ? super V>> above(
			TypeToken<K> keyType, TypeToken<V> valueType) {
			return (ModelInstanceType.DoubleTyped<ObservableMultiMap, ? super K, ? super V, ObservableMultiMap<? super K, ? super V>>) super.above(
				keyType, valueType);
		}

		@Override
		public <K, V> ModelInstanceType.DoubleTyped<ObservableMultiMap, ? super K, ? super V, ObservableMultiMap<? super K, ? super V>> above(
			Class<K> keyType, Class<V> valueType) {
			return (ModelInstanceType.DoubleTyped<ObservableMultiMap, ? super K, ? super V, ObservableMultiMap<? super K, ? super V>>) super.above(
				keyType, valueType);
		}

		@Override
		protected void setupConversions(ConversionBuilder<ObservableMultiMap> builder) {
			builder.convertibleTo(Event, (m, __) -> m.changes());
		}
	};
	public static final ModelType.DoubleTyped<ObservableSortedMultiMap> SortedMultiMap = new ModelType.DoubleTyped<ObservableSortedMultiMap>(
		"SortedMultiMap", ObservableSortedMultiMap.class) {
		@Override
		public <K, V> ModelInstanceType.DoubleTyped<ObservableSortedMultiMap, K, V, ObservableSortedMultiMap<K, V>> forType(
			TypeToken<K> keyType, TypeToken<V> valueType) {
			return (ModelInstanceType.DoubleTyped<ObservableSortedMultiMap, K, V, ObservableSortedMultiMap<K, V>>) super.forType(keyType,
				valueType);
		}

		@Override
		public <K, V> ModelInstanceType.DoubleTyped<ObservableSortedMultiMap, K, V, ObservableSortedMultiMap<K, V>> forType(
			Class<K> keyType, Class<V> valueType) {
			return (ModelInstanceType.DoubleTyped<ObservableSortedMultiMap, K, V, ObservableSortedMultiMap<K, V>>) super.forType(keyType,
				valueType);
		}

		@Override
		public <K, V> ModelInstanceType.DoubleTyped<ObservableSortedMultiMap, ? extends K, ? extends V, ObservableSortedMultiMap<? extends K, ? extends V>> below(
			TypeToken<K> keyType, TypeToken<V> valueType) {
			return (ModelInstanceType.DoubleTyped<ObservableSortedMultiMap, ? extends K, ? extends V, ObservableSortedMultiMap<? extends K, ? extends V>>) super.below(
				keyType, valueType);
		}

		@Override
		public <K, V> ModelInstanceType.DoubleTyped<ObservableSortedMultiMap, ? extends K, ? extends V, ObservableSortedMultiMap<? extends K, ? extends V>> below(
			Class<K> keyType, Class<V> valueType) {
			return (ModelInstanceType.DoubleTyped<ObservableSortedMultiMap, ? extends K, ? extends V, ObservableSortedMultiMap<? extends K, ? extends V>>) super.below(
				keyType, valueType);
		}

		@Override
		public <K, V> ModelInstanceType.DoubleTyped<ObservableSortedMultiMap, ? super K, ? super V, ObservableSortedMultiMap<? super K, ? super V>> above(
			TypeToken<K> keyType, TypeToken<V> valueType) {
			return (ModelInstanceType.DoubleTyped<ObservableSortedMultiMap, ? super K, ? super V, ObservableSortedMultiMap<? super K, ? super V>>) super.above(
				keyType, valueType);
		}

		@Override
		public <K, V> ModelInstanceType.DoubleTyped<ObservableSortedMultiMap, ? super K, ? super V, ObservableSortedMultiMap<? super K, ? super V>> above(
			Class<K> keyType, Class<V> valueType) {
			return (ModelInstanceType.DoubleTyped<ObservableSortedMultiMap, ? super K, ? super V, ObservableSortedMultiMap<? super K, ? super V>>) super.above(
				keyType, valueType);
		}

		@Override
		protected void setupConversions(ConversionBuilder<ObservableSortedMultiMap> builder) {
			builder.convertibleTo(Event, (m, __) -> m.changes())//
			.convertibleTo(MultiMap, (m, __) -> m);
		}
	};
}
