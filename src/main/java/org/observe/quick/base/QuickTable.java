package org.observe.quick.base;

import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.CompiledExpression;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.quick.QuickElement;
import org.observe.util.TypeTokens;
import org.qommons.config.AbstractQIS;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public class QuickTable<R> extends TabularWidget.Abstract<R> {
	public static class Def extends TabularWidget.Def.Abstract<QuickTable<?>> {
		private CompiledExpression theRows;
		private String theValueName;
		private CompiledExpression theSelection;
		private CompiledExpression theMultiSelection;

		public Def(QuickElement.Def<?> parent, QonfigElement element) {
			super(parent, element);
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

		@Override
		public Def update(AbstractQIS<?> session) throws QonfigInterpretationException {
			super.update(session);
			theRows = getExpressoSession().getAttributeExpression("rows");
			theValueName = session.getAttributeText("value-name");
			theSelection = getExpressoSession().getAttributeExpression("selection");
			theMultiSelection = getExpressoSession().getAttributeExpression("multi-selection");
			return this;
		}

		@Override
		public Interpreted<?> interpret(QuickElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}
	}

	public static class Interpreted<R> extends TabularWidget.Interpreted.Abstract<R, QuickTable<R>> {
		private InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<R>> theRows;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<R>> theSelection;
		private InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<R>> theMultiSelection;

		public Interpreted(Def definition, QuickElement.Interpreted<?> parent) {
			super(definition, parent);
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

		@Override
		public TypeToken<QuickTable<R>> getWidgetType() {
			return TypeTokens.get().keyFor(QuickTable.class).parameterized(getRowType());
		}

		@Override
		public Interpreted<R> update(QuickInterpretationCache cache) throws ExpressoInterpretationException {
			// Do this first so we have the row type
			theRows = getDefinition().getRows().evaluate(ModelTypes.Collection.<R> anyAs()).interpret();
			super.update(cache);
			theSelection = getDefinition().getSelection() == null ? null
				: getDefinition().getSelection().evaluate(ModelTypes.Value.forType(getRowType())).interpret();
			theMultiSelection = getDefinition().getMultiSelection() == null ? null
				: getDefinition().getMultiSelection().evaluate(ModelTypes.Collection.forType(getRowType())).interpret();
			return this;
		}

		@Override
		public QuickTable<R> create(QuickElement parent) {
			return new QuickTable<>(this, parent);
		}
	}

	private final SettableValue<ObservableCollection<R>> theRows;
	private final SettableValue<SettableValue<R>> theSelection;
	private final SettableValue<ObservableCollection<R>> theMultiSelection;

	public QuickTable(Interpreted<R> interpreted, QuickElement parent) {
		super(interpreted, parent);
		theRows = SettableValue
			.build(
				TypeTokens.get().keyFor(ObservableCollection.class).<ObservableCollection<R>> parameterized(getInterpreted().getRowType()))
			.build();
		theSelection = SettableValue
			.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<R>> parameterized(getInterpreted().getRowType())).build();
		theMultiSelection = SettableValue
			.build(
				TypeTokens.get().keyFor(ObservableCollection.class).<ObservableCollection<R>> parameterized(getInterpreted().getRowType()))
			.build();
	}

	@Override
	public Interpreted<R> getInterpreted() {
		return (Interpreted<R>) super.getInterpreted();
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

	@Override
	public QuickTable<R> update(ModelSetInstance models) throws ModelInstantiationException {
		super.update(models);
		theRows.set(getInterpreted().getRows().get(getModels()), null);
		theSelection.set(getInterpreted().getSelection() == null ? null : getInterpreted().getSelection().get(getModels()), null);
		theMultiSelection.set(getInterpreted().getMultiSelection() == null ? null : getInterpreted().getMultiSelection().get(getModels()),
			null);
		return this;
	}
}
