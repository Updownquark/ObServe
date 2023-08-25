package org.observe.quick.base;

import javax.swing.Icon;

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
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public class QuickButton extends QuickWidget.Abstract {
	public static final String BUTTON = "button";

	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = BUTTON,
		interpretation = Interpreted.class,
		instance = QuickButton.class)
	public static class Def<B extends QuickButton> extends QuickWidget.Def.Abstract<B> {
		private CompiledExpression theText;
		private CompiledExpression theIcon;
		private CompiledExpression theAction;

		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		@QonfigAttributeGetter
		public CompiledExpression getText() {
			return theText;
		}

		@QonfigAttributeGetter("icon")
		public CompiledExpression getIcon() {
			return theIcon;
		}

		@QonfigAttributeGetter("action")
		public CompiledExpression getAction() {
			return theAction;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));
			theText = session.getValueExpression();
			theIcon = session.getAttributeExpression("icon");
			theAction = session.getAttributeExpression("action");
		}

		@Override
		public Interpreted<? extends B> interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}
	}

	public static class Interpreted<B extends QuickButton> extends QuickWidget.Interpreted.Abstract<B> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theText;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Icon>> theIcon;
		private InterpretedValueSynth<ObservableAction<?>, ObservableAction<?>> theAction;

		public Interpreted(Def<? super B> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def<? super B> getDefinition() {
			return (Def<? super B>) super.getDefinition();
		}

		@Override
		public TypeToken<B> getWidgetType() {
			return (TypeToken<B>) TypeTokens.get().of(QuickButton.class);
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getText() {
			return theText;
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<Icon>> getIcon() {
			return theIcon;
		}

		public InterpretedValueSynth<ObservableAction<?>, ObservableAction<?>> getAction() {
			return theAction;
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			super.doUpdate(env);
			theText = getDefinition().getText() == null ? null : getDefinition().getText().interpret(ModelTypes.Value.STRING, env);
			theIcon = getDefinition().getIcon() == null ? null : QuickBaseInterpretation.evaluateIcon(getDefinition().getIcon(), env,
				getDefinition().getElement().getDocument().getLocation());
			theAction = getDefinition().getAction().interpret(ModelTypes.Action.any(), env);
		}

		@Override
		public B create() {
			return (B) new QuickButton(getIdentity());
		}
	}

	private ModelValueInstantiator<SettableValue<String>> theTextInstantiator;
	private ModelValueInstantiator<SettableValue<Icon>> theIconInstantiator;
	private ModelValueInstantiator<ObservableAction<?>> theActionInstantiator;

	private SettableValue<String> theText;
	private SettableValue<Icon> theIcon;
	private ObservableAction<?> theAction;

	public QuickButton(Object id) {
		super(id);
	}

	public SettableValue<String> getText() {
		return theText;
	}

	public SettableValue<Icon> getIcon() {
		return theIcon;
	}

	public ObservableAction<?> getAction() {
		return theAction;
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) {
		super.doUpdate(interpreted);
		QuickButton.Interpreted<?> myInterpreted = (QuickButton.Interpreted<?>) interpreted;
		theTextInstantiator = myInterpreted.getText() == null ? null : myInterpreted.getText().instantiate();
		theIconInstantiator = myInterpreted.getIcon() == null ? null : myInterpreted.getIcon().instantiate();
		theActionInstantiator = myInterpreted.getAction().instantiate();
	}

	@Override
	public void instantiated() {
		super.instantiated();

		if (theTextInstantiator != null)
			theTextInstantiator.instantiate();
		if (theIconInstantiator != null)
			theIconInstantiator.instantiate();
		theActionInstantiator.instantiate();
	}

	@Override
	protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		super.doInstantiate(myModels);
		theText = theTextInstantiator == null ? null : theTextInstantiator.get(myModels);
		theIcon = theIconInstantiator == null ? null : theIconInstantiator.get(myModels);
		theAction = theActionInstantiator.get(myModels);
	}
}
