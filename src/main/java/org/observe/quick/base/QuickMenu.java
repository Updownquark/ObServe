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

/**
 * A menu in a menu bar, or a sub-menu in another menu
 *
 * @param <T> The type of value representing the menu
 */
public class QuickMenu<T> extends QuickAbstractMenuItem<T> {
	/** The XML name of this element */
	public static final String MENU = "menu";

	/**
	 * {@link QuickMenu} definition
	 *
	 * @param <M> The sub-type of menu to create
	 */
	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = MENU,
		interpretation = Interpreted.class,
		instance = QuickMenu.class)
	public static class Def<M extends QuickMenu<?>> extends QuickAbstractMenuItem.Def<M> {
		private final List<QuickAbstractMenuItem.Def<?>> theMenuItems;

		/**
		 * @param parent The parent element of the menu
		 * @param type The Qonfig type of the menu
		 */
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
			theMenuItems = new ArrayList<>();
		}

		/** @return The menu items in this menu */
		@QonfigChildGetter("menu-item")
		public List<QuickAbstractMenuItem.Def<?>> getMenuItems() {
			return Collections.unmodifiableList(theMenuItems);
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);

			syncChildren(QuickAbstractMenuItem.Def.class, theMenuItems, session.forChildren("menu-item"));
		}

		@Override
		public Interpreted<?, ? extends M> interpret(ExElement.Interpreted<?> parent) {
			return (Interpreted<?, ? extends M>) new Interpreted<>((Def<QuickMenu<Object>>) this, parent);
		}
	}

	/**
	 * {@link QuickMenu} definition
	 *
	 * @param <T> The type of value representing the menu
	 * @param <M> The sub-type of menu to create
	 */
	public static class Interpreted<T, M extends QuickMenu<T>> extends QuickAbstractMenuItem.Interpreted<T, M> {
		private final List<QuickAbstractMenuItem.Interpreted<?, ?>> theMenuItems;

		/**
		 * @param definition The definition to interpret
		 * @param parent The parent element for the menu
		 */
		protected Interpreted(Def<? super M> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
			theMenuItems = new ArrayList<>();
		}

		@Override
		public Def<? super M> getDefinition() {
			return (Def<? super M>) super.getDefinition();
		}

		/** @return The menu items in this menu */
		public List<QuickAbstractMenuItem.Interpreted<?, ?>> getMenuItems() {
			return Collections.unmodifiableList(theMenuItems);
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			super.doUpdate(env);

			syncChildren(getDefinition().getMenuItems(), theMenuItems, def -> def.interpret(this),
				QuickAbstractMenuItem.Interpreted::updateElement);
		}

		@Override
		public M create() {
			return (M) new QuickMenu<>(getIdentity());
		}
	}

	private ObservableCollection<QuickAbstractMenuItem<?>> theMenuItems;

	/** @param id The element ID for this menu */
	protected QuickMenu(Object id) {
		super(id);
		theMenuItems = ObservableCollection.<QuickAbstractMenuItem<?>> build().build();
	}

	/** @return The menu items in this menu */
	public ObservableCollection<QuickAbstractMenuItem<?>> getMenuItems() {
		return theMenuItems.flow().unmodifiable(false).collect();
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);

		Interpreted<T, ?> myInterpreted = (Interpreted<T, ?>) interpreted;
		CollectionUtils
		.synchronize(theMenuItems, myInterpreted.getMenuItems(), (inst, interp) -> inst.getIdentity() == interp.getIdentity())//
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

		for (QuickAbstractMenuItem<?> menuItem : theMenuItems)
			menuItem.instantiated();
	}

	@Override
	protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		super.doInstantiate(myModels);

		for (QuickAbstractMenuItem<?> menuItem : theMenuItems)
			menuItem.instantiate(myModels);
	}

	@Override
	public QuickMenu<T> copy(ExElement parent) {
		QuickMenu<T> copy = (QuickMenu<T>) super.copy(parent);

		copy.theMenuItems = ObservableCollection.<QuickAbstractMenuItem<?>> build().build();
		for (QuickAbstractMenuItem<?> menuItem : theMenuItems)
			copy.theMenuItems.add(menuItem.copy(copy));

		return copy;
	}
}
