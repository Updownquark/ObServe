package org.observe.util;

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
import org.observe.config.ObservableConfigParseSession;
import org.observe.config.ObservableConfigPath;
import org.observe.config.ObservableValueSet;
import org.observe.config.SyncValueSet;
import org.observe.expresso.DefaultExpressoParser;
import org.observe.expresso.ExpressoParser;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableExpression.MethodFinder;
import org.observe.util.ModelType.ModelInstanceType;
import org.observe.util.ObservableModelSet.Builder;
import org.observe.util.ObservableModelSet.ExternalModelSet;
import org.observe.util.ObservableModelSet.ModelSetInstance;
import org.observe.util.ObservableModelSet.ValueContainer;
import org.observe.util.ObservableModelSet.ValueGetter;
import org.observe.util.swing.WindowPopulation;
import org.qommons.BiTuple;
import org.qommons.Identifiable;
import org.qommons.LambdaUtils;
import org.qommons.SubClassMap2;
import org.qommons.TimeUtils;
import org.qommons.TimeUtils.TimeEvaluationOptions;
import org.qommons.TriFunction;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.config.QommonsConfig;
import org.qommons.config.QonfigAttributeDef;
import org.qommons.config.QonfigChildDef;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigElementDef;
import org.qommons.config.QonfigInterpreter;
import org.qommons.config.QonfigInterpreter.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreter.QonfigInterpretingSession;
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

	public static final QonfigToolkitAccess TOOLKIT = new QonfigToolkitAccess(ObservableModelQonfigParser.class, "observe-models.qtd")
		.withCustomValueType(new ExpressionValueType("expression", EXPRESSION_PARSER, false))//
		.withCustomValueType(new ExpressionValueType("expression-or-string", EXPRESSION_PARSER, true));

	public interface ValueParser {
		<T> BiFunction<ModelSetInstance, ExternalModelSet, T> parseModelValue(ObservableModelSet models, TypeToken<T> type, String text)
			throws QonfigInterpretationException;
	}

	public interface SimpleValueParser extends ValueParser {
		@Override
		default <T> BiFunction<ModelSetInstance, ExternalModelSet, T> parseModelValue(ObservableModelSet models, TypeToken<T> type,
			String text)
				throws QonfigInterpretationException {
			T value;
			try {
				value = (T) parseValue(type, text);
			} catch (ParseException e) {
				throw new QonfigInterpretationException(e);
			}
			if (value != null && !TypeTokens.get().isInstance(type, value))
				throw new IllegalStateException("Parser " + this + " parsed a value of type " + value.getClass() + " for type " + type);
			return (msi, extModel) -> value;
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
		DEFAULT_PARSERS.with(Instant.class, simple((t, s) -> TimeUtils.parseFlexFormatTime(s, true, true, null).evaluate(Instant::now)));
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

	public static <T> SettableValue<T> literal(TypeToken<T> type, T value, String text) {
		return SettableValue.asSettable(ObservableValue.of(value), __ -> "Literal value '" + text + "'");
	}

	public static <T> SettableValue<T> literal(T value, String text) {
		return literal(TypeTokens.get().of((Class<T>) value.getClass()), value, text);
	}

	public static <T> ValueGetter<SettableValue<T>> literalGetter(T value, String text) {
		return literalGetter(TypeTokens.get().of((Class<T>) value.getClass()), value, text);
	}

	public static <T> ValueGetter<SettableValue<T>> literalGetter(TypeToken<T> type, T value, String text) {
		return new ValueGetter<SettableValue<T>>() {
			private final SettableValue<T> theValue = literal(type, value, text);

			@Override
			public SettableValue<T> get(ModelSetInstance models, ExternalModelSet extModels) {
				return theValue;
			}

			@Override
			public String toString() {
				return value.toString();
			}
		};
	}

	@SuppressWarnings("rawtypes")
	public static <T> ValueContainer<SettableValue, SettableValue<T>> literalContainer(
		ModelInstanceType<SettableValue, SettableValue<T>> type, T value, String text) {
		return new ValueContainer<SettableValue, SettableValue<T>>() {
			private final SettableValue<T> theValue = literal((TypeToken<T>) type.getType(0), value, text);

			@Override
			public ModelInstanceType<SettableValue, SettableValue<T>> getType() {
				return type;
			}

			@Override
			public SettableValue<T> get(ModelSetInstance extModels) {
				return theValue;
			}
		};
	}

	public interface AppEnvironment {
		Function<ModelSetInstance, ? extends ObservableValue<String>> getTitle();

		Function<ModelSetInstance, ? extends ObservableValue<Image>> getIcon();
	}

	@SuppressWarnings("rawtypes")
	public QonfigInterpreter.Builder configureInterpreter(QonfigInterpreter.Builder interpreter) {
		QonfigToolkit obsTk = TOOLKIT.get();
		QonfigInterpreter.Builder observeInterpreter = interpreter.forToolkit(obsTk);
		observeInterpreter.createWith("imports", ClassView.class, (el, session) -> {
			ClassView.Builder builder = ClassView.build();
			for (QonfigElement imp : session.getChildren("import")) {
				if (imp.getValueText().endsWith(".*"))
					builder.withWildcardImport(imp.getValueText().substring(0, imp.getValueText().length() - 2));
				else
					builder.withImport(imp.getValueText());
			}
			ClassView cv = builder.build();
			session.putGlobal("imports", cv);
			TypeTokens.get().addClassRetriever(new TypeTokens.TypeRetriever() {
				@Override
				public Type getType(String typeName) {
					return cv.getType(typeName);
				}
			});
			return cv;
		});
		observeInterpreter.createWith("models", ObservableModelSet.class, (el, session) -> {
			ObservableModelSet.Builder builder = ObservableModelSet.build();
			QonfigChildDef.Declared modelRole = obsTk.getChild("models", "model").getDeclared();
			for (QonfigElement model : el.getChildrenByRole().get(modelRole)) {
				ObservableModelSet.Builder subModel = builder
					.createSubModel(model.getAttributeText(modelRole.getType().getAttribute("name")));
				session.put("model", subModel);
				session.getInterpreter().interpret(model, ObservableModelSet.class);
			}
			return builder.build();
		}).createWith("abst-model", ObservableModelSet.class, (el, session) -> {
			ObservableModelSet.Builder model = (ObservableModelSet.Builder) session.get("model");
			QonfigElementDef modelType = TOOLKIT.get().getElement("abst-model");
			for (QonfigElement child : el.getChildren()) {
				if (modelType.isAssignableFrom(child.getType())) {
					ObservableModelSet.Builder subModel = model
						.createSubModel(child.getAttributeText(obsTk.getAttribute("abst-model-value", "name")));
					session.put("model", subModel);
					session.getInterpreter().interpret(child, ObservableModelSet.class);
				} else
					session.getInterpreter().interpret(child, Void.class);
			}
			return model;
		}).extend("abst-model", "ext-model", ObservableModelSet.class, ObservableModelSet.class, (s, el, session) -> {
			return s;
		}).extend("abst-model", "model", ObservableModelSet.class, ObservableModelSet.class, (s, el, session) -> {
			return s;
		}).createWith("config", ObservableModelSet.class, new QonfigInterpreter.QonfigValueCreator<ObservableModelSet>() {
			@Override
			public ObservableModelSet createValue(QonfigElement el, QonfigInterpretingSession session)
				throws QonfigInterpretationException {
				ObservableModelSet.Builder model = (ObservableModelSet.Builder) session.get("model");
				session.put("config-model", true);
				String configName = session.getAttributeText("config-name");
				Function<ModelSetInstance, SettableValue<BetterFile>> configDir;
				ObservableExpression configDirX = session.getAttribute("config-dir", ObservableExpression.class);
				if (configDirX != null)
					configDir = configDirX.evaluate(ModelTypes.Value.forType(BetterFile.class), model, (ClassView) session.get("imports"));
				else {
					configDir = msi -> {
						String prop = System.getProperty(configName + ".config");
						if (prop != null)
							return literal(BetterFile.at(new NativeFileSource(), prop), prop);
						else
							return literal(BetterFile.at(new NativeFileSource(), "./" + configName), "./" + configName);
					};
				}
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
					ObservableConfig config = ObservableConfig.createRoot(configName);
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
				QonfigElementDef modelType = TOOLKIT.get().getElement("abst-model");
				for (QonfigElement child : el.getChildren()) {
					if (modelType.isAssignableFrom(child.getType())) {
						ObservableModelSet.Builder subModel = model.createSubModel(child.getAttributeText(modelType.getAttribute("name")));
						session.put("model", subModel);
						session.getInterpreter().interpret(child, ObservableModelSet.class);
					} else
						session.getInterpreter().interpret(child, Void.class);
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
		abstract class TypedModelThing<T> implements QonfigInterpreter.QonfigValueCreator<Void> {
			@Override
			public Void createValue(QonfigElement element, QonfigInterpretingSession session) throws QonfigInterpretationException {
				ObservableModelSet.Builder model = (ObservableModelSet.Builder) session.get("model");
				ClassView cv = (ClassView) session.get("imports");
				String name = element.getAttributeText(obsTk.getAttribute("abst-model-value", "name"));
				TypeToken<?> type = parseType(element.getAttributeText(obsTk.getAttribute("abst-model-value", "type")), cv);
				String path;
				if (session.get("config-model") != null) {
					path = element.getAttributeText(obsTk.getAttribute("config-value", "config-path"));
					if (path == null)
						path = name;
					Function<ModelSetInstance, ObservableConfigFormat<Object>> format = parseConfigFormat(
						element.getAttribute(obsTk.getAttribute("config-value", "format"), ObservableExpression.class), null, model,
						cv, (TypeToken<Object>) type);
					buildConfig(model, cv, name, (TypeToken<Object>) type, element, path, format);
				} else {
					path = model.getPath();
					if (path == null)
						path = name;
					else
						path = path + "." + name;
					build(model, cv, name, type, element, name);
				}
				return null;
			}

			abstract <V> void build(ObservableModelSet.Builder model, ClassView cv, String name, TypeToken<V> type, QonfigElement element,
				String path) throws QonfigInterpretationException;

			abstract <V> void buildConfig(ObservableModelSet.Builder model, ClassView cv, String name, TypeToken<V> type,
				QonfigElement element, String path, Function<ModelSetInstance, ObservableConfigFormat<V>> format)
					throws QonfigInterpretationException;
		}
		observeInterpreter.createWith("constant", Void.class, new TypedModelThing<SettableValue>() {
			@Override
			<V> void build(ObservableModelSet.Builder model, ClassView cv, String name, TypeToken<V> type, QonfigElement element,
				String path) throws QonfigInterpretationException {
				BiFunction<ModelSetInstance, ExternalModelSet, V> value = parseValue(model, cv, type,
					(ObservableExpression) element.getValue());
				model.with(name, ModelTypes.Value.forType(type), (modelSet, extModels) -> SettableValue
					.asSettable(new ObservableValue.ConstantObservableValue<V>(type, value.apply(modelSet, extModels)) {
						private Object theIdentity;

						@Override
						public Object getIdentity() {
							if (theIdentity == null)
								theIdentity = Identifiable.baseId(path, path);
							return theIdentity;
						}
					}, __ -> "This value is a constant"));
			}

			@Override
			<V> void buildConfig(Builder model, ClassView cv, String name, TypeToken<V> type, QonfigElement element, String path,
				Function<ModelSetInstance, ObservableConfigFormat<V>> format) throws QonfigInterpretationException {
				build(model, cv, name, type, element, path);
			}
		}).createWith("value", Void.class, new TypedModelThing<SettableValue>() {
			@Override
			<V> void build(ObservableModelSet.Builder model, ClassView cv, String name, TypeToken<V> type, QonfigElement element,
				String path) throws QonfigInterpretationException {
				BiFunction<ModelSetInstance, ExternalModelSet, V> value = parseValue(model, cv, type,
					(ObservableExpression) element.getValue());
				model.with(name, ModelTypes.Value.forType(type), (modelSet, extModels) -> {
					SettableValue.Builder<V> builder = SettableValue.build(type);
					builder.withDescription(path);
					if (value != null)
						builder.withValue(value.apply(modelSet, extModels));
					return builder.build();
				});
			}

			@Override
			<V> void buildConfig(Builder model, ClassView cv, String name, TypeToken<V> type, QonfigElement element, String path,
				Function<ModelSetInstance, ObservableConfigFormat<V>> format) throws QonfigInterpretationException {
				if (element.getValue() != null)
					System.err.println("WARNING: Config value " + model.pathTo(name) + " specifies a value, ignored");
				Function<ModelSetInstance, SettableValue<V>> defaultX;
				String defaultS;
				QonfigAttributeDef defAttr = obsTk.getAttribute("config-value", "default");
				if (element.getAttributes().get(defAttr) instanceof ExpressionValueType.Literal) {
					defaultX = null;
					defaultS = element.getAttributeText(defAttr);
				} else if (element.getAttributes().get(defAttr) != null) {
					defaultS = null;
					defaultX = element.getAttribute(defAttr, ObservableExpression.class).evaluate(ModelTypes.Value.forType(type), model,
						cv);
				} else {
					defaultS = null;
					defaultX = null;
				}
				model.with(name, ModelTypes.Value.forType(type), (msi, extModels) -> {
					ObservableConfig config = (ObservableConfig) msi.getModelConfiguration();
					ObservableConfigValueBuilder<V> valueBuilder = config.asValue(type).at(path);
					valueBuilder.at(path);
					if (format != null)
						valueBuilder.withFormat(format.apply(msi));
					if (defaultS != null && config.getChild(path) == null)
						config.set(path, defaultS);
					if (defaultX != null) {
						valueBuilder.getFormat().format(new ObservableConfigParseSession(), //
							defaultX.apply(msi).get(), null, (cia, trivial) -> config.getChild(path, cia, newCfg -> {
								newCfg.setTrivial(trivial);
							}), __ -> {
							}, null);
					}
					SettableValue<V> built = valueBuilder.buildValue(null);
					return built;
				});
			}
		}).createWith("value-set", Void.class, new TypedModelThing<ObservableValueSet>() {
			@Override
			<V> void build(Builder model, ClassView cv, String name, TypeToken<V> type, QonfigElement element, String path)
				throws QonfigInterpretationException {
				throw new QonfigInterpretationException("value-sets can only be used under <config> elements");
			}

			@Override
			<V> void buildConfig(Builder model, ClassView cv, String name, TypeToken<V> type, QonfigElement element, String path,
				Function<ModelSetInstance, ObservableConfigFormat<V>> format) throws QonfigInterpretationException {
				if (!element.getChildrenByRole().get(obsTk.getChild("value-set", "element").getDeclared()).isEmpty())
					System.err.println("WARNING: Config value " + model.pathTo(name) + " specifies elements, ignored");
				if (element.getAttributes().get(obsTk.getAttribute("config-value", "default")) != null)
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
			<V> void build(ObservableModelSet.Builder model, ClassView cv, String name, TypeToken<V> type, QonfigElement element,
				String path) throws QonfigInterpretationException {
				List<BiFunction<ModelSetInstance, ExternalModelSet, V>> values = new ArrayList<>();
				QonfigChildDef.Declared elRole = obsTk.getChild("list", "element").getDeclared();
				for (QonfigElement el : element.getChildrenByRole().get(elRole))
					values.add(parseValue(model, cv, type, (ObservableExpression) el.getValue()));
				prepare(type, element, model, cv);
				add(model, cv, name, type, element, (modelSet, extModels) -> {
					C collection = (C) create(type, modelSet).withDescription(path).build();
					int i = 0;
					for (QonfigElement el : element.getChildrenByRole().get(elRole)) {
						if (!collection.add(values.get(i).apply(modelSet, extModels)))
							System.err.println(
								"Warning: Value " + el.getValue() + " already added to " + element.getType().getName() + " " + name);
						i++;
					}
					return collection;
				});
			}

			@Override
			<V> void buildConfig(Builder model, ClassView cv, String name, TypeToken<V> type, QonfigElement element, String path,
				Function<ModelSetInstance, ObservableConfigFormat<V>> format) throws QonfigInterpretationException {
				if (!element.getChildrenByRole().get(obsTk.getChild("list", "element").getDeclared()).isEmpty())
					System.err.println("WARNING: Config collection " + model.pathTo(name) + " specifies elements, ignored");
				if (element.getAttributes().get(obsTk.getAttribute("config-value", "default")) != null)
					System.err.println("WARNING: Config collection " + model.pathTo(name) + " specifies default, ignored");
				prepare(type, element, model, cv);
				add(model, cv, name, type, element, (msi, extModels) -> {
					ObservableConfig config = (ObservableConfig) msi.getModelConfiguration();
					ObservableConfigValueBuilder<V> valueBuilder = config.asValue(type);
					valueBuilder.at(path);
					if (format != null)
						valueBuilder.withFormat(format.apply(msi));
					ObservableCollection<V> collection = valueBuilder.buildCollection(null);
					return modify(collection, msi);
				});
			}

			protected <V> void prepare(TypeToken<V> type, QonfigElement element, ObservableModelSet models, ClassView cv)
				throws QonfigInterpretationException {
			}

			abstract <V> ObservableCollectionBuilder<V, ?> create(TypeToken<V> type, ModelSetInstance models);

			abstract <V> void add(ObservableModelSet.Builder model, ClassView cv, String name, TypeToken<V> type, QonfigElement element,
				ValueGetter<? extends C> collection);

			abstract <V> C modify(ObservableCollection<V> collection, ModelSetInstance msi);
		}
		observeInterpreter.createWith("list", Void.class, new CollectionCreator<ObservableCollection>() {
			@Override
			<V> ObservableCollectionBuilder<V, ?> create(TypeToken<V> type, ModelSetInstance models) {
				return ObservableCollection.build(type);
			}

			@Override
			<V> void add(Builder model, ClassView cv, String name, TypeToken<V> type, QonfigElement element,
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
			<V> void add(Builder model, ClassView cv, String name, TypeToken<V> type, QonfigElement element,
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
			protected <V> void prepare(TypeToken<V> type, QonfigElement element, ObservableModelSet models, ClassView cv)
				throws QonfigInterpretationException {
				theComparator = parseComparator((TypeToken<Object>) type,
					element.getAttribute(obsTk.getAttribute("sorted", "sort-with"), ObservableExpression.class), models, cv);
			}

			@Override
			<V> ObservableCollectionBuilder<V, ?> create(TypeToken<V> type, ModelSetInstance models) {
				return ObservableCollection.build(type)//
					.distinctSorted(theComparator.apply(models));
			}

			@Override
			<V> void add(Builder model, ClassView cv, String name, TypeToken<V> type, QonfigElement element,
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
			protected <V> void prepare(TypeToken<V> type, QonfigElement element, ObservableModelSet models, ClassView cv)
				throws QonfigInterpretationException {
				theComparator = parseComparator((TypeToken<Object>) type,
					element.getAttribute(obsTk.getAttribute("sorted", "sort-with"), ObservableExpression.class), models, cv);
			}

			@Override
			<V> ObservableCollectionBuilder<V, ?> create(TypeToken<V> type, ModelSetInstance models) {
				return ObservableCollection.build(type)//
					.sortBy(theComparator.apply(models));
			}

			@Override
			<V> void add(Builder model, ClassView cv, String name, TypeToken<V> type, QonfigElement element,
				ValueGetter<? extends ObservableSortedCollection> collection) {
				model.with(name, ModelTypes.SortedCollection.forType(type), (ValueGetter<ObservableSortedCollection<V>>) collection);
			}

			@Override
			<V> ObservableSortedCollection modify(ObservableCollection<V> collection, ModelSetInstance msi) {
				return collection.flow().sorted(theComparator.apply(msi)).collect();
			}
		});
		abstract class BiTypedModelThing<T> implements QonfigInterpreter.QonfigValueCreator<Void> {
			@Override
			public Void createValue(QonfigElement element, QonfigInterpretingSession session) throws QonfigInterpretationException {
				ObservableModelSet.Builder model = (ObservableModelSet.Builder) session.get("model");
				ClassView cv = (ClassView) session.get("imports");
				String name = element.getAttributeText(obsTk.getAttribute("abst-model-value", "name"));
				TypeToken<?> keyType = parseType(element.getAttributeText(obsTk.getAttribute("map-model-value", "key-type")), cv);
				TypeToken<?> valueType = parseType(element.getAttributeText(obsTk.getAttribute("abst-model-value", "type")), cv);
				String path;
				if (session.get("config-model") != null) {
					path = element.getAttributeText(obsTk.getAttribute("config-value", "config-path"));
					if (path == null)
						path = name;
					Function<ModelSetInstance, ObservableConfigFormat<Object>> keyFormat = parseConfigFormat(
						element.getAttribute(obsTk.getAttribute("config-value", "key-format"), ObservableExpression.class),
						element.getAttributeText(obsTk.getAttribute("config-value", "key-default")), model, cv,
						(TypeToken<Object>) keyType);
					Function<ModelSetInstance, ObservableConfigFormat<Object>> valueFormat = parseConfigFormat(
						element.getAttribute(obsTk.getAttribute("config-value", "format"), ObservableExpression.class),
						element.getAttributeText(obsTk.getAttribute("config-value", "default")), model, cv,
						(TypeToken<Object>) valueType);
					buildConfig(model, cv, name, (TypeToken<Object>) keyType, (TypeToken<Object>) valueType, element, path, keyFormat,
						valueFormat);
				} else {
					path = model.getPath();
					if (path == null)
						path = name;
					else
						path = path + "." + name;
					build(model, cv, name, keyType, valueType, element, path);
				}
				return null;
			}

			abstract <K, V> void build(ObservableModelSet.Builder model, ClassView cv, String name, TypeToken<K> keyType,
				TypeToken<V> valueType, QonfigElement element, String path) throws QonfigInterpretationException;

			abstract <K, V> void buildConfig(Builder model, ClassView cv, String name, TypeToken<K> keyType, TypeToken<V> valueType,
				QonfigElement element, String path, Function<ModelSetInstance, ObservableConfigFormat<K>> keyFormat,
				Function<ModelSetInstance, ObservableConfigFormat<V>> valueFormat) throws QonfigInterpretationException;
		}
		abstract class MapCreator<M extends ObservableMap> extends BiTypedModelThing<M> {
			@Override
			<K, V> void build(ObservableModelSet.Builder model, ClassView cv, String name, TypeToken<K> keyType, TypeToken<V> valueType,
				QonfigElement element, String path) throws QonfigInterpretationException {
				List<BiTuple<BiFunction<ModelSetInstance, ExternalModelSet, K>, BiFunction<ModelSetInstance, ExternalModelSet, V>>> entries = new ArrayList<>();
				QonfigChildDef.Declared entryRole = obsTk.getChild("map", "entry").getDeclared();
				for (QonfigElement entry : element.getChildrenByRole().get(entryRole)) {
					BiFunction<ModelSetInstance, ExternalModelSet, K> key = parseValue(model, cv, keyType,
						entry.getAttribute(entryRole.getType().getAttribute("key"), ObservableExpression.class));
					BiFunction<ModelSetInstance, ExternalModelSet, V> v = parseValue(model, cv, valueType,
						entry.getAttribute(entryRole.getType().getAttribute("value"), ObservableExpression.class));
					entries.add(new BiTuple<>(key, v));
				}
				prepare(keyType, valueType, element, model, cv);
				add(model, cv, name, keyType, valueType, element, (modelSet, extModels) -> {
					M map = (M) create(keyType, valueType, modelSet).withDescription(path).build();
					for (int i = 0; i < entries.size(); i++) {
						BiTuple<BiFunction<ModelSetInstance, ExternalModelSet, K>, BiFunction<ModelSetInstance, ExternalModelSet, V>> entry = entries
							.get(i);
						Object key = entry.getValue1().apply(modelSet, extModels);
						Object v = entry.getValue2().apply(modelSet, extModels);
						if (map.put(key, v) != null)
							System.err.println("Warning: Key " + key + " already used for " + element.getType().getName() + " " + name);
					}
					return map;
				});
			}

			@Override
			<K, V> void buildConfig(Builder model, ClassView cv, String name, TypeToken<K> keyType, TypeToken<V> valueType,
				QonfigElement element, String path, Function<ModelSetInstance, ObservableConfigFormat<K>> keyFormat,
				Function<ModelSetInstance, ObservableConfigFormat<V>> valueFormat) throws QonfigInterpretationException {
				if (!element.getChildrenByRole().get(obsTk.getChild("map", "entry").getDeclared()).isEmpty())
					System.err.println("WARNING: Config value " + model.pathTo(name) + " specifies entries, ignored");
				if (element.getAttributes().get(obsTk.getAttribute("config-value", "default")) != null)
					System.err.println("WARNING: Config map " + model.pathTo(name) + " specifies default, ignored");
				prepare(keyType, valueType, element, model, cv);
				add(model, cv, name, keyType, valueType, element, (msi, extModels) -> {
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

			protected <K, V> void prepare(TypeToken<K> keyType, TypeToken<V> valueType, QonfigElement element, ObservableModelSet models,
				ClassView cv) throws QonfigInterpretationException {
			}

			abstract <K, V> ObservableMap.Builder<K, V, ?> create(TypeToken<K> keyType, TypeToken<V> valueType, ModelSetInstance models);

			abstract <K, V> void add(ObservableModelSet.Builder model, ClassView cv, String name, TypeToken<K> keyType,
				TypeToken<V> valueType, QonfigElement element, ValueGetter<? extends M> map);

			protected abstract <K, V> M modify(ObservableMap<K, V> map, ModelSetInstance msi);
		}
		observeInterpreter.createWith("map", Void.class, new MapCreator<ObservableMap>() {
			@Override
			<K, V> ObservableMap.Builder<K, V, ?> create(TypeToken<K> keyType, TypeToken<V> type, ModelSetInstance models) {
				return ObservableMap.build(keyType, type);
			}

			@Override
			<K, V> void add(ObservableModelSet.Builder model, ClassView cv, String name, TypeToken<K> keyType, TypeToken<V> valueType,
				QonfigElement element, ValueGetter<? extends ObservableMap> map) {
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
			protected <K, V> void prepare(TypeToken<K> keyType, TypeToken<V> valueType, QonfigElement element, ObservableModelSet models,
				ClassView cv) throws QonfigInterpretationException {
				theComparator = parseComparator((TypeToken<Object>) keyType,
					element.getAttribute(obsTk.getAttribute("sorted", "sort-with"), ObservableExpression.class), models, cv);
			}

			@Override
			<K, V> ObservableSortedMap.Builder<K, V, ?> create(TypeToken<K> keyType, TypeToken<V> type, ModelSetInstance models) {
				return ObservableSortedMap.build(keyType, type, //
					theComparator.apply(models));
			}

			@Override
			<K, V> void add(ObservableModelSet.Builder model, ClassView cv, String name, TypeToken<K> keyType, TypeToken<V> valueType,
				QonfigElement element, ValueGetter<? extends ObservableSortedMap> map) {
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
			<K, V> void build(ObservableModelSet.Builder model, ClassView cv, String name, TypeToken<K> keyType, TypeToken<V> valueType,
				QonfigElement element, String path) throws QonfigInterpretationException {
				List<BiTuple<BiFunction<ModelSetInstance, ExternalModelSet, K>, BiFunction<ModelSetInstance, ExternalModelSet, V>>> entries = new ArrayList<>();
				QonfigChildDef.Declared entryRole = obsTk.getChild("map", "entry").getDeclared();
				for (QonfigElement entry : element.getChildrenByRole().get(entryRole)) {
					BiFunction<ModelSetInstance, ExternalModelSet, K> key = parseValue(model, cv, keyType,
						entry.getAttribute(entryRole.getType().getAttribute("key"), ObservableExpression.class));
					BiFunction<ModelSetInstance, ExternalModelSet, V> v = parseValue(model, cv, valueType,
						entry.getAttribute(entryRole.getType().getAttribute("value"), ObservableExpression.class));
					entries.add(new BiTuple<>(key, v));
				}
				prepare(keyType, valueType, element, model, cv);
				add(model, cv, name, keyType, valueType, element, (modelSet, extModels) -> {
					M map = (M) create(keyType, valueType, modelSet).withDescription(path).build(null);
					for (int i = 0; i < entries.size(); i++) {
						BiTuple<BiFunction<ModelSetInstance, ExternalModelSet, K>, BiFunction<ModelSetInstance, ExternalModelSet, V>> entry = entries
							.get(i);
						Object key = entry.getValue1().apply(modelSet, extModels);
						Object v = entry.getValue2().apply(modelSet, extModels);
						if (!map.add(key, v))
							System.err.println("Warning: Key " + key + " already used for " + element.getType().getName() + " " + name);
					}
					return map;
				});
			}

			@Override
			<K, V> void buildConfig(Builder model, ClassView cv, String name, TypeToken<K> keyType, TypeToken<V> valueType,
				QonfigElement element, String path, Function<ModelSetInstance, ObservableConfigFormat<K>> keyFormat,
				Function<ModelSetInstance, ObservableConfigFormat<V>> valueFormat) throws QonfigInterpretationException {
				if (!element.getChildrenByRole().get(obsTk.getChild("map", "entry").getDeclared()).isEmpty())
					System.err.println("WARNING: Config value " + model.pathTo(name) + " specifies entries, ignored");
				if (element.getAttributes().get(obsTk.getAttribute("config-value", "default")) != null)
					System.err.println("WARNING: Config multi-map " + model.pathTo(name) + " specifies default, ignored");
				prepare(keyType, valueType, element, model, cv);
				add(model, cv, name, keyType, valueType, element, (msi, extModels) -> {
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

			protected <K, V> void prepare(TypeToken<K> keyType, TypeToken<V> valueType, QonfigElement element, ObservableModelSet models,
				ClassView cv) throws QonfigInterpretationException {
			}

			abstract <K, V> ObservableMultiMap.Builder<K, V, ?> create(TypeToken<K> keyType, TypeToken<V> type, ModelSetInstance models);

			abstract <K, V> void add(ObservableModelSet.Builder model, ClassView cv, String name, TypeToken<K> keyType,
				TypeToken<V> valueType, QonfigElement element, ValueGetter<? extends M> map);

			protected abstract <K, V> M modify(ObservableMultiMap<K, V> map, ModelSetInstance msi);
		}
		observeInterpreter.createWith("multi-map", Void.class, new MultiMapCreator<ObservableMultiMap>() {
			@Override
			<K, V> ObservableMultiMap.Builder<K, V, ?> create(TypeToken<K> keyType, TypeToken<V> type, ModelSetInstance models) {
				return ObservableMultiMap.build(keyType, type);
			}

			@Override
			<K, V> void add(ObservableModelSet.Builder model, ClassView cv, String name, TypeToken<K> keyType, TypeToken<V> valueType,
				QonfigElement element, ValueGetter<? extends ObservableMultiMap> map) {
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
			protected <K, V> void prepare(TypeToken<K> keyType, TypeToken<V> valueType, QonfigElement element, ObservableModelSet models,
				ClassView cv) throws QonfigInterpretationException {
				theComparator = parseComparator((TypeToken<Object>) keyType,
					element.getAttribute(obsTk.getAttribute("sorted", "sort-with"), ObservableExpression.class), models, cv);
			}

			@Override
			<K, V> ObservableSortedMultiMap.Builder<K, V, ?> create(TypeToken<K> keyType, TypeToken<V> type, ModelSetInstance models) {
				return ObservableMultiMap.build(keyType, type)//
					.sortedBy(theComparator.apply(models));
			}

			@Override
			<K, V> void add(ObservableModelSet.Builder model, ClassView cv, String name, TypeToken<K> keyType, TypeToken<V> valueType,
				QonfigElement element, ValueGetter<? extends ObservableSortedMultiMap> map) {
				model.with(name, ModelTypes.SortedMultiMap.forType(keyType, valueType), (ValueGetter<ObservableSortedMultiMap<K, V>>) map);
			}

			@Override
			protected <K, V> ObservableSortedMultiMap<K, V> modify(ObservableMultiMap<K, V> map, ModelSetInstance msi) {
				return (ObservableSortedMultiMap<K, V>) map.flow().withKeys(keys -> keys.distinctSorted(theComparator.apply(msi), false))
					.gather();
			}
		});
		abstract class ExtModelValidator<T> implements QonfigInterpreter.QonfigValueCreator<Void> {
			@Override
			public Void createValue(QonfigElement element, QonfigInterpretingSession session) throws QonfigInterpretationException {
				ObservableModelSet.Builder model = (ObservableModelSet.Builder) session.get("model");
				ClassView cv = (ClassView) session.get("imports");
				String name = element.getAttributeText(obsTk.getAttribute("abst-model-value", "name"));
				String typeS = element.getAttributeText(obsTk.getAttribute("abst-model-value", "type"));
				TypeToken<?> type = parseType(typeS, cv);
				String path;
				if (model.getPath() == null)
					path = name;
				else {
					String pathBuilder = name;
					QonfigElement modelEl = element.getParent();
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
		abstract class ExtBiTypedModelValidator<T> implements QonfigInterpreter.QonfigValueCreator<Void> {
			@Override
			public Void createValue(QonfigElement element, QonfigInterpretingSession session) throws QonfigInterpretationException {
				ObservableModelSet.Builder model = (ObservableModelSet.Builder) session.get("model");
				ClassView cv = (ClassView) session.get("imports");
				String name = element.getAttributeText(obsTk.getAttribute("abst-model-value", "name"));
				String keyTypeS = element.getAttributeText(obsTk.getAttribute("map-model-value", "key-type"));
				String valueTypeS = element.getAttributeText(obsTk.getAttribute("abst-model-value", "type"));
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
		observeInterpreter.createWith("transform", Void.class, new QonfigInterpreter.QonfigValueCreator<Void>() {
			@Override
			public Void createValue(QonfigElement element, QonfigInterpretingSession session) throws QonfigInterpretationException {
				ObservableModelSet.Builder model = (ObservableModelSet.Builder) session.get("model");
				ClassView cv = (ClassView) session.get("imports");
				String name = element.getAttributeText(obsTk.getAttribute("abst-model-value", "name"));
				String sourceName = element.getAttributeText(obsTk.getAttribute("transform", "source"));
				ValueContainer<?, ?> source = model.get(sourceName, true);
				ModelType<?> type = source.getType().getModelType();
				if (type == ModelTypes.Event)
					transformEvent((ValueContainer<Object, Observable<Object>>) source, element, model, cv, name, 0);
				else if (type == ModelTypes.Value)
					transformValue((ValueContainer<Object, SettableValue<Object>>) source, element, model, cv, name, 0);
				else if (type == ModelTypes.Collection || type == ModelTypes.SortedCollection || type == ModelTypes.Set
					|| type == ModelTypes.SortedSet)
					transformCollection((ValueContainer<Object, ObservableCollection<Object>>) source, element, model, cv, name, 0);
				else
					throw new IllegalArgumentException("Transformation unsupported for source of type " + source.getType().getModelType());
				return null;
			}
		});
		observeInterpreter.createWith("file-source", Void.class, new QonfigInterpreter.QonfigValueCreator<Void>() {
			@Override
			public Void createValue(QonfigElement element, QonfigInterpretingSession session) throws QonfigInterpretationException {
				ObservableModelSet.Builder model = (ObservableModelSet.Builder) session.get("model");
				String name = element.getAttributeText(obsTk.getAttribute("abst-model-value", "name"));
				ValueGetter<SettableValue<FileDataSource>> source;
				switch (element.getAttributeText(obsTk.getAttribute("file-source", "type"))) {
				case "native":
					source = (modelSet, extModels) -> literal(new NativeFileSource(), "native-file-source");
					break;
				case "sftp":
					throw new UnsupportedOperationException("Not yet implemented");
				default:
					throw new IllegalArgumentException(
						"Unrecognized file-source type: "
							+ element.getAttributeText(obsTk.getAttribute("file-source", "type")));
				}
				if (!element.getChildrenByRole().get(obsTk.getChild("file-source", "archive").getDeclared()).isEmpty()) {
					Set<String> archiveMethodStrs = new HashSet<>();
					List<ArchiveEnabledFileSource.FileArchival> archiveMethods = new ArrayList<>(5);
					for (QonfigElement archive : element.getChildrenByRole().get(obsTk.getChild("file-source", "archive").getDeclared())) {
						String type = archive.getAttributeText(obsTk.getAttribute("archive-method", "type"));
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
					if (element.getAttributes().get(obsTk.getAttribute("file-source", "max-archive-depth")) == null)
						maxZipDepth = null;
					else {
						String mad = element.getAttributeText(obsTk.getAttribute("file-source", "max-archive-depth"));
						int zipDepth;
						try {
							zipDepth = Integer.parseInt(mad);
						} catch (NumberFormatException e) {
							zipDepth = -1;
						}
						if (zipDepth >= 0) {
							int finalZD = zipDepth;
							maxZipDepth = models -> literal(finalZD, mad);
						} else {
							maxZipDepth = model.get(mad, ModelTypes.Value.forType(Integer.class));
						}
					}
					ValueGetter<SettableValue<FileDataSource>> root = source;
					source = (modelSet, extModels) -> root.get(modelSet, extModels).transformReversible(FileDataSource.class, //
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
				model.with(name, ModelTypes.Value.forType(FileDataSource.class), source);
				return null;
			}
		});
		observeInterpreter.createWith("format", Void.class, new QonfigInterpreter.QonfigValueCreator<Void>() {
			@Override
			public Void createValue(QonfigElement element, QonfigInterpretingSession session) throws QonfigInterpretationException {
				return null;
			}

			@Override
			public Void postModification(Void value, QonfigElement element, QonfigInterpretingSession session)
				throws QonfigInterpretationException {
				ObservableModelSet.Builder model = (ObservableModelSet.Builder) session.get("model");
				String name = element.getAttributeText(obsTk.getAttribute("abst-model-value", "name"));
				if (model.get(name, false) == null)
					throw new QonfigInterpretationException(
						"Modifier for type=" + element.getAttributeText(obsTk.getAttribute("format", "type"))
						+ " not registered or did not create a format for " + name);
				return value;
			}
		});
		observeInterpreter.modifyWith("text", Void.class, new QonfigInterpreter.QonfigValueModifier<Void>() {
			@Override
			public Void modifyValue(Void value, QonfigElement element, QonfigInterpretingSession session)
				throws QonfigInterpretationException {
				ObservableModelSet.Builder model = (ObservableModelSet.Builder) session.get("model");
				String name = element.getAttributeText(obsTk.getAttribute("abst-model-value", "name"));
				model.with(name,
					ModelTypes.Value.forType(TypeTokens.get().keyFor(Format.class).<Format<String>> parameterized(String.class)),
					literalGetter(SpinnerFormat.NUMERICAL_TEXT, "text"));
				return null;
			}
		});
		observeInterpreter.modifyWith("int-format", Void.class, new QonfigInterpreter.QonfigValueModifier<Void>() {
			@Override
			public Void modifyValue(Void value, QonfigElement element, QonfigInterpretingSession session)
				throws QonfigInterpretationException {
				ObservableModelSet.Builder model = (ObservableModelSet.Builder) session.get("model");
				String name = element.getAttributeText(obsTk.getAttribute("abst-model-value", "name"));
				SpinnerFormat.IntFormat format = SpinnerFormat.INT;
				String sep=element.getAttributeText(obsTk.getAttribute("int-format", "grouping-separator"));
				if (sep != null) {
					if (sep.length() != 0)
						System.err.println("WARNING: grouping-separator must be a single character");
					else
						format = format.withGroupingSeparator(sep.charAt(0));
				}
				model.with(name,
					ModelTypes.Value.forType(TypeTokens.get().keyFor(Format.class).<Format<Integer>> parameterized(Integer.class)),
					literalGetter(format, "int"));
				return null;
			}
		});
		observeInterpreter.modifyWith("long-format", Void.class, new QonfigInterpreter.QonfigValueModifier<Void>() {
			@Override
			public Void modifyValue(Void value, QonfigElement element, QonfigInterpretingSession session)
				throws QonfigInterpretationException {
				ObservableModelSet.Builder model = (ObservableModelSet.Builder) session.get("model");
				String name = element.getAttributeText(obsTk.getAttribute("abst-model-value", "name"));
				SpinnerFormat.LongFormat format = SpinnerFormat.LONG;
				String sep = element.getAttributeText(obsTk.getAttribute("int-format", "grouping-separator"));
				if (sep != null) {
					if (sep.length() != 0)
						System.err.println("WARNING: grouping-separator must be a single character");
					else
						format = format.withGroupingSeparator(sep.charAt(0));
				}
				model.with(name, ModelTypes.Value.forType(TypeTokens.get().keyFor(Format.class).<Format<Long>> parameterized(Long.class)),
					literalGetter(format, "long"));
				return null;
			}
		});
		observeInterpreter.modifyWith("double", Void.class, new QonfigInterpreter.QonfigValueModifier<Void>() {
			@Override
			public Void modifyValue(Void value, QonfigElement element, QonfigInterpretingSession session)
				throws QonfigInterpretationException {
				ObservableModelSet.Builder model = (ObservableModelSet.Builder) session.get("model");
				String name = element.getAttributeText(obsTk.getAttribute("abst-model-value", "name"));
				int sigDigs = Integer.parseInt(element.getAttributeText(obsTk.getAttribute("double", "sig-digs")));
				Format.SuperDoubleFormatBuilder builder = Format.doubleFormat(sigDigs);
				String unit = element.getAttributeText(obsTk.getAttribute("double", "unit"));
				boolean withMetricPrefixes = element.getAttribute(obsTk.getAttribute("double", "metric-prefixes"), boolean.class);
				boolean withMetricPrefixesP2 = element.getAttribute(obsTk.getAttribute("double", "metric-prefixes-p2"), boolean.class);
				QonfigChildDef.Declared prefixRole = obsTk.getChild("double", "prefix").getDeclared();
				if (unit != null) {
					builder.withUnit(unit, element.getAttribute(obsTk.getAttribute("double", "unit-required"), boolean.class));
					if (withMetricPrefixes) {
						if (withMetricPrefixesP2)
							session.withWarning("Both 'metric-prefixes' and 'metric-prefixes-p2' specified");
						builder.withMetricPrefixes();
					} else if (withMetricPrefixesP2)
						builder.withMetricPrefixesPower2();
					for (QonfigElement prefix : element.getChildrenByRole().get(prefixRole)) {
						String prefixName = prefix.getAttributeText(prefixRole.getType().getAttribute("name"));
						String expS = prefix.getAttributeText(prefixRole.getType().getAttribute("exp"));
						String multS = prefix.getAttributeText(prefixRole.getType().getAttribute("mult"));
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
					if (!element.getChildrenByRole().get(prefixRole).isEmpty())
						session.withWarning("prefixes specified without a unit");
				}
				TypeToken<Format<Double>> formatType = TypeTokens.get().keyFor(Format.class).<Format<Double>> parameterized(Double.class);
				ModelInstanceType<SettableValue, SettableValue<Format<Double>>> formatInstanceType = ModelTypes.Value.forType(formatType);
				model.with(name, formatInstanceType, literalGetter(formatType, builder.build(), "double"));
				return null;
			}
		});
		observeInterpreter.modifyWith("instant", Void.class, new QonfigInterpreter.QonfigValueModifier<Void>() {
			@Override
			public Void modifyValue(Void value, QonfigElement element, QonfigInterpretingSession session)
				throws QonfigInterpretationException {
				ObservableModelSet.Builder model = (ObservableModelSet.Builder) session.get("model");
				ClassView cv = (ClassView) session.get("imports");
				String name = element.getAttributeText(obsTk.getAttribute("abst-model-value", "name"));
				String dayFormat = element.getAttributeText(obsTk.getAttribute("instant", "day-format"));
				TimeEvaluationOptions options = TimeUtils.DEFAULT_OPTIONS;
				String tzs = element.getAttributeText(obsTk.getAttribute("instant", "time-zone"));
				if (tzs != null) {
					TimeZone timeZone = TimeZone.getTimeZone(tzs);
					if (timeZone.getRawOffset() == 0 && !timeZone.useDaylightTime()//
						&& !(tzs.equalsIgnoreCase("GMT") || tzs.equalsIgnoreCase("Z")))
						throw new QonfigInterpretationException("Unrecognized time-zone '" + tzs + "'");
					options = options.withTimeZone(timeZone);
				}
				try {
					options = options.withMaxResolution(
						TimeUtils.DateElementType.valueOf(element.getAttributeText(obsTk.getAttribute("instant", "max-resolution"))));
				} catch (IllegalArgumentException e) {
					session.withWarning(
						"Unrecognized instant resolution: '" + element.getAttributeText(obsTk.getAttribute("instant", "max-resolution")));
				}
				options = options.with24HourFormat(element.getAttribute(obsTk.getAttribute("instant", "format-24h"), boolean.class));
				String rteS = element.getAttributeText(obsTk.getAttribute("instant", "relative-eval-type"));
				try {
					options = options.withEvaluationType(TimeUtils.RelativeTimeEvaluation.valueOf(rteS));
				} catch (IllegalArgumentException e) {
					session.withWarning("Unrecognized relative evaluation type: '" + rteS);
				}
				TimeEvaluationOptions fOptions = options;
				SpinnerFormat<Instant> format = SpinnerFormat.flexDate(Instant::now, dayFormat, __ -> fOptions);
				TypeToken<Format<Instant>> formatType = TypeTokens.get().keyFor(Format.class)
					.<Format<Instant>> parameterized(Instant.class);
				ModelInstanceType<SettableValue, SettableValue<Format<Instant>>> formatInstanceType = ModelTypes.Value.forType(formatType);
				Function<ModelSetInstance, Supplier<Instant>> relativeTo;
				Object relativeV = element.getAttributes().get(obsTk.getAttribute("instant", "relative-to"));
				if (relativeV == null) {
					model.with(name, formatInstanceType, literalGetter(formatType, format, "instant"));
				} else if (relativeV instanceof String) {
					try {
						Instant ri = format.parse((String) relativeV);
						relativeTo = msi -> LambdaUtils.constantSupplier(ri, (String) relativeV, null);
						model.with(name, formatInstanceType, (msi, extModels) -> {
							return literal(SpinnerFormat.flexDate(relativeTo.apply(msi), dayFormat, __ -> fOptions), "instant");
						});
					} catch (ParseException e) {
						session.withWarning("Malformatted relative time: '" + relativeV, e);
						model.with(name, formatInstanceType, literalGetter(formatType, format, "instant"));
					}
				} else {
					relativeTo = ((ObservableExpression) relativeV).findMethod(Instant.class, model, cv)
						.withOption(BetterList.empty(), null).find0();
					model.with(name, formatInstanceType, (msi, extModels) -> {
						return literal(SpinnerFormat.flexDate(relativeTo.apply(msi), dayFormat, __ -> fOptions), "instant");
					});
				}
				return null;
			}
		});
		observeInterpreter.modifyWith("file", Void.class, new QonfigInterpreter.QonfigValueModifier<Void>() {
			@Override
			public Void modifyValue(Void value, QonfigElement element, QonfigInterpretingSession session)
				throws QonfigInterpretationException {
				ObservableModelSet.Builder model = (ObservableModelSet.Builder) session.get("model");
				ClassView cv = (ClassView) session.get("imports");
				String name = element.getAttributeText(obsTk.getAttribute("abst-model-value", "name"));
				Function<ModelSetInstance, SettableValue<BetterFile.FileDataSource>> fileSource;
				ObservableExpression fileSourceEx = element.getAttribute(obsTk.getAttribute("file", "file-source"),
					ObservableExpression.class);
				if (fileSourceEx != null)
					fileSource = fileSourceEx.evaluate(//
						ModelTypes.Value.forType(BetterFile.FileDataSource.class), model, cv);
				else
					fileSource = literalContainer(ModelTypes.Value.forType(BetterFile.FileDataSource.class), new NativeFileSource(),
						"native");
				ObservableExpression workingDirEx = element.getAttribute(obsTk.getAttribute("file", "working-dir"),
					ObservableExpression.class);
				Function<ModelSetInstance, SettableValue<String>> workingDir;
				if (workingDirEx != null)
					workingDir = workingDirEx.evaluate(ModelTypes.Value.forType(String.class), model, cv);
				else
					workingDir = null;
				boolean allowEmpty = element.getAttribute(obsTk.getAttribute("file", "allow-empty"), boolean.class);
				TypeToken<Format<BetterFile>> fileFormatType = TypeTokens.get().keyFor(Format.class)
					.<Format<BetterFile>> parameterized(BetterFile.class);
				model.with(name, ModelTypes.Value.forType(fileFormatType), (models, extModels) -> {
					SettableValue<BetterFile.FileDataSource> fds = fileSource.apply(models);
					return SettableValue.asSettable(//
						fds.transform(fileFormatType, tx -> tx.map(fs -> {
							BetterFile workingDirFile = BetterFile.at(fs, workingDir == null ? "." : workingDir.apply(models).get());
							return new BetterFile.FileFormat(fs, workingDirFile, allowEmpty);
						})), //
						__ -> "Not reversible");
				});
				return null;
			}
		});
		observeInterpreter.modifyWith("regex-format", Void.class, new QonfigInterpreter.QonfigValueModifier<Void>() {
			@Override
			public Void modifyValue(Void value, QonfigElement element, QonfigInterpretingSession session)
				throws QonfigInterpretationException {
				ObservableModelSet.Builder model = (ObservableModelSet.Builder) session.get("model");
				String name = element.getAttributeText(obsTk.getAttribute("abst-model-value", "name"));
				ModelInstanceType<SettableValue, SettableValue<Format<Pattern>>> patternType = ModelTypes.Value
					.forType(TypeTokens.get().keyFor(Format.class).<Format<Pattern>> parameterized(Pattern.class));
				model.with(name, patternType, literalGetter(Format.PATTERN, "regex-format"));
				return null;
			}
		});
		observeInterpreter.createWith("simple-config-format", Void.class, new QonfigInterpreter.QonfigValueCreator<Void>() {
			@Override
			public Void createValue(QonfigElement element, QonfigInterpretingSession session) throws QonfigInterpretationException {
				ObservableModelSet.Builder model = (ObservableModelSet.Builder) session.get("model");
				ClassView cv = (ClassView) session.get("imports");
				String name = element.getAttributeText(obsTk.getAttribute("abst-model-value", "name"));
				TypeToken<Object> valueType = (TypeToken<Object>) parseType(
					element.getAttributeText(obsTk.getAttribute("abst-model-value", "type")), cv);
				ValueContainer<SettableValue, SettableValue<Format<Object>>> format;
				ModelInstanceType<SettableValue, SettableValue<Format<Object>>> formatType = ModelTypes.Value
					.forType(TypeTokens.get().keyFor(Format.class).parameterized(valueType));
				Function<ModelSetInstance, Object> defaultValue;
				String defaultS = element.getAttributeText(obsTk.getAttribute("config-value", "default"));
				ObservableExpression formatEx = element.getAttribute(obsTk.getAttribute("config-value", "format"),
					ObservableExpression.class);
				if (formatEx != null) {
					format = formatEx.evaluate(formatType, model, cv);
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
					format = literalContainer(formatType, (Format<Object>) f, type.getSimpleName());
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
							throw new QonfigInterpretationException("default value '" + defaultS + ", type " + defaultV.getClass()
							+ ", is incompatible with value type " + valueType);
						defaultValue = msi -> defaultV;
					}
				}
				TypeToken<ObservableConfigFormat<Object>> ocfType = TypeTokens.get().keyFor(ObservableConfigFormat.class)
					.<ObservableConfigFormat<Object>> parameterized(valueType);
				ModelType.ModelInstanceType<SettableValue, SettableValue<ObservableConfigFormat<Object>>> ocfInstanceType;
				ocfInstanceType = ModelTypes.Value.<ObservableConfigFormat<Object>> forType(ocfType);
				ValueGetter<SettableValue<ObservableConfigFormat<Object>>> getter = (models, extModels) -> {
					SettableValue<Format<Object>> formatObj = format.get(models);
					Supplier<Object> defaultV = defaultValue == null ? null : () -> defaultValue.apply(models);
					return SettableValue.asSettable(formatObj.transform(ocfType, tx -> tx.nullToNull(true)//
						.map(f -> ObservableConfigFormat.ofQommonFormat(f, defaultV))), __ -> "Not reversible");
				};
				model.with(name, ocfInstanceType, getter);
				return null;
			}
		});
		return interpreter;
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
			opts -> opts.withMaxResolution(TimeUtils.DateElementType.Second).withEvaluationType(TimeUtils.RelativeTimeEvaluation.Past));
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
						col -> col.formatText(t -> durationFormat.print(t)).withWidths(50, 90, 500))//
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

	<T> BiFunction<ModelSetInstance, ExternalModelSet, T> parseValue(ObservableModelSet models, ClassView cv, TypeToken<T> type,
		ObservableExpression expression)
			throws QonfigInterpretationException {
		if (expression == null)
			return null;
		else if (expression instanceof ExpressionValueType.Literal) {
			ValueParser parser = theParsers.get(TypeTokens.get().wrap(TypeTokens.getRawType(type)), false);
			if (parser == null)
				throw new QonfigInterpretationException("No parser configured for type " + type);
			return parser.parseModelValue(models, type, expression.toString());
		} else {
			@SuppressWarnings("rawtypes")
			ValueContainer<SettableValue, SettableValue<T>> container = expression.evaluate(ModelTypes.Value.forType(type), models, cv);
			return (msi, ext) -> container.apply(msi).get();
		}
	}

	private void transformEvent(ValueContainer<Object, Observable<Object>> source, QonfigElement transform,
		ObservableModelSet.Builder model, ClassView cv, String name, int startOp) {
		QonfigToolkit obsTk = TOOLKIT.get();
		// skip
		// noInit
		// take
		// takeUntil
		List<QonfigElement> ops = (List<QonfigElement>) transform.getChildrenByRole().get(obsTk.getChild("transform", "op").getDeclared());
		for (int i = startOp; i < ops.size(); i++) {
			QonfigElement op = ops.get(i);
			switch (op.getType().getName()) {
			case "no-init":
				model.with(name, ModelTypes.Event.forType((TypeToken<Object>) source.getType().getType(0)),
					(modelSet, extModels) -> source.get(modelSet).noInit());
				break;
			case "skip":
				int times = Integer.parseInt(op.getAttributeText(obsTk.getAttribute("skip", "times")));
				model.with(name, ModelTypes.Event.forType((TypeToken<Object>) source.getType().getType(0)),
					(modelSet, extModels) -> source.get(modelSet).skip(times));
				break;
			case "take":
				times = Integer.parseInt(op.getAttributeText(obsTk.getAttribute("take", "times")));
				model.with(name, ModelTypes.Event.forType((TypeToken<Object>) source.getType().getType(0)),
					(modelSet, extModels) -> source.get(modelSet).take(times));
				break;
			case "take-until":
				throw new UnsupportedOperationException("Not yet implemented");// TODO
			case "map-to":
				throw new UnsupportedOperationException("Not yet implemented");// TODO
			case "refresh":
				throw new UnsupportedOperationException("Not yet implemented");// TODO
			case "filter":
				throw new UnsupportedOperationException("Not yet implemented");// TODO
			case "filter-by-type":
				throw new UnsupportedOperationException("Not yet implemented");// TODO
			case "flatten":
				throw new UnsupportedOperationException("Not yet implemented");// TODO
			case "unmodifiable":
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
				throw new IllegalArgumentException("Operation of type " + op.getType().getName() + " is not supported for values");
			default:
				throw new IllegalArgumentException("Unrecognized operation type: " + op.getType().getName());
			}
		}
	}

	private interface ValueTransform {
		SettableValue<Object> transform(SettableValue<Object> source, ModelSetInstance models);
	}

	private void transformValue(ValueContainer<Object, SettableValue<Object>> source, QonfigElement transform,
		ObservableModelSet.Builder model, ClassView cv, String name, int startOp) throws QonfigInterpretationException {
		QonfigToolkit obsTk = TOOLKIT.get();
		ValueTransform transformFn = (v, models) -> v;
		TypeToken<Object> currentType = (TypeToken<Object>) source.getType().getType(0);
		List<QonfigElement> ops = (List<QonfigElement>) transform.getChildrenByRole().get(obsTk.getChild("transform", "op").getDeclared());
		for (int i = startOp; i < ops.size(); i++) {
			QonfigElement op = ops.get(i);
			ValueTransform prevTransformFn = transformFn;
			switch (op.getType().getName()) {
			case "take-until":
				throw new UnsupportedOperationException("Not yet implemented");// TODO
			case "map-to":
				ParsedTransformation ptx = mapTransformation(currentType, op, model, cv);
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
				Function<ModelSetInstance, Observable<?>> refresh = op
				.getAttribute(obsTk.getAttribute("refresh", "on"), ObservableExpression.class)
				.evaluate(ModelTypes.Event.any(), model, cv);
				transformFn = (v, models) -> prevTransformFn.transform(v, models).refresh(refresh.apply(models));
				break;
			case "unmodifiable":
				boolean allowUpdates = op.getAttribute(obsTk.getAttribute("unmodifiable", "allow-updates"), Boolean.class);
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
				if (op.getAttributes().get(obsTk.getAttribute("flatten", "function")) != null) {
					MethodFinder<Object, Object, Object, Object> finder = op
						.getAttribute(obsTk.getAttribute("flatten", "function"), ObservableExpression.class)
						.<Object, Object, Object, Object> findMethod(TypeTokens.get().of(Object.class), model, cv)//
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
				boolean nullToNull = op.getAttribute(obsTk.getAttribute("flatten", "null-to-null"), boolean.class);
				if (ObservableValue.class.isAssignableFrom(resultClass)) {
					throw new UnsupportedOperationException("Not yet implemented");// TODO
				} else if (ObservableCollection.class.isAssignableFrom(resultClass)) {
					ModelInstanceType<Object, ObservableCollection<Object>> modelType;
					TypeToken<Object> elementType = (TypeToken<Object>) resultType
						.resolveType(ObservableCollection.class.getTypeParameters()[0]);
					if (ObservableSet.class.isAssignableFrom(resultClass))
						modelType = (ModelInstanceType<Object, ObservableCollection<Object>>) (ModelInstanceType<?, ?>) ModelTypes.Set
						.forType(elementType);
					else
						modelType = (ModelInstanceType<Object, ObservableCollection<Object>>) (ModelInstanceType<?, ?>) ModelTypes.Collection
						.forType(elementType);
					ValueTransform penultimateTransform = transformFn;
					ValueContainer<Object, ObservableCollection<Object>> collectionContainer = new ValueContainer<Object, ObservableCollection<Object>>() {
						@Override
						public ModelInstanceType<Object, ObservableCollection<Object>> getType() {
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
					transformCollection(collectionContainer, transform, model, cv, name, i + 1);
					return;
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
				throw new IllegalArgumentException("Operation of type " + op.getType().getName() + " is not supported for values");
			default:
				throw new IllegalArgumentException("Unrecognized operation type: " + op.getType().getName());
			}
		}

		ValueTransform finalTransform = transformFn;
		model.with(name, ModelTypes.Value.forType(currentType),
			(modelSet, extModel) -> finalTransform.transform(source.get(modelSet), modelSet));
	}

	private interface CollectionTransform {
		ObservableCollection.CollectionDataFlow<Object, ?, Object> transform(
			ObservableCollection.CollectionDataFlow<Object, ?, Object> source, ModelSetInstance models);
	}

	private void transformCollection(ValueContainer<Object, ObservableCollection<Object>> source, QonfigElement transform,
		ObservableModelSet.Builder model, ClassView cv, String name, int startOp) throws QonfigInterpretationException {
		QonfigToolkit obsTk = TOOLKIT.get();
		CollectionTransform transformFn = (src, models) -> src;
		TypeToken<Object> currentType = (TypeToken<Object>) source.getType().getType(0);
		List<QonfigElement> ops = (List<QonfigElement>) transform.getChildrenByRole().get(obsTk.getChild("transform", "op").getDeclared());
		for (int i = startOp; i < ops.size(); i++) {
			QonfigElement op = ops.get(i);
			CollectionTransform prevTransform = transformFn;
			switch (op.getType().getName()) {
			case "map-to":
				ParsedTransformation ptx = mapTransformation(currentType, op, model, cv);
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
				.getAttribute(obsTk.getAttribute("refresh", "on"), ObservableExpression.class)
				.evaluate(ModelTypes.Event.any(), model, cv);
				transformFn = (v, models) -> prevTransform.transform(v, models).refresh(refresh.apply(models));
				break;
			case "refresh-each":
				throw new UnsupportedOperationException("Not yet implemented");// TODO
			case "distinct":
				boolean useFirst = op.getAttribute(obsTk.getAttribute("distinct", "use-first"), Boolean.class);
				boolean preserveSourceOrder = op.getAttribute(obsTk.getAttribute("distinct", "preserve-source-order"), Boolean.class);
				ObservableExpression sortWith = op.getAttribute(obsTk.getAttribute("distinct", "sort-with"), ObservableExpression.class);
				if (sortWith != null) {
					if (preserveSourceOrder)
						System.err.println("WARNING: preserve-source-order cannot be used with sorted collections,"
							+ " as order is determined by the values themselves");
					Function<ModelSetInstance, Comparator<Object>> comparator = parseComparator(currentType, sortWith, model, cv);
					transformFn = (src, models) -> prevTransform.transform(src, models).distinctSorted(comparator.apply(models), useFirst);
				} else
					transformFn = (src, models) -> prevTransform.transform(src, models)
					.distinct(uo -> uo.useFirst(useFirst).preserveSourceOrder(preserveSourceOrder));
					break;
			case "sort":
				Function<ModelSetInstance, Comparator<Object>> comparator = parseComparator(currentType,
					op.getAttribute(obsTk.getAttribute("sort", "sort-with"), ObservableExpression.class), model, cv);
				transformFn = (src, models) -> prevTransform.transform(src, models).sorted(comparator.apply(models));
				break;
			case "with-equivalence":
				throw new UnsupportedOperationException("Not yet implemented");// TODO
			case "unmodifiable":
				boolean allowUpdates = op.getAttribute(obsTk.getAttribute("unmodifiable", "allow-updates"), boolean.class);
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
				if (op.getAttributes().get(obsTk.getAttribute("flatten", "function")) != null) {
					MethodFinder<Object, Object, Object, Object> finder = op
						.getAttribute(obsTk.getAttribute("flatten", "function"), ObservableExpression.class)
						.<Object, Object, Object, Object> findMethod(TypeTokens.get().of(Object.class), model, cv)//
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
				throw new IllegalArgumentException("Operation type " + op.getType().getName() + " is not supported for collections");
			default:
				throw new IllegalArgumentException("Unrecognized operation type: " + op.getType().getName());
			}
		}
		Boolean active = transform.getAttribute(obsTk.getAttribute("transform", "active"), Boolean.class);
		CollectionTransform finalTransform = transformFn;
		model.with(name, ModelTypes.Collection.forType(currentType), (models, extModels) -> {
			ObservableCollection.CollectionDataFlow<Object, ?, Object> flow = finalTransform.transform(//
				source.get(models).flow(), models);
			if (active == null)
				return flow.collect();
			else if (active)
				return flow.collectActive(null);
			else
				return flow.collectPassive();
		});
	}

	private ParsedTransformation mapTransformation(TypeToken<Object> currentType, QonfigElement op, ObservableModelSet model, ClassView cv)
		throws QonfigInterpretationException {
		QonfigToolkit obsTk = TOOLKIT.get();
		List<ValueContainer<?, ? extends SettableValue<?>>> combined = new ArrayList<>(
			op.getChildrenByRole().get(obsTk.getChild("map-to", "combined-value").getDeclared()).size());
		for (QonfigElement combine : op.getChildrenByRole().get(obsTk.getChild("map-to", "combined-value").getDeclared())) {
			String name = combine.getValue().toString();
			combined.add(model.get(name, ModelTypes.Value));
		}
		TypeToken<?>[] argTypes = new TypeToken[combined.size()];
		for (int i = 0; i < argTypes.length; i++)
			argTypes[i] = combined.get(i).getType().getType(0);
		MethodFinder<Object, TransformationValues<?, ?>, ?, Object> map;
		map = op.getAttribute(obsTk.getAttribute("map-to", "function"), ObservableExpression.class).findMethod(TypeTokens.get().OBJECT,
			model, cv);
		map.withOption(argList(currentType).with(argTypes), (src, tv, __, args, models) -> {
			args[0] = src;
			for (int i = 0; i < combined.size(); i++)
				args[i + 1] = tv.get(combined.get(i).get(models));
		});
		Function<ModelSetInstance, BiFunction<Object, TransformationValues<?, ?>, Object>> mapFn = map.find2();
		TypeToken<Object> targetType = (TypeToken<Object>) map.getResultType();
		QonfigElement reverseEl = op.getChildrenByRole().get(obsTk.getChild("map-to", "reverse").getDeclared()).peekFirst();
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
					String reverseType = reverseEl.getAttributeText(obsTk.getAttribute("map-reverse", "type"));
					if (reverseType.equals("replace-source")) {
						modifying = false;
						stateful = reverseEl.getAttribute(obsTk.getAttribute("replace-source", "stateful"), Boolean.class);
						inexact = reverseEl.getAttribute(obsTk.getAttribute("replace-source", "inexact"), Boolean.class);
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
							.getAttribute(obsTk.getAttribute("map-reverse", "function"), ObservableExpression.class)
							.findMethod(TypeTokens.get().VOID, model, cv);
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
							.getAttribute(obsTk.getAttribute("map-reverse", "function"), ObservableExpression.class)
							.findMethod(currentType, model, cv);
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

					ObservableExpression enabledEx = reverseEl.getAttribute(obsTk.getAttribute("map-reverse", "enabled"),
						ObservableExpression.class);
					if (enabledEx != null) {
						enabled = enabledEx.<TransformationValues<?, ?>, Void, Void, String> findMethod(TypeTokens.get().STRING, model, cv)//
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
					ObservableExpression acceptEx = reverseEl.getAttribute(obsTk.getAttribute("map-reverse", "accept"),
						ObservableExpression.class);
					if (acceptEx != null) {
						MethodFinder<Object, TransformationValues<?, ?>, ?, String> acceptFinder = acceptEx
							.findMethod(TypeTokens.get().STRING, model, cv);
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
					ObservableExpression createEx = reverseEl.getAttribute(obsTk.getAttribute("map-reverse", "create"),
						ObservableExpression.class);
					if (createEx != null) {
						create = createEx.<Object, TransformationValues<?, ?>, Boolean, Object> findMethod(currentType, model, cv)//
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
					ObservableExpression addAcceptEx = reverseEl.getAttribute(obsTk.getAttribute("map-reverse", "add-accept"),
						ObservableExpression.class);
					if (addAcceptEx != null) {
						MethodFinder<Object, TransformationValues<?, ?>, ?, String> addAcceptFinder = addAcceptEx
							.findMethod(TypeTokens.get().STRING, model, cv);
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
		return new BetterTreeList<TypeToken<?>>(false).with(init);
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
