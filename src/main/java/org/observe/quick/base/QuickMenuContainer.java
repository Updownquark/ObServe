package org.observe.quick.base;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ExAddOn;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigChildGetter;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;

/** A container that may have a menu bar along the top */
public class QuickMenuContainer extends ExAddOn.Abstract<ExElement> {
	/** The XML name of this add-on */
	public static final String MENU_CONTAINER = "menu-container";

	/** {@link QuickMenuContainer} definition */
	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = MENU_CONTAINER,
		interpretation = Interpreted.class,
		instance = QuickMenuContainer.class)
	public static class Def extends ExAddOn.Def.Abstract<ExElement, QuickMenuContainer> {
		private QuickMenuBar.Def theMenuBar;

		/**
		 * @param type The Qonfig type of this add-on
		 * @param element The container
		 */
		public Def(QonfigAddOn type, ExElement.Def<? extends ExElement> element) {
			super(type, element);
		}

		/** @return The menu bar to display above the container */
		@QonfigChildGetter("menu-bar")
		public QuickMenuBar.Def getMenuBar() {
			return theMenuBar;
		}

		@Override
		public void update(ExpressoQIS session, ExElement.Def<?> element) throws QonfigInterpretationException {
			super.update(session, element);
		}

		/* Due to the weirdness of <quick-document> inheriting models from its child <head> section, the environment argument to the
		 * update(IEE) method called on this add-on does not know of the model data in the head section.
		 * So we need to do our work in the postUpdate() method. */
		@Override
		public void postUpdate(ExpressoQIS session, ExElement.Def<?> addOnElement) throws QonfigInterpretationException {
			super.postUpdate(session, addOnElement);
			theMenuBar = addOnElement.syncChild(QuickMenuBar.Def.class, theMenuBar, session, "menu-bar");
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<? extends ExElement> element) {
			return new Interpreted(this, element);
		}
	}

	/** {@link QuickMenuContainer} interpretation */
	public static class Interpreted extends ExAddOn.Interpreted.Abstract<ExElement, QuickMenuContainer> {
		private QuickMenuBar.Interpreted theMenuBar;

		/**
		 * @param definition The definition to interpret
		 * @param element The container
		 */
		protected Interpreted(Def definition, ExElement.Interpreted<? extends ExElement> element) {
			super(definition, element);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		/** @return The menu bar to display above the container */
		public QuickMenuBar.Interpreted getMenuBar() {
			return theMenuBar;
		}

		@Override
		public void postUpdate(ExElement.Interpreted<?> element) throws ExpressoInterpretationException {
			super.postUpdate(element);

			theMenuBar = getElement().syncChild(getDefinition().getMenuBar(), theMenuBar, def -> def.interpret(getElement()),
				(b, bEnv) -> b.updateMenuBar(bEnv));
		}

		@Override
		public Class<QuickMenuContainer> getInstanceType() {
			return QuickMenuContainer.class;
		}

		@Override
		public QuickMenuContainer create(ExElement element) {
			return new QuickMenuContainer(element);
		}
	}

	private QuickMenuBar theMenuBar;

	/** @param element The container */
	protected QuickMenuContainer(ExElement element) {
		super(element);
	}

	/** @return The menu bar to display above the container */
	public QuickMenuBar getMenuBar() {
		return theMenuBar;
	}

	@Override
	public Class<? extends ExAddOn.Interpreted<?, ?>> getInterpretationType() {
		return Interpreted.class;
	}

	@Override
	public void update(ExAddOn.Interpreted<?, ?> interpreted, ExElement element) throws ModelInstantiationException {
		super.update(interpreted, element);

		Interpreted myInterpreted = (Interpreted) interpreted;

		if (theMenuBar != null
			&& (myInterpreted.getMenuBar() == null || theMenuBar.getIdentity() != myInterpreted.getMenuBar().getIdentity())) {
			theMenuBar.destroy();
			theMenuBar = null;
		}
		if (theMenuBar == null && myInterpreted.getMenuBar() != null)
			theMenuBar = myInterpreted.getMenuBar().create();
		if (theMenuBar != null)
			theMenuBar.update(myInterpreted.getMenuBar(), getElement());
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();

		if (theMenuBar != null)
			theMenuBar.instantiated();
	}

	@Override
	public void instantiate(ModelSetInstance models) throws ModelInstantiationException {
		super.instantiate(models);

		if (theMenuBar != null)
			theMenuBar.instantiate(models);
	}

	@Override
	public QuickMenuContainer copy(ExElement element) {
		QuickMenuContainer copy = (QuickMenuContainer) super.copy(element);

		if (theMenuBar != null)
			copy.theMenuBar = theMenuBar.copy(element);

		return copy;
	}
}
