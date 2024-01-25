package org.observe.quick;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;

/** A dialog that may be displayed over the main application window by a widget */
public interface QuickDialog extends ExElement {
	/**
	 * Definition for a {@link QuickDialog}
	 *
	 * @param <D> The sub-type of dialog to create
	 */
	@ExElementTraceable(toolkit = QuickCoreInterpretation.CORE,
		qonfigType = "dialog",
		interpretation = Interpreted.class,
		instance = QuickDialog.class)
	public static interface Def<D extends QuickDialog> extends ExElement.Def<D> {
		/**
		 * @param parent The parent for the interpreted dialog
		 * @return The interpreted dialog
		 */
		Interpreted<? extends D> interpret(ExElement.Interpreted<?> parent);
	}

	/**
	 * Interpretation for a {@link QuickDialog}
	 *
	 * @param <D> The sub-type of dialog to create
	 */
	public static interface Interpreted<D extends QuickDialog> extends ExElement.Interpreted<D> {
		/**
		 * Initializes or updates this dialog
		 *
		 * @param expressoEnv The expresso environment for evaluating expressions
		 * @throws ExpressoInterpretationException If this dialog could not be interpreted
		 */
		void updateDialog(InterpretedExpressoEnv expressoEnv) throws ExpressoInterpretationException;

		/** @return The instantiated dialog */
		D create();
	}

	@Override
	QuickDialog copy(ExElement parent);
}
