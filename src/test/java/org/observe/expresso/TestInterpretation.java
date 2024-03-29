package org.observe.expresso;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.observe.SettableValue;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.ObservableModelSet.ModelInstantiator;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExFlexibleElementModelAddOn;
import org.observe.expresso.qonfig.ExWithElementModel;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.ModelValueElement;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.qommons.Version;
import org.qommons.collect.BetterList;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretation;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreterCore.Builder;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.SpecialSession;

/** Interpretation for the test-specific Qonfig toolkit */
public class TestInterpretation implements QonfigInterpretation {
	/** The name of the test toolkit */
	public static final String TOOLKIT_NAME = "Expresso-Test";
	/** The version of the test toolkit */
	public static final Version VERSION = new Version(0, 1, 0);

	public static final String TESTING = "Expresso-Test v0.1";

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
		interpreter.createWith("stateful-struct", ModelValueElement.CompiledSynth.class, ExElement.creator(StatefulStruct::new));
		interpreter.createWith("dynamic-type-stateful-struct", ModelValueElement.CompiledSynth.class,
			ExElement.creator(DynamicTypeStatefulStruct::new));
		interpreter.createWith("dynamic-type-stateful-struct2", ModelValueElement.CompiledSynth.class,
			ExElement.creator(DynamicTypeStatefulStruct2::new));
		return interpreter;
	}

	@ExElementTraceable(toolkit = TESTING, qonfigType = "stateful-struct", interpretation = StatefulStruct.Interpreted.class)
	static class StatefulStruct extends ExElement.Def.Abstract<ModelValueElement<SettableValue<?>, SettableValue<StatefulTestStructure>>>
	implements
	ModelValueElement.CompiledSynth<SettableValue<?>, ModelValueElement<SettableValue<?>, SettableValue<StatefulTestStructure>>> {
		private String theModelPath;
		private CompiledExpression theDerivedState;
		private ModelComponentId theInternalStateVariable;

		public StatefulStruct(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@Override
		public String getModelPath() {
			return theModelPath;
		}

		@Override
		public ModelType<SettableValue<?>> getModelType(CompiledExpressoEnv env) {
			return ModelTypes.Value;
		}

		@QonfigAttributeGetter("derived-state")
		public CompiledExpression getDerivedState() {
			return theDerivedState;
		}

		public ModelComponentId getInternalStateVariable() {
			return theInternalStateVariable;
		}

		@Override
		public CompiledExpression getElementValue() {
			return null;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			theModelPath = session.get(ModelValueElement.PATH_KEY, String.class);
			theDerivedState = getAttributeExpression("derived-state", session);
			theInternalStateVariable = getAddOn(ExWithElementModel.Def.class).getElementValueModelId("internalState");
		}

		@Override
		public void prepareModelValue(ExpressoQIS session) throws QonfigInterpretationException {
		}

		@Override
		public Interpreted interpretValue(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}

		static class Interpreted
		extends ExElement.Interpreted.Abstract<ModelValueElement<SettableValue<?>, SettableValue<StatefulTestStructure>>> implements
		ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<StatefulTestStructure>, ModelValueElement<SettableValue<?>, SettableValue<StatefulTestStructure>>> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> theDerivedState;

			Interpreted(StatefulStruct def, ExElement.Interpreted<?> parent) {
				super(def, parent);
			}

			@Override
			public StatefulStruct getDefinition() {
				return (StatefulStruct) super.getDefinition();
			}

			@Override
			public ModelInstanceType<SettableValue<?>, SettableValue<StatefulTestStructure>> getType() {
				return ModelTypes.Value.forType(StatefulTestStructure.class);
			}

			@Override
			public InterpretedValueSynth<?, ?> getElementValue() {
				return null;
			}

			@Override
			public void updateValue(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				update(env);
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);
				theDerivedState = interpret(getDefinition().getDerivedState(), ModelTypes.Value.INT);
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return theDerivedState.getComponents();
			}

			@Override
			public ModelValueInstantiator<SettableValue<StatefulTestStructure>> instantiate() {
				return new Instantiator(getExpressoEnv().getModels().instantiate(), theDerivedState.instantiate(),
					getDefinition().getInternalStateVariable());
			}

			@Override
			public ModelValueElement<SettableValue<?>, SettableValue<StatefulTestStructure>> create() {
				return null;
			}
		}

		static class Instantiator implements ModelValueInstantiator<SettableValue<StatefulTestStructure>> {
			private final ModelInstantiator theLocalModel;
			private final ModelValueInstantiator<SettableValue<Integer>> theDerivedState;
			private final ModelComponentId theInternalStateVariable;

			Instantiator(ModelInstantiator localModel, ModelValueInstantiator<SettableValue<Integer>> derivedState,
				ModelComponentId internalStateVariable) {
				theLocalModel = localModel;
				theDerivedState = derivedState;
				theInternalStateVariable = internalStateVariable;
			}

			@Override
			public void instantiate() {
				theLocalModel.instantiate();
				theDerivedState.instantiate();
			}

			@Override
			public SettableValue<StatefulTestStructure> get(ModelSetInstance models)
				throws ModelInstantiationException, IllegalStateException {
				models = theLocalModel.wrap(models);
				StatefulTestStructure structure = new StatefulTestStructure(theDerivedState.get(models));
				ExFlexibleElementModelAddOn.satisfyElementValue(theInternalStateVariable, models, structure.getInternalState());
				return SettableValue.of(StatefulTestStructure.class, structure, "Not Settable");
			}

			@Override
			public SettableValue<StatefulTestStructure> forModelCopy(SettableValue<StatefulTestStructure> value,
				ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				return get(newModels);
			}
		}
	}

	@ExElementTraceable(toolkit = TESTING,
		qonfigType = "dynamic-type-stateful-struct",
		interpretation = DynamicTypeStatefulStruct.Interpreted.class)
	static class DynamicTypeStatefulStruct
	extends ExElement.Def.Abstract<ModelValueElement<SettableValue<?>, SettableValue<DynamicTypeStatefulTestStructure>>> implements
	ModelValueElement.CompiledSynth<SettableValue<?>, ModelValueElement<SettableValue<?>, SettableValue<DynamicTypeStatefulTestStructure>>> {
		private String theModelPath;
		private CompiledExpression theInternalState;
		private CompiledExpression theDerivedState;
		private ModelComponentId theInternalStateValue;

		public DynamicTypeStatefulStruct(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@Override
		public String getModelPath() {
			return theModelPath;
		}

		@Override
		public ModelType<SettableValue<?>> getModelType(CompiledExpressoEnv env) {
			return ModelTypes.Value;
		}

		@Override
		public CompiledExpression getElementValue() {
			return null;
		}

		@QonfigAttributeGetter("internal-state")
		public CompiledExpression getInternalState() {
			return theInternalState;
		}

		@QonfigAttributeGetter("derived-state")
		public CompiledExpression getDerivedState() {
			return theDerivedState;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			theModelPath = session.get(ModelValueElement.PATH_KEY, String.class);
			theInternalState = getAttributeExpression("internal-state", session);
			theDerivedState = getAttributeExpression("derived-state", session);
			ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
			theInternalStateValue = elModels.getElementValueModelId("internalState");
			elModels.satisfyElementValueType(theInternalStateValue, ModelTypes.Value,
				(interp, env) -> ((Interpreted<?>) interp).getInternalState().getType());
		}

		@Override
		public void prepareModelValue(ExpressoQIS session) throws QonfigInterpretationException {
		}

		@Override
		public Interpreted<?> interpretValue(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<T> extends
		ExElement.Interpreted.Abstract<ModelValueElement<SettableValue<?>, SettableValue<DynamicTypeStatefulTestStructure>>> implements
		ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<DynamicTypeStatefulTestStructure>, ModelValueElement<SettableValue<?>, SettableValue<DynamicTypeStatefulTestStructure>>> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<T>> theInternalState;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<T>> theDerivedState;

			Interpreted(DynamicTypeStatefulStruct def, ExElement.Interpreted<?> parent) {
				super(def, parent);
			}

			@Override
			public DynamicTypeStatefulStruct getDefinition() {
				return (DynamicTypeStatefulStruct) super.getDefinition();
			}

			@Override
			public InterpretedValueSynth<?, ?> getElementValue() {
				return null;
			}

			@Override
			public ModelInstanceType<SettableValue<?>, SettableValue<DynamicTypeStatefulTestStructure>> getType() {
				return ModelTypes.Value.forType(DynamicTypeStatefulTestStructure.class);
			}

			protected InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getInternalState() throws ExpressoInterpretationException {
				if (theInternalState == null)
					theInternalState = interpret(getDefinition().getInternalState(), ModelTypes.Value.<SettableValue<T>> anyAs());
				return theInternalState;
			}

			@Override
			public void updateValue(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				update(env);
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				theInternalState = null;
				super.doUpdate(env);
				System.out.println("Interpret " + getDefinition().getModelPath());
				// Satisfy the internalState value with the internalState container
				getAddOn(ExWithElementModel.Interpreted.class).satisfyElementValue("internalState", getInternalState());
				theDerivedState = interpret(getDefinition().getDerivedState(), ModelTypes.Value.<SettableValue<T>> anyAs());
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList.of(theInternalState, theDerivedState);
			}

			@Override
			public ModelValueInstantiator<SettableValue<DynamicTypeStatefulTestStructure>> instantiate() {
				return new Instantiator<>(getExpressoEnv().getModels().instantiate(), theInternalState.instantiate(),
					theDerivedState.instantiate());
			}

			@Override
			public ModelValueElement<SettableValue<?>, SettableValue<DynamicTypeStatefulTestStructure>> create() {
				return null;
			}
		}

		static class Instantiator<T> implements ModelValueInstantiator<SettableValue<DynamicTypeStatefulTestStructure>> {
			private final ModelInstantiator theLocalModel;
			private final ModelValueInstantiator<SettableValue<T>> theInternalState;
			private final ModelValueInstantiator<SettableValue<T>> theDerivedState;

			Instantiator(ModelInstantiator localModel, ModelValueInstantiator<SettableValue<T>> internalState,
				ModelValueInstantiator<SettableValue<T>> derivedState) {
				theLocalModel = localModel;
				theInternalState = internalState;
				theDerivedState = derivedState;
			}

			@Override
			public void instantiate() {
				theLocalModel.instantiate();
				theInternalState.instantiate();
				theDerivedState.instantiate();
			}

			@Override
			public SettableValue<DynamicTypeStatefulTestStructure> get(ModelSetInstance models)
				throws ModelInstantiationException, IllegalStateException {
				models = theLocalModel.wrap(models);
				DynamicTypeStatefulTestStructure structure = new DynamicTypeStatefulTestStructure(//
					theInternalState.get(models), theDerivedState.get(models));
				return SettableValue.of(DynamicTypeStatefulTestStructure.class, structure, "Not Settable");
			}

			@Override
			public SettableValue<DynamicTypeStatefulTestStructure> forModelCopy(SettableValue<DynamicTypeStatefulTestStructure> value,
				ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				return get(newModels);
			}
		}
	}

	@ExElementTraceable(toolkit = TESTING,
		qonfigType = "dynamic-type-stateful-struct2",
		interpretation = DynamicTypeStatefulStruct.Interpreted.class)
	static class DynamicTypeStatefulStruct2
	extends ExElement.Def.Abstract<ModelValueElement<SettableValue<?>, SettableValue<DynamicTypeStatefulTestStructure>>> implements
	ModelValueElement.CompiledSynth<SettableValue<?>, ModelValueElement<SettableValue<?>, SettableValue<DynamicTypeStatefulTestStructure>>> {
		private String theModelPath;
		private CompiledExpression theInternalState;
		private CompiledExpression theDerivedState;

		public DynamicTypeStatefulStruct2(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@Override
		public String getModelPath() {
			return theModelPath;
		}

		@Override
		public ModelType<SettableValue<?>> getModelType(CompiledExpressoEnv env) {
			return ModelTypes.Value;
		}

		@Override
		public CompiledExpression getElementValue() {
			return null;
		}

		@QonfigAttributeGetter("internal-state")
		public CompiledExpression getInternalState() {
			return theInternalState;
		}

		@QonfigAttributeGetter("derived-state")
		public CompiledExpression getDerivedState() {
			return theDerivedState;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			theModelPath = session.get(ModelValueElement.PATH_KEY, String.class);
			theInternalState = getAttributeExpression("internal-state", session);
			theDerivedState = getAttributeExpression("derived-state", session);
		}

		@Override
		public void prepareModelValue(ExpressoQIS session) throws QonfigInterpretationException {
		}

		@Override
		public Interpreted<?> interpretValue(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<T> extends
		ExElement.Interpreted.Abstract<ModelValueElement<SettableValue<?>, SettableValue<DynamicTypeStatefulTestStructure>>> implements
		ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<DynamicTypeStatefulTestStructure>, ModelValueElement<SettableValue<?>, SettableValue<DynamicTypeStatefulTestStructure>>> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<T>> theInternalState;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<T>> theDerivedState;

			Interpreted(DynamicTypeStatefulStruct2 def, ExElement.Interpreted<?> parent) {
				super(def, parent);
			}

			@Override
			public DynamicTypeStatefulStruct2 getDefinition() {
				return (DynamicTypeStatefulStruct2) super.getDefinition();
			}

			@Override
			public InterpretedValueSynth<?, ?> getElementValue() {
				return null;
			}

			@Override
			public ModelInstanceType<SettableValue<?>, SettableValue<DynamicTypeStatefulTestStructure>> getType() {
				return ModelTypes.Value.forType(DynamicTypeStatefulTestStructure.class);
			}

			@Override
			public void updateValue(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				update(env);
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);
				try {
					theInternalState = getExpressoEnv().getModels().getValue("internalState", ModelTypes.Value.<SettableValue<T>> anyAs(),
						env);
				} catch (ModelException | TypeConversionException e) {
					throw new ExpressoInterpretationException(e.getMessage(), reporting().getFileLocation().getPosition(0), 0, e);
				}
				theDerivedState = interpret(getDefinition().getDerivedState(), ModelTypes.Value.<SettableValue<T>> anyAs());
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList.of(theInternalState, theDerivedState);
			}

			@Override
			public ModelValueInstantiator<SettableValue<DynamicTypeStatefulTestStructure>> instantiate() {
				return new Instantiator<>(getExpressoEnv().getModels().instantiate(), theInternalState.instantiate(),
					theDerivedState.instantiate());
			}

			@Override
			public ModelValueElement<SettableValue<?>, SettableValue<DynamicTypeStatefulTestStructure>> create() {
				return null;
			}
		}

		static class Instantiator<T> implements ModelValueInstantiator<SettableValue<DynamicTypeStatefulTestStructure>> {
			private final ModelInstantiator theLocalModel;
			private final ModelValueInstantiator<SettableValue<T>> theInternalState;
			private final ModelValueInstantiator<SettableValue<T>> theDerivedState;

			Instantiator(ModelInstantiator localModel, ModelValueInstantiator<SettableValue<T>> internalState,
				ModelValueInstantiator<SettableValue<T>> derivedState) {
				theLocalModel = localModel;
				theInternalState = internalState;
				theDerivedState = derivedState;
			}

			@Override
			public void instantiate() {
				theLocalModel.instantiate();
				theInternalState.instantiate();
				theDerivedState.instantiate();
			}

			@Override
			public SettableValue<DynamicTypeStatefulTestStructure> get(ModelSetInstance models)
				throws ModelInstantiationException, IllegalStateException {
				models = theLocalModel.wrap(models);
				DynamicTypeStatefulTestStructure structure = new DynamicTypeStatefulTestStructure(//
					theInternalState.get(models), theDerivedState.get(models));
				return SettableValue.of(DynamicTypeStatefulTestStructure.class, structure, "Not Settable");
			}

			@Override
			public SettableValue<DynamicTypeStatefulTestStructure> forModelCopy(SettableValue<DynamicTypeStatefulTestStructure> value,
				ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				return get(newModels);
			}
		}
	}
}
