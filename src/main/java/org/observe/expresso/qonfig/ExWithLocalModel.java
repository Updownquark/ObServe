package org.observe.expresso.qonfig;

import org.observe.expresso.CompiledExpressoEnv;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;

/** An add-on that allows an element to contain a &lt;model> element with model components usable by the element's contents */
public class ExWithLocalModel extends ExModelAugmentation<ExElement> {
	/** The XML name of this add-on */
	public static final String WITH_LOCAL_MODEL = "with-local-model";

	/** Definition for {@link ExWithLocalModel} */
	@ExElementTraceable(toolkit = ExpressoSessionImplV0_1.CORE,
		qonfigType = WITH_LOCAL_MODEL,
		interpretation = Interpreted.class,
		instance = ExWithLocalModel.class)
	public static class Def extends ExModelAugmentation.Def<ExElement, ExWithLocalModel> {
		private ObservableModelElement.LocalModelElementDef theLocalModelElement;

		/**
		 * @param type The Qonfig type of this element
		 * @param element The element to use the local model components
		 */
		public Def(QonfigAddOn type, ExElement.Def<? extends ExElement> element) {
			super(type, element);
		}

		/** @return The local model element */
		@QonfigChildGetter("model")
		public ObservableModelElement.LocalModelElementDef getLocalModelElement() {
			return theLocalModelElement;
		}

		@Override
		public void update(ExpressoQIS session, ExElement.Def<?> element) throws QonfigInterpretationException {
			super.update(session, element);
			ExpressoQIS modelEl = session.children().get("model").get().peekFirst();
			if (modelEl == null) { // Don't create a local model if there's no reason to
				theLocalModelElement = null;
				return;
			}

			String doc = modelEl.getInterpretingDocument();
			// Don't need to use the local model builder, just create it. The local model parsing below will populate it.
			createBuilder(session, doc);
			theLocalModelElement = getElement().syncChild(ObservableModelElement.LocalModelElementDef.class, theLocalModelElement, session,
				"model");
			CompiledExpressoEnv env = getElement().getExpressoEnv(doc);
			env = env.with(theLocalModelElement.getExpressoEnv(doc).getModels());
			getElement().setExpressoEnv(doc, env);
			session.setExpressoEnv(doc, env);
		}

		@Override
		public <E2 extends ExElement> Interpreted interpret(ExElement.Interpreted<E2> element) {
			return new Interpreted(this, element);
		}
	}

	/** Interpretation for {@link ExWithLocalModel} */
	public static class Interpreted extends ExAddOn.Interpreted.Abstract<ExElement, ExWithLocalModel> {
		private ObservableModelElement.DefaultModelElement.Interpreted<?> theLocalModelElement;

		Interpreted(Def definition, ExElement.Interpreted<? extends ExElement> element) {
			super(definition, element);
		}

		/** @return The local model element */
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
				def -> def.interpret(element), el -> el.update());
			if (theLocalModelElement != null) {
				String doc = theLocalModelElement.getDocument();
				getElement().setExpressoEnv(doc,
					getElement().getExpressoEnv(doc).with(theLocalModelElement.getExpressoEnv(doc).getModels()));
			}
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

	ExWithLocalModel(ExElement element) {
		super(element);
	}

	@Override
	public Class<Interpreted> getInterpretationType() {
		return (Class<Interpreted>) (Class<?>) Interpreted.class;
	}

	/** @return The local model element */
	public ObservableModelElement.DefaultModelElement getLocalModelElement() {
		return theLocalModelElement;
	}

	@Override
	public void update(ExAddOn.Interpreted<? super ExElement, ?> interpreted, ExElement element) throws ModelInstantiationException {
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
	public ModelSetInstance instantiate(ModelSetInstance models) throws ModelInstantiationException {
		models = super.instantiate(models);
		if (theLocalModelElement != null)
			models = theLocalModelElement.instantiate(models);
		return models;
	}
}
