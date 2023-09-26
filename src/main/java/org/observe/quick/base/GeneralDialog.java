package org.observe.quick.base;

import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.quick.QuickContentDialog;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

public class GeneralDialog extends QuickContentDialog.Abstract {
	public static final String GENERAL_DIALOG = "general-dialog";

	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = GENERAL_DIALOG,
		interpretation = Interpreted.class,
		instance = GeneralDialog.class)
	public static class Def<D extends GeneralDialog> extends QuickContentDialog.Def.Abstract<D> {
		private boolean isModal;

		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@QonfigAttributeGetter("modal")
		public boolean isModal() {
			return isModal;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			isModal = session.getAttribute("modal", boolean.class);
		}

		@Override
		public Interpreted<? extends D> interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}
	}

	public static class Interpreted<D extends GeneralDialog> extends QuickContentDialog.Interpreted.Abstract<D> {
		public Interpreted(Def<? super D> definition, ExElement.Interpreted<?> parent) {
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

	public GeneralDialog(Object id) {
		super(id);
	}

	public boolean isModal() {
		return isModal;
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) {
		super.doUpdate(interpreted);

		Interpreted<?> myInterpreted = (Interpreted<?>) interpreted;
		isModal = myInterpreted.getDefinition().isModal();
	}
}
