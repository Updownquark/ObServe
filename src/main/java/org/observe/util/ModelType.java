package org.observe.util;

import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import org.observe.util.ObservableModelSet.ModelSetInstance;
import org.observe.util.ObservableModelSet.ValueContainer;
import org.observe.util.TypeTokens.TypeConverter;
import org.qommons.Named;
import org.qommons.config.QonfigInterpreter.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

/**
 * Represents a type of value stored in an {@link ObservableModelSet}
 *
 * @param <M> The java type of value represented by this type
 */
public abstract class ModelType<M> implements Named {
	public interface ModelConverter<M1, M2> {
		ModelInstanceConverter<M1, M2> convert(ModelInstanceType<M1, ?> source, ModelInstanceType<M2, ?> dest)
			throws IllegalArgumentException;

		public interface SimpleUnTyped<M1, M2> extends ModelConverter<M1, M2> {
			@Override
			default ModelInstanceConverter<M1, M2> convert(ModelInstanceType<M1, ?> source, ModelInstanceType<M2, ?> dest)
				throws IllegalArgumentException {
				return new ModelInstanceConverter<M1, M2>() {
					private ModelInstanceType<M2, ?> theType;

					@Override
					public M2 convert(M1 src) {
						return convert(src);
					}

					@Override
					public ModelInstanceType<M2, ?> getType() {
						if (theType == null)
							theType = SimpleUnTyped.this.getType(source, dest);
						return theType;
					}
				};
			}

			M2 convert(M1 source);

			ModelInstanceType<M2, ?> getType(ModelInstanceType<M1, ?> source, ModelInstanceType<M2, ?> dest);
		}

		public interface SimpleSingleTyped<M1, M2> extends ModelConverter<M1, M2> {
			@Override
			default ModelInstanceConverter<M1, M2> convert(ModelInstanceType<M1, ?> source, ModelInstanceType<M2, ?> dest)
				throws IllegalArgumentException {
				TypeConverter<Object, Object> cast;
				TypeConverter<Object, Object> reverse;
				TypeToken<?> type;
				if (source.getType(0).equals(dest.getType(0)) || (dest.getType(0).isAssignableFrom(source.getType(0))
					&& (dest.getType(0).getType() instanceof WildcardType || source.getType(0).isAssignableFrom(dest.getType(0))))) {
					type = source.getType(0);
					cast = reverse = null;
				} else {
					cast = TypeTokens.get().getCast((TypeToken<Object>) source.getType(0), (TypeToken<Object>) dest.getType(0), true);
					if (cast == null)
						throw new IllegalArgumentException("Cannot convert " + source + " to " + dest);
					type = cast.getConvertedType();
					reverse = TypeTokens.get().getCast((TypeToken<Object>) dest.getType(0), (TypeToken<Object>) source.getType(0), true);
				}
				return new ModelInstanceConverter<M1, M2>() {
					@Override
					public M2 convert(M1 src) {
						return SimpleSingleTyped.this.convert(src, (TypeToken<Object>) dest.getType(0), cast, reverse);
					}

					@Override
					public ModelInstanceType<M2, ?> getType() {
						return dest.getModelType().forTypes(type);
					}
				};
			}

			<S, T> M2 convert(M1 source, TypeToken<T> targetType, Function<S, T> cast, Function<T, S> reverse);
		}

		public interface SimpleDoubleTyped<M1, M2> extends ModelConverter<M1, M2> {
			@Override
			default ModelInstanceConverter<M1, M2> convert(ModelInstanceType<M1, ?> source, ModelInstanceType<M2, ?> dest)
				throws IllegalArgumentException {
				TypeToken<?> keyType;
				TypeConverter<Object, Object> keyCast;
				TypeConverter<Object, Object> keyReverse;
				if (source.getType(0).equals(dest.getType(0)) || (dest.getType(0).isAssignableFrom(source.getType(0))
					&& (dest.getType(0).getType() instanceof WildcardType || source.getType(0).isAssignableFrom(dest.getType(0))))) {
					keyType = source.getType(0);
					keyCast = keyReverse = null;
				} else {
					keyCast = TypeTokens.get().getCast((TypeToken<Object>) source.getType(0), (TypeToken<Object>) dest.getType(0), true);
					if (keyCast == null)
						throw new IllegalArgumentException("Cannot convert " + source + " to " + dest);
					keyType = keyCast.getConvertedType();
					keyReverse = TypeTokens.get().getCast((TypeToken<Object>) dest.getType(0), (TypeToken<Object>) source.getType(0), true);
				}
				TypeToken<?> valueType;
				TypeConverter<Object, Object> valueCast;
				TypeConverter<Object, Object> valueReverse;
				if (source.getType(1).equals(dest.getType(1)) || (dest.getType(1).isAssignableFrom(source.getType(1))
					&& (dest.getType(1).getType() instanceof WildcardType || source.getType(1).isAssignableFrom(dest.getType(1))))) {
					valueType = source.getType(1);
					valueCast = valueReverse = null;
				} else {
					valueCast = TypeTokens.get().getCast((TypeToken<Object>) source.getType(1), (TypeToken<Object>) dest.getType(1), true);
					if (valueCast == null)
						throw new IllegalArgumentException("Cannot convert " + source + " to " + dest);
					valueType = valueCast.getConvertedType();
					valueReverse = TypeTokens.get().getCast((TypeToken<Object>) dest.getType(1), (TypeToken<Object>) source.getType(1),
						true);
				}
				return new ModelInstanceConverter<M1, M2>() {
					@Override
					public M2 convert(M1 src) {
						return SimpleDoubleTyped.this.convert(src, (TypeToken<Object>) dest.getType(0), (TypeToken<Object>) dest.getType(1), //
							keyCast, keyReverse, valueCast, valueReverse);
					}

					@Override
					public ModelInstanceType<M2, ?> getType() {
						return dest.getModelType().forTypes(keyType, valueType);
					}
				};
			}

			<KS, KT, VS, VT> M2 convert(M1 source, TypeToken<KT> targetKeyType, TypeToken<VT> targetValueType, //
				Function<KS, KT> keyCast, Function<KT, KS> keyReverse, Function<VS, VT> valueCast, Function<VT, VS> valueReverse);
		}
	}

	public interface ModelInstanceConverter<M1, M2> {
		M2 convert(M1 source);

		ModelInstanceType<M2, ?> getType();

		default <M3> ModelInstanceConverter<M1, M3> and(ModelInstanceConverter<M2, M3> next) {
			ModelInstanceConverter<M1, M2> self = this;
			return new ModelInstanceConverter<M1, M3>() {
				@Override
				public M3 convert(M1 source) {
					M2 stage1 = self.convert(source);
					return next.convert(stage1);
				}

				@Override
				public ModelInstanceType<M3, ?> getType() {
					return next.getType();
				}
			};
		}
	}

	public static <M> ModelInstanceConverter<M, M> nullConverter(ModelInstanceType<M, ?> type) {
		if (type == null)
			throw new NullPointerException();
		return new ModelInstanceConverter<M, M>() {
			@Override
			public M convert(M source) {
				return source;
			}

			@Override
			public ModelInstanceType<M, ?> getType() {
				return type;
			}

			@Override
			public String toString() {
				return "no-op";
			}
		};
	}

	public static <M1, M2> ModelInstanceConverter<M1, M2> converter(Function<M1, M2> converter, ModelInstanceType<M2, ?> type) {
		if (converter == null || type == null)
			throw new NullPointerException();
		return new ModelInstanceConverter<M1, M2>() {
			@Override
			public M2 convert(M1 source) {
				return converter.apply(source);
			}

			@Override
			public ModelInstanceType<M2, ?> getType() {
				return type;
			}
		};
	}

	public static <M1, M2> ModelInstanceConverter<M1, M2> converter(Function<M1, M2> converter,
		Supplier<ModelInstanceType<M2, ?>> typeConverter) {
		if (converter == null || typeConverter == null)
			throw new NullPointerException();
		return new ModelInstanceConverter<M1, M2>() {
			private ModelInstanceType<M2, ?> theType;

			@Override
			public M2 convert(M1 source) {
				return converter.apply(source);
			}

			@Override
			public ModelInstanceType<M2, ?> getType() {
				if (theType == null)
					theType = typeConverter.get();
				return theType;
			}
		};
	}

	public static abstract class ModelInstanceType<M, MV extends M> {
		public abstract ModelType<M> getModelType();

		public abstract TypeToken<?> getType(int typeIndex);

		public TypeToken<?>[] getTypeList() {
			TypeToken<?>[] types = new TypeToken[getModelType().getTypeCount()];
			for (int t = 0; t < types.length; t++)
				types[t] = getType(t);
			return types;
		}

		public <M2> ModelInstanceConverter<M, M2> convert(ModelInstanceType<M2, ? extends M2> target) {
			if (target == null) {
				ModelConverter<M, M2> selfConverter = (ModelConverter<M, M2>) SELF_CONVERSION_TARGETS.get(getModelType());
				ModelInstanceConverter<M, M2> selfInstConverter = selfConverter == null ? null : selfConverter.convert(this, target);
				if (selfInstConverter == null)
					return (ModelInstanceConverter<M, M2>) nullConverter(this);
				else
					return selfInstConverter;
			}
			if (target.getModelType() == getModelType()) {
				boolean goodType = true;
				for (int i = 0; goodType && i < getModelType().getTypeCount(); i++)
					goodType = target.getType(i).equals(getType(i));
				if (goodType) {
					ModelConverter<M, M2> selfConverter = (ModelConverter<M, M2>) SELF_CONVERSION_TARGETS.get(getModelType());
					ModelInstanceConverter<M, M2> selfInstConverter = selfConverter == null ? null : selfConverter.convert(this, target);
					if (selfInstConverter == null)
						return (ModelInstanceConverter<M, M2>) nullConverter(target);
					else
						return selfInstConverter;
				}
				TypeToken<?>[] params = new TypeToken[getModelType().getTypeCount()];
				TypeConverter<Object, Object>[] casts = new TypeConverter[params.length];
				TypeConverter<Object, Object>[] reverses = new TypeConverter[casts.length];
				boolean trivial = true, reversible = true;
				for (int i = 0; i < getModelType().getTypeCount(); i++) {
					if (target.getType(i).equals(getType(i))) {
						params[i] = getType(i);
						continue;
					} else if (target.getType(i).isAssignableFrom(getType(i))
						&& (target.getType(i).getType() instanceof WildcardType || getType(i).isAssignableFrom(target.getType(i)))) {
						params[i] = getType(i);
						casts[i] = null;
					} else {
						trivial = false;
						try {
							casts[i] = (TypeConverter<Object, Object>) TypeTokens.get().getCast(target.getType(i), getType(i), true);
							params[i] = casts[i].getConvertedType();
						} catch (IllegalArgumentException e) {
							return null;
						}
					}
					if (reversible && !params[i].isAssignableFrom(target.getType(i))) {
						try {
							reverses[i] = (TypeConverter<Object, Object>) TypeTokens.get().getCast(getType(i), params[i], true);
						} catch (IllegalArgumentException e) {
							reversible = false;
						}
					}
				}
				Function<M, M2> converter;
				if (trivial) {
					converter = m -> (M2) m;
				} else {
					converter = (Function<M, M2>) getModelType().convertType(//
						(ModelInstanceType<M, ? extends M>) target.getModelType().forTypes(params), casts, reversible ? reverses : null);
				}
				if (converter == null)
					return null;
				ModelInstanceType<M2, ?> newType = target.getModelType().forTypes(params);
				ModelInstanceConverter<M, M2> firstConverter = converter(converter, newType);
				ModelConverter<M2, M2> selfConverter = (ModelConverter<M2, M2>) SELF_CONVERSION_TARGETS.get(target.getModelType());
				ModelInstanceConverter<M2, M2> selfInstConverter = selfConverter == null ? null : selfConverter.convert(newType, target);
				if (selfInstConverter != null)
					return firstConverter.and(selfInstConverter);
				else
					return firstConverter;
			}
			ModelConverter<M, M2> modelConverter = (ModelConverter<M, M2>) CONVERSION_TARGETS
				.getOrDefault(getModelType(), Collections.emptyMap())//
				.get(target.getModelType());
			if (modelConverter == null)
				modelConverter = (ModelConverter<M, M2>) FLEX_CONVERSION_TARGETS.get(target.getModelType());
			if (modelConverter == null)
				return null;
			ModelInstanceConverter<M, M2> firstConverter = modelConverter.convert(this, target);
			ModelConverter<M2, M2> selfConverter = (ModelConverter<M2, M2>) SELF_CONVERSION_TARGETS.get(target.getModelType());
			ModelInstanceConverter<M2, M2> selfInstConverter = selfConverter == null ? null
				: selfConverter.convert(firstConverter.getType(), target);
			if (selfInstConverter != null)
				return firstConverter.and(selfInstConverter);
			else
				return firstConverter;
		}

		public <M2, MV2 extends M2> ValueContainer<M2, MV2> as(ValueContainer<M, MV> source, ModelInstanceType<M2, MV2> targetType)
			throws QonfigInterpretationException {
			ModelType.ModelInstanceConverter<M, M2> converter = source.getType().convert(targetType);
			if (converter == null)
				throw new QonfigInterpretationException("Cannot convert " + source + " (" + source.getType() + ") to " + targetType);

			return new ConvertedValue<>(source, (ModelInstanceType<M2, MV2>) converter.getType(), converter);
		}

		@Override
		public int hashCode() {
			int hash = getModelType().hashCode();
			for (int i = 0; i < getModelType().getTypeCount(); i++)
				hash = Integer.rotateLeft(i, 7) ^ getType(i).hashCode();
			return hash;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof ModelInstanceType))
				return false;
			ModelInstanceType<?, ?> other = (ModelInstanceType<?, ?>) obj;
			if (getModelType() != other.getModelType())
				return false;
			for (int i = 0; i < getModelType().getTypeCount(); i++) {
				if (!getType(i).equals(other.getType(i)))
					return false;
			}
			return true;
		}

		@Override
		public String toString() {
			if (getModelType().getTypeCount() == 0)
				return getModelType().modelType.getSimpleName();
			StringBuilder str = new StringBuilder(getModelType().modelType.getSimpleName()).append('<');
			for (int i = 0; i < getModelType().getTypeCount(); i++) {
				if (i > 0)
					str.append(", ");
				str.append(getType(i));
			}
			return str.append('>').toString();
		}

		public static abstract class UnTyped<M> extends ModelInstanceType<M, M> {
			@Override
			public abstract ModelType.UnTyped<M> getModelType();

			@Override
			public TypeToken<?> getType(int typeIndex) {
				throw new IndexOutOfBoundsException(typeIndex + " of 0");
			}

			@Override
			public String toString() {
				return getModelType().modelType.getName();
			}
		}

		public static abstract class SingleTyped<M, V, MV extends M> extends ModelInstanceType<M, MV> {
			private final TypeToken<V> theValueType;

			SingleTyped(TypeToken<V> valueType) {
				theValueType = valueType;
			}

			@Override
			public abstract ModelType.SingleTyped<M> getModelType();

			public TypeToken<V> getValueType() {
				return theValueType;
			}

			@Override
			public TypeToken<?> getType(int typeIndex) {
				if (typeIndex == 0)
					return theValueType;
				throw new IndexOutOfBoundsException(typeIndex + " of 1");
			}

			@Override
			public String toString() {
				return new StringBuilder(getModelType().modelType.getName())//
					.append('<').append(getValueType()).append('>')//
					.toString();
			}
		}

		public static abstract class DoubleTyped<M, K, V, MV extends M> extends ModelInstanceType<M, MV> {
			private final TypeToken<K> theKeyType;
			private final TypeToken<V> theValueType;

			DoubleTyped(TypeToken<K> keyType, TypeToken<V> valueType) {
				theKeyType = keyType;
				theValueType = valueType;
			}

			@Override
			public abstract ModelType.DoubleTyped<M> getModelType();

			public TypeToken<K> getKeyType() {
				return theKeyType;
			}

			public TypeToken<V> getValueType() {
				return theValueType;
			}

			@Override
			public TypeToken<?> getType(int typeIndex) {
				switch (typeIndex) {
				case 0:
					return theKeyType;
				case 1:
					return theValueType;
				default:
					throw new IndexOutOfBoundsException(typeIndex + " of 1");
				}
			}

			@Override
			public String toString() {
				return new StringBuilder(getModelType().modelType.getName())//
					.append('<').append(getKeyType()).append(", ")//
					.append(getValueType()).append('>')//
					.toString();
			}
		}
	}

	// Event(Observable.class),
	// Action(ObservableAction.class),
	private static final Map<String, ModelType<?>> MODEL_TYPES_BY_NAME = Collections.synchronizedMap(new LinkedHashMap<>());
	private static final Map<Class<?>, ModelType<?>> MODEL_TYPES_BY_TYPE = Collections.synchronizedMap(new LinkedHashMap<>());
	private static final List<ModelType<?>> ALL_MODEL_TYPES = Collections.synchronizedList(new ArrayList<>());
	private static final Map<ModelType<?>, Map<ModelType<?>, ModelConverter<?, ?>>> CONVERSION_TARGETS = new HashMap<>();
	private static final Map<ModelType<?>, ModelConverter<Object, ?>> FLEX_CONVERSION_TARGETS = new HashMap<>();
	private static final Map<ModelType<?>, ModelConverter<?, ?>> SELF_CONVERSION_TARGETS = new HashMap<>();

	private final String theName;
	/** The type of model this represents */
	public final Class<M> modelType;
	private final int theTypeCount;

	/**
	 * @param name The name of the model type
	 * @param type The type of model represented by this type
	 */
	protected ModelType(String name, Class<M> type) {
		theName = name;
		this.modelType = type;
		theTypeCount = type.getTypeParameters().length;
		synchronized (ModelType.class) {
			ModelType<?> old = MODEL_TYPES_BY_NAME.get(name);
			if (old != null)
				throw new IllegalArgumentException("A " + ModelType.class.getSimpleName() + " named '" + name + "' already exists: " + old);
			old = MODEL_TYPES_BY_TYPE.get(type);
			if (old != null)
				throw new IllegalArgumentException("A model type (" + old.getName() + ") for type '" + type.getName() + "' already exists");
			MODEL_TYPES_BY_NAME.put(name, this);
			MODEL_TYPES_BY_TYPE.put(type, this);
			ALL_MODEL_TYPES.add(this);
		}
		setupConversions(new ConversionBuilder<M>() {
			@Override
			public <M2> ConversionBuilder<M> convertibleTo(ModelType<M2> targetModelType, ModelConverter<M, M2> converter) {
				CONVERSION_TARGETS.computeIfAbsent(ModelType.this, __ -> new HashMap<>()).put(targetModelType, converter);
				return this;
			}

			@Override
			public <M2> ConversionBuilder<M> convertibleFrom(ModelType<M2> sourceModelType, ModelConverter<M2, M> converter) {
				CONVERSION_TARGETS.computeIfAbsent(sourceModelType, __ -> new HashMap<>()).put(ModelType.this, converter);
				return this;
			}

			@Override
			public ConversionBuilder<M> convertibleFromAny(ModelConverter<Object, M> converter) {
				FLEX_CONVERSION_TARGETS.put(ModelType.this, converter);
				return this;
			}

			@Override
			public ConversionBuilder<M> convertSelf(ModelConverter<M, M> converter) {
				SELF_CONVERSION_TARGETS.put(ModelType.this, converter);
				return this;
			}
		});
	}

	@Override
	public String getName() {
		return theName;
	}

	/** @return The number of type parameters for this model type */
	public int getTypeCount() {
		return theTypeCount;
	}

	public abstract ModelInstanceType<M, ?> forTypes(TypeToken<?>... types);

	public ModelInstanceType<M, ?> any() {
		TypeToken<?>[] types = new TypeToken[theTypeCount];
		Arrays.fill(types, TypeTokens.get().WILDCARD);
		return forTypes(types);
	}

	protected abstract Function<M, M> convertType(ModelInstanceType<M, ?> target, Function<Object, Object>[] casts,
		Function<Object, Object>[] reverses);

	protected void setupConversions(ConversionBuilder<M> builder) {
	}

	@Override
	public String toString() {
		return new StringBuilder(theName)//
			.append('(').append(modelType).append(')')//
			.toString();
	}

	public static String print(ModelType type) {
		return type == null ? "Unknown" : type.toString();
	}

	public static abstract class UnTyped<M> extends ModelType<M> {
		private final ModelInstanceType.UnTyped<M> theInstance;

		public UnTyped(String name, Class<M> type) {
			super(name, type);
			theInstance = new ModelInstanceType.UnTyped<M>() {
				@Override
				public ModelType.UnTyped<M> getModelType() {
					return ModelType.UnTyped.this;
				}
			};
		}

		@Override
		public ModelInstanceType<M, ?> forTypes(TypeToken<?>... types) {
			if (types.length != 0)
				throw new IllegalArgumentException(this + " is parameterized with 0 types, not " + types.length);
			return instance();
		}

		public ModelInstanceType.UnTyped<M> instance() {
			return theInstance;
		}

		@Override
		protected Function<M, M> convertType(ModelInstanceType<M, ?> target, Function<Object, Object>[] casts,
			Function<Object, Object>[] reverses) {
			throw new IllegalStateException("No types to convert");
		}
	}

	public static abstract class SingleTyped<M> extends ModelType<M> {
		public SingleTyped(String name, Class<M> type) {
			super(name, type);
		}

		private <V, MV extends M> ModelInstanceType.SingleTyped<M, V, MV> createInstance(TypeToken<V> valueType) {
			return new ModelInstanceType.SingleTyped<M, V, MV>(valueType) {
				@Override
				public ModelType.SingleTyped<M> getModelType() {
					return ModelType.SingleTyped.this;
				}
			};
		}

		@Override
		public ModelInstanceType<M, ?> forTypes(TypeToken<?>... types) {
			if (types.length != getTypeCount())
				throw new IllegalArgumentException(this + " is parameterized with 1 type, not " + types.length);
			return createInstance(types[0]);
		}

		public <V> ModelInstanceType.SingleTyped<M, V, ?> forType(TypeToken<V> type) {
			return createInstance(type);
		}

		public <V> ModelInstanceType.SingleTyped<M, V, ?> forType(Class<V> type) {
			return forType(TypeTokens.get().of(type));
		}

		public <V> ModelInstanceType.SingleTyped<M, ? extends V, ?> below(TypeToken<V> type) {
			return forType(TypeTokens.get().getExtendsWildcard(type));
		}

		public <V> ModelInstanceType.SingleTyped<M, ? extends V, ?> below(Class<V> type) {
			return below(TypeTokens.get().of(type));
		}

		public <V> ModelInstanceType.SingleTyped<M, ? super V, ?> above(TypeToken<V> type) {
			return forType(TypeTokens.get().getSuperWildcard(type));
		}

		public <V> ModelInstanceType.SingleTyped<M, ? super V, ?> above(Class<V> type) {
			return above(TypeTokens.get().of(type));
		}
	}

	public static abstract class DoubleTyped<M> extends ModelType<M> {
		public DoubleTyped(String name, Class<M> type) {
			super(name, type);
		}

		private <K, V, MV extends M> ModelInstanceType.DoubleTyped<M, K, V, MV> createInstance(TypeToken<K> keyType,
			TypeToken<V> valueType) {
			return new ModelInstanceType.DoubleTyped<M, K, V, MV>(keyType, valueType) {
				@Override
				public ModelType.DoubleTyped<M> getModelType() {
					return ModelType.DoubleTyped.this;
				}
			};
		}

		@Override
		public ModelInstanceType<M, ?> forTypes(TypeToken<?>... types) {
			if (types.length != getTypeCount())
				throw new IllegalArgumentException(this + " is parameterized with 2 types, not " + types.length);
			return createInstance(types[0], types[1]);
		}

		public <K, V> ModelInstanceType.DoubleTyped<M, K, V, ?> forType(TypeToken<K> keyType, TypeToken<V> valueType) {
			return createInstance(keyType, valueType);
		}

		public <K, V> ModelInstanceType.DoubleTyped<M, K, V, ?> forType(Class<K> keyType, Class<V> valueType) {
			return forType(TypeTokens.get().of(keyType), TypeTokens.get().of(valueType));
		}

		public <K, V> ModelInstanceType.DoubleTyped<M, ? extends K, ? extends V, ?> below(TypeToken<K> keyType, TypeToken<V> valueType) {
			return forType(TypeTokens.get().getSuperWildcard(keyType), TypeTokens.get().getExtendsWildcard(valueType));
		}

		public <K, V> ModelInstanceType.DoubleTyped<M, ? extends K, ? extends V, ?> below(Class<K> keyType, Class<V> valueType) {
			return below(TypeTokens.get().of(keyType), TypeTokens.get().of(valueType));
		}

		public <K, V> ModelInstanceType.DoubleTyped<M, ? super K, ? super V, ?> above(TypeToken<K> keyType, TypeToken<V> valueType) {
			return forType(TypeTokens.get().getSuperWildcard(keyType), TypeTokens.get().getSuperWildcard(valueType));
		}

		public <K, V> ModelInstanceType.DoubleTyped<M, ? super K, ? super V, ?> above(Class<K> keyType, Class<V> valueType) {
			return above(TypeTokens.get().of(keyType), TypeTokens.get().of(valueType));
		}
	}

	public interface ConversionBuilder<M> {
		<M2> ConversionBuilder<M> convertibleTo(ModelType<M2> targetModelType, ModelConverter<M, M2> converter);

		<M2> ConversionBuilder<M> convertibleFrom(ModelType<M2> sourceModelType, ModelConverter<M2, M> converter);

		ConversionBuilder<M> convertibleFromAny(ModelConverter<Object, M> converter);

		ConversionBuilder<M> convertSelf(ModelConverter<M, M> converter);
	}

	public static class ConvertedValue<MS, MVS extends MS, MT, MVT extends MT> implements ValueContainer<MT, MVT> {
		private final ValueContainer<MS, MVS> theSource;
		private final ModelInstanceType<MT, MVT> theType;
		private final ModelType.ModelInstanceConverter<MS, MT> theConverter;

		ConvertedValue(ValueContainer<MS, MVS> source, ModelInstanceType<MT, MVT> type, ModelInstanceConverter<MS, MT> converter) {
			if (source == null || type == null || converter == null)
				throw new NullPointerException();
			theSource = source;
			theType = type;
			theConverter = converter;
		}

		@Override
		public ModelInstanceType<MT, MVT> getType() {
			return theType;
		}

		@Override
		public MVT get(ModelSetInstance extModels) {
			MVS modelV = theSource.get(extModels);
			MVT converted = (MVT) theConverter.convert(modelV);
			return converted;
		}

		@Override
		public String toString() {
			return theSource.toString();
		}
	}
}
