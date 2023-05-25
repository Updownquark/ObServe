package org.observe.expresso;

import static org.observe.expresso.ExpressoBaseV0_1.KEY_TYPE_KEY;
import static org.observe.expresso.ExpressoBaseV0_1.VALUE_TYPE_KEY;

import java.awt.Image;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.JFrame;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.SettableValue;
import org.observe.Transformation;
import org.observe.assoc.ObservableMap;
import org.observe.assoc.ObservableMultiMap;
import org.observe.assoc.ObservableSortedMap;
import org.observe.assoc.ObservableSortedMultiMap;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableSet;
import org.observe.collect.ObservableSortedCollection;
import org.observe.collect.ObservableSortedSet;
import org.observe.config.ObservableConfig;
import org.observe.config.ObservableConfig.ObservableConfigMapBuilder;
import org.observe.config.ObservableConfig.ObservableConfigPersistence;
import org.observe.config.ObservableConfig.ObservableConfigValueBuilder;
import org.observe.config.ObservableConfigFormat;
import org.observe.config.ObservableConfigPath;
import org.observe.config.ObservableValueSet;
import org.observe.config.SyncValueSet;
import org.observe.expresso.Expresso.ConfigModelValue;
import org.observe.expresso.ExpressoBaseV0_1.AppEnvironment;
import org.observe.expresso.ExpressoBaseV0_1.ParsedSorting;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ObservableModelSet.CompiledModelValue;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueSynth;
import org.observe.util.TypeTokens;
import org.observe.util.swing.WindowPopulation;
import org.qommons.Identifiable;
import org.qommons.ThreadConstraint;
import org.qommons.TimeUtils;
import org.qommons.TimeUtils.TimeEvaluationOptions;
import org.qommons.Transaction;
import org.qommons.Version;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.config.QommonsConfig;
import org.qommons.config.QonfigAttributeDef;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigElement.QonfigValue;
import org.qommons.config.QonfigInterpretation;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreterCore;
import org.qommons.config.QonfigInterpreterCore.CoreSession;
import org.qommons.config.QonfigInterpreterCore.QonfigValueCreator;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.SpecialSession;
import org.qommons.io.ArchiveEnabledFileSource;
import org.qommons.io.ArchiveEnabledFileSource.FileArchival;
import org.qommons.io.BetterFile;
import org.qommons.io.BetterFile.FileDataSource;
import org.qommons.io.ErrorReporting;
import org.qommons.io.FileBackups;
import org.qommons.io.Format;
import org.qommons.io.LocatedFilePosition;
import org.qommons.io.NativeFileSource;
import org.qommons.io.SftpFileSource;
import org.qommons.io.SpinnerFormat;
import org.qommons.io.TextParseException;
import org.qommons.threading.QommonsTimer;

import com.google.common.reflect.TypeToken;

/** Qonfig Interpretation for the ExpressoConfigV0_1 API */
public class ExpressoConfigV0_1 implements QonfigInterpretation {
	/** Equivalent of a {@link CompiledModelValue} for a &lt;validate> element */
	public interface ValidationProducer {
		/**
		 * @param <T> The type to validate
		 * @param formatType The type to validate
		 * @return A Validation to create the actual validator
		 * @throws ExpressoInterpretationException If the validator cannot be created
		 */
		<T> Validation<T> createValidator(TypeToken<T> formatType) throws ExpressoInterpretationException;
	}

	/**
	 * Equivalent of a {@link ModelValueSynth} for a &lt;validate> element
	 *
	 * @param <V> The type to validate
	 */
	public interface Validation<V> {
		/**
		 * @param models The model instance for the test to use
		 * @return The actual test
		 * @throws ModelInstantiationException If the test cannot be created
		 */
		Function<V, String> getTest(ModelSetInstance models) throws ModelInstantiationException;

		/**
		 * Equivalent of {@link ModelValueSynth#forModelCopy(Object, ModelSetInstance, ModelSetInstance)}
		 *
		 * @param validator The validator from the source models
		 * @param sourceModels The source models
		 * @param newModels The new models
		 * @return The validator for the new models
		 * @throws ModelInstantiationException If the new test cannot be created
		 */
		Function<V, String> forModelCopy(Function<V, String> validator, ModelSetInstance sourceModels, ModelSetInstance newModels)
			throws ModelInstantiationException;

		/**
		 * Equivalent of {@link ModelValueSynth#getCores()}
		 *
		 * @return The value container cores that are the fundamental sources of value for the test
		 * @throws ExpressoInterpretationException If an error occurs retrieving the cores
		 */
		BetterList<ModelValueSynth<?, ?>> getCores() throws ExpressoInterpretationException;
	}

	/** The name of the model value to store the {@link ObservableConfig} in the model */
	public static final String CONFIG_NAME = "$CONFIG$";

	@Override
	public Set<Class<? extends SpecialSession<?>>> getExpectedAPIs() {
		return Collections.singleton(ExpressoQIS.class);
	}

	@Override
	public String getToolkitName() {
		return "Expresso-Config";
	}

	@Override
	public Version getVersion() {
		return ExpressoSessionImplV0_1.VERSION;
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

	static ExpressoQIS wrap(CoreSession session) throws QonfigInterpretationException {
		return session.as(ExpressoQIS.class);
	}

	void configureConfigModels(QonfigInterpreterCore.Builder interpreter) {
		interpreter.createWith("config", ObservableModelSet.class, new ConfigModelCreator());
		interpreter.createWith("value", Expresso.ConfigModelValue.class, session -> createConfigValue(wrap(session)));
		interpreter.createWith("value-set", Expresso.ConfigModelValue.class, valueSetCreator());
		interpreter.createWith("list", Expresso.ConfigModelValue.class, collectionCreator());
		interpreter.createWith("sorted-list", Expresso.ConfigModelValue.class, sortedCollectionCreator());
		interpreter.createWith("set", Expresso.ConfigModelValue.class, setCreator());
		interpreter.createWith("sorted-set", Expresso.ConfigModelValue.class, sortedSetCreator());
		interpreter.createWith("map", Expresso.ConfigModelValue.class, mapCreator());
		interpreter.createWith("sorted-map", Expresso.ConfigModelValue.class, sortedMapCreator());
		interpreter.createWith("multi-map", Expresso.ConfigModelValue.class, multiMapCreator());
		interpreter.createWith("sorted-multi-map", Expresso.ConfigModelValue.class, sortedMultiMapCreator());
	}

	static class ConfigModelCreator implements QonfigInterpreterCore.QonfigValueCreator<ObservableModelSet> {
		@Override
		public ObservableModelSet createValue(CoreSession session) throws QonfigInterpretationException {
			ExpressoQIS eqis = wrap(session);
			ObservableModelSet.Builder model = (ObservableModelSet.Builder) eqis.getExpressoEnv().getModels();
			String configName = session.getAttributeText("config-name");
			CompiledExpression configDirX = eqis.getAttributeExpression("config-dir");
			List<String> oldConfigNames = new ArrayList<>(2);
			for (QonfigElement ch : session.getChildren("old-config-name"))
				oldConfigNames.add(ch.getValueText());

			boolean backup = session.getAttribute("backup", boolean.class);

			List<ConfigModelValue<?, ?, ?>> values = eqis.interpretChildren("value", ConfigModelValue.class);

			model.withMaker(CONFIG_NAME, new CompiledModelValue<SettableValue<?>, SettableValue<ObservableConfig>>() {
				private final ModelInstanceType<SettableValue<?>, SettableValue<ObservableConfig>> theType = ModelTypes.Value
					.forType(ObservableConfig.class);

				@Override
				public ModelType<SettableValue<?>> getModelType() {
					return ModelTypes.Value;
				}

				@Override
				public ModelValueSynth<SettableValue<?>, SettableValue<ObservableConfig>> createSynthesizer()
					throws ExpressoInterpretationException {
					ModelValueSynth<SettableValue<?>, SettableValue<BetterFile>> configDir;
					if (configDirX != null)
						configDir = configDirX.evaluate(ModelTypes.Value.forType(BetterFile.class));
					else {
						configDir = ModelValueSynth.of(ModelTypes.Value.forType(BetterFile.class), msi -> {
							String prop = System.getProperty(configName + ".config");
							if (prop != null)
								return ObservableModelSet.literal(BetterFile.at(new NativeFileSource(), prop), prop);
							else
								return ObservableModelSet.literal(BetterFile.at(new NativeFileSource(), "./" + configName),
									"./" + configName);
						});
					}

					return ModelValueSynth.of(theType, msi -> {
						BetterFile configDirFile = configDir == null ? null : configDir.get(msi).get();
						if (configDirFile == null) {
							String configProp = System.getProperty(configName + ".config");
							if (configProp != null)
								configDirFile = BetterFile.at(new NativeFileSource(), configProp);
							else
								configDirFile = BetterFile.at(new NativeFileSource(), "./" + configName);
						}
						if (!configDirFile.exists()) {
							try {
								configDirFile.create(true);
							} catch (IOException e) {
								throw new IllegalStateException("Could not create config directory " + configDirFile.getPath(), e);
							}
						} else if (!configDirFile.isDirectory())
							throw new IllegalStateException("Not a directory: " + configDirFile.getPath());

						BetterFile configFile = configDirFile.at(configName + ".xml");
						if (!configFile.exists()) {
							BetterFile oldConfigFile = configDirFile.getParent().at(configName + ".config");
							if (oldConfigFile.exists()) {
								try {
									oldConfigFile.move(configFile);
								} catch (IOException e) {
									System.err.println(
										"Could not move old configuration " + oldConfigFile.getPath() + " to " + configFile.getPath());
									e.printStackTrace();
								}
							}
						}

						FileBackups backups = backup ? new FileBackups(configFile) : null;

						if (!configFile.exists() && oldConfigNames != null) {
							boolean found = false;
							for (String oldConfigName : oldConfigNames) {
								BetterFile oldConfigFile = configDirFile.at(oldConfigName);
								if (oldConfigFile.exists()) {
									try {
										oldConfigFile.move(configFile);
									} catch (IOException e) {
										System.err.println("Could not rename " + oldConfigFile.getPath() + " to " + configFile.getPath());
										e.printStackTrace();
									}
									if (backups != null)
										backups.renamedFrom(oldConfigFile);
									found = true;
									break;
								}
								if (!found) {
									oldConfigFile = configDirFile.getParent().at(oldConfigName + "/" + oldConfigName + ".xml");
									if (oldConfigFile.exists()) {
										try {
											oldConfigFile.move(configFile);
										} catch (IOException e) {
											System.err
											.println("Could not rename " + oldConfigFile.getPath() + " to " + configFile.getPath());
											e.printStackTrace();
										}
										if (backups != null)
											backups.renamedFrom(oldConfigFile);
										found = true;
										break;
									}
								}
							}
						}
						ObservableConfig config = ObservableConfig.createRoot(configName, ThreadConstraint.EDT);
						ObservableConfig.XmlEncoding encoding = ObservableConfig.XmlEncoding.DEFAULT;
						boolean loaded = false;
						if (configFile.exists()) {
							try {
								try (InputStream configStream = new BufferedInputStream(configFile.read())) {
									ObservableConfig.readXml(config, configStream, encoding);
								}
								config.setName(configName);
								loaded = true;
							} catch (IOException | TextParseException e) {
								System.out.println("Could not read config file " + configFile.getPath());
								e.printStackTrace(System.out);
							}
						}
						boolean[] closingWithoutSave = new boolean[1];
						AppEnvironment storedApp = session.get(ExpressoBaseV0_1.APP_ENVIRONMENT_KEY, AppEnvironment.class);
						AppEnvironment app = storedApp != null ? storedApp : new AppEnvironment() {
							@Override
							public ModelValueSynth<SettableValue<?>, ? extends ObservableValue<String>> getTitle() {
								return ModelValueSynth.literal(TypeTokens.get().STRING, "Unspecified Application",
									"Unspecified Application");
							}

							@Override
							public ModelValueSynth<SettableValue<?>, ? extends ObservableValue<Image>> getIcon() {
								return ModelValueSynth.literal(TypeTokens.get().of(Image.class), null, "No Image");
							}
						};
						if (loaded)
							build2(config, configFile, backups, closingWithoutSave);
						else if (backups != null && !backups.getBackups().isEmpty()) {
							restoreBackup(true, config, backups, () -> {
								config.setName(configName);
								build2(config, configFile, backups, closingWithoutSave);
							}, () -> {
								config.setName(configName);
								build2(config, configFile, backups, closingWithoutSave);
							}, app, closingWithoutSave, msi);
						} else {
							config.setName(configName);
							build2(config, configFile, backups, closingWithoutSave);
						}
						return SettableValue.of(ObservableConfig.class, config, "Not Settable");
					});
				}
			});
			ModelValueSynth<SettableValue<?>, SettableValue<ObservableConfig>> configV;
			try {
				configV = (ModelValueSynth<SettableValue<?>, SettableValue<ObservableConfig>>) model.getComponent(CONFIG_NAME);
			} catch (ModelException e) {
				throw new QonfigInterpretationException("But we just installed it!", session.getElement().getPositionInFile(), 0, e);
			}

			int mvi = 0;
			for (ExpressoQIS child : eqis.forChildren("value")) {
				String name = child.getAttributeText("model-element", "name");
				String path = child.getAttributeText("config-path");
				if (path == null)
					path = name;
				ConfigModelValue<?, ?, ?> mv = values.get(mvi);
				model.withMaker(name, createConfigValue(mv, configV, path, child));
				mvi++;
			}
			return model;
		}

		private <T, M, MV extends M> CompiledModelValue<M, MV> createConfigValue(ConfigModelValue<T, M, MV> configValue,
			ModelValueSynth<SettableValue<?>, SettableValue<ObservableConfig>> configV, String path, ExpressoQIS session)
				throws QonfigInterpretationException {
			ExpressoQIS formatSession = session.forChildren("format").peekFirst();
			CompiledModelValue<?, ?> formatCreator = formatSession == null ? null : formatSession.interpret(CompiledModelValue.class);
			return CompiledModelValue.of("value", configValue.getType().getModelType(), () -> {
				configValue.init();
				InterpretedValueSynth<SettableValue<?>, SettableValue<ObservableConfig>> iConfigV = configV.interpret();
				TypeToken<T> formatType = (TypeToken<T>) configValue.getType()
					.getType(configValue.getType().getModelType().getTypeCount() - 1);
				ModelValueSynth<SettableValue<?>, SettableValue<ObservableConfigFormat<T>>> formatContainer;
				ObservableConfigFormat<T> defaultFormat;
				if (formatCreator != null) {
					try {
						formatContainer = formatCreator.createSynthesizer().as(ModelTypes.Value.forType(
							TypeTokens.get().keyFor(ObservableConfigFormat.class).<ObservableConfigFormat<T>> parameterized(formatType)));
					} catch (TypeConversionException e) {
						LocatedFilePosition position;
						if (formatSession != null)
							position = formatSession.getElement().getPositionInFile();
						else
							position = session.getElement().getPositionInFile();
						throw new ExpressoInterpretationException("Could not evaluate " + formatCreator + " as a config format", position,
							0, e);
					}
					defaultFormat = null;
				} else {
					formatContainer = null;
					defaultFormat = getDefaultConfigFormat(formatType);
					if (defaultFormat == null)
						throw new ExpressoInterpretationException("No default config format available for type " + formatType,
							session.getElement().getPositionInFile(), 0);
				}
				return new ModelValueSynth<M, MV>() {
					@Override
					public ModelType<M> getModelType() {
						return configValue.getType().getModelType();
					}

					@Override
					public ModelInstanceType<M, MV> getType() throws ExpressoInterpretationException {
						return configValue.getType();
					}

					@Override
					public MV get(ModelSetInstance msi) throws ModelInstantiationException, IllegalStateException {
						ObservableConfig config = iConfigV.get(msi).get();
						ObservableConfig.ObservableConfigValueBuilder<T> builder = config//
							.asValue(formatType).at(path)//
							.until(msi.getUntil());
						if (formatContainer != null)
							builder.withFormat(formatContainer.get(msi).get());
						else
							builder.withFormat(defaultFormat);
						return configValue.create(builder, msi);
					}

					@Override
					public MV forModelCopy(MV value, ModelSetInstance sourceModels, ModelSetInstance newModels) {
						// Should be the same thing, since the config hasn't changed
						return value;
					}

					@Override
					public BetterList<ModelValueSynth<?, ?>> getCores() {
						return BetterList.of(this);
					}
				};
			});
		}

		private void build2(ObservableConfig config, BetterFile configFile, FileBackups backups, boolean[] closingWithoutSave) {
			if (configFile != null) {
				ObservableConfigPersistence<IOException> actuallyPersist = ObservableConfig.toFile(configFile,
					ObservableConfig.XmlEncoding.DEFAULT);
				boolean[] persistenceQueued = new boolean[1];
				ObservableConfigPersistence<IOException> persist = new ObservableConfig.ObservableConfigPersistence<IOException>() {
					@Override
					public void persist(ObservableConfig config2) throws IOException {
						try {
							if (persistenceQueued[0] && !closingWithoutSave[0]) {
								actuallyPersist.persist(config2);
								if (backups != null)
									backups.fileChanged();
							}
						} finally {
							persistenceQueued[0] = false;
						}
					}
				};
				config.persistOnShutdown(persist, ex -> {
					System.err.println("Could not persist UI config");
					ex.printStackTrace();
				});
				QommonsTimer timer = QommonsTimer.getCommonInstance();
				Object key = new Object() {
					@Override
					public String toString() {
						return config.getName() + " persistence";
					}
				};
				Duration persistDelay = Duration.ofSeconds(2);
				config.watch(ObservableConfigPath.buildPath(ObservableConfigPath.ANY_NAME).multi(true).build()).act(evt -> {
					if (evt.changeType == CollectionChangeType.add && evt.getChangeTarget().isTrivial())
						return;
					persistenceQueued[0] = true;
					timer.doAfterInactivity(key, () -> {
						try {
							persist.persist(config);
						} catch (IOException ex) {
							System.err.println("Could not persist UI config");
							ex.printStackTrace();
						}
					}, persistDelay);
				});
			}
		}
	}

	private <T> ConfigModelValue<T, SettableValue<?>, SettableValue<T>> createConfigValue(ExpressoQIS session)
		throws QonfigInterpretationException {
		CompiledExpression defaultX = session.asElement("config-value").getAttributeExpression("default");
		VariableType type = (VariableType) session.get(VALUE_TYPE_KEY);
		ExpressoEnv env = session.getExpressoEnv();
		return new ConfigModelValue<T, SettableValue<?>, SettableValue<T>>() {
			private TypeToken<T> theValueType;
			private Format<T> theTextFormat;
			private ModelValueSynth<SettableValue<?>, SettableValue<T>> defaultV;

			@Override
			public void init() throws ExpressoInterpretationException {
				theValueType = (TypeToken<T>) type.getType(env.getModels());
				if (defaultX != null) {
					// If the format is a simple text format, add the ability to parse the default value with it as an external literal
					NonStructuredParser nsp = theTextFormat == null ? null : new NonStructuredParser() {
						@Override
						public boolean canParse(TypeToken<?> type2, String text) {
							return theTextFormat != null;
						}

						@Override
						public <T2> ObservableValue<? extends T2> parse(TypeToken<T2> type2, String text) throws ParseException {
							return ObservableValue.of(type2, (T2) theTextFormat.parse(text));
						}
					};
					Class<T> raw = TypeTokens.getRawType(theValueType);
					env.withNonStructuredParser(raw, nsp);
					try {
						defaultV = defaultX == null ? null : defaultX.evaluate(ModelTypes.Value.forType(theValueType));
					} finally {
						env.removeNonStructuredParser(raw, nsp);
					}
				} else
					defaultV = null;
			}

			@Override
			public ModelInstanceType<SettableValue<?>, SettableValue<T>> getType() {
				return ModelTypes.Value.forType(theValueType);
			}

			@Override
			public SettableValue<T> create(ObservableConfigValueBuilder<T> config, ModelSetInstance msi)
				throws ModelInstantiationException {
				SettableValue<T> built = config.buildValue(null);
				if (defaultV != null && config.getConfig().getChild(config.getPath(), false, null) == null) {
					if (config.getFormat() instanceof ObservableConfigFormat.Impl.SimpleConfigFormat)
						theTextFormat = ((ObservableConfigFormat.Impl.SimpleConfigFormat<T>) config.getFormat()).format;
					built.set(defaultV.get(msi).get(), null);
					theTextFormat = null;
				}
				return built;
			}
		};
	}

	abstract class ConfigValueCreator<T, M, MV extends M> implements QonfigValueCreator<Expresso.ConfigModelValue<T, M, MV>> {
		private final ModelType<M> theModelType;

		public ConfigValueCreator(ModelType<M> modelType) {
			theModelType = modelType;
		}

		@Override
		public ConfigModelValue<T, M, MV> createValue(CoreSession session) throws QonfigInterpretationException {
			ExpressoQIS exS = session.as(ExpressoQIS.class);
			VariableType type = (VariableType) exS.get(VALUE_TYPE_KEY);
			prepare(exS);
			ExpressoEnv env = exS.getExpressoEnv();
			return new ConfigModelValue<T, M, MV>() {
				private ModelInstanceType<M, MV> theInstanceType;

				@Override
				public void init() throws ExpressoInterpretationException {
					TypeToken<T> valueType = (TypeToken<T>) type.getType(env.getModels());
					theInstanceType = (ModelInstanceType<M, MV>) theModelType.forTypes(valueType);
					prepare(env, theInstanceType);
				}

				@Override
				public ModelInstanceType<M, MV> getType() {
					return theInstanceType;
				}

				@Override
				public MV create(ObservableConfigValueBuilder<T> config, ModelSetInstance msi) throws ModelInstantiationException {
					return ConfigValueCreator.this.create(config, msi);
				}
			};
		}

		protected abstract void prepare(ExpressoQIS exS) throws QonfigInterpretationException;

		protected abstract void prepare(ExpressoEnv env, ModelInstanceType<M, MV> instanceType) throws ExpressoInterpretationException;

		protected abstract MV create(ObservableConfigValueBuilder<T> builder, ModelSetInstance msi) throws ModelInstantiationException;
	}

	abstract class ConfigMapCreator<K, V, M, MV extends M> implements QonfigValueCreator<Expresso.ConfigModelValue<V, M, MV>> {
		private final ModelType<M> theModelType;
		CompiledModelValue<?, ?> keyFormatCreator;
		ModelValueSynth<SettableValue<?>, SettableValue<ObservableConfigFormat<K>>> keyFormatContainer;
		ObservableConfigFormat<K> defaultKeyFormat;

		public ConfigMapCreator(ModelType<M> modelType) {
			theModelType = modelType;
		}

		@Override
		public ConfigModelValue<V, M, MV> createValue(CoreSession session) throws QonfigInterpretationException {
			ExpressoQIS exS = session.as(ExpressoQIS.class);
			ExpressoQIS formatSession = exS.forChildren("key-format").peekFirst();
			keyFormatCreator = formatSession == null ? null : formatSession.interpret(CompiledModelValue.class);

			VariableType vblKeyType = (VariableType) exS.get(KEY_TYPE_KEY);
			VariableType vblValueype = (VariableType) exS.get(VALUE_TYPE_KEY);
			prepare(exS);
			return new ConfigModelValue<V, M, MV>() {
				private ModelInstanceType<M, MV> theInstanceType;

				@Override
				public void init() throws ExpressoInterpretationException {
					TypeToken<K> keyType = (TypeToken<K>) vblKeyType.getType(exS.getExpressoEnv().getModels());
					TypeToken<V> valueType = (TypeToken<V>) vblValueype.getType(exS.getExpressoEnv().getModels());
					theInstanceType = (ModelInstanceType<M, MV>) theModelType.forTypes(keyType, valueType);

					if (keyFormatCreator != null) {
						try {
							keyFormatContainer = keyFormatCreator.createSynthesizer().as(ModelTypes.Value.forType(
								TypeTokens.get().keyFor(ObservableConfigFormat.class).<ObservableConfigFormat<K>> parameterized(keyType)));
						} catch (TypeConversionException e) {
							LocatedFilePosition position;
							if (formatSession != null)
								position = formatSession.getElement().getPositionInFile();
							else
								position = session.getElement().getPositionInFile();
							throw new ExpressoInterpretationException("Could not evaluate " + keyFormatCreator + " as a config format",
								position, 0, e);
						}
						defaultKeyFormat = null;
					} else {
						keyFormatContainer = null;
						defaultKeyFormat = getDefaultConfigFormat(keyType);
						if (defaultKeyFormat == null)
							throw new ExpressoInterpretationException("No default config format available for key type " + keyType,
								session.getElement().getPositionInFile(), 0);
					}

					prepare(exS, theInstanceType);
				}

				@Override
				public ModelInstanceType<M, MV> getType() {
					return theInstanceType;
				}

				@Override
				public MV create(ObservableConfigValueBuilder<V> config, ModelSetInstance msi) throws ModelInstantiationException {
					ObservableConfigMapBuilder<K, V> mapBuilder = config.asMap((TypeToken<K>) theInstanceType.getType(0));
					if (keyFormatContainer != null)
						mapBuilder.withKeyFormat(keyFormatContainer.get(msi).get());
					else
						mapBuilder.withKeyFormat(defaultKeyFormat);
					return ConfigMapCreator.this.create(mapBuilder, msi);
				}
			};
		}

		protected abstract void prepare(ExpressoQIS exS) throws QonfigInterpretationException;

		protected abstract void prepare(ExpressoQIS exS, ModelInstanceType<M, MV> instanceType) throws ExpressoInterpretationException;

		protected abstract MV create(ObservableConfigMapBuilder<K, V> builder, ModelSetInstance msi) throws ModelInstantiationException;
	}

	private <T> ConfigValueCreator<T, ObservableValueSet<?>, ObservableValueSet<T>> valueSetCreator() {
		return new ConfigValueCreator<T, ObservableValueSet<?>, ObservableValueSet<T>>(ModelTypes.ValueSet) {
			@Override
			protected void prepare(ExpressoQIS exS) throws QonfigInterpretationException {
			}

			@Override
			protected void prepare(ExpressoEnv env, ModelInstanceType<ObservableValueSet<?>, ObservableValueSet<T>> instanceType)
				throws ExpressoInterpretationException {
			}

			@Override
			protected ObservableValueSet<T> create(ObservableConfigValueBuilder<T> builder, ModelSetInstance msi)
				throws ModelInstantiationException {
				return builder.buildEntitySet(null);
			}
		};
	}

	private <T> ConfigValueCreator<T, ObservableCollection<?>, ObservableCollection<T>> collectionCreator() {
		return new ConfigValueCreator<T, ObservableCollection<?>, ObservableCollection<T>>(ModelTypes.Collection) {
			@Override
			protected void prepare(ExpressoQIS exS) throws QonfigInterpretationException {
			}

			@Override
			protected void prepare(ExpressoEnv env, ModelInstanceType<ObservableCollection<?>, ObservableCollection<T>> instanceType)
				throws ExpressoInterpretationException {
			}

			@Override
			protected ObservableCollection<T> create(ObservableConfigValueBuilder<T> builder, ModelSetInstance msi)
				throws ModelInstantiationException {
				return builder.buildCollection(null);
			}
		};
	}

	private <T> ConfigValueCreator<T, ObservableSortedCollection<?>, ObservableSortedCollection<T>> sortedCollectionCreator() {
		return new ConfigValueCreator<T, ObservableSortedCollection<?>, ObservableSortedCollection<T>>(ModelTypes.SortedCollection) {
			private ParsedSorting theSortingCreator;
			private ModelValueSynth<SettableValue<?>, SettableValue<Comparator<T>>> theSortingContainer;

			@Override
			protected void prepare(ExpressoQIS exS) throws QonfigInterpretationException {
				ExpressoQIS sorting = exS.forChildren("sort").peekFirst();
				if (sorting != null)
					theSortingCreator = sorting.interpret(ParsedSorting.class);
				else
					theSortingCreator = ExpressoBaseV0_1.getDefaultSorting(exS.getElement().getPositionInFile());
			}

			@Override
			protected void prepare(ExpressoEnv env,
				ModelInstanceType<ObservableSortedCollection<?>, ObservableSortedCollection<T>> instanceType)
					throws ExpressoInterpretationException {
				theSortingContainer = theSortingCreator.evaluate((TypeToken<T>) instanceType.getType(0));
			}

			@Override
			protected ObservableSortedCollection<T> create(ObservableConfigValueBuilder<T> builder, ModelSetInstance msi)
				throws ModelInstantiationException {
				Comparator<T> sorting = theSortingContainer.get(msi).get();
				return builder.buildCollection(null).flow().sorted(sorting).collectActive(msi.getUntil());
			}
		};
	}

	private <T> ConfigValueCreator<T, ObservableSet<?>, ObservableSet<T>> setCreator() {
		return new ConfigValueCreator<T, ObservableSet<?>, ObservableSet<T>>(ModelTypes.Set) {
			@Override
			protected void prepare(ExpressoQIS exS) throws QonfigInterpretationException {
			}

			@Override
			protected void prepare(ExpressoEnv env, ModelInstanceType<ObservableSet<?>, ObservableSet<T>> instanceType)
				throws ExpressoInterpretationException {
			}

			@Override
			protected ObservableSet<T> create(ObservableConfigValueBuilder<T> builder, ModelSetInstance msi)
				throws ModelInstantiationException {
				return builder.buildCollection(null).flow().distinct().collectActive(msi.getUntil());
			}
		};
	}

	private <T> ConfigValueCreator<T, ObservableSortedSet<?>, ObservableSortedSet<T>> sortedSetCreator() {
		return new ConfigValueCreator<T, ObservableSortedSet<?>, ObservableSortedSet<T>>(ModelTypes.SortedSet) {
			private ParsedSorting theSortingCreator;
			private ModelValueSynth<SettableValue<?>, SettableValue<Comparator<T>>> theSortingContainer;

			@Override
			protected void prepare(ExpressoQIS exS) throws QonfigInterpretationException {
				ExpressoQIS sorting = exS.forChildren("sort").peekFirst();
				if (sorting != null)
					theSortingCreator = sorting.interpret(ParsedSorting.class);
				else
					theSortingCreator = ExpressoBaseV0_1.getDefaultSorting(exS.getElement().getPositionInFile());
			}

			@Override
			protected void prepare(ExpressoEnv env, ModelInstanceType<ObservableSortedSet<?>, ObservableSortedSet<T>> instanceType)
				throws ExpressoInterpretationException {
				theSortingContainer = theSortingCreator.evaluate((TypeToken<T>) instanceType.getType(0));
			}

			@Override
			protected ObservableSortedSet<T> create(ObservableConfigValueBuilder<T> builder, ModelSetInstance msi)
				throws ModelInstantiationException {
				Comparator<T> sorting = theSortingContainer.get(msi).get();
				return builder.buildCollection(null).flow().distinctSorted(sorting, false).collectActive(msi.getUntil());
			}
		};
	}

	private <K, V> ConfigMapCreator<K, V, ObservableMap<?, ?>, ObservableMap<K, V>> mapCreator() {
		return new ConfigMapCreator<K, V, ObservableMap<?, ?>, ObservableMap<K, V>>(ModelTypes.Map) {
			@Override
			protected void prepare(ExpressoQIS exS) throws QonfigInterpretationException {
			}

			@Override
			protected void prepare(ExpressoQIS exS, ModelInstanceType<ObservableMap<?, ?>, ObservableMap<K, V>> instanceType)
				throws ExpressoInterpretationException {
			}

			@Override
			protected ObservableMap<K, V> create(ObservableConfigMapBuilder<K, V> builder, ModelSetInstance msi)
				throws ModelInstantiationException {
				return builder.buildMap(null);
			}
		};
	}

	private <K, V> ConfigMapCreator<K, V, ObservableSortedMap<?, ?>, ObservableSortedMap<K, V>> sortedMapCreator() {
		return new ConfigMapCreator<K, V, ObservableSortedMap<?, ?>, ObservableSortedMap<K, V>>(ModelTypes.SortedMap) {
			@Override
			protected void prepare(ExpressoQIS exS) throws QonfigInterpretationException {
				throw new QonfigInterpretationException("config-based sorted-maps are not yet supported",
					exS.getElement().getPositionInFile(), 0);
			}

			@Override
			protected void prepare(ExpressoQIS exS, ModelInstanceType<ObservableSortedMap<?, ?>, ObservableSortedMap<K, V>> instanceType)
				throws ExpressoInterpretationException {
			}

			@Override
			protected ObservableSortedMap<K, V> create(ObservableConfigMapBuilder<K, V> builder, ModelSetInstance msi)
				throws ModelInstantiationException {
				return null;
			}
		};
	}

	private <K, V> ConfigValueCreator<V, ObservableMultiMap<?, ?>, ObservableMultiMap<K, V>> multiMapCreator() {
		return new ConfigValueCreator<V, ObservableMultiMap<?, ?>, ObservableMultiMap<K, V>>(ModelTypes.MultiMap) {
			private TypeToken<K> theKeyType;

			@Override
			protected void prepare(ExpressoQIS exS) throws QonfigInterpretationException {
			}

			@Override
			protected void prepare(ExpressoEnv env, ModelInstanceType<ObservableMultiMap<?, ?>, ObservableMultiMap<K, V>> instanceType)
				throws ExpressoInterpretationException {
				theKeyType = (TypeToken<K>) instanceType.getType(0);
			}

			@Override
			protected ObservableMultiMap<K, V> create(ObservableConfigValueBuilder<V> builder, ModelSetInstance msi)
				throws ModelInstantiationException {
				return builder.asMap(theKeyType).buildMultiMap(null);
			}
		};
	}

	private <K, V> ConfigMapCreator<K, V, ObservableSortedMultiMap<?, ?>, ObservableSortedMultiMap<K, V>> sortedMultiMapCreator() {
		return new ConfigMapCreator<K, V, ObservableSortedMultiMap<?, ?>, ObservableSortedMultiMap<K, V>>(ModelTypes.SortedMultiMap) {
			@Override
			protected void prepare(ExpressoQIS exS) throws QonfigInterpretationException {
				throw new QonfigInterpretationException("config-based sorted-multi-maps are not yet supported",
					exS.getElement().getPositionInFile(), 0);
			}

			@Override
			protected void prepare(ExpressoQIS exS,
				ModelInstanceType<ObservableSortedMultiMap<?, ?>, ObservableSortedMultiMap<K, V>> instanceType)
					throws ExpressoInterpretationException {
			}

			@Override
			protected ObservableSortedMultiMap<K, V> create(ObservableConfigMapBuilder<K, V> builder, ModelSetInstance msi)
				throws ModelInstantiationException {
				return null;
			}
		};
	}

	static void restoreBackup(boolean fromError, ObservableConfig config, FileBackups backups, Runnable onBackup, Runnable onNoBackup,
		AppEnvironment app, boolean[] closingWithoutSave, ModelSetInstance msi) throws ModelInstantiationException {
		BetterSortedSet<Instant> backupTimes = backups == null ? null : backups.getBackups();
		if (backupTimes == null || backupTimes.isEmpty()) {
			if (onNoBackup != null)
				onNoBackup.run();
			return;
		}
		SettableValue<Instant> selectedBackup = SettableValue.build(Instant.class).build();
		Format<Instant> PAST_DATE_FORMAT = SpinnerFormat.flexDate(Instant::now, "EEE MMM dd, yyyy",
			opts -> opts.withMaxResolution(TimeUtils.DateElementType.Second).withEvaluationType(TimeUtils.RelativeInstantEvaluation.Past));
		JFrame[] frame = new JFrame[1];
		boolean[] backedUp = new boolean[1];
		ObservableValue<String> title = (app == null || app.getTitle() == null) ? ObservableValue.of("Unnamed Application")
			: app.getTitle().get(msi);
		ObservableValue<Image> icon = (app == null || app.getIcon() == null) ? ObservableValue.of(Image.class, null)
			: app.getIcon().get(msi);
		frame[0] = WindowPopulation.populateWindow(null, null, false, false)//
			.withTitle((app == null || title.get() == null) ? "Backup" : title.get() + " Backup")//
			.withIcon(app == null ? ObservableValue.of(Image.class, null) : icon)//
			.withVContent(content -> {
				if (fromError)
					content.addLabel(null, "Your configuration is missing or has been corrupted", null);
				TimeUtils.RelativeTimeFormat durationFormat = TimeUtils.relativeFormat()
					.withMaxPrecision(TimeUtils.DurationComponentType.Second).withMaxElements(2).withMonthsAndYears();
				content.addLabel(null, "Please choose a backup to restore", null)//
				.addTable(ObservableCollection.of(TypeTokens.get().of(Instant.class), backupTimes.reverse()), table -> {
					table.fill()
					.withColumn("Date", Instant.class, t -> t,
						col -> col.formatText(PAST_DATE_FORMAT::format).withWidths(80, 160, 500))//
					.withColumn("Age", Instant.class, t -> t,
						col -> col.formatText(t -> durationFormat.printAsDuration(t, Instant.now())).withWidths(50, 90, 500))//
					.withSelection(selectedBackup, true);
				}).addButton("Backup", __ -> {
					closingWithoutSave[0] = true;
					try {
						backups.restore(selectedBackup.get());
						if (config != null)
							populate(config,
								QommonsConfig.fromXml(QommonsConfig.getRootElement(backups.getBackup(selectedBackup.get()).read())));
						backedUp[0] = true;
					} catch (IOException e) {
						e.printStackTrace();
					} finally {
						closingWithoutSave[0] = false;
					}
					frame[0].setVisible(false);
				}, btn -> btn.disableWith(selectedBackup.map(t -> t == null ? "Select a Backup" : null)));
			}).run(null).getWindow();
		frame[0].addComponentListener(new ComponentAdapter() {
			@Override
			public void componentHidden(ComponentEvent e) {
				if (backedUp[0]) {
					if (onBackup != null)
						onBackup.run();
				} else {
					if (onNoBackup != null)
						onNoBackup.run();
				}
			}
		});
	}

	static void populate(ObservableConfig config, QommonsConfig initConfig) {
		config.setName(initConfig.getName());
		config.setValue(initConfig.getValue());
		SyncValueSet<? extends ObservableConfig> subConfigs = config.getAllContent();
		int configIdx = 0;
		for (QommonsConfig initSubConfig : initConfig.subConfigs()) {
			if (configIdx < subConfigs.getValues().size())
				populate(subConfigs.getValues().get(configIdx), initSubConfig);
			else
				populate(config.addChild(initSubConfig.getName()), initSubConfig);
			configIdx++;
		}
	}

	private static <T> ObservableConfigFormat<T> getDefaultConfigFormat(TypeToken<T> valueType) {
		Format<T> f;
		Class<?> type = TypeTokens.get().unwrap(TypeTokens.getRawType(valueType));
		if (type == String.class)
			f = (Format<T>) SpinnerFormat.NUMERICAL_TEXT;
		else if (type == int.class)
			f = (Format<T>) SpinnerFormat.INT;
		else if (type == long.class)
			f = (Format<T>) SpinnerFormat.LONG;
		else if (type == double.class)
			f = (Format<T>) Format.doubleFormat(4).build();
		else if (type == float.class)
			f = (Format<T>) Format.doubleFormat(4).buildFloat();
		else if (type == boolean.class)
			f = (Format<T>) Format.BOOLEAN;
		else if (Enum.class.isAssignableFrom(type))
			f = (Format<T>) Format.enumFormat((Class<Enum<?>>) type);
		else if (type == Instant.class)
			f = (Format<T>) SpinnerFormat.flexDate(Instant::now, "EEE MMM dd, yyyy", null);
		else if (type == Duration.class)
			f = (Format<T>) SpinnerFormat.flexDuration(false);
		else
			return null;
		T defaultValue = TypeTokens.get().getDefaultValue(valueType);
		return ObservableConfigFormat.ofQommonFormat(f, () -> defaultValue);
	}

	private void configureFormats(QonfigInterpreterCore.Builder interpreter) {
		interpreter.createWith("model-reference", CompiledModelValue.class, session -> {
			ExpressoQIS exS = wrap(session);
			CompiledExpression ref = exS.getAttributeExpression("ref");
			return CompiledModelValue.of("model-reference", ModelTypes.Value, () -> ref.evaluate(ModelTypes.Value.any()));
		});
		// File sources
		interpreter.createWith("native-file-source", CompiledModelValue.class,
			session -> CompiledModelValue.literal(TypeTokens.get().of(NativeFileSource.class), new NativeFileSource(), "native-file-source"));
		interpreter.createWith("sftp-file-source", CompiledModelValue.class, session -> createSftpFileSource(wrap(session)));
		interpreter.createWith("archive-enabled-file-source", CompiledModelValue.class, session -> createArchiveEnabledFileSource(wrap(session)));
		interpreter.createWith("zip-archival", CompiledModelValue.class,
			session -> CompiledModelValue.literal(TypeTokens.get().of(ArchiveEnabledFileSource.ZipCompression.class),
				new ArchiveEnabledFileSource.ZipCompression(), "zip-archival"));
		interpreter.createWith("tar-archival", CompiledModelValue.class,
			session -> CompiledModelValue.literal(TypeTokens.get().of(ArchiveEnabledFileSource.TarArchival.class),
				new ArchiveEnabledFileSource.TarArchival(), "tar-archival"));
		interpreter.createWith("gz-archival", CompiledModelValue.class,
			session -> CompiledModelValue.literal(TypeTokens.get().of(ArchiveEnabledFileSource.GZipCompression.class),
				new ArchiveEnabledFileSource.GZipCompression(), "gz-archival"));

		// Text formats
		interpreter.createWith("text-format", CompiledModelValue.class,
			session -> CompiledModelValue.literal(
				TypeTokens.get().keyFor(SpinnerFormat.class).<SpinnerFormat<String>> parameterized(String.class),
				SpinnerFormat.NUMERICAL_TEXT, "text-format"));
		interpreter.createWith("int-format", CompiledModelValue.class, session -> {
			SpinnerFormat.IntFormat format = SpinnerFormat.INT;
			String sep = session.getAttributeText("grouping-separator");
			if (sep != null) {
				if (sep.length() != 0)
					session.reporting().warn("WARNING: grouping-separator must be a single character");
				else
					format = format.withGroupingSeparator(sep.charAt(0));
			}
			return CompiledModelValue.constant(ModelValueSynth
				.literal(TypeTokens.get().keyFor(Format.class).<Format<Integer>> parameterized(Integer.class), format, "int-format"));
		});
		interpreter.createWith("long-format", CompiledModelValue.class, session -> {
			session.put(ExpressoBaseV0_1.VALUE_TYPE_KEY, TypeTokens.get().LONG);
			SpinnerFormat.LongFormat format = SpinnerFormat.LONG;
			String sep = session.getAttributeText("grouping-separator");
			if (sep != null) {
				if (sep.length() != 0)
					session.reporting().warn("WARNING: grouping-separator must be a single character");
				else
					format = format.withGroupingSeparator(sep.charAt(0));
			}
			return CompiledModelValue.constant(ModelValueSynth
				.literal(TypeTokens.get().keyFor(Format.class).<Format<Long>> parameterized(Long.class), format, "long-format"));
		});
		interpreter.createWith("double-format", CompiledModelValue.class, session -> createDoubleFormat(wrap(session)));
		interpreter.createWith("instant-format", CompiledModelValue.class, session -> createInstantFormat(wrap(session)));
		interpreter.createWith("file-format", CompiledModelValue.class, session -> createFileFormat(wrap(session)));
		interpreter.createWith("regex-format", CompiledModelValue.class, session -> CompiledModelValue
			.literal(TypeTokens.get().keyFor(Format.class).<Format<Pattern>> parameterized(Pattern.class), Format.PATTERN, "regex-format"));
		interpreter.createWith("regex-format-string", CompiledModelValue.class,
			session -> CompiledModelValue.literal(TypeTokens.get().keyFor(Format.class).<Format<String>> parameterized(String.class), //
				Format.validate(Format.TEXT, str -> {
					if (str == null || str.isEmpty())
						return null; // That's fine
					try {
						Pattern.compile(str);
						return null;
					} catch (PatternSyntaxException e) {
						return e.getMessage();
					}
				}), "regex-format-string"));

		interpreter.modifyWith("format",
			(Class<CompiledModelValue<SettableValue<?>, SettableValue<Format<Object>>>>) (Class<?>) CompiledModelValue.class,
			this::configureFormatValidation);
		interpreter.createWith("regex-validation", ValidationProducer.class, session -> createRegexValidation(wrap(session)));
		interpreter.createWith("filter-validation", ValidationProducer.class,
			session -> createFilterValidation(session.as(ExpressoQIS.class)));

		// ObservableConfigFormats
		interpreter.createWith("text-config-format", CompiledModelValue.class, session -> createTextConfigFormat(wrap(session)));
	}

	private <T> CompiledModelValue<SettableValue<?>, SettableValue<ObservableConfigFormat<T>>> createTextConfigFormat(ExpressoQIS session)
		throws QonfigInterpretationException {
		CompiledModelValue<SettableValue<?>, SettableValue<Format<T>>> textFormatCreator = session
			.interpretChildren("text-format", CompiledModelValue.class).getFirst();
		CompiledExpression defaultV = session.getAttributeExpression("default");
		String defaultS = session.getAttributeText("default-text");
		if (defaultV != null && defaultS != null)
			defaultV.throwQonfigException("default and default-text cannot both be specified", null);
		LocatedFilePosition formatPosition = session.forChildren("text-format").getFirst().getElement().getPositionInFile();
		ErrorReporting defaultReporting = defaultS == null ? null
			: session.reporting().at(session.getAttributeValuePosition("default-text"));
		return CompiledModelValue.of(session.getElement().getType().getName(), ModelTypes.Value, () -> {
			ModelValueSynth<SettableValue<?>, SettableValue<Format<T>>> textFormatContainer;
			try {
				textFormatContainer = textFormatCreator.createSynthesizer()
					.as(ModelTypes.Value.forType(TypeTokens.get().keyFor(Format.class).<Format<T>> wildCard()));
			} catch (TypeConversionException e) {
				throw new ExpressoInterpretationException("Could not interpret " + textFormatCreator + " as a format", formatPosition, 0,
					e);
			}
			TypeToken<T> formattedType = (TypeToken<T>) textFormatContainer.getType().getType(0)
				.resolveType(Format.class.getTypeParameters()[0]);
			ModelValueSynth<SettableValue<?>, SettableValue<T>> defaultValueContainer;
			if (defaultV != null)
				defaultValueContainer = defaultV.evaluate(ModelTypes.Value.forType(formattedType));
			else if (defaultS != null)
				defaultValueContainer = textFormatContainer.map(ModelTypes.Value.forType(formattedType), format -> {
					return SettableValue.asSettable(format.map(formattedType, f -> {
						if (f == null)
							return null;
						try {
							return f.parse(defaultS);
						} catch (ParseException e) {
							defaultReporting.error("Could not parse '" + defaultS + "'", e);
							return TypeTokens.get().getDefaultValue(formattedType);
						}
					}), __ -> "Unsettable");
				});
			else
				defaultValueContainer = null;
			TypeToken<ObservableConfigFormat<T>> cfType = TypeTokens.get().keyFor(ObservableConfigFormat.class)
				.<ObservableConfigFormat<T>> parameterized(formattedType);
			return new ModelValueSynth<SettableValue<?>, SettableValue<ObservableConfigFormat<T>>>() {
				private final ModelInstanceType<SettableValue<?>, SettableValue<ObservableConfigFormat<T>>> theType = ModelTypes.Value
					.forType(cfType);

				@Override
				public ModelType<SettableValue<?>> getModelType() {
					return theType.getModelType();
				}

				@Override
				public ModelInstanceType<SettableValue<?>, SettableValue<ObservableConfigFormat<T>>> getType()
					throws ExpressoInterpretationException {
					return theType;
				}

				@Override
				public SettableValue<ObservableConfigFormat<T>> get(ModelSetInstance models)
					throws ModelInstantiationException, IllegalStateException {
					SettableValue<Format<T>> textFormatV = textFormatContainer.get(models);
					SettableValue<T> defaultValueV = defaultValueContainer == null ? null : defaultValueContainer.get(models);
					return SettableValue.asSettable(//
						textFormatV.map(cfType, textFormat -> ObservableConfigFormat.ofQommonFormat(textFormat, defaultValueV)),
						__ -> "Unsettable");
				}

				@Override
				public BetterList<ModelValueSynth<?, ?>> getCores() throws ExpressoInterpretationException {
					return textFormatContainer.getCores();
				}

				@Override
				public SettableValue<ObservableConfigFormat<T>> forModelCopy(SettableValue<ObservableConfigFormat<T>> value,
					ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
					SettableValue<Format<T>> srcTextFormat = textFormatContainer.get(sourceModels);
					SettableValue<Format<T>> newTextFormat = textFormatContainer.forModelCopy(srcTextFormat, sourceModels, newModels);
					if (srcTextFormat == newTextFormat)
						return value;
					SettableValue<T> defaultValueV = defaultValueContainer == null ? null : defaultValueContainer.get(newModels);
					return SettableValue.asSettable(//
						newTextFormat.map(cfType, textFormat -> ObservableConfigFormat.ofQommonFormat(textFormat, defaultValueV)),
						__ -> "Unsettable");
				}
			};
		});
	}

	private static CompiledModelValue<SettableValue<?>, SettableValue<SftpFileSource>> createSftpFileSource(ExpressoQIS exS)
		throws QonfigInterpretationException {
		CompiledExpression hostX = exS.getAttributeExpression("host");
		CompiledExpression userX = exS.getAttributeExpression("user");
		CompiledExpression passwordX = exS.getAttributeExpression("password");
		CompiledExpression connectingX = exS.getAttributeExpression("connecting");
		CompiledExpression connectedX = exS.getAttributeExpression("connected");
		CompiledExpression timeoutX = exS.getAttributeExpression("timeout");
		CompiledExpression retryX = exS.getAttributeExpression("retry");
		return CompiledModelValue.of(exS.getElement().getType().getName(), ModelTypes.Value, () -> {
			ModelValueSynth<SettableValue<?>, SettableValue<String>> hostVC = hostX.evaluate(ModelTypes.Value.STRING);
			ModelValueSynth<SettableValue<?>, SettableValue<String>> userVC = userX.evaluate(ModelTypes.Value.STRING);
			ModelValueSynth<SettableValue<?>, SettableValue<String>> passwordVC = passwordX.evaluate(ModelTypes.Value.STRING);
			ModelValueSynth<SettableValue<?>, SettableValue<Boolean>> connectingVC = connectingX == null ? null
				: connectingX.evaluate(ModelTypes.Value.BOOLEAN);
			ModelValueSynth<SettableValue<?>, SettableValue<Boolean>> connectedVC = connectedX == null ? null
				: connectedX.evaluate(ModelTypes.Value.BOOLEAN);
			ModelValueSynth<SettableValue<?>, SettableValue<Duration>> timeoutVC = timeoutX
				.evaluate(ModelTypes.Value.forType(Duration.class));
			ModelValueSynth<SettableValue<?>, SettableValue<Integer>> retryVC = retryX.evaluate(ModelTypes.Value.INT);
			return new ModelValueSynth<SettableValue<?>, SettableValue<SftpFileSource>>() {
				private final ModelInstanceType<SettableValue<?>, SettableValue<SftpFileSource>> theType = ModelTypes.Value
					.forType(SftpFileSource.class);

				@Override
				public ModelType<SettableValue<?>> getModelType() {
					return ModelTypes.Value;
				}

				@Override
				public ModelInstanceType<SettableValue<?>, SettableValue<SftpFileSource>> getType() throws ExpressoInterpretationException {
					return theType;
				}

				@Override
				public SettableValue<SftpFileSource> get(ModelSetInstance models)
					throws ModelInstantiationException, IllegalStateException {
					SettableValue<String> host = hostVC.get(models);
					SettableValue<String> user = userVC.get(models);
					SettableValue<String> password = passwordVC.get(models);
					SettableValue<Boolean> connecting = connectingVC == null ? null : connectingVC.get(models);
					SettableValue<Boolean> connected = connectedVC == null ? null : connectedVC.get(models);
					SettableValue<Duration> timeout = timeoutVC.get(models);
					SettableValue<Integer> retry = retryVC.get(models);
					return createFileSource(host, user, password, connecting, connected, timeout, retry);
				}

				@Override
				public BetterList<ModelValueSynth<?, ?>> getCores() throws ExpressoInterpretationException {
					return BetterList.of(Stream.of(hostVC, userVC, passwordVC, connectingVC, connectedVC, timeoutVC, retryVC)//
						.filter(vc -> vc != null), vc -> vc.getCores().stream());
				}

				@Override
				public SettableValue<SftpFileSource> forModelCopy(SettableValue<SftpFileSource> value, ModelSetInstance sourceModels,
					ModelSetInstance newModels) throws ModelInstantiationException {
					SettableValue<String> srcHost = hostVC.get(sourceModels);
					SettableValue<String> srcUser = userVC.get(sourceModels);
					SettableValue<String> srcPassword = passwordVC.get(sourceModels);
					SettableValue<Boolean> srcConnecting = connectingVC == null ? null : connectingVC.get(sourceModels);
					SettableValue<Boolean> srcConnected = connectedVC == null ? null : connectedVC.get(sourceModels);
					SettableValue<Duration> srcTimeout = timeoutVC.get(sourceModels);
					SettableValue<Integer> srcRetry = retryVC.get(sourceModels);

					SettableValue<String> newHost = hostVC.forModelCopy(srcHost, sourceModels, newModels);
					SettableValue<String> newUser = userVC.forModelCopy(srcUser, sourceModels, newModels);
					SettableValue<String> newPassword = passwordVC.forModelCopy(srcPassword, sourceModels, newModels);
					SettableValue<Boolean> newConnecting = connectingVC == null ? null
						: connectingVC.forModelCopy(srcConnecting, sourceModels, newModels);
					SettableValue<Boolean> newConnected = connectedVC == null ? null
						: connectedVC.forModelCopy(srcConnected, sourceModels, newModels);
					SettableValue<Duration> newTimeout = timeoutVC.forModelCopy(srcTimeout, sourceModels, newModels);
					SettableValue<Integer> newRetry = retryVC.forModelCopy(srcRetry, sourceModels, newModels);

					if (srcHost != newHost || srcUser != newUser || srcPassword != newPassword || srcConnecting != newConnecting
						|| srcConnected != newConnected || srcTimeout != newTimeout || srcRetry != newRetry)
						return createFileSource(newHost, newUser, newPassword, newConnecting, newConnected, newTimeout, newRetry);
					else
						return value;
				}

				private SettableValue<SftpFileSource> createFileSource(SettableValue<String> host, SettableValue<String> user,
					SettableValue<String> password, SettableValue<Boolean> connecting, SettableValue<Boolean> connected,
					SettableValue<Duration> timeout, SettableValue<Integer> retry) {
					ObservableValue<Integer> timeoutMSV = timeout.map(d -> d == null ? 100 : (int) d.toMillis());
					return SettableValue.asSettable(host.transform(SftpFileSource.class, tx -> tx//
						.combineWith(user).combineWith(password).combineWith(timeoutMSV)//
						.build((hostS, txv) -> {
							String userS = txv.get(user);
							String passwordS = txv.get(password);
							int timeoutI = txv.get(timeoutMSV);
							if (connected != null)
								connected.set(false, null);
							if (connecting != null)
								connecting.set(true, null);
							SftpFileSource sftp;
							try {
								sftp = new SftpFileSource(hostS, userS, sftpSession -> {
									sftpSession.withTimeout(timeoutI)//
									.withAuthentication(userS, passwordS);
								}, "/");
								retry.changes()
								.takeUntil(Observable.or(user.noInitChanges(), password.noInitChanges(), timeout.noInitChanges()))
								.act(evt -> sftp.setRetryCount(evt.getNewValue() == null ? 1 : evt.getNewValue()));
								BetterFile root = BetterFile.at(sftp, "/");
								if (root.isDirectory()) { // Attempt to connect
									if (connected != null)
										connected.set(true, null);
								} else {
									if (connected != null)
										connected.set(false, null);
								}
							} catch (RuntimeException | Error e) {
								if (connected != null)
									connected.set(false, null);
								return null;
							} finally {
								if (connecting != null)
									connecting.set(false, null);
							}
							return sftp;
						})), __ -> "Unsettable");
				}
			};
		});
	}

	private static CompiledModelValue<SettableValue<?>, SettableValue<ArchiveEnabledFileSource>> createArchiveEnabledFileSource(ExpressoQIS exS)
		throws QonfigInterpretationException {
		CompiledModelValue<SettableValue<?>, SettableValue<BetterFile.FileDataSource>> wrappedCreator;
		ExpressoQIS wrappedSession = exS.forChildren("wrapped").peekFirst();
		if (wrappedSession == null)
			wrappedCreator = CompiledModelValue.literal(TypeTokens.get().of(BetterFile.FileDataSource.class), new NativeFileSource(),
				"native-file-source");
		else
			wrappedCreator = wrappedSession.interpret(CompiledModelValue.class);
		CompiledExpression archiveDepthX = exS.getAttributeExpression("max-archive-depth");
		List<CompiledModelValue<SettableValue<?>, SettableValue<ArchiveEnabledFileSource.FileArchival>>> archiveMethodCreators;
		archiveMethodCreators = exS.interpretChildren("archive-method", CompiledModelValue.class);
		LocatedFilePosition position;
		if (wrappedSession != null)
			position = wrappedSession.getElement().getPositionInFile();
		else
			position = exS.getElement().getPositionInFile();
		List<LocatedFilePosition> archivePositions = exS.getChildren("archive-method").stream().map(s -> s.getPositionInFile())
			.collect(Collectors.toList());
		return CompiledModelValue.of(exS.getElement().getType().getName(), ModelTypes.Value, () -> {
			ModelValueSynth<SettableValue<?>, SettableValue<BetterFile.FileDataSource>> wrappedContainer;
			try {
				wrappedContainer = wrappedCreator.createSynthesizer().as(ModelTypes.Value.forType(BetterFile.FileDataSource.class));
			} catch (TypeConversionException e) {
				throw new ExpressoInterpretationException("Could not evaluate " + wrappedCreator + " as a file source", //
					position, 0, e);
			}
			ModelValueSynth<SettableValue<?>, SettableValue<Integer>> archiveDepthVC = archiveDepthX.evaluate(ModelTypes.Value.INT);
			List<ModelValueSynth<SettableValue<?>, SettableValue<ArchiveEnabledFileSource.FileArchival>>> archiveMethodContainers = new ArrayList<>(
				archiveMethodCreators.size());
			int i = 0;
			for (CompiledModelValue<SettableValue<?>, SettableValue<ArchiveEnabledFileSource.FileArchival>> amc : archiveMethodCreators) {
				try {
					archiveMethodContainers
					.add(amc.createSynthesizer().as(ModelTypes.Value.forType(ArchiveEnabledFileSource.FileArchival.class)));
				} catch (TypeConversionException e) {
					throw new ExpressoInterpretationException("Could not interpret archive method", //
						archivePositions.get(i), 0, e);
				}
				i++;
			}
			return new ModelValueSynth<SettableValue<?>, SettableValue<ArchiveEnabledFileSource>>() {
				private final ModelInstanceType<SettableValue<?>, SettableValue<ArchiveEnabledFileSource>> theType = ModelTypes.Value
					.forType(ArchiveEnabledFileSource.class);

				@Override
				public ModelType<SettableValue<?>> getModelType() {
					return ModelTypes.Value;
				}

				@Override
				public ModelInstanceType<SettableValue<?>, SettableValue<ArchiveEnabledFileSource>> getType() {
					return theType;
				}

				@Override
				public SettableValue<ArchiveEnabledFileSource> get(ModelSetInstance models)
					throws ModelInstantiationException, IllegalStateException {
					SettableValue<BetterFile.FileDataSource> wrappedV = wrappedContainer.get(models);
					SettableValue<Integer> archiveDepth = archiveDepthVC.get(models);
					List<SettableValue<ArchiveEnabledFileSource.FileArchival>> archiveMethods = new ArrayList<>(
						archiveMethodContainers.size());
					for (ModelValueSynth<SettableValue<?>, SettableValue<ArchiveEnabledFileSource.FileArchival>> amc : archiveMethodContainers)
						archiveMethods.add(amc.get(models));
					return createFileSource(wrappedV, archiveDepth, archiveMethods);
				}

				@Override
				public BetterList<ModelValueSynth<?, ?>> getCores() throws ExpressoInterpretationException {
					return BetterList.of(Stream.concat(Stream.of(wrappedContainer, archiveDepthVC), //
						archiveMethodContainers.stream()), vc -> vc.getCores().stream());
				}

				@Override
				public SettableValue<ArchiveEnabledFileSource> forModelCopy(SettableValue<ArchiveEnabledFileSource> value,
					ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
					SettableValue<BetterFile.FileDataSource> srcWrapped = wrappedContainer.get(sourceModels);
					SettableValue<Integer> srcArchiveDepth = archiveDepthVC.get(sourceModels);

					SettableValue<BetterFile.FileDataSource> newWrapped = wrappedContainer.forModelCopy(srcWrapped, sourceModels,
						newModels);
					SettableValue<Integer> newArchiveDepth = archiveDepthVC.forModelCopy(srcArchiveDepth, sourceModels, newModels);

					List<SettableValue<ArchiveEnabledFileSource.FileArchival>> srcArchiveMethods = new ArrayList<>(
						archiveMethodContainers.size());
					List<SettableValue<ArchiveEnabledFileSource.FileArchival>> newArchiveMethods = new ArrayList<>(
						archiveMethodContainers.size());
					boolean identical = srcWrapped == newWrapped && srcArchiveDepth == newArchiveDepth;
					for (ModelValueSynth<SettableValue<?>, SettableValue<ArchiveEnabledFileSource.FileArchival>> amc : archiveMethodContainers) {
						SettableValue<ArchiveEnabledFileSource.FileArchival> srcAM = amc.get(sourceModels);
						SettableValue<ArchiveEnabledFileSource.FileArchival> newAM = amc.forModelCopy(srcAM, sourceModels, newModels);
						srcArchiveMethods.add(srcAM);
						newArchiveMethods.add(newAM);
						identical &= srcAM == newAM;
					}

					if (identical)
						return value;
					else
						return createFileSource(newWrapped, newArchiveDepth, newArchiveMethods);
				}

				private SettableValue<ArchiveEnabledFileSource> createFileSource(SettableValue<FileDataSource> wrapped,
					SettableValue<Integer> archiveDepth, List<SettableValue<FileArchival>> archiveMethods) {
					return SettableValue.asSettable(wrapped.transform(ArchiveEnabledFileSource.class, tx -> {
						Transformation.TransformationBuilder<BetterFile.FileDataSource, ArchiveEnabledFileSource, ?> tx2 = tx
							.combineWith(archiveDepth);
						for (SettableValue<ArchiveEnabledFileSource.FileArchival> archiveMethod : archiveMethods)
							tx2 = tx2.combineWith(archiveMethod);
						return tx2.build((wrappedFS, txv) -> {
							Integer maxDepth = txv.get(archiveDepth);
							List<ArchiveEnabledFileSource.FileArchival> archiveMethodInsts = new ArrayList<>(archiveMethods.size());
							for (SettableValue<ArchiveEnabledFileSource.FileArchival> am : archiveMethods)
								archiveMethodInsts.add(txv.get(am));
							ArchiveEnabledFileSource fileSource = new ArchiveEnabledFileSource(wrappedFS);
							fileSource.withArchival(archiveMethodInsts);
							fileSource.setMaxArchiveDepth(maxDepth == null ? 10 : maxDepth);
							return fileSource;
						});
					}), __ -> "Unsettable");
				}
			};
		});
	}

	private static <T> SettableValue<Format<T>> createWrappedValidatedFormat(SettableValue<Format<T>> format,
		List<Function<T, String>> validation) {
		Function<T, String> validationFn = null;
		for (Function<T, String> val : validation) {
			if (validationFn == null)
				validationFn = val;
			else {
				Function<T, String> old = validationFn;
				validationFn = v -> {
					String err = old.apply(v);
					if (err == null)
						err = val.apply(v);
					return err;
				};
			}
		}
		final Function<T, String> finalVal = validationFn;
		return SettableValue.asSettable(format.map(format.getType(), f -> f == null ? null : Format.validate(f, finalVal)), //
			__ -> "Not settable");
	}

	private <T> CompiledModelValue<SettableValue<?>, SettableValue<Format<T>>> configureFormatValidation(
		CompiledModelValue<SettableValue<?>, SettableValue<Format<T>>> formatCreator, CoreSession session, Object prepared)
			throws QonfigInterpretationException {
		List<ValidationProducer> validatorProducers = new ArrayList<>();
		for (ExpressoQIS valSession : session.as(ExpressoQIS.class).forChildren())
			validatorProducers.add(valSession.interpret(ValidationProducer.class));
		return CompiledModelValue.of(formatCreator::toString, ModelTypes.Value, () -> {
			ModelValueSynth<SettableValue<?>, SettableValue<Format<T>>> formatContainer = formatCreator.createSynthesizer();
			TypeToken<T> formattedType = (TypeToken<T>) formatContainer.getType().getType(0)
				.resolveType(Format.class.getTypeParameters()[0]);
			List<Validation<T>> validationContainers = new ArrayList<>();
			for (ValidationProducer valProducer : validatorProducers)
				validationContainers.add(valProducer.createValidator(formattedType));

			class ValidatedFormat extends SettableValue.WrappingSettableValue<Format<T>> {
				private final SettableValue<Format<T>> theFormat;
				private final List<Function<T, String>> theValidation;

				public ValidatedFormat(SettableValue<Format<T>> format, List<Function<T, String>> validation) {
					super(createWrappedValidatedFormat(format, validation));
					theFormat = format;
					theValidation = validation;
				}

				ValidatedFormat forModelCopy(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
					SettableValue<Format<T>> newFormat = formatContainer.forModelCopy(theFormat, sourceModels, newModels);
					boolean different = newFormat != theFormat;
					List<Function<T, String>> newVal = new ArrayList<>(theValidation.size());
					for (int v = 0; v < theValidation.size(); v++) {
						Function<T, String> newValI = validationContainers.get(v).forModelCopy(theValidation.get(v), sourceModels,
							newModels);
						different = newValI != theValidation.get(v);
						newVal.add(newValI);
					}
					if (!different)
						return this;
					return new ValidatedFormat(newFormat, newVal);
				}
			}
			class ValidatedFormatContainer implements ModelValueSynth<SettableValue<?>, SettableValue<Format<T>>> {
				private final ModelValueSynth<SettableValue<?>, SettableValue<Format<T>>> theFormatContainer;
				private final List<Validation<T>> theValidationContainers;

				ValidatedFormatContainer(ModelValueSynth<SettableValue<?>, SettableValue<Format<T>>> format,
					List<Validation<T>> validation) {
					theFormatContainer = format;
					theValidationContainers = validation;
				}

				@Override
				public ModelType<SettableValue<?>> getModelType() {
					return formatContainer.getModelType();
				}

				@Override
				public ModelInstanceType<SettableValue<?>, SettableValue<Format<T>>> getType() throws ExpressoInterpretationException {
					return formatContainer.getType();
				}

				@Override
				public SettableValue<Format<T>> get(ModelSetInstance models) throws ModelInstantiationException {
					return new ValidatedFormat(//
						theFormatContainer.get(models), //
						BetterList.of2(theValidationContainers.stream(), val -> val.getTest(models)));
				}

				@Override
				public SettableValue<Format<T>> forModelCopy(SettableValue<Format<T>> formatV, ModelSetInstance sourceModels,
					ModelSetInstance newModels) throws ModelInstantiationException {
					return ((ValidatedFormat) formatV).forModelCopy(sourceModels, newModels);
				}

				@Override
				public BetterList<ModelValueSynth<?, ?>> getCores() throws ExpressoInterpretationException {
					List<ModelValueSynth<?, ?>> formatCores = formatContainer.getCores();
					List<ModelValueSynth<?, ?>> validationCores = BetterList.of(validationContainers.stream(), vc -> vc.getCores().stream());
					return BetterList.of(Stream.concat(formatCores.stream(), validationCores.stream()));
				}
			}
			return new ValidatedFormatContainer(formatContainer, validationContainers);
		});
	}

	private CompiledModelValue<SettableValue<?>, SettableValue<Format<Double>>> createDoubleFormat(ExpressoQIS session)
		throws QonfigInterpretationException {
		int sigDigs = Integer.parseInt(session.getAttributeText("sig-digs"));
		Format.SuperDoubleFormatBuilder builder = Format.doubleFormat(sigDigs);
		String unit = session.getAttributeText("unit");
		boolean withMetricPrefixes = session.getAttribute("metric-prefixes", boolean.class);
		boolean withMetricPrefixesP2 = session.getAttribute("metric-prefixes-p2", boolean.class);
		List<? extends ExpressoQIS> prefixes = session.forChildren("prefix");
		if (unit != null) {
			builder.withUnit(unit, session.getAttribute("unit-required", boolean.class));
			if (withMetricPrefixes) {
				if (withMetricPrefixesP2)
					session.reporting().warn("Both 'metric-prefixes' and 'metric-prefixes-p2' specified");
				builder.withMetricPrefixes();
			} else if (withMetricPrefixesP2)
				builder.withMetricPrefixesPower2();
			for (ExpressoQIS prefix : prefixes) {
				String prefixName = prefix.getAttributeText("name");
				String expS = prefix.getAttributeText("exp");
				String multS = prefix.getAttributeText("mult");
				if (expS != null) {
					if (multS != null)
						session.reporting().warn("Both 'exp' and 'mult' specified for prefix '" + prefixName + "'");
					builder.withPrefix(prefixName, Integer.parseInt(expS));
				} else if (multS != null)
					builder.withPrefix(prefixName, Double.parseDouble(multS));
				else
					session.reporting().warn("Neither 'exp' nor 'mult' specified for prefix '" + prefixName + "'");
			}
		} else {
			if (withMetricPrefixes)
				session.reporting().warn("'metric-prefixes' specified without a unit");
			if (withMetricPrefixesP2)
				session.reporting().warn("'metric-prefixes-p2' specified without a unit");
			if (!prefixes.isEmpty())
				session.reporting().warn("prefixes specified without a unit");
		}
		return CompiledModelValue.literal(TypeTokens.get().keyFor(Format.class).<Format<Double>> parameterized(Double.class), builder.build(),
			"double");
	}

	private CompiledModelValue<SettableValue<?>, SettableValue<Format<Instant>>> createInstantFormat(ExpressoQIS session)
		throws QonfigInterpretationException {
		String dayFormat = session.getAttributeText("day-format");
		TimeEvaluationOptions options = TimeUtils.DEFAULT_OPTIONS;
		QonfigAttributeDef tzAttr = session.getAttributeDef(null, null, "time-zone");
		String tzs = session.getAttributeText("time-zone");
		if (tzs != null) {
			TimeZone timeZone = TimeZone.getTimeZone(tzs);
			if (timeZone.getRawOffset() == 0 && !timeZone.useDaylightTime()//
				&& !(tzs.equalsIgnoreCase("GMT") || tzs.equalsIgnoreCase("Z"))) {
				QonfigValue tzAttrV = session.getElement().getAttributes().get(tzAttr);
				throw new QonfigInterpretationException("Unrecognized time-zone '" + tzs + "'",
					new LocatedFilePosition(tzAttrV.fileLocation, tzAttrV.position.getPosition(0)), tzs.length());
			}
			options = options.withTimeZone(timeZone);
		}
		try {
			options = options.withMaxResolution(TimeUtils.DateElementType.valueOf(session.getAttributeText("max-resolution")));
		} catch (IllegalArgumentException e) {
			session.reporting().warn("Unrecognized instant resolution: '" + session.getAttributeText("max-resolution"));
		}
		options = options.with24HourFormat(session.getAttribute("format-24h", boolean.class));
		String rteS = session.getAttributeText("relative-eval-type");
		try {
			options = options.withEvaluationType(TimeUtils.RelativeInstantEvaluation.valueOf(rteS));
		} catch (IllegalArgumentException e) {
			session.reporting().warn("Unrecognized relative evaluation type: '" + rteS);
		}
		TimeEvaluationOptions fOptions = options;
		TypeToken<Instant> instantType = TypeTokens.get().of(Instant.class);
		TypeToken<Format<Instant>> formatType = TypeTokens.get().keyFor(Format.class).<Format<Instant>> parameterized(instantType);
		ModelInstanceType<SettableValue<?>, SettableValue<Instant>> instantModelType = ModelTypes.Value.forType(instantType);
		ModelInstanceType<SettableValue<?>, SettableValue<Format<Instant>>> formatInstanceType = ModelTypes.Value.forType(formatType);
		CompiledExpression relativeX = session.getAttributeExpression("relative-to");
		return CompiledModelValue.of(session.getElement().getType().getName(), ModelTypes.Value, () -> {
			ModelValueSynth<SettableValue<?>, SettableValue<Instant>> relativeTo;
			if (relativeX != null)
				relativeTo = relativeX.evaluate(instantModelType);
			else {
				relativeTo = ModelValueSynth.of(instantModelType, msi -> new SettableValue<Instant>() {
					private long theStamp;

					@Override
					public Object getIdentity() {
						return Identifiable.baseId("Instant.now()", "Instant.now()");
					}

					@Override
					public TypeToken<Instant> getType() {
						return null;
					}

					@Override
					public Instant get() {
						return Instant.now();
					}

					@Override
					public Observable<ObservableValueEvent<Instant>> noInitChanges() {
						return Observable.empty();
					}

					@Override
					public long getStamp() {
						return theStamp++;
					}

					@Override
					public boolean isLockSupported() {
						return false;
					}

					@Override
					public Transaction lock(boolean write, Object cause) {
						return Transaction.NONE;
					}

					@Override
					public Transaction tryLock(boolean write, Object cause) {
						return Transaction.NONE;
					}

					@Override
					public ObservableValue<String> isEnabled() {
						return SettableValue.ALWAYS_DISABLED;
					}

					@Override
					public String isAcceptable(Instant value) {
						return StdMsg.UNSUPPORTED_OPERATION;
					}

					@Override
					public Instant set(Instant value, Object cause) throws IllegalArgumentException, UnsupportedOperationException {
						throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
					}
				});
			}
			ModelValueSynth<SettableValue<?>, SettableValue<Instant>> relativeTo2 = relativeTo;
			return new ObservableModelSet.AbstractValueSynth<SettableValue<?>, SettableValue<Format<Instant>>>(formatInstanceType) {
				@Override
				public SettableValue<Format<Instant>> get(ModelSetInstance models) throws ModelInstantiationException {
					return ObservableModelSet.literal(SpinnerFormat.flexDate(relativeTo2.get(models), dayFormat, __ -> fOptions),
						"instant");
				}

				@Override
				public SettableValue<Format<Instant>> forModelCopy(SettableValue<Format<Instant>> value, ModelSetInstance sourceModels,
					ModelSetInstance newModels) throws ModelInstantiationException {
					return ObservableModelSet.literal(SpinnerFormat.flexDate(relativeTo2.get(newModels), dayFormat, __ -> fOptions),
						"instant");
				}

				@Override
				public BetterList<ModelValueSynth<?, ?>> getCores() {
					return BetterList.of(this);
				}
			};
		});
	}

	private CompiledModelValue<SettableValue<?>, SettableValue<Format<BetterFile>>> createFileFormat(ExpressoQIS session)
		throws QonfigInterpretationException {
		ExpressoQIS fileSourceSession = session.forChildren("file-source").peekFirst();
		CompiledModelValue<SettableValue<?>, SettableValue<BetterFile.FileDataSource>> fileSourceCreator;
		if (fileSourceSession == null)
			fileSourceCreator = CompiledModelValue.literal(TypeTokens.get().of(BetterFile.FileDataSource.class), new NativeFileSource(),
				"native-file-source");
		else
			fileSourceCreator = fileSourceSession.interpret(CompiledModelValue.class);
		String workingDir = session.getAttributeText("working-dir");
		boolean allowEmpty = session.getAttribute("allow-empty", boolean.class);
		LocatedFilePosition position;
		if (fileSourceSession != null)
			position = fileSourceSession.getElement().getPositionInFile();
		else
			position = session.getElement().getPositionInFile();
		return CompiledModelValue.of(session.getElement().getType().getName(), ModelTypes.Value, () -> {
			ModelValueSynth<SettableValue<?>, SettableValue<BetterFile.FileDataSource>> fileSource;
			try {
				fileSource = fileSourceCreator.createSynthesizer().as(ModelTypes.Value.forType(BetterFile.FileDataSource.class));
			} catch (TypeConversionException e) {
				throw new ExpressoInterpretationException("Could not interpret " + fileSourceCreator + " as a file source", position, 0, e);
			}

			TypeToken<Format<BetterFile>> fileFormatType = TypeTokens.get().keyFor(Format.class)
				.<Format<BetterFile>> parameterized(BetterFile.class);
			return new ObservableModelSet.AbstractValueSynth<SettableValue<?>, SettableValue<Format<BetterFile>>>(
				ModelTypes.Value.forType(fileFormatType)) {
				@Override
				public SettableValue<Format<BetterFile>> get(ModelSetInstance models) throws ModelInstantiationException {
					SettableValue<BetterFile.FileDataSource> fds = fileSource.get(models);
					return createFileFormat(fds);
				}

				@Override
				public SettableValue<Format<BetterFile>> forModelCopy(SettableValue<Format<BetterFile>> value,
					ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
					SettableValue<BetterFile.FileDataSource> sourceFDS = fileSource.get(sourceModels);
					SettableValue<BetterFile.FileDataSource> newFDS = fileSource.forModelCopy(sourceFDS, sourceModels, newModels);
					if (sourceFDS == newFDS)
						return value;
					else
						return createFileFormat(newFDS);
				}

				private SettableValue<Format<BetterFile>> createFileFormat(SettableValue<FileDataSource> fds) {
					return SettableValue.asSettable(//
						fds.transform(fileFormatType, tx -> tx.map(fs -> {
							BetterFile workingDirFile = BetterFile.at(fs, workingDir);
							return new BetterFile.FileFormat(fs, workingDirFile, allowEmpty);
						})), //
						__ -> "Not reversible");
				}

				@Override
				public BetterList<ModelValueSynth<?, ?>> getCores() throws ExpressoInterpretationException {
					return fileSource.getCores();
				}
			};
		});
	}

	private ValidationProducer createRegexValidation(ExpressoQIS session) throws QonfigInterpretationException {
		CompiledExpression patternX = session.getAttributeExpression("pattern");
		return new ValidationProducer() {
			@Override
			public <T> Validation<T> createValidator(TypeToken<T> formatType) throws ExpressoInterpretationException {
				ModelValueSynth<SettableValue<?>, SettableValue<Pattern>> patternC = patternX
					.evaluate(ModelTypes.Value.forType(Pattern.class));
				class PatternValidator implements Function<T, String> {
					final SettableValue<Pattern> patternV;

					PatternValidator(SettableValue<Pattern> patternV) {
						this.patternV = patternV;
					}

					@Override
					public String apply(T v) {
						if (v == null)
							return null;
						Pattern pattern = patternV.get();
						if (pattern == null)
							return null;
						else if (pattern.matcher(v.toString()).matches())
							return null;
						else
							return "Value must match " + pattern.pattern();
					}
				}
				return new Validation<T>() {
					@Override
					public Function<T, String> getTest(ModelSetInstance models) throws ModelInstantiationException {
						return new PatternValidator(patternC.get(models));
					}

					@Override
					public Function<T, String> forModelCopy(Function<T, String> validator, ModelSetInstance sourceModels,
						ModelSetInstance newModels) throws ModelInstantiationException {
						SettableValue<Pattern> oldPattern = ((PatternValidator) validator).patternV;
						SettableValue<Pattern> newPattern = patternC.forModelCopy(oldPattern, sourceModels, newModels);
						if (newPattern == oldPattern)
							return validator;
						return new PatternValidator(newPattern);
					}

					@Override
					public BetterList<ModelValueSynth<?, ?>> getCores() throws ExpressoInterpretationException {
						return patternC.getCores();
					}
				};
			}
		};
	}

	private ValidationProducer createFilterValidation(ExpressoQIS session) throws QonfigInterpretationException {
		String filterValue = session.getAttributeText("filter-value-name");
		CompiledExpression testX = session.getAttributeExpression("test");
		ExpressoEnv env = session.getExpressoEnv();
		LocatedFilePosition position = session.getElement().getPositionInFile();
		return new ValidationProducer() {
			@Override
			public <T> Validation<T> createValidator(TypeToken<T> formatType) throws ExpressoInterpretationException {
				ModelInstanceType<SettableValue<?>, SettableValue<T>> type = ModelTypes.Value.forType(formatType);
				DynamicModelValue.satisfyDynamicValueType(filterValue, env.getModels(), type);
				ModelValueSynth<SettableValue<?>, SettableValue<String>> filter;
				try {
					filter = testX.evaluate(ModelTypes.Value.STRING);
				} catch (ExpressoInterpretationException e) {
					ModelValueSynth<SettableValue<?>, SettableValue<Boolean>> bFilter;
					try {
						bFilter = testX.evaluate(ModelTypes.Value.BOOLEAN);
						filter = bFilter.map(ModelTypes.Value.STRING, bv -> SettableValue.asSettable(//
							bv.map(String.class, b -> b ? null : "Filter does not pass"), __ -> "Not Settable"));
					} catch (ExpressoInterpretationException e2) {
						throw new ExpressoInterpretationException("Could not evaluate filter test", e2.getPosition(), e2.getErrorLength(),
							e2);
					}
				}
				class FilterValidator implements Function<T, String> {
					final SettableValue<T> value;
					final SettableValue<String> testValue;

					public FilterValidator(SettableValue<T> value, SettableValue<String> testValue) {
						this.value = value;
						this.testValue = testValue;
					}

					@Override
					public String apply(T t) {
						value.set(t, null);
						return testValue.get();
					}
				}
				ModelValueSynth<SettableValue<?>, SettableValue<String>> fFilter = filter;
				env.interpretLocalModel();
				return new Validation<T>() {
					@Override
					public Function<T, String> getTest(ModelSetInstance models) throws ModelInstantiationException {
						models = env.wrapLocal(models);
						SettableValue<T> value = SettableValue.build((TypeToken<T>) type.getType(0)).build();
						try {
							DynamicModelValue.satisfyDynamicValue(filterValue, type, models, value);
						} catch (ModelException | TypeConversionException e) {
							throw new ModelInstantiationException("Could not satisfy " + filterValue, position, 0, e);
						}
						SettableValue<String> testValue = fFilter.get(models);
						return new FilterValidator(value, testValue);
					}

					@Override
					public Function<T, String> forModelCopy(Function<T, String> validator, ModelSetInstance sourceModels,
						ModelSetInstance newModels) throws ModelInstantiationException {
						SettableValue<String> oldTest = ((FilterValidator) validator).testValue;
						SettableValue<String> newTest = fFilter.forModelCopy(oldTest, sourceModels, newModels);
						if (oldTest == newTest)
							return validator;
						return new FilterValidator(((FilterValidator) validator).value, newTest);
					}

					@Override
					public BetterList<ModelValueSynth<?, ?>> getCores() throws ExpressoInterpretationException {
						return fFilter.getCores();
					}
				};
			}
		};
	}
}
