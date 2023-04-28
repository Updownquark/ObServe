package org.observe.quick;

import java.lang.reflect.Type;

import org.observe.expresso.ClassView;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.InterpretedModelSet;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.quick.style.QuickStyleSheet;
import org.observe.util.TypeTokens;
import org.qommons.config.AbstractQIS;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;

public class QuickDocument2 extends QuickElement.Abstract {
	public static class Def extends QuickElement.Def.Abstract<QuickDocument2> {
		private QuickHeadSection.Def theHead;
		private QuickWidget.Def<?> theBody;

		public Def(QuickElement.Def<?> parent, QonfigElement element) {
			super(parent, element);
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
			theHead = QuickElement.useOrReplace(QuickHeadSection.Def.class, theHead, session, "head");
			if(theHead!=null)
				getExpressoSession()
				.setExpressoEnv(getExpressoSession().getExpressoEnv().with(theHead.getModels(), theHead.getClassView()));
			theBody = QuickElement.useOrReplace(QuickWidget.Def.class, theBody, session, "body");
			return this;
		}

		public Interpreted interpret(QuickElement.Interpreted<?> parent) throws ExpressoInterpretationException {
			return new Interpreted(this, parent);
		}
	}

	public static class Interpreted extends QuickElement.Interpreted.Abstract<QuickDocument2> {
		private QuickHeadSection theHead;
		private QuickWidget.Interpreted<?> theBody;

		public Interpreted(Def def, QuickElement.Interpreted<?> parent) {
			super(def, parent);
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

		@Override
		protected Interpreted update(InterpretedModelSet models) throws ExpressoInterpretationException {
			super.update(models);

			if (getDefinition().getHead() == null)
				theHead = null;
			else if (theHead == null || theHead.getDefinition() != getDefinition().getHead())
				theHead=getDefinition().getHead().interpret(this);
			if (theHead != null)
				theHead.update(models);

			if (theBody == null || theBody.getDefinition() != getDefinition().getBody())
				theBody = getDefinition().getBody().interpret(null);
			theBody.update(models, new QuickWidget.QuickInterpretationCache());
			return this;
		}

		public QuickDocument2 create() {
			return new QuickDocument2(this);
		}
	}

	public static class QuickHeadSection extends QuickElement.Interpreted.Abstract<QuickElement> {
		private final ClassView theClassView;
		private final InterpretedModelSet theModels;
		private final QuickStyleSheet theStyleSheet;

		public QuickHeadSection(Def def, QuickDocument2.Interpreted document, ClassView classView, InterpretedModelSet models,
			QuickStyleSheet styleSheet) {
			super(def, document);
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
			private ClassView theClassView;
			private ObservableModelSet.Built theModels;
			private QuickStyleSheet theStyleSheet;

			public Def(QuickDocument2.Def parent, QonfigElement element) throws QonfigInterpretationException {
				super(parent, element);
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

			@Override
			public Def update(AbstractQIS<?> session) throws QonfigInterpretationException {
				super.update(session);
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
				getExpressoSession().setExpressoEnv(getExpressoSession().getExpressoEnv().with(model, cv));
				theStyleSheet = getStyleSession().getStyleSheet();
				return this;
			}

			public QuickHeadSection interpret(QuickDocument2.Interpreted document) throws ExpressoInterpretationException {
				return new QuickHeadSection(this, document, getClassView(), getModels().interpret(), theStyleSheet);
			}
		}
	}

	private QuickWidget theBody;

	public QuickDocument2(Interpreted interpreted) {
		super(interpreted, null);
	}

	@Override
	public Interpreted getInterpreted() {
		return (Interpreted) super.getInterpreted();
	}

	public QuickWidget getBody() {
		return theBody;
	}

	@Override
	protected QuickDocument2 update(ModelSetInstance models) throws ModelInstantiationException {
		super.update(models);
		if (theBody == null)
			theBody = getInterpreted().getBody().create(null);
		theBody.update(getModels(), new QuickWidget.QuickInstantiationCache());
		return this;
	}
}
