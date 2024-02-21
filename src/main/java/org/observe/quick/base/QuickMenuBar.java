package org.observe.quick.base;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.observe.collect.ObservableCollection;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigChildGetter;
import org.qommons.collect.CollectionUtils;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

/** A menu bar along the top of the widget containing menus to control the application */
public class QuickMenuBar extends ExElement.Abstract {
	/** The XML name of this element */
	public static final String MENU_BAR = "menu-bar";

	/** {@link QuickMenuBar} definition */
	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = MENU_BAR,
		interpretation = Interpreted.class,
		instance = QuickMenuBar.class)
	public static class Def extends ExElement.Def.Abstract<QuickMenuBar> {
		private final List<QuickMenu.Def<?>> theMenus;

		/**
		 * @param parent The parent element of the menu bar
		 * @param qonfigType The Qonfig type of the menu bar
		 */
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
			theMenus = new ArrayList<>();
		}

		/** @return The menus in this menu bar */
		@QonfigChildGetter("menu")
		public List<QuickMenu.Def<?>> getMenus() {
			return Collections.unmodifiableList(theMenus);
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);

			syncChildren(QuickMenu.Def.class, theMenus, session.forChildren("menu"));
		}

		/**
		 * @param parent The parent for the interpreted menu bar
		 * @return The interpreted menu bar
		 */
		public Interpreted interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}
	}

	/** {@link QuickMenuBar} interpretation */
	public static class Interpreted extends ExElement.Interpreted.Abstract<QuickMenuBar> {
		private final List<QuickMenu.Interpreted<?, ?>> theMenus;

		/**
		 * @param definition The definition to interpret
		 * @param parent The parent element for the menu bar
		 */
		protected Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
			theMenus = new ArrayList<>();
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		/** @return The menus in this menu bar */
		public List<QuickMenu.Interpreted<?, ?>> getMenus() {
			return Collections.unmodifiableList(theMenus);
		}

		/**
		 * Initializes or updates the menu bar
		 *
		 * @param env The expresso environment for interpreting expressions
		 * @throws ExpressoInterpretationException If this menu bar could not be interpreted
		 */
		public void updateMenuBar(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			update(env);
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv expressoEnv) throws ExpressoInterpretationException {
			super.doUpdate(expressoEnv);

			syncChildren(getDefinition().getMenus(), theMenus, def -> def.interpret(this), QuickMenu.Interpreted::updateElement);
		}

		/** @return The menu bar */
		public QuickMenuBar create() {
			return new QuickMenuBar(getIdentity());
		}
	}

	private ObservableCollection<QuickMenu<?>> theMenus;

	/** @param id The element ID for this menu bar */
	protected QuickMenuBar(Object id) {
		super(id);
		theMenus = ObservableCollection.<QuickMenu<?>> build().build();
	}

	/** @return The menus in this menu bar */
	public ObservableCollection<QuickMenu<?>> getMenus() {
		return theMenus.flow().unmodifiable(false).collect();
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);

		Interpreted myInterpreted = (Interpreted) interpreted;
		CollectionUtils.synchronize(theMenus, myInterpreted.getMenus(), (inst, interp) -> inst.getIdentity() == interp.getIdentity())//
		.<ModelInstantiationException> simpleX(interp -> interp.create())//
		.onLeft(el -> el.getLeftValue().destroy())//
		.onRightX(el -> el.getLeftValue().update(el.getRightValue(), this))//
		.onCommonX(el -> el.getLeftValue().update(el.getRightValue(), this))//
		.rightOrder()//
		.adjust();
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();

		for (QuickMenu<?> menuItem : theMenus)
			menuItem.instantiated();
	}

	@Override
	protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		super.doInstantiate(myModels);

		for (QuickMenu<?> menuItem : theMenus)
			menuItem.instantiate(myModels);
	}

	@Override
	public QuickMenuBar copy(ExElement parent) {
		QuickMenuBar copy = (QuickMenuBar) super.copy(parent);

		copy.theMenus = ObservableCollection.<QuickMenu<?>> build().build();
		for (QuickMenu<?> menuItem : theMenus)
			copy.theMenus.add(menuItem.copy(copy));

		return copy;
	}
}
