package org.observe.expresso;

import java.awt.Image;
import java.lang.reflect.Type;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.observe.Observable;
import org.observe.ObservableAction;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.SettableValue;
import org.observe.Transformation;
import org.observe.Transformation.MaybeReversibleTransformation;
import org.observe.Transformation.TransformationPrecursor;
import org.observe.assoc.ObservableMap;
import org.observe.assoc.ObservableMultiMap;
import org.observe.assoc.ObservableSortedMap;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollectionBuilder;
import org.observe.collect.ObservableSet;
import org.observe.collect.ObservableSortedCollection;
import org.observe.collect.ObservableSortedSet;
import org.observe.config.ObservableConfig;
import org.observe.config.ObservableValueSet;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ObservableModelSet.AbstractValueContainer;
import org.observe.expresso.ObservableModelSet.ExternalModelSet;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.RuntimeValuePlaceholder;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.observe.expresso.ObservableModelSet.ValueCreator;
import org.observe.expresso.ops.NameExpression;
import org.observe.util.TypeTokens;
import org.qommons.BiTuple;
import org.qommons.Causable;
import org.qommons.Identifiable.AbstractIdentifiable;
import org.qommons.StringUtils;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.TriFunction;
import org.qommons.Version;
import org.qommons.collect.BetterList;
import org.qommons.collect.FastFailLockingStrategy;
import org.qommons.collect.MutableCollectionElement.StdMsg;
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
import org.qommons.tree.BetterTreeList;

import com.google.common.reflect.TypeToken;

/** Qonfig Interpretation for the ExpressoBaseV0_1 API */
public class ExpressoBaseV0_1 implements QonfigInterpretation {
	/** Session key containing a model value's path */
	public static final String PATH_KEY = "model-path";
	/** Session key containing a model value's type, if known */
	public static final String VALUE_TYPE_KEY = "value-type";
	/** Sesison key containing a model value's key-type, if applicable and known */
	public static final String KEY_TYPE_KEY = "key-type";

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
		return "Expresso-Base";
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
		interpreter.createWith("expresso", Expresso.class, session -> {
			ClassView classView = wrap(session).interpretChildren("imports", ClassView.class).peekFirst();
			ObservableModelSet models = wrap(session).setModels(null, classView)//
				.interpretChildren("models", ObservableModelSet.class).peekFirst();
			return new Expresso(classView, models);
		});
		interpreter.modifyWith("with-local-model", Object.class, new QonfigValueModifier<Object>() {
			@Override
			public void prepareSession(CoreSession session) throws QonfigInterpretationException {
				ExpressoQIS exS = wrap(session);
				ExpressoQIS model = exS.forChildren("model").peekFirst();
				if (model != null) {
					ObservableModelSet.WrappedBuilder localModelBuilder = exS.getExpressoEnv().getModels().wrap();
					model.setModels(localModelBuilder, null).interpret(ObservableModelSet.class);
					ObservableModelSet.Wrapped localModel = localModelBuilder.build();
					exS.setModels(localModel, null);
					exS.put(ExpressoQIS.LOCAL_MODEL_KEY, localModel);
				}
			}

			@Override
			public Object modifyValue(Object value, CoreSession session) throws QonfigInterpretationException {
				return value;
			}
		});
		configureBaseModels(interpreter);
		configureExternalModels(interpreter);
		configureInternalModels(interpreter);
		interpreter.createWith("first-value", ValueCreator.class, session -> createFirstValue(wrap(session)));
		configureTransformation(interpreter);
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
					ValueContainer<Object, Object> defaultV = child.asElement("ext-model-value").getAttribute("default", childType, null);
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
				TypeToken<Object> type = TypeTokens.get().wrap((TypeToken<Object>) session.get(VALUE_TYPE_KEY, TypeToken.class));
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
				TypeToken<Object> keyType = TypeTokens.get().wrap((TypeToken<Object>) session.get(KEY_TYPE_KEY, TypeToken.class));
				TypeToken<Object> valueType = TypeTokens.get().wrap((TypeToken<Object>) session.get(VALUE_TYPE_KEY, TypeToken.class));
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
				TypeToken<Object> keyType = TypeTokens.get().wrap((TypeToken<Object>) session.get(KEY_TYPE_KEY, TypeToken.class));
				TypeToken<Object> valueType = TypeTokens.get().wrap((TypeToken<Object>) session.get(VALUE_TYPE_KEY, TypeToken.class));
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
					ValueCreator<?, ?> container = child.setModels(model, null).interpret(ValueCreator.class);
					model.withMaker(child.getAttributeText("model-element", "name"), container);
				} else if (child.fulfills(subModelRole)) {
					ObservableModelSet.Builder subModel = model.createSubModel(child.getAttributeText("named", "name"));
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
							class ConstantValue<T> extends AbstractIdentifiable implements SettableValue<T> {
								private final TypeToken<T> theType;
								private final T theValue;

								public ConstantValue(TypeToken<T> type, T value2) {
									theType = type;
									theValue = value2;
								}

								@Override
								public TypeToken<T> getType() {
									return theType;
								}

								@Override
								public long getStamp() {
									return 0;
								}

								@Override
								public T get() {
									return theValue;
								}

								@Override
								public Observable<ObservableValueEvent<T>> noInitChanges() {
									return Observable.empty();
								}

								@Override
								public boolean isLockSupported() {
									return true;
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
								public <V extends T> T set(V value2, Object cause)
									throws IllegalArgumentException, UnsupportedOperationException {
									throw new UnsupportedOperationException("Constant value");
								}

								@Override
								public <V extends T> String isAcceptable(V value2) {
									return "Constant value";
								}

								@Override
								public ObservableValue<String> isEnabled() {
									return ObservableValue.of("Constant value");
								}

								@Override
								protected Object createIdentity() {
									return session.get(PATH_KEY);
								}
							}
							return new ConstantValue<>((TypeToken<Object>) getType().getType(0), v);
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
				@SuppressWarnings("unused") // For debugging
				String valueS = valueX == null ? null : valueX.toString();
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
			return () -> {
				TypeToken<Object> type = (TypeToken<Object>) session.get(VALUE_TYPE_KEY);
				try {
					ValueContainer<ObservableAction<?>, ObservableAction<Object>> action = valueX.evaluate(
						ModelTypes.Action.forType(type == null ? (TypeToken<Object>) (TypeToken<?>) TypeTokens.get().WILDCARD : type),
						exS.getExpressoEnv());
					return action.wrapModels(exS::wrapLocal);
				} catch (QonfigInterpretationException e) {
					session.withError(e.getMessage(), e);
					return null;
				}
			};
		}).createWith("action-group", ValueCreator.class, session -> {
			ExpressoQIS exS = wrap(session);
			List<ValueCreator<ObservableAction<?>, ObservableAction<Object>>> actions = exS.interpretChildren("action", ValueCreator.class);
			return () -> {
				List<ValueContainer<ObservableAction<?>, ObservableAction<Object>>> actionVs = actions.stream()//
					.map(a -> a.createValue()).collect(Collectors.toList());
				return new ValueContainer<ObservableAction<?>, ObservableAction<Object>>() {
					@Override
					public ModelInstanceType<ObservableAction<?>, ObservableAction<Object>> getType() {
						return ModelTypes.Action.forType(Object.class);
					}

					@Override
					public ObservableAction<Object> get(ModelSetInstance models) {
						ModelSetInstance wrappedModels = exS.wrapLocal(models);
						List<ObservableAction<Object>> realActions = actionVs.stream().map(a -> a.get(wrappedModels))
							.collect(Collectors.toList());
						return ObservableAction.of(TypeTokens.get().OBJECT, cause -> {
							List<Object> result = new ArrayList<>(realActions.size());
							for (ObservableAction<Object> realAction : realActions)
								result.add(realAction.act(cause));
							return result;
						});
					}
				};
			};
		}).createWith("loop", ValueCreator.class, session -> {
			ExpressoQIS exS = wrap(session);
			ObservableExpression init = exS.getAttributeExpression("init");
			ObservableExpression before = exS.getAttributeExpression("before-while");
			ObservableExpression whileX = exS.getAttributeExpression("while");
			ObservableExpression beforeBody = exS.getAttributeExpression("before-body");
			ObservableExpression afterBody = exS.getAttributeExpression("after-body");
			ObservableExpression finallly = exS.getAttributeExpression("finally");
			List<ValueCreator<ObservableAction<?>, ObservableAction<?>>> exec = exS.interpretChildren("body", ValueCreator.class);
			return () -> {
				try {
					ValueContainer<ObservableAction<?>, ObservableAction<?>> initV = init == null ? null
						: init.evaluate(ModelTypes.Action.any(), exS.getExpressoEnv());
					ValueContainer<ObservableAction<?>, ObservableAction<?>> beforeV = before == null ? null
						: before.evaluate(ModelTypes.Action.any(), exS.getExpressoEnv());
					ValueContainer<SettableValue<?>, SettableValue<Boolean>> whileV = whileX
						.evaluate(ModelTypes.Value.forType(boolean.class), exS.getExpressoEnv());
					ValueContainer<ObservableAction<?>, ObservableAction<?>> beforeBodyV = beforeBody == null ? null
						: beforeBody.evaluate(ModelTypes.Action.any(), exS.getExpressoEnv());
					ValueContainer<ObservableAction<?>, ObservableAction<?>> afterBodyV = afterBody == null ? null
						: afterBody.evaluate(ModelTypes.Action.any(), exS.getExpressoEnv());
					ValueContainer<ObservableAction<?>, ObservableAction<?>> finallyV = finallly == null ? null
						: finallly.evaluate(ModelTypes.Action.any(), exS.getExpressoEnv());
					List<ValueContainer<ObservableAction<?>, ObservableAction<?>>> execVs = exec.stream().map(v -> v.createValue())
						.collect(Collectors.toList());
					return new ValueContainer<ObservableAction<?>, ObservableAction<Object>>() {
						@Override
						public ModelInstanceType<ObservableAction<?>, ObservableAction<Object>> getType() {
							return ModelTypes.Action.forType(Object.class);
						}

						@Override
						public ObservableAction<Object> get(ModelSetInstance models) {
							ModelSetInstance wrappedModels = exS.wrapLocal(models);
							return new LoopAction(//
								initV == null ? null : initV.get(wrappedModels), //
								beforeV == null ? null : beforeV.get(wrappedModels), //
								whileV.get(wrappedModels), //
								beforeBodyV == null ? null : beforeBodyV.get(wrappedModels), //
								execVs.stream().map(v -> v.get(wrappedModels)).collect(Collectors.toList()), //
								afterBodyV == null ? null : afterBodyV.get(wrappedModels), //
								finallyV == null ? null : finallyV.get(wrappedModels)//
							);
						}
					};
				} catch (QonfigInterpretationException e) {
					session.withError(e.getMessage(), e);
					return null;
				}
			};
		}).createWith("value-set", ValueCreator.class, session -> () -> {
			TypeToken<Object> type = TypeTokens.get().wrap((TypeToken<Object>) session.get(VALUE_TYPE_KEY, TypeToken.class));
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

	private static class LoopAction implements ObservableAction<Object> {
		private final ObservableAction<?> theInit;
		private final ObservableAction<?> theBeforeCondition;
		private final ObservableValue<Boolean> theCondition;
		private final ObservableAction<?> theBeforeBody;
		private final List<ObservableAction<?>> theBody;
		private final ObservableAction<?> theAfterBody;
		private final ObservableAction<?> theFinally;

		public LoopAction(ObservableAction<?> init, ObservableAction<?> before, ObservableValue<Boolean> condition,
			ObservableAction<?> beforeBody, List<ObservableAction<?>> body, ObservableAction<?> afterBody, ObservableAction<?> finallly) {
			theInit = init;
			theBeforeCondition = before;
			theCondition = condition;
			theBeforeBody = beforeBody;
			theBody = body;
			theAfterBody = afterBody;
			theFinally = finallly;
		}

		@Override
		public TypeToken<Object> getType() {
			return TypeTokens.get().OBJECT;
		}

		@Override
		public ObservableValue<String> isEnabled() {
			return SettableValue.ALWAYS_ENABLED;
		}

		@Override
		public Object act(Object cause) throws IllegalStateException {
			try (Causable.CausableInUse cause2 = Causable.cause(cause)) {
				if (theInit != null)
					theInit.act(cause2);

				try {
					Object result = null;
					// Prevent infinite loops. This structure isn't terribly efficient, so I think this should be sufficient.
					int count = 0;
					while (count < 1_000_000) {
						if (theBeforeCondition != null)
							theBeforeCondition.act(cause2);
						if (!Boolean.TRUE.equals(theCondition.get()))
							break;
						if (theBeforeBody != null)
							theBeforeBody.act(cause2);
						for (ObservableAction<?> body : theBody)
							result = body.act(cause2);
						if (theAfterBody != null)
							theAfterBody.act(cause2);
					}
					return result;
				} finally {
					if (theFinally != null)
						theFinally.act(cause2);
				}
			}
		}
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

	/**
	 * Parses a type specified in a Qonfig file
	 *
	 * @param text The text to parse the type from
	 * @param env The expresso environment to use to parse the type
	 * @return The parsed type
	 * @throws QonfigInterpretationException If the type cannot be parsed
	 */
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

	/**
	 * Parses a simple value
	 *
	 * @param <T> The type to parse
	 * @param env The environment to parse from
	 * @param type The type to parse
	 * @param expression The expression representing the value to parse
	 * @return The parsed and evaluated value
	 * @throws QonfigInterpretationException If the value could not be parsed or evaluated
	 */
	public static <T> ValueContainer<SettableValue<?>, SettableValue<T>> parseValue(ExpressoEnv env, TypeToken<T> type,
		ObservableExpression expression) throws QonfigInterpretationException {
		if (expression == null)
			return null;
		return expression.evaluate(
			type == null ? (ModelInstanceType<SettableValue<?>, SettableValue<T>>) (ModelInstanceType<?, ?>) ModelTypes.Value.any()
				: ModelTypes.Value.forType(type),
				env);
	}

	void configureTransformation(QonfigInterpreterCore.Builder interpreter) {
		interpreter.createWith("transform", ValueCreator.class, session -> createTransform(wrap(session)));
		interpreter//
		.createWith("no-init", ObservableTransform.class, session -> noInitObservable(wrap(session)))//
		.createWith("skip", ObservableTransform.class, session -> skipObservable(wrap(session)))//
		.createWith("take", ObservableTransform.class, session -> takeObservable(wrap(session)))//
		.createWith("take-until", ObservableTransform.class, session -> takeUntilObservable(wrap(session)))//
		.createWith("map-to", ObservableTransform.class, session -> mapObservableTo(wrap(session)))//
		.createWith("filter", ObservableTransform.class, session -> filterObservable(wrap(session)))//
		.createWith("filter-by-type", ObservableTransform.class, session -> filterObservableByType(wrap(session)))//
		;
		interpreter//
		.createWith("map-to", ValueTransform.class, session -> mapValueTo(wrap(session)))//
		.createWith("refresh", ValueTransform.class, session -> refreshValue(wrap(session)))//
		.createWith("unmodifiable", ValueTransform.class, session -> unmodifiableValue(wrap(session)))//
		.createWith("flatten", ValueTransform.class, session -> flattenValue(wrap(session)))//
		;
		interpreter//
		.createWith("map-to", CollectionTransform.class, session -> mapCollectionTo(wrap(session)))//
		.createWith("filter", CollectionTransform.class, session -> filterCollection(wrap(session)))//
		.createWith("filter-by-type", CollectionTransform.class, session -> filterCollectionByType(wrap(session)))//
		.createWith("reverse", CollectionTransform.class, session -> reverseCollection(wrap(session)))//
		.createWith("refresh", CollectionTransform.class, session -> refreshCollection(wrap(session)))//
		.createWith("refresh-each", CollectionTransform.class, session -> refreshCollectionEach(wrap(session)))//
		.createWith("distinct", CollectionTransform.class, session -> distinctCollection(wrap(session)))//
		.createWith("sort", CollectionTransform.class, session -> sortedCollection(wrap(session)))//
		.createWith("with-equivalence", CollectionTransform.class, session -> withCollectionEquivalence(wrap(session)))//
		.createWith("unmodifiable", CollectionTransform.class, session -> unmodifiableCollection(wrap(session)))//
		.createWith("filter-mod", CollectionTransform.class, session -> filterCollectionModification(wrap(session)))//
		.createWith("map-equivalent", CollectionTransform.class, session -> mapEquivalentCollectionTo(wrap(session)))//
		.createWith("flatten", CollectionTransform.class, session -> flattenCollection(wrap(session)))//
		.createWith("cross", CollectionTransform.class, session -> crossCollection(wrap(session)))//
		.createWith("where-contained", CollectionTransform.class, session -> whereCollectionContained(wrap(session)))//
		.createWith("group-by", CollectionTransform.class, session -> groupCollectionBy(wrap(session)))//
		.createWith("collect", CollectionTransform.class, session -> collectCollection(wrap(session)))//
		;

		interpreter.createWith("sort", ValueCreator.class, session -> createComparator(wrap(session)))//
		.createWith("sort-by", ValueCreator.class, session -> createComparator(wrap(session)));
		interpreter.delegateToType("map-reverse", "type", MapReverse.class)//
		.createWith("replace-source", MapReverse.class, session -> createSourceReplace(wrap(session)))//
		.createWith("modify-source", MapReverse.class, session -> createSourceModifier(wrap(session)));
	}

	private ValueCreator<?, ?> createTransform(ExpressoQIS session) throws QonfigInterpretationException {
		ObservableExpression sourceX = session.getAttributeExpression("source");
		return new ValueCreator<Object, Object>() {
			@Override
			public ValueContainer<Object, Object> createValue() {
				ValueContainer<SettableValue<?>, SettableValue<?>> source;
				try {
					source = sourceX.evaluate(ModelTypes.Value.any(), session.getExpressoEnv());
				} catch (QonfigInterpretationException e) {
					session.withError("Could not interpret source " + sourceX, e);
					return null;
				}
				Class<?> raw = TypeTokens.getRawType(source.getType().getType(0));
				ModelType<?> modelType = getModelType(raw);
				ValueContainer<Object, Object> firstStep;
				@SuppressWarnings("rawtypes")
				Class<? extends ObservableStructureTransform> transformType;
				if (modelType != null) {
					transformType = getTransformFor(modelType);
					if (transformType != null) {
						TypeToken<?>[] types = new TypeToken[raw.getTypeParameters().length];
						for (int t = 0; t < types.length; t++)
							types[t] = source.getType().getType(0).resolveType(raw.getTypeParameters()[t]);
						ModelInstanceType<Object, Object> mit = (ModelInstanceType<Object, Object>) modelType.forTypes(types);
						try {
							firstStep = source.getType().as(source, mit);
						} catch (QonfigInterpretationException e) {
							session.withError("Could not convert source " + sourceX + ", type " + source.getType() + " to type " + mit, e);
							return null;
						}
					} else {
						firstStep = (ValueContainer<Object, Object>) (ValueContainer<?, ?>) source;
						transformType = getTransformFor(ModelTypes.Value);
					}
				} else {
					firstStep=(ValueContainer<Object, Object>)(ValueContainer<?, ?>)source;
					transformType=getTransformFor(ModelTypes.Value);
				}
				ObservableStructureTransform<Object, Object, Object, Object> transform = ObservableStructureTransform
					.unity(firstStep.getType());
				ValueContainer<Object, Object> step = firstStep;
				List<ExpressoQIS> ops;
				try {
					ops = session.forChildren("op");
				} catch (QonfigInterpretationException e) {
					session.withError("Could not interpret transformation operations", e);
					return null;
				}
				for (ExpressoQIS op : ops) {
					transformType = getTransformFor(transform.getTargetType().getModelType());
					if (transformType == null) {
						session.withError("No transform supported for model type " + transform.getTargetType().getModelType());
						return null;
					} else if (!op.supportsInterpretation(transformType)) {
						session.withError("No transform supported for operation type " + op.getType().getName() + " for model type "
							+ transform.getTargetType().getModelType());
						return null;
					}
					ObservableStructureTransform<Object, Object, Object, Object> next;
					try {
						next = op.put(VALUE_TYPE_KEY, step.getType()).interpret(transformType);
					} catch (QonfigInterpretationException e) {
						session.withError("Could not interpret operation " + op.toString() + " as a transformation from "
							+ transform.getTargetType() + " via " + transformType.getName(), e);
						return null;
					}
					transform = next.after(transform);
				}
				ObservableStructureTransform<Object, Object, Object, Object> fTransform = transform;
				return new ValueContainer<Object, Object>() {
					@Override
					public ModelInstanceType<Object, Object> getType() {
						return fTransform.getTargetType();
					}

					@Override
					public Object get(ModelSetInstance models) {
						return fTransform.transform(//
							firstStep.get(models), models);
					}
				};
			}
		};
	}

	public interface ObservableStructureTransform<M1, MV1 extends M1, M2, MV2 extends M2> {
		ModelInstanceType<M2, MV2> getTargetType();

		MV2 transform(MV1 source, ModelSetInstance models);

		default <M0, MV0 extends M0> ObservableStructureTransform<M0, MV0, M2, MV2> after(
			ObservableStructureTransform<M0, MV0, M1, MV1> previous) {
			ObservableStructureTransform<M1, MV1, M2, MV2> next = this;
			return new ObservableStructureTransform<M0, MV0, M2, MV2>() {
				@Override
				public ModelInstanceType<M2, MV2> getTargetType() {
					return next.getTargetType();
				}

				@Override
				public MV2 transform(MV0 source, ModelSetInstance models) {
					MV1 intermediate = previous.transform(source, models);
					return next.transform(intermediate, models);
				}
			};
		}

		static <M, MV extends M> ObservableStructureTransform<M, MV, M, MV> unity(ModelInstanceType<M, MV> type){
			return new ObservableStructureTransform<M, MV, M, MV>() {
				@Override
				public ModelInstanceType<M, MV> getTargetType() {
					return type;
				}

				@Override
				public MV transform(MV source, ModelSetInstance models) {
					return source;
				}
			};
		}
	}

	protected <M> ModelType<M> getModelType(Class<M> modelType) {
		if (Observable.class.isAssignableFrom(modelType))
			return (ModelType<M>) ModelTypes.Event;
		else if (SettableValue.class.isAssignableFrom(modelType))
			return (ModelType<M>) ModelTypes.Value;
		else if (ObservableSortedSet.class.isAssignableFrom(modelType))
			return (ModelType<M>) ModelTypes.SortedSet;
		else if (ObservableSortedCollection.class.isAssignableFrom(modelType))
			return (ModelType<M>) ModelTypes.SortedCollection;
		else if (ObservableSet.class.isAssignableFrom(modelType))
			return (ModelType<M>) ModelTypes.Set;
		else if (ObservableCollection.class.isAssignableFrom(modelType))
			return (ModelType<M>) ModelTypes.Collection;
		else
			return null;
	}

	@SuppressWarnings("rawtypes")
	protected Class<? extends ObservableStructureTransform> getTransformFor(ModelType modelType) {
		if (modelType==ModelTypes.Event)
			return ObservableTransform.class;
		else if (modelType==ModelTypes.Value)
			return ValueTransform.class;
		else if (modelType == ModelTypes.Collection || modelType == ModelTypes.Set || modelType == ModelTypes.SortedCollection
			|| modelType == ModelTypes.SortedSet)
			return CollectionTransform.class;
		else
			return null;
	}

	public interface ObservableTransform<S, M, MV extends M> extends ObservableStructureTransform<Observable<?>, Observable<S>, M, MV> {
		static <S, T> ObservableTransform<S, Observable<?>, Observable<T>> of(TypeToken<T> type,
			BiFunction<Observable<S>, ModelSetInstance, Observable<T>> transform) {
			return new ObservableTransform<S, Observable<?>, Observable<T>>() {
				@Override
				public ModelInstanceType<Observable<?>, Observable<T>> getTargetType() {
					return ModelTypes.Event.forType(type);
				}

				@Override
				public Observable<T> transform(Observable<S> source, ModelSetInstance models) {
					return transform.apply(source, models);
				}
			};
		}

		static <S, M, MV extends M> ObservableTransform<S, M, MV> of(ModelInstanceType<M, MV> type,
			BiFunction<Observable<S>, ModelSetInstance, MV> transform) {
			return new ObservableTransform<S, M, MV>() {
				@Override
				public ModelInstanceType<M, MV> getTargetType() {
					return type;
				}

				@Override
				public MV transform(Observable<S> source, ModelSetInstance models) {
					return transform.apply(source, models);
				}
			};
		}
	}

	public interface ValueTransform<S, M, MV extends M> extends ObservableStructureTransform<SettableValue<?>, SettableValue<S>, M, MV> {
		static <S, T> ValueTransform<S, SettableValue<?>, SettableValue<T>> of(TypeToken<T> type,
			BiFunction<SettableValue<S>, ModelSetInstance, SettableValue<T>> transform) {
			return new ValueTransform<S, SettableValue<?>, SettableValue<T>>() {
				@Override
				public ModelInstanceType<SettableValue<?>, SettableValue<T>> getTargetType() {
					return ModelTypes.Value.forType(type);
				}

				@Override
				public SettableValue<T> transform(SettableValue<S> source, ModelSetInstance models) {
					return transform.apply(source, models);
				}
			};
		}

		static <S, M, MV extends M> ValueTransform<S, M, MV> of(ModelInstanceType<M, MV> type,
			BiFunction<SettableValue<S>, ModelSetInstance, MV> transform) {
			return new ValueTransform<S, M, MV>() {
				@Override
				public ModelInstanceType<M, MV> getTargetType() {
					return type;
				}

				@Override
				public MV transform(SettableValue<S> source, ModelSetInstance models) {
					return transform.apply(source, models);
				}
			};
		}
	}

	public interface CollectionTransform<S, M, MV extends M>
	extends ObservableStructureTransform<ObservableCollection<?>, ObservableCollection<S>, M, MV> {
		static <S, T> FlowCollectionTransform<S, T, ObservableCollection<?>, ObservableCollection<T>> of(
			ModelType.SingleTyped<? extends ObservableCollection<?>> modelType, TypeToken<T> type,
				BiFunction<CollectionDataFlow<?, ?, S>, ModelSetInstance, CollectionDataFlow<?, ?, T>> transform) {
			return new FlowCollectionTransform<S, T, ObservableCollection<?>, ObservableCollection<T>>() {
				@Override
				public ModelInstanceType<ObservableCollection<?>, ObservableCollection<T>> getTargetType() {
					return (ModelInstanceType<ObservableCollection<?>, ObservableCollection<T>>) modelType.forType(type);
				}

				@Override
				public CollectionDataFlow<?, ?, T> transformFlow(CollectionDataFlow<?, ?, S> source, ModelSetInstance models) {
					return transform.apply(source, models);
				}

				@Override
				public CollectionDataFlow<?, ?, T> transformToFlow(ObservableCollection<S> source, ModelSetInstance models) {
					return transform.apply(source.flow(), models);
				}
			};
		}

		static <S, M, MV extends M> CollectionTransform<S, M, MV> of(ModelInstanceType<M, MV> type,
			BiFunction<ObservableCollection<S>, ModelSetInstance, MV> transform) {
			return new CollectionTransform<S, M, MV>() {
				@Override
				public ModelInstanceType<M, MV> getTargetType() {
					return type;
				}

				@Override
				public MV transform(ObservableCollection<S> source, ModelSetInstance models) {
					return transform.apply(source, models);
				}
			};
		}
	}

	public interface FlowTransform<M1, MV1 extends M1, S, T, M extends ObservableCollection<?>, MV extends M>
	extends ObservableStructureTransform<M1, MV1, M, MV> {
		CollectionDataFlow<?, ?, T> transformFlow(CollectionDataFlow<?, ?, S> source, ModelSetInstance models);

		CollectionDataFlow<?, ?, T> transformToFlow(MV1 source, ModelSetInstance models);

		@Override
		default MV transform(MV1 source, ModelSetInstance models) {
			ObservableCollection.CollectionDataFlow<?, ?, T> flow = transformToFlow(source, models);
			return (MV) flow.collect();
		}

		@Override
		default <M0, MV0 extends M0> ObservableStructureTransform<M0, MV0, M, MV> after(
			ObservableStructureTransform<M0, MV0, M1, MV1> previous) {
			if (previous instanceof CollectionTransform) {
				FlowTransform<M0, MV0, Object, S, ObservableCollection<?>, ObservableCollection<?>> prevTCT;
				prevTCT = (FlowTransform<M0, MV0, Object, S, ObservableCollection<?>, ObservableCollection<?>>) previous;
				FlowTransform<M1, MV1, S, T, M, MV> next = this;
				return new FlowTransform<M0, MV0, Object, T, M, MV>() {
					@Override
					public ModelInstanceType<M, MV> getTargetType() {
						return next.getTargetType();
					}

					@Override
					public CollectionDataFlow<?, ?, T> transformToFlow(MV0 source, ModelSetInstance models) {
						CollectionDataFlow<?, ?, S> sourceFlow = prevTCT.transformToFlow(source, models);
						return next.transformFlow(sourceFlow, models);
					}

					@Override
					public CollectionDataFlow<?, ?, T> transformFlow(CollectionDataFlow<?, ?, Object> source, ModelSetInstance models) {
						CollectionDataFlow<?, ?, S> sourceFlow = prevTCT.transformFlow(source, models);
						return next.transformFlow(sourceFlow, models);
					}
				};
			} else
				return ObservableStructureTransform.super.after(previous);
		}
	}

	public interface FlowCollectionTransform<S, T, M extends ObservableCollection<?>, MV extends M>
	extends CollectionTransform<S, M, MV>, FlowTransform<ObservableCollection<?>, ObservableCollection<S>, S, T, M, MV> {
	}

	// Event transform

	private <T> ObservableTransform<T, Observable<?>, Observable<T>> noInitObservable(ExpressoQIS op) throws QonfigInterpretationException {
		ModelInstanceType.SingleTyped<Observable<?>, T, Observable<T>> sourceType;
		sourceType = (ModelInstanceType.SingleTyped<Observable<?>, T, Observable<T>>) op.get(VALUE_TYPE_KEY);
		return ObservableTransform.of(sourceType.getValueType(), (source, models) -> source.noInit());
	}

	private <T> ObservableTransform<T, Observable<?>, Observable<T>> skipObservable(ExpressoQIS op) throws QonfigInterpretationException {
		ModelInstanceType.SingleTyped<Observable<?>, T, Observable<T>> sourceType;
		sourceType = (ModelInstanceType.SingleTyped<Observable<?>, T, Observable<T>>) op.get(VALUE_TYPE_KEY);
		int times = Integer.parseInt(op.getAttributeText("times"));
		return ObservableTransform.of(sourceType.getValueType(), (source, models) -> source.skip(times));
	}

	private <T> ObservableTransform<T, Observable<?>, Observable<T>> takeObservable(ExpressoQIS op) throws QonfigInterpretationException {
		ModelInstanceType.SingleTyped<Observable<?>, T, Observable<T>> sourceType;
		sourceType = (ModelInstanceType.SingleTyped<Observable<?>, T, Observable<T>>) op.get(VALUE_TYPE_KEY);
		int times = Integer.parseInt(op.getAttributeText("times"));
		return ObservableTransform.of(sourceType.getValueType(), (source, models) -> source.take(times));
	}

	private <T> ObservableTransform<T, Observable<?>, Observable<T>> takeUntilObservable(ExpressoQIS op)
		throws QonfigInterpretationException {
		ModelInstanceType.SingleTyped<Observable<?>, T, Observable<T>> sourceType;
		sourceType = (ModelInstanceType.SingleTyped<Observable<?>, T, Observable<T>>) op.get(VALUE_TYPE_KEY);
		ValueContainer<Observable<?>, Observable<?>> until = op.getAttributeExpression("until").evaluate(ModelTypes.Event.any(),
			op.getExpressoEnv());
		return ObservableTransform.of(sourceType.getValueType(), (source, models) -> source.takeUntil(until.get(models)));
	}

	private <T> ObservableTransform<T, Observable<?>, Observable<T>> mapObservableTo(ExpressoQIS op) throws QonfigInterpretationException {
		throw new QonfigInterpretationException("Not yet implemented"); // TODO
	}

	private <T> ObservableTransform<T, Observable<?>, Observable<T>> filterObservable(ExpressoQIS op) throws QonfigInterpretationException {
		throw new QonfigInterpretationException("Not yet implemented"); // TODO
	}

	private <T> ObservableTransform<T, Observable<?>, Observable<T>> filterObservableByType(ExpressoQIS op)
		throws QonfigInterpretationException {
		throw new QonfigInterpretationException("Not yet implemented"); // TODO
	}

	// Value transform

	private <S, T> ValueTransform<S, SettableValue<?>, SettableValue<T>> mapValueTo(ExpressoQIS op) throws QonfigInterpretationException {
		ModelInstanceType.SingleTyped<SettableValue<?>, S, SettableValue<S>> sourceType = (ModelInstanceType.SingleTyped<SettableValue<?>, S, SettableValue<S>>) op
			.get(VALUE_TYPE_KEY);
		ParsedTransformation<S, T> ptx = mapTransformation(sourceType.getValueType(), op);
		if (ptx.isReversible()) {
			return ValueTransform.of(ptx.getTargetType(), (v, models) -> v.transformReversible(ptx.getTargetType(),
				tx -> (Transformation.ReversibleTransformation<S, T>) ptx.transform(tx, models)));
		} else {
			return ValueTransform.of(ptx.getTargetType(), (v, models) -> {
				ObservableValue<T> value = v.transform(ptx.getTargetType(), tx -> ptx.transform(tx, models));
				return SettableValue.asSettable(value, __ -> "No reverse configured for " + op.toString());
			});
		}
	}

	private <T> ValueTransform<T, SettableValue<?>, SettableValue<T>> refreshValue(ExpressoQIS op) throws QonfigInterpretationException {
		ModelInstanceType.SingleTyped<SettableValue<?>, T, SettableValue<T>> sourceType = (ModelInstanceType.SingleTyped<SettableValue<?>, T, SettableValue<T>>) op
			.get(VALUE_TYPE_KEY);
		Function<ModelSetInstance, Observable<?>> refresh = op.getAttributeExpression("on").evaluate(ModelTypes.Event.any(),
			op.getExpressoEnv());
		return ValueTransform.of(sourceType, (v, models) -> v.refresh(refresh.apply(models)));
	}

	private <T> ValueTransform<T, SettableValue<?>, SettableValue<T>> unmodifiableValue(ExpressoQIS op)
		throws QonfigInterpretationException {
		ModelInstanceType.SingleTyped<SettableValue<?>, T, SettableValue<T>> sourceType = (ModelInstanceType.SingleTyped<SettableValue<?>, T, SettableValue<T>>) op
			.get(VALUE_TYPE_KEY);
		boolean allowUpdates = op.getAttribute("allow-updates", Boolean.class);
		if (!allowUpdates) {
			return ValueTransform.of(sourceType, (v, models) -> v.disableWith(ObservableValue.of(StdMsg.UNSUPPORTED_OPERATION)));
		} else {
			return ValueTransform.of(sourceType, (v, models) -> {
				return v.filterAccept(input -> {
					if (v.get() == input)
						return null;
					else
						return StdMsg.ILLEGAL_ELEMENT;
				});
			});
		}
	}

	private <S, T> ValueTransform<S, ?, ?> flattenValue(ExpressoQIS op) throws QonfigInterpretationException {
		System.err.println("WARNING: Value.flatten is not fully implemented!!  Some options may be ignored.");
		ModelInstanceType.SingleTyped<SettableValue<?>, S, SettableValue<S>> sourceType;
		sourceType = (ModelInstanceType.SingleTyped<SettableValue<?>, S, SettableValue<S>>) op.get(VALUE_TYPE_KEY);
		TypeToken<T> resultType;
		Class<S> rawType = TypeTokens.getRawType(sourceType.getValueType());
		ExpressoQIS sort=op.forChildren("sort").peekFirst();
		if (SettableValue.class.isAssignableFrom(rawType)) {
			return ValueTransform.of((TypeToken<T>) sourceType.getValueType().resolveType(SettableValue.class.getTypeParameters()[0]),
				(v, models) -> SettableValue.flatten((ObservableValue<SettableValue<T>>) (ObservableValue<?>) v));
		} else if (ObservableValue.class.isAssignableFrom(rawType)) {
			return ValueTransform.of((TypeToken<T>) sourceType.getValueType().resolveType(ObservableValue.class.getTypeParameters()[0]),
				(v, models) -> SettableValue.flattenAsSettable((ObservableValue<ObservableValue<T>>) (ObservableValue<?>) v, () -> null));
		} else if (ObservableSortedSet.class.isAssignableFrom(rawType) && sort!=null) {
			ValueContainer<SettableValue<?>, SettableValue<Comparator<T>>> compare=sort.interpret(ValueContainer.class);
			resultType = (TypeToken<T>) sourceType.getValueType().resolveType(ObservableCollection.class.getTypeParameters()[0]);
			return ValueTransform.of(ModelTypes.SortedSet.forType(resultType), (value, models) -> ObservableSortedSet
				.flattenValue((ObservableValue<ObservableSortedSet<T>>) value, compare.get(models).get()));
		} else if (ObservableSortedCollection.class.isAssignableFrom(rawType) && sort!=null) {
			ValueContainer<SettableValue<?>, SettableValue<Comparator<T>>> compare=sort.interpret(ValueContainer.class);
			resultType = (TypeToken<T>) sourceType.getValueType().resolveType(ObservableCollection.class.getTypeParameters()[0]);
			return ValueTransform.of(ModelTypes.SortedCollection.forType(resultType), (value, models) -> ObservableSortedCollection
				.flattenValue((ObservableValue<ObservableSortedCollection<T>>) value, compare.get(models).get()));
		} else if(ObservableSet.class.isAssignableFrom(rawType)) {
			resultType = (TypeToken<T>) sourceType.getValueType().resolveType(ObservableCollection.class.getTypeParameters()[0]);
			return ValueTransform.of(ModelTypes.Set.forType(resultType), (value, models) -> ObservableSet
				.flattenValue((ObservableValue<ObservableSet<T>>) value));
		} else if(ObservableCollection.class.isAssignableFrom(rawType)) {
			resultType = (TypeToken<T>) sourceType.getValueType().resolveType(ObservableCollection.class.getTypeParameters()[0]);
			return ValueTransform.of(ModelTypes.Collection.forType(resultType), (value, models) -> ObservableCollection
				.flattenValue((ObservableValue<ObservableCollection<T>>) value));
		} else
			throw new QonfigInterpretationException("Cannot flatten value of type " + sourceType);
	}

	// Collection transform

	private <S, T> FlowCollectionTransform<S, T, ObservableCollection<?>, ObservableCollection<T>> mapCollectionTo(
		ExpressoQIS op) throws QonfigInterpretationException {
		ModelInstanceType.SingleTyped<? extends ObservableCollection<?>, S, ? extends ObservableCollection<S>> sourceType;
		sourceType = (ModelInstanceType.SingleTyped<? extends ObservableCollection<?>, S, ? extends ObservableCollection<S>>) op
			.get(VALUE_TYPE_KEY);

		ParsedTransformation<S, T> ptx = mapTransformation(sourceType.getValueType(), op);
		if (ptx.isReversible()) {
			if (sourceType.getModelType() == ModelTypes.SortedSet)
				return CollectionTransform.of(ModelTypes.SortedSet, ptx.getTargetType(),
					(c, models) -> ((ObservableCollection.DistinctSortedDataFlow<?, ?, S>) c).transformEquivalent(ptx.getTargetType(),
						tx -> (Transformation.ReversibleTransformation<S, T>) ptx.transform(tx, models)));
			else if (sourceType.getModelType() == ModelTypes.SortedCollection)
				return CollectionTransform.of(ModelTypes.SortedCollection, ptx.getTargetType(),
					(c, models) -> ((ObservableCollection.SortedDataFlow<?, ?, S>) c).transformEquivalent(ptx.getTargetType(),
						tx -> (Transformation.ReversibleTransformation<S, T>) ptx.transform(tx, models)));
			else if (sourceType.getModelType() == ModelTypes.Set)
				return CollectionTransform.of(ModelTypes.Set, ptx.getTargetType(),
					(c, models) -> ((ObservableCollection.DistinctDataFlow<?, ?, S>) c).transformEquivalent(ptx.getTargetType(),
						tx -> (Transformation.ReversibleTransformation<S, T>) ptx.transform(tx, models)));
			else
				return CollectionTransform.of(ModelTypes.Collection, ptx.getTargetType(), (c, models) -> c.transform(ptx.getTargetType(),
					tx -> (Transformation.ReversibleTransformation<S, T>) ptx.transform(tx, models)));
		} else {
			return CollectionTransform.of(ModelTypes.Collection, ptx.getTargetType(),
				(f, models) -> f.transform(ptx.getTargetType(), tx -> ptx.transform(tx, models)));
		}
	}

	private <T> FlowCollectionTransform<T, T, ObservableCollection<?>, ObservableCollection<T>> filterCollection(
		ExpressoQIS op) throws QonfigInterpretationException {
		throw new QonfigInterpretationException("Not yet implemented"); // TODO
	}

	private <T> FlowCollectionTransform<T, T, ObservableCollection<?>, ObservableCollection<T>> filterCollectionByType(
		ExpressoQIS op) throws QonfigInterpretationException {
		throw new QonfigInterpretationException("Not yet implemented"); // TODO
	}

	private <T> FlowCollectionTransform<T, T, ObservableCollection<?>, ObservableCollection<T>> reverseCollection(
		ExpressoQIS op) throws QonfigInterpretationException {
		ModelInstanceType.SingleTyped<? extends ObservableCollection<?>, T, ? extends ObservableCollection<T>> sourceType;
		sourceType = (ModelInstanceType.SingleTyped<? extends ObservableCollection<?>, T, ? extends ObservableCollection<T>>) op
			.get(VALUE_TYPE_KEY);

		return new FlowCollectionTransform<T, T, ObservableCollection<?>, ObservableCollection<T>>() {
			@Override
			public ModelInstanceType<ObservableCollection<?>, ObservableCollection<T>> getTargetType() {
				return (ModelInstanceType<ObservableCollection<?>, ObservableCollection<T>>) sourceType;
			}

			@Override
			public CollectionDataFlow<?, ?, T> transformFlow(CollectionDataFlow<?, ?, T> source, ModelSetInstance models) {
				return source.reverse();
			}

			@Override
			public CollectionDataFlow<?, ?, T> transformToFlow(ObservableCollection<T> source, ModelSetInstance models) {
				return source.flow().reverse();
			}

			@Override
			public ObservableCollection<T> transform(ObservableCollection<T> source, ModelSetInstance models) {
				return source.reverse();
			}
		};
	}

	private <T> FlowCollectionTransform<T, T, ObservableCollection<?>, ObservableCollection<T>> refreshCollection(
		ExpressoQIS op) throws QonfigInterpretationException {
		ModelInstanceType.SingleTyped<? extends ObservableCollection<?>, T, ? extends ObservableCollection<T>> sourceType;
		sourceType = (ModelInstanceType.SingleTyped<? extends ObservableCollection<?>, T, ? extends ObservableCollection<T>>) op
			.get(VALUE_TYPE_KEY);

		ValueContainer<Observable<?>, Observable<?>> refreshX = op.getAttributeExpression("on").evaluate(ModelTypes.Event.any(),
			op.getExpressoEnv());
		return CollectionTransform.of(sourceType.getModelType(), sourceType.getValueType(), (f, models) -> {
			Observable<?> refresh = refreshX.get(models);
			return f.refresh(refresh);
		});
	}

	private <T> FlowCollectionTransform<T, T, ObservableCollection<?>, ObservableCollection<T>> refreshCollectionEach(
		ExpressoQIS op) throws QonfigInterpretationException {
		ModelInstanceType.SingleTyped<? extends ObservableCollection<?>, T, ? extends ObservableCollection<T>> sourceType;
		sourceType = (ModelInstanceType.SingleTyped<? extends ObservableCollection<?>, T, ? extends ObservableCollection<T>>) op
			.get(VALUE_TYPE_KEY);

		String sourceAs = op.getAttributeText("source-as");
		ObservableModelSet.WrappedBuilder refreshModelBuilder = op.getExpressoEnv().getModels().wrap();
		RuntimeValuePlaceholder<SettableValue<?>, SettableValue<T>> sourcePlaceholder = refreshModelBuilder.withRuntimeValue(sourceAs,
			ModelTypes.Value.forType(sourceType.getValueType()));
		ObservableModelSet.Wrapped refreshModel = refreshModelBuilder.build();
		ValueContainer<SettableValue<?>, SettableValue<Observable<?>>> refreshX = op.getAttributeExpression("on").evaluate(
			ModelTypes.Value.forType(TypeTokens.get().keyFor(Observable.class).wildCard()), op.getExpressoEnv().with(refreshModel, null));
		return CollectionTransform.of(sourceType.getModelType(), sourceType.getValueType(), (f, models) -> {
			SettableValue<T> sourceValue = SettableValue.build(sourceType.getValueType()).build();
			ModelSetInstance refreshModelInstance = refreshModel.wrap(models)//
				.with(sourcePlaceholder, sourceValue)//
				.build();
			SettableValue<Observable<?>> refresh = refreshX.get(refreshModelInstance);
			return f.refreshEach(v -> {
				sourceValue.set(v, null);
				return refresh.get();
			});
		});
	}

	private <T> FlowCollectionTransform<T, T, ObservableCollection<?>, ObservableCollection<T>> distinctCollection(
		ExpressoQIS op) throws QonfigInterpretationException {
		ModelInstanceType.SingleTyped<? extends ObservableCollection<?>, T, ? extends ObservableCollection<T>> sourceType;
		sourceType = (ModelInstanceType.SingleTyped<? extends ObservableCollection<?>, T, ? extends ObservableCollection<T>>) op
			.get(VALUE_TYPE_KEY);

		boolean useFirst = op.getAttribute("use-first", Boolean.class);
		boolean preserveSourceOrder = op.getAttribute("preserve-source-order", Boolean.class);
		ExpressoQIS sort = op.forChildren("sort").peekFirst();
		ValueContainer<SettableValue<?>, SettableValue<Comparator<T>>> compare = sort == null ? null
			: sort.put(VALUE_TYPE_KEY, sourceType.getValueType()).interpret(ValueContainer.class);
		if (compare != null) {
			return CollectionTransform.of(ModelTypes.SortedSet, sourceType.getValueType(), (f, models) -> {
				Comparator<T> comparator = compare.get(models).get();
				return f.distinctSorted(comparator, useFirst);
			});
		} else {
			return CollectionTransform.of(ModelTypes.Set, sourceType.getValueType(),
				(f, models) -> f.distinct(opts -> opts.useFirst(useFirst).preserveSourceOrder(preserveSourceOrder)));
		}
	}

	private <T> FlowCollectionTransform<T, T, ObservableCollection<?>, ObservableCollection<T>> sortedCollection(
		ExpressoQIS op) throws QonfigInterpretationException {
		ModelInstanceType.SingleTyped<? extends ObservableCollection<?>, T, ? extends ObservableCollection<T>> sourceType;
		sourceType = (ModelInstanceType.SingleTyped<? extends ObservableCollection<?>, T, ? extends ObservableCollection<T>>) op
			.get(VALUE_TYPE_KEY);

		ValueCreator<SettableValue<?>, SettableValue<Comparator<T>>> compare = op.put(VALUE_TYPE_KEY, sourceType.getValueType())
			.interpret(ValueCreator.class);
		return CollectionTransform.of(ModelTypes.SortedCollection, sourceType.getValueType(), (f, models) -> {
			Comparator<T> comparator = compare.createValue().get(models).get();
			return f.sorted(comparator);
		});
	}

	private <T> FlowCollectionTransform<T, T, ObservableCollection<?>, ObservableCollection<T>> withCollectionEquivalence(
		ExpressoQIS op) throws QonfigInterpretationException {
		throw new QonfigInterpretationException("Not yet implemented"); // TODO
	}

	private <T> FlowCollectionTransform<T, T, ObservableCollection<?>, ObservableCollection<T>> unmodifiableCollection(
		ExpressoQIS op) throws QonfigInterpretationException {
		ModelInstanceType.SingleTyped<? extends ObservableCollection<?>, T, ? extends ObservableCollection<T>> sourceType;
		sourceType = (ModelInstanceType.SingleTyped<? extends ObservableCollection<?>, T, ? extends ObservableCollection<T>>) op
			.get(VALUE_TYPE_KEY);

		boolean allowUpdates = op.getAttribute("allow-updates", boolean.class);
		return CollectionTransform.of(ModelTypes.Collection, sourceType.getValueType(), (f, models) -> f.unmodifiable(allowUpdates));
	}

	private <T> FlowCollectionTransform<T, T, ObservableCollection<?>, ObservableCollection<T>> filterCollectionModification(
		ExpressoQIS op) throws QonfigInterpretationException {
		throw new QonfigInterpretationException("Not yet implemented"); // TODO
	}

	private <S, T> FlowCollectionTransform<S, T, ObservableCollection<?>, ObservableCollection<T>> mapEquivalentCollectionTo(
		ExpressoQIS op) throws QonfigInterpretationException {
		throw new QonfigInterpretationException("Not yet implemented"); // TODO
	}

	private <S, T> FlowCollectionTransform<S, T, ObservableCollection<?>, ObservableCollection<T>> flattenCollection(
		ExpressoQIS op) throws QonfigInterpretationException {
		ModelInstanceType.SingleTyped<? extends ObservableCollection<?>, T, ? extends ObservableCollection<T>> sourceType;
		sourceType = (ModelInstanceType.SingleTyped<? extends ObservableCollection<?>, T, ? extends ObservableCollection<T>>) op
			.get(VALUE_TYPE_KEY);

		Class<?> raw = TypeTokens.getRawType(sourceType.getValueType());
		if (ObservableValue.class.isAssignableFrom(raw)) {
			TypeToken<T> resultType = (TypeToken<T>) sourceType.getValueType().resolveType(ObservableValue.class.getTypeParameters()[0]);
			return CollectionTransform.of(ModelTypes.Collection, resultType, //
				(flow, models) -> flow.flattenValues(resultType, v -> (ObservableValue<? extends T>) v));
		} else if (ObservableCollection.class.isAssignableFrom(raw)) {
			System.err.println("WARNING: Collection flatten is not fully implemented.  Many options are unsupported.");
			// TODO Use sort, map options, reverse
			TypeToken<T> resultType = (TypeToken<T>) sourceType.getValueType()
				.resolveType(ObservableCollection.class.getTypeParameters()[0]);
			return CollectionTransform.of(ModelTypes.Collection, resultType, //
				(flow, models) -> flow.flatMap(resultType, v -> ((ObservableCollection<? extends T>) v).flow()));
		} else if (CollectionDataFlow.class.isAssignableFrom(raw)) {
			System.err.println("WARNING: Collection flatten is not fully implemented.  Many options are unsupported.");
			// TODO Use sort, map options, reverse
			TypeToken<T> resultType = (TypeToken<T>) sourceType.getValueType().resolveType(CollectionDataFlow.class.getTypeParameters()[2]);
			return CollectionTransform.of(ModelTypes.Collection, resultType, //
				(flow, models) -> flow.flatMap(resultType, v -> (CollectionDataFlow<?, ?, ? extends T>) v));
		} else
			throw new QonfigInterpretationException("Cannot flatten a collection of type " + sourceType.getValueType());
	}

	private <T> FlowCollectionTransform<T, T, ObservableCollection<?>, ObservableCollection<T>> crossCollection(
		ExpressoQIS op) throws QonfigInterpretationException {
		throw new QonfigInterpretationException("Not yet implemented"); // TODO
	}

	private <T> FlowCollectionTransform<T, T, ObservableCollection<?>, ObservableCollection<T>> whereCollectionContained(
		ExpressoQIS op) throws QonfigInterpretationException {
		ModelInstanceType.SingleTyped<? extends ObservableCollection<?>, T, ? extends ObservableCollection<T>> sourceType;
		sourceType = (ModelInstanceType.SingleTyped<? extends ObservableCollection<?>, T, ? extends ObservableCollection<T>>) op
			.get(VALUE_TYPE_KEY);

		ValueContainer<ObservableCollection<?>, ObservableCollection<?>> filter = op.getAttributeExpression("filter")//
			.evaluate(ModelTypes.Collection.any(), op.getExpressoEnv());
		boolean inclusive = op.getAttribute("inclusive", boolean.class);
		return CollectionTransform.of(sourceType.getModelType(), sourceType.getValueType(), (flow, models) -> flow.whereContained(//
			filter.get(models).flow(), inclusive));
	}

	private <K, V> CollectionTransform<V, ObservableMultiMap<?, ?>, ObservableMultiMap<K, V>> groupCollectionBy(ExpressoQIS op)
		throws QonfigInterpretationException {
		throw new QonfigInterpretationException("Not yet implemented"); // TODO
	}

	private <T> FlowCollectionTransform<T, T, ObservableCollection<?>, ObservableCollection<T>> collectCollection(ExpressoQIS op)
		throws QonfigInterpretationException {
		ModelInstanceType.SingleTyped<? extends ObservableCollection<?>, T, ? extends ObservableCollection<T>> sourceType;
		sourceType = (ModelInstanceType.SingleTyped<? extends ObservableCollection<?>, T, ? extends ObservableCollection<T>>) op
			.get(VALUE_TYPE_KEY);

		Boolean active = op.getAttribute("active", Boolean.class);
		return CollectionTransform.of(sourceType.getModelType(), sourceType.getValueType(), (flow, models) -> {
			boolean reallyActive;
			if (active != null)
				reallyActive = active.booleanValue();
			else
				reallyActive = !flow.prefersPassive();
			if (reallyActive)
				return flow.collectActive(models.getUntil()).flow();
			else
				return flow.collectPassive().flow();
		});
	}

	private <S, T> ParsedTransformation<S, T> mapTransformation(TypeToken<S> currentType, ExpressoQIS op)
		throws QonfigInterpretationException {
		List<? extends ExpressoQIS> combinedValues = op.forChildren("combined-value");
		ExpressoQIS map = op.forChildren("map").getFirst();
		ObservableModelSet.WrappedBuilder mapModelsBuilder = map.getExpressoEnv().getModels().wrap();
		Map<String, ValueContainer<SettableValue<?>, SettableValue<Object>>> combined = new LinkedHashMap<>(combinedValues.size() * 2);
		Map<String, ObservableModelSet.RuntimeValuePlaceholder<SettableValue<?>, SettableValue<Object>>> combinedPlaceholders = new LinkedHashMap<>();
		for (ExpressoQIS combine : combinedValues) {
			String name = combine.getAttributeText("name");
			op.getExpressoEnv().getModels().getNameChecker().checkName(name);
			ObservableExpression value = combine.getValueExpression();
			ValueContainer<SettableValue<?>, SettableValue<Object>> combineV = value.evaluate(
				(ModelInstanceType<SettableValue<?>, SettableValue<Object>>) (ModelInstanceType<?, ?>) ModelTypes.Value.any(),
				combine.getExpressoEnv());
			combined.put(name, combineV);
			combinedPlaceholders.put(name, mapModelsBuilder.withRuntimeValue(name, combineV.getType()));
		}
		String sourceName = op.getAttributeText("source-as");
		ObservableModelSet.RuntimeValuePlaceholder<SettableValue<?>, SettableValue<S>> sourcePlaceholder = mapModelsBuilder
			.withRuntimeValue(sourceName, ModelTypes.Value.forType(currentType));
		ObservableModelSet.Wrapped mapModels = mapModelsBuilder.build();
		map.setModels(mapModels, null);
		ObservableExpression mapEx = map.getValueExpression();
		String targetTypeName = op.getAttributeText("type");
		TypeToken<T> targetType;
		ValueContainer<SettableValue<?>, SettableValue<T>> mapped;
		if (targetTypeName != null) {
			targetType = (TypeToken<T>) parseType(targetTypeName, map.getExpressoEnv());
			mapped = mapEx.evaluate(ModelTypes.Value.forType(targetType), map.getExpressoEnv());
		} else {
			mapped = mapEx.evaluate(
				(ModelInstanceType<SettableValue<?>, SettableValue<T>>) (ModelInstanceType<?, ?>) ModelTypes.Value.any(),
				map.getExpressoEnv());
			targetType = (TypeToken<T>) mapped.getType().getType(0);
		}

		boolean cached = op.getAttribute("cache", boolean.class);
		boolean reEval = op.getAttribute("re-eval-on-update", boolean.class);
		boolean fireIfUnchanged = op.getAttribute("fire-if-unchanged", boolean.class);
		boolean nullToNull = op.getAttribute("null-to-null", boolean.class);
		boolean manyToOne = op.getAttribute("many-to-one", boolean.class);
		boolean oneToMany = op.getAttribute("one-to-many", boolean.class);

		MapReverse<S, T> mapReverse;
		ExpressoQIS reverse = op.forChildren("reverse").peekFirst();
		if (reverse != null) {
			Map<String, TypeToken<Object>> combinedTypes = new LinkedHashMap<>();
			for (Map.Entry<String, ValueContainer<SettableValue<?>, SettableValue<Object>>> combinedV : combined.entrySet()) {
				combinedTypes.put(combinedV.getKey(), (TypeToken<Object>) combinedV.getValue().getType().getType(0));
			}
			reverse.put(REVERSE_CONFIG, new MapReverseConfig<>(sourceName, currentType, targetType, combinedTypes));
			mapReverse = reverse.interpret(MapReverse.class);
		} else
			mapReverse = null;

		return new ParsedTransformation<S, T>() {
			@Override
			public TypeToken<T> getTargetType() {
				return targetType;
			}

			@Override
			public boolean isReversible() {
				return mapReverse != null;
			}

			@Override
			public Transformation<S, T> transform(Transformation.TransformationPrecursor<S, T, ?> precursor,
				ModelSetInstance modelSet) {
				SettableValue<S> sourceV = SettableValue.build(currentType).build();
				Map<String, SettableValue<Object>> combinedSourceVs = new LinkedHashMap<>();
				Map<String, SettableValue<Object>> combinedTransformVs = new LinkedHashMap<>();
				Transformation.TransformationBuilder<S, T, ?> builder = precursor//
					.cache(cached).reEvalOnUpdate(reEval).fireIfUnchanged(fireIfUnchanged).nullToNull(nullToNull).manyToOne(manyToOne)
					.oneToMany(oneToMany);
				ObservableModelSet.WrappedInstanceBuilder mapMSIBuilder = mapModels.wrap(modelSet)//
					.with(sourcePlaceholder, sourceV.disableWith(ObservableValue.of(StdMsg.UNSUPPORTED_OPERATION)));
				for (Map.Entry<String, ValueContainer<SettableValue<?>, SettableValue<Object>>> cv : combined.entrySet()) {
					SettableValue<Object> v = cv.getValue().get(modelSet);
					combinedSourceVs.put(cv.getKey(), v);
					builder = builder.combineWith(v);
					SettableValue<Object> cv2 = SettableValue.build((TypeToken<Object>) cv.getValue().getType().getType(0)).build();
					combinedTransformVs.put(cv.getKey(), cv2);
					mapMSIBuilder.with(combinedPlaceholders.get(cv.getKey()), cv2);
				}
				ModelSetInstance mapMSI = mapMSIBuilder.build();
				SettableValue<T> mappedV = mapped.get(mapMSI);
				BiFunction<S, Transformation.TransformationValues<? extends S, ? extends T>, T> mapFn = (source, tvs) -> {
					sourceV.set(source, null);
					for (Map.Entry<String, SettableValue<Object>> cv : combinedTransformVs.entrySet())
						cv.getValue().set(tvs.get(combinedSourceVs.get(cv.getKey())), null);
					return mappedV.get();
				};
				Transformation<S, T> transformation = builder.build(mapFn);
				if (mapReverse != null)
					transformation = ((MaybeReversibleTransformation<S, T>) transformation).withReverse(//
						mapReverse.reverse(transformation, modelSet));
				return transformation;
			}
		};
	}

	/**
	 * Provided to a {@link MapReverse} implementation via the {@link ExpressoBaseV0_1#REVERSE_CONFIG} key
	 *
	 * @param <S> The type of the source value
	 * @param <T> The type of the transformed value
	 */
	public static class MapReverseConfig<S, T> {
		/** The name for the source value in expressions */
		public final String sourceName;
		/** The type of the source value */
		public final TypeToken<S> sourceType;
		/** The type of the transformed value */
		public final TypeToken<T> targetType;
		/** The name and type of each value combined */
		public final Map<String, TypeToken<Object>> combinedValues;

		/**
		 * @param sourceName The name for the source value in expressions
		 * @param sourceType The type of the source value
		 * @param targetType The type of the transformed value
		 * @param combinedValues The name and type of each value combined
		 */
		public MapReverseConfig(String sourceName, TypeToken<S> sourceType, TypeToken<T> targetType,
			Map<String, TypeToken<Object>> combinedValues) {
			this.sourceName = sourceName;
			this.sourceType = sourceType;
			this.targetType = targetType;
			this.combinedValues = combinedValues;
		}
	}

	/** The session key for the {@link MapReverseConfig} provided to the map-reverse session */
	public static final String REVERSE_CONFIG = "reverse-config";

	/**
	 * Interface to provide via {@link org.qommons.config.QonfigInterpreterCore.Builder#createWith(String, Class, QonfigValueCreator)} to
	 * support a new map-reverse type
	 *
	 * @param <S> The type of the source value
	 * @param <T> The type of the transformed value
	 */
	public interface MapReverse<S, T> {
		/**
		 * @param transformation The transformation to reverse
		 * @param modelSet The model instance
		 * @return The transformation reverse
		 */
		Transformation.TransformReverse<S, T> reverse(Transformation<S, T> transformation, ModelSetInstance modelSet);
	}

	<S, T> MapReverse<S, T> createSourceReplace(ExpressoQIS reverse) throws QonfigInterpretationException {
		MapReverseConfig<S, T> reverseConfig = (MapReverseConfig<S, T>) reverse.get(REVERSE_CONFIG);
		ObservableExpression reverseX = reverse.getValueExpression();
		ObservableExpression enabled = reverse.getAttributeExpression("enabled");
		ObservableExpression accept = reverse.getAttributeExpression("accept");
		ObservableExpression add = reverse.getAttributeExpression("add");
		ObservableExpression addAccept = reverse.getAttributeExpression("add-accept");
		String targetName = reverse.getAttributeText("target-as");
		reverse.getExpressoEnv().getModels().getNameChecker().checkName(targetName);

		boolean stateful = refersToSource(reverseX, reverseConfig.sourceName)//
			|| (enabled != null && refersToSource(enabled, reverseConfig.sourceName))
			|| (accept != null && refersToSource(accept, reverseConfig.sourceName));
		boolean inexact = reverse.getAttribute("inexact", boolean.class);

		ObservableModelSet.WrappedBuilder reverseModelBuilder = reverse.getExpressoEnv().getModels().wrap();
		ObservableModelSet.RuntimeValuePlaceholder<SettableValue<?>, SettableValue<S>> sourcePlaceholder = reverseModelBuilder
			.withRuntimeValue(reverseConfig.sourceName, ModelTypes.Value.forType(reverseConfig.sourceType));
		ObservableModelSet.RuntimeValuePlaceholder<SettableValue<?>, SettableValue<T>> targetPlaceholder = reverseModelBuilder
			.withRuntimeValue(targetName, ModelTypes.Value.forType(reverseConfig.targetType));
		Map<String, ObservableModelSet.RuntimeValuePlaceholder<SettableValue<?>, SettableValue<Object>>> combinedValues = new LinkedHashMap<>();
		for (Map.Entry<String, TypeToken<Object>> combined : reverseConfig.combinedValues.entrySet())
			combinedValues.put(combined.getKey(),
				reverseModelBuilder.withRuntimeValue(combined.getKey(), ModelTypes.Value.forType(combined.getValue())));

		ObservableModelSet.Wrapped reverseModels = reverseModelBuilder.build();
		reverse.setModels(reverseModels, null);

		ValueContainer<SettableValue<?>, SettableValue<S>> reversedV = reverseX.evaluate(ModelTypes.Value.forType(reverseConfig.sourceType),
			reverse.getExpressoEnv());
		ValueContainer<SettableValue<?>, SettableValue<String>> enabledV = enabled == null ? null
			: enabled.evaluate(ModelTypes.Value.forType(String.class), reverse.getExpressoEnv());
		ValueContainer<SettableValue<?>, SettableValue<String>> acceptV = accept == null ? null
			: accept.evaluate(ModelTypes.Value.forType(String.class), reverse.getExpressoEnv());
		ValueContainer<SettableValue<?>, SettableValue<S>> addV = add == null ? null
			: add.evaluate(ModelTypes.Value.forType(reverseConfig.sourceType), reverse.getExpressoEnv());
		ValueContainer<SettableValue<?>, SettableValue<String>> addAcceptV = addAccept == null ? null
			: addAccept.evaluate(ModelTypes.Value.forType(String.class), reverse.getExpressoEnv());

		return (transformation, modelSet) -> {
			SettableValue<S> sourceV = SettableValue.build(reverseConfig.sourceType).build();
			SettableValue<T> targetV = SettableValue.build(reverseConfig.targetType).build();
			ObservableModelSet.WrappedInstanceBuilder reverseMSIBuilder = reverseModels.wrap(modelSet)//
				.with(sourcePlaceholder, sourceV.disableWith(ObservableValue.of(StdMsg.UNSUPPORTED_OPERATION)))//
				.with(targetPlaceholder, targetV.disableWith(ObservableValue.of(StdMsg.UNSUPPORTED_OPERATION)));
			Map<String, SettableValue<Object>> combinedVs = new LinkedHashMap<>();
			for (Map.Entry<String, ObservableModelSet.RuntimeValuePlaceholder<SettableValue<?>, SettableValue<Object>>> cv : combinedValues
				.entrySet()) {
				SettableValue<Object> value = SettableValue.build((TypeToken<Object>) cv.getValue().getType().getType(0)).build();
				combinedVs.put(cv.getKey(), value);
				reverseMSIBuilder.with(cv.getValue(), value.disableWith(ObservableValue.of(StdMsg.UNSUPPORTED_OPERATION)));
			}
			ModelSetInstance reverseMSI = reverseMSIBuilder.build();

			SettableValue<S> reversedEvld = reversedV.get(reverseMSI);
			SettableValue<String> enabledEvld = enabledV == null ? null : enabledV.get(reverseMSI);
			SettableValue<String> acceptEvld = acceptV == null ? null : acceptV.get(reverseMSI);
			SettableValue<S> addEvld = addV == null ? null : addV.get(reverseMSI);
			SettableValue<String> addAcceptEvld = addAcceptV == null ? null : addAcceptV.get(reverseMSI);

			BiFunction<T, Transformation.TransformationValues<? extends S, ? extends T>, S> reverseFn;
			Function<Transformation.TransformationValues<? extends S, ? extends T>, String> enabledFn;
			BiFunction<T, Transformation.TransformationValues<? extends S, ? extends T>, String> acceptFn;
			TriFunction<T, Transformation.TransformationValues<? extends S, ? extends T>, Boolean, S> addFn;
			BiFunction<T, Transformation.TransformationValues<? extends S, ? extends T>, String> addAcceptFn;
			reverseFn = (target, tvs) -> {
				if (stateful)
					sourceV.set(tvs.getCurrentSource(), null);
				targetV.set(target, null);
				int cvIdx = 0;
				for (Map.Entry<String, ObservableModelSet.RuntimeValuePlaceholder<SettableValue<?>, SettableValue<Object>>> cv : combinedValues
					.entrySet()) {
					combinedVs.get(cv.getKey()).set(tvs.get(transformation.getArg(cvIdx)), null);
					cvIdx++;
				}
				return reversedEvld.get();
			};
			enabledFn = enabledEvld == null ? null : tvs -> {
				if (stateful)
					sourceV.set(tvs.getCurrentSource(), null);
				targetV.set(null, null);
				int cvIdx = 0;
				for (Map.Entry<String, ObservableModelSet.RuntimeValuePlaceholder<SettableValue<?>, SettableValue<Object>>> cv : combinedValues
					.entrySet()) {
					combinedVs.get(cv.getKey()).set(tvs.get(transformation.getArg(cvIdx)), null);
					cvIdx++;
				}
				return acceptEvld.get();
			};
			acceptFn = enabledEvld == null ? null : (target, tvs) -> {
				if (stateful)
					sourceV.set(tvs.getCurrentSource(), null);
				targetV.set(target, null);
				int cvIdx = 0;
				for (Map.Entry<String, ObservableModelSet.RuntimeValuePlaceholder<SettableValue<?>, SettableValue<Object>>> cv : combinedValues
					.entrySet()) {
					combinedVs.get(cv.getKey()).set(tvs.get(transformation.getArg(cvIdx)), null);
					cvIdx++;
				}
				return acceptEvld.get();
			};
			addFn = addEvld == null ? null : (target, tvs, test) -> {
				if (stateful)
					sourceV.set(tvs.getCurrentSource(), null);
				targetV.set(target, null);
				int cvIdx = 0;
				for (Map.Entry<String, ObservableModelSet.RuntimeValuePlaceholder<SettableValue<?>, SettableValue<Object>>> cv : combinedValues
					.entrySet()) {
					combinedVs.get(cv.getKey()).set(tvs.get(transformation.getArg(cvIdx)), null);
					cvIdx++;
				}
				return addEvld.get();
			};
			addAcceptFn = addAcceptEvld == null ? null : (target, tvs) -> {
				if (stateful)
					sourceV.set(tvs.getCurrentSource(), null);
				targetV.set(target, null);
				int cvIdx = 0;
				for (Map.Entry<String, ObservableModelSet.RuntimeValuePlaceholder<SettableValue<?>, SettableValue<Object>>> cv : combinedValues
					.entrySet()) {
					combinedVs.get(cv.getKey()).set(tvs.get(transformation.getArg(cvIdx)), null);
					cvIdx++;
				}
				return addAcceptEvld.get();
			};
			return new Transformation.SourceReplacingReverse<>(transformation, reverseFn, enabledFn, acceptFn, addFn, addAcceptFn, stateful,
				inexact);
		};
	}

	<S, T> MapReverse<S, T> createSourceModifier(ExpressoQIS reverse) throws QonfigInterpretationException {
		MapReverseConfig<S, T> reverseConfig = (MapReverseConfig<S, T>) reverse.get(REVERSE_CONFIG);
		ObservableExpression reverseX = reverse.getValueExpression();
		ObservableExpression enabled = reverse.getAttributeExpression("enabled");
		ObservableExpression accept = reverse.getAttributeExpression("accept");
		ObservableExpression add = reverse.getAttributeExpression("add");
		ObservableExpression addAccept = reverse.getAttributeExpression("add-accept");
		String targetName = reverse.getAttributeText("target-as");
		reverse.getExpressoEnv().getModels().getNameChecker().checkName(targetName);

		ObservableModelSet.WrappedBuilder reverseModelBuilder = reverse.getExpressoEnv().getModels().wrap();
		ObservableModelSet.RuntimeValuePlaceholder<SettableValue<?>, SettableValue<S>> sourcePlaceholder = reverseModelBuilder
			.withRuntimeValue(reverseConfig.sourceName, ModelTypes.Value.forType(reverseConfig.sourceType));
		ObservableModelSet.RuntimeValuePlaceholder<SettableValue<?>, SettableValue<T>> targetPlaceholder = reverseModelBuilder
			.withRuntimeValue(targetName, ModelTypes.Value.forType(reverseConfig.targetType));
		Map<String, ObservableModelSet.RuntimeValuePlaceholder<SettableValue<?>, SettableValue<Object>>> combinedValues = new LinkedHashMap<>();
		for (Map.Entry<String, TypeToken<Object>> combined : reverseConfig.combinedValues.entrySet())
			combinedValues.put(combined.getKey(),
				reverseModelBuilder.withRuntimeValue(combined.getKey(), ModelTypes.Value.forType(combined.getValue())));

		ObservableModelSet.Wrapped reverseModels = reverseModelBuilder.build();
		reverse.setModels(reverseModels, null);

		ValueContainer<ObservableAction<?>, ObservableAction<?>> reversedV = reverseX.evaluate(ModelTypes.Action.any(),
			reverse.getExpressoEnv());
		ValueContainer<SettableValue<?>, SettableValue<String>> enabledV = enabled == null ? null
			: enabled.evaluate(ModelTypes.Value.forType(String.class), reverse.getExpressoEnv());
		ValueContainer<SettableValue<?>, SettableValue<String>> acceptV = accept == null ? null
			: accept.evaluate(ModelTypes.Value.forType(String.class), reverse.getExpressoEnv());
		ValueContainer<SettableValue<?>, SettableValue<S>> addV = add == null ? null
			: add.evaluate(ModelTypes.Value.forType(reverseConfig.sourceType), reverse.getExpressoEnv());
		ValueContainer<SettableValue<?>, SettableValue<String>> addAcceptV = addAccept == null ? null
			: addAccept.evaluate(ModelTypes.Value.forType(String.class), reverse.getExpressoEnv());

		return (transformation, modelSet) -> {
			SettableValue<S> sourceV = SettableValue.build(reverseConfig.sourceType).build();
			SettableValue<T> targetV = SettableValue.build(reverseConfig.targetType).build();
			ObservableModelSet.WrappedInstanceBuilder reverseMSIBuilder = reverseModels.wrap(modelSet)//
				.with(sourcePlaceholder, sourceV.disableWith(ObservableValue.of(StdMsg.UNSUPPORTED_OPERATION)))//
				.with(targetPlaceholder, targetV.disableWith(ObservableValue.of(StdMsg.UNSUPPORTED_OPERATION)));
			Map<String, SettableValue<Object>> combinedVs = new LinkedHashMap<>();
			for (Map.Entry<String, ObservableModelSet.RuntimeValuePlaceholder<SettableValue<?>, SettableValue<Object>>> cv : combinedValues
				.entrySet()) {
				SettableValue<Object> value = SettableValue.build((TypeToken<Object>) cv.getValue().getType().getType(0)).build();
				combinedVs.put(cv.getKey(), value);
				reverseMSIBuilder.with(cv.getValue(), value.disableWith(ObservableValue.of(StdMsg.UNSUPPORTED_OPERATION)));
			}
			ModelSetInstance reverseMSI = reverseMSIBuilder.build();

			ObservableAction<?> reversedEvld = reversedV.get(reverseMSI);
			SettableValue<String> enabledEvld = enabledV == null ? null : enabledV.get(reverseMSI);
			SettableValue<String> acceptEvld = acceptV == null ? null : acceptV.get(reverseMSI);
			SettableValue<S> addEvld = addV == null ? null : addV.get(reverseMSI);
			SettableValue<String> addAcceptEvld = addAcceptV == null ? null : addAcceptV.get(reverseMSI);

			BiConsumer<T, Transformation.TransformationValues<? extends S, ? extends T>> reverseFn;
			Function<Transformation.TransformationValues<? extends S, ? extends T>, String> enabledFn;
			BiFunction<T, Transformation.TransformationValues<? extends S, ? extends T>, String> acceptFn;
			TriFunction<T, Transformation.TransformationValues<? extends S, ? extends T>, Boolean, S> addFn;
			BiFunction<T, Transformation.TransformationValues<? extends S, ? extends T>, String> addAcceptFn;
			reverseFn = (target, tvs) -> {
				sourceV.set(tvs.getCurrentSource(), null);
				targetV.set(target, null);
				int cvIdx = 0;
				for (Map.Entry<String, ObservableModelSet.RuntimeValuePlaceholder<SettableValue<?>, SettableValue<Object>>> cv : combinedValues
					.entrySet()) {
					combinedVs.get(cv.getKey()).set(tvs.get(transformation.getArg(cvIdx)), null);
					cvIdx++;
				}
				reversedEvld.act(null);
			};
			enabledFn = enabledEvld == null ? null : tvs -> {
				sourceV.set(tvs.getCurrentSource(), null);
				targetV.set(null, null);
				int cvIdx = 0;
				for (Map.Entry<String, ObservableModelSet.RuntimeValuePlaceholder<SettableValue<?>, SettableValue<Object>>> cv : combinedValues
					.entrySet()) {
					combinedVs.get(cv.getKey()).set(tvs.get(transformation.getArg(cvIdx)), null);
					cvIdx++;
				}
				return acceptEvld.get();
			};
			acceptFn = enabledEvld == null ? null : (target, tvs) -> {
				sourceV.set(tvs.getCurrentSource(), null);
				targetV.set(target, null);
				int cvIdx = 0;
				for (Map.Entry<String, ObservableModelSet.RuntimeValuePlaceholder<SettableValue<?>, SettableValue<Object>>> cv : combinedValues
					.entrySet()) {
					combinedVs.get(cv.getKey()).set(tvs.get(transformation.getArg(cvIdx)), null);
					cvIdx++;
				}
				return acceptEvld.get();
			};
			addFn = addEvld == null ? null : (target, tvs, test) -> {
				sourceV.set(tvs.getCurrentSource(), null);
				targetV.set(target, null);
				int cvIdx = 0;
				for (Map.Entry<String, ObservableModelSet.RuntimeValuePlaceholder<SettableValue<?>, SettableValue<Object>>> cv : combinedValues
					.entrySet()) {
					combinedVs.get(cv.getKey()).set(tvs.get(transformation.getArg(cvIdx)), null);
					cvIdx++;
				}
				return addEvld.get();
			};
			addAcceptFn = addAcceptEvld == null ? null : (target, tvs) -> {
				sourceV.set(tvs.getCurrentSource(), null);
				targetV.set(target, null);
				int cvIdx = 0;
				for (Map.Entry<String, ObservableModelSet.RuntimeValuePlaceholder<SettableValue<?>, SettableValue<Object>>> cv : combinedValues
					.entrySet()) {
					combinedVs.get(cv.getKey()).set(tvs.get(transformation.getArg(cvIdx)), null);
					cvIdx++;
				}
				return addAcceptEvld.get();
			};
			return new Transformation.SourceModifyingReverse<>(reverseFn, enabledFn, acceptFn, addFn, addAcceptFn);
		};
	}

	private static boolean refersToSource(ObservableExpression ex, String sourceName) {
		return !ex.find(ex2 -> ex2 instanceof NameExpression && ((NameExpression) ex2).getNames().getFirst().equals(sourceName)).isEmpty();
	}

	private <T> ValueCreator<SettableValue<?>, SettableValue<Comparator<T>>> createComparator(ExpressoQIS session)
		throws QonfigInterpretationException {
		TypeToken<T> type = (TypeToken<T>) session.get(VALUE_TYPE_KEY);
		if (type == null)
			throw new QonfigInterpretationException("No " + VALUE_TYPE_KEY + " provided for sort interpretation");
		String valueAs = session.getAttributeText("sort-value-as");
		String compareValueAs = session.getAttributeText("sort-compare-value-as");
		ObservableExpression sortWith = session.getAttributeExpression("sort-with");
		List<ExpressoQIS> sortBy = session.forChildren("sort-by");
		// TODO use ascending
		ModelInstanceType.SingleTyped<SettableValue<?>, Comparator<T>, SettableValue<Comparator<T>>> compareType = ModelTypes.Value
			.forType(TypeTokens.get().keyFor(Comparator.class).parameterized(type));
		if (sortWith != null) {
			if (!sortBy.isEmpty())
				session.getParseSession().withError("sort-with or sort-by may be used, but not both");
			if (valueAs == null)
				throw new QonfigInterpretationException("sort-with must be used with sort-value-as");
			else if (compareValueAs == null)
				throw new QonfigInterpretationException("sort-with must be used with sort-compare-value-as");
			return () -> {
				ObservableModelSet.WrappedBuilder cModelBuilder = session.getExpressoEnv().getModels().wrap();
				ObservableModelSet.RuntimeValuePlaceholder<SettableValue<?>, SettableValue<T>> valuePlaceholder = cModelBuilder
					.withRuntimeValue(valueAs, ModelTypes.Value.forType(type));
				ObservableModelSet.RuntimeValuePlaceholder<SettableValue<?>, SettableValue<T>> compareValuePlaceholder = cModelBuilder
					.withRuntimeValue(compareValueAs, ModelTypes.Value.forType(type));
				ObservableModelSet.Wrapped cModel = cModelBuilder.build();
				ValueContainer<SettableValue<?>, SettableValue<Integer>> comparison;
				try {
					comparison = sortWith.evaluate(ModelTypes.Value.forType(int.class), session.getExpressoEnv().with(cModel, null));
				} catch (QonfigInterpretationException e) {
					session.withError("Could not parse comparison", e);
					return null;
				}
				return new ValueContainer<SettableValue<?>, SettableValue<Comparator<T>>>() {
					@Override
					public ModelInstanceType<SettableValue<?>, SettableValue<Comparator<T>>> getType() {
						return compareType;
					}

					@Override
					public SettableValue<Comparator<T>> get(ModelSetInstance models) {
						SettableValue<T> leftValue = SettableValue.build(type).build();
						SettableValue<T> rightValue = SettableValue.build(type).build();
						ModelSetInstance cModelInstance = cModel.wrap(models)//
							.with(valuePlaceholder, leftValue)//
							.with(compareValuePlaceholder, rightValue)//
							.build();
						SettableValue<Integer> comparisonV = comparison.get(cModelInstance);
						return SettableValue.of(compareType.getValueType(), (v1, v2) -> {
							if (v1 == null) {
								if (v2 == null)
									return 0;
								else
									return 1;
							} else if (v2 == null)
								return -1;
							leftValue.set(v1, null);
							rightValue.set(v2, null);
							return comparisonV.get();
						}, "Not Modifiable");
					}
				};
			};
		} else if (!sortBy.isEmpty()) {
			if (valueAs == null)
				throw new QonfigInterpretationException("sort-by must be used with sort-value-as");
			if (compareValueAs != null)
				session.withWarning("sort-compare-value-as is not used with sort-by");

			ObservableModelSet.WrappedBuilder cModelBuilder = session.getExpressoEnv().getModels().wrap();
			ObservableModelSet.RuntimeValuePlaceholder<SettableValue<?>, SettableValue<T>> valuePlaceholder = cModelBuilder
				.withRuntimeValue(valueAs, ModelTypes.Value.forType(type));
			ObservableModelSet.Wrapped cModel = cModelBuilder.build();

			List<ValueContainer<SettableValue<?>, SettableValue<Object>>> sortByMaps = new ArrayList<>(sortBy.size());
			List<ValueCreator<SettableValue<?>, SettableValue<Comparator<Object>>>> sortByVCs = new ArrayList<>(sortBy.size());
			for (ExpressoQIS sortByX : sortBy) {
				sortByX.setModels(cModel, null);
				ValueContainer<SettableValue<?>, SettableValue<Object>> sortByMap = sortByX.getValueExpression().evaluate(
					(ModelInstanceType<SettableValue<?>, SettableValue<Object>>) (ModelInstanceType<?, ?>) ModelTypes.Value.any(),
					sortByX.getExpressoEnv());
				sortByMaps.add(sortByMap);
				sortByX.put(VALUE_TYPE_KEY, sortByMap.getType().getType(0));
				sortByVCs.add(sortByX.interpret(ValueCreator.class));
			}
			return () -> {
				List<ValueContainer<SettableValue<?>, SettableValue<Comparator<Object>>>> sortByCVs = new ArrayList<>(sortBy.size());
				for (int i = 0; i < sortBy.size(); i++)
					sortByCVs.add(sortByVCs.get(i).createValue());
				return new ValueContainer<SettableValue<?>, SettableValue<Comparator<T>>>() {
					@Override
					public ModelInstanceType<SettableValue<?>, SettableValue<Comparator<T>>> getType() {
						return compareType;
					}

					@Override
					public SettableValue<Comparator<T>> get(ModelSetInstance models) {
						SettableValue<T> value = SettableValue.build(type).build();
						ModelSetInstance cModelInstance = cModel.wrap(models)//
							.with(valuePlaceholder, value)//
							.build();
						List<SettableValue<Object>> sortByMapVs = new ArrayList<>(sortBy.size());
						for (ValueContainer<SettableValue<?>, SettableValue<Object>> sortByMapV : sortByMaps)
							sortByMapVs.add(sortByMapV.get(cModelInstance));
						List<Comparator<Object>> sortByComps = new ArrayList<>(sortBy.size());
						for (ValueContainer<SettableValue<?>, SettableValue<Comparator<Object>>> sortByCV : sortByCVs)
							sortByComps.add(sortByCV.get(cModelInstance).get());
						return SettableValue.of(compareType.getValueType(), (v1, v2) -> {
							if (v1 == null) {
								if (v2 == null)
									return 0;
								else
									return 1;
							} else if (v2 == null)
								return -1;
							for (int i = 0; i < sortByComps.size(); i++) {
								value.set(v1, null);
								Object cv1 = sortByMapVs.get(i).get();
								value.set(v2, null);
								Object cv2 = sortByMapVs.get(i).get();
								int comp = sortByComps.get(i).compare(cv1, cv2);
								if (comp != 0)
									return comp;
							}
							return 0;
						}, "Not Modifiable");
					}
				};
			};
		} else if (TypeTokens.get().isAssignable(TypeTokens.get().keyFor(Comparable.class).wildCard(), type)) {
			return () -> {
				if (CharSequence.class.isAssignableFrom(TypeTokens.getRawType(type))) {
					return ObservableModelSet.literalContainer(compareType, (Comparator<T>) StringUtils.DISTINCT_NUMBER_TOLERANT,
						"NUMBER_TOLERANT");
				} else {
					return ObservableModelSet.literalContainer(compareType, (Comparator<T>) (v1, v2) -> {
						if (v1 == null) {
							if (v2 == null)
								return 0;
							else
								return 1;
						} else if (v2 == null)
							return -1;
						else
							return ((Comparable<T>) v1).compareTo(v2);
					}, "Comparable");
				}
			};
		} else
			throw new QonfigInterpretationException(type + " is not Comparable, use either sort-with or sort-by");
	}

	/**
	 * @param <T> The type to compare
	 * @param type The type to compare
	 * @param expression The expression representing the comparison operation
	 * @param env The environment to evaluate the expression in
	 * @return A function to produce a comparator given a model instance set
	 * @throws QonfigInterpretationException If the comparator cannot be evaluated
	 */
	public static <T> Function<ModelSetInstance, Comparator<T>> parseComparator(TypeToken<T> type, ObservableExpression expression,
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

	static BetterList<TypeToken<?>> argList(TypeToken<?>... init) {
		return BetterTreeList.<TypeToken<?>> build().build().with(init);
	}

	private interface ParsedTransformation<S, T> {
		TypeToken<T> getTargetType();

		boolean isReversible();

		Transformation<S, T> transform(TransformationPrecursor<S, T, ?> precursor, ModelSetInstance modelSet);
	}
}
