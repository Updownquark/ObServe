package org.observe.quick.base;

import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.quick.QuickContentDialog;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

/** A general dialog that may contain a widget with any content */
public class GeneralDialog extends QuickContentDialog.Abstract {
	/** The XML name of this element */
	public static final String GENERAL_DIALOG = "general-dialog";

	/**
	 * {@link GeneralDialog} definition
	 *
	 * @param <D> The sub-type of dialog to create
	 */
	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = GENERAL_DIALOG,
		interpretation = Interpreted.class,
		instance = GeneralDialog.class)
	public static class Def<D extends GeneralDialog> extends QuickContentDialog.Def.Abstract<D> {
		private boolean isModal;
		private boolean isAlwaysOnTop;

		/**
		 * @param parent The parent element of the dialog
		 * @param qonfigType The Qonfig type of the dialog
		 */
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		/** @return Whether the dialog is modal (preventing interaction with the rest of the document as long as it is open) */
		@QonfigAttributeGetter("modal")
		public boolean isModal() {
			return isModal;
		}

		/** @return Whether the dialog should always show on top of other windows */
		@QonfigAttributeGetter("always-on-top")
		public boolean isAlwaysOnTop() {
			return isAlwaysOnTop;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			isModal = session.getAttribute("modal", boolean.class);
			isAlwaysOnTop = session.getAttribute("always-on-top", boolean.class);
		}

		@Override
		public Interpreted<? extends D> interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}
	}

	/**
	 * {@link GeneralDialog} interpretation
	 *
	 * @param <D> The sub-type of dialog to create
	 */
	public static class Interpreted<D extends GeneralDialog> extends QuickContentDialog.Interpreted.Abstract<D> {
		/**
		 * @param definition The definition to interpret
		 * @param parent The parent element for the dialog
		 */
		protected Interpreted(Def<? super D> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def<? super D> getDefinition() {
			return (Def<? super D>) super.getDefinition();
		}

		@Override
		public D create() {
			return (D) new GeneralDialog(getIdentity());
		}
	}

	private boolean isModal;
	private boolean isAlwaysOnTop;

	/** @param id The element ID for this dialog */
	protected GeneralDialog(Object id) {
		super(id);
	}

	/** @return Whether the dialog is modal (preventing interaction with the rest of the document as long as it is open) */
	public boolean isModal() {
		return isModal;
	}

	/** @return Whether the dialog should always show on top of other windows */
	public boolean isAlwaysOnTop() {
		return isAlwaysOnTop;
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);

		Interpreted<?> myInterpreted = (Interpreted<?>) interpreted;
		isModal = myInterpreted.getDefinition().isModal();
		isAlwaysOnTop = myInterpreted.getDefinition().isAlwaysOnTop();
	}
}
