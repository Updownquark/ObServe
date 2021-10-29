package org.observe.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.qommons.Named;

import com.google.common.reflect.TypeToken;

public abstract class ModelType<M> implements Named {
	public interface ModelConverter<M1, M2> {
		ModelInstanceConverter<M1, M2> convert(ModelInstanceType<M1, ?> source, ModelInstanceType<M2, ?> dest)
			throws IllegalArgumentException;

		public interface SimpleUnTyped<M1, M2> extends ModelConverter<M1, M2> {
			@Override
			default ModelInstanceConverter<M1, M2> convert(ModelInstanceType<M1, ?> source, ModelInstanceType<M2, ?> dest)
				throws IllegalArgumentException {
				return src -> convert(src);
			}

			M2 convert(M1 source);
		}

		public interface SimpleSingleTyped<M1, M2> extends ModelConverter<M1, M2> {
			@Override
			default ModelInstanceConverter<M1, M2> convert(ModelInstanceType<M1, ?> source, ModelInstanceType<M2, ?> dest)
				throws IllegalArgumentException {
				Function<Object, Object> cast;
				Function<Object, Object> reverse;
				if (!source.getType(0).equals(dest.getType(0))) {
					cast = TypeTokens.get().getCast((TypeToken<Object>) source.getType(0), (TypeToken<Object>) dest.getType(0), true);
					if (cast == null)
						throw new IllegalArgumentException("Cannot convert " + source + " to " + dest);
					reverse = TypeTokens.get().getCast((TypeToken<Object>) dest.getType(0), (TypeToken<Object>) source.getType(0), true);
				} else
					cast = reverse = null;
				return src -> convert(src, (TypeToken<Object>) dest.getType(0), cast, reverse);
			}

			<S, T> M2 convert(M1 source, TypeToken<T> targetType, Function<S, T> cast, Function<T, S> reverse);
		}

		public interface SimpleDoubleTyped<M1, M2> extends ModelConverter<M1, M2> {
			@Override
			default ModelInstanceConverter<M1, M2> convert(ModelInstanceType<M1, ?> source, ModelInstanceType<M2, ?> dest)
				throws IllegalArgumentException {
				Function<Object, Object> keyCast;
				Function<Object, Object> keyReverse;
				if (!source.getType(0).equals(dest.getType(0))) {
					keyCast = TypeTokens.get().getCast((TypeToken<Object>) source.getType(0), (TypeToken<Object>) dest.getType(0), true);
					if (keyCast == null)
						throw new IllegalArgumentException("Cannot convert " + source + " to " + dest);
					keyReverse = TypeTokens.get().getCast((TypeToken<Object>) dest.getType(0), (TypeToken<Object>) source.getType(0), true);
				} else
					keyCast = keyReverse = null;
				Function<Object, Object> valueCast;
				Function<Object, Object> valueReverse;
				if (!source.getType(1).equals(dest.getType(1))) {
					valueCast = TypeTokens.get().getCast((TypeToken<Object>) source.getType(1), (TypeToken<Object>) dest.getType(1), true);
					if (valueCast == null)
						throw new IllegalArgumentException("Cannot convert " + source + " to " + dest);
					valueReverse = TypeTokens.get().getCast((TypeToken<Object>) dest.getType(1), (TypeToken<Object>) source.getType(1),
						true);
				} else
					valueCast = valueReverse = null;
				return src -> convert(src, (TypeToken<Object>) dest.getType(0), (TypeToken<Object>) dest.getType(1), //
					keyCast, keyReverse, valueCast, valueReverse);
			}

			<KS, KT, VS, VT> M2 convert(M1 source, TypeToken<KT> targetKeyType, TypeToken<VT> targetValueType, //
				Function<KS, KT> keyCast, Function<KT, KS> keyReverse, Function<VS, VT> valueCast, Function<VT, VS> valueReverse);
		}
	}

	public interface ModelInstanceConverter<M1, M2> {
		M2 convert(M1 source);

		static ModelInstanceConverter<?, ?> NULL = new ModelInstanceConverter<Object, Object>() {
			@Override
			public Object convert(Object source) {
				return source;
			}

			@Override
			public String toString() {
				return "no-op";
			}
		};
	}

	public static abstract class ModelInstanceType<M, MV extends M> {
		public abstract ModelType<M> getModelType();

		public abstract TypeToken<?> getType(int typeIndex);

		public <M2> ModelInstanceConverter<M, M2> convert(ModelInstanceType<M2, ? extends M2> target) {
			if (target.getModelType() == getModelType()) {
				boolean goodType = true;
				for (int i = 0; goodType && i < getModelType().getTypeCount(); i++)
					goodType = target.getType(i).equals(getType(i));
				if (goodType)
					return (ModelInstanceConverter<M, M2>) ModelInstanceConverter.NULL;
				Function<Object, Object>[] casts = new Function[getModelType().getTypeCount()];
				Function<Object, Object>[] reverses = new Function[casts.length];
				boolean reversible = true;
				for (int i = 0; i < getModelType().getTypeCount(); i++) {
					if (target.getType(i).equals(getType(i)))
						continue;
					if (target.getType(i).isAssignableFrom(getType(i)))
						casts[i] = null;
					else {
						try {
							casts[i] = (Function<Object, Object>) TypeTokens.get().getCast(getType(i), target.getType(i), true);
						} catch (IllegalArgumentException e) {
							return null;
						}
					}
					if (reversible && !getType(i).isAssignableFrom(target.getType(i))) {
						try {
							reverses[i] = (Function<Object, Object>) TypeTokens.get().getCast(target.getType(i), getType(i), true);
						} catch (IllegalArgumentException e) {
							reversible = false;
						}
					}
				}
				return (ModelInstanceConverter<M, M2>) getModelType().convertType((ModelInstanceType<M, ? extends M>) target, casts,
					reversible ? reverses : null);
			}
			ModelConverter<M, M2> modelConverter = (ModelConverter<M, M2>) CONVERSION_TARGETS
				.getOrDefault(getModelType(), Collections.emptyMap())//
				.get(target.getModelType());
			if (modelConverter == null)
				return null;
			return modelConverter.convert(this, target);
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

	private final String theName;
	public final Class<M> modelType;
	private final int theTypeCount;

	protected ModelType(String name, Class<M> type, int typeCount) {
		theName = name;
		this.modelType = type;
		theTypeCount = typeCount;
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
		});
	}

	@Override
	public String getName() {
		return theName;
	}

	public int getTypeCount() {
		return theTypeCount;
	}

	protected abstract ModelInstanceConverter<M, M> convertType(ModelInstanceType<M, ?> target, Function<Object, Object>[] casts,
		Function<Object, Object>[] reverses);

	protected void setupConversions(ConversionBuilder<M> builder) {
	}

	@Override
	public String toString() {
		return new StringBuilder(theName)//
			.append('(').append(modelType).append(')')//
			.toString();
	}

	// public boolean isExtension(ModelType type) {
	// return this == type || superTypes.contains(type);
	// }
	//
	// public static ModelType of(Class<?> thing) {
	// if (ObservableModelSet.class.isAssignableFrom(thing))
	// return Model;
	// else if (ObservableSortedMultiMap.class.isAssignableFrom(thing))
	// return SortedMultiMap;
	// else if (ObservableMultiMap.class.isAssignableFrom(thing))
	// return MultiMap;
	// else if (ObservableSortedMap.class.isAssignableFrom(thing))
	// return SortedMap;
	// else if (ObservableMap.class.isAssignableFrom(thing))
	// return Map;
	// else if (ObservableSortedSet.class.isAssignableFrom(thing))
	// return SortedSet;
	// else if (ObservableSet.class.isAssignableFrom(thing))
	// return Set;
	// else if (ObservableSortedCollection.class.isAssignableFrom(thing))
	// return SortedCollection;
	// else if (ObservableCollection.class.isAssignableFrom(thing))
	// return Collection;
	// else if (SettableValue.class.isAssignableFrom(thing))
	// return Value;
	// else if (ObservableAction.class.isAssignableFrom(thing))
	// return Action;
	// else if (Observable.class.isAssignableFrom(thing))
	// return Event;
	// else
	// return null;
	// }

	// public static String printType(Object value) {
	// return printType(of(value.getClass()));
	// }

	public static String print(ModelType type) {
		return type == null ? "Unknown" : type.toString();
	}

	public static abstract class UnTyped<M> extends ModelType<M> {
		private final ModelInstanceType.UnTyped<M> theInstance;

		public UnTyped(String name, Class<M> type) {
			super(name, type, 0);
			theInstance = new ModelInstanceType.UnTyped<M>() {
				@Override
				public ModelType.UnTyped<M> getModelType() {
					return ModelType.UnTyped.this;
				}
			};
		}

		public ModelInstanceType.UnTyped<M> instance() {
			return theInstance;
		}

		@Override
		protected ModelInstanceConverter<M, M> convertType(ModelInstanceType<M, ?> target, Function<Object, Object>[] casts,
			Function<Object, Object>[] reverses) {
			throw new IllegalStateException("No types to convert");
		}
	}

	public static abstract class SingleTyped<M> extends ModelType<M> {
		public SingleTyped(String name, Class<M> type) {
			super(name, type, 1);
		}

		private <V, MV extends M> ModelInstanceType.SingleTyped<M, V, MV> createInstance(TypeToken<V> valueType) {
			return new ModelInstanceType.SingleTyped<M, V, MV>(valueType) {
				@Override
				public ModelType.SingleTyped<M> getModelType() {
					return ModelType.SingleTyped.this;
				}
			};
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
			super(name, type, 2);
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
		<M2> ConversionBuilder<M> convertibleTo(ModelType<M2> modelType, ModelConverter<M, M2> converter);
	}
}
