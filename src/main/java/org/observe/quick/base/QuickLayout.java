package org.observe.quick.base;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.quick.QuickCompiledStructure;
import org.observe.quick.style.StyleQIS;
import org.qommons.config.AbstractQIS;
import org.qommons.config.QonfigInterpretationException;

public interface QuickLayout {
	public abstract class Def implements QuickCompiledStructure {
		private StyleQIS theStyleSession;
		private ExpressoQIS theExpressoSession;

		public Def(AbstractQIS<?> session) throws QonfigInterpretationException {
			update(session);
		}

		@Override
		public StyleQIS getStyleSession() {
			return theStyleSession;
		}

		@Override
		public ExpressoQIS getExpressoSession() {
			return theExpressoSession;
		}

		public void update(AbstractQIS<?> session) throws QonfigInterpretationException {
			theStyleSession = session.as(StyleQIS.class);
			theExpressoSession = session.as(ExpressoQIS.class);
		}

		public abstract Interpreted interpret() throws ExpressoInterpretationException;
	}

	public abstract class Interpreted {
		private final Def theDefinition;

		public Interpreted(Def definition) throws ExpressoInterpretationException {
			theDefinition = definition;
			update();
		}

		public Def getDefinition() {
			return theDefinition;
		}

		public abstract void update() throws ExpressoInterpretationException;

		public abstract QuickLayout create(ModelSetInstance models) throws ModelInstantiationException;
	}

	Interpreted getIntepreted();

	void update(ModelSetInstance models) throws ModelInstantiationException;
}
