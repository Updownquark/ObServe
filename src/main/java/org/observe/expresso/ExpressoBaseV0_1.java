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
import org.observe.collect.ObservableCollectionBuilder;
import org.observe.collect.ObservableSet;
import org.observe.collect.ObservableSortedCollection;
import org.observe.collect.ObservableSortedSet;
import org.observe.config.ObservableConfig;
import org.observe.config.ObservableValueSet;
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
import org.qommons.BiTuple;
import org.qommons.Identifiable.AbstractIdentifiable;
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
		configureBaseModels(interpreter);
		configureExternalModels(interpreter);
		configureInternalModels(interpreter);
		interpreter.createWith("first-value", ValueCreator.class, session -> createFirstValue(wrap(session)));
		interpreter.createWith("transform", ValueCreator.class, session -> createTransform(wrap(session)));
		interpreter.delegateToType("map-reverse", "type", MapReverse.class)//
		.createWith("replace-source", MapReverse.class, session -> createSourceReplace(wrap(session)))//
		.createWith("modify-source", MapReverse.class, session -> createSourceModifier(wrap(session)));
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
					ValueCreator<?, ?> container = child.interpret(ValueCreator.class);
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
					return action;
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
				}
				currentType = ptx.getTargetType();
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
		ObservableModelSet.RuntimeValuePlaceholder<SettableValue<?>, SettableValue<Object>> sourcePlaceholder = mapModelsBuilder
			.withRuntimeValue(sourceName, ModelTypes.Value.forType(currentType));
		ObservableModelSet.Wrapped mapModels = mapModelsBuilder.build();
		map.setModels(mapModels, null);
		ObservableExpression mapEx = map.getValueExpression();
		String targetTypeName = op.getAttributeText("type");
		TypeToken<Object> targetType;
		ValueContainer<SettableValue<?>, SettableValue<Object>> mapped;
		if (targetTypeName != null) {
			targetType = (TypeToken<Object>) parseType(targetTypeName, map.getExpressoEnv());
			mapped = mapEx.evaluate(ModelTypes.Value.forType(targetType), map.getExpressoEnv());
		} else {
			mapped = mapEx.evaluate(
				(ModelInstanceType<SettableValue<?>, SettableValue<Object>>) (ModelInstanceType<?, ?>) ModelTypes.Value.any(),
				map.getExpressoEnv());
			targetType = (TypeToken<Object>) mapped.getType().getType(0);
		}

		boolean cached = op.getAttribute("cache", boolean.class);
		boolean reEval = op.getAttribute("re-eval-on-update", boolean.class);
		boolean fireIfUnchanged = op.getAttribute("fire-if-unchanged", boolean.class);
		boolean nullToNull = op.getAttribute("null-to-null", boolean.class);
		boolean manyToOne = op.getAttribute("many-to-one", boolean.class);
		boolean oneToMany = op.getAttribute("one-to-many", boolean.class);

		MapReverse<Object, Object> mapReverse;
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

		return new ParsedTransformation() {
			@Override
			public TypeToken<Object> getTargetType() {
				return targetType;
			}

			@Override
			public boolean isReversible() {
				return mapReverse != null;
			}

			@Override
			public Transformation<Object, Object> transform(Transformation.TransformationPrecursor<Object, Object, ?> precursor,
				ModelSetInstance modelSet) {
				SettableValue<Object> sourceV = SettableValue.build(currentType).build();
				Map<String, SettableValue<Object>> combinedSourceVs = new LinkedHashMap<>();
				Map<String, SettableValue<Object>> combinedTransformVs = new LinkedHashMap<>();
				Transformation.TransformationBuilder<Object, Object, ?> builder = precursor//
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
				SettableValue<Object> mappedV = mapped.get(mapMSI);
				BiFunction<Object, Transformation.TransformationValues<?, ?>, Object> mapFn = (source, tvs) -> {
					sourceV.set(source, null);
					for (Map.Entry<String, SettableValue<Object>> cv : combinedTransformVs.entrySet())
						cv.getValue().set(tvs.get(combinedSourceVs.get(cv.getKey())), null);
					return mappedV.get();
				};
				Transformation<Object, Object> transformation = builder.build(mapFn);
				if (mapReverse != null)
					transformation = ((MaybeReversibleTransformation<Object, Object>) transformation).withReverse(//
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

	private interface ParsedTransformation {
		TypeToken<Object> getTargetType();

		boolean isReversible();

		Transformation<Object, Object> transform(TransformationPrecursor<Object, Object, ?> precursor, ModelSetInstance modelSet);
	}
}
