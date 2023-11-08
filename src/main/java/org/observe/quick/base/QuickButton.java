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

public class QuickButton extends QuickWidget.Abstract {
	public static final String BUTTON = "button";

	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = BUTTON,
		interpretation = Interpreted.class,
		instance = QuickButton.class)
	public static class Def<B extends QuickButton> extends QuickWidget.Def.Abstract<B> {
		private CompiledExpression theText;
		private CompiledExpression theAction;

		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		@QonfigAttributeGetter
		public CompiledExpression getText() {
			return theText;
		}

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

	public static class Interpreted<B extends QuickButton> extends QuickWidget.Interpreted.Abstract<B> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theText;
		private InterpretedValueSynth<ObservableAction, ObservableAction> theAction;

		public Interpreted(Def<? super B> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def<? super B> getDefinition() {
			return (Def<? super B>) super.getDefinition();
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getText() {
			return theText;
		}

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

	public QuickButton(Object id) {
		super(id);
	}

	public SettableValue<String> getText() {
		return theText;
	}

	public ObservableAction getAction() {
		return theAction;
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) {
		super.doUpdate(interpreted);
		QuickButton.Interpreted<?> myInterpreted = (QuickButton.Interpreted<?>) interpreted;
		theTextInstantiator = myInterpreted.getText() == null ? null : myInterpreted.getText().instantiate();
		theActionInstantiator = myInterpreted.getAction().instantiate();
	}

	@Override
	public void instantiated() {
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
