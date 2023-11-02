package org.observe.quick;

import java.util.function.Consumer;

import org.observe.Observable;
import org.observe.SimpleObservable;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.InterpretedModelSet;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.ObservableModelSet.ModelInstantiator;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExFlexibleElementModelAddOn;
import org.observe.expresso.qonfig.ExWithElementModel;
import org.observe.expresso.qonfig.Expresso;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigChildGetter;
import org.observe.quick.style.ExWithStyleSheet;
import org.observe.quick.style.QuickStyleSheet;
import org.observe.quick.style.WithStyleSheet;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.ex.ExConsumer;

/** The root of a quick file, containing all information needed to power an application */
public class QuickDocument extends ExElement.Abstract implements WithStyleSheet {
	/** Name of the Qonfig element type that this interpretation is for */
	public static final String QUICK = "quick";

	/** The definition of a Quick document */
	@ExElementTraceable(toolkit = QuickCoreInterpretation.CORE,
		qonfigType = QUICK,
		interpretation = Interpreted.class,
		instance = QuickDocument.class)
	public static class Def extends ExElement.Def.Abstract<QuickDocument> implements WithStyleSheet.Def<QuickDocument> {
		private QuickHeadSection.Def theHead;
		private QuickWidget.Def<?> theBody;
		private ModelComponentId theModelLoadValue;
		private ModelComponentId theBodyLoadValue;

		/**
		 * @param parent The parent of this document, typically null
		 * @param type The element type that this document definition is being interpreted from
		 */
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		/** @return The head section definition of the document */
		@QonfigChildGetter("head")
		public QuickHeadSection.Def getHead() {
			return theHead;
		}

		/** @return The body definition of the document */
		@QonfigChildGetter("body")
		public QuickWidget.Def<?> getBody() {
			return theBody;
		}

		@Override
		public QuickStyleSheet getStyleSheet() {
			return getHead() == null ? null : getHead().getStyleSheet();
		}

		public ModelComponentId getModelLoadValue() {
			return theModelLoadValue;
		}

		public ModelComponentId getBodyLoadValue() {
			return theBodyLoadValue;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);

			ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
			theModelLoadValue = elModels.getElementValueModelId("onModelLoad");
			theBodyLoadValue = elModels.getElementValueModelId("onBodyLoad");

			theHead = syncChild(QuickHeadSection.Def.class, theHead, session, "head");
			if (theHead != null) {
				setExpressoEnv(theHead.getExpressoEnv());
				session.setExpressoEnv(theHead.getExpressoEnv());
				session.put(ExWithStyleSheet.QUICK_STYLE_SHEET, theHead.getStyleSheet());
			}
			theBody = syncChild(QuickWidget.Def.class, theBody, session, "body");
		}

		/**
		 * @param parent The interpreted parent, typically null
		 * @return The interpreted document
		 */
		public Interpreted interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}
	}

	/** An interpreted Quick document */
	public static class Interpreted extends ExElement.Interpreted.Abstract<QuickDocument>
	implements WithStyleSheet.Interpreted<QuickDocument> {
		private QuickHeadSection theHead;
		private QuickWidget.Interpreted<?> theBody;

		/**
		 * @param def The document's definition
		 * @param parent The document's interpreted parent, typically null
		 */
		public Interpreted(Def def, ExElement.Interpreted<?> parent) {
			super(def, parent);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		/** @return The head section of the document */
		public QuickHeadSection getHead() {
			return theHead;
		}

		/** @return The interpreted body of the document */
		public QuickWidget.Interpreted<?> getBody() {
			return theBody;
		}

		@Override
		public QuickStyleSheet.Interpreted getStyleSheet() {
			return getHead() == null ? null : getHead().getStyleSheet();
		}

		public void updateDocument(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			if (getDefinition().getHead().getClassViewElement() != null)
				env = env.with(getDefinition().getHead().getClassViewElement().configureClassView(env.getClassView().copy()).build());
			env = env.with(env.getClassView().copy()//
				.withWildcardImport(MouseCursor.StandardCursors.class.getName())//
				.build());
			update(env);
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			super.doUpdate(env);
			if (getDefinition().getHead() == null) {
				theHead = null;
			} else {
				if (theHead == null || theHead.getDefinition() != getDefinition().getHead())
					theHead = getDefinition().getHead().interpret(this);
				theHead.updateExpresso(env);
				setExpressoEnv(theHead.getExpressoEnv());
			}

			theBody = syncChild(getDefinition().getBody(), theBody, def -> def.interpret(this),
				(b, bEnv) -> b.updateElement(theHead.getExpressoEnv()));
		}

		/** @return The new document */
		public QuickDocument create() {
			return new QuickDocument(getIdentity());
		}
	}

	/** Represents the head section of a Quick document, containing class-view, model, and style information */
	public static class QuickHeadSection extends Expresso {
		/** Name of the Qonfig element type that this interpretation is for */
		public static final String HEAD = "head";

		/** The definition of a head section */
		public static class Def extends Expresso.Def {
			/**
			 * @param parent The document that this head section is for
			 * @param type The element type that this head section is being parsed from
			 */
			public Def(QuickDocument.Def parent, QonfigElementOrAddOn type) {
				super(parent, type);
			}

			/** @return The style sheet defined in this head section */
			public QuickStyleSheet getStyleSheet() {
				return getAddOn(ExWithStyleSheet.class).getStyleSheet();
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));
			}

			/**
			 * @param document The document that the head section is for
			 * @return The new head section
			 * @throws ExpressoInterpretationException If the
			 *         {@link org.observe.expresso.ObservableModelSet.Built#createInterpreted(InterpretedExpressoEnv) interpretation} of the
			 *         head section's {@link #getModels() models} fails
			 */
			public QuickHeadSection interpret(QuickDocument.Interpreted document) throws ExpressoInterpretationException {
				return new QuickHeadSection(this, document);
			}
		}

		/**
		 * @param def The definition of this head section
		 * @param document The document that this head section is for
		 */
		public QuickHeadSection(Def def, QuickDocument.Interpreted document) {
			super(def, document);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		/** @return The style sheet defined in this head section */
		public QuickStyleSheet.Interpreted getStyleSheet() {
			return getAddOnValue(ExWithStyleSheet.Interpreted.class, ExWithStyleSheet.Interpreted::getStyleSheet);
		}

		@Override
		protected void addRuntimeModels(Consumer<InterpretedModelSet> model) {
			super.addRuntimeModels(model);
			if (getStyleSheet() != null)
				model.accept(getStyleSheet().getExpressoEnv().getModels());
		}
	}

	private ModelInstantiator theModels;
	private ModelInstantiator theSpreadSheetModel;
	private QuickWidget theBody;
	private ModelComponentId theModelLoadValue;
	private ModelComponentId theBodyLoadValue;
	private SimpleObservable<Void> theModelLoad;
	private SimpleObservable<Void> theBodyLoad;

	public QuickDocument(Object id) {
		super(id);
		theModelLoad = new SimpleObservable<>();
		theBodyLoad = new SimpleObservable<>();
	}

	/** @return The document's body */
	public QuickWidget getBody() {
		return theBody;
	}

	public void update(QuickDocument.Interpreted interpreted) {
		update(interpreted, null);
	}

	public ModelSetInstance instantiate(Observable<?> until) throws ModelInstantiationException {
		ModelSetInstance models = InterpretedExpressoEnv.INTERPRETED_STANDARD_JAVA.getModels().createInstance(until).build();
		return instantiate(models);
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) {
		super.doUpdate(interpreted);

		QuickDocument.Interpreted myInterpreted = (QuickDocument.Interpreted) interpreted;

		theModelLoadValue = myInterpreted.getDefinition().getModelLoadValue();
		theBodyLoadValue = myInterpreted.getDefinition().getBodyLoadValue();

		theModels = myInterpreted.getHead().getExpressoEnv().getModels().instantiate();
		theSpreadSheetModel = myInterpreted.getHead().getStyleSheet() == null ? null
			: myInterpreted.getHead().getStyleSheet().getExpressoEnv().getModels().instantiate();
		if (theBody == null)
			theBody = myInterpreted.getBody().create();
		theBody.update(myInterpreted.getBody(), this);
		theBodyLoad.onNext(null);
	}

	@Override
	public void instantiated() {
		theModels.instantiate();
		if (theSpreadSheetModel != null)
			theSpreadSheetModel.instantiate();
		super.instantiated();
		theBody.instantiated();
	}

	@Override
	protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		super.doInstantiate(myModels);

		ExFlexibleElementModelAddOn.satisfyElementValue(theModelLoadValue, myModels, theModelLoad.readOnly());
		ExFlexibleElementModelAddOn.satisfyElementValue(theBodyLoadValue, myModels, theBodyLoad.readOnly());
		ModelSetInstance headModels = theModels.wrap(myModels);
		theModelLoad.onNext(null);
		theBody.instantiate(headModels);
		theBodyLoad.onNext(null);
	}

	@Override
	protected void addRuntimeModels(ExConsumer<ModelSetInstance, ModelInstantiationException> model, ModelSetInstance elementModels,
		Observable<?> until) throws ModelInstantiationException {
		super.addRuntimeModels(model, elementModels, until);
		if (theSpreadSheetModel != null)
			model.accept(theSpreadSheetModel.createInstance(until)//
				.withAll(elementModels)//
				.build());
	}

	@Override
	protected QuickDocument clone() {
		QuickDocument copy = (QuickDocument) super.clone();

		copy.theModelLoad = new SimpleObservable<>();
		copy.theBodyLoad = new SimpleObservable<>();

		return copy;
	}
}
