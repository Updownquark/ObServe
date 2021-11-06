package org.observe.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.qommons.QommonsUtils;

public class ClassView {
	private static final Class<?> NOT_FOUND = new Object() {
	}.getClass();

	private final List<ClassLoader> theClassLoaders;
	private final Map<String, String> theImportedTypes;
	private final Set<String> theWildcardImports;

	private Map<String, Class<?>> theTypeCache;
	private Map<String, Field> theFieldCache;
	private Map<String, List<Method>> theMethodCache;

	private ClassView(List<ClassLoader> classLoaders, Map<String, String> importedTypes, Set<String> wildcardImports) {
		theClassLoaders = classLoaders;
		theImportedTypes = importedTypes;
		theWildcardImports = wildcardImports;

		for (String imp : theImportedTypes.keySet()) {
			Class<?> type = getType(imp);
			if (type == null)
				System.err.println("Import '" + theImportedTypes.get(imp) + "' cannot be resolved");
		}
	}

	public Field getImportedStaticField(String fieldName) {
		// TODO
		return null;
	}

	public List<Method> getImportedStaticMethods(String methodName) {
		// TODO
		return Collections.emptyList();
	}

	public Class<?> getType(String typeName) {
		Class<?> type;
		if (theTypeCache == null)
			theTypeCache = new HashMap<>();
		else {
			type = theTypeCache.get(typeName);
			if (type == NOT_FOUND)
				return null;
			else if (type != null)
				return type;
		}
		String key, suffix;
		int lastDot = typeName.indexOf('.');
		if (lastDot >= 0) {
			key = typeName.substring(0, lastDot);
			suffix = typeName.substring(lastDot);
		} else {
			key = typeName;
			suffix = "";
		}
		String imp = theImportedTypes.get(key);
		if (imp != null) {
			imp += suffix;
			type = tryLoad(imp);
			if (type != null) {
				theTypeCache.put(imp, type);
				theTypeCache.put(typeName, type);
				return type;
			}
		}
		type = tryLoad(typeName);
		if (type != null) {
			theTypeCache.put(typeName, type);
			return type;
		}
		for (String wc : theWildcardImports) {
			String fullName = wc + "." + typeName;
			type = tryLoad(fullName);
			if (type != null) {
				theTypeCache.put(typeName, type);
				theTypeCache.put(fullName, type);
				return type;
			}
		}
		theTypeCache.put(typeName, NOT_FOUND);
		return null;
	}

	private Class<?> tryLoad(String name) {
		for (ClassLoader cl : theClassLoaders) {
			try {
				return cl.loadClass(name);
			} catch (ClassNotFoundException e) {
			}
		}
		int dot = name.lastIndexOf('.');
		while (dot >= 0) {
			name = new StringBuilder().append(name, 0, dot).append('$').append(name, dot + 1, name.length()).toString();
			for (ClassLoader cl : theClassLoaders) {
				try {
					return cl.loadClass(name);
				} catch (ClassNotFoundException e) {
				}
			}
			dot = name.lastIndexOf('.', dot - 1);
		}
		return null;
	}

	public static Builder build() {
		return new Builder();
	}

	public static class Builder {
		private final List<ClassLoader> theClassLoaders;
		private final Map<String, String> theImportedTypes;
		private final Set<String> theWildcardImports;

		Builder() {
			theClassLoaders = new ArrayList<>(3);
			theImportedTypes = new LinkedHashMap<>();
			theWildcardImports = new LinkedHashSet<>();
		}

		public Builder withClassLoader(ClassLoader cl) {
			theClassLoaders.add(cl);
			return this;
		}

		public Builder withImport(String importedType) {
			int lastDot = importedType.lastIndexOf('.');
			if (lastDot >= 0)
				theImportedTypes.put(importedType.substring(lastDot + 1), importedType);
			else
				theImportedTypes.put(importedType, importedType);
			return this;
		}

		public Builder withWildcardImport(String wildcardImport) {
			theWildcardImports.add(wildcardImport);
			return this;
		}

		public ClassView build() {
			return new ClassView(//
				theClassLoaders.isEmpty() ? Collections.unmodifiableList(Arrays.asList(Thread.currentThread().getContextClassLoader()))
					: QommonsUtils.unmodifiableCopy(theClassLoaders), //
					QommonsUtils.unmodifiableCopy(theImportedTypes), //
					QommonsUtils.unmodifiableDistinctCopy(theWildcardImports));
		}
	}
}
