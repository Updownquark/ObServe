package org.observe.quick.base;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.InterpretedModelSet;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.quick.style.StyleQIS;
import org.qommons.config.AbstractQIS;
import org.qommons.config.QonfigInterpretationException;

public interface QuickLayout {
	public abstract class Def<L extends QuickLayout> {
		private StyleQIS theStyleSession;
		private ExpressoQIS theExpressoSession;

		public Def(AbstractQIS<?> session) throws QonfigInterpretationException {
			update(session);
		}

		public StyleQIS getStyleSession() {
			return theStyleSession;
		}

		public ExpressoQIS getExpressoSession() {
			return theExpressoSession;
		}

		public void update(AbstractQIS<?> session) throws QonfigInterpretationException {
			theStyleSession = session.as(StyleQIS.class);
			theExpressoSession = session.as(ExpressoQIS.class);
		}

		public abstract Interpreted interpret(InterpretedModelSet models) throws ExpressoInterpretationException;
	}

	public abstract class Interpreted<L extends QuickLayout> {
		private final Def<L> theDefinition;

		public Interpreted(Def<L> definition) throws ExpressoInterpretationException {
			theDefinition = definition;
		}

		public Def<L> getDefinition() {
			return theDefinition;
		}

		public abstract void update(InterpretedModelSet models) throws ExpressoInterpretationException;

		public abstract QuickLayout create(ModelSetInstance models) throws ModelInstantiationException;
	}

	Interpreted<?> getIntepreted();

	void update(ModelSetInstance models) throws ModelInstantiationException;
}
