package org.observe.quick.base;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
import org.qommons.collect.CollectionUtils;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

/**
 * A table that displays a row for each value in a collection
 *
 * @param <R> The row type of the table
 */
public class QuickTable<R> extends TabularWidget.Abstract<R> {
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
	public static class Def<T extends QuickTable<?>> extends TabularWidget.Def.Abstract<T> {
		private CompiledExpression theRows;
		private CompiledExpression theSelection;
		private CompiledExpression theMultiSelection;
		private final List<ValueAction.Def<?>> theActions;

		/**
		 * @param parent The parent element of the widget
		 * @param type The Qonfig type of the widget
		 */
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
			theActions = new ArrayList<>();
		}

		@Override
		protected String getActiveValueVariableName(ExpressoQIS session) {
			return session.getAttributeText("active-value-name");
		}

		@Override
		public CompiledExpression getSelection() {
			return theSelection;
		}

		@Override
		public CompiledExpression getMultiSelection() {
			return theMultiSelection;
		}

		/** @return The row values for the table */
		@QonfigAttributeGetter("rows")
		public CompiledExpression getRows() {
			return theRows;
		}

		/** @return Actions that can be executed against rows in the table */
		@QonfigChildGetter("action")
		public List<ValueAction.Def<?>> getActions() {
			return Collections.unmodifiableList(theActions);
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session.asElement(TABULAR_WIDGET));
			theRows = getAttributeExpression("rows", session);
			theSelection = getAttributeExpression("selection", session);
			theMultiSelection = getAttributeExpression("multi-selection", session);
			syncChildren(ValueAction.Def.class, theActions, session.forChildren("action"));
		}

		@Override
		protected TypeToken<?> getRowType(TabularWidget.Interpreted<?, ?> interpreted, InterpretedExpressoEnv env)
			throws ExpressoInterpretationException {
			return ((Interpreted<?, ?>) interpreted).getValueType();
		}

		@Override
		public Interpreted<?, T> interpret(ExElement.Interpreted<?> parent) {
			return (Interpreted<?, T>) new Interpreted<>((Def<QuickTable<Object>>) this, parent);
		}
	}

	/**
	 * {@link QuickTable} interpretation
	 *
	 * @param <R> The row type of the table
	 * @param <T> The sub-type of table to create
	 */
	public static class Interpreted<R, T extends QuickTable<R>> extends TabularWidget.Interpreted.Abstract<R, T> {
		private InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<R>> theRows;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<R>> theSelection;
		private InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<R>> theMultiSelection;
		private final List<ValueAction.Interpreted<R, ?>> theActions;

		/**
		 * @param definition The definition to interpret
		 * @param parent The parent element for the widget
		 */
		protected Interpreted(Def<T> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
			theActions = new ArrayList<>();
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

		@Override
		public InterpretedValueSynth<SettableValue<?>, SettableValue<R>> getSelection() {
			return theSelection;
		}

		@Override
		public InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<R>> getMultiSelection() {
			return theMultiSelection;
		}

		/** @return Actions that can be executed against rows in the table */
		public List<ValueAction.Interpreted<R, ?>> getActions() {
			return Collections.unmodifiableList(theActions);
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			super.doUpdate(env);
			theSelection = interpret(getDefinition().getSelection(), ModelTypes.Value.forType(getValueType()));
			theMultiSelection = interpret(getDefinition().getMultiSelection(), ModelTypes.Collection.forType(getValueType()));
			syncChildren(getDefinition().getActions(), theActions,
				def -> (ValueAction.Interpreted<R, ?>) ((ValueAction.Def<?>) def).interpret(this, getValueType()),
				ValueAction.Interpreted::updateAction);
		}

		@Override
		public T create() {
			return (T) new QuickTable<>(getIdentity());
		}
	}

	private ModelValueInstantiator<ObservableCollection<R>> theRowsInstantiator;
	private ModelValueInstantiator<SettableValue<R>> theSelectionInstantiator;
	private ModelValueInstantiator<ObservableCollection<R>> theMultiSelectionInstantiator;

	private SettableValue<ObservableCollection<R>> theRows;
	private SettableValue<SettableValue<R>> theSelection;
	private SettableValue<ObservableCollection<R>> theMultiSelection;
	private ObservableCollection<ValueAction<R>> theActions;

	/** @param id The element ID for this widget */
	protected QuickTable(Object id) {
		super(id);
	}

	/** @return The row values for the table */
	public ObservableCollection<R> getRows() {
		return ObservableCollection.flattenValue(theRows);
	}

	@Override
	public SettableValue<R> getSelection() {
		return SettableValue.flatten(theSelection);
	}

	@Override
	public ObservableCollection<R> getMultiSelection() {
		return ObservableCollection.flattenValue(theMultiSelection);
	}

	/** @return Actions that can be executed against rows in the table */
	public ObservableCollection<ValueAction<R>> getActions() {
		return theActions.flow().unmodifiable(false).collect();
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);
		QuickTable.Interpreted<R, ?> myInterpreted = (QuickTable.Interpreted<R, ?>) interpreted;

		theRows = SettableValue.<ObservableCollection<R>> build().build();
		theSelection = SettableValue.<SettableValue<R>> build().build();
		theMultiSelection = SettableValue.<ObservableCollection<R>> build().build();
		theActions = ObservableCollection.<ValueAction<R>> build().build();

		theRowsInstantiator = myInterpreted.getRows().instantiate();
		theSelectionInstantiator = myInterpreted.getSelection() == null ? null : myInterpreted.getSelection().instantiate();
		theMultiSelectionInstantiator = myInterpreted.getMultiSelection() == null ? null : myInterpreted.getMultiSelection().instantiate();
		CollectionUtils.synchronize(theActions, myInterpreted.getActions(), //
			(a, i) -> a.getIdentity() == i.getIdentity())//
		.<ModelInstantiationException> simpleX(action -> action.create())//
		.rightOrder()//
		.onLeftX(element -> element.getLeftValue().destroy())//
		.onRightX(element -> element.getLeftValue().update(element.getRightValue(), this))//
		.onCommonX(element -> element.getLeftValue().update(element.getRightValue(), this))//
		.adjust();
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();

		theRowsInstantiator.instantiate();
		if (theSelectionInstantiator != null)
			theSelectionInstantiator.instantiate();
		if (theMultiSelectionInstantiator != null)
			theMultiSelectionInstantiator.instantiate();

		for (ValueAction<R> action : theActions)
			action.instantiated();
	}

	@Override
	protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		super.doInstantiate(myModels);

		theRows.set(theRowsInstantiator.get(myModels), null);
		theSelection.set(theSelectionInstantiator == null ? null : theSelectionInstantiator.get(myModels), null);
		theMultiSelection.set(theMultiSelectionInstantiator == null ? null : theMultiSelectionInstantiator.get(myModels), null);

		for (ValueAction<R> action : theActions)
			action.instantiate(myModels);
	}

	@Override
	public QuickTable<R> copy(ExElement parent) {
		QuickTable<R> copy = (QuickTable<R>) super.copy(parent);

		copy.theRows = SettableValue.<ObservableCollection<R>> build().build();
		copy.theSelection = SettableValue.<SettableValue<R>> build().build();
		copy.theMultiSelection = SettableValue.<ObservableCollection<R>> build().build();
		copy.theActions = ObservableCollection.<ValueAction<R>> build().build();

		for (ValueAction<R> action : theActions)
			copy.theActions.add(action.copy(copy));

		return copy;
	}
}
