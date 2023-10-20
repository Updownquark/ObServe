package org.observe.expresso.qonfig;

import org.observe.expresso.CompiledExpressoEnv;
import org.observe.expresso.InterpretedExpressoEnv;
import org.qommons.config.QonfigElementOrAddOn;

public class ExpressoChildPlaceholder extends QonfigPromise {
	public static final String CHILD_PLACEHOLDER = "child-placeholder";

	@ExElementTraceable(toolkit = "Qonfig-Reference v0.1",
		qonfigType = CHILD_PLACEHOLDER,
		interpretation = Interpreted.class,
		instance = ExpressoChildPlaceholder.class)
	public static class Def<P extends ExpressoChildPlaceholder> extends QonfigPromise.Def<P> {
		private Object theDocumentParentId;

		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		public Object getDocumentParentId() {
			return theDocumentParentId;
		}

		@Override
		protected CompiledExpressoEnv getSourceEnv(CompiledExpressoEnv parentEnv) {
			ExElement.Def<?> content = getContent();
			String targetDoc = content.getElement().getDocument().getLocation();
			content = content.getParentElement();
			while (content != null
				&& !ExElement.documentsMatch(content.getElement().getDocument().getLocation(), targetDoc))
				content = content.getParentElement();
			if (content != null) {
				theDocumentParentId = content.getIdentity();
				return content.getExpressoEnv();
			} else {
				reporting().error("Could not locate ancestor in hierarchy with document " + getElement().getDocument().getLocation());
				return parentEnv;
			}
		}

		@Override
		public Interpreted<? extends P> interpret() {
			return new Interpreted<>(this, null);
		}
	}

	public static class Interpreted<P extends ExpressoChildPlaceholder> extends QonfigPromise.Interpreted<P> {
		Interpreted(Def<? super P> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def<? super P> getDefinition() {
			return (Def<? super P>) super.getDefinition();
		}

		@Override
		protected InterpretedExpressoEnv getSourceEnv(InterpretedExpressoEnv parentEnv) {
			Object dpi = getDefinition().getDocumentParentId();
			if (dpi == null)
				return parentEnv;
			ExElement.Interpreted<?> content = getContent();
			while (content != null && content.getIdentity() != dpi)
				content = content.getParentElement();
			if (content != null)
				return content.getExpressoEnv();
			else {
				reporting().error("Could not locate ancestor in hierarchy with ID " + dpi);
				return parentEnv;
			}
		}
	}

	ExpressoChildPlaceholder(Object id) {
		super(id);
	}
}
