package org.observe.quick.base;

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
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

/**
 * A menu item that can be selected or deselected like a check box
 *
 * @param <T> The type of value to represent
 */
public class QuickCheckBoxMenuItem<T> extends QuickAbstractMenuItem<T> {
	/** The XML name of this element */
	public static final String CHECK_BOX_MENU_ITEM = "check-box-menu-item";

	/**
	 * {@link QuickCheckBoxMenuItem} definition
	 *
	 * @param <M> The sub-type of menu item to create
	 */
	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = CHECK_BOX_MENU_ITEM,
		interpretation = Interpreted.class,
		instance = QuickCheckBoxMenuItem.class)
	public static class Def<M extends QuickCheckBoxMenuItem<?>> extends QuickAbstractMenuItem.Def<M> {
		private CompiledExpression isSelected;

		/**
		 * @param parent The parent element of the widget
		 * @param type The Qonfig type of the widget
		 */
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		/** @return Whether the menu check box is selected */
		@QonfigAttributeGetter("selected")
		public CompiledExpression isSelected() {
			return isSelected;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);

			isSelected = getAttributeExpression("selected", session);
		}

		@Override
		public Interpreted<?, ? extends M> interpret(org.observe.expresso.qonfig.ExElement.Interpreted<?> parent) {
			return (Interpreted<?, ? extends M>) new Interpreted<>((Def<QuickCheckBoxMenuItem<Object>>) this, parent);
		}
	}

	/**
	 * {@link QuickCheckBoxMenuItem} interpretation
	 *
	 * @param <T> The type of value to represent
	 * @param <M> The sub-type of menu item to create
	 */
	public static class Interpreted<T, M extends QuickCheckBoxMenuItem<T>> extends QuickAbstractMenuItem.Interpreted<T, M> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> isSelected;

		/**
		 * @param definition The definition to interpret
		 * @param parent The parent element for the widget
		 */
		protected Interpreted(Def<? super M> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		/** @return Whether the menu check box is selected */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> isSelected() {
			return isSelected;
		}

		@Override
		public Def<? super M> getDefinition() {
			return (Def<? super M>) super.getDefinition();
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			super.doUpdate(env);

			isSelected = interpret(getDefinition().isSelected(), ModelTypes.Value.BOOLEAN);
		}

		@Override
		public M create() {
			return (M) new QuickCheckBoxMenuItem<>(getIdentity());
		}
	}

	private ModelValueInstantiator<SettableValue<Boolean>> theSelectedInstantiator;
	private SettableValue<SettableValue<Boolean>> isSelected;

	/** @param id The element ID for this widget */
	protected QuickCheckBoxMenuItem(Object id) {
		super(id);
		isSelected = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Boolean>> parameterized(boolean.class))
			.build();
	}

	/** @return Whether the menu check box is selected */
	public SettableValue<Boolean> isSelected() {
		return SettableValue.flatten(isSelected);
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);

		Interpreted<T, ?> myInterpreted = (Interpreted<T, ?>) interpreted;
		theSelectedInstantiator = myInterpreted.isSelected().instantiate();
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();

		theSelectedInstantiator.instantiate();
	}

	@Override
	protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		super.doInstantiate(myModels);

		isSelected.set(theSelectedInstantiator.get(myModels), null);
	}

	@Override
	public QuickCheckBoxMenuItem<T> copy(ExElement parent) {
		QuickCheckBoxMenuItem<T> copy = (QuickCheckBoxMenuItem<T>) super.copy(parent);

		copy.isSelected = SettableValue.build(isSelected.getType()).build();

		return copy;
	}
}
