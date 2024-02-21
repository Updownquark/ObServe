package org.observe.quick.base;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.quick.QuickValueWidget;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

/** A horizontal bar that displays the progress of an operation */
public class QuickProgressBar extends QuickValueWidget.Abstract<Integer> {
	/** The XML name of this element */
	public static final String PROGRESS_BAR = "progress-bar";

	/** {@link QuickProgressBar} definition */
	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = PROGRESS_BAR,
		interpretation = Interpreted.class,
		instance = QuickProgressBar.class)
	public static class Def extends QuickValueWidget.Def.Abstract<QuickProgressBar> {
		private CompiledExpression theMaximum;
		private CompiledExpression theText;

		/**
		 * @param parent The parent element of the widget
		 * @param type The Qonfig type of the widget
		 */
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		/** @return The maximum value for the progress bar */
		@QonfigAttributeGetter("max")
		public CompiledExpression getMaximum() {
			return theMaximum;
		}

		/** @return The text to display in the progress bar */
		@QonfigAttributeGetter("text")
		public CompiledExpression getText() {
			return theText;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			theMaximum = getAttributeExpression("max", session);
			theText = getAttributeExpression("text", session);
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}
	}

	/** {@link QuickProgressBar} interpretation */
	public static class Interpreted extends QuickValueWidget.Interpreted.Abstract<Integer, QuickProgressBar> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> theMaximum;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theText;

		/**
		 * @param definition The definition to interpret
		 * @param parent The parent element for the widget
		 */
		protected Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		/** @return The maximum value for the progress bar */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> getMaximum() {
			return theMaximum;
		}

		/** @return The text to display in the progress bar */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getText() {
			return theText;
		}

		@Override
		protected ModelInstanceType<SettableValue<?>, SettableValue<Integer>> getTargetType() {
			return ModelTypes.Value.INT;
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			super.doUpdate(env);
			theMaximum = interpret(getDefinition().getMaximum(), ModelTypes.Value.INT);
			theText = interpret(getDefinition().getText(), ModelTypes.Value.STRING);
		}

		@Override
		public QuickProgressBar create() {
			return new QuickProgressBar(getIdentity());
		}
	}

	private ModelValueInstantiator<SettableValue<Integer>> theMaximumInstantiator;
	private ModelValueInstantiator<SettableValue<String>> theTextInstantiator;

	private SettableValue<SettableValue<Integer>> theMaximum;
	private SettableValue<SettableValue<String>> theText;

	/** @param id The element ID for this widget */
	protected QuickProgressBar(Object id) {
		super(id);
		theMaximum = SettableValue.<SettableValue<Integer>> build().build();
		theText = SettableValue.<SettableValue<String>> build().build();
	}

	/** @return The maximum value for the progress bar */
	public SettableValue<Integer> getMaximum() {
		return SettableValue.flatten(theMaximum);
	}

	/** @return The text to display in the progress bar */
	public SettableValue<String> getText() {
		return SettableValue.flatten(theText);
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);

		Interpreted myInterpreted = (Interpreted) interpreted;
		theMaximumInstantiator = myInterpreted.getMaximum().instantiate();
		theTextInstantiator = myInterpreted.getText() == null ? null : myInterpreted.getText().instantiate();
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();

		theMaximumInstantiator.instantiate();
		if (theTextInstantiator != null)
			theTextInstantiator.instantiate();
	}

	@Override
	protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		super.doInstantiate(myModels);

		theMaximum.set(theMaximumInstantiator.get(myModels), null);
		theText.set(theTextInstantiator == null ? null : theTextInstantiator.get(myModels), null);
	}

	@Override
	public QuickProgressBar copy(ExElement parent) {
		QuickProgressBar copy = (QuickProgressBar) super.copy(parent);

		copy.theMaximum = SettableValue.<SettableValue<Integer>> build().build();
		copy.theText = SettableValue.<SettableValue<String>> build().build();

		return copy;
	}
}
