package org.observe.quick.base;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

/**
 * A horizontal set of radio buttons, each representing a value that may be selected from a collection
 *
 * @param <T> The type of the value to select
 */
public class QuickRadioButtons<T> extends CollectionSelectorWidget<T> {
	/** The XML name of this element */
	public static final String RADIO_BUTTONS = "radio-buttons";

	/** {@link QuickRadioButton} definition */
	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = RADIO_BUTTONS,
		interpretation = Interpreted.class,
		instance = QuickRadioButtons.class)
	public static class Def extends CollectionSelectorWidget.Def<QuickRadioButtons<?>> {
		private CompiledExpression theRender;
		private CompiledExpression theValueTooltip;

		/**
		 * @param parent The parent element of the widget
		 * @param type The Qonfig type of the widget
		 */
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		/** @return The text to use to render each radio button */
		@QonfigAttributeGetter("render")
		public CompiledExpression getRender() {
			return theRender;
		}

		/** @return The tooltip to display for each radio button individually */
		@QonfigAttributeGetter("value-tooltip")
		public CompiledExpression getValueTooltip() {
			return theValueTooltip;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);

			theRender = getAttributeExpression("render", session);
			theValueTooltip = getAttributeExpression("value-tooltip", session);
		}

		@Override
		public Interpreted<?> interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}
	}

	/**
	 * {@link QuickRadioButton} interpretation
	 *
	 * @param <T> The type of the value to select
	 */
	public static class Interpreted<T> extends CollectionSelectorWidget.Interpreted<T, QuickRadioButtons<T>> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theRender;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theValueTooltip;

		/**
		 * @param definition The definition to interpret
		 * @param parent The parent element for the widget
		 */
		public Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		/** @return The text to use to render each radio button */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getRender() {
			return theRender;
		}

		/** @return The tooltip to display for each radio button individually */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getValueTooltip() {
			return theValueTooltip;
		}

		@Override
		protected void doUpdate() throws ExpressoInterpretationException {
			super.doUpdate();

			theRender = interpret(getDefinition().getRender(), ModelTypes.Value.STRING);
			theValueTooltip = interpret(getDefinition().getValueTooltip(), ModelTypes.Value.STRING);
		}

		@Override
		public QuickRadioButtons<T> create() {
			return new QuickRadioButtons<>(getIdentity());
		}
	}

	private ModelValueInstantiator<SettableValue<String>> theRenderInstantiator;
	private ModelValueInstantiator<SettableValue<String>> theValueTooltipInstantiator;

	private SettableValue<SettableValue<String>> theRender;
	private SettableValue<SettableValue<String>> theValueTooltip;

	/** @param id The element ID for this widget */
	protected QuickRadioButtons(Object id) {
		super(id);
		theRender = SettableValue.create();
		theValueTooltip = SettableValue.create();
	}

	/** @return The text to use to render each radio button */
	public SettableValue<String> getRender() {
		return SettableValue.flatten(theRender);
	}

	/** @return The tooltip to display for each radio button individually */
	public SettableValue<String> getValueTooltip() {
		return SettableValue.flatten(theValueTooltip);
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);

		Interpreted<T> myInterpreted = (Interpreted<T>) interpreted;
		theRenderInstantiator = myInterpreted.getRender() == null ? null : myInterpreted.getRender().instantiate();
		theValueTooltipInstantiator = myInterpreted.getValueTooltip() == null ? null : myInterpreted.getValueTooltip().instantiate();
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();

		if (theRenderInstantiator != null)
			theRenderInstantiator.instantiate();
		if (theValueTooltipInstantiator != null)
			theValueTooltipInstantiator.instantiate();
	}

	@Override
	protected ModelSetInstance doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		myModels = super.doInstantiate(myModels);

		theRender.set(theRenderInstantiator == null ? null : theRenderInstantiator.get(myModels), null);
		theValueTooltip.set(theValueTooltipInstantiator == null ? null : theValueTooltipInstantiator.get(myModels), null);
		return myModels;
	}

	@Override
	public QuickRadioButtons<T> copy(ExElement parent) {
		QuickRadioButtons<T> copy = (QuickRadioButtons<T>) super.copy(parent);

		copy.theRender = SettableValue.create();
		copy.theValueTooltip = SettableValue.create();

		return copy;
	}
}
