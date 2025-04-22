package org.observe.expresso.qonfig;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.qommons.config.QonfigChildDef;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.io.LocatedFilePosition;

/**
 * <p>
 * A promise inside an externally-loaded expresso document that is fulfilled by content specified on the {@link ExpressoExternalReference}
 * that loaded the external content.
 * </p>
 * <p>
 * This is the mechanism by which content may specified in the root document to be injected into the middle of externally-loaded content.
 * </p>
 */
public class ExpressoChildPlaceholder extends ExElement.Abstract implements QonfigPromise {
	/** The XML name of this element */
	public static final String CHILD_PLACEHOLDER = "child-placeholder";

	/**
	 * {@link ExpressoChildPlaceholder} definition
	 *
	 * @param <P> The sub-type of element to create
	 */
	@ExElementTraceable(toolkit = QonfigExternalDocument.QONFIG_REFERENCE_TK,
		qonfigType = CHILD_PLACEHOLDER,
		interpretation = Interpreted.class,
		instance = ExpressoChildPlaceholder.class)
	public static class Def<P extends ExpressoChildPlaceholder> extends ExElement.Def.Abstract<P> implements QonfigPromise.Def<P> {
		private ExElement.Def<?> theDocumentParent;
		private ExElement.Def<?> theFulfilledContent;
		private String theRefRoleName;
		private QonfigChildDef theRefRole;

		/**
		 * @param parent The parent element containing this promise
		 * @param qonfigType The Qonfig type of this element
		 */
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		/** @return The element in the document that loaded the external content */
		public ExElement.Def<?> getDocumentParent() {
			return theDocumentParent;
		}

		/** @return The name of the role in the {@link #getDocumentParent() document parent} that this promise will be fulfilled with */
		public String getRefRoleName() {
			return theRefRoleName;
		}

		/** @return The role in the {@link #getDocumentParent() document parent} that this promise will be fulfilled with */
		@QonfigAttributeGetter("ref-role")
		public QonfigChildDef getRefRole() {
			return theRefRole;
		}

		/** @param refRole The role in the {@link #getDocumentParent() document parent} that this promise will be fulfilled with */
		public void setRefRole(QonfigChildDef refRole) {
			theRefRole = refRole;
		}

		@Override
		public ExElement.Def<?> getFulfilledContent() {
			return theFulfilledContent;
		}

		@Override
		public <D extends ExElement.Def<?>> D as(Class<D> type, LocatedFilePosition errorPosition) throws QonfigInterpretationException {
			if (type.isInstance(theFulfilledContent))
				return (D) theFulfilledContent;
			return super.as(type, errorPosition);
		}

		@Override
		public void update(ExpressoQIS session, ExElement.Def<?> content) throws QonfigInterpretationException {
			theFulfilledContent = content;

			theRefRoleName = session.getAttributeText("ref-role");

			String targetDoc = content.getDocument();
			content = content.getParentElement();
			while (content != null
				&& (content.getPromise() == null || !ExElement.documentsMatch(content.getPromise().getDocument(), targetDoc)))
				content = content.getParentElement();
			if (content != null) {
				theDocumentParent = content;
			} else
				reporting().error("Could not locate ancestor in hierarchy with document " + getElement().getDocument().getLocation());

			update(session);
		}

		@Override
		public Interpreted<? extends P> interpret() {
			return new Interpreted<>(this, null);
		}
	}

	/**
	 * {@link ExpressoChildPlaceholder} interpretation
	 *
	 * @param <P> The sub-type of element to create
	 */
	public static class Interpreted<P extends ExpressoChildPlaceholder> extends ExElement.Interpreted.Abstract<P>
	implements QonfigPromise.Interpreted<P> {
		private ExElement.Interpreted<?> theFulfilledContent;
		private ExElement.Interpreted<?> theDocumentParent;

		Interpreted(Def<? super P> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def<? super P> getDefinition() {
			return (Def<? super P>) super.getDefinition();
		}

		/** @return The element in the document that loaded the external content */
		public ExElement.Interpreted<?> getDocumentParent() {
			return theDocumentParent;
		}

		@Override
		public ExElement.Interpreted<?> getFulfilledContent() {
			return theFulfilledContent;
		}

		@Override
		public <I extends ExElement.Interpreted<?>> I as(Class<I> type, LocatedFilePosition errorPosition)
			throws ExpressoInterpretationException {
			if (type.isInstance(theFulfilledContent))
				return (I) theFulfilledContent;
			return super.as(type, errorPosition);
		}

		@Override
		public void update(ExElement.Interpreted<?> content) throws ExpressoInterpretationException {
			theFulfilledContent = content;
			addLogicalParent(content.getParentElement());
			super.update();
			content.addLogicalParent(this);

			Object dpi = getDefinition().getDocumentParent().getIdentity();
			while (content != null && content.getIdentity() != dpi)
				content = content.getParentElement();
			if (content != null) {
				theDocumentParent = content;
			} else {
				reporting().error("Could not locate ancestor in hierarchy with ID " + dpi);
			}
		}

		@Override
		public QonfigPromise create(ExElement content) {
			return new ExpressoChildPlaceholder(getIdentity(), content);
		}
	}

	private ExElement theFulfilledContent;

	ExpressoChildPlaceholder(Object id, ExElement content) {
		super(id);
		theFulfilledContent = content;
	}

	@Override
	public <E extends ExElement> E as(Class<E> type, LocatedFilePosition errorPosition) throws ModelInstantiationException {
		if (type.isInstance(theFulfilledContent))
			return (E) theFulfilledContent;
		return super.as(type, errorPosition);
	}

	@Override
	public void update(QonfigPromise.Interpreted<?> interpreted) throws ModelInstantiationException {
		addLogicalParent(theFulfilledContent);
		super.update(interpreted, null);
	}

	@Override
	public ExpressoChildPlaceholder copy(ExElement content) {
		ExpressoChildPlaceholder copy = (ExpressoChildPlaceholder) super.copy(null);
		copy.theFulfilledContent = content;
		return copy;
	}
}
