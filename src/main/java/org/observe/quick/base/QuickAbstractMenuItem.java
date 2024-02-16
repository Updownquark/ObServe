package org.observe.quick.base;

import org.observe.expresso.qonfig.ExElement;
import org.qommons.config.QonfigElementOrAddOn;

/**
 * Abstract class representing a Quick menu item
 *
 * @param <T> The type of value for the menu item to represent
 */
public abstract class QuickAbstractMenuItem<T> extends QuickLabel<T> {
	/** The XML name of this element */
	public static final String ABST_MENU_ITEM = "abst-menu-item";

	/**
	 * {@link QuickAbstractMenuItem} definition
	 *
	 * @param <MI> The sub-type of menu item to create
	 */
	public static abstract class Def<MI extends QuickAbstractMenuItem<?>> extends QuickLabel.Def<MI> {
		/**
		 * @param parent The parent element of the menu item
		 * @param type The Qonfig type of the menu item
		 */
		protected Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		@Override
		public abstract Interpreted<?, ? extends MI> interpret(ExElement.Interpreted<?> parent);
	}

	/**
	 * {@link QuickAbstractMenuItem} interpretation
	 *
	 * @param <T> The type of value for the menu item to represent
	 * @param <MI> The sub-type of menu item to create
	 */
	public static abstract class Interpreted<T, MI extends QuickAbstractMenuItem<T>> extends QuickLabel.Interpreted<T, MI> {
		/**
		 * @param definition The definition to interpret
		 * @param parent The parent element for the menu item
		 */
		protected Interpreted(Def<? super MI> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def<? super MI> getDefinition() {
			return (Def<? super MI>) super.getDefinition();
		}

		@Override
		public abstract MI create();
	}

	/** @param id The element ID for this menu item */
	protected QuickAbstractMenuItem(Object id) {
		super(id);
	}

	@Override
	public QuickAbstractMenuItem<T> copy(ExElement parent) {
		return (QuickAbstractMenuItem<T>) super.copy(parent);
	}
}
