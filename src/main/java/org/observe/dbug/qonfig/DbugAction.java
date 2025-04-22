package org.observe.dbug.qonfig;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.qonfig.ExElement;

public interface DbugAction extends ExElement {
	public interface Def<A extends DbugAction> extends ExElement.Def<A> {
		public Interpreted<A> interpret(ExElement.Interpreted<?> parent);
	}

	public interface Interpreted<A extends DbugAction> extends ExElement.Interpreted<A> {
		void updateAction() throws ExpressoInterpretationException;

		A create();
	}

	void execute(Object cause);

	@Override
	DbugAction copy(ExElement parent);
}
