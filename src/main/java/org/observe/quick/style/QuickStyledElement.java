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
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ElementTypeTraceability;
import org.observe.expresso.qonfig.ElementTypeTraceability.SingleTypeTraceability;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExFlexibleElementModelAddOn;
import org.observe.expresso.qonfig.ExWithElementModel;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigChildGetter;
import org.observe.quick.style.QuickInterpretedStyle.QuickStyleAttributeInstantiator;
import org.observe.quick.style.QuickInterpretedStyleCache.Applications;
import org.observe.util.TypeTokens;
import org.qommons.Version;
import org.qommons.collect.CollectionUtils;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigToolkit;

import com.google.common.reflect.TypeToken;

/** A Quick element that has style */
public interface QuickStyledElement extends ExElement {
	public static final SingleTypeTraceability<QuickStyledElement, Interpreted<?>, Def<?>> STYLED_TRACEABILITY = ElementTypeTraceability
		.getElementTraceability(QuickStyleInterpretation.NAME, QuickStyleInterpretation.VERSION, "styled", Def.class, Interpreted.class,
			QuickStyledElement.class);

	/**
	 * The definition of a styled element
	 *
	 * @param <S> The type of the styled element that this definition is for
	 */
	public interface Def<S extends QuickStyledElement> extends ExElement.Def<S> {
		/** @return This element's style */
		QuickInstanceStyle.Def getStyle();

		@QonfigChildGetter("style")
		List<QuickStyleElement.Def> getStyleElements();

		ModelComponentId getParentStyleValue();

		/**
		 * An abstract {@link Def} implementation
		 *
		 * @param <S> The type of styled object that this definition is for
		 */
		public abstract class Abstract<S extends QuickStyledElement> extends ExElement.Def.Abstract<S> implements Def<S> {
			private final List<QuickStyleElement.Def> theStyleElements;
			private QuickInstanceStyle.Def theStyle;
			private ModelComponentId theParentStyleValue;

			/**
			 * @param parent The parent container definition
			 * @param type The type that this widget is interpreted from
			 */
			protected Abstract(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
				super(parent, type);
				theStyleElements = new ArrayList<>();
			}

			@Override
			public List<QuickStyleElement.Def> getStyleElements() {
				return Collections.unmodifiableList(theStyleElements);
			}

			@Override
			public QuickInstanceStyle.Def getStyle() {
				return theStyle;
			}

			@Override
			public ModelComponentId getParentStyleValue() {
				return theParentStyleValue;
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				withTraceability(STYLED_TRACEABILITY.validate(session.getFocusType(), session.reporting()));
				super.doUpdate(session);
				theParentStyleValue = getAddOn(ExWithElementModel.Def.class)
					.getElementValueModelId(InterpretedStyleApplication.PARENT_MODEL_NAME);

				ObservableModelSet.Builder builder;
				if (getExpressoEnv().getModels() instanceof ObservableModelSet.Builder)
					builder = (ObservableModelSet.Builder) getExpressoEnv().getModels();
				else
					builder = ObservableModelSet.build(getElement().getType().getName() + ".local", ObservableModelSet.JAVA_NAME_CHECKER)
					.withAll(getExpressoEnv().getModels());
				builder.withTagValue(StyleApplicationDef.STYLED_ELEMENT_TAG, getElement());
				if (builder != getExpressoEnv().getModels()) {
					setExpressoEnv(getExpressoEnv().with(builder.build()));
					session.setExpressoEnv(getExpressoEnv());
				}

				ExElement.syncDefs(QuickStyleElement.Def.class, theStyleElements, session.forChildren("style"));
				List<QuickStyleValue> declaredValues;
				if (theStyleElements.isEmpty())
					declaredValues = Collections.emptyList();
				else {
					declaredValues = new ArrayList<>();
					for (QuickStyleElement.Def styleEl : theStyleElements)
						styleEl.getStyleValues(declaredValues, StyleApplicationDef.ALL, getElement());
				}

				QuickStyleSheet styleSheet = session.get(ExWithStyleSheet.QUICK_STYLE_SHEET, QuickStyleSheet.class);
				List<QuickStyleValue> styleSheetValues;
				if (styleSheet != null) {
					styleSheetValues = new ArrayList<>();
					styleSheet.getStyleValues(styleSheetValues, getElement());
				} else
					styleSheetValues = Collections.emptyList();

				if (theStyle == null) {
					QuickTypeStyle.TypeStyleSet styleTypes = session.get(QuickStyleElement.STYLE_TYPE_SET,
						QuickTypeStyle.TypeStyleSet.class);
					if (styleTypes == null) {
						styleTypes = new QuickTypeStyle.TypeStyleSet();
						session.putGlobal(QuickStyleElement.STYLE_TYPE_SET, styleTypes);
					}
					// Initialize all of this element's types for style
					styleTypes.getOrCompile(getElement().getType(), session.reporting(), getQonfigType().getDeclarer());
					for (QonfigAddOn inh : getElement().getInheritance().getExpanded(QonfigAddOn::getInheritance))
						styleTypes.getOrCompile(inh, session.reporting(), getQonfigType().getDeclarer());

					// Find the nearest styled ancestor to inherit styles from
					ExElement.Def<?> parent = getParentElement();
					while (parent != null && !(parent instanceof QuickStyledElement.Def))
						parent = parent.getParentElement();
					QuickInstanceStyle.Def parentStyle = parent == null ? null : ((QuickStyledElement.Def<?>) parent).getStyle();
					QuickCompiledStyle rootStyle = new QuickCompiledStyle.Default(styleTypes, getElement(), parentStyle, reporting(),
						session.getFocusType().getDeclarer());

					theStyle = wrap(parentStyle, rootStyle);
				}
				theStyle.update(declaredValues, styleSheetValues);
			}

			/**
			 * Provides the element an opportunity to wrap the standard style with one specific to this element
			 *
			 * @param style The style interpreted from the {@link #getStyle() compiled style}
			 * @return The style to use for this element
			 */
			protected abstract QuickInstanceStyle.Def wrap(QuickInstanceStyle.Def parentStyle, QuickCompiledStyle style);
		}
	}

	/**
	 * An interpretation of a styled element
	 *
	 * @param <S> The type of styled element that this interpretation creates
	 */
	public interface Interpreted<S extends QuickStyledElement> extends ExElement.Interpreted<S> {
		@Override
		Def<? super S> getDefinition();

		/** @return This element's interpreted style */
		QuickInstanceStyle.Interpreted getStyle();

		List<QuickStyleElement.Interpreted<?>> getStyleElements();

		/**
		 * Populates and updates this interpretation. Must be called once after being produced by the {@link #getDefinition() definition}.
		 *
		 * @param cache The cache to use to interpret the widget
		 * @throws ExpressoInterpretationException If any models could not be interpreted from their expressions in this widget or its
		 *         content
		 */
		void updateElement(InterpretedExpressoEnv env) throws ExpressoInterpretationException;

		/**
		 * An abstract {@link Interpreted} implementation
		 *
		 * @param <S> The type of widget that this interpretation is for
		 */
		public abstract class Abstract<S extends QuickStyledElement> extends ExElement.Interpreted.Abstract<S> implements Interpreted<S> {
			private QuickInstanceStyle.Interpreted theStyle;
			private final List<QuickStyleElement.Interpreted<?>> theStyleElements;

			/**
			 * @param definition The definition producing this interpretation
			 * @param parent The interpreted parent
			 */
			protected Abstract(Def<? super S> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
				theStyleElements = new ArrayList<>();
			}

			@Override
			public Def<? super S> getDefinition() {
				return (Def<? super S>) super.getDefinition();
			}

			@Override
			public QuickInstanceStyle.Interpreted getStyle() {
				return theStyle;
			}

			@Override
			public List<QuickStyleElement.Interpreted<?>> getStyleElements() {
				return Collections.unmodifiableList(theStyleElements);
			}

			@Override
			public void updateElement(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				update(env);
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);

				if (theStyle == null) {
					ExElement.Interpreted<?> parent = getParentElement();
					while (parent != null && !(parent instanceof QuickStyledElement.Interpreted))
						parent = parent.getParentElement();
					theStyle = getDefinition().getStyle().interpret(this,
						parent == null ? null : ((QuickStyledElement.Interpreted<?>) parent).getStyle(), getExpressoEnv());
				}
				theStyle.update(getExpressoEnv(), new QuickInterpretedStyleCache.Applications());

				CollectionUtils
				.synchronize(theStyleElements, getDefinition().getStyleElements(), (i, d) -> i.getIdentity() == d.getIdentity())
				.<ExpressoInterpretationException> simpleE(d -> d.interpret(this))//
				.onLeftX(el -> el.getLeftValue().destroy())//
				.onRightX(el -> el.getLeftValue().updateStyle(getExpressoEnv()))//
				.onCommonX(el -> el.getLeftValue().updateStyle(getExpressoEnv()))//
				.adjust();
			}
		}
	}

	QuickInstanceStyle getStyle();

	List<QuickStyleElement<?>> getStyleElements();

	/** An abstract {@link QuickStyledElement} implementation */
	public abstract class Abstract extends ExElement.Abstract implements QuickStyledElement {
		private QuickInstanceStyle theStyle;
		private final List<QuickStyleElement<?>> theStyleElements;
		private ModelComponentId theParentStyleValue;

		protected Abstract(Object id) {
			super(id);
			theStyleElements = new ArrayList<>();
		}

		@Override
		public QuickInstanceStyle getStyle() {
			return theStyle;
		}

		@Override
		public List<QuickStyleElement<?>> getStyleElements() {
			return Collections.unmodifiableList(theStyleElements);
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) {
			super.doUpdate(interpreted);

			QuickStyledElement.Interpreted<?> myInterpreted = (QuickStyledElement.Interpreted<?>) interpreted;
			theParentStyleValue = myInterpreted.getDefinition().getParentStyleValue();

			ExElement parent = getParentElement();
			while (parent != null && !(parent instanceof QuickStyledElement.Abstract))
				parent = parent.getParentElement();
			if (theStyle == null)
				theStyle = myInterpreted.getStyle().create(this);
			theStyle.update(myInterpreted.getStyle(), (QuickStyledElement) parent);

			CollectionUtils
			.synchronize(theStyleElements, myInterpreted.getStyleElements(),
				(inst, interp) -> inst.getIdentity() == interp.getIdentity())//
			.simple(interp -> interp.create()).onRight(el -> el.getLeftValue().update(el.getRightValue(), this))//
			.onCommon(el -> el.getLeftValue().update(el.getRightValue(), this))//
			.adjust();
		}

		@Override
		public void instantiated() {
			super.instantiated();

			theStyle.instantiated();

			for (QuickStyleElement<?> styleEl : theStyleElements)
				styleEl.instantiated();
		}

		@Override
		protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
			super.doInstantiate(myModels);

			ExElement parent = getParentElement();
			while (parent != null && !(parent instanceof QuickStyledElement.Abstract))
				parent = parent.getParentElement();
			ModelSetInstance parentModels = parent == null ? null : ((QuickStyledElement.Abstract) parent).getUpdatingModels();
			ExFlexibleElementModelAddOn.satisfyElementValue(theParentStyleValue, getUpdatingModels(),
				SettableValue.of(ModelSetInstance.class, parentModels, "Not settable"),
				ExFlexibleElementModelAddOn.ActionIfSatisfied.Replace);

			theStyle.instantiate(myModels);

			for (QuickStyleElement<?> styleEl : theStyleElements)
				styleEl.instantiate(myModels);
		}

		@Override
		public QuickStyledElement.Abstract copy(ExElement parent) {
			QuickStyledElement.Abstract copy = (QuickStyledElement.Abstract) super.copy(parent);

			copy.theStyle = theStyle.copy(copy);

			return copy;
		}
	}

	public interface QuickInstanceStyle {
		public interface Def extends QuickCompiledStyle {
			QuickStyledElement.Def<?> getStyledElement();

			@Override
			Interpreted interpret(ExElement.Interpreted<?> parentEl, QuickInterpretedStyle parent, InterpretedExpressoEnv env)
				throws ExpressoInterpretationException;

			Set<QuickStyleAttributeDef> getApplicableAttributes();

			public static abstract class Abstract extends QuickCompiledStyle.Wrapper implements Def {
				private final QuickStyledElement.Def<?> theStyledElement;
				private final Set<QuickStyleAttributeDef> theApplicableAttributes;

				protected Abstract(Def parent, QuickStyledElement.Def<?> styledElement, QuickCompiledStyle wrapped) {
					super(parent, wrapped);
					theStyledElement = styledElement;
					theApplicableAttributes = new LinkedHashSet<>();
				}

				@Override
				public Def getParent() {
					return (Def) super.getParent();
				}

				@Override
				public QuickStyledElement.Def<?> getStyledElement() {
					return theStyledElement;
				}

				@Override
				public Set<QuickStyleAttributeDef> getApplicableAttributes() {
					return Collections.unmodifiableSet(theApplicableAttributes);
				}

				protected QuickStyleAttributeDef addApplicableAttribute(QuickStyleAttributeDef attr) {
					theApplicableAttributes.add(attr);
					return attr;
				}

				@Override
				public abstract Interpreted interpret(ExElement.Interpreted<?> parentEl, QuickInterpretedStyle parent,
					InterpretedExpressoEnv env) throws ExpressoInterpretationException;
			}
		}

		public interface Interpreted extends QuickInterpretedStyle {
			@Override
			Def getDefinition();

			QuickStyledElement.Interpreted<?> getStyledElement();

			Map<QuickStyleAttributeDef, QuickStyleAttribute<?>> getApplicableAttributes();

			QuickInstanceStyle create(QuickStyledElement parent);

			public static abstract class Abstract extends QuickInterpretedStyle.Wrapper implements Interpreted {
				private final Def theDefinition;
				private final QuickStyledElement.Interpreted<?> theStyledElement;
				private final Map<QuickStyleAttributeDef, QuickStyleAttribute<?>> theApplicableAttributes;

				protected Abstract(Def definition, QuickStyledElement.Interpreted<?> styledElement, QuickInstanceStyle.Interpreted parent,
					QuickInterpretedStyle wrapped) {
					super(parent, wrapped);
					theDefinition = definition;
					theStyledElement = styledElement;
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
				public QuickStyledElement.Interpreted<?> getStyledElement() {
					return theStyledElement;
				}

				@Override
				public Map<QuickStyleAttributeDef, QuickStyleAttribute<?>> getApplicableAttributes() {
					return Collections.unmodifiableMap(theApplicableAttributes);
				}

				@Override
				public void update(InterpretedExpressoEnv env, Applications appCache) throws ExpressoInterpretationException {
					super.update(env, appCache);
					theApplicableAttributes.clear();
					QuickInterpretedStyleCache cache = QuickInterpretedStyleCache.get(env);
					for (QuickStyleAttributeDef attr : getDefinition().getApplicableAttributes())
						theApplicableAttributes.put(attr, cache.getAttribute(attr, env));
				}
			}
		}

		Set<QuickStyleAttribute<?>> getApplicableAttributes();

		<T> ObservableValue<T> getApplicableAttribute(QuickStyleAttribute<T> attribute);

		Observable<ObservableValueEvent<?>> changes();

		void update(Interpreted interpreted, QuickStyledElement styledElement);

		void instantiated();

		void instantiate(ModelSetInstance models) throws ModelInstantiationException;

		public QuickInstanceStyle copy(QuickStyledElement styledElement);

		public abstract class Abstract implements QuickInstanceStyle, Cloneable {
			private QuickStyledElement theStyledElement;
			private Map<QuickStyleAttribute<?>, StyleAttributeData<?>> theApplicableAttributes;
			private SettableValue<Observable<ObservableValueEvent<?>>> theChanges;
			private Observable<ObservableValueEvent<?>> theFlatChanges;

			protected Abstract() {
				theApplicableAttributes = new LinkedHashMap<>();
				theChanges = SettableValue.build((Class<Observable<ObservableValueEvent<?>>>) (Class<?>) Observable.class).build();
				theFlatChanges = ObservableValue.flattenObservableValue(theChanges);
			}

			public QuickStyledElement getStyledElement() {
				return theStyledElement;
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
			public void update(Interpreted interpreted, QuickStyledElement styledElement) {
				theStyledElement = styledElement;
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

			private <T> void initAttribute(QuickStyleAttribute<T> attr, Interpreted interpreted, boolean[] different) {
				QuickStyleAttributeInstantiator<T> instantiator = interpreted.get(attr)
					.instantiate(interpreted.getStyledElement().getModels());
				theApplicableAttributes.computeIfAbsent(attr, __ -> {
					different[0] = true;
					return new StyleAttributeData<>(instantiator);
				});
			}

			@Override
			public void instantiated() {
				for (StyleAttributeData<?> attr : theApplicableAttributes.values())
					attr.theInstantiator.instantiate();
			}

			@Override
			public void instantiate(ModelSetInstance models) throws ModelInstantiationException {
				for (StyleAttributeData<?> attr : theApplicableAttributes.values())
					attr.update(models);
			}

			@Override
			public Abstract copy(QuickStyledElement styledElement) {
				Abstract copy = clone();
				copy.theStyledElement = styledElement;
				copy.theApplicableAttributes = new LinkedHashMap<>();
				for (Map.Entry<QuickStyleAttribute<?>, StyleAttributeData<?>> attr : theApplicableAttributes.entrySet())
					copy.theApplicableAttributes.put(attr.getKey(), new StyleAttributeData<>(attr.getValue().theInstantiator));
				copy.theChanges = SettableValue.build((Class<Observable<ObservableValueEvent<?>>>) (Class<?>) Observable.class).build();
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
				StringBuilder str = new StringBuilder(theStyledElement.getTypeName()).append(".style{");
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
					TypeToken<T> type = instantiator.getAttribute().getType();
					theValueContainer = SettableValue
						.build(TypeTokens.get().keyFor(ObservableValue.class).<ObservableValue<T>> parameterized(type)).build();
					flatValue = ObservableValue.flatten(theValueContainer);
				}

				void update(QuickStyleAttributeInstantiator<T> instantiator, ModelSetInstance models) throws ModelInstantiationException {
					theInstantiator = instantiator;
					update(models);
				}

				void update(ModelSetInstance models) throws ModelInstantiationException {
					theValueContainer.set(theInstantiator.evaluate(models), null);
				}
			}
		}
	}

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
