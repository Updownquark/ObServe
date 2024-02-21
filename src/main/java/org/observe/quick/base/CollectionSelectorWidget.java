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
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

/**
 * A widget that allows the user to select a value from a collection (e.g. a combo box)
 *
 * @param <T> The type of values represented by the widget
 */
public abstract class CollectionSelectorWidget<T> extends QuickValueWidget.Abstract<T> implements MultiValueRenderable<T> {
	/** The XML name of this element */
	public static final String COLLECTION_SELECTOR = "collection-selector-widget";

	/**
	 * {@link CollectionSelectorWidget} definition
	 *
	 * @param <W> The sub-type of widget to create
	 */
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

		/**
		 * @param parent The parent element for this widget
		 * @param type The Qonfig type of this element
		 */
		protected Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		/** @return The elements the user may select from */
		@QonfigAttributeGetter(asType = COLLECTION_SELECTOR, value = "values")
		public CompiledExpression getValues() {
			return theValues;
		}

		@QonfigAttributeGetter(asType = MultiValueRenderable.MULTI_VALUE_RENDERABLE, value = "active-value-name")
		@Override
		public ModelComponentId getActiveValueVariable() {
			return theActiveValueVariable;
		}

		/** @return The ID of the model value in which the selected value for the widget will be available to expressions */
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

	/**
	 * {@link CollectionSelectorWidget} interpretation
	 *
	 * @param <T> The type of values represented by the widget
	 * @param <W> The sub-type of widget to create
	 */
	public static abstract class Interpreted<T, W extends CollectionSelectorWidget<T>> extends QuickValueWidget.Interpreted.Abstract<T, W> {
		private InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<T>> theValues;

		/**
		 * @param definition The definition to interpret
		 * @param parent The parent for this element
		 */
		protected Interpreted(Def<? super W> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def<? super W> getDefinition() {
			return (Def<? super W>) super.getDefinition();
		}

		/** @return The elements the user may select from */
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

	/** @param id The element identity of the widget */
	protected CollectionSelectorWidget(Object id) {
		super(id);
		theValues = SettableValue.<ObservableCollection<T>> build().build();
		theActiveValue = SettableValue.<SettableValue<T>> build().build();
		theSelectedValue = SettableValue.<SettableValue<Boolean>> build().build();
	}

	/** @return The elements the user may select from */
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
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);
		Interpreted<T, ?> myInterpreted = (Interpreted<T, ?>) interpreted;
		theValuesInstantiator = myInterpreted.getValues() == null ? null : myInterpreted.getValues().instantiate();
		theActiveValueVariable = myInterpreted.getDefinition().getActiveValueVariable();
		theSelectedVariable = myInterpreted.getDefinition().getSelectedVariable();
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
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

		copy.theValues = SettableValue.<ObservableCollection<T>> build().build();
		copy.theActiveValue = SettableValue.<SettableValue<T>> build().build();
		copy.theSelectedValue = SettableValue.<SettableValue<Boolean>> build().build();

		return copy;
	}
}
