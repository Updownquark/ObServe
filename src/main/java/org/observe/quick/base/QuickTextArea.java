package org.observe.quick.base;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ElementTypeTraceability;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public class QuickTextArea<T> extends QuickEditableTextWidget.Abstract<T> {
	public static final String TEXT_AREA = "text-area";
	private static final ElementTypeTraceability<QuickTextArea<?>, Interpreted<?>, Def<?>> TRACEABILITY = ElementTypeTraceability
		.<QuickTextArea<?>, Interpreted<?>, Def<?>> build(QuickBaseInterpretation.NAME, QuickBaseInterpretation.VERSION, TEXT_AREA)//
		.reflectMethods(Def.class, Interpreted.class, QuickTextArea.class)//
		.build();

	public static class Def<T> extends QuickEditableTextWidget.Def.Abstract<T, QuickTextArea<T>> {
		private CompiledExpression theRows;
		private CompiledExpression isHtml;

		public Def(ExElement.Def<?> parent, QonfigElement element) {
			super(parent, element);
		}

		@Override
		public boolean isTypeEditable() {
			return true;
		}

		@QonfigAttributeGetter("rows")
		public CompiledExpression getRows() {
			return theRows;
		}

		@QonfigAttributeGetter("html")
		public CompiledExpression isHtml() {
			return isHtml;
		}

		@Override
		public void update(ExpressoQIS session) throws QonfigInterpretationException {
			withTraceability(TRACEABILITY.validate(session.getFocusType(), session.reporting()));
			super.update(session.asElement(session.getFocusType().getSuperElement()));
			theRows = session.getAttributeExpression("rows");
			isHtml = session.getAttributeExpression("html");
		}

		@Override
		public Interpreted<T> interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}
	}

	public static class Interpreted<T> extends QuickEditableTextWidget.Interpreted.Abstract<T, QuickTextArea<T>> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> theRows;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> isHtml;

		public Interpreted(Def<T> definition, ExElement.Interpreted<?> parent) {
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
		public QuickTextArea<T> create(ExElement parent) {
			return new QuickTextArea<>(this, parent);
		}
	}

	public interface QuickTextAreaContext {
		SettableValue<Integer> getMousePosition();

		public class Default implements QuickTextAreaContext {
			private final SettableValue<Integer> theMousePosition;

			public Default(SettableValue<Integer> mousePosition) {
				theMousePosition = mousePosition;
			}

			public Default() {
				this(SettableValue.build(int.class).withDescription("mousePosition").withValue(0).build());
			}

			@Override
			public SettableValue<Integer> getMousePosition() {
				return theMousePosition;
			}
		}
	}

	private final SettableValue<SettableValue<Integer>> theRows;
	private final SettableValue<SettableValue<Boolean>> isHtml;
	private final SettableValue<SettableValue<Integer>> theMousePosition;

	public QuickTextArea(Interpreted<T> interpreted, ExElement parent) {
		super(interpreted, parent);
		theRows = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Integer>> parameterized(Integer.class))
			.build();
		isHtml = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Boolean>> parameterized(boolean.class))
			.build();
		theMousePosition = SettableValue
			.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Integer>> parameterized(int.class)).build();
	}

	public SettableValue<Integer> getRows() {
		return SettableValue.flatten(theRows, () -> 0);
	}

	public SettableValue<Boolean> isHtml() {
		return SettableValue.flatten(isHtml, () -> false);
	}

	public SettableValue<Integer> getMousePosition() {
		return SettableValue.flatten(theMousePosition, () -> 0);
	}

	public void setTextAreaContext(QuickTextAreaContext ctx) {
		theMousePosition.set(ctx.getMousePosition(), null);
	}

	@Override
	protected void updateModel(ExElement.Interpreted<?> interpreted, ModelSetInstance myModels) throws ModelInstantiationException {
		satisfyContextValue("mousePosition", ModelTypes.Value.INT, getMousePosition(), myModels);
		super.updateModel(interpreted, myModels);
		QuickTextArea.Interpreted<T> myInterpreted = (QuickTextArea.Interpreted<T>) interpreted;
		theRows.set(myInterpreted.getRows() == null ? null : myInterpreted.getRows().get(myModels), null);
		isHtml.set(myInterpreted.isHtml() == null ? null : myInterpreted.isHtml().get(myModels), null);
	}
}
