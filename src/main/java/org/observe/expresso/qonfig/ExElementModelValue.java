package org.observe.expresso.qonfig;

import org.observe.expresso.qonfig.ElementTypeTraceability.SingleTypeTraceability;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigAttributeDef;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigMetadata;

public class ExElementModelValue extends ExAddOn.Abstract<ExtModelValueElement<?, ?>> {
	private static SingleTypeTraceability<ExtModelValueElement<?, ?>, ExElement.Interpreted<? extends ExtModelValueElement<?, ?>>, ExElement.Def<? extends ExtModelValueElement<?, ?>>> TRACEABILITY = ElementTypeTraceability//
		.<ExtModelValueElement<?, ?>, ExElementModelValue, Interpreted<?>, Def<?>> getAddOnTraceability(
			ExpressoSessionImplV0_1.TOOLKIT_NAME, ExpressoSessionImplV0_1.VERSION, "element-model-value", Def.class,
			Interpreted.class, ExElementModelValue.class);

	public static class Def<AO extends ExElementModelValue> extends ExAddOn.Def.Abstract<ExtModelValueElement<?, ?>, AO> {
		private ElementModelValue.Identity theElementValue;

		public Def(QonfigAddOn type, ExElement.Def<? extends ExtModelValueElement<?, ?>> element) {
			super(type, element);
		}

		@QonfigAttributeGetter("name-attribute")
		public QonfigAttributeDef getNameAttribute() {
			return theElementValue == null ? null : theElementValue.getNameAttribute();
		}

		@QonfigAttributeGetter("source-attribute")
		public QonfigAttributeDef getSourceAttribute() {
			return theElementValue == null ? null : theElementValue.getSourceAttribute();
		}

		@Override
		public void update(ExpressoQIS session, ExElement.Def<? extends ExtModelValueElement<?, ?>> element)
			throws QonfigInterpretationException {
			element.withTraceability(TRACEABILITY.validate(session.getFocusType(), element.reporting()));
			super.update(session, element);
			if (session.getElement().getDocument() instanceof QonfigMetadata) {
				QonfigElementOrAddOn owner = ((QonfigMetadata) session.getElement().getDocument()).getElement();
				theElementValue = session.getElementValueCache().getDynamicValues(session.getFocusType().getDeclarer(), owner, null)//
					.get(session.getAttributeText("name"));
			}
		}

		@Override
		public Interpreted<AO> interpret(ExElement.Interpreted<? extends ExtModelValueElement<?, ?>> element) {
			return new Interpreted<>(this, element);
		}
	}

	protected static class Interpreted<AO extends ExElementModelValue>
	extends ExAddOn.Interpreted.Abstract<ExtModelValueElement<?, ?>, AO> {
		protected Interpreted(Def<? super AO> definition, ExElement.Interpreted<? extends ExtModelValueElement<?, ?>> element) {
			super(definition, element);
		}

		@Override
		public AO create(ExtModelValueElement<?, ?> element) {
			return (AO) new ExElementModelValue(element);
		}
	}

	protected ExElementModelValue(ExtModelValueElement<?, ?> element) {
		super(element);
	}

	@Override
	public Class<Interpreted<?>> getInterpretationType() {
		return (Class<Interpreted<?>>) (Class<?>) Interpreted.class;
	}
}
