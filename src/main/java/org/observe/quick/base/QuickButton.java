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
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ElementTypeTraceability;
import org.observe.expresso.qonfig.ElementTypeTraceability.SingleTypeTraceability;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.quick.QuickWidget;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public class QuickButton extends QuickWidget.Abstract {
	public static final String BUTTON = "button";
	private static final SingleTypeTraceability<QuickButton, Interpreted<?>, Def<?>> TRACEABILITY = ElementTypeTraceability
		.getElementTraceability(QuickBaseInterpretation.NAME, QuickBaseInterpretation.VERSION, BUTTON, Def.class, Interpreted.class,
			QuickButton.class);

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
			withTraceability(TRACEABILITY.validate(session.getFocusType(), session.reporting()));
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
			theIcon = getDefinition().getIcon() == null ? null
				: QuickBaseInterpretation.evaluateIcon(getDefinition().getIcon(), env,
					getDefinition().getElement().getDocument().getLocation());
			theAction = getDefinition().getAction().interpret(ModelTypes.Action.any(), env);
		}

		@Override
		public B create(ExElement parent) {
			return (B) new QuickButton(this, parent);
		}
	}

	private SettableValue<String> theText;
	private SettableValue<Icon> theIcon;
	private ObservableAction<?> theAction;

	public QuickButton(Interpreted<?> interpreted, ExElement parent) {
		super(interpreted, parent);
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
	protected void updateModel(ExElement.Interpreted<?> interpreted, ModelSetInstance myModels) throws ModelInstantiationException {
		super.updateModel(interpreted, myModels);
		QuickButton.Interpreted<?> myInterpreted = (QuickButton.Interpreted<?>) interpreted;
		theText = myInterpreted.getText() == null ? null : myInterpreted.getText().get(myModels);
		theIcon = myInterpreted.getIcon() == null ? null : myInterpreted.getIcon().get(myModels);
		theAction = myInterpreted.getAction().get(myModels);
	}
}
