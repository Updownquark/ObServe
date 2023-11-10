package org.observe.expresso.qonfig;

import org.observe.expresso.CompiledExpressoEnv;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ObservableModelSet.ModelInstantiator;
import org.qommons.config.QonfigInterpretationException;

public interface QonfigPromise extends ExElement {
	public interface Def<P extends QonfigPromise> extends ExElement.Def<P> {
		ExElement.Def<?> getFulfilledContent();

		void update(ExpressoQIS session, ExElement.Def<?> content) throws QonfigInterpretationException;

		CompiledExpressoEnv getExternalExpressoEnv();

		void setExternalExpressoEnv(CompiledExpressoEnv env);

		Interpreted<? extends P> interpret();
	}

	public interface Interpreted<P extends QonfigPromise> extends ExElement.Interpreted<P> {
		ExElement.Interpreted<?> getFulfilledContent();

		InterpretedExpressoEnv getExternalExpressoEnv();

		void setExternalExpressoEnv(InterpretedExpressoEnv env);

		void update(InterpretedExpressoEnv env, ExElement.Interpreted<?> content) throws ExpressoInterpretationException;

		QonfigPromise create(ExElement content);
	}

	void update(Interpreted<?> interpreted);

	ModelInstantiator getExtModels();

	@Override
	QonfigPromise copy(ExElement parent);
}
