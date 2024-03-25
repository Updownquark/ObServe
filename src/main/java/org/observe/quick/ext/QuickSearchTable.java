package org.observe.quick.ext;

import org.observe.expresso.qonfig.ExElement;
import org.observe.quick.base.QuickTable;
import org.qommons.config.QonfigElementOrAddOn;

/**
 * A table extension with a search bar and other added capabilities
 *
 * @param <R> The type of rows in the table
 */
public class QuickSearchTable<R> extends QuickTable<R> {
	/** The XML name of this Qonfig type */
	public static final String SEARCH_TABLE = "search-table";

	/**
	 * {@link QuickSearchTable} definition
	 *
	 * @param <T> The sub-type of table to create
	 */
	public static class Def<T extends QuickSearchTable<?>> extends QuickTable.Def<T> {
		/**
		 * @param parent The parent element of the widget
		 * @param type The Qonfig type of the widget
		 */
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		@Override
		public Interpreted<?, T> interpret(ExElement.Interpreted<?> parent) {
			return (Interpreted<?, T>) new Interpreted<>((Def<QuickSearchTable<Object>>) this, parent);
		}
	}

	/**
	 * {@link QuickSearchTable} interpretation
	 *
	 * @param <R> The type of rows in the table
	 * @param <T> The sub-type of table to create
	 */
	public static class Interpreted<R, T extends QuickSearchTable<R>> extends QuickTable.Interpreted<R, T> {
		/**
		 * @param definition The definition to interpret
		 * @param parent The parent element for the widget
		 */
		protected Interpreted(Def<T> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public T create() {
			return (T) new QuickSearchTable<R>(getIdentity());
		}
	}

	/** @param id The element ID for this widget */
	protected QuickSearchTable(Object id) {
		super(id);
	}
}
