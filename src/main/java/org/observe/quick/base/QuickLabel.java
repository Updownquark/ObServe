package org.observe.quick.base;

import org.observe.quick.QuickContainer2;
import org.observe.quick.QuickTextWidget;
import org.observe.util.TypeTokens;
import org.qommons.config.AbstractQIS;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public class QuickLabel<T> extends QuickTextWidget.Abstract<T> {
	public static class Def<T, W extends QuickLabel<T>> extends QuickTextWidget.Def.Abstract<T, W> {
		public Def(AbstractQIS<?> session) throws QonfigInterpretationException {
			super(session);
		}

		@Override
		public boolean isEditable() {
			return false;
		}

		@Override
		public Interpreted<T, ? extends W> interpret(QuickContainer2.Interpreted<?, ?> parent) {
			return new Interpreted<>(this, parent);
		}
	}

	public static class Interpreted<T, W extends QuickLabel<T>> extends QuickTextWidget.Interpreted.Abstract<T, W> {
		public Interpreted(QuickLabel.Def<T, ? super W> definition, QuickContainer2.Interpreted<?, ?> parent) {
			super(definition, parent);
		}

		@Override
		public TypeToken<W> getWidgetType() {
			return (TypeToken<W>) TypeTokens.get().keyFor(QuickLabel.class).parameterized(getValueType());
		}

		@Override
		public W create(QuickContainer2<?> parent) {
			return (W) new QuickLabel<>(this, parent);
		}
	}

	public QuickLabel(Interpreted<T, ?> interpreted, QuickContainer2<?> parent) {
		super(interpreted, parent);
	}

	@Override
	public QuickLabel.Interpreted<T, ?> getInterpreted() {
		return (Interpreted<T, ?>) super.getInterpreted();
	}
}
