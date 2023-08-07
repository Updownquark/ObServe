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

import org.qommons.ClassMap;
import org.qommons.LambdaUtils;
import org.qommons.QommonsUtils;
import org.qommons.StringUtils;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

/**
 * It turns out that {@link TypeToken} is quite slow for many basic operations, like creation and {@link TypeToken#getRawType()
 * getRawType()}. This class provides some optimizations as well as boilerplate-reducing utilities.
 */
public class TypeTokens implements TypeParser {
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
		public final TypeConverter<T, T, T, T> unity;
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
			unity = new TypeConverter<>("no-op", "no-op", type, type, null, LambdaUtils.identity(), null, LambdaUtils.identity());
		}

		/**
		 * @return A number representing the specificity of this type, i.e. how many things it extends/implements. The number is not
		 *         quantitative, but qualitative--it doesn't represent an exact value relating to anything
		 */
		public int getSpecificity() {
			if (theComplexity < 0)
				theComplexity = computeSpecificity();
			return theComplexity;
		}

		int computeSpecificity() {
			if (clazz == Object.class)
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
			return obj instanceof TypeKey && clazz == ((TypeKey<?>) obj).clazz;
		}

		@Override
		public String toString() {
			return clazz.toString();
		}
	}

	private static final Function<Object, String> ALWAYS_NULL = LambdaUtils.constantFn(null, "null", null);

	public class PrimitiveTypeData<T> extends TypeKey<T> {
		public final Class<T> primitiveClass;
		public final TypeToken<T> primitiveType;
		public final TypeConverter<T, T, T, T> primitiveUnity;
		public final boolean number;
		public final boolean bool;
		public final boolean isVoid;
		public final boolean isChar;
		public final int size;
		public final T defaultValue;
		public final TypeConverter<T, T, T, T> safeCast;
		public final TypeConverter<T, T, T, T> unsafeCast;

		private final Map<Class<?>, TypeConverter<?, ?, T, T>> thePrimitiveCasts;

		PrimitiveTypeData(Class<T> wrapper, Class<T> primitive, boolean number, boolean bool, boolean isVoid, boolean isChar, int size,
			T defaultValue) {
			super(wrapper);
			this.primitiveClass = primitive;
			primitiveType = TypeToken.of(primitive);
			this.number = number;
			this.bool = bool;
			this.isVoid = isVoid;
			this.isChar = isChar;
			this.size = size;
			this.defaultValue = defaultValue;
			thePrimitiveCasts = new HashMap<>();
			primitiveUnity = new TypeConverter<>("no-op", "no-op", primitiveType, primitiveType, //
				null, LambdaUtils.identity(), null, LambdaUtils.identity());
			safeCast = isVoid ? null : new TypeConverter<>("safeCast", "primitiveWrap", type, primitiveType, //
				ALWAYS_NULL, LambdaUtils.printableFn(w -> w != null ? w : defaultValue, "safeCast", null), //
				ALWAYS_NULL, LambdaUtils.identity());
			String castError = "Null cannot be cast to primitive type " + primitive.getName();
			unsafeCast = isVoid ? null : new TypeConverter<>("unsafeCast", "primitiveWrap", type, primitiveType, //
				LambdaUtils.printableFn(w -> w == null ? castError : null, "nullCheck", null), LambdaUtils.identity(), //
				ALWAYS_NULL, LambdaUtils.identity());
			thePrimitiveCasts.put(primitiveClass, unsafeCast);
		}

		@Override
		int computeSpecificity() {
			if (isVoid)
				return 0;
			else if (primitiveClass == double.class)
				return 1;
			else if (primitiveClass == float.class)
				return 2;
			else if (primitiveClass == long.class)
				return 3;
			else if (primitiveClass == int.class)
				return 4;
			else if (primitiveClass == short.class)
				return 5;
			else if (primitiveClass == byte.class)
				return 6;
			else if (isChar)
				return 7;
			else if (bool)
				return 8;
			else
				throw new IllegalStateException("Unaccounted primitive " + clazz);
		}

		public <S> TypeConverter<S, S, T, T> getPrimitiveCast(Class<S> primitiveType) {
			return (TypeConverter<S, S, T, T>) thePrimitiveCasts.get(primitiveType);
		}

		<S> void populatePrimitiveCast(PrimitiveTypeData<S> typeData, TypeConverter<S, S, T, T> converter) {
			thePrimitiveCasts.put(typeData.primitiveClass, converter);
			typeData.thePrimitiveCasts.put(primitiveClass, converter.reverse());
		}
	}

	public class NumberTypeData<T extends Number> extends PrimitiveTypeData<T> {
		private final boolean isFloatingPoint;

		public NumberTypeData(Class<T> wrapper, Class<T> primitive, int size, T defaultValue, boolean floatingPoint) {
			super(wrapper, primitive, true, false, false, false, size, defaultValue);
			isFloatingPoint = floatingPoint;
		}

		public boolean isFloatingPoint() {
			return isFloatingPoint;
		}
	}

	/**
	 * May be {@link TypeTokens#addTypeRetriever(TypeRetriever) added} to {@link TypeTokens} to enable retrieval of non-Class types or types
	 * that may not be retrievable by the {@link TypeTokens} class using {@link Class#forName(String)}
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

	/**
	 * Adds the ability for {@link TypeTokens#getCast(TypeToken, TypeToken)} to cast one type to another
	 *
	 * @param <S> The type to cast from
	 * @param <T> The type to cast to
	 */
	public interface SupplementaryCast<S, T> {
		/**
		 * @param sourceType The source type to cast
		 * @return The result type that this cast will produce from the given source type
		 */
		TypeToken<? extends T> getCastType(TypeToken<? extends S> sourceType);

		/**
		 * @param source The source value to cast
		 * @return The cast value
		 */
		T cast(S source);

		boolean isSafe();

		String canCast(S source);
	}

	private final ConcurrentHashMap<Class<?>, TypeKey<?>> TYPES;
	private final ClassMap<ClassMap<SupplementaryCast<?, ?>>> theSupplementaryCasts;
	private final TypeTokensParser theParser;

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
		theParser = new TypeTokensParser();
		theSupplementaryCasts = new ClassMap<>();

		// Populate the primitive types as both the primitive type and the wrapper
		PrimitiveTypeData<Void> voidData = new PrimitiveTypeData<>(Void.class, void.class, false, false, true, false, 0, null);
		TYPES.put(void.class, voidData);
		TYPES.put(Void.class, voidData);
		VOID = voidData.type;
		PR_VOID = voidData.primitiveType;
		PrimitiveTypeData<Boolean> boolData = new PrimitiveTypeData<>(Boolean.class, boolean.class, false, true, false, false, 1,
			Boolean.FALSE);
		TYPES.put(boolean.class, boolData);
		TYPES.put(Boolean.class, boolData);
		BOOLEAN = boolData.type;
		PR_BOOLEAN = boolData.primitiveType;
		PrimitiveTypeData<Character> charData = new PrimitiveTypeData<>(Character.class, char.class, false, false, false, true, 2,
			Character.valueOf((char) 0));
		TYPES.put(char.class, charData);
		TYPES.put(Character.class, charData);
		CHAR = charData.type;
		PR_CHAR = charData.primitiveType;
		NumberTypeData<Byte> byteData = new NumberTypeData<>(Byte.class, byte.class, 1, Byte.valueOf((byte) 0), false);
		TYPES.put(byte.class, byteData);
		TYPES.put(Byte.class, byteData);
		BYTE = byteData.type;
		PR_BYTE = byteData.primitiveType;
		NumberTypeData<Short> shortData = new NumberTypeData<>(Short.class, short.class, 2, Short.valueOf((short) 0), false);
		TYPES.put(short.class, shortData);
		TYPES.put(Short.class, shortData);
		SHORT = shortData.type;
		PR_SHORT = shortData.primitiveType;
		NumberTypeData<Integer> intData = new NumberTypeData<>(Integer.class, int.class, 4, Integer.valueOf(0), false);
		TYPES.put(int.class, intData);
		TYPES.put(Integer.class, intData);
		INT = intData.type;
		PR_INT = intData.primitiveType;
		NumberTypeData<Long> longData = new NumberTypeData<>(Long.class, long.class, 8, Long.valueOf(0L), false);
		TYPES.put(long.class, longData);
		TYPES.put(Long.class, longData);
		LONG = longData.type;
		PR_LONG = longData.primitiveType;
		NumberTypeData<Float> floatData = new NumberTypeData<>(Float.class, float.class, 4, Float.valueOf(0.0f), true);
		TYPES.put(float.class, floatData);
		TYPES.put(Float.class, floatData);
		FLOAT = floatData.type;
		PR_FLOAT = floatData.primitiveType;
		NumberTypeData<Double> doubleData = new NumberTypeData<>(Double.class, double.class, 8, Double.valueOf(0.0), true);
		TYPES.put(double.class, doubleData);
		TYPES.put(Double.class, doubleData);
		DOUBLE = doubleData.type;
		PR_DOUBLE = doubleData.primitiveType;

		populatePrimitiveCasts(voidData, boolData, charData, byteData, shortData, intData, longData, floatData, doubleData);

		STRING = of(String.class);
		OBJECT = of(Object.class);
		WILDCARD = TypeToken.of(new WildcardTypeImpl());
		CLASS = keyFor(Class.class).wildCard();
		// WILDCARD = new TypeToken<Class<?>>() {}.resolveType(Class.class.getTypeParameters()[0]);
	}

	private void populatePrimitiveCasts(PrimitiveTypeData<Void> voidData, PrimitiveTypeData<Boolean> boolData,
		PrimitiveTypeData<Character> charData, NumberTypeData<Byte> byteData, NumberTypeData<Short> shortData,
		NumberTypeData<Integer> intData, NumberTypeData<Long> longData, NumberTypeData<Float> floatData,
		NumberTypeData<Double> doubleData) {
		// Allow conversion of to void (null) and void to the primitive default value
		Function<Object, Void> toVoid = LambdaUtils.constantFn(null, "null", null);
		voidData.populatePrimitiveCast(boolData, //
			new TypeConverter<>("null-to-void", "null", boolData.primitiveType, voidData.primitiveType, //
				null, toVoid, null, LambdaUtils.constantFn(boolData.defaultValue, "false", null)));
		voidData.populatePrimitiveCast(charData, //
			new TypeConverter<>("null-to-void", "null", charData.primitiveType, voidData.primitiveType, //
				null, toVoid, null, LambdaUtils.constantFn(charData.defaultValue, "false", null)));
		voidData.populatePrimitiveCast(byteData, //
			new TypeConverter<>("null-to-void", "null", byteData.primitiveType, voidData.primitiveType, //
				null, toVoid, null, LambdaUtils.constantFn(byteData.defaultValue, "false", null)));
		voidData.populatePrimitiveCast(shortData, //
			new TypeConverter<>("null-to-void", "null", shortData.primitiveType, voidData.primitiveType, //
				null, toVoid, null, LambdaUtils.constantFn(shortData.defaultValue, "false", null)));
		voidData.populatePrimitiveCast(intData, //
			new TypeConverter<>("null-to-void", "null", intData.primitiveType, voidData.primitiveType, //
				null, toVoid, null, LambdaUtils.constantFn(intData.defaultValue, "false", null)));
		voidData.populatePrimitiveCast(longData, //
			new TypeConverter<>("null-to-void", "null", longData.primitiveType, voidData.primitiveType, //
				null, toVoid, null, LambdaUtils.constantFn(longData.defaultValue, "false", null)));
		voidData.populatePrimitiveCast(floatData, //
			new TypeConverter<>("null-to-void", "null", floatData.primitiveType, voidData.primitiveType, //
				null, toVoid, null, LambdaUtils.constantFn(floatData.defaultValue, "false", null)));
		voidData.populatePrimitiveCast(doubleData, //
			new TypeConverter<>("null-to-void", "null", doubleData.primitiveType, voidData.primitiveType, //
				null, toVoid, null, LambdaUtils.constantFn(doubleData.defaultValue, "false", null)));

		// No boolean conversions except with void above

		// Character conversions
		charData.populatePrimitiveCast(byteData, //
			new TypeConverter<>("byte-to-char", "char-to-byte", byteData.primitiveType, charData.primitiveType, //
				null, LambdaUtils.printableFn(b -> (char) b.byteValue(), "byte-to-char", null), //
				LambdaUtils.printableFn(ch -> ch <= (char) Byte.MAX_VALUE ? null : "Char value " + (int) ch + " is not in byte range",
					"check-byte-range", null),
				LambdaUtils.printableFn(ch -> (byte) ch.charValue(), "char-to-byte", null)));
		charData.populatePrimitiveCast(shortData, //
			new TypeConverter<>("short-to-char", "char-to-short", shortData.primitiveType, charData.primitiveType, //
				LambdaUtils.printableFn(sh -> sh >= 0 ? null : "A character cannot be negative", "check-char-range", null),
				LambdaUtils.printableFn(sh -> (char) sh.shortValue(), "short-to-char", null), //
				LambdaUtils.printableFn(ch -> ch <= (char) Short.MAX_VALUE ? null : "Char value " + (int) ch + " is not in short range",
					"check-short-range", null),
				LambdaUtils.printableFn(ch -> (short) ch.charValue(), "char-to-short", null)));
		charData.populatePrimitiveCast(intData, //
			new TypeConverter<>("int-to-char", "char-to-int", intData.primitiveType, charData.primitiveType, //
				LambdaUtils.printableFn(i -> {
					if (i < 0)
						return "A character cannot be negative";
					else if (i > Character.MAX_VALUE)
						return "Int value " + i + " is not in char range";
					else
						return null;
				}, "check-char-range", null),
				LambdaUtils.printableFn(i -> (char) i.intValue(), "int-to-char", null), //
				null, LambdaUtils.printableFn(ch -> (int) ch.charValue(), "char-to-int", null)));
		charData.populatePrimitiveCast(longData, //
			new TypeConverter<>("long-to-char", "char-to-long", longData.primitiveType, charData.primitiveType, //
				LambdaUtils.printableFn(i -> (i >= 0 && i <= Character.MAX_VALUE) ? null : "Long value " + i + " is not in char range",
					"check-char-range", null),
				LambdaUtils.printableFn(i -> (char) i.longValue(), "long-to-char", null), //
				null, LambdaUtils.printableFn(ch -> (long) ch.charValue(), "char-to-long", null)));
		charData.populatePrimitiveCast(floatData, //
			new TypeConverter<>("float-to-char", "char-to-float", floatData.primitiveType, charData.primitiveType, //
				LambdaUtils.printableFn(f -> {
					if (f < 0)
						return "A character cannot be negative";
					else if (f > Character.MAX_VALUE)
						return "Float value " + f + " is not in char range";
					float rem = f % 1;
					if (rem != 0)
						return "Float value has decimal--cannot be assigned to a char";
					return null;
				}, "check-char-range", null),
				LambdaUtils.printableFn(f -> (char) f.floatValue(), "float-to-char", null), //
				null, LambdaUtils.printableFn(ch -> (float) ch.charValue(), "char-to-float", null)));
		charData.populatePrimitiveCast(doubleData, //
			new TypeConverter<>("double-to-char", "char-to-double", doubleData.primitiveType, charData.primitiveType, //
				LambdaUtils.printableFn(d -> {
					if (d < 0)
						return "A character cannot be negative";
					else if (d > Character.MAX_VALUE)
						return "Double value " + d + " is not in char range";
					double rem = d % 1;
					if (rem != 0)
						return "Double value has decimal--cannot be assigned to a char";
					return null;
				}, "check-char-range", null),
				LambdaUtils.printableFn(d -> (char) d.floatValue(), "float-to-char", null), //
				null, LambdaUtils.printableFn(ch -> (double) ch.charValue(), "char-to-double", null)));

		// Byte conversions
		byteData.populatePrimitiveCast(shortData, //
			new TypeConverter<>("short-to-byte", "byte-to-short", shortData.primitiveType, byteData.primitiveType, //
				LambdaUtils.printableFn(
					sh -> (sh >= Byte.MIN_VALUE && sh <= Byte.MAX_VALUE) ? null : "Short value " + sh + " is not in byte range",
						"check-byte-range", null),
				LambdaUtils.printableFn(sh -> (byte) sh.shortValue(), "short-to-byte", null), //
				null, LambdaUtils.printableFn(b -> (short) b.byteValue(), "byte-to-short", null)));
		byteData.populatePrimitiveCast(intData, //
			new TypeConverter<>("int-to-byte", "byte-to-int", intData.primitiveType, byteData.primitiveType, //
				LambdaUtils.printableFn(
					i -> (i >= Byte.MIN_VALUE && i <= Byte.MAX_VALUE) ? null : "Int value " + i + " is not in byte range",
						"check-byte-range", null),
				LambdaUtils.printableFn(i -> (byte) i.intValue(), "int-to-byte", null), //
				null, LambdaUtils.printableFn(b -> (int) b.byteValue(), "byte-to-int", null)));
		byteData.populatePrimitiveCast(longData, //
			new TypeConverter<>("long-to-byte", "byte-to-long", longData.primitiveType, byteData.primitiveType, //
				LambdaUtils.printableFn(
					i -> (i >= Byte.MIN_VALUE && i <= Byte.MAX_VALUE) ? null : "Long value " + i + " is not in byte range",
						"check-byte-range", null),
				LambdaUtils.printableFn(sh -> (byte) sh.longValue(), "short-to-byte", null), //
				null, LambdaUtils.printableFn(b -> (long) b.byteValue(), "byte-to-long", null)));
		byteData.populatePrimitiveCast(floatData, //
			new TypeConverter<>("float-to-byte", "byte-to-float", floatData.primitiveType, byteData.primitiveType, //
				LambdaUtils.printableFn(f -> {
					if (f < Byte.MIN_VALUE || f > Byte.MAX_VALUE)
						return "Float value " + f + " is not in byte range";
					float rem = f % 1;
					if (rem != 0)
						return "Float value has decimal--cannot be assigned to a byte";
					return null;
				}, "check-byte-range", null),
				LambdaUtils.printableFn(f -> (byte) f.floatValue(), "float-to-byte", null), //
				null, LambdaUtils.printableFn(b -> (float) b.byteValue(), "byte-to-float", null)));
		byteData.populatePrimitiveCast(doubleData, //
			new TypeConverter<>("double-to-byte", "byte-to-double", doubleData.primitiveType, byteData.primitiveType, //
				LambdaUtils.printableFn(d -> {
					if (d < Byte.MIN_VALUE || d > Byte.MAX_VALUE)
						return "Double value " + d + " is not in byte range";
					double rem = d % 1;
					if (rem != 0)
						return "Double value has decimal--cannot be assigned to a byte";
					return null;
				}, "check-byte-range", null),
				LambdaUtils.printableFn(d -> (byte) d.doubleValue(), "double-to-byte", null), //
				null, LambdaUtils.printableFn(b -> (double) b.byteValue(), "byte-to-double", null)));

		// Short conversions
		shortData.populatePrimitiveCast(intData, //
			new TypeConverter<>("int-to-short", "short-to-int", intData.primitiveType, shortData.primitiveType, //
				LambdaUtils.printableFn(
					i -> (i >= Short.MIN_VALUE && i <= Short.MAX_VALUE) ? null : "Int value " + i + " is not in short range",
						"check-short-range", null),
				LambdaUtils.printableFn(i -> (short) i.intValue(), "int-to-short", null), //
				null, LambdaUtils.printableFn(b -> (int) b.shortValue(), "short-to-int", null)));
		shortData.populatePrimitiveCast(longData, //
			new TypeConverter<>("long-to-short", "short-to-long", longData.primitiveType, shortData.primitiveType, //
				LambdaUtils.printableFn(
					i -> (i >= Short.MIN_VALUE && i <= Short.MAX_VALUE) ? null : "Long value " + i + " is not in short range",
						"check-short-range", null),
				LambdaUtils.printableFn(i -> (short) i.longValue(), "long-to-short", null), //
				null, LambdaUtils.printableFn(b -> (long) b.shortValue(), "short-to-long", null)));
		shortData.populatePrimitiveCast(floatData, //
			new TypeConverter<>("float-to-short", "short-to-float", floatData.primitiveType, shortData.primitiveType, //
				LambdaUtils.printableFn(f -> {
					if (f < Short.MIN_VALUE || f > Short.MAX_VALUE)
						return "FLoat value " + f + " is not in short range";
					float rem = f % 1;
					if (rem != 0)
						return "Float value has decimal--cannot be assigned to a short";
					return null;
				}, "check-short-range", null),
				LambdaUtils.printableFn(i -> (short) i.floatValue(), "float-to-short", null), //
				null, LambdaUtils.printableFn(b -> (float) b.shortValue(), "short-to-float", null)));
		shortData.populatePrimitiveCast(doubleData, //
			new TypeConverter<>("double-to-short", "short-to-double", doubleData.primitiveType, shortData.primitiveType, //
				LambdaUtils.printableFn(d -> {
					if (d < Short.MIN_VALUE || d > Short.MAX_VALUE)
						return "Double value " + d + " is not in short range";
					double rem = d % 1;
					if (rem != 0)
						return "Double value has decimal--cannot be assigned to a short";
					return null;
				}, "check-short-range", null),
				LambdaUtils.printableFn(i -> (short) i.doubleValue(), "double-to-short", null), //
				null, LambdaUtils.printableFn(b -> (double) b.shortValue(), "short-to-double", null)));

		// Int conversions
		intData.populatePrimitiveCast(longData, //
			new TypeConverter<>("long-to-int", "int-to-long", longData.primitiveType, intData.primitiveType, //
				LambdaUtils.printableFn(
					i -> (i >= Integer.MIN_VALUE && i <= Integer.MAX_VALUE) ? null : "Long value " + i + " is not in int range",
						"check-int-range", null),
				LambdaUtils.printableFn(i -> (int) i.longValue(), "long-to-int", null), //
				null, LambdaUtils.printableFn(b -> (long) b.intValue(), "int-to-long", null)));
		intData.populatePrimitiveCast(floatData, //
			new TypeConverter<>("float-to-int", "int-to-float", floatData.primitiveType, intData.primitiveType, //
				LambdaUtils.printableFn(f -> {
					if (f < Integer.MIN_VALUE || f > Integer.MAX_VALUE)
						return "Float value " + f + " is not in int range";
					float rem = f % 1;
					if (rem != 0)
						return "Float value has decimal--cannot be assigned to an int";
					return null;
				}, "check-int-range", null),
				LambdaUtils.printableFn(i -> (int) i.floatValue(), "float-to-int", null), //
				null, LambdaUtils.printableFn(b -> (float) b.intValue(), "int-to-float", null)));
		intData.populatePrimitiveCast(doubleData, //
			new TypeConverter<>("double-to-int", "int-to-double", doubleData.primitiveType, intData.primitiveType, //
				LambdaUtils.printableFn(d -> {
					if (d < Integer.MIN_VALUE || d > Integer.MAX_VALUE)
						return "Double value " + d + " is not in int range";
					double rem = d % 1;
					if (rem != 0)
						return "Double value has decimal--cannot be assigned to an int";
					return null;
				}, "check-int-range", null),
				LambdaUtils.printableFn(i -> (int) i.doubleValue(), "double-to-int", null), //
				null, LambdaUtils.printableFn(b -> (double) b.intValue(), "int-to-double", null)));

		// Long conversions
		longData.populatePrimitiveCast(floatData, //
			new TypeConverter<>("float-to-long", "long-to-float", floatData.primitiveType, longData.primitiveType, //
				LambdaUtils.printableFn(f -> {
					if (f < Long.MIN_VALUE || f > Long.MAX_VALUE)
						return "Float value " + f + " is not in long range";
					float rem = f % 1;
					if (rem != 0)
						return "Float value has decimal--cannot be assigned to a long";
					return null;
				}, "check-long-range", null),
				LambdaUtils.printableFn(i -> (long) i.floatValue(), "float-to-long", null), //
				null, LambdaUtils.printableFn(b -> (float) b.longValue(), "long-to-float", null)));
		longData.populatePrimitiveCast(doubleData, //
			new TypeConverter<>("double-to-long", "long-to-double", doubleData.primitiveType, longData.primitiveType, //
				LambdaUtils.printableFn(d -> {
					if (d < Long.MIN_VALUE || d > Long.MAX_VALUE)
						return "Double value " + d + " is not in long range";
					double rem = d % 1;
					if (rem != 0)
						return "Double value has decimal--cannot be assigned to a long";
					return null;
				}, "check-long-range", null),
				LambdaUtils.printableFn(i -> (long) i.doubleValue(), "double-to-long", null), //
				null, LambdaUtils.printableFn(b -> (double) b.longValue(), "long-to-double", null)));

		// Float conversion
		floatData.populatePrimitiveCast(doubleData, //
			new TypeConverter<>("double-to-float", "float-to-double", doubleData.primitiveType, floatData.primitiveType, //
				LambdaUtils.printableFn(d -> {
					if (d < Float.MIN_VALUE || d > Float.MAX_VALUE)
						return "Double value is too large for a float";
					float f = d.floatValue();
					if (f != d)
						return "Double value has decimal precision that cannot be stored in a float";
					return null;
				}, "check-float-range", null),
				LambdaUtils.printableFn(i -> (float) i.doubleValue(), "double-to-float", null), //
				null, LambdaUtils.printableFn(b -> (double) b.floatValue(), "float-to-double", null)));
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

	public <T> PrimitiveTypeData<T> primitiveKeyFor(Class<T> type) {
		TypeKey<T> key = keyFor(type);
		if (!(key instanceof PrimitiveTypeData))
			throw new IllegalArgumentException(type.getName() + " is not a primitive type or wrapper");
		return (PrimitiveTypeData<T>) key;
	}

	public <N extends Number> NumberTypeData<N> numberKeyFor(Class<N> type) {
		TypeKey<N> key = keyFor(type);
		if (key instanceof NumberTypeData)
			return (NumberTypeData<N>) key;
		else if (!(key instanceof PrimitiveTypeData))
			throw new IllegalArgumentException(type.getName() + " is not a primitive type or wrapper");
		else
			throw new IllegalArgumentException(
				"Primitive type " + ((PrimitiveTypeData<N>) key).primitiveClass.getName() + " is not a number type");
	}

	/**
	 * @param <T> The compile-time type to get the type token for
	 * @param type The type to get the type token for
	 * @return The type token for the given type
	 */
	public <T> TypeToken<T> of(Class<T> type) {
		TypeKey<T> key = keyFor(type);
		if (type.isPrimitive())
			return ((PrimitiveTypeData<T>) key).primitiveType;
		else
			return key.type;
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
			return keyFor((Class<?>) ((ParameterizedType) type).getRawType())
				.parameterized(((ParameterizedType) type).getActualTypeArguments());
		else
			return TypeToken.of(type);
	}

	@Override
	public TypeTokens addTypeRetriever(TypeRetriever typeRetriever) {
		theParser.addTypeRetriever(typeRetriever);
		return this;
	}

	@Override
	public boolean removeTypeRetriever(TypeRetriever typeRetriever) {
		return theParser.removeTypeRetriever(typeRetriever);
	}

	/**
	 * Adds the ability for {@link #getCast(TypeToken, TypeToken)} to cast from one type to another
	 *
	 * @param <S> The type to cast from
	 * @param <T> The type to cast to
	 * @param sourceType The type to cast from
	 * @param targetType The type to cast to
	 * @param cast The cast to perform the cast operation
	 * @return This TypeTokens
	 */
	public <S, T> TypeTokens addSupplementaryCast(Class<S> sourceType, Class<T> targetType, SupplementaryCast<S, T> cast) {
		theSupplementaryCasts.computeIfAbsent(targetType, ClassMap::new).put(sourceType, cast);
		return this;
	}

	/**
	 * @param sourceType The source type
	 * @param targetType The target type
	 * @param cast The cast to remove
	 * @return Whether the cast was found and removed for the given source/target type pair
	 */
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
	 * @return A string representation of the given type using just the {@link Class#getSimpleName()} of the type and any parameters
	 */
	public static String getSimpleName(Type type) {
		return getSimpleName(type, null).toString();
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
		TypeKey<T> key = (TypeKey<T>) TYPES.get(type);
		return key instanceof PrimitiveTypeData ? ((PrimitiveTypeData<T>) key).defaultValue : null;
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
		return getDefaultValue(type);
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
		Class<?> rawType = getRawType(type.getType());
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
	 * Similar to {@link TypeToken#isSupertypeOf(TypeToken)}, but this method allows for assignments where conversion is required, e.g.
	 * auto-(un)boxing and primitive number type conversion.
	 *
	 * @param left The type of the variable to assign the value to
	 * @param right The type of the value to assign
	 * @return Whether the given value type can be assigned to the given variable type
	 */
	public boolean isAssignable(TypeToken<?> left, TypeToken<?> right) {
		return isAssignable(left, right, new LinkedHashSet<>());
	}

	private boolean isAssignable(TypeToken<?> left, TypeToken<?> right, Set<TypeToken<?>> stack) {
		if (!stack.add(left))
			return true;
		if (left.isSupertypeOf(wrap(right)))
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
			else if (rawLeft == Comparable.class)
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
			for (Type bound : ((TypeVariable<?>) left.getType()).getBounds()) {
				TypeToken<?> boundToken = left.resolveType(bound);
				if (!isAssignable(boundToken, right, stack))
					return false;
				stack.remove(boundToken);
			}
			return true;
		} else if (left.getType() instanceof WildcardType) {
			for (Type bound : ((WildcardType) left.getType()).getUpperBounds()) {
				TypeToken<?> boundToken = left.resolveType(bound);
				if (!isAssignable(boundToken, right, stack))
					return false;
				stack.remove(boundToken);
			}
			for (Type bound : ((WildcardType) left.getType()).getLowerBounds()) {
				TypeToken<?> boundToken = left.resolveType(bound);
				if (!isAssignable(right, boundToken, stack))
					return false;
				stack.remove(right);
			}
			return true;
		} else if (left.getType() instanceof ParameterizedType) {
			if (!rawLeft.isAssignableFrom(rawRight))
				return false;
			for (int p = 0; p < rawLeft.getTypeParameters().length; p++) {
				TypeToken<?> leftTP = left.resolveType(rawLeft.getTypeParameters()[p]);
				TypeToken<?> rightTP = right.resolveType(rawLeft.getTypeParameters()[p]);

				if (!isAssignable(leftTP, rightTP, stack))
					return false;
				stack.remove(leftTP);
			}
			return true;
		} else if (left.isArray())
			return right.isArray() && isAssignable(left.getComponentType(), right.getComponentType());
		else
			return false; // Dunno what it could be, but we can't resolve it
	}

	/**
	 * Converts from one type to another
	 *
	 * @param <S> The super type of all values that this converter can convert
	 * @param <R> The super type which all reversed values will extend
	 * @param <TR> The super type of all values that this converter can {@link #reverse(Object target)}
	 * @param <T> The super type which all converted values will extend
	 */
	public static class TypeConverter<S, R extends S, TR, T extends TR> implements Function<S, T> {
		private final String theName;
		private final String theReverseName;
		private final TypeToken<R> theReverseType;
		private final TypeToken<T> theConvertedType;
		private final Function<? super S, String> theApplicability;
		private final Function<? super S, T> theConverter;
		private final Function<? super TR, String> theReversibility;
		private final Function<? super TR, R> theReverse;

		private TypeConverter<TR, T, S, R> theReversed;

		public TypeConverter(String name, String reverseName, //
			TypeToken<R> sourceType, TypeToken<T> convertedType, //
			Function<? super S, String> applicability, Function<? super S, T> converter, //
			Function<? super TR, String> reversibility, Function<? super TR, R> reverse) {
			theName = name;
			theReverseName = reverseName;
			theReverseType = sourceType;
			theConvertedType = convertedType;
			theApplicability = applicability;
			theConverter = converter;
			theReversibility = reversibility;
			theReverse = reverse;
		}

		public String getName() {
			return theName;
		}

		public String getReverseName() {
			return theReverseName;
		}

		/** @return The {@link TypeToken} of the target type */
		public TypeToken<T> getConvertedType() {
			return theConvertedType;
		}

		public String isApplicable(S source) {
			return theApplicability == null ? null : theApplicability.apply(source);
		}

		@Override
		public T apply(S source) {
			String app = isApplicable(source);
			if (app != null)
				throw new IllegalArgumentException(app);
			return theConverter.apply(source);
		}

		public String isReversible(TR target) {
			return theReversibility == null ? null : theReversibility.apply(target);
		}

		public R reverse(TR target) {
			String app = isReversible(target);
			if (app != null)
				throw new IllegalArgumentException(app);
			return theReverse.apply(target);
		}

		public boolean isTrivial() {
			return theConverter == LambdaUtils.identity() && theReverse == LambdaUtils.identity();
		}

		public boolean isSafe() {
			return theApplicability == null;
		}

		public boolean isReverseSafe() {
			return theReversibility == null;
		}

		public Function<? super S, String> getApplicability() {
			return theApplicability;
		}

		public Function<? super T, String> getReversibility() {
			return theReversibility;
		}

		public TypeConverter<TR, T, S, R> reverse() {
			if (theReversed == null) {
				theReversed = new TypeConverter<>(theReverseName, theName, theConvertedType, theReverseType, //
					theReversibility, theReverse, theApplicability, theConverter);
				theReversed.theReversed = this;
			}
			return theReversed;
		}

		public <TR2, T2 extends TR2> TypeConverter<S, R, TR2, T2> andThen(TypeConverter<? super T, ? extends T, TR2, T2> other) {
			// If one or the other is trivial, keep it simple
			if (theConverter == LambdaUtils.identity()) {
				if (theReverseType.equals(other.theReverseType))
					return (TypeConverter<S, R, TR2, T2>) other;
				return new TypeConverter<>(other.theName, other.theReverseName, theReverseType, other.theConvertedType, //
					(Function<S, String>) other.theApplicability, (Function<S, T2>) other.theConverter, //
					other.theReversibility, (Function<TR2, R>) other.theReverse);
			} else if (other.theConverter == LambdaUtils.identity()) {
				if (theConvertedType.equals(other.theConvertedType))
					return (TypeConverter<S, R, TR2, T2>) this;
				return new TypeConverter<>(theName, theReverseName, theReverseType, other.theConvertedType, //
					theApplicability, (Function<S, T2>) theConverter, //
					(Function<TR2, String>) theReversibility, (Function<TR2, R>) theReverse);
			}
			Function<? super S, String> applicability;
			Function<? super TR2, String> reversibility;
			if (other.theApplicability == null)
				applicability = theApplicability;
			else {
				Function<? super S, String> myApp = theApplicability;
				Function<? super S, T> myConverter = theConverter;
				Function<? super T, String> otherApp = other.theApplicability;
				applicability = LambdaUtils.printableFn(s -> {
					String msg = myApp == null ? null : myApp.apply(s);
					if (msg != null)
						return msg;
					T interm = myConverter.apply(s);
					return otherApp.apply(interm);
				}, () -> myApp + "->" + otherApp, null);
			}
			if (theReversibility == null)
				reversibility = other.theReversibility;
			else {
				Function<? super T, String> myRev = theReversibility;
				Function<? super TR2, ? extends T> otherReverse = other.theReverse;
				Function<? super TR2, String> otherRev = other.theReversibility;
				reversibility = LambdaUtils.printableFn(s -> {
					String msg = otherRev == null ? null : otherRev.apply(s);
					if (msg != null)
						return msg;
					T interm = otherReverse.apply(s);
					return myRev.apply(interm);
				}, () -> myRev + "->" + otherRev, null);
			}
			Function<? super S, T2> convert = theConverter.andThen(other.theConverter);
			Function<? super TR2, R> reverse = other.theReverse.andThen(theReverse);
			return new TypeConverter<>(theName + "->" + other.theName, other.theReverseName + "->" + theReverseName, //
				theReverseType, other.theConvertedType, //
				applicability, convert, reversibility, reverse);
		}

		@Override
		public int hashCode() {
			return theConverter.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof TypeConverter && theConverter.equals(((TypeConverter<?, ?, ?, ?>) obj).theConverter);
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
	public <T, X> TypeConverter<? super T, ? extends T, ? super X, ? extends X> getCast(TypeToken<X> left, TypeToken<T> right)
		throws IllegalArgumentException {
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
	 * @param target The type to cast to
	 * @param source The type to cast from
	 * @param safe For primitive right types, whether to use a safe value (0 or false) if the left value is null, as opposed to throwing a
	 *        {@link NullPointerException}
	 * @return A function that takes an instance of the right type and returns it as an instance of the left type, throwing a a
	 *         {@link NullPointerException} if the right value is null and the left type is primitive
	 * @throws IllegalArgumentException If values of the right type cannot be cast to the left type in general
	 */
	public <S, T> TypeConverter<? super S, ? extends S, ? super T, ? extends T> getCast(TypeToken<T> target, TypeToken<S> source,
		boolean safe) throws IllegalArgumentException {
		return getCast(target, source, safe, true);
	}

	private static class InstanceChecker<T> {
		final String checkString;
		final Function<Object, String> check;
		final Function<Object, T> cast;

		InstanceChecker(Class<T> type) {
			String error = "Not an instance of " + type.getName();
			checkString = "check" + type.getSimpleName();
			check = LambdaUtils.printableFn(value -> {
				if (value == null)
					return null;
				else if (type.isInstance(value))
					return null;
				else
					return error;
			}, checkString, null);
			String castString = "cast" + type.getSimpleName();
			cast = LambdaUtils.printableFn(value -> type.cast(value), castString, null);
		}

		@Override
		public String toString() {
			return checkString;
		};
	}

	/**
	 * @param <S> The compile-time type to cast from
	 * @param <T> The compile-time type to cast to
	 * @param target The type to cast to
	 * @param source The type to cast from
	 * @param safe For primitive right types, whether to use a safe value (0 or false) if the left value is null, as opposed to throwing a
	 *        {@link NullPointerException}
	 * @param downCastOnly Whether to only allow down casts (e.g. int->double) or to also facilitate upcasts (e.g. double->int)
	 * @return A function that takes an instance of the right type and returns it as an instance of the left type, throwing a a
	 *         {@link NullPointerException} if the right value is null and the left type is primitive
	 * @throws IllegalArgumentException If values of the right type cannot be cast to the left type in general
	 */
	public <S, T> TypeConverter<? super S, ? extends S, ? super T, ? extends T> getCast(TypeToken<T> target, TypeToken<S> source,
		boolean safe, boolean downCastOnly) throws IllegalArgumentException {
		Class<S> rawSource = getRawType(source);
		TypeKey<S> sourceKey = keyFor(rawSource);
		if (target.equals(source)) {
			if (source.isPrimitive())
				return (TypeConverter<S, S, T, T>) ((PrimitiveTypeData<S>) sourceKey).primitiveUnity;
			return (TypeConverter<S, S, T, T>) sourceKey.unity;
		}
		// Handle the other primitive cases first, as they're cheap and make the complexity much less later
		// We only care about this if it's primitive--no need to compute
		Class<T> rawTarget = getRawType(target);
		TypeKey<T> targetKey = (TypeKey<T>) TYPES.get(rawTarget);
		if (targetKey instanceof PrimitiveTypeData) {
			PrimitiveTypeData<T> primTarget = (PrimitiveTypeData<T>) targetKey;
			if (sourceKey instanceof PrimitiveTypeData) {
				PrimitiveTypeData<S> primSource = (PrimitiveTypeData<S>) sourceKey;
				if (primSource.primitiveType == primTarget.primitiveType) { // Same type, but one is primitive and the other a wrapper
					if (source.isPrimitive())
						return (TypeConverter<S, S, T, T>) (safe ? primSource.safeCast : primSource.unsafeCast).reverse();
					else
						return (TypeConverter<S, S, T, T>) (safe ? primSource.safeCast : primSource.unsafeCast);
				}
				TypeConverter<S, S, T, T> primitiveCast = primTarget.getPrimitiveCast(primSource.primitiveClass);
				if (primitiveCast == null)
					throw new IllegalArgumentException("Cannot convert between " + source + " and " + target);
				else if (downCastOnly && !primitiveCast.isSafe())
					throw new IllegalArgumentException("Cannot safely convert from " + source + " to " + target);
				else if (target.isPrimitive()) {
					if (source.isPrimitive())
						return primitiveCast;
					else
						return (safe ? primSource.safeCast : primSource.unsafeCast).andThen(primitiveCast);
				} else if (primTarget.isVoid)
					return primitiveCast; // Void is chill if target is nullable
				else if (source.isPrimitive())
					return primitiveCast.andThen((safe ? primTarget.safeCast : primTarget.unsafeCast).reverse());
				else
					return new TypeConverter<>(primitiveCast.getName(), primitiveCast.getReverseName(), source, target, //
						primitiveCast.isSafe() ? null
							: LambdaUtils.printableFn(s -> s == null ? null : primitiveCast.isApplicable(s),
								primitiveCast.getApplicability()::toString, null), //
							LambdaUtils.printableFn(s -> s == null ? null : primitiveCast.apply(s), primitiveCast.getName(), null), //
							primitiveCast.isReverseSafe() ? null
								: LambdaUtils.printableFn(t -> t == null ? null : primitiveCast.isReversible(t),
									primitiveCast.getReversibility()::toString, null), //
								LambdaUtils.printableFn(t -> t == null ? null : primitiveCast.reverse(t), primitiveCast.getReverseName(), null));
			}
			TypeConverter<S, ? extends S, ? super T, T> suppConvert = getSpecialCast(source, target, rawSource, rawTarget);
			if (suppConvert != null)
				return suppConvert;

			if (downCastOnly)
				throw new IllegalArgumentException("Cannot safely convert from " + source + " to " + target);
			else if (rawSource.isAssignableFrom(Comparable.class) // All remaining possible primitives are comparable
				|| (primTarget.number && rawSource.isAssignableFrom(Number.class))) {
				InstanceChecker<T> checker = new InstanceChecker<>(primTarget.clazz);
				TypeConverter<S, S, T, T> typeCheckConverter = new TypeConverter<>(checker.checkString, "no-op", (TypeToken<S>) target,
					target, //
					checker.check, checker.cast, null, LambdaUtils.<T, S> unenforcedCast());
				if (!target.isPrimitive() || primTarget.isVoid)
					return typeCheckConverter;
				else
					return typeCheckConverter.andThen(safe ? primTarget.safeCast : primTarget.unsafeCast);
			}
		} else if (sourceKey instanceof PrimitiveTypeData) { // Source is primitive or a wrapper but target is not
			// We've handled this case in the reverse above
			TypeConverter<? super T, ? extends T, ? super S, ? extends S> reverseConverter = getCast(source, target, safe, false);
			TypeConverter<? super S, ? extends S, ? super T, ? extends T> converter = reverseConverter.reverse();
			if (downCastOnly && !converter.isSafe())
				throw new IllegalArgumentException("Cannot safely convert from " + source + " to " + target);
			return converter;
		}
		// Now neither type is primitive or a wrapper
		if (target.getType() instanceof TypeVariable && ((TypeVariable<?>) target.getType()).getBounds().length == 1)
			target = (TypeToken<T>) of(((TypeVariable<?>) target.getType()).getBounds()[0]);
		if (source.getType() instanceof WildcardType && ((WildcardType) source.getType()).getLowerBounds().length == 0) {
			// As far as I can tell, Google seems to have broken this.
			// TypeToken.isAssignableFrom(TypeToken) used to return true for this case,
			// but now TypeToken.isSuperTypeOf(TypeToken)returns false, which seems clearly wrong.
			return new TypeConverter<>("no-op", "no-op", source, (TypeToken<T>) source, null, LambdaUtils.<S, T> unenforcedCast(), null,
				LambdaUtils.<T, S> unenforcedCast());
		} else if (isAssignable(target, source)) {
			if (isAssignable(source, target))
				return new TypeConverter<>("no-op", "no-op", source, target, //
					null, LambdaUtils.<S, T> unenforcedCast(), null, LambdaUtils.<T, S> unenforcedCast());
			else {
				InstanceChecker<S> sourceChecker = new InstanceChecker<>(rawSource);
				return new TypeConverter<>("no-op", sourceChecker.checkString, source, (TypeToken<T>) source, //
					null, LambdaUtils.<S, T> unenforcedCast(), sourceChecker.check, sourceChecker.cast);
			}
		} else if (target.isArray()) {
			if (!source.isArray() || source.isPrimitive() || target.isPrimitive()) {
				TypeConverter<S, ? extends S, ? super T, T> suppConvert = getSpecialCast(source, target, rawSource, rawTarget);
				if (suppConvert != null)
					return suppConvert;
				else
					throw new IllegalArgumentException("Cannot convert from " + source + " to " + target);
			} else
				throw new IllegalArgumentException("Cannot convert from " + source + " to " + target);
		}

		TypeConverter<S, ? extends S, ? super T, T> suppConvert = getSpecialCast(source, target, rawSource, rawTarget);
		if (suppConvert != null)
			return suppConvert;
		else
			throw new IllegalArgumentException("Cannot convert from " + source + " to " + target);
	}

	private <S, T> TypeConverter<S, ? extends S, ? super T, T> getSpecialCast(TypeToken<S> sourceType, TypeToken<T> targetType,
		Class<S> sourceClass, Class<T> targetClass) {
		ClassMap<SupplementaryCast<?, ?>> sourceCasts = theSupplementaryCasts.get(targetClass, ClassMap.TypeMatch.SUB_TYPE);
		SupplementaryCast<S, T> suppCast = sourceCasts == null ? null
			: (SupplementaryCast<S, T>) sourceCasts.get(sourceClass, ClassMap.TypeMatch.SUPER_TYPE);
		if (suppCast != null) {
			ClassMap<SupplementaryCast<?, ?>> targetCasts = theSupplementaryCasts.get(sourceClass, ClassMap.TypeMatch.SUB_TYPE);
			SupplementaryCast<T, S> reverseCast = targetCasts == null ? null
				: (SupplementaryCast<T, S>) targetCasts.get(targetClass, ClassMap.TypeMatch.SUPER_TYPE);
			if (reverseCast != null)
				return new TypeConverter<>(suppCast.toString(), reverseCast.toString(), //
					sourceType, (TypeToken<T>) suppCast.getCastType(sourceType), //
					suppCast.isSafe() ? null : suppCast::canCast, suppCast::cast, //
						reverseCast.isSafe() ? null : reverseCast::canCast, reverseCast::cast);
			else
				return new TypeConverter<>(suppCast.toString(), "Impossible", //
					sourceType, (TypeToken<T>) suppCast.getCastType(sourceType), //
					suppCast.isSafe() ? null : suppCast::canCast, suppCast::cast, //
						LambdaUtils.constantFn("Impossible", "Impossible", null), __ -> {
							throw new IllegalStateException("Impossible");
						});
		}
		return null;
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
			return true;
		} else if (type instanceof TypeVariable) {
			TypeVariable<?> vbl = (TypeVariable<?>) type;
			for (Type bound : vbl.getBounds()) {
				if (bound instanceof Class) {
					if (bound != Object.class)
						return false;
				} else if (!isTrivialType(bound))
					return false;
			}
			return true;
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
	@Override
	public TypeToken<?> parseType(CharSequence typeName) throws ParseException {
		return theParser.parseType(typeName);
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
	public TypeToken<?> parseType(CharSequence typeName, int offset) throws ParseException {
		return theParser.parseType(typeName, offset);
	}

	/** @return A new TypeParser */
	public TypeParser newParser() {
		return new TypeTokensParser();
	}

	private static class ParameterizedTypeImpl implements ParameterizedType {
		private final Type theOwnerType;
		private final Type[] theTypeArgs;

		ParameterizedTypeImpl(Type ownerType, Type... typeArgs) {
			theOwnerType = ownerType;
			theTypeArgs = typeArgs;
			TypeTokens tokens = TypeTokens.get();
			for (int t = 0; t < theTypeArgs.length; t++) {
				if (tokens != null)
					theTypeArgs[t] = tokens.wrap(theTypeArgs[t]);
			}
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
			TypeTokens tokens = TypeTokens.get();
			for (int t = 0; t < theLowerBounds.length; t++) {
				if (tokens != null)
					theLowerBounds[t] = tokens.wrap(theLowerBounds[t]);
			}
			for (int t = 0; t < theUpperBounds.length; t++) {
				if (tokens != null)
					theUpperBounds[t] = tokens.wrap(theUpperBounds[t]);
			}
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

	class TypeTokensParser implements TypeParser {
		private final Set<TypeRetriever> theTypeRetrievers;

		TypeTokensParser() {
			theTypeRetrievers = new LinkedHashSet<>();
		}

		@Override
		public TypeParser addTypeRetriever(TypeRetriever typeRetriever) {
			theTypeRetrievers.add(typeRetriever);
			return this;
		}

		@Override
		public boolean removeTypeRetriever(TypeRetriever typeRetriever) {
			return theTypeRetrievers.remove(typeRetriever);
		}

		@Override
		public TypeToken<?> parseType(CharSequence typeName) throws ParseException {
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
		public TypeToken<?> parseType(CharSequence typeName, int offset) throws ParseException {
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
								typeName.subSequence(componentStart, c), offset + componentStart));
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
								typeName.subSequence(componentStart, c), offset + componentStart));
						componentStart = c + 1;
					}
					break;
				case '?':
					if (depth == 0) {
						for (c++; c < typeName.length(); c++) {
							if (typeName.charAt(c) != ' ')
								break;
						}
						if (offsetMatch(typeName, c, "extends")) {
							extendsOrSuper = Boolean.TRUE;
							c += "extends".length();
						} else if (offsetMatch(typeName, c, "super")) {
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

		private boolean offsetMatch(CharSequence seq, int offset, String test) {
			if (seq.length() < offset + test.length())
				return false;
			for (int i = 0; i < test.length(); i++) {
				if (seq.charAt(offset + i) != test.charAt(i))
					return false;
			}
			return true;
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
			for (TypeRetriever retriever : theTypeRetrievers) {
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
	}
}
