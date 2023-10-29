package org.observe.expresso.qonfig;

import java.util.ArrayList;
import java.util.List;

import org.observe.SettableValue;
import org.observe.config.ObservableConfig;
import org.observe.config.ObservableConfigFormat;
import org.observe.config.ObservableConfigFormatSet;
import org.observe.config.ObservableConfigPath;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.TypeConversionException;
import org.observe.expresso.VariableType;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreterCore.QonfigValueCreator;

import com.google.common.reflect.TypeToken;

/**
 * Provided to
 * {@link org.qommons.config.QonfigInterpreterCore.Builder#createWith(org.qommons.config.QonfigElementOrAddOn, Class, QonfigValueCreator)}
 * to support using an element type as a child of a &lt;config-model> element.
 *
 * @param <T> The element type of the result
 * @param <M> The model type of the result
 * @param <MV> The value type of the result
 */
public interface ConfigModelValue<T, M, MV extends M> extends ModelValueElement<M, MV> {
	public static final String FORMAT_SET_KEY = "Expresso.Config.FormatSet";

	@ExElementTraceable(toolkit = ExpressoConfigV0_1.CONFIG,
		qonfigType = "config-model-value",
		interpretation = Interpreted.class,
		instance = ConfigModelValue.class)
	public interface Def<M> extends ModelValueElement.CompiledSynth<M, ConfigModelValue<?, M, ?>> {
		VariableType getValueType();

		@QonfigAttributeGetter("config-path")
		ObservableConfigPath getConfigPath();

		@QonfigAttributeGetter("format")
		CompiledExpression getFormat();

		@Override
		Interpreted<?, M, ?> interpret();

		public abstract class Abstract<M> extends ModelValueElement.Def.Abstract<M, ConfigModelValue<?, M, ?>> implements Def<M> {
			private VariableType theValueType;
			private ObservableConfigPath theConfigPath;
			private CompiledExpression theFormat;

			protected Abstract(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType, ModelType<M> modelType) {
				super(parent, qonfigType, modelType);
			}

			@Override
			public VariableType getValueType() {
				return theValueType;
			}

			@Override
			public ObservableConfigPath getConfigPath() {
				return theConfigPath;
			}

			@Override
			public CompiledExpression getFormat() {
				return theFormat;
			}

			@Override
			protected void doPrepare(ExpressoQIS session) throws QonfigInterpretationException {
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session);

				session = session.asElement(ExpressoConfigV0_1.CONFIG, "config-model-value");
				theValueType = getAddOn(ExTyped.Def.class).getValueType();
				String configPath = session.getAttributeText("config-path");
				if (configPath != null)
					theConfigPath = ObservableConfigPath.create(configPath);
				else
					theConfigPath = ObservableConfigPath.create(getAddOn(ExNamed.Def.class).getName());
				theFormat = getAttributeExpression("format", session);
			}
		}
	}

	public interface Interpreted<T, M, MV extends M> extends ModelValueElement.InterpretedSynth<M, MV, ConfigModelValue<T, M, MV>> {
		@Override
		Def<M> getDefinition();

		@Override
		default ConfigModelValue<T, M, MV> create() {
			return null;
		}

		TypeToken<T> getValueType();

		public abstract class Abstract<T, M, MV extends M> extends ModelValueElement.Interpreted.Abstract<M, MV, ConfigModelValue<T, M, MV>>
		implements Interpreted<T, M, MV> {
			private TypeToken<T> theValueType;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<ObservableConfig>> theConfigValue;
			private ObservableConfigPath theConfigPath;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<ObservableConfigFormat<T>>> theFormat;
			private ObservableConfigFormatSet theFormatSet;

			protected Abstract(Def<M> definition) {
				super(definition, null);
			}

			@Override
			public Def<M> getDefinition() {
				return (Def<M>) super.getDefinition();
			}

			@Override
			public Interpreted.Abstract<T, M, MV> setParentElement(ExElement.Interpreted<?> parent) {
				super.setParentElement(parent);
				return this;
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<ObservableConfig>> getConfigValue() {
				return theConfigValue;
			}

			public ObservableConfigPath getConfigPath() {
				return theConfigPath;
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<ObservableConfigFormat<T>>> getFormat() {
				return theFormat;
			}

			public ObservableConfigFormatSet getFormatSet() {
				return theFormatSet;
			}

			@Override
			public TypeToken<T> getValueType() {
				return theValueType;
			}

			protected List<? extends InterpretedValueSynth<?, ?>> getComponents(InterpretedValueSynth<?, ?>... others) {
				List<InterpretedValueSynth<?, ?>> components = new ArrayList<>();
				components.add(theConfigValue);
				if (theFormat != null)
					components.add(theFormat);
				for (InterpretedValueSynth<?, ?> other : others) {
					if (other != null)
						components.add(other);
				}
				return components;
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);
				theValueType = getAddOn(ExTyped.Interpreted.class).getValueType();
				try {
					theConfigValue = env.getModels().getValue(ExpressoConfigV0_1.CONFIG_NAME,
						ModelTypes.Value.forType(ObservableConfig.class), env);
				} catch (ModelException | TypeConversionException e) {
					throw new ExpressoInterpretationException("Could not retrieve " + ObservableConfig.class.getSimpleName() + " value",
						reporting().getPosition(), 0, e);
				}
				theConfigPath = getDefinition().getConfigPath();
				theFormat = interpret(getDefinition().getFormat(), ModelTypes.Value.forType(
					TypeTokens.get().keyFor(ObservableConfigFormat.class).<ObservableConfigFormat<T>> parameterized(getValueType())));
				theFormatSet = theFormat == null ? env.get(FORMAT_SET_KEY, ObservableConfigFormatSet.class) : null;
			}
		}
	}

	public static abstract class Instantiator<T, MV> implements ModelValueInstantiator<MV> {
		private final ModelValueInstantiator<SettableValue<ObservableConfig>> theConfigValue;
		private final TypeToken<T> theValueType;
		private final ObservableConfigPath theConfigPath;
		private final ModelValueInstantiator<SettableValue<ObservableConfigFormat<T>>> theFormat;
		private final ObservableConfigFormatSet theFormatSet;

		protected Instantiator(ModelValueInstantiator<SettableValue<ObservableConfig>> configValue, TypeToken<T> valueType,
			ObservableConfigPath configPath, ModelValueInstantiator<SettableValue<ObservableConfigFormat<T>>> format,
			ObservableConfigFormatSet formatSet) {
			theConfigValue = configValue;
			theValueType = valueType;
			theConfigPath = configPath;
			theFormat = format;
			theFormatSet = formatSet;
		}

		@Override
		public void instantiate() {
			theConfigValue.instantiate();
			if (theFormat != null)
				theFormat.instantiate();
		}

		@Override
		public MV get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
			ObservableConfig config = theConfigValue.get(models).get();
			ObservableConfig.ObservableConfigValueBuilder<T> builder = config.asValue(theValueType);
			builder = builder.at(theConfigPath);
			if (theFormat != null) {
				ObservableConfigFormat<T> format = theFormat.get(models).get();
				builder.withFormat(format);
			} else
				builder.withFormatSet(theFormatSet);
			return create(builder, models);
		}

		/**
		 * Creates the value
		 *
		 * @param config The config value builder to use to build the structure
		 * @param msi The model set to use to build the structure
		 * @return The created value
		 * @throws ModelInstantiationException If the value could not be instantiated
		 */
		public abstract MV create(ObservableConfig.ObservableConfigValueBuilder<T> config, ModelSetInstance msi)
			throws ModelInstantiationException;

		@Override
		public MV forModelCopy(MV value, ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
			return value; // Same value, because the config doesn't change with model copy
		}
	}
}