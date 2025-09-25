package org.observe.expresso.qonfig;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.observe.Equivalence;
import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.Observer;
import org.observe.SettableValue;
import org.observe.Subscription;
import org.observe.Transformation;
import org.observe.assoc.ObservableMultiMap;
import org.observe.assoc.ObservableMultiMap.MultiMapFlow;
import org.observe.collect.FlatMapOptions;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollection.DistinctDataFlow;
import org.observe.collect.ObservableCollection.SortedDataFlow;
import org.observe.collect.ObservableCollectionImpl;
import org.observe.collect.ObservableSet;
import org.observe.collect.ObservableSortedCollection;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelType.ModelInstanceType.SingleTyped;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.ObservableModelSet.ModelInstantiator;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.VariableType;
import org.observe.expresso.qonfig.ExpressoTransformations.AbstractCompiledTransformation;
import org.observe.expresso.qonfig.ExpressoTransformations.CollectionTransform;
import org.observe.expresso.qonfig.ExpressoTransformations.Operation;
import org.observe.expresso.qonfig.ExpressoTransformations.TransformInstantiator;
import org.observe.expresso.qonfig.ExpressoTransformations.TypePreservingTransform;
import org.observe.util.TypeTokens;
import org.qommons.Causable;
import org.qommons.Identifiable;
import org.qommons.LambdaUtils;
import org.qommons.QommonsUtils;
import org.qommons.collect.BetterList;
import org.qommons.config.QonfigElement.QonfigValue;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreterCore;
import org.qommons.config.QonfigValueType;
import org.qommons.ex.CheckedExceptionWrapper;
import org.qommons.io.LocatedFilePosition;

import com.google.common.reflect.TypeToken;

/** Transformations for {@link ModelTypes#Collection Collection} model values */
public class ObservableCollectionTransformations {
	/**
	 * A transformer capable of transforming an observable structure into an {@link ObservableCollection}. This type contains added
	 * capabilities so that when multiple flow operations are stacked, the intermediate structures don't need to be instantiated.
	 *
	 * @param <M1> The type of the source observable structure
	 * @param <M2> The type of the target collection
	 * @param <T> The element type of the target collection
	 */
	public interface CollectionFlowTransformInstantiator<M1, M2 extends ObservableCollection<?>, T> extends Operation.Instantiator<M1, M2> {
		/**
		 * Transforms a source observable structure into a transformed flow
		 *
		 * @param source The source observable structure
		 * @param models The models to do the transformation
		 * @return The transformed flow
		 * @throws ModelInstantiationException If the transformation fails
		 */
		CollectionDataFlow<?, ?, T> transformToFlow(M1 source, ModelSetInstance models) throws ModelInstantiationException;

		@Override
		default M2 transform(M1 source, ModelSetInstance models) throws ModelInstantiationException {
			ObservableCollection.CollectionDataFlow<?, ?, T> flow = transformToFlow(source, models);
			return (M2) flow.collect();
		}
	}

	/**
	 * A transformer capable for transforming an observable structure into another via a {CollectionDataFlow collection flow}. This type
	 * contains added capabilities so that when multiple flow operations are stacked, the intermediate structures don't need to be
	 * instantiated.
	 *
	 * @param <M1> The type of the source collection
	 * @param <M2> The type of the target observable structure
	 * @param <S> The element type of the source collection
	 */
	public interface CollectionFlowSourcedTransformInstantiator<M1, M2, S> extends Operation.Instantiator<M1, M2> {
		/**
		 * Transforms a source collection flow into a transformed observable structure
		 *
		 * @param flow The flow to transform
		 * @param models The models to do the transformation
		 * @return The transformed observable structure
		 * @throws ModelInstantiationException If the transformation fails
		 */
		M2 transformFromFlow(CollectionDataFlow<?, ?, S> flow, ModelSetInstance models) throws ModelInstantiationException;

		@Override
		default <S0> TransformInstantiator<S0, M2> after(TransformInstantiator<S0, ? extends M1> before) {
			if (before instanceof CollectionFlowToFlowTransformInstantiator) {
				CollectionFlowToFlowTransformInstantiator<S0, ? extends M1, Object, S> flowBefore = (CollectionFlowToFlowTransformInstantiator<S0, ? extends M1, Object, S>) before;
				CollectionFlowSourcedTransformInstantiator<M1, M2, S> next = this;
				return new CollectionFlowSourcedTransformInstantiator<S0, M2, Object>() {
					@Override
					public M2 transformFromFlow(CollectionDataFlow<?, ?, Object> source, ModelSetInstance models)
						throws ModelInstantiationException {
						CollectionDataFlow<?, ?, S> sourceFlow = flowBefore.transformFlow(source, models);
						return next.transformFromFlow(sourceFlow, models);
					}

					@Override
					public M2 transform(S0 source, ModelSetInstance models) throws ModelInstantiationException {
						CollectionDataFlow<?, ?, S> sourceFlow = flowBefore.transformToFlow(source, models);
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
			} else if (before instanceof CollectionFlowTransformInstantiator) {
				CollectionFlowTransformInstantiator<S0, ? extends M1, S> flowBefore = (CollectionFlowTransformInstantiator<S0, ? extends M1, S>) before;
				CollectionFlowSourcedTransformInstantiator<M1, M2, S> next = this;
				return new Operation.Instantiator<S0, M2>() {
					@Override
					public M2 transform(S0 source, ModelSetInstance models) throws ModelInstantiationException {
						CollectionDataFlow<?, ?, S> sourceFlow = flowBefore.transformToFlow(source, models);
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
			} else
				return Operation.Instantiator.super.after(before);
		}
	}

	/**
	 * A transformer capable of transforming an observable structure into an {@link ObservableCollection} via a {@link CollectionDataFlow
	 * collection flow}. This type contains added capabilities so that when multiple flow operations are stacked, the intermediate
	 * structures don't need to be instantiated.
	 *
	 * @param <M1> The type of the source observable structure
	 * @param <M2> The type of the target collection
	 * @param <S> The element type of the source collection
	 * @param <T> The element type of the target collection
	 */
	public interface CollectionFlowToFlowTransformInstantiator<M1, M2 extends ObservableCollection<?>, S, T>
	extends CollectionFlowTransformInstantiator<M1, M2, T>, CollectionFlowSourcedTransformInstantiator<M1, M2, S> {
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

		@Override
		default M2 transformFromFlow(CollectionDataFlow<?, ?, S> flow, ModelSetInstance models) throws ModelInstantiationException {
			return (M2) transformFlow(flow, models)//
				.collect();
		}

		@Override
		default <S0> TransformInstantiator<S0, M2> after(TransformInstantiator<S0, ? extends M1> before) {
			if (before instanceof CollectionFlowToFlowTransformInstantiator) {
				CollectionFlowToFlowTransformInstantiator<S0, ? extends M1, Object, S> flowBefore = (CollectionFlowToFlowTransformInstantiator<S0, ? extends M1, Object, S>) before;
				CollectionFlowToFlowTransformInstantiator<M1, M2, S, T> next = this;
				return new CollectionFlowToFlowTransformInstantiator<S0, M2, Object, T>() {
					@Override
					public CollectionDataFlow<?, ?, T> transformFlow(CollectionDataFlow<?, ?, Object> source, ModelSetInstance models)
						throws ModelInstantiationException {
						CollectionDataFlow<?, ?, S> sourceFlow = flowBefore.transformFlow(source, models);
						return next.transformFlow(sourceFlow, models);
					}

					@Override
					public CollectionDataFlow<?, ?, T> transformToFlow(S0 source, ModelSetInstance models)
						throws ModelInstantiationException {
						CollectionDataFlow<?, ?, S> sourceFlow = flowBefore.transformToFlow(source, models);
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
			} else if (before instanceof CollectionFlowTransformInstantiator) {
				CollectionFlowTransformInstantiator<S0, ? extends M1, S> flowBefore = (CollectionFlowTransformInstantiator<S0, ? extends M1, S>) before;
				CollectionFlowToFlowTransformInstantiator<M1, M2, S, T> next = this;
				return new CollectionFlowTransformInstantiator<S0, M2, T>() {
					@Override
					public CollectionDataFlow<?, ?, T> transformToFlow(S0 source, ModelSetInstance models)
						throws ModelInstantiationException {
						CollectionDataFlow<?, ?, S> sourceFlow = flowBefore.transformToFlow(source, models);
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
			} else
				return CollectionFlowSourcedTransformInstantiator.super.after(before);
		}
	}

	private ObservableCollectionTransformations() {
	}

	/**
	 * Configures an interpreter with collection transformation capabilities
	 *
	 * @param interpreter The interpretation builder to configure
	 */
	public static void configureTransformation(QonfigInterpreterCore.Builder interpreter) {
		interpreter.createWith(DisableCollectionTransform.DISABLE, CollectionTransform.class,
			ExElement.creator(DisableCollectionTransform::new));
		interpreter.createWith(MapCollectionTransform.MAP_TO, CollectionTransform.class, ExElement.creator(MapCollectionTransform::new));
		interpreter.createWith(FilterCollectionTransform.FILTER, CollectionTransform.class,
			ExElement.creator(FilterCollectionTransform::new));
		interpreter.createWith(TypeFilteredCollectionTransform.FILTER_BY_TYPE, CollectionTransform.class,
			ExElement.creator(TypeFilteredCollectionTransform::new));
		interpreter.createWith(ReverseCollectionTransform.REVERSE, CollectionTransform.class,
			ExElement.creator(ReverseCollectionTransform::new));
		interpreter.createWith(RefreshCollectionTransform.REFRESH, CollectionTransform.class,
			ExElement.creator(RefreshCollectionTransform::new));
		interpreter.createWith(RefreshEachCollectionTransform.REFRESH_EACH, CollectionTransform.class,
			ExElement.creator(RefreshEachCollectionTransform::new));
		interpreter.createWith(DistinctCollectionTransform.DISTINCT, CollectionTransform.class,
			ExElement.creator(DistinctCollectionTransform::new));
		interpreter.createWith(ExSort.SORT, CollectionTransform.class, ExElement.creator(SortedCollectionTransform::new));
		interpreter.createWith(UnmodifiableCollectionTransform.UNMODIFIABLE, CollectionTransform.class,
			ExElement.creator(UnmodifiableCollectionTransform::new));
		interpreter.createWith(FilterModCollectionTransform.FILTER_MOD, CollectionTransform.class,
			ExElement.creator(FilterModCollectionTransform::new));
		interpreter.createWith(MapEquivalentCollectionTransform.MAP_EQUIVALENT, CollectionTransform.class,
			ExElement.creator(MapEquivalentCollectionTransform::new));
		interpreter.createWith(FlattenCollectionTransform.FLATTEN, CollectionTransform.class,
			ExElement.creator(FlattenCollectionTransform::new));
		interpreter.createWith(CrossCollectionTransform.CROSS, CollectionTransform.class, ExElement.creator(CrossCollectionTransform::new));
		interpreter.createWith(WhereContainedCollectionTransform.WHERE_CONTAINED, CollectionTransform.class,
			ExElement.creator(WhereContainedCollectionTransform::new));
		interpreter.createWith(SizeCollectionTransform.SIZE, CollectionTransform.class, ExElement.creator(SizeCollectionTransform::new));
		interpreter.createWith(ReducedCollectionTransform.REDUCE, CollectionTransform.class,
			ExElement.creator(ReducedCollectionTransform::new));
		interpreter.createWith(TerminalCollectionTransform.TERMINAL, CollectionTransform.class,
			ExElement.creator(TerminalCollectionTransform::new));
		interpreter.createWith(GroupByTransform.GROUP_BY, CollectionTransform.class, ExElement.creator(GroupByTransform::new));
		interpreter.createWith(CollectCollectionTransform.COLLECT, CollectionTransform.class,
			ExElement.creator(CollectCollectionTransform::new));

		// TODO Probably should support value-set transformations here, just grabbing the values and returning a collection
		// This can always be overridden later
	}

	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = DisableCollectionTransform.DISABLE,
		interpretation = DisableCollectionTransform.Interpreted.class)
	static class DisableCollectionTransform<C extends ObservableCollection<?>> extends TypePreservingTransform<C>
	implements CollectionTransform<C, C, ExElement> {
		static final String DISABLE = "disable";

		private CompiledExpression theWith;

		DisableCollectionTransform(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@QonfigAttributeGetter("with")
		public CompiledExpression getWith() {
			return theWith;
		}

		@Override
		public void update(ExpressoQIS session, ModelType<C> sourceModelType) throws QonfigInterpretationException {
			super.update(session, sourceModelType);
			theWith = getAttributeExpression("with", session);
		}

		@Override
		protected Interpreted<?, C, ?> tppInterpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<T, C extends ObservableCollection<?>, CV extends C> extends TypePreservingTransform.Interpreted<C, CV> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theWith;

			Interpreted(DisableCollectionTransform<C> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public DisableCollectionTransform<C> getDefinition() {
				return (DisableCollectionTransform<C>) super.getDefinition();
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getWith() {
				return theWith;
			}

			@Override
			public void update(ModelInstanceType<C, CV> sourceType) throws ExpressoInterpretationException {
				super.update(sourceType);
				theWith = ExpressoTransformations.parseFilter(getDefinition().getWith(), this, true);
			}

			@Override
			public BetterList<InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList.of(theWith);
			}

			@Override
			public Operation.Instantiator<CV, CV> instantiate() throws ModelInstantiationException {
				return new Instantiator<>(theWith.instantiate());
			}

			@Override
			public String toString() {
				return "disable(" + theWith + ")";
			}
		}

		static class Instantiator<T, CV extends ObservableCollection<?>>
		implements CollectionFlowToFlowTransformInstantiator<CV, CV, T, T> {
			private final ModelValueInstantiator<SettableValue<String>> theWith;

			Instantiator(ModelValueInstantiator<SettableValue<String>> with) {
				theWith = with;
			}

			@Override
			public void instantiate() throws ModelInstantiationException {
				theWith.instantiate();
			}

			@Override
			public CollectionDataFlow<?, ?, T> transformFlow(CollectionDataFlow<?, ?, T> source, ModelSetInstance models)
				throws ModelInstantiationException {
				SettableValue<String> with = theWith.get(models);
				return source.disableWith(with, true);
			}

			@Override
			public CollectionDataFlow<?, ?, T> transformToFlow(CV source, ModelSetInstance models) throws ModelInstantiationException {
				return transformFlow((CollectionDataFlow<?, ?, T>) source.flow(), models);
			}

			@Override
			public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				SettableValue<String> refresh = theWith.get(sourceModels);
				return refresh != theWith.forModelCopy(refresh, sourceModels, newModels);
			}
		}
	}

	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = MapCollectionTransform.MAP_TO,
		interpretation = MapCollectionTransform.Interpreted.class)
	static class MapCollectionTransform<C1 extends ObservableCollection<?>, C2 extends ObservableCollection<?>> extends
	ExpressoTransformations.AbstractCompiledTransformation<C1, C2, ExElement> implements CollectionTransform<C1, C2, ExElement> {
		static final String MAP_TO = "map-to";

		private ModelType<C1> theSourceType;
		private ModelType<C2> theTargetType;

		MapCollectionTransform(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		public ModelType<C1> getSourceType() {
			return theSourceType;
		}

		@Override
		public ModelType<? extends C2> getTargetModelType() {
			return theTargetType;
		}

		@Override
		public void update(ExpressoQIS session, ModelType<C1> sourceModelType) throws QonfigInterpretationException {
			theSourceType = sourceModelType;
			super.update(session, sourceModelType);
			if (sourceModelType == ModelTypes.Collection)
				theTargetType = (ModelType<C2>) sourceModelType;
			else if (sourceModelType == ModelTypes.Set || sourceModelType == ModelTypes.SortedCollection
				|| sourceModelType == ModelTypes.SortedSet) {
				if (isReversible())
					theTargetType = (ModelType<C2>) sourceModelType;
				else
					theTargetType = (ModelType<C2>) ModelTypes.Collection;
			} else
				throw new QonfigInterpretationException("Unrecognized source collection type: " + sourceModelType,
					reporting().getFileLocation().getPosition(0), 0);
		}

		@Override
		public ExpressoTransformations.CompiledTransformation.Interpreted<C1, ?, ?, ?, C2, ?, ? extends ExElement> interpret(
			ExElement.Interpreted<?> parent) throws ExpressoInterpretationException {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<C1 extends ObservableCollection<?>, S, T, CV1 extends C1, C2 extends ObservableCollection<?>, CV2 extends C2>
		extends ExpressoTransformations.AbstractCompiledTransformation.Interpreted<C1, S, T, CV1, C2, CV2, ExElement> {
			private ModelInstanceType<C2, CV2> theTargetModelType;

			Interpreted(MapCollectionTransform<C1, C2> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public MapCollectionTransform<C1, C2> getDefinition() {
				return (MapCollectionTransform<C1, C2>) super.getDefinition();
			}

			@Override
			public void update(ModelInstanceType<C1, CV1> sourceType) throws ExpressoInterpretationException {
				super.update(sourceType);
				theTargetModelType = (ModelInstanceType<C2, CV2>) getDefinition().getTargetModelType().forTypes(getTargetValueType());
			}

			@Override
			public ModelInstanceType<? extends C2, ? extends CV2> getTargetType() {
				return theTargetModelType;
			}

			@Override
			public ExpressoTransformations.CompiledTransformation.Instantiator<S, T, CV1, CV2> instantiate()
				throws ModelInstantiationException {
				return new Instantiator<>(instantiateLocalModels(), getMap().instantiate(),
					TypeTokens.get().getDefaultValue(getSourceType()), //
					QommonsUtils.filterMapE(getCombinedValues(), null, cv -> cv.instantiate()),
					getReverse() == null ? null : getReverse().instantiate(), getDefinition().getSourceName(),
						getDefinition().getPreviousResultAs(), getDefinition().isCached(), getDefinition().isReEvalOnUpdate(),
						getDefinition().isFireIfUnchanged(), getDefinition().isNullToNull(), getDefinition().isManyToOne(),
						getDefinition().isOneToMany(), getEquivalence() == null ? null : getEquivalence().instantiate(), isTesting());
			}
		}

		static class Instantiator<S, T, CV1 extends ObservableCollection<?>, CV2 extends ObservableCollection<?>>
		extends ExpressoTransformations.AbstractCompiledTransformation.Instantiator<S, T, CV1, CV2>
		implements CollectionFlowToFlowTransformInstantiator<CV1, CV2, S, T> {

			Instantiator(DocumentMap<ModelInstantiator> localModel, ModelValueInstantiator<SettableValue<T>> map, S defaultSource,
				List<ExpressoTransformations.CombineWith.Instantiator<?>> combinedValues,
				ExpressoTransformations.CompiledMapReverse.Instantiator<S, T> reverse, ModelComponentId sourceVariable,
				ModelComponentId previousResultVariable, boolean cached, boolean reEvalOnUpdate, boolean fireIfUnchanged,
				boolean nullToNull, boolean manyToOne, boolean oneToMany,
				ModelValueInstantiator<SettableValue<Equivalence<? super T>>> equivalence, boolean testing) {
				super(localModel, map, defaultSource, combinedValues, reverse, sourceVariable, previousResultVariable, cached,
					reEvalOnUpdate, fireIfUnchanged, nullToNull, manyToOne, oneToMany, equivalence, testing);
			}

			@Override
			public CV2 transform(CV1 source, ModelSetInstance models) throws ModelInstantiationException {
				CollectionDataFlow<?, ?, T> flow = transformToFlow(source, models);
				if (flow.prefersPassive())
					return (CV2) flow.collectPassive();
				else
					return (CV2) flow.collectActive(models.getUntil());
			}

			@Override
			public CollectionDataFlow<?, ?, T> transformFlow(CollectionDataFlow<?, ?, S> source, ModelSetInstance models)
				throws ModelInstantiationException {
				try {
					return source.transform(tx -> {
						try {
							return transform(tx, models);
						} catch (ModelInstantiationException e) {
							throw new CheckedExceptionWrapper(e);
						}
					});
				} catch (CheckedExceptionWrapper e) {
					throw CheckedExceptionWrapper.getThrowable(e, ModelInstantiationException.class);
				}
			}

			@Override
			public CollectionDataFlow<?, ?, T> transformToFlow(CV1 source, ModelSetInstance models) throws ModelInstantiationException {
				return transformFlow((CollectionDataFlow<?, ?, S>) source.flow(), models);
			}
		}
	}

	@ExMultiElementTraceable({ //
		@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
			qonfigType = FilterCollectionTransform.FILTER,
			interpretation = FilterCollectionTransform.Interpreted.class), //
		@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = "complex-operation",
		interpretation = FilterCollectionTransform.Interpreted.class)//
	})
	static class FilterCollectionTransform<C extends ObservableCollection<?>> extends TypePreservingTransform<C>
	implements CollectionTransform<C, C, ExElement> {
		public static final String FILTER = "filter";

		private ModelComponentId theSourceVariable;
		private CompiledExpression theTest;

		FilterCollectionTransform(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@QonfigAttributeGetter(asType = "complex-operation", value = "source-as")
		public ModelComponentId getSourceVariable() {
			return theSourceVariable;
		}

		@QonfigAttributeGetter(asType = FILTER, value = "test")
		public CompiledExpression getTest() {
			return theTest;
		}

		@Override
		public void update(ExpressoQIS session, ModelType<C> sourceModelType) throws QonfigInterpretationException {
			super.update(session, sourceModelType);
			String sourceAs = session.getAttributeText("source-as");
			ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
			theSourceVariable = elModels.getElementValueModelId(sourceAs);
			theTest = getAttributeExpression("test", session);
			elModels.<Interpreted<?, ?, ?>, SettableValue<?>> satisfyElementSingleValueType(theSourceVariable, ModelTypes.Value,
				Interpreted::getSourceType);
		}

		@Override
		protected Interpreted<?, C, ?> tppInterpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<T, C extends ObservableCollection<?>, CV extends C> extends TypePreservingTransform.Interpreted<C, CV> {
			private TypeToken<T> theSourceType;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theTest;

			Interpreted(FilterCollectionTransform<C> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public FilterCollectionTransform<C> getDefinition() {
				return (FilterCollectionTransform<C>) super.getDefinition();
			}

			public TypeToken<T> getSourceType() {
				return theSourceType;
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getTest() {
				return theTest;
			}

			@Override
			public void update(ModelInstanceType<C, CV> sourceType) throws ExpressoInterpretationException {
				theSourceType = (TypeToken<T>) sourceType.getType(0);
				super.update(sourceType);
				theTest = ExpressoTransformations.parseFilter(getDefinition().getTest(), this, true);
			}

			@Override
			public BetterList<InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList.of(theTest);
			}

			@Override
			public Operation.Instantiator<CV, CV> instantiate() throws ModelInstantiationException {
				return new Instantiator<>(this);
			}
		}

		static class Instantiator<T, CV extends ObservableCollection<?>>
		implements CollectionFlowToFlowTransformInstantiator<CV, CV, T, T> {
			private final DocumentMap<ModelInstantiator> theModels;
			private final ModelComponentId theSourceVariable;
			private final ModelValueInstantiator<SettableValue<String>> theTest;

			Instantiator(Interpreted<T, ?, CV> interpreted) throws ModelInstantiationException {
				theModels = interpreted.instantiateLocalModels();
				theSourceVariable = interpreted.getDefinition().getSourceVariable();
				theTest = interpreted.getTest().instantiate();
			}

			@Override
			public void instantiate() throws ModelInstantiationException {
				theModels.forEach(ModelInstantiator::instantiate);
				theTest.instantiate();
			}

			@Override
			public CollectionDataFlow<?, ?, T> transformFlow(CollectionDataFlow<?, ?, T> source, ModelSetInstance models)
				throws ModelInstantiationException {
				models = theModels.operate(models, (m, mi) -> mi.wrap(m));
				SettableValue<T> sourceV = SettableValue.<T> build().build();
				ExFlexibleElementModelAddOn.satisfyElementValue(theSourceVariable, models, sourceV);
				SettableValue<T> flatSourceV = (SettableValue<T>) models.get(theSourceVariable);
				SettableValue<String> testV = theTest.get(models);
				String print = theTest.toString();
				Function<T, String> filter = LambdaUtils.printableFn(v -> {
					sourceV.set(v, null);
					return testV.get();
				}, () -> print);
				Observable.CoreChangeSources refresh = testV.noInitChanges().getChangeSources()
					.excluding(flatSourceV.noInitChanges().getChangeSources());
				if (!refresh.isEmpty())
					source = source.refresh(refresh);
				return source.filter(filter);
			}

			@Override
			public CollectionDataFlow<?, ?, T> transformToFlow(CV source, ModelSetInstance models) throws ModelInstantiationException {
				return transformFlow((CollectionDataFlow<?, ?, T>) source.flow(), models);
			}

			@Override
			public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				sourceModels = theModels.operate(sourceModels, (m, mi) -> mi.wrap(m));
				newModels = theModels.operate(newModels, (m, mi) -> mi.wrap(m));
				SettableValue<String> test = theTest.get(sourceModels);
				return test != theTest.forModelCopy(test, sourceModels, newModels);
			}
		}
	}

	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = TypeFilteredCollectionTransform.FILTER_BY_TYPE,
		interpretation = TypeFilteredCollectionTransform.Interpreted.class)
	static class TypeFilteredCollectionTransform<C extends ObservableCollection<?>> extends TypePreservingTransform<C>
	implements CollectionTransform<C, C, ExElement> {
		static final String FILTER_BY_TYPE = "filter-by-type";

		TypeFilteredCollectionTransform(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@Override
		public void update(ExpressoQIS session, ModelType<C> sourceModelType) throws QonfigInterpretationException {
			super.update(session, sourceModelType);
			VariableType type = getAddOn(ExTyped.Def.class).getValueType();
			if (type instanceof VariableType.Parameterized) {
				QonfigValue typeQV = session.attributes().get("type").get();
				throw new QonfigInterpretationException("Parameterized types are not permitted for filter type",
					new LocatedFilePosition(typeQV.fileLocation, typeQV.position.getPosition(0)), typeQV.position.length());
			}
		}

		@Override
		protected Interpreted<?, C, ?> tppInterpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<T, C extends ObservableCollection<?>, CV extends C> extends TypePreservingTransform.Interpreted<C, CV> {
			Interpreted(TypeFilteredCollectionTransform<C> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public TypeFilteredCollectionTransform<C> getDefinition() {
				return (TypeFilteredCollectionTransform<C>) super.getDefinition();
			}

			@Override
			public BetterList<InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList.empty();
			}

			@Override
			public Operation.Instantiator<CV, CV> instantiate() {
				return new Instantiator<>(TypeTokens.getRawType(getAddOn(ExTyped.Interpreted.class).getValueType()));
			}
		}

		static class Instantiator<T, CV extends ObservableCollection<?>>
		implements CollectionFlowToFlowTransformInstantiator<CV, CV, T, T> {
			private final Class<?> theType;

			Instantiator(Class<?> type) {
				theType = type;
			}

			@Override
			public void instantiate() {
			}

			@Override
			public CollectionDataFlow<?, ?, T> transformFlow(CollectionDataFlow<?, ?, T> source, ModelSetInstance models)
				throws ModelInstantiationException {
				Class<?> type = theType;
				String notInstance = "Not an instance of " + type.getName();
				Function<T, String> filter = LambdaUtils.printableFn(v -> {
					if (type.isInstance(v))
						return null;
					return notInstance;
				}, () -> "instanceof " + type.getName());
				return source.filter(filter);
			}

			@Override
			public CollectionDataFlow<?, ?, T> transformToFlow(CV source, ModelSetInstance models) throws ModelInstantiationException {
				return transformFlow((CollectionDataFlow<?, ?, T>) source.flow(), models);
			}

			@Override
			public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				return false;
			}
		}

		static class TypeFilteredObservable<T> extends Observable.WrappingObservable<T, T> {
			private final Class<?> theFilterType;

			TypeFilteredObservable(Observable<T> wrapped, Class<?> filterType) {
				super(wrapped);
				theFilterType = filterType;
			}

			@Override
			protected Observable<T> getWrapped() {
				return super.getWrapped();
			}

			Class<?> getFilterType() {
				return theFilterType;
			}

			@Override
			protected Object createIdentity() {
				return Identifiable.wrap(getWrapped().getIdentity(), "filter", theFilterType);
			}

			@Override
			public Subscription subscribe(Observer<? super T> observer) {
				return getWrapped().subscribe(new Observer<T>() {
					@Override
					public void onNext(T value) {
						if (theFilterType.isInstance(value))
							observer.onNext(value);
					}

					@Override
					public void onCompleted(Supplier<Causable> cause) {
						observer.onCompleted(cause);
					}
				});
			}
		}
	}

	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = ReverseCollectionTransform.REVERSE,
		interpretation = ReverseCollectionTransform.Interpreted.class)
	static class ReverseCollectionTransform<C extends ObservableCollection<?>> extends TypePreservingTransform<C>
	implements CollectionTransform<C, C, ExElement> {
		static final String REVERSE = "reverse";

		ReverseCollectionTransform(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@Override
		protected Interpreted<?, C, ?> tppInterpret(org.observe.expresso.qonfig.ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<T, C extends ObservableCollection<?>, CV extends C> extends TypePreservingTransform.Interpreted<C, CV> {
			Interpreted(ReverseCollectionTransform<C> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public BetterList<InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList.empty();
			}

			@Override
			public Instantiator<CV, CV> instantiate() {
				return new Instantiator<>();
			}

			@Override
			public String toString() {
				return "reverse()";
			}
		}

		static class Instantiator<T, CV extends ObservableCollection<?>>
		implements CollectionFlowToFlowTransformInstantiator<CV, CV, T, T> {
			@Override
			public CV transform(CV source, ModelSetInstance models) throws ModelInstantiationException {
				return (CV) source.reverse();
			}

			@Override
			public void instantiate() {
			}

			@Override
			public CollectionDataFlow<?, ?, T> transformFlow(CollectionDataFlow<?, ?, T> source, ModelSetInstance models)
				throws ModelInstantiationException {
				return source.reverse();
			}

			@Override
			public CollectionDataFlow<?, ?, T> transformToFlow(CV source, ModelSetInstance models) throws ModelInstantiationException {
				return ((CollectionDataFlow<?, ?, T>) source.flow()).reverse();
			}

			@Override
			public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				return false;
			}
		}
	}

	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = RefreshCollectionTransform.REFRESH,
		interpretation = RefreshCollectionTransform.Interpreted.class)
	static class RefreshCollectionTransform<C extends ObservableCollection<?>> extends TypePreservingTransform<C>
	implements CollectionTransform<C, C, ExElement> {
		static final String REFRESH = "refresh";

		private CompiledExpression theRefresh;

		RefreshCollectionTransform(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@QonfigAttributeGetter("on")
		public CompiledExpression getRefresh() {
			return theRefresh;
		}

		@Override
		public void update(ExpressoQIS session, ModelType<C> sourceModelType) throws QonfigInterpretationException {
			super.update(session, sourceModelType);
			theRefresh = getAttributeExpression("on", session);
		}

		@Override
		protected Interpreted<?, C, ?> tppInterpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<T, C extends ObservableCollection<?>, CV extends C> extends TypePreservingTransform.Interpreted<C, CV> {
			private InterpretedValueSynth<Observable<?>, Observable<?>> theRefresh;

			Interpreted(RefreshCollectionTransform<C> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public RefreshCollectionTransform<C> getDefinition() {
				return (RefreshCollectionTransform<C>) super.getDefinition();
			}

			public InterpretedValueSynth<Observable<?>, Observable<?>> getRefresh() {
				return theRefresh;
			}

			@Override
			public void update(ModelInstanceType<C, CV> sourceType) throws ExpressoInterpretationException {
				super.update(sourceType);
				theRefresh = interpret(getDefinition().getRefresh(), ModelTypes.Event.any());
			}

			@Override
			public BetterList<InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList.of(theRefresh);
			}

			@Override
			public Operation.Instantiator<CV, CV> instantiate() throws ModelInstantiationException {
				return new Instantiator<>(theRefresh.instantiate());
			}

			@Override
			public String toString() {
				return "refresh(" + theRefresh + ")";
			}
		}

		static class Instantiator<T, CV extends ObservableCollection<?>>
		implements CollectionFlowToFlowTransformInstantiator<CV, CV, T, T> {
			private final ModelValueInstantiator<Observable<?>> theRefresh;

			Instantiator(ModelValueInstantiator<Observable<?>> refresh) {
				theRefresh = refresh;
			}

			@Override
			public void instantiate() throws ModelInstantiationException {
				theRefresh.instantiate();
			}

			@Override
			public CollectionDataFlow<?, ?, T> transformFlow(CollectionDataFlow<?, ?, T> source, ModelSetInstance models)
				throws ModelInstantiationException {
				Observable<?> refresh = theRefresh.get(models);
				return source.refresh(refresh);
			}

			@Override
			public CollectionDataFlow<?, ?, T> transformToFlow(CV source, ModelSetInstance models) throws ModelInstantiationException {
				return transformFlow((CollectionDataFlow<?, ?, T>) source.flow(), models);
			}

			@Override
			public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				Observable<?> refresh = theRefresh.get(sourceModels);
				return refresh != theRefresh.forModelCopy(refresh, sourceModels, newModels);
			}
		}
	}

	@ExMultiElementTraceable({
		@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
			qonfigType = RefreshEachCollectionTransform.REFRESH_EACH,
			interpretation = RefreshEachCollectionTransform.Interpreted.class),
		@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = "complex-operation",
		interpretation = RefreshEachCollectionTransform.Interpreted.class) })
	static class RefreshEachCollectionTransform<C extends ObservableCollection<?>> extends TypePreservingTransform<C>
	implements CollectionTransform<C, C, ExElement> {
		static final String REFRESH_EACH = "refresh-each";
		private ModelComponentId theSourceName;
		private CompiledExpression theRefresh;

		RefreshEachCollectionTransform(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@QonfigAttributeGetter(asType = "complex-operation", value = "source-as")
		public ModelComponentId getSourceVariable() {
			return theSourceName;
		}

		@QonfigAttributeGetter(asType = "refresh-each", value = "on")
		public CompiledExpression getRefresh() {
			return theRefresh;
		}

		@Override
		public void update(ExpressoQIS session, ModelType<C> sourceModelType) throws QonfigInterpretationException {
			super.update(session, sourceModelType);
			String sourceAs = session.getAttributeText("source-as");
			ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
			theSourceName = elModels.getElementValueModelId(sourceAs);
			theRefresh = getAttributeExpression("on", session);
			elModels.<Interpreted<?, ?, ?>, SettableValue<?>> satisfyElementSingleValueType(theSourceName, ModelTypes.Value,
				Interpreted::getSourceType);
		}

		@Override
		protected Interpreted<?, C, ?> tppInterpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<T, C extends ObservableCollection<?>, CV extends C> extends TypePreservingTransform.Interpreted<C, CV> {
			private TypeToken<T> theSourceType;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Observable<?>>> theRefresh;

			Interpreted(RefreshEachCollectionTransform<C> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public RefreshEachCollectionTransform<C> getDefinition() {
				return (RefreshEachCollectionTransform<C>) super.getDefinition();
			}

			public TypeToken<T> getSourceType() {
				return theSourceType;
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<Observable<?>>> getRefresh() {
				return theRefresh;
			}

			@Override
			public void update(ModelInstanceType<C, CV> sourceType) throws ExpressoInterpretationException {
				theSourceType = (TypeToken<T>) sourceType.getType(0);
				super.update(sourceType);
				theRefresh = interpret(getDefinition().getRefresh(),
					ModelTypes.Value.forType(TypeTokens.get().keyFor(Observable.class).wildCard()));
			}

			@Override
			public BetterList<InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList.of(theRefresh);
			}

			@Override
			public Operation.Instantiator<CV, CV> instantiate() throws ModelInstantiationException {
				return new Instantiator<>(instantiateLocalModels(), getDefinition().getSourceVariable(), theRefresh.instantiate());
			}

			@Override
			public String toString() {
				return "refreshEach(" + theRefresh + ")";
			}
		}

		static class Instantiator<T, CV extends ObservableCollection<?>>
		implements CollectionFlowToFlowTransformInstantiator<CV, CV, T, T> {
			private final DocumentMap<ModelInstantiator> theLocalModel;
			private final ModelComponentId theSourceVariable;
			private final ModelValueInstantiator<SettableValue<Observable<?>>> theRefresh;

			Instantiator(DocumentMap<ModelInstantiator> localModel, ModelComponentId sourceVariable,
				ModelValueInstantiator<SettableValue<Observable<?>>> refresh) {
				theLocalModel = localModel;
				theSourceVariable = sourceVariable;
				theRefresh = refresh;
			}

			@Override
			public void instantiate() throws ModelInstantiationException {
				theLocalModel.forEach(ModelInstantiator::instantiate);
				theRefresh.instantiate();
			}

			@Override
			public CollectionDataFlow<?, ?, T> transformFlow(CollectionDataFlow<?, ?, T> source, ModelSetInstance models)
				throws ModelInstantiationException {
				models = theLocalModel.operate(models, (m, mi) -> mi.wrap(m));
				SettableValue<T> sourceV = SettableValue.<T> build().build();
				ExFlexibleElementModelAddOn.satisfyElementValue(theSourceVariable, models, sourceV);
				SettableValue<Observable<?>> refresh = theRefresh.get(models);
				String print = theRefresh.toString();
				Function<T, Observable<?>> refreshFn = LambdaUtils.printableFn(v -> {
					sourceV.set(v, null);
					return refresh.get();
				}, () -> print);
				return source.refreshEach(refreshFn);
			}

			@Override
			public CollectionDataFlow<?, ?, T> transformToFlow(CV source, ModelSetInstance models) throws ModelInstantiationException {
				return transformFlow((CollectionDataFlow<?, ?, T>) source.flow(), models);
			}

			@Override
			public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				sourceModels = theLocalModel.operate(sourceModels, (m, mi) -> mi.wrap(m));
				newModels = theLocalModel.operate(newModels, (m, mi) -> mi.wrap(m));
				SettableValue<Observable<?>> refresh = theRefresh.get(sourceModels);
				return refresh != theRefresh.forModelCopy(refresh, sourceModels, newModels);
			}
		}
	}

	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = DistinctCollectionTransform.DISTINCT,
		interpretation = DistinctCollectionTransform.Interpreted.class)
	static class DistinctCollectionTransform<C1 extends ObservableCollection<?>, C2 extends ObservableSet<?>>
	extends ExElement.Def.Abstract<ExElement> implements CollectionTransform<C1, C2, ExElement> {
		static final String DISTINCT = "distinct";

		private ModelType<C2> theTargetType;
		private boolean isUseFirst;
		private boolean isPreservingSourceOrder;
		private ExSort.ExRootSort theSort;

		DistinctCollectionTransform(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@QonfigAttributeGetter("use-first")
		public boolean isUseFirst() {
			return isUseFirst;
		}

		@QonfigAttributeGetter("preserve-source-order")
		public boolean isPreservingSourceOrder() {
			return isPreservingSourceOrder;
		}

		@QonfigChildGetter("sort")
		public ExSort.ExRootSort getSort() {
			return theSort;
		}

		@Override
		public ModelType<? extends C2> getTargetModelType() {
			return theTargetType;
		}

		@Override
		public void update(ExpressoQIS session, ModelType<C1> sourceModelType) throws QonfigInterpretationException {
			super.update(session);
			isUseFirst = session.getAttribute("use-first", boolean.class);
			isPreservingSourceOrder = session.getAttribute("preserve-source-order", boolean.class);
			theSort = syncChild(ExSort.ExRootSort.class, theSort, session, "sort");
			if (isPreservingSourceOrder && theSort != null)
				reporting().warn("'preserve-source-order' is not used when sorting is specified");
			if (theSort != null || sourceModelType == ModelTypes.SortedCollection || sourceModelType == ModelTypes.SortedSet)
				theTargetType = (ModelType<C2>) ModelTypes.SortedSet;
			else
				theTargetType = (ModelType<C2>) ModelTypes.Set;
		}

		@Override
		public Interpreted<?, C1, ?, C2, ?> interpret(ExElement.Interpreted<?> parent) throws ExpressoInterpretationException {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<T, C1 extends ObservableCollection<?>, CV1 extends C1, C2 extends ObservableSet<?>, CV2 extends C2>
		extends ExElement.Interpreted.Abstract<ExElement> implements Operation.Interpreted<C1, CV1, C2, CV2, ExElement> {
			private TypeToken<T> theValueType;
			private ExSort.ExRootSort.Interpreted<T> theSort;

			Interpreted(DistinctCollectionTransform<C1, C2> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public DistinctCollectionTransform<C1, C2> getDefinition() {
				return (DistinctCollectionTransform<C1, C2>) super.getDefinition();
			}

			public TypeToken<T> getValueType() {
				return theValueType;
			}

			public ExSort.ExRootSort.Interpreted<T> getSort() {
				return theSort;
			}

			@Override
			public void update(ModelInstanceType<C1, CV1> sourceType) throws ExpressoInterpretationException {
				theValueType = (TypeToken<T>) sourceType.getType(0);
				update();
				if (getDefinition().getSort() == null) {
					if (theSort != null)
						theSort.destroy();
					theSort = null;
				} else {
					if (theSort == null || theSort.getIdentity() != getDefinition().getSort().getIdentity()) {
						if (theSort != null)
							theSort.destroy();
						theSort = (ExSort.ExRootSort.Interpreted<T>) getDefinition().getSort().interpret(this);
					}
					theSort.update(theValueType);
				}
			}

			@Override
			public ModelInstanceType<? extends C2, ? extends CV2> getTargetType() {
				return (ModelInstanceType<? extends C2, ? extends CV2>) getDefinition().getTargetModelType().forTypes(theValueType);
			}

			@Override
			public BetterList<InterpretedValueSynth<?, ?>> getComponents() {
				return theSort == null ? BetterList.empty() : theSort.getComponents();
			}

			@Override
			public Operation.Instantiator<CV1, CV2> instantiate() throws ModelInstantiationException {
				return new Instantiator<>(theSort == null ? null : theSort.instantiateSort(), getDefinition().isUseFirst(),
					getDefinition().isPreservingSourceOrder());
			}
		}

		static class Instantiator<T, CV1 extends ObservableCollection<?>, CV2 extends ObservableSet<?>>
		implements CollectionFlowToFlowTransformInstantiator<CV1, CV2, T, T> {
			private final ModelValueInstantiator<Comparator<? super T>> theSort;
			private final boolean isUseFirst;
			private final boolean isPreservingSourceOrder;

			Instantiator(ModelValueInstantiator<Comparator<? super T>> sort, boolean useFirst, boolean preservingSourceOrder) {
				theSort = sort;
				isUseFirst = useFirst;
				isPreservingSourceOrder = preservingSourceOrder;
			}

			@Override
			public void instantiate() throws ModelInstantiationException {
				if (theSort != null)
					theSort.instantiate();
			}

			@Override
			public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				if (theSort != null && theSort.get(sourceModels) != theSort.get(newModels))
					return true;
				return false;
			}

			@Override
			public CV2 transform(CV1 source, ModelSetInstance models) throws ModelInstantiationException {
				return (CV2) transformToFlow(source, models).collect();
			}

			@Override
			public CollectionDataFlow<?, ?, T> transformFlow(CollectionDataFlow<?, ?, T> source, ModelSetInstance models)
				throws ModelInstantiationException {
				if (theSort != null) {
					Comparator<? super T> sort = theSort.get(models);
					return source.distinctSorted(sort, isUseFirst);
				} else {
					return source.distinct(uo -> uo//
						.preserveSourceOrder(isPreservingSourceOrder)//
						.useFirst(isUseFirst));
				}
			}

			@Override
			public CollectionDataFlow<?, ?, T> transformToFlow(CV1 source, ModelSetInstance models) throws ModelInstantiationException {
				return transformFlow((CollectionDataFlow<?, ?, T>) source.flow(), models);
			}
		}
	}

	/**
	 * Collection implementation for the &lt;sort> transform operation
	 *
	 * @param <C1> The model type of the source collection type
	 */
	public static class SortedCollectionTransform<C1 extends ObservableCollection<?>> extends ExSort.ExRootSort
	implements CollectionTransform<C1, ObservableSortedCollection<?>, ExElement> {
		/**
		 * @param parent The parent element of this transform operation
		 * @param qonfigType The Qonfig type of this element
		 */
		public SortedCollectionTransform(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@Override
		public ModelType<? extends ObservableSortedCollection<?>> getTargetModelType() {
			return ModelTypes.SortedCollection;
		}

		@Override
		public void update(ExpressoQIS session, ModelType<C1> sourceModelType) throws QonfigInterpretationException {
			super.update(session);
		}

		@Override
		public Interpreted<?, C1, ?, ?> interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<T, C1 extends ObservableCollection<?>, CV1 extends C1, CV2 extends ObservableSortedCollection<?>> extends
		ExSort.ExRootSort.Interpreted<T> implements Operation.Interpreted<C1, CV1, ObservableSortedCollection<?>, CV2, ExElement> {
			private ModelInstanceType.SingleTyped<ObservableSortedCollection<?>, T, CV2> theTargetType;

			Interpreted(SortedCollectionTransform<C1> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public SortedCollectionTransform<C1> getDefinition() {
				return (SortedCollectionTransform<C1>) super.getDefinition();
			}

			@Override
			public void update(ModelInstanceType<C1, CV1> sourceType) throws ExpressoInterpretationException {
				TypeToken<T> valueType = (TypeToken<T>) sourceType.getType(0);
				theTargetType = (SingleTyped<ObservableSortedCollection<?>, T, CV2>) ModelTypes.SortedCollection.forType(valueType);
				super.update(valueType);
			}

			@Override
			public ModelInstanceType<? extends ObservableSortedCollection<?>, ? extends CV2> getTargetType() {
				return theTargetType;
			}

			@Override
			public Operation.Instantiator<CV1, CV2> instantiate() throws ModelInstantiationException {
				return new Instantiator<>(instantiateSort());
			}
		}

		static class Instantiator<T, CV1 extends ObservableCollection<?>, CV2 extends ObservableSortedCollection<?>>
		implements CollectionFlowToFlowTransformInstantiator<CV1, CV2, T, T> {
			private final ModelValueInstantiator<Comparator<? super T>> theSorting;

			Instantiator(ModelValueInstantiator<Comparator<? super T>> sorting) {
				theSorting = sorting;
			}

			@Override
			public void instantiate() throws ModelInstantiationException {
				theSorting.instantiate();
			}

			@Override
			public CV2 transform(CV1 source, ModelSetInstance models) throws ModelInstantiationException {
				return (CV2) transformFlow((CollectionDataFlow<?, ?, T>) source.flow(), models).collectActive(models.getUntil());
			}

			@Override
			public CollectionDataFlow<?, ?, T> transformFlow(CollectionDataFlow<?, ?, T> source, ModelSetInstance models)
				throws ModelInstantiationException {
				Comparator<? super T> sorting = theSorting.get(models);
				return source.sorted(sorting);
			}

			@Override
			public CollectionDataFlow<?, ?, T> transformToFlow(CV1 source, ModelSetInstance models) throws ModelInstantiationException {
				return transformFlow((CollectionDataFlow<?, ?, T>) source.flow(), models);
			}

			@Override
			public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				if (theSorting.get(sourceModels) != theSorting.get(newModels))
					return true;
				return false;
			}
		}
	}

	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = UnmodifiableCollectionTransform.UNMODIFIABLE,
		interpretation = UnmodifiableCollectionTransform.Interpreted.class)
	static class UnmodifiableCollectionTransform<C extends ObservableCollection<?>> extends TypePreservingTransform<C>
	implements CollectionTransform<C, C, ExElement> {
		static final String UNMODIFIABLE = "unmodifiable";

		private boolean isAllowUpdates;

		UnmodifiableCollectionTransform(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		public boolean isAllowUpdates() {
			return isAllowUpdates;
		}

		@Override
		public void update(ExpressoQIS session, ModelType<C> sourceModelType) throws QonfigInterpretationException {
			super.update(session, sourceModelType);
			isAllowUpdates = session.getAttribute("allow-updates", boolean.class);
		}

		@Override
		protected Interpreted<?, C, ?> tppInterpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<T, C extends ObservableCollection<?>, CV extends C> extends TypePreservingTransform.Interpreted<C, CV> {
			Interpreted(UnmodifiableCollectionTransform<C> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public UnmodifiableCollectionTransform<C> getDefinition() {
				return (UnmodifiableCollectionTransform<C>) super.getDefinition();
			}

			@Override
			public BetterList<InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList.empty();
			}

			@Override
			public Instantiator<CV, CV> instantiate() {
				return new Instantiator<>(getDefinition().isAllowUpdates());
			}

			@Override
			public String toString() {
				return "unmodifiable()";
			}
		}

		static class Instantiator<T, CV extends ObservableCollection<?>>
		implements CollectionFlowToFlowTransformInstantiator<CV, CV, T, T> {
			private final boolean isAllowUpdates;

			Instantiator(boolean allowUpdates) {
				isAllowUpdates = allowUpdates;
			}

			@Override
			public void instantiate() {
			}

			@Override
			public CollectionDataFlow<?, ?, T> transformFlow(CollectionDataFlow<?, ?, T> source, ModelSetInstance models)
				throws ModelInstantiationException {
				return source.unmodifiable(isAllowUpdates);
			}

			@Override
			public CollectionDataFlow<?, ?, T> transformToFlow(CV source, ModelSetInstance models) throws ModelInstantiationException {
				return transformFlow((CollectionDataFlow<?, ?, T>) source.flow(), models);
			}

			@Override
			public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				return false;
			}
		}
	}

	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = FilterModCollectionTransform.FILTER_MOD,
		interpretation = FilterModCollectionTransform.Interpreted.class)
	static class FilterModCollectionTransform<C extends ObservableCollection<?>> extends TypePreservingTransform<C>
	implements CollectionTransform<C, C, ExElement> {
		static final String FILTER_MOD = "filter-mod";

		FilterModCollectionTransform(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@Override
		public void update(ExpressoQIS session, ModelType<C> sourceModelType) throws QonfigInterpretationException {
			super.update(session, sourceModelType);
		}

		@Override
		protected Interpreted<?, C, ?> tppInterpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<T, C extends ObservableCollection<?>, CV extends C> extends TypePreservingTransform.Interpreted<C, CV> {
			Interpreted(FilterModCollectionTransform<C> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public FilterModCollectionTransform<C> getDefinition() {
				return (FilterModCollectionTransform<C>) super.getDefinition();
			}

			@Override
			public void update(ModelInstanceType<C, CV> sourceType) throws ExpressoInterpretationException {
				super.update(sourceType);
				throw new ExpressoInterpretationException("Not yet implemented", reporting().getFileLocation().getPosition(0), 0);
			}

			@Override
			public BetterList<InterpretedValueSynth<?, ?>> getComponents() {
				throw new UnsupportedOperationException();
			}

			@Override
			public Instantiator<CV, CV> instantiate() {
				throw new UnsupportedOperationException("Not yet implemented");
			}

			@Override
			public String toString() {
				throw new UnsupportedOperationException();
			}
		}
	}

	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = MapEquivalentCollectionTransform.MAP_EQUIVALENT,
		interpretation = MapEquivalentCollectionTransform.Interpreted.class)
	static class MapEquivalentCollectionTransform<C extends ObservableCollection<?>>
	extends ExpressoTransformations.AbstractCompiledTransformation<C, C, ExElement> implements CollectionTransform<C, C, ExElement> {
		static final String MAP_EQUIVALENT = "map-equivalent";

		private ModelType<C> theSourceType;
		private ExSort.ExRootSort theSort;

		MapEquivalentCollectionTransform(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@Override
		public ModelType<? extends C> getTargetModelType() {
			return theSourceType;
		}

		@QonfigChildGetter("sort")
		public ExSort.ExRootSort getSort() {
			return theSort;
		}

		@Override
		public void update(ExpressoQIS session, ModelType<C> sourceModelType) throws QonfigInterpretationException {
			theSourceType = sourceModelType;
			super.update(session, sourceModelType);
			theSort = syncChild(ExSort.ExRootSort.class, theSort, session, "sort");
			if (theSourceType == ModelTypes.Collection)
				throw new QonfigInterpretationException("map-equivalent is not valid for a simple collection",
					session.reporting().getPosition(), 0);
			else if (theSourceType == ModelTypes.Set) {
				if (theSort != null)
					throw new QonfigInterpretationException("Sorting is invalid for map-equivalent on a non-sorted set",
						theSort.reporting().getPosition(), 0);
				else if (getReverse() == null)
					throw new QonfigInterpretationException("Reverse required for map-equivalent on a non-sorted set",
						reporting().getFileLocation().getPosition(0), 0);
			} else if (theSourceType == ModelTypes.SortedCollection || theSourceType == ModelTypes.SortedSet) {
				if (theSort == null && getReverse() == null)
					throw new QonfigInterpretationException(
						"Either reverse or sort required for map-equivalent on a sorted collection or set",
						reporting().getFileLocation().getPosition(0), 0);
			} else
				throw new QonfigInterpretationException("Unrecognized source collection type: " + theSourceType,
					reporting().getFileLocation().getPosition(0), 0);
		}

		@Override
		public ExpressoTransformations.CompiledTransformation.Interpreted<C, ?, ?, ?, C, ?, ? extends ExElement> interpret(
			ExElement.Interpreted<?> parent) throws ExpressoInterpretationException {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<C extends ObservableCollection<?>, S, T, CV1 extends C, CV2 extends C>
		extends ExpressoTransformations.AbstractCompiledTransformation.Interpreted<C, S, T, CV1, C, CV2, ExElement> {
			private ModelInstanceType<C, CV2> theTargetModelType;
			private ExSort.ExRootSort.Interpreted<T> theSort;

			Interpreted(MapEquivalentCollectionTransform<C> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public MapEquivalentCollectionTransform<C> getDefinition() {
				return (MapEquivalentCollectionTransform<C>) super.getDefinition();
			}

			public ExSort.ExRootSort.Interpreted<? super T> getSort() {
				return theSort;
			}

			@Override
			public void update(ModelInstanceType<C, CV1> sourceType) throws ExpressoInterpretationException {
				super.update(sourceType);
				theTargetModelType = (ModelInstanceType<C, CV2>) getDefinition().getTargetModelType().forTypes(getTargetValueType());
				if (getDefinition().getSort() == null) {
					if (theSort != null)
						theSort.destroy();
					theSort = null;
				} else if (theSort == null || theSort.getIdentity() != getDefinition().getSort().getIdentity()) {
					if (theSort != null)
						theSort.destroy();
					theSort = (ExSort.ExRootSort.Interpreted<T>) getDefinition().getSort().interpret(this);
				}
				if (theSort != null)
					theSort.update();
			}

			@Override
			public ModelInstanceType<? extends C, ? extends CV2> getTargetType() {
				return theTargetModelType;
			}

			@Override
			public ExpressoTransformations.CompiledTransformation.Instantiator<S, T, CV1, CV2> instantiate()
				throws ModelInstantiationException {
				return new Instantiator<>(instantiateLocalModels(), getMap().instantiate(),
					TypeTokens.get().getDefaultValue(getSourceType()), //
					QommonsUtils.filterMapE(getCombinedValues(), null, cv -> cv.instantiate()),
					getReverse() == null ? null : getReverse().instantiate(), getDefinition().getSourceName(),
						getDefinition().getPreviousResultAs(), getDefinition().isCached(), getDefinition().isReEvalOnUpdate(),
						getDefinition().isFireIfUnchanged(), getDefinition().isNullToNull(), getDefinition().isManyToOne(),
						getDefinition().isOneToMany(), getEquivalence() == null ? null : getEquivalence().instantiate(), isTesting(),
							theSort == null ? null : theSort.instantiateSort(), reporting().getPosition());
			}
		}

		static class Instantiator<S, T, CV1 extends ObservableCollection<?>, CV2 extends ObservableCollection<?>>
		extends AbstractCompiledTransformation.Instantiator<S, T, CV1, CV2>
		implements CollectionFlowToFlowTransformInstantiator<CV1, CV2, S, T> {
			private final ModelValueInstantiator<Comparator<? super T>> theSort;
			private final LocatedFilePosition theLocation;

			Instantiator(DocumentMap<ModelInstantiator> localModel, ModelValueInstantiator<SettableValue<T>> map, S defaultSource,
				List<ExpressoTransformations.CombineWith.Instantiator<?>> combinedValues,
				ExpressoTransformations.CompiledMapReverse.Instantiator<S, T> reverse, ModelComponentId sourceVariable,
				ModelComponentId previousResultVariable, boolean cached, boolean reEvalOnUpdate, boolean fireIfUnchanged,
				boolean nullToNull, boolean manyToOne, boolean oneToMany,
				ModelValueInstantiator<SettableValue<Equivalence<? super T>>> equivalence, boolean testing,
				ModelValueInstantiator<Comparator<? super T>> sort, LocatedFilePosition location) {
				super(localModel, map, defaultSource, combinedValues, reverse, sourceVariable, previousResultVariable, cached,
					reEvalOnUpdate, fireIfUnchanged, nullToNull, manyToOne, oneToMany, equivalence, testing);
				theSort = sort;
				theLocation = location;
			}

			@Override
			public void instantiate() throws ModelInstantiationException {
				super.instantiate();
				if (theSort != null)
					theSort.instantiate();
			}

			@Override
			public CV2 transform(CV1 source, ModelSetInstance models) throws ModelInstantiationException {
				CollectionDataFlow<?, ?, T> flow = transformToFlow(source, models);
				if (flow.prefersPassive())
					return (CV2) flow.collectPassive();
				else
					return (CV2) flow.collectActive(models.getUntil());
			}

			@Override
			public CollectionDataFlow<?, ?, T> transformFlow(CollectionDataFlow<?, ?, S> source, ModelSetInstance models)
				throws ModelInstantiationException {
				try {
					if (source instanceof SortedDataFlow) {
						if (theSort != null) {
							Comparator<? super T> sort = theSort.get(models);
							return ((SortedDataFlow<?, ?, S>) source).transformEquivalent(tx -> {
								try {
									return transform(tx, models);
								} catch (ModelInstantiationException e) {
									throw new CheckedExceptionWrapper(e);
								}
							}, sort);
						} else {
							return ((SortedDataFlow<?, ?, S>) source).transformEquivalent(tx -> {
								try {
									return (Transformation.ReversibleTransformation<S, T>) transform(tx, models);
								} catch (ModelInstantiationException e) {
									throw new CheckedExceptionWrapper(e);
								}
							});
						}
					} else if (source instanceof DistinctDataFlow) {
						return ((DistinctDataFlow<?, ?, S>) source).transformEquivalent(tx -> {
							try {
								return (Transformation.ReversibleTransformation<S, T>) transform(tx, models);
							} catch (ModelInstantiationException e) {
								throw new CheckedExceptionWrapper(e);
							}
						});
					} else
						throw new ModelInstantiationException("Source flow is neither distinct nor sorted: " + source.getClass().getName(),
							theLocation, 0);
				} catch (CheckedExceptionWrapper e) {
					throw CheckedExceptionWrapper.getThrowable(e, ModelInstantiationException.class);
				}
			}

			@Override
			public CollectionDataFlow<?, ?, T> transformToFlow(CV1 source, ModelSetInstance models) throws ModelInstantiationException {
				return transformFlow((CollectionDataFlow<?, ?, S>) source.flow(), models);
			}
		}
	}

	@ExMultiElementTraceable({
		@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
			qonfigType = FlattenCollectionTransform.FLATTEN,
			interpretation = FlattenCollectionTransform.Interpreted.class), //
		@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = "abst-map-op",
		interpretation = FlattenCollectionTransform.Interpreted.class) })
	static class FlattenCollectionTransform<C1 extends ObservableCollection<?>, C2 extends ObservableCollection<?>>
	extends ExElement.Def.Abstract<ExElement> implements CollectionTransform<C1, C2, ExElement> {
		public static final String FLATTEN = "flatten";

		private ModelType.SingleTyped<C2> theTargetModelType;
		private ExSort.ExRootSort theSort;
		private boolean isPropagateToParent;
		private boolean isCached;
		private boolean isReEvalOnUpdate;
		private boolean isFireIfUnchanged;
		private boolean isNullToNull;
		private boolean isManyToOne;
		private boolean isOneToMany;

		public FlattenCollectionTransform(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@QonfigChildGetter(asType = FLATTEN, value = "sort")
		public ExSort.ExRootSort getSort() {
			return theSort;
		}

		@QonfigAttributeGetter(asType = FLATTEN, value = "propagate-update-to-parent")
		public boolean isPropagateToParent() {
			return isPropagateToParent;
		}

		@QonfigAttributeGetter(asType = "abst-map-op", value = "cache")
		public boolean isCached() {
			return isCached;
		}

		@QonfigAttributeGetter(asType = "abst-map-op", value = "re-eval-on-update")
		public boolean isReEvalOnUpdate() {
			return isReEvalOnUpdate;
		}

		@QonfigAttributeGetter(asType = "abst-map-op", value = "fire-if-unchanged")
		public boolean isFireIfUnchanged() {
			return isFireIfUnchanged;
		}

		@QonfigAttributeGetter(asType = "abst-map-op", value = "null-to-null")
		public boolean isNullToNull() {
			return isNullToNull;
		}

		@QonfigAttributeGetter(asType = "abst-map-op", value = "many-to-one")
		public boolean isManyToOne() {
			return isManyToOne;
		}

		@QonfigAttributeGetter(asType = "abst-map-op", value = "one-to-many")
		public boolean isOneToMany() {
			return isOneToMany;
		}

		@QonfigAttributeGetter(asType = FLATTEN, value = "to")
		@Override
		public ModelType<? extends C2> getTargetModelType() {
			return theTargetModelType;
		}

		// These 2 methods suppress a warning

		@QonfigAttributeGetter(asType = FLATTEN, value = "equivalence")
		public Void getEquivalence() {
			return null;
		}

		@QonfigChildGetter(asType = FLATTEN, value = "reverse")
		public ExElement.Def<?> getReverse() {
			return null;
		}

		@Override
		public void update(ExpressoQIS session, ModelType<C1> sourceModelType) throws QonfigInterpretationException {
			update(session);
			if (!session.forChildren("reverse").isEmpty())
				throw new QonfigInterpretationException("Reverse is not yet implemented",
					session.children().get("reverse").get().getFirst().getElement().getFilePosition());
			if (session.attributes().get("equivalence").get() != null)
				throw new QonfigInterpretationException("Equivalence is not yet implemented",
					session.attributes().get("equivalence").getLocatedContent());
			theSort = syncChild(ExSort.ExRootSort.class, theSort, session, "sort");
			isPropagateToParent = session.attributes().get("propagate-update-to-parent").getValue(boolean.class, false);
			isCached = session.attributes().get("cache").getValue(boolean.class, false);
			isReEvalOnUpdate = session.attributes().get("re-eval-on-update").getValue(boolean.class, false);
			isFireIfUnchanged = session.attributes().get("fire-if-unchanged").getValue(boolean.class, false);
			isNullToNull = session.attributes().get("null-to-null").getValue(boolean.class, false);
			isManyToOne = session.attributes().get("many-to-one").getValue(boolean.class, false);
			isOneToMany = session.attributes().get("one-to-many").getValue(boolean.class, false);

			String targetModelTypeName = session.getAttributeText("to");
			switch (targetModelTypeName.toLowerCase()) {
			case "list":
				if (theSort != null)
					theTargetModelType = (ModelType.SingleTyped<C2>) ModelTypes.SortedCollection;
				else
					theTargetModelType = (ModelType.SingleTyped<C2>) ModelTypes.Collection;
				break;
			case "sorted-list":
				theTargetModelType = (ModelType.SingleTyped<C2>) ModelTypes.SortedCollection;
				break;
			case "set":
				if (theSort != null)
					theTargetModelType = (ModelType.SingleTyped<C2>) ModelTypes.SortedSet;
				else
					theTargetModelType = (ModelType.SingleTyped<C2>) ModelTypes.Set;
				break;
			case "sorted-set":
				theTargetModelType = (ModelType.SingleTyped<C2>) ModelTypes.SortedSet;
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
					session.attributes().get("to").getLocatedContent());
			default:
				throw new QonfigInterpretationException("Unrecognized model type target: '" + targetModelTypeName + "'",
					session.attributes().get("to").getLocatedContent());
			}
		}

		@Override
		public Interpreted<C1, ?, ?, ?, C2, ?> interpret(ExElement.Interpreted<?> parent) throws ExpressoInterpretationException {
			return new Interpreted<>(this, parent);
		}

		// All this tomfoolery is to avoid keeping any references to definition or interpretation values at runtime
		static final class MyOptions {
			private final Consumer<FlatMapOptions<?, ?, ?>> theOptions;

			public MyOptions(Consumer<FlatMapOptions<?, ?, ?>> options) {
				theOptions = options;
			}

			public <T, V, X> FlatMapOptions<T, V, X> apply(FlatMapOptions<T, V, X> options) {
				theOptions.accept(options);
				return options;
			}
		}

		MyOptions options() {
			boolean propagateToParent = isPropagateToParent();
			boolean cache = isCached();
			boolean reEvalOnUpdate = isReEvalOnUpdate();
			boolean fireIfUnchanged = isFireIfUnchanged();
			boolean nullToNull = isNullToNull();
			boolean manyToOne = isManyToOne();
			boolean oneToMany = isOneToMany();
			return new MyOptions(opts -> opts.cache(cache).reEvalOnUpdate(reEvalOnUpdate).fireIfUnchanged(fireIfUnchanged)
				.nullToNull(nullToNull).manyToOne(manyToOne).oneToMany(oneToMany)//
				.propagateUpdateToParent(propagateToParent));
		}

		static class Interpreted<C1 extends ObservableCollection<?>, CV1 extends C1, S, T, C2 extends ObservableCollection<?>, CV2 extends C2>
		extends ExElement.Interpreted.Abstract<ExElement> implements Operation.Interpreted<C1, CV1, C2, CV2, ExElement> {
			private TypeToken<T> theResultType;
			private ExSort.ExRootSort.Interpreted<T> theSort;
			private Function<CollectionDataFlow<?, ?, ?>, CollectionDataFlow<?, ?, T>> theFlatten;

			Interpreted(FlattenCollectionTransform<C1, C2> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public FlattenCollectionTransform<C1, C2> getDefinition() {
				return (FlattenCollectionTransform<C1, C2>) super.getDefinition();
			}

			@Override
			public void update(ModelInstanceType<C1, CV1> sourceType) throws ExpressoInterpretationException {
				Class<?> raw = TypeTokens.getRawType(sourceType.getType(0));
				TypeToken<T> resultType;
				MyOptions options = getDefinition().options();
				if (ObservableValue.class.isAssignableFrom(raw)) {
					resultType = (TypeToken<T>) sourceType.getType(0).resolveType(ObservableValue.class.getTypeParameters()[0]);
					theFlatten = LambdaUtils.printableFn(flow -> flow.flattenValues(v -> (ObservableValue<? extends T>) v), "flatValues",
						null);
				} else if (ObservableCollection.class.isAssignableFrom(raw)) {
					resultType = (TypeToken<T>) sourceType.getType(0).resolveType(Collection.class.getTypeParameters()[0]);
					theFlatten = LambdaUtils.printableFn(
						flow -> flow.flatMap(v -> v == null ? null : ((ObservableCollection<T>) v).flow(), opts -> options.apply(opts)//
							.map((s, v) -> v)),
						"FlatCollections", null);
				} else if (Collection.class.isAssignableFrom(raw)) {
					resultType = (TypeToken<T>) sourceType.getType(0).resolveType(Collection.class.getTypeParameters()[0]);
					theFlatten = LambdaUtils.printableFn(flow -> flow//
						.<ObservableCollection<T>> transform(tx -> tx//
							.cache(true).reEvalOnUpdate(true).fireIfUnchanged(false)//
							.build((s, txvs) -> {
								CollectionObservable<T> coll;
								if (txvs.hasPreviousResult()) {
									coll = (CollectionObservable<T>) txvs.getPreviousResult();
									coll.getCollectionValue().set((Collection<T>) s);
								} else {
									coll = new CollectionObservable<>(SettableValue.<Collection<T>> build()//
										.withValue((Collection<T>) s)//
										.build());
								}
								return coll;
							}))//
						.flatMap(v -> v.flow(), opts -> options.apply(opts)//
							.map((s, v) -> v)), //
						"FlatCollections", null);
				} else if (CollectionDataFlow.class.isAssignableFrom(raw)) {
					resultType = (TypeToken<T>) sourceType.getType(0).resolveType(CollectionDataFlow.class.getTypeParameters()[2]);
					theFlatten = LambdaUtils.printableFn(flow -> flow.flatMap(v -> (CollectionDataFlow<?, ?, ? extends T>) v, //
						opts -> options.apply(opts)//
						.map((s, v) -> v)),
						"flatFlows", null);
				} else
					throw new ExpressoInterpretationException("Cannot flatten a collection of type " + sourceType.getType(0),
						reporting().getFileLocation().getPosition(0), 0);
				theResultType = resultType;

				update();

				if (getDefinition().getSort() == null) {
					if (theSort != null)
						theSort.destroy();
					theSort = null;
				} else if (theSort == null || theSort.getIdentity() != getDefinition().getSort().getIdentity()) {
					if (theSort != null)
						theSort.destroy();
					theSort = (ExSort.ExRootSort.Interpreted<T>) getDefinition().getSort().interpret(this);
				}
				if (theSort != null)
					theSort.update();
			}

			@Override
			public ModelInstanceType<? extends C2, ? extends CV2> getTargetType() {
				return (ModelInstanceType<? extends C2, ? extends CV2>) getDefinition().getTargetModelType().forTypes(theResultType);
			}

			@Override
			public BetterList<InterpretedValueSynth<?, ?>> getComponents() {
				if (theSort != null)
					return theSort.getComponents();
				else
					return BetterList.empty();
			}

			@Override
			public Operation.Instantiator<CV1, CV2> instantiate() throws ModelInstantiationException {
				return new Instantiator<S, T, CV1, CV2>((ModelType<CV2>) getDefinition().getTargetModelType(),
					theSort == null ? null : theSort.instantiateSort(), theFlatten);
			}
		}

		static class CollectionObservable<T> extends ObservableCollectionImpl.SimpleCollectionBackedObservable<T> {
			CollectionObservable(SettableValue<? extends Collection<T>> collectionValue) {
				super(ObservableCollection.<T> build().build(), collectionValue);
			}

			@Override
			protected SettableValue<Collection<T>> getCollectionValue() {
				return (SettableValue<Collection<T>>) super.getCollectionValue();
			}
		}

		static class Instantiator<S, T, CV1 extends ObservableCollection<?>, CV2 extends ObservableCollection<?>>
		implements CollectionFlowToFlowTransformInstantiator<CV1, CV2, S, T> {
			private final ModelType<CV2> theTargetModelType;
			private final ModelValueInstantiator<Comparator<? super T>> theSort;
			private Function<CollectionDataFlow<?, ?, ?>, CollectionDataFlow<?, ?, T>> theFlatten;

			Instantiator(ModelType<CV2> targetModelType, ModelValueInstantiator<Comparator<? super T>> sort,
				Function<CollectionDataFlow<?, ?, ?>, CollectionDataFlow<?, ?, T>> flatten) {
				theTargetModelType = targetModelType;
				theSort = sort;
				theFlatten = flatten;
			}

			@Override
			public void instantiate() throws ModelInstantiationException {
				if (theSort != null)
					theSort.instantiate();
			}

			@Override
			public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				if (theSort != null && theSort.get(sourceModels) != theSort.get(newModels))
					return true;
				return false;
			}

			@Override
			public CV2 transform(CV1 source, ModelSetInstance models) throws ModelInstantiationException {
				return (CV2) transformToFlow(source, models).collect();
			}

			@Override
			public CollectionDataFlow<?, ?, T> transformFlow(CollectionDataFlow<?, ?, S> source, ModelSetInstance models)
				throws ModelInstantiationException {
				Comparator<? super T> sort = theSort == null ? null : theSort.get(models);
				CollectionDataFlow<?, ?, T> mapped = theFlatten.apply(source);
				boolean distinct = theTargetModelType == ModelTypes.SortedSet || theTargetModelType == ModelTypes.Set;
				if (distinct) {
					if (theSort != null)
						mapped = mapped.distinctSorted(sort, false);
					else
						mapped = mapped.distinct();
				} else if (theSort != null)
					mapped = mapped.sorted(sort);
				return mapped;
			}

			@Override
			public CollectionDataFlow<?, ?, T> transformToFlow(CV1 source, ModelSetInstance models) throws ModelInstantiationException {
				return transformFlow((CollectionDataFlow<?, ?, S>) source.flow(), models);
			}
		}
	}

	@ExMultiElementTraceable({
		@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
			qonfigType = CrossCollectionTransform.CROSS,
			interpretation = CrossCollectionTransform.Interpreted.class), //
		@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = FlattenCollectionTransform.FLATTEN,
		interpretation = CrossCollectionTransform.Interpreted.class), //
		@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = "abst-map-op",
		interpretation = CrossCollectionTransform.Interpreted.class), //
		@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = "complex-operation",
		interpretation = CrossCollectionTransform.Interpreted.class),//
	})
	static class CrossCollectionTransform<C extends ObservableCollection<?>> extends ExElement.Def.Abstract<ExElement>
	implements CollectionTransform<C, ObservableCollection<?>, ExElement> {
		public static final String CROSS = "cross";

		private ModelComponentId theSourceAs;
		private ModelComponentId theCrossAs;
		private CompiledExpression theWith;
		private CompiledExpression theValue;

		// Flatten options
		private boolean isPropagateToParent;
		private ExSort.ExRootSort theSort;
		private boolean isCached;
		private boolean isReEvalOnUpdate;
		private boolean isFireIfUnchanged;
		private boolean isNullToNull;
		private boolean isManyToOne;
		private boolean isOneToMany;

		public CrossCollectionTransform(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@QonfigAttributeGetter(asType = "complex-operation", value = "source-as")
		public ModelComponentId getSourceAs() {
			return theSourceAs;
		}

		@QonfigAttributeGetter(asType = FlattenCollectionTransform.FLATTEN, value = "to")
		public ModelType<SettableValue<?>> getTo() {
			return ModelTypes.Value;
		}

		@QonfigAttributeGetter(asType = CROSS, value = "crossed-as")
		public ModelComponentId getCrossAs() {
			return theCrossAs;
		}

		@QonfigAttributeGetter(asType = CROSS, value = "with")
		public CompiledExpression getWith() {
			return theWith;
		}

		@Override
		@QonfigAttributeGetter(asType = CROSS)
		public CompiledExpression getElementValue() {
			return theValue;
		}

		@QonfigAttributeGetter(asType = FlattenCollectionTransform.FLATTEN, value = "propagate-update-to-parent")
		public boolean isPropagateToParent() {
			return isPropagateToParent;
		}

		@QonfigChildGetter(asType = FlattenCollectionTransform.FLATTEN, value = "sort")
		public ExSort.ExRootSort getSort() {
			return theSort;
		}

		@QonfigAttributeGetter(asType = "abst-map-op", value = "cache")
		public boolean isCached() {
			return isCached;
		}

		@QonfigAttributeGetter(asType = "abst-map-op", value = "re-eval-on-update")
		public boolean isReEvalOnUpdate() {
			return isReEvalOnUpdate;
		}

		@QonfigAttributeGetter(asType = "abst-map-op", value = "fire-if-unchanged")
		public boolean isFireIfUnchanged() {
			return isFireIfUnchanged;
		}

		@QonfigAttributeGetter(asType = "abst-map-op", value = "null-to-null")
		public boolean isNullToNull() {
			return isNullToNull;
		}

		@QonfigAttributeGetter(asType = "abst-map-op", value = "many-to-one")
		public boolean isManyToOne() {
			return isManyToOne;
		}

		@QonfigAttributeGetter(asType = "abst-map-op", value = "one-to-many")
		public boolean isOneToMany() {
			return isOneToMany;
		}

		@Override
		public ModelType<? extends ObservableCollection<?>> getTargetModelType() {
			return ModelTypes.Collection;
		}

		@Override
		public void update(ExpressoQIS session, ModelType<C> sourceModelType) throws QonfigInterpretationException {
			super.update(session);
			ExWithElementModel.Def withElModel = getAddOn(ExWithElementModel.Def.class);
			String sourceAs = session.getAttributeText("source-as");
			theSourceAs = withElModel.getElementValueModelId(sourceAs);
			String crossAs = session.getAttributeText("crossed-as");
			theCrossAs = withElModel.getElementValueModelId(crossAs);
			withElModel.<Interpreted<?, ?, ?, ?, ?>, SettableValue<?>> satisfyElementSingleValueType(theSourceAs, ModelTypes.Value,
				Interpreted::getSourceType);
			withElModel.<Interpreted<?, ?, ?, ?, ?>, SettableValue<?>> satisfyElementSingleValueType(theCrossAs, ModelTypes.Value,
				interp -> interp.getWith().getType().getType(0));
			theWith = getAttributeExpression("with", session);
			theValue = getValueExpression(session);

			theSort = syncChild(ExSort.ExRootSort.class, theSort, session, "sort");
			isPropagateToParent = session.attributes().get("propagate-update-to-parent").getValue(boolean.class, false);
			isCached = session.attributes().get("cache").getValue(boolean.class, false);
			isReEvalOnUpdate = session.attributes().get("re-eval-on-update").getValue(boolean.class, false);
			isFireIfUnchanged = session.attributes().get("fire-if-unchanged").getValue(boolean.class, false);
			isNullToNull = session.attributes().get("null-to-null").getValue(boolean.class, false);
			isManyToOne = session.attributes().get("many-to-one").getValue(boolean.class, false);
			isOneToMany = session.attributes().get("one-to-many").getValue(boolean.class, false);
		}

		@Override
		public ExpressoTransformations.Operation.Interpreted<C, ?, ObservableCollection<?>, ?, ? extends ExElement> interpret(
			ExElement.Interpreted<?> parent) throws ExpressoInterpretationException {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<C1 extends ObservableCollection<?>, S, T, CV1 extends C1, X>
		extends ExElement.Interpreted.Abstract<ExElement>
		implements Operation.Interpreted<C1, CV1, ObservableCollection<?>, ObservableCollection<T>, ExElement> {
			private TypeToken<S> theSourceType;
			private InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<X>> theWith;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<T>> theValue;

			private ExSort.ExRootSort.Interpreted<T> theSort;

			Interpreted(CrossCollectionTransform<C1> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public CrossCollectionTransform<C1> getDefinition() {
				return (CrossCollectionTransform<C1>) super.getDefinition();
			}

			public TypeToken<S> getSourceType() {
				return theSourceType;
			}

			public InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<X>> getWith() {
				return theWith;
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getElementValue() {
				return theValue;
			}

			public ExSort.ExRootSort.Interpreted<T> getSort() {
				return theSort;
			}

			@Override
			public void update(ModelInstanceType<C1, CV1> sourceType) throws ExpressoInterpretationException {
				theSourceType = (TypeToken<S>) sourceType.getType(0);
				theWith = interpret(getDefinition().getWith(), ModelTypes.Collection.anyAsV());
				super.update();
				theValue = interpret(getDefinition().getElementValue(), ModelTypes.Value.anyAsV());

				if (getDefinition().getSort() == null) {
					if (theSort != null)
						theSort.destroy();
					theSort = null;
				} else if (theSort == null || theSort.getIdentity() != getDefinition().getSort().getIdentity()) {
					if (theSort != null)
						theSort.destroy();
					theSort = (ExSort.ExRootSort.Interpreted<T>) getDefinition().getSort().interpret(this);
				}
				if (theSort != null)
					theSort.update();
			}

			@Override
			public ModelInstanceType<? extends ObservableCollection<?>, ? extends ObservableCollection<T>> getTargetType() {
				return ModelTypes.Collection.forType((TypeToken<T>) theValue.getType().getType(0));
			}

			@Override
			public BetterList<InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList.of(theWith, theValue);
			}

			@Override
			public Operation.Instantiator<CV1, ObservableCollection<T>> instantiate() throws ModelInstantiationException {
				return new Instantiator<>(this);
			}
		}

		static class Instantiator<CV1 extends ObservableCollection<?>, S, T, X>
		implements CollectionFlowToFlowTransformInstantiator<CV1, ObservableCollection<T>, S, T> {
			private final DocumentMap<ModelInstantiator> theModels;
			private final ModelComponentId theSourceAs;
			private final ModelComponentId theCrossedAs;
			private final ModelValueInstantiator<ObservableCollection<X>> theWith;
			private final ModelValueInstantiator<SettableValue<T>> theValue;

			// Flatten options
			private final ModelValueInstantiator<Comparator<? super T>> theSort;
			private final boolean isPropagateToParent;
			private final boolean isCached;
			private final boolean isReEvalOnUpdate;
			private final boolean isFireIfUnchanged;
			private final boolean isNullToNull;
			private final boolean isManyToOne;
			private final boolean isOneToMany;

			Instantiator(Interpreted<? super CV1, S, T, CV1, X> interpreted) throws ModelInstantiationException {
				theModels = interpreted.instantiateLocalModels();
				theSourceAs = interpreted.getDefinition().getSourceAs();
				theCrossedAs = interpreted.getDefinition().getCrossAs();
				theWith = interpreted.getWith().instantiate();
				theValue = interpreted.getElementValue().instantiate();

				theSort = interpreted.getSort() == null ? null : interpreted.getSort().instantiateSort();
				isPropagateToParent = interpreted.getDefinition().isPropagateToParent();
				isCached = interpreted.getDefinition().isCached();
				isReEvalOnUpdate = interpreted.getDefinition().isReEvalOnUpdate();
				isFireIfUnchanged = interpreted.getDefinition().isFireIfUnchanged();
				isNullToNull = interpreted.getDefinition().isNullToNull();
				isManyToOne = interpreted.getDefinition().isManyToOne();
				isOneToMany = interpreted.getDefinition().isOneToMany();
			}

			@Override
			public void instantiate() throws ModelInstantiationException {
				theModels.forEach(ModelInstantiator::instantiate);
				theWith.instantiate();
				theValue.instantiate();
				if (theSort != null)
					theSort.instantiate();
			}

			@Override
			public CollectionDataFlow<?, ?, T> transformFlow(CollectionDataFlow<?, ?, S> source, ModelSetInstance models)
				throws ModelInstantiationException {
				models = theModels.operate(models, (m, mi) -> mi.wrap(m));
				SettableValue<S> sourceAs = SettableValue.<S> build().build();
				SettableValue<X> crossedAs = SettableValue.<X> build().build();
				ExFlexibleElementModelAddOn.satisfyElementValue(theSourceAs, models, sourceAs);
				ExFlexibleElementModelAddOn.satisfyElementValue(theCrossedAs, models, crossedAs);
				SettableValue<T> result = theValue.get(models);
				ObservableCollection<X> with = theWith.get(models);
				Comparator<? super T> sort = theSort == null ? null : theSort.get(models);

				CollectionDataFlow<?, ?, T> crossed = source.cross(with.flow(), opts -> opts//
					.propagateUpdateToParent(isPropagateToParent)//
					.cache(isCached).reEvalOnUpdate(isReEvalOnUpdate).fireIfUnchanged(isFireIfUnchanged).nullToNull(isNullToNull)
					.manyToOne(isManyToOne).oneToMany(isOneToMany)//
					.map((s, x) -> {
						sourceAs.set(s, null);
						crossedAs.set(x, null);
						return result.get();
					}));
				if (theSort != null)
					crossed = crossed.distinctSorted(sort, false);
				return crossed;
			}

			@Override
			public CollectionDataFlow<?, ?, T> transformToFlow(CV1 source, ModelSetInstance models) throws ModelInstantiationException {
				return transformFlow((CollectionDataFlow<?, ?, S>) source.flow(), models);
			}

			@Override
			public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				sourceModels = theModels.operate(sourceModels, (m, mi) -> mi.wrap(m));
				newModels = theModels.operate(newModels, (m, mi) -> mi.wrap(m));
				ObservableCollection<X> sourceWith = theWith.get(sourceModels);
				if (theWith.forModelCopy(sourceWith, sourceModels, newModels) != sourceWith)
					return true;
				SettableValue<T> sourceValue = theValue.get(sourceModels);
				if (theValue.forModelCopy(sourceValue, sourceModels, newModels) != sourceValue)
					return true;
				return false;
			}
		}
	}

	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = WhereContainedCollectionTransform.WHERE_CONTAINED,
		interpretation = WhereContainedCollectionTransform.Interpreted.class)
	static class WhereContainedCollectionTransform<C extends ObservableCollection<?>> extends TypePreservingTransform<C>
	implements CollectionTransform<C, C, ExElement> {
		static final String WHERE_CONTAINED = "where-contained";

		private CompiledExpression theFilter;
		private boolean isInclusive;

		WhereContainedCollectionTransform(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@QonfigAttributeGetter("filter")
		public CompiledExpression getFilter() {
			return theFilter;
		}

		@QonfigAttributeGetter("inclusive")
		public boolean isInclusive() {
			return isInclusive;
		}

		@Override
		public void update(ExpressoQIS session, ModelType<C> sourceModelType) throws QonfigInterpretationException {
			super.update(session, sourceModelType);
			theFilter = getAttributeExpression("filter", session);
			isInclusive = session.getAttribute("inclusive", boolean.class);
		}

		@Override
		protected Interpreted<?, C, ?> tppInterpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<T, C extends ObservableCollection<?>, CV extends C> extends TypePreservingTransform.Interpreted<C, CV> {
			private InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<?>> theFilter;

			Interpreted(WhereContainedCollectionTransform<C> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public WhereContainedCollectionTransform<C> getDefinition() {
				return (WhereContainedCollectionTransform<C>) super.getDefinition();
			}

			public InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<?>> getRefresh() {
				return theFilter;
			}

			@Override
			public void update(ModelInstanceType<C, CV> sourceType) throws ExpressoInterpretationException {
				super.update(sourceType);
				theFilter = interpret(getDefinition().getFilter(), ModelTypes.Collection.any());
			}

			@Override
			public BetterList<InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList.of(theFilter);
			}

			@Override
			public Operation.Instantiator<CV, CV> instantiate() throws ModelInstantiationException {
				return new Instantiator<>(theFilter.instantiate(), getDefinition().isInclusive());
			}

			@Override
			public String toString() {
				return "whereContained(" + theFilter + ", " + getDefinition().isInclusive() + ")";
			}
		}

		static class Instantiator<T, CV extends ObservableCollection<?>>
		implements CollectionFlowToFlowTransformInstantiator<CV, CV, T, T> {
			private final ModelValueInstantiator<ObservableCollection<?>> theFilter;
			private final boolean isInclusive;

			Instantiator(ModelValueInstantiator<ObservableCollection<?>> filter, boolean inclusive) {
				theFilter = filter;
				isInclusive = inclusive;
			}

			@Override
			public void instantiate() throws ModelInstantiationException {
				theFilter.instantiate();
			}

			@Override
			public CollectionDataFlow<?, ?, T> transformFlow(CollectionDataFlow<?, ?, T> source, ModelSetInstance models)
				throws ModelInstantiationException {
				ObservableCollection<?> filter = theFilter.get(models);
				return source.whereContained(filter.flow(), isInclusive);
			}

			@Override
			public CollectionDataFlow<?, ?, T> transformToFlow(CV source, ModelSetInstance models) throws ModelInstantiationException {
				return transformFlow((CollectionDataFlow<?, ?, T>) source.flow(), models);
			}

			@Override
			public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				ObservableCollection<?> filter = theFilter.get(sourceModels);
				return theFilter.forModelCopy(filter, sourceModels, newModels) != filter;
			}
		}
	}

	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = SizeCollectionTransform.SIZE,
		interpretation = SizeCollectionTransform.Interpreted.class)
	static class SizeCollectionTransform<C extends ObservableCollection<?>> extends ExElement.Def.Abstract<ExElement>
	implements CollectionTransform<C, SettableValue<?>, ExElement> {
		static final String SIZE = "size";

		private QonfigValueType.Literal theType;

		SizeCollectionTransform(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@QonfigAttributeGetter("type")
		public QonfigValueType.Literal getType() {
			return theType;
		}

		@Override
		public ModelType<? extends SettableValue<?>> getTargetModelType() {
			return ModelTypes.Value;
		}

		@Override
		public void update(ExpressoQIS session, ModelType<C> sourceModelType) throws QonfigInterpretationException {
			super.update(session);
			theType = session.getAttribute("type", QonfigValueType.Literal.class);
			if (!"value".equals(theType.getValue()))
				throw new QonfigInterpretationException("Only 'value' type may be used for size of collections, not " + theType.getValue(),
					session.attributes().get("type").getLocatedContent());
		}

		@Override
		public Interpreted<C, ?> interpret(ExElement.Interpreted<?> parent) throws ExpressoInterpretationException {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<C extends ObservableCollection<?>, CV extends C> extends ExElement.Interpreted.Abstract<ExElement>
		implements Operation.Interpreted<C, CV, SettableValue<?>, SettableValue<Integer>, ExElement> {
			Interpreted(SizeCollectionTransform<C> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public ModelInstanceType<? extends SettableValue<?>, ? extends SettableValue<Integer>> getTargetType() {
				return ModelTypes.Value.INT;
			}

			@Override
			public void update(ModelInstanceType<C, CV> sourceType) throws ExpressoInterpretationException {
				super.update();
			}

			@Override
			public BetterList<InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList.empty();
			}

			@Override
			public Operation.Instantiator<CV, SettableValue<Integer>> instantiate() {
				return new Instantiator<>(toString());
			}
		}

		static class Instantiator<CV extends ObservableCollection<?>>
		implements Operation.EfficientCopyingInstantiator<CV, SettableValue<Integer>> {
			private final String theAlias;

			public Instantiator(String alias) {
				theAlias = alias;
			}

			@Override
			public boolean isEfficientCopy() {
				return true;
			}

			@Override
			public void instantiate() {
			}

			@Override
			public SettableValue<Integer> transform(CV source, ModelSetInstance models) throws ModelInstantiationException {
				return new CollectionSizeObservable<>(source).alias(theAlias);
			}

			@Override
			public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				return false;
			}

			@Override
			public CV getSource(SettableValue<Integer> value) {
				return ((CollectionSizeObservable<CV>) value).getSource();
			}

			@Override
			public SettableValue<Integer> forModelCopy(SettableValue<Integer> prevValue, CV newSource, ModelSetInstance sourceModels,
				ModelSetInstance newModels) throws ModelInstantiationException {
				if (newSource == ((CollectionSizeObservable<CV>) prevValue).getSource())
					return prevValue;
				return new CollectionSizeObservable<>(newSource).alias(theAlias);
			}
		}

		static class CollectionSizeObservable<CV extends ObservableCollection<?>> extends SettableValue.AlwaysDisabledValue<Integer> {
			private final CV theCollection;

			CollectionSizeObservable(CV collection) {
				super(collection.observeSize(), __ -> "Size cannot be assigned directly");
				theCollection = collection;
			}

			protected CV getSource() {
				return theCollection;
			}

			@Override
			public String toString() {
				return theCollection.getIdentity() + ".size()";
			}
		}
	}

	@ExMultiElementTraceable({ //
		@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
			qonfigType = ReducedCollectionTransform.REDUCE,
			interpretation = ReducedCollectionTransform.Interpreted.class), //
		@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = "complex-operation",
		interpretation = ReducedCollectionTransform.Interpreted.class)//
	})
	static class ReducedCollectionTransform<C extends ObservableCollection<?>> extends ExElement.Def.Abstract<ExElement>
	implements CollectionTransform<C, SettableValue<?>, ExElement> {
		public static final String REDUCE = "reduce";

		private ModelComponentId theSourceAs;
		private ModelComponentId theTempAs;
		private CompiledExpression theSeed;
		private CompiledExpression theValue;

		public ReducedCollectionTransform(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@Override
		public ModelType<? extends SettableValue<?>> getTargetModelType() {
			return ModelTypes.Value;
		}

		@QonfigAttributeGetter(asType = "complex-operation", value = "source-as")
		public ModelComponentId getSourceAs() {
			return theSourceAs;
		}

		@QonfigAttributeGetter(asType = REDUCE, value = "temp-as")
		public ModelComponentId getTempAs() {
			return theTempAs;
		}

		@QonfigAttributeGetter(asType = REDUCE, value = "seed")
		public CompiledExpression getSeed() {
			return theSeed;
		}

		@Override
		@QonfigAttributeGetter(asType = REDUCE)
		public CompiledExpression getElementValue() {
			return theValue;
		}

		@Override
		public void update(ExpressoQIS session, ModelType<C> sourceModelType) throws QonfigInterpretationException {
			super.update(session);

			ExWithElementModel.Def withElModel = getAddOn(ExWithElementModel.Def.class);
			String sourceAs = session.getAttributeText("source-as");
			theSourceAs = withElModel.getElementValueModelId(sourceAs);
			String tempAs = session.getAttributeText("temp-as");
			theTempAs = withElModel.getElementValueModelId(tempAs);
			withElModel.<Interpreted<?, ?, ?, ?>, SettableValue<?>> satisfyElementSingleValueType(theSourceAs, ModelTypes.Value,
				Interpreted::getSourceType);
			withElModel.<Interpreted<?, ?, ?, ?>, SettableValue<?>> satisfyElementSingleValueType(theTempAs, ModelTypes.Value,
				Interpreted::getValueType);
			theSeed = getAttributeExpression("seed", session);
			theValue = getValueExpression(session);
		}

		@Override
		public Operation.Interpreted<C, ?, SettableValue<?>, ?, ? extends ExElement> interpret(ExElement.Interpreted<?> parent)
			throws ExpressoInterpretationException {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<C extends ObservableCollection<?>, CV extends C, S, T> extends ExElement.Interpreted.Abstract<ExElement>
		implements Operation.Interpreted<C, CV, SettableValue<?>, SettableValue<T>, ExElement> {
			private TypeToken<S> theSourceType;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<T>> theSeed;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<T>> theValue;

			Interpreted(ReducedCollectionTransform<C> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public ReducedCollectionTransform<C> getDefinition() {
				return (ReducedCollectionTransform<C>) super.getDefinition();
			}

			public TypeToken<S> getSourceType() {
				return theSourceType;
			}

			public TypeToken<T> getValueType() {
				return (TypeToken<T>) theSeed.getType().getType(0);
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getSeed() {
				return theSeed;
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getValue() {
				return theValue;
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getElementValue() {
				return theValue;
			}

			@Override
			public void update(ModelInstanceType<C, CV> sourceType) throws ExpressoInterpretationException {
				theSourceType = (TypeToken<S>) sourceType.getType(0);
				theSeed = interpret(getDefinition().getSeed(), ModelTypes.Value.anyAsV());
				super.update();
				theValue = interpret(getDefinition().getElementValue(), theSeed.getType());
			}

			@Override
			public ModelInstanceType<? extends SettableValue<?>, ? extends SettableValue<T>> getTargetType() {
				return theValue.getType();
			}

			@Override
			public BetterList<InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList.of(theSeed, theValue);
			}

			@Override
			public Operation.Instantiator<CV, SettableValue<T>> instantiate() throws ModelInstantiationException {
				return new Instantiator<>(this);
			}
		}

		static class Instantiator<C extends ObservableCollection<?>, S, T> implements Operation.Instantiator<C, SettableValue<T>> {
			private final DocumentMap<ModelInstantiator> theModels;
			private final ModelComponentId theSourceAs;
			private final ModelComponentId theTempAs;
			private final ModelValueInstantiator<SettableValue<T>> theSeed;
			private final ModelValueInstantiator<SettableValue<T>> theValue;

			Instantiator(Interpreted<?, C, S, T> interpreted) throws ModelInstantiationException {
				theModels = interpreted.instantiateLocalModels();
				theSourceAs = interpreted.getDefinition().getSourceAs();
				theTempAs = interpreted.getDefinition().getTempAs();
				theSeed = interpreted.getSeed().instantiate();
				theValue = interpreted.getElementValue().instantiate();
			}

			@Override
			public void instantiate() throws ModelInstantiationException {
				theModels.forEach(ModelInstantiator::instantiate);
				theSeed.instantiate();
				theValue.instantiate();
			}

			@Override
			public SettableValue<T> transform(C source, ModelSetInstance models) throws ModelInstantiationException {
				SettableValue<T> seed = theSeed.get(models);
				models = theModels.operate(models, (m, mi) -> mi.wrap(m));
				SettableValue<S> sourceAs = SettableValue.<S> build().build();
				SettableValue<T> tempAs = SettableValue.<T> build().build();
				ExFlexibleElementModelAddOn.satisfyElementValue(theSourceAs, models, sourceAs);
				ExFlexibleElementModelAddOn.satisfyElementValue(theTempAs, models, tempAs);
				SettableValue<T> value = theValue.get(models);
				ObservableValue<T> reduced;
				/*
				 * I was trying to be clever here.  If the value was reversible, I'd use the efficient version of reduce.
				 * I tested this with addition and it seemed to work.
				 * But this is invalid if the operation doesn't preserve enough information.
				 * E.g. for an OR operation among booleans, this fails on modification.
				if (value.isEnabled() == null) {
					reduced = ((ObservableCollection<S>) source).reduce(seed.get(), (temp, newValue) -> {
						tempAs.set(temp, null);
						sourceAs.set(newValue, null);
						return value.get();
					}, (temp, oldValue) -> {
						sourceAs.set(oldValue, null);
						value.set(temp, null);
						return tempAs.get();
					});
				} else {*/
				reduced = ((ObservableCollection<S>) source).reduce(seed.get(), (temp, newValue) -> {
					tempAs.set(temp, null);
					sourceAs.set(newValue, null);
					return value.get();
				});
				// }
				return SettableValue.asSettable(reduced, __ -> "Reduced values are not modifiable");
			}

			@Override
			public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				sourceModels = theModels.operate(sourceModels, (m, mi) -> mi.wrap(m));
				newModels = theModels.operate(newModels, (m, mi) -> mi.wrap(m));
				SettableValue<T> seed = theSeed.get(sourceModels);
				if (theValue.forModelCopy(seed, sourceModels, newModels) != seed)
					return true;
				SettableValue<T> value = theValue.get(sourceModels);
				if (theValue.forModelCopy(value, sourceModels, newModels) != value)
					return true;
				return false;
			}
		}
	}

	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = TerminalCollectionTransform.TERMINAL,
		interpretation = TerminalCollectionTransform.Interpreted.class) //
	static class TerminalCollectionTransform<C extends ObservableCollection<?>> extends ExElement.Def.Abstract<ExElement>
	implements CollectionTransform<C, SettableValue<?>, ExElement> {
		public static final String TERMINAL = "terminal";

		private boolean isFirst;

		public TerminalCollectionTransform(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@Override
		public ModelType<? extends SettableValue<?>> getTargetModelType() {
			return ModelTypes.Value;
		}

		@QonfigAttributeGetter("first")
		public boolean isFirst() {
			return isFirst;
		}

		@Override
		public void update(ExpressoQIS session, ModelType<C> sourceModelType) throws QonfigInterpretationException {
			super.update(session);

			isFirst = session.getAttribute("first", boolean.class);
		}

		@Override
		public Operation.Interpreted<C, ?, SettableValue<?>, ?, ? extends ExElement> interpret(ExElement.Interpreted<?> parent)
			throws ExpressoInterpretationException {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<C extends ObservableCollection<?>, CV extends C, T> extends ExElement.Interpreted.Abstract<ExElement>
		implements Operation.Interpreted<C, CV, SettableValue<?>, SettableValue<T>, ExElement> {
			private TypeToken<T> theValueType;

			Interpreted(TerminalCollectionTransform<C> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public TerminalCollectionTransform<C> getDefinition() {
				return (TerminalCollectionTransform<C>) super.getDefinition();
			}

			public TypeToken<T> getValueType() {
				return theValueType;
			}

			@Override
			public void update(ModelInstanceType<C, CV> sourceType) throws ExpressoInterpretationException {
				theValueType = (TypeToken<T>) sourceType.getType(0);
				super.update();
			}

			@Override
			public ModelInstanceType<? extends SettableValue<?>, ? extends SettableValue<T>> getTargetType() {
				return ModelTypes.Value.forType(theValueType);
			}

			@Override
			public BetterList<InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList.empty();
			}

			@Override
			public Operation.Instantiator<CV, SettableValue<T>> instantiate() throws ModelInstantiationException {
				return new Instantiator<>(this);
			}
		}

		static class Instantiator<C extends ObservableCollection<?>, T> implements Operation.Instantiator<C, SettableValue<T>> {
			private final boolean isFirst;

			Instantiator(Interpreted<?, C, T> interpreted) throws ModelInstantiationException {
				isFirst = interpreted.getDefinition().isFirst();
			}

			@Override
			public void instantiate() throws ModelInstantiationException {
			}

			@Override
			public SettableValue<T> transform(C source, ModelSetInstance models) throws ModelInstantiationException {
				return ((ObservableCollection<T>) source).observeTerminal().at(isFirst).find();
			}

			@Override
			public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				return false;
			}
		}
	}

	@ExMultiElementTraceable({
		@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
			qonfigType = GroupByTransform.GROUP_BY,
			interpretation = GroupByTransform.Interpreted.class), //
		@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = "complex-operation",
		interpretation = GroupByTransform.Interpreted.class)//
	})
	static class GroupByTransform<C extends ObservableCollection<?>> extends ExElement.Def.Abstract<ExElement>
	implements CollectionTransform<C, ObservableMultiMap<?, ?>, ExElement> {
		/** The XML name of this element */
		public static final String GROUP_BY = "group-by";

		private ModelComponentId theSourceAs;
		private CompiledExpression theKey;

		public GroupByTransform(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		/**
		 * @return The ID of the model value that will hold the value of elements in the source collection to be grouped by the
		 *         {@link #getKey() key} expression
		 */
		@QonfigAttributeGetter(asType = "complex-operation", value = "source-as")
		public ModelComponentId getSourceAs() {
			return theSourceAs;
		}

		/** @return The expression determining the key group that each element in the collection belongs to */
		@QonfigAttributeGetter(asType = GROUP_BY, value = "key")
		public CompiledExpression getKey() {
			return theKey;
		}

		@Override
		public ModelType<? extends ObservableMultiMap<?, ?>> getTargetModelType() {
			return ModelTypes.MultiMap;
		}

		@Override
		public void update(ExpressoQIS session, ModelType<C> sourceModelType) throws QonfigInterpretationException {
			super.update(session);
			String sourceAs = session.getAttributeText("source-as");
			ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
			theSourceAs = elModels.getElementValueModelId(sourceAs);
			theKey = getAttributeExpression("key", session);
			elModels.<Interpreted<C, ?, ?, ?>, SettableValue<?>> satisfyElementSingleValueType(theSourceAs, ModelTypes.Value,
				Interpreted::getSourceType);
		}

		@Override
		public ExpressoTransformations.Operation.Interpreted<C, ?, ObservableMultiMap<?, ?>, ?, ? extends ExElement> interpret(
			ExElement.Interpreted<?> parent) throws ExpressoInterpretationException {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<C extends ObservableCollection<?>, S, CV extends C, K> extends ExElement.Interpreted.Abstract<ExElement>
		implements Operation.Interpreted<C, CV, ObservableMultiMap<?, ?>, ObservableMultiMap<K, S>, ExElement> {
			private TypeToken<S> theSourceType;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<K>> theKey;
			private ModelInstanceType<ObservableMultiMap<?, ?>, ObservableMultiMap<K, S>> theTargetType;

			Interpreted(GroupByTransform<C> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public GroupByTransform<C> getDefinition() {
				return (GroupByTransform<C>) super.getDefinition();
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<K>> getKey() {
				return theKey;
			}

			public TypeToken<S> getSourceType() {
				return theSourceType;
			}

			@Override
			public void update(ModelInstanceType<C, CV> sourceType) throws ExpressoInterpretationException {
				theSourceType = (TypeToken<S>) sourceType.getType(0);
				super.update();
				theKey = interpret(getDefinition().getKey(), ModelTypes.Value.anyAs());
				theTargetType = ModelTypes.MultiMap.forType((TypeToken<K>) theKey.getType().getType(0), theSourceType);
			}

			@Override
			public ModelInstanceType<? extends ObservableMultiMap<?, ?>, ? extends ObservableMultiMap<K, S>> getTargetType() {
				return theTargetType;
			}

			@Override
			public BetterList<InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList.of(theKey);
			}

			@Override
			public Operation.Instantiator<CV, ObservableMultiMap<K, S>> instantiate() throws ModelInstantiationException {
				return new Instantiator<>(this);
			}
		}

		static class Instantiator<C extends ObservableCollection<?>, K, V> implements //
		ObservableMultiMapTransformations.CollectionToMultiMapTransformInstantiator<C, ObservableMultiMap<K, V>, V, K, V> {
			private final DocumentMap<ModelInstantiator> theModels;
			private final ModelComponentId theSourceAs;
			private final ModelValueInstantiator<SettableValue<K>> theKey;

			Instantiator(Interpreted<?, V, C, K> interpreted) throws ModelInstantiationException {
				theModels = interpreted.instantiateLocalModels();
				theSourceAs = interpreted.getDefinition().getSourceAs();
				theKey = interpreted.getKey().instantiate();
			}

			@Override
			public void instantiate() throws ModelInstantiationException {
				theModels.forEach(ModelInstantiator::instantiate);
				theKey.instantiate();
			}

			@Override
			public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				sourceModels = theModels.operate(sourceModels, (m, mi) -> mi.wrap(m));
				newModels = theModels.operate(newModels, (m, mi) -> mi.wrap(m));
				SettableValue<K> sourceKey = theKey.get(sourceModels);
				SettableValue<K> newKey = theKey.forModelCopy(sourceKey, sourceModels, newModels);
				return sourceKey != newKey;
			}

			@Override
			public CollectionDataFlow<?, ?, V> getSourceFlow(C source, ModelSetInstance models) throws ModelInstantiationException {
				return (CollectionDataFlow<?, ?, V>) source.flow();
			}

			@Override
			public MultiMapFlow<K, V> toMapFlow(CollectionDataFlow<?, ?, V> sourceFlow, ModelSetInstance models)
				throws ModelInstantiationException {
				models = theModels.operate(models, (m, mi) -> mi.wrap(m));
				SettableValue<V> value = SettableValue.create();
				ExFlexibleElementModelAddOn.satisfyElementValue(theSourceAs, models, value);
				SettableValue<K> key = theKey.get(models);
				BiFunction<K, V, V> reverse;
				if (key.isEnabled().get() == null) {
					reverse = (k, v) -> {
						value.set(v);
						key.set(k);
						return value.get();
					};
				} else
					reverse = null;
				return sourceFlow.groupBy(LambdaUtils.printableFn(v -> {
					value.set(v);
					return key.get();
				}, value::toString, null), reverse);
			}
		}
	}

	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = CollectCollectionTransform.COLLECT,
		interpretation = CollectCollectionTransform.Interpreted.class)
	static class CollectCollectionTransform<C extends ObservableCollection<?>> extends TypePreservingTransform<C>
	implements CollectionTransform<C, C, ExElement> {
		static final String COLLECT = "collect";

		Boolean isActive;

		CollectCollectionTransform(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@QonfigAttributeGetter("active")
		public Boolean isActive() {
			return isActive;
		}

		@Override
		public void update(ExpressoQIS session, ModelType<C> sourceModelType) throws QonfigInterpretationException {
			super.update(session, sourceModelType);
			isActive = session.getAttribute("active", Boolean.class);
		}

		@Override
		protected Interpreted<?, C, ?> tppInterpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		@Override
		public String toString() {
			return "collect(" + (isActive == null ? "" : (isActive ? "active" : "passive")) + ")";
		}

		static class Interpreted<T, C extends ObservableCollection<?>, CV extends C> extends TypePreservingTransform.Interpreted<C, CV> {
			Interpreted(CollectCollectionTransform<C> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public CollectCollectionTransform<C> getDefinition() {
				return (CollectCollectionTransform<C>) super.getDefinition();
			}

			@Override
			public BetterList<InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList.empty();
			}

			@Override
			public Operation.Instantiator<CV, CV> instantiate() {
				return new Instantiator<>(getDefinition().isActive());
			}

			@Override
			public String toString() {
				return getDefinition().toString();
			}
		}

		static class Instantiator<T, CV extends ObservableCollection<?>>
		implements CollectionFlowToFlowTransformInstantiator<CV, CV, T, T> {
			private final Boolean isActive;

			Instantiator(Boolean active) {
				isActive = active;
			}

			@Override
			public void instantiate() {
			}

			@Override
			public CollectionDataFlow<?, ?, T> transformFlow(CollectionDataFlow<?, ?, T> source, ModelSetInstance models)
				throws ModelInstantiationException {
				boolean reallyActive;
				if (isActive != null)
					reallyActive = isActive.booleanValue();
				else
					reallyActive = !source.prefersPassive();
				if (reallyActive)
					return source.collectActive(models.getUntil()).flow();
				else
					return source.collectPassive().flow();
			}

			@Override
			public CollectionDataFlow<?, ?, T> transformToFlow(CV source, ModelSetInstance models) throws ModelInstantiationException {
				return transformFlow((CollectionDataFlow<?, ?, T>) source.flow(), models);
			}

			@Override
			public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				return false;
			}
		}
	}
}
