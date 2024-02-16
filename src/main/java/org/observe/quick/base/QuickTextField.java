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
 * A component that displays a single line of text to represent and allow modification of a value
 *
 * @param <T> The type of the value to represent
 */
public class QuickTextField<T> extends QuickEditableTextWidget.Abstract<T> {
	/** The XML name of this element */
	public static final String TEXT_FIELD = "text-field";

	/**
	 * {@link QuickTextField} definition
	 *
	 * @param <F> The sub-type of text field to create
	 */
	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = TEXT_FIELD,
		interpretation = Interpreted.class,
		instance = QuickTextField.class)
	public static class Def<F extends QuickTextField<?>> extends QuickEditableTextWidget.Def.Abstract<F> {
		private Integer theColumns;
		private CompiledExpression isPassword;
		private CompiledExpression theEmptyText;

		/**
		 * @param parent The parent element of the widget
		 * @param type The Qonfig type of the widget
		 */
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		@Override
		public boolean isTypeEditable() {
			return true;
		}

		/** @return The number of columns of text to display--the horizontal size of the text field */
		@QonfigAttributeGetter("columns")
		public Integer getColumns() {
			return theColumns;
		}

		/** @return Determines whether the text field should display the text as-is or obfuscate it */
		@QonfigAttributeGetter("password")
		public CompiledExpression isPassword() {
			return isPassword;
		}

		/** @return Text to display to the user when the formatted value is empty, e.g. as a user prompt */
		@QonfigAttributeGetter("empty-text")
		public CompiledExpression getEmptyText() {
			return theEmptyText;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));
			String columnsStr = session.getAttributeText("columns");
			theColumns = columnsStr == null ? null : Integer.parseInt(columnsStr);
			isPassword = getAttributeExpression("password", session);
			theEmptyText = getAttributeExpression("empty-text", session);
		}

		@Override
		public Interpreted<?, ? extends F> interpret(ExElement.Interpreted<?> parent) {
			return (Interpreted<?, ? extends F>) new Interpreted<>((Def<QuickTextField<Object>>) this, parent);
		}
	}

	/**
	 * {@link QuickTextField} interpretation
	 *
	 * @param <T> The type of the value to represent
	 * @param <F> The sub-type of text field to create
	 */
	public static class Interpreted<T, F extends QuickTextField<T>> extends QuickEditableTextWidget.Interpreted.Abstract<T, F> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> isPassword;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theEmptyText;

		/**
		 * @param definition The definition to interpret
		 * @param parent The parent element for the widget
		 */
		protected Interpreted(Def<? super F> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def<? super F> getDefinition() {
			return (Def<? super F>) super.getDefinition();
		}

		/** @return Determines whether the text field should display the text as-is or obfuscate it */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> isPassword() {
			return isPassword;
		}

		/** @return Text to display to the user when the formatted value is empty, e.g. as a user prompt */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getEmptyText() {
			return theEmptyText;
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			super.doUpdate(env);
			isPassword = interpret(getDefinition().isPassword(), ModelTypes.Value.BOOLEAN);
			theEmptyText = interpret(getDefinition().getEmptyText(), ModelTypes.Value.STRING);
		}

		@Override
		public F create() {
			return (F) new QuickTextField<>(getIdentity());
		}
	}

	private ModelValueInstantiator<SettableValue<Boolean>> thePasswordInstantiator;
	private ModelValueInstantiator<SettableValue<String>> theEmptyTextInstantiator;
	private Integer theColumns;
	private SettableValue<SettableValue<Boolean>> isPassword;
	private SettableValue<SettableValue<String>> theEmptyText;

	/** @param id The element ID for this widget */
	protected QuickTextField(Object id) {
		super(id);
		isPassword = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Boolean>> parameterized(boolean.class))
			.build();
		theEmptyText = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<String>> parameterized(String.class))
			.build();
	}

	/** @return The number of columns of text to display--the horizontal size of the text field */
	public Integer getColumns() {
		return theColumns;
	}

	/** @return Determines whether the text field should display the text as-is or obfuscate it */
	public SettableValue<Boolean> isPassword() {
		return SettableValue.flatten(isPassword);
	}

	/** @return Text to display to the user when the formatted value is empty, e.g. as a user prompt */
	public SettableValue<String> getEmptyText() {
		return SettableValue.flatten(theEmptyText);
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);
		Interpreted<T, ?> myInterpreted = (Interpreted<T, ?>) interpreted;
		theColumns = myInterpreted.getDefinition().getColumns();
		thePasswordInstantiator = myInterpreted.isPassword().instantiate();
		theEmptyTextInstantiator = myInterpreted.getEmptyText() == null ? null : myInterpreted.getEmptyText().instantiate();
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();
		thePasswordInstantiator.instantiate();
		if (theEmptyTextInstantiator != null)
			theEmptyTextInstantiator.instantiate();
	}

	@Override
	protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		super.doInstantiate(myModels);
		isPassword.set(thePasswordInstantiator.get(myModels), null);
		theEmptyText.set(theEmptyTextInstantiator == null ? null : theEmptyTextInstantiator.get(myModels), null);
	}

	@Override
	public QuickTextField<T> copy(ExElement parent) {
		QuickTextField<T> copy = (QuickTextField<T>) super.copy(parent);

		copy.isPassword = SettableValue.build(isPassword.getType()).build();
		copy.theEmptyText = SettableValue.build(theEmptyText.getType()).build();

		return copy;
	}
}
