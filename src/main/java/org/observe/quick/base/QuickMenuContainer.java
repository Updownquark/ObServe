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

public class QuickMenuContainer extends ExAddOn.Abstract<ExElement> {
	public static final String MENU_CONTAINER = "menu-container";

	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = MENU_CONTAINER,
		interpretation = Interpreted.class,
		instance = QuickMenuContainer.class)
	public static class Def extends ExAddOn.Def.Abstract<ExElement, QuickMenuContainer> {
		private QuickMenuBar.Def theMenuBar;

		public Def(QonfigAddOn type, ExElement.Def<? extends ExElement> element) {
			super(type, element);
		}

		@QonfigChildGetter("menu-bar")
		public QuickMenuBar.Def getMenuBar() {
			return theMenuBar;
		}

		@Override
		public void update(ExpressoQIS session, ExElement.Def<? extends ExElement> element) throws QonfigInterpretationException {
			super.update(session, element);
		}

		/* Due to the weirdness of <quick-document> inheriting models from its child <head> section, the environment argument to the
		 * update(IEE) method called on this add-on does not know of the model data in the head section.
		 * So we need to do our work in the postUpdate() method. */
		@Override
		public void postUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.postUpdate(session);
			theMenuBar = ExElement.useOrReplace(QuickMenuBar.Def.class, theMenuBar, session, "menu-bar");
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<? extends ExElement> element) {
			return new Interpreted(this, element);
		}
	}

	public static class Interpreted extends ExAddOn.Interpreted.Abstract<ExElement, QuickMenuContainer> {
		private QuickMenuBar.Interpreted theMenuBar;

		public Interpreted(Def definition, ExElement.Interpreted<? extends ExElement> element) {
			super(definition, element);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		public QuickMenuBar.Interpreted getMenuBar() {
			return theMenuBar;
		}

		@Override
		public void postUpdate() throws ExpressoInterpretationException {
			super.postUpdate();

			if (theMenuBar != null
				&& (getDefinition().getMenuBar() == null || theMenuBar.getIdentity() != getDefinition().getMenuBar().getIdentity())) {
				theMenuBar.destroy();
				theMenuBar = null;
			}
			if (theMenuBar == null && getDefinition().getMenuBar() != null)
				theMenuBar = getDefinition().getMenuBar().interpret(getElement());
			if (theMenuBar != null)
				theMenuBar.updateMenuBar(getElement().getExpressoEnv());
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

	public QuickMenuContainer(ExElement element) {
		super(element);
	}

	public QuickMenuBar getMenuBar() {
		return theMenuBar;
	}

	@Override
	public Class<? extends ExAddOn.Interpreted<?, ?>> getInterpretationType() {
		return Interpreted.class;
	}

	@Override
	public void update(ExAddOn.Interpreted<?, ?> interpreted) {
		super.update(interpreted);

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
	public void instantiated() {
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
