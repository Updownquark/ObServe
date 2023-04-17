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
import org.observe.Transformation.TransformReverse;
import org.observe.Transformation.TransformationPrecursor;
import org.observe.assoc.ObservableMap;
import org.observe.assoc.ObservableMultiMap;
import org.observe.assoc.ObservableSortedMap;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollection.DistinctDataFlow;
import org.observe.collect.ObservableCollection.DistinctSortedDataFlow;
import org.observe.collect.ObservableCollection.SortedDataFlow;
import org.observe.collect.ObservableCollectionBuilder;
import org.observe.collect.ObservableSet;
import org.observe.collect.ObservableSortedCollection;
import org.observe.collect.ObservableSortedSet;
import org.observe.config.ObservableConfig;
import org.observe.config.ObservableValueSet;
import org.observe.expresso.DynamicModelValue.Identity;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelType.ModelInstanceType.SingleTyped;
import org.observe.expresso.ObservableModelSet.AbstractValueContainer;
import org.observe.expresso.ObservableModelSet.CompiledModelValue;
import org.observe.expresso.ObservableModelSet.InterpretedModelSet;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueSynth;
import org.observe.expresso.ObservableModelSet.RuntimeValuePlaceholder;
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
import org.qommons.ex.ExFunction;
import org.qommons.io.SimpleXMLParser.ContentPosition;
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
		ModelValueSynth<SettableValue<?>, ? extends ObservableValue<String>> getTitle();

		/** @return A function to provide the icon representing the application */
		ModelValueSynth<SettableValue<?>, ? extends ObservableValue<Image>> getIcon();
	}

	/** A structure parsed from a {@link QonfigElement} that is capable of generating a {@link Comparator} for sorting */
	public interface ParsedSorting {
		/**
		 * @param <T> The type to sort
		 * @param type The type to sort
		 * @return A value container capable
		 * @throws ExpressoInterpretationException
		 */
		<T> ModelValueSynth<SettableValue<?>, SettableValue<Comparator<T>>> evaluate(TypeToken<T> type)
			throws ExpressoInterpretationException;
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
			ObservableModelSet.Built built;
			if (models instanceof ObservableModelSet.Built)
				built = (ObservableModelSet.Built) models;
			else if (models instanceof ObservableModelSet.Builder)
				built = ((ObservableModelSet.Builder) models).build();
			else
				throw new IllegalStateException(
					"Interpreted a " + models.getClass().getName() + " as a model set, which is not either built or a builder");
			return new Expresso(classView, built);
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
						CompiledExpression sourceAttrX;
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
									e.getPosition(), e.getErrorLength(), e);
							else
								throw new QonfigInterpretationException(
									"Could not obtain source-attribute expression for " + dv.getSourceAttribute(), e.getPosition(),
									e.getErrorLength(), e);
						}
						if (sourceAttrX != null) {
							builder.withMaker(name, new DynamicModelValue.Compiled<Object, Object>() {
								@Override
								public Identity getIdentity() {
									return dv;
								}

								@Override
								public ModelType<Object> getModelType() {
									return spec.getModelType();
								}

								@Override
								public ModelValueSynth<Object, Object> createSynthesizer() throws ExpressoInterpretationException {
									ModelInstanceType<Object, Object> valueType;
									try {
										valueType = (ModelInstanceType<Object, Object>) spec.getType(session);
									} catch (ExpressoInterpretationException e) {
										throw new ExpressoInterpretationException("Could not interpret type", e.getPosition(),
											e.getErrorLength(), e);
									}
									try {
										return sourceAttrX.evaluate(valueType);
									} catch (ExpressoInterpretationException e) {
										String msg = "Could not interpret source" + (dv.isSourceValue() ? " value for " + dv.getOwner()
										: "-attribute " + dv.getSourceAttribute());
										session.error(msg, e);
										throw new ExpressoInterpretationException(msg, e.getPosition(), e.getErrorLength(), e);
									}
								}

								@Override
								public String toString() {
									return name + "=" + sourceAttrX;
								}
							});
							// } else if (dv.getType() != null) {
							// wrappedBuilder.withMaker(name, ObservableModelSet.IdentifableCompiledValue.of(dv,
							// new DynamicModelValue.RuntimeModelValue<>(dv, valueType)));
						} else {
							builder.withMaker(name, new DynamicModelValue.DynamicTypedModelValueCreator<>(dv, () -> {
								return (ModelInstanceType<Object, Object>) spec.getType(session);
							}));
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
		.createWith("first-value", CompiledModelValue.class, session -> createFirstValue(wrap(session)))//
		.createWith("hook", CompiledModelValue.class, session -> createHook(wrap(session)))//
		.createWith("sort", ParsedSorting.class, session -> parseSorting(wrap(session)))//
		.createWith("sort-by", ParsedSorting.class, session -> parseSorting(wrap(session)))//
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
			ObservableModelSet.Built built = builder.build();
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
							session.getElement().getDocument().getLocation(), //
							typeV.position != null ? typeV.position : new ContentPosition.Fixed(session.getElement().getFilePosition())));
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
				} catch (ExpressoInterpretationException e) {
					throw new QonfigInterpretationException("Could not interpret type", e.getPosition(), e.getErrorLength(), e);
				}
				CompiledExpression defaultX = valueEl.getAttributeExpression("default");
				model.withExternal(name, childType, valueEl.getElement().getPositionInFile(), extModels -> {
					try {
						return extModels.getValue(childPath, childType);
					} catch (IllegalArgumentException | ModelException | TypeConversionException e) {
						if (defaultX == null)
							throw e;
						// TODO Can delete this?
						// if (defaultX == null)
						// throw new ModelInstantiationException(
						// "External model " + model.getIdentity() + " does not match expected: " + e.getMessage(),
						// session.getElement().getPositionInFile(), 0, e);
						// } catch (QonfigEvaluationException e) {
						// if (defaultX == null)
						// throw new QonfigEvaluationException(
						// "External model " + model.getIdentity() + " does not match expected: " + e.getMessage(), e, e.getPosition(),
						// e.getErrorLength());
					}
					return null;
				}, models -> {
					if (defaultX == null)
						return null;
					ModelValueSynth<Object, Object> defaultV;
					try {
						defaultV = defaultX.evaluate(childType);
					} catch (ExpressoInterpretationException e) {
						throw new ModelInstantiationException(e.getMessage(), e.getPosition(), e.getErrorLength(), e);
					}
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

	abstract class InternalCollectionValue<C extends ObservableCollection<?>> implements QonfigValueCreator<CompiledModelValue<C, C>> {
		private final ModelType<C> theType;

		protected InternalCollectionValue(ModelType<C> type) {
			theType = type;
		}

		@Override
		public CompiledModelValue<C, C> createValue(CoreSession session) throws QonfigInterpretationException {
			ExpressoQIS exS = wrap(session);
			VariableType vblType = session.get(VALUE_TYPE_KEY, VariableType.class);
			List<CompiledModelValue<SettableValue<?>, SettableValue<Object>>> elCreators = session.asElement("int-list")
				.interpretChildren("element", CompiledModelValue.class);
			Object preI = preInterpret(vblType, exS);
			return CompiledModelValue.of(session.getElement().getType().getName(), theType, () -> {
				TypeToken<Object> type;
				try {
					type = TypeTokens.get().wrap((TypeToken<Object>) vblType.getType(exS.getExpressoEnv().getModels()));
				} catch (ExpressoInterpretationException e) {
					throw new ExpressoInterpretationException("Could not parse type", e.getPosition(), e.getErrorLength(), e);
				}
				Object prep;
				try {
					prep = prepare(type, preI);
				} catch (ExpressoInterpretationException e) {
					throw new ExpressoInterpretationException("Could not prepare values", e.getPosition(), e.getErrorLength(), e);
				}
				List<ModelValueSynth<SettableValue<?>, SettableValue<Object>>> elContainers = new ArrayList<>(elCreators.size());
				for (CompiledModelValue<SettableValue<?>, SettableValue<Object>> creator : elCreators)
					elContainers.add(creator.createSynthesizer());
				TypeToken<Object> fType = type;
				return new AbstractValueContainer<C, C>((ModelInstanceType<C, C>) theType.forTypes(type)) {
					@Override
					public C get(ModelSetInstance models) throws ModelInstantiationException {
						C collection = (C) create(fType, models, prep).withDescription(session.get(PATH_KEY, String.class)).build();
						for (ModelValueSynth<SettableValue<?>, SettableValue<Object>> value : elContainers) {
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
					public BetterList<ModelValueSynth<?, ?>> getCores() {
						return BetterList.of(this);
					}
				};
			});
		}

		protected Object preInterpret(VariableType type, ExpressoQIS session) throws QonfigInterpretationException {
			return null;
		}

		protected <V> Object prepare(TypeToken<V> type, Object preInterpret) throws ExpressoInterpretationException {
			return null;
		}

		protected abstract <V> ObservableCollectionBuilder<V, ?> create(TypeToken<V> type, ModelSetInstance models, Object prep)
			throws ModelInstantiationException;

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
			ExpressoQIS sort = session.forChildren("sort").peekFirst();
			if (sort != null)
				return sort.interpret(ParsedSorting.class);
			else
				return getDefaultSorting(session.getElement());
		}

		@Override
		protected <V> Object prepare(TypeToken<V> type, Object preInterpret) throws ExpressoInterpretationException {
			return ((ParsedSorting) preInterpret).evaluate(type);
		}

		@Override
		protected <V> ObservableCollectionBuilder<V, ?> create(TypeToken<V> type, ModelSetInstance models, Object prep)
			throws ModelInstantiationException {
			Comparator<V> sorting = ((ModelValueSynth<SettableValue<?>, SettableValue<Comparator<V>>>) prep).get(models).get();
			return create(type, sorting);
		}

		protected abstract <V> ObservableCollectionBuilder<V, ?> create(TypeToken<V> type, Comparator<V> comparator)
			throws ModelInstantiationException;
	}

	abstract class InternalMapValue<M extends ObservableMap<?, ?>> implements QonfigValueCreator<CompiledModelValue<M, M>> {
		private final ModelType<M> theType;

		protected InternalMapValue(ModelType<M> type) {
			theType = type;
		}

		@Override
		public CompiledModelValue<M, M> createValue(CoreSession session) throws QonfigInterpretationException {
			ExpressoQIS exS = wrap(session);
			VariableType vblKeyType = session.get(KEY_TYPE_KEY, VariableType.class);
			VariableType vblValueType = session.get(VALUE_TYPE_KEY, VariableType.class);
			Object preI = preInterpret(vblKeyType, vblValueType, exS);
			List<BiTuple<CompiledModelValue<SettableValue<?>, SettableValue<Object>>, CompiledModelValue<SettableValue<?>, SettableValue<Object>>>> entryCreators;
			entryCreators = session.asElement("int-map").interpretChildren("entry", BiTuple.class);
			return CompiledModelValue.of(session.getElement().getType().getName(), theType, () -> {
				TypeToken<Object> keyType;
				try {
					keyType = TypeTokens.get().wrap((TypeToken<Object>) vblKeyType.getType(exS.getExpressoEnv().getModels()));
				} catch (ExpressoInterpretationException e) {
					throw new ExpressoInterpretationException("Could not parse key type", e.getPosition(), e.getErrorLength(), e);
				}
				TypeToken<Object> valueType;
				try {
					valueType = TypeTokens.get().wrap((TypeToken<Object>) vblValueType.getType(exS.getExpressoEnv().getModels()));
				} catch (ExpressoInterpretationException e) {
					throw new ExpressoInterpretationException("Could not parse value type", e.getPosition(), e.getErrorLength(), e);
				}
				List<BiTuple<ModelValueSynth<SettableValue<?>, SettableValue<Object>>, ModelValueSynth<SettableValue<?>, SettableValue<Object>>>> entryContainers;
				entryContainers = new ArrayList<>(entryCreators.size());
				for (BiTuple<CompiledModelValue<SettableValue<?>, SettableValue<Object>>, CompiledModelValue<SettableValue<?>, SettableValue<Object>>> entry : entryCreators)
					entryContainers.add(new BiTuple<>(entry.getValue1().createSynthesizer(), entry.getValue2().createSynthesizer()));
				Object prep;
				try {
					prep = prepare(keyType, valueType, exS, preI);
				} catch (ExpressoInterpretationException e) {
					throw new ExpressoInterpretationException("Could not prepare values", e.getPosition(), e.getErrorLength(), e);
				}
				TypeToken<Object> fKeyType = keyType, fValueType = valueType;
				return new AbstractValueContainer<M, M>((ModelInstanceType<M, M>) theType.forTypes(keyType, valueType)) {
					@Override
					public M get(ModelSetInstance models) throws ModelInstantiationException {
						M map = (M) create(fKeyType, fValueType, models, prep).withDescription(session.get(PATH_KEY, String.class))
							.buildMap();
						for (BiTuple<ModelValueSynth<SettableValue<?>, SettableValue<Object>>, ModelValueSynth<SettableValue<?>, SettableValue<Object>>> entry : entryContainers) {
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
					public BetterList<ModelValueSynth<?, ?>> getCores() {
						return BetterList.of(this);
					}
				};
			});
		}

		protected Object preInterpret(VariableType keyType, VariableType valueType, ExpressoQIS session)
			throws QonfigInterpretationException {
			return null;
		}

		protected <K, V> Object prepare(TypeToken<K> keyType, TypeToken<V> valueType, ExpressoQIS session, Object preInterpret)
			throws ExpressoInterpretationException {
			return null;
		}

		protected abstract <K, V> ObservableMap.Builder<K, V, ?> create(TypeToken<K> keyType, TypeToken<V> valueType,
			ModelSetInstance models, Object prep) throws ModelInstantiationException;

		@Override
		public String toString() {
			return theType.toString();
		}
	}

	abstract class InternalMultiMapValue<M extends ObservableMultiMap<?, ?>> implements QonfigValueCreator<CompiledModelValue<M, M>> {
		private final ModelType<M> theType;

		protected InternalMultiMapValue(ModelType<M> type) {
			theType = type;
		}

		@Override
		public CompiledModelValue<M, M> createValue(CoreSession session) throws QonfigInterpretationException {
			ExpressoQIS exS = wrap(session);
			VariableType vblKeyType = session.get(KEY_TYPE_KEY, VariableType.class);
			VariableType vblValueType = session.get(VALUE_TYPE_KEY, VariableType.class);
			Object preI = preInterpret(vblKeyType, vblValueType, exS);
			List<BiTuple<CompiledModelValue<SettableValue<?>, SettableValue<Object>>, CompiledModelValue<SettableValue<?>, SettableValue<Object>>>> entryCreators;
			entryCreators = session.asElement("int-map").interpretChildren("entry", BiTuple.class);
			return CompiledModelValue.of(session.getElement().getType().getName(), theType, () -> {
				TypeToken<Object> keyType;
				try {
					keyType = TypeTokens.get().wrap((TypeToken<Object>) vblKeyType.getType(exS.getExpressoEnv().getModels()));
				} catch (ExpressoInterpretationException e) {
					throw new ExpressoInterpretationException("Could not parse key type", e.getPosition(), e.getErrorLength(), e);
				}
				TypeToken<Object> valueType;
				try {
					valueType = TypeTokens.get().wrap((TypeToken<Object>) vblValueType.getType(exS.getExpressoEnv().getModels()));
				} catch (ExpressoInterpretationException e) {
					throw new ExpressoInterpretationException("Could not parse value type", e.getPosition(), e.getErrorLength(), e);
				}
				List<BiTuple<ModelValueSynth<SettableValue<?>, SettableValue<Object>>, ModelValueSynth<SettableValue<?>, SettableValue<Object>>>> entryContainers;
				entryContainers = new ArrayList<>(entryCreators.size());
				for (BiTuple<CompiledModelValue<SettableValue<?>, SettableValue<Object>>, CompiledModelValue<SettableValue<?>, SettableValue<Object>>> entry : entryCreators)
					entryContainers.add(new BiTuple<>(entry.getValue1().createSynthesizer(), entry.getValue2().createSynthesizer()));
				Object prep;
				try {
					prep = prepare(keyType, valueType, exS, preI);
				} catch (ExpressoInterpretationException e) {
					throw new ExpressoInterpretationException("Could not prepare values", e.getPosition(), e.getErrorLength(), e);
				}
				TypeToken<Object> fKeyType = keyType, fValueType = valueType;
				return new AbstractValueContainer<M, M>((ModelInstanceType<M, M>) theType.forTypes(keyType, valueType)) {
					@Override
					public M get(ModelSetInstance models) throws ModelInstantiationException {
						M map = (M) create(fKeyType, fValueType, models, prep).withDescription(session.get(PATH_KEY, String.class))
							.build(models.getUntil());
						for (BiTuple<ModelValueSynth<SettableValue<?>, SettableValue<Object>>, ModelValueSynth<SettableValue<?>, SettableValue<Object>>> entry : entryContainers) {
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
					public BetterList<ModelValueSynth<?, ?>> getCores() {
						return BetterList.of(this);
					}
				};
			});
		}

		protected Object preInterpret(VariableType keyType, VariableType valueType, ExpressoQIS session)
			throws QonfigInterpretationException {
			return null;
		}

		protected <K, V> Object prepare(TypeToken<K> keyType, TypeToken<V> valueType, ExpressoQIS session, Object preInterpret)
			throws ExpressoInterpretationException {
			return null;
		}

		protected abstract <K, V> ObservableMultiMap.Builder<K, V, ?> create(TypeToken<K> keyType, TypeToken<V> valueType,
			ModelSetInstance models, Object prep) throws ModelInstantiationException;

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
					CompiledModelValue<?, ?> container = child.setModels(model, null).interpret(CompiledModelValue.class);
					model.withMaker(child.getAttributeText("model-element", "name"), container);
				} else if (child.fulfills(subModelRole)) {
					ObservableModelSet.Builder subModel = model.createSubModel(child.getAttributeText("named", "name"));
					child.setModels(subModel, null);
					child.interpret(ObservableModelSet.class);
				}
			}
			return model;
		});
		interpreter.createWith("constant", CompiledModelValue.class, session -> interpretConstant(wrap(session)));
		interpreter.createWith("value", CompiledModelValue.class, session -> interpretValue(wrap(session)));
		interpreter.createWith("action", CompiledModelValue.class, session -> {
			ExpressoQIS exS = wrap(session);
			CompiledExpression valueX = exS.getValueExpression();
			VariableType vblType = session.get(VALUE_TYPE_KEY, VariableType.class);
			return CompiledModelValue.of(valueX::toString, ModelTypes.Action, () -> {
				TypeToken<Object> type = vblType == null ? null : (TypeToken<Object>) vblType.getType(exS.getExpressoEnv().getModels());
				ModelValueSynth<ObservableAction<?>, ObservableAction<Object>> action = valueX.evaluate(
					ModelTypes.Action.forType(type == null ? (TypeToken<Object>) (TypeToken<?>) TypeTokens.get().WILDCARD : type));
				exS.interpretLocalModel();
				return action.wrapModels(exS::wrapLocal);
			});
		}).createWith("action-group", CompiledModelValue.class, session -> interpretActionGroup(wrap(session)));
		interpreter.createWith("loop", CompiledModelValue.class, session -> interpretLoop(wrap(session)));
		interpreter.createWith("value-set", CompiledModelValue.class, session -> {
			ExpressoQIS exS = wrap(session);
			VariableType vblType = session.get(VALUE_TYPE_KEY, VariableType.class);
			return CompiledModelValue.of(() -> "value-set<" + vblType + ">", ModelTypes.ValueSet, () -> {
				ModelInstanceType<ObservableValueSet<?>, ObservableValueSet<Object>> type;
				try {
					type = ModelTypes.ValueSet.forType((TypeToken<Object>) vblType.getType(exS.getExpressoEnv().getModels()));
				} catch (ExpressoInterpretationException e) {
					throw new ExpressoInterpretationException("Could not evaluate type", e.getPosition(), e.getErrorLength(), e);
				}
				return new ModelValueSynth<ObservableValueSet<?>, ObservableValueSet<Object>>() {
					@Override
					public ModelType<ObservableValueSet<?>> getModelType() {
						return ModelTypes.ValueSet;
					}

					@Override
					public ModelInstanceType<ObservableValueSet<?>, ObservableValueSet<Object>> getType()
						throws ExpressoInterpretationException {
						return type;
					}

					@Override
					public ObservableValueSet<Object> get(ModelSetInstance models) throws ModelInstantiationException {
						// Although a purely in-memory value set would be more efficient, I have yet to implement one.
						// Easiest path forward for this right now is to make an unpersisted ObservableConfig and use it to back the value
						// set.
						// TODO At some point I should come back and make an in-memory implementation and use it here.
						ObservableConfig config = ObservableConfig.createRoot("root", null,
							__ -> new FastFailLockingStrategy(ThreadConstraint.ANY));
						return config.asValue((TypeToken<Object>) type.getType(0)).buildEntitySet(null);
					}

					@Override
					public ObservableValueSet<Object> forModelCopy(ObservableValueSet<Object> value, ModelSetInstance sourceModels,
						ModelSetInstance newModels) throws ModelInstantiationException {
						return value;
					}

					@Override
					public BetterList<ModelValueSynth<?, ?>> getCores() {
						return BetterList.of(this);
					}
				};
			});
		});
		interpreter.createWith("element", CompiledModelValue.class, session -> {
			ExpressoQIS exS = wrap(session);
			VariableType vblType = session.get(VALUE_TYPE_KEY, VariableType.class);
			if (vblType == null)
				throw new QonfigInterpretationException("No " + VALUE_TYPE_KEY + " available", session.getElement().getPositionInFile(), 0);
			CompiledExpression valueX = exS.getValueExpression();
			return CompiledModelValue.of("element", ModelTypes.Value, () -> {
				TypeToken<?> type = vblType.getType(exS.getExpressoEnv().getModels());
				return valueX.evaluate(ModelTypes.Value.forType(type));
			});
		});
		interpreter.createWith("list", CompiledModelValue.class,
			new InternalCollectionValue<ObservableCollection<?>>(ModelTypes.Collection) {
			@Override
			protected <V> ObservableCollectionBuilder<V, ?> create(TypeToken<V> type, ModelSetInstance models, Object prep) {
				return ObservableCollection.build(type);
			}
		});
		interpreter.createWith("set", CompiledModelValue.class, new InternalCollectionValue<ObservableSet<?>>(ModelTypes.Set) {
			@Override
			protected <V> ObservableCollectionBuilder<V, ?> create(TypeToken<V> type, ModelSetInstance models, Object prep) {
				return ObservableSet.build(type);
			}
		});
		// class SortedCollectionCreator<C extends ObservableSortedCollection<?>> extends InternalCollectionValue
		interpreter.createWith("sorted-set", CompiledModelValue.class,
			new InternalSortedCollectionValue<ObservableSortedSet<?>>(ModelTypes.SortedSet) {
			@Override
			protected <V> ObservableCollectionBuilder<V, ?> create(TypeToken<V> type, Comparator<V> comparator) {
				return ObservableSortedSet.build(type, comparator);
			}
		}).createWith("sorted-list", CompiledModelValue.class,
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
			CompiledExpression keyX = exS.getAttributeExpression("key");
			CompiledExpression valueX = exS.getAttributeExpression("value");
			CompiledModelValue<SettableValue<?>, SettableValue<Object>> key = CompiledModelValue.of("key", ModelTypes.Value, () -> {
				TypeToken<?> keyType = vblKeyType.getType(exS.getExpressoEnv().getModels());
				return (ModelValueSynth<SettableValue<?>, SettableValue<Object>>) (ModelValueSynth<?, ?>) keyX
					.evaluate(ModelTypes.Value.forType(keyType));
			});
			CompiledModelValue<SettableValue<?>, SettableValue<Object>> value = CompiledModelValue.of("value", ModelTypes.Value, () -> {
				TypeToken<?> valueType = vblValueType.getType(exS.getExpressoEnv().getModels());
				return (ModelValueSynth<SettableValue<?>, SettableValue<Object>>) (ModelValueSynth<?, ?>) valueX
					.evaluate(ModelTypes.Value.forType(valueType));
			});
			return new BiTuple<>(key, value);
		}).createWith("map", CompiledModelValue.class, new InternalMapValue<ObservableMap<?, ?>>(ModelTypes.Map) {
			@Override
			protected <K, V> ObservableMap.Builder<K, V, ?> create(TypeToken<K> keyType, TypeToken<V> valueType,
				ModelSetInstance models, Object prep) {
				return ObservableMap.build(keyType, valueType);
			}
		}).createWith("sorted-map", CompiledModelValue.class, new InternalMapValue<ObservableSortedMap<?, ?>>(ModelTypes.SortedMap) {
			@Override
			protected Object preInterpret(VariableType keyType, VariableType valueType, ExpressoQIS session)
				throws QonfigInterpretationException {
				ExpressoQIS sort = session.forChildren("sort").peekFirst();
				if (sort != null)
					return sort.interpret(ParsedSorting.class);
				else
					return getDefaultSorting(session.getElement());
			}

			@Override
			protected <K, V> Object prepare(TypeToken<K> keyType, TypeToken<V> valueType, ExpressoQIS session, Object preInterpret)
				throws ExpressoInterpretationException {
				return ((ParsedSorting) preInterpret).evaluate(keyType);
			}

			@Override
			protected <K, V> ObservableMap.Builder<K, V, ?> create(TypeToken<K> keyType, TypeToken<V> valueType,
				ModelSetInstance models, Object prep) throws ModelInstantiationException {
				Comparator<K> sorting = ((ModelValueSynth<SettableValue<?>, SettableValue<Comparator<K>>>) prep).get(models).get();
				return ObservableSortedMap.build(keyType, valueType, sorting);
			}
		}).createWith("multi-map", CompiledModelValue.class, new InternalMultiMapValue<ObservableMultiMap<?, ?>>(ModelTypes.MultiMap) {
			@Override
			protected <K, V> ObservableMultiMap.Builder<K, V, ?> create(TypeToken<K> keyType, TypeToken<V> valueType,
				ModelSetInstance models, Object prep) {
				return ObservableMultiMap.build(keyType, valueType);
			}
		}).createWith("sorted-multi-map", CompiledModelValue.class,
			new InternalMultiMapValue<ObservableMultiMap<?, ?>>(ModelTypes.MultiMap) {
			@Override
			protected Object preInterpret(VariableType keyType, VariableType valueType, ExpressoQIS session)
				throws QonfigInterpretationException {
				ExpressoQIS sort = session.forChildren("sort").peekFirst();
				if (sort != null)
					return sort.interpret(ParsedSorting.class);
				else
					return getDefaultSorting(session.getElement());
			}

			@Override
			protected <K, V> Object prepare(TypeToken<K> keyType, TypeToken<V> valueType, ExpressoQIS session, Object preInterpret)
				throws ExpressoInterpretationException {
				return ((ParsedSorting) preInterpret).evaluate(keyType);
			}

			@Override
			protected <K, V> ObservableMultiMap.Builder<K, V, ?> create(TypeToken<K> keyType, TypeToken<V> valueType,
				ModelSetInstance models, Object prep) throws ModelInstantiationException {
				Comparator<K> sorting = ((ModelValueSynth<SettableValue<?>, SettableValue<Comparator<K>>>) prep).get(models).get();
				return ObservableMultiMap.build(keyType, valueType).sortedBy(sorting);
			}
		});
	}

	private CompiledModelValue<SettableValue<?>, SettableValue<Object>> interpretConstant(ExpressoQIS exS)
		throws QonfigInterpretationException {
		TypeToken<Object> valueType = (TypeToken<Object>) exS.get(VALUE_TYPE_KEY);
		CompiledExpression value = exS.getValueExpression();
		return CompiledModelValue.of(() -> "constant:" + valueType, ModelTypes.Value, () -> {
			InterpretedValueSynth<SettableValue<?>, SettableValue<Object>> valueC;
			try {
				valueC = value.evaluate(valueType == null ? ModelTypes.Value.anyAs() : ModelTypes.Value.forType(valueType))//
					.interpret();
			} catch (ExpressoInterpretationException e) {
				throw new ExpressoInterpretationException("Could not interpret value of constant: " + value, e.getPosition(),
					e.getErrorLength(), e);
			}
			return new ModelValueSynth<SettableValue<?>, SettableValue<Object>>() {
				@Override
				public ModelType<SettableValue<?>> getModelType() {
					return ModelTypes.Value;
				}

				@Override
				public ModelInstanceType<SettableValue<?>, SettableValue<Object>> getType() {
					return valueC.getType();
				}

				@Override
				public SettableValue<Object> get(ModelSetInstance models) throws ModelInstantiationException {
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
						public <V extends T> T set(V value2, Object cause) throws IllegalArgumentException, UnsupportedOperationException {
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
				public BetterList<ModelValueSynth<?, ?>> getCores() {
					return BetterList.of(this);
				}
			};
		});
	}

	private CompiledModelValue<SettableValue<?>, SettableValue<Object>> interpretValue(ExpressoQIS exS)
		throws QonfigInterpretationException {
		CompiledExpression valueX = exS.getValueExpression();
		CompiledExpression initX = exS.isInstance("int-value") ? exS.asElement("int-value").getAttributeExpression("init") : null;
		if (initX != null && valueX != null)
			exS.warn("Either a value or an init value may be specified, but not both.  Initial value will be ignored.");
		VariableType vblType = exS.get(VALUE_TYPE_KEY, VariableType.class);
		if (vblType == null && valueX == null && initX == null)
			throw new QonfigInterpretationException("A type, a value, or an initializer must be specified",
				exS.getElement().getPositionInFile(), 0);
		return CompiledModelValue.of(() -> {
			if (valueX != null)
				return valueX.toString();
			else if (vblType != null)
				return vblType.toString();
			else
				return "init:" + initX.toString();
		}, ModelTypes.Value, () -> {
			TypeToken<Object> type;
			try {
				type = vblType == null ? null : (TypeToken<Object>) vblType.getType(exS.getExpressoEnv().getModels());
			} catch (ExpressoInterpretationException e) {
				throw new ExpressoInterpretationException("Could not parse type", e.getPosition(), e.getErrorLength(), e);
			}
			ModelInstanceType<SettableValue<?>, SettableValue<Object>> modelType = type == null ? ModelTypes.Value.anyAs()
				: ModelTypes.Value.forType(type);
			ModelValueSynth<SettableValue<?>, SettableValue<Object>> value = valueX == null ? null : valueX.evaluate(modelType);
			ModelValueSynth<SettableValue<?>, SettableValue<Object>> init;
			if (value != null)
				init = null;
			else if (initX != null)
				init = initX.evaluate(modelType);
			else
				init = ModelValueSynth.literal(type, TypeTokens.get().getDefaultValue(type), "<default>");
			ModelInstanceType<SettableValue<?>, SettableValue<Object>> fType;
			if (type != null)
				fType = modelType;
			else if (value != null)
				fType = value.getType();
			else
				fType = init.getType();
			return new ObservableModelSet.AbstractValueContainer<SettableValue<?>, SettableValue<Object>>(fType) {
				@Override
				public SettableValue<Object> get(ModelSetInstance models) throws ModelInstantiationException {
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
					ModelSetInstance newModels) throws ModelInstantiationException {
					if (value != null)
						return value.forModelCopy(value2, sourceModels, newModels);
					else
						return value2;
				}

				@Override
				public BetterList<ModelValueSynth<?, ?>> getCores() throws ExpressoInterpretationException {
					if (value != null)
						return value.getCores();
					else
						return BetterList.of(this);
				}
			};
		});
	}

	private CompiledModelValue<ObservableAction<?>, ObservableAction<Object>> interpretActionGroup(ExpressoQIS exS)
		throws QonfigInterpretationException {
		List<CompiledModelValue<ObservableAction<?>, ObservableAction<Object>>> actions = exS.interpretChildren("action",
			CompiledModelValue.class);
		return CompiledModelValue.of("action-group", ModelTypes.Action, () -> {
			BetterList<ModelValueSynth<ObservableAction<?>, ObservableAction<Object>>> actionVs = BetterList.of2(actions.stream(), //
				a -> a.createSynthesizer());
			exS.interpretLocalModel();
			return new ModelValueSynth<ObservableAction<?>, ObservableAction<Object>>() {
				@Override
				public ModelType<ObservableAction<?>> getModelType() {
					return ModelTypes.Action;
				}

				@Override
				public ModelInstanceType<ObservableAction<?>, ObservableAction<Object>> getType() {
					return ModelTypes.Action.forType(Object.class);
				}

				@Override
				public ObservableAction<Object> get(ModelSetInstance models) throws ModelInstantiationException {
					ModelSetInstance wrappedModels = exS.wrapLocal(models);
					BetterList<ObservableAction<Object>> realActions = BetterList.of2(actionVs.stream(), a -> a.get(wrappedModels));
					return createActionGroup(realActions);
				}

				@Override
				public ObservableAction<Object> forModelCopy(ObservableAction<Object> value, ModelSetInstance sourceModels,
					ModelSetInstance newModels) throws ModelInstantiationException {
					ModelSetInstance wrappedNew = exS.wrapLocal(newModels);
					List<ObservableAction<Object>> realActions = new ArrayList<>(actionVs.size());
					boolean different = false;
					for (ModelValueSynth<ObservableAction<?>, ObservableAction<Object>> actionV : actionVs) {
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
				public BetterList<ModelValueSynth<?, ?>> getCores() throws ExpressoInterpretationException {
					return BetterList.of(actionVs.stream(), vc -> vc.getCores().stream());
				}
			};
		});
	}

	private CompiledModelValue<ObservableAction<?>, ObservableAction<Object>> interpretLoop(ExpressoQIS exS)
		throws QonfigInterpretationException {
		CompiledExpression init = exS.getAttributeExpression("init");
		CompiledExpression before = exS.getAttributeExpression("before-while");
		CompiledExpression whileX = exS.getAttributeExpression("while");
		CompiledExpression beforeBody = exS.getAttributeExpression("before-body");
		CompiledExpression afterBody = exS.getAttributeExpression("after-body");
		CompiledExpression finallly = exS.getAttributeExpression("finally");
		List<CompiledModelValue<ObservableAction<?>, ObservableAction<?>>> exec = exS.interpretChildren("body", CompiledModelValue.class);
		return CompiledModelValue.of("loop", ModelTypes.Action, () -> {
			ModelValueSynth<ObservableAction<?>, ObservableAction<?>> initV = init == null ? null : init.evaluate(ModelTypes.Action.any());
			ModelValueSynth<ObservableAction<?>, ObservableAction<?>> beforeV = before == null ? null
				: before.evaluate(ModelTypes.Action.any());
			ModelValueSynth<SettableValue<?>, SettableValue<Boolean>> whileV = whileX.evaluate(ModelTypes.Value.forType(boolean.class));
			ModelValueSynth<ObservableAction<?>, ObservableAction<?>> beforeBodyV = beforeBody == null ? null
				: beforeBody.evaluate(ModelTypes.Action.any());
			ModelValueSynth<ObservableAction<?>, ObservableAction<?>> afterBodyV = afterBody == null ? null
				: afterBody.evaluate(ModelTypes.Action.any());
			ModelValueSynth<ObservableAction<?>, ObservableAction<?>> finallyV = finallly == null ? null
				: finallly.evaluate(ModelTypes.Action.any());
			List<ModelValueSynth<ObservableAction<?>, ObservableAction<?>>> execVs = BetterList.of2(exec.stream(),
				v -> v.createSynthesizer());
			exS.interpretLocalModel();
			return new ModelValueSynth<ObservableAction<?>, ObservableAction<Object>>() {
				@Override
				public ModelType<ObservableAction<?>> getModelType() {
					return ModelTypes.Action;
				}

				@Override
				public ModelInstanceType<ObservableAction<?>, ObservableAction<Object>> getType() {
					return ModelTypes.Action.forType(Object.class);
				}

				@Override
				public ObservableAction<Object> get(ModelSetInstance models) throws ModelInstantiationException {
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
					ModelSetInstance newModels) throws ModelInstantiationException {
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
					for (ModelValueSynth<ObservableAction<?>, ObservableAction<?>> execV : execVs) {
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
				public BetterList<ModelValueSynth<?, ?>> getCores() throws ExpressoInterpretationException {
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

	private CompiledModelValue<SettableValue<?>, SettableValue<Object>> createFirstValue(ExpressoQIS session)
		throws QonfigInterpretationException {
		List<CompiledModelValue<SettableValue<?>, SettableValue<?>>> valueCreators = session.interpretChildren("value",
			CompiledModelValue.class);
		return CompiledModelValue.of("first", ModelTypes.Value, () -> {
			List<ModelValueSynth<SettableValue<?>, SettableValue<?>>> valueContainers = new ArrayList<>(valueCreators.size());
			List<TypeToken<?>> valueTypes = new ArrayList<>(valueCreators.size());
			for (CompiledModelValue<SettableValue<?>, SettableValue<?>> creator : valueCreators) {
				valueContainers.add(creator.createSynthesizer());
				valueTypes.add(valueContainers.get(valueContainers.size() - 1).getType().getType(0));
			}
			TypeToken<Object> commonType = TypeTokens.get().getCommonType(valueTypes);
			return new AbstractValueContainer<SettableValue<?>, SettableValue<Object>>(ModelTypes.Value.forType(commonType)) {
				@Override
				public SettableValue<Object> get(ModelSetInstance models) throws ModelInstantiationException {
					SettableValue<?>[] vs = new SettableValue[valueCreators.size()];
					for (int i = 0; i < vs.length; i++)
						vs[i] = valueContainers.get(i).get(models);
					return SettableValue.firstValue(commonType, v -> v != null, () -> null, vs);
				}

				@Override
				public SettableValue<Object> forModelCopy(SettableValue<Object> value, ModelSetInstance sourceModels,
					ModelSetInstance newModels) throws ModelInstantiationException {
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
				public BetterList<ModelValueSynth<?, ?>> getCores() throws ExpressoInterpretationException {
					return BetterList.of(valueContainers.stream(), vc -> vc.getCores().stream());
				}
			};
		});
	}

	private <V> CompiledModelValue<Observable<?>, Observable<V>> createHook(ExpressoQIS session) throws QonfigInterpretationException {
		CompiledExpression onX = session.getAttributeExpression("on");
		CompiledExpression actionX = session.getValueExpression();
		return CompiledModelValue.of("hook", ModelTypes.Event, () -> {
			ModelValueSynth<Observable<?>, Observable<V>> onC = onX == null ? null : onX.evaluate(ModelTypes.Event.<V> anyAs());
			ModelInstanceType.SingleTyped<Observable<?>, V, Observable<V>> eventType;
			eventType = onC == null ? ModelTypes.Event.forType((Class<V>) void.class)
				: (ModelInstanceType.SingleTyped<Observable<?>, V, Observable<V>>) onC.getType();
			DynamicModelValue.satisfyDynamicValueType("event", session.getExpressoEnv().getModels(),
				ModelTypes.Value.forType(eventType.getValueType()));
			ModelValueSynth<ObservableAction<?>, ObservableAction<?>> actionC = actionX.evaluate(ModelTypes.Action.any());
			session.interpretLocalModel();
			return ModelValueSynth.of(eventType, msi -> {
				msi = session.wrapLocal(msi);
				Observable<V> on = onC == null ? null : onC.get(msi);
				ObservableAction<?> action = actionC.get(msi);
				SettableValue<V> event = SettableValue.build(eventType.getValueType())//
					.withValue(TypeTokens.get().getDefaultValue(eventType.getValueType())).build();
				try {
					DynamicModelValue.satisfyDynamicValue("event", ModelTypes.Value.forType(eventType.getValueType()), msi, event);
				} catch (ModelException | TypeConversionException e) {
					throw new ModelInstantiationException("Could not satisfy event variable", session.getElement().getPositionInFile(), 0);
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
	 * @throws ExpressoInterpretationException If the value in the session for the given key is not a type
	 */
	public static <T> TypeToken<T> getType(ExpressoQIS session, String typeKey) throws ExpressoInterpretationException {
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
		interpreter.createWith("transform", CompiledModelValue.class, session -> createTransform(wrap(session)));
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

		interpreter.delegateToType("map-reverse", "type", CompiledMapReverse.class)//
		.createWith("replace-source", CompiledMapReverse.class, session -> createSourceReplace(wrap(session)))//
		.createWith("modify-source", CompiledMapReverse.class, session -> createSourceModifier(wrap(session)));
	}

	private CompiledModelValue<?, ?> createTransform(ExpressoQIS session) throws QonfigInterpretationException {
		CompiledExpression sourceX = session.getAttributeExpression("source");
		ModelType<Object> modelType = (ModelType<Object>) sourceX.getModelType();
		ObservableCompiledTransform<Object, Object> compiledTransform = ObservableCompiledTransform.unity(modelType);
		List<ExpressoQIS> ops = session.forChildren("op");
		for (ExpressoQIS op : ops) {
			@SuppressWarnings("rawtypes")
			Class<? extends ObservableCompiledTransform> transformType = getTransformFor(modelType);
			if (transformType == null) {
				throw new QonfigInterpretationException("No transform supported for model type " + modelType,
					op.getElement().getPositionInFile(), 0);
			} else if (!op.supportsInterpretation(transformType)) {
				throw new QonfigInterpretationException(
					"No transform supported for operation type " + op.getFocusType().getName() + " for model type " + modelType,
					op.getElement().getPositionInFile(), 0);
			}
			ObservableCompiledTransform<Object, Object> next;
			try {
				op.put(VALUE_TYPE_KEY, modelType);
				next = op.interpret(transformType);
			} catch (RuntimeException e) {
				throw new QonfigInterpretationException("Could not interpret operation " + op.toString() + " as a transformation from "
					+ modelType + " via " + transformType.getName(), op.getElement().getPositionInFile(), 0, e.getCause());
			}
			compiledTransform = next.after(compiledTransform);
			modelType = (ModelType<Object>) compiledTransform.getTargetModelType();
		}
		ObservableCompiledTransform<Object, Object> fTransform = compiledTransform;
		return CompiledModelValue.of("transform", modelType, () -> {
			ModelValueSynth<Object, Object> compiledSource = sourceX
				.evaluate((ModelInstanceType<Object, Object>) sourceX.getModelType().any());
			ObservableStructureTransform<Object, Object, ?, ?> transform = fTransform.createTransform(compiledSource.getType());
			return new ModelValueSynth<Object, Object>() {
				@Override
				public ModelType<Object> getModelType() {
					return (ModelType<Object>) transform.getTargetType().getModelType();
				}

				@Override
				public ModelInstanceType<Object, Object> getType() throws ExpressoInterpretationException {
					return (ModelInstanceType<Object, Object>) transform.getTargetType();
				}

				@Override
				public Object get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
					Object sourceV = compiledSource.get(models);
					try {
						return transform.transform(sourceV, models);
					} catch (CheckedExceptionWrapper e) {
						if (e.getCause() instanceof ModelInstantiationException)
							throw (ModelInstantiationException) e.getCause();
						else
							throw new ModelInstantiationException(e.getCause().getMessage(), session.getElement().getPositionInFile(), 0,
								e.getCause());
					} catch (RuntimeException | Error e) {
						throw new ModelInstantiationException(e.getCause().getMessage(), session.getElement().getPositionInFile(), 0,
							e.getCause());
					}
				}

				@Override
				public BetterList<ModelValueSynth<?, ?>> getCores() throws ExpressoInterpretationException {
					return BetterList.of(Stream.concat(compiledSource.getCores().stream(), transform.getCores().stream()));
				}

				@Override
				public Object forModelCopy(Object value, ModelSetInstance sourceModels, ModelSetInstance newModels)
					throws ModelInstantiationException {
					Object sourceS = compiledSource.get(sourceModels);
					Object sourceN = compiledSource.forModelCopy(sourceS, sourceModels, newModels);
					if (sourceS != sourceN || transform.isDifferent(sourceModels, newModels))
						return transform.transform(sourceN, newModels);
					else
						return value;
				}
			};
		});
	}

	/**
	 * A compiled struct capable of generating an {@link ObservableStructureTransform}
	 *
	 * @param <M1> The model type of the source value that this transformation accepts
	 * @param <M2> The model type of the target value that this transformation produces
	 */
	public interface ObservableCompiledTransform<M1, M2> {
		/** @return The model type of the target value that this transformation produces */
		ModelType<? extends M2> getTargetModelType();

		/**
		 * @param <MV1> The model type of the source value
		 * @param sourceType The model type of the source value
		 * @return A transformer for transforming actual values
		 * @throws ExpressoInterpretationException If the transformer could not be produced
		 */
		<MV1 extends M1> ObservableStructureTransform<M1, MV1, ? extends M2, ?> createTransform(
			ModelInstanceType<? extends M1, ? extends MV1> sourceType) throws ExpressoInterpretationException;

		/**
		 * @param <M0> The original source model type
		 * @param previous The original transformation which this transform comes after
		 * @return A transformation capable of transforming a source observable structure for <code>previous</code> to this transformation's
		 *         structure
		 */
		default <M0> ObservableCompiledTransform<M0, M2> after(ObservableCompiledTransform<M0, M1> previous) {
			ObservableCompiledTransform<M1, M2> next = this;
			return new ObservableCompiledTransform<M0, M2>() {
				@Override
				public ModelType<? extends M2> getTargetModelType() {
					return next.getTargetModelType();
				}

				@Override
				public <MV0 extends M0> ObservableStructureTransform<M0, MV0, ? extends M2, ?> createTransform(
					ModelInstanceType<? extends M0, ? extends MV0> sourceType) throws ExpressoInterpretationException {
					ObservableStructureTransform<M0, MV0, ? extends M1, ?> intermediate = previous.createTransform(sourceType);
					return _after(intermediate);
				}

				private <MV0 extends M0, MV1 extends M1> ObservableStructureTransform<M0, MV0, ? extends M2, ?> _after(
					ObservableStructureTransform<M0, MV0, ? extends M1, MV1> intermediate) throws ExpressoInterpretationException {
					ObservableStructureTransform<M1, MV1, ? extends M2, ?> nextOST = next.createTransform(intermediate.getTargetType());
					return nextOST.after(intermediate);
				}

				@Override
				public String toString() {
					return previous + "->" + next;
				}
			};
		}

		/**
		 * @param <M> The model type of the source value
		 * @param modelType The model type of the source value
		 * @return A trivial transformer that returns the source value
		 */
		public static <M> ObservableCompiledTransform<M, M> unity(ModelType<M> modelType) {
			return new ObservableCompiledTransform<M, M>() {
				@Override
				public ModelType<M> getTargetModelType() {
					return modelType;
				}

				@Override
				public <MV1 extends M> ObservableStructureTransform<M, MV1, ? extends M, ?> createTransform(
					ModelInstanceType<? extends M, ? extends MV1> sourceType) throws ExpressoInterpretationException {
					return ObservableStructureTransform.unity(sourceType);
				}
			};
		}
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
		ModelInstanceType<? extends M2, ? extends MV2> getTargetType();

		/**
		 * @param source The source observable
		 * @param models The models to use for the transformation
		 * @return The transformed observable
		 * @throws ModelInstantiationException If the transformation fails
		 */
		MV2 transform(MV1 source, ModelSetInstance models) throws ModelInstantiationException;

		/**
		 * Helps support the {@link ModelValueSynth#forModelCopy(Object, ModelSetInstance, ModelSetInstance)} method
		 *
		 * @param sourceModels The source model instance
		 * @param newModels The new model instance
		 * @return Whether observables produced by this transform would be different between the two models
		 * @throws ModelInstantiationException If the inspection fails
		 */
		boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException;

		/**
		 * @return The cores of any model values that compose this transform
		 * @throws ExpressoInterpretationException If the cores cannot be retrieved
		 */
		BetterList<ModelValueSynth<?, ?>> getCores() throws ExpressoInterpretationException;

		/**
		 * @param <M0> The original source model type
		 * @param <MV0> The original source type
		 * @param previous The original transformation which this transform comes after
		 * @return A transformation capable of transforming a source observable structure for <code>previous</code> to this transformation's
		 *         structure
		 */
		default <M0, MV0 extends M0> ObservableStructureTransform<M0, MV0, M2, MV2> after(
			ObservableStructureTransform<M0, MV0, ? extends M1, ? extends MV1> previous) {
			ObservableStructureTransform<M1, MV1, M2, MV2> next = this;
			return new ObservableStructureTransform<M0, MV0, M2, MV2>() {
				@Override
				public ModelInstanceType<? extends M2, ? extends MV2> getTargetType() {
					return next.getTargetType();
				}

				@Override
				public MV2 transform(MV0 source, ModelSetInstance models) throws ModelInstantiationException {
					MV1 intermediate = previous.transform(source, models);
					return next.transform(intermediate, models);
				}

				@Override
				public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
					return previous.isDifferent(sourceModels, newModels) || next.isDifferent(sourceModels, newModels);
				}

				@Override
				public BetterList<ModelValueSynth<?, ?>> getCores() throws ExpressoInterpretationException {
					return BetterList.of(Stream.concat(previous.getCores().stream(), next.getCores().stream()));
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
		static <M, MV extends M> ObservableStructureTransform<M, MV, M, MV> unity(ModelInstanceType<? extends M, ? extends MV> type) {
			return new ObservableStructureTransform<M, MV, M, MV>() {
				@Override
				public ModelInstanceType<? extends M, ? extends MV> getTargetType() {
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
				public BetterList<ModelValueSynth<?, ?>> getCores() {
					return BetterList.empty();
				}

				@Override
				public String toString() {
					return "init";
				}
			};
		}
	}

	/**
	 * Gets the transformation type for an observable model type. This will be used to query {@link AbstractQIS#interpret(Class)} to satisfy
	 * a transformation operation.
	 *
	 * @param modelType The model type to get the transform type for
	 * @return The type of transformation known to be able to handle observable structures of the given model type
	 */
	protected <M> Class<? extends ObservableCompiledTransform<M, ?>> getTransformFor(ModelType<M> modelType) {
		if (modelType == ModelTypes.Event)
			return (Class<? extends ObservableCompiledTransform<M, ?>>) ObservableTransform.class;
		else if (modelType == ModelTypes.Action)
			return (Class<? extends ObservableCompiledTransform<M, ?>>) ActionTransform.class;
		else if (modelType == ModelTypes.Value)
			return (Class<? extends ObservableCompiledTransform<M, ?>>) ValueTransform.class;
		else if (modelType == ModelTypes.Collection || modelType == ModelTypes.Set || modelType == ModelTypes.SortedCollection
			|| modelType == ModelTypes.SortedSet)
			return (Class<? extends ObservableCompiledTransform<M, ?>>) CollectionTransform.class;
		else
			return null;
	}

	/**
	 * A transformer capable of transforming an {@link Observable}
	 *
	 * @param <M> The model type of the target observable structure
	 */
	public interface ObservableTransform<M> extends ObservableCompiledTransform<Observable<?>, M> {
		/**
		 * Creates an {@link ObservableTransform}
		 *
		 * @param <S> The value type of the source observable
		 * @param <M2> The model type of the target observable structure
		 * @param modelType The model type of the target observable structure
		 * @param transform Function to produce the transformer given type information
		 * @return The {@link ObservableTransform}
		 */
		public static <S, M2> ObservableTransform<M2> create(ModelType<M2> modelType,
			ExFunction<SingleTyped<Observable<?>, S, Observable<S>>, ObservableStructureTransform<Observable<?>, Observable<S>, M2, ?>, ExpressoInterpretationException> transform) {
			return new ObservableTransform<M2>() {
				@Override
				public ModelType<M2> getTargetModelType() {
					return modelType;
				}

				@Override
				public <MV1 extends Observable<?>> ObservableStructureTransform<Observable<?>, MV1, M2, ?> createTransform(
					ModelInstanceType<? extends Observable<?>, ? extends MV1> sourceType) throws ExpressoInterpretationException {
					return (ObservableStructureTransform<Observable<?>, MV1, M2, ?>) transform
						.apply((SingleTyped<Observable<?>, S, Observable<S>>) sourceType);
				}
			};
		}

		/**
		 * Creates an observable->observable transformer
		 *
		 * @param <S> The type of the source observable
		 * @param <T> The type of the target observable
		 * @param type The type of the target observable
		 * @param transform A function to do the transformation
		 * @param difference Implementation for the {@link ObservableStructureTransform#isDifferent(ModelSetInstance, ModelSetInstance)}
		 *        method
		 * @param name The name for the transformation (just for {@link #toString()})
		 * @param components Component values that contribute to the transformation
		 * @return The transformer
		 */
		static <S, T> ObservableStructureTransform<Observable<?>, Observable<S>, Observable<?>, Observable<T>> of(TypeToken<T> type,
			ExBiFunction<Observable<S>, ModelSetInstance, Observable<T>, ModelInstantiationException> transform,
			ExBiPredicate<ModelSetInstance, ModelSetInstance, ModelInstantiationException> difference, Supplier<String> name,
			ModelValueSynth<?, ?>... components) {
			return new ObservableStructureTransform<Observable<?>, Observable<S>, Observable<?>, Observable<T>>() {
				@Override
				public ModelInstanceType<Observable<?>, Observable<T>> getTargetType() {
					return ModelTypes.Event.forType(type);
				}

				@Override
				public Observable<T> transform(Observable<S> source, ModelSetInstance models) throws ModelInstantiationException {
					return transform.apply(source, models);
				}

				@Override
				public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
					return difference != null && difference.test(sourceModels, newModels);
				}

				@Override
				public BetterList<ModelValueSynth<?, ?>> getCores() throws ExpressoInterpretationException {
					return BetterList.of(Stream.of(components), c -> c.getCores().stream());
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
		 * @param difference Implementation for the {@link ObservableStructureTransform#isDifferent(ModelSetInstance, ModelSetInstance)}
		 *        method
		 * @param name The name for the transformation (just for {@link #toString()})
		 * @param components Component values that contribute to the transformation
		 * @return The transformer
		 */
		static <S, M, MV extends M> ObservableStructureTransform<Observable<?>, Observable<S>, M, MV> of(ModelInstanceType<M, MV> type,
			ExBiFunction<Observable<S>, ModelSetInstance, MV, ModelInstantiationException> transform,
			ExBiPredicate<ModelSetInstance, ModelSetInstance, ModelInstantiationException> difference, Supplier<String> name,
			ModelValueSynth<?, ?>... components) {
			return new ObservableStructureTransform<Observable<?>, Observable<S>, M, MV>() {
				@Override
				public ModelInstanceType<M, MV> getTargetType() {
					return type;
				}

				@Override
				public MV transform(Observable<S> source, ModelSetInstance models) throws ModelInstantiationException {
					return transform.apply(source, models);
				}

				@Override
				public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
					return difference != null && difference.test(sourceModels, newModels);
				}

				@Override
				public BetterList<ModelValueSynth<?, ?>> getCores() throws ExpressoInterpretationException {
					return BetterList.of(Stream.of(components), c -> c.getCores().stream());
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
	 * @param <M> The model type of the target observable structure
	 */
	public interface ActionTransform<M> extends ObservableCompiledTransform<ObservableAction<?>, M> {
		/**
		 * Creates an {@link ActionTransform}
		 *
		 * @param <S> The value type of the source action
		 * @param <M2> The model type of the target observable structure
		 * @param modelType The model type of the target observable structure
		 * @param transform Function to produce the transformer given type information
		 * @return The {@link ActionTransform}
		 */
		public static <S, M2> ActionTransform<M2> create(ModelType<M2> modelType,
			ExFunction<SingleTyped<ObservableAction<?>, S, ObservableAction<S>>, ObservableStructureTransform<ObservableAction<?>, ObservableAction<S>, M2, ?>, ExpressoInterpretationException> transform) {
			return new ActionTransform<M2>() {
				@Override
				public ModelType<M2> getTargetModelType() {
					return modelType;
				}

				@Override
				public <MV1 extends ObservableAction<?>> ObservableStructureTransform<ObservableAction<?>, MV1, M2, ?> createTransform(
					ModelInstanceType<? extends ObservableAction<?>, ? extends MV1> sourceType) throws ExpressoInterpretationException {
					return (ObservableStructureTransform<ObservableAction<?>, MV1, M2, ?>) transform
						.apply((SingleTyped<ObservableAction<?>, S, ObservableAction<S>>) sourceType);
				}
			};
		}

		/**
		 * Creates an action->action transformer
		 *
		 * @param <S> The type of the source action
		 * @param <T> The type of the target action
		 * @param type The type of the target action
		 * @param transform A function to do the transformation
		 * @param difference Implementation for the {@link ObservableStructureTransform#isDifferent(ModelSetInstance, ModelSetInstance)}
		 *        method
		 * @param name The name for the transformation (just for {@link #toString()})
		 * @param components Component values that contribute to the transformation
		 * @return The transformer
		 */
		static <S, T> ObservableStructureTransform<ObservableAction<?>, ObservableAction<S>, ObservableAction<?>, ObservableAction<T>> of(
			TypeToken<T> type,
			ExBiFunction<ObservableAction<S>, ModelSetInstance, ObservableAction<T>, ModelInstantiationException> transform,
			ExBiPredicate<ModelSetInstance, ModelSetInstance, ModelInstantiationException> difference, Supplier<String> name,
			ModelValueSynth<?, ?>... components) {
			return new ObservableStructureTransform<ObservableAction<?>, ObservableAction<S>, ObservableAction<?>, ObservableAction<T>>() {
				@Override
				public ModelInstanceType<ObservableAction<?>, ObservableAction<T>> getTargetType() {
					return ModelTypes.Action.forType(type);
				}

				@Override
				public ObservableAction<T> transform(ObservableAction<S> source, ModelSetInstance models)
					throws ModelInstantiationException {
					return transform.apply(source, models);
				}

				@Override
				public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
					return difference != null && difference.test(sourceModels, newModels);
				}

				@Override
				public BetterList<ModelValueSynth<?, ?>> getCores() throws ExpressoInterpretationException {
					return BetterList.of(Stream.of(components), c -> c.getCores().stream());
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
		 * @param difference Implementation for the {@link ObservableStructureTransform#isDifferent(ModelSetInstance, ModelSetInstance)}
		 *        method
		 * @param name The name for the transformation (just for {@link #toString()})
		 * @param components Component values that contribute to the transformation
		 * @return The transformer
		 */
		static <S, M, MV extends M> ObservableStructureTransform<ObservableAction<?>, ObservableAction<S>, M, MV> of(
			ModelInstanceType<M, MV> type, ExBiFunction<ObservableAction<S>, ModelSetInstance, MV, ModelInstantiationException> transform,
			ExBiPredicate<ModelSetInstance, ModelSetInstance, ModelInstantiationException> difference, Supplier<String> name,
			ModelValueSynth<?, ?>... components) {
			return new ObservableStructureTransform<ObservableAction<?>, ObservableAction<S>, M, MV>() {
				@Override
				public ModelInstanceType<M, MV> getTargetType() {
					return type;
				}

				@Override
				public MV transform(ObservableAction<S> source, ModelSetInstance models) throws ModelInstantiationException {
					return transform.apply(source, models);
				}

				@Override
				public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
					return difference != null && difference.test(sourceModels, newModels);
				}

				@Override
				public BetterList<ModelValueSynth<?, ?>> getCores() throws ExpressoInterpretationException {
					return BetterList.of(Stream.of(components), c -> c.getCores().stream());
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
	 * @param <M> The model type of the target observable structure
	 */
	public interface ValueTransform<M> extends ObservableCompiledTransform<SettableValue<?>, M> {
		/**
		 * Creates n {@link ValueTransform}
		 *
		 * @param <S> The value type of the source value
		 * @param <M2> The model type of the target observable structure
		 * @param modelType The model type of the target observable structure
		 * @param transform Function to produce the transformer given type information
		 * @return The {@link ValueTransform}
		 */
		public static <S, M2> ValueTransform<M2> create(ModelType<M2> modelType,
			ExFunction<SingleTyped<SettableValue<?>, S, SettableValue<S>>, ObservableStructureTransform<SettableValue<?>, SettableValue<S>, M2, ?>, ExpressoInterpretationException> transform) {
			return new ValueTransform<M2>() {
				@Override
				public ModelType<M2> getTargetModelType() {
					return modelType;
				}

				@Override
				public <MV1 extends SettableValue<?>> ObservableStructureTransform<SettableValue<?>, MV1, M2, ?> createTransform(
					ModelInstanceType<? extends SettableValue<?>, ? extends MV1> sourceType) throws ExpressoInterpretationException {
					return (ObservableStructureTransform<SettableValue<?>, MV1, M2, ?>) transform
						.apply((SingleTyped<SettableValue<?>, S, SettableValue<S>>) sourceType);
				}
			};
		}

		/**
		 * Creates a value->value transformer
		 *
		 * @param <S> The type of the source value
		 * @param <T> The type of the target value
		 * @param type The type of the target value
		 * @param transform A function to do the transformation
		 * @param difference Implementation for the {@link ObservableStructureTransform#isDifferent(ModelSetInstance, ModelSetInstance)}
		 *        method
		 * @param name The name for the transformation (just for {@link #toString()})
		 * @param components Component values that contribute to the transformation
		 * @return The transformer
		 */
		static <S, T> ObservableStructureTransform<SettableValue<?>, SettableValue<S>, SettableValue<?>, SettableValue<T>> of(
			TypeToken<T> type, ExBiFunction<SettableValue<S>, ModelSetInstance, SettableValue<T>, ModelInstantiationException> transform,
			ExBiPredicate<ModelSetInstance, ModelSetInstance, ModelInstantiationException> difference, Supplier<String> name,
			ModelValueSynth<?, ?>... components) {
			return new ObservableStructureTransform<SettableValue<?>, SettableValue<S>, SettableValue<?>, SettableValue<T>>() {
				@Override
				public ModelInstanceType<SettableValue<?>, SettableValue<T>> getTargetType() {
					return ModelTypes.Value.forType(type);
				}

				@Override
				public SettableValue<T> transform(SettableValue<S> source, ModelSetInstance models) throws ModelInstantiationException {
					return transform.apply(source, models);
				}

				@Override
				public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
					return difference != null && difference.test(sourceModels, newModels);
				}

				@Override
				public BetterList<ModelValueSynth<?, ?>> getCores() throws ExpressoInterpretationException {
					return BetterList.of(Stream.of(components), c -> c.getCores().stream());
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
		 * @param difference Implementation for the {@link ObservableStructureTransform#isDifferent(ModelSetInstance, ModelSetInstance)}
		 *        method
		 * @param name The name for the transformation (just for {@link #toString()})
		 * @param components Component values that contribute to the transformation
		 * @return The transformer
		 */
		static <S, M, MV extends M> ObservableStructureTransform<SettableValue<?>, SettableValue<S>, M, MV> of(
			ModelInstanceType<M, MV> type, ExBiFunction<SettableValue<S>, ModelSetInstance, MV, ModelInstantiationException> transform,
			ExBiPredicate<ModelSetInstance, ModelSetInstance, ModelInstantiationException> difference, Supplier<String> name,
			ModelValueSynth<?, ?>... components) {
			return new ObservableStructureTransform<SettableValue<?>, SettableValue<S>, M, MV>() {
				@Override
				public ModelInstanceType<M, MV> getTargetType() {
					return type;
				}

				@Override
				public MV transform(SettableValue<S> source, ModelSetInstance models) throws ModelInstantiationException {
					return transform.apply(source, models);
				}

				@Override
				public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
					return difference != null && difference.test(sourceModels, newModels);
				}

				@Override
				public BetterList<ModelValueSynth<?, ?>> getCores() throws ExpressoInterpretationException {
					return BetterList.of(Stream.of(components), c -> c.getCores().stream());
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
	 * @param <M1> The model type of the source collection
	 * @param <M2> The model type of the target observable structure
	 */
	public interface CollectionTransform<M1 extends ObservableCollection<?>, M2> extends ObservableCompiledTransform<M1, M2> {
		/**
		 * Creates n {@link CollectionTransform}
		 *
		 * @param <M1> The model type of the source collection
		 * @param <S> The value type of the source collection
		 * @param <M2> The model type of the target observable structure
		 * @param sourceModelType The model type of the source collection
		 * @param targetModelType The model type of the target observable structure
		 * @param transform Function to produce the transformer given type information
		 * @return The {@link CollectionTransform}
		 */
		public static <M1 extends ObservableCollection<?>, S, MV1 extends M1, M2> CollectionTransform<M1, M2> create(
			ModelType<M1> sourceModelType, ModelType<M2> targetModelType,
			ExFunction<SingleTyped<M1, S, MV1>, ? extends ObservableStructureTransform<? super M1, ? super MV1, ? extends M2, ?>, ExpressoInterpretationException> transform) {
			return new CollectionTransform<M1, M2>() {
				@Override
				public ModelType<M2> getTargetModelType() {
					return targetModelType;
				}

				@Override
				public <MV11 extends M1> ObservableStructureTransform<M1, MV11, M2, ?> createTransform(
					ModelInstanceType<? extends M1, ? extends MV11> sourceType) throws ExpressoInterpretationException {
					return (ObservableStructureTransform<M1, MV11, M2, ?>) transform.apply((SingleTyped<M1, S, MV1>) sourceType);
				}
			};
		}

		/**
		 * Creates a collection transformer
		 *
		 * @param <S> The type of the source collection
		 * @param <M2> the model type of the target structure
		 * @param <MV2> The type of the target structure
		 * @param targetType The type of the target structure
		 * @param transform A function to do the transformation
		 * @param difference Implementation for the {@link ObservableStructureTransform#isDifferent(ModelSetInstance, ModelSetInstance)}
		 *        method
		 * @param name The name for the transformation (just for {@link #toString()})
		 * @param components Component values that contribute to the transformation
		 * @return The transformer
		 */
		static <S, T, M2 extends ObservableCollection<?>, MV2 extends M2> FlowCollectionTransform<S, T, ObservableCollection<?>, ObservableCollection<S>, M2, MV2> flowTransform(
			ModelInstanceType<M2, MV2> targetType,
			ExBiFunction<CollectionDataFlow<?, ?, S>, ModelSetInstance, ? extends CollectionDataFlow<?, ?, T>, ModelInstantiationException> transform,
			ExBiPredicate<ModelSetInstance, ModelSetInstance, ModelInstantiationException> difference, Supplier<String> name,
			ModelValueSynth<?, ?>... components) {
			return new FlowCollectionTransform<S, T, ObservableCollection<?>, ObservableCollection<S>, M2, MV2>() {
				@Override
				public ModelInstanceType<M2, MV2> getTargetType() {
					return targetType;
				}

				@Override
				public CollectionDataFlow<?, ?, T> transformFlow(CollectionDataFlow<?, ?, S> source, ModelSetInstance models)
					throws ModelInstantiationException {
					return transform.apply(source, models);
				}

				@Override
				public CollectionDataFlow<?, ?, T> transformToFlow(ObservableCollection<S> source, ModelSetInstance models)
					throws ModelInstantiationException {
					return transform.apply(source.flow(), models);
				}

				@Override
				public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
					return difference != null && difference.test(sourceModels, newModels);
				}

				@Override
				public BetterList<ModelValueSynth<?, ?>> getCores() throws ExpressoInterpretationException {
					return BetterList.of(Stream.of(components), c -> c.getCores().stream());
				}

				@Override
				public String toString() {
					return name.get();
				}
			};
		}

		/**
		 * Creates a collection transformer that creates a non-sorted, non-distinct result
		 *
		 * @param <S> The type of the source collection
		 * @param <T> The type of the target collection
		 * @param type The type of the target structure
		 * @param transform A function to do the transformation
		 * @param difference Implementation for the {@link ObservableStructureTransform#isDifferent(ModelSetInstance, ModelSetInstance)}
		 *        method
		 * @param name The name for the transformation (just for {@link #toString()})
		 * @param components Component values that contribute to the transformation
		 * @return The transformer
		 */
		static <S, T> FlowCollectionTransform<S, T, ObservableCollection<?>, ObservableCollection<S>, ObservableCollection<?>, ObservableCollection<T>> flowTransform(
			TypeToken<T> type,
			ExBiFunction<CollectionDataFlow<?, ?, S>, ModelSetInstance, ? extends CollectionDataFlow<?, ?, T>, ModelInstantiationException> transform,
			ExBiPredicate<ModelSetInstance, ModelSetInstance, ModelInstantiationException> difference, Supplier<String> name,
			ModelValueSynth<?, ?>... components) {
			return new FlowCollectionTransform<S, T, ObservableCollection<?>, ObservableCollection<S>, ObservableCollection<?>, ObservableCollection<T>>() {
				@Override
				public ModelInstanceType<ObservableCollection<?>, ObservableCollection<T>> getTargetType() {
					return ModelTypes.Collection.forType(type);
				}

				@Override
				public CollectionDataFlow<?, ?, T> transformFlow(CollectionDataFlow<?, ?, S> source, ModelSetInstance models)
					throws ModelInstantiationException {
					return transform.apply(source, models);
				}

				@Override
				public CollectionDataFlow<?, ?, T> transformToFlow(ObservableCollection<S> source, ModelSetInstance models)
					throws ModelInstantiationException {
					return transform.apply(source.flow(), models);
				}

				@Override
				public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
					return difference != null && difference.test(sourceModels, newModels);
				}

				@Override
				public BetterList<ModelValueSynth<?, ?>> getCores() throws ExpressoInterpretationException {
					return BetterList.of(Stream.of(components), c -> c.getCores().stream());
				}

				@Override
				public String toString() {
					return name.get();
				}
			};
		}

		/**
		 * Creates a collection transformer that creates a sorted, non-distinct result
		 *
		 * @param <S> The type of the source collection
		 * @param <T> The type of the target collection
		 * @param type The type of the target structure
		 * @param transform A function to do the transformation
		 * @param difference Implementation for the {@link ObservableStructureTransform#isDifferent(ModelSetInstance, ModelSetInstance)}
		 *        method
		 * @param name The name for the transformation (just for {@link #toString()})
		 * @param components Component values that contribute to the transformation
		 * @return The transformer
		 */
		static <S, T> FlowCollectionTransform<S, T, ObservableCollection<?>, ObservableCollection<S>, ObservableSortedCollection<?>, ObservableSortedCollection<T>> sortedFlowTransform(
			TypeToken<T> type,
			ExBiFunction<CollectionDataFlow<?, ?, S>, ModelSetInstance, ? extends SortedDataFlow<?, ?, T>, ModelInstantiationException> transform,
			ExBiPredicate<ModelSetInstance, ModelSetInstance, ModelInstantiationException> difference, Supplier<String> name,
			ModelValueSynth<?, ?>... components) {
			return new FlowCollectionTransform<S, T, ObservableCollection<?>, ObservableCollection<S>, ObservableSortedCollection<?>, ObservableSortedCollection<T>>() {
				@Override
				public ModelInstanceType<ObservableSortedCollection<?>, ObservableSortedCollection<T>> getTargetType() {
					return ModelTypes.SortedCollection.forType(type);
				}

				@Override
				public CollectionDataFlow<?, ?, T> transformFlow(CollectionDataFlow<?, ?, S> source, ModelSetInstance models)
					throws ModelInstantiationException {
					return transform.apply(source, models);
				}

				@Override
				public CollectionDataFlow<?, ?, T> transformToFlow(ObservableCollection<S> source, ModelSetInstance models)
					throws ModelInstantiationException {
					return transform.apply(source.flow(), models);
				}

				@Override
				public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
					return difference != null && difference.test(sourceModels, newModels);
				}

				@Override
				public BetterList<ModelValueSynth<?, ?>> getCores() throws ExpressoInterpretationException {
					return BetterList.of(Stream.of(components), c -> c.getCores().stream());
				}

				@Override
				public String toString() {
					return name.get();
				}
			};
		}

		/**
		 * Creates a collection transformer that creates a non-sorted, distinct result
		 *
		 * @param <S> The type of the source collection
		 * @param <T> The type of the target collection
		 * @param type The type of the target structure
		 * @param transform A function to do the transformation
		 * @param difference Implementation for the {@link ObservableStructureTransform#isDifferent(ModelSetInstance, ModelSetInstance)}
		 *        method
		 * @param name The name for the transformation (just for {@link #toString()})
		 * @param components Component values that contribute to the transformation
		 * @return The transformer
		 */
		static <S, T> FlowCollectionTransform<S, T, ObservableCollection<?>, ObservableCollection<S>, ObservableSet<?>, ObservableSet<T>> distinctFlowTransform(
			TypeToken<T> type,
			ExBiFunction<CollectionDataFlow<?, ?, S>, ModelSetInstance, ? extends DistinctDataFlow<?, ?, T>, ModelInstantiationException> transform,
			ExBiPredicate<ModelSetInstance, ModelSetInstance, ModelInstantiationException> difference, Supplier<String> name,
			ModelValueSynth<?, ?>... components) {
			return new FlowCollectionTransform<S, T, ObservableCollection<?>, ObservableCollection<S>, ObservableSet<?>, ObservableSet<T>>() {
				@Override
				public ModelInstanceType<ObservableSet<?>, ObservableSet<T>> getTargetType() {
					return ModelTypes.Set.forType(type);
				}

				@Override
				public CollectionDataFlow<?, ?, T> transformFlow(CollectionDataFlow<?, ?, S> source, ModelSetInstance models)
					throws ModelInstantiationException {
					return transform.apply(source, models);
				}

				@Override
				public CollectionDataFlow<?, ?, T> transformToFlow(ObservableCollection<S> source, ModelSetInstance models)
					throws ModelInstantiationException {
					return transform.apply(source.flow(), models);
				}

				@Override
				public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
					return difference != null && difference.test(sourceModels, newModels);
				}

				@Override
				public BetterList<ModelValueSynth<?, ?>> getCores() throws ExpressoInterpretationException {
					return BetterList.of(Stream.of(components), c -> c.getCores().stream());
				}

				@Override
				public String toString() {
					return name.get();
				}
			};
		}

		/**
		 * Creates a collection transformer that creates a sorted, distinct result
		 *
		 * @param <S> The type of the source collection
		 * @param <T> The type of the target collection
		 * @param type The type of the target structure
		 * @param transform A function to do the transformation
		 * @param difference Implementation for the {@link ObservableStructureTransform#isDifferent(ModelSetInstance, ModelSetInstance)}
		 *        method
		 * @param name The name for the transformation (just for {@link #toString()})
		 * @param components Component values that contribute to the transformation
		 * @return The transformer
		 */
		static <S, T> FlowCollectionTransform<S, T, ObservableCollection<?>, ObservableCollection<S>, ObservableSortedSet<?>, ObservableSortedSet<T>> distinctSortedFlowTransform(
			TypeToken<T> type,
			ExBiFunction<CollectionDataFlow<?, ?, S>, ModelSetInstance, ? extends DistinctSortedDataFlow<?, ?, T>, ModelInstantiationException> transform,
			ExBiPredicate<ModelSetInstance, ModelSetInstance, ModelInstantiationException> difference, Supplier<String> name,
			ModelValueSynth<?, ?>... components) {
			return new FlowCollectionTransform<S, T, ObservableCollection<?>, ObservableCollection<S>, ObservableSortedSet<?>, ObservableSortedSet<T>>() {
				@Override
				public ModelInstanceType<ObservableSortedSet<?>, ObservableSortedSet<T>> getTargetType() {
					return ModelTypes.SortedSet.forType(type);
				}

				@Override
				public CollectionDataFlow<?, ?, T> transformFlow(CollectionDataFlow<?, ?, S> source, ModelSetInstance models)
					throws ModelInstantiationException {
					return transform.apply(source, models);
				}

				@Override
				public CollectionDataFlow<?, ?, T> transformToFlow(ObservableCollection<S> source, ModelSetInstance models)
					throws ModelInstantiationException {
					return transform.apply(source.flow(), models);
				}

				@Override
				public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
					return difference != null && difference.test(sourceModels, newModels);
				}

				@Override
				public BetterList<ModelValueSynth<?, ?>> getCores() throws ExpressoInterpretationException {
					return BetterList.of(Stream.of(components), c -> c.getCores().stream());
				}

				@Override
				public String toString() {
					return name.get();
				}
			};
		}

		/**
		 * @param <S> The value type of the source collection
		 * @param <C> The type of the source collection
		 * @param modelType The model type of the source
		 * @return A trivial transform that simply returns the source
		 */
		static <S, C extends ObservableCollection<?>> CollectionTransform<C, C> trivial(ModelType<C> modelType) {
			return new CollectionTransform<C, C>() {
				@Override
				public ModelType<? extends C> getTargetModelType() {
					return modelType;
				}

				@Override
				public <MV1 extends C> ObservableStructureTransform<C, MV1, ? extends C, ?> createTransform(
					ModelInstanceType<? extends C, ? extends MV1> sourceType) throws ExpressoInterpretationException {
					return (ObservableStructureTransform<C, MV1, C, ?>) FlowCollectionTransform
						.trivial((ModelInstanceType<C, C>) sourceType);
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
		 * @throws ModelInstantiationException If the transformation fails
		 */
		CollectionDataFlow<?, ?, T> transformFlow(CollectionDataFlow<?, ?, S> source, ModelSetInstance models)
			throws ModelInstantiationException;

		/**
		 * Transforms a source observable structure into a transformed flow
		 *
		 * @param source The source observable structure
		 * @param models The models to do the transformation
		 * @return The transformed flow
		 * @throws ModelInstantiationException If the transformation fails
		 */
		CollectionDataFlow<?, ?, T> transformToFlow(MV1 source, ModelSetInstance models) throws ModelInstantiationException;

		@Override
		default MV transform(MV1 source, ModelSetInstance models) throws ModelInstantiationException {
			ObservableCollection.CollectionDataFlow<?, ?, T> flow = transformToFlow(source, models);
			return (MV) flow.collect();
		}

		@Override
		default <M0, MV0 extends M0> ObservableStructureTransform<M0, MV0, M, MV> after(
			ObservableStructureTransform<M0, MV0, ? extends M1, ? extends MV1> previous) {
			if (previous instanceof CollectionTransform) {
				FlowTransform<M0, MV0, Object, S, ObservableCollection<?>, ObservableCollection<?>> prevTCT;
				prevTCT = (FlowTransform<M0, MV0, Object, S, ObservableCollection<?>, ObservableCollection<?>>) previous;
				FlowTransform<M1, MV1, S, T, M, MV> next = this;
				return new FlowTransform<M0, MV0, Object, T, M, MV>() {
					@Override
					public ModelInstanceType<? extends M, ? extends MV> getTargetType() {
						return next.getTargetType();
					}

					@Override
					public CollectionDataFlow<?, ?, T> transformToFlow(MV0 source, ModelSetInstance models)
						throws ModelInstantiationException {
						CollectionDataFlow<?, ?, S> sourceFlow = prevTCT.transformToFlow(source, models);
						return next.transformFlow(sourceFlow, models);
					}

					@Override
					public CollectionDataFlow<?, ?, T> transformFlow(CollectionDataFlow<?, ?, Object> source, ModelSetInstance models)
						throws ModelInstantiationException {
						CollectionDataFlow<?, ?, S> sourceFlow = prevTCT.transformFlow(source, models);
						return next.transformFlow(sourceFlow, models);
					}

					@Override
					public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels)
						throws ModelInstantiationException {
						return previous.isDifferent(sourceModels, newModels) || next.isDifferent(sourceModels, newModels);
					}

					@Override
					public BetterList<ModelValueSynth<?, ?>> getCores() throws ExpressoInterpretationException {
						return BetterList.of(Stream.concat(previous.getCores().stream(), next.getCores().stream()));
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
	 * A collection transformation that is also a {@link FlowTransform}. A simple collection transformation is inefficient at
	 * chain-transforming collections and a {@link FlowTransform} is capable of transforming source observable structures other than
	 * collections, but compiled collection transforms must return a structure transform that accepts collections. Generally, if a
	 * collection transform operation produces a collection, an instance of this interface should be returned.
	 *
	 * @param <S> The type of the collection
	 * @param <T> The type of the target collection
	 * @param <M1> The model type of the source collection
	 * @param <MV1> The type of the source collection
	 * @param <M2> The model type of the target observable structure
	 * @param <MV2> The type of the target observable structure
	 */

	public interface FlowCollectionTransform<S, T, M1 extends ObservableCollection<?>, MV1 extends M1, M2 extends ObservableCollection<?>, MV2 extends M2>
	extends FlowTransform<M1, MV1, S, T, M2, MV2> {
		/**
		 * @param <S> The value type of the source collection
		 * @param <C> The type of the source collection
		 * @param modelType The model type of the source
		 * @return A trivial transform that simply returns the source
		 */
		static <S, C extends ObservableCollection<?>> FlowCollectionTransform<S, S, C, C, C, C> trivial(ModelInstanceType<C, C> modelType) {
			return new FlowCollectionTransform<S, S, C, C, C, C>() {
				@Override
				public CollectionDataFlow<?, ?, S> transformFlow(CollectionDataFlow<?, ?, S> source, ModelSetInstance models)
					throws ModelInstantiationException {
					return source;
				}

				@Override
				public CollectionDataFlow<?, ?, S> transformToFlow(C source, ModelSetInstance models) throws ModelInstantiationException {
					return (CollectionDataFlow<?, ?, S>) source.flow();
				}

				@Override
				public ModelInstanceType<? extends C, ? extends C> getTargetType() {
					return modelType;
				}

				@Override
				public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
					return false;
				}

				@Override
				public BetterList<ModelValueSynth<?, ?>> getCores() {
					return BetterList.empty();
				}
			};
		}
	}

	// Event transform

	private ObservableTransform<Observable<?>> noInitObservable(ExpressoQIS op) throws QonfigInterpretationException {
		return ObservableTransform.create(ModelTypes.Event,
			sourceType -> ObservableTransform.of(sourceType.getValueType(), (source, models) -> source.noInit(), null, () -> "noInit"));
	}

	private ObservableTransform<Observable<?>> skipObservable(ExpressoQIS op) throws QonfigInterpretationException {
		int times = Integer.parseInt(op.getAttributeText("times"));
		return ObservableTransform.create(ModelTypes.Event, sourceType -> ObservableTransform.of(sourceType.getValueType(),
			(source, models) -> source.skip(times), null, () -> "skip(" + times + ")"));
	}

	private ObservableTransform<Observable<?>> takeObservable(ExpressoQIS op) throws QonfigInterpretationException {
		int times = Integer.parseInt(op.getAttributeText("times"));
		return ObservableTransform.create(ModelTypes.Event, sourceType -> ObservableTransform.of(sourceType.getValueType(),
			(source, models) -> source.take(times), null, () -> "take(" + times + ")"));
	}

	private ObservableTransform<Observable<?>> takeUntilObservable(ExpressoQIS op) throws QonfigInterpretationException {
		CompiledExpression untilX = op.getAttributeExpression("until");
		return ObservableTransform.create(ModelTypes.Event, sourceType -> {
			ModelValueSynth<Observable<?>, Observable<?>> until;
			try {
				until = untilX.evaluate(ModelTypes.Event.any());
			} catch (ExpressoInterpretationException e) {
				throw new ExpressoInterpretationException("Could not parse until", e.getPosition(), e.getErrorLength(), e);
			}
			return ObservableTransform.of(sourceType.getValueType(), (source, models) -> source.takeUntil(until.get(models)),
				(sourceModels, newModels) -> until.get(sourceModels) != until.get(newModels), () -> "takeUntil(" + until + ")", until);
		});
	}

	private <S, T> ObservableTransform<Observable<?>> mapObservableTo(ExpressoQIS op) throws QonfigInterpretationException {
		String sourceAs = op.getAttributeText("source-as");

		CompiledExpression mapX = op.getAttributeExpression("map");
		return ObservableTransform.create(ModelTypes.Event, sourceType -> {
			ObservableModelSet.Builder wrappedBuilder = op.getExpressoEnv().getModels().wrap("mapModel");
			RuntimeValuePlaceholder<SettableValue<?>, SettableValue<S>> sourcePlaceholder = wrappedBuilder.withRuntimeValue(sourceAs,
				ModelTypes.Value.forType((TypeToken<S>) sourceType.getValueType()));
			ObservableModelSet.Built wrapped = wrappedBuilder.build();
			InterpretedModelSet interpretedWrapped = wrapped.interpret();

			ModelValueSynth<SettableValue<?>, SettableValue<T>> map;
			TypeToken<T> targetType;
			try {
				map = mapX.evaluate(
					(ModelInstanceType<SettableValue<?>, SettableValue<T>>) (ModelInstanceType<?, ?>) ModelTypes.Value.any(),
					op.getExpressoEnv().with(wrapped, null));
				targetType = (TypeToken<T>) map.getType().getType(0).resolveType(SettableValue.class.getTypeParameters()[0]);
			} catch (ExpressoInterpretationException e) {
				throw new ExpressoInterpretationException("Could not parse map", e.getPosition(), e.getErrorLength(), e);
			}
			return ObservableTransform.of(targetType, (source, models) -> {
				SettableValue<S> sourceV = SettableValue.build((TypeToken<S>) sourceType.getValueType()).build();
				ModelSetInstance wrappedModel = interpretedWrapped.createInstance(models.getUntil()).withAll(models)//
					.with(sourcePlaceholder, sourceV)//
					.build();
				SettableValue<T> mappedV = map.get(wrappedModel);
				return source.map(s -> {
					sourceV.set((S) s, null);
					return mappedV.get();
				});
			}, null, () -> "map(" + map + ")", map);
		});
	}

	private <T> ObservableTransform<Observable<?>> filterObservable(ExpressoQIS op) throws QonfigInterpretationException {
		String sourceAs = op.getAttributeText("source-as");
		CompiledExpression testX = op.getAttributeExpression("test");
		return ObservableTransform.create(ModelTypes.Event, sourceType -> {
			ObservableModelSet.Builder wrappedBuilder = op.getExpressoEnv().getModels().wrap("filterModel");
			RuntimeValuePlaceholder<SettableValue<?>, SettableValue<T>> sourcePlaceholder = wrappedBuilder.withRuntimeValue(sourceAs,
				ModelTypes.Value.forType((TypeToken<T>) sourceType.getValueType()));
			ObservableModelSet.Built wrapped = wrappedBuilder.build();

			ModelValueSynth<SettableValue<?>, SettableValue<String>> test = parseFilter(testX, op.getExpressoEnv().with(wrapped, null),
				false);
			InterpretedModelSet interpretedWrapped = wrapped.interpret();

			return ObservableTransform.of(sourceType.getValueType(), (source, models) -> {
				SettableValue<T> sourceV = SettableValue.build((TypeToken<T>) sourceType.getValueType()).build();
				ModelSetInstance wrappedMSI = interpretedWrapped.createInstance(models.getUntil()).withAll(models)//
					.with(sourcePlaceholder, sourceV)//
					.build();
				SettableValue<String> message = test.get(wrappedMSI);
				return source.filter(v -> {
					sourceV.set((T) v, null);
					return message.get() == null;
				});
			}, null, () -> "filter(" + test + ")", test, test);
		});
	}

	private static ModelValueSynth<SettableValue<?>, SettableValue<String>> parseFilter(CompiledExpression testX, ExpressoEnv env,
		boolean preferMessage) throws ExpressoInterpretationException {
		ModelValueSynth<SettableValue<?>, SettableValue<String>> test;
		try {
			if (preferMessage)
				test = testX.evaluate(ModelTypes.Value.forType(String.class), env);
			else {
				test = testX.evaluate(ModelTypes.Value.forType(boolean.class), env)//
					.map(ModelTypes.Value.forType(String.class),
						bv -> SettableValue.asSettable(bv.map(String.class, b -> b ? null : "Not allowed"), __ -> "Not settable"));
			}
		} catch (ExpressoInterpretationException e) {
			try {
				if (preferMessage) {
					test = testX.evaluate(ModelTypes.Value.forType(boolean.class), env)//
						.map(ModelTypes.Value.forType(String.class),
							bv -> SettableValue.asSettable(bv.map(String.class, b -> b ? null : "Not allowed"), __ -> "Not settable"));
				} else
					test = testX.evaluate(ModelTypes.Value.forType(String.class), env);
			} catch (ExpressoInterpretationException e2) {
				throw new ExpressoInterpretationException("Could not interpret '" + testX + "' as a String or a boolean", e.getPosition(),
					e.getErrorLength(), e);
			}
		}
		return test;
	}

	private <S, T> ObservableTransform<Observable<?>> filterObservableByType(ExpressoQIS op) throws QonfigInterpretationException {
		CompiledExpression typeX = op.getAttributeExpression("type");
		return ObservableTransform.create(ModelTypes.Event, sourceType -> {
			ModelValueSynth<SettableValue<?>, SettableValue<Class<T>>> typeC;
			TypeToken<T> targetType;
			try {
				typeC = typeX.evaluate(ModelTypes.Value.forType(TypeTokens.get().keyFor(Class.class).wildCard()));
				targetType = (TypeToken<T>) typeC.getType().getType(0).resolveType(Class.class.getTypeParameters()[0]);
			} catch (ExpressoInterpretationException e) {
				throw new ExpressoInterpretationException("Could not parse type", e.getPosition(), e.getErrorLength(), e);
			}
			return ObservableTransform.of(targetType, (source, models) -> {
				Class<T> type = typeC.get(models).get();
				return source.filter(type);
			}, null, () -> "filter(" + targetType + ")", typeC);
		});
	}

	// Action transform

	private ActionTransform<ObservableAction<?>> disabledAction(ExpressoQIS op) throws QonfigInterpretationException {
		CompiledExpression enabledX = op.getAttributeExpression("with");
		return ActionTransform.create(ModelTypes.Action, sourceType -> {
			ModelValueSynth<SettableValue<?>, SettableValue<String>> enabled;
			try {
				enabled = enabledX.evaluate(ModelTypes.Value.forType(String.class), op.getExpressoEnv());
			} catch (ExpressoInterpretationException e) {
				throw new ExpressoInterpretationException("Could not parse enabled", e.getPosition(), e.getErrorLength(), e);
			}
			return ActionTransform.of(sourceType.getValueType(), (source, models) -> {
				return source.disableWith(enabled.get(models));
			}, (sourceModels, newModels) -> enabled.get(sourceModels) != enabled.get(newModels), () -> "disable(" + enabled + ")", enabled);
		});
	}

	// Value transform

	private ValueTransform<SettableValue<?>> disabledValue(ExpressoQIS op) throws QonfigInterpretationException {
		CompiledExpression enabledX = op.getAttributeExpression("with");
		return ValueTransform.create(ModelTypes.Value, sourceType -> {
			ModelValueSynth<SettableValue<?>, SettableValue<String>> enabled;
			try {
				enabled = enabledX.evaluate(ModelTypes.Value.forType(String.class), op.getExpressoEnv());
			} catch (ExpressoInterpretationException e) {
				throw new ExpressoInterpretationException("Could not parse enabled", e.getPosition(), e.getErrorLength(), e);
			}
			return ValueTransform.of(sourceType.getValueType(), (source, models) -> source.disableWith(enabled.get(models)),
				(sourceModels, newModels) -> enabled.get(sourceModels) != enabled.get(newModels), () -> "disable(" + enabled + ")",
				enabled);
		});
	}

	private <T> ValueTransform<SettableValue<?>> filterAcceptValue(ExpressoQIS op) throws QonfigInterpretationException {
		String sourceAs = op.getAttributeText("source-as");
		CompiledExpression testX = op.getAttributeExpression("test");
		return ValueTransform.<T, SettableValue<?>> create(ModelTypes.Value, sourceType -> {
			ObservableModelSet.Builder wrappedBuilder = op.getExpressoEnv().getModels().wrap("filterAcceptModel");
			RuntimeValuePlaceholder<SettableValue<?>, SettableValue<T>> sourcePlaceholder = wrappedBuilder.withRuntimeValue(sourceAs,
				sourceType);
			ObservableModelSet.Built wrapped = wrappedBuilder.build();

			ModelValueSynth<SettableValue<?>, SettableValue<String>> test = parseFilter(testX, op.getExpressoEnv().with(wrapped, null),
				true);
			InterpretedModelSet interpretedWrapped = wrapped.interpret();
			return ValueTransform.<T, T> of(sourceType.getValueType(), (source, models) -> {
				SettableValue<T> sourceV = SettableValue.build(sourceType.getValueType()).build();
				ModelSetInstance wrappedMSI = interpretedWrapped.createInstance(models.getUntil()).withAll(models)//
					.with(sourcePlaceholder, sourceV)//
					.build();
				SettableValue<String> message = test.get(wrappedMSI);
				return source.filterAccept(v -> {
					sourceV.set(v, null);
					return message.get();
				});
			}, null, () -> "filterAccept(" + test + ")", test);
		});
	}

	private <S, T> ValueTransform<SettableValue<?>> mapValueTo(ExpressoQIS op) throws QonfigInterpretationException {
		CompiledTransformation ctx;
		try {
			ctx = mapTransformation(op);
		} catch (QonfigInterpretationException e) {
			throw new QonfigInterpretationException("Could not parse map", e.getPosition(), e.getErrorLength(), e);
		}
		if (ctx.isReversible()) {
			return ValueTransform.<S, SettableValue<?>> create(ModelTypes.Value, sourceType -> {
				InterpretedTransformation<S, T> ptx;
				try {
					ptx = (InterpretedTransformation<S, T>) ctx.interpret(sourceType.getValueType());
				} catch (ExpressoInterpretationException e) {
					throw new ExpressoInterpretationException("Could not parse map", e.getPosition(), e.getErrorLength(), e);
				}
				return ValueTransform.of(ptx.getTargetType(), (v, models) -> v.transformReversible(ptx.getTargetType(), tx -> {
					try {
						return (Transformation.ReversibleTransformation<S, T>) ptx.transform(tx, models);
					} catch (ModelInstantiationException e) {
						throw new CheckedExceptionWrapper(e);
					}
				}), ptx::isDifferent, ptx::toString, ptx.getComponents());
			});
		} else {
			return ValueTransform.<S, SettableValue<?>> create(ModelTypes.Value, sourceType -> {
				InterpretedTransformation<S, T> ptx;
				try {
					ptx = (InterpretedTransformation<S, T>) ctx.interpret(sourceType.getValueType());
				} catch (ExpressoInterpretationException e) {
					throw new ExpressoInterpretationException("Could not parse map", e.getPosition(), e.getErrorLength(), e);
				}
				return ValueTransform.of(ptx.getTargetType(), (v, models) -> {
					ObservableValue<T> value = v.transform(ptx.getTargetType(), tx -> {
						try {
							return ptx.transform(tx, models);
						} catch (ModelInstantiationException e) {
							throw new CheckedExceptionWrapper(e);
						}
					});
					return SettableValue.asSettable(value, __ -> "No reverse configured for " + op.toString());
				}, ptx::isDifferent, ptx::toString, ptx.getComponents());
			});
		}
	}

	private <T> ValueTransform<SettableValue<?>> refreshValue(ExpressoQIS op) throws QonfigInterpretationException {
		CompiledExpression onX = op.getAttributeExpression("on");
		return ValueTransform.create(ModelTypes.Value, sourceType -> {
			ModelValueSynth<Observable<?>, Observable<?>> refresh;
			try {
				refresh = onX.evaluate(ModelTypes.Event.any(), op.getExpressoEnv());
			} catch (ExpressoInterpretationException e) {
				throw new ExpressoInterpretationException("Could not parse refresh", e.getPosition(), e.getErrorLength(), e);
			}
			return ValueTransform.of(sourceType.getValueType(), (v, models) -> v.refresh(refresh.get(models)),
				(sourceModels, newModels) -> refresh.get(sourceModels) != refresh.get(newModels), () -> "refresh(" + refresh + ")",
				refresh);
		});
	}

	private <T> ValueTransform<SettableValue<?>> unmodifiableValue(ExpressoQIS op) throws QonfigInterpretationException {
		boolean allowUpdates = op.getAttribute("allow-updates", Boolean.class);
		if (!allowUpdates) {
			return ValueTransform.create(ModelTypes.Value,
				sourceType -> ValueTransform.of(sourceType, (v, models) -> v.disableWith(ObservableValue.of(StdMsg.UNSUPPORTED_OPERATION)),
					null, () -> "unmodifiable(" + allowUpdates + ")"));
		} else {
			return ValueTransform.create(ModelTypes.Value, sourceType -> ValueTransform.of(sourceType, (v, models) -> {
				return v.filterAccept(input -> {
					if (v.get() == input)
						return null;
					else
						return StdMsg.ILLEGAL_ELEMENT;
				});
			}, null, () -> "unmodifiable(" + allowUpdates + ")"));
		}
	}

	private <S, T> ValueTransform<?> flattenValue(ExpressoQIS op) throws QonfigInterpretationException {
		if (op.getAttribute("propagate-update-to-parent", Boolean.class, null) != null)
			op.warn("'propagate-update-to-parent' attribute not usable for value flattening");
		ExpressoQIS reverse = op.forChildren("reverse").peekFirst();
		if (reverse != null)
			reverse.warn("reverse is not usable for value flattening");
		String targetModelTypeName = op.getAttributeText("to");
		ExpressoQIS sortSession = op.forChildren("sort").peekFirst();
		ParsedSorting sort = sortSession == null ? null : sortSession.interpret(ParsedSorting.class);
		ModelType.SingleTyped<? extends ObservableCollection<?>> collectionModel;
		switch (targetModelTypeName.toLowerCase()) {
		case "value":
			if (sort != null)
				sortSession.warn("Sorting specified, but not usable");
			return flattenValueToValue(op);
		case "list":
			collectionModel = ModelTypes.Collection;
			break;
		case "sorted-list":
			collectionModel = ModelTypes.SortedCollection;
			break;
		case "set":
			collectionModel = ModelTypes.Set;
			break;
		case "sorted-set":
			collectionModel = ModelTypes.SortedSet;
			break;
		case "event":
		case "action":
		case "value-set":
		case "map":
		case "sorted-map":
		case "multi-map":
		case "sorted-multi-map":
			throw new QonfigInterpretationException("Unsupported value flatten target: '" + targetModelTypeName + "'",
				op.getAttributeValuePosition("to", 0), targetModelTypeName.length());
		default:
			throw new QonfigInterpretationException("Unrecognized model type target: '" + targetModelTypeName + "'",
				op.getAttributeValuePosition("to", 0), targetModelTypeName.length());
		}
		return flattenValueToCollection(op, collectionModel, sort, sortSession);
	}

	private <T> ValueTransform<SettableValue<?>> flattenValueToValue(ExpressoQIS op) throws QonfigInterpretationException {
		return ValueTransform.create(ModelTypes.Value, sourceType -> {
			Class<?> rawType = TypeTokens.getRawType(sourceType.getValueType());
			if (SettableValue.class.isAssignableFrom(rawType)) {
				return ValueTransform.of((TypeToken<T>) sourceType.getValueType().resolveType(SettableValue.class.getTypeParameters()[0]),
					(v, models) -> SettableValue.flatten((ObservableValue<SettableValue<T>>) (ObservableValue<?>) v), null,
					() -> "flatValue");
			} else if (ObservableValue.class.isAssignableFrom(rawType)) {
				return ValueTransform.of((TypeToken<T>) sourceType.getValueType().resolveType(ObservableValue.class.getTypeParameters()[0]),
					(v, models) -> SettableValue.flattenAsSettable((ObservableValue<ObservableValue<T>>) (ObservableValue<?>) v,
						() -> null),
					null, () -> "flatValue");
			} else
				throw new ExpressoInterpretationException("Cannot flatten type " + sourceType.getValueType() + " to a value",
					op.getElement().getPositionInFile(), 0);
		});
	}

	private <T, C extends ObservableCollection<?>> ValueTransform<C> flattenValueToCollection(ExpressoQIS op,
		ModelType.SingleTyped<C> collectionType, ParsedSorting sort, ExpressoQIS sortSession) throws QonfigInterpretationException {
		return ValueTransform.<C, C> create(collectionType, sourceType -> {
			Class<?> rawType = TypeTokens.getRawType(sourceType.getValueType());
			if (!collectionType.modelType.isAssignableFrom(rawType))
				throw new ExpressoInterpretationException("Cannot flatten type " + sourceType.getValueType() + " to a " + collectionType,
					op.getElement().getPositionInFile(), 0);
			TypeToken<T> resultType = (TypeToken<T>) sourceType.getValueType()
				.resolveType(ObservableCollection.class.getTypeParameters()[0]);
			if (collectionType == ModelTypes.Collection) {
				if (sort != null)
					sortSession.warn("Sorting specified, but not usable");
				return ValueTransform.of((ModelInstanceType<C, C>) collectionType.forType(resultType),
					(value, models) -> (C) ObservableCollection.flattenValue((ObservableValue<ObservableCollection<T>>) value), null,
					() -> "flatCollection");
			} else if (collectionType == ModelTypes.SortedCollection) {
				ModelValueSynth<SettableValue<?>, SettableValue<Comparator<T>>> evaluatedSort = sort.evaluate(resultType);
				return ValueTransform.of((ModelInstanceType<C, C>) collectionType.forType(resultType), (value, models) -> {
					Comparator<T> instantiatedSort = evaluatedSort.get(models).get();
					return (C) ObservableSortedCollection.flattenValue((ObservableValue<ObservableSortedCollection<T>>) value,
						instantiatedSort);
				}, null, () -> "flatSortedCollection");
			} else if (collectionType == ModelTypes.Set) {
				if (sort != null)
					sortSession.warn("Sorting specified, but not usable");
				return ValueTransform.of((ModelInstanceType<C, C>) collectionType.forType(resultType),
					(value, models) -> (C) ObservableSet.flattenValue((ObservableValue<ObservableSet<T>>) value), null, () -> "flatSet");
			} else if (collectionType == ModelTypes.SortedSet) {
				ModelValueSynth<SettableValue<?>, SettableValue<Comparator<T>>> evaluatedSort = sort.evaluate(resultType);
				return ValueTransform.of((ModelInstanceType<C, C>) collectionType.forType(resultType), (value, models) -> {
					Comparator<T> instantiatedSort = evaluatedSort.get(models).get();
					return (C) ObservableSortedSet.flattenValue((ObservableValue<ObservableSortedSet<T>>) value, instantiatedSort);
				}, null, () -> "flatSortedCollection");
			} else
				throw new IllegalStateException("Unrecognized collection type: " + collectionType);
		});
	}

	// Collection transform

	private <S, SC extends ObservableCollection<?>, T> CollectionTransform<? extends ObservableCollection<?>, ? extends ObservableCollection<?>> mapCollectionTo(
		ExpressoQIS op) throws QonfigInterpretationException {
		ModelType<? extends ObservableCollection<?>> sourceModelType = (ModelType<? extends ObservableCollection<?>>) op
			.get(VALUE_TYPE_KEY);
		CompiledTransformation ctx;
		try {
			ctx = mapTransformation(op);
		} catch (QonfigInterpretationException e) {
			throw new QonfigInterpretationException("Could not parse map", e.getPosition(), e.getErrorLength(), e);
		}
		if (ctx.isReversible()) {
			if (sourceModelType == ModelTypes.SortedSet) {
				return CollectionTransform.<ObservableSortedSet<?>, S, ObservableSortedSet<S>, ObservableSortedSet<?>> create(
					ModelTypes.SortedSet, ModelTypes.SortedSet, sourceType -> {
						InterpretedTransformation<S, T> ptx;
						try {
							ptx = (InterpretedTransformation<S, T>) ctx.interpret(sourceType.getValueType());
						} catch (ExpressoInterpretationException e) {
							throw new ExpressoInterpretationException("Could not parse map", e.getPosition(), e.getErrorLength(), e);
						}
						return CollectionTransform.distinctSortedFlowTransform(ptx.getTargetType(),
							(c, models) -> ((ObservableCollection.DistinctSortedDataFlow<?, ?, S>) c)
							.transformEquivalent(ptx.getTargetType(), tx -> {
								try {
									return (Transformation.ReversibleTransformation<S, T>) ptx.transform(tx, models);
								} catch (ModelInstantiationException e) {
									throw new CheckedExceptionWrapper(e);
								}
							}),
							ptx::isDifferent, ptx::toString, ptx.getComponents());
					});
			} else if (sourceModelType == ModelTypes.SortedCollection) {
				return CollectionTransform
					.<ObservableSortedCollection<?>, S, ObservableSortedCollection<S>, ObservableSortedCollection<?>> create(
						ModelTypes.SortedCollection, ModelTypes.SortedCollection, sourceType -> {
							InterpretedTransformation<S, T> ptx;
							try {
								ptx = (InterpretedTransformation<S, T>) ctx.interpret(sourceType.getValueType());
							} catch (ExpressoInterpretationException e) {
								throw new ExpressoInterpretationException("Could not parse map", e.getPosition(), e.getErrorLength(), e);
							}
							return CollectionTransform.sortedFlowTransform(ptx.getTargetType(),
								(c, models) -> ((ObservableCollection.SortedDataFlow<?, ?, S>) c).transformEquivalent(ptx.getTargetType(),
									tx -> {
										try {
											return (Transformation.ReversibleTransformation<S, T>) ptx.transform(tx, models);
										} catch (ModelInstantiationException e) {
											throw new CheckedExceptionWrapper(e);
										}
									}),
								ptx::isDifferent, ptx::toString, ptx.getComponents());
						});
			} else if (sourceModelType == ModelTypes.Set) {
				return CollectionTransform.<ObservableSet<?>, S, ObservableSet<S>, ObservableSet<?>> create(ModelTypes.Set, ModelTypes.Set,
					sourceType -> {
						InterpretedTransformation<S, T> ptx;
						try {
							ptx = (InterpretedTransformation<S, T>) ctx.interpret(sourceType.getValueType());
						} catch (ExpressoInterpretationException e) {
							throw new ExpressoInterpretationException("Could not parse map", e.getPosition(), e.getErrorLength(), e);
						}
						return CollectionTransform.distinctFlowTransform(ptx.getTargetType(),
							(c, models) -> ((DistinctDataFlow<?, ?, S>) c).transformEquivalent(ptx.getTargetType(), tx -> {
								try {
									return (Transformation.ReversibleTransformation<S, T>) ptx.transform(tx, models);
								} catch (ModelInstantiationException e) {
									throw new CheckedExceptionWrapper(e);
								}
							}), ptx::isDifferent, ptx::toString, ptx.getComponents());
					});
			} else {
				return CollectionTransform.<ObservableCollection<?>, S, ObservableCollection<S>, ObservableCollection<?>> create(
					ModelTypes.Collection, ModelTypes.Collection, sourceType -> {
						InterpretedTransformation<S, T> ptx;
						try {
							ptx = (InterpretedTransformation<S, T>) ctx.interpret(sourceType.getValueType());
						} catch (ExpressoInterpretationException e) {
							throw new ExpressoInterpretationException("Could not parse map", e.getPosition(), e.getErrorLength(), e);
						}
						return CollectionTransform.flowTransform(ptx.getTargetType(),
							(c, models) -> c.transform(ptx.getTargetType(), tx -> {
								try {
									return (Transformation.ReversibleTransformation<S, T>) ptx.transform(tx, models);
								} catch (ModelInstantiationException e) {
									throw new CheckedExceptionWrapper(e);
								}
							}), ptx::isDifferent, ptx::toString, ptx.getComponents());
					});
			}
		} else {
			return CollectionTransform.<ObservableCollection<?>, S, ObservableCollection<S>, ObservableCollection<?>> create(
				ModelTypes.Collection, ModelTypes.Collection, sourceType -> {
					InterpretedTransformation<S, T> ptx;
					try {
						ptx = (InterpretedTransformation<S, T>) ctx.interpret(sourceType.getValueType());
					} catch (ExpressoInterpretationException e) {
						throw new ExpressoInterpretationException("Could not parse map", e.getPosition(), e.getErrorLength(), e);
					}
					return CollectionTransform.flowTransform(ptx.getTargetType(), (c, models) -> c.transform(ptx.getTargetType(), tx -> {
						try {
							return ptx.transform(tx, models);
						} catch (ModelInstantiationException e) {
							throw new CheckedExceptionWrapper(e);
						}
					}), ptx::isDifferent, ptx::toString, ptx.getComponents());
				});
		}
	}

	private <T, C extends ObservableCollection<?>> CollectionTransform<C, C> filterCollection(ExpressoQIS op)
		throws QonfigInterpretationException {
		ModelType.SingleTyped<C> sourceModelType = (ModelType.SingleTyped<C>) op.get(VALUE_TYPE_KEY);
		String sourceAs = op.getAttributeText("source-as");
		CompiledExpression testX = op.getAttributeExpression("test");
		return CollectionTransform.<C, T, C, C> create(sourceModelType, sourceModelType, sourceType -> {
			ObservableModelSet.Builder wrappedBuilder = op.getExpressoEnv().getModels().wrap("filterModel");
			RuntimeValuePlaceholder<SettableValue<?>, SettableValue<T>> sourcePlaceholder = wrappedBuilder.withRuntimeValue(sourceAs,
				ModelTypes.Value.forType(sourceType.getValueType()));
			ObservableModelSet.Built wrapped = wrappedBuilder.build();

			ModelValueSynth<SettableValue<?>, SettableValue<String>> test = parseFilter(testX, op.getExpressoEnv().with(wrapped, null),
				false);
			InterpretedModelSet wrappedInterpreted;
			try {
				wrappedInterpreted = wrapped.interpret();
			} catch (ExpressoInterpretationException e) {
				throw new ExpressoInterpretationException(e.getMessage(), e.getPosition(), e.getErrorLength(), e);
			}
			return (ObservableStructureTransform<C, C, C, C>) CollectionTransform.flowTransform(sourceType, (source, models) -> {
				SettableValue<T> sourceV = SettableValue.build(sourceType.getValueType()).build();
				ModelSetInstance wrappedMSI = wrappedInterpreted.createInstance(models.getUntil()).withAll(models)//
					.with(sourcePlaceholder, sourceV)//
					.build();
				SettableValue<String> message = test.get(wrappedMSI);
				return source.filter(v -> {
					sourceV.set((T) v, null);
					return message.get();
				});
			}, null, () -> "filter(" + test + ")", test);
		});
	}

	private <S, T, C extends ObservableCollection<?>> CollectionTransform<C, C> filterCollectionByType(ExpressoQIS op)
		throws QonfigInterpretationException {
		ModelType.SingleTyped<C> sourceModelType = (ModelType.SingleTyped<C>) op.get(VALUE_TYPE_KEY);
		CompiledExpression typeX = op.getAttributeExpression("type");
		return CollectionTransform.<C, S, C, C> create(sourceModelType, sourceModelType, sourceType -> {
			ModelValueSynth<SettableValue<?>, SettableValue<Class<T>>> typeC;
			TypeToken<T> targetType;
			try {
				typeC = typeX.evaluate(ModelTypes.Value.forType(TypeTokens.get().keyFor(Class.class).wildCard()), op.getExpressoEnv());
				targetType = (TypeToken<T>) typeC.getType().getType(0).resolveType(Class.class.getTypeParameters()[0]);
			} catch (ExpressoInterpretationException e) {
				throw new ExpressoInterpretationException("Could not parse type", e.getPosition(), e.getErrorLength(), e);
			}
			return (ObservableStructureTransform<C, C, C, C>) CollectionTransform
				.flowTransform((ModelInstanceType<C, C>) sourceType.getModelType().forType(targetType), (source, models) -> {
					Class<T> type = typeC.get(models).get();
					return source.filter(type);
				}, null, () -> "filter(" + targetType + ")", typeC);
		});
	}

	private <T, C extends ObservableCollection<?>> CollectionTransform<C, C> reverseCollection(ExpressoQIS op)
		throws QonfigInterpretationException {
		ModelType.SingleTyped<C> sourceModelType = (ModelType.SingleTyped<C>) op.get(VALUE_TYPE_KEY);
		return CollectionTransform.<C, T, C, C> create(sourceModelType, sourceModelType, sourceType -> {
			return new FlowCollectionTransform<T, T, C, C, C, C>() {
				@Override
				public ModelInstanceType<C, C> getTargetType() {
					return sourceType;
				}

				@Override
				public CollectionDataFlow<?, ?, T> transformFlow(CollectionDataFlow<?, ?, T> source, ModelSetInstance models) {
					return source.reverse();
				}

				@Override
				public CollectionDataFlow<?, ?, T> transformToFlow(C source, ModelSetInstance models) {
					return (CollectionDataFlow<?, ?, T>) source.flow().reverse();
				}

				@Override
				public C transform(C source, ModelSetInstance models) {
					return (C) source.reverse();
				}

				@Override
				public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) {
					return false;
				}

				@Override
				public BetterList<ModelValueSynth<?, ?>> getCores() {
					return BetterList.empty();
				}

				@Override
				public String toString() {
					return "reverse";
				}
			};
		});
	}

	private <T, C extends ObservableCollection<?>> CollectionTransform<C, C> refreshCollection(ExpressoQIS op)
		throws QonfigInterpretationException {
		ModelType.SingleTyped<C> sourceModelType = (ModelType.SingleTyped<C>) op.get(VALUE_TYPE_KEY);
		CompiledExpression refreshX = op.getAttributeExpression("on");
		return CollectionTransform.<C, T, C, C> create(sourceModelType, sourceModelType, sourceType -> {
			ModelValueSynth<Observable<?>, Observable<?>> refreshV;
			try {
				refreshV = refreshX.evaluate(ModelTypes.Event.any(), op.getExpressoEnv());
			} catch (ExpressoInterpretationException e) {
				throw new ExpressoInterpretationException("Could not parse refresh", e.getPosition(), e.getErrorLength(), e);
			}
			return (ObservableStructureTransform<C, C, C, ?>) CollectionTransform.flowTransform((ModelInstanceType<C, C>) sourceType,
				(f, models) -> {
					Observable<?> refresh = refreshV.get(models);
					return f.refresh(refresh);
				}, (sourceModels, newModels) -> refreshV.get(sourceModels) != refreshV.get(newModels), () -> "refresh(" + refreshV + ")",
					refreshV);
		});
	}

	private <T, C extends ObservableCollection<?>> CollectionTransform<C, C> refreshCollectionEach(ExpressoQIS op)
		throws QonfigInterpretationException {
		ModelType.SingleTyped<C> sourceModelType = (ModelType.SingleTyped<C>) op.get(VALUE_TYPE_KEY);
		String sourceAs = op.getAttributeText("source-as");
		CompiledExpression refreshX = op.getAttributeExpression("on");
		return CollectionTransform.<C, T, C, C> create(sourceModelType, sourceModelType, sourceType -> {
			ObservableModelSet.Builder refreshModelBuilder = op.getExpressoEnv().getModels().wrap("refreshEachModel");
			RuntimeValuePlaceholder<SettableValue<?>, SettableValue<T>> sourcePlaceholder = refreshModelBuilder.withRuntimeValue(sourceAs,
				ModelTypes.Value.forType(sourceType.getValueType()));
			ObservableModelSet.Built refreshModel = refreshModelBuilder.build();
			ModelValueSynth<SettableValue<?>, SettableValue<Observable<?>>> refreshV;
			try {
				refreshV = refreshX.evaluate(ModelTypes.Value.forType(TypeTokens.get().keyFor(Observable.class).wildCard()),
					op.getExpressoEnv().with(refreshModel, null));
			} catch (ExpressoInterpretationException e) {
				throw new ExpressoInterpretationException("Could not parse refresh", e.getPosition(), e.getErrorLength(), e);
			}
			InterpretedModelSet refreshModelInterpreted;
			try {
				refreshModelInterpreted = refreshModel.interpret();
			} catch (ExpressoInterpretationException e) {
				throw new ExpressoInterpretationException(e.getMessage(), e.getPosition(), e.getErrorLength(), e);
			}
			return (ObservableStructureTransform<C, C, C, ?>) CollectionTransform.flowTransform(sourceType, (f, models) -> {
				SettableValue<T> sourceValue = SettableValue.build(sourceType.getValueType()).build();
				ModelSetInstance refreshModelInstance = refreshModelInterpreted.createInstance(models.getUntil()).withAll(models)//
					.with(sourcePlaceholder, sourceValue)//
					.build();
				SettableValue<Observable<?>> refresh = refreshV.get(refreshModelInstance);
				return f.refreshEach(v -> {
					sourceValue.set((T) v, null);
					return refresh.get();
				});
			}, null, () -> "refresh(" + refreshV + ")", refreshV);
		});
	}

	private <T, C extends ObservableCollection<C>> CollectionTransform<C, ? extends ObservableSet<?>> distinctCollection(ExpressoQIS op)
		throws QonfigInterpretationException {
		ModelType.SingleTyped<C> sourceModelType = (ModelType.SingleTyped<C>) op.get(VALUE_TYPE_KEY);
		boolean useFirst = op.getAttribute("use-first", Boolean.class);
		boolean preserveSourceOrder = op.getAttribute("preserve-source-order", Boolean.class);
		ExpressoQIS sortSession = op.forChildren("sort").peekFirst();
		ParsedSorting sort = sortSession == null ? null : sortSession.interpret(ParsedSorting.class);
		if (sort != null || sourceModelType == ModelTypes.SortedCollection) {
			if (preserveSourceOrder)
				op.warn("'preserve-source-order' is not applicable for sorted transformations");
			return CollectionTransform.<C, T, C, ObservableSortedSet<?>> create(sourceModelType, ModelTypes.SortedSet, sourceType -> {
				ModelValueSynth<SettableValue<?>, SettableValue<Comparator<T>>> compare = sort.evaluate(sourceType.getValueType());
				return (ObservableStructureTransform<C, C, ObservableSortedSet<?>, ?>) CollectionTransform
					.<T, T> distinctSortedFlowTransform(sourceType.getValueType(), (f, models) -> {
						Comparator<T> comparator = compare.get(models).get();
						return f.distinctSorted(comparator, useFirst);
					}, null, () -> "distinct", compare);
			});
		} else if (sourceModelType == ModelTypes.SortedSet || sourceModelType == ModelTypes.Set)
			return (CollectionTransform<C, ObservableSet<?>>) CollectionTransform.trivial(sourceModelType);
		else {
			return CollectionTransform.<C, T, C, ObservableSet<?>> create(sourceModelType, ModelTypes.Set, sourceType -> {
				return (ObservableStructureTransform<C, C, ObservableSet<?>, ?>) CollectionTransform
					.<T, T> distinctFlowTransform(sourceType.getValueType(), (f, models) -> {
						return f.distinct(opts -> opts.useFirst(useFirst).preserveSourceOrder(preserveSourceOrder));
					}, null, () -> "distinct");
			});
		}
	}

	private <T, C extends ObservableCollection<C>> CollectionTransform<C, ? extends ObservableSortedCollection<?>> sortedCollection(
		ExpressoQIS op) throws QonfigInterpretationException {
		ModelType.SingleTyped<C> sourceModelType = (ModelType.SingleTyped<C>) op.get(VALUE_TYPE_KEY);
		ParsedSorting sort = op.interpret(ParsedSorting.class);
		return CollectionTransform.<C, T, C, ObservableSortedCollection<?>> create(sourceModelType, ModelTypes.SortedCollection,
			sourceType -> {
				ModelValueSynth<SettableValue<?>, SettableValue<Comparator<T>>> compare = sort.evaluate(sourceType.getValueType());
				return (ObservableStructureTransform<C, C, ObservableSortedCollection<?>, ?>) CollectionTransform
					.<T, T> sortedFlowTransform(sourceType.getValueType(), (f, models) -> {
						Comparator<T> comparator = compare.get(models).get();
						return f.sorted(comparator);
					}, null, () -> "sorted", compare);
			});
	}

	private <T, C extends ObservableCollection<?>> CollectionTransform<C, ? extends ObservableCollection<?>> withCollectionEquivalence(
		ExpressoQIS op) throws QonfigInterpretationException {
		throw new QonfigInterpretationException("Not yet implemented", op.getElement().getPositionInFile(), 0); // TODO
	}

	private <T, C extends ObservableCollection<?>> CollectionTransform<C, C> unmodifiableCollection(ExpressoQIS op)
		throws QonfigInterpretationException {
		ModelType.SingleTyped<C> sourceModelType = (ModelType.SingleTyped<C>) op.get(VALUE_TYPE_KEY);
		boolean allowUpdates = op.getAttribute("allow-updates", boolean.class);
		return CollectionTransform.<C, T, C, C> create(sourceModelType, sourceModelType, sourceType -> {
			return (ObservableStructureTransform<C, C, C, ?>) CollectionTransform.<C, C, C, C> flowTransform(sourceType,
				(f, models) -> f.unmodifiable(allowUpdates), null, () -> "unmodifiable(" + allowUpdates + ")");
		});
	}

	private <T, C extends ObservableCollection<?>> CollectionTransform<C, C> filterCollectionModification(ExpressoQIS op)
		throws QonfigInterpretationException {
		throw new QonfigInterpretationException("Not yet implemented", op.getElement().getPositionInFile(), 0); // TODO
	}

	private <S, T, C extends ObservableCollection<?>> CollectionTransform<C, C> mapEquivalentCollectionTo(ExpressoQIS op)
		throws QonfigInterpretationException {
		throw new QonfigInterpretationException("Not yet implemented", op.getElement().getPositionInFile(), 0); // TODO
	}

	private <T, C1 extends ObservableCollection<?>, C2 extends ObservableCollection<?>> CollectionTransform<C1, C2> flattenCollection(
		ExpressoQIS op)
			throws QonfigInterpretationException {
		ModelType.SingleTyped<C1> sourceModelType = (ModelType.SingleTyped<C1>) op.get(VALUE_TYPE_KEY);
		String targetModelTypeName = op.getAttributeText("to");
		ExpressoQIS sortSession = op.forChildren("sort").peekFirst();
		ParsedSorting sort = sortSession == null ? null : sortSession.interpret(ParsedSorting.class);
		@SuppressWarnings("unused")
		ExpressoQIS reverse = op.forChildren("reverse").peekFirst();
		@SuppressWarnings("unused")
		boolean propagateToParent = op.getAttribute("propagate-to-parent", boolean.class, false);
		ModelType.SingleTyped<C2> collectionModel;
		switch (targetModelTypeName.toLowerCase()) {
		case "list":
			if (sort != null)
				collectionModel = (ModelType.SingleTyped<C2>) ModelTypes.SortedCollection;
			else
				collectionModel = (ModelType.SingleTyped<C2>) ModelTypes.Collection;
			break;
		case "sorted-list":
			collectionModel = (ModelType.SingleTyped<C2>) ModelTypes.SortedCollection;
			break;
		case "set":
			if (sort != null)
				collectionModel = (ModelType.SingleTyped<C2>) ModelTypes.SortedSet;
			else
				collectionModel = (ModelType.SingleTyped<C2>) ModelTypes.Set;
			break;
		case "sorted-set":
			collectionModel = (ModelType.SingleTyped<C2>) ModelTypes.SortedSet;
			break;
		case "event":
		case "action":
		case "value":
		case "value-set":
		case "map":
		case "sorted-map":
		case "multi-map":
		case "sorted-multi-map":
			throw new QonfigInterpretationException("Unsupported collection flatten target: '" + targetModelTypeName + "'",
				op.getAttributeValuePosition("to", 0), targetModelTypeName.length());
		default:
			throw new QonfigInterpretationException("Unrecognized model type target: '" + targetModelTypeName + "'",
				op.getAttributeValuePosition("to", 0), targetModelTypeName.length());
		}
		return CollectionTransform.<C1, T, C1, C2> create(sourceModelType, collectionModel, sourceType -> {
			Class<?> raw = TypeTokens.getRawType(sourceType.getValueType());
			TypeToken<T> resultType;
			ExBiFunction<CollectionDataFlow<?, ?, ?>, ModelSetInstance, CollectionDataFlow<?, ?, T>, ModelInstantiationException> flatten;
			String txName;
			if (ObservableValue.class.isAssignableFrom(raw)) {
				resultType = (TypeToken<T>) sourceType.getValueType().resolveType(ObservableValue.class.getTypeParameters()[0]);
				flatten = (flow, models) -> flow.flattenValues(resultType, v -> (ObservableValue<? extends T>) v);
				txName = "flatValues";
			} else if (ObservableCollection.class.isAssignableFrom(raw)) {
				System.err.println("WARNING: Collection flatten is not fully implemented.  Many options are unsupported.");
				// TODO Use map options, reverse
				resultType = (TypeToken<T>) sourceType.getValueType().resolveType(ObservableCollection.class.getTypeParameters()[0]);
				flatten = (flow, models) -> flow.flatMap(resultType, v -> ((ObservableCollection<? extends T>) v).flow());
				txName = "flatCollections";
			} else if (CollectionDataFlow.class.isAssignableFrom(raw)) {
				System.err.println("WARNING: Collection flatten is not fully implemented.  Many options are unsupported.");
				// TODO Use map options, reverse
				resultType = (TypeToken<T>) sourceType.getValueType().resolveType(CollectionDataFlow.class.getTypeParameters()[2]);
				flatten = (flow, models) -> flow.flatMap(resultType, v -> (CollectionDataFlow<?, ?, ? extends T>) v);
				txName = "flatFlows";
			} else
				throw new ExpressoInterpretationException("Cannot flatten a collection of type " + sourceType.getValueType(),
					op.getElement().getPositionInFile(), 0);
			ModelValueSynth<SettableValue<?>, SettableValue<Comparator<T>>> sortSynth;
			List<ModelValueSynth<?, ?>> components = new ArrayList<>();
			if (sort != null) {
				sortSynth = sort.evaluate(resultType);
				components.add(sortSynth);
			} else if (collectionModel == ModelTypes.SortedSet || collectionModel == ModelTypes.SortedCollection)
				sortSynth = getDefaultSorting(op.getElement()).evaluate(resultType);
			else
				sortSynth = null;
			boolean distinct = collectionModel == ModelTypes.SortedSet || collectionModel == ModelTypes.Set;
			return (ObservableStructureTransform<? super C1, ? super C1, ? extends C2, ?>) CollectionTransform
				.flowTransform(collectionModel.forType(resultType), (source, models) -> {
					Comparator<T> compare = sortSynth == null ? null : sortSynth.get(models).get();
					CollectionDataFlow<?, ?, T> mapped = flatten.apply(source, models);
					if (distinct) {
						if (sortSynth != null)
							mapped = mapped.distinctSorted(compare, false);
						else
							mapped = mapped.distinct();
					} else if (sortSynth != null)
						mapped = mapped.sorted(compare);
					return mapped;
				}, null, () -> txName, components.toArray(new ModelValueSynth[0]));
		});
	}

	private CollectionTransform<?, ?> crossCollection(ExpressoQIS op) throws QonfigInterpretationException {
		throw new QonfigInterpretationException("Not yet implemented", op.getElement().getPositionInFile(), 0); // TODO
	}

	private <T, C extends ObservableCollection<?>> CollectionTransform<C, C> whereCollectionContained(ExpressoQIS op)
		throws QonfigInterpretationException {
		ModelType.SingleTyped<C> sourceModelType = (ModelType.SingleTyped<C>) op.get(VALUE_TYPE_KEY);
		CompiledExpression filterX = op.getAttributeExpression("filter");
		boolean inclusive = op.getAttribute("inclusive", boolean.class);
		return CollectionTransform.create(sourceModelType, sourceModelType, sourceType -> {
			ModelValueSynth<ObservableCollection<?>, ObservableCollection<?>> filter;
			try {
				filter = filterX.evaluate(ModelTypes.Collection.any(), op.getExpressoEnv());
			} catch (ExpressoInterpretationException e) {
				throw new ExpressoInterpretationException("Could not parse filter", e.getPosition(), e.getErrorLength(), e);
			}
			return (ObservableStructureTransform<? super C, ? super C, ? extends C, ?>) CollectionTransform.flowTransform(sourceType,
				(flow, models) -> flow.whereContained(//
					filter.get(models).flow(), inclusive), //
				(sourceModels, newModels) -> filter.get(sourceModels) != filter.get(newModels),
				() -> "whereContained(" + filter + ", " + inclusive + ")");
		});
	}

	private <K, V> CollectionTransform<ObservableCollection<?>, ObservableMultiMap<?, ?>> groupCollectionBy(ExpressoQIS op)
		throws QonfigInterpretationException {
		throw new QonfigInterpretationException("Not yet implemented", op.getElement().getPositionInFile(), 0); // TODO
	}

	private <T, C extends ObservableCollection<C>> CollectionTransform<C, C> collectCollection(ExpressoQIS op)
		throws QonfigInterpretationException {
		ModelType.SingleTyped<C> sourceModelType = (ModelType.SingleTyped<C>) op.get(VALUE_TYPE_KEY);
		Boolean active = op.getAttribute("active", Boolean.class);
		return CollectionTransform.create(sourceModelType, sourceModelType, sourceType -> {
			return CollectionTransform.flowTransform(sourceType, (flow, models) -> {
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
		});
	}

	/** A compiled structure capable of generating an {@link InterpretedTransformation} */
	public interface CompiledTransformation {
		/**
		 * @param <S> The type of the source values to transform
		 * @param sourceType The type of the source values to transform
		 * @return The interpreted transformer
		 * @throws ExpressoInterpretationException If the transformer could not be produced
		 */
		<S> InterpretedTransformation<S, ?> interpret(TypeToken<S> sourceType) throws ExpressoInterpretationException;

		/** @return Whether this transformation is reversible */
		boolean isReversible();
	}

	/**
	 * Interface to provide via {@link org.qommons.config.QonfigInterpreterCore.Builder#createWith(String, Class, QonfigValueCreator)} to
	 * support a new transformation type
	 *
	 * @param <S> The type of the source value
	 * @param <T> The type of the transformed value
	 */
	public interface InterpretedTransformation<S, T> {
		/** @return The type of value that this transformation produces */
		TypeToken<T> getTargetType();

		/**
		 * @param precursor The observable transformation precursor to configure
		 * @param modelSet The model instances to use
		 * @return The observable transformation
		 * @throws ModelInstantiationException If the transformation could not be produced
		 */
		Transformation<S, T> transform(TransformationPrecursor<S, T, ?> precursor, ModelSetInstance modelSet)
			throws ModelInstantiationException;

		/**
		 * Helps support the {@link ModelValueSynth#forModelCopy(Object, ModelSetInstance, ModelSetInstance)} method
		 *
		 * @param sourceModels The source model instance
		 * @param newModels The new model instance
		 * @return Whether observables produced by this transform would be different between the two models
		 * @throws ModelInstantiationException If the inspection fails
		 */
		boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException;

		/** @return Any component model values that are used by this transformation */
		ModelValueSynth<?, ?>[] getComponents();
	}

	/** A compiled structure capable of generating an {@link InterpretedMapReverse} */
	public interface CompiledMapReverse {
		/**
		 * @param <S> The type of the source values to produce
		 * @param <T> The type of the mapped values to reverse-transform
		 * @param sourceName The name of the source model value
		 * @param sourceType The source type of the transformation
		 * @param targetType The target type of the transformation
		 * @param combinedTypes The names and types of all combined values incorporated into the transform
		 * @return The interpreted map reverse
		 * @throws ExpressoInterpretationException If the reverse transformation could not be produced
		 */
		<S, T> InterpretedMapReverse<S, T> interpret(String sourceName, TypeToken<S> sourceType, TypeToken<T> targetType,
			Map<String, TypeToken<?>> combinedTypes) throws ExpressoInterpretationException;
	}

	/**
	 * Interface to provide via {@link org.qommons.config.QonfigInterpreterCore.Builder#createWith(String, Class, QonfigValueCreator)} to
	 * support a new map-reverse type
	 *
	 * @param <S> The type of the source value
	 * @param <T> The type of the transformed value
	 */
	public interface InterpretedMapReverse<S, T> {
		/**
		 * @param transformation The transformation to reverse
		 * @param modelSet The model instance
		 * @return The transformation reverse
		 * @throws ModelInstantiationException If the reverse operation could not be instantiated
		 */
		Transformation.TransformReverse<S, T> reverse(Transformation<S, T> transformation, ModelSetInstance modelSet)
			throws ModelInstantiationException;

		/**
		 * Helps support the {@link ModelValueSynth#forModelCopy(Object, ModelSetInstance, ModelSetInstance)} method
		 *
		 * @param sourceModels The source model instance
		 * @param newModels The new model instance
		 * @return Whether observables produced by this reverse would be different between the two models
		 * @throws ModelInstantiationException If the inspection fails
		 */
		boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException;

		/** @return Any component model values that are used by this reverse */
		List<? extends ModelValueSynth<?, ?>> getComponents();
	}

	private <T> CompiledTransformation mapTransformation(ExpressoQIS op) throws QonfigInterpretationException {
		ExpressoQIS mapQIS = op.forChildren("map").getFirst();
		CompiledExpression mapX = mapQIS.getValueExpression();

		QonfigValue targetTypeQV = op.getElement().getAttributes().get(op.getAttributeDef(null, null, "type"));
		VariableType targetTypeV = targetTypeQV == null ? null : VariableType.parseType(targetTypeQV.text,
			op.getExpressoEnv().getClassView(), targetTypeQV.fileLocation, targetTypeQV.position);

		Map<String, CompiledExpression> combinedXs = new LinkedHashMap<>();
		for (ExpressoQIS combinedQIS : op.forChildren("combined-value")) {
			String name = combinedQIS.getAttributeText("name");
			try {
				op.getExpressoEnv().getModels().getNameChecker().checkName(name);
			} catch (IllegalArgumentException e) {
				throw new QonfigInterpretationException("Illegal name for combined-value: '" + name + "'",
					combinedQIS.getAttributeValuePosition("name", 0), name.length(), e);
			}
			combinedXs.put(name, combinedQIS.getValueExpression());
		}
		String sourceName = op.getAttributeText("source-as");
		boolean cached = op.getAttribute("cache", boolean.class);
		boolean reEval = op.getAttribute("re-eval-on-update", boolean.class);
		boolean fireIfUnchanged = op.getAttribute("fire-if-unchanged", boolean.class);
		boolean nullToNull = op.getAttribute("null-to-null", boolean.class);
		boolean manyToOne = op.getAttribute("many-to-one", boolean.class);
		boolean oneToMany = op.getAttribute("one-to-many", boolean.class);

		CompiledMapReverse compiledReverse = op.interpretChildren("reverse", CompiledMapReverse.class).peekFirst();

		return new CompiledTransformation() {
			@Override
			public boolean isReversible() {
				return compiledReverse != null;
			}

			@Override
			public <S> InterpretedTransformation<S, ?> interpret(TypeToken<S> sourceType) throws ExpressoInterpretationException {
				ObservableModelSet.Builder mapModelsBuilder = mapQIS.getExpressoEnv().getModels().wrap("mapModel");
				Map<String, InterpretedValueSynth<SettableValue<?>, SettableValue<Object>>> combinedSynths = new LinkedHashMap<>(
					combinedXs.size() * 2);
				Map<String, ObservableModelSet.RuntimeValuePlaceholder<SettableValue<?>, SettableValue<Object>>> combinedPlaceholders = new LinkedHashMap<>();
				for (Map.Entry<String, CompiledExpression> combinedX : combinedXs.entrySet()) {
					ModelValueSynth<SettableValue<?>, SettableValue<Object>> combineV = combinedX.getValue().evaluate(
						(ModelInstanceType<SettableValue<?>, SettableValue<Object>>) (ModelInstanceType<?, ?>) ModelTypes.Value.any());
					combinedSynths.put(combinedX.getKey(), combineV.interpret());
					combinedPlaceholders.put(combinedX.getKey(), mapModelsBuilder.withRuntimeValue(combinedX.getKey(), combineV.getType()));
				}
				ObservableModelSet.RuntimeValuePlaceholder<SettableValue<?>, SettableValue<S>> sourcePlaceholder = mapModelsBuilder
					.withRuntimeValue(sourceName, ModelTypes.Value.forType(sourceType));
				ObservableModelSet.Built mapModels = mapModelsBuilder.build();
				mapQIS.setModels(mapModels, null);
				TypeToken<T> targetType;
				ModelValueSynth<SettableValue<?>, SettableValue<T>> mapped;
				if (targetTypeV != null) {
					targetType = (TypeToken<T>) targetTypeV.getType(mapQIS.getExpressoEnv().getModels());
					mapped = mapX.evaluate(ModelTypes.Value.forType(targetType));
				} else {
					mapped = mapX.evaluate(
						(ModelInstanceType<SettableValue<?>, SettableValue<T>>) (ModelInstanceType<?, ?>) ModelTypes.Value.any(),
						mapQIS.getExpressoEnv());
					targetType = (TypeToken<T>) mapped.getType().getType(0);
				}

				InterpretedMapReverse<S, T> interpretedReverse;
				if (compiledReverse != null) {
					Map<String, TypeToken<?>> combinedTypes = new LinkedHashMap<>();
					for (Map.Entry<String, InterpretedValueSynth<SettableValue<?>, SettableValue<Object>>> combinedV : combinedSynths
						.entrySet()) {
						combinedTypes.put(combinedV.getKey(), combinedV.getValue().getType().getType(0));
					}
					interpretedReverse = compiledReverse.interpret(sourceName, sourceType, targetType, combinedTypes);
				} else
					interpretedReverse = null;

				InterpretedModelSet mapModelsInterpreted = mapModels.interpret();
				return new InterpretedTransformation<S, T>() {
					@Override
					public TypeToken<T> getTargetType() {
						return targetType;
					}

					@Override
					public Transformation<S, T> transform(Transformation.TransformationPrecursor<S, T, ?> precursor,
						ModelSetInstance modelSet) throws ModelInstantiationException {
						SettableValue<S> sourceV = SettableValue.build(sourceType).build();
						Map<String, SettableValue<Object>> combinedSourceVs = new LinkedHashMap<>();
						Map<String, SettableValue<Object>> combinedTransformVs = new LinkedHashMap<>();
						Transformation.TransformationBuilder<S, T, ?> builder = precursor//
							.cache(cached).reEvalOnUpdate(reEval).fireIfUnchanged(fireIfUnchanged).nullToNull(nullToNull)
							.manyToOne(manyToOne).oneToMany(oneToMany);
						ObservableModelSet.ModelSetInstanceBuilder mapMSIBuilder = mapModelsInterpreted.createInstance(modelSet.getUntil())
							.withAll(modelSet)//
							.with(sourcePlaceholder, sourceV.disableWith(ObservableValue.of(StdMsg.UNSUPPORTED_OPERATION)));
						for (Map.Entry<String, InterpretedValueSynth<SettableValue<?>, SettableValue<Object>>> cv : combinedSynths
							.entrySet()) {
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
						if (interpretedReverse != null)
							transformation = ((MaybeReversibleTransformation<S, T>) transformation).withReverse(//
								interpretedReverse.reverse(transformation, modelSet));
						return transformation;
					}

					@Override
					public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels)
						throws ModelInstantiationException {
						for (ModelValueSynth<SettableValue<?>, SettableValue<Object>> combinedSourceV : combinedSynths.values()) {
							if (combinedSourceV.get(sourceModels) != combinedSourceV.get(newModels))
								return true;
						}
						if (interpretedReverse != null && interpretedReverse.isDifferent(sourceModels, newModels))
							return true;
						return false;
					}

					@Override
					public ModelValueSynth<?, ?>[] getComponents() {
						List<ModelValueSynth<?, ?>> components = new ArrayList<>();
						components.add(mapped);
						components.addAll(combinedSynths.values());
						if (interpretedReverse != null)
							components.addAll(interpretedReverse.getComponents());
						return components.toArray(new ModelValueSynth[components.size()]);
					}

					@Override
					public String toString() {
						StringBuilder str = new StringBuilder("map(").append(mapX);
						if (!combinedSynths.isEmpty())
							str.append("with ").append(combinedSynths.values());
						return str.append(")").toString();
					}
				};
			}
		};
	}


	CompiledMapReverse createSourceReplace(ExpressoQIS reverse) throws QonfigInterpretationException {
		CompiledExpression reverseX = reverse.getValueExpression();
		CompiledExpression enabled = reverse.getAttributeExpression("enabled");
		CompiledExpression accept = reverse.getAttributeExpression("accept");
		CompiledExpression add = reverse.getAttributeExpression("add");
		CompiledExpression addAccept = reverse.getAttributeExpression("add-accept");
		String targetName = reverse.getAttributeText("target-as");
		try {
			reverse.getExpressoEnv().getModels().getNameChecker().checkName(targetName);
		} catch (IllegalArgumentException e) {
			throw new QonfigInterpretationException("Illegal name for target-as: '" + targetName + "'",
				reverse.getAttributeValuePosition("target-as", 0), targetName.length(), e);
		}

		boolean inexact = reverse.getAttribute("inexact", boolean.class);
		return new CompiledMapReverse() {
			@Override
			public <S, T> InterpretedMapReverse<S, T> interpret(String sourceName, TypeToken<S> sourceType, TypeToken<T> targetType,
				Map<String, TypeToken<?>> combinedTypes) throws ExpressoInterpretationException {
				boolean stateful = refersToSource(reverseX.getExpression(), sourceName)//
					|| (enabled != null && refersToSource(enabled.getExpression(), sourceName))
					|| (accept != null && refersToSource(accept.getExpression(), sourceName));

				ObservableModelSet.Builder reverseModelBuilder = reverse.getExpressoEnv().getModels().wrap("mapRepReverseModel");
				RuntimeValuePlaceholder<SettableValue<?>, SettableValue<S>> sourcePlaceholder = reverseModelBuilder
					.withRuntimeValue(sourceName, ModelTypes.Value.forType(sourceType));
				RuntimeValuePlaceholder<SettableValue<?>, SettableValue<T>> targetPlaceholder = reverseModelBuilder
					.withRuntimeValue(targetName, ModelTypes.Value.forType(targetType));
				Map<String, ObservableModelSet.RuntimeValuePlaceholder<SettableValue<?>, ? extends SettableValue<?>>> combinedValues = new LinkedHashMap<>();
				for (Map.Entry<String, TypeToken<?>> combined : combinedTypes.entrySet())
					combinedValues.put(combined.getKey(),
						reverseModelBuilder.withRuntimeValue(combined.getKey(), ModelTypes.Value.forType(combined.getValue())));

				InterpretedModelSet reverseModels = reverseModelBuilder.build().interpret();
				reverse.setModels(reverseModels, null);

				ModelValueSynth<SettableValue<?>, SettableValue<S>> reversedV;
				try {
					reversedV = reverseX.evaluate(ModelTypes.Value.forType(sourceType));
				} catch (ExpressoInterpretationException e) {
					throw new ExpressoInterpretationException("Could not interpret reversed value", e.getPosition(), e.getErrorLength(), e);
				}
				ModelValueSynth<SettableValue<?>, SettableValue<String>> enabledV;
				try {
					enabledV = enabled == null ? null : enabled.evaluate(ModelTypes.Value.forType(String.class));
				} catch (ExpressoInterpretationException e) {
					throw new ExpressoInterpretationException("Could not interpret reversed enablement", e.getPosition(),
						e.getErrorLength(), e);
				}
				ModelValueSynth<SettableValue<?>, SettableValue<String>> acceptV;
				try {
					acceptV = accept == null ? null : accept.evaluate(ModelTypes.Value.forType(String.class));
				} catch (ExpressoInterpretationException e) {
					throw new ExpressoInterpretationException("Could not interpret reversed acceptance", e.getPosition(),
						e.getErrorLength(), e);
				}
				ModelValueSynth<SettableValue<?>, SettableValue<S>> addV;
				try {
					addV = add == null ? null : add.evaluate(ModelTypes.Value.forType(sourceType));
				} catch (ExpressoInterpretationException e) {
					throw new ExpressoInterpretationException("Could not interpret reversed add", e.getPosition(), e.getErrorLength(), e);
				}
				ModelValueSynth<SettableValue<?>, SettableValue<String>> addAcceptV;
				try {
					addAcceptV = addAccept == null ? null : addAccept.evaluate(ModelTypes.Value.forType(String.class));
				} catch (ExpressoInterpretationException e) {
					throw new ExpressoInterpretationException("Could not interpret reversed add acceptance", e.getPosition(),
						e.getErrorLength(), e);
				}
				return new InterpretedMapReverse<S, T>() {
					@Override
					public TransformReverse<S, T> reverse(Transformation<S, T> transformation, ModelSetInstance modelSet)
						throws ModelInstantiationException {
						SettableValue<S> sourceV = SettableValue.build(sourceType).build();
						SettableValue<T> targetV = SettableValue.build(targetType).build();
						ObservableModelSet.ModelSetInstanceBuilder reverseMSIBuilder = reverseModels.createInstance(modelSet.getUntil())
							.withAll(modelSet)//
							.with(sourcePlaceholder, sourceV.disableWith(ObservableValue.of(StdMsg.UNSUPPORTED_OPERATION)))//
							.with(targetPlaceholder, targetV.disableWith(ObservableValue.of(StdMsg.UNSUPPORTED_OPERATION)));
						Map<String, SettableValue<Object>> combinedVs = new LinkedHashMap<>();
						for (Map.Entry<String, RuntimeValuePlaceholder<SettableValue<?>, ? extends SettableValue<?>>> cv : combinedValues
							.entrySet()) {
							SettableValue<Object> value = SettableValue.build((TypeToken<Object>) cv.getValue().getType().getType(0))
								.build();
							combinedVs.put(cv.getKey(), value);
							reverseMSIBuilder.with(//
								(RuntimeValuePlaceholder<SettableValue<?>, SettableValue<Object>>) cv.getValue(), //
								value.disableWith(ObservableValue.of(StdMsg.UNSUPPORTED_OPERATION)));
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
							for (Map.Entry<String, RuntimeValuePlaceholder<SettableValue<?>, ? extends SettableValue<?>>> cv : combinedValues
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
							for (Map.Entry<String, RuntimeValuePlaceholder<SettableValue<?>, ? extends SettableValue<?>>> cv : combinedValues
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
							for (Map.Entry<String, RuntimeValuePlaceholder<SettableValue<?>, ? extends SettableValue<?>>> cv : combinedValues
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
							for (Map.Entry<String, RuntimeValuePlaceholder<SettableValue<?>, ? extends SettableValue<?>>> cv : combinedValues
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
							for (Map.Entry<String, RuntimeValuePlaceholder<SettableValue<?>, ? extends SettableValue<?>>> cv : combinedValues
								.entrySet()) {
								combinedVs.get(cv.getKey()).set(tvs.get(transformation.getArg(cvIdx)), null);
								cvIdx++;
							}
							return addAcceptEvld.get();
						};
						return new Transformation.SourceReplacingReverse<>(transformation, reverseFn, enabledFn, acceptFn, addFn,
							addAcceptFn, stateful, inexact);
					}

					@Override
					public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels)
						throws ModelInstantiationException {
						SettableValue<S> srcReversed = reversedV.get(sourceModels);
						SettableValue<S> newReversed = reversedV.forModelCopy(srcReversed, sourceModels, newModels);
						if (srcReversed != newReversed)
							return true;
						if (enabledV != null) {
							SettableValue<String> srcEnabled = enabledV.get(sourceModels);
							SettableValue<String> newEnabled = enabledV.forModelCopy(srcEnabled, sourceModels, newModels);
							if (srcEnabled != newEnabled)
								return true;
						}
						if (acceptV != null) {
							SettableValue<String> srcAccept = acceptV.get(sourceModels);
							SettableValue<String> newAccept = acceptV.forModelCopy(srcAccept, sourceModels, newModels);
							if (srcAccept != newAccept)
								return true;
						}
						if (addV != null) {
							SettableValue<S> srcAdd = addV.get(sourceModels);
							SettableValue<S> newAdd = addV.forModelCopy(srcAdd, sourceModels, newModels);
							if (srcAdd != newAdd)
								return true;
						}
						if (addAcceptV != null) {
							SettableValue<String> srcAccept = addAcceptV.get(sourceModels);
							SettableValue<String> newAccept = addAcceptV.forModelCopy(srcAccept, sourceModels, newModels);
							if (srcAccept != newAccept)
								return true;
						}
						return false;
					}

					@Override
					public List<? extends ModelValueSynth<?, ?>> getComponents() {
						ArrayList<ModelValueSynth<?, ?>> components = new ArrayList<>();
						components.add(reversedV);
						if (enabledV != null)
							components.add(enabledV);
						if (acceptV != null)
							components.add(acceptV);
						if (addV != null)
							components.add(addV);
						if (addAcceptV != null)
							components.add(addAcceptV);
						return components;
					}
				};
			}
		};
	}

	CompiledMapReverse createSourceModifier(ExpressoQIS reverse) throws QonfigInterpretationException {
		CompiledExpression reverseX = reverse.getValueExpression();
		CompiledExpression enabled = reverse.getAttributeExpression("enabled");
		CompiledExpression accept = reverse.getAttributeExpression("accept");
		CompiledExpression add = reverse.getAttributeExpression("add");
		CompiledExpression addAccept = reverse.getAttributeExpression("add-accept");
		String targetName = reverse.getAttributeText("target-as");
		try {
			reverse.getExpressoEnv().getModels().getNameChecker().checkName(targetName);
		} catch (IllegalArgumentException e) {
			throw new QonfigInterpretationException("Illegal name for target-as: '" + targetName + "'",
				reverse.getAttributeValuePosition("target-as", 0), targetName.length(), e);
		}
		return new CompiledMapReverse() {
			@Override
			public <S, T> InterpretedMapReverse<S, T> interpret(String sourceName, TypeToken<S> sourceType, TypeToken<T> targetType,
				Map<String, TypeToken<?>> combinedTypes) throws ExpressoInterpretationException {
				ObservableModelSet.Builder reverseModelBuilder = reverse.getExpressoEnv().getModels().wrap("mapModReverseModel");
				RuntimeValuePlaceholder<SettableValue<?>, SettableValue<S>> sourcePlaceholder = reverseModelBuilder
					.withRuntimeValue(sourceName, ModelTypes.Value.forType(sourceType));
				RuntimeValuePlaceholder<SettableValue<?>, SettableValue<T>> targetPlaceholder = reverseModelBuilder
					.withRuntimeValue(targetName, ModelTypes.Value.forType(targetType));
				Map<String, RuntimeValuePlaceholder<SettableValue<?>, ? extends SettableValue<?>>> combinedValues = new LinkedHashMap<>();
				for (Map.Entry<String, TypeToken<?>> combined : combinedTypes.entrySet())
					combinedValues.put(combined.getKey(),
						reverseModelBuilder.withRuntimeValue(combined.getKey(), ModelTypes.Value.forType(combined.getValue())));

				InterpretedModelSet reverseModels = reverseModelBuilder.build().interpret();
				reverse.setModels(reverseModels, null);

				ModelValueSynth<ObservableAction<?>, ObservableAction<?>> reversedV;
				try {
					reversedV = reverseX.evaluate(ModelTypes.Action.any());
				} catch (ExpressoInterpretationException e) {
					throw new ExpressoInterpretationException("Could not interpret reversed value", e.getPosition(), e.getErrorLength(), e);
				}
				ModelValueSynth<SettableValue<?>, SettableValue<String>> enabledV;
				try {
					enabledV = enabled == null ? null : enabled.evaluate(ModelTypes.Value.forType(String.class));
				} catch (ExpressoInterpretationException e) {
					throw new ExpressoInterpretationException("Could not interpret reversed enablement", e.getPosition(),
						e.getErrorLength(), e);
				}
				ModelValueSynth<SettableValue<?>, SettableValue<String>> acceptV;
				try {
					acceptV = accept == null ? null : accept.evaluate(ModelTypes.Value.forType(String.class));
				} catch (ExpressoInterpretationException e) {
					throw new ExpressoInterpretationException("Could not interpret reversed acceptance", e.getPosition(),
						e.getErrorLength(), e);
				}
				ModelValueSynth<SettableValue<?>, SettableValue<S>> addV;
				try {
					addV = add == null ? null : add.evaluate(ModelTypes.Value.forType(sourceType));
				} catch (ExpressoInterpretationException e) {
					throw new ExpressoInterpretationException("Could not interpret reversed add", e.getPosition(), e.getErrorLength(), e);
				}
				ModelValueSynth<SettableValue<?>, SettableValue<String>> addAcceptV;
				try {
					addAcceptV = addAccept == null ? null : addAccept.evaluate(ModelTypes.Value.forType(String.class));
				} catch (ExpressoInterpretationException e) {
					throw new ExpressoInterpretationException("Could not interpret reversed add acceptance", e.getPosition(),
						e.getErrorLength(), e);
				}
				return new InterpretedMapReverse<S, T>() {
					@Override
					public TransformReverse<S, T> reverse(Transformation<S, T> transformation, ModelSetInstance modelSet)
						throws ModelInstantiationException {
						SettableValue<S> sourceV = SettableValue.build(sourceType).build();
						SettableValue<T> targetV = SettableValue.build(targetType).build();
						ObservableModelSet.ModelSetInstanceBuilder reverseMSIBuilder = reverseModels.createInstance(modelSet.getUntil())
							.withAll(modelSet)//
							.with(sourcePlaceholder, sourceV.disableWith(ObservableValue.of(StdMsg.UNSUPPORTED_OPERATION)))//
							.with(targetPlaceholder, targetV.disableWith(ObservableValue.of(StdMsg.UNSUPPORTED_OPERATION)));
						Map<String, SettableValue<Object>> combinedVs = new LinkedHashMap<>();
						for (Map.Entry<String, RuntimeValuePlaceholder<SettableValue<?>, ? extends SettableValue<?>>> cv : combinedValues
							.entrySet()) {
							SettableValue<Object> value = SettableValue.build((TypeToken<Object>) cv.getValue().getType().getType(0))
								.build();
							combinedVs.put(cv.getKey(), value);
							reverseMSIBuilder.with((RuntimeValuePlaceholder<SettableValue<?>, SettableValue<Object>>) cv.getValue(),
								value.disableWith(ObservableValue.of(StdMsg.UNSUPPORTED_OPERATION)));
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
							for (Map.Entry<String, RuntimeValuePlaceholder<SettableValue<?>, ? extends SettableValue<?>>> cv : combinedValues
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
							for (Map.Entry<String, RuntimeValuePlaceholder<SettableValue<?>, ? extends SettableValue<?>>> cv : combinedValues
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
							for (Map.Entry<String, RuntimeValuePlaceholder<SettableValue<?>, ? extends SettableValue<?>>> cv : combinedValues
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
							for (Map.Entry<String, RuntimeValuePlaceholder<SettableValue<?>, ? extends SettableValue<?>>> cv : combinedValues
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
							for (Map.Entry<String, RuntimeValuePlaceholder<SettableValue<?>, ? extends SettableValue<?>>> cv : combinedValues
								.entrySet()) {
								combinedVs.get(cv.getKey()).set(tvs.get(transformation.getArg(cvIdx)), null);
								cvIdx++;
							}
							return addAcceptEvld.get();
						};
						return new Transformation.SourceModifyingReverse<>(reverseFn, enabledFn, acceptFn, addFn, addAcceptFn);
					}

					@Override
					public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels)
						throws ModelInstantiationException {
						ObservableAction<?> srcReversed = reversedV.get(sourceModels);
						ObservableAction<?> newReversed = reversedV.forModelCopy(srcReversed, sourceModels, newModels);
						if (srcReversed != newReversed)
							return true;
						if (enabledV != null) {
							SettableValue<String> srcEnabled = enabledV.get(sourceModels);
							SettableValue<String> newEnabled = enabledV.forModelCopy(srcEnabled, sourceModels, newModels);
							if (srcEnabled != newEnabled)
								return true;
						}
						if (acceptV != null) {
							SettableValue<String> srcAccept = acceptV.get(sourceModels);
							SettableValue<String> newAccept = acceptV.forModelCopy(srcAccept, sourceModels, newModels);
							if (srcAccept != newAccept)
								return true;
						}
						if (addV != null) {
							SettableValue<S> srcAdd = addV.get(sourceModels);
							SettableValue<S> newAdd = addV.forModelCopy(srcAdd, sourceModels, newModels);
							if (srcAdd != newAdd)
								return true;
						}
						if (addAcceptV != null) {
							SettableValue<String> srcAccept = addAcceptV.get(sourceModels);
							SettableValue<String> newAccept = addAcceptV.forModelCopy(srcAccept, sourceModels, newModels);
							if (srcAccept != newAccept)
								return true;
						}
						return false;
					}

					@Override
					public List<? extends ModelValueSynth<?, ?>> getComponents() {
						ArrayList<ModelValueSynth<?, ?>> components = new ArrayList<>();
						components.add(reversedV);
						if (enabledV != null)
							components.add(enabledV);
						if (acceptV != null)
							components.add(acceptV);
						if (addV != null)
							components.add(addV);
						if (addAcceptV != null)
							components.add(addAcceptV);
						return components;
					}
				};
			}
		};
	}

	private static boolean refersToSource(ObservableExpression ex, String sourceName) {
		return !ex.find(ex2 -> ex2 instanceof NameExpression && ((NameExpression) ex2).getNames().getFirst().getName().equals(sourceName))
			.isEmpty();
	}

	private ParsedSorting parseSorting(ExpressoQIS session) throws QonfigInterpretationException {
		String valueAs = session.getAttributeText("sort-value-as");
		String compareValueAs = session.getAttributeText("sort-compare-value-as");
		CompiledExpression sortWith = session.getAttributeExpression("sort-with");
		List<ExpressoQIS> sortBy = session.forChildren("sort-by");
		boolean ascending = session.getAttribute("ascending", boolean.class);
		// ModelInstanceType.SingleTyped<SettableValue<?>, Comparator<T>, SettableValue<Comparator<T>>> compareType = ModelTypes.Value
		// .forType(TypeTokens.get().keyFor(Comparator.class).parameterized(type));
		if (sortWith != null) {
			if (!sortBy.isEmpty())
				session.error("sort-with or sort-by may be used, but not both");
			if (valueAs == null)
				throw new QonfigInterpretationException("sort-with must be used with sort-value-as",
					sortWith.getElement().getPositionInFile(), 0);
			else if (compareValueAs == null)
				throw new QonfigInterpretationException("sort-with must be used with sort-compare-value-as",
					sortWith.getElement().getPositionInFile(), 0);
			return new ParsedSorting() {
				@Override
				public <T> ModelValueSynth<SettableValue<?>, SettableValue<Comparator<T>>> evaluate(TypeToken<T> type)
					throws ExpressoInterpretationException {
					ModelInstanceType.SingleTyped<SettableValue<?>, Comparator<T>, SettableValue<Comparator<T>>> compareType = ModelTypes.Value
						.forType(TypeTokens.get().keyFor(Comparator.class).parameterized(type));
					ObservableModelSet.Builder cModelBuilder = session.getExpressoEnv().getModels().wrap("sortAsModel");
					ObservableModelSet.RuntimeValuePlaceholder<SettableValue<?>, SettableValue<T>> valuePlaceholder = cModelBuilder
						.withRuntimeValue(valueAs, ModelTypes.Value.forType(type));
					ObservableModelSet.RuntimeValuePlaceholder<SettableValue<?>, SettableValue<T>> compareValuePlaceholder = cModelBuilder
						.withRuntimeValue(compareValueAs, ModelTypes.Value.forType(type));
					ObservableModelSet.Built cModel = cModelBuilder.build();
					ModelValueSynth<SettableValue<?>, SettableValue<Integer>> comparison;
					try {
						comparison = sortWith.evaluate(ModelTypes.Value.forType(int.class), session.getExpressoEnv().with(cModel, null));
					} catch (ExpressoInterpretationException e) {
						throw new ExpressoInterpretationException("Could not parse comparison", e.getPosition(), e.getErrorLength(), e);
					}
					InterpretedModelSet cModelWrapped;
					try {
						cModelWrapped = cModel.interpret();
					} catch (ExpressoInterpretationException e) {
						throw new ExpressoInterpretationException(e.getMessage(), e.getPosition(), e.getErrorLength(), e);
					}
					return new ModelValueSynth<SettableValue<?>, SettableValue<Comparator<T>>>() {
						@Override
						public ModelType<SettableValue<?>> getModelType() {
							return ModelTypes.Value;
						}

						@Override
						public ModelInstanceType<SettableValue<?>, SettableValue<Comparator<T>>> getType() {
							return compareType;
						}

						@Override
						public SettableValue<Comparator<T>> get(ModelSetInstance models) throws ModelInstantiationException {
							SettableValue<T> leftValue = SettableValue.build(type).build();
							SettableValue<T> rightValue = SettableValue.build(type).build();
							ModelSetInstance cModelInstance = cModelWrapped.createInstance(models.getUntil()).withAll(models)//
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
						public BetterList<ModelValueSynth<?, ?>> getCores() throws ExpressoInterpretationException {
							return comparison.getCores();
						}
					};
				}
			};
		} else if (!sortBy.isEmpty()) {
			if (valueAs == null)
				throw new QonfigInterpretationException("sort-by must be used with sort-value-as", session.getElement().getPositionInFile(),
					0);
			if (compareValueAs != null)
				session.warn("sort-compare-value-as is not used with sort-by");

			return new ParsedSorting() {
				@Override
				public <T> ModelValueSynth<SettableValue<?>, SettableValue<Comparator<T>>> evaluate(TypeToken<T> type)
					throws ExpressoInterpretationException {
					ObservableModelSet.Builder cModelBuilder = session.getExpressoEnv().getModels().wrap("sortByModel");
					ObservableModelSet.RuntimeValuePlaceholder<SettableValue<?>, SettableValue<T>> valuePlaceholder = cModelBuilder
						.withRuntimeValue(valueAs, ModelTypes.Value.forType(type));
					ObservableModelSet.Built cModel = cModelBuilder.build();

					List<CompiledExpression> subSortingXs = new ArrayList<>(sortBy.size());
					List<ParsedSorting> subSorting = new ArrayList<>(sortBy.size());
					try {
						// Can't parse these outside because the value expression is populated with the models,
						// which need the source value, which is typed
						for (ExpressoQIS sortByX : sortBy) {
							sortByX.setModels(cModel, null);
							subSortingXs.add(sortByX.getValueExpression());
							subSorting.add(sortByX.interpret(ParsedSorting.class));
						}
					} catch (QonfigInterpretationException e) {
						throw new ExpressoInterpretationException(e.getMessage(), e.getPosition(), e.getErrorLength(), e);
					}

					List<ModelValueSynth<SettableValue<?>, SettableValue<Object>>> sortByMaps = new ArrayList<>(sortBy.size());
					List<ModelValueSynth<SettableValue<?>, SettableValue<Comparator<Object>>>> sortByCVs = new ArrayList<>(sortBy.size());
					for (int s = 0; s < sortBy.size(); s++) {
						ModelValueSynth<SettableValue<?>, SettableValue<Object>> sortByMap = subSortingXs.get(s)
							.evaluate(ModelTypes.Value.anyAs());
						sortByMaps.add(sortByMap);
						sortByCVs.add(subSorting.get(s).evaluate((TypeToken<Object>) sortByMap.getType().getType(0)));
					}
					InterpretedModelSet cModelWrapped = cModel.interpret();

					ModelInstanceType.SingleTyped<SettableValue<?>, Comparator<T>, SettableValue<Comparator<T>>> compareType = ModelTypes.Value
						.forType(TypeTokens.get().keyFor(Comparator.class).parameterized(type));
					return new ModelValueSynth<SettableValue<?>, SettableValue<Comparator<T>>>() {
						@Override
						public ModelType<SettableValue<?>> getModelType() {
							return ModelTypes.Value;
						}

						@Override
						public ModelInstanceType<SettableValue<?>, SettableValue<Comparator<T>>> getType() {
							return compareType;
						}

						@Override
						public SettableValue<Comparator<T>> get(ModelSetInstance models) throws ModelInstantiationException {
							SettableValue<T> value = SettableValue.build(type).build();
							ModelSetInstance cModelInstance = cModelWrapped.createInstance(models.getUntil()).withAll(models)//
								.with(valuePlaceholder, value)//
								.build();
							List<SettableValue<Object>> sortByMapVs = new ArrayList<>(sortBy.size());
							for (ModelValueSynth<SettableValue<?>, SettableValue<Object>> sortByMapV : sortByMaps)
								sortByMapVs.add(sortByMapV.get(cModelInstance));
							List<Comparator<Object>> sortByComps = new ArrayList<>(sortBy.size());
							for (ModelValueSynth<SettableValue<?>, SettableValue<Comparator<Object>>> sortByCV : sortByCVs)
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
						public BetterList<ModelValueSynth<?, ?>> getCores() throws ExpressoInterpretationException {
							return BetterList.of(sortByMaps.stream(), vc -> vc.getCores().stream());
						}
					};
				}
			};
		} else
			return getDefaultSorting(session.getElement());
	}

	static ParsedSorting getDefaultSorting(QonfigElement element) {
		return new ParsedSorting() {
			@Override
			public <T> ModelValueSynth<SettableValue<?>, SettableValue<Comparator<T>>> evaluate(TypeToken<T> type)
				throws ExpressoInterpretationException {
				Comparator<T> compare = getDefaultSorting(TypeTokens.getRawType(type));
				if (compare != null)
					return ModelValueSynth.literal(TypeTokens.get().keyFor(Comparator.class).parameterized(type), compare,
						compare.toString());
				else
					throw new ExpressoInterpretationException(type + " is not Comparable, use either sort-with or sort-by",
						element.getPositionInFile(), 0);
			}

			private <T> Comparator<T> getDefaultSorting(Class<T> type) {
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
		};
	}

	static BetterList<TypeToken<?>> argList(TypeToken<?>... init) {
		return BetterTreeList.<TypeToken<?>> build().build().with(init);
	}
}
