package org.observe.quick.base;

import org.observe.ObservableAction;
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
import org.observe.quick.QuickWidget;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

/** Simple widget that performs an action when clicked */
public class QuickButton extends QuickWidget.Abstract {
	/** The XML name of this element */
	public static final String BUTTON = "button";

	/**
	 * {@link QuickButton} definition
	 *
	 * @param <B> The sub-type of button to create
	 */
	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = BUTTON,
		interpretation = Interpreted.class,
		instance = QuickButton.class)
	public static class Def<B extends QuickButton> extends QuickWidget.Def.Abstract<B> {
		private CompiledExpression theText;
		private CompiledExpression theAction;

		/**
		 * @param parent The parent element of the widget
		 * @param type The Qonfig type of the widget
		 */
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		/** @return The text to display for the button */
		@QonfigAttributeGetter
		public CompiledExpression getText() {
			return theText;
		}

		/** @return The action to perform when clicked */
		@QonfigAttributeGetter("action")
		public CompiledExpression getAction() {
			return theAction;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));
			theText = getValueExpression(session);
			theAction = getAttributeExpression("action", session);
		}

		@Override
		public Interpreted<? extends B> interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}
	}

	/**
	 * {@link QuickButton} interpretation
	 *
	 * @param <B> The sub-type of button to create
	 */
	public static class Interpreted<B extends QuickButton> extends QuickWidget.Interpreted.Abstract<B> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theText;
		private InterpretedValueSynth<ObservableAction, ObservableAction> theAction;

		/**
		 * @param definition The definition to interpret
		 * @param parent The parent element for the widget
		 */
		protected Interpreted(Def<? super B> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def<? super B> getDefinition() {
			return (Def<? super B>) super.getDefinition();
		}

		/** @return The text to display for the button */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getText() {
			return theText;
		}

		/** @return The action to perform when clicked */
		public InterpretedValueSynth<ObservableAction, ObservableAction> getAction() {
			return theAction;
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			super.doUpdate(env);
			theText = interpret(getDefinition().getText(), ModelTypes.Value.STRING);
			theAction = interpret(getDefinition().getAction(), ModelTypes.Action.instance());
		}

		@Override
		public B create() {
			return (B) new QuickButton(getIdentity());
		}
	}

	private ModelValueInstantiator<SettableValue<String>> theTextInstantiator;
	private ModelValueInstantiator<ObservableAction> theActionInstantiator;

	private SettableValue<String> theText;
	private ObservableAction theAction;

	/** @param id The element ID for this widget */
	protected QuickButton(Object id) {
		super(id);
	}

	/** @return The text to display for the button */
	public SettableValue<String> getText() {
		return theText;
	}

	/** @return The action to perform when clicked */
	public ObservableAction getAction() {
		return theAction;
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);
		QuickButton.Interpreted<?> myInterpreted = (QuickButton.Interpreted<?>) interpreted;
		theTextInstantiator = myInterpreted.getText() == null ? null : myInterpreted.getText().instantiate();
		theActionInstantiator = myInterpreted.getAction().instantiate();
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();

		if (theTextInstantiator != null)
			theTextInstantiator.instantiate();
		theActionInstantiator.instantiate();
	}

	@Override
	protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		super.doInstantiate(myModels);
		theText = theTextInstantiator == null ? null : theTextInstantiator.get(myModels);
		theAction = theActionInstantiator.get(myModels);
	}
}
