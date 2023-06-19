package org.observe.quick.base;

import org.observe.SettableValue;
import org.observe.expresso.CompiledExpression;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ExElement;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public class QuickTextField<T> extends QuickEditableTextWidget.Abstract<T> {
	public static final String TEXT_FIELD = "text-field";

	private static final ExElement.AttributeValueGetter.Expression<QuickTextField<?>, Interpreted<?>, Def<?>, SettableValue<?>, SettableValue<String>> EMPTY_TEXT = ExElement.AttributeValueGetter
		.ofX(Def::getEmptyText, Interpreted::getEmptyText, QuickTextField::getEmptyText,
			"The text cue to display to the user when the text field has no value");
	private static final ExElement.AttributeValueGetter<QuickTextField<?>, Interpreted<?>, Def<?>> COLUMNS = ExElement.AttributeValueGetter
		.of(Def::getColumns, i -> i.getDefinition().getColumns(), QuickTextField::getColumns,
			"The number of columns of characters to configure the text field's width");

	public static class Def<T> extends QuickEditableTextWidget.Def.Abstract<T, QuickTextField<T>> {
		private Integer theColumns;
		private CompiledExpression theEmptyText;

		public Def(ExElement.Def<?> parent, QonfigElement element) {
			super(parent, element);
		}

		@Override
		public boolean isTypeEditable() {
			return true;
		}

		public Integer getColumns() {
			return theColumns;
		}

		public CompiledExpression getEmptyText() {
			return theEmptyText;
		}

		@Override
		public void update(ExpressoQIS session) throws QonfigInterpretationException {
			ExElement.checkElement(session.getFocusType(), QuickBaseInterpretation.NAME, QuickBaseInterpretation.VERSION, TEXT_FIELD);
			forAttribute(session.getAttributeDef(null, null, "empty-text"), EMPTY_TEXT);
			forAttribute(session.getAttributeDef(null, null, "columns"), COLUMNS);
			super.update(session.asElement(session.getFocusType().getSuperElement()));
			theColumns = session.getAttribute("columns", Integer.class);
			theEmptyText = session.getAttributeExpression("empty-text");
		}

		@Override
		public Interpreted<T> interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}
	}

	public static class Interpreted<T> extends QuickEditableTextWidget.Interpreted.Abstract<T, QuickTextField<T>> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theEmptyText;

		public Interpreted(Def<T> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public QuickTextField.Def<T> getDefinition() {
			return (Def<T>) super.getDefinition();
		}

		@Override
		public TypeToken<QuickTextField<T>> getWidgetType() {
			return TypeTokens.get().keyFor(QuickTextField.class).parameterized(getValueType());
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getEmptyText() {
			return theEmptyText;
		}

		@Override
		public void update(QuickInterpretationCache cache) throws ExpressoInterpretationException {
			super.update(cache);
			theEmptyText = getDefinition().getEmptyText() == null ? null
				: getDefinition().getEmptyText().evaluate(ModelTypes.Value.forType(String.class)).interpret();
		}

		@Override
		public QuickTextField<T> create(ExElement parent) {
			return new QuickTextField<>(this, parent);
		}
	}

	private Integer theColumns;
	private final SettableValue<SettableValue<String>> theEmptyText;

	public QuickTextField(Interpreted<T> interpreted, ExElement parent) {
		super(interpreted, parent);
		theEmptyText = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<String>> parameterized(String.class))
			.build();
	}

	public Integer getColumns() {
		return theColumns;
	}

	public SettableValue<String> getEmptyText() {
		return SettableValue.flatten(theEmptyText);
	}

	@Override
	protected void updateModel(ExElement.Interpreted<?> interpreted, ModelSetInstance myModels) throws ModelInstantiationException {
		super.updateModel(interpreted, myModels);
		QuickTextField.Interpreted<T> myInterpreted = (QuickTextField.Interpreted<T>) interpreted;
		theColumns = myInterpreted.getDefinition().getColumns();
		theEmptyText.set(myInterpreted.getEmptyText() == null ? null : myInterpreted.getEmptyText().get(myModels), null);
	}
}
