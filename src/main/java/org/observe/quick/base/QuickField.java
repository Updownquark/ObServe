package org.observe.quick.base;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ExAddOn;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.quick.QuickWidget;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;

/** An add-on automatically inherited by widget contents in a {@link QuickFieldPanel} */
public class QuickField extends ExAddOn.Abstract<QuickWidget> {
	/** The XML name of this add-on */
	public static final String FIELD = "field";

	/** {@link QuickField} definition */
	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = FIELD,
		interpretation = Interpreted.class,
		instance = QuickField.class)
	public static class Def extends ExAddOn.Def.Abstract<QuickWidget, QuickField> {
		private CompiledExpression theFieldLabel;
		private boolean isFill;
		private boolean isVFill;

		/**
		 * @param type The Qonfig type of this add-on
		 * @param element The content widget
		 */
		public Def(QonfigAddOn type, QuickWidget.Def<?> element) {
			super(type, element);
		}

		/** @return The text label for the field widget */
		@QonfigAttributeGetter("field-label")
		public CompiledExpression getFieldLabel() {
			return theFieldLabel;
		}

		/** @return Whether the widget should be stretched to fill the horizontal space of the container */
		@QonfigAttributeGetter("fill")
		public boolean isFill() {
			return isFill;
		}

		/** @return Whether the widget should be stretched to fill the vertical space of the container */
		@QonfigAttributeGetter("v-fill")
		public boolean isVFill() {
			return isVFill;
		}

		@Override
		public void update(ExpressoQIS session, ExElement.Def<? extends QuickWidget> element) throws QonfigInterpretationException {
			super.update(session, element);
			theFieldLabel = element.getAttributeExpression("field-label", session);
			isFill = Boolean.TRUE.equals(session.getAttribute("fill", Boolean.class));
			isVFill = Boolean.TRUE.equals(session.getAttribute("v-fill", Boolean.class));
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<?> element) {
			return new Interpreted(this, (QuickWidget.Interpreted<?>) element);
		}
	}

	/** {@link QuickField} interpretation */
	public static class Interpreted extends ExAddOn.Interpreted.Abstract<QuickWidget, QuickField> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theName;

		/**
		 * @param definition The definition to interpret
		 * @param element The content widget
		 */
		protected Interpreted(Def definition, QuickWidget.Interpreted<?> element) {
			super(definition, element);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		@Override
		public QuickWidget.Interpreted<?> getElement() {
			return (QuickWidget.Interpreted<?>) super.getElement();
		}

		/** @return The text label for the field widget */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getFieldLabel() {
			return theName;
		}

		@Override
		public void update(ExElement.Interpreted<? extends QuickWidget> element) throws ExpressoInterpretationException {
			super.update(element);
			theName = getElement().interpret(getDefinition().getFieldLabel(), ModelTypes.Value.STRING);
		}

		@Override
		public Class<QuickField> getInstanceType() {
			return QuickField.class;
		}

		@Override
		public QuickField create(QuickWidget widget) {
			return new QuickField(widget);
		}
	}

	private ModelValueInstantiator<SettableValue<String>> theFieldLabelInstantiator;
	private SettableValue<String> theFieldLabel;

	/** @param widget The content widget */
	protected QuickField(QuickWidget widget) {
		super(widget);
	}

	/** @return The text label for the field widget */
	public SettableValue<String> getFieldLabel() {
		return theFieldLabel;
	}

	@Override
	public Class<Interpreted> getInterpretationType() {
		return Interpreted.class;
	}

	@Override
	public void update(ExAddOn.Interpreted<? extends QuickWidget, ?> interpreted, QuickWidget element) throws ModelInstantiationException {
		super.update(interpreted, element);
		QuickField.Interpreted myInterpreted = (QuickField.Interpreted) interpreted;
		theFieldLabelInstantiator = myInterpreted.getFieldLabel() == null ? null : myInterpreted.getFieldLabel().instantiate();
	}

	@Override
	public void instantiate(ModelSetInstance models) throws ModelInstantiationException {
		super.instantiate(models);
		theFieldLabel = theFieldLabelInstantiator == null ? null : theFieldLabelInstantiator.get(models);
	}
}
