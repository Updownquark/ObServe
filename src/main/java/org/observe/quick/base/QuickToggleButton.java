package org.observe.quick.base;

import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.qommons.config.QonfigElementOrAddOn;

/** A button that shows a depressed state when its value is true */
public class QuickToggleButton extends QuickCheckBox {
	/** The XML name of this element */
	public static final String TOGGLE_BUTTON = "toggle-button";

	/** {@link QuickToggleButton} definition */
	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = TOGGLE_BUTTON,
		interpretation = Interpreted.class,
		instance = QuickToggleButton.class)
	public static class Def extends QuickCheckBox.Def<QuickToggleButton> {
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

	/** {@link QuickToggleButton} interpretation */
	public static class Interpreted extends QuickCheckBox.Interpreted<QuickToggleButton> {
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
		public QuickToggleButton create() {
			return new QuickToggleButton(getIdentity());
		}
	}

	/** @param id The element ID for this widget */
	protected QuickToggleButton(Object id) {
		super(id);
	}

	@Override
	public QuickToggleButton copy(ExElement parent) {
		return (QuickToggleButton) super.copy(parent);
	}
}
