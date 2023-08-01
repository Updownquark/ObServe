package org.observe.expresso.qonfig;

import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ElementTypeTraceability.SingleTypeTraceability;
import org.qommons.Named;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;

public class ExNamed extends ExAddOn.Abstract<ExElement> implements Named {
	private static final SingleTypeTraceability<ExElement, ExElement.Interpreted<?>, ExElement.Def<?>> TRACEABILITY = ElementTypeTraceability
		.getAddOnTraceability(ExpressoSessionImplV0_1.TOOLKIT_NAME, ExpressoSessionImplV0_1.VERSION, "named", Def.class, Interpreted.class,
			ExNamed.class);

	public static class Def extends ExAddOn.Def.Abstract<ExElement, ExNamed> implements Named {
		private String theName;

		public Def(QonfigAddOn type, ExElement.Def<? extends ExElement> element) {
			super(type, element);
		}

		@Override
		@QonfigAttributeGetter("name")
		public String getName() {
			return theName;
		}

		@Override
		public void update(ExpressoQIS session, ExElement.Def<? extends ExElement> element) throws QonfigInterpretationException {
			element.withTraceability(TRACEABILITY.validate(getType(), element.reporting()));
			super.update(session, element);
			theName = session.getAttributeText("name");
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<? extends ExElement> element) {
			return new Interpreted(this, element);
		}
	}

	public static class Interpreted extends ExAddOn.Interpreted.Abstract<ExElement, ExNamed> {
		public Interpreted(Def definition, ExElement.Interpreted<? extends ExElement> element) {
			super(definition, element);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		@Override
		public ExNamed create(ExElement element) {
			return new ExNamed(this, element);
		}
	}

	private String theName;

	public ExNamed(Interpreted interpreted, ExElement element) {
		super(interpreted, element);
		theName = interpreted.getDefinition().getName();
	}

	@Override
	public String getName() {
		return theName;
	}

	@Override
	public void update(ExAddOn.Interpreted<?, ?> interpreted, ModelSetInstance models) throws ModelInstantiationException {
		super.update(interpreted, models);
		Interpreted myInterpreted = (Interpreted) interpreted;
		theName = myInterpreted.getDefinition().getName();
	}
}
