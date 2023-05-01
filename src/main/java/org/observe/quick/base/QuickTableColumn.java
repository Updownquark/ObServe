package org.observe.quick.base;

import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.CompiledExpression;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedModelSet;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.quick.QuickElement;
import org.observe.quick.QuickWidget.QuickInterpretationCache;
import org.observe.util.TypeTokens;
import org.qommons.config.AbstractQIS;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public interface QuickTableColumn<R, C> {
	public interface TableColumnSet<R> extends QuickElement {
		public interface Def<CC extends TableColumnSet<?>> extends QuickElement.Def<CC> {
			<R> Interpreted<R, ? extends CC> interpret(QuickElement.Interpreted<?> parent);
		}

		public interface Interpreted<R, CC extends TableColumnSet<R>> extends QuickElement.Interpreted<CC> {
			void update(InterpretedModelSet models, QuickInterpretationCache cache) throws ExpressoInterpretationException;

			CC create(QuickElement parent);
		}

		ObservableCollection<? extends QuickTableColumn<R, ?>> getColumns();

		TableColumnSet<R> update(ModelSetInstance models) throws ModelInstantiationException;
	}

	SettableValue<String> getName();

	QuickTableColumn.TableColumnSet<R> getColumnSet();

	TypeToken<C> getType();

	SettableValue<C> getValue();

	void update();

	public class SingleColumnSet<R, C> extends QuickElement.Abstract implements TableColumnSet<R> {
		public static class Def extends QuickElement.Def.Abstract<SingleColumnSet<?, ?>>
		implements TableColumnSet.Def<SingleColumnSet<?, ?>> {
			private CompiledExpression theName;
			private CompiledExpression theValue;

			public Def(QuickElement.Def<?> parent, QonfigElement element) {
				super(parent, element);
			}

			public CompiledExpression getName() {
				return theName;
			}

			public CompiledExpression getValue() {
				return theValue;
			}

			@Override
			public Def update(AbstractQIS<?> session) throws QonfigInterpretationException {
				super.update(session);
				theName = getExpressoSession().getAttributeExpression("name");
				theValue = getExpressoSession().getAttributeExpression("value");
				return this;
			}

			@Override
			public <R> Interpreted<R, ?> interpret(QuickElement.Interpreted<?> parent) {
				return new Interpreted<>(this, parent);
			}
		}

		public static class Interpreted<R, C> extends QuickElement.Interpreted.Abstract<SingleColumnSet<R, C>>
		implements TableColumnSet.Interpreted<R, SingleColumnSet<R, C>> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theName;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<C>> theValue;

			public Interpreted(SingleColumnSet.Def definition, QuickElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public SingleColumnSet.Def getDefinition() {
				return (SingleColumnSet.Def) super.getDefinition();
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getName() {
				return theName;
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<C>> getValue() {
				return theValue;
			}

			public TypeToken<C> getType() {
				return (TypeToken<C>) theValue.getType().getType(0);
			}

			@Override
			public void update(InterpretedModelSet models, QuickInterpretationCache cache) throws ExpressoInterpretationException {
				super.update();
				theName = getDefinition().getName().evaluate(ModelTypes.Value.STRING).interpret();
				theValue = getDefinition().getValue().evaluate(ModelTypes.Value.<C> anyAs()).interpret();
			}

			@Override
			public SingleColumnSet<R, C> create(QuickElement parent) {
				return new SingleColumnSet<>(this, parent);
			}
		}

		private SettableValue<SettableValue<String>> theName;
		private SettableValue<SettableValue<C>> theValue;
		private final ObservableCollection<SingleColumn> theColumn;

		public SingleColumnSet(Interpreted<R, C> interpreted, QuickElement parent) {
			super(interpreted, parent);
			theName = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<String>> parameterized(String.class))
				.build();
			theValue = SettableValue
				.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<C>> parameterized(getInterpreted().getType())).build();
			theColumn = ObservableCollection.of((Class<SingleColumnSet<R, C>.SingleColumn>) (Class<?>) SingleColumn.class,
				new SingleColumn());
		}

		@Override
		public Interpreted<R, C> getInterpreted() {
			return (Interpreted<R, C>) super.getInterpreted();
		}

		@Override
		public ObservableCollection<? extends QuickTableColumn<R, ?>> getColumns() {
			return theColumn;
		}

		@Override
		public SingleColumnSet<R, C> update(ModelSetInstance models) throws ModelInstantiationException {
			super.update(models);
			theName.set(getInterpreted().getName().get(models), null);
			theValue.set(getInterpreted().getValue().get(models), null);
			return this;
		}

		public class SingleColumn implements QuickTableColumn<R, C> {
			@Override
			public TableColumnSet<R> getColumnSet() {
				return SingleColumnSet.this;
			}

			@Override
			public SettableValue<String> getName() {
				return SettableValue.flatten(theName);
			}

			@Override
			public TypeToken<C> getType() {
				return getInterpreted().getType();
			}

			@Override
			public SettableValue<C> getValue() {
				return SettableValue.flatten(theValue);
			}

			@Override
			public void update() {
			}
		}
	}
}
