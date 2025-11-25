package org.observe.quick.base;

import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

/** A container that arranges its components vertically as fields in a form */
public class QuickFieldPanel extends QuickVariableContainer {
	/** The XML name of this element */
	public static final String FIELD_PANEL = "field-panel";

	/**
	 * {@link QuickFieldPanel} definition
	 *
	 * @param <P> The sub-type of field panel to create
	 */
	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = FIELD_PANEL,
		interpretation = Interpreted.class,
		instance = QuickFieldPanel.class)
	public static class Def<P extends QuickFieldPanel> extends QuickVariableContainer.Def<P> {
		private boolean isShowInvisible;

		/**
		 * @param parent The parent element of the widget
		 * @param type The Qonfig type of the widget
		 */
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		/** @return Whether invisible components in this field panel should occupy space */
		@QonfigAttributeGetter("show-invisible")
		public boolean isShowInvisible() {
			return isShowInvisible;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));
			isShowInvisible = session.getAttribute("show-invisible", boolean.class);
		}

		@Override
		public Interpreted<P> interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}
	}

	/**
	 * {@link QuickFieldPanel} interpretation
	 *
	 * @param <P> The sub-type of field panel to create
	 */
	public static class Interpreted<P extends QuickFieldPanel> extends QuickVariableContainer.Interpreted<P> {
		/**
		 * @param definition The definition to interpret
		 * @param parent The parent element for the widget
		 */
		protected Interpreted(Def<P> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def<P> getDefinition() {
			return (Def<P>) super.getDefinition();
		}

		@Override
		public P create() {
			return (P) new QuickFieldPanel(getIdentity());
		}
	}

	private boolean isShowInvisible;

	/** @param id The element ID for this widget */
	protected QuickFieldPanel(Object id) {
		super(id);
	}

	/** @return Whether invisible components in this field panel should occupy space */
	public boolean isShowInvisible() {
		return isShowInvisible;
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);
		Interpreted<?> myInterpreted = (Interpreted<?>) interpreted;
		isShowInvisible = myInterpreted.getDefinition().isShowInvisible();
	}
}
