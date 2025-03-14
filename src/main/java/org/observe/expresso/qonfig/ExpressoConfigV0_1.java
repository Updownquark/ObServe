package org.observe.expresso.qonfig;

import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.SettableValue;
import org.observe.Subscription;
import org.observe.assoc.ObservableMap;
import org.observe.config.ObservableConfig;
import org.observe.config.ObservableConfig.ObservableConfigMapBuilder;
import org.observe.config.ObservableConfig.ObservableConfigValueBuilder;
import org.observe.config.ObservableConfigFormat;
import org.observe.config.ObservableConfigFormatSet;
import org.observe.config.ObservableValueSet;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.ObservableModelSet.ModelInstantiator;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.VariableType;
import org.observe.expresso.qonfig.ExAddOn.Void;
import org.observe.expresso.qonfig.ExElement.Def;
import org.observe.expresso.qonfig.ModelValueElement.InterpretedSynth;
import org.observe.util.EntityReflector;
import org.observe.util.TypeTokens;
import org.qommons.ArrayUtils;
import org.qommons.Causable;
import org.qommons.Identifiable;
import org.qommons.LambdaUtils;
import org.qommons.QommonsUtils;
import org.qommons.ThreadConstraint;
import org.qommons.TimeUtils;
import org.qommons.Transaction;
import org.qommons.Version;
import org.qommons.collect.BetterList;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretation;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreterCore;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.SpecialSession;
import org.qommons.io.ArchiveEnabledFileSource;
import org.qommons.io.BetterFile;
import org.qommons.io.BetterFile.FileDataSource;
import org.qommons.io.ErrorReporting;
import org.qommons.io.Format;
import org.qommons.io.LocatedPositionedContent;
import org.qommons.io.NativeFileSource;
import org.qommons.io.SpinnerFormat;

import com.google.common.reflect.TypeToken;

/** Qonfig Interpretation for the ExpressoConfigV0_1 API */
public class ExpressoConfigV0_1 implements QonfigInterpretation {
	/** The name of the expresso config toolkit */
	public static final String NAME = "Expresso-Config";

	/** The version of this implementation of the expresso config toolkit */
	public static final Version VERSION = new Version(0, 1, 0);

	/** {@link #NAME} and {@link #VERSION} combined */
	public static final String CONFIG = "Expresso-Config v0.1";

	/** The name of the model value to store the {@link ObservableConfig} in the model */
	public static final String CONFIG_NAME = "$CONFIG$";

	/**
	 * Validation for a format, prevents a text sequence from being assigned to a value based on some condition
	 *
	 * @param <T> The type of the value to validate
	 */
	public interface FormatValidation<T> {
		/**
		 * Definition for a {@link FormatValidation}
		 *
		 * @param <E> The type of the instantiator for the format
		 */
		public interface Def<E extends Instantiator<?, ?>> extends ExElement.Def<E> {
			/**
			 * @param parent The parent element for the interpreted validation
			 * @return The interpreted validation
			 */
			Interpreted<?, ? extends E> interpret(ExElement.Interpreted<?> parent);
		}

		/**
		 * Interpretation for a {@link FormatValidation}
		 *
		 * @param <T> The type of the value to validate
		 * @param <E> The type of the instantiator for the format
		 */
		public interface Interpreted<T, E extends Instantiator<T, ?>> extends ExElement.Interpreted<E> {
			/**
			 * @param env The expresso environment to use to interpret expressions
			 * @param valueType The type of the value being validated
			 * @throws ExpressoInterpretationException If this interpretation could not be updated
			 */
			void updateFormat(InterpretedExpressoEnv env, TypeToken<T> valueType) throws ExpressoInterpretationException;

			/** @return All model values that this format will use to do validation */
			List<? extends InterpretedValueSynth<?, ?>> getComponents();

			/** @return The instantiator for the validation */
			Instantiator<T, ?> create();
		}

		/**
		 * Instantiator for a {@link FormatValidation}
		 *
		 * @param <T> The type of the value to validate
		 * @param <V> The sub-type of {@link FormatValidation} to create
		 */
		public interface Instantiator<T, V extends FormatValidation<T>> extends ExElement, ModelValueInstantiator<V> {
			@Override
			default void instantiate() throws ModelInstantiationException {
				instantiated();
			}
		}

		/**
		 * @param value The parsed value to test
		 * @param text The text that the value was parsed from
		 * @return Null if the value is acceptable to this validation, or a user-readable message why it is not
		 */
		String test(T value, CharSequence text);
	}

	@Override
	public Set<Class<? extends SpecialSession<?>>> getExpectedAPIs() {
		return Collections.singleton(ExpressoQIS.class);
	}

	@Override
	public String getToolkitName() {
		return NAME;
	}

	@Override
	public Version getVersion() {
		return VERSION;
	}

	@Override
	public void init(QonfigToolkit toolkit) {
	}

	@Override
	public QonfigInterpreterCore.Builder configureInterpreter(QonfigInterpreterCore.Builder interpreter) {
		configureConfigModels(interpreter);
		configureFormats(interpreter);
		return interpreter;
	}

	void configureConfigModels(QonfigInterpreterCore.Builder interpreter) {
		interpreter.createWith("config", ObservableModelElement.ConfigModelElement.Def.class,
			ExElement.creator(ObservableModelElement.ConfigModelElement.Def::new));
		interpreter.createWith("value", ConfigModelValue.Def.class, ExElement.creator(ConfigValue::new));
		interpreter.createWith("value-set", ConfigModelValue.Def.class, ExElement.creator(ConfigValueSet::new));
		// TODO list, sorted-list, set, sorted-set
		interpreter.createWith(ConfigMap.CONFIG_MAP, ConfigMap.Def.class, ExAddOn.creator(ConfigMap.Def::new));
		interpreter.createWith("map", ConfigModelValue.Def.class, ExElement.creator(ExConfigMap::new));
		// TODO sorted-map, multi-map, sorted-multi-map
	}

	@ExElementTraceable(toolkit = CONFIG,
		qonfigType = "config-value",
		interpretation = ConfigValue.Interpreted.class,
		instance = ConfigValue.Instantiator.class)
	static class ConfigValue extends ConfigModelValue.Def.Abstract<SettableValue<?>> {
		private CompiledExpression theDefaultValue;

		public ConfigValue(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type, ModelTypes.Value);
		}

		@QonfigAttributeGetter("default")
		public CompiledExpression getDefaultValue() {
			return theDefaultValue;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session.asElement(CONFIG, "config-model-value"));
			theDefaultValue = getAttributeExpression("default", session.asElement(CONFIG, "config-value"));
		}

		@Override
		public Interpreted<?> interpretValue(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<T> extends ConfigModelValue.Interpreted.Abstract<T, SettableValue<?>, SettableValue<T>> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<T>> theDefaultValue;

			Interpreted(ConfigValue definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public ConfigValue getDefinition() {
				return (ConfigValue) super.getDefinition();
			}

			@Override
			protected ModelInstanceType<SettableValue<?>, SettableValue<T>> getTargetType() {
				return ModelTypes.Value.forType(getValueType());
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getDefaultValue() {
				return theDefaultValue;
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);
				theDefaultValue = interpret(getDefinition().getDefaultValue(), ModelTypes.Value.forType(getValueType()));
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return getComponents(theDefaultValue);
			}

			@Override
			public ConfigModelValue<T, SettableValue<T>> create() throws ModelInstantiationException {
				return new Instantiator<>(this);
			}
		}

		static class Instantiator<T> extends ConfigModelValue<T, SettableValue<T>> {
			private final ModelValueInstantiator<SettableValue<T>> theDefaultValue;

			Instantiator(ConfigValue.Interpreted<T> interpreted) throws ModelInstantiationException {
				super(interpreted, interpreted.getConfigValue().instantiate(), interpreted.getValueType(), interpreted.getConfigPath(),
					interpreted.getFormat() == null ? null : interpreted.getFormat().instantiate(), interpreted.getFormatSet());
				theDefaultValue = interpreted.getDefaultValue() == null ? null : interpreted.getDefaultValue().instantiate();
			}

			public ModelValueInstantiator<SettableValue<T>> getDefaultValue() {
				return theDefaultValue;
			}

			@Override
			public void instantiate() throws ModelInstantiationException {
				super.instantiate();
				if (theDefaultValue != null)
					theDefaultValue.instantiate();
			}

			@Override
			public SettableValue<T> create(ObservableConfigValueBuilder<T> config, ModelSetInstance msi)
				throws ModelInstantiationException {
				SettableValue<T> value;
				try {
					value = config.buildValue(null);
				} catch (IllegalArgumentException e) {
					reporting().error("No default format available for type " + config.getType() + ". Specify a format.", e);
					String uModMsg = reporting().getPosition().toShortString() + ": Unmodifiable";
					value = SettableValue.of(null, uModMsg);
				}
				if (theDefaultValue != null && config.getConfig().getChild(config.getPath(), false, null) == null)
					value.set(theDefaultValue.get(msi).get(), null);
				return value;
			}
		}
	}

	static class ConfigValueSet extends ConfigModelValue.Def.Abstract<ObservableValueSet<?>> {
		public ConfigValueSet(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, ModelTypes.ValueSet);
		}

		@Override
		public Interpreted<?> interpretValue(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<T> extends ConfigModelValue.Interpreted.Abstract<T, ObservableValueSet<?>, ObservableValueSet<T>> {
			public Interpreted(ConfigValueSet definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return Collections.emptyList();
			}

			@Override
			protected ModelInstanceType<ObservableValueSet<?>, ObservableValueSet<T>> getTargetType() {
				return ModelTypes.ValueSet.forType(getValueType());
			}

			@Override
			public ConfigModelValue<T, ObservableValueSet<T>> create() throws ModelInstantiationException {
				return new Instantiator<>(this);
			}
		}

		static class Instantiator<T> extends ConfigModelValue<T, ObservableValueSet<T>> {
			public Instantiator(ConfigValueSet.Interpreted<T> interpreted) throws ModelInstantiationException {
				super(interpreted, interpreted.getConfigValue().instantiate(), interpreted.getValueType(), interpreted.getConfigPath(),
					interpreted.getFormat() == null ? null : interpreted.getFormat().instantiate(), interpreted.getFormatSet());
			}

			@Override
			public ObservableValueSet<T> create(ObservableConfigValueBuilder<T> config, ModelSetInstance msi)
				throws ModelInstantiationException {
				return config.buildEntitySet(null);
			}
		}
	}

	/**
	 * Add on for map model values in &lt;config> models
	 *
	 * @param <K> The key type of the map
	 */
	public static class ConfigMap<K> extends ExMapModelValue<K> {
		/** The XML name of this type */
		public static final String CONFIG_MAP = "config-map";

		/** Definition for a {@link ConfigMap} */
		@ExElementTraceable(toolkit = CONFIG, qonfigType = CONFIG_MAP, interpretation = Interpreted.class, instance = ConfigMap.class)
		public static class Def extends ExMapModelValue.Def<ConfigMap<?>> {
			private CompiledExpression theKeyFormat;

			/**
			 * @param type The Qonfig type of this add-on
			 * @param element The map element being affected
			 */
			public Def(QonfigAddOn type, ExElement.Def<? extends ExElement> element) {
				super(type, element);
			}

			/** @return The model value for the format specified for keys in the map */
			@QonfigAttributeGetter("key-format")
			public CompiledExpression getKeyFormat() {
				return theKeyFormat;
			}

			@Override
			public void update(ExpressoQIS session, ExElement.Def<? extends ExElement> element) throws QonfigInterpretationException {
				super.update(session, element);

				theKeyFormat = element.getAttributeExpression("key-format", session);
			}

			@Override
			public <E2 extends ExElement> Interpreted<?> interpret(ExElement.Interpreted<E2> element) {
				return new Interpreted<>(this, element);
			}
		}

		/**
		 * Interpretation for a {@link ConfigMap}
		 *
		 * @param <K> The key type of the map
		 */
		public static class Interpreted<K> extends ExMapModelValue.Interpreted<K, ConfigMap<K>> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<ObservableConfigFormat<K>>> theKeyFormat;

			Interpreted(Def definition, ExElement.Interpreted<?> element) {
				super(definition, element);
			}

			@Override
			public Def getDefinition() {
				return (Def) super.getDefinition();
			}

			/** @return The model value for the format specified for keys in the map */
			public InterpretedValueSynth<SettableValue<?>, SettableValue<ObservableConfigFormat<K>>> getKeyFormat() {
				return theKeyFormat;
			}

			@Override
			public Class<ConfigMap<K>> getInstanceType() {
				return (Class<ConfigMap<K>>) (Class<?>) ConfigMap.class;
			}

			@Override
			public void update(ExElement.Interpreted<?> element) throws ExpressoInterpretationException {
				super.update(element);

				theKeyFormat = getElement().interpret(getDefinition().getKeyFormat(), ModelTypes.Value.forType(
					TypeTokens.get().keyFor(ObservableConfigFormat.class).<ObservableConfigFormat<K>> parameterized(getKeyType())));
			}

			@Override
			public ConfigMap<K> create(ExElement element) {
				return new ConfigMap<>(element);
			}
		}

		private ModelValueInstantiator<SettableValue<ObservableConfigFormat<K>>> theKeyFormat;

		ConfigMap(ExElement element) {
			super(element);
		}

		/** @return The model value for the format specified for keys in the map */
		public ModelValueInstantiator<SettableValue<ObservableConfigFormat<K>>> getKeyFormat() {
			return theKeyFormat;
		}

		@Override
		public void update(ExAddOn.Interpreted<? super ExElement, ?> interpreted, ExElement element) throws ModelInstantiationException {
			super.update(interpreted, element);

			Interpreted<K> myInterpreted = (Interpreted<K>) interpreted;
			theKeyFormat = myInterpreted.getKeyFormat() == null ? null : myInterpreted.getKeyFormat().instantiate();
		}

		@Override
		public Class<Interpreted<?>> getInterpretationType() {
			return (Class<Interpreted<?>>) (Class<?>) Interpreted.class;
		}
	}

	static abstract class AbstractConfigMap<M> extends ConfigModelValue.Def.Abstract<M> {
		private VariableType theKeyType;
		private CompiledExpression theKeyFormat;

		protected AbstractConfigMap(Def<?> parent, QonfigElementOrAddOn qonfigType, ModelType.DoubleTyped<M> modelType) {
			super(parent, qonfigType, modelType);
		}

		public VariableType getKeyType() {
			return theKeyType;
		}

		public CompiledExpression getKeyFormat() {
			return theKeyFormat;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);

			ConfigMap.Def mapAddOn = getAddOn(ConfigMap.Def.class);
			theKeyType = mapAddOn.getKeyType();
			theKeyFormat = mapAddOn.getKeyFormat();
		}

		public static abstract class Interpreted<K, V, M, MV extends M> extends ConfigModelValue.Interpreted.Abstract<V, M, MV> {
			private TypeToken<K> theKeyType;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<ObservableConfigFormat<K>>> theKeyFormat;

			protected Interpreted(AbstractConfigMap<M> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public AbstractConfigMap<M> getDefinition() {
				return (AbstractConfigMap<M>) super.getDefinition();
			}

			public TypeToken<K> getKeyType() {
				return theKeyType;
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<ObservableConfigFormat<K>>> getKeyFormat() {
				return theKeyFormat;
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);

				ConfigMap.Interpreted<K> mapAddOn = getAddOn(ConfigMap.Interpreted.class);
				theKeyType = mapAddOn.getKeyType();
				theKeyFormat = mapAddOn.getKeyFormat();
			}
		}
	}

	static class ExConfigMap extends AbstractConfigMap<ObservableMap<?, ?>> {
		public ExConfigMap(Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, ModelTypes.Map);
		}

		@Override
		public Interpreted<?, ?> interpretValue(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<K, V> extends AbstractConfigMap.Interpreted<K, V, ObservableMap<?, ?>, ObservableMap<K, V>> {
			public Interpreted(ExConfigMap definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return Collections.emptyList();
			}

			@Override
			protected ModelInstanceType<ObservableMap<?, ?>, ObservableMap<K, V>> getTargetType() {
				return ModelTypes.Map.forType(getKeyType(), getValueType());
			}

			@Override
			public ConfigModelValue<V, ObservableMap<K, V>> create() throws ModelInstantiationException {
				return new Instantiator<>(this);
			}
		}

		static class Instantiator<K, V> extends ConfigModelValue<V, ObservableMap<K, V>> {
			private TypeToken<K> theKeyType;
			private ModelValueInstantiator<SettableValue<ObservableConfigFormat<K>>> theKeyFormat;

			public Instantiator(ExConfigMap.Interpreted<K, V> interpreted) throws ModelInstantiationException {
				super(interpreted, interpreted.getConfigValue().instantiate(), interpreted.getValueType(), interpreted.getConfigPath(),
					interpreted.getFormat() == null ? null : interpreted.getFormat().instantiate(), interpreted.getFormatSet());
				theKeyType = interpreted.getKeyType();
				theKeyFormat = interpreted.getKeyFormat() == null ? null : interpreted.getKeyFormat().instantiate();
			}

			@Override
			public ObservableMap<K, V> create(ObservableConfigValueBuilder<V> config, ModelSetInstance msi)
				throws ModelInstantiationException {
				ObservableConfigMapBuilder<K, V> mapBuilder = config.asMap(theKeyType);
				if (theKeyFormat != null) {
					ObservableConfigFormat<K> keyFormat = theKeyFormat.get(msi).get();
					if (keyFormat != null)
						mapBuilder.withKeyFormat(keyFormat);
				}
				return mapBuilder.buildMap(null);
			}
		}
	}

	private void configureFormats(QonfigInterpreterCore.Builder interpreter) {
		// Text formats
		interpreter.createWith(StandardTextFormat.STANDARD_TEXT_FORMAT, ModelValueElement.CompiledSynth.class,
			ExElement.creator(StandardTextFormat::new));
		interpreter.createWith(CustomTextFormat.CUSTOM_TEXT_FORMAT, ModelValueElement.CompiledSynth.class,
			ExElement.creator(CustomTextFormat::new));
		interpreter.createWith(IntFormat.INT_FORMAT, ModelValueElement.CompiledSynth.class, ExElement.creator(IntFormat::new));
		interpreter.createWith(LongFormat.LONG_FORMAT, ModelValueElement.CompiledSynth.class, ExElement.creator(LongFormat::new));
		interpreter.createWith(DoubleFormat.DOUBLE_FORMAT, ModelValueElement.CompiledSynth.class, ExElement.creator(DoubleFormat::new));
		interpreter.createWith(FileFormat.FILE_FORMAT, ModelValueElement.CompiledSynth.class, ExElement.creator(FileFormat::new));
		interpreter.createWith(DateFormat.INSTANT_FORMAT, ModelValueElement.CompiledSynth.class, ExElement.creator(DateFormat::new));
		interpreter.createWith(RegexStringFormat.REGEX_FORMAT_STRING, ModelValueElement.CompiledSynth.class,
			ExElement.creator(RegexStringFormat::new));
		interpreter.createWith(ListFormat.LIST_FORMAT, ModelValueElement.CompiledSynth.class, ExElement.creator(ListFormat::new));
		// TODO regex-format

		// Text format validation
		// TODO regex-validation
		interpreter.createWith(FilterValidation.FILTER_VALIDATION, FormatValidation.Def.class,
			ExElement.creator(FilterValidation.Def::new));

		// File sources
		// TODO native-file-source, sftp-file-source
		interpreter.createWith(ExArchiveEnabledFileSource.ARCHIVE_ENABLED_FILE_SOURCE, ModelValueElement.CompiledSynth.class,
			ExElement.creator(ExArchiveEnabledFileSource::new));
		interpreter.createWith(ZipCompression.ZIP_ARCHIVAL, ModelValueElement.CompiledSynth.class, ExElement.creator(ZipCompression::new));
		interpreter.createWith(GZCompression.GZ_ARCHIVAL, ModelValueElement.CompiledSynth.class, ExElement.creator(GZCompression::new));
		interpreter.createWith(TarArchival.TAR_ARCHIVAL, ModelValueElement.CompiledSynth.class, ExElement.creator(TarArchival::new));

		// ObservableConfig formats
		interpreter.createWith(TextConfigFormat.TEXT_CONFIG_FORMAT, ModelValueElement.CompiledSynth.class,
			ExElement.creator(TextConfigFormat::new));
		interpreter.createWith(EntityConfigFormat.ENTITY_CONFIG_FORMAT, ModelValueElement.CompiledSynth.class,
			ExElement.creator(EntityConfigFormat::new));
		interpreter.createWith(EntityConfigField.ENTITY_CONFIG_FIELD, EntityConfigField.class, ExAddOn.creator(EntityConfigField::new));
	}

	@ExElementTraceable(toolkit = CONFIG,
		qonfigType = ExArchiveEnabledFileSource.ARCHIVE_ENABLED_FILE_SOURCE,
		interpretation = ExArchiveEnabledFileSource.Interpreted.class)
	static class ExArchiveEnabledFileSource
	extends ModelValueElement.Def.SingleTyped<SettableValue<?>, ModelValueElement<SettableValue<ArchiveEnabledFileSource>>>
	implements ModelValueElement.CompiledSynth<SettableValue<?>, ModelValueElement<SettableValue<ArchiveEnabledFileSource>>> {
		public static final String ARCHIVE_ENABLED_FILE_SOURCE = "archive-enabled-file-source";

		private CompiledExpression theWrapped;
		private CompiledExpression theMaxArchiveDepth;
		private final List<ModelValueElement.CompiledSynth<SettableValue<?>, ModelValueElement<SettableValue<ArchiveEnabledFileSource.FileArchival>>>> theArchiveMethods;

		public ExArchiveEnabledFileSource(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, ModelTypes.Value);
			theArchiveMethods = new ArrayList<>();
		}

		@QonfigAttributeGetter("wrapped")
		public CompiledExpression getWrapped() {
			return theWrapped;
		}

		@QonfigAttributeGetter("max-archive-depth")
		public CompiledExpression getMaxArchiveDepth() {
			return theMaxArchiveDepth;
		}

		@QonfigChildGetter("archive-method")
		public List<ModelValueElement.CompiledSynth<SettableValue<?>, ModelValueElement<SettableValue<ArchiveEnabledFileSource.FileArchival>>>> getArchiveMethods() {
			return Collections.unmodifiableList(theArchiveMethods);
		}

		@Override
		protected void doPrepare(ExpressoQIS session) throws QonfigInterpretationException {
			theWrapped = getAttributeExpression("wrapped", session);
			theMaxArchiveDepth = getAttributeExpression("max-archive-depth", session);
			syncChildren(ModelValueElement.CompiledSynth.class, theArchiveMethods, session.forChildren("archive-method"));
		}

		@Override
		public InterpretedSynth<SettableValue<?>, ?, ? extends ModelValueElement<SettableValue<ArchiveEnabledFileSource>>> interpretValue(
			ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}

		static class Interpreted extends
		ModelValueElement.Def.SingleTyped.Interpreted<SettableValue<?>, SettableValue<ArchiveEnabledFileSource>, ModelValueElement<SettableValue<ArchiveEnabledFileSource>>>
		implements
		ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<ArchiveEnabledFileSource>, ModelValueElement<SettableValue<ArchiveEnabledFileSource>>> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<FileDataSource>> theWrapped;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> theMaxArchiveDepth;
			private final List<ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<ArchiveEnabledFileSource.FileArchival>, ?>> theArchiveMethods;

			Interpreted(ExArchiveEnabledFileSource definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
				theArchiveMethods = new ArrayList<>();
			}

			@Override
			public ExArchiveEnabledFileSource getDefinition() {
				return (ExArchiveEnabledFileSource) super.getDefinition();
			}

			@Override
			protected ModelInstanceType<SettableValue<?>, SettableValue<ArchiveEnabledFileSource>> getTargetType() {
				return ModelTypes.Value.forType(ArchiveEnabledFileSource.class);
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<FileDataSource>> getWrapped() {
				return theWrapped;
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> getMaxArchiveDepth() {
				return theMaxArchiveDepth;
			}

			public List<ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<ArchiveEnabledFileSource.FileArchival>, ?>> getArchiveMethods() {
				return Collections.unmodifiableList(theArchiveMethods);
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);
				theWrapped = interpret(getDefinition().getWrapped(), ModelTypes.Value.forType(FileDataSource.class));
				theMaxArchiveDepth = interpret(getDefinition().getMaxArchiveDepth(), ModelTypes.Value.INT);
				try (Transaction t = ModelValueElement.INTERPRETING_PARENTS.installParent(this)) {
					syncChildren(getDefinition().getArchiveMethods(), theArchiveMethods, (def,
						vEnv) -> (ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<ArchiveEnabledFileSource.FileArchival>, ?>) def
						.interpret(vEnv),
						(i, vEnv) -> i.updateValue(vEnv));
				}
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				List<InterpretedValueSynth<?, ?>> components = new ArrayList<>();
				if (theWrapped != null)
					components.add(theWrapped);
				components.add(theMaxArchiveDepth);
				components.addAll(theArchiveMethods);
				return components;
			}

			@Override
			public ModelValueElement<SettableValue<ArchiveEnabledFileSource>> create() throws ModelInstantiationException {
				return new Instantiator(this);
			}
		}

		static class Instantiator extends ModelValueElement.Abstract<SettableValue<ArchiveEnabledFileSource>> {
			private final ModelValueInstantiator<SettableValue<FileDataSource>> theWrapped;
			private final ModelValueInstantiator<SettableValue<Integer>> theMaxArchiveDepth;
			private final List<ModelValueInstantiator<SettableValue<ArchiveEnabledFileSource.FileArchival>>> theArchiveMethods;
			private final String theLocation;

			Instantiator(ExArchiveEnabledFileSource.Interpreted interpreted) throws ModelInstantiationException {
				super(interpreted);
				theWrapped = interpreted.getWrapped() == null ? null : interpreted.getWrapped().instantiate();
				theMaxArchiveDepth = interpreted.getMaxArchiveDepth().instantiate();
				theArchiveMethods = QommonsUtils.filterMapE(interpreted.getArchiveMethods(), null, am -> am.instantiate());
				theLocation = interpreted.reporting().getFileLocation().getPosition(0).toShortString();
			}

			@Override
			public void instantiate() throws ModelInstantiationException {
				if (theWrapped != null)
					theWrapped.instantiate();
				theMaxArchiveDepth.instantiate();
				for (ModelValueInstantiator<?> am : theArchiveMethods)
					am.instantiate();
			}

			@Override
			public SettableValue<ArchiveEnabledFileSource> get(ModelSetInstance models)
				throws ModelInstantiationException, IllegalStateException {
				instantiate(models);
				SettableValue<FileDataSource> wrapped = theWrapped == null ? SettableValue.of(new NativeFileSource(), "Unmodifiable")
					: theWrapped.get(models);
				SettableValue<Integer> maxArchiveDepth = theMaxArchiveDepth.get(models);
				List<ArchiveEnabledFileSource.FileArchival> archiveMethods = new ArrayList<>(theArchiveMethods.size());
				for (ModelValueInstantiator<SettableValue<ArchiveEnabledFileSource.FileArchival>> am : theArchiveMethods)
					archiveMethods.add(am.get(models).get());
				String uModMsg = theLocation + ": Unmodifiable";
				return SettableValue.asSettable(wrapped.transform(tx -> tx.map(w -> {
					ArchiveEnabledFileSource aefs = new ArchiveEnabledFileSource(w)//
						.withArchival(archiveMethods);
					maxArchiveDepth.changes().takeUntil(wrapped.noInitChanges()).act(evt -> aefs.setMaxArchiveDepth(evt.getNewValue()));
					return aefs;
				})), __ -> uModMsg);
			}

			@Override
			public SettableValue<ArchiveEnabledFileSource> forModelCopy(SettableValue<ArchiveEnabledFileSource> value,
				ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				SettableValue<FileDataSource> srcWrapped = theWrapped == null ? SettableValue.of(new NativeFileSource(), "Unmodifiable")
					: theWrapped.get(sourceModels);
				SettableValue<FileDataSource> newWrapped = theWrapped == null ? srcWrapped
					: theWrapped.forModelCopy(srcWrapped, sourceModels, newModels);
				SettableValue<Integer> srcMAD = theMaxArchiveDepth.get(sourceModels);
				SettableValue<Integer> newMAD = theMaxArchiveDepth.forModelCopy(srcMAD, sourceModels, newModels);
				List<ArchiveEnabledFileSource.FileArchival> archiveMethods = new ArrayList<>(theArchiveMethods.size());
				boolean diff = srcWrapped != newWrapped || srcMAD != newMAD;
				for (ModelValueInstantiator<SettableValue<ArchiveEnabledFileSource.FileArchival>> am : theArchiveMethods) {
					SettableValue<ArchiveEnabledFileSource.FileArchival> srcAM = am.get(sourceModels);
					SettableValue<ArchiveEnabledFileSource.FileArchival> newAM = am.forModelCopy(srcAM, sourceModels, newModels);
					diff |= srcAM != newAM;
					archiveMethods.add(newAM.get());
				}
				if (!diff)
					return value;
				String uModMsg = theLocation + ": Unmodifiable";
				return SettableValue.asSettable(newWrapped.transform(tx -> tx.map(w -> {
					ArchiveEnabledFileSource aefs = new ArchiveEnabledFileSource(w)//
						.withArchival(archiveMethods);
					newMAD.changes().takeUntil(newWrapped.noInitChanges()).act(evt -> aefs.setMaxArchiveDepth(evt.getNewValue()));
					return aefs;
				})), __ -> uModMsg);
			}
		}
	}

	static class ZipCompression extends
	ModelValueElement.Def.SingleTyped<SettableValue<?>, ModelValueElement<SettableValue<ArchiveEnabledFileSource.ZipCompression>>>
	implements
	ModelValueElement.CompiledSynth<SettableValue<?>, ModelValueElement<SettableValue<ArchiveEnabledFileSource.ZipCompression>>> {
		public static final String ZIP_ARCHIVAL = "zip-archival";

		public ZipCompression(Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, ModelTypes.Value);
		}

		@Override
		protected void doPrepare(ExpressoQIS session) throws QonfigInterpretationException {
		}

		@Override
		public Interpreted interpretValue(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}

		static class Interpreted extends
		ModelValueElement.Def.SingleTyped.Interpreted<SettableValue<?>, SettableValue<ArchiveEnabledFileSource.ZipCompression>, ModelValueElement<SettableValue<ArchiveEnabledFileSource.ZipCompression>>>
		implements
		ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<ArchiveEnabledFileSource.ZipCompression>, ModelValueElement<SettableValue<ArchiveEnabledFileSource.ZipCompression>>> {
			Interpreted(ZipCompression definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return Collections.emptyList();
			}

			@Override
			public ModelValueElement<SettableValue<ArchiveEnabledFileSource.ZipCompression>> create() throws ModelInstantiationException {
				return new Instantiator(this);
			}
		}

		static class Instantiator extends ModelValueElement.Abstract<SettableValue<ArchiveEnabledFileSource.ZipCompression>> {
			Instantiator(ZipCompression.Interpreted interpreted) throws ModelInstantiationException {
				super(interpreted);
			}

			@Override
			public void instantiate() {

			}

			@Override
			public SettableValue<ArchiveEnabledFileSource.ZipCompression> get(ModelSetInstance models)
				throws ModelInstantiationException, IllegalStateException {
				instantiate(models);
				return SettableValue.of(new ArchiveEnabledFileSource.ZipCompression(), "Unmodifiable");
			}

			@Override
			public SettableValue<ArchiveEnabledFileSource.ZipCompression> forModelCopy(
				SettableValue<ArchiveEnabledFileSource.ZipCompression> value, ModelSetInstance sourceModels, ModelSetInstance newModels)
					throws ModelInstantiationException {
				return value;
			}
		}
	}

	static class GZCompression extends
	ModelValueElement.Def.SingleTyped<SettableValue<?>, ModelValueElement<SettableValue<ArchiveEnabledFileSource.GZipCompression>>>
	implements
	ModelValueElement.CompiledSynth<SettableValue<?>, ModelValueElement<SettableValue<ArchiveEnabledFileSource.GZipCompression>>> {
		public static final String GZ_ARCHIVAL = "gz-archival";

		public GZCompression(Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, ModelTypes.Value);
		}

		@Override
		protected void doPrepare(ExpressoQIS session) throws QonfigInterpretationException {
		}

		@Override
		public Interpreted interpretValue(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}

		static class Interpreted extends
		ModelValueElement.Def.SingleTyped.Interpreted<SettableValue<?>, SettableValue<ArchiveEnabledFileSource.GZipCompression>, ModelValueElement<SettableValue<ArchiveEnabledFileSource.GZipCompression>>>
		implements
		ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<ArchiveEnabledFileSource.GZipCompression>, ModelValueElement<SettableValue<ArchiveEnabledFileSource.GZipCompression>>> {

			Interpreted(GZCompression definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return Collections.emptyList();
			}

			@Override
			public ModelValueElement<SettableValue<ArchiveEnabledFileSource.GZipCompression>> create() throws ModelInstantiationException {
				return new Instantiator(this);
			}
		}

		static class Instantiator extends ModelValueElement.Abstract<SettableValue<ArchiveEnabledFileSource.GZipCompression>> {
			Instantiator(GZCompression.Interpreted interpreted) throws ModelInstantiationException {
				super(interpreted);
			}

			@Override
			public void instantiate() {

			}

			@Override
			public SettableValue<ArchiveEnabledFileSource.GZipCompression> get(ModelSetInstance models)
				throws ModelInstantiationException, IllegalStateException {
				instantiate(models);
				return SettableValue.of(new ArchiveEnabledFileSource.GZipCompression(), "Unmodifiable");
			}

			@Override
			public SettableValue<ArchiveEnabledFileSource.GZipCompression> forModelCopy(
				SettableValue<ArchiveEnabledFileSource.GZipCompression> value, ModelSetInstance sourceModels, ModelSetInstance newModels)
					throws ModelInstantiationException {
				return value;
			}
		}
	}

	static class TarArchival
	extends ModelValueElement.Def.SingleTyped<SettableValue<?>, ModelValueElement<SettableValue<ArchiveEnabledFileSource.TarArchival>>>
	implements
	ModelValueElement.CompiledSynth<SettableValue<?>, ModelValueElement<SettableValue<ArchiveEnabledFileSource.TarArchival>>> {
		public static final String TAR_ARCHIVAL = "tar-archival";

		public TarArchival(Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, ModelTypes.Value);
		}

		@Override
		protected void doPrepare(ExpressoQIS session) throws QonfigInterpretationException {
		}

		@Override
		public Interpreted interpretValue(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}

		static class Interpreted extends
		ModelValueElement.Def.SingleTyped.Interpreted<SettableValue<?>, SettableValue<ArchiveEnabledFileSource.TarArchival>, ModelValueElement<SettableValue<ArchiveEnabledFileSource.TarArchival>>>
		implements
		ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<ArchiveEnabledFileSource.TarArchival>, ModelValueElement<SettableValue<ArchiveEnabledFileSource.TarArchival>>> {

			Interpreted(TarArchival definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return Collections.emptyList();
			}

			@Override
			public ModelValueElement<SettableValue<ArchiveEnabledFileSource.TarArchival>> create() throws ModelInstantiationException {
				return new Instantiator(this);
			}
		}

		static class Instantiator extends ModelValueElement.Abstract<SettableValue<ArchiveEnabledFileSource.TarArchival>> {
			Instantiator(TarArchival.Interpreted interpreted) throws ModelInstantiationException {
				super(interpreted);
			}

			@Override
			public void instantiate() {

			}

			@Override
			public SettableValue<ArchiveEnabledFileSource.TarArchival> get(ModelSetInstance models)
				throws ModelInstantiationException, IllegalStateException {
				instantiate(models);
				return SettableValue.of(new ArchiveEnabledFileSource.TarArchival(), "Unmodifiable");
			}

			@Override
			public SettableValue<ArchiveEnabledFileSource.TarArchival> forModelCopy(
				SettableValue<ArchiveEnabledFileSource.TarArchival> value, ModelSetInstance sourceModels, ModelSetInstance newModels)
					throws ModelInstantiationException {
				return value;
			}
		}
	}

	@ExElementTraceable(toolkit = CONFIG,
		qonfigType = AbstractFormat.FORMAT,
		interpretation = AbstractFormat.Interpreted.class,
		instance = AbstractFormat.Instantiator.class)
	static abstract class AbstractFormat<T>
	extends ModelValueElement.Def.SingleTyped<SettableValue<?>, ModelValueElement<SettableValue<Format<T>>>>
	implements ModelValueElement.CompiledSynth<SettableValue<?>, ModelValueElement<SettableValue<Format<T>>>> {
		public static final String FORMAT = "format";

		private final List<FormatValidation.Def<?>> theValidation;

		protected AbstractFormat(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, ModelTypes.Value);
			theValidation = new ArrayList<>();
		}

		@QonfigChildGetter("validate")
		public List<FormatValidation.Def<?>> getValidation() {
			return Collections.unmodifiableList(theValidation);
		}

		@Override
		protected void doPrepare(ExpressoQIS session) throws QonfigInterpretationException {
			syncChildren(FormatValidation.Def.class, theValidation, session.forChildren("validate"));
		}

		@Override
		public abstract Interpreted<T> interpretValue(ExElement.Interpreted<?> parent);

		public static abstract class Interpreted<T> extends
		ModelValueElement.Def.SingleTyped.Interpreted<SettableValue<?>, SettableValue<Format<T>>, ModelValueElement<SettableValue<Format<T>>>>
		implements
		ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<Format<T>>, ModelValueElement<SettableValue<Format<T>>>> {
			private final List<FormatValidation.Interpreted<T, ?>> theValidation;

			protected Interpreted(AbstractFormat<T> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
				theValidation = new ArrayList<>();
			}

			@Override
			public AbstractFormat<T> getDefinition() {
				return (AbstractFormat<T>) super.getDefinition();
			}

			@Override
			protected ModelInstanceType<SettableValue<?>, SettableValue<Format<T>>> getTargetType() {
				return ModelTypes.Value.forType(TypeTokens.get().keyFor(Format.class).<Format<T>> parameterized(getValueType()));
			}

			public List<FormatValidation.Interpreted<T, ?>> getValidation() {
				return Collections.unmodifiableList(theValidation);
			}

			protected abstract TypeToken<T> getValueType();

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);

				if (!theValidation.isEmpty()) {
					TypeToken<T> valueType = getValueType();
					syncChildren(getDefinition().getValidation(), theValidation,
						def -> (FormatValidation.Interpreted<T, ?>) def.interpret(this), (i, vEnv) -> i.updateFormat(vEnv, valueType));
				}
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList.of(theValidation.stream().flatMap(v -> v.getComponents().stream()));
			}

			@Override
			public abstract Instantiator<T> create() throws ModelInstantiationException;
		}

		public static abstract class Instantiator<T> extends ModelValueElement.Abstract<SettableValue<Format<T>>> {
			private final ModelInstantiator theLocalModels;
			private final List<FormatValidation.Instantiator<T, ?>> theValidation;

			protected Instantiator(AbstractFormat.Interpreted<T> interpreted) throws ModelInstantiationException {
				super(interpreted);
				theLocalModels = interpreted.getExpressoEnv().getModels().instantiate();
				theValidation = new ArrayList<>(interpreted.getValidation().size());
				for (FormatValidation.Interpreted<T, ?> validation : interpreted.getValidation()) {
					FormatValidation.Instantiator<T, ?> v = validation.create();
					v.update(validation, null);
					theValidation.add(v);
				}
			}

			@Override
			public void instantiate() throws ModelInstantiationException {
				if (theLocalModels != null)
					theLocalModels.instantiate();
				for (FormatValidation.Instantiator<T, ?> validation : theValidation)
					validation.instantiated();
			}

			@Override
			public SettableValue<Format<T>> get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
				if (theLocalModels != null)
					models = theLocalModels.wrap(models);
				instantiate(models);
				List<FormatValidation<T>> validation = new ArrayList<>(theValidation.size());
				for (FormatValidation.Instantiator<T, ?> v : theValidation)
					validation.add(v.get(models));
				SettableValue<Format<T>> sourceFormat = createFormat(models);
				if (validation.isEmpty())
					return sourceFormat;
				return new ValidatedFormatValue<>(sourceFormat, validation);
			}

			protected abstract SettableValue<Format<T>> createFormat(ModelSetInstance models)
				throws ModelInstantiationException, IllegalStateException;

			@Override
			public SettableValue<Format<T>> forModelCopy(SettableValue<Format<T>> value, ModelSetInstance sourceModels,
				ModelSetInstance newModels) throws ModelInstantiationException {
				if (theLocalModels != null) {
					sourceModels = theLocalModels.wrap(sourceModels);
					newModels = theLocalModels.wrap(newModels);
				}
				if (!(value instanceof ValidatedFormatValue))
					return copyFormat(value, sourceModels, newModels);
				ValidatedFormatValue<T> validatedValue = (ValidatedFormatValue<T>) value;
				SettableValue<Format<T>> newSource = copyFormat(validatedValue.getSourceFormat(), sourceModels, newModels);
				List<FormatValidation<T>> newValidation = null;
				for (int i = 0; i < theValidation.size(); i++) {
					FormatValidation<T> validation = ((FormatValidation.Instantiator<T, FormatValidation<T>>) theValidation.get(i))
						.forModelCopy(validatedValue.getValidation().get(i), sourceModels, newModels);
					if (newValidation == null && validation != validatedValue.getValidation().get(i)) {
						newValidation = new ArrayList<>(theValidation.size());
						for (int j = 0; j < i; j++)
							newValidation.add(validatedValue.getValidation().get(j));
					}
					if (newValidation != null)
						newValidation.add(validatedValue.getValidation().get(i));
				}
				if (newSource != validatedValue.getSourceFormat() && newValidation == null)
					newValidation = validatedValue.getValidation();
				if (newValidation != null)
					return new ValidatedFormatValue<>(newSource, newValidation);
				return value;
			}

			protected abstract SettableValue<Format<T>> copyFormat(SettableValue<Format<T>> format, ModelSetInstance sourceModels,
				ModelSetInstance newModels) throws ModelInstantiationException;
		}

		private static class ValidatedFormatValue<T> extends AbstractIdentifiable implements SettableValue<Format<T>> {
			private final SettableValue<Format<T>> theSourceFormat;
			private final List<FormatValidation<T>> theValidation;

			private long theStamp;
			private ValidatedFormat<T> thePreviousFormat;
			private ValidatedFormat<T> theValidatedFormat;

			ValidatedFormatValue(SettableValue<Format<T>> sourceFormat, List<FormatValidation<T>> validation) {
				theSourceFormat = sourceFormat;
				theValidation = validation;
				theStamp = -1;
			}

			SettableValue<Format<T>> getSourceFormat() {
				return theSourceFormat;
			}

			List<FormatValidation<T>> getValidation() {
				return theValidation;
			}

			@Override
			public ValidatedFormat<T> get() {
				if (theStamp != theSourceFormat.getStamp()) {
					thePreviousFormat = theValidatedFormat;
					theStamp = theSourceFormat.getStamp();
					Format<T> f = theSourceFormat.get();
					theValidatedFormat = f == null ? null : new ValidatedFormat<>(f, theValidation);
				}
				return theValidatedFormat;
			}

			@Override
			public Observable<ObservableValueEvent<Format<T>>> noInitChanges() {
				Observable<ObservableValueEvent<Format<T>>> sourceChanges = theSourceFormat.noInitChanges();
				class ValidatedFormatChanges extends AbstractIdentifiable implements Observable<ObservableValueEvent<Format<T>>> {
					@Override
					public CoreId getCoreId() {
						return theSourceFormat.getCoreId();
					}

					@Override
					protected Object createIdentity() {
						return Identifiable.wrap(sourceChanges.getIdentity(), "validated", theValidation.toArray());
					}

					@Override
					public ThreadConstraint getThreadConstraint() {
						return sourceChanges.getThreadConstraint();
					}

					@Override
					public boolean isEventing() {
						return sourceChanges.isEventing();
					}

					@Override
					public Subscription subscribe(Observer<? super ObservableValueEvent<Format<T>>> observer) {
						return sourceChanges.subscribe(new Observer<ObservableValueEvent<Format<T>>>() {
							@Override
							public void onNext(ObservableValueEvent<Format<T>> value) {
								ValidatedFormat<T> newFormat = get();
								ValidatedFormat<T> old = value.isInitial() ? newFormat : thePreviousFormat;
								ObservableValueEvent<Format<T>> evt;
								if (value.isInitial())
									evt = createInitialEvent(newFormat, value);
								else
									evt = createChangeEvent(old, old, value);
								try (Transaction t = evt.use()) {
									observer.onNext(evt);
								}
							}

							@Override
							public void onCompleted(Supplier<Causable> cause) {
								observer.onCompleted(cause);
							}
						});
					}

					@Override
					public boolean isSafe() {
						return sourceChanges.isSafe();
					}

					@Override
					public Transaction lock() {
						return sourceChanges.lock();
					}

					@Override
					public Transaction tryLock() {
						return sourceChanges.tryLock();
					}

					@Override
					public long getStamp() {
						return sourceChanges.getStamp();
					}

					@Override
					public CoreChangeSources getChangeSources() {
						return sourceChanges.getChangeSources();
					}
				}
				return new ValidatedFormatChanges();
			}

			@Override
			protected Object createIdentity() {
				return Identifiable.wrap(theSourceFormat.getIdentity(), "validated", theValidation.toArray());
			}

			@Override
			public ValidatedFormatValue<T> alias(String alias) {
				super.alias(alias);
				return this;
			}

			@Override
			public Transaction lock(boolean write, Object cause) {
				return theSourceFormat.lock(write, cause);
			}

			@Override
			public Transaction tryLock(boolean write, Object cause) {
				return theSourceFormat.tryLock(write, cause);
			}

			@Override
			public Collection<Cause> getCurrentCauses() {
				return theSourceFormat.getCurrentCauses();
			}

			@Override
			public long getStamp() {
				return theSourceFormat.getStamp();
			}

			@Override
			public boolean isLockSupported() {
				return theSourceFormat.isLockSupported();
			}

			@Override
			public Format<T> set(Format<T> value) throws IllegalArgumentException, UnsupportedOperationException {
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			}

			@Override
			public String isAcceptable(Format<T> value) {
				return StdMsg.UNSUPPORTED_OPERATION;
			}

			@Override
			public ObservableValue<String> isEnabled() {
				return SettableValue.ALWAYS_DISABLED;
			}
		}

		private static class ValidatedFormat<T> implements Format<T> {
			private final Format<T> theSourceFormat;
			private final List<FormatValidation<T>> theValidation;

			ValidatedFormat(Format<T> sourceFormat, List<FormatValidation<T>> validation) {
				theSourceFormat = sourceFormat;
				theValidation = validation;
			}

			@Override
			public void append(StringBuilder text, T value) {
				theSourceFormat.append(text, value);
			}

			@Override
			public T parse(CharSequence text) throws ParseException {
				T parsed = theSourceFormat.parse(text);
				for (FormatValidation<T> validation : theValidation) {
					String error = validation.test(parsed, text);
					if (error != null)
						throw new ParseException(error, 0);
				}
				return parsed;
			}
		}
	}

	@ExElementTraceable(toolkit = CONFIG,
		qonfigType = FileFormat.FILE_FORMAT,
		interpretation = FileFormat.Interpreted.class,
		instance = FileFormat.Instantiator.class)
	static class FileFormat extends AbstractFormat<BetterFile> {
		public static final String FILE_FORMAT = "file-format";

		private CompiledExpression theFileSource;
		private CompiledExpression theWorkingDir;
		private boolean isAllowEmpty;

		public FileFormat(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@QonfigAttributeGetter("file-source")
		public CompiledExpression getFileSource() {
			return theFileSource;
		}

		@QonfigAttributeGetter("working-dir")
		public CompiledExpression getWorkingDir() {
			return theWorkingDir;
		}

		@QonfigAttributeGetter("allow-empty")
		public boolean isAllowEmpty() {
			return isAllowEmpty;
		}

		@Override
		protected void doPrepare(ExpressoQIS session) throws QonfigInterpretationException {
			super.doPrepare(session);
			theFileSource = getAttributeExpression("file-source", session);
			theWorkingDir = getAttributeExpression("working-dir", session);
		}

		@Override
		public Interpreted interpretValue(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}

		static class Interpreted extends AbstractFormat.Interpreted<BetterFile> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<FileDataSource>> theFileSource;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<BetterFile>> theWorkingDir;

			Interpreted(FileFormat definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public FileFormat getDefinition() {
				return (FileFormat) super.getDefinition();
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<FileDataSource>> getFileSource() {
				return theFileSource;
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<BetterFile>> getWorkingDir() {
				return theWorkingDir;
			}

			@Override
			protected TypeToken<BetterFile> getValueType() {
				return TypeTokens.get().of(BetterFile.class);
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);
				theFileSource = interpret(getDefinition().getFileSource(), ModelTypes.Value.forType(FileDataSource.class));
				theWorkingDir = interpret(getDefinition().getWorkingDir(), ModelTypes.Value.forType(BetterFile.class));

			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				List<InterpretedValueSynth<?, ?>> components = new ArrayList<>();
				if (theFileSource != null)
					components.add(theFileSource);
				components.add(theWorkingDir);
				return components;
			}

			@Override
			public Instantiator create() throws ModelInstantiationException {
				return new Instantiator(this);
			}
		}

		static class Instantiator extends AbstractFormat.Instantiator<BetterFile> {
			private final ModelValueInstantiator<SettableValue<FileDataSource>> theFileSource;
			private final ModelValueInstantiator<SettableValue<BetterFile>> theWorkingDir;
			private final boolean isAllowEmpty;
			private final String theLocation;

			Instantiator(FileFormat.Interpreted interpreted) throws ModelInstantiationException {
				super(interpreted);
				theFileSource = interpreted.getFileSource() == null ? null : interpreted.getFileSource().instantiate();
				theWorkingDir = interpreted.getWorkingDir() == null ? null : interpreted.getWorkingDir().instantiate();
				isAllowEmpty = interpreted.getDefinition().isAllowEmpty();
				theLocation = interpreted.reporting().getPosition().toShortString();
			}

			@Override
			public void instantiate() throws ModelInstantiationException {
				super.instantiate();
				if (theFileSource != null)
					theFileSource.instantiate();
				if (theWorkingDir != null)
					theWorkingDir.instantiate();
			}

			@Override
			protected SettableValue<Format<BetterFile>> createFormat(ModelSetInstance models)
				throws ModelInstantiationException, IllegalStateException {
				String uModMsg = theLocation + "formats are not reversible";
				SettableValue<FileDataSource> fileSource = theFileSource == null ? SettableValue.of(new NativeFileSource(), uModMsg)
					: theFileSource.get(models);
				SettableValue<BetterFile> workingDir;
				if (theWorkingDir != null)
					workingDir = theWorkingDir.get(models);
				else
					workingDir = SettableValue.asSettable(fileSource.map(fs -> BetterFile.at(fs, System.getProperty("user.dir"))),
						__ -> uModMsg);

				return SettableValue.asSettable(fileSource.transform(tx -> tx.combineWith(workingDir)//
					.combine((fs, wd) -> new BetterFile.FileFormat(fs, wd, isAllowEmpty))), __ -> uModMsg);
			}

			@Override
			protected SettableValue<Format<BetterFile>> copyFormat(SettableValue<Format<BetterFile>> format, ModelSetInstance sourceModels,
				ModelSetInstance newModels) throws ModelInstantiationException {
				String uModMsg = theLocation + "formats are not reversible";
				SettableValue<FileDataSource> srcFS = theFileSource == null ? SettableValue.of(new NativeFileSource(), uModMsg)
					: theFileSource.get(sourceModels);
				SettableValue<FileDataSource> newFS = theFileSource == null ? srcFS
					: theFileSource.forModelCopy(srcFS, sourceModels, newModels);
				SettableValue<BetterFile> srcWD, newWD;
				if (theWorkingDir != null) {
					srcWD = theWorkingDir.get(sourceModels);
					newWD = theWorkingDir.forModelCopy(srcWD, sourceModels, newModels);
				} else {
					srcWD = SettableValue.asSettable(srcFS.map(fs -> BetterFile.at(fs, System.getProperty("user.dir"))),
						__ -> uModMsg);
					if (newFS == srcFS)
						newWD = srcWD;
					else
						newWD = SettableValue.asSettable(newFS.map(fs -> BetterFile.at(fs, System.getProperty("user.dir"))),
							__ -> uModMsg);
				}
				if (srcFS == newFS && srcWD == newWD)
					return format;

				return SettableValue.asSettable(newFS.transform(tx -> tx.combineWith(newWD)//
					.combine((fs, wd) -> new BetterFile.FileFormat(fs, wd, isAllowEmpty))), __ -> uModMsg);
			}
		}
	}

	@ExElementTraceable(toolkit = CONFIG,
		qonfigType = IntFormat.INT_FORMAT,
		interpretation = IntFormat.Interpreted.class,
		instance = IntFormat.Instantiator.class)
	static class IntFormat extends AbstractFormat<Integer> {
		public static final String INT_FORMAT = "int-format";

		private char theGroupingSeparator;
		private boolean isEmptyAllowed;

		public IntFormat(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@QonfigAttributeGetter("grouping-separator")
		public char getGroupingSeparator() {
			return theGroupingSeparator;
		}

		@QonfigAttributeGetter("allow-empty")
		public boolean isEmptyAllowed() {
			return isEmptyAllowed;
		}

		@Override
		protected void doPrepare(ExpressoQIS session) throws QonfigInterpretationException {
			super.doPrepare(session);
			String gs = session.getAttributeText("grouping-separator");
			if (gs == null || gs.isEmpty())
				theGroupingSeparator = (char) 0;
			else if (gs.length() == 1)
				theGroupingSeparator = gs.charAt(0);
			else {
				reporting().at(session.attributes().get("grouping-separator").get().position)
				.error("grouping-separator must be a single character, not '" + gs + "'");
			}
			isEmptyAllowed = session.getAttribute("allow-empty", boolean.class);
		}

		@Override
		public Interpreted interpretValue(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}

		static class Interpreted extends AbstractFormat.Interpreted<Integer> {
			Interpreted(IntFormat definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public IntFormat getDefinition() {
				return (IntFormat) super.getDefinition();
			}

			@Override
			protected TypeToken<Integer> getValueType() {
				return TypeTokens.get().INT;
			}

			@Override
			public Instantiator create() throws ModelInstantiationException {
				return new Instantiator(this);
			}
		}

		static class Instantiator extends AbstractFormat.Instantiator<Integer> {
			private char theGroupingSeparator;
			private boolean isEmptyAllowed;

			Instantiator(IntFormat.Interpreted interpreted) throws ModelInstantiationException {
				super(interpreted);
				theGroupingSeparator = interpreted.getDefinition().getGroupingSeparator();
				isEmptyAllowed = interpreted.getDefinition().isEmptyAllowed();
			}

			@Override
			protected SettableValue<Format<Integer>> createFormat(ModelSetInstance models)
				throws ModelInstantiationException, IllegalStateException {
				return SettableValue.of(SpinnerFormat.INT.withGroupingSeparator(theGroupingSeparator).withEmptyAllowed(isEmptyAllowed),
					reporting().getPosition().toShortString() + ":Not modifiable");
			}

			@Override
			protected SettableValue<Format<Integer>> copyFormat(SettableValue<Format<Integer>> format, ModelSetInstance sourceModels,
				ModelSetInstance newModels) throws ModelInstantiationException {
				return format;
			}
		}
	}

	static class LongFormat extends AbstractFormat<Long> {
		public static final String LONG_FORMAT = "long-format";

		private char theGroupingSeparator;
		private boolean isEmptyAllowed;

		public LongFormat(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@QonfigAttributeGetter("grouping-separator")
		public char getGroupingSeparator() {
			return theGroupingSeparator;
		}

		@QonfigAttributeGetter("allow-empty")
		public boolean isEmptyAllowed() {
			return isEmptyAllowed;
		}

		@Override
		protected void doPrepare(ExpressoQIS session) throws QonfigInterpretationException {
			super.doPrepare(session);
			String gs = session.getAttributeText("grouping-separator");
			if (gs == null || gs.isEmpty())
				theGroupingSeparator = (char) 0;
			else if (gs.length() == 1)
				theGroupingSeparator = gs.charAt(0);
			else {
				reporting().at(session.attributes().get("grouping-separator").get().position)
				.error("grouping-separator must be a single character, not '" + gs + "'");
			}
			isEmptyAllowed = session.getAttribute("allow-empty", boolean.class);
		}

		@Override
		public Interpreted interpretValue(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}

		static class Interpreted extends AbstractFormat.Interpreted<Long> {
			Interpreted(LongFormat definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public LongFormat getDefinition() {
				return (LongFormat) super.getDefinition();
			}

			@Override
			protected TypeToken<Long> getValueType() {
				return TypeTokens.get().LONG;
			}

			@Override
			public Instantiator create() throws ModelInstantiationException {
				return new Instantiator(this);
			}
		}

		static class Instantiator extends AbstractFormat.Instantiator<Long> {
			private char theGroupingSeparator;
			private boolean isEmptyAllowed;

			Instantiator(LongFormat.Interpreted interpreted) throws ModelInstantiationException {
				super(interpreted);
				theGroupingSeparator = interpreted.getDefinition().getGroupingSeparator();
				isEmptyAllowed = interpreted.getDefinition().isEmptyAllowed();
			}

			@Override
			protected SettableValue<Format<Long>> createFormat(ModelSetInstance models)
				throws ModelInstantiationException, IllegalStateException {
				return SettableValue.of(SpinnerFormat.LONG.withGroupingSeparator(theGroupingSeparator).withEmptyAllowed(isEmptyAllowed),
					reporting().getPosition().toShortString() + ":Not modifiable");
			}

			@Override
			protected SettableValue<Format<Long>> copyFormat(SettableValue<Format<Long>> format, ModelSetInstance sourceModels,
				ModelSetInstance newModels) throws ModelInstantiationException {
				return format;
			}
		}
	}

	@ExElementTraceable(toolkit = CONFIG,
		qonfigType = DoubleFormat.DOUBLE_FORMAT,
		interpretation = DoubleFormat.Interpreted.class,
		instance = DoubleFormat.Instantiator.class)
	static class DoubleFormat extends AbstractFormat<Double> {
		public static final String DOUBLE_FORMAT = "double-format";

		private CompiledExpression theMinSignificantDigits;
		private CompiledExpression theMaxSignificantDigits;
		private int theMaxIntDigits;
		private int theZeroExp;
		private boolean isEmptyAllowed;
		private String theUnit;
		private boolean isUnitRequired;
		private boolean isMetricPrefixed;
		private boolean isMetricPrefixedP2;
		private boolean isMetricPrefixed3K;
		private double theDefaultPrefixMult;
		private final List<Prefix> thePrefixes;
		private final Map<String, Double> thePrefixMults;

		public DoubleFormat(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
			thePrefixes = new ArrayList<>();
			thePrefixMults = new LinkedHashMap<>();
		}

		@QonfigAttributeGetter("min-sig-digs")
		public CompiledExpression getMinSignificantDigits() {
			return theMinSignificantDigits;
		}

		@QonfigAttributeGetter("max-sig-digs")
		public CompiledExpression getMaxSignificantDigits() {
			return theMaxSignificantDigits;
		}

		@QonfigAttributeGetter("max-int-digits")
		public int getMaxIntDigits() {
			return theMaxIntDigits;
		}

		@QonfigAttributeGetter("zero-exp")
		public int getZeroExp() {
			return theZeroExp;
		}

		@QonfigAttributeGetter("allow-empty")
		public boolean isEmptyAllowed() {
			return isEmptyAllowed;
		}

		@QonfigAttributeGetter("unit")
		public String getUnit() {
			return theUnit;
		}

		@QonfigAttributeGetter("unit-required")
		public boolean isUnitRequired() {
			return isUnitRequired;
		}

		@QonfigAttributeGetter("metric-prefixes")
		public boolean isMetricPrefixed() {
			return isMetricPrefixed;
		}

		@QonfigAttributeGetter("metric-prefixes-p2")
		public boolean isMetricPrefixedP2() {
			return isMetricPrefixedP2;
		}

		@QonfigAttributeGetter("metric-prefixes-3k")
		public boolean isMetricPrefixed3K() {
			return isMetricPrefixed3K;
		}

		@QonfigAttributeGetter("default-prefix-multiplier")
		public double getDefaultPrefixMult() {
			return theDefaultPrefixMult;
		}

		@QonfigChildGetter("prefix")
		public List<Prefix> getPrefixes() {
			return Collections.unmodifiableList(thePrefixes);
		}

		public Map<String, Double> getPrefixMults() {
			return Collections.unmodifiableMap(thePrefixMults);
		}

		@Override
		protected void doPrepare(ExpressoQIS session) throws QonfigInterpretationException {
			super.doPrepare(session);
			theMinSignificantDigits = getAttributeExpression("min-sig-digs", session);
			theMaxSignificantDigits = getAttributeExpression("max-sig-digs", session);
			String maxIntDigits = session.getAttributeText("max-int-digits");
			theMaxIntDigits = maxIntDigits == null ? -1 : Integer.parseInt(maxIntDigits);
			if (maxIntDigits != null && theMaxIntDigits < 0) {
				session.reporting().at(session.attributes().get("max-int-digits").getLocatedContent())
				.warn("max-int-digits must be greater >= zero");
				theMaxIntDigits = -1;
			}
			String zeroExp = session.getAttributeText("zero-exp");
			theZeroExp = zeroExp == null ? -1 : Integer.parseInt(zeroExp);
			if (zeroExp != null && theZeroExp <= 0) {
				session.reporting().at(session.attributes().get("zero-exp").getLocatedContent()).warn("zero-exp must be greater than zero");
				theZeroExp = -1;
			}
			isEmptyAllowed = session.getAttribute("allow-empty", boolean.class);
			theUnit = session.getAttributeText("unit");
			isUnitRequired = session.getAttribute("unit-required", boolean.class);
			isMetricPrefixed = session.getAttribute("metric-prefixes", boolean.class);
			isMetricPrefixedP2 = session.getAttribute("metric-prefixes-p2", boolean.class);
			isMetricPrefixed3K = session.getAttribute("metric-prefixes-3k", boolean.class);
			int mp = 0;
			if (isMetricPrefixed)
				mp++;
			if (isMetricPrefixedP2)
				mp++;
			if (isMetricPrefixed3K)
				mp++;
			if (mp > 1)
				throw new QonfigInterpretationException(
					"Only one of 'metrix-prefixes', 'metric-prefixes-p2', or 'metric-prefixes-3k'" + " may be specified",
					session.attributes().get("metric-prefixes-p2").getLocatedContent());
			String dpm = session.getAttributeText("default-prefix-multiplier");
			theDefaultPrefixMult = dpm == null ? Double.NaN : Double.parseDouble(dpm);
			syncChildren(Prefix.class, thePrefixes, session.forChildren("prefix"));
			thePrefixMults.clear();
			for (Prefix prefix : thePrefixes) {
				if (thePrefixMults.containsKey(prefix.getName()))
					prefix.reporting().error("Multiple prefix elements named '" + prefix.getName() + "'");
				else if (prefix.getExponent() != null)
					thePrefixMults.put(prefix.getName(), Math.pow(10, prefix.getExponent()));
				else
					thePrefixMults.put(prefix.getName(), prefix.getMultiplier());
			}
		}

		@Override
		public Interpreted interpretValue(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}

		static class Interpreted extends AbstractFormat.Interpreted<Double> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> theMinSignificantDigits;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> theMaxSignificantDigits;

			Interpreted(DoubleFormat definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public DoubleFormat getDefinition() {
				return (DoubleFormat) super.getDefinition();
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> getMinSignificantDigits() {
				return theMinSignificantDigits;
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> getMaxSignificantDigits() {
				return theMaxSignificantDigits;
			}

			@Override
			protected TypeToken<Double> getValueType() {
				return TypeTokens.get().DOUBLE;
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);
				theMinSignificantDigits = interpret(getDefinition().getMinSignificantDigits(), ModelTypes.Value.INT);
				theMaxSignificantDigits = interpret(getDefinition().getMaxSignificantDigits(), ModelTypes.Value.INT);
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return Collections.emptyList();
			}

			@Override
			public Instantiator create() throws ModelInstantiationException {
				return new Instantiator(this);
			}
		}

		static class Instantiator extends AbstractFormat.Instantiator<Double> {
			private final ModelValueInstantiator<SettableValue<Integer>> theMinSignificantDigits;
			private final ModelValueInstantiator<SettableValue<Integer>> theMaxSignificantDigits;
			private final int theMaxIntDigits;
			private final int theZeroExp;
			private final boolean isEmptyAllowed;
			private final String theUnit;
			private final boolean isUnitRequired;
			private final boolean isMetricPrefixed;
			private final boolean isMetricPrefixedP2;
			private final boolean isMetricPrefixed3K;
			private final double theDefaultPrefixMult;
			private final Map<String, Double> thePrefixMults;
			private final String theUModMsg;

			public Instantiator(DoubleFormat.Interpreted interpreted) throws ModelInstantiationException {
				super(interpreted);
				theMinSignificantDigits = interpreted.getMinSignificantDigits().instantiate();
				theMaxSignificantDigits = interpreted.getMaxSignificantDigits() == null ? null
					: interpreted.getMaxSignificantDigits().instantiate();
				theMaxIntDigits = interpreted.getDefinition().getMaxIntDigits();
				theZeroExp = interpreted.getDefinition().getZeroExp();
				isEmptyAllowed = interpreted.getDefinition().isEmptyAllowed();
				theUnit = interpreted.getDefinition().getUnit();
				isUnitRequired = interpreted.getDefinition().isUnitRequired();
				isMetricPrefixed = interpreted.getDefinition().isMetricPrefixed();
				isMetricPrefixedP2 = interpreted.getDefinition().isMetricPrefixedP2();
				isMetricPrefixed3K = interpreted.getDefinition().isMetricPrefixed3K();
				theDefaultPrefixMult = interpreted.getDefinition().getDefaultPrefixMult();
				thePrefixMults = QommonsUtils.unmodifiableCopy(interpreted.getDefinition().getPrefixMults());
				theUModMsg = interpreted.reporting().getFileLocation().getPosition(0).toShortString() + "formats are not reversible";
			}

			@Override
			public void instantiate() throws ModelInstantiationException {
				super.instantiate();
				theMinSignificantDigits.instantiate();
				if (theMaxSignificantDigits != null)
					theMaxSignificantDigits.instantiate();
			}

			@Override
			protected SettableValue<Format<Double>> createFormat(ModelSetInstance models)
				throws ModelInstantiationException, IllegalStateException {
				SettableValue<Integer> minSigDigs = theMinSignificantDigits.get(models);
				SettableValue<Integer> maxSigDigs = theMaxSignificantDigits == null ? null : theMaxSignificantDigits.get(models);
				return createFormat(minSigDigs, maxSigDigs);
			}

			private SettableValue<Format<Double>> createFormat(SettableValue<Integer> minSigDigs, SettableValue<Integer> maxSigDigs) {
				return SettableValue.asSettable(minSigDigs.transform(tx -> tx//
					.combineWith(maxSigDigs == null ? ObservableValue.of(null) : maxSigDigs)//
					.combine((min, max) -> {
						Format.SuperDoubleFormatBuilder builder = Format.doubleFormat(min);
						if (max != null)
							builder.withSigDigs(min, max);
						if (theMaxIntDigits >= 0)
							builder.printIntFor(theMaxIntDigits, true);
						if (theZeroExp > 0)
							builder.withZeroExp(theZeroExp);
						builder.emptyAllowed(isEmptyAllowed);
						builder.withUnit(theUnit, isUnitRequired);
						if (isMetricPrefixed)
							builder.withMetricPrefixes();
						else if (isMetricPrefixedP2)
							builder.withMetricPrefixesPower2();
						else if (isMetricPrefixed3K)
							builder.withMetricPrefixesPower3K();
						for (Map.Entry<String, Double> prefix : thePrefixMults.entrySet())
							builder.withPrefix(prefix.getKey(), prefix.getValue());
						if (!Double.isNaN(theDefaultPrefixMult))
							builder.withDefaultPrefixMultiplier(theDefaultPrefixMult);
						return builder.build();
					})), __ -> theUModMsg);
			}

			@Override
			protected SettableValue<Format<Double>> copyFormat(SettableValue<Format<Double>> format, ModelSetInstance sourceModels,
				ModelSetInstance newModels) throws ModelInstantiationException {
				SettableValue<Integer> srcMinSigDigs = theMinSignificantDigits.get(sourceModels);
				SettableValue<Integer> srcMaxSigDigs = theMaxSignificantDigits == null ? null : theMaxSignificantDigits.get(sourceModels);
				SettableValue<Integer> newMinSigDigs = theMinSignificantDigits.forModelCopy(srcMinSigDigs, sourceModels, newModels);
				SettableValue<Integer> newMaxSigDigs = theMaxSignificantDigits == null ? null
					: theMaxSignificantDigits.forModelCopy(srcMinSigDigs, sourceModels, newModels);
				if (newMinSigDigs == srcMinSigDigs && newMaxSigDigs == srcMaxSigDigs)
					return format;
				else
					return createFormat(newMinSigDigs, newMaxSigDigs);
			}
		}
	}

	@ExElementTraceable(toolkit = CONFIG, qonfigType = Prefix.PREFIX)
	static class Prefix extends ExElement.Def.Abstract<ExElement.Void> {
		public static final String PREFIX = "prefix";

		private String theName;
		private Integer theExponent;
		private Double theMultiplier;

		public Prefix(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		public String getName() {
			return theName;
		}

		public Integer getExponent() {
			return theExponent;
		}

		public Double getMultiplier() {
			return theMultiplier;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			theName = session.getAttributeText("name");
			String str = session.getAttributeText("exp");
			if (str != null) {
				LocatedPositionedContent mult = session.attributes().get("multiplier").getLocatedContent();
				if (mult != null)
					throw new QonfigInterpretationException("Only one of 'exp', 'multiplier' may be specified", mult.getPosition(0),
						mult.length());
				theExponent = Integer.parseInt(str);
				theMultiplier = null;
			} else {
				str = session.getAttributeText("multiplier");
				if (str == null)
					throw new QonfigInterpretationException("One of 'exp', 'multiplier' may be specified",
						session.getElement().getPositionInFile(), 0);
				theMultiplier = Double.parseDouble(str);
				theExponent = null;
			}
		}
	}

	@ExElementTraceable(toolkit = CONFIG,
		qonfigType = DateFormat.INSTANT_FORMAT,
		interpretation = DateFormat.Interpreted.class,
		instance = DateFormat.Instantiator.class)
	static class DateFormat extends AbstractFormat<Instant> {
		public static final String INSTANT_FORMAT = "instant-format";

		private String theDayFormat;
		private TimeZone theTimeZone;
		private TimeUtils.DateElementType theMaxResolution;
		private boolean isFormat24H;
		private TimeUtils.RelativeInstantEvaluation theRelativeEvaluation;
		private CompiledExpression theRelativeTo;

		public DateFormat(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@QonfigAttributeGetter("day-format")
		public String getDayFormat() {
			return theDayFormat;
		}

		@QonfigAttributeGetter("time-zone")
		public TimeZone getTimeZone() {
			return theTimeZone;
		}

		@QonfigAttributeGetter("max-resolution")
		public TimeUtils.DateElementType getMaxResolution() {
			return theMaxResolution;
		}

		@QonfigAttributeGetter("format-24h")
		public boolean isFormat24H() {
			return isFormat24H;
		}

		@QonfigAttributeGetter("relative-evaluation")
		public TimeUtils.RelativeInstantEvaluation getRelativeEvaluation() {
			return theRelativeEvaluation;
		}

		@QonfigAttributeGetter("relative-to")
		public CompiledExpression getRelativeTo() {
			return theRelativeTo;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);

			theDayFormat = session.getAttributeText("day-format");
			String tz = session.getAttributeText("time-zone");
			theTimeZone = tz == null ? TimeZone.getDefault() : TimeZone.getTimeZone(tz);
			// Above method returns UTC if the string isn't recognized. But we want to tell the user.
			if (tz != null && theTimeZone.getRawOffset() == 0 && !"GMT".equalsIgnoreCase(tz) && !"UTC".equalsIgnoreCase(tz)
				&& !ArrayUtils.contains(TimeZone.getAvailableIDs(), tz)) {
				reporting().error("Unrecognized time zone: " + tz);
				theTimeZone = TimeZone.getDefault();
			}
			try {
				theMaxResolution = TimeUtils.DateElementType.parse(session.getAttributeText("max-resolution"));
				switch (theMaxResolution) {
				case AmPm:
				case TimeZone:
					reporting().error("Invalid max-resolution: " + theMaxResolution);
					theMaxResolution = TimeUtils.DateElementType.Second;
					break;
				case Weekday:
					theMaxResolution = TimeUtils.DateElementType.Day;
					break;
				default:
					// It's fine
				}
			} catch (IllegalArgumentException e) {
				reporting().error("Unrecognized max-resolution: " + session.getAttributeText("max-resolution"), e);
				theMaxResolution = TimeUtils.DateElementType.Second;
			}

			isFormat24H = session.getAttribute("format-24h", boolean.class);
			try {
				theRelativeEvaluation = TimeUtils.RelativeInstantEvaluation.parse(session.getAttributeText("relative-evaluation"));
			} catch (IllegalArgumentException e) {
				reporting().error("Unrecognized max-resolution: " + session.getAttributeText("relative-evaluation"), e);
				theRelativeEvaluation = TimeUtils.RelativeInstantEvaluation.Closest;
			}

			theRelativeTo = getAttributeExpression("relative-to", session);
		}

		@Override
		public Interpreted interpretValue(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}

		static class Interpreted extends AbstractFormat.Interpreted<Instant> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Instant>> theRelativeTo;

			Interpreted(DateFormat definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public DateFormat getDefinition() {
				return (DateFormat) super.getDefinition();
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<Instant>> getRelativeTo() {
				return theRelativeTo;
			}

			@Override
			protected TypeToken<Instant> getValueType() {
				return TypeTokens.get().of(Instant.class);
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);
				theRelativeTo = interpret(getDefinition().getRelativeTo(), ModelTypes.Value.forType(Instant.class));
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return theRelativeTo == null ? Collections.emptyList() : Collections.singletonList(theRelativeTo);
			}

			@Override
			public Instantiator create() throws ModelInstantiationException {
				return new Instantiator(this);
			}
		}

		static class Instantiator extends AbstractFormat.Instantiator<Instant> {
			private final String theDayFormat;
			private final TimeZone theTimeZone;
			private final TimeUtils.DateElementType theMaxResolution;
			private final boolean isFormat24H;
			private final TimeUtils.RelativeInstantEvaluation theRelativeEvaluation;
			private final ModelValueInstantiator<SettableValue<Instant>> theRelativeTo;
			private final String theLocation;

			Instantiator(DateFormat.Interpreted interpreted) throws ModelInstantiationException {
				super(interpreted);
				theDayFormat = interpreted.getDefinition().getDayFormat();
				theTimeZone = interpreted.getDefinition().getTimeZone();
				theMaxResolution = interpreted.getDefinition().getMaxResolution();
				isFormat24H = interpreted.getDefinition().isFormat24H();
				theRelativeEvaluation = interpreted.getDefinition().getRelativeEvaluation();
				theRelativeTo = interpreted.getRelativeTo() == null ? null : interpreted.getRelativeTo().instantiate();
				theLocation = interpreted.reporting().getFileLocation().getPosition(0).toShortString();
			}

			@Override
			public void instantiate() throws ModelInstantiationException {
				super.instantiate();
				if (theRelativeTo != null)
					theRelativeTo.instantiate();
			}

			@Override
			protected SettableValue<Format<Instant>> createFormat(ModelSetInstance models)
				throws ModelInstantiationException, IllegalStateException {
				Supplier<Instant> relativeTo = theRelativeTo == null ? LambdaUtils.printableSupplier(Instant::now, () -> "now", null)
					: theRelativeTo.get(models);
				return SettableValue.of(SpinnerFormat.flexDate(relativeTo, theDayFormat, teo -> teo//
					.withTimeZone(theTimeZone)//
					.withMaxResolution(theMaxResolution)//
					.with24HourFormat(isFormat24H)//
					.withEvaluationType(theRelativeEvaluation)), theLocation + ": Unsettable");
			}

			@Override
			protected SettableValue<Format<Instant>> copyFormat(SettableValue<Format<Instant>> format, ModelSetInstance sourceModels,
				ModelSetInstance newModels) throws ModelInstantiationException {
				if (theRelativeTo == null)
					return format;
				SettableValue<Instant> sourceRT = theRelativeTo.get(sourceModels);
				SettableValue<Instant> newRT = theRelativeTo.forModelCopy(sourceRT, sourceModels, newModels);
				if (sourceRT == newRT)
					return format;
				return SettableValue.of(SpinnerFormat.flexDate(newRT, theDayFormat, teo -> teo//
					.withTimeZone(theTimeZone)//
					.withMaxResolution(theMaxResolution)//
					.with24HourFormat(isFormat24H)//
					.withEvaluationType(theRelativeEvaluation)), theLocation + ": Unsettable");
			}
		}
	}

	@ExElementTraceable(toolkit = CONFIG,
		qonfigType = RegexStringFormat.REGEX_FORMAT_STRING,
		interpretation = RegexStringFormat.Interpreted.class,
		instance = RegexStringFormat.Instantiator.class)
	static class RegexStringFormat extends AbstractFormat<String> {
		public static final String REGEX_FORMAT_STRING = "regex-format-string";

		public static final Format<String> INSTANCE = new Format<String>() {
			@Override
			public void append(StringBuilder text, String value) {
				if (value != null)
					text.append(value);
			}

			@Override
			public String parse(CharSequence text) throws ParseException {
				String str = text.toString();
				try {
					Pattern.compile(str);
				} catch (PatternSyntaxException e) {
					throw new ParseException(e.getMessage(), e.getIndex());
				}
				return str;
			}

			@Override
			public String toString() {
				return REGEX_FORMAT_STRING;
			}
		};

		public RegexStringFormat(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@Override
		public Interpreted interpretValue(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}

		static class Interpreted extends AbstractFormat.Interpreted<String> {
			Interpreted(RegexStringFormat definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public RegexStringFormat getDefinition() {
				return (RegexStringFormat) super.getDefinition();
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return Collections.emptyList();
			}

			@Override
			protected TypeToken<String> getValueType() {
				return TypeTokens.get().STRING;
			}

			@Override
			public Instantiator create() throws ModelInstantiationException {
				return new Instantiator(this);
			}
		}

		static class Instantiator extends AbstractFormat.Instantiator<String> {
			private final String theLocation;

			public Instantiator(RegexStringFormat.Interpreted interpreted) throws ModelInstantiationException {
				super(interpreted);
				theLocation = interpreted.reporting().getFileLocation().getPosition(0).toShortString();
			}

			@Override
			protected SettableValue<Format<String>> createFormat(ModelSetInstance models)
				throws ModelInstantiationException, IllegalStateException {
				return SettableValue.of(INSTANCE, theLocation + ": Unmodifiable");
			}

			@Override
			protected SettableValue<Format<String>> copyFormat(SettableValue<Format<String>> format, ModelSetInstance sourceModels,
				ModelSetInstance newModels) throws ModelInstantiationException {
				return format;
			}
		}
	}

	static class StandardTextFormat<T> extends AbstractFormat<T> {
		public static final String STANDARD_TEXT_FORMAT = "standard-text-format";

		public StandardTextFormat(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@Override
		public Interpreted<T> interpretValue(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<T> extends AbstractFormat.Interpreted<T> {
			private TypeToken<T> theType;
			private Format<T> theFormat;

			Interpreted(StandardTextFormat<T> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			protected TypeToken<T> getValueType() {
				return theType;
			}

			public Format<T> getFormat() {
				return theFormat;
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);

				theType = getAddOn(ExTyped.Interpreted.class).getValueType();
				Format<?> f;
				Class<?> type = TypeTokens.get().unwrap(TypeTokens.getRawType(theType));
				if (type == String.class)
					f = SpinnerFormat.NUMERICAL_TEXT;
				else if (type == int.class)
					f = SpinnerFormat.INT;
				else if (type == long.class)
					f = SpinnerFormat.LONG;
				else if (type == double.class)
					f = Format.doubleFormat(4).build();
				else if (type == float.class)
					f = Format.doubleFormat(4).buildFloat();
				else if (type == boolean.class)
					f = Format.BOOLEAN;
				else if (Enum.class.isAssignableFrom(type))
					f = Format.enumFormat((Class<Enum<?>>) type);
				else if (type == Instant.class)
					f = SpinnerFormat.flexDate(Instant::now, "EEE MMM dd, yyyy", null);
				else if (type == Duration.class)
					f = SpinnerFormat.flexDuration(false);
				else if (type == java.io.File.class)
					f = new Format.FileFormat(true);
				else
					throw new ExpressoInterpretationException("No standard format available for type " + theType, reporting().getPosition(),
						0);
				theFormat = (Format<T>) f;
			}

			@Override
			public Instantiator<T> create() throws ModelInstantiationException {
				return new Instantiator<>(this);
			}
		}

		static class Instantiator<T> extends AbstractFormat.Instantiator<T> {
			private final Format<T> theFormat;
			private final String theLocation;

			Instantiator(StandardTextFormat.Interpreted<T> interpreted) throws ModelInstantiationException {
				super(interpreted);
				theFormat = interpreted.getFormat();
				theLocation = interpreted.reporting().getFileLocation().getPosition(0).toShortString();
			}

			@Override
			protected SettableValue<Format<T>> createFormat(ModelSetInstance models)
				throws ModelInstantiationException, IllegalStateException {
				return SettableValue.of(theFormat, theLocation + ": Unmodifiable");
			}

			@Override
			protected SettableValue<Format<T>> copyFormat(SettableValue<Format<T>> format, ModelSetInstance sourceModels,
				ModelSetInstance newModels) throws ModelInstantiationException {
				return format;
			}
		}
	}

	@ExElementTraceable(toolkit = CONFIG,
		qonfigType = CustomTextFormat.CUSTOM_TEXT_FORMAT,
		interpretation = CustomTextFormat.Interpreted.class,
		instance = CustomTextFormat.Instantiator.class)
	static class CustomTextFormat<T> extends AbstractFormat<T> {
		public static final String CUSTOM_TEXT_FORMAT = "custom-text-format";

		private ModelComponentId theTextAs;
		private ModelComponentId theValueAs;
		private CompiledExpression theCanParse;
		private CompiledExpression theParse;
		private CompiledExpression thePrint;

		CustomTextFormat(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@QonfigAttributeGetter("text-as")
		public ModelComponentId getTextAs() {
			return theTextAs;
		}

		@QonfigAttributeGetter("format-value-as")
		public ModelComponentId getValueAs() {
			return theValueAs;
		}

		@QonfigAttributeGetter("can-parse")
		public CompiledExpression getCanParse() {
			return theCanParse;
		}

		@QonfigAttributeGetter("parse")
		public CompiledExpression getParse() {
			return theParse;
		}

		@QonfigAttributeGetter("print")
		public CompiledExpression getPrint() {
			return thePrint;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);

			ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
			String textAs = session.getAttributeText("text-as");
			String valueAs = session.getAttributeText("format-value-as");
			theTextAs = textAs == null ? null : elModels.getElementValueModelId(textAs);
			theValueAs = elModels.getElementValueModelId(valueAs);
			if (theTextAs != null)
				elModels.satisfyElementValueType(theTextAs, ModelTypes.Value.STRING);
			elModels.satisfyElementValueType(theValueAs, ModelTypes.Value,
				(interp, env) -> ModelTypes.Value.forType(((Interpreted<T>) interp).getValueType(env)));
			theCanParse = getAttributeExpression("can-parse", session);
			theParse = getAttributeExpression("parse", session);
			thePrint = getAttributeExpression("print", session);
			if (theTextAs != null && theParse == null)
				reporting().at(session.attributes().get("text-as").getName()).warn("text-as specified without parse--no use");
			else if (theTextAs == null && theParse == null)
				reporting().at(theParse.getFilePosition()).error("parse specified without text-as");
		}

		@Override
		public Interpreted<T> interpretValue(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<T> extends AbstractFormat.Interpreted<T> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theCanParse;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<T>> theParse;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> thePrint;

			Interpreted(CustomTextFormat<T> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public CustomTextFormat<T> getDefinition() {
				return (CustomTextFormat<T>) super.getDefinition();
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getCanParse() {
				return theCanParse;
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getParse() {
				return theParse;
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getPrint() {
				return thePrint;
			}

			@Override
			protected TypeToken<T> getValueType() {
				return getAddOn(ExTyped.Interpreted.class).getValueType();
			}

			TypeToken<T> getValueType(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				return getAddOn(ExTyped.Interpreted.class).getValueType(env);
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);
				theCanParse = interpret(getDefinition().getCanParse(), ModelTypes.Value.STRING);
				theParse = interpret(getDefinition().getParse(), ModelTypes.Value.forType(getValueType()));
				thePrint = interpret(getDefinition().getPrint(), ModelTypes.Value.STRING);
			}

			@Override
			public Instantiator<T> create() throws ModelInstantiationException {
				return new Instantiator<>(this);
			}
		}

		static class Instantiator<T> extends AbstractFormat.Instantiator<T> {
			private ModelComponentId theTextAs;
			private ModelComponentId theValueAs;
			private ModelValueInstantiator<SettableValue<String>> theCanParse;
			private ModelValueInstantiator<SettableValue<T>> theParse;
			private ModelValueInstantiator<SettableValue<String>> thePrint;
			private ErrorReporting thePrintReporting;
			private ErrorReporting theParseReporting;

			Instantiator(CustomTextFormat.Interpreted<T> interpreted) throws ModelInstantiationException {
				super(interpreted);

				theTextAs = interpreted.getDefinition().getTextAs();
				theValueAs = interpreted.getDefinition().getValueAs();
				theCanParse = interpreted.getCanParse().instantiate();
				theParse = interpreted.getParse() == null ? null : interpreted.getParse().instantiate();
				thePrint = interpreted.getPrint().instantiate();
				thePrintReporting = interpreted.reporting().at(interpreted.getDefinition().getPrint().getFilePosition());
				theParseReporting = theParse == null ? null
					: interpreted.reporting().at(interpreted.getDefinition().getParse().getFilePosition());
			}

			public ModelComponentId getTextAs() {
				return theTextAs;
			}

			public ModelComponentId getValueAs() {
				return theValueAs;
			}

			public ModelValueInstantiator<SettableValue<String>> getCanParse() {
				return theCanParse;
			}

			public ModelValueInstantiator<SettableValue<T>> getParse() {
				return theParse;
			}

			public ModelValueInstantiator<SettableValue<String>> getPrint() {
				return thePrint;
			}

			@Override
			public void instantiate() throws ModelInstantiationException {
				super.instantiate();

				theCanParse.instantiate();
				if (theParse != null)
					theParse.instantiate();
				thePrint.instantiate();
			}

			@Override
			protected SettableValue<Format<T>> createFormat(ModelSetInstance models)
				throws ModelInstantiationException, IllegalStateException {
				boolean parseable = theTextAs != null && theParse != null;
				SettableValue<String> textValue = parseable ? SettableValue.<String> build()//
					.withDescription(theTextAs.getName()).build() : null;
				SettableValue<T> printValue = SettableValue.<T> build()//
					.withDescription(theValueAs.getName()).build();
				SettableValue<String> canParse = parseable ? theCanParse.get(models) : null;
				SettableValue<T> parse = parseable ? theParse.get(models) : null;
				SettableValue<String> print = thePrint.get(models);
				if (theTextAs != null)
					ExFlexibleElementModelAddOn.satisfyElementValue(theTextAs, models, textValue);
				ExFlexibleElementModelAddOn.satisfyElementValue(theValueAs, models, printValue);
				return SettableValue.of(
					new CustomTextFormatValue<>(textValue, printValue, canParse, parse, print, thePrintReporting, theParseReporting),
					reporting().getPosition().toShortString() + ": Format is not reversible");
			}

			@Override
			protected SettableValue<Format<T>> copyFormat(SettableValue<Format<T>> format, ModelSetInstance sourceModels,
				ModelSetInstance newModels) throws ModelInstantiationException {
				CustomTextFormatValue<T> myFormat = (CustomTextFormatValue<T>) format.get();
				boolean parseable = theTextAs != null && theParse != null;
				SettableValue<String> canParse;
				SettableValue<T> parse;
				if (!parseable) {
					canParse = null;
					parse = null;
				} else if (myFormat.theParse == null) {
					canParse = theCanParse.get(newModels);
					parse = theParse.get(newModels);
				} else {
					canParse = theCanParse.forModelCopy(myFormat.theCanParse, sourceModels, newModels);
					parse = theParse.forModelCopy(myFormat.theParse, sourceModels, newModels);
				}

				SettableValue<String> print = thePrint.forModelCopy(myFormat.thePrint, sourceModels, newModels);
				if (Objects.equals(canParse, myFormat.theCanParse) && Objects.equals(parse, myFormat.theParse)
					&& print == myFormat.thePrint)
					return format;
				else {
					SettableValue<String> textValue = parseable ? SettableValue.<String> build()//
						.withDescription(theTextAs.getName()).build() : null;
					SettableValue<T> printValue = SettableValue.<T> build()//
						.withDescription(theValueAs.getName()).build();
					if (theTextAs != null)
						ExFlexibleElementModelAddOn.satisfyElementValue(theTextAs, newModels, textValue);
					ExFlexibleElementModelAddOn.satisfyElementValue(theValueAs, newModels, printValue);
					return SettableValue.of(
						new CustomTextFormatValue<>(textValue, printValue, canParse, parse, print, thePrintReporting, theParseReporting),
						reporting().getPosition().toShortString() + ": Format is not reversible");
				}
			}
		}

		static class CustomTextFormatValue<T> implements Format<T> {
			final SettableValue<String> theTextAs;
			final SettableValue<T> theValueAs;
			final SettableValue<String> theCanParse;
			final SettableValue<T> theParse;
			final SettableValue<String> thePrint;
			final ErrorReporting thePrintReporting;
			final ErrorReporting theParseReporting;

			CustomTextFormatValue(SettableValue<String> textAs, SettableValue<T> valueAs, SettableValue<String> canParse,
				SettableValue<T> parse, SettableValue<String> print, ErrorReporting printReporting, ErrorReporting parseReporting) {
				theTextAs = textAs;
				theValueAs = valueAs;
				theCanParse = canParse;
				theParse = parse;
				thePrint = print;
				thePrintReporting = printReporting;
				theParseReporting = parseReporting;
			}

			@Override
			public void append(StringBuilder text, T value) {
				theValueAs.set(value);
				try {
					text.append(thePrint.get());
				} catch (RuntimeException e) {
					e.printStackTrace();
					thePrintReporting.error("Error printing value " + value, e);
				}
			}

			@Override
			public T parse(CharSequence text) throws ParseException {
				if (theTextAs == null)
					throw new ParseException("This format cannot parse values. Both the text-as and parse attributes must be specified.",
						0);
				theTextAs.set(text.toString());
				String canParse = theCanParse.get();
				if (canParse != null)
					throw new ParseException(canParse, 0);
				try {
					return theParse.get();
				} catch (RuntimeException e) {
					e.printStackTrace();
					thePrintReporting.error("Error parsing value from '" + text + "'", e);
					return null;
				}
			}

			@Override
			public String toString() {
				if (theParse != null)
					return "Format:" + theParse + "/" + thePrint;
				else
					return "Format:" + thePrint;
			}
		}
	}

	static class FilterValidation<T> implements FormatValidation<T> {
		public static final String FILTER_VALIDATION = "filter-validation";

		@ExElementTraceable(toolkit = CONFIG,
			qonfigType = FILTER_VALIDATION,
			interpretation = Interpreted.class,
			instance = Instantiator.class)
		static class Def extends ExElement.Def.Abstract<Instantiator<?>> implements FormatValidation.Def<Instantiator<?>> {
			private ModelComponentId theFilterValueVariable;
			private CompiledExpression theTest;

			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
				super(parent, qonfigType);
			}

			@QonfigAttributeGetter("filter-value-name")
			public ModelComponentId getFilterValueVariable() {
				return theFilterValueVariable;
			}

			@QonfigAttributeGetter("test")
			public CompiledExpression getTest() {
				return theTest;
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session);

				ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
				theFilterValueVariable = elModels.getElementValueModelId(session.getAttributeText("filter-value-name"));
				elModels.satisfyElementValueType(theFilterValueVariable, ModelTypes.Value,
					(interp, env) -> ModelTypes.Value.forType(((Interpreted<?>) interp).getValueType()));
				theTest = getAttributeExpression("test", session);
			}

			@Override
			public Interpreted<?> interpret(ExElement.Interpreted<?> parent) {
				return new Interpreted<>(this, parent);
			}
		}

		static class Interpreted<T> extends ExElement.Interpreted.Abstract<Instantiator<T>>
		implements FormatValidation.Interpreted<T, Instantiator<T>> {
			private TypeToken<T> theValueType;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theTest;

			Interpreted(Def definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Def getDefinition() {
				return (Def) super.getDefinition();
			}

			public TypeToken<T> getValueType() {
				return theValueType;
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getTest() {
				return theTest;
			}

			@Override
			public void updateFormat(InterpretedExpressoEnv env, TypeToken<T> valueType) throws ExpressoInterpretationException {
				theValueType = valueType;
				update(env);
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv expressoEnv) throws ExpressoInterpretationException {
				super.doUpdate(expressoEnv);
				theTest = ExpressoTransformations.parseFilter(getDefinition().getTest(), this, true);
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return Arrays.asList(theTest);
			}

			@Override
			public Instantiator<T> create() {
				return new Instantiator<>(getIdentity());
			}
		}

		static class Instantiator<T> extends ExElement.Abstract implements FormatValidation.Instantiator<T, FilterValidation<T>> {
			private ModelComponentId theFilterValueVariable;
			private ModelValueInstantiator<SettableValue<String>> theTest;

			Instantiator(Object id) {
				super(id);
			}

			@Override
			protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
				super.doUpdate(interpreted);

				FilterValidation.Interpreted<T> myInterpreted = (FilterValidation.Interpreted<T>) interpreted;
				theFilterValueVariable = myInterpreted.getDefinition().getFilterValueVariable();
				theTest = myInterpreted.getTest().instantiate();
			}

			@Override
			public void instantiated() throws ModelInstantiationException {
				super.instantiated();
				theTest.instantiate();
			}

			@Override
			protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
				super.doInstantiate(myModels);
			}

			@Override
			public FilterValidation<T> get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
				SettableValue<T> filterValue = SettableValue.<T> build().build();
				ExFlexibleElementModelAddOn.satisfyElementValue(theFilterValueVariable, models, filterValue);
				SettableValue<String> test = theTest.get(models);
				return new FilterValidation<>(filterValue, test);
			}

			@Override
			public FilterValidation<T> forModelCopy(FilterValidation<T> value, ModelSetInstance sourceModels, ModelSetInstance newModels)
				throws ModelInstantiationException {
				SettableValue<String> newTest = theTest.forModelCopy(value.getTest(), sourceModels, newModels);
				if (newTest != value.getTest()) {
					SettableValue<T> filterValue = SettableValue.<T> build().build();
					ExFlexibleElementModelAddOn.satisfyElementValue(theFilterValueVariable, newModels, filterValue);
					return new FilterValidation<>(filterValue, newTest);
				} else
					return value;
			}
		}

		private final SettableValue<T> theValue;
		private final SettableValue<String> theTest;

		FilterValidation(SettableValue<T> value, SettableValue<String> test) {
			theValue = value;
			theTest = test;
		}

		SettableValue<T> getValue() {
			return theValue;
		}

		SettableValue<String> getTest() {
			return theTest;
		}

		@Override
		public String test(T value, CharSequence text) {
			theValue.set(value, null);
			return theTest.get();
		}
	}

	@ExElementTraceable(toolkit = CONFIG,
		qonfigType = ListFormat.LIST_FORMAT,
		interpretation = ListFormat.Interpreted.class,
		instance = ListFormat.Instantiator.class)
	static class ListFormat<T> extends AbstractFormat<Collection<T>> {
		public static final String LIST_FORMAT = "list-format";

		private CompiledExpression theComponentFormat;
		private boolean isDistinct;
		private CompiledExpression theDelimiter;
		private CompiledExpression thePostDelimiter;

		public ListFormat(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@QonfigAttributeGetter("component-format")
		public CompiledExpression getComponentFormat() {
			return theComponentFormat;
		}

		@QonfigAttributeGetter("distinct")
		public boolean isDistinct() {
			return isDistinct;
		}

		@QonfigAttributeGetter("delimiter")
		public CompiledExpression getDelimiter() {
			return theDelimiter;
		}

		@QonfigAttributeGetter("post-delimiter")
		public CompiledExpression getPostDelimiter() {
			return thePostDelimiter;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);

			theComponentFormat = getAttributeExpression("component-format", session);
			isDistinct = session.getAttribute("distinct", boolean.class);
			theDelimiter = getAttributeExpression("delimiter", session);
			thePostDelimiter = getAttributeExpression("post-delimiter", session);
		}

		@Override
		public Interpreted<T> interpretValue(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<T> extends AbstractFormat.Interpreted<Collection<T>> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Format<T>>> theComponentFormat;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theDelimiter;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> thePostDelimiter;
			private TypeToken<Collection<T>> theValueType;

			Interpreted(ListFormat<T> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public ListFormat<T> getDefinition() {
				return (ListFormat<T>) super.getDefinition();
			}

			@Override
			protected TypeToken<Collection<T>> getValueType() {
				return theValueType;
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<Format<T>>> getComponentFormat() {
				return theComponentFormat;
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getDelimiter() {
				return theDelimiter;
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getPostDelimiter() {
				return thePostDelimiter;
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				theComponentFormat = interpret(getDefinition().getComponentFormat(), ModelTypes.Value.forType(//
					TypeTokens.get().keyFor(Format.class).wildCard()));
				Class<Collection<?>> collType = (Class<Collection<?>>) (Class<?>) (getDefinition().isDistinct() ? Set.class : List.class);
				theValueType = TypeTokens.get().keyFor(collType).parameterized(//
					theComponentFormat.getType().getType(0).resolveType(Format.class.getTypeParameters()[0]));
				super.doUpdate(env);
				theDelimiter = interpret(getDefinition().getDelimiter(), ModelTypes.Value.STRING);
				thePostDelimiter = interpret(getDefinition().getPostDelimiter(), ModelTypes.Value.STRING);
			}

			@Override
			public ListFormat.Instantiator<T> create() throws ModelInstantiationException {
				return new Instantiator<>(this);
			}
		}

		static class Instantiator<T> extends AbstractFormat.Instantiator<Collection<T>> {
			private String theLocationString;
			private ModelValueInstantiator<SettableValue<Format<T>>> theComponentFormat;
			private ErrorReporting theComponentFormatReporting;
			private boolean isDistinct;
			private ModelValueInstantiator<SettableValue<String>> theDelimiter;
			private ErrorReporting theDelimiterReporting;
			private ModelValueInstantiator<SettableValue<String>> thePostDelimiter;

			Instantiator(ListFormat.Interpreted<T> interpreted) throws ModelInstantiationException {
				super(interpreted);

				theLocationString = interpreted.reporting().getPosition().toShortString();
				theComponentFormat = interpreted.getComponentFormat().instantiate();
				theComponentFormatReporting = interpreted.reporting()
					.at(interpreted.getDefinition().getComponentFormat().getFilePosition());
				isDistinct = interpreted.getDefinition().isDistinct();
				theDelimiter = interpreted.getDelimiter().instantiate();
				theDelimiterReporting = interpreted.reporting().at(interpreted.getDefinition().getDelimiter().getFilePosition());
				thePostDelimiter = interpreted.getPostDelimiter().instantiate();
			}

			@Override
			public void instantiate() throws ModelInstantiationException {
				super.instantiate();
				theComponentFormat.instantiate();
				theDelimiter.instantiate();
				thePostDelimiter.instantiate();
			}

			@Override
			protected SettableValue<Format<Collection<T>>> createFormat(ModelSetInstance models)
				throws ModelInstantiationException, IllegalStateException {
				SettableValue<Format<T>> componentFormat = theComponentFormat.get(models);
				SettableValue<String> delimiter = theDelimiter.get(models);
				SettableValue<String> postDelimiter = thePostDelimiter.get(models);
				return new ListFormatValue<>(theLocationString, componentFormat, theComponentFormatReporting, isDistinct, delimiter,
					theDelimiterReporting, postDelimiter);
			}

			@Override
			protected SettableValue<Format<Collection<T>>> copyFormat(SettableValue<Format<Collection<T>>> format,
				ModelSetInstance sourceModels,
				ModelSetInstance newModels) throws ModelInstantiationException {
				ListFormatValue<T> myValue = (ListFormatValue<T>) format;
				SettableValue<Format<T>> componentFormat = theComponentFormat.forModelCopy(myValue.theComponentFormat, sourceModels,
					newModels);
				SettableValue<String> delimiter = theDelimiter.forModelCopy(myValue.theDelimiter, sourceModels, newModels);
				SettableValue<String> postDelimiter = thePostDelimiter.forModelCopy(myValue.thePostDelimiter, sourceModels, newModels);
				if (componentFormat == myValue.theComponentFormat && delimiter == myValue.theDelimiter
					&& postDelimiter == myValue.thePostDelimiter)
					return myValue;
				else
					return new ListFormatValue<>(theLocationString, componentFormat, theComponentFormatReporting, isDistinct, delimiter,
						theDelimiterReporting, postDelimiter);
			}
		}

		static class ListFormatValue<T> extends SettableValue.WrappingSettableValue<Format<Collection<T>>> {
			final SettableValue<Format<T>> theComponentFormat;
			final boolean isDistinct;
			final SettableValue<String> theDelimiter;
			final SettableValue<String> thePostDelimiter;

			ListFormatValue(String formatLocation, SettableValue<Format<T>> componentFormat, ErrorReporting componentFormatReporting,
				boolean distinct,
				SettableValue<String> delimiter, ErrorReporting delimiterReporting, SettableValue<String> postDelimiter) {
				super(SettableValue.asSettable(componentFormat.<Format<Collection<T>>> transform(tx -> tx//
					.combineWith(delimiter)//
					.combineWith(postDelimiter).build((cf, txvs) -> {
						if (cf == null) {
							componentFormatReporting.error("Component format is null");
							return null;
						}
						String delimit = txvs.get(delimiter);
						if(delimit==null || delimit.isEmpty()) {
							delimiterReporting.warn("No delimiter--using default (,)");
							delimit = ",";
						}
						String postDelimit = txvs.get(postDelimiter);
						return new Format.CollectionFormat<>(cf, delimit, postDelimit, "Duplicate values not permitted",
							() -> distinct ? new LinkedHashSet<>() : new ArrayList<>());
					})), __ -> formatLocation + ": Format is not settable"));
				theComponentFormat = componentFormat;
				this.isDistinct = distinct;
				theDelimiter = delimiter;
				thePostDelimiter = postDelimiter;
			}
		}
	}

	@ExElementTraceable(toolkit = CONFIG,
		qonfigType = TextConfigFormat.TEXT_CONFIG_FORMAT,
		interpretation = TextConfigFormat.Interpreted.class,
		instance = TextConfigFormat.Instantiator.class)
	static class TextConfigFormat extends
	ModelValueElement.Def.SingleTyped<SettableValue<?>, ModelValueElement<? extends SettableValue<? extends ObservableConfigFormat<?>>>>
	implements
	ModelValueElement.CompiledSynth<SettableValue<?>, ModelValueElement<? extends SettableValue<? extends ObservableConfigFormat<?>>>> {
		public static final String TEXT_CONFIG_FORMAT = "text-config-format";
		public static final String INTERPRETED_TYPE_MANAGED = "Expresso.Config.Text.Format.Type.Managed";

		private CompiledExpression theTextFormat;
		private CompiledExpression theDefaultValue;
		private String theDefaultText;

		public TextConfigFormat(Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, ModelTypes.Value);
		}

		@QonfigAttributeGetter("text-format")
		public CompiledExpression getTextFormat() {
			return theTextFormat;
		}

		@QonfigAttributeGetter("default")
		public CompiledExpression getDefaultValue() {
			return theDefaultValue;
		}

		@QonfigAttributeGetter("default-text")
		public String getDefaultText() {
			return theDefaultText;
		}

		@Override
		protected void doPrepare(ExpressoQIS session) throws QonfigInterpretationException {
			VariableType type = getAddOn(ExTyped.Def.class).getValueType();
			theTextFormat = getAttributeExpression("text-format", session);
			if (type == null && theTextFormat == null) {
				if (!Boolean.TRUE.equals(session.get(INTERPRETED_TYPE_MANAGED)))
					throw new QonfigInterpretationException("Either 'type' or 'text-format' must be specified",
						session.getElement().getPositionInFile(), 0);
			} else if (type != null && theTextFormat != null)
				throw new QonfigInterpretationException("Only one of 'type' or 'text-format' may be specified",
					session.getElement().getPositionInFile(), 0);
			theDefaultValue = getAttributeExpression("default", session);
			theDefaultText = session.getAttributeText("default-text");
			if (theDefaultValue != null && theDefaultText != null)
				throw new QonfigInterpretationException("Only one of 'default' or 'default-text' may be specified",
					session.getElement().getPositionInFile(), 0);
		}

		@Override
		public Interpreted<?> interpretValue(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<T> extends
		ModelValueElement.Def.SingleTyped.Interpreted<SettableValue<?>, SettableValue<ObservableConfigFormat<T>>, ModelValueElement<SettableValue<ObservableConfigFormat<T>>>>
		implements
		ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<ObservableConfigFormat<T>>, ModelValueElement<SettableValue<ObservableConfigFormat<T>>>> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Format<T>>> theTextFormat;
			private Format<T> theDefaultFormat;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<T>> theDefaultValue;

			Interpreted(TextConfigFormat definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public TextConfigFormat getDefinition() {
				return (TextConfigFormat) super.getDefinition();
			}

			protected TypeToken<T> getValueType() {
				TypeToken<T> type = getAddOn(ExTyped.Interpreted.class).getValueType();
				return type != null ? type
					: (TypeToken<T>) theTextFormat.getType().getType(0).resolveType(Format.class.getTypeParameters()[0]);
			}

			@Override
			protected ModelInstanceType<SettableValue<?>, SettableValue<ObservableConfigFormat<T>>> getTargetType() {
				return ModelTypes.Value.forType(
					TypeTokens.get().keyFor(ObservableConfigFormat.class).<ObservableConfigFormat<T>> parameterized(getValueType()));
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<Format<T>>> getTextFormat() {
				return theTextFormat;
			}

			public Format<T> getDefaultFormat() {
				return theDefaultFormat;
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getDefaultValue() {
				return theDefaultValue;
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);
				TypeToken<T> type = getAddOn(ExTyped.Interpreted.class).getValueType();
				ModelInstanceType<SettableValue<?>, SettableValue<Format<T>>> formatType;
				if (type != null)
					formatType = ModelTypes.Value.forType(TypeTokens.get().keyFor(Format.class).<Format<T>> parameterized(type));
				else
					formatType = ModelTypes.Value.anyAsV();
				theTextFormat = interpret(getDefinition().getTextFormat(), formatType);

				if (theTextFormat == null) {
					if (type == null)
						throw new ExpressoInterpretationException(
							"If no text-format is specified, a type must be specified to determine a default text format",
							reporting().getPosition(), 0);
					try {
						ObservableConfigFormat<T> defaultFormat = new ObservableConfigFormatSet().getConfigFormat(type, null);
						if (!(defaultFormat instanceof ObservableConfigFormat.Impl.SimpleConfigFormat))
							throw new IllegalArgumentException();
						theDefaultFormat = ((ObservableConfigFormat.Impl.SimpleConfigFormat<T>) defaultFormat).format;
					} catch (IllegalArgumentException e) {
						throw new ExpressoInterpretationException(
							"No default text format available for type " + type + ". 'text-format' must be specified.",
							reporting().getFileLocation(), e);
					}
				}

				theDefaultValue = interpret(getDefinition().getDefaultValue(), ModelTypes.Value.forType(getValueType()));
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				List<InterpretedValueSynth<?, ?>> components = new ArrayList<>();
				if (theTextFormat != null)
					components.add(theTextFormat);
				if (theDefaultValue != null)
					components.add(theDefaultValue);
				return components;
			}

			@Override
			public Instantiator<T> create() throws ModelInstantiationException {
				return new Instantiator<>(this);
			}
		}

		static class Instantiator<T> extends ModelValueElement.Abstract<SettableValue<ObservableConfigFormat<T>>> {
			private final ModelValueInstantiator<SettableValue<Format<T>>> theTextFormat;
			private final Format<T> theDefaultFormat;
			private final ModelValueInstantiator<SettableValue<T>> theDefaultValue;
			private final String theDefaultText;
			private final T theStaticDefaultValue;
			private final ErrorReporting theReporting;

			Instantiator(TextConfigFormat.Interpreted<T> interpreted) throws ModelInstantiationException {
				super(interpreted);
				theTextFormat = interpreted.getTextFormat() == null ? null : interpreted.getTextFormat().instantiate();
				theDefaultFormat = interpreted.getDefaultFormat();
				theDefaultValue = interpreted.getDefaultValue() == null ? null : interpreted.getDefaultValue().instantiate();
				theDefaultText = interpreted.getDefinition().getDefaultText();
				theStaticDefaultValue = TypeTokens.get().getDefaultValue(interpreted.getValueType());
				theReporting = interpreted.reporting();
			}

			@Override
			public void instantiate() throws ModelInstantiationException {
				if (theTextFormat != null)
					theTextFormat.instantiate();
				if (theDefaultValue != null)
					theDefaultValue.instantiate();
			}

			@Override
			public SettableValue<ObservableConfigFormat<T>> get(ModelSetInstance models)
				throws ModelInstantiationException, IllegalStateException {
				instantiate(models);
				SettableValue<Format<T>> textFormat;
				if (theTextFormat != null)
					textFormat = theTextFormat.get(models);
				else
					textFormat = SettableValue.of(theDefaultFormat, "Unmodifiable");
				ObservableValue<T> defaultValue;
				if (theDefaultValue != null)
					defaultValue = theDefaultValue.get(models);
				else if (theDefaultText != null) {
					defaultValue = textFormat.map(tf -> {
						try {
							return tf.parse(theDefaultText);
						} catch (ParseException e) {
							theReporting.error("Could not parse default value", e);
							return theStaticDefaultValue;
						}
					});
				} else
					defaultValue = ObservableValue.of(theStaticDefaultValue);
				String uModMsg = theReporting.getFileLocation().getPosition(0).toShortString() + ": Unmodifiable";
				return SettableValue.asSettable(textFormat.map(tf -> ObservableConfigFormat.ofQommonFormat(tf, defaultValue)),
					__ -> uModMsg);
			}

			@Override
			public SettableValue<ObservableConfigFormat<T>> forModelCopy(SettableValue<ObservableConfigFormat<T>> value,
				ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				SettableValue<Format<T>> srcTextFormat, newTextFormat;
				if (theTextFormat != null) {
					srcTextFormat = theTextFormat.get(sourceModels);
					newTextFormat = theTextFormat.forModelCopy(srcTextFormat, sourceModels, newModels);
				} else
					srcTextFormat = newTextFormat = SettableValue.of(theDefaultFormat, "Unmodifiable");

				ObservableValue<T> srcDefaultValue, newDefaultValue;
				if (theDefaultValue != null) {
					srcDefaultValue = theDefaultValue.get(sourceModels);
					newDefaultValue = theDefaultValue.forModelCopy((SettableValue<T>) srcDefaultValue, sourceModels, newModels);
				} else if (theDefaultText != null) {
					srcDefaultValue = newDefaultValue = newTextFormat.map(tf -> {
						try {
							return tf.parse(theDefaultText);
						} catch (ParseException e) {
							theReporting.error("Could not parse default value", e);
							return theStaticDefaultValue;
						}
					});
				} else
					srcDefaultValue = newDefaultValue = ObservableValue.of(theStaticDefaultValue);
				if (srcTextFormat == newTextFormat && srcDefaultValue == newDefaultValue)
					return value;
				String uModMsg = theReporting.getFileLocation().getPosition(0).toShortString() + ": Unmodifiable";
				return SettableValue.asSettable(newTextFormat.map(tf -> ObservableConfigFormat.ofQommonFormat(tf, newDefaultValue)),
					__ -> uModMsg);
			}
		}
	}

	@ExElementTraceable(toolkit = CONFIG,
		qonfigType = EntityConfigFormat.ENTITY_CONFIG_FORMAT,
		interpretation = EntityConfigFormat.Interpreted.class,
		instance = EntityConfigFormat.Instantiator.class)
	static class EntityConfigFormat extends
	ModelValueElement.Def.SingleTyped<SettableValue<?>, ModelValueElement<? extends SettableValue<? extends ObservableConfigFormat<?>>>>
	implements
	ModelValueElement.CompiledSynth<SettableValue<?>, ModelValueElement<? extends SettableValue<? extends ObservableConfigFormat<?>>>> {
		public static final String ENTITY_CONFIG_FORMAT = "entity-config-format";

		private CompiledExpression theFormatSet;
		private final Map<String, ModelValueElement.CompiledSynth<SettableValue<?>, ModelValueElement<? extends SettableValue<ObservableConfigFormat<?>>>>> theFields;

		public EntityConfigFormat(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, ModelTypes.Value);
			theFields = new LinkedHashMap<>();
		}

		@QonfigAttributeGetter("format-set")
		public CompiledExpression getFormatSet() {
			return theFormatSet;
		}

		@QonfigChildGetter("field")
		public Map<String, ModelValueElement.CompiledSynth<SettableValue<?>, ModelValueElement<? extends SettableValue<ObservableConfigFormat<?>>>>> getFields() {
			return Collections.unmodifiableMap(theFields);
		}

		@Override
		protected void doPrepare(ExpressoQIS session) throws QonfigInterpretationException {
			theFormatSet = getAttributeExpression("format-set", session);
			List<ModelValueElement.CompiledSynth<SettableValue<?>, ModelValueElement<? extends SettableValue<ObservableConfigFormat<?>>>>> fields;
			fields = new ArrayList<>(theFields.values());
			syncChildren(ModelValueElement.CompiledSynth.class, fields, session.forChildren("field"), (f, s) -> {
				s = s.put(ExTyped.VALUE_TYPE_KEY, null).putLocal(TextConfigFormat.INTERPRETED_TYPE_MANAGED, true);
				f.update(s);
				f.prepareModelValue(s);
			});
			theFields.clear();
			for (ModelValueElement.CompiledSynth<SettableValue<?>, ModelValueElement<? extends SettableValue<ObservableConfigFormat<?>>>> field : fields) {
				String fieldName = field.getAddOn(EntityConfigField.class).getFieldName();
				if (theFields.containsKey(fieldName))
					reporting().warn("Multiple fields specified named '" + fieldName + "': using the first specification");
				else
					theFields.put(fieldName, field);
			}
		}

		@Override
		public Interpreted<?> interpretValue(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<E> extends
		ModelValueElement.Def.SingleTyped.Interpreted<SettableValue<?>, SettableValue<ObservableConfigFormat<E>>, ModelValueElement<SettableValue<ObservableConfigFormat<E>>>>
		implements
		ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<ObservableConfigFormat<E>>, ModelValueElement<SettableValue<ObservableConfigFormat<E>>>> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<ObservableConfigFormatSet>> theFormatSet;
			private final Map<String, ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<ObservableConfigFormat<E>>, ModelValueElement<SettableValue<ObservableConfigFormat<E>>>>> theFields;

			Interpreted(EntityConfigFormat definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
				theFields = new LinkedHashMap<>();
			}

			@Override
			public EntityConfigFormat getDefinition() {
				return (EntityConfigFormat) super.getDefinition();
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<ObservableConfigFormatSet>> getFormatSet() {
				return theFormatSet;
			}

			public Map<String, ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<ObservableConfigFormat<E>>, ModelValueElement<SettableValue<ObservableConfigFormat<E>>>>> getFields() {
				return Collections.unmodifiableMap(theFields);
			}

			protected TypeToken<ObservableConfigFormat<E>> getValueType() {
				TypeToken<E> type = getAddOn(ExTyped.Interpreted.class).getValueType();
				return TypeTokens.get().keyFor(ObservableConfigFormat.class).<ObservableConfigFormat<E>> parameterized(type);
			}

			@Override
			protected ModelInstanceType<SettableValue<?>, SettableValue<ObservableConfigFormat<E>>> getTargetType() {
				return ModelTypes.Value.forType(getValueType());
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return new ArrayList<InterpretedValueSynth<?, ?>>(theFields.values());
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);
				theFormatSet = interpret(getDefinition().getFormatSet(), ModelTypes.Value.forType(ObservableConfigFormatSet.class));
				Set<String> fieldNames = new LinkedHashSet<>(getDefinition().getFields().keySet());
				TypeToken<E> entityType = getAddOn(ExTyped.Interpreted.class).getValueType();
				EntityReflector<E> reflector = EntityReflector.build(entityType, true).build();
				List<ModelValueElement.CompiledSynth<SettableValue<?>, ModelValueElement<? extends SettableValue<ObservableConfigFormat<?>>>>> defFields;
				defFields = new ArrayList<>();
				for (int f = 0; f < reflector.getFields().keySize(); f++) {
					ModelValueElement.CompiledSynth<SettableValue<?>, ModelValueElement<? extends SettableValue<ObservableConfigFormat<?>>>> defField;
					String fieldName = reflector.getFields().keySet().get(f);
					defField = getDefinition().getFields().get(fieldName);
					if (defField == null)
						continue;
					fieldNames.remove(fieldName);
					defFields.add(defField);
				}
				if (!fieldNames.isEmpty()) {
					String msg = "No such field" + (fieldNames.size() == 1 ? "" : "s") + ": " + entityType + ".";
					if (fieldNames.size() == 1)
						msg += fieldNames.iterator().next();
					else
						msg += fieldNames;
					msg += "\nAvailable fields are " + reflector.getFields().keySet();
					reporting().warn(msg);
				}
				List<ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<ObservableConfigFormat<E>>, ModelValueElement<SettableValue<ObservableConfigFormat<E>>>>> fields;
				fields = new ArrayList<>(theFields.values());
				syncChildren(defFields, fields,
					f -> (ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<ObservableConfigFormat<E>>, ModelValueElement<SettableValue<ObservableConfigFormat<E>>>>) f
					.interpretValue(this),
					(i, mEnv) -> {
						String fieldName = i.getDefinition().getAddOn(EntityConfigField.class).getFieldName();
						mEnv.putLocal(ExTyped.VALUE_TYPE_KEY, reflector.getFields().get(fieldName).getType());
						i.updateValue(mEnv);
					});
				theFields.clear();
				for (ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<ObservableConfigFormat<E>>, ModelValueElement<SettableValue<ObservableConfigFormat<E>>>> field : fields)
					theFields.put(field.getDefinition().getAddOn(EntityConfigField.class).getFieldName(), field);
			}

			@Override
			public ModelValueElement<SettableValue<ObservableConfigFormat<E>>> create() throws ModelInstantiationException {
				return new Instantiator<>(this);
			}
		}

		static class Instantiator<E> extends ModelValueElement.Abstract<SettableValue<ObservableConfigFormat<E>>> {
			private final TypeToken<E> theEntityType;
			private final ModelValueInstantiator<SettableValue<ObservableConfigFormatSet>> theFormatSet;
			private final Map<String, String> theFieldConfigNames;
			private final Map<String, ModelValueInstantiator<? extends SettableValue<? extends ObservableConfigFormat<?>>>> theFields;

			Instantiator(EntityConfigFormat.Interpreted<E> interpreted) throws ModelInstantiationException {
				super(interpreted);
				theEntityType = interpreted.getAddOn(ExTyped.Interpreted.class).getValueType();
				theFormatSet = interpreted.getFormatSet() == null ? null : interpreted.getFormatSet().instantiate();
				theFieldConfigNames = new LinkedHashMap<>();
				theFields = new LinkedHashMap<>();
				for (Map.Entry<String, InterpretedSynth<SettableValue<?>, SettableValue<ObservableConfigFormat<E>>, ModelValueElement<SettableValue<ObservableConfigFormat<E>>>>> field : interpreted
					.getFields().entrySet()) {
					String configName = field.getValue().getDefinition().getAddOn(EntityConfigField.class).getConfigName();
					if (configName != null)
						theFieldConfigNames.put(field.getKey(), configName);
					theFields.put(field.getKey(), field.getValue().instantiate());
				}
			}

			@Override
			public void instantiate() throws ModelInstantiationException {
				if (theFormatSet != null)
					theFormatSet.instantiate();
				for (ModelValueInstantiator<? extends SettableValue<? extends ObservableConfigFormat<?>>> field : theFields.values())
					field.instantiate();
			}

			@Override
			public SettableValue<ObservableConfigFormat<E>> get(ModelSetInstance models)
				throws ModelInstantiationException, IllegalStateException {
				instantiate(models);
				ObservableConfigFormatSet formatSet;
				if (theFormatSet != null)
					formatSet = theFormatSet.get(models).get();
				else
					formatSet = new ObservableConfigFormatSet();
				ObservableConfigFormat.EntityFormatBuilder<E> builder = ObservableConfigFormat.buildEntities(theEntityType, formatSet);
				for (Map.Entry<String, ModelValueInstantiator<? extends SettableValue<? extends ObservableConfigFormat<?>>>> field : theFields
					.entrySet()) {
					String configName = theFieldConfigNames.get(field.getKey());
					if (configName != null)
						builder.withFieldChildName(field.getKey(), configName);
					builder.withFieldFormat(field.getKey(), field.getValue().get(models).get());
				}
				return SettableValue.of(builder.build(), "Unmodifiable");
			}

			@Override
			public SettableValue<ObservableConfigFormat<E>> forModelCopy(SettableValue<ObservableConfigFormat<E>> value,
				ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				// Meh
				return get(newModels);
			}
		}
	}

	@ExElementTraceable(toolkit = CONFIG, qonfigType = EntityConfigField.ENTITY_CONFIG_FIELD)
	static class EntityConfigField extends ExAddOn.Def.Abstract<ExElement, ExAddOn.Void<ExElement>> {
		public static final String ENTITY_CONFIG_FIELD = "entity-config-field";

		private String theFieldName;
		private String theConfigName;

		public EntityConfigField(QonfigAddOn type, ExElement.Def<?> element) {
			super(type, element);
		}

		@QonfigAttributeGetter("field-name")
		public String getFieldName() {
			return theFieldName;
		}

		@QonfigAttributeGetter("config-name")
		public String getConfigName() {
			return theConfigName;
		}

		@Override
		public void update(ExpressoQIS session, ExElement.Def<? extends ExElement> element) throws QonfigInterpretationException {
			super.update(session, element);
			theFieldName = session.getAttributeText("field-name");
			theConfigName = session.getAttributeText("config-name");
		}

		@Override
		public <E2 extends ExElement> ExAddOn.Interpreted<? super E2, ? extends Void<ExElement>> interpret(
			ExElement.Interpreted<E2> element) {
			return null;
		}
	}
}
