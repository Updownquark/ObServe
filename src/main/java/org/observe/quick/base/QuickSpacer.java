package org.observe.quick.base;

import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.quick.QuickWidget;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

/** An invisible widget that just adds space between other contents in a container managed by a layout */
public class QuickSpacer extends QuickWidget.Abstract {
	/** The XML name of this element */
	public static final String SPACER = "spacer";

	/** {@link QuickSpacer} definition */
	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = SPACER,
		interpretation = Interpreted.class,
		instance = QuickSpacer.class)
	public static class Def extends QuickWidget.Def.Abstract<QuickSpacer> {
		private int theLength;

		/**
		 * @param parent The parent element of the widget
		 * @param type The Qonfig type of the widget
		 */
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		/** @return The size of the spacer */
		@QonfigAttributeGetter("length")
		public int getLength() {
			return theLength;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			theLength = Integer.parseInt(session.getAttributeText("length"));
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}
	}

	/** {@link QuickSpacer} interpretation */
	public static class Interpreted extends QuickWidget.Interpreted.Abstract<QuickSpacer> {
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
		public QuickSpacer create() {
			return new QuickSpacer(getIdentity());
		}
	}

	private int theLength;

	/** @param id The element ID for this widget */
	protected QuickSpacer(Object id) {
		super(id);
	}

	/** @return The size of the spacer */
	public int getLength() {
		return theLength;
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);
		Interpreted myInterpreted = (Interpreted) interpreted;
		theLength = myInterpreted.getDefinition().getLength();
	}
}
