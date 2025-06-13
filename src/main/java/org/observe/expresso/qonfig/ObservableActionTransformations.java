package org.observe.expresso.qonfig;

import org.observe.ObservableAction;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.ExElement.Def;
import org.observe.expresso.qonfig.ExpressoTransformations.ActionTransform;
import org.observe.expresso.qonfig.ExpressoTransformations.Operation;
import org.observe.expresso.qonfig.ExpressoTransformations.TypePreservingTransform;
import org.qommons.LambdaUtils;
import org.qommons.collect.BetterList;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreterCore;

/** Transformation for {@link ModelTypes#Action Action} model values */
public class ObservableActionTransformations {
	private ObservableActionTransformations() {
	}

	/**
	 * Configures an interpreter with action transformation capabilities
	 *
	 * @param interpreter The interpretation builder to configure
	 */
	public static void configureTransformation(QonfigInterpreterCore.Builder interpreter) {
		interpreter.createWith(DisabledActionTransform.DISABLE, ActionTransform.class, ExElement.creator(DisabledActionTransform::new));
		interpreter.createWith(EnabledActionTransform.ENABLED, ActionTransform.class, ExElement.creator(EnabledActionTransform::new));
	}

	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = DisabledActionTransform.DISABLE,
		interpretation = DisabledActionTransform.Interpreted.class)
	static class DisabledActionTransform extends TypePreservingTransform<ObservableAction>
	implements ActionTransform<ObservableAction, ExElement> {
		public static final String DISABLE = "disable";
		private CompiledExpression theDisablement;

		DisabledActionTransform(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@QonfigAttributeGetter("with")
		public CompiledExpression getDisablement() {
			return theDisablement;
		}

		@Override
		public void update(ExpressoQIS session, ModelType<ObservableAction> sourceModelType) throws QonfigInterpretationException {
			super.update(session, sourceModelType);
			theDisablement = getAttributeExpression("with", session);
		}

		@Override
		protected Interpreted<?> tppInterpret(org.observe.expresso.qonfig.ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<T> extends TypePreservingTransform.Interpreted<ObservableAction, ObservableAction> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theDisablement;

			Interpreted(DisabledActionTransform definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public DisabledActionTransform getDefinition() {
				return (DisabledActionTransform) super.getDefinition();
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getDisablement() {
				return theDisablement;
			}

			@Override
			public void update(ModelInstanceType<ObservableAction, ObservableAction> sourceType)
				throws ExpressoInterpretationException {
				super.update(sourceType);
				theDisablement = ExpressoTransformations.parseFilter(getDefinition().getDisablement(), this, true);
			}

			@Override
			public BetterList<InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList.of(theDisablement);
			}

			@Override
			public Instantiator<T> instantiate() throws ModelInstantiationException {
				return new Instantiator<>(theDisablement.instantiate());
			}

			@Override
			public String toString() {
				return "disableWith(" + theDisablement + ")";
			}
		}

		static class Instantiator<T> implements Operation.EfficientCopyingInstantiator<ObservableAction, ObservableAction> {
			private final ModelValueInstantiator<SettableValue<String>> theDisablement;

			Instantiator(ModelValueInstantiator<SettableValue<String>> disablement) {
				theDisablement = disablement;
			}

			@Override
			public boolean isEfficientCopy() {
				return true;
			}

			@Override
			public void instantiate() throws ModelInstantiationException {
				theDisablement.instantiate();
			}

			@Override
			public ObservableAction transform(ObservableAction source, ModelSetInstance models) throws ModelInstantiationException {
				SettableValue<String> disabled = theDisablement.get(models);
				return new DisabledAction(source, disabled);
			}

			@Override
			public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				SettableValue<String> disabled = theDisablement.get(sourceModels);
				return disabled != theDisablement.forModelCopy(disabled, sourceModels, newModels);
			}

			@Override
			public ObservableAction getSource(ObservableAction value) {
				return ((DisabledAction) value).getParentAction();
			}

			@Override
			public ObservableAction forModelCopy(ObservableAction prevValue, ObservableAction newSource, ModelSetInstance sourceModels,
				ModelSetInstance newModels) throws ModelInstantiationException {
				DisabledAction disabled = (DisabledAction) prevValue;
				SettableValue<String> newDisablement = theDisablement.forModelCopy((SettableValue<String>) disabled.getDisablement(),
					sourceModels, newModels);
				if (newSource == disabled.getParentAction() && newDisablement == disabled.getDisablement())
					return prevValue;
				else
					return new DisabledAction(newSource, newDisablement);
			}
		}

		static class DisabledAction extends ObservableAction.DisabledObservableAction {
			DisabledAction(ObservableAction parentAction, ObservableValue<String> disablement) {
				super(parentAction, disablement);
			}

			@Override
			protected ObservableAction getParentAction() {
				return super.getParentAction();
			}

			@Override
			protected ObservableValue<String> getDisablement() {
				return super.getDisablement();
			}
		}
	}

	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = EnabledActionTransform.ENABLED,
		interpretation = EnabledActionTransform.Interpreted.class)
	static class EnabledActionTransform extends ExElement.Def.Abstract<ExElement> implements ActionTransform<SettableValue<?>, ExElement> {
		public static final String ENABLED = "enabled";

		EnabledActionTransform(Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@Override
		public ModelType<? extends SettableValue<?>> getTargetModelType() {
			return ModelTypes.Value;
		}

		@Override
		public void update(ExpressoQIS session, ModelType<ObservableAction> sourceModelType) throws QonfigInterpretationException {
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<?> parent) throws ExpressoInterpretationException {
			return new Interpreted(this, parent);
		}

		static class Interpreted extends ExElement.Interpreted.Abstract<ExElement>
			implements Operation.Interpreted<ObservableAction, ObservableAction, SettableValue<?>, SettableValue<String>, ExElement> {
			Interpreted(EnabledActionTransform definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public EnabledActionTransform getDefinition() {
				return (EnabledActionTransform) super.getDefinition();
			}

			@Override
			public BetterList<InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList.empty();
			}

			@Override
			public void update(ModelInstanceType<ObservableAction, ObservableAction> sourceType) throws ExpressoInterpretationException {
			}

			@Override
			public ModelInstanceType<? extends SettableValue<?>, ? extends SettableValue<String>> getTargetType() {
				return ModelTypes.Value.STRING;
			}

			@Override
			public Operation.Instantiator<ObservableAction, SettableValue<String>> instantiate() throws ModelInstantiationException {
				return new Instantiator();
			}

			@Override
			public String toString() {
				return "enabled";
			}
		}

		static class Instantiator implements Operation.EfficientCopyingInstantiator<ObservableAction, SettableValue<String>> {
			Instantiator() {
			}

			@Override
			public boolean isEfficientCopy() {
				return true;
			}

			@Override
			public void instantiate() throws ModelInstantiationException {
			}

			@Override
			public SettableValue<String> transform(ObservableAction source, ModelSetInstance models) throws ModelInstantiationException {
				return new EnabledValue(source);
			}

			@Override
			public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				return false;
			}

			@Override
			public ObservableAction getSource(SettableValue<String> value) {
				return ((EnabledValue) value).getAction();
			}

			@Override
			public SettableValue<String> forModelCopy(SettableValue<String> prevValue, ObservableAction newSource,
				ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				EnabledValue ev = (EnabledValue) prevValue;
				if (ev.getAction() == newSource)
					return ev;
				else
					return new EnabledValue(newSource);
			}
		}

		static class EnabledValue extends SettableValue.AlwaysDisabledValue<String> {
			private final ObservableAction theAction;

			EnabledValue(ObservableAction wrapped) {
				super(wrapped.isEnabled(), LambdaUtils.constantFn("Enablement is not settable", "Enablement is not settable", null));
				theAction = wrapped;
			}

			ObservableAction getAction() {
				return theAction;
			}
		}
	}
}
