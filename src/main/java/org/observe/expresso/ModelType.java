package org.observe.expresso;

import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.util.TypeTokens;
import org.observe.util.TypeTokens.TypeConverter;
import org.qommons.LambdaUtils;
import org.qommons.Named;

import com.google.common.reflect.TypeToken;

/**
 * Represents a type of value stored in an {@link ObservableModelSet}
 *
 * @param <M> The java type of value represented by this type
 */
public abstract class ModelType<M> implements Named {
	/**
	 * An object that knows how to produce {@link ModelInstanceConverter instance converters}. ModelType implementations may override
	 * {@link ModelType#setupConversions(ConversionBuilder)} and install instances of this type to provide conversion ability.
	 *
	 * @param <M1> The model type to convert from
	 * @param <M2> The model type to convert to
	 */
	public interface ModelConverter<M1, M2> {
		/**
		 * @param source The type to convert from
		 * @param target The type to convert to
		 * @param env The environment which may contain needed resources for the conversion
		 * @return A converter capable of converting from the given source type to the given target type, or null if this converter cannot
		 *         create an instance converter for the given source/target types be made
		 */
		ModelInstanceConverter<M1, M2> convert(ModelInstanceType<M1, ?> source, ModelInstanceType<M2, ?> target,
			InterpretedExpressoEnv env);

		/**
		 * A model converter for an {@link UnTyped un-typed} model type
		 *
		 * @param <M1> The model type to convert from
		 * @param <M2> The model type to convert to
		 */
		public interface SimpleUnTyped<M1, M2> extends ModelConverter<M1, M2> {
			@Override
			default ModelInstanceConverter<M1, M2> convert(ModelInstanceType<M1, ?> source, ModelInstanceType<M2, ?> dest,
				InterpretedExpressoEnv env) throws IllegalArgumentException {
				return new ModelInstanceConverter<M1, M2>() {
					@Override
					public M2 convert(M1 src) {
						return SimpleUnTyped.this.convert(src);
					}

					@Override
					public ModelInstanceType<M2, ?> getType() {
						return dest;
					}
				};
			}

			/**
			 * @param source The source value to convert
			 * @return The converted value
			 */
			M2 convert(M1 source);
		}

		/**
		 * A model converter for a {@link SingleTyped single-typed} model type
		 *
		 * @param <M1> The model type to convert from
		 * @param <M2> The model type to convert to
		 */
		public interface SimpleSingleTyped<M1, M2> extends ModelConverter<M1, M2> {
			@Override
			default ModelInstanceConverter<M1, M2> convert(ModelInstanceType<M1, ?> source, ModelInstanceType<M2, ?> dest,
				InterpretedExpressoEnv env) throws IllegalArgumentException {
				TypeConverter<Object, ?, ?, Object> cast;
				TypeToken<Object> type;
				if (source.getType(0).equals(dest.getType(0)) || TypeTokens.get().isAssignable(dest.getType(0), source.getType(0))
					&& (dest.getType(0).getType() instanceof WildcardType
						|| TypeTokens.get().isAssignable(source.getType(0), dest.getType(0)))) {
					cast = null;
					type = (TypeToken<Object>) source.getType(0);
				} else {
					cast = (TypeConverter<Object, ?, ?, Object>) TypeTokens.get().getCast((TypeToken<Object>) dest.getType(0),
						(TypeToken<Object>) source.getType(0), true);
					type = cast.getConvertedType();
				}
				return new ModelInstanceConverter<M1, M2>() {
					@Override
					public M2 convert(M1 src) {
						return SimpleSingleTyped.this.convert(src, (TypeToken<Object>) dest.getType(0), cast);
					}

					@Override
					public ModelInstanceType<M2, ?> getType() {
						return dest.getModelType().forTypes(type);
					}
				};
			}

			/**
			 * @param <S> The value-type of the source to convert from
			 * @param <T> The value-type of the target to convert to
			 * @param source The source value to convert
			 * @param targetType The target value type
			 * @param converter The function to cast source values to target values and back
			 * @return The source model value, converted to this type
			 */
			<S, T> M2 convert(M1 source, TypeToken<T> targetType, TypeConverter<S, ?, ?, ? extends T> converter);
		}

		/**
		 * A model converter for a {@link DoubleTyped double-typed} model type
		 *
		 * @param <M1> The model type to convert from
		 * @param <M2> The model type to convert to
		 */
		public interface SimpleDoubleTyped<M1, M2> extends ModelConverter<M1, M2> {
			@Override
			default ModelInstanceConverter<M1, M2> convert(ModelInstanceType<M1, ?> source, ModelInstanceType<M2, ?> dest,
				InterpretedExpressoEnv env) throws IllegalArgumentException {
				TypeToken<Object> keyType;
				TypeConverter<Object, ?, ?, Object> keyCast;
				if (source.getType(0).equals(dest.getType(0)) || TypeTokens.get().isAssignable(dest.getType(0), source.getType(0))
					&& (dest.getType(0).getType() instanceof WildcardType
						|| TypeTokens.get().isAssignable(source.getType(0), dest.getType(0)))) {
					keyCast = null;
					keyType = (TypeToken<Object>) source.getType(0);
				} else {
					keyCast = (TypeConverter<Object, ?, ?, Object>) TypeTokens.get().getCast((TypeToken<Object>) dest.getType(0),
						(TypeToken<Object>) source.getType(0), true);
					if (keyCast == null)
						throw new IllegalArgumentException("Cannot convert " + source + " to " + dest);
					keyType = keyCast.getConvertedType();
				}
				TypeToken<Object> valueType;
				TypeConverter<Object, ?, ?, Object> valueCast;
				if (source.getType(1).equals(dest.getType(1)) || TypeTokens.get().isAssignable(dest.getType(1), source.getType(1))
					&& (dest.getType(1).getType() instanceof WildcardType
						|| TypeTokens.get().isAssignable(source.getType(1), dest.getType(1)))) {
					valueType = (TypeToken<Object>) source.getType(1);
					valueCast = null;
				} else {
					valueCast = (TypeConverter<Object, ?, ?, Object>) TypeTokens.get().getCast((TypeToken<Object>) dest.getType(1),
						(TypeToken<Object>) source.getType(1), true);
					if (valueCast == null)
						throw new IllegalArgumentException("Cannot convert " + source + " to " + dest);
					valueType = valueCast.getConvertedType();
				}
				return new ModelInstanceConverter<M1, M2>() {
					@Override
					public M2 convert(M1 src) {
						return SimpleDoubleTyped.this.convert(src, (TypeToken<Object>) dest.getType(0), (TypeToken<Object>) dest.getType(1), //
							keyCast, valueCast);
					}

					@Override
					public ModelInstanceType<M2, ?> getType() {
						return dest.getModelType().forTypes(keyType, valueType);
					}
				};
			}

			/**
			 * @param <KS> The key-type of the source to convert from
			 * @param <KT> The key-type of the target to convert to
			 * @param <VS> The value-type of the source to convert from
			 * @param <VT> The value-type of the target to convert to
			 * @param source The source value to convert
			 * @param targetKeyType The target key type
			 * @param targetValueType The target value type
			 * @param keyCast The function to cast source keys to target keys
			 * @param keyReverse The function to cast target keys back to source keys (if possible)
			 * @param valueCast The function to cast source values to target values
			 * @param valueReverse The function to cast target values back to source values (if possible)
			 * @return The source model value, converted to this type
			 */
			<KS, KT, VS, VT> M2 convert(M1 source, TypeToken<KT> targetKeyType, TypeToken<VT> targetValueType, //
				TypeConverter<KS, ?, ?, ? extends KT> keyCast, TypeConverter<VS, ?, ?, ? extends VT> valueCast);
		}
	}

	/**
	 * A converter that knows how to convert a source-typed model instance value to a target type
	 *
	 * @param <M1> The type of model instance value to convert
	 * @param <M2> The type to convert the model value to
	 */
	public interface ModelInstanceConverter<M1, M2> {
		/**
		 * @param source The source value to convert
		 * @return The converter value
		 * @throws ModelInstantiationException If the conversion fails
		 */
		M2 convert(M1 source) throws ModelInstantiationException;

		/** @return The type of value that this convert converts to */
		ModelInstanceType<M2, ?> getType();

		/**
		 * @param <M3> The target type of the next converter
		 * @param next The next converter
		 * @return A converter that converts from this converter's source type to the target type of the next converter
		 */
		default <M3> ModelInstanceConverter<M1, M3> and(ModelInstanceConverter<M2, M3> next) {
			ModelInstanceConverter<M1, M2> self = this;
			return new ModelInstanceConverter<M1, M3>() {
				@Override
				public M3 convert(M1 source) throws ModelInstantiationException {
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

	/**
	 * A converter that does nothing, and simply returns the source it is to convert
	 *
	 * @param <M> The model type that this converter is for
	 */
	public static class NoOpConverter<M> implements ModelInstanceConverter<M, M> {
		private final ModelInstanceType<M, ?> theType;

		/** @param type The type to convert */
		public NoOpConverter(ModelInstanceType<M, ?> type) {
			theType = type;
		}

		@Override
		public M convert(M source) {
			return source;
		}

		@Override
		public ModelInstanceType<M, ?> getType() {
			return theType;
		}

		@Override
		public String toString() {
			return "no-op";
		}
	}

	/**
	 * @param <M> The model type to convert
	 * @param type The type to convert
	 * @return A converter that does nothing, and simply returns the source it is to convert
	 */
	public static <M> NoOpConverter<M> nullConverter(ModelInstanceType<M, ?> type) {
		if (type == null)
			throw new NullPointerException();
		return new NoOpConverter<>(type);
	}

	/**
	 * Creates a converter from a function
	 *
	 * @param <M1> The model type to convert from
	 * @param <M2> The model type to convert to
	 * @param converter The function to convert a source model value to a target model value
	 * @param type The type that the function converts to
	 * @return A converter that performs the given transformation
	 */
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

			@Override
			public String toString() {
				return converter.toString();
			}
		};
	}

	/**
	 * Represents a type of model value complete with parameter types
	 *
	 * @param <M> The wildcard-parameterized type of values of this type
	 * @param <MV> The fully-parameterized type of values of this type
	 */
	public static abstract class ModelInstanceType<M, MV extends M> {
		/** @return This type's model type */
		public abstract ModelType<M> getModelType();

		/**
		 * @param typeIndex The type index to get the type parameter for
		 * @return The type parameter of this type for the given index
		 */
		public abstract TypeToken<?> getType(int typeIndex);

		/** @return All of this type's parameter types */
		public TypeToken<?>[] getTypeList() {
			TypeToken<?>[] types = new TypeToken[getModelType().getTypeCount()];
			for (int t = 0; t < types.length; t++)
				types[t] = getType(t);
			return types;
		}

		/**
		 * @param value The model value to check
		 * @return Whether, to the best of this type's ability to discern, the given value is an instance of this model type
		 */
		public boolean isInstance(Object value) {
			if (!(getModelType().modelType.isInstance(value)))
				return false;
			for (int t = 0; t < getModelType().getTypeCount(); t++) {
				if (!TypeTokens.get().isAssignable(getType(t), getModelType().getType((M) value, t)))
					return false;
			}
			return true;
		}

		/**
		 * @param <M2> The type to convert to
		 * @param target The type to convert to
		 * @param env The environment which may contain information needed for the conversion
		 * @return A converter capable of converting instances of this type to instances of the given target type
		 */
		public <M2> ModelInstanceConverter<M, M2> convert(ModelInstanceType<M2, ? extends M2> target, InterpretedExpressoEnv env) {
			if (target == null) {
				ModelConverter<M, M2> selfConverter = (ModelConverter<M, M2>) SELF_CONVERSION_TARGETS.get(getModelType());
				ModelInstanceConverter<M, M2> selfInstConverter = selfConverter == null ? null : selfConverter.convert(this, target, env);
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
					ModelInstanceConverter<M, M2> selfInstConverter = selfConverter == null ? null
						: selfConverter.convert(this, target, env);
					if (selfInstConverter == null)
						return (ModelInstanceConverter<M, M2>) nullConverter(target);
					else
						return selfInstConverter;
				}
				TypeToken<?>[] params = new TypeToken[getModelType().getTypeCount()];
				TypeConverter<Object, Object, Object, Object>[] casts = new TypeConverter[params.length];
				boolean trivial = true, exit = false;
				for (int i = 0; i < getModelType().getTypeCount(); i++) {
					TypeToken<?> myType = getType(i);
					TypeToken<?> targetType = target.getType(i);
					if (myType.equals(targetType)) {
						params[i] = getType(i);
						continue;
					} else if (!myType.isPrimitive() && !targetType.isPrimitive() //
						&& TypeTokens.get().isAssignable(targetType, myType)//
						&& (targetType.getType() instanceof WildcardType || TypeTokens.get().isAssignable(myType, targetType))) {
						params[i] = myType;
						casts[i] = null;
					} else {
						trivial = true;
						try {
							casts[i] = (TypeConverter<Object, Object, Object, Object>) TypeTokens.get()//
								.getCast(targetType, myType, true);
							if (!casts[i].isTrivial())
								trivial = false;
							params[i] = casts[i].getConvertedType();
						} catch (IllegalArgumentException e) {
							exit = true;
							break;
						}
					}
				}
				if (!exit) {
					Function<M, M2> converter;
					if (trivial) {
						converter = LambdaUtils.printableFn(m -> (M2) m, "trivial", "trivial");
					} else {
						converter = (Function<M, M2>) getModelType().convertType(//
							(ModelInstanceType<M, ? extends M>) target.getModelType().forTypes(params), casts);
					}
					if (converter == null)
						return null;
					ModelInstanceType<M2, ?> newType = target.getModelType().forTypes(params);
					ModelInstanceConverter<M, M2> firstConverter = converter(converter, newType);
					ModelConverter<M2, M2> selfConverter = (ModelConverter<M2, M2>) SELF_CONVERSION_TARGETS.get(target.getModelType());
					ModelInstanceConverter<M2, M2> selfInstConverter = selfConverter == null ? null
						: selfConverter.convert(newType, target, env);
					if (selfInstConverter != null)
						return firstConverter.and(selfInstConverter);
					else
						return firstConverter;
				}
			}
			ModelConverter<M, M2> modelConverter = (ModelConverter<M, M2>) CONVERSION_TARGETS
				.getOrDefault(getModelType(), Collections.emptyMap())//
				.get(target.getModelType());
			ModelInstanceConverter<M, M2> firstConverter = modelConverter == null ? null : modelConverter.convert(this, target, env);
			if (firstConverter == null) {
				modelConverter = (ModelConverter<M, M2>) FLEX_CONVERSION_FROM_TARGETS.get(target.getModelType());
				firstConverter = modelConverter == null ? null : modelConverter.convert(this, target, env);
			}
			if (firstConverter == null) {
				modelConverter = (ModelConverter<M, M2>) FLEX_CONVERSION_TO_TARGETS.get(getModelType());
				firstConverter = modelConverter == null ? null : modelConverter.convert(this, target, env);
			}
			if (firstConverter == null)
				return null;
			if (firstConverter.getType().getModelType().equals(target.getModelType())) {
				boolean allMatch = true;
				for (int i = 0; allMatch && i < firstConverter.getType().getModelType().getTypeCount(); i++)
					allMatch = TypeTokens.get().isAssignable(target.getType(i), firstConverter.getType().getType(i));
				if (allMatch)
					return firstConverter;
			}
			ModelInstanceConverter<M2, M2> secondConverter = firstConverter.getType().convert(target, env);
			if (secondConverter == null)
				return null;
			return firstConverter.and(secondConverter);
		}

		/**
		 * @param <M2> The model type to convert to
		 * @param <MV2> The type to convert to
		 * @param source The source value of this type
		 * @param targetType The type to convert to
		 * @param env The environment which may contain information needed for the conversion
		 * @return A value equivalent to this value, but with the given type
		 * @throws TypeConversionException If no converter is available for the conversion from this type to the given type
		 */
		public <M2, MV2 extends M2> InterpretedValueSynth<M2, MV2> as(InterpretedValueSynth<M, MV> source,
			ModelInstanceType<M2, MV2> targetType, InterpretedExpressoEnv env) throws TypeConversionException {
			ModelInstanceType<M, MV> sourceType = source.getType();
			ModelType.ModelInstanceConverter<M, M2> converter = sourceType.convert(targetType, env);
			if (converter == null) {
				// This next line is for debugging. I'm going to keep it here because it continues to be useful,
				// and it's only a performance hit when there's a conversion error, and then only a slight one.
				sourceType.convert(targetType, env);
				throw new TypeConversionException(source.toString(), sourceType, targetType);
			} else if (converter instanceof NoOpConverter)
				return (InterpretedValueSynth<M2, MV2>) source;
			else
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

		/**
		 * An {@link ModelInstanceType} for {@link UnTyped} models
		 *
		 * @param <M> The model type of this type
		 */
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

		/**
		 * An {@link ModelInstanceType} for {@link SingleTyped} models
		 *
		 * @param <M> The model type of this type
		 * @param <V> The value type of this type
		 * @param <MV> The model value type of this type
		 */
		public static abstract class SingleTyped<M, V, MV extends M> extends ModelInstanceType<M, MV> {
			private final TypeToken<V> theValueType;

			SingleTyped(TypeToken<V> valueType) {
				if (valueType == null)
					throw new NullPointerException();
				theValueType = valueType;
			}

			@Override
			public abstract ModelType.SingleTyped<M> getModelType();

			/** @return The only parameter type of this type */
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

		/**
		 * An {@link ModelInstanceType} for {@link DoubleTyped} models, e.g. Maps
		 *
		 * @param <M> The model type of this type
		 * @param <K> The key type of this type
		 * @param <V> The value type of this type
		 * @param <MV> The model value type of this type
		 */
		public static abstract class DoubleTyped<M, K, V, MV extends M> extends ModelInstanceType<M, MV> {
			private final TypeToken<K> theKeyType;
			private final TypeToken<V> theValueType;

			DoubleTyped(TypeToken<K> keyType, TypeToken<V> valueType) {
				if (keyType == null || valueType == null)
					throw new NullPointerException();
				theKeyType = keyType;
				theValueType = valueType;
			}

			@Override
			public abstract ModelType.DoubleTyped<M> getModelType();

			/** @return The first parameter type of this type */
			public TypeToken<K> getKeyType() {
				return theKeyType;
			}

			/** @return The second parameter type of this type */
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

	private static final Map<String, ModelType<?>> MODEL_TYPES_BY_NAME = Collections.synchronizedMap(new LinkedHashMap<>());
	private static final Map<Class<?>, ModelType<?>> MODEL_TYPES_BY_TYPE = Collections.synchronizedMap(new LinkedHashMap<>());
	private static final List<ModelType<?>> ALL_MODEL_TYPES = Collections.synchronizedList(new ArrayList<>());
	private static final Map<ModelType<?>, Map<ModelType<?>, ModelConverter<?, ?>>> CONVERSION_TARGETS = new HashMap<>();
	private static final Map<ModelType<?>, ModelConverter<?, Object>> FLEX_CONVERSION_TO_TARGETS = new HashMap<>();
	private static final Map<ModelType<?>, ModelConverter<Object, ?>> FLEX_CONVERSION_FROM_TARGETS = new HashMap<>();
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
			public ConversionBuilder<M> convertibleToAny(ModelConverter<M, Object> converter) {
				FLEX_CONVERSION_TO_TARGETS.put(ModelType.this, converter);
				return this;
			}

			@Override
			public ConversionBuilder<M> convertibleFromAny(ModelConverter<Object, M> converter) {
				FLEX_CONVERSION_FROM_TARGETS.put(ModelType.this, converter);
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

	/**
	 * @param value The value to get the type of
	 * @param typeIndex the type index to get the type for
	 * @return The type of the given value at the given index, or null if the information cannot be determined
	 */
	public abstract TypeToken<?> getType(M value, int typeIndex);

	/**
	 * @param types The parameter types to create the instance type for
	 * @return A {@link ModelInstanceType} of this model type with the given parameter types
	 */
	public abstract ModelInstanceType<M, ?> forTypes(TypeToken<?>... types);

	/** @return A {@link ModelInstanceType} of this model type with wildcard parameter types */
	public ModelInstanceType<M, ?> any() {
		TypeToken<?>[] types = new TypeToken[theTypeCount];
		Arrays.fill(types, TypeTokens.get().WILDCARD);
		return forTypes(types);
	}

	/** @return A {@link ModelInstanceType} of this model type with wildcard parameter types */
	public <MV extends M> ModelInstanceType<M, MV> anyAs() {
		TypeToken<?>[] types = new TypeToken[theTypeCount];
		Arrays.fill(types, TypeTokens.get().WILDCARD);
		return (ModelInstanceType<M, MV>) forTypes(types);
	}

	/**
	 * @param target The type to convert to
	 * @param casts Functions to convert values of each of this model type's parameter types to values of the target type's parameter types
	 * @return The function to convert model values of this model type to model values of the target type
	 */
	protected abstract Function<M, M> convertType(ModelInstanceType<M, ?> target, TypeConverter<Object, Object, Object, Object>[] casts);

	/**
	 * Called by the constructor to set up this type's conversion capabilities
	 *
	 * @param builder The builder to use to install converters
	 */
	protected void setupConversions(ConversionBuilder<M> builder) {
	}

	/**
	 * @param <MV> The type of the value to create
	 * @param name The name of the value to create
	 * @param type The type of the value to create
	 * @return A model value that minimally fulfills the contract of its model type until it is {@link HollowModelValue#satisfy(Object)
	 *         satisfied} with a value that it shall then reflect
	 */
	public abstract <MV extends M> HollowModelValue<M, MV> createHollowValue(String name, ModelInstanceType<M, MV> type);

	/**
	 * @param other The model type to compare to
	 * @return A model type that both this and the <code>other</code> model type can be converted to
	 */
	public abstract ModelType<?> getCommonType(ModelType<?> other);

	@Override
	public String toString() {
		return new StringBuilder(theName)//
			.append('(').append(modelType).append(')')//
			.toString();
	}

	/**
	 * A model type with no parameter types
	 *
	 * @param <M> The type of values of this model type
	 */
	public static abstract class UnTyped<M> extends ModelType<M> {
		private final ModelInstanceType.UnTyped<M> theInstance;

		/**
		 * @param name The name of the model type
		 * @param type The super type of the model type
		 */
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

		/**
		 * Since there are no parameters, there's no need for more than one instance of this type
		 *
		 * @return The instance of this type
		 */
		public ModelInstanceType.UnTyped<M> instance() {
			return theInstance;
		}

		@Override
		protected Function<M, M> convertType(ModelInstanceType<M, ?> target, TypeConverter<Object, Object, Object, Object>[] casts) {
			throw new IllegalStateException("No types to convert");
		}
	}

	/**
	 * A model type with one parameter type
	 *
	 * @param <M> The type of values of this model type
	 */
	public static abstract class SingleTyped<M> extends ModelType<M> {
		/**
		 * @param name The name of the model type
		 * @param type The super type of the model type
		 */
		protected SingleTyped(String name, Class<M> type) {
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

		/**
		 * @param <V> The parameter type for the new instance type
		 * @param type The parameter type for the new instance type
		 * @return The parameterized instance type
		 */
		public <V> ModelInstanceType.SingleTyped<M, V, ?> forType(TypeToken<V> type) {
			return createInstance(type);
		}

		/**
		 * @param <V> The parameter type for the new instance type
		 * @param type The parameter type for the new instance type
		 * @return The parameterized instance type
		 */
		public <V> ModelInstanceType.SingleTyped<M, V, ?> forType(Class<V> type) {
			return forType(TypeTokens.get().of(type));
		}

		/**
		 * Creates an instance of this type with a parameterized type
		 *
		 * @param <V> The type to convert to
		 * @param rawType The raw type to convert to
		 * @param parameters The parameters for the raw type
		 * @return The parameterized instance type
		 */
		public <V> ModelInstanceType.SingleTyped<M, V, ?> forType(Class<? super V> rawType, TypeToken<?>... parameters) {
			return forType(TypeTokens.get().keyFor(rawType).parameterized(parameters));
		}

		/**
		 * Creates an instance of this type with a parameterized type
		 *
		 * @param <V> The type to convert to
		 * @param rawType The raw type to convert to
		 * @param parameters The parameters for the raw type
		 * @return The parameterized instance type
		 */
		public <V> ModelInstanceType.SingleTyped<M, V, ?> forType(Class<? super V> rawType, Class<?>... parameters) {
			return forType(TypeTokens.get().keyFor(rawType).parameterized(parameters));
		}

		/**
		 * Creates a "? extends" wildcard-parameterized instance of this type
		 *
		 * @param <V> The upper bound for the wildcard
		 * @param type The upper bound for the wildcard
		 * @return The wildcard-parameterized instance type
		 */
		public <V> ModelInstanceType.SingleTyped<M, ? extends V, ?> below(TypeToken<V> type) {
			return forType(TypeTokens.get().getExtendsWildcard(type));
		}

		/**
		 * Creates a "? extends" wildcard-parameterized instance of this type
		 *
		 * @param <V> The upper bound for the wildcard
		 * @param type The upper bound for the wildcard
		 * @return The wildcard-parameterized instance type
		 */
		public <V> ModelInstanceType.SingleTyped<M, ? extends V, ?> below(Class<V> type) {
			return below(TypeTokens.get().of(type));
		}

		/**
		 * Creates a "? super" wildcard-parameterized instance of this type
		 *
		 * @param <V> The lower bound for the wildcard
		 * @param type The lower bound for the wildcard
		 * @return The wildcard-parameterized instance type
		 */
		public <V> ModelInstanceType.SingleTyped<M, ? super V, ?> above(TypeToken<V> type) {
			return forType(TypeTokens.get().getSuperWildcard(type));
		}

		/**
		 * Creates a "? super" wildcard-parameterized instance of this type
		 *
		 * @param <V> The lower bound for the wildcard
		 * @param type The lower bound for the wildcard
		 * @return The wildcard-parameterized instance type
		 */
		public <V> ModelInstanceType.SingleTyped<M, ? super V, ?> above(Class<V> type) {
			return above(TypeTokens.get().of(type));
		}
	}

	/**
	 * A model type with two parameter types
	 *
	 * @param <M> The type of values of this model type
	 */
	public static abstract class DoubleTyped<M> extends ModelType<M> {
		/**
		 * @param name The name of the model type
		 * @param type The super type of the model type
		 */
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

		/**
		 * @param <K> The key parameter type for the new instance type
		 * @param <V> The value parameter type for the new instance type
		 * @param keyType The key parameter type for the new instance type
		 * @param valueType The value parameter type for the new instance type
		 * @return The parameterized instance type
		 */
		public <K, V> ModelInstanceType.DoubleTyped<M, K, V, ?> forType(TypeToken<K> keyType, TypeToken<V> valueType) {
			return createInstance(keyType, valueType);
		}

		/**
		 * @param <K> The key parameter type for the new instance type
		 * @param <V> The value parameter type for the new instance type
		 * @param keyType The key parameter type for the new instance type
		 * @param valueType The value parameter type for the new instance type
		 * @return The parameterized instance type
		 */
		public <K, V> ModelInstanceType.DoubleTyped<M, K, V, ?> forType(Class<K> keyType, Class<V> valueType) {
			return forType(TypeTokens.get().of(keyType), TypeTokens.get().of(valueType));
		}

		/**
		 * Creates a "? extends" wildcard-parameterized instance of this type
		 *
		 * @param <K> The upper bound for the key wildcard type
		 * @param <V> The upper bound for the value wildcard type
		 * @param keyType The upper bound for the key wildcard type
		 * @param valueType The upper bound for the value wildcard type
		 * @return The wildcard-parameterized instance type
		 */
		public <K, V> ModelInstanceType.DoubleTyped<M, ? extends K, ? extends V, ?> below(TypeToken<K> keyType, TypeToken<V> valueType) {
			return forType(TypeTokens.get().getSuperWildcard(keyType), TypeTokens.get().getExtendsWildcard(valueType));
		}

		/**
		 * Creates a "? extends" wildcard-parameterized instance of this type
		 *
		 * @param <K> The upper bound for the key wildcard type
		 * @param <V> The upper bound for the value wildcard type
		 * @param keyType The upper bound for the key wildcard type
		 * @param valueType The upper bound for the value wildcard type
		 * @return The wildcard-parameterized instance type
		 */
		public <K, V> ModelInstanceType.DoubleTyped<M, ? extends K, ? extends V, ?> below(Class<K> keyType, Class<V> valueType) {
			return below(TypeTokens.get().of(keyType), TypeTokens.get().of(valueType));
		}

		/**
		 * Creates a "? super" wildcard-parameterized instance of this type
		 *
		 * @param <K> The lower bound for the key wildcard type
		 * @param <V> The lower bound for the value wildcard type
		 * @param keyType The lower bound for the key wildcard type
		 * @param valueType The lower bound for the value wildcard type
		 * @return The wildcard-parameterized instance type
		 */
		public <K, V> ModelInstanceType.DoubleTyped<M, ? super K, ? super V, ?> above(TypeToken<K> keyType, TypeToken<V> valueType) {
			return forType(TypeTokens.get().getSuperWildcard(keyType), TypeTokens.get().getSuperWildcard(valueType));
		}

		/**
		 * Creates a "? super" wildcard-parameterized instance of this type
		 *
		 * @param <K> The lower bound for the key wildcard type
		 * @param <V> The lower bound for the value wildcard type
		 * @param keyType The lower bound for the key wildcard type
		 * @param valueType The lower bound for the value wildcard type
		 * @return The wildcard-parameterized instance type
		 */
		public <K, V> ModelInstanceType.DoubleTyped<M, ? super K, ? super V, ?> above(Class<K> keyType, Class<V> valueType) {
			return above(TypeTokens.get().of(keyType), TypeTokens.get().of(valueType));
		}
	}

	/**
	 * A builder (passed to {@link #setupConversions(ConversionBuilder)} from the constructor) to allow implementations of ModelType to
	 * install converters related to this type.
	 *
	 * @param <M> The model type installing the converters
	 */
	public interface ConversionBuilder<M> {
		/**
		 * Installs a converter for any model value of this type to another given model type. The converter can always return null if a
		 * particular conversion cannot be made.
		 *
		 * @param <M2> The model type to convert to
		 * @param targetModelType The model type to convert to
		 * @param converter The converter to do the conversion
		 * @return This builder
		 */
		<M2> ConversionBuilder<M> convertibleTo(ModelType<M2> targetModelType, ModelConverter<M, M2> converter);

		/**
		 * Installs a converter for any model value of the given type to this type. The converter can always return null if a particular
		 * conversion cannot be made.
		 *
		 * @param <M2> The model type to convert from
		 * @param sourceModelType The model type to convert from
		 * @param converter The converter to do the conversion
		 * @return This builder
		 */
		<M2> ConversionBuilder<M> convertibleFrom(ModelType<M2> sourceModelType, ModelConverter<M2, M> converter);

		/**
		 * Installs a converter that may know how to convert a model value of this type to a value of any other type. The converter can
		 * always return null if a particular conversion cannot be made.
		 *
		 * @param converter The converter to do the conversion
		 * @return This builder
		 */
		ConversionBuilder<M> convertibleToAny(ModelConverter<M, Object> converter);

		/**
		 * Installs a converter that may know how to convert a model value of the given type to a value of this type. The converter can
		 * always return null if a particular conversion cannot be made.
		 *
		 * @param converter The converter to do the conversion
		 * @return This builder
		 */
		ConversionBuilder<M> convertibleFromAny(ModelConverter<Object, M> converter);

		/**
		 * Installs a converter that may know how to convert a model value of this type to a model value of a different parameterization of
		 * this type. The converter can always return null if a particular conversion cannot be made.
		 *
		 * @param converter The converter to do the conversion
		 * @return This builder
		 */
		ConversionBuilder<M> convertSelf(ModelConverter<M, M> converter);
	}

	/**
	 * A ModelValueSynth, converted from one type to another
	 *
	 * @param <MS> The model type of the source value
	 * @param <MVS> The type of the source value
	 * @param <MT> The model type of the target value
	 * @param <MVT> The type of the target value
	 */
	public static class ConvertedValue<MS, MVS extends MS, MT, MVT extends MT> implements InterpretedValueSynth<MT, MVT> {
		private final InterpretedValueSynth<MS, MVS> theSource;
		private final ModelInstanceType<MT, MVT> theType;
		private final ModelType.ModelInstanceConverter<MS, MT> theConverter;

		/**
		 * @param source The source value to convert
		 * @param type The type to convert to
		 * @param converter The convert to convert values of the source type to the target type
		 */
		public ConvertedValue(InterpretedValueSynth<MS, MVS> source, ModelInstanceType<MT, MVT> type,
			ModelInstanceConverter<MS, MT> converter) {
			if (source == null || type == null || converter == null)
				throw new NullPointerException();
			theSource = source;
			theType = type;
			theConverter = converter;
		}

		/** @return The lowest-level source value that is converted by this value */
		public InterpretedValueSynth<?, ?> getSourceRoot() {
			if (theSource instanceof ConvertedValue)
				return ((ConvertedValue<?, ?, ?, ?>) theSource).getSourceRoot();
			else
				return theSource;
		}

		/** @return The converter converting the source value to this value's type */
		public ModelType.ModelInstanceConverter<MS, MT> getConverter() {
			return theConverter;
		}

		@Override
		public ModelInstanceType<MT, MVT> getType() {
			return theType;
		}

		@Override
		public ModelValueInstantiator<MVT> instantiate() {
			return new ConvertedInstantiator<>(theSource.instantiate(), theConverter);
		}

		@Override
		public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
			return Collections.singletonList(theSource);
		}

		@Override
		public String toString() {
			return theSource.toString();
		}
	}

	public static class ConvertedInstantiator<MS, MVS extends MS, MT, MVT extends MT> implements ModelValueInstantiator<MVT> {
		private final ModelValueInstantiator<MVS> theSource;
		private final ModelType.ModelInstanceConverter<MS, MT> theConverter;

		public ConvertedInstantiator(ModelValueInstantiator<MVS> source, ModelType.ModelInstanceConverter<MS, MT> converter) {
			theSource = source;
			theConverter = converter;
		}

		@Override
		public MVT get(ModelSetInstance extModels) throws ModelInstantiationException {
			MVS modelV = theSource.get(extModels);
			MVT converted = (MVT) theConverter.convert(modelV);
			return converted;
		}

		@Override
		public MVT forModelCopy(MVT value, ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
			MVS sourceV = theSource.get(sourceModels);
			MVS newSourceV = theSource.get(newModels);
			if (sourceV == newSourceV)
				return value;
			else
				return (MVT) theConverter.convert(newSourceV);
		}

		@Override
		public String toString() {
			return theSource.toString();
		}
	}

	/**
	 * It can't be declared, but instances of this interface also must implement MV. This interface functions as an empty vessel that
	 * implements the full contract of a typical model value, but with no content until it is {@link #satisfy(Object) satisfied} dynamically
	 * with a real value, upon which it becomes a pass-through to that value.
	 *
	 * @param <M> The model type of the value
	 * @param <MV> The type of the value
	 */
	public interface HollowModelValue<M, MV extends M> {
		/**
		 * Satisfies this value with a real value, so that this value becomes a pass-through to the given value.
		 *
		 * @param realValue The model value for this value to reflect
		 */
		void satisfy(MV realValue);

		/** @return If this value has been {@link #satisfy(Object) satisfied} */
		boolean isSatisfied();
	}
}
