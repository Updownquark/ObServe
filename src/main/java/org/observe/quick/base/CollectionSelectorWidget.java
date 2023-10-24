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
import org.observe.quick.QuickValueWidget;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public abstract class CollectionSelectorWidget<T> extends QuickValueWidget.Abstract<T> implements MultiValueRenderable<T> {
	public static final String COLLECTION_SELECTOR = "collection-selector-widget";

	@ExMultiElementTraceable({
		@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
			qonfigType = MultiValueRenderable.MULTI_VALUE_RENDERABLE,
			interpretation = Interpreted.class,
			instance = QuickComboBox.class),
		@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = COLLECTION_SELECTOR,
		interpretation = Interpreted.class,
		instance = CollectionSelectorWidget.class)//
	})
	public static abstract class Def<W extends CollectionSelectorWidget<?>> extends QuickValueWidget.Def.Abstract<W>
	implements MultiValueRenderable.Def<W> {
		private CompiledExpression theValues;
		private ModelComponentId theActiveValueVariable;
		private ModelComponentId theSelectedVariable;

		protected Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		@QonfigAttributeGetter(asType = COLLECTION_SELECTOR, value = "values")
		public CompiledExpression getValues() {
			return theValues;
		}

		@QonfigAttributeGetter(asType = MultiValueRenderable.MULTI_VALUE_RENDERABLE, value = "active-value-name")
		@Override
		public ModelComponentId getActiveValueVariable() {
			return theActiveValueVariable;
		}

		public ModelComponentId getSelectedVariable() {
			return theSelectedVariable;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));
			theValues = getAttributeExpression("values", session);

			ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
			theActiveValueVariable = elModels.getElementValueModelId(session.getAttributeText("active-value-name"));
			theSelectedVariable = elModels.getElementValueModelId("selected");
			elModels.satisfyElementValueType(theActiveValueVariable, ModelTypes.Value,
				(interp, env) -> ModelTypes.Value.forType(((Interpreted<?, ?>) interp).getValueType()));
		}

		@Override
		public abstract Interpreted<?, ? extends W> interpret(ExElement.Interpreted<?> parent);
	}

	public static abstract class Interpreted<T, W extends CollectionSelectorWidget<T>> extends QuickValueWidget.Interpreted.Abstract<T, W> {
		private InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<T>> theValues;

		protected Interpreted(Def<? super W> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def<? super W> getDefinition() {
			return (Def<? super W>) super.getDefinition();
		}

		public InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<T>> getValues() {
			return theValues;
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			super.doUpdate(env);
			theValues = interpret(getDefinition().getValues(), ModelTypes.Collection.forType(getValueType()));
		}
	}

	private ModelValueInstantiator<ObservableCollection<T>> theValuesInstantiator;
	private SettableValue<ObservableCollection<T>> theValues;
	private ModelComponentId theActiveValueVariable;
	private ModelComponentId theSelectedVariable;
	private SettableValue<SettableValue<T>> theActiveValue;
	private SettableValue<SettableValue<Boolean>> theSelectedValue;

	protected CollectionSelectorWidget(Object id) {
		super(id);
		theSelectedValue = SettableValue
			.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Boolean>> parameterized(boolean.class)).build();
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
		return theSelectedVariable;
	}

	@Override
	public void setContext(MultiValueRenderContext<T> ctx) throws ModelInstantiationException {
		theActiveValue.set(ctx.getActiveValue(), null);
		theSelectedValue.set(ctx.isSelected(), null);
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) {
		super.doUpdate(interpreted);
		Interpreted<T, ?> myInterpreted = (Interpreted<T, ?>) interpreted;
		TypeToken<T> valueType = (TypeToken<T>) myInterpreted.getValue().getType().getType(0);
		if (theValues == null || !getValues().getType().equals(valueType)) {
			theValues = SettableValue
				.build(TypeTokens.get().keyFor(ObservableCollection.class).<ObservableCollection<T>> parameterized(valueType)).build();
			theActiveValue = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<T>> parameterized(valueType))
				.build();
		}
		theValuesInstantiator = myInterpreted.getValues() == null ? null : myInterpreted.getValues().instantiate();
		theActiveValueVariable = myInterpreted.getDefinition().getActiveValueVariable();
		theSelectedVariable = myInterpreted.getDefinition().getSelectedVariable();
	}

	@Override
	public void instantiated() {
		super.instantiated();
		if (theValuesInstantiator != null)
			theValuesInstantiator.instantiate();
	}

	@Override
	protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		super.doInstantiate(myModels);
		theValues.set(theValuesInstantiator == null ? null : theValuesInstantiator.get(myModels), null);
		ExFlexibleElementModelAddOn.satisfyElementValue(theActiveValueVariable, myModels, SettableValue.flatten(theActiveValue));
		ExFlexibleElementModelAddOn.satisfyElementValue(theSelectedVariable, myModels, SettableValue.flatten(theSelectedValue));
	}

	@Override
	protected CollectionSelectorWidget<T> clone() {
		CollectionSelectorWidget<T> copy = (CollectionSelectorWidget<T>) super.clone();

		copy.theValues = SettableValue.build(theValues.getType()).build();
		copy.theActiveValue = SettableValue.build(theActiveValue.getType()).build();
		copy.theSelectedValue = SettableValue.build(theSelectedValue.getType()).build();

		return copy;
	}
}
