package org.observe.expresso;

import java.util.Collection;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.observe.CausableChanging;
import org.observe.Observable;
import org.observe.ObservableAction;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.SettableValue;
import org.observe.Subscription;
import org.observe.Transformation;
import org.observe.assoc.ObservableMap;
import org.observe.assoc.ObservableMultiMap;
import org.observe.assoc.ObservableSortedMap;
import org.observe.assoc.ObservableSortedMultiMap;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollectionEvent;
import org.observe.collect.ObservableSet;
import org.observe.collect.ObservableSortedCollection;
import org.observe.collect.ObservableSortedSet;
import org.observe.config.ObservableValueSet;
import org.observe.util.ObservableCollectionWrapper;
import org.observe.util.TypeTokens;
import org.observe.util.TypeTokens.TypeConverter;
import org.qommons.BiTuple;
import org.qommons.Causable;
import org.qommons.ClassMap;
import org.qommons.ClassMap.TypeMatch;
import org.qommons.Identifiable.AbstractIdentifiable;
import org.qommons.LambdaUtils;
import org.qommons.QommonsUtils;
import org.qommons.Stamped;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.collect.BetterSortedList;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.MultiMap;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.collect.SortedMultiMap;
import org.qommons.io.ErrorReporting;

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
		@Override
		public <MV extends ObservableModelSet> HollowModelValue<ObservableModelSet, MV> createHollowValue(String name,
			ModelInstanceType<ObservableModelSet, MV> type) {
			throw new IllegalStateException("Hollow values not supported for models");
		}

		@Override
		public ModelType<?> getCommonType(ModelType<?> other) {
			if (other == this)
				return this;
			return null;
		}
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

	static final Map<ModelType<?>, BiFunction<ModelType.ModelInstanceType<?, ?>, SettableValue<?>, Object>> MODEL_VALUE_FLATTENERS = QommonsUtils//
		.<ModelType<?>, BiFunction<ModelType.ModelInstanceType<?, ?>, SettableValue<?>, Object>> buildMap(null)//
		.with(Event, (type, value) -> ObservableValue.flattenObservableValue((SettableValue<Observable<?>>) value))//
		.with(Action, (type, value) -> {
			return ObservableAction.of(cause -> {
				ObservableAction a = (ObservableAction) value.get();
				if (a != null)
					a.act(cause);
			});
		})//
		.with(Value, (type, value) -> {
			Object defaultV = TypeTokens.get().getDefaultValue(type.getType(0));
			return SettableValue.flattenAsSettable((ObservableValue<ObservableValue<Object>>) value, () -> defaultV);
		})//
		.with(Collection, (type, value) -> ObservableCollection.flattenValue((ObservableValue<ObservableCollection<Object>>) value))//
		.with(Set, (type, value) -> ObservableSet.flattenValue((ObservableValue<ObservableSet<Object>>) value))//
		.getUnmodifiable();

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

	static class NamedUniqueIdentity {
		private final String theName;

		NamedUniqueIdentity(String name) {
			theName = name;
		}

		@Override
		public String toString() {
			return theName;
		}
	}

	/** See {@link ModelTypes#Event} */
	public static class EventModelType extends ModelType.SingleTyped<Observable<?>> {
		/** Event&lt;Void> type */
		public final ModelInstanceType.SingleTyped<Observable<?>, Void, Observable<Void>> VOID = forType(Void.class);

		private EventModelType() {
			super("Event", (Class<Observable<?>>) (Class<?>) Observable.class);
		}

		@Override
		public ModelInstanceType<Observable<?>, Observable<?>> any() {
			return (ModelInstanceType<Observable<?>, Observable<?>>) super.any();
		}

		/**
		 * A form of {@link #any()} that casts the type to a type parameter. This is useful in cases where the value type defines a type
		 * parameter in the calling method.
		 *
		 * @param <V> The type to assign as
		 * @return A ModelInstanceType of this model type with wildcard parameter types, as the given type
		 */
		public <V> ModelInstanceType<Observable<?>, Observable<V>> anyAsV() {
			return (ModelInstanceType<Observable<?>, Observable<V>>) super.any();
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
			TypeConverter<Object, Object, Object, Object>[] casts) {
			return src -> src.map(casts[0]);
		}

		@Override
		public <MV extends Observable<?>> HollowModelValue<Observable<?>, MV> createHollowValue(String name,
			ModelInstanceType<Observable<?>, MV> type) {
			return (HollowModelValue<Observable<?>, MV>) new HollowObservable<>(name);
		}

		class HollowObservable<T> extends AbstractIdentifiable implements Observable<T>, HollowModelValue<Observable<?>, Observable<T>> {
			private final NamedUniqueIdentity theId;
			private final SettableValue<Observable<T>> theContainer;
			private final Observable<T> theFlatObservable;

			public HollowObservable(String name) {
				theId = new NamedUniqueIdentity(name);
				theContainer = SettableValue.<Observable<T>> build().build();
				theFlatObservable = ObservableValue.flattenObservableValue(theContainer);
			}

			@Override
			protected Object createIdentity() {
				return theId;
			}

			@Override
			public CoreId getCoreId() {
				return theFlatObservable.getCoreId();
			}

			@Override
			public ThreadConstraint getThreadConstraint() {
				return theContainer.get() == null ? ThreadConstraint.ANY : theContainer.get().getThreadConstraint();
			}

			@Override
			public boolean isEventing() {
				return theFlatObservable.isEventing();
			}

			@Override
			public void satisfy(Observable<T> realValue) {
				theContainer.set(realValue, null);
			}

			@Override
			public boolean isSatisfied() {
				return theContainer.get() != null;
			}

			@Override
			public Subscription subscribe(Observer<? super T> observer) {
				return theFlatObservable.subscribe(observer);
			}

			@Override
			public boolean isSafe() {
				return false;
			}

			@Override
			public Transaction lock() {
				return theFlatObservable.lock();
			}

			@Override
			public Transaction tryLock() {
				return theFlatObservable.tryLock();
			}
		}

		@Override
		public ModelType<?> getCommonType(ModelType<?> other) {
			if (other == Action || other == Value || other == Collection || other == SortedCollection || other == Set || other == SortedSet
				|| other == Map || other == SortedMap || other == MultiMap || other == SortedMultiMap || other == ValueSet)
				return this;
			return null;
		}
	}

	/** See {@link ModelTypes#Action} */
	public static class ActionModelType extends ModelType.UnTyped<ObservableAction> {
		private ActionModelType() {
			super("Action", ObservableAction.class);
		}

		@Override
		public <MV extends ObservableAction> HollowModelValue<ObservableAction, MV> createHollowValue(String name,
			ModelInstanceType<ObservableAction, MV> type) {
			return (HollowModelValue<ObservableAction, MV>) new HollowAction(name);
		}

		static class HollowAction implements HollowModelValue<ObservableAction, ObservableAction>, ObservableAction {
			private final String theName;
			private HollowModelValue<SettableValue<?>, SettableValue<String>> theEnabled;

			private ObservableAction theSatisfied;

			public HollowAction(String name) {
				theName = name;
			}

			@Override
			public void act(Object cause) throws IllegalStateException {
				if (theSatisfied != null)
					theSatisfied.act(cause);
			}

			@Override
			public ObservableValue<String> isEnabled() {
				if (theSatisfied != null)
					return theSatisfied.isEnabled();
				HollowModelValue<SettableValue<?>, SettableValue<String>> enabled = theEnabled;
				if (enabled != null)
					return (ObservableValue<String>) enabled;
				synchronized (this) {
					if (theSatisfied != null)
						return theSatisfied.isEnabled();
					enabled = theEnabled;
					if (enabled == null)
						theEnabled = enabled = Value.createHollowValue(theName + ".enabled", Value.STRING);
					return (ObservableValue<String>) enabled;
				}
			}

			@Override
			public void satisfy(ObservableAction realValue) throws IllegalStateException {
				if (realValue == null)
					throw new NullPointerException("Cannot satisfy a hollow value (Action) with null");
				theSatisfied = realValue;
				if (theEnabled != null)
					theEnabled.satisfy(SettableValue.asSettable(realValue.isEnabled(), __ -> "Not Settable"));
			}

			@Override
			public boolean isSatisfied() {
				return theSatisfied != null;
			}

			@Override
			public String toString() {
				return theName;
			}
		}

		@Override
		public ModelType<?> getCommonType(ModelType<?> other) {
			if (other == this)
				return this;
			return null;
		}
	}

	/** See {@link ModelTypes#Value} */
	public static class ValueModelType extends ModelType.SingleTyped<SettableValue<?>> {
		/** Value&lt;boolean> type */
		public final ModelInstanceType.SingleTyped<SettableValue<?>, Boolean, SettableValue<Boolean>> BOOLEAN = forType(boolean.class);
		/** Value&lt;char> type */
		public final ModelInstanceType.SingleTyped<SettableValue<?>, Character, SettableValue<Character>> CHAR = forType(char.class);
		/** Value&lt;byte> type */
		public final ModelInstanceType.SingleTyped<SettableValue<?>, Byte, SettableValue<Byte>> BYTE = forType(byte.class);
		/** Value&lt;int> type */
		public final ModelInstanceType.SingleTyped<SettableValue<?>, Integer, SettableValue<Integer>> INT = forType(int.class);
		/** Value&lt;double> type */
		public final ModelInstanceType.SingleTyped<SettableValue<?>, Double, SettableValue<Double>> DOUBLE = forType(double.class);
		/** Value&lt;String> type */
		public final ModelInstanceType.SingleTyped<SettableValue<?>, String, SettableValue<String>> STRING = forType(String.class);

		private ValueModelType() {
			super("Value", (Class<SettableValue<?>>) (Class<?>) SettableValue.class);
		}

		@Override
		public ModelInstanceType<SettableValue<?>, SettableValue<?>> any() {
			return (ModelInstanceType<SettableValue<?>, SettableValue<?>>) super.any();
		}

		/**
		 * A form of {@link #any()} that casts the type to a type parameter. This is useful in cases where the value type defines a type
		 * parameter in the calling method.
		 *
		 * @param <V> The type to assign as
		 * @return A ModelInstanceType of this model type with wildcard parameter types, as the given type
		 */
		public <V> ModelInstanceType<SettableValue<?>, SettableValue<V>> anyAsV() {
			return (ModelInstanceType<SettableValue<?>, SettableValue<V>>) super.any();
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
			TypeConverter<Object, Object, Object, Object>[] casts) {
			return src -> ((SettableValue<Object>) src).transformReversible(transformReversible(casts[0]));
		}

		@Override
		protected void setupConversions(ConversionBuilder<SettableValue<?>> builder) {
			builder.convertibleTo(Event, new ModelConverter.SimpleSingleTyped<SettableValue<?>, Observable<?>>() {
				@Override
				public ModelInstanceConverter<SettableValue<?>, Observable<?>> convert(ModelInstanceType<SettableValue<?>, ?> source,
					ModelInstanceType<Observable<?>, ?> dest, InterpretedExpressoEnv env) throws IllegalArgumentException {
					if (dest.getType(0) == TypeTokens.get().VOID || TypeTokens.getRawType(dest.getType(0)) == void.class) {
						return ModelType.converter(LambdaUtils.printableFn(src -> src.noInitChanges().map(__ -> null), "changes", null),
							dest);
					} else {
						ModelType<?> holderType = ALL_TYPES.get(TypeTokens.getRawType(source.getType(0)), TypeMatch.SUPER_TYPE);
						if (holderType != null) {
							TypeToken<?>[] holderParamTypes = new TypeToken[holderType.getTypeCount()];
							for (int t = 0; t < holderParamTypes.length; t++)
								holderParamTypes[t] = source.getType(0).resolveType(holderType.modelType.getTypeParameters()[t]);
							ModelInstanceType<?, ?> holderInstanceType = holderType.forTypes(holderParamTypes);
							ModelInstanceConverter<Object, Observable<?>> holderConverter = (ModelInstanceConverter<Object, Observable<?>>) holderInstanceType
								.convert(dest, env);
							if (holderConverter != null) {
								ErrorReporting reporting = env.reporting();
								return ModelType.<SettableValue<?>, Observable<?>> converter(LambdaUtils.printableFn(src -> {
									if (src == null)
										return null;
									return Observable.flatten(src.value().map(v -> {
										try {
											return holderConverter.convert(v);
										} catch (ModelInstantiationException e) {
											reporting.error(e.getMessage(), e);
											return null;
										}
									}));
								}, "flat" + holderConverter, null), holderConverter.getType());
							}
						}
						TypeToken<?> oveType = TypeTokens.get().keyFor(ObservableValueEvent.class).parameterized(source.getType(0));
						if (TypeTokens.get().isAssignable(dest.getType(0), oveType))
							return ModelType.converter(LambdaUtils.printableFn(src -> src.noInitChanges(), "changes", null),
								dest.getModelType().forTypes(oveType));
						else
							return SimpleSingleTyped.super.convert(source, dest, env);
					}
				}

				@Override
				public <S, T> Observable<?> convert(SettableValue<?> source, TypeToken<T> targetType,
					TypeConverter<S, ?, ?, ? extends T> converter) {
					if (converter != null)
						return ((SettableValue<S>) source).value().noInit().map(converter);
					else
						return source.value().noInit();
				}
			});
			builder.convertibleFromAny(new ModelConverter<Object, SettableValue<?>>() {
				private boolean isRecursing;

				@Override
				public ModelInstanceConverter<Object, SettableValue<?>> convert(ModelInstanceType<Object, ?> source,
					ModelInstanceType<SettableValue<?>, ?> dest, InterpretedExpressoEnv env) throws IllegalArgumentException {
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
						ModelInstanceConverter<?, ?> valueConverter = source.convert(destModelType, env);
						if (valueConverter == null)
							return null;
						ModelInstanceType<SettableValue<?>, ?> type = Value.forType(//
							TypeTokens.get().keyFor(valueConverter.getType().getModelType().modelType)
							.parameterized(valueConverter.getType().getTypeList()));
						return new ModelInstanceConverter<Object, SettableValue<?>>() {
							@Override
							public SettableValue<?> convert(Object sourceV) throws ModelInstantiationException {
								Object converted = ((ModelInstanceConverter<Object, Object>) valueConverter).convert(sourceV);
								if (sourceV instanceof Stamped && sourceV instanceof CausableChanging) {
									// This section creates a value that updates with the contained model value
									return SettableValue.asSettable(//
										ObservableValue.of(() -> converted, ((Stamped) converted)::getStamp,
											((CausableChanging) converted).simpleChanges()),
										NOT_REVERSIBLE);
								} else {
									return SettableValue.asSettable(ObservableValue.of(converted), NOT_REVERSIBLE);
								}
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
							valueConverter = sourceType.convert(dest, env);
						} finally {
							isRecursing = false;
						}
						if (valueConverter == null)
							return null;
						return new ModelInstanceConverter<Object, SettableValue<?>>() {
							@Override
							public SettableValue<?> convert(Object sourceV) throws ModelInstantiationException {
								SettableValue<?> container;
								if (sourceV instanceof Stamped && sourceV instanceof CausableChanging) {
									// This section creates a value that updates with the contained model value
									container = SettableValue.asSettable(//
										ObservableValue.of(() -> sourceV, ((Stamped) sourceV)::getStamp,
											((CausableChanging) sourceV).simpleChanges()),
										NOT_REVERSIBLE);
								} else {
									return SettableValue.asSettable(ObservableValue.of(sourceV), NOT_REVERSIBLE);
								}
								return valueConverter.convert(container);
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
					ModelInstanceType<Object, ?> dest, InterpretedExpressoEnv env) throws IllegalArgumentException {
					// Let's see if the source value is a model holder
					Class<?> rawSourceType = TypeTokens.getRawType(source.getType(0));
					// The source type has to be convertible observably to the model
					boolean ov = rawSourceType == ObservableValue.class;
					if (ov && ((ModelType<?>) dest.getModelType()) == Value // This is a special case we can handle
						|| dest.getModelType().modelType.isAssignableFrom(rawSourceType)) {
						BiFunction<ModelInstanceType<?, ?>, SettableValue<?>, Object> flattener = MODEL_VALUE_FLATTENERS
							.get(dest.getModelType());
						if (flattener == null)
							return null;
						TypeToken<?>[] sourceParamTypes = new TypeToken[dest.getModelType().getTypeCount()];
						BiTuple<Class<?>, ModelType<?>> convertModel = ALL_TYPES.getEntry(dest.getModelType().modelType,
							TypeMatch.SUPER_TYPE);
						for (int i = 0; i < sourceParamTypes.length; i++)
							sourceParamTypes[i] = source.getType(0).resolveType(convertModel.getValue1().getTypeParameters()[i]);
						ModelInstanceType<Object, ?> sourceModelType = dest.getModelType().forTypes(sourceParamTypes);
						ModelInstanceConverter<Object, ?> valueConverter = sourceModelType.convert(dest, env);
						if (valueConverter == null)
							return null;
						return new ModelInstanceConverter<SettableValue<?>, Object>() {
							@Override
							public Object convert(SettableValue<?> sourceV) throws ModelInstantiationException {
								Object v = flattener.apply(dest, sourceV);
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
					ModelInstanceType<SettableValue<?>, ?> dest, InterpretedExpressoEnv env) throws IllegalArgumentException {
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

		@Override
		public <MV extends SettableValue<?>> HollowModelValue<SettableValue<?>, MV> createHollowValue(String name,
			ModelInstanceType<SettableValue<?>, MV> type) {
			return (HollowModelValue<SettableValue<?>, MV>) new HollowValue<>(name, (TypeToken<Object>) type.getType(0));
		}

		static class HollowValue<T> extends SettableValue.SettableFlattenedObservableValue<T>
		implements HollowModelValue<SettableValue<?>, SettableValue<T>> {
			private final NamedUniqueIdentity theId;

			public HollowValue(String name, TypeToken<T> type) {
				this(name, TypeTokens.get().getDefaultValue(type));
			}

			private HollowValue(String name, T defaultValue) {
				super(SettableValue.<SettableValue<T>> build().build(), () -> defaultValue);
				theId = new NamedUniqueIdentity(name);
			}

			@Override
			protected SettableValue<SettableValue<T>> getWrapped() {
				return (SettableValue<SettableValue<T>>) super.getWrapped();
			}

			@Override
			protected Object createIdentity() {
				return theId;
			}

			@Override
			public void satisfy(SettableValue<T> realValue) throws IllegalStateException {
				if (realValue == getWrapped())
					return;
				else if (realValue == null)
					throw new NullPointerException("Cannot satisfy a hollow value (Value>) with null");
				getWrapped().set(realValue, null);
			}

			@Override
			public boolean isSatisfied() {
				return getWrapped().get() != null;
			}

			@Override
			public String toString() {
				return theId.theName;
			}
		}

		@Override
		public ModelType<?> getCommonType(ModelType<?> other) {
			if (other == Event)
				return other;
			else if (other == this || other == Collection || other == SortedCollection || other == Set || other == SortedSet || other == Map
				|| other == SortedMap || other == MultiMap || other == SortedMultiMap || other == ValueSet)
				return this;
			return null;
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

		/**
		 * A form of {@link #any()} that casts the type to a type parameter. This is useful in cases where the value type defines a type
		 * parameter in the calling method.
		 *
		 * @param <V> The type to assign as
		 * @return A ModelInstanceType of this model type with wildcard parameter types, as the given type
		 */
		public <V> ModelInstanceType<ObservableCollection<?>, ObservableCollection<V>> anyAsV() {
			return (ModelInstanceType<ObservableCollection<?>, ObservableCollection<V>>) super.any();
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
			ModelInstanceType<ObservableCollection<?>, ?> target, TypeConverter<Object, Object, Object, Object>[] casts) {
			return src -> ((ObservableCollection<Object>) src).flow().transform(transformReversible(casts[0])).collectPassive();
		}

		@Override
		protected void setupConversions(ConversionBuilder<ObservableCollection<?>> builder) {
			builder.convertibleTo(Event, new ModelConverter<ObservableCollection<?>, Observable<?>>() {
				@Override
				public ModelInstanceConverter<ObservableCollection<?>, Observable<?>> convert(
					ModelInstanceType<ObservableCollection<?>, ?> source, ModelInstanceType<Observable<?>, ?> dest,
					InterpretedExpressoEnv env) throws IllegalArgumentException {
					if (dest.getType(0) == TypeTokens.get().VOID || TypeTokens.getRawType(dest.getType(0)) == void.class) {
						return ModelType.converter(LambdaUtils.printableFn(src -> src.simpleChanges().map(__ -> null), "changes", null),
							dest);
					} else {
						TypeToken<?> oceType = TypeTokens.get().of(Causable.class);
						if (TypeTokens.get().isAssignable(dest.getType(0), oceType)) {
							return ModelType.converter(LambdaUtils.printableFn(src -> src.simpleChanges(), "changes", null),
								dest.getModelType().forTypes(oceType));
						} else
							throw new IllegalArgumentException("Cannot convert from " + source + " to " + dest);
					}
				}
			});
			// Support constant collections from arrays
			builder.convertibleFrom(ModelTypes.Value, new ModelConverter<SettableValue<?>, ObservableCollection<?>>() {
				@Override
				public ModelInstanceConverter<SettableValue<?>, ObservableCollection<?>> convert(
					ModelInstanceType<SettableValue<?>, ?> source, ModelInstanceType<ObservableCollection<?>, ?> target,
					InterpretedExpressoEnv env) {
					if (source.getType(0).isArray())
						return convertArrayValue(source.getType(0).getComponentType(), target, env);
					Class<?> raw = TypeTokens.getRawType(source.getType(0));
					if (ObservableCollection.class.isAssignableFrom(raw))
						return null; // The Value type takes care of this case
					else if (Collection.class.isAssignableFrom(raw))
						return convertCollectionValue(raw, source.getType(0).resolveType(Collection.class.getTypeParameters()[0]), target,
							env);
					else
						return null;
				}

				private <T> ModelInstanceConverter<SettableValue<?>, ObservableCollection<?>> convertArrayValue(TypeToken<T> componentType,
					ModelInstanceType<ObservableCollection<?>, ?> target, InterpretedExpressoEnv env) {
					ModelInstanceType<ObservableCollection<?>, ObservableCollection<T>> collectionType = forType(componentType);
					ModelInstanceConverter<ObservableCollection<?>, ObservableCollection<?>> collectionConverter = target
						.convert(collectionType, env);
					if (collectionConverter == null)
						return null;
					return ModelType.converter(LambdaUtils.printableFn(arrayValue -> {
						return ObservableCollection
							.flattenValue(arrayValue.map(v -> v == null ? null : ObservableCollection.of((T[]) v)));
					}, "asCollection", null), collectionConverter.getType());
				}

				private <T> ModelInstanceConverter<SettableValue<?>, ObservableCollection<?>> convertCollectionValue(Class<?> raw,
					TypeToken<T> componentType, ModelInstanceType<ObservableCollection<?>, ?> target, InterpretedExpressoEnv env) {
					ModelInstanceType<ObservableCollection<?>, ObservableCollection<T>> collectionType = forType(componentType);
					ModelInstanceConverter<ObservableCollection<?>, ObservableCollection<?>> collectionConverter = target
						.convert(collectionType, env);
					if (collectionConverter == null)
						return null;
					return ModelType.converter(LambdaUtils.printableFn(collectionValue -> {
						return ObservableCollection.flattenSimpleCollectionValue((SettableValue<? extends Collection<T>>) collectionValue);
					}, "asObservableCollection", null), collectionConverter.getType());
				}
			});
		}

		@Override
		public <MV extends ObservableCollection<?>> HollowModelValue<ObservableCollection<?>, MV> createHollowValue(String name,
			ModelInstanceType<ObservableCollection<?>, MV> type) {
			return (HollowModelValue<ObservableCollection<?>, MV>) new HollowCollection<>(name, (TypeToken<Object>) type.getType(0));
		}

		static class HollowCollection<T> extends ObservableCollectionWrapper<T>
		implements HollowModelValue<ObservableCollection<?>, ObservableCollection<T>> {
			private final NamedUniqueIdentity theId;
			private final SettableValue<ObservableCollection<T>> theContainer;

			public HollowCollection(String name, TypeToken<T> type) {
				theId = new NamedUniqueIdentity(name);
				theContainer = SettableValue.<ObservableCollection<T>> build().build();
				init(ObservableCollection.flattenValue(theContainer));
			}

			@Override
			public Object getIdentity() {
				return theId;
			}

			@Override
			public void satisfy(ObservableCollection<T> realValue) {
				theContainer.set(realValue, null);
			}

			@Override
			public boolean isSatisfied() {
				return theContainer.get() != null;
			}
		}

		@Override
		public ModelType<?> getCommonType(ModelType<?> other) {
			if (other == Event || other == Value)
				return other;
			else if (other == this || other == SortedCollection || other == Set || other == SortedSet || other == ValueSet)
				return this;
			return null;
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

		/**
		 * A form of {@link #any()} that casts the type to a type parameter. This is useful in cases where the value type defines a type
		 * parameter in the calling method.
		 *
		 * @param <V> The type to assign as
		 * @return A ModelInstanceType of this model type with wildcard parameter types, as the given type
		 */
		public <V> ModelInstanceType<ObservableSortedCollection<?>, ObservableSortedCollection<V>> anyAsV() {
			return (ModelInstanceType<ObservableSortedCollection<?>, ObservableSortedCollection<V>>) super.any();
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
			ModelInstanceType<ObservableSortedCollection<?>, ?> target, TypeConverter<Object, Object, Object, Object>[] casts) {
			return src -> ((ObservableSortedCollection<Object>) src).flow().transformEquivalent(transformReversible(casts[0]))
				.collectPassive();
		}

		@Override
		protected void setupConversions(ConversionBuilder<ObservableSortedCollection<?>> builder) {
			builder
			.convertibleTo(Collection,
				(source, dest, env) -> ModelType.converter(LambdaUtils.printableFn(src -> src, "trivial", "trivial"),
					Collection.forType(source.getType(0))))//
			.convertibleTo(Event, new ModelConverter<ObservableSortedCollection<?>, Observable<?>>() {
				@Override
				public ModelInstanceConverter<ObservableSortedCollection<?>, Observable<?>> convert(
					ModelInstanceType<ObservableSortedCollection<?>, ?> source, ModelInstanceType<Observable<?>, ?> dest,
					InterpretedExpressoEnv env) throws IllegalArgumentException {
					if (dest.getType(0) == TypeTokens.get().VOID || TypeTokens.getRawType(dest.getType(0)) == void.class) {
						return ModelType.converter(LambdaUtils.printableFn(src -> src.changes().map(__ -> null), "changes", null),
							dest);
					} else {
						TypeToken<?> oceType = TypeTokens.get().keyFor(ObservableCollectionEvent.class)
							.parameterized(source.getType(0));
						if (TypeTokens.get().isAssignable(dest.getType(0), oceType))
							return ModelType.converter(LambdaUtils
								.printableFn(LambdaUtils.printableFn(src -> src.changes(), "changes", null), "changes", null),
								dest.getModelType().forTypes(oceType));
						else
							throw new IllegalArgumentException("Cannot convert from " + source + " to " + dest);
					}
				}
			});
		}

		@Override
		public <MV extends ObservableSortedCollection<?>> HollowModelValue<ObservableSortedCollection<?>, MV> createHollowValue(String name,
			ModelInstanceType<ObservableSortedCollection<?>, MV> type) {
			throw new UnsupportedOperationException(this + ".createHollowValue not implemented");
		}

		@Override
		public ModelType<?> getCommonType(ModelType<?> other) {
			if (other == Event || other == Value || other == Collection)
				return other;
			else if (other == this || other == SortedSet)
				return this;
			else if (other == Set)
				return Collection;
			else if (other == ValueSet)
				return Collection;
			return null;
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

		/**
		 * A form of {@link #any()} that casts the type to a type parameter. This is useful in cases where the value type defines a type
		 * parameter in the calling method.
		 *
		 * @param <V> The type to assign as
		 * @return A ModelInstanceType of this model type with wildcard parameter types, as the given type
		 */
		public <V> ModelInstanceType<ObservableSet<?>, ObservableSet<V>> anyAsV() {
			return (ModelInstanceType<ObservableSet<?>, ObservableSet<V>>) super.any();
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
			TypeConverter<Object, Object, Object, Object>[] casts) {
			return src -> ((ObservableSet<Object>) src).flow().transformEquivalent(transformReversible(casts[0])).collectPassive();
		}

		@Override
		protected void setupConversions(ConversionBuilder<ObservableSet<?>> builder) {
			builder
			.<ObservableCollection<?>> convertibleTo(Collection,
				(source, dest, env) -> ModelType.converter(LambdaUtils.printableFn(src -> src, "trivial", "trivial"),
					Collection.forType(source.getType(0))))//
			.convertibleTo(Event, new ModelConverter<ObservableSet<?>, Observable<?>>() {
				@Override
				public ModelInstanceConverter<ObservableSet<?>, Observable<?>> convert(ModelInstanceType<ObservableSet<?>, ?> source,
					ModelInstanceType<Observable<?>, ?> dest, InterpretedExpressoEnv env) throws IllegalArgumentException {
					if (dest.getType(0) == TypeTokens.get().VOID || TypeTokens.getRawType(dest.getType(0)) == void.class) {
						return ModelType.converter(LambdaUtils.printableFn(src -> src.changes().map(__ -> null), "changes", null),
							dest);
					} else {
						TypeToken<?> oceType = TypeTokens.get().keyFor(ObservableCollectionEvent.class)
							.parameterized(source.getType(0));
						if (TypeTokens.get().isAssignable(dest.getType(0), oceType))
							return ModelType.converter(LambdaUtils.printableFn(src -> src.changes(), "changes", null),
								dest.getModelType().forTypes(oceType));
						else
							throw new IllegalArgumentException("Cannot convert from " + source + " to " + dest);
					}
				}
			});
		}

		@Override
		public <MV extends ObservableSet<?>> HollowModelValue<ObservableSet<?>, MV> createHollowValue(String name,
			ModelInstanceType<ObservableSet<?>, MV> type) {
			return (HollowModelValue<ObservableSet<?>, MV>) new HollowSet<>(name, (TypeToken<Object>) type.getType(0));
		}

		static class HollowSet<T> extends ObservableCollectionWrapper<T>
		implements HollowModelValue<ObservableSet<?>, ObservableSet<T>>, ObservableSet<T> {
			private final NamedUniqueIdentity theId;
			private final SettableValue<ObservableSet<T>> theContainer;

			public HollowSet(String name, TypeToken<T> type) {
				theId = new NamedUniqueIdentity(name);
				theContainer = SettableValue.<ObservableSet<T>> build().build();
				init(ObservableCollection.flattenValue(theContainer));
			}

			@Override
			public Object getIdentity() {
				return theId;
			}

			@Override
			public void satisfy(ObservableSet<T> realValue) {
				theContainer.set(realValue, null);
			}

			@Override
			public boolean isSatisfied() {
				return theContainer.get() != null;
			}

			@Override
			public CollectionElement<T> getOrAdd(T value, ElementId after, ElementId before, boolean first, Runnable preAdd,
				Runnable postAdd) {
				ObservableSet<T> set = theContainer.get();
				if (set == null)
					throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
				return set.getOrAdd(value, after, before, first, preAdd, postAdd);
			}

			@Override
			public boolean isConsistent(ElementId element) {
				ObservableSet<T> set = theContainer.get();
				if (set == null)
					throw new NoSuchElementException();
				return set.isConsistent(element);
			}

			@Override
			public boolean checkConsistency() {
				ObservableSet<T> set = theContainer.get();
				if (set == null)
					return false;
				return set.checkConsistency();
			}

			@Override
			public <X> boolean repair(ElementId element, RepairListener<T, X> listener) {
				ObservableSet<T> set = theContainer.get();
				if (set == null)
					throw new NoSuchElementException();
				return set.repair(element, listener);
			}

			@Override
			public <X> boolean repair(RepairListener<T, X> listener) {
				ObservableSet<T> set = theContainer.get();
				if (set == null)
					return false;
				return set.repair(listener);
			}
		}

		@Override
		public ModelType<?> getCommonType(ModelType<?> other) {
			if (other == Event || other == Value || other == Collection)
				return other;
			else if (other == this || other == SortedSet)
				return this;
			else if (other == SortedCollection)
				return Collection;
			else if (other == ValueSet)
				return Collection;
			return null;
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

		/**
		 * A form of {@link #any()} that casts the type to a type parameter. This is useful in cases where the value type defines a type
		 * parameter in the calling method.
		 *
		 * @param <V> The type to assign as
		 * @return A ModelInstanceType of this model type with wildcard parameter types, as the given type
		 */
		public <V> ModelInstanceType<ObservableSortedSet<?>, ObservableSortedSet<V>> anyAsV() {
			return (ModelInstanceType<ObservableSortedSet<?>, ObservableSortedSet<V>>) super.any();
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
			TypeConverter<Object, Object, Object, Object>[] casts) {
			return src -> ((ObservableSortedSet<Object>) src).flow().transformEquivalent(transformReversible(casts[0])).collectPassive();
		}

		@Override
		protected void setupConversions(ConversionBuilder<ObservableSortedSet<?>> builder) {
			builder
			.convertibleTo(Collection,
				(source, dest, env) -> ModelType.converter(LambdaUtils.printableFn(src -> src, "trivial", "trivial"),
					Collection.forType(source.getType(0))))//
			.convertibleTo(SortedCollection,
				(source, dest, env) -> ModelType.converter(LambdaUtils.printableFn(src -> src, "trivial", "trivial"),
					SortedCollection.forType(source.getType(0))))//
			.convertibleTo(Set,
				(source, dest, env) -> ModelType.converter(LambdaUtils.printableFn(src -> src, "trivial", "trivial"),
					Set.forType(source.getType(0))))//
			.convertibleTo(Event, new ModelConverter<ObservableSortedSet<?>, Observable<?>>() {
				@Override
				public ModelInstanceConverter<ObservableSortedSet<?>, Observable<?>> convert(
					ModelInstanceType<ObservableSortedSet<?>, ?> source, ModelInstanceType<Observable<?>, ?> dest,
					InterpretedExpressoEnv env) throws IllegalArgumentException {
					if (dest.getType(0) == TypeTokens.get().VOID || TypeTokens.getRawType(dest.getType(0)) == void.class) {
						return ModelType.converter(LambdaUtils.printableFn(src -> src.changes().map(__ -> null), "changes", null),
							dest);
					} else {
						TypeToken<?> oceType = TypeTokens.get().keyFor(ObservableCollectionEvent.class)
							.parameterized(source.getType(0));
						if (TypeTokens.get().isAssignable(dest.getType(0), oceType))
							return ModelType.converter(LambdaUtils.printableFn(src -> src.changes(), "changes", null),
								dest.getModelType().forTypes(oceType));
						else
							throw new IllegalArgumentException("Cannot convert from " + source + " to " + dest);
					}
				}
			});
		}

		@Override
		public <MV extends ObservableSortedSet<?>> HollowModelValue<ObservableSortedSet<?>, MV> createHollowValue(String name,
			ModelInstanceType<ObservableSortedSet<?>, MV> type) {
			throw new UnsupportedOperationException(this + ".createHollowValue not implemented");
		}

		@Override
		public ModelType<?> getCommonType(ModelType<?> other) {
			if (other == Event || other == Value || other == Collection || other == Set || other == SortedCollection)
				return other;
			else if (other == this)
				return this;
			else if (other == ValueSet)
				return Collection;
			return null;
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
			TypeConverter<Object, Object, Object, Object>[] casts) {
			return null;
		}

		@Override
		protected void setupConversions(ConversionBuilder<ObservableValueSet<?>> builder) {
			builder//
			.convertibleTo(Collection,
				(source, dest, env) -> ModelType.converter(LambdaUtils.printableFn(src -> src.getValues(), "values", null), // dest))//
					Collection.forType(source.getType(0))))//
			.convertibleTo(Event, new ModelConverter<ObservableValueSet<?>, Observable<?>>() {
				@Override
				public ModelInstanceConverter<ObservableValueSet<?>, Observable<?>> convert(
					ModelInstanceType<ObservableValueSet<?>, ?> source, ModelInstanceType<Observable<?>, ?> dest,
					InterpretedExpressoEnv env) throws IllegalArgumentException {
					if (dest.getType(0) == TypeTokens.get().VOID || TypeTokens.getRawType(dest.getType(0)) == void.class) {
						return ModelType.converter(
							LambdaUtils.printableFn(src -> src.getValues().simpleChanges().map(__ -> null), "changes", null), dest);
					} else {
						TypeToken<?> oceType = TypeTokens.get().of(Causable.class);
						if (TypeTokens.get().isAssignable(dest.getType(0), oceType)) {
							return ModelType.converter(LambdaUtils.printableFn(src -> src.getValues().simpleChanges(), "changes", null),
								dest.getModelType().forTypes(oceType));
						} else
							throw new IllegalArgumentException("Cannot convert from " + source + " to " + dest);
					}
				}
			});
		}

		@Override
		public <MV extends ObservableValueSet<?>> HollowModelValue<ObservableValueSet<?>, MV> createHollowValue(String name,
			ModelInstanceType<ObservableValueSet<?>, MV> type) {
			throw new UnsupportedOperationException(this + ".createHollowValue not implemented");
		}

		@Override
		public ModelType<?> getCommonType(ModelType<?> other) {
			if (other == Event || other == Value || other == Collection)
				return other;
			else if (other == SortedCollection || other == Set || other == SortedSet)
				return Collection;
			else if (other == this)
				return this;
			return null;
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
			TypeConverter<Object, Object, Object, Object>[] casts) {
			return null; // ObservableMap doesn't have flow at the moment at least
		}

		@Override
		protected void setupConversions(ConversionBuilder<ObservableMap<?, ?>> builder) {
			builder.convertibleTo(Event, new ModelConverter<ObservableMap<?, ?>, Observable<?>>() {
				@Override
				public ModelInstanceConverter<ObservableMap<?, ?>, Observable<?>> convert(ModelInstanceType<ObservableMap<?, ?>, ?> source,
					ModelInstanceType<Observable<?>, ?> dest, InterpretedExpressoEnv env) throws IllegalArgumentException {
					if (dest.getType(0) == TypeTokens.get().VOID || TypeTokens.getRawType(dest.getType(0)) == void.class) {
						return ModelType.converter(LambdaUtils.printableFn(src -> src.simpleChanges().map(__ -> null), "changes", null),
							dest);
					} else {
						TypeToken<?> omeType = TypeTokens.get().of(Causable.class);
						if (TypeTokens.get().isAssignable(dest.getType(0), omeType))
							return ModelType.converter(LambdaUtils.printableFn(src -> src.simpleChanges(), "changes", null),
								dest.getModelType().forTypes(omeType));
						else
							throw new IllegalArgumentException("Cannot convert from " + source + " to " + dest);
					}
				}
			});
		}

		@Override
		public <MV extends ObservableMap<?, ?>> HollowModelValue<ObservableMap<?, ?>, MV> createHollowValue(String name,
			ModelInstanceType<ObservableMap<?, ?>, MV> type) {
			throw new UnsupportedOperationException(this + ".createHollowValue not implemented");
		}

		@Override
		public ModelType<?> getCommonType(ModelType<?> other) {
			if (other == Event || other == Value)
				return other;
			else if (other == this || other == SortedMap)
				return this;
			else if (other == MultiMap || other == SortedMultiMap)
				return MultiMap;
			return null;
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
			ModelInstanceType<ObservableSortedMap<?, ?>, ?> target, TypeConverter<Object, Object, Object, Object>[] casts) {
			return null; // ObservableMap doesn't have flow at the moment at least
		}

		@Override
		protected void setupConversions(ConversionBuilder<ObservableSortedMap<?, ?>> builder) {
			builder
			.convertibleTo(Map,
				(source, dest, env) -> ModelType.converter(LambdaUtils.printableFn(src -> src, "trivial", "trivial"),
					Map.forType(source.getType(0), source.getType(1))))//
			.convertibleTo(Event, new ModelConverter<ObservableSortedMap<?, ?>, Observable<?>>() {
				@Override
				public ModelInstanceConverter<ObservableSortedMap<?, ?>, Observable<?>> convert(
					ModelInstanceType<ObservableSortedMap<?, ?>, ?> source, ModelInstanceType<Observable<?>, ?> dest,
					InterpretedExpressoEnv env) throws IllegalArgumentException {
					if (dest.getType(0) == TypeTokens.get().VOID || TypeTokens.getRawType(dest.getType(0)) == void.class) {
						return ModelType.converter(LambdaUtils.printableFn(src -> src.simpleChanges().map(__ -> null), "changes", null),
							dest);
					} else {
						TypeToken<?> omeType = TypeTokens.get().of(Causable.class);
						if (TypeTokens.get().isAssignable(dest.getType(0), omeType))
							return ModelType.converter(LambdaUtils.printableFn(src -> src.simpleChanges(), "changes", null),
								dest.getModelType().forTypes(omeType));
						else
							throw new IllegalArgumentException("Cannot convert from " + source + " to " + dest);
					}
				}
			});
		}

		@Override
		public <MV extends ObservableSortedMap<?, ?>> HollowModelValue<ObservableSortedMap<?, ?>, MV> createHollowValue(String name,
			ModelInstanceType<ObservableSortedMap<?, ?>, MV> type) {
			throw new UnsupportedOperationException(this + ".createHollowValue not implemented");
		}

		@Override
		public ModelType<?> getCommonType(ModelType<?> other) {
			if (other == Event || other == Value || other == Map || other == MultiMap || other == SortedMultiMap)
				return other;
			else if (other == this)
				return this;
			return null;
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
			ModelInstanceType<ObservableMultiMap<?, ?>, ?> target, TypeConverter<Object, Object, Object, Object>[] casts) {
			return src -> {
				ObservableMultiMap.MultiMapFlow<Object, Object> flow = ((ObservableMultiMap<Object, Object>) src).flow();
				if (casts[0] != null) {
					flow = flow.withKeys(keyFlow -> keyFlow.transformEquivalent(transformReversible(casts[0])));
				}
				if (casts[1] != null) {
					flow = flow.withValues(valueFlow -> valueFlow.transform(transformReversible(casts[1])));
				}
				return flow.gatherPassive();
			};
		}

		@Override
		protected void setupConversions(ConversionBuilder<ObservableMultiMap<?, ?>> builder) {
			builder.convertibleTo(Event, new ModelConverter<ObservableMultiMap<?, ?>, Observable<?>>() {
				@Override
				public ModelInstanceConverter<ObservableMultiMap<?, ?>, Observable<?>> convert(
					ModelInstanceType<ObservableMultiMap<?, ?>, ?> source, ModelInstanceType<Observable<?>, ?> dest,
					InterpretedExpressoEnv env) throws IllegalArgumentException {
					if (dest.getType(0) == TypeTokens.get().VOID || TypeTokens.getRawType(dest.getType(0)) == void.class) {
						return ModelType.converter(LambdaUtils.printableFn(src -> src.simpleChanges().map(__ -> null), "changes", null),
							dest);
					} else {
						TypeToken<?> omeType = TypeTokens.get().of(Causable.class);
						if (TypeTokens.get().isAssignable(dest.getType(0), omeType))
							return ModelType.converter(
								LambdaUtils.printableFn(LambdaUtils.printableFn(src -> src.simpleChanges(), "changes", null), "changes",
									null),
								dest.getModelType().forTypes(omeType));
						else
							throw new IllegalArgumentException("Cannot convert from " + source + " to " + dest);
					}
				}
			});
		}

		@Override
		public <MV extends ObservableMultiMap<?, ?>> HollowModelValue<ObservableMultiMap<?, ?>, MV> createHollowValue(String name,
			ModelInstanceType<ObservableMultiMap<?, ?>, MV> type) {
			throw new UnsupportedOperationException(this + ".createHollowValue not implemented");
		}

		@Override
		public ModelType<?> getCommonType(ModelType<?> other) {
			if (other == Event || other == Value)
				return other;
			else if (other == this || other == SortedMultiMap || other == Map || other == SortedMap)
				return this;
			return null;
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
			ModelInstanceType<ObservableSortedMultiMap<?, ?>, ?> target, TypeConverter<Object, Object, Object, Object>[] casts) {
			return src -> {
				ObservableSortedMultiMap.SortedMultiMapFlow<Object, Object> flow = ((ObservableSortedMultiMap<Object, Object>) src).flow();
				if (casts[0] != null) {
					flow = flow.withStillSortedKeys(keyFlow -> keyFlow.transformEquivalent(transformReversible(casts[0])));
				}
				if (casts[1] != null) {
					flow = flow.withValues(valueFlow -> valueFlow.transform(transformReversible(casts[1])));
				}
				return flow.gatherPassive();
			};
		}

		@Override
		protected void setupConversions(ConversionBuilder<ObservableSortedMultiMap<?, ?>> builder) {
			builder
			.convertibleTo(MultiMap,
				(source, dest, env) -> ModelType.converter(LambdaUtils.printableFn(src -> src, "trivial", "trivial"),
					MultiMap.forType(source.getType(0), source.getType(1))))//
			.convertibleTo(Event, new ModelConverter<ObservableSortedMultiMap<?, ?>, Observable<?>>() {
				@Override
				public ModelInstanceConverter<ObservableSortedMultiMap<?, ?>, Observable<?>> convert(
					ModelInstanceType<ObservableSortedMultiMap<?, ?>, ?> source, ModelInstanceType<Observable<?>, ?> dest,
					InterpretedExpressoEnv env) throws IllegalArgumentException {
					if (dest.getType(0) == TypeTokens.get().VOID || TypeTokens.getRawType(dest.getType(0)) == void.class) {
						return ModelType.converter(LambdaUtils.printableFn(src -> src.simpleChanges().map(__ -> null), "changes", null),
							dest);
					} else {
						TypeToken<?> omeType = TypeTokens.get().of(Causable.class);
						if (TypeTokens.get().isAssignable(dest.getType(0), omeType))
							return ModelType.converter(LambdaUtils
								.printableFn(LambdaUtils.printableFn(src -> src.simpleChanges(), "changes", null), "changes", null),
								dest.getModelType().forTypes(omeType));
						else
							throw new IllegalArgumentException("Cannot convert from " + source + " to " + dest);
					}
				}
			});
		}

		@Override
		public <MV extends ObservableSortedMultiMap<?, ?>> HollowModelValue<ObservableSortedMultiMap<?, ?>, MV> createHollowValue(
			String name, ModelInstanceType<ObservableSortedMultiMap<?, ?>, MV> type) {
			throw new UnsupportedOperationException(this + ".createHollowValue not implemented");
		}

		@Override
		public ModelType<?> getCommonType(ModelType<?> other) {
			if (other == Event || other == Value || other == MultiMap)
				return other;
			else if (other == this || other == Map || other == SortedMap)
				return this;
			return null;
		}
	}

	static Function<Transformation.ReversibleTransformationPrecursor<Object, Object, ?>, Transformation.ReversibleTransformation<Object, Object>> transformReversible(
		TypeConverter<Object, Object, Object, Object> cast) {
		TypeConverter<Object, Object, Object, Object> reverse = cast.reverse();
		return tx -> tx.cache(false).map(cast).replaceSource(reverse, rev -> {
			if (reverse.getApplicability() == null)
				return rev;
			else
				return rev.rejectWith(reverse.getApplicability());
		});
	}
}
