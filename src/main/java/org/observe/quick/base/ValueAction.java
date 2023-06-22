package org.observe.quick.base;

import javax.swing.Icon;

import org.observe.ObservableAction;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.DynamicModelValue;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.ex.ExFunction;

import com.google.common.reflect.TypeToken;

public interface ValueAction<T> extends ExElement {
	public interface Def<T, A extends ValueAction<T>> extends ExElement.Def<A> {
		public static final ExElement.AttributeValueGetter.Expression<ValueAction<?>, Interpreted<?, ?>, Def<?, ?>, ObservableAction<?>, ObservableAction<?>> ACTION = ExElement.AttributeValueGetter
			.<ValueAction<?>, Interpreted<?, ?>, Def<?, ?>, ObservableAction<?>, ObservableAction<?>> ofX(Def::getAction,
				Interpreted::getAction, ValueAction::getAction, "The action to perform when the user selects the action");
		public static final ExElement.AttributeValueGetter.Expression<ValueAction<?>, Interpreted<?, ?>, Def<?, ?>, SettableValue<?>, SettableValue<String>> NAME = ExElement.AttributeValueGetter
			.<ValueAction<?>, Interpreted<?, ?>, Def<?, ?>, SettableValue<?>, SettableValue<String>> ofX(Def::getName, Interpreted::getName,
				ValueAction::getName, "The name of the action to display to the user");
		public static final ExElement.AttributeValueGetter<ValueAction<?>, Interpreted<?, ?>, Def<?, ?>> AS_BUTTON = ExElement.AttributeValueGetter
			.<ValueAction<?>, Interpreted<?, ?>, Def<?, ?>> of(Def::isButton, i -> i.getDefinition().isButton(), ValueAction::isButton,
				"Whether the action should be presented to the user as a button near the table");
		public static final ExElement.AttributeValueGetter<ValueAction<?>, Interpreted<?, ?>, Def<?, ?>> AS_POPUP = ExElement.AttributeValueGetter
			.<ValueAction<?>, Interpreted<?, ?>, Def<?, ?>> of(Def::isPopup, i -> i.getDefinition().isPopup(), ValueAction::isPopup,
				"Whether the action should be presented to the user as a popup when they right-click on the table");
		public static final ExElement.AttributeValueGetter<ValueAction<?>, Interpreted<?, ?>, Def<?, ?>> ICON = ExElement.AttributeValueGetter
			.<ValueAction<?>, Interpreted<?, ?>, Def<?, ?>> of(Def::getIcon, Interpreted::getIcon, ValueAction::getIcon,
				"The icon to display to represent this action");
		public static final ExElement.AttributeValueGetter.Expression<ValueAction<?>, Interpreted<?, ?>, Def<?, ?>, SettableValue<?>, SettableValue<String>> ENABLED = ExElement.AttributeValueGetter
			.<ValueAction<?>, Interpreted<?, ?>, Def<?, ?>, SettableValue<?>, SettableValue<String>> ofX(Def::isEnabled,
				Interpreted::isEnabled, ValueAction::isEnabled, "Whether the action is currently enabled");
		public static final ExElement.AttributeValueGetter.Expression<ValueAction<?>, Interpreted<?, ?>, Def<?, ?>, SettableValue<?>, SettableValue<String>> TOOLTIP = ExElement.AttributeValueGetter
			.<ValueAction<?>, Interpreted<?, ?>, Def<?, ?>, SettableValue<?>, SettableValue<String>> ofX(Def::getTooltip,
				Interpreted::getTooltip, ValueAction::getTooltip, "The tooltip to display to the user to describe the action");

		CompiledExpression getName();

		boolean isButton();

		boolean isPopup();

		CompiledExpression getIcon();

		CompiledExpression isEnabled();

		CompiledExpression getTooltip();

		CompiledExpression getAction();

		Interpreted<? extends T, ? extends A> interpret(ExElement.Interpreted<?> parent, TypeToken<? extends T> valueType);

		public abstract class Abstract<T, A extends ValueAction<T>> extends ExElement.Def.Abstract<A> implements Def<T, A> {
			public static final String ABTRACT_VALUE_ACTION = "abstract-value-action";

			private CompiledExpression theName;
			private boolean isButton;
			private boolean isPopup;
			private CompiledExpression theIcon;
			private CompiledExpression isEnabled;
			private CompiledExpression theTooltip;
			private CompiledExpression theAction;

			protected Abstract(ExElement.Def<?> parent, QonfigElement element) {
				super(parent, element);
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
			public CompiledExpression getIcon() {
				return theIcon;
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
			public void update(ExpressoQIS session) throws QonfigInterpretationException {
				ExElement.checkElement(session.getFocusType(), QuickBaseInterpretation.NAME, QuickBaseInterpretation.VERSION,
					ABTRACT_VALUE_ACTION);
				forValue(ACTION);
				forAttribute(session.getAttributeDef(null, null, "name"), NAME);
				forAttribute(session.getAttributeDef(null, null, "as-button"), AS_BUTTON);
				forAttribute(session.getAttributeDef(null, null, "as-popup"), AS_POPUP);
				forAttribute(session.getAttributeDef(null, null, "icon"), ICON);
				forAttribute(session.getAttributeDef(null, null, "enabled"), ENABLED);
				forAttribute(session.getAttributeDef(null, null, "tooltip"), TOOLTIP);
				super.update(session);
				theName = session.getAttributeExpression("name");
				isButton = session.getAttribute("as-button", boolean.class);
				isPopup = session.getAttribute("as-popup", boolean.class);
				theIcon = session.getAttributeExpression("icon");
				isEnabled = session.getAttributeExpression("enabled");
				theTooltip = session.getAttributeExpression("tooltip");
				theAction = session.getValueExpression();
			}
		}
	}

	public interface Interpreted<T, A extends ValueAction<T>> extends ExElement.Interpreted<A> {
		@Override
		Def<? super T, ? super A> getDefinition();

		TypeToken<T> getValueType();

		InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getName();

		ExFunction<ModelSetInstance, SettableValue<Icon>, ModelInstantiationException> getIcon();

		InterpretedValueSynth<SettableValue<?>, SettableValue<String>> isEnabled();

		InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getTooltip();

		InterpretedValueSynth<ObservableAction<?>, ObservableAction<?>> getAction();

		void update() throws ExpressoInterpretationException;

		ValueAction<T> create(ExElement parent);

		public abstract class Abstract<T, A extends ValueAction<T>> extends ExElement.Interpreted.Abstract<A> implements Interpreted<T, A> {
			private final TypeToken<T> theValueType;
			InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theName;
			ExFunction<ModelSetInstance, SettableValue<Icon>, ModelInstantiationException> theIcon;
			InterpretedValueSynth<SettableValue<?>, SettableValue<String>> isEnabled;
			InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theTooltip;
			InterpretedValueSynth<ObservableAction<?>, ObservableAction<?>> theAction;

			protected Abstract(Def<? super T, ? super A> definition, ExElement.Interpreted<?> parent, TypeToken<T> valueType) {
				super(definition, parent);
				theValueType = valueType;
			}

			@Override
			public Def<? super T, ? super A> getDefinition() {
				return (Def<? super T, ? super A>) super.getDefinition();
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
			public ExFunction<ModelSetInstance, SettableValue<Icon>, ModelInstantiationException> getIcon() {
				return theIcon;
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
			public InterpretedValueSynth<ObservableAction<?>, ObservableAction<?>> getAction() {
				return theAction;
			}

			@Override
			public void update() throws ExpressoInterpretationException {
				super.update();
				theName = getDefinition().getName() == null ? null
					: getDefinition().getName().evaluate(ModelTypes.Value.STRING).interpret();
				theIcon = getDefinition().getIcon() == null ? null : QuickBaseInterpretation.evaluateIcon(getDefinition().getIcon(),
					getDefinition().getExpressoEnv(), getDefinition().getCallingClass());
				isEnabled = getDefinition().isEnabled() == null ? null
					: getDefinition().isEnabled().evaluate(ModelTypes.Value.STRING).interpret();
				theTooltip = getDefinition().getTooltip() == null ? null
					: getDefinition().getTooltip().evaluate(ModelTypes.Value.STRING).interpret();
				theAction = getDefinition().getAction().evaluate(ModelTypes.Action.any()).interpret();
			}
		}
	}

	SettableValue<String> getName();

	boolean isButton();

	boolean isPopup();

	ObservableValue<Icon> getIcon();

	SettableValue<String> isEnabled();

	SettableValue<String> getTooltip();

	ObservableAction<?> getAction();

	public abstract class Abstract<T> extends ExElement.Abstract implements ValueAction<T> {
		private final TypeToken<T> theValueType;
		private final SettableValue<SettableValue<String>> theName;
		private boolean isButton;
		private boolean isPopup;
		private final SettableValue<SettableValue<Icon>> theIcon;
		private final SettableValue<SettableValue<String>> isEnabled;
		private final SettableValue<SettableValue<String>> theTooltip;
		private ObservableAction<?> theAction;

		protected Abstract(ValueAction.Interpreted<T, ?> interpreted, ExElement parent) {
			super(interpreted, parent);
			theValueType = interpreted.getValueType();
			theName = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<String>> parameterized(String.class))
				.build();
			theIcon = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Icon>> parameterized(Icon.class))
				.build();
			isEnabled = SettableValue.build(theName.getType()).build();
			theTooltip = SettableValue.build(theName.getType()).build();
		}

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
		public ObservableValue<Icon> getIcon() {
			return SettableValue.flatten(theIcon);
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
		public ObservableAction<?> getAction() {
			return theAction;
		}

		@Override
		public ModelSetInstance update(ExElement.Interpreted<?> interpreted, ModelSetInstance models) throws ModelInstantiationException {
			ModelSetInstance myModels = super.update(interpreted, models);
			ValueAction.Interpreted<T, ?> myInterpreted = (ValueAction.Interpreted<T, ?>) interpreted;
			theName.set(myInterpreted.getName() == null ? null : myInterpreted.getName().get(myModels), null);
			isButton = myInterpreted.getDefinition().isButton();
			isPopup = myInterpreted.getDefinition().isPopup();
			theIcon.set(myInterpreted.getIcon() == null ? null : myInterpreted.getIcon().apply(myModels), null);
			isEnabled.set(myInterpreted.isEnabled() == null ? null : myInterpreted.isEnabled().get(myModels), null);
			theTooltip.set(myInterpreted.getTooltip() == null ? null : myInterpreted.getTooltip().get(myModels), null);
			theAction = myInterpreted.getAction().get(myModels);
			return myModels;
		}
	}

	public interface SingleValueActionContext<T> {
		SettableValue<T> getActionValue();

		public class Default<T> implements SingleValueActionContext<T> {
			private final SettableValue<T> theActionValue;

			public Default(SettableValue<T> actionValue) {
				theActionValue = actionValue;
			}

			public Default(TypeToken<T> type) {
				this(SettableValue.build(type).build());
			}

			@Override
			public SettableValue<T> getActionValue() {
				return theActionValue;
			}
		}
	}

	public interface MultiValueActionContext<T> {
		ObservableCollection<T> getActionValues();

		public class Default<T> implements MultiValueActionContext<T> {
			private final ObservableCollection<T> theActionValue;

			public Default(ObservableCollection<T> actionValue) {
				theActionValue = actionValue;
			}

			public Default(TypeToken<T> type) {
				this(ObservableCollection.build(type).build());
			}

			@Override
			public ObservableCollection<T> getActionValues() {
				return theActionValue;
			}
		}
	}

	public class Single<T> extends ValueAction.Abstract<T> {
		public static final String SINGLE_VALUE_ACTION = "value-action";

		private static final ExElement.AttributeValueGetter<Single<?>, Interpreted<?, ?>, Def<?, ?>> VALUE_NAME = ExElement.AttributeValueGetter
			.<Single<?>, Interpreted<?, ?>, Def<?, ?>> of(Def::getValueName, i -> i.getDefinition().getValueName(), Single::getValueName,
				"The variable name of the value that the action is invoked upon, for expressions");
		private static final ExElement.AttributeValueGetter<Single<?>, Interpreted<?, ?>, Def<?, ?>> ALLOW_FOR_MULTIPLE = ExElement.AttributeValueGetter
			.<Single<?>, Interpreted<?, ?>, Def<?, ?>> of(Def::allowForMultiple, i -> i.getDefinition().allowForMultiple(),
				Single::allowForMultiple,
				"Whether an action can be invoked by the user for multiple values at once.  The action would then be invoked separately for each value.");
		public static class Def<T, A extends Single<T>> extends ValueAction.Def.Abstract<T, A> {
			private String theValueName;
			private boolean allowForMultiple;

			public Def(ExElement.Def<?> parent, QonfigElement element) {
				super(parent, element);
			}

			public String getValueName() {
				return theValueName;
			}

			public boolean allowForMultiple() {
				return allowForMultiple;
			}

			@Override
			public void update(ExpressoQIS session) throws QonfigInterpretationException {
				ExElement.checkElement(session.getFocusType(), QuickBaseInterpretation.NAME, QuickBaseInterpretation.VERSION,
					SINGLE_VALUE_ACTION);
				forAttribute(session.getAttributeDef(null, null, "value-name"), VALUE_NAME);
				forAttribute(session.getAttributeDef(null, null, "allow-for-multiple"), ALLOW_FOR_MULTIPLE);
				super.update(session.asElement(session.getFocusType().getSuperElement()));
				theValueName = session.getAttributeText("value-name");
				allowForMultiple = session.getAttribute("allow-for-multiple", boolean.class);
			}

			@Override
			public Interpreted<? extends T, ? extends A> interpret(ExElement.Interpreted<?> parent, TypeToken<? extends T> valueType) {
				return new Single.Interpreted<>(this, parent, (TypeToken<T>) valueType);
			}
		}

		public static class Interpreted<T, A extends Single<T>> extends ValueAction.Interpreted.Abstract<T, A> {
			public Interpreted(Single.Def<? super T, ? super A> definition, ExElement.Interpreted<?> parent, TypeToken<T> valueType) {
				super(definition, parent, valueType);
			}

			@Override
			public Def<? super T, ? super A> getDefinition() {
				return (Def<? super T, ? super A>) super.getDefinition();
			}

			@Override
			public void update() throws ExpressoInterpretationException {
				DynamicModelValue.satisfyDynamicValueType(getDefinition().getValueName(), getDefinition().getModels(),
					ModelTypes.Value.forType(getValueType()));
				super.update();
			}

			@Override
			public Single<T> create(ExElement parent) {
				return new Single<>(this, parent);
			}
		}

		private final SettableValue<SettableValue<T>> theActionValue;
		private String theValueName;
		private boolean allowForMultiple;

		public Single(Interpreted<T, ?> interpreted, ExElement parent) {
			super(interpreted, parent);
			theActionValue = SettableValue
				.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<T>> parameterized(interpreted.getValueType())).build();
		}

		public String getValueName() {
			return theValueName;
		}

		public boolean allowForMultiple() {
			return allowForMultiple;
		}

		public void setActionContext(SingleValueActionContext<T> ctx) throws ModelInstantiationException {
			theActionValue.set(ctx.getActionValue(), null);
		}

		@Override
		protected void updateModel(ExElement.Interpreted<?> interpreted, ModelSetInstance myModels) throws ModelInstantiationException {
			super.updateModel(interpreted, myModels);
			Interpreted<T, ?> myInterpreted = (Interpreted<T, ?>) interpreted;
			theValueName = myInterpreted.getDefinition().getValueName();
			allowForMultiple = myInterpreted.getDefinition().allowForMultiple();
			ExElement.satisfyContextValue(theValueName, ModelTypes.Value.forType(getValueType()), SettableValue.flatten(theActionValue),
				myModels, this);
		}
	}

	public class Multi<T> extends ValueAction.Abstract<T> {
		public static final String MULTI_VALUE_ACTION = "multi-value-action";

		private static final ExElement.AttributeValueGetter<Multi<?>, Interpreted<?, ?>, Def<?, ?>> VALUES_NAME = ExElement.AttributeValueGetter
			.<Multi<?>, Interpreted<?, ?>, Def<?, ?>> of(Def::getValuesName, i -> i.getDefinition().getValuesName(), Multi::getValuesName,
				"The variable name of the values that the action is invoked upon, for expressions");
		private static final ExElement.AttributeValueGetter<Multi<?>, Interpreted<?, ?>, Def<?, ?>> ALLOW_FOR_EMPTY = ExElement.AttributeValueGetter
			.<Multi<?>, Interpreted<?, ?>, Def<?, ?>> of(Def::allowForEmpty, i -> i.getDefinition().allowForEmpty(), Multi::allowForEmpty,
				"Whether the action can be invoked upon an empty list of values");

		public static class Def<T, A extends Multi<T>> extends ValueAction.Def.Abstract<T, A> {
			private String theValuesName;
			private boolean allowForEmpty;

			public Def(ExElement.Def<?> parent, QonfigElement element) {
				super(parent, element);
			}

			public String getValuesName() {
				return theValuesName;
			}

			public boolean allowForEmpty() {
				return allowForEmpty;
			}

			@Override
			public void update(ExpressoQIS session) throws QonfigInterpretationException {
				ExElement.checkElement(session.getFocusType(), QuickBaseInterpretation.NAME, QuickBaseInterpretation.VERSION,
					MULTI_VALUE_ACTION);
				forAttribute(session.getAttributeDef(null, null, "values-name"), VALUES_NAME);
				forAttribute(session.getAttributeDef(null, null, "allow-for-empty"), ALLOW_FOR_EMPTY);
				super.update(session.asElement(session.getFocusType().getSuperElement()));
				theValuesName = session.getAttributeText("values-name");
				allowForEmpty = session.getAttribute("allow-for-empty", boolean.class);
			}

			@Override
			public Multi.Interpreted<? extends T, ? extends A> interpret(ExElement.Interpreted<?> parent,
				TypeToken<? extends T> valueType) {
				return new Multi.Interpreted<>(this, parent, (TypeToken<T>) valueType);
			}
		}

		public static class Interpreted<T, A extends Multi<T>> extends ValueAction.Interpreted.Abstract<T, A> {
			public Interpreted(Multi.Def<? super T, ? super A> definition, ExElement.Interpreted<?> parent, TypeToken<T> valueType) {
				super(definition, parent, valueType);
			}

			@Override
			public Def<? super T, ? super A> getDefinition() {
				return (Def<? super T, ? super A>) super.getDefinition();
			}

			@Override
			public void update() throws ExpressoInterpretationException {
				DynamicModelValue.satisfyDynamicValueType(getDefinition().getValuesName(), getDefinition().getModels(),
					ModelTypes.Collection.forType(getValueType()));
				super.update();
			}

			@Override
			public Multi<T> create(ExElement parent) {
				return new Multi<>(this, parent);
			}
		}

		private final SettableValue<ObservableCollection<T>> theActionValues;
		private String theValuesName;
		private boolean allowForEmpty;

		public Multi(Interpreted<T, ?> interpreted, ExElement parent) {
			super(interpreted, parent);
			theActionValues = SettableValue
				.build(
					TypeTokens.get().keyFor(ObservableCollection.class).<ObservableCollection<T>> parameterized(interpreted.getValueType()))
				.build();
		}

		public String getValuesName() {
			return theValuesName;
		}

		public boolean allowForEmpty() {
			return allowForEmpty;
		}

		public void setActionContext(MultiValueActionContext<T> ctx) throws ModelInstantiationException {
			theActionValues.set(ctx.getActionValues(), null);
		}

		@Override
		protected void updateModel(ExElement.Interpreted<?> interpreted, ModelSetInstance myModels) throws ModelInstantiationException {
			super.updateModel(interpreted, myModels);
			Interpreted<T, ?> myInterpreted = (Interpreted<T, ?>) interpreted;
			theValuesName = myInterpreted.getDefinition().getValuesName();
			allowForEmpty = myInterpreted.getDefinition().allowForEmpty();
			ExElement.satisfyContextValue(theValuesName, ModelTypes.Collection.forType(getValueType()),
				ObservableCollection.flattenValue(theActionValues), myModels, this);
		}
	}
}
