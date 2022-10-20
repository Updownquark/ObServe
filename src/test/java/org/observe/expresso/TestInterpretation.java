package org.observe.expresso;

import java.util.Collections;
import java.util.Set;

import org.observe.SettableValue;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.qommons.Version;
import org.qommons.collect.BetterList;
import org.qommons.config.QonfigInterpretation;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreterCore.Builder;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.SpecialSession;

public class TestInterpretation implements QonfigInterpretation {
	public static final String TOOLKIT_NAME = "Expresso-Test";
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
		interpreter.createWith("stateful-struct", ObservableModelSet.ValueCreator.class, session -> {
			ExpressoQIS exS = session.as(ExpressoQIS.class);
			ObservableExpression derivedStateX = exS.getAttributeExpression("derived-state");
			return () -> {
				ValueContainer<SettableValue<?>, SettableValue<Integer>> derivedStateV;
				try {
					derivedStateV = derivedStateX.evaluate(ModelTypes.Value.forType(int.class), exS.getExpressoEnv());
				} catch (QonfigInterpretationException e) {
					session.withError("Could not create derived value", e);
					return null;
				}
				return new ObservableModelSet.AbstractValueContainer<SettableValue<?>, SettableValue<StatefulTestStructure>>(
					ModelTypes.Value.forType(StatefulTestStructure.class)) {
					@Override
					public SettableValue<StatefulTestStructure> get(ModelSetInstance models) {
						models = exS.wrapLocal(models);
						StatefulTestStructure structure = new StatefulTestStructure(derivedStateV.get(models));
						DynamicModelValue.satisfyDynamicValue(//
							"internalState", ModelTypes.Value.forType(int.class), models, structure.getInternalState());
						return SettableValue.of(StatefulTestStructure.class, structure, "Not Settable");
					}

					@Override
					public BetterList<ValueContainer<?, ?>> getCores() {
						return BetterList.of(this);
					}
				};
			};
		});
		return interpreter;
	}
}
