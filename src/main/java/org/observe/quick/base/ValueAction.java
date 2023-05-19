package org.observe.quick.base;

import javax.swing.Icon;

import org.observe.ObservableAction;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.CompiledExpression;
import org.observe.expresso.DynamicModelValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.quick.QuickElement;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.ex.ExFunction;

import com.google.common.reflect.TypeToken;

public interface ValueAction<T> extends QuickElement {
	public interface Def<T, A extends ValueAction<T>> extends QuickElement.Def<A> {
		CompiledExpression getName();

		boolean isButton();

		boolean isPopup();

		CompiledExpression getIcon();

		CompiledExpression isEnabled();

		boolean allowForEmpty();

		boolean allowForMultiple();

		CompiledExpression getTooltip();

		CompiledExpression getAction();

		Interpreted<? extends T, ? extends A> interpret(QuickElement.Interpreted<?> parent, TypeToken<? extends T> valueType);

		public abstract class Abstract<T, A extends ValueAction<T>> extends QuickElement.Def.Abstract<A> implements Def<T, A> {
			private CompiledExpression theName;
			private boolean isButton;
			private boolean isPopup;
			private CompiledExpression theIcon;
			private CompiledExpression isEnabled;
			private boolean allowForEmpty;
			private boolean allowForMultiple;
			private CompiledExpression theTooltip;
			private CompiledExpression theAction;

			protected Abstract(QuickElement.Def<?> parent, QonfigElement element) {
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
			public boolean allowForEmpty() {
				return allowForEmpty;
			}

			@Override
			public boolean allowForMultiple() {
				return allowForMultiple;
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
				super.update(session);
				theName = session.getAttributeExpression("name");
				isButton = session.getAttribute("as-button", boolean.class);
				isPopup = session.getAttribute("as-popup", boolean.class);
				theIcon = session.getAttributeExpression("icon");
				isEnabled = session.getAttributeExpression("enabled");
				allowForEmpty = session.getAttribute("allow-for-empty", boolean.class);
				allowForMultiple = session.getAttribute("allow-for-multiple", boolean.class);
				theTooltip = session.getAttributeExpression("tooltip");
				theAction = session.getValueExpression();
			}
		}
	}

	public interface Interpreted<T, A extends ValueAction<T>> extends QuickElement.Interpreted<A> {
		@Override
		Def<? super T, ? super A> getDefinition();

		TypeToken<T> getValueType();

		InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getName();

		ExFunction<ModelSetInstance, SettableValue<Icon>, ModelInstantiationException> getIcon();

		InterpretedValueSynth<SettableValue<?>, SettableValue<String>> isEnabled();

		InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getTooltip();

		InterpretedValueSynth<ObservableAction<?>, ObservableAction<?>> getAction();

		void update() throws ExpressoInterpretationException;

		ValueAction<T> create(QuickElement parent);

		public abstract class Abstract<T, A extends ValueAction<T>> extends QuickElement.Interpreted.Abstract<A>
		implements Interpreted<T, A> {
			private final TypeToken<T> theValueType;
			InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theName;
			ExFunction<ModelSetInstance, SettableValue<Icon>, ModelInstantiationException> theIcon;
			InterpretedValueSynth<SettableValue<?>, SettableValue<String>> isEnabled;
			InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theTooltip;
			InterpretedValueSynth<ObservableAction<?>, ObservableAction<?>> theAction;

			protected Abstract(Def<? super T, ? super A> definition, QuickElement.Interpreted<?> parent, TypeToken<T> valueType) {
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

	boolean allowForEmpty();

	boolean allowForMultiple();

	SettableValue<String> getTooltip();

	ObservableAction<?> getAction();

	public abstract class Abstract<T> extends QuickElement.Abstract implements ValueAction<T> {
		private final TypeToken<T> theValueType;
		private final SettableValue<SettableValue<String>> theName;
		private boolean isButton;
		private boolean isPopup;
		private final SettableValue<SettableValue<Icon>> theIcon;
		private final SettableValue<SettableValue<String>> isEnabled;
		private boolean allowForEmpty;
		private boolean allowForMultiple;
		private final SettableValue<SettableValue<String>> theTooltip;
		private ObservableAction<?> theAction;

		protected Abstract(ValueAction.Interpreted<T, ?> interpreted, QuickElement parent) {
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
		public boolean allowForEmpty() {
			return allowForEmpty;
		}

		@Override
		public boolean allowForMultiple() {
			return allowForMultiple;
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
		public void update(QuickElement.Interpreted<?> interpreted, ModelSetInstance models) throws ModelInstantiationException {
			super.update(interpreted, models);
			ValueAction.Interpreted<T, ?> myInterpreted = (ValueAction.Interpreted<T, ?>) interpreted;
			theName.set(myInterpreted.getName() == null ? null : myInterpreted.getName().get(getModels()), null);
			isButton = myInterpreted.getDefinition().isButton();
			isPopup = myInterpreted.getDefinition().isPopup();
			theIcon.set(myInterpreted.getIcon() == null ? null : myInterpreted.getIcon().apply(getModels()), null);
			isEnabled.set(myInterpreted.isEnabled() == null ? null : myInterpreted.isEnabled().get(getModels()), null);
			allowForEmpty = myInterpreted.getDefinition().allowForEmpty();
			allowForMultiple = myInterpreted.getDefinition().allowForMultiple();
			theTooltip.set(myInterpreted.getTooltip() == null ? null : myInterpreted.getTooltip().get(getModels()), null);
			theAction = myInterpreted.getAction().get(getModels());
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
		public static class Def<T, A extends Single<T>> extends ValueAction.Def.Abstract<T, A> {
			private String theValueName;

			public Def(QuickElement.Def<?> parent, QonfigElement element) {
				super(parent, element);
			}

			public String getValueName() {
				return theValueName;
			}

			@Override
			public void update(ExpressoQIS session) throws QonfigInterpretationException {
				super.update(session);
				theValueName = session.getAttributeText("value-name");
			}

			@Override
			public Interpreted<? extends T, ? extends A> interpret(QuickElement.Interpreted<?> parent, TypeToken<? extends T> valueType) {
				return new Single.Interpreted<>(this, parent, (TypeToken<T>) valueType);
			}
		}

		public static class Interpreted<T, A extends Single<T>> extends ValueAction.Interpreted.Abstract<T, A> {
			public Interpreted(Single.Def<? super T, ? super A> definition, QuickElement.Interpreted<?> parent, TypeToken<T> valueType) {
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
			public Single<T> create(QuickElement parent) {
				return new Single<>(this, parent);
			}
		}

		private String theValueName;

		public Single(Interpreted<T, ?> interpreted, QuickElement parent) {
			super(interpreted, parent);
		}

		public String getValueName() {
			return theValueName;
		}

		public void setActionContext(SingleValueActionContext<T> ctx) throws ModelInstantiationException {
			QuickElement.satisfyContextValue(theValueName, ModelTypes.Value.forType(getValueType()), ctx.getActionValue(), this);
		}

		@Override
		public void update(QuickElement.Interpreted<?> interpreted, ModelSetInstance models) throws ModelInstantiationException {
			super.update(interpreted, models);
			Interpreted<T, ?> myInterpreted = (Interpreted<T, ?>) interpreted;
			theValueName = myInterpreted.getDefinition().getValueName();
		}
	}

	public class Multi<T> extends ValueAction.Abstract<T> {
		public static class Def<T, A extends Multi<T>> extends ValueAction.Def.Abstract<T, A> {
			private String theValuesName;

			public Def(QuickElement.Def<?> parent, QonfigElement element) {
				super(parent, element);
			}

			public String getValuesName() {
				return theValuesName;
			}

			@Override
			public void update(ExpressoQIS session) throws QonfigInterpretationException {
				super.update(session);
				theValuesName = session.getAttributeText("values-name");
			}

			@Override
			public Multi.Interpreted<? extends T, ? extends A> interpret(QuickElement.Interpreted<?> parent,
				TypeToken<? extends T> valueType) {
				return new Multi.Interpreted<>(this, parent, (TypeToken<T>) valueType);
			}
		}

		public static class Interpreted<T, A extends Multi<T>> extends ValueAction.Interpreted.Abstract<T, A> {
			public Interpreted(Multi.Def<? super T, ? super A> definition, QuickElement.Interpreted<?> parent, TypeToken<T> valueType) {
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
			public Multi<T> create(QuickElement parent) {
				return new Multi<>(this, parent);
			}
		}

		private String theValuesName;

		public Multi(Interpreted<T, ?> interpreted, QuickElement parent) {
			super(interpreted, parent);
		}

		public String getValueName() {
			return theValuesName;
		}

		public void setActionContext(MultiValueActionContext<T> ctx) throws ModelInstantiationException {
			QuickElement.satisfyContextValue(theValuesName, ModelTypes.Collection.forType(getValueType()), ctx.getActionValues(), this);
		}

		@Override
		public void update(QuickElement.Interpreted<?> interpreted, ModelSetInstance models) throws ModelInstantiationException {
			super.update(interpreted, models);
			Interpreted<T, ?> myInterpreted = (Interpreted<T, ?>) interpreted;
			theValuesName = myInterpreted.getDefinition().getValuesName();
		}
	}
}
