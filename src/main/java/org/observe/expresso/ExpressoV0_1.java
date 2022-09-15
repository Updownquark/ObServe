package org.observe.expresso;

import java.awt.Image;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
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
import org.observe.expresso.ModelType.ModelInstanceConverter;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ObservableExpression.MethodFinder;
import org.observe.expresso.ObservableModelSet.AbstractValueContainer;
import org.observe.expresso.ObservableModelSet.ExternalModelSet;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.observe.expresso.ObservableModelSet.ValueCreator;
import org.observe.expresso.ops.NameExpression;
import org.observe.util.TypeTokens;
import org.observe.util.swing.WindowPopulation;
import org.qommons.BiTuple;
import org.qommons.ThreadConstraint;
import org.qommons.TimeUtils;
import org.qommons.TimeUtils.TimeEvaluationOptions;
import org.qommons.TriFunction;
import org.qommons.Version;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.FastFailLockingStrategy;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.config.QommonsConfig;
import org.qommons.config.QonfigChildDef;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretation;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreterCore;
import org.qommons.config.QonfigInterpreterCore.CoreSession;
import org.qommons.config.QonfigInterpreterCore.QonfigValueCreator;
import org.qommons.config.QonfigInterpreterCore.QonfigValueModifier;
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
import org.qommons.tree.BetterTreeList;
import org.xml.sax.SAXException;

import com.google.common.reflect.TypeToken;

/** Qonfig Interpretation for the ExpressoV0_1 API */
public class ExpressoV0_1 implements QonfigInterpretation {
	public static final String PATH_KEY = "model-path";
	public static final String VALUE_TYPE_KEY = "value-type";
	public static final String KEY_TYPE_KEY = "key-type";

	public interface ConfigFormatProducer<T> {
		ObservableConfigFormat<T> getFormat(ModelSetInstance models);
	}

	public static abstract class AbstractConfigModelValue<M, MV extends M> implements Expresso.ConfigModelValue<M, MV> {
		private final ModelInstanceType<M, MV> theType;

		public AbstractConfigModelValue(ModelInstanceType<M, MV> type) {
			theType = type;
		}

		@Override
		public ModelInstanceType<M, MV> getType() {
			return theType;
		}
	}

	/** Represents an application so that various models in this class can provide intelligent interaction with the user */
	public interface AppEnvironment {
		/** @return A function to provide the title of the application */
		Function<ModelSetInstance, ? extends ObservableValue<String>> getTitle();

		/** @return A function to provide the icon representing the application */
		Function<ModelSetInstance, ? extends ObservableValue<Image>> getIcon();
	}

	@Override
	public Set<Class<? extends SpecialSession<?>>> getExpectedAPIs() {
		return Collections.singleton(ExpressoQIS.class);
	}

	@Override
	public String getToolkitName() {
		return ExpressoQIS.TOOLKIT_NAME;
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
		configureBaseModels(interpreter);
		configureExternalModels(interpreter);
		configureInternalModels(interpreter);
		configureConfigModels(interpreter);
		interpreter.createWith("first-value", ValueCreator.class, session -> createFirstValue(wrap(session)));
		interpreter.createWith("transform", ValueCreator.class, session -> createTransform(wrap(session)));
		configureFormats(interpreter);
		return interpreter;
	}

	ExpressoQIS wrap(CoreSession session) throws QonfigInterpretationException {
		return session.as(ExpressoQIS.class);
	}

	void configureBaseModels(QonfigInterpreterCore.Builder interpreter) {
		interpreter.createWith("imports", ClassView.class, session -> {
			ClassView.Builder builder = ClassView.build().withWildcardImport("java.lang");
			for (QonfigElement imp : session.getChildren("import")) {
				if (imp.getValueText().endsWith(".*"))
					builder.withWildcardImport(imp.getValueText().substring(0, imp.getValueText().length() - 2));
				else
					builder.withImport(imp.getValueText());
			}
			ClassView cv = builder.build();
			wrap(session).setModels(null, cv);
			TypeTokens.get().addClassRetriever(new TypeTokens.TypeRetriever() {
				@Override
				public Type getType(String typeName) {
					return cv.getType(typeName);
				}
			});
			return cv;
		}).createWith("models", ObservableModelSet.class, session -> {
			ExpressoQIS expressoSession = wrap(session);
			ObservableModelSet.Builder builder = ObservableModelSet.build(ObservableModelSet.JAVA_NAME_CHECKER);
			for (ExpressoQIS model : expressoSession.forChildren("model")) {
				ObservableModelSet.Builder subModel = builder.createSubModel(model.getAttributeText("named", "name"));
				expressoSession.setModels(subModel, null);
				model.setModels(subModel, null).interpret(ObservableModelSet.class);
			}
			ObservableModelSet built = builder.build();
			expressoSession.setModels(built, null);
			return built;
		}).modifyWith("model-value", Object.class, new QonfigValueModifier<Object>() {
			@Override
			public void prepareSession(CoreSession session) throws QonfigInterpretationException {
				if (session.get(VALUE_TYPE_KEY) == null) {
					String typeStr = session.getAttributeText("type");
					if (typeStr != null && !typeStr.isEmpty())
						session.put(VALUE_TYPE_KEY, parseType(typeStr, wrap(session).getExpressoEnv()));
				}
				if (session.isInstance("model-element"))
					session.put(PATH_KEY, wrap(session).getExpressoEnv().getModels().getPath() + "."
						+ session.getAttributeText("model-element", "name"));
			}

			@Override
			public Object modifyValue(Object value, CoreSession session) throws QonfigInterpretationException {
				return value;
			}
		}).modifyWith("map", Object.class, new QonfigValueModifier<Object>() {
			@Override
			public void prepareSession(CoreSession session) throws QonfigInterpretationException {
				if (session.get(KEY_TYPE_KEY) == null) {
					String typeStr = session.getAttributeText("key-type");
					if (typeStr != null && !typeStr.isEmpty())
						session.put(KEY_TYPE_KEY, parseType(typeStr, wrap(session).getExpressoEnv()));
				}
			}

			@Override
			public Object modifyValue(Object value, CoreSession session) throws QonfigInterpretationException {
				return value;
			}
		});
	}

	void configureExternalModels(QonfigInterpreterCore.Builder interpreter) {
		interpreter.createWith("ext-model", ObservableModelSet.class, session -> {
			ExpressoQIS eqis = wrap(session);
			ObservableModelSet.Builder model = (ObservableModelSet.Builder) eqis.getExpressoEnv().getModels();
			QonfigChildDef subModelRole = session.getRole("sub-model");
			QonfigChildDef valueRole = session.getRole("value");
			StringBuilder path = new StringBuilder(model.getPath());
			int pathLen = path.length();
			for (ExpressoQIS child : eqis.forChildren()) {
				if (child.fulfills(valueRole)) {
					String name = child.getAttributeText("model-element", "name");
					path.append('.').append(name);
					String childPath = path.toString();
					path.setLength(pathLen);
					Expresso.ExtModelValue<?> container = child.interpret(Expresso.ExtModelValue.class);
					ModelInstanceType<Object, Object> childType = (ModelInstanceType<Object, Object>) container.getType(child);
					ValueContainer<Object, Object> defaultV = child.getAttribute("default", childType, () -> null);
					model.withExternal(name, childType, new ObservableModelSet.ExtValueRef<Object>() {
						@Override
						public Object get(ExternalModelSet extModels) {
							try {
								return extModels.get(childPath, childType);
							} catch (IllegalArgumentException | QonfigInterpretationException e) {
								if (defaultV == null)
									throw new IllegalArgumentException(
										"External model " + model.getPath() + " does not match expected: " + e.getMessage(), e);
							}
							return null;
						}

						@Override
						public Object getDefault(ModelSetInstance models) {
							return defaultV.get(models);
						}
					});
				} else if (child.fulfills(subModelRole)) {
					ObservableModelSet.Builder subModel = model.createSubModel(child.getAttributeText("named", "name"));
					child.setModels(subModel, null);
					child.interpret(ObservableModelSet.class);
				}
			}
			return model;
		})//
		.createWith("event", Expresso.ExtModelValue.class, session -> new Expresso.ExtModelValue.SingleTyped<>(ModelTypes.Event))//
		.createWith("action", Expresso.ExtModelValue.class, session -> new Expresso.ExtModelValue.SingleTyped<>(ModelTypes.Action))//
		.createWith("value", Expresso.ExtModelValue.class, session -> new Expresso.ExtModelValue.SingleTyped<>(ModelTypes.Value))//
		.createWith("list", Expresso.ExtModelValue.class, session -> new Expresso.ExtModelValue.SingleTyped<>(ModelTypes.Collection))//
		.createWith("set", Expresso.ExtModelValue.class, session -> new Expresso.ExtModelValue.SingleTyped<>(ModelTypes.Set))//
		.createWith("sorted-list", Expresso.ExtModelValue.class, session -> new Expresso.ExtModelValue.SingleTyped<>(ModelTypes.SortedCollection))//
		.createWith("sorted-set", Expresso.ExtModelValue.class, session -> new Expresso.ExtModelValue.SingleTyped<>(ModelTypes.SortedSet))//
		.createWith("value-set", Expresso.ExtModelValue.class, session -> new Expresso.ExtModelValue.SingleTyped<>(ModelTypes.ValueSet))//
		.createWith("map", Expresso.ExtModelValue.class, session -> new Expresso.ExtModelValue.DoubleTyped<>(ModelTypes.Map))//
		.createWith("sorted-map", Expresso.ExtModelValue.class, session -> new Expresso.ExtModelValue.DoubleTyped<>(ModelTypes.SortedMap))//
		.createWith("multi-map", Expresso.ExtModelValue.class, session -> new Expresso.ExtModelValue.DoubleTyped<>(ModelTypes.MultiMap))//
		.createWith("sorted-multi-map", Expresso.ExtModelValue.class, session -> new Expresso.ExtModelValue.DoubleTyped<>(ModelTypes.SortedMultiMap))//
		;
	}

	void configureInternalModels(QonfigInterpreterCore.Builder interpreter) {
		abstract class InternalCollectionValue<C extends ObservableCollection<?>>
		implements QonfigValueCreator<ValueCreator<C, C>> {
			private final ModelType<C> theType;

			protected InternalCollectionValue(ModelType<C> type) {
				theType = type;
			}

			@Override
			public ValueCreator<C, C> createValue(CoreSession session) throws QonfigInterpretationException {
				TypeToken<Object> type = session.get(VALUE_TYPE_KEY, TypeToken.class);
				List<ValueCreator<SettableValue<?>, SettableValue<Object>>> elCreators = session.asElement("int-list")
					.interpretChildren("element", ValueCreator.class);
				return () -> {
					try {
						prepare(type, wrap(session));
					} catch (QonfigInterpretationException e) {
						session.withError(e.getMessage(), e);
					}
					List<ValueContainer<SettableValue<?>, SettableValue<Object>>> elContainers = new ArrayList<>(elCreators.size());
					for (ValueCreator<SettableValue<?>, SettableValue<Object>> creator : elCreators)
						elContainers.add(creator.createValue());
					return new AbstractValueContainer<C, C>((ModelInstanceType<C, C>) theType.forTypes(type)) {
						@Override
						public C get(ModelSetInstance models) {
							C collection = (C) create(type, models).withDescription(session.get(PATH_KEY, String.class)).build();
							for (ValueContainer<SettableValue<?>, SettableValue<Object>> value : elContainers) {
								if (!((ObservableCollection<Object>) collection).add(value.apply(models).get()))
									System.err.println("Warning: Value " + value + " already added to " + session.get(PATH_KEY));
							}
							return collection;
						}
					};
				};
			}

			protected <V> void prepare(TypeToken<V> type, ExpressoQIS session) throws QonfigInterpretationException {
			}

			protected abstract <V> ObservableCollectionBuilder<V, ?> create(TypeToken<V> type, ModelSetInstance models);
		}
		abstract class InternalMapValue<M extends ObservableMap<?, ?>>
		implements QonfigValueCreator<ValueCreator<M, M>> {
			private final ModelType<M> theType;

			protected InternalMapValue(ModelType<M> type) {
				theType = type;
			}

			@Override
			public ValueCreator<M, M> createValue(CoreSession session) throws QonfigInterpretationException {
				TypeToken<Object> keyType = session.get(KEY_TYPE_KEY, TypeToken.class);
				TypeToken<Object> valueType = session.get(VALUE_TYPE_KEY, TypeToken.class);
				List<BiTuple<ValueCreator<SettableValue<?>, SettableValue<Object>>, ValueCreator<SettableValue<?>, SettableValue<Object>>>> entryCreators;
				entryCreators = session.asElement("int-map").interpretChildren("entry", BiTuple.class);
				return () -> {
					List<BiTuple<ValueContainer<SettableValue<?>, SettableValue<Object>>, ValueContainer<SettableValue<?>, SettableValue<Object>>>> entryContainers;
					entryContainers = new ArrayList<>(entryCreators.size());
					for (BiTuple<ValueCreator<SettableValue<?>, SettableValue<Object>>, ValueCreator<SettableValue<?>, SettableValue<Object>>> entry : entryCreators) {
						entryContainers.add(new BiTuple<>(entry.getValue1().createValue(), entry.getValue2().createValue()));
					}
					try {
						prepare(keyType, valueType, wrap(session));
					} catch (QonfigInterpretationException e) {
						session.withError(e.getMessage(), e);
					}
					return new AbstractValueContainer<M, M>((ModelInstanceType<M, M>) theType.forTypes(keyType, valueType)) {
						@Override
						public M get(ModelSetInstance models) {
							M map = (M) create(keyType, valueType, models).withDescription(session.get(PATH_KEY, String.class)).buildMap();
							for (BiTuple<ValueContainer<SettableValue<?>, SettableValue<Object>>, ValueContainer<SettableValue<?>, SettableValue<Object>>> entry : entryContainers) {
								Object key = entry.getValue1().apply(models).get();
								if (map.containsKey(key))
									System.err.println(
										"Warning: Entry for key " + entry.getValue1() + " already added to " + session.get(PATH_KEY));
								else
									((ObservableMap<Object, Object>) map).put(key, entry.getValue2().apply(models).get());
							}
							return map;
						}
					};
				};
			}

			protected <K, V> void prepare(TypeToken<K> keyType, TypeToken<V> valueType, ExpressoQIS session)
				throws QonfigInterpretationException {
			}

			protected abstract <K, V> ObservableMap.Builder<K, V, ?> create(TypeToken<K> keyType, TypeToken<V> valueType,
				ModelSetInstance models);
		}
		abstract class InternalMultiMapValue<M extends ObservableMultiMap<?, ?>>
		implements QonfigValueCreator<ValueCreator<M, M>> {
			private final ModelType<M> theType;

			protected InternalMultiMapValue(ModelType<M> type) {
				theType = type;
			}

			@Override
			public ValueCreator<M, M> createValue(CoreSession session) throws QonfigInterpretationException {
				TypeToken<Object> keyType = session.get(KEY_TYPE_KEY, TypeToken.class);
				TypeToken<Object> valueType = session.get(VALUE_TYPE_KEY, TypeToken.class);
				List<BiTuple<ValueCreator<SettableValue<?>, SettableValue<Object>>, ValueCreator<SettableValue<?>, SettableValue<Object>>>> entryCreators;
				entryCreators = session.asElement("int-map").interpretChildren("entry", BiTuple.class);
				return () -> {
					List<BiTuple<ValueContainer<SettableValue<?>, SettableValue<Object>>, ValueContainer<SettableValue<?>, SettableValue<Object>>>> entryContainers;
					entryContainers = new ArrayList<>(entryCreators.size());
					for (BiTuple<ValueCreator<SettableValue<?>, SettableValue<Object>>, ValueCreator<SettableValue<?>, SettableValue<Object>>> entry : entryCreators) {
						entryContainers.add(new BiTuple<>(entry.getValue1().createValue(), entry.getValue2().createValue()));
					}
					try {
						prepare(keyType, valueType, wrap(session));
					} catch (QonfigInterpretationException e) {
						session.withError(e.getMessage(), e);
					}
					return new AbstractValueContainer<M, M>((ModelInstanceType<M, M>) theType.forTypes(keyType, valueType)) {
						@Override
						public M get(ModelSetInstance models) {
							M map = (M) create(keyType, valueType, models).withDescription(session.get(PATH_KEY, String.class))
								.build(models.getUntil());
							for (BiTuple<ValueContainer<SettableValue<?>, SettableValue<Object>>, ValueContainer<SettableValue<?>, SettableValue<Object>>> entry : entryContainers) {
								((ObservableMultiMap<Object, Object>) map).add(entry.getValue1().apply(models),
									entry.getValue2().apply(models));
							}
							return map;
						}
					};
				};
			}

			protected <K, V> void prepare(TypeToken<K> keyType, TypeToken<V> valueType, ExpressoQIS session)
				throws QonfigInterpretationException {
			}

			protected abstract <K, V> ObservableMultiMap.Builder<K, V, ?> create(TypeToken<K> keyType, TypeToken<V> valueType,
				ModelSetInstance models);
		}
		interpreter.createWith("model", ObservableModelSet.class, session -> {
			ExpressoQIS eqis = wrap(session);
			ObservableModelSet.Builder model = (ObservableModelSet.Builder) eqis.getExpressoEnv().getModels();
			QonfigChildDef subModelRole = session.getRole("sub-model");
			QonfigChildDef valueRole = session.getRole("value");
			for (ExpressoQIS child : eqis.forChildren()) {
				if (child.fulfills(valueRole)) {
					ValueCreator<?, ?> container = child.interpret(ValueCreator.class);
					model.withMaker(child.getAttributeText("model-element", "name"), container);
				} else if (child.fulfills(subModelRole)) {
					ObservableModelSet.Builder subModel = model.createSubModel(child.getAttributeText("abst-model", "name"));
					child.setModels(subModel, null);
					child.interpret(ObservableModelSet.class);
				}
			}
			return model;
		}).createWith("constant", ValueCreator.class, session -> {
			ExpressoQIS exS = wrap(session);
			TypeToken<Object> valueType = (TypeToken<Object>) session.get(VALUE_TYPE_KEY);
			ObservableExpression value = exS.getValueExpression();
			return new ValueCreator<SettableValue<?>, SettableValue<Object>>() {
				@Override
				public ValueContainer<SettableValue<?>, SettableValue<Object>> createValue() {
					ValueContainer<SettableValue<?>, SettableValue<Object>> valueC;
					try {
						valueC = value.evaluate(//
							valueType == null
							? (ModelInstanceType<SettableValue<?>, SettableValue<Object>>) (ModelInstanceType<?, ?>) ModelTypes.Value
								.any()
								: ModelTypes.Value.forType(valueType),
								exS.getExpressoEnv());
					} catch (QonfigInterpretationException e) {
						throw new IllegalStateException("Could not interpret value of constant: " + value, e);
					}
					return new ValueContainer<SettableValue<?>, SettableValue<Object>>() {
						@Override
						public ModelInstanceType<SettableValue<?>, SettableValue<Object>> getType() {
							return valueC.getType();
						}

						@Override
						public SettableValue<Object> get(ModelSetInstance models) {
							Object v = valueC.apply(models).get();
							return SettableValue.of((TypeToken<Object>) getType().getType(0), v, "Constant value");
						}
					};
				}
			};
		}).createWith("value", ValueCreator.class, session -> {
			ExpressoQIS exS = wrap(session);
			ObservableExpression valueX = exS.getValueExpression();
			ObservableExpression initX = session.isInstance("int-value") ? exS.asElement("int-value").getAttributeExpression("init") : null;
			if (initX != null && valueX != null)
				session.withWarning("Either a value or an init value may be specified, but not both.  Initial value will be ignored.");
			return () -> {
				TypeToken<Object> type = (TypeToken<Object>) session.get(VALUE_TYPE_KEY);
				ValueContainer<SettableValue<?>, SettableValue<Object>> value;
				try {
					value = valueX == null ? null : parseValue(//
						exS.getExpressoEnv(), type, valueX);
				} catch (QonfigInterpretationException e) {
					session.withError(e.getMessage(), e);
					value = null;
				}
				ValueContainer<SettableValue<?>, SettableValue<Object>> init;
				try {
					init = initX == null ? null : parseValue(//
						exS.getExpressoEnv(), type, initX);
				} catch (QonfigInterpretationException e) {
					session.withError(e.getMessage(), e);
					init = null;
				}
				if (value == null && init == null && type == null) {
					session.withError("Either a type or a value must be specified");
					return null;
				}
				ValueContainer<SettableValue<?>, SettableValue<Object>> fValue = value;
				ValueContainer<SettableValue<?>, SettableValue<Object>> fInit = init;
				ModelInstanceType<SettableValue<?>, SettableValue<Object>> fType;
				if (type != null)
					fType = ModelTypes.Value.forType(type);
				else if (value != null)
					fType = value.getType();
				else
					fType = init.getType();
				return new ObservableModelSet.AbstractValueContainer<SettableValue<?>, SettableValue<Object>>(fType) {
					@Override
					public SettableValue<Object> get(ModelSetInstance models) {
						if (fValue != null)
							return fValue.get(models);
						SettableValue.Builder<Object> builder = SettableValue.build((TypeToken<Object>) fType.getType(0));
						builder.withDescription((String) session.get(PATH_KEY));
						if (fInit != null)
							builder.withValue(fInit.apply(models).get());
						return builder.build();
					}
				};
			};
		}).createWith("action", ValueCreator.class, session -> {
			ExpressoQIS exS = wrap(session);
			ObservableExpression valueX = exS.getValueExpression();
			ObservableExpression disableX = exS.getAttributeExpression("disable-with");
			return () -> {
				TypeToken<Object> type = (TypeToken<Object>) session.get(VALUE_TYPE_KEY);
				try {
					ValueContainer<ObservableAction<?>, ObservableAction<Object>> action = valueX.evaluate(
						ModelTypes.Action.forType(type == null ? (TypeToken<Object>) (TypeToken<?>) TypeTokens.get().VOID : type),
						exS.getExpressoEnv());
					if (disableX != null) {
						ValueContainer<SettableValue<?>, SettableValue<String>> disableV = disableX
							.evaluate(ModelTypes.Value.forType(String.class), exS.getExpressoEnv());
						action = action.map(action.getType(), (a, msi) -> {
							ObservableValue<String> disabledMsg = disableV.get(msi);
							return a.disableWith(disabledMsg);
						});
					}
					return action;
				} catch (QonfigInterpretationException e) {
					session.withError(e.getMessage(), e);
					return null;
				}
			};
		}).createWith("value-set", ValueCreator.class, session -> () -> {
			TypeToken<Object> type = session.get(VALUE_TYPE_KEY, TypeToken.class);
			return new AbstractValueContainer<ObservableValueSet<?>, ObservableValueSet<Object>>(ModelTypes.ValueSet.forType(type)) {
				@Override
				public ObservableValueSet<Object> get(ModelSetInstance models) {
					// Although a purely in-memory value set would be more efficient, I have yet to implement one.
					// Easiest path forward for this right now is to make an unpersisted ObservableConfig and use it to back the value set.
					// TODO At some point I should come back and make an in-memory implementation and use it here.
					ObservableConfig config = ObservableConfig.createRoot("root", null,
						__ -> new FastFailLockingStrategy(ThreadConstraint.ANY));
					return config.asValue(type).buildEntitySet(null);
				}
			};
		}).createWith("element", ValueCreator.class, session -> {
			ExpressoQIS exS = wrap(session);
			return () -> {
				try {
					return parseValue(//
						exS.getExpressoEnv(), (TypeToken<Object>) session.get(VALUE_TYPE_KEY), exS.getValueExpression());
				} catch (QonfigInterpretationException e) {
					session.withError(e.getMessage(), e);
					return null;
				}
			};
		}).createWith("list", ValueCreator.class, new InternalCollectionValue<ObservableCollection<?>>(ModelTypes.Collection) {
			@Override
			protected <V> ObservableCollectionBuilder<V, ?> create(TypeToken<V> type, ModelSetInstance models) {
				return ObservableCollection.build(type);
			}
		}).createWith("set", ValueCreator.class, new InternalCollectionValue<ObservableSet<?>>(ModelTypes.Set) {
			@Override
			protected <V> ObservableCollectionBuilder<V, ?> create(TypeToken<V> type, ModelSetInstance models) {
				return ObservableSet.build(type);
			}
		}).createWith("sorted-set", ValueCreator.class, new InternalCollectionValue<ObservableSortedSet<?>>(ModelTypes.SortedSet) {
			private Function<ModelSetInstance, Comparator<Object>> theComparator;

			@Override
			protected <V> void prepare(TypeToken<V> type, ExpressoQIS session) throws QonfigInterpretationException {
				theComparator = parseComparator((TypeToken<Object>) type, session.getAttributeExpression("sort-with"),
					session.getExpressoEnv());
			}

			@Override
			protected <V> ObservableCollectionBuilder<V, ?> create(TypeToken<V> type, ModelSetInstance models) {
				return ObservableSortedSet.build(type, (Comparator<? super V>) theComparator);
			}
		}).createWith("sorted-list", ValueCreator.class,
			new InternalCollectionValue<ObservableSortedCollection<?>>(ModelTypes.SortedCollection) {
			private Function<ModelSetInstance, Comparator<Object>> theComparator;

			@Override
			protected <V> void prepare(TypeToken<V> type, ExpressoQIS session) throws QonfigInterpretationException {
				theComparator = parseComparator((TypeToken<Object>) type, session.getAttributeExpression("sort-with"),
					session.getExpressoEnv());
			}

			@Override
			protected <V> ObservableCollectionBuilder<V, ?> create(TypeToken<V> type, ModelSetInstance models) {
				return ObservableSortedCollection.build(type, (Comparator<? super V>) theComparator);
			}
		}).createWith("entry", BiTuple.class, session -> {
			ExpressoQIS exS = wrap(session);
			TypeToken<Object> keyType = session.get(KEY_TYPE_KEY, TypeToken.class);
			TypeToken<Object> valueType = session.get(VALUE_TYPE_KEY, TypeToken.class);
			ValueContainer<SettableValue<?>, SettableValue<Object>> key = parseValue(//
				exS.getExpressoEnv(), keyType, exS.getAttributeExpression("key"));
			ValueContainer<SettableValue<?>, SettableValue<Object>> value = parseValue(//
				exS.getExpressoEnv(), valueType, exS.getValueExpression());
			return new BiTuple<>(key, value);
		}).createWith("map", ValueCreator.class, new InternalMapValue<ObservableMap<?, ?>>(ModelTypes.Map) {
			@Override
			protected <K, V> ObservableMap.Builder<K, V, ?> create(TypeToken<K> keyType, TypeToken<V> valueType,
				ModelSetInstance models) {
				return ObservableMap.build(keyType, valueType);
			}
		}).createWith("sorted-map", ValueCreator.class, new InternalMapValue<ObservableSortedMap<?, ?>>(ModelTypes.SortedMap) {
			private Function<ModelSetInstance, Comparator<Object>> theComparator;

			@Override
			protected <K, V> void prepare(TypeToken<K> keyType, TypeToken<V> valueType, ExpressoQIS session)
				throws QonfigInterpretationException {
				theComparator = parseComparator((TypeToken<Object>) keyType,
					session.getAttributeExpression("sort-with"), session.getExpressoEnv());
			}

			@Override
			protected <K, V> ObservableMap.Builder<K, V, ?> create(TypeToken<K> keyType, TypeToken<V> valueType,
				ModelSetInstance models) {
				return ObservableSortedMap.build(keyType, valueType, theComparator.apply(models));
			}
		}).createWith("multi-map", ValueCreator.class, new InternalMultiMapValue<ObservableMultiMap<?, ?>>(ModelTypes.MultiMap) {
			@Override
			protected <K, V> ObservableMultiMap.Builder<K, V, ?> create(TypeToken<K> keyType, TypeToken<V> valueType,
				ModelSetInstance models) {
				return ObservableMultiMap.build(keyType, valueType);
			}
		}).createWith("sorted-multi-map", ValueCreator.class, new InternalMultiMapValue<ObservableMultiMap<?, ?>>(ModelTypes.MultiMap) {
			private Function<ModelSetInstance, Comparator<Object>> theComparator;

			@Override
			protected <K, V> void prepare(TypeToken<K> keyType, TypeToken<V> valueType, ExpressoQIS session)
				throws QonfigInterpretationException {
				theComparator = parseComparator((TypeToken<Object>) keyType,
					session.getAttributeExpression("sort-with"), session.getExpressoEnv());
			}

			@Override
			protected <K, V> ObservableMultiMap.Builder<K, V, ?> create(TypeToken<K> keyType, TypeToken<V> valueType,
				ModelSetInstance models) {
				return ObservableMultiMap.build(keyType, valueType).sortedBy(theComparator.apply(models));
			}
		});
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

	private ValueCreator<SettableValue<?>, SettableValue<Object>> createFirstValue(ExpressoQIS session)
		throws QonfigInterpretationException {
		List<ValueCreator<SettableValue<?>, SettableValue<?>>> valueCreators = session.interpretChildren("value", ValueCreator.class);
		return () -> {
			List<ValueContainer<SettableValue<?>, SettableValue<?>>> valueContainers = valueCreators.stream()//
				.map(ValueCreator::createValue).collect(Collectors.toList());
			TypeToken<Object> commonType = (TypeToken<Object>) TypeTokens.get()
				.getCommonType(valueContainers.stream().map(v -> v.getType().getType(0)).collect(Collectors.toList()));
			return new AbstractValueContainer<SettableValue<?>, SettableValue<Object>>(ModelTypes.Value.forType(commonType)) {
				@Override
				public SettableValue<Object> get(ModelSetInstance models) {
					SettableValue<?>[] vs = new SettableValue[valueCreators.size()];
					for (int i = 0; i < vs.length; i++)
						vs[i] = valueContainers.get(i).get(models);
					return SettableValue.firstValue(commonType, v -> v != null, () -> null, vs);
				}
			};
		};
	}

	private ValueCreator<?, ?> createTransform(ExpressoQIS session) throws QonfigInterpretationException {
		ObservableExpression sourceX = session.getAttributeExpression("source");
		return new ValueCreator<Object, Object>() {
			@Override
			public ValueContainer<Object, Object> createValue() {
				ValueContainer<?, ?> source = null;
				try {
					if (sourceX instanceof NameExpression)
						source = session.getExpressoEnv().getModels().get(sourceX.toString(), false);
					if (source == null)
						source = sourceX.evaluate(ModelTypes.Value.any(), session.getExpressoEnv());
					ModelType<?> type = source.getType().getModelType();
					if (type == ModelTypes.Event)
						return (ValueContainer<Object, Object>) transformEvent((ValueContainer<Observable<?>, Observable<Object>>) source,
							session, 0);
					else if (type == ModelTypes.Value)
						return (ValueContainer<Object, Object>) transformValue(
							(ValueContainer<SettableValue<?>, SettableValue<Object>>) source, session, 0);
					else if (type == ModelTypes.Collection || type == ModelTypes.SortedCollection || type == ModelTypes.Set
						|| type == ModelTypes.SortedSet) // TODO Support transformation for ValueSets
						return (ValueContainer<Object, Object>) (ValueContainer<?, ?>) transformCollection(
							(ValueContainer<ObservableCollection<?>, ObservableCollection<Object>>) source, session, 0);
					else
						throw new IllegalArgumentException(
							"Transformation unsupported for source of type " + source.getType().getModelType());
				} catch (QonfigInterpretationException e) {
					session.withError(e.getMessage(), e);
					return null;
				}
			}
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

	public static TypeToken<?> parseType(String text, ExpressoEnv env) throws QonfigInterpretationException {
		VariableType vbl;
		try {
			vbl = VariableType.parseType(text, env.getClassView());
		} catch (ParseException e) {
			throw new QonfigInterpretationException("Could not parse type '" + text + "'", e);
		}
		TypeToken<?> type = vbl.getType(env.getModels());
		return type;
	}

	<T> ValueContainer<SettableValue<?>, SettableValue<T>> parseValue(ExpressoEnv env, TypeToken<T> type, ObservableExpression expression)
		throws QonfigInterpretationException {
		if (expression == null)
			return null;
		return expression
			.evaluate(
				type == null ? (ModelInstanceType<SettableValue<?>, SettableValue<T>>) (ModelInstanceType<?, ?>) ModelTypes.Value.any()
					: ModelTypes.Value.forType(type), env);
	}

	private ValueContainer<?, ?> transformEvent(ValueContainer<Observable<?>, Observable<Object>> source, ExpressoQIS transform,
		int startOp) throws QonfigInterpretationException {
		List<? extends ExpressoQIS> ops = transform.forChildren("op");
		for (int i = startOp; i < ops.size(); i++) {
			ExpressoQIS op = ops.get(i);
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

	private ValueContainer<?, ?> transformValue(ValueContainer<SettableValue<?>, SettableValue<Object>> source, ExpressoQIS transform,
		int startOp) throws QonfigInterpretationException {
		ValueTransform transformFn = (v, models) -> v;
		TypeToken<Object> currentType = (TypeToken<Object>) source.getType().getType(0);
		List<? extends ExpressoQIS> ops = transform.forChildren("op");
		for (int i = startOp; i < ops.size(); i++) {
			ExpressoQIS op = ops.get(i);
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
				Function<ModelSetInstance, Observable<?>> refresh = op.getAttributeExpression("on")
				.evaluate(ModelTypes.Event.any(), op.getExpressoEnv());
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
				if (op.getAttributeExpression("function") != null) {
					MethodFinder<Object, Object, Object, Object> finder = op.getAttributeExpression("function")
						.<Object, Object, Object, Object> findMethod(TypeTokens.get().of(Object.class), op.getExpressoEnv())//
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
					transformFn = (v, models) -> {
						SettableValue<Object> intermediate = prevTransformFn.transform(v, models);
						if (SettableValue.class.isAssignableFrom(resultClass))
							return SettableValue.flatten((ObservableValue<SettableValue<Object>>) (ObservableValue<?>) intermediate);
						else
							return SettableValue.flattenAsSettable(
								(ObservableValue<ObservableValue<Object>>) (ObservableValue<?>) intermediate, () -> null);
					};
					break;
				} else if (ObservableCollection.class.isAssignableFrom(resultClass)) {
					ModelInstanceType<ObservableCollection<?>, ObservableCollection<Object>> modelType;
					TypeToken<Object> elementType = (TypeToken<Object>) resultType
						.resolveType(ObservableCollection.class.getTypeParameters()[0]);
					if (ObservableSet.class.isAssignableFrom(resultClass))
						modelType = (ModelInstanceType<ObservableCollection<?>, ObservableCollection<Object>>) (ModelInstanceType<?, ?>) ModelTypes.Set
						.forType(elementType);
					else
						modelType = (ModelInstanceType<ObservableCollection<?>, ObservableCollection<Object>>) (ModelInstanceType<?, ?>) ModelTypes.Collection
						.forType(elementType);
					ValueTransform penultimateTransform = transformFn;
					ValueContainer<ObservableCollection<?>, ObservableCollection<Object>> collectionContainer = new ValueContainer<ObservableCollection<?>, ObservableCollection<Object>>() {
						@Override
						public ModelInstanceType<ObservableCollection<?>, ObservableCollection<Object>> getType() {
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
					return transformCollection(collectionContainer, transform, i + 1);
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
		String typeName = transform.getAttributeText("type");
		if (typeName != null && !typeName.isEmpty()) {
			ModelInstanceType<SettableValue<?>, SettableValue<Object>> targetType = ModelTypes.Value.forType(//
				(TypeToken<Object>) parseType(typeName, transform.getExpressoEnv()));
			ModelInstanceConverter<SettableValue<?>, SettableValue<?>> converter = ModelTypes.Value.forType(currentType)
				.convert(targetType);
			if (converter == null)
				throw new QonfigInterpretationException("Cannot convert from " + currentType + " to " + targetType.getType(0));
			ValueTransform prevTransformFn = transformFn;
			transformFn = (v, models) -> {
				SettableValue<Object> intermediate = prevTransformFn.transform(v, models);
				return (SettableValue<Object>) converter.convert(intermediate);
			};
			currentType = (TypeToken<Object>) targetType.getType(0);
		}

		ValueTransform finalTransform = transformFn;
		return source.map(ModelTypes.Value.forType(currentType), (v, models) -> finalTransform.transform(v, models));
	}

	private interface CollectionTransform {
		ObservableCollection.CollectionDataFlow<Object, ?, Object> transform(
			ObservableCollection.CollectionDataFlow<Object, ?, Object> source, ModelSetInstance models);
	}

	private ValueContainer<ObservableCollection<?>, ObservableCollection<Object>> transformCollection(
		ValueContainer<ObservableCollection<?>, ObservableCollection<Object>> source, ExpressoQIS transform, int startOp)
			throws QonfigInterpretationException {
		CollectionTransform transformFn = (src, models) -> src;
		TypeToken<Object> currentType = (TypeToken<Object>) source.getType().getType(0);
		List<? extends ExpressoQIS> ops = transform.forChildren("op");
		for (int i = startOp; i < ops.size(); i++) {
			ExpressoQIS op = ops.get(i);
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
				Function<ModelSetInstance, Observable<?>> refresh = op.getAttributeExpression("on")
				.evaluate(ModelTypes.Event.any(), op.getExpressoEnv());
				transformFn = (v, models) -> prevTransform.transform(v, models).refresh(refresh.apply(models));
				break;
			case "refresh-each":
				throw new UnsupportedOperationException("Not yet implemented");// TODO
			case "distinct":
				boolean useFirst = op.getAttribute("use-first", Boolean.class);
				boolean preserveSourceOrder = op.getAttribute("preserve-source-order", Boolean.class);
				ObservableExpression sortWith = op.getAttributeExpression("sort-with");
				if (sortWith != null) {
					if (preserveSourceOrder)
						System.err.println("WARNING: preserve-source-order cannot be used with sorted collections,"
							+ " as order is determined by the values themselves");
					Function<ModelSetInstance, Comparator<Object>> comparator = parseComparator(currentType, sortWith,
						op.getExpressoEnv());
					transformFn = (src, models) -> prevTransform.transform(src, models).distinctSorted(comparator.apply(models), useFirst);
				} else
					transformFn = (src, models) -> prevTransform.transform(src, models)
					.distinct(uo -> uo.useFirst(useFirst).preserveSourceOrder(preserveSourceOrder));
					break;
			case "sort":
				Function<ModelSetInstance, Comparator<Object>> comparator = parseComparator(currentType,
					op.getAttributeExpression("sort-with"), op.getExpressoEnv());
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
				if (op.getAttributeExpression("function") != null) {
					MethodFinder<Object, Object, Object, Object> finder = op.getAttributeExpression("function")
						.<Object, Object, Object, Object> findMethod(TypeTokens.get().of(Object.class), op.getExpressoEnv())//
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
		ValueContainer<ObservableCollection<?>, ObservableCollection<Object>> transformedCollection = source
			.map(ModelTypes.Collection.forType(currentType), (v, models) -> {
				ObservableCollection.CollectionDataFlow<Object, ?, Object> flow = finalTransform.transform(//
					v.flow(), models);
				if (active == null)
					return flow.collect();
				else if (active)
					return flow.collectActive(null);
				else
					return flow.collectPassive();
			});

		String typeName = transform.getAttributeText("type");
		if (typeName != null && !typeName.isEmpty()) {
			ModelInstanceType<ObservableCollection<?>, ObservableCollection<Object>> targetType = ModelTypes.Collection.forType(//
				(TypeToken<Object>) parseType(typeName, transform.getExpressoEnv()));
			ModelInstanceConverter<ObservableCollection<?>, ObservableCollection<?>> converter = ModelTypes.Collection.forType(currentType)
				.convert(targetType);
			if (converter == null)
				throw new QonfigInterpretationException("Cannot convert from " + currentType + " to " + targetType.getType(0));
			return transformedCollection.map(targetType, (coll, msi) -> (ObservableCollection<Object>) converter.convert(coll));
		} else
			return transformedCollection;
	}

	private ParsedTransformation mapTransformation(TypeToken<Object> currentType, ExpressoQIS op)
		throws QonfigInterpretationException {
		List<? extends ExpressoQIS> combinedValues = op.forChildren("combined-value");
		List<ValueContainer<?, ? extends SettableValue<?>>> combined = new ArrayList<>(combinedValues.size());
		for (ExpressoQIS combine : combinedValues) {
			String name = combine.getValueText();
			combined.add(op.getExpressoEnv().getModels().get(name, ModelTypes.Value));
		}
		TypeToken<?>[] argTypes = new TypeToken[combined.size()];
		for (int i = 0; i < argTypes.length; i++)
			argTypes[i] = combined.get(i).getType().getType(0);
		MethodFinder<Object, TransformationValues<?, ?>, ?, Object> map;
		map = op.getAttributeExpression("function").findMethod(TypeTokens.get().OBJECT, op.getExpressoEnv());
		map.withOption(argList(currentType).with(argTypes), (src, tv, __, args, models) -> {
			args[0] = src;
			for (int i = 0; i < combined.size(); i++)
				args[i + 1] = tv.get(combined.get(i).get(models));
		});
		Function<ModelSetInstance, BiFunction<Object, TransformationValues<?, ?>, Object>> mapFn = map.find2();
		TypeToken<Object> targetType = (TypeToken<Object>) map.getResultType();
		ExpressoQIS reverseEl = op.forChildren("reverse").peekFirst();
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
						ExpressoQIS replaceSource = reverseEl.asElement("replace-source");
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
							.getAttributeExpression("function")
							.findMethod(TypeTokens.get().VOID, reverseEl.getExpressoEnv());
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
							.getAttributeExpression("function")
							.findMethod(currentType, reverseEl.getExpressoEnv());
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

					ObservableExpression enabledEx = reverseEl.getAttributeExpression("enabled");
					if (enabledEx != null) {
						enabled = enabledEx
							.<TransformationValues<?, ?>, Void, Void, String> findMethod(TypeTokens.get().STRING,
								reverseEl.getExpressoEnv())//
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
					ObservableExpression acceptEx = reverseEl.getAttributeExpression("accept");
					if (acceptEx != null) {
						MethodFinder<Object, TransformationValues<?, ?>, ?, String> acceptFinder = acceptEx
							.findMethod(TypeTokens.get().STRING, reverseEl.getExpressoEnv());
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
					ObservableExpression createEx = reverseEl.getAttributeExpression("create");
					if (createEx != null) {
						create = createEx
							.<Object, TransformationValues<?, ?>, Boolean, Object> findMethod(currentType, reverseEl.getExpressoEnv())//
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
					ObservableExpression addAcceptEx = reverseEl.getAttributeExpression("add-accept");
					if (addAcceptEx != null) {
						MethodFinder<Object, TransformationValues<?, ?>, ?, String> addAcceptFinder = addAcceptEx
							.findMethod(TypeTokens.get().STRING, reverseEl.getExpressoEnv());
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
		ExpressoEnv env) throws QonfigInterpretationException {
		TypeToken<? super T> superType = TypeTokens.get().getSuperWildcard(type);
		Function<ModelSetInstance, BiFunction<T, T, Integer>> compareFn = expression
			.<T, T, Void, Integer> findMethod(TypeTokens.get().INT, env)//
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
}
