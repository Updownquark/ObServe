package org.observe.quick.base;

import javax.swing.Icon;

import org.observe.ObservableAction;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExFlexibleElementModelAddOn;
import org.observe.expresso.qonfig.ExWithElementModel;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.quick.style.QuickCompiledStyle;
import org.observe.quick.style.QuickInterpretedStyle;
import org.observe.quick.style.QuickStyledElement;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

/**
 * An action that can be performed on a single value in a {@link MultiValueWidget} or upon multiple values at once
 *
 * @param <T> The type of the values in the widget
 */
public interface ValueAction<T> extends QuickStyledElement {
	/** The XML name of the abstract value action element */
	public static final String ABSTRACT_VALUE_ACTION = "abstract-value-action";

	/**
	 * {@link ValueAction} definition
	 *
	 * @param <A> The sub-type of action to create
	 */
	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = ABSTRACT_VALUE_ACTION,
		interpretation = Interpreted.class,
		instance = ValueAction.class)
	public interface Def<A extends ValueAction<?>> extends QuickStyledElement.Def<A> {
		/** @return The name of the action--the text to represent the action to the user */
		@QonfigAttributeGetter("name")
		CompiledExpression getName();

		/** @return Whether the action should be represented a button near the multi widget */
		@QonfigAttributeGetter("as-button")
		boolean isButton();

		/** @return Whether the action should be represented as an item in a right-click popup menu on the widget */
		@QonfigAttributeGetter("as-popup")
		boolean isPopup();

		/** @return Whether the action is currently enabled */
		@QonfigAttributeGetter("enabled")
		CompiledExpression isEnabled();

		/** @return A tooltip describing the action */
		@QonfigAttributeGetter("tooltip")
		CompiledExpression getTooltip();

		/** @return The action to perform */
		@QonfigAttributeGetter
		CompiledExpression getAction();

		/**
		 * @param <T> The type of values in the widget
		 * @param parent The parent of the interpreted action
		 * @param valueType The type of values in the widget
		 * @return The interpreted action
		 */
		<T> Interpreted<? extends T, ? extends A> interpret(ExElement.Interpreted<?> parent, TypeToken<T> valueType);

		/**
		 * Abstract {@link ValueAction} definition interpretation
		 *
		 * @param <A> The sub-type of action to create
		 */
		public abstract class Abstract<A extends ValueAction<?>> extends QuickStyledElement.Def.Abstract<A> implements Def<A> {
			private CompiledExpression theName;
			private boolean isButton;
			private boolean isPopup;
			private CompiledExpression isEnabled;
			private CompiledExpression theTooltip;
			private CompiledExpression theAction;

			/**
			 * @param parent The parent of this element
			 * @param type The Qonfig type of this element
			 */
			protected Abstract(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
				super(parent, type);
			}

			@Override
			public CompiledExpression getName() {
				return theName;
			}

			@Override
			public boolean isButton() {
				return isButton;
			}

			@Override
			public boolean isPopup() {
				return isPopup;
			}

			@Override
			public CompiledExpression isEnabled() {
				return isEnabled;
			}

			@Override
			public CompiledExpression getTooltip() {
				return theTooltip;
			}

			@Override
			public CompiledExpression getAction() {
				return theAction;
			}

			@Override
			protected ActionStyle.Def wrap(QuickInstanceStyle.Def parentStyle, QuickCompiledStyle style) {
				return new ActionStyle.Def(parentStyle, this, style);
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session);
				theName = getAttributeExpression("name", session);
				isButton = session.getAttribute("as-button", boolean.class);
				isPopup = session.getAttribute("as-popup", boolean.class);
				isEnabled = getAttributeExpression("enabled", session);
				theTooltip = getAttributeExpression("tooltip", session);
				theAction = getValueExpression(session);
			}
		}
	}

	/**
	 * {@link ValueAction} interpretation
	 *
	 * @param <T> The type of values in the widget
	 * @param <A> The sub-type of action to create
	 */
	public interface Interpreted<T, A extends ValueAction<T>> extends QuickStyledElement.Interpreted<A> {
		@Override
		Def<? super A> getDefinition();

		/** @return The type of values in the widget */
		TypeToken<T> getValueType();

		/** @return The name of the action--the text to represent the action to the user */
		InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getName();

		/** @return Whether the action is currently enabled */
		InterpretedValueSynth<SettableValue<?>, SettableValue<String>> isEnabled();

		/** @return A tooltip describing the action */
		InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getTooltip();

		/** @return The action to perform */
		InterpretedValueSynth<ObservableAction, ObservableAction> getAction();

		/**
		 * Initializes or updates the action
		 *
		 * @param env The expresso environment to interpret expressions
		 * @throws ExpressoInterpretationException If the action could not be interpreted
		 */
		void updateAction(InterpretedExpressoEnv env) throws ExpressoInterpretationException;

		/** @return The value action instance */
		A create();

		/**
		 * Abstract {@link ValueAction} interpretation implementation
		 *
		 * @param <T> The type of values in the widget
		 * @param <A> The sub-type of action to create
		 */
		public abstract class Abstract<T, A extends ValueAction<T>> extends QuickStyledElement.Interpreted.Abstract<A>
		implements Interpreted<T, A> {
			private final TypeToken<T> theValueType;
			InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theName;
			InterpretedValueSynth<SettableValue<?>, SettableValue<String>> isEnabled;
			InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theTooltip;
			InterpretedValueSynth<ObservableAction, ObservableAction> theAction;

			/**
			 * @param definition The definition to interpret
			 * @param parent The parent of this element
			 * @param valueType the type of values in the widget
			 */
			protected Abstract(Def<? super A> definition, ExElement.Interpreted<?> parent, TypeToken<T> valueType) {
				super(definition, parent);
				theValueType = valueType;
			}

			@Override
			public Def<? super A> getDefinition() {
				return (Def<? super A>) super.getDefinition();
			}

			@Override
			public TypeToken<T> getValueType() {
				return theValueType;
			}

			@Override
			public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getName() {
				return theName;
			}

			@Override
			public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> isEnabled() {
				return isEnabled;
			}

			@Override
			public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getTooltip() {
				return theTooltip;
			}

			@Override
			public InterpretedValueSynth<ObservableAction, ObservableAction> getAction() {
				return theAction;
			}

			@Override
			public void updateAction(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				update(env);
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);
				theName = interpret(getDefinition().getName(), ModelTypes.Value.STRING);
				isEnabled = interpret(getDefinition().isEnabled(), ModelTypes.Value.STRING);
				theTooltip = interpret(getDefinition().getTooltip(), ModelTypes.Value.STRING);
				theAction = interpret(getDefinition().getAction(), ModelTypes.Action.instance());
			}
		}
	}

	/** @return The name of the action--the text to represent the action to the user */
	SettableValue<String> getName();

	/** @return Whether the action should be represented a button near the multi widget */
	boolean isButton();

	/** @return Whether the action should be represented as an item in a right-click popup menu on the widget */
	boolean isPopup();

	/** @return Whether the action is currently enabled */
	SettableValue<String> isEnabled();

	/** @return A tooltip describing the action */
	SettableValue<String> getTooltip();

	/** @return The action to perform */
	ObservableAction getAction();

	@Override
	ValueAction<T> copy(ExElement parent);

	/**
	 * Abstract {@link ValueAction} implementation
	 *
	 * @param <T> The type of values in the widget
	 */
	public abstract class Abstract<T> extends QuickStyledElement.Abstract implements ValueAction<T> {
		private TypeToken<T> theValueType;
		private ModelValueInstantiator<SettableValue<String>> theNameInstantiator;
		private ModelValueInstantiator<SettableValue<Icon>> theIconInstantiator;
		private ModelValueInstantiator<SettableValue<String>> theEnabledInstantiator;
		private ModelValueInstantiator<SettableValue<String>> theTooltipInstantiator;
		private ModelValueInstantiator<ObservableAction> theActionInstantiator;
		private SettableValue<SettableValue<String>> theName;
		private boolean isButton;
		private boolean isPopup;
		private SettableValue<SettableValue<String>> isEnabled;
		private SettableValue<SettableValue<String>> theTooltip;
		private ObservableAction theAction;

		/** @param id The element ID for the action */
		protected Abstract(Object id) {
			super(id);
			theName = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<String>> parameterized(String.class))
				.build();
			isEnabled = SettableValue.build(theName.getType()).build();
			theTooltip = SettableValue.build(theName.getType()).build();
		}

		/** @return The type of values in the widget */
		public TypeToken<T> getValueType() {
			return theValueType;
		}

		@Override
		public SettableValue<String> getName() {
			return SettableValue.flatten(theName);
		}

		@Override
		public boolean isButton() {
			return isButton;
		}

		@Override
		public boolean isPopup() {
			return isPopup;
		}

		@Override
		public SettableValue<String> isEnabled() {
			return SettableValue.flatten(isEnabled);
		}

		@Override
		public SettableValue<String> getTooltip() {
			return SettableValue.flatten(theTooltip);
		}

		@Override
		public ObservableAction getAction() {
			return theAction.disableWith(isEnabled());
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
			super.doUpdate(interpreted);
			ValueAction.Interpreted<T, ?> myInterpreted = (ValueAction.Interpreted<T, ?>) interpreted;
			theValueType = myInterpreted.getValueType();
			theNameInstantiator = myInterpreted.getName() == null ? null : myInterpreted.getName().instantiate();
			isButton = myInterpreted.getDefinition().isButton();
			isPopup = myInterpreted.getDefinition().isPopup();
			theEnabledInstantiator = myInterpreted.isEnabled() == null ? null : myInterpreted.isEnabled().instantiate();
			theTooltipInstantiator = myInterpreted.getTooltip() == null ? null : myInterpreted.getTooltip().instantiate();
			theActionInstantiator = myInterpreted.getAction().instantiate();
		}

		@Override
		public void instantiated() throws ModelInstantiationException {
			super.instantiated();

			if (theNameInstantiator != null)
				theNameInstantiator.instantiate();
			if (theIconInstantiator != null)
				theIconInstantiator.instantiate();
			if (theEnabledInstantiator != null)
				theEnabledInstantiator.instantiate();
			if (theTooltipInstantiator != null)
				theTooltipInstantiator.instantiate();
			theActionInstantiator.instantiate();
		}

		@Override
		protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
			super.doInstantiate(myModels);

			theName.set(theNameInstantiator == null ? null : theNameInstantiator.get(myModels), null);
			isEnabled.set(theEnabledInstantiator == null ? null : theEnabledInstantiator.get(myModels), null);
			theTooltip.set(theTooltipInstantiator == null ? null : theTooltipInstantiator.get(myModels), null);
			theAction = theActionInstantiator.get(myModels);
		}

		@Override
		public ValueAction.Abstract<T> copy(ExElement parent) {
			ValueAction.Abstract<T> copy = (ValueAction.Abstract<T>) super.copy(parent);

			copy.theName = SettableValue.build(theName.getType()).build();
			copy.isEnabled = SettableValue.build(isEnabled.getType()).build();
			copy.theTooltip = SettableValue.build(theTooltip.getType()).build();

			return copy;
		}
	}

	/**
	 * Model context for a single-value action
	 *
	 * @param <T> The type of the value
	 */
	public interface SingleValueActionContext<T> {
		/** @return The value that the action is being invoked or queried (e.g. its tooltip) upon */
		SettableValue<T> getActionValue();

		/**
		 * Default {@link SingleValueActionContext} implementation
		 *
		 * @param <T> The type of the value
		 */
		public class Default<T> implements SingleValueActionContext<T> {
			private final SettableValue<T> theActionValue;

			/** @param actionValue The value that the action is being invoked or queried (e.g. its tooltip) upon */
			public Default(SettableValue<T> actionValue) {
				theActionValue = actionValue;
			}

			/** @param type The type of the value */
			public Default(TypeToken<T> type) {
				this(SettableValue.build(type).build());
			}

			@Override
			public SettableValue<T> getActionValue() {
				return theActionValue;
			}
		}
	}

	/**
	 * Model context for a multi-value action
	 *
	 * @param <T> The type of the values
	 */
	public interface MultiValueActionContext<T> {
		/** @return The values that the action is being invoked or queried (e.g. its tooltip) upon */
		ObservableCollection<T> getActionValues();

		/**
		 * Default {@link MultiValueActionContext} implementation
		 *
		 * @param <T> The type of the values
		 */
		public class Default<T> implements MultiValueActionContext<T> {
			private final ObservableCollection<T> theActionValue;

			/** @param actionValues The values that the action is being invoked or queried (e.g. its tooltip) upon */
			public Default(ObservableCollection<T> actionValues) {
				theActionValue = actionValues;
			}

			/** @param type The type of the values */
			public Default(TypeToken<T> type) {
				this(ObservableCollection.build(type).build());
			}

			@Override
			public ObservableCollection<T> getActionValues() {
				return theActionValue;
			}
		}
	}

	/**
	 * Single value action
	 *
	 * @param <T> The type of the value
	 */
	public class Single<T> extends ValueAction.Abstract<T> {
		/** The XML name of this element */
		public static final String SINGLE_VALUE_ACTION = "value-action";

		/**
		 * {@link Single} {@link ValueAction} definition
		 *
		 * @param <A> The sub-type of action to create
		 */
		@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
			qonfigType = SINGLE_VALUE_ACTION,
			interpretation = Interpreted.class,
			instance = Single.class)
		public static class Def<A extends Single<?>> extends ValueAction.Def.Abstract<A> {
			private String theValueName;
			private boolean allowForMultiple;
			private ModelComponentId theValueVariable;

			/**
			 * @param parent The parent of this element
			 * @param type The Qonfig type of this element
			 */
			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
				super(parent, type);
			}

			/** @return Whether this action may be selected for multiple values, to be executed against each in sequence */
			@QonfigAttributeGetter("allow-for-multiple")
			public boolean allowForMultiple() {
				return allowForMultiple;
			}

			/**
			 * @return The model ID of the model value in which the current value (the one being acted or queried upon) will be available to
			 *         expressions
			 */
			@QonfigAttributeGetter("value-name")
			public ModelComponentId getValueVariable() {
				return theValueVariable;
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));
				theValueName = session.getAttributeText("value-name");
				allowForMultiple = session.getAttribute("allow-for-multiple", boolean.class);
				ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
				theValueVariable = elModels.getElementValueModelId(theValueName);
				elModels.satisfyElementValueType(theValueVariable, ModelTypes.Value,
					(interp, env) -> ModelTypes.Value.forType(((Interpreted<?, ?>) interp).getValueType()));
			}

			@Override
			public <T> Interpreted<? extends T, ? extends A> interpret(ExElement.Interpreted<?> parent, TypeToken<T> valueType) {
				return (Interpreted<? extends T, ? extends A>) new Single.Interpreted<>((Def<Single<T>>) this, parent, valueType);
			}
		}

		/**
		 * {@link Single} {@link ValueAction} definition
		 *
		 * @param <T> The type of the value
		 * @param <A> The sub-type of action to create
		 */
		public static class Interpreted<T, A extends Single<T>> extends ValueAction.Interpreted.Abstract<T, A> {
			/**
			 * @param definition The definition to interpret
			 * @param parent The parent element of this action
			 * @param valueType The type of the value
			 */
			protected Interpreted(Single.Def<? super A> definition, ExElement.Interpreted<?> parent, TypeToken<T> valueType) {
				super(definition, parent, valueType);
			}

			@Override
			public Def<? super A> getDefinition() {
				return (Def<? super A>) super.getDefinition();
			}

			@Override
			public A create() {
				return (A) new Single<>(getIdentity());
			}
		}

		private ModelComponentId theValueVariable;
		private SettableValue<SettableValue<T>> theActionValue;
		private boolean allowForMultiple;

		/** @param id The element ID for this action */
		protected Single(Object id) {
			super(id);
		}

		/** @return Whether this action may be selected for multiple values, to be executed against each in sequence */
		public boolean allowForMultiple() {
			return allowForMultiple;
		}

		/** @param ctx The model context for this action */
		public void setActionContext(SingleValueActionContext<T> ctx) {
			theActionValue.set(ctx.getActionValue(), null);
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
			super.doUpdate(interpreted);
			Interpreted<T, ?> myInterpreted = (Interpreted<T, ?>) interpreted;
			theValueVariable = myInterpreted.getDefinition().getValueVariable();
			allowForMultiple = myInterpreted.getDefinition().allowForMultiple();
			theActionValue = SettableValue
				.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<T>> parameterized(myInterpreted.getValueType())).build();
		}

		@Override
		protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
			super.doInstantiate(myModels);
			ExFlexibleElementModelAddOn.satisfyElementValue(theValueVariable, myModels, SettableValue.flatten(theActionValue));
		}

		@Override
		public Single<T> copy(ExElement parent) {
			Single<T> copy = (Single<T>) super.copy(parent);

			copy.theActionValue = SettableValue.build(theActionValue.getType()).build();

			return copy;
		}
	}

	/**
	 * Multi value action
	 *
	 * @param <T> The type of the values
	 */
	public class Multi<T> extends ValueAction.Abstract<T> {
		/** The XML name of this element */
		public static final String MULTI_VALUE_ACTION = "multi-value-action";

		/**
		 * {@link Multi} {@link ValueAction} definition
		 *
		 * @param <A> The sub-type of action to create
		 */
		@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
			qonfigType = MULTI_VALUE_ACTION,
			interpretation = Interpreted.class,
			instance = Multi.class)
		public static class Def<A extends Multi<?>> extends ValueAction.Def.Abstract<A> {
			private String theValuesName;
			private boolean allowForEmpty;
			private ModelComponentId theValuesVariable;

			/**
			 * @param parent The parent of this element
			 * @param type The Qonfig type of this element
			 */
			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
				super(parent, type);
			}

			/** @return Whether this action may be executed against an empty collection of values */
			@QonfigAttributeGetter("allow-for-empty")
			public boolean allowForEmpty() {
				return allowForEmpty;
			}

			/**
			 * @return The model ID of the model value in which the current values (the ones being acted or queried upon) will be available
			 *         to expressions
			 */
			@QonfigAttributeGetter("values-name")
			public ModelComponentId getValuesVariable() {
				return theValuesVariable;
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));
				theValuesName = session.getAttributeText("values-name");
				allowForEmpty = session.getAttribute("allow-for-empty", boolean.class);
				ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
				theValuesVariable = elModels.getElementValueModelId(theValuesName);
				elModels.satisfyElementValueType(theValuesVariable, ModelTypes.Collection,
					(interp, env) -> ModelTypes.Collection.forType(((Interpreted<?, ?>) interp).getValueType()));
			}

			@Override
			public <T> Multi.Interpreted<? extends T, ? extends A> interpret(ExElement.Interpreted<?> parent,
				TypeToken<T> valueType) {
				return (Interpreted<? extends T, ? extends A>) new Multi.Interpreted<>((Def<Multi<T>>) this, parent, valueType);
			}
		}

		/**
		 * {@link Multi} {@link ValueAction} interpretation
		 *
		 * @param <T> The type of the values
		 * @param <A> The sub-type of action to create
		 */
		public static class Interpreted<T, A extends Multi<T>> extends ValueAction.Interpreted.Abstract<T, A> {
			/**
			 * @param definition The definition to interpret
			 * @param parent The parent element of this action
			 * @param valueType The type of the value
			 */
			public Interpreted(Multi.Def<? super A> definition, ExElement.Interpreted<?> parent, TypeToken<T> valueType) {
				super(definition, parent, valueType);
			}

			@Override
			public Def<? super A> getDefinition() {
				return (Def<? super A>) super.getDefinition();
			}

			@Override
			public A create() {
				return (A) new Multi<>(getIdentity());
			}
		}

		private ModelComponentId theValuesVariable;
		private SettableValue<ObservableCollection<T>> theActionValues;
		private boolean allowForEmpty;

		/** @param id The element ID for this action */
		public Multi(Object id) {
			super(id);
		}

		/** @return Whether this action may be executed against an empty collection of values */
		public boolean allowForEmpty() {
			return allowForEmpty;
		}

		/** @param ctx The model context for this action */
		public void setActionContext(MultiValueActionContext<T> ctx) {
			theActionValues.set(ctx.getActionValues(), null);
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
			super.doUpdate(interpreted);
			Interpreted<T, ?> myInterpreted = (Interpreted<T, ?>) interpreted;
			theValuesVariable = myInterpreted.getDefinition().getValuesVariable();
			allowForEmpty = myInterpreted.getDefinition().allowForEmpty();
			theActionValues = SettableValue.build(
				TypeTokens.get().keyFor(ObservableCollection.class).<ObservableCollection<T>> parameterized(myInterpreted.getValueType()))
				.build();
		}

		@Override
		protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
			super.doInstantiate(myModels);
			ExFlexibleElementModelAddOn.satisfyElementValue(theValuesVariable, myModels,
				ObservableCollection.flattenValue(theActionValues));
		}

		@Override
		public Multi<T> copy(ExElement parent) {
			Multi<T> copy = (Multi<T>) super.copy(parent);

			copy.theActionValues = SettableValue.build(theActionValues.getType()).build();

			return copy;
		}
	}

	/** Doesn't do anything, but it's needed by the {@link QuickStyledElement} API since this doesn't extend another styled element */
	static class ActionStyle extends QuickInstanceStyle.Abstract {
		public static class Def extends QuickInstanceStyle.Def.Abstract {
			Def(QuickInstanceStyle.Def parent, QuickStyledElement.Def<?> styledElement, QuickCompiledStyle wrapped) {
				super(parent, styledElement, wrapped);
			}

			@Override
			public Interpreted interpret(ExElement.Interpreted<?> parentEl, QuickInterpretedStyle parent, InterpretedExpressoEnv env)
				throws ExpressoInterpretationException {
				return new Interpreted(this, (ValueAction.Interpreted<?, ?>) parentEl, (QuickInstanceStyle.Interpreted) parent,
					getWrapped().interpret(parentEl, parent, env));
			}
		}

		public static class Interpreted extends QuickInstanceStyle.Interpreted.Abstract {
			Interpreted(QuickInstanceStyle.Def definition, ValueAction.Interpreted<?, ?> styledElement,
				QuickInstanceStyle.Interpreted parent, QuickInterpretedStyle wrapped) {
				super(definition, styledElement, parent, wrapped);
			}

			@Override
			public QuickInstanceStyle create(QuickStyledElement parent) {
				return new ActionStyle();
			}
		}

		ActionStyle() {
			super();
		}
	}
}
