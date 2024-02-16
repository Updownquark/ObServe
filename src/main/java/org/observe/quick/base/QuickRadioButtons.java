package org.observe.quick.base;

import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.qommons.config.QonfigElementOrAddOn;

/**
 * A horizontal set of radio buttons, each representing a value that may be selected from a collection
 *
 * @param <T> The type of the value to select
 */
public class QuickRadioButtons<T> extends CollectionSelectorWidget<T> {
	/** The XML name of this element */
	public static final String RADIO_BUTTONS = "radio-buttons";

	/** {@link QuickRadioButton} definition */
	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = RADIO_BUTTONS,
		interpretation = Interpreted.class,
		instance = QuickRadioButtons.class)
	public static class Def extends CollectionSelectorWidget.Def<QuickRadioButtons<?>> {
		/**
		 * @param parent The parent element of the widget
		 * @param type The Qonfig type of the widget
		 */
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		@Override
		public Interpreted<?> interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}
	}

	/**
	 * {@link QuickRadioButton} interpretation
	 *
	 * @param <T> The type of the value to select
	 */
	public static class Interpreted<T> extends CollectionSelectorWidget.Interpreted<T, QuickRadioButtons<T>> {
		/**
		 * @param definition The definition to interpret
		 * @param parent The parent element for the widget
		 */
		public Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		@Override
		public QuickRadioButtons<T> create() {
			return new QuickRadioButtons<>(getIdentity());
		}
	}

	/** @param id The element ID for this widget */
	protected QuickRadioButtons(Object id) {
		super(id);
	}

	@Override
	protected QuickRadioButtons<T> clone() {
		return (QuickRadioButtons<T>) super.clone();
	}
}
