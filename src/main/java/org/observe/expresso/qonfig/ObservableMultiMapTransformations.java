package org.observe.expresso.qonfig;

import java.util.function.Function;

import org.observe.SettableValue;
import org.observe.assoc.ObservableMap;
import org.observe.assoc.ObservableMultiMap;
import org.observe.assoc.ObservableMultiMap.MultiMapFlow;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollection.DistinctDataFlow;
import org.observe.collect.ObservableCollection.DistinctSortedDataFlow;
import org.observe.collect.ObservableSet;
import org.observe.expresso.CompiledExpressoEnv;
import org.observe.expresso.ExpressoCompilationException;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.ExElement.Def;
import org.observe.expresso.qonfig.ExpressoTransformations.MultiMapTransform;
import org.observe.expresso.qonfig.ExpressoTransformations.Operation;
import org.observe.expresso.qonfig.ExpressoTransformations.TransformInstantiator;
import org.observe.expresso.qonfig.ObservableCollectionTransformations.CollectionFlowSourcedTransformInstantiator;
import org.qommons.collect.BetterList;
import org.qommons.collect.MultiEntryHandle;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreterCore;
import org.qommons.ex.CheckedExceptionWrapper;

/** Transformations for {@link ModelTypes#MultiMap Multi-Map} model values */
public class ObservableMultiMapTransformations {
	/**
	 * A transformer capable of transforming an observable structure into an {@link ObservableCollection}. This type contains added
	 * capabilities so that when multiple flow operations are stacked, the intermediate structures don't need to be instantiated.
	 *
	 * @param <M1> The type of the source observable structure
	 * @param <M2> The type of the target collection
	 * @param <K> The key-type of the target multi-map
	 * @param <V> The value-type of the target multi-map
	 */
	public interface MultiMapFlowTransformInstantiator<M1, M2 extends ObservableMultiMap<?, ?>, K, V>
	extends Operation.Instantiator<M1, M2> {
		/**
		 * Transforms a source observable structure into a transformed multi-map flow
		 *
		 * @param source The source observable structure
		 * @param models The models to do the transformation
		 * @return The transformed flow
		 * @throws ModelInstantiationException If the transformation fails
		 */
		MultiMapFlow<K, V> transformToFlow(M1 source, ModelSetInstance models) throws ModelInstantiationException;

		@Override
		default M2 transform(M1 source, ModelSetInstance models) throws ModelInstantiationException {
			MultiMapFlow<K, V> flow = transformToFlow(source, models);
			return (M2) flow.gather();
		}
	}

	/**
	 * A transformer capable for transforming an observable structure into another via a {MultiMapFlow multi-map flow}. This type contains
	 * added capabilities so that when multiple flow operations are stacked, the intermediate structures don't need to be instantiated.
	 *
	 * @param <M1> The type of the source multi-map
	 * @param <M2> The type of the target observable structure
	 * @param <KS> The key-type of the source multi-map
	 * @param <VS> The value-type of the source multi-map
	 */
	public interface MultiMapFlowSourcedTransformInstantiator<M1, M2, KS, VS> extends Operation.Instantiator<M1, M2> {
		/**
		 * Transforms a source multi-map flow into a transformed observable structure
		 *
		 * @param flow The flow to transform
		 * @param models The models to do the transformation
		 * @return The transformed observable structure
		 * @throws ModelInstantiationException If the transformation fails
		 */
		M2 transformFromFlow(MultiMapFlow<KS, VS> flow, ModelSetInstance models) throws ModelInstantiationException;

		@Override
		default <S0> TransformInstantiator<S0, M2> after(TransformInstantiator<S0, ? extends M1> before) {
			if (before instanceof MultiMapFlowToFlowTransformInstantiator) {
				MultiMapFlowToFlowTransformInstantiator<S0, ? extends M1, Object, Object, KS, VS> flowBefore;
				flowBefore = (MultiMapFlowToFlowTransformInstantiator<S0, ? extends M1, Object, Object, KS, VS>) before;
				MultiMapFlowSourcedTransformInstantiator<M1, M2, KS, VS> next = this;
				return new MultiMapFlowSourcedTransformInstantiator<S0, M2, Object, Object>() {
					@Override
					public M2 transformFromFlow(MultiMapFlow<Object, Object> source, ModelSetInstance models)
						throws ModelInstantiationException {
						MultiMapFlow<KS, VS> sourceFlow = flowBefore.transformFlow(source, models);
						return next.transformFromFlow(sourceFlow, models);
					}

					@Override
					public M2 transform(S0 source, ModelSetInstance models) throws ModelInstantiationException {
						MultiMapFlow<KS, VS> sourceFlow = flowBefore.transformToFlow(source, models);
						return next.transformFromFlow(sourceFlow, models);
					}

					@Override
					public void instantiate() throws ModelInstantiationException {
						flowBefore.instantiate();
						next.instantiate();
					}

					@Override
					public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels)
						throws ModelInstantiationException {
						return flowBefore.isDifferent(sourceModels, newModels) || next.isDifferent(sourceModels, newModels);
					}

					@Override
					public String toString() {
						return flowBefore + "->" + next;
					}
				};
			} else if (before instanceof MultiMapFlowTransformInstantiator) {
				MultiMapFlowTransformInstantiator<S0, ? extends M1, KS, VS> flowBefore = (MultiMapFlowTransformInstantiator<S0, ? extends M1, KS, VS>) before;
				MultiMapFlowSourcedTransformInstantiator<M1, M2, KS, VS> next = this;
				return new Operation.Instantiator<S0, M2>() {
					@Override
					public M2 transform(S0 source, ModelSetInstance models) throws ModelInstantiationException {
						MultiMapFlow<KS, VS> sourceFlow = flowBefore.transformToFlow(source, models);
						return next.transformFromFlow(sourceFlow, models);
					}

					@Override
					public void instantiate() throws ModelInstantiationException {
						flowBefore.instantiate();
						next.instantiate();
					}

					@Override
					public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels)
						throws ModelInstantiationException {
						return flowBefore.isDifferent(sourceModels, newModels) || next.isDifferent(sourceModels, newModels);
					}

					@Override
					public String toString() {
						return flowBefore + "->" + next;
					}
				};
			} else if (before instanceof CollectionToMultiMapTransformInstantiator) {
				CollectionToMultiMapTransformInstantiator<S0, ?, Object, KS, VS> flowBefore;
				flowBefore = (CollectionToMultiMapTransformInstantiator<S0, ?, Object, KS, VS>) before;
				MultiMapFlowSourcedTransformInstantiator<M1, M2, KS, VS> next = this;
				return new CollectionFlowSourcedTransformInstantiator<S0, M2, Object>() {
					@Override
					public void instantiate() throws ModelInstantiationException {
						flowBefore.instantiate();
						next.instantiate();
					}

					@Override
					public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels)
						throws ModelInstantiationException {
						return flowBefore.isDifferent(sourceModels, newModels) || next.isDifferent(sourceModels, newModels);
					}

					@Override
					public M2 transform(S0 source, ModelSetInstance models) throws ModelInstantiationException {
						MultiMapFlow<KS, VS> mapFlow = flowBefore.transformToFlow(source, models);
						return next.transformFromFlow(mapFlow, models);
					}

					@Override
					public M2 transformFromFlow(CollectionDataFlow<?, ?, Object> flow, ModelSetInstance models)
						throws ModelInstantiationException {
						MultiMapFlow<KS, VS> mapFlow = flowBefore.toMapFlow(flow, models);
						return next.transformFromFlow(mapFlow, models);
					}
				};
			} else
				return Operation.Instantiator.super.after(before);
		}
	}

	/**
	 * A transformer capable of transforming an observable structure or a {@link MultiMapFlow multi-map flow} into an
	 * {@link ObservableMultiMap}. This type contains added capabilities so that when multiple flow operations are stacked, the intermediate
	 * structures don't need to be instantiated.
	 *
	 * @param <M1> The type of the source observable structure
	 * @param <M2> The type of the target collection
	 * @param <KS> The key-type of the source multi-map
	 * @param <VS> The value-type of the source multi-map
	 * @param <KT> The key-type of the target multi-map
	 * @param <VT> The value-type of the target multi-map
	 */
	public interface MultiMapFlowToFlowTransformInstantiator<M1, M2 extends ObservableMultiMap<?, ?>, KS, VS, KT, VT>
	extends MultiMapFlowTransformInstantiator<M1, M2, KT, VT>, MultiMapFlowSourcedTransformInstantiator<M1, M2, KS, VS> {
		/**
		 * Transforms a multi-map flow
		 *
		 * @param source The source flow
		 * @param models The models to do the transformation
		 * @return The transformed flow
		 * @throws ModelInstantiationException If the transformation fails
		 */
		MultiMapFlow<KT, VT> transformFlow(MultiMapFlow<KS, VS> source, ModelSetInstance models) throws ModelInstantiationException;

		@Override
		default M2 transformFromFlow(MultiMapFlow<KS, VS> flow, ModelSetInstance models) throws ModelInstantiationException {
			return (M2) transformFlow(flow, models)//
				.gather();
		}

		@Override
		default <S0> TransformInstantiator<S0, M2> after(TransformInstantiator<S0, ? extends M1> before) {
			if (before instanceof MultiMapFlowToFlowTransformInstantiator) {
				MultiMapFlowToFlowTransformInstantiator<S0, ? extends M1, Object, Object, KS, VS> flowBefore;
				flowBefore = (MultiMapFlowToFlowTransformInstantiator<S0, ? extends M1, Object, Object, KS, VS>) before;
				MultiMapFlowToFlowTransformInstantiator<M1, M2, KS, VS, KT, VT> next = this;
				return new MultiMapFlowToFlowTransformInstantiator<S0, M2, Object, Object, KT, VT>() {
					@Override
					public MultiMapFlow<KT, VT> transformFlow(MultiMapFlow<Object, Object> source, ModelSetInstance models)
						throws ModelInstantiationException {
						MultiMapFlow<KS, VS> sourceFlow = flowBefore.transformFlow(source, models);
						return next.transformFlow(sourceFlow, models);
					}

					@Override
					public MultiMapFlow<KT, VT> transformToFlow(S0 source, ModelSetInstance models) throws ModelInstantiationException {
						MultiMapFlow<KS, VS> sourceFlow = flowBefore.transformToFlow(source, models);
						return next.transformFlow(sourceFlow, models);
					}

					@Override
					public void instantiate() throws ModelInstantiationException {
						flowBefore.instantiate();
						next.instantiate();
					}

					@Override
					public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels)
						throws ModelInstantiationException {
						return flowBefore.isDifferent(sourceModels, newModels) || next.isDifferent(sourceModels, newModels);
					}

					@Override
					public String toString() {
						return flowBefore + "->" + next;
					}
				};
			} else if (before instanceof MultiMapFlowTransformInstantiator) {
				MultiMapFlowTransformInstantiator<S0, ? extends M1, KS, VS> flowBefore = (MultiMapFlowTransformInstantiator<S0, ? extends M1, KS, VS>) before;
				MultiMapFlowToFlowTransformInstantiator<M1, M2, KS, VS, KT, VT> next = this;
				return new MultiMapFlowTransformInstantiator<S0, M2, KT, VT>() {
					@Override
					public MultiMapFlow<KT, VT> transformToFlow(S0 source, ModelSetInstance models) throws ModelInstantiationException {
						MultiMapFlow<KS, VS> sourceFlow = flowBefore.transformToFlow(source, models);
						return next.transformFlow(sourceFlow, models);
					}

					@Override
					public void instantiate() throws ModelInstantiationException {
						flowBefore.instantiate();
						next.instantiate();
					}

					@Override
					public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels)
						throws ModelInstantiationException {
						return flowBefore.isDifferent(sourceModels, newModels) || next.isDifferent(sourceModels, newModels);
					}

					@Override
					public String toString() {
						return flowBefore + "->" + next;
					}
				};
			} else if (before instanceof CollectionToMultiMapTransformInstantiator) {
				CollectionToMultiMapTransformInstantiator<S0, ?, Object, KS, VS> flowBefore;
				flowBefore = (CollectionToMultiMapTransformInstantiator<S0, ?, Object, KS, VS>) before;
				MultiMapFlowToFlowTransformInstantiator<M1, M2, KS, VS, KT, VT> next = this;
				return new CollectionToMultiMapTransformInstantiator<S0, M2, Object, KT, VT>() {
					@Override
					public void instantiate() throws ModelInstantiationException {
						flowBefore.instantiate();
						next.instantiate();
					}

					@Override
					public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels)
						throws ModelInstantiationException {
						return flowBefore.isDifferent(sourceModels, newModels) || next.isDifferent(sourceModels, newModels);
					}

					@Override
					public M2 transform(S0 source, ModelSetInstance models) throws ModelInstantiationException {
						MultiMapFlow<KS, VS> mapFlow = flowBefore.transformToFlow(source, models);
						return next.transformFromFlow(mapFlow, models);
					}

					@Override
					public M2 transformFromFlow(CollectionDataFlow<?, ?, Object> flow, ModelSetInstance models)
						throws ModelInstantiationException {
						MultiMapFlow<KS, VS> mapFlow = flowBefore.toMapFlow(flow, models);
						return next.transformFromFlow(mapFlow, models);
					}

					@Override
					public CollectionDataFlow<?, ?, Object> getSourceFlow(S0 source, ModelSetInstance models)
						throws ModelInstantiationException {
						return flowBefore.getSourceFlow(source, models);
					}

					@Override
					public MultiMapFlow<KT, VT> toMapFlow(CollectionDataFlow<?, ?, Object> sourceFlow, ModelSetInstance models)
						throws ModelInstantiationException {
						MultiMapFlow<KS, VS> intermediateFlow = flowBefore.toMapFlow(sourceFlow, models);
						return next.transformFlow(intermediateFlow, models);
					}
				};
			} else
				return MultiMapFlowSourcedTransformInstantiator.super.after(before);
		}
	}

	/**
	 * A transformer capable of transforming an observable structure into a multi-map via a {@link CollectionDataFlow collection flow}. This
	 * type contains added capabilities so that when multiple flow operations are stacked, the intermediate structures don't need to be
	 * instantiated.
	 *
	 * @param <M1> The type of the source observable structure
	 * @param <M2> The type of the target multi-map
	 * @param <T> The type of the collection flow that is this transformer's intermediate collection type
	 * @param <K> The key-type of the target multi-map
	 * @param <V> The value-type of the target multi-map
	 */
	public interface CollectionToMultiMapTransformInstantiator<M1, M2 extends ObservableMultiMap<?, ?>, T, K, V>
	extends CollectionFlowSourcedTransformInstantiator<M1, M2, T>, MultiMapFlowTransformInstantiator<M1, M2, K, V> {
		/**
		 * @param source The source observable structure to transform
		 * @param models The models to use for transformation
		 * @return The intermediate collection flow that this transform is capable of converting to a multi-map
		 * @throws ModelInstantiationException If the transformation fails
		 */
		CollectionDataFlow<?, ?, T> getSourceFlow(M1 source, ModelSetInstance models) throws ModelInstantiationException;

		/**
		 * Transforms the intermediate collection flow for this transform into a multi-map flow that may be gathered into the target
		 * observable structure
		 *
		 * @param sourceFlow The intermediate collection data flow to transform
		 * @param models The models to use for transformation
		 * @return The transformed multi-map flow
		 * @throws ModelInstantiationException If the transformation fails
		 */
		MultiMapFlow<K, V> toMapFlow(CollectionDataFlow<?, ?, T> sourceFlow, ModelSetInstance models) throws ModelInstantiationException;

		@Override
		default M2 transform(M1 source, ModelSetInstance models) throws ModelInstantiationException {
			return (M2) transformToFlow(source, models)//
				.gather();
		}

		@Override
		default MultiMapFlow<K, V> transformToFlow(M1 source, ModelSetInstance models) throws ModelInstantiationException {
			CollectionDataFlow<?, ?, T> sourceFlow = getSourceFlow(source, models);
			return toMapFlow(sourceFlow, models);
		}

		@Override
		default M2 transformFromFlow(CollectionDataFlow<?, ?, T> flow, ModelSetInstance models) throws ModelInstantiationException {
			MultiMapFlow<K, V> mapFlow = toMapFlow(flow, models);
			return (M2) mapFlow.gather();
		}
	}

	private ObservableMultiMapTransformations() {
	}

	/**
	 * Configures an interpreter with multi-map transformation capabilities
	 *
	 * @param interpreter The interpretation builder to configure
	 */
	public static void configureTransformation(QonfigInterpreterCore.Builder interpreter) {
		// interpreter.createWith(SingleMapTransform.SINGLE_MAP, MultiMapTransform.class, ExElement.creator(SingleMapTransform::new));
		interpreter.createWith(MapTransform.MAP_TRANSFORM, MultiMapTransform.class, ExElement.creator(MapTransform::new));
		interpreter.createWith(MultiEntrySet.MULTI_ENTRY_SET, MultiEntrySet.class, ExElement.creator(MultiEntrySet::new));
	}

	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = SingleMapTransform.SINGLE_MAP,
		interpretation = SingleMapTransform.Interpreted.class)
	static class SingleMapTransform<M1 extends ObservableMultiMap<?, ?>, M2 extends ObservableMap<?, ?>>
	extends ExElement.Def.Abstract<ExElement> implements MultiMapTransform<M1, M2, ExElement> {
		/** The XML name of this element */
		public static final String SINGLE_MAP = "single-map";

		private boolean isAlwaysFirst;
		private ModelType<? extends M2> theTargetModelType;

		SingleMapTransform(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@QonfigAttributeGetter("always-first")
		public boolean isAlwaysFirst() {
			return isAlwaysFirst;
		}

		@Override
		public ModelType<? extends M2> getTargetModelType() {
			return theTargetModelType;
		}

		@Override
		public void update(ExpressoQIS session, ModelType<M1> sourceModelType) throws QonfigInterpretationException {
			if (sourceModelType == ModelTypes.SortedMultiMap)
				theTargetModelType = (ModelType<M2>) ModelTypes.SortedMap;
			else
				theTargetModelType = (ModelType<M2>) ModelTypes.Map;
			super.update(session);
		}

		@Override
		public Interpreted<M1, ?, M2, ?, ?, ?> interpret(ExElement.Interpreted<?> parent) throws ExpressoInterpretationException {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<M1 extends ObservableMultiMap<?, ?>, MV1 extends M1, M2 extends ObservableMap<?, ?>, MV2 extends M2, K, V>
		extends ExElement.Interpreted.Abstract<ExElement> implements Operation.Interpreted<M1, MV1, M2, MV2, ExElement> {
			private ModelInstanceType<? extends M2, ? extends MV2> theTargetType;

			Interpreted(SingleMapTransform<M1, M2> definition, org.observe.expresso.qonfig.ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public SingleMapTransform<M1, M2> getDefinition() {
				return (SingleMapTransform<M1, M2>) super.getDefinition();
			}

			@Override
			public void update(ModelInstanceType<M1, MV1> sourceType) throws ExpressoInterpretationException {
				theTargetType = (ModelInstanceType<M2, MV2>) getDefinition().getTargetModelType().forTypes(sourceType.getType(0),
					sourceType.getType(1));
				super.update();
			}

			@Override
			public ModelInstanceType<? extends M2, ? extends MV2> getTargetType() {
				return theTargetType;
			}

			@Override
			public BetterList<InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList.empty();
			}

			@Override
			public Instantiator<MV1, MV2, K, V> instantiate() throws ModelInstantiationException {
				return new Instantiator<>(this);
			}
		}

		static class Instantiator<M1 extends ObservableMultiMap<?, ?>, M2 extends ObservableMap<?, ?>, K, V>
		implements MultiMapFlowSourcedTransformInstantiator<M1, M2, K, V> {
			private boolean isAlwaysFirst;

			Instantiator(Interpreted<?, M1, ?, M2, K, V> interpreted) {
				isAlwaysFirst = interpreted.getDefinition().isAlwaysFirst();
			}

			@Override
			public void instantiate() throws ModelInstantiationException {
			}

			@Override
			public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				return false;
			}

			@Override
			public M2 transform(M1 source, ModelSetInstance models) throws ModelInstantiationException {
				return (M2) source.singleMap(isAlwaysFirst);
			}

			@Override
			public M2 transformFromFlow(MultiMapFlow<K, V> flow, ModelSetInstance models) throws ModelInstantiationException {
				return (M2) flow.gather()//
					.singleMap(isAlwaysFirst);
			}
		}
	}

	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = MapTransform.MAP_TRANSFORM,
		interpretation = MapTransform.Interpreted.class)
	static class MapTransform<M1 extends ObservableMultiMap<?, ?>, M2, C1 extends ObservableCollection<?>, C2>
	extends ExpressoTransformations.AbstractExpressoTransformedElement<M1, C1, M2, C2>
	implements MultiMapTransform<M1, M2, ModelValueElement<?>> {
		public static final String MAP_TRANSFORM = "map-transform";

		private boolean isKey;
		private ModelType<M1> theSourceModelType;
		private ModelType<M2> theTargetModelType;

		public MapTransform(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		/** @return Whether this transformation is for the map's keys or values */
		@QonfigAttributeGetter("type")
		public boolean isKey() {
			return isKey;
		}

		public boolean isSingleMap() {
			return theTargetModelType == ModelTypes.Map || theTargetModelType == ModelTypes.SortedMap;
		}

		public boolean isSorted() {
			return theSourceModelType == ModelTypes.SortedMultiMap;
		}

		@Override
		public ModelType<M2> getModelType(CompiledExpressoEnv env) throws ExpressoCompilationException {
			throw new ExpressoCompilationException(MAP_TRANSFORM + " cannot be used as a model value", env.reporting().getPosition(), 0);
		}

		@Override
		public void prepareModelValue(ExpressoQIS session) throws QonfigInterpretationException {
			throw new QonfigInterpretationException(MAP_TRANSFORM + " cannot be used as a model value", session.reporting().getPosition(),
				0);
		}

		@Override
		protected ModelType<C1> getInternalSourceModelType() throws ExpressoCompilationException {
			throw new ExpressoCompilationException(MAP_TRANSFORM + " cannot be used as a model value", reporting().getPosition(), 0);
		}

		@Override
		public ExpressoTransformations.AbstractExpressoTransformedElement.Interpreted<M1, ?, C1, ?, M2, ?, C2, ?> interpretValue(
			ExElement.Interpreted<?> parent) {
			throw new IllegalStateException(MAP_TRANSFORM + " cannot be used as a model value");
		}

		@Override
		public CompiledExpression getSource() {
			return null;
		}

		@Override
		public ModelType<? extends M2> getTargetModelType() {
			return theTargetModelType;
		}

		@Override
		public void update(ExpressoQIS session, ModelType<M1> sourceModelType) throws QonfigInterpretationException {
			ExpressoQIS transformSession = session.asElement(session.getFocusType().getSuperElement());
			super.update(transformSession);
			isKey = "key".equals(session.getAttributeText("type"));
			ModelType<C1> collectionSourceType;
			if (isKey) {
				if (sourceModelType == ModelTypes.SortedMultiMap)
					collectionSourceType = (ModelType<C1>) ModelTypes.SortedSet;
				else
					collectionSourceType = (ModelType<C1>) ModelTypes.Set;
			} else {
				collectionSourceType = (ModelType<C1>) ModelTypes.Collection;
			}
			prepareModelValue(transformSession, collectionSourceType);
			ModelType<C2> targetModelType = getInternalTargetType();
			if (isKey) {
				if (targetModelType == ModelTypes.SortedSet)
					theTargetModelType = (ModelType<M2>) ModelTypes.SortedMultiMap;
				else if (targetModelType == ModelTypes.Set)
					theTargetModelType = (ModelType<M2>) ModelTypes.MultiMap;
				else {
					Operation<?, ?, ?> last = getOperations().get(getOperations().size() - 1);
					throw new QonfigInterpretationException("The resulting model type of a key-type " + MAP_TRANSFORM
						+ " operation must be a set or sorted-set, not " + targetModelType, last.reporting().getPosition(), 0);
				}
			} else {
				if (targetModelType == ModelTypes.Value) {
					if (sourceModelType == ModelTypes.SortedMultiMap)
						theTargetModelType = (ModelType<M2>) ModelTypes.SortedMap;
					else
						theTargetModelType = (ModelType<M2>) ModelTypes.Map;
				} else if (targetModelType == ModelTypes.Collection || targetModelType == ModelTypes.Set
					|| targetModelType == ModelTypes.SortedCollection || targetModelType == ModelTypes.SortedSet) {
					theTargetModelType = (ModelType<M2>) sourceModelType;
				} else {
					Operation<?, ?, ?> last = getOperations().get(getOperations().size() - 1);
					throw new QonfigInterpretationException(
						"The resulting model type of a value-type " + MAP_TRANSFORM
						+ " operation must be a value, list, sorted-list, set or sorted-set, not " + targetModelType,
						last.reporting().getPosition(), 0);
				}
			}
		}

		@Override
		public Interpreted<M1, ?, C1, ?, M2, ?, C2, ?, ?, ?, ?, ?> interpret(ExElement.Interpreted<?> parent)
			throws ExpressoInterpretationException {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<M1 extends ObservableMultiMap<?, ?>, MV1 extends M1, //
		C1 extends ObservableCollection<?>, CV1 extends C1, //
		M2, MV2 extends M2, //
		C2, CV2 extends C2, //
		KS, VS, KT, VT>
		extends ExpressoTransformations.AbstractExpressoTransformedElement.Interpreted<M1, MV1, C1, CV1, M2, MV2, C2, CV2>
		implements Operation.Interpreted<M1, MV1, M2, MV2, ModelValueElement<MV2>> {
			private ModelInstanceType<ObservableMultiMap<?, ?>, ObservableMultiMap<KT, VT>> theTargetType;

			Interpreted(MapTransform<M1, M2, C1, C2> def, ExElement.Interpreted<?> parent) {
				super(def, parent);
			}

			@Override
			public MapTransform<M1, M2, C1, C2> getDefinition() {
				return (MapTransform<M1, M2, C1, C2>) super.getDefinition();
			}

			@Override
			public ModelInstanceType<M2, MV2> getType() {
				throw new IllegalStateException(MAP_TRANSFORM + " cannot be used as a model value");
			}

			@Override
			public void updateValue() throws ExpressoInterpretationException {
				throw new ExpressoInterpretationException(MAP_TRANSFORM + " cannot be used as a model value", reporting().getPosition(),
					0);
			}

			@Override
			public BetterList<InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList.of(getOperations().stream().flatMap(op -> op.getComponents().stream()));
			}

			@Override
			public InterpretedValueSynth<C1, CV1> getSource() {
				return null;
			}

			@Override
			public void update(ModelInstanceType<M1, MV1> sourceType) throws ExpressoInterpretationException {
				super.update();
				ModelInstanceType<C1, CV1> collectionSourceType = (ModelInstanceType<C1, CV1>) ModelTypes.Collection
					.forType(sourceType.getType(getDefinition().isKey() ? 0 : 1));
				doUpdateValue(collectionSourceType);
				if (getDefinition().isKey()) {
					theTargetType = (ModelInstanceType<ObservableMultiMap<?, ?>, ObservableMultiMap<KT, VT>>) getDefinition()
						.getTargetModelType().forTypes(getInternalType().getType(0), sourceType.getType(1));
				} else {
					theTargetType = (ModelInstanceType<ObservableMultiMap<?, ?>, ObservableMultiMap<KT, VT>>) getDefinition()
						.getTargetModelType().forTypes(sourceType.getType(0), getInternalType().getType(0));
				}
			}

			@Override
			public ModelInstanceType<? extends M2, ? extends MV2> getTargetType() {
				return (ModelInstanceType<M2, MV2>) theTargetType;
			}

			@Override
			public MultiMapTransformInstantiator<MV1, CV1, MV2, CV2, KS, VS, KT, VT> instantiate() throws ModelInstantiationException {
				MultiMapTransformInstantiator<?, ?, ?, ?, ?, ?, ?, ?> instantiator;
				if (getDefinition().isKey()) {
					instantiator = new MultiMapKeyTransformInstantiator<>(
						(Interpreted<?, MV1, ?, CV1, ?, ObservableMultiMap<?, ?>, ?, ObservableSet<?>, KS, VT, KT, VT>) this);
				} else {
					if (getDefinition().isSingleMap())
						instantiator = new SingleMapValueTransformInstantiator<>(
							(Interpreted<?, MV1, ?, CV1, ?, ObservableMap<?, ?>, ?, SettableValue<?>, KT, VS, KT, VT>) this);
					else
						instantiator = new MultiMapValueTransformInstantiator<>(
							(Interpreted<?, MV1, ?, CV1, ?, ObservableMultiMap<?, ?>, ?, ObservableCollection<?>, KT, VS, KT, VT>) this);
				}
				return (MultiMapTransformInstantiator<MV1, CV1, MV2, CV2, KS, VS, KT, VT>) instantiator;
			}
		}

		static abstract class MultiMapTransformInstantiator<MV1 extends ObservableMultiMap<?, ?>, CV1 extends ObservableCollection<?>, MV2, CV2, KS, VS, KT, VT>
		extends ExpressoTransformations.AbstractExpressoTransformedElement.Instantiator<MV1, CV1, MV2, CV2>
		implements Operation.Instantiator<MV1, MV2> {
			protected MultiMapTransformInstantiator(MapTransform.Interpreted<?, MV1, ?, CV1, ?, MV2, ?, CV2, KS, VS, KT, VT> interpreted)
				throws ModelInstantiationException {
				super(interpreted);
			}

			@Override
			public MV2 evaluate(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
				throw new ModelInstantiationException(MAP_TRANSFORM + " cannot be used as a model value", reporting().getPosition(), 0);
			}

			@Override
			public MV2 forModelCopy(MV2 value, ModelSetInstance sourceModels, ModelSetInstance newModels)
				throws ModelInstantiationException {
				throw new ModelInstantiationException(MAP_TRANSFORM + " cannot be used as a model value", reporting().getPosition(), 0);
			}
		}

		static class MultiMapKeyTransformInstantiator<MV1 extends ObservableMultiMap<?, ?>, CV1 extends ObservableCollection<?>, //
		MV2 extends ObservableMultiMap<?, ?>, CV2 extends ObservableSet<?>, KS, KT, V>
		extends MultiMapTransformInstantiator<MV1, CV1, MV2, CV2, KS, V, KT, V>
		implements MultiMapFlowToFlowTransformInstantiator<MV1, MV2, KS, V, KT, V> {
			private boolean isSorted;

			MultiMapKeyTransformInstantiator(MapTransform.Interpreted<?, MV1, ?, CV1, ?, MV2, ?, CV2, KS, V, KT, V> interpreted)
				throws ModelInstantiationException {
				super(interpreted);
				isSorted = interpreted.getDefinition().isSorted();
				for (int i = 0; i < getOperations().size(); i++) {
					if (!(getOperations().get(i) instanceof ObservableCollectionTransformations.CollectionFlowToFlowTransformInstantiator))
						throw new ModelInstantiationException("This operation is not supported for " + MAP_TRANSFORM,
							interpreted.getOperations().get(i).reporting().getPosition(), 0);
				}
			}

			@Override
			public MultiMapFlow<KT, V> transformToFlow(MV1 source, ModelSetInstance models) throws ModelInstantiationException {
				return transformFlow((MultiMapFlow<KS, V>) source.flow(), models);
			}

			@Override
			public MultiMapFlow<KT, V> transformFlow(MultiMapFlow<KS, V> source, ModelSetInstance models)
				throws ModelInstantiationException {
				instantiate();
				try {
					if (isSorted) {
						return source.withKeys(keyFlow -> {
							CollectionDataFlow<Object, ?, Object> flow = (CollectionDataFlow<Object, ?, Object>) keyFlow;
							for (Operation.Instantiator<?, ?> op : getOperations()) {
								try {
									flow = (CollectionDataFlow<Object, ?, Object>) ((ObservableCollectionTransformations.CollectionFlowToFlowTransformInstantiator<?, ?, Object, Object>) op)
										.transformFlow(flow, models);
								} catch (ModelInstantiationException e) {
									e.fillInStackTrace();
									throw new CheckedExceptionWrapper(e);
								}
							}
							return (DistinctSortedDataFlow<?, ?, KT>) flow;
						});
					} else {
						return source.withKeys(keyFlow -> {
							CollectionDataFlow<Object, ?, Object> flow = (CollectionDataFlow<Object, ?, Object>) keyFlow;
							for (Operation.Instantiator<?, ?> op : getOperations()) {
								try {
									flow = (CollectionDataFlow<Object, ?, Object>) ((ObservableCollectionTransformations.CollectionFlowToFlowTransformInstantiator<?, ?, Object, Object>) op)
										.transformFlow(flow, models);
								} catch (ModelInstantiationException e) {
									e.fillInStackTrace();
									throw new CheckedExceptionWrapper(e);
								}
							}
							return (DistinctDataFlow<?, ?, KT>) flow;
						});
					}
				} catch (CheckedExceptionWrapper e) {
					ModelInstantiationException mie = CheckedExceptionWrapper.getThrowable(e, ModelInstantiationException.class);
					if (mie != null)
						throw mie;
					throw e;
				}
			}
		}

		static class SingleMapValueTransformInstantiator<MV1 extends ObservableMultiMap<?, ?>, CV1 extends ObservableCollection<?>, //
		MV2 extends ObservableMap<?, ?>, CV2 extends SettableValue<?>, K, VS, VT>
		extends MultiMapTransformInstantiator<MV1, CV1, MV2, CV2, K, VS, K, VT>
		implements MultiMapFlowSourcedTransformInstantiator<MV1, MV2, K, VS> {
			private final String theLocation;

			SingleMapValueTransformInstantiator(MapTransform.Interpreted<?, MV1, ?, CV1, ?, MV2, ?, CV2, K, VS, K, VT> interpreted)
				throws ModelInstantiationException {
				super(interpreted);
				theLocation = interpreted.reporting().getFileLocation().getPosition(0).toShortString();
			}

			@Override
			public MV2 transform(MV1 source, ModelSetInstance models) throws ModelInstantiationException {
				instantiate(models);
				boolean[] initialized = new boolean[1];
				String location = theLocation;
				ObservableMultiMap<K, VS> sourceMap = (ObservableMultiMap<K, VS>) source;
				ObservableMap<K, VT> resultMap;
				try {
					resultMap = sourceMap.observeSingleMap((values, until) -> {
						try {
							return (SettableValue<VT>) transformInternal((CV1) values, models.until(until));
						} catch (ModelInstantiationException e) {
							if (initialized[0])
								reporting().error("Error transforming map value collection", e);
							else {
								e.fillInStackTrace();
								throw new CheckedExceptionWrapper(e);
							}
							return SettableValue.of(null, location + ": " + (e.getMessage() != null ? e.getMessage() : e.toString()));
						}
					}, models.getUntil());
				} catch (CheckedExceptionWrapper e) {
					ModelInstantiationException mie = CheckedExceptionWrapper.getThrowable(e, ModelInstantiationException.class);
					if (mie != null)
						throw mie;
					throw e;
				} finally {
					initialized[0] = true;
				}
				return (MV2) resultMap;
			}

			@Override
			public MV2 transformFromFlow(MultiMapFlow<K, VS> flow, ModelSetInstance models) throws ModelInstantiationException {
				return transform((MV1) flow.gather(), models);
			}
		}

		static class MultiMapValueTransformInstantiator<MV1 extends ObservableMultiMap<?, ?>, CV1 extends ObservableCollection<?>, //
		MV2 extends ObservableMultiMap<?, ?>, CV2 extends ObservableCollection<?>, K, VS, VT>
		extends MultiMapTransformInstantiator<MV1, CV1, MV2, CV2, K, VS, K, VT>
		implements MultiMapFlowToFlowTransformInstantiator<MV1, MV2, K, VS, K, VT> {
			MultiMapValueTransformInstantiator(MapTransform.Interpreted<?, MV1, ?, CV1, ?, MV2, ?, CV2, K, VS, K, VT> interpreted)
				throws ModelInstantiationException {
				super(interpreted);
				for (int i = 0; i < getOperations().size(); i++) {
					if (!(getOperations().get(i) instanceof ObservableCollectionTransformations.CollectionFlowToFlowTransformInstantiator))
						throw new ModelInstantiationException("This operation is not supported for " + MAP_TRANSFORM,
							interpreted.getOperations().get(i).reporting().getPosition(), 0);
				}
			}

			@Override
			public MultiMapFlow<K, VT> transformToFlow(MV1 source, ModelSetInstance models) throws ModelInstantiationException {
				return transformFlow((MultiMapFlow<K, VS>) source.flow(), models);
			}

			@Override
			public MultiMapFlow<K, VT> transformFlow(MultiMapFlow<K, VS> source, ModelSetInstance models)
				throws ModelInstantiationException {
				try {
					return source.withValues(valueFFlow -> {
						CollectionDataFlow<Object, ?, Object> flow = (CollectionDataFlow<Object, ?, Object>) valueFFlow;
						for (Operation.Instantiator<?, ?> op : getOperations()) {
							try {
								flow = (CollectionDataFlow<Object, ?, Object>) ((ObservableCollectionTransformations.CollectionFlowToFlowTransformInstantiator<?, ?, Object, Object>) op)
									.transformFlow(flow, models);
							} catch (ModelInstantiationException e) {
								e.fillInStackTrace();
								throw new CheckedExceptionWrapper(e);
							}
						}
						return (CollectionDataFlow<?, ?, VT>) flow;
					});
				} catch (CheckedExceptionWrapper e) {
					ModelInstantiationException mie = CheckedExceptionWrapper.getThrowable(e, ModelInstantiationException.class);
					if (mie != null)
						throw mie;
					throw e;
				}
			}
		}
	}

	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = MultiEntrySet.MULTI_ENTRY_SET,
		interpretation = MultiEntrySet.Interpreted.class)
	static class MultiEntrySet<M extends ObservableMultiMap<?, ?>> extends ExElement.Def.Abstract<ExElement>
	implements MultiMapTransform<M, ObservableCollection<?>, ExElement> {
		public static final String MULTI_ENTRY_SET = "multi-entry-set";

		private ModelComponentId theKeyAs;
		private ModelComponentId theValuesAs;
		private CompiledExpression theTransform;

		public MultiEntrySet(Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@QonfigAttributeGetter("key-as")
		public ModelComponentId getKeyAs() {
			return theKeyAs;
		}

		@QonfigAttributeGetter("values-as")
		public ModelComponentId getValuesAs() {
			return theValuesAs;
		}

		@QonfigAttributeGetter
		public CompiledExpression getTransform() {
			return theTransform;
		}

		@Override
		public ModelType<ObservableCollection<?>> getTargetModelType() {
			return ModelTypes.Collection;
		}

		@Override
		public void update(ExpressoQIS session, ModelType<M> sourceModelType) throws QonfigInterpretationException {
			String keyAs = session.getAttributeText("key-as");
			String valuesAs = session.getAttributeText("values-as");
			ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
			theKeyAs = elModels.getElementValueModelId(keyAs);
			theValuesAs = elModels.getElementValueModelId(valuesAs);
			elModels.<Interpreted<M, ?, ?, ?, ?>, SettableValue<?>> satisfyElementSingleValueType(theKeyAs, ModelTypes.Value,
				interp -> interp.getSourceType().getType(0));
			elModels.<Interpreted<M, ?, ?, ?, ?>, ObservableCollection<?>> satisfyElementSingleValueType(theValuesAs, ModelTypes.Collection,
				interp -> interp.getSourceType().getType(1));
		}

		@Override
		public Interpreted<M, ?, ?, ?, ? extends ExElement> interpret(ExElement.Interpreted<?> parent)
			throws ExpressoInterpretationException {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<M1 extends ObservableMultiMap<?, ?>, K, V, M2 extends M1, T>
		extends ExElement.Interpreted.Abstract<ExElement>
		implements Operation.Interpreted<M1, M2, ObservableCollection<?>, ObservableCollection<?>, ExElement> {
			private ModelInstanceType<M1, M2> theSourceType;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<T>> theTransform;

			Interpreted(MultiEntrySet<M1> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public MultiEntrySet<M1> getDefinition() {
				return (MultiEntrySet<M1>) super.getDefinition();
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getTransform() {
				return theTransform;
			}

			public ModelInstanceType<M1, M2> getSourceType() {
				return theSourceType;
			}

			@Override
			public void update(ModelInstanceType<M1, M2> sourceType) throws ExpressoInterpretationException {
				theSourceType = sourceType;
				super.update();
				theTransform = interpret(getDefinition().getTransform(), ModelTypes.Value.anyAs());
			}

			@Override
			public ModelInstanceType<ObservableCollection<?>, ? extends ObservableCollection<?>> getTargetType() {
				return getDefinition().getTargetModelType().forTypes(theTransform.getType().getType(0));
			}

			@Override
			public BetterList<InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList.of(theTransform);
			}

			@Override
			public Instantiator<K, V, M2, T> instantiate() throws ModelInstantiationException {
				return new Instantiator<>(this);
			}
		}

		static class Instantiator<K, V, M extends ObservableMultiMap<?, ?>, T>
		implements ObservableCollectionTransformations.CollectionFlowTransformInstantiator<M, ObservableCollection<?>, T> {
			private final ModelComponentId theKeyAs;
			private final ModelComponentId theValuesAs;
			private final ModelValueInstantiator<SettableValue<T>> theTransform;

			Instantiator(Interpreted<?, K, V, M, T> interpreted) throws ModelInstantiationException {
				theKeyAs = interpreted.getDefinition().getKeyAs();
				theValuesAs = interpreted.getDefinition().getValuesAs();
				theTransform = interpreted.getTransform().instantiate();
			}

			@Override
			public void instantiate() throws ModelInstantiationException {
				theTransform.instantiate();
			}

			@Override
			public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				SettableValue<T> sourceT = theTransform.get(sourceModels);
				return theTransform.forModelCopy(sourceT, sourceModels, newModels) == sourceT;
			}

			@Override
			public CollectionDataFlow<?, ?, T> transformToFlow(M source, ModelSetInstance models) throws ModelInstantiationException {
				SettableValue<K> key = SettableValue.create();
				SettableValue<ObservableCollection<V>> valuesV = SettableValue.create();
				ObservableCollection<V> valuesC = ObservableCollection.flattenValue(valuesV);
				ExFlexibleElementModelAddOn.satisfyElementValue(theKeyAs, models, key);
				ExFlexibleElementModelAddOn.satisfyElementValue(theValuesAs, models, valuesC);
				SettableValue<T> transform = theTransform.get(models);
				ObservableSet<? extends MultiEntryHandle<K, V>> entrySet = ((ObservableMultiMap<K, V>) source).entrySet();
				Function<MultiEntryHandle<K, V>, T> entryTransform = entry -> {
					key.set(entry.getKey());
					valuesV.set((ObservableCollection<V>) entry.getValues());
					return transform.get();
				};
				return entrySet.flow()//
					.map(entryTransform);
			}
		}
	}
}
