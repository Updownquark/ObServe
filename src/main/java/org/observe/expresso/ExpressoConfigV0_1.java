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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.JFrame;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.SettableValue;
import org.observe.assoc.ObservableMap;
import org.observe.assoc.ObservableMultiMap;
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
import org.observe.expresso.ExpressoBaseV0_1.AppEnvironment;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.observe.expresso.ObservableModelSet.ValueCreator;
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
import org.qommons.io.BetterFile;
import org.qommons.io.BetterFile.FileDataSource;
import org.qommons.io.FileBackups;
import org.qommons.io.Format;
import org.qommons.io.LocatedFilePosition;
import org.qommons.io.NativeFileSource;
import org.qommons.io.SpinnerFormat;
import org.qommons.threading.QommonsTimer;
import org.xml.sax.SAXException;

import com.google.common.reflect.TypeToken;

/** Qonfig Interpretation for the ExpressoBaseV0_1 API */
public class ExpressoConfigV0_1 implements QonfigInterpretation {
	/**
	 * Creates an {@link ObservableConfigFormat}
	 *
	 * @param <T> The type of the format
	 */
	public interface ConfigFormatProducer<T> {
		/**
		 * Creates the format
		 *
		 * @param models The model instance to use to create the format
		 * @return The config format
		 */
		ObservableConfigFormat<T> getFormat(ModelSetInstance models);
	}

	/**
	 * Abstract {@link Expresso.ConfigModelValue} implementation
	 *
	 * @param <M> The model type of the value
	 * @param <MV> The type of the value
	 */
	public static abstract class AbstractConfigModelValue<M, MV extends M> implements Expresso.ConfigModelValue<M, MV> {
		private final ModelInstanceType<M, MV> theType;

		/** @param type The type of the value */
		public AbstractConfigModelValue(ModelInstanceType<M, MV> type) {
			theType = type;
		}

		@Override
		public ModelInstanceType<M, MV> getType() {
			return theType;
		}
	}

	/**
	 * Equivalent of a {@link ValueCreator} for a &lt;validate> element
	 *
	 * @param <V> The type to validate
	 */
	public interface ValidationProducer<V> {
		/** @return A Validation to create the actual validator */
		Validation<V> createValidator();
	}

	/**
	 * Equivalent of a {@link ValueContainer} for a &lt;validate> element
	 *
	 * @param <V> The type to validate
	 */
	public interface Validation<V> {
		/**
		 * @param models The model instance for the test to use
		 * @return The actual test
		 */
		Function<V, String> getTest(ModelSetInstance models);

		/**
		 * Equivalent of {@link ValueContainer#forModelCopy(Object, ModelSetInstance, ModelSetInstance)}
		 *
		 * @param validator The validator from the source models
		 * @param sourceModels The source models
		 * @param newModels The new models
		 * @return The validator for the new models
		 */
		Function<V, String> forModelCopy(Function<V, String> validator, ModelSetInstance sourceModels, ModelSetInstance newModels);

		/**
		 * Equivalent of {@link ValueContainer#getCores()}
		 *
		 * @return The value container cores that are the fundamental sources of value for the test
		 */
		BetterList<ValueContainer<?, ?>> getCores();
	}

	/** The name of the model value to store the {@link ObservableCollection} in the model */
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

	ExpressoQIS wrap(CoreSession session) throws QonfigInterpretationException {
		return session.as(ExpressoQIS.class);
	}

	void configureConfigModels(QonfigInterpreterCore.Builder interpreter) {
		abstract class ConfigCollectionValue<C extends ObservableCollection<?>>
		implements QonfigValueCreator<Expresso.ConfigModelValue<C, C>> {
			private final ModelType<C> theType;

			protected ConfigCollectionValue(ModelType<C> type) {
				theType = type;
			}

			@Override
			public Expresso.ConfigModelValue<C, C> createValue(CoreSession session) throws QonfigInterpretationException {
				TypeToken<Object> type = ExpressoBaseV0_1.getType(wrap(session), VALUE_TYPE_KEY);
				prepare(type, wrap(session));
				return new AbstractConfigModelValue<C, C>((ModelInstanceType<C, C>) theType.forTypes(type)) {
					@Override
					public C create(ObservableConfigValueBuilder<?> config, ModelSetInstance msi) {
						return modify(config.buildCollection(null), msi);
					}
				};
			}

			protected <V> void prepare(TypeToken<V> type, ExpressoQIS session) throws QonfigInterpretationException {
			}

			protected abstract <V> C modify(ObservableCollection<V> collection, ModelSetInstance msi);
		}
		interpreter//
		.createWith("simple-config-format", ValueCreator.class, session -> createSimpleConfigFormat(wrap(session)))//
		.createWith("config", ObservableModelSet.class, new ConfigModelCreator())//
		.createWith("value", Expresso.ConfigModelValue.class, session -> {
			ExpressoQIS exS = wrap(session);
			TypeToken<Object> type = ExpressoBaseV0_1.getType(exS, VALUE_TYPE_KEY);
			ObservableExpression defaultX = exS.asElement("config-value").getAttributeExpression("default");
			ValueContainer<SettableValue<?>, SettableValue<Object>> defaultV;
			Format<Object>[] format = new Format[1];
			if (defaultX != null) {
				// If the format is a simple text format, add the ability to parse literals with it
				NonStructuredParser nsp = format == null ? null : new NonStructuredParser() {
					@Override
					public boolean canParse(TypeToken<?> type2, String text) {
						return format[0] != null;
					}

					@Override
					public <T> ObservableValue<? extends T> parse(TypeToken<T> type2, String text) throws ParseException {
						return ObservableValue.of(type2, (T) format[0].parse(text));
					}
				};
				if (format != null)
					exS.getExpressoEnv().withNonStructuredParser(TypeTokens.getRawType(type), nsp);
					defaultV = defaultX == null ? null : ExpressoBaseV0_1.parseValue(exS.getExpressoEnv(), type, defaultX);
				if (format != null)
					exS.getExpressoEnv().removeNonStructuredParser(TypeTokens.getRawType(type), nsp);
			} else
				defaultV = null;
			return new AbstractConfigModelValue<SettableValue<?>, SettableValue<Object>>(ModelTypes.Value.forType(type)) {
				@Override
				public SettableValue<Object> create(ObservableConfigValueBuilder<?> config, ModelSetInstance msi) {
					SettableValue<Object> built = (SettableValue<Object>) config.buildValue(null);
					if (defaultV != null && config.getConfig().getChild(config.getPath(), false, null) == null) {
						if (config.getFormat() instanceof ObservableConfigFormat.Impl.SimpleConfigFormat)
							format[0] = ((ObservableConfigFormat.Impl.SimpleConfigFormat<Object>) config.getFormat()).format;
						built.set(defaultV.get(msi).get(), null);
						format[0] = null;
					}
					return built;
				}
			};
		}).createWith("value-set", Expresso.ConfigModelValue.class, session -> {
			TypeToken<Object> type = ExpressoBaseV0_1.getType(wrap(session), VALUE_TYPE_KEY);
			return new AbstractConfigModelValue<ObservableValueSet<?>, ObservableValueSet<Object>>(ModelTypes.ValueSet.forType(type)) {
				@Override
				public ObservableValueSet<Object> create(ObservableConfigValueBuilder<?> config, ModelSetInstance msi) {
					return (ObservableValueSet<Object>) config.buildEntitySet(null);
				}
			};
		})
		.createWith("list", Expresso.ConfigModelValue.class, new ConfigCollectionValue<ObservableCollection<?>>(ModelTypes.Collection) {
			@Override
			protected <V> ObservableCollection<V> modify(ObservableCollection<V> collection, ModelSetInstance msi) {
				return collection;
			}
		}).createWith("set", Expresso.ConfigModelValue.class, new ConfigCollectionValue<ObservableSet<?>>(ModelTypes.Set) {
			@Override
			protected <V> ObservableSet<V> modify(ObservableCollection<V> collection, ModelSetInstance msi) {
				return collection.flow().distinct().collectActive(msi.getUntil());
			}
		}).createWith("sorted-set", Expresso.ConfigModelValue.class,
			new ConfigCollectionValue<ObservableSortedSet<?>>(ModelTypes.SortedSet) {
			private ValueContainer<SettableValue<?>, SettableValue<Comparator<Object>>> theComparator;

			@Override
			protected <V> void prepare(TypeToken<V> type, ExpressoQIS session) throws QonfigInterpretationException {
				theComparator = (ValueContainer<SettableValue<?>, SettableValue<Comparator<Object>>>) (ValueContainer<?, ?>) ExpressoBaseV0_1
					.parseSorting(type, session.forChildren("sort").peekFirst()).createContainer();
			}

			@Override
			protected <V> ObservableSortedSet<V> modify(ObservableCollection<V> collection, ModelSetInstance msi) {
				return collection.flow().distinctSorted(theComparator.get(msi).get(), false).collectActive(msi.getUntil());
			}
		})
		.createWith("sorted-list", Expresso.ConfigModelValue.class,
			new ConfigCollectionValue<ObservableSortedCollection<?>>(ModelTypes.SortedCollection) {
			private ValueContainer<SettableValue<?>, SettableValue<Comparator<Object>>> theComparator;

			@Override
			protected <V> void prepare(TypeToken<V> type, ExpressoQIS session) throws QonfigInterpretationException {
				theComparator = (ValueContainer<SettableValue<?>, SettableValue<Comparator<Object>>>) (ValueContainer<?, ?>) ExpressoBaseV0_1
					.parseSorting(type, session.forChildren("sort").peekFirst()).createContainer();
			}

			@Override
			protected <V> ObservableSortedCollection<?> modify(ObservableCollection<V> collection, ModelSetInstance msi) {
				return collection.flow().sorted(theComparator.get(msi).get()).collectActive(msi.getUntil());
			}
		})
		.createWith("map", Expresso.ConfigModelValue.class, session -> {
			ExpressoQIS exS = wrap(session);
			return new Expresso.ConfigModelValue<ObservableMap<?, ?>, ObservableMap<Object, Object>>() {
				private ModelInstanceType<ObservableMap<?, ?>, ObservableMap<Object, Object>> theType;
				private ConfigFormatProducer<Object> keyFormat;

				@Override
				public ModelInstanceType<ObservableMap<?, ?>, ObservableMap<Object, Object>> getType() {
					if (theType == null) {
						try {
							TypeToken<Object> keyType = ExpressoBaseV0_1.getType(exS, KEY_TYPE_KEY);
							TypeToken<Object> valueType = ExpressoBaseV0_1.getType(exS, VALUE_TYPE_KEY);
							theType = ModelTypes.Map.forType(keyType, valueType);
							keyFormat = parseConfigFormat(exS.asElement("config-map").getAttributeExpression("key-format"), null,
								exS.getExpressoEnv(), keyType);
						} catch (QonfigInterpretationException e) {
							throw new IllegalStateException("Could not parse key type or format", e);
						}
					}
					return theType;
				}

				@Override
				public ObservableMap<Object, Object> create(ObservableConfigValueBuilder<?> config, ModelSetInstance msi) {
					ObservableConfigMapBuilder<Object, Object> mapBuilder = (ObservableConfigMapBuilder<Object, Object>) config
						.asMap(getType().getType(0));
					if (keyFormat != null)
						mapBuilder.withKeyFormat(keyFormat.getFormat(msi));
					return mapBuilder.buildMap(null);
				}
			};
		}).createWith("sorted-map", Expresso.ConfigModelValue.class, session -> {
			throw new QonfigInterpretationException("config-based sorted-maps are not yet supported",
				session.getElement().getPositionInFile(), 0);
		}).createWith("multi-map", Expresso.ConfigModelValue.class, session -> {
			ExpressoQIS exS = wrap(session);
			return new Expresso.ConfigModelValue<ObservableMultiMap<?, ?>, ObservableMultiMap<Object, Object>>() {
				private ModelInstanceType<ObservableMultiMap<?, ?>, ObservableMultiMap<Object, Object>> theType;
				private ConfigFormatProducer<Object> keyFormat;

				@Override
				public ModelInstanceType<ObservableMultiMap<?, ?>, ObservableMultiMap<Object, Object>> getType() {
					if (theType == null) {
						try {
							TypeToken<Object> keyType = ExpressoBaseV0_1.getType(exS, KEY_TYPE_KEY);
							TypeToken<Object> valueType = ExpressoBaseV0_1.getType(exS, VALUE_TYPE_KEY);
							theType = ModelTypes.MultiMap.forType(keyType, valueType);
							keyFormat = parseConfigFormat(exS.asElement("config-map").getAttributeExpression("key-format"), null,
								exS.getExpressoEnv(), keyType);
						} catch (QonfigInterpretationException e) {
							throw new IllegalStateException("Could not parse key type or format", e);
						}
					}
					return theType;
				}

				@Override
				public ObservableMultiMap<Object, Object> create(ObservableConfigValueBuilder<?> config, ModelSetInstance msi) {
					ObservableConfigMapBuilder<Object, Object> mapBuilder = (ObservableConfigMapBuilder<Object, Object>) config
						.asMap(getType().getType(0));
					if (keyFormat != null)
						mapBuilder.withKeyFormat(keyFormat.getFormat(msi));
					return mapBuilder.buildMultiMap(null);
				}
			};
		}).createWith("sorted-multi-map", Expresso.ConfigModelValue.class, session -> {
			throw new QonfigInterpretationException("config-based sorted-multi-maps are not yet supported",
				session.getElement().getPositionInFile(), 0);
		});
	}

	class ConfigModelCreator implements QonfigInterpreterCore.QonfigValueCreator<ObservableModelSet> {
		@Override
		public ObservableModelSet createValue(CoreSession session) throws QonfigInterpretationException {
			ExpressoQIS eqis = wrap(session);
			ObservableModelSet.Builder model = (ObservableModelSet.Builder) eqis.getExpressoEnv().getModels();
			String configName = session.getAttributeText("config-name");
			ValueContainer<SettableValue<?>, SettableValue<BetterFile>> configDir = eqis.getAttributeAsValue("config-dir", BetterFile.class,
				() -> {
					return msi -> {
						String prop = System.getProperty(configName + ".config");
						if (prop != null)
							return ObservableModelSet.literal(BetterFile.at(new NativeFileSource(), prop), prop);
						else
							return ObservableModelSet.literal(BetterFile.at(new NativeFileSource(), "./" + configName), "./" + configName);
					};
				});
			List<String> oldConfigNames = new ArrayList<>(2);
			for (QonfigElement ch : session.getChildren("old-config-name"))
				oldConfigNames.add(ch.getValueText());
			model.with(CONFIG_NAME, ModelTypes.Value.forType(ObservableConfig.class), msi -> {
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
							System.err
							.println("Could not move old configuration " + oldConfigFile.getPath() + " to " + configFile.getPath());
							e.printStackTrace();
						}
					}
				}

				FileBackups backups = session.getAttribute("backup", boolean.class) ? new FileBackups(configFile) : null;

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
									System.err.println("Could not rename " + oldConfigFile.getPath() + " to " + configFile.getPath());
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
					} catch (IOException | SAXException e) {
						System.out.println("Could not read config file " + configFile.getPath());
						e.printStackTrace(System.out);
					}
				}
				boolean[] closingWithoutSave = new boolean[1];
				AppEnvironment storedApp = session.get(ExpressoBaseV0_1.APP_ENVIRONMENT_KEY, AppEnvironment.class);
				AppEnvironment app = storedApp != null ? storedApp : new AppEnvironment() {
					@Override
					public ValueContainer<SettableValue<?>, ? extends ObservableValue<String>> getTitle() {
						return ValueContainer.literal(TypeTokens.get().STRING, "Unspecified Application", "Unspecified Application");
					}

					@Override
					public ValueContainer<SettableValue<?>, ? extends ObservableValue<Image>> getIcon() {
						return ValueContainer.literal(TypeTokens.get().of(Image.class), null, "No Image");
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
			ValueContainer<SettableValue<?>, SettableValue<ObservableConfig>> configV = model.getValue(CONFIG_NAME,
				ModelTypes.Value.forType(ObservableConfig.class));
			for (ExpressoQIS child : eqis.forChildren("value")) {
				String name = child.getAttributeText("model-element", "name");
				String path = child.getAttributeText("config-path");
				if (path == null)
					path = name;
				String typeStr = child.getAttributeText("type");
				TypeToken<Object> type = (TypeToken<Object>) parseType(typeStr, eqis.getExpressoEnv());
				String fPath = path;
				model.withMaker(name, new ValueCreator<Object, Object>() {
					@Override
					public ValueContainer<Object, Object> createContainer() {
						child.put(VALUE_TYPE_KEY, type);
						try {
							ConfigFormatProducer<Object> format = parseConfigFormat(child.getAttributeExpression("format"), null,
								child.getExpressoEnv().with(model, null), type);
							Expresso.ConfigModelValue<Object, Object> configValue = child.interpret(Expresso.ConfigModelValue.class);
							return new ValueContainer<Object, Object>() {
								@Override
								public ModelInstanceType<Object, Object> getType() {
									return configValue.getType();
								}

								@Override
								public Object get(ModelSetInstance msi) {
									ObservableConfig config = configV.get(msi).get();
									ObservableConfig.ObservableConfigValueBuilder<Object> builder = config.asValue(type).at(fPath)
										.until(msi.getUntil());
									if (format != null)
										builder.withFormat(format.getFormat(msi));
									return configValue.create(builder, msi);
								}

								@Override
								public Object forModelCopy(Object value, ModelSetInstance sourceModels, ModelSetInstance newModels) {
									// Should be the same thing, since the config hasn't changed
									return value;
								}

								@Override
								public BetterList<ValueContainer<?, ?>> getCores() {
									return BetterList.of(this);
								}
							};
						} catch (QonfigInterpretationException e) {
							child.error(e.getMessage(), e);
							return null;
						}
					}
				});
			}
			return model;
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

	static void restoreBackup(boolean fromError, ObservableConfig config, FileBackups backups, Runnable onBackup, Runnable onNoBackup,
		AppEnvironment app, boolean[] closingWithoutSave, ModelSetInstance msi) {
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
		ObservableValue<String> title = app.getTitle() == null ? ObservableValue.of("Unnamed Application") : app.getTitle().get(msi);
		ObservableValue<Image> icon = app.getIcon() == null ? ObservableValue.of(Image.class, null) : app.getIcon().get(msi);
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

	static <T> ConfigFormatProducer<T> parseConfigFormat(ObservableExpression formatX, String valueS, ExpressoEnv env, TypeToken<T> type)
		throws QonfigInterpretationException {
		if (formatX != null) {
			ValueContainer<SettableValue<?>, ?> formatVC = formatX.evaluate(ModelTypes.Value.any(), env);
			if (ObservableConfigFormat.class.isAssignableFrom(TypeTokens.getRawType(formatVC.getType().getType(0)))) {
				if (!type.equals(formatVC.getType().getType(0).resolveType(ObservableConfigFormat.class.getTypeParameters()[0]))) {
					System.err.println(formatX + ": Cannot use " + formatVC.getType().getType(0) + " as "
						+ ObservableConfigFormat.class.getSimpleName() + "<" + type + ">");
					return null;
				} else
					return msi -> (ObservableConfigFormat<T>) formatVC.get(msi).get();
			} else if (Format.class.isAssignableFrom(TypeTokens.getRawType(formatVC.getType().getType(0)))) {
				if (!type.equals(formatVC.getType().getType(0).resolveType(Format.class.getTypeParameters()[0]))) {
					System.err.println(formatX + ": Cannot use " + formatVC.getType().getType(0) + " as " + Format.class.getSimpleName()
						+ "<" + type + ">");
					return null;
				} else {
					return msi -> {
						Format<T> format = (Format<T>) formatVC.get(msi).get();
						return ObservableConfigFormat.ofQommonFormat(format, //
							valueS == null ? null : () -> {
								try {
									return format.parse(valueS);
								} catch (ParseException e) {
									System.err.println("WARNING: Could not parse '" + valueS + "' for config format of type " + type);
									e.printStackTrace();
									return null;
								}
							});
					};
				}
			} else {
				System.err.println(formatX + ": Unrecognized format type: " + formatVC.getType());
				return null;
			}
		} else
			return null;
	}

	private ValueCreator<SettableValue<?>, SettableValue<ObservableConfigFormat<Object>>> createSimpleConfigFormat(ExpressoQIS session)
		throws QonfigInterpretationException {
		TypeToken<Object> valueType = (TypeToken<Object>) parseType(session.getAttributeText("type"), session.getExpressoEnv());
		ModelInstanceType<SettableValue<?>, SettableValue<Format<Object>>> formatType = ModelTypes.Value
			.forType(TypeTokens.get().keyFor(Format.class).parameterized(valueType));
		String defaultS = session.getAttributeText("default");
		TypeToken<ObservableConfigFormat<Object>> ocfType = TypeTokens.get().keyFor(ObservableConfigFormat.class)
			.<ObservableConfigFormat<Object>> parameterized(valueType);
		ObservableExpression formatEx = session.getAttributeExpression("format");
		return () -> {
			ValueContainer<SettableValue<?>, SettableValue<Format<Object>>> format;
			Function<ModelSetInstance, Object> defaultValue;
			if (formatEx != null) {
				try {
					format = formatEx.evaluate(formatType, session.getExpressoEnv());
				} catch (QonfigInterpretationException e) {
					session.error(e.getMessage(), e);
					return null;
				}
				defaultValue = defaultS == null ? null : msi -> {
					Format<Object> f = format.get(msi).get();
					try {
						return f.parse(defaultS);
					} catch (ParseException e) {
						System.err.println("Could not parse default value '" + defaultS + "' with format " + f);
						e.printStackTrace();
						return null;
					}
				};
			} else {
				// see if there's an obvious choice by type
				Format<?> f;
				Class<?> type = TypeTokens.get().unwrap(TypeTokens.getRawType(valueType));
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
				else {
					session.error("No default format available for type " + valueType + " -- please specify a format");
					return null;
				}
				format = ValueContainer.literal(formatType, (Format<Object>) f, type.getSimpleName());
				if (defaultS == null)
					defaultValue = null;
				else {
					Object defaultV;
					try {
						defaultV = f.parse(defaultS);
					} catch (ParseException e) {
						session.error(e.getMessage(), e);
						return null;
					}
					if (!(TypeTokens.get().isInstance(valueType, defaultV))) {
						session.error("default value '" + defaultS + ", type " + defaultV.getClass()
						+ ", is incompatible with value type " + valueType);
						return null;
					}
					defaultValue = msi -> defaultV;
				}
			}
			return new ObservableModelSet.AbstractValueContainer<SettableValue<?>, SettableValue<ObservableConfigFormat<Object>>>(
				ModelTypes.Value.forType(ocfType)) {
				@Override
				public SettableValue<ObservableConfigFormat<Object>> get(ModelSetInstance models) {
					SettableValue<Format<Object>> formatObj = format.get(models);
					Supplier<Object> defaultV = defaultValue == null ? null : () -> defaultValue.apply(models);
					return SettableValue.asSettable(formatObj.transform(ocfType, tx -> tx.nullToNull(true)//
						.map(f -> ObservableConfigFormat.ofQommonFormat(f, defaultV))), __ -> "Not reversible");
				}

				@Override
				public SettableValue<ObservableConfigFormat<Object>> forModelCopy(SettableValue<ObservableConfigFormat<Object>> value,
					ModelSetInstance sourceModels, ModelSetInstance newModels) {
					SettableValue<Format<Object>> sourceFormat = format.get(sourceModels);
					SettableValue<Format<Object>> newFormat = format.get(newModels);
					if (sourceFormat == newFormat)
						return value;
					else {
						Supplier<Object> defaultV = defaultValue == null ? null : () -> defaultValue.apply(newModels);
						return SettableValue.asSettable(newFormat.transform(ocfType, tx -> tx.nullToNull(true)//
							.map(f -> ObservableConfigFormat.ofQommonFormat(f, defaultV))), __ -> "Not reversible");
					}
				}

				@Override
				public BetterList<ValueContainer<?, ?>> getCores() {
					return BetterList.of(this);
				}
			};
		};
	}

	private void configureFormats(QonfigInterpreterCore.Builder interpreter) {
		interpreter.delegateToType("format", "type", ValueCreator.class);
		interpreter.delegateToType("validate", "type", ValidationProducer.class);
		interpreter.modifyWith("format",
			(Class<ValueCreator<SettableValue<?>, SettableValue<Format<Object>>>>) (Class<?>) ValueCreator.class,
			this::configureFormatValidation);
		interpreter.createWith("file-source", ValueCreator.class, session -> createFileSource(wrap(session)));
		interpreter.createWith("text", ValueCreator.class, session -> {
			session.put(ExpressoBaseV0_1.VALUE_TYPE_KEY, TypeTokens.get().STRING);
			return ValueCreator.constant(ValueContainer.literal(
				ModelTypes.Value.forType(TypeTokens.get().keyFor(Format.class).<Format<String>> parameterized(String.class)),
				SpinnerFormat.NUMERICAL_TEXT, "text"));
		});
		interpreter.createWith("int-format", ValueCreator.class, session -> {
			session.put(ExpressoBaseV0_1.VALUE_TYPE_KEY, TypeTokens.get().INT);
			SpinnerFormat.IntFormat format = SpinnerFormat.INT;
			String sep = session.getAttributeText("grouping-separator");
			if (sep != null) {
				if (sep.length() != 0)
					System.err.println("WARNING: grouping-separator must be a single character");
				else
					format = format.withGroupingSeparator(sep.charAt(0));
			}
			return ValueCreator.constant(ValueContainer.literal(
				ModelTypes.Value.forType(TypeTokens.get().keyFor(Format.class).<Format<Integer>> parameterized(Integer.class)), format,
				"int"));
		});
		interpreter.createWith("long-format", ValueCreator.class, session -> {
			session.put(ExpressoBaseV0_1.VALUE_TYPE_KEY, TypeTokens.get().LONG);
			SpinnerFormat.LongFormat format = SpinnerFormat.LONG;
			String sep = session.getAttributeText("grouping-separator");
			if (sep != null) {
				if (sep.length() != 0)
					System.err.println("WARNING: grouping-separator must be a single character");
				else
					format = format.withGroupingSeparator(sep.charAt(0));
			}
			return ValueCreator.constant(ValueContainer.literal(
				ModelTypes.Value.forType(TypeTokens.get().keyFor(Format.class).<Format<Long>> parameterized(Long.class)), format, "long"));
		});
		interpreter.createWith("double", ValueCreator.class, session -> createDoubleFormat(wrap(session)));
		interpreter.createWith("instant", ValueCreator.class, session -> createInstantFormat(wrap(session)));
		interpreter.createWith("file", ValueCreator.class, session -> createFileFormat(wrap(session)));
		interpreter.createWith("regex-format", ValueCreator.class, session -> {
			session.put(ExpressoBaseV0_1.VALUE_TYPE_KEY, TypeTokens.get().of(Pattern.class));
			return ValueCreator.literal(TypeTokens.get().keyFor(Format.class).<Format<Pattern>> parameterized(Pattern.class),
				Format.PATTERN, "regex-format");
		});
		interpreter.createWith("regex-format-string", ValueCreator.class, session -> {
			session.put(ExpressoBaseV0_1.VALUE_TYPE_KEY, TypeTokens.get().STRING);
			return ValueCreator.literal(TypeTokens.get().keyFor(Format.class).<Format<String>> parameterized(String.class), //
				Format.validate(Format.TEXT, str -> {
					if (str == null || str.isEmpty())
						return null; // That's fine
					try {
						Pattern.compile(str);
						return null;
					} catch (PatternSyntaxException e) {
						return e.getMessage();
					}
				}), "regex-format-string");
		});
		interpreter.createWith("regex-validation", ValidationProducer.class, session -> {
			return () -> {
				ValueContainer<SettableValue<?>, SettableValue<Pattern>> patternC;
				try {
					patternC = session.as(ExpressoQIS.class).getAttribute("pattern", ModelTypes.Value.forType(Pattern.class), null);
				} catch (QonfigInterpretationException e) {
					throw new IllegalStateException("Could not obtain pattern", e);
				}
				class PatternValidator implements Function<Object, String> {
					final SettableValue<Pattern> patternV;

					PatternValidator(SettableValue<Pattern> patternV) {
						this.patternV = patternV;
					}

					@Override
					public String apply(Object v) {
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
				return new Validation<Object>() {
					@Override
					public Function<Object, String> getTest(ModelSetInstance models) {
						return new PatternValidator(patternC.get(models));
					}

					@Override
					public Function<Object, String> forModelCopy(Function<Object, String> validator, ModelSetInstance sourceModels,
						ModelSetInstance newModels) {
						SettableValue<Pattern> oldPattern = ((PatternValidator) validator).patternV;
						SettableValue<Pattern> newPattern = patternC.forModelCopy(oldPattern, sourceModels, newModels);
						if (newPattern == oldPattern)
							return validator;
						return new PatternValidator(newPattern);
					}

					@Override
					public BetterList<ValueContainer<?, ?>> getCores() {
						return patternC.getCores();
					}
				};
			};
		});
		interpreter.createWith("filter-validation", ValidationProducer.class,
			session -> createFilterValidation(session.as(ExpressoQIS.class)));
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

	private <T> ValueCreator<SettableValue<?>, SettableValue<Format<T>>> configureFormatValidation(
		ValueCreator<SettableValue<?>, SettableValue<Format<T>>> formatCreator, CoreSession session, Object prepared)
			throws QonfigInterpretationException {
		List<ValidationProducer<T>> validationCreators = session.interpretChildren("validate", ValidationProducer.class);
		if (validationCreators.isEmpty())
			return formatCreator;
		return ValueCreator.name(formatCreator::toString, () -> {
			ValueContainer<SettableValue<?>, SettableValue<Format<T>>> formatContainer = formatCreator.createContainer();
			List<Validation<T>> validationContainers;
			validationContainers = validationCreators.stream().map(creator -> creator.createValidator()).collect(Collectors.toList());
			class ValidatedFormat extends SettableValue.WrappingSettableValue<Format<T>> {
				private final SettableValue<Format<T>> theFormat;
				private final List<Function<T, String>> theValidation;

				public ValidatedFormat(SettableValue<Format<T>> format, List<Function<T, String>> validation) {
					super(createWrappedValidatedFormat(format, validation));
					theFormat = format;
					theValidation = validation;
				}

				ValidatedFormat forModelCopy(ModelSetInstance sourceModels, ModelSetInstance newModels) {
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
			class ValidatedFormatContainer implements ValueContainer<SettableValue<?>, SettableValue<Format<T>>> {
				private final ValueContainer<SettableValue<?>, SettableValue<Format<T>>> theFormatContainer;
				private final List<Validation<T>> theValidationContainers;

				ValidatedFormatContainer(ValueContainer<SettableValue<?>, SettableValue<Format<T>>> format,
					List<Validation<T>> validation) {
					theFormatContainer = format;
					theValidationContainers = validation;
				}

				@Override
				public ModelInstanceType<SettableValue<?>, SettableValue<Format<T>>> getType() {
					return formatContainer.getType();
				}

				@Override
				public SettableValue<Format<T>> get(ModelSetInstance models) {
					return new ValidatedFormat(//
						theFormatContainer.get(models), //
						theValidationContainers.stream().map(val -> val.getTest(models)).collect(Collectors.toList()));
				}

				@Override
				public SettableValue<Format<T>> forModelCopy(SettableValue<Format<T>> formatV, ModelSetInstance sourceModels,
					ModelSetInstance newModels) {
					return ((ValidatedFormat) formatV).forModelCopy(sourceModels, newModels);
				}

				@Override
				public BetterList<ValueContainer<?, ?>> getCores() {
					return BetterList.of(Stream.concat(//
						formatContainer.getCores().stream(), //
						validationContainers.stream().flatMap(vc -> vc.getCores().stream())));
				}
			}
			return new ValidatedFormatContainer(formatContainer, validationContainers);
		});
	}

	private ValueCreator<SettableValue<?>, SettableValue<BetterFile.FileDataSource>> createFileSource(ExpressoQIS session)
		throws QonfigInterpretationException {
		session.put(ExpressoBaseV0_1.VALUE_TYPE_KEY, TypeTokens.get().of(BetterFile.FileDataSource.class));
		Supplier<ValueContainer<SettableValue<?>, SettableValue<FileDataSource>>> source;
		switch (session.getAttributeText("type")) {
		case "native":
			source = () -> ValueContainer.literal(TypeTokens.get().of(FileDataSource.class), new NativeFileSource(), "native-file-source");
			break;
		case "sftp":
			throw new UnsupportedOperationException("Not yet implemented");
		default:
			throw new IllegalArgumentException("Unrecognized file-source type: " + session.getAttributeText("type"));
		}
		if (!session.getChildren("archive").isEmpty()) {
			Set<String> archiveMethodStrs = new HashSet<>();
			List<ArchiveEnabledFileSource.FileArchival> archiveMethods = new ArrayList<>(5);
			for (ExpressoQIS archive : session.forChildren("archive")) {
				String type = archive.getAttributeText("type");
				if (!archiveMethodStrs.add(type))
					continue;
				switch (type) {
				case "zip":
					archiveMethods.add(new ArchiveEnabledFileSource.ZipCompression());
					break;
				case "tar":
					archiveMethods.add(new ArchiveEnabledFileSource.TarArchival());
					break;
				case "gz":
					archiveMethods.add(new ArchiveEnabledFileSource.GZipCompression());
					break;
				default:
					System.err.println("Unrecognized archive-method: " + type);
				}
			}
			ObservableExpression mad = session.getAttributeExpression("max-archive-depth");
			Supplier<ValueContainer<SettableValue<?>, SettableValue<Integer>>> maxZipDepth = () -> {
				try {
					return mad.evaluate(ModelTypes.Value.forType(int.class), session.getExpressoEnv());
				} catch (QonfigInterpretationException e) {
					session.error(e.getMessage(), e);
					return ValueContainer.literal(TypeTokens.get().INT, 10, "10");
				}
			};

			Supplier<ValueContainer<SettableValue<?>, SettableValue<FileDataSource>>> root = source;
			source = () -> {
				ValueContainer<SettableValue<?>, SettableValue<Integer>> mzd = maxZipDepth == null ? null : maxZipDepth.get();
				return ValueContainer.of(ModelTypes.Value.forType(FileDataSource.class), modelSet -> {
					SettableValue<Integer> zd = mzd == null ? null : mzd.get(modelSet);
					return root.get().get(modelSet).transformReversible(FileDataSource.class, //
						tx -> tx.nullToNull(true).map(fs -> {
							ArchiveEnabledFileSource aefs = new ArchiveEnabledFileSource(fs).withArchival(archiveMethods);
							if (zd != null) {
								zd.takeUntil(modelSet.getUntil()).changes().act(evt -> {
									if (evt.getNewValue() != null)
										aefs.setMaxArchiveDepth(evt.getNewValue());
								});
							}
							return aefs;
						}).replaceSource(aefs -> null, rev -> rev.disableWith(tv -> "Not settable")));
				});
			};
		}
		Supplier<ValueContainer<SettableValue<?>, SettableValue<FileDataSource>>> fSource = source;
		return () -> {
			ValueContainer<SettableValue<?>, SettableValue<FileDataSource>> fSource2 = fSource.get();
			return new ObservableModelSet.AbstractValueContainer<SettableValue<?>, SettableValue<BetterFile.FileDataSource>>(
				ModelTypes.Value.forType(FileDataSource.class)) {
				@Override
				public SettableValue<FileDataSource> get(ModelSetInstance models) {
					return fSource2.get(models);
				}

				@Override
				public SettableValue<FileDataSource> forModelCopy(SettableValue<FileDataSource> value, ModelSetInstance sourceModels,
					ModelSetInstance newModels) {
					return fSource2.forModelCopy(value, sourceModels, newModels);
				}

				@Override
				public BetterList<ValueContainer<?, ?>> getCores() {
					return BetterList.of(this);
				}
			};
		};
	}

	private ValueCreator<SettableValue<?>, SettableValue<Format<Double>>> createDoubleFormat(ExpressoQIS session)
		throws QonfigInterpretationException {
		session.put(ExpressoBaseV0_1.VALUE_TYPE_KEY, TypeTokens.get().DOUBLE);
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
					session.warn("Both 'metric-prefixes' and 'metric-prefixes-p2' specified");
				builder.withMetricPrefixes();
			} else if (withMetricPrefixesP2)
				builder.withMetricPrefixesPower2();
			for (ExpressoQIS prefix : prefixes) {
				String prefixName = prefix.getAttributeText("name");
				String expS = prefix.getAttributeText("exp");
				String multS = prefix.getAttributeText("mult");
				if (expS != null) {
					if (multS != null)
						session.warn("Both 'exp' and 'mult' specified for prefix '" + prefixName + "'");
					builder.withPrefix(prefixName, Integer.parseInt(expS));
				} else if (multS != null)
					builder.withPrefix(prefixName, Double.parseDouble(multS));
				else
					session.warn("Neither 'exp' nor 'mult' specified for prefix '" + prefixName + "'");
			}
		} else {
			if (withMetricPrefixes)
				session.warn("'metric-prefixes' specified without a unit");
			if (withMetricPrefixesP2)
				session.warn("'metric-prefixes-p2' specified without a unit");
			if (!prefixes.isEmpty())
				session.warn("prefixes specified without a unit");
		}
		return ValueCreator.literal(TypeTokens.get().keyFor(Format.class).<Format<Double>> parameterized(Double.class), builder.build(),
			"double");
	}

	private ValueCreator<SettableValue<?>, SettableValue<Format<Instant>>> createInstantFormat(ExpressoQIS session)
		throws QonfigInterpretationException {
		session.put(ExpressoBaseV0_1.VALUE_TYPE_KEY, TypeTokens.get().of(Instant.class));
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
			session.warn("Unrecognized instant resolution: '" + session.getAttributeText("max-resolution"));
		}
		options = options.with24HourFormat(session.getAttribute("format-24h", boolean.class));
		String rteS = session.getAttributeText("relative-eval-type");
		try {
			options = options.withEvaluationType(TimeUtils.RelativeInstantEvaluation.valueOf(rteS));
		} catch (IllegalArgumentException e) {
			session.warn("Unrecognized relative evaluation type: '" + rteS);
		}
		TimeEvaluationOptions fOptions = options;
		TypeToken<Instant> instantType = TypeTokens.get().of(Instant.class);
		TypeToken<Format<Instant>> formatType = TypeTokens.get().keyFor(Format.class).<Format<Instant>> parameterized(instantType);
		ModelInstanceType<SettableValue<?>, SettableValue<Instant>> instantModelType = ModelTypes.Value.forType(instantType);
		ModelInstanceType<SettableValue<?>, SettableValue<Format<Instant>>> formatInstanceType = ModelTypes.Value.forType(formatType);
		ObservableExpression relativeV = session.getAttributeExpression("relative-to");
		return () -> {
			ValueContainer<SettableValue<?>, SettableValue<Instant>> relativeTo = null;
			if (relativeV != null) {
				try {
					relativeTo = relativeV.evaluate(instantModelType, session.getExpressoEnv());
				} catch (QonfigInterpretationException e) {
					session.error(e.getMessage(), e);
				}
			}
			if (relativeTo == null) {
				relativeTo = ValueContainer.of(instantModelType, msi -> new SettableValue<Instant>() {
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
			ValueContainer<SettableValue<?>, SettableValue<Instant>> relativeTo2 = relativeTo;
			return new ObservableModelSet.AbstractValueContainer<SettableValue<?>, SettableValue<Format<Instant>>>(formatInstanceType) {
				@Override
				public SettableValue<Format<Instant>> get(ModelSetInstance models) {
					return ObservableModelSet.literal(SpinnerFormat.flexDate(relativeTo2.get(models), dayFormat, __ -> fOptions),
						"instant");
				}

				@Override
				public SettableValue<Format<Instant>> forModelCopy(SettableValue<Format<Instant>> value, ModelSetInstance sourceModels,
					ModelSetInstance newModels) {
					return ObservableModelSet.literal(SpinnerFormat.flexDate(relativeTo2.get(newModels), dayFormat, __ -> fOptions),
						"instant");
				}

				@Override
				public BetterList<ValueContainer<?, ?>> getCores() {
					return BetterList.of(this);
				}
			};
		};
	}

	private ValueCreator<SettableValue<?>, SettableValue<Format<BetterFile>>> createFileFormat(ExpressoQIS session)
		throws QonfigInterpretationException {
		session.put(ExpressoBaseV0_1.VALUE_TYPE_KEY, TypeTokens.get().of(BetterFile.class));
		ObservableExpression fileSourceEx = session.getAttributeExpression("file-source");
		ObservableExpression workingDirEx = session.getAttributeExpression("working-dir");
		boolean allowEmpty = session.getAttribute("allow-empty", boolean.class);
		return () -> {
			ValueContainer<SettableValue<?>, SettableValue<BetterFile.FileDataSource>> fileSource;
			ValueContainer<SettableValue<?>, SettableValue<String>> workingDir;
			try {
				if (fileSourceEx != null)
					fileSource = fileSourceEx.evaluate(//
						ModelTypes.Value.forType(BetterFile.FileDataSource.class), session.getExpressoEnv());
				else
					fileSource = ValueContainer.literal(ModelTypes.Value.forType(BetterFile.FileDataSource.class), new NativeFileSource(),
						"native");
			} catch (QonfigInterpretationException e) {
				session.error(e.getMessage(), e);
				fileSource = ValueContainer.literal(ModelTypes.Value.forType(BetterFile.FileDataSource.class), new NativeFileSource(),
					"native");
			}
			try {
				if (workingDirEx != null)
					workingDir = workingDirEx.evaluate(ModelTypes.Value.forType(String.class), session.getExpressoEnv());
				else
					workingDir = null;
			} catch (QonfigInterpretationException e) {
				session.error(e.getMessage(), e);
				workingDir = null;
			}
			ValueContainer<SettableValue<?>, SettableValue<BetterFile.FileDataSource>> fileSource2 = fileSource;
			ValueContainer<SettableValue<?>, SettableValue<String>> workingDir2 = workingDir;
			TypeToken<Format<BetterFile>> fileFormatType = TypeTokens.get().keyFor(Format.class)
				.<Format<BetterFile>> parameterized(BetterFile.class);
			return new ObservableModelSet.AbstractValueContainer<SettableValue<?>, SettableValue<Format<BetterFile>>>(
				ModelTypes.Value.forType(fileFormatType)) {
				@Override
				public SettableValue<Format<BetterFile>> get(ModelSetInstance models) {
					SettableValue<BetterFile.FileDataSource> fds = fileSource2.get(models);
					SettableValue<String> workingDirF = workingDir2 == null ? SettableValue.of(String.class, ".", "Not Settable")
						: workingDir2.get(models);
					return SettableValue.asSettable(//
						fds.transform(fileFormatType, tx -> tx.map(fs -> {
							BetterFile workingDirFile = BetterFile.at(fs, workingDirF.get());
							return new BetterFile.FileFormat(fs, workingDirFile, allowEmpty);
						})), //
						__ -> "Not reversible");
				}

				@Override
				public SettableValue<Format<BetterFile>> forModelCopy(SettableValue<Format<BetterFile>> value,
					ModelSetInstance sourceModels, ModelSetInstance newModels) {
					SettableValue<BetterFile.FileDataSource> sourceFDS = fileSource2.get(sourceModels);
					SettableValue<BetterFile.FileDataSource> newFDS = fileSource2.get(newModels);
					SettableValue<String> sourceWorkingDir = workingDir2 == null ? SettableValue.of(String.class, ".", "Not Settable")
						: workingDir2.get(sourceModels);
					SettableValue<String> newWorkingDir = workingDir2 == null ? sourceWorkingDir : workingDir2.get(newModels);
					if (sourceFDS == newFDS && sourceWorkingDir == newWorkingDir)
						return value;
					else {
						return SettableValue.asSettable(//
							newFDS.transform(fileFormatType, tx -> tx.map(fs -> {
								BetterFile workingDirFile = BetterFile.at(fs, newWorkingDir.get());
								return new BetterFile.FileFormat(fs, workingDirFile, allowEmpty);
							})), //
							__ -> "Not reversible");
					}
				}

				@Override
				public BetterList<ValueContainer<?, ?>> getCores() {
					return BetterList.of(this);
				}
			};
		};
	}

	private <V> ValidationProducer<V> createFilterValidation(ExpressoQIS session) throws QonfigInterpretationException {
		String filterValue = session.getAttributeText("filter-value-name");
		return () -> {
			ModelInstanceType<SettableValue<?>, SettableValue<V>> type;
			try {
				type = ModelTypes.Value.forType(ExpressoBaseV0_1.getType(session, ExpressoBaseV0_1.VALUE_TYPE_KEY));
			} catch (QonfigInterpretationException e) {
				throw new IllegalStateException("Could not determine filter type", e);
			}
			DynamicModelValue.satisfyDynamicValueType(filterValue, session.getExpressoEnv().getModels(), type);
			ValueContainer<SettableValue<?>, SettableValue<String>> filter;
			try {
				filter = session.getAttribute("test", ModelTypes.Value.STRING, null);
			} catch (QonfigInterpretationException e) {
				ValueContainer<SettableValue<?>, SettableValue<Boolean>> bFilter;
				try {
					bFilter = session.getAttribute("test", ModelTypes.Value.BOOLEAN, null);
					filter = bFilter.map(ModelTypes.Value.STRING, bv -> SettableValue.asSettable(//
						bv.map(String.class, b -> b ? null : "Filter does not pass"), __ -> "Not Settable"));
				} catch (QonfigInterpretationException e2) {
					throw new IllegalStateException("Could not evaluate filter test", e);
				}
			}
			class FilterValidator implements Function<V, String> {
				final SettableValue<V> value;
				final SettableValue<String> testValue;

				public FilterValidator(SettableValue<V> value, SettableValue<String> testValue) {
					this.value = value;
					this.testValue = testValue;
				}

				@Override
				public String apply(V t) {
					value.set(t, null);
					return testValue.get();
				}
			}
			ValueContainer<SettableValue<?>, SettableValue<String>> fFilter = filter;
			session.interpretLocalModel();
			return new Validation<V>() {
				@Override
				public Function<V, String> getTest(ModelSetInstance models) {
					models = session.wrapLocal(models);
					SettableValue<V> value = SettableValue.build((TypeToken<V>) type.getType(0)).build();
					DynamicModelValue.satisfyDynamicValue(filterValue, type, models, value);
					SettableValue<String> testValue = fFilter.get(models);
					return new FilterValidator(value, testValue);
				}

				@Override
				public Function<V, String> forModelCopy(Function<V, String> validator, ModelSetInstance sourceModels,
					ModelSetInstance newModels) {
					SettableValue<String> oldTest = ((FilterValidator) validator).testValue;
					SettableValue<String> newTest = fFilter.forModelCopy(oldTest, sourceModels, newModels);
					if (oldTest == newTest)
						return validator;
					return new FilterValidator(((FilterValidator) validator).value, newTest);
				}

				@Override
				public BetterList<ValueContainer<?, ?>> getCores() {
					return fFilter.getCores();
				}
			};
		};
	}
}
