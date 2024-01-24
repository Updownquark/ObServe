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

public class QuickTiledPane<T> extends QuickWidget.Abstract implements MultiValueRenderable<T> {
	public static final String TILED_PANE = "tiled-pane";

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

		public Def(org.observe.expresso.qonfig.ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		@QonfigAttributeGetter(asType = TILED_PANE, value = "values")
		public CompiledExpression getValues() {
			return theValues;
		}

		@Override
		public ModelComponentId getActiveValueVariable() {
			return theActiveValueVariable;
		}

		public ModelComponentId getSelectedVariable() {
			return theSelectedVariable;
		}

		public ModelComponentId getValueIndexVariable() {
			return theValueIndexVariable;
		}

		@QonfigAttributeGetter(asType = TILED_PANE, value = "layout")
		public QuickLayout.Def<?> getLayout() {
			return getAddOn(QuickLayout.Def.class);
		}

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

	public static class Interpreted<T> extends QuickWidget.Interpreted.Abstract<QuickTiledPane<T>>
	implements MultiValueRenderable.Interpreted<T, QuickTiledPane<T>> {
		private InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<T>> theValues;
		private QuickWidget.Interpreted<?> theRenderer;

		Interpreted(QuickTiledPane.Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		public TypeToken<T> getValueType() throws ExpressoInterpretationException {
			if (theValues == null) {
				theValues = getDefinition().getValues().interpret(ModelTypes.Collection.anyAsV(), getExpressoEnv());
			}
			return (TypeToken<T>) theValues.getType().getType(0);
		}

		public InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<T>> getValues() {
			return theValues;
		}

		public QuickLayout.Interpreted<?> getLayout() {
			return getAddOn(QuickLayout.Interpreted.class);
		}

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

	QuickTiledPane(Object id) {
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

	public void setContext(TabularWidget.TabularContext<T> ctx) throws ModelInstantiationException {
		setContext((MultiValueRenderContext<T>) ctx);
		theValueIndex.set(ctx.getRowIndex(), null);
	}

	@Override
	public void setContext(MultiValueRenderContext<T> ctx) throws ModelInstantiationException {
		theActiveValue.set(ctx.getActiveValue(), null);
		isSelected.set(ctx.isSelected(), null);
	}

	public ObservableCollection<T> getValues() {
		return ObservableCollection.flattenValue(theValues);
	}

	public SettableValue<T> getActiveValue() {
		return SettableValue.flatten(theActiveValue);
	}

	public QuickLayout getLayout() {
		return getAddOn(QuickLayout.class);
	}

	public QuickWidget getRenderer() {
		return theRenderer;
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) {
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
	public void instantiated() {
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
