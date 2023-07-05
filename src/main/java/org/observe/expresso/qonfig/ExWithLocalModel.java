package org.observe.expresso.qonfig;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.InterpretedModelSet;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;

public class ExWithLocalModel extends ExAddOn.Abstract<ExElement> {
	private static final ElementTypeTraceability<ExElement, ExElement.Interpreted<?>, ExElement.Def<?>> TRACEABILITY = ElementTypeTraceability
		.buildAddOn(ExpressoSessionImplV0_1.TOOLKIT_NAME, ExpressoSessionImplV0_1.VERSION, "with-local-model", Def.class, Interpreted.class,
			ExWithLocalModel.class)//
		.reflectAddOnMethods()//
		.build();

	public static class Def extends ExAddOn.Def.Abstract<ExElement, ExWithLocalModel> {
		private ObservableModelElement.DefaultModelElement.Def<?> theLocalModelElement;

		public Def(QonfigAddOn type, ExElement.Def<? extends ExElement> element) {
			super(type, element);
		}

		@QonfigChildGetter("model")
		public ObservableModelElement.DefaultModelElement.Def<?> getLocalModelElement() {
			return theLocalModelElement;
		}

		@Override
		public void update(ExpressoQIS session, ExElement.Def<? extends ExElement> element) throws QonfigInterpretationException {
			element.withTraceability(TRACEABILITY.validate(getType(), element.reporting()));
			super.update(session, element);
			theLocalModelElement = session.get(ExpressoBaseV0_1.LOCAL_MODEL_ELEMENT_KEY,
				ObservableModelElement.DefaultModelElement.Def.class);
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
		public void update(InterpretedModelSet models) throws ExpressoInterpretationException {
			super.update(models);
			if (theLocalModelElement == null || theLocalModelElement.getDefinition() != getDefinition().getLocalModelElement()) {
				if (theLocalModelElement != null)
					theLocalModelElement.destroy();
				theLocalModelElement = getDefinition().getLocalModelElement() == null ? null
					: getDefinition().getLocalModelElement().interpret(getElement());
			}
			if (theLocalModelElement != null)
				theLocalModelElement.update();
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
