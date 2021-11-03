package org.observe.util;

import java.awt.Image;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import javax.swing.JFrame;

import org.observe.Observable;
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
import org.observe.expresso.DefaultExpressoParser;
import org.observe.expresso.ExpressoParser;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableExpression.MethodFinder;
import org.observe.util.ObservableModelSet.Builder;
import org.observe.util.ObservableModelSet.ExternalModelSet;
import org.observe.util.ObservableModelSet.ModelSetInstance;
import org.observe.util.ObservableModelSet.ValueContainer;
import org.observe.util.ObservableModelSet.ValueGetter;
import org.observe.util.swing.WindowPopulation;
import org.qommons.BiTuple;
import org.qommons.Identifiable;
import org.qommons.SubClassMap2;
import org.qommons.TimeUtils;
import org.qommons.TriFunction;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.config.QommonsConfig;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigElementDef;
import org.qommons.config.QonfigInterpreter;
import org.qommons.config.QonfigInterpreter.QonfigInterpretingSession;
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
		<T> Function<ExternalModelSet, T> parseModelValue(ObservableModelSet models, TypeToken<T> type, String text) throws ParseException;
	}

	public interface SimpleValueParser extends ValueParser {
		@Override
		default <T> Function<ExternalModelSet, T> parseModelValue(ObservableModelSet models, TypeToken<T> type, String text)
			throws ParseException {
			T value = (T) parseValue(type, text);
			if (value != null && !TypeTokens.get().isInstance(type, value))
				throw new IllegalStateException("Parser " + this + " parsed a value of type " + value.getClass() + " for type " + type);
			return extModel -> value;
		}

		Object parseValue(TypeToken<?> type, String text) throws ParseException;
	}

	public static SimpleValueParser simple(SimpleValueParser parser) {
		return parser;
	}

	private static final SubClassMap2<Object, ValueParser> DEFAULT_PARSERS;
	static {
		DEFAULT_PARSERS = new SubClassMap2<>(Object.class);
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
	private Map<String, Class<?>> theImports;

	public ObservableModelQonfigParser() {
		theParsers = new SubClassMap2<>(Object.class);
		theParsers.putAll(DEFAULT_PARSERS);
	}

	public ObservableModelQonfigParser withParser(Class<?> type, ValueParser parser) {
		theParsers.with(type, parser);
		return this;
	}

	public ObservableModelQonfigParser withImport(String alias, Class<?> importType) {
		if (theImports == null)
			theImports = new HashMap<>();
		theImports.put(alias, importType);
		return this;
	}

	public static <T> SettableValue<T> literal(T value, String text) {
		return SettableValue.asSettable(ObservableValue.of(value), __ -> "Literal value '" + text + "'");
	}

	public static class AppEnvironment {
		private final ObservableValue<String> theTitle;
		private final ObservableValue<Image> theIcon;

		public AppEnvironment(ObservableValue<String> title, ObservableValue<Image> icon) {
			theTitle = title;
			theIcon = icon;
		}

		public ObservableValue<String> getTitle() {
			return theTitle;
		}

		public ObservableValue<Image> getIcon() {
			return theIcon;
		}
	}

	@SuppressWarnings("rawtypes")
	public QonfigInterpreter.Builder configureInterpreter(QonfigInterpreter.Builder interpreter) {
		QonfigInterpreter.Builder observeInterpreter = interpreter.forToolkit(TOOLKIT.get());
		observeInterpreter.createWith("imports", ClassView.class, (el, session) -> {
			ClassView.Builder builder = ClassView.build();
			for (QonfigElement imp : el.getChildrenInRole("import")) {
				if (imp.getValueText().endsWith(".*"))
					builder.withWildcardImport(imp.getValueText().substring(0, imp.getValueText().length() - 2));
				else
					builder.withImport(imp.getValueText());
			}
			ClassView cv = builder.build();
			session.putGlobal("imports", cv);
			return cv;
		});
		observeInterpreter.createWith("models", ObservableModelSet.class, (el, session) -> {
			ObservableModelSet.Builder builder = ObservableModelSet.build();
			for (QonfigElement model : el.getChildrenInRole("model")) {
				ObservableModelSet.Builder subModel = builder.createSubModel(model.getAttributeText("name"));
				session.put("model", subModel);
				session.getInterpreter().interpret(model, ObservableModelSet.class);
			}
			return builder.build();
		}).createWith("abst-model", ObservableModelSet.class, (el, session) -> {
			ObservableModelSet.Builder model = (ObservableModelSet.Builder) session.get("model");
			QonfigElementDef modelType = TOOLKIT.get().getElement("abst-model");
			for (QonfigElement child : el.getChildren()) {
				if (modelType.isAssignableFrom(child.getType())) {
					ObservableModelSet.Builder subModel = model.createSubModel(child.getAttributeText("name"));
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
			public ObservableModelSet createValue(QonfigElement el, QonfigInterpretingSession session) throws ParseException {
				ObservableModelSet.Builder model = (ObservableModelSet.Builder) session.get("model");
				session.put("config-model", true);
				String configName = el.getAttributeText("config-name");
				Function<ModelSetInstance, SettableValue<BetterFile>> configDir;
				ObservableExpression configDirX = el.getAttribute("config-dir", ObservableExpression.class);
				if (configDirX != null)
					configDir = configDirX.evaluate(ModelTypes.Value.forType(BetterFile.class), model, (ClassView) session.get("imports"));
				else {
					configDir = msi -> {
						String prop = System.getProperty(configName + ".config");
						if (prop != null)
							return literal(BetterFile.at(new NativeFileSource(), prop), prop);
						else
							return literal(BetterFile.at(new NativeFileSource(), configName), "./" + configName);
					};
				}
				ObservableExpression oldConfigNamesX = el.getAttribute("old-config-names", ObservableExpression.class);
				Function<ModelSetInstance, ObservableCollection<String>> oldConfigNames;
				if (oldConfigNamesX != null)
					oldConfigNames = oldConfigNamesX.evaluate(ModelTypes.Collection.forType(String.class), model,
						(ClassView) session.get("cv"));
				else
					oldConfigNames = null;
				model.setModelConfiguration(msi -> {
					BetterFile configDirFile = configDir == null ? null : configDir.apply(msi).get();
					if (configDirFile == null) {
						String configProp = System.getProperty(configName + ".config");
						if (configProp != null)
							configDirFile = BetterFile.at(new NativeFileSource(), configProp);
						else
							configDirFile = BetterFile.at(new NativeFileSource(), configName);
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
						List<String> ocns = oldConfigNames.apply(msi);
						boolean found = false;
						for (String oldConfigName : ocns) {
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
						}, app, closingWithoutSave);
					} else {
						config.setName(configName);
						build2(config, configFile, backups, closingWithoutSave);
					}
					return config;
				});
				QonfigElementDef modelType = TOOLKIT.get().getElement("abst-model");
				for (QonfigElement child : el.getChildren()) {
					if (modelType.isAssignableFrom(child.getType())) {
						ObservableModelSet.Builder subModel = model.createSubModel(child.getAttributeText("name"));
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
			public Void createValue(QonfigElement element, QonfigInterpretingSession session) throws ParseException {
				ObservableModelSet.Builder model = (ObservableModelSet.Builder) session.get("model");
				ClassView cv=(ClassView) session.get("cv");
				String name = element.getAttributeText("name");
				TypeToken<?> type = parseType(element.getAttributeText("type"));
				String path;
				if (session.get("config-model") != null) {
					path = element.getAttributeText("config-path");
					if (path == null)
						path = name;
					Function<ModelSetInstance, ObservableConfigFormat<Object>> format = parseConfigFormat(
						element.getAttribute("format", ObservableExpression.class),
						element.getAttribute("default", ObservableExpression.class), model, cv, (TypeToken<Object>) type);
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
				String path)				throws ParseException;

			abstract <V> void buildConfig(ObservableModelSet.Builder model, ClassView cv, String name, TypeToken<V> type,
				QonfigElement element, String path, Function<ModelSetInstance, ObservableConfigFormat<V>> format) throws ParseException;
		}
		observeInterpreter.createWith("constant", Void.class, new TypedModelThing<SettableValue>() {
			@Override
			<V> void build(ObservableModelSet.Builder model, ClassView cv, String name, TypeToken<V> type, QonfigElement element, String path)
				throws ParseException {
				Function<ExternalModelSet, V> value = parseValue(model, type, element.getValue().toString());
				model.with(name, ModelTypes.Value.forType(type),
					(modelSet, extModels) -> SettableValue
					.asSettable(new ObservableValue.ConstantObservableValue<V>(type, value.apply(extModels)) {
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
				Function<ModelSetInstance, ObservableConfigFormat<V>> format) throws ParseException {
				build(model, cv, name, type, element, path);
			}
		}).createWith("value", Void.class, new TypedModelThing<SettableValue>() {
			@Override
			<V> void build(ObservableModelSet.Builder model, ClassView cv, String name, TypeToken<V> type, QonfigElement element, String path)
				throws ParseException {
				Function<ExternalModelSet, V> value;
				if (element.getValue() != null)
					value = parseValue(model, type, element.getValue().toString());
				else
					value = null;
				model.with(name, ModelTypes.Value.forType(type), (modelSet, extModels) -> {
					SettableValue.Builder<V> builder = SettableValue.build(type).safe(false);
					builder.withDescription(path);
					if (value != null)
						builder.withValue(value.apply(extModels));
					return builder.build();
				});
			}

			@Override
			<V> void buildConfig(Builder model, ClassView cv, String name, TypeToken<V> type, QonfigElement element, String path,
				Function<ModelSetInstance, ObservableConfigFormat<V>> format) throws ParseException {
				if (element.getValue() != null)
					System.err.println("WARNING: Config value " + model.pathTo(name) + " specifies a value, ignored");
				model.with(name, ModelTypes.Value.forType(type), (msi, extModels) -> {
					ObservableConfig config = (ObservableConfig) msi.getModelConfiguration();
					ObservableConfigValueBuilder<V> valueBuilder = config.asValue(type).at(path);
					if (format != null)
						valueBuilder.withFormat(format.apply(msi));
					return valueBuilder.buildValue(null);
				});
			}
		}).createWith("value-set", Void.class, new TypedModelThing<ObservableValueSet>() {
			@Override
			<V> void build(Builder model, ClassView cv, String name, TypeToken<V> type, QonfigElement element, String path)
				throws ParseException {
				throw new ParseException("value-sets can only be used under <config> elements", 0);
			}

			@Override
			<V> void buildConfig(Builder model, ClassView cv, String name, TypeToken<V> type, QonfigElement element, String path,
				Function<ModelSetInstance, ObservableConfigFormat<V>> format) throws ParseException {
				if (!element.getChildrenInRole("element").isEmpty())
					System.err.println("WARNING: Config value " + model.pathTo(name) + " specifies elements, ignored");
				model.<ObservableValueSet, ObservableValueSet<V>> with(name, ModelTypes.ValueSet.forType(type), (msi, extModels) -> {
					ObservableConfig config = (ObservableConfig) msi.getModelConfiguration();
					ObservableConfigValueBuilder<V> valueBuilder = config.asValue(type);
					if (format != null)
						valueBuilder.withFormat(format.apply(msi));
					return valueBuilder.buildEntitySet(null);
				});
			}
		});
		abstract class CollectionCreator<C extends ObservableCollection> extends TypedModelThing<C> {
			@Override
			<V> void build(ObservableModelSet.Builder model, ClassView cv, String name, TypeToken<V> type, QonfigElement element,
				String path) throws ParseException {
				List<Function<ExternalModelSet, V>> values = new ArrayList<>();
				for (QonfigElement el : element.getChildrenInRole("element"))
					values.add(parseValue(model, type, el.getValue().toString()));
				prepare(type, element, model, cv);
				add(model, cv, name, type, element, (modelSet, extModels) -> {
					C collection = (C) create(type, modelSet).safe(false).withDescription(path).build();
					for (int i = 0; i < values.size(); i++) {
						if (!collection.add(values.get(i).apply(extModels)))
							System.err.println("Warning: Value " + element.getChildrenInRole("element").get(i).getValue()
								+ " already added to " + element.getType().getName() + " " + element.getAttributeText("name"));
					}
					return collection;
				});
			}

			@Override
			<V> void buildConfig(Builder model, ClassView cv, String name, TypeToken<V> type, QonfigElement element, String path,
				Function<ModelSetInstance, ObservableConfigFormat<V>> format) throws ParseException {
				if (!element.getChildrenInRole("element").isEmpty())
					System.err.println("WARNING: Config value " + model.pathTo(name) + " specifies elements, ignored");
				prepare(type, element, model, cv);
				add(model, cv, name, type, element, (msi, extModels) -> {
					ObservableConfig config = (ObservableConfig) msi.getModelConfiguration();
					ObservableConfigValueBuilder<V> valueBuilder = config.asValue(type);
					if (format != null)
						valueBuilder.withFormat(format.apply(msi));
					ObservableCollection<V> collection = valueBuilder.buildCollection(null);
					return modify(collection, msi);
				});
			}

			protected <V> void prepare(TypeToken<V> type, QonfigElement element, ObservableModelSet models, ClassView cv)
				throws ParseException {
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
				throws ParseException {
				theComparator = parseComparator((TypeToken<Object>) type, element.getAttribute("sort-with", ObservableExpression.class));
			}

			@Override
			<V> ObservableCollectionBuilder<V, ?> create(TypeToken<V> type, ModelSetInstance models) {
				return ObservableCollection.build(type).safe(false)//
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
				throws ParseException {
				theComparator = parseComparator((TypeToken<Object>) type, element.getAttribute("sort-with", ObservableExpression.class));
			}

			@Override
			<V> ObservableCollectionBuilder<V, ?> create(TypeToken<V> type, ModelSetInstance models) {
				return ObservableCollection.build(type).safe(false)//
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
			public Void createValue(QonfigElement element, QonfigInterpretingSession session) throws ParseException {
				ObservableModelSet.Builder model = (ObservableModelSet.Builder) session.get("model");
				ClassView cv = (ClassView) session.get("cv");
				String name = element.getAttributeText("name");
				TypeToken<?> keyType = parseType(element.getAttributeText("key-type"));
				TypeToken<?> valueType = parseType(element.getAttributeText("type"));
				String path;
				if (session.get("config-model") != null) {
					path = element.getAttributeText("config-path");
					if (path == null)
						path = name;
					Function<ModelSetInstance, ObservableConfigFormat<Object>> keyFormat = parseConfigFormat(
						element.getAttribute("key-format", ObservableExpression.class),
						element.getAttribute("key-default", ObservableExpression.class), model, cv, (TypeToken<Object>) keyType);
					Function<ModelSetInstance, ObservableConfigFormat<Object>> valueFormat = parseConfigFormat(
						element.getAttribute("format", ObservableExpression.class),
						element.getAttribute("default", ObservableExpression.class), model, cv, (TypeToken<Object>) valueType);
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
				TypeToken<V> valueType, QonfigElement element, String path) throws ParseException;

			abstract <K, V> void buildConfig(Builder model, ClassView cv, String name, TypeToken<K> keyType, TypeToken<V> valueType,
				QonfigElement element, String path, Function<ModelSetInstance, ObservableConfigFormat<K>> keyFormat,
				Function<ModelSetInstance, ObservableConfigFormat<V>> valueFormat) throws ParseException;
		}
		abstract class MapCreator<M extends ObservableMap> extends BiTypedModelThing<M> {
			@Override
			<K, V> void build(ObservableModelSet.Builder model, ClassView cv, String name, TypeToken<K> keyType, TypeToken<V> valueType,
				QonfigElement element, String path) throws ParseException {
				List<BiTuple<Function<ExternalModelSet, K>, Function<ExternalModelSet, V>>> entries = new ArrayList<>();
				for (QonfigElement entry : element.getChildrenInRole("entry")) {
					Function<ExternalModelSet, K> key = parseValue(model, keyType, entry.getAttributeText("key"));
					Function<ExternalModelSet, V> v = parseValue(model, valueType, entry.getAttributeText("value"));
					entries.add(new BiTuple<>(key, v));
				}
				prepare(keyType, valueType, element, model, cv);
				add(model, cv, name, keyType, valueType, element, (modelSet, extModels) -> {
					M map = (M) create(keyType, valueType, modelSet).safe(false).withDescription(path).build();
					for (int i = 0; i < entries.size(); i++) {
						BiTuple<Function<ExternalModelSet, K>, Function<ExternalModelSet, V>> entry = entries.get(i);
						Object key = entry.getValue1().apply(extModels);
						Object v = entry.getValue2().apply(extModels);
						if (map.put(key, v) != null)
							System.err.println("Warning: Key " + key + " already used for " + element.getType().getName() + " "
								+ element.getAttributeText("name"));
					}
					return map;
				});
			}

			@Override
			<K, V> void buildConfig(Builder model, ClassView cv, String name, TypeToken<K> keyType, TypeToken<V> valueType,
				QonfigElement element, String path, Function<ModelSetInstance, ObservableConfigFormat<K>> keyFormat,
				Function<ModelSetInstance, ObservableConfigFormat<V>> valueFormat) throws ParseException {
				if (!element.getChildrenInRole("entry").isEmpty())
					System.err.println("WARNING: Config value " + model.pathTo(name) + " specifies entries, ignored");
				prepare(keyType, valueType, element, model, cv);
				add(model, cv, name, keyType, valueType, element, (msi, extModels) -> {
					ObservableConfig config = (ObservableConfig) msi.getModelConfiguration();
					ObservableConfigMapBuilder<K, V> valueBuilder = config.asValue(valueType).asMap(keyType);
					if (keyFormat != null)
						valueBuilder.withKeyFormat(keyFormat.apply(msi));
					if (valueFormat != null)
						valueBuilder.values().withFormat(valueFormat.apply(msi));
					ObservableMap<K, V> map = valueBuilder.buildMap(null);
					return modify(map, msi);
				});
			}

			protected <K, V> void prepare(TypeToken<K> keyType, TypeToken<V> valueType, QonfigElement element, ObservableModelSet models,
				ClassView cv) throws ParseException {
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
				ClassView cv) throws ParseException {
				theComparator = parseComparator((TypeToken<Object>) keyType, element.getAttribute("sort-with", ObservableExpression.class));
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
				QonfigElement element, String path) throws ParseException {
				List<BiTuple<Function<ExternalModelSet, K>, Function<ExternalModelSet, V>>> entries = new ArrayList<>();
				for (QonfigElement entry : element.getChildrenInRole("entry")) {
					Function<ExternalModelSet, K> key = parseValue(model, keyType, entry.getAttributeText("key"));
					Function<ExternalModelSet, V> v = parseValue(model, valueType, entry.getAttributeText("value"));
					entries.add(new BiTuple<>(key, v));
				}
				prepare(keyType, valueType, element, model, cv);
				add(model, cv, name, keyType, valueType, element, (modelSet, extModels) -> {
					M map = (M) create(keyType, valueType, modelSet).safe(false).withDescription(path).build(null);
					for (int i = 0; i < entries.size(); i++) {
						BiTuple<Function<ExternalModelSet, K>, Function<ExternalModelSet, V>> entry = entries.get(i);
						Object key = entry.getValue1().apply(extModels);
						Object v = entry.getValue2().apply(extModels);
						if (!map.add(key, v))
							System.err.println("Warning: Key " + key + " already used for " + element.getType().getName() + " "
								+ element.getAttributeText("name"));
					}
					return map;
				});
			}

			@Override
			<K, V> void buildConfig(Builder model, ClassView cv, String name, TypeToken<K> keyType, TypeToken<V> valueType,
				QonfigElement element, String path, Function<ModelSetInstance, ObservableConfigFormat<K>> keyFormat,
				Function<ModelSetInstance, ObservableConfigFormat<V>> valueFormat) throws ParseException {
				if (!element.getChildrenInRole("entry").isEmpty())
					System.err.println("WARNING: Config value " + model.pathTo(name) + " specifies entries, ignored");
				prepare(keyType, valueType, element, model, cv);
				add(model, cv, name, keyType, valueType, element, (msi, extModels) -> {
					ObservableConfig config = (ObservableConfig) msi.getModelConfiguration();
					ObservableConfigMapBuilder<K, V> valueBuilder = config.asValue(valueType).asMap(keyType);
					if (keyFormat != null)
						valueBuilder.withKeyFormat(keyFormat.apply(msi));
					if (valueFormat != null)
						valueBuilder.values().withFormat(valueFormat.apply(msi));
					ObservableMultiMap<K, V> map = valueBuilder.buildMultiMap(null);
					return modify(map, msi);
				});
			}

			protected <K, V> void prepare(TypeToken<K> keyType, TypeToken<V> valueType, QonfigElement element, ObservableModelSet models,
				ClassView cv) throws ParseException {
			}

			abstract <K, V> ObservableMultiMap.Builder<K, V> create(TypeToken<K> keyType, TypeToken<V> type, ModelSetInstance models);

			abstract <K, V> void add(ObservableModelSet.Builder model, ClassView cv, String name, TypeToken<K> keyType,
				TypeToken<V> valueType, QonfigElement element, ValueGetter<? extends M> map);

			protected abstract <K, V> M modify(ObservableMultiMap<K, V> map, ModelSetInstance msi);
		}
		observeInterpreter.createWith("multi-map", Void.class, new MultiMapCreator<ObservableMultiMap>() {
			@Override
			<K, V> ObservableMultiMap.Builder<K, V> create(TypeToken<K> keyType, TypeToken<V> type, ModelSetInstance models) {
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
				ClassView cv) throws ParseException {
				theComparator = parseComparator((TypeToken<Object>) keyType, element.getAttribute("sort-with", ObservableExpression.class));
			}

			@Override
			<K, V> ObservableSortedMultiMap.Builder<K, V> create(TypeToken<K> keyType, TypeToken<V> type, ModelSetInstance models) {
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
			public Void createValue(QonfigElement element, QonfigInterpretingSession session) throws ParseException {
				ObservableModelSet.Builder model = (ObservableModelSet.Builder) session.get("model");
				String name = element.getAttributeText("name");
				String typeS = element.getAttributeText("type");
				TypeToken<?> type = parseType(typeS);
				String path;
				if (model.getPath() == null)
					path = name;
				else
					path = model.getPath() + "." + name;
				expect(model, name, type, new ValueGetter<T>() {
					@Override
					public T get(ModelSetInstance modelSet, ExternalModelSet extModels) {
						try {
							return getValue(extModels, path, type);
						} catch (IllegalArgumentException e) {
							throw new IllegalArgumentException(
								"External model " + model.getPath() + " does not match expected: " + e.getMessage(), e);
						}
					}
				});
				return null;
			}

			protected abstract <V> void expect(ObservableModelSet.Builder model, String name, TypeToken<V> type,
				ValueGetter<? extends T> valueGetter);

			protected abstract T getValue(ExternalModelSet extModels, String name, TypeToken<?> type);
		}
		observeInterpreter.createWith("ext-event", Void.class, new ExtModelValidator<Observable>() {
			@Override
			protected <V> void expect(Builder model, String name, TypeToken<V> type, ValueGetter<? extends Observable> valueGetter) {
				model.with(name, ModelTypes.Event.forType(type), (ValueGetter<Observable<V>>) valueGetter);
			}

			@Override
			protected Observable getValue(ExternalModelSet extModels, String name, TypeToken<?> type) {
				return extModels.get(name, ModelTypes.Event.forType(type));
			}
		});
		observeInterpreter.createWith("ext-value", Void.class, new ExtModelValidator<SettableValue>() {
			@Override
			protected <V> void expect(Builder model, String name, TypeToken<V> type, ValueGetter<? extends SettableValue> valueGetter) {
				model.with(name, ModelTypes.Value.forType(type), (ValueGetter<SettableValue<V>>) valueGetter);
			}

			@Override
			protected SettableValue getValue(ExternalModelSet extModels, String name, TypeToken<?> type) {
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
			protected ObservableCollection getValue(ExternalModelSet extModels, String name, TypeToken<?> type) {
				return extModels.get(name, ModelTypes.Collection.forType(type));
			}
		});
		observeInterpreter.createWith("ext-set", Void.class, new ExtModelValidator<ObservableSet>() {
			@Override
			protected <V> void expect(Builder model, String name, TypeToken<V> type, ValueGetter<? extends ObservableSet> valueGetter) {
				model.with(name, ModelTypes.Set.forType(type), (ValueGetter<ObservableSet<V>>) valueGetter);
			}

			@Override
			protected ObservableSet getValue(ExternalModelSet extModels, String name, TypeToken<?> type) {
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
			protected ObservableSortedCollection getValue(ExternalModelSet extModels, String name, TypeToken<?> type) {
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
			protected ObservableSortedSet getValue(ExternalModelSet extModels, String name, TypeToken<?> type) {
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
			protected ObservableValueSet getValue(ExternalModelSet extModels, String name, TypeToken<?> type) {
				return extModels.get(name, ModelTypes.ValueSet.forType(type));
			}
		});
		abstract class ExtBiTypedModelValidator<T> implements QonfigInterpreter.QonfigValueCreator<Void> {
			@Override
			public Void createValue(QonfigElement element, QonfigInterpretingSession session) throws ParseException {
				ObservableModelSet.Builder model = (ObservableModelSet.Builder) session.get("model");
				String name = element.getAttributeText("name");
				String keyTypeS = element.getAttributeText("key-type");
				String valueTypeS = element.getAttributeText("type");
				TypeToken<?> keyType = parseType(keyTypeS);
				TypeToken<?> valueType = parseType(valueTypeS);
				String path;
				if (model.getPath() == null)
					path = name;
				else
					path = model.getPath() + "." + name;
				expect(model, name, keyType, valueType, new ValueGetter<T>() {
					@Override
					public T get(ModelSetInstance modelSet, ExternalModelSet extModels) {
						return getValue(extModels, path, keyType, valueType);
					}
				});
				return null;
			}

			protected abstract <K, V> void expect(ObservableModelSet.Builder model, String name, TypeToken<K> keyType,
				TypeToken<V> valueType, ValueGetter<? extends T> valueGetter);

			protected abstract T getValue(ExternalModelSet extModels, String name, TypeToken<?> keyType, TypeToken<?> valueType);
		}
		observeInterpreter.createWith("ext-map", Void.class, new ExtBiTypedModelValidator<ObservableMap>() {
			@Override
			protected <K, V> void expect(Builder model, String name, TypeToken<K> keyType, TypeToken<V> valueType,
				ValueGetter<? extends ObservableMap> valueGetter) {
				model.with(name, ModelTypes.Map.forType(keyType, valueType), (ValueGetter<ObservableMap<K, V>>) valueGetter);
			}

			@Override
			protected ObservableMap getValue(ExternalModelSet extModels, String name, TypeToken<?> keyType, TypeToken<?> valueType) {
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
			protected ObservableSortedMap getValue(ExternalModelSet extModels, String name, TypeToken<?> keyType, TypeToken<?> valueType) {
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
			protected ObservableMultiMap getValue(ExternalModelSet extModels, String name, TypeToken<?> keyType, TypeToken<?> valueType) {
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
				TypeToken<?> valueType) {
				return extModels.get(name, ModelTypes.SortedMultiMap.forType(keyType, valueType));
			}
		});
		observeInterpreter.createWith("transform", Void.class, new QonfigInterpreter.QonfigValueCreator<Void>() {
			@Override
			public Void createValue(QonfigElement element, QonfigInterpretingSession session) throws ParseException {
				ObservableModelSet.Builder model = (ObservableModelSet.Builder) session.get("model");
				ClassView cv = (ClassView) session.get("imports");
				String name = element.getAttributeText("name");
				String sourceName = element.getAttributeText("source");
				ValueContainer<?, ?> source = model.get(sourceName, true);
				ModelType<?> type = source.getType().getModelType();
				if (type == ModelTypes.Event)
					transformEvent((ValueContainer<Object, Observable<Object>>) source, element, model, cv, name);
				else if (type == ModelTypes.Value)
					transformValue((ValueContainer<Object, SettableValue<Object>>) source, element, model, cv, name);
				else if (type == ModelTypes.Collection || type == ModelTypes.SortedCollection || type == ModelTypes.Set
					|| type == ModelTypes.SortedSet)
					transformCollection((ValueContainer<Object, ObservableCollection<Object>>) source, element, model, cv, name);
				else
					throw new IllegalArgumentException("Transformation unsupported for source of type " + source.getType().getModelType());
				return null;
			}
		});
		observeInterpreter.createWith("file-source", Void.class, new QonfigInterpreter.QonfigValueCreator<Void>() {
			@Override
			public Void createValue(QonfigElement element, QonfigInterpretingSession session) throws ParseException {
				ObservableModelSet.Builder model = (ObservableModelSet.Builder) session.get("model");
				String name = element.getAttributeText("name");
				ValueGetter<SettableValue<FileDataSource>> source;
				switch (element.getAttributeText("file-source-type")) {
				case "native":
					source = (modelSet, extModels) -> literal(new NativeFileSource(), "native-file-source");
					break;
				case "sftp":
					throw new UnsupportedOperationException("Not yet implemented");
				default:
					throw new IllegalArgumentException("Unrecognized file-source-type: " + element.getAttributeText("file-source-type"));
				}
				if (!element.getChildrenByRole().get("archive").isEmpty()) {
					Set<String> archiveMethodStrs = new HashSet<>();
					List<ArchiveEnabledFileSource.FileArchival> archiveMethods = new ArrayList<>(5);
					for (QonfigElement archive : element.getChildrenByRole().get("archive")) {
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
					if (element.getAttribute("max-zip-depth") == null)
						maxZipDepth = null;
					else {
						int zipDepth;
						try {
							zipDepth = Integer.parseInt(element.getAttributeText("max-zip-depth"));
						} catch (NumberFormatException e) {
							zipDepth = -1;
						}
						if (zipDepth >= 0) {
							int finalZD = zipDepth;
							maxZipDepth = models -> literal(finalZD, element.getAttributeText("max-archive-depth"));
						} else {
							maxZipDepth = model.get(element.getAttributeText("max-zip-depth"), ModelTypes.Value.forType(Integer.class));
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
		return interpreter;
	}

	private static void restoreBackup(boolean fromError, ObservableConfig config, FileBackups backups, Runnable onBackup,
		Runnable onNoBackup, AppEnvironment app, boolean[] closingWithoutSave) {
		BetterSortedSet<Instant> backupTimes = backups == null ? null : backups.getBackups();
		if (backupTimes == null || backupTimes.isEmpty()) {
			if (onNoBackup != null)
				onNoBackup.run();
			return;
		}
		SettableValue<Instant> selectedBackup = SettableValue.build(Instant.class).safe(false).build();
		Format<Instant> PAST_DATE_FORMAT = SpinnerFormat.flexDate(Instant::now, "EEE MMM dd, yyyy",
			opts -> opts.withMaxResolution(TimeUtils.DateElementType.Second).withEvaluationType(TimeUtils.RelativeTimeEvaluation.Past));
		JFrame[] frame = new JFrame[1];
		boolean[] backedUp = new boolean[1];
		frame[0] = WindowPopulation.populateWindow(null, null, false, false)//
			.withTitle((app == null || app.getTitle().get() == null) ? "Backup" : app.getTitle().get() + " Backup")//
			.withIcon(app == null ? ObservableValue.of(Image.class, null) : app.getIcon())//
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

	private static <T> Function<ModelSetInstance, ObservableConfigFormat<T>> parseConfigFormat(ObservableExpression formatX,
		ObservableExpression valueX, ObservableModelSet model, ClassView cv, TypeToken<T> type) throws ParseException {
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
					Function<ModelSetInstance, SettableValue<T>> initValue;
					if (valueX != null)
						initValue = valueX.evaluate(ModelTypes.Value.forType(type), model, cv);
					else
						initValue = null;
					return msi -> ObservableConfigFormat.ofQommonFormat((Format<T>) formatVC.apply(msi).get(), //
						initValue == null ? null : () -> initValue.apply(msi).get());
				}
			} else {
				System.err.println(formatX + ": Unrecognized format type: " + formatVC.getType());
				return null;
			}
		} else
			return null;
	}

	private TypeToken<?> parseType(String text) throws ParseException {
		if (theImports != null) {
			Class<?> imported = theImports.get(text);
			if (imported != null)
				return TypeTokens.get().of(imported);
		}
		try {
			return TypeTokens.get().parseType(text);
		} catch (ParseException e) {
			try {
				return TypeTokens.get().parseType("java.lang." + text);
			} catch (ParseException e2) {
				throw e;
			}
		}
	}

	private <T> Function<ExternalModelSet, T> parseValue(ObservableModelSet models, TypeToken<T> type, String text) throws ParseException {
		ValueParser parser = theParsers.get(TypeTokens.get().wrap(TypeTokens.getRawType(type)), false);
		if (parser == null)
			throw new ParseException("No parser configured for type " + type, 0);
		return parser.parseModelValue(models, type, text);
	}

	private void transformEvent(ValueContainer<Object, Observable<Object>> source, QonfigElement transform,
		ObservableModelSet.Builder model, ClassView cv, String name) {
		// skip
		// noInit
		// take
		// takeUntil
		for (QonfigElement op : transform.getChildrenInRole("op")) {
			switch (op.getType().getName()) {
			case "no-init":
				model.with(name, ModelTypes.Event.forType((TypeToken<Object>) source.getType().getType(0)),
					(modelSet, extModels) -> source.get(modelSet).noInit());
				break;
			case "skip":
				int times = Integer.parseInt(op.getAttributeText("times"));
				model.with(name, ModelTypes.Event.forType((TypeToken<Object>) source.getType().getType(0)),
					(modelSet, extModels) -> source.get(modelSet).skip(times));
				break;
			case "take":
				times = Integer.parseInt(op.getAttributeText("times"));
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
		ObservableModelSet.Builder model, ClassView cv, String name) throws ParseException {
		ValueTransform transformFn = (v, models) -> v;
		TypeToken<Object> currentType = (TypeToken<Object>) source.getType().getType(0);
		for (QonfigElement op : transform.getChildrenInRole("op")) {
			ValueTransform prevTransformFn = transformFn;
			switch (op.getType().getName()) {
			case "take-until":
				throw new UnsupportedOperationException("Not yet implemented");// TODO
			case "map-to":
				ParsedTransformation ptx = transformation(currentType, op, model, cv);
				if (ptx.isReversible())
					transformFn = (v, models) -> prevTransformFn.transform(v, models).transformReversible(ptx.getTargetType(),
						tx -> (Transformation.ReversibleTransformation<Object, Object>) ptx.transform(tx, models));
					else
						transformFn = (v, models) -> {
							ObservableValue<Object> value = prevTransformFn.transform(v, models).transform(ptx.getTargetType(),
								tx -> ptx.transform(tx, models));
							return SettableValue.asSettable(value, __ -> "No reverse configured for " + transform.toString());
						};
						currentType = ptx.getTargetType();
						break;
			case "refresh":
				throw new UnsupportedOperationException("Not yet implemented");// TODO
			case "unmodifiable":
				boolean allowUpdates = (Boolean) op.getAttribute("allow-updates");
				if (!allowUpdates)
					transformFn = (v, models) -> prevTransformFn.transform(v, models)
					.disableWith(ObservableValue.of(StdMsg.UNSUPPORTED_OPERATION));
					else {
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
				throw new UnsupportedOperationException("Not yet implemented");// TODO
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
		ObservableModelSet.Builder model, ClassView cv, String name) throws ParseException {
		CollectionTransform transformFn = (src, models) -> src;
		TypeToken<Object> currentType = (TypeToken<Object>) source.getType().getType(0);
		for (QonfigElement op : transform.getChildrenInRole("op")) {
			CollectionTransform prevTransform = transformFn;
			switch (op.getType().getName()) {
			case "map-to":
				ParsedTransformation ptx = transformation(currentType, op, model, cv);
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
				throw new UnsupportedOperationException("Not yet implemented");// TODO
			case "refresh-each":
				throw new UnsupportedOperationException("Not yet implemented");// TODO
			case "distinct":
				boolean useFirst = (Boolean) op.getAttribute("use-first");
				boolean preserveSourceOrder = (Boolean) op.getAttribute("preserve-source-order");
				if (op.getAttribute("sort-with") != null) {
					if (preserveSourceOrder)
						System.err.println("WARNING: preserve-source-order cannot be used with sorted collections,"
							+ " as order is determined by the values themselves");
					Function<ModelSetInstance, Comparator<Object>> comparator = parseComparator(currentType,
						op.getAttribute("sort-with", ObservableExpression.class));
					transformFn = (src, models) -> prevTransform.transform(src, models).distinctSorted(comparator.apply(models), useFirst);
				} else
					transformFn = (src, models) -> prevTransform.transform(src, models)
					.distinct(uo -> uo.useFirst(useFirst).preserveSourceOrder(preserveSourceOrder));
					break;
			case "sort":
				Function<ModelSetInstance, Comparator<Object>> comparator = parseComparator(currentType,
					op.getAttribute("sort-with", ObservableExpression.class));
				transformFn = (src, models) -> prevTransform.transform(src, models).sorted(comparator.apply(models));
				break;
			case "with-equivalence":
				throw new UnsupportedOperationException("Not yet implemented");// TODO
			case "unmodifiable":
				boolean allowUpdates = (Boolean) op.getAttribute("allow-updates");
				transformFn = (src, models) -> prevTransform.transform(src, models).unmodifiable(allowUpdates);
				break;
			case "filter-mod":
				throw new UnsupportedOperationException("Not yet implemented");// TODO
			case "map-equivalent":
				throw new UnsupportedOperationException("Not yet implemented");// TODO
			case "flatten":
				throw new UnsupportedOperationException("Not yet implemented");// TODO
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
		Boolean active = transform.getAttribute("active") == null ? null : (Boolean) transform.getAttribute("active");
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

	private ParsedTransformation transformation(TypeToken<Object> currentType, QonfigElement op, ObservableModelSet model, ClassView cv)
		throws ParseException {
		List<ValueContainer<?, ? extends SettableValue<?>>> combined = new ArrayList<>(op.getChildrenInRole("combined-value").size());
		for (QonfigElement combine : op.getChildrenInRole("combined-value")) {
			String name = combine.getValue().toString();
			combined.add(model.get(name, ModelTypes.Value));
		}
		TypeToken<?>[] argTypes = new TypeToken[combined.size()];
		for (int i = 0; i < argTypes.length; i++)
			argTypes[i] = combined.get(i).getType().getType(0);
		MethodFinder<Object, TransformationValues<?, ?>, ?, Object> map;
		map = op.getAttribute("function", ObservableExpression.class).findMethod(TypeTokens.get().OBJECT);
		map.withOption(argList(currentType).with(argTypes), (src, tv, __, args, models) -> {
			args[0] = src;
			for (int i = 0; i < combined.size(); i++)
				args[i + 1] = tv.get(combined.get(i).get(models));
		});
		Function<ModelSetInstance, BiFunction<Object, TransformationValues<?, ?>, Object>> mapFn = map.find2();
		TypeToken<Object> targetType = (TypeToken<Object>) map.getResultType();
		QonfigElement reverseEl = op.getChildrenInRole("reverse").peekFirst();
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
					if (reverseEl.getAttribute("type", QonfigElementDef.class).getName().equals("replace-source")) {
						modifying = false;
						stateful = (Boolean) reverseEl.getAttribute("stateful");
						inexact = (Boolean) reverseEl.getAttribute("inexact");
					} else if (reverseEl.getAttribute("type", QonfigElementDef.class).getName().equals("modify-source")) {
						modifying = true;
						stateful = true;
						inexact = false;
					} else
						throw new IllegalStateException(
							"Unrecognized reverse type: " + reverseEl.getAttribute("type", QonfigElementDef.class).getName());
					TypeToken<TransformationValues<Object, Object>> tvType = TypeTokens.get()
						.keyFor(Transformation.TransformationValues.class)
						.parameterized(TypeTokens.get().getExtendsWildcard(currentType), TypeTokens.get().getExtendsWildcard(targetType));
					if (modifying) {
						reverseFn = null;
						MethodFinder<Object, TransformationValues<?, ?>, ?, Void> reverse = reverseEl
							.getAttribute("function", ObservableExpression.class).findMethod(TypeTokens.get().VOID);
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
							.getAttribute("function", ObservableExpression.class).findMethod(currentType);
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

					if (reverseEl.getAttribute("enabled") != null) {
						enabled = reverseEl.getAttribute("enabled", ObservableExpression.class)
							.<TransformationValues<?, ?>, Void, Void, String> findMethod(TypeTokens.get().STRING)//
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
					if (reverseEl.getAttribute("accept") != null) {
						MethodFinder<Object, TransformationValues<?, ?>, ?, String> acceptFinder = reverseEl
							.getAttribute("accept", ObservableExpression.class).findMethod(TypeTokens.get().STRING);
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
					if (reverseEl.getAttribute("create") != null) {
						create = reverseEl.getAttribute("create", ObservableExpression.class)
							.<Object, TransformationValues<?, ?>, Boolean, Object> findMethod(currentType)//
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
					if (reverseEl.getAttribute("add-accept") != null) {
						MethodFinder<Object, TransformationValues<?, ?>, ?, String> addAcceptFinder = reverseEl
							.getAttribute("add-accept", ObservableExpression.class).findMethod(TypeTokens.get().STRING);
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
			public Transformation<Object, Object> transform(TransformationPrecursor<Object, Object, ?> precursor, ModelSetInstance modelSet) {
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

	private <T> Function<ModelSetInstance, Comparator<T>> parseComparator(TypeToken<T> type, ObservableExpression expression)
		throws ParseException {
		TypeToken<? super T> superType = TypeTokens.get().getSuperWildcard(type);
		Function<ModelSetInstance, BiFunction<T, T, Integer>> compareFn = expression.<T, T, Void, Integer> findMethod(TypeTokens.get().INT)//
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
