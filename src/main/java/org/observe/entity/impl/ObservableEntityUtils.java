package org.observe.entity.impl;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.Function;

public class ObservableEntityUtils {
	public static <E> Method getField(Class<E> entityType, Function<? super E, ?> fieldGetter) {
		MethodRetrievingHandler handler = new MethodRetrievingHandler();
		E proxy = (E) Proxy.newProxyInstance(ObservableEntityUtils.class.getClassLoader(), new Class[] { entityType }, handler);
		fieldGetter.apply(proxy);
		return handler.getInvoked();
	}
}
