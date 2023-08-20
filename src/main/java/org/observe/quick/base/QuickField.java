package org.observe.quick.base;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ExAddOn;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.quick.QuickWidget;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;

public class QuickField extends ExAddOn.Abstract<QuickWidget> {
	public static class Def extends ExAddOn.Def.Abstract<QuickWidget, QuickField> {
		private CompiledExpression theFieldLabel;
		private boolean isFill;

		public Def(QonfigAddOn type, QuickWidget.Def<?> element) {
			super(type, element);
		}

		public CompiledExpression getFieldLabel() {
			return theFieldLabel;
		}

		public boolean isFill() {
			return isFill;
		}

		@Override
		public void update(ExpressoQIS session, ExElement.Def<? extends QuickWidget> element) throws QonfigInterpretationException {
			super.update(session, element);
			theFieldLabel = session.getAttributeExpression("field-label");
			isFill = Boolean.TRUE.equals(session.getAttribute("fill", Boolean.class));
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<? extends QuickWidget> element) {
			return new Interpreted(this, (QuickWidget.Interpreted<?>) element);
		}
	}

	public static class Interpreted extends ExAddOn.Interpreted.Abstract<QuickWidget, QuickField> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theName;

		public Interpreted(Def definition, QuickWidget.Interpreted<?> element) {
			super(definition, element);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		@Override
		public QuickWidget.Interpreted<?> getElement() {
			return (QuickWidget.Interpreted<?>) super.getElement();
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getFieldLabel() {
			return theName;
		}

		@Override
		public void update(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			theName = getDefinition().getFieldLabel() == null ? null
				: getDefinition().getFieldLabel().interpret(ModelTypes.Value.STRING, env);
		}

		@Override
		public QuickField create(QuickWidget widget) {
			return new QuickField(this, widget);
		}
	}

	private SettableValue<String> theFieldLabel;

	public QuickField(Interpreted interpreted, QuickWidget widget) {
		super(interpreted, widget);
	}

	public SettableValue<String> getFieldLabel() {
		return theFieldLabel;
	}

	@Override
	public void update(ExAddOn.Interpreted<?, ?> interpreted, ModelSetInstance models) throws ModelInstantiationException {
		super.update(interpreted, models);
		QuickField.Interpreted myInterpreted = (QuickField.Interpreted) interpreted;
		theFieldLabel = myInterpreted.getFieldLabel() == null ? null : myInterpreted.getFieldLabel().instantiate().get(models);
	}
}
