package org.observe.expresso.qonfig;

import java.util.Collections;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.InterpretedModelSet;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;

public class ExWithLocalModel extends ExAddOn.Abstract<ExElement> {
	private static final ExAddOn.AddOnChildGetter<ExElement, ExWithLocalModel, Interpreted, Def> LOCAL_MODEL = ExAddOn.AddOnChildGetter
		.<ExElement, ExWithLocalModel, Interpreted, Def> of(Def.class,
			d -> d.getLocalModelElement() == null ? Collections.emptyList() : Collections.singletonList(d.getLocalModelElement()), //
				Interpreted.class,
				d -> d.getLocalModelElement() == null ? Collections.emptyList() : Collections.singletonList(d.getLocalModelElement()), //
					ExWithLocalModel.class,
					d -> d.getLocalModelElement() == null ? Collections.emptyList() : Collections.singletonList(d.getLocalModelElement()), //
			"The local model with values defined only for use within the element");

	public static class Def extends ExAddOn.Def.Abstract<ExElement, ExWithLocalModel> {
		private ObservableModelElement.DefaultModelElement.Def<?> theLocalModelElement;

		public Def(QonfigAddOn type, ExElement.Def<? extends ExElement> element) {
			super(type, element);
		}

		public ObservableModelElement.DefaultModelElement.Def<?> getLocalModelElement() {
			return theLocalModelElement;
		}

		@Override
		public void update(ExpressoQIS session, ExElement.Def<? extends ExElement> element) throws QonfigInterpretationException {
			element.forChild(session.getRole("model"), LOCAL_MODEL);
			super.update(session, element);
			ExElement.useOrReplace(ObservableModelElement.DefaultModelElement.Def.class, theLocalModelElement, session, "model");
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
			} else if (theLocalModelElement != null)
				theLocalModelElement.update();
		}

		@Override
		public ExWithLocalModel create(ExElement element) {
			// TODO Auto-generated method stub
			return null;
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
		else
			theLocalModelElement.update(myInterpreted.getLocalModelElement(), models);
	}
}
