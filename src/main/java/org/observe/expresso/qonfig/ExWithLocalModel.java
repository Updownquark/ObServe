package org.observe.expresso.qonfig;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ElementTypeTraceability.SingleTypeTraceability;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;

public class ExWithLocalModel extends ExModelAugmentation<ExElement> {
	private static final SingleTypeTraceability<ExElement, ExElement.Interpreted<?>, ExElement.Def<?>> TRACEABILITY = ElementTypeTraceability
		.getAddOnTraceability(ExpressoSessionImplV0_1.TOOLKIT_NAME, ExpressoSessionImplV0_1.VERSION, "with-local-model", Def.class,
			Interpreted.class, ExWithLocalModel.class);

	public static class Def extends ExModelAugmentation.Def<ExElement, ExWithLocalModel> {
		private ObservableModelElement.LocalModelElementDef theLocalModelElement;

		public Def(QonfigAddOn type, ExElement.Def<? extends ExElement> element) {
			super(type, element);
		}

		@QonfigChildGetter("model")
		public ObservableModelElement.LocalModelElementDef getLocalModelElement() {
			return theLocalModelElement;
		}

		@Override
		public void update(ExpressoQIS session, ExElement.Def<? extends ExElement> element) throws QonfigInterpretationException {
			element.withTraceability(TRACEABILITY.validate(getType(), element.reporting()));
			super.update(session, element);
			if (session.getChildren("model").isEmpty()) { // Don't create a local model if there's no reason to
				theLocalModelElement = null;
				return;
			}
			// Don't need to use the local model builder, just create it. The local model parsing below will populate it.
			createBuilder(session);
			theLocalModelElement = ExElement.useOrReplace(ObservableModelElement.LocalModelElementDef.class, theLocalModelElement, session,
				"model");
			session.setModels(theLocalModelElement.getExpressoEnv().getModels());
			getElement().setExpressoEnv(getElement().getExpressoEnv().with(theLocalModelElement.getExpressoEnv().getModels()));
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<? extends ExElement> element) {
			return new Interpreted(this, element);
		}
	}

	public static class Interpreted extends ExAddOn.Interpreted.Abstract<ExElement, ExWithLocalModel> {
		private ObservableModelElement.DefaultModelElement.Interpreted<?> theLocalModelElement;

		public Interpreted(Def definition, ExElement.Interpreted<? extends ExElement> element) {
			super(definition, element);
		}

		public ObservableModelElement.DefaultModelElement.Interpreted<?> getLocalModelElement() {
			return theLocalModelElement;
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		@Override
		public void update(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			super.update(env);
			if (theLocalModelElement == null || theLocalModelElement.getDefinition() != getDefinition().getLocalModelElement()) {
				if (theLocalModelElement != null)
					theLocalModelElement.destroy();
				theLocalModelElement = getDefinition().getLocalModelElement() == null ? null
					: getDefinition().getLocalModelElement().interpret(getElement());
			}
			if (theLocalModelElement != null) {
				theLocalModelElement.update(env);
				getElement().setExpressoEnv(env.with(theLocalModelElement.getExpressoEnv().getModels()));
			}
		}

		@Override
		public ExWithLocalModel create(ExElement element) {
			return new ExWithLocalModel(this, element);
		}
	}

	private ObservableModelElement.DefaultModelElement theLocalModelElement;

	public ExWithLocalModel(Interpreted interpreted, ExElement element) {
		super(interpreted, element);
	}

	public ObservableModelElement.DefaultModelElement getLocalModelElement() {
		return theLocalModelElement;
	}

	@Override
	public void update(ExAddOn.Interpreted<?, ?> interpreted, ModelSetInstance models) throws ModelInstantiationException {
		super.update(interpreted, models);
		Interpreted myInterpreted = (Interpreted) interpreted;
		if (myInterpreted.getLocalModelElement() == null)
			theLocalModelElement = null;
		else if (theLocalModelElement == null
			|| theLocalModelElement.getIdentity() != myInterpreted.getLocalModelElement().getDefinition().getIdentity())
			theLocalModelElement = myInterpreted.getLocalModelElement().create(getElement());
		if (theLocalModelElement != null)
			theLocalModelElement.update(myInterpreted.getLocalModelElement(), models);
	}
}
