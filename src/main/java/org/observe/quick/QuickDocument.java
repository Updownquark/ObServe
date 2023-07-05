package org.observe.quick;

import java.util.Collections;

import org.observe.Observable;
import org.observe.SimpleObservable;
import org.observe.expresso.ClassView;
import org.observe.expresso.ExpressoEnv;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ClassViewElement;
import org.observe.expresso.qonfig.ElementTypeTraceability;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.ExpressoSessionImplV0_1;
import org.observe.expresso.qonfig.ObservableModelElement;
import org.observe.expresso.qonfig.QonfigChildGetter;
import org.observe.quick.style.QuickStyleSheet;
import org.observe.quick.style.StyleQIS;
import org.observe.quick.style.StyleSessionImplV0_1;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;

/** The root of a quick file, containing all information needed to power an application */
public class QuickDocument extends ExElement.Abstract {
	public static final String QUICK = "quick";
	private static final ElementTypeTraceability<QuickDocument, Interpreted, Def> TRACEABILITY = ElementTypeTraceability
		.<QuickDocument, Interpreted, Def> build(QuickCoreInterpretation.NAME, QuickCoreInterpretation.VERSION, QUICK)//
		.reflectMethods(Def.class, Interpreted.class, QuickDocument.class)//
		.build();

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
		public void update(ExpressoQIS session) throws QonfigInterpretationException {
			withTraceability(TRACEABILITY.validate(session.getFocusType(), session.reporting()));
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
		// Can't use reflection here because we're configuring 2 related Qonfig types for one java type
		private static final ElementTypeTraceability<ExElement, QuickHeadSection, Def> EXPRESSO_TRACEABILITY = ElementTypeTraceability
			.<ExElement, QuickHeadSection, Def> build(ExpressoSessionImplV0_1.TOOLKIT_NAME, ExpressoSessionImplV0_1.VERSION, "expresso")//
			.withChild("imports",
				d -> d.getClassViewElement() == null ? Collections.emptyList() : Collections.singletonList(d.getClassViewElement()), null,
					null)//
			.withChild("models",
				d -> d.getModelElement() == null ? Collections.emptyList() : Collections.singletonList(d.getModelElement()), //
					i -> i.getModelElement() == null ? Collections.emptyList() : Collections.singletonList(i.getModelElement()), //
						null)//
			.build();

		private static final ElementTypeTraceability<ExElement, QuickHeadSection, Def> WSS_TRACEABILITY = ElementTypeTraceability
			.<ExElement, QuickHeadSection, Def> build(StyleSessionImplV0_1.NAME, StyleSessionImplV0_1.VERSION, "with-style-sheet")//
			.withChild("style-sheet",
				d -> d.getStyleSheet() == null ? Collections.emptyList() : Collections.singletonList(d.getStyleSheet()), //
					null, null)//
			.build();

		/** The definition of a head section */
		public static class Def extends ExElement.Def.Abstract<ExElement> {
			private ClassViewElement theClassViewElement;
			private ClassView theClassView;
			private ObservableModelElement.ModelSetElement.Def<?> theModelElement;
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

			public ClassViewElement getClassViewElement() {
				return theClassViewElement;
			}

			/** @return The models defined in this head section */
			@Override
			public ObservableModelSet.Built getModels() {
				return theModelElement.getModels();
			}

			public ObservableModelElement.ModelSetElement.Def<?> getModelElement() {
				return theModelElement;
			}

			/** @return The style sheet defined in this head section */
			public QuickStyleSheet getStyleSheet() {
				return theStyleSheet;
			}

			@Override
			public void update(ExpressoQIS session) throws QonfigInterpretationException {
				withTraceability(EXPRESSO_TRACEABILITY.validate(session.asElement("expresso").getFocusType(), session.reporting()));
				withTraceability(WSS_TRACEABILITY.validate(session.asElement("with-style-sheet").getFocusType(), session.reporting()));
				super.update(session);
				theClassViewElement = ExElement.useOrReplace(ClassViewElement.class, theClassViewElement, session, "imports");
				ClassView.Builder cvBuilder = ClassView.build()//
					.withWildcardImport("java.lang")//
					.withWildcardImport(MouseCursor.StandardCursors.class.getName());
				if (theClassViewElement != null)
					theClassViewElement.configureClassView(cvBuilder);
				theClassView = cvBuilder.build();
				// Install the class view now, so the model can use it
				session.setExpressoEnv(session.getExpressoEnv().with(null, theClassView));
				theModelElement = ExElement.useOrReplace(ObservableModelElement.ModelSetElement.Def.class, theModelElement, session,
					"models");
				session.setExpressoEnv(session.getExpressoEnv().with(getModels(), theClassView));
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
				return new QuickHeadSection(this, document);
			}
		}

		private ObservableModelElement.ModelSetElement.Interpreted<?> theModelElement;

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

		/** @return The class view defined in this head section */
		public ClassView getClassView() {
			return getDefinition().getClassView();
		}

		public ObservableModelElement.ModelSetElement.Interpreted<?> getModelElement() {
			return theModelElement;
		}

		/** @return The style sheet defined in this head section */
		public QuickStyleSheet getStyleSheet() {
			return getDefinition().getStyleSheet();
		}

		@Override
		protected void update() throws ExpressoInterpretationException {
			super.update();
			if (theModelElement == null || theModelElement.getDefinition() != getDefinition().getModelElement()) {
				if (theModelElement != null)
					theModelElement.destroy();
				theModelElement = getDefinition().getModelElement() == null ? null : getDefinition().getModelElement().interpret(this);
			}
			if (theModelElement != null)
				theModelElement.update();
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
