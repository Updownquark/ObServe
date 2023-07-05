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
import org.observe.expresso.qonfig.ElementTypeTraceability;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.ex.ExFunction;

import com.google.common.reflect.TypeToken;

public interface ValueAction<T> extends ExElement {
	public static final ElementTypeTraceability<ValueAction<?>, Interpreted<?, ?>, Def<?, ?>> VALUE_ACTION_TRACEABILITY = ElementTypeTraceability
		.<ValueAction<?>, Interpreted<?, ?>, Def<?, ?>> build(QuickBaseInterpretation.NAME, QuickBaseInterpretation.VERSION,
			"abstract-value-action")//
		.reflectMethods(Def.class, Interpreted.class, ValueAction.class)//
		.build();

	public interface Def<T, A extends ValueAction<T>> extends ExElement.Def<A> {
		@QonfigAttributeGetter("name")
		CompiledExpression getName();

		@QonfigAttributeGetter("as-button")
		boolean isButton();

		@QonfigAttributeGetter("as-popup")
		boolean isPopup();

		@QonfigAttributeGetter("icon")
		CompiledExpression getIcon();

		@QonfigAttributeGetter("enabled")
		CompiledExpression isEnabled();

		@QonfigAttributeGetter("tooltip")
		CompiledExpression getTooltip();

		@QonfigAttributeGetter
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
				withTraceability(VALUE_ACTION_TRACEABILITY.validate(session.getFocusType(), session.reporting()));
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
		private static final ElementTypeTraceability<Single<?>, Interpreted<?, ?>, Def<?, ?>> TRACEABILITY = ElementTypeTraceability
			.<Single<?>, Interpreted<?, ?>, Def<?, ?>> build(QuickBaseInterpretation.NAME, QuickBaseInterpretation.VERSION,
				SINGLE_VALUE_ACTION)//
			.reflectMethods(Def.class, Interpreted.class, Single.class)//
			.build();

		public static class Def<T, A extends Single<T>> extends ValueAction.Def.Abstract<T, A> {
			private String theValueName;
			private boolean allowForMultiple;

			public Def(ExElement.Def<?> parent, QonfigElement element) {
				super(parent, element);
			}

			@QonfigAttributeGetter("value-name")
			public String getValueName() {
				return theValueName;
			}

			@QonfigAttributeGetter("allow-for-multiple")
			public boolean allowForMultiple() {
				return allowForMultiple;
			}

			@Override
			public void update(ExpressoQIS session) throws QonfigInterpretationException {
				withTraceability(TRACEABILITY.validate(session.getFocusType(), session.reporting()));
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
		private static final ElementTypeTraceability<Multi<?>, Interpreted<?, ?>, Def<?, ?>> TRACEABILITY = ElementTypeTraceability
			.<Multi<?>, Interpreted<?, ?>, Def<?, ?>> build(QuickBaseInterpretation.NAME, QuickBaseInterpretation.VERSION,
				MULTI_VALUE_ACTION)//
			.reflectMethods(Def.class, Interpreted.class, Multi.class)//
			.build();

		public static class Def<T, A extends Multi<T>> extends ValueAction.Def.Abstract<T, A> {
			private String theValuesName;
			private boolean allowForEmpty;

			public Def(ExElement.Def<?> parent, QonfigElement element) {
				super(parent, element);
			}

			@QonfigAttributeGetter("values-name")
			public String getValuesName() {
				return theValuesName;
			}

			@QonfigAttributeGetter("allow-for-empty")
			public boolean allowForEmpty() {
				return allowForEmpty;
			}

			@Override
			public void update(ExpressoQIS session) throws QonfigInterpretationException {
				withTraceability(TRACEABILITY.validate(session.getFocusType(), session.reporting()));
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
