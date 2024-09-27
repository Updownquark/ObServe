package org.observe.quick.base;

import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.quick.QuickWidget;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

/** A vertical or horizontal bar to visually separator components in a container */
public class QuickSeparator extends QuickWidget.Abstract {
	/** The XML name of this element */
	public static final String SEPARATOR = "separator";

	/** {@link QuickSeparator} definition */
	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = SEPARATOR,
		interpretation = Interpreted.class,
		instance = QuickSeparator.class)
	public static class Def extends QuickWidget.Def.Abstract<QuickSeparator> {
		private boolean isVertical;

		/**
		 * @param parent The parent element of the widget
		 * @param type The Qonfig type of the widget
		 */
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		/** @return Whether the separator is a vertical or a horizontal bar */
		@QonfigAttributeGetter("orientation")
		public boolean isVertical() {
			return isVertical;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			isVertical = "vertical".equalsIgnoreCase(session.getAttributeText("orientation"));
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}
	}

	/** {@link QuickSeparator} interpretation */
	public static class Interpreted extends QuickWidget.Interpreted.Abstract<QuickSeparator> {
		/**
		 * @param definition The definition to interpret
		 * @param parent The parent element for the widget
		 */
		protected Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		@Override
		public QuickSeparator create() {
			return new QuickSeparator(getIdentity());
		}
	}

	private boolean isVertical;

	/** @param id The element ID for this widget */
	protected QuickSeparator(Object id) {
		super(id);
	}

	/** @return Whether the separator is a vertical or a horizontal bar */
	public boolean isVertical() {
		return isVertical;
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);
		Interpreted myInterpreted = (Interpreted) interpreted;
		isVertical = myInterpreted.getDefinition().isVertical();
	}
}
