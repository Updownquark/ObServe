package org.observe.expresso.qonfig;

import org.observe.ObservableAction;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.ExpressoTransformations.ActionTransform;
import org.observe.expresso.qonfig.ExpressoTransformations.Operation;
import org.observe.expresso.qonfig.ExpressoTransformations.TypePreservingTransform;
import org.observe.util.TypeTokens;
import org.qommons.collect.BetterList;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreterCore;

public class ObservableActionTransformations {
	private ObservableActionTransformations() {
	}

	public static void configureTransformation(QonfigInterpreterCore.Builder interpreter) {
		interpreter.createWith("disable", ActionTransform.class, ExElement.creator(DisabledActionTransform::new));
	}

	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE, qonfigType = "disable", interpretation = DisabledActionTransform.Interpreted.class)
	static class DisabledActionTransform extends TypePreservingTransform<ObservableAction>
	implements ActionTransform<ObservableAction, ExElement> {
		private CompiledExpression theDisablement;
		private ModelComponentId theSourceVariable;

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
			String sourceAs = session.getAttributeText("source-as");
			ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
			theSourceVariable = sourceAs == null ? null : elModels.getElementValueModelId(sourceAs);
			theDisablement = getAttributeExpression("with", session);
			// Not useable for action--there's no value, but we still have to satisfy the type
			if (theSourceVariable != null)
				elModels.satisfyElementValueType(theSourceVariable, ModelTypes.Value.forType(TypeTokens.get().VOID));
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
			public void update(ModelInstanceType<ObservableAction, ObservableAction> sourceType, InterpretedExpressoEnv env)
				throws ExpressoInterpretationException {
				super.update(sourceType, env);
				theDisablement = ExpressoTransformations.parseFilter(getDefinition().getDisablement(), getExpressoEnv(), true);
			}

			@Override
			public BetterList<InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList.of(theDisablement);
			}

			@Override
			public Instantiator<T> instantiate() {
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
			public void instantiate() {
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
}
