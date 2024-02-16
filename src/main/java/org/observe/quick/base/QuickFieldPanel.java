package org.observe.quick.base;

import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.quick.QuickContainer;
import org.observe.quick.QuickWidget;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

/** A container that arranges its components vertically as fields in a form */
public class QuickFieldPanel extends QuickContainer.Abstract<QuickWidget> {
	/** The XML name of this element */
	public static final String FIELD_PANEL = "field-panel";

	/** {@link QuickFieldPanel} definition */
	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = FIELD_PANEL,
		interpretation = Interpreted.class,
		instance = QuickFieldPanel.class)
	public static class Def extends QuickContainer.Def.Abstract<QuickFieldPanel, QuickWidget> {
		/**
		 * @param parent The parent element of the widget
		 * @param type The Qonfig type of the widget
		 */
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}
	}

	/** {@link QuickFieldPanel} interpretation */
	public static class Interpreted extends QuickContainer.Interpreted.Abstract<QuickFieldPanel, QuickWidget> {
		/**
		 * @param definition The definition to interpret
		 * @param parent The parent element for the widget
		 */
		protected Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public QuickFieldPanel create() {
			return new QuickFieldPanel(getIdentity());
		}
	}

	/** @param id The element ID for this widget */
	protected QuickFieldPanel(Object id) {
		super(id);
	}
}
