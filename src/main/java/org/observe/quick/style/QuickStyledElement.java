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
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ElementTypeTraceability;
import org.observe.expresso.qonfig.ElementTypeTraceability.SingleTypeTraceability;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExWithElementModel;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigChildGetter;
import org.observe.quick.style.QuickInterpretedStyleCache.Applications;
import org.observe.util.TypeTokens;
import org.qommons.Version;
import org.qommons.collect.CollectionUtils;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigToolkit;

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

		/**
		 * An abstract {@link Def} implementation
		 *
		 * @param <S> The type of styled object that this definition is for
		 */
		public abstract class Abstract<S extends QuickStyledElement> extends ExElement.Def.Abstract<S> implements Def<S> {
			private final List<QuickStyleElement.Def> theStyleElements;
			private QuickInstanceStyle.Def theStyle;

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
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				withTraceability(STYLED_TRACEABILITY.validate(session.getFocusType(), session.reporting()));
				super.doUpdate(session);

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

				if (theStyle == null || theStyle.getId() != getDefinition().getStyle().getId()) {
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

		/**
		 * @param interpreted The interpretation that is creating this element
		 * @param parent The parent element
		 */
		protected Abstract(QuickStyledElement.Interpreted<?> interpreted, ExElement parent) {
			super(interpreted, parent);
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
		protected void updateModel(ExElement.Interpreted<?> interpreted, ModelSetInstance myModels) throws ModelInstantiationException {
			super.updateModel(interpreted, myModels);
			QuickStyledElement.Interpreted<?> myInterpreted = (QuickStyledElement.Interpreted<?>) interpreted;

			ExElement parent = getParentElement();
			while (parent != null && !(parent instanceof QuickStyledElement.Abstract))
				parent = parent.getParentElement();
			ModelSetInstance parentModels = parent == null ? null : ((QuickStyledElement.Abstract) parent).getUpdatingModels();
			getAddOn(ExWithElementModel.class).satisfyElementValue(InterpretedStyleApplication.PARENT_MODEL_NAME,
				SettableValue.of(ModelSetInstance.class, parentModels, "Not settable"), ExWithElementModel.ActionIfSatisfied.Replace);
			if (theStyle == null || theStyle.getId() != myInterpreted.getStyle().getId())
				theStyle = myInterpreted.getStyle().create(this);
			theStyle.update(myInterpreted.getStyle(), myModels);

			CollectionUtils
			.synchronize(theStyleElements, myInterpreted.getStyleElements(),
				(inst, interp) -> inst.getIdentity() == interp.getIdentity())//
			.<ModelInstantiationException> simpleE(interp -> interp.create(this))
			.onRightX(el -> el.getLeftValue().update(el.getRightValue(), myModels))//
			.onCommonX(el -> el.getLeftValue().update(el.getRightValue(), myModels))//
			.adjust();
		}
	}

	public interface QuickInstanceStyle {
		public interface Def extends QuickCompiledStyle {
			Object getId();

			QuickStyledElement.Def<?> getStyledElement();

			@Override
			Interpreted interpret(ExElement.Interpreted<?> parentEl, QuickInterpretedStyle parent, InterpretedExpressoEnv env)
				throws ExpressoInterpretationException;

			Set<QuickStyleAttributeDef> getApplicableAttributes();

			public static abstract class Abstract extends QuickCompiledStyle.Wrapper implements Def {
				private final QuickStyledElement.Def<?> theStyledElement;
				private final Object theId;
				private final Set<QuickStyleAttributeDef> theApplicableAttributes;

				protected Abstract(Def parent, QuickStyledElement.Def<?> styledElement, QuickCompiledStyle wrapped) {
					super(parent, wrapped);
					theStyledElement = styledElement;
					theId = new Object();
					theApplicableAttributes = new LinkedHashSet<>();
				}

				@Override
				public Def getParent() {
					return (Def) super.getParent();
				}

				@Override
				public Object getId() {
					return theId;
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

			default Object getId() {
				return getDefinition().getId();
			}

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

		Object getId();

		Set<QuickStyleAttribute<?>> getApplicableAttributes();

		<T> ObservableValue<T> getApplicableAttribute(QuickStyleAttribute<T> attribute);

		Observable<ObservableValueEvent<?>> changes();

		void update(Interpreted interpreted, ModelSetInstance models) throws ModelInstantiationException;

		public abstract class Abstract implements QuickInstanceStyle {
			private final QuickStyledElement theStyledElement;
			private final Object theId;
			private final Map<QuickStyleAttribute<?>, SettableValue<? extends ObservableValue<?>>> theApplicableAttributes;
			private final Map<QuickStyleAttribute<?>, ObservableValue<?>> theFlattenedAttributes;
			private Observable<ObservableValueEvent<?>> theChanges;

			protected Abstract(Interpreted interpreted, QuickStyledElement styledElement) {
				theId = interpreted.getId();
				theStyledElement = styledElement;
				theApplicableAttributes = new LinkedHashMap<>();
				theFlattenedAttributes = new LinkedHashMap<>();

				for (QuickStyleAttribute<?> attr : interpreted.getApplicableAttributes().values())
					initAttribute(attr);
			}

			private <T> SettableValue<ObservableValue<T>> initAttribute(QuickStyleAttribute<T> attr) {
				return (SettableValue<ObservableValue<T>>) theApplicableAttributes.computeIfAbsent(attr, __ -> {
					SettableValue<ObservableValue<T>> value = SettableValue
						.build(TypeTokens.get().keyFor(ObservableValue.class).<ObservableValue<T>> parameterized(attr.getType())).build();
					theFlattenedAttributes.put(attr, ObservableValue.flatten(value));
					return value;
				});
			}

			public QuickStyledElement getStyledElement() {
				return theStyledElement;
			}

			@Override
			public Object getId() {
				return theId;
			}

			@Override
			public Set<QuickStyleAttribute<?>> getApplicableAttributes() {
				return Collections.unmodifiableSet(theApplicableAttributes.keySet());
			}

			@Override
			public <T> ObservableValue<T> getApplicableAttribute(QuickStyleAttribute<T> attribute) {
				ObservableValue<T> value = (ObservableValue<T>) theFlattenedAttributes.get(attribute);
				if (value == null)
					throw new IllegalArgumentException(
						"Attribute " + attribute + " is not advertised as applicable to " + getClass().getName());
				return value;
			}

			@Override
			public Observable<ObservableValueEvent<?>> changes() {
				if (theChanges == null) {
					Observable<? extends ObservableValueEvent<?>>[] changes = new Observable[theFlattenedAttributes.size()];
					int i = 0;
					for (ObservableValue<?> value : theFlattenedAttributes.values())
						changes[i++] = value.noInitChanges();
					theChanges = Observable.or(changes);
				}
				return theChanges;
			}

			@Override
			public void update(Interpreted interpreted, ModelSetInstance models) throws ModelInstantiationException {
				for (QuickStyleAttribute<?> attr : interpreted.getApplicableAttributes().values())
					checkAttribute(attr, interpreted, models);
			}

			private <T> void checkAttribute(QuickStyleAttribute<T> attr, Interpreted interpreted, ModelSetInstance models)
				throws ModelInstantiationException {
				SettableValue<ObservableValue<T>> value = initAttribute(attr);
				value.set(interpreted.get(attr).evaluate(models), null);
			}

			@Override
			public String toString() {
				StringBuilder str = new StringBuilder(theStyledElement.getTypeName()).append(".style{");
				boolean any = false;
				for (Map.Entry<QuickStyleAttribute<?>, ObservableValue<?>> attr : theFlattenedAttributes.entrySet()) {
					Object value = attr.getValue().get();
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
