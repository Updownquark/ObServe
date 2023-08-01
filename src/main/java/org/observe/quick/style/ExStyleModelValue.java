package org.observe.quick.style;

import org.observe.expresso.qonfig.ElementTypeTraceability;
import org.observe.expresso.qonfig.ElementTypeTraceability.SingleTypeTraceability;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementModelValue;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.ExtModelValueElement;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;

public class ExStyleModelValue extends ExElementModelValue {
	private static SingleTypeTraceability<ExtModelValueElement<?, ?>, ExElement.Interpreted<? extends ExtModelValueElement<?, ?>>, ExElement.Def<? extends ExtModelValueElement<?, ?>>> TRACEABILITY = ElementTypeTraceability//
		.<ExtModelValueElement<?, ?>, ExStyleModelValue, Interpreted<?>, Def<?>> getAddOnTraceability(QuickStyleInterpretation.NAME,
			QuickStyleInterpretation.VERSION, "style-model-value", Def.class, Interpreted.class, ExStyleModelValue.class);

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
			element.withTraceability(TRACEABILITY.validate(session.getFocusType(), session.reporting()));
			super.update(session, element);
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
			return (AO) new ExStyleModelValue(this, element);
		}
	}

	protected ExStyleModelValue(Interpreted<?> interpreted, ExtModelValueElement<?, ?> element) {
		super(interpreted, element);
	}
}
