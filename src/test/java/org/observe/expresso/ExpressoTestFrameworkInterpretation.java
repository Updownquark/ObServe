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
import org.observe.expresso.qonfig.ExElementTraceable;
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

	/** {@link #TOOLKIT_NAME} and {@link #VERSION} */
	public static final String TESTING = "Expresso-Testing v0.1";

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
		.createWith("testing", ExpressoTesting.Def.class, ExElement.creator(ExpressoTesting.Def::new));
		interpreter.createWith("test", ExpressoTesting.ExpressoTest.Def.class, ExElement.creator(ExpressoTesting.ExpressoTest.Def::new));
		interpreter.createWith("test-action", ExpressoTesting.TestAction.class, ExElement.creator(ExpressoTesting.TestAction::new));
		interpreter.createWith("watch", ModelValueElement.CompiledSynth.class, ExElement.creator(WatchedValue::new));
		return interpreter;
	}

	@ExElementTraceable(toolkit = TESTING, qonfigType = "watch", interpretation = WatchedValue.Interpreted.class)
	static class WatchedValue extends ExElement.Def.Abstract<ModelValueElement<?>>
	implements ModelValueElement.CompiledSynth<SettableValue<?>, ModelValueElement<?>> {
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
			theModelPath = session.get(ModelValueElement.PATH_KEY, String.class);
			theValue = getValueExpression(session);
		}

		@Override
		public void prepareModelValue(ExpressoQIS session) {
		}

		@Override
		public Interpreted<?> interpretValue(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<T> extends ExElement.Interpreted.Abstract<ModelValueElement<SettableValue<T>>>
		implements ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<T>, ModelValueElement<SettableValue<T>>> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<T>> theValue;

			public Interpreted(WatchedValue definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
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
				theValue = interpret(getDefinition().getElementValue(), ModelTypes.Value.<SettableValue<T>> anyAs());
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return theValue.getComponents();
			}

			@Override
			public ModelValueElement<SettableValue<T>> create() throws ModelInstantiationException {
				return new Instantiator<>(this);
			}
		}

		static class Instantiator<T> extends ModelValueElement.Abstract<SettableValue<T>> {
			Instantiator(WatchedValue.Interpreted<T> interpreted) throws ModelInstantiationException {
				super(interpreted);
			}

			@Override
			public ModelValueInstantiator<SettableValue<T>> getElementValue() {
				return (ModelValueInstantiator<SettableValue<T>>) super.getElementValue();
			}

			@Override
			public SettableValue<T> get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
				SettableValue<T> value = getElementValue().get(models);
				SettableValue<T> copy = SettableValue.<T> build().withValue(value.get()).build();
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
