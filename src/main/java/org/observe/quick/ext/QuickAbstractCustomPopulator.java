package org.observe.quick.ext;

import org.observe.ObservableAction;
import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExFlexibleElementModelAddOn;
import org.observe.expresso.qonfig.ExWithElementModel;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.quick.QuickWidget;
import org.observe.util.TypeTokens;
import org.observe.util.swing.PanelPopulation;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

/** A custom widget populated with the {@link PanelPopulation} API */
public abstract class QuickAbstractCustomPopulator extends QuickWidget.Abstract {
	/** The XML name of this Quick element */
	public static final String ABSTRACT_CUSTOM_POPULATOR = "abstract-custom-populator";
	/** The {@link SettableValue}&lt;{@link org.observe.util.swing.PanelPopulation.PanelPopulator}&lt;?, ?>> model type */
	public static final ModelInstanceType<SettableValue<?>, SettableValue<PanelPopulation.PanelPopulator<?, ?>>> PANEL_TYPE = ModelTypes.Value
		.forType(TypeTokens.get().keyFor(PanelPopulation.PanelPopulator.class).wildCard());

	/**
	 * Definition for a {@link QuickAbstractCustomPopulator}
	 *
	 * @param <P> The sub-type of populator
	 */
	@ExElementTraceable(toolkit = QuickXInterpretation.X,
		qonfigType = ABSTRACT_CUSTOM_POPULATOR,
		interpretation = Interpreted.class,
		instance = QuickAbstractCustomPopulator.class)
	public static abstract class Def<P extends QuickAbstractCustomPopulator> extends QuickWidget.Def.Abstract<P> {
		private ModelComponentId thePanelAs;
		private CompiledExpression thePopulation;

		/**
		 * @param parent The parent element of the widget
		 * @param type The Qonfig type of the widget
		 */
		protected Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		/** @return The variable by which the {@link org.observe.util.swing.PanelPopulation.PanelPopulator populator} will be available */
		@QonfigAttributeGetter("panel-as")
		public ModelComponentId getPanelAs() {
			return thePanelAs;
		}

		/**
		 * @return The action that will build the widget from the {@link org.observe.util.swing.PanelPopulation.PanelPopulator populator}
		 */
		@QonfigAttributeGetter
		public CompiledExpression getPopulation() {
			return thePopulation;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);

			String panelAsAttr = session.getAttributeText("panel-as");
			ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
			thePanelAs = elModels.getElementValueModelId(panelAsAttr);
			// Would be cool if we could make this more general somehow, but not sure how and I'm making this for a specific purpose
			elModels.satisfyElementValueType(thePanelAs, PANEL_TYPE);
			thePopulation = getValueExpression(session);
		}

		@Override
		public abstract Interpreted<? extends P> interpret(ExElement.Interpreted<?> parent);
	}

	/**
	 * Interpretation for a {@link QuickAbstractCustomPopulator}
	 *
	 * @param <P> The sub-type of populator
	 */
	public static abstract class Interpreted<P extends QuickAbstractCustomPopulator> extends QuickWidget.Interpreted.Abstract<P> {
		private InterpretedValueSynth<ObservableAction, ObservableAction> thePopulation;

		/**
		 * @param definition The widget definition
		 * @param parent The parent element of this widget
		 */
		protected Interpreted(Def<? super P> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def<? super P> getDefinition() {
			return (Def<? super P>) super.getDefinition();
		}

		/**
		 * @return The action that will build the widget from the {@link org.observe.util.swing.PanelPopulation.PanelPopulator populator}
		 */
		public InterpretedValueSynth<ObservableAction, ObservableAction> getPopulation() {
			return thePopulation;
		}

		@Override
		protected void doUpdate() throws ExpressoInterpretationException {
			super.doUpdate();

			thePopulation = interpret(getDefinition().getPopulation(), ModelTypes.Action.instance());
		}

		@Override
		public abstract P create();
	}

	private ModelComponentId thePanelAs;
	private ModelValueInstantiator<ObservableAction> thePopulationInstantiator;

	private SettableValue<PanelPopulation.PanelPopulator<?, ?>> thePanel;
	private ObservableAction thePopulation;

	/** @param id The identity of the widget */
	protected QuickAbstractCustomPopulator(Object id) {
		super(id);

		thePanel = SettableValue.create();
	}

	/** @return The variable by which the {@link org.observe.util.swing.PanelPopulation.PanelPopulator populator} will be available */
	public ModelComponentId getPanelAs() {
		return thePanelAs;
	}

	/** @return The action that will build the widget from the {@link org.observe.util.swing.PanelPopulation.PanelPopulator populator} */
	public ObservableAction getPopulation() {
		return thePopulation;
	}

	/**
	 * Builds the custom widget
	 *
	 * @param panel The populator to use to populate the widget
	 */
	public void populate(PanelPopulation.PanelPopulator<?, ?> panel) {
		thePanel.set(panel);
		thePopulation.act(null);
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);

		Interpreted<?> myInterpreted = (Interpreted<?>) interpreted;
		thePanelAs = myInterpreted.getDefinition().getPanelAs();
		thePopulationInstantiator = myInterpreted.getPopulation().instantiate();
	}

	@Override
	protected ModelSetInstance doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		myModels = super.doInstantiate(myModels);

		ExFlexibleElementModelAddOn.satisfyElementValue(thePanelAs, myModels, thePanel);
		thePopulation = thePopulationInstantiator.get(myModels);

		return myModels;
	}

	@Override
	public QuickAbstractCustomPopulator copy(ExElement parent) {
		QuickAbstractCustomPopulator copy = (QuickAbstractCustomPopulator) super.copy(parent);

		copy.thePanel = SettableValue.create();

		return copy;
	}
}
