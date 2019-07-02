package org.observe.util;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

import org.observe.entity.impl.ObservableEntityUtils;
import org.qommons.collect.ParameterSet.ParameterMap;

import com.google.common.reflect.Invokable;
import com.google.common.reflect.TypeToken;

public class EntityReflector<E> {
	public static class DefaultGetterFilter implements Predicate<Method> {
		@Override
		public boolean test(Method t) {
			return (t.getModifiers() & Modifier.PUBLIC) != 0 && (t.getModifiers() & Modifier.STATIC) == 0//
				&& t.getName().startsWith("get") && t.getReturnType() != void.class && t.getReturnType() != Void.class//
				&& t.getParameterTypes().length == 0;
		}
	}

	private final TypeToken<E> theType;
	private final ParameterMap<Invokable<? super E, ?>> theFieldGetters;
	private final E theProxy;
	private final MethodRetrievingHandler theProxyHandler;

	public EntityReflector(TypeToken<E> type) {
		this(type, new DefaultGetterFilter());
	}

	public EntityReflector(TypeToken<E> type, Predicate<Method> getterFilter) {
		Class<E> raw = TypeTokens.getRawType(type);
		if (raw == null || !raw.isInterface())
			throw new IllegalArgumentException("This class only works for interface types");
		theType = type;
		Map<String, Invokable<? super E, ?>> fieldGetters = new LinkedHashMap<>();
		addFieldGetters(theType, fieldGetters, getterFilter);
		theFieldGetters = ParameterMap.of(fieldGetters).unmodifiable();

		theProxyHandler = new MethodRetrievingHandler();
		theProxy = (E) Proxy.newProxyInstance(ObservableEntityUtils.class.getClassLoader(), new Class[] { raw }, theProxyHandler);
	}

	private static <T> void addFieldGetters(TypeToken<T> type, Map<String, Invokable<? super T, ?>> fieldGetters, Predicate<Method> getterFilter) {
		Class<T> clazz = TypeTokens.getRawType(type);
		if (clazz == null || clazz == Object.class)
			return;
		for(Method m : clazz.getDeclaredMethods())
			if(getterFilter.test(m))
				fieldGetters.put(m.getName(), type.method(m));
		for (Class<?> intf : clazz.getInterfaces())
			addFieldGetters((TypeToken<T>) type.resolveType(intf), fieldGetters, getterFilter); // Generics hack
	}

	public ParameterMap<Invokable<? super E, ?>> getFieldGetters() {
		return theFieldGetters;
	}

	/**
	 * @param fieldGetter A function invoking a field getter on this type
	 * @return The index in {@link #getFieldGetters()} of the field invoked. If the function invokes multiple methods, this will be last
	 *         method invoked. If the function does not invoke any getter, -1 will be returned. If the method the function invokes last is
	 *         not an included getter method, -2 will be returned.
	 */
	public int getGetterIndex(Function<? super E, ?> fieldGetter) {
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
			return -2;
		return idx;
	}
}
