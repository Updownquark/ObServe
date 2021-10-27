package org.observe.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

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
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollectionBuilder;
import org.observe.collect.ObservableSet;
import org.observe.collect.ObservableSortedCollection;
import org.observe.collect.ObservableSortedSet;
import org.observe.util.ObservableModelSet.Builder;
import org.observe.util.ObservableModelSet.ExternalModelSet;
import org.observe.util.ObservableModelSet.ModelSetInstance;
import org.observe.util.ObservableModelSet.ValueContainer;
import org.observe.util.ObservableModelSet.ValueGetter;
import org.qommons.BiTuple;
import org.qommons.Identifiable;
import org.qommons.SubClassMap2;
import org.qommons.TimeUtils;
import org.qommons.TriConsumer;
import org.qommons.TriFunction;
import org.qommons.collect.BetterList;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigElementDef;
import org.qommons.config.QonfigInterpreter;
import org.qommons.config.QonfigInterpreter.QonfigInterpretingSession;
import org.qommons.config.QonfigToolkitAccess;
import org.qommons.io.ArchiveEnabledFileSource;
import org.qommons.io.BetterFile.FileDataSource;
import org.qommons.io.Format;
import org.qommons.io.NativeFileSource;
import org.qommons.tree.BetterTreeList;

import com.google.common.reflect.TypeToken;

public class ObservableModelQonfigParser {
	public static final QonfigToolkitAccess TOOLKIT = new QonfigToolkitAccess(ObservableModelQonfigParser.class, "observe-models.qtd");

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

	public void configureInterpreter(QonfigInterpreter.Builder interpreter) {
		interpreter = interpreter.forToolkit(TOOLKIT.get());
		interpreter.createWith("imports", Void.class, (el, session) -> {
			for (QonfigElement imp : el.getChildrenInRole("import"))
				session.getInterpreter().interpret(imp, Void.class);
			return null;
		}).createWith("import", Void.class, (el, session) -> {
			String typeName = el.getAttributeText("type");
			Class<?> type;
			try {
				type = Thread.currentThread().getContextClassLoader().loadClass(typeName);
			} catch (ClassNotFoundException e) {
				throw new IllegalArgumentException("Unrecognized type: " + typeName, e);
			}
			theImports.put(el.getAttributeText("alias"), type);
			return null;
		});
		interpreter.createWith("models", ObservableModelSet.class, (el, session) -> {
			ObservableModelSet.Builder builder = ObservableModelSet.build();
			for (QonfigElement model : el.getChildrenInRole("model")) {
				ObservableModelSet.Builder subModel = builder.createSubModel(model.getAttributeText("name"));
				session.put("model", subModel);
				session.getInterpreter().interpret(model, ObservableModelSet.class);
			}
			return builder.build();
		}).createWith("abst-model", ObservableModelSet.class, (el, session) -> {
			for (QonfigElement extModel : el.getChildrenInRole("value"))
				session.getInterpreter().interpret(extModel, Void.class);
			return (ObservableModelSet) session.get("model");
		}).extend("abst-model", "ext-model", ObservableModelSet.class, ObservableModelSet.class, (s, el, session) -> {
			return s;
		}).extend("abst-model", "model", ObservableModelSet.class, ObservableModelSet.class, (s, el, session) -> {
			return s;
		});
		abstract class TypedModelThing<T> implements QonfigInterpreter.QonfigValueCreator<Void> {
			@Override
			public Void createValue(QonfigElement element, QonfigInterpretingSession session) throws ParseException {
				ObservableModelSet.Builder model = (ObservableModelSet.Builder) session.get("model");
				String name = element.getAttributeText("name");
				TypeToken<?> type = parseType(element.getAttributeText("type"));
				String path = model.getPath();
				if (path == null)
					path = name;
				else
					path = path + "." + name;
				build(model, name, type, element, name);
				return null;
			}

			abstract <V> void build(ObservableModelSet.Builder model, String name, TypeToken<V> type, QonfigElement element, String path)
				throws ParseException;
		}
		interpreter.createWith("constant", Void.class, new TypedModelThing<SettableValue>() {
			@Override
			<V> void build(ObservableModelSet.Builder model, String name, TypeToken<V> type, QonfigElement element, String path)
				throws ParseException {
				Function<ExternalModelSet, V> value = parseValue(model, type, element.getValue().toString());
				model.withValue(name, type,
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
		}).createWith("value", Void.class, new TypedModelThing<SettableValue>() {
			@Override
			<V> void build(ObservableModelSet.Builder model, String name, TypeToken<V> type, QonfigElement element, String path)
				throws ParseException {
				Function<ExternalModelSet, V> value;
				if (element.getValue() != null)
					value = parseValue(model, type, element.getValue().toString());
				else
					value = null;
				model.withValue(name, type, (modelSet, extModels) -> {
					SettableValue.Builder<V> builder = SettableValue.build(type).safe(false);
					builder.withDescription(path);
					if (value != null)
						builder.withValue(value.apply(extModels));
					return builder.build();
				});
			}
		});
		abstract class CollectionCreator<C extends ObservableCollection> extends TypedModelThing<C> {
			@Override
			<V> void build(ObservableModelSet.Builder model, String name, TypeToken<V> type, QonfigElement element, String path)
				throws ParseException {
				List<Function<ExternalModelSet, V>> values = new ArrayList<>();
				for (QonfigElement el : element.getChildrenInRole("element"))
					values.add(parseValue(model, type, el.getValue().toString()));
				prepare(type, element);
				add(model, name, type, element, (modelSet, extModels) -> {
					C collection = (C) create(type, modelSet).safe(false).withDescription(path).build();
					for (int i = 0; i < values.size(); i++) {
						if (!collection.add(values.get(i).apply(extModels)))
							System.err.println("Warning: Value " + element.getChildrenInRole("element").get(i).getValue()
								+ " already added to " + element.getType().getName() + " " + element.getAttributeText("name"));
					}
					return collection;
				});
			}

			protected <V> void prepare(TypeToken<V> type, QonfigElement element) {
			}

			abstract <V> ObservableCollectionBuilder<V, ?> create(TypeToken<V> type, ModelSetInstance models);

			abstract <V> void add(ObservableModelSet.Builder model, String name, TypeToken<V> type, QonfigElement element,
				ValueGetter<? extends C> collection);
		}
		interpreter.createWith("list", Void.class, new CollectionCreator<ObservableCollection>() {
			@Override
			<V> ObservableCollectionBuilder<V, ?> create(TypeToken<V> type, ModelSetInstance models) {
				return ObservableCollection.build(type);
			}

			@Override
			<V> void add(Builder model, String name, TypeToken<V> type, QonfigElement element,
				ValueGetter<? extends ObservableCollection> collection) {
				model.withCollection(name, type, (ValueGetter<ObservableCollection<V>>) collection);
			}
		}).createWith("set", Void.class, new CollectionCreator<ObservableSet>() {
			@Override
			<V> ObservableCollectionBuilder<V, ?> create(TypeToken<V> type, ModelSetInstance models) {
				return ObservableCollection.build(type).distinct();
			}

			@Override
			<V> void add(Builder model, String name, TypeToken<V> type, QonfigElement element,
				ValueGetter<? extends ObservableSet> collection) {
				model.withSet(name, type, (ValueGetter<ObservableSet<V>>) collection);
			}
		}).createWith("sorted-set", Void.class, new CollectionCreator<ObservableSortedSet>() {
			private Function<ModelSetInstance, Comparator<Object>> theComparator;

			@Override
			protected <V> void prepare(TypeToken<V> type, QonfigElement element) {
				theComparator = parseComparator((TypeToken<Object>) type, element.getAttribute("sort-with"));
			}

			@Override
			<V> ObservableCollectionBuilder<V, ?> create(TypeToken<V> type, ModelSetInstance models) {
				return ObservableCollection.build(type).safe(false)//
					.distinctSorted(theComparator.apply(models));
			}

			@Override
			<V> void add(Builder model, String name, TypeToken<V> type, QonfigElement element,
				ValueGetter<? extends ObservableSortedSet> collection) {
				model.withSortedSet(name, type, (ValueGetter<ObservableSortedSet<V>>) collection);
			}
		}).createWith("sorted-list", Void.class, new CollectionCreator<ObservableSortedCollection>() {
			private Function<ModelSetInstance, Comparator<Object>> theComparator;

			@Override
			protected <V> void prepare(TypeToken<V> type, QonfigElement element) {
				theComparator = parseComparator((TypeToken<Object>) type, element.getAttribute("sort-with"));
			}

			@Override
			<V> ObservableCollectionBuilder<V, ?> create(TypeToken<V> type, ModelSetInstance models) {
				return ObservableCollection.build(type).safe(false)//
					.sortBy(theComparator.apply(models));
			}

			@Override
			<V> void add(Builder model, String name, TypeToken<V> type, QonfigElement element,
				ValueGetter<? extends ObservableSortedCollection> collection) {
				model.withSortedCollection(name, type, (ValueGetter<ObservableSortedCollection<V>>) collection);
			}
		});
		abstract class BiTypedModelThing<T> implements QonfigInterpreter.QonfigValueCreator<Void> {
			@Override
			public Void createValue(QonfigElement element, QonfigInterpretingSession session) throws ParseException {
				ObservableModelSet.Builder model = (ObservableModelSet.Builder) session.get("model");
				String name = element.getAttributeText("name");
				TypeToken<?> keyType = parseType(element.getAttributeText("key-type"));
				TypeToken<?> valueType = parseType(element.getAttributeText("type"));
				String path = model.getPath();
				if (path == null)
					path = name;
				else
					path = path + "." + name;
				build(model, name, keyType, valueType, element, path);
				return null;
			}

			abstract <K, V> void build(ObservableModelSet.Builder model, String name, TypeToken<K> keyType, TypeToken<V> valueType,
				QonfigElement element, String path) throws ParseException;
		}
		abstract class MapCreator<M extends ObservableMap> extends BiTypedModelThing<M> {
			@Override
			<K, V> void build(ObservableModelSet.Builder model, String name, TypeToken<K> keyType, TypeToken<V> valueType,
				QonfigElement element, String path) throws ParseException {
				List<BiTuple<Function<ExternalModelSet, K>, Function<ExternalModelSet, V>>> entries = new ArrayList<>();
				for (QonfigElement entry : element.getChildrenInRole("entry")) {
					Function<ExternalModelSet, K> key = parseValue(model, keyType, entry.getAttributeText("key"));
					Function<ExternalModelSet, V> v = parseValue(model, valueType, entry.getAttributeText("value"));
					entries.add(new BiTuple<>(key, v));
				}
				prepare(keyType, valueType, element);
				add(model, name, keyType, valueType, element, (modelSet, extModels) -> {
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

			protected <K, V> void prepare(TypeToken<K> keyType, TypeToken<V> valueType, QonfigElement element) {
			}

			abstract <K, V> ObservableMap.Builder<K, V, ?> create(TypeToken<K> keyType, TypeToken<V> valueType, ModelSetInstance models);

			abstract <K, V> void add(ObservableModelSet.Builder model, String name, TypeToken<K> keyType, TypeToken<V> valueType,
				QonfigElement element, ValueGetter<? extends M> map);
		}
		interpreter.createWith("map", Void.class, new MapCreator<ObservableMap>() {
			@Override
			<K, V> ObservableMap.Builder<K, V, ?> create(TypeToken<K> keyType, TypeToken<V> type, ModelSetInstance models) {
				return ObservableMap.build(keyType, type);
			}

			@Override
			<K, V> void add(ObservableModelSet.Builder model, String name, TypeToken<K> keyType, TypeToken<V> valueType,
				QonfigElement element, ValueGetter<? extends ObservableMap> map) {
				model.withMap(name, keyType, valueType, (ValueGetter<ObservableMap<K, V>>) map);
			}
		});
		interpreter.createWith("sorted-map", Void.class, new MapCreator<ObservableSortedMap>() {
			private Function<ModelSetInstance, Comparator<Object>> theComparator;

			@Override
			protected <K, V> void prepare(TypeToken<K> keyType, TypeToken<V> valueType, QonfigElement element) {
				theComparator = parseComparator((TypeToken<Object>) keyType, element.getAttribute("sort-with"));
			}

			@Override
			<K, V> ObservableSortedMap.Builder<K, V, ?> create(TypeToken<K> keyType, TypeToken<V> type, ModelSetInstance models) {
				return ObservableSortedMap.build(keyType, type, //
					theComparator.apply(models));
			}

			@Override
			<K, V> void add(ObservableModelSet.Builder model, String name, TypeToken<K> keyType, TypeToken<V> valueType,
				QonfigElement element, ValueGetter<? extends ObservableSortedMap> map) {
				model.withSortedMap(name, keyType, valueType, (ValueGetter<ObservableSortedMap<K, V>>) map);
			}
		});
		abstract class MultiMapCreator<M extends ObservableMultiMap> extends BiTypedModelThing<M> {
			@Override
			<K, V> void build(ObservableModelSet.Builder model, String name, TypeToken<K> keyType, TypeToken<V> valueType,
				QonfigElement element, String path) throws ParseException {
				List<BiTuple<Function<ExternalModelSet, K>, Function<ExternalModelSet, V>>> entries = new ArrayList<>();
				for (QonfigElement entry : element.getChildrenInRole("entry")) {
					Function<ExternalModelSet, K> key = parseValue(model, keyType, entry.getAttributeText("key"));
					Function<ExternalModelSet, V> v = parseValue(model, valueType, entry.getAttributeText("value"));
					entries.add(new BiTuple<>(key, v));
				}
				prepare(keyType, valueType, element);
				add(model, name, keyType, valueType, element, (modelSet, extModels) -> {
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

			protected <K, V> void prepare(TypeToken<K> keyType, TypeToken<V> valueType, QonfigElement element) {
			}

			abstract <K, V> ObservableMultiMap.Builder<K, V> create(TypeToken<K> keyType, TypeToken<V> type, ModelSetInstance models);

			abstract <K, V> void add(ObservableModelSet.Builder model, String name, TypeToken<K> keyType, TypeToken<V> valueType,
				QonfigElement element, ValueGetter<? extends M> map);
		}
		interpreter.createWith("multi-map", Void.class, new MultiMapCreator<ObservableMultiMap>() {
			@Override
			<K, V> ObservableMultiMap.Builder<K, V> create(TypeToken<K> keyType, TypeToken<V> type, ModelSetInstance models) {
				return ObservableMultiMap.build(keyType, type);
			}

			@Override
			<K, V> void add(ObservableModelSet.Builder model, String name, TypeToken<K> keyType, TypeToken<V> valueType,
				QonfigElement element, ValueGetter<? extends ObservableMultiMap> map) {
				model.withMultiMap(name, keyType, valueType, (ValueGetter<ObservableMultiMap<K, V>>) map);
			}
		});
		interpreter.createWith("sorted-multi-map", Void.class, new MultiMapCreator<ObservableSortedMultiMap>() {
			private Function<ModelSetInstance, Comparator<Object>> theComparator;

			@Override
			protected <K, V> void prepare(TypeToken<K> keyType, TypeToken<V> valueType, QonfigElement element) {
				theComparator = parseComparator((TypeToken<Object>) keyType, element.getAttribute("sort-with"));
			}

			@Override
			<K, V> ObservableSortedMultiMap.Builder<K, V> create(TypeToken<K> keyType, TypeToken<V> type, ModelSetInstance models) {
				return ObservableMultiMap.build(keyType, type)//
					.sortedBy(theComparator.apply(models));
			}

			@Override
			<K, V> void add(ObservableModelSet.Builder model, String name, TypeToken<K> keyType, TypeToken<V> valueType,
				QonfigElement element, ValueGetter<? extends ObservableSortedMultiMap> map) {
				model.withSortedMultiMap(name, keyType, valueType, (ValueGetter<ObservableSortedMultiMap<K, V>>) map);
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
		interpreter.createWith("ext-event", Void.class, new ExtModelValidator<Observable>() {
			@Override
			protected <V> void expect(Builder model, String name, TypeToken<V> type, ValueGetter<? extends Observable> valueGetter) {
				model.withEvent(name, type, (ValueGetter<Observable<? extends V>>) valueGetter);
			}

			@Override
			protected Observable getValue(ExternalModelSet extModels, String name, TypeToken<?> type) {
				return extModels.getEvent(name, type);
			}
		});
		interpreter.createWith("ext-value", Void.class, new ExtModelValidator<SettableValue>() {
			@Override
			protected <V> void expect(Builder model, String name, TypeToken<V> type, ValueGetter<? extends SettableValue> valueGetter) {
				model.withValue(name, type, (ValueGetter<SettableValue<V>>) valueGetter);
			}

			@Override
			protected SettableValue getValue(ExternalModelSet extModels, String name, TypeToken<?> type) {
				return extModels.getValue(name, type);
			}
		});
		interpreter.createWith("ext-list", Void.class, new ExtModelValidator<ObservableCollection>() {
			@Override
			protected <V> void expect(Builder model, String name, TypeToken<V> type,
				ValueGetter<? extends ObservableCollection> valueGetter) {
				model.withCollection(name, type, (ValueGetter<ObservableCollection<V>>) valueGetter);
			}

			@Override
			protected ObservableCollection getValue(ExternalModelSet extModels, String name, TypeToken<?> type) {
				return extModels.getCollection(name, type);
			}
		});
		interpreter.createWith("ext-set", Void.class, new ExtModelValidator<ObservableSet>() {
			@Override
			protected <V> void expect(Builder model, String name, TypeToken<V> type, ValueGetter<? extends ObservableSet> valueGetter) {
				model.withSet(name, type, (ValueGetter<ObservableSet<V>>) valueGetter);
			}

			@Override
			protected ObservableSet getValue(ExternalModelSet extModels, String name, TypeToken<?> type) {
				return extModels.getSet(name, type);
			}
		});
		interpreter.createWith("ext-sorted-list", Void.class, new ExtModelValidator<ObservableSortedCollection>() {
			@Override
			protected <V> void expect(Builder model, String name, TypeToken<V> type,
				ValueGetter<? extends ObservableSortedCollection> valueGetter) {
				model.withSortedCollection(name, type, (ValueGetter<ObservableSortedCollection<V>>) valueGetter);
			}

			@Override
			protected ObservableSortedCollection getValue(ExternalModelSet extModels, String name, TypeToken<?> type) {
				return extModels.getSortedCollection(name, type);
			}
		});
		interpreter.createWith("ext-sorted-set", Void.class, new ExtModelValidator<ObservableSortedSet>() {
			@Override
			protected <V> void expect(Builder model, String name, TypeToken<V> type,
				ValueGetter<? extends ObservableSortedSet> valueGetter) {
				model.withSortedSet(name, type, (ValueGetter<ObservableSortedSet<V>>) valueGetter);
			}

			@Override
			protected ObservableSortedSet getValue(ExternalModelSet extModels, String name, TypeToken<?> type) {
				return extModels.getSortedSet(name, type);
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
		interpreter.createWith("ext-map", Void.class, new ExtBiTypedModelValidator<ObservableMap>() {
			@Override
			protected <K, V> void expect(Builder model, String name, TypeToken<K> keyType, TypeToken<V> valueType,
				ValueGetter<? extends ObservableMap> valueGetter) {
				model.withMap(name, keyType, valueType, (ValueGetter<ObservableMap<K, V>>) valueGetter);
			}

			@Override
			protected ObservableMap getValue(ExternalModelSet extModels, String name, TypeToken<?> keyType, TypeToken<?> valueType) {
				return extModels.getMap(name, keyType, valueType);
			}
		});
		interpreter.createWith("ext-map", Void.class, new ExtBiTypedModelValidator<ObservableSortedMap>() {
			@Override
			protected <K, V> void expect(Builder model, String name, TypeToken<K> keyType, TypeToken<V> valueType,
				ValueGetter<? extends ObservableSortedMap> valueGetter) {
				model.withSortedMap(name, keyType, valueType, (ValueGetter<ObservableSortedMap<K, V>>) valueGetter);
			}

			@Override
			protected ObservableSortedMap getValue(ExternalModelSet extModels, String name, TypeToken<?> keyType, TypeToken<?> valueType) {
				return extModels.getSortedMap(name, keyType, valueType);
			}
		});
		interpreter.createWith("ext-multi-map", Void.class, new ExtBiTypedModelValidator<ObservableMultiMap>() {
			@Override
			protected <K, V> void expect(Builder model, String name, TypeToken<K> keyType, TypeToken<V> valueType,
				ValueGetter<? extends ObservableMultiMap> valueGetter) {
				model.withMultiMap(name, keyType, valueType, (ValueGetter<ObservableMultiMap<K, V>>) valueGetter);
			}

			@Override
			protected ObservableMultiMap getValue(ExternalModelSet extModels, String name, TypeToken<?> keyType, TypeToken<?> valueType) {
				return extModels.getMultiMap(name, keyType, valueType);
			}
		});
		interpreter.createWith("ext-sorted-multi-map", Void.class, new ExtBiTypedModelValidator<ObservableSortedMultiMap>() {
			@Override
			protected <K, V> void expect(Builder model, String name, TypeToken<K> keyType, TypeToken<V> valueType,
				ValueGetter<? extends ObservableSortedMultiMap> valueGetter) {
				model.withSortedMultiMap(name, keyType, valueType, (ValueGetter<ObservableSortedMultiMap<K, V>>) valueGetter);
			}

			@Override
			protected ObservableSortedMultiMap getValue(ExternalModelSet extModels, String name, TypeToken<?> keyType,
				TypeToken<?> valueType) {
				return extModels.getSortedMultiMap(name, keyType, valueType);
			}
		});
		interpreter.createWith("transform", Void.class, new QonfigInterpreter.QonfigValueCreator<Void>() {
			@Override
			public Void createValue(QonfigElement element, QonfigInterpretingSession session) throws ParseException {
				ObservableModelSet.Builder model = (ObservableModelSet.Builder) session.get("model");
				String name = element.getAttributeText("name");
				String sourceName = element.getAttributeText("source");
				ValueContainer<?, ?> source = model.getThing(sourceName);
				switch (source.getModelType()) {
				case Event:
					transformEvent((ValueContainer<Object, Observable<Object>>) source, element, model, name);
					break;
				case Value:
					transformValue((ValueContainer<Object, SettableValue<Object>>) source, element, model, name);
					break;
				case Collection:
				case SortedCollection:
				case Set:
				case SortedSet:
					transformCollection((ValueContainer<Object, ObservableCollection<Object>>) source, element, model, name);
					break;
				default:
					throw new IllegalArgumentException("Transformation unsupported for source of type " + source.getModelType());
				}
				return null;
			}
		});
		interpreter.createWith("file-source", Void.class, new QonfigInterpreter.QonfigValueCreator<Void>() {
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
							maxZipDepth = model.getValue(element.getAttributeText("max-zip-depth"), TypeTokens.get().INT);
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
				model.withValue(name, TypeTokens.get().of(FileDataSource.class), source);
				return null;
			}
		});
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

	private void transformEvent(ValueContainer<Object, Observable<Object>> source, QonfigElement transform, ObservableModelSet.Builder model,
		String name) {
		// skip
		// noInit
		// take
		// takeUntil
		for (QonfigElement op : transform.getChildrenInRole("op")) {
			switch (op.getType().getName()) {
			case "no-init":
				model.withEvent(name, source.getValueType(), (modelSet, extModels) -> source.get(modelSet).noInit());
				break;
			case "skip":
				int times = Integer.parseInt(op.getAttributeText("times"));
				model.withEvent(name, source.getValueType(), (modelSet, extModels) -> source.get(modelSet).skip(times));
				break;
			case "take":
				times = Integer.parseInt(op.getAttributeText("times"));
				model.withEvent(name, source.getValueType(), (modelSet, extModels) -> source.get(modelSet).take(times));
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
		ObservableModelSet.Builder model, String name) {
		ValueTransform transformFn = (v, models) -> v;
		TypeToken<Object> currentType = source.getValueType();
		for (QonfigElement op : transform.getChildrenInRole("op")) {
			ValueTransform prevTransformFn = transformFn;
			switch (op.getType().getName()) {
			case "take-until":
				throw new UnsupportedOperationException("Not yet implemented");// TODO
			case "map-to":
				ParsedTransformation ptx = transformation(currentType, op, model);
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
		model.withValue(name, currentType, (modelSet, extModel) -> finalTransform.transform(source.get(modelSet), modelSet));
	}

	private interface CollectionTransform {
		ObservableCollection.CollectionDataFlow<Object, ?, Object> transform(
			ObservableCollection.CollectionDataFlow<Object, ?, Object> source, ModelSetInstance models);
	}

	private void transformCollection(ValueContainer<Object, ObservableCollection<Object>> source, QonfigElement transform,
		ObservableModelSet.Builder model, String name) {
		CollectionTransform transformFn = (src, models) -> src;
		TypeToken<Object> currentType = source.getValueType();
		for (QonfigElement op : transform.getChildrenInRole("op")) {
			CollectionTransform prevTransform = transformFn;
			switch (op.getType().getName()) {
			case "map-to":
				ParsedTransformation ptx = transformation(currentType, op, model);
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
					Function<ModelSetInstance, Comparator<Object>> comparator = parseComparator(currentType, op.getAttribute("sort-with"));
					transformFn = (src, models) -> prevTransform.transform(src, models).distinctSorted(comparator.apply(models), useFirst);
				} else
					transformFn = (src, models) -> prevTransform.transform(src, models)
					.distinct(uo -> uo.useFirst(useFirst).preserveSourceOrder(preserveSourceOrder));
					break;
			case "sort":
				Function<ModelSetInstance, Comparator<Object>> comparator = parseComparator(currentType, op.getAttribute("sort-with"));
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
		model.withCollection(name, currentType, (models, extModels) -> {
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

	private ParsedTransformation transformation(TypeToken<Object> currentType, QonfigElement op, ObservableModelSet model) {
		List<ValueContainer<?, ? extends SettableValue<?>>> combined = new ArrayList<>(op.getChildrenInRole("combined-value").size());
		for (QonfigElement combine : op.getChildrenInRole("combined-value")) {
			String name = combine.getValue().toString();
			combined.add(model.getValue(name, null));
		}
		TypeToken<?>[] argTypes = new TypeToken[combined.size()];
		for (int i = 0; i < argTypes.length; i++)
			argTypes[i] = combined.get(i).getValueType();
		MethodFinder<Object, TransformationValues<?, ?>, ?, Object> map = findMethod(TypeTokens.get().OBJECT);
		map.withOption(argList(currentType).with(argTypes), (src, tv, __, args, models) -> {
			args[0] = src;
			for (int i = 0; i < combined.size(); i++)
				args[i + 1] = tv.get(combined.get(i).get(models));
		});
		TriFunction<Object, TransformationValues<?, ?>, ModelSetInstance, Object> mapFn = map.find2(op.getAttributeText("function"));
		TypeToken<Object> targetType = (TypeToken<Object>) map.getResultType();
		QonfigElement reverseEl = op.getChildrenInRole("reverse").peekFirst();
		return new ParsedTransformation() {
			private boolean stateful;
			private boolean inexact;
			TriFunction<Object, TransformationValues<?, ?>, ModelSetInstance, Object> reverseFn;
			TriConsumer<Object, TransformationValues<?, ?>, ModelSetInstance> reverseModifier;
			BiFn<TransformationValues<?, ?>, ModelSetInstance, String> enabled;
			TriFunction<Object, TransformationValues<?, ?>, ModelSetInstance, String> accept;
			QuadFn<Object, TransformationValues<?, ?>, Boolean, ModelSetInstance, Object> create;
			TriFunction<Object, TransformationValues<?, ?>, ModelSetInstance, String> addAccept;

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
						MethodFinder<Object, TransformationValues<?, ?>, ?, Void> reverse = findMethod(TypeTokens.get().VOID);
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
						TriFunction<Object, TransformationValues<?, ?>, ModelSetInstance, Void> modifyFn = reverse
							.find2(reverseEl.getAttributeText("function"));
						reverseModifier = (r, tv, models) -> {
							modifyFn.apply(r, tv, models);
						};
					} else {
						reverseModifier = null;
						MethodFinder<Object, TransformationValues<?, ?>, ?, Object> reverse = findMethod(currentType);
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
						reverseFn = reverse.find2(reverseEl.getAttributeText("function"));
					}

					if (reverseEl.getAttribute("enabled") != null) {
						enabled = ObservableModelQonfigParser.this
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
							}).find1(reverseEl.getAttributeText("enabled"));
					}
					if (reverseEl.getAttribute("accept") != null) {
						MethodFinder<Object, TransformationValues<?, ?>, ?, String> acceptFinder = findMethod(TypeTokens.get().STRING);
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
						accept = acceptFinder.find2(reverseEl.getAttributeText("accept"));
					}
					if (reverseEl.getAttribute("create") != null) {
						create = ObservableModelQonfigParser.this
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
							}).find3(reverseEl.getAttributeText("create"));
					}
					if (reverseEl.getAttribute("add-accept") != null) {
						MethodFinder<Object, TransformationValues<?, ?>, ?, String> addAcceptFinder = findMethod(TypeTokens.get().STRING);
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
						addAccept = addAcceptFinder.find2(reverseEl.getAttributeText("add-accept"));
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
					return precursor.build(mapFn.curry3(modelSet));
				Transformation.MaybeReversibleMapping<Object, Object> built;
				built = ((Transformation.ReversibleTransformationPrecursor<Object, Object, ?>) precursor).build(mapFn.curry3(modelSet));
				if (reverseFn == null && reverseModifier == null) {//
					return built;
				} else {
					Fn<TransformationValues<?, ?>, String> enabled2 = enabled == null ? null : enabled.curry2(modelSet);
					BiFunction<Object, TransformationValues<?, ?>, String> accept2 = accept == null ? null : accept.curry3(modelSet);
					TriFunction<Object, TransformationValues<?, ?>, Boolean, Object> create2 = create == null ? null
						: create.curry4(modelSet);
					BiFunction<Object, TransformationValues<?, ?>, String> addAccept2 = addAccept == null ? null
						: addAccept.curry3(modelSet);
					if (reverseModifier != null) {
						BiConsumer<Object, TransformationValues<?, ?>> reverseModifier2 = reverseModifier.curry3(modelSet);
						return built.withReverse(
							new Transformation.SourceModifyingReverse<>(reverseModifier2, enabled2, accept2, create2, addAccept2));
					} else {
						BiFunction<Object, TransformationValues<?, ?>, Object> reverseFn2 = reverseFn.curry3(modelSet);
						return built.withReverse(new Transformation.SourceReplacingReverse<>(built, reverseFn2, enabled2, accept2, create2,
							addAccept2, stateful, inexact));
					}
				}
			}
		};
	}

	private <T> Function<ModelSetInstance, Comparator<T>> parseComparator(TypeToken<T> type, Object given) {
		TriFunction<T, T, ModelSetInstance, Integer> compareFn = this.<T, T, Void, Integer> findMethod(TypeTokens.get().INT)//
			.withOption(argList(type, type), (v1, v2, __, args, models) -> {
				args[0] = v1;
				args[1] = v2;
			})//
			.find2(given.toString());
		return models -> (v1, v2) -> compareFn.apply(v1, v2, models);
	}

	<P1, P2, P3, T> MethodFinder<P1, P2, P3, T> findMethod(TypeToken<T> targetType) {
		return new MethodFinder<>(targetType);
	}

	BetterList<TypeToken<?>> argList(TypeToken<?>... init) {
		return new BetterTreeList<TypeToken<?>>(false).with(init);
	}

	private interface ParsedTransformation {
		TypeToken<Object> getTargetType();

		boolean isReversible();

		Transformation<Object, Object> transform(TransformationPrecursor<Object, Object, ?> precursor, ModelSetInstance modelSet);
	}

	interface Fn<P, T> extends Function<P, T> {
		@Override
		T apply(P arg1);

		default Supplier<T> curry1(P arg1) {
			return () -> apply(arg1);
		}
	}

	interface BiFn<P1, P2, T> extends BiFunction<P1, P2, T> {
		@Override
		T apply(P1 arg1, P2 arg2);

		default Fn<P1, T> curry2(P2 arg2) {
			return arg1 -> apply(arg1, arg2);
		}
	}

	interface QuadFn<P1, P2, P3, P4, T> {
		T apply(P1 arg1, P2 arg2, P3 arg3, P4 arg4);

		default TriFunction<P1, P2, P3, T> curry4(P4 arg4) {
			return (arg1, arg2, arg3) -> apply(arg1, arg2, arg3, arg4);
		}
	}

	private class MethodFinder<P1, P2, P3, T> {
		private final List<MethodOption<P1, P2, P3>> theOptions;
		private final TypeToken<T> theTargetType;
		private TypeToken<? extends T> theResultType;

		MethodFinder(TypeToken<T> targetType) {
			theOptions = new ArrayList<>(5);
			theTargetType = targetType;
		}

		MethodFinder<P1, P2, P3, T> withOption(BetterList<TypeToken<?>> argTypes, ArgMaker<P1, P2, P3> args) {
			theOptions.add(new MethodOption<>(argTypes.toArray(new TypeToken[argTypes.size()]), args));
			return this;
		}

		Fn<ModelSetInstance, T> find0(String methodName) {
			QuadFn<P1, P2, P3, ModelSetInstance, T> found = find3(methodName);
			return models -> found.apply(null, null, null, models);
		}

		BiFn<P1, ModelSetInstance, T> find1(String methodName) {
			QuadFn<P1, P2, P3, ModelSetInstance, T> found = find3(methodName);
			return (p1, models) -> found.apply(p1, null, null, models);
		}

		TriFunction<P1, P2, ModelSetInstance, T> find2(String methodName) {
			QuadFn<P1, P2, P3, ModelSetInstance, T> found = find3(methodName);
			return (p1, p2, models) -> found.apply(p1, p2, null, models);
		}

		QuadFn<P1, P2, P3, ModelSetInstance, T> find3(String methodName) {
			// TODO This is wrong--doesn't even account for parentheses
			// Need to allow model values to be passed in to methods at the end of the parameter list
			int lastDot = methodName.lastIndexOf('.');
			if (lastDot < 0)
				throw new IllegalArgumentException("'.' expected--cannot parse method with no qualifying type");
			String typeName = methodName.substring(0, lastDot);
			Class<?> type = theImports == null ? null : theImports.get(typeName);
			if (type == null) {
				try {
					type = Thread.currentThread().getContextClassLoader().loadClass(typeName);
				} catch (ClassNotFoundException e) {
					throw new IllegalArgumentException("Could not load class " + typeName);
				}
			}
			String method = methodName.substring(lastDot + 1);
			for (Method m : type.getMethods()) {
				if (!Modifier.isStatic(m.getModifiers()))
					continue;
				else if (m.isVarArgs())
					continue; // Not messing with this for now
				else if (!m.getName().equals(method))
					continue;
				TypeToken<?> returnType = TypeTokens.get().of(m.getGenericReturnType());
				if (!TypeTokens.get().isAssignable(theTargetType, returnType))
					continue;
				TypeToken<?>[] paramTypes = new TypeToken[m.getParameterCount()];
				for (int i = 0; i < paramTypes.length; i++)
					paramTypes[i] = TypeTokens.get().of(m.getGenericParameterTypes()[i]);
				for (MethodOption<P1, P2, P3> option : theOptions) {
					if (m.getParameterCount() != option.argTypes.length)
						continue;
					boolean matches = true;
					for (int i = 0; matches && i < option.argTypes.length; i++)
						matches = TypeTokens.get().isAssignable(paramTypes[i], option.argTypes[i]);
					if (!matches)
						continue;
					theResultType = (TypeToken<? extends T>) returnType;
					return (p1, p2, p3, models) -> {
						Object[] args = new Object[option.argTypes.length];
						option.argMaker.makeArgs(p1, p2, p3, args, models);
						for (int i = 0; i < args.length; i++)
							args[i] = TypeTokens.get().cast(paramTypes[i], args[i]);
						T result;
						try {
							result = (T) m.invoke(null, args);
						} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
							throw new IllegalStateException("Unable to invoke method " + m, e);
						}
						return result;
					};
				}
			}
			StringBuilder str = new StringBuilder("No such method '").append(methodName).append("' found with parameter types ");
			for (int i = 0; i < theOptions.size(); i++) {
				if (i > 0) {
					str.append(", ");
					if (i == theOptions.size() - 1)
						str.append("or ");
				}
				str.append(Arrays.toString(theOptions.get(i).argTypes));
			}
			throw new IllegalArgumentException(str.toString());
		}

		TypeToken<? extends T> getResultType() {
			return theResultType;
		}
	}

	interface ArgMaker<T, U, V> {
		void makeArgs(T t, U u, V v, Object[] args, ModelSetInstance models);
	}

	static class MethodOption<P1, P2, P3> {
		final TypeToken<?>[] argTypes;
		final ArgMaker<P1, P2, P3> argMaker;

		MethodOption(TypeToken<?>[] argTypes, ArgMaker<P1, P2, P3> argMaker) {
			this.argTypes = argTypes;
			this.argMaker = argMaker;
		}
	}

	private static <E extends Enum<E>> E parseEnum(TypeToken<?> type, String text) throws ParseException {
		try {
			return Enum.valueOf((Class<E>) TypeTokens.getRawType(type), text);
		} catch (IllegalArgumentException e) {
			throw new ParseException(e.getMessage(), 0);
		}
	}
}
