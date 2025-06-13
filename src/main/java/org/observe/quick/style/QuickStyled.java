package org.observe.quick.style;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.SettableValue;
import org.observe.expresso.CompiledExpressoEnv;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ExAddOn;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExModelAugmentation;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigChildGetter;
import org.observe.quick.style.QuickInterpretedStyle.QuickStyleAttributeInstantiator;
import org.qommons.Version;
import org.qommons.collect.CollectionUtils;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigToolkit;

/** An add-on for a quick element that may have an icon */
public class QuickStyled extends ExAddOn.Abstract<ExElement> {
	/** The XML name of this type */
	public static final String STYLED = "styled";

	/** The definition to create an {@link QuickStyled} element */
	@ExElementTraceable(toolkit = QuickStyleInterpretation.STYLE,
		qonfigType = STYLED,
		interpretation = Interpreted.class,
		instance = QuickStyled.class)
	public static class Def extends ExAddOn.Def.Abstract<ExElement, QuickStyled> {
		private final List<QuickStyleElement.Def> theStyleElements;
		private QuickInstanceStyle.Def theStyle;

		/**
		 * @param type The Qonfig type of this element
		 * @param element The Qonfig element to interpret
		 */
		public Def(QonfigAddOn type, ExElement.Def<? extends ExElement> element) {
			super(type, element);
			theStyleElements = new ArrayList<>();
		}

		@Override
		public Set<? extends Class<? extends ExAddOn.Def<?, ?>>> getDependencies() {
			return (Set<Class<ExAddOn.Def<?, ?>>>) (Set<?>) Collections.singleton(ExModelAugmentation.Def.class);
		}

		/** @return This element's style */
		public QuickInstanceStyle.Def getStyle() {
			return theStyle;
		}

		/** @return Style elements declared on this element */
		@QonfigChildGetter("style")
		public List<QuickStyleElement.Def> getStyleElements() {
			return Collections.unmodifiableList(theStyleElements);
		}

		@Override
		public void update(ExpressoQIS session, ExElement.Def<? extends ExElement> element) throws QonfigInterpretationException {
			super.update(session, element);

			String doc = element.getDocument();
			CompiledExpressoEnv defaultEnv = element.getExpressoEnv(doc);
			ObservableModelSet.Builder builder;
			if (defaultEnv.getModels() instanceof ObservableModelSet.Builder)
				builder = (ObservableModelSet.Builder) defaultEnv.getModels();
			else
				builder = ObservableModelSet
				.build(element.getElement().getType().getName() + ".local", ObservableModelSet.JAVA_NAME_CHECKER)
				.withAll(defaultEnv.getModels());
			builder.withTagValue(StyleApplicationDef.STYLED_ELEMENT_TAG, element.getElement());
			if (builder != defaultEnv.getModels()) {
				defaultEnv = defaultEnv.with(builder.build());
				element.setExpressoEnv(doc, defaultEnv);
				session.setExpressoEnv(doc, defaultEnv);
			}

			element.syncChildren(QuickStyleElement.Def.class, theStyleElements, session.forChildren("style"));
			List<QuickStyleValue> declaredValues;
			List<QuickStyleValue> styleSheetValues;
			if (theStyleElements.isEmpty())
				declaredValues = Collections.emptyList();
			else {
				declaredValues = new ArrayList<>();
				for (QuickStyleElement.Def styleEl : theStyleElements)
					styleEl.getStyleValues(declaredValues, StyleApplicationDef.ALL, element, defaultEnv, null);
			}

			QuickStyleSheet styleSheet = session.get(ExWithStyleSheet.QUICK_STYLE_SHEET, QuickStyleSheet.class);
			if (styleSheet != null) {
				styleSheetValues = new ArrayList<>();
				styleSheet.getStyleValues(styleSheetValues, element, defaultEnv);
			} else
				styleSheetValues = Collections.emptyList();

			if (theStyle == null) {
				QuickTypeStyle.TypeStyleSet styleTypes = session.get(QuickStyleElement.STYLE_TYPE_SET, QuickTypeStyle.TypeStyleSet.class);
				if (styleTypes == null) {
					styleTypes = new QuickTypeStyle.TypeStyleSet();
					session.putGlobal(QuickStyleElement.STYLE_TYPE_SET, styleTypes);
				}
				// Initialize all of this element's types for style
				QonfigToolkit styleTK = getType().getDeclarer();
				styleTypes.getOrCompile(element.getElement().getType(), session.reporting(), styleTK);
				for (QonfigAddOn inh : element.getElement().getInheritance().getExpanded(QonfigAddOn::getInheritance))
					styleTypes.getOrCompile(inh, session.reporting(), styleTK);

				// Find the nearest styled ancestor to inherit styles from
				ExElement.Def<?> parent = element.getParentElement();
				if (parent == null)
					parent = element.getPromise();
				while (parent != null && parent.getAddOn(QuickStyled.Def.class) == null) {
					ExElement.Def<?> p = parent.getParentElement();
					if (p == null)
						p = parent.getPromise();
					parent = p;
				}
				QuickInstanceStyle.Def parentStyle = parent == null ? null
					: parent.getAddOnValue(QuickStyled.Def.class, QuickStyled.Def::getStyle);
				QuickCompiledStyle rootStyle = new QuickCompiledStyle.Default(styleTypes, element.getElement(), parentStyle,
					element.reporting(), styleTK);

				if (element instanceof QuickStyledElement.Def)
					theStyle = ((QuickStyledElement.Def<?>) element).wrap(parentStyle, rootStyle);
				else
					theStyle = new QuickInstanceStyle.Def.Base(parentStyle, this, rootStyle);
			}
			theStyle.update(declaredValues, styleSheetValues, defaultEnv);
		}

		@Override
		public <E2 extends ExElement> Interpreted interpret(ExElement.Interpreted<E2> element) {
			return new Interpreted(this, element);
		}
	}

	/** The interpretation to create an {@link QuickStyled} element */
	public static class Interpreted extends ExAddOn.Interpreted.Abstract<ExElement, QuickStyled> {
		private QuickInstanceStyle.Interpreted theStyle;
		private final List<QuickStyleElement.Interpreted<?>> theStyleElements;
		private QuickStyleSheet.Interpreted theStyleSheet;

		Interpreted(Def definition, ExElement.Interpreted<? extends ExElement> element) {
			super(definition, element);
			theStyleElements = new ArrayList<>();
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		/** @return This element's interpreted style */
		public QuickInstanceStyle.Interpreted getStyle() {
			return theStyle;
		}

		/** @return Style elements declared on this element */
		public List<QuickStyleElement.Interpreted<?>> getStyleElements() {
			return Collections.unmodifiableList(theStyleElements);
		}

		@Override
		public Class<QuickStyled> getInstanceType() {
			return QuickStyled.class;
		}

		@Override
		public void update(ExElement.Interpreted<? extends ExElement> element) throws ExpressoInterpretationException {
			super.update(element);

			if (theStyle == null) {
				ExElement.Interpreted<?> parent = element.getParentElement();
				QuickStyled.Interpreted parentStyled = null;
				while (parent != null && (parentStyled = parent.getAddOn(QuickStyled.Interpreted.class)) == null)
					parent = parent.getParentElement();
				theStyle = getDefinition().getStyle().interpret(element,
					parentStyled == null ? null : parentStyled.getStyle());
			}
			theStyleSheet = null;
			ExElement.Interpreted<?> parent = element.getParentElement();
			while (parent != null && theStyleSheet == null) {
				if (parent instanceof WithStyleSheet.Interpreted)
					theStyleSheet = ((WithStyleSheet.Interpreted<?>) parent).getStyleSheet();
				if (theStyleSheet == null)
					theStyleSheet = parent.getAddOnValue(ExWithStyleSheet.Interpreted.class, ss -> ss.getStyleSheet());
				parent = parent.getParentElement();
			}

			element.syncChildren(getDefinition().getStyleElements(), theStyleElements, def -> def.interpret(element),
				QuickStyleElement.Interpreted::updateStyle);

			theStyle.update(element, theStyleSheet);
		}

		@Override
		public QuickStyled create(ExElement element) {
			return new QuickStyled(element);
		}
	}

	private QuickInstanceStyle theStyle;
	private final List<QuickStyleElement<?>> theStyleElements;

	QuickStyled(ExElement element) {
		super(element);
		theStyleElements = new ArrayList<>();
	}

	@Override
	public Class<Interpreted> getInterpretationType() {
		return Interpreted.class;
	}

	/** @return This element's style */
	public QuickInstanceStyle getStyle() {
		return theStyle;
	}

	/** @return Style elements declared on this element */
	public List<QuickStyleElement<?>> getStyleElements() {
		return Collections.unmodifiableList(theStyleElements);
	}

	@Override
	public void update(ExAddOn.Interpreted<? super ExElement, ?> interpreted, ExElement element) throws ModelInstantiationException {
		super.update(interpreted, element);

		QuickStyled.Interpreted myInterpreted = (QuickStyled.Interpreted) interpreted;

		ExElement parent = element.getParentElement();
		while (parent != null && parent.getAddOn(QuickStyled.class) != null)
			parent = parent.getParentElement();
		if (theStyle == null)
			theStyle = myInterpreted.getStyle().create(this);
		theStyle.update(myInterpreted.getStyle(), parent == null ? null : parent.getAddOn(QuickStyled.class));

		CollectionUtils
		.synchronize(theStyleElements, myInterpreted.getStyleElements(), (inst, interp) -> inst.getIdentity() == interp.getIdentity())//
		.<ModelInstantiationException> simpleX(interp -> interp.create())//
		.onRightX(el -> el.getLeftValue().update(el.getRightValue(), element))//
		.onCommonX(el -> el.getLeftValue().update(el.getRightValue(), element))//
		.adjust();
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();

		theStyle.instantiated();

		for (QuickStyleElement<?> styleEl : theStyleElements)
			styleEl.instantiated();
	}

	@Override
	public ModelSetInstance instantiate(ModelSetInstance models) throws ModelInstantiationException {
		models = super.instantiate(models);

		theStyle.instantiate(models);

		for (QuickStyleElement<?> styleEl : theStyleElements)
			styleEl.instantiate(models);
		return models;
	}

	@Override
	public QuickStyled copy(ExElement element) {
		QuickStyled copy = (QuickStyled) super.copy(element);

		copy.theStyle = theStyle.copy(copy);

		return copy;
	}

	/** Structure containing style information for a {@link QuickStyled} add-on */
	public interface QuickInstanceStyle {
		/** Definition for a {@link QuickInstanceStyle} */
		public interface Def extends QuickCompiledStyle {
			/** @return The element this style is for */
			QuickStyled.Def getStyled();

			@Override
			Interpreted interpret(ExElement.Interpreted<?> parentEl, QuickInterpretedStyle parent) throws ExpressoInterpretationException;

			/** @return All style attributes that apply to this style's element */
			Set<QuickStyleAttributeDef> getApplicableAttributes();

			/** Abstract {@link QuickInstanceStyle} definition implementation */
			public static abstract class Abstract extends QuickCompiledStyle.Wrapper implements Def {
				private final QuickStyled.Def theStyled;
				private final Set<QuickStyleAttributeDef> theApplicableAttributes;

				/**
				 * @param parent The parent style to inherit from
				 * @param styled The styled add-on of the element that this style is for
				 * @param wrapped The compiled style to wrap
				 */
				protected Abstract(Def parent, QuickStyled.Def styled, QuickCompiledStyle wrapped) {
					super(parent, wrapped);
					theStyled = styled;
					theApplicableAttributes = new LinkedHashSet<>();

					for (ExAddOn.Def<?, ?> addOn : styled.getElement().getAddOns()) {
						if (addOn instanceof QuickStyledAddOn) {
							QuickTypeStyle type = getWrapped().getStyleTypes().get(addOn.getType());
							((QuickStyledAddOn<?, ?>) addOn).addStyleAttributes(type, this::addApplicableAttribute);
						}
					}
				}

				@Override
				public Def getParent() {
					return (Def) super.getParent();
				}

				@Override
				public QuickStyled.Def getStyled() {
					return theStyled;
				}

				@Override
				public Set<QuickStyleAttributeDef> getApplicableAttributes() {
					return Collections.unmodifiableSet(theApplicableAttributes);
				}

				/**
				 * @param attr The attribute to add
				 * @return This definition
				 */
				protected QuickStyleAttributeDef addApplicableAttribute(QuickStyleAttributeDef attr) {
					theApplicableAttributes.add(attr);
					return attr;
				}

				@Override
				public abstract Interpreted interpret(ExElement.Interpreted<?> parentEl, QuickInterpretedStyle parent)
					throws ExpressoInterpretationException;
			}

			/** Basic Quick style definition for when the element is not a {@link QuickStyledElement} instance */
			public static class Base extends Abstract {
				/**
				 * @param parent The parent style to inherit from
				 * @param styled The styled add-on of the element that this style is for
				 * @param wrapped The compiled style to wrap
				 */
				public Base(Def parent, QuickStyled.Def styled, QuickCompiledStyle wrapped) {
					super(parent, styled, wrapped);
				}

				@Override
				public Interpreted interpret(ExElement.Interpreted<?> parentEl, QuickInterpretedStyle parent)
					throws ExpressoInterpretationException {
					return new Interpreted.Base(this, parentEl.getAddOn(QuickStyled.Interpreted.class),
						(QuickInstanceStyle.Interpreted) parent, getWrapped().interpret(parentEl, parent));
				}
			}
		}

		/** Interpretation for a {@link QuickInstanceStyle} */
		public interface Interpreted extends QuickInterpretedStyle {
			@Override
			Def getDefinition();

			/** @return The styled add-on that this style is for */
			QuickStyled.Interpreted getStyled();

			/** @return All style attributes that apply to this style's element, by definition */
			Map<QuickStyleAttributeDef, QuickStyleAttribute<?>> getApplicableAttributes();

			/**
			 * @param parent The styled add-on on the element that the style instance is for
			 * @return The style instance
			 */
			QuickInstanceStyle create(QuickStyled parent);

			/** Abstract {@link QuickInstanceStyle} definition implementation */
			public static abstract class Abstract extends QuickInterpretedStyle.Wrapper implements Interpreted {
				private final Def theDefinition;
				private final QuickStyled.Interpreted theStyled;
				private final Map<QuickStyleAttributeDef, QuickStyleAttribute<?>> theApplicableAttributes;

				/**
				 * @param definition The definition to interpret
				 * @param styled The styled add-on of the element that this style is for
				 * @param parent The parent style to inherit from
				 * @param wrapped The interpreted style to wrap
				 */
				protected Abstract(Def definition, QuickStyled.Interpreted styled, QuickInstanceStyle.Interpreted parent,
					QuickInterpretedStyle wrapped) {
					super(parent, wrapped);
					theDefinition = definition;
					theStyled = styled;
					theApplicableAttributes = new LinkedHashMap<>();
				}

				@Override
				public Def getDefinition() {
					return theDefinition;
				}

				@Override
				public Interpreted getParent() {
					return (Interpreted) super.getParent();
				}

				@Override
				public QuickStyled.Interpreted getStyled() {
					return theStyled;
				}

				@Override
				public Map<QuickStyleAttributeDef, QuickStyleAttribute<?>> getApplicableAttributes() {
					return Collections.unmodifiableMap(theApplicableAttributes);
				}

				@Override
				public void update(ExElement.Interpreted<?> element, QuickStyleSheet.Interpreted styleSheet)
					throws ExpressoInterpretationException {
					super.update(element, styleSheet);
					theApplicableAttributes.clear();
					InterpretedExpressoEnv defaultEnv = element.getDefaultEnv();
					QuickInterpretedStyleCache cache = QuickInterpretedStyleCache.get(defaultEnv);
					for (QuickStyleAttributeDef attr : getDefinition().getApplicableAttributes())
						theApplicableAttributes.put(attr, cache.getAttribute(attr, defaultEnv));
				}
			}

			/** Basic Quick style interpretation for when the element is not a {@link QuickStyledElement} instance */
			public static class Base extends Abstract {
				/**
				 * @param definition The definition to interpret
				 * @param styled The styled add-on of the element that this style is for
				 * @param parent The parent style to inherit from
				 * @param wrapped The interpreted style to wrap
				 */
				public Base(Def definition, QuickStyled.Interpreted styled, Interpreted parent, QuickInterpretedStyle wrapped) {
					super(definition, styled, parent, wrapped);
				}

				@Override
				public QuickInstanceStyle create(QuickStyled parent) {
					return new QuickInstanceStyle.Base();
				}
			}
		}

		/** @return The styled add-on that this style is for */
		QuickStyled getStyled();

		/** @return All style attributes that apply to this style's element */
		Set<QuickStyleAttribute<?>> getApplicableAttributes();

		/**
		 * @param <T> The type of the attribute
		 * @param attribute The style attribute
		 * @return The value of the style attribute in this style
		 */
		<T> ObservableValue<T> getApplicableAttribute(QuickStyleAttribute<T> attribute);

		/** @return An observable that fires whenever the value of any style applicable to this style's element changes */
		Observable<ObservableValueEvent<?>> changes();

		/**
		 * @param interpreted The interpretation of this style
		 * @param styled The element this style is for
		 * @throws ModelInstantiationException If any model values fail to initialize
		 */
		void update(Interpreted interpreted, QuickStyled styled) throws ModelInstantiationException;

		/**
		 * Instantiates all model values. Must be called once after creation.
		 *
		 * @throws ModelInstantiationException If any model values fail to initialize
		 */
		void instantiated() throws ModelInstantiationException;

		/**
		 * @param models The model instance to instantiate with
		 * @throws ModelInstantiationException If this style could not instantiate its data
		 */
		void instantiate(ModelSetInstance models) throws ModelInstantiationException;

		/**
		 * @param styledElement The element copy that the style copy is for
		 * @return A copy of this style
		 */
		public QuickInstanceStyle copy(QuickStyled styledElement);

		/** Abstract {@link QuickInstanceStyle} implementation */
		public abstract class Abstract implements QuickInstanceStyle, Cloneable {
			private QuickStyled theStyled;
			private Map<QuickStyleAttribute<?>, StyleAttributeData<?>> theApplicableAttributes;
			private SettableValue<Observable<ObservableValueEvent<?>>> theChanges;
			private Observable<ObservableValueEvent<?>> theFlatChanges;

			/** Creates the style */
			protected Abstract() {
				theApplicableAttributes = new LinkedHashMap<>();
				theChanges = SettableValue.<Observable<ObservableValueEvent<?>>> build().build();
				theFlatChanges = ObservableValue.flattenObservableValue(theChanges);
			}

			@Override
			public QuickStyled getStyled() {
				return theStyled;
			}

			@Override
			public Set<QuickStyleAttribute<?>> getApplicableAttributes() {
				return Collections.unmodifiableSet(theApplicableAttributes.keySet());
			}

			@Override
			public <T> ObservableValue<T> getApplicableAttribute(QuickStyleAttribute<T> attribute) {
				StyleAttributeData<T> attr = (StyleAttributeData<T>) theApplicableAttributes.get(attribute);
				if (attr == null)
					throw new IllegalArgumentException(
						"Attribute " + attribute + " is not advertised as applicable to " + getClass().getName());
				return attr.flatValue;
			}

			@Override
			public Observable<ObservableValueEvent<?>> changes() {
				return theFlatChanges;
			}

			@Override
			public void update(Interpreted interpreted, QuickStyled styled) throws ModelInstantiationException {
				theStyled = styled;
				boolean[] different = new boolean[1];
				different[0] = theApplicableAttributes.keySet().retainAll(interpreted.getApplicableAttributes().values());
				for (QuickStyleAttribute<?> attr : interpreted.getApplicableAttributes().values())
					initAttribute(attr, interpreted, different);

				if (different[0])
					initChanges();
			}

			private void initChanges() {
				Observable<? extends ObservableValueEvent<?>>[] changes = new Observable[theApplicableAttributes.size()];
				int i = 0;
				for (StyleAttributeData<?> attr : theApplicableAttributes.values())
					changes[i++] = attr.flatValue.noInitChanges();
				theChanges.set(Observable.or(changes), null);
			}

			private <T> void initAttribute(QuickStyleAttribute<T> attr, Interpreted interpreted, boolean[] different)
				throws ModelInstantiationException {
				QuickStyleAttributeInstantiator<T> instantiator = interpreted.get(attr).instantiate();
				theApplicableAttributes.computeIfAbsent(attr, __ -> {
					different[0] = true;
					return new StyleAttributeData<>(instantiator);
				});
			}

			@Override
			public void instantiated() throws ModelInstantiationException {
				for (StyleAttributeData<?> attr : theApplicableAttributes.values())
					attr.theInstantiator.instantiate();
			}

			@Override
			public void instantiate(ModelSetInstance models) throws ModelInstantiationException {
				for (StyleAttributeData<?> attr : theApplicableAttributes.values())
					attr.update(models);
			}

			@Override
			public Abstract copy(QuickStyled styled) {
				Abstract copy = clone();
				copy.theStyled = styled;
				copy.theApplicableAttributes = new LinkedHashMap<>();
				for (Map.Entry<QuickStyleAttribute<?>, StyleAttributeData<?>> attr : theApplicableAttributes.entrySet())
					copy.theApplicableAttributes.put(attr.getKey(), new StyleAttributeData<>(attr.getValue().theInstantiator));
				copy.theChanges = SettableValue.<Observable<ObservableValueEvent<?>>> build().build();
				copy.theFlatChanges = ObservableValue.flattenObservableValue(copy.theChanges);
				copy.initChanges();
				return copy;
			}

			@Override
			protected Abstract clone() {
				try {
					return (Abstract) super.clone();
				} catch (CloneNotSupportedException e) {
					throw new IllegalStateException("Not cloneable?", e);
				}
			}

			@Override
			public String toString() {
				StringBuilder str = new StringBuilder(theStyled.getElement().toString()).append(".style{");
				boolean any = false;
				for (Map.Entry<QuickStyleAttribute<?>, StyleAttributeData<?>> attr : theApplicableAttributes.entrySet()) {
					Object value = attr.getValue().flatValue.get();
					if (value != null) {
						str.append('\n').append(attr.getKey().getName()).append('=').append(value);
						any = true;
					}
				}
				if (any)
					str.append('\n');
				str.append('}');
				return str.toString();
			}

			static class StyleAttributeData<T> {
				QuickStyleAttributeInstantiator<T> theInstantiator;
				private final SettableValue<ObservableValue<T>> theValueContainer;
				final ObservableValue<T> flatValue;

				StyleAttributeData(QuickStyleAttributeInstantiator<T> instantiator) {
					theInstantiator = instantiator;
					theValueContainer = SettableValue.<ObservableValue<T>> build().build();
					flatValue = ObservableValue.flatten(theValueContainer);
				}

				void update(QuickStyleAttributeInstantiator<T> instantiator, ModelSetInstance models) throws ModelInstantiationException {
					theInstantiator = instantiator;
					update(models);
				}

				void update(ModelSetInstance models) throws ModelInstantiationException {
					theValueContainer.set(theInstantiator.evaluate(models), null);
				}

				@Override
				public String toString() {
					return theInstantiator.getAttribute().toString();
				}
			}
		}

		/** Basic Quick style instance for when the element is not a {@link QuickStyledElement} instance */
		public static class Base extends Abstract {
		}
	}

	/**
	 * @param styleSet The type set to get the style from
	 * @param element The element to get the style for
	 * @param toolkitName The name of the toolkit declaring the type to get the style for
	 * @param toolkitVersion The version of the toolkit declaring the type to get the style for
	 * @param elementName The name of the element to get style for
	 * @return Style information for the given type
	 */
	public static QuickTypeStyle getTypeStyle(QuickTypeStyle.TypeStyleSet styleSet, QonfigElement element, String toolkitName,
		Version toolkitVersion, String elementName) {
		QonfigToolkit.ToolkitDefVersion tdv = new QonfigToolkit.ToolkitDefVersion(toolkitVersion.major, toolkitVersion.minor);
		QonfigToolkit toolkit;
		if (element.getType().getDeclarer().getName().equals(toolkitName)//
			&& element.getType().getDeclarer().getMajorVersion() == tdv.majorVersion
			&& element.getType().getDeclarer().getMinorVersion() == tdv.minorVersion)
			toolkit = element.getType().getDeclarer();
		else
			toolkit = element.getType().getDeclarer().getDependenciesByDefinition()
			.getOrDefault(toolkitName, Collections.emptyNavigableMap()).get(tdv);
		QonfigElementOrAddOn type;
		if (toolkit == null) {
			for (QonfigAddOn inh : element.getInheritance().values()) {
				toolkit = inh.getDeclarer().getDependenciesByDefinition().getOrDefault(toolkitName, Collections.emptyNavigableMap())
					.get(tdv);
				if (toolkit != null)
					break;
			}
		}
		if (toolkit == null)
			throw new IllegalArgumentException(
				"No such toolkit " + toolkitName + " " + toolkitVersion + " found in type information of element " + element);
		type = toolkit.getElementOrAddOn(elementName);
		return styleSet.get(type); // Should be available
	}
}
