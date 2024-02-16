package org.observe.quick.base;

import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.qommons.config.QonfigElementOrAddOn;

/** A single radio button representing and providing control over a boolean value */
public class QuickRadioButton extends QuickCheckBox {
	/** The XML name of this element */
	public static final String RADIO_BUTTON = "radio-button";

	/** {@link QuickRadioButton} definition */
	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = RADIO_BUTTON,
		interpretation = Interpreted.class,
		instance = QuickRadioButton.class)
	public static class Def extends QuickCheckBox.Def<QuickRadioButton> {
		/**
		 * @param parent The parent element of the widget
		 * @param type The Qonfig type of the widget
		 */
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}
	}

	/** {@link QuickRadioButton} interpretation */
	public static class Interpreted extends QuickCheckBox.Interpreted<QuickRadioButton> {
		/**
		 * @param definition The definition to interpret
		 * @param parent The parent element for the widget
		 */
		protected Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		@Override
		public QuickRadioButton create() {
			return new QuickRadioButton(getIdentity());
		}
	}

	/** @param id The element ID for this widget */
	protected QuickRadioButton(Object id) {
		super(id);
	}

	@Override
	public QuickRadioButton copy(ExElement parent) {
		return (QuickRadioButton) super.copy(parent);
	}
}
