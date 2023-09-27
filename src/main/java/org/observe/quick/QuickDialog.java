package org.observe.quick;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;

public interface QuickDialog extends ExElement {
	@ExElementTraceable(toolkit = QuickCoreInterpretation.CORE,
		qonfigType = "dialog",
		interpretation = Interpreted.class,
		instance = QuickDialog.class)
	public static interface Def<D extends QuickDialog> extends ExElement.Def<D> {
		Interpreted<? extends D> interpret(ExElement.Interpreted<?> parent);
	}

	public static interface Interpreted<D extends QuickDialog> extends ExElement.Interpreted<D> {
		void updateDialog(InterpretedExpressoEnv expressoEnv) throws ExpressoInterpretationException;

		D create();
	}

	@Override
	QuickDialog copy(ExElement parent);
}
