package org.observe.quick.base;

import javax.swing.Icon;

import org.observe.ObservableAction;
import org.observe.ObservableValue;
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
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public interface ValueAction<T> extends ExElement {
	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = "abstract-value-action",
		interpretation = Interpreted.class,
		instance = ValueAction.class)
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
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session);
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

		InterpretedValueSynth<SettableValue<?>, SettableValue<Icon>> getIcon();

		InterpretedValueSynth<SettableValue<?>, SettableValue<String>> isEnabled();

		InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getTooltip();

		InterpretedValueSynth<ObservableAction, ObservableAction> getAction();

		void updateAction(InterpretedExpressoEnv env) throws ExpressoInterpretationException;

		ValueAction<T> create();

		public abstract class Abstract<T, A extends ValueAction<T>> extends ExElement.Interpreted.Abstract<A> implements Interpreted<T, A> {
			private final TypeToken<T> theValueType;
			InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theName;
			InterpretedValueSynth<SettableValue<?>, SettableValue<Icon>> theIcon;
			InterpretedValueSynth<SettableValue<?>, SettableValue<String>> isEnabled;
			InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theTooltip;
			InterpretedValueSynth<ObservableAction, ObservableAction> theAction;

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
			public InterpretedValueSynth<SettableValue<?>, SettableValue<Icon>> getIcon() {
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
				theName = getDefinition().getName() == null ? null
					: getDefinition().getName().interpret(ModelTypes.Value.STRING, getExpressoEnv());
				theIcon = getDefinition().getIcon() == null ? null : QuickBaseInterpretation.evaluateIcon(getDefinition().getIcon(),
					getExpressoEnv(), getDefinition().getElement().getDocument().getLocation());
				isEnabled = getDefinition().isEnabled() == null ? null
					: getDefinition().isEnabled().interpret(ModelTypes.Value.STRING, getExpressoEnv());
				theTooltip = getDefinition().getTooltip() == null ? null
					: getDefinition().getTooltip().interpret(ModelTypes.Value.STRING, getExpressoEnv());
				theAction = getDefinition().getAction().interpret(ModelTypes.Action.instance(), getExpressoEnv());
			}
		}
	}

	SettableValue<String> getName();

	boolean isButton();

	boolean isPopup();

	ObservableValue<Icon> getIcon();

	SettableValue<String> isEnabled();

	SettableValue<String> getTooltip();

	ObservableAction getAction();

	@Override
	ValueAction<T> copy(ExElement parent);

	public abstract class Abstract<T> extends ExElement.Abstract implements ValueAction<T> {
		private TypeToken<T> theValueType;
		private ModelValueInstantiator<SettableValue<String>> theNameInstantiator;
		private ModelValueInstantiator<SettableValue<Icon>> theIconInstantiator;
		private ModelValueInstantiator<SettableValue<String>> theEnabledInstantiator;
		private ModelValueInstantiator<SettableValue<String>> theTooltipInstantiator;
		private ModelValueInstantiator<ObservableAction> theActionInstantiator;
		private SettableValue<SettableValue<String>> theName;
		private boolean isButton;
		private boolean isPopup;
		private SettableValue<SettableValue<Icon>> theIcon;
		private SettableValue<SettableValue<String>> isEnabled;
		private SettableValue<SettableValue<String>> theTooltip;
		private ObservableAction theAction;

		protected Abstract(Object id) {
			super(id);
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
		public ObservableAction getAction() {
			return theAction.disableWith(isEnabled());
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) {
			super.doUpdate(interpreted);
			ValueAction.Interpreted<T, ?> myInterpreted = (ValueAction.Interpreted<T, ?>) interpreted;
			theValueType = myInterpreted.getValueType();
			theNameInstantiator = myInterpreted.getName() == null ? null : myInterpreted.getName().instantiate();
			isButton = myInterpreted.getDefinition().isButton();
			isPopup = myInterpreted.getDefinition().isPopup();
			theIconInstantiator = myInterpreted.getIcon() == null ? null : myInterpreted.getIcon().instantiate();
			theEnabledInstantiator = myInterpreted.isEnabled() == null ? null : myInterpreted.isEnabled().instantiate();
			theTooltipInstantiator = myInterpreted.getTooltip() == null ? null : myInterpreted.getTooltip().instantiate();
			theActionInstantiator = myInterpreted.getAction().instantiate();
		}

		@Override
		public void instantiated() {
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
			theIcon.set(theIconInstantiator == null ? null : theIconInstantiator.get(myModels), null);
			isEnabled.set(theEnabledInstantiator == null ? null : theEnabledInstantiator.get(myModels), null);
			theTooltip.set(theTooltipInstantiator == null ? null : theTooltipInstantiator.get(myModels), null);
			theAction = theActionInstantiator.get(myModels);
		}

		@Override
		public ValueAction.Abstract<T> copy(ExElement parent) {
			ValueAction.Abstract<T> copy = (ValueAction.Abstract<T>) super.copy(parent);

			copy.theName = SettableValue.build(theName.getType()).build();
			copy.theIcon = SettableValue.build(theIcon.getType()).build();
			copy.isEnabled = SettableValue.build(isEnabled.getType()).build();
			copy.theTooltip = SettableValue.build(theTooltip.getType()).build();

			return copy;
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

		@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
			qonfigType = SINGLE_VALUE_ACTION,
			interpretation = Interpreted.class,
			instance = Single.class)
		public static class Def<T, A extends Single<T>> extends ValueAction.Def.Abstract<T, A> {
			private String theValueName;
			private boolean allowForMultiple;
			private ModelComponentId theValueVariable;

			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
				super(parent, type);
			}

			@QonfigAttributeGetter("value-name")
			public String getValueName() {
				return theValueName;
			}

			@QonfigAttributeGetter("allow-for-multiple")
			public boolean allowForMultiple() {
				return allowForMultiple;
			}

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
			public Single<T> create() {
				return new Single<>(getIdentity());
			}
		}

		private ModelComponentId theValueVariable;
		private SettableValue<SettableValue<T>> theActionValue;
		private boolean allowForMultiple;

		public Single(Object id) {
			super(id);
		}

		public boolean allowForMultiple() {
			return allowForMultiple;
		}

		public void setActionContext(SingleValueActionContext<T> ctx) throws ModelInstantiationException {
			theActionValue.set(ctx.getActionValue(), null);
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) {
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

	public class Multi<T> extends ValueAction.Abstract<T> {
		public static final String MULTI_VALUE_ACTION = "multi-value-action";

		@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
			qonfigType = MULTI_VALUE_ACTION,
			interpretation = Interpreted.class,
			instance = Multi.class)
		public static class Def<T, A extends Multi<T>> extends ValueAction.Def.Abstract<T, A> {
			private String theValuesName;
			private boolean allowForEmpty;
			private ModelComponentId theValuesVariable;

			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
				super(parent, type);
			}

			@QonfigAttributeGetter("values-name")
			public String getValuesName() {
				return theValuesName;
			}

			@QonfigAttributeGetter("allow-for-empty")
			public boolean allowForEmpty() {
				return allowForEmpty;
			}

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
			public Multi<T> create() {
				return new Multi<>(getIdentity());
			}
		}

		private ModelComponentId theValuesVariable;
		private SettableValue<ObservableCollection<T>> theActionValues;
		private boolean allowForEmpty;

		public Multi(Object id) {
			super(id);
		}

		public boolean allowForEmpty() {
			return allowForEmpty;
		}

		public void setActionContext(MultiValueActionContext<T> ctx) throws ModelInstantiationException {
			theActionValues.set(ctx.getActionValues(), null);
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) {
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
}
