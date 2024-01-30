package org.observe.expresso.qonfig;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;

public class ExWithLocalModel extends ExModelAugmentation<ExElement> {
	@ExElementTraceable(toolkit = ExpressoSessionImplV0_1.CORE,
		qonfigType = "with-local-model",
		interpretation = Interpreted.class,
		instance = ExWithLocalModel.class)
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
		public void update(ExpressoQIS session, ExElement.Def<?> element) throws QonfigInterpretationException {
			super.update(session, element);
			if (session.children().get("model").get().isEmpty()) { // Don't create a local model if there's no reason to
				theLocalModelElement = null;
				return;
			}
			// Don't need to use the local model builder, just create it. The local model parsing below will populate it.
			createBuilder(session);
			theLocalModelElement = getElement().syncChild(ObservableModelElement.LocalModelElementDef.class, theLocalModelElement, session,
				"model");
			getElement().setExpressoEnv(getElement().getExpressoEnv().with(theLocalModelElement.getExpressoEnv().getModels()));
			session.setExpressoEnv(getElement().getExpressoEnv());
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
		public void update(ExElement.Interpreted<?> element) throws ExpressoInterpretationException {
			super.update(element);
			theLocalModelElement = getElement().syncChild(getDefinition().getLocalModelElement(), theLocalModelElement,
				def -> def.interpret(element), (el, elEnv) -> el.update(elEnv));
			if (theLocalModelElement != null)
				getElement().setExpressoEnv(getElement().getExpressoEnv().with(theLocalModelElement.getExpressoEnv().getModels()));
		}

		@Override
		public Class<ExWithLocalModel> getInstanceType() {
			return ExWithLocalModel.class;
		}

		@Override
		public ExWithLocalModel create(ExElement element) {
			return new ExWithLocalModel(element);
		}
	}

	private ObservableModelElement.DefaultModelElement theLocalModelElement;

	public ExWithLocalModel(ExElement element) {
		super(element);
	}

	@Override
	public Class<Interpreted> getInterpretationType() {
		return (Class<Interpreted>) (Class<?>) Interpreted.class;
	}

	public ObservableModelElement.DefaultModelElement getLocalModelElement() {
		return theLocalModelElement;
	}

	@Override
	public void update(ExAddOn.Interpreted<?, ?> interpreted, ExElement element) throws ModelInstantiationException {
		super.update(interpreted, element);

		Interpreted myInterpreted = (Interpreted) interpreted;
		if (myInterpreted.getLocalModelElement() == null)
			theLocalModelElement = null;
		else if (theLocalModelElement == null
			|| theLocalModelElement.getIdentity() != myInterpreted.getLocalModelElement().getDefinition().getIdentity())
			theLocalModelElement = myInterpreted.getLocalModelElement().create(getElement());
		if (theLocalModelElement != null)
			theLocalModelElement.update(myInterpreted.getLocalModelElement(), getElement());
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();
		if (theLocalModelElement != null)
			theLocalModelElement.instantiated();
	}

	@Override
	public void instantiate(ModelSetInstance models) throws ModelInstantiationException {
		super.instantiate(models);
		if (theLocalModelElement != null)
			theLocalModelElement.instantiate(models);
	}
}
