package org.observe.quick.base;

import org.observe.expresso.CompiledExpression;
import org.observe.expresso.ObservableExpression;
import org.observe.quick.QuickContainer2;
import org.observe.quick.QuickElement;
import org.observe.quick.QuickTextWidget;
import org.observe.util.TypeTokens;
import org.qommons.config.AbstractQIS;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public class QuickLabel<T> extends QuickTextWidget.Abstract<T> {
	public static class Def<T, W extends QuickLabel<T>> extends QuickTextWidget.Def.Abstract<T, W> {
		private String theStaticText;
		private CompiledExpression theTextExpression;

		public Def(QuickElement.Def<?> parent, QonfigElement element) {
			super(parent, element);
		}

		@Override
		public boolean isEditable() {
			return false;
		}

		@Override
		public CompiledExpression getValue() {
			if (theStaticText == null)
				return super.getValue();
			return theTextExpression;
		}

		@Override
		public Def<T, W> update(AbstractQIS<?> session) throws QonfigInterpretationException {
			super.update(session);
			theStaticText = session.getValueText();
			if (theStaticText != null) {
				if (super.getValue().getExpression() != ObservableExpression.EMPTY)
					throw new QonfigInterpretationException("Cannot specify both 'value' attribute and element value",
						session.getValuePosition(0), 0);
				if (getFormat() != null)
					throw new QonfigInterpretationException("Cannot specify format with and element value",
						session.getAttributeValuePosition("format", 0), 0);
			} else if (super.getValue().getExpression() == ObservableExpression.EMPTY)
				throw new QonfigInterpretationException("Must specify either 'value' attribute or element value",
					session.getElement().getPositionInFile(), 0);
			if (theStaticText != null) {
				theTextExpression = new CompiledExpression(//
					new ObservableExpression.LiteralExpression<>(theStaticText, theStaticText), session.getElement(), session.getValueDef(),
					session.getElement().getValue().position, getExpressoSession());
			}
			return this;
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
