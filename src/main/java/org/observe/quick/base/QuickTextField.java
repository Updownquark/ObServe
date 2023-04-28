package org.observe.quick.base;

import org.observe.quick.QuickContainer2;
import org.observe.quick.QuickElement;
import org.observe.util.TypeTokens;
import org.qommons.config.AbstractQIS;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public class QuickTextField<T> extends EditableTextWidget.Abstract<T> {
	public static class Def<T> extends EditableTextWidget.Def.Abstract<T, QuickTextField<T>> {
		private Integer theColumns;

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

		@Override
		public Def<T> update(AbstractQIS<?> session) throws QonfigInterpretationException {
			super.update(session);
			theColumns = session.getAttribute("columns", Integer.class);
			return this;
		}

		@Override
		public Interpreted<T> interpret(org.observe.quick.QuickContainer2.Interpreted<?, ?> parent) {
			return new Interpreted<>(this, parent);
		}
	}

	public static class Interpreted<T> extends EditableTextWidget.Interpreted.Abstract<T, QuickTextField<T>> {
		public Interpreted(Def<T> definition, QuickContainer2.Interpreted<?, ?> parent) {
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

		@Override
		public QuickTextField<T> create(QuickContainer2<?> parent) {
			return new QuickTextField<>(this, parent);
		}
	}

	public QuickTextField(Interpreted<T> interpreted, QuickElement parent) {
		super(interpreted, parent);
	}

	@Override
	public Interpreted<T> getInterpreted() {
		return (Interpreted<T>) super.getInterpreted();
	}
}
