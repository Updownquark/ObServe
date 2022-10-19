package org.observe.expresso;

import static org.observe.expresso.ExpressoBaseV0_1.KEY_TYPE_KEY;
import static org.observe.expresso.ExpressoBaseV0_1.VALUE_TYPE_KEY;
import static org.observe.expresso.ExpressoBaseV0_1.parseComparator;
import static org.observe.expresso.ExpressoBaseV0_1.parseType;
import static org.observe.expresso.ExpressoBaseV0_1.parseValue;

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

import javax.swing.JFrame;

import org.observe.ObservableValue;
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
import org.qommons.ThreadConstraint;
import org.qommons.TimeUtils;
import org.qommons.TimeUtils.TimeEvaluationOptions;
import org.qommons.Version;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterSortedSet;
import org.qommons.config.QommonsConfig;
import org.qommons.config.QonfigElement;
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
				TypeToken<Object> type = session.get(VALUE_TYPE_KEY, TypeToken.class);
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
			TypeToken<Object> type = session.get(VALUE_TYPE_KEY, TypeToken.class);
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
				defaultV = defaultX == null ? null : parseValue(exS.getExpressoEnv(), type, defaultX);
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
						built.set(defaultV.apply(msi).get(), null);
						format[0] = null;
					}
					return built;
				}
			};
		}).createWith("value-set", Expresso.ConfigModelValue.class, session -> {
			TypeToken<Object> type = (TypeToken<Object>) session.get(VALUE_TYPE_KEY);
			return new AbstractConfigModelValue<ObservableValueSet<?>, ObservableValueSet<Object>>(ModelTypes.ValueSet.forType(type)) {
				@Override
				public ObservableValueSet<Object> create(ObservableConfigValueBuilder<?> config, ModelSetInstance msi) {
					return (ObservableValueSet<Object>) config.buildEntitySet(null);
				}
			};
		}).createWith("list", Expresso.ConfigModelValue.class, new ConfigCollectionValue<ObservableCollection<?>>(ModelTypes.Collection) {
			@Override
			protected <V> ObservableCollection<V> modify(ObservableCollection<V> collection, ModelSetInstance msi) {
				return collection;
			}
		}).createWith("set", Expresso.ConfigModelValue.class, new ConfigCollectionValue<ObservableSet<?>>(ModelTypes.Set) {
			@Override
			protected <V> ObservableSet<V> modify(ObservableCollection<V> collection, ModelSetInstance msi) {
				return collection.flow().distinct().collectActive(msi.getUntil());
			}
		}).createWith("sorted-set", Expresso.ConfigModelValue.class, new ConfigCollectionValue<ObservableSortedSet<?>>(ModelTypes.SortedSet) {
			private Function<ModelSetInstance, Comparator<Object>> theComparator;

			@Override
			protected <V> void prepare(TypeToken<V> type, ExpressoQIS session) throws QonfigInterpretationException {
				theComparator = parseComparator((TypeToken<Object>) type, session.getAttributeExpression("sort-with"),
					session.getExpressoEnv());
			}

			@Override
			protected <V> ObservableSortedSet<V> modify(ObservableCollection<V> collection, ModelSetInstance msi) {
				return collection.flow().distinctSorted(theComparator.apply(msi), false).collectActive(msi.getUntil());
			}
		}).createWith("sorted-list", Expresso.ConfigModelValue.class,
			new ConfigCollectionValue<ObservableSortedCollection<?>>(ModelTypes.SortedCollection) {
			private Function<ModelSetInstance, Comparator<Object>> theComparator;

			@Override
			protected <V> void prepare(TypeToken<V> type, ExpressoQIS session) throws QonfigInterpretationException {
				theComparator = parseComparator((TypeToken<Object>) type,
					session.getAttributeExpression("sort-with"), session.getExpressoEnv());
			}

			@Override
			protected <V> ObservableSortedCollection<?> modify(ObservableCollection<V> collection, ModelSetInstance msi) {
				return collection.flow().sorted(theComparator.apply(msi)).collectActive(msi.getUntil());
			}
		})
		.createWith("map", Expresso.ConfigModelValue.class, session -> {
			ExpressoQIS exS = wrap(session);
			TypeToken<Object> keyType = session.get(KEY_TYPE_KEY, TypeToken.class);
			TypeToken<Object> valueType = session.get(VALUE_TYPE_KEY, TypeToken.class);
			ConfigFormatProducer<Object> keyFormat = parseConfigFormat(exS.asElement("config-map").getAttributeExpression("key-format"),
				null, exS.getExpressoEnv(), keyType);
			return new AbstractConfigModelValue<ObservableMap<?, ?>, ObservableMap<Object, Object>>(
				ModelTypes.Map.forType(keyType, valueType)) {
				@Override
				public ObservableMap<Object, Object> create(ObservableConfigValueBuilder<?> config, ModelSetInstance msi) {
					ObservableConfigMapBuilder<Object, Object> mapBuilder = (ObservableConfigMapBuilder<Object, Object>) config
						.asMap(keyType);
					if (keyFormat != null)
						mapBuilder.withKeyFormat(keyFormat.getFormat(msi));
					return mapBuilder.buildMap(null);
				}
			};
		}).createWith("sorted-map", Expresso.ConfigModelValue.class, session -> {
			throw new QonfigInterpretationException("config-based sorted-maps are not yet supported");
		}).createWith("multi-map", Expresso.ConfigModelValue.class, session -> {
			ExpressoQIS exS = wrap(session);
			TypeToken<Object> keyType = session.get(KEY_TYPE_KEY, TypeToken.class);
			TypeToken<Object> valueType = session.get(VALUE_TYPE_KEY, TypeToken.class);
			ConfigFormatProducer<Object> keyFormat = parseConfigFormat(exS.asElement("config-map").getAttributeExpression("key-format"),
				null, exS.getExpressoEnv(), keyType);
			return new AbstractConfigModelValue<ObservableMultiMap<?, ?>, ObservableMultiMap<Object, Object>>(
				ModelTypes.MultiMap.forType(keyType, valueType)) {
				@Override
				public ObservableMultiMap<Object, Object> create(ObservableConfigValueBuilder<?> config, ModelSetInstance msi) {
					return (ObservableMultiMap<Object, Object>) config.asMap(keyType).withKeyFormat(keyFormat.getFormat(msi))
						.buildMultiMap(null);
				}
			};
		}).createWith("sorted-multi-map", Expresso.ConfigModelValue.class, session -> {
			throw new QonfigInterpretationException("config-based sorted-multi-maps are not yet supported");
		});
	}

	class ConfigModelCreator implements QonfigInterpreterCore.QonfigValueCreator<ObservableModelSet> {
		@Override
		public ObservableModelSet createValue(CoreSession session) throws QonfigInterpretationException {
			ExpressoQIS eqis = wrap(session);
			ObservableModelSet.Builder model = (ObservableModelSet.Builder) eqis.getExpressoEnv().getModels();
			String configName = session.getAttributeText("config-name");
			Function<ModelSetInstance, SettableValue<BetterFile>> configDir = eqis.getAttributeAsValue("config-dir", BetterFile.class,
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
			model.setModelConfiguration(msi -> {
				BetterFile configDirFile = configDir == null ? null : configDir.apply(msi).get();
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
				AppEnvironment app = null; // TODO
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
				return config;
			});
			for (ExpressoQIS child : eqis.forChildren("value")) {
				ExpressoQIS childSession = child.asElement("config-model-value");
				String name = childSession.getAttributeText("model-element", "name");
				String path = childSession.getAttributeText("config-path");
				if (path == null)
					path = name;
				String typeStr = childSession.getAttributeText("type");
				TypeToken<Object> type = (TypeToken<Object>) parseType(typeStr, eqis.getExpressoEnv());
				String fPath = path;
				model.withMaker(name, new ValueCreator<Object, Object>() {
					@Override
					public ValueContainer<Object, Object> createValue() {
						childSession.put(VALUE_TYPE_KEY, type);
						try {
							ConfigFormatProducer<Object> format = parseConfigFormat(
								childSession.getAttributeExpression("format"), null,
								childSession.getExpressoEnv().with(model, null), type);
							Expresso.ConfigModelValue<Object, Object> configValue = child.interpret(Expresso.ConfigModelValue.class);
							return new ValueContainer<Object, Object>() {
								@Override
								public ModelInstanceType<Object, Object> getType() {
									return configValue.getType();
								}

								@Override
								public Object get(ModelSetInstance msi) {
									ObservableConfig config = (ObservableConfig) msi.getModelConfiguration();
									ObservableConfig.ObservableConfigValueBuilder<Object> builder = config.asValue(type).at(fPath)
										.until(msi.getUntil());
									if (format != null)
										builder.withFormat(format.getFormat(msi));
									return configValue.create(builder, msi);
								}
							};
						} catch (QonfigInterpretationException e) {
							childSession.withError(e.getMessage(), e);
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
		ObservableValue<String> title = app.getTitle().apply(msi);
		ObservableValue<Image> icon = app.getIcon().apply(msi);
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
					return msi -> (ObservableConfigFormat<T>) formatVC.apply(msi).get();
			} else if (Format.class.isAssignableFrom(TypeTokens.getRawType(formatVC.getType().getType(0)))) {
				if (!type.equals(formatVC.getType().getType(0).resolveType(Format.class.getTypeParameters()[0]))) {
					System.err.println(formatX + ": Cannot use " + formatVC.getType().getType(0) + " as " + Format.class.getSimpleName()
						+ "<" + type + ">");
					return null;
				} else {
					return msi -> {
						Format<T> format = (Format<T>) formatVC.apply(msi).get();
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

	private ValueCreator<SettableValue<?>, SettableValue<ObservableConfigFormat<Object>>> createSimpleConfigFormat(
		ExpressoQIS session)
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
					session.withError(e.getMessage(), e);
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
					session.withError("No default format available for type " + valueType + " -- please specify a format");
					return null;
				}
				format = ObservableModelSet.literalContainer(formatType, (Format<Object>) f, type.getSimpleName());
				if (defaultS == null)
					defaultValue = null;
				else {
					Object defaultV;
					try {
						defaultV = f.parse(defaultS);
					} catch (ParseException e) {
						session.withError(e.getMessage(), e);
						return null;
					}
					if (!(TypeTokens.get().isInstance(valueType, defaultV))) {
						session.withError("default value '" + defaultS + ", type " + defaultV.getClass()
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
			};
		};
	}

	private void configureFormats(QonfigInterpreterCore.Builder interpreter) {
		interpreter.delegateToType("format", "type", ValueCreator.class);
		interpreter.createWith("file-source", ValueCreator.class, session -> createFileSource(wrap(session)));
		interpreter.createWith("text", ValueCreator.class, session -> {
			return ValueCreator.constant(ObservableModelSet.literalContainer(
				ModelTypes.Value.forType(TypeTokens.get().keyFor(Format.class).<Format<String>> parameterized(String.class)),
				SpinnerFormat.NUMERICAL_TEXT, "text"));
		});
		interpreter.createWith("int-format", ValueCreator.class, session -> {
			SpinnerFormat.IntFormat format = SpinnerFormat.INT;
			String sep = session.getAttributeText("grouping-separator");
			if (sep != null) {
				if (sep.length() != 0)
					System.err.println("WARNING: grouping-separator must be a single character");
				else
					format = format.withGroupingSeparator(sep.charAt(0));
			}
			return ValueCreator.constant(ObservableModelSet.literalContainer(
				ModelTypes.Value.forType(TypeTokens.get().keyFor(Format.class).<Format<Integer>> parameterized(Integer.class)), format,
				"int"));
		});
		interpreter.createWith("long-format", ValueCreator.class, session -> {
			SpinnerFormat.LongFormat format = SpinnerFormat.LONG;
			String sep = session.getAttributeText("grouping-separator");
			if (sep != null) {
				if (sep.length() != 0)
					System.err.println("WARNING: grouping-separator must be a single character");
				else
					format = format.withGroupingSeparator(sep.charAt(0));
			}
			return ValueCreator.constant(ObservableModelSet.literalContainer(
				ModelTypes.Value.forType(TypeTokens.get().keyFor(Format.class).<Format<Long>> parameterized(Long.class)), format, "long"));
		});
		interpreter.createWith("double", ValueCreator.class, session -> createDoubleFormat(wrap(session)));
		interpreter.createWith("instant", ValueCreator.class, session -> createInstantFormat(wrap(session)));
		interpreter.createWith("file", ValueCreator.class, session -> createFileFormat(wrap(session)));
		interpreter.createWith("regex-format", ValueCreator.class, session -> {
			return ValueCreator.literal(TypeTokens.get().keyFor(Format.class).<Format<Pattern>> parameterized(Pattern.class),
				Format.PATTERN, "regex-format");
		});
		interpreter.createWith("regex-format-string", ValueCreator.class, session -> {
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
	}

	private ValueCreator<SettableValue<?>, SettableValue<BetterFile.FileDataSource>> createFileSource(ExpressoQIS session)
		throws QonfigInterpretationException {
		Supplier<Function<ModelSetInstance, SettableValue<FileDataSource>>> source;
		switch (session.getAttributeText("type")) {
		case "native":
			source = () -> modelSet -> ObservableModelSet.literal(new NativeFileSource(), "native-file-source");
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
			Supplier<Function<ModelSetInstance, SettableValue<Integer>>> maxZipDepth = () -> {
				try {
					return mad.evaluate(ModelTypes.Value.forType(int.class), session.getExpressoEnv());
				} catch (QonfigInterpretationException e) {
					session.withError(e.getMessage(), e);
					return __ -> ObservableModelSet.literal(TypeTokens.get().INT, 10, "10");
				}
			};

			Supplier<Function<ModelSetInstance, SettableValue<FileDataSource>>> root = source;
			source = () -> {
				Function<ModelSetInstance, SettableValue<Integer>> mzd = maxZipDepth == null ? null : maxZipDepth.get();
				return modelSet -> {
					SettableValue<Integer> zd = mzd == null ? null : mzd.apply(modelSet);
					return root.get().apply(modelSet).transformReversible(FileDataSource.class, //
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
				};
			};
		}
		Supplier<Function<ModelSetInstance, SettableValue<FileDataSource>>> fSource = source;
		return () -> {
			Function<ModelSetInstance, SettableValue<FileDataSource>> fSource2 = fSource.get();
			return new ObservableModelSet.AbstractValueContainer<SettableValue<?>, SettableValue<BetterFile.FileDataSource>>(
				ModelTypes.Value.forType(FileDataSource.class)) {
				@Override
				public SettableValue<FileDataSource> get(ModelSetInstance models) {
					return fSource2.apply(models);
				}
			};
		};
	}

	private ValueCreator<SettableValue<?>, SettableValue<Format<Double>>> createDoubleFormat(ExpressoQIS session)
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
					session.withWarning("Both 'metric-prefixes' and 'metric-prefixes-p2' specified");
				builder.withMetricPrefixes();
			} else if (withMetricPrefixesP2)
				builder.withMetricPrefixesPower2();
			for (ExpressoQIS prefix : prefixes) {
				String prefixName = prefix.getAttributeText("name");
				String expS = prefix.getAttributeText("exp");
				String multS = prefix.getAttributeText("mult");
				if (expS != null) {
					if (multS != null)
						session.withWarning("Both 'exp' and 'mult' specified for prefix '" + prefixName + "'");
					builder.withPrefix(prefixName, Integer.parseInt(expS));
				} else if (multS != null)
					builder.withPrefix(prefixName, Double.parseDouble(multS));
				else
					session.withWarning("Neither 'exp' nor 'mult' specified for prefix '" + prefixName + "'");
			}
		} else {
			if (withMetricPrefixes)
				session.withWarning("'metric-prefixes' specified without a unit");
			if (withMetricPrefixesP2)
				session.withWarning("'metric-prefixes-p2' specified without a unit");
			if (!prefixes.isEmpty())
				session.withWarning("prefixes specified without a unit");
		}
		return ValueCreator.literal(TypeTokens.get().keyFor(Format.class).<Format<Double>> parameterized(Double.class), builder.build(),
			"double");
	}

	private ValueCreator<SettableValue<?>, SettableValue<Format<Instant>>> createInstantFormat(ExpressoQIS session)
		throws QonfigInterpretationException {
		String dayFormat = session.getAttributeText("day-format");
		TimeEvaluationOptions options = TimeUtils.DEFAULT_OPTIONS;
		String tzs = session.getAttributeText("time-zone");
		if (tzs != null) {
			TimeZone timeZone = TimeZone.getTimeZone(tzs);
			if (timeZone.getRawOffset() == 0 && !timeZone.useDaylightTime()//
				&& !(tzs.equalsIgnoreCase("GMT") || tzs.equalsIgnoreCase("Z")))
				throw new QonfigInterpretationException("Unrecognized time-zone '" + tzs + "'");
			options = options.withTimeZone(timeZone);
		}
		try {
			options = options.withMaxResolution(TimeUtils.DateElementType.valueOf(session.getAttributeText("max-resolution")));
		} catch (IllegalArgumentException e) {
			session.withWarning("Unrecognized instant resolution: '" + session.getAttributeText("max-resolution"));
		}
		options = options.with24HourFormat(session.getAttribute("format-24h", boolean.class));
		String rteS = session.getAttributeText("relative-eval-type");
		try {
			options = options.withEvaluationType(TimeUtils.RelativeInstantEvaluation.valueOf(rteS));
		} catch (IllegalArgumentException e) {
			session.withWarning("Unrecognized relative evaluation type: '" + rteS);
		}
		TimeEvaluationOptions fOptions = options;
		TypeToken<Format<Instant>> formatType = TypeTokens.get().keyFor(Format.class).<Format<Instant>> parameterized(Instant.class);
		ModelInstanceType<SettableValue<?>, SettableValue<Format<Instant>>> formatInstanceType = ModelTypes.Value.forType(formatType);
		ObservableExpression relativeV = session.getAttributeExpression("relative-to");
		return () -> {
			Function<ModelSetInstance, Supplier<Instant>> relativeTo;
			if (relativeV == null) {
				relativeTo = msi -> Instant::now;
			} else {
				try {
					relativeTo = relativeV.findMethod(Instant.class, session.getExpressoEnv()).withOption(BetterList.empty(), null).find0();
				} catch (QonfigInterpretationException e) {
					session.withError(e.getMessage(), e);
					relativeTo = msi -> Instant::now;
				}
			}
			Function<ModelSetInstance, Supplier<Instant>> relativeTo2 = relativeTo;
			return new ObservableModelSet.AbstractValueContainer<SettableValue<?>, SettableValue<Format<Instant>>>(formatInstanceType) {
				@Override
				public SettableValue<Format<Instant>> get(ModelSetInstance models) {
					return ObservableModelSet.literal(SpinnerFormat.flexDate(relativeTo2.apply(models), dayFormat, __ -> fOptions),
						"instant");
				}
			};
		};
	}

	private ValueCreator<SettableValue<?>, SettableValue<Format<BetterFile>>> createFileFormat(ExpressoQIS session)
		throws QonfigInterpretationException {
		ObservableExpression fileSourceEx = session.getAttributeExpression("file-source");
		ObservableExpression workingDirEx = session.getAttributeExpression("working-dir");
		boolean allowEmpty = session.getAttribute("allow-empty", boolean.class);
		return () -> {
			Function<ModelSetInstance, SettableValue<BetterFile.FileDataSource>> fileSource;
			Function<ModelSetInstance, SettableValue<String>> workingDir;
			try {
				if (fileSourceEx != null)
					fileSource = fileSourceEx.evaluate(//
						ModelTypes.Value.forType(BetterFile.FileDataSource.class), session.getExpressoEnv());
				else
					fileSource = ObservableModelSet.literalContainer(ModelTypes.Value.forType(BetterFile.FileDataSource.class),
						new NativeFileSource(), "native");
			} catch (QonfigInterpretationException e) {
				session.withError(e.getMessage(), e);
				fileSource = ObservableModelSet.literalContainer(ModelTypes.Value.forType(BetterFile.FileDataSource.class),
					new NativeFileSource(), "native");
			}
			try {
				if (workingDirEx != null)
					workingDir = workingDirEx.evaluate(ModelTypes.Value.forType(String.class), session.getExpressoEnv());
				else
					workingDir = null;
			} catch (QonfigInterpretationException e) {
				session.withError(e.getMessage(), e);
				workingDir = null;
			}
			Function<ModelSetInstance, SettableValue<BetterFile.FileDataSource>> fileSource2 = fileSource;
			Function<ModelSetInstance, SettableValue<String>> workingDir2 = workingDir;
			TypeToken<Format<BetterFile>> fileFormatType = TypeTokens.get().keyFor(Format.class)
				.<Format<BetterFile>> parameterized(BetterFile.class);
			return new ObservableModelSet.AbstractValueContainer<SettableValue<?>, SettableValue<Format<BetterFile>>>(
				ModelTypes.Value.forType(fileFormatType)) {
				@Override
				public SettableValue<Format<BetterFile>> get(ModelSetInstance models) {
					SettableValue<BetterFile.FileDataSource> fds = fileSource2.apply(models);
					return SettableValue.asSettable(//
						fds.transform(fileFormatType, tx -> tx.map(fs -> {
							BetterFile workingDirFile = BetterFile.at(fs, workingDir2 == null ? "." : workingDir2.apply(models).get());
							return new BetterFile.FileFormat(fs, workingDirFile, allowEmpty);
						})), //
						__ -> "Not reversible");
				}
			};
		};
	}
}
