package org.observe.quick.base;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.DynamicModelValue;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.quick.QuickStyledElement;
import org.observe.util.TypeTokens;
import org.qommons.collect.CollectionUtils;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public class QuickTable<R> extends TabularWidget.Abstract<R> {
	public static final String TABLE = "table";

	public static final ExElement.AttributeValueGetter<QuickTable<?>, Interpreted<?>, Def> ROWS = ExElement.AttributeValueGetter
		.ofX(Def::getRows, Interpreted::getRows, QuickTable::getRows, "The rows to display in the table");

	public static final ExElement.ChildElementGetter<QuickTable<?>, Interpreted<?>, Def> ACTIONS = new ExElement.ChildElementGetter<QuickTable<?>, Interpreted<?>, Def>() {
		@Override
		public String getDescription() {
			return "Actions that may be performed on rows, groups of rows, or the data set as a whole";
		}

		@Override
		public List<? extends ExElement.Def<?>> getChildrenFromDef(Def def) {
			return def.getActions();
		}

		@Override
		public List<? extends ExElement.Interpreted<?>> getChildrenFromInterpreted(Interpreted<?> interp) {
			return interp.getActions();
		}

		@Override
		public List<? extends ExElement> getChildrenFromElement(QuickTable<?> element) {
			return element.getActions();
		}
	};

	public static class Def extends TabularWidget.Def.Abstract<QuickTable<?>> {
		private CompiledExpression theRows;
		private String theValueName;
		private CompiledExpression theSelection;
		private CompiledExpression theMultiSelection;
		private final List<ValueAction.Def<?, ?>> theActions;

		public Def(ExElement.Def<?> parent, QonfigElement element) {
			super(parent, element);
			theActions = new ArrayList<>();
		}

		@Override
		public String getValueName() {
			return theValueName;
		}

		@Override
		public CompiledExpression getSelection() {
			return theSelection;
		}

		@Override
		public CompiledExpression getMultiSelection() {
			return theMultiSelection;
		}

		public CompiledExpression getRows() {
			return theRows;
		}

		public List<ValueAction.Def<?, ?>> getActions() {
			return Collections.unmodifiableList(theActions);
		}

		@Override
		public void update(ExpressoQIS session) throws QonfigInterpretationException {
			ExElement.checkElement(session.getFocusType(), QuickBaseInterpretation.NAME, QuickBaseInterpretation.VERSION, TABLE);
			forAttribute(session.getAttributeDef(null, null, "rows"), ROWS);
			forChild(session.getRole("action"), ACTIONS);
			super.update(session); // table is a tabular widget, tabular-widget is an add-on. Don't get the multi-value-widget super
			theRows = session.getAttributeExpression("rows");
			theValueName = session.getAttributeText("value-name");
			theSelection = session.getAttributeExpression("selection");
			theMultiSelection = session.getAttributeExpression("multi-selection");
			ExElement.syncDefs(ValueAction.Def.class, theActions, session.forChildren("action"));
		}

		@Override
		public Interpreted<?> interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}
	}

	public static class Interpreted<R> extends TabularWidget.Interpreted.Abstract<R, QuickTable<R>> {
		private InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<R>> theRows;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<R>> theSelection;
		private InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<R>> theMultiSelection;
		private final List<ValueAction.Interpreted<R, ?>> theActions;

		public Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
			theActions = new ArrayList<>();
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		@Override
		public TypeToken<R> getRowType() {
			return (TypeToken<R>) theRows.getType().getType(0);
		}

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

		public List<ValueAction.Interpreted<R, ?>> getActions() {
			return Collections.unmodifiableList(theActions);
		}

		@Override
		public TypeToken<QuickTable<R>> getWidgetType() {
			return TypeTokens.get().keyFor(QuickTable.class).parameterized(getRowType());
		}

		@Override
		public void update(QuickStyledElement.QuickInterpretationCache cache) throws ExpressoInterpretationException {
			// Do this first so we have the row type
			theRows = getDefinition().getRows().evaluate(ModelTypes.Collection.<R> anyAsV()).interpret();
			DynamicModelValue.satisfyDynamicValueType(getDefinition().getValueName(), getDefinition().getModels(),
				ModelTypes.Value.forType(theRows.getType().getType(0)));
			super.update(cache);
			theSelection = getDefinition().getSelection() == null ? null
				: getDefinition().getSelection().evaluate(ModelTypes.Value.forType(getRowType())).interpret();
			theMultiSelection = getDefinition().getMultiSelection() == null ? null
				: getDefinition().getMultiSelection().evaluate(ModelTypes.Collection.forType(getRowType())).interpret();
			CollectionUtils.synchronize(theActions, getDefinition().getActions(), //
				(a, d) -> a.getDefinition() == d)//
			.<ExpressoInterpretationException> simpleE(
				child -> (ValueAction.Interpreted<R, ?>) ((ValueAction.Def<R, ?>) child).interpret(this, getRowType()))//
			.rightOrder()//
			.onRightX(element -> element.getLeftValue().update())//
			.onCommonX(element -> element.getLeftValue().update())//
			.adjust();
		}

		@Override
		public QuickTable<R> create(ExElement parent) {
			return new QuickTable<>(this, parent);
		}
	}

	private final SettableValue<ObservableCollection<R>> theRows;
	private final SettableValue<SettableValue<R>> theSelection;
	private final SettableValue<ObservableCollection<R>> theMultiSelection;
	private final ObservableCollection<ValueAction<R>> theActions;

	public QuickTable(Interpreted<R> interpreted, ExElement parent) {
		super(interpreted, parent);
		theRows = SettableValue
			.build(
				TypeTokens.get().keyFor(ObservableCollection.class).<ObservableCollection<R>> parameterized(getRowType()))
			.build();
		theSelection = SettableValue
			.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<R>> parameterized(getRowType())).build();
		theMultiSelection = SettableValue
			.build(
				TypeTokens.get().keyFor(ObservableCollection.class).<ObservableCollection<R>> parameterized(getRowType()))
			.build();
		theActions = ObservableCollection.build(TypeTokens.get().keyFor(ValueAction.class).<ValueAction<R>> parameterized(getRowType()))
			.build();
	}

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

	public ObservableCollection<ValueAction<R>> getActions() {
		return theActions.flow().unmodifiable(false).collect();
	}

	@Override
	protected void updateModel(ExElement.Interpreted<?> interpreted, ModelSetInstance myModels) throws ModelInstantiationException {
		super.updateModel(interpreted, myModels);
		QuickTable.Interpreted<R> myInterpreted = (QuickTable.Interpreted<R>) interpreted;
		theRows.set(myInterpreted.getRows().get(myModels), null);
		theSelection.set(myInterpreted.getSelection() == null ? null : myInterpreted.getSelection().get(myModels), null);
		theMultiSelection.set(myInterpreted.getMultiSelection() == null ? null : myInterpreted.getMultiSelection().get(myModels),
			null);
		CollectionUtils.synchronize(theActions, myInterpreted.getActions(), //
			(a, i) -> a.getIdentity() == i.getDefinition().getIdentity())
		.<ModelInstantiationException> simpleE(action -> action.create(this))//
		.rightOrder()//
		.onRightX(element -> element.getLeftValue().update(element.getRightValue(), myModels))//
		.onCommonX(element -> element.getLeftValue().update(element.getRightValue(), myModels))//
		.adjust();
	}
}
