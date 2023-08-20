package org.observe.expresso.qonfig;

import org.observe.expresso.ClassView;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.qonfig.ElementTypeTraceability.SingleTypeTraceability;
import org.observe.expresso.qonfig.ExElement.Interpreted;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

/** A simple structure consisting of a class view and models, the definition for a set of models for an application */
public class Expresso extends ExElement.Interpreted.Abstract<ExElement> {
	// Can't use reflection here because we're configuring traceability for 2 related Qonfig types for one java type
	private static final SingleTypeTraceability<ExElement, Expresso, Def> TRACEABILITY = ElementTypeTraceability
		.<ExElement, Expresso, Def> getElementTraceability(ExpressoSessionImplV0_1.TOOLKIT_NAME, ExpressoSessionImplV0_1.VERSION,
			"expresso", Def.class, Expresso.class, null);

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
			withTraceability(TRACEABILITY.validate(session.getFocusType(), session.reporting()));
			super.doUpdate(session);
			theClassView = ExElement.useOrReplace(ClassViewElement.class, theClassView, session, "imports");
			theModels = ExElement.useOrReplace(ObservableModelElement.ModelSetElement.Def.class, theModels, session, "models");
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
			theModels = getDefinition().getModelElement().interpret(this);
			theModels.update(getExpressoEnv());
			setExpressoEnv(getExpressoEnv().with(theModels.getModels()));
		}
	}
}
