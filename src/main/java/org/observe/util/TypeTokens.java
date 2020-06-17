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
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import org.qommons.BiTuple;
import org.qommons.TriTuple;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterMap;
import org.qommons.collect.BetterMultiMap;
import org.qommons.collect.BetterSet;
import org.qommons.collect.BetterSortedMap;
import org.qommons.collect.BetterSortedMultiMap;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.MultiMap;
import org.qommons.collect.MutableCollectionElement;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

/**
 * It turns out that {@link TypeToken} is quite slow for many basic operations, like creation and {@link TypeToken#getRawType()
 * getRawType()}. This class provides some optimizations as well as boilerplate-reducing utilities.
 */
public class TypeTokens {
	static final Map<Class<?>, UnaryCompoundTypeCreator<?>> STANDARD_UNARY_TYPE_CREATORS;
	static final Map<Class<?>, BinaryCompoundTypeCreator<?>> STANDARD_BINARY_TYPE_CREATORS;
	static final Map<Class<?>, TernaryCompoundTypeCreator<?>> STANDARD_TERNARY_TYPE_CREATORS;

	static {
		Map<Class<?>, UnaryCompoundTypeCreator<?>> unaryTypes = new HashMap<>();
		Map<Class<?>, BinaryCompoundTypeCreator<?>> binaryTypes = new HashMap<>();
		Map<Class<?>, TernaryCompoundTypeCreator<?>> ternaryTypes = new HashMap<>();

		populateStandardCompoundTypes(unaryTypes, binaryTypes, ternaryTypes);

		STANDARD_UNARY_TYPE_CREATORS = Collections.unmodifiableMap(unaryTypes);
		STANDARD_BINARY_TYPE_CREATORS = Collections.unmodifiableMap(binaryTypes);
		STANDARD_TERNARY_TYPE_CREATORS = Collections.unmodifiableMap(ternaryTypes);
	}

	private static final TypeTokens instance = new TypeTokens();

	@SuppressWarnings("rawtypes")
	private static void populateStandardCompoundTypes(Map<Class<?>, UnaryCompoundTypeCreator<?>> unaryTypes,
		Map<Class<?>, BinaryCompoundTypeCreator<?>> binaryTypes, Map<Class<?>, TernaryCompoundTypeCreator<?>> ternaryTypes) {
		unaryTypes.put(Class.class, new UnaryCompoundTypeCreator<Class>() {
			@Override
			public <P> TypeToken<? extends Class> createCompoundType(TypeToken<P> param) {
				return new TypeToken<Class<P>>() {}.where(new TypeParameter<P>() {}, param);
			}
		});
		unaryTypes.put(Comparable.class, new UnaryCompoundTypeCreator<Comparable>() {
			@Override
			public <P> TypeToken<? extends Comparable> createCompoundType(TypeToken<P> param) {
				return new TypeToken<Comparable<P>>() {}.where(new TypeParameter<P>() {}, param);
			}
		});
		unaryTypes.put(Comparator.class, new UnaryCompoundTypeCreator<Comparator>() {
			@Override
			public <P> TypeToken<? extends Comparator> createCompoundType(TypeToken<P> param) {
				return new TypeToken<Comparator<P>>() {}.where(new TypeParameter<P>() {}, param);
			}
		});
		unaryTypes.put(Iterable.class, new UnaryCompoundTypeCreator<Iterable>() {
			@Override
			public <P> TypeToken<? extends Iterable> createCompoundType(TypeToken<P> param) {
				return new TypeToken<Iterable<P>>() {}.where(new TypeParameter<P>() {}, param);
			}
		});
		unaryTypes.put(Collection.class, new UnaryCompoundTypeCreator<Collection>() {
			@Override
			public <P> TypeToken<? extends Collection> createCompoundType(TypeToken<P> param) {
				return new TypeToken<Collection<P>>() {}.where(new TypeParameter<P>() {}, param);
			}
		});
		unaryTypes.put(Set.class, new UnaryCompoundTypeCreator<Set>() {
			@Override
			public <P> TypeToken<? extends Set> createCompoundType(TypeToken<P> param) {
				return new TypeToken<Set<P>>() {}.where(new TypeParameter<P>() {}, param);
			}
		});
		unaryTypes.put(List.class, new UnaryCompoundTypeCreator<List>() {
			@Override
			public <P> TypeToken<? extends List> createCompoundType(TypeToken<P> param) {
				return new TypeToken<List<P>>() {}.where(new TypeParameter<P>() {}, param);
			}
		});
		unaryTypes.put(SortedSet.class, new UnaryCompoundTypeCreator<SortedSet>() {
			@Override
			public <P> TypeToken<? extends SortedSet> createCompoundType(TypeToken<P> param) {
				return new TypeToken<SortedSet<P>>() {}.where(new TypeParameter<P>() {}, param);
			}
		});
		unaryTypes.put(NavigableSet.class, new UnaryCompoundTypeCreator<NavigableSet>() {
			@Override
			public <P> TypeToken<? extends NavigableSet> createCompoundType(TypeToken<P> param) {
				return new TypeToken<NavigableSet<P>>() {}.where(new TypeParameter<P>() {}, param);
			}
		});
		unaryTypes.put(BetterCollection.class, new UnaryCompoundTypeCreator<BetterCollection>() {
			@Override
			public <P> TypeToken<? extends BetterCollection> createCompoundType(TypeToken<P> param) {
				return new TypeToken<BetterCollection<P>>() {}.where(new TypeParameter<P>() {}, param);
			}
		});
		unaryTypes.put(CollectionElement.class, new UnaryCompoundTypeCreator<CollectionElement>() {
			@Override
			public <P> TypeToken<? extends CollectionElement> createCompoundType(TypeToken<P> param) {
				return new TypeToken<CollectionElement<P>>() {}.where(new TypeParameter<P>() {}, param);
			}
		});
		unaryTypes.put(MutableCollectionElement.class, new UnaryCompoundTypeCreator<MutableCollectionElement>() {
			@Override
			public <P> TypeToken<? extends MutableCollectionElement> createCompoundType(TypeToken<P> param) {
				return new TypeToken<MutableCollectionElement<P>>() {}.where(new TypeParameter<P>() {}, param);
			}
		});
		unaryTypes.put(BetterList.class, new UnaryCompoundTypeCreator<BetterList>() {
			@Override
			public <P> TypeToken<? extends BetterList> createCompoundType(TypeToken<P> param) {
				return new TypeToken<BetterList<P>>() {}.where(new TypeParameter<P>() {}, param);
			}
		});
		unaryTypes.put(BetterSet.class, new UnaryCompoundTypeCreator<BetterSet>() {
			@Override
			public <P> TypeToken<? extends BetterSet> createCompoundType(TypeToken<P> param) {
				return new TypeToken<BetterSet<P>>() {}.where(new TypeParameter<P>() {}, param);
			}
		});
		unaryTypes.put(BetterSortedSet.class, new UnaryCompoundTypeCreator<BetterSortedSet>() {
			@Override
			public <P> TypeToken<? extends BetterSortedSet> createCompoundType(TypeToken<P> param) {
				return new TypeToken<BetterSortedSet<P>>() {}.where(new TypeParameter<P>() {}, param);
			}
		});

		binaryTypes.put(Map.class, new BinaryCompoundTypeCreator<Map>() {
			@Override
			public <P1, P2> TypeToken<? extends Map> createCompoundType(TypeToken<P1> param1, TypeToken<P2> param2) {
				return new TypeToken<Map<P1, P2>>() {}.where(new TypeParameter<P1>() {}, param1).where(new TypeParameter<P2>() {}, param2);
			}
		});
		binaryTypes.put(SortedMap.class, new BinaryCompoundTypeCreator<SortedMap>() {
			@Override
			public <P1, P2> TypeToken<? extends SortedMap> createCompoundType(TypeToken<P1> param1, TypeToken<P2> param2) {
				return new TypeToken<SortedMap<P1, P2>>() {}.where(new TypeParameter<P1>() {}, param1).where(new TypeParameter<P2>() {},
					param2);
			}
		});
		binaryTypes.put(NavigableMap.class, new BinaryCompoundTypeCreator<NavigableMap>() {
			@Override
			public <P1, P2> TypeToken<? extends NavigableMap> createCompoundType(TypeToken<P1> param1, TypeToken<P2> param2) {
				return new TypeToken<NavigableMap<P1, P2>>() {}.where(new TypeParameter<P1>() {}, param1).where(new TypeParameter<P2>() {},
					param2);
			}
		});
		binaryTypes.put(BetterMap.class, new BinaryCompoundTypeCreator<BetterMap>() {
			@Override
			public <P1, P2> TypeToken<? extends BetterMap> createCompoundType(TypeToken<P1> param1, TypeToken<P2> param2) {
				return new TypeToken<BetterMap<P1, P2>>() {}.where(new TypeParameter<P1>() {}, param1).where(new TypeParameter<P2>() {},
					param2);
			}
		});
		binaryTypes.put(BetterSortedMap.class, new BinaryCompoundTypeCreator<BetterSortedMap>() {
			@Override
			public <P1, P2> TypeToken<? extends BetterSortedMap> createCompoundType(TypeToken<P1> param1, TypeToken<P2> param2) {
				return new TypeToken<BetterSortedMap<P1, P2>>() {}.where(new TypeParameter<P1>() {}, param1)
					.where(new TypeParameter<P2>() {}, param2);
			}
		});
		binaryTypes.put(MultiMap.class, new BinaryCompoundTypeCreator<MultiMap>() {
			@Override
			public <P1, P2> TypeToken<? extends MultiMap> createCompoundType(TypeToken<P1> param1, TypeToken<P2> param2) {
				return new TypeToken<MultiMap<P1, P2>>() {}.where(new TypeParameter<P1>() {}, param1).where(new TypeParameter<P2>() {},
					param2);
			}
		});
		binaryTypes.put(BetterMultiMap.class, new BinaryCompoundTypeCreator<BetterMultiMap>() {
			@Override
			public <P1, P2> TypeToken<? extends BetterMultiMap> createCompoundType(TypeToken<P1> param1, TypeToken<P2> param2) {
				return new TypeToken<BetterMultiMap<P1, P2>>() {}.where(new TypeParameter<P1>() {}, param1)
					.where(new TypeParameter<P2>() {}, param2);
			}
		});
		binaryTypes.put(BetterSortedMultiMap.class, new BinaryCompoundTypeCreator<BetterSortedMultiMap>() {
			@Override
			public <P1, P2> TypeToken<? extends BetterSortedMultiMap> createCompoundType(TypeToken<P1> param1, TypeToken<P2> param2) {
				return new TypeToken<BetterSortedMultiMap<P1, P2>>() {}.where(new TypeParameter<P1>() {}, param1)
					.where(new TypeParameter<P2>() {}, param2);
			}
		});
		binaryTypes.put(BiTuple.class, new BinaryCompoundTypeCreator<BiTuple>() {
			@Override
			public <P1, P2> TypeToken<? extends BiTuple> createCompoundType(TypeToken<P1> param1, TypeToken<P2> param2) {
				return new TypeToken<BiTuple<P1, P2>>() {}.where(new TypeParameter<P1>() {}, param1).where(new TypeParameter<P2>() {},
					param2);
			}
		});
		binaryTypes.put(Function.class, new BinaryCompoundTypeCreator<Function>() {
			@Override
			public <P1, P2> TypeToken<? extends Function> createCompoundType(TypeToken<P1> param1, TypeToken<P2> param2) {
				return new TypeToken<Function<P1, P2>>() {}.where(new TypeParameter<P1>() {}, param1).where(new TypeParameter<P2>() {},
					param2);
			}
		});

		ternaryTypes.put(TriTuple.class, new TernaryCompoundTypeCreator<TriTuple>() {
			@Override
			public <P1, P2, P3> TypeToken<? extends TriTuple> createCompoundType(TypeToken<P1> param1, TypeToken<P2> param2,
				TypeToken<P3> param3) {
				return new TypeToken<TriTuple<P1, P2, P3>>() {}.where(new TypeParameter<P1>() {}, param1).where(new TypeParameter<P2>() {},
					param2);
			}
		});
		ternaryTypes.put(BiFunction.class, new TernaryCompoundTypeCreator<BiFunction>() {
			@Override
			public <P1, P2, P3> TypeToken<? extends BiFunction> createCompoundType(TypeToken<P1> param1, TypeToken<P2> param2,
				TypeToken<P3> param3) {
				return new TypeToken<BiFunction<P1, P2, P3>>() {}.where(new TypeParameter<P1>() {}, param1)
					.where(new TypeParameter<P2>() {}, param2);
			}
		});
	}

	/** @return The common instanceof this class */
	public static final TypeTokens get() {
		return instance;
	}

	public static abstract class UnaryCompoundTypeCreator<T> {
		public abstract <P> TypeToken<? extends T> createCompoundType(TypeToken<P> param);
	}

	public static abstract class BinaryCompoundTypeCreator<T> {
		public abstract <P1, P2> TypeToken<? extends T> createCompoundType(TypeToken<P1> param1, TypeToken<P2> param2);
	}

	public static abstract class TernaryCompoundTypeCreator<T> {
		public abstract <P1, P2, P3> TypeToken<? extends T> createCompoundType(TypeToken<P1> param1, TypeToken<P2> param2,
			TypeToken<P3> param3);
	}

	public class TypeKey<T> {
		public final Class<T> clazz;
		public final int typeParameters;
		public final TypeToken<T> type;
		private TypeToken<?> parameterizedType;
		public final boolean isBoolean;
		public final Class<? extends Number> number;
		public final boolean comparable;
		public final List<T> enumConstants;
		private Object theCompoundTypeCreator;
		private Map<List<TypeToken<?>>, TypeToken<? extends T>> theCompoundTypes;
		private Map<Object, Object> theCache;

		TypeKey(Class<T> clazz) {
			this.clazz = clazz;
			typeParameters = clazz.getTypeParameters().length;
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
				number = Short.class;
			} else {
				number = null;
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
			switch (typeParameters) {
			case 1:
				theCompoundTypeCreator = STANDARD_UNARY_TYPE_CREATORS.get(clazz);
				break;
			case 2:
				theCompoundTypeCreator = STANDARD_BINARY_TYPE_CREATORS.get(clazz);
				break;
			case 3:
				theCompoundTypeCreator = STANDARD_TERNARY_TYPE_CREATORS.get(clazz);
				break;
			}
			if (typeParameters > 0)
				theCompoundTypes = new ConcurrentHashMap<>();
		}

		public TypeKey<T> enableCompoundTypes(UnaryCompoundTypeCreator<T> creator) {
			if (typeParameters != 1)
				throw new IllegalArgumentException("Type " + clazz.getName() + " has " + typeParameters + " parameters, not 1");
			else if (theCompoundTypeCreator != null) {
				System.err.println("Compound types already enabled for " + clazz.getName());
				return this;
			}
			theCompoundTypeCreator = creator;
			if (theCompoundTypes == null)
				theCompoundTypes = new ConcurrentHashMap<>();
			return this;
		}

		public TypeKey<T> enableCompoundTypes(BinaryCompoundTypeCreator<T> creator) {
			if (typeParameters != 2)
				throw new IllegalArgumentException("Type " + clazz.getName() + " has " + typeParameters + " parameters, not 2");
			else if (theCompoundTypeCreator != null) {
				System.err.println("Compound types already enabled for " + clazz.getName());
				return this;
			}
			theCompoundTypeCreator = creator;
			if (theCompoundTypes == null)
				theCompoundTypes = new ConcurrentHashMap<>();
			return this;
		}

		public TypeKey<T> enableCompoundTypes(TernaryCompoundTypeCreator<T> creator) {
			if (typeParameters != 3)
				throw new IllegalArgumentException("Type " + clazz.getName() + " has " + typeParameters + " parameters, not 3");
			else if (theCompoundTypeCreator != null) {
				System.err.println("Compound types already enabled for " + clazz.getName());
				return this;
			}
			theCompoundTypeCreator = creator;
			if (theCompoundTypes == null)
				theCompoundTypes = new ConcurrentHashMap<>();
			return this;
		}

		public boolean areCompoundTypesEnabled() {
			return theCompoundTypeCreator != null;
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
		 * @param paramTypes The parameter types
		 * @return The compound type token
		 */
		public <C extends T> TypeToken<C> getCompoundType(Class<?>... paramTypes) {
			if (paramTypes.length != typeParameters)
				throw new IllegalArgumentException("Type " + clazz.getName() + " has " + typeParameters
					+ " parameters; cannot be parameterized with " + Arrays.toString(paramTypes));
			else if (theCompoundTypeCreator == null)
				throw new IllegalStateException("Compound types have not been enabled for " + clazz.getName());
			TypeToken<?>[] tts = new TypeToken[paramTypes.length];
			for (int i = 0; i < typeParameters; i++) {
				tts[i] = of(paramTypes[i]);
			}
			return getCompoundType(tts);
		}

		public <C extends T> TypeToken<C> getCompoundType(TypeToken<?>... paramTypes) {
			if (paramTypes.length != typeParameters)
				throw new IllegalArgumentException("Type " + clazz.getName() + " has " + typeParameters
					+ " parameters; cannot be parameterized with " + Arrays.toString(paramTypes));
			else if (theCompoundTypeCreator == null)
				throw new IllegalStateException("Compound types have not been enabled for " + clazz.getName());
			return (TypeToken<C>) theCompoundTypes.computeIfAbsent(Arrays.asList(paramTypes), this::createCompoundType);
		}

		private TypeToken<? extends T> createCompoundType(List<TypeToken<?>> paramTypes) {
			switch (typeParameters) {
			case 1:
				return ((UnaryCompoundTypeCreator<T>) theCompoundTypeCreator).createCompoundType(paramTypes.get(0));
			case 2:
				return ((BinaryCompoundTypeCreator<T>) theCompoundTypeCreator).createCompoundType(paramTypes.get(0), paramTypes.get(1));
			case 3:
				return ((TernaryCompoundTypeCreator<T>) theCompoundTypeCreator).createCompoundType(paramTypes.get(0), paramTypes.get(1),
					paramTypes.get(2));
			default:
				throw new IllegalStateException("Compound types unsupported for " + typeParameters + "-ary parameterized types");
			}
		}

		public <P extends T> TypeToken<P> parameterized() {
			if (parameterizedType == null) {
				TypeToken<?>[] paramTypes = new TypeToken[typeParameters];
				Arrays.fill(paramTypes, WILDCARD);
				parameterizedType = getCompoundType(paramTypes);
			}
			return (TypeToken<P>) parameterizedType;
		}

		public synchronized <X> X getCached(Object key, Supplier<X> supplier) {
			if (theCache == null)
				theCache = new HashMap<>();
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
	private final Map<Class<?>, Class<?>> PRIMITIVE_TO_WRAPPER;
	private final Map<Class<?>, Class<?>> WRAPPER_TO_PRIMITIVE;

	public final TypeToken<String> STRING;
	public final TypeToken<Boolean> BOOLEAN;
	public final TypeToken<Double> DOUBLE;
	public final TypeToken<Float> FLOAT;
	public final TypeToken<Long> LONG;
	public final TypeToken<Integer> INT;
	public final TypeToken<Short> SHORT;
	public final TypeToken<Byte> BYTE;
	public final TypeToken<Character> CHAR;
	public final TypeToken<Object> OBJECT;
	public final TypeToken<Void> VOID;
	public final TypeToken<?> WILDCARD;
	public final TypeToken<Class<?>> CLASS;

	/** Creates a new instance */
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
		wrappers.put(void.class, Void.class);
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
		wrappers.put(Void.class, void.class);
		WRAPPER_TO_PRIMITIVE = Collections.unmodifiableMap(wrappers);

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
		CLASS = keyFor(Class.class).parameterized();
		// WILDCARD = new TypeToken<Class<?>>() {}.resolveType(Class.class.getTypeParameters()[0]);
	}

	protected <T> TypeKey<T> createKey(Class<T> type) {
		return new TypeKey<>(type);
	}

	public <T> TypeKey<T> keyFor(Class<T> type) {
		return (TypeKey<T>) TYPES.computeIfAbsent(type, this::createKey);
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
		else if (type == char.class)
			return (Class<T>) Character.class;
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

	public <X> X castAssignable(TypeToken<X> type, Object value) throws ClassCastException {
		if (value == null) {
			if (type.isPrimitive())
				throw new ClassCastException("Null cannot be cast to primitive type " + type);
			else
				return null;
		} else if (!isAssignable(type, TypeTokens.get().of(value.getClass())))
			throw new ClassCastException("Cannot cast instance of " + value.getClass().getName() + " to " + type);
		return (X) value;
	}

	public <X> TypeToken<X> getCommonType(TypeToken<? extends X>... types) {
		return getCommonType(Arrays.asList(types));
	}

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

	public <X> List<TypeToken<? super X>> decompose(TypeToken<X> type) {
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
				type = (TypeToken<X>) of(byte[].class);
			else if (raw == int.class)
				type = (TypeToken<X>) of(byte[].class);
			else if (raw == long.class)
				type = (TypeToken<X>) of(byte[].class);
			else if (raw == float.class)
				type = (TypeToken<X>) of(byte[].class);
			else if (raw == double.class)
				type = (TypeToken<X>) of(byte[].class);
			else
				throw new IllegalStateException("Unrecognized primitive type: " + type);

			dimCount--;
		}
		for (int i = 0; i < dimCount; i++) {
			type = (TypeToken<X>) new TypeToken<X[]>() {}.where(new TypeParameter<X>() {}, type);
		}
		return type;
	}

	public <D extends GenericDeclaration, X> TypeToken<X> getTypeVariable(String variableName, D declaration) {
		return (TypeToken<X>) TypeToken.of(new TypeVariableImpl<>(declaration, variableName));
	}

	public <X> TypeToken<? extends X> getExtendsWildcard(TypeToken<X> type) {
		return (TypeToken<? extends X>) TypeToken.of(new WildcardTypeImpl(new Type[0], new Type[] { type.getType() }));
	}

	public <X> TypeToken<? super X> getSuperWildcard(TypeToken<X> type) {
		return (TypeToken<? super X>) TypeToken.of(new WildcardTypeImpl(new Type[] { type.getType() }, new Type[0]));
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
