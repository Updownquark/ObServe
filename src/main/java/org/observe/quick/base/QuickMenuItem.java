package org.observe.quick.base;

import org.observe.ObservableAction;
import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

/**
 * A simple menu item in a menu that performs an action
 *
 * @param <T> The type of alue to represent
 */
public class QuickMenuItem<T> extends QuickAbstractMenuItem<T> {
	/** The XML name of this element */
	public static final String MENU_ITEM = "menu-item";

	/**
	 * {@link QuickMenuItem} definition
	 *
	 * @param <M> The sub-type of menu item to create
	 */
	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = MENU_ITEM,
		interpretation = Interpreted.class,
		instance = QuickMenuItem.class)
	public static class Def<M extends QuickMenuItem<?>> extends QuickAbstractMenuItem.Def<M> {
		private CompiledExpression theAction;

		/**
		 * @param parent The parent element of the menu item
		 * @param type The Qonfig type of the menu item
		 */
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		/** @return The action to perform when the menu item is selected */
		@QonfigAttributeGetter("action")
		public CompiledExpression getAction() {
			return theAction;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);

			theAction = getAttributeExpression("action", session);
		}

		@Override
		public Interpreted<?, ? extends M> interpret(org.observe.expresso.qonfig.ExElement.Interpreted<?> parent) {
			return (Interpreted<?, ? extends M>) new Interpreted<>((Def<QuickMenuItem<Object>>) this, parent);
		}
	}

	/**
	 * {@link QuickMenuItem} interpretation
	 *
	 * @param <T> The type of value to represent
	 * @param <M> The sub-type of menu item to create
	 */
	public static class Interpreted<T, M extends QuickMenuItem<T>> extends QuickAbstractMenuItem.Interpreted<T, M> {
		private InterpretedValueSynth<ObservableAction, ObservableAction> theAction;

		/**
		 * @param definition The definition to interpret
		 * @param parent The parent element for the menu item
		 */
		protected Interpreted(Def<? super M> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		/** @return The action to perform when the menu item is selected */
		public InterpretedValueSynth<ObservableAction, ObservableAction> getAction() {
			return theAction;
		}

		@Override
		public Def<? super M> getDefinition() {
			return (Def<? super M>) super.getDefinition();
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			super.doUpdate(env);

			theAction = interpret(getDefinition().getAction(), ModelTypes.Action.instance());
		}

		@Override
		public M create() {
			return (M) new QuickMenuItem<>(getIdentity());
		}
	}

	private ModelValueInstantiator<ObservableAction> theActionInstantiator;
	private SettableValue<ObservableAction> theAction;

	/** @param id The element ID for this menu item */
	protected QuickMenuItem(Object id) {
		super(id);
		theAction = SettableValue.build(ObservableAction.class).build();
	}

	/** @return The action to perform when the menu item is selected */
	public ObservableAction getAction() {
		return ObservableAction.flatten(theAction);
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);

		Interpreted<T, ?> myInterpreted = (Interpreted<T, ?>) interpreted;
		theActionInstantiator = myInterpreted.getAction().instantiate();
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();

		theActionInstantiator.instantiate();
	}

	@Override
	protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		super.doInstantiate(myModels);

		theAction.set(theActionInstantiator.get(myModels), null);
	}

	@Override
	public QuickMenuItem<T> copy(ExElement parent) {
		QuickMenuItem<T> copy = (QuickMenuItem<T>) super.copy(parent);

		copy.theAction = SettableValue.build(ObservableAction.class).build();

		return copy;
	}
}
