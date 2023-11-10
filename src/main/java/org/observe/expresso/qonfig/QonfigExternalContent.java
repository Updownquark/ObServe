package org.observe.expresso.qonfig;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.qommons.config.QonfigElementDef;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

public abstract class QonfigExternalContent extends ExElement.Abstract {
	@ExElementTraceable(toolkit = "Qonfig-Reference v0.1",
		qonfigType = "external-content",
		interpretation = Interpreted.class,
		instance = ExpressoExternalContent.class)
	public static abstract class Def<C extends QonfigExternalContent> extends ExElement.Def.Abstract<C> {
		private QonfigElementDef theFulfills;
		private ExElement.Def<?> theContent;

		protected Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@QonfigAttributeGetter("fulfills")
		public QonfigElementDef getFulfills() {
			return theFulfills;
		}

		@QonfigChildGetter("fulfillment")
		public ExElement.Def<?> getContent() {
			return theContent;
		}

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

		public abstract Interpreted<? extends C> interpret();
	}

	public static abstract class Interpreted<C extends QonfigExternalContent> extends ExElement.Interpreted.Abstract<C> {
		private ExElement.Interpreted<?> theContent;

		protected Interpreted(Def<? super C> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def<? super C> getDefinition() {
			return (Def<? super C>) super.getDefinition();
		}

		public ExElement.Interpreted<?> getContent() {
			return theContent;
		}

		public void update(ExElement.Interpreted<?> content) throws ExpressoInterpretationException {
			theContent = content;

			super.update(InterpretedExpressoEnv.INTERPRETED_STANDARD_JAVA);
		}
	}

	protected QonfigExternalContent(Object id) {
		super(id);
	}

	@Override
	public QonfigExternalContent copy(ExElement parent) {
		return (QonfigExternalContent) super.copy(parent);
	}
}
