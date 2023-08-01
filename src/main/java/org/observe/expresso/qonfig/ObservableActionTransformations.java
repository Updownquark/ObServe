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
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ElementTypeTraceability.SingleTypeTraceability;
import org.observe.expresso.qonfig.ExpressoTransformations.ActionTransform;
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
		interpreter.createWith("disable", ActionTransform.class, ExpressoBaseV0_1.creator(DisabledActionTransform::new));
	}

	static class DisabledActionTransform extends TypePreservingTransform<ObservableAction<?>>
	implements ActionTransform<ObservableAction<?>, ExElement> {
		private static final SingleTypeTraceability<ExElement, Interpreted<?>, DisabledActionTransform> TRACEABILITY = ElementTypeTraceability
			.getElementTraceability(ExpressoBaseV0_1.NAME, ExpressoBaseV0_1.VERSION, "disable", DisabledActionTransform.class,
				Interpreted.class, null);
		private CompiledExpression theDisablement;

		DisabledActionTransform(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@QonfigAttributeGetter("with")
		public CompiledExpression getDisablement() {
			return theDisablement;
		}

		@Override
		public void update(ExpressoQIS session, ModelType<ObservableAction<?>> sourceModelType) throws QonfigInterpretationException {
			withTraceability(TRACEABILITY.validate(session.getFocusType(), session.reporting()));
			super.update(session, sourceModelType);
			String sourceAs = session.getAttributeText("source-as");
			theDisablement = session.getAttributeExpression("with");
			// Not useable for action--there's no value, but we still have to satisfy the type
			getAddOn(ExWithElementModel.Def.class).satisfyElementValueType(sourceAs, ModelTypes.Value.forType(TypeTokens.get().VOID));
		}

		@Override
		protected Interpreted<?> tppInterpret(org.observe.expresso.qonfig.ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<T> extends TypePreservingTransform.Interpreted<ObservableAction<?>, ObservableAction<T>> implements
		ExpressoTransformations.Operation.EfficientCopyingInterpreted<ObservableAction<?>, ObservableAction<T>, ObservableAction<?>, ObservableAction<T>, ExElement> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theDisablement;

			Interpreted(DisabledActionTransform definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public DisabledActionTransform getDefinition() {
				return (DisabledActionTransform) super.getDefinition();
			}

			@Override
			public boolean isEfficientCopy() {
				return true;
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getDisablement() {
				return theDisablement;
			}

			@Override
			public void update(ModelInstanceType<ObservableAction<?>, ObservableAction<T>> sourceType, InterpretedExpressoEnv env)
				throws ExpressoInterpretationException {
				super.update(sourceType, env);
				theDisablement = ExpressoTransformations.parseFilter(getDefinition().getDisablement(), getExpressoEnv(), true);
			}

			@Override
			public BetterList<InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList.of(theDisablement);
			}

			@Override
			public ObservableAction<T> transform(ObservableAction<T> source, ModelSetInstance models) throws ModelInstantiationException {
				SettableValue<String> disabled = theDisablement.get(models);
				return new DisabledAction<>(source, disabled);
			}

			@Override
			public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				SettableValue<String> disabled = theDisablement.get(sourceModels);
				return disabled != theDisablement.forModelCopy(disabled, sourceModels, newModels);
			}

			@Override
			public ObservableAction<T> getSource(ObservableAction<T> value) {
				return ((DisabledAction<T>) value).getParentAction();
			}

			@Override
			public ObservableAction<T> forModelCopy(ObservableAction<T> prevValue, ObservableAction<T> newSource,
				ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				DisabledAction<T> disabled = (DisabledAction<T>) prevValue;
				SettableValue<String> newDisablement = theDisablement.forModelCopy((SettableValue<String>) disabled.getDisablement(),
					sourceModels, newModels);
				if (newSource == disabled.getParentAction() && newDisablement == disabled.getDisablement())
					return prevValue;
				else
					return new DisabledAction<>(newSource, newDisablement);
			}

			@Override
			public String toString() {
				return "disableWith(" + theDisablement + ")";
			}
		}

		static class DisabledAction<T> extends ObservableAction.DisabledObservableAction<T> {
			DisabledAction(ObservableAction<T> parentAction, ObservableValue<String> disablement) {
				super(parentAction, disablement);
			}

			@Override
			protected ObservableAction<T> getParentAction() {
				return super.getParentAction();
			}

			@Override
			protected ObservableValue<String> getDisablement() {
				return super.getDisablement();
			}
		}
	}
}
