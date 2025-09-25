package org.observe.quick.base;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.observe.Observable;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.ExpressoInterpretationException;
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
import org.observe.quick.QuickWidget;
import org.qommons.QommonsUtils;
import org.qommons.collect.CollectionUtils;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

/**
 * A table that displays a row for each value in a collection
 *
 * @param <R> The row type of the table
 * @param <C> The column type of the table
 */
public class QuickTable<R, C> extends TabularWidget.Abstract<R, C> {
	/** The XML name of this element */
	public static final String TABLE = "table";

	/**
	 * {@link QuickTable} definition
	 *
	 * @param <T> The sub-type of table to create
	 */
	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = TABLE,
		interpretation = Interpreted.class,
		instance = QuickTable.class)
	public static class Def<T extends QuickTable<?, ?>> extends TabularWidget.Def.Abstract<T> {
		private CompiledExpression theRows;
		private final List<ExElement.Def<?>> theActionsAndOptions;
		private boolean isOptionsOnTop;

		/**
		 * @param parent The parent element of the widget
		 * @param type The Qonfig type of the widget
		 */
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
			theActionsAndOptions = new ArrayList<>();
		}

		@Override
		protected String getActiveValueVariableName(ExpressoQIS session) {
			return session.getAttributeText("active-value-name");
		}

		/** @return The row values for the table */
		@QonfigAttributeGetter("rows")
		public CompiledExpression getRows() {
			return theRows;
		}

		/**
		 * @return The list containing the {@link #getActions() actions} and {@link #getOptions() table options} for this table, in order of
		 *         their specification in the file
		 */
		public List<ExElement.Def<?>> getActionsAndOptions() {
			return Collections.unmodifiableList(theActionsAndOptions);
		}

		/** @return Whether options and button actions should be placed at the top of the table or the bottom */
		@QonfigAttributeGetter("options-on-top")
		public boolean isOptionsOnTop() {
			return isOptionsOnTop;
		}

		/** @return Actions that can be executed against rows in the table */
		@QonfigChildGetter("action")
		public List<ValueAction.Def<?>> getActions() {
			return QommonsUtils.filterMap(theActionsAndOptions, aao -> aao instanceof ValueAction.Def, aao -> (ValueAction.Def<?>) aao);
		}

		/** @return Widget options to place in a bar above or below the table along with button actions */
		@QonfigChildGetter("option")
		public List<QuickWidget.Def<?>> getOptions() {
			return QommonsUtils.filterMap(theActionsAndOptions, aao -> aao instanceof QuickWidget.Def, aao -> (QuickWidget.Def<?>) aao);
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session.asElement(TABULAR_WIDGET));
			theRows = getAttributeExpression("rows", session);
			isOptionsOnTop = session.getAttribute("options-on-top", boolean.class);
			syncChildren(ExElement.Def.class, theActionsAndOptions, session.forChildren("action", "option"));
		}

		@Override
		protected TypeToken<?> getRowType(TabularWidget.Interpreted<?, ?, ?> interpreted) throws ExpressoInterpretationException {
			return ((Interpreted<?, ?, ?>) interpreted).getValueType();
		}

		@Override
		public Interpreted<?, ?, T> interpret(ExElement.Interpreted<?> parent) {
			return (Interpreted<?, ?, T>) new Interpreted<>((Def<QuickTable<Object, Object>>) this, parent);
		}
	}

	/**
	 * {@link QuickTable} interpretation
	 *
	 * @param <R> The row type of the table
	 * @param <C> The column type of the table
	 * @param <T> The sub-type of table to create
	 */
	public static class Interpreted<R, C, T extends QuickTable<R, C>> extends TabularWidget.Interpreted.Abstract<R, C, T> {
		private InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<R>> theRows;
		private final List<ExElement.Interpreted<?>> theActionsAndOptions;

		/**
		 * @param definition The definition to interpret
		 * @param parent The parent element for the widget
		 */
		protected Interpreted(Def<T> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
			theActionsAndOptions = new ArrayList<>();
		}

		@Override
		public Def<T> getDefinition() {
			return (Def<T>) super.getDefinition();
		}

		@Override
		public TypeToken<R> getValueType() throws ExpressoInterpretationException {
			if (theRows == null)
				theRows = interpret(getDefinition().getRows(), ModelTypes.Collection.<R> anyAsV());

			return (TypeToken<R>) theRows.getType().getType(0);
		}

		/** @return The row values for the table */
		public InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<R>> getRows() {
			return theRows;
		}

		/**
		 * @return The list containing the {@link #getActions() actions} and {@link #getOptions() table options} for this table, in order of
		 *         their specification in the file
		 */
		public List<ExElement.Interpreted<?>> getActionsAndOptions() {
			return Collections.unmodifiableList(theActionsAndOptions);
		}

		/** @return Actions that can be executed against rows in the table */
		public List<ValueAction.Interpreted<R, ?>> getActions() {
			return QommonsUtils.filterMap(theActionsAndOptions, aao -> aao instanceof ValueAction.Interpreted,
				aao -> (ValueAction.Interpreted<R, ?>) aao);
		}

		/** @return Widget options to place in a bar above or below the table along with button actions */
		public List<QuickWidget.Interpreted<?>> getOptions() {
			return QommonsUtils.filterMap(theActionsAndOptions, aao -> aao instanceof QuickWidget.Interpreted,
				aao -> (QuickWidget.Interpreted<?>) aao);
		}

		@Override
		protected void doUpdate() throws ExpressoInterpretationException {
			super.doUpdate();
			syncChildren(getDefinition().getActionsAndOptions(), theActionsAndOptions, def -> {
				if (def instanceof ValueAction.Def)
					return (ValueAction.Interpreted<R, ?>) ((ValueAction.Def<?>) def).interpret(this, getValueType());
				else if (def instanceof QuickWidget.Def)
					return ((QuickWidget.Def<?>) def).interpret(this);
				else
					throw new IllegalStateException("Whats this? " + def.getClass().getName());
			}, interp -> {
				if (interp instanceof ValueAction.Interpreted)
					((ValueAction.Interpreted<R, ?>) interp).updateAction();
				else
					((QuickWidget.Interpreted<?>) interp).updateElement();
			});
		}

		@Override
		public T create() {
			return (T) new QuickTable<>(getIdentity());
		}
	}

	private ModelValueInstantiator<ObservableCollection<R>> theRowsInstantiator;

	private SettableValue<ObservableCollection<R>> theRows;
	private ObservableCollection<ExElement> theActionsAndOptions;
	private ObservableCollection<ValueAction<R>> theActions;
	private ObservableCollection<QuickWidget> theOptions;
	private boolean isOptionsOnTop;

	/** @param id The element ID for this widget */
	protected QuickTable(Object id) {
		super(id);
		theActionsAndOptions = ObservableCollection.create();
	}

	/** @return The row values for the table */
	public ObservableCollection<R> getRows() {
		return ObservableCollection.flattenValue(theRows);
	}

	/**
	 * @return The list containing the {@link #getActions() actions} and {@link #getOptions() table options} for this table, in order of
	 *         their specification in the file
	 */
	public ObservableCollection<ExElement> getActionsAndOptions() {
		return theActionsAndOptions.flow().unmodifiable(false).collectPassive();
	}

	/** @return Whether options and button actions should be placed at the top of the table or the bottom */
	public boolean isOptionsOnTop() {
		return isOptionsOnTop;
	}

	/** @return Actions that can be executed against rows in the table */
	public ObservableCollection<ValueAction<R>> getActions() {
		return theActions;
	}

	/** @return Widget options to place in a bar above or below the table along with button actions */
	public ObservableCollection<QuickWidget> getOptions() {
		return theOptions;
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);
		QuickTable.Interpreted<R, C, ?> myInterpreted = (QuickTable.Interpreted<R, C, ?>) interpreted;

		theRows = SettableValue.<ObservableCollection<R>> build().build();

		theRowsInstantiator = myInterpreted.getRows().instantiate();
		CollectionUtils.synchronize(theActionsAndOptions, myInterpreted.getActionsAndOptions(), //
			(a, i) -> a.getIdentity() == i.getIdentity())//
		.<ModelInstantiationException> simpleX(aao -> {
			if (aao instanceof ValueAction.Interpreted)
				return ((ValueAction.Interpreted<R, ?>) aao).create();
			else if (aao instanceof QuickWidget.Interpreted)
				return ((QuickWidget.Interpreted<?>) aao).create();
			else
				throw new IllegalStateException("What is this? " + aao.getClass().getName());
		})//
		.rightOrder()//
		.onLeftX(element -> element.getLeftValue().destroy())//
		.onRightX(element -> element.getLeftValue().update(element.getRightValue(), this))//
		.onCommonX(element -> element.getLeftValue().update(element.getRightValue(), this))//
		.adjust();
		isOptionsOnTop = myInterpreted.getDefinition().isOptionsOnTop();
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();

		theRowsInstantiator.instantiate();

		for (ExElement aao : theActionsAndOptions)
			aao.instantiated();
	}

	@Override
	protected ModelSetInstance doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		myModels = super.doInstantiate(myModels);

		theRows.set(theRowsInstantiator.get(myModels), null);
		if (theActions == null) {
			theActions = theActionsAndOptions.flow()//
				.filter((Class<ValueAction<R>>) (Class<?>) ValueAction.class)//
				.unmodifiable(false)//
				.collectActive(Observable.or(myModels.getUntil(), onDestroy()));
			theOptions = theActionsAndOptions.flow()//
				.filter(QuickWidget.class)//
				.unmodifiable(false)//
				.collectActive(Observable.or(myModels.getUntil(), onDestroy()));
		}

		for (ExElement aao : theActionsAndOptions)
			aao.instantiate(myModels);
		return myModels;
	}

	@Override
	public QuickTable<R, C> copy(ExElement parent) {
		QuickTable<R, C> copy = (QuickTable<R, C>) super.copy(parent);

		copy.theRows = SettableValue.<ObservableCollection<R>> build().build();
		copy.theActionsAndOptions = ObservableCollection.create();
		copy.theActions = null;
		copy.theOptions = null;

		for (ExElement aao : theActionsAndOptions)
			copy.theActionsAndOptions.add(aao.copy(copy));

		return copy;
	}
}
