package org.observe.quick.base;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ExAddOn;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.quick.QuickWidget;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;

public class QuickField extends ExAddOn.Abstract<QuickWidget> {
	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = "field",
		interpretation = Interpreted.class,
		instance = QuickField.class)
	public static class Def extends ExAddOn.Def.Abstract<QuickWidget, QuickField> {
		private CompiledExpression theFieldLabel;
		private boolean isFill;

		public Def(QonfigAddOn type, QuickWidget.Def<?> element) {
			super(type, element);
		}

		@QonfigAttributeGetter("field-label")
		public CompiledExpression getFieldLabel() {
			return theFieldLabel;
		}

		@QonfigAttributeGetter("fill")
		public boolean isFill() {
			return isFill;
		}

		@Override
		public void update(ExpressoQIS session, ExElement.Def<? extends QuickWidget> element) throws QonfigInterpretationException {
			super.update(session, element);
			theFieldLabel = element.getAttributeExpression("field-label", session);
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
		public Class<QuickField> getInstanceType() {
			return QuickField.class;
		}

		@Override
		public QuickField create(QuickWidget widget) {
			return new QuickField(widget);
		}
	}

	private ModelValueInstantiator<SettableValue<String>> theFieldLabelInstantiator;
	private SettableValue<String> theFieldLabel;

	public QuickField(QuickWidget widget) {
		super(widget);
	}

	public SettableValue<String> getFieldLabel() {
		return theFieldLabel;
	}

	@Override
	public Class<Interpreted> getInterpretationType() {
		return Interpreted.class;
	}

	@Override
	public void update(ExAddOn.Interpreted<?, ?> interpreted) {
		super.update(interpreted);
		QuickField.Interpreted myInterpreted = (QuickField.Interpreted) interpreted;
		theFieldLabelInstantiator = myInterpreted.getFieldLabel() == null ? null : myInterpreted.getFieldLabel().instantiate();
	}

	@Override
	public void instantiate(ModelSetInstance models) throws ModelInstantiationException {
		super.instantiate(models);
		theFieldLabel = theFieldLabelInstantiator == null ? null : theFieldLabelInstantiator.get(models);
	}
}
