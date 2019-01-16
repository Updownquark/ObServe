package org.observe.util;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

import com.google.common.reflect.TypeToken;

/**
 * It turns out that {@link TypeToken} is quite slow for many basic operations, like creation and {@link TypeToken#getRawType()
 * getRawType()}. This class provides some optimizations.
 */
public class TypeTokens {
	private static final TypeTokens instance = new TypeTokens();

	public static final TypeTokens get() {
		return instance;
	}

	public class TypeKey<T> {
		public final Class<T> clazz;
		public final TypeToken<T> type;
		private TypeToken<?> parameterizedType;
		public final boolean isBoolean;
		public final Class<? extends Number> number;
		public final boolean comparable;
		public final List<T> enumConstants;
		private Map<TypeToken<?>, TypeToken<?>> theCompoundTypes;

		TypeKey(Class<T> clazz) {
			this.clazz = clazz;
			type = TypeToken.of(clazz);
			boolean b = false;
			List<T> consts = null;
			if (clazz == double.class || clazz == Double.class) {
				number = Double.class;
			} else if (clazz == int.class || clazz == Integer.class) {
				number = Integer.class;
			} else if (clazz == long.class || clazz == Long.class) {
				number = Long.class;
			} else if (clazz == float.class || clazz == Float.class) {
				number = Float.class;
			} else if (clazz == byte.class || clazz == Byte.class) {
				number = Byte.class;
			} else if (clazz == short.class || clazz == Short.class) {
				number=Short.class;
			} else {
				number=null;
				if (clazz == boolean.class || clazz == Boolean.class) {
					b = true;
				} else if (Enum.class.isAssignableFrom(clazz)) {
					T[] ecs = clazz.getEnumConstants();
					consts = Collections.unmodifiableList(Arrays.asList(ecs));
				}
			}
			isBoolean = b;
			enumConstants = consts;
			comparable = number != null || clazz.isPrimitive() || Comparable.class.isAssignableFrom(clazz);
		}

		/**
		 * <p>
		 * Allows caching of compound type tokens.
		 * </p>
		 *
		 * <p>
		 * For example, to cache the type for Collection&lt;Integer&gt;, one could use:
		 * <code>TypeTokens.get().keyFor(Collection.class).getCompoundType(Integer.class, (i)->new TypeToken<Collection<Integer>>(){})</code>
		 * </p>
		 *
		 * @param paramType The type of the parameter
		 * @param creator Creates the type if it is not yet cached
		 * @return The compound type token
		 */
		public <I, C> TypeToken<C> getCompoundType(Class<I> paramType, Function<TypeToken<I>, TypeToken<C>> creator) {
			return getCompoundType(of(paramType), creator);
		}

		public <I, C> TypeToken<C> getCompoundType(TypeToken<I> paramType, Function<TypeToken<I>, TypeToken<C>> creator) {
			if (theCompoundTypes == null)
				theCompoundTypes = new ConcurrentHashMap<>();
			return (TypeToken<C>) theCompoundTypes.computeIfAbsent(paramType, t -> creator.apply((TypeToken<I>) t));
		}

		public <P> TypeToken<P> parameterized(Supplier<TypeToken<P>> creator) {
			if (parameterizedType == null)
				parameterizedType = creator.get();
			return (TypeToken<P>) parameterizedType;
		}

		@Override
		public int hashCode() {
			return clazz.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof TypeKey) {
				return clazz == ((TypeKey<?>) obj).clazz;
			} else if (obj instanceof Class) {
				return clazz == obj;
			} else if (obj instanceof TypeToken) {
				return clazz == ((TypeToken<?>) obj).getType();
			} else {
				return false;
			}
		}

		@Override
		public String toString() {
			return clazz.toString();
		}
	}

	private final ConcurrentHashMap<TypeKey<?>, TypeKey<?>> TYPES;
	private final Map<Class<?>, Class<?>> PRIMITIVE_TO_WRAPPER;
	private final Map<Class<?>, Class<?>> WRAPPER_TO_PRIMITIVE;

	public final TypeToken<String> STRING;
	public final TypeToken<Boolean> BOOLEAN;
	public final TypeToken<Double> DOUBLE;
	public final TypeToken<Long> LONG;
	public final TypeToken<Integer> INT;
	public final TypeToken<Object> OBJECT;

	protected TypeTokens() {
		TYPES = new ConcurrentHashMap<>();

		Map<Class<?>, Class<?>> wrappers = new HashMap<>();
		wrappers.put(boolean.class, Boolean.class);
		wrappers.put(char.class, Character.class);
		wrappers.put(double.class, Double.class);
		wrappers.put(float.class, Float.class);
		wrappers.put(long.class, Long.class);
		wrappers.put(int.class, Integer.class);
		wrappers.put(short.class, Short.class);
		wrappers.put(byte.class, Byte.class);
		PRIMITIVE_TO_WRAPPER = Collections.unmodifiableMap(wrappers);
		wrappers = new HashMap<>();
		wrappers.put(Boolean.class, boolean.class);
		wrappers.put(Character.class, char.class);
		wrappers.put(Double.class, double.class);
		wrappers.put(Float.class, float.class);
		wrappers.put(Long.class, long.class);
		wrappers.put(Integer.class, int.class);
		wrappers.put(Short.class, short.class);
		wrappers.put(Byte.class, byte.class);
		WRAPPER_TO_PRIMITIVE = Collections.unmodifiableMap(wrappers);

		STRING = of(String.class);
		BOOLEAN = of(Boolean.class);
		DOUBLE = of(Double.class);
		LONG = of(Long.class);
		INT = of(Integer.class);
		OBJECT = of(Object.class);
	}

	protected <T> TypeKey<T> createKey(Class<T> type) {
		return new TypeKey<>(type);
	}

	public <T> TypeKey<T> keyFor(Class<T> type) {
		// Type hackery, yes, I know
		TypeKey<T> key = (TypeKey<T>) TYPES.get(type);
		if (key == null) {
			key = createKey(type);
			TypeKey<T> old = (TypeKey<T>) TYPES.put(key, key);
			if (old != null) {
				key = old;
			}
		}
		return key;
	}

	public <T> TypeToken<T> of(Class<T> type) {
		return keyFor(type).type;
	}

	public static <T> Class<T> getRawType(TypeToken<T> type) {
		return (Class<T>) resolveType(type.getType());
	}

	public static Class<?> resolveType(Type t) {
		while (!(t instanceof Class)) {
			if (t instanceof ParameterizedType) {
				t = ((ParameterizedType) t).getRawType();
			} else if (t instanceof GenericArrayType) {
				t = ((GenericArrayType) t).getGenericComponentType();
			} else if (t instanceof WildcardType) {
				Type[] bounds = ((WildcardType) t).getUpperBounds();
				if (bounds.length > 0) {
					t = bounds[0];
				} else {
					return null;
				}
			} else if (t instanceof TypeVariable) {
				Type[] bounds = ((TypeVariable<?>) t).getBounds();
				if (bounds.length > 0) {
					t = bounds[0];
				} else {
					return null;
				}
			}
		}
		return (Class<?>) t;
	}

	public <T> Class<T> wrap(Class<T> type) {
		// Class<?> wrapper = PRIMITIVE_TO_WRAPPER.get(type);
		// return wrapper != null ? (Class<T>) wrapper : type;
		if (type == boolean.class)
			return (Class<T>) Boolean.class;
		else if (type == int.class)
			return (Class<T>) Integer.class;
		else if (type == long.class)
			return (Class<T>) Long.class;
		else if (type == double.class)
			return (Class<T>) Double.class;
		else if (type == float.class)
			return (Class<T>) Float.class;
		else if (type == byte.class)
			return (Class<T>) Byte.class;
		else if (type == short.class)
			return (Class<T>) Short.class;
		else
			return type;
	}

	public <T> Class<T> unwrap(Class<T> type) {
		Class<?> wrapped = WRAPPER_TO_PRIMITIVE.get(type);
		return wrapped == null ? type : (Class<T>) wrapped;
	}

	public <T> TypeToken<T> unwrap(TypeToken<T> type) {
		Class<T> raw = getRawType(type);
		Class<T> unwrapped = unwrap(raw);
		if (unwrapped == raw)
			return type;
		else
			return of(unwrapped);
	}

	public <T> TypeToken<T> wrap(TypeToken<T> type) {
		Class<T> raw = getRawType(type);
		Class<T> wrapped = wrap(raw);
		if (wrapped == raw)
			return type;
		else
			return of(wrapped);
	}

	public boolean isInstance(TypeToken<?> type, Object value) {
		if (value == null) {
			return false;
		}
		// This code is a necessary optimization because TypeToken.getRawType is apparently expensive
		if (isBoolean(type)) {
			return value instanceof Boolean;
		} else if (type == DOUBLE) {
			return value instanceof Double;
		} else if (type == LONG) {
			return value instanceof Long;
		} else if (type == INT) {
			return value instanceof Integer;
		} else if (type == STRING) {
			return value instanceof String;
		}
		Class<?> rawType = resolveType(type.getType());
		if (rawType == null) {
			rawType = type.getRawType();
		}
		rawType = wrap(rawType);
		return rawType.isInstance(value);
	}

	public <T> T cast(TypeToken<T> type, Object value) {
		if (value != null && !(isInstance(type, value)))
			throw new ClassCastException("Cannot cast instance of " + value.getClass().getName() + " to " + type);
		return (T) value;
	}

	public boolean isBoolean(TypeToken<?> type) {
		if (type == BOOLEAN)
			return true;
		Type runtimeType = type.getType();
		if (!(runtimeType instanceof Class)) {
			return false;
		}
		return isBoolean((Class<?>) runtimeType);
	}

	public boolean isBoolean(Class<?> clazz) {
		return clazz == Boolean.class || clazz == boolean.class;
	}

	public List<? extends Enum<?>> getEnumValues(TypeToken<?> type) {
		Type runtimeType = type.getType();
		if (!(runtimeType instanceof Class)) {
			return null;
		}
		return getEnumValues((Class<? extends Enum<?>>) runtimeType);
	}

	public <E extends Enum<?>> List<E> getEnumValues(Class<E> clazz) {
		return keyFor(clazz).enumConstants;
	}

	public Class<? extends Number> toNumber(TypeToken<?> type) {
		Type runtimeType = type.getType();
		if (!(runtimeType instanceof Class)) {
			return null;
		}
		return toNumber((Class<?>) runtimeType);
	}

	public Class<? extends Number> toNumber(Class<?> clazz) {
		return keyFor(clazz).number;
	}

	public boolean isComparable(TypeToken<?> type) {
		Type runtimeType = type.getType();
		if (runtimeType instanceof Class) {
			return keyFor((Class<?>) runtimeType).comparable;
		}
		Class<?> clazz = resolveType(runtimeType);
		if (clazz == null) {
			// TypeToken's resolution is more reliable, but slower
			clazz = type.getRawType();
		}
		return Comparable.class.isAssignableFrom(type.getRawType());
	}

	public boolean isComparable(Class<?> type) {
		return keyFor(type).comparable;
	}
}
