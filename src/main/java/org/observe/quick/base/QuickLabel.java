package org.observe.quick.base;

import org.observe.expresso.CompiledExpression;
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ObservableExpression;
import org.observe.quick.QuickElement;
import org.observe.quick.QuickTextWidget;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.io.LocatedContentPosition;

import com.google.common.reflect.TypeToken;

public class QuickLabel<T> extends QuickTextWidget.Abstract<T> {
	public static class Def<T, W extends QuickLabel<T>> extends QuickTextWidget.Def.Abstract<T, W> {
		private String theStaticText;
		private CompiledExpression theTextExpression;

		public Def(QuickElement.Def<?> parent, QonfigElement element) {
			super(parent, element);
		}

		@Override
		public boolean isTypeEditable() {
			return false;
		}

		@Override
		public CompiledExpression getValue() {
			if (theStaticText == null)
				return super.getValue();
			return theTextExpression;
		}

		@Override
		public Def<T, W> update(ExpressoQIS session) throws QonfigInterpretationException {
			super.update(session);
			String staticText = session.getValueText();
			if (staticText.isEmpty())
				staticText = null;
			if (staticText != null) {
				if (super.getValue().getExpression() != ObservableExpression.EMPTY)
					throw new QonfigInterpretationException("Cannot specify both 'value' attribute and element value",
						session.getValuePosition(0), 0);
				if (getFormat() != null)
					throw new QonfigInterpretationException("Cannot specify format with and element value",
						session.getAttributeValuePosition("format", 0), 0);
			} else if (super.getValue().getExpression() == ObservableExpression.EMPTY)
				throw new QonfigInterpretationException("Must specify either 'value' attribute or element value",
					session.getElement().getPositionInFile(), 0);
			theStaticText = staticText;
			if (theStaticText != null) {
				theTextExpression = new CompiledExpression(//
					new ObservableExpression.LiteralExpression<>(theStaticText, theStaticText), session.getElement(), session.getValueDef(),
					LocatedContentPosition.of(session.getElement().getDocument().getLocation(), session.getElement().getValue().position),
					session);
			}
			return this;
		}

		@Override
		public Interpreted<T, ? extends W> interpret(QuickElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}
	}

	public static class Interpreted<T, W extends QuickLabel<T>> extends QuickTextWidget.Interpreted.Abstract<T, W> {
		public Interpreted(QuickLabel.Def<T, ? super W> definition, QuickElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public TypeToken<W> getWidgetType() {
			return (TypeToken<W>) TypeTokens.get().keyFor(QuickLabel.class).parameterized(getValueType());
		}

		@Override
		public W create(QuickElement parent) {
			return (W) new QuickLabel<>(this, parent);
		}
	}

	public QuickLabel(Interpreted<T, ?> interpreted, QuickElement parent) {
		super(interpreted, parent);
	}

	@Override
	public QuickLabel.Interpreted<T, ?> getInterpreted() {
		return (Interpreted<T, ?>) super.getInterpreted();
	}
}
