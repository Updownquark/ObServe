package org.observe.quick.base;

import org.observe.SettableValue;
import org.observe.expresso.CompiledExpression;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.quick.QuickElement;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public class QuickTextArea<T> extends QuickEditableTextWidget.Abstract<T> {
	public static class Def<T> extends QuickEditableTextWidget.Def.Abstract<T, QuickTextArea<T>> {
		private CompiledExpression theRows;
		private CompiledExpression isHtml;

		public Def(QuickElement.Def<?> parent, QonfigElement element) {
			super(parent, element);
		}

		@Override
		public boolean isTypeEditable() {
			return true;
		}

		public CompiledExpression getRows() {
			return theRows;
		}

		public CompiledExpression isHtml() {
			return isHtml;
		}

		@Override
		public void update(ExpressoQIS session) throws QonfigInterpretationException {
			super.update(session);
			theRows = session.getAttributeExpression("rows");
			isHtml = session.getAttributeExpression("html");
		}

		@Override
		public Interpreted<T> interpret(QuickElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}
	}

	public static class Interpreted<T> extends QuickEditableTextWidget.Interpreted.Abstract<T, QuickTextArea<T>> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> theRows;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> isHtml;

		public Interpreted(Def<T> definition, QuickElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public QuickTextArea.Def<T> getDefinition() {
			return (Def<T>) super.getDefinition();
		}

		@Override
		public TypeToken<QuickTextArea<T>> getWidgetType() {
			return TypeTokens.get().keyFor(QuickTextArea.class).parameterized(getValueType());
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> getRows() {
			return theRows;
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> isHtml() {
			return isHtml;
		}

		@Override
		public void update(QuickInterpretationCache cache) throws ExpressoInterpretationException {
			super.update(cache);
			theRows = getDefinition().getRows() == null ? null
				: getDefinition().getRows().evaluate(ModelTypes.Value.forType(Integer.class)).interpret();
			isHtml = getDefinition().isHtml() == null ? null
				: getDefinition().isHtml().evaluate(ModelTypes.Value.forType(boolean.class)).interpret();
		}

		@Override
		public QuickTextArea<T> create(QuickElement parent) {
			return new QuickTextArea<>(this, parent);
		}
	}

	public interface QuickTextAreaContext {
		SettableValue<Integer> getMouseRow();

		SettableValue<Integer> getMouseColumn();

		public class Default implements QuickTextAreaContext {
			private final SettableValue<Integer> theMouseRow;
			private final SettableValue<Integer> theMouseColumn;

			public Default(SettableValue<Integer> mouseRow, SettableValue<Integer> mouseColumn) {
				theMouseRow = mouseRow;
				theMouseColumn = mouseColumn;
			}

			public Default() {
				this(SettableValue.build(int.class).withDescription("mouseRow").withValue(0).build(),
					SettableValue.build(int.class).withDescription("mouseColumn").withValue(0).build());
			}

			@Override
			public SettableValue<Integer> getMouseRow() {
				return theMouseRow;
			}

			@Override
			public SettableValue<Integer> getMouseColumn() {
				return theMouseColumn;
			}
		}
	}

	private final SettableValue<SettableValue<Integer>> theRows;
	private final SettableValue<SettableValue<Boolean>> isHtml;
	private final SettableValue<SettableValue<Integer>> theMouseRow;
	private final SettableValue<SettableValue<Integer>> theMouseColumn;

	public QuickTextArea(Interpreted<T> interpreted, QuickElement parent) {
		super(interpreted, parent);
		theRows = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Integer>> parameterized(Integer.class))
			.build();
		isHtml = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Boolean>> parameterized(boolean.class))
			.build();
		theMouseRow = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Integer>> parameterized(int.class))
			.build();
		theMouseColumn = SettableValue.build(theMouseRow.getType()).build();
	}

	public SettableValue<Integer> getRows() {
		return SettableValue.flatten(theRows, () -> 0);
	}

	public SettableValue<Boolean> isHtml() {
		return SettableValue.flatten(isHtml, () -> false);
	}

	public SettableValue<Integer> getMouseRow() {
		return SettableValue.flatten(theMouseRow, () -> 0);
	}

	public SettableValue<Integer> getMouseColumn() {
		return SettableValue.flatten(theMouseColumn, () -> 0);
	}

	public void setTextAreaContext(QuickTextAreaContext ctx) {
		theMouseRow.set(ctx.getMouseRow(), null);
		theMouseColumn.set(ctx.getMouseColumn(), null);
	}

	@Override
	protected void updateModel(QuickElement.Interpreted<?> interpreted, ModelSetInstance myModels) throws ModelInstantiationException {
		satisfyContextValue("mouseRow", ModelTypes.Value.INT, getMouseRow(), myModels);
		satisfyContextValue("mouseColumn", ModelTypes.Value.INT, getMouseColumn(), myModels);
		super.updateModel(interpreted, myModels);
		QuickTextArea.Interpreted<T> myInterpreted = (QuickTextArea.Interpreted<T>) interpreted;
		theRows.set(myInterpreted.getRows() == null ? null : myInterpreted.getRows().get(myModels), null);
		isHtml.set(myInterpreted.isHtml() == null ? null : myInterpreted.isHtml().get(myModels), null);
	}
}
