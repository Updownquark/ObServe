package org.observe.expresso.qonfig;

import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;

public class ExTyped extends ExAddOn.Abstract<ExElement> {
	public static final ExAddOn.AddOnAttributeGetter<ExElement, ExTyped, Interpreted, Def> VALUE_TYPE = ExAddOn.AddOnAttributeGetter
		.<ExElement, ExTyped, Interpreted, Def> of(Def.class, Def::getValueType, Interpreted.class, i -> i.getDefinition().getValueType(),
			ExTyped.class, ExTyped::getValueType, "The declared type of the variable");

	public static class Def extends ExAddOn.Def.Abstract<ExElement, ExTyped> {
		private Object theValueType;

		public Def(QonfigAddOn type, ExElement.Def<? extends ExElement> element) {
			super(type, element);
		}

		public Object getValueType() {
			return theValueType;
		}

		@Override
		public void update(ExpressoQIS session, ExElement.Def<? extends ExElement> element) throws QonfigInterpretationException {
			element.forAttribute(session.getAttributeDef(null, null, "type"), VALUE_TYPE);
			super.update(session, element);

			theValueType = session.get(ExpressoBaseV0_1.VALUE_TYPE_KEY);
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<? extends ExElement> element) {
			return new Interpreted(this, element);
		}
	}

	public static class Interpreted extends ExAddOn.Interpreted.Abstract<ExElement, ExTyped> {
		public Interpreted(Def definition, ExElement.Interpreted<? extends ExElement> element) {
			super(definition, element);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		@Override
		public ExTyped create(ExElement element) {
			return new ExTyped(this, element);
		}
	}

	private Object theValueType;

	public ExTyped(Interpreted interpreted, ExElement element) {
		super(interpreted, element);
	}

	public Object getValueType() {
		return theValueType;
	}

	@Override
	public void update(ExAddOn.Interpreted<?, ?> interpreted, ModelSetInstance models) throws ModelInstantiationException {
		super.update(interpreted, models);
		Interpreted myInterpreted = (Interpreted) interpreted;
		theValueType = myInterpreted.getDefinition().getValueType();
	}
}
