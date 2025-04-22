package org.observe.dbug.qonfig;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

public class DbugTag extends ExElement.Abstract {
	public static final String DBUG_TAG = "dbug-tag";

	@ExElementTraceable(toolkit = ExpressoDbugV0_1.DBUG,
		qonfigType = DBUG_TAG,
		interpretation = Interpreted.class,
		instance = DbugTag.class)
	public static class Def extends ExElement.Def.Abstract<DbugTag> {
		private String theTag;

		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@QonfigAttributeGetter
		public String getTag() {
			return theTag;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			theTag = session.getValueText();
			if (theTag.isEmpty())
				reporting().at(session.getValue().getContent()).error("Empty tag");
		}

		public Interpreted interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}
	}

	public static class Interpreted extends ExElement.Interpreted.Abstract<DbugTag> {
		Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		public void updateTag() throws ExpressoInterpretationException {
			update();
		}

		public DbugTag create() {
			return new DbugTag(getIdentity());
		}
	}

	private String theTag;

	DbugTag(Object id) {
		super(id);
	}

	public String getTag() {
		return theTag;
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);

		Interpreted myInterpreted = (Interpreted) interpreted;
		theTag = myInterpreted.getDefinition().getTag();
	}

	@Override
	public DbugTag copy(ExElement parent) {
		return (DbugTag) super.copy(parent);
	}
}
