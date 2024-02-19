package org.observe.expresso.qonfig;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.text.ParseException;
import java.util.Collection;
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
import org.observe.quick.qwysiwyg.Qwysiwyg;
import org.observe.util.TypeTokens;
import org.qommons.Identifiable;
import org.qommons.QommonsUtils;
import org.qommons.Version;
import org.qommons.config.QonfigAttributeDef;
import org.qommons.config.QonfigChildDef;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.QonfigToolkit.ToolkitDefVersion;
import org.qommons.config.QonfigValueDef;
import org.qommons.io.ErrorReporting;

import com.google.common.reflect.TypeToken;

/**
 * <p>
 * A utility for supporting the {@link ExElement.Def#getAttribute(QonfigAttributeDef)}, {@link ExElement.Def#getElementValue()},
 * {@link ExElement.Def#getDefChildren(QonfigChildDef)} and other related methods for traceability.
 * </p>
 * <p>
 * Traceability is the ability to inspect expresso elements without any knowledge of what the elements do or their extension classes.
 * Traceability is not critical to the Expresso framework, but it is a very useful feature. At the moment of this writing, it is only used
 * by the {@link Qwysiwyg} application to support reporting current model values and other information to the user, but many other uses may
 * be possible.
 * </p>
 * <p>
 * This class exposes methods to build traceability from scratch, but the preferred method is to tag {@link ExElement.Def ExElement.Def} and
 * {@link ExAddOn.Def ExAddOn.Def} extensions with annotations. The {@link ExElement.Def.Abstract ExElement.Def.Abstract} class then calls
 * {@link #traceabilityFor(Class, QonfigToolkit, ErrorReporting)}, which reflects the annotation-tagged methods and assembles traceability
 * based on that.
 * </p>
 * <p>
 * A hybrid method is available by using the {@link TraceabilityConfiguration} annotation, which tags a static method that accepts a
 * traceability builder (either a {@link SingleTypeTraceabilityBuilder} for an element-def or a {@link AddOnBuilder} for an add-on) to
 * configure custom traceability after the reflection has been performed.
 * </p>
 *
 * @param <E> The type of element instance this object supports traceability for
 * @param <I> The type of element interpretation this object supports traceability for
 * @param <D> The type of element definition this object supports traceability for
 * @see ExElementTraceable
 * @see ExMultiElementTraceable
 * @see QonfigAttributeGetter
 * @see QonfigChildGetter
 * @see TraceabilityConfiguration
 */
public interface ElementTypeTraceability<E extends ExElement, I extends ExElement.Interpreted<? extends E>, D extends ExElement.Def<? extends E>> {
	/**
	 * Validates this traceability object on the Qonfig type
	 *
	 * @param type The Qonfig type that this traceability is for
	 * @param reporting The error reporting in case there are any issues
	 * @return This traceability
	 */
	ElementTypeTraceability<E, I, D> validate(QonfigElementOrAddOn type, ErrorReporting reporting);

	/**
	 * @param def The element definition to get the attribute for
	 * @param attribute The attribute to get the value for
	 * @return The object on the element definition that was parsed from the value of the given attribute
	 */
	default Object getDefAttribute(D def, QonfigAttributeDef attribute) {
		attribute = attribute.getDeclared();
		return getDefAttribute(def, attribute.getDeclarer().getName(), attribute.getDeclarer().getMajorVersion(),
			attribute.getDeclarer().getMinorVersion(), attribute.getName());
	}

	/**
	 * @param def The element definition to get the attribute for
	 * @param toolkitName The name of the toolkit defining the attribute to get the value for
	 * @param majorVersion The major version of the toolkit defining the attribute to get the value for
	 * @param minorVersion The minor version of the toolkit defining the attribute to get the value for
	 * @param attribute The name of the attribute to get the value for
	 * @return The object on the element definition that was parsed from the value of the given attribute
	 */
	Object getDefAttribute(D def, String toolkitName, int majorVersion, int minorVersion, String attribute);

	/**
	 * @param def The element definition to get the element value for
	 * @return The object on the element definition that was parsed from the value of the element
	 */
	Object getDefElementValue(D def);

	/**
	 * @param interp The element interpretation to get the attribute for
	 * @param attribute The attribute to get the value for
	 * @return The object on the element interpretation that was interpreted from the value of the given attribute
	 */
	default Object getInterpretedAttribute(I interp, QonfigAttributeDef attribute) {
		attribute = attribute.getDeclared();
		return getInterpretedAttribute(interp, attribute.getDeclarer().getName(), attribute.getDeclarer().getMajorVersion(),
			attribute.getDeclarer().getMinorVersion(), attribute.getName());
	}

	/**
	 * @param interp The element interpretation to get the attribute for
	 * @param toolkitName The name of the toolkit defining the attribute to get the value for
	 * @param majorVersion The major version of the toolkit defining the attribute to get the value for
	 * @param minorVersion The minor version of the toolkit defining the attribute to get the value for
	 * @param attribute The name of the attribute to get the value for
	 * @return The object on the element interpretation that was interpreted from the value of the given attribute
	 */
	Object getInterpretedAttribute(I interp, String toolkitName, int majorVersion, int minorVersion, String attribute);

	/**
	 * @param interp The element interpretation to get the element value for
	 * @return The object on the element interpretation that was interpreted from the value of the element
	 */
	Object getInterpretedElementValue(I interp);

	/**
	 * @param def The element definition to get the children for
	 * @param child The child role definition to get the elements for
	 * @return The element definitions interpreted from each of the children fulfilling the given role on on the given parent element
	 *         definition
	 */
	default List<? extends ExElement.Def<?>> getDefChildren(D def, QonfigChildDef child) {
		child = child.getDeclared();
		return getDefChildren(def, child.getDeclarer().getName(), child.getDeclarer().getMajorVersion(),
			child.getDeclarer().getMinorVersion(), child.getName());
	}

	/**
	 * @param def The element definition to get the children for
	 * @param toolkitName The name of the toolkit defining the child role to get the elements for
	 * @param majorVersion The major version of the toolkit defining the child role to get the elements for
	 * @param minorVersion The minor version of the toolkit defining the child role to get the elements for
	 * @param child The name of the child role to get the elements for
	 * @return The element definitions interpreted from each of the children fulfilling the given role on on the given parent element
	 *         definition
	 */
	List<? extends ExElement.Def<?>> getDefChildren(D def, String toolkitName, int majorVersion, int minorVersion, String child);

	/**
	 * @param interp The element interpretation to get the children for
	 * @param child The child role definition to get the elements for
	 * @return The element interpretations interpreted from each of the children fulfilling the given role on on the given parent element
	 *         interpretation
	 */
	default List<? extends ExElement.Interpreted<?>> getInterpretedChildren(I interp, QonfigChildDef child) {
		child = child.getDeclared();
		return getInterpretedChildren(interp, child.getDeclarer().getName(), child.getDeclarer().getMajorVersion(),
			child.getDeclarer().getMinorVersion(), child.getName());
	}

	/**
	 * @param interp The element interpretation to get the children for
	 * @param toolkitName The name of the toolkit defining the child role to get the elements for
	 * @param majorVersion The major version of the toolkit defining the child role to get the elements for
	 * @param minorVersion The minor version of the toolkit defining the child role to get the elements for
	 * @param child The name of the child role to get the elements for
	 * @return The element interpretations interpreted from each of the children fulfilling the given role on on the given parent element
	 *         interpretation
	 */
	List<? extends ExElement.Interpreted<?>> getInterpretedChildren(I interp, String toolkitName, int majorVersion, int minorVersion,
		String child);

	/**
	 * @param element The element instance to get the children for
	 * @param child The child role definition to get the elements for
	 * @return The element instances interpreted from each of the children fulfilling the given role on on the given parent element instance
	 */
	default List<? extends ExElement> getElementChildren(E element, QonfigChildDef child) {
		child = child.getDeclared();
		return getElementChildren(element, child.getDeclarer().getName(), child.getDeclarer().getMajorVersion(),
			child.getDeclarer().getMinorVersion(), child.getName());
	}

	/**
	 * @param element The element instance to get the children for
	 * @param toolkitName The name of the toolkit defining the child role to get the elements for
	 * @param majorVersion The major version of the toolkit defining the child role to get the elements for
	 * @param minorVersion The minor version of the toolkit defining the child role to get the elements for
	 * @param child The name of the child role to get the elements for
	 * @return The element instances interpreted from each of the children fulfilling the given role on on the given parent element instance
	 */
	List<? extends ExElement> getElementChildren(E element, String toolkitName, int majorVersion, int minorVersion, String child);

	/**
	 * Uses the {@link ExElementTraceable}, {@link ExMultiElementTraceable}, {@link QonfigAttributeGetter}, {@link QonfigChildGetter}, and
	 * {@link TraceabilityConfiguration} annotations on methods in an element or add-on definition to determine how to retrieve values
	 * interpreted from the various attributes, element value, and child roles of an expresso element.
	 *
	 * @param <E> The type of the element the traceability is for
	 * @param type The type of element or add-on definition to parse traceability for
	 * @param toolkit The Qonfig toolkit to use to interpret traceability
	 * @param reporting Error reporting for if traceability cannot be interpreted
	 * @return Traceability for each Qonfig type for which tracability is configured in the given class
	 */
	public static <E extends ExElement> Map<QonfigElementKey, SingleTypeTraceability<? super E, ?, ?>> traceabilityFor(Class<?> type,
		QonfigToolkit toolkit, ErrorReporting reporting) {
		return SingleTypeTraceability.traceabilityFor(type, toolkit, reporting);
	}

	/**
	 * Joins two traceability maps
	 *
	 * @param <E> The type of the element that the traceability is for
	 * @param traceability The first (mutable) traceability map to merge
	 * @param other The second map to merge
	 */
	public static <E extends ExElement> void join(Map<QonfigElementKey, SingleTypeTraceability<? super E, ?, ?>> traceability,
		Map<QonfigElementKey, ? extends SingleTypeTraceability<?, ?, ?>> other) {
		for (Map.Entry<QonfigElementKey, ? extends SingleTypeTraceability<?, ?, ?>> entry : other.entrySet())
			traceability.compute(entry.getKey(),
				(key, old) -> old == null ? (SingleTypeTraceability<? super E, ?, ?>) entry.getValue()
					: ((SingleTypeTraceability<E, ExElement.Interpreted<E>, ExElement.Def<? extends E>>) old)
					.combine((SingleTypeTraceability<E, ExElement.Interpreted<E>, ExElement.Def<? extends E>>) entry.getValue()));
	}

	/**
	 * Traceability support for a single Qonfig attribute on an element
	 *
	 * @param <E> The type of element instance this getter supports traceability for
	 * @param <I> The type of element interpretation this getter supports traceability for
	 * @param <D> The type of element definition this getter supports traceability for
	 */
	public interface AttributeValueGetter<E extends ExElement, I extends ExElement.Interpreted<? extends E>, D extends ExElement.Def<? extends E>> {
		/**
		 * @param def The element definition to get the attribute from
		 * @return The object on the element definition that was parsed from the value of the attribute this getter is for
		 */
		Object getFromDef(D def);

		/**
		 * @param interp The element interpretation to get the attribute from
		 * @return The object on the element interpretation that was interpreted from the value of the attribute this getter is for
		 */
		Object getFromInterpreted(I interp);

		/**
		 * Creates an attribute getter
		 *
		 * @param <E> The type of element instance to support traceability for
		 * @param <I> The type of element interpretation to support traceability for
		 * @param <D> The type of element definition to support traceability for
		 * @param defGetter Getter function for the attribute value on the element definition
		 * @param interpretedGetter Getter function for the attribute value on the element interpretation
		 * @return Traceability support for the attribute
		 */
		public static <E extends ExElement, I extends ExElement.Interpreted<? extends E>, D extends ExElement.Def<? extends E>> Default<E, I, D> of(
			Function<? super D, ?> defGetter, Function<? super I, ?> interpretedGetter) {
			return new Default<>(defGetter, interpretedGetter);
		}

		/**
		 * Default {@link AttributeValueGetter} implementation
		 *
		 * @param <E> The type of element instance this getter supports traceability for
		 * @param <I> The type of element interpretation this getter supports traceability for
		 * @param <D> The type of element definition this getter supports traceability for
		 */
		public static class Default<E extends ExElement, I extends ExElement.Interpreted<? extends E>, D extends ExElement.Def<? extends E>>
		implements AttributeValueGetter<E, I, D> {
			private final Function<? super D, ?> theDefGetter;
			private final Function<? super I, ?> theInterpretedGetter;

			/**
			 * @param defGetter Getter function for the attribute value on the element definition
			 * @param interpretedGetter Getter function for the attribute value on the element interpretation
			 */
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

	/**
	 * Traceability support for a single Qonfig child role on an element
	 *
	 * @param <E> The type of element instance this getter supports traceability for
	 * @param <I> The type of element interpretation this getter supports traceability for
	 * @param <D> The type of element definition this getter supports traceability for
	 */
	public interface ChildElementGetter<E extends ExElement, I extends ExElement.Interpreted<? extends E>, D extends ExElement.Def<? extends E>> {
		/**
		 * @param def The element definition to get the children from
		 * @return The element definitions interpreted from each of the children fulfilling the child role on on the given parent element
		 *         definition
		 */
		List<? extends ExElement.Def<?>> getChildrenFromDef(D def);

		/**
		 * @param interp The element interpretation to get the children from
		 * @return The element interpretations interpreted from each of the children fulfilling the child role on on the given parent
		 *         element interpretation
		 */
		List<? extends ExElement.Interpreted<?>> getChildrenFromInterpreted(I interp);

		/**
		 * @param element The element instance to get the children from
		 * @return The element instances interpreted from each of the children fulfilling the child role on on the given parent element
		 *         instance
		 */
		List<? extends ExElement> getChildrenFromElement(E element);

		/**
		 * Creates a child getter
		 *
		 * @param <E> The type of element instance to support traceability for
		 * @param <I> The type of element interpretation to support traceability for
		 * @param <D> The type of element definition to support traceability for
		 * @param defGetter Getter function for the child elements on the element definition
		 * @param interpGetter Getter function for the child elements on the element interpretation
		 * @param elGetter Getter function for the child elements on the element instance
		 * @return Traceability support for the child role
		 */
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

	/**
	 * Traceability support for a single Qonfig attribute on an add-on
	 *
	 * @param <E> The type of element the add-on applies to
	 * @param <AO> The type of add-on this getter supports traceability for
	 * @param <I> The type of add-on interpretation this getter supports traceability for
	 * @param <D> The type of add-on definition this getter supports traceability for
	 */
	public static abstract class AddOnAttributeGetter<E extends ExElement, AO extends ExAddOn<? super E>, I extends ExAddOn.Interpreted<? super E, ? extends AO>, D extends ExAddOn.Def<? super E, ? extends AO>> //
	implements AttributeValueGetter<E, ExElement.Interpreted<? extends E>, ExElement.Def<? extends E>> {
		private final Class<D> theDefType;
		private final Class<I> theInterpretedType;

		/**
		 * @param defType The type of the add-on definition
		 * @param interpretedType The type of the add-on interpretations
		 */
		protected AddOnAttributeGetter(Class<? super D> defType, Class<? super I> interpretedType) {
			theDefType = (Class<D>) (Class<?>) defType;
			theInterpretedType = (Class<I>) (Class<?>) interpretedType;
		}

		/**
		 * @param def The add-on definition to get the attribute value from
		 * @return The object on the element definition that was parsed from the value of the attribute this getter is for
		 */
		public abstract Object getFromDef(D def);

		/**
		 * @param interpreted The add-on interpretation to get the attribute value from
		 * @return The object on the element interpretation that was interpreted from the value of the attribute this getter is for
		 */
		public abstract Object getFromInterpreted(I interpreted);

		@Override
		public Object getFromDef(ExElement.Def<? extends E> def) {
			return def.getAddOnValue(theDefType, this::getFromDef);
		}

		@Override
		public Object getFromInterpreted(ExElement.Interpreted<? extends E> interp) {
			return theInterpretedType == null ? null : interp.getAddOnValue(theInterpretedType, this::getFromInterpreted);
		}

		/**
		 *
		 * @param <E> The type of element the add-on applies to
		 * @param <AO> The type of add-on to support traceability for
		 * @param <I> The type of add-on interpretation to support traceability for
		 * @param <D> The type of add-on definition to support traceability for
		 * @param defType The add-on definition type
		 * @param defGetter Getter function for the value of the attribute on the add-on definition
		 * @param interpretedType The add-on interpretation type
		 * @param interpretedGetter Getter function for the value of the attribute on the add-on interpretation
		 * @return Traceability support for the given attribute on the add-on
		 */
		public static <E extends ExElement, AO extends ExAddOn<? super E>, I extends ExAddOn.Interpreted<? super E, ? extends AO>, D extends ExAddOn.Def<? super E, ? extends AO>> Default<E, AO, I, D> of(
			Class<? super D> defType, Function<? super D, ?> defGetter, Class<? super I> interpretedType,
			Function<? super I, ?> interpretedGetter) {
			return new Default<>(defType, defGetter, interpretedType, interpretedGetter);
		}

		/**
		 * Default {@link AddOnAttributeGetter} implementation
		 *
		 * @param <E> The type of element the add-on applies to
		 * @param <AO> The type of add-on this getter supports traceability for
		 * @param <I> The type of add-on interpretation this getter supports traceability for
		 * @param <D> The type of add-on definition this getter supports traceability for
		 */
		public static class Default<E extends ExElement, AO extends ExAddOn<? super E>, I extends ExAddOn.Interpreted<? super E, ? extends AO>, D extends ExAddOn.Def<? super E, ? extends AO>>
		extends AddOnAttributeGetter<E, AO, I, D> {
			private final Function<? super D, ?> theDefGetter;
			private final Function<? super I, ?> theInterpretedGetter;

			/**
			 * @param defType The add-on definition type
			 * @param defGetter Getter function for the value of the attribute on the add-on definition
			 * @param interpretedType The add-on interpretation type
			 * @param interpretedGetter Getter function for the value of the attribute on the add-on interpretation
			 */
			public Default(Class<? super D> defType, Function<? super D, ?> defGetter, Class<? super I> interpretedType,
				Function<? super I, ?> interpretedGetter) {
				super(defType, interpretedType);
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

	/**
	 * Traceability support for a single Qonfig child role on an add-on
	 *
	 * @param <E> The type of element the add-on applies to
	 * @param <AO> The type of add-on this getter supports traceability for
	 * @param <I> The type of add-on interpretation this getter supports traceability for
	 * @param <D> The type of add-on definition this getter supports traceability for
	 */
	public static abstract class AddOnChildGetter<E extends ExElement, AO extends ExAddOn<? super E>, I extends ExAddOn.Interpreted<? super E, ? extends AO>, D extends ExAddOn.Def<? super E, ? extends AO>> //
	implements ChildElementGetter<E, ExElement.Interpreted<? extends E>, ExElement.Def<? extends E>> {
		private final Class<D> theDefType;
		private final Class<I> theInterpretedType;
		private final Class<AO> theAddOnType;

		/**
		 * @param defType The add-on definition type
		 * @param interpretedType The add-on interpretation type
		 * @param addOnType The add-on type
		 */
		protected AddOnChildGetter(Class<? super D> defType, Class<? super I> interpretedType, Class<? super AO> addOnType) {
			theDefType = (Class<D>) (Class<?>) defType;
			theInterpretedType = (Class<I>) (Class<?>) interpretedType;
			theAddOnType = (Class<AO>) (Class<?>) addOnType;
		}

		/**
		 * @param def The add-on definition to get the children from
		 * @return The element definitions interpreted from each of the children fulfilling the child role on on the given parent add-on
		 *         definition
		 */
		public abstract List<? extends ExElement.Def<?>> getFromDef(D def);

		/**
		 * @param interpreted The add-on interpretation to get the children from
		 * @return The element interpretations interpreted from each of the children fulfilling the child role on on the given parent add-on
		 *         interpretation
		 */
		public abstract List<? extends ExElement.Interpreted<?>> getFromInterpreted(I interpreted);

		/**
		 * @param addOn The add-on instance to get the children from
		 * @return The element instances interpreted from each of the children fulfilling the child role on on the given parent add-on
		 *         instance
		 */
		public abstract List<? extends ExElement> getFromAddOn(AO addOn);

		@Override
		public List<? extends ExElement.Def<?>> getChildrenFromDef(ExElement.Def<? extends E> def) {
			return def.getAddOnValue(theDefType, this::getFromDef);
		}

		@Override
		public List<? extends ExElement.Interpreted<?>> getChildrenFromInterpreted(ExElement.Interpreted<? extends E> interp) {
			if (theInterpretedType == null)
				return Collections.emptyList();
			return interp.getAddOnValue(theInterpretedType, this::getFromInterpreted);
		}

		@Override
		public List<? extends ExElement> getChildrenFromElement(E element) {
			if (theAddOnType == null)
				return Collections.emptyList();
			return element.getAddOnValue(theAddOnType, this::getFromAddOn);
		}

		/**
		 *
		 * @param <E> The type of element the add-on applies to
		 * @param <AO> The type of add-on to support traceability for
		 * @param <I> The type of add-on interpretation to support traceability for
		 * @param <D> The type of add-on definition to support traceability for
		 * @param defType The add-on definition type
		 * @param defGetter Getter function for the child elements on the add-on definition
		 * @param interpretedType The add-on interpretation type
		 * @param interpretedGetter Getter function for the child elements on the add-on interpretation
		 * @param addOnType The add-on type
		 * @param addOnGetter Getter function for the child elements on the add-on instance
		 * @return Traceability support for the given child on the add-on
		 */
		public static <E extends ExElement, AO extends ExAddOn<? super E>, I extends ExAddOn.Interpreted<? super E, ? extends AO>, D extends ExAddOn.Def<? super E, ? extends AO>> Default<E, AO, I, D> of(
			Class<? super D> defType, Function<? super D, ? extends List<? extends ExElement.Def<?>>> defGetter,
				Class<? super I> interpretedType, Function<? super I, ? extends List<? extends ExElement.Interpreted<?>>> interpretedGetter,
					Class<? super AO> addOnType, Function<? super AO, ? extends List<? extends ExElement>> addOnGetter) {
			return new Default<>(defType, defGetter, interpretedType, interpretedGetter, addOnType, addOnGetter);
		}

		/**
		 * Default {@link AddOnChildGetter} implementation
		 *
		 * @param <E> The type of element the add-on applies to
		 * @param <AO> The type of add-on this getter supports traceability for
		 * @param <I> The type of add-on interpretation this getter supports traceability for
		 * @param <D> The type of add-on definition this getter supports traceability for
		 */
		public static class Default<E extends ExElement, AO extends ExAddOn<? super E>, I extends ExAddOn.Interpreted<? super E, ? extends AO>, D extends ExAddOn.Def<? super E, ? extends AO>>
		extends AddOnChildGetter<E, AO, I, D> {
			private final Function<? super D, ? extends List<? extends ExElement.Def<?>>> theDefGetter;
			private final Function<? super I, ? extends List<? extends ExElement.Interpreted<?>>> theInterpretedGetter;
			private final Function<? super AO, ? extends List<? extends ExElement>> theAddOnGetter;

			/**
			 * @param defType The add-on definition type
			 * @param defGetter Getter function for the child elements on the add-on definition
			 * @param interpretedType The add-on interpretation type
			 * @param interpretedGetter Getter function for the child elements on the add-on interpretation
			 * @param addOnType The add-on type
			 * @param addOnGetter Getter function for the child elements on the add-on instance
			 */
			public Default(Class<? super D> defType, Function<? super D, ? extends List<? extends ExElement.Def<?>>> defGetter,
				Class<? super I> interpretedType, Function<? super I, ? extends List<? extends ExElement.Interpreted<?>>> interpretedGetter,
					Class<? super AO> addOnType, Function<? super AO, ? extends List<? extends ExElement>> addOnGetter) {
				super(defType, interpretedType, addOnType);
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

	/**
	 * {@link ElementTypeTraceability} for a single Qonfig type
	 *
	 * @param <E> The type of element instance this object supports traceability for
	 * @param <I> The type of element interpretation this object supports traceability for
	 * @param <D> The type of element definition this object supports traceability for
	 */
	public static class SingleTypeTraceability<E extends ExElement, I extends ExElement.Interpreted<? extends E>, D extends ExElement.Def<? extends E>>
	implements ElementTypeTraceability<E, I, D> {
		private static WeakHashMap<QonfigToolkit, Map<Class<?>, Map<QonfigElementKey, ? extends SingleTypeTraceability<?, ?, ?>>>> TRACEABILITY = new WeakHashMap<>();

		static synchronized <E extends ExElement> Map<QonfigElementKey, SingleTypeTraceability<? super E, ?, ?>> traceabilityFor(
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
			else if (ExElement.Def.class.isAssignableFrom(type)) {
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

		private static <E extends ExElement, I extends ExElement.Interpreted<? extends E>, D extends ExElement.Def<? extends E>> SingleTypeTraceability<E, I, D> getElementTraceability(
			String toolkitName, Version toolkitVersion, String qonfigTypeName, Class<D> def, Class<I> interp, Class<E> element)
				throws IllegalArgumentException {
			SingleTypeTraceabilityBuilder<E, I, D> builder = build(toolkitName, toolkitVersion, qonfigTypeName);
			builder.reflectMethods(def, interp, element);
			return builder.build();
		}

		private static <E extends ExElement, I extends ExElement.Interpreted<? extends E>, D extends ExElement.Def<? extends E>> SingleTypeTraceabilityBuilder<E, I, D> build(
			String toolkitName, Version toolkitVersion, String typeName) {
			return ElementTypeTraceability.build(toolkitName, toolkitVersion.major, toolkitVersion.minor, typeName);
		}

		@SuppressWarnings("rawtypes")
		private static <E extends ExElement, AO extends ExAddOn<E>> SingleTypeTraceability<E, Interpreted<E>, Def<? extends E>> _traceAddOn(
			Class<?> type, QonfigToolkit.ToolkitDef tk, QonfigElementOrAddOn qonfigType, ExElementTraceable traceable) {
			Class interpretation = traceable.interpretation() == void.class ? null : traceable.interpretation();
			Class instance = traceable.instance() == void.class ? null : traceable.instance();
			return getAddOnTraceability(tk.name, tk.majorVersion, tk.minorVersion, qonfigType.getName(), (Class) type, interpretation,
				instance);
		}

		private static <E extends ExElement, AO extends ExAddOn<? super E>, I extends ExAddOn.Interpreted<? super E, ? extends AO>, //
		D extends ExAddOn.Def<? super E, ? extends AO>> SingleTypeTraceability<E, ExElement.Interpreted<? extends E>, ExElement.Def<? extends E>>//
		getAddOnTraceability(String toolkitName, int majorVersion, int minorVersion, String typeName, Class<? super D> defType,
			Class<? super I> interpType, Class<? super AO> addOnType) {
			AddOnBuilder<E, AO, I, D> builder = buildAddOn(toolkitName, majorVersion, minorVersion, typeName, defType, interpType,
				addOnType);
			builder.reflectAddOnMethods();
			return builder.build();
		}

		private static <E extends ExElement, AO extends ExAddOn<? super E>, I extends ExAddOn.Interpreted<? super E, ? extends AO>, D extends ExAddOn.Def<? super E, ? extends AO>> AddOnBuilder<E, AO, I, D> buildAddOn(
			String toolkitName, int majorVersion, int minorVersion, String typeName, Class<? super D> defType, Class<? super I> interpType,
			Class<? super AO> addOnType) {
			return new AddOnBuilder<>(toolkitName, new QonfigToolkit.ToolkitDefVersion(majorVersion, minorVersion), typeName, defType,
				interpType, addOnType);
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

		/** @return The name of the toolkit that declares the Qonfig type that this traceability is for */
		public String getToolkitName() {
			return theToolkitName;
		}

		/** @return The version of the toolkit that declares the Qonfig type that this traceability is for */
		public QonfigToolkit.ToolkitDefVersion getToolkitVersion() {
			return theToolkitVersion;
		}

		/** @return The name of the Qonfig type that this traceability is for */
		public String getTypeName() {
			return theTypeName;
		}

		SingleTypeTraceability<E, I, D> combine(SingleTypeTraceability<E, I, D> other) {
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
					reporting.warn("Traceability not configured for attribute '" + attr.getName() + "' of " + theToolkitName + " "
						+ theToolkitVersion + "." + theTypeName);
			}
			if (!unusedAttrs.isEmpty())
				reporting.warn("No such attributes: " + unusedAttrs + " found in element for " + theToolkitName + " " + theToolkitVersion
					+ "." + theTypeName);

			if (type.getValue() != null && type.getValue() instanceof QonfigValueDef.Declared && type.getValue().getOwner() == type) {
				if (theValue == null)
					reporting
					.warn("Traceability not configured for value of " + theToolkitName + " " + theToolkitVersion + "." + theTypeName);
			} else if (theValue != null)
				reporting.warn("Value configured for value-less element " + theToolkitName + " " + theToolkitVersion + "." + theTypeName);

			Set<String> unusedChildren = new HashSet<>(theChildren.keySet());
			for (QonfigChildDef.Declared child : type.getDeclaredChildren().values()) {
				if (!unusedChildren.remove(child.getName()))
					reporting.warn("Traceablity not configured for child '" + child.getName() + "' of " + theToolkitName + " "
						+ theToolkitVersion + "." + theTypeName);
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

		/** @return A builder pre-populated with all the information from this type's traceability */
		public SingleTypeTraceabilityBuilder<E, I, D> copy() {
			SingleTypeTraceabilityBuilder<E, I, D> builder = new SingleTypeTraceabilityBuilder<>(theToolkitName, theToolkitVersion,
				theTypeName);
			builder.theAttributes.putAll(theAttributes);
			builder.theValue = theValue;
			builder.theChildren.putAll(theChildren);
			return builder;
		}

		@Override
		public String toString() {
			return theToolkitName + " " + theToolkitVersion + " " + theTypeName;
		}
	}

	/**
	 * @param <E> The type of element instance to support traceability for
	 * @param <I> The type of element interpretation to support traceability for
	 * @param <D> The type of element definition to support traceability for
	 * @param toolkitName The name of the toolkit declaring the element to support traceability for
	 * @param majorVersion The major version of the toolkit declaring the element to support traceability for
	 * @param minorVersion The minor version of the toolkit declaring the element to support traceability for
	 * @param typeName The name of the element to support traceability for
	 * @return The builder for traceability for the given element
	 */
	public static <E extends ExElement, I extends ExElement.Interpreted<? extends E>, D extends ExElement.Def<? extends E>> SingleTypeTraceabilityBuilder<E, I, D> build(
		String toolkitName, int majorVersion, int minorVersion, String typeName) {
		return new SingleTypeTraceabilityBuilder<>(toolkitName, new QonfigToolkit.ToolkitDefVersion(majorVersion, minorVersion), typeName);
	}

	/**
	 * A builder for traceability support on a particular Qonfig element-def
	 *
	 * @param <E> The type of element instance to support traceability for
	 * @param <I> The type of element interpretation to support traceability for
	 * @param <D> The type of element definition to support traceability for
	 */
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

		SingleTypeTraceabilityBuilder<E, I, D> reflectMethods(Class<?> defClass, Class<?> interpClass, Class<?> elementClass) {
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
						withValue(new Internal.ReflectedAttributeGetter<>(defMethod, interpMethod));
					else
						withAttribute(attr.value(), new Internal.ReflectedAttributeGetter<>(defMethod, interpMethod));
				}
				QonfigChildGetter child = defMethod.getAnnotation(QonfigChildGetter.class);
				if (child != null && !checkAsType(defMethod, child.asType())) {
					child = null;
				}
				if (child != null) {
					Internal.ChildGetterReturn childGetterType = Internal.checkReturnType(defMethod, ExElement.Def.class);
					if (childGetterType != null) {
						Method interpMethod;
						try {
							if (interpClass != null) {
								interpMethod = interpClass.getDeclaredMethod(defMethod.getName());
								Internal.ChildGetterReturn interpGetterType = Internal.checkReturnType(interpMethod,
									ExElement.Interpreted.class);
								if (interpGetterType == null)
									interpMethod = null;
								else if (interpGetterType != childGetterType) {
									System.err.println("If the definition method (" + defClass.getName() + "." + defMethod.getName()
									+ "()) returns a " + childGetterType + ", the interpreted method (" + interpClass.getName() + "."
									+ defMethod.getName() + "()) must also return a " + childGetterType);
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
								Internal.ChildGetterReturn elementListReturn = Internal.checkReturnType(elementMethod, ExElement.class);
								if (elementListReturn == null)
									elementMethod = null;
								else if (elementListReturn != childGetterType) {
									System.err.println("If the definition method (" + defClass.getName() + "." + defMethod.getName()
									+ "()) returns a " + childGetterType + ", the element method (" + elementClass.getName() + "."
									+ defMethod.getName() + "()) must also return a " + childGetterType);
									elementMethod = null;
								}
							} else
								elementMethod = null;
						} catch (NoSuchMethodException | SecurityException e) {
							elementMethod = null;
						}

						withChild(child.value(),
							new Internal.ReflectedChildGetter<>(defMethod, interpMethod, elementMethod, childGetterType));
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

		/**
		 * @param attribute The name of the attribute to support traceability for
		 * @param getter Traceability support for the given attribute on this builder's element
		 * @return This builder
		 */
		public SingleTypeTraceabilityBuilder<E, I, D> withAttribute(String attribute,
			AttributeValueGetter<? super E, ? super I, ? super D> getter) {
			theAttributes.put(attribute, getter);
			return this;
		}

		/**
		 * @param attribute The name of the attribute to support traceability for
		 * @param defGetter Getter function for the attribute value on the element definition
		 * @param interpGetter Getter function for the attribute value on the element interpretation
		 * @return This builder
		 */
		public SingleTypeTraceabilityBuilder<E, I, D> withAttribute(String attribute, Function<? super D, ?> defGetter,
			Function<? super I, ?> interpGetter) {
			return withAttribute(attribute, AttributeValueGetter.of(defGetter, interpGetter));
		}

		/**
		 * @param value Traceability support for the element value on this builder's element
		 * @return This builder
		 */
		public SingleTypeTraceabilityBuilder<E, I, D> withValue(AttributeValueGetter<? super E, ? super I, ? super D> value) {
			theValue = value;
			return this;
		}

		/**
		 * @param defGetter Getter function for the attribute value on the element definition
		 * @param interpGetter Getter function for the attribute value on the element interpretation
		 * @return This builder
		 */
		public SingleTypeTraceabilityBuilder<E, I, D> withValue(Function<? super D, ?> defGetter, Function<? super I, ?> interpGetter) {
			return withValue(AttributeValueGetter.of(defGetter, interpGetter));
		}

		/**
		 * @param child The name of the child role to support traceability for
		 * @param getter Traceability support for the given child on this builder's element
		 * @return This builder
		 */
		public SingleTypeTraceabilityBuilder<E, I, D> withChild(String child, ChildElementGetter<? super E, ? super I, ? super D> getter) {
			theChildren.put(child, getter);
			return this;
		}

		/**
		 * @param child The name of the child role to support traceability for
		 * @param defGetter Getter function for the child elements on the element definition
		 * @param interpGetter Getter function for the child elements on the element interpretation
		 * @param elementGetter Getter function for the child elements on the element instance
		 * @return This builder
		 */
		public SingleTypeTraceabilityBuilder<E, I, D> withChild(String child,
			Function<? super D, ? extends List<? extends ExElement.Def<?>>> defGetter,
				Function<? super I, ? extends List<? extends ExElement.Interpreted<?>>> interpGetter,
					Function<? super E, ? extends List<? extends ExElement>> elementGetter) {
			return withChild(child, ChildElementGetter.of(defGetter, interpGetter, elementGetter));
		}

		SingleTypeTraceability<E, I, D> build() {
			return new SingleTypeTraceability<>(theToolkitName, theToolkitVersion, theTypeName, theAttributes, theValue, theChildren);
		}
	}

	/**
	 * A builder for traceability support on a particular Qonfig add-on
	 *
	 * @param <E> The element type that this builder's add-on applies to
	 * @param <AO> The add-on that this builder supports traceability for
	 * @param <I> The interpretation of the add-on that this builder supports traceability for
	 * @param <D> The definition of the add-on that this builder supports traceability for
	 */
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

		AddOnBuilder<E, AO, I, D> reflectAddOnMethods() {
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
						withValue(new Internal.ReflectedAddOnAttributeGetter<>(theDefClass, defMethod, theInterpretedClass, interpMethod));
					else
						withAttribute(attr.value(),
							new Internal.ReflectedAddOnAttributeGetter<>(theDefClass, defMethod, theInterpretedClass, interpMethod));
				}
				QonfigChildGetter child = defMethod.getAnnotation(QonfigChildGetter.class);
				if (child != null && !checkAsType(defMethod, child.asType())) {
					child = null;
				}
				if (child != null) {
					Internal.ChildGetterReturn childGetterType = Internal.checkReturnType(defMethod, ExElement.Def.class);
					if (childGetterType != null) {
						Method interpMethod;
						try {
							if (theInterpretedClass != null) {
								interpMethod = theInterpretedClass.getDeclaredMethod(defMethod.getName());
								Internal.ChildGetterReturn interpGetterType = Internal.checkReturnType(interpMethod,
									ExElement.Interpreted.class);
								if (interpGetterType == null)
									interpMethod = null;
								else if (interpGetterType != childGetterType) {
									System.err.println("If the definition method (" + theDefClass.getName() + "." + defMethod.getName()
									+ "()) returns a " + childGetterType + ", the interpreted method (" + theInterpretedClass.getName()
									+ "." + defMethod.getName() + "()) must also return a " + childGetterType);
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
								Internal.ChildGetterReturn elementListReturn = Internal.checkReturnType(elementMethod, ExElement.class);
								if (elementListReturn == null)
									elementMethod = null;
								else if (elementListReturn != childGetterType) {
									System.err.println("If the definition method (" + theDefClass.getName() + "." + defMethod.getName()
									+ "()) returns a " + childGetterType + ", the element method (" + theAddOnClass.getName() + "."
									+ defMethod.getName() + "()) must also return a " + childGetterType);
									elementMethod = null;
								}
							} else
								elementMethod = null;
						} catch (NoSuchMethodException | SecurityException e) {
							elementMethod = null;
						}

						withChild(child.value(), new Internal.ReflectedAddOnChildGetter<>(theDefClass, defMethod, theInterpretedClass,
							interpMethod, theAddOnClass, elementMethod, childGetterType));
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

		/**
		 * Configures traceability for an attribute of the add-on
		 *
		 * @param attribute The name of the attribute on the add-on
		 * @param defGetter The getter for the attribute on the add-on definition
		 * @param interpGetter The getter for the attribute on the add-on interpretation
		 * @return This builder
		 */
		public AddOnBuilder<E, AO, I, D> withAddOnAttribute(String attribute, Function<? super D, ?> defGetter,
			Function<? super I, ?> interpGetter) {
			return withAttribute(attribute,
				AddOnAttributeGetter.<E, AO, I, D> of(theDefClass, defGetter, theInterpretedClass, interpGetter));
		}

		/**
		 * Configures traceability for the element value of the add-on
		 *
		 * @param defGetter The getter for the element value on the add-on definition
		 * @param interpGetter The getter for the element value on the add-on interpretation
		 * @return This builder
		 */
		public AddOnBuilder<E, AO, I, D> withAddOnValue(Function<? super D, ?> defGetter, Function<? super I, ?> interpGetter) {
			return withValue(AddOnAttributeGetter.<E, AO, I, D> of(theDefClass, defGetter, theInterpretedClass, interpGetter));
		}

		/**
		 * Configures traceability for a child role of the add-on
		 *
		 * @param child The name of the child role on the add-on
		 * @param defGetter The getter for the children on the add-on definition
		 * @param interpGetter The getter for the children on the add-on interpretation
		 * @param elementGetter The getter for the children on the add-on instance
		 * @return This builder
		 */
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

	/** Internal implementation methods for {@link ElementTypeTraceability} */
	static class Internal {
		enum ChildGetterReturn {
			Singleton, //
			Collection, //
			Map;
		}

		private static final String CHILD_GETTER_ERROR_MSG = " cannot be a " + QonfigChildGetter.class.getSimpleName() + "\n"//
			+ "Return type is incompatible with all of:\n" + " * " + ExElement.class.getSimpleName() + ".Def\n" + " * Collection<? extends "
			+ ExElement.class.getSimpleName() + ".Def>\n" + " * Map<?, ? extends " + ExElement.class.getSimpleName() + ".Def>";

		static ChildGetterReturn checkReturnType(Method method, Class<?> targetClass) {
			TypeToken<?> retType = TypeTokens.get().of(method.getGenericReturnType());
			TypeToken<?> targetType;
			if (targetClass.getTypeParameters().length > 0)
				targetType = TypeTokens.get().keyFor(targetClass).wildCard();
			else
				targetType = TypeTokens.get().of(targetClass);
			if (targetType.isSupertypeOf(retType))
				return ChildGetterReturn.Singleton;
			else if (TypeTokens.get().keyFor(Collection.class).wildCard().isSupertypeOf(retType)) {
				TypeToken<?> elementType = retType.resolveType(Collection.class.getTypeParameters()[0]);
				if (!targetType.isSupertypeOf(elementType)) {
					System.err.println("Method " + method.getDeclaringClass().getName() + "." + method.getName() + "() with return type "
						+ retType + CHILD_GETTER_ERROR_MSG);
					return null;
				}
				return ChildGetterReturn.Collection;
			} else if (TypeTokens.get().keyFor(Map.class).wildCard().isSupertypeOf(retType)) {
				TypeToken<?> elementType = retType.resolveType(Map.class.getTypeParameters()[1]);
				if (!targetType.isSupertypeOf(elementType)) {
					System.err.println("Method " + method.getDeclaringClass().getName() + "." + method.getName() + "() with return type "
						+ retType + CHILD_GETTER_ERROR_MSG);
					return null;
				}
				return ChildGetterReturn.Map;
			} else {
				System.err
				.println("Method " + method.getDeclaringClass().getName() + "." + method.getName() + "() with return type " + retType);
				return null;
			}
		}

		static class ReflectedAttributeGetter<E extends ExElement, I extends ExElement.Interpreted<? extends E>, D extends ExElement.Def<? extends E>>
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

		static class ReflectedAddOnAttributeGetter<E extends ExElement, AO extends ExAddOn<? super E>, I extends ExAddOn.Interpreted<? super E, ? extends AO>, D extends ExAddOn.Def<? super E, ? extends AO>>
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

		static class ReflectedChildGetter<E extends ExElement, I extends ExElement.Interpreted<? extends E>, D extends ExElement.Def<? extends E>>
		implements ChildElementGetter<E, I, D> {
			private final Method theDefGetter;
			private final Method theInterpretedGetter;
			private final Method theElementGetter;
			private final ChildGetterReturn theGetterType;

			public ReflectedChildGetter(Method defGetter, Method interpretedGetter, Method elementGetter, ChildGetterReturn getterType) {
				theDefGetter = defGetter;
				theInterpretedGetter = interpretedGetter;
				theElementGetter = elementGetter;
				theGetterType = getterType;
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
				else {
					switch (theGetterType) {
					case Singleton:
						return Collections.singletonList((ExElement.Def<?>) ret);
					case Collection:
						if (ret instanceof List)
							return (List<? extends ExElement.Def<?>>) ret;
						else
							return QommonsUtils.unmodifiableCopy((Collection<? extends ExElement.Def<?>>) ret);
					case Map:
						return QommonsUtils.unmodifiableCopy(((Map<?, ? extends ExElement.Def<?>>) ret).values());
					}
				}
				throw new IllegalStateException("Unrecognized getter type: " + theGetterType);
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
				else {
					switch (theGetterType) {
					case Singleton:
						return Collections.singletonList((ExElement.Interpreted<?>) ret);
					case Collection:
						if (ret instanceof List)
							return (List<? extends ExElement.Interpreted<?>>) ret;
						else
							return QommonsUtils.unmodifiableCopy((Collection<? extends ExElement.Interpreted<?>>) ret);
					case Map:
						return QommonsUtils.unmodifiableCopy(((Map<?, ? extends ExElement.Interpreted<?>>) ret).values());
					}
				}
				throw new IllegalStateException("Unrecognized getter type: " + theGetterType);
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
				else {
					switch (theGetterType) {
					case Singleton:
						return Collections.singletonList((ExElement) ret);
					case Collection:
						if (ret instanceof List)
							return (List<? extends ExElement>) ret;
						else
							return QommonsUtils.unmodifiableCopy((Collection<? extends ExElement>) ret);
					case Map:
						return QommonsUtils.unmodifiableCopy(((Map<?, ? extends ExElement>) ret).values());
					}
				}
				throw new IllegalStateException("Unrecognized getter type: " + theGetterType);
			}

			@Override
			public String toString() {
				return theDefGetter.getDeclaringClass().getSimpleName() + "." + theDefGetter.getName() + "()";
			}
		}

		static class ReflectedAddOnChildGetter<E extends ExElement, AO extends ExAddOn<? super E>, I extends ExAddOn.Interpreted<? super E, ? extends AO>, D extends ExAddOn.Def<? super E, ? extends AO>>
		extends AddOnChildGetter<E, AO, I, D> {
			private final Method theDefGetter;
			private final Method theInterpretedGetter;
			private final Method theElementGetter;
			private final ChildGetterReturn theGetterType;

			public ReflectedAddOnChildGetter(Class<D> defType, Method defGetter, Class<I> interpType, Method interpretedGetter,
				Class<AO> addOnType, Method elementGetter, ChildGetterReturn getterType) {
				super(defType, interpType, addOnType);
				theDefGetter = defGetter;
				theInterpretedGetter = interpretedGetter;
				theElementGetter = elementGetter;
				theGetterType = getterType;
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
				else {
					switch (theGetterType) {
					case Singleton:
						return Collections.singletonList((ExElement.Def<?>) ret);
					case Collection:
						if (ret instanceof List)
							return (List<? extends ExElement.Def<?>>) ret;
						else
							return QommonsUtils.unmodifiableCopy((Collection<? extends ExElement.Def<?>>) ret);
					case Map:
						return QommonsUtils.unmodifiableCopy(((Map<?, ? extends ExElement.Def<?>>) ret).values());
					}
				}
				throw new IllegalStateException("Unrecognized getter type: " + theGetterType);
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
				else {
					switch (theGetterType) {
					case Singleton:
						return Collections.singletonList((ExElement.Interpreted<?>) ret);
					case Collection:
						if (ret instanceof List)
							return (List<? extends ExElement.Interpreted<?>>) ret;
						else
							return QommonsUtils.unmodifiableCopy((Collection<? extends ExElement.Interpreted<?>>) ret);
					case Map:
						return QommonsUtils.unmodifiableCopy(((Map<?, ? extends ExElement.Interpreted<?>>) ret).values());
					}
				}
				throw new IllegalStateException("Unrecognized getter type: " + theGetterType);
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
				else {
					switch (theGetterType) {
					case Singleton:
						return Collections.singletonList((ExElement) ret);
					case Collection:
						if (ret instanceof List)
							return (List<? extends ExElement>) ret;
						else
							return QommonsUtils.unmodifiableCopy((Collection<? extends ExElement>) ret);
					case Map:
						return QommonsUtils.unmodifiableCopy(((Map<?, ? extends ExElement>) ret).values());
					}
				}
				throw new IllegalStateException("Unrecognized getter type: " + theGetterType);
			}
		}
	}

	/** The definition of a {@link QonfigElementOrAddOn} */
	public static class QonfigElementKey {
		/** The name of the toolkit that declared the element or add-on */
		public String toolkitName;
		/** The major version of the toolkit that declared the element or add-on */
		public int toolkitMajorVersion;
		/** The minor version name of the toolkit that declared the element or add-on */
		public int toolkitMinorVersion;
		/** The name of the element or add-on */
		public String typeName;

		/**
		 * @param toolkitName The name of the toolkit that declared the element or add-on
		 * @param toolkitMajorVersion The major version of the toolkit that declared the element or add-on
		 * @param toolkitMinorVersion The minor version name of the toolkit that declared the element or add-on
		 * @param typeName The name of the element or add-on
		 */
		public QonfigElementKey(String toolkitName, int toolkitMajorVersion, int toolkitMinorVersion, String typeName) {
			this.toolkitName = toolkitName;
			this.toolkitMinorVersion = toolkitMinorVersion;
			this.toolkitMajorVersion = toolkitMajorVersion;
			this.typeName = typeName;
		}

		/** @param traceability The traceability whose type to create a key for */
		public QonfigElementKey(SingleTypeTraceability<?, ?, ?> traceability) {
			toolkitName = traceability.getToolkitName();
			toolkitMajorVersion = traceability.getToolkitVersion().majorVersion;
			toolkitMinorVersion = traceability.getToolkitVersion().minorVersion;
			typeName = traceability.getTypeName();
		}

		/** @param type The element or add-on to create a key for */
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
