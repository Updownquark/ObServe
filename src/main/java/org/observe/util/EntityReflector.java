package org.observe.util;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import org.observe.entity.impl.ObservableEntityUtils;
import org.qommons.collect.ParameterSet.ParameterMap;

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

	private final TypeToken<E> theType;
	private final ParameterMap<Invokable<? super E, ?>> theFieldGetters;
	private final ParameterMap<Invokable<? super E, ?>> theFieldSetters;
	private final E theProxy;
	private final MethodRetrievingHandler theProxyHandler;

	public EntityReflector(TypeToken<E> type) {
		this(type, new PrefixFilter("get", 0), new PrefixFilter("set", 1));
	}

	public EntityReflector(TypeToken<E> type, Function<Method, String> getterFilter, Function<Method, String> setterFilter) {
		Class<E> raw = TypeTokens.getRawType(type);
		if (raw == null || !raw.isInterface())
			throw new IllegalArgumentException("This class only works for interface types");
		theType = type;
		Map<String, Invokable<? super E, ?>> fieldGetters = new LinkedHashMap<>();
		Map<String, Invokable<? super E, ?>> fieldSetters = new LinkedHashMap<>();
		addFieldGetters(theType, fieldGetters, fieldSetters, getterFilter, setterFilter);
		theFieldGetters = ParameterMap.of(fieldGetters).unmodifiable();
		ParameterMap<Invokable<? super E, ?>> setters = theFieldGetters.keySet().createMap();
		for (int i = 0; i < setters.keySet().size(); i++) {
			String fieldName = setters.keySet().get(i);
			setters.put(i, fieldSetters.get(fieldName));
		}
		theFieldSetters = setters.unmodifiable();

		theProxyHandler = new MethodRetrievingHandler();
		theProxy = (E) Proxy.newProxyInstance(ObservableEntityUtils.class.getClassLoader(), new Class[] { raw }, theProxyHandler);
	}

	private static <T> void addFieldGetters(TypeToken<T> type, Map<String, Invokable<? super T, ?>> fieldGetters,
		Map<String, Invokable<? super T, ?>> fieldSetters, Function<Method, String> getterFilter, Function<Method, String> setterFilter) {
		Class<T> clazz = TypeTokens.getRawType(type);
		if (clazz == null || clazz == Object.class)
			return;
		for (Method m : clazz.getDeclaredMethods()) {
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
		}
		for (Class<?> intf : clazz.getInterfaces())
			addFieldGetters((TypeToken<T>) type.resolveType(intf), fieldGetters, fieldSetters, getterFilter, setterFilter); // Generics hack
	}

	public ParameterMap<Invokable<? super E, ?>> getFieldGetters() {
		return theFieldGetters;
	}

	public ParameterMap<Invokable<? super E, ?>> getFieldSetters() {
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
}
