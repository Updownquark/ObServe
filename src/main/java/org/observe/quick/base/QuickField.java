package org.observe.quick.base;

import org.observe.SettableValue;
import org.observe.expresso.CompiledExpression;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedModelSet;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.quick.QuickAddOn;
import org.observe.quick.QuickElement;
import org.observe.quick.QuickWidget;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;

public class QuickField extends QuickAddOn.Abstract<QuickWidget> {
	public static class Def extends QuickAddOn.Def.Abstract<QuickWidget, QuickField> {
		private CompiledExpression theFieldLabel;
		private boolean isFill;

		public Def(QonfigAddOn type, QuickElement.Def<? extends QuickWidget> element) {
			super(type, element);
		}

		public CompiledExpression getFieldLabel() {
			return theFieldLabel;
		}

		public boolean isFill() {
			return isFill;
		}

		@Override
		public void update(ExpressoQIS session) throws QonfigInterpretationException {
			theFieldLabel = session.getAttributeExpression("field-label");
			isFill = Boolean.TRUE.equals(session.getAttribute("fill", Boolean.class));
		}

		@Override
		public Interpreted interpret(QuickElement.Interpreted<? extends QuickWidget> element) {
			return new Interpreted(this, (QuickWidget.Interpreted<?>) element);
		}
	}

	public static class Interpreted extends QuickAddOn.Interpreted.Abstract<QuickWidget, QuickField> {
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
		public void update(InterpretedModelSet models) throws ExpressoInterpretationException {
			theName = getDefinition().getFieldLabel() == null ? null
				: getDefinition().getFieldLabel().evaluate(ModelTypes.Value.STRING).interpret();
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
	public void update(QuickAddOn.Interpreted<?, ?> interpreted, ModelSetInstance models) throws ModelInstantiationException {
		super.update(interpreted, models);
		QuickField.Interpreted myInterpreted = (QuickField.Interpreted) interpreted;
		theFieldLabel = myInterpreted.getFieldLabel() == null ? null : myInterpreted.getFieldLabel().get(models);
	}
}
