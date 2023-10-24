package org.observe.expresso.qonfig;

import org.observe.Observable;
import org.observe.expresso.CompiledExpressoEnv;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

public class ExpressoChildPlaceholder extends ExElement.Abstract implements QonfigPromise {
	public static final String CHILD_PLACEHOLDER = "child-placeholder";

	@ExElementTraceable(toolkit = ExpressoExternalReference.QONFIG_REFERENCE_TK,
		qonfigType = CHILD_PLACEHOLDER,
		interpretation = Interpreted.class,
		instance = ExpressoChildPlaceholder.class)
	public static class Def<P extends ExpressoChildPlaceholder> extends ExElement.Def.Abstract<P> implements QonfigPromise.Def<P> {
		private Object theDocumentParentId;
		private ExElement.Def<?> theFulfilledContent;
		private CompiledExpressoEnv theExtExpressoEnv;

		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		public Object getDocumentParentId() {
			return theDocumentParentId;
		}

		@Override
		public ExElement.Def<?> getFulfilledContent() {
			return theFulfilledContent;
		}

		@Override
		public void update(ExpressoQIS session, ExElement.Def<?> content) throws QonfigInterpretationException {
			theFulfilledContent = content;

			String targetDoc = content.getElement().getDocument().getLocation();
			content = content.getParentElement();
			while (content != null
				&& !ExElement.documentsMatch(content.getElement().getDocument().getLocation(), targetDoc))
				content = content.getParentElement();
			if (content != null) {
				theDocumentParentId = content.getIdentity();
				theExtExpressoEnv = content.getExpressoEnv();
			} else {
				reporting().error("Could not locate ancestor in hierarchy with document " + getElement().getDocument().getLocation());
			}

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

			Object dpi = getDefinition().getDocumentParentId();
			if (dpi == null)
				return;
			while (content != null && content.getIdentity() != dpi)
				content = content.getParentElement();
			if (content != null)
				theExtExpressoEnv = content.getExpressoEnv();
			else {
				reporting().error("Could not locate ancestor in hierarchy with ID " + dpi);
			}
		}

		@Override
		public QonfigPromise create(ExElement content) {
			return new ExpressoChildPlaceholder(getIdentity(), content);
		}
	}

	private Object theDocumentParentId;
	private ExElement theContent;

	ExpressoChildPlaceholder(Object id, ExElement content) {
		super(id);
		theContent = content;
	}

	@Override
	public void update(QonfigPromise.Interpreted<?> interpreted) {
		super.update(interpreted, null);
		ExpressoChildPlaceholder.Interpreted<?> myInterpreted = (ExpressoChildPlaceholder.Interpreted<?>) interpreted;
		theDocumentParentId = myInterpreted.getDefinition().getDocumentParentId();
	}

	@Override
	public ModelSetInstance getExternalModels(ModelSetInstance contentModels, Observable<?> until) throws ModelInstantiationException {
		if (theDocumentParentId == null)
			return null;
		ExElement content = theContent;
		while (content != null && content.getIdentity() != theDocumentParentId)
			content = content.getParentElement();
		if (content != null)
			return content.getUpdatingModels(); // Should still be updating as the content is one of its descendants
		else {
			reporting().error("Could not locate ancestor in hierarchy with ID " + theDocumentParentId);
			return null;
		}
	}

	@Override
	public ExpressoChildPlaceholder copy(ExElement content) {
		ExpressoChildPlaceholder copy = (ExpressoChildPlaceholder) super.copy(null);
		copy.theContent = content;
		return copy;
	}
}
