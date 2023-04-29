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
		private CompiledExpression theName;
		private boolean isFill;

		public Def(QonfigAddOn type, QuickElement.Def<? extends QuickWidget> element) {
			super(type, element);
		}

		public CompiledExpression getName() {
			return theName;
		}

		public boolean isFill() {
			return isFill;
		}

		@Override
		public Def update(ExpressoQIS session) throws QonfigInterpretationException {
			theName = session.getAttributeExpression("field-name");
			isFill = Boolean.TRUE.equals(session.getAttribute("fill", Boolean.class));
			return this;
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

		public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getName() {
			return theName;
		}

		@Override
		public Interpreted update(InterpretedModelSet models) throws ExpressoInterpretationException {
			theName = getDefinition().getName() == null ? null : getDefinition().getName().evaluate(ModelTypes.Value.STRING).interpret();
			return this;
		}

		@Override
		public QuickField create(QuickWidget widget) {
			return new QuickField(this, widget);
		}
	}

	private SettableValue<String> theName;

	public QuickField(Interpreted interpreted, QuickWidget widget) {
		super(interpreted, widget);
	}

	@Override
	public Interpreted getInterpreted() {
		return (Interpreted) super.getInterpreted();
	}

	@Override
	public QuickWidget getElement() {
		return super.getElement();
	}

	public SettableValue<String> getName() {
		return theName;
	}

	@Override
	public QuickField update(ModelSetInstance models) throws ModelInstantiationException {
		theName = getInterpreted().getName() == null ? null : getInterpreted().getName().get(models);
		return this;
	}
}
