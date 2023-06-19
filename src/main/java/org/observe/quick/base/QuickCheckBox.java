package org.observe.quick.base;

import org.observe.SettableValue;
import org.observe.expresso.CompiledExpression;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ExElement;
import org.observe.quick.QuickValueWidget;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public class QuickCheckBox extends QuickValueWidget.Abstract<Boolean> {
	public static final String CHECK_BOX = "check-box";

	public static final ExElement.AttributeValueGetter.Expression<QuickCheckBox, Interpreted, Def, SettableValue<?>, SettableValue<String>> CHECK_BOX_TEXT = ExElement.AttributeValueGetter
		.<QuickCheckBox, Interpreted, Def, SettableValue<?>, SettableValue<String>> ofX(Def::getText, Interpreted::getText,
			QuickCheckBox::getText, "The text to display for the check box");

	public static class Def extends QuickValueWidget.Def.Abstract<Boolean, QuickCheckBox> {
		private CompiledExpression theText;

		public Def(ExElement.Def<?> parent, QonfigElement element) {
			super(parent, element);
		}

		public CompiledExpression getText() {
			return theText;
		}

		@Override
		public void update(ExpressoQIS session) throws QonfigInterpretationException {
			ExElement.checkElement(session.getFocusType(), QuickBaseInterpretation.NAME, QuickBaseInterpretation.VERSION, CHECK_BOX);
			forValue(CHECK_BOX_TEXT);
			super.update(session.asElement(session.getFocusType().getSuperElement()));
			theText = session.getValueExpression();
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}
	}

	public static class Interpreted extends QuickValueWidget.Interpreted.Abstract<Boolean, QuickCheckBox> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theText;

		public Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getText() {
			return theText;
		}

		@Override
		public TypeToken<QuickCheckBox> getWidgetType() {
			return TypeTokens.get().of(QuickCheckBox.class);
		}

		@Override
		public void update(QuickInterpretationCache cache) throws ExpressoInterpretationException {
			super.update(cache);
			theText = getDefinition().getText() == null ? null : getDefinition().getText().evaluate(ModelTypes.Value.STRING).interpret();
		}

		@Override
		public QuickCheckBox create(ExElement parent) {
			return new QuickCheckBox(this, parent);
		}
	}

	private final SettableValue<SettableValue<String>> theText;

	public QuickCheckBox(Interpreted interpreted, ExElement parent) {
		super(interpreted, parent);
		theText = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<String>> parameterized(String.class))
			.build();
	}

	public SettableValue<String> getText() {
		return SettableValue.flatten(theText);
	}

	@Override
	protected void updateModel(org.observe.expresso.qonfig.ExElement.Interpreted<?> interpreted, ModelSetInstance myModels)
		throws ModelInstantiationException {
		super.updateModel(interpreted, myModels);
		Interpreted myInterpreted = (Interpreted) interpreted;
		theText.set(myInterpreted.getText() == null ? null : myInterpreted.getText().get(myModels), null);
	}
}
