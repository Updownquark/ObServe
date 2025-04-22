package org.observe.expresso.qonfig;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;

/** An {@link ExElement} representing a {@link QonfigElement#getPromise() promise} in Qonfig */
public interface QonfigPromise extends ExElement {
	/**
	 * {@link QonfigPromise} definition
	 *
	 * @param <P> The type of promise to create
	 */
	public interface Def<P extends QonfigPromise> extends ExElement.Def<P> {
		/** @return The content fulfilled by this promise */
		ExElement.Def<?> getFulfilledContent();

		/**
		 * Initializes or updates this promise
		 *
		 * @param session The expresso session to interpret the promise in
		 * @param content The fulfilled content for the promise
		 * @throws QonfigInterpretationException If the promise could not be updated
		 */
		void update(ExpressoQIS session, ExElement.Def<?> content) throws QonfigInterpretationException;

		/** @return The interpreted promise */
		Interpreted<? extends P> interpret();
	}

	/**
	 * {@link QonfigPromise} interpretation
	 *
	 * @param <P> The type of promise to create
	 */
	public interface Interpreted<P extends QonfigPromise> extends ExElement.Interpreted<P> {
		/** @return The content fulfilled by this promise */
		ExElement.Interpreted<?> getFulfilledContent();

		/**
		 * Initializes or updates this promise
		 *
		 * @param env The expresso environment to interpret the promise in
		 * @param content The fulfilled content for the promise
		 * @throws ExpressoInterpretationException If the promise could not be updated
		 */
		void update(ExElement.Interpreted<?> content) throws ExpressoInterpretationException;

		/**
		 * @param content The fulfilled content for the promise
		 * @return The instantiated promise
		 */
		QonfigPromise create(ExElement content);
	}

	/**
	 * Initializes or updates this promise
	 *
	 * @param interpreted The interpreted promise that this is an instantiation of
	 * @throws ModelInstantiationException If the promise could not be instantiated
	 */
	void update(Interpreted<?> interpreted) throws ModelInstantiationException;

	@Override
	QonfigPromise copy(ExElement parent);
}
