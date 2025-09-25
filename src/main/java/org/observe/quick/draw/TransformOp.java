package org.observe.quick.draw;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.qonfig.ExElement;

public interface TransformOp extends ExElement {
	public interface Def<T extends TransformOp> extends ExElement.Def<T> {
		Interpreted<? extends T> interpret(ExElement.Interpreted<?> parent);
	}

	public interface Interpreted<T extends TransformOp> extends ExElement.Interpreted<T> {
		void updateOperation() throws ExpressoInterpretationException;

		T create();
	}

	@Override
	TransformOp copy(ExElement parent);
}
