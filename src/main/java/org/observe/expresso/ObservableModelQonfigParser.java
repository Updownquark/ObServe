package org.observe.expresso;

import java.awt.Image;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

import javax.swing.JFrame;

import org.observe.Observable;
import org.observe.ObservableAction;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.Transformation;
import org.observe.Transformation.TransformationPrecursor;
import org.observe.Transformation.TransformationValues;
import org.observe.assoc.ObservableMap;
import org.observe.assoc.ObservableMultiMap;
import org.observe.assoc.ObservableSortedMap;
import org.observe.assoc.ObservableSortedMultiMap;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollectionBuilder;
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
import org.observe.expresso.ExpressoInterpreter.ExpressoSession;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ObservableExpression.MethodFinder;
import org.observe.expresso.ObservableModelSet.AbstractValueContainer;
import org.observe.expresso.ObservableModelSet.Builder;
import org.observe.expresso.ObservableModelSet.ExternalModelSet;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.observe.expresso.ObservableModelSet.ValueGetter;
import org.observe.util.TypeTokens;
import org.observe.util.swing.WindowPopulation;
import org.qommons.BiTuple;
import org.qommons.LambdaUtils;
import org.qommons.SubClassMap2;
import org.qommons.ThreadConstraint;
import org.qommons.TimeUtils;
import org.qommons.TimeUtils.TimeEvaluationOptions;
import org.qommons.TriFunction;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.config.QommonsConfig;
import org.qommons.config.QonfigChildDef;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreter;
import org.qommons.config.QonfigInterpreter.QonfigValueModifier;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.QonfigToolkitAccess;
import org.qommons.io.ArchiveEnabledFileSource;
import org.qommons.io.BetterFile;
import org.qommons.io.BetterFile.FileDataSource;
import org.qommons.io.FileBackups;
import org.qommons.io.Format;
import org.qommons.io.NativeFileSource;
import org.qommons.io.SpinnerFormat;
import org.qommons.threading.QommonsTimer;
import org.qommons.tree.BetterTreeList;
import org.xml.sax.SAXException;

import com.google.common.reflect.TypeToken;

public class ObservableModelQonfigParser {
	public static final ExpressoParser EXPRESSION_PARSER = new DefaultExpressoParser();

	public static final QonfigToolkitAccess OBSERVE = new QonfigToolkitAccess(ObservableModelQonfigParser.class, "observe-models.qtd")
		.withCustomValueType(ObservableModelSet.JAVA_IDENTIFIER_TYPE)//
		.withCustomValueType(new ExpressionValueType("expression", EXPRESSION_PARSER, false))//
		.withCustomValueType(new ExpressionValueType("expression-or-string", EXPRESSION_PARSER, true));

	public static final String PATH_KEY = "model-path";
	public static final String VALUE_TYPE_KEY = "value-type";
	public static final String FORMAT_KEY = "format";

	public interface ExtModelValue<M, MV extends M> {
		ModelInstanceType<M, MV> getType();

		MV get(ExternalModelSet extModels);
	}

	public interface ConfigModelValue<M, MV extends M, T> {
		ModelInstanceType<M, MV> getType();

		MV create(ObservableConfig.ObservableConfigValueBuilder<T> config, ModelSetInstance msi);
	}

	public static abstract class AbstractConfigModelValue<M, MV extends M, T> implements ConfigModelValue<M, MV, T> {
		private final ModelInstanceType<M, MV> theType;

		public AbstractConfigModelValue(ModelInstanceType<M, MV> type) {
			theType = type;
		}

		@Override
		public ModelInstanceType<M, MV> getType() {
			return theType;
		}
	}

	public interface ValueParser {
		<T> T parseModelValue(TypeToken<T> type, String text) throws QonfigInterpretationException;
	}

	public interface SimpleValueParser extends ValueParser {
		@Override
		default <T> T parseModelValue(TypeToken<T> type, String text) throws QonfigInterpretationException {

			T value;
			try {
				value = (T) parseValue(type, text);
			} catch (ParseException e) {
				throw new QonfigInterpretationException(e);
			}
			if (value != null && !TypeTokens.get().isInstance(type, value))
				throw new IllegalStateException("Parser " + this + " parsed a value of type " + value.getClass() + " for type " + type);
			return value;
		}

		Object parseValue(TypeToken<?> type, String text) throws ParseException;
	}

	public static SimpleValueParser simple(SimpleValueParser parser) {
		return parser;
	}

	private static final SubClassMap2<Object, ValueParser> DEFAULT_PARSERS;
	static {
		DEFAULT_PARSERS = new SubClassMap2<>(Object.class);
		DEFAULT_PARSERS.with(Boolean.class, simple((t, s) -> {
			if ("true".equals(s))
				return Boolean.TRUE;
			else if ("false".equals(s))
				return Boolean.FALSE;
			throw new ParseException("Unrecognized boolean value: '" + s + "'", 0);
		}));
		DEFAULT_PARSERS.with(Integer.class, simple((t, s) -> Format.INT.parse(s)));
		DEFAULT_PARSERS.with(Long.class, simple((t, s) -> Format.LONG.parse(s)));
		DecimalFormat df = new DecimalFormat("0.#######");
		DEFAULT_PARSERS.with(Double.class, simple((t, s) -> Format.parseDouble(s, df)));
		DEFAULT_PARSERS.with(Float.class, simple((t, s) -> (float) Format.parseDouble(s, df)));
		DEFAULT_PARSERS.with(String.class, simple((t, s) -> s));
		DEFAULT_PARSERS.with(Duration.class, simple((t, s) -> TimeUtils.parseDuration(s)));
		DEFAULT_PARSERS.with(Instant.class, simple((t, s) -> TimeUtils.parseInstant(s, true, true, null).evaluate(Instant::now)));
		DEFAULT_PARSERS.with(Enum.class, simple((t, s) -> parseEnum(t, s)));
	}

	private final SubClassMap2<Object, ValueParser> theParsers;

	public ObservableModelQonfigParser() {
		theParsers = new SubClassMap2<>(Object.class);
		theParsers.putAll(DEFAULT_PARSERS);
	}

	public ObservableModelQonfigParser withParser(Class<?> type, ValueParser parser) {
		theParsers.with(type, parser);
		return this;
	}
	public interface AppEnvironment {
		Function<ModelSetInstance, ? extends ObservableValue<String>> getTitle();

		Function<ModelSetInstance, ? extends ObservableValue<Image>> getIcon();
	}

	@SuppressWarnings("rawtypes")
	public <QIS extends ExpressoInterpreter.ExpressoSession<QIS>, B extends ExpressoInterpreter.Builder<QIS, B>> B configureInterpreter(
		B interpreter) {
		QonfigToolkit obsTk = OBSERVE.get();
		ExpressoInterpreter.Builder<?, ?> observeInterpreter = interpreter.forToolkit(obsTk);
		observeInterpreter.createWith("imports", ClassView.class, session -> {
			ClassView.Builder builder = ClassView.build();
			for (QonfigElement imp : session.getChildren("import")) {
				if (imp.getValueText().endsWith(".*"))
					builder.withWildcardImport(imp.getValueText().substring(0, imp.getValueText().length() - 2));
				else
					builder.withImport(imp.getValueText());
			}
			ClassView cv = builder.build();
			session.setClassView(cv);
			TypeTokens.get().addClassRetriever(new TypeTokens.TypeRetriever() {
				@Override
				public Type getType(String typeName) {
					return cv.getType(typeName);
				}
			});
			return cv;
		});
		observeInterpreter.createWith("models", ObservableModelSet.class, session -> {
			ObservableModelSet.Builder builder = ObservableModelSet.build(ObservableModelSet.JAVA_NAME_CHECKER);
			for (ExpressoSession<?> model : session.forChildren("model")) {
				ObservableModelSet.Builder subModel = builder.createSubModel(model.getAttributeText("name"));
				model.setModels(subModel);
				model.interpret(ObservableModelSet.class);
			}
			ObservableModelSet built = builder.build();
			session.setModels(built);
			return built;
		}).createWith("model", ObservableModelSet.class, session -> {
			ObservableModelSet.Builder model = (ObservableModelSet.Builder) session.getModels();
			QonfigChildDef subModelRole = session.getRole("sub-model");
			QonfigChildDef valueRole = session.getRole("value");
			for (ExpressoSession<?> child : session.forChildren()) {
				if (child.fulfills(valueRole)) {
					ValueContainer<?, ?> container = child.interpret(ValueContainer.class);
					model.with(child.getAttributeText("model-element", "name"), (ModelInstanceType<Object, Object>) container.getType(),
						(msi, ext) -> container.get(msi));
				} else if (child.fulfills(subModelRole)) {
					ObservableModelSet.Builder subModel = model.createSubModel(child.getAttributeText("abst-model", "name"));
					child.setModels(subModel);
					child.interpret(ObservableModelSet.class);
				}
			}
			return model;
		}).createWith("ext-model", ObservableModelSet.class, session -> {
			ObservableModelSet.Builder model = (ObservableModelSet.Builder) session.getModels();
			QonfigChildDef subModelRole = session.getRole("sub-model");
			QonfigChildDef valueRole = session.getRole("value");
			for (ExpressoSession<?> child : session.forChildren()) {
				if (child.fulfills(valueRole)) {
					ExtModelValue<?, ?> container = child.interpret(ExtModelValue.class);
					model.with(child.getAttributeText("model-element", "name"), (ModelInstanceType<Object, Object>) container.getType(),
						(msi, ext) -> container.get(ext));
				} else if (child.fulfills(subModelRole)) {
					ObservableModelSet.Builder subModel = model.createSubModel(child.getAttributeText("abst-model", "name"));
					child.setModels(subModel);
					child.interpret(ObservableModelSet.class);
				}
			}
			return model;
		}).modifyWith("abst-model-value", Object.class, new QonfigValueModifier<ExpressoSession<?>, Object>() {
			@Override
			public void prepareSession(ExpressoSession<?> session) throws QonfigInterpretationException {
				if (session.get(VALUE_TYPE_KEY) == null) {
					String typeStr = session.getAttributeText("type");
					if (typeStr != null)
						session.put(VALUE_TYPE_KEY, parseType(typeStr, session.getClassView()));
				}
				if (session.isInstance("model-element"))
					session.put(PATH_KEY, session.getModels().getPath() + "." + session.getAttributeText("model-element", "name"));
			}

			@Override
			public Object modifyValue(Object value, ExpressoSession<?> session) throws QonfigInterpretationException {
				return value;
			}
		}).createWith("config", ObservableModelSet.class,
			new QonfigInterpreter.QonfigValueCreator<ExpressoSession<?>, ObservableModelSet>() {
			@Override
			public ObservableModelSet createValue(ExpressoSession<?> session)
				throws QonfigInterpretationException {
				ObservableModelSet.Builder model = (ObservableModelSet.Builder) session.getModels();
				String configName = session.getAttributeText("config-name");
				Function<ModelSetInstance, SettableValue<BetterFile>> configDir = session.getAttributeAsValue("config-dir",
					BetterFile.class, () -> {
						return msi -> {
							String prop = System.getProperty(configName + ".config");
							if (prop != null)
								return ObservableModelSet.literal(BetterFile.at(new NativeFileSource(), prop), prop);
							else
								return ObservableModelSet.literal(BetterFile.at(new NativeFileSource(), "./" + configName),
									"./" + configName);
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

					FileBackups backups = new FileBackups(configFile);

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
					else if (!backups.getBackups().isEmpty()) {
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
				for (ExpressoSession<?> child : session.forChildren("value")) {
					child = child.as("config-value");
					String name = child.getAttributeText("model-element", "name");
					String path = child.getAttributeText("config-path");
					if (path == null)
						path = name;
					String typeStr = session.getAttributeText("type");
					TypeToken<Object> type = (TypeToken<Object>) parseType(typeStr, session.getClassView());
					session.put(VALUE_TYPE_KEY, type);
					Function<ModelSetInstance, ObservableConfigFormat<Object>> format = parseConfigFormat(
						session.getAttribute("format", ObservableExpression.class), null, model, session.getClassView(), type);
					session.put(FORMAT_KEY, format);
					ConfigModelValue<Object, Object, Object> configValue = child.interpret(ConfigModelValue.class);
					String fPath = path;
					model.with(name, configValue.getType(), (msi, ext) -> {
						ObservableConfig config = (ObservableConfig) msi.getModelConfiguration();
						ObservableConfig.ObservableConfigValueBuilder<Object> builder = config.asValue(type).at(fPath);
						return configValue.create(builder, msi);
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
		});
		abstract class TypedModelThing<T> implements QonfigInterpreter.QonfigValueCreator<ExpressoSession<?>, Void> {
			@Override
			public Void createValue(ExpressoSession<?> session) throws QonfigInterpretationException {
				ObservableModelSet.Builder model = (ObservableModelSet.Builder) session.getModels();
				String name = session.getElement().getAttributeText(obsTk.getAttribute("abst-model-value", "name"));
				String typeStr = session.getAttributeText("type");
				TypeToken<?> type = typeStr == null ? null : parseType(typeStr, session.getClassView());
				String path;
				if (session.get("config-model") != null) {
					path = session.getElement().getAttributeText(obsTk.getAttribute("config-value", "config-path"));
					if (path == null)
						path = name;
					Function<ModelSetInstance, ObservableConfigFormat<Object>> format = parseConfigFormat(
						session.getElement().getAttribute(obsTk.getAttribute("config-value", "format"), ObservableExpression.class), null,
						model, session.getClassView(), (TypeToken<Object>) type);
					buildConfig(model, name, (TypeToken<Object>) type, session, path, format);
				} else {
					path = model.getPath();
					if (path == null)
						path = name;
					else
						path = path + "." + name;
					build(model, name, type, session, name);
				}
				return null;
			}

			abstract <V> void build(ObservableModelSet.Builder model, String name, TypeToken<V> type, ExpressoSession<?> session,
				String path) throws QonfigInterpretationException;

			abstract <V> void buildConfig(ObservableModelSet.Builder model, String name, TypeToken<V> type, ExpressoSession<?> session,
				String path, Function<ModelSetInstance, ObservableConfigFormat<V>> format)
					throws QonfigInterpretationException;
		}
		observeInterpreter.createWith("constant", ValueContainer.class, session -> {
			return parseValue(//
				session.getModels(), session.getClassView(), (TypeToken<Object>) session.get(VALUE_TYPE_KEY),
				(ObservableExpression) session.getElement().getValue());
		}).createWith("value", ValueContainer.class, session -> {
			ObservableExpression valueX = (ObservableExpression) session.getElement().getValue();
			ValueContainer<SettableValue, SettableValue<Object>> value;
			value = valueX == null ? null : parseValue(//
				session.getModels(), session.getClassView(), (TypeToken<Object>) session.get(VALUE_TYPE_KEY),
				(ObservableExpression) session.getElement().getValue());
			return new ObservableModelSet.AbstractValueContainer<SettableValue, SettableValue<Object>>(value.getType()) {
				@Override
				public SettableValue<Object> get(ModelSetInstance models) {
					SettableValue.Builder<Object> builder = SettableValue.build((TypeToken<Object>) value.getType().getType(0))
						.withDescription((String) session.get(PATH_KEY));
					if (value != null)
						builder.withValue(value.apply(models).get());
					return builder.build();
				}
			};
		}).createWith("value", ConfigModelValue.class, session -> {
			TypeToken<Object> type = (TypeToken<Object>) session.get(VALUE_TYPE_KEY);
			String path = (String) session.get(PATH_KEY);
			Function<ModelSetInstance, ObservableConfigFormat<Object>> format = (Function<ModelSetInstance, ObservableConfigFormat<Object>>) session
				.get(FORMAT_KEY);
			ObservableExpression defaultX = session.getAttribute("default", ObservableExpression.class);
			ValueContainer<SettableValue, SettableValue<Object>> defaultV;
			defaultV = defaultX == null ? null : parseValue(//
				session.getModels(), session.getClassView(), type, (ObservableExpression) session.getElement().getValue());
			return new AbstractConfigModelValue<SettableValue, SettableValue<Object>, Object>(ModelTypes.Value.forType(type)) {
				@Override
				public SettableValue<Object> create(ObservableConfigValueBuilder<Object> config, ModelSetInstance msi) {
					if (format != null)
						config.withFormat(format.apply(msi));
					SettableValue<Object> built = config.buildValue(null);
					if (defaultV != null && config.getConfig().get(config.getPath()) == null)
						built.set(defaultV.apply(msi).get(), null);
					return built;
				}
			};
		}).createWith("value-set", Void.class, new TypedModelThing<ObservableValueSet>() {
			@Override
			<V> void build(Builder model, String name, TypeToken<V> type, ExpressoSession<?> session, String path)
				throws QonfigInterpretationException {
				throw new QonfigInterpretationException("value-sets can only be used under <config> elements");
			}

			@Override
			<V> void buildConfig(Builder model, String name, TypeToken<V> type, ExpressoSession<?> session, String path,
				Function<ModelSetInstance, ObservableConfigFormat<V>> format) throws QonfigInterpretationException {
				if (!session.getElement().getChildrenByRole().get(obsTk.getChild("value-set", "element").getDeclared()).isEmpty())
					System.err.println("WARNING: Config value " + model.pathTo(name) + " specifies elements, ignored");
				if (session.getElement().getAttributes().get(obsTk.getAttribute("config-value", "default")) != null)
					System.err.println("WARNING: Config entry-set " + model.pathTo(name) + " specifies default, ignored");
				model.<ObservableValueSet, ObservableValueSet<V>> with(name, ModelTypes.ValueSet.forType(type), (msi, extModels) -> {
					ObservableConfig config = (ObservableConfig) msi.getModelConfiguration();
					ObservableConfigValueBuilder<V> valueBuilder = config.asValue(type);
					valueBuilder.at(path);
					if (format != null)
						valueBuilder.withFormat(format.apply(msi));
					return valueBuilder.buildEntitySet(null);
				});
			}
		});
		abstract class CollectionCreator<C extends ObservableCollection> extends TypedModelThing<C> {
			@Override
			<V> void build(ObservableModelSet.Builder model, String name, TypeToken<V> type, ExpressoSession<?> session,
				String path) throws QonfigInterpretationException {
				List<Function<ModelSetInstance, V>> values = new ArrayList<>();
				for (ExpressoSession<?> el : session.forChildren("element"))
					values.add(parseValue(model, session.getClassView(), type, (ObservableExpression) el.getElement().getValue()));
				prepare(type, session);
				add(model, name, type, session, (modelSet, extModels) -> {
					C collection = (C) create(type, modelSet).withDescription(path).build();
					int i = 0;
					for (ExpressoSession<?> el : session.forChildren("element")) {
						if (!collection.add(values.get(i).apply(modelSet)))
							System.err.println("Warning: Value " + el.getElement().getValue() + " already added to "
								+ session.getElement().getType().getName() + " " + name);
						i++;
					}
					return collection;
				});
			}

			@Override
			<V> void buildConfig(Builder model, String name, TypeToken<V> type, ExpressoSession<?> session, String path,
				Function<ModelSetInstance, ObservableConfigFormat<V>> format) throws QonfigInterpretationException {
				if (!session.getElement().getChildrenByRole().get(obsTk.getChild("list", "element").getDeclared()).isEmpty())
					System.err.println("WARNING: Config collection " + model.pathTo(name) + " specifies elements, ignored");
				if (session.getElement().getAttributes().get(obsTk.getAttribute("config-value", "default")) != null)
					System.err.println("WARNING: Config collection " + model.pathTo(name) + " specifies default, ignored");
				prepare(type, session);
				add(model, name, type, session, (msi, extModels) -> {
					ObservableConfig config = (ObservableConfig) msi.getModelConfiguration();
					ObservableConfigValueBuilder<V> valueBuilder = config.asValue(type);
					valueBuilder.at(path);
					if (format != null)
						valueBuilder.withFormat(format.apply(msi));
					ObservableCollection<V> collection = valueBuilder.buildCollection(null);
					return modify(collection, msi);
				});
			}

			protected <V> void prepare(TypeToken<V> type, ExpressoSession<?> session) throws QonfigInterpretationException {
			}

			abstract <V> ObservableCollectionBuilder<V, ?> create(TypeToken<V> type, ModelSetInstance models);

			abstract <V> void add(ObservableModelSet.Builder model, String name, TypeToken<V> type, ExpressoSession<?> session,
				ValueGetter<? extends C> collection);

			abstract <V> C modify(ObservableCollection<V> collection, ModelSetInstance msi);
		}
		observeInterpreter.createWith("list", Void.class, new CollectionCreator<ObservableCollection>() {
			@Override
			<V> ObservableCollectionBuilder<V, ?> create(TypeToken<V> type, ModelSetInstance models) {
				return ObservableCollection.build(type);
			}

			@Override
			<V> void add(Builder model, String name, TypeToken<V> type, ExpressoSession<?> session,
				ValueGetter<? extends ObservableCollection> collection) {
				model.with(name, ModelTypes.Collection.forType(type), (ValueGetter<ObservableCollection<V>>) collection);
			}

			@Override
			<V> ObservableCollection<V> modify(ObservableCollection<V> collection, ModelSetInstance msi) {
				return collection;
			}
		}).createWith("set", Void.class, new CollectionCreator<ObservableSet>() {
			@Override
			<V> ObservableCollectionBuilder<V, ?> create(TypeToken<V> type, ModelSetInstance models) {
				return ObservableCollection.build(type).distinct();
			}

			@Override
			<V> void add(Builder model, String name, TypeToken<V> type, ExpressoSession<?> session,
				ValueGetter<? extends ObservableSet> collection) {
				model.with(name, ModelTypes.Set.forType(type), (ValueGetter<ObservableSet<V>>) collection);
			}

			@Override
			<V> ObservableSet<V> modify(ObservableCollection<V> collection, ModelSetInstance msi) {
				return collection.flow().distinct().collect();
			}
		}).createWith("sorted-set", Void.class, new CollectionCreator<ObservableSortedSet>() {
			private Function<ModelSetInstance, Comparator<Object>> theComparator;

			@Override
			protected <V> void prepare(TypeToken<V> type, ExpressoSession<?> session)
				throws QonfigInterpretationException {
				theComparator = parseComparator((TypeToken<Object>) type,
					session.getAttribute("sort-with", ObservableExpression.class), session.getModels(), session.getClassView());
			}

			@Override
			<V> ObservableCollectionBuilder<V, ?> create(TypeToken<V> type, ModelSetInstance models) {
				return ObservableCollection.build(type)//
					.distinctSorted(theComparator.apply(models));
			}

			@Override
			<V> void add(Builder model, String name, TypeToken<V> type, ExpressoSession<?> session,
				ValueGetter<? extends ObservableSortedSet> collection) {
				model.with(name, ModelTypes.SortedSet.forType(type), (ValueGetter<ObservableSortedSet<V>>) collection);
			}

			@Override
			<V> ObservableSortedSet<V> modify(ObservableCollection<V> collection, ModelSetInstance msi) {
				return collection.flow().distinctSorted(theComparator.apply(msi), false).collect();
			}
		}).createWith("sorted-list", Void.class, new CollectionCreator<ObservableSortedCollection>() {
			private Function<ModelSetInstance, Comparator<Object>> theComparator;

			@Override
			protected <V> void prepare(TypeToken<V> type, ExpressoSession<?> session)
				throws QonfigInterpretationException {
				theComparator = parseComparator((TypeToken<Object>) type,
					session.getAttribute("sort-with", ObservableExpression.class), session.getModels(), session.getClassView());
			}

			@Override
			<V> ObservableCollectionBuilder<V, ?> create(TypeToken<V> type, ModelSetInstance models) {
				return ObservableCollection.build(type)//
					.sortBy(theComparator.apply(models));
			}

			@Override
			<V> void add(Builder model, String name, TypeToken<V> type, ExpressoSession<?> session,
				ValueGetter<? extends ObservableSortedCollection> collection) {
				model.with(name, ModelTypes.SortedCollection.forType(type), (ValueGetter<ObservableSortedCollection<V>>) collection);
			}

			@Override
			<V> ObservableSortedCollection modify(ObservableCollection<V> collection, ModelSetInstance msi) {
				return collection.flow().sorted(theComparator.apply(msi)).collect();
			}
		});
		abstract class BiTypedModelThing<T> implements QonfigInterpreter.QonfigValueCreator<ExpressoSession<?>, Void> {
			@Override
			public Void createValue(ExpressoSession<?> session) throws QonfigInterpretationException {
				ObservableModelSet.Builder model = (ObservableModelSet.Builder) session.getModels();
				ClassView cv = session.getClassView();
				String name = session.getAttributeText("name");
				TypeToken<?> keyType = parseType(session.getAttributeText("key-type"), cv);
				TypeToken<?> valueType = parseType(session.getAttributeText("type"), cv);
				String path;
				if (session.get("config-model") != null) {
					path = session.getElement().getAttributeText(obsTk.getAttribute("config-value", "config-path"));
					if (path == null)
						path = name;
					Function<ModelSetInstance, ObservableConfigFormat<Object>> keyFormat = parseConfigFormat(
						session.getElement().getAttribute(obsTk.getAttribute("config-value", "key-format"), ObservableExpression.class),
						session.getElement().getAttributeText(obsTk.getAttribute("config-value", "key-default")), model, cv,
						(TypeToken<Object>) keyType);
					Function<ModelSetInstance, ObservableConfigFormat<Object>> valueFormat = parseConfigFormat(
						session.getElement().getAttribute(obsTk.getAttribute("config-value", "format"), ObservableExpression.class),
						session.getElement().getAttributeText(obsTk.getAttribute("config-value", "default")), model, cv,
						(TypeToken<Object>) valueType);
					buildConfig(model, name, (TypeToken<Object>) keyType, (TypeToken<Object>) valueType, session, path, keyFormat,
						valueFormat);
				} else {
					path = model.getPath();
					if (path == null)
						path = name;
					else
						path = path + "." + name;
					build(model, name, keyType, valueType, session, path);
				}
				return null;
			}

			abstract <K, V> void build(ObservableModelSet.Builder model, String name, TypeToken<K> keyType, TypeToken<V> valueType,
				ExpressoSession<?> session, String path) throws QonfigInterpretationException;

			abstract <K, V> void buildConfig(Builder model, String name, TypeToken<K> keyType, TypeToken<V> valueType,
				ExpressoSession<?> session, String path, Function<ModelSetInstance, ObservableConfigFormat<K>> keyFormat,
				Function<ModelSetInstance, ObservableConfigFormat<V>> valueFormat) throws QonfigInterpretationException;
		}
		abstract class MapCreator<M extends ObservableMap> extends BiTypedModelThing<M> {
			@Override
			<K, V> void build(ObservableModelSet.Builder model, String name, TypeToken<K> keyType, TypeToken<V> valueType,
				ExpressoSession<?> session, String path) throws QonfigInterpretationException {
				List<BiTuple<Function<ModelSetInstance, K>, Function<ModelSetInstance, V>>> entries = new ArrayList<>();
				for (ExpressoSession<?> entry : session.forChildren("entry")) {
					Function<ModelSetInstance, K> key = parseValue(model, session.getClassView(), keyType,
						entry.getAttribute("key", ObservableExpression.class));
					Function<ModelSetInstance, V> v = parseValue(model, session.getClassView(), valueType,
						entry.getAttribute("value", ObservableExpression.class));
					entries.add(new BiTuple<>(key, v));
				}
				prepare(keyType, valueType, session);
				add(model, name, keyType, valueType, session, (modelSet, extModels) -> {
					M map = (M) create(keyType, valueType, modelSet).withDescription(path).build();
					for (int i = 0; i < entries.size(); i++) {
						BiTuple<Function<ModelSetInstance, K>, Function<ModelSetInstance, V>> entry = entries.get(i);
						Object key = entry.getValue1().apply(modelSet);
						Object v = entry.getValue2().apply(modelSet);
						if (map.put(key, v) != null)
							System.err.println(
								"Warning: Key " + key + " already used for " + session.getElement().getType().getName() + " " + name);
					}
					return map;
				});
			}

			@Override
			<K, V> void buildConfig(Builder model, String name, TypeToken<K> keyType, TypeToken<V> valueType, ExpressoSession<?> session,
				String path, Function<ModelSetInstance, ObservableConfigFormat<K>> keyFormat,
				Function<ModelSetInstance, ObservableConfigFormat<V>> valueFormat) throws QonfigInterpretationException {
				if (!session.getElement().getChildrenByRole().get(obsTk.getChild("map", "entry").getDeclared()).isEmpty())
					System.err.println("WARNING: Config value " + model.pathTo(name) + " specifies entries, ignored");
				if (session.getElement().getAttributes().get(obsTk.getAttribute("config-value", "default")) != null)
					System.err.println("WARNING: Config map " + model.pathTo(name) + " specifies default, ignored");
				prepare(keyType, valueType, session);
				add(model, name, keyType, valueType, session, (msi, extModels) -> {
					ObservableConfig config = (ObservableConfig) msi.getModelConfiguration();
					ObservableConfigMapBuilder<K, V> valueBuilder = config.asValue(valueType).asMap(keyType);
					valueBuilder.at(path);
					if (keyFormat != null)
						valueBuilder.withKeyFormat(keyFormat.apply(msi));
					if (valueFormat != null)
						valueBuilder.values().withFormat(valueFormat.apply(msi));
					ObservableMap<K, V> map = valueBuilder.buildMap(null);
					return modify(map, msi);
				});
			}

			protected <K, V> void prepare(TypeToken<K> keyType, TypeToken<V> valueType, ExpressoSession<?> session)
				throws QonfigInterpretationException {
			}

			abstract <K, V> ObservableMap.Builder<K, V, ?> create(TypeToken<K> keyType, TypeToken<V> valueType, ModelSetInstance models);

			abstract <K, V> void add(ObservableModelSet.Builder model, String name, TypeToken<K> keyType, TypeToken<V> valueType,
				ExpressoSession<?> session, ValueGetter<? extends M> map);

			protected abstract <K, V> M modify(ObservableMap<K, V> map, ModelSetInstance msi);
		}
		observeInterpreter.createWith("map", Void.class, new MapCreator<ObservableMap>() {
			@Override
			<K, V> ObservableMap.Builder<K, V, ?> create(TypeToken<K> keyType, TypeToken<V> type, ModelSetInstance models) {
				return ObservableMap.build(keyType, type);
			}

			@Override
			<K, V> void add(ObservableModelSet.Builder model, String name, TypeToken<K> keyType, TypeToken<V> valueType,
				ExpressoSession<?> session, ValueGetter<? extends ObservableMap> map) {
				model.with(name, ModelTypes.Map.forType(keyType, valueType), (ValueGetter<ObservableMap<K, V>>) map);
			}

			@Override
			protected <K, V> ObservableMap modify(ObservableMap<K, V> map, ModelSetInstance msi) {
				return map;
			}
		});
		observeInterpreter.createWith("sorted-map", Void.class, new MapCreator<ObservableSortedMap>() {
			private Function<ModelSetInstance, Comparator<Object>> theComparator;

			@Override
			protected <K, V> void prepare(TypeToken<K> keyType, TypeToken<V> valueType, ExpressoSession<?> session)
				throws QonfigInterpretationException {
				theComparator = parseComparator((TypeToken<Object>) keyType,
					session.getAttribute("sort-with", ObservableExpression.class), session.getModels(), session.getClassView());
			}

			@Override
			<K, V> ObservableSortedMap.Builder<K, V, ?> create(TypeToken<K> keyType, TypeToken<V> type, ModelSetInstance models) {
				return ObservableSortedMap.build(keyType, type, //
					theComparator.apply(models));
			}

			@Override
			<K, V> void add(ObservableModelSet.Builder model, String name, TypeToken<K> keyType, TypeToken<V> valueType,
				ExpressoSession<?> session, ValueGetter<? extends ObservableSortedMap> map) {
				model.with(name, ModelTypes.SortedMap.forType(keyType, valueType), (ValueGetter<ObservableSortedMap<K, V>>) map);
			}

			@Override
			protected <K, V> ObservableSortedMap modify(ObservableMap<K, V> map, ModelSetInstance msi) {
				// ObservableMap lacks flow, so there's no standard way to make a sorted map here
				throw new UnsupportedOperationException("Config-based sorted-maps are not yet supported");
			}
		});
		abstract class MultiMapCreator<M extends ObservableMultiMap> extends BiTypedModelThing<M> {
			@Override
			<K, V> void build(ObservableModelSet.Builder model, String name, TypeToken<K> keyType, TypeToken<V> valueType,
				ExpressoSession<?> session, String path) throws QonfigInterpretationException {
				List<BiTuple<Function<ModelSetInstance, K>, Function<ModelSetInstance, V>>> entries = new ArrayList<>();
				for (ExpressoSession<?> entry : session.forChildren("entry")) {
					Function<ModelSetInstance, K> key = parseValue(model, session.getClassView(), keyType,
						entry.getAttribute("key", ObservableExpression.class));
					Function<ModelSetInstance, V> v = parseValue(model, session.getClassView(), valueType,
						entry.getAttribute("value", ObservableExpression.class));
					entries.add(new BiTuple<>(key, v));
				}
				prepare(keyType, valueType, session);
				add(model, name, keyType, valueType, session, (modelSet, extModels) -> {
					M map = (M) create(keyType, valueType, modelSet).withDescription(path).build(null);
					for (int i = 0; i < entries.size(); i++) {
						BiTuple<Function<ModelSetInstance, K>, Function<ModelSetInstance, V>> entry = entries.get(i);
						Object key = entry.getValue1().apply(modelSet);
						Object v = entry.getValue2().apply(modelSet);
						if (!map.add(key, v))
							System.err.println(
								"Warning: Key " + key + " already used for " + session.getElement().getType().getName() + " " + name);
					}
					return map;
				});
			}

			@Override
			<K, V> void buildConfig(Builder model, String name, TypeToken<K> keyType, TypeToken<V> valueType, ExpressoSession<?> session,
				String path, Function<ModelSetInstance, ObservableConfigFormat<K>> keyFormat,
				Function<ModelSetInstance, ObservableConfigFormat<V>> valueFormat) throws QonfigInterpretationException {
				if (!session.getElement().getChildrenByRole().get(obsTk.getChild("map", "entry").getDeclared()).isEmpty())
					System.err.println("WARNING: Config value " + model.pathTo(name) + " specifies entries, ignored");
				if (session.getElement().getAttributes().get(obsTk.getAttribute("config-value", "default")) != null)
					System.err.println("WARNING: Config multi-map " + model.pathTo(name) + " specifies default, ignored");
				prepare(keyType, valueType, session);
				add(model, name, keyType, valueType, session, (msi, extModels) -> {
					ObservableConfig config = (ObservableConfig) msi.getModelConfiguration();
					ObservableConfigMapBuilder<K, V> valueBuilder = config.asValue(valueType).asMap(keyType);
					valueBuilder.at(path);
					if (keyFormat != null)
						valueBuilder.withKeyFormat(keyFormat.apply(msi));
					if (valueFormat != null)
						valueBuilder.values().withFormat(valueFormat.apply(msi));
					ObservableMultiMap<K, V> map = valueBuilder.buildMultiMap(null);
					return modify(map, msi);
				});
			}

			protected <K, V> void prepare(TypeToken<K> keyType, TypeToken<V> valueType, ExpressoSession<?> session)
				throws QonfigInterpretationException {
			}

			abstract <K, V> ObservableMultiMap.Builder<K, V, ?> create(TypeToken<K> keyType, TypeToken<V> type, ModelSetInstance models);

			abstract <K, V> void add(ObservableModelSet.Builder model, String name, TypeToken<K> keyType, TypeToken<V> valueType,
				ExpressoSession<?> session, ValueGetter<? extends M> map);

			protected abstract <K, V> M modify(ObservableMultiMap<K, V> map, ModelSetInstance msi);
		}
		observeInterpreter.createWith("multi-map", Void.class, new MultiMapCreator<ObservableMultiMap>() {
			@Override
			<K, V> ObservableMultiMap.Builder<K, V, ?> create(TypeToken<K> keyType, TypeToken<V> type, ModelSetInstance models) {
				return ObservableMultiMap.build(keyType, type);
			}

			@Override
			<K, V> void add(ObservableModelSet.Builder model, String name, TypeToken<K> keyType, TypeToken<V> valueType,
				ExpressoSession<?> session, ValueGetter<? extends ObservableMultiMap> map) {
				model.with(name, ModelTypes.MultiMap.forType(keyType, valueType), (ValueGetter<ObservableMultiMap<K, V>>) map);
			}

			@Override
			protected <K, V> ObservableMultiMap<K, V> modify(ObservableMultiMap<K, V> map, ModelSetInstance msi) {
				return map;
			}
		});
		observeInterpreter.createWith("sorted-multi-map", Void.class, new MultiMapCreator<ObservableSortedMultiMap>() {
			private Function<ModelSetInstance, Comparator<Object>> theComparator;

			@Override
			protected <K, V> void prepare(TypeToken<K> keyType, TypeToken<V> valueType, ExpressoSession<?> session)
				throws QonfigInterpretationException {
				theComparator = parseComparator((TypeToken<Object>) keyType,
					session.getAttribute("sort-with", ObservableExpression.class), session.getModels(), session.getClassView());
			}

			@Override
			<K, V> ObservableSortedMultiMap.Builder<K, V, ?> create(TypeToken<K> keyType, TypeToken<V> type, ModelSetInstance models) {
				return ObservableMultiMap.build(keyType, type)//
					.sortedBy(theComparator.apply(models));
			}

			@Override
			<K, V> void add(ObservableModelSet.Builder model, String name, TypeToken<K> keyType, TypeToken<V> valueType,
				ExpressoSession<?> session, ValueGetter<? extends ObservableSortedMultiMap> map) {
				model.with(name, ModelTypes.SortedMultiMap.forType(keyType, valueType), (ValueGetter<ObservableSortedMultiMap<K, V>>) map);
			}

			@Override
			protected <K, V> ObservableSortedMultiMap<K, V> modify(ObservableMultiMap<K, V> map, ModelSetInstance msi) {
				return (ObservableSortedMultiMap<K, V>) map.flow().withKeys(keys -> keys.distinctSorted(theComparator.apply(msi), false))
					.gather();
			}
		});
		abstract class ExtModelValidator<T> implements QonfigInterpreter.QonfigValueCreator<ExpressoSession<?>, Void> {
			@Override
			public Void createValue(ExpressoSession<?> session) throws QonfigInterpretationException {
				ObservableModelSet.Builder model = (ObservableModelSet.Builder) session.getModels();
				ClassView cv = session.getClassView();
				String name = session.getAttributeText("name");
				String typeS = session.getAttributeText("type");
				TypeToken<?> type = parseType(typeS, cv);
				String path;
				if (model.getPath() == null)
					path = name;
				else {
					String pathBuilder = name;
					QonfigElement modelEl = session.getElement().getParent();
					while (modelEl != null && modelEl.getType().getName().equals("ext-model")) {
						pathBuilder = modelEl.getAttributeText(obsTk.getAttribute("ext-model", "name")) + "." + pathBuilder;
						modelEl = modelEl.getParent();
					}
					path = pathBuilder;
				}
				expect(model, name, type, new ValueGetter<T>() {
					@Override
					public T get(ModelSetInstance modelSet, ExternalModelSet extModels) {
						try {
							return getValue(extModels, path, type);
						} catch (IllegalArgumentException | QonfigInterpretationException e) {
							throw new IllegalArgumentException(
								"External model " + model.getPath() + " does not match expected: " + e.getMessage(), e);
						}
					}
				});
				return null;
			}

			protected abstract <V> void expect(ObservableModelSet.Builder model, String name, TypeToken<V> type,
				ValueGetter<? extends T> valueGetter);

			protected abstract T getValue(ExternalModelSet extModels, String name, TypeToken<?> type) throws QonfigInterpretationException;
		}
		observeInterpreter.createWith("ext-event", Void.class, new ExtModelValidator<Observable>() {
			@Override
			protected <V> void expect(Builder model, String name, TypeToken<V> type, ValueGetter<? extends Observable> valueGetter) {
				model.with(name, ModelTypes.Event.forType(type), (ValueGetter<Observable<V>>) valueGetter);
			}

			@Override
			protected Observable getValue(ExternalModelSet extModels, String name, TypeToken<?> type) throws QonfigInterpretationException {
				return extModels.get(name, ModelTypes.Event.forType(type));
			}
		});
		observeInterpreter.createWith("ext-action", Void.class, new ExtModelValidator<ObservableAction>() {
			@Override
			protected <V> void expect(Builder model, String name, TypeToken<V> type, ValueGetter<? extends ObservableAction> valueGetter) {
				model.with(name, ModelTypes.Action.forType(type), (ValueGetter<ObservableAction<V>>) valueGetter);
			}

			@Override
			protected ObservableAction getValue(ExternalModelSet extModels, String name, TypeToken<?> type)
				throws QonfigInterpretationException {
				return extModels.get(name, ModelTypes.Action.forType(type));
			}
		});
		observeInterpreter.createWith("ext-value", Void.class, new ExtModelValidator<SettableValue>() {
			@Override
			protected <V> void expect(Builder model, String name, TypeToken<V> type, ValueGetter<? extends SettableValue> valueGetter) {
				model.with(name, ModelTypes.Value.forType(type), (ValueGetter<SettableValue<V>>) valueGetter);
			}

			@Override
			protected SettableValue getValue(ExternalModelSet extModels, String name, TypeToken<?> type)
				throws QonfigInterpretationException {
				return extModels.get(name, ModelTypes.Value.forType(type));
			}
		});
		observeInterpreter.createWith("ext-list", Void.class, new ExtModelValidator<ObservableCollection>() {
			@Override
			protected <V> void expect(Builder model, String name, TypeToken<V> type,
				ValueGetter<? extends ObservableCollection> valueGetter) {
				model.with(name, ModelTypes.Collection.forType(type), (ValueGetter<ObservableCollection<V>>) valueGetter);
			}

			@Override
			protected ObservableCollection getValue(ExternalModelSet extModels, String name, TypeToken<?> type)
				throws QonfigInterpretationException {
				return extModels.get(name, ModelTypes.Collection.forType(type));
			}
		});
		observeInterpreter.createWith("ext-set", Void.class, new ExtModelValidator<ObservableSet>() {
			@Override
			protected <V> void expect(Builder model, String name, TypeToken<V> type, ValueGetter<? extends ObservableSet> valueGetter) {
				model.with(name, ModelTypes.Set.forType(type), (ValueGetter<ObservableSet<V>>) valueGetter);
			}

			@Override
			protected ObservableSet getValue(ExternalModelSet extModels, String name, TypeToken<?> type)
				throws QonfigInterpretationException {
				return extModels.get(name, ModelTypes.Set.forType(type));
			}
		});
		observeInterpreter.createWith("ext-sorted-list", Void.class, new ExtModelValidator<ObservableSortedCollection>() {
			@Override
			protected <V> void expect(Builder model, String name, TypeToken<V> type,
				ValueGetter<? extends ObservableSortedCollection> valueGetter) {
				model.with(name, ModelTypes.SortedCollection.forType(type), (ValueGetter<ObservableSortedCollection<V>>) valueGetter);
			}

			@Override
			protected ObservableSortedCollection getValue(ExternalModelSet extModels, String name, TypeToken<?> type)
				throws QonfigInterpretationException {
				return extModels.get(name, ModelTypes.SortedCollection.forType(type));
			}
		});
		observeInterpreter.createWith("ext-sorted-set", Void.class, new ExtModelValidator<ObservableSortedSet>() {
			@Override
			protected <V> void expect(Builder model, String name, TypeToken<V> type,
				ValueGetter<? extends ObservableSortedSet> valueGetter) {
				model.with(name, ModelTypes.SortedSet.forType(type), (ValueGetter<ObservableSortedSet<V>>) valueGetter);
			}

			@Override
			protected ObservableSortedSet getValue(ExternalModelSet extModels, String name, TypeToken<?> type)
				throws QonfigInterpretationException {
				return extModels.get(name, ModelTypes.SortedSet.forType(type));
			}
		});
		observeInterpreter.createWith("ext-value-set", Void.class, new ExtModelValidator<ObservableValueSet>() {
			@Override
			protected <V> void expect(Builder model, String name, TypeToken<V> type,
				ValueGetter<? extends ObservableValueSet> valueGetter) {
				model.with(name, ModelTypes.ValueSet.forType(type), (ValueGetter<ObservableValueSet<V>>) valueGetter);
			}

			@Override
			protected ObservableValueSet getValue(ExternalModelSet extModels, String name, TypeToken<?> type)
				throws QonfigInterpretationException {
				return extModels.get(name, ModelTypes.ValueSet.forType(type));
			}
		});
		abstract class ExtBiTypedModelValidator<T> implements QonfigInterpreter.QonfigValueCreator<ExpressoSession<?>, Void> {
			@Override
			public Void createValue(ExpressoSession<?> session) throws QonfigInterpretationException {
				ObservableModelSet.Builder model = (ObservableModelSet.Builder) session.getModels();
				ClassView cv = session.getClassView();
				String name = session.getAttributeText("name");
				String keyTypeS = session.getAttributeText("key-type");
				String valueTypeS = session.getAttributeText("type");
				TypeToken<?> keyType = parseType(keyTypeS, cv);
				TypeToken<?> valueType = parseType(valueTypeS, cv);
				String path;
				if (model.getPath() == null)
					path = name;
				else
					path = model.getPath() + "." + name;
				expect(model, name, keyType, valueType, new ValueGetter<T>() {
					@Override
					public T get(ModelSetInstance modelSet, ExternalModelSet extModels) {
						try {
							return getValue(extModels, path, keyType, valueType);
						} catch (QonfigInterpretationException e) {
							throw new IllegalArgumentException(
								"External model " + model.getPath() + " does not match expected: " + e.getMessage(), e);
						}
					}
				});
				return null;
			}

			protected abstract <K, V> void expect(ObservableModelSet.Builder model, String name, TypeToken<K> keyType,
				TypeToken<V> valueType, ValueGetter<? extends T> valueGetter);

			protected abstract T getValue(ExternalModelSet extModels, String name, TypeToken<?> keyType, TypeToken<?> valueType)
				throws QonfigInterpretationException;
		}
		observeInterpreter.createWith("ext-map", Void.class, new ExtBiTypedModelValidator<ObservableMap>() {
			@Override
			protected <K, V> void expect(Builder model, String name, TypeToken<K> keyType, TypeToken<V> valueType,
				ValueGetter<? extends ObservableMap> valueGetter) {
				model.with(name, ModelTypes.Map.forType(keyType, valueType), (ValueGetter<ObservableMap<K, V>>) valueGetter);
			}

			@Override
			protected ObservableMap getValue(ExternalModelSet extModels, String name, TypeToken<?> keyType, TypeToken<?> valueType)
				throws QonfigInterpretationException {
				return extModels.get(name, ModelTypes.Map.forType(keyType, valueType));
			}
		});
		observeInterpreter.createWith("ext-sorted-map", Void.class, new ExtBiTypedModelValidator<ObservableSortedMap>() {
			@Override
			protected <K, V> void expect(Builder model, String name, TypeToken<K> keyType, TypeToken<V> valueType,
				ValueGetter<? extends ObservableSortedMap> valueGetter) {
				model.with(name, ModelTypes.SortedMap.forType(keyType, valueType), (ValueGetter<ObservableSortedMap<K, V>>) valueGetter);
			}

			@Override
			protected ObservableSortedMap getValue(ExternalModelSet extModels, String name, TypeToken<?> keyType, TypeToken<?> valueType)
				throws QonfigInterpretationException {
				return extModels.get(name, ModelTypes.SortedMap.forType(keyType, valueType));
			}
		});
		observeInterpreter.createWith("ext-multi-map", Void.class, new ExtBiTypedModelValidator<ObservableMultiMap>() {
			@Override
			protected <K, V> void expect(Builder model, String name, TypeToken<K> keyType, TypeToken<V> valueType,
				ValueGetter<? extends ObservableMultiMap> valueGetter) {
				model.with(name, ModelTypes.MultiMap.forType(keyType, valueType), (ValueGetter<ObservableMultiMap<K, V>>) valueGetter);
			}

			@Override
			protected ObservableMultiMap getValue(ExternalModelSet extModels, String name, TypeToken<?> keyType, TypeToken<?> valueType)
				throws QonfigInterpretationException {
				return extModels.get(name, ModelTypes.MultiMap.forType(keyType, valueType));
			}
		});
		observeInterpreter.createWith("ext-sorted-multi-map", Void.class, new ExtBiTypedModelValidator<ObservableSortedMultiMap>() {
			@Override
			protected <K, V> void expect(Builder model, String name, TypeToken<K> keyType, TypeToken<V> valueType,
				ValueGetter<? extends ObservableSortedMultiMap> valueGetter) {
				model.with(name, ModelTypes.SortedMultiMap.forType(keyType, valueType),
					(ValueGetter<ObservableSortedMultiMap<K, V>>) valueGetter);
			}

			@Override
			protected ObservableSortedMultiMap getValue(ExternalModelSet extModels, String name, TypeToken<?> keyType,
				TypeToken<?> valueType) throws QonfigInterpretationException {
				return extModels.get(name, ModelTypes.SortedMultiMap.forType(keyType, valueType));
			}
		});
		observeInterpreter.createWith("first-value", ValueContainer.class, this::createFirstValue);
		observeInterpreter.createWith("transform", ValueContainer.class, this::createTransform);
		observeInterpreter.createWith("file-source", ValueContainer.class, this::createFileSource);
		observeInterpreter.createWith("text", ValueContainer.class, this::createTextFormat);
		observeInterpreter.createWith("int-format", ValueContainer.class, this::createIntFormat);
		observeInterpreter.createWith("long-format", ValueContainer.class, this::createLongFormat);
		observeInterpreter.createWith("double", ValueContainer.class, this::createDoubleFormat);
		observeInterpreter.createWith("instant", ValueContainer.class, this::createInstantFormat);
		observeInterpreter.createWith("file", ValueContainer.class, this::createFileFormat);
		observeInterpreter.createWith("regex-format", ValueContainer.class, this::createRegexFormat);
		observeInterpreter.createWith("regex-format-string", ValueContainer.class, this::createRegexStringFormat);
		observeInterpreter.createWith("simple-config-format", ValueContainer.class, this::createSimpleConfigFormat);
		return interpreter;
	}

	private ValueContainer<SettableValue, SettableValue<Object>> createFirstValue(ExpressoSession<?> session)
		throws QonfigInterpretationException {
		ObservableModelSet.Builder model = (ObservableModelSet.Builder) session.getModels();
		String name = session.getAttributeText("name");
		List<ValueContainer<SettableValue, SettableValue<?>>> values = new ArrayList<>(5);
		for (ExpressoSession<?> valueSession : session.forChildren("value"))
			values.add(session.interpret(ValueContainer.class));
		TypeToken<Object> commonType = (TypeToken<Object>) TypeTokens.get()
			.getCommonType(values.stream().map(v -> v.getType().getType(0)).collect(Collectors.toList()));
		return new AbstractValueContainer<SettableValue, SettableValue<Object>>(ModelTypes.Value.forType(commonType)) {
			@Override
			public SettableValue<Object> get(ModelSetInstance models) {
				ObservableValue<?>[] vs = new ObservableValue[values.size()];
				for (int i = 0; i < vs.length; i++)
					vs[i] = values.get(i).get(models);
				// TODO We should be able to make this settable
				return SettableValue.asSettable(ObservableValue.firstValue(commonType, v -> v != null, () -> null, vs),
					__ -> "Not settable");
			}
		};
	}

	private ValueContainer<?, ?> createTransform(ExpressoSession<?> session) throws QonfigInterpretationException {
		String name = session.getAttributeText("name");
		String sourceName = session.getAttributeText("source");
		ValueContainer<?, ?> source = session.getModels().get(sourceName, true);
		ModelType<?> type = source.getType().getModelType();
		if (type == ModelTypes.Event)
			transformEvent((ValueContainer<Observable, Observable<Object>>) source, session, name, 0);
		else if (type == ModelTypes.Value)
			transformValue((ValueContainer<SettableValue, SettableValue<Object>>) source, session, name, 0);
		else if (type == ModelTypes.Collection || type == ModelTypes.SortedCollection || type == ModelTypes.Set
			|| type == ModelTypes.SortedSet)
			transformCollection((ValueContainer<ObservableCollection, ObservableCollection<Object>>) source, session, name, 0);
		else
			throw new IllegalArgumentException("Transformation unsupported for source of type " + source.getType().getModelType());
		return null;
	}

	private ValueContainer<SettableValue, SettableValue<BetterFile.FileDataSource>> createFileSource(ExpressoSession<?> session)
		throws QonfigInterpretationException {
		ObservableModelSet.Builder model = (ObservableModelSet.Builder) session.getModels();
		String name = session.getAttributeText("name");
		Function<ModelSetInstance, SettableValue<FileDataSource>> source;
		switch (session.getAttributeText("type")) {
		case "native":
			source = modelSet -> ObservableModelSet.literal(new NativeFileSource(), "native-file-source");
			break;
		case "sftp":
			throw new UnsupportedOperationException("Not yet implemented");
		default:
			throw new IllegalArgumentException("Unrecognized file-source type: " + session.getAttributeText("type"));
		}
		if (!session.getChildren("archive").isEmpty()) {
			Set<String> archiveMethodStrs = new HashSet<>();
			List<ArchiveEnabledFileSource.FileArchival> archiveMethods = new ArrayList<>(5);
			for (ExpressoSession<?> archive : session.forChildren("archive")) {
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
			Function<ModelSetInstance, SettableValue<Integer>> maxZipDepth;
			if (session.getAttributeText("max-archive-depth") == null)
				maxZipDepth = null;
			else {
				String mad = session.getAttributeText("max-archive-depth");
				int zipDepth;
				try {
					zipDepth = Integer.parseInt(mad);
				} catch (NumberFormatException e) {
					zipDepth = -1;
				}
				if (zipDepth >= 0) {
					int finalZD = zipDepth;
					maxZipDepth = models -> ObservableModelSet.literal(finalZD, mad);
				} else {
					maxZipDepth = model.get(mad, ModelTypes.Value.forType(Integer.class));
				}
			}
			Function<ModelSetInstance, SettableValue<FileDataSource>> root = source;
			source = modelSet -> root.apply(modelSet).transformReversible(FileDataSource.class, //
				tx -> tx.nullToNull(true).map(fs -> {
					ArchiveEnabledFileSource aefs = new ArchiveEnabledFileSource(fs).withArchival(archiveMethods);
					if (maxZipDepth != null) {
						SettableValue<Integer> zd = maxZipDepth.apply(modelSet);
						zd.takeUntil(modelSet.getUntil()).changes().act(evt -> {
							if (evt.getNewValue() != null)
								aefs.setMaxArchiveDepth(evt.getNewValue());
						});
					}
					return aefs;
				}).replaceSource(aefs -> null, rev -> rev.disableWith(tv -> "Not settable")));
		}
		Function<ModelSetInstance, SettableValue<FileDataSource>> fSource = source;
		return new ObservableModelSet.AbstractValueContainer<SettableValue, SettableValue<BetterFile.FileDataSource>>(
			ModelTypes.Value.forType(FileDataSource.class)) {
			@Override
			public SettableValue<FileDataSource> get(ModelSetInstance models) {
				return fSource.apply(models);
			}
		};
	}

	private ValueContainer<SettableValue, SettableValue<Format<String>>> createTextFormat(ExpressoSession<?> session)
		throws QonfigInterpretationException {
		ObservableModelSet.Builder model = (ObservableModelSet.Builder) session.getModels();
		String name = session.getAttributeText("name");
		ObservableModelSet.literalContainer(
			ModelTypes.Value.forType(TypeTokens.get().keyFor(Format.class).<Format<String>> parameterized(String.class)),
			SpinnerFormat.NUMERICAL_TEXT, "text");
		return null;
	}

	private ValueContainer<SettableValue, SettableValue<Format<Integer>>> createIntFormat(ExpressoSession<?> session)
		throws QonfigInterpretationException {
		ObservableModelSet.Builder model = (ObservableModelSet.Builder) session.getModels();
		String name = session.getAttributeText("name");
		SpinnerFormat.IntFormat format = SpinnerFormat.INT;
		String sep = session.getAttributeText("grouping-separator");
		if (sep != null) {
			if (sep.length() != 0)
				System.err.println("WARNING: grouping-separator must be a single character");
			else
				format = format.withGroupingSeparator(sep.charAt(0));
		}
		ObservableModelSet.literalContainer(
			ModelTypes.Value.forType(TypeTokens.get().keyFor(Format.class).<Format<Integer>> parameterized(Integer.class)), format, "int");
		return null;
	}

	private ValueContainer<SettableValue, SettableValue<Format<Long>>> createLongFormat(ExpressoSession<?> session)
		throws QonfigInterpretationException {
		ObservableModelSet.Builder model = (ObservableModelSet.Builder) session.getModels();
		String name = session.getAttributeText("name");
		SpinnerFormat.LongFormat format = SpinnerFormat.LONG;
		String sep = session.getAttributeText("grouping-separator");
		if (sep != null) {
			if (sep.length() != 0)
				System.err.println("WARNING: grouping-separator must be a single character");
			else
				format = format.withGroupingSeparator(sep.charAt(0));
		}
		ObservableModelSet.literalContainer(
			ModelTypes.Value.forType(TypeTokens.get().keyFor(Format.class).<Format<Long>> parameterized(Long.class)), format, "long");
		return null;
	}

	private ValueContainer<SettableValue, SettableValue<Format<Double>>> createDoubleFormat(ExpressoSession<?> session)
		throws QonfigInterpretationException {
		int sigDigs = Integer.parseInt(session.getAttributeText("sig-digs"));
		Format.SuperDoubleFormatBuilder builder = Format.doubleFormat(sigDigs);
		String unit = session.getAttributeText("unit");
		boolean withMetricPrefixes = session.getAttribute("metric-prefixes", boolean.class);
		boolean withMetricPrefixesP2 = session.getAttribute("metric-prefixes-p2", boolean.class);
		List<? extends ExpressoSession<?>> prefixes = session.forChildren("prefix");
		if (unit != null) {
			builder.withUnit(unit, session.getAttribute("unit-required", boolean.class));
			if (withMetricPrefixes) {
				if (withMetricPrefixesP2)
					session.withWarning("Both 'metric-prefixes' and 'metric-prefixes-p2' specified");
				builder.withMetricPrefixes();
			} else if (withMetricPrefixesP2)
				builder.withMetricPrefixesPower2();
			for (ExpressoSession<?> prefix : prefixes) {
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
		return ObservableModelSet.literalContainer(
			ModelTypes.Value.forType(TypeTokens.get().keyFor(Format.class).<Format<Double>> parameterized(Double.class)), builder.build(),
			"double");
	}

	private ValueContainer<SettableValue, SettableValue<Format<Instant>>> createInstantFormat(ExpressoSession<?> session)
		throws QonfigInterpretationException {
		ObservableModelSet.Builder model = (ObservableModelSet.Builder) session.getModels();
		ClassView cv = session.getClassView();
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
		SpinnerFormat<Instant> format = SpinnerFormat.flexDate(Instant::now, dayFormat, __ -> fOptions);
		TypeToken<Format<Instant>> formatType = TypeTokens.get().keyFor(Format.class).<Format<Instant>> parameterized(Instant.class);
		ModelInstanceType<SettableValue, SettableValue<Format<Instant>>> formatInstanceType = ModelTypes.Value.forType(formatType);
		Function<ModelSetInstance, Supplier<Instant>> relativeTo;
		ObservableExpression relativeV = session.getAttribute("relative-to", ObservableExpression.class);
		if (relativeV == null) {
			return ObservableModelSet.literalContainer(formatInstanceType, format, "instant");
		} else if (relativeV instanceof ExpressionValueType.Literal) {
			try {
				Instant ri = format.parse(((ExpressionValueType.Literal) relativeV).getValue());
				relativeTo = msi -> LambdaUtils.constantSupplier(ri, ((ExpressionValueType.Literal) relativeV).getValue(), null);
				return new ObservableModelSet.AbstractValueContainer<SettableValue, SettableValue<Format<Instant>>>(formatInstanceType) {
					@Override
					public SettableValue<Format<Instant>> get(ModelSetInstance models) {
						return ObservableModelSet.literal(SpinnerFormat.flexDate(relativeTo.apply(models), dayFormat, __ -> fOptions),
							"instant");
					}
				};
			} catch (ParseException e) {
				session.withWarning("Malformatted relative time: '" + relativeV, e);
				return ObservableModelSet.literalContainer(formatInstanceType, format, "instant");
			}
		} else {
			// TODO This should just be a value that is evaluated when needed
			relativeTo = relativeV.findMethod(Instant.class, model, cv).withOption(BetterList.empty(), null).find0();
			return new ObservableModelSet.AbstractValueContainer<SettableValue, SettableValue<Format<Instant>>>(formatInstanceType) {
				@Override
				public SettableValue<Format<Instant>> get(ModelSetInstance models) {
					return ObservableModelSet.literal(SpinnerFormat.flexDate(relativeTo.apply(models), dayFormat, __ -> fOptions),
						"instant");
				}
			};
		}
	}

	private ValueContainer<SettableValue, SettableValue<Format<BetterFile>>> createFileFormat(ExpressoSession<?> session)
		throws QonfigInterpretationException {
		ObservableModelSet model = session.getModels();
		ClassView cv = session.getClassView();
		Function<ModelSetInstance, SettableValue<BetterFile.FileDataSource>> fileSource;
		ObservableExpression fileSourceEx = session.getAttribute("file-source", ObservableExpression.class);
		if (fileSourceEx != null)
			fileSource = fileSourceEx.evaluate(//
				ModelTypes.Value.forType(BetterFile.FileDataSource.class), model, cv);
		else
			fileSource = ObservableModelSet.literalContainer(ModelTypes.Value.forType(BetterFile.FileDataSource.class),
				new NativeFileSource(), "native");
		ObservableExpression workingDirEx = session.getAttribute("working-dir", ObservableExpression.class);
		Function<ModelSetInstance, SettableValue<String>> workingDir;
		if (workingDirEx != null)
			workingDir = workingDirEx.evaluate(ModelTypes.Value.forType(String.class), model, cv);
		else
			workingDir = null;
		boolean allowEmpty = session.getAttribute("allow-empty", boolean.class);
		TypeToken<Format<BetterFile>> fileFormatType = TypeTokens.get().keyFor(Format.class)
			.<Format<BetterFile>> parameterized(BetterFile.class);
		return new ObservableModelSet.AbstractValueContainer<SettableValue, SettableValue<Format<BetterFile>>>(
			ModelTypes.Value.forType(fileFormatType)) {
			@Override
			public SettableValue<Format<BetterFile>> get(ModelSetInstance models) {
				SettableValue<BetterFile.FileDataSource> fds = fileSource.apply(models);
				return SettableValue.asSettable(//
					fds.transform(fileFormatType, tx -> tx.map(fs -> {
						BetterFile workingDirFile = BetterFile.at(fs, workingDir == null ? "." : workingDir.apply(models).get());
						return new BetterFile.FileFormat(fs, workingDirFile, allowEmpty);
					})), //
					__ -> "Not reversible");
			}
		};
	}

	private ValueContainer<SettableValue, SettableValue<Format<Pattern>>> createRegexFormat(ExpressoSession<?> session)
		throws QonfigInterpretationException {
		ObservableModelSet.Builder model = (ObservableModelSet.Builder) session.getModels();
		String name = session.getAttributeText("name");
		return ObservableModelSet.literalContainer(
			ModelTypes.Value.forType(TypeTokens.get().keyFor(Format.class).<Format<Pattern>> parameterized(Pattern.class)), Format.PATTERN,
			"regex-format");
	}

	private ValueContainer<SettableValue, SettableValue<Format<String>>> createRegexStringFormat(ExpressoSession<?> session)
		throws QonfigInterpretationException {
		return ObservableModelSet.literalContainer(
			ModelTypes.Value.forType(TypeTokens.get().keyFor(Format.class).<Format<String>> parameterized(String.class)), //
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
	}

	private ValueContainer<SettableValue, SettableValue<ObservableConfigFormat<Object>>> createSimpleConfigFormat(
		ExpressoSession<?> session) throws QonfigInterpretationException {
		ClassView cv = session.getClassView();
		TypeToken<Object> valueType = (TypeToken<Object>) parseType(session.getAttributeText("type"), cv);
		ValueContainer<SettableValue, SettableValue<Format<Object>>> format;
		ModelInstanceType<SettableValue, SettableValue<Format<Object>>> formatType = ModelTypes.Value
			.forType(TypeTokens.get().keyFor(Format.class).parameterized(valueType));
		Function<ModelSetInstance, Object> defaultValue;
		ExpressoSession<?> configValueSession = session.as("config-value");
		String defaultS = configValueSession.getAttributeText("default");
		ObservableExpression formatEx = configValueSession.getAttribute("format", ObservableExpression.class);
		if (formatEx != null) {
			format = formatEx.evaluate(formatType, session.getModels(), cv);
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
			else
				throw new QonfigInterpretationException(
					"No default format available for type " + valueType + " -- please specify a format");
			format = ObservableModelSet.literalContainer(formatType, (Format<Object>) f, type.getSimpleName());
			if (defaultS == null)
				defaultValue = null;
			else {
				Object defaultV;
				try {
					defaultV = f.parse(defaultS);
				} catch (ParseException e) {
					throw new QonfigInterpretationException(e);
				}
				if (!(TypeTokens.get().isInstance(valueType, defaultV)))
					throw new QonfigInterpretationException(
						"default value '" + defaultS + ", type " + defaultV.getClass() + ", is incompatible with value type " + valueType);
				defaultValue = msi -> defaultV;
			}
		}
		TypeToken<ObservableConfigFormat<Object>> ocfType = TypeTokens.get().keyFor(ObservableConfigFormat.class)
			.<ObservableConfigFormat<Object>> parameterized(valueType);
		return new ObservableModelSet.AbstractValueContainer<SettableValue, SettableValue<ObservableConfigFormat<Object>>>(
			ModelTypes.Value.forType(ocfType)) {
			@Override
			public SettableValue<ObservableConfigFormat<Object>> get(ModelSetInstance models) {
				SettableValue<Format<Object>> formatObj = format.get(models);
				Supplier<Object> defaultV = defaultValue == null ? null : () -> defaultValue.apply(models);
				return SettableValue.asSettable(formatObj.transform(ocfType, tx -> tx.nullToNull(true)//
					.map(f -> ObservableConfigFormat.ofQommonFormat(f, defaultV))), __ -> "Not reversible");
			}
		};
	}

	private static void restoreBackup(boolean fromError, ObservableConfig config, FileBackups backups, Runnable onBackup,
		Runnable onNoBackup, AppEnvironment app, boolean[] closingWithoutSave, ModelSetInstance msi) {
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

	private static void populate(ObservableConfig config, QommonsConfig initConfig) {
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

	private static <T> Function<ModelSetInstance, ObservableConfigFormat<T>> parseConfigFormat(ObservableExpression formatX, String valueS,
		ObservableModelSet model, ClassView cv, TypeToken<T> type) throws QonfigInterpretationException {
		if (formatX != null) {
			@SuppressWarnings("rawtypes")
			ValueContainer<SettableValue, ?> formatVC = formatX.evaluate(ModelTypes.Value.any(), model, cv);
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

	public static TypeToken<?> parseType(String text, ClassView cv) throws QonfigInterpretationException {
		try {
			return TypeTokens.get().parseType(text.replaceAll("\\{", "<").replaceAll("\\}", ">"));
		} catch (ParseException e) {
			try {
				return TypeTokens.get().parseType("java.lang." + text);
			} catch (ParseException e2) {
				throw new QonfigInterpretationException(text + ": " + e.getMessage(), e);
			}
		}
	}

	<T> ValueContainer<SettableValue, SettableValue<T>> parseValue(ObservableModelSet models, ClassView cv, TypeToken<T> type,
		ObservableExpression expression)
			throws QonfigInterpretationException {
		if (expression == null)
			return null;
		else if (expression instanceof ExpressionValueType.Literal) {
			if (type == null)
				throw new QonfigInterpretationException("type must be specified if value is not an expression");
			ValueParser parser = theParsers.get(TypeTokens.get().wrap(TypeTokens.getRawType(type)), false);
			if (parser == null)
				throw new QonfigInterpretationException("No parser configured for type " + type);
			T value = parser.parseModelValue(type, expression.toString());
			return ObservableModelSet.literalContainer(ModelTypes.Value.forType(type), value, expression.toString());
		} else
			return expression.evaluate(ModelTypes.Value.forType(type), models, cv);
	}

	private ValueContainer<?, ?> transformEvent(ValueContainer<Observable, Observable<Object>> source, ExpressoSession<?> transform,
		String name, int startOp) {
		List<? extends ExpressoSession<?>> ops = transform.forChildren("op");
		for (int i = startOp; i < ops.size(); i++) {
			ExpressoSession<?> op = ops.get(i);
			switch (op.getElement().getType().getName()) {
			case "no-init":
				source = source.map(source.getType(), Observable::noInit);
				break;
			case "skip":
				int times = Integer.parseInt(op.getAttributeText("times"));
				source = source.map(source.getType(), obs -> obs.skip(times));
				break;
			case "take":
				times = Integer.parseInt(op.getAttributeText("times"));
				source = source.map(source.getType(), obs -> obs.take(times));
				break;
			case "take-until":
				throw new UnsupportedOperationException("Not yet implemented");// TODO
			case "map-to":
				throw new UnsupportedOperationException("Not yet implemented");// TODO
			case "filter":
				throw new UnsupportedOperationException("Not yet implemented");// TODO
			case "filter-by-type":
				throw new UnsupportedOperationException("Not yet implemented");// TODO
			case "flatten":
			case "unmodifiable":
			case "reverse":
			case "refresh":
			case "refresh-each":
			case "distinct":
			case "sort":
			case "with-equivalence":
			case "filter-mod":
			case "map-equivalent":
			case "cross":
			case "where-contained":
			case "group-by":
				throw new IllegalArgumentException(
					"Operation of type " + op.getElement().getType().getName() + " is not supported for events");
			default:
				throw new IllegalArgumentException("Unrecognized operation type: " + op.getElement().getType().getName());
			}
		}
		return source;
	}

	private interface ValueTransform {
		SettableValue<Object> transform(SettableValue<Object> source, ModelSetInstance models);
	}

	private ValueContainer<?, ?> transformValue(ValueContainer<SettableValue, SettableValue<Object>> source, ExpressoSession<?> transform,
		String name, int startOp) throws QonfigInterpretationException {
		ValueTransform transformFn = (v, models) -> v;
		TypeToken<Object> currentType = (TypeToken<Object>) source.getType().getType(0);
		List<? extends ExpressoSession<?>> ops = transform.forChildren("op");
		for (int i = startOp; i < ops.size(); i++) {
			ExpressoSession<?> op = ops.get(i);
			ValueTransform prevTransformFn = transformFn;
			switch (op.getElement().getType().getName()) {
			case "take-until":
				throw new UnsupportedOperationException("Not yet implemented");// TODO
			case "map-to":
				ParsedTransformation ptx = mapTransformation(currentType, op);
				if (ptx.isReversible()) {
					transformFn = (v, models) -> prevTransformFn.transform(v, models).transformReversible(ptx.getTargetType(),
						tx -> (Transformation.ReversibleTransformation<Object, Object>) ptx.transform(tx, models));
				} else {
					transformFn = (v, models) -> {
						ObservableValue<Object> value = prevTransformFn.transform(v, models).transform(ptx.getTargetType(),
							tx -> ptx.transform(tx, models));
						return SettableValue.asSettable(value, __ -> "No reverse configured for " + transform.toString());
					};
					currentType = ptx.getTargetType();
				}
				break;
			case "refresh":
				Function<ModelSetInstance, Observable<?>> refresh = op.getAttribute("on", ObservableExpression.class)
				.evaluate(ModelTypes.Event.any(), transform.getModels(), op.getClassView());
				transformFn = (v, models) -> prevTransformFn.transform(v, models).refresh(refresh.apply(models));
				break;
			case "unmodifiable":
				boolean allowUpdates = op.getAttribute("allow-updates", Boolean.class);
				if (!allowUpdates) {
					transformFn = (v, models) -> prevTransformFn.transform(v, models)
						.disableWith(ObservableValue.of(StdMsg.UNSUPPORTED_OPERATION));
				} else {
					transformFn = (v, models) -> {
						SettableValue<Object> intermediate = prevTransformFn.transform(v, models);
						return intermediate.filterAccept(input -> {
							if (intermediate.get() == input)
								return null;
							else
								return StdMsg.ILLEGAL_ELEMENT;
						});
					};
				}
				break;
			case "flatten":
				System.err.println("WARNING: Value.flatten is not fully implemented!!  Some options may be ignored.");
				Function<ModelSetInstance, Function<Object, Object>> function;
				TypeToken<?> resultType;
				if (op.getAttribute("function", ObservableExpression.class) != null) {
					MethodFinder<Object, Object, Object, Object> finder = op.getAttribute("function", ObservableExpression.class)
						.<Object, Object, Object, Object> findMethod(TypeTokens.get().of(Object.class), transform.getModels(),
							op.getClassView())//
						.withOption(BetterList.of(currentType), new ObservableExpression.ArgMaker<Object, Object, Object>() {
							@Override
							public void makeArgs(Object t, Object u, Object v, Object[] args, ModelSetInstance models) {
								args[0] = t;
							}
						});
					function = finder.find1();
					resultType = finder.getResultType();
				} else {
					resultType = currentType;
					function = msi -> v -> v;
				}
				Class<?> resultClass = TypeTokens.getRawType(resultType);
				boolean nullToNull = op.getAttribute("null-to-null", boolean.class);
				if (ObservableValue.class.isAssignableFrom(resultClass)) {
					throw new UnsupportedOperationException("Not yet implemented");// TODO
				} else if (ObservableCollection.class.isAssignableFrom(resultClass)) {
					ModelInstanceType<ObservableCollection, ObservableCollection<Object>> modelType;
					TypeToken<Object> elementType = (TypeToken<Object>) resultType
						.resolveType(ObservableCollection.class.getTypeParameters()[0]);
					if (ObservableSet.class.isAssignableFrom(resultClass))
						modelType = (ModelInstanceType<ObservableCollection, ObservableCollection<Object>>) (ModelInstanceType<?, ?>) ModelTypes.Set
						.forType(elementType);
					else
						modelType = (ModelInstanceType<ObservableCollection, ObservableCollection<Object>>) (ModelInstanceType<?, ?>) ModelTypes.Collection
						.forType(elementType);
					ValueTransform penultimateTransform = transformFn;
					ValueContainer<ObservableCollection, ObservableCollection<Object>> collectionContainer = new ValueContainer<ObservableCollection, ObservableCollection<Object>>() {
						@Override
						public ModelInstanceType<ObservableCollection, ObservableCollection<Object>> getType() {
							return modelType;
						}

						@Override
						public ObservableCollection<Object> get(ModelSetInstance extModels) {
							ObservableValue<?> txValue = penultimateTransform.transform(source.get(extModels), extModels)
								.transform((TypeToken<Object>) resultType, tx -> tx.nullToNull(nullToNull).map(function.apply(extModels)));
							if (ObservableSet.class.isAssignableFrom(resultClass))
								return ObservableSet.flattenValue((ObservableValue<ObservableSet<Object>>) txValue);
							else
								return ObservableCollection.flattenValue((ObservableValue<ObservableCollection<Object>>) txValue);
						}
					};
					return transformCollection(collectionContainer, transform, name, i + 1);
				} else
					throw new QonfigInterpretationException("Cannot flatten a value of type " + resultType);
				// transformFn=(v, models)->prevTransformFn.transform(v, models).fl
			case "filter":
			case "filter-by-type":
			case "reverse":
			case "refresh-each":
			case "distinct":
			case "sort":
			case "with-equivalence":
			case "filter-mod":
			case "map-equivalent":
			case "cross":
			case "where-contained":
			case "group-by":
				throw new IllegalArgumentException(
					"Operation of type " + op.getElement().getType().getName() + " is not supported for values");
			default:
				throw new IllegalArgumentException("Unrecognized operation type: " + op.getElement().getType().getName());
			}
		}

		ValueTransform finalTransform = transformFn;
		return source.map(ModelTypes.Value.forType(currentType), (v, models) -> finalTransform.transform(v, models));
	}

	private interface CollectionTransform {
		ObservableCollection.CollectionDataFlow<Object, ?, Object> transform(
			ObservableCollection.CollectionDataFlow<Object, ?, Object> source, ModelSetInstance models);
	}

	private ValueContainer<ObservableCollection, ObservableCollection<Object>> transformCollection(
		ValueContainer<ObservableCollection, ObservableCollection<Object>> source, ExpressoSession<?> transform, String name, int startOp)
			throws QonfigInterpretationException {
		CollectionTransform transformFn = (src, models) -> src;
		TypeToken<Object> currentType = (TypeToken<Object>) source.getType().getType(0);
		List<? extends ExpressoSession<?>> ops = transform.forChildren("op");
		for (int i = startOp; i < ops.size(); i++) {
			ExpressoSession<?> op = ops.get(i);
			CollectionTransform prevTransform = transformFn;
			switch (op.getElement().getType().getName()) {
			case "map-to":
				ParsedTransformation ptx = mapTransformation(currentType, op);
				transformFn = (src, models) -> prevTransform.transform(src, models).transform(ptx.getTargetType(),
					tx -> ptx.transform(tx, models));
				currentType = ptx.getTargetType();
				break;
			case "filter":
				throw new UnsupportedOperationException("Not yet implemented");// TODO
			case "filter-by-type":
				throw new UnsupportedOperationException("Not yet implemented");// TODO
			case "reverse":
				transformFn = (src, models) -> prevTransform.transform(src, models).reverse();
				break;
			case "refresh":
				Function<ModelSetInstance, Observable<?>> refresh = op
				.getAttribute("on", ObservableExpression.class)
				.evaluate(ModelTypes.Event.any(), transform.getModels(), op.getClassView());
				transformFn = (v, models) -> prevTransform.transform(v, models).refresh(refresh.apply(models));
				break;
			case "refresh-each":
				throw new UnsupportedOperationException("Not yet implemented");// TODO
			case "distinct":
				boolean useFirst = op.getAttribute("use-first", Boolean.class);
				boolean preserveSourceOrder = op.getAttribute("preserve-source-order", Boolean.class);
				ObservableExpression sortWith = op.getAttribute("sort-with", ObservableExpression.class);
				if (sortWith != null) {
					if (preserveSourceOrder)
						System.err.println("WARNING: preserve-source-order cannot be used with sorted collections,"
							+ " as order is determined by the values themselves");
					Function<ModelSetInstance, Comparator<Object>> comparator = parseComparator(currentType, sortWith,
						transform.getModels(),
						op.getClassView());
					transformFn = (src, models) -> prevTransform.transform(src, models).distinctSorted(comparator.apply(models), useFirst);
				} else
					transformFn = (src, models) -> prevTransform.transform(src, models)
					.distinct(uo -> uo.useFirst(useFirst).preserveSourceOrder(preserveSourceOrder));
					break;
			case "sort":
				Function<ModelSetInstance, Comparator<Object>> comparator = parseComparator(currentType,
					op.getAttribute("sort-with", ObservableExpression.class), transform.getModels(), op.getClassView());
				transformFn = (src, models) -> prevTransform.transform(src, models).sorted(comparator.apply(models));
				break;
			case "with-equivalence":
				throw new UnsupportedOperationException("Not yet implemented");// TODO
			case "unmodifiable":
				boolean allowUpdates = op.getAttribute("allow-updates", boolean.class);
				transformFn = (src, models) -> prevTransform.transform(src, models).unmodifiable(allowUpdates);
				break;
			case "filter-mod":
				throw new UnsupportedOperationException("Not yet implemented");// TODO
			case "map-equivalent":
				throw new UnsupportedOperationException("Not yet implemented");// TODO
			case "flatten":
				System.err.println("WARNING: Collection.flatten is not fully implemented!!  Some options may be ignored.");
				Function<ModelSetInstance, Function<Object, Object>> function;
				TypeToken<?> resultType;
				if (op.getAttribute("function", ObservableExpression.class) != null) {
					MethodFinder<Object, Object, Object, Object> finder = op.getAttribute("function", ObservableExpression.class)
						.<Object, Object, Object, Object> findMethod(TypeTokens.get().of(Object.class), transform.getModels(),
							op.getClassView())//
						.withOption(BetterList.of(currentType), new ObservableExpression.ArgMaker<Object, Object, Object>() {
							@Override
							public void makeArgs(Object t, Object u, Object v, Object[] args, ModelSetInstance models) {
								args[0] = t;
							}
						});
					function = finder.find1();
					resultType = finder.getResultType();
				} else {
					resultType = currentType;
					function = msi -> v -> v;
				}
				Class<?> resultClass = TypeTokens.getRawType(resultType);
				if (ObservableValue.class.isAssignableFrom(resultClass)) {
					throw new UnsupportedOperationException("Not yet implemented");// TODO
				} else if (ObservableCollection.class.isAssignableFrom(resultClass)) {
					TypeToken<Object> elementType = (TypeToken<Object>) resultType
						.resolveType(ObservableCollection.class.getTypeParameters()[0]);
					transformFn = (srcFlow, extModels) -> {
						Function<Object, Object> function2 = function.apply(extModels);
						return prevTransform.transform(source.get(extModels).flow(), extModels)//
							.flatMap(elementType, v -> v == null ? null : ((ObservableCollection<Object>) function2.apply(v)).flow());
					};
					currentType = elementType;
				} else
					throw new QonfigInterpretationException("Cannot flatten a collection of type " + resultType);
				break;
			case "cross":
				throw new UnsupportedOperationException("Not yet implemented");// TODO
			case "where-contained":
				throw new UnsupportedOperationException("Not yet implemented");// TODO
			case "group-by":
				throw new UnsupportedOperationException("Not yet implemented");// TODO
			case "no-init":
			case "skip":
			case "take":
			case "take-until":
				throw new IllegalArgumentException(
					"Operation type " + op.getElement().getType().getName() + " is not supported for collections");
			default:
				throw new IllegalArgumentException("Unrecognized operation type: " + op.getElement().getType().getName());
			}
		}
		Boolean active = transform.getAttribute("active", Boolean.class);
		CollectionTransform finalTransform = transformFn;
		return source.map(ModelTypes.Collection.forType(currentType), (v, models) -> {
			ObservableCollection.CollectionDataFlow<Object, ?, Object> flow = finalTransform.transform(//
				v.flow(), models);
			if (active == null)
				return flow.collect();
			else if (active)
				return flow.collectActive(null);
			else
				return flow.collectPassive();
		});
	}

	private ParsedTransformation mapTransformation(TypeToken<Object> currentType, ExpressoSession<?> op)
		throws QonfigInterpretationException {
		List<? extends ExpressoSession<?>> combinedValues = op.forChildren("combined-value");
		List<ValueContainer<?, ? extends SettableValue<?>>> combined = new ArrayList<>(combinedValues.size());
		for (ExpressoSession<?> combine : combinedValues) {
			String name = combine.getValueText();
			combined.add(op.getModels().get(name, ModelTypes.Value));
		}
		TypeToken<?>[] argTypes = new TypeToken[combined.size()];
		for (int i = 0; i < argTypes.length; i++)
			argTypes[i] = combined.get(i).getType().getType(0);
		MethodFinder<Object, TransformationValues<?, ?>, ?, Object> map;
		map = op.getAttribute("function", ObservableExpression.class).findMethod(TypeTokens.get().OBJECT, op.getModels(),
			op.getClassView());
		map.withOption(argList(currentType).with(argTypes), (src, tv, __, args, models) -> {
			args[0] = src;
			for (int i = 0; i < combined.size(); i++)
				args[i + 1] = tv.get(combined.get(i).get(models));
		});
		Function<ModelSetInstance, BiFunction<Object, TransformationValues<?, ?>, Object>> mapFn = map.find2();
		TypeToken<Object> targetType = (TypeToken<Object>) map.getResultType();
		ExpressoSession<?> reverseEl = op.forChildren("reverse").peekFirst();
		return new ParsedTransformation() {
			private boolean stateful;
			private boolean inexact;
			Function<ModelSetInstance, BiFunction<Object, TransformationValues<?, ?>, Object>> reverseFn;
			Function<ModelSetInstance, BiConsumer<Object, TransformationValues<?, ?>>> reverseModifier;
			Function<ModelSetInstance, Function<TransformationValues<?, ?>, String>> enabled;
			Function<ModelSetInstance, BiFunction<Object, TransformationValues<?, ?>, String>> accept;
			Function<ModelSetInstance, TriFunction<Object, TransformationValues<?, ?>, Boolean, Object>> create;
			Function<ModelSetInstance, BiFunction<Object, TransformationValues<?, ?>, String>> addAccept;

			{
				if (reverseEl != null) {//
					boolean modifying;
					String reverseType = reverseEl.getAttributeText("type");
					if (reverseType.equals("replace-source")) {
						ExpressoSession<?> replaceSource = reverseEl.as("replace-source");
						modifying = false;
						stateful = replaceSource.getAttribute("stateful", Boolean.class);
						inexact = replaceSource.getAttribute("inexact", Boolean.class);
					} else if (reverseType.equals("modify-source")) {
						modifying = true;
						stateful = true;
						inexact = false;
					} else
						throw new IllegalStateException("Unrecognized reverse type: " + reverseType);
					TypeToken<TransformationValues<Object, Object>> tvType = TypeTokens.get()
						.keyFor(Transformation.TransformationValues.class)
						.parameterized(TypeTokens.get().getExtendsWildcard(currentType), TypeTokens.get().getExtendsWildcard(targetType));
					if (modifying) {
						reverseFn = null;
						MethodFinder<Object, TransformationValues<?, ?>, ?, Void> reverse = reverseEl
							.getAttribute("function", ObservableExpression.class)
							.findMethod(TypeTokens.get().VOID, reverseEl.getModels(), reverseEl.getClassView());
						reverse.withOption(argList(targetType, tvType), (r, tv, __, args, models) -> {
							args[0] = r;
							args[1] = tv;
						});
						if (stateful)
							reverse.withOption(argList(currentType, targetType).with(argTypes), (r, tv, __, args, models) -> {
								args[0] = r;
								args[1] = tv.getCurrentSource();
								for (int i = 0; i < combined.size(); i++)
									args[i + 2] = combined.get(i);
							});
						reverse.withOption(argList(targetType).with(argTypes), (r, tv, __, args, models) -> {
							args[0] = r;
							for (int i = 0; i < combined.size(); i++)
								args[i + 1] = combined.get(i);
						});
						reverseModifier = reverse.find2().andThen(fn -> (r, tv) -> {
							fn.apply(r, tv);
						});
					} else {
						reverseModifier = null;
						MethodFinder<Object, TransformationValues<?, ?>, ?, Object> reverse = reverseEl
							.getAttribute("function", ObservableExpression.class)
							.findMethod(currentType, reverseEl.getModels(), reverseEl.getClassView());
						reverse.withOption(argList(targetType, tvType), (r, tv, __, args, models) -> {
							args[0] = r;
							args[1] = tv;
						});
						if (stateful)
							reverse.withOption(argList(currentType, targetType).with(argTypes), (r, tv, __, args, models) -> {
								args[0] = r;
								args[1] = tv.getCurrentSource();
								for (int i = 0; i < combined.size(); i++)
									args[i + 2] = combined.get(i);
							});
						reverse.withOption(argList(targetType).with(argTypes), (r, tv, __, args, models) -> {
							args[0] = r;
							for (int i = 0; i < combined.size(); i++)
								args[i + 1] = combined.get(i);
						});
						reverseFn = reverse.find2();
					}

					ObservableExpression enabledEx = reverseEl.getAttribute("enabled", ObservableExpression.class);
					if (enabledEx != null) {
						enabled = enabledEx
							.<TransformationValues<?, ?>, Void, Void, String> findMethod(TypeTokens.get().STRING, reverseEl.getModels(),
								reverseEl.getClassView())//
							.withOption(argList(tvType), (tv, __, ___, args, models) -> {
								args[0] = tv;
							}).withOption(argList(currentType).with(argTypes), (tv, __, ___, args, models) -> {
								args[0] = tv.getCurrentSource();
								for (int i = 0; i < combined.size(); i++)
									args[i + 1] = combined.get(i);
							}).withOption(argList(argTypes), (tv, __, ___, args, models) -> {
								for (int i = 0; i < combined.size(); i++)
									args[i] = combined.get(i);
							}).find1();
					}
					ObservableExpression acceptEx = reverseEl.getAttribute("accept", ObservableExpression.class);
					if (acceptEx != null) {
						MethodFinder<Object, TransformationValues<?, ?>, ?, String> acceptFinder = acceptEx
							.findMethod(TypeTokens.get().STRING, reverseEl.getModels(), reverseEl.getClassView());
						acceptFinder.withOption(argList(targetType, tvType), (r, tv, __, args, models) -> {
							args[0] = r;
							args[1] = tv;
						});
						if (stateful)
							acceptFinder.withOption(argList(targetType, currentType).with(argTypes), (r, tv, __, args, models) -> {
								args[0] = r;
								args[1] = tv.getCurrentSource();
								for (int i = 0; i < combined.size(); i++)
									args[i + 2] = combined.get(i);
							});
						acceptFinder.withOption(argList(targetType).with(argTypes), (r, tv, __, args, models) -> {
							args[0] = r;
							for (int i = 0; i < combined.size(); i++)
								args[i + 1] = combined.get(i);
						});
						accept = acceptFinder.find2();
					}
					ObservableExpression createEx = reverseEl.getAttribute("create", ObservableExpression.class);
					if (createEx != null) {
						create = createEx
							.<Object, TransformationValues<?, ?>, Boolean, Object> findMethod(currentType, reverseEl.getModels(),
								reverseEl.getClassView())//
							.withOption(argList(targetType, tvType, TypeTokens.get().BOOLEAN), (r, tv, b, args, models) -> {
								args[0] = r;
								args[1] = tv;
								args[2] = b;
							}).withOption(argList(targetType).with(argTypes).with(TypeTokens.get().BOOLEAN), (r, tv, b, args, models) -> {
								args[0] = r;
								for (int i = 0; i < combined.size(); i++)
									args[i + 1] = combined.get(i);
								args[args.length - 1] = b;
							}).withOption(argList(targetType).with(argTypes), (r, tv, b, args, models) -> {
								args[0] = r;
								for (int i = 0; i < combined.size(); i++)
									args[i + 1] = combined.get(i);
							}).find3();
					}
					ObservableExpression addAcceptEx = reverseEl.getAttribute("add-accept", ObservableExpression.class);
					if (addAcceptEx != null) {
						MethodFinder<Object, TransformationValues<?, ?>, ?, String> addAcceptFinder = addAcceptEx
							.findMethod(TypeTokens.get().STRING, reverseEl.getModels(), reverseEl.getClassView());
						addAcceptFinder.withOption(argList(targetType, tvType), (r, tv, __, args, models) -> {
							args[0] = r;
							args[1] = tv;
						});
						if (stateful)
							addAcceptFinder.withOption(argList(targetType, currentType).with(argTypes), (r, tv, __, args, models) -> {
								args[0] = r;
								args[1] = tv.getCurrentSource();
								for (int i = 0; i < combined.size(); i++)
									args[i + 2] = combined.get(i);
							});
						addAcceptFinder.withOption(argList(targetType).with(argTypes), (r, tv, __, args, models) -> {
							args[0] = r;
							for (int i = 0; i < combined.size(); i++)
								args[i + 1] = combined.get(i);
						});
						addAccept = addAcceptFinder.find2();
					}
				}
			}

			@Override
			public TypeToken<Object> getTargetType() {
				return targetType;
			}

			@Override
			public boolean isReversible() {
				return reverseEl != null;
			}

			@Override
			public Transformation<Object, Object> transform(TransformationPrecursor<Object, Object, ?> precursor,
				ModelSetInstance modelSet) {
				for (ValueContainer<?, ? extends SettableValue<?>> v : combined)
					precursor = (Transformation.ReversibleTransformationPrecursor<Object, Object, ?>) precursor
					.combineWith(v.get(modelSet));
				if (!(precursor instanceof Transformation.ReversibleTransformationPrecursor))
					return precursor.build(mapFn.apply(modelSet));
				Transformation.MaybeReversibleMapping<Object, Object> built;
				built = ((Transformation.ReversibleTransformationPrecursor<Object, Object, ?>) precursor).build(mapFn.apply(modelSet));
				if (reverseFn == null && reverseModifier == null) {//
					return built;
				} else {
					Function<TransformationValues<?, ?>, String> enabled2 = enabled == null ? null : enabled.apply(modelSet);
					BiFunction<Object, TransformationValues<?, ?>, String> accept2 = accept == null ? null : accept.apply(modelSet);
					TriFunction<Object, TransformationValues<?, ?>, Boolean, Object> create2 = create == null ? null
						: create.apply(modelSet);
					BiFunction<Object, TransformationValues<?, ?>, String> addAccept2 = addAccept == null ? null
						: addAccept.apply(modelSet);
					if (reverseModifier != null) {
						BiConsumer<Object, TransformationValues<?, ?>> reverseModifier2 = reverseModifier.apply(modelSet);
						return built.withReverse(
							new Transformation.SourceModifyingReverse<>(reverseModifier2, enabled2, accept2, create2, addAccept2));
					} else {
						BiFunction<Object, TransformationValues<?, ?>, Object> reverseFn2 = reverseFn.apply(modelSet);
						return built.withReverse(new Transformation.SourceReplacingReverse<>(built, reverseFn2, enabled2, accept2, create2,
							addAccept2, stateful, inexact));
					}
				}
			}
		};
	}

	private <T> Function<ModelSetInstance, Comparator<T>> parseComparator(TypeToken<T> type, ObservableExpression expression,
		ObservableModelSet model, ClassView cv) throws QonfigInterpretationException {
		TypeToken<? super T> superType = TypeTokens.get().getSuperWildcard(type);
		Function<ModelSetInstance, BiFunction<T, T, Integer>> compareFn = expression
			.<T, T, Void, Integer> findMethod(TypeTokens.get().INT, model, cv)//
			.withOption(argList(superType, superType), (v1, v2, __, args, models) -> {
				args[0] = v1;
				args[1] = v2;
			})//
			.find2();
		return compareFn.andThen(biFn -> (v1, v2) -> biFn.apply(v1, v2));
	}

	BetterList<TypeToken<?>> argList(TypeToken<?>... init) {
		return BetterTreeList.<TypeToken<?>> build().build().with(init);
	}

	private interface ParsedTransformation {
		TypeToken<Object> getTargetType();

		boolean isReversible();

		Transformation<Object, Object> transform(TransformationPrecursor<Object, Object, ?> precursor, ModelSetInstance modelSet);
	}

	private static <E extends Enum<E>> E parseEnum(TypeToken<?> type, String text) throws ParseException {
		try {
			return Enum.valueOf((Class<E>) TypeTokens.getRawType(type), text);
		} catch (IllegalArgumentException e) {
			throw new ParseException(e.getMessage(), 0);
		}
	}
}
