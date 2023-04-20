package org.observe.quick;

import org.observe.expresso.ClassView;
import org.observe.expresso.Expresso;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.InterpretedModelSet;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.quick.style.QuickStyleSheet;
import org.observe.quick.style.StyleQIS;

public class QuickDocument2 {
	public static class Def implements QuickCompiledStructure {
		private StyleQIS theStyleSession;
		private ExpressoQIS theExpressoSession;
		private final QuickHeadSection.Def theHead;
		private final QuickWidget.Def<?> theBody;

		public Def(StyleQIS styleSession, ExpressoQIS expressoSession, QuickHeadSection.Def head, QuickWidget.Def<?> body) {
			theStyleSession = styleSession;
			theHead = head;
			theBody = body;
		}

		@Override
		public StyleQIS getStyleSession() {
			return theStyleSession;
		}

		@Override
		public ExpressoQIS getExpressoSession() {
			return theExpressoSession;
		}

		public QuickHeadSection.Def getHead() {
			return theHead;
		}

		public QuickWidget.Def<?> getBody() {
			return theBody;
		}

		public Interpreted interpret() throws ExpressoInterpretationException {
			return new Interpreted(theHead.interpret(), theBody.interpret(null, new QuickWidget.QuickInterpretationCache()));
		}
	}

	public static class Interpreted {
		private final QuickHeadSection theHead;
		private final QuickWidget.Interpreted<?> theBody;

		public Interpreted(QuickHeadSection head, QuickWidget.Interpreted<?> body) {
			theHead = head;
			theBody = body;
		}

		public QuickHeadSection getHead() {
			return theHead;
		}

		public QuickWidget.Interpreted<?> getBody() {
			return theBody;
		}

		public QuickDocument2 create(ModelSetInstance models) throws ModelInstantiationException {
			return new QuickDocument2(this, models, theBody.create(null, models, new QuickWidget.QuickInstantiationCache()));
		}
	}

	public static class QuickHeadSection {
		private final ClassView theClassView;
		private final InterpretedModelSet theModels;
		private final QuickStyleSheet theStyleSheet;

		public QuickHeadSection(ClassView classView, InterpretedModelSet models, QuickStyleSheet styleSheet) {
			theClassView = classView;
			theModels = models;
			theStyleSheet = styleSheet;
		}

		public ClassView getClassView() {
			return theClassView;
		}

		public InterpretedModelSet getModels() {
			return theModels;
		}

		public QuickStyleSheet getStyleSheet() {
			return theStyleSheet;
		}

		public static class Def extends Expresso implements QuickCompiledStructure {
			private final StyleQIS theStyleSession;
			private final ExpressoQIS theExpressoSession;
			private final QuickStyleSheet theStyleSheet;

			public Def(StyleQIS styleSession, ExpressoQIS expressoSession, ClassView classView, ObservableModelSet.Built models,
				QuickStyleSheet styleSheet) {
				super(classView, models);
				theStyleSession = styleSession;
				theExpressoSession = expressoSession;
				theStyleSheet = styleSheet;
			}

			@Override
			public StyleQIS getStyleSession() {
				return theStyleSession;
			}

			@Override
			public ExpressoQIS getExpressoSession() {
				return theExpressoSession;
			}

			public QuickStyleSheet getStyleSheet() {
				return theStyleSheet;
			}

			public QuickHeadSection interpret() throws ExpressoInterpretationException {
				return new QuickHeadSection(getClassView(), getModels().interpret(), theStyleSheet);
			}
		}
	}

	private final Interpreted theInterpreted;
	private final ModelSetInstance theModels;
	private final QuickWidget theBody;

	public QuickDocument2(Interpreted interpreted, ModelSetInstance models, QuickWidget body) {
		theInterpreted = interpreted;
		theModels = models;
		theBody = body;
	}

	public Interpreted getInterpreted() {
		return theInterpreted;
	}

	public ModelSetInstance getModels() {
		return theModels;
	}

	public QuickWidget getBody() {
		return theBody;
	}
}
