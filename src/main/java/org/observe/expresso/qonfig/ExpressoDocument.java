package org.observe.expresso.qonfig;

import org.observe.SimpleObservable;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;

/**
 * A document containing an expresso &lt;head> section, whose models will be usable by the document's content
 *
 * @param <B> The type of the document's body
 */
public class ExpressoDocument<B> extends ExModelAugmentation<ExElement> {
	/** The XML name of this type */
	public static final String EXPRESSO_DOCUMENT = "base-expresso-document";

	/**
	 * Definition for an {@link ExpressoDocument}
	 *
	 * @param <B> The type of the document's body
	 * @param <BD> The definition of the document's body type
	 */
	@ExMultiElementTraceable({ //
		@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
			qonfigType = EXPRESSO_DOCUMENT,
			interpretation = Interpreted.class,
			instance = ExpressoDocument.class), //
		@ExElementTraceable(toolkit = ExpressoSessionImplV0_1.CORE,
		qonfigType = "expresso-document",
		interpretation = Interpreted.class,
		instance = ExpressoDocument.class)//
	})
	public static class Def<B extends ExElement, BD extends ExElement.Def<B>>
	extends ExModelAugmentation.Def<ExElement, ExpressoDocument<? extends B>> {
		private ExpressoHeadSection.Def theHead;
		private ModelComponentId theModelLoadValue;
		private ModelComponentId theBodyLoadValue;

		/**
		 * @param type The Qonfig type of this add-on
		 * @param element The element this add-on will affect
		 */
		public Def(QonfigAddOn type, ExElement.Def<?> element) {
			super(type, element);
		}

		/** @return The head section definition of the document */
		@QonfigChildGetter(asType = "expresso-document", value = "head")
		public ExpressoHeadSection.Def getHead() {
			return theHead;
		}

		// @Override
		// public Set<? extends Class<? extends ExAddOn.Def<?, ?>>> getDependencies() {
		// return Collections.singleton(ExWithElementModel.Def.class);
		// }

		/** @return The model value ID of the onModelLoad action */
		public ModelComponentId getModelLoadValue() {
			return theModelLoadValue;
		}

		/** @return The model value ID of the onBodyLoad action */
		public ModelComponentId getBodyLoadValue() {
			return theBodyLoadValue;
		}

		@Override
		public void update(ExpressoQIS session, ExElement.Def<? extends ExElement> element) throws QonfigInterpretationException {
			super.update(session, element);

			createBuilder(session, element.getDocument());
			theHead = getElement().syncChild(ExpressoHeadSection.Def.class, theHead, session, "head");
			if (theHead != null) {
				for (String doc : theHead.getExpressoDocuments())
					element.setExpressoEnv(doc, theHead.getExpressoEnv(doc));
			}
		}

		@Override
		public void postUpdate(ExpressoQIS session, ExElement.Def<?> element) throws QonfigInterpretationException {
			super.postUpdate(session, element);

			ExWithElementModel.Def elModels = getElement().getAddOn(ExWithElementModel.Def.class);
			if (elModels != null) {
				theModelLoadValue = elModels.getElementValueModelId("onModelLoad");
				theBodyLoadValue = elModels.getElementValueModelId("onBodyLoad");
			} else
				theModelLoadValue = theBodyLoadValue = null;
		}

		@Override
		public <E2 extends ExElement> Interpreted<B, BD> interpret(ExElement.Interpreted<E2> element) {
			return new Interpreted<>(this, element);
		}
	}

	/**
	 * Interpretation of an {@link ExpressoDocument}
	 *
	 * @param <B> The type of the document's body
	 * @param <BD> The definition of the document's body type
	 */
	public static class Interpreted<B extends ExElement, BD extends ExElement.Def<? super B>>
	extends ExAddOn.Interpreted.Abstract<ExElement, ExpressoDocument<B>> {
		private ExpressoHeadSection.Interpreted theHead;

		/**
		 * @param definition The definition to interpret
		 * @param parent The element to affect
		 */
		protected Interpreted(Def<? super B, BD> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def<? super B, BD> getDefinition() {
			return (Def<? super B, BD>) super.getDefinition();
		}

		/** @return This document's head section */
		public ExpressoHeadSection.Interpreted getHead() {
			return theHead;
		}

		@Override
		public void update(ExElement.Interpreted<? extends ExElement> element) throws ExpressoInterpretationException {
			super.update(element);

			theHead = getElement().syncChild(getDefinition().getHead(), theHead, def -> def.interpret(element),
				ExpressoHeadSection.Interpreted::updateHead);
			if (theHead != null) {
				element.addLogicalParent(theHead);
				String headDoc = theHead.getDocument();
				element.setExpressoEnv(headDoc, theHead.getExpressoEnv(headDoc)); // Need to force this environment
			}
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

	/** @param element The element that this add-on will affect */
	protected ExpressoDocument(ExElement element) {
		super(element);

		theModelLoad = new SimpleObservable<>();
		theBodyLoad = new SimpleObservable<>();
	}

	/** @return The document's head section */
	public ExpressoHeadSection getHead() {
		return theHead;
	}

	@Override
	public Class<? extends Interpreted<?, ?>> getInterpretationType() {
		return (Class<Interpreted<?, ?>>) (Class<?>) Interpreted.class;
	}

	@Override
	public void update(ExAddOn.Interpreted<? super ExElement, ?> interpreted, ExElement element) throws ModelInstantiationException {
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
		if (theHead != null) {
			theHead.update(myInterpreted.getHead(), element);
			element.addLogicalParent(theHead);
		}
	}

	@Override
	public void preInstantiated() throws ModelInstantiationException {
		super.preInstantiated();

		if (theHead != null)
			theHead.instantiated();
	}

	@Override
	public ModelSetInstance instantiate(ModelSetInstance models) throws ModelInstantiationException {
		models = super.instantiate(models);

		if (theHead != null) {
			ObservableModelSet.ModelSetInstanceBuilder builder = ObservableModelSet.createMultiModelInstanceBag(models.getUntil());
			builder.withAll(models);
			builder.withAll(theHead.instantiate(models));
			models = builder.build();
		}

		return models;
	}

	@Override
	public void postInstantiate(ModelSetInstance models) throws ModelInstantiationException {
		super.postInstantiate(models);

		if (theModelLoadValue != null) {
			ExFlexibleElementModelAddOn.satisfyElementValue(theModelLoadValue, models, theModelLoad.readOnly());
			ExFlexibleElementModelAddOn.satisfyElementValue(theBodyLoadValue, models, theBodyLoad.readOnly());
		}

		theModelLoad.onNext(null);
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
