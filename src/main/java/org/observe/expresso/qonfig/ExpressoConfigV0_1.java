package org.observe.expresso.qonfig;

import java.text.ParseException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.assoc.ObservableMap;
import org.observe.config.ObservableConfig;
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
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.VariableType;
import org.observe.expresso.qonfig.ExElement.Def;
import org.observe.expresso.qonfig.ModelValueElement.CompiledSynth;
import org.observe.expresso.qonfig.ModelValueElement.InterpretedSynth;
import org.observe.util.TypeTokens;
import org.qommons.ArrayUtils;
import org.qommons.LambdaUtils;
import org.qommons.QommonsUtils;
import org.qommons.TimeUtils;
import org.qommons.Version;
import org.qommons.collect.CollectionUtils;
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
	// /** Equivalent of a {@link CompiledModelValue} for a &lt;validate> element */
	// public interface ValidationProducer {
	// /**
	// * @param <T> The type to validate
	// * @param formatType The type to validate
	// * @return A Validation to create the actual validator
	// * @throws ExpressoInterpretationException If the validator cannot be created
	// */
	// <T> Validation<T> createValidator(TypeToken<T> formatType) throws ExpressoInterpretationException;
	// }
	//
	// /**
	// * Equivalent of a {@link ModelValueSynth} for a &lt;validate> element
	// *
	// * @param <V> The type to validate
	// */
	// public interface Validation<V> {
	// /**
	// * @param models The model instance for the test to use
	// * @return The actual test
	// * @throws ModelInstantiationException If the test cannot be created
	// */
	// Function<V, String> getTest(ModelSetInstance models) throws ModelInstantiationException;
	//
	// /**
	// * Equivalent of {@link ModelValueSynth#forModelCopy(Object, ModelSetInstance, ModelSetInstance)}
	// *
	// * @param validator The validator from the source models
	// * @param sourceModels The source models
	// * @param newModels The new models
	// * @return The validator for the new models
	// * @throws ModelInstantiationException If the new test cannot be created
	// */
	// Function<V, String> forModelCopy(Function<V, String> validator, ModelSetInstance sourceModels, ModelSetInstance newModels)
	// throws ModelInstantiationException;
	//
	// /**
	// * Equivalent of {@link ModelValueSynth#getComponents()}
	// *
	// * @return The value container that are components of the test
	// */
	// List<InterpretedValueSynth<?, ?>> getComponents();
	// }

	/** The name of the expresso config toolkit */
	public static final String NAME = "Expresso-Config";

	/** The version of this implementation of the expresso config toolkit */
	public static final Version VERSION = new Version(0, 1, 0);

	/** {@link #NAME} and {@link #VERSION} combined */
	public static final String CONFIG = "Expresso-Config v0.1";

	/** The name of the model value to store the {@link ObservableConfig} in the model */
	public static final String CONFIG_NAME = "$CONFIG$";

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
		// Not needed
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
		interpreter.createWith("map", ConfigModelValue.Def.class, ExElement.creator(ConfigMap::new));
		// interpreter.createWith("list", ConfigModelValue.class, collectionCreator());
		// interpreter.createWith("sorted-list", ConfigModelValue.class, sortedCollectionCreator());
		// interpreter.createWith("set", ConfigModelValue.class, setCreator());
		// interpreter.createWith("sorted-set", ConfigModelValue.class, sortedSetCreator());
		interpreter.createWith("map", ConfigModelValue.Def.class, ExElement.creator(ConfigMap::new));
		// interpreter.createWith("sorted-map", ConfigModelValue.class, sortedMapCreator());
		// interpreter.createWith("multi-map", ConfigModelValue.class, multiMapCreator());
		// interpreter.createWith("sorted-multi-map", ConfigModelValue.class, sortedMultiMapCreator());
	}

	@ExElementTraceable(toolkit = CONFIG, qonfigType = "config-value", interpretation = ConfigValue.Interpreted.class)
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
			super.doUpdate(session.asElement("config-model-value"));
			theDefaultValue = session.asElement("config-value").getAttributeExpression("default");
		}

		@Override
		public Interpreted<?> interpret() {
			return new Interpreted<>(this);
		}

		static class Interpreted<T> extends ConfigModelValue.Interpreted.Abstract<T, SettableValue<?>, SettableValue<T>> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<T>> theDefaultValue;

			Interpreted(ConfigValue definition) {
				super(definition);
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
				theDefaultValue = getDefinition().getDefaultValue() == null ? null
					: getDefinition().getDefaultValue().interpret(ModelTypes.Value.forType(getValueType()), env);
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return getComponents(theDefaultValue);
			}

			@Override
			public ModelValueInstantiator<SettableValue<T>> instantiate() {
				return new Instantiator<>(this);
			}
		}

		static class Instantiator<T> extends ConfigModelValue.Instantiator<T, SettableValue<T>> {
			private final ModelValueInstantiator<SettableValue<T>> theDefaultValue;

			Instantiator(Interpreted<T> interpreted) {
				super(interpreted.getConfigValue().instantiate(), interpreted.getValueType(), interpreted.getConfigPath(),
					interpreted.getFormat() == null ? null : interpreted.getFormat().instantiate(), interpreted.getFormatSet());
				theDefaultValue = interpreted.getDefaultValue() == null ? null : interpreted.getDefaultValue().instantiate();
			}

			@Override
			public void instantiate() {
				super.instantiate();
				if (theDefaultValue != null)
					theDefaultValue.instantiate();
			}

			@Override
			public SettableValue<T> create(ObservableConfigValueBuilder<T> config, ModelSetInstance msi)
				throws ModelInstantiationException {
				SettableValue<T> value = config.buildValue(null);
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
		public Interpreted<?> interpret() {
			return new Interpreted<>(this);
		}

		static class Interpreted<T> extends ConfigModelValue.Interpreted.Abstract<T, ObservableValueSet<?>, ObservableValueSet<T>> {
			public Interpreted(ConfigValueSet definition) {
				super(definition);
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
			public ModelValueInstantiator<ObservableValueSet<T>> instantiate() {
				return new Instantiator<>(this);
			}
		}

		static class Instantiator<T> extends ConfigModelValue.Instantiator<T, ObservableValueSet<T>> {
			public Instantiator(Interpreted<T> interpreted) {
				super(interpreted.getConfigValue().instantiate(), interpreted.getValueType(), interpreted.getConfigPath(),
					interpreted.getFormat() == null ? null : interpreted.getFormat().instantiate(), interpreted.getFormatSet());
			}

			@Override
			public ObservableValueSet<T> create(ObservableConfigValueBuilder<T> config, ModelSetInstance msi)
				throws ModelInstantiationException {
				return config.buildEntitySet(null);
			}
		}
	}

	static abstract class ConfigMapValue<M> extends ConfigModelValue.Def.Abstract<M> {
		private VariableType theKeyType;

		protected ConfigMapValue(Def<?> parent, QonfigElementOrAddOn qonfigType, ModelType.DoubleTyped<M> modelType) {
			super(parent, qonfigType, modelType);
		}

		public VariableType getKeyType() {
			return theKeyType;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);

			theKeyType = getAddOn(ExMapModelValue.Def.class).getKeyType();
		}

		public static abstract class Interpreted<K, V, M, MV extends M> extends ConfigModelValue.Interpreted.Abstract<V, M, MV> {
			private TypeToken<K> theKeyType;

			protected Interpreted(ConfigMapValue<M> definition) {
				super(definition);
			}

			@Override
			public ConfigMapValue<M> getDefinition() {
				return (ConfigMapValue<M>) super.getDefinition();
			}

			public TypeToken<K> getKeyType() {
				return theKeyType;
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);

				theKeyType = (TypeToken<K>) getDefinition().getKeyType().getType(env);
			}
		}
	}

	static class ConfigMap extends ConfigMapValue<ObservableMap<?, ?>> {
		public ConfigMap(Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, ModelTypes.Map);
		}

		@Override
		public Interpreted<?, ?> interpret() {
			return new Interpreted<>(this);
		}

		static class Interpreted<K, V> extends ConfigMapValue.Interpreted<K, V, ObservableMap<?, ?>, ObservableMap<K, V>> {
			public Interpreted(ConfigMap definition) {
				super(definition);
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
			public ModelValueInstantiator<ObservableMap<K, V>> instantiate() {
				return new Instantiator<>(this);
			}
		}

		static class Instantiator<K, V> extends ConfigModelValue.Instantiator<V, ObservableMap<K, V>> {
			private TypeToken<K> theKeyType;

			public Instantiator(Interpreted<K, V> interpreted) {
				super(interpreted.getConfigValue().instantiate(), interpreted.getValueType(), interpreted.getConfigPath(),
					interpreted.getFormat() == null ? null : interpreted.getFormat().instantiate(), interpreted.getFormatSet());
				theKeyType = interpreted.getKeyType();
			}

			@Override
			public ObservableMap<K, V> create(ObservableConfigValueBuilder<V> config, ModelSetInstance msi)
				throws ModelInstantiationException {
				return config.asMap(theKeyType).buildMap(null);
			}
		}
	}

	private void configureFormats(QonfigInterpreterCore.Builder interpreter) {
		interpreter.createWith(FileSourceFromModel.FILE_SOURCE_FROM_MODEL, ModelValueElement.CompiledSynth.class,
			ExElement.creator(FileSourceFromModel::new));
		interpreter.createWith(ExArchiveEnabledFileSource.ARCHIVE_ENABLED_FILE_SOURCE, ModelValueElement.CompiledSynth.class,
			ExElement.creator(ExArchiveEnabledFileSource::new));
		interpreter.createWith(ZipCompression.ZIP_ARCHIVAL, ModelValueElement.CompiledSynth.class, ExElement.creator(ZipCompression::new));
		interpreter.createWith(GZCompression.GZ_ARCHIVAL, ModelValueElement.CompiledSynth.class, ExElement.creator(GZCompression::new));
		interpreter.createWith(TarArchival.TAR_ARCHIVAL, ModelValueElement.CompiledSynth.class, ExElement.creator(TarArchival::new));
		interpreter.createWith(DoubleFormat.DOUBLE_FORMAT, ModelValueElement.CompiledSynth.class, ExElement.creator(DoubleFormat::new));
		interpreter.createWith(FileFormat.FILE_FORMAT, ModelValueElement.CompiledSynth.class, ExElement.creator(FileFormat::new));
		interpreter.createWith(DateFormat.INSTANT_FORMAT, ModelValueElement.CompiledSynth.class, ExElement.creator(DateFormat::new));
		interpreter.createWith(RegexStringFormat.REGEX_FORMAT_STRING, ModelValueElement.CompiledSynth.class,
			ExElement.creator(RegexStringFormat::new));
		interpreter.createWith(TextConfigFormat.TEXT_CONFIG_FORMAT, ModelValueElement.CompiledSynth.class,
			ExElement.creator(TextConfigFormat::new));
		// interpreter.createWith("model-reference", CompiledModelValue.class, session -> {
		// ExpressoQIS exS = wrap(session);
		// CompiledExpression ref = exS.getAttributeExpression("ref");
		// return CompiledModelValue.of("model-reference", ModelTypes.Value, () -> ref.evaluate(ModelTypes.Value.any()));
		// });
		// // File sources
		// interpreter.createWith("native-file-source", CompiledModelValue.class, session -> CompiledModelValue
		// .literal(TypeTokens.get().of(NativeFileSource.class), new NativeFileSource(), "native-file-source"));
		// interpreter.createWith("sftp-file-source", CompiledModelValue.class, session -> createSftpFileSource(wrap(session)));
		// interpreter.createWith("archive-enabled-file-source", CompiledModelValue.class,
		// session -> createArchiveEnabledFileSource(wrap(session)));
		// interpreter.createWith("zip-archival", CompiledModelValue.class,
		// session -> CompiledModelValue.literal(TypeTokens.get().of(ArchiveEnabledFileSource.ZipCompression.class),
		// new ArchiveEnabledFileSource.ZipCompression(), "zip-archival"));
		// interpreter.createWith("tar-archival", CompiledModelValue.class,
		// session -> CompiledModelValue.literal(TypeTokens.get().of(ArchiveEnabledFileSource.TarArchival.class),
		// new ArchiveEnabledFileSource.TarArchival(), "tar-archival"));
		// interpreter.createWith("gz-archival", CompiledModelValue.class,
		// session -> CompiledModelValue.literal(TypeTokens.get().of(ArchiveEnabledFileSource.GZipCompression.class),
		// new ArchiveEnabledFileSource.GZipCompression(), "gz-archival"));
		//
		// // Text formats
		// interpreter.createWith("text-format", CompiledModelValue.class,
		// session -> CompiledModelValue.literal(
		// TypeTokens.get().keyFor(SpinnerFormat.class).<SpinnerFormat<String>> parameterized(String.class),
		// SpinnerFormat.NUMERICAL_TEXT, "text-format"));
		// interpreter.createWith("int-format", CompiledModelValue.class, session -> {
		// SpinnerFormat.IntFormat format = SpinnerFormat.INT;
		// String sep = session.getAttributeText("grouping-separator");
		// if (sep != null) {
		// if (sep.length() != 0)
		// session.reporting().warn("WARNING: grouping-separator must be a single character");
		// else
		// format = format.withGroupingSeparator(sep.charAt(0));
		// }
		// return CompiledModelValue.constant(ModelValueSynth
		// .literal(TypeTokens.get().keyFor(Format.class).<Format<Integer>> parameterized(Integer.class), format, "int-format"));
		// });
		// interpreter.createWith("long-format", CompiledModelValue.class, session -> {
		// session.put(ExpressoBaseV0_1.VALUE_TYPE_KEY, TypeTokens.get().LONG);
		// SpinnerFormat.LongFormat format = SpinnerFormat.LONG;
		// String sep = session.getAttributeText("grouping-separator");
		// if (sep != null) {
		// if (sep.length() != 0)
		// session.reporting().warn("WARNING: grouping-separator must be a single character");
		// else
		// format = format.withGroupingSeparator(sep.charAt(0));
		// }
		// return CompiledModelValue.constant(ModelValueSynth
		// .literal(TypeTokens.get().keyFor(Format.class).<Format<Long>> parameterized(Long.class), format, "long-format"));
		// });
		// interpreter.createWith("double-format", CompiledModelValue.class, session -> createDoubleFormat(wrap(session)));
		// interpreter.createWith("instant-format", CompiledModelValue.class, session -> createInstantFormat(wrap(session)));
		// interpreter.createWith("file-format", CompiledModelValue.class, session -> createFileFormat(wrap(session)));
		// interpreter.createWith("regex-format", CompiledModelValue.class, session -> CompiledModelValue
		// .literal(TypeTokens.get().keyFor(Format.class).<Format<Pattern>> parameterized(Pattern.class), Format.PATTERN, "regex-format"));
		// interpreter.createWith("regex-format-string", CompiledModelValue.class,
		// session -> CompiledModelValue.literal(TypeTokens.get().keyFor(Format.class).<Format<String>> parameterized(String.class), //
		// Format.validate(Format.TEXT, str -> {
		// if (str == null || str.isEmpty())
		// return null; // That's fine
		// try {
		// Pattern.compile(str);
		// return null;
		// } catch (PatternSyntaxException e) {
		// return e.getMessage();
		// }
		// }), "regex-format-string"));
		//
		// interpreter.modifyWith("format",
		// (Class<CompiledModelValue<SettableValue<?>, SettableValue<Format<Object>>>>) (Class<?>) CompiledModelValue.class,
		// this::configureFormatValidation);
		// interpreter.createWith("regex-validation", ValidationProducer.class, session -> createRegexValidation(wrap(session)));
		// interpreter.createWith("filter-validation", ValidationProducer.class,
		// session -> createFilterValidation(session.as(ExpressoQIS.class)));
		//
		// // ObservableConfigFormats
		// interpreter.createWith("text-config-format", CompiledModelValue.class, session -> createTextConfigFormat(wrap(session)));
	}

	public interface FormatValidation<T> extends ExElement, Predicate<T> {
		public interface Def<V extends FormatValidation<?>> extends ExElement.Def<V> {
			Interpreted<?, ? extends V> interpret(ExElement.Interpreted<?> parent);
		}

		public interface Interpreted<T, V extends FormatValidation<T>> extends ExElement.Interpreted<V> {
			FormatValidation<T> create();
		}
	}

	// static class AbstractFormatProducer<T>
	// extends ModelValueElement.Def.SingleTyped<SettableValue<?>, ModelValueElement<SettableValue<?>, SettableValue<Format<T>>>>
	// implements ModelValueElement.CompiledSynth<SettableValue<?>, ModelValueElement<SettableValue<?>, SettableValue<Format<T>>>> {
	// private final List<FormatValidation.Def<?>> theValidation;
	//
	// protected AbstractFormatProducer(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
	// super(parent, qonfigType, ModelTypes.Value);
	// theValidation = new ArrayList<>();
	// }
	//
	// public List<FormatValidation.Def<?>> getValidation() {
	// return Collections.unmodifiableList(theValidation);
	// }
	// }

	@ExElementTraceable(toolkit = CONFIG, qonfigType = "model-reference", interpretation = ModelReference.Interpreted.class)
	public static abstract class ModelReference<T>
	extends ModelValueElement.Def.SingleTyped<SettableValue<?>, ModelValueElement<SettableValue<?>, SettableValue<T>>>
	implements ModelValueElement.CompiledSynth<SettableValue<?>, ModelValueElement<SettableValue<?>, SettableValue<T>>> {
		private CompiledExpression theReference;

		protected ModelReference(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, ModelTypes.Value);
		}

		@QonfigAttributeGetter("ref")
		public CompiledExpression getReference() {
			return theReference;
		}

		@Override
		protected void doPrepare(ExpressoQIS session) throws QonfigInterpretationException {
			theReference = session.getAttributeExpression("ref");
		}

		public static abstract class Interpreted<T> extends
		ModelValueElement.Def.SingleTyped.Interpreted<SettableValue<?>, SettableValue<T>, ModelValueElement<SettableValue<?>, SettableValue<T>>>
		implements
		ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<T>, ModelValueElement<SettableValue<?>, SettableValue<T>>> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<T>> theReference;

			protected Interpreted(ModelReference<T> definition) {
				super(definition, null);
			}

			@Override
			public ModelReference<T> getDefinition() {
				return (ModelReference<T>) super.getDefinition();
			}

			@Override
			public Interpreted<T> setParentElement(ExElement.Interpreted<?> parent) {
				super.setParentElement(parent);
				return this;
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getReference() {
				return theReference;
			}

			protected abstract TypeToken<T> getValueType();

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);
				theReference = getDefinition().getReference().interpret(ModelTypes.Value.forType(getValueType()), env);
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return Arrays.asList(theReference);
			}

			@Override
			public ModelValueInstantiator<SettableValue<T>> instantiate() {
				return theReference.instantiate();
			}
		}
	}

	static class FileSourceFromModel extends ModelReference<FileDataSource> {
		public static final String FILE_SOURCE_FROM_MODEL = "file-source-from-model";

		public FileSourceFromModel(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@Override
		public Interpreted interpret() {
			return new Interpreted(this);
		}

		static class Interpreted extends ModelReference.Interpreted<FileDataSource> {
			public Interpreted(FileSourceFromModel definition) {
				super(definition);
			}

			@Override
			protected TypeToken<FileDataSource> getValueType() {
				return TypeTokens.get().of(FileDataSource.class);
			}
		}
	}

	@ExElementTraceable(toolkit = CONFIG,
		qonfigType = ExArchiveEnabledFileSource.ARCHIVE_ENABLED_FILE_SOURCE,
		interpretation = ExArchiveEnabledFileSource.Interpreted.class)
	static class ExArchiveEnabledFileSource extends
	ModelValueElement.Def.SingleTyped<SettableValue<?>, ModelValueElement<SettableValue<?>, SettableValue<ArchiveEnabledFileSource>>>
	implements
	ModelValueElement.CompiledSynth<SettableValue<?>, ModelValueElement<SettableValue<?>, SettableValue<ArchiveEnabledFileSource>>> {
		public static final String ARCHIVE_ENABLED_FILE_SOURCE = "archive-enabled-file-source";

		private ModelValueElement.CompiledSynth<SettableValue<?>, ModelValueElement<SettableValue<?>, SettableValue<FileDataSource>>> theWrapped;
		private CompiledExpression theMaxArchiveDepth;
		private final List<ModelValueElement.CompiledSynth<SettableValue<?>, ModelValueElement<SettableValue<?>, SettableValue<ArchiveEnabledFileSource.FileArchival>>>> theArchiveMethods;

		public ExArchiveEnabledFileSource(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, ModelTypes.Value);
			theArchiveMethods = new ArrayList<>();
		}

		@QonfigChildGetter("wrapped")
		public ModelValueElement.CompiledSynth<SettableValue<?>, ModelValueElement<SettableValue<?>, SettableValue<FileDataSource>>> getWrapped() {
			return theWrapped;
		}

		@QonfigAttributeGetter("max-archive-depth")
		public CompiledExpression getMaxArchiveDepth() {
			return theMaxArchiveDepth;
		}

		@QonfigChildGetter("archive-method")
		public List<ModelValueElement.CompiledSynth<SettableValue<?>, ModelValueElement<SettableValue<?>, SettableValue<ArchiveEnabledFileSource.FileArchival>>>> getArchiveMethods() {
			return Collections.unmodifiableList(theArchiveMethods);
		}

		@Override
		protected void doPrepare(ExpressoQIS session) throws QonfigInterpretationException {
			theWrapped = ExElement.useOrReplace(ModelValueElement.CompiledSynth.class, theWrapped, session, "wrapped");
			theMaxArchiveDepth = session.getAttributeExpression("max-archive-depth");
			ExElement.syncDefs(ModelValueElement.CompiledSynth.class, theArchiveMethods, session.forChildren("archive-method"));
		}

		@Override
		public InterpretedSynth<SettableValue<?>, ?, ? extends ModelValueElement<SettableValue<?>, SettableValue<ArchiveEnabledFileSource>>> interpret() {
			return new Interpreted(this);
		}

		static class Interpreted extends
		ModelValueElement.Def.SingleTyped.Interpreted<SettableValue<?>, SettableValue<ArchiveEnabledFileSource>, ModelValueElement<SettableValue<?>, SettableValue<ArchiveEnabledFileSource>>>
		implements
		ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<ArchiveEnabledFileSource>, ModelValueElement<SettableValue<?>, SettableValue<ArchiveEnabledFileSource>>> {
			private ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<FileDataSource>, ?> theWrapped;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> theMaxArchiveDepth;
			private final List<ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<ArchiveEnabledFileSource.FileArchival>, ?>> theArchiveMethods;

			Interpreted(ExArchiveEnabledFileSource definition) {
				super(definition, null);
				theArchiveMethods = new ArrayList<>();
			}

			@Override
			public ExArchiveEnabledFileSource getDefinition() {
				return (ExArchiveEnabledFileSource) super.getDefinition();
			}

			@Override
			public Interpreted setParentElement(ExElement.Interpreted<?> parent) {
				super.setParentElement(parent);
				return this;
			}

			public ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<FileDataSource>, ?> getWrapped() {
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
				if (theWrapped != null
					&& (getDefinition().getWrapped() == null || theWrapped.getIdentity() != getDefinition().getWrapped().getIdentity())) {
					theWrapped.destroy();
					theWrapped = null;
				}
				if (theWrapped == null && getDefinition().getWrapped() != null)
					theWrapped = (ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<FileDataSource>, ?>) getDefinition()
					.getWrapped().interpret(env);
				if (theWrapped != null)
					theWrapped.updateValue(env);
				theMaxArchiveDepth = getDefinition().getMaxArchiveDepth().interpret(ModelTypes.Value.INT, env);
				CollectionUtils
				.synchronize(theArchiveMethods, getDefinition().getArchiveMethods(),
					(interp, def) -> interp.getIdentity() == def.getIdentity())//
				.<ExpressoInterpretationException> simpleE(
					def -> (ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<ArchiveEnabledFileSource.FileArchival>, ?>) def
					.interpret(env))//
				.onLeftX(el -> el.getLeftValue().destroy())//
				.onRightX(el -> el.getLeftValue().updateValue(env))//
				.onCommonX(el -> el.getLeftValue().updateValue(env))//
				.rightOrder()//
				.adjust();
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
			public ModelValueInstantiator<SettableValue<ArchiveEnabledFileSource>> instantiate() {
				return new Instantiator(this);
			}
		}

		static class Instantiator implements ModelValueInstantiator<SettableValue<ArchiveEnabledFileSource>> {
			private final ModelValueInstantiator<SettableValue<FileDataSource>> theWrapped;
			private final ModelValueInstantiator<SettableValue<Integer>> theMaxArchiveDepth;
			private final List<ModelValueInstantiator<SettableValue<ArchiveEnabledFileSource.FileArchival>>> theArchiveMethods;

			Instantiator(Interpreted interpreted) {
				theWrapped = interpreted.getWrapped() == null ? null : interpreted.getWrapped().instantiate();
				theMaxArchiveDepth = interpreted.getMaxArchiveDepth().instantiate();
				theArchiveMethods = QommonsUtils.filterMap(interpreted.getArchiveMethods(), null, am -> am.instantiate());
			}

			@Override
			public void instantiate() {
				if (theWrapped != null)
					theWrapped.instantiate();
				theMaxArchiveDepth.instantiate();
				for (ModelValueInstantiator<?> am : theArchiveMethods)
					am.instantiate();
			}

			@Override
			public SettableValue<ArchiveEnabledFileSource> get(ModelSetInstance models)
				throws ModelInstantiationException, IllegalStateException {
				SettableValue<FileDataSource> wrapped = theWrapped == null
					? SettableValue.of(FileDataSource.class, new NativeFileSource(), "Unmodifiable") : theWrapped.get(models);
				SettableValue<Integer> maxArchiveDepth = theMaxArchiveDepth.get(models);
				List<ArchiveEnabledFileSource.FileArchival> archiveMethods = new ArrayList<>(theArchiveMethods.size());
				for (ModelValueInstantiator<SettableValue<ArchiveEnabledFileSource.FileArchival>> am : theArchiveMethods)
					archiveMethods.add(am.get(models).get());
				return SettableValue.asSettable(wrapped.transform(ArchiveEnabledFileSource.class, tx -> tx.map(w -> {
					ArchiveEnabledFileSource aefs = new ArchiveEnabledFileSource(w)//
						.withArchival(archiveMethods);
					maxArchiveDepth.changes().takeUntil(wrapped.noInitChanges()).act(evt -> aefs.setMaxArchiveDepth(evt.getNewValue()));
					return aefs;
				})), __ -> "Unmodifiable");
			}

			@Override
			public SettableValue<ArchiveEnabledFileSource> forModelCopy(SettableValue<ArchiveEnabledFileSource> value,
				ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				SettableValue<FileDataSource> srcWrapped = theWrapped == null
					? SettableValue.of(FileDataSource.class, new NativeFileSource(), "Unmodifiable") : theWrapped.get(sourceModels);
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
				return SettableValue.asSettable(newWrapped.transform(ArchiveEnabledFileSource.class, tx -> tx.map(w -> {
					ArchiveEnabledFileSource aefs = new ArchiveEnabledFileSource(w)//
						.withArchival(archiveMethods);
					newMAD.changes().takeUntil(newWrapped.noInitChanges()).act(evt -> aefs.setMaxArchiveDepth(evt.getNewValue()));
					return aefs;
				})), __ -> "Unmodifiable");
			}
		}
	}

	static class ZipCompression extends
	ModelValueElement.Def.SingleTyped<SettableValue<?>, ModelValueElement<SettableValue<?>, SettableValue<ArchiveEnabledFileSource.ZipCompression>>>
	implements
	ModelValueElement.CompiledSynth<SettableValue<?>, ModelValueElement<SettableValue<?>, SettableValue<ArchiveEnabledFileSource.ZipCompression>>> {
		public static final String ZIP_ARCHIVAL = "zip-archival";

		public ZipCompression(Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, ModelTypes.Value);
		}

		@Override
		protected void doPrepare(ExpressoQIS session) throws QonfigInterpretationException {
		}

		@Override
		public Interpreted interpret() {
			return new Interpreted(this);
		}

		static class Interpreted extends
		ModelValueElement.Def.SingleTyped.Interpreted<SettableValue<?>, SettableValue<ArchiveEnabledFileSource.ZipCompression>, ModelValueElement<SettableValue<?>, SettableValue<ArchiveEnabledFileSource.ZipCompression>>>
		implements
		ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<ArchiveEnabledFileSource.ZipCompression>, ModelValueElement<SettableValue<?>, SettableValue<ArchiveEnabledFileSource.ZipCompression>>> {

			Interpreted(ZipCompression definition) {
				super(definition, null);
			}

			@Override
			public Interpreted setParentElement(ExElement.Interpreted<?> parent) {
				super.setParentElement(parent);
				return this;
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return Collections.emptyList();
			}

			@Override
			public ModelValueInstantiator<SettableValue<ArchiveEnabledFileSource.ZipCompression>> instantiate() {
				return new Instantiator();
			}
		}

		static class Instantiator implements ModelValueInstantiator<SettableValue<ArchiveEnabledFileSource.ZipCompression>> {
			@Override
			public void instantiate() {

			}

			@Override
			public SettableValue<ArchiveEnabledFileSource.ZipCompression> get(ModelSetInstance models)
				throws ModelInstantiationException, IllegalStateException {
				return SettableValue.of(ArchiveEnabledFileSource.ZipCompression.class, new ArchiveEnabledFileSource.ZipCompression(),
					"Unmodifiable");
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
	ModelValueElement.Def.SingleTyped<SettableValue<?>, ModelValueElement<SettableValue<?>, SettableValue<ArchiveEnabledFileSource.GZipCompression>>>
	implements
	ModelValueElement.CompiledSynth<SettableValue<?>, ModelValueElement<SettableValue<?>, SettableValue<ArchiveEnabledFileSource.GZipCompression>>> {
		public static final String GZ_ARCHIVAL = "gz-archival";

		public GZCompression(Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, ModelTypes.Value);
		}

		@Override
		protected void doPrepare(ExpressoQIS session) throws QonfigInterpretationException {
		}

		@Override
		public Interpreted interpret() {
			return new Interpreted(this);
		}

		static class Interpreted extends
		ModelValueElement.Def.SingleTyped.Interpreted<SettableValue<?>, SettableValue<ArchiveEnabledFileSource.GZipCompression>, ModelValueElement<SettableValue<?>, SettableValue<ArchiveEnabledFileSource.GZipCompression>>>
		implements
		ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<ArchiveEnabledFileSource.GZipCompression>, ModelValueElement<SettableValue<?>, SettableValue<ArchiveEnabledFileSource.GZipCompression>>> {

			Interpreted(GZCompression definition) {
				super(definition, null);
			}

			@Override
			public Interpreted setParentElement(ExElement.Interpreted<?> parent) {
				super.setParentElement(parent);
				return this;
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return Collections.emptyList();
			}

			@Override
			public ModelValueInstantiator<SettableValue<ArchiveEnabledFileSource.GZipCompression>> instantiate() {
				return new Instantiator();
			}
		}

		static class Instantiator implements ModelValueInstantiator<SettableValue<ArchiveEnabledFileSource.GZipCompression>> {
			@Override
			public void instantiate() {

			}

			@Override
			public SettableValue<ArchiveEnabledFileSource.GZipCompression> get(ModelSetInstance models)
				throws ModelInstantiationException, IllegalStateException {
				return SettableValue.of(ArchiveEnabledFileSource.GZipCompression.class, new ArchiveEnabledFileSource.GZipCompression(),
					"Unmodifiable");
			}

			@Override
			public SettableValue<ArchiveEnabledFileSource.GZipCompression> forModelCopy(
				SettableValue<ArchiveEnabledFileSource.GZipCompression> value, ModelSetInstance sourceModels, ModelSetInstance newModels)
					throws ModelInstantiationException {
				return value;
			}
		}
	}

	static class TarArchival extends
	ModelValueElement.Def.SingleTyped<SettableValue<?>, ModelValueElement<SettableValue<?>, SettableValue<ArchiveEnabledFileSource.TarArchival>>>
	implements
	ModelValueElement.CompiledSynth<SettableValue<?>, ModelValueElement<SettableValue<?>, SettableValue<ArchiveEnabledFileSource.TarArchival>>> {
		public static final String TAR_ARCHIVAL = "tar-archival";

		public TarArchival(Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, ModelTypes.Value);
		}

		@Override
		protected void doPrepare(ExpressoQIS session) throws QonfigInterpretationException {
		}

		@Override
		public Interpreted interpret() {
			return new Interpreted(this);
		}

		static class Interpreted extends
		ModelValueElement.Def.SingleTyped.Interpreted<SettableValue<?>, SettableValue<ArchiveEnabledFileSource.TarArchival>, ModelValueElement<SettableValue<?>, SettableValue<ArchiveEnabledFileSource.TarArchival>>>
		implements
		ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<ArchiveEnabledFileSource.TarArchival>, ModelValueElement<SettableValue<?>, SettableValue<ArchiveEnabledFileSource.TarArchival>>> {

			Interpreted(TarArchival definition) {
				super(definition, null);
			}

			@Override
			public Interpreted setParentElement(ExElement.Interpreted<?> parent) {
				super.setParentElement(parent);
				return this;
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return Collections.emptyList();
			}

			@Override
			public ModelValueInstantiator<SettableValue<ArchiveEnabledFileSource.TarArchival>> instantiate() {
				return new Instantiator();
			}
		}

		static class Instantiator implements ModelValueInstantiator<SettableValue<ArchiveEnabledFileSource.TarArchival>> {
			@Override
			public void instantiate() {

			}

			@Override
			public SettableValue<ArchiveEnabledFileSource.TarArchival> get(ModelSetInstance models)
				throws ModelInstantiationException, IllegalStateException {
				return SettableValue.of(ArchiveEnabledFileSource.TarArchival.class, new ArchiveEnabledFileSource.TarArchival(),
					"Unmodifiable");
			}

			@Override
			public SettableValue<ArchiveEnabledFileSource.TarArchival> forModelCopy(
				SettableValue<ArchiveEnabledFileSource.TarArchival> value, ModelSetInstance sourceModels, ModelSetInstance newModels)
					throws ModelInstantiationException {
				return value;
			}
		}
	}

	@ExElementTraceable(toolkit = CONFIG, qonfigType = FileFormat.FILE_FORMAT, interpretation = FileFormat.Interpreted.class)
	static class FileFormat
	extends ModelValueElement.Def.SingleTyped<SettableValue<?>, ModelValueElement<SettableValue<?>, SettableValue<Format<BetterFile>>>>
	implements
	ModelValueElement.CompiledSynth<SettableValue<?>, ModelValueElement<SettableValue<?>, SettableValue<Format<BetterFile>>>> {
		public static final String FILE_FORMAT = "file-format";

		private ModelValueElement.CompiledSynth<SettableValue<?>, ModelValueElement<SettableValue<?>, SettableValue<FileDataSource>>> theFileSource;
		private CompiledExpression theWorkingDir;
		private boolean isAllowEmpty;

		public FileFormat(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, ModelTypes.Value);
		}

		@QonfigChildGetter("file-source")
		public CompiledSynth<SettableValue<?>, ModelValueElement<SettableValue<?>, SettableValue<FileDataSource>>> getFileSource() {
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
			theFileSource = ExElement.useOrReplace(ModelValueElement.CompiledSynth.class, theFileSource, session, "file-source");
			theWorkingDir = session.getAttributeExpression("working-dir");
		}

		@Override
		public Interpreted interpret() {
			return new Interpreted(this);
		}

		static class Interpreted extends
		ModelValueElement.Def.SingleTyped.Interpreted<SettableValue<?>, SettableValue<Format<BetterFile>>, ModelValueElement<SettableValue<?>, SettableValue<Format<BetterFile>>>>
		implements
		ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<Format<BetterFile>>, ModelValueElement<SettableValue<?>, SettableValue<Format<BetterFile>>>> {
			private ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<FileDataSource>, ?> theFileSource;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<BetterFile>> theWorkingDir;

			Interpreted(FileFormat definition) {
				super(definition, null);
			}

			@Override
			public FileFormat getDefinition() {
				return (FileFormat) super.getDefinition();
			}

			@Override
			public Interpreted setParentElement(ExElement.Interpreted<?> parent) {
				super.setParentElement(parent);
				return this;
			}

			public ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<FileDataSource>, ?> getFileSource() {
				return theFileSource;
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<BetterFile>> getWorkingDir() {
				return theWorkingDir;
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);
				if (theFileSource != null && (getDefinition().getFileSource() == null
					|| theFileSource.getIdentity() != getDefinition().getFileSource().getIdentity())) {
					theFileSource.destroy();
					theFileSource = null;
				}
				if (theFileSource == null && getDefinition().getFileSource() != null)
					theFileSource = (ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<FileDataSource>, ?>) getDefinition()
					.getFileSource().interpret(env);
				if (theFileSource != null)
					theFileSource.updateValue(env);
				theWorkingDir = getDefinition().getWorkingDir() == null ? null
					: getDefinition().getWorkingDir().interpret(ModelTypes.Value.forType(BetterFile.class), env);

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
			public ModelValueInstantiator<SettableValue<Format<BetterFile>>> instantiate() {
				return new Instantiator(this);
			}
		}

		static class Instantiator implements ModelValueInstantiator<SettableValue<Format<BetterFile>>> {
			private final ModelValueInstantiator<SettableValue<FileDataSource>> theFileSource;
			private final ModelValueInstantiator<SettableValue<BetterFile>> theWorkingDir;
			private final boolean isAllowEmpty;

			Instantiator(Interpreted interpreted) {
				theFileSource = interpreted.getFileSource() == null ? null : interpreted.getFileSource().instantiate();
				theWorkingDir = interpreted.getWorkingDir() == null ? null : interpreted.getWorkingDir().instantiate();
				isAllowEmpty = interpreted.getDefinition().isAllowEmpty();
			}

			@Override
			public void instantiate() {
				if (theFileSource != null)
					theFileSource.instantiate();
				if (theWorkingDir != null)
					theWorkingDir.instantiate();
			}

			@Override
			public SettableValue<Format<BetterFile>> get(ModelSetInstance models)
				throws ModelInstantiationException, IllegalStateException {
				SettableValue<FileDataSource> fileSource = theFileSource == null
					? SettableValue.of(FileDataSource.class, new NativeFileSource(), "Unmodifiable") : theFileSource.get(models);
				SettableValue<BetterFile> workingDir;
				if (theWorkingDir != null)
					workingDir = theWorkingDir.get(models);
				else
					workingDir = SettableValue.asSettable(
						fileSource.map(BetterFile.class, fs -> BetterFile.at(fs, System.getProperty("user.dir"))), __ -> "Unmodifiable");

				return SettableValue.asSettable(
					fileSource.transform(TypeTokens.get().keyFor(Format.class).<Format<BetterFile>> parameterized(BetterFile.class),
						tx -> tx.combineWith(workingDir)//
						.combine((fs, wd) -> new BetterFile.FileFormat(fs, wd, isAllowEmpty))),
					__ -> "Unmodifiable");
			}

			@Override
			public SettableValue<Format<BetterFile>> forModelCopy(SettableValue<Format<BetterFile>> value, ModelSetInstance sourceModels,
				ModelSetInstance newModels) throws ModelInstantiationException {
				SettableValue<FileDataSource> srcFS = theFileSource == null
					? SettableValue.of(FileDataSource.class, new NativeFileSource(), "Unmodifiable") : theFileSource.get(sourceModels);
				SettableValue<FileDataSource> newFS = theFileSource == null ? srcFS
					: theFileSource.forModelCopy(srcFS, sourceModels, newModels);
				SettableValue<BetterFile> srcWD, newWD;
				if (theWorkingDir != null) {
					srcWD = theWorkingDir.get(sourceModels);
					newWD = theWorkingDir.forModelCopy(srcWD, sourceModels, newModels);
				} else {
					srcWD = SettableValue.asSettable(srcFS.map(BetterFile.class, fs -> BetterFile.at(fs, System.getProperty("user.dir"))),
						__ -> "Unmodifiable");
					if (newFS == srcFS)
						newWD = srcWD;
					else
						newWD = SettableValue.asSettable(
							newFS.map(BetterFile.class, fs -> BetterFile.at(fs, System.getProperty("user.dir"))), __ -> "Unmodifiable");
				}
				if (srcFS == newFS && srcWD == newWD)
					return value;

				return SettableValue.asSettable(newFS.transform(
					TypeTokens.get().keyFor(Format.class).<Format<BetterFile>> parameterized(BetterFile.class), tx -> tx.combineWith(newWD)//
					.combine((fs, wd) -> new BetterFile.FileFormat(fs, wd, isAllowEmpty))),
					__ -> "Unmodifiable");
			}
		}
	}

	@ExElementTraceable(toolkit = CONFIG, qonfigType = DoubleFormat.DOUBLE_FORMAT, interpretation = DoubleFormat.Interpreted.class)
	static class DoubleFormat
	extends ModelValueElement.Def.SingleTyped<SettableValue<?>, ModelValueElement<SettableValue<?>, SettableValue<Format<Double>>>>
	implements ModelValueElement.CompiledSynth<SettableValue<?>, ModelValueElement<SettableValue<?>, SettableValue<Format<Double>>>> {
		public static final String DOUBLE_FORMAT = "double-format";

		private CompiledExpression theSignificantDigits;
		private String theUnit;
		private boolean isUnitRequired;
		private boolean isMetricPrefixed;
		private boolean isMetricPrefixedP2;
		private final List<Prefix> thePrefixes;
		private final Map<String, Double> thePrefixMults;

		public DoubleFormat(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, ModelTypes.Value);
			thePrefixes = new ArrayList<>();
			thePrefixMults = new LinkedHashMap<>();
		}

		@QonfigAttributeGetter("sig-digs")
		public CompiledExpression getSignificantDigits() {
			return theSignificantDigits;
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

		@QonfigChildGetter("prefix")
		public List<Prefix> getPrefixes() {
			return Collections.unmodifiableList(thePrefixes);
		}

		public Map<String, Double> getPrefixMults() {
			return Collections.unmodifiableMap(thePrefixMults);
		}

		@Override
		protected void doPrepare(ExpressoQIS session) throws QonfigInterpretationException {
			theSignificantDigits = session.getAttributeExpression("sig-digs");
			theUnit = session.getAttributeText("unit");
			isUnitRequired = session.getAttribute("unit-required", boolean.class);
			isMetricPrefixed = session.getAttribute("metric-prefixes", boolean.class);
			isMetricPrefixedP2 = session.getAttribute("metric-prefixes-p2", boolean.class);
			if (isMetricPrefixed && isMetricPrefixedP2)
				throw new QonfigInterpretationException("Only one of 'metrix-prefixes' and 'metric-prefixes-p2' may be specified",
					session.getAttributeValuePosition("metric-prefixes-p2", 0), 0);
			ExElement.syncDefs(Prefix.class, thePrefixes, session.forChildren("prefix"));
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
		public Interpreted interpret() {
			return new Interpreted(this);
		}

		static class Interpreted extends
		ModelValueElement.Def.SingleTyped.Interpreted<SettableValue<?>, SettableValue<Format<Double>>, ModelValueElement<SettableValue<?>, SettableValue<Format<Double>>>>
		implements
		ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<Format<Double>>, ModelValueElement<SettableValue<?>, SettableValue<Format<Double>>>> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> theSignificantDigits;

			Interpreted(DoubleFormat definition) {
				super(definition, null);
			}

			@Override
			public DoubleFormat getDefinition() {
				return (DoubleFormat) super.getDefinition();
			}

			@Override
			public Interpreted setParentElement(ExElement.Interpreted<?> parent) {
				super.setParentElement(parent);
				return this;
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> getSignificantDigits() {
				return theSignificantDigits;
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);
				theSignificantDigits = getDefinition().getSignificantDigits().interpret(ModelTypes.Value.INT, env);
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return Collections.emptyList();
			}

			@Override
			public ModelValueInstantiator<SettableValue<Format<Double>>> instantiate() {
				return new Instantiator(this);
			}
		}

		static class Instantiator implements ModelValueInstantiator<SettableValue<Format<Double>>> {
			private ModelValueInstantiator<SettableValue<Integer>> theSignificantDigits;
			private String theUnit;
			private boolean isUnitRequired;
			private boolean isMetricPrefixed;
			private boolean isMetricPrefixedP2;
			private Map<String, Double> thePrefixMults;

			public Instantiator(Interpreted interpreted) {
				theSignificantDigits = interpreted.getSignificantDigits().instantiate();
				theUnit = interpreted.getDefinition().getUnit();
				isUnitRequired = interpreted.getDefinition().isUnitRequired();
				isMetricPrefixed = interpreted.getDefinition().isMetricPrefixed();
				isMetricPrefixedP2 = interpreted.getDefinition().isMetricPrefixedP2();
				thePrefixMults = QommonsUtils.unmodifiableCopy(interpreted.getDefinition().getPrefixMults());
			}

			@Override
			public void instantiate() {
				theSignificantDigits.instantiate();
			}

			@Override
			public SettableValue<Format<Double>> get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
				SettableValue<Integer> sigDigs = theSignificantDigits.get(models);
				return createFormat(sigDigs);
			}

			private SettableValue<Format<Double>> createFormat(SettableValue<Integer> sigDigs) {
				return SettableValue
					.asSettable(sigDigs.map(TypeTokens.get().keyFor(Format.class).<Format<Double>> parameterized(double.class), sd -> {
						Format.SuperDoubleFormatBuilder builder = Format.doubleFormat(sd);
						builder.withUnit(theUnit, isUnitRequired);
						if (isMetricPrefixed)
							builder.withMetricPrefixes();
						else if (isMetricPrefixedP2)
							builder.withMetricPrefixesPower2();
						for (Map.Entry<String, Double> prefix : thePrefixMults.entrySet())
							builder.withPrefix(prefix.getKey(), prefix.getValue());
						return builder.build();
					}), __ -> "Unmodifiable");
			}

			@Override
			public SettableValue<Format<Double>> forModelCopy(SettableValue<Format<Double>> value, ModelSetInstance sourceModels,
				ModelSetInstance newModels) throws ModelInstantiationException {
				SettableValue<Integer> srcSigDigs = theSignificantDigits.get(sourceModels);
				SettableValue<Integer> newSigDigs = theSignificantDigits.forModelCopy(srcSigDigs, sourceModels, newModels);
				if (newSigDigs == srcSigDigs)
					return value;
				else
					return createFormat(newSigDigs);
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
				LocatedPositionedContent mult = session.getAttributeValuePosition("multiplier");
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

	@ExElementTraceable(toolkit = CONFIG, qonfigType = DateFormat.INSTANT_FORMAT, interpretation = DateFormat.Interpreted.class)
	static class DateFormat
	extends ModelValueElement.Def.SingleTyped<SettableValue<?>, ModelValueElement<SettableValue<?>, SettableValue<Format<Instant>>>>
	implements ModelValueElement.CompiledSynth<SettableValue<?>, ModelValueElement<SettableValue<?>, SettableValue<Format<Instant>>>> {
		public static final String INSTANT_FORMAT = "instant-format";

		private String theDayFormat;
		private TimeZone theTimeZone;
		private TimeUtils.DateElementType theMaxResolution;
		private boolean isFormat24H;
		private TimeUtils.RelativeInstantEvaluation theRelativeEvaluation;
		private CompiledExpression theRelativeTo;

		public DateFormat(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, ModelTypes.Value);
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

			theRelativeTo = session.getAttributeExpression("relative-to");
		}

		@Override
		protected void doPrepare(ExpressoQIS session) throws QonfigInterpretationException {
		}

		@Override
		public InterpretedSynth<SettableValue<?>, ?, ? extends ModelValueElement<SettableValue<?>, SettableValue<Format<Instant>>>> interpret() {
			return new Interpreted(this);
		}

		static class Interpreted extends
		ModelValueElement.Def.SingleTyped.Interpreted<SettableValue<?>, SettableValue<Format<Instant>>, ModelValueElement<SettableValue<?>, SettableValue<Format<Instant>>>>
		implements
		ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<Format<Instant>>, ModelValueElement<SettableValue<?>, SettableValue<Format<Instant>>>> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Instant>> theRelativeTo;

			Interpreted(DateFormat definition) {
				super(definition, null);
			}

			@Override
			public DateFormat getDefinition() {
				return (DateFormat) super.getDefinition();
			}

			@Override
			public Interpreted setParentElement(ExElement.Interpreted<?> parent) {
				super.setParentElement(parent);
				return this;
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<Instant>> getRelativeTo() {
				return theRelativeTo;
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);
				theRelativeTo = getDefinition().getRelativeTo() == null ? null
					: getDefinition().getRelativeTo().interpret(ModelTypes.Value.forType(Instant.class), env);
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return theRelativeTo == null ? Collections.emptyList() : Collections.singletonList(theRelativeTo);
			}

			@Override
			public ModelValueInstantiator<SettableValue<Format<Instant>>> instantiate() {
				return new Instantiator(this);
			}
		}

		static class Instantiator implements ModelValueInstantiator<SettableValue<Format<Instant>>> {
			private final String theDayFormat;
			private final TimeZone theTimeZone;
			private final TimeUtils.DateElementType theMaxResolution;
			private final boolean isFormat24H;
			private final TimeUtils.RelativeInstantEvaluation theRelativeEvaluation;
			private final ModelValueInstantiator<SettableValue<Instant>> theRelativeTo;

			Instantiator(Interpreted interpreted) {
				theDayFormat = interpreted.getDefinition().getDayFormat();
				theTimeZone = interpreted.getDefinition().getTimeZone();
				theMaxResolution = interpreted.getDefinition().getMaxResolution();
				isFormat24H = interpreted.getDefinition().isFormat24H();
				theRelativeEvaluation = interpreted.getDefinition().getRelativeEvaluation();
				theRelativeTo = interpreted.getRelativeTo() == null ? null : interpreted.getRelativeTo().instantiate();
			}

			@Override
			public void instantiate() {
				if (theRelativeTo != null)
					theRelativeTo.instantiate();
			}

			@Override
			public SettableValue<Format<Instant>> get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
				Supplier<Instant> relativeTo = theRelativeTo == null ? LambdaUtils.printableSupplier(Instant::now, () -> "now", null)
					: theRelativeTo.get(models);
				return SettableValue.of(TypeTokens.get().keyFor(Format.class).<Format<Instant>> parameterized(Instant.class), //
					SpinnerFormat.flexDate(relativeTo, theDayFormat, teo -> teo//
						.withTimeZone(theTimeZone)//
						.withMaxResolution(theMaxResolution)//
						.with24HourFormat(isFormat24H)//
						.withEvaluationType(theRelativeEvaluation)),
					"Unsettable");
			}

			@Override
			public SettableValue<Format<Instant>> forModelCopy(SettableValue<Format<Instant>> value, ModelSetInstance sourceModels,
				ModelSetInstance newModels) throws ModelInstantiationException {
				if (theRelativeTo == null)
					return value;
				SettableValue<Instant> sourceRT = theRelativeTo.get(sourceModels);
				SettableValue<Instant> newRT = theRelativeTo.forModelCopy(sourceRT, sourceModels, newModels);
				if (sourceRT == newRT)
					return value;
				return SettableValue.of(TypeTokens.get().keyFor(Format.class).<Format<Instant>> parameterized(Instant.class), //
					SpinnerFormat.flexDate(newRT, theDayFormat, teo -> teo//
						.withTimeZone(theTimeZone)//
						.withMaxResolution(theMaxResolution)//
						.with24HourFormat(isFormat24H)//
						.withEvaluationType(theRelativeEvaluation)),
					"Unsettable");
			}
		}
	}

	@ExElementTraceable(toolkit = CONFIG,
		qonfigType = RegexStringFormat.REGEX_FORMAT_STRING,
		interpretation = RegexStringFormat.Interpreted.class)
	static class RegexStringFormat
	extends ModelValueElement.Def.SingleTyped<SettableValue<?>, ModelValueElement<SettableValue<?>, SettableValue<Format<String>>>>
	implements ModelValueElement.CompiledSynth<SettableValue<?>, ModelValueElement<SettableValue<?>, SettableValue<Format<String>>>> {
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
			super(parent, qonfigType, ModelTypes.Value);
		}

		@Override
		protected void doPrepare(ExpressoQIS session) throws QonfigInterpretationException {
			// Nothing to do
		}

		@Override
		public Interpreted interpret() {
			return new Interpreted(this);
		}

		static class Interpreted extends
		ModelValueElement.Def.SingleTyped.Interpreted<SettableValue<?>, SettableValue<Format<String>>, ModelValueElement<SettableValue<?>, SettableValue<Format<String>>>>
		implements
		ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<Format<String>>, ModelValueElement<SettableValue<?>, SettableValue<Format<String>>>> {
			Interpreted(RegexStringFormat definition) {
				super(definition, null);
			}

			@Override
			public RegexStringFormat getDefinition() {
				return (RegexStringFormat) super.getDefinition();
			}

			@Override
			public Interpreted setParentElement(ExElement.Interpreted<?> parent) {
				super.setParentElement(parent);
				return this;
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return Collections.emptyList();
			}

			@Override
			public ModelValueInstantiator<SettableValue<Format<String>>> instantiate() {
				return new Instantiator();
			}
		}

		static class Instantiator implements ModelValueInstantiator<SettableValue<Format<String>>> {
			@Override
			public void instantiate() {
				// Nothing to do
			}

			@Override
			public SettableValue<Format<String>> get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
				return SettableValue.of(TypeTokens.get().keyFor(Format.class).<Format<String>> parameterized(String.class), INSTANCE,
					"Unmodifiable");
			}

			@Override
			public SettableValue<Format<String>> forModelCopy(SettableValue<Format<String>> value, ModelSetInstance sourceModels,
				ModelSetInstance newModels) throws ModelInstantiationException {
				return value;
			}
		}
	}

	@ExElementTraceable(toolkit = CONFIG,
		qonfigType = TextConfigFormat.TEXT_CONFIG_FORMAT,
		interpretation = TextConfigFormat.Interpreted.class)
	static class TextConfigFormat extends
	ModelValueElement.Def.SingleTyped<SettableValue<?>, ModelValueElement<SettableValue<?>, ? extends SettableValue<? extends ObservableConfigFormat<?>>>>
	implements
	ModelValueElement.CompiledSynth<SettableValue<?>, ModelValueElement<SettableValue<?>, ? extends SettableValue<? extends ObservableConfigFormat<?>>>> {
		public static final String TEXT_CONFIG_FORMAT = "text-config-format";

		private VariableType theType;
		private ModelValueElement.CompiledSynth<SettableValue<?>, ModelValueElement<SettableValue<?>, SettableValue<Format<?>>>> theTextFormat;
		private CompiledExpression theDefaultValue;
		private String theDefaultText;

		public TextConfigFormat(Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, ModelTypes.Value);
		}

		@QonfigAttributeGetter("type")
		public VariableType getConfiguredType() {
			return theType;
		}

		@QonfigChildGetter("text-format")
		public ModelValueElement.CompiledSynth<SettableValue<?>, ModelValueElement<SettableValue<?>, SettableValue<Format<?>>>> getTextFormat() {
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
			LocatedPositionedContent type = session.getAttributeValuePosition("type");
			theType = type == null ? null : VariableType.parseType(type);
			theTextFormat = ExElement.useOrReplace(ModelValueElement.CompiledSynth.class, theTextFormat, session, "text-format");
			if (theType == null && theTextFormat == null)
				throw new QonfigInterpretationException("Either 'type' or 'text-format' must be specified",
					session.getElement().getPositionInFile(), 0);
			else if (theType != null && theTextFormat != null)
				throw new QonfigInterpretationException("Only one of 'type' or 'text-format' may be specified",
					session.getElement().getPositionInFile(), 0);
			theDefaultValue = session.getAttributeExpression("default");
			theDefaultText = session.getAttributeText("default-text");
			if (theDefaultValue != null && theDefaultText != null)
				throw new QonfigInterpretationException("Only one of 'default' or 'default-text' may be specified",
					session.getElement().getPositionInFile(), 0);
		}

		@Override
		public Interpreted<?> interpret() {
			return new Interpreted<>(this);
		}

		static class Interpreted<T> extends
		ModelValueElement.Def.SingleTyped.Interpreted<SettableValue<?>, SettableValue<ObservableConfigFormat<T>>, ModelValueElement<SettableValue<?>, SettableValue<ObservableConfigFormat<T>>>>
		implements
		ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<ObservableConfigFormat<T>>, ModelValueElement<SettableValue<?>, SettableValue<ObservableConfigFormat<T>>>> {
			private TypeToken<T> theType;
			private ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<Format<T>>, ?> theTextFormat;
			private Format<T> theDefaultFormat;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<T>> theDefaultValue;

			Interpreted(TextConfigFormat definition) {
				super(definition, null);
			}

			@Override
			public TextConfigFormat getDefinition() {
				return (TextConfigFormat) super.getDefinition();
			}

			@Override
			public Interpreted<T> setParentElement(ExElement.Interpreted<?> parent) {
				super.setParentElement(parent);
				return this;
			}

			protected TypeToken<T> getValueType() {
				return theType != null ? theType
					: (TypeToken<T>) theTextFormat.getType().getType(0).resolveType(Format.class.getTypeParameters()[0]);
			}

			@Override
			protected ModelInstanceType<SettableValue<?>, SettableValue<ObservableConfigFormat<T>>> getTargetType() {
				return ModelTypes.Value.forType(
					TypeTokens.get().keyFor(ObservableConfigFormat.class).<ObservableConfigFormat<T>> parameterized(getValueType()));
			}

			public TypeToken<T> getConfiguredType() {
				return theType;
			}

			public ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<Format<T>>, ?> getTextFormat() {
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
				theType = getDefinition().getConfiguredType() == null ? null
					: (TypeToken<T>) getDefinition().getConfiguredType().getType(env);
				if (theTextFormat != null && (getDefinition().getTextFormat() == null
					|| theTextFormat.getIdentity() != getDefinition().getTextFormat().getIdentity())) {
					theTextFormat.destroy();
					theTextFormat = null;
				}
				if (theTextFormat == null && getDefinition().getTextFormat() != null)
					theTextFormat = (ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<Format<T>>, ?>) getDefinition()
					.getTextFormat().interpret(env);
				if (theTextFormat != null)
					theTextFormat.updateValue(env);

				if (theTextFormat == null) {
					try {
						ObservableConfigFormat<T> defaultFormat = new ObservableConfigFormatSet().getConfigFormat(theType, null);
						if (!(defaultFormat instanceof ObservableConfigFormat.Impl.SimpleConfigFormat))
							throw new IllegalArgumentException();
						theDefaultFormat = ((ObservableConfigFormat.Impl.SimpleConfigFormat<T>) defaultFormat).format;
					} catch (IllegalArgumentException e) {
						throw new ExpressoInterpretationException(
							"No default text format available for type " + theType + ". 'text-format' must be specified.",
							getDefinition().getConfiguredType().getContent().getPosition(0),
							getDefinition().getConfiguredType().getContent().length(), e);
					}
				}

				theDefaultValue = getDefinition().getDefaultValue() == null ? null
					: getDefinition().getDefaultValue().interpret(ModelTypes.Value.forType(getValueType()), env);
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
			public Instantiator<T> instantiate() {
				return new Instantiator<>(this);
			}
		}

		static class Instantiator<T> implements ModelValueInstantiator<SettableValue<ObservableConfigFormat<T>>> {
			private final TypeToken<T> theType;
			private final ModelValueInstantiator<SettableValue<Format<T>>> theTextFormat;
			private final Format<T> theDefaultFormat;
			private final ModelValueInstantiator<SettableValue<T>> theDefaultValue;
			private final String theDefaultText;
			private final ErrorReporting theReporting;

			Instantiator(Interpreted<T> interpreted) {
				theType = interpreted.getValueType();
				theTextFormat = interpreted.getTextFormat() == null ? null : interpreted.getTextFormat().instantiate();
				theDefaultFormat = interpreted.getDefaultFormat();
				theDefaultValue = interpreted.getDefaultValue() == null ? null : interpreted.getDefaultValue().instantiate();
				theDefaultText = interpreted.getDefinition().getDefaultText();
				theReporting = interpreted.reporting();
			}

			@Override
			public void instantiate() {
				if (theTextFormat != null)
					theTextFormat.instantiate();
				if (theDefaultValue != null)
					theDefaultValue.instantiate();
			}

			@Override
			public SettableValue<ObservableConfigFormat<T>> get(ModelSetInstance models)
				throws ModelInstantiationException, IllegalStateException {
				SettableValue<Format<T>> textFormat;
				if (theTextFormat != null)
					textFormat = theTextFormat.get(models);
				else
					textFormat = SettableValue.of(TypeTokens.get().of((Class<Format<T>>) (Class<?>) Format.class), theDefaultFormat,
						"Unmodifiable");
				ObservableValue<T> defaultValue;
				if (theDefaultValue != null)
					defaultValue = theDefaultValue.get(models);
				else if (theDefaultText != null) {
					defaultValue = textFormat.map(theType, tf -> {
						try {
							return tf.parse(theDefaultText);
						} catch (ParseException e) {
							theReporting.error("Could not parse default value", e);
							return TypeTokens.get().getDefaultValue(theType);
						}
					});
				} else
					defaultValue = ObservableValue.of(theType, TypeTokens.get().getDefaultValue(theType));
				return SettableValue.asSettable(
					textFormat.map(TypeTokens.get().of((Class<ObservableConfigFormat<T>>) (Class<?>) ObservableConfigFormat.class),
						tf -> ObservableConfigFormat.ofQommonFormat(tf, defaultValue)),
					__ -> "Unmodifiable");
			}

			@Override
			public SettableValue<ObservableConfigFormat<T>> forModelCopy(SettableValue<ObservableConfigFormat<T>> value,
				ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				SettableValue<Format<T>> srcTextFormat, newTextFormat;
				if (theTextFormat != null) {
					srcTextFormat = theTextFormat.get(sourceModels);
					newTextFormat = theTextFormat.forModelCopy(srcTextFormat, sourceModels, newModels);
				} else
					srcTextFormat = newTextFormat = SettableValue.of(TypeTokens.get().of((Class<Format<T>>) (Class<?>) Format.class),
						theDefaultFormat, "Unmodifiable");

				ObservableValue<T> srcDefaultValue, newDefaultValue;
				if (theDefaultValue != null) {
					srcDefaultValue = theDefaultValue.get(sourceModels);
					newDefaultValue = theDefaultValue.forModelCopy((SettableValue<T>) srcDefaultValue, sourceModels, newModels);
				} else if (theDefaultText != null) {
					srcDefaultValue = newDefaultValue = newTextFormat.map(theType, tf -> {
						try {
							return tf.parse(theDefaultText);
						} catch (ParseException e) {
							theReporting.error("Could not parse default value", e);
							return TypeTokens.get().getDefaultValue(theType);
						}
					});
				} else
					srcDefaultValue = newDefaultValue = ObservableValue.of(theType, TypeTokens.get().getDefaultValue(theType));
				if (srcTextFormat == newTextFormat && srcDefaultValue == newDefaultValue)
					return value;
				return SettableValue.asSettable(
					newTextFormat.map(TypeTokens.get().of((Class<ObservableConfigFormat<T>>) (Class<?>) ObservableConfigFormat.class),
						tf -> ObservableConfigFormat.ofQommonFormat(tf, newDefaultValue)),
					__ -> "Unmodifiable");
			}
		}
	}
}
