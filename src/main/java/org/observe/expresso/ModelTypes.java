package org.observe.expresso;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.function.Function;

import org.observe.Observable;
import org.observe.ObservableAction;
import org.observe.ObservableValue;
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
import org.observe.config.ObservableValueSet;
import org.observe.util.TypeTokens;
import org.qommons.BiTuple;
import org.qommons.ClassMap;
import org.qommons.ClassMap.TypeMatch;
import org.qommons.LambdaUtils;
import org.qommons.collect.BetterSortedList;
import org.qommons.collect.MultiMap;
import org.qommons.collect.SortedMultiMap;

import com.google.common.reflect.TypeToken;

/** Standard {@link ModelType}s */
public class ModelTypes {
	/** Used for disabled settable values */
	public static final Function<Object, String> NOT_REVERSIBLE = LambdaUtils.constantFn("Not reversible", "Not reversible",
		"Not reversible");

	private static final ClassMap<ModelType<?>> ALL_TYPES = new ClassMap<>();

	/** A nested model in a model */
	public static final ModelType.UnTyped<ObservableModelSet> Model = new ModelType.UnTyped<ObservableModelSet>("Model",
		ObservableModelSet.class) {
	};
	/** An {@link Observable} */
	public static final EventModelType Event = new EventModelType();
	/** An {@link ObservableAction} */
	public static final ActionModelType Action = new ActionModelType();
	/** A {@link SettableValue} */
	public static final ValueModelType Value = new ValueModelType();

	/** An {@link ObservableCollection} */
	public static final CollectionModelType Collection = new CollectionModelType();
	/** An {@link ObservableSortedCollection} */
	public static final SortedCollectionModelType SortedCollection = new SortedCollectionModelType();
	/** An {@link ObservableSet} */
	public static final SetModelType Set = new SetModelType();
	/** An {@link ObservableSortedSet} */
	public static final SortedSetModelType SortedSet = new SortedSetModelType();
	/** An {@link ObservableValueSet} */
	public static final ValueSetModelType ValueSet = new ValueSetModelType();
	/** An {@link ObservableMap} */
	public static final MapModelType Map = new MapModelType();
	/** An {@link ObservableSortedMap} */
	public static final SortedMapModelType SortedMap = new SortedMapModelType();
	/** An {@link ObservableMultiMap} */
	public static final MultiMapModelType MultiMap = new MultiMapModelType();
	/** An {@link ObservableSortedMultiMap} */
	public static final SortedMultiMapModelType SortedMultiMap = new SortedMultiMapModelType();

	static {
		ALL_TYPES.with(Observable.class, Event);
		ALL_TYPES.with(ObservableAction.class, Action);
		ALL_TYPES.with(ObservableValue.class, Value);
		ALL_TYPES.with(Collection.class, Collection);
		ALL_TYPES.with(BetterSortedList.class, SortedCollection);
		ALL_TYPES.with(Set.class, Set);
		ALL_TYPES.with(SortedSet.class, SortedSet);
		ALL_TYPES.with(ObservableValueSet.class, ValueSet);
		ALL_TYPES.with(Map.class, Map);
		ALL_TYPES.with(SortedMap.class, SortedMap);
		ALL_TYPES.with(MultiMap.class, MultiMap);
		ALL_TYPES.with(SortedMultiMap.class, SortedMultiMap);
	}

	/** See {@link ModelTypes#Event} */
	public static class EventModelType extends ModelType.SingleTyped<Observable<?>> {
		private EventModelType() {
			super("Event", (Class<Observable<?>>) (Class<?>) Observable.class);
		}

		@Override
		public ModelInstanceType<Observable<?>, Observable<?>> any() {
			return (ModelInstanceType<Observable<?>, Observable<?>>) super.any();
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<Observable<?>, V, Observable<V>> forType(TypeToken<V> type) {
			return (ModelInstanceType.SingleTyped<Observable<?>, V, Observable<V>>) super.forType(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<Observable<?>, V, Observable<V>> forType(Class<V> type) {
			return (ModelInstanceType.SingleTyped<Observable<?>, V, Observable<V>>) super.forType(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<Observable<?>, ? extends V, Observable<? extends V>> below(TypeToken<V> type) {
			return (ModelInstanceType.SingleTyped<Observable<?>, ? extends V, Observable<? extends V>>) super.below(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<Observable<?>, ? extends V, Observable<? extends V>> below(Class<V> type) {
			return (ModelInstanceType.SingleTyped<Observable<?>, ? extends V, Observable<? extends V>>) super.below(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<Observable<?>, ? super V, Observable<? super V>> above(TypeToken<V> type) {
			return (ModelInstanceType.SingleTyped<Observable<?>, ? super V, Observable<? super V>>) super.above(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<Observable<?>, ? super V, Observable<? super V>> above(Class<V> type) {
			return (ModelInstanceType.SingleTyped<Observable<?>, ? super V, Observable<? super V>>) super.above(type);
		}

		@Override
		protected Function<Observable<?>, Observable<?>> convertType(ModelInstanceType<Observable<?>, ?> target,
			Function<Object, Object>[] casts, Function<Object, Object>[] reverses) {
			return src -> src.map(casts[0]);
		}
	}

	/** See {@link ModelTypes#Action} */
	public static class ActionModelType extends ModelType.SingleTyped<ObservableAction<?>> {
		private ActionModelType() {
			super("Action", (Class<ObservableAction<?>>) (Class<?>) ObservableAction.class);
		}

		@Override
		public ModelInstanceType<ObservableAction<?>, ObservableAction<?>> any() {
			return (ModelInstanceType<ObservableAction<?>, ObservableAction<?>>) super.any();
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableAction<?>, V, ObservableAction<V>> forType(TypeToken<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableAction<?>, V, ObservableAction<V>>) super.forType(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableAction<?>, V, ObservableAction<V>> forType(Class<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableAction<?>, V, ObservableAction<V>>) super.forType(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableAction<?>, ? extends V, ObservableAction<? extends V>> below(TypeToken<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableAction<?>, ? extends V, ObservableAction<? extends V>>) super.below(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableAction<?>, ? extends V, ObservableAction<? extends V>> below(Class<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableAction<?>, ? extends V, ObservableAction<? extends V>>) super.below(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableAction<?>, ? super V, ObservableAction<? super V>> above(TypeToken<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableAction<?>, ? super V, ObservableAction<? super V>>) super.above(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableAction<?>, ? super V, ObservableAction<? super V>> above(Class<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableAction<?>, ? super V, ObservableAction<? super V>>) super.above(type);
		}

		@Override
		protected Function<ObservableAction<?>, ObservableAction<?>> convertType(ModelInstanceType<ObservableAction<?>, ?> target,
			Function<Object, Object>[] casts, Function<Object, Object>[] reverses) {
			return src -> src.map((TypeToken<Object>) target.getType(0), casts[0]);
		}
	}

	/** See {@link ModelTypes#Value} */
	public static class ValueModelType extends ModelType.SingleTyped<SettableValue<?>> {
		private ValueModelType() {
			super("Value", (Class<SettableValue<?>>) (Class<?>) SettableValue.class);
		}

		@Override
		public ModelInstanceType<SettableValue<?>, SettableValue<?>> any() {
			return (ModelInstanceType<SettableValue<?>, SettableValue<?>>) super.any();
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<SettableValue<?>, V, SettableValue<V>> forType(TypeToken<V> type) {
			return (ModelInstanceType.SingleTyped<SettableValue<?>, V, SettableValue<V>>) super.forType(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<SettableValue<?>, V, SettableValue<V>> forType(Class<V> type) {
			return (ModelInstanceType.SingleTyped<SettableValue<?>, V, SettableValue<V>>) super.forType(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<SettableValue<?>, ? extends V, SettableValue<? extends V>> below(TypeToken<V> type) {
			return (ModelInstanceType.SingleTyped<SettableValue<?>, ? extends V, SettableValue<? extends V>>) super.below(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<SettableValue<?>, ? extends V, SettableValue<? extends V>> below(Class<V> type) {
			return (ModelInstanceType.SingleTyped<SettableValue<?>, ? extends V, SettableValue<? extends V>>) super.below(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<SettableValue<?>, ? super V, SettableValue<? super V>> above(TypeToken<V> type) {
			return (ModelInstanceType.SingleTyped<SettableValue<?>, ? super V, SettableValue<? super V>>) super.above(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<SettableValue<?>, ? super V, SettableValue<? super V>> above(Class<V> type) {
			return (ModelInstanceType.SingleTyped<SettableValue<?>, ? super V, SettableValue<? super V>>) super.above(type);
		}

		@Override
		protected Function<SettableValue<?>, SettableValue<?>> convertType(ModelInstanceType<SettableValue<?>, ?> target,
			Function<Object, Object>[] casts, Function<Object, Object>[] reverses) {
			if (reverses != null) {
				return src -> ((SettableValue<Object>) src).transformReversible((TypeToken<Object>) target.getType(0),
					transformReversible(casts[0], reverses[0]));
			} else {
				return src -> SettableValue.asSettable(
					((SettableValue<Object>) src).transform((TypeToken<Object>) target.getType(0), transform(casts[0])), NOT_REVERSIBLE);
			}
		}

		@Override
		protected void setupConversions(ConversionBuilder<SettableValue<?>> builder) {
			builder.convertibleTo(Event, new ModelConverter.SimpleSingleTyped<SettableValue<?>, Observable<?>>() {
				@Override
				public ModelInstanceConverter<SettableValue<?>, Observable<?>> convert(ModelInstanceType<SettableValue<?>, ?> source,
					ModelInstanceType<Observable<?>, ?> dest) throws IllegalArgumentException {
					if (dest.getType(0) == TypeTokens.get().VOID || TypeTokens.getRawType(dest.getType(0)) == void.class) {
						return ModelType.converter(LambdaUtils.printableFn(src -> src.noInitChanges().map(__ -> null), "changes", null),
							dest);
					} else {
						TypeToken<?> oveType = TypeTokens.get().keyFor(ObservableValueEvent.class).parameterized(source.getType(0));
						if (dest.getType(0).isAssignableFrom(oveType))
							return ModelType.converter(LambdaUtils.printableFn(src -> src.noInitChanges(), "changes", null),
								dest.getModelType().forTypes(oveType));
						else
							return SimpleSingleTyped.super.convert(source, dest);
					}
				}

				@Override
				public <S, T> Observable<?> convert(SettableValue<?> source, TypeToken<T> targetType, Function<S, T> cast,
					Function<T, S> reverse) {
					if (cast != null)
						return ((SettableValue<S>) source).value().noInit().map(cast);
					else
						return source.value().noInit();
				}
			});
			builder.convertibleFromAny(new ModelConverter<Object, SettableValue<?>>() {
				private boolean isRecursing;

				@Override
				public ModelInstanceConverter<Object, SettableValue<?>> convert(ModelInstanceType<Object, ?> source,
					ModelInstanceType<SettableValue<?>, ?> dest) throws IllegalArgumentException {
					/* This converter enables the passing of actual model value holders into java code.
					 * E.g. a method which accepts a SettableValue<Double> instead of just a double
					 * so it can observe changes in the model value instead of just the current value. */
					BiTuple<Class<?>, ModelType<?>> convertModel = ALL_TYPES.getEntry(TypeTokens.getRawType(dest.getType(0)),
						TypeMatch.SUPER_TYPE);
					if (convertModel != null) {
						TypeToken<?>[] destParamTypes = new TypeToken[convertModel.getValue2().getTypeCount()];
						for (int i = 0; i < destParamTypes.length; i++)
							destParamTypes[i] = dest.getType(0).resolveType(convertModel.getValue1().getTypeParameters()[i]);
						ModelInstanceType<?, ?> destModelType = convertModel.getValue2().forTypes(destParamTypes);
						ModelInstanceConverter<?, ?> valueConverter = source.convert(destModelType);
						if (valueConverter == null)
							return null;
						ModelInstanceType<SettableValue<?>, ?> type = Value.forType(//
							TypeTokens.get().keyFor(valueConverter.getType().getModelType().modelType).parameterized(destParamTypes));
						return new ModelInstanceConverter<Object, SettableValue<?>>() {
							@Override
							public SettableValue<?> convert(Object sourceV) {
								return SettableValue.asSettable(//
									ObservableValue.of((TypeToken<Object>) type.getType(0), //
										((ModelInstanceConverter<Object, Object>) valueConverter).convert(sourceV)), //
									NOT_REVERSIBLE);
							}

							@Override
							public ModelInstanceType<SettableValue<?>, ?> getType() {
								return type;
							}
						};
					} else if (isRecursing) {
						return null;
					} else {
						ModelInstanceType<SettableValue<?>, ? extends SettableValue<?>> sourceType = Value.forType(//
							TypeTokens.get().keyFor(source.getModelType().modelType).parameterized(source.getTypeList()));
						ModelInstanceConverter<SettableValue<?>, SettableValue<?>> valueConverter;
						// This call is prone to infinite recursion, so we must prevent that explicitly
						isRecursing = true;
						try {
							valueConverter = sourceType.convert(dest);
						} finally {
							isRecursing = false;
						}
						if (valueConverter == null)
							return null;
						return new ModelInstanceConverter<Object, SettableValue<?>>() {
							@Override
							public SettableValue<?> convert(Object sourceV) {
								return valueConverter.convert(//
									SettableValue.of((TypeToken<Object>) sourceType.getType(0), sourceV, "Unmodifiable"));
							}

							@Override
							public ModelInstanceType<SettableValue<?>, ?> getType() {
								return valueConverter.getType();
							}
						};
					}
				}
			});
			builder.convertibleToAny(new ModelConverter<SettableValue<?>, Object>() {
				@Override
				public ModelInstanceConverter<SettableValue<?>, Object> convert(ModelInstanceType<SettableValue<?>, ?> source,
					ModelInstanceType<Object, ?> dest) throws IllegalArgumentException {
					// Let's see if the source value is a model holder
					Class<?> rawSourceType = TypeTokens.getRawType(source.getType(0));
					// The source type has to be convertible observably to the model
					boolean ov = rawSourceType == ObservableValue.class;
					if (ov && ((ModelType<?>) dest.getModelType()) == Value // This is a special case we can handle
						|| dest.getModelType().modelType.isAssignableFrom(rawSourceType)) {
						TypeToken<?>[] sourceParamTypes = new TypeToken[dest.getModelType().getTypeCount()];
						BiTuple<Class<?>, ModelType<?>> convertModel = ALL_TYPES.getEntry(dest.getModelType().modelType,
							TypeMatch.SUPER_TYPE);
						for (int i = 0; i < sourceParamTypes.length; i++)
							sourceParamTypes[i] = source.getType(0).resolveType(convertModel.getValue1().getTypeParameters()[i]);
						ModelInstanceType<Object, ?> sourceModelType = dest.getModelType().forTypes(sourceParamTypes);
						ModelInstanceConverter<Object, ?> valueConverter = sourceModelType.convert(dest);
						if (valueConverter == null)
							return null;
						return new ModelInstanceConverter<SettableValue<?>, Object>() {
							@Override
							public Object convert(SettableValue<?> sourceV) {
								Object v = sourceV.get();
								if (v == null)
									throw new IllegalStateException("A value converted to a model cannot be null");
								if (ov)
									v = SettableValue.asSettable((ObservableValue<Object>) v, __ -> "Not Settable");
								return valueConverter.convert(v);
							}

							@Override
							public ModelInstanceType<Object, ?> getType() {
								return (ModelInstanceType<Object, ?>) valueConverter.getType();
							}
						};
					}
					return null;
				}
			});
			builder.convertSelf(new ModelConverter<SettableValue<?>, SettableValue<?>>() {
				@Override
				public ModelInstanceConverter<SettableValue<?>, SettableValue<?>> convert(ModelInstanceType<SettableValue<?>, ?> source,
					ModelInstanceType<SettableValue<?>, ?> dest) throws IllegalArgumentException {
					Class<?> rawSource = TypeTokens.getRawType(source.getType(0));
					if (!ObservableValue.class.isAssignableFrom(rawSource))
						return null;
					TypeToken<?> sourceType = source.getType(0).resolveType(ObservableValue.class.getTypeParameters()[0]);
					if (!TypeTokens.get().isAssignable(dest.getType(0), sourceType))
						return null;
					ModelInstanceType<SettableValue<?>, ?> type = Value.forType(sourceType);
					if (SettableValue.class.isAssignableFrom(rawSource)) {
						return new ModelInstanceConverter<SettableValue<?>, SettableValue<?>>() {
							@Override
							public SettableValue<?> convert(SettableValue<?> sourceV) {
								return SettableValue.flatten((SettableValue<SettableValue<Object>>) sourceV);
							}

							@Override
							public ModelInstanceType<SettableValue<?>, ?> getType() {
								return type;
							}
						};
					} else {
						return new ModelInstanceConverter<SettableValue<?>, SettableValue<?>>() {
							@Override
							public SettableValue<?> convert(SettableValue<?> sourceV) {
								return SettableValue.asSettable(//
									ObservableValue.flatten((SettableValue<ObservableValue<Object>>) sourceV), //
									NOT_REVERSIBLE);
							}

							@Override
							public ModelInstanceType<SettableValue<?>, ?> getType() {
								return type;
							}
						};
					}
				}
			});
		}
	}

	/** See {@link ModelTypes#Collection} */
	public static class CollectionModelType extends ModelType.SingleTyped<ObservableCollection<?>> {
		private CollectionModelType() {
			super("Collection", (Class<ObservableCollection<?>>) (Class<?>) ObservableCollection.class);
		}

		@Override
		public ModelInstanceType<ObservableCollection<?>, ObservableCollection<?>> any() {
			return (ModelInstanceType<ObservableCollection<?>, ObservableCollection<?>>) super.any();
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableCollection<?>, V, ObservableCollection<V>> forType(TypeToken<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableCollection<?>, V, ObservableCollection<V>>) super.forType(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableCollection<?>, V, ObservableCollection<V>> forType(Class<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableCollection<?>, V, ObservableCollection<V>>) super.forType(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableCollection<?>, ? extends V, ObservableCollection<? extends V>> below(
			TypeToken<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableCollection<?>, ? extends V, ObservableCollection<? extends V>>) super.below(
				type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableCollection<?>, ? extends V, ObservableCollection<? extends V>> below(
			Class<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableCollection<?>, ? extends V, ObservableCollection<? extends V>>) super.below(
				type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableCollection<?>, ? super V, ObservableCollection<? super V>> above(
			TypeToken<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableCollection<?>, ? super V, ObservableCollection<? super V>>) super.above(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableCollection<?>, ? super V, ObservableCollection<? super V>> above(Class<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableCollection<?>, ? super V, ObservableCollection<? super V>>) super.above(type);
		}

		@Override
		protected Function<ObservableCollection<?>, ObservableCollection<?>> convertType(
			ModelInstanceType<ObservableCollection<?>, ?> target, Function<Object, Object>[] casts, Function<Object, Object>[] reverses) {
			if (reverses != null)
				return src -> ((ObservableCollection<Object>) src).flow()
					.transform((TypeToken<Object>) target.getType(0), transformReversible(casts[0], reverses[0])).collectPassive();
				else
					return src -> ((ObservableCollection<Object>) src).flow()
						.transform((TypeToken<Object>) target.getType(0), transform(casts[0])).filterMod(opts -> opts.noAdd("Not reversible"))
						.collectPassive();
		}

		@Override
		protected void setupConversions(ConversionBuilder<ObservableCollection<?>> builder) {
			builder.convertibleTo(Event, new ModelConverter<ObservableCollection<?>, Observable<?>>() {
				@Override
				public ModelInstanceConverter<ObservableCollection<?>, Observable<?>> convert(
					ModelInstanceType<ObservableCollection<?>, ?> source, ModelInstanceType<Observable<?>, ?> dest)
						throws IllegalArgumentException {
					if (dest.getType(0) == TypeTokens.get().VOID || TypeTokens.getRawType(dest.getType(0)) == void.class) {
						return ModelType.converter(LambdaUtils.printableFn(src -> src.changes().map(__ -> null), "changes", null), dest);
					} else {
						TypeToken<?> oceType = TypeTokens.get().keyFor(ObservableCollectionEvent.class).parameterized(source.getType(0));
						if (dest.getType(0).isAssignableFrom(oceType)) {
							return ModelType.converter(LambdaUtils.printableFn(src -> src.changes(), "changes", null),
								dest.getModelType().forTypes(oceType));
						} else
							throw new IllegalArgumentException("Cannot convert from " + source + " to " + dest);
					}
				}
			});
		}
	}

	/** See {@link ModelTypes#SortedCollection} */
	public static class SortedCollectionModelType extends ModelType.SingleTyped<ObservableSortedCollection<?>> {
		SortedCollectionModelType() {
			super("SortedCollection", (Class<ObservableSortedCollection<?>>) (Class<?>) ObservableSortedCollection.class);
		}

		@Override
		public ModelInstanceType<ObservableSortedCollection<?>, ObservableSortedCollection<?>> any() {
			return (ModelInstanceType<ObservableSortedCollection<?>, ObservableSortedCollection<?>>) super.any();
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableSortedCollection<?>, V, ObservableSortedCollection<V>> forType(
			TypeToken<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableSortedCollection<?>, V, ObservableSortedCollection<V>>) super.forType(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableSortedCollection<?>, V, ObservableSortedCollection<V>> forType(Class<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableSortedCollection<?>, V, ObservableSortedCollection<V>>) super.forType(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableSortedCollection<?>, ? extends V, ObservableSortedCollection<? extends V>> below(
			TypeToken<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableSortedCollection<?>, ? extends V, ObservableSortedCollection<? extends V>>) super.below(
				type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableSortedCollection<?>, ? extends V, ObservableSortedCollection<? extends V>> below(
			Class<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableSortedCollection<?>, ? extends V, ObservableSortedCollection<? extends V>>) super.below(
				type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableSortedCollection<?>, ? super V, ObservableSortedCollection<? super V>> above(
			TypeToken<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableSortedCollection<?>, ? super V, ObservableSortedCollection<? super V>>) super.above(
				type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableSortedCollection<?>, ? super V, ObservableSortedCollection<? super V>> above(
			Class<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableSortedCollection<?>, ? super V, ObservableSortedCollection<? super V>>) super.above(
				type);
		}

		@Override
		protected Function<ObservableSortedCollection<?>, ObservableSortedCollection<?>> convertType(
			ModelInstanceType<ObservableSortedCollection<?>, ?> target, Function<Object, Object>[] casts,
			Function<Object, Object>[] reverses) {
			if (reverses != null) {
				return src -> ((ObservableSortedCollection<Object>) src).flow()
					.transformEquivalent((TypeToken<Object>) target.getType(0), transformReversible(casts[0], reverses[0]))
					.collectPassive();
			} else
				return null; // Need reverse for transformEquivalent
		}

		@Override
		protected void setupConversions(ConversionBuilder<ObservableSortedCollection<?>> builder) {
			builder
			.convertibleTo(Collection,
				(source, dest) -> ModelType.converter(LambdaUtils.printableFn(src -> src, "trivial", "trivial"), dest))//
			.convertibleTo(Event, new ModelConverter<ObservableSortedCollection<?>, Observable<?>>() {
				@Override
				public ModelInstanceConverter<ObservableSortedCollection<?>, Observable<?>> convert(
					ModelInstanceType<ObservableSortedCollection<?>, ?> source, ModelInstanceType<Observable<?>, ?> dest)
						throws IllegalArgumentException {
					if (dest.getType(0) == TypeTokens.get().VOID || TypeTokens.getRawType(dest.getType(0)) == void.class) {
						return ModelType.converter(LambdaUtils.printableFn(src -> src.changes().map(__ -> null), "changes", null),
							dest);
					} else {
						TypeToken<?> oceType = TypeTokens.get().keyFor(ObservableCollectionEvent.class)
							.parameterized(source.getType(0));
						if (dest.getType(0).isAssignableFrom(oceType))
							return ModelType.converter(LambdaUtils
								.printableFn(LambdaUtils.printableFn(src -> src.changes(), "changes", null), "changes", null),
								dest.getModelType().forTypes(oceType));
						else
							throw new IllegalArgumentException("Cannot convert from " + source + " to " + dest);
					}
				}
			});
		}
	}

	/** See {@link ModelTypes#Set} */
	public static class SetModelType extends ModelType.SingleTyped<ObservableSet<?>> {
		SetModelType() {
			super("Set", (Class<ObservableSet<?>>) (Class<?>) ObservableSet.class);
		}

		@Override
		public ModelInstanceType<ObservableSet<?>, ObservableSet<?>> any() {
			return (ModelInstanceType<ObservableSet<?>, ObservableSet<?>>) super.any();
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableSet<?>, V, ObservableSet<V>> forType(TypeToken<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableSet<?>, V, ObservableSet<V>>) super.forType(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableSet<?>, V, ObservableSet<V>> forType(Class<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableSet<?>, V, ObservableSet<V>>) super.forType(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableSet<?>, ? extends V, ObservableSet<? extends V>> below(TypeToken<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableSet<?>, ? extends V, ObservableSet<? extends V>>) super.below(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableSet<?>, ? extends V, ObservableSet<? extends V>> below(Class<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableSet<?>, ? extends V, ObservableSet<? extends V>>) super.below(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableSet<?>, ? super V, ObservableSet<? super V>> above(TypeToken<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableSet<?>, ? super V, ObservableSet<? super V>>) super.above(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableSet<?>, ? super V, ObservableSet<? super V>> above(Class<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableSet<?>, ? super V, ObservableSet<? super V>>) super.above(type);
		}

		@Override
		protected Function<ObservableSet<?>, ObservableSet<?>> convertType(ModelInstanceType<ObservableSet<?>, ?> target,
			Function<Object, Object>[] casts, Function<Object, Object>[] reverses) {
			if (reverses != null) {
				return src -> ((ObservableSet<Object>) src).flow()
					.transformEquivalent((TypeToken<Object>) target.getType(0), transformReversible(casts[0], reverses[0]))
					.collectPassive();
			} else
				return null; // Need reverse for transformEquivalent
		}

		@Override
		protected void setupConversions(ConversionBuilder<ObservableSet<?>> builder) {
			builder
			.<ObservableCollection<?>> convertibleTo(Collection,
				(source, dest) -> ModelType.converter(LambdaUtils.printableFn(src -> src, "trivial", "trivial"), dest))//
			.convertibleTo(Event, new ModelConverter<ObservableSet<?>, Observable<?>>() {
				@Override
				public ModelInstanceConverter<ObservableSet<?>, Observable<?>> convert(ModelInstanceType<ObservableSet<?>, ?> source,
					ModelInstanceType<Observable<?>, ?> dest) throws IllegalArgumentException {
					if (dest.getType(0) == TypeTokens.get().VOID || TypeTokens.getRawType(dest.getType(0)) == void.class) {
						return ModelType.converter(LambdaUtils.printableFn(src -> src.changes().map(__ -> null), "changes", null),
							dest);
					} else {
						TypeToken<?> oceType = TypeTokens.get().keyFor(ObservableCollectionEvent.class)
							.parameterized(source.getType(0));
						if (dest.getType(0).isAssignableFrom(oceType))
							return ModelType.converter(LambdaUtils.printableFn(src -> src.changes(), "changes", null),
								dest.getModelType().forTypes(oceType));
						else
							throw new IllegalArgumentException("Cannot convert from " + source + " to " + dest);
					}
				}
			});
		}
	}

	/** See {@link ModelTypes#SortedSet} */
	public static class SortedSetModelType extends ModelType.SingleTyped<ObservableSortedSet<?>> {
		SortedSetModelType() {
			super("SortedSet", (Class<ObservableSortedSet<?>>) (Class<?>) ObservableSortedSet.class);
		}

		@Override
		public ModelInstanceType<ObservableSortedSet<?>, ObservableSortedSet<?>> any() {
			return (ModelInstanceType<ObservableSortedSet<?>, ObservableSortedSet<?>>) super.any();
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableSortedSet<?>, V, ObservableSortedSet<V>> forType(TypeToken<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableSortedSet<?>, V, ObservableSortedSet<V>>) super.forType(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableSortedSet<?>, V, ObservableSortedSet<V>> forType(Class<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableSortedSet<?>, V, ObservableSortedSet<V>>) super.forType(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableSortedSet<?>, ? extends V, ObservableSortedSet<? extends V>> below(
			TypeToken<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableSortedSet<?>, ? extends V, ObservableSortedSet<? extends V>>) super.below(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableSortedSet<?>, ? extends V, ObservableSortedSet<? extends V>> below(
			Class<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableSortedSet<?>, ? extends V, ObservableSortedSet<? extends V>>) super.below(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableSortedSet<?>, ? super V, ObservableSortedSet<? super V>> above(
			TypeToken<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableSortedSet<?>, ? super V, ObservableSortedSet<? super V>>) super.above(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableSortedSet<?>, ? super V, ObservableSortedSet<? super V>> above(Class<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableSortedSet<?>, ? super V, ObservableSortedSet<? super V>>) super.above(type);
		}

		@Override
		protected Function<ObservableSortedSet<?>, ObservableSortedSet<?>> convertType(ModelInstanceType<ObservableSortedSet<?>, ?> target,
			Function<Object, Object>[] casts, Function<Object, Object>[] reverses) {
			if (reverses != null) {
				return src -> ((ObservableSortedSet<Object>) src).flow()
					.transformEquivalent((TypeToken<Object>) target.getType(0), transformReversible(casts[0], reverses[0]))
					.collectPassive();
			} else
				return null; // Need reverse to transform equivalent
		}

		@Override
		protected void setupConversions(ConversionBuilder<ObservableSortedSet<?>> builder) {
			builder
			.convertibleTo(Collection,
				(source, dest) -> ModelType.converter(LambdaUtils.printableFn(src -> src, "trivial", "trivial"), dest))//
			.convertibleTo(SortedCollection,
				(source, dest) -> ModelType.converter(LambdaUtils.printableFn(src -> src, "trivial", "trivial"), dest))//
			.convertibleTo(Set, (source, dest) -> ModelType.converter(LambdaUtils.printableFn(src -> src, "trivial", "trivial"), dest))//
			.convertibleTo(Event, new ModelConverter<ObservableSortedSet<?>, Observable<?>>() {
				@Override
				public ModelInstanceConverter<ObservableSortedSet<?>, Observable<?>> convert(
					ModelInstanceType<ObservableSortedSet<?>, ?> source, ModelInstanceType<Observable<?>, ?> dest)
						throws IllegalArgumentException {
					if (dest.getType(0) == TypeTokens.get().VOID || TypeTokens.getRawType(dest.getType(0)) == void.class) {
						return ModelType.converter(LambdaUtils.printableFn(src -> src.changes().map(__ -> null), "changes", null),
							dest);
					} else {
						TypeToken<?> oceType = TypeTokens.get().keyFor(ObservableCollectionEvent.class)
							.parameterized(source.getType(0));
						if (dest.getType(0).isAssignableFrom(oceType))
							return ModelType.converter(LambdaUtils.printableFn(src -> src.changes(), "changes", null),
								dest.getModelType().forTypes(oceType));
						else
							throw new IllegalArgumentException("Cannot convert from " + source + " to " + dest);
					}
				}
			});
		}
	}

	/** See {@link ModelTypes#ValueSet} */
	public static class ValueSetModelType extends ModelType.SingleTyped<ObservableValueSet<?>> {
		private ValueSetModelType() {
			super("ValueSet", (Class<ObservableValueSet<?>>) (Class<?>) ObservableValueSet.class);
		}

		@Override
		public ModelInstanceType<ObservableValueSet<?>, ObservableValueSet<?>> any() {
			return (ModelInstanceType<ObservableValueSet<?>, ObservableValueSet<?>>) super.any();
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableValueSet<?>, V, ObservableValueSet<V>> forType(TypeToken<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableValueSet<?>, V, ObservableValueSet<V>>) super.forType(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableValueSet<?>, V, ObservableValueSet<V>> forType(Class<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableValueSet<?>, V, ObservableValueSet<V>>) super.forType(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableValueSet<?>, ? extends V, ObservableValueSet<? extends V>> below(
			TypeToken<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableValueSet<?>, ? extends V, ObservableValueSet<? extends V>>) super.below(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableValueSet<?>, ? extends V, ObservableValueSet<? extends V>> below(Class<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableValueSet<?>, ? extends V, ObservableValueSet<? extends V>>) super.below(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableValueSet<?>, ? super V, ObservableValueSet<? super V>> above(TypeToken<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableValueSet<?>, ? super V, ObservableValueSet<? super V>>) super.above(type);
		}

		@Override
		public <V> ModelInstanceType.SingleTyped<ObservableValueSet<?>, ? super V, ObservableValueSet<? super V>> above(Class<V> type) {
			return (ModelInstanceType.SingleTyped<ObservableValueSet<?>, ? super V, ObservableValueSet<? super V>>) super.above(type);
		}

		@Override
		protected Function<ObservableValueSet<?>, ObservableValueSet<?>> convertType(ModelInstanceType<ObservableValueSet<?>, ?> target,
			Function<Object, Object>[] casts, Function<Object, Object>[] reverses) {
			return null;
		}

		@Override
		protected void setupConversions(ConversionBuilder<ObservableValueSet<?>> builder) {
			builder//
			.convertibleTo(Collection,
				(source, dest) -> ModelType.converter(LambdaUtils.printableFn(src -> src.getValues(), "values", null), // dest))//
					Collection.forType(source.getType(0))))//
			.convertibleTo(Event, new ModelConverter<ObservableValueSet<?>, Observable<?>>() {
				@Override
				public ModelInstanceConverter<ObservableValueSet<?>, Observable<?>> convert(
					ModelInstanceType<ObservableValueSet<?>, ?> source, ModelInstanceType<Observable<?>, ?> dest)
						throws IllegalArgumentException {
					if (dest.getType(0) == TypeTokens.get().VOID || TypeTokens.getRawType(dest.getType(0)) == void.class) {
						return ModelType.converter(
							LambdaUtils.printableFn(src -> src.getValues().changes().map(__ -> null), "changes", null), dest);
					} else {
						TypeToken<?> oceType = TypeTokens.get().keyFor(ObservableCollectionEvent.class)
							.parameterized(source.getType(0));
						if (dest.getType(0).isAssignableFrom(oceType)) {
							return ModelType.converter(LambdaUtils.printableFn(src -> src.getValues().changes(), "changes", null),
								dest.getModelType().forTypes(oceType));
						} else
							throw new IllegalArgumentException("Cannot convert from " + source + " to " + dest);
					}
				}
			});
		}
	}

	/** See {@link ModelTypes#Map} */
	public static class MapModelType extends ModelType.DoubleTyped<ObservableMap<?, ?>> {
		MapModelType() {
			super("Map", (Class<ObservableMap<?, ?>>) (Class<?>) ObservableMap.class);
		}

		@Override
		public ModelInstanceType<ObservableMap<?, ?>, ObservableMap<?, ?>> any() {
			return (ModelInstanceType<ObservableMap<?, ?>, ObservableMap<?, ?>>) super.any();
		}

		@Override
		public <K, V> ModelInstanceType.DoubleTyped<ObservableMap<?, ?>, K, V, ObservableMap<K, V>> forType(TypeToken<K> keyType,
			TypeToken<V> valueType) {
			return (ModelInstanceType.DoubleTyped<ObservableMap<?, ?>, K, V, ObservableMap<K, V>>) super.forType(keyType, valueType);
		}

		@Override
		public <K, V> ModelInstanceType.DoubleTyped<ObservableMap<?, ?>, K, V, ObservableMap<K, V>> forType(Class<K> keyType,
			Class<V> valueType) {
			return (ModelInstanceType.DoubleTyped<ObservableMap<?, ?>, K, V, ObservableMap<K, V>>) super.forType(keyType, valueType);
		}

		@Override
		public <K, V> ModelInstanceType.DoubleTyped<ObservableMap<?, ?>, ? extends K, ? extends V, ObservableMap<? extends K, ? extends V>> below(
			TypeToken<K> keyType, TypeToken<V> valueType) {
			return (ModelInstanceType.DoubleTyped<ObservableMap<?, ?>, ? extends K, ? extends V, ObservableMap<? extends K, ? extends V>>) super.below(
				keyType, valueType);
		}

		@Override
		public <K, V> ModelInstanceType.DoubleTyped<ObservableMap<?, ?>, ? extends K, ? extends V, ObservableMap<? extends K, ? extends V>> below(
			Class<K> keyType, Class<V> valueType) {
			return (ModelInstanceType.DoubleTyped<ObservableMap<?, ?>, ? extends K, ? extends V, ObservableMap<? extends K, ? extends V>>) super.below(
				keyType, valueType);
		}

		@Override
		public <K, V> ModelInstanceType.DoubleTyped<ObservableMap<?, ?>, ? super K, ? super V, ObservableMap<? super K, ? super V>> above(
			TypeToken<K> keyType, TypeToken<V> valueType) {
			return (ModelInstanceType.DoubleTyped<ObservableMap<?, ?>, ? super K, ? super V, ObservableMap<? super K, ? super V>>) super.above(
				keyType, valueType);
		}

		@Override
		public <K, V> ModelInstanceType.DoubleTyped<ObservableMap<?, ?>, ? super K, ? super V, ObservableMap<? super K, ? super V>> above(
			Class<K> keyType, Class<V> valueType) {
			return (ModelInstanceType.DoubleTyped<ObservableMap<?, ?>, ? super K, ? super V, ObservableMap<? super K, ? super V>>) super.above(
				keyType, valueType);
		}

		@Override
		protected Function<ObservableMap<?, ?>, ObservableMap<?, ?>> convertType(ModelInstanceType<ObservableMap<?, ?>, ?> target,
			Function<Object, Object>[] casts, Function<Object, Object>[] reverses) {
			return null; // ObservableMap doesn't have flow at the moment at least
		}

		@Override
		protected void setupConversions(ConversionBuilder<ObservableMap<?, ?>> builder) {
			builder.convertibleTo(Event, new ModelConverter<ObservableMap<?, ?>, Observable<?>>() {
				@Override
				public ModelInstanceConverter<ObservableMap<?, ?>, Observable<?>> convert(ModelInstanceType<ObservableMap<?, ?>, ?> source,
					ModelInstanceType<Observable<?>, ?> dest) throws IllegalArgumentException {
					if (dest.getType(0) == TypeTokens.get().VOID || TypeTokens.getRawType(dest.getType(0)) == void.class) {
						return ModelType.converter(LambdaUtils.printableFn(src -> src.changes().map(__ -> null), "changes", null), dest);
					} else {
						TypeToken<?> omeType = TypeTokens.get().keyFor(ObservableMapEvent.class).parameterized(source.getType(0),
							source.getType(1));
						if (dest.getType(0).isAssignableFrom(omeType))
							return ModelType.converter(LambdaUtils.printableFn(src -> src.changes(), "changes", null),
								dest.getModelType().forTypes(omeType));
						else
							throw new IllegalArgumentException("Cannot convert from " + source + " to " + dest);
					}
				}
			});
		}
	}

	/** See {@link ModelTypes#SortedMap} */
	public static class SortedMapModelType extends ModelType.DoubleTyped<ObservableSortedMap<?, ?>> {
		SortedMapModelType() {
			super("SortedMap", (Class<ObservableSortedMap<?, ?>>) (Class<?>) ObservableSortedMap.class);
		}

		@Override
		public ModelInstanceType<ObservableSortedMap<?, ?>, ObservableSortedMap<?, ?>> any() {
			return (ModelInstanceType<ObservableSortedMap<?, ?>, ObservableSortedMap<?, ?>>) super.any();
		}

		@Override
		public <K, V> ModelInstanceType.DoubleTyped<ObservableSortedMap<?, ?>, K, V, ObservableSortedMap<K, V>> forType(
			TypeToken<K> keyType, TypeToken<V> valueType) {
			return (ModelInstanceType.DoubleTyped<ObservableSortedMap<?, ?>, K, V, ObservableSortedMap<K, V>>) super.forType(keyType,
				valueType);
		}

		@Override
		public <K, V> ModelInstanceType.DoubleTyped<ObservableSortedMap<?, ?>, K, V, ObservableSortedMap<K, V>> forType(Class<K> keyType,
			Class<V> valueType) {
			return (ModelInstanceType.DoubleTyped<ObservableSortedMap<?, ?>, K, V, ObservableSortedMap<K, V>>) super.forType(keyType,
				valueType);
		}

		@Override
		public <K, V> ModelInstanceType.DoubleTyped<ObservableSortedMap<?, ?>, ? extends K, ? extends V, ObservableSortedMap<? extends K, ? extends V>> below(
			TypeToken<K> keyType, TypeToken<V> valueType) {
			return (ModelInstanceType.DoubleTyped<ObservableSortedMap<?, ?>, ? extends K, ? extends V, ObservableSortedMap<? extends K, ? extends V>>) super.below(
				keyType, valueType);
		}

		@Override
		public <K, V> ModelInstanceType.DoubleTyped<ObservableSortedMap<?, ?>, ? extends K, ? extends V, ObservableSortedMap<? extends K, ? extends V>> below(
			Class<K> keyType, Class<V> valueType) {
			return (ModelInstanceType.DoubleTyped<ObservableSortedMap<?, ?>, ? extends K, ? extends V, ObservableSortedMap<? extends K, ? extends V>>) super.below(
				keyType, valueType);
		}

		@Override
		public <K, V> ModelInstanceType.DoubleTyped<ObservableSortedMap<?, ?>, ? super K, ? super V, ObservableSortedMap<? super K, ? super V>> above(
			TypeToken<K> keyType, TypeToken<V> valueType) {
			return (ModelInstanceType.DoubleTyped<ObservableSortedMap<?, ?>, ? super K, ? super V, ObservableSortedMap<? super K, ? super V>>) super.above(
				keyType, valueType);
		}

		@Override
		public <K, V> ModelInstanceType.DoubleTyped<ObservableSortedMap<?, ?>, ? super K, ? super V, ObservableSortedMap<? super K, ? super V>> above(
			Class<K> keyType, Class<V> valueType) {
			return (ModelInstanceType.DoubleTyped<ObservableSortedMap<?, ?>, ? super K, ? super V, ObservableSortedMap<? super K, ? super V>>) super.above(
				keyType, valueType);
		}

		@Override
		protected Function<ObservableSortedMap<?, ?>, ObservableSortedMap<?, ?>> convertType(
			ModelInstanceType<ObservableSortedMap<?, ?>, ?> target, Function<Object, Object>[] casts, Function<Object, Object>[] reverses) {
			return null; // ObservableMap doesn't have flow at the moment at least
		}

		@Override
		protected void setupConversions(ConversionBuilder<ObservableSortedMap<?, ?>> builder) {
			builder
			.convertibleTo(Map, (source, dest) -> ModelType.converter(LambdaUtils.printableFn(src -> src, "trivial", "trivial"), dest))//
			.convertibleTo(Event, new ModelConverter<ObservableSortedMap<?, ?>, Observable<?>>() {
				@Override
				public ModelInstanceConverter<ObservableSortedMap<?, ?>, Observable<?>> convert(
					ModelInstanceType<ObservableSortedMap<?, ?>, ?> source, ModelInstanceType<Observable<?>, ?> dest)
						throws IllegalArgumentException {
					if (dest.getType(0) == TypeTokens.get().VOID || TypeTokens.getRawType(dest.getType(0)) == void.class) {
						return ModelType.converter(LambdaUtils.printableFn(src -> src.changes().map(__ -> null), "changes", null),
							dest);
					} else {
						TypeToken<?> omeType = TypeTokens.get().keyFor(ObservableMapEvent.class).parameterized(source.getType(0),
							source.getType(1));
						if (dest.getType(0).isAssignableFrom(omeType))
							return ModelType.converter(LambdaUtils.printableFn(src -> src.changes(), "changes", null),
								dest.getModelType().forTypes(omeType));
						else
							throw new IllegalArgumentException("Cannot convert from " + source + " to " + dest);
					}
				}
			});
		}
	}

	/** See {@link ModelTypes#MultiMap} */
	public static class MultiMapModelType extends ModelType.DoubleTyped<ObservableMultiMap<?, ?>> {
		MultiMapModelType() {
			super("MultiMap", (Class<ObservableMultiMap<?, ?>>) (Class<?>) ObservableMultiMap.class);
		}

		@Override
		public ModelInstanceType<ObservableMultiMap<?, ?>, ObservableMultiMap<?, ?>> any() {
			return (ModelInstanceType<ObservableMultiMap<?, ?>, ObservableMultiMap<?, ?>>) super.any();
		}

		@Override
		public <K, V> ModelInstanceType.DoubleTyped<ObservableMultiMap<?, ?>, K, V, ObservableMultiMap<K, V>> forType(TypeToken<K> keyType,
			TypeToken<V> valueType) {
			return (ModelInstanceType.DoubleTyped<ObservableMultiMap<?, ?>, K, V, ObservableMultiMap<K, V>>) super.forType(keyType,
				valueType);
		}

		@Override
		public <K, V> ModelInstanceType.DoubleTyped<ObservableMultiMap<?, ?>, K, V, ObservableMultiMap<K, V>> forType(Class<K> keyType,
			Class<V> valueType) {
			return (ModelInstanceType.DoubleTyped<ObservableMultiMap<?, ?>, K, V, ObservableMultiMap<K, V>>) super.forType(keyType,
				valueType);
		}

		@Override
		public <K, V> ModelInstanceType.DoubleTyped<ObservableMultiMap<?, ?>, ? extends K, ? extends V, ObservableMultiMap<? extends K, ? extends V>> below(
			TypeToken<K> keyType, TypeToken<V> valueType) {
			return (ModelInstanceType.DoubleTyped<ObservableMultiMap<?, ?>, ? extends K, ? extends V, ObservableMultiMap<? extends K, ? extends V>>) super.below(
				keyType, valueType);
		}

		@Override
		public <K, V> ModelInstanceType.DoubleTyped<ObservableMultiMap<?, ?>, ? extends K, ? extends V, ObservableMultiMap<? extends K, ? extends V>> below(
			Class<K> keyType, Class<V> valueType) {
			return (ModelInstanceType.DoubleTyped<ObservableMultiMap<?, ?>, ? extends K, ? extends V, ObservableMultiMap<? extends K, ? extends V>>) super.below(
				keyType, valueType);
		}

		@Override
		public <K, V> ModelInstanceType.DoubleTyped<ObservableMultiMap<?, ?>, ? super K, ? super V, ObservableMultiMap<? super K, ? super V>> above(
			TypeToken<K> keyType, TypeToken<V> valueType) {
			return (ModelInstanceType.DoubleTyped<ObservableMultiMap<?, ?>, ? super K, ? super V, ObservableMultiMap<? super K, ? super V>>) super.above(
				keyType, valueType);
		}

		@Override
		public <K, V> ModelInstanceType.DoubleTyped<ObservableMultiMap<?, ?>, ? super K, ? super V, ObservableMultiMap<? super K, ? super V>> above(
			Class<K> keyType, Class<V> valueType) {
			return (ModelInstanceType.DoubleTyped<ObservableMultiMap<?, ?>, ? super K, ? super V, ObservableMultiMap<? super K, ? super V>>) super.above(
				keyType, valueType);
		}

		@Override
		protected Function<ObservableMultiMap<?, ?>, ObservableMultiMap<?, ?>> convertType(
			ModelInstanceType<ObservableMultiMap<?, ?>, ?> target, Function<Object, Object>[] casts, Function<Object, Object>[] reverses) {
			if (casts[0] != null && reverses[0] == null)
				return null; // Need reverse for key mapping
			return src -> {
				ObservableMultiMap.MultiMapFlow<Object, Object> flow = ((ObservableMultiMap<Object, Object>) src).flow();
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
		protected void setupConversions(ConversionBuilder<ObservableMultiMap<?, ?>> builder) {
			builder.convertibleTo(Event, new ModelConverter<ObservableMultiMap<?, ?>, Observable<?>>() {
				@Override
				public ModelInstanceConverter<ObservableMultiMap<?, ?>, Observable<?>> convert(
					ModelInstanceType<ObservableMultiMap<?, ?>, ?> source, ModelInstanceType<Observable<?>, ?> dest)
						throws IllegalArgumentException {
					if (dest.getType(0) == TypeTokens.get().VOID || TypeTokens.getRawType(dest.getType(0)) == void.class) {
						return ModelType.converter(LambdaUtils.printableFn(src -> src.changes().map(__ -> null), "changes", null), dest);
					} else {
						TypeToken<?> omeType = TypeTokens.get().keyFor(ObservableMultiMapEvent.class).parameterized(source.getType(0),
							source.getType(1));
						if (dest.getType(0).isAssignableFrom(omeType))
							return ModelType.converter(
								LambdaUtils.printableFn(LambdaUtils.printableFn(src -> src.changes(), "changes", null), "changes", null),
								dest.getModelType().forTypes(omeType));
						else
							throw new IllegalArgumentException("Cannot convert from " + source + " to " + dest);
					}
				}
			});
		}
	}

	/** See {@link ModelTypes#SortedMultiMap} */
	public static class SortedMultiMapModelType extends ModelType.DoubleTyped<ObservableSortedMultiMap<?, ?>> {
		SortedMultiMapModelType() {
			super("SortedMultiMap", (Class<ObservableSortedMultiMap<?, ?>>) (Class<?>) ObservableSortedMultiMap.class);
		}

		@Override
		public ModelInstanceType<ObservableSortedMultiMap<?, ?>, ObservableSortedMultiMap<?, ?>> any() {
			return (ModelInstanceType<ObservableSortedMultiMap<?, ?>, ObservableSortedMultiMap<?, ?>>) super.any();
		}

		@Override
		public <K, V> ModelInstanceType.DoubleTyped<ObservableSortedMultiMap<?, ?>, K, V, ObservableSortedMultiMap<K, V>> forType(
			TypeToken<K> keyType, TypeToken<V> valueType) {
			return (ModelInstanceType.DoubleTyped<ObservableSortedMultiMap<?, ?>, K, V, ObservableSortedMultiMap<K, V>>) super.forType(
				keyType, valueType);
		}

		@Override
		public <K, V> ModelInstanceType.DoubleTyped<ObservableSortedMultiMap<?, ?>, K, V, ObservableSortedMultiMap<K, V>> forType(
			Class<K> keyType, Class<V> valueType) {
			return (ModelInstanceType.DoubleTyped<ObservableSortedMultiMap<?, ?>, K, V, ObservableSortedMultiMap<K, V>>) super.forType(
				keyType, valueType);
		}

		@Override
		public <K, V> ModelInstanceType.DoubleTyped<ObservableSortedMultiMap<?, ?>, ? extends K, ? extends V, ObservableSortedMultiMap<? extends K, ? extends V>> below(
			TypeToken<K> keyType, TypeToken<V> valueType) {
			return (ModelInstanceType.DoubleTyped<ObservableSortedMultiMap<?, ?>, ? extends K, ? extends V, ObservableSortedMultiMap<? extends K, ? extends V>>) super.below(
				keyType, valueType);
		}

		@Override
		public <K, V> ModelInstanceType.DoubleTyped<ObservableSortedMultiMap<?, ?>, ? extends K, ? extends V, ObservableSortedMultiMap<? extends K, ? extends V>> below(
			Class<K> keyType, Class<V> valueType) {
			return (ModelInstanceType.DoubleTyped<ObservableSortedMultiMap<?, ?>, ? extends K, ? extends V, ObservableSortedMultiMap<? extends K, ? extends V>>) super.below(
				keyType, valueType);
		}

		@Override
		public <K, V> ModelInstanceType.DoubleTyped<ObservableSortedMultiMap<?, ?>, ? super K, ? super V, ObservableSortedMultiMap<? super K, ? super V>> above(
			TypeToken<K> keyType, TypeToken<V> valueType) {
			return (ModelInstanceType.DoubleTyped<ObservableSortedMultiMap<?, ?>, ? super K, ? super V, ObservableSortedMultiMap<? super K, ? super V>>) super.above(
				keyType, valueType);
		}

		@Override
		public <K, V> ModelInstanceType.DoubleTyped<ObservableSortedMultiMap<?, ?>, ? super K, ? super V, ObservableSortedMultiMap<? super K, ? super V>> above(
			Class<K> keyType, Class<V> valueType) {
			return (ModelInstanceType.DoubleTyped<ObservableSortedMultiMap<?, ?>, ? super K, ? super V, ObservableSortedMultiMap<? super K, ? super V>>) super.above(
				keyType, valueType);
		}

		@Override
		protected Function<ObservableSortedMultiMap<?, ?>, ObservableSortedMultiMap<?, ?>> convertType(
			ModelInstanceType<ObservableSortedMultiMap<?, ?>, ?> target, Function<Object, Object>[] casts,
			Function<Object, Object>[] reverses) {
			if (casts[0] != null && reverses[0] == null)
				return null; // Need reverse for key mapping
			return src -> {
				ObservableSortedMultiMap.SortedMultiMapFlow<Object, Object> flow = ((ObservableSortedMultiMap<Object, Object>) src).flow();
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
		protected void setupConversions(ConversionBuilder<ObservableSortedMultiMap<?, ?>> builder) {
			builder
			.convertibleTo(MultiMap,
				(source, dest) -> ModelType.converter(LambdaUtils.printableFn(src -> src, "trivial", "trivial"), dest))//
			.convertibleTo(Event, new ModelConverter<ObservableSortedMultiMap<?, ?>, Observable<?>>() {
				@Override
				public ModelInstanceConverter<ObservableSortedMultiMap<?, ?>, Observable<?>> convert(
					ModelInstanceType<ObservableSortedMultiMap<?, ?>, ?> source, ModelInstanceType<Observable<?>, ?> dest)
						throws IllegalArgumentException {
					if (dest.getType(0) == TypeTokens.get().VOID || TypeTokens.getRawType(dest.getType(0)) == void.class) {
						return ModelType.converter(LambdaUtils.printableFn(src -> src.changes().map(__ -> null), "changes", null),
							dest);
					} else {
						TypeToken<?> omeType = TypeTokens.get().keyFor(ObservableMultiMapEvent.class).parameterized(source.getType(0),
							source.getType(1));
						if (dest.getType(0).isAssignableFrom(omeType))
							return ModelType.converter(LambdaUtils.printableFn(src -> src.changes(), "changes", null),
								dest.getModelType().forTypes(omeType));
						else
							throw new IllegalArgumentException("Cannot convert from " + source + " to " + dest);
					}
				}
			});
		}
	}

	static Function<Transformation.TransformationPrecursor<Object, Object, ?>, Transformation<Object, Object>> transform(
		Function<Object, Object> cast) {
		return tx -> tx.cache(false).map(cast);
	}

	static Function<Transformation.ReversibleTransformationPrecursor<Object, Object, ?>, Transformation.ReversibleTransformation<Object, Object>> transformReversible(
		Function<Object, Object> cast, Function<Object, Object> reverse) {
		return tx -> tx.cache(false).map(cast).withReverse(reverse);
	}
}
