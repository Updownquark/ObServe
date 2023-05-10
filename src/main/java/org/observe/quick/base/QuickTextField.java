package org.observe.quick.base;

import org.observe.SettableValue;
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

public class QuickTextField<T> extends QuickEditableTextWidget.Abstract<T> {
	public static class Def<T> extends QuickEditableTextWidget.Def.Abstract<T, QuickTextField<T>> {
		private Integer theColumns;
		private CompiledExpression theEmptyText;

		public Def(QuickElement.Def<?> parent, QonfigElement element) {
			super(parent, element);
		}

		@Override
		public boolean isEditable() {
			return true;
		}

		public Integer getColumns() {
			return theColumns;
		}

		public CompiledExpression getEmptyText() {
			return theEmptyText;
		}

		@Override
		public Def<T> update(AbstractQIS<?> session) throws QonfigInterpretationException {
			super.update(session);
			theColumns = session.getAttribute("columns", Integer.class);
			theEmptyText = getExpressoSession().getAttributeExpression("empty-text");
			return this;
		}

		@Override
		public Interpreted<T> interpret(QuickElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}
	}

	public static class Interpreted<T> extends QuickEditableTextWidget.Interpreted.Abstract<T, QuickTextField<T>> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theEmptyText;

		public Interpreted(Def<T> definition, QuickElement.Interpreted<?> parent) {
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
		public Interpreted<T> update(QuickInterpretationCache cache) throws ExpressoInterpretationException {
			super.update(cache);
			theEmptyText = getDefinition().getEmptyText() == null ? null
				: getDefinition().getEmptyText().evaluate(ModelTypes.Value.forType(String.class)).interpret();
			return this;
		}

		@Override
		public QuickTextField<T> create(QuickElement parent) {
			return new QuickTextField<>(this, parent);
		}
	}

	private final SettableValue<SettableValue<String>> theEmptyText;

	public QuickTextField(Interpreted<T> interpreted, QuickElement parent) {
		super(interpreted, parent);
		theEmptyText = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<String>> parameterized(String.class))
			.build();
	}

	@Override
	public Interpreted<T> getInterpreted() {
		return (Interpreted<T>) super.getInterpreted();
	}

	public SettableValue<String> getEmptyText() {
		return SettableValue.flatten(theEmptyText);
	}

	@Override
	public QuickTextField<T> update(ModelSetInstance models) throws ModelInstantiationException {
		super.update(models);
		theEmptyText.set(getInterpreted().getEmptyText() == null ? null : getInterpreted().getEmptyText().get(getModels()), null);
		return this;
	}
}
