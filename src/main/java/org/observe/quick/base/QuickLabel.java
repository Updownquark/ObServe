package org.observe.quick.base;

import org.observe.expresso.ObservableExpression;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.quick.QuickTextWidget;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.io.LocatedPositionedContent;

/**
 * A simple text label, icon, or both
 *
 * @param <T> The type of value to represent
 */
public class QuickLabel<T> extends QuickTextWidget.Abstract<T> {
	/** The XML name of this element */
	public static final String LABEL = "label";

	/**
	 * {@link QuickLabel} definition
	 *
	 * @param <W> The sub-type of label to create
	 */
	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = LABEL,
		interpretation = Interpreted.class,
		instance = QuickLabel.class)
	public static class Def<W extends QuickLabel<?>> extends QuickTextWidget.Def.Abstract<W> {
		private String theStaticText;
		private CompiledExpression theTextExpression;

		/**
		 * @param parent The parent element of the widget
		 * @param type The Qonfig type of the widget
		 */
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

		/** @return The value text to use in place of a dynamic value attribute */
		@QonfigAttributeGetter
		public String getValueText() {
			return theStaticText;
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
						session.getValue().getLocatedContent());
				if (getFormat() != null)
					throw new QonfigInterpretationException("Cannot specify format with an element value",
						session.attributes().get("format").getLocatedContent());
			}
			theStaticText = staticText;
			if (theStaticText != null) {
				theTextExpression = new CompiledExpression(//
					new ObservableExpression.LiteralExpression<>(theStaticText, theStaticText), session.getElement(),
					LocatedPositionedContent.of(session.getElement().getDocument().getLocation(), session.getElement().getValue().position),
					this::getExpressoEnv);
			}
		}

		@Override
		public Interpreted<?, ? extends W> interpret(ExElement.Interpreted<?> parent) {
			// Stupid generics
			return (Interpreted<?, ? extends W>) new Interpreted<>((Def<QuickLabel<Object>>) this, parent);
		}
	}

	/**
	 * {@link QuickLabel} interpretation
	 *
	 * @param <T> The type of value to represent
	 * @param <W> The sub-type of label to create
	 */
	public static class Interpreted<T, W extends QuickLabel<T>> extends QuickTextWidget.Interpreted.Abstract<T, W> {
		/**
		 * @param definition The definition to interpret
		 * @param parent The parent element for the widget
		 */
		protected Interpreted(QuickLabel.Def<? super W> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def<? super W> getDefinition() {
			return (Def<? super W>) super.getDefinition();
		}

		@Override
		public W create() {
			return (W) new QuickLabel<>(getIdentity());
		}
	}

	/** @param id The element ID for this widget */
	protected QuickLabel(Object id) {
		super(id);
	}

	@Override
	public QuickLabel<T> copy(ExElement parent) {
		return (QuickLabel<T>) super.copy(parent);
	}
}
