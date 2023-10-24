package org.observe.quick.base;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigChildGetter;
import org.observe.quick.QuickContainer;
import org.observe.quick.QuickWidget;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public class QuickScrollPane extends QuickContainer.Abstract<QuickWidget> {
	public static final String SCROLL = "scroll";

	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = SCROLL,
		interpretation = Interpreted.class,
		instance = QuickScrollPane.class)
	public static class Def extends QuickContainer.Def.Abstract<QuickScrollPane, QuickWidget> {
		private QuickWidget.Def<?> theRowHeader;
		private QuickWidget.Def<?> theColumnHeader;

		public Def(ExElement.Def parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		@QonfigChildGetter("row-header")
		public QuickWidget.Def<?> getRowHeader() {
			return theRowHeader;
		}

		@QonfigChildGetter("column-header")
		public QuickWidget.Def<?> getColumnHeader() {
			return theColumnHeader;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session.asElement(session.getFocusType().getSuperElement().getSuperElement())); // Skip singleton-container
			theRowHeader = syncChild(QuickWidget.Def.class, theRowHeader, session, "row-header");
			theColumnHeader = syncChild(QuickWidget.Def.class, theColumnHeader, session, "column-header");
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}
	}

	public static class Interpreted extends QuickContainer.Interpreted.Abstract<QuickScrollPane, QuickWidget> {
		private QuickWidget.Interpreted<?> theRowHeader;
		private QuickWidget.Interpreted<?> theColumnHeader;

		public Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		public QuickWidget.Interpreted<?> getRowHeader() {
			return theRowHeader;
		}

		public QuickWidget.Interpreted<?> getColumnHeader() {
			return theColumnHeader;
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			super.doUpdate(env);

			theRowHeader = syncChild(getDefinition().getRowHeader(), theRowHeader, def -> def.interpret(this),
				(r, rEnv) -> r.updateElement(rEnv));
			theColumnHeader = syncChild(getDefinition().getColumnHeader(), theColumnHeader, def -> def.interpret(this),
				(r, rEnv) -> r.updateElement(rEnv));
		}

		@Override
		public QuickScrollPane create() {
			return new QuickScrollPane(getIdentity());
		}
	}

	private QuickWidget theRowHeader;
	private QuickWidget theColumnHeader;

	public QuickScrollPane(Object id) {
		super(id);
	}

	public QuickWidget getRowHeader() {
		return theRowHeader;
	}

	public QuickWidget getColumnHeader() {
		return theColumnHeader;
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) {
		super.doUpdate(interpreted);
		Interpreted myInterpreted = (Interpreted) interpreted;

		if (myInterpreted.getRowHeader() != null) {
			if (theRowHeader == null || theRowHeader.getIdentity() != myInterpreted.getRowHeader().getIdentity())
				theRowHeader = myInterpreted.getRowHeader().create();
			theRowHeader.update(myInterpreted.getRowHeader(), this);
		}

		if (myInterpreted.getColumnHeader() != null) {
			if (theColumnHeader == null || theColumnHeader.getIdentity() != myInterpreted.getColumnHeader().getIdentity())
				theColumnHeader = myInterpreted.getColumnHeader().create();
			theColumnHeader.update(myInterpreted.getColumnHeader(), this);
		}
	}

	@Override
	public void instantiated() {
		super.instantiated();
		if (theRowHeader != null)
			theRowHeader.instantiated();
		if (theColumnHeader != null)
			theColumnHeader.instantiated();
	}

	@Override
	protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		super.doInstantiate(myModels);
		if (theRowHeader != null)
			theRowHeader.instantiate(myModels);
		if (theColumnHeader != null)
			theColumnHeader.instantiate(myModels);
	}

	@Override
	public QuickScrollPane copy(ExElement parent) {
		QuickScrollPane copy = (QuickScrollPane) super.clone();

		if (theRowHeader != null)
			copy.theRowHeader = theRowHeader.copy(copy);
		if (theColumnHeader != null)
			copy.theColumnHeader = theColumnHeader.copy(copy);

		return copy;
	}
}
