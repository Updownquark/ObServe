package org.observe.util;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.function.Predicate;

/** Enables retrieval of a {@link Method} object from a getter function by calling it on a proxy */
public class MethodRetrievingHandler implements InvocationHandler {
	private Predicate<Method> theFilter;
	private boolean useFirstMethod;
	private Method theFirstNoFilter;
	private Method theInvoked;

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		if (theFirstNoFilter == null)
			theFirstNoFilter = method;
		if (theFilter == null || theFilter.test(method)) {
			if (!useFirstMethod || theInvoked == null)
				theInvoked = method;
		}
		Class<?> retType = method.getReturnType();
		if (!retType.isPrimitive() || retType == void.class)
			return null;
		if (retType == double.class)
			return 0.0;
		else if (retType == float.class)
			return 0.0f;
		else if (retType == long.class)
			return 0L;
		else if (retType == int.class)
			return 0;
		else if (retType == short.class)
			return (short) 0;
		else if (retType == byte.class)
			return (byte) 0;
		else if (retType == char.class)
			return (char) 0;
		else if (retType == boolean.class)
			return false;
		else
			return null; // ?
	}

	/**
	 * @param filter The method filter to use to exclude some method invocations
	 * @param useFirst Whether to use the first method invoked (typical) or the last one
	 */
	public void reset(Predicate<Method> filter, boolean useFirst) {
		theFilter = filter;
		this.useFirstMethod = useFirst;
		theFirstNoFilter = theInvoked = null;
	}

	/** @return The method retrieved from the proxy invocation */
	public Method getInvoked() {
		return theInvoked;
	}

	/** @return The first method called from the proxy invocation */
	public Method getFirstNoFilter() {
		return theFirstNoFilter;
	}
}