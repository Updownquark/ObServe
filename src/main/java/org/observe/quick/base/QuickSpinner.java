package org.observe.quick.base;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
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
 * A text field with little up and down buttons next to it for adjusting a quantitative value
 *
 * @param <T> The type of the value
 */
public class QuickSpinner<T> extends QuickTextField<T> {
	/** The XML name of this element */
	public static final String SPINNER = "spinner";

	/** {@link QuickSpinner} definition */
	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = SPINNER,
		interpretation = Interpreted.class,
		instance = QuickSpinner.class)
	public static class Def extends QuickTextField.Def<QuickSpinner<?>> {
		private CompiledExpression thePrevious;
		private CompiledExpression theNext;

		/**
		 * @param parent The parent element of the widget
		 * @param type The Qonfig type of the widget
		 */
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		/** @return An expression that determines the previous value when the user presses down */
		@QonfigAttributeGetter("previous")
		public CompiledExpression getPrevious() {
			return thePrevious;
		}

		/** @return An expression that determines the next value when the user presses up */
		@QonfigAttributeGetter("next")
		public CompiledExpression getNext() {
			return theNext;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);

			thePrevious = getAttributeExpression("previous", session);
			theNext = getAttributeExpression("next", session);
		}

		@Override
		public Interpreted<?> interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}
	}

	/**
	 * {@link QuickSpinner} interpretation
	 *
	 * @param <T> The type of the value
	 */
	public static class Interpreted<T> extends QuickTextField.Interpreted<T, QuickSpinner<T>> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<T>> thePrevious;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<T>> theNext;

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

		/** @return An expression that determines the previous value when the user presses down */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getPrevious() {
			return thePrevious;
		}

		/** @return An expression that determines the next value when the user presses up */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getNext() {
			return theNext;
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			super.doUpdate(env);

			thePrevious = interpret(getDefinition().getPrevious(), ModelTypes.Value.forType(getValueType()));
			theNext = interpret(getDefinition().getNext(), ModelTypes.Value.forType(getValueType()));
		}

		@Override
		public QuickSpinner<T> create() {
			return new QuickSpinner<>(getIdentity());
		}
	}

	private ModelValueInstantiator<SettableValue<T>> thePreviousInstantiator;
	private ModelValueInstantiator<SettableValue<T>> theNextInstantiator;

	private SettableValue<T> thePrevious;
	private SettableValue<T> theNext;

	/** @param id The element ID for this widget */
	protected QuickSpinner(Object id) {
		super(id);
	}

	/** @return An expression that determines the previous value when the user presses down */
	public SettableValue<T> getPrevious() {
		return thePrevious;
	}

	/** @return An expression that determines the next value when the user presses up */
	public SettableValue<T> getNext() {
		return theNext;
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);

		Interpreted<T> myInterpreted = (Interpreted<T>) interpreted;
		thePreviousInstantiator = myInterpreted.getPrevious() == null ? null : myInterpreted.getPrevious().instantiate();
		theNextInstantiator = myInterpreted.getNext() == null ? null : myInterpreted.getNext().instantiate();
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();

		if (thePrevious != null)
			thePreviousInstantiator.instantiate();
		if (theNext != null)
			theNextInstantiator.instantiate();
	}

	@Override
	protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		super.doInstantiate(myModels);

		thePrevious = thePreviousInstantiator == null ? null : thePreviousInstantiator.get(myModels);
		theNext = theNextInstantiator == null ? null : theNextInstantiator.get(myModels);
	}

	@Override
	public QuickSpinner<T> copy(ExElement parent) {
		return (QuickSpinner<T>) super.copy(parent);
	}
}
