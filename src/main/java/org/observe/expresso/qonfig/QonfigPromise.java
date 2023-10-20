package org.observe.expresso.qonfig;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

public abstract class QonfigPromise extends ExElement.Abstract {
	public static abstract class Def<P extends QonfigPromise> extends ExElement.Def.Abstract<P> {
		private ExElement.Def<?> theContent;

		protected Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		public ExElement.Def<?> getContent() {
			return theContent;
		}

		public void update(ExpressoQIS session, ExElement.Def<?> content) throws QonfigInterpretationException {
			theContent = content;
			update(session);
		}

		protected abstract Interpreted<? extends P> interpret();
	}

	public static abstract class Interpreted<P extends QonfigPromise> extends ExElement.Interpreted.Abstract<P> {
		private ExElement.Interpreted<?> theContent;

		protected Interpreted(Def<? super P> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		public ExElement.Interpreted<?> getContent() {
			return theContent;
		}

		public void update(ExElement.Interpreted<?> content) throws ExpressoInterpretationException {
			theContent = content;
			update(InterpretedExpressoEnv.INTERPRETED_STANDARD_JAVA);
		}
	}

	protected QonfigPromise(Object id) {
		super(id);
	}
}
