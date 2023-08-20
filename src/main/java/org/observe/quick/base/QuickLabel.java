package org.observe.quick.base;

import javax.swing.Icon;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ElementTypeTraceability;
import org.observe.expresso.qonfig.ElementTypeTraceability.SingleTypeTraceability;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.quick.QuickTextWidget;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.io.LocatedPositionedContent;

import com.google.common.reflect.TypeToken;

public class QuickLabel<T> extends QuickTextWidget.Abstract<T> {
	public static final String LABEL = "label";
	private static final SingleTypeTraceability<QuickLabel<?>, Interpreted<?, ?>, Def<?>> TRACEABILITY = ElementTypeTraceability
		.getElementTraceability(QuickBaseInterpretation.NAME, QuickBaseInterpretation.VERSION, LABEL, Def.class, Interpreted.class,
			QuickLabel.class);

	public static class Def<W extends QuickLabel<?>> extends QuickTextWidget.Def.Abstract<W> {
		private String theStaticText;
		private CompiledExpression theTextExpression;
		private CompiledExpression theIcon;

		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
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

		@QonfigAttributeGetter
		public String getValueText() {
			return theStaticText;
		}

		@QonfigAttributeGetter("icon")
		public CompiledExpression getIcon() {
			return theIcon;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			withTraceability(TRACEABILITY.validate(session.getFocusType(), session.reporting()));
			super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));
			String staticText = session.getValueText();
			if (staticText != null && staticText.isEmpty())
				staticText = null;
			if (staticText != null) {
				if (super.getValue().getExpression() != ObservableExpression.EMPTY)
					throw new QonfigInterpretationException("Cannot specify both 'value' attribute and element value",
						session.getValuePosition(0), 0);
				if (getFormat() != null)
					throw new QonfigInterpretationException("Cannot specify format with an element value",
						session.getAttributeValuePosition("format", 0), 0);
			}
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
		public Interpreted<?, ? extends W> interpret(ExElement.Interpreted<?> parent) {
			// Stupid generics
			return (Interpreted<?, ? extends W>) new Interpreted<>((Def<QuickLabel<Object>>) this, parent);
		}
	}

	public static class Interpreted<T, W extends QuickLabel<T>> extends QuickTextWidget.Interpreted.Abstract<T, W> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Icon>> theIcon;

		public Interpreted(QuickLabel.Def<? super W> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def<? super W> getDefinition() {
			return (Def<? super W>) super.getDefinition();
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<Icon>> getIcon() {
			return theIcon;
		}

		@Override
		public TypeToken<W> getWidgetType() throws ExpressoInterpretationException {
			return (TypeToken<W>) TypeTokens.get().keyFor(QuickLabel.class).parameterized(getValueType());
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			super.doUpdate(env);
			theIcon = getDefinition().getIcon() == null ? null
				: QuickBaseInterpretation.evaluateIcon(getDefinition().getIcon(), env,
					getDefinition().getElement().getDocument().getLocation());
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
		theIcon = myInterpreted.getIcon() == null ? null : myInterpreted.getIcon().instantiate().get(myModels);
	}
}
