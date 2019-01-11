package org.observe.entity;

public class EntityUtils {
	public static String fieldFromGetter(String methodName) {
		return fieldFromGetterOrSetter(methodName, "get");
	}

	public static String fieldFromSetter(String methodName) {
		return fieldFromGetterOrSetter(methodName, "set");
	}

	private static String fieldFromGetterOrSetter(String methodName, String prefix) {
		if (methodName.length() <= prefix.length() || !methodName.startsWith(prefix))
			return null;
		StringBuilder fieldName = new StringBuilder(methodName.length() - prefix.length());
		fieldName.append(Character.toLowerCase(methodName.charAt(prefix.length())));
		fieldName.append(methodName, prefix.length() + 1, methodName.length());
		return fieldName.toString();
	}
}
