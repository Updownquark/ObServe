package org.observe.quick.ext;

import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExFlexibleElementModelAddOn;
import org.observe.expresso.qonfig.ExMultiElementTraceable;
import org.observe.expresso.qonfig.ExWithElementModel;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.expresso.qonfig.QonfigChildGetter;
import org.observe.quick.QuickCoreInterpretation;
import org.observe.quick.QuickWidget;
import org.observe.quick.base.MultiValueRenderable;
import org.observe.quick.base.QuickComboBox;
import org.observe.quick.base.QuickLayout;
import org.observe.quick.base.TabularWidget;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

/**
 * A widget that renders each value of a collection as a separate content widget.
 *
 * @param <T> The type of the values in the collection
 */
public class QuickTiledPane<T> extends QuickWidget.Abstract implements MultiValueRenderable<T> {
	/** The XML name of this element */
	public static final String TILED_PANE = "tiled-pane";

	/** {@link QuickTiledPane} definition */
	@ExMultiElementTraceable({
		@ExElementTraceable(toolkit = QuickXInterpretation.X,
			qonfigType = TILED_PANE,
			interpretation = Interpreted.class,
			instance = QuickComboBox.class),
		@ExElementTraceable(toolkit = QuickCoreInterpretation.CORE,
		qonfigType = "rendering",
		interpretation = Interpreted.class,
		instance = QuickComboBox.class)//
	})
	public static class Def extends QuickWidget.Def.Abstract<QuickTiledPane<?>> implements MultiValueRenderable.Def<QuickTiledPane<?>> {
		private ModelComponentId theActiveValueVariable;
		private ModelComponentId theSelectedVariable;
		private ModelComponentId theValueIndexVariable;
		private CompiledExpression theValues;
		private QuickWidget.Def<?> theRenderer;

		/**
		 * @param parent The parent element of the widget
		 * @param type The Qonfig type of the widget
		 */
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		/** @return The values, each of which to represent as a separate content widget */
		@QonfigAttributeGetter(asType = TILED_PANE, value = "values")
		public CompiledExpression getValues() {
			return theValues;
		}

		@Override
		public ModelComponentId getActiveValueVariable() {
			return theActiveValueVariable;
		}

		/** @return The model ID of the value containing whether the current value is selected */
		public ModelComponentId getSelectedVariable() {
			return theSelectedVariable;
		}

		/** @return The model ID of the value containing the index of the current value */
		public ModelComponentId getValueIndexVariable() {
			return theValueIndexVariable;
		}

		/** @return The layout for the content of this tiled pane */
		@QonfigAttributeGetter(asType = TILED_PANE, value = "layout")
		public QuickLayout.Def<?> getLayout() {
			return getAddOn(QuickLayout.Def.class);
		}

		/** @return The renderer to render each value of this tiled pane */
		@QonfigChildGetter(asType = "rendering", value = "renderer")
		public QuickWidget.Def<?> getRenderer() {
			return theRenderer;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));

			ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
			theActiveValueVariable = elModels.getElementValueModelId(session.getAttributeText("active-value-name"));
			theSelectedVariable = elModels.getElementValueModelId("selected");
			theValueIndexVariable = elModels.getElementValueModelId("rowIndex");
			theValues = getAttributeExpression("values", session);
			elModels.satisfyElementValueType(theActiveValueVariable, ModelTypes.Value,
				(interp, env) -> ModelTypes.Value.forType(((Interpreted<?>) interp).getValueType()));

			ExpressoQIS renderer = session.forChildren("renderer").peekFirst();
			if (renderer == null)
				renderer = session.metadata().get("default-renderer").get().peekFirst();
			theRenderer = syncChild(QuickWidget.Def.class, theRenderer, renderer, null);
		}

		@Override
		public QuickWidget.Interpreted<? extends QuickTiledPane<?>> interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}
	}

	/**
	 * {@link QuickTiledPane} interpretation
	 *
	 * @param <T> The type of the values in the collection
	 */
	public static class Interpreted<T> extends QuickWidget.Interpreted.Abstract<QuickTiledPane<T>>
	implements MultiValueRenderable.Interpreted<T, QuickTiledPane<T>> {
		private InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<T>> theValues;
		private QuickWidget.Interpreted<?> theRenderer;

		/**
		 * @param definition The definition to interpret
		 * @param parent The parent element for the widget
		 */
		protected Interpreted(QuickTiledPane.Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		/**
		 * @return The type of values in the collection
		 * @throws ExpressoInterpretationException If the type of the values cannot be interpreted
		 */
		public TypeToken<T> getValueType() throws ExpressoInterpretationException {
			if (theValues == null) {
				theValues = getDefinition().getValues().interpret(ModelTypes.Collection.anyAsV(), getExpressoEnv());
			}
			return (TypeToken<T>) theValues.getType().getType(0);
		}

		/** @return The values, each of which to represent as a separate content widget */
		public InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<T>> getValues() {
			return theValues;
		}

		/** @return The layout for the content of this tiled pane */
		public QuickLayout.Interpreted<?> getLayout() {
			return getAddOn(QuickLayout.Interpreted.class);
		}

		/** @return The renderer to render each value of this tiled pane */
		public QuickWidget.Interpreted<?> getRenderer() {
			return theRenderer;
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			super.doUpdate(env);
			getValueType();
			theRenderer = syncChild(getDefinition().getRenderer(), theRenderer, def -> def.interpret(this),
				(r, rEnv) -> r.updateElement(rEnv));
		}

		@Override
		public QuickTiledPane<T> create() {
			return new QuickTiledPane<>(getIdentity());
		}
	}

	private TypeToken<T> theValueType;
	private ModelComponentId theSelectedVariable;
	private ModelComponentId theValueIndexVariable;
	private ModelComponentId theActiveValueVariable;
	private ModelValueInstantiator<ObservableCollection<T>> theValuesInstantiator;

	private SettableValue<ObservableCollection<T>> theValues;
	private SettableValue<SettableValue<T>> theActiveValue;
	private SettableValue<SettableValue<Boolean>> isSelected;
	private SettableValue<SettableValue<Integer>> theValueIndex;
	private QuickWidget theRenderer;

	/** @param id The element ID for this widget */
	protected QuickTiledPane(Object id) {
		super(id);
		isSelected = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Boolean>> parameterized(boolean.class))
			.build();
		theValueIndex = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Integer>> parameterized(int.class))
			.build();
	}

	@Override
	public ModelComponentId getActiveValueVariable() {
		return theActiveValueVariable;
	}

	@Override
	public ModelComponentId getSelectedVariable() {
		return theSelectedVariable;
	}

	/**
	 * @param ctx The model context for this tiled pane
	 * @throws ModelInstantiationException If the model context could not be installed
	 */
	public void setContext(TabularWidget.TabularContext<T> ctx) throws ModelInstantiationException {
		setContext((MultiValueRenderContext<T>) ctx);
		theValueIndex.set(ctx.getRowIndex(), null);
	}

	@Override
	public void setContext(MultiValueRenderContext<T> ctx) throws ModelInstantiationException {
		theActiveValue.set(ctx.getActiveValue(), null);
		isSelected.set(ctx.isSelected(), null);
	}

	/** @return The values, each of which to represent as a separate content widget */
	public ObservableCollection<T> getValues() {
		return ObservableCollection.flattenValue(theValues);
	}

	/** @return The active value (e.g. the one being rendered or interacted with) */
	public SettableValue<T> getActiveValue() {
		return SettableValue.flatten(theActiveValue);
	}

	/** @return The layout for the content of this tiled pane */
	public QuickLayout getLayout() {
		return getAddOn(QuickLayout.class);
	}

	/** @return The renderer to render each value of this tiled pane */
	public QuickWidget getRenderer() {
		return theRenderer;
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);

		QuickTiledPane.Interpreted<T> myInterpreted = (QuickTiledPane.Interpreted<T>) interpreted;
		TypeToken<T> valueType;
		try {
			valueType = myInterpreted.getValueType();
		} catch (ExpressoInterpretationException e) {
			throw new IllegalStateException("Not initialized?", e);
		}
		if (theValueType == null || !theValueType.equals(valueType)) {
			theValueType = valueType;
			theValues = SettableValue
				.build(TypeTokens.get().keyFor(ObservableCollection.class).<ObservableCollection<T>> parameterized(theValueType)).build();
			theActiveValue = SettableValue
				.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<T>> parameterized(theValueType)).build();
		}
		theValuesInstantiator = myInterpreted.getValues().instantiate();
		theSelectedVariable = myInterpreted.getDefinition().getSelectedVariable();
		theValueIndexVariable = myInterpreted.getDefinition().getValueIndexVariable();
		theActiveValueVariable = myInterpreted.getDefinition().getActiveValueVariable();

		if (theRenderer != null
			&& (myInterpreted.getRenderer() == null || theRenderer.getIdentity() != myInterpreted.getRenderer().getIdentity())) {
			theRenderer.destroy();
			theRenderer = null;
		}
		if (theRenderer == null && myInterpreted.getRenderer() != null)
			theRenderer = myInterpreted.getRenderer().create();
		if (theRenderer != null)
			theRenderer.update(myInterpreted.getRenderer(), this);
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();

		theValuesInstantiator.instantiate();
		if (theRenderer != null)
			theRenderer.instantiated();
	}

	@Override
	protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		super.doInstantiate(myModels);

		theValues.set(theValuesInstantiator.get(myModels), null);

		ExFlexibleElementModelAddOn.satisfyElementValue(theActiveValueVariable, myModels, SettableValue.flatten(theActiveValue));
		ExFlexibleElementModelAddOn.satisfyElementValue(theSelectedVariable, myModels, SettableValue.flatten(isSelected));
		ExFlexibleElementModelAddOn.satisfyElementValue(theValueIndexVariable, myModels, SettableValue.flatten(theValueIndex));

		if (theRenderer != null)
			theRenderer.instantiate(myModels);
	}

	@Override
	public QuickTiledPane<T> copy(ExElement parent) {
		QuickTiledPane<T> copy = (QuickTiledPane<T>) super.copy(parent);

		copy.theValues = SettableValue.build(theValues.getType()).build();
		copy.theActiveValue = SettableValue.build(theActiveValue.getType()).build();
		copy.isSelected = SettableValue.build(isSelected.getType()).build();
		copy.theValueIndex = SettableValue.build(theValueIndex.getType()).build();
		if (theRenderer != null)
			copy.theRenderer = theRenderer.copy(copy);

		return copy;
	}
}
