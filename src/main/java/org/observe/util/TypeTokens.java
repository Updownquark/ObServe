package org.observe.util;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import org.qommons.ClassMap;
import org.qommons.QommonsUtils;
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
		private int theComplexity = -1;
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
		 * @return A number representing the specificity of this type, i.e. how many things it extends/implements. The number is not
		 *         quantitative, but qualitative--it doesn't represent an exact value relating to anything
		 */
		public int getSpecificity() {
			if (theComplexity >= 0)
				return theComplexity;
			Class<?> unwrapped = unwrap(clazz);
			if (unwrapped.isPrimitive()) {
				if (unwrapped == void.class)
					theComplexity = 0;
				else if (unwrapped == double.class)
					theComplexity = 1;
				else if (unwrapped == float.class)
					theComplexity = 2;
				else if (unwrapped == long.class)
					theComplexity = 3;
				else if (unwrapped == int.class)
					theComplexity = 4;
				else if (unwrapped == short.class)
					theComplexity = 5;
				else if (unwrapped == byte.class)
					theComplexity = 6;
				else if (unwrapped == char.class)
					theComplexity = 7;
				else if (unwrapped == boolean.class)
					theComplexity = 8;
				else
					throw new IllegalStateException("Unaccounted primitive " + clazz);
				return theComplexity;
			} else if (clazz == Object.class)
				return theComplexity = 0;
			else if (clazz.isInterface())
				return theComplexity = 10 + addIntfs(clazz, new HashSet<>(Arrays.asList(clazz))).size();
			else
				return theComplexity = keyFor(clazz.getSuperclass()).getSpecificity() + 1 + addIntfs(clazz, new HashSet<>()).size();
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
		 * @param parameters The type parameters to parameterize this type with
		 * @return A type token with this key's type as its raw type and the given parameters
		 * @throws IllegalArgumentException If the number of parameters does not match this type's parameter count
		 */
		public <C extends T> TypeToken<C> parameterized(Type... parameters) {
			if (typeParameters == 0 || parameters.length != typeParameters)
				throw new IllegalArgumentException("Type " + clazz.getName() + " has " + typeParameters
					+ " parameters; cannot be parameterized with " + Arrays.toString(parameters));
			TypeToken<?>[] paramTokens = new TypeToken[parameters.length];
			for (int i = 0; i < paramTokens.length; i++)
				paramTokens[i] = of(wrap(parameters[i]));
			return parameterized(parameters, paramTokens, paramTypes -> TypeToken.of(new ParameterizedTypeImpl(clazz, parameters)));
		}

		/**
		 * @param parameters The type parameters to parameterize this type with
		 * @return A type token with this key's type as its raw type and the given parameters
		 * @throws IllegalArgumentException If the number of parameters does not match this type's parameter count
		 */
		public <C extends T> TypeToken<C> parameterized(TypeToken<?>... parameters) {
			if (typeParameters == 0 || parameters.length != typeParameters)
				throw new IllegalArgumentException("Type " + clazz.getName() + " has " + typeParameters
					+ " parameters; cannot be parameterized with " + Arrays.toString(parameters));
			Type[] paramTypes = new Type[parameters.length];
			for (int i = 0; i < paramTypes.length; i++)
				paramTypes[i] = wrap(parameters[i].getType());
			return parameterized(paramTypes, parameters, __ -> TypeToken.of(new ParameterizedTypeImpl(clazz, paramTypes)));
		}

		private <P, C extends T> TypeToken<C> parameterized(Type[] paramTypes, TypeToken<?>[] paramTokens,
			Function<Type[], TypeToken<?>> creator) {
			for (int t = 0; t < paramTokens.length; t++) {
				paramTypes[t] = wrap(paramTypes[t]);
				paramTokens[t] = wrap(paramTokens[t]);
			}
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

	/**
	 * May be {@link TypeTokens#addClassRetriever(TypeRetriever) added} to {@link TypeTokens} to enable retrieval of non-Class types or
	 * types that may not be retrievable by the {@link TypeTokens} class using {@link Class#forName(String)}
	 */
	public interface TypeRetriever {
		/**
		 * Retrieves the type if it is recognized. Null should be returned if the name is not recognized. Instances of this interface should
		 * never throw exceptions.
		 *
		 * @param typeName The name of the type to retrieve
		 * @return The type with the given name, or null if this retriever does not recognize the given type
		 */
		Type getType(String typeName);
	}

	public interface SupplementaryCast<S, T> {
		TypeToken<? extends T> getCastType(TypeToken<? extends S> sourceType);

		T cast(S source);
	}

	private final ConcurrentHashMap<Class<?>, TypeKey<?>> TYPES;
	private final Set<TypeRetriever> theClassRetrievers;
	private final ClassMap<ClassMap<SupplementaryCast<?, ?>>> theSupplementaryCasts;

	/** TypeToken&lt;String> */
	public final TypeToken<String> STRING;
	/** TypeToken&lt;Boolean> */
	public final TypeToken<Boolean> BOOLEAN;
	/** TypeToken&lt;boolean> */
	public final TypeToken<Boolean> PR_BOOLEAN;
	/** TypeToken&lt;Double> */
	public final TypeToken<Double> DOUBLE;
	/** TypeToken&lt;double> */
	public final TypeToken<Double> PR_DOUBLE;
	/** TypeToken&lt;Float> */
	public final TypeToken<Float> FLOAT;
	/** TypeToken&lt;float> */
	public final TypeToken<Float> PR_FLOAT;
	/** TypeToken&lt;Long> */
	public final TypeToken<Long> LONG;
	/** TypeToken&lt;long> */
	public final TypeToken<Long> PR_LONG;
	/** TypeToken&lt;Integer> */
	public final TypeToken<Integer> INT;
	/** TypeToken&lt;int> */
	public final TypeToken<Integer> PR_INT;
	/** TypeToken&lt;Short> */
	public final TypeToken<Short> SHORT;
	/** TypeToken&lt;short> */
	public final TypeToken<Short> PR_SHORT;
	/** TypeToken&lt;Byte> */
	public final TypeToken<Byte> BYTE;
	/** TypeToken&lt;byte> */
	public final TypeToken<Byte> PR_BYTE;
	/** TypeToken&lt;Character> */
	public final TypeToken<Character> CHAR;
	/** TypeToken&lt;char> */
	public final TypeToken<Character> PR_CHAR;
	/** TypeToken&lt;Object> */
	public final TypeToken<Object> OBJECT;
	/** TypeToken&lt;Void> */
	public final TypeToken<Void> VOID;
	/** TypeToken&lt;void> */
	public final TypeToken<Void> PR_VOID;
	/** TypeToken&lt;?> */
	public final TypeToken<?> WILDCARD;
	/** TypeToken&lt;Class&lt;?>> */
	public final TypeToken<Class<?>> CLASS;

	/** Creates a new instance */
	protected TypeTokens() {
		TYPES = new ConcurrentHashMap<>();
		theClassRetrievers = new LinkedHashSet<>();
		theSupplementaryCasts = new ClassMap<>();

		STRING = of(String.class);
		BOOLEAN = of(Boolean.class);
		PR_BOOLEAN = of(boolean.class);
		DOUBLE = of(Double.class);
		PR_DOUBLE = of(double.class);
		FLOAT = of(Float.class);
		PR_FLOAT = of(float.class);
		LONG = of(Long.class);
		PR_LONG = of(long.class);
		INT = of(Integer.class);
		PR_INT = of(int.class);
		SHORT = of(Short.class);
		PR_SHORT = of(short.class);
		BYTE = of(Byte.class);
		PR_BYTE = of(byte.class);
		CHAR = of(Character.class);
		PR_CHAR = of(char.class);
		OBJECT = of(Object.class);
		VOID = of(Void.class);
		PR_VOID = of(void.class);
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
		if (type == null)
			throw new NullPointerException();
		if (type instanceof Class)
			return of((Class<?>) type);
		else if (type instanceof ParameterizedType && ((ParameterizedType) type).getOwnerType() instanceof Class)
			return keyFor((Class<?>) ((ParameterizedType) type).getOwnerType())
				.parameterized(((ParameterizedType) type).getActualTypeArguments());
		else
			return TypeToken.of(type);
	}

	/**
	 * @param classRetriever The class retriever to retrieve classes by name (for {@link #parseType(String)}
	 * @return This instance
	 */
	public TypeTokens addClassRetriever(TypeRetriever classRetriever) {
		theClassRetrievers.add(classRetriever);
		return this;
	}

	/**
	 * @param classRetriever The class retriever (added with {@link #addClassRetriever(TypeRetriever)}) to remove
	 * @return Whether the class retriever was found in the list
	 */
	public boolean removeClassRetriever(TypeRetriever classRetriever) {
		return theClassRetrievers.remove(classRetriever);
	}

	public <S, T> TypeTokens addSupplementaryCast(Class<S> sourceType, Class<T> targetType, SupplementaryCast<S, T> cast) {
		theSupplementaryCasts.computeIfAbsent(targetType, ClassMap::new).put(sourceType, cast);
		return this;
	}

	public boolean removeSupplementaryCast(Class<?> sourceType, Class<?> targetType, SupplementaryCast<?, ?> cast) {
		ClassMap<SupplementaryCast<?, ?>> srcCasts = theSupplementaryCasts.get(targetType, ClassMap.TypeMatch.EXACT);
		boolean[] match = new boolean[1];
		srcCasts.compute(sourceType, existing -> {
			if (existing == cast) {
				match[0] = true;
				return null;
			} else
				return existing;
		});
		return match[0];
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
					return Object.class;
				}
			} else if (t instanceof TypeVariable) {
				Type[] bounds = ((TypeVariable<?>) t).getBounds();
				if (bounds.length > 0) {
					t = bounds[0];
				} else {
					return Object.class;
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
	 * @param <T> The type of the value to get
	 * @param type The type to get a default value for
	 * @return A default value to use for the given type
	 */
	public <T> T getDefaultValue(TypeToken<T> type) {
		return getDefaultValue(getRawType(type));
	}

	/**
	 * @param <T> The type of the value to get
	 * @param type The type to get a default value for
	 * @return A default value to use for the given type
	 */
	public <T> T getDefaultValue(Class<T> type) {
		if (!type.isPrimitive())
			return null;
		else if (type == void.class)
			return null;
		else if (type == boolean.class)
			return (T) Boolean.FALSE;
		else if (type == char.class)
			return (T) Character.valueOf((char) 0);
		else if (type == byte.class)
			return (T) Byte.valueOf((byte) 0);
		else if (type == short.class)
			return (T) Short.valueOf((short) 0);
		else if (type == int.class)
			return (T) Integer.valueOf(0);
		else if (type == long.class)
			return (T) Long.valueOf(0L);
		else if (type == float.class)
			return (T) Float.valueOf(0.0f);
		else if (type == double.class)
			return (T) Double.valueOf(0.0);
		else
			throw new IllegalStateException("Unrecognized primitive type: " + type);
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
		else if (type == void.class)
			return (Class<T>) Void.class;
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
		else if (type == Integer.class)
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
		else if (type == Void.class)
			return (Class<T>) void.class;
		else
			return type;
	}

	/**
	 * @param type The type to wrap
	 * @return The non-primitive wrapper class corresponding to the given primitive type, or the input if it is not primitive
	 */
	public Type wrap(Type type) {
		if (type instanceof Class)
			return wrap((Class<?>) type);
		else
			return type;
	}

	/**
	 * @param type The type to unwrap
	 * @return The primitive type corresponding to the given primitive wrapper class, or the input if it is not a primitive wrapper
	 */
	public Type unwrap(Type type) {
		if (type instanceof Class)
			return unwrap((Class<?>) type);
		else
			return type;
	}

	/**
	 * @param type The type to wrap
	 * @return The non-primitive wrapper class corresponding to the given primitive type, or the input if it is not primitive
	 */
	public <T> TypeToken<T> wrap(TypeToken<T> type) {
		Class<T> raw = getRawType(type);
		if (raw == null)
			return (TypeToken<T>) OBJECT;
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
		if (raw == null)
			return (TypeToken<T>) OBJECT;
		Class<T> unwrapped = unwrap(raw);
		if (unwrapped == raw)
			return type;
		else
			return of(unwrapped);
	}

	/**
	 * @param <T> The type of value to get
	 * @param type The type of value to get
	 * @return A default value for the given type, accommodating primitives, which cannot be null
	 */
	public <T> T getPrimitiveDefault(TypeToken<T> type) {
		return getPrimitiveDefault(getRawType(type));
	}

	/**
	 * @param <T> The type of value to get
	 * @param type The type of value to get
	 * @return A default value for the given type, accommodating primitives, which cannot be null
	 */
	public <T> T getPrimitiveDefault(Class<T> type) {
		type = unwrap(type);
		if (type == boolean.class)
			return (T) Boolean.FALSE;
		else if (type == int.class)
			return (T) Integer.valueOf(0);
		else if (type == long.class)
			return (T) Long.valueOf(0);
		else if (type == double.class)
			return (T) Double.valueOf(0);
		else if (type == float.class)
			return (T) Float.valueOf(0);
		else if (type == char.class)
			return (T) Character.valueOf(' ');
		else if (type == byte.class)
			return (T) Byte.valueOf((byte) 0);
		else if (type == short.class)
			return (T) Short.valueOf((short) 0);
		else
			return null;
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

	private final ThreadLocal<Set<TypeVariable<?>>> ASSIGNABLE_VARIABLE_STACK = ThreadLocal.withInitial(HashSet::new);

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
		Class<?> rawRight = unwrap(getRawType(right));
		Class<?> rawLeft = unwrap(getRawType(left));
		if (rawLeft == Object.class)
			return true;
		else if (rawRight.isPrimitive()) {
			if (rawRight.equals(rawLeft))
				return true;
			else if (rawRight == boolean.class)
				return false;
			else if (rawLeft == Number.class)
				return true;
			else if (!rawLeft.isPrimitive())
				return false;
			else if (rawLeft == double.class)
				return true;
			// Now left!=right and left!=double.class
			else if (rawRight == double.class || rawRight == float.class || rawRight == long.class)
				return false;
			// Now right can only be int, short, byte, or char
			else if (rawLeft == long.class || rawLeft == int.class)
				return true;
			else if (rawRight == byte.class)
				return rawLeft == short.class || rawLeft == char.class;
		}

		// Neither is primitive
		if (left.getType() instanceof Class)
			return false; // Simple type, not compatible
		else if (left.getType() instanceof TypeVariable) {
			if (!ASSIGNABLE_VARIABLE_STACK.get().add((TypeVariable<?>) left.getType()))
				return true; // Recursive, already checked
			for (Type bound : ((TypeVariable<?>) left.getType()).getBounds()) {
				TypeToken<?> boundToken = left.resolveType(bound);
				if (!isAssignable(boundToken, right))
					return false;
			}
			return true;
		} else if (left.getType() instanceof WildcardType) {
			for (Type bound : ((WildcardType) left.getType()).getUpperBounds()) {
				TypeToken<?> boundToken = left.resolveType(bound);
				if (!isAssignable(boundToken, right))
					return false;
			}
			for (Type bound : ((WildcardType) left.getType()).getLowerBounds()) {
				TypeToken<?> boundToken = left.resolveType(bound);
				if (!isAssignable(right, boundToken))
					return false;
			}
			return true;
		} else if (left.getType() instanceof ParameterizedType) {
			if (!rawLeft.isAssignableFrom(rawRight))
				return false;
			for (int p = 0; p < rawLeft.getTypeParameters().length; p++) {
				TypeToken<?> leftTP = left.resolveType(rawLeft.getTypeParameters()[p]);
				TypeToken<?> rightTP = right.resolveType(rawLeft.getTypeParameters()[p]);

				if (!isAssignable(leftTP, rightTP))
					return false;
			}
			return true;
		} else if (left.isArray()) {
			return right.isArray() && isAssignable(left.getComponentType(), right.getComponentType());
		} else
			return false; // Dunno what it could be, but we can't resolve it
	}

	/**
	 * Converts from one type to another
	 *
	 * @param <S> The source type
	 * @param <T> The target type
	 */
	public static class TypeConverter<S, T> implements Function<S, T> {
		private final TypeToken<T> theConvertedType;
		private final Function<S, T> theConverter;
		private final String theName;

		TypeConverter(TypeToken<T> convertedType, String name, Function<S, T> converter) {
			theConvertedType = convertedType;
			theConverter = converter;
			theName = name;
		}

		/** @return The {@link TypeToken} of the target type */
		public TypeToken<T> getConvertedType() {
			return theConvertedType;
		}

		@Override
		public T apply(S t) {
			return theConverter.apply(t);
		}

		@Override
		public int hashCode() {
			return theConverter.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof TypeConverter && theConverter.equals(((TypeConverter<?, ?>) obj).theConverter);
		}

		@Override
		public String toString() {
			return theName;
		}
	}

	/**
	 * @param <T> The compile-time type to cast to
	 * @param <X> The compile-time type to cast from
	 * @param left The type to cast to
	 * @param right The type to cast from
	 * @return A function that takes an instance of the right type and returns it as an instance of the left type, throwing a
	 *         {@link ClassCastException} if the value is neither null nor an instance of the left type, or a {@link NullPointerException}
	 *         if the value is null and the left type is primitive
	 * @throws IllegalArgumentException If values of the right type cannot be cast to the left type in general
	 */
	public <T, X> TypeConverter<T, X> getCast(TypeToken<T> left, TypeToken<X> right) throws IllegalArgumentException {
		return getCast(left, right, false);
	}

	/** Values to use for primitive types in place of null if <code>safe==true</code> for {@link #getCast(TypeToken, TypeToken, boolean)} */
	public static final Map<Class<?>, Object> SAFE_VALUES = Collections.unmodifiableMap(QommonsUtils.<Class<?>, Object> buildMap(null)//
		.with(boolean.class, false)//
		.with(char.class, (char) 0)//
		.with(byte.class, (byte) 0)//
		.with(short.class, (short) 0)//
		.with(int.class, 0)//
		.with(long.class, 0L)//
		.with(float.class, 0.0f)//
		.with(double.class, 0.0)//
		.get());

	/**
	 * @param <S> The compile-time type to cast from
	 * @param <T> The compile-time type to cast to
	 * @param source The type to cast from
	 * @param target The type to cast to
	 * @param safe For primitive right types, whether to use a safe value (0 or false) if the left value is null, as opposed to throwing a
	 *        {@link NullPointerException}
	 * @return A function that takes an instance of the right type and returns it as an instance of the left type, throwing a a
	 *         {@link NullPointerException} if the right value is null and the left type is primitive
	 * @throws IllegalArgumentException If values of the right type cannot be cast to the left type in general
	 */
	public <S, T> TypeConverter<S, T> getCast(TypeToken<S> source, TypeToken<T> target, boolean safe) throws IllegalArgumentException {
		return getCast(source, target, safe, true);
	}

	/**
	 * @param <S> The compile-time type to cast from
	 * @param <T> The compile-time type to cast to
	 * @param source The type to cast from
	 * @param target The type to cast to
	 * @param safe For primitive right types, whether to use a safe value (0 or false) if the left value is null, as opposed to throwing a
	 *        {@link NullPointerException}
	 * @param downCastOnly Whether to only allow down casts (e.g. int->double) or to also facilitate upcasts (e.g. double->int)
	 * @return A function that takes an instance of the right type and returns it as an instance of the left type, throwing a a
	 *         {@link NullPointerException} if the right value is null and the left type is primitive
	 * @throws IllegalArgumentException If values of the right type cannot be cast to the left type in general
	 */
	public <S, T> TypeConverter<S, T> getCast(TypeToken<S> source, TypeToken<T> target, boolean safe, boolean downCastOnly)
		throws IllegalArgumentException {

		if (target.getType() instanceof TypeVariable && ((TypeVariable<?>) target.getType()).getBounds().length == 1)
			target = (TypeToken<T>) of(((TypeVariable<?>) target.getType()).getBounds()[0]);
		if (target.isAssignableFrom(source)) {
			if (target.isPrimitive() && !source.isPrimitive()) {
				T safeValue = safe ? (T) SAFE_VALUES.get(getRawType(source)) : null;
				return new TypeConverter<>((TypeToken<T>) source, "primitive-safe-cast", v -> {
					if (v == null) {
						if (safeValue != null)
							return safeValue;
						throw new NullPointerException("Cannot cast null to primitive type " + source);
					}
					return (T) v;
				});
			} else
				return new TypeConverter<>((TypeToken<T>) source, "no-op", v -> (T) v);
		}
		Class<S> wrappedSource = getRawType(wrap(source));
		Class<T> wrappedTarget = getRawType(wrap(target));
		if (target.isArray()) {
			if (!source.isArray() || source.isPrimitive() || target.isPrimitive())
				return specialCast(source, target, wrappedSource, wrappedTarget);
			try {
				getCast(source.getComponentType(), target.getComponentType()); // Don't need the actual cast, just the check
				return new TypeConverter<>((TypeToken<T>) source, "no-op", v -> (T) v);
			} catch (IllegalArgumentException e) {
				return specialCast(source, target, wrappedSource, wrappedTarget);
			}
		}
		Class<?> primitiveTarget = getRawType(unwrap(target));
		Class<?> primitiveSource = getRawType(unwrap(source));
		UnaryOperator<S> nullCheck;
		if (target.isPrimitive() && !source.isPrimitive()) {
			S safeValue = safe ? (S) SAFE_VALUES.get(primitiveSource) : null;
			nullCheck = v -> {
				if (v == null) {
					if (safeValue != null)
						return safeValue;
					throw new NullPointerException("Cannot cast null to primitive type " + source);
				}
				return v;
			};
		} else {
			nullCheck = v -> v;
		}
		if (primitiveSource.isPrimitive()) {
			if (primitiveSource == primitiveTarget)
				return new TypeConverter<>((TypeToken<T>) source, "wrapping-cast", v -> (T) nullCheck.apply(v));
			else if (wrappedTarget.isAssignableFrom(wrappedSource))
				return new TypeConverter<>((TypeToken<T>) TypeTokens.get().of(wrappedSource), "wrapping-cast", v -> (T) nullCheck.apply(v));
			else if (!primitiveTarget.isPrimitive())
				return specialCast(source, target, wrappedSource, wrappedTarget);
			else if (primitiveSource == boolean.class)
				return specialCast(source, target, wrappedSource, wrappedTarget);
			Function<S, Number> toNumber;
			if (primitiveSource == char.class) {
				toNumber = v -> Integer.valueOf((Character) nullCheck.apply(v));
			} else {
				toNumber = v -> (Number) nullCheck.apply(v);
			}
			Function<S, Object> fn;
			if (primitiveTarget == double.class) {
				fn = v -> {
					Number n = toNumber.apply(v);
					return n == null ? null : n.doubleValue();
				};
				// Now left!=right and left!=double.class
			} else if (primitiveSource == double.class || primitiveSource == float.class || primitiveSource == long.class)
				fn = null;
			// Now right can only be int, short, byte, or char
			else if (primitiveTarget == long.class) {
				fn = v -> {
					Number n = toNumber.apply(v);
					return n == null ? null : n.longValue();
				};
			} else if (primitiveTarget == int.class) {
				fn = v -> {
					Number n = toNumber.apply(v);
					return n == null ? null : n.intValue();
				};
			} else if (primitiveSource == byte.class) {
				if (primitiveTarget == short.class) {
					fn = v -> {
						Number n = toNumber.apply(v);
						return n == null ? null : n.shortValue();
					};
				} else if (primitiveTarget == char.class) {
					fn = v -> {
						Number n = toNumber.apply(v);
						if (n == null)
							return null;
						else
							return Character.valueOf((char) n.byteValue());
					};
				} else
					fn = null;
			} else
				fn = null;

			if (fn == null) {
				if (downCastOnly)
					throw new IllegalArgumentException("Cannot cast " + target + " to " + source);
				if (primitiveTarget == float.class) {
					fn = v -> {
						Number n = toNumber.apply(v);
						return n == null ? null : n.floatValue();
					};
				} else if (primitiveTarget == long.class) {
					fn = v -> {
						Number n = toNumber.apply(v);
						return n == null ? null : n.longValue();
					};
				} else if (primitiveTarget == int.class) {
					fn = v -> {
						Number n = toNumber.apply(v);
						return n == null ? null : n.intValue();
					};
				} else if (primitiveTarget == short.class) {
					fn = v -> {
						Number n = toNumber.apply(v);
						return n == null ? null : n.shortValue();
					};
				} else if (primitiveTarget == byte.class) {
					fn = v -> {
						Number n = toNumber.apply(v);
						return n == null ? null : n.byteValue();
					};
				} else if (primitiveTarget == char.class) {
					fn = v -> {
						Number n = toNumber.apply(v);
						return n == null ? null : Character.valueOf((char) n.intValue());
					};
				}
			}
			return new TypeConverter<>(target, "primitive-cast", (Function<S, T>) fn);
		} else if (isAssignable(target, source))
			return new TypeConverter<>((TypeToken<T>) source, "no-op", v -> (T) v);
		else
			return specialCast(source, target, wrappedSource, wrappedTarget);
	}

	private <S, T> TypeConverter<S, T> specialCast(TypeToken<S> sourceType, TypeToken<T> targetType, Class<S> sourceClass,
		Class<T> targetClass) {
		ClassMap<SupplementaryCast<?, ?>> sourceCasts = theSupplementaryCasts.get(targetClass, ClassMap.TypeMatch.SUB_TYPE);
		SupplementaryCast<S, T> suppCast = sourceCasts == null ? null
			: (SupplementaryCast<S, T>) sourceCasts.get(sourceClass, ClassMap.TypeMatch.SUPER_TYPE);
		if (suppCast != null)
			return new TypeConverter<>((TypeToken<T>) suppCast.getCastType(sourceType), suppCast.toString(), suppCast::cast);
		throw new IllegalArgumentException("Cannot cast " + sourceType + " to " + targetType);
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
			while (c != Object.class) {
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
	 * <p>
	 * A class representing a set of method parameters.
	 * </p>
	 * <p>
	 * This class facilitates determination of whether invocation of a parameterized method is valid with a given set of argument types as
	 * well as determination of the return type of the method for a given set of argument types.
	 * </p>
	 */
	public class TypeVariableAccumulation {
		private final Map<TypeVariable<?>, TypeToken<?>[]> theAccumulatedTypes;
		private final TypeToken<?> theResolver;

		TypeVariableAccumulation(TypeVariable<?>[] variables, TypeToken<?> resolver) {
			theAccumulatedTypes = new LinkedHashMap<>();
			theResolver = resolver;
			for (TypeVariable<?> vbl : variables)
				theAccumulatedTypes.put(vbl, new TypeToken[] { null, null });
		}

		private TypeToken<?> resolveInternal(Type type) {
			return theResolver == null ? of(type) : theResolver.resolveType(type);
		}

		/**
		 * Called to check an argument type against a method parameter
		 *
		 * @param parameterType The type of the method's parameter
		 * @param evaluatedType The type of the argument that is supplied for the parameter
		 * @return Whether the argument type is valid for the given parameter type in the parameterized method
		 */
		public boolean accumulate(TypeToken<?> parameterType, TypeToken<?> evaluatedType) {
			return accumulate(parameterType, evaluatedType, true);
		}

		private boolean accumulate(TypeToken<?> parameterType, TypeToken<?> evaluatedType, boolean upperBound) {
			if (theAccumulatedTypes.isEmpty())
				return true;
			if (parameterType.getType() instanceof TypeVariable<?>) {
				TypeToken<?>[] accumulated = theAccumulatedTypes.get(parameterType.getType());
				if (accumulated != null) {
					if (upperBound) {
						TypeToken<?> newAccum = accumulated[0] == null ? evaluatedType : getCommonType(accumulated[0], evaluatedType);
						if (newAccum != null && isAssignable(parameterType, newAccum)) {
							accumulated[0] = newAccum;
							return true;
						} else
							return false;
					} else {
						TypeToken<?> newAccum = accumulated[1] == null ? evaluatedType : getCommonType(accumulated[1], evaluatedType);
						if (newAccum != null && isAssignable(newAccum, parameterType)) {
							accumulated[1] = newAccum;
							return true;
						} else
							return false;
					}
				} else {
					for (Type bound : ((TypeVariable<?>) parameterType.getType()).getBounds()) {
						if (!accumulate(resolveInternal(bound), evaluatedType, upperBound))
							return false;
					}
					return true;
				}
			} else if (parameterType.getType() instanceof WildcardType) {
				for (Type bound : ((WildcardType) parameterType.getType()).getLowerBounds()) {
					if (!accumulate(resolveInternal(bound), evaluatedType, !upperBound))
						return false;
				}
				for (Type bound : ((WildcardType) parameterType.getType()).getUpperBounds()) {
					if (!accumulate(resolveInternal(bound), evaluatedType, upperBound))
						return false;
				}
				return true;
			} else if (parameterType.getType() instanceof ParameterizedType) {
				ParameterizedType pt = (ParameterizedType) parameterType.getType();
				if (pt.getRawType() instanceof Class) {
					Class<?> raw = (Class<?>) pt.getRawType();
					for (int t = 0; t < pt.getActualTypeArguments().length; t++) {
						Type tp = raw.getTypeParameters()[t];
						if (!accumulate(parameterType.resolveType(tp), evaluatedType.resolveType(tp), upperBound))
							return false;
					}
				} else
					return false; // Don't know how to handle non-class raw types
				return true;
			} else if (parameterType.isArray() && evaluatedType.isArray())
				return accumulate(parameterType.getComponentType(), evaluatedType.getComponentType());
			else
				return true;
		}

		/**
		 * Typically used to resolve the return type of a parameterized method given a set of argument types (provided via
		 * {@link #accumulate(TypeToken, TypeToken)})
		 *
		 * @param type The generic return type of the method
		 * @return The return type of the method, given the argument types provided via {@link #accumulate(TypeToken, TypeToken)}
		 */
		public TypeToken<?> resolve(Type type) {
			if (type instanceof TypeVariable) {
				TypeToken<?>[] accumulated = theAccumulatedTypes.get(type);
				return accumulated == null ? resolveInternal(type) : accumulated[0];
			} else if (type instanceof WildcardType) {
				WildcardType wild = (WildcardType) type;
				Type[] upper = new Type[wild.getUpperBounds().length], lower = new Type[wild.getLowerBounds().length];
				for (int b = 0; b < upper.length; b++)
					upper[b] = resolve(wild.getUpperBounds()[b]).getType();
				for (int b = 0; b < lower.length; b++)
					lower[b] = resolve(wild.getLowerBounds()[b]).getType();
				return of(new WildcardTypeImpl(lower, upper));
			} else if (type instanceof ParameterizedType) {
				ParameterizedType pt = (ParameterizedType) type;
				Type[] pts = new Type[pt.getActualTypeArguments().length];
				for (int p = 0; p < pts.length; p++)
					pts[p] = resolve(pt.getActualTypeArguments()[p]).getType();
				return of(new ParameterizedTypeImpl(//
					resolve(pt.getRawType()).getType(), //
					pts));
			} else if (type instanceof GenericArrayType)
				return getArrayType(resolve(//
					((GenericArrayType) type).getGenericComponentType()), 1);
			else
				return resolveInternal(type);
		}

		@Override
		public String toString() {
			if (theAccumulatedTypes.size() == 1)
				return print(theAccumulatedTypes.entrySet().iterator().next(), new StringBuilder()).toString();
			else {
				StringBuilder str = new StringBuilder("{");
				for (Map.Entry<TypeVariable<?>, TypeToken<?>[]> accum : theAccumulatedTypes.entrySet())
					print(accum, str.append("\n\t"));
				return str.append('}').toString();
			}
		}

		private StringBuilder print(Map.Entry<TypeVariable<?>, TypeToken<?>[]> accum, StringBuilder str) {
			str.append(accum.getKey().getName()).append('=');
			if (accum.getValue()[0] == null) {
				if (accum.getValue()[1] == null)
					str.append('?');
				else
					str.append("? super ").append(accum.getValue()[1]);
			} else if (accum.getValue()[1] == null) {
				str.append("? extends ").append(accum.getValue()[0]);
			} else
				str.append(accum.getValue()[0]).append("...").append(accum.getValue()[1]);
			return str;
		}
	}

	/**
	 * Creates a type variable accumulator
	 *
	 * @param vbls The type variables of the method to invoke
	 * @param resolver A context type to use to resolve types, e.g. the object that the non-static method is being invoked on
	 * @return The type variable accumulator
	 */
	public TypeVariableAccumulation accumulate(TypeVariable<?>[] vbls, TypeToken<?> resolver) {
		return new TypeVariableAccumulation(vbls, resolver);
	}

	/**
	 * @param type The type to analyze
	 * @return The specificity of the type. Sub-types are more specific than super-types.
	 */
	public int getTypeSpecificity(Type type) {
		return getTypeSpecificity(type, new HashSet<>());
	}

	private int getTypeSpecificity(Type type, Set<Type> stack) {
		if (!stack.add(type))
			return 0;
		if (type instanceof Class)
			return keyFor((Class<?>) type).getSpecificity();
		else if (type instanceof ParameterizedType) {
			ParameterizedType pt = (ParameterizedType) type;
			int complexity = getTypeSpecificity(pt.getRawType());
			for (Type p : pt.getActualTypeArguments())
				complexity += getTypeSpecificity(p, stack);
			return complexity;
		} else if (type instanceof GenericArrayType)
			return getTypeSpecificity(((GenericArrayType) type).getGenericComponentType(), stack) + 1;
		else if (type instanceof TypeVariable) {
			int complexity = 0;
			for (Type bound : ((TypeVariable<?>) type).getBounds())
				complexity += getTypeSpecificity(bound, stack);
			return complexity;
		} else if (type instanceof WildcardType) {
			int complexity = 0;
			for (Type bound : ((WildcardType) type).getLowerBounds())
				complexity += getTypeSpecificity(bound, stack);
			for (Type bound : ((WildcardType) type).getUpperBounds())
				complexity += getTypeSpecificity(bound, stack);
			return complexity;
		} else
			throw new IllegalArgumentException("Unrecognized type: " + type);
	}

	static Set<Class<?>> addIntfs(Class<?> clazz, Set<Class<?>> intfs) {
		for (Class<?> intf : clazz.getInterfaces()) {
			if (clazz.getSuperclass() != null && intf.isAssignableFrom(clazz.getSuperclass())) {//
			} else if (intfs.add(intf))
				addIntfs(intf, intfs);
		}
		return intfs;
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
			type = (TypeToken<X>) new TypeToken<X[]>() {
			}.where(new TypeParameter<X>() {
			}, type);
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

	/**
	 * @param type The type to test
	 * @return Whether the type is a trival one, like ? extends Object
	 */
	public boolean isTrivialType(Type type) {
		if (type instanceof WildcardType) {
			WildcardType wildcard = (WildcardType) type;
			if (wildcard.getLowerBounds().length > 0)
				return false;
			for (Type bound : wildcard.getUpperBounds()) {
				if (bound instanceof Class) {
					if (bound != Object.class)
						return false;
				} else if (!isTrivialType(bound))
					return false;
			}
		} else if (type instanceof TypeVariable) {
			TypeVariable<?> vbl = (TypeVariable<?>) type;
			for (Type bound : vbl.getBounds()) {
				if (bound instanceof Class) {
					if (bound != Object.class)
						return false;
				} else if (!isTrivialType(bound))
					return false;
			}
		}
		return false;
	}

	/**
	 * Parses a type from a string
	 *
	 * @param typeName The string to parse
	 * @return The parsed type
	 * @throws ParseException If the string cannot be parsed as a type
	 */
	public TypeToken<?> parseType(String typeName) throws ParseException {
		return parseType(typeName, 0);
	}

	/**
	 * Parses a type from a string
	 *
	 * @param typeName The string to parse
	 * @param offset The offset to add to the {@link ParseException#getErrorOffset() error offset} if a {@link ParseException} must be
	 *        thrown
	 * @return The parsed type
	 * @throws ParseException If the string cannot be parsed as a type
	 */
	public TypeToken<?> parseType(String typeName, int offset) throws ParseException {
		StringBuilder name = new StringBuilder();
		List<TypeToken<?>> componentTypes = null;
		int componentStart = 0, depth = 0, firstComponentStart = -1;
		boolean wasWS = false;
		Boolean extendsOrSuper = null;
		int array = 0;
		for (int c = 0; c < typeName.length(); c++) {
			switch (typeName.charAt(c)) {
			case '<':
				if (name.length() == 0)
					throw new ParseException("Unexpected '" + typeName.charAt(c) + "'", offset + c);
				if (depth == 0) {
					if (componentTypes != null)
						throw new ParseException("Unexpected '" + typeName.charAt(c) + "'", offset + c);
					componentStart = c + 1;
				}
				depth++;
				break;
			case '[':
				if (name.length() == 0)
					throw new ParseException("Unexpected '" + typeName.charAt(c) + "'", offset + c);
				if (depth == 0) {
					array++;
				}
				depth++;
				break;
			case '>':
			case ']':
				if (depth == 0)
					throw new ParseException("Unexpected '" + typeName.charAt(c) + "'", offset + c);
				depth--;
				if (depth == 0 && array == 0) {
					if (componentTypes == null)
						componentTypes = new ArrayList<>(4);
					componentTypes.add(//
						parseType(//
							typeName.substring(componentStart, c), offset + componentStart));
				}
				break;
			case ',':
				if (depth == 0) {
					throw new ParseException("Unexpected '" + typeName.charAt(c) + "'", offset + c);
				} else if (depth == 1) {
					if (componentTypes == null)
						componentTypes = new ArrayList<>(4);
					componentTypes.add(//
						parseType(//
							typeName.substring(componentStart, c), offset + componentStart));
					componentStart = c + 1;
				}
				break;
			case '?':
				if (depth == 0) {
					for (c++; c < typeName.length(); c++) {
						if (typeName.charAt(c) != ' ')
							break;
					}
					if (typeName.regionMatches(c, "extends", 0, "extends".length())) {
						extendsOrSuper = Boolean.TRUE;
						c += "extends".length();
					} else if (typeName.regionMatches(c, "super", 0, "super".length())) {
						extendsOrSuper = Boolean.FALSE;
						c += "super".length();
					} else if (typeName.length() == 1)
						return WILDCARD;
					else
						throw new ParseException("Expected 'extends' or 'super' after '?'", c);
				}
				break;
			case ' ':
				break;
			default:
				if (depth == 0) {
					if (array > 0)
						throw new ParseException("Unexpected content after array specification", offset + c);
					if ((wasWS || componentTypes != null) && name.length() > 0)
						throw new ParseException("Unexpected content after whitespace/component types", offset + c);
					name.append(typeName.charAt(c));
				}
				break;
			}
			wasWS = typeName.charAt(c) == ' ';
			if (wasWS) {
				wasWS = true;
				if (componentStart == c)
					componentStart++;
			}
		}
		Type baseType = parseClass(name.toString(), offset);
		TypeToken<?> type;
		if (componentTypes != null) {
			if (!(baseType instanceof Class))
				throw new ParseException("Only Class types may be parameterized, not " + baseType.getClass().getName() + " instances",
					firstComponentStart);
			type = keyFor((Class<?>) baseType).parameterized(componentTypes.toArray(new TypeToken[componentTypes.size()]));
		} else
			type = of(baseType);
		if (array > 0)
			type = getArrayType(type, array);
		if (extendsOrSuper != null) {
			type = extendsOrSuper ? getExtendsWildcard(type) : getSuperWildcard(type);
		}
		return type;
	}

	private Type parseClass(String typeName, int offset) throws ParseException {
		switch (typeName) {
		case "boolean":
			return boolean.class;
		case "char":
			return char.class;
		case "byte":
			return byte.class;
		case "short":
			return short.class;
		case "int":
			return int.class;
		case "long":
			return long.class;
		case "float":
			return float.class;
		case "double":
			return double.class;
		}
		for (TypeRetriever retriever : theClassRetrievers) {
			Type found = retriever.getType(typeName);
			if (found != null)
				return found;
		}
		try {
			return Class.forName(typeName);
		} catch (ClassNotFoundException e) {
			throw new ParseException("No such class found: " + typeName, offset);
		}
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
			ParameterizedType other = (ParameterizedType) obj;
			Type otherOwner = other.getOwnerType();
			if (otherOwner == null)
				otherOwner = other.getRawType();
			return theOwnerType.equals(otherOwner) && Arrays.equals(theTypeArgs, other.getActualTypeArguments());
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder(theOwnerType.getTypeName());
			str.append('<');
			StringUtils.print(str, ", ", Arrays.asList(theTypeArgs), (s, type) -> s.append(type.getTypeName()));
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

		@Override
		public int hashCode() {
			return Arrays.hashCode(theLowerBounds) * 3 + Arrays.hashCode(theUpperBounds);
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof WildcardType//
				&& Arrays.equals(theLowerBounds, ((WildcardType) obj).getLowerBounds())//
				&& Arrays.equals(theUpperBounds, ((WildcardType) obj).getUpperBounds());
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			str.append('?');
			if (theUpperBounds.length > 0)
				str.append(" extends ");
			for (int i = 0; i < theUpperBounds.length; i++) {
				if (i > 0)
					str.append(", ");
				str.append(theUpperBounds[i].getTypeName());
			}
			if (theLowerBounds.length > 0)
				str.append(" super ");
			for (int i = 0; i < theLowerBounds.length; i++) {
				if (i > 0)
					str.append(", ");
				str.append(theLowerBounds[i].getTypeName());
			}
			return str.toString();
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
