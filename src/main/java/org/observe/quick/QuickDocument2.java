package org.observe.quick;

import java.lang.reflect.Type;

import org.observe.expresso.ClassView;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.InterpretedModelSet;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.quick.style.QuickStyleSheet;
import org.observe.util.TypeTokens;
import org.qommons.config.AbstractQIS;
import org.qommons.config.QonfigInterpretationException;

public class QuickDocument2 extends QuickElement.Abstract {
	public static class Def extends QuickElement.Def.Abstract<QuickDocument2> {
		private final QuickHeadSection.Def theHead;
		private final QuickWidget.Def<?> theBody;

		public Def(AbstractQIS<?> session) throws QonfigInterpretationException {
			super(session);
			theHead = session.interpretChildren("head", QuickHeadSection.Def.class).peekFirst();
			theBody = session.interpretChildren("body", QuickWidget.Def.class).peekFirst();
		}

		public QuickHeadSection.Def getHead() {
			return theHead;
		}

		public QuickWidget.Def<?> getBody() {
			return theBody;
		}

		@Override
		public Def update(AbstractQIS<?> session) throws QonfigInterpretationException {
			super.update(session);
			theBody.update(session.forChildren("body").getFirst());
			return this;
		}

		public Interpreted interpret() throws ExpressoInterpretationException {
			return new Interpreted(this, theHead.interpret(), theBody.interpret(null));
		}
	}

	public static class Interpreted extends QuickElement.Interpreted.Abstract<QuickDocument2> {
		private final QuickHeadSection theHead;
		private final QuickWidget.Interpreted<?> theBody;

		public Interpreted(Def def, QuickHeadSection head, QuickWidget.Interpreted<?> body) {
			super(def);
			theHead = head;
			theBody = body;
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		public QuickHeadSection getHead() {
			return theHead;
		}

		public QuickWidget.Interpreted<?> getBody() {
			return theBody;
		}

		public QuickDocument2 create(ModelSetInstance models) {
			return new QuickDocument2(this, models, theBody.create(null));
		}
	}

	public static class QuickHeadSection extends QuickElement.Interpreted.Abstract<QuickElement> {
		private final ClassView theClassView;
		private final InterpretedModelSet theModels;
		private final QuickStyleSheet theStyleSheet;

		public QuickHeadSection(Def def, ClassView classView, InterpretedModelSet models, QuickStyleSheet styleSheet) {
			super(def);
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

		public static class Def extends QuickElement.Def.Abstract<QuickElement> {
			private final ClassView theClassView;
			private final ObservableModelSet.Built theModels;
			private final QuickStyleSheet theStyleSheet;

			public Def(AbstractQIS<?> session) throws QonfigInterpretationException {
				super(session);
				ClassView cv = session.interpretChildren("imports", ClassView.class).peekFirst();
				if (cv == null) {
					ClassView defaultCV = ClassView.build().withWildcardImport("java.lang").build();
					TypeTokens.get().addClassRetriever(new TypeTokens.TypeRetriever() {
						@Override
						public Type getType(String typeName) {
							return defaultCV.getType(typeName);
						}
					});
					cv = defaultCV;
				}
				theClassView = cv;
				ObservableModelSet.Built model = session.interpretChildren("models", ObservableModelSet.Built.class).peekFirst();
				if (model == null)
					model = ObservableModelSet.build("models", ObservableModelSet.JAVA_NAME_CHECKER).build();
				theModels = model;
				theStyleSheet = getStyleSession().getStyleSheet();
			}

			public ClassView getClassView() {
				return theClassView;
			}

			public ObservableModelSet.Built getModels() {
				return theModels;
			}

			public QuickStyleSheet getStyleSheet() {
				return theStyleSheet;
			}

			public QuickHeadSection interpret() throws ExpressoInterpretationException {
				return new QuickHeadSection(this, getClassView(), getModels().interpret(), theStyleSheet);
			}
		}
	}

	private final QuickWidget theBody;

	public QuickDocument2(Interpreted interpreted, ModelSetInstance models, QuickWidget body) {
		super(interpreted, null);
		theBody = body;
	}

	@Override
	public Interpreted getInterpreted() {
		return (Interpreted) super.getInterpreted();
	}

	public QuickWidget getBody() {
		return theBody;
	}
}
