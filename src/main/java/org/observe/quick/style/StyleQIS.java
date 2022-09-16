package org.observe.quick.style;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ExpressoQIS.OneTimeSettableValue;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreterCore.CoreSession;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.SpecialSession;

public class StyleQIS implements SpecialSession<StyleQIS> {
	public static final String STYLE_ELEMENT = "quick-style-element";

	public static final String STYLED_PROP = "quick-interpreter-styled";
	public static final String STYLE_PROP = "quick-interpreter-style";
	public static final String STYLE_SHEET_PROP = "quick-interpreter-style-sheet";

	public static final String PARENT_MODEL_NAME = "__PARENT$MODEL$INSTANCE";
	public static final ValueContainer<SettableValue<?>, SettableValue<ModelSetInstance>> PARENT_MODEL = new ValueContainer<SettableValue<?>, SettableValue<ModelSetInstance>>() {
		private final ModelInstanceType<SettableValue<?>, SettableValue<ModelSetInstance>> theType = ModelTypes.Value
			.forType(ModelSetInstance.class);

		@Override
		public ModelInstanceType<SettableValue<?>, SettableValue<ModelSetInstance>> getType() {
			return theType;
		}

		@Override
		public SettableValue<ModelSetInstance> get(ModelSetInstance models) {
			throw new IllegalStateException("Parent model was not installed");
		}

		@Override
		public String toString() {
			return PARENT_MODEL_NAME;
		}
	};

	public static ModelSetInstance getParentModels(ModelSetInstance models) {
		return models.get(PARENT_MODEL_NAME, PARENT_MODEL.getType()).get();
	}

	public static ObservableModelSet.WrappedBuilder createChildModel(ObservableModelSet parentModels) {
		return parentModels.wrap()//
			.withCustomValue(PARENT_MODEL_NAME, PARENT_MODEL);
	}

	public static ObservableModelSet.WrappedInstanceBuilder createChildModelInstance(ObservableModelSet.Wrapped models,
		ModelSetInstance parentModelInstance) {
		return models.wrap(parentModelInstance)//
			.withCustom(PARENT_MODEL, SettableValue.of(ModelSetInstance.class, parentModelInstance, "Not Reversible"));
	}

	private final CoreSession theWrapped;
	private final QonfigToolkit theStyleTK;

	public StyleQIS(CoreSession session, QonfigToolkit styleTK) {
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
		return (Boolean) getWrapped().get(STYLED_PROP);
	}

	public QuickStyleSheet getStyleSheet() {
		return (QuickStyleSheet) getWrapped().get(STYLE_SHEET_PROP);
	}

	public StyleQIS setStyleSheet(QuickStyleSheet styleSheet) {
		getWrapped().put(STYLE_SHEET_PROP, styleSheet);
		return this;
	}

	public QuickElementStyle getStyle() {
		return (QuickElementStyle) getWrapped().get(STYLE_PROP);
	}

	public <T> QuickStyleAttribute<? extends T> getStyleAttribute(String element, String name, Class<T> type)
		throws QonfigInterpretationException {
		if (element != null) {
			QonfigElementOrAddOn el = getElement().getDocument().getDocToolkit().getElementOrAddOn(element);
			if (el == null)
				throw new QonfigInterpretationException("No such element or add-on '" + element + "'");
			else if (!getElement().isInstance(el))
				throw new QonfigInterpretationException("This element is not an instance of  '" + element + "'");
			return QuickStyleType.of(el, as(ExpressoQIS.class), theStyleTK).getAttribute(name, type);
		}
		return QuickStyleType.of(getType(), as(ExpressoQIS.class), theStyleTK).getAttribute(name, type);
	}

	public void installParentModels(ModelSetInstance models, ModelSetInstance parentModels) {
		((OneTimeSettableValue<ModelSetInstance>) models.get(PARENT_MODEL_NAME, null)).set(parentModels, null);
	}

	@Override
	public String toString() {
		return "quick:" + getWrapped().toString();
	}
}