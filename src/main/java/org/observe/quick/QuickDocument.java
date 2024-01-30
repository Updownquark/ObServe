package org.observe.quick;

import org.observe.Observable;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoDocument;
import org.observe.expresso.qonfig.ExpressoHeadSection;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigChildGetter;
import org.observe.quick.style.ExWithStyleSheet;
import org.observe.quick.style.QuickStyleSheet;
import org.observe.quick.style.WithStyleSheet;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

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
		private QuickWidget.Def<?> theBody;

		/**
		 * @param parent The parent of this document, typically null
		 * @param type The element type that this document definition is being interpreted from
		 */
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		/** @return The body definition of the document */
		@QonfigChildGetter("body")
		public QuickWidget.Def<?> getBody() {
			return theBody;
		}

		/** @return The head section containing model and style information for the document */
		public ExpressoHeadSection.Def getHead() {
			return getAddOn(ExpressoDocument.Def.class).getHead();
		}

		@Override
		public QuickStyleSheet getStyleSheet() {
			ExpressoHeadSection.Def head = getHead();
			return head == null ? null : head.getAddOn(ExWithStyleSheet.Def.class).getStyleSheet();
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);

			session.put(ExWithStyleSheet.QUICK_STYLE_SHEET, getStyleSheet());
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

		/** @return The head section containing model and style information for the document */
		public ExpressoHeadSection.Interpreted getHead() {
			return getAddOn(ExpressoDocument.Interpreted.class).getHead();
		}

		/** @return The interpreted body of the document */
		public QuickWidget.Interpreted<?> getBody() {
			return theBody;
		}

		@Override
		public QuickStyleSheet.Interpreted getStyleSheet() {
			ExpressoHeadSection.Interpreted head = getHead();
			return head == null ? null : head.getAddOn(ExWithStyleSheet.Interpreted.class).getStyleSheet();
		}

		/**
		 * Initializes or updates this document
		 *
		 * @param env The expresso environment to interpret expressions with
		 * @throws ExpressoInterpretationException If an error occurs interpreting the document
		 */
		public void updateDocument(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			env = env.with(env.getClassView().copy()//
				.withWildcardImport(MouseCursor.StandardCursors.class.getName())//
				.build());
			update(env);
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			super.doUpdate(env);

			theBody = syncChild(getDefinition().getBody(), theBody, def -> def.interpret(this), (b, bEnv) -> b.updateElement(bEnv));
		}

		/** @return The new document */
		public QuickDocument create() {
			return new QuickDocument(getIdentity());
		}
	}

	private QuickWidget theBody;

	/** @param id The element identifier for the document */
	public QuickDocument(Object id) {
		super(id);
	}

	/** @return The document's body */
	public QuickWidget getBody() {
		return theBody;
	}

	/**
	 * Initializes or updates this document
	 *
	 * @param interpreted The interpreted document to use to initialize this document
	 * @throws ModelInstantiationException If any model values fail to initialize
	 */
	public void update(QuickDocument.Interpreted interpreted) throws ModelInstantiationException {
		update(interpreted, null);
	}

	/**
	 * Instantiates the values in this document
	 *
	 * @param until The observable to deconstruct values owned by the document
	 * @return The model set instance for the document
	 * @throws ModelInstantiationException If instantiation fails
	 */
	public ModelSetInstance instantiate(Observable<?> until) throws ModelInstantiationException {
		ModelSetInstance models = InterpretedExpressoEnv.INTERPRETED_STANDARD_JAVA.getModels().createInstance(until).build();
		return instantiate(models);
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);

		QuickDocument.Interpreted myInterpreted = (QuickDocument.Interpreted) interpreted;
		if (theBody == null)
			theBody = myInterpreted.getBody().create();
		theBody.update(myInterpreted.getBody(), this);
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();
		theBody.instantiated();
	}

	@Override
	protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		super.doInstantiate(myModels);

		theBody.instantiate(myModels);
	}

	@Override
	protected QuickDocument clone() {
		QuickDocument copy = (QuickDocument) super.clone();

		copy.theBody = theBody.copy(copy);

		return copy;
	}
}
