package org.observe.expresso.qonfig;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.text.ParseException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.observe.expresso.qonfig.ExElement.Def;
import org.observe.expresso.qonfig.ExElement.Interpreted;
import org.observe.util.TypeTokens;
import org.qommons.Identifiable;
import org.qommons.Version;
import org.qommons.config.QonfigAttributeDef;
import org.qommons.config.QonfigChildDef;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.QonfigToolkit.ToolkitDefVersion;
import org.qommons.config.QonfigValueDef;
import org.qommons.io.ErrorReporting;

import com.google.common.reflect.TypeToken;

public interface ElementTypeTraceability<E extends ExElement, I extends ExElement.Interpreted<? extends E>, D extends ExElement.Def<? extends E>> {
	ElementTypeTraceability<E, I, D> validate(QonfigElementOrAddOn type, ErrorReporting reporting);

	default Object getDefAttribute(D def, QonfigAttributeDef attribute) {
		attribute = attribute.getDeclared();
		return getDefAttribute(def, attribute.getDeclarer().getName(), attribute.getDeclarer().getMajorVersion(),
			attribute.getDeclarer().getMinorVersion(), attribute.getName());
	}

	Object getDefAttribute(D def, String toolkitName, int majorVersion, int minorVersion, String attribute);

	Object getDefElementValue(D def);

	default Object getInterpretedAttribute(I interp, QonfigAttributeDef attribute) {
		attribute = attribute.getDeclared();
		return getInterpretedAttribute(interp, attribute.getDeclarer().getName(), attribute.getDeclarer().getMajorVersion(),
			attribute.getDeclarer().getMinorVersion(), attribute.getName());
	}

	Object getInterpretedAttribute(I interp, String toolkitName, int majorVersion, int minorVersion, String attribute);

	Object getInterpretedElementValue(I interp);

	default List<? extends ExElement.Def<?>> getDefChildren(D def, QonfigChildDef child) {
		child = child.getDeclared();
		return getDefChildren(def, child.getDeclarer().getName(), child.getDeclarer().getMajorVersion(),
			child.getDeclarer().getMinorVersion(), child.getName());
	}

	List<? extends ExElement.Def<?>> getDefChildren(D def, String toolkitName, int majorVersion, int minorVersion, String child);

	default List<? extends ExElement.Interpreted<?>> getInterpretedChildren(I interp, QonfigChildDef child) {
		child = child.getDeclared();
		return getInterpretedChildren(interp, child.getDeclarer().getName(), child.getDeclarer().getMajorVersion(),
			child.getDeclarer().getMinorVersion(), child.getName());
	}

	List<? extends ExElement.Interpreted<?>> getInterpretedChildren(I interp, String toolkitName, int majorVersion, int minorVersion,
		String child);

	default List<? extends ExElement> getElementChildren(E element, QonfigChildDef child) {
		child = child.getDeclared();
		return getElementChildren(element, child.getDeclarer().getName(), child.getDeclarer().getMajorVersion(),
			child.getDeclarer().getMinorVersion(), child.getName());
	}

	List<? extends ExElement> getElementChildren(E element, String toolkitName, int majorVersion, int minorVersion, String child);

	public interface AttributeValueGetter<E extends ExElement, I extends ExElement.Interpreted<? extends E>, D extends ExElement.Def<? extends E>> {
		Object getFromDef(D def);

		Object getFromInterpreted(I interp);

		public static <E extends ExElement, I extends ExElement.Interpreted<? extends E>, D extends ExElement.Def<? extends E>> Default<E, I, D> of(
			Function<? super D, ?> defGetter, Function<? super I, ?> interpretedGetter) {
			return new Default<>(defGetter, interpretedGetter);
		}

		public static class Default<E extends ExElement, I extends ExElement.Interpreted<? extends E>, D extends ExElement.Def<? extends E>>
		implements AttributeValueGetter<E, I, D> {
			private final Function<? super D, ?> theDefGetter;
			private final Function<? super I, ?> theInterpretedGetter;

			public Default(Function<? super D, ?> defGetter, Function<? super I, ?> interpretedGetter) {
				theDefGetter = defGetter;
				theInterpretedGetter = interpretedGetter;
			}

			@Override
			public Object getFromDef(D def) {
				return theDefGetter.apply(def);
			}

			@Override
			public Object getFromInterpreted(I interp) {
				return theInterpretedGetter == null ? null : theInterpretedGetter.apply(interp);
			}
		}
	}

	public interface ChildElementGetter<E extends ExElement, I extends ExElement.Interpreted<? extends E>, D extends ExElement.Def<? extends E>> {
		List<? extends ExElement.Def<?>> getChildrenFromDef(D def);

		List<? extends ExElement.Interpreted<?>> getChildrenFromInterpreted(I interp);

		List<? extends ExElement> getChildrenFromElement(E element);

		public static <E extends ExElement, I extends ExElement.Interpreted<? extends E>, D extends ExElement.Def<? extends E>> ChildElementGetter<E, I, D> of(
			Function<? super D, ? extends List<? extends ExElement.Def<?>>> defGetter,
				Function<? super I, ? extends List<? extends ExElement.Interpreted<?>>> interpGetter,
					Function<? super E, ? extends List<? extends ExElement>> elGetter) {
			return new ChildElementGetter<E, I, D>() {
				@Override
				public List<? extends ExElement.Def<?>> getChildrenFromDef(D def) {
					return defGetter.apply(def);
				}

				@Override
				public List<? extends ExElement.Interpreted<?>> getChildrenFromInterpreted(I interp) {
					return interpGetter == null ? Collections.emptyList() : interpGetter.apply(interp);
				}

				@Override
				public List<? extends ExElement> getChildrenFromElement(E element) {
					return elGetter == null ? Collections.emptyList() : elGetter.apply(element);
				}
			};
		}
	}

	public static abstract class AddOnAttributeGetter<E extends ExElement, AO extends ExAddOn<? super E>, I extends ExAddOn.Interpreted<? super E, ? extends AO>, D extends ExAddOn.Def<? super E, ? extends AO>> //
	implements AttributeValueGetter<E, ExElement.Interpreted<? extends E>, ExElement.Def<? extends E>> {
		private final Class<D> theDefType;
		private final Class<I> theInterpType;

		protected AddOnAttributeGetter(Class<? super D> defType, Class<? super I> interpType) {
			theDefType = (Class<D>) (Class<?>) defType;
			theInterpType = (Class<I>) (Class<?>) interpType;
		}

		public abstract Object getFromDef(D def);

		public abstract Object getFromInterpreted(I interpreted);

		@Override
		public Object getFromDef(ExElement.Def<? extends E> def) {
			return def.getAddOnValue(theDefType, this::getFromDef);
		}

		@Override
		public Object getFromInterpreted(ExElement.Interpreted<? extends E> interp) {
			return interp.getAddOnValue(theInterpType, this::getFromInterpreted);
		}

		public static <E extends ExElement, AO extends ExAddOn<? super E>, I extends ExAddOn.Interpreted<? super E, ? extends AO>, D extends ExAddOn.Def<? super E, ? extends AO>> Default<E, AO, I, D> of(
			Class<? super D> defType, Function<? super D, ?> defGetter, Class<? super I> interpretedType,
			Function<? super I, ?> interpretedGetter) {
			return new Default<>(defType, defGetter, interpretedType, interpretedGetter);
		}

		public static class Default<E extends ExElement, AO extends ExAddOn<? super E>, I extends ExAddOn.Interpreted<? super E, ? extends AO>, D extends ExAddOn.Def<? super E, ? extends AO>>
		extends AddOnAttributeGetter<E, AO, I, D> {
			private final Function<? super D, ?> theDefGetter;
			private final Function<? super I, ?> theInterpretedGetter;

			public Default(Class<? super D> defType, Function<? super D, ?> defGetter, Class<? super I> interpType,
				Function<? super I, ?> interpretedGetter) {
				super(defType, interpType);
				theDefGetter = defGetter;
				theInterpretedGetter = interpretedGetter;
			}

			@Override
			public Object getFromDef(D def) {
				return theDefGetter.apply(def);
			}

			@Override
			public Object getFromInterpreted(I interpreted) {
				return theInterpretedGetter.apply(interpreted);
			}
		}
	}

	public static abstract class AddOnChildGetter<E extends ExElement, AO extends ExAddOn<? super E>, I extends ExAddOn.Interpreted<? super E, ? extends AO>, D extends ExAddOn.Def<? super E, ? extends AO>> //
	implements ChildElementGetter<E, ExElement.Interpreted<? extends E>, ExElement.Def<? extends E>> {
		private final Class<D> theDefType;
		private final Class<I> theInterpType;
		private final Class<AO> theAddOnType;

		protected AddOnChildGetter(Class<? super D> defType, Class<? super I> interpType, Class<? super AO> addOnType) {
			theDefType = (Class<D>) (Class<?>) defType;
			theInterpType = (Class<I>) (Class<?>) interpType;
			theAddOnType = (Class<AO>) (Class<?>) addOnType;
		}

		public abstract List<? extends ExElement.Def<?>> getFromDef(D def);

		public abstract List<? extends ExElement.Interpreted<?>> getFromInterpreted(I interpreted);

		public abstract List<? extends ExElement> getFromAddOn(AO addOn);

		@Override
		public List<? extends ExElement.Def<?>> getChildrenFromDef(ExElement.Def<? extends E> def) {
			return def.getAddOnValue(theDefType, this::getFromDef);
		}

		@Override
		public List<? extends ExElement.Interpreted<?>> getChildrenFromInterpreted(ExElement.Interpreted<? extends E> interp) {
			if (theInterpType == null)
				return Collections.emptyList();
			return interp.getAddOnValue(theInterpType, this::getFromInterpreted);
		}

		@Override
		public List<? extends ExElement> getChildrenFromElement(E element) {
			if (theAddOnType == null)
				return Collections.emptyList();
			return element.getAddOnValue(theAddOnType, this::getFromAddOn);
		}

		public static <E extends ExElement, AO extends ExAddOn<? super E>, I extends ExAddOn.Interpreted<? super E, ? extends AO>, D extends ExAddOn.Def<? super E, ? extends AO>> Default<E, AO, I, D> of(
			Class<? super D> defType, Function<? super D, ? extends List<? extends ExElement.Def<?>>> defGetter,
				Class<? super I> interpretedType, Function<? super I, ? extends List<? extends ExElement.Interpreted<?>>> interpretedGetter,
					Class<? super AO> addOnType, Function<? super AO, ? extends List<? extends ExElement>> addOnGetter) {
			return new Default<>(defType, defGetter, interpretedType, interpretedGetter, addOnType, addOnGetter);
		}

		public static class Default<E extends ExElement, AO extends ExAddOn<? super E>, I extends ExAddOn.Interpreted<? super E, ? extends AO>, D extends ExAddOn.Def<? super E, ? extends AO>>
		extends AddOnChildGetter<E, AO, I, D> {
			private final Function<? super D, ? extends List<? extends ExElement.Def<?>>> theDefGetter;
			private final Function<? super I, ? extends List<? extends ExElement.Interpreted<?>>> theInterpretedGetter;
			private final Function<? super AO, ? extends List<? extends ExElement>> theAddOnGetter;

			public Default(Class<? super D> defType, Function<? super D, ? extends List<? extends ExElement.Def<?>>> defGetter,
				Class<? super I> interpType, Function<? super I, ? extends List<? extends ExElement.Interpreted<?>>> interpretedGetter,
					Class<? super AO> addOnType, Function<? super AO, ? extends List<? extends ExElement>> addOnGetter) {
				super(defType, interpType, addOnType);
				theDefGetter = defGetter;
				theInterpretedGetter = interpretedGetter;
				theAddOnGetter = addOnGetter;
			}

			@Override
			public List<? extends ExElement.Def<?>> getFromDef(D def) {
				return theDefGetter.apply(def);
			}

			@Override
			public List<? extends ExElement.Interpreted<?>> getFromInterpreted(I interpreted) {
				return theInterpretedGetter.apply(interpreted);
			}

			@Override
			public List<? extends ExElement> getFromAddOn(AO addOn) {
				return theAddOnGetter.apply(addOn);
			}
		}
	}

	public static class SingleTypeTraceability<E extends ExElement, I extends ExElement.Interpreted<? extends E>, D extends ExElement.Def<? extends E>>
	implements ElementTypeTraceability<E, I, D> {
		private static WeakHashMap<QonfigToolkit, Map<Class<?>, Map<QonfigElementKey, ? extends SingleTypeTraceability<?, ?, ?>>>> TRACEABILITY = new WeakHashMap<>();

		public static synchronized <E extends ExElement> Map<QonfigElementKey, SingleTypeTraceability<? super E, ?, ?>> traceabilityFor(
			Class<?> type, QonfigToolkit toolkit, ErrorReporting reporting) {
			if (type == Object.class || type == Identifiable.class)
				return Collections.emptyMap();
			Map<Class<?>, Map<QonfigElementKey, ? extends SingleTypeTraceability<?, ?, ?>>> tkTraceability = TRACEABILITY
				.computeIfAbsent(toolkit, __ -> new HashMap<>());
			Map<QonfigElementKey, SingleTypeTraceability<? super E, ?, ?>> traceability = (Map<QonfigElementKey, SingleTypeTraceability<? super E, ?, ?>>) tkTraceability
				.get(type);
			if (traceability != null)
				return traceability;
			traceability = new LinkedHashMap<>();
			ExElementTraceable traceable = type.getAnnotation(ExElementTraceable.class);
			if (traceable != null)
				parseTraceability(type, toolkit, traceable, traceability, reporting);
			ExMultiElementTraceable multiTraceable = type.getAnnotation(ExMultiElementTraceable.class);
			if (multiTraceable != null) {
				for (ExElementTraceable element : multiTraceable.value())
					parseTraceability(type, toolkit, element, traceability, reporting);
			}
			if (type.getSuperclass() != null)
				join(traceability, traceabilityFor(type.getSuperclass(), toolkit, reporting));
			for (Class<?> intf : type.getInterfaces())
				join(traceability, traceabilityFor(intf, toolkit, reporting));

			// Now validate everything
			for (Map.Entry<QonfigElementKey, SingleTypeTraceability<? super E, ?, ?>> entry : traceability.entrySet()) {
				// Shouldn't be any danger of not finding the type here, since if the traceability is there it means we found it earlier
				QonfigElementOrAddOn qonfigType = findToolkit(toolkit,
					new QonfigToolkit.ToolkitDef(entry.getKey().toolkitName, entry.getKey().toolkitMajorVersion,
						entry.getKey().toolkitMinorVersion))//
					.getElementOrAddOn(entry.getKey().typeName);
				entry.getValue().validate(qonfigType, reporting);
			}
			traceability = Collections.unmodifiableMap(traceability);
			tkTraceability.put(type, traceability);
			return traceability;
		}

		private static <E extends ExElement> void parseTraceability(Class<?> type, QonfigToolkit toolkit, ExElementTraceable traceable,
			Map<QonfigElementKey, SingleTypeTraceability<? super E, ?, ?>> traceability, ErrorReporting reporting) {
			QonfigToolkit.ToolkitDef tk;
			try {
				tk = QonfigToolkit.ToolkitDef.parse(traceable.toolkit());
			} catch (ParseException e) {
				reporting.warn("Could not parse toolkit for traceable class " + type.getName(), e);
				tk = null;
			}
			QonfigToolkit targetTK = tk == null ? null : findToolkit(toolkit, tk);
			if (tk != null && targetTK == null) {
				if (tk.name.equals(toolkit.getName()) && tk.majorVersion == toolkit.getMajorVersion()
					&& tk.minorVersion == toolkit.getMinorVersion())
					targetTK = toolkit;
				else
					reporting.warn("For traceable class " + type.getName() + ": No such toolkit found: " + tk);
			}
			QonfigElementOrAddOn qonfigType;
			try {
				qonfigType = targetTK == null ? null : targetTK.getElementOrAddOn(traceable.qonfigType());
			} catch (IllegalArgumentException e) {
				reporting.warn("For traceable class " + type.getName() + ": " + e.getMessage());
				qonfigType = null;
			}
			if (targetTK != null && qonfigType == null)
				reporting
				.warn("For traceable class " + type.getName() + ": No such Qonfig type: " + targetTK + "." + traceable.qonfigType());
			if (ExElement.Def.class.isAssignableFrom(type)) {
				Class<ExElement.Interpreted<E>> interpretation = traceable.interpretation() == void.class ? null
					: (Class<Interpreted<E>>) traceable.interpretation();
				Class<E> instance = traceable.instance() == void.class ? null : (Class<E>) traceable.instance();
				SingleTypeTraceability<E, ExElement.Interpreted<E>, ExElement.Def<? extends E>> typeTraceability = getElementTraceability(
					tk.name, new Version(tk.majorVersion, tk.minorVersion, 0), qonfigType.getName(),
					(Class<ExElement.Def<? extends E>>) type, interpretation, instance);
				traceability.compute(new QonfigElementKey(qonfigType), (key, old) -> old == null ? typeTraceability
					: ((SingleTypeTraceability<E, ExElement.Interpreted<E>, ExElement.Def<? extends E>>) old).combine(typeTraceability));
			} else if (ExAddOn.Def.class.isAssignableFrom(type)) {
				SingleTypeTraceability<E, ExElement.Interpreted<E>, ExElement.Def<? extends E>> typeTraceability = _traceAddOn(type, tk,
					qonfigType, traceable);
				traceability.compute(new QonfigElementKey(qonfigType), (key, old) -> old == null ? typeTraceability
					: ((SingleTypeTraceability<E, ExElement.Interpreted<E>, ExElement.Def<? extends E>>) old).combine(typeTraceability));
			} else
				reporting.error("Can't use traceability on type " + type.getName());
		}

		private static QonfigToolkit findToolkit(QonfigToolkit toolkit, QonfigToolkit.ToolkitDef search) {
			if (search.name.equals(toolkit.getName()) && search.majorVersion == toolkit.getMajorVersion()
				&& search.minorVersion == toolkit.getMinorVersion())
				return toolkit;
			else
				return toolkit.getDependenciesByDefinition().getOrDefault(search.name, Collections.emptyNavigableMap()).get(search);
		}

		@SuppressWarnings("rawtypes")
		private static <E extends ExElement, AO extends ExAddOn<E>> SingleTypeTraceability<E, Interpreted<E>, Def<? extends E>> _traceAddOn(
			Class<?> type, QonfigToolkit.ToolkitDef tk, QonfigElementOrAddOn qonfigType, ExElementTraceable traceable) {
			Class interpretation = traceable.interpretation() == void.class ? null : traceable.interpretation();
			Class instance = traceable.instance() == void.class ? null : traceable.instance();
			return getAddOnTraceability(tk.name, tk.majorVersion, tk.minorVersion, qonfigType.getName(), (Class) type, interpretation,
				instance);
		}

		public static <E extends ExElement> void join(Map<QonfigElementKey, SingleTypeTraceability<? super E, ?, ?>> traceability,
			Map<QonfigElementKey, ? extends SingleTypeTraceability<?, ?, ?>> other) {
			for (Map.Entry<QonfigElementKey, ? extends SingleTypeTraceability<?, ?, ?>> entry : other.entrySet())
				traceability.compute(entry.getKey(),
					(key, old) -> old == null ? (SingleTypeTraceability<? super E, ?, ?>) entry.getValue()
						: ((SingleTypeTraceability<E, ExElement.Interpreted<E>, ExElement.Def<? extends E>>) old)
						.combine((SingleTypeTraceability<E, ExElement.Interpreted<E>, ExElement.Def<? extends E>>) entry.getValue()));
		}

		private final String theToolkitName;
		private final QonfigToolkit.ToolkitDefVersion theToolkitVersion;
		private final String theTypeName;

		private final Map<String, AttributeValueGetter<? super E, ? super I, ? super D>> theAttributes;
		private final AttributeValueGetter<? super E, ? super I, ? super D> theValue;
		private final Map<String, ChildElementGetter<? super E, ? super I, ? super D>> theChildren;

		private boolean isValidated;

		private SingleTypeTraceability(String toolkitName, ToolkitDefVersion toolkitVersion, String elementName,
			Map<String, AttributeValueGetter<? super E, ? super I, ? super D>> attributes,
			AttributeValueGetter<? super E, ? super I, ? super D> value,
			Map<String, ChildElementGetter<? super E, ? super I, ? super D>> children) {
			theToolkitName = toolkitName;
			theToolkitVersion = toolkitVersion;
			theTypeName = elementName;
			theAttributes = attributes;
			theValue = value;
			theChildren = children;
		}

		public String getToolkitName() {
			return theToolkitName;
		}

		public QonfigToolkit.ToolkitDefVersion getToolkitVersion() {
			return theToolkitVersion;
		}

		public String getTypeName() {
			return theTypeName;
		}

		public SingleTypeTraceability<E, I, D> combine(SingleTypeTraceability<E, I, D> other) {
			if (this == other)
				return this;
			if (!theToolkitName.equals(other.theToolkitName) || !theToolkitVersion.equals(other.theToolkitVersion)
				|| !theTypeName.equals(other.theTypeName))
				throw new IllegalArgumentException("Traceabilities are for different types: " + this + " and " + other);

			Map<String, AttributeValueGetter<? super E, ? super I, ? super D>> attributes;
			AttributeValueGetter<? super E, ? super I, ? super D> value;
			Map<String, ChildElementGetter<? super E, ? super I, ? super D>> children;

			if (theAttributes.isEmpty())
				attributes = other.theAttributes;
			else if (other.theAttributes.isEmpty())
				attributes = theAttributes;
			else {
				attributes = new LinkedHashMap<>();
				attributes.putAll(theAttributes);
				attributes.putAll(other.theAttributes);
				attributes = Collections.unmodifiableMap(attributes);
			}
			if (theValue == null)
				value = other.theValue;
			else
				value = theValue;

			if (theChildren.isEmpty())
				children = other.theChildren;
			else if (other.theChildren.isEmpty())
				children = theChildren;
			else {
				children = new LinkedHashMap<>();
				children.putAll(theChildren);
				children.putAll(other.theChildren);
				children = Collections.unmodifiableMap(children);
			}

			return new SingleTypeTraceability<>(theToolkitName, theToolkitVersion, theTypeName, attributes, value, children);
		}

		@Override
		public SingleTypeTraceability<E, I, D> validate(QonfigElementOrAddOn type, ErrorReporting reporting) {
			// Only perform the validation and report any applicable warnings once
			// We're assuming here that there's no possibility of differing elements with the same toolkit/name combo. Should be safe.
			if (isValidated)
				return this;
			isValidated = true;

			if (!type.getDeclarer().getName().equals(theToolkitName)
				|| theToolkitVersion.majorVersion != type.getDeclarer().getMajorVersion()
				|| theToolkitVersion.minorVersion != type.getDeclarer().getMinorVersion() || !theTypeName.equals(type.getName()))
				throw new IllegalStateException("This class is designed against " + this + ", not " + type.getDeclarer().getName() + " v"
					+ type.getDeclarer().getMajorVersion() + "." + type.getDeclarer().getMinorVersion() + " " + type.getName());
			Set<String> unusedAttrs = new HashSet<>(theAttributes.keySet());
			for (QonfigAttributeDef.Declared attr : type.getDeclaredAttributes().values()) {
				if (!unusedAttrs.remove(attr.getName()))
					reporting.warn("No attribute '" + attr.getName() + "' configured for " + theToolkitName + " " + theToolkitVersion + "."
						+ theTypeName);
			}
			if (!unusedAttrs.isEmpty())
				reporting.warn("No such attributes: " + unusedAttrs + " found in element for " + theToolkitName + " " + theToolkitVersion
					+ "." + theTypeName);

			if (type.getValue() != null && type.getValue() instanceof QonfigValueDef.Declared && type.getValue().getOwner() == type) {
				if (theValue == null)
					reporting.warn("No value configured for " + theToolkitName + " " + theToolkitVersion + "." + theTypeName);
			} else if (theValue != null)
				reporting.warn("Value configured for value-less element " + theToolkitName + " " + theToolkitVersion + "." + theTypeName);

			Set<String> unusedChildren = new HashSet<>(theChildren.keySet());
			for (QonfigChildDef.Declared child : type.getDeclaredChildren().values()) {
				if (!unusedChildren.remove(child.getName()))
					reporting.warn("No child '" + child.getName() + "' configured for " + theToolkitName + " " + theToolkitVersion + "."
						+ theTypeName);
			}
			if (!unusedChildren.isEmpty())
				reporting.warn("No such children: " + unusedChildren + " found in element for " + theToolkitName + " " + theToolkitVersion
					+ "." + theTypeName);
			return this;
		}

		@Override
		public Object getDefAttribute(D def, String toolkitName, int majorVersion, int minorVersion, String attribute) {
			AttributeValueGetter<? super E, ? super I, ? super D> getter = theAttributes.get(attribute);
			return getter == null ? null : getter.getFromDef(def);
		}

		@Override
		public Object getDefElementValue(D def) {
			AttributeValueGetter<? super E, ? super I, ? super D> getter = theValue;
			return getter == null ? null : getter.getFromDef(def);
		}

		@Override
		public Object getInterpretedAttribute(I interp, String toolkitName, int majorVersion, int minorVersion, String attribute) {
			AttributeValueGetter<? super E, ? super I, ? super D> getter = theAttributes.get(attribute);
			return getter == null ? null : getter.getFromInterpreted(interp);
		}

		@Override
		public Object getInterpretedElementValue(I interp) {
			AttributeValueGetter<? super E, ? super I, ? super D> getter = theValue;
			return getter == null ? null : getter.getFromInterpreted(interp);
		}

		@Override
		public List<? extends ExElement.Def<?>> getDefChildren(D def, String toolkitName, int majorVersion, int minorVersion,
			String child) {
			ChildElementGetter<? super E, ? super I, ? super D> getter = theChildren.get(child);
			return getter == null ? Collections.emptyList() : getter.getChildrenFromDef(def);
		}

		@Override
		public List<? extends ExElement.Interpreted<?>> getInterpretedChildren(I interp, String toolkitName, int majorVersion,
			int minorVersion, String child) {
			ChildElementGetter<? super E, ? super I, ? super D> getter = theChildren.get(child);
			return getter == null ? Collections.emptyList() : getter.getChildrenFromInterpreted(interp);
		}

		@Override
		public List<? extends ExElement> getElementChildren(E element, String toolkitName, int majorVersion, int minorVersion,
			String child) {
			ChildElementGetter<? super E, ? super I, ? super D> getter = theChildren.get(child);
			return getter == null ? Collections.emptyList() : getter.getChildrenFromElement(element);
		}

		@Override
		public String toString() {
			return theToolkitName + " " + theToolkitVersion + " " + theTypeName;
		}
	}

	public static <E extends ExElement, I extends ExElement.Interpreted<? extends E>, D extends ExElement.Def<? extends E>> SingleTypeTraceability<E, I, D> getElementTraceability(
		String toolkitName, Version toolkitVersion, String qonfigTypeName, Class<D> def, Class<I> interp, Class<E> element)
			throws IllegalArgumentException {
		SingleTypeTraceabilityBuilder<E, I, D> builder = build(toolkitName, toolkitVersion, qonfigTypeName);
		builder.reflectMethods(def, interp, element);
		return builder.build();
	}

	public static <E extends ExElement, AO extends ExAddOn<? super E>, I extends ExAddOn.Interpreted<? super E, ? extends AO>, D extends ExAddOn.Def<? super E, ? extends AO>> SingleTypeTraceability<E, ExElement.Interpreted<? extends E>, ExElement.Def<? extends E>> getAddOnTraceability(
		String toolkitName, Version toolkitVersion, String typeName, Class<? super D> defType, Class<? super I> interpType,
		Class<? super AO> addOnType) {
		return getAddOnTraceability(toolkitName, toolkitVersion.major, toolkitVersion.minor, typeName, defType, interpType, addOnType);
	}

	public static <E extends ExElement, AO extends ExAddOn<? super E>, I extends ExAddOn.Interpreted<? super E, ? extends AO>, D extends ExAddOn.Def<? super E, ? extends AO>> SingleTypeTraceability<E, ExElement.Interpreted<? extends E>, ExElement.Def<? extends E>> getAddOnTraceability(
		String toolkitName, int majorVersion, int minorVersion, String typeName, Class<? super D> defType, Class<? super I> interpType,
		Class<? super AO> addOnType) {
		AddOnBuilder<E, AO, I, D> builder = buildAddOn(toolkitName, majorVersion, minorVersion, typeName, defType, interpType, addOnType);
		builder.reflectAddOnMethods();
		return builder.build();
	}

	public static <E extends ExElement, I extends ExElement.Interpreted<? extends E>, D extends ExElement.Def<? extends E>> SingleTypeTraceabilityBuilder<E, I, D> build(
		String toolkitName, int majorVersion, int minorVersion, String typeName) {
		return new SingleTypeTraceabilityBuilder<>(toolkitName, new QonfigToolkit.ToolkitDefVersion(majorVersion, minorVersion), typeName);
	}

	public static <E extends ExElement, I extends ExElement.Interpreted<? extends E>, D extends ExElement.Def<? extends E>> SingleTypeTraceabilityBuilder<E, I, D> build(
		String toolkitName, Version toolkitVersion, String typeName) {
		return build(toolkitName, toolkitVersion.major, toolkitVersion.minor, typeName);
	}

	public static <E extends ExElement, AO extends ExAddOn<? super E>, I extends ExAddOn.Interpreted<? super E, ? extends AO>, D extends ExAddOn.Def<? super E, ? extends AO>> AddOnBuilder<E, AO, I, D> buildAddOn(
		String toolkitName, int majorVersion, int minorVersion, String typeName, Class<? super D> defType, Class<? super I> interpType,
		Class<? super AO> addOnType) {
		return new AddOnBuilder<>(toolkitName, new QonfigToolkit.ToolkitDefVersion(majorVersion, minorVersion), typeName, defType,
			interpType, addOnType);
	}

	public static <E extends ExElement, AO extends ExAddOn<? super E>, I extends ExAddOn.Interpreted<? super E, ? extends AO>, D extends ExAddOn.Def<? super E, ? extends AO>> AddOnBuilder<E, AO, I, D> buildAddOn(
		String toolkitName, Version toolkitVersion, String typeName, Class<? super D> defType, Class<? super I> interpType,
		Class<? super AO> addOnType) {
		return buildAddOn(toolkitName, toolkitVersion.major, toolkitVersion.minor, typeName, defType, interpType, addOnType);
	}

	public static class SingleTypeTraceabilityBuilder<E extends ExElement, I extends ExElement.Interpreted<? extends E>, D extends ExElement.Def<? extends E>> {
		private final String theToolkitName;
		private final QonfigToolkit.ToolkitDefVersion theToolkitVersion;
		private final String theTypeName;

		private final Map<String, AttributeValueGetter<? super E, ? super I, ? super D>> theAttributes;
		private AttributeValueGetter<? super E, ? super I, ? super D> theValue;
		private final Map<String, ChildElementGetter<? super E, ? super I, ? super D>> theChildren;

		SingleTypeTraceabilityBuilder(String toolkitName, ToolkitDefVersion toolkitVersion, String typeName) {
			theToolkitName = toolkitName;
			theToolkitVersion = toolkitVersion;
			theTypeName = typeName;
			theAttributes = new HashMap<>();
			theChildren = new HashMap<>();
		}

		static final Pattern AS_TYPE_PATTERN = Pattern
			.compile("((?<tkName>[A-Za-z0-9_\\-]+)(\\s+\\v?(?<major>\\d+)\\.(?<minor>\\d+))?\\:)?(?<typeName>[A-Za-z0-9_\\-]+)");

		public SingleTypeTraceabilityBuilder<E, I, D> reflectMethods(Class<?> defClass, Class<?> interpClass, Class<?> elementClass) {
			for (Method defMethod : defClass.getDeclaredMethods()) {
				if (defMethod.getParameterCount() != 0 || defMethod.getReturnType() == void.class)
					continue;
				QonfigAttributeGetter attr = defMethod.getAnnotation(QonfigAttributeGetter.class);
				if (attr != null && !checkAsType(defMethod, attr.asType()))
					attr = null;
				if (attr != null) {
					Method interpMethod;
					try {
						interpMethod = interpClass == null ? null : interpClass.getDeclaredMethod(defMethod.getName());
					} catch (NoSuchMethodException | SecurityException e) {
						interpMethod = null;
					}

					if (attr.value().isEmpty())
						withValue(new ReflectedAttributeGetter<>(defMethod, interpMethod));
					else
						withAttribute(attr.value(), new ReflectedAttributeGetter<>(defMethod, interpMethod));
				}
				QonfigChildGetter child = defMethod.getAnnotation(QonfigChildGetter.class);
				if (child != null && !checkAsType(defMethod, child.asType())) {
					child = null;
				}
				if (child != null) {
					Boolean listReturn = checkReturnType(defMethod, ExElement.Def.class);
					if (listReturn != null) {
						Method interpMethod;
						try {
							if (interpClass != null) {
								interpMethod = interpClass.getDeclaredMethod(defMethod.getName());
								Boolean interpListReturn = checkReturnType(interpMethod, ExElement.Interpreted.class);
								if (interpListReturn == null)
									interpMethod = null;
								else if (interpListReturn.booleanValue() != listReturn.booleanValue()) {
									if (listReturn.booleanValue())
										System.err.println("If the definition method (" + defClass.getName() + "." + defMethod.getName()
										+ "()) returns a list, the interpreted method (" + interpClass.getName() + "."
										+ defMethod.getName() + "()) must also return a list");
									else
										System.err.println("If the definition method (" + defClass.getName() + "." + defMethod.getName()
										+ "()) returns a singleton element, the interpreted method (" + interpClass.getName() + "."
										+ defMethod.getName() + "()) must also return a singleton element" + "");
									interpMethod = null;
								}
							} else
								interpMethod = null;
						} catch (NoSuchMethodException | SecurityException e) {
							interpMethod = null;
						}

						Method elementMethod;
						try {
							if (elementClass != null) {
								elementMethod = elementClass.getDeclaredMethod(defMethod.getName());
								Boolean elementListReturn = checkReturnType(elementMethod, ExElement.class);
								if (elementListReturn == null)
									elementMethod = null;
								else if (elementListReturn.booleanValue() != listReturn.booleanValue()) {
									if (listReturn.booleanValue())
										System.err.println("If the definition method (" + defClass.getName() + "." + defMethod.getName()
										+ "()) returns a list, the element method (" + elementClass.getName() + "."
										+ defMethod.getName() + "()) must also return a list");
									else
										System.err.println("If the definition method (" + defClass.getName() + "." + defMethod.getName()
										+ "()) returns a singleton element, the element method (" + elementClass.getName() + "."
										+ defMethod.getName() + "()) must also return a singleton element" + "");
									elementMethod = null;
								}
							} else
								elementMethod = null;
						} catch (NoSuchMethodException | SecurityException e) {
							elementMethod = null;
						}

						withChild(child.value(), new ReflectedChildGetter<>(defMethod, interpMethod, elementMethod, listReturn));
					}
				}
				TraceabilityConfiguration config = defMethod.getDeclaredAnnotation(TraceabilityConfiguration.class);
				if (config != null) {
					if (!Modifier.isStatic(defMethod.getModifiers()))
						System.err.println("Traceability configuration method " + defMethod + " is not static");
					else {
						try {
							defMethod.invoke(this);
						} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
							System.err.println("Traceability configuration method " + defMethod + " failed");
							e.printStackTrace();
						}
					}
				}
			}
			return this;
		}

		boolean checkAsType(Method method, String asType) {
			if (asType.isEmpty())
				return true;
			Matcher m = AS_TYPE_PATTERN.matcher(asType);
			if (!m.matches()) {
				System.err.println(
					"For " + method.getDeclaringClass().getName() + "." + method.getName() + "(), expected 'Toolkit vM.m:element'");
				return false;
			}
			if (!theTypeName.equals(m.group("typeName")))
				return false;
			String tkName = m.group("tkName");
			if (tkName != null && !theToolkitName.equals(tkName))
				return false;
			String major = m.group("major");
			if (major != null) {
				if (theToolkitVersion.majorVersion != Integer.parseInt(major))
					return false;
				if (theToolkitVersion.minorVersion != Integer.parseInt(m.group("minor")))
					return false;
			}
			return true;
		}

		static Boolean checkReturnType(Method method, Class<?> targetClass) {
			TypeToken<?> retType = TypeTokens.get().of(method.getGenericReturnType());
			TypeToken<?> targetType;
			if (targetClass.getTypeParameters().length > 0)
				targetType = TypeTokens.get().keyFor(targetClass).wildCard();
			else
				targetType = TypeTokens.get().of(targetClass);
			if (TypeTokens.get().keyFor(List.class).wildCard().isSupertypeOf(retType)) {
				TypeToken<?> elementType = retType.resolveType(List.class.getTypeParameters()[0]);
				if (!targetType.isSupertypeOf(elementType)) {
					System.err.println("Method " + method.getDeclaringClass().getName() + "." + method.getName() + "() cannot be a "
						+ QonfigChildGetter.class.getSimpleName() + ": return type " + retType
						+ " is incompatible with both List<? extends " + ExElement.class.getSimpleName() + ".Def> and "
						+ ExElement.class.getSimpleName() + ".Def");
					return null;
				}
				return true;
			} else {
				if (!targetType.isSupertypeOf(retType)) {
					System.err.println("Method " + method.getDeclaringClass().getName() + "." + method.getName() + "() cannot be a "
						+ QonfigChildGetter.class.getSimpleName() + ": return type " + retType
						+ " is incompatible with both List<? extends " + ExElement.class.getSimpleName() + ".Def> and "
						+ ExElement.class.getSimpleName() + ".Def");
					return null;
				}
				return false;
			}
		}

		public SingleTypeTraceabilityBuilder<E, I, D> withAttribute(String attribute,
			AttributeValueGetter<? super E, ? super I, ? super D> getter) {
			theAttributes.put(attribute, getter);
			return this;
		}

		public SingleTypeTraceabilityBuilder<E, I, D> withAttribute(String attribute, Function<? super D, ?> defGetter,
			Function<? super I, ?> interpGetter) {
			return withAttribute(attribute, AttributeValueGetter.of(defGetter, interpGetter));
		}

		public SingleTypeTraceabilityBuilder<E, I, D> withValue(AttributeValueGetter<? super E, ? super I, ? super D> value) {
			theValue = value;
			return this;
		}

		public SingleTypeTraceabilityBuilder<E, I, D> withValue(Function<? super D, ?> defGetter, Function<? super I, ?> interpGetter) {
			return withValue(AttributeValueGetter.of(defGetter, interpGetter));
		}

		public SingleTypeTraceabilityBuilder<E, I, D> withChild(String child, ChildElementGetter<? super E, ? super I, ? super D> getter) {
			theChildren.put(child, getter);
			return this;
		}

		public SingleTypeTraceabilityBuilder<E, I, D> withChild(String child,
			Function<? super D, ? extends List<? extends ExElement.Def<?>>> defGetter,
				Function<? super I, ? extends List<? extends ExElement.Interpreted<?>>> interpGetter,
					Function<? super E, ? extends List<? extends ExElement>> elementGetter) {
			return withChild(child, ChildElementGetter.of(defGetter, interpGetter, elementGetter));
		}

		public SingleTypeTraceability<E, I, D> build() {
			return new SingleTypeTraceability<>(theToolkitName, theToolkitVersion, theTypeName, theAttributes, theValue, theChildren);
		}
	}

	public static class AddOnBuilder<E extends ExElement, AO extends ExAddOn<? super E>, I extends ExAddOn.Interpreted<? super E, ? extends AO>, D extends ExAddOn.Def<? super E, ? extends AO>>
	extends SingleTypeTraceabilityBuilder<E, ExElement.Interpreted<? extends E>, ExElement.Def<? extends E>> {
		private final Class<D> theDefClass;
		private final Class<I> theInterpretedClass;
		private final Class<AO> theAddOnClass;

		AddOnBuilder(String toolkitName, ToolkitDefVersion toolkitVersion, String elementName, Class<? super D> defClass,
			Class<? super I> interpretedClass, Class<? super AO> addOnClass) {
			super(toolkitName, toolkitVersion, elementName);
			theDefClass = (Class<D>) (Class<?>) defClass;
			theInterpretedClass = (Class<I>) (Class<?>) interpretedClass;
			theAddOnClass = (Class<AO>) (Class<?>) addOnClass;
		}

		public AddOnBuilder<E, AO, I, D> reflectAddOnMethods() {
			for (Method defMethod : theDefClass.getDeclaredMethods()) {
				if (defMethod.getParameterCount() != 0 || defMethod.getReturnType() == void.class)
					continue;
				QonfigAttributeGetter attr = defMethod.getAnnotation(QonfigAttributeGetter.class);
				if (attr != null && !checkAsType(defMethod, attr.asType()))
					attr = null;
				if (attr != null) {
					Method interpMethod;
					try {
						interpMethod = theInterpretedClass == null ? null : theInterpretedClass.getDeclaredMethod(defMethod.getName());
					} catch (NoSuchMethodException | SecurityException e) {
						interpMethod = null;
					}

					if (attr.value().isEmpty())
						withValue(new ReflectedAddOnAttributeGetter<>(theDefClass, defMethod, theInterpretedClass, interpMethod));
					else
						withAttribute(attr.value(),
							new ReflectedAddOnAttributeGetter<>(theDefClass, defMethod, theInterpretedClass, interpMethod));
				}
				QonfigChildGetter child = defMethod.getAnnotation(QonfigChildGetter.class);
				if (child != null && !checkAsType(defMethod, child.asType()))
					child = null;
				if (child != null) {
					Boolean listReturn = checkReturnType(defMethod, ExElement.Def.class);
					if (listReturn != null) {
						Method interpMethod;
						try {
							if (theInterpretedClass != null) {
								interpMethod = theInterpretedClass.getDeclaredMethod(defMethod.getName());
								Boolean interpListReturn = checkReturnType(interpMethod, ExElement.Interpreted.class);
								if (interpListReturn == null)
									interpMethod = null;
								else if (interpListReturn.booleanValue() != listReturn.booleanValue()) {
									if (listReturn.booleanValue())
										System.err.println("If the definition method (" + theDefClass.getName() + "." + defMethod.getName()
										+ "()) returns a list, the interpreted method (" + theInterpretedClass.getName() + "."
										+ defMethod.getName() + "()) must also return a list");
									else
										System.err.println("If the definition method (" + theDefClass.getName() + "." + defMethod.getName()
										+ "()) returns a singleton element, the interpreted method (" + theInterpretedClass.getName()
										+ "." + defMethod.getName() + "()) must also return a singleton element" + "");
									interpMethod = null;
								}
							} else
								interpMethod = null;
						} catch (NoSuchMethodException | SecurityException e) {
							interpMethod = null;
						}

						Method elementMethod;
						try {
							if (theAddOnClass != null) {
								elementMethod = theAddOnClass.getDeclaredMethod(defMethod.getName());
								Boolean elementListReturn = checkReturnType(elementMethod, ExElement.class);
								if (elementListReturn == null)
									elementMethod = null;
								else if (elementListReturn.booleanValue() != listReturn.booleanValue()) {
									if (listReturn.booleanValue())
										System.err.println("If the definition method (" + theDefClass.getName() + "." + defMethod.getName()
										+ "()) returns a list, the element method (" + theAddOnClass.getName() + "."
										+ defMethod.getName() + "()) must also return a list");
									else
										System.err.println("If the definition method (" + theDefClass.getName() + "." + defMethod.getName()
										+ "()) returns a singleton element, the element method (" + theAddOnClass.getName() + "."
										+ defMethod.getName() + "()) must also return a singleton element" + "");
									elementMethod = null;
								}
							} else
								elementMethod = null;
						} catch (NoSuchMethodException | SecurityException e) {
							elementMethod = null;
						}

						withChild(child.value(), new ReflectedAddOnChildGetter<>(theDefClass, defMethod, theInterpretedClass, interpMethod,
							theAddOnClass, elementMethod, listReturn));
					}
				}
				TraceabilityConfiguration config = defMethod.getDeclaredAnnotation(TraceabilityConfiguration.class);
				if (config != null) {
					if (!Modifier.isStatic(defMethod.getModifiers()))
						System.err.println("Traceability configuration method " + defMethod + " is not static");
					else {
						try {
							defMethod.invoke(this);
						} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
							System.err.println("Traceability configuration method " + defMethod + " failed");
							e.printStackTrace();
						}
					}
				}
			}
			return this;
		}

		public AddOnBuilder<E, AO, I, D> withAddOnAttribute(String attribute, Function<? super D, ?> defGetter,
			Function<? super I, ?> interpGetter) {
			return withAttribute(attribute,
				AddOnAttributeGetter.<E, AO, I, D> of(theDefClass, defGetter, theInterpretedClass, interpGetter));
		}

		public AddOnBuilder<E, AO, I, D> withAddOnValue(Function<? super D, ?> defGetter, Function<? super I, ?> interpGetter) {
			return withValue(AddOnAttributeGetter.<E, AO, I, D> of(theDefClass, defGetter, theInterpretedClass, interpGetter));
		}

		public AddOnBuilder<E, AO, I, D> withAddOnChild(String child,
			Function<? super D, ? extends List<? extends ExElement.Def<?>>> defGetter,
				Function<? super I, ? extends List<? extends ExElement.Interpreted<?>>> interpGetter,
					Function<? super AO, ? extends List<? extends ExElement>> elementGetter) {
			return withChild(child,
				AddOnChildGetter.<E, AO, I, D> of(theDefClass, defGetter, theInterpretedClass, interpGetter, theAddOnClass, elementGetter));
		}

		@Override
		public AddOnBuilder<E, AO, I, D> withAttribute(String attribute,
			AttributeValueGetter<? super E, ? super ExElement.Interpreted<? extends E>, ? super Def<? extends E>> getter) {
			super.withAttribute(attribute, getter);
			return this;
		}

		@Override
		public AddOnBuilder<E, AO, I, D> withAttribute(String attribute, Function<? super Def<? extends E>, ?> defGetter,
			Function<? super ExElement.Interpreted<? extends E>, ?> interpGetter) {
			super.withAttribute(attribute, defGetter, interpGetter);
			return this;
		}

		@Override
		public AddOnBuilder<E, AO, I, D> withValue(
			AttributeValueGetter<? super E, ? super ExElement.Interpreted<? extends E>, ? super Def<? extends E>> value) {
			super.withValue(value);
			return this;
		}

		@Override
		public AddOnBuilder<E, AO, I, D> withValue(Function<? super Def<? extends E>, ?> defGetter,
			Function<? super ExElement.Interpreted<? extends E>, ?> interpGetter) {
			super.withValue(defGetter, interpGetter);
			return this;
		}

		@Override
		public AddOnBuilder<E, AO, I, D> withChild(String child,
			ChildElementGetter<? super E, ? super ExElement.Interpreted<? extends E>, ? super Def<? extends E>> getter) {
			super.withChild(child, getter);
			return this;
		}

		@Override
		public AddOnBuilder<E, AO, I, D> withChild(String child,
			Function<? super Def<? extends E>, ? extends List<? extends ExElement.Def<?>>> defGetter,
				Function<? super ExElement.Interpreted<? extends E>, ? extends List<? extends Interpreted<?>>> interpGetter,
					Function<? super E, ? extends List<? extends ExElement>> elementGetter) {
			super.withChild(child, defGetter, interpGetter, elementGetter);
			return this;
		}
	}

	public static class ReflectedAttributeGetter<E extends ExElement, I extends ExElement.Interpreted<? extends E>, D extends ExElement.Def<? extends E>>
	implements AttributeValueGetter<E, I, D> {
		private final Method theDefGetter;
		private final Method theInterpretedGetter;

		public ReflectedAttributeGetter(Method defGetter, Method interpretedGetter) {
			theDefGetter = defGetter;
			theInterpretedGetter = interpretedGetter;
		}

		@Override
		public Object getFromDef(D def) {
			try {
				return theDefGetter.invoke(def);
			} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				e.printStackTrace();
				return null;
			}
		}

		@Override
		public Object getFromInterpreted(I interp) {
			try {
				return theInterpretedGetter == null ? null : theInterpretedGetter.invoke(interp);
			} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				e.printStackTrace();
				return null;
			}
		}

		@Override
		public String toString() {
			return theDefGetter.getDeclaringClass().getSimpleName() + "." + theDefGetter.getName() + "()";
		}
	}

	public static class ReflectedAddOnAttributeGetter<E extends ExElement, AO extends ExAddOn<? super E>, I extends ExAddOn.Interpreted<? super E, ? extends AO>, D extends ExAddOn.Def<? super E, ? extends AO>>
	extends AddOnAttributeGetter<E, AO, I, D> {
		private final Method theDefGetter;
		private final Method theInterpretedGetter;

		public ReflectedAddOnAttributeGetter(Class<D> defType, Method defGetter, Class<I> interpType, Method interpretedGetter) {
			super(defType, interpType);
			theDefGetter = defGetter;
			theInterpretedGetter = interpretedGetter;
		}

		@Override
		public Object getFromDef(D def) {
			try {
				return theDefGetter.invoke(def);
			} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				e.printStackTrace();
				return null;
			}
		}

		@Override
		public Object getFromInterpreted(I interp) {
			try {
				return theInterpretedGetter == null ? null : theInterpretedGetter.invoke(interp);
			} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				e.printStackTrace();
				return null;
			}
		}

		@Override
		public String toString() {
			return theDefGetter.getDeclaringClass().getSimpleName() + "." + theDefGetter.getName() + "()";
		}
	}

	public static class ReflectedChildGetter<E extends ExElement, I extends ExElement.Interpreted<? extends E>, D extends ExElement.Def<? extends E>>
	implements ChildElementGetter<E, I, D> {
		private final Method theDefGetter;
		private final Method theInterpretedGetter;
		private final Method theElementGetter;
		private final boolean isListReturn;

		public ReflectedChildGetter(Method defGetter, Method interpretedGetter, Method elementGetter, boolean listReturn) {
			theDefGetter = defGetter;
			theInterpretedGetter = interpretedGetter;
			theElementGetter = elementGetter;
			isListReturn = listReturn;
		}

		@Override
		public List<? extends ExElement.Def<?>> getChildrenFromDef(D def) {
			Object ret;
			try {
				ret = theDefGetter.invoke(def);
			} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				e.printStackTrace();
				return Collections.emptyList();
			}
			if (ret == null)
				return Collections.emptyList();
			else if (isListReturn)
				return (List<? extends ExElement.Def<?>>) ret;
			else
				return Collections.singletonList((ExElement.Def<?>) ret);
		}

		@Override
		public List<? extends Interpreted<?>> getChildrenFromInterpreted(I interp) {
			if (theInterpretedGetter == null)
				return Collections.emptyList();
			Object ret;
			try {
				ret = theInterpretedGetter.invoke(interp);
			} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				e.printStackTrace();
				return Collections.emptyList();
			}
			if (ret == null)
				return Collections.emptyList();
			else if (isListReturn)
				return (List<? extends ExElement.Interpreted<?>>) ret;
			else
				return Collections.singletonList((ExElement.Interpreted<?>) ret);
		}

		@Override
		public List<? extends ExElement> getChildrenFromElement(E element) {
			if (theElementGetter == null)
				return Collections.emptyList();
			Object ret;
			try {
				ret = theElementGetter.invoke(element);
			} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				e.printStackTrace();
				return Collections.emptyList();
			}
			if (ret == null)
				return Collections.emptyList();
			else if (isListReturn)
				return (List<? extends ExElement>) ret;
			else
				return Collections.singletonList((ExElement) ret);
		}

		@Override
		public String toString() {
			return theDefGetter.getDeclaringClass().getSimpleName() + "." + theDefGetter.getName() + "()";
		}
	}

	public static class ReflectedAddOnChildGetter<E extends ExElement, AO extends ExAddOn<? super E>, I extends ExAddOn.Interpreted<? super E, ? extends AO>, D extends ExAddOn.Def<? super E, ? extends AO>>
	extends AddOnChildGetter<E, AO, I, D> {
		private final Method theDefGetter;
		private final Method theInterpretedGetter;
		private final Method theElementGetter;
		private final boolean isListReturn;

		public ReflectedAddOnChildGetter(Class<D> defType, Method defGetter, Class<I> interpType, Method interpretedGetter,
			Class<AO> addOnType, Method elementGetter, boolean listReturn) {
			super(defType, interpType, addOnType);
			theDefGetter = defGetter;
			theInterpretedGetter = interpretedGetter;
			theElementGetter = elementGetter;
			isListReturn = listReturn;
		}

		@Override
		public List<? extends ExElement.Def<?>> getFromDef(D def) {
			Object ret;
			try {
				ret = theDefGetter.invoke(def);
			} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				e.printStackTrace();
				return Collections.emptyList();
			}
			if (ret == null)
				return Collections.emptyList();
			else if (isListReturn)
				return (List<? extends ExElement.Def<?>>) ret;
			else
				return Collections.singletonList((ExElement.Def<?>) ret);
		}

		@Override
		public List<? extends ExElement.Interpreted<?>> getFromInterpreted(I interpreted) {
			if (theInterpretedGetter == null)
				return Collections.emptyList();
			Object ret;
			try {
				ret = theInterpretedGetter.invoke(interpreted);
			} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				e.printStackTrace();
				return Collections.emptyList();
			}
			if (ret == null)
				return Collections.emptyList();
			else if (isListReturn)
				return (List<? extends ExElement.Interpreted<?>>) ret;
			else
				return Collections.singletonList((ExElement.Interpreted<?>) ret);
		}

		@Override
		public List<? extends ExElement> getFromAddOn(AO addOn) {
			if (theElementGetter == null)
				return Collections.emptyList();
			Object ret;
			try {
				ret = theElementGetter.invoke(addOn);
			} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				e.printStackTrace();
				return Collections.emptyList();
			}
			if (ret == null)
				return Collections.emptyList();
			else if (isListReturn)
				return (List<? extends ExElement>) ret;
			else
				return Collections.singletonList((ExElement) ret);
		}
	}

	class QonfigElementKey {
		public String toolkitName;
		public int toolkitMajorVersion;
		public int toolkitMinorVersion;
		public String typeName;

		public QonfigElementKey(String toolkitName, int toolkitMajorVersion, int toolkitMinorVersion, String typeName) {
			this.toolkitName = toolkitName;
			this.toolkitMinorVersion = toolkitMinorVersion;
			this.toolkitMajorVersion = toolkitMajorVersion;
			this.typeName = typeName;
		}

		public QonfigElementKey(SingleTypeTraceability<?, ?, ?> traceability) {
			toolkitName = traceability.getToolkitName();
			toolkitMajorVersion = traceability.getToolkitVersion().majorVersion;
			toolkitMinorVersion = traceability.getToolkitVersion().minorVersion;
			typeName = traceability.getTypeName();
		}

		public QonfigElementKey(QonfigElementOrAddOn type) {
			toolkitName = type.getDeclarer().getName();
			toolkitMajorVersion = type.getDeclarer().getMajorVersion();
			toolkitMinorVersion = type.getDeclarer().getMinorVersion();
			typeName = type.getName();
		}

		@Override
		public int hashCode() {
			return Objects.hash(toolkitName, toolkitMajorVersion, toolkitMinorVersion, typeName);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else if (!(obj instanceof QonfigElementKey))
				return false;
			QonfigElementKey other = (QonfigElementKey) obj;
			return toolkitName.equals(other.toolkitName) //
				&& toolkitMajorVersion == other.toolkitMajorVersion && toolkitMinorVersion == other.toolkitMinorVersion//
				&& typeName.equals(other.typeName);
		}

		@Override
		public String toString() {
			return toolkitName + " v" + toolkitMajorVersion + "." + toolkitMinorVersion + "." + typeName;
		}
	}
}
