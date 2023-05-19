package org.observe.quick.base;

import javax.swing.Icon;

import org.observe.ObservableAction;
import org.observe.SettableValue;
import org.observe.expresso.CompiledExpression;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.quick.QuickElement;
import org.observe.quick.QuickStyledElement;
import org.observe.quick.QuickWidget;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.ex.ExFunction;

import com.google.common.reflect.TypeToken;

public class QuickButton extends QuickWidget.Abstract {
	public static class Def<B extends QuickButton> extends QuickWidget.Def.Abstract<B> {
		private CompiledExpression theText;
		private CompiledExpression theIcon;
		private CompiledExpression theAction;

		public Def(QuickElement.Def<?> parent, QonfigElement element) {
			super(parent, element);
		}

		public CompiledExpression getText() {
			return theText;
		}

		public CompiledExpression getIcon() {
			return theIcon;
		}

		public CompiledExpression getAction() {
			return theAction;
		}

		@Override
		public void update(ExpressoQIS session) throws QonfigInterpretationException {
			super.update(session);
			theText = session.getValueExpression();
			theIcon = session.getAttributeExpression("icon");
			theAction = session.getAttributeExpression("action");
		}

		@Override
		public Interpreted<? extends B> interpret(QuickElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}
	}

	public static class Interpreted<B extends QuickButton> extends QuickWidget.Interpreted.Abstract<B> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theText;
		private ExFunction<ModelSetInstance, SettableValue<Icon>, ModelInstantiationException> theIcon;
		private InterpretedValueSynth<ObservableAction<?>, ObservableAction<?>> theAction;

		public Interpreted(Def<? super B> definition, QuickElement.Interpreted<?> parent) {
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

		public ExFunction<ModelSetInstance, SettableValue<Icon>, ModelInstantiationException> getIcon() {
			return theIcon;
		}

		public InterpretedValueSynth<ObservableAction<?>, ObservableAction<?>> getAction() {
			return theAction;
		}

		@Override
		public void update(QuickStyledElement.QuickInterpretationCache cache) throws ExpressoInterpretationException {
			super.update(cache);
			theText = getDefinition().getText() == null ? null : getDefinition().getText().evaluate(ModelTypes.Value.STRING).interpret();
			theIcon = getDefinition().getIcon() == null ? null
				: QuickBaseInterpretation.evaluateIcon(getDefinition().getIcon(), getDefinition().getExpressoEnv(),
					getDefinition().getCallingClass());
			theAction = getDefinition().getAction().evaluate(ModelTypes.Action.any()).interpret();
		}

		@Override
		public B create(QuickElement parent) {
			return (B) new QuickButton(this, parent);
		}
	}

	private SettableValue<String> theText;
	private SettableValue<Icon> theIcon;
	private ObservableAction<?> theAction;

	public QuickButton(Interpreted<?> interpreted, QuickElement parent) {
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
	public void update(QuickElement.Interpreted<?> interpreted, ModelSetInstance models) throws ModelInstantiationException {
		super.update(interpreted, models);
		QuickButton.Interpreted<?> myInterpreted = (QuickButton.Interpreted<?>) interpreted;
		theText = myInterpreted.getText() == null ? null : myInterpreted.getText().get(getModels());
		theIcon = myInterpreted.getIcon() == null ? null : myInterpreted.getIcon().apply(getModels());
		theAction = myInterpreted.getAction().get(getModels());
	}
}
