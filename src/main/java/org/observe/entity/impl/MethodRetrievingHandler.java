package org.observe.entity.impl;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

public class MethodRetrievingHandler implements InvocationHandler {
	private Method theInvoked;

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		if (theInvoked == null)
			theInvoked = method;
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

	public void reset() {
		theInvoked = null;
	}

	public Method getInvoked() {
		return theInvoked;
	}
}