package org.observe.util;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntFunction;

import org.observe.entity.impl.ObservableEntityUtils;
import org.qommons.QommonsUtils;
import org.qommons.collect.QuickSet;
import org.qommons.collect.QuickSet.QuickMap;

import com.google.common.reflect.Invokable;
import com.google.common.reflect.TypeToken;

/**
 * A utility for reflectively compiling a public interface's fields with getters and setters. Especially, this class's
 * {@link #newInstance(IntFunction, BiConsumer) newInstance} method allows the caller to easily create new instances of the target interface
 * backed by a custom data set.
 *
 * @param <E> The type of interface entity this reflector is for
 */
public class EntityReflector<E> {
	private static final Constructor<Lookup> LOOKUP_CONSTRUCTOR;
	static {
		Constructor<Lookup> c;
		try {
			c = Lookup.class.getDeclaredConstructor(Class.class);
			c.setAccessible(true);
		} catch (NoSuchMethodException e) {
			System.err.println("Lookup class has changed: unable to call default or Object methods on entities");
			e.printStackTrace();
			c = null;
		} catch (SecurityException e) {
			System.err.println("Could not access Lookup: unable to call default or Object methods on entities");
			c = null;
		}
		LOOKUP_CONSTRUCTOR = c;
	}

	/**
	 * @param type The type to test
	 * @return Whether the type is an entity type candidate to be reflected by this class
	 */
	public static boolean isEntityType(Class<?> type) {
		return type.isInterface() && (type.getModifiers() & Modifier.PUBLIC) != 0;
	}

	/**
	 * @param type The interface type to reflect on
	 * @return A builder to create a reflector
	 */
	public static <E> Builder<E> build(TypeToken<E> type) {
		return new Builder<>(type);
	}

	/**
	 * Builds an {@link EntityReflector}
	 *
	 * @param <E> The interface type to reflect on
	 */
	public static class Builder<E> {
		private final TypeToken<E> theType;
		private final Class<E> theRawType;
		private Function<Method, String> theGetterFilter;
		private Function<Method, String> theSetterFilter;
		private Map<Method, BiFunction<? super E, Object[], ?>> theCustomMethods;
		private Set<String> theIdFields;

		private Builder(TypeToken<E> type) {
			theRawType = TypeTokens.getRawType(type);
			if (theRawType == null || !theRawType.isInterface())
				throw new IllegalArgumentException("This class only works for interface types");
			else if ((theRawType.getModifiers() & Modifier.PUBLIC) == 0)
				throw new IllegalArgumentException("This class only works for public interface types");
			theType = type;
			theGetterFilter = new PrefixFilter("get", 0);
			theSetterFilter = new PrefixFilter("set", 1);
		}

		/**
		 * @param getterFilter The function to determine what field a getter is for
		 * @return This builder
		 */
		public Builder<E> withGetterFilter(Function<Method, String> getterFilter) {
			theGetterFilter = getterFilter;
			return this;
		}

		/**
		 * @param setterFilter The function to determine what field a setter is for
		 * @return This builder
		 */
		public Builder<E> withSetterFilter(Function<Method, String> setterFilter) {
			theSetterFilter = setterFilter;
			return this;
		}

		/**
		 * @param customMethods Custom method implementations for {@link EntityReflector#newInstance(IntFunction, BiConsumer) created
		 *        instances} of the type to use
		 * @return This builder
		 */
		public Builder<E> withCustomMethods(Map<Method, ? extends BiFunction<? super E, Object[], ?>> customMethods) {
			if (theCustomMethods == null)
				theCustomMethods = new LinkedHashMap<>();
			theCustomMethods.putAll(customMethods);
			return this;
		}

		/**
		 * @param method The method to implement
		 * @param implementation The custom implementation of the method for {@link EntityReflector#newInstance(IntFunction, BiConsumer)
		 *        created instances} of the type to use
		 * @return This builder
		 */
		public Builder<E> withCustomMethod(Method method, BiFunction<? super E, Object[], ?> implementation) {
			if (theCustomMethods == null)
				theCustomMethods = new LinkedHashMap<>();
			theCustomMethods.put(method, implementation);
			return this;
		}

		public Builder<E> withId(String... ids) {
			if (theIdFields == null)
				theIdFields = new LinkedHashSet<>();
			for (String id : ids)
				theIdFields.add(id);
			return this;
		}

		/** @return The new reflector */
		public EntityReflector<E> build() {
			return new EntityReflector<>(theType, theRawType, theGetterFilter, theSetterFilter, theCustomMethods, theIdFields);
		}
	}

	/** A simple filter function to determine whether a method is a getter or setter for a field */
	public static class PrefixFilter implements Function<Method, String> {
		private final String thePrefix;
		private final int theParameterCount;

		/**
		 * @param prefix The prefix to look for
		 * @param paramCount The number of parameters the method should accept
		 */
		public PrefixFilter(String prefix, int paramCount) {
			thePrefix = prefix;
			theParameterCount = paramCount;
		}

		/** @return The prefix that this filter looks for */
		public String getPrefix() {
			return thePrefix;
		}

		/** @return The number of parameters a method must accept in order to pass this filter */
		public int getParameterCount() {
			return theParameterCount;
		}

		@Override
		public String apply(Method m) {
			if ((m.getModifiers() & Modifier.PUBLIC) == 0 || (m.getModifiers() & Modifier.STATIC) != 0)
				return null; // Must be public and not static
			else if (theParameterCount == 0 && (m.getReturnType() == void.class || m.getReturnType() == Void.class))
				return null; // Must have non-void return type
			else if (m.getParameterTypes().length != theParameterCount)
				return null;
			else if (!m.getName().startsWith(thePrefix) || m.getName().length() == thePrefix.length())
				return null;
			StringBuilder s = new StringBuilder(m.getName().substring(thePrefix.length()));
			s.setCharAt(0, Character.toLowerCase(s.charAt(0)));
			return s.toString();
		}
	}

	/**
	 * Represents a field in an interface entity type
	 *
	 * @param <E> The entity type
	 * @param <F> The field type
	 */
	public static class ReflectedField<E, F> {
		private final String theName;
		private int theFieldIndex;
		private boolean id;
		private final Invokable<? super E, F> theGetter;
		private final Invokable<? super E, ?> theSetter;
		private final SetterReturnType theSetterReturnType;

		ReflectedField(String name, int fieldIndex, boolean id, Invokable<? super E, F> getter, Invokable<? super E, ?> setter,
			SetterReturnType setterReturnType) {
			theName = name;
			theFieldIndex = fieldIndex;
			this.id = id;
			theGetter = getter;
			theSetter = setter;
			theSetterReturnType = setterReturnType;
		}

		/** @return The name of the field */
		public String getName() {
			return theName;
		}

		/** @return The field index, i.e. the index of this field in {@link EntityReflector#getFields()} */
		public int getFieldIndex() {
			return theFieldIndex;
		}

		public boolean isId() {
			return id;
		}

		/** @return The entity type's getter method for this field */
		public Invokable<? super E, F> getGetter() {
			return theGetter;
		}

		/** @return The entity type's setter method for this field */
		public Invokable<? super E, ?> getSetter() {
			return theSetter;
		}

		@Override
		public String toString() {
			return theName + " (" + theGetter.getReturnType() + ")";
		}
	}

	private enum SetterReturnType {
		OLD_VALUE, SELF, VOID;
	}

	static class MethodSignature implements Comparable<MethodSignature> {
		final String name;
		final Class<?>[] parameters;

		MethodSignature(Method method) {
			this.name = method.getName();
			this.parameters = method.getParameterTypes();
		}

		@Override
		public int compareTo(MethodSignature o) {
			if (this == o)
				return 0;
			int comp = QommonsUtils.compareNumberTolerant(name, o.name, true, true);
			if (comp != 0)
				return comp;
			comp = Integer.compare(parameters.length, o.parameters.length);
			if (comp != 0)
				return comp;
			for (int i = 0; i < parameters.length; i++) {
				comp = QommonsUtils.compareNumberTolerant(parameters[i].getName(), o.parameters[i].getName(), true, true);
				if (comp != 0)
					return comp;
			}
			return 0;
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder(name).append('(');
			for (int i = 0; i < parameters.length; i++) {
				if (i > 0)
					str.append(", ");
				str.append(parameters[i]);
			}
			return str.append(')').toString();
		}
	}

	private final TypeToken<E> theType;
	private final Function<Method, String> theGetterFilter;
	private final Function<Method, String> theSetterFilter;
	private final QuickMap<String, ReflectedField<E, ?>> theFields;
	private final QuickMap<String, ReflectedField<E, ?>> theFieldsByGetter;
	private final QuickMap<String, ReflectedField<E, ?>> theFieldsBySetter;
	private final QuickMap<MethodSignature, BiFunction<? super E, Object[], ?>> theCustomMethods;
	private final QuickMap<MethodSignature, MethodHandle> theDefaultMethods;
	private final Set<Integer> theIdFields;
	private final E theProxy;
	private final MethodRetrievingHandler theProxyHandler;

	private EntityReflector(TypeToken<E> type, Class<E> raw, Function<Method, String> getterFilter, Function<Method, String> setterFilter,
		Map<Method, ? extends BiFunction<? super E, Object[], ?>> customMethods, Set<String> idFieldNames) {
		theType = type;
		theGetterFilter = getterFilter == null ? new PrefixFilter("get", 0) : getterFilter;
		theSetterFilter = setterFilter == null ? new PrefixFilter("set", 1) : setterFilter;
		Map<String, Invokable<? super E, ?>> fieldGetters = new LinkedHashMap<>();
		Map<String, Invokable<? super E, ?>> fieldSetters = new LinkedHashMap<>();
		Map<MethodSignature, MethodHandle> defaultMethods = new LinkedHashMap<>();
		populateMethods(theType, fieldGetters, theGetterFilter, fieldSetters, theSetterFilter, defaultMethods, //
			customMethods == null ? Collections.emptySet() : customMethods.keySet());
		QuickMap<String, ReflectedField<E, ?>> fields = QuickSet.of(fieldGetters.keySet()).createMap();
		LinkedHashSet<Integer> idFields = new LinkedHashSet<>();
		if (idFieldNames != null) {
			for (String id : idFieldNames) {
				int i = fields.keyIndexTolerant(id);
				if (i < 0)
					throw new IllegalArgumentException("No such field for ID: " + id);
				idFields.add(i);
			}
		} else { // Not specified, see if we can find the IDs ourselves
			// First, see if any fields are tagged with @Id
			for (Map.Entry<String, Invokable<? super E, ?>> getter : fieldGetters.entrySet()) {
				for (Annotation annotation : getter.getValue().getAnnotations()) {
					if (annotation.annotationType().getSimpleName().equalsIgnoreCase("id")) // Any old ID annotation will do
						idFields.add(fields.keySet().indexOf(getter.getKey()));
				}
			}
			if (idFields.isEmpty()) {
				// Otherwise, see if there is a field named ID
				for (int i = 0; i < fields.keySize(); i++)
					if (fields.keySet().get(i).equalsIgnoreCase("id")) {
						idFields.add(i);
						break;
					}
			}
		}
		theIdFields = idFields.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(idFields);
		Map<String, ReflectedField<E, ?>> fieldsByGetter = new LinkedHashMap<>();
		Map<String, ReflectedField<E, ?>> fieldsBySetter = new LinkedHashMap<>();
		for (int i = 0; i < fields.keySet().size(); i++) {
			Invokable<? super E, ?> getter = fieldGetters.get(fields.keySet().get(i));
			Invokable<? super E, ?> setter = fieldSetters.get(fields.keySet().get(i));
			if (setter != null && !setter.getParameters().get(0).getType().isAssignableFrom(getter.getReturnType())) {
				System.err.println("Setter " + setter + " for field with getter " + getter + " must accept a " + getter.getReturnType());
				continue;
			}
			SetterReturnType setterReturnType = null;
			if (setter != null) {
				Class<?> rawSetterRT = TypeTokens.getRawType(setter.getReturnType());
				if (rawSetterRT == void.class || rawSetterRT == Void.class)
					setterReturnType = SetterReturnType.VOID;
				else if (setter.getReturnType().isAssignableFrom(type))
					setterReturnType = SetterReturnType.SELF;
				else if (setter.getReturnType().isAssignableFrom(getter.getReturnType()))
					setterReturnType = SetterReturnType.OLD_VALUE;
				else {
					System.err.println("Return type of setter " + setter + " cannot be satisfied for this class");
					setter = null;
				}
			}
			ReflectedField<E, ?> field = new ReflectedField<>(fields.keySet().get(i), i, idFields.contains(i), getter, setter,
				setterReturnType);
			fields.put(i, field);
			fieldsByGetter.put(getter.getName(), field);
			if (setter != null)
				fieldsBySetter.put(setter.getName(), field);
		}
		theFields = fields.unmodifiable();
		theFieldsByGetter = QuickMap.of(fieldsByGetter, String::compareTo).unmodifiable();
		theFieldsBySetter = QuickMap.of(fieldsBySetter, String::compareTo).unmodifiable();
		theDefaultMethods = QuickMap.of(defaultMethods, MethodSignature::compareTo).unmodifiable();
		Map<MethodSignature, BiFunction<? super E, Object[], ?>> internalCustomMethods = new LinkedHashMap<>();
		if (customMethods != null) {
			for (Map.Entry<Method, ? extends BiFunction<? super E, Object[], ?>> method : customMethods.entrySet())
				internalCustomMethods.put(new MethodSignature(method.getKey()), method.getValue());
		}
		theCustomMethods = QuickMap.of(internalCustomMethods, MethodSignature::compareTo).unmodifiable();

		theProxyHandler = new MethodRetrievingHandler();
		theProxy = (E) Proxy.newProxyInstance(ObservableEntityUtils.class.getClassLoader(), new Class[] { raw }, theProxyHandler);
	}

	private static <T> void populateMethods(TypeToken<T> type,//
		Map<String, Invokable<? super T, ?>> fieldGetters,Function<Method, String> getterFilter, //
		Map<String, Invokable<? super T, ?>> fieldSetters, Function<Method, String> setterFilter,//
		Map<MethodSignature, MethodHandle> defaultMethods, Set<Method> customMethods) {
		Class<T> clazz = TypeTokens.getRawType(type);
		if (clazz == null || clazz == Object.class)
			return;
		for (Method m : clazz.getDeclaredMethods()) {
			if (customMethods.contains(m))
				continue;
			if(m.isDefault()){
				if (LOOKUP_CONSTRUCTOR == null) {
					continue;
				}
				try{
					defaultMethods.put(new MethodSignature(m), LOOKUP_CONSTRUCTOR.newInstance(clazz).in(clazz).unreflectSpecial(m, clazz));
				} catch (IllegalArgumentException | InstantiationException | InvocationTargetException e) {
					throw new IllegalStateException("Bad method? " + m + ": " + e);
				} catch (SecurityException | IllegalAccessException e) {
					System.err.println("No access to "+m+": "+e);
				}
				continue;
			}
			String fieldName = getterFilter.apply(m);
			if (fieldName != null) {
				fieldGetters.put(fieldName, type.method(m));
				continue;
			}
			fieldName = setterFilter.apply(m);
			if (fieldName != null) {
				fieldSetters.put(fieldName, type.method(m));
				continue;
			}
			System.err.println("Method " + m + " is not default or a recognized getter or setter, and its implementation is not provided");
		}
		for (Class<?> intf : clazz.getInterfaces())
			populateMethods((TypeToken<T>) type.resolveType(intf), // Generics hack
				fieldGetters, getterFilter,//
				fieldSetters, setterFilter,//
				defaultMethods, customMethods);
	}

	/** @return The interface entity type */
	public TypeToken<E> getType() {
		return theType;
	}

	/** @return This entity's fields */
	public QuickMap<String, ReflectedField<E, ?>> getFields() {
		return theFields;
	}

	/** @return The set of field IDs that are marked as identifying for this type */
	public Set<Integer> getIdFields() {
		return theIdFields;
	}

	/**
	 * @param fieldGetter A function invoking a field getter on this type
	 * @return The index in {@link #getFields()} of the field invoked. If the function invokes multiple methods, this will be last method
	 *         invoked
	 * @throws IllegalArgumentException If the function does not invoke a field getter or the method the function invokes last is not an
	 *         included getter method
	 */
	public int getFieldIndex(Function<? super E, ?> fieldGetter) throws IllegalArgumentException {
		Method invoked;
		synchronized (this) {
			theProxyHandler.reset();
			fieldGetter.apply(theProxy);
			invoked = theProxyHandler.getInvoked();
		}
		if (invoked == null)
			throw new IllegalArgumentException("No " + theType + " field getter invoked");
		int idx = theFieldsByGetter.keySet().indexOf(invoked.getName());
		if (idx < 0)
			throw new IllegalArgumentException(invoked + " is not a " + theType + " field getter");
		return idx;
	}

	/**
	 * Creates a new instance of the type, backed by the given getter/setter implementations. The integer inputs to both functions are the
	 * {@link ReflectedField#getFieldIndex()}, which is also the index of the field in {@link #getFields()}.
	 *
	 * @param fieldGetter The field getter implementation for the new instance
	 * @param fieldSetter The field setter implementation for the new instance
	 * @return The new entity instance
	 */
	public E newInstance(IntFunction<Object> fieldGetter, BiConsumer<Integer, Object> fieldSetter) {
		Class<E> rawType = TypeTokens.getRawType(theType);
		return (E) Proxy.newProxyInstance(rawType.getClassLoader(), new Class[] { rawType },
			new ProxyMethodHandler(fieldGetter, fieldSetter));
	}

	/**
	 * Allows the association of an entity with a custom piece of information (retrievable via {@link #getAssociated(Object)})
	 *
	 * @param proxy The entity created with {@link #newInstance(IntFunction, BiConsumer)}
	 * @param key The key to associate the data with
	 * @param associated The data to associate with the entity for the key
	 * @return The entity
	 */
	public E associate(E proxy, Object key, Object associated) {
		ProxyMethodHandler handler = (EntityReflector<E>.ProxyMethodHandler) Proxy.getInvocationHandler(proxy);
		handler.associate(key, associated);
		return proxy;
	}

	/**
	 * @param proxy The entity created with {@link #newInstance(IntFunction, BiConsumer)}
	 * @param key The key the data is associated with
	 * @return The data {@link #associate(Object, Object, Object) associated} with the entity and key
	 */
	public Object getAssociated(E proxy, Object key) {
		return ((ProxyMethodHandler) Proxy.getInvocationHandler(proxy)).getAssociated(key);
	}

	public Object getField(E proxy, int fieldIndex) {
		ProxyMethodHandler handler = (EntityReflector<E>.ProxyMethodHandler) Proxy.getInvocationHandler(proxy);
		return handler.theFieldGetter.apply(fieldIndex);
	}

	public void setField(E proxy, int fieldIndex, Object value) {
		ProxyMethodHandler handler = (EntityReflector<E>.ProxyMethodHandler) Proxy.getInvocationHandler(proxy);
		handler.theFieldSetter.accept(fieldIndex, value);
	}

	private class ProxyMethodHandler implements InvocationHandler {
		private final IntFunction<Object> theFieldGetter;
		private final BiConsumer<Integer, Object> theFieldSetter;
		private final Class<?> theRawType;
		private IdentityHashMap<Object, Object> theAssociated;

		ProxyMethodHandler(IntFunction<Object> fieldGetter, BiConsumer<Integer, Object> fieldSetter) {
			theFieldGetter = fieldGetter;
			theFieldSetter = fieldSetter;
			theRawType = TypeTokens.getRawType(theType);
		}

		private EntityReflector<E> getReflector() {
			return EntityReflector.this;
		}

		Object associate(Object key, Object value) {
			if (theAssociated == null)
				theAssociated = new IdentityHashMap<>();
			return theAssociated.put(key, value);
		}

		Object getAssociated(Object key) {
			return theAssociated == null ? null : theAssociated.get(key);
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			MethodSignature sig = new MethodSignature(method);
			int idx = theCustomMethods.keyIndexTolerant(sig);
			if (idx >= 0)
				return theCustomMethods.get(idx).apply((E) proxy, args);
			idx = theDefaultMethods.keyIndexTolerant(sig);
			if (idx >= 0) {
				MethodHandle handle = theDefaultMethods.get(idx);
				synchronized (handle) {
					return handle.bindTo(proxy).invokeWithArguments(args);
				}
			}
			idx = theFieldsBySetter.keyIndexTolerant(method.getName());
			if (idx >= 0) {
				ReflectedField<E, ?> field = theFieldsBySetter.get(idx);
				Object returnValue;
				switch (field.theSetterReturnType) {
				case SELF:
					returnValue = proxy;
					break;
				case OLD_VALUE:
					returnValue = theFieldGetter.apply(field.getFieldIndex());
					break;
				default:
					returnValue = null;
					break;
				}
				theFieldSetter.accept(field.getFieldIndex(), args[0]);
				return returnValue;
			}
			idx = theFieldsByGetter.keyIndexTolerant(method.getName());
			if (idx >= 0) {
				ReflectedField<E, ?> field = theFieldsByGetter.get(idx);
				return theFieldGetter.apply(field.getFieldIndex());
			}
			if (method.getDeclaringClass() == Object.class) {
				switch (method.getName()) {
				case "hashCode":
					if (method.getParameterTypes().length == 0)
						return hashCodeImpl(proxy);
					break;
				case "equals":
					if (args.length == 1 && method.getParameterTypes().length == 1 && method.getParameterTypes()[0] == Object.class)
						return equalsImpl(proxy, args[0]);
					break;
				case "getClass": // Does this get delegated to here?
					return theRawType;
				case "clone":
					throw new CloneNotSupportedException("Cannot clone proxies");
				case "toString":
					return buildString((E) proxy);
				case "notify":
					this.notify();
					return null;
				case "notifyAll":
					this.notifyAll();
					return null;
				case "wait":
					if (args.length == 0)
						wait();
					else if (args.length == 1)
						wait((Long) args[0]);
					else
						wait((Long) args[0], (Integer) args[1]);
					return null;
				case "finalize":
					return null;
				default:
					throw new IllegalStateException("Unrecognized object method: " + method);
				}
			}
			throw new IllegalStateException(
				"Method " + method + " is not default or a recognized getter or setter, and its implementation is not provided");
		}

		private int hashCodeImpl(Object proxy) {
			if (getIdFields().isEmpty())
				return System.identityHashCode(proxy);
			Object[] idValues = new Object[getIdFields().size()];
			int i = 0;
			for (int id : getIdFields()) {
				idValues[i++] = theFieldGetter.apply(id);
			}
			return Objects.hash(idValues);
		}

		private boolean equalsImpl(Object proxy, Object arg) {
			if (proxy == arg)
				return true;
			else if (getIdFields().isEmpty())
				return false;
			else if (arg == null)
				return false;
			else if (!Proxy.isProxyClass(arg.getClass())
				|| !(Proxy.getInvocationHandler(arg) instanceof EntityReflector.ProxyMethodHandler))
				return false;
			EntityReflector<?>.ProxyMethodHandler other = (EntityReflector<?>.ProxyMethodHandler) Proxy.getInvocationHandler(arg);
			if (getReflector() != other.getReflector() || theRawType != other.theRawType)
				return false;
			for (int id : getIdFields()) {
				if (!Objects.equals(theFieldGetter.apply(id), other.theFieldGetter.apply(id)))
					return false;
			}
			return true;
		}

		private Object buildString(E proxy) {
			StringBuilder str = new StringBuilder(theRawType.getSimpleName()).append(": ");
			for (int i = 0; i < theFields.keySet().size(); i++) {
				if (i > 0)
					str.append(", ");
				str.append(theFields.keySet().get(i)).append("=").append(theFieldGetter.apply(i));
			}
			return str.toString();
		}
	}
}
