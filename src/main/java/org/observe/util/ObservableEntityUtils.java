package org.observe.util;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.Function;

/** Entity reflection utilities for ObServe */
public class ObservableEntityUtils {
	/**
	 * @param entityType The entity type
	 * @param fieldGetter The getter function for a field in the entity
	 * @return The getter method for the field
	 */
	public static <E> Method getField(Class<E> entityType, Function<? super E, ?> fieldGetter) {
		MethodRetrievingHandler handler = new MethodRetrievingHandler();
		E proxy = (E) Proxy.newProxyInstance(ObservableEntityUtils.class.getClassLoader(), new Class[] { entityType }, handler);
		fieldGetter.apply(proxy);
		return handler.getInvoked();
	}
}
