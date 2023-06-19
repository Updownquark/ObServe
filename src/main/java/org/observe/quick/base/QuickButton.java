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
import org.observe.expresso.qonfig.ExElement;
import org.observe.quick.QuickStyledElement;
import org.observe.quick.QuickWidget;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.ex.ExFunction;

import com.google.common.reflect.TypeToken;

public class QuickButton extends QuickWidget.Abstract {
	public static final String BUTTON = "button";

	public static final ExElement.AttributeValueGetter.Expression<QuickButton, Interpreted<?>, Def<?>, ObservableAction<?>, ObservableAction<?>> ACTION = ExElement.AttributeValueGetter
		.<QuickButton, Interpreted<?>, Def<?>, ObservableAction<?>, ObservableAction<?>> ofX(Def::getAction, Interpreted::getAction,
			QuickButton::getAction, "The action to perform when the button is pressed");
	public static final ExElement.AttributeValueGetter<QuickButton, Interpreted<?>, Def<?>> ICON = ExElement.AttributeValueGetter
		.<QuickButton, Interpreted<?>, Def<?>> of(Def::getIcon, Interpreted::getIcon, QuickButton::getIcon,
			"The icon to display for the button");
	public static final ExElement.AttributeValueGetter<QuickButton, Interpreted<?>, Def<?>> VALUE = ExElement.AttributeValueGetter
		.<QuickButton, Interpreted<?>, Def<?>> of(Def::getText, Interpreted::getText, QuickButton::getText,
			"The text to display for the button");

	public static class Def<B extends QuickButton> extends QuickWidget.Def.Abstract<B> {
		private CompiledExpression theText;
		private CompiledExpression theIcon;
		private CompiledExpression theAction;

		public Def(ExElement.Def<?> parent, QonfigElement element) {
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
			ExElement.checkElement(session.getFocusType(), QuickBaseInterpretation.NAME, QuickBaseInterpretation.VERSION, BUTTON);
			forAttribute(session.getAttributeDef(null, null, "action"), ACTION);
			forAttribute(session.getAttributeDef(null, null, "icon"), ICON);
			forValue(VALUE);
			super.update(session.asElement(session.getFocusType().getSuperElement()));
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
		private ExFunction<ModelSetInstance, SettableValue<Icon>, ModelInstantiationException> theIcon;
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
		theIcon = myInterpreted.getIcon() == null ? null : myInterpreted.getIcon().apply(myModels);
		theAction = myInterpreted.getAction().get(myModels);
	}
}
