package org.observe.expresso.qonfig;

import java.util.Collections;
import java.util.Set;

import org.observe.SimpleObservable;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelSetInstanceBuilder;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;

public class ExpressoDocument<B> extends ExModelAugmentation<ExElement> {
	public static final String EXPRESSO_DOCUMENT = "expresso-document";

	@ExElementTraceable(toolkit = ExpressoSessionImplV0_1.CORE,
		qonfigType = EXPRESSO_DOCUMENT,
		interpretation = Interpreted.class,
		instance = ExpressoDocument.class)
	public static class Def<B extends ExElement, BD extends ExElement.Def<B>>
	extends ExModelAugmentation.Def<ExElement, ExpressoDocument<? extends B>> {
		private ExpressoHeadSection.Def theHead;
		private ModelComponentId theModelLoadValue;
		private ModelComponentId theBodyLoadValue;

		public Def(QonfigAddOn type, ExElement.Def<?> element) {
			super(type, element);
		}

		/** @return The head section definition of the document */
		@QonfigChildGetter("head")
		public ExpressoHeadSection.Def getHead() {
			return theHead;
		}

		@Override
		public Set<? extends Class<? extends ExAddOn.Def<?, ?>>> getDependencies() {
			return Collections.singleton(ExWithElementModel.Def.class);
		}

		public ModelComponentId getModelLoadValue() {
			return theModelLoadValue;
		}

		public ModelComponentId getBodyLoadValue() {
			return theBodyLoadValue;
		}

		@Override
		public void update(ExpressoQIS session, ExElement.Def<? extends ExElement> element) throws QonfigInterpretationException {
			super.update(session, element);

			createBuilder(session);
			theHead = getElement().syncChild(ExpressoHeadSection.Def.class, theHead, session, "head");

			ExWithElementModel.Def elModels = getElement().getAddOn(ExWithElementModel.Def.class);
			if (elModels != null) {
				theModelLoadValue = elModels.getElementValueModelId("onModelLoad");
				theBodyLoadValue = elModels.getElementValueModelId("onBodyLoad");
			} else
				theModelLoadValue = theBodyLoadValue = null;
		}

		@Override
		public Interpreted<? extends B, BD> interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}
	}

	public static class Interpreted<B extends ExElement, BD extends ExElement.Def<? super B>>
	extends ExAddOn.Interpreted.Abstract<ExElement, ExpressoDocument<B>> {
		private ExpressoHeadSection.Interpreted theHead;
		private ExElement.Interpreted<B> theBody;

		protected Interpreted(Def<? super B, BD> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def<? super B, BD> getDefinition() {
			return (Def<? super B, BD>) super.getDefinition();
		}

		public ExpressoHeadSection.Interpreted getHead() {
			return theHead;
		}

		public ExElement.Interpreted<B> getBody() {
			return theBody;
		}

		@Override
		public void update(ExElement.Interpreted<? extends ExElement> element) throws ExpressoInterpretationException {
			super.update(element);

			theHead = getElement().syncChild(getDefinition().getHead(), theHead, def -> def.interpret(element),
				(i, iEnv) -> i.updateHead(iEnv));
			if (theHead != null && theHead.getClassView() != null)
				getElement().setExpressoEnv(getElement().getExpressoEnv().with(theHead.getClassView()));
		}

		@Override
		public Class<ExpressoDocument<B>> getInstanceType() {
			return (Class<ExpressoDocument<B>>) (Class<?>) ExpressoDocument.class;
		}

		@Override
		public ExpressoDocument<B> create(ExElement element) {
			return new ExpressoDocument<>(element);
		}
	}

	private ExpressoHeadSection theHead;
	private ModelComponentId theModelLoadValue;
	private ModelComponentId theBodyLoadValue;
	private SimpleObservable<java.lang.Void> theModelLoad;
	private SimpleObservable<java.lang.Void> theBodyLoad;

	protected ExpressoDocument(ExElement element) {
		super(element);

		theModelLoad = new SimpleObservable<>();
		theBodyLoad = new SimpleObservable<>();
	}

	public ExpressoHeadSection getHead() {
		return theHead;
	}

	@Override
	public Class<? extends Interpreted<?, ?>> getInterpretationType() {
		return (Class<Interpreted<?, ?>>) (Class<?>) Interpreted.class;
	}

	@Override
	public void update(ExAddOn.Interpreted<? extends ExElement, ?> interpreted, ExElement element) throws ModelInstantiationException {
		super.update(interpreted, element);

		Interpreted<?, ?> myInterpreted = (Interpreted<?, ?>) interpreted;
		theModelLoadValue = myInterpreted.getDefinition().getModelLoadValue();
		theBodyLoadValue = myInterpreted.getDefinition().getBodyLoadValue();
		if (myInterpreted.getHead() == null) {
			if (theHead != null)
				theHead.destroy();
			theHead = null;
		} else if (theHead == null)
			theHead = myInterpreted.getHead().create();
		if (theHead != null)
			theHead.update(myInterpreted.getHead(), element);
	}

	@Override
	public void preInstantiated() throws ModelInstantiationException {
		super.preInstantiated();

		theHead.instantiated();
	}

	@Override
	public void instantiate(ModelSetInstance models) throws ModelInstantiationException {
		super.instantiate(models);

		if (theModelLoadValue != null) {
			ExFlexibleElementModelAddOn.satisfyElementValue(theModelLoadValue, models, theModelLoad.readOnly());
			ExFlexibleElementModelAddOn.satisfyElementValue(theBodyLoadValue, models, theBodyLoad.readOnly());
		}

		theModelLoad.onNext(null);
	}

	@Override
	public void addRuntimeModels(ModelSetInstanceBuilder builder, ModelSetInstance elementModels) throws ModelInstantiationException {
		super.addRuntimeModels(builder, elementModels);

		theHead.addRuntimeModels(builder, elementModels);
	}

	@Override
	public void postInstantiate(ModelSetInstance models) throws ModelInstantiationException {
		super.postInstantiate(models);

		theBodyLoad.onNext(null);
	}

	@Override
	public ExpressoDocument<B> copy(ExElement element) {
		ExpressoDocument<B> copy = (ExpressoDocument<B>) super.copy(element);

		copy.theModelLoad = new SimpleObservable<>();
		copy.theBodyLoad = new SimpleObservable<>();

		return copy;
	}
}
