package org.observe.dbug.qonfig;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.qommons.BreakpointHere;
import org.qommons.config.QonfigElementOrAddOn;

public class DbugBreak extends ExElement.Abstract implements DbugAction {
	public static final String BREAK = "break";

	@ExElementTraceable(toolkit = ExpressoDbugV0_1.DBUG, qonfigType = BREAK, interpretation = Interpreted.class, instance = DbugBreak.class)
	public static class Def extends ExElement.Def.Abstract<DbugBreak> implements DbugAction.Def<DbugBreak> {
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}
	}

	public static class Interpreted extends ExElement.Interpreted.Abstract<DbugBreak> implements DbugAction.Interpreted<DbugBreak> {
		Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		@Override
		public void updateAction() throws ExpressoInterpretationException {
			update();
		}

		@Override
		public DbugBreak create() {
			return new DbugBreak(getIdentity());
		}
	}

	DbugBreak(Object id) {
		super(id);
	}

	@Override
	public void execute(Object cause) {
		BreakpointHere.breakpoint();
	}

	@Override
	public DbugBreak copy(ExElement parent) {
		return (DbugBreak) super.copy(parent);
	}
}
