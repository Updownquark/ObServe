package org.observe.expresso.qonfig;

import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigAttributeDef;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigMetadata;

/** A model value specified under an &lt;element-model> model */
public class ExElementModelValue extends ExAddOn.Abstract<ExtModelValueElement<?>> {
	/** The XML name of this add-on */
	public static final String ELEMENT_MODEL_VALUE = "element-model-value";

	/**
	 * {@link ExElementModelValue} definition
	 *
	 * @param <AO> The sub-type of add-on to create
	 */
	@ExElementTraceable(toolkit = ExpressoSessionImplV0_1.CORE,
		qonfigType = ELEMENT_MODEL_VALUE,
		interpretation = Interpreted.class,
		instance = ExElementModelValue.class)
	public static class Def<AO extends ExElementModelValue> extends ExAddOn.Def.Abstract<ExtModelValueElement<?>, AO> {
		private ElementModelValue.Identity theElementValue;

		/**
		 * @param type The Qonfig type of this add-on
		 * @param element The model value element
		 */
		public Def(QonfigAddOn type, ExElement.Def<? extends ExtModelValueElement<?>> element) {
			super(type, element);
		}

		/** @return The name for the model value */
		@QonfigAttributeGetter("name-attribute")
		public QonfigAttributeDef getNameAttribute() {
			return theElementValue == null ? null : theElementValue.getNameAttribute();
		}

		/** @return The attribute that the name of the model value can be specified with */
		@QonfigAttributeGetter("source-attribute")
		public QonfigAttributeDef getSourceAttribute() {
			return theElementValue == null ? null : theElementValue.getSourceAttribute();
		}

		@Override
		public void update(ExpressoQIS session, ExElement.Def<? extends ExtModelValueElement<?>> element)
			throws QonfigInterpretationException {
			super.update(session, element);
			if (session.getElement().getDocument() instanceof QonfigMetadata) {
				QonfigElementOrAddOn owner = ((QonfigMetadata) session.getElement().getDocument()).getElement();
				theElementValue = session.getElementValueCache()
					.getDynamicValues(session.getFocusType().getDeclarer(), owner, null, element.reporting())//
					.get(session.getAttributeText("name"));
			}
		}

		@Override
		public Interpreted<AO> interpret(ExElement.Interpreted<?> element) {
			return new Interpreted<>(this, element);
		}
	}

	/**
	 * {@link ExElementModelValue} interpretation
	 *
	 * @param <AO> The sub-type of add-on to create
	 */
	protected static class Interpreted<AO extends ExElementModelValue> extends ExAddOn.Interpreted.Abstract<ExtModelValueElement<?>, AO> {
		/**
		 * @param definition The definition to interpret
		 * @param element The model value element
		 */
		protected Interpreted(Def<? super AO> definition, ExElement.Interpreted<?> element) {
			super(definition, element);
		}

		@Override
		public Class<AO> getInstanceType() {
			return (Class<AO>) ExElementModelValue.class;
		}

		@Override
		public AO create(ExtModelValueElement<?> element) {
			return (AO) new ExElementModelValue(element);
		}
	}

	/** @param element The model value element */
	protected ExElementModelValue(ExtModelValueElement<?> element) {
		super(element);
	}

	@Override
	public Class<Interpreted<?>> getInterpretationType() {
		return (Class<Interpreted<?>>) (Class<?>) Interpreted.class;
	}
}
