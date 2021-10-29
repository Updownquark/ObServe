package org.observe.util;

import java.util.function.Function;

import org.observe.Observable;
import org.observe.ObservableAction;
import org.observe.ObservableValueEvent;
import org.observe.SettableValue;
import org.observe.Transformation;
import org.observe.assoc.ObservableMap;
import org.observe.assoc.ObservableMapEvent;
import org.observe.assoc.ObservableMultiMap;
import org.observe.assoc.ObservableMultiMapEvent;
import org.observe.assoc.ObservableSortedMap;
import org.observe.assoc.ObservableSortedMultiMap;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollectionEvent;
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

		@Override
		protected ModelInstanceConverter<Observable, Observable> convertType(ModelInstanceType<Observable, ?> target,
			Function<Object, Object>[] casts, Function<Object, Object>[] reverses) {
			return src -> src.map(casts[0]);
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

		@Override
		protected ModelInstanceConverter<ObservableAction, ObservableAction> convertType(ModelInstanceType<ObservableAction, ?> target,
			Function<Object, Object>[] casts, Function<Object, Object>[] reverses) {
			return src -> src.map(casts[0]);
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
		protected ModelInstanceConverter<SettableValue, SettableValue> convertType(ModelInstanceType<SettableValue, ?> target,
			Function<Object, Object>[] casts, Function<Object, Object>[] reverses) {
			if (reverses != null)
				return src -> src.transformReversible(target.getType(0), transformReversible(casts[0], reverses[0]));
				else
					return src -> SettableValue.asSettable(src.transform(target.getType(0), transform(casts[0])), //
						__ -> "Not reversble");
		}

		@Override
		protected void setupConversions(ConversionBuilder<SettableValue> builder) {
			builder.convertibleTo(Event, new ModelConverter.SimpleSingleTyped<SettableValue, Observable>() {
				@Override
				public ModelInstanceConverter<SettableValue, Observable> convert(ModelInstanceType<SettableValue, ?> source,
					ModelInstanceType<Observable, ?> dest) throws IllegalArgumentException {
					if (dest.getType(0) == TypeTokens.get().VOID || TypeTokens.getRawType(dest.getType(0)) == void.class) {
						return src -> src.noInitChanges().map(__ -> null);
					} else if (dest.getType(0)
						.isAssignableFrom(TypeTokens.get().keyFor(ObservableValueEvent.class).parameterized(source.getType(0)))) {
						return src -> src.noInitChanges();
					} else
						return SimpleSingleTyped.super.convert(source, dest);
				}

				@Override
				public <S, T> Observable convert(SettableValue source, TypeToken<T> targetType, Function<S, T> cast,
					Function<T, S> reverse) {
					if (cast != null)
						return source.value().noInit().map(cast);
					else
						return source.value().noInit();
				}
			});
		}
	};

	static Function<Transformation.TransformationPrecursor<Object, Object, ?>, Transformation<Object, Object>> transform(
		Function<Object, Object> cast) {
		return tx -> tx.map(cast);
	}

	static Function<Transformation.ReversibleTransformationPrecursor<Object, Object, ?>, Transformation.ReversibleTransformation<Object, Object>> transformReversible(
		Function<Object, Object> cast, Function<Object, Object> reverse) {
		return tx -> tx.map(cast).withReverse(reverse);
	}

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
		protected ModelInstanceConverter<ObservableCollection, ObservableCollection> convertType(
			ModelInstanceType<ObservableCollection, ?> target, Function<Object, Object>[] casts, Function<Object, Object>[] reverses) {
			if (reverses != null)
				return src -> src.flow().transform(target.getType(0), transformReversible(casts[0], reverses[0])).collectPassive();
				else
					return src -> src.flow().transform(target.getType(0), transform(casts[0]))
						.filterMod(opts -> ((ObservableCollection.ModFilterBuilder<Object>) opts).noAdd("Not reversible")).collectPassive();
		}

		@Override
		protected void setupConversions(ConversionBuilder<ObservableCollection> builder) {
			builder.convertibleTo(Event, new ModelConverter<ObservableCollection, Observable>() {
				@Override
				public ModelInstanceConverter<ObservableCollection, Observable> convert(ModelInstanceType<ObservableCollection, ?> source,
					ModelInstanceType<Observable, ?> dest) throws IllegalArgumentException {
					if (dest.getType(0) == TypeTokens.get().VOID || TypeTokens.getRawType(dest.getType(0)) == void.class) {
						return src -> src.changes().map(__ -> null);
					} else if (dest.getType(0)
						.isAssignableFrom(TypeTokens.get().keyFor(ObservableCollectionEvent.class).parameterized(source.getType(0)))) {
						return src -> src.changes();
					} else
						throw new IllegalArgumentException("Cannot convert from " + source + " to " + dest);
				}
			});
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
		protected ModelInstanceConverter<ObservableSortedCollection, ObservableSortedCollection> convertType(
			ModelInstanceType<ObservableSortedCollection, ?> target, Function<Object, Object>[] casts,
			Function<Object, Object>[] reverses) {
			if (reverses != null)
				return src -> src.flow().transformEquivalent(target.getType(0), transformReversible(casts[0], reverses[0]))
					.collectPassive();
				else
					return src -> src.flow().transformEquivalent(target.getType(0), transform(casts[0]))
						.filterMod(opts -> ((ObservableCollection.ModFilterBuilder<Object>) opts).noAdd("Not reversible")).collectPassive();
		}

		@Override
		protected void setupConversions(ConversionBuilder<ObservableSortedCollection> builder) {
			builder.convertibleTo(Collection, (source, dest) -> src -> src)//
			.convertibleTo(Event, new ModelConverter<ObservableSortedCollection, Observable>() {
				@Override
				public ModelInstanceConverter<ObservableSortedCollection, Observable> convert(
					ModelInstanceType<ObservableSortedCollection, ?> source, ModelInstanceType<Observable, ?> dest)
						throws IllegalArgumentException {
					if (dest.getType(0) == TypeTokens.get().VOID || TypeTokens.getRawType(dest.getType(0)) == void.class) {
						return src -> src.changes().map(__ -> null);
					} else if (dest.getType(0)
						.isAssignableFrom(TypeTokens.get().keyFor(ObservableCollectionEvent.class).parameterized(source.getType(0)))) {
						return src -> src.changes();
					} else
						throw new IllegalArgumentException("Cannot convert from " + source + " to " + dest);
				}
			});
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
		protected ModelInstanceConverter<ObservableSet, ObservableSet> convertType(ModelInstanceType<ObservableSet, ?> target,
			Function<Object, Object>[] casts, Function<Object, Object>[] reverses) {
			if (reverses != null)
				return src -> src.flow().transformEquivalent(target.getType(0), transformReversible(casts[0], reverses[0]))
					.collectPassive();
				else
					return src -> src.flow().transformEquivalent(target.getType(0), transform(casts[0]))
						.filterMod(opts -> ((ObservableCollection.ModFilterBuilder<Object>) opts).noAdd("Not reversible")).collectPassive();
		}

		@Override
		protected void setupConversions(ConversionBuilder<ObservableSet> builder) {
			builder.convertibleTo(Collection, (source, dest) -> src -> src)//
			.convertibleTo(Event, new ModelConverter<ObservableSet, Observable>() {
				@Override
				public ModelInstanceConverter<ObservableSet, Observable> convert(ModelInstanceType<ObservableSet, ?> source,
					ModelInstanceType<Observable, ?> dest) throws IllegalArgumentException {
					if (dest.getType(0) == TypeTokens.get().VOID || TypeTokens.getRawType(dest.getType(0)) == void.class) {
						return src -> src.changes().map(__ -> null);
					} else if (dest.getType(0)
						.isAssignableFrom(TypeTokens.get().keyFor(ObservableCollectionEvent.class).parameterized(source.getType(0)))) {
						return src -> src.changes();
					} else
						throw new IllegalArgumentException("Cannot convert from " + source + " to " + dest);
				}
			});
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
		protected ModelInstanceConverter<ObservableSortedSet, ObservableSortedSet> convertType(
			ModelInstanceType<ObservableSortedSet, ?> target, Function<Object, Object>[] casts, Function<Object, Object>[] reverses) {
			if (reverses != null)
				return src -> src.flow().transformEquivalent(target.getType(0), transformReversible(casts[0], reverses[0]))
					.collectPassive();
				else
					return src -> src.flow().transformEquivalent(target.getType(0), transform(casts[0]))
						.filterMod(opts -> ((ObservableCollection.ModFilterBuilder<Object>) opts).noAdd("Not reversible")).collectPassive();
		}

		@Override
		protected void setupConversions(ConversionBuilder<ObservableSortedSet> builder) {
			builder.convertibleTo(Collection, (source, dest) -> src -> src)//
			.convertibleTo(SortedCollection, (source, dest) -> src -> src)//
			.convertibleTo(Set, (source, dest) -> src -> src)//
			.convertibleTo(Event, new ModelConverter<ObservableSortedSet, Observable>() {
				@Override
				public ModelInstanceConverter<ObservableSortedSet, Observable> convert(ModelInstanceType<ObservableSortedSet, ?> source,
					ModelInstanceType<Observable, ?> dest) throws IllegalArgumentException {
					if (dest.getType(0) == TypeTokens.get().VOID || TypeTokens.getRawType(dest.getType(0)) == void.class) {
						return src -> src.changes().map(__ -> null);
					} else if (dest.getType(0)
						.isAssignableFrom(TypeTokens.get().keyFor(ObservableCollectionEvent.class).parameterized(source.getType(0)))) {
						return src -> src.changes();
					} else
						throw new IllegalArgumentException("Cannot convert from " + source + " to " + dest);
				}
			});
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
		protected ModelInstanceConverter<ObservableMap, ObservableMap> convertType(ModelInstanceType<ObservableMap, ?> target,
			Function<Object, Object>[] casts, Function<Object, Object>[] reverses) {
			return null; // ObservableMap doesn't have flow at the moment at least
		}

		@Override
		protected void setupConversions(ConversionBuilder<ObservableMap> builder) {
			builder.convertibleTo(Event, new ModelConverter<ObservableMap, Observable>() {
				@Override
				public ModelInstanceConverter<ObservableMap, Observable> convert(ModelInstanceType<ObservableMap, ?> source,
					ModelInstanceType<Observable, ?> dest) throws IllegalArgumentException {
					if (dest.getType(0) == TypeTokens.get().VOID || TypeTokens.getRawType(dest.getType(0)) == void.class) {
						return src -> src.changes().map(__ -> null);
					} else if (dest.getType(0).isAssignableFrom(
						TypeTokens.get().keyFor(ObservableMapEvent.class).parameterized(source.getType(0), source.getType(1)))) {
						return src -> src.changes();
					} else
						throw new IllegalArgumentException("Cannot convert from " + source + " to " + dest);
				}
			});
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
		protected ModelInstanceConverter<ObservableSortedMap, ObservableSortedMap> convertType(
			ModelInstanceType<ObservableSortedMap, ?> target, Function<Object, Object>[] casts, Function<Object, Object>[] reverses) {
			return null; // ObservableMap doesn't have flow at the moment at least
		}

		@Override
		protected void setupConversions(ConversionBuilder<ObservableSortedMap> builder) {
			builder.convertibleTo(Map, (source, dest) -> src -> src)//
			.convertibleTo(Event, new ModelConverter<ObservableSortedMap, Observable>() {
				@Override
				public ModelInstanceConverter<ObservableSortedMap, Observable> convert(ModelInstanceType<ObservableSortedMap, ?> source,
					ModelInstanceType<Observable, ?> dest) throws IllegalArgumentException {
					if (dest.getType(0) == TypeTokens.get().VOID || TypeTokens.getRawType(dest.getType(0)) == void.class) {
						return src -> src.changes().map(__ -> null);
					} else if (dest.getType(0).isAssignableFrom(
						TypeTokens.get().keyFor(ObservableMapEvent.class).parameterized(source.getType(0), source.getType(1)))) {
						return src -> src.changes();
					} else
						throw new IllegalArgumentException("Cannot convert from " + source + " to " + dest);
				}
			});
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
		protected ModelInstanceConverter<ObservableMultiMap, ObservableMultiMap> convertType(
			ModelInstanceType<ObservableMultiMap, ?> target, Function<Object, Object>[] casts, Function<Object, Object>[] reverses) {
			if (casts[0] != null && reverses[0] == null)
				return null; // Need reverse for key mapping
			return src -> {
				ObservableMultiMap.MultiMapFlow<Object, Object> flow = src.flow();
				if (casts[0] != null) {
					flow = flow.withKeys(keyFlow -> keyFlow.transformEquivalent((TypeToken<Object>) target.getType(0),
						transformReversible(casts[0], reverses[0])));
				}
				if (casts[1] != null) {
					if (reverses[1] != null) {
						flow = flow.withValues(valueFlow -> valueFlow.transform((TypeToken<Object>) target.getType(1),
							transformReversible(casts[1], reverses[1])));
					} else {
						flow = flow
							.withValues(valueFlow -> valueFlow.transform((TypeToken<Object>) target.getType(1), transform(casts[1])));
					}
				}
				return flow.gatherPassive();
			};
		}

		@Override
		protected void setupConversions(ConversionBuilder<ObservableMultiMap> builder) {
			builder.convertibleTo(Event, new ModelConverter<ObservableMultiMap, Observable>() {
				@Override
				public ModelInstanceConverter<ObservableMultiMap, Observable> convert(ModelInstanceType<ObservableMultiMap, ?> source,
					ModelInstanceType<Observable, ?> dest) throws IllegalArgumentException {
					if (dest.getType(0) == TypeTokens.get().VOID || TypeTokens.getRawType(dest.getType(0)) == void.class) {
						return src -> src.changes().map(__ -> null);
					} else if (dest.getType(0).isAssignableFrom(
						TypeTokens.get().keyFor(ObservableMultiMapEvent.class).parameterized(source.getType(0), source.getType(1)))) {
						return src -> src.changes();
					} else
						throw new IllegalArgumentException("Cannot convert from " + source + " to " + dest);
				}
			});
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
		protected ModelInstanceConverter<ObservableSortedMultiMap, ObservableSortedMultiMap> convertType(
			ModelInstanceType<ObservableSortedMultiMap, ?> target, Function<Object, Object>[] casts, Function<Object, Object>[] reverses) {
			if (casts[0] != null && reverses[0] == null)
				return null; // Need reverse for key mapping
			return src -> {
				ObservableSortedMultiMap.SortedMultiMapFlow<Object, Object> flow = src.flow();
				if (casts[0] != null) {
					flow = flow.withStillSortedKeys(keyFlow -> keyFlow.transformEquivalent((TypeToken<Object>) target.getType(0),
						transformReversible(casts[0], reverses[0])));
				}
				if (casts[1] != null) {
					if (reverses[1] != null) {
						flow = flow.withValues(valueFlow -> valueFlow.transform((TypeToken<Object>) target.getType(1),
							transformReversible(casts[1], reverses[1])));
					} else {
						flow = flow
							.withValues(valueFlow -> valueFlow.transform((TypeToken<Object>) target.getType(1), transform(casts[1])));
					}
				}
				return flow.gatherPassive();
			};
		}

		@Override
		protected void setupConversions(ConversionBuilder<ObservableSortedMultiMap> builder) {
			builder.convertibleTo(MultiMap, (source, dest) -> src -> src)//
			.convertibleTo(Event, new ModelConverter<ObservableSortedMultiMap, Observable>() {
				@Override
				public ModelInstanceConverter<ObservableSortedMultiMap, Observable> convert(
					ModelInstanceType<ObservableSortedMultiMap, ?> source, ModelInstanceType<Observable, ?> dest)
						throws IllegalArgumentException {
					if (dest.getType(0) == TypeTokens.get().VOID || TypeTokens.getRawType(dest.getType(0)) == void.class) {
						return src -> src.changes().map(__ -> null);
					} else if (dest.getType(0).isAssignableFrom(
						TypeTokens.get().keyFor(ObservableMultiMapEvent.class).parameterized(source.getType(0), source.getType(1)))) {
						return src -> src.changes();
					} else
						throw new IllegalArgumentException("Cannot convert from " + source + " to " + dest);
				}
			});
		}
	};
}
