package org.observe.quick.base;

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
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public class QuickComboButton<T> extends QuickButton implements MultiValueRenderable<T> {
	public static final String COMBO_BUTTON = "combo-button";

	@ExMultiElementTraceable({
		@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
			qonfigType = MultiValueRenderable.MULTI_VALUE_RENDERABLE,
			interpretation = Interpreted.class,
			instance = QuickComboBox.class),
		@ExElementTraceable(toolkit = QuickXInterpretation.X,
		qonfigType = COMBO_BUTTON,
		interpretation = Interpreted.class,
		instance = QuickComboButton.class),
		@ExElementTraceable(toolkit = QuickCoreInterpretation.CORE,
		qonfigType = "rendering",
		interpretation = Interpreted.class,
		instance = QuickComboButton.class)//
	})
	public static class Def<B extends QuickComboButton<?>> extends QuickButton.Def<B> implements MultiValueRenderable.Def<B> {
		private CompiledExpression theValues;
		private ModelComponentId theActiveValueVariable;
		private QuickWidget.Def<?> theRenderer;

		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		@QonfigAttributeGetter(asType = COMBO_BUTTON, value = "values")
		public CompiledExpression getValues() {
			return theValues;
		}

		@QonfigAttributeGetter(asType = MultiValueRenderable.MULTI_VALUE_RENDERABLE, value = "active-value-name")
		@Override
		public ModelComponentId getActiveValueVariable() {
			return theActiveValueVariable;
		}

		@QonfigChildGetter(asType = "rendering", value = "renderer")
		public QuickWidget.Def<?> getRenderer() {
			return theRenderer;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));
			theValues = getAttributeExpression("values", session);

			ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
			theActiveValueVariable = elModels.getElementValueModelId(session.getAttributeText("active-value-name"));
			elModels.satisfyElementValueType(theActiveValueVariable, ModelTypes.Value,
				(interp, env) -> ModelTypes.Value.forType(((Interpreted<?, ?>) interp).getValueType()));

			ExpressoQIS renderer = session.forChildren("renderer").peekFirst();
			if (renderer == null)
				renderer = session.metadata().get("default-renderer").get().peekFirst();
			theRenderer = syncChild(QuickWidget.Def.class, theRenderer, renderer, null);
		}

		@Override
		public Interpreted<?, ? extends B> interpret(ExElement.Interpreted<?> parent) {
			return (Interpreted<?, ? extends B>) new Interpreted<>((Def<QuickComboButton<Object>>) this, parent);
		}
	}

	public static class Interpreted<T, B extends QuickComboButton<T>> extends QuickButton.Interpreted<B>
	implements MultiValueRenderable.Interpreted<T, B> {
		private InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<T>> theValues;
		private QuickWidget.Interpreted<?> theRenderer;

		protected Interpreted(Def<? super B> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def<? super B> getDefinition() {
			return (Def<? super B>) super.getDefinition();
		}

		public InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<T>> getValues() {
			return theValues;
		}

		public TypeToken<T> getValueType() throws ExpressoInterpretationException {
			return (TypeToken<T>) getOrInitValues().getType().getType(0);
		}

		protected InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<T>> getOrInitValues()
			throws ExpressoInterpretationException {
			if (theValues == null)
				theValues = interpret(getDefinition().getValues(), ModelTypes.Collection.anyAsV());
			return theValues;
		}

		public QuickWidget.Interpreted<?> getRenderer() {
			return theRenderer;
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			super.doUpdate(env);
			getOrInitValues();

			theRenderer = syncChild(getDefinition().getRenderer(), theRenderer, def -> def.interpret(this),
				(r, rEnv) -> r.updateElement(rEnv));
		}

		@Override
		public B create() {
			return (B) new QuickComboButton<>(getIdentity());
		}
	}

	private ModelValueInstantiator<ObservableCollection<T>> theValuesInstantiator;
	private SettableValue<ObservableCollection<T>> theValues;
	private ModelComponentId theActiveValueVariable;
	private SettableValue<SettableValue<T>> theActiveValue;
	private QuickWidget theRenderer;

	protected QuickComboButton(Object id) {
		super(id);
	}

	public ObservableCollection<T> getValues() {
		return ObservableCollection.flattenValue(theValues);
	}

	@Override
	public ModelComponentId getActiveValueVariable() {
		return theActiveValueVariable;
	}

	@Override
	public ModelComponentId getSelectedVariable() {
		return null;
	}

	public QuickWidget getRenderer() {
		return theRenderer;
	}

	@Override
	public void setContext(MultiValueRenderContext<T> ctx) throws ModelInstantiationException {
		theActiveValue.set(ctx.getActiveValue(), null);
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) {
		super.doUpdate(interpreted);
		Interpreted<T, ?> myInterpreted = (Interpreted<T, ?>) interpreted;

		TypeToken<T> valueType = (TypeToken<T>) myInterpreted.getValues().getType().getType(0);
		if (theValues == null || !getValues().getType().equals(valueType)) {
			theValues = SettableValue
				.build(TypeTokens.get().keyFor(ObservableCollection.class).<ObservableCollection<T>> parameterized(valueType)).build();
			theActiveValue = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<T>> parameterized(valueType))
				.build();
		}
		theValuesInstantiator = myInterpreted.getValues() == null ? null : myInterpreted.getValues().instantiate();
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
		if (theValuesInstantiator != null)
			theValuesInstantiator.instantiate();
		if (theRenderer != null)
			theRenderer.instantiated();
	}

	@Override
	protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		super.doInstantiate(myModels);
		theValues.set(theValuesInstantiator == null ? null : theValuesInstantiator.get(myModels), null);
		ExFlexibleElementModelAddOn.satisfyElementValue(theActiveValueVariable, myModels, SettableValue.flatten(theActiveValue));

		if (theRenderer != null)
			theRenderer.instantiate(myModels);
	}

	@Override
	protected QuickComboButton<T> clone() {
		QuickComboButton<T> copy = (QuickComboButton<T>) super.clone();

		copy.theValues = SettableValue.build(theValues.getType()).build();
		copy.theActiveValue = SettableValue.build(theActiveValue.getType()).build();

		if (theRenderer != null)
			copy.theRenderer = theRenderer.copy(copy);

		return copy;
	}
}
