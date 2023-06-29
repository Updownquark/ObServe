package org.observe.quick.base;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.quick.QuickContainer;
import org.observe.quick.QuickWidget;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public class QuickScrollPane extends QuickContainer.Abstract<QuickWidget> {
	public static final String SCROLL = "scroll";

	public static class Def extends QuickContainer.Def.Abstract<QuickScrollPane, QuickWidget> {
		private QuickWidget.Def<?> theRowHeader;
		private QuickWidget.Def<?> theColumnHeader;

		public Def(ExElement.Def parent, QonfigElement element) {
			super(parent, element);
		}

		public QuickWidget.Def<?> getRowHeader() {
			return theRowHeader;
		}

		public QuickWidget.Def<?> getColumnHeader() {
			return theColumnHeader;
		}

		@Override
		public void update(ExpressoQIS session) throws QonfigInterpretationException {
			ExElement.checkElement(session.getFocusType(), QuickBaseInterpretation.NAME, QuickBaseInterpretation.VERSION, SCROLL);
			super.update(session.asElement(session.getFocusType().getSuperElement().getSuperElement())); // Skip singleton-container
			theRowHeader = ExElement.useOrReplace(QuickWidget.Def.class, theRowHeader, session, "row-header");
			theColumnHeader = ExElement.useOrReplace(QuickWidget.Def.class, theColumnHeader, session, "column-header");
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

		@Override
		public TypeToken<? extends QuickScrollPane> getWidgetType() {
			return TypeTokens.get().of(QuickScrollPane.class);
		}

		public QuickWidget.Interpreted<?> getRowHeader() {
			return theRowHeader;
		}

		public QuickWidget.Interpreted<?> getColumnHeader() {
			return theColumnHeader;
		}

		@Override
		public void update(QuickInterpretationCache cache) throws ExpressoInterpretationException {
			super.update(cache);

			if (theRowHeader != null && theRowHeader.getDefinition() != getDefinition().getRowHeader()) {
				theRowHeader.destroy();
				theRowHeader = null;
			}
			if (getDefinition().getRowHeader() != null) {
				if (theRowHeader == null)
					theRowHeader = getDefinition().getRowHeader().interpret(this);
				theRowHeader.update(cache);
			}

			if (theColumnHeader != null && theColumnHeader.getDefinition() != getDefinition().getColumnHeader()) {
				theColumnHeader.destroy();
				theColumnHeader = null;
			}
			if (getDefinition().getColumnHeader() != null) {
				if (theColumnHeader == null)
					theColumnHeader = getDefinition().getColumnHeader().interpret(this);
				theColumnHeader.update(cache);
			}
		}

		@Override
		public QuickScrollPane create(ExElement parent) {
			return new QuickScrollPane(this, parent);
		}
	}

	private QuickWidget theRowHeader;
	private QuickWidget theColumnHeader;

	public QuickScrollPane(Interpreted interpreted, ExElement parent) {
		super(interpreted, parent);
	}

	public QuickWidget getRowHeader() {
		return theRowHeader;
	}

	public QuickWidget getColumnHeader() {
		return theColumnHeader;
	}

	@Override
	protected void updateModel(ExElement.Interpreted<?> interpreted, ModelSetInstance myModels) throws ModelInstantiationException {
		super.updateModel(interpreted, myModels);
		Interpreted myInterpreted = (Interpreted) interpreted;

		if (myInterpreted.getRowHeader() != null) {
			if (theRowHeader == null || theRowHeader.getIdentity() != myInterpreted.getRowHeader().getDefinition().getIdentity())
				theRowHeader = myInterpreted.getRowHeader().create(this);
			theRowHeader.update(myInterpreted.getRowHeader(), myModels);
		}

		if (myInterpreted.getColumnHeader() != null) {
			if (theColumnHeader == null || theColumnHeader.getIdentity() != myInterpreted.getColumnHeader().getDefinition().getIdentity())
				theColumnHeader = myInterpreted.getColumnHeader().create(this);
			theColumnHeader.update(myInterpreted.getColumnHeader(), myModels);
		}
	}
}
