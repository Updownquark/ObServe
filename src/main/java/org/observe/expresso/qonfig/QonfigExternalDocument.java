package org.observe.expresso.qonfig;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.qommons.config.QonfigElementDef;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

/**
 * The root of an external document loaded by a {@link QonfigPromise} to be injected into a source document
 */
public abstract class QonfigExternalDocument extends ExElement.Abstract {
	/** The name of the toolkit defining this element */
	public static final String QONFIG_REFERENCE_TK = "Qonfig-Reference v0.1";
	/** The XML name of this element */
	public static final String EXTERNAL_DOCUMENT = "external-document";

	/**
	 * {@link QonfigExternalDocument} definition
	 *
	 * @param <C> The type of document to create
	 */
	@ExElementTraceable(toolkit = QONFIG_REFERENCE_TK,
		qonfigType = EXTERNAL_DOCUMENT,
		interpretation = Interpreted.class,
		instance = ExpressoExternalDocument.class)
	public static abstract class Def<C extends QonfigExternalDocument> extends ExElement.Def.Abstract<C> {
		private QonfigElementDef theFulfills;
		private ExElement.Def<?> theContent;

		/**
		 * @param parent The parent element of this document, typically null
		 * @param qonfigType The Qonfig type of this element
		 */
		protected Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		/** @return The Qonfig type of the promise element that this content fulfills */
		@QonfigAttributeGetter("fulfills")
		public QonfigElementDef getFulfills() {
			return theFulfills;
		}

		/** @return The fulfilled content of the document to be injected into the source document */
		@QonfigChildGetter("fulfillment")
		public ExElement.Def<?> getContent() {
			return theContent;
		}

		/**
		 * Initializes or updates this external document
		 *
		 * @param session The expresso session to use to update this document
		 * @param content The fulfilled content of the document to be injected into the source document
		 * @throws QonfigInterpretationException If this document could not be interpreted
		 */
		public void update(ExpressoQIS session, ExElement.Def<?> content) throws QonfigInterpretationException {
			theContent = content;
			update(session);
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);

			if (theFulfills == null)
				initFulfills(session);
		}

		/**
		 * Determines the Qonfig element type fulfilled by this external document
		 *
		 * @param session The Expresso interpretation session to use to get Qonfig types
		 * @throws QonfigInterpretationException If the fulfillment type could not be determined
		 */
		protected void initFulfills(ExpressoQIS session) throws QonfigInterpretationException {
			try {
				theFulfills = session.getElement().getDocument().getDocToolkit().getElement(//
					session.getAttributeText("fulfills"));
				if (theFulfills == null)
					reporting().at(session.attributes().get("fulfills").getName())
					.error("Fulfillment type '" + session.getAttributeText("fulfills") + "' not found");
			} catch (IllegalArgumentException e) {
				reporting().error(e.getMessage(), e);
			}
		}

		/** @return The interpreted external document */
		public abstract Interpreted<? extends C> interpret();
	}

	/**
	 * {@link QonfigExternalDocument} interpretation
	 *
	 * @param <C> The type of content to create
	 */
	public static abstract class Interpreted<C extends QonfigExternalDocument> extends ExElement.Interpreted.Abstract<C> {
		private ExElement.Interpreted<?> theContent;

		/**
		 * @param definition The definition to interpret
		 * @param parent The parent element of this document, typically null
		 */
		protected Interpreted(Def<? super C> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def<? super C> getDefinition() {
			return (Def<? super C>) super.getDefinition();
		}

		/** @return The fulfilled content of the document to be injected into the source document */
		public ExElement.Interpreted<?> getContent() {
			return theContent;
		}

		/**
		 * Initializes or updates this external document
		 *
		 * @param content The fulfilled content of the document to be injected into the source document
		 * @throws ExpressoInterpretationException If this document could not be interpreted
		 */
		public void update(ExElement.Interpreted<?> content) throws ExpressoInterpretationException {
			theContent = content;

			super.update(InterpretedExpressoEnv.INTERPRETED_STANDARD_JAVA);
		}
	}

	/** @param id The element ID for this document */
	protected QonfigExternalDocument(Object id) {
		super(id);
	}

	@Override
	public QonfigExternalDocument copy(ExElement parent) {
		return (QonfigExternalDocument) super.copy(parent);
	}
}
