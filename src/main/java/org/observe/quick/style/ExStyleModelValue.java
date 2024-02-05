package org.observe.quick.style;

import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementModelValue;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.ExtModelValueElement;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;

/** Add on for a model value in the metadata for a styled element */
public class ExStyleModelValue extends ExElementModelValue {
	/** The XML name of this type */
	public static final String STYLE_MODEL_VALUE = "style-model-value";

	/**
	 * Definition for a {@link ExStyleModelValue}
	 *
	 * @param <AO> The sub-type of add-on to create
	 */
	@ExElementTraceable(toolkit = QuickStyleInterpretation.STYLE,
		qonfigType = STYLE_MODEL_VALUE,
		interpretation = Interpreted.class,
		instance = ExStyleModelValue.class)
	public static class Def<AO extends ExStyleModelValue> extends ExElementModelValue.Def<AO> {
		private int thePriority;

		/**
		 * @param type The Qonfig type of this add-on
		 * @param element The element to affect
		 */
		public Def(QonfigAddOn type, ExElement.Def<? extends ExtModelValueElement<?>> element) {
			super(type, element);
		}

		/** @return The priority of this model value in style expressions */
		@QonfigAttributeGetter("priority")
		public int getPriority() {
			return thePriority;
		}

		@Override
		public void update(ExpressoQIS session, ExElement.Def<? extends ExtModelValueElement<?>> element)
			throws QonfigInterpretationException {
			super.update(session.asElement("element-model-value"), element);
			thePriority = Integer.parseInt(session.getAttributeText("priority"));
		}

		@Override
		public Interpreted<AO> interpret(ExElement.Interpreted<?> element) {
			return new Interpreted<>(this, element);
		}
	}

	/**
	 * Interpretation for a {@link ExStyleModelValue}
	 *
	 * @param <AO> The sub-type of add-on to create
	 */
	protected static class Interpreted<AO extends ExStyleModelValue> extends ExElementModelValue.Interpreted<AO> {
		/**
		 * @param definition The definition to interpret
		 * @param element The element to affect
		 */
		protected Interpreted(Def<? super AO> definition, ExElement.Interpreted<?> element) {
			super(definition, element);
		}

		@Override
		public AO create(ExtModelValueElement<?> element) {
			return (AO) new ExStyleModelValue(element);
		}
	}

	/** @param element The model value element */
	protected ExStyleModelValue(ExtModelValueElement<?> element) {
		super(element);
	}
}
