package org.observe.quick.ext;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.expresso.qonfig.QonfigChildGetter;
import org.observe.quick.base.QuickTable;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

/**
 * A table extension with a search bar and other added capabilities
 *
 * @param <R> The type of rows in the table
 */
public class QuickSuperTable<R> extends QuickTable<R> {
	/** The XML name of this Qonfig type */
	public static final String SUPER_TABLE = "super-table";
	/** The XML name of the {@link AdaptiveHeight} type */
	public static final String ADAPTIVE_HEIGHT = "adaptive-height";

	/**
	 * {@link QuickSuperTable} definition
	 *
	 * @param <T> The sub-type of table to create
	 */
	@ExElementTraceable(toolkit = QuickXInterpretation.X,
		qonfigType = SUPER_TABLE,
		interpretation = Interpreted.class,
		instance = QuickSuperTable.class)
	public static class Def<T extends QuickSuperTable<?>> extends QuickTable.Def<T> {
		private boolean isSearchable;
		private String theItemName;
		private CompiledExpression theDisplayed;
		private AdaptiveHeight.Def theAdaptiveHeight;

		/**
		 * @param parent The parent element of the widget
		 * @param type The Qonfig type of the widget
		 */
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		/** @return Whether a text field should be available to the user for filtering and sorting the table data */
		@QonfigAttributeGetter("searchable")
		public boolean isSearchable() {
			return isSearchable;
		}

		/** @return The item name for rows in the table. May affect some kinds of auto-generated UI text. */
		@QonfigAttributeGetter("item-name")
		public String getItemName() {
			return theItemName;
		}

		/**
		 * @return A collection of the same type as this table's rows that, if specified, will contain the list of row values actually
		 *         displayed to the user in the table
		 */
		@QonfigAttributeGetter("displayed")
		public CompiledExpression getDisplayed() {
			return theDisplayed;
		}

		/** @return Determines the size of the table from the number of rows displayed */
		@QonfigChildGetter("adaptive-height")
		public AdaptiveHeight.Def getAdaptiveHeight() {
			return theAdaptiveHeight;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);

			isSearchable = session.getAttribute("searchable", boolean.class);
			theItemName = session.getAttribute("item-name", String.class);
			theDisplayed = getAttributeExpression("displayed", session);
			theAdaptiveHeight = syncChild(AdaptiveHeight.Def.class, theAdaptiveHeight, session, "adaptive-height");
		}

		@Override
		public Interpreted<?, T> interpret(ExElement.Interpreted<?> parent) {
			return (Interpreted<?, T>) new Interpreted<>((Def<QuickSuperTable<Object>>) this, parent);
		}
	}

	/**
	 * {@link QuickSuperTable} interpretation
	 *
	 * @param <R> The type of rows in the table
	 * @param <T> The sub-type of table to create
	 */
	public static class Interpreted<R, T extends QuickSuperTable<R>> extends QuickTable.Interpreted<R, T> {
		private InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<R>> theDisplayed;
		private AdaptiveHeight.Interpreted theAdaptiveHeight;

		/**
		 * @param definition The definition to interpret
		 * @param parent The parent element for the widget
		 */
		protected Interpreted(Def<T> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def<T> getDefinition() {
			return (Def<T>) super.getDefinition();
		}

		/**
		 * @return A collection of the same type as this table's rows that, if specified, will contain the list of row values actually
		 *         displayed to the user in the table
		 */
		public InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<R>> getDisplayed() {
			return theDisplayed;
		}

		/** @return Determines the size of the table from the number of rows displayed */
		public AdaptiveHeight.Interpreted getAdaptiveHeight() {
			return theAdaptiveHeight;
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			super.doUpdate(env);
			theDisplayed = getDefinition().getDisplayed() == null ? null
				: getDefinition().getDisplayed().interpret(ModelTypes.Collection.forType(getValueType()), env);
			theAdaptiveHeight = syncChild(getDefinition().getAdaptiveHeight(), theAdaptiveHeight, def -> def.interpret(this),
				AdaptiveHeight.Interpreted::updateAdaptiveHeight);
		}

		@Override
		public T create() {
			return (T) new QuickSuperTable<R>(getIdentity());
		}
	}

	/**
	 * If specified in a &lt;super-table>, this element causes the table to be sized in accordance with the number of rows displayed in it
	 */
	public static class AdaptiveHeight extends ExElement.Abstract {
		/** Definition for {@link AdaptiveHeight} */
		@ExElementTraceable(toolkit = QuickXInterpretation.X,
			qonfigType = ADAPTIVE_HEIGHT,
			interpretation = Interpreted.class,
			instance = AdaptiveHeight.class)
		public static class Def extends ExElement.Def.Abstract<AdaptiveHeight> {
			private Integer theMinRows;
			private Integer thePrefRows;
			private Integer theMaxRows;

			/**
			 * @param parent The parent element of the widget
			 * @param qonfigType The Qonfig type of the widget
			 */
			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
				super(parent, qonfigType);
			}

			/** @return The number of rows to determine the minimum size for the table */
			@QonfigAttributeGetter("min-rows")
			public Integer getMinRows() {
				return theMinRows;
			}

			/** @return The number of rows to determine the preferred size for the table */
			@QonfigAttributeGetter("pref-rows")
			public Integer getPrefRows() {
				return thePrefRows;
			}

			/** @return The number of rows to determine the maximum size for the table */
			@QonfigAttributeGetter("max-rows")
			public Integer getMaxRows() {
				return theMaxRows;
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session);

				theMinRows = session.getAttribute("min-rows", Integer.class);
				thePrefRows = session.getAttribute("pref-rows", Integer.class);
				theMaxRows = session.getAttribute("max-rows", Integer.class);
			}

			/**
			 * @param parent The parent for the interpreted element
			 * @return The interpretation for this adaptive height
			 */
			public Interpreted interpret(ExElement.Interpreted<?> parent) {
				return new Interpreted(this, parent);
			}
		}

		/** Interpretation for {@link AdaptiveHeight} */
		public static class Interpreted extends ExElement.Interpreted.Abstract<AdaptiveHeight> {
			Interpreted(Def definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Def getDefinition() {
				return (Def) super.getDefinition();
			}

			/**
			 * Updates this element
			 *
			 * @param env The interpreted environment to use for evaluating expressions
			 * @throws ExpressoInterpretationException If an error occurs interpreting this element
			 */
			public void updateAdaptiveHeight(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				update(env);
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv expressoEnv) throws ExpressoInterpretationException {
				super.doUpdate(expressoEnv);
			}

			/** @return The adaptive height instance */
			public AdaptiveHeight create() {
				return new AdaptiveHeight(getIdentity());
			}
		}

		private Integer theMinRows;
		private Integer thePrefRows;
		private Integer theMaxRows;

		AdaptiveHeight(Object id) {
			super(id);
		}

		/** @return The number of rows to determine the minimum size for the table, or null if unspecified */
		public Integer getMinRows() {
			return theMinRows;
		}

		/**
		 * @param defaultV The default value to return if the value is unspecified
		 * @return The number of rows to determine the minimum size for the table, or the given default if unspecified
		 */
		public int getMinRows(int defaultV) {
			return theMinRows == null ? defaultV : theMinRows;
		}

		/** @return The number of rows to determine the preferred size for the table, or null if unspecified */
		public Integer getPrefRows() {
			return thePrefRows;
		}

		/**
		 * @param defaultV The default value to return if the value is unspecified
		 * @return The number of rows to determine the preferred size for the table, or the given default if unspecified
		 */
		public int getPrefRows(int defaultV) {
			return thePrefRows == null ? defaultV : thePrefRows;
		}

		/** @return The number of rows to determine the maximum size for the table, or null if unspecified */
		public Integer getMaxRows() {
			return theMaxRows;
		}

		/**
		 * @param defaultV The default value to return if the value is unspecified
		 * @return The number of rows to determine the maximum size for the table, or the given default if unspecified
		 */
		public int getMaxRows(int defaultV) {
			return theMaxRows == null ? defaultV : theMaxRows;
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
			super.doUpdate(interpreted);

			Interpreted myInterpreted = (Interpreted) interpreted;
			theMinRows = myInterpreted.getDefinition().getMinRows();
			thePrefRows = myInterpreted.getDefinition().getPrefRows();
			theMaxRows = myInterpreted.getDefinition().getMaxRows();
		}

		@Override
		public AdaptiveHeight copy(ExElement parent) {
			return (AdaptiveHeight) super.copy(parent);
		}
	}

	private boolean isSearchable;
	private String theItemName;

	private ModelValueInstantiator<ObservableCollection<R>> theDisplayedInstantiator;

	private SettableValue<ObservableCollection<R>> theDisplayed;

	private AdaptiveHeight theAdaptiveHeight;

	/** @param id The element ID for this widget */
	protected QuickSuperTable(Object id) {
		super(id);
		theDisplayed = SettableValue.create();
	}

	/** @return Whether a text field should be available to the user for filtering and sorting the table data */
	public boolean isSearchable() {
		return isSearchable;
	}

	/** @return The item name for rows in the table. May affect some kinds of auto-generated UI text. */
	public String getItemName() {
		return theItemName;
	}

	/**
	 * @return A collection of the same type as this table's rows that, if specified, will contain the list of row values actually displayed
	 *         to the user in the table
	 */
	public ObservableValue<ObservableCollection<R>> getDisplayed() {
		return theDisplayed.unsettable();
	}

	/** @return Determines the size of the table from the number of rows displayed */
	public AdaptiveHeight getAdaptiveHeight() {
		return theAdaptiveHeight;
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);

		Interpreted<R, ?> myInterpreted = (Interpreted<R, ?>) interpreted;
		isSearchable = myInterpreted.getDefinition().isSearchable();
		theItemName = myInterpreted.getDefinition().getItemName();
		theDisplayedInstantiator = myInterpreted.getDisplayed() == null ? null : myInterpreted.getDisplayed().instantiate();
		if (myInterpreted.getAdaptiveHeight() == null) {
			if (theAdaptiveHeight != null) {
				theAdaptiveHeight.destroy();
				theAdaptiveHeight = null;
			}
		} else {
			if (theAdaptiveHeight != null && theAdaptiveHeight.getIdentity() != myInterpreted.getAdaptiveHeight().getIdentity()) {
				theAdaptiveHeight.destroy();
				theAdaptiveHeight = null;
			}
			if (theAdaptiveHeight == null)
				theAdaptiveHeight = myInterpreted.getAdaptiveHeight().create();
			theAdaptiveHeight.update(myInterpreted.getAdaptiveHeight(), this);
		}
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();

		if (theDisplayedInstantiator != null)
			theDisplayedInstantiator.instantiate();
		if (theAdaptiveHeight != null)
			theAdaptiveHeight.instantiated();
	}

	@Override
	protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		super.doInstantiate(myModels);

		theDisplayed.set(theDisplayedInstantiator == null ? null : theDisplayedInstantiator.get(myModels), null);
		if (theAdaptiveHeight != null)
			theAdaptiveHeight.instantiate(myModels);
	}

	@Override
	public QuickSuperTable<R> copy(ExElement parent) {
		QuickSuperTable<R> copy = (QuickSuperTable<R>) super.copy(parent);

		copy.theDisplayed = SettableValue.create();
		if (theAdaptiveHeight != null)
			copy.theAdaptiveHeight = theAdaptiveHeight.copy(copy);

		return copy;
	}
}
