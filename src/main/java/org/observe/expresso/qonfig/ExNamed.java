package org.observe.expresso.qonfig;

import org.qommons.Named;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;

public class ExNamed extends ExAddOn.Abstract<ExElement> implements Named {
	@ExElementTraceable(toolkit = ExpressoSessionImplV0_1.CORE,
		qonfigType = "named",
		interpretation = Interpreted.class,
		instance = ExNamed.class)
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
		public Class<ExNamed> getInstanceType() {
			return ExNamed.class;
		}

		@Override
		public ExNamed create(ExElement element) {
			return new ExNamed(element);
		}
	}

	private String theName;

	public ExNamed(ExElement element) {
		super(element);
	}

	@Override
	public Class<Interpreted> getInterpretationType() {
		return (Class<Interpreted>) (Class<?>) Interpreted.class;
	}

	@Override
	public String getName() {
		return theName;
	}

	@Override
	public void update(ExAddOn.Interpreted<?, ?> interpreted) {
		super.update(interpreted);
		Interpreted myInterpreted = (Interpreted) interpreted;
		theName = myInterpreted.getDefinition().getName();
	}
}
