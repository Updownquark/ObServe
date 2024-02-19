package org.observe.expresso.qonfig;

import org.observe.expresso.CompiledExpressoEnv;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.ModelInstantiator;
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

		/** @return The expresso environment of the fulfilled content */
		CompiledExpressoEnv getExternalExpressoEnv();

		/** @param env The expresso environment of the fulfilled content */
		void setExternalExpressoEnv(CompiledExpressoEnv env);

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

		/** @return The expresso environment of the fulfilled content */
		InterpretedExpressoEnv getExternalExpressoEnv();

		/** @param env The expresso environment of the fulfilled content */
		void setExternalExpressoEnv(InterpretedExpressoEnv env);

		/**
		 * Initializes or updates this promise
		 *
		 * @param env The expresso environment to interpret the promise in
		 * @param content The fulfilled content for the promise
		 * @throws ExpressoInterpretationException If the promise could not be updated
		 */
		void update(InterpretedExpressoEnv env, ExElement.Interpreted<?> content) throws ExpressoInterpretationException;

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

	/** @return The expresso models of the fulfilled content */
	ModelInstantiator getExtModels();

	@Override
	QonfigPromise copy(ExElement parent);
}
