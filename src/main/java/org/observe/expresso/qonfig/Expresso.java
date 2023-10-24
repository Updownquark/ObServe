package org.observe.expresso.qonfig;

import org.observe.expresso.ClassView;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.qonfig.ExElement.Interpreted;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

/** A simple structure consisting of a class view and models, the definition for a set of models for an application */
public class Expresso extends ExElement.Interpreted.Abstract<ExElement> {
	@ExElementTraceable(toolkit = ExpressoSessionImplV0_1.CORE, qonfigType = "expresso", interpretation = Expresso.class)
	public static class Def extends ExElement.Def.Abstract<ExElement> {
		private ClassViewElement theClassView;
		private ObservableModelElement.ModelSetElement.Def<?> theModels;

		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@QonfigChildGetter("imports")
		public ClassViewElement getClassViewElement() {
			return theClassView;
		}

		@QonfigChildGetter("models")
		public ObservableModelElement.ModelSetElement.Def<?> getModelElement() {
			return theModels;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			theClassView = syncChild(ClassViewElement.class, theClassView, session, "imports");
			theModels = syncChild(ObservableModelElement.ModelSetElement.Def.class, theModels, session, "models");
			if (theModels != null) {
				setExpressoEnv(theModels.getExpressoEnv());
				session.setExpressoEnv(getExpressoEnv());
			}
		}

		public Expresso interpret(ExElement.Interpreted<?> parent) {
			return new Expresso(this, parent);
		}
	}

	private ClassView theClassView;
	private ObservableModelElement.ModelSetElement.Interpreted<?> theModels;

	public Expresso(Def definition, Interpreted<?> parent) {
		super(definition, parent);
	}

	@Override
	public Def getDefinition() {
		return (Def) super.getDefinition();
	}

	/** @return The class view of this expresso structure */
	public ClassView getClassView() {
		return theClassView;
	}

	/** @return The models of this expresso structure */
	public ObservableModelElement.ModelSetElement.Interpreted<?> getModelElement() {
		return theModels;
	}

	public void updateExpresso(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
		update(env);
		getExpressoEnv().getModels().interpret(getExpressoEnv());
	}

	@Override
	protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
		// Interpret the class view first, so the models (which are interpreted in the super call below) can use the imports
		theClassView = getDefinition().getClassViewElement() == null ? null
			: getDefinition().getClassViewElement().configureClassView(env.getClassView().copy()).build();
		if (theClassView != null)
			env = env.with(theClassView);
		super.doUpdate(env);
		if (getDefinition().getModelElement() == null) {
			if (theModels != null)
				theModels.destroy();
			theModels = null;
		} else {
			theModels = syncChild(getDefinition().getModelElement(), theModels, def -> def.interpret(this), (m, mEnv) -> m.update(mEnv));
			setExpressoEnv(getExpressoEnv().with(theModels.getModels()));
		}
	}
}
