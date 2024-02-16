package org.observe.quick.base;

import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.qommons.config.QonfigElementOrAddOn;

/**
 * A horizontal set of toggle buttons, each representing a value that may be selected from a collection
 *
 * @param <T> The type of the value to select
 */
public class QuickToggleButtons<T> extends CollectionSelectorWidget<T> {
	/** The XML name of this element */
	public static final String TOGGLE_BUTTONS = "toggle-buttons";

	/** {@link QuickToggleButtons} definition */
	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = TOGGLE_BUTTONS,
		interpretation = Interpreted.class,
		instance = QuickToggleButtons.class)
	public static class Def extends CollectionSelectorWidget.Def<QuickToggleButtons<?>> {
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
	 * {@link QuickToggleButtons}interpretation
	 *
	 * @param <T> The type of the value to select
	 */
	public static class Interpreted<T> extends CollectionSelectorWidget.Interpreted<T, QuickToggleButtons<T>> {
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
		public QuickToggleButtons<T> create() {
			return new QuickToggleButtons<>(getIdentity());
		}
	}

	/** @param id The element ID for this widget */
	protected QuickToggleButtons(Object id) {
		super(id);
	}

	@Override
	protected QuickToggleButtons<T> clone() {
		return (QuickToggleButtons<T>) super.clone();
	}
}
