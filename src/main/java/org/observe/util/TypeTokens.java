package org.observe.util;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

import org.qommons.StringUtils;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

/**
 * It turns out that {@link TypeToken} is quite slow for many basic operations, like creation and {@link TypeToken#getRawType()
 * getRawType()}. This class provides some optimizations as well as boilerplate-reducing utilities.
 */
public class TypeTokens {
	private static final TypeTokens instance = new TypeTokens();

	/** @return The common instance of this class */
	public static final TypeTokens get() {
		return instance;
	}

	/**
	 * Internal, cached holder for {@link TypeToken}-related information regarding a class
	 *
	 * @param <T> The type that this holder is for
	 */
	public class TypeKey<T> {
		/** The class that this holder is for */
		public final Class<T> clazz;
		/** The number of type parameters that */
		public final int typeParameters;
		/** The raw type-token of the class */
		public final TypeToken<T> type;
		private TypeToken<?> parameterizedType;
		/** Whether this type is extends {@link Comparable} */
		public final boolean comparable;
		private Map<List<TypeToken<?>>, TypeToken<? extends T>> theCompoundTypes;
		private Map<Object, Object> theCache;

		TypeKey(Class<T> clazz) {
			this.clazz = clazz;
			typeParameters = clazz.getTypeParameters().length;
			type = TypeToken.of(clazz);
			comparable = clazz.isPrimitive() || Comparable.class.isAssignableFrom(clazz);
			if (typeParameters > 0)
				theCompoundTypes = new ConcurrentHashMap<>();
		}

		/**
		 * @param <P> The compile-time wildcard type
		 * @return The wildcard-parameterized type of this class
		 */
		public <P extends T> TypeToken<P> wildCard() {
			if (typeParameters == 0)
				return (TypeToken<P>) type;
			if (parameterizedType == null) {
				TypeToken<?>[] paramTypes = new TypeToken[typeParameters];
				Arrays.fill(paramTypes, WILDCARD);
				parameterizedType = parameterized(paramTypes);
			}
			return (TypeToken<P>) parameterizedType;
		}

		/**
		 * @param <P> The type of the parameter
		 * @param <C> TypeToken&lt;P>
		 * @param parameter The type parameter for this one-parameter type
		 * @return TypeToken&lt;P>
		 * @throws IllegalArgumentException If this is not a one-parameter type
		 */
		public <P, C extends T> TypeToken<C> parameterized(TypeToken<P> parameter) {
			TypeParameter<P> tp = new TypeParameter<P>() {};
			return parameterized(//
				new Type[] { parameter.getType() }, //
				new TypeToken[] { parameter }, //
				paramTypes -> TypeToken.of(new ParameterizedTypeImpl(clazz, paramTypes)).where(tp, parameter)//
				);
		}

		/**
		 * @param <P1> The type of the first parameter
		 * @param <P2> The type of the second parameter
		 * @param <C> TypeToken&lt;P1, P2>
		 * @param param1 The first type parameter for this two-parameter type
		 * @param param2 The second type parameter for this two-parameter type
		 * @return TypeToken&lt;P1, P2>
		 * @throws IllegalArgumentException If this is not a two-parameter type
		 */
		public <P1, P2, C extends T> TypeToken<C> parameterized(TypeToken<P1> param1, TypeToken<P2> param2) {
			TypeParameter<P1> tp1 = new TypeParameter<P1>() {};
			TypeParameter<P2> tp2 = new TypeParameter<P2>() {};
			return parameterized(//
				new Type[] { param1.getType(), param2.getType() }, //
				new TypeToken[] { param1, param2 },
				paramTypes -> TypeToken.of(new ParameterizedTypeImpl(clazz, paramTypes)).where(tp1, param1).where(tp2, param2)//
				);
		}

		/**
		 * @param <P1> The type of the first parameter
		 * @param <P2> The type of the second parameter
		 * @param <P3> The type of the third parameter
		 * @param <C> TypeToken&lt;P1, P2, P3>
		 * @param param1 The first type parameter for this three-parameter type
		 * @param param2 The second type parameter for this three-parameter type
		 * @param param3 The third type parameter for this three-parameter type
		 * @return TypeToken&lt;P1, P2, P3>
		 * @throws IllegalArgumentException If this is not a three-parameter type
		 */
		public <P1, P2, P3, C extends T> TypeToken<C> parameterized(TypeToken<P1> param1, TypeToken<P2> param2, TypeToken<P3> param3) {
			TypeParameter<P1> tp1 = new TypeParameter<P1>() {};
			TypeParameter<P2> tp2 = new TypeParameter<P2>() {};
			TypeParameter<P3> tp3 = new TypeParameter<P3>() {};
			return parameterized(//
				new Type[] { param1.getType(), param2.getType(), param3.getType() }, //
				new TypeToken[] { param1, param2, param3 },
				paramTypes -> TypeToken.of(new ParameterizedTypeImpl(clazz, paramTypes)).where(tp1, param1).where(tp2, param2).where(tp3,
					param3)//
				);
		}

		/**
		 * @param <P1> The type of the first parameter
		 * @param <P2> The type of the second parameter
		 * @param <P3> The type of the third parameter
		 * @param <P4> The type of the fourth parameter
		 * @param <C> TypeToken&lt;P1, P2, P3, P4>
		 * @param param1 The first type parameter for this four-parameter type
		 * @param param2 The second type parameter for this four-parameter type
		 * @param param3 The third type parameter for this four-parameter type
		 * @param param4 The fourth type parameter for this four-parameter type
		 * @return TypeToken&lt;P1, P2, P3, P4>
		 * @throws IllegalArgumentException If this is not a four-parameter type
		 */
		public <P1, P2, P3, P4, C extends T> TypeToken<C> parameterized(TypeToken<P1> param1, TypeToken<P2> param2, TypeToken<P3> param3,
			TypeToken<P4> param4) {
			TypeParameter<P1> tp1 = new TypeParameter<P1>() {};
			TypeParameter<P2> tp2 = new TypeParameter<P2>() {};
			TypeParameter<P3> tp3 = new TypeParameter<P3>() {};
			TypeParameter<P4> tp4 = new TypeParameter<P4>() {};
			return parameterized(//
				new Type[] { param1.getType(), param2.getType(), param3.getType(), param4.getType() }, //
				new TypeToken[] { param1, param2, param3, param4 },
				paramTypes -> TypeToken.of(new ParameterizedTypeImpl(clazz, paramTypes)).where(tp1, param1).where(tp2, param2)
					.where(tp3, param3).where(tp4, param4)//
				);
		}

		/**
		 * @param <P1> The type of the first parameter
		 * @param <P2> The type of the second parameter
		 * @param <P3> The type of the third parameter
		 * @param <P4> The type of the fourth parameter
		 * @param <P5> The type of the fifth parameter
		 * @param <C> TypeToken&lt;P1, P2, P3, P4, P5>
		 * @param param1 The first type parameter for this five-parameter type
		 * @param param2 The second type parameter for this five-parameter type
		 * @param param3 The third type parameter for this five-parameter type
		 * @param param4 The fourth type parameter for this five-parameter type
		 * @param param5 The fifth type parameter for this five-parameter type
		 * @return TypeToken&lt;P1, P2, P3, P4, P5>
		 * @throws IllegalArgumentException If this is not a five-parameter type
		 */
		public <P1, P2, P3, P4, P5, C extends T> TypeToken<C> parameterized(TypeToken<P1> param1, TypeToken<P2> param2,
			TypeToken<P3> param3, TypeToken<P4> param4, TypeToken<P5> param5) {
			TypeParameter<P1> tp1 = new TypeParameter<P1>() {};
			TypeParameter<P2> tp2 = new TypeParameter<P2>() {};
			TypeParameter<P3> tp3 = new TypeParameter<P3>() {};
			TypeParameter<P4> tp4 = new TypeParameter<P4>() {};
			TypeParameter<P5> tp5 = new TypeParameter<P5>() {};
			return parameterized(//
				new Type[] { param1.getType(), param2.getType(), param3.getType(), param4.getType(), param5.getType() }, //
				new TypeToken[] { param1, param2, param3, param4, param5 },
				paramTypes -> TypeToken.of(new ParameterizedTypeImpl(clazz, paramTypes)).where(tp1, param1).where(tp2, param2)
					.where(tp3, param3).where(tp4, param4).where(tp5, param5)//
				);
		}

		/**
		 * @param parameters The type parameters to parameterize this type with
		 * @return A type token with this key's type as its raw type and the given parameters
		 * @throws IllegalArgumentException If the number of parameters does not match this type's parameter count
		 */
		public <C extends T> TypeToken<C> parameterized(Type... parameters) {
			if (parameters.length != typeParameters)
				throw new IllegalArgumentException("Type " + clazz.getName() + " has " + typeParameters
					+ " parameters; cannot be parameterized with " + Arrays.toString(parameters));
			TypeToken<?>[] paramTokens = new TypeToken[parameters.length];
			for (int i = 0; i < paramTokens.length; i++)
				paramTokens[i] = of(parameters[i]);
			return parameterized(parameters, paramTokens, paramTypes -> TypeToken.of(new ParameterizedTypeImpl(clazz, parameters)));
		}

		/**
		 * @param parameters The type parameters to parameterize this type with
		 * @return A type token with this key's type as its raw type and the given parameters
		 * @throws IllegalArgumentException If the number of parameters does not match this type's parameter count
		 */
		public <C extends T> TypeToken<C> parameterized(TypeToken<?>... parameters) {
			if (parameters.length != typeParameters)
				throw new IllegalArgumentException("Type " + clazz.getName() + " has " + typeParameters
					+ " parameters; cannot be parameterized with " + Arrays.toString(parameters));
			Type[] paramTypes = new Type[parameters.length];
			for (int i = 0; i < paramTypes.length; i++)
				paramTypes[i] = parameters[i].getType();
			return parameterized(paramTypes, parameters, __ -> TypeToken.of(new ParameterizedTypeImpl(clazz, paramTypes)));
		}

		private <P, C extends T> TypeToken<C> parameterized(Type[] paramTypes, TypeToken<?>[] paramTokens,
			Function<Type[], TypeToken<?>> creator) {
			return (TypeToken<C>) theCompoundTypes.computeIfAbsent(Arrays.asList(paramTokens),
				__ -> (TypeToken<? extends T>) creator.apply(paramTypes));
		}

		/**
		 * This method may be used to permanently cache certain constants that may be frequently used but are difficult to compute
		 *
		 * @param <X> The type of the value
		 * @param key The key by which to store the constant value
		 * @param supplier Creates the value initially if not yet stored. If null and no constant is stored, null will be returned.
		 * @return The value of the constant
		 */
		public synchronized <X> X getCached(Object key, Supplier<X> supplier) {
			if (theCache == null)
				theCache = new HashMap<>();
			if (supplier == null)
				return (X) theCache.get(key);
			else
				return (X) theCache.computeIfAbsent(key, __ -> supplier.get());
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

	private final ConcurrentHashMap<Class<?>, TypeKey<?>> TYPES;

	/** TypeToken&lt;String> */
	public final TypeToken<String> STRING;
	/** TypeToken&lt;String> */
	public final TypeToken<Boolean> BOOLEAN;
	/** TypeToken&lt;Double> */
	public final TypeToken<Double> DOUBLE;
	/** TypeToken&lt;Float> */
	public final TypeToken<Float> FLOAT;
	/** TypeToken&lt;Long> */
	public final TypeToken<Long> LONG;
	/** TypeToken&lt;Integer> */
	public final TypeToken<Integer> INT;
	/** TypeToken&lt;Short> */
	public final TypeToken<Short> SHORT;
	/** TypeToken&lt;Byte> */
	public final TypeToken<Byte> BYTE;
	/** TypeToken&lt;Character> */
	public final TypeToken<Character> CHAR;
	/** TypeToken&lt;Object> */
	public final TypeToken<Object> OBJECT;
	/** TypeToken&lt;Void> */
	public final TypeToken<Void> VOID;
	/** TypeToken&lt;?> */
	public final TypeToken<?> WILDCARD;
	/** TypeToken&lt;Class&lt;?>> */
	public final TypeToken<Class<?>> CLASS;

	/** Creates a new instance */
	protected TypeTokens() {
		TYPES = new ConcurrentHashMap<>();

		STRING = of(String.class);
		BOOLEAN = of(Boolean.class);
		DOUBLE = of(Double.class);
		FLOAT = of(Float.class);
		LONG = of(Long.class);
		INT = of(Integer.class);
		SHORT = of(Short.class);
		BYTE = of(Byte.class);
		CHAR = of(Character.class);
		OBJECT = of(Object.class);
		VOID = of(Void.class);
		WILDCARD = TypeToken.of(new WildcardTypeImpl());
		CLASS = keyFor(Class.class).wildCard();
		// WILDCARD = new TypeToken<Class<?>>() {}.resolveType(Class.class.getTypeParameters()[0]);
	}

	/**
	 * @param <T> The compile-time type to create the key for
	 * @param type The type to create the key for
	 * @return The new key for the type
	 */
	protected <T> TypeKey<T> createKey(Class<T> type) {
		return new TypeKey<>(type);
	}

	/**
	 * @param <T> The compile-time type to get the key for
	 * @param type The type to get the key for
	 * @return The key for the type
	 */
	public <T> TypeKey<T> keyFor(Class<T> type) {
		return (TypeKey<T>) TYPES.computeIfAbsent(type, this::createKey);
	}

	/**
	 * @param <T> The compile-time type to get the type token for
	 * @param type The type to get the type token for
	 * @return The type token for the given type
	 */
	public <T> TypeToken<T> of(Class<T> type) {
		return keyFor(type).type;
	}

	/**
	 * @param type The type to get the type token for
	 * @return The type token for the given type
	 */
	public TypeToken<?> of(Type type) {
		if (type instanceof Class)
			return of((Class<?>) type);
		else if (type instanceof ParameterizedType && ((ParameterizedType) type).getOwnerType() instanceof Class)
			return keyFor((Class<?>) ((ParameterizedType) type).getOwnerType())
				.parameterized(((ParameterizedType) type).getActualTypeArguments());
		else
			return TypeToken.of(type);
	}

	/**
	 * Same as {@link TypeToken#getRawType()}, but faster in many cases
	 *
	 * @param <T> The type of the type token
	 * @param type The type token to get the raw type of
	 * @return The raw type of the given type token
	 */
	public static <T> Class<T> getRawType(TypeToken<T> type) {
		return (Class<T>) getRawType(type.getType());
	}

	/**
	 * @param t The type to get the raw type of
	 * @return The raw type for the given type
	 */
	public static Class<?> getRawType(Type t) {
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
			} else
				throw new IllegalStateException("Unrecognized type implementation: " + t.getClass().getName());
		}
		return (Class<?>) t;
	}

	/**
	 * @param type The type to print
	 * @return A string representation of the given type using just the {@link Class#getSimpleName()} of the type and any parameters
	 */
	public static String getSimpleName(TypeToken<?> type) {
		return getSimpleName(type, null).toString();
	}

	/**
	 * @param type The type to print
	 * @param str The StringBuilder to print the type into, or null to create a new one
	 * @return The string builder containing a string representation of the given type using just the {@link Class#getSimpleName()} of the
	 *         type and any parameters
	 */
	public static StringBuilder getSimpleName(TypeToken<?> type, StringBuilder str) {
		return getSimpleName(type.getType(), str);
	}

	/**
	 * @param type The type to print
	 * @param str The StringBuilder to print the type into, or null to create a new one
	 * @return The string builder containing a string representation of the given type using just the {@link Class#getSimpleName()} of the
	 *         type and any parameters
	 */
	public static StringBuilder getSimpleName(Type type, StringBuilder str) {
		if (str == null)
			str = new StringBuilder();
		if (type instanceof Class)
			str.append(((Class<?>) type).getSimpleName());
		else if (type instanceof ParameterizedType) {
			ParameterizedType p = (ParameterizedType) type;
			if (p.getOwnerType() != null)
				getSimpleName(p.getOwnerType(), str).append('.');
			getSimpleName(p.getRawType(), str).append('<');
			boolean first = true;
			for (Type arg : p.getActualTypeArguments()) {
				if (first)
					first = false;
				else
					str.append(',');
				getSimpleName(arg, str);
			}
			str.append('>');
		} else if (type instanceof GenericArrayType) {
			GenericArrayType arrayType = (GenericArrayType) type;
			getSimpleName(arrayType.getGenericComponentType(), str).append("[]");
		} else if (type instanceof TypeVariable) {
			TypeVariable<?> v = (TypeVariable<?>) type;
			str.append(v.getTypeName());
		} else if (type instanceof WildcardType) {
			WildcardType w = (WildcardType) type;
			str.append(w.getTypeName());
			for (Type ext : w.getLowerBounds())
				getSimpleName(ext, str.append(" super "));
			for (Type ext : w.getUpperBounds())
				getSimpleName(ext, str.append(" extends "));
		} else
			str.append("??");
		return str;
	}

	/**
	 * @param type The type to wrap
	 * @return The non-primitive wrapper class corresponding to the given primitive type, or the input if it is not primitive
	 */
	public <T> Class<T> wrap(Class<T> type) {
		if (!type.isPrimitive())
			return type;
		else if (type == boolean.class)
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
		else if (type == char.class)
			return (Class<T>) Character.class;
		else
			throw new IllegalStateException("Unrecognized primitive type: " + type);
	}

	/**
	 * @param type The type to unwrap
	 * @return The primitive type corresponding to the given primitive wrapper class, or the input if it is not a primitive wrapper
	 */
	public <T> Class<T> unwrap(Class<T> type) {
		if (type.isPrimitive())
			return type;
		else if (type == Boolean.class)
			return (Class<T>) boolean.class;
		else if (type == int.class)
			return (Class<T>) int.class;
		else if (type == Long.class)
			return (Class<T>) long.class;
		else if (type == Double.class)
			return (Class<T>) double.class;
		else if (type == Float.class)
			return (Class<T>) float.class;
		else if (type == Byte.class)
			return (Class<T>) byte.class;
		else if (type == Short.class)
			return (Class<T>) short.class;
		else if (type == Character.class)
			return (Class<T>) char.class;
		else
			return type;
	}

	/**
	 * @param type The type to wrap
	 * @return The non-primitive wrapper class corresponding to the given primitive type, or the input if it is not primitive
	 */
	public <T> TypeToken<T> wrap(TypeToken<T> type) {
		Class<T> raw = getRawType(type);
		Class<T> wrapped = wrap(raw);
		if (wrapped == raw)
			return type;
		else
			return of(wrapped);
	}

	/**
	 * @param type The type to unwrap
	 * @return The primitive type corresponding to the given primitive wrapper class, or the input if it is not a primitive wrapper
	 */
	public <T> TypeToken<T> unwrap(TypeToken<T> type) {
		Class<T> raw = getRawType(type);
		Class<T> unwrapped = unwrap(raw);
		if (unwrapped == raw)
			return type;
		else
			return of(unwrapped);
	}

	/**
	 * Checks the value against the type's raw type
	 *
	 * @param type The type to check against
	 * @param value The value to check
	 * @return Whether the given value is an instance of the given type
	 */
	public boolean isInstance(TypeToken<?> type, Object value) {
		if (value == null || type == VOID) {
			return false;
		} else if (type == OBJECT)
			return true;
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
		Class<?> rawType = getRawType(type.getType());
		if (rawType == null) {
			rawType = type.getRawType();
		}
		rawType = wrap(rawType);
		return rawType.isInstance(value);
	}

	/**
	 * @param <T> The compile-time type to cast to
	 * @param type The type to cast to
	 * @param value The value to cast
	 * @return The value as an instance of the given type
	 * @throws ClassCastException If the given value is not null, nor an instance of the given type
	 */
	public <T> T cast(TypeToken<T> type, Object value) throws ClassCastException {
		if (value != null && !(isInstance(type, value)))
			throw new ClassCastException("Cannot cast instance of " + value.getClass().getName() + " to " + type);
		return (T) value;
	}

	/**
	 * Similar to {@link TypeToken#isAssignableFrom(TypeToken)}, but this method allows for assignments where conversion is required, e.g.
	 * auto-(un)boxing and primitive number type conversion.
	 *
	 * @param left The type of the variable to assign the value to
	 * @param right The type of the value to assign
	 * @return Whether the given value type can be assigned to the given variable type
	 */
	public boolean isAssignable(TypeToken<?> left, TypeToken<?> right) {
		if (left.isAssignableFrom(right))
			return true;
		Class<?> primitiveRight = unwrap(getRawType(right));
		Class<?> primitiveLeft = unwrap(getRawType(left));
		if (!primitiveRight.isPrimitive())
			return false;
		else if (primitiveRight.equals(primitiveLeft))
			return true;
		else if (primitiveRight == boolean.class)
			return false;
		else if (primitiveLeft == Number.class)
			return true;
		else if (!primitiveLeft.isPrimitive())
			return false;
		else if (primitiveLeft == double.class)
			return true;
		// Now left!=right and left!=double.class
		else if (primitiveRight == double.class || primitiveRight == float.class || primitiveRight == long.class)
			return false;
		// Now right can only be int, short, byte, or char
		else if (primitiveLeft == long.class || primitiveLeft == int.class)
			return true;
		else if (primitiveRight == byte.class)
			return primitiveLeft == short.class || primitiveLeft == char.class;
		else
			return false;
	}

	/**
	 * @param <T> The compile-time type to cast to
	 * @param <X> The compile-time type to cast from
	 * @param left The type to cast to
	 * @param right The type to cast from
	 * @return A function that takes an instance of the right type and returns it as an instance of the left type, throwing a
	 *         {@link ClassCastException} if the value is neither null nor an instance of the left type, or a {@link NullPointerException}
	 *         if the value is null and the left type is primitive
	 */
	public <T, X> Function<T, X> getCast(TypeToken<T> left, TypeToken<X> right) {
		if (left.isAssignableFrom(right)) {
			if (left.isPrimitive() && !right.isPrimitive())
				return v -> {
					if (v == null)
						throw new NullPointerException("Cannot cast null to primitive type " + left);
					return (X) v;
				};
				else
					return v -> (X) v;
		}
		Class<?> primitiveRight = unwrap(getRawType(right));
		Class<?> primitiveLeft = unwrap(getRawType(left));
		Function<T, T> nullCheck;
		if (left.isPrimitive() && !right.isPrimitive()) {
			nullCheck = v -> {
				if (v == null)
					throw new NullPointerException("Cannot cast null to primitive type " + left);
				return v;
			};
		} else {
			nullCheck = v -> v;
		}
		if (!primitiveRight.isPrimitive())
			throw new IllegalArgumentException("Cannot cast " + right + " to " + left);
		else if (primitiveRight == primitiveLeft) {
			return v -> (X) nullCheck.apply(v);
		} else if (!primitiveLeft.isPrimitive())
			throw new IllegalArgumentException("Cannot cast " + right + " to " + left);
		else if (primitiveRight == boolean.class)
			throw new IllegalArgumentException("Cannot cast " + right + " to " + left);
		Function<T, Number> toNumber;
		if (primitiveRight == char.class) {
			toNumber = v -> Integer.valueOf(((Character) nullCheck.apply(v)).charValue());
		} else {
			toNumber = v -> (Number) nullCheck.apply(v);
		}
		Function<T, Object> fn;
		if (primitiveLeft == double.class) {
			fn = v -> toNumber.apply(v).doubleValue();
			// Now left!=right and left!=double.class
		} else if (primitiveRight == double.class || primitiveRight == float.class || primitiveRight == long.class)
			throw new IllegalArgumentException("Cannot cast " + right + " to " + left);
		// Now right can only be int, short, byte, or char
		else if (primitiveLeft == long.class) {
			fn = v -> toNumber.apply(v).longValue();
		} else if (primitiveLeft == int.class) {
			fn = v -> toNumber.apply(v).intValue();
		} else if (primitiveRight == byte.class) {
			if (primitiveLeft == short.class) {
				fn = v -> toNumber.apply(v).shortValue();
			} else if (primitiveLeft == char.class) {
				fn = v -> Character.valueOf((char) toNumber.apply(v).byteValue());
			} else
				throw new IllegalArgumentException("Cannot cast " + right + " to " + left);
		} else
			throw new IllegalArgumentException("Cannot cast " + right + " to " + left);

		return (Function<T, X>) fn;
	}

	/**
	 * @param <X> The compile-time type to cast to
	 * @param type The type to cast to
	 * @param value The value to cast
	 * @return The value as an instance of the given type
	 * @throws NullPointerException if the value is null and the left type is primitive
	 * @throws ClassCastException if the value is neither null nor an instance of the type
	 */
	public <X> X castAssignable(TypeToken<X> type, Object value) throws NullPointerException, ClassCastException {
		if (value == null) {
			if (type.isPrimitive())
				throw new ClassCastException("Null cannot be cast to primitive type " + type);
			else
				return null;
		} else if (!isAssignable(type, TypeTokens.get().of(value.getClass())))
			throw new ClassCastException("Cannot cast instance of " + value.getClass().getName() + " to " + type);
		return (X) value;
	}

	/**
	 * @param <X> The compile-time common type of the given types
	 * @param types The types to get the common type of
	 * @return The most specific TypeToken that can be found which all of the inputs extend
	 */
	public <X> TypeToken<X> getCommonType(TypeToken<? extends X>... types) {
		return getCommonType(Arrays.asList(types));
	}

	/**
	 * @param <X> The compile-time common type of the given types
	 * @param types The types to get the common type of
	 * @return The most specific TypeToken that can be found which all of the inputs extend
	 */
	public <X> TypeToken<X> getCommonType(List<TypeToken<? extends X>> types) {
		return (TypeToken<X>) _commonType(types);
	}

	private <X> TypeToken<?> _commonType(List<TypeToken<? extends X>> types) {
		TypeToken<? extends X> firstType = types.get(0);
		boolean first = true;
		boolean firstIsCommon = true;
		for (TypeToken<? extends X> type : types) {
			if (first)
				first = false;
			else if (!isAssignable(firstType, type)) {
				firstIsCommon = false;
				break;
			}
		}
		if (firstIsCommon)
			return firstType;
		List<? extends TypeToken<?>> extendedTypes = decompose(firstType);
		for (TypeToken<?> extType : extendedTypes) {
			first = true;
			boolean extIsCommon = true;
			for (TypeToken<? extends X> type : types) {
				if (first)
					first = false;
				else if (!isAssignable(extType, type)) {
					extIsCommon = false;
					break;
				}
			}
			if (extIsCommon)
				return extType;
		}
		return OBJECT;
	}

	private <X> List<TypeToken<? super X>> decompose(TypeToken<X> type) {
		List<TypeToken<? super X>> decomposed = new LinkedList<>();
		Class<?> raw = getRawType(type);
		if (raw.isPrimitive()) {
			decomposed.add(wrap(type));
			if (raw != boolean.class)
				decomposed.add((TypeToken<? super X>) of(Number.class));
			while (true) {
				decomposed.add((TypeToken<? super X>) of(raw));
				if (raw == byte.class) {
					decomposed.add((TypeToken<? super X>) of(char.class));
					raw = short.class;
				} else if (raw == short.class)
					raw = int.class;
				else if (raw == int.class) {
					decomposed.add((TypeToken<? super X>) of(float.class));
					decomposed.add((TypeToken<? super X>) of(double.class));
					raw = long.class;
				} else if (raw == float.class)
					raw = double.class;
				else
					break;
			}
		}
		if (!raw.isInterface()) {
			Class<X> c = (Class<X>) raw;
			TypeToken<X> t = type;
			while (raw != Object.class) {
				decomposed.add(t);
				c = (Class<X>) c.getSuperclass();
				t = (TypeToken<X>) t.getSupertype(c);
			}
		}
		for (Class<?> intf : raw.getInterfaces())
			this.<Object, X> decomposeInterfaces(decomposed, (Class<Object>) intf,
				(TypeToken<Object>) type.getSupertype((Class<Object>) intf));
		return decomposed;
	}

	private <I, X extends I> void decomposeInterfaces(List<TypeToken<? super X>> decomposed, Class<I> intf, TypeToken<I> intfType) {
		decomposed.add(intfType);
		for (Class<?> intf2 : intf.getInterfaces())
			this.<Object, X> decomposeInterfaces(decomposed, (Class<Object>) intf2,
				(TypeToken<Object>) intfType.getSupertype((Class<Object>) intf2));
	}

	/**
	 * @param type The type to test
	 * @return Whether the type is of <code>Boolean.class</code> or <code>boolean.class</code>
	 */
	public boolean isBoolean(TypeToken<?> type) {
		if (type == BOOLEAN)
			return true;
		Type runtimeType = type.getType();
		if (!(runtimeType instanceof Class)) {
			return false;
		}
		return isBoolean((Class<?>) runtimeType);
	}

	/**
	 * @param clazz The class to test
	 * @return Whether the class is <code>Boolean.class</code> or <code>boolean.class</code>
	 */
	public boolean isBoolean(Class<?> clazz) {
		return clazz == Boolean.class || clazz == boolean.class;
	}

	/**
	 * @param type The type to get the number extension of
	 * @return If the type is primitive or primitive-wrapper number type, the Number extension representing the type; otherwise null
	 */
	public Class<? extends Number> toNumber(TypeToken<?> type) {
		Type runtimeType = type.getType();
		if (!(runtimeType instanceof Class)) {
			return null;
		}
		return toNumber((Class<?>) runtimeType);
	}

	/**
	 * @param clazz The class to get the number extension of
	 * @return If the class is primitive or primitive-wrapper number type, the Number extension representing the class; otherwise null
	 */
	public Class<? extends Number> toNumber(Class<?> clazz) {
		if (clazz.isPrimitive()) {
			if (clazz == int.class)
				return Integer.class;
			else if (clazz == long.class)
				return Long.class;
			else if (clazz == double.class)
				return Double.class;
			else if (clazz == float.class)
				return Float.class;
			else if (clazz == byte.class)
				return Byte.class;
			else if (clazz == short.class)
				return Short.class;
			else
				return null;
		} else {
			if (clazz == int.class)
				return Integer.class;
			else if (clazz == Long.class)
				return Long.class;
			else if (clazz == Double.class)
				return Double.class;
			else if (clazz == Float.class)
				return Float.class;
			else if (clazz == Byte.class)
				return Byte.class;
			else if (clazz == Short.class)
				return Short.class;
			else
				return null;
		}
	}

	/**
	 * @param type The type to test
	 * @return Whether the given type extends {@link Comparable}
	 */
	public boolean isComparable(TypeToken<?> type) {
		Type runtimeType = type.getType();
		if (runtimeType instanceof Class) {
			return keyFor((Class<?>) runtimeType).comparable;
		}
		Class<?> clazz = getRawType(runtimeType);
		if (clazz == null) {
			// TypeToken's resolution is more reliable, but slower
			clazz = type.getRawType();
		}
		return Comparable.class.isAssignableFrom(type.getRawType());
	}

	/**
	 * @param type The type to test
	 * @return Whether the given type extends {@link Comparable}
	 */
	public boolean isComparable(Class<?> type) {
		return keyFor(type).comparable;
	}

	/**
	 * @param componentType The component type for the array
	 * @param dimCount The dimension count for the array type
	 * @return An array-type TypeToken with the given component type and dimension count
	 */
	public TypeToken<?> getArrayType(TypeToken<?> componentType, int dimCount) {
		return _arrayType(componentType, dimCount);
	}

	private <X> TypeToken<?> _arrayType(TypeToken<X> componentType, int dimCount) {
		if (dimCount == 0)
			return componentType;
		else if (dimCount < 0)
			throw new IllegalArgumentException("Negative dimension count: " + dimCount);
		TypeToken<X> type = componentType;
		if (type.isPrimitive()) {
			Class<X> raw = getRawType(type);
			if (raw == boolean.class)
				type = (TypeToken<X>) of(boolean[].class);
			else if (raw == char.class)
				type = (TypeToken<X>) of(char[].class);
			else if (raw == byte.class)
				type = (TypeToken<X>) of(byte[].class);
			else if (raw == short.class)
				type = (TypeToken<X>) of(short[].class);
			else if (raw == int.class)
				type = (TypeToken<X>) of(int[].class);
			else if (raw == long.class)
				type = (TypeToken<X>) of(long[].class);
			else if (raw == float.class)
				type = (TypeToken<X>) of(float[].class);
			else if (raw == double.class)
				type = (TypeToken<X>) of(double[].class);
			else
				throw new IllegalStateException("Unrecognized primitive type: " + type);

			dimCount--;
		}
		for (int i = 0; i < dimCount; i++) {
			type = (TypeToken<X>) new TypeToken<X[]>() {}.where(new TypeParameter<X>() {}, type);
		}
		return type;
	}

	/**
	 * @param <D> The type of the declaration
	 * @param <X> The compile-time type variable
	 * @param variableName The name for the type variable
	 * @param declaration The generic declaration for the type variable
	 * @return The type variable token
	 */
	public <D extends GenericDeclaration, X> TypeToken<X> getTypeVariable(String variableName, D declaration) {
		return (TypeToken<X>) TypeToken.of(new TypeVariableImpl<>(declaration, variableName));
	}

	/**
	 * @param type TypeToken&lt;X>
	 * @return TypeToken&lt;? extends X>
	 */
	public <X> TypeToken<? extends X> getExtendsWildcard(TypeToken<X> type) {
		return (TypeToken<? extends X>) TypeToken.of(new WildcardTypeImpl(new Type[0], new Type[] { type.getType() }));
	}

	/**
	 * @param type TypeToken&lt;X>
	 * @return TypeToken&lt;? super X>
	 */
	public <X> TypeToken<? super X> getSuperWildcard(TypeToken<X> type) {
		return (TypeToken<? super X>) TypeToken.of(new WildcardTypeImpl(new Type[] { type.getType() }, new Type[0]));
	}

	private static class ParameterizedTypeImpl implements ParameterizedType {
		private final Type theOwnerType;
		private final Type[] theTypeArgs;

		ParameterizedTypeImpl(Type ownerType, Type... typeArgs) {
			theOwnerType = ownerType;
			theTypeArgs = typeArgs;
		}

		@Override
		public Type[] getActualTypeArguments() {
			return theTypeArgs;
		}

		@Override
		public Type getRawType() {
			return theOwnerType;
		}

		@Override
		public Type getOwnerType() {
			return null;
		}

		@Override
		public int hashCode() {
			return Objects.hash(theOwnerType, theTypeArgs);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof ParameterizedType))
				return false;
			return getOwnerType().equals(((ParameterizedType) obj).getOwnerType())
				&& Arrays.equals(theTypeArgs, ((ParameterizedType) obj).getActualTypeArguments());
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder(theOwnerType.toString());
			str.append('<');
			StringUtils.print(str, ", ", Arrays.asList(theTypeArgs), StringBuilder::append);
			str.append('>');
			return str.toString();
		}
	}

	private static class WildcardTypeImpl implements WildcardType {
		private final Type[] theLowerBounds;
		private final Type[] theUpperBounds;

		public WildcardTypeImpl() {
			this(new Type[0], new Type[0]);
		}

		public WildcardTypeImpl(Type[] lowerBounds, Type[] upperBounds) {
			theLowerBounds = lowerBounds;
			theUpperBounds = upperBounds;
		}

		@Override
		public Type[] getUpperBounds() {
			return theUpperBounds;
		}

		@Override
		public Type[] getLowerBounds() {
			return theLowerBounds;
		}
	}

	private static class TypeVariableImpl<D extends GenericDeclaration> implements TypeVariable<D> {
		private final D theDeclaration;
		private final String theName;

		TypeVariableImpl(D declaration, String name) {
			theDeclaration = declaration;
			theName = name;
		}

		@Override
		public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
			return null;
		}

		@Override
		public Annotation[] getAnnotations() {
			return new Annotation[0];
		}

		@Override
		public Annotation[] getDeclaredAnnotations() {
			return new Annotation[0];
		}

		@Override
		public Type[] getBounds() {
			return new Type[0];
		}

		@Override
		public D getGenericDeclaration() {
			return theDeclaration;
		}

		@Override
		public String getName() {
			return theName;
		}

		@Override
		public AnnotatedType[] getAnnotatedBounds() {
			return new AnnotatedType[0];
		}

		@Override
		public int hashCode() {
			return theName.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof TypeVariableImpl && theDeclaration.equals(((TypeVariableImpl<?>) obj).theDeclaration)
				&& theName.equals(((TypeVariableImpl<?>) obj).theName);
		}

		@Override
		public String toString() {
			return theName;
		}
	}
}
