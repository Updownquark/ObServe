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
 * @param <MV> The value type of the result
 */
public abstract class ConfigModelValue<T, MV> extends ModelValueElement.Abstract<MV> {
	/** Session key in which to store the {@link ObservableConfigFormatSet} */
	public static final String FORMAT_SET_KEY = "Expresso.Config.FormatSet";

	/**
	 * Definition for a {@link ConfigModelValue}
	 *
	 * @param <M> The model type of the value
	 */
	@ExElementTraceable(toolkit = ExpressoConfigV0_1.CONFIG,
		qonfigType = "config-model-value",
		interpretation = Interpreted.class,
		instance = ConfigModelValue.class)
	public interface Def<M> extends ModelValueElement.CompiledSynth<M, ConfigModelValue<?, ?>> {
		/** @return The type of the model value, if specified */
		VariableType getValueType();

		/** @return The path in the config to store data for the model value */
		@QonfigAttributeGetter("config-path")
		ObservableConfigPath getConfigPath();

		/** @return The model value for the config format specified for this model value */
		@QonfigAttributeGetter("format")
		CompiledExpression getFormat();

		@Override
		Interpreted<?, M, ?> interpretValue(ExElement.Interpreted<?> interpreted);

		/**
		 * Abstract implementation for a {@link ConfigModelValue} definition
		 *
		 * @param <M> The model type of the value
		 */
		public abstract class Abstract<M> extends ModelValueElement.Def.Abstract<M, ConfigModelValue<?, ?>> implements Def<M> {
			private VariableType theValueType;
			private ObservableConfigPath theConfigPath;
			private CompiledExpression theFormat;

			/**
			 * @param parent The parent element for this element
			 * @param qonfigType The Qonfig type of this element
			 * @param modelType The model type of the value
			 */
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

	/**
	 * Interpretation for a {@link ConfigModelValue}
	 *
	 * @param <T> The type of the value
	 * @param <M> The model type of the value
	 * @param <MV> The instance type of the model value
	 */
	public interface Interpreted<T, M, MV extends M> extends ModelValueElement.InterpretedSynth<M, MV, ConfigModelValue<T, MV>> {
		@Override
		Def<M> getDefinition();

		@Override
		ConfigModelValue<T, MV> create() throws ModelInstantiationException;

		/** @return The type of the value */
		TypeToken<T> getValueType();

		/**
		 * Abstract implementation for a {@link ConfigModelValue} interpretation
		 *
		 * @param <T> The type of the value
		 * @param <M> The model type of the value
		 * @param <MV> The instance type of the model value
		 */
		public abstract class Abstract<T, M, MV extends M> extends ModelValueElement.Interpreted.Abstract<M, MV, ConfigModelValue<T, MV>>
		implements Interpreted<T, M, MV> {
			private TypeToken<T> theValueType;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<ObservableConfig>> theConfigValue;
			private ObservableConfigPath theConfigPath;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<ObservableConfigFormat<T>>> theFormat;
			private ObservableConfigFormatSet theFormatSet;

			/**
			 * @param definition The definition to interpret
			 * @param parent The parent element for this interpreted element
			 */
			protected Abstract(Def<M> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Def<M> getDefinition() {
				return (Def<M>) super.getDefinition();
			}

			/**
			 * @return The interpretation of the {@link ObservableConfig} model value that this interpretation will derive its value from
			 */
			public InterpretedValueSynth<SettableValue<?>, SettableValue<ObservableConfig>> getConfigValue() {
				return theConfigValue;
			}

			/** @return The path in the config to store data for the model value */
			public ObservableConfigPath getConfigPath() {
				return theConfigPath;
			}

			/** @return The interpretation for the config format specified for the model value */
			public InterpretedValueSynth<SettableValue<?>, SettableValue<ObservableConfigFormat<T>>> getFormat() {
				return theFormat;
			}

			/** @return The format set to use to get the format for the value if it is not specified */
			public ObservableConfigFormatSet getFormatSet() {
				return theFormatSet;
			}

			@Override
			public TypeToken<T> getValueType() {
				return theValueType;
			}

			/**
			 * @param others Other model values that are not part of this abstract implementation to include in the returned list
			 * @return All model values that this interpretation will use to create its model value
			 */
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

	private final ModelValueInstantiator<SettableValue<ObservableConfig>> theConfigValue;
	private final TypeToken<T> theValueType;
	private final ObservableConfigPath theConfigPath;
	private final ModelValueInstantiator<SettableValue<ObservableConfigFormat<T>>> theFormat;
	private final ObservableConfigFormatSet theFormatSet;

	/**
	 * @param interpreted The interpretation that created this value
	 * @param configValue The instantiator for the {@link ObservableConfig} that this instantiator will derive its value from
	 * @param valueType The value type of the model value
	 * @param configPath The path in the config to store data for the model value
	 * @param format The instantiator for the config format specified for the model value
	 * @param formatSet The format set to use to get the format for the value if it is not specified
	 * @throws ModelInstantiationException If any model values fail to initialize
	 */
	protected ConfigModelValue(ConfigModelValue.Interpreted<T, ?, MV> interpreted,
		ModelValueInstantiator<SettableValue<ObservableConfig>> configValue, TypeToken<T> valueType, ObservableConfigPath configPath,
		ModelValueInstantiator<SettableValue<ObservableConfigFormat<T>>> format, ObservableConfigFormatSet formatSet)
			throws ModelInstantiationException {
		super(interpreted);
		theConfigValue = configValue;
		theValueType = valueType;
		theConfigPath = configPath;
		theFormat = format;
		theFormatSet = formatSet;
	}

	@Override
	public void instantiate() throws ModelInstantiationException {
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
