package org.observe.quick.base;

import javax.swing.Icon;

import org.observe.SettableValue;
import org.observe.expresso.CompiledExpression;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ExElement;
import org.observe.quick.QuickTextWidget;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.ex.ExFunction;
import org.qommons.io.LocatedPositionedContent;

import com.google.common.reflect.TypeToken;

public class QuickLabel<T> extends QuickTextWidget.Abstract<T> {
	public static final String LABEL = "label";

	public static final ExElement.AttributeValueGetter<QuickLabel<?>, Interpreted<?, ?>, Def<?, ?>> VALUE_TEXT = ExElement.AttributeValueGetter
		.<QuickLabel<?>, Interpreted<?, ?>, Def<?, ?>> of(Def::getValueText, null, null,
			"For a label, either the 'value' attribute or the value text may specified on a label.  The value text is not an expression, but static text.");
	public static final ExElement.AttributeValueGetter<QuickLabel<?>, Interpreted<?, ?>, Def<?, ?>> ICON = ExElement.AttributeValueGetter
		.<QuickLabel<?>, Interpreted<?, ?>, Def<?, ?>> of(Def::getIcon, Interpreted::getIcon, QuickLabel::getIcon,
			"The icon to display for the label");

	public static class Def<T, W extends QuickLabel<T>> extends QuickTextWidget.Def.Abstract<T, W> {
		private String theStaticText;
		private CompiledExpression theTextExpression;
		private CompiledExpression theIcon;;

		public Def(ExElement.Def<?> parent, QonfigElement element) {
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

		public String getValueText() {
			return theStaticText;
		}

		public CompiledExpression getIcon() {
			return theIcon;
		}

		@Override
		public void update(ExpressoQIS session) throws QonfigInterpretationException {
			ExElement.checkElement(session.getFocusType(), QuickBaseInterpretation.NAME, QuickBaseInterpretation.VERSION, LABEL);
			forAttribute(session.getAttributeDef(null, null, "icon"), ICON);
			forValue(VALUE_TEXT);
			super.update(session.asElement(session.getFocusType().getSuperElement()));
			String staticText = session.getValueText();
			if (staticText != null && staticText.isEmpty())
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
					LocatedPositionedContent.of(session.getElement().getDocument().getLocation(), session.getElement().getValue().position),
					session);
			}
			theIcon = session.getAttributeExpression("icon");
		}

		@Override
		public Interpreted<T, ? extends W> interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}
	}

	public static class Interpreted<T, W extends QuickLabel<T>> extends QuickTextWidget.Interpreted.Abstract<T, W> {
		private ExFunction<ModelSetInstance, SettableValue<Icon>, ModelInstantiationException> theIcon;

		public Interpreted(QuickLabel.Def<T, ? super W> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def<T, ? super W> getDefinition() {
			return (Def<T, ? super W>) super.getDefinition();
		}

		public ExFunction<ModelSetInstance, SettableValue<Icon>, ModelInstantiationException> getIcon() {
			return theIcon;
		}

		public void setIcon(ExFunction<ModelSetInstance, SettableValue<Icon>, ModelInstantiationException> icon) {
			theIcon = icon;
		}

		@Override
		public TypeToken<W> getWidgetType() {
			return (TypeToken<W>) TypeTokens.get().keyFor(QuickLabel.class).parameterized(getValueType());
		}

		@Override
		public void update(QuickInterpretationCache cache) throws ExpressoInterpretationException {
			super.update(cache);
			theIcon = getDefinition().getIcon() == null ? null : QuickBaseInterpretation.evaluateIcon(getDefinition().getIcon(),
				getDefinition().getExpressoEnv(), getDefinition().getCallingClass());
		}

		@Override
		public W create(ExElement parent) {
			return (W) new QuickLabel<>(this, parent);
		}
	}

	private SettableValue<Icon> theIcon;

	public QuickLabel(Interpreted<T, ?> interpreted, ExElement parent) {
		super(interpreted, parent);
	}

	public SettableValue<Icon> getIcon() {
		return theIcon;
	}

	@Override
	protected void updateModel(ExElement.Interpreted<?> interpreted, ModelSetInstance myModels) throws ModelInstantiationException {
		super.updateModel(interpreted, myModels);
		QuickLabel.Interpreted<T, ?> myInterpreted = (QuickLabel.Interpreted<T, ?>) interpreted;
		theIcon = myInterpreted.getIcon() == null ? null : myInterpreted.getIcon().apply(myModels);
	}
}
