package org.observe.quick;

import org.observe.expresso.ClassView;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.InterpretedModelSet;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.quick.style.QuickStyleSheet;
import org.observe.quick.style.StyleQIS;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;

/** The root of a quick file, containing all information needed to power an application */
public class QuickDocument2 extends QuickElement.Abstract {
	/** The definition of a Quick document */
	public static class Def extends QuickElement.Def.Abstract<QuickDocument2> {
		private QuickHeadSection.Def theHead;
		private QuickWidget.Def<?> theBody;

		/**
		 * @param parent The parent of this document, typically null
		 * @param element The element that this document definition is being interpreted from
		 */
		public Def(QuickElement.Def<?> parent, QonfigElement element) {
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
			super.update(session);
			theHead = QuickElement.useOrReplace(QuickHeadSection.Def.class, theHead, session, "head");
			if (theHead != null)
				session.setExpressoEnv(session.getExpressoEnv().with(theHead.getModels(), theHead.getClassView()));
			theBody = QuickElement.useOrReplace(QuickWidget.Def.class, theBody, session, "body");
		}

		/**
		 * @param parent The interpreted parent, typically null
		 * @return The interpreted document
		 */
		public Interpreted interpret(QuickElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}
	}

	/** An interpreted Quick document */
	public static class Interpreted extends QuickElement.Interpreted.Abstract<QuickDocument2> {
		private QuickHeadSection theHead;
		private QuickWidget.Interpreted<?> theBody;

		/**
		 * @param def The document's definition
		 * @param parent The document's interpreted parent, typically null
		 */
		public Interpreted(Def def, QuickElement.Interpreted<?> parent) {
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
				theBody = getDefinition().getBody().interpret(null);
			theBody.update(new QuickStyledElement.QuickInterpretationCache());
		}

		/** @return The new document */
		public QuickDocument2 create() {
			return new QuickDocument2(this);
		}
	}

	/** Represents the head section of a Quick document, containing class-view, model, and style information */
	public static class QuickHeadSection extends QuickElement.Interpreted.Abstract<QuickElement> {
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
		public QuickHeadSection(Def def, QuickDocument2.Interpreted document, ClassView classView, InterpretedModelSet models,
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

		/** The definition of a head section */
		public static class Def extends QuickElement.Def.Abstract<QuickElement> {
			private ClassView theClassView;
			private ObservableModelSet.Built theModels;
			private QuickStyleSheet theStyleSheet;

			/**
			 * @param parent The document that this head section is for
			 * @param element The element that this head section is being parsed from
			 */
			public Def(QuickDocument2.Def parent, QonfigElement element) {
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
				super.update(session);
				ClassView cv = session.interpretChildren("imports", ClassView.class).peekFirst();
				if (cv == null) {
					ClassView defaultCV = ClassView.build().withWildcardImport("java.lang").build();
					cv = defaultCV;
				}
				theClassView = cv;
				ObservableModelSet.Built model = session.interpretChildren("models", ObservableModelSet.Built.class).peekFirst();
				if (model == null)
					model = ObservableModelSet.build("models", ObservableModelSet.JAVA_NAME_CHECKER).build();
				theModels = model;
				session.setExpressoEnv(session.getExpressoEnv().with(model, cv));
				theStyleSheet = session.as(StyleQIS.class).getStyleSheet();
			}

			/**
			 * @param document The document that the head section is for
			 * @return The new head section
			 * @throws ExpressoInterpretationException If the {@link org.observe.expresso.ObservableModelSet.Built#interpret()
			 *         interpretation} of the head section's {@link #getModels() models} fails
			 */
			public QuickHeadSection interpret(QuickDocument2.Interpreted document) throws ExpressoInterpretationException {
				return new QuickHeadSection(this, document, getClassView(), getModels().interpret(), theStyleSheet);
			}
		}
	}

	private QuickWidget theBody;

	/** @param interpreted The interpreted document that is creating this document */
	public QuickDocument2(Interpreted interpreted) {
		super(interpreted, null);
	}

	/** @return The document's body */
	public QuickWidget getBody() {
		return theBody;
	}

	@Override
	protected void updateModel(QuickElement.Interpreted<?> interpreted, ModelSetInstance myModels) throws ModelInstantiationException {
		super.updateModel(interpreted, myModels);
		QuickDocument2.Interpreted myInterpreted = (QuickDocument2.Interpreted) interpreted;
		if (theBody == null)
			theBody = myInterpreted.getBody().create(null);
		theBody.update(myInterpreted.getBody(), myModels);
	}
}
