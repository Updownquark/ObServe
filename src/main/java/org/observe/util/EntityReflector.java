package org.observe.util;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntFunction;

import org.observe.entity.impl.ObservableEntityUtils;
import org.qommons.QommonsUtils;
import org.qommons.collect.QuickSet.QuickMap;

import com.google.common.reflect.Invokable;
import com.google.common.reflect.TypeToken;

public class EntityReflector<E> {
	public static class PrefixFilter implements Function<Method, String> {
		private final String thePrefix;
		private final int theParameterCount;

		public PrefixFilter(String prefix, int paramCount) {
			thePrefix = prefix;
			theParameterCount = paramCount;
		}

		public String getPrefix() {
			return thePrefix;
		}

		public int getParameterCount() {
			return theParameterCount;
		}

		@Override
		public String apply(Method m) {
			if ((m.getModifiers() & Modifier.PUBLIC) == 0 || (m.getModifiers() & Modifier.STATIC) != 0)
				return null; // Must be public and not static
			else if (m.getReturnType() == void.class || m.getReturnType() == Void.class)
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
	}

	private final TypeToken<E> theType;
	private final Function<Method, String> theGetterFilter;
	private final Function<Method, String> theSetterFilter;
	private final QuickMap<String, Invokable<? super E, ?>> theFieldGetters;
	private final QuickMap<String, Invokable<? super E, ?>> theFieldSetters;
	private final QuickMap<MethodSignature, Function<Object[], ?>> theCustomMethods;
	private final QuickMap<MethodSignature, MethodHandle> theDefaultMethods;
	private final E theProxy;
	private final MethodRetrievingHandler theProxyHandler;

	public EntityReflector(TypeToken<E> type) {
		this(type, new PrefixFilter("get", 0), new PrefixFilter("set", 1), Collections.emptyMap());
	}

	public EntityReflector(TypeToken<E> type, Function<Method, String> getterFilter, Function<Method, String> setterFilter,
		Map<Method, Function<Object[], ?>> customMethods) {
		Class<E> raw = TypeTokens.getRawType(type);
		if (raw == null || !raw.isInterface())
			throw new IllegalArgumentException("This class only works for interface types");
		theType = type;
		theGetterFilter = getterFilter;
		theSetterFilter = setterFilter;
		Map<String, Invokable<? super E, ?>> fieldGetters = new LinkedHashMap<>();
		Map<String, Invokable<? super E, ?>> fieldSetters = new LinkedHashMap<>();
		Map<MethodSignature, MethodHandle> defaultMethods = new LinkedHashMap<>();
		populateMethods(theType, fieldGetters, getterFilter, fieldSetters, setterFilter, defaultMethods, customMethods.keySet());
		theFieldGetters = QuickMap.of(fieldGetters, QommonsUtils.DISTINCT_NUMBER_TOLERANT).unmodifiable();
		QuickMap<String, Invokable<? super E, ?>> setters = theFieldGetters.keySet().createMap();
		for (int i = 0; i < setters.keySet().size(); i++) {
			String fieldName = setters.keySet().get(i);
			setters.put(i, fieldSetters.get(fieldName));
		}
		theFieldSetters = setters.unmodifiable();
		theDefaultMethods = QuickMap.of(defaultMethods, MethodSignature::compareTo).unmodifiable();
		Map<MethodSignature, Function<Object[], ?>> internalCustomMethods = new LinkedHashMap<>();
		for (Map.Entry<Method, Function<Object[], ?>> method : customMethods.entrySet())
			internalCustomMethods.put(new MethodSignature(method.getKey()), method.getValue());
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
				try{
					defaultMethods.put(new MethodSignature(m), MethodHandles.lookup()
						.in(clazz)
						.unreflectSpecial(m, clazz));
				} catch(IllegalAccessException e){
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

	public QuickMap<String, Invokable<? super E, ?>> getFieldGetters() {
		return theFieldGetters;
	}

	public QuickMap<String, Invokable<? super E, ?>> getFieldSetters() {
		return theFieldSetters;
	}

	/**
	 * @param fieldGetter A function invoking a field getter on this type
	 * @return The index in {@link #getFieldGetters()} of the field invoked. If the function invokes multiple methods, this will be last
	 *         method invoked. If the function does not invoke any getter, -1 will be returned.
	 * @throws IllegalArgumentException If the method the function invokes last is not an included getter method
	 */
	public int getGetterIndex(Function<? super E, ?> fieldGetter) throws IllegalArgumentException {
		Method invoked;
		synchronized (this) {
			theProxyHandler.reset();
			fieldGetter.apply(theProxy);
			invoked = theProxyHandler.getInvoked();
		}
		if (invoked == null)
			return -1;
		int idx = theFieldGetters.keySet().indexOf(invoked.getName());
		if (idx < 0)
			throw new IllegalArgumentException(invoked + " is not a " + theType + " field getter");
		return idx;
	}

	public E newInstance(IntFunction<Object> fieldGetter, BiFunction<Integer, Object, Object> fieldSetter) {
		Class<E> rawType = TypeTokens.getRawType(theType);
		return (E) Proxy.newProxyInstance(rawType.getClassLoader(), new Class[] { rawType }, new InvocationHandler() {
			@Override
			public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
				MethodSignature sig = new MethodSignature(method);
				int idx = theCustomMethods.keyIndexTolerant(sig);
				if (idx >= 0)
					return theCustomMethods.get(idx).apply(args);
				if (method.isDefault()) {
					idx = theDefaultMethods.keyIndexTolerant(sig);
					if (idx < 0)
						throw new IllegalStateException("Could not access default method " + method);
					MethodHandle handle = theDefaultMethods.get(idx);
					synchronized (handle) {
						return handle.bindTo(proxy).invokeWithArguments(args);
					}
				}
				String fieldName = theSetterFilter.apply(method);
				if (fieldName != null) {
					idx = theFieldSetters.keyIndexTolerant(fieldName);
					if (idx >= 0)
						return fieldSetter.apply(idx, args[0]);
				}
				fieldName = theGetterFilter.apply(method);
				if (fieldName != null) {
					idx = theFieldGetters.keyIndexTolerant(fieldName);
					if (idx >= 0)
						return fieldGetter.apply(idx);
				}
				throw new IllegalStateException(
					"Method " + method + " is not default or a recognized getter or setter, and its implementation is not provided");
			}
		});
	}
}
