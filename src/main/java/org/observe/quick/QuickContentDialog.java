package org.observe.quick;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigChildGetter;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

public interface QuickContentDialog extends QuickDialog {
	@ExElementTraceable(toolkit = QuickCoreInterpretation.CORE,
		qonfigType = "content-dialog",
		interpretation = Interpreted.class,
		instance = QuickContentDialog.class)
	public static interface Def<D extends QuickContentDialog> extends QuickDialog.Def<D> {
		@QonfigChildGetter("content")
		QuickWidget.Def<?> getContent();

		@Override
		Interpreted<? extends D> interpret(ExElement.Interpreted<?> parent);

		public static abstract class Abstract<D extends QuickContentDialog> extends QuickDialog.Def.Abstract<D> implements Def<D> {
			private QuickWidget.Def<?> theContent;

			protected Abstract(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
				super(parent, qonfigType);
			}

			@Override
			public QuickWidget.Def<?> getContent() {
				return theContent;
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session);

				theContent = syncChild(QuickWidget.Def.class, theContent, session, "content");
			}
		}
	}

	public static interface Interpreted<D extends QuickContentDialog> extends QuickDialog.Interpreted<D> {
		QuickWidget.Interpreted<?> getContent();

		@Override
		void updateDialog(InterpretedExpressoEnv expressoEnv) throws ExpressoInterpretationException;

		@Override
		D create();

		public static abstract class Abstract<D extends QuickContentDialog> extends QuickDialog.Interpreted.Abstract<D>
		implements Interpreted<D> {
			private QuickWidget.Interpreted<?> theContent;

			protected Abstract(Def<? super D> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Def<? super D> getDefinition() {
				return (Def<? super D>) super.getDefinition();
			}

			@Override
			public QuickWidget.Interpreted<?> getContent() {
				return theContent;
			}

			@Override
			public void updateDialog(InterpretedExpressoEnv expressoEnv) throws ExpressoInterpretationException {
				update(expressoEnv);
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv expressoEnv) throws ExpressoInterpretationException {
				super.doUpdate(expressoEnv);

				theContent = syncChild(getDefinition().getContent(), theContent, def -> def.interpret(this),
					(c, cEnv) -> c.updateElement(cEnv));
			}
		}
	}

	QuickWidget getContent();

	@Override
	QuickDialog copy(ExElement parent);

	public static abstract class Abstract extends QuickDialog.Abstract implements QuickContentDialog {
		private QuickWidget theContent;

		protected Abstract(Object id) {
			super(id);
		}

		@Override
		public QuickWidget getContent() {
			return theContent;
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) {
			super.doUpdate(interpreted);

			QuickContentDialog.Interpreted<?> myInterpreted = (QuickContentDialog.Interpreted<?>) interpreted;
			if (theContent != null && theContent.getIdentity() != myInterpreted.getContent().getIdentity()) {
				theContent.destroy();
				theContent = null;
			}
			if (theContent == null)
				theContent = myInterpreted.getContent().create();
			theContent.update(myInterpreted.getContent(), this);
		}

		@Override
		public void instantiated() {
			super.instantiated();

			theContent.instantiated();
		}

		@Override
		protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
			super.doInstantiate(myModels);

			theContent.instantiate(myModels);
		}

		@Override
		public QuickContentDialog.Abstract copy(ExElement parent) {
			QuickContentDialog.Abstract copy = (QuickContentDialog.Abstract) super.copy(parent);

			copy.theContent = theContent.copy(copy);

			return copy;
		}
	}
}
