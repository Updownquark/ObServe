package org.observe.expresso;

import java.util.List;
import java.util.Set;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExpressoBaseV0_1;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.ModelValueElement;
import org.qommons.QommonsUtils;
import org.qommons.Version;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretation;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreterCore.Builder;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.SpecialSession;

/** Interpretation for types defined by the Expresso Test Framework */
public class ExpressoTestFrameworkInterpretation implements QonfigInterpretation {
	/** The name of the test toolkit */
	public static final String TOOLKIT_NAME = "Expresso-Testing";
	/** The version of the test toolkit */
	public static final Version VERSION = new Version(0, 1, 0);

	@Override
	public Set<Class<? extends SpecialSession<?>>> getExpectedAPIs() {
		return QommonsUtils.unmodifiableDistinctCopy(ExpressoQIS.class);
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
		.createWith("testing", ExpressoTesting.class, ExElement.creator(ExpressoTesting::new));
		interpreter.createWith("test", ExpressoTesting.ExpressoTest.Def.class, ExElement.creator(ExpressoTesting.ExpressoTest.Def::new));
		interpreter.createWith("test-action", ExpressoTesting.TestAction.class, ExElement.creator(ExpressoTesting.TestAction::new));
		interpreter.createWith("watch", ModelValueElement.CompiledSynth.class, ExElement.creator(WatchedValue::new));
		return interpreter;
	}

	static class WatchedValue extends ExElement.Def.Abstract<ModelValueElement<SettableValue<?>, ?>>
	implements ModelValueElement.CompiledSynth<SettableValue<?>, ModelValueElement<SettableValue<?>, ?>> {
		private String theModelPath;
		private CompiledExpression theValue;

		public WatchedValue(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
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
			return theValue;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			theModelPath = session.get(ExpressoBaseV0_1.PATH_KEY, String.class);
			theValue = session.getValueExpression();
		}

		@Override
		public void prepareModelValue(ExpressoQIS session) {
		}

		@Override
		public Interpreted<?> interpret() {
			return new Interpreted<>(this);
		}

		static class Interpreted<T> extends ExElement.Interpreted.Abstract<ModelValueElement<SettableValue<?>, SettableValue<T>>> implements
		ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<T>, ModelValueElement<SettableValue<?>, SettableValue<T>>> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<T>> theValue;

			public Interpreted(WatchedValue definition) {
				super(definition, null);
			}

			@Override
			public WatchedValue getDefinition() {
				return (WatchedValue) super.getDefinition();
			}

			@Override
			public Interpreted<T> setParentElement(ExElement.Interpreted<?> parent) {
				super.setParentElement(parent);
				return this;
			}

			@Override
			public ModelInstanceType<SettableValue<?>, SettableValue<T>> getType() {
				return theValue.getType();
			}

			@Override
			public InterpretedValueSynth<?, ?> getElementValue() {
				return theValue;
			}

			@Override
			public void updateValue(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				update(env);
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);
				theValue = getDefinition().getElementValue().interpret(ModelTypes.Value.<SettableValue<T>> anyAs(), getExpressoEnv());
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return theValue.getComponents();
			}

			@Override
			public ModelValueInstantiator<SettableValue<T>> instantiate() {
				return new Instantiator<>(theValue.instantiate());
			}

			@Override
			public ModelValueElement<SettableValue<?>, SettableValue<T>> create() {
				return null;
			}
		}

		static class Instantiator<T> implements ModelValueInstantiator<SettableValue<T>> {
			private final ModelValueInstantiator<SettableValue<T>> theSource;

			Instantiator(ModelValueInstantiator<SettableValue<T>> source) {
				theSource = source;
			}

			@Override
			public void instantiate() {
				theSource.instantiate();
			}

			@Override
			public SettableValue<T> get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
				SettableValue<T> value = theSource.get(models);
				SettableValue<T> copy = SettableValue.build(value.getType()).withValue(value.get()).build();
				value.noInitChanges().takeUntil(models.getUntil()).act(evt -> copy.set(evt.getNewValue(), evt));
				return copy.disableWith(ObservableValue.of("A watched value cannot be modified"));
			}

			@Override
			public SettableValue<T> forModelCopy(SettableValue<T> value, ModelSetInstance sourceModels, ModelSetInstance newModels)
				throws ModelInstantiationException {
				return get(newModels);
			}
		}
	}
}
