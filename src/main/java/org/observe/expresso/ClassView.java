package org.observe.expresso;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.observe.util.TypeParser;
import org.observe.util.TypeTokens;
import org.observe.util.TypeTokens.TypeRetriever;
import org.qommons.QommonsUtils;
import org.qommons.ValueHolder;

import com.google.common.reflect.TypeToken;

/** A perspective from which to load classes by name, qualified or not, as well as statically-imported fields and methods */
public class ClassView implements TypeParser {
	private static final Class<?> NOT_FOUND = new Object() {
	}.getClass();

	private final TypeParser theParser;
	private final List<ClassLoader> theClassLoaders;
	private final Map<String, String> theImportedTypes;
	private final Set<String> theWildcardImports;

	private Map<String, Class<?>> theTypeCache;
	private Map<String, ValueHolder<Field>> theFieldCache;
	private Map<String, List<Method>> theMethodCache;

	private ClassView(List<ClassLoader> classLoaders, Map<String, String> importedTypes, Set<String> wildcardImports) {
		theClassLoaders = classLoaders;
		theImportedTypes = importedTypes;
		theWildcardImports = wildcardImports;
		theFieldCache = new ConcurrentHashMap<>();
		theMethodCache = new ConcurrentHashMap<>();

		for (String imp : theImportedTypes.keySet()) {
			Class<?> type = getType(imp);
			if (type == null)
				System.err.println("Import '" + theImportedTypes.get(imp) + "' cannot be resolved");
		}

		theParser = TypeTokens.get().newParser();
		theParser.addTypeRetriever(typeName -> ClassView.this.getType(typeName));
	}

	/**
	 * @param fieldName The name of the field to get
	 * @return The statically-imported field with the given name in this class view, or null if there is no such statically-imported field
	 */
	public Field getImportedStaticField(String fieldName) {
		ValueHolder<Field> cached = theFieldCache.get(fieldName);
		if (cached != null)
			return cached.get();
		Field found = null;
		for (String wildcard : theWildcardImports) {
			Class<?> type = getType(wildcard);
			if (type == null)
				continue;
			for (Field field : type.getDeclaredFields()) {
				int mod = field.getModifiers();
				if (Modifier.isPublic(mod) && Modifier.isStatic(mod)) {
					found = field;
					break;
				}
			}
			if (found != null)
				break;
		}
		theFieldCache.put(fieldName, new ValueHolder<>(found));
		return found;
	}

	/**
	 * @param methodName The name of the methods to get
	 * @return All statically-imported methods with the given name in this class view
	 */
	public List<Method> getImportedStaticMethods(String methodName) {
		List<Method> methods = theMethodCache.get(methodName);
		if (methods != null)
			return methods;
		methods = new ArrayList<>();
		for (String wildcard : theWildcardImports) {
			Class<?> type = getType(wildcard);
			if (type == null)
				continue;
			for (Method method : type.getDeclaredMethods()) {
				int mod = method.getModifiers();
				if (Modifier.isPublic(mod) && Modifier.isStatic(mod))
					methods.add(method);
			}
		}
		theMethodCache.put(methodName, Collections.unmodifiableList(methods));
		return Collections.unmodifiableList(methods);
	}

	/**
	 * @param typeName The name to get the type for
	 * @return The type corresponding to the given name, or null if no such type could be found in this view
	 */
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

	@Override
	public TypeToken<?> parseType(CharSequence text) throws ParseException {
		return theParser.parseType(text);
	}

	@Override
	public TypeParser addTypeRetriever(TypeRetriever typeRetriever) {
		theParser.addTypeRetriever(typeRetriever);
		return this;
	}

	@Override
	public boolean removeTypeRetriever(TypeRetriever typeRetriever) {
		return theParser.removeTypeRetriever(typeRetriever);
	}

	/** @return A builder to create a new class view */
	public static Builder build() {
		return new Builder();
	}

	/** Builds a {@link ClassView} */
	public static class Builder {
		private final List<ClassLoader> theClassLoaders;
		private final Map<String, String> theImportedTypes;
		private final Set<String> theWildcardImports;

		Builder() {
			theClassLoaders = new ArrayList<>(3);
			theImportedTypes = new LinkedHashMap<>();
			theWildcardImports = new LinkedHashSet<>();
		}

		/**
		 * @param cl The class loader to use to load classes for the new class loader. Multiple are supported.
		 * @return This builder
		 */
		public Builder withClassLoader(ClassLoader cl) {
			theClassLoaders.add(cl);
			return this;
		}

		/**
		 * @param importedType The imported type. The given type will be available for load by its simple name.
		 * @return This builder
		 */
		public Builder withImport(String importedType) {
			int lastDot = importedType.lastIndexOf('.');
			if (lastDot >= 0)
				theImportedTypes.put(importedType.substring(lastDot + 1), importedType);
			else
				theImportedTypes.put(importedType, importedType);
			return this;
		}

		/**
		 * @param wildcardImport The imported package or type. Classes in the given package will be available for load by their simple
		 *        names, and fields and methods of the given type will be available by name.
		 * @return This builder
		 */
		public Builder withWildcardImport(String wildcardImport) {
			theWildcardImports.add(wildcardImport);
			return this;
		}

		/** @return The new {@link ClassView} */
		public ClassView build() {
			return new ClassView(//
				theClassLoaders.isEmpty() ? Collections.unmodifiableList(Arrays.asList(Thread.currentThread().getContextClassLoader()))
					: QommonsUtils.unmodifiableCopy(theClassLoaders), //
					QommonsUtils.unmodifiableCopy(theImportedTypes), //
					QommonsUtils.unmodifiableDistinctCopy(theWildcardImports));
		}
	}
}
