package org.observe.expresso.qonfig;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.observe.SettableValue;
import org.observe.config.ObservableConfig;
import org.observe.config.ObservableConfig.ObservableConfigValueBuilder;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.ElementTypeTraceability.SingleTypeTraceability;
import org.qommons.Version;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretation;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreterCore;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.SpecialSession;

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
		// interpreter.createWith("value-set", ConfigModelValue.class, valueSetCreator());
		// interpreter.createWith("list", ConfigModelValue.class, collectionCreator());
		// interpreter.createWith("sorted-list", ConfigModelValue.class, sortedCollectionCreator());
		// interpreter.createWith("set", ConfigModelValue.class, setCreator());
		// interpreter.createWith("sorted-set", ConfigModelValue.class, sortedSetCreator());
		// interpreter.createWith("map", ConfigModelValue.class, mapCreator());
		// interpreter.createWith("sorted-map", ConfigModelValue.class, sortedMapCreator());
		// interpreter.createWith("multi-map", ConfigModelValue.class, multiMapCreator());
		// interpreter.createWith("sorted-multi-map", ConfigModelValue.class, sortedMultiMapCreator());
	}

	static class ConfigValue extends ConfigModelValue.Def.Abstract<SettableValue<?>> {
		private static final SingleTypeTraceability<ConfigModelValue<?, ?, ?>, Interpreted<?>, ConfigValue> TRACEABILITY = ElementTypeTraceability
			.getElementTraceability(NAME, VERSION, "config-value", ConfigValue.class, Interpreted.class, null);

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
			withTraceability(TRACEABILITY.validate(session.asElement("config-value").getFocusType(), session.reporting()));
			super.doUpdate(session.asElement("config-model-value"));
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

	// abstract class ConfigValueCreator<T, M, MV extends M> implements QonfigValueCreator<ConfigModelValue<T, M, MV>> {
	// private final ModelType<M> theModelType;
	//
	// public ConfigValueCreator(ModelType<M> modelType) {
	// theModelType = modelType;
	// }
	//
	// @Override
	// public ConfigModelValue<T, M, MV> createValue(CoreSession session) throws QonfigInterpretationException {
	// ExpressoQIS exS = session.as(ExpressoQIS.class);
	// VariableType type = (VariableType) exS.get(VALUE_TYPE_KEY);
	// prepare(exS);
	// InterpretedExpressoEnv env = exS.getExpressoEnv();
	// return new ConfigModelValue<T, M, MV>() {
	// private ModelInstanceType<M, MV> theInstanceType;
	//
	// @Override
	// public void init() throws ExpressoInterpretationException {
	// TypeToken<T> valueType = (TypeToken<T>) type.getType(env.getModels());
	// theInstanceType = (ModelInstanceType<M, MV>) theModelType.forTypes(valueType);
	// prepare(env, theInstanceType);
	// }
	//
	// @Override
	// public ModelInstanceType<M, MV> getType() {
	// return theInstanceType;
	// }
	//
	// @Override
	// public MV create(ObservableConfigValueBuilder<T> config, ModelSetInstance msi) throws ModelInstantiationException {
	// return ConfigValueCreator.this.create(config, msi);
	// }
	// };
	// }
	//
	// protected abstract void prepare(ExpressoQIS exS) throws QonfigInterpretationException;
	//
	// protected abstract void prepare(InterpretedExpressoEnv env, ModelInstanceType<M, MV> instanceType)
	// throws ExpressoInterpretationException;
	//
	// protected abstract MV create(ObservableConfigValueBuilder<T> builder, ModelSetInstance msi) throws ModelInstantiationException;
	// }
	//
	// abstract class ConfigMapCreator<K, V, M, MV extends M> implements QonfigValueCreator<ConfigModelValue<V, M, MV>> {
	// private final ModelType<M> theModelType;
	// CompiledModelValue<?> keyFormatCreator;
	// InterpretedValueSynth<SettableValue<?>, SettableValue<ObservableConfigFormat<K>>> keyFormatContainer;
	// ObservableConfigFormat<K> defaultKeyFormat;
	//
	// public ConfigMapCreator(ModelType<M> modelType) {
	// theModelType = modelType;
	// }
	//
	// @Override
	// public ConfigModelValue<V, M, MV> createValue(CoreSession session) throws QonfigInterpretationException {
	// ExpressoQIS exS = session.as(ExpressoQIS.class);
	// ExpressoQIS formatSession = exS.forChildren("key-format").peekFirst();
	// keyFormatCreator = formatSession == null ? null : formatSession.interpret(CompiledModelValue.class);
	//
	// VariableType vblKeyType = (VariableType) exS.get(KEY_TYPE_KEY);
	// VariableType vblValueype = (VariableType) exS.get(VALUE_TYPE_KEY);
	// prepare(exS);
	// return new ConfigModelValue<V, M, MV>() {
	// private ModelInstanceType<M, MV> theInstanceType;
	//
	// @Override
	// public void init() throws ExpressoInterpretationException {
	// TypeToken<K> keyType = (TypeToken<K>) vblKeyType.getType(exS.getExpressoEnv().getModels());
	// TypeToken<V> valueType = (TypeToken<V>) vblValueype.getType(exS.getExpressoEnv().getModels());
	// theInstanceType = (ModelInstanceType<M, MV>) theModelType.forTypes(keyType, valueType);
	//
	// if (keyFormatCreator != null) {
	// try {
	// keyFormatContainer = keyFormatCreator.createSynthesizer().as(ModelTypes.Value.forType(
	// TypeTokens.get().keyFor(ObservableConfigFormat.class).<ObservableConfigFormat<K>> parameterized(keyType)));
	// } catch (TypeConversionException e) {
	// LocatedFilePosition position;
	// if (formatSession != null)
	// position = formatSession.getElement().getPositionInFile();
	// else
	// position = session.getElement().getPositionInFile();
	// throw new ExpressoInterpretationException("Could not evaluate " + keyFormatCreator + " as a config format",
	// position, 0, e);
	// }
	// defaultKeyFormat = null;
	// } else {
	// keyFormatContainer = null;
	// defaultKeyFormat = getDefaultConfigFormat(keyType);
	// if (defaultKeyFormat == null)
	// throw new ExpressoInterpretationException("No default config format available for key type " + keyType,
	// session.getElement().getPositionInFile(), 0);
	// }
	//
	// prepare(exS, theInstanceType);
	// }
	//
	// @Override
	// public ModelInstanceType<M, MV> getType() {
	// return theInstanceType;
	// }
	//
	// @Override
	// public MV create(ObservableConfigValueBuilder<V> config, ModelSetInstance msi) throws ModelInstantiationException {
	// ObservableConfigMapBuilder<K, V> mapBuilder = config.asMap((TypeToken<K>) theInstanceType.getType(0));
	// if (keyFormatContainer != null)
	// mapBuilder.withKeyFormat(keyFormatContainer.get(msi).get());
	// else
	// mapBuilder.withKeyFormat(defaultKeyFormat);
	// return ConfigMapCreator.this.create(mapBuilder, msi);
	// }
	// };
	// }
	//
	// protected abstract void prepare(ExpressoQIS exS) throws QonfigInterpretationException;
	//
	// protected abstract void prepare(ExpressoQIS exS, ModelInstanceType<M, MV> instanceType) throws ExpressoInterpretationException;
	//
	// protected abstract MV create(ObservableConfigMapBuilder<K, V> builder, ModelSetInstance msi) throws ModelInstantiationException;
	// }
	//
	// private <T> ConfigValueCreator<T, ObservableValueSet<?>, ObservableValueSet<T>> valueSetCreator() {
	// return new ConfigValueCreator<T, ObservableValueSet<?>, ObservableValueSet<T>>(ModelTypes.ValueSet) {
	// @Override
	// protected void prepare(ExpressoQIS exS) throws QonfigInterpretationException {
	// }
	//
	// @Override
	// protected void prepare(InterpretedExpressoEnv env, ModelInstanceType<ObservableValueSet<?>, ObservableValueSet<T>> instanceType)
	// throws ExpressoInterpretationException {
	// }
	//
	// @Override
	// protected ObservableValueSet<T> create(ObservableConfigValueBuilder<T> builder, ModelSetInstance msi)
	// throws ModelInstantiationException {
	// return builder.buildEntitySet(null);
	// }
	// };
	// }
	//
	// private <T> ConfigValueCreator<T, ObservableCollection<?>, ObservableCollection<T>> collectionCreator() {
	// return new ConfigValueCreator<T, ObservableCollection<?>, ObservableCollection<T>>(ModelTypes.Collection) {
	// @Override
	// protected void prepare(ExpressoQIS exS) throws QonfigInterpretationException {
	// }
	//
	// @Override
	// protected void prepare(InterpretedExpressoEnv env,
	// ModelInstanceType<ObservableCollection<?>, ObservableCollection<T>> instanceType) throws ExpressoInterpretationException {
	// }
	//
	// @Override
	// protected ObservableCollection<T> create(ObservableConfigValueBuilder<T> builder, ModelSetInstance msi)
	// throws ModelInstantiationException {
	// return builder.buildCollection(null);
	// }
	// };
	// }
	//
	// private <T> ConfigValueCreator<T, ObservableSortedCollection<?>, ObservableSortedCollection<T>> sortedCollectionCreator() {
	// return new ConfigValueCreator<T, ObservableSortedCollection<?>, ObservableSortedCollection<T>>(ModelTypes.SortedCollection) {
	// private ParsedSorting theSortingCreator;
	// private ModelValueSynth<SettableValue<?>, SettableValue<Comparator<T>>> theSortingContainer;
	//
	// @Override
	// protected void prepare(ExpressoQIS exS) throws QonfigInterpretationException {
	// ExpressoQIS sorting = exS.forChildren("sort").peekFirst();
	// if (sorting != null)
	// theSortingCreator = sorting.interpret(ParsedSorting.class);
	// else
	// theSortingCreator = ExSort.getDefaultSorting(exS.getElement().getPositionInFile());
	// }
	//
	// @Override
	// protected void prepare(InterpretedExpressoEnv env,
	// ModelInstanceType<ObservableSortedCollection<?>, ObservableSortedCollection<T>> instanceType)
	// throws ExpressoInterpretationException {
	// theSortingContainer = theSortingCreator.evaluate((TypeToken<T>) instanceType.getType(0));
	// }
	//
	// @Override
	// protected ObservableSortedCollection<T> create(ObservableConfigValueBuilder<T> builder, ModelSetInstance msi)
	// throws ModelInstantiationException {
	// Comparator<T> sorting = theSortingContainer.get(msi).get();
	// return builder.buildCollection(null).flow().sorted(sorting).collectActive(msi.getUntil());
	// }
	// };
	// }
	//
	// private <T> ConfigValueCreator<T, ObservableSet<?>, ObservableSet<T>> setCreator() {
	// return new ConfigValueCreator<T, ObservableSet<?>, ObservableSet<T>>(ModelTypes.Set) {
	// @Override
	// protected void prepare(ExpressoQIS exS) throws QonfigInterpretationException {
	// }
	//
	// @Override
	// protected void prepare(InterpretedExpressoEnv env, ModelInstanceType<ObservableSet<?>, ObservableSet<T>> instanceType)
	// throws ExpressoInterpretationException {
	// }
	//
	// @Override
	// protected ObservableSet<T> create(ObservableConfigValueBuilder<T> builder, ModelSetInstance msi)
	// throws ModelInstantiationException {
	// return builder.buildCollection(null).flow().distinct().collectActive(msi.getUntil());
	// }
	// };
	// }
	//
	// private <T> ConfigValueCreator<T, ObservableSortedSet<?>, ObservableSortedSet<T>> sortedSetCreator() {
	// return new ConfigValueCreator<T, ObservableSortedSet<?>, ObservableSortedSet<T>>(ModelTypes.SortedSet) {
	// private ParsedSorting theSortingCreator;
	// private ModelValueSynth<SettableValue<?>, SettableValue<Comparator<T>>> theSortingContainer;
	//
	// @Override
	// protected void prepare(ExpressoQIS exS) throws QonfigInterpretationException {
	// ExpressoQIS sorting = exS.forChildren("sort").peekFirst();
	// if (sorting != null)
	// theSortingCreator = sorting.interpret(ParsedSorting.class);
	// else
	// theSortingCreator = ExpressoBaseV0_1.getDefaultSorting(exS.getElement().getPositionInFile());
	// }
	//
	// @Override
	// protected void prepare(InterpretedExpressoEnv env,
	// ModelInstanceType<ObservableSortedSet<?>, ObservableSortedSet<T>> instanceType) throws ExpressoInterpretationException {
	// theSortingContainer = theSortingCreator.evaluate((TypeToken<T>) instanceType.getType(0));
	// }
	//
	// @Override
	// protected ObservableSortedSet<T> create(ObservableConfigValueBuilder<T> builder, ModelSetInstance msi)
	// throws ModelInstantiationException {
	// Comparator<T> sorting = theSortingContainer.get(msi).get();
	// return builder.buildCollection(null).flow().distinctSorted(sorting, false).collectActive(msi.getUntil());
	// }
	// };
	// }
	//
	// private <K, V> ConfigMapCreator<K, V, ObservableMap<?, ?>, ObservableMap<K, V>> mapCreator() {
	// return new ConfigMapCreator<K, V, ObservableMap<?, ?>, ObservableMap<K, V>>(ModelTypes.Map) {
	// @Override
	// protected void prepare(ExpressoQIS exS) throws QonfigInterpretationException {
	// }
	//
	// @Override
	// protected void prepare(ExpressoQIS exS, ModelInstanceType<ObservableMap<?, ?>, ObservableMap<K, V>> instanceType)
	// throws ExpressoInterpretationException {
	// }
	//
	// @Override
	// protected ObservableMap<K, V> create(ObservableConfigMapBuilder<K, V> builder, ModelSetInstance msi)
	// throws ModelInstantiationException {
	// return builder.buildMap(null);
	// }
	// };
	// }
	//
	// private <K, V> ConfigMapCreator<K, V, ObservableSortedMap<?, ?>, ObservableSortedMap<K, V>> sortedMapCreator() {
	// return new ConfigMapCreator<K, V, ObservableSortedMap<?, ?>, ObservableSortedMap<K, V>>(ModelTypes.SortedMap) {
	// @Override
	// protected void prepare(ExpressoQIS exS) throws QonfigInterpretationException {
	// throw new QonfigInterpretationException("config-based sorted-maps are not yet supported",
	// exS.getElement().getPositionInFile(), 0);
	// }
	//
	// @Override
	// protected void prepare(ExpressoQIS exS, ModelInstanceType<ObservableSortedMap<?, ?>, ObservableSortedMap<K, V>> instanceType)
	// throws ExpressoInterpretationException {
	// }
	//
	// @Override
	// protected ObservableSortedMap<K, V> create(ObservableConfigMapBuilder<K, V> builder, ModelSetInstance msi)
	// throws ModelInstantiationException {
	// return null;
	// }
	// };
	// }
	//
	// private <K, V> ConfigValueCreator<V, ObservableMultiMap<?, ?>, ObservableMultiMap<K, V>> multiMapCreator() {
	// return new ConfigValueCreator<V, ObservableMultiMap<?, ?>, ObservableMultiMap<K, V>>(ModelTypes.MultiMap) {
	// private TypeToken<K> theKeyType;
	//
	// @Override
	// protected void prepare(ExpressoQIS exS) throws QonfigInterpretationException {
	// }
	//
	// @Override
	// protected void prepare(InterpretedExpressoEnv env,
	// ModelInstanceType<ObservableMultiMap<?, ?>, ObservableMultiMap<K, V>> instanceType) throws ExpressoInterpretationException {
	// theKeyType = (TypeToken<K>) instanceType.getType(0);
	// }
	//
	// @Override
	// protected ObservableMultiMap<K, V> create(ObservableConfigValueBuilder<V> builder, ModelSetInstance msi)
	// throws ModelInstantiationException {
	// return builder.asMap(theKeyType).buildMultiMap(null);
	// }
	// };
	// }
	//
	// private <K, V> ConfigMapCreator<K, V, ObservableSortedMultiMap<?, ?>, ObservableSortedMultiMap<K, V>> sortedMultiMapCreator() {
	// return new ConfigMapCreator<K, V, ObservableSortedMultiMap<?, ?>, ObservableSortedMultiMap<K, V>>(ModelTypes.SortedMultiMap) {
	// @Override
	// protected void prepare(ExpressoQIS exS) throws QonfigInterpretationException {
	// throw new QonfigInterpretationException("config-based sorted-multi-maps are not yet supported",
	// exS.getElement().getPositionInFile(), 0);
	// }
	//
	// @Override
	// protected void prepare(ExpressoQIS exS,
	// ModelInstanceType<ObservableSortedMultiMap<?, ?>, ObservableSortedMultiMap<K, V>> instanceType)
	// throws ExpressoInterpretationException {
	// }
	//
	// @Override
	// protected ObservableSortedMultiMap<K, V> create(ObservableConfigMapBuilder<K, V> builder, ModelSetInstance msi)
	// throws ModelInstantiationException {
	// return null;
	// }
	// };
	// }
	//
	private void configureFormats(QonfigInterpreterCore.Builder interpreter) {
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
	//
	// private <T> CompiledModelValue<SettableValue<?>>> createTextConfigFormat(ExpressoQIS session)
	// throws QonfigInterpretationException {
	// CompiledModelValue<SettableValue<?>, SettableValue<Format<T>>> textFormatCreator = session
	// .interpretChildren("text-format", CompiledModelValue.class).getFirst();
	// CompiledExpression defaultV = session.getAttributeExpression("default");
	// String defaultS = session.getAttributeText("default-text");
	// if (defaultV != null && defaultS != null)
	// defaultV.throwQonfigException("default and default-text cannot both be specified", null);
	// LocatedFilePosition formatPosition = session.forChildren("text-format").getFirst().getElement().getPositionInFile();
	// ErrorReporting defaultReporting = defaultS == null ? null
	// : session.reporting().at(session.getAttributeValuePosition("default-text"));
	// return CompiledModelValue.of(session.getElement().getType().getName(), ModelTypes.Value, () -> {
	// InterpretedValueSynth<SettableValue<?>, SettableValue<Format<T>>> textFormatContainer;
	// try {
	// textFormatContainer = textFormatCreator.createSynthesizer()
	// .as(ModelTypes.Value.forType(TypeTokens.get().keyFor(Format.class).<Format<T>> wildCard())).interpret();
	// } catch (TypeConversionException e) {
	// throw new ExpressoInterpretationException("Could not interpret " + textFormatCreator + " as a format", formatPosition, 0,
	// e);
	// }
	// TypeToken<T> formattedType = (TypeToken<T>) textFormatContainer.getType().getType(0)
	// .resolveType(Format.class.getTypeParameters()[0]);
	// ModelValueSynth<SettableValue<?>, SettableValue<T>> defaultValueContainer;
	// if (defaultV != null)
	// defaultValueContainer = defaultV.evaluate(ModelTypes.Value.forType(formattedType));
	// else if (defaultS != null)
	// defaultValueContainer = textFormatContainer.map(ModelTypes.Value.forType(formattedType), format -> {
	// return SettableValue.asSettable(format.map(formattedType, f -> {
	// if (f == null)
	// return null;
	// try {
	// return f.parse(defaultS);
	// } catch (ParseException e) {
	// defaultReporting.error("Could not parse '" + defaultS + "'", e);
	// return TypeTokens.get().getDefaultValue(formattedType);
	// }
	// }), __ -> "Unsettable");
	// });
	// else
	// defaultValueContainer = null;
	// TypeToken<ObservableConfigFormat<T>> cfType = TypeTokens.get().keyFor(ObservableConfigFormat.class)
	// .<ObservableConfigFormat<T>> parameterized(formattedType);
	// return new ModelValueSynth<SettableValue<?>, SettableValue<ObservableConfigFormat<T>>>() {
	// private final ModelInstanceType<SettableValue<?>, SettableValue<ObservableConfigFormat<T>>> theType = ModelTypes.Value
	// .forType(cfType);
	//
	// @Override
	// public ModelType<SettableValue<?>> getModelType() {
	// return theType.getModelType();
	// }
	//
	// @Override
	// public SettableValue<ObservableConfigFormat<T>> get(ModelSetInstance models)
	// throws ModelInstantiationException, IllegalStateException {
	// SettableValue<Format<T>> textFormatV = textFormatContainer.get(models);
	// SettableValue<T> defaultValueV = defaultValueContainer == null ? null : defaultValueContainer.get(models);
	// return SettableValue.asSettable(//
	// textFormatV.map(cfType, textFormat -> ObservableConfigFormat.ofQommonFormat(textFormat, defaultValueV)),
	// __ -> "Unsettable");
	// }
	//
	// @Override
	// public List<? extends ModelValueSynth<?, ?>> getComponents() {
	// return Collections.singletonList(textFormatContainer);
	// }
	//
	// @Override
	// public SettableValue<ObservableConfigFormat<T>> forModelCopy(SettableValue<ObservableConfigFormat<T>> value,
	// ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
	// SettableValue<Format<T>> srcTextFormat = textFormatContainer.get(sourceModels);
	// SettableValue<Format<T>> newTextFormat = textFormatContainer.forModelCopy(srcTextFormat, sourceModels, newModels);
	// if (srcTextFormat == newTextFormat)
	// return value;
	// SettableValue<T> defaultValueV = defaultValueContainer == null ? null : defaultValueContainer.get(newModels);
	// return SettableValue.asSettable(//
	// newTextFormat.map(cfType, textFormat -> ObservableConfigFormat.ofQommonFormat(textFormat, defaultValueV)),
	// __ -> "Unsettable");
	// }
	// };
	// });
	// }
	//
	// private static CompiledModelValue<SettableValue<?>> createSftpFileSource(ExpressoQIS exS)
	// throws QonfigInterpretationException {
	// CompiledExpression hostX = exS.getAttributeExpression("host");
	// CompiledExpression userX = exS.getAttributeExpression("user");
	// CompiledExpression passwordX = exS.getAttributeExpression("password");
	// CompiledExpression connectingX = exS.getAttributeExpression("connecting");
	// CompiledExpression connectedX = exS.getAttributeExpression("connected");
	// CompiledExpression timeoutX = exS.getAttributeExpression("timeout");
	// CompiledExpression retryX = exS.getAttributeExpression("retry");
	// return CompiledModelValue.of(exS.getElement().getType().getName(), ModelTypes.Value, () -> {
	// ModelValueSynth<SettableValue<?>, SettableValue<String>> hostVC = hostX.evaluate(ModelTypes.Value.STRING);
	// ModelValueSynth<SettableValue<?>, SettableValue<String>> userVC = userX.evaluate(ModelTypes.Value.STRING);
	// ModelValueSynth<SettableValue<?>, SettableValue<String>> passwordVC = passwordX.evaluate(ModelTypes.Value.STRING);
	// ModelValueSynth<SettableValue<?>, SettableValue<Boolean>> connectingVC = connectingX == null ? null
	// : connectingX.evaluate(ModelTypes.Value.BOOLEAN);
	// ModelValueSynth<SettableValue<?>, SettableValue<Boolean>> connectedVC = connectedX == null ? null
	// : connectedX.evaluate(ModelTypes.Value.BOOLEAN);
	// ModelValueSynth<SettableValue<?>, SettableValue<Duration>> timeoutVC = timeoutX
	// .evaluate(ModelTypes.Value.forType(Duration.class));
	// ModelValueSynth<SettableValue<?>, SettableValue<Integer>> retryVC = retryX.evaluate(ModelTypes.Value.INT);
	// return new ModelValueSynth<SettableValue<?>, SettableValue<SftpFileSource>>() {
	// private final ModelInstanceType<SettableValue<?>, SettableValue<SftpFileSource>> theType = ModelTypes.Value
	// .forType(SftpFileSource.class);
	//
	// @Override
	// public ModelType<SettableValue<?>> getModelType() {
	// return ModelTypes.Value;
	// }
	//
	// @Override
	// public SettableValue<SftpFileSource> get(ModelSetInstance models)
	// throws ModelInstantiationException, IllegalStateException {
	// SettableValue<String> host = hostVC.get(models);
	// SettableValue<String> user = userVC.get(models);
	// SettableValue<String> password = passwordVC.get(models);
	// SettableValue<Boolean> connecting = connectingVC == null ? null : connectingVC.get(models);
	// SettableValue<Boolean> connected = connectedVC == null ? null : connectedVC.get(models);
	// SettableValue<Duration> timeout = timeoutVC.get(models);
	// SettableValue<Integer> retry = retryVC.get(models);
	// return createFileSource(host, user, password, connecting, connected, timeout, retry);
	// }
	//
	// @Override
	// public List<? extends ModelValueSynth<?, ?>> getComponents() {
	// return BetterList.of(Stream.of(hostVC, userVC, passwordVC, connectingVC, connectedVC, timeoutVC, retryVC)//
	// .filter(vc -> vc != null));
	// }
	//
	// @Override
	// public SettableValue<SftpFileSource> forModelCopy(SettableValue<SftpFileSource> value, ModelSetInstance sourceModels,
	// ModelSetInstance newModels) throws ModelInstantiationException {
	// SettableValue<String> srcHost = hostVC.get(sourceModels);
	// SettableValue<String> srcUser = userVC.get(sourceModels);
	// SettableValue<String> srcPassword = passwordVC.get(sourceModels);
	// SettableValue<Boolean> srcConnecting = connectingVC == null ? null : connectingVC.get(sourceModels);
	// SettableValue<Boolean> srcConnected = connectedVC == null ? null : connectedVC.get(sourceModels);
	// SettableValue<Duration> srcTimeout = timeoutVC.get(sourceModels);
	// SettableValue<Integer> srcRetry = retryVC.get(sourceModels);
	//
	// SettableValue<String> newHost = hostVC.forModelCopy(srcHost, sourceModels, newModels);
	// SettableValue<String> newUser = userVC.forModelCopy(srcUser, sourceModels, newModels);
	// SettableValue<String> newPassword = passwordVC.forModelCopy(srcPassword, sourceModels, newModels);
	// SettableValue<Boolean> newConnecting = connectingVC == null ? null
	// : connectingVC.forModelCopy(srcConnecting, sourceModels, newModels);
	// SettableValue<Boolean> newConnected = connectedVC == null ? null
	// : connectedVC.forModelCopy(srcConnected, sourceModels, newModels);
	// SettableValue<Duration> newTimeout = timeoutVC.forModelCopy(srcTimeout, sourceModels, newModels);
	// SettableValue<Integer> newRetry = retryVC.forModelCopy(srcRetry, sourceModels, newModels);
	//
	// if (srcHost != newHost || srcUser != newUser || srcPassword != newPassword || srcConnecting != newConnecting
	// || srcConnected != newConnected || srcTimeout != newTimeout || srcRetry != newRetry)
	// return createFileSource(newHost, newUser, newPassword, newConnecting, newConnected, newTimeout, newRetry);
	// else
	// return value;
	// }
	//
	// private SettableValue<SftpFileSource> createFileSource(SettableValue<String> host, SettableValue<String> user,
	// SettableValue<String> password, SettableValue<Boolean> connecting, SettableValue<Boolean> connected,
	// SettableValue<Duration> timeout, SettableValue<Integer> retry) {
	// ObservableValue<Integer> timeoutMSV = timeout.map(d -> d == null ? 100 : (int) d.toMillis());
	// return SettableValue.asSettable(host.transform(SftpFileSource.class, tx -> tx//
	// .combineWith(user).combineWith(password).combineWith(timeoutMSV)//
	// .build((hostS, txv) -> {
	// String userS = txv.get(user);
	// String passwordS = txv.get(password);
	// int timeoutI = txv.get(timeoutMSV);
	// if (connected != null)
	// connected.set(false, null);
	// if (connecting != null)
	// connecting.set(true, null);
	// SftpFileSource sftp;
	// try {
	// sftp = new SftpFileSource(hostS, userS, sftpSession -> {
	// sftpSession.withTimeout(timeoutI)//
	// .withAuthentication(userS, passwordS);
	// }, "/");
	// retry.changes()
	// .takeUntil(Observable.or(user.noInitChanges(), password.noInitChanges(), timeout.noInitChanges()))
	// .act(evt -> sftp.setRetryCount(evt.getNewValue() == null ? 1 : evt.getNewValue()));
	// BetterFile root = BetterFile.at(sftp, "/");
	// if (root.isDirectory()) { // Attempt to connect
	// if (connected != null)
	// connected.set(true, null);
	// } else {
	// if (connected != null)
	// connected.set(false, null);
	// }
	// } catch (RuntimeException | Error e) {
	// if (connected != null)
	// connected.set(false, null);
	// return null;
	// } finally {
	// if (connecting != null)
	// connecting.set(false, null);
	// }
	// return sftp;
	// })), __ -> "Unsettable");
	// }
	// };
	// });
	// }
	//
	// private static CompiledModelValue<SettableValue<?>> createArchiveEnabledFileSource(
	// ExpressoQIS exS) throws QonfigInterpretationException {
	// CompiledModelValue<SettableValue<?>, SettableValue<BetterFile.FileDataSource>> wrappedCreator;
	// ExpressoQIS wrappedSession = exS.forChildren("wrapped").peekFirst();
	// if (wrappedSession == null)
	// wrappedCreator = CompiledModelValue.literal(TypeTokens.get().of(BetterFile.FileDataSource.class), new NativeFileSource(),
	// "native-file-source");
	// else
	// wrappedCreator = wrappedSession.interpret(CompiledModelValue.class);
	// CompiledExpression archiveDepthX = exS.getAttributeExpression("max-archive-depth");
	// List<CompiledModelValue<SettableValue<?>, SettableValue<ArchiveEnabledFileSource.FileArchival>>> archiveMethodCreators;
	// archiveMethodCreators = exS.interpretChildren("archive-method", CompiledModelValue.class);
	// LocatedFilePosition position;
	// if (wrappedSession != null)
	// position = wrappedSession.getElement().getPositionInFile();
	// else
	// position = exS.getElement().getPositionInFile();
	// List<LocatedFilePosition> archivePositions = exS.getChildren("archive-method").stream().map(s -> s.getPositionInFile())
	// .collect(Collectors.toList());
	// return CompiledModelValue.of(exS.getElement().getType().getName(), ModelTypes.Value, () -> {
	// ModelValueSynth<SettableValue<?>, SettableValue<BetterFile.FileDataSource>> wrappedContainer;
	// try {
	// wrappedContainer = wrappedCreator.createSynthesizer().as(ModelTypes.Value.forType(BetterFile.FileDataSource.class));
	// } catch (TypeConversionException e) {
	// throw new ExpressoInterpretationException("Could not evaluate " + wrappedCreator + " as a file source", //
	// position, 0, e);
	// }
	// ModelValueSynth<SettableValue<?>, SettableValue<Integer>> archiveDepthVC = archiveDepthX.evaluate(ModelTypes.Value.INT);
	// List<ModelValueSynth<SettableValue<?>, SettableValue<ArchiveEnabledFileSource.FileArchival>>> archiveMethodContainers = new
	// ArrayList<>(
	// archiveMethodCreators.size());
	// int i = 0;
	// for (CompiledModelValue<SettableValue<?>, SettableValue<ArchiveEnabledFileSource.FileArchival>> amc : archiveMethodCreators) {
	// try {
	// archiveMethodContainers
	// .add(amc.createSynthesizer().as(ModelTypes.Value.forType(ArchiveEnabledFileSource.FileArchival.class)));
	// } catch (TypeConversionException e) {
	// throw new ExpressoInterpretationException("Could not interpret archive method", //
	// archivePositions.get(i), 0, e);
	// }
	// i++;
	// }
	// return new ModelValueSynth<SettableValue<?>, SettableValue<ArchiveEnabledFileSource>>() {
	// private final ModelInstanceType<SettableValue<?>, SettableValue<ArchiveEnabledFileSource>> theType = ModelTypes.Value
	// .forType(ArchiveEnabledFileSource.class);
	//
	// @Override
	// public ModelType<SettableValue<?>> getModelType() {
	// return ModelTypes.Value;
	// }
	//
	// @Override
	// public SettableValue<ArchiveEnabledFileSource> get(ModelSetInstance models)
	// throws ModelInstantiationException, IllegalStateException {
	// SettableValue<BetterFile.FileDataSource> wrappedV = wrappedContainer.get(models);
	// SettableValue<Integer> archiveDepth = archiveDepthVC.get(models);
	// List<SettableValue<ArchiveEnabledFileSource.FileArchival>> archiveMethods = new ArrayList<>(
	// archiveMethodContainers.size());
	// for (ModelValueSynth<SettableValue<?>, SettableValue<ArchiveEnabledFileSource.FileArchival>> amc : archiveMethodContainers)
	// archiveMethods.add(amc.get(models));
	// return createFileSource(wrappedV, archiveDepth, archiveMethods);
	// }
	//
	// @Override
	// public List<? extends ModelValueSynth<?, ?>> getComponents() {
	// return BetterList.of(Stream.concat(Stream.of(wrappedContainer, archiveDepthVC), archiveMethodContainers.stream()));
	// }
	//
	// @Override
	// public SettableValue<ArchiveEnabledFileSource> forModelCopy(SettableValue<ArchiveEnabledFileSource> value,
	// ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
	// SettableValue<BetterFile.FileDataSource> srcWrapped = wrappedContainer.get(sourceModels);
	// SettableValue<Integer> srcArchiveDepth = archiveDepthVC.get(sourceModels);
	//
	// SettableValue<BetterFile.FileDataSource> newWrapped = wrappedContainer.forModelCopy(srcWrapped, sourceModels,
	// newModels);
	// SettableValue<Integer> newArchiveDepth = archiveDepthVC.forModelCopy(srcArchiveDepth, sourceModels, newModels);
	//
	// List<SettableValue<ArchiveEnabledFileSource.FileArchival>> srcArchiveMethods = new ArrayList<>(
	// archiveMethodContainers.size());
	// List<SettableValue<ArchiveEnabledFileSource.FileArchival>> newArchiveMethods = new ArrayList<>(
	// archiveMethodContainers.size());
	// boolean identical = srcWrapped == newWrapped && srcArchiveDepth == newArchiveDepth;
	// for (ModelValueSynth<SettableValue<?>, SettableValue<ArchiveEnabledFileSource.FileArchival>> amc : archiveMethodContainers) {
	// SettableValue<ArchiveEnabledFileSource.FileArchival> srcAM = amc.get(sourceModels);
	// SettableValue<ArchiveEnabledFileSource.FileArchival> newAM = amc.forModelCopy(srcAM, sourceModels, newModels);
	// srcArchiveMethods.add(srcAM);
	// newArchiveMethods.add(newAM);
	// identical &= srcAM == newAM;
	// }
	//
	// if (identical)
	// return value;
	// else
	// return createFileSource(newWrapped, newArchiveDepth, newArchiveMethods);
	// }
	//
	// private SettableValue<ArchiveEnabledFileSource> createFileSource(SettableValue<FileDataSource> wrapped,
	// SettableValue<Integer> archiveDepth, List<SettableValue<FileArchival>> archiveMethods) {
	// return SettableValue.asSettable(wrapped.transform(ArchiveEnabledFileSource.class, tx -> {
	// Transformation.TransformationBuilder<BetterFile.FileDataSource, ArchiveEnabledFileSource, ?> tx2 = tx
	// .combineWith(archiveDepth);
	// for (SettableValue<ArchiveEnabledFileSource.FileArchival> archiveMethod : archiveMethods)
	// tx2 = tx2.combineWith(archiveMethod);
	// return tx2.build((wrappedFS, txv) -> {
	// Integer maxDepth = txv.get(archiveDepth);
	// List<ArchiveEnabledFileSource.FileArchival> archiveMethodInsts = new ArrayList<>(archiveMethods.size());
	// for (SettableValue<ArchiveEnabledFileSource.FileArchival> am : archiveMethods)
	// archiveMethodInsts.add(txv.get(am));
	// ArchiveEnabledFileSource fileSource = new ArchiveEnabledFileSource(wrappedFS);
	// fileSource.withArchival(archiveMethodInsts);
	// fileSource.setMaxArchiveDepth(maxDepth == null ? 10 : maxDepth);
	// return fileSource;
	// });
	// }), __ -> "Unsettable");
	// }
	// };
	// });
	// }
	//
	// private static <T> SettableValue<Format<T>> createWrappedValidatedFormat(SettableValue<Format<T>> format,
	// List<Function<T, String>> validation) {
	// Function<T, String> validationFn = null;
	// for (Function<T, String> val : validation) {
	// if (validationFn == null)
	// validationFn = val;
	// else {
	// Function<T, String> old = validationFn;
	// validationFn = v -> {
	// String err = old.apply(v);
	// if (err == null)
	// err = val.apply(v);
	// return err;
	// };
	// }
	// }
	// final Function<T, String> finalVal = validationFn;
	// return SettableValue.asSettable(format.map(format.getType(), f -> f == null ? null : Format.validate(f, finalVal)), //
	// __ -> "Not settable");
	// }
	//
	// private <T> CompiledModelValue<SettableValue<?>>> configureFormatValidation(
	// InterpretedValueSynth<SettableValue<?>, SettableValue<Format<T>>> formatCreator, CoreSession session, Object prepared)
	// throws QonfigInterpretationException {
	// List<ValidationProducer> validatorProducers = new ArrayList<>();
	// for (ExpressoQIS valSession : session.as(ExpressoQIS.class).forChildren())
	// validatorProducers.add(valSession.interpret(ValidationProducer.class));
	// return CompiledModelValue.of(formatCreator::toString, ModelTypes.Value, () -> {
	// InterpretedValueSynth<SettableValue<?>, SettableValue<Format<T>>> formatContainer = formatCreator.createSynthesizer()
	// .interpret();
	// TypeToken<T> formattedType = (TypeToken<T>) formatContainer.getType().getType(0)
	// .resolveType(Format.class.getTypeParameters()[0]);
	// List<Validation<T>> validationContainers = new ArrayList<>();
	// for (ValidationProducer valProducer : validatorProducers)
	// validationContainers.add(valProducer.createValidator(formattedType));
	//
	// class ValidatedFormat extends SettableValue.WrappingSettableValue<Format<T>> {
	// private final SettableValue<Format<T>> theFormat;
	// private final List<Function<T, String>> theValidation;
	//
	// public ValidatedFormat(SettableValue<Format<T>> format, List<Function<T, String>> validation) {
	// super(createWrappedValidatedFormat(format, validation));
	// theFormat = format;
	// theValidation = validation;
	// }
	//
	// ValidatedFormat forModelCopy(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
	// SettableValue<Format<T>> newFormat = formatContainer.forModelCopy(theFormat, sourceModels, newModels);
	// boolean different = newFormat != theFormat;
	// List<Function<T, String>> newVal = new ArrayList<>(theValidation.size());
	// for (int v = 0; v < theValidation.size(); v++) {
	// Function<T, String> newValI = validationContainers.get(v).forModelCopy(theValidation.get(v), sourceModels,
	// newModels);
	// different = newValI != theValidation.get(v);
	// newVal.add(newValI);
	// }
	// if (!different)
	// return this;
	// return new ValidatedFormat(newFormat, newVal);
	// }
	// }
	// class ValidatedFormatContainer implements ModelValueSynth<SettableValue<?>, SettableValue<Format<T>>> {
	// private final ModelValueSynth<SettableValue<?>, SettableValue<Format<T>>> theFormatContainer;
	// private final List<Validation<T>> theValidationContainers;
	//
	// ValidatedFormatContainer(ModelValueSynth<SettableValue<?>, SettableValue<Format<T>>> format,
	// List<Validation<T>> validation) {
	// theFormatContainer = format;
	// theValidationContainers = validation;
	// }
	//
	// @Override
	// public ModelType<SettableValue<?>> getModelType() {
	// return formatContainer.getModelType();
	// }
	//
	// @Override
	// public SettableValue<Format<T>> get(ModelSetInstance models) throws ModelInstantiationException {
	// return new ValidatedFormat(//
	// theFormatContainer.get(models), //
	// BetterList.of2(theValidationContainers.stream(), val -> val.getTest(models)));
	// }
	//
	// @Override
	// public SettableValue<Format<T>> forModelCopy(SettableValue<Format<T>> formatV, ModelSetInstance sourceModels,
	// ModelSetInstance newModels) throws ModelInstantiationException {
	// return ((ValidatedFormat) formatV).forModelCopy(sourceModels, newModels);
	// }
	//
	// @Override
	// public List<? extends ModelValueSynth<?, ?>> getComponents() {
	// return BetterList.of(
	// Stream.concat(Stream.of(formatContainer), validationContainers.stream().flatMap(v -> v.getComponents().stream())));
	// }
	// }
	// return new ValidatedFormatContainer(formatContainer, validationContainers);
	// });
	// }
	//
	// private CompiledModelValue<SettableValue<?>> createDoubleFormat(ExpressoQIS session)
	// throws QonfigInterpretationException {
	// int sigDigs = Integer.parseInt(session.getAttributeText("sig-digs"));
	// Format.SuperDoubleFormatBuilder builder = Format.doubleFormat(sigDigs);
	// String unit = session.getAttributeText("unit");
	// boolean withMetricPrefixes = session.getAttribute("metric-prefixes", boolean.class);
	// boolean withMetricPrefixesP2 = session.getAttribute("metric-prefixes-p2", boolean.class);
	// List<? extends ExpressoQIS> prefixes = session.forChildren("prefix");
	// if (unit != null) {
	// builder.withUnit(unit, session.getAttribute("unit-required", boolean.class));
	// if (withMetricPrefixes) {
	// if (withMetricPrefixesP2)
	// session.reporting().warn("Both 'metric-prefixes' and 'metric-prefixes-p2' specified");
	// builder.withMetricPrefixes();
	// } else if (withMetricPrefixesP2)
	// builder.withMetricPrefixesPower2();
	// for (ExpressoQIS prefix : prefixes) {
	// String prefixName = prefix.getAttributeText("name");
	// String expS = prefix.getAttributeText("exp");
	// String multS = prefix.getAttributeText("mult");
	// if (expS != null) {
	// if (multS != null)
	// session.reporting().warn("Both 'exp' and 'mult' specified for prefix '" + prefixName + "'");
	// builder.withPrefix(prefixName, Integer.parseInt(expS));
	// } else if (multS != null)
	// builder.withPrefix(prefixName, Double.parseDouble(multS));
	// else
	// session.reporting().warn("Neither 'exp' nor 'mult' specified for prefix '" + prefixName + "'");
	// }
	// } else {
	// if (withMetricPrefixes)
	// session.reporting().warn("'metric-prefixes' specified without a unit");
	// if (withMetricPrefixesP2)
	// session.reporting().warn("'metric-prefixes-p2' specified without a unit");
	// if (!prefixes.isEmpty())
	// session.reporting().warn("prefixes specified without a unit");
	// }
	// return CompiledModelValue.literal(TypeTokens.get().keyFor(Format.class).<Format<Double>> parameterized(Double.class),
	// builder.build(), "double");
	// }
	//
	// private CompiledModelValue<SettableValue<?>> createInstantFormat(ExpressoQIS session)
	// throws QonfigInterpretationException {
	// String dayFormat = session.getAttributeText("day-format");
	// TimeEvaluationOptions options = TimeUtils.DEFAULT_OPTIONS;
	// QonfigAttributeDef tzAttr = session.getAttributeDef(null, null, "time-zone");
	// String tzs = session.getAttributeText("time-zone");
	// if (tzs != null) {
	// TimeZone timeZone = TimeZone.getTimeZone(tzs);
	// if (timeZone.getRawOffset() == 0 && !timeZone.useDaylightTime()//
	// && !(tzs.equalsIgnoreCase("GMT") || tzs.equalsIgnoreCase("Z"))) {
	// QonfigValue tzAttrV = session.getElement().getAttributes().get(tzAttr);
	// throw new QonfigInterpretationException("Unrecognized time-zone '" + tzs + "'",
	// new LocatedFilePosition(tzAttrV.fileLocation, tzAttrV.position.getPosition(0)), tzs.length());
	// }
	// options = options.withTimeZone(timeZone);
	// }
	// try {
	// options = options.withMaxResolution(TimeUtils.DateElementType.valueOf(session.getAttributeText("max-resolution")));
	// } catch (IllegalArgumentException e) {
	// session.reporting().warn("Unrecognized instant resolution: '" + session.getAttributeText("max-resolution"));
	// }
	// options = options.with24HourFormat(session.getAttribute("format-24h", boolean.class));
	// String rteS = session.getAttributeText("relative-eval-type");
	// try {
	// options = options.withEvaluationType(TimeUtils.RelativeInstantEvaluation.valueOf(rteS));
	// } catch (IllegalArgumentException e) {
	// session.reporting().warn("Unrecognized relative evaluation type: '" + rteS);
	// }
	// TimeEvaluationOptions fOptions = options;
	// TypeToken<Instant> instantType = TypeTokens.get().of(Instant.class);
	// TypeToken<Format<Instant>> formatType = TypeTokens.get().keyFor(Format.class).<Format<Instant>> parameterized(instantType);
	// ModelInstanceType<SettableValue<?>, SettableValue<Instant>> instantModelType = ModelTypes.Value.forType(instantType);
	// ModelInstanceType<SettableValue<?>, SettableValue<Format<Instant>>> formatInstanceType = ModelTypes.Value.forType(formatType);
	// CompiledExpression relativeX = session.getAttributeExpression("relative-to");
	// return CompiledModelValue.of(session.getElement().getType().getName(), ModelTypes.Value, () -> {
	// ModelValueSynth<SettableValue<?>, SettableValue<Instant>> relativeTo;
	// if (relativeX != null)
	// relativeTo = relativeX.evaluate(instantModelType);
	// else {
	// relativeTo = ModelValueSynth.of(instantModelType, msi -> new SettableValue<Instant>() {
	// private long theStamp;
	//
	// @Override
	// public Object getIdentity() {
	// return Identifiable.baseId("Instant.now()", "Instant.now()");
	// }
	//
	// @Override
	// public TypeToken<Instant> getType() {
	// return null;
	// }
	//
	// @Override
	// public Instant get() {
	// return Instant.now();
	// }
	//
	// @Override
	// public Observable<ObservableValueEvent<Instant>> noInitChanges() {
	// return Observable.empty();
	// }
	//
	// @Override
	// public long getStamp() {
	// return theStamp++;
	// }
	//
	// @Override
	// public boolean isLockSupported() {
	// return false;
	// }
	//
	// @Override
	// public Transaction lock(boolean write, Object cause) {
	// return Transaction.NONE;
	// }
	//
	// @Override
	// public Transaction tryLock(boolean write, Object cause) {
	// return Transaction.NONE;
	// }
	//
	// @Override
	// public ObservableValue<String> isEnabled() {
	// return SettableValue.ALWAYS_DISABLED;
	// }
	//
	// @Override
	// public String isAcceptable(Instant value) {
	// return StdMsg.UNSUPPORTED_OPERATION;
	// }
	//
	// @Override
	// public Instant set(Instant value, Object cause) throws IllegalArgumentException, UnsupportedOperationException {
	// throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
	// }
	// });
	// }
	// ModelValueSynth<SettableValue<?>, SettableValue<Instant>> relativeTo2 = relativeTo;
	// return new ObservableModelSet.AbstractValueSynth<SettableValue<?>, SettableValue<Format<Instant>>>(formatInstanceType) {
	// @Override
	// public SettableValue<Format<Instant>> get(ModelSetInstance models) throws ModelInstantiationException {
	// return ObservableModelSet.literal(SpinnerFormat.flexDate(relativeTo2.get(models), dayFormat, __ -> fOptions),
	// "instant");
	// }
	//
	// @Override
	// public SettableValue<Format<Instant>> forModelCopy(SettableValue<Format<Instant>> value, ModelSetInstance sourceModels,
	// ModelSetInstance newModels) throws ModelInstantiationException {
	// return ObservableModelSet.literal(SpinnerFormat.flexDate(relativeTo2.get(newModels), dayFormat, __ -> fOptions),
	// "instant");
	// }
	//
	// @Override
	// public List<? extends ModelValueSynth<?, ?>> getComponents() {
	// return Collections.emptyList();
	// }
	// };
	// });
	// }
	//
	// private CompiledModelValue<SettableValue<?>> createFileFormat(ExpressoQIS session)
	// throws QonfigInterpretationException {
	// ExpressoQIS fileSourceSession = session.forChildren("file-source").peekFirst();
	// CompiledModelValue<SettableValue<?>, SettableValue<BetterFile.FileDataSource>> fileSourceCreator;
	// if (fileSourceSession == null)
	// fileSourceCreator = CompiledModelValue.literal(TypeTokens.get().of(BetterFile.FileDataSource.class), new NativeFileSource(),
	// "native-file-source");
	// else
	// fileSourceCreator = fileSourceSession.interpret(CompiledModelValue.class);
	// String workingDir = session.getAttributeText("working-dir");
	// boolean allowEmpty = session.getAttribute("allow-empty", boolean.class);
	// LocatedFilePosition position;
	// if (fileSourceSession != null)
	// position = fileSourceSession.getElement().getPositionInFile();
	// else
	// position = session.getElement().getPositionInFile();
	// return CompiledModelValue.of(session.getElement().getType().getName(), ModelTypes.Value, () -> {
	// ModelValueSynth<SettableValue<?>, SettableValue<BetterFile.FileDataSource>> fileSource;
	// try {
	// fileSource = fileSourceCreator.createSynthesizer().as(ModelTypes.Value.forType(BetterFile.FileDataSource.class));
	// } catch (TypeConversionException e) {
	// throw new ExpressoInterpretationException("Could not interpret " + fileSourceCreator + " as a file source", position, 0, e);
	// }
	//
	// TypeToken<Format<BetterFile>> fileFormatType = TypeTokens.get().keyFor(Format.class)
	// .<Format<BetterFile>> parameterized(BetterFile.class);
	// return new ObservableModelSet.AbstractValueSynth<SettableValue<?>, SettableValue<Format<BetterFile>>>(
	// ModelTypes.Value.forType(fileFormatType)) {
	// @Override
	// public SettableValue<Format<BetterFile>> get(ModelSetInstance models) throws ModelInstantiationException {
	// SettableValue<BetterFile.FileDataSource> fds = fileSource.get(models);
	// return createFileFormat(fds);
	// }
	//
	// @Override
	// public SettableValue<Format<BetterFile>> forModelCopy(SettableValue<Format<BetterFile>> value,
	// ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
	// SettableValue<BetterFile.FileDataSource> sourceFDS = fileSource.get(sourceModels);
	// SettableValue<BetterFile.FileDataSource> newFDS = fileSource.forModelCopy(sourceFDS, sourceModels, newModels);
	// if (sourceFDS == newFDS)
	// return value;
	// else
	// return createFileFormat(newFDS);
	// }
	//
	// private SettableValue<Format<BetterFile>> createFileFormat(SettableValue<FileDataSource> fds) {
	// return SettableValue.asSettable(//
	// fds.transform(fileFormatType, tx -> tx.map(fs -> {
	// BetterFile workingDirFile = BetterFile.at(fs, workingDir);
	// return new BetterFile.FileFormat(fs, workingDirFile, allowEmpty);
	// })), //
	// __ -> "Not reversible");
	// }
	//
	// @Override
	// public List<? extends ModelValueSynth<?, ?>> getComponents() {
	// return Collections.singletonList(fileSource);
	// }
	// };
	// });
	// }
	//
	// private ValidationProducer createRegexValidation(ExpressoQIS session) throws QonfigInterpretationException {
	// CompiledExpression patternX = session.getAttributeExpression("pattern");
	// return new ValidationProducer() {
	// @Override
	// public <T> Validation<T> createValidator(TypeToken<T> formatType) throws ExpressoInterpretationException {
	// ModelValueSynth<SettableValue<?>, SettableValue<Pattern>> patternC = patternX
	// .evaluate(ModelTypes.Value.forType(Pattern.class));
	// class PatternValidator implements Function<T, String> {
	// final SettableValue<Pattern> patternV;
	//
	// PatternValidator(SettableValue<Pattern> patternV) {
	// this.patternV = patternV;
	// }
	//
	// @Override
	// public String apply(T v) {
	// if (v == null)
	// return null;
	// Pattern pattern = patternV.get();
	// if (pattern == null)
	// return null;
	// else if (pattern.matcher(v.toString()).matches())
	// return null;
	// else
	// return "Value must match " + pattern.pattern();
	// }
	// }
	// return new Validation<T>() {
	// @Override
	// public Function<T, String> getTest(ModelSetInstance models) throws ModelInstantiationException {
	// return new PatternValidator(patternC.get(models));
	// }
	//
	// @Override
	// public Function<T, String> forModelCopy(Function<T, String> validator, ModelSetInstance sourceModels,
	// ModelSetInstance newModels) throws ModelInstantiationException {
	// SettableValue<Pattern> oldPattern = ((PatternValidator) validator).patternV;
	// SettableValue<Pattern> newPattern = patternC.forModelCopy(oldPattern, sourceModels, newModels);
	// if (newPattern == oldPattern)
	// return validator;
	// return new PatternValidator(newPattern);
	// }
	//
	// @Override
	// public List<ModelValueSynth<?, ?>> getComponents() {
	// return Collections.singletonList(patternC);
	// }
	// };
	// }
	// };
	// }
	//
	// private ValidationProducer createFilterValidation(ExpressoQIS session) throws QonfigInterpretationException {
	// String filterValue = session.getAttributeText("filter-value-name");
	// CompiledExpression testX = session.getAttributeExpression("test");
	// InterpretedExpressoEnv env = session.getExpressoEnv();
	// LocatedFilePosition position = session.getElement().getPositionInFile();
	// return new ValidationProducer() {
	// @Override
	// public <T> Validation<T> createValidator(TypeToken<T> formatType) throws ExpressoInterpretationException {
	// ModelInstanceType<SettableValue<?>, SettableValue<T>> type = ModelTypes.Value.forType(formatType);
	// ElementModelValue.satisfyElementValueType(filterValue, env.getModels(), type);
	// ModelValueSynth<SettableValue<?>, SettableValue<String>> filter;
	// try {
	// filter = testX.evaluate(ModelTypes.Value.STRING);
	// } catch (ExpressoInterpretationException e) {
	// ModelValueSynth<SettableValue<?>, SettableValue<Boolean>> bFilter;
	// try {
	// bFilter = testX.evaluate(ModelTypes.Value.BOOLEAN);
	// filter = bFilter.map(ModelTypes.Value.STRING, bv -> SettableValue.asSettable(//
	// bv.map(String.class, b -> b ? null : "Filter does not pass"), __ -> "Not Settable"));
	// } catch (ExpressoInterpretationException e2) {
	// throw new ExpressoInterpretationException("Could not evaluate filter test", e2.getPosition(), e2.getErrorLength(),
	// e2);
	// }
	// }
	// class FilterValidator implements Function<T, String> {
	// final SettableValue<T> value;
	// final SettableValue<String> testValue;
	//
	// public FilterValidator(SettableValue<T> value, SettableValue<String> testValue) {
	// this.value = value;
	// this.testValue = testValue;
	// }
	//
	// @Override
	// public String apply(T t) {
	// value.set(t, null);
	// return testValue.get();
	// }
	// }
	// ModelValueSynth<SettableValue<?>, SettableValue<String>> fFilter = filter;
	// env.interpretLocalModel();
	// return new Validation<T>() {
	// @Override
	// public Function<T, String> getTest(ModelSetInstance models) throws ModelInstantiationException {
	// models = env.wrapLocal(models);
	// SettableValue<T> value = SettableValue.build((TypeToken<T>) type.getType(0)).build();
	// try {
	// DynamicModelValue.satisfyDynamicValue(filterValue, type, models, value);
	// } catch (ModelException | TypeConversionException e) {
	// throw new ModelInstantiationException("Could not satisfy " + filterValue, position, 0, e);
	// }
	// SettableValue<String> testValue = fFilter.get(models);
	// return new FilterValidator(value, testValue);
	// }
	//
	// @Override
	// public Function<T, String> forModelCopy(Function<T, String> validator, ModelSetInstance sourceModels,
	// ModelSetInstance newModels) throws ModelInstantiationException {
	// SettableValue<String> oldTest = ((FilterValidator) validator).testValue;
	// SettableValue<String> newTest = fFilter.forModelCopy(oldTest, sourceModels, newModels);
	// if (oldTest == newTest)
	// return validator;
	// return new FilterValidator(((FilterValidator) validator).value, newTest);
	// }
	//
	// @Override
	// public List<ModelValueSynth<?, ?>> getComponents() {
	// return Collections.singletonList(fFilter);
	// }
	// };
	// }
	// };
	// }
}
