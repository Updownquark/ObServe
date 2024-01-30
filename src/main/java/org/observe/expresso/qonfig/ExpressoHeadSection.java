package org.observe.expresso.qonfig;

import org.observe.expresso.ClassView;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.ModelInstantiator;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

/** A simple structure consisting of a class view and models, the definition for a set of models for an application */
public class ExpressoHeadSection extends ExElement.Abstract {
	public static final String HEAD = "head";

	@ExElementTraceable(toolkit = ExpressoSessionImplV0_1.CORE, qonfigType = HEAD, interpretation = ExpressoHeadSection.class)
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

		public Interpreted interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}
	}

	public static class Interpreted extends ExElement.Interpreted.Abstract<ExElement> {
		private ClassView theClassView;
		private ObservableModelElement.ModelSetElement.Interpreted<?> theModels;

		public Interpreted(Def definition, ExElement.Interpreted<?> parent) {
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

		public void updateHead(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			if (getDefinition().getClassViewElement() != null)
				env = env.with(getDefinition().getClassViewElement().configureClassView(env.getClassView().copy()).build());
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

			if (getDefinition().getModelElement() == null) {
				if (theModels != null)
					theModels.destroy();
				theModels = null;
			} else {
				theModels = syncChild(getDefinition().getModelElement(), theModels, def -> def.interpret(this),
					(m, mEnv) -> m.update(mEnv));
				env = getExpressoEnv().with(theModels.getModels());
			}

			super.doUpdate(env);
		}

		public ExpressoHeadSection create() {
			return new ExpressoHeadSection(getIdentity());
		}
	}

	private ModelInstantiator theModels;

	public ExpressoHeadSection(Object id) {
		super(id);
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);

		Interpreted myInterpreted = (Interpreted) interpreted;
		theModels = myInterpreted.getModels().instantiate();
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		theModels.instantiate();

		super.instantiated();
	}
}
