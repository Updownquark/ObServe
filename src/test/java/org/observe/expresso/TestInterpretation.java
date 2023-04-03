package org.observe.expresso;

import java.util.Collections;
import java.util.Set;

import org.observe.SettableValue;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.qommons.Version;
import org.qommons.collect.BetterList;
import org.qommons.config.QonfigInterpretation;
import org.qommons.config.QonfigInterpreterCore.Builder;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.SpecialSession;

/** Interpretation for the test-specific Qonfig toolkit */
public class TestInterpretation implements QonfigInterpretation {
	/** The name of the test toolkit */
	public static final String TOOLKIT_NAME = "Expresso-Test";
	/** The version of the test toolkit */
	public static final Version VERSION = new Version(0, 1, 0);

	@Override
	public Set<Class<? extends SpecialSession<?>>> getExpectedAPIs() {
		return Collections.singleton(ExpressoQIS.class);
	}

	@Override
	public String getToolkitName() {
		return TOOLKIT_NAME;
	}

	@Override
	public Version getVersion() {
		return VERSION;
	}

	@Override
	public void init(QonfigToolkit toolkit) {
	}

	@Override
	public Builder configureInterpreter(Builder interpreter) {
		interpreter//
		.createWith("stateful-struct", ObservableModelSet.ValueCreator.class, session -> {
			ExpressoQIS exS = session.as(ExpressoQIS.class);
			QonfigExpression2 derivedStateX = exS.getAttributeExpression("derived-state");
			return () -> {
				ValueContainer<SettableValue<?>, SettableValue<Integer>> derivedStateV;
				derivedStateV = derivedStateX.evaluate(ModelTypes.Value.forType(int.class));
				return new ObservableModelSet.AbstractValueContainer<SettableValue<?>, SettableValue<StatefulTestStructure>>(
					ModelTypes.Value.forType(StatefulTestStructure.class)) {
					@Override
					public SettableValue<StatefulTestStructure> get(ModelSetInstance models) throws ModelInstantiationException {
						models = exS.wrapLocal(models);
						StatefulTestStructure structure = new StatefulTestStructure(derivedStateV.get(models));
						try {
							DynamicModelValue.satisfyDynamicValue(//
								"internalState", ModelTypes.Value.forType(int.class), models, structure.getInternalState());
						} catch (ExpressoInterpretationException e) {
							throw new ModelInstantiationException(e.getMessage(), e.getPosition(), e.getErrorLength(), e);
						} catch (ModelException | TypeConversionException e) {
							throw new ModelInstantiationException(e.getMessage(), session.getElement().getPositionInFile(), 0, e);
						}
						return SettableValue.of(StatefulTestStructure.class, structure, "Not Settable");
					}

					@Override
					public SettableValue<StatefulTestStructure> forModelCopy(SettableValue<StatefulTestStructure> value,
						ModelSetInstance sourceModels, ModelSetInstance newModels) {
						return value;
					}

					@Override
					public BetterList<ValueContainer<?, ?>> getCores() {
						return BetterList.of(this);
					}
				};
			};
		})//
		.createWith("dynamic-type-stateful-struct", ObservableModelSet.ValueCreator.class, session -> {
			ExpressoQIS exS = session.as(ExpressoQIS.class);
			QonfigExpression2 internalStateX = exS.getAttributeExpression("internal-state");
			QonfigExpression2 derivedStateX = exS.getAttributeExpression("derived-state");
			return () -> {
				// Satisfy the internalState value with the internalState container
				DynamicModelValue.satisfyDynamicValue("internalState", exS.getExpressoEnv().getModels(), () -> {
					return internalStateX.evaluate(ModelTypes.Value.any());
				});
				ValueContainer<SettableValue<?>, SettableValue<?>> internalStateV;
				try {
					internalStateV = exS.getExpressoEnv().getModels().getValue("internalState", ModelTypes.Value.any());
				} catch (ModelException | TypeConversionException e) {
					throw new ExpressoInterpretationException(e.getMessage(), session.getElement().getPositionInFile(), 0, e);
				}
				ValueContainer<SettableValue<?>, SettableValue<?>> derivedStateV = derivedStateX.evaluate(ModelTypes.Value.any());
				return new ObservableModelSet.AbstractValueContainer<SettableValue<?>, SettableValue<DynamicTypeStatefulTestStructure>>(
					ModelTypes.Value.forType(DynamicTypeStatefulTestStructure.class)) {
					@Override
					public SettableValue<DynamicTypeStatefulTestStructure> get(ModelSetInstance models)
						throws ModelInstantiationException {
						models = exS.wrapLocal(models);
						DynamicTypeStatefulTestStructure structure = new DynamicTypeStatefulTestStructure(//
							internalStateV.get(models), derivedStateV.get(models));
						return SettableValue.of(DynamicTypeStatefulTestStructure.class, structure, "Not Settable");
					}

					@Override
					public SettableValue<DynamicTypeStatefulTestStructure> forModelCopy(
						SettableValue<DynamicTypeStatefulTestStructure> value, ModelSetInstance sourceModels,
						ModelSetInstance newModels) {
						return value;
					}

					@Override
					public BetterList<ValueContainer<?, ?>> getCores() {
						return BetterList.of(this);
					}
				};
			};
		})//
		.createWith("dynamic-type-stateful-struct2", ObservableModelSet.ValueCreator.class, session -> {
			ExpressoQIS exS = session.as(ExpressoQIS.class);
			QonfigExpression2 derivedStateX = exS.getAttributeExpression("derived-state");
			return () -> {
				ValueContainer<SettableValue<?>, SettableValue<?>> internalStateV;
				try {
					internalStateV = exS.getExpressoEnv().getModels().getValue("internalState", ModelTypes.Value.any());
				} catch (ModelException | TypeConversionException e) {
					throw new ExpressoInterpretationException(e.getMessage(), session.getElement().getPositionInFile(), 0, e);
				}
				ValueContainer<SettableValue<?>, SettableValue<?>> derivedStateV = derivedStateX.evaluate(ModelTypes.Value.any());
				return new ObservableModelSet.AbstractValueContainer<SettableValue<?>, SettableValue<DynamicTypeStatefulTestStructure>>(
					ModelTypes.Value.forType(DynamicTypeStatefulTestStructure.class)) {
					@Override
					public SettableValue<DynamicTypeStatefulTestStructure> get(ModelSetInstance models)
						throws ModelInstantiationException {
						models = exS.wrapLocal(models);
						DynamicTypeStatefulTestStructure structure = new DynamicTypeStatefulTestStructure(//
							internalStateV.get(models), derivedStateV.get(models));
						return SettableValue.of(DynamicTypeStatefulTestStructure.class, structure, "Not Settable");
					}

					@Override
					public SettableValue<DynamicTypeStatefulTestStructure> forModelCopy(
						SettableValue<DynamicTypeStatefulTestStructure> value, ModelSetInstance sourceModels,
						ModelSetInstance newModels) {
						return value;
					}

					@Override
					public BetterList<ValueContainer<?, ?>> getCores() {
						return BetterList.of(this);
					}
				};
			};
		})//
		;
		return interpreter;
	}
}
