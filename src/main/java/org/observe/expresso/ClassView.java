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

import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.ExpressoSessionImplV0_1;
import org.observe.util.TypeParser;
import org.observe.util.TypeTokens;
import org.observe.util.TypeTokens.TypeRetriever;
import org.qommons.QommonsUtils;
import org.qommons.ValueHolder;
import org.qommons.collect.BetterList;
import org.qommons.config.AbstractQIS;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.io.ErrorReporting;

import com.google.common.reflect.TypeToken;

/** A perspective from which to load classes by name, qualified or not, as well as statically-imported fields and methods */
public class ClassView extends ExElement.Def.Abstract<ExElement> implements TypeParser {
	private static final Class<?> NOT_FOUND = new Object() {
	}.getClass();

	private static final ExElement.ChildElementGetter<ExElement, ExElement.Interpreted<?>, ClassView> IMPORT_ELEMENTS = ExElement.ChildElementGetter.<ExElement, ExElement.Interpreted<?>, ClassView> of(
		ClassView::getImportElements, null, null, "The import statements");

	private static final ExElement.AttributeValueGetter<ExElement, ExElement.Interpreted<?>, ImportElement> IMPORT_VALUE = ExElement.AttributeValueGetter.<ExElement, ExElement.Interpreted<?>, ImportElement> of(
		ImportElement::getValue, null, null, "The type name, static field or method name, or wildcard expression to import");

	private static class ImportElement extends ExElement.Def.Abstract<ExElement> {
		ImportElement(ExElement.Def<?> parent, AbstractQIS<?> session) {
			super(parent, session.getElement());
			ExElement.checkElement(session.getFocusType(), ExpressoSessionImplV0_1.TOOLKIT_NAME, ExpressoSessionImplV0_1.VERSION, "import");
			forValue(IMPORT_VALUE);
		}

		String getValue() {
			return getElement().getValueText();
		}
	}

	private final TypeParser theParser;
	private final List<ClassLoader> theClassLoaders;
	private final Map<String, String> theImportedTypes;
	private final Set<String> theWildcardImports;
	private final List<ImportElement> theImportElements;

	private Map<String, Class<?>> theTypeCache;
	private Map<String, ValueHolder<Field>> theFieldCache;
	private Map<String, List<Method>> theMethodCache;

	private ClassView(List<ClassLoader> classLoaders, Map<String, String> importedTypes, Map<String, ErrorReporting> importTypeErrors,
		Set<String> wildcardImports, ExElement.Def<?> parent, AbstractQIS<?> session, List<AbstractQIS<?>> importElements) {
		super(parent, session == null ? null : session.getElement());
		if (session != null) {
			ExElement.checkElement(session.getFocusType(), ExpressoSessionImplV0_1.TOOLKIT_NAME, ExpressoSessionImplV0_1.VERSION,
				"imports");
			forChild(session.getRole("import"), IMPORT_ELEMENTS);
		}
		theClassLoaders = classLoaders;
		theImportedTypes = importedTypes;
		theWildcardImports = wildcardImports;
		theImportElements = BetterList.of2(importElements.stream(), s -> new ImportElement(this, s));
		theFieldCache = new ConcurrentHashMap<>();
		theMethodCache = new ConcurrentHashMap<>();

		for (Map.Entry<String, String> imp : theImportedTypes.entrySet()) {
			Class<?> type = getType(imp.getKey());
			if (type == null)
				importTypeErrors.get(imp.getKey()).error("Import '" + imp.getValue() + "' cannot be resolved");
		}

		theParser = TypeTokens.get().newParser();
		theParser.addTypeRetriever(ClassView.this::getType);
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
			} catch (ClassNotFoundException e) { // It's fine, we'll try with the configured class loaders
			}
		}
		int dot = name.lastIndexOf('.');
		while (dot >= 0) {
			name = new StringBuilder().append(name, 0, dot).append('$').append(name, dot + 1, name.length()).toString();
			for (ClassLoader cl : theClassLoaders) {
				try {
					return cl.loadClass(name);
				} catch (ClassNotFoundException e) { // We don't throw exceptions, just return null
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

	public List<ImportElement> getImportElements() {
		return theImportElements;
	}

	@Override
	public void update(ExpressoQIS session) throws QonfigInterpretationException {
		super.update(session);
		int i = 0;
		for (ExpressoQIS impSession : session.forChildren("import"))
			theImportElements.get(i++).update(impSession);
	}

	/** @return A builder to create a new class view */
	public static Builder build() {
		return new Builder();
	}

	/** Builds a {@link ClassView} */
	public static class Builder {
		private final List<ClassLoader> theClassLoaders;
		private final Map<String, String> theImportedTypes;
		private final Map<String, ErrorReporting> theImportTypeErrors;
		private final Set<String> theWildcardImports;
		private final List<AbstractQIS<?>> theImportElements;

		Builder() {
			theClassLoaders = new ArrayList<>(3);
			theImportedTypes = new LinkedHashMap<>();
			theImportTypeErrors = new LinkedHashMap<>();
			theWildcardImports = new LinkedHashSet<>();
			theImportElements = new ArrayList<>();
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
		 * @param onError Error reporting if this type cannot be resolved after the class view is built
		 * @return This builder
		 */
		public Builder withImport(String importedType, ErrorReporting onError, AbstractQIS<?> session) {
			int lastDot = importedType.lastIndexOf('.');
			if (lastDot >= 0)
				theImportedTypes.put(importedType.substring(lastDot + 1), importedType);
			else
				theImportedTypes.put(importedType, importedType);
			if (onError != null)
				theImportTypeErrors.put(importedType, onError);
			if (session != null)
				theImportElements.add(session);
			return this;
		}

		/**
		 * @param wildcardImport The imported package or type. Classes in the given package will be available for load by their simple
		 *        names, and fields and methods of the given type will be available by name.
		 * @return This builder
		 */
		public Builder withWildcardImport(String wildcardImport, AbstractQIS<?> session) {
			theWildcardImports.add(wildcardImport);
			if (session != null)
				theImportElements.add(session);
			return this;
		}

		/** @return The new {@link ClassView} */
		public ClassView build(ExElement.Def<?> parent, AbstractQIS<?> session) {
			return new ClassView(//
				theClassLoaders.isEmpty() ? Collections.unmodifiableList(Arrays.asList(Thread.currentThread().getContextClassLoader()))
					: QommonsUtils.unmodifiableCopy(theClassLoaders), //
					QommonsUtils.unmodifiableCopy(theImportedTypes), theImportTypeErrors, //
					QommonsUtils.unmodifiableDistinctCopy(theWildcardImports), parent, session, theImportElements);
		}
	}
}
