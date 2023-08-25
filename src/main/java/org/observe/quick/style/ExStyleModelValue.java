package org.observe.quick.style;

import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementModelValue;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.ExtModelValueElement;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;

public class ExStyleModelValue extends ExElementModelValue {
	@ExElementTraceable(toolkit = QuickStyleInterpretation.STYLE, qonfigType = "style-model-value", interpretation = Interpreted.class)
	public static class Def<AO extends ExStyleModelValue> extends ExElementModelValue.Def<AO> {
		private int thePriority;

		public Def(QonfigAddOn type, ExElement.Def<? extends ExtModelValueElement<?, ?>> element) {
			super(type, element);
		}

		@QonfigAttributeGetter("priority")
		public int getPriority() {
			return thePriority;
		}

		@Override
		public void update(ExpressoQIS session, ExElement.Def<? extends ExtModelValueElement<?, ?>> element)
			throws QonfigInterpretationException {
			super.update(session.asElement("element-model-value"), element);
			thePriority = Integer.parseInt(session.getAttributeText("priority"));
		}

		@Override
		public Interpreted<AO> interpret(ExElement.Interpreted<? extends ExtModelValueElement<?, ?>> element) {
			return new Interpreted<>(this, element);
		}
	}

	protected static class Interpreted<AO extends ExStyleModelValue> extends ExElementModelValue.Interpreted<AO> {
		protected Interpreted(Def<? super AO> definition, ExElement.Interpreted<? extends ExtModelValueElement<?, ?>> element) {
			super(definition, element);
		}

		@Override
		public AO create(ExtModelValueElement<?, ?> element) {
			return (AO) new ExStyleModelValue(element);
		}
	}

	protected ExStyleModelValue(ExtModelValueElement<?, ?> element) {
		super(element);
	}
}
