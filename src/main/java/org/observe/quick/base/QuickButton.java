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
	public static class Def extends QuickWidget.Def.Abstract<QuickButton> {
		private Class<?> theCallingClass;
		private CompiledExpression theText;
		private CompiledExpression theIcon;
		private CompiledExpression theAction;

		public Def(QuickElement.Def<?> parent, QonfigElement element) {
			super(parent, element);
		}

		public Class<?> getCallingClass() {
			return theCallingClass;
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
			theCallingClass = session.getWrapped().getInterpreter().getCallingClass();
			theText = session.getValueExpression();
			theIcon = session.getAttributeExpression("icon");
			theAction = session.getAttributeExpression("action");
		}

		@Override
		public Interpreted interpret(QuickElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}
	}

	public static class Interpreted extends QuickWidget.Interpreted.Abstract<QuickButton> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theText;
		private ExFunction<ModelSetInstance, SettableValue<Icon>, ModelInstantiationException> theIcon;
		private InterpretedValueSynth<ObservableAction<?>, ObservableAction<?>> theAction;

		public Interpreted(Def definition, QuickElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		@Override
		public TypeToken<QuickButton> getWidgetType() {
			return TypeTokens.get().of(QuickButton.class);
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
		public QuickButton create(QuickElement parent) {
			return new QuickButton(this, parent);
		}
	}

	private SettableValue<String> theText;
	private SettableValue<Icon> theIcon;
	private ObservableAction<?> theAction;

	public QuickButton(Interpreted interpreted, QuickElement parent) {
		super(interpreted, parent);
	}

	@Override
	public Interpreted getInterpreted() {
		return (Interpreted) super.getInterpreted();
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
	public void update(ModelSetInstance models) throws ModelInstantiationException {
		super.update(models);
		theText = getInterpreted().getText() == null ? null : getInterpreted().getText().get(getModels());
		theIcon = getInterpreted().getIcon() == null ? null : getInterpreted().getIcon().apply(getModels());
		theAction = getInterpreted().getAction().get(getModels());
	}
}
