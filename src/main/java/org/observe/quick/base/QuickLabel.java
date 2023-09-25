package org.observe.quick.base;

import javax.swing.Icon;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
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

	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = LABEL,
		interpretation = Interpreted.class,
		instance = QuickLabel.class)
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
					new ObservableExpression.LiteralExpression<>(theStaticText, theStaticText), session.getElement(),
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
			theIcon = getDefinition().getIcon() == null ? null : QuickBaseInterpretation.evaluateIcon(getDefinition().getIcon(), env,
				getDefinition().getElement().getDocument().getLocation());
		}

		@Override
		protected void checkValidModel() throws ExpressoInterpretationException {
			super.checkValidModel();
			if (getDefinition().getValue().getExpression() == ObservableExpression.EMPTY && getDefinition().getValueText() == null
				&& getIcon() == null)
				reporting().warn("Label has no value, text, or icon");
		}

		@Override
		public W create() {
			return (W) new QuickLabel<>(getIdentity());
		}
	}

	private ModelValueInstantiator<SettableValue<Icon>> theIconInstantiator;
	private SettableValue<Icon> theIcon;

	public QuickLabel(Object id) {
		super(id);
	}

	public SettableValue<Icon> getIcon() {
		return theIcon;
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) {
		super.doUpdate(interpreted);
		QuickLabel.Interpreted<T, ?> myInterpreted = (QuickLabel.Interpreted<T, ?>) interpreted;
		theIconInstantiator = myInterpreted.getIcon() == null ? null : myInterpreted.getIcon().instantiate();
	}

	@Override
	public void instantiated() {
		super.instantiated();
		if (theIconInstantiator != null)
			theIconInstantiator.instantiate();
	}

	@Override
	protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		super.doInstantiate(myModels);
		theIcon = theIconInstantiator == null ? null : theIconInstantiator.get(myModels);
	}
}
