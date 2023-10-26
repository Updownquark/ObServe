package org.observe.expresso.qonfig;

import org.observe.Observable;
import org.observe.expresso.CompiledExpressoEnv;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.ModelInstantiator;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.qommons.config.QonfigChildDef;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

public class ExpressoChildPlaceholder extends ExElement.Abstract implements QonfigPromise {
	public static final String CHILD_PLACEHOLDER = "child-placeholder";

	@ExElementTraceable(toolkit = ExpressoExternalReference.QONFIG_REFERENCE_TK,
		qonfigType = CHILD_PLACEHOLDER,
		interpretation = Interpreted.class,
		instance = ExpressoChildPlaceholder.class)
	public static class Def<P extends ExpressoChildPlaceholder> extends ExElement.Def.Abstract<P> implements QonfigPromise.Def<P> {
		private ExElement.Def<?> theDocumentParent;
		private ExElement.Def<?> theFulfilledContent;
		private CompiledExpressoEnv theExtExpressoEnv;
		private String theRefRoleName;
		private QonfigChildDef theRefRole;

		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		public ExElement.Def<?> getDocumentParent() {
			return theDocumentParent;
		}

		public String getRefRoleName() {
			return theRefRoleName;
		}

		@QonfigAttributeGetter("ref-role")
		public QonfigChildDef getRefRole() {
			return theRefRole;
		}

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
				theExtExpressoEnv.getModels().interpret(theExtExpressoEnv);
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
	public void update(QonfigPromise.Interpreted<?> interpreted) {
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
	public ModelSetInstance getExternalModels(ModelSetInstance contentModels, Observable<?> until) throws ModelInstantiationException {
		// Should still be updating as the content is one of its descendants
		ModelSetInstance parentModels = theDocumentParent.getUpdatingModels();
		if (theExtLocalModels == null)
			return parentModels;
		return theExtLocalModels.wrap(parentModels);
	}

	@Override
	public ExpressoChildPlaceholder copy(ExElement content) {
		ExpressoChildPlaceholder copy = (ExpressoChildPlaceholder) super.copy(null);
		copy.theContent = content;
		return copy;
	}
}
