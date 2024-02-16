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
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

/**
 * A container with a single content widget. The scroll pane can display scroll bars that allow a very large contained widget to be
 * contained in a smaller container, with only a section of the content displayed at a time.
 */
public class QuickScrollPane extends QuickContainer.Abstract<QuickWidget> {
	/** The XML name of this element */
	public static final String SCROLL = "scroll";

	/** {@link QuickScrollPane} definition */
	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = SCROLL,
		interpretation = Interpreted.class,
		instance = QuickScrollPane.class)
	public static class Def extends QuickContainer.Def.Abstract<QuickScrollPane, QuickWidget> {
		private QuickWidget.Def<?> theRowHeader;
		private QuickWidget.Def<?> theColumnHeader;

		/**
		 * @param parent The parent element of the widget
		 * @param type The Qonfig type of the widget
		 */
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		/** @return The row header for the scroll pane, displayed to the left of the content and scrolled vertically with it */
		@QonfigChildGetter("row-header")
		public QuickWidget.Def<?> getRowHeader() {
			return theRowHeader;
		}

		/** @return The column header for the scroll pane, displayed above the content and scrolled horizontally with it */
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

	/** {@link QuickScrollPane} interpretation */
	public static class Interpreted extends QuickContainer.Interpreted.Abstract<QuickScrollPane, QuickWidget> {
		private QuickWidget.Interpreted<?> theRowHeader;
		private QuickWidget.Interpreted<?> theColumnHeader;

		/**
		 * @param definition The definition to interpret
		 * @param parent The parent element for the widget
		 */
		protected Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		/** @return The row header for the scroll pane, displayed to the left of the content and scrolled vertically with it */
		public QuickWidget.Interpreted<?> getRowHeader() {
			return theRowHeader;
		}

		/** @return The column header for the scroll pane, displayed above the content and scrolled horizontally with it */
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

	/** @param id The element ID for this widget */
	protected QuickScrollPane(Object id) {
		super(id);
	}

	/** @return The row header for the scroll pane, displayed to the left of the content and scrolled vertically with it */
	public QuickWidget getRowHeader() {
		return theRowHeader;
	}

	/** @return The column header for the scroll pane, displayed above the content and scrolled horizontally with it */
	public QuickWidget getColumnHeader() {
		return theColumnHeader;
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
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
	public void instantiated() throws ModelInstantiationException {
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
