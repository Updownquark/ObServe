package org.observe.expresso;

import java.awt.Image;
import java.lang.reflect.Type;
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
import java.util.function.Supplier;
import java.util.stream.Stream;

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
import org.observe.assoc.ObservableSortedMultiMap;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollectionBuilder;
import org.observe.collect.ObservableSet;
import org.observe.collect.ObservableSortedCollection;
import org.observe.collect.ObservableSortedSet;
import org.observe.config.ObservableConfig;
import org.observe.config.ObservableValueSet;
import org.observe.expresso.DynamicModelValue.Identity;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ObservableModelSet.AbstractValueContainer;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.RuntimeValuePlaceholder;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.observe.expresso.ObservableModelSet.ValueCreator;
import org.observe.expresso.ops.NameExpression;
import org.observe.util.TypeTokens;
import org.qommons.BiTuple;
import org.qommons.Causable;
import org.qommons.Identifiable.AbstractIdentifiable;
import org.qommons.LambdaUtils;
import org.qommons.StringUtils;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.TriFunction;
import org.qommons.Version;
import org.qommons.collect.BetterList;
import org.qommons.collect.FastFailLockingStrategy;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.config.AbstractQIS;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigChildDef;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigElement.QonfigValue;
import org.qommons.config.QonfigEvaluationException;
import org.qommons.config.QonfigException;
import org.qommons.config.QonfigInterpretation;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreterCore;
import org.qommons.config.QonfigInterpreterCore.CoreSession;
import org.qommons.config.QonfigInterpreterCore.QonfigValueCreator;
import org.qommons.config.QonfigInterpreterCore.QonfigValueModifier;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.SpecialSession;
import org.qommons.ex.CheckedExceptionWrapper;
import org.qommons.ex.ExBiFunction;
import org.qommons.ex.ExBiPredicate;
import org.qommons.tree.BetterTreeList;

import com.google.common.reflect.TypeToken;

/** Qonfig Interpretation for the ExpressoBaseV0_1 API */
public class ExpressoBaseV0_1 implements QonfigInterpretation {
	/** Session key containing a model value's path */
	public static final String PATH_KEY = "model-path";
	/**
	 * Session key containing a model value's type, if known. This is typically a {@link VariableType}, but may be a
	 * {@link ModelInstanceType} depending on the API of the thing being parsed
	 */
	public static final String VALUE_TYPE_KEY = "value-type";
	/**
	 * Session key containing a model value's key-type, if applicable and known. This is typically a {@link VariableType}, but may be a
	 * {@link ModelInstanceType} depending on the API of the thing being parsed
	 */
	public static final String KEY_TYPE_KEY = "key-type";

	/** Session key where the {@link AppEnvironment} should be stored if available */
	public static final String APP_ENVIRONMENT_KEY = "ExpressoAppEnvironment";

	/** Represents an application so that various models in this class can provide intelligent interaction with the user */
	public interface AppEnvironment {
		/** @return A function to provide the title of the application */
		ValueContainer<SettableValue<?>, ? extends ObservableValue<String>> getTitle();

		/** @return A function to provide the icon representing the application */
		ValueContainer<SettableValue<?>, ? extends ObservableValue<Image>> getIcon();
	}

	public interface ParsedSorting {
		<T> ValueContainer<SettableValue<?>, SettableValue<Comparator<T>>> evaluate(TypeToken<T> type) throws QonfigEvaluationException;
	}

	private QonfigToolkit theExpressoToolkit;

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
		theExpressoToolkit = toolkit;
	}

	@Override
	public QonfigInterpreterCore.Builder configureInterpreter(QonfigInterpreterCore.Builder interpreter) {
		interpreter.createWith("expresso", Expresso.class, session -> {
			ClassView classView = wrap(session).interpretChildren("imports", ClassView.class).peekFirst();
			ObservableModelSet models = wrap(session).setModels(null, classView)//
				.interpretChildren("models", ObservableModelSet.class).peekFirst();
			return new Expresso(classView, models);
		});
		interpreter//
		.modifyWith("with-element-model", Object.class, new Expresso.ElementModelAugmentation<Object>() {
			@Override
			public void augmentElementModel(ExpressoQIS session, ObservableModelSet.Builder builder)
				throws QonfigInterpretationException {
				Map<String, DynamicModelValue.Identity> dynamicValues = new LinkedHashMap<>();
				DynamicModelValue.getDynamicValues(theExpressoToolkit, session.getElement().getType(), dynamicValues);
				for (QonfigAddOn inh : session.getElement().getInheritance().values())
					DynamicModelValue.getDynamicValues(theExpressoToolkit, inh, dynamicValues);
				if (!dynamicValues.isEmpty()) {
					for (DynamicModelValue.Identity dv : dynamicValues.values()) {
						String name;
						if (dv.getNameAttribute() == null)
							name = dv.getName();
						else
							name = session.getElement().getAttributeText(dv.getNameAttribute());
						ExpressoQIS dvSession = session.interpretChild(dv.getDeclaration(), dv.getDeclaration().getType());
						Expresso.ExtModelValue<Object> spec = dvSession.interpret(Expresso.ExtModelValue.class);
							ModelInstanceType<Object, Object> valueType;
							try {
								valueType = (ModelInstanceType<Object, Object>) spec.getType(session);
							} catch (QonfigEvaluationException e) {
								throw new QonfigInterpretationException("Could not interpret type", e, e.getPosition(), e.getErrorLength());
							}
						QonfigExpression2 sourceAttrX;
						try {
							if (dv.isSourceValue())
								sourceAttrX = session.asElement(dv.getOwner()).getValueExpression();
							else if (dv.getSourceAttribute() != null)
								sourceAttrX = session.asElement(dv.getOwner()).getAttributeExpression(dv.getSourceAttribute());
							else
								sourceAttrX = null;
						} catch (QonfigInterpretationException e) {
							if (dv.isSourceValue())
								throw new QonfigInterpretationException("Could not obtain source value expression for " + dv.getOwner(),
									e, e.getPosition(), e.getErrorLength());
							else
								throw new QonfigInterpretationException(
									"Could not obtain source-attribute expression for " + dv.getSourceAttribute(), e, e.getPosition(),
									e.getErrorLength());
						}
						if (sourceAttrX != null) {
							builder.withMaker(name, new DynamicModelValue.Creator<Object, Object>() {
								@Override
								public Identity getIdentity() {
									return dv;
								}

								@Override
								public ValueContainer<Object, Object> createContainer() throws QonfigEvaluationException {
									try {
										return sourceAttrX.evaluate(valueType);
									} catch (QonfigEvaluationException e) {
										String msg = "Could not interpret source" + (dv.isSourceValue() ? " value for " + dv.getOwner()
										: "-attribute " + dv.getSourceAttribute());
										session.error(msg, e);
										throw new QonfigEvaluationException(msg, e, e.getPosition(), e.getErrorLength());
									}
								}

								@Override
								public String toString() {
									return name + "=" + sourceAttrX;
								}
							});
							// } else if (dv.getType() != null) {
							// wrappedBuilder.withMaker(name, ObservableModelSet.IdentifableValueCreator.of(dv,
							// new DynamicModelValue.RuntimeModelValue<>(dv, valueType)));
						} else {
							builder.withMaker(name, new DynamicModelValue.DynamicTypedModelValueCreator<>(dv, valueType));
						}
					}
				}
			}
		})//
		.modifyWith("with-local-model", Object.class, new Expresso.ElementModelAugmentation<Object>() {
			@Override
			public void augmentElementModel(ExpressoQIS session, ObservableModelSet.Builder builder)
				throws QonfigInterpretationException {
				ExpressoQIS model = session.forChildren("model").peekFirst();
				if (model != null)
					model.interpret(ObservableModelSet.class);
			}
		})//
		;
		configureBaseModels(interpreter);
		configureExternalModels(interpreter);
		configureInternalModels(interpreter);
		interpreter//
		.createWith("first-value", ValueCreator.class, session -> createFirstValue(wrap(session)))//
		.createWith("hook", ValueCreator.class, session -> createHook(wrap(session)))//
		;
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
			ObservableModelSet.Builder builder = ObservableModelSet.build("models", ObservableModelSet.JAVA_NAME_CHECKER);
			for (ExpressoQIS model : expressoSession.forChildren("model")) {
				ObservableModelSet.Builder subModel = builder.createSubModel(model.getAttributeText("named", "name"));
				expressoSession.setModels(subModel, null);
				model.setModels(subModel, null).interpret(ObservableModelSet.class);
			}
			ObservableModelSet built = builder.build();
			expressoSession.setModels(built, null);
			return built;
		});
		interpreter.modifyWith("model-value", Object.class, new QonfigValueModifier<Object>() {
			@Override
			public Object prepareSession(CoreSession session) throws QonfigInterpretationException {
				if (session.get(VALUE_TYPE_KEY) == null) {
					QonfigValue typeV = session.getElement().getAttributes().get(session.getAttributeDef(null, null, "type"));
					if (typeV != null && !typeV.text.isEmpty()) {
						session.put(VALUE_TYPE_KEY, VariableType.parseType(typeV.text, wrap(session).getExpressoEnv().getClassView(),
							session.getElement().getDocument().getLocation(), typeV.position));
					}
				}
				if (session.isInstance("model-element"))
					session.put(PATH_KEY, wrap(session).getExpressoEnv().getModels().getIdentity().getPath() + "."
						+ session.getAttributeText("model-element", "name"));
				return null;
			}

			@Override
			public Object modifyValue(Object value, CoreSession session, Object prepValue) throws QonfigInterpretationException {
				return value;
			}
		}).modifyWith("map", Object.class, new QonfigValueModifier<Object>() {
			@Override
			public Object prepareSession(CoreSession session) throws QonfigInterpretationException {
				if (session.get(KEY_TYPE_KEY) == null) {
					QonfigValue typeV = session.getElement().getAttributes().get(session.getAttributeDef(null, null, "key-type"));
					if (typeV != null && !typeV.text.isEmpty()) {
						session.put(KEY_TYPE_KEY, VariableType.parseType(typeV.text, wrap(session).getExpressoEnv().getClassView(),
							session.getElement().getDocument().getLocation(), typeV.position));
					}
				}
				return null;
			}

			@Override
			public Object modifyValue(Object value, CoreSession session, Object prepValue) throws QonfigInterpretationException {
				return value;
			}
		});
	}

	void configureExternalModels(QonfigInterpreterCore.Builder interpreter) {
		interpreter.createWith("ext-model", ObservableModelSet.class, session -> {
			ExpressoQIS eqis = wrap(session);
			ObservableModelSet.Builder model = (ObservableModelSet.Builder) eqis.getExpressoEnv().getModels();
			StringBuilder path = new StringBuilder(model.getIdentity().getPath());
			int pathLen = path.length();
			for (ExpressoQIS valueEl : eqis.forChildren("value")) {
				String name = valueEl.getAttributeText("name");
				path.append('.').append(name);
				String childPath = path.toString();
				path.setLength(pathLen);
				Expresso.ExtModelValue<?> container = valueEl.interpret(Expresso.ExtModelValue.class);
				ModelInstanceType<Object, Object> childType;
				try {
					childType = (ModelInstanceType<Object, Object>) container.getType(valueEl);
				} catch (QonfigEvaluationException e) {
					throw new QonfigInterpretationException("Could not interpret type", e, e.getPosition(), e.getErrorLength());
				}
				QonfigExpression2 defaultX = valueEl.getAttributeExpression("default");
				model.withExternal(name, childType, extModels -> {
					try {
						return extModels.getValue(childPath, childType);
					} catch (IllegalArgumentException | ModelException | TypeConversionException e) {
						if (defaultX == null)
							throw new QonfigEvaluationException(
								"External model " + model.getIdentity() + " does not match expected: " + e.getMessage(), e,
								session.getElement().getPositionInFile(), 0);
					} catch (QonfigEvaluationException e) {
						if (defaultX == null)
							throw new QonfigEvaluationException(
								"External model " + model.getIdentity() + " does not match expected: " + e.getMessage(), e, e.getPosition(),
								e.getErrorLength());
					}
					return null;
				}, models -> {
					if (defaultX == null)
						return null;
					ValueContainer<Object, Object> defaultV = defaultX.evaluate(childType);
					return defaultV.get(models);
				});
			}
			for (ExpressoQIS subModelEl : eqis.forChildren("sub-model")) {
				ObservableModelSet.Builder subModel = model.createSubModel(subModelEl.getAttributeText("named", "name"));
				subModelEl.setModels(subModel, null);
				subModelEl.interpret(ObservableModelSet.class);
			}
			return model;
		})//
		.createWith("event", Expresso.ExtModelValue.class,
			session -> new Expresso.ExtModelValue.SingleTyped<>(ModelTypes.Event, session.get(VALUE_TYPE_KEY, VariableType.class)))//
		.createWith("action", Expresso.ExtModelValue.class,
			session -> new Expresso.ExtModelValue.SingleTyped<>(ModelTypes.Action, session.get(VALUE_TYPE_KEY, VariableType.class)))//
		.createWith("value", Expresso.ExtModelValue.class,
			session -> new Expresso.ExtModelValue.SingleTyped<>(ModelTypes.Value, session.get(VALUE_TYPE_KEY, VariableType.class)))//
		.createWith("list", Expresso.ExtModelValue.class,
			session -> new Expresso.ExtModelValue.SingleTyped<>(ModelTypes.Collection, session.get(VALUE_TYPE_KEY, VariableType.class)))//
		.createWith("set", Expresso.ExtModelValue.class,
			session -> new Expresso.ExtModelValue.SingleTyped<>(ModelTypes.Set, session.get(VALUE_TYPE_KEY, VariableType.class)))//
		.createWith("sorted-list", Expresso.ExtModelValue.class,
			session -> new Expresso.ExtModelValue.SingleTyped<>(ModelTypes.SortedCollection,
				session.get(VALUE_TYPE_KEY, VariableType.class)))//
		.createWith("sorted-set", Expresso.ExtModelValue.class,
			session -> new Expresso.ExtModelValue.SingleTyped<>(ModelTypes.SortedSet, session.get(VALUE_TYPE_KEY, VariableType.class)))//
		.createWith("value-set", Expresso.ExtModelValue.class,
			session -> new Expresso.ExtModelValue.SingleTyped<>(ModelTypes.ValueSet, session.get(VALUE_TYPE_KEY, VariableType.class)))//
		.createWith("map", Expresso.ExtModelValue.class,
			session -> new Expresso.ExtModelValue.DoubleTyped<>(ModelTypes.Map, session.get(KEY_TYPE_KEY, VariableType.class),
				session.get(VALUE_TYPE_KEY, VariableType.class)))//
		.createWith("sorted-map", Expresso.ExtModelValue.class,
			session -> new Expresso.ExtModelValue.DoubleTyped<>(ModelTypes.SortedMap, session.get(KEY_TYPE_KEY, VariableType.class),
				session.get(VALUE_TYPE_KEY, VariableType.class)))//
		.createWith("multi-map", Expresso.ExtModelValue.class,
			session -> new Expresso.ExtModelValue.DoubleTyped<>(ModelTypes.MultiMap, session.get(KEY_TYPE_KEY, VariableType.class),
				session.get(VALUE_TYPE_KEY, VariableType.class)))//
		.createWith("sorted-multi-map", Expresso.ExtModelValue.class,
			session -> new Expresso.ExtModelValue.DoubleTyped<>(ModelTypes.SortedMultiMap,
				session.get(KEY_TYPE_KEY, VariableType.class), session.get(VALUE_TYPE_KEY, VariableType.class)))//
		;
	}

	abstract class InternalCollectionValue<C extends ObservableCollection<?>> implements QonfigValueCreator<ValueCreator<C, C>> {
		private final ModelType<C> theType;

		protected InternalCollectionValue(ModelType<C> type) {
			theType = type;
		}

		@Override
		public ValueCreator<C, C> createValue(CoreSession session) throws QonfigInterpretationException {
			ExpressoQIS exS = wrap(session);
			VariableType vblType = session.get(VALUE_TYPE_KEY, VariableType.class);
			List<ValueCreator<SettableValue<?>, SettableValue<Object>>> elCreators = session.asElement("int-list")
				.interpretChildren("element", ValueCreator.class);
			Object preI = preInterpret(vblType, exS);
			return () -> {
				TypeToken<Object> type;
				try {
					type = TypeTokens.get().wrap((TypeToken<Object>) vblType.getType(exS.getExpressoEnv().getModels()));
				} catch (QonfigEvaluationException e) {
					throw new QonfigEvaluationException("Could not parse type", e, e.getPosition(), e.getErrorLength());
				}
				Object prep;
				try {
					prep = prepare(type, preI);
				} catch (QonfigEvaluationException e) {
					throw new QonfigEvaluationException("Could not prepare values", e, e.getPosition(), e.getErrorLength());
				}
				List<ValueContainer<SettableValue<?>, SettableValue<Object>>> elContainers = new ArrayList<>(elCreators.size());
				for (ValueCreator<SettableValue<?>, SettableValue<Object>> creator : elCreators)
					elContainers.add(creator.createContainer());
				TypeToken<Object> fType = type;
				return new AbstractValueContainer<C, C>((ModelInstanceType<C, C>) theType.forTypes(type)) {
					@Override
					public C get(ModelSetInstance models) throws QonfigEvaluationException {
						C collection = (C) create(fType, models, prep).withDescription(session.get(PATH_KEY, String.class)).build();
						for (ValueContainer<SettableValue<?>, SettableValue<Object>> value : elContainers) {
							if (!((ObservableCollection<Object>) collection).add(value.get(models).get()))
								session.warn("Warning: Value " + value + " already added to " + session.get(PATH_KEY));
						}
						return collection;
					}

					@Override
					public C forModelCopy(C value, ModelSetInstance sourceModels, ModelSetInstance newModels) {
						// Configured elements are merely initialized, not slaved, and the collection may have been modified
						// since it was created. There's no sense to making a re-initialized copy here.
						return value;
					}

					@Override
					public BetterList<ValueContainer<?, ?>> getCores() {
						return BetterList.of(this);
					}
				};
			};
		}

		protected Object preInterpret(VariableType type, ExpressoQIS session) throws QonfigInterpretationException {
			return null;
		}

		protected <V> Object prepare(TypeToken<V> type, Object preInterpret) throws QonfigEvaluationException {
			return null;
		}

		protected abstract <V> ObservableCollectionBuilder<V, ?> create(TypeToken<V> type, ModelSetInstance models, Object prep)
			throws QonfigEvaluationException;

		@Override
		public String toString() {
			return theType.toString();
		}
	}

	abstract class InternalSortedCollectionValue<C extends ObservableSortedCollection<?>> extends InternalCollectionValue<C> {
		public InternalSortedCollectionValue(ModelType<C> type) {
			super(type);
		}

		@Override
		protected Object preInterpret(VariableType type, ExpressoQIS session) throws QonfigInterpretationException {
			return parseSorting(session.forChildren("sort").peekFirst(), type, session.getElement());
		}

		@Override
		protected <V> Object prepare(TypeToken<V> type, Object preInterpret) throws QonfigEvaluationException {
			return ((ParsedSorting) preInterpret).evaluate(type);
		}

		@Override
		protected <V> ObservableCollectionBuilder<V, ?> create(TypeToken<V> type, ModelSetInstance models, Object prep)
			throws QonfigEvaluationException {
			Comparator<V> sorting = ((ValueContainer<SettableValue<?>, SettableValue<Comparator<V>>>) prep).get(models).get();
			return create(type, sorting);
		}

		protected abstract <V> ObservableCollectionBuilder<V, ?> create(TypeToken<V> type, Comparator<V> comparator);
	}

	abstract class InternalMapValue<M extends ObservableMap<?, ?>> implements QonfigValueCreator<ValueCreator<M, M>> {
		private final ModelType<M> theType;

		protected InternalMapValue(ModelType<M> type) {
			theType = type;
		}

		@Override
		public ValueCreator<M, M> createValue(CoreSession session) throws QonfigInterpretationException {
			ExpressoQIS exS = wrap(session);
			VariableType vblKeyType = session.get(KEY_TYPE_KEY, VariableType.class);
			VariableType vblValueType = session.get(VALUE_TYPE_KEY, VariableType.class);
			Object preI = preInterpret(vblKeyType, vblValueType, exS);
			List<BiTuple<ValueCreator<SettableValue<?>, SettableValue<Object>>, ValueCreator<SettableValue<?>, SettableValue<Object>>>> entryCreators;
			entryCreators = session.asElement("int-map").interpretChildren("entry", BiTuple.class);
			return () -> {
				TypeToken<Object> keyType;
				try {
					keyType = TypeTokens.get().wrap((TypeToken<Object>) vblKeyType.getType(exS.getExpressoEnv().getModels()));
				} catch (QonfigEvaluationException e) {
					throw new QonfigEvaluationException("Could not parse key type", e, e.getPosition(), e.getErrorLength());
				}
				TypeToken<Object> valueType;
				try {
					valueType = TypeTokens.get().wrap((TypeToken<Object>) vblValueType.getType(exS.getExpressoEnv().getModels()));
				} catch (QonfigEvaluationException e) {
					throw new QonfigEvaluationException("Could not parse value type", e, e.getPosition(), e.getErrorLength());
				}
				List<BiTuple<ValueContainer<SettableValue<?>, SettableValue<Object>>, ValueContainer<SettableValue<?>, SettableValue<Object>>>> entryContainers;
				entryContainers = new ArrayList<>(entryCreators.size());
				for (BiTuple<ValueCreator<SettableValue<?>, SettableValue<Object>>, ValueCreator<SettableValue<?>, SettableValue<Object>>> entry : entryCreators)
					entryContainers.add(new BiTuple<>(entry.getValue1().createContainer(), entry.getValue2().createContainer()));
				Object prep;
				try {
					prep = prepare(keyType, valueType, exS, preI);
				} catch (QonfigEvaluationException e) {
					throw new QonfigEvaluationException("Could not prepare values", e, e.getPosition(), e.getErrorLength());
				}
				TypeToken<Object> fKeyType = keyType, fValueType = valueType;
				return new AbstractValueContainer<M, M>((ModelInstanceType<M, M>) theType.forTypes(keyType, valueType)) {
					@Override
					public M get(ModelSetInstance models) throws QonfigEvaluationException {
						M map = (M) create(fKeyType, fValueType, models, prep).withDescription(session.get(PATH_KEY, String.class))
							.buildMap();
						for (BiTuple<ValueContainer<SettableValue<?>, SettableValue<Object>>, ValueContainer<SettableValue<?>, SettableValue<Object>>> entry : entryContainers) {
							Object key = entry.getValue1().get(models).get();
							if (map.containsKey(key))
								System.err
								.println("Warning: Entry for key " + entry.getValue1() + " already added to " + session.get(PATH_KEY));
							else
								((ObservableMap<Object, Object>) map).put(key, entry.getValue2().get(models).get());
						}
						return map;
					}

					@Override
					public M forModelCopy(M value, ModelSetInstance sourceModels, ModelSetInstance newModels) {
						// Configured entries are merely initialized, not slaved, and the map may have been modified
						// since it was created. There's no sense to making a re-initialized copy here.
						return value;
					}

					@Override
					public BetterList<ValueContainer<?, ?>> getCores() {
						return BetterList.of(this);
					}
				};
			};
		}

		protected Object preInterpret(VariableType keyType, VariableType valueType, ExpressoQIS session)
			throws QonfigInterpretationException {
			return null;
		}

		protected <K, V> Object prepare(TypeToken<K> keyType, TypeToken<V> valueType, ExpressoQIS session, Object preInterpret)
			throws QonfigEvaluationException {
			return null;
		}

		protected abstract <K, V> ObservableMap.Builder<K, V, ?> create(TypeToken<K> keyType, TypeToken<V> valueType,
			ModelSetInstance models, Object prep) throws QonfigEvaluationException;

		@Override
		public String toString() {
			return theType.toString();
		}
	}

	abstract class InternalMultiMapValue<M extends ObservableMultiMap<?, ?>> implements QonfigValueCreator<ValueCreator<M, M>> {
		private final ModelType<M> theType;

		protected InternalMultiMapValue(ModelType<M> type) {
			theType = type;
		}

		@Override
		public ValueCreator<M, M> createValue(CoreSession session) throws QonfigInterpretationException {
			ExpressoQIS exS = wrap(session);
			VariableType vblKeyType = session.get(KEY_TYPE_KEY, VariableType.class);
			VariableType vblValueType = session.get(VALUE_TYPE_KEY, VariableType.class);
			Object preI = preInterpret(vblKeyType, vblValueType, exS);
			List<BiTuple<ValueCreator<SettableValue<?>, SettableValue<Object>>, ValueCreator<SettableValue<?>, SettableValue<Object>>>> entryCreators;
			entryCreators = session.asElement("int-map").interpretChildren("entry", BiTuple.class);
			return () -> {
				TypeToken<Object> keyType;
				try {
					keyType = TypeTokens.get().wrap((TypeToken<Object>) vblKeyType.getType(exS.getExpressoEnv().getModels()));
				} catch (QonfigEvaluationException e) {
					throw new QonfigEvaluationException("Could not parse key type", e, e.getPosition(), e.getErrorLength());
				}
				TypeToken<Object> valueType;
				try {
					valueType = TypeTokens.get().wrap((TypeToken<Object>) vblValueType.getType(exS.getExpressoEnv().getModels()));
				} catch (QonfigEvaluationException e) {
					throw new QonfigEvaluationException("Could not parse value type", e, e.getPosition(), e.getErrorLength());
				}
				List<BiTuple<ValueContainer<SettableValue<?>, SettableValue<Object>>, ValueContainer<SettableValue<?>, SettableValue<Object>>>> entryContainers;
				entryContainers = new ArrayList<>(entryCreators.size());
				for (BiTuple<ValueCreator<SettableValue<?>, SettableValue<Object>>, ValueCreator<SettableValue<?>, SettableValue<Object>>> entry : entryCreators)
					entryContainers.add(new BiTuple<>(entry.getValue1().createContainer(), entry.getValue2().createContainer()));
				Object prep;
				try {
					prep = prepare(keyType, valueType, wrap(session), preI);
				} catch (QonfigInterpretationException e) {
					throw new QonfigEvaluationException("Could not prepare values", e, e.getPosition(), e.getErrorLength());
				}
				TypeToken<Object> fKeyType = keyType, fValueType = valueType;
				return new AbstractValueContainer<M, M>((ModelInstanceType<M, M>) theType.forTypes(keyType, valueType)) {
					@Override
					public M get(ModelSetInstance models) throws QonfigEvaluationException {
						M map = (M) create(fKeyType, fValueType, models, prep).withDescription(session.get(PATH_KEY, String.class))
							.build(models.getUntil());
						for (BiTuple<ValueContainer<SettableValue<?>, SettableValue<Object>>, ValueContainer<SettableValue<?>, SettableValue<Object>>> entry : entryContainers) {
							((ObservableMultiMap<Object, Object>) map).add(entry.getValue1().get(models), entry.getValue2().get(models));
						}
						return map;
					}

					@Override
					public M forModelCopy(M value, ModelSetInstance sourceModels, ModelSetInstance newModels) {
						// Configured entries are merely initialized, not slaved, and the multi-map may have been modified
						// since it was created. There's no sense to making a re-initialized copy here.
						return value;
					}

					@Override
					public BetterList<ValueContainer<?, ?>> getCores() {
						return BetterList.of(this);
					}
				};
			};
		}

		protected Object preInterpret(VariableType keyType, VariableType valueType, ExpressoQIS session)
			throws QonfigInterpretationException {
			return null;
		}

		protected <K, V> Object prepare(TypeToken<K> keyType, TypeToken<V> valueType, ExpressoQIS session, Object preInterpret)
			throws QonfigEvaluationException {
			return null;
		}

		protected abstract <K, V> ObservableMultiMap.Builder<K, V, ?> create(TypeToken<K> keyType, TypeToken<V> valueType,
			ModelSetInstance models, Object prep) throws QonfigEvaluationException;

		@Override
		public String toString() {
			return theType.toString();
		}
	}

	void configureInternalModels(QonfigInterpreterCore.Builder interpreter) {
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
		});
		interpreter.createWith("constant", ValueCreator.class, session -> interpretConstant(wrap(session)));
		interpreter.createWith("value", ValueCreator.class, session -> interpretValue(wrap(session)));
		interpreter.createWith("action", ValueCreator.class, session -> {
			ExpressoQIS exS = wrap(session);
			QonfigExpression2 valueX = exS.getValueExpression();
			VariableType vblType = session.get(VALUE_TYPE_KEY, VariableType.class);
			return ValueCreator.name(valueX::toString, () -> {
				TypeToken<Object> type = vblType == null ? null : (TypeToken<Object>) vblType.getType(exS.getExpressoEnv().getModels());
				ValueContainer<ObservableAction<?>, ObservableAction<Object>> action = valueX.evaluate(
					ModelTypes.Action.forType(type == null ? (TypeToken<Object>) (TypeToken<?>) TypeTokens.get().WILDCARD : type));
				return action.wrapModels(exS::wrapLocal);
			});
		}).createWith("action-group", ValueCreator.class, session -> interpretActionGroup(wrap(session)));
		interpreter.createWith("loop", ValueCreator.class, session -> interpretLoop(wrap(session)));
		interpreter.createWith("value-set", ValueCreator.class, session -> {
			ExpressoQIS exS = wrap(session);
			VariableType vblType = session.get(VALUE_TYPE_KEY, VariableType.class);
			return ValueCreator.name(() -> "value-set<" + vblType + ">", () -> {
				return new ValueContainer<ObservableValueSet<?>, ObservableValueSet<Object>>() {
					private ModelInstanceType<ObservableValueSet<?>, ObservableValueSet<Object>> theType;

					@Override
					public ModelInstanceType<ObservableValueSet<?>, ObservableValueSet<Object>> getType() throws QonfigEvaluationException {
						if (theType == null) {
							try {
								theType = ModelTypes.ValueSet
									.forType((TypeToken<Object>) vblType.getType(exS.getExpressoEnv().getModels()));
							} catch (QonfigEvaluationException e) {
								throw new QonfigEvaluationException("Could not evaluate type", e, e.getPosition(), e.getErrorLength());
							}
						}
						return theType;
					}

					@Override
					public ObservableValueSet<Object> get(ModelSetInstance models) throws QonfigEvaluationException {
						// Although a purely in-memory value set would be more efficient, I have yet to implement one.
						// Easiest path forward for this right now is to make an unpersisted ObservableConfig and use it to back the value
						// set.
						// TODO At some point I should come back and make an in-memory implementation and use it here.
						ObservableConfig config = ObservableConfig.createRoot("root", null,
							__ -> new FastFailLockingStrategy(ThreadConstraint.ANY));
						return config.asValue((TypeToken<Object>) getType().getType(0)).buildEntitySet(null);
					}

					@Override
					public ObservableValueSet<Object> forModelCopy(ObservableValueSet<Object> value, ModelSetInstance sourceModels,
						ModelSetInstance newModels) throws QonfigEvaluationException {
						return value;
					}

					@Override
					public BetterList<ValueContainer<?, ?>> getCores() throws QonfigEvaluationException {
						return BetterList.of(this);
					}
				};
			});
		});
		interpreter.createWith("element", ValueCreator.class, session -> {
			ExpressoQIS exS = wrap(session);
			VariableType vblType = session.get(VALUE_TYPE_KEY, VariableType.class);
			if (vblType == null)
				throw new QonfigInterpretationException("No " + VALUE_TYPE_KEY + " available", session.getElement().getPositionInFile(), 0);
			QonfigExpression2 valueX = exS.getValueExpression();
			return () -> {
				TypeToken<?> type = vblType.getType(exS.getExpressoEnv().getModels());
				return valueX.evaluate(ModelTypes.Value.forType(type));
			};
		});
		interpreter.createWith("list", ValueCreator.class, new InternalCollectionValue<ObservableCollection<?>>(ModelTypes.Collection) {
			@Override
			protected <V> ObservableCollectionBuilder<V, ?> create(TypeToken<V> type, ModelSetInstance models, Object prep) {
				return ObservableCollection.build(type);
			}
		});
		interpreter.createWith("set", ValueCreator.class, new InternalCollectionValue<ObservableSet<?>>(ModelTypes.Set) {
			@Override
			protected <V> ObservableCollectionBuilder<V, ?> create(TypeToken<V> type, ModelSetInstance models, Object prep) {
				return ObservableSet.build(type);
			}
		});
		// class SortedCollectionCreator<C extends ObservableSortedCollection<?>> extends InternalCollectionValue
		interpreter
		.createWith("sorted-set", ValueCreator.class, new InternalSortedCollectionValue<ObservableSortedSet<?>>(ModelTypes.SortedSet) {
			@Override
			protected <V> ObservableCollectionBuilder<V, ?> create(TypeToken<V> type, Comparator<V> comparator) {
				return ObservableSortedSet.build(type, comparator);
			}
		}).createWith("sorted-list", ValueCreator.class,
			new InternalSortedCollectionValue<ObservableSortedCollection<?>>(ModelTypes.SortedCollection) {
			@Override
			protected <V> ObservableCollectionBuilder<V, ?> create(TypeToken<V> type, Comparator<V> comparator) {
				return ObservableSortedCollection.build(type, comparator);
			}
		})
		.createWith("entry", BiTuple.class, session -> {
			ExpressoQIS exS = wrap(session);
			VariableType vblKeyType = session.get(KEY_TYPE_KEY, VariableType.class);
			VariableType vblValueType = session.get(VALUE_TYPE_KEY, VariableType.class);
			if (vblKeyType == null)
				throw new QonfigInterpretationException("No " + KEY_TYPE_KEY + " available", session.getElement().getPositionInFile(),
					0);
			if (vblValueType == null)
				throw new QonfigInterpretationException("No " + VALUE_TYPE_KEY + " available", session.getElement().getPositionInFile(),
					0);
			QonfigExpression2 keyX = exS.getAttributeExpression("key");
			QonfigExpression2 valueX = exS.getAttributeExpression("value");
			ValueCreator<SettableValue<?>, SettableValue<Object>> key = () -> {
				TypeToken<?> keyType = vblKeyType.getType(exS.getExpressoEnv().getModels());
				return (ValueContainer<SettableValue<?>, SettableValue<Object>>) (ValueContainer<?, ?>) keyX
					.evaluate(ModelTypes.Value.forType(keyType));
			};
			ValueCreator<SettableValue<?>, SettableValue<Object>> value = () -> {
				TypeToken<?> valueType = vblValueType.getType(exS.getExpressoEnv().getModels());
				return (ValueContainer<SettableValue<?>, SettableValue<Object>>) (ValueContainer<?, ?>) valueX
					.evaluate(ModelTypes.Value.forType(valueType));
			};
			return new BiTuple<>(key, value);
		}).createWith("map", ValueCreator.class, new InternalMapValue<ObservableMap<?, ?>>(ModelTypes.Map) {
			@Override
			protected <K, V> ObservableMap.Builder<K, V, ?> create(TypeToken<K> keyType, TypeToken<V> valueType,
				ModelSetInstance models, Object prep) {
				return ObservableMap.build(keyType, valueType);
			}
		}).createWith("sorted-map", ValueCreator.class, new InternalMapValue<ObservableSortedMap<?, ?>>(ModelTypes.SortedMap) {
			@Override
			protected Object preInterpret(VariableType keyType, VariableType valueType, ExpressoQIS session)
				throws QonfigInterpretationException {
				return parseSorting(session.forChildren("sort").peekFirst(), keyType, session.getElement());
			}

			@Override
			protected <K, V> Object prepare(TypeToken<K> keyType, TypeToken<V> valueType, ExpressoQIS session, Object preInterpret)
				throws QonfigEvaluationException {
				return ((ParsedSorting) preInterpret).evaluate(keyType);
			}

			@Override
			protected <K, V> ObservableMap.Builder<K, V, ?> create(TypeToken<K> keyType, TypeToken<V> valueType,
				ModelSetInstance models, Object prep) throws QonfigEvaluationException {
				Comparator<K> sorting = ((ValueContainer<SettableValue<?>, SettableValue<Comparator<K>>>) prep).get(models).get();
				return ObservableSortedMap.build(keyType, valueType, sorting);
			}
		}).createWith("multi-map", ValueCreator.class, new InternalMultiMapValue<ObservableMultiMap<?, ?>>(ModelTypes.MultiMap) {
			@Override
			protected <K, V> ObservableMultiMap.Builder<K, V, ?> create(TypeToken<K> keyType, TypeToken<V> valueType,
				ModelSetInstance models, Object prep) {
				return ObservableMultiMap.build(keyType, valueType);
			}
		}).createWith("sorted-multi-map", ValueCreator.class, new InternalMultiMapValue<ObservableMultiMap<?, ?>>(ModelTypes.MultiMap) {
			@Override
			protected Object preInterpret(VariableType keyType, VariableType valueType, ExpressoQIS session)
				throws QonfigInterpretationException {
				return parseSorting(session.forChildren("sort").peekFirst(), keyType, session.getElement());
			}

			@Override
			protected <K, V> Object prepare(TypeToken<K> keyType, TypeToken<V> valueType, ExpressoQIS session, Object preInterpret)
				throws QonfigEvaluationException {
				return ((ParsedSorting) preInterpret).evaluate(keyType);
			}

			@Override
			protected <K, V> ObservableMultiMap.Builder<K, V, ?> create(TypeToken<K> keyType, TypeToken<V> valueType,
				ModelSetInstance models, Object prep) throws QonfigEvaluationException {
				Comparator<K> sorting = ((ValueContainer<SettableValue<?>, SettableValue<Comparator<K>>>) prep).get(models).get();
				return ObservableMultiMap.build(keyType, valueType).sortedBy(sorting);
			}
		});
	}

	private ValueCreator<SettableValue<?>, SettableValue<Object>> interpretConstant(ExpressoQIS exS) throws QonfigInterpretationException {
		TypeToken<Object> valueType = (TypeToken<Object>) exS.get(VALUE_TYPE_KEY);
		QonfigExpression2 value = exS.getValueExpression();
		return new ValueCreator<SettableValue<?>, SettableValue<Object>>() {
			@Override
			public ValueContainer<SettableValue<?>, SettableValue<Object>> createContainer() throws QonfigEvaluationException {
				ValueContainer<SettableValue<?>, SettableValue<Object>> valueC;
				try {
					valueC = value.evaluate(//
						valueType == null
						? (ModelInstanceType<SettableValue<?>, SettableValue<Object>>) (ModelInstanceType<?, ?>) ModelTypes.Value.any()
							: ModelTypes.Value.forType(valueType));
				} catch (QonfigEvaluationException e) {
					throw new QonfigEvaluationException("Could not interpret value of constant: " + value, e, e.getPosition(),
						e.getErrorLength());
				}
				return new ValueContainer<SettableValue<?>, SettableValue<Object>>() {
					@Override
					public ModelInstanceType<SettableValue<?>, SettableValue<Object>> getType() throws QonfigEvaluationException {
						return valueC.getType();
					}

					@Override
					public SettableValue<Object> get(ModelSetInstance models) throws QonfigEvaluationException {
						Object v = valueC.get(models).get();
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
								return exS.get(PATH_KEY);
							}
						}
						return new ConstantValue<>((TypeToken<Object>) getType().getType(0), v);
					}

					@Override
					public SettableValue<Object> forModelCopy(SettableValue<Object> value2, ModelSetInstance sourceModels,
						ModelSetInstance newModels) {
						return value2;
					}

					@Override
					public BetterList<ValueContainer<?, ?>> getCores() {
						return BetterList.of(this);
					}
				};
			}

			@Override
			public String toString() {
				return "constant:" + valueType;
			}
		};
	}

	private ValueCreator<SettableValue<?>, SettableValue<Object>> interpretValue(ExpressoQIS exS) throws QonfigInterpretationException {
		QonfigExpression2 valueX = exS.getValueExpression();
		QonfigExpression2 initX = exS.isInstance("int-value") ? exS.asElement("int-value").getAttributeExpression("init") : null;
		if (initX != null && valueX != null)
			exS.warn("Either a value or an init value may be specified, but not both.  Initial value will be ignored.");
		VariableType vblType = exS.get(VALUE_TYPE_KEY, VariableType.class);
		if (vblType == null && valueX == null && initX == null)
			throw new QonfigInterpretationException("A type, a value, or an initializer must be specified",
				exS.getElement().getPositionInFile(), 0);
		return ValueCreator.name(() -> {
			if (valueX != null)
				return valueX.toString();
			else if (vblType != null)
				return vblType.toString();
			else
				return "init:" + initX.toString();
		}, () -> {
			TypeToken<Object> type;
			try {
				type = vblType == null ? null : (TypeToken<Object>) vblType.getType(exS.getExpressoEnv().getModels());
			} catch (QonfigEvaluationException e) {
				throw new QonfigEvaluationException("Could not parse type", e, e.getPosition(), e.getErrorLength());
			}
			ValueContainer<SettableValue<?>, SettableValue<Object>> value = valueX == null ? null
				: valueX.evaluate(ModelTypes.Value.forType(type));
			ValueContainer<SettableValue<?>, SettableValue<Object>> init;
			if (value != null)
				init = null;
			else if (initX != null)
				init = initX.evaluate(ModelTypes.Value.forType(type));
			else
				init = ValueContainer.literal(type, TypeTokens.get().getDefaultValue(type), "<default>");
			ModelInstanceType<SettableValue<?>, SettableValue<Object>> fType;
			if (type != null)
				fType = ModelTypes.Value.forType(type);
			else if (value != null)
				fType = value.getType();
			else
				fType = init.getType();
			return new ObservableModelSet.AbstractValueContainer<SettableValue<?>, SettableValue<Object>>(fType) {
				@Override
				public SettableValue<Object> get(ModelSetInstance models) throws QonfigEvaluationException {
					if (value != null)
						return value.get(models);
					SettableValue.Builder<Object> builder = SettableValue.build((TypeToken<Object>) fType.getType(0));
					builder.withDescription((String) exS.get(PATH_KEY));
					if (init != null)
						builder.withValue(init.get(models).get());
					return builder.build();
				}

				@Override
				public SettableValue<Object> forModelCopy(SettableValue<Object> value2, ModelSetInstance sourceModels,
					ModelSetInstance newModels) throws QonfigEvaluationException {
					if (value != null)
						return value.forModelCopy(value2, sourceModels, newModels);
					else
						return value2;
				}

				@Override
				public BetterList<ValueContainer<?, ?>> getCores() throws QonfigEvaluationException {
					if (value != null)
						return value.getCores();
					else
						return BetterList.of(this);
				}
			};
		});
	}

	private ValueCreator<ObservableAction<?>, ObservableAction<Object>> interpretActionGroup(ExpressoQIS exS)
		throws QonfigInterpretationException {
		List<ValueCreator<ObservableAction<?>, ObservableAction<Object>>> actions = exS.interpretChildren("action", ValueCreator.class);
		return ValueCreator.name("action-group", () -> {
			BetterList<ValueContainer<ObservableAction<?>, ObservableAction<Object>>> actionVs = BetterList.of2(actions.stream(), //
				a -> a.createContainer());
			return new ValueContainer<ObservableAction<?>, ObservableAction<Object>>() {
				@Override
				public ModelInstanceType<ObservableAction<?>, ObservableAction<Object>> getType() {
					return ModelTypes.Action.forType(Object.class);
				}

				@Override
				public ObservableAction<Object> get(ModelSetInstance models) throws QonfigEvaluationException {
					ModelSetInstance wrappedModels = exS.wrapLocal(models);
					BetterList<ObservableAction<Object>> realActions = BetterList.of2(actionVs.stream(), a -> a.get(wrappedModels));
					return createActionGroup(realActions);
				}

				@Override
				public ObservableAction<Object> forModelCopy(ObservableAction<Object> value, ModelSetInstance sourceModels,
					ModelSetInstance newModels) throws QonfigEvaluationException {
					ModelSetInstance wrappedNew = exS.wrapLocal(newModels);
					List<ObservableAction<Object>> realActions = new ArrayList<>(actionVs.size());
					boolean different = false;
					for (ValueContainer<ObservableAction<?>, ObservableAction<Object>> actionV : actionVs) {
						ObservableAction<Object> sourceAction = actionV.get(sourceModels);
						ObservableAction<Object> copyAction = actionV.get(wrappedNew);
						different |= sourceAction != copyAction;
						realActions.add(copyAction);
					}
					return different ? createActionGroup(realActions) : value;
				}

				private ObservableAction<Object> createActionGroup(List<ObservableAction<Object>> realActions) {
					return ObservableAction.of(TypeTokens.get().OBJECT, cause -> {
						List<Object> result = new ArrayList<>(realActions.size());
						for (ObservableAction<Object> realAction : realActions)
							result.add(realAction.act(cause));
						return result;
					});
				}

				@Override
				public BetterList<ValueContainer<?, ?>> getCores() throws QonfigEvaluationException {
					return BetterList.of(actionVs.stream(), vc -> vc.getCores().stream());
				}
			};
		});
	}

	private ValueCreator<ObservableAction<?>, ObservableAction<Object>> interpretLoop(ExpressoQIS exS)
		throws QonfigInterpretationException {
		QonfigExpression2 init = exS.getAttributeExpression("init");
		QonfigExpression2 before = exS.getAttributeExpression("before-while");
		QonfigExpression2 whileX = exS.getAttributeExpression("while");
		QonfigExpression2 beforeBody = exS.getAttributeExpression("before-body");
		QonfigExpression2 afterBody = exS.getAttributeExpression("after-body");
		QonfigExpression2 finallly = exS.getAttributeExpression("finally");
		List<ValueCreator<ObservableAction<?>, ObservableAction<?>>> exec = exS.interpretChildren("body", ValueCreator.class);
		return ValueCreator.name("loop", () -> {
			ValueContainer<ObservableAction<?>, ObservableAction<?>> initV = init == null ? null : init.evaluate(ModelTypes.Action.any());
			ValueContainer<ObservableAction<?>, ObservableAction<?>> beforeV = before == null ? null
				: before.evaluate(ModelTypes.Action.any());
			ValueContainer<SettableValue<?>, SettableValue<Boolean>> whileV = whileX.evaluate(ModelTypes.Value.forType(boolean.class));
			ValueContainer<ObservableAction<?>, ObservableAction<?>> beforeBodyV = beforeBody == null ? null
				: beforeBody.evaluate(ModelTypes.Action.any());
			ValueContainer<ObservableAction<?>, ObservableAction<?>> afterBodyV = afterBody == null ? null
				: afterBody.evaluate(ModelTypes.Action.any());
			ValueContainer<ObservableAction<?>, ObservableAction<?>> finallyV = finallly == null ? null
				: finallly.evaluate(ModelTypes.Action.any());
			List<ValueContainer<ObservableAction<?>, ObservableAction<?>>> execVs = BetterList.of2(exec.stream(), v -> v.createContainer());
			return new ValueContainer<ObservableAction<?>, ObservableAction<Object>>() {
				@Override
				public ModelInstanceType<ObservableAction<?>, ObservableAction<Object>> getType() {
					return ModelTypes.Action.forType(Object.class);
				}

				@Override
				public ObservableAction<Object> get(ModelSetInstance models) throws QonfigEvaluationException {
					ModelSetInstance wrappedModels = exS.wrapLocal(models);
					return new LoopAction(//
						initV == null ? null : initV.get(wrappedModels), //
							beforeV == null ? null : beforeV.get(wrappedModels), //
								whileV.get(wrappedModels), //
								beforeBodyV == null ? null : beforeBodyV.get(wrappedModels), //
									BetterList.of2(execVs.stream(), v -> v.get(wrappedModels)), //
									afterBodyV == null ? null : afterBodyV.get(wrappedModels), //
										finallyV == null ? null : finallyV.get(wrappedModels)//
						);
				}

				@Override
				public ObservableAction<Object> forModelCopy(ObservableAction<Object> value, ModelSetInstance sourceModels,
					ModelSetInstance newModels) throws QonfigEvaluationException {
					ObservableAction<?> initS = initV == null ? null : initV.get(sourceModels);
					ObservableAction<?> initA = initV == null ? null : initV.get(newModels);
					ObservableAction<?> beforeS = beforeV == null ? null : beforeV.get(sourceModels);
					ObservableAction<?> beforeA = beforeV == null ? null : beforeV.get(newModels);
					SettableValue<Boolean> whileS = whileV.get(sourceModels);
					SettableValue<Boolean> whileC = whileV.get(newModels);
					ObservableAction<?> beforeBodyS = beforeBodyV == null ? null : beforeBodyV.get(sourceModels);
					ObservableAction<?> beforeBodyA = beforeBodyV == null ? null : beforeBodyV.get(newModels);
					boolean different = initS != initA || beforeS != beforeA || whileS != whileC || beforeBodyS != beforeBodyA;
					List<ObservableAction<?>> execAs = new ArrayList<>(execVs.size());
					for (ValueContainer<ObservableAction<?>, ObservableAction<?>> execV : execVs) {
						ObservableAction<?> execS = execV.get(sourceModels);
						ObservableAction<?> execA = execV.get(newModels);
						different |= execS != execA;
					}
					ObservableAction<?> afterBodyS = afterBodyV == null ? null : afterBodyV.get(sourceModels);
					ObservableAction<?> afterBodyA = afterBodyV == null ? null : afterBodyV.get(newModels);
					ObservableAction<?> finallyS = finallyV == null ? null : finallyV.get(sourceModels);
					ObservableAction<?> finallyA = finallyV == null ? null : finallyV.get(newModels);
					different |= afterBodyS != afterBodyA || finallyS != finallyA;
					if (different)
						return new LoopAction(initA, beforeA, whileC, beforeBodyA, execAs, afterBodyA, finallyA);
					else
						return value;
				}

				@Override
				public BetterList<ValueContainer<?, ?>> getCores() throws QonfigEvaluationException {
					return BetterList.of(Stream.concat(//
						Stream.of(initV, beforeV, whileV, beforeBodyV, afterBodyV, finallyV), //
						execVs.stream()), cv -> cv == null ? Stream.empty() : cv.getCores().stream());
				}
			};
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
		return ValueCreator.name("first", () -> {
			List<ValueContainer<SettableValue<?>, SettableValue<?>>> valueContainers = new ArrayList<>(valueCreators.size());
			List<TypeToken<?>> valueTypes = new ArrayList<>(valueCreators.size());
			for (ValueCreator<SettableValue<?>, SettableValue<?>> creator : valueCreators) {
				valueContainers.add(creator.createContainer());
				valueTypes.add(valueContainers.get(valueContainers.size() - 1).getType().getType(0));
			}
			TypeToken<Object> commonType = TypeTokens.get().getCommonType(valueTypes);
			return new AbstractValueContainer<SettableValue<?>, SettableValue<Object>>(ModelTypes.Value.forType(commonType)) {
				@Override
				public SettableValue<Object> get(ModelSetInstance models) throws QonfigEvaluationException {
					SettableValue<?>[] vs = new SettableValue[valueCreators.size()];
					for (int i = 0; i < vs.length; i++)
						vs[i] = valueContainers.get(i).get(models);
					return SettableValue.firstValue(commonType, v -> v != null, () -> null, vs);
				}

				@Override
				public SettableValue<Object> forModelCopy(SettableValue<Object> value, ModelSetInstance sourceModels,
					ModelSetInstance newModels) throws QonfigEvaluationException {
					SettableValue<?>[] vs = new SettableValue[valueCreators.size()];
					boolean different = false;
					for (int i = 0; i < vs.length; i++) {
						SettableValue<?> sv = valueContainers.get(i).get(sourceModels);
						SettableValue<?> nv = valueContainers.get(i).get(newModels);
						different |= sv != nv;
						vs[i] = nv;
					}
					if (different)
						return SettableValue.firstValue(commonType, v -> v != null, () -> null, vs);
					else
						return value;
				}

				@Override
				public BetterList<ValueContainer<?, ?>> getCores() throws QonfigEvaluationException {
					return BetterList.of(valueContainers.stream(), vc -> vc.getCores().stream());
				}
			};
		});
	}

	private <V> ValueCreator<Observable<?>, Observable<V>> createHook(ExpressoQIS session) throws QonfigInterpretationException {
		QonfigExpression2 onX = session.getAttributeExpression("on");
		QonfigExpression2 actionX = session.getValueExpression();
		return ValueCreator.name("hook", () -> {
			ValueContainer<Observable<?>, Observable<V>> onC = onX == null ? null : onX.evaluate(ModelTypes.Event.<V> anyAs());
			ModelInstanceType.SingleTyped<Observable<?>, V, Observable<V>> eventType;
			eventType = onC == null ? ModelTypes.Event.forType((Class<V>) void.class)
				: (ModelInstanceType.SingleTyped<Observable<?>, V, Observable<V>>) onC.getType();
			DynamicModelValue.satisfyDynamicValueType("event", session.getExpressoEnv().getModels(),
				ModelTypes.Value.forType(eventType.getValueType()));
			ValueContainer<ObservableAction<?>, ObservableAction<?>> actionC = actionX.evaluate(ModelTypes.Action.any());
			return ValueContainer.of(eventType, msi -> {
				msi = session.wrapLocal(msi);
				Observable<V> on = onC == null ? null : onC.get(msi);
				ObservableAction<?> action = actionC.get(msi);
				SettableValue<V> event = SettableValue.build(eventType.getValueType())//
					.withValue(TypeTokens.get().getDefaultValue(eventType.getValueType())).build();
				try {
					DynamicModelValue.satisfyDynamicValue("event", ModelTypes.Value.forType(eventType.getValueType()), msi, event);
				} catch (ModelException | TypeConversionException e) {
					throw new QonfigEvaluationException("Could not satisfy event variable", session.getElement().getPositionInFile(), 0);
				}
				if (on != null) {
					on.takeUntil(msi.getUntil()).act(v -> {
						event.set(v, null);
						action.act(v);
					});
					return on;
				} else {
					action.act(null);
					return Observable.empty();
				}
			});
		});
	}

	/**
	 * @param <T> The type to assume of the session type
	 * @param session The session to get the type from
	 * @param typeKey The value key to get the type from the session
	 * @return The type stored in the given key, or null if no value is stored for the key
	 * @throws QonfigEvaluationException If the value in the session for the given key is not a type
	 */
	public static <T> TypeToken<T> getType(ExpressoQIS session, String typeKey) throws QonfigEvaluationException {
		Object type = session.get(typeKey);
		if (type == null)
			return null;
		else if (type instanceof TypeToken<?>)
			return (TypeToken<T>) type;
		else if (type instanceof VariableType) {
			type = ((VariableType) type).getType(session.getExpressoEnv().getModels());
			session.put(typeKey, type);
			return (TypeToken<T>) type;
		} else
			throw new IllegalStateException("Type is a " + type.getClass().getName() + ", not a TypeToken");
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
		.createWith("disable", ActionTransform.class, session -> disabledAction(wrap(session)))//
		;
		interpreter//
		.createWith("disable", ValueTransform.class, session -> disabledValue(wrap(session)))//
		.createWith("filter-accept", ValueTransform.class, session -> filterAcceptValue(wrap(session)))//
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
		QonfigExpression2 sourceX = session.getAttributeExpression("source");
		/*
		 * We can't interpret the transformation operations here, where we'd really like to to conform with the architecture.
		 * The interpretation of the transformations depends on the model type of the source (or previous step),
		 * and this can't be known until we evaluate it.
		 */
		return new ValueCreator<Object, Object>() {
			@Override
			public ValueContainer<Object, Object> createContainer() throws QonfigEvaluationException {
				ValueContainer<SettableValue<?>, SettableValue<?>> source;
				try {
					source = sourceX.evaluate(ModelTypes.Value.any());
				} catch (QonfigEvaluationException e) {
					throw new QonfigEvaluationException("Could not interpret source " + sourceX, e, e.getPosition(), e.getErrorLength());
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
							firstStep = source.as(mit);
						} catch (QonfigEvaluationException e) {
							throw new QonfigEvaluationException("Could not interpret source " + sourceX, e, e.getPosition(),
								e.getErrorLength());
						} catch (TypeConversionException e) {
							sourceX.throwException("Could not convert source " + sourceX + ", type " + source.getType() + " to type " + mit,
								e);
							throw new IllegalStateException(); // Shouldn't get here
						}
					} else {
						firstStep = (ValueContainer<Object, Object>) (ValueContainer<?, ?>) source;
						transformType = getTransformFor(ModelTypes.Value);
					}
				} else {
					firstStep = (ValueContainer<Object, Object>) (ValueContainer<?, ?>) source;
					transformType = getTransformFor(ModelTypes.Value);
				}
				ObservableStructureTransform<Object, Object, Object, Object> transform = ObservableStructureTransform
					.unity(firstStep.getType());
				List<ExpressoQIS> ops;
				try {
					ops = session.forChildren("op");
				} catch (QonfigInterpretationException e) {
					throw new QonfigEvaluationException("Could not interpret transformation operations", e, e.getPosition(),
						e.getErrorLength());
				}
				for (ExpressoQIS op : ops) {
					transformType = getTransformFor(transform.getTargetType().getModelType());
					if (transformType == null) {
						throw new QonfigEvaluationException(
							"No transform supported for model type " + transform.getTargetType().getModelType(),
							op.getElement().getPositionInFile(), 0);
					} else if (!op.supportsInterpretation(transformType)) {
						throw new QonfigEvaluationException("No transform supported for operation type " + op.getFocusType().getName()
							+ " for model type " + transform.getTargetType().getModelType(), op.getElement().getPositionInFile(), 0);
					}
					ObservableStructureTransform<Object, Object, Object, Object> next;
					try {
						next = op.put(VALUE_TYPE_KEY, transform.getTargetType())//
							.interpret(transformType);
					} catch (QonfigInterpretationException e) {
						throw new QonfigEvaluationException("Could not interpret operation " + op.toString() + " as a transformation from "
							+ transform.getTargetType() + " via " + transformType.getName(), e, e.getPosition(), e.getErrorLength());
					} catch (CheckedExceptionWrapper e) {
						if (e.getCause() instanceof QonfigException) {
							throw new QonfigEvaluationException(
								"Could not interpret operation " + op.toString() + " as a transformation from " + transform.getTargetType()
								+ " via " + transformType.getName(),
								e.getCause(), ((QonfigException) e.getCause()).getPosition(),
								((QonfigException) e.getCause()).getErrorLength());
						} else
							throw new QonfigEvaluationException("Could not interpret operation " + op.toString()
							+ " as a transformation from " + transform.getTargetType() + " via " + transformType.getName(),
							e.getCause(), op.getElement().getPositionInFile(), 0);
					} catch (RuntimeException e) {
						throw new QonfigEvaluationException("Could not interpret operation " + op.toString() + " as a transformation from "
							+ transform.getTargetType() + " via " + transformType.getName(), e.getCause(),
							op.getElement().getPositionInFile(), 0);
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
					public Object get(ModelSetInstance models) throws QonfigEvaluationException {
						return fTransform.transform(//
							firstStep.get(models), models);
					}

					@Override
					public Object forModelCopy(Object value, ModelSetInstance sourceModels, ModelSetInstance newModels)
						throws QonfigEvaluationException {
						Object firstStepS = firstStep.get(sourceModels);
						Object firstStepN = firstStep.get(newModels);
						if (firstStepS != firstStepN || fTransform.isDifferent(sourceModels, newModels))
							return fTransform.transform(//
								firstStepN, newModels);
						else
							return value;
					}

					@Override
					public BetterList<ValueContainer<?, ?>> getCores() throws QonfigEvaluationException {
						return firstStep.getCores();
					}
				};
			}
		};
	}

	/**
	 * A transformer capable of handling a specific transformation of one observable structure to another
	 *
	 * @param <M1> The model type of the source observable
	 * @param <MV1> The type of the source observable
	 * @param <M2> The model type of the transformed observable
	 * @param <MV2> The type of the transformed observable
	 */
	public interface ObservableStructureTransform<M1, MV1 extends M1, M2, MV2 extends M2> {
		/** @return The type of the transformed observable */
		ModelInstanceType<M2, MV2> getTargetType();

		/**
		 * @param source The source observable
		 * @param models The models to use for the transformation
		 * @return The transformed observable
		 */
		MV2 transform(MV1 source, ModelSetInstance models) throws QonfigEvaluationException;

		/**
		 * Helps support the {@link ValueContainer#forModelCopy(Object, ModelSetInstance, ModelSetInstance)} method
		 *
		 * @param sourceModels The source model instance
		 * @param newModels The new model instance
		 * @return Whether observables produced by this transform would be different between the two models
		 */
		boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws QonfigEvaluationException;

		/**
		 * @param <M0> The original source model type
		 * @param <MV0> The original source type
		 * @param previous The original transformation which this transform comes after
		 * @return A transformation capable of transforming a source observable structure for <code>previous</code> to this transformation's
		 *         structure
		 */
		default <M0, MV0 extends M0> ObservableStructureTransform<M0, MV0, M2, MV2> after(
			ObservableStructureTransform<M0, MV0, M1, MV1> previous) {
			ObservableStructureTransform<M1, MV1, M2, MV2> next = this;
			return new ObservableStructureTransform<M0, MV0, M2, MV2>() {
				@Override
				public ModelInstanceType<M2, MV2> getTargetType() {
					return next.getTargetType();
				}

				@Override
				public MV2 transform(MV0 source, ModelSetInstance models) throws QonfigEvaluationException {
					MV1 intermediate = previous.transform(source, models);
					return next.transform(intermediate, models);
				}

				@Override
				public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws QonfigEvaluationException {
					return previous.isDifferent(sourceModels, newModels) || next.isDifferent(sourceModels, newModels);
				}

				@Override
				public String toString() {
					return previous + "->" + next;
				}
			};
		}

		/**
		 * @param <M> The observable model type
		 * @param <MV> The observable type
		 * @param type The observable type
		 * @return A transformation that is a no-op
		 */
		static <M, MV extends M> ObservableStructureTransform<M, MV, M, MV> unity(ModelInstanceType<M, MV> type) {
			return new ObservableStructureTransform<M, MV, M, MV>() {
				@Override
				public ModelInstanceType<M, MV> getTargetType() {
					return type;
				}

				@Override
				public MV transform(MV source, ModelSetInstance models) {
					return source;
				}

				@Override
				public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) {
					return false;
				}

				@Override
				public String toString() {
					return "init";
				}
			};
		}
	}

	/**
	 * @param <M> The model type
	 * @param modelType The model type class
	 * @return The {@link ModelType} whose {@link ModelType#modelType modelType} class is the given class
	 */
	protected <M> ModelType<M> getModelType(Class<M> modelType) {
		if (Observable.class.isAssignableFrom(modelType))
			return (ModelType<M>) ModelTypes.Event;
		else if (ObservableAction.class.isAssignableFrom(modelType))
			return (ModelType<M>) ModelTypes.Action;
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
		else if (ObservableSortedMap.class.isAssignableFrom(modelType))
			return (ModelType<M>) ModelTypes.SortedMap;
		else if (ObservableMap.class.isAssignableFrom(modelType))
			return (ModelType<M>) ModelTypes.Map;
		else if (ObservableSortedMultiMap.class.isAssignableFrom(modelType))
			return (ModelType<M>) ModelTypes.SortedMultiMap;
		else if (ObservableMultiMap.class.isAssignableFrom(modelType))
			return (ModelType<M>) ModelTypes.MultiMap;
		else
			return null;
	}

	/**
	 * Gets the transformation type for an observable model type. This will be used to query {@link AbstractQIS#interpret(Class)} to satisfy
	 * a transformation operation.
	 *
	 * @param modelType The model type to get the transform type for
	 * @return The type of transformation known to be able to handle observable structures of the given model type
	 */
	@SuppressWarnings("rawtypes")
	protected Class<? extends ObservableStructureTransform> getTransformFor(ModelType modelType) {
		if (modelType == ModelTypes.Event)
			return ObservableTransform.class;
		else if (modelType == ModelTypes.Action)
			return ActionTransform.class;
		else if (modelType == ModelTypes.Value)
			return ValueTransform.class;
		else if (modelType == ModelTypes.Collection || modelType == ModelTypes.Set || modelType == ModelTypes.SortedCollection
			|| modelType == ModelTypes.SortedSet)
			return CollectionTransform.class;
		else
			return null;
	}

	/**
	 * A transformer capable of transforming an {@link Observable}
	 *
	 * @param <S> The type of the observable
	 * @param <M> The model type of the target observable structure
	 * @param <MV> The type of the target observable structure
	 */

	public interface ObservableTransform<S, M, MV extends M> extends ObservableStructureTransform<Observable<?>, Observable<S>, M, MV> {
		/**
		 * Creates an observable->observable transformer
		 *
		 * @param <S> The type of the source observable
		 * @param <T> The type of the target observable
		 * @param type The type of the target observable
		 * @param transform A function to do the transformation
		 * @param difference Implementation for the {@link ObservableTransform#isDifferent(ModelSetInstance, ModelSetInstance)} method
		 * @param name The name for the transformation (just for {@link #toString()})
		 * @return The transformer
		 */
		static <S, T> ObservableTransform<S, Observable<?>, Observable<T>> of(TypeToken<T> type,
			ExBiFunction<Observable<S>, ModelSetInstance, Observable<T>, QonfigEvaluationException> transform,
			ExBiPredicate<ModelSetInstance, ModelSetInstance, QonfigEvaluationException> difference, Supplier<String> name) {
			return new ObservableTransform<S, Observable<?>, Observable<T>>() {
				@Override
				public ModelInstanceType<Observable<?>, Observable<T>> getTargetType() {
					return ModelTypes.Event.forType(type);
				}

				@Override
				public Observable<T> transform(Observable<S> source, ModelSetInstance models) throws QonfigEvaluationException {
					return transform.apply(source, models);
				}

				@Override
				public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws QonfigEvaluationException {
					return difference != null && difference.test(sourceModels, newModels);
				}

				@Override
				public String toString() {
					return name.get();
				}
			};
		}

		/**
		 * Creates an observable transformer
		 *
		 * @param <S> The type of the source observable
		 * @param <M> The model type of the target structure
		 * @param <MV> The type of the target structure
		 * @param type The type of the target structure
		 * @param transform A function to do the transformation
		 * @param difference Implementation for the {@link ObservableTransform#isDifferent(ModelSetInstance, ModelSetInstance)} method
		 * @param name The name for the transformation (just for {@link #toString()})
		 * @return The transformer
		 */
		static <S, M, MV extends M> ObservableTransform<S, M, MV> of(ModelInstanceType<M, MV> type,
			ExBiFunction<Observable<S>, ModelSetInstance, MV, QonfigEvaluationException> transform,
			ExBiPredicate<ModelSetInstance, ModelSetInstance, QonfigEvaluationException> difference, Supplier<String> name) {
			return new ObservableTransform<S, M, MV>() {
				@Override
				public ModelInstanceType<M, MV> getTargetType() {
					return type;
				}

				@Override
				public MV transform(Observable<S> source, ModelSetInstance models) throws QonfigEvaluationException {
					return transform.apply(source, models);
				}

				@Override
				public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws QonfigEvaluationException {
					return difference != null && difference.test(sourceModels, newModels);
				}

				@Override
				public String toString() {
					return name.get();
				}
			};
		}
	}

	/**
	 * A transformer capable of transforming an {@link ObservableAction}
	 *
	 * @param <S> The type of the action
	 * @param <M> The model type of the target observable structure
	 * @param <MV> The type of the target observable structure
	 */

	public interface ActionTransform<S, M, MV extends M>
	extends ObservableStructureTransform<ObservableAction<?>, ObservableAction<S>, M, MV> {
		/**
		 * Creates an action->action transformer
		 *
		 * @param <S> The type of the source action
		 * @param <T> The type of the target action
		 * @param type The type of the target action
		 * @param transform A function to do the transformation
		 * @param difference Implementation for the {@link ObservableTransform#isDifferent(ModelSetInstance, ModelSetInstance)} method
		 * @param name The name for the transformation (just for {@link #toString()})
		 * @return The transformer
		 */
		static <S, T> ActionTransform<S, ObservableAction<?>, ObservableAction<T>> of(TypeToken<T> type,
			ExBiFunction<ObservableAction<S>, ModelSetInstance, ObservableAction<T>, QonfigEvaluationException> transform,
			ExBiPredicate<ModelSetInstance, ModelSetInstance, QonfigEvaluationException> difference, Supplier<String> name) {
			return new ActionTransform<S, ObservableAction<?>, ObservableAction<T>>() {
				@Override
				public ModelInstanceType<ObservableAction<?>, ObservableAction<T>> getTargetType() {
					return ModelTypes.Action.forType(type);
				}

				@Override
				public ObservableAction<T> transform(ObservableAction<S> source, ModelSetInstance models) throws QonfigEvaluationException {
					return transform.apply(source, models);
				}

				@Override
				public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws QonfigEvaluationException {
					return difference != null && difference.test(sourceModels, newModels);
				}

				@Override
				public String toString() {
					return name.get();
				}
			};
		}

		/**
		 * Creates an action transformer
		 *
		 * @param <S> The type of the source action
		 * @param <M> The model type of the target structure
		 * @param <MV> The type of the target structure
		 * @param type The type of the target structure
		 * @param transform A function to do the transformation
		 * @param difference Implementation for the {@link ObservableTransform#isDifferent(ModelSetInstance, ModelSetInstance)} method
		 * @param name The name for the transformation (just for {@link #toString()})
		 * @return The transformer
		 */
		static <S, M, MV extends M> ActionTransform<S, M, MV> of(ModelInstanceType<M, MV> type,
			ExBiFunction<ObservableAction<S>, ModelSetInstance, MV, QonfigEvaluationException> transform,
			ExBiPredicate<ModelSetInstance, ModelSetInstance, QonfigEvaluationException> difference, Supplier<String> name) {
			return new ActionTransform<S, M, MV>() {
				@Override
				public ModelInstanceType<M, MV> getTargetType() {
					return type;
				}

				@Override
				public MV transform(ObservableAction<S> source, ModelSetInstance models) throws QonfigEvaluationException {
					return transform.apply(source, models);
				}

				@Override
				public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws QonfigEvaluationException {
					return difference != null && difference.test(sourceModels, newModels);
				}

				@Override
				public String toString() {
					return name.get();
				}
			};
		}
	}

	/**
	 * A transformer capable of transforming a {@link SettableValue}
	 *
	 * @param <S> The type of the value
	 * @param <M> The model type of the target observable structure
	 * @param <MV> The type of the target observable structure
	 */
	public interface ValueTransform<S, M, MV extends M> extends ObservableStructureTransform<SettableValue<?>, SettableValue<S>, M, MV> {
		/**
		 * Creates a value->value transformer
		 *
		 * @param <S> The type of the source value
		 * @param <T> The type of the target value
		 * @param type The type of the target value
		 * @param transform A function to do the transformation
		 * @param difference Implementation for the {@link ObservableTransform#isDifferent(ModelSetInstance, ModelSetInstance)} method
		 * @param name The name for the transformation (just for {@link #toString()})
		 * @return The transformer
		 */
		static <S, T> ValueTransform<S, SettableValue<?>, SettableValue<T>> of(TypeToken<T> type,
			ExBiFunction<SettableValue<S>, ModelSetInstance, SettableValue<T>, QonfigEvaluationException> transform,
			ExBiPredicate<ModelSetInstance, ModelSetInstance, QonfigEvaluationException> difference, Supplier<String> name) {
			return new ValueTransform<S, SettableValue<?>, SettableValue<T>>() {
				@Override
				public ModelInstanceType<SettableValue<?>, SettableValue<T>> getTargetType() {
					return ModelTypes.Value.forType(type);
				}

				@Override
				public SettableValue<T> transform(SettableValue<S> source, ModelSetInstance models) throws QonfigEvaluationException {
					return transform.apply(source, models);
				}

				@Override
				public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws QonfigEvaluationException {
					return difference != null && difference.test(sourceModels, newModels);
				}

				@Override
				public String toString() {
					return name.get();
				}
			};
		}

		/**
		 * Creates a value transformer
		 *
		 * @param <S> The type of the source value
		 * @param <M> The model type of the target structure
		 * @param <MV> The type of the target structure
		 * @param type The type of the target structure
		 * @param transform A function to do the transformation
		 * @param difference Implementation for the {@link ObservableTransform#isDifferent(ModelSetInstance, ModelSetInstance)} method
		 * @param name The name for the transformation (just for {@link #toString()})
		 * @return The transformer
		 */
		static <S, M, MV extends M> ValueTransform<S, M, MV> of(ModelInstanceType<M, MV> type,
			ExBiFunction<SettableValue<S>, ModelSetInstance, MV, QonfigEvaluationException> transform,
			ExBiPredicate<ModelSetInstance, ModelSetInstance, QonfigEvaluationException> difference, Supplier<String> name) {
			return new ValueTransform<S, M, MV>() {
				@Override
				public ModelInstanceType<M, MV> getTargetType() {
					return type;
				}

				@Override
				public MV transform(SettableValue<S> source, ModelSetInstance models) throws QonfigEvaluationException {
					return transform.apply(source, models);
				}

				@Override
				public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws QonfigEvaluationException {
					return difference != null && difference.test(sourceModels, newModels);
				}

				@Override
				public String toString() {
					return name.get();
				}
			};
		}
	}

	/**
	 * A transformer capable of transforming an {@link ObservableCollection}
	 *
	 * @param <S> The type of the collection
	 * @param <M> The model type of the target observable structure
	 * @param <MV> The type of the target observable structure
	 */
	public interface CollectionTransform<S, M, MV extends M>
	extends ObservableStructureTransform<ObservableCollection<?>, ObservableCollection<S>, M, MV> {
		/**
		 * Creates a collection->collection transformer
		 *
		 * @param <S> The type of the source collection
		 * @param <T> The type of the target collection
		 * @param modelType the sub-model type of the target collection
		 * @param type The type of the target collection
		 * @param transform A function to do the transformation
		 * @param difference Implementation for the {@link ObservableTransform#isDifferent(ModelSetInstance, ModelSetInstance)} method
		 * @param name The name for the transformation (just for {@link #toString()})
		 * @return The transformer
		 */
		static <S, T> FlowCollectionTransform<S, T, ObservableCollection<?>, ObservableCollection<T>> of(
			ModelType.SingleTyped<? extends ObservableCollection<?>> modelType, TypeToken<T> type,
				ExBiFunction<CollectionDataFlow<?, ?, S>, ModelSetInstance, CollectionDataFlow<?, ?, T>, QonfigEvaluationException> transform,
				ExBiPredicate<ModelSetInstance, ModelSetInstance, QonfigEvaluationException> difference, Supplier<String> name) {
			return new FlowCollectionTransform<S, T, ObservableCollection<?>, ObservableCollection<T>>() {
				@Override
				public ModelInstanceType<ObservableCollection<?>, ObservableCollection<T>> getTargetType() {
					return (ModelInstanceType<ObservableCollection<?>, ObservableCollection<T>>) modelType.forType(type);
				}

				@Override
				public CollectionDataFlow<?, ?, T> transformFlow(CollectionDataFlow<?, ?, S> source, ModelSetInstance models)
					throws QonfigEvaluationException {
					return transform.apply(source, models);
				}

				@Override
				public CollectionDataFlow<?, ?, T> transformToFlow(ObservableCollection<S> source, ModelSetInstance models)
					throws QonfigEvaluationException {
					return transform.apply(source.flow(), models);
				}

				@Override
				public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws QonfigEvaluationException {
					return difference != null && difference.test(sourceModels, newModels);
				}

				@Override
				public String toString() {
					return name.get();
				}
			};
		}

		/**
		 * Creates a collection transformer
		 *
		 * @param <S> The type of the source collection
		 * @param <M> The model type of the target structure
		 * @param <MV> The type of the target structure
		 * @param type The type of the target structure
		 * @param transform A function to do the transformation
		 * @param difference Implementation for the {@link ObservableTransform#isDifferent(ModelSetInstance, ModelSetInstance)} method
		 * @param name The name for the transformation (just for {@link #toString()})
		 * @return The transformer
		 */
		static <S, M, MV extends M> CollectionTransform<S, M, MV> of(ModelInstanceType<M, MV> type,
			ExBiFunction<ObservableCollection<S>, ModelSetInstance, MV, QonfigEvaluationException> transform,
			ExBiPredicate<ModelSetInstance, ModelSetInstance, QonfigEvaluationException> difference, Supplier<String> name) {
			return new CollectionTransform<S, M, MV>() {
				@Override
				public ModelInstanceType<M, MV> getTargetType() {
					return type;
				}

				@Override
				public MV transform(ObservableCollection<S> source, ModelSetInstance models) throws QonfigEvaluationException {
					return transform.apply(source, models);
				}

				@Override
				public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws QonfigEvaluationException {
					return difference != null && difference.test(sourceModels, newModels);
				}

				@Override
				public String toString() {
					return name.get();
				}
			};
		}
	}

	/**
	 * A transformer capable of transforming an {@link ObservableCollection} into another {@link ObservableCollection}
	 *
	 * @param <M1> The model type of the source collection
	 * @param <MV1> The model instance type of the target collection
	 * @param <S> The type of the source collection
	 * @param <T> The type of the target collection
	 * @param <M> The model type of the target observable structure
	 * @param <MV> The type of the target observable structure
	 */
	public interface FlowTransform<M1, MV1 extends M1, S, T, M extends ObservableCollection<?>, MV extends M>
	extends ObservableStructureTransform<M1, MV1, M, MV> {
		/**
		 * Transforms a collection flow
		 *
		 * @param source The source flow
		 * @param models The models to do the transformation
		 * @return The transformed flow
		 */
		CollectionDataFlow<?, ?, T> transformFlow(CollectionDataFlow<?, ?, S> source, ModelSetInstance models)
			throws QonfigEvaluationException;

		/**
		 * Transforms a source observable structure into a transformed flow
		 *
		 * @param source The source observable structure
		 * @param models The models to do the transformation
		 * @return The transformed flow
		 */
		CollectionDataFlow<?, ?, T> transformToFlow(MV1 source, ModelSetInstance models) throws QonfigEvaluationException;

		@Override
		default MV transform(MV1 source, ModelSetInstance models) throws QonfigEvaluationException {
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
					public CollectionDataFlow<?, ?, T> transformToFlow(MV0 source, ModelSetInstance models)
						throws QonfigEvaluationException {
						CollectionDataFlow<?, ?, S> sourceFlow = prevTCT.transformToFlow(source, models);
						return next.transformFlow(sourceFlow, models);
					}

					@Override
					public CollectionDataFlow<?, ?, T> transformFlow(CollectionDataFlow<?, ?, Object> source, ModelSetInstance models)
						throws QonfigEvaluationException {
						CollectionDataFlow<?, ?, S> sourceFlow = prevTCT.transformFlow(source, models);
						return next.transformFlow(sourceFlow, models);
					}

					@Override
					public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws QonfigEvaluationException {
						return previous.isDifferent(sourceModels, newModels) || next.isDifferent(sourceModels, newModels);
					}

					@Override
					public String toString() {
						return previous + "->" + next;
					}
				};
			} else
				return ObservableStructureTransform.super.after(previous);
		}
	}

	/**
	 * A {@link CollectionTransform} that is also a {@link FlowTransform}. A {@link CollectionTransform} is inefficient at
	 * chain-transforming collections and a {@link FlowTransform} is capable of transforming source observable structures other than
	 * collections, but implementations of collection transform operations must return {@link CollectionTransform}. Generally, if a
	 * collection transform operation produces a collection, an instance of this interface should be returned.
	 *
	 * @param <S> The type of the collection
	 * @param <T> The type of the target collection
	 * @param <M> The model type of the target observable structure
	 * @param <MV> The type of the target observable structure
	 */

	public interface FlowCollectionTransform<S, T, M extends ObservableCollection<?>, MV extends M>
	extends CollectionTransform<S, M, MV>, FlowTransform<ObservableCollection<?>, ObservableCollection<S>, S, T, M, MV> {
	}

	// Event transform

	private <T> ObservableTransform<T, Observable<?>, Observable<T>> noInitObservable(ExpressoQIS op) throws QonfigInterpretationException {
		ModelInstanceType.SingleTyped<Observable<?>, T, Observable<T>> sourceType;
		sourceType = (ModelInstanceType.SingleTyped<Observable<?>, T, Observable<T>>) op.get(VALUE_TYPE_KEY);
		return ObservableTransform.of(sourceType.getValueType(), (source, models) -> source.noInit(), null, () -> "noInit");
	}

	private <T> ObservableTransform<T, Observable<?>, Observable<T>> skipObservable(ExpressoQIS op) throws QonfigInterpretationException {
		ModelInstanceType.SingleTyped<Observable<?>, T, Observable<T>> sourceType;
		sourceType = (ModelInstanceType.SingleTyped<Observable<?>, T, Observable<T>>) op.get(VALUE_TYPE_KEY);
		int times = Integer.parseInt(op.getAttributeText("times"));
		return ObservableTransform.of(sourceType.getValueType(), (source, models) -> source.skip(times), null, () -> "skip(" + times + ")");
	}

	private <T> ObservableTransform<T, Observable<?>, Observable<T>> takeObservable(ExpressoQIS op) throws QonfigInterpretationException {
		ModelInstanceType.SingleTyped<Observable<?>, T, Observable<T>> sourceType;
		sourceType = (ModelInstanceType.SingleTyped<Observable<?>, T, Observable<T>>) op.get(VALUE_TYPE_KEY);
		int times = Integer.parseInt(op.getAttributeText("times"));
		return ObservableTransform.of(sourceType.getValueType(), (source, models) -> source.take(times), null, () -> "take(" + times + ")");
	}

	private <T> ObservableTransform<T, Observable<?>, Observable<T>> takeUntilObservable(ExpressoQIS op)
		throws QonfigInterpretationException {
		ModelInstanceType.SingleTyped<Observable<?>, T, Observable<T>> sourceType;
		sourceType = (ModelInstanceType.SingleTyped<Observable<?>, T, Observable<T>>) op.get(VALUE_TYPE_KEY);
		QonfigExpression2 untilX = op.getAttributeExpression("until");
		ValueContainer<Observable<?>, Observable<?>> until;
		try {
			until = untilX.evaluate(ModelTypes.Event.any());
		} catch (QonfigEvaluationException e) {
			throw new QonfigInterpretationException("Could not parse until", e, e.getPosition(), e.getErrorLength());
		}
		return ObservableTransform.of(sourceType.getValueType(), (source, models) -> source.takeUntil(until.get(models)),
			(sourceModels, newModels) -> until.get(sourceModels) != until.get(newModels), () -> "takeUntil(" + until + ")");
	}

	private <S, T> ObservableTransform<S, Observable<?>, Observable<T>> mapObservableTo(ExpressoQIS op)
		throws QonfigInterpretationException {
		ModelInstanceType.SingleTyped<SettableValue<?>, S, SettableValue<S>> sourceType;
		sourceType = (ModelInstanceType.SingleTyped<SettableValue<?>, S, SettableValue<S>>) op.get(VALUE_TYPE_KEY);

		String sourceAs = op.getAttributeText("source-as");
		ObservableModelSet.Builder wrappedBuilder = op.getExpressoEnv().getModels().wrap("mapModel");
		RuntimeValuePlaceholder<SettableValue<?>, SettableValue<S>> sourcePlaceholder = wrappedBuilder.withRuntimeValue(sourceAs,
			ModelTypes.Value.forType(sourceType.getValueType()));
		ObservableModelSet wrapped = wrappedBuilder.build();

		QonfigExpression2 mapX = op.getAttributeExpression("map");
		ValueContainer<SettableValue<?>, SettableValue<T>> map;
		TypeToken<T> targetType;
		try {
			map = mapX.evaluate((ModelInstanceType<SettableValue<?>, SettableValue<T>>) (ModelInstanceType<?, ?>) ModelTypes.Value.any(),
				op.getExpressoEnv().with(wrapped, null));
			targetType = (TypeToken<T>) map.getType().getType(0).resolveType(SettableValue.class.getTypeParameters()[0]);
		} catch (QonfigEvaluationException e) {
			throw new QonfigInterpretationException("Could not parse map", e, e.getPosition(), e.getErrorLength());
		}
		return ObservableTransform.of(targetType, (source, models) -> {
			SettableValue<S> sourceV = SettableValue.build(sourceType.getValueType()).build();
			ModelSetInstance wrappedModel = wrapped.createInstance(models.getUntil()).withAll(models)//
				.with(sourcePlaceholder, sourceV)//
				.build();
			SettableValue<T> mappedV = map.get(wrappedModel);
			return source.map(s -> {
				sourceV.set(s, null);
				return mappedV.get();
			});
		}, null, () -> "map(" + map + ")");
	}

	private <T> ObservableTransform<T, Observable<?>, Observable<T>> filterObservable(ExpressoQIS op) throws QonfigInterpretationException {
		ModelInstanceType.SingleTyped<Observable<?>, T, Observable<T>> sourceType;
		sourceType = (ModelInstanceType.SingleTyped<Observable<?>, T, Observable<T>>) op.get(VALUE_TYPE_KEY);
		String sourceAs = op.getAttributeText("source-as");
		ObservableModelSet.Builder wrappedBuilder = op.getExpressoEnv().getModels().wrap("filterModel");
		RuntimeValuePlaceholder<SettableValue<?>, SettableValue<T>> sourcePlaceholder = wrappedBuilder.withRuntimeValue(sourceAs,
			ModelTypes.Value.forType(sourceType.getValueType()));
		ObservableModelSet wrapped = wrappedBuilder.build();

		ValueContainer<SettableValue<?>, SettableValue<String>> test = parseFilter(op.getAttributeExpression("test"),
			op.getExpressoEnv().with(wrapped, null), false);
		return ObservableTransform.of(sourceType.getValueType(), (source, models) -> {
			SettableValue<T> sourceV = SettableValue.build(sourceType.getValueType()).build();
			ModelSetInstance wrappedMSI = wrapped.createInstance(models.getUntil()).withAll(models)//
				.with(sourcePlaceholder, sourceV)//
				.build();
			SettableValue<String> message = test.get(wrappedMSI);
			return source.filter(v -> {
				sourceV.set(v, null);
				return message.get() == null;
			});
		}, null, () -> "filter(" + test + ")");
	}

	private static ValueContainer<SettableValue<?>, SettableValue<String>> parseFilter(QonfigExpression2 testX, ExpressoEnv env,
		boolean preferMessage) throws QonfigInterpretationException {
		ValueContainer<SettableValue<?>, SettableValue<String>> test;
		try {
			if (preferMessage)
				test = testX.evaluate(ModelTypes.Value.forType(String.class), env);
			else {
				test = testX.evaluate(ModelTypes.Value.forType(boolean.class), env)//
					.map(ModelTypes.Value.forType(String.class),
						bv -> SettableValue.asSettable(bv.map(String.class, b -> b ? null : "Not allowed"), __ -> "Not settable"));
			}
		} catch (QonfigEvaluationException e) {
			try {
				if (preferMessage) {
					test = testX.evaluate(ModelTypes.Value.forType(boolean.class), env)//
						.map(ModelTypes.Value.forType(String.class),
							bv -> SettableValue.asSettable(bv.map(String.class, b -> b ? null : "Not allowed"), __ -> "Not settable"));
				} else
					test = testX.evaluate(ModelTypes.Value.forType(String.class), env);
			} catch (QonfigEvaluationException e2) {
				throw new QonfigInterpretationException("Could not interpret '" + testX + "' as a String or a boolean", e, e.getPosition(),
					e.getErrorLength());
			}
		}
		return test;
	}

	private <S, T> ObservableTransform<S, Observable<?>, Observable<T>> filterObservableByType(ExpressoQIS op)
		throws QonfigInterpretationException {
		QonfigExpression2 typeX = op.getAttributeExpression("type");
		ValueContainer<SettableValue<?>, SettableValue<Class<T>>> typeC;
		TypeToken<T> targetType;
		try {
			typeC = typeX.evaluate(ModelTypes.Value.forType(TypeTokens.get().keyFor(Class.class).wildCard()));
			targetType = (TypeToken<T>) typeC.getType().getType(0).resolveType(Class.class.getTypeParameters()[0]);
		} catch (QonfigEvaluationException e) {
			throw new QonfigInterpretationException("Could not parse type", e, e.getPosition(), e.getErrorLength());
		}
		return ObservableTransform.of(targetType, (source, models) -> {
			Class<T> type = typeC.get(models).get();
			return source.filter(type);
		}, null, () -> "filter(" + targetType + ")");
	}

	// Action transform

	private <T> ActionTransform<T, ObservableAction<?>, ObservableAction<T>> disabledAction(ExpressoQIS op)
		throws QonfigInterpretationException {
		ModelInstanceType.SingleTyped<ObservableAction<?>, T, ObservableAction<T>> sourceType;
		sourceType = (ModelInstanceType.SingleTyped<ObservableAction<?>, T, ObservableAction<T>>) op.get(VALUE_TYPE_KEY);
		QonfigExpression2 enabledX = op.getAttributeExpression("with");
		ValueContainer<SettableValue<?>, SettableValue<String>> enabled;
		try {
			enabled = enabledX.evaluate(ModelTypes.Value.forType(String.class), op.getExpressoEnv());
		} catch (QonfigEvaluationException e) {
			throw new QonfigInterpretationException("Could not parse enabled", e, e.getPosition(), e.getErrorLength());
		}
		return ActionTransform.of(sourceType.getValueType(), (source, models) -> {
			return source.disableWith(enabled.get(models));
		}, (sourceModels, newModels) -> enabled.get(sourceModels) != enabled.get(newModels), () -> "disable(" + enabled + ")");
	}

	// Value transform

	private <T> ValueTransform<T, SettableValue<?>, SettableValue<T>> disabledValue(ExpressoQIS op) throws QonfigInterpretationException {
		ModelInstanceType.SingleTyped<SettableValue<?>, T, SettableValue<T>> sourceType;
		sourceType = (ModelInstanceType.SingleTyped<SettableValue<?>, T, SettableValue<T>>) op.get(VALUE_TYPE_KEY);
		QonfigExpression2 enabledX = op.getAttributeExpression("with");
		ValueContainer<SettableValue<?>, SettableValue<String>> enabled;
		try {
			enabled = enabledX.evaluate(ModelTypes.Value.forType(String.class), op.getExpressoEnv());
		} catch (QonfigEvaluationException e) {
			throw new QonfigInterpretationException("Could not parse enabled", e, e.getPosition(), e.getErrorLength());
		}
		return ValueTransform.of(sourceType.getValueType(), (source, models) -> source.disableWith(enabled.get(models)),
			(sourceModels, newModels) -> enabled.get(sourceModels) != enabled.get(newModels), () -> "disable(" + enabled + ")");
	}

	private <T> ValueTransform<T, SettableValue<?>, SettableValue<T>> filterAcceptValue(ExpressoQIS op)
		throws QonfigInterpretationException {
		ModelInstanceType.SingleTyped<SettableValue<?>, T, SettableValue<T>> sourceType;
		sourceType = (ModelInstanceType.SingleTyped<SettableValue<?>, T, SettableValue<T>>) op.get(VALUE_TYPE_KEY);
		String sourceAs = op.getAttributeText("source-as");
		ObservableModelSet.Builder wrappedBuilder = op.getExpressoEnv().getModels().wrap("filterAcceptModel");
		RuntimeValuePlaceholder<SettableValue<?>, SettableValue<T>> sourcePlaceholder = wrappedBuilder.withRuntimeValue(sourceAs,
			sourceType);
		ObservableModelSet wrapped = wrappedBuilder.build();

		ValueContainer<SettableValue<?>, SettableValue<String>> test = parseFilter(op.getAttributeExpression("test"),
			op.getExpressoEnv().with(wrapped, null), true);
		return ValueTransform.of(sourceType.getValueType(), (source, models) -> {
			SettableValue<T> sourceV = SettableValue.build(sourceType.getValueType()).build();
			ModelSetInstance wrappedMSI = wrapped.createInstance(models.getUntil()).withAll(models)//
				.with(sourcePlaceholder, sourceV)//
				.build();
			SettableValue<String> message = test.get(wrappedMSI);
			return source.filterAccept(v -> {
				sourceV.set(v, null);
				return message.get();
			});
		}, null, () -> "filterAccept(" + test + ")");
	}

	private <S, T> ValueTransform<S, SettableValue<?>, SettableValue<T>> mapValueTo(ExpressoQIS op) throws QonfigInterpretationException {
		ModelInstanceType.SingleTyped<SettableValue<?>, S, SettableValue<S>> sourceType = (ModelInstanceType.SingleTyped<SettableValue<?>, S, SettableValue<S>>) op
			.get(VALUE_TYPE_KEY);
		ParsedTransformation<S, T> ptx;
		try {
			ptx = mapTransformation(sourceType.getValueType(), op);
		} catch (QonfigEvaluationException e) {
			throw new QonfigInterpretationException("Could not parse map", e, e.getPosition(), e.getErrorLength());
		}
		if (ptx.isReversible()) {
			return ValueTransform.of(ptx.getTargetType(), (v, models) -> v.transformReversible(ptx.getTargetType(),
				tx -> {
					try {
						return (Transformation.ReversibleTransformation<S, T>) ptx.transform(tx, models);
					} catch (QonfigEvaluationException e) {
						throw new CheckedExceptionWrapper(e);
					}
				}), ptx::isDifferent, ptx::toString);
		} else {
			return ValueTransform.of(ptx.getTargetType(), (v, models) -> {
				ObservableValue<T> value = v.transform(ptx.getTargetType(), tx -> {
					try {
						return ptx.transform(tx, models);
					} catch (QonfigEvaluationException e) {
						throw new CheckedExceptionWrapper(e);
					}
				});
				return SettableValue.asSettable(value, __ -> "No reverse configured for " + op.toString());
			}, ptx::isDifferent, ptx::toString);
		}
	}

	private <T> ValueTransform<T, SettableValue<?>, SettableValue<T>> refreshValue(ExpressoQIS op) throws QonfigInterpretationException {
		ModelInstanceType.SingleTyped<SettableValue<?>, T, SettableValue<T>> sourceType = (ModelInstanceType.SingleTyped<SettableValue<?>, T, SettableValue<T>>) op
			.get(VALUE_TYPE_KEY);
		QonfigExpression2 onX = op.getAttributeExpression("on");
		ValueContainer<Observable<?>, Observable<?>> refresh;
		try {
			refresh = onX.evaluate(ModelTypes.Event.any(), op.getExpressoEnv());
		} catch (QonfigEvaluationException e) {
			throw new QonfigInterpretationException("Could not parse refresh", e, e.getPosition(), e.getErrorLength());
		}
		return ValueTransform.of(sourceType, (v, models) -> v.refresh(refresh.get(models)),
			(sourceModels, newModels) -> refresh.get(sourceModels) != refresh.get(newModels), () -> "refresh(" + refresh + ")");
	}

	private <T> ValueTransform<T, SettableValue<?>, SettableValue<T>> unmodifiableValue(ExpressoQIS op)
		throws QonfigInterpretationException {
		ModelInstanceType.SingleTyped<SettableValue<?>, T, SettableValue<T>> sourceType = (ModelInstanceType.SingleTyped<SettableValue<?>, T, SettableValue<T>>) op
			.get(VALUE_TYPE_KEY);
		boolean allowUpdates = op.getAttribute("allow-updates", Boolean.class);
		if (!allowUpdates) {
			return ValueTransform.of(sourceType, (v, models) -> v.disableWith(ObservableValue.of(StdMsg.UNSUPPORTED_OPERATION)), null,
				() -> "unmodifiable(" + allowUpdates + ")");
		} else {
			return ValueTransform.of(sourceType, (v, models) -> {
				return v.filterAccept(input -> {
					if (v.get() == input)
						return null;
					else
						return StdMsg.ILLEGAL_ELEMENT;
				});
			}, null, () -> "unmodifiable(" + allowUpdates + ")");
		}
	}

	private <S, T> ValueTransform<S, ?, ?> flattenValue(ExpressoQIS op) throws QonfigInterpretationException {
		System.err.println("WARNING: Value.flatten is not fully implemented!!  Some options may be ignored.");
		ModelInstanceType.SingleTyped<SettableValue<?>, S, SettableValue<S>> sourceType;
		sourceType = (ModelInstanceType.SingleTyped<SettableValue<?>, S, SettableValue<S>>) op.get(VALUE_TYPE_KEY);
		TypeToken<T> resultType;
		Class<S> rawType = TypeTokens.getRawType(sourceType.getValueType());
		ExpressoQIS sort = op.forChildren("sort").peekFirst();
		if (SettableValue.class.isAssignableFrom(rawType)) {
			return ValueTransform.of((TypeToken<T>) sourceType.getValueType().resolveType(SettableValue.class.getTypeParameters()[0]),
				(v, models) -> SettableValue.flatten((ObservableValue<SettableValue<T>>) (ObservableValue<?>) v), null, () -> "flatValue");
		} else if (ObservableValue.class.isAssignableFrom(rawType)) {
			return ValueTransform.of((TypeToken<T>) sourceType.getValueType().resolveType(ObservableValue.class.getTypeParameters()[0]),
				(v, models) -> SettableValue.flattenAsSettable((ObservableValue<ObservableValue<T>>) (ObservableValue<?>) v, () -> null),
				null, () -> "flatValue");
		} else if (ObservableSortedSet.class.isAssignableFrom(rawType) && sort != null) {
			ValueContainer<SettableValue<?>, SettableValue<Comparator<T>>> compare = sort.interpret(ValueContainer.class);
			resultType = (TypeToken<T>) sourceType.getValueType().resolveType(ObservableCollection.class.getTypeParameters()[0]);
			return ValueTransform.of(ModelTypes.SortedSet.forType(resultType), (value, models) -> ObservableSortedSet
				.flattenValue((ObservableValue<ObservableSortedSet<T>>) value, compare.get(models).get()), null, () -> "flatSortedSet");
		} else if (ObservableSortedCollection.class.isAssignableFrom(rawType) && sort != null) {
			ValueContainer<SettableValue<?>, SettableValue<Comparator<T>>> compare = sort.interpret(ValueContainer.class);
			resultType = (TypeToken<T>) sourceType.getValueType().resolveType(ObservableCollection.class.getTypeParameters()[0]);
			return ValueTransform.of(
				ModelTypes.SortedCollection.forType(resultType), (value, models) -> ObservableSortedCollection
				.flattenValue((ObservableValue<ObservableSortedCollection<T>>) value, compare.get(models).get()),
				null, () -> "flatSortedCollection");
		} else if (ObservableSet.class.isAssignableFrom(rawType)) {
			resultType = (TypeToken<T>) sourceType.getValueType().resolveType(ObservableCollection.class.getTypeParameters()[0]);
			return ValueTransform.of(ModelTypes.Set.forType(resultType),
				(value, models) -> ObservableSet.flattenValue((ObservableValue<ObservableSet<T>>) value), null, () -> "flatSet");
		} else if (ObservableCollection.class.isAssignableFrom(rawType)) {
			resultType = (TypeToken<T>) sourceType.getValueType().resolveType(ObservableCollection.class.getTypeParameters()[0]);
			return ValueTransform.of(ModelTypes.Collection.forType(resultType),
				(value, models) -> ObservableCollection.flattenValue((ObservableValue<ObservableCollection<T>>) value), null,
				() -> "flatCollection");
		} else
			throw new QonfigInterpretationException("Cannot flatten value of type " + sourceType, op.getElement().getPositionInFile(), 0);
	}

	// Collection transform

	private <S, T> FlowCollectionTransform<S, T, ObservableCollection<?>, ObservableCollection<T>> mapCollectionTo(ExpressoQIS op)
		throws QonfigInterpretationException {
		ModelInstanceType.SingleTyped<? extends ObservableCollection<?>, S, ? extends ObservableCollection<S>> sourceType;
		sourceType = (ModelInstanceType.SingleTyped<? extends ObservableCollection<?>, S, ? extends ObservableCollection<S>>) op
			.get(VALUE_TYPE_KEY);

		ParsedTransformation<S, T> ptx;
		try {
			ptx = mapTransformation(sourceType.getValueType(), op);
		} catch (QonfigEvaluationException e) {
			throw new QonfigInterpretationException("Could not parse map", e, e.getPosition(), e.getErrorLength());
		}
		if (ptx.isReversible()) {
			if (sourceType.getModelType() == ModelTypes.SortedSet)
				return CollectionTransform.of(ModelTypes.SortedSet, ptx.getTargetType(), (c,
					models) -> ((ObservableCollection.DistinctSortedDataFlow<?, ?, S>) c).transformEquivalent(ptx.getTargetType(), tx -> {
						try {
							return (Transformation.ReversibleTransformation<S, T>) ptx.transform(tx, models);
						} catch (QonfigEvaluationException e) {
							throw new CheckedExceptionWrapper(e);
						}
					}), ptx::isDifferent, ptx::toString);
			else if (sourceType.getModelType() == ModelTypes.SortedCollection)
				return CollectionTransform.of(ModelTypes.SortedCollection, ptx.getTargetType(),
					(c, models) -> ((ObservableCollection.SortedDataFlow<?, ?, S>) c).transformEquivalent(ptx.getTargetType(), tx -> {
						try {
							return (Transformation.ReversibleTransformation<S, T>) ptx.transform(tx, models);
						} catch (QonfigEvaluationException e) {
							throw new CheckedExceptionWrapper(e);
						}
					}), ptx::isDifferent, ptx::toString);
			else if (sourceType.getModelType() == ModelTypes.Set)
				return CollectionTransform.of(ModelTypes.Set, ptx.getTargetType(),
					(c, models) -> ((ObservableCollection.DistinctDataFlow<?, ?, S>) c).transformEquivalent(ptx.getTargetType(), tx -> {
						try {
							return (Transformation.ReversibleTransformation<S, T>) ptx.transform(tx, models);
						} catch (QonfigEvaluationException e) {
							throw new CheckedExceptionWrapper(e);
						}
					}), ptx::isDifferent, ptx::toString);
			else
				return CollectionTransform.of(ModelTypes.Collection, ptx.getTargetType(),
					(c, models) -> c.transform(ptx.getTargetType(), tx -> {
						try {
							return (Transformation.ReversibleTransformation<S, T>) ptx.transform(tx, models);
						} catch (QonfigEvaluationException e) {
							throw new CheckedExceptionWrapper(e);
						}
					}), ptx::isDifferent, ptx::toString);
		} else {
			return CollectionTransform.of(ModelTypes.Collection, ptx.getTargetType(),
				(f, models) -> f.transform(ptx.getTargetType(), tx -> {
					try {
						return ptx.transform(tx, models);
					} catch (QonfigEvaluationException e) {
						throw new CheckedExceptionWrapper(e);
					}
				}), ptx::isDifferent, ptx::toString);
		}
	}

	private <T> FlowCollectionTransform<T, T, ObservableCollection<?>, ObservableCollection<T>> filterCollection(ExpressoQIS op)
		throws QonfigInterpretationException {
		ModelInstanceType.SingleTyped<ObservableCollection<?>, T, ObservableCollection<T>> sourceType;
		sourceType = (ModelInstanceType.SingleTyped<ObservableCollection<?>, T, ObservableCollection<T>>) op.get(VALUE_TYPE_KEY);
		String sourceAs = op.getAttributeText("source-as");
		ObservableModelSet.Builder wrappedBuilder = op.getExpressoEnv().getModels().wrap("filterModel");
		RuntimeValuePlaceholder<SettableValue<?>, SettableValue<T>> sourcePlaceholder = wrappedBuilder.withRuntimeValue(sourceAs,
			ModelTypes.Value.forType(sourceType.getValueType()));
		ObservableModelSet wrapped = wrappedBuilder.build();

		ValueContainer<SettableValue<?>, SettableValue<String>> test = parseFilter(op.getAttributeExpression("test"),
			op.getExpressoEnv().with(wrapped, null), false);
		return CollectionTransform.of(sourceType.getModelType(), sourceType.getValueType(), (source, models) -> {
			SettableValue<T> sourceV = SettableValue.build(sourceType.getValueType()).build();
			ModelSetInstance wrappedMSI = wrapped.createInstance(models.getUntil()).withAll(models)//
				.with(sourcePlaceholder, sourceV)//
				.build();
			SettableValue<String> message = test.get(wrappedMSI);
			return source.filter(v -> {
				sourceV.set(v, null);
				return message.get();
			});
		}, null, () -> "filter(" + test + ")");
	}

	private <S, T> FlowCollectionTransform<T, T, ObservableCollection<?>, ObservableCollection<T>> filterCollectionByType(ExpressoQIS op)
		throws QonfigInterpretationException {
		ModelInstanceType.SingleTyped<ObservableCollection<?>, S, ObservableCollection<S>> sourceType;
		sourceType = (ModelInstanceType.SingleTyped<ObservableCollection<?>, S, ObservableCollection<S>>) op.get(VALUE_TYPE_KEY);

		QonfigExpression2 typeX = op.getAttributeExpression("type");
		ValueContainer<SettableValue<?>, SettableValue<Class<T>>> typeC;
		TypeToken<T> targetType;
		try {
			typeC = typeX.evaluate(ModelTypes.Value.forType(TypeTokens.get().keyFor(Class.class).wildCard()), op.getExpressoEnv());
			targetType = (TypeToken<T>) typeC.getType().getType(0).resolveType(Class.class.getTypeParameters()[0]);
		} catch (QonfigEvaluationException e) {
			throw new QonfigInterpretationException("Could not parse type", e, e.getPosition(), e.getErrorLength());
		}
		return CollectionTransform.of(sourceType.getModelType(), targetType, (source, models) -> {
			Class<T> type = typeC.get(models).get();
			return source.filter(type);
		}, null, () -> "filter(" + targetType + ")");
	}

	private <T> FlowCollectionTransform<T, T, ObservableCollection<?>, ObservableCollection<T>> reverseCollection(ExpressoQIS op)
		throws QonfigInterpretationException {
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

			@Override
			public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) {
				return false;
			}

			@Override
			public String toString() {
				return "reverse";
			}
		};
	}

	private <T> FlowCollectionTransform<T, T, ObservableCollection<?>, ObservableCollection<T>> refreshCollection(ExpressoQIS op)
		throws QonfigInterpretationException {
		ModelInstanceType.SingleTyped<? extends ObservableCollection<?>, T, ? extends ObservableCollection<T>> sourceType;
		sourceType = (ModelInstanceType.SingleTyped<? extends ObservableCollection<?>, T, ? extends ObservableCollection<T>>) op
			.get(VALUE_TYPE_KEY);

		QonfigExpression2 refreshX = op.getAttributeExpression("on");
		ValueContainer<Observable<?>, Observable<?>> refreshV;
		try {
			refreshV = refreshX.evaluate(ModelTypes.Event.any(), op.getExpressoEnv());
		} catch (QonfigEvaluationException e) {
			throw new QonfigInterpretationException("Could not parse refresh", e, e.getPosition(), e.getErrorLength());
		}
		return CollectionTransform.of(sourceType.getModelType(), sourceType.getValueType(), (f, models) -> {
			Observable<?> refresh = refreshV.get(models);
			return f.refresh(refresh);
		}, (sourceModels, newModels) -> refreshV.get(sourceModels) != refreshV.get(newModels), () -> "refresh(" + refreshV + ")");
	}

	private <T> FlowCollectionTransform<T, T, ObservableCollection<?>, ObservableCollection<T>> refreshCollectionEach(ExpressoQIS op)
		throws QonfigInterpretationException {
		ModelInstanceType.SingleTyped<? extends ObservableCollection<?>, T, ? extends ObservableCollection<T>> sourceType;
		sourceType = (ModelInstanceType.SingleTyped<? extends ObservableCollection<?>, T, ? extends ObservableCollection<T>>) op
			.get(VALUE_TYPE_KEY);

		String sourceAs = op.getAttributeText("source-as");
		ObservableModelSet.Builder refreshModelBuilder = op.getExpressoEnv().getModels().wrap("refreshEachModel");
		RuntimeValuePlaceholder<SettableValue<?>, SettableValue<T>> sourcePlaceholder = refreshModelBuilder.withRuntimeValue(sourceAs,
			ModelTypes.Value.forType(sourceType.getValueType()));
		ObservableModelSet refreshModel = refreshModelBuilder.build();
		QonfigExpression2 refreshX = op.getAttributeExpression("on");
		ValueContainer<SettableValue<?>, SettableValue<Observable<?>>> refreshV;
		try {
			refreshV = refreshX.evaluate(ModelTypes.Value.forType(TypeTokens.get().keyFor(Observable.class).wildCard()),
				op.getExpressoEnv().with(refreshModel, null));
		} catch (QonfigEvaluationException e) {
			throw new QonfigInterpretationException("Could not parse refresh", e, e.getPosition(), e.getErrorLength());
		}
		return CollectionTransform.of(sourceType.getModelType(), sourceType.getValueType(), (f, models) -> {
			SettableValue<T> sourceValue = SettableValue.build(sourceType.getValueType()).build();
			ModelSetInstance refreshModelInstance = refreshModel.createInstance(models.getUntil()).withAll(models)//
				.with(sourcePlaceholder, sourceValue)//
				.build();
			SettableValue<Observable<?>> refresh = refreshV.get(refreshModelInstance);
			return f.refreshEach(v -> {
				sourceValue.set(v, null);
				return refresh.get();
			});
		}, null, () -> "refresh(" + refreshV + ")");
	}

	private <T> FlowCollectionTransform<T, T, ObservableCollection<?>, ObservableCollection<T>> distinctCollection(ExpressoQIS op)
		throws QonfigInterpretationException {
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
			}, null, () -> "distinct");
		} else {
			return CollectionTransform.of(ModelTypes.Set, sourceType.getValueType(),
				(f, models) -> f.distinct(opts -> opts.useFirst(useFirst).preserveSourceOrder(preserveSourceOrder)), null,
				() -> "distinct");
		}
	}

	private <T> FlowCollectionTransform<T, T, ObservableCollection<?>, ObservableCollection<T>> sortedCollection(ExpressoQIS op)
		throws QonfigInterpretationException {
		ModelInstanceType.SingleTyped<? extends ObservableCollection<?>, T, ? extends ObservableCollection<T>> sourceType;
		sourceType = (ModelInstanceType.SingleTyped<? extends ObservableCollection<?>, T, ? extends ObservableCollection<T>>) op
			.get(VALUE_TYPE_KEY);

		ValueCreator<SettableValue<?>, SettableValue<Comparator<T>>> compare = op.put(VALUE_TYPE_KEY, sourceType.getValueType())
			.interpret(ValueCreator.class);
		return CollectionTransform.of(ModelTypes.SortedCollection, sourceType.getValueType(), (f, models) -> {
			Comparator<T> comparator = compare.createContainer().get(models).get();
			return f.sorted(comparator);
		}, null, () -> "sorted");
	}

	private <T> FlowCollectionTransform<T, T, ObservableCollection<?>, ObservableCollection<T>> withCollectionEquivalence(ExpressoQIS op)
		throws QonfigInterpretationException {
		throw new QonfigInterpretationException("Not yet implemented", op.getElement().getPositionInFile(), 0); // TODO
	}

	private <T> FlowCollectionTransform<T, T, ObservableCollection<?>, ObservableCollection<T>> unmodifiableCollection(ExpressoQIS op)
		throws QonfigInterpretationException {
		ModelInstanceType.SingleTyped<? extends ObservableCollection<?>, T, ? extends ObservableCollection<T>> sourceType;
		sourceType = (ModelInstanceType.SingleTyped<? extends ObservableCollection<?>, T, ? extends ObservableCollection<T>>) op
			.get(VALUE_TYPE_KEY);

		boolean allowUpdates = op.getAttribute("allow-updates", boolean.class);
		return CollectionTransform.of(ModelTypes.Collection, sourceType.getValueType(), (f, models) -> f.unmodifiable(allowUpdates), null,
			() -> "unmodifiable(" + allowUpdates + ")");
	}

	private <T> FlowCollectionTransform<T, T, ObservableCollection<?>, ObservableCollection<T>> filterCollectionModification(ExpressoQIS op)
		throws QonfigInterpretationException {
		throw new QonfigInterpretationException("Not yet implemented", op.getElement().getPositionInFile(), 0); // TODO
	}

	private <S, T> FlowCollectionTransform<S, T, ObservableCollection<?>, ObservableCollection<T>> mapEquivalentCollectionTo(ExpressoQIS op)
		throws QonfigInterpretationException {
		throw new QonfigInterpretationException("Not yet implemented", op.getElement().getPositionInFile(), 0); // TODO
	}

	private <S, T> FlowCollectionTransform<S, T, ObservableCollection<?>, ObservableCollection<T>> flattenCollection(ExpressoQIS op)
		throws QonfigInterpretationException {
		ModelInstanceType.SingleTyped<? extends ObservableCollection<?>, T, ? extends ObservableCollection<T>> sourceType;
		sourceType = (ModelInstanceType.SingleTyped<? extends ObservableCollection<?>, T, ? extends ObservableCollection<T>>) op
			.get(VALUE_TYPE_KEY);

		Class<?> raw = TypeTokens.getRawType(sourceType.getValueType());
		if (ObservableValue.class.isAssignableFrom(raw)) {
			TypeToken<T> resultType = (TypeToken<T>) sourceType.getValueType().resolveType(ObservableValue.class.getTypeParameters()[0]);
			return CollectionTransform.of(ModelTypes.Collection, resultType, //
				(flow, models) -> flow.flattenValues(resultType, v -> (ObservableValue<? extends T>) v), null, () -> "flatValues");
		} else if (ObservableCollection.class.isAssignableFrom(raw)) {
			System.err.println("WARNING: Collection flatten is not fully implemented.  Many options are unsupported.");
			// TODO Use sort, map options, reverse
			TypeToken<T> resultType = (TypeToken<T>) sourceType.getValueType()
				.resolveType(ObservableCollection.class.getTypeParameters()[0]);
			return CollectionTransform.of(ModelTypes.Collection, resultType, //
				(flow, models) -> flow.flatMap(resultType, v -> ((ObservableCollection<? extends T>) v).flow()), null,
				() -> "flatCollections");
		} else if (CollectionDataFlow.class.isAssignableFrom(raw)) {
			System.err.println("WARNING: Collection flatten is not fully implemented.  Many options are unsupported.");
			// TODO Use sort, map options, reverse
			TypeToken<T> resultType = (TypeToken<T>) sourceType.getValueType().resolveType(CollectionDataFlow.class.getTypeParameters()[2]);
			return CollectionTransform.of(ModelTypes.Collection, resultType, //
				(flow, models) -> flow.flatMap(resultType, v -> (CollectionDataFlow<?, ?, ? extends T>) v), null, () -> "flatFlows");
		} else
			throw new QonfigInterpretationException("Cannot flatten a collection of type " + sourceType.getValueType(),
				op.getElement().getPositionInFile(), 0);
	}

	private <T> FlowCollectionTransform<T, T, ObservableCollection<?>, ObservableCollection<T>> crossCollection(ExpressoQIS op)
		throws QonfigInterpretationException {
		throw new QonfigInterpretationException("Not yet implemented", op.getElement().getPositionInFile(), 0); // TODO
	}

	private <T> FlowCollectionTransform<T, T, ObservableCollection<?>, ObservableCollection<T>> whereCollectionContained(ExpressoQIS op)
		throws QonfigInterpretationException {
		ModelInstanceType.SingleTyped<? extends ObservableCollection<?>, T, ? extends ObservableCollection<T>> sourceType;
		sourceType = (ModelInstanceType.SingleTyped<? extends ObservableCollection<?>, T, ? extends ObservableCollection<T>>) op
			.get(VALUE_TYPE_KEY);

		QonfigExpression2 filterX = op.getAttributeExpression("filter");
		ValueContainer<ObservableCollection<?>, ObservableCollection<?>> filter;
		try {
			filter = filterX.evaluate(ModelTypes.Collection.any(), op.getExpressoEnv());
		} catch (QonfigEvaluationException e) {
			throw new QonfigInterpretationException("Could not parse filter", e, e.getPosition(), e.getErrorLength());
		}
		boolean inclusive = op.getAttribute("inclusive", boolean.class);
		return CollectionTransform.of(sourceType.getModelType(), sourceType.getValueType(), (flow, models) -> flow.whereContained(//
			filter.get(models).flow(), inclusive), //
			(sourceModels, newModels) -> filter.get(sourceModels) != filter.get(newModels),
			() -> "whereContained(" + filter + ", " + inclusive + ")");
	}

	private <K, V> CollectionTransform<V, ObservableMultiMap<?, ?>, ObservableMultiMap<K, V>> groupCollectionBy(ExpressoQIS op)
		throws QonfigInterpretationException {
		throw new QonfigInterpretationException("Not yet implemented", op.getElement().getPositionInFile(), 0); // TODO
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
		}, null, () -> "collect(" + active + ")");
	}

	private <S, T> ParsedTransformation<S, T> mapTransformation(TypeToken<S> currentType, ExpressoQIS op)
		throws QonfigInterpretationException, QonfigEvaluationException {
		List<? extends ExpressoQIS> combinedValues = op.forChildren("combined-value");
		ExpressoQIS map = op.forChildren("map").getFirst();
		ObservableModelSet.Builder mapModelsBuilder = map.getExpressoEnv().getModels().wrap("mapModel");
		Map<String, ValueContainer<SettableValue<?>, SettableValue<Object>>> combined = new LinkedHashMap<>(combinedValues.size() * 2);
		Map<String, ObservableModelSet.RuntimeValuePlaceholder<SettableValue<?>, SettableValue<Object>>> combinedPlaceholders = new LinkedHashMap<>();
		for (ExpressoQIS combine : combinedValues) {
			String name = combine.getAttributeText("name");
			op.getExpressoEnv().getModels().getNameChecker().checkName(name);
			QonfigExpression2 value = combine.getValueExpression();
			ValueContainer<SettableValue<?>, SettableValue<Object>> combineV = value
				.evaluate((ModelInstanceType<SettableValue<?>, SettableValue<Object>>) (ModelInstanceType<?, ?>) ModelTypes.Value.any());
			combined.put(name, combineV);
			combinedPlaceholders.put(name, mapModelsBuilder.withRuntimeValue(name, combineV.getType()));
		}
		String sourceName = op.getAttributeText("source-as");
		ObservableModelSet.RuntimeValuePlaceholder<SettableValue<?>, SettableValue<S>> sourcePlaceholder = mapModelsBuilder
			.withRuntimeValue(sourceName, ModelTypes.Value.forType(currentType));
		ObservableModelSet mapModels = mapModelsBuilder.build();
		map.setModels(mapModels, null);
		QonfigExpression2 mapEx = map.getValueExpression();
		QonfigValue targetTypeV = op.getElement().getAttributes().get(op.getAttributeDef(null, null, "type"));
		TypeToken<T> targetType;
		ValueContainer<SettableValue<?>, SettableValue<T>> mapped;
		if (targetTypeV != null) {
			targetType = (TypeToken<T>) VariableType
				.parseType(targetTypeV.text, map.getExpressoEnv().getClassView(), op.getElement().getDocument().getLocation(),
					targetTypeV.position)//
				.getType(map.getExpressoEnv().getModels());
			mapped = mapEx.evaluate(ModelTypes.Value.forType(targetType));
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
			public Transformation<S, T> transform(Transformation.TransformationPrecursor<S, T, ?> precursor, ModelSetInstance modelSet)
				throws QonfigEvaluationException {
				SettableValue<S> sourceV = SettableValue.build(currentType).build();
				Map<String, SettableValue<Object>> combinedSourceVs = new LinkedHashMap<>();
				Map<String, SettableValue<Object>> combinedTransformVs = new LinkedHashMap<>();
				Transformation.TransformationBuilder<S, T, ?> builder = precursor//
					.cache(cached).reEvalOnUpdate(reEval).fireIfUnchanged(fireIfUnchanged).nullToNull(nullToNull).manyToOne(manyToOne)
					.oneToMany(oneToMany);
				ObservableModelSet.ModelSetInstanceBuilder mapMSIBuilder = mapModels.createInstance(modelSet.getUntil()).withAll(modelSet)//
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

			@Override
			public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws QonfigEvaluationException {
				for (ValueContainer<SettableValue<?>, SettableValue<Object>> combinedSourceV : combined.values()) {
					if (combinedSourceV.get(sourceModels) != combinedSourceV.get(newModels))
						return true;
				}
				return false;
			}

			@Override
			public String toString() {
				StringBuilder str = new StringBuilder("map(").append(mapEx);
				if (!combined.isEmpty())
					str.append("with ").append(combined.values());
				return str.append(")").toString();
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
		Transformation.TransformReverse<S, T> reverse(Transformation<S, T> transformation, ModelSetInstance modelSet)
			throws QonfigEvaluationException;
	}

	<S, T> MapReverse<S, T> createSourceReplace(ExpressoQIS reverse) throws QonfigInterpretationException {
		MapReverseConfig<S, T> reverseConfig = (MapReverseConfig<S, T>) reverse.get(REVERSE_CONFIG);
		QonfigExpression2 reverseX = reverse.getValueExpression();
		QonfigExpression2 enabled = reverse.getAttributeExpression("enabled");
		QonfigExpression2 accept = reverse.getAttributeExpression("accept");
		QonfigExpression2 add = reverse.getAttributeExpression("add");
		QonfigExpression2 addAccept = reverse.getAttributeExpression("add-accept");
		String targetName = reverse.getAttributeText("target-as");
		reverse.getExpressoEnv().getModels().getNameChecker().checkName(targetName);

		boolean stateful = refersToSource(reverseX.getExpression(), reverseConfig.sourceName)//
			|| (enabled != null && refersToSource(enabled.getExpression(), reverseConfig.sourceName))
			|| (accept != null && refersToSource(accept.getExpression(), reverseConfig.sourceName));
		boolean inexact = reverse.getAttribute("inexact", boolean.class);

		ObservableModelSet.Builder reverseModelBuilder = reverse.getExpressoEnv().getModels().wrap("mapRepReverseModel");
		ObservableModelSet.RuntimeValuePlaceholder<SettableValue<?>, SettableValue<S>> sourcePlaceholder = reverseModelBuilder
			.withRuntimeValue(reverseConfig.sourceName, ModelTypes.Value.forType(reverseConfig.sourceType));
		ObservableModelSet.RuntimeValuePlaceholder<SettableValue<?>, SettableValue<T>> targetPlaceholder = reverseModelBuilder
			.withRuntimeValue(targetName, ModelTypes.Value.forType(reverseConfig.targetType));
		Map<String, ObservableModelSet.RuntimeValuePlaceholder<SettableValue<?>, SettableValue<Object>>> combinedValues = new LinkedHashMap<>();
		for (Map.Entry<String, TypeToken<Object>> combined : reverseConfig.combinedValues.entrySet())
			combinedValues.put(combined.getKey(),
				reverseModelBuilder.withRuntimeValue(combined.getKey(), ModelTypes.Value.forType(combined.getValue())));

		ObservableModelSet reverseModels = reverseModelBuilder.build();
		reverse.setModels(reverseModels, null);

		return (transformation, modelSet) -> {
			ValueContainer<SettableValue<?>, SettableValue<S>> reversedV = reverseX
				.evaluate(ModelTypes.Value.forType(reverseConfig.sourceType));
			ValueContainer<SettableValue<?>, SettableValue<String>> enabledV = enabled == null ? null
				: enabled.evaluate(ModelTypes.Value.forType(String.class));
			ValueContainer<SettableValue<?>, SettableValue<String>> acceptV = accept == null ? null
				: accept.evaluate(ModelTypes.Value.forType(String.class));
			ValueContainer<SettableValue<?>, SettableValue<S>> addV = add == null ? null
				: add.evaluate(ModelTypes.Value.forType(reverseConfig.sourceType));
			ValueContainer<SettableValue<?>, SettableValue<String>> addAcceptV = addAccept == null ? null
				: addAccept.evaluate(ModelTypes.Value.forType(String.class));

			SettableValue<S> sourceV = SettableValue.build(reverseConfig.sourceType).build();
			SettableValue<T> targetV = SettableValue.build(reverseConfig.targetType).build();
			ObservableModelSet.ModelSetInstanceBuilder reverseMSIBuilder = reverseModels.createInstance(modelSet.getUntil())
				.withAll(modelSet)//
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
		QonfigExpression2 reverseX = reverse.getValueExpression();
		QonfigExpression2 enabled = reverse.getAttributeExpression("enabled");
		QonfigExpression2 accept = reverse.getAttributeExpression("accept");
		QonfigExpression2 add = reverse.getAttributeExpression("add");
		QonfigExpression2 addAccept = reverse.getAttributeExpression("add-accept");
		String targetName = reverse.getAttributeText("target-as");
		reverse.getExpressoEnv().getModels().getNameChecker().checkName(targetName);

		ObservableModelSet.Builder reverseModelBuilder = reverse.getExpressoEnv().getModels().wrap("mapModReverseModel");
		ObservableModelSet.RuntimeValuePlaceholder<SettableValue<?>, SettableValue<S>> sourcePlaceholder = reverseModelBuilder
			.withRuntimeValue(reverseConfig.sourceName, ModelTypes.Value.forType(reverseConfig.sourceType));
		ObservableModelSet.RuntimeValuePlaceholder<SettableValue<?>, SettableValue<T>> targetPlaceholder = reverseModelBuilder
			.withRuntimeValue(targetName, ModelTypes.Value.forType(reverseConfig.targetType));
		Map<String, ObservableModelSet.RuntimeValuePlaceholder<SettableValue<?>, SettableValue<Object>>> combinedValues = new LinkedHashMap<>();
		for (Map.Entry<String, TypeToken<Object>> combined : reverseConfig.combinedValues.entrySet())
			combinedValues.put(combined.getKey(),
				reverseModelBuilder.withRuntimeValue(combined.getKey(), ModelTypes.Value.forType(combined.getValue())));

		ObservableModelSet reverseModels = reverseModelBuilder.build();
		reverse.setModels(reverseModels, null);

		return (transformation, modelSet) -> {
			ValueContainer<ObservableAction<?>, ObservableAction<?>> reversedV = reverseX.evaluate(ModelTypes.Action.any());
			ValueContainer<SettableValue<?>, SettableValue<String>> enabledV = enabled == null ? null
				: enabled.evaluate(ModelTypes.Value.forType(String.class));
			ValueContainer<SettableValue<?>, SettableValue<String>> acceptV = accept == null ? null
				: accept.evaluate(ModelTypes.Value.forType(String.class));
			ValueContainer<SettableValue<?>, SettableValue<S>> addV = add == null ? null
				: add.evaluate(ModelTypes.Value.forType(reverseConfig.sourceType));
			ValueContainer<SettableValue<?>, SettableValue<String>> addAcceptV = addAccept == null ? null
				: addAccept.evaluate(ModelTypes.Value.forType(String.class));

			SettableValue<S> sourceV = SettableValue.build(reverseConfig.sourceType).build();
			SettableValue<T> targetV = SettableValue.build(reverseConfig.targetType).build();
			ObservableModelSet.ModelSetInstanceBuilder reverseMSIBuilder = reverseModels.createInstance(modelSet.getUntil())
				.withAll(modelSet)//
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
			throw new QonfigInterpretationException("No " + VALUE_TYPE_KEY + " provided for sort interpretation",
				session.getElement().getPositionInFile(), 0);
		String valueAs = session.getAttributeText("sort-value-as");
		String compareValueAs = session.getAttributeText("sort-compare-value-as");
		QonfigExpression2 sortWith = session.getAttributeExpression("sort-with");
		List<ExpressoQIS> sortBy = session.forChildren("sort-by");
		boolean ascending = session.getAttribute("ascending", boolean.class);
		ModelInstanceType.SingleTyped<SettableValue<?>, Comparator<T>, SettableValue<Comparator<T>>> compareType = ModelTypes.Value
			.forType(TypeTokens.get().keyFor(Comparator.class).parameterized(type));
		if (sortWith != null) {
			if (!sortBy.isEmpty())
				session.error("sort-with or sort-by may be used, but not both");
			if (valueAs == null)
				throw new QonfigInterpretationException("sort-with must be used with sort-value-as",
					sortWith.getElement().getPositionInFile(), 0);
			else if (compareValueAs == null)
				throw new QonfigInterpretationException("sort-with must be used with sort-compare-value-as",
					sortWith.getElement().getPositionInFile(), 0);
			return () -> {
				ObservableModelSet.Builder cModelBuilder = session.getExpressoEnv().getModels().wrap("sortAsModel");
				ObservableModelSet.RuntimeValuePlaceholder<SettableValue<?>, SettableValue<T>> valuePlaceholder = cModelBuilder
					.withRuntimeValue(valueAs, ModelTypes.Value.forType(type));
				ObservableModelSet.RuntimeValuePlaceholder<SettableValue<?>, SettableValue<T>> compareValuePlaceholder = cModelBuilder
					.withRuntimeValue(compareValueAs, ModelTypes.Value.forType(type));
				ObservableModelSet cModel = cModelBuilder.build();
				ValueContainer<SettableValue<?>, SettableValue<Integer>> comparison;
				try {
					comparison = sortWith.evaluate(ModelTypes.Value.forType(int.class), session.getExpressoEnv().with(cModel, null));
				} catch (QonfigEvaluationException e) {
					throw new QonfigEvaluationException("Could not parse comparison", e, e.getPosition(), e.getErrorLength());
				}
				return new ValueContainer<SettableValue<?>, SettableValue<Comparator<T>>>() {
					@Override
					public ModelInstanceType<SettableValue<?>, SettableValue<Comparator<T>>> getType() {
						return compareType;
					}

					@Override
					public SettableValue<Comparator<T>> get(ModelSetInstance models) throws QonfigEvaluationException {
						SettableValue<T> leftValue = SettableValue.build(type).build();
						SettableValue<T> rightValue = SettableValue.build(type).build();
						ModelSetInstance cModelInstance = cModel.createInstance(models.getUntil()).withAll(models)//
							.with(valuePlaceholder, leftValue)//
							.with(compareValuePlaceholder, rightValue)//
							.build();
						SettableValue<Integer> comparisonV = comparison.get(cModelInstance);
						return SettableValue.of(compareType.getValueType(), (v1, v2) -> {
							// Put nulls last regardless of ascending
							if (v1 == null) {
								if (v2 == null)
									return 0;
								else
									return 1;
							} else if (v2 == null)
								return -1;
							leftValue.set(v1, null);
							rightValue.set(v2, null);
							int comp = comparisonV.get();
							return ascending ? comp : -comp;
						}, "Not Modifiable");
					}

					@Override
					public SettableValue<Comparator<T>> forModelCopy(SettableValue<Comparator<T>> value, ModelSetInstance sourceModels,
						ModelSetInstance newModels) {
						return value;
					}

					@Override
					public BetterList<ValueContainer<?, ?>> getCores() throws QonfigEvaluationException {
						return comparison.getCores();
					}
				};
			};
		} else if (!sortBy.isEmpty()) {
			if (valueAs == null)
				throw new QonfigInterpretationException("sort-by must be used with sort-value-as", session.getElement().getPositionInFile(),
					0);
			if (compareValueAs != null)
				session.warn("sort-compare-value-as is not used with sort-by");

			ObservableModelSet.Builder cModelBuilder = session.getExpressoEnv().getModels().wrap("sortByModel");
			ObservableModelSet.RuntimeValuePlaceholder<SettableValue<?>, SettableValue<T>> valuePlaceholder = cModelBuilder
				.withRuntimeValue(valueAs, ModelTypes.Value.forType(type));
			ObservableModelSet cModel = cModelBuilder.build();

			List<QonfigExpression2> sortByXs = new ArrayList<>(sortBy.size());
			List<ValueCreator<SettableValue<?>, SettableValue<Comparator<Object>>>> sortByVCs = new ArrayList<>(sortBy.size());
			for (ExpressoQIS sortByX : sortBy) {
				sortByX.setModels(cModel, null);
				sortByXs.add(sortByX.getValueExpression());
				sortByVCs.add(sortByX.interpret(ValueCreator.class));
			}
			return () -> {
				List<ValueContainer<SettableValue<?>, SettableValue<Object>>> sortByMaps = new ArrayList<>(sortBy.size());
				for (int s = 0; s < sortBy.size(); s++) {
					ValueContainer<SettableValue<?>, SettableValue<Object>> sortByMap = sortByXs.get(s).evaluate(
						(ModelInstanceType<SettableValue<?>, SettableValue<Object>>) (ModelInstanceType<?, ?>) ModelTypes.Value.any());
					sortByMaps.add(sortByMap);
					sortBy.get(s).put(VALUE_TYPE_KEY, sortByMap.getType().getType(0));
				}
				List<ValueContainer<SettableValue<?>, SettableValue<Comparator<Object>>>> sortByCVs = new ArrayList<>(sortBy.size());
				for (int i = 0; i < sortBy.size(); i++)
					sortByCVs.add(sortByVCs.get(i).createContainer());
				return new ValueContainer<SettableValue<?>, SettableValue<Comparator<T>>>() {
					@Override
					public ModelInstanceType<SettableValue<?>, SettableValue<Comparator<T>>> getType() {
						return compareType;
					}

					@Override
					public SettableValue<Comparator<T>> get(ModelSetInstance models) throws QonfigEvaluationException {
						SettableValue<T> value = SettableValue.build(type).build();
						ModelSetInstance cModelInstance = cModel.createInstance(models.getUntil()).withAll(models)//
							.with(valuePlaceholder, value)//
							.build();
						List<SettableValue<Object>> sortByMapVs = new ArrayList<>(sortBy.size());
						for (ValueContainer<SettableValue<?>, SettableValue<Object>> sortByMapV : sortByMaps)
							sortByMapVs.add(sortByMapV.get(cModelInstance));
						List<Comparator<Object>> sortByComps = new ArrayList<>(sortBy.size());
						for (ValueContainer<SettableValue<?>, SettableValue<Comparator<Object>>> sortByCV : sortByCVs)
							sortByComps.add(sortByCV.get(cModelInstance).get());
						return SettableValue.of(compareType.getValueType(), (v1, v2) -> {
							// Put nulls last regardless of ascending
							if (v1 == null) {
								if (v2 == null)
									return 0;
								else
									return 1;
							} else if (v2 == null)
								return -1;
							int comp = 0;
							for (int i = 0; i < sortByComps.size(); i++) {
								value.set(v1, null);
								Object cv1 = sortByMapVs.get(i).get();
								value.set(v2, null);
								Object cv2 = sortByMapVs.get(i).get();
								comp = sortByComps.get(i).compare(cv1, cv2);
								if (comp != 0)
									break;
							}
							return ascending ? comp : -comp;
						}, "Not Modifiable");
					}

					@Override
					public SettableValue<Comparator<T>> forModelCopy(SettableValue<Comparator<T>> value, ModelSetInstance sourceModels,
						ModelSetInstance newModels) {
						return value;
					}

					@Override
					public BetterList<ValueContainer<?, ?>> getCores() throws QonfigEvaluationException {
						return BetterList.of(sortByMaps.stream(), vc -> vc.getCores().stream());
					}
				};
			};
		} else {
			Comparator<T> compare = getDefaultSorting(TypeTokens.getRawType(type));
			if (compare != null)
				return ValueCreator.literal(TypeTokens.get().keyFor(Comparator.class).parameterized(type), compare, compare.toString());
			else
				throw new QonfigInterpretationException(type + " is not Comparable, use either sort-with or sort-by",
					session.getElement().getPositionInFile(), 0);
		}
	}

	/**
	 * @param <T> The type to get the sorting for
	 * @param type The type to get the sorting for
	 * @return The default sorting for the given type, or null if no default sorting is available for the type
	 */
	public static <T> Comparator<T> getDefaultSorting(Class<T> type) {
		if (CharSequence.class.isAssignableFrom(type))
			return (Comparator<T>) StringUtils.DISTINCT_NUMBER_TOLERANT;
		else if (Comparable.class.isAssignableFrom(type)) {
			return LambdaUtils.printableComparator((v1, v2) -> {
				if (v1 == null) {
					if (v2 == null)
						return 0;
					else
						return 1;
				} else if (v2 == null)
					return -1;
				else
					return ((Comparable<T>) v1).compareTo(v2);
			}, () -> "comparable", "comparableComparator");
		} else
			return null;
	}

	/**
	 * @param type The type to get the sorting for
	 * @param sort The sorting declaration (null to use the {@link #getDefaultSorting(Class) default sorting} for the type)
	 * @return The defined sorting
	 * @throws QonfigInterpretationException If the sorting could not be parsed or is not defined by default for the given type
	 */
	public static ParsedSorting parseSorting(ExpressoQIS sort, VariableType type, QonfigElement source)
		throws QonfigInterpretationException {
		if (sort == null) {
			return new ParsedSorting() {
				@Override
				public <T> ValueContainer<SettableValue<?>, SettableValue<Comparator<T>>> evaluate(TypeToken<T> type2)
					throws QonfigEvaluationException {
					Comparator<T> sorting = getDefaultSorting(TypeTokens.getRawType(type2));
					if (sorting == null)
						throw new QonfigEvaluationException("No default sorting available for type " + type + ", use <sort>",
							source.getPositionInFile(), 0);
					return ValueContainer.literal(TypeTokens.get().keyFor(Comparator.class).<Comparator<T>> parameterized(type2), sorting,
						sorting.toString());
				}
			};
		}
		ValueCreator<SettableValue<?>, ? extends SettableValue<? extends Comparator<?>>> creator = sort.put(VALUE_TYPE_KEY, type)
			.interpret(ValueCreator.class);
		return new ParsedSorting() {
			@Override
			public <T> ValueContainer<SettableValue<?>, SettableValue<Comparator<T>>> evaluate(TypeToken<T> type2)
				throws QonfigEvaluationException {
				return (ValueContainer<SettableValue<?>, SettableValue<Comparator<T>>>) creator.createContainer();
			}
		};
	}

	static BetterList<TypeToken<?>> argList(TypeToken<?>... init) {
		return BetterTreeList.<TypeToken<?>> build().build().with(init);
	}

	private interface ParsedTransformation<S, T> {
		TypeToken<T> getTargetType();

		boolean isReversible();

		Transformation<S, T> transform(TransformationPrecursor<S, T, ?> precursor, ModelSetInstance modelSet)
			throws QonfigEvaluationException;

		boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws QonfigEvaluationException;
	}
}
