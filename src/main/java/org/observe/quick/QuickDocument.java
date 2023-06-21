package org.observe.quick;

import java.util.Collections;
import java.util.List;

import org.observe.Observable;
import org.observe.SimpleObservable;
import org.observe.expresso.ClassView;
import org.observe.expresso.ExpressoEnv;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.InterpretedModelSet;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ExElement;
import org.observe.quick.style.QuickStyleSheet;
import org.observe.quick.style.StyleQIS;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;

/** The root of a quick file, containing all information needed to power an application */
public class QuickDocument extends ExElement.Abstract {
	public static final String QUICK = "quick";

	public static final ExElement.ChildElementGetter<QuickDocument, Interpreted, Def> DOC_HEAD = new ExElement.ChildElementGetter<QuickDocument, Interpreted, Def>() {
		@Override
		public String getDescription() {
			return "The head section of the Quick document, containing application models, style sheets, etc.";
		}

		@Override
		public List<? extends ExElement.Def<?>> getChildrenFromDef(Def def) {
			return Collections.singletonList(def.getHead());
		}

		@Override
		public List<? extends ExElement.Interpreted<?>> getChildrenFromInterpreted(Interpreted interp) {
			return Collections.singletonList(interp.getHead());
		}

		@Override
		public List<? extends ExElement> getChildrenFromElement(QuickDocument element) {
			return Collections.emptyList();
		}
	};

	public static final ExElement.ChildElementGetter<QuickDocument, Interpreted, Def> DOC_BODY = new ExElement.ChildElementGetter<QuickDocument, Interpreted, Def>() {
		@Override
		public String getDescription() {
			return "The widget content of the application";
		}

		@Override
		public List<? extends ExElement.Def<?>> getChildrenFromDef(Def def) {
			return Collections.singletonList(def.getBody());
		}

		@Override
		public List<? extends ExElement.Interpreted<?>> getChildrenFromInterpreted(Interpreted interp) {
			return Collections.singletonList(interp.getBody());
		}

		@Override
		public List<? extends ExElement> getChildrenFromElement(QuickDocument element) {
			return Collections.singletonList(element.getBody());
		}
	};

	/** The definition of a Quick document */
	public static class Def extends ExElement.Def.Abstract<QuickDocument> {
		private QuickHeadSection.Def theHead;
		private QuickWidget.Def<?> theBody;

		/**
		 * @param parent The parent of this document, typically null
		 * @param element The element that this document definition is being interpreted from
		 */
		public Def(ExElement.Def<?> parent, QonfigElement element) {
			super(parent, element);
		}

		/** @return The head section definition of the document */
		public QuickHeadSection.Def getHead() {
			return theHead;
		}

		/** @return The body definition of the document */
		public QuickWidget.Def<?> getBody() {
			return theBody;
		}

		@Override
		public void update(ExpressoQIS session) throws QonfigInterpretationException {
			ExElement.checkElement(session.getFocusType(), QuickCoreInterpretation.NAME, QuickCoreInterpretation.VERSION, QUICK);
			forChild(session.getRole(null, null, "head").getDeclared(), DOC_HEAD);
			forChild(session.getRole(null, null, "body").getDeclared(), DOC_BODY);
			super.update(session);
			theHead = ExElement.useOrReplace(QuickHeadSection.Def.class, theHead, session, "head");
			if (theHead != null) {
				session.setExpressoEnv(session.getExpressoEnv().with(theHead.getModels(), theHead.getClassView()));
				session.as(StyleQIS.class).setStyleSheet(theHead.getStyleSheet());
			}
			theBody = ExElement.useOrReplace(QuickWidget.Def.class, theBody, session, "body");
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
	public static class Interpreted extends ExElement.Interpreted.Abstract<QuickDocument> {
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
		public void update() throws ExpressoInterpretationException {
			super.update();

			if (getDefinition().getHead() == null)
				theHead = null;
			else if (theHead == null || theHead.getDefinition() != getDefinition().getHead())
				theHead = getDefinition().getHead().interpret(this);
			if (theHead != null)
				theHead.update();

			if (theBody == null || theBody.getDefinition() != getDefinition().getBody())
				theBody = getDefinition().getBody().interpret(this);
			theBody.update(new QuickStyledElement.QuickInterpretationCache());
		}

		/** @return The new document */
		public QuickDocument create() {
			return new QuickDocument(this);
		}
	}

	/** Represents the head section of a Quick document, containing class-view, model, and style information */
	public static class QuickHeadSection extends ExElement.Interpreted.Abstract<ExElement> {
		public static final String HEAD = "head";

		public static final ExElement.ChildElementGetter<ExElement, QuickHeadSection, QuickHeadSection.Def> IMPORTS = new ExElement.ChildElementGetter<ExElement, QuickHeadSection, QuickHeadSection.Def>() {
			@Override
			public String getDescription() {
				return "The imports of the document, allowing types and static fields and methods to be used by name without full qualification";
			}

			@Override
			public List<? extends ExElement.Def<?>> getChildrenFromDef(QuickHeadSection.Def def) {
				if(def.getClassView().getElement()!=null)
					return Collections.singletonList(def.getClassView());
				else
					return Collections.emptyList();
			}

			@Override
			public List<? extends ExElement.Interpreted<?>> getChildrenFromInterpreted(QuickHeadSection interp) {
				return Collections.emptyList();
			}

			@Override
			public List<? extends ExElement> getChildrenFromElement(ExElement element) {
				return Collections.emptyList();
			}
		};

		public static final ExElement.ChildElementGetter<ExElement, QuickHeadSection, QuickHeadSection.Def> STYLE_SHEET = new ExElement.ChildElementGetter<ExElement, QuickHeadSection, QuickHeadSection.Def>() {
			@Override
			public String getDescription() {
				return "The style sheet of the document, defining styles that can be applied to sets of elements without being declared on each element";
			}

			@Override
			public List<? extends ExElement.Def<?>> getChildrenFromDef(QuickHeadSection.Def def) {
				if(def.getStyleSheet()!=null)
					return Collections.singletonList(def.getStyleSheet());
				else
					return Collections.emptyList();
			}

			@Override
			public List<? extends ExElement.Interpreted<?>> getChildrenFromInterpreted(QuickHeadSection interp) {
				return Collections.emptyList();
			}

			@Override
			public List<? extends ExElement> getChildrenFromElement(ExElement element) {
				return Collections.emptyList();
			}
		};

		/** The definition of a head section */
		public static class Def extends ExElement.Def.Abstract<ExElement> {
			private ClassView theClassView;
			private ObservableModelSet.Built theModels;
			private QuickStyleSheet theStyleSheet;

			/**
			 * @param parent The document that this head section is for
			 * @param element The element that this head section is being parsed from
			 */
			public Def(QuickDocument.Def parent, QonfigElement element) {
				super(parent, element);
			}

			/** @return The class view defined in this head section */
			public ClassView getClassView() {
				return theClassView;
			}

			/** @return The models defined in this head section */
			@Override
			public ObservableModelSet.Built getModels() {
				return theModels;
			}

			/** @return The style sheet defined in this head section */
			public QuickStyleSheet getStyleSheet() {
				return theStyleSheet;
			}

			@Override
			public void update(ExpressoQIS session) throws QonfigInterpretationException {
				ExElement.checkElement(session.getFocusType(), QuickCoreInterpretation.NAME, QuickCoreInterpretation.VERSION, HEAD);
				forChild(session.getRole("imports"), IMPORTS);
				forChild(session.getRole("style-sheet"), STYLE_SHEET);
				super.update(session);
				ClassView cv = session.interpretChildren("imports", ClassView.class).peekFirst();
				if (cv == null) {
					ClassView defaultCV = ClassView.build().withWildcardImport("java.lang", null).build(this, null);
					cv = defaultCV;
				} else
					cv.update(session.forChildren("imports").getFirst());
				theClassView = cv;
				// Install the class view now, so the model can use it
				session.setExpressoEnv(session.getExpressoEnv().with(null, cv));
				ObservableModelSet.Built model = session.interpretChildren("models", ObservableModelSet.Built.class).peekFirst();
				if (model == null)
					model = ObservableModelSet.build("models", ObservableModelSet.JAVA_NAME_CHECKER).build();
				theModels = model;
				session.setExpressoEnv(session.getExpressoEnv().with(model, cv));
				theStyleSheet = session.as(StyleQIS.class).getStyleSheet();
				if (theStyleSheet != null)
					theStyleSheet.update(session.forChildren("style-sheet").getFirst());
			}

			/**
			 * @param document The document that the head section is for
			 * @return The new head section
			 * @throws ExpressoInterpretationException If the {@link org.observe.expresso.ObservableModelSet.Built#interpret()
			 *         interpretation} of the head section's {@link #getModels() models} fails
			 */
			public QuickHeadSection interpret(QuickDocument.Interpreted document) throws ExpressoInterpretationException {
				return new QuickHeadSection(this, document, getClassView(), getModels().interpret(), theStyleSheet);
			}
		}

		private final ClassView theClassView;
		private final InterpretedModelSet theModels;
		private final QuickStyleSheet theStyleSheet;

		/**
		 * @param def The definition of this head section
		 * @param document The document that this head section is for
		 * @param classView The class view defined in this head section
		 * @param models The models defined in this head section
		 * @param styleSheet The style sheet defined in this head section
		 */
		public QuickHeadSection(Def def, QuickDocument.Interpreted document, ClassView classView, InterpretedModelSet models,
			QuickStyleSheet styleSheet) {
			super(def, document);
			theClassView = classView;
			theModels = models;
			theStyleSheet = styleSheet;
		}

		/** @return The class view defined in this head section */
		public ClassView getClassView() {
			return theClassView;
		}

		/** @return The models defined in this head section */
		@Override
		public InterpretedModelSet getModels() {
			return theModels;
		}

		/** @return The style sheet defined in this head section */
		public QuickStyleSheet getStyleSheet() {
			return theStyleSheet;
		}

		@Override
		protected void update() throws ExpressoInterpretationException {
			super.update();
		}
	}

	private QuickWidget theBody;
	private final SimpleObservable<Void> theModelLoad;
	private final SimpleObservable<Void> theBodyLoad;

	/** @param interpreted The interpreted document that is creating this document */
	public QuickDocument(Interpreted interpreted) {
		super(interpreted, null);
		theModelLoad = new SimpleObservable<>();
		theBodyLoad = new SimpleObservable<>();
	}

	/** @return The document's body */
	public QuickWidget getBody() {
		return theBody;
	}

	public ModelSetInstance update(QuickDocument.Interpreted interpreted, ObservableModelSet.ExternalModelSet extModels,
		Observable<?> until) throws ModelInstantiationException {
		ModelSetInstance models;
		try {
			models = createElementModel(interpreted, ExpressoEnv.STANDARD_JAVA.getBuiltModels().interpret().createInstance(until).build());
		} catch (ExpressoInterpretationException e) {
			throw new IllegalStateException("Could not update static models?", e);
		}
		satisfyContextValue("onModelLoad", ModelTypes.Event.VOID, theModelLoad.readOnly(), models);
		satisfyContextValue("onBodyLoad", ModelTypes.Event.VOID, theBodyLoad.readOnly(), models);
		ModelSetInstance headModels = interpreted.getHead().getModels().createInstance(extModels, until).withAll(models).build();
		updateModel(interpreted, headModels);
		return headModels;
	}

	@Override
	public ModelSetInstance update(ExElement.Interpreted<?> interpreted, ModelSetInstance models) throws ModelInstantiationException {
		throw new UnsupportedOperationException(
			"This update method is not supported.  Use update(QuickDocument.Interpreted, ExternalModelSet, Observable)");
	}

	@Override
	protected void updateModel(ExElement.Interpreted<?> interpreted, ModelSetInstance myModels) throws ModelInstantiationException {
		super.updateModel(interpreted, myModels);
		QuickDocument.Interpreted myInterpreted = (QuickDocument.Interpreted) interpreted;
		theModelLoad.onNext(null);
		if (theBody == null)
			theBody = myInterpreted.getBody().create(null);
		theBody.update(myInterpreted.getBody(), myModels);
		theBodyLoad.onNext(null);
	}
}
