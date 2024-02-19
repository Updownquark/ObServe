package org.observe.expresso.qonfig;

import org.observe.expresso.CompiledExpressoEnv;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.ModelInstantiator;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelSetInstanceBuilder;
import org.qommons.config.QonfigChildDef;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

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
		private CompiledExpressoEnv theExtExpressoEnv;
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
		public void update(ExpressoQIS session, ExElement.Def<?> content) throws QonfigInterpretationException {
			theFulfilledContent = content;

			theRefRoleName = session.getAttributeText("ref-role");

			String targetDoc = content.getElement().getDocument().getLocation();
			content = content.getParentElement();
			while (content != null
				&& (content.getPromise() == null
				|| !ExElement.documentsMatch(content.getPromise().getElement().getDocument().getLocation(), targetDoc)))
				content = content.getParentElement();
			if (content != null) {
				theDocumentParent = content;
				theExtExpressoEnv = theDocumentParent.getExpressoEnv();
			} else
				reporting().error("Could not locate ancestor in hierarchy with document " + getElement().getDocument().getLocation());

			update(session);
		}

		@Override
		public CompiledExpressoEnv getExternalExpressoEnv() {
			return theExtExpressoEnv;
		}

		@Override
		public void setExternalExpressoEnv(CompiledExpressoEnv env) {
			theExtExpressoEnv = env;
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
		private InterpretedExpressoEnv theExtExpressoEnv;

		Interpreted(Def<? super P> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def<? super P> getDefinition() {
			return (Def<? super P>) super.getDefinition();
		}

		@Override
		public ExElement.Interpreted<?> getFulfilledContent() {
			return theFulfilledContent;
		}

		@Override
		public InterpretedExpressoEnv getExternalExpressoEnv() {
			return theExtExpressoEnv;
		}

		@Override
		public void setExternalExpressoEnv(InterpretedExpressoEnv env) {
			theExtExpressoEnv = env;
		}

		@Override
		public void update(InterpretedExpressoEnv env, ExElement.Interpreted<?> content) throws ExpressoInterpretationException {
			theFulfilledContent = content;
			super.update(env);

			Object dpi = getDefinition().getDocumentParent().getIdentity();
			while (content != null && content.getIdentity() != dpi)
				content = content.getParentElement();
			if (content != null) {
				theDocumentParent = content;
				theExtExpressoEnv = theDocumentParent.getExpressoEnv()//
					.forChild(getDefinition().getExternalExpressoEnv());
			} else {
				reporting().error("Could not locate ancestor in hierarchy with ID " + dpi);
			}
		}

		@Override
		public QonfigPromise create(ExElement content) {
			return new ExpressoChildPlaceholder(getIdentity(), content);
		}
	}

	private ExElement theDocumentParent;
	private ExElement theContent;
	private ObservableModelSet.ModelInstantiator theExtLocalModels;

	ExpressoChildPlaceholder(Object id, ExElement content) {
		super(id);
		theContent = content;
	}

	@Override
	public void update(QonfigPromise.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.update(interpreted, null);
		ExpressoChildPlaceholder.Interpreted<?> myInterpreted = (ExpressoChildPlaceholder.Interpreted<?>) interpreted;
		Object dpi = myInterpreted.getDefinition().getDocumentParent().getIdentity();
		ExElement content = theContent;
		while (content != null && content.getIdentity() != dpi)
			content = content.getParentElement();
		if (content != null) {
			theDocumentParent = content;
			if (interpreted.getExternalExpressoEnv().getModels().getIdentity() != theDocumentParent.getParentElement().getModels()
				.getIdentity())
				theExtLocalModels = interpreted.getExternalExpressoEnv().getModels().instantiate();
			else
				theExtLocalModels = null;
		} else {
			reporting().error("Could not locate ancestor in hierarchy with ID " + dpi);
			theDocumentParent = null;
			theExtLocalModels = null;
		}
	}

	@Override
	public ModelInstantiator getExtModels() {
		if (theExtLocalModels != null)
			return theExtLocalModels;
		else
			return theDocumentParent.getModels();
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();
		if (theExtLocalModels != null)
			theExtLocalModels.instantiate();
	}

	@Override
	protected void addRuntimeModels(ModelSetInstanceBuilder builder, ModelSetInstance elementModels) throws ModelInstantiationException {
		// Should still be updating as the content is one of its descendants
		ModelSetInstance parentModels = theDocumentParent.getUpdatingModels();
		if (theExtLocalModels == null)
			builder.withAll(parentModels);
		else
			builder.withAll(theExtLocalModels.wrap(parentModels));
		super.addRuntimeModels(builder, elementModels);
	}

	@Override
	public ExpressoChildPlaceholder copy(ExElement content) {
		ExpressoChildPlaceholder copy = (ExpressoChildPlaceholder) super.copy(null);
		copy.theContent = content;
		return copy;
	}
}
