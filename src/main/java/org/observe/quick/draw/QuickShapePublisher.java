package org.observe.quick.draw;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.qonfig.ExElement;

public interface QuickShapePublisher extends ExElement {
	public interface Def<P extends QuickShapePublisher> extends ExElement.Def<P> {
		Interpreted<? extends P> interpret(ExElement.Interpreted<?> parent);
	}

	public interface Interpreted<P extends QuickShapePublisher> extends ExElement.Interpreted<P> {
		/**
		 * Populates and updates this interpretation. Must be called once after being produced by the {@link #getDefinition() definition}.
		 *
		 * @throws ExpressoInterpretationException If any models could not be interpreted from their expressions in this widget or its
		 *         content
		 */
		void updateElement() throws ExpressoInterpretationException;

		P create();
	}

	@Override
	QuickShapePublisher copy(ExElement parent);
}
