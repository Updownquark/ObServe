package org.observe.quick.style;

import org.observe.SettableValue;
import org.observe.expresso.DynamicModelValue;
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ModelException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelTag;
import org.observe.expresso.TypeConversionException;
import org.observe.expresso.qonfig.ExElement;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretation;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreterCore.CoreSession;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.SpecialSession;

/** Special session for {@link QonfigInterpretation} that provides utilities related to the Quick-Style toolkit */
public class StyleQIS implements SpecialSession<StyleQIS> {
	/** {@link ObservableModelSet} model tag on local models for elements that are instances of &lt;styled> */
	public static final ModelTag<QonfigElement> STYLED_ELEMENT_TAG = ModelTag.of(QonfigElement.class.getSimpleName(),
		TypeTokens.get().of(QonfigElement.class));
	static final String STYLE_SET_PROP = "quick-interpreter-style-type-set";
	static final String STYLED_PROP = "quick-interpreter-styled";
	static final String STYLE_PROP = "quick-interpreter-style";
	static final String STYLE_SHEET_PROP = "quick-interpreter-style-sheet";
	static final String STYLE_ELEMENT = "expresso-style-element";

	private static final String PARENT_MODEL_NAME = "PARENT$MODEL$INSTANCE";
	private static final ModelInstanceType<SettableValue<?>, SettableValue<ModelSetInstance>> PARENT_MODEL_TYPE = ModelTypes.Value
		.forType(ModelSetInstance.class);

	/**
	 * @param models The model instance to install the parent models into
	 * @param parentModels The model instance for the parent &lt;style> element
	 */
	public static void installParentModels(ModelSetInstance models, ModelSetInstance parentModels) {
		try {
			DynamicModelValue.satisfyDynamicValue(PARENT_MODEL_NAME, ModelTypes.Value.forType(ModelSetInstance.class), models, //
				SettableValue.of(ModelSetInstance.class, parentModels, "Not settable"));
		} catch (ModelException | TypeConversionException | ModelInstantiationException e) {
			throw new IllegalStateException("Could not install parent models", e);
		}
	}

	/**
	 * @param models The model instance to get the parent model from
	 * @return The model instance for the parent &lt;styled> element
	 */
	public static ModelSetInstance getParentModels(ModelSetInstance models) {
		try {
			return models.getModel().getValue(PARENT_MODEL_NAME, PARENT_MODEL_TYPE).get(models).get();
		} catch (ModelException | TypeConversionException | ModelInstantiationException | IllegalStateException e) {
			throw new IllegalStateException("Could not access parent models. Perhaps they have not been installed.", e);
		}
	}

	private final CoreSession theWrapped;
	private final QonfigToolkit theStyleTK;

	StyleQIS(CoreSession session, QonfigToolkit styleTK) {
		theWrapped = session;
		theStyleTK = styleTK;
	}

	@Override
	public CoreSession getWrapped() {
		return theWrapped;
	}

	StyleQIS wrap(CoreSession session) throws QonfigInterpretationException {
		return session.as(StyleQIS.class);
	}

	boolean isStyled() {
		return (Boolean) getWrapped().get(STYLED_PROP, true);
	}

	/** @return The set of style types for this style session */
	public QuickTypeStyle.TypeStyleSet getStyleTypes() {
		return get(STYLE_SET_PROP, QuickTypeStyle.TypeStyleSet.class);
	}

	/** @return The style sheet applying to this element */
	public QuickStyleSheet getStyleSheet() {
		return (QuickStyleSheet) getWrapped().get(STYLE_SHEET_PROP);
	}

	/**
	 * @param styleSheet The style sheet to apply to this element and its descendants
	 * @return This session
	 */
	public StyleQIS setStyleSheet(QuickStyleSheet styleSheet) {
		getWrapped().put(STYLE_SHEET_PROP, styleSheet);
		return this;
	}

	public ExElement.Def<?> getStyleElement() {
		return getWrapped().get(STYLE_ELEMENT, ExElement.Def.class);
	}

	public StyleQIS setStyleElement(ExElement.Def<?> def) {
		getWrapped().put(STYLE_ELEMENT, def);
		return this;
	}

	/** @return This element's style */
	public QuickCompiledStyle getStyle() {
		return (QuickCompiledStyle) getWrapped().get(STYLE_PROP);
	}

	/**
	 * @param <T> The type of the style attribute
	 * @param element The name of the element type owning the style attribute
	 * @param name The name of the style attribute to get
	 * @param type The type of the style attribute
	 * @return The style attribute referred to
	 * @throws QonfigInterpretationException If no such element or style attribute exists
	 */
	public <T> QuickStyleAttribute<? extends T> getStyleAttribute(String element, String name, Class<T> type)
		throws QonfigInterpretationException {
		if (element != null) {
			QonfigElementOrAddOn el = getElement().getDocument().getDocToolkit().getElementOrAddOn(element);
			if (el == null)
				throw new QonfigInterpretationException("No such element or add-on '" + element + "'", getElement().getPositionInFile(), 0);
			else if (!getElement().isInstance(el))
				throw new QonfigInterpretationException("This element is not an instance of  '" + element + "'",
					getElement().getPositionInFile(), 0);
			return getStyleTypes().getOrCompile(el, as(ExpressoQIS.class), theStyleTK).getAttribute(name, type);
		}
		return getStyleTypes().getOrCompile(getFocusType(), as(ExpressoQIS.class), theStyleTK).getAttribute(name, type);
	}

	@Override
	public String toString() {
		return "quick:" + getWrapped().toString();
	}
}
